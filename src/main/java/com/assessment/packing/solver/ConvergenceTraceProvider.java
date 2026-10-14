package com.assessment.packing.solver;

import java.util.List;

/** 由实验框架读取统一收敛轨迹，不把绘图逻辑耦合进算法。 */
public interface ConvergenceTraceProvider {
    List<ConvergencePoint> lastConvergenceTrace();
}
