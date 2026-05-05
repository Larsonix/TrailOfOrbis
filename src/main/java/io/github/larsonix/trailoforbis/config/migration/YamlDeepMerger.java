package io.github.larsonix.trailoforbis.config.migration;

import java.util.*;

/**
 * Recursive deep-merge algorithm for YAML config maps.
 *
 * <p>Merges a user's existing config values on top of a bundled template,
 * preserving user customizations while adding new keys from the template.
 *
 * <p>Merge rules:
 * <ul>
 *   <li>Key in template only → add from template (new feature)</li>
 *   <li>Key in both, same type:
 *     <ul>
 *       <li>Both Maps → recurse</li>
 *       <li>Both Lists of Maps → discriminator-based match</li>
 *       <li>Both Lists of primitives → keep user's list</li>
 *       <li>Both scalars → keep user's value</li>
 *     </ul>
 *   </li>
 *   <li>Key in both, type mismatch → use template (schema changed)</li>
 *   <li>Key in user only → preserve (user custom addition)</li>
 * </ul>
 *
 * <p>Uses a deep-merge algorithm that preserves user customizations while updating defaults.
 */
public final class YamlDeepMerger {

    /** Discriminator fields checked in priority order for list-of-maps matching. */
    private static final List<String> DISCRIMINATOR_FIELDS = List.of(
        "id", "tier", "key", "name", "type"
    );

    private YamlDeepMerger() {}

    /**
     * Deep-merge user values over bundled template.
     *
     * @param bundled the template map (source of truth for structure)
     * @param user    the user's existing map (source of truth for values)
     * @return merged map with template structure + user values
     */
    public static Map<String, Object> merge(Map<String, Object> bundled, Map<String, Object> user) {
        if (bundled == null || bundled.isEmpty()) {
            return user != null ? deepCopy(user) : new LinkedHashMap<>();
        }
        if (user == null || user.isEmpty()) {
            return deepCopy(bundled);
        }

        // Normalize both maps: SnakeYAML may parse integer keys (e.g., "2:" → Integer 2)
        // which causes ClassCastException. Convert all keys to String.
        Map<String, Object> normalizedBundled = normalizeKeys(bundled);
        Map<String, Object> normalizedUser = normalizeKeys(user);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();

        // First pass: iterate bundled keys in template order
        for (Map.Entry<String, Object> entry : normalizedBundled.entrySet()) {
            String key = entry.getKey();
            Object bundledValue = entry.getValue();

            if (!normalizedUser.containsKey(key)) {
                // New key — add from template
                result.put(key, deepCopyValue(bundledValue));
                continue;
            }

            Object userValue = normalizedUser.get(key);

            if (!typesCompatible(bundledValue, userValue)) {
                // Type mismatch — schema changed, use template
                result.put(key, deepCopyValue(bundledValue));
                continue;
            }

            if (bundledValue instanceof Map && userValue instanceof Map) {
                // Recursive merge for nested sections
                @SuppressWarnings("unchecked")
                Map<String, Object> bundledMap = (Map<String, Object>) bundledValue;
                @SuppressWarnings("unchecked")
                Map<String, Object> userMap = (Map<String, Object>) userValue;
                result.put(key, merge(bundledMap, userMap));
            } else if (bundledValue instanceof List && userValue instanceof List) {
                result.put(key, mergeLists((List<?>) bundledValue, (List<?>) userValue));
            } else {
                // Scalar: keep user's value
                result.put(key, deepCopyValue(userValue));
            }
        }

        // Second pass: preserve user keys NOT in bundled (custom additions)
        for (Map.Entry<String, Object> entry : normalizedUser.entrySet()) {
            if (!normalizedBundled.containsKey(entry.getKey())) {
                result.put(entry.getKey(), deepCopyValue(entry.getValue()));
            }
        }

        return result;
    }

    /**
     * Normalizes map keys to String. SnakeYAML parses YAML like {@code 2: value}
     * as Integer keys, which causes ClassCastException in typed iteration.
     */
    private static Map<String, Object> normalizeKeys(Map<?, ?> map) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * Merges two lists, using discriminator-based matching for lists of maps.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> mergeLists(List<?> bundledList, List<?> userList) {
        if (bundledList.isEmpty()) {
            return deepCopyList(userList);
        }
        if (userList.isEmpty()) {
            return deepCopyList(bundledList);
        }

        // Check if this is a list of maps (supports discriminator matching)
        if (isListOfMaps(bundledList) && isListOfMaps(userList)) {
            return mergeListsOfMaps(
                (List<Map<String, Object>>) bundledList,
                (List<Map<String, Object>>) userList
            );
        }

        // Lists of primitives: keep user's list entirely
        return deepCopyList(userList);
    }

    /**
     * Merges lists of maps using discriminator-based entry matching.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>For each bundled entry, find matching user entry by discriminator</li>
     *   <li>Matched pairs → recursively merge</li>
     *   <li>Unmatched bundled entries → append (new content)</li>
     *   <li>Unmatched user entries → preserve (user additions)</li>
     * </ol>
     */
    private static List<Object> mergeListsOfMaps(
            List<Map<String, Object>> bundledList,
            List<Map<String, Object>> userList) {

        List<Object> result = new ArrayList<>();
        boolean[] userUsed = new boolean[userList.size()];

        for (Map<String, Object> bundledEntry : bundledList) {
            String bundledDisc = getDiscriminator(bundledEntry);
            int matchIndex = -1;

            if (bundledDisc != null) {
                for (int i = 0; i < userList.size(); i++) {
                    if (userUsed[i]) continue;
                    String userDisc = getDiscriminator(userList.get(i));
                    if (bundledDisc.equals(userDisc)) {
                        matchIndex = i;
                        break;
                    }
                }
            }

            if (matchIndex >= 0) {
                // Matched — recursively merge the map entries
                userUsed[matchIndex] = true;
                result.add(merge(bundledEntry, userList.get(matchIndex)));
            } else {
                // Unmatched bundled entry — new content
                result.add(deepCopyValue(bundledEntry));
            }
        }

        // Append unmatched user entries (user additions)
        for (int i = 0; i < userList.size(); i++) {
            if (!userUsed[i]) {
                result.add(deepCopyValue(userList.get(i)));
            }
        }

        return result;
    }

    /**
     * Gets the discriminator value for a map entry.
     * Checks fields in priority order: id, tier, key, name, type.
     *
     * @return normalized "field:VALUE" or null if no discriminator found
     */
    private static String getDiscriminator(Map<String, Object> entry) {
        for (String field : DISCRIMINATOR_FIELDS) {
            Object value = entry.get(field);
            if (value != null) {
                return field + ":" + value.toString().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    /**
     * Checks if two values have compatible types for merging.
     */
    private static boolean typesCompatible(Object bundled, Object user) {
        if (bundled == null || user == null) {
            return true; // null is compatible with anything
        }
        if (bundled instanceof Map && user instanceof Map) return true;
        if (bundled instanceof List && user instanceof List) return true;
        if (bundled instanceof Number && user instanceof Number) return true;
        if (bundled instanceof String && user instanceof String) return true;
        if (bundled instanceof Boolean && user instanceof Boolean) return true;
        // Same exact class
        return bundled.getClass().equals(user.getClass());
    }

    private static boolean isListOfMaps(List<?> list) {
        if (list.isEmpty()) return false;
        return list.getFirst() instanceof Map;
    }

    // =========================================================================
    // DEEP COPY UTILITIES
    // =========================================================================

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepCopy(Map<String, Object> source) {
        if (source == null) return new LinkedHashMap<>();
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object value) {
        if (value == null) return null;
        if (value instanceof Map) {
            return deepCopy((Map<String, Object>) value);
        }
        if (value instanceof List) {
            return deepCopyList((List<?>) value);
        }
        // Primitives (String, Number, Boolean) are immutable
        return value;
    }

    private static List<Object> deepCopyList(List<?> source) {
        if (source == null) return new ArrayList<>();
        List<Object> copy = new ArrayList<>(source.size());
        for (Object item : source) {
            copy.add(deepCopyValue(item));
        }
        return copy;
    }
}
