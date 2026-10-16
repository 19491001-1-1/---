package com.assessment.packing.web.dto;

public record DecoderDto(
        String machineValue,
        String chineseName,
        String englishName,
        String description,
        String guide,
        boolean rotationSupported,
        String spaceStructure) {}
