package com.oasissmp.technoblade;

/**
 * What hitting Technoblade costs the attacker: health, though never taking
 * them below {@link #LEAST_HEALTH}, and a throw straight away from the pig.
 */
final class Retaliation {

    static final double LEAST_HEALTH = 1;

    /** A direction across the ground, one block long. */
    record Heading(double x, double z) { }

    private Retaliation() { }

    static double healthAfter(double health, double damage) {
        return Math.min(health, Math.max(LEAST_HEALTH, health - damage));
    }

    /**
     * Which way the attacker is thrown: along {@code dx}, {@code dz}, the way
     * from the pig to them, or, standing on the pig's own column, backwards
     * from the way their {@code yaw} faces.
     */
    static Heading away(double dx, double dz, float yaw) {
        double length = Math.hypot(dx, dz);
        if (length < 1e-6) {
            double radians = Math.toRadians(yaw);
            return new Heading(Math.sin(radians), -Math.cos(radians));
        }
        return new Heading(dx / length, dz / length);
    }
}
