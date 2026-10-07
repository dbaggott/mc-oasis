package com.oasissmp.filter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Players' names, which are taken out of text before it's checked. A name was
 * approved when its player was allowlisted, so it never counts as a blocked
 * word, even where '_' splits it into words that are: "/msg Hell_Raiser hi".
 */
final class PlayerNames {

    private final Set<String> names;
    private final Pattern pattern;

    PlayerNames(Collection<String> names) {
        this.names = names.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        // A name only where it stands whole, not inside a longer word or name.
        this.pattern = this.names.isEmpty() ? null : Pattern.compile(
                "(?<![A-Za-z0-9_])(?:"
                        + this.names.stream().map(Pattern::quote).collect(Collectors.joining("|"))
                        + ")(?![A-Za-z0-9_])",
                Pattern.CASE_INSENSITIVE);
    }

    /** These names and one more. */
    PlayerNames with(String name) {
        if (names.contains(name.toLowerCase(Locale.ROOT))) {
            return this;
        }
        Set<String> more = new HashSet<>(names);
        more.add(name);
        return new PlayerNames(more);
    }

    /** The text with each name in it replaced by a space. */
    String removeFrom(String text) {
        return pattern == null ? text : pattern.matcher(text).replaceAll(" ");
    }
}
