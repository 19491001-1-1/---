package com.assessment.packing.bench;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.solver.PackingDecoder;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.PackingDecoders;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 单个解码器把「优先级排列」解码成布局的吞吐与平均耗时。
 * 这是搜索过程中最内层、调用次数最多的操作。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DecoderThroughputBenchmark {

    @State(Scope.Benchmark)
    public static class DecoderState {
        @Param({"gcut1", "gcut3", "gcut12"})
        public String instanceName;

        @Param({"SKYLINE", "MAXRECTS", "GUILLOTINE"})
        public String decoderName;

        Instance instance;
        PackingDecoder decoder;
        List<Item> priority;

        @Setup(Level.Trial)
        public void setUp() {
            instance = BenchInstances.load(instanceName);
            decoder = PackingDecoders.create(PackingDecoderType.parse(decoderName), true);
            priority = new ArrayList<>(instance.items());
        }
    }

    /** 固定排列重复解码，测量纯解码成本（不涉及搜索）。 */
    @Benchmark
    public long decodeFixedOrder(DecoderState state) {
        return state.decoder.solveInPriorityOrder(state.instance, state.priority).packedArea();
    }

    /** 每个样本前打乱排列，测量解码器对输入顺序的敏感性成本。 */
    @State(Scope.Thread)
    public static class Scratch {
        final Random random = new Random(20261012L);

        List<Item> shuffled(Instance instance) {
            List<Item> items = new ArrayList<>(instance.items());
            java.util.Collections.shuffle(items, random);
            return items;
        }
    }

    @Benchmark
    public long decodeShuffledOrder(DecoderState state, Scratch scratch) {
        return state.decoder
                .solveInPriorityOrder(state.instance, scratch.shuffled(state.instance))
                .packedArea();
    }
}
