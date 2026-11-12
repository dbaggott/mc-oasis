package com.oasissmp.technoblade;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a hit costing 6 health never takes the attacker below 1 or
 * raises them to it, and that the throw is one block long, away
 * from the pig, or backwards for an attacker on its column.
 */
public final class RetaliationTest {

    private RetaliationTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        health(failures, 20, 14);
        health(failures, 6, 1);
        health(failures, 3, 1);
        health(failures, 1, 1);
        health(failures, 0.5, 0.5);

        heading(failures, 3, 4, 0, 0.6, 0.8, "an attacker 3 east and 4 south of the pig");
        heading(failures, -5, 0, 180, -1, 0, "an attacker west of the pig, whichever way they face");
        heading(failures, 0, 0, 0, 0, -1, "an attacker on the pig's column facing south");
        heading(failures, 0, 0, 90, 1, 0, "an attacker on the pig's column facing west");

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("RetaliationTest passed");
    }

    private static void health(List<String> failures, double before, double expected) {
        double after = Retaliation.healthAfter(before, 6);
        if (after != expected) {
            failures.add("an attacker at " + before + " health: " + after + ", not " + expected);
        }
    }

    private static void heading(List<String> failures, double dx, double dz, float yaw, double x, double z,
            String what) {
        Retaliation.Heading away = Retaliation.away(dx, dz, yaw);
        if (Math.abs(away.x() - x) > 1e-9 || Math.abs(away.z() - z) > 1e-9) {
            failures.add(what + ": thrown along " + away + ", not " + x + ", " + z);
        }
    }
}
