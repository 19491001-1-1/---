package com.assessment.packing.solver;

public enum PackingDecoderType {
    SHELF_BAF,
    FLOOR_CEILING,
    BOTTOM_LEFT,
    CONTINUOUS_BOTTOM_LEFT,
    SKYLINE,
    SKYLINE_BL_WM,
    MAXRECTS,
    GUILLOTINE;

    public static PackingDecoderType parse(String value) {
        String normalized = value.trim().toUpperCase().replace('-', '_');
        return switch (normalized) {
            case "SHELF" -> SHELF_BAF;
            case "SHELF_FC" -> FLOOR_CEILING;
            case "MAXRECTS_BEST" -> MAXRECTS;
            case "GUILLOTINE_BSSF_SAS_RM" -> GUILLOTINE;
            default -> valueOf(normalized);
        };
    }
}
