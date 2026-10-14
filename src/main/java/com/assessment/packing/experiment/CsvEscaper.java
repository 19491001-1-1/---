package com.assessment.packing.experiment;

/** CSV字段编码，并阻止电子表格将外部文本解释为公式。 */
public final class CsvEscaper {
    private CsvEscaper() {}

    public static String escape(String value) {
        if (value == null) throw new IllegalArgumentException("CSV字段不能为空");
        String safe = formulaSafe(value);
        if (!safe.contains(",") && !safe.contains("\"")
                && !safe.contains("\n") && !safe.contains("\r")) return safe;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static String formulaSafe(String value) {
        if (value.isEmpty() || isPlainNumber(value)) return value;
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r'
                ? "'" + value : value;
    }

    private static boolean isPlainNumber(String value) {
        return value.matches("-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
    }
}
