package simplex

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

object directorMain extends App {
  iotesters.Driver.execute(args, () => new director_module) {
    c => new DirectorTester(c)
  }
}

class DirectorTester(c: director_module) extends PeekPokeTester(c) {
	val prec = 10000
	// Define an toy example
  // Note that data will need to be padded to match accelerator size

  // 2 coefs 3 constraints
  val toy = Array( 2, 1, 1, 0, 0, 18,
                   2, 3, 0, 1, 0, 26,
                   3, 1, 0, 0, 1, 25,
                  -3,-2, 0, 0, 0,  0)

  /*
  // 16x16
  // Same problem as above with padding
  // So the accelerator size can stay fixed and the problem can just be padded
  // This requires no extra cycles, but is pretty heavy to run in sim
  // Obviously the amount of wiring increases exponentially with the number of rows supported by the accel
  // This will probably be the main limiting factor 
  val toy = Array(2,  1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 18,
                  2,  3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 26,
                  3,  1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 25,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0,
                  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0,
                 -3, -2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
  */

  var pos = 0
	var count = 0

	poke(c.io.optimal_value.ready, false)

  // Send datato the director
  printf("Loading data...\n")
  for (i <- 0 until 4) {
    for (j <- 0 until 6) { 
      poke(c.io.message.bits(i)(j), toy(pos)*prec)
      pos = pos + 1
    }
  }
  poke(c.io.message.valid, true)
	count = count + 1
	printf("---------- iter %d ----------\n", count)
  step(1)
  while (peek(c.io.busy) == 1) {
    count = count + 1
		printf("---------- iter %d ----------\n", count)
		step(1)
  }

  printf("Data loaded....\n")
  printf("Turning off message valid signal\n")
  poke(c.io.message.valid, false)

	count = count + 1
	printf("---------- iter %d ----------\n", count)
  step(1)

	printf("Starting simplex\n")
	// The simplex signal does not need to stay high
	poke(c.io.start_simplex, true)
	count = count + 1
	printf("---------- iter %d ----------\n", count)
	step(1)
	poke(c.io.start_simplex, false)

  while(peek(c.io.optimal_value.valid) == 0 & count < 35) {
		count = count + 1
		printf("---------- iter %d ----------\n", count)
		step(1)
  }
	
  // Get the optimal value
  printf("Getting optimal value\n")
  count = count + 1
	printf("---------- iter %d ----------\n", count)
  step(1)
  poke(c.io.optimal_value.ready, true)
  printf("Optimal value: %f\n", (peek(c.io.optimal_value.bits).toFloat)/prec)
  
  // Let the accelerator move back to the idle state
  count = count + 1
  printf("---------- iter %d ----------\n", count)
  step(1)

  count = count + 1
  printf("---------- iter %d ----------\n", count)
  step(1)
  
}