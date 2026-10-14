package com.assessment.packing.experiment;

public final class CsvEscaperTest {
    public static void main(String[] args) {
        check(CsvEscaper.escape("plain").equals("plain"), "普通文本不应改变");
        check(CsvEscaper.escape("a,b").equals("\"a,b\""), "逗号字段必须加引号");
        check(CsvEscaper.escape("a\"b").equals("\"a\"\"b\""), "引号必须转义");
        check(CsvEscaper.escape("=1+1").equals("'=1+1"), "公式文本必须失活");
        check(CsvEscaper.escape("@SUM(A:A)").equals("'@SUM(A:A)"), "@公式必须失活");
        check(CsvEscaper.escape("-12.5").equals("-12.5"), "负数必须保持数值格式");
        check(CsvEscaper.escape("-1.2e-3").equals("-1.2e-3"), "科学计数法必须保持数值格式");
        System.out.println("CsvEscaperTest: 全部通过");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
