package com.oasissmp.technoblade;

import java.util.List;
import java.util.Set;

/**
 * What Technoblade's killer is left with: half of each food stack, rounded
 * down, and every slot that is then empty filled with poisonous potatoes.
 * Everything else, golden apples and existing poisonous potatoes included, is
 * left as it is.
 */
final class FoodRule {

    static final String POISONOUS_POTATO = "minecraft:poisonous_potato";
    static final int FILL = 64;
    // Food left as it is.
    static final Set<String> KEPT = Set.of(POISONOUS_POTATO, "minecraft:golden_apple",
            "minecraft:enchanted_golden_apple");

    /** One inventory slot: what's in it, how many, and whether it counts as food. */
    record Slot(String item, int count, boolean food) {

        static final String AIR = "minecraft:air";
        static final Slot EMPTY = new Slot(AIR, 0, false);

        Slot {
            if (count < 0 || (count == 0) != item.equals(AIR)) {
                throw new IllegalArgumentException("A slot holds air or a positive count: " + item + " x" + count);
            }
        }

        boolean isEmpty() {
            return count == 0;
        }
    }

    private FoodRule() { }

    static List<Slot> punish(List<Slot> slots) {
        return slots.stream().map(FoodRule::punish).toList();
    }

    private static Slot punish(Slot slot) {
        Slot kept = slot;
        if (slot.food() && !KEPT.contains(slot.item())) {
            kept = slot.count() / 2 == 0 ? Slot.EMPTY : new Slot(slot.item(), slot.count() / 2, true);
        }
        return kept.isEmpty() ? new Slot(POISONOUS_POTATO, FILL, true) : kept;
    }
}
