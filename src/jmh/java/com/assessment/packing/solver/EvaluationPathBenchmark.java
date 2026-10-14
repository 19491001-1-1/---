package com.assessment.packing.solver;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 测量搜索**真正走的**评价路径：{@code packedAreaInPriorityOrder} 经 {@link DecodingCache}
 * 的查表 + 轻量评分。
 *
 * <p>放在 {@code solver} 包内是因为 {@link DecodingCache} 是包私有的——它只服务于搜索内部，
 * 不对外暴露。{@code com.assessment.packing.bench.DecoderThroughputBenchmark} 直接调
 * {@code solveInPriorityOrder}（完整解码 + 构造 Solution/Placement），**绕过了缓存与轻量评分**，
 * 因此无法给这些优化的收益归因。本基准补齐这块盲区。
 *
 * <p>三个方法刻意分层，便于把收益拆开看：
 * <ul>
 *   <li>{@link #lightScoreUncached}：固定排列，只走轻量评分（未命中即解码），不含缓存命中；</li>
 *   <li>{@link #cachedAreaLookup}：固定排列，反复查表——稳态下全是命中，测量纯查表成本；</li>
 *   <li>{@link #cachedAreaShuffled}：每个样本前打乱排列，测量「未命中 → 解码回填」与命中的混合。</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class EvaluationPathBenchmark {

    /** 每轮固定输入，用容量足够大的缓存覆盖全部排列。 */
    @State(Scope.Benchmark)
    public static class PathState {
        @Param({"gcut1", "gcut3", "gcut12"})
        public String instanceName;

        @Param({"SKYLINE", "MAXRECTS", "GUILLOTINE"})
        public String decoderName;

        Instance instance;
        PackingDecoder decoder;
        DecodingCache cache;
        int[] sequence;

        @Setup(Level.Trial)
        public void setUp() {
            instance = load(instanceName);
            decoder = PackingDecoders.create(PackingDecoderType.parse(decoderName), true);
            cache = new DecodingCache(instance, decoder, 1 << 20);
            sequence = new int[instance.items().size()];
            List<Item> items = instance.items();
            for (int index = 0; index < items.size(); index++) sequence[index] = items.get(index).id();
            // 先灌一次，让固定排列的查表路径处于命中态。
            cache.areaOf(sequence.clone());
        }
    }

    /** 轻量评分本身（不含缓存命中）：每个样本直接调解码器的只算分入口。 */
    @Benchmark
    public long lightScoreUncached(PathState state) {
        List<Item> priority = new ArrayList<>(state.instance.items());
        return state.decoder.packedAreaInPriorityOrder(state.instance, priority);
    }

    /** 缓存稳定命中：测量查表（键构造 + equals/hashCode）的纯成本。 */
    @Benchmark
    public long cachedAreaLookup(PathState state) {
        return state.cache.areaOf(state.sequence.clone());
    }

    /** 打乱混合：未命中走解码回填，命中直接返回。 */
    @State(Scope.Thread)
    public static class Scratch {
        final Random random = new Random(20261012L);
    }

    @Benchmark
    public long cachedAreaShuffled(PathState state, Scratch scratch) {
        int[] shuffled = state.sequence.clone();
        for (int i = shuffled.length - 1; i > 0; i--) {
            int swap = scratch.random.nextInt(i + 1);
            int temporary = shuffled[i];
            shuffled[i] = shuffled[swap];
            shuffled[swap] = temporary;
        }
        return state.cache.areaOf(shuffled);
    }

    private static Instance load(String name) {
        Path directory = Path.of(System.getProperty("gcut.dir", "gcut01"));
        try {
            return new InstanceReader().read(directory.resolve(name + ".txt"));
        } catch (IOException e) {
            throw new IllegalStateException("无法读取基准实例：" + name, e);
        }
    }
}
