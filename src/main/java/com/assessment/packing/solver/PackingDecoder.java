package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;

import java.util.List;

/** 将搜索算法给出的零件优先级序列解码为合法排样。 */
public interface PackingDecoder {
    String name();

    Solution solveInPriorityOrder(Instance instance, List<Item> priority);
}
