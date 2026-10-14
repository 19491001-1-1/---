package com.assessment.packing.solver;

import java.util.Arrays;

/**
 * 以零件编号排列作为不可变哈希键。hash 在构造时预计算，避免反复遍历排列。
 *
 * <p>这是搜索层唯一的一份排列键实现，取代原先散落在 ACO、ILS、GA 内部的三份重复定义。
 */
final class SequenceKey {
    private final int[] values;
    private int hash;

    SequenceKey(int[] values) {
        this.values = values.clone();
        this.hash = Arrays.hashCode(this.values);
    }

    int[] values() {
        return values;
    }

    /**
     * 就地改写为另一段排列并同步哈希。仅供 {@link DecodingCache} 复用单个「只读查找用」键实例，
     * 从而避免每次查表都新建键并克隆数组。调用方必须保证该实例没有同时被当作 map 的键存放。
     */
    void set(int[] next) {
        System.arraycopy(next, 0, values, 0, values.length);
        this.hash = Arrays.hashCode(values);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SequenceKey key && Arrays.equals(values, key.values);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
