package org.example.manyToMany;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.SetVar;
import org.chocosolver.util.objects.graphs.DirectedGraph;
import org.example.propagators.PropClosedSubSet;
import org.example.propagators.PropIndexDisjointUnion;
import org.example.propagators.PropSetDifference;
import org.javatuples.Pair;

import java.util.*;

public class CPModel {

    public static float[] run_naive_model(int number_couples, int number_rotation, DirectedGraph poSet, Map<Pair<Integer, Integer>, Pair<Integer, Integer>> rotationOX, int[][] rotationGeneratedCouples, int[][] rotationEliminatedCouples, int[][] S_up, int[][] S_down, int[] M_LB, int[] M_UB, int b_LB, int b_UB, int b_threshold, String timeLimit) {
        // DATA

        int[] rotations = new int[number_rotation];
        for (int r = 0; r < number_rotation; r++) {
            rotations[r] = r;
        }
        int[] couples = new int[number_couples];
        for (int r = 0; r < number_couples; r++) {
            couples[r] = r;
        }
        int number_stable_pairs = rotationOX.size();
        Map<Pair<Integer, Integer>, Integer> stable_pair_index = new HashMap<>();
        int s = 0;
        for (Pair<Integer, Integer> pair : rotationOX.keySet()){
            stable_pair_index.put(pair, s);
            s++;
        }


        // MODEL

        Model model = new Model("SM_(1,b)_robust");


        // VARIABLES

        SetVar M = model.setVar("stable_matching", M_LB, M_UB);

        SetVar[] S_up_var = new SetVar[number_rotation];
        SetVar[] S_down_var = new SetVar[number_rotation];
        for (int r = 0; r < number_rotation; r++) {
            S_up_var[r] = model.setVar(S_up[r], S_up[r]);
            S_down_var[r] = model.setVar(S_down[r], S_down[r]);
        }

        SetVar[] couples_up = model.setVarArray(number_rotation, new int[]{}, couples);
        SetVar[] couples_down = model.setVarArray(number_rotation, new int[]{}, couples);

        SetVar[] generated_up = model.setVarArray(number_rotation, new int[]{}, couples);
        SetVar[] generated_down = model.setVarArray(number_rotation, new int[]{}, couples);

        SetVar[] eliminated_up = model.setVarArray(number_rotation, new int[]{}, couples);
        SetVar[] eliminated_down = model.setVarArray(number_rotation, new int[]{}, couples);

        SetVar[] sym_diff_up = model.setVarArray(number_rotation, new int[]{}, rotations);
        SetVar[] sym_diff_down = model.setVarArray(number_rotation, new int[]{}, rotations);


        // VIEWS

        IntVar[] distance_up = new IntVar[number_rotation];
        IntVar[] distance_down = new IntVar[number_rotation];
        for (int r = 0; r < number_rotation; r++) {
            distance_up[r] = couples_up[r].getCard();
            distance_down[r] = couples_down[r].getCard();
        }

        IntVar[] cost = new IntVar[number_stable_pairs];
        for (Pair<Integer,Integer> p : rotationOX.keySet()) {
            s = stable_pair_index.get(p);
            if (rotationOX.get(p).getValue0() == null) {
                int rho_e = rotationOX.get(p).getValue1();
                cost[s] = distance_down[rho_e];
            }
            else if (rotationOX.get(p).getValue1() == null) {
                int rho_p = rotationOX.get(p).getValue0();
                cost[s] = distance_up[rho_p];
            }
            else {
                int rho_p = rotationOX.get(p).getValue0();
                int rho_e = rotationOX.get(p).getValue1();
                cost[s] = model.min("cost_{s}", distance_up[rho_p], distance_down[rho_e]);
            }
        }


        // CONSTRAINTS

        for (int r = 0; r < number_rotation; r++) {
            model.post(new Constraint("SymDiff_up_[" + r + "]", new PropSetDifference(sym_diff_up[r], M, S_up_var[r])));
            model.post(new Constraint("SymDiff_down_[" + r + "]", new PropSetDifference(sym_diff_down[r], S_down_var[r], M)));
        }


        for (int r = 0; r < number_rotation; r++) {
            model.post(new Constraint("IndexUnion_up_{r}", new PropIndexDisjointUnion(sym_diff_up[r], generated_up[r], rotationGeneratedCouples, false)));
            model.post(new Constraint("IndexUnion_down_{r}", new PropIndexDisjointUnion(sym_diff_down[r], generated_down[r], rotationGeneratedCouples, false)));
        }

        for (int r = 0; r < number_rotation; r++) {
            model.post(new Constraint("IndexUnion_up_{r}", new PropIndexDisjointUnion(sym_diff_up[r], eliminated_up[r], rotationEliminatedCouples, false)));
            model.post(new Constraint("IndexUnion_down_{r}", new PropIndexDisjointUnion(sym_diff_down[r], eliminated_down[r], rotationEliminatedCouples, false)));
        }

        for (int r = 0; r < number_rotation; r++) {
            model.post(new Constraint("Diff_up_{r}", new PropSetDifference(couples_up[r], generated_up[r], eliminated_up[r])));
            model.post(new Constraint("Diff_down_{r}", new PropSetDifference(couples_down[r], generated_down[r], eliminated_down[r])));
        }


        model.post(new Constraint("closedSubset", new PropClosedSubSet(M, poSet)));


        // OBJECTIVE

        IntVar b = model.intVar("b", b_LB, b_UB);
        model.arithm(b, "=", model.max("max_cost", cost), "-", 1).post();
        model.setObjective(Model.MINIMIZE, b);


        //SOLVE

        Solver slv = model.getSolver();
        if (timeLimit != "none"){
            slv.limitTime(timeLimit);
        }
        slv.setSearch(Search.setVarSearch(M));
        // slv.showShortStatistics();
        // slv.printShortFeatures();


        if (b_threshold > 0) {
            float time_threshold = 0;
            while (slv.solve()) {
                if ((int) slv.getBestSolutionValue() <= b_threshold && time_threshold == 0) {
                    time_threshold = slv.getTimeToBestSolution();
                }
            }
            float[] output = {slv.getBestSolutionValue().floatValue(), slv.getTimeToBestSolution(), model.getNbVars(), slv.getNodeCount(), time_threshold};
            return output;
        }
        else {
            slv.findOptimalSolution(b, false);
            float optimality = 1;
            if (slv.isObjectiveOptimal()) {optimality = 0;}
            float[] output = {slv.getBestSolutionValue().floatValue(), slv.getTimeToBestSolution(), model.getNbVars(), slv.getNodeCount(), 0, optimality};
            return output;
        }
    }

    public static float[] run_model_with_reduced_variables(int number_couples, int number_rotation, DirectedGraph poSet, Map<Pair<Integer, Integer>, Pair<Integer, Integer>> reducedOX, int[][] rotationGeneratedCouples, int[][] rotationEliminatedCouples, int[][] S_up, int[][] S_down, int[] rotation_up, int[] rotation_down, int[] M_LB, int[] M_UB, int b_LB, int b_UB, int b_threshold) {
        // DATA
        int[] rotations = new int[number_rotation];
        for (int r = 0; r < number_rotation; r++) {
            rotations[r] = r;
        }
        int[] couples = new int[number_couples];
        for (int r = 0; r < number_couples; r++) {
            couples[r] = r;
        }
        int number_stable_pairs = reducedOX.size();
        Map<Pair<Integer, Integer>, Integer> stable_pair_index = new HashMap<>();
        int s = 0;
        for (Pair<Integer, Integer> pair : reducedOX.keySet()){
            stable_pair_index.put(pair, s);
            s++;
        }
        int number_rotation_up = rotation_up.length;
        int number_rotation_down = rotation_down.length;


        // MODEL

        Model model = new Model("SM_(1,b)_robust");


        // VARIABLES

        SetVar M = model.setVar("stable_matching", M_LB, M_UB);

        SetVar[] S_up_var = new SetVar[number_rotation_up];
        for (int r = 0; r < number_rotation_up; r++) {
            S_up_var[r] = model.setVar(S_up[rotation_up[r]], S_up[rotation_up[r]]);
        }
        SetVar[] S_down_var = new SetVar[number_rotation_down];
        for (int r = 0; r < number_rotation_down; r++) {
            S_down_var[r] = model.setVar(S_down[rotation_down[r]], S_down[rotation_down[r]]);
        }

        SetVar[] couples_up = model.setVarArray(number_rotation_up, new int[]{}, couples);
        SetVar[] couples_down = model.setVarArray(number_rotation_down, new int[]{}, couples);

        SetVar[] generated_up = model.setVarArray(number_rotation_up, new int[]{}, couples);
        SetVar[] generated_down = model.setVarArray(number_rotation_down, new int[]{}, couples);

        SetVar[] eliminated_up = model.setVarArray(number_rotation_up, new int[]{}, couples);
        SetVar[] eliminated_down = model.setVarArray(number_rotation_down, new int[]{}, couples);

        SetVar[] sym_diff_up = model.setVarArray(number_rotation_up, new int[]{}, rotations);
        SetVar[] sym_diff_down = model.setVarArray(number_rotation_down, new int[]{}, rotations);


        // VIEWS

        IntVar[] distance_up = new IntVar[number_rotation_up];
        for (int r = 0; r < number_rotation_up; r++) {
            distance_up[r] = couples_up[r].getCard();
        }

        IntVar[] distance_down = new IntVar[number_rotation_down];
        for (int r = 0; r < number_rotation_down; r++) {
            distance_down[r] = couples_down[r].getCard();
        }

        IntVar[] cost = new IntVar[number_stable_pairs];
        for (Pair<Integer,Integer> p : reducedOX.keySet()) {
            s = stable_pair_index.get(p);
            if (reducedOX.get(p).getValue0() == null) {
                int rho_e = reducedOX.get(p).getValue1();
                cost[s] = distance_down[rho_e];
            }
            else if (reducedOX.get(p).getValue1() == null) {
                int rho_p = reducedOX.get(p).getValue0();
                cost[s] = distance_up[rho_p];
            }
            else {
                int rho_p = reducedOX.get(p).getValue0();
                int rho_e = reducedOX.get(p).getValue1();
                cost[s] = model.min("cost_{s}", distance_up[rho_p], distance_down[rho_e]);
            }
        }


        // CONSTRAINTS

        for (int r = 0; r < number_rotation_up; r++) {
            model.post(new Constraint("IndexUnion_up_{r}", new PropIndexDisjointUnion(sym_diff_up[r], generated_up[r], rotationGeneratedCouples, false)));
        }

        for (int r = 0; r < number_rotation_down; r++) {
            model.post(new Constraint("IndexUnion_down_{r}", new PropIndexDisjointUnion(sym_diff_down[r], generated_down[r], rotationGeneratedCouples, false)));
        }

        for (int r = 0; r < number_rotation_up; r++) {
            model.post(new Constraint("IndexUnion_up_{r}", new PropIndexDisjointUnion(sym_diff_up[r], eliminated_up[r], rotationEliminatedCouples, false)));
        }

        for (int r = 0; r < number_rotation_down; r++) {
            model.post(new Constraint("IndexUnion_down_{r}", new PropIndexDisjointUnion(sym_diff_down[r], eliminated_down[r], rotationEliminatedCouples, false)));
        }

        for (int r = 0; r < number_rotation_up; r++) {
            model.post(new Constraint("Couples_up_[" + r + "]", new PropSetDifference(couples_up[r], generated_up[r], eliminated_up[r])));
        }

        for (int r = 0; r < number_rotation_down; r++) {
            model.post(new Constraint("Couples_down_[" + r + "]", new PropSetDifference(couples_down[r], generated_down[r], eliminated_down[r])));
        }

        for (int r = 0; r < number_rotation_up; r++) {
            model.post(new Constraint("SymDiff_up_[" + r + "]", new PropSetDifference(sym_diff_up[r], M, S_up_var[r])));
        }

        for (int r = 0; r < number_rotation_down; r++) {
            model.post(new Constraint("SymDiff_down_[" + r + "]", new PropSetDifference(sym_diff_down[r], S_down_var[r], M)));
        }


        model.post(new Constraint("closedSubset", new PropClosedSubSet(M, poSet)));


        // OBJECTIVE
        IntVar b = model.intVar("b", b_LB, b_UB);
        model.arithm(b, "=", model.max("max_cost", cost), "-", 1).post();
        model.setObjective(Model.MINIMIZE, b);


        //SOLVE
        Solver slv = model.getSolver();
        slv.setSearch(Search.setVarSearch(M));
//        slv.showShortStatistics();
//        slv.printShortFeatures();

        if (b_threshold > 0) {
            float time_threshold = 0;
            while (slv.solve()) {
                if ((int) slv.getBestSolutionValue() <= b_threshold && time_threshold == 0) {
                    time_threshold = slv.getTimeToBestSolution();
                }
            }
            float[] output = {slv.getBestSolutionValue().floatValue(), slv.getTimeToBestSolution(), model.getNbVars(), slv.getNodeCount(), time_threshold};
            return output;
        }
        else {
            slv.findOptimalSolution(b, false);
            float[] output = {slv.getBestSolutionValue().floatValue(), slv.getTimeToBestSolution(), model.getNbVars(), slv.getNodeCount(), 0};
            return output;
        }
    }
}
