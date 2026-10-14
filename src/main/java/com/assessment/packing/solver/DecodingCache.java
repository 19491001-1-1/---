package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索过程中「零件排列 → 解码结果」的有界 LRU 记忆层。
 *
 * <p>搜索算法会反复遇到相同或等价排列，重复解码是当前最大的一笔浪费。本类让原本完全
 * 没有缓存的 Tabu、SA 也能复用先前算过的排列，并取代 ACO、ILS、GA 三份各自为政的重复实现。
 *
 * <p>缓存实例在单次 {@code solve} 内创建，因此实例身份、解码器类型和旋转权限由缓存自身的
 * 生命周期唯一确定，不需要塞进每个键；一个缓存不会跨越实例或解码器复用。
 *
 * <p>本类刻意<b>不内置计数器，也不做预算判定</b>。是否把缓存命中计入评价数、预算耗尽时
 * 如何退出，全部留在各搜索算法原有的循环与统计逻辑里。这样各算法原有的评价计数口径
 * 保持逐位一致，启用缓存前后搜索轨迹与最终结果完全相同。
 *
 * <p>缓存<b>只存装载面积（{@code long}），不存 {@link Solution}</b>。搜索过程中同一个排列
 * 会被反复比较面积，而完整布局只在最后需要一次；若把携带整张放置列表的 {@code Solution}
 * 留在缓存里，等于把最热路径上最大的一笔分配长期钉住，反而拖慢 GC。需要完整布局时一律
 * 走 {@link #solve}，不经过缓存。
 */
final class DecodingCache {
    /** 未配置时的默认容量，足以覆盖一次搜索里常见的重复排列。 */
    static final int DEFAULT_CAPACITY = 4096;

    private final Instance instance;
    private final Item[] byId;
    private final PackingDecoder decoder;
    private final Map<SequenceKey, Long> areas;

    DecodingCache(Instance instance, PackingDecoder decoder) {
        this(instance, decoder, DEFAULT_CAPACITY);
    }

    DecodingCache(Instance instance, PackingDecoder decoder, int capacity) {
        this.instance = java.util.Objects.requireNonNull(instance, "实例不能为空");
        this.decoder = java.util.Objects.requireNonNull(decoder, "排样解码器不能为空");
        if (capacity <= 0) throw new IllegalArgumentException("缓存容量必须为正数");
        this.byId = new Item[maxId(instance) + 1];
        for (Item item : instance.items()) byId[item.id()] = item;
        this.areas = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<SequenceKey, Long> eldest) {
                return size() > capacity;
            }
        };
    }

    /** 命中则返回已缓存的装载面积，否则返回 {@code null}。不触发解码。 */
    Long get(int[] sequence) {
        return areas.get(new SequenceKey(sequence));
    }

    /**
     * 命中直接返回缓存面积；未命中则算一次面积并存入缓存。调用方负责预算判定。
     *
     * <p>未命中时走 {@link PackingDecoder#packedAreaInPriorityOrder}，解码器可以选择
     * 只评分而不构造完整布局；默认实现退化为完整解码后取面积，结果一致。
     */
    long areaOf(int[] sequence) {
        SequenceKey key = new SequenceKey(sequence);
        Long cached = areas.get(key);
        if (cached != null) return cached;
        long area = decoder.packedAreaInPriorityOrder(instance, toItems(sequence));
        areas.put(key, area);
        return area;
    }

    /** 真实解码出完整布局。不走缓存：完整布局只在收尾时需要，存下来只会长期占着内存。 */
    Solution solve(int[] sequence) {
        return decoder.solveInPriorityOrder(instance, toItems(sequence));
    }

    /** 存入一个已算出的装载面积，用于调用方自己完成了预算判定之后的回填。 */
    void put(int[] sequence, long area) {
        areas.put(new SequenceKey(sequence), area);
    }

    boolean contains(int[] sequence) {
        return areas.containsKey(new SequenceKey(sequence));
    }

    int size() {
        return areas.size();
    }

    /** 把零件编号排列还原为零件列表，编号越界时立即报错而不是静默跳过。 */
    List<Item> toItems(int[] sequence) {
        List<Item> priority = new ArrayList<>(sequence.length);
        for (int id : sequence) {
            if (id < 0 || id >= byId.length || byId[id] == null) {
                throw new IllegalStateException("序列包含不存在的零件编号：" + id);
            }
            priority.add(byId[id]);
        }
        return priority;
    }

    private static int maxId(Instance instance) {
        int maxId = 0;
        for (Item item : instance.items()) maxId = Math.max(maxId, item.id());
        return maxId;
    }
}
