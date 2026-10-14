package com.assessment.packing.model;

public record Item(int id, int width, int height) {
    public Item {
        if (id <= 0) throw new IllegalArgumentException("零件编号必须为正数");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("零件尺寸必须为正数");
    }

    public long area() {
        return (long) width * height;
    }
}
