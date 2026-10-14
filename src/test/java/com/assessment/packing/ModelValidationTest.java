package com.assessment.packing;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;
import com.assessment.packing.validation.SolutionValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelValidationTest {
    private final SolutionValidator validator = new SolutionValidator();

    @Test
    void rejectsDuplicateItemIds() {
        assertThrows(IllegalArgumentException.class, () -> new Instance(
                "duplicate", 10, 10, List.of(new Item(1, 2, 2), new Item(1, 3, 3))));
    }

    @Test
    void rejectsNullCollections() {
        assertThrows(IllegalArgumentException.class,
                () -> new Instance("null-items", 10, 10, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Solution("solver", null, 0));
    }

    @Test
    void detectsCoordinateOverflow() {
        Item item = new Item(1, 20, 20);
        Instance instance = new Instance("overflow", Integer.MAX_VALUE, 100, List.of(item));
        Placement placement = new Placement(item, Integer.MAX_VALUE - 10, 0, false);
        var result = validator.validate(
                instance, new Solution("solver", List.of(placement), 0), false);
        assertFalse(result.valid(), "坐标加宽度溢出时仍必须判定越界");
    }

    @Test
    void detectsOverlapWithoutOverflow() {
        Item first = new Item(1, 20, 20);
        Item second = new Item(2, 20, 20);
        Placement a = new Placement(first, Integer.MAX_VALUE - 15, 0, false);
        Placement b = new Placement(second, Integer.MAX_VALUE - 10, 0, false);
        assertTrue(SolutionValidator.overlaps(a, b), "极大坐标下重叠判断不能受整数溢出影响");
    }
}