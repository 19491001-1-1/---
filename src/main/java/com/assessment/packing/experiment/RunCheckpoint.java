package com.assessment.packing.experiment;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ConvergencePoint;
import com.assessment.packing.solver.MaxRectsBestStatistics;
import com.assessment.packing.solver.MaxRectsHeuristic;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RunCheckpoint {
    private static final int VERSION = 1;

    private RunCheckpoint() {}

    static void save(
            Path file, ExperimentRunner.Options options, List<RunObservation> runs) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(temporary)))) {
            out.writeInt(VERSION);
            out.writeUTF(fingerprint(options));
            out.writeInt(runs.size());
            for (RunObservation run : runs) writeRun(out, run);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static List<RunObservation> load(
            Path file, ExperimentRunner.Options options, List<Instance> instances) throws IOException {
        if (!Files.exists(file)) return List.of();
        Map<String, Instance> byName = new HashMap<>();
        for (Instance instance : instances) byName.put(instance.name(), instance);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            int version = in.readInt();
            if (version != VERSION) throw new IllegalStateException("不支持的断点文件版本：" + version);
            String actual = in.readUTF();
            String expected = fingerprint(options);
            if (!actual.equals(expected)) {
                throw new IllegalStateException("断点文件与当前实验配置不一致，请更换输出目录");
            }
            int count = in.readInt();
            List<RunObservation> runs = new ArrayList<>(count);
            for (int index = 0; index < count; index++) runs.add(readRun(in, byName));
            return List.copyOf(runs);
        }
    }

    private static void writeRun(DataOutputStream out, RunObservation run) throws IOException {
        out.writeUTF(run.experiment());
        out.writeUTF(run.budgetKind());
        out.writeLong(run.budgetValue());
        out.writeUTF(run.algorithm().name());
        out.writeUTF(run.instance().name());
        out.writeInt(run.repetition());
        out.writeLong(run.seed());
        out.writeUTF(run.solution().algorithm());
        out.writeLong(run.solution().elapsedNanos());
        out.writeInt(run.solution().placements().size());
        for (Placement placement : run.solution().placements()) {
            out.writeInt(placement.item().id());
            out.writeInt(placement.x());
            out.writeInt(placement.y());
            out.writeBoolean(placement.rotated());
        }
        out.writeBoolean(run.valid());
        out.writeUTF(run.validationErrors());
        out.writeLong(run.evaluations());
        out.writeLong(run.iterations());
        MaxRectsBestStatistics statistics = run.maxRectsBestStatistics();
        out.writeBoolean(statistics.available());
        if (statistics.available()) {
            out.writeUTF(statistics.selectedHeuristic().name());
            out.writeInt(statistics.entries().size());
            for (MaxRectsBestStatistics.Entry entry : statistics.entries()) {
                out.writeUTF(entry.heuristic().name());
                out.writeLong(entry.packedArea());
                out.writeInt(entry.placedItems());
                out.writeLong(entry.elapsedNanos());
            }
        }
        out.writeInt(run.trace().size());
        for (ConvergencePoint point : run.trace()) {
            out.writeLong(point.step());
            out.writeLong(point.evaluations());
            out.writeLong(point.bestPackedArea());
        }
    }

    private static RunObservation readRun(
            DataInputStream in, Map<String, Instance> instances) throws IOException {
        String experiment = in.readUTF();
        String budgetKind = in.readUTF();
        long budgetValue = in.readLong();
        AlgorithmId algorithm = AlgorithmId.valueOf(in.readUTF());
        Instance instance = instances.get(in.readUTF());
        if (instance == null) throw new IllegalStateException("断点文件引用了不存在的实例");
        int repetition = in.readInt();
        long seed = in.readLong();
        String solutionAlgorithm = in.readUTF();
        long elapsedNanos = in.readLong();
        Map<Integer, Item> items = new HashMap<>();
        for (Item item : instance.items()) items.put(item.id(), item);
        int placementCount = in.readInt();
        List<Placement> placements = new ArrayList<>(placementCount);
        for (int index = 0; index < placementCount; index++) {
            Item item = items.get(in.readInt());
            if (item == null) throw new IllegalStateException("断点文件引用了不存在的零件");
            placements.add(new Placement(item, in.readInt(), in.readInt(), in.readBoolean()));
        }
        boolean valid = in.readBoolean();
        String errors = in.readUTF();
        long evaluations = in.readLong();
        long iterations = in.readLong();
        MaxRectsBestStatistics statistics = readMaxRectsStatistics(in);
        int traceCount = in.readInt();
        List<ConvergencePoint> trace = new ArrayList<>(traceCount);
        for (int index = 0; index < traceCount; index++) {
            trace.add(new ConvergencePoint(in.readLong(), in.readLong(), in.readLong()));
        }
        return new RunObservation(
                experiment, budgetKind, budgetValue, algorithm, instance, repetition, seed,
                new Solution(solutionAlgorithm, placements, elapsedNanos), valid, errors,
                evaluations, iterations, statistics, trace);
    }

    private static MaxRectsBestStatistics readMaxRectsStatistics(DataInputStream in) throws IOException {
        if (!in.readBoolean()) return MaxRectsBestStatistics.empty();
        MaxRectsHeuristic selected = MaxRectsHeuristic.valueOf(in.readUTF());
        int count = in.readInt();
        List<MaxRectsBestStatistics.Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new MaxRectsBestStatistics.Entry(
                    MaxRectsHeuristic.valueOf(in.readUTF()), in.readLong(),
                    in.readInt(), in.readLong()));
        }
        return new MaxRectsBestStatistics(selected, entries);
    }

    private static String fingerprint(ExperimentRunner.Options options) {
        return options.dataDirectory().toAbsolutePath().normalize() + "|"
                + options.algorithms() + "|" + options.rotationAllowed() + "|"
                + options.order() + "|" + options.seeds() + "|" + options.profile() + "|"
                + options.searchLimit() + "|" + options.experimentName() + "|"
                + options.budgetKind() + "|" + options.budgetValue() + "|"
                + options.repeatDeterministic() + "|" + options.decoderType();
    }
}
