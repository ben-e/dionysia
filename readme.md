# Dionysia

Dionysia is a hardware implementation of the Simplex algorithm for solving linear programming problems. This project was produced as part of Duke CS 590.03 "Compute Architecture and Hardware Acceleration" with [Prof. Lisa Wills Wu](https://www.lisawuwills.com/). It is implemented using [Chisel](https://github.com/freechipsproject/chisel3), a hardware description language embedded in Scala. 

## Motivation

Having never formally studied hardware I wanted to pursue a project that would be both feasible and potentially useful. Linear programming has [broad applications](https://github.com/neilpruthi/ad_knap) and many software solutions (e.g. CPLEX, GLPK, Gurobi), but relatively few hardware solutions ([1](https://ieeexplore.ieee.org/abstract/document/4042415), [2](http://users.uom.gr/~samaras/pdf/J34.pdf)). While there are now faster algorithms for solving LP problems, the Simplex algorithm is wonderfully simple and consistent. I highly recommend reading [George Dantzig's account of its development](https://www.sciencedirect.com/science/article/abs/pii/0167637782900438). 

## Overview

The full paper can be found [here]().

### The Simplex Algorithm

Linear programs are optimization problems with linear constraints and a linear objective function. For example:

\begin{align}
\label{lp_2}
&\text{Maximize}   & \mathbf{c}^T\mathbf{x} \nonumber\\
&\text{subject to} & \mathbf{Ax} \leq \mathbf{b} \\
&                  & \mathbf{x} \geq 0 \nonumber.
\end{align}

Where $\mathbf{x}$ are the variables, $\mathbf{c}$ are the coefficients for the objective function, $\mathbf{A}$ are the coefficients for the constraints, and $\mathbf{b}$ are the constraint values. The Simplex algorithm represents this problem as a matrix, known as the Simplex tableau:

\begin{equation}\label{ex_tableau1}
\begin{bmatrix}
\mathbf{A} & \mathbf{I} & \mathbf{b} \\
\mathbf{c} & \mathbf{0} & 0 \nonumber
\end{bmatrix}
\end{equation}

The identity matrix $\mathbf{I}$ represents the [slack variables](https://en.wikipedia.org/wiki/Slack_variable) which are added to the problem. The bottom row will be referred to as the objective row.

The Simplex algorithm consists of four steps. 

0. Repeat until now pivot row or pivot column can be found:
    1. Choose the pivot column by searching for the minimum value along the objective row.
    2. Choose the pivot row to be the row with the minimum positive ratio between the pivot column and constraint column.
    3. Divide the pivot row by the pivot element.
    4. For each row $i$, take the difference to be row $i$ minus the pivot column (a scalar; the element at row $i$) multiplied by the pivot row.

### Dionysia

Dionysia consists of two types of modules: actors and directors. Each instantiation of Dionysia consists of many actors and a single director. The director module is the I/O and control module. It reads the Simplex tableau from the host, issues instructions to the actor modules, and monitors the algorithm for stopping conditions. Each actor module holds a row of the Simplex tableau, and implements each of the Simplex four steps. Actors operate completely in parallel and relatively little data needs to be moved.

## Results

Dionysia is very fast, but only for very small problems (on the order of 32 variables and 32 constraitns). The size of LP problem that Dionysia can solve is limited by the number of actors that can be instantiated on a given FPGA. This is because Dionysia keeps the entire Simplex tableau on-FPGA and makes no use of the FPGA's DRAM (beyond the initial loading of data). Future work will involve modifying the actor modules to be more memory-efficient (i.e. using actors that hold multiple rows of the Simplex tableau), and making use of the FPGA's DRAM.

## Use

The contents of this repository are sufficient to generate [FIRRTL](https://freechipsproject.github.io/firrtl/) and Verilog and simulate Dionysia with VCS or the [FIRRTL Interpreter](https://github.com/freechipsproject/firrtl-interpreter). The tests folder contains a simple tester used to validate Dionysia for small LP problems. 

A separate project exists to deploy Dionysia on the [AWS EC2 F1](https://aws.amazon.com/ec2/instance-types/f1/) platform, however this work represents ongoing (under wraps) research.

## The Name

Dionysia is a relatively strange name for this project. It comes from [The Dionysia](https://en.wikipedia.org/wiki/Dionysia), an ancient Greek festival centered around theater and the God Dionysis. Given that this hardware uses the [actor concurrency model](https://en.wikipedia.org/wiki/Actor_model), the theatrical theme seemed appropriate.