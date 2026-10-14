package com.assessment.packing.experiment;

/** 实验报告中使用的稳定算法标识。 */
public enum AlgorithmId {
    SHELF("Shelf-NF", false),
    SHELF_FF("Shelf-FF", false),
    SHELF_BWF("Shelf-BWF", false),
    SHELF_BHF("Shelf-BHF", false),
    SHELF_BAF("Shelf-BAF", false),
    SHELF_WWF("Shelf-WWF", false),
    SHELF_FC("Shelf-Floor-Ceiling", false),
    SHELF_NF_GLOBAL("Shelf-NF-GLOBAL", false),
    SHELF_FF_GLOBAL("Shelf-FF-GLOBAL", false),
    SHELF_BWF_GLOBAL("Shelf-BWF-GLOBAL", false),
    SHELF_BHF_GLOBAL("Shelf-BHF-GLOBAL", false),
    SHELF_BAF_GLOBAL("Shelf-BAF-GLOBAL", false),
    SHELF_WWF_GLOBAL("Shelf-WWF-GLOBAL", false),
    SHELF_NF_WM("Shelf-NF-WM", false),
    SHELF_FF_WM("Shelf-FF-WM", false),
    SHELF_BWF_WM("Shelf-BWF-WM", false),
    SHELF_BHF_WM("Shelf-BHF-WM", false),
    SHELF_BAF_WM("Shelf-BAF-WM", false),
    SHELF_WWF_WM("Shelf-WWF-WM", false),
    BOTTOM_LEFT("Bottom-Left", false),
    CONTINUOUS_BOTTOM_LEFT("Continuous BL", false),
    SKYLINE("Skyline", false),
    SKYLINE_BL("Skyline-BL-Global", false),
    SKYLINE_BF("Skyline-BF-Global", false),
    SKYLINE_BL_WM("Skyline-BL-WM", false),
    SKYLINE_BF_WM("Skyline-BF-WM", false),
    GUILLOTINE("Guillotine-BSSF-SAS-RM", false),
    MAXRECTS_BSSF("MaxRects-BSSF", false),
    MAXRECTS_BLSF("MaxRects-BLSF", false),
    MAXRECTS_BAF("MaxRects-BAF", false),
    MAXRECTS_BL("MaxRects-BL", false),
    MAXRECTS_CP("MaxRects-CP", false),
    MAXRECTS_BEST("MaxRects-BEST", false),
    TABU_FULL("Tabu-Full", true),
    TABU_MOVE("Tabu-Move", true),
    TABU_POSITION("Tabu-Position", true),
    TABU_ORDER("Tabu-Order", true),
    TABU_ADJACENCY("Tabu-Adjacency", true),
    TABU_PLACEMENT("Tabu-Placement", true),
    SA("Simulated Annealing", true),
    ACO("Ant Colony Optimization", true),
    ILS("Iterated Local Search", true),
    GA("Genetic Algorithm", true),
    ADAPTIVE_GA("Adaptive Genetic Algorithm", true),
    GA_SA("GA+SA", true),
    GA_TABU("GA+Tabu", true);

    private final String displayName;
    private final boolean stochastic;

    AlgorithmId(String displayName, boolean stochastic) {
        this.displayName = displayName;
        this.stochastic = stochastic;
    }

    public String displayName() {
        return displayName;
    }

    public boolean stochastic() {
        return stochastic;
    }

    public static AlgorithmId parse(String text) {
        String normalized = text.trim().toUpperCase().replace('-', '_');
        if (normalized.equals("NF") || normalized.equals("SHELF_NF")) return SHELF;
        if (normalized.equals("FC") || normalized.equals("FLOOR_CEILING")) return SHELF_FC;
        if (normalized.equals("BL")) return BOTTOM_LEFT;
        if (normalized.equals("CBL") || normalized.equals("CONTINUOUS_BL")) {
            return CONTINUOUS_BOTTOM_LEFT;
        }
        if (normalized.equals("SKY")) return SKYLINE;
        if (normalized.equals("SKY_BL")) return SKYLINE_BL;
        if (normalized.equals("SKY_BF")) return SKYLINE_BF;
        if (normalized.equals("SKY_BL_WM")) return SKYLINE_BL_WM;
        if (normalized.equals("SKY_BF_WM")) return SKYLINE_BF_WM;
        if (normalized.equals("BSSF")) return MAXRECTS_BSSF;
        if (normalized.equals("BLSF")) return MAXRECTS_BLSF;
        if (normalized.equals("BAF")) return MAXRECTS_BAF;
        if (normalized.equals("MR_BL")) return MAXRECTS_BL;
        if (normalized.equals("CP")) return MAXRECTS_CP;
        if (normalized.equals("MR_BEST") || normalized.equals("BEST")) return MAXRECTS_BEST;
        if (normalized.equals("GUIL")) return GUILLOTINE;
        if (normalized.equals("TS_FULL")) return TABU_FULL;
        if (normalized.equals("TS_MOVE")) return TABU_MOVE;
        if (normalized.equals("TABU_ATTRIBUTE") || normalized.equals("TS_ATTRIBUTE")) {
            return TABU_POSITION;
        }
        if (normalized.equals("TABU_ATTRIBUTE_ORDER")
                || normalized.equals("TS_ATTRIBUTE_ORDER")) return TABU_ORDER;
        if (normalized.equals("TABU_ATTRIBUTE_ADJACENCY")
                || normalized.equals("TS_ATTRIBUTE_ADJACENCY")) return TABU_ADJACENCY;
        if (normalized.equals("TABU_ATTRIBUTE_PLACEMENT")
                || normalized.equals("TS_ATTRIBUTE_PLACEMENT")) return TABU_PLACEMENT;
        if (normalized.equals("SIMULATED_ANNEALING") || normalized.equals("ANNEALING")) return SA;
        if (normalized.equals("ANT_COLONY") || normalized.equals("ANT_COLONY_OPTIMIZATION")) {
            return ACO;
        }
        if (normalized.equals("AGA") || normalized.equals("ADAPTIVE_GENETIC")) {
            return ADAPTIVE_GA;
        }
        if (normalized.equals("ITERATED_LOCAL_SEARCH")) return ILS;
        if (normalized.equals("GENETIC_ALGORITHM") || normalized.equals("GENETIC")) return GA;
        if (normalized.equals("ADAPTIVE_GENETIC_ALGORITHM")) return ADAPTIVE_GA;
        if (normalized.equals("MEMETIC_SA")) return GA_SA;
        if (normalized.equals("MEMETIC_TABU")) return GA_TABU;
        return valueOf(normalized);
    }
}
