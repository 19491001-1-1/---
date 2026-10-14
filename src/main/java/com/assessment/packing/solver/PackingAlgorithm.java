package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;

public interface PackingAlgorithm {
    String name();

    Solution solve(Instance instance);
}
