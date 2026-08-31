package org.example.manyToMany;

import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.HashSet;

public class LocalSearch {

    public ProcessInstance instance;
    public ArrayList<Integer> bestMatching;
    public int bestValue;
    public ArrayList<Integer> currentMatching;
    public int currentValue;
    public long timeBestSolution;  // milliseconds

    public LocalSearch(ProcessInstance instance) {
        this.instance = instance;
    }


    /*
     * This class implements the Local Search algorithm described in the following thesis to solve the Robust Stable Marriage problem:
     * - B. Genc. 2019. An approach to robustness in stable marriage and stable roommates problems. PhD thesis. University College Cork
     */

    public void generalProcedure(long timeLimit, int descLimit, int cntLimit) {
        long startTime = System.currentTimeMillis();
        currentMatching = initialMatching();
        currentValue = evaluate(currentMatching);
        bestMatching = new ArrayList<>();
        bestMatching.addAll(currentMatching);
        bestValue = currentValue;
        timeBestSolution = System.currentTimeMillis() - startTime;
        int count = 0;
        int iter;
        while (System.currentTimeMillis() - startTime < timeLimit) {
            iter = 0;
            while (iter < descLimit && System.currentTimeMillis() - startTime < timeLimit) {
                Pair<Integer, ArrayList<Integer>> result = getBest(getNeighborhood(currentMatching));
                int new_b = result.getValue0();
                if (new_b < currentValue) {
                    currentMatching = result.getValue1();
                    currentValue = new_b;
                    if (currentValue < bestValue) {
                        bestMatching = new ArrayList<>();
                        bestMatching.addAll(currentMatching);
                        bestValue = currentValue;
                        timeBestSolution = System.currentTimeMillis() - startTime;
                        count = 0;
                    }
                }
                count++;
                iter++;
                if (count == cntLimit || bestValue == 1) {
                    return;
                }
            }
            currentMatching = initialMatching();
        }

    }

    public ArrayList<Integer> initialMatching() {
        ArrayList<Integer> matching = new ArrayList<>();
        int rho = (int) (Math.random() * instance.metaRotations.size());
        matching.add(rho);
        for (int u : instance.compGraph.getPredecessorsOf(rho)) {
            matching.add(u);
        }
        return matching;
    }

    public int evaluate (ArrayList<Integer> closedSubset) {
        int b = 0;
        int cost;
        for (Pair<Integer, Integer> pair : instance.rotationOX.keySet()) {
            cost = instance.malesN * instance.femalesN;
            if (!(instance.rotationOX.get(pair).getValue0() == null)) {
                cost  = costUp(closedSubset, instance.rotationOX.get(pair).getValue0());
            }
            if (!(instance.rotationOX.get(pair).getValue1() == null)) {
                int cost_down = costDown(closedSubset, instance.rotationOX.get(pair).getValue1());
                if (cost_down < cost) {cost = cost_down;}
            }
            if (b < cost) {b = cost;}
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

    private HashSet<Integer> diffDown(ArrayList<Integer> M, int rho) {
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

    public ArrayList<ArrayList<Integer>> getNeighborhood(ArrayList<Integer> matching) {
        ArrayList<ArrayList<Integer>> neighbors = new ArrayList<>();
        for (int i = 0; i < instance.metaRotations.size(); i++) {
            if (!(matching.contains(i))) {
                int count = 0;
                for (int u : instance.metaRotationPoset.getPredecessorsOf(i)) {
                    if (!(matching.contains(u))) {
                        count++;
                        break;
                    }
                }
                if (count == 0) {
                    ArrayList<Integer> neighbor = new ArrayList<>();
                    neighbor.add(i);
                    neighbor.addAll(matching);
                    neighbors.add(neighbor);
                }
            }
        }
        for (int u : matching) {
            int count = 0;
            for (int v : instance.metaRotationPoset.getSuccessorsOf(u)) {
                if (matching.contains(v)) {
                    count++;
                    break;
                }
            }
            if (count == 0) {
                ArrayList<Integer> neighbor = new ArrayList<>();
                for (int s : matching) {
                    if (s != u) {
                        neighbor.add(s);
                    }
                }
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    public Pair<Integer, ArrayList<Integer>> getBest(ArrayList<ArrayList<Integer>> matchings) {
        int b = instance.malesN * instance.femalesN;
        ArrayList<Integer> bestMatching = new ArrayList<>();
        for (ArrayList<Integer> m : matchings) {
            int new_b = evaluate(m);
            if (new_b < b) {
                b = new_b;
                bestMatching = m;
            }
        }
        return new Pair<>(b, bestMatching);
    }

    public boolean contains(int[] array, int element) {
        for (int i : array) {
            if (i == element) {return true;}
        }
        return false;
    }
}
