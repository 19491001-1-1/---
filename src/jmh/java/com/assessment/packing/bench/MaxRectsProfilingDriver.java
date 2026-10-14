package com.assessment.packing.bench;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.MaxRectsHeuristic;
import com.assessment.packing.solver.MaxRectsSolver;
import com.assessment.packing.solver.PackingDecoder;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.PackingDecoders;

import java.util.ArrayList;
import java.util.List;

/**
 * MaxRects 的剖析驱动器：把「搜索层每次评价真正调用的那段代码」跑成一段稳定负载，
 * 供 JFR 或异步采样剖析器归因。
 *
 * <p>JMH 只回答「MaxRects 比 Skyline 慢多少」，回答不了「慢在 {@code splitFreeRectangles} 的
 * 切割分配、还是 {@code pruneFreeList} 的双重扫描」。因此这里不做测量，只负责重复执行，
 * 把热点次数堆到剖析器采得到为止。
 *
 * <p>三种负载对应三条真实路径：
 * <ul>
 *   <li>{@code light}：{@link PackingDecoder#packedAreaInPriorityOrder}，即搜索评价实际走的路径
 *       （MaxRects-BEST 会依次跑完五种启发式各一次面积）；</li>
 *   <li>{@code decode}：{@link PackingDecoder#solveInPriorityOrder}，完整解码并构造布局；</li>
 *   <li>{@code single:<启发式>}：单独跑一个启发式的轻量评分，用于把五种启发式的成本分离开。</li>
 * </ul>
 *
 * <p>运行（配合 JFR）：
 * <pre>{@code
 * java -XX:StartFlightRecording=duration=60s,filename=/tmp/maxrects.jfr,settings=profile \
 *      -cp <classes>:<deps> com.assessment.packing.bench.MaxRectsProfilingDriver light 40 gcut3
 * }</pre>
 */
public final class MaxRectsProfilingDriver {

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "light";
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        String instanceName = args.length > 2 ? args[2] : "gcut3";

        // 按 AREA_DESC 排好序后交给解码器，模拟搜索传给解码器的优先级序列。
        Instance instance = withOrder(BenchInstances.load(instanceName));
        List<Item> priority = instance.items();
        PackingDecoder decoder = PackingDecoders.create(PackingDecoderType.MAXRECTS, true);

        long deadline = System.nanoTime() + seconds * 1_000_000_000L;
        long rounds = 0;
        long checksum = 0;
        while (System.nanoTime() < deadline) {
            checksum += run(instance, priority, decoder, mode);
            rounds++;
        }
        System.out.printf("MaxRectsProfilingDriver 模式=%s 实例=%s 轮次=%d 校验和=%d%n",
                mode, instanceName, rounds, checksum);
    }

    private static long run(Instance instance, List<Item> priority, PackingDecoder decoder,
                            String mode) {
        if (mode.startsWith("single:")) {
            MaxRectsHeuristic heuristic = MaxRectsHeuristic.valueOf(mode.substring("single:".length()));
            // 与 PackingDecoders 里的 MaxRects-BEST 适配器保持同样的参数（INPUT 排序 + 顺序选择）。
            return new MaxRectsSolver(true, ItemOrder.INPUT, heuristic, false)
                    .packedAreaOf(instance);
        }
        return switch (mode) {
            case "light" -> decoder.packedAreaInPriorityOrder(instance, priority);
            case "decode" -> decoder.solveInPriorityOrder(instance, priority).packedArea();
            default -> throw new IllegalArgumentException("未知剖析模式：" + mode);
        };
    }

    /** 把零件按面积降序重排，得到与搜索层相同的输入形态。 */
    private static Instance withOrder(Instance instance) {
        List<Item> ordered = new ArrayList<>(instance.items());
        ordered.sort(java.util.Comparator.comparingLong(Item::area).reversed());
        return new Instance(instance.name(), instance.binWidth(), instance.binHeight(), ordered);
    }
}
