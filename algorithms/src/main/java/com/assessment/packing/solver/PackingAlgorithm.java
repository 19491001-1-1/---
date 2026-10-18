package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;

public interface PackingAlgorithm {
    String name();

    Solution solve(Instance instance);

    /**
     * 轻量评分：只需要装入面积、不需要完整布局时使用。
     *
     * <p>搜索过程里绝大多数候选排列只用来比较面积，构造整张放置列表纯属浪费。默认实现
     * 退化为完整解码后取面积，实现方可在内部复用同一套放置逻辑来省掉这部分开销。
     * 覆写必须保证返回面积与 {@link #solve} 的 {@code packedArea()} 完全一致。
     */
    default long packedAreaOf(Instance instance) {
        return solve(instance).packedArea();
    }
}
