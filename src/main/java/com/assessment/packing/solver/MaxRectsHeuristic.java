package com.assessment.packing.solver;

/** MaxRects 选择“空闲矩形 × 零件朝向”候选时使用的启发式。 */
public enum MaxRectsHeuristic {
    /** Best Short Side Fit：先最小化短边剩余，再最小化长边剩余。 */
    BSSF,
    /** Best Long Side Fit：先最小化长边剩余，再最小化短边剩余。 */
    BLSF,
    /** Best Area Fit：先最小化面积剩余，再用 BSSF 打破平局。 */
    BAF,
    /** Bottom-Left：先最小化放置后的顶边，再最小化横坐标。 */
    BL,
    /** Contact Point：最大化与板材边界和已放矩形接触的总边长。 */
    CP
}
