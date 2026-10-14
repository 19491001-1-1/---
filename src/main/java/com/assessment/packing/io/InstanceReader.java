package com.assessment.packing.io;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class InstanceReader {
    public Instance read(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            int count = parse(requireLine(reader, "缺少零件数量"), 1)[0];
            if (count < 0) throw new IllegalArgumentException("零件数量不能为负数");

            int[] bin = parse(requireLine(reader, "缺少箱子尺寸"), 2);
            if (bin[0] <= 0 || bin[1] <= 0) throw new IllegalArgumentException("箱子尺寸必须为正数");

            List<Item> items = new ArrayList<>(count);
            for (int id = 1; id <= count; id++) {
                int[] values = parse(requireLine(reader, "缺少第" + id + "个零件"), 3);
                Item item = new Item(id, values[0], values[1]);
                if (item.area() != values[2]) {
                    throw new IllegalArgumentException("第" + id + "个零件面积错误：文件值="
                            + values[2] + "，计算值=" + item.area());
                }
                items.add(item);
            }

            String extra;
            while ((extra = reader.readLine()) != null) {
                if (!extra.isBlank()) throw new IllegalArgumentException("文件末尾存在多余数据：" + extra);
            }

            String fileName = path.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String name = dot < 0 ? fileName : fileName.substring(0, dot);
            return new Instance(name, bin[0], bin[1], items);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("文件包含非整数数据：" + path, e);
        }
    }

    private static String requireLine(BufferedReader reader, String message) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) return line.trim();
        }
        throw new IllegalArgumentException(message);
    }

    private static int[] parse(String line, int expected) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length != expected) {
            throw new IllegalArgumentException("期望" + expected + "个整数，实际得到" + parts.length + "个：" + line);
        }
        int[] result = new int[expected];
        for (int i = 0; i < expected; i++) result[i] = Integer.parseInt(parts[i]);
        return result;
    }
}
