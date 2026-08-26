package org.example.benchmarks;

import org.example.manyToMany.*;
import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import static org.example.Main.generateLists;

public class Benchmark {

    public static void main(String[] args) throws Exception {
        bench_zip("mapel_69_n100.zip");
    }

    /**
     * Runs full pipeline on a single instance.
     */
    public static JSONObject bench(
            InstanceData data,
            boolean writeToFile,
            String outputFolder
    ) throws Exception {

        int N = data.N;

        int[] maleCap = new int[N];
        int[] femaleCap = new int[N];

        Arrays.fill(maleCap, data.cap);
        Arrays.fill(femaleCap, data.cap);

        ProcessInstance instance = new ProcessInstance(
                data.malePref,
                data.femalePref,
                maleCap,
                femaleCap
        );

        instance.constructMetaRotationPoset();

        JSONObject result;

        // ======================================================
        // UNIQUE STABLE MATCHING
        // ======================================================

        if (instance.metaRotations.isEmpty()) {

            result = new JSONObject()
                    .put("uniqueStableMatching", true);

        } else {

            // ==================================================
            // BASE INSTANCE
            // ==================================================

            CPInstance cp = new CPInstance(instance);
            cp.runNaive = true;
            cp.timeLimit = "30s";
            cp.solve();

            LocalSearch ls = new LocalSearch(instance);
            ls.generalProcedure(30000, 50, 10000);

            JSONObject baseInstance = new JSONObject()
                    .put("rotations", instance.metaRotations.size())
                    .put("pairs", instance.rotationOX.size())
                    .put("timeOne", instance.preProcessingOneTime)
                    .put("constraintProgramming",
                            new JSONObject()
                                    .put("status",
                                            cp.chocoStatus == 0
                                                    ? "TERMINATED"
                                                    : "STOPPED")
                                    .put("optimalValue", (int) cp.optValue)
                                    .put("timeBest", cp.timeBestSolution)
                                    .put("timeTotal", cp.solveTime))
                    .put("localSearch",
                            new JSONObject()
                                    .put("bestValue", ls.bestValue)
                                    .put("timeBest",
                                            (float) ls.timeBestSolution / 1000));

            // ==================================================
            // PREPROCESSED INSTANCE
            // ==================================================

            CPInstance cp2 = new CPInstance(instance);
            cp2.runNaive = false;
            cp2.timeLimit = "30s";
            cp2.solve();

            if (cp2.heuristicOptimal) {

                JSONObject preProcessedInstance = new JSONObject()
                        .put("bM0", cp2.M0b)
                        .put("bMZ", cp2.MZb)
                        .put("bLowerMedian", cp2.lowerMedianB)
                        .put("bUpperMedian", cp2.upperMedianB)
                        .put("timeTwo", cp2.preProcessingTwoTime);

                result = new JSONObject()
                        .put("uniqueStableMatching", false)
                        .put("baseInstance", baseInstance)
                        .put("heuristicOptimal", true)
                        .put("preProcessedInstance",
                                preProcessedInstance);

            } else {

                ReducedLocalSearch rls =
                        new ReducedLocalSearch(instance, cp2);

                rls.generalProcedure(30000, 50, 10000);

                JSONObject preProcessedInstance = new JSONObject()
                        .put("bM0", cp2.M0b)
                        .put("bMZ", cp2.MZb)
                        .put("bLowerMedian", cp2.lowerMedianB)
                        .put("bUpperMedian", cp2.upperMedianB)
                        .put("relevantPairs",
                                cp2.reducedRotationOX.size())
                        .put("searchSpace",
                                cp2.SuperstableUB.length
                                        - cp2.SuperstableLB.length)
                        .put("timeTwo", cp2.preProcessingTwoTime)
                        .put("constraintProgramming",
                                new JSONObject()
                                        .put("status",
                                                cp2.chocoStatus == 0
                                                        ? "TERMINATED"
                                                        : "STOPPED")
                                        .put("optimalValue",
                                                (int) cp2.optValue)
                                        .put("timeBest",
                                                cp2.timeBestSolution)
                                        .put("timeTotal",
                                                cp2.solveTime))
                        .put("localSearch",
                                new JSONObject()
                                        .put("bestValue", rls.bestValue)
                                        .put("timeBest",
                                                (float) rls.timeBestSolution
                                                        / 1000));

                result = new JSONObject()
                        .put("uniqueStableMatching", false)
                        .put("baseInstance", baseInstance)
                        .put("heuristicOptimal", false)
                        .put("preProcessedInstance",
                                preProcessedInstance);
            }
        }

        // ======================================================
        // OUTPUT
        // ======================================================

        if (writeToFile) {

            File folder = new File(outputFolder);

            if (!folder.exists())
                folder.mkdirs();

            File outputFile = new File(
                    folder,
                    data.name + "_result.txt"
            );

            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(result.toString(4));
            }

        } else {

            System.out.println(result.toString(4));
        }

        return result;
    }


    // ==========================================================
    // BENCHMARK A ZIP
    // ==========================================================

    public static void bench_zip(String zipName) throws Exception {

        File folder = new File(
                "src/main/java/org/example/benchmarks/"
        );

        File zipFile = new File(folder, zipName);

        if (!zipFile.exists()) {
            throw new IllegalArgumentException(
                    "ZIP file not found: "
                            + zipFile.getAbsolutePath()
            );
        }

        String baseName = zipName.substring(
                0,
                zipName.length() - 4
        );

        File tempInput = new File(
                folder,
                baseName + "_tmp"
        );

        File tempOutput = new File(
                folder,
                baseName + "_result_tmp"
        );

        File resultZip = new File(
                folder,
                baseName + "_result.zip"
        );

        // ======================================================
        // CLEAN PREVIOUS FILES
        // ======================================================

        deleteDirectory(tempInput);
        deleteDirectory(tempOutput);

        if (resultZip.exists())
            resultZip.delete();

        tempInput.mkdirs();
        tempOutput.mkdirs();

        // ======================================================
        // EXTRACT ZIP
        // ======================================================

        System.out.println(
                "Extracting " + zipName + "..."
        );

        unzip(zipFile, tempInput);

        // ======================================================
        // FIND INSTANCES
        // ======================================================

        List<File> files = new ArrayList<>();

        collectTxtFiles(tempInput, files);

        System.out.println(
                "Found " + files.size() + " instances."
        );

        // ======================================================
        // CAPACITIES
        // ======================================================

        int[] capacities = {1, 5, 10};

        int total = files.size() * capacities.length;
        int count = 0;

        // ======================================================
        // BENCHMARK
        // ======================================================

        for (File inputFile : files) {

            String relativePath = tempInput
                    .toURI()
                    .relativize(inputFile.toURI())
                    .getPath();

            File relativeFile = new File(relativePath);

            File relativeParent =
                    relativeFile.getParentFile();

            File resultDir = tempOutput;

            if (relativeParent != null) {
                resultDir = new File(
                        tempOutput,
                        relativeParent.getPath()
                );
            }

            if (!resultDir.exists())
                resultDir.mkdirs();

            // ----------------------------------------------
            // Run the same instance with 3 capacities
            // ----------------------------------------------

            for (int cap : capacities) {

                InstanceData data =
                        parseInstance(inputFile, cap);

                /*
                 * parseInstance removes ".txt" from the name.
                 *
                 * Example:
                 *   IC_001
                 *
                 * becomes:
                 *   IC_001_cap1_result.txt
                 *   IC_001_cap5_result.txt
                 *   IC_001_cap10_result.txt
                 *
                 * bench() adds "_result.txt".
                 */
                data.name =
                        data.name + "_cap" + cap;

                System.out.println(
                        "[" + (++count) + "/"
                                + total + "] "
                                + relativePath
                                + " (capacity "
                                + cap + ")"
                );

                bench(
                        data,
                        true,
                        resultDir.getPath()
                );
            }
        }

        // ======================================================
        // CREATE RESULT ZIP
        // ======================================================

        System.out.println(
                "\nCreating result ZIP..."
        );

        zipDirectory(
                tempOutput,
                resultZip
        );

        // ======================================================
        // CLEAN UP
        // ======================================================

        deleteDirectory(tempInput);
        deleteDirectory(tempOutput);

        System.out.println(
                "\nBenchmark complete: "
                        + count
                        + " instances."
        );

        System.out.println(
                "Result: "
                        + resultZip.getAbsolutePath()
        );
    }


    // ==========================================================
    // COLLECT TXT FILES
    // ==========================================================

    private static void collectTxtFiles(
            File folder,
            List<File> files) {

        File[] entries = folder.listFiles();

        if (entries == null)
            return;

        for (File entry : entries) {

            if (entry.isDirectory()) {

                collectTxtFiles(entry, files);

            } else if (
                    entry.getName().endsWith(".txt")
                            && !entry.getName()
                            .endsWith("_result.txt")
            ) {

                files.add(entry);
            }
        }
    }


    // ==========================================================
    // UNZIP
    // ==========================================================

    private static void unzip(
            File zipFile,
            File destination
    ) throws IOException {

        try (
                ZipInputStream zis =
                        new ZipInputStream(
                                new FileInputStream(zipFile)
                        )
        ) {

            ZipEntry entry;

            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {

                File outputFile = new File(
                        destination,
                        entry.getName()
                );

                // ------------------------------------------------
                // Protect against Zip Slip
                // ------------------------------------------------

                String destinationPath =
                        destination.getCanonicalPath()
                                + File.separator;

                String outputPath =
                        outputFile.getCanonicalPath();

                if (!outputPath.startsWith(destinationPath)) {

                    throw new IOException(
                            "Invalid ZIP entry: "
                                    + entry.getName()
                    );
                }

                if (entry.isDirectory()) {

                    outputFile.mkdirs();

                } else {

                    outputFile.getParentFile().mkdirs();

                    try (
                            FileOutputStream fos =
                                    new FileOutputStream(
                                            outputFile
                                    )
                    ) {

                        int len;

                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }

                zis.closeEntry();
            }
        }
    }


    // ==========================================================
    // ZIP DIRECTORY
    // ==========================================================

    private static void zipDirectory(
            File directory,
            File zipFile
    ) throws IOException {

        try (
                ZipOutputStream zos =
                        new ZipOutputStream(
                                new FileOutputStream(zipFile)
                        )
        ) {

            java.nio.file.Path base =
                    directory.toPath();

            java.nio.file.Files.walk(base)
                    .filter(path ->
                            !java.nio.file.Files.isDirectory(path))
                    .forEach(path -> {

                        try {

                            String relative =
                                    base.relativize(path)
                                            .toString()
                                            .replace(
                                                    File.separator,
                                                    "/"
                                            );

                            ZipEntry entry =
                                    new ZipEntry(relative);

                            zos.putNextEntry(entry);

                            java.nio.file.Files.copy(
                                    path,
                                    zos
                            );

                            zos.closeEntry();

                        } catch (IOException e) {

                            throw new RuntimeException(e);
                        }
                    });
        }
    }


    // ==========================================================
    // DELETE DIRECTORY
    // ==========================================================

    private static void deleteDirectory(
            File directory
    ) throws IOException {

        if (!directory.exists())
            return;

        java.nio.file.Files.walk(directory.toPath())
                .sorted(
                        Comparator.reverseOrder()
                )
                .map(java.nio.file.Path::toFile)
                .forEach(File::delete);
    }


    // ==========================================================
    // PARSE INSTANCE
    // ==========================================================

    public static InstanceData parseInstance(
            File file,
            int cap
    ) throws IOException {

        try (
                BufferedReader br =
                        new BufferedReader(
                                new FileReader(file)
                        )
        ) {

            List<String> lines = br.lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            int N =
                    Integer.parseInt(lines.get(0));

            int[][] malePref =
                    new int[N][N];

            int[][] femalePref =
                    new int[N][N];

            // --------------------------------------------------
            // MEN
            // --------------------------------------------------

            for (int i = 0; i < N; i++) {

                String[] values =
                        lines.get(i + 1)
                                .split("\\s+");

                for (int j = 0; j < N; j++) {

                    malePref[i][j] =
                            Integer.parseInt(values[j]);
                }
            }

            // --------------------------------------------------
            // WOMEN
            // --------------------------------------------------

            for (int i = 0; i < N; i++) {

                String[] values =
                        lines.get(i + N + 1)
                                .split("\\s+");

                for (int j = 0; j < N; j++) {

                    femalePref[i][j] =
                            Integer.parseInt(values[j]);
                }
            }

            // --------------------------------------------------
            // NAME
            // --------------------------------------------------

            String name =
                    file.getName();

            if (name.endsWith(".txt")) {

                name = name.substring(
                        0,
                        name.length() - 4
                );
            }

            return new InstanceData(
                    N,
                    cap,
                    malePref,
                    femalePref,
                    name
            );
        }
    }


    // ==========================================================
    // INSTANCE DATA
    // ==========================================================

    public static class InstanceData {

        int N;
        int cap;

        int[][] malePref;
        int[][] femalePref;

        String name;

        public InstanceData(
                int N,
                int cap,
                int[][] malePref,
                int[][] femalePref,
                String name) {

            this.N = N;
            this.cap = cap;
            this.malePref = malePref;
            this.femalePref = femalePref;
            this.name = name;
        }
    }
}