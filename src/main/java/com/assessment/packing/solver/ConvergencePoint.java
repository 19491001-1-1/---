package com.assessment.packing.solver;

/** 搜索过程中一个可绘图、可导出的历史最好观测点。 */
public record ConvergencePoint(long step, long evaluations, long bestPackedArea) {
    public ConvergencePoint {
        if (step < 0 || evaluations < 0 || bestPackedArea < 0) {
            throw new IllegalArgumentException("收敛点的字段不能为负数");
        }
    }
}
