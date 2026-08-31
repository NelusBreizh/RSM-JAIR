package org.example.manyToMany;

import org.chocosolver.util.objects.graphs.DirectedGraph;
import org.chocosolver.util.objects.setDataStructures.SetType;
import org.javatuples.Pair;

import java.util.*;

public class ProcessInstance {
    public int malesN;
    public int femalesN;
    public int[][] malesPref;
    public int[][] femalesPref;
    public int[] femalesCap;
    public int[] malesCap;
    public ArrayList<Integer>[] malesList;
    public ArrayList<Integer>[] femalesList;
    public ArrayList<Integer>[] maleOptimalStableMatching;
    public ArrayList<Integer>[] currentStableMatching;
    public ArrayList<ArrayList<Pair<Integer, Integer>>> metaRotations;
    public HashMap<Pair<Integer, Integer>, Integer> loopCurrentCouples;
    public DirectedGraph metaRotationPoset;
    public int[][] rowBest;
    public int[][] columnBest;
    public int[][] nodeDominated;
    public HashMap<Pair<Integer, Integer>, Integer> labels;
    public HashMap<Pair<Integer, Integer>, Pair<Integer, Integer>> rotationOX;
    public HashMap<Pair<Integer, Integer>, Integer> pairToIndex;
    public ArrayList<Pair<Integer, Integer>> indexToPair;
    public DirectedGraph compGraph;
    public int[][] Sup;
    public int[][] Sdown;
    public int[][] rotationGeneratedCouples;
    public int[][] rotationEliminatedCouples;
    public float preProcessingOneTime;

    /*
     * This class implements the various algorithms from the following papers to compute the rotation poset of a many-to-many stable marriage instance:
     * - M. Baıou and M. Balinski. 2000. Many-to-many matching: stable polyandrous polygamy (or polygamous polyandry). Discrete Applied Mathematics
     * - V. Bansal, A. Agrawal, and V. S. Malhotra. 2007. Polynomial time algorithm for an optimal stable assignment with multiple partners. Theoretical Computer Science
     * - P. Eirinakis, D. Magos, I. Mourtos, and P. Miliotis. 2012. Finding all stable pairs and solutions to the many-to-many stable matching problem. INFORMS Journal on Computing
     */


    public ProcessInstance(int[][] malesPref, int[][] femalesPref, int[] malesCap, int[] femalesCap) {
        this.malesN = malesPref.length;
        this.femalesN = femalesPref.length;
        this.malesPref = malesPref;
        this.femalesPref = femalesPref;
        this.malesCap = malesCap;
        this.femalesCap = femalesCap;
//        initializeLists();
        this.rowBest = new int[malesN][femalesN];
        this.columnBest = new int[malesN][femalesN];
        this.nodeDominated = new int[malesN][femalesN];
        this.labels = new HashMap<>();
        this.metaRotationPoset = new DirectedGraph(malesN * femalesN, SetType.BITSET, false);
        this.rotationOX = new HashMap<>();
    }

    public void constructMetaRotationPoset() {
        long startTime = System.currentTimeMillis();

        // Construct the meta-rotation poset
        getMaleOptimalMatching();
        initialPruning();
        findAllRotations();
        // Create the additional data structures
        if (!metaRotations.isEmpty()) {
            linkPairsWithIndices();
            getComparibilityGraph();
            initialiseSup();
            initialiseSdown();
            initialiseRotationGeneratedCouples();
            initialiseRotationEliminatedCouples();
        }
        preProcessingOneTime = System.currentTimeMillis() - startTime;
        preProcessingOneTime = preProcessingOneTime / 1000;
    }


    //***********************************************************************************
    // METHODS FOR CONSTRUCTING THE META-ROTATION POSET
    //***********************************************************************************

    private void getMaleOptimalMatching() {
        int count;
        boolean update;

        update = eliminateNodes();
        while (update) {
            update = eliminateNodes();
        }

        // Construct male optimal stable matching
        this.maleOptimalStableMatching = new ArrayList[malesN];
        for (int r = 0; r < malesN; r++) {
            maleOptimalStableMatching[r] = new ArrayList<>();
            count = 0;
            for (int c : malesPref[r]) {
                if (nodeDominated[r][c] == 0) {
                    maleOptimalStableMatching[r].add(c);
                    count++;
                }
                if (count == malesCap[r]) {
                    break;
                }
            }
        }
    }

    private boolean eliminateNodes() {
        int count;
        boolean update = false;

        // Eliminate row-dominated nodes
        for (int c = 0; c < femalesN; c++) {
            count = 0;
            for (int i = 0; i < malesN; i++) {
                if (nodeDominated[femalesPref[c][i]][c] == 0 && count < femalesCap[c]) {
                    columnBest[femalesPref[c][i]][c] = 1;
                    count++;
                }
                if (count == femalesCap[c]) {
                    break;
                }
            }
        }
        for (int r = 0; r < malesN; r++) {
            count = 0;
            for (int i = 0; i < femalesN; i++) {
                if (count >= malesCap[r] && nodeDominated[r][malesPref[r][i]] == 0) {
                    nodeDominated[r][malesPref[r][i]] = 1;
                    update = true;
                }
                if (columnBest[r][malesPref[r][i]] == 1 && nodeDominated[r][malesPref[r][i]] == 0) {
                    count++;
                }
            }
        }


        // Eliminate column-dominated nodes
        for (int r = 0; r < malesN; r++) {
            count = 0;
            for (int i = 0; i < femalesN; i++) {
                if (nodeDominated[r][malesPref[r][i]] == 0 && count < malesCap[r]) {
                    rowBest[r][malesPref[r][i]] = 1;
                    count++;
                }
                if (count == malesCap[r]) {
                    break;
                }
            }
        }
        for (int c = 0; c < femalesN; c++) {
            count = 0;
            for (int i = 0; i < malesN; i++) {
                if (count >= femalesCap[c] && nodeDominated[femalesPref[c][i]][c] == 0) {
                    nodeDominated[femalesPref[c][i]][c] = 1;
                    update = true;
                }
                if (rowBest[femalesPref[c][i]][c] == 1 && nodeDominated[femalesPref[c][i]][c] == 0) {
                    count++;
                }
            }
        }
        return update;
    }

    private void initialPruning() {
        int N;
        // Step A
        this.malesList = new ArrayList[malesN];
        for (int m = 0; m < malesN; m++) {
            malesList[m] = new ArrayList<>();
            if (!maleOptimalStableMatching[m].isEmpty()) {
                int last_f = maleOptimalStableMatching[m].get(maleOptimalStableMatching[m].size() - 1);
                boolean reached = false;
                for (int f : malesPref[m]) {
                    if (f == last_f) {
                        reached = true;
                    }
                    if (reached || maleOptimalStableMatching[m].contains(f)) {
                        malesList[m].add(f);
                    }
                }
            }
        }

        // Step B
        this.femalesList = new ArrayList[femalesN];
        for (int f = 0; f < femalesN; f++) {
            int last_m = -1;
            for (int i = femalesPref[f].length - 1; i >= 0; i--) {
                if (maleOptimalStableMatching[femalesPref[f][i]].contains(f)) {
                    last_m = femalesPref[f][i];
                    break;
                }
            }
            femalesList[f] = new ArrayList<>();
            for (int m : femalesPref[f]) {
                femalesList[f].add(m);
                if (m == last_m) {
                    break;
                }
            }
        }

        // Step C
        for (int m = 0; m < malesN; m++) {
            int iter = 0;
            N = malesList[m].size();
            for (int i = 0; i < N; i++) {
                if (!(femalesList[malesList[m].get(iter)].contains(m))) {
                    malesList[m].remove(iter);
                } else {
                    iter++;
                }
            }
        }

        // Step D
        for (int f = 0; f < femalesN; f++) {
            int iter = 0;
            N = femalesList[f].size();
            for (int i = 0; i < N; i++) {
                if (!(malesList[femalesList[f].get(iter)].contains(f))) {
                    femalesList[f].remove(iter);
                } else {
                    iter++;
                }
            }
        }
    }

    public void findAllRotations() {
        this.currentStableMatching = maleOptimalStableMatching;
        this.metaRotations = new ArrayList<>();
        boolean found = findRotation();
        while (found) {
            found = findRotation();
        }
    }

    public boolean findRotation() {
        this.loopCurrentCouples = new HashMap<>();
        for (int f = 0; f < femalesN; f++) {
            // m = min(f)
            int m = getMinMale(f);
            if (m > -1) {
                loopCurrentCouples.put(new Pair<>(m, f), 0);
            }
        }

        for (Pair<Integer, Integer> couple : loopCurrentCouples.keySet()) {
            if (loopCurrentCouples.get(couple) == 0) {
                Pair<Integer, ArrayList<Pair<Integer, Integer>>> exploration = explore(couple);
                if (exploration.getValue0() == 1) {
                    updateRotationOX(exploration.getValue1());
                    metaRotationPoset.addNode(metaRotations.size());
                    addPosetEdges1(exploration.getValue1(), metaRotations.size());
                    addPosetEdges2(exploration.getValue1(), metaRotations.size());
                    metaRotations.add(exploration.getValue1());
                    eliminateRotation(exploration.getValue1());

                    return true;
                }
            }
        }
        return false;
    }

    public void updateRotationOX(ArrayList<Pair<Integer, Integer>> rho) {
        for (int i = 0; i < rho.size(); i++) {
            int m = rho.get(i).getValue0();
            int next_f = rho.get((i+1) % rho.size()).getValue1();
            rotationOX.put(new Pair<>(m, next_f), new Pair<>(metaRotations.size(), null));
            if (rotationOX.containsKey(rho.get(i))) {
                rotationOX.put(rho.get(i), new Pair<>(rotationOX.get(rho.get(i)).getValue0(), metaRotations.size()));
            } else {
                rotationOX.put(rho.get(i), new Pair<>(null, metaRotations.size()));
            }
        }
    }

    public Pair<Integer, ArrayList<Pair<Integer, Integer>>> explore(Pair<Integer, Integer> couple) {
        int m = couple.getValue0();
        int f = couple.getValue1();
        int isRotation = 0;
        ArrayList<Pair<Integer, Integer>> rho = new ArrayList<>();
        rho.add(couple);
        loopCurrentCouples.put(couple, 1);

        for (int i = 0; i < femalesN; i++) {
            // find next_f = smin(m)
            int next_f = getNextFemale(m);
            if (next_f == -1) {
                return new Pair<>(0, rho);
            }
            int next_m = getMinMale(next_f);
            if (next_m == -1) {
                return new Pair<>(0, rho);
            }
            Pair<Integer, Integer> next_couple = new Pair<>(next_m, next_f);
            int indexLoop = checkLoop(rho, next_m, next_f);
            if (indexLoop > -1) {
                ArrayList<Pair<Integer, Integer>> rho_bis = new ArrayList<>();
                for (int j = indexLoop; j < rho.size(); j++) {
                    rho_bis.add(rho.get(j));
                }
                return new Pair<>(1, rho_bis);
            }
            if (loopCurrentCouples.get(next_couple) == 1) {
                return new Pair<>(0, rho);
            }
            loopCurrentCouples.put(next_couple, 1);
            rho.add(next_couple);
            m = next_m;
            f = next_f;
        }
        System.out.println("NOT SUPPOSED TO REACH THIS LINE");
        return new Pair<>(isRotation, rho);
    }

    public int checkLoop(ArrayList<Pair<Integer, Integer>> rho, int m, int f) {
        for (int i = 0; i < rho.size() - 1; i++) {
            if (m == rho.get(i).getValue0() && f == rho.get(i).getValue1()) {
                return i;
            }
        }
        return -1;
    }

    public int getNextFemale(int m) {
        int next_f = -1;
        int last_f = currentStableMatching[m].get(currentStableMatching[m].size() - 1);
        boolean reached = false;
        for (int i = 0; i < malesList[m].size(); i++) {
            if (reached) {
                next_f = malesList[m].get(i);
                break;
            }
            if (malesList[m].get(i) == last_f) {
                reached = true;
            }
        }
        return next_f;
    }

    public int getMinMale(int f) {
        for (int i = femalesList[f].size() - 1; i >= 0; i--) {
            if (currentStableMatching[femalesList[f].get(i)].contains(f)) {
                return femalesList[f].get(i);
            }
        }
        return -1;
    }

    public void eliminateRotation(ArrayList<Pair<Integer, Integer>> rho) {
        int N;
        int K;
        for (int i = 0; i < rho.size(); i++) {
            int m = rho.get(i).getValue0();
            int f = rho.get(i).getValue1();
            int next_m = rho.get((i - 1 + rho.size()) % rho.size()).getValue0();
            int next_f = rho.get((i + 1) % rho.size()).getValue1();
            // Delete (m_i, f_i)
            N = currentStableMatching[m].size();
            for (int j = N - 1; j >= 0; j--) {
                if (currentStableMatching[m].get(j) == f) {
                    currentStableMatching[m].remove(j);
                    break;
                }
            }
            // Add (m_i, f_i+1)
            currentStableMatching[m].add(next_f);
            // Update lists
            N = femalesList[f].size();
            for (int j = 0; j < N; j++) {
                int iter = femalesList[f].size() - 1;
                if (femalesList[f].get(iter) == next_m || currentStableMatching[femalesList[f].get(iter)].contains(f)) {
                    break;
                }
                // Update L_m
                K = malesList[femalesList[f].get(iter)].size();
                for (int k = 0; k < K; k++) {
                    if (malesList[femalesList[f].get(iter)].get(k) == f) {
                        //Label for constructing rotation poset
                        labels.put(new Pair<>(femalesList[f].get(iter), f), metaRotations.size() - 1);
                        malesList[femalesList[f].get(iter)].remove(k);
                        break;
                    }
                }
                // Update L_f
                femalesList[f].remove(iter);
            }
        }
    }

    public void addPosetEdges1(ArrayList<Pair<Integer, Integer>> rho, int label) {
        for (Pair<Integer, Integer> couple : rho) {
            int m = couple.getValue0();
            int f = couple.getValue1();
            boolean reached = false;
            for (int i = 0; i < malesN; i++) {
                Pair<Integer,Integer> temp = new Pair<>(femalesPref[f][i], f);
                if (reached && rotationOX.containsKey(temp) && rotationOX.get(temp).getValue1() != null) {
                    metaRotationPoset.addEdge(rotationOX.get(temp).getValue1(), label);
                    break;
                }
                if (femalesPref[f][i] == m) {
                    reached = true;
                }
            }
        }
    }

    public void addPosetEdges2(ArrayList<Pair<Integer, Integer>> rho, int label) {
        for (int i = 0; i < rho.size(); i++) {
            int m = rho.get(i).getValue0();
            int min_m = currentStableMatching[m].get(currentStableMatching[m].size() - 1);
            int smin_m = rho.get((i + 1) % rho.size()).getValue1();
            boolean reached = false;
            for (int j = 0; j < femalesN; j++) {
                if (malesPref[m][j] == smin_m) {
                    break;
                }
                if (reached) {
                    Pair<Integer,Integer> couple = new Pair<>(m, malesPref[m][j]);
                    if (labels.containsKey(couple)) {
                        metaRotationPoset.addEdge(labels.get(couple), label);
                    }
                }
                if (malesPref[m][j] == min_m) {
                    reached = true;
                }
            }
            // Supplementary rule in paper Finding All Stable Pairs...
            if (rotationOX.containsKey(new Pair<>(m, min_m)) && rotationOX.get(new Pair<>(m, min_m)).getValue0() != null) {
                metaRotationPoset.addEdge(rotationOX.get(new Pair<>(m, min_m)).getValue0(), label);
            }
        }
    }


    //***********************************************************************************
    // METHODS FOR INITIALISING THE ADDITIONAL DATA STRUCTURES
    //***********************************************************************************


    public void linkPairsWithIndices() {
        this.pairToIndex = new HashMap<>();
        this.indexToPair = new ArrayList<>();
        int index = 0;
        for (Pair<Integer,Integer> couple : rotationOX.keySet()) {
            pairToIndex.put(couple, index);
            indexToPair.add(couple);
            index++;
        }
    }

    public void getComparibilityGraph() {
        this.compGraph = new DirectedGraph(metaRotations.size(), SetType.BITSET, true);
        for (int u = 0; u < metaRotations.size(); u++) {
            for (int v : metaRotationPoset.getSuccessorsOf(u)) {
                compGraph.addEdge(u, v);
                for (int w : compGraph.getPredecessorsOf(u)) {
                    compGraph.addEdge(w, v);
                }
            }
        }
    }

    public void initialiseSup() {
        Sup = new int[metaRotations.size()][];
        boolean[] markRotation = new boolean[metaRotations.size()];
        for (int node : compGraph.getNodes()) {
            Arrays.fill(markRotation, true);
            markRotation[node] = false;
            for (int rotation : compGraph.getSuccessorsOf(node)){
                markRotation[rotation] = false;
            }
            ArrayList<Integer> rotations = new ArrayList<>();
            for (int rotation = 0; rotation < metaRotations.size(); rotation++) {
                if (markRotation[rotation]) {
                    rotations.add(rotation);
                }
            }
            Sup[node] = rotations.stream()
                    .mapToInt(Integer::intValue)
                    .toArray();
        }
    }

    public void initialiseSdown() {
        Sdown = new int[metaRotations.size()][];
        for (int node : compGraph.getNodes()) {
            ArrayList<Integer> rotations = new ArrayList<>();
            rotations.add(node);
            for (int rotation : compGraph.getPredecessorsOf(node)){
                rotations.add(rotation);
            }
            Sdown[node] = rotations.stream()
                    .mapToInt(Integer::intValue)
                    .toArray();
        }
    }

    public void initialiseRotationGeneratedCouples() {
        this.rotationGeneratedCouples = new int[metaRotations.size()][];
        for (int indexRho = 0; indexRho < metaRotations.size(); indexRho++) {
            ArrayList<Pair<Integer, Integer>> rho = metaRotations.get(indexRho);
            int[] generatedCouples = new int[rho.size()];
            for (int i = 0; i < rho.size(); i++) {
                generatedCouples[i] = pairToIndex.get(new Pair<>(rho.get(i).getValue0(), rho.get((i + 1) % rho.size()).getValue1()));
            }
            rotationGeneratedCouples[indexRho] = generatedCouples;
        }
    }

    public void initialiseRotationEliminatedCouples() {
        this.rotationEliminatedCouples = new int[metaRotations.size()][];
        for (int indexRho = 0; indexRho < metaRotations.size(); indexRho++) {
            ArrayList<Pair<Integer, Integer>> rho = metaRotations.get(indexRho);
            int[] eliminatedCouples = new int[rho.size()];
            for (int i = 0; i < rho.size(); i++) {
                eliminatedCouples[i] = pairToIndex.get(rho.get(i));
            }
            rotationEliminatedCouples[indexRho] = eliminatedCouples;
        }
    }

    //***********************************************************************************
    // UPDATES AND QUERIES FOR DATA STRUCTURES
    //***********************************************************************************

    public boolean isInSup(int rho, int query) {
        return query != rho && !compGraph.getSuccessorsOf(rho).contains(query);
    }

    public boolean isInSdown(int rho, int query) {
        return query == rho || compGraph.getPredecessorsOf(rho).contains(query);
    }

}

