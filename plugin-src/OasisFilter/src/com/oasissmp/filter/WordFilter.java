package com.oasissmp.filter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds blocked words in text, however a player disguises them.
 *
 * <p>Text is first reduced to lowercase words of a-z: accents are dropped,
 * digits, symbols and Cyrillic or Greek letters that imitate a letter become
 * that letter, and every other character separates words. Letters spelled out
 * one at a time, "n i g g e r" or "n.i.g.g.e.r", are joined back into one word.
 * A blocked term matches with any of its letters repeated, so "niiigger"
 * matches "nigger", though "niger" doesn't.
 *
 * <p>The lists are three files of one entry per line, {@code #} starting a
 * comment:
 * <ul>
 *   <li>{@code words.txt}: terms that match whole words only, so "spic"
 *       doesn't block "spicy". Plurals and other forms are entries of their
 *       own. An entry of several words matches them written apart or together.
 *   <li>{@code fragments.txt}: terms that match inside longer words too.
 *   <li>{@code allowed.txt}: words skipped before matching, for the innocent
 *       words a fragment is part of.
 * </ul>
 *
 * <p>No term matches across the gap between two words of more than one
 * letter, so "who reads" isn't "whoreads". Inside spelled-out letters there
 * are no gaps to go by, and a whole-word term matches anywhere in them.
 */
final class WordFilter {

    // Each maps to the letter it imitates. A character not here or in a-z is a
    // separator, so mapping one changes where words break as well as which
    // letters they hold.
    private static final Map<Integer, Character> LOOKALIKES = Map.ofEntries(
            Map.entry((int) '0', 'o'),
            Map.entry((int) '1', 'i'),
            Map.entry((int) '3', 'e'),
            Map.entry((int) '4', 'a'),
            Map.entry((int) '5', 's'),
            Map.entry((int) '6', 'g'),
            Map.entry((int) '7', 't'),
            Map.entry((int) '8', 'b'),
            Map.entry((int) '9', 'g'),
            Map.entry((int) '@', 'a'),
            Map.entry((int) '$', 's'),
            Map.entry((int) 'q', 'g'),
            // Cyrillic
            Map.entry(0x0430, 'a'),
            Map.entry(0x0432, 'b'),
            Map.entry(0x0435, 'e'),
            Map.entry(0x0456, 'i'),
            Map.entry(0x0458, 'j'),
            Map.entry(0x043A, 'k'),
            Map.entry(0x043C, 'm'),
            Map.entry(0x043D, 'h'),
            Map.entry(0x043E, 'o'),
            Map.entry(0x043F, 'n'),
            Map.entry(0x0440, 'p'),
            Map.entry(0x0441, 'c'),
            Map.entry(0x0442, 't'),
            Map.entry(0x0443, 'y'),
            Map.entry(0x0445, 'x'),
            Map.entry(0x0455, 's'),
            Map.entry(0x0501, 'd'),
            // Greek
            Map.entry(0x03B1, 'a'),
            Map.entry(0x03B2, 'b'),
            Map.entry(0x03B5, 'e'),
            Map.entry(0x03B9, 'i'),
            Map.entry(0x03BA, 'k'),
            Map.entry(0x03BD, 'v'),
            Map.entry(0x03BF, 'o'),
            Map.entry(0x03C1, 'p'),
            Map.entry(0x03C4, 't'),
            Map.entry(0x03C5, 'u'),
            Map.entry(0x03C7, 'x'),
            // Latin letters outside a-z that NFKD leaves alone
            Map.entry(0x0131, 'i'),
            Map.entry(0x0261, 'g'));

    // Stand for an 'i' only between two letters: "n!gger", but not "fag!", which
    // as "fagi" would no longer be the whole word "fag".
    private static final Set<Integer> INNER_I = Set.of((int) '!', (int) '|');

    private final List<List<Word>> wholeWords = new ArrayList<>();
    private final List<List<Run>> fragments = new ArrayList<>();
    private final Set<String> allowed;

    private WordFilter(List<String> words, List<String> fragments, Set<String> allowed) {
        this.allowed = allowed;
        for (String entry : words) {
            List<Word> apart = words(entry, Set.of());
            wholeWords.add(apart);
            if (apart.size() > 1) {
                wholeWords.add(words(entry.replace(" ", ""), Set.of()));
            }
        }
        for (String entry : fragments) {
            this.fragments.add(runs(entry));
        }
    }

    static WordFilter load(Path folder) throws IOException {
        Set<String> allowed = new HashSet<>();
        for (String word : entries(folder.resolve("allowed.txt"))) {
            if (word.contains(" ")) {
                throw new IllegalArgumentException(
                        "allowed.txt: '" + word + "' isn't one word once normalized");
            }
            allowed.add(word);
        }
        List<String> fragments = entries(folder.resolve("fragments.txt"));
        for (String fragment : fragments) {
            if (fragment.contains(" ")) {
                throw new IllegalArgumentException(
                        "fragments.txt: '" + fragment + "' isn't one word once normalized");
            }
        }
        return new WordFilter(entries(folder.resolve("words.txt")), fragments, allowed);
    }

    boolean blocks(String text) {
        List<Word> words = words(normalize(text), allowed);
        for (int at = 0; at < words.size(); at++) {
            Word word = words.get(at);
            for (List<Run> fragment : fragments) {
                if (contains(word.runs, fragment)) {
                    return true;
                }
            }
            for (List<Word> term : wholeWords) {
                if (matchesAt(words, at, term)
                        || (word.spelledOut && term.size() == 1
                                && contains(word.runs, term.getFirst().runs))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Lowercase a-z for every letter, and a space for every separator. */
    static String normalize(String text) {
        int[] codePoints = Normalizer.normalize(text, Normalizer.Form.NFKD).codePoints()
                .filter(codePoint -> !isIgnored(codePoint))
                .map(Character::toLowerCase)
                .toArray();
        StringBuilder normalized = new StringBuilder(codePoints.length);
        for (int i = 0; i < codePoints.length; i++) {
            int codePoint = codePoints[i];
            if (INNER_I.contains(codePoint)) {
                boolean inner = i > 0 && i < codePoints.length - 1
                        && letter(codePoints[i - 1]) != ' ' && letter(codePoints[i + 1]) != ' ';
                normalized.append(inner ? 'i' : ' ');
            } else {
                normalized.append(letter(codePoint));
            }
        }
        return normalized.toString();
    }

    // The a-z letter a lowercase code point is or imitates, or a space.
    private static char letter(int codePoint) {
        Character lookalike = LOOKALIKES.get(codePoint);
        if (lookalike != null) {
            return lookalike;
        }
        return codePoint >= 'a' && codePoint <= 'z' ? (char) codePoint : ' ';
    }

    // Accent marks, which NFKD splits from the letters they're on, and
    // invisible characters such as zero-width spaces: neither separates words.
    private static boolean isIgnored(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.FORMAT;
    }

    private static List<String> entries(Path file) throws IOException {
        List<String> entries = new ArrayList<>();
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String entry = normalize(line).strip().replaceAll(" +", " ");
            if (entry.isEmpty()) {
                throw new IllegalArgumentException(
                        file.getFileName() + ":" + (i + 1) + ": '" + line + "' has no letters");
            }
            entries.add(entry);
        }
        return entries;
    }

    /**
     * The words of normalized text, the ones in {@code allowed} left out, and
     * each unbroken series of one-letter words joined into one spelled-out word.
     */
    private static List<Word> words(String normalized, Set<String> allowed) {
        List<Word> words = new ArrayList<>();
        StringBuilder letters = new StringBuilder();
        for (String token : normalized.split(" +")) {
            if (token.length() == 1) {
                letters.append(token);
                continue;
            }
            addLetters(words, letters);
            if (!token.isEmpty() && !allowed.contains(token)) {
                words.add(new Word(runs(token), false));
            }
        }
        addLetters(words, letters);
        return words;
    }

    private static void addLetters(List<Word> words, StringBuilder letters) {
        if (!letters.isEmpty()) {
            words.add(new Word(runs(letters.toString()), letters.length() > 1));
            letters.setLength(0);
        }
    }

    private static List<Run> runs(String word) {
        List<Run> runs = new ArrayList<>();
        for (int i = 0; i < word.length(); i++) {
            char letter = word.charAt(i);
            if (!runs.isEmpty() && runs.getLast().letter == letter) {
                runs.set(runs.size() - 1, new Run(letter, runs.getLast().count + 1));
            } else {
                runs.add(new Run(letter, 1));
            }
        }
        return runs;
    }

    private static boolean matchesAt(List<Word> words, int at, List<Word> term) {
        if (at + term.size() > words.size()) {
            return false;
        }
        for (int i = 0; i < term.size(); i++) {
            List<Run> text = words.get(at + i).runs;
            List<Run> expected = term.get(i).runs;
            if (text.size() != expected.size() || !runsMatchAt(text, 0, expected)) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(List<Run> text, List<Run> expected) {
        for (int at = 0; at + expected.size() <= text.size(); at++) {
            if (runsMatchAt(text, at, expected)) {
                return true;
            }
        }
        return false;
    }

    // A letter may be repeated more often in the text than in the term.
    private static boolean runsMatchAt(List<Run> text, int at, List<Run> expected) {
        for (int i = 0; i < expected.size(); i++) {
            Run actual = text.get(at + i);
            if (actual.letter != expected.get(i).letter
                    || actual.count < expected.get(i).count) {
                return false;
            }
        }
        return true;
    }

    /** One letter, and how many times it's repeated. */
    private record Run(char letter, int count) { }

    /** A word as runs of letters, and whether it was spelled out letter by letter. */
    private record Word(List<Run> runs, boolean spelledOut) { }
}
