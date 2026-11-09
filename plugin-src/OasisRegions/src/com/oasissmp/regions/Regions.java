package com.oasissmp.regions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The regions in {@code regions.txt}: each a {@code [name]} line, a
 * {@code corners x,y,z x,y,z} line, and a {@code <flag> allow|deny} or
 * {@code <flag> "<text>"} line per flag, with blank lines and lines starting
 * with {@code #} left out.
 */
record Regions(List<Region> regions) {

    static final String FILE_NAME = "regions.txt";

    private static final Pattern NAME = Pattern.compile("\\[([a-z0-9_-]+)]");
    private static final Pattern CORNERS = Pattern.compile(
            "corners (-?\\d+),(-?\\d+),(-?\\d+) (-?\\d+),(-?\\d+),(-?\\d+)");
    private static final Pattern FLAG = Pattern.compile("([a-z-]+) (allow|deny)");
    // Everything between the first and last quote, so the text can hold quotes.
    private static final Pattern TEXT_FLAG = Pattern.compile("([a-z-]+) \"(.*)\"");

    record Corner(int x, int y, int z) { }

    /**
     * A region, with each allow/deny flag's value, "allow" or "deny", and each
     * text flag's text, each in file order.
     */
    record Region(String name, Corner first, Corner second, Map<String, String> flags,
            Map<String, String> textFlags) {

        Region {
            flags = Collections.unmodifiableMap(new LinkedHashMap<>(flags));
            textFlags = Collections.unmodifiableMap(new LinkedHashMap<>(textFlags));
        }
    }

    Regions {
        regions = List.copyOf(regions);
    }

    static Regions load(Path dataFolder) throws IOException {
        return parse(Files.readAllLines(dataFolder.resolve(FILE_NAME)));
    }

    static Regions parse(List<String> lines) {
        List<Region> regions = new ArrayList<>();
        Set<String> names = new HashSet<>();
        String name = null;
        Corner first = null;
        Corner second = null;
        Map<String, String> flags = new LinkedHashMap<>();
        Map<String, String> textFlags = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String where = FILE_NAME + " line " + (i + 1) + ": ";
            Matcher matcher;
            if ((matcher = NAME.matcher(line)).matches()) {
                if (name != null) {
                    regions.add(region(name, first, second, flags, textFlags));
                }
                name = matcher.group(1);
                // WorldGuard's world-wide region, which a cuboid would replace.
                if (name.equals("__global__")) {
                    throw new IllegalArgumentException(where + "[__global__] isn't a region with corners");
                }
                if (!names.add(name)) {
                    throw new IllegalArgumentException(where + "a second [" + name + "]");
                }
                first = second = null;
                flags = new LinkedHashMap<>();
                textFlags = new LinkedHashMap<>();
            } else if (name == null) {
                throw new IllegalArgumentException(where + "comes before any [name] line: " + line);
            } else if ((matcher = CORNERS.matcher(line)).matches()) {
                if (first != null) {
                    throw new IllegalArgumentException(where + "a second corners line for " + name);
                }
                first = corner(matcher, 1);
                second = corner(matcher, 4);
            } else if ((matcher = FLAG.matcher(line)).matches()
                    || (matcher = TEXT_FLAG.matcher(line)).matches()) {
                String flag = matcher.group(1);
                if (flags.containsKey(flag) || textFlags.containsKey(flag)) {
                    throw new IllegalArgumentException(where + name + " sets " + flag + " twice");
                }
                (matcher.pattern() == FLAG ? flags : textFlags).put(flag, matcher.group(2));
            } else {
                throw new IllegalArgumentException(where
                        + "isn't [name], corners x,y,z x,y,z, <flag> allow|deny, or <flag> \"<text>\": " + line);
            }
        }
        if (name != null) {
            regions.add(region(name, first, second, flags, textFlags));
        }
        return new Regions(regions);
    }

    private static Region region(String name, Corner first, Corner second, Map<String, String> flags,
            Map<String, String> textFlags) {
        if (first == null) {
            throw new IllegalArgumentException(FILE_NAME + ": " + name + " has no corners line");
        }
        return new Region(name, first, second, flags, textFlags);
    }

    private static Corner corner(Matcher matcher, int group) {
        return new Corner(Integer.parseInt(matcher.group(group)),
                Integer.parseInt(matcher.group(group + 1)),
                Integer.parseInt(matcher.group(group + 2)));
    }
}
