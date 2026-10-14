package com.assessment.packing.bench;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** 基准共享的实例装载与选择，避免每个基准类重复读取 gcut 文件。 */
public final class BenchInstances {
    private static final Map<String, Instance> CACHE = new LinkedHashMap<>();

    private BenchInstances() {}

    /** 从仓库根目录的 gcut01/ 读取，允许用 -Dgcut.dir=... 覆盖。 */
    public static synchronized Instance load(String name) {
        return CACHE.computeIfAbsent(name, key -> {
            Path directory = Path.of(System.getProperty("gcut.dir", "gcut01"));
            try {
                return new InstanceReader().read(directory.resolve(key + ".txt"));
            } catch (IOException e) {
                throw new IllegalStateException("无法读取基准实例：" + key, e);
            }
        });
    }
}
