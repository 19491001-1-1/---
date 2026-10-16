package com.assessment.packing.web.dto;

/** 历史与高级算法变体的只读档案。 */
public record AdvancedAlgorithmDto(
        String machineValue,
        String displayName,
        String family,
        String representative,
        String representativeName,
        String idea,
        boolean stochastic) {}
