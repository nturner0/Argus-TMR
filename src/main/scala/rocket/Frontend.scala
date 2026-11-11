// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.rocket

import chisel3._
import chisel3.util._
import chisel3.experimental.SourceInfo

import org.chipsalliance.cde.config._
import org.chipsalliance.diplomacy.bundlebridge._
import org.chipsalliance.diplomacy.lazymodule._

import freechips.rocketchip.tile.{CoreBundle, BaseTile}
import freechips.rocketchip.tilelink.{TLWidthWidget, TLEdgeOut}
import freechips.rocketchip.util.{ClockGate, ShiftQueue, property}

import freechips.rocketchip.util.UIntToAugmentedUInt

class FrontendReq(implicit p: Parameters) extends CoreBundle()(p) {
  val pc = UInt(vaddrBitsExtended.W)
  val speculative = Bool()
}

class FrontendExceptions extends Bundle {
  val pf = new Bundle {
    val inst = Bool()
  }
  val gf = new Bundle {
    val inst = Bool()
  }
  val ae = new Bundle {
    val inst = Bool()
  }
}

class FrontendResp(implicit p: Parameters) extends CoreBundle()(p) {
  val btb = new BTBResp
  val pc = UInt(vaddrBitsExtended.W)  // ID stage PC
  val data = UInt((fetchWidth * coreInstBits).W)
  val mask = Bits(fetchWidth.W)
  val xcpt = new FrontendExceptions
  val replay = Bool()
}

class FrontendPerfEvents extends Bundle {
  val acquire = Bool()
  val tlbMiss = Bool()
}

class FrontendIO(implicit p: Parameters) extends CoreBundle()(p) {
  val might_request = Output(Bool())
  val clock_enabled = Input(Bool())
  val req = Valid(new FrontendReq)
  val sfence = Valid(new SFenceReq)
  val resp = Flipped(Decoupled(new FrontendResp))
  val gpa = Flipped(Valid(UInt(vaddrBitsExtended.W)))
  val gpa_is_pte = Input(Bool())
  val btb_update = Valid(new BTBUpdate)
  val bht_update = Valid(new BHTUpdate)
  val ras_update = Valid(new RASUpdate)
  val flush_icache = Output(Bool())
  val npc = Input(UInt(vaddrBitsExtended.W))
  val perf = Input(new FrontendPerfEvents())
  val progress = Output(Bool())
}

class Frontend(val icacheParams: ICacheParams, tileId: Int)(implicit p: Parameters) extends LazyModule {
  lazy val module = new FrontendModule(this)
  val icache = LazyModule(new ICache(icacheParams, tileId))
  val masterNode = icache.masterNode
  val slaveNode = icache.slaveNode
  val resetVectorSinkNode = BundleBridgeSink[UInt](Some(() => UInt(masterNode.edges.out.head.bundle.addressBits.W)))
}

class FrontendBundle(val outer: Frontend) extends CoreBundle()(outer.p) {
  val cpu = Flipped(new FrontendIO())
  val ptw = new TLBPTWIO()
  val errors = new ICacheErrors
}

class FrontendModule(outer: Frontend) extends LazyModuleImp(outer)
    with HasRocketCoreParameters
    with HasL1ICacheParameters {
  val io = IO(new FrontendBundle(outer))
  val io_reset_vector = outer.resetVectorSinkNode.bundle
  implicit val edge: TLEdgeOut = outer.masterNode.edges.out(0)
  val icache = outer.icache.module
  require(fetchWidth*coreInstBytes == outer.icacheParams.fetchBytes)

  val fq = withReset(reset.asBool || io.cpu.req.valid) { Module(new TMRShiftQueue(new FrontendResp, 5, flow = true)) }

  // TMR for frontend pipeline state: voted read, corrected feedback on hold.
  // Majority vote each scalar leaf; works for UInt() with inferred widths
  // and for Vec/Bundle records containing inferred-width fields.
  private def tmrVoteBits[T <: Data](a: T, b: T, c: T): T = {
    val voted = Wire(chiselTypeOf(a))
    def voteInto(x: Data, y: Data, z: Data, dst: Data): Unit = (x, y, z, dst) match {
      case (xa: Bool, yb: Bool, zc: Bool, out: Bool) =>
        out := (xa & yb) | (xa & zc) | (yb & zc)
      case (xa: UInt, yb: UInt, zc: UInt, out: UInt) =>
        out := (xa & yb) | (xa & zc) | (yb & zc)
      case (xa: SInt, yb: SInt, zc: SInt, out: SInt) =>
        out := ((xa.asUInt & yb.asUInt) |
                (xa.asUInt & zc.asUInt) |
                (yb.asUInt & zc.asUInt)).asSInt
      case (xa: Vec[_], yb: Vec[_], zc: Vec[_], out: Vec[_]) =>
        require(xa.length == yb.length && xa.length == zc.length && xa.length == out.length)
        for (i <- 0 until xa.length) voteInto(xa(i), yb(i), zc(i), out(i))
      case (xa: Record, yb: Record, zc: Record, out: Record) =>
        require(xa.elements.keySet == out.elements.keySet &&
                yb.elements.keySet == out.elements.keySet &&
                zc.elements.keySet == out.elements.keySet)
        out.elements.foreach { case (field, leaf) =>
          voteInto(xa.elements(field), yb.elements(field), zc.elements(field), leaf)
        }
      case _ =>
        throw new IllegalArgumentException("Unsupported TMR Data leaf type: " + dst.getClass.getName)
    }
    voteInto(a, b, c, voted)
    voted
  }

  private def tmrFetchVote[T <: Data](a: T, b: T, c: T, label: String): (T, T) = {
    a.suggestName(label + "_A")
    b.suggestName(label + "_B")
    c.suggestName(label + "_C")
    dontTouch(a)
    dontTouch(b)
    dontTouch(c)
    val voted = tmrVoteBits(a, b, c)
    val next = WireDefault(voted)
    a := next
    b := next
    c := next
    (voted, next)
  }

  private def tmrFetchReg[T <: Data](gen: => T, label: String): (T, T) =
    tmrFetchVote(Reg(gen), Reg(gen), Reg(gen), label)

  private def tmrFetchRegInit[T <: Data](init: => T, label: String): (T, T) =
    tmrFetchVote(RegInit(init), RegInit(init), RegInit(init), label)

  private def tmrFetchRegInitTyped[T <: Data](t: => T, init: => T, label: String): (T, T) =
    tmrFetchVote(RegInit(t, init), RegInit(t, init), RegInit(t, init), label)

  private def tmrFetchRegNext[T <: Data](d: T, label: String): T = {
    val (voted, next) = tmrFetchReg(chiselTypeOf(d), label)
    next := d
    voted
  }

  private def tmrFetchRegNextInit[T <: Data](d: T, init: T, label: String): T = {
    val (voted, next) = tmrFetchRegInit(init, label)
    next := d
    voted
  }

  val (clock_en_reg, clock_en_reg_next) = tmrFetchReg(Bool(), "clock_en_reg")
  val clock_en = clock_en_reg || io.cpu.might_request
  io.cpu.clock_enabled := clock_en
  assert(!(io.cpu.req.valid || io.cpu.sfence.valid || io.cpu.flush_icache || io.cpu.bht_update.valid || io.cpu.btb_update.valid) || io.cpu.might_request)
  val gated_clock =
    if (!rocketParams.clockGate) clock
    else ClockGate(clock, clock_en, "icache_clock_gate")

  icache.clock := gated_clock
  icache.io.clock_enabled := clock_en
  withClock (gated_clock) { // entering gated-clock domain

  val tlb = Module(new TLB(true, log2Ceil(fetchBytes), TLBConfig(nTLBSets, nTLBWays, outer.icacheParams.nTLBBasePageSectors, outer.icacheParams.nTLBSuperpages)))

  val (s1_valid, s1_valid_next) = tmrFetchReg(Bool(), "s1_valid")
  val (s2_valid, s2_valid_next) = tmrFetchRegInit(false.B, "s2_valid")
  val s0_fq_has_space =
    !fq.io.mask(fq.io.mask.getWidth-3) ||
    (!fq.io.mask(fq.io.mask.getWidth-2) && (!s1_valid || !s2_valid)) ||
    (!fq.io.mask(fq.io.mask.getWidth-1) && (!s1_valid && !s2_valid))
  val s0_valid = io.cpu.req.valid || s0_fq_has_space
  s1_valid_next := s0_valid
  val (s1_pc, s1_pc_next) = tmrFetchReg(UInt(vaddrBitsExtended.W), "s1_pc")
  val (s1_speculative, s1_speculative_next) = tmrFetchReg(Bool(), "s1_speculative")
  val (s2_pc, s2_pc_next) = tmrFetchRegInitTyped(UInt(vaddrBitsExtended.W), alignPC(io_reset_vector), "s2_pc")
  val (s2_btb_resp_valid, s2_btb_resp_valid_next) =
    if (usingBTB) tmrFetchReg(Bool(), "s2_btb_resp_valid")
    else (false.B, WireDefault(false.B))
  val (s2_btb_resp_bits, s2_btb_resp_bits_next) = tmrFetchReg(new BTBResp, "s2_btb_resp_bits")
  val s2_btb_taken = s2_btb_resp_valid && s2_btb_resp_bits.taken
  val (s2_tlb_resp, s2_tlb_resp_next) = tmrFetchReg(tlb.io.resp.cloneType, "s2_tlb_resp")
  val s2_xcpt = s2_tlb_resp.ae.inst || s2_tlb_resp.pf.inst || s2_tlb_resp.gf.inst
  val (s2_speculative, s2_speculative_next) = tmrFetchRegInit(false.B, "s2_speculative")
  val (s2_partial_insn_valid, s2_partial_insn_valid_next) = tmrFetchRegInit(false.B, "s2_partial_insn_valid")
  val (s2_partial_insn, s2_partial_insn_next) = tmrFetchReg(UInt(coreInstBits.W), "s2_partial_insn")
  val (wrong_path, wrong_path_next) = tmrFetchRegInit(false.B, "wrong_path")

  val s1_base_pc = ~(~s1_pc | (fetchBytes - 1).U)
  val ntpc = s1_base_pc + fetchBytes.U
  val predicted_npc = WireDefault(ntpc)
  val predicted_taken = WireDefault(false.B)

  val s2_replay = Wire(Bool())
  s2_replay := (s2_valid && !fq.io.enq.fire) || tmrFetchRegNextInit(s2_replay && !s0_valid, true.B, "s2_replay_delay")
  val npc = Mux(s2_replay, s2_pc, predicted_npc)

  s1_pc_next := io.cpu.npc
  // consider RVC fetches across blocks to be non-speculative if the first
  // part was non-speculative
  val s0_speculative =
    if (usingCompressed) s1_speculative || s2_valid && !s2_speculative || predicted_taken
    else true.B
  s1_speculative_next := Mux(io.cpu.req.valid, io.cpu.req.bits.speculative, Mux(s2_replay, s2_speculative, s0_speculative))

  val s2_redirect = WireDefault(io.cpu.req.valid)
  s2_valid_next := false.B
  when (!s2_replay) {
    s2_valid_next := !s2_redirect
    s2_pc_next := s1_pc
    s2_speculative_next := s1_speculative
    s2_tlb_resp_next := tlb.io.resp
  }

  val recent_progress_counter_init = 3.U
  val (recent_progress_counter, recent_progress_counter_next) = tmrFetchRegInit(recent_progress_counter_init, "recent_progress_counter")
  val recent_progress = recent_progress_counter > 0.U
  when(io.ptw.req.fire && recent_progress) { recent_progress_counter_next := recent_progress_counter - 1.U }
  when(io.cpu.progress) { recent_progress_counter_next := recent_progress_counter_init }

  val s2_kill_speculative_tlb_refill = s2_speculative && !recent_progress

  io.ptw <> tlb.io.ptw
  tlb.io.req.valid := s1_valid && !s2_replay
  tlb.io.req.bits.cmd := M_XRD // Frontend only reads
  tlb.io.req.bits.vaddr := s1_pc
  tlb.io.req.bits.passthrough := false.B
  tlb.io.req.bits.size := log2Ceil(coreInstBytes*fetchWidth).U
  tlb.io.req.bits.prv := io.ptw.status.prv
  tlb.io.req.bits.v := io.ptw.status.v
  tlb.io.sfence := io.cpu.sfence
  tlb.io.kill := !s2_valid || s2_kill_speculative_tlb_refill

  icache.io.req.valid := s0_valid
  icache.io.req.bits.addr := io.cpu.npc
  icache.io.invalidate := io.cpu.flush_icache
  icache.io.s1_paddr := tlb.io.resp.paddr
  icache.io.s2_vaddr := s2_pc
  icache.io.s1_kill := s2_redirect || tlb.io.resp.miss || s2_replay
  val s2_can_speculatively_refill = s2_tlb_resp.cacheable && !io.ptw.customCSRs.asInstanceOf[RocketCustomCSRs].disableSpeculativeICacheRefill
  icache.io.s2_kill := s2_speculative && !s2_can_speculatively_refill || s2_xcpt
  icache.io.s2_cacheable := s2_tlb_resp.cacheable
  icache.io.s2_prefetch := s2_tlb_resp.prefetchable && !io.ptw.customCSRs.asInstanceOf[RocketCustomCSRs].disableICachePrefetch

  fq.io.enq.valid := tmrFetchRegNext(s1_valid, "s1_valid_delay") && s2_valid && (icache.io.resp.valid || (s2_kill_speculative_tlb_refill && s2_tlb_resp.miss) || (!s2_tlb_resp.miss && icache.io.s2_kill))
  fq.io.enq.bits.pc := s2_pc
  io.cpu.npc := alignPC(Mux(io.cpu.req.valid, io.cpu.req.bits.pc, npc))

  fq.io.enq.bits.data := icache.io.resp.bits.data
  fq.io.enq.bits.mask := ((1 << fetchWidth)-1).U << s2_pc.extract(log2Ceil(fetchWidth)+log2Ceil(coreInstBytes)-1, log2Ceil(coreInstBytes))
  fq.io.enq.bits.replay := (icache.io.resp.bits.replay || icache.io.s2_kill && !icache.io.resp.valid && !s2_xcpt) || (s2_kill_speculative_tlb_refill && s2_tlb_resp.miss)
  fq.io.enq.bits.btb := s2_btb_resp_bits
  fq.io.enq.bits.btb.taken := s2_btb_taken
  fq.io.enq.bits.xcpt := s2_tlb_resp
  assert(!(s2_speculative && io.ptw.customCSRs.asInstanceOf[RocketCustomCSRs].disableSpeculativeICacheRefill && !icache.io.s2_kill))
  when (icache.io.resp.valid && icache.io.resp.bits.ae) { fq.io.enq.bits.xcpt.ae.inst := true.B }

  if (usingBTB) {
    val btb = Module(new BTB)
    btb.io.flush := false.B
    btb.io.req.valid := false.B
    btb.io.req.bits.addr := s1_pc
    btb.io.btb_update := io.cpu.btb_update
    btb.io.bht_update := io.cpu.bht_update
    btb.io.ras_update.valid := false.B
    btb.io.ras_update.bits := DontCare
    btb.io.bht_advance.valid := false.B
    btb.io.bht_advance.bits := DontCare
    when (!s2_replay) {
      btb.io.req.valid := !s2_redirect
      s2_btb_resp_valid_next := btb.io.resp.valid
      s2_btb_resp_bits_next := btb.io.resp.bits
    }
    when (btb.io.resp.valid && btb.io.resp.bits.taken) {
      predicted_npc := btb.io.resp.bits.target.sextTo(vaddrBitsExtended)
      predicted_taken := true.B
    }

    val force_taken = io.ptw.customCSRs.bpmStatic
    when (io.ptw.customCSRs.flushBTB) { btb.io.flush := true.B }
    when (force_taken) { btb.io.bht_update.valid := false.B }

    val s2_base_pc = ~(~s2_pc | (fetchBytes-1).U)
    val taken_idx = Wire(UInt())
    val after_idx = Wire(UInt())
    val useRAS = WireDefault(false.B)
    val updateBTB = WireDefault(false.B)

    // If !prevTaken, ras_update / bht_update is always invalid. 
    taken_idx := DontCare
    after_idx := DontCare

    def scanInsns(idx: Int, prevValid: Bool, prevBits: UInt, prevTaken: Bool): Bool = {
      def insnIsRVC(bits: UInt) = bits(1,0) =/= 3.U
      val prevRVI = prevValid && !insnIsRVC(prevBits)
      val valid = fq.io.enq.bits.mask(idx) && !prevRVI
      val bits = fq.io.enq.bits.data(coreInstBits*(idx+1)-1, coreInstBits*idx)
      val rvc = insnIsRVC(bits)
      val rviBits = Cat(bits, prevBits)
      val rviBranch = rviBits(6,0) === Instructions.BEQ.value.U.extract(6,0)
      val rviJump = rviBits(6,0) === Instructions.JAL.value.U.extract(6,0)
      val rviJALR = rviBits(6,0) === Instructions.JALR.value.U.extract(6,0)
      val rviReturn = rviJALR && !rviBits(7) && BitPat("b00?01") === rviBits(19,15)
      val rviCall = (rviJALR || rviJump) && rviBits(7)
      val rvcBranch = bits === Instructions.C_BEQZ || bits === Instructions.C_BNEZ
      val rvcJAL = (xLen == 32).B && bits === Instructions32.C_JAL
      val rvcJump = bits === Instructions.C_J || rvcJAL
      val rvcImm = Mux(bits(14), new RVCDecoder(bits, xLen, fLen).bImm.asSInt, new RVCDecoder(bits, xLen, fLen).jImm.asSInt)
      val rvcJR = bits === Instructions.C_MV && bits(6,2) === 0.U
      val rvcReturn = rvcJR && BitPat("b00?01") === bits(11,7)
      val rvcJALR = bits === Instructions.C_ADD && bits(6,2) === 0.U
      val rvcCall = rvcJAL || rvcJALR
      val rviImm = Mux(rviBits(3), ImmGen(IMM_UJ, rviBits), ImmGen(IMM_SB, rviBits))
      val predict_taken = s2_btb_resp_bits.bht.taken || force_taken
      val taken =
        prevRVI && (rviJump || rviJALR || rviBranch && predict_taken) ||
        valid && (rvcJump || rvcJALR || rvcJR || rvcBranch && predict_taken)
      val predictReturn = btb.io.ras_head.valid && (prevRVI && rviReturn || valid && rvcReturn)
      val predictJump = prevRVI && rviJump || valid && rvcJump
      val predictBranch = predict_taken && (prevRVI && rviBranch || valid && rvcBranch)

      when (s2_valid && s2_btb_resp_valid && s2_btb_resp_bits.bridx === idx.U && valid && !rvc) {
        // The BTB has predicted that the middle of an RVI instruction is
        // a branch! Flush the BTB and the pipeline.
        btb.io.flush := true.B
        fq.io.enq.bits.replay := true.B
        wrong_path_next := true.B
        ccover(wrong_path, "BTB_NON_CFI_ON_WRONG_PATH", "BTB predicted a non-branch was taken while on the wrong path")
      }

      when (!prevTaken) {
        taken_idx := idx.U
        after_idx := (idx + 1).U
        btb.io.ras_update.valid := fq.io.enq.fire && !wrong_path && (prevRVI && (rviCall || rviReturn) || valid && (rvcCall || rvcReturn))
        btb.io.ras_update.bits.cfiType := Mux(Mux(prevRVI, rviReturn, rvcReturn), CFIType.ret,
                                          Mux(Mux(prevRVI, rviCall, rvcCall), CFIType.call,
                                          Mux(Mux(prevRVI, rviBranch, rvcBranch) && !force_taken, CFIType.branch,
                                          CFIType.jump)))

        when (!s2_btb_taken) {
          when (fq.io.enq.fire && taken && !predictBranch && !predictJump && !predictReturn) {
            wrong_path_next := true.B
          }
          when (s2_valid && predictReturn) {
            useRAS := true.B
          }
          when (s2_valid && (predictBranch || predictJump)) {
            val pc = s2_base_pc | (idx*coreInstBytes).U
            val npc =
              if (idx == 0) pc.asSInt + Mux(prevRVI, rviImm -& 2.S, rvcImm)
              else Mux(prevRVI, pc - coreInstBytes.U, pc).asSInt + Mux(prevRVI, rviImm, rvcImm)
            predicted_npc := npc.asUInt
          }
        }
        when (prevRVI && rviBranch || valid && rvcBranch) {
          btb.io.bht_advance.valid := fq.io.enq.fire && !wrong_path
          btb.io.bht_advance.bits := s2_btb_resp_bits
        }
        when (!s2_btb_resp_valid && (predictBranch && s2_btb_resp_bits.bht.strongly_taken || predictJump || predictReturn)) {
          updateBTB := true.B
        }
      }

      if (idx == fetchWidth-1) {
        when (fq.io.enq.fire) {
          s2_partial_insn_valid_next := false.B
          when (valid && !prevTaken && !rvc) {
            s2_partial_insn_valid_next := true.B
            s2_partial_insn_next := bits | 0x3.U
          }
        }
        prevTaken || taken
      } else {
        scanInsns(idx + 1, valid, bits, prevTaken || taken)
      }
    }

    when (!io.cpu.btb_update.valid) {
      val fetch_bubble_likely = !fq.io.mask(1)
      btb.io.btb_update.valid := fq.io.enq.fire && !wrong_path && fetch_bubble_likely && updateBTB
      btb.io.btb_update.bits.prediction.entry := tileParams.btb.get.nEntries.U
      btb.io.btb_update.bits.isValid := true.B
      btb.io.btb_update.bits.cfiType := btb.io.ras_update.bits.cfiType
      btb.io.btb_update.bits.br_pc := s2_base_pc | (taken_idx << log2Ceil(coreInstBytes))
      btb.io.btb_update.bits.pc := s2_base_pc
    }

    btb.io.ras_update.bits.returnAddr := s2_base_pc + (after_idx << log2Ceil(coreInstBytes))

    val taken = scanInsns(0, s2_partial_insn_valid, s2_partial_insn, false.B)
    when (useRAS) {
      predicted_npc := btb.io.ras_head.bits
    }
    when (fq.io.enq.fire && (s2_btb_taken || taken)) {
      s2_partial_insn_valid_next := false.B
    }
    when (!s2_btb_taken) {
      when (taken) {
        fq.io.enq.bits.btb.bridx := taken_idx
        fq.io.enq.bits.btb.taken := true.B
        fq.io.enq.bits.btb.entry := tileParams.btb.get.nEntries.U
        when (fq.io.enq.fire) { s2_redirect := true.B }
      }
    }

    assert(!s2_partial_insn_valid || fq.io.enq.bits.mask(0))
    when (s2_redirect) { s2_partial_insn_valid_next := false.B }
    when (io.cpu.req.valid) { wrong_path_next := false.B }
  }

  io.cpu.resp <> fq.io.deq

  // supply guest physical address to commit stage
  val (gpa_valid, gpa_valid_next) = tmrFetchReg(Bool(), "gpa_valid")
  val (gpa, gpa_next) = tmrFetchReg(UInt(vaddrBitsExtended.W), "gpa")
  val (gpa_is_pte, gpa_is_pte_next) = tmrFetchReg(Bool(), "gpa_is_pte")
  when (fq.io.enq.fire && s2_tlb_resp.gf.inst) {
    when (!gpa_valid) {
      gpa_next := s2_tlb_resp.gpa
      gpa_is_pte_next := s2_tlb_resp.gpa_is_pte
    }
    gpa_valid_next := true.B
  }
  when (io.cpu.req.valid) {
    gpa_valid_next := false.B
  }
  io.cpu.gpa.valid := gpa_valid
  io.cpu.gpa.bits := gpa
  io.cpu.gpa_is_pte := gpa_is_pte

  // performance events
  io.cpu.perf.acquire := icache.io.perf.acquire
  io.cpu.perf.tlbMiss := io.ptw.req.fire
  io.errors := icache.io.errors

  // gate the clock
  clock_en_reg_next := !rocketParams.clockGate.B ||
    io.cpu.might_request || // chicken bit
    icache.io.keep_clock_enabled || // I$ miss or ITIM access
    s1_valid || s2_valid || // some fetch in flight
    !tlb.io.req.ready || // handling TLB miss
    !fq.io.mask(fq.io.mask.getWidth-1) // queue not full
  } // leaving gated-clock domain

  def alignPC(pc: UInt) = ~(~pc | (coreInstBytes - 1).U)

  def ccover(cond: Bool, label: String, desc: String)(implicit sourceInfo: SourceInfo) =
    property.cover(cond, s"FRONTEND_$label", "Rocket;;" + desc)
}

/** Mix-ins for constructing tiles that have an ICache-based pipeline frontend */
trait HasICacheFrontend extends CanHavePTW { this: BaseTile =>
  val module: HasICacheFrontendModule
  val frontend = LazyModule(new Frontend(tileParams.icache.get, tileId))
  tlMasterXbar.node := TLWidthWidget(tileParams.icache.get.rowBits/8) := frontend.masterNode
  connectTLSlave(frontend.slaveNode, tileParams.core.fetchBytes)
  frontend.icache.hartIdSinkNodeOpt.foreach { _ := hartIdNexusNode }
  frontend.icache.mmioAddressPrefixSinkNodeOpt.foreach { _ := mmioAddressPrefixNexusNode }
  frontend.resetVectorSinkNode := resetVectorNexusNode
  nPTWPorts += 1

  // This should be a None in the case of not having an ITIM address, when we
  // don't actually use the device that is instantiated in the frontend.
  private val deviceOpt = if (tileParams.icache.get.itimAddr.isDefined) Some(frontend.icache.device) else None
}

trait HasICacheFrontendModule extends CanHavePTWModule {
  val outer: HasICacheFrontend
  ptwPorts += outer.frontend.module.io.ptw
}
