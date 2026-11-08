package com.oasissmp.technoblade;

import java.util.List;

/**
 * What Technoblade's killer is left with: half of each food stack, rounded
 * down, and every slot that is then empty filled with poisonous potatoes.
 * Everything else, existing poisonous potatoes included, is left as it is.
 */
final class FoodRule {

    static final String POISONOUS_POTATO = "minecraft:poisonous_potato";
    static final int FILL = 64;

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
        if (slot.food()) {
            kept = slot.count() / 2 == 0 ? Slot.EMPTY : new Slot(slot.item(), slot.count() / 2, true);
        }
        return kept.isEmpty() ? new Slot(POISONOUS_POTATO, FILL, false) : kept;
    }
}
