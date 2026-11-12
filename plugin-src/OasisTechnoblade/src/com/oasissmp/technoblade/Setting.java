package com.oasissmp.technoblade;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The knobs {@code /technoblade config} can turn, and what they mean when
 * nobody has turned them. Each belongs to one {@link Subject}, which is how
 * the command names it: {@code rumor interval}, {@code stare reach}. Adding
 * one is an entry here: the command reads its subject, name, bounds, default
 * and unit off this table.
 */
enum Setting {
    /** What hitting Technoblade costs the attacker, though never their last half heart. 2 is a heart. */
    HIT_DAMAGE(Subject.HIT, "damage", 6, 0, 20, "health"),
    /**
     * How fast the attacker is thrown back across the ground, in hundredths
     * of a block a tick. 70 with a lift of 40 lands them about 5 blocks away
     * on flat ground.
     */
    HIT_SPEED(Subject.HIT, "speed", 70, 0, 400, "hundredths of a block a tick"),
    /** How fast the attacker is thrown upwards. */
    HIT_LIFT(Subject.HIT, "lift", 40, 0, 400, "hundredths of a block a tick"),

    /** How long a player waits between golden apples. 0 removes the wait. */
    FEED_COOLDOWN(Subject.FEED, "cooldown", 5, 0, 1440, "minutes"),
    /** The chance of a gold nugget with the apple. */
    FEED_NUGGET(Subject.FEED, "nugget", 10, 0, 100, "percent"),
    /** The chance of Luck or Speed with the apple, rolled apart from the nugget. */
    FEED_EFFECT(Subject.FEED, "effect", 10, 0, 100, "percent"),
    /** How long that Luck or Speed lasts. */
    FEED_EFFECT_DURATION(Subject.FEED, "effect_duration", 2, 1, 60, "minutes"),

    /** How long Technoblade guards a player after they feed it. 0 means it guards nobody. */
    GUARD_DURATION(Subject.GUARD, "duration", 5, 0, 1440, "minutes"),
    /** How near Technoblade a monster has to be to go for it in place of the player. */
    GUARD_REACH(Subject.GUARD, "reach", 16, 0, 128, "blocks"),

    /** How often, on average, players are told where Technoblade was seen. */
    RUMOR_INTERVAL(Subject.RUMOR, "interval", 30, 1, 1440, "minutes"),
    /** How much earlier or later than the interval each rumor can come. */
    RUMOR_JITTER(Subject.RUMOR, "jitter", 10, 0, 1440, "minutes"),
    /** How near Technoblade a player has to be for a rumor to name them. 0 means rumors only name biomes. */
    RUMOR_REACH(Subject.RUMOR, "reach", 128, 0, 512, "blocks"),

    /** How long Technoblade stares at a player who killed it. 0 means it doesn't. */
    STARE_DURATION(Subject.STARE, "duration", 10, 0, 600, "seconds"),
    /** How far from it the killer has to get for the stare to end early. */
    STARE_REACH(Subject.STARE, "reach", 32, 1, 128, "blocks");

    /** What a knob belongs to. */
    enum Subject {
        HIT, FEED, GUARD, RUMOR, STARE;

        /** The lowercase name this answers to on the command line. */
        String commandName() {
            return name().toLowerCase(Locale.ROOT);
        }

        List<Setting> settings() {
            return Arrays.stream(Setting.values()).filter(setting -> setting.subject == this).toList();
        }
    }

    private final Subject subject;
    private final String commandName;
    private final int defaultValue;
    private final int min;
    private final int max;
    private final String unit;

    Setting(Subject subject, String commandName, int defaultValue, int min, int max, String unit) {
        this.subject = subject;
        this.commandName = commandName;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.unit = unit;
    }

    Subject subject() {
        return subject;
    }

    /** The name this answers to on the command line, after its subject's. */
    String commandName() {
        return commandName;
    }

    int defaultValue() {
        return defaultValue;
    }

    int min() {
        return min;
    }

    int max() {
        return max;
    }

    String unit() {
        return unit;
    }
}
