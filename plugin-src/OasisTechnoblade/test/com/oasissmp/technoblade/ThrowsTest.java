package com.oasissmp.technoblade;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Checks that a thrown player's next fall is spared and only that one, that a
 * throw ends once they are back on the ground after leaving it and not while
 * they still stand where they were hit, that it ends after 10 seconds
 * whatever happened, and that players are thrown apart.
 */
public final class ThrowsTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private ThrowsTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        Throws thrown = new Throws();
        check(failures, !thrown.endsWithFall(ALEX, 0), "a fall with no throw is spared");
        thrown.start(ALEX, 100);
        check(failures, thrown.endsWithFall(ALEX, 130), "the fall from a throw isn't spared");
        check(failures, !thrown.endsWithFall(ALEX, 131), "a second fall after one throw is spared");
        check(failures, thrown.isEmpty(), "a throw outlasts its fall");

        thrown = new Throws();
        thrown.start(ALEX, 100);
        thrown.land(101, Set.of(ALEX)::contains);
        check(failures, !thrown.isEmpty(), "a throw ends while the player hasn't left the ground yet");
        thrown.land(102, Set.<UUID>of()::contains);
        thrown.land(103, Set.<UUID>of()::contains);
        check(failures, !thrown.isEmpty(), "a throw ends in the air");
        thrown.land(110, Set.of(ALEX)::contains);
        check(failures, thrown.isEmpty(), "a throw goes on after the player landed");
        check(failures, !thrown.endsWithFall(ALEX, 150), "a fall after landing unhurt is spared");

        thrown = new Throws();
        thrown.start(ALEX, 100);
        thrown.land(299, Set.of(ALEX)::contains);
        check(failures, !thrown.isEmpty(), "a throw ends before its 10 seconds on the ground");
        thrown.land(300, Set.of(ALEX)::contains);
        check(failures, thrown.isEmpty(), "a throw outlasts its 10 seconds");

        thrown = new Throws();
        thrown.start(ALEX, 100);
        check(failures, !thrown.endsWithFall(ALEX, 300), "a fall 10 seconds after the throw is spared");

        thrown = new Throws();
        thrown.start(ALEX, 100);
        thrown.start(STEVE, 100);
        check(failures, thrown.endsWithFall(STEVE, 120), "a second thrown player's fall isn't spared");
        check(failures, thrown.endsWithFall(ALEX, 120), "one player's fall ended another's throw");

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("ThrowsTest passed");
    }

    private static void check(List<String> failures, boolean holds, String failure) {
        if (!holds) {
            failures.add(failure);
        }
    }
}
