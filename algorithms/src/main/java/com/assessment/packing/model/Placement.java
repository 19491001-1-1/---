package com.assessment.packing.model;

public record Placement(Item item, int x, int y, boolean rotated) {
    public Placement {
        if (item == null) throw new IllegalArgumentException("零件不能为空");
    }

    public int width() {
        return rotated ? item.height() : item.width();
    }

    public int height() {
        return rotated ? item.width() : item.height();
    }

    public long area() {
        return item.area();
    }
}
