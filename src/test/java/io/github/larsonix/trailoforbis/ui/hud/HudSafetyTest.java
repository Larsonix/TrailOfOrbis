package io.github.larsonix.trailoforbis.ui.hud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Build-time enforcement: bans unsafe HyUI methods from our codebase.
 *
 * <h2>Why This Test Exists</h2>
 * <p>{@code HyUIHud.hide()} and {@code unhide()} send raw diff-based {@code Set}
 * commands with auto-generated {@code #HYUUIDxxx} selectors that bypass MultiHud's
 * selector prefixing. These selectors become stale whenever ANY HUD is added, removed,
 * or rerendered through MultiHud — causing the client to crash with:
 * {@code "Selected element in CustomUI command was not found"}.
 *
 * <p>The safe alternatives are:
 * <ul>
 *   <li>{@link HudRefreshHelper#safeSetVisibility} — for showing/hiding HUDs</li>
 *   <li>{@link HudRefreshHelper#safeRefreshWithToggle} — for refreshes with toggle state</li>
 *   <li>{@code hud.remove()} — for removal (sends safe Remove command via MultiHud)</li>
 * </ul>
 *
 * <p>This test scans all Java source files in our plugin (excluding tests and
 * external libraries) and fails the build if any banned method call is found.
 */
class HudSafetyTest {

    /**
     * Patterns that indicate unsafe HyUI calls.
     * Matches .hide(), .hideUnsafe(), .unhide(), .unhideUnsafe() on any variable.
     * Excludes lines that are comments (// or *) or string literals in Javadoc.
     */
    private static final Pattern UNSAFE_CALL = Pattern.compile(
        "\\.(hide|unhide|hideUnsafe|unhideUnsafe)\\s*\\("
    );

    /** Lines containing these are comments/Javadoc — not real calls. */
    private static final Pattern COMMENT_LINE = Pattern.compile(
        "^\\s*(//|\\*|/\\*)|\\{@code"
    );

    private static final Path SOURCE_ROOT = findSourceRoot();

    @Test
    @DisplayName("No direct hide()/unhide() calls on HyUIHud — use HudRefreshHelper.safeSetVisibility()")
    void noUnsafeHideUnhideCalls() throws IOException {
        List<String> violations = new ArrayList<>();

        Files.walkFileTree(SOURCE_ROOT, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }

                // Skip test files — they may legitimately reference these methods
                String relative = SOURCE_ROOT.relativize(file).toString();
                if (relative.contains("test")) {
                    return FileVisitResult.CONTINUE;
                }

                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);

                    Matcher unsafeMatcher = UNSAFE_CALL.matcher(line);
                    if (unsafeMatcher.find()) {
                        // Skip comments and Javadoc
                        if (COMMENT_LINE.matcher(line).find()) {
                            continue;
                        }
                        // Skip string literals (e.g., log messages containing "hide()")
                        if (line.contains("\"") && isInsideString(line, unsafeMatcher.start())) {
                            continue;
                        }

                        String method = unsafeMatcher.group(1);
                        violations.add(String.format(
                            "%s:%d — .%s() is banned. Use HudRefreshHelper.safeSetVisibility() instead.%n    %s",
                            relative, i + 1, method, line.trim()
                        ));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (!violations.isEmpty()) {
            fail(String.format(
                "%d unsafe HyUI call(s) found. hide()/unhide() send raw Set commands with " +
                "stale #HYUUIDxxx selectors that crash the client.%n%nViolations:%n%s",
                violations.size(),
                String.join("\n\n", violations)
            ));
        }
    }

    /**
     * Rough check: is the position inside a string literal on this line?
     * Counts unescaped quotes before the position.
     */
    private static boolean isInsideString(String line, int position) {
        boolean inString = false;
        for (int i = 0; i < position && i < line.length(); i++) {
            if (line.charAt(i) == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
        }
        return inString;
    }

    /**
     * Finds the main source root. Works from both IDE and Gradle test runner.
     */
    private static Path findSourceRoot() {
        // Try relative to working directory (Gradle runs from project root)
        Path candidate = Path.of("src/main/java");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        // Fallback: navigate from this test's location
        Path testFile = Path.of("src/test/java/io/github/larsonix/trailoforbis/ui/hud/HudSafetyTest.java");
        if (Files.exists(testFile)) {
            return Path.of("src/main/java");
        }
        throw new RuntimeException("Cannot find source root — run from project directory");
    }
}
