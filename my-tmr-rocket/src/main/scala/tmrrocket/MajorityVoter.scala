package tmrrocket

import chisel3._

class MajorityVoter(width: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(3, UInt(width.W)))
    val out = Output(UInt(width.W))
  })

  io.out := (io.in(0) & io.in(1)) |
            (io.in(0) & io.in(2)) |
            (io.in(1) & io.in(2))
}
