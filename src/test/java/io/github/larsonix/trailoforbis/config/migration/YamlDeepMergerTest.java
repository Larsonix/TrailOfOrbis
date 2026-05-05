package io.github.larsonix.trailoforbis.config.migration;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the YAML deep merge algorithm.
 * Covers all merge rules: new keys, user values, type mismatches, nested maps,
 * list discriminator matching, and user extras.
 */
class YamlDeepMergerTest {

    @Test
    void emptyUserReturnsTemplate() {
        Map<String, Object> template = Map.of("key", "value", "num", 42);
        Map<String, Object> user = Map.of();

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        assertEquals("value", result.get("key"));
        assertEquals(42, result.get("num"));
    }

    @Test
    void emptyTemplateReturnsUser() {
        Map<String, Object> template = Map.of();
        Map<String, Object> user = Map.of("custom", "mine");

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        assertEquals("mine", result.get("custom"));
    }

    @Test
    void userValuePreservedOverTemplate() {
        Map<String, Object> template = Map.of("damage", 10.0, "speed", 1.5);
        Map<String, Object> user = Map.of("damage", 25.0, "speed", 1.5);

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        assertEquals(25.0, result.get("damage"), "User's custom value should be preserved");
        assertEquals(1.5, result.get("speed"), "Unchanged value should remain");
    }

    @Test
    void newKeyFromTemplateAdded() {
        Map<String, Object> template = new LinkedHashMap<>(Map.of("old", 1, "new_feature", true));
        Map<String, Object> user = Map.of("old", 1);

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        assertEquals(1, result.get("old"));
        assertEquals(true, result.get("new_feature"), "New key from template should appear");
    }

    @Test
    void userExtraKeysPreserved() {
        Map<String, Object> template = Map.of("official", "yes");
        Map<String, Object> user = new LinkedHashMap<>(Map.of("official", "yes", "my_custom_key", "custom"));

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        assertEquals("yes", result.get("official"));
        assertEquals("custom", result.get("my_custom_key"), "User's extra key should be preserved");
    }

    @Test
    void typeMismatchUsesTemplate() {
        Map<String, Object> template = Map.of("setting", Map.of("nested", true));
        Map<String, Object> user = Map.of("setting", "was_a_string_before");

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        assertInstanceOf(Map.class, result.get("setting"),
            "Type mismatch should use template's type (Map)");
    }

    @Test
    void nestedMapsRecurseMerge() {
        Map<String, Object> template = new LinkedHashMap<>(Map.of(
            "combat", new LinkedHashMap<>(Map.of(
                "critMultiplier", 2.0,
                "newParam", 0.5  // New in template
            ))
        ));
        Map<String, Object> user = Map.of(
            "combat", new LinkedHashMap<>(Map.of(
                "critMultiplier", 3.0  // User customized
            ))
        );

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        @SuppressWarnings("unchecked")
        Map<String, Object> combat = (Map<String, Object>) result.get("combat");
        assertEquals(3.0, combat.get("critMultiplier"), "User's nested value preserved");
        assertEquals(0.5, combat.get("newParam"), "New nested key from template added");
    }

    @Test
    void deeplyNestedMerge() {
        Map<String, Object> template = Map.of(
            "level1", new LinkedHashMap<>(Map.of(
                "level2", new LinkedHashMap<>(Map.of(
                    "level3", new LinkedHashMap<>(Map.of(
                        "deep", "default",
                        "new_deep", "added"
                    ))
                ))
            ))
        );
        Map<String, Object> user = Map.of(
            "level1", new LinkedHashMap<>(Map.of(
                "level2", new LinkedHashMap<>(Map.of(
                    "level3", new LinkedHashMap<>(Map.of(
                        "deep", "custom"
                    ))
                ))
            ))
        );

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        @SuppressWarnings("unchecked")
        Map<String, Object> level3 = (Map<String, Object>)
            ((Map<String, Object>) ((Map<String, Object>) result.get("level1")).get("level2")).get("level3");
        assertEquals("custom", level3.get("deep"));
        assertEquals("added", level3.get("new_deep"));
    }

    @Test
    void listOfPrimitivesKeepsUserList() {
        Map<String, Object> template = Map.of("mobs", List.of("Trork", "Feran", "NewMob"));
        Map<String, Object> user = Map.of("mobs", List.of("Trork", "Feran"));

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        @SuppressWarnings("unchecked")
        List<Object> mobs = (List<Object>) result.get("mobs");
        assertEquals(List.of("Trork", "Feran"), mobs, "User's primitive list should be kept as-is");
    }

    @Test
    void listOfMapsDiscriminatorMerge() {
        // Template has a new modifier "of_frost" not in user's list
        List<Map<String, Object>> templateList = List.of(
            new LinkedHashMap<>(Map.of("id", "sharp", "weight", 1.0, "new_field", true)),
            new LinkedHashMap<>(Map.of("id", "of_frost", "weight", 0.5))
        );
        List<Map<String, Object>> userList = List.of(
            new LinkedHashMap<>(Map.of("id", "sharp", "weight", 2.0))  // User changed weight
        );

        Map<String, Object> template = Map.of("modifiers", templateList);
        Map<String, Object> user = Map.of("modifiers", userList);

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modifiers = (List<Map<String, Object>>) result.get("modifiers");

        assertEquals(2, modifiers.size(), "Should have both entries");

        // "sharp" should be merged: user's weight + template's new_field
        Map<String, Object> sharp = modifiers.stream()
            .filter(m -> "sharp".equals(m.get("id"))).findFirst().orElseThrow();
        assertEquals(2.0, sharp.get("weight"), "User's weight value preserved");
        assertEquals(true, sharp.get("new_field"), "Template's new field added");

        // "of_frost" should be added from template
        Map<String, Object> frost = modifiers.stream()
            .filter(m -> "of_frost".equals(m.get("id"))).findFirst().orElseThrow();
        assertEquals(0.5, frost.get("weight"));
    }

    @Test
    void listOfMapsPreservesUserAdditions() {
        List<Map<String, Object>> templateList = List.of(
            new LinkedHashMap<>(Map.of("id", "standard"))
        );
        List<Map<String, Object>> userList = List.of(
            new LinkedHashMap<>(Map.of("id", "standard")),
            new LinkedHashMap<>(Map.of("id", "custom_user_entry", "custom", true))
        );

        Map<String, Object> template = Map.of("entries", templateList);
        Map<String, Object> user = Map.of("entries", userList);

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entries");

        assertEquals(2, entries.size(), "User's extra entry should be preserved");
        assertTrue(entries.stream().anyMatch(m -> "custom_user_entry".equals(m.get("id"))));
    }

    @Test
    void templateOrderingPreserved() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("alpha", 1);
        template.put("beta", 2);
        template.put("gamma", 3);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("gamma", 30);
        user.put("alpha", 10);

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        List<String> keys = new ArrayList<>(result.keySet());
        assertEquals(List.of("alpha", "beta", "gamma"), keys,
            "Result should follow template key ordering");
        assertEquals(10, result.get("alpha"));
        assertEquals(2, result.get("beta"));
        assertEquals(30, result.get("gamma"));
    }

    @Test
    void nullValuesHandledGracefully() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("nullable", null);
        template.put("present", "yes");

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("nullable", "set_by_user");
        user.put("present", null);

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        assertEquals("set_by_user", result.get("nullable"));
        assertNull(result.get("present"));
    }

    @Test
    void numberTypeCompatibility() {
        // Template uses Integer, user has Double — should be compatible
        Map<String, Object> template = Map.of("count", 5);
        Map<String, Object> user = Map.of("count", 5.0);

        Map<String, Object> result = YamlDeepMerger.merge(template, user);

        assertEquals(5.0, result.get("count"), "Number types are compatible, user value preserved");
    }
}
