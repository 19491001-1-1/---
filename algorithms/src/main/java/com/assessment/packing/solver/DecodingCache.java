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
    /** 是否真正查表。关闭时 {@link #get} 恒返回 {@code null} 且不写入，语义等同于「没有缓存这一层」。 */
    private final boolean enabled;
    /** 命中/查询/淘汰记账；{@code enabled=false} 时只有 {@code enabled} 位有意义。 */
    private long queries;
    private long hits;
    private long evictions;
    /**
     * 还原零件列表用的复用缓冲区。解码是搜索里最热的路径，未命中时若每次都新建
     * {@code ArrayList<Item>}，这笔分配会随评价次数线性增长。所有解码器都只读取该列表
     * （适配器内部 {@code List.copyOf} 会复制一份），不会修改或长期持有，因此可以安全复用。
     */
    private final List<Item> priority;
    /**
     * 只读查表用的复用键。查表是搜索里最热的一步，若每次都新建键对象、克隆数组并重算哈希，
     * 这笔分配同样随评价次数线性增长。{@link SequenceKey#set} 会把内容就地改写并同步哈希；
     * 因为 {@link SequenceKey} 按值相等，它无需进入 map 也能命中已有条目（新键 equals 旧键）。
     * 该实例只在一次查表内有效，不得长期持有。
     */
    private final SequenceKey lookupKey;

    DecodingCache(Instance instance, PackingDecoder decoder) {
        this(instance, decoder, DEFAULT_CAPACITY);
    }

    DecodingCache(Instance instance, PackingDecoder decoder, int capacity) {
        this.instance = java.util.Objects.requireNonNull(instance, "实例不能为空");
        this.decoder = java.util.Objects.requireNonNull(decoder, "排样解码器不能为空");
        if (capacity < 0) throw new IllegalArgumentException("缓存容量不能为负数");
        this.enabled = capacity > 0;
        this.byId = new Item[maxId(instance) + 1];
        for (Item item : instance.items()) byId[item.id()] = item;
        this.priority = new ArrayList<>(instance.items().size());
        this.lookupKey = new SequenceKey(new int[instance.items().size()]);
        this.areas = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<SequenceKey, Long> eldest) {
                if (size() > capacity) {
                    evictions++;
                    return true;
                }
                return false;
            }
        };
    }

    /** 缓存是否真正生效。关闭时等价于「每次评价都直接解码」。 */
    boolean enabled() {
        return enabled;
    }

    /** 本次搜索的缓存账本快照。 */
    DecodingCacheStatistics statistics() {
        return new DecodingCacheStatistics(enabled, queries, hits, evictions);
    }

    /**
     * 命中则返回已缓存的装载面积，否则返回 {@code null}。不触发解码。
     *
     * <p>缓存关闭时恒返回 {@code null}（不查表、不计数），因为此时每次评价都必须真实解码，
     * 「未命中」这个说法没有意义。
     */
    Long get(int[] sequence) {
        if (!enabled) return null;
        queries++;
        Long cached = areas.get(lookup(sequence));
        if (cached != null) hits++;
        return cached;
    }

    /**
     * 命中直接返回缓存面积；未命中则算一次面积并存入缓存。调用方负责预算判定。
     *
     * <p>未命中时走 {@link PackingDecoder#packedAreaInPriorityOrder}，解码器可以选择
     * 只评分而不构造完整布局；默认实现退化为完整解码后取面积，结果一致。
     */
    long areaOf(int[] sequence) {
        Long cached = get(sequence);
        return cached != null ? cached : decodeAndStore(sequence);
    }

    /**
     * 解码并回填，不做前置查表——供已经自行查过表、做完预算判定的调用方使用，
     * 避免「先 {@code get} 再 {@code areaOf}」在未命中路径上重复查一次表。
     */
    long decodeAndStore(int[] sequence) {
        long area = decoder.packedAreaInPriorityOrder(instance, toItems(sequence));
        store(sequence, area);
        return area;
    }

    /** 真实解码出完整布局。不走缓存：完整布局只在收尾时需要，存下来只会长期占着内存。 */
    Solution solve(int[] sequence) {
        return decoder.solveInPriorityOrder(instance, toItems(sequence));
    }

    /** 存入一个已算出的装载面积，用于调用方自己完成了预算判定之后的回填。 */
    void put(int[] sequence, long area) {
        store(sequence, area);
    }

    /** 缓存关闭时不存条目：调用方已经付过一次真实解码，不需要再把这笔内存钉住。 */
    private void store(int[] sequence, long area) {
        if (!enabled) return;
        areas.put(new SequenceKey(sequence), area);
    }

    boolean contains(int[] sequence) {
        return enabled && areas.containsKey(lookup(sequence));
    }

    int size() {
        return areas.size();
    }

    /** 用复用键完成一次只读查找；返回的键仅在同一次方法调用内有效。 */
    private SequenceKey lookup(int[] sequence) {
        lookupKey.set(sequence);
        return lookupKey;
    }

    /**
     * 把零件编号排列还原为零件列表，编号越界时立即报错而不是静默跳过。
     *
     * <p>返回的是<b>复用的内部缓冲区</b>，每次调用都会覆盖上一次的内容，调用方不得长期持有。
     * 这样做的目的是消掉热路径上每次评价一次的列表分配；解码器都只读取该列表。
     */
    List<Item> toItems(int[] sequence) {
        priority.clear();
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
