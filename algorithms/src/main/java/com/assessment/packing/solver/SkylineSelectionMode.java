package com.assessment.packing.solver;

/** Skyline处理零件的方式：按排序顺序逐个处理，或每轮做GLOBAL全局选择。 */
public enum SkylineSelectionMode {
    SEQUENTIAL,
    GLOBAL
}
