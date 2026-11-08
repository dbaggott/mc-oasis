package com.oasissmp.technoblade;

import com.oasissmp.technoblade.FoodRule.Slot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checks the slots Technoblade's killer is left with: half of each food stack
 * rounded down, every slot then empty filled with poisonous potatoes, and
 * everything else as it was.
 */
public final class FoodRuleTest {

    private static final Slot POTATOES = new Slot(FoodRule.POISONOUS_POTATO, 64, false);

    private FoodRuleTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        Slot steak = new Slot("minecraft:cooked_beef", 64, true);
        Slot bread = new Slot("minecraft:bread", 3, true);
        Slot apple = new Slot("minecraft:golden_apple", 1, true);
        Slot sword = new Slot("minecraft:diamond_sword", 1, false);
        Slot stone = new Slot("minecraft:stone", 17, false);
        Slot partialPotatoes = new Slot(FoodRule.POISONOUS_POTATO, 5, false);

        punishes(failures, "an even food stack", List.of(steak), List.of(new Slot("minecraft:cooked_beef", 32, true)));
        punishes(failures, "an odd food stack", List.of(bread), List.of(new Slot("minecraft:bread", 1, true)));
        punishes(failures, "a lone food item", List.of(apple), List.of(POTATOES));
        punishes(failures, "items that aren't food", List.of(sword, stone), List.of(sword, stone));
        punishes(failures, "a partial stack of poisonous potatoes", List.of(partialPotatoes), List.of(partialPotatoes));
        punishes(failures, "an empty slot", List.of(Slot.EMPTY), List.of(POTATOES));

        // 36 storage slots, then the offhand, empty.
        List<Slot> inventory = new ArrayList<>(List.of(steak, bread, apple, sword, stone, partialPotatoes));
        inventory.addAll(Collections.nCopies(37 - inventory.size(), Slot.EMPTY));
        List<Slot> expected = new ArrayList<>(List.of(new Slot("minecraft:cooked_beef", 32, true),
                new Slot("minecraft:bread", 1, true), POTATOES, sword, stone, partialPotatoes));
        expected.addAll(Collections.nCopies(37 - expected.size(), POTATOES));
        List<Slot> punished = FoodRule.punish(inventory);
        check(failures, punished.equals(expected), "a whole inventory, the offhand empty: " + punished);
        check(failures, punished.getLast().equals(POTATOES), "the empty offhand wasn't filled: " + punished.getLast());
        check(failures, punished.stream().noneMatch(Slot::isEmpty), "a slot was left empty: " + punished);

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("FoodRuleTest passed");
    }

    private static void punishes(List<String> failures, String what, List<Slot> before, List<Slot> after) {
        List<Slot> punished = FoodRule.punish(before);
        check(failures, punished.equals(after), what + ": " + before + " became " + punished + ", not " + after);
    }

    private static void check(List<String> failures, boolean ok, String failure) {
        if (!ok) {
            failures.add(failure);
        }
    }
}
