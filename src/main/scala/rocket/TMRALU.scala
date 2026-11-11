
package freechips.rocketchip.rocket

import chisel3._
import org.chipsalliance.cde.config.Parameters

class TMRALU(injectFault: Boolean = false)
  (implicit p: Parameters) extends AbstractALU()(p) {

  override def desiredName = "RocketTMRALU"

  // Three ALU replicas
  val alu0 = Module(new ALU)
  val alu1 = Module(new ALU)
  val alu2 = Module(new ALU)

  // Drive all replicas with identical inputs
  Seq(alu0, alu1, alu2).foreach { alu =>
    alu.io.dw  := io.dw
    alu.io.fn  := io.fn
    alu.io.in1 := io.in1
    alu.io.in2 := io.in2
  }

  // Bitwise majority voter
  def majority(a: UInt, b: UInt, c: UInt): UInt =
    (a & b) | (a & c) | (b & c)

  // Optional fault injection for testing
  val alu0Out =
    if (injectFault) alu0.io.out ^ 1.U
    else alu0.io.out

  // Vote on ALU result
  io.out := majority(
    alu0Out,
    alu1.io.out,
    alu2.io.out
  )

  // Vote on adder result
  io.adder_out := majority(
    alu0.io.adder_out,
    alu1.io.adder_out,
    alu2.io.adder_out
  )

  // Vote on comparison result
  io.cmp_out :=
    (alu0.io.cmp_out && alu1.io.cmp_out) ||
    (alu0.io.cmp_out && alu2.io.cmp_out) ||
    (alu1.io.cmp_out && alu2.io.cmp_out)

  // Simulation checks for injected-fault mode
  if (injectFault) {
    assert(alu0Out =/= alu1.io.out,
      "Fault injection was not applied")

    assert(io.out === alu1.io.out,
      "TMR majority voting failed")
  }
}

