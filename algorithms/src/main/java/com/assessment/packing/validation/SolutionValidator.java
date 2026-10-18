package com.assessment.packing.validation;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SolutionValidator {
    public ValidationResult validate(Instance instance, Solution solution, boolean rotationAllowed) {
        List<String> errors = new ArrayList<>();
        Map<Integer, Item> inputItems = new HashMap<>();
        for (Item item : instance.items()) inputItems.put(item.id(), item);
        Set<Integer> used = new HashSet<>();

        for (Placement p : solution.placements()) {
            Item original = inputItems.get(p.item().id());
            if (original == null || !original.equals(p.item())) {
                errors.add("使用了不存在或属性不一致的零件：" + p.item().id());
                continue;
            }
            if (!used.add(p.item().id())) errors.add("零件重复使用：" + p.item().id());
            if (p.rotated() && !rotationAllowed) errors.add("禁止旋转时旋转了零件：" + p.item().id());
            if (p.x() < 0 || p.y() < 0
                    || (long) p.x() + p.width() > instance.binWidth()
                    || (long) p.y() + p.height() > instance.binHeight()) {
                errors.add("零件越界：" + p.item().id());
            }
        }

        List<Placement> ps = solution.placements();
        for (int i = 0; i < ps.size(); i++) {
            for (int j = i + 1; j < ps.size(); j++) {
                if (overlaps(ps.get(i), ps.get(j))) {
                    errors.add("零件重叠：" + ps.get(i).item().id() + "与" + ps.get(j).item().id());
                }
            }
        }
        return new ValidationResult(errors.isEmpty(), errors);
    }

    public static boolean overlaps(Placement a, Placement b) {
        return (long) a.x() < (long) b.x() + b.width()
                && (long) b.x() < (long) a.x() + a.width()
                && (long) a.y() < (long) b.y() + b.height()
                && (long) b.y() < (long) a.y() + a.height();
    }
}
