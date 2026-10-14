package com.assessment.packing.experiment;

/** QUICK用于冒烟测试，STANDARD用于正式实验。 */
public enum ExperimentProfile {
    QUICK,
    STANDARD;

    public static ExperimentProfile parse(String text) {
        return valueOf(text.trim().toUpperCase());
    }
}
