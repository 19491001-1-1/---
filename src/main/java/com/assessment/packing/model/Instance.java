package com.assessment.packing.model;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public record Instance(String name, int binWidth, int binHeight, List<Item> items) {
    public Instance {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("实例名称不能为空");
        if (binWidth <= 0 || binHeight <= 0) throw new IllegalArgumentException("箱子尺寸必须为正数");
        if (items == null) throw new IllegalArgumentException("零件列表不能为空");
        items = List.copyOf(items);
        Set<Integer> ids = new HashSet<>();
        for (Item item : items) {
            if (!ids.add(item.id())) {
                throw new IllegalArgumentException("零件编号不能重复：" + item.id());
            }
        }
    }

    public long binArea() {
        return (long) binWidth * binHeight;
    }
}
