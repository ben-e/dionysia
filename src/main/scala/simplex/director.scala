package simplex

import chisel3._
import chisel3.util._

/* Notes: 
  * constraint_n and coefficient_n are fixed, but Dionysia can work on any
    problem that has appropriate padding. For example:
     2,  1,  0, 1, 0, 0, 10,
     0,  2,  1, 0, 1, 0, 20,
     1,  0,  2, 0, 0, 1, 30,
    -3, -4, -5, 0, 0, 0, 00
    and 
     2,  1, 0, 0, 0, 1, 0, 0, 0, 0, 18,
     2,  3, 0, 0, 0, 0, 1, 0, 0, 0, 26,
     3,  1, 0, 0, 0, 0, 0, 1, 0, 0, 25,
     0,  0, 0, 0, 0, 0, 0, 0, 1, 0, 00,
     0,  0, 0, 0, 0, 0, 0, 0, 0, 1, 00,
    -3, -2, 0, 0, 0, 0, 0, 0, 0, 0, 00
    are equivalent problems.
  * row number is assumed to be constraint_n + 1
  * column number (i.e. row size) is assumed to be 
    constraint_n + coefficient_n + 1
*/

class director_module(val W: Int = 64, val constraint_n: Int = 3, val coefficient_n: Int = 2, val precision: Int = 10000)
extends Module {
  val row_num = constraint_n + 1
  val row_size = constraint_n + coefficient_n + 1

  val io = IO(new Bundle {
    // Message contains the data  
    val message = Flipped(new DecoupledIO(Vec(row_num, Vec(row_size, SInt(W.W)))))

    // Busy indicator
    val busy = Output(Bool())
    // Start indicator
    val start_simplex = Input(Bool())
    // Value of the optimal solution
    val optimal_value = Decoupled(SInt(W.W))

    // Debug commands
    val test = Input(Bool())
    val test_row = Input(UInt(W.W))
  })

  // Helper functions
  def sum(s1: SInt, s2: SInt): SInt = s1 + s2
  def max(s1: SInt, s2: SInt): SInt = Mux(s1 > s2, s1, s2)
  def min(s1: SInt, s2: SInt): SInt = Mux(s1 < s2, s1, s2)
  def clipped_min(s1: SInt, s2: SInt, min_ex: SInt): SInt = Mux(s1 < s2, Mux(s1 > min_ex, s1, s2), Mux(s2 > min_ex, s2, s1))

  // Data passes through the director to actors
  val actor_data_in = Reg(Vec(row_num, Vec(row_size, SInt(W.W))))

  // Other data
  val pivot_col = Mem(1, UInt(W.W))
  val pivot_row = Mem(1, UInt(W.W))

  // Default values
  io.optimal_value.valid := false.B
  io.optimal_value.bits := 0.S

  // Define modules
  // Adapted from https://stackoverflow.com/questions/33621533/how-to-do-a-vector-of-modules
  val actors = for (i <- 0 until row_num) yield {
    val actor = Module(new actor_module(W, row_size, coefficient_n, precision, i))
    actor.io.message.bits := actor_data_in(i)
    actor.io.message.valid := false.B
    actor.io.send_min_index.ready := false.B
    actor.io.ratio_ready := false.B
    actor.io.step_4_ready := false.B
    actor.io.step_5_ready := false.B
    actor.io.pivot_col := 0.U
    actor.io.pivot_row := DontCare
    actor.io.get_data.ready := false.B
    actor.io.test := false.B
    actor
  }

  // FSM
  val s_idle :: s_read :: s_s2 :: s_s3 :: s_s4 :: s_s5 :: s_write :: s_test :: Nil = Enum(8)
  val state = RegInit(init = s_idle)
  io.busy := state =/= s_idle
  io.message.ready := false.B

  when (state === s_idle) {
    io.message.ready := true.B
    when (io.message.valid) {
      state := s_read
    }

    when (io.start_simplex) {
      state := s_s2
    }

    when (io.test) {
      state := s_test
    }
  }

  // Step 1. Read data into rows.
  when (state === s_read) {
    io.message.ready := true.B
    printf("Step 1\n")
    for(i <- 0 until row_num) {
      when (io.message.valid && actors(i).io.message.ready) {
        actor_data_in(i) := io.message.bits(i)
        actors(i).io.message.valid := true.B
      }
    }
    state := s_idle
  }

  // Step 2. Find pivot column
  when (state === s_s2) {
    // The pivot column is the column on the bottom row with the min value
    // Only including the coefficients
    actors(row_num - 1).io.send_min_index.ready := true.B
    when (actors(row_num - 1).io.send_min_index.valid) {
      when (actors(row_num - 1).io.send_min_value >= 0.S) {
        printf("No pivot column found!\n")
        state := s_write
      } .otherwise {
        pivot_col.write(0.U, actors(row_num - 1).io.send_min_index.bits)
        // Wait for the write to take place
        when (pivot_col.read(0.U) === actors(row_num - 1).io.send_min_index.bits) {
          state := s_s3
        } .otherwise {
          printf("Waiting for pivot_col write..\n")
          state := s_s2
        }
      }
    } .otherwise {
      state := s_s2
    }
  }

  // Step 3. Find pivot row
  val ratios = Wire(Vec(row_num - 1, SInt(W.W)))
  val ratios_valid = Wire(Vec(row_num - 1, UInt(W.W)))
  for (i <- 0 until (row_num-1)) {
    ratios(i) := 0.S
    ratios_valid(i) := 0.U
  }
  when (state === s_s3) {
    printf(p"pivot_col: ${pivot_col.read(0.U)}\n")

    // Tell all rows (but the constraint row) to compute ratios
    for(i <- 0 until (row_num-1)) {
      actors(i).io.ratio_ready := true.B
      actors(i).io.pivot_col := pivot_col.read(0.U)
      when (actors(i).io.ratio_valid === 1.U) {
        printf(p"ratio ${i}: ${actors(i).io.ratio_bits}\n")
        ratios(i) := actors(i).io.ratio_bits
        ratios_valid(i) := actors(i).io.ratio_valid
      }
    }

    // Some useful registers
    val max_ratio = Wire(SInt(W.W))
    val min_ratio = Wire(SInt(W.W))
    val min_ratio_index = Wire(UInt(W.W))
    min_ratio := 0.S
    min_ratio_index := 0.U
    max_ratio := 0.S

    // Once all ratios are loaded find the min one
    // This requirement should keep the director in this state while register are being written
    when (ratios_valid.reduce(_ + _) === row_num.U - 1.U) {
      max_ratio := ratios.reduceLeft{(acc, value) => max(acc, value)}
      // If all are non-posituve then we are done
      when (max_ratio <= 0.S) {
        printf("No pivot row found!\n")
        state := s_write
      } .otherwise {
        // Otherwise choose the pivot row
        // We want the min positive ratio
        min_ratio := ratios.reduceLeft{(a, b) => clipped_min(a, b, 0.S)}
        min_ratio_index := ratios.indexWhere((p) => p === min_ratio)
        // clipped_min might not be very robust, but because  we only call it after
        // verifying there is at least one valid response it should be safe here
        pivot_row.write(0.U, min_ratio_index)
        printf(p"min_ratio: ${min_ratio}\n")
        printf(p"min_ratio_index: ${min_ratio_index}\n")

        // Wait for pivot row to write
        when (pivot_row.read(0.U) === min_ratio_index) {
          state := s_s4
        } .otherwise {
          printf("Waiting for pivot row to write\n")
          state := s_s3
        }
      }
    } . otherwise {
      // stay in this state if we are waiting for the registers to write
      state := s_s3
    }
  }

  // Step 4. Pivot the pivot row
  when (state === s_s4) {
    printf(p"pivot_col: ${pivot_col.read(0.U)}\n")
    printf(p"pivot_row: ${pivot_row.read(0.U)}\n")

    for (i <- 0 until row_num) {
      when (i.U === pivot_row.read(0.U)) {
          // printf(p"Pivot row: ${pivot_row}\n")
          actors(i).io.step_4_ready := true.B
          actors(i).io.pivot_col := pivot_col.read(0.U)
          // Wait for pivot to complete
          when(actors(i).io.step_4_valid) {
            state := s_s5
          } .otherwise {
            state := s_s4
        }
      }
    }
  }

  // Step 5. Elementar
  // Sending the pivot row data seems to cause a large amount of wiring to happen
  // this step could definitely be optimized
  val elementar_valid = Wire(Vec(row_num, UInt(W.W)))
  for (i <- 0 until row_num) {
    elementar_valid(i) := 0.U
  }
  val pivot_data = Wire(Vec(row_size, SInt(W.W)))
  for (i <- 0 until row_size) {
    pivot_data(i) := 0.S
  }

  when (state === s_s5) {
    for(i <- 0 until row_num) {
      when (i.U =/= pivot_row.read(0.U)) {
        actors(i).io.step_5_ready := true.B
        actors(i).io.pivot_row := pivot_data
        actors(i).io.pivot_col := pivot_col.read(0.U)
        elementar_valid(i) := actors(i).io.step_5_valid
      } .otherwise {
        actors(i).io.get_data.ready := true.B
        when (actors(i).io.get_data.valid) {
          pivot_data := actors(i).io.get_data.bits
        }
      }
    }
    when (elementar_valid.reduce(_ + _) === row_num.U - 1.U) {
      printf("Done with elementar\n")
      // Repeat until one of the steps fails
      state := s_s2
    } .otherwise {
      state := s_s5
    }
  }

  when (state === s_write) {
    // Let the host know the optimal value is valid
    io.optimal_value.valid := true.B

    // Get the optimal value from the bottom row
    actors(row_num - 1).io.get_data.ready := true.B
    // When the host is ready, and the row is ready, send the value
    when (io.optimal_value.ready && actors(row_num - 1).io.get_data.valid) {
      io.optimal_value.bits := actors(row_num - 1).io.get_data.bits(row_size - 1)
      // And go backt to the idle state
      state := s_idle
    } .otherwise {
      state := s_write
    }
  }

  when (state === s_test) {
    for (i <- 0 until row_num) {
        when (i.U === io.test_row) {
          actors(i).io.test := true.B
          when(actors(i).io.test_valid) {
            state := s_idle
          } .otherwise {
            state := s_test
          }
        }
      }
    }
  // Seems to be off by a cycle on ocassion?
  printf(p"Director state: ${state}\n")
}
