package org.example.benchmarks;

import org.example.manyToMany.*;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import static org.example.Main.generateLists;

public class Benchmark {

    public static void main(String[] args) throws Exception {
        int N = 1500;
        int C = 1;
        int[][][] preferences = generateLists(N);
        int[][] malePref = preferences[0];
        int[][] femalePref = preferences[1];
        InstanceData data = new InstanceData(N, C, malePref, femalePref);
        System.out.println(bench(data));
    }

    /**
     * Runs full pipeline on a single instance and returns JSON stats.
     */
    public static JSONObject bench(InstanceData data)
            throws Exception {

        int N = data.N;
        int[] maleCap = new int[N];
        int[] femaleCap = new int[N];
        Arrays.fill(maleCap, data.cap);
        Arrays.fill(femaleCap, data.cap);

        int[][] malePref = data.malePref;
        int[][] femalePref = data.femalePref;

        ProcessInstance instance = new ProcessInstance(malePref, femalePref, maleCap, femaleCap);
        instance.constructMetaRotationPoset();

        // ================= BASE INSTANCE =================
        CPInstance cp = new CPInstance(instance);
        cp.runNaive = true;
        cp.timeLimit = "60s";
        cp.solve();

        LocalSearch ls = new LocalSearch(instance);
        ls.generalProcedure(60000, 50, 10000);

        JSONObject baseInstance = new JSONObject()
                .put("rotations", instance.metaRotations.size())
                .put("pairs", instance.rotationOX.size())
                .put("timeOne", instance.preProcessingOneTime)
                .put("constraintProgramming", new JSONObject()
                        .put("status", cp.chocoStatus == 0 ? "TERMINATED" : "STOPPED")
                        .put("optimalValue", (int) cp.optValue)
                        .put("timeBest", cp.timeBestSolution)
                        .put("timeTotal", cp.solveTime))
                .put("localSearch", new JSONObject()
                        .put("bestValue", ls.bestValue)
                        .put("timeBest", (float) ls.timeBestSolution / 1000));

        // ================= PREPROCESSED INSTANCE =================
        CPInstance cp2 = new CPInstance(instance);
        cp2.runNaive = false;
        cp2.timeLimit = "60s";
        cp2.solve();

        ReducedLocalSearch rls = new ReducedLocalSearch(instance, cp2);
        rls.generalProcedure(60000, 50, 10000);

        JSONObject preProcessedInstance = new JSONObject()
                .put("bM0", cp2.M0b)
                .put("bMZ", cp2.MZb)
                .put("bLowerMedian", cp2.lowerMedianB)
                .put("bUpperMedian", cp2.upperMedianB)
                .put("relevantPairs", cp2.reducedRotationOX.size())
                .put("searchSpace", cp2.SuperstableUB.length - cp2.SuperstableLB.length)
                .put("timeTwo", cp2.preProcessingTwoTime)
                .put("constraintProgramming", new JSONObject()
                        .put("status", cp2.chocoStatus == 0 ? "TERMINATED" : "STOPPED")
                        .put("optimalValue", (int) cp2.optValue)
                        .put("timeBest", cp2.timeBestSolution)
                        .put("timeTotal", cp2.solveTime))
                .put("localSearch", new JSONObject()
                        .put("bestValue", rls.bestValue)
                        .put("timeBest", (float) rls.timeBestSolution / 1000));

        return new JSONObject()
                .put("baseInstance", baseInstance)
                .put("preProcessedInstance", preProcessedInstance);
    }

    /**
     * TODO: implement your own parser later.
     */
    public static InstanceData parseInstance(File file) {
        // Placeholder stub
        // You will replace this with real parsing logic
        throw new UnsupportedOperationException("Parser not implemented yet: " + file.getName());
    }

    /**
     * Simple container for parsed instance data.
     */
    public static class InstanceData {
        int N;
        int cap;
        int[][] malePref;
        int[][] femalePref;

        public InstanceData(int N, int cap, int[][] malePref, int[][] femalePref) {
            this.N = N;
            this.cap = cap;
            this.malePref = malePref;
            this.femalePref = femalePref;
        }
    }
}