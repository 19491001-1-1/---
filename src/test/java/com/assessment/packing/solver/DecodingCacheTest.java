package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link DecodingCache} 的记忆语义与有界淘汰验证。
 *
 * <p>缓存是纯记忆层：它只能少解码，绝不能改变同一个排列的解码结果。
 * 因此这里用一个可计数的解码器桩，既数解码次数，又让结果依赖排列顺序，
 * 从而同时验证「命中复用」与「命中返回的确实是该排列的解」。
 */
public final class DecodingCacheTest {
    public static void main(String[] args) {
        repeatedSequenceDecodesOnce();
        distinctSequencesDecodeSeparately();
        getDoesNotDecodeAndReportsMisses();
        evictsLeastRecentlyUsedBeyondCapacity();
        toItemsMapsIdsAndRejectsUnknown();
        putStoresPreevaluatedSolution();
        decoderAndInstanceDoNotLeakAcrossCaches();
        decodeAndStoreSkipsLookup();
        reusedLookupKeyDoesNotResurrectEvictedEntry();
        statisticsCountQueriesHitsAndEvictions();
        disabledCacheNeverStoresAndMatchesDirectDecode();
        System.out.println("DecodingCacheTest: 全部通过");
    }

    private static void repeatedSequenceDecodesOnce() {
        CountingDecoder decoder = new CountingDecoder();
        DecodingCache cache = new DecodingCache(instance(), decoder);
        int[] sequence = {3, 5, 9, 7};

        long first = cache.areaOf(sequence);
        long second = cache.areaOf(sequence.clone());

        check(decoder.calls == 1, "同一排列重复解码应只调用解码器一次，实际=" + decoder.calls);
        check(first == second, "命中应返回同一装载面积");
        check(first == 300, "面积应由排列首元素决定，实际=" + first);
    }

    private static void distinctSequencesDecodeSeparately() {
        CountingDecoder decoder = new CountingDecoder();
        DecodingCache cache = new DecodingCache(instance(), decoder);

        long a = cache.areaOf(new int[]{3, 5, 9, 7});
        long b = cache.areaOf(new int[]{5, 3, 9, 7});

        check(decoder.calls == 2, "不同排列应分别解码，实际=" + decoder.calls);
        check(a != b, "不同排列应得到不同面积，实际=" + a + "/" + b);
        check(cache.size() == 2, "两个排列都应留在缓存里");
    }

    private static void getDoesNotDecodeAndReportsMisses() {
        CountingDecoder decoder = new CountingDecoder();
        DecodingCache cache = new DecodingCache(instance(), decoder);

        check(cache.get(new int[]{3, 5, 9, 7}) == null, "未缓存的排列get必须返回null");
        check(decoder.calls == 0, "get不得触发解码，实际=" + decoder.calls);
        cache.areaOf(new int[]{3, 5, 9, 7});
        check(cache.get(new int[]{3, 5, 9, 7}) == 300, "缓存后get应返回该面积");
        check(decoder.calls == 1, "未命中后get不应再解码，实际=" + decoder.calls);
        check(cache.contains(new int[]{3, 5, 9, 7}), "contains应报告已缓存");
    }

    private static void evictsLeastRecentlyUsedBeyondCapacity() {
        CountingDecoder decoder = new CountingDecoder();
        DecodingCache cache = new DecodingCache(instance(), decoder, 2);

        cache.areaOf(new int[]{3, 5, 9, 7});
        cache.areaOf(new int[]{5, 3, 9, 7});
        cache.get(new int[]{3, 5, 9, 7});            // 3 变成最近使用
        cache.areaOf(new int[]{9, 3, 5, 7});         // 触发淘汰：应淘汰 5

        check(cache.size() == 2, "容量为2时不得超出，实际=" + cache.size());
        check(cache.contains(new int[]{3, 5, 9, 7}), "最近使用的排列应保留");
        check(!cache.contains(new int[]{5, 3, 9, 7}), "最久未使用的排列应被淘汰");
    }

    private static void toItemsMapsIdsAndRejectsUnknown() {
        DecodingCache cache = new DecodingCache(instance(), new CountingDecoder());
        List<Item> items = cache.toItems(new int[]{9, 3, 7});
        check(items.size() == 3, "应还原出3个零件");
        check(items.get(0).id() == 9 && items.get(1).id() == 3 && items.get(2).id() == 7,
                "应保持排列顺序还原零件");
        try {
            cache.toItems(new int[]{42});
            check(false, "越界零件编号必须报错");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("42"), "报错信息应包含非法编号");
        }
    }

    private static void putStoresPreevaluatedSolution() {
        DecodingCache cache = new DecodingCache(instance(), new CountingDecoder());
        int[] sequence = {3, 5, 9, 7};

        cache.put(sequence, 12345L);

        check(cache.get(sequence) == 12345L, "put后get应返回该面积");
        check(new DecodingCache(instance(), new CountingDecoder()).get(sequence) == null,
                "未写入的缓存必须返回null");
    }

    private static void decoderAndInstanceDoNotLeakAcrossCaches() {
        CountingDecoder first = new CountingDecoder();
        CountingDecoder second = new CountingDecoder();
        int[] sequence = {3, 5, 9, 7};

        new DecodingCache(instance(), first).areaOf(sequence);
        new DecodingCache(instance(), second).areaOf(sequence);

        check(first.calls == 1 && second.calls == 1,
                "不同解码器的缓存互不影响，实际=" + first.calls + "/" + second.calls);
    }

    /** decodeAndStore 用于「已查过表且未命中」的调用方，应直接解码回填。 */
    private static void decodeAndStoreSkipsLookup() {
        CountingDecoder decoder = new CountingDecoder();
        DecodingCache cache = new DecodingCache(instance(), decoder);
        int[] sequence = {3, 5, 9, 7};

        long area = cache.decodeAndStore(sequence);

        check(area == 300, "面积应由排列首元素决定，实际=" + area);
        check(decoder.calls == 1, "decodeAndStore应解码一次，实际=" + decoder.calls);
        check(cache.get(sequence) == 300, "decodeAndStore应把结果回填进缓存");
    }

    /**
     * 复用查找键必须与 map 条目按值匹配：某排列被 LRU 淘汰后，再用同一段排列查找
     * 必须重新未命中（不得因为复用了键实例就复活已淘汰的条目）。
     */
    private static void reusedLookupKeyDoesNotResurrectEvictedEntry() {
        CountingDecoder decoder = new CountingDecoder();
        DecodingCache cache = new DecodingCache(instance(), decoder, 2);

        cache.areaOf(new int[]{3, 5, 9, 7});
        cache.areaOf(new int[]{5, 3, 9, 7});
        cache.areaOf(new int[]{9, 3, 5, 7});     // 淘汰 3,5,9,7

        check(!cache.contains(new int[]{3, 5, 9, 7}), "已淘汰的排列不得被复用键复活");
        check(cache.get(new int[]{3, 5, 9, 7}) == null, "淘汰后重新查找必须未命中");
        cache.areaOf(new int[]{3, 5, 9, 7});
        check(decoder.calls == 4, "淘汰后应重新解码，实际=" + decoder.calls);
    }

    /** 缓存账本必须如实反映查询、命中与淘汰，这是判断缓存是否值得开的数据来源。 */
    private static void statisticsCountQueriesHitsAndEvictions() {
        CountingDecoder decoder = new CountingDecoder();
        DecodingCache cache = new DecodingCache(instance(), decoder, 2);

        check(cache.statistics().equals(new DecodingCacheStatistics(true, 0, 0, 0)),
                "尚未评价时统计应为零，实际=" + cache.statistics());
        check(cache.statistics().enabled(), "默认容量的缓存应报告已启用");

        // 容量2。注意 get 只查表不存表，因此未命中的排列随后走 areaOf 仍是未命中。
        cache.areaOf(new int[]{3, 5, 9, 7});         // 查询1 未命中 → 存入 A
        cache.areaOf(new int[]{3, 5, 9, 7});         // 查询2 命中 A
        cache.areaOf(new int[]{5, 3, 9, 7});         // 查询3 未命中 → 存入 B
        cache.areaOf(new int[]{5, 3, 9, 7});         // 查询4 命中 B
        cache.areaOf(new int[]{3, 5, 9, 7});         // 查询5 命中最久未用的 A，A 转为最近使用
        cache.areaOf(new int[]{7, 9, 5, 3});         // 查询6 未命中 → 存入 C，淘汰最久未用的 B

        DecodingCacheStatistics statistics = cache.statistics();
        check(statistics.enabled(), "统计应报告缓存已启用");
        check(statistics.queries() == 6, "查询数应为6，实际=" + statistics.queries());
        check(statistics.hits() == 3, "命中数应为3，实际=" + statistics.hits());
        check(statistics.misses() == 3, "未命中数应为3，实际=" + statistics.misses());
        check(statistics.evictions() == 1, "淘汰数应为1，实际=" + statistics.evictions());
        check(Math.abs(statistics.hitRatePercent() - 50.0) < 1e-9,
                "命中率应为50%，实际=" + statistics.hitRatePercent());
    }

    /**
     * 容量为 0 表示彻底关闭缓存：既不查表也不存条目，每次评价都是真实解码。
     * 这正是「无缓存」对照组的语义，必须与直接求解逐位一致。
     */
    private static void disabledCacheNeverStoresAndMatchesDirectDecode() {
        CountingDecoder decoder = new CountingDecoder();
        DecodingCache disabled = new DecodingCache(instance(), decoder, 0);
        int[] sequence = {3, 5, 9, 7};

        long first = disabled.areaOf(sequence);
        long second = disabled.areaOf(sequence.clone());

        check(first == second && first == 300, "关闭缓存后仍应得到同一面积，实际=" + first + "/" + second);
        check(decoder.calls == 2, "关闭缓存后每次评价都必须真实解码，实际=" + decoder.calls);
        check(!disabled.enabled(), "容量为0应报告缓存未启用");
        check(!disabled.contains(sequence), "关闭缓存时contains恒为false");
        check(disabled.get(sequence) == null, "关闭缓存时get恒为null");

        DecodingCacheStatistics statistics = disabled.statistics();
        check(!statistics.enabled(), "关闭缓存时统计应报告未启用");
        check(statistics.queries() == 0 && statistics.hits() == 0 && statistics.evictions() == 0,
                "关闭缓存时不应产生任何查询记账，实际=" + statistics);
        check(statistics.hitRatePercent() == 0.0, "未启用时命中率记为0");

        // put 也不能把条目留在关闭的缓存里，否则会与「无缓存」语义相悖。
        disabled.put(sequence, 12345L);
        check(disabled.get(sequence) == null, "关闭缓存的put不得留下条目");
        check(disabled.size() == 0, "关闭缓存的size应恒为0，实际=" + disabled.size());
    }

    private static Instance instance() {
        return new Instance("cache-test", 100, 100,
                List.of(new Item(3, 30, 10), new Item(5, 20, 20),
                        new Item(7, 10, 40), new Item(9, 50, 5)));
    }

    /** 计入解码次数，并让面积随排列首元素变化，以便区分不同排列。 */
    private static final class CountingDecoder implements PackingDecoder {
        private int calls;

        @Override
        public String name() {
            return "counting";
        }

        @Override
        public Solution solveInPriorityOrder(Instance instance, List<Item> priority) {
            calls++;
            // 只放首个零件：这样装载面积由排列首元素唯一决定，便于区分不同排列。
            List<Placement> placements = new ArrayList<>();
            if (!priority.isEmpty()) placements.add(new Placement(priority.get(0), 0, 0, false));
            return new Solution("counting", placements, 0);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
