package com.assessment.packing.solver;

/** Shelf 的六种选层规则。 */
public enum ShelfHeuristic {
    NEXT_FIT("NF"),
    FIRST_FIT("FF"),
    BEST_WIDTH_FIT("BWF"),
    BEST_HEIGHT_FIT("BHF"),
    BEST_AREA_FIT("BAF"),
    WORST_WIDTH_FIT("WWF");

    private final String abbreviation;

    ShelfHeuristic(String abbreviation) {
        this.abbreviation = abbreviation;
    }

    public String abbreviation() {
        return abbreviation;
    }
}
