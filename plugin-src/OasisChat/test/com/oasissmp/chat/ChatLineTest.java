package com.oasissmp.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Checks that OasisChat's line is a player's prefix and then the vanilla chat
 * line, and that every prefix in the LuckPerms groups under
 * plugins/LuckPerms/yaml-storage/groups reads as MiniMessage: a tag
 * MiniMessage doesn't know, or a legacy colour code, which TAB would still
 * read, is shown in chat as typed. Runs from the repo root.
 */
public final class ChatLineTest {

    private static final Path GROUPS = Path.of("plugins/LuckPerms/yaml-storage/groups");
    private static final Pattern PREFIX = Pattern.compile("^- '(.*)':$");
    private static final Pattern LEGACY_CODE = Pattern.compile("[&\u00a7][0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Component NAME = Component.text("Steve");
    private static final Component MESSAGE = Component.text("hi");
    private static final Component VANILLA = Component.translatable("chat.type.text", NAME, MESSAGE);

    private ChatLineTest() { }

    public static void main(String[] args) throws IOException {
        List<String> failures = new ArrayList<>();

        Component line = OasisChat.line("<gold>[Admin]</gold> ", NAME, MESSAGE);
        if (!line.children().getLast().equals(VANILLA)) {
            failures.add("the line doesn't end with the vanilla line: " + line);
        }
        if (!shownPrefix(line).equals("[Admin] ")) {
            failures.add("the line's prefix shows as '" + shownPrefix(line) + "', not '[Admin] '");
        }

        int prefixes = 0;
        List<Path> files;
        try (Stream<Path> listing = Files.list(GROUPS)) {
            files = listing.filter(file -> file.toString().endsWith(".yml")).sorted().toList();
        }
        for (Path file : files) {
            boolean inPrefixes = false;
            for (String yamlLine : Files.readAllLines(file)) {
                if (!yamlLine.startsWith(" ") && !yamlLine.startsWith("- ")) {
                    inPrefixes = yamlLine.equals("prefixes:");
                    continue;
                }
                if (!inPrefixes || !yamlLine.startsWith("- ")) {
                    continue;
                }
                Matcher key = PREFIX.matcher(yamlLine);
                if (!key.matches()) {
                    failures.add(file + ": can't read the prefix in " + yamlLine
                            + ", which isn't single-quoted the way LuckPerms writes it");
                    continue;
                }
                prefixes++;
                String prefix = key.group(1).replace("''", "'");
                String shown = shownPrefix(OasisChat.line(prefix, NAME, MESSAGE));
                if (shown.contains("<") || LEGACY_CODE.matcher(shown).find() || !shown.endsWith(" ")) {
                    failures.add(file + ": " + prefix + " shows as '" + shown + "': a tag MiniMessage"
                            + " doesn't know, a legacy colour code, or no space before the name");
                }
            }
        }
        if (prefixes == 0) {
            failures.add("no prefixes found in " + GROUPS);
        }

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("the chat line and " + prefixes + " prefixes check out");
    }

    // The line's text before the vanilla line, whose own text is the same in every line.
    private static String shownPrefix(Component line) {
        String text = plain(line);
        return text.substring(0, text.length() - plain(VANILLA).length());
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
