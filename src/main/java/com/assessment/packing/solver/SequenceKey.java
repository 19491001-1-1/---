package com.assessment.packing.solver;

import java.util.Arrays;

/**
 * 以零件编号排列作为不可变哈希键。hash 在构造时预计算，避免反复遍历排列。
 *
 * <p>这是搜索层唯一的一份排列键实现，取代原先散落在 ACO、ILS、GA 内部的三份重复定义。
 */
final class SequenceKey {
    private final int[] values;
    private final int hash;

    SequenceKey(int[] values) {
        this.values = values.clone();
        this.hash = Arrays.hashCode(this.values);
    }

    int[] values() {
        return values;
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
