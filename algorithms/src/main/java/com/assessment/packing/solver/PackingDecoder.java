package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;

import java.util.List;

/** 将搜索算法给出的零件优先级序列解码为合法排样。 */
public interface PackingDecoder {
    String name();

    Solution solveInPriorityOrder(Instance instance, List<Item> priority);

    /**
     * 轻量评分路径：只返回装入面积，不构造放置列表。
     *
     * <p>搜索过程中绝大多数候选排列只需要一个分数，最终优胜排列才需要完整布局。
     * 默认实现退化为完整解码后取面积，因此既有的解码器无需改动即可编译；
     * 实现方可在内部复用同一套解码逻辑来避免构造 {@link Solution} 的开销。
     */
    default long packedAreaInPriorityOrder(Instance instance, List<Item> priority) {
        return solveInPriorityOrder(instance, priority).packedArea();
    }
}
