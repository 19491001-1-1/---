package com.assessment.packing.web.dto;

import java.util.List;

public final class InstanceDtos {
    private InstanceDtos() {}

    public record Summary(
            String id,
            String name,
            int itemCount,
            int binWidth,
            int binHeight,
            long totalItemArea,
            String source) {}

    public record Detail(
            String id,
            String name,
            int itemCount,
            int binWidth,
            int binHeight,
            long binArea,
            long totalItemArea,
            String source,
            List<Item> items) {
        public Detail {
            items = List.copyOf(items);
        }
    }

    public record Item(int id, int width, int height, long area) {}
}
