package org.example.manyToMany;

import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class CPInstance {
    public ProcessInstance instance;
    public int numberCouples;
    public int numberRotations;
    public boolean runNaive;
    public boolean reducedVariables;
    public int[] reducedRotation_up;
    public int[] reducedRotation_down;
    public int[] reverseReducedRotation_up;
    public int[] reverseReducedRotation_down;
    public int NoDeletedUp;
    public int NoDeletedDown;
    public Map<Pair<Integer, Integer>, Pair<Integer, Integer>> reducedRotationOX;
    public int[] SuperstableLB;
    public int[] SuperstableUB;
    public int OptLB;
    public int OptUB;
    public ArrayList<Integer> lowerMedianUpDown;
    public int lowerMedianB;
    public ArrayList<Integer> upperMedianUpDown;
    public int upperMedianB;
    public int M0b;
    public int MZb;
    public float optValue;
    public float timeBestSolution;
    public float NbVariables;
    public float NbNodes;
    public int b_threshold;
    public float time_threshold;
    public float preProcessingTwoTime;
    public float solveTime;
    public String timeLimit;
    public float chocoStatus;
    public String status;
    public boolean heuristicOptimal;

    /*
     * This class implements the preprocessing algorithms making the contributions of the submitted paper.
     * The implemented preprocessing algorithms are a simpler version than those presented in the paper. In particular, they do not implement the marking trick that reduces computation time.
     */

    public CPInstance(ProcessInstance instance) {
        this.instance = instance;
        this.numberCouples = instance.rotationOX.keySet().size();
        this.numberRotations = instance.metaRotations.size();
        int[] rotations = new int[instance.metaRotations.size()];
        for (int r = 0; r < instance.metaRotations.size(); r++) {
            rotations[r] = r;
        }

        this.SuperstableLB = new int[]{};
        this.SuperstableUB = rotations;
        this.OptLB = 1;
        this.OptUB = instance.malesN * instance.femalesN - 1;
        this.optValue = 0;
        this.timeBestSolution = 0;
        this.NbVariables = 0;
        this.NbNodes = 0;
        this.runNaive = false;
        this.reducedVariables = false;
        this.reducedRotation_up = new int[]{};
        this.reducedRotation_down = new int[]{};
        this.NoDeletedUp = 0;
        this.NoDeletedDown = 0;
        this.lowerMedianUpDown = new ArrayList<>();
        this.lowerMedianB = 0;
        this.upperMedianUpDown = new ArrayList<>();
        this.upperMedianB = 0;
        this.M0b = 0;
        this.MZb = 0;
        this.b_threshold = 0;
        this.time_threshold = 0;
        this.preProcessingTwoTime = 0;
        this.solveTime = 0;
        this.timeLimit = "none";
        this.chocoStatus = 0;
        this.status = "NOT SOLVED YET";
        this.heuristicOptimal = false;
    }

    public void solve() {
        float[] output;
        if (numberRotations == 0) {
            this.status = "THERE ARE NO ROTATIONS";
            System.out.println("There is a unique stable matching");
        }
        else if (runNaive) {
            optValue = 0;
            OptLB = 1;
            OptUB = instance.malesN * instance.femalesN - 1;
            long start = System.currentTimeMillis();
            output = CPModel.run_naive_model(numberCouples, numberRotations, instance.metaRotationPoset, instance.rotationOX, instance.rotationGeneratedCouples, instance.rotationEliminatedCouples, instance.Sup, instance.Sdown, this.SuperstableLB, this.SuperstableUB, this.OptLB, this.OptUB, this.b_threshold, this.timeLimit);
            this.solveTime = System.currentTimeMillis() - start;
            this.solveTime = this.solveTime / 1000;
            this.optValue = output[0];
            this.timeBestSolution = output[1];
            this.NbVariables = output[2];
            this.NbNodes = output[3];
            this.time_threshold = output[4];
            this.chocoStatus = output[5];
            this.status = "SOLVED WITHOUT PREPROCESSING";

        } else {
            optValue = 0;
            OptLB = 1;
            OptUB = instance.malesN * instance.femalesN - 1;
            long start = System.currentTimeMillis();
            reduceVariables();
            computeMO();
            computeMZ();
            computeMedianUpDown(false);
            computeMedianUpDown(true);
            reduceSearchSpace();
            if (lowerMedianB == OptLB || upperMedianB == OptLB || M0b == OptLB || MZb == OptLB || !(checkBoundValidity())) {
                if (M0b == OptLB || M0b - 1 == OptUB) {
//                    System.out.println("M_O is optimal");
                    optValue = M0b;
                    NbVariables = 0;
                    NbNodes = 0;
                }
                if (lowerMedianB == OptLB || lowerMedianB - 1 == OptUB) {
//                    System.out.println("Lower-median UP/DOWN is optimal");
                    optValue = lowerMedianB;
                    NbVariables = 0;
                    NbNodes = 0;
                }
                if (upperMedianB == OptLB || upperMedianB - 1 == OptUB) {
//                    System.out.println("Upper-median UP/DOWN is optimal");
                    optValue = upperMedianB;
                    NbVariables = 0;
                    NbNodes = 0;
                }
                if (MZb == OptLB || MZb - 1 == OptUB) {
//                    System.out.println("M_Z is optimal");
                    optValue = MZb;
                    NbVariables = 0;
                    NbNodes = 0;
                }
                this.preProcessingTwoTime = System.currentTimeMillis() - start;
                this.preProcessingTwoTime = preProcessingTwoTime / 1000;
                this.status = "A HEURISTIC IS OPTIMAL";
                this.heuristicOptimal = true;
            } else {
                this.preProcessingTwoTime = System.currentTimeMillis() - start;
                this.preProcessingTwoTime = preProcessingTwoTime / 1000;
                start = System.currentTimeMillis();
                output = CPModel.run_model_with_reduced_variables(this.numberCouples, this.numberRotations, instance.metaRotationPoset, this.reducedRotationOX, instance.rotationGeneratedCouples, instance.rotationEliminatedCouples, instance.Sup, instance.Sdown, this.reducedRotation_up, this.reducedRotation_down, this.SuperstableLB, this.SuperstableUB, this.OptLB, this.OptUB, this.b_threshold);
                this.solveTime = System.currentTimeMillis() - start;
                this.solveTime = solveTime / 1000;
                this.optValue = output[0];
                this.timeBestSolution = output[1];
                this.NbVariables = output[2];
                this.NbNodes = output[3];
                this.time_threshold = output[4];
                this.status = "SOLVED WITH CP AFTER PREPROCESSING";
            }
        }
    }

    public boolean checkBoundValidity() {
        for (int rho : SuperstableLB) {
            if (!(contains(SuperstableUB, rho))) {
                return false;
            }
        }
        return true;
    }

    public void computeMedianUpDown(boolean upper) {
        ArrayList<Integer> median = upper ? upperMedianUpDown : lowerMedianUpDown;
        median.clear();
        for (int rho = 0; rho < numberRotations; rho++) {
            if (instance.Sup[rho].length + instance.Sdown[rho].length < numberRotations + (upper ? 1 : 0)) {
                median.add(rho);
            }
        }
        int b = computeB(median);
        if (upper) {upperMedianB = b;} else {lowerMedianB = b;}
        OptUB = Math.min(b - 1, OptUB);
    }

    public void computeMO() {
        this.M0b = computeB(new ArrayList<>());
        if (M0b - 1 < OptUB) {OptUB = M0b - 1;}
    }

    public void computeMZ() {
        ArrayList<Integer> rotations = new ArrayList<>();
        for (int i = 0; i < numberRotations; i++) {
            rotations.add(i);
        }
        this.MZb = computeB(rotations);
        if (MZb - 1 < OptUB) {OptUB = MZb - 1;}
    }

    public int computeB (ArrayList<Integer> closedSubset) {
        int b = 0;
        int cost;
        for (Pair<Integer, Integer> pair : instance.rotationOX.keySet()) {
            cost = instance.malesN * instance.femalesN + 1;
            if (!(instance.rotationOX.get(pair).getValue0() == null) && reverseReducedRotation_up[instance.rotationOX.get(pair).getValue0()] > -1) {
                cost  = costUp(closedSubset, instance.rotationOX.get(pair).getValue0());
            }
            if (!(instance.rotationOX.get(pair).getValue1() == null) && reverseReducedRotation_down[instance.rotationOX.get(pair).getValue1()] > -1) {
                int cost_down = costDown(closedSubset, instance.rotationOX.get(pair).getValue1());
                if (cost_down < cost) {cost = cost_down;}
            }
            if (b < cost && cost != instance.malesN * instance.femalesN + 1) {b = cost;}
        }
        return b - 1;
    }

    private HashSet<Integer> diffUp(ArrayList<Integer> M, int rho) {
        HashSet<Integer> diff = new HashSet<>(M);
        for (int e : instance.Sup[rho]) {
            diff.remove(e);
        }
        return diff;
    }

    private HashSet<Integer> diffUp(int[] M, int rho) {
        HashSet<Integer> diff = new HashSet<>();
        for (int e : M) {diff.add(e);}
        for (int e : instance.Sup[rho]) {
            diff.remove(e);
        }
        return diff;
    }

    private HashSet<Integer> diffDown(ArrayList<Integer> M, int rho) {
        HashSet<Integer> diff = new HashSet<>();
        for (int e : instance.Sdown[rho]) {diff.add(e);}
        for (int e : M) {
            diff.remove(e);
        }
        return diff;
    }

    private HashSet<Integer> diffDown(int[] M, int rho) {
        HashSet<Integer> diff = new HashSet<>();
        for (int e : instance.Sdown[rho]) {diff.add(e);}
        for (int e : M) {
            diff.remove(e);
        }
        return diff;
    }

    private int costUp(ArrayList<Integer> M, int rho) {
        HashSet<Integer> generated = new HashSet<>();
        HashSet<Integer> eliminated = new HashSet<>();
        for (int e : diffUp(M, rho)) {
            for (int m : instance.rotationGeneratedCouples[e]) {
                generated.add(m);
            }
            for (int m : instance.rotationEliminatedCouples[e]) {
                eliminated.add(m);
            }
        }
        for (int c : eliminated) {
            generated.remove(c);
        }
        return generated.size();
    }

    private int costUp(int[] M, int rho) {
        HashSet<Integer> generated = new HashSet<>();
        HashSet<Integer> eliminated = new HashSet<>();
        for (int e : diffUp(M, rho)) {
            for (int m : instance.rotationGeneratedCouples[e]) {
                generated.add(m);
            }
            for (int m : instance.rotationEliminatedCouples[e]) {
                eliminated.add(m);
            }
        }
        for (int c : eliminated) {
            generated.remove(c);
        }
        return generated.size();
    }

    private int costDown(ArrayList<Integer> M, int rho) {
        HashSet<Integer> generated = new HashSet<>();
        HashSet<Integer> eliminated = new HashSet<>();
        for (int e : diffDown(M, rho)) {
            for (int m : instance.rotationGeneratedCouples[e]) {
                generated.add(m);
            }
            for (int m : instance.rotationEliminatedCouples[e]) {
                eliminated.add(m);
            }
        }
        for (int c : eliminated) {
            generated.remove(c);
        }
        return generated.size();
    }

    private int costDown(int[] M, int rho) {
        HashSet<Integer> generated = new HashSet<>();
        HashSet<Integer> eliminated = new HashSet<>();
        for (int e : diffDown(M, rho)) {
            for (int m : instance.rotationGeneratedCouples[e]) {
                generated.add(m);
            }
            for (int m : instance.rotationEliminatedCouples[e]) {
                eliminated.add(m);
            }
        }
        for (int c : eliminated) {
            generated.remove(c);
        }
        return generated.size();
    }

    public void reduceSearchSpace() {
        HashSet<Integer> lowerBound = new HashSet<>();
        HashSet<Integer> upperBound = new HashSet<>();
        for (int i : SuperstableUB) {upperBound.add(i);}
        HashSet<Integer> relevantMO = new HashSet<>();
        HashSet<Integer> relevantMZ = new HashSet<>();
        for (Pair<Integer, Integer> pair : instance.rotationOX.keySet()) {
            if (instance.rotationOX.get(pair).getValue1() == null && reverseReducedRotation_up[instance.rotationOX.get(pair).getValue0()] > -1) {
                relevantMZ.add(instance.rotationOX.get(pair).getValue0());
            }
            if (instance.rotationOX.get(pair).getValue0() == null && reverseReducedRotation_down[instance.rotationOX.get(pair).getValue1()] > -1) {
                relevantMO.add(instance.rotationOX.get(pair).getValue1());
            }
        }
        for (int rho_Z : relevantMZ) {
            if (upperBound.contains(rho_Z) && costUp(instance.Sdown[rho_Z], rho_Z) > OptUB + 1) {
                upperBound.remove(rho_Z);
            }
            for (int rho : instance.compGraph.getSuccessorsOf(rho_Z)) {
                if (upperBound.contains(rho) && costUp(instance.Sdown[rho], rho_Z) > OptUB + 1) {
                    upperBound.remove(rho);
                }
            }
        }
        for (int rho_O : relevantMO) {
            for (int rho : instance.Sdown[rho_O]) {
                if ( !(lowerBound.contains(rho)) && costDown(instance.Sup[rho], rho_O) > OptUB + 1) {lowerBound.add(rho);}
            }
        }

        int[] lb = new int[lowerBound.size()];
        int iter = 0;
        for (int rho : lowerBound) {
            lb[iter] = rho;
            iter++;
        }
        int[] ub = new int[upperBound.size()];
        iter = 0;
        for (int rho : upperBound) {
            ub[iter] = rho;
            iter++;
        }
        this.SuperstableLB = lb;
        this.SuperstableUB = ub;
    }

    public void reduceVariables() {

        this.reducedVariables = true;
        HashSet<Integer> removeSet_up = new HashSet<>();
        HashSet<Integer> removeSet_down = new HashSet<>();
        HashSet<Pair<Integer,Integer>> couplesSet = new HashSet<>(instance.rotationOX.keySet());

        int[] H_up = new int[numberRotations];
        int[] H_down = new int[numberRotations];
        for (int rho = 0; rho < numberRotations; rho++) {
            H_up[rho] = instance.rotationGeneratedCouples[rho].length;
            H_down[rho] = instance.rotationGeneratedCouples[rho].length;
        }

        int[] report_up = new int[numberRotations];
        int[] report_down = new int[numberRotations];
        for (int i = 0; i < numberRotations; i++) {
            report_up[i] = i;
            report_down[i] = i;
        }


        for (Pair<Integer,Integer> p : instance.rotationOX.keySet()) {
            if (instance.rotationOX.get(p).getValue0() == null) {
                for (int rho : instance.compGraph.getPredecessorsOf(instance.rotationOX.get(p).getValue1())) {
                    removeSet_down.add(rho);
                    if (instance.rotationOX.get(p).getValue1() > report_down[rho]) {report_down[rho] = instance.rotationOX.get(p).getValue1();}

                }
            } else if (instance.rotationOX.get(p).getValue1() == null) {
                for (int rho : instance.compGraph.getSuccessorsOf(instance.rotationOX.get(p).getValue0())) {
                    removeSet_up.add(rho);
                    if (instance.rotationOX.get(p).getValue0() < report_up[rho]) {report_up[rho] = instance.rotationOX.get(p).getValue0();}
                }
            } else {
                for (Pair<Integer,Integer> p_bis : instance.rotationOX.keySet()) {
                    if (!(instance.rotationOX.get(p_bis).getValue0() == null) && !(instance.rotationOX.get(p_bis).getValue1() == null) && instance.compGraph.containsEdge(instance.rotationOX.get(p_bis).getValue0(), instance.rotationOX.get(p).getValue0()) && instance.compGraph.containsEdge(instance.rotationOX.get(p).getValue1(), instance.rotationOX.get(p_bis).getValue1())) {
                        H_up[instance.rotationOX.get(p).getValue0()]--;
                        H_down[instance.rotationOX.get(p).getValue1()]--;
                        couplesSet.remove(p);
                        break;
                    }
                }
            }
        }

        for (int rho = 0; rho < numberRotations; rho++) {
            if (H_up[rho] == 0) {removeSet_up.add(rho);}
            if (H_down[rho] == 0) {removeSet_down.add(rho);}
        }

        this.NoDeletedUp = removeSet_up.size();
        this.NoDeletedDown = removeSet_down.size();

        int[] r_up = new int[numberRotations - removeSet_up.size()];
        int[] r_down = new int[numberRotations - removeSet_down.size()];

        int[] reverse_r_up = new int[numberRotations];
        int[] reverse_r_down = new int[numberRotations];

        int iter_up = 0;
        int iter_down = 0;

        for (int i = 0; i < numberRotations; i++) {
            if (!(removeSet_up.contains(i))) {
                r_up[iter_up] = i;
                reverse_r_up[i] = iter_up;
                iter_up++;
            } else {
                reverse_r_up[i] = -1;
            }
            if (!(removeSet_down.contains(i))) {
                r_down[iter_down] = i;
                reverse_r_down[i] = iter_down;
                iter_down++;
            } else {
                reverse_r_down[i] = -1;
            }
        }
        this.reducedRotation_up = r_up;
        this.reducedRotation_down = r_down;
        this.reverseReducedRotation_up = reverse_r_up;
        this.reverseReducedRotation_down = reverse_r_down;

        Map<Pair<Integer, Integer>, Pair<Integer, Integer>> reducedOX = new HashMap<>(){};
        for (Pair<Integer,Integer> p : couplesSet) {
            Integer rho_O = null;
            Integer rho_X = null;
            boolean update_O = false;
            boolean update_X = false;
            if (!(instance.rotationOX.get(p).getValue0() == null)) {
                rho_O = reverse_r_up[report_up[instance.rotationOX.get(p).getValue0()]];
                if (report_up[instance.rotationOX.get(p).getValue0()] < instance.rotationOX.get(p).getValue0()) {update_O = true;}
            }
            if (!(instance.rotationOX.get(p).getValue1() == null)) {
                rho_X = reverse_r_down[report_down[instance.rotationOX.get(p).getValue1()]];
                if (report_down[instance.rotationOX.get(p).getValue1()] > instance.rotationOX.get(p).getValue1()) {update_X = true;}
            }
            if (!(rho_O == null) && !(rho_X == null)) {
                if (!(update_O || update_X)) {
                    reducedOX.put(p, new Pair(rho_O, rho_X));
                }
            } else if (!(rho_O == null || update_O)) {
                reducedOX.put(p, new Pair(rho_O, null));
            } else if (!(rho_X == null || update_X)) {
                reducedOX.put(p, new Pair(null, rho_X));
            }
        }
        this.reducedRotationOX = reducedOX;
    }

    public boolean contains(int[] array, int element) {
        for (int i : array) {
            if (i == element) {return true;}
        }
        return false;
    }
}
