# ROBUST STABLE MATCHING

The code for paper "Preprocessing Algorithms and a Constraint Programming Model for the Robust Stable Marriage Problem" submitted to JAIR.


# PROJECT ARCHITECTURE

## benchmarks package

The benchmarks packages includes the following files:
- mapel_230_n100.zip: The instances on which experiments were conducted.
- mapel_230_n100_result.zip: The results of the experiments.
- Benchmark.java: The script for running the experiments with the different capacity values.


## manyToMany package

The manyToMany package contains all the models and algorithms related to the Robust Stable Marriage Problem (RSM) generalised to the many-to-many instances.

- ProcessInstance.java: This file contains the algorithms that allow to get M_0, M_Z and the directed graph of rotations.

- CPInstance.java: This file contains the preprocessing algorithms presented in the submitted paper.

- CPModel.java: This file contains the CP model solving the RSM problem for many-to-many instances.

- LocalSearch.java: This file contains the state-of-the-art local search algorithm to solve RSM, adapted to many-to-many instances.

- ReducedLocalSearch.java: This file contains the LS algorithm leveraging our preprocessing algorithms.

## propagators package

The propagator package contains the propagators of constraints specifically developped for the RSM problem and that are not initially implemented in choco-solver.




