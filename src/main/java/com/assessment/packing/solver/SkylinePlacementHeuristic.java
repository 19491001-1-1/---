package com.assessment.packing.solver;

/** Skyline 的两种基础放置评分。 */
public enum SkylinePlacementHeuristic {
    /** 优先让新矩形的顶边最低，再选择更靠左的位置。 */
    BOTTOM_LEFT,
    /** 优先让矩形下方因跨越凹槽而损失的面积最小，再使用左下规则。 */
    BEST_FIT
}
