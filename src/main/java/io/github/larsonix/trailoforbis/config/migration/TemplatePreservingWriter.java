package io.github.larsonix.trailoforbis.config.migration;

import com.hypixel.hytale.logger.HytaleLogger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/**
 * Writes a merged config map to disk, preserving documentation from the template.
 *
 * <p><b>Strategy</b>: Use the bundled template text as the base (preserving all comments,
 * indentation, and structure). Then substitute only the scalar values that the user
 * customized. For structural differences (new/removed map sections), fall back to
 * SnakeYAML dump for that section.
 *
 * <p><b>Why this is robust</b>:
 * <ul>
 *   <li>Template is the base → all new keys, comments, formatting are already correct</li>
 *   <li>Only scalar value substitution happens on most lines (minimal surgery)</li>
 *   <li>Structural diffs use yaml.dump (guaranteed correct YAML output)</li>
 *   <li>If anything fails → falls back to full yaml.dump (data correctness over formatting)</li>
 * </ul>
 */
public final class TemplatePreservingWriter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TemplatePreservingWriter() {}

    /**
     * Writes the merged config using the template for formatting.
     *
     * @param merged       the final merged map to write
     * @param templateText raw template text from bundled resource (with comments)
     * @param outputPath   where to write the result
     */
    public static void write(Map<String, Object> merged, String templateText,
                             Path outputPath) throws IOException {
        try {
            String result = buildOutput(merged, templateText);
            Files.writeString(outputPath, result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log(
                "Template-preserving write failed for %s, using plain YAML dump",
                outputPath.getFileName());
            fallbackWrite(merged, outputPath);
        }
    }

    /**
     * Builds the output by walking template lines and substituting user values.
     */
    private static String buildOutput(Map<String, Object> merged, String templateText) {
        Yaml yaml = createYaml();

        // Parse template to understand its default values
        @SuppressWarnings("unchecked")
        Map<String, Object> templateMap = yaml.loadAs(templateText, LinkedHashMap.class);
        if (templateMap == null) templateMap = new LinkedHashMap<>();

        // Walk template lines, substitute where merged differs
        List<String> templateLines = templateText.lines().toList();
        List<String> output = new ArrayList<>(templateLines.size());
        Deque<PathEntry> pathStack = new ArrayDeque<>();
        Set<String> processedTopKeys = new HashSet<>();

        for (int i = 0; i < templateLines.size(); i++) {
            String line = templateLines.get(i);

            // Comments and blank lines: keep unchanged
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                output.add(line);
                continue;
            }

            // Parse the line for key and indent
            int indent = countIndent(line);
            String trimmed = line.stripLeading();
            int colonPos = findUnquotedColon(trimmed);

            if (colonPos <= 0) {
                // Not a key line (list item, continuation, etc.) — keep as-is
                output.add(line);
                continue;
            }

            String key = trimmed.substring(0, colonPos).strip();
            String afterColon = trimmed.substring(colonPos + 1).strip();

            // Pop path stack to match current indent level
            while (!pathStack.isEmpty() && pathStack.peek().indent >= indent) {
                pathStack.pop();
            }

            // Build current full path (iterate tail→head = root→parent, then add current key)
            List<String> currentPath = new ArrayList<>();
            Iterator<PathEntry> descIter = pathStack.descendingIterator();
            while (descIter.hasNext()) {
                currentPath.add(descIter.next().key);
            }
            currentPath.add(key);

            // Look up the merged value at this path
            Object mergedValue = getValueAtPath(merged, currentPath);
            Object templateValue = getValueAtPath(templateMap, currentPath);

            if (mergedValue == null && templateValue != null) {
                // Key was removed by user? Unlikely after merge, but keep template line
                output.add(line);
                if (isBlockStart(afterColon)) {
                    pathStack.push(new PathEntry(key, indent));
                }
                continue;
            }

            if (isBlockStart(afterColon)) {
                // This is a section header (key with no inline value, children follow)
                pathStack.push(new PathEntry(key, indent));

                if (mergedValue instanceof Map && templateValue instanceof Map) {
                    // Both are maps — keep the header line, let children be processed individually
                    output.add(line);
                } else if (mergedValue instanceof List && templateValue instanceof List) {
                    // Both are lists — template uses block style, merged has same data
                    // Keep the header and let list items be output from template
                    // (they'll be kept as-is since values match after merge)
                    output.add(line);
                } else if (mergedValue instanceof List) {
                    // Merged is a list but template wasn't — render as block list and skip children
                    output.add(" ".repeat(indent) + key + ":");
                    String rendered = renderValue(mergedValue, indent + 2, yaml);
                    output.addAll(rendered.lines().toList());
                    i = skipBlock(templateLines, i, indent);
                    pathStack.pop();
                } else if (mergedValue != null && !(mergedValue instanceof Map)) {
                    // Merged value is a scalar but template expects block — render inline
                    output.add(" ".repeat(indent) + key + ": " + formatScalar(mergedValue));
                    i = skipBlock(templateLines, i, indent);
                    pathStack.pop();
                } else {
                    output.add(line);
                }
            } else {
                // This is a scalar value line: "key: value" or "key: value # comment"
                if (Objects.equals(mergedValue, templateValue)) {
                    // Same value — keep template line unchanged (preserves exact formatting + comment)
                    output.add(line);
                } else if (mergedValue instanceof Map || mergedValue instanceof List) {
                    // Merged value became a collection — render as block
                    output.add(" ".repeat(indent) + key + ":");
                    pathStack.push(new PathEntry(key, indent));
                    String rendered = renderValue(mergedValue, indent + 2, yaml);
                    output.addAll(rendered.lines().toList());
                } else {
                    // Scalar substitution — preserve the comment
                    String comment = extractComment(afterColon);
                    String newLine = " ".repeat(indent) + key + ": " + formatScalar(mergedValue);
                    if (comment != null) {
                        newLine += "  " + comment;
                    }
                    output.add(newLine);
                }
            }

            // Track top-level keys we've processed
            if (currentPath.size() == 1) {
                processedTopKeys.add(key);
            }
        }

        // Append any top-level keys from merged that aren't in the template
        Map<String, Object> extras = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : merged.entrySet()) {
            if (!processedTopKeys.contains(entry.getKey())
                    && !templateMap.containsKey(entry.getKey())) {
                extras.put(entry.getKey(), entry.getValue());
            }
        }
        if (!extras.isEmpty()) {
            output.add("");
            output.add("# User-added keys (preserved from previous config)");
            String extraYaml = yaml.dump(extras);
            for (String extraLine : extraYaml.lines().toList()) {
                if (!extraLine.isBlank()) {
                    output.add(extraLine);
                }
            }
        }

        return String.join(System.lineSeparator(), output) + System.lineSeparator();
    }

    // =========================================================================
    // PATH NAVIGATION
    // =========================================================================

    /**
     * Gets a value from a nested map by path.
     */
    @SuppressWarnings("unchecked")
    private static Object getValueAtPath(Map<String, Object> root, List<String> path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(key);
            if (current == null) return null;
        }
        return current;
    }

    /**
     * Skips all lines belonging to a block starting at startLine.
     * Returns the index of the last line in the block.
     */
    private static int skipBlock(List<String> lines, int startLine, int baseIndent) {
        int i = startLine + 1;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                i++;
                continue;
            }
            int lineIndent = countIndent(line);
            if (lineIndent > baseIndent) {
                i++; // Deeper indent — still in this block
                continue;
            }
            // Same indent: YAML list items ("- value") are still children of the block.
            // A key:value pair at same indent is a sibling (stop here).
            String trimmed = line.stripLeading();
            if (lineIndent == baseIndent && trimmed.startsWith("- ")) {
                i++; // List item at parent indent — still a child
                continue;
            }
            return i - 1; // Same or lower indent, not a list item — end of block
        }
        return i - 1;
    }

    // =========================================================================
    // VALUE FORMATTING
    // =========================================================================

    /**
     * Formats a scalar value for YAML.
     */
    private static String formatScalar(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Integer || value instanceof Long) return value.toString();
        if (value instanceof Double d) {
            if (d == Math.floor(d) && Math.abs(d) < 1_000_000 && d != 0.0) {
                // Whole numbers: show as "5.0" not "5"
                return String.valueOf(d);
            }
            return String.valueOf(d);
        }
        if (value instanceof Float f) {
            return String.valueOf(f.doubleValue());
        }
        if (value instanceof String str) {
            if (needsQuoting(str)) {
                return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
            return str;
        }
        return String.valueOf(value);
    }

    /**
     * Renders a Map or List as indented YAML lines.
     */
    private static String renderValue(Object value, int indent, Yaml yaml) {
        // Dump the value and indent each line
        String dumped = yaml.dump(value);
        StringBuilder sb = new StringBuilder();
        String indentStr = " ".repeat(indent);
        for (String line : dumped.lines().toList()) {
            if (!line.isBlank()) {
                sb.append(indentStr).append(line).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    private static boolean needsQuoting(String str) {
        if (str.isEmpty()) return true;
        if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false")) return true;
        if (str.equalsIgnoreCase("null") || str.equalsIgnoreCase("~")) return true;
        if (str.startsWith(" ") || str.endsWith(" ")) return true;
        if (str.contains(": ") || str.contains(" #") || str.startsWith("#")) return true;
        if (str.contains("{") || str.contains("}") || str.contains("[") || str.contains("]")) return true;
        try {
            Double.parseDouble(str);
            return true; // Looks like a number
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // =========================================================================
    // LINE PARSING UTILITIES
    // =========================================================================

    private static int countIndent(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') count++;
            else break;
        }
        return count;
    }

    /**
     * Finds the first unquoted colon in a line (the key-value separator).
     * Returns -1 if no unquoted colon found.
     */
    private static int findUnquotedColon(String trimmed) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            if (c == '"' && !inSingle) inDouble = !inDouble;
            if (c == ':' && !inSingle && !inDouble) {
                // YAML colon must be followed by space or end of line
                if (i + 1 >= trimmed.length() || trimmed.charAt(i + 1) == ' ') {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Checks if a "value" after the colon indicates a block start (empty or only comment).
     */
    private static boolean isBlockStart(String afterColon) {
        if (afterColon.isEmpty()) return true;
        return afterColon.startsWith("#"); // "key:  # comment" = block start
    }

    /**
     * Extracts a trailing comment from after the value.
     * e.g., "0.05  # 5% minimum" → "# 5% minimum"
     */
    private static String extractComment(String afterColon) {
        if (afterColon == null || afterColon.isEmpty()) return null;

        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < afterColon.length(); i++) {
            char c = afterColon.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            if (c == '"' && !inSingle) inDouble = !inDouble;
            if (c == '#' && !inSingle && !inDouble) {
                return afterColon.substring(i);
            }
        }
        return null;
    }

    // =========================================================================
    // FALLBACK
    // =========================================================================

    private static void fallbackWrite(Map<String, Object> merged, Path outputPath) throws IOException {
        Yaml yaml = createYaml();
        String output = yaml.dump(merged);
        Files.writeString(outputPath, output, StandardCharsets.UTF_8);
    }

    private static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(false);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        options.setWidth(200); // Avoid unnecessary line wrapping
        return new Yaml(options);
    }

    // =========================================================================
    // INTERNAL TYPES
    // =========================================================================

    private record PathEntry(String key, int indent) {}
}
