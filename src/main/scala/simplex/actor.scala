package simplex

import chisel3._
import chisel3.util._

class actor_module(val W: Int = 64, val row_size: Int = 6, val coefficient_n: Int = 2, val precision: Int = 10000, val row_id: Int = 0)
extends Module {
  val io = IO(new Bundle {
    // Step 1. Row data
    val message = Flipped(Decoupled((Vec(row_size, SInt(W.W)))))
    val busy = Output(Bool())
    
    // Step 2. Send min
    val send_min_index = Decoupled(UInt(W.W))
    val send_min_value = Output(SInt(W.W))

    // Step 3. Send ratio
    val ratio_bits = Output(SInt(W.W))
    val ratio_ready = Input(Bool())
    val ratio_valid = Output(UInt(W.W))

    // Step 4. Pivot the pivot row
    val step_4_ready = Input(Bool())
    val step_4_valid = Output(Bool())

    // Step 5. Elementar
    val step_5_ready = Input(Bool())
    val step_5_valid = Output(UInt(W.W))
    val pivot_row = Input(Vec(row_size, SInt(W.W)))

    // Get row data
    val get_data = Decoupled((Vec(row_size, SInt(W.W))))

    // Used throughout
    val pivot_col = Input(UInt(W.W))

    // Debug commands
    val test = Input(Bool())
    val test_valid = Output(Bool())
  })

  // Helper functions
  def min(s1: SInt, s2: SInt): SInt = Mux(s1 < s2, s1, s2)

  // Main async mem
  // This seems kind of silly, should I use a reg? SyncReadMem?
  val row_data = Mem(1, Vec(row_size, SInt(W.W)))

  // Default
  io.test_valid := false.B
  io.send_min_index.valid := false.B
  io.send_min_index.bits := DontCare
  io.send_min_value := DontCare
  io.ratio_valid := 0.U
  io.ratio_bits := DontCare
  io.step_4_valid := false.B
  io.step_5_valid := 0.U
  io.get_data.valid := false.B
  io.get_data.bits := DontCare

  // FSM
  val s_idle :: s_read :: s_test :: send_min_index :: compute_ratio :: step_4 :: step_5 :: get_data :: Nil = Enum(8)
  val state = RegInit(init = s_idle)
  io.busy := state =/= s_idle
  io.message.ready := false.B

  when (state === s_idle) {
    io.message.ready := true.B
    when (io.message.valid) {
      state := s_read
    }

    when (io.send_min_index.ready) {
      state := send_min_index
    }

    when (io.ratio_ready) {
      state := compute_ratio
    }

    when (io.step_4_ready) {
      state := step_4
    }

    when (io.step_5_ready) {
      state := step_5
    }

    when (io.get_data.ready) {
      state := get_data
    }

    when (io.test) {
      state := s_test
    }

    // Give an update every idle cycle
    printf(p"Row ${row_id}: ${row_data.read(0.U)}\n")
  }

  when (state === s_read) {
    io.message.ready := true.B
    // row_data := io.message.bits
    for (i <- 0 until row_size) {
      row_data.write(0.U, io.message.bits)
    }
    state := s_idle
  }

  when (state === send_min_index) {
    // Only considers the coefficients
    val min_coef = Wire(SInt(W.W))
    val min_coef_index = Wire(UInt(W.W))
    min_coef := 0.S
    min_coef_index := 0.U

    min_coef := row_data.read(0.U).slice(0, coefficient_n).reduceLeft{(v1, v2) => min(v1, v2)}
    io.send_min_index.bits := row_data.read(0.U).indexWhere((p) => p === min_coef)
    io.send_min_value := min_coef
    io.send_min_index.valid := true.B
    state := s_idle
  }

  when (state === compute_ratio) {
    // the ratio is the last column divided by the pivot column
    printf(p"Pivot col in row ${row_id}: ${io.pivot_col}\n")
    io.ratio_bits := (row_data.read(0.U)(row_size.U-1.U)*precision.S)/row_data.read(0.U)(io.pivot_col)
    io.ratio_valid := 1.U
    state := s_idle
  }

  when (state === step_4) {
    // the ratio is the last column divided by the pivot column
    val row_data_s4 = Wire(Vec(row_size, SInt(W.W)))
    for (i <- 0 until row_size) {
      row_data_s4(i) := (row_data.read(0.U)(i)*precision.S)/row_data.read(0.U)(io.pivot_col)
    }
    row_data.write(0.U, row_data_s4)
    io.step_4_valid := true.B
    state := s_idle
  }

  when (state === get_data) {
    io.get_data.bits := row_data.read(0.U)
    io.get_data.valid := true.B
    state := s_idle
  }

  when (state === step_5) {
    val row_data_s5 = Wire(Vec(row_size, SInt(W.W)))
    val row_data_pivot_col = Wire(SInt(W.W))
    row_data_pivot_col := row_data.read(0.U)(io.pivot_col)
    printf(p"Row ${row_id} io.pivot_row: ${io.pivot_row}\n")
    printf(p"Row ${row_id} io.pivot_col: ${io.pivot_col}\n")
    for (i <- 0 until row_size) {
      row_data_s5(i) := row_data.read(0.U)(i) - (row_data.read(0.U)(io.pivot_col)*io.pivot_row(i))/precision.S
    }
    row_data.write(0.U, row_data_s5)
    io.step_5_valid := 1.U
    state := s_idle
  }

  when (state === s_test) {
    io.test_valid := true.B
    val tmp = Wire(Vec(row_size, SInt(W.W)))
    for (i <- 0 until row_size) {
      tmp(i) := 999.S
    }
    row_data.write(0.U, tmp)
    state := s_idle
  }
}
