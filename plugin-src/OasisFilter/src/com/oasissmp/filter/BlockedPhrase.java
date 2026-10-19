package com.oasissmp.filter;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * The part of blocked text to show its player: the fewest words, as typed,
 * that are blocked on their own, the one that ends first. Letters spelled out
 * one at a time are one word to {@link WordFilter}, so the phrase takes in all
 * of them, not only the ones it needs: "n i g g e r", not "n i g g".
 */
final class BlockedPhrase {

    private BlockedPhrase() { }

    /** The phrase of {@code text}, which {@code blocks} must block, that it blocks. */
    static String in(String text, Predicate<String> blocks) {
        List<String> words = Arrays.asList(text.strip().split("\\s+"));
        int end = 1;
        while (end < words.size() && !blocks.test(phrase(words, 0, end))) {
            end++;
        }
        int start = end - 1;
        while (start > 0 && !blocks.test(phrase(words, start, end))) {
            start--;
        }
        while (start > 0 && isLetter(words.get(start)) && isLetter(words.get(start - 1))) {
            start--;
        }
        while (end < words.size() && isLetter(words.get(end - 1)) && isLetter(words.get(end))) {
            end++;
        }
        return phrase(words, start, end);
    }

    private static boolean isLetter(String word) {
        return word.codePointCount(0, word.length()) == 1;
    }

    private static String phrase(List<String> words, int start, int end) {
        return String.join(" ", words.subList(start, end));
    }
}
