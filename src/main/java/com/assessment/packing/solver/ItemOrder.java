package com.assessment.packing.solver;

public enum ItemOrder {
    INPUT,
    HEIGHT_DESC,
    AREA_ASC,
    AREA_DESC,
    SHORT_SIDE_ASC,
    SHORT_SIDE_DESC,
    LONG_SIDE_ASC,
    LONG_SIDE_DESC,
    PERIMETER_ASC,
    PERIMETER_DESC,
    SIDE_DIFFERENCE_ASC,
    SIDE_DIFFERENCE_DESC,
    SIDE_RATIO_ASC,
    SIDE_RATIO_DESC;

    /**
     * 命令行友好解析，并兼容常用的排序缩写。
     */
    public static ItemOrder parse(String text) {
        String value = text.trim().toUpperCase().replace('-', '_');
        return switch (value) {
            case "ASCA", "AREA_ASCENDING" -> AREA_ASC;
            case "DESCA", "AREA_DESCENDING" -> AREA_DESC;
            case "ASCSS", "SHORT_ASC" -> SHORT_SIDE_ASC;
            case "DESCSS", "SHORT_DESC" -> SHORT_SIDE_DESC;
            case "ASCLS", "LONG_ASC" -> LONG_SIDE_ASC;
            case "DESCLS", "LONG_DESC" -> LONG_SIDE_DESC;
            case "ASCP", "ASCPERIM", "PERIM_ASC" -> PERIMETER_ASC;
            case "DESCP", "DESCPERIM", "PERIM_DESC" -> PERIMETER_DESC;
            case "ASCD", "ASCDIFF", "DIFF_ASC" -> SIDE_DIFFERENCE_ASC;
            case "DESCD", "DESCDIFF", "DIFF_DESC" -> SIDE_DIFFERENCE_DESC;
            case "ASCR", "ASCRATIO", "RATIO_ASC" -> SIDE_RATIO_ASC;
            case "DESCR", "DESCRATIO", "RATIO_DESC" -> SIDE_RATIO_DESC;
            default -> valueOf(value);
        };
    }
}
