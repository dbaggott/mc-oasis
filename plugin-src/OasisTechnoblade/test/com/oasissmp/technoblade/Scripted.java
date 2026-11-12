package com.oasissmp.technoblade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * A random source for tests that gives back the values it was handed, in
 * order, and keeps the bound each {@code nextInt} asked for.
 */
final class Scripted implements RandomGenerator {

    private final Deque<Integer> ints = new ArrayDeque<>();
    private final Deque<Boolean> booleans = new ArrayDeque<>();
    final List<Integer> bounds = new ArrayList<>();

    Scripted thenInts(Integer... next) {
        ints.addAll(List.of(next));
        return this;
    }

    Scripted thenBooleans(Boolean... next) {
        booleans.addAll(List.of(next));
        return this;
    }

    @Override
    public int nextInt(int bound) {
        bounds.add(bound);
        return ints.remove();
    }

    @Override
    public boolean nextBoolean() {
        return booleans.remove();
    }

    @Override
    public long nextLong() {
        throw new UnsupportedOperationException("Not scripted");
    }
}
