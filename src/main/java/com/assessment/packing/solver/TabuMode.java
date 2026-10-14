package com.assessment.packing.solver;

public enum TabuMode {
    /** 博客复现：保存近期访问过的完整排列，不使用特赦。 */
    FULL_STATE,
    /** 保存近期交换过的无序零件对，优于历史最好时特赦。 */
    MOVE,
    /** 禁止被移动的零件短期返回原优先级位置，优于历史最好时特赦。 */
    POSITION_ATTRIBUTE,
    /** 禁止短期恢复一次交换所破坏的零件相对先后次序。 */
    RELATIVE_ORDER_ATTRIBUTE,
    /** 禁止短期恢复一次交换所破坏的有向相邻关系。 */
    ADJACENCY_ATTRIBUTE,
    /** 禁止零件短期回到旧的坐标、朝向或未装入状态。 */
    PLACEMENT_ATTRIBUTE
}
