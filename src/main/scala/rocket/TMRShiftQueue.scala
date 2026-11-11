// See LICENSE.SiFive for license details.

package freechips.rocketchip.rocket

import freechips.rocketchip.util._

import chisel3._
import chisel3.util._

/** Implements the same interface as chisel3.util.Queue, but uses a shift
  * register internally.  It is less energy efficient whenever the queue
  * has more than one entry populated, but is faster on the dequeue side.
  * It is efficient for usually-empty flow-through queues. */
class TMRShiftQueue[T <: Data](gen: T,
                            val entries: Int,
                            pipe: Boolean = false,
                            flow: Boolean = false)
    extends Module {
  val io = IO(new QueueIO(gen, entries) {
    val mask = Output(UInt(TMRShiftQueue.this.entries.W))
  })

  // Each frontend queue register bank has three copies. Reads are majority-
  // voted, and holding a value writes back the corrected voted state.
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

  private def tmrQueueVote[D <: Data](a: D, b: D, c: D, label: String): (D, D) = {
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

  private def tmrQueueReg[D <: Data](gen: => D, label: String): (D, D) =
    tmrQueueVote(Reg(gen), Reg(gen), Reg(gen), label)

  private def tmrQueueRegInit[D <: Data](init: => D, label: String): (D, D) =
    tmrQueueVote(RegInit(init), RegInit(init), RegInit(init), label)

  val (valid, validNext) = tmrQueueRegInit(VecInit(Seq.fill(entries) { false.B }), "valid")
  val (elts, eltsNext) = tmrQueueReg(Vec(entries, gen), "elts")

  for (i <- 0 until entries) {
    def paddedValid(i: Int) = if (i == -1) true.B else if (i == entries) false.B else valid(i)

    val wdata = if (i == entries-1) io.enq.bits else Mux(valid(i+1), elts(i+1), io.enq.bits)
    val wen =
      Mux(io.deq.ready,
          paddedValid(i+1) || io.enq.fire && ((i == 0 && !flow).B || valid(i)),
          io.enq.fire && paddedValid(i-1) && !valid(i))
    when (wen) { eltsNext(i) := wdata }

    validNext(i) :=
      Mux(io.deq.ready,
          paddedValid(i+1) || io.enq.fire && ((i == 0 && !flow).B || valid(i)),
          io.enq.fire && paddedValid(i-1) || valid(i))
  }

  io.enq.ready := !valid(entries-1)
  io.deq.valid := valid(0)
  io.deq.bits := elts.head

  if (flow) {
    when (io.enq.valid) { io.deq.valid := true.B }
    when (!valid(0)) { io.deq.bits := io.enq.bits }
  }

  if (pipe) {
    when (io.deq.ready) { io.enq.ready := true.B }
  }

  io.mask := valid.asUInt
  io.count := PopCount(io.mask)
}

object TMRShiftQueue
{
  def apply[T <: Data](enq: DecoupledIO[T], entries: Int = 2, pipe: Boolean = false, flow: Boolean = false): DecoupledIO[T] = {
    val q = Module(new TMRShiftQueue(enq.bits.cloneType, entries, pipe, flow))
    q.io.enq <> enq
    q.io.deq
  }
}
