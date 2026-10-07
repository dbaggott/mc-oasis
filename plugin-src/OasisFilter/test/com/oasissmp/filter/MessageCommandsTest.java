package com.oasissmp.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Checks which command lines MessageCommands takes for sending a message. */
public final class MessageCommandsTest {

    private static final Map<String, Boolean> CASES = Map.ofEntries(
            Map.entry("/msg Steve hello", true),
            Map.entry("/tell Steve hello", true),
            Map.entry("/w Steve hello", true),
            Map.entry("/me waves", true),
            Map.entry("/say hello", true),
            Map.entry("/teammsg hello", true),
            Map.entry("/tm hello", true),
            Map.entry("/MSG Steve hello", true),
            Map.entry("/minecraft:msg Steve hello", true),
            Map.entry("/msg", true),
            Map.entry("/team add reds", false),
            Map.entry("/rg define spawn", false),
            Map.entry("/minecraft:team add reds", false),
            Map.entry("/message Steve hello", false),
            Map.entry("/", false));

    private MessageCommandsTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();
        CASES.forEach((line, expected) -> {
            if (MessageCommands.sends(line) != expected) {
                failures.add("'" + line + "' should " + (expected ? "" : "not ") + "send a message");
            }
        });
        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println(CASES.size() + " message command cases right");
    }
}
