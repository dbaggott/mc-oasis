package com.oasissmp.technoblade;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

/**
 * Checks the crown's turn round the pig's neck: the neck stays put, a point
 * ahead of it swings the way the display's own yaw would turn it, and the
 * turn reads back as the one asked for.
 */
public final class CrownTest {

    private static final float NECK = 0.375f;
    private static final float CLOSE = 1e-4f;

    private CrownTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        for (float degrees : new float[] {0, 30, -45, 90, 179}) {
            Transformation turned = Crown.turnedAtNeck(degrees);
            Vector3f neck = apply(turned, new Vector3f(0, 0, NECK));
            check(failures, neck.distance(0, 0, NECK) < CLOSE, degrees + "°: the neck moved to " + neck);
            float back = Crown.turn(turned);
            check(failures, Math.abs(back - degrees) < 1e-3f, degrees + "°: read back as " + back + "°");
        }

        // Yaw 90 turns a mob from facing +z (south) to facing -x (west); the
        // display renders yaw y as a rotation of -y about +y, so does the turn.
        Vector3f ahead = apply(Crown.turnedAtNeck(90), new Vector3f(0, 0, NECK + 0.25f));
        check(failures, ahead.distance(-0.25f, 0, NECK) < CLOSE, "a 90° turn put the point ahead of the neck at " + ahead);

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("CrownTest passed");
    }

    // As the client applies it: translation, left rotation, scale, right rotation.
    private static Vector3f apply(Transformation t, Vector3f point) {
        Vector3f p = t.getRightRotation().transform(new Vector3f(point));
        p.mul(t.getScale());
        t.getLeftRotation().transform(p);
        return p.add(t.getTranslation());
    }

    private static void check(List<String> failures, boolean ok, String failure) {
        if (!ok) {
            failures.add(failure);
        }
    }
}
