package com.assessment.packing.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 基准实例文件的发现和稳定自然排序。 */
public final class InstanceFiles {
    private static final Pattern GCUT_FILE = Pattern.compile("gcut(\\d+)\\.txt", Pattern.CASE_INSENSITIVE);

    private InstanceFiles() {}

    public static List<Path> listGcutFiles(Path directory) throws IOException {
        if (directory == null) throw new IllegalArgumentException("数据目录不能为空");
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("数据目录不存在或不是目录：" + directory);
        }
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> GCUT_FILE.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(
                            InstanceFiles::numericPart,
                            InstanceFiles::compareUnsignedDecimalStrings)
                            .thenComparing(path -> path.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(path -> path.getFileName().toString()))
                    .toList();
        }
        if (files.isEmpty()) throw new IllegalArgumentException("目录中未找到gcut*.txt：" + directory);
        return files;
    }

    private static String numericPart(Path path) {
        Matcher matcher = GCUT_FILE.matcher(path.getFileName().toString());
        if (!matcher.matches()) throw new IllegalArgumentException("不是gcut实例文件：" + path);
        return stripLeadingZeros(matcher.group(1));
    }

    private static String stripLeadingZeros(String value) {
        int index = 0;
        while (index < value.length() - 1 && value.charAt(index) == '0') index++;
        return value.substring(index);
    }

    private static int compareUnsignedDecimalStrings(String first, String second) {
        int lengthComparison = Integer.compare(first.length(), second.length());
        return lengthComparison != 0 ? lengthComparison : first.compareTo(second);
    }
}
