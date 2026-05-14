package io.github.larsonix.trailoforbis.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architectural test that scans the production codebase for UUID-keyed maps
 * and verifies that each class containing one has a cleanup method.
 *
 * <p>This test prevents the recurring pattern of adding per-player state
 * (ConcurrentHashMap&lt;UUID, ...&gt;) without adding a cleanup path on
 * player disconnect.
 *
 * <p><b>When this test fails:</b> You added a new UUID-keyed map to a class.
 * Either add a cleanup/disconnect method to that class and wire it into
 * {@code PlayerJoinListener.onPlayerDisconnect()}, OR add the class to the
 * {@link #KNOWN_NON_PLAYER_MAPS} allowlist if the map is NOT keyed by player UUID
 * (e.g., keyed by realm ID, entity ID, or world ID).
 */
@DisplayName("Architecture: All UUID-keyed maps must have cleanup methods")
class UuidMapCleanupArchitectureTest {

    /**
     * Classes whose UUID maps are NOT player-scoped (keyed by realm ID, entity ID, etc.)
     * and therefore don't need disconnect cleanup. Keep this list minimal and documented.
     */
    private static final Set<String> KNOWN_NON_PLAYER_MAPS = Set.of(
        // Realm-scoped maps (keyed by realm UUID, cleaned when realm closes)
        "RealmCompletionTracker",      // per-realm kill/damage tracking, GC'd with realm
        "VictoryPortalManager",        // per-realm portal tracking
        "RealmRemovalHandler",         // per-realm removal scheduling
        "RealmInstanceFactory",        // per-realm instance creation
        "RealmInstance",               // per-realm instance state

        // World-scoped maps
        "SpawnGatewayManager",         // per-world processing flags

        // Entity-scoped maps (bounded + age-limited)
        "HexCasterRegistry",           // entity UUID, max 500 + 5min TTL

        // Non-player UUID maps with explicit lifecycle
        "RealmPortalManager",          // portal-to-owner mapping, cleaned on realm close
        "SpawnGatewayRepository",      // DB-backed, no in-memory player maps

        // Singleton admin-only state (low-impact, now cleaned on disconnect)
        "CopyPasteClipboard",

        // Re-entrancy guards — add UUID at start, remove in finally block
        // These are transient processing flags, not persistent per-player state
        "TimedCraftConversionHandler",  // processing Set<UUID>, self-cleans in finally
        "LootFilterInventoryHandler",   // processing Set<UUID>, self-cleans in finally

        // Event data objects — immutable snapshots, GC'd after event dispatch
        "RealmCompletedEvent",          // participants + playerKills passed in constructor

        // Method return type false positive (Map<UUID,...> in method sig, not a field)
        "RealmRewardService"
    );

    /**
     * Cleanup method name patterns that indicate a class handles disconnect properly.
     * Case-insensitive contains check.
     */
    private static final List<String> CLEANUP_METHOD_PATTERNS = List.of(
        "onPlayerDisconnect",
        "cleanupPlayer",
        "removePlayer",
        "cleanup",
        "clearAll",
        "evict",
        "removeHud",
        "discardStale",
        "discardAllHuds",
        "removeAllHuds",
        "closeSanctum",
        "shutdown",
        "onDisconnect",
        "removeTracker",
        "clear"
    );

    @Test
    @DisplayName("Every class with Map<UUID,...> field has a cleanup method or is allowlisted")
    void everyUuidMapHasCleanup() throws Exception {
        // Find all production Java classes
        Path srcRoot = Paths.get("src/main/java/io/github/larsonix/trailoforbis");
        assertTrue(Files.isDirectory(srcRoot), "Source root must exist: " + srcRoot);

        List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(srcRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        assertFalse(javaFiles.isEmpty(), "Should find Java files");

        // For each Java file, check if it contains UUID map declarations
        List<String> violations = new ArrayList<>();

        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);
            String className = javaFile.getFileName().toString().replace(".java", "");

            // Skip allowlisted classes
            if (KNOWN_NON_PLAYER_MAPS.contains(className)) {
                continue;
            }

            // Skip test files that somehow ended up here
            if (content.contains("@Test")) {
                continue;
            }

            // Check for UUID-keyed map declarations
            boolean hasUuidMap = false;
            // Pattern: Map<UUID, ...> or ConcurrentHashMap<UUID, ...> as fields
            if (content.contains("Map<UUID,") || content.contains("HashMap<UUID,")
                    || content.contains("ConcurrentHashMap<UUID,")) {
                // Confirm it's a field (not a local variable or parameter)
                // Look for field-like patterns: private/final/protected + Map<UUID
                if (content.matches("(?s).*(?:private|final|protected|public)\\s+.*(?:Map|HashMap|ConcurrentHashMap)<UUID,.*"))
                {
                    hasUuidMap = true;
                }
            }

            // Also check Set<UUID> (ConcurrentHashMap.newKeySet())
            if (content.contains("Set<UUID>") && content.contains("newKeySet()")) {
                hasUuidMap = true;
            }

            if (!hasUuidMap) {
                continue;
            }

            // Verify the class has at least one cleanup method
            boolean hasCleanupMethod = CLEANUP_METHOD_PATTERNS.stream()
                .anyMatch(pattern -> content.contains(pattern));

            if (!hasCleanupMethod) {
                violations.add(String.format(
                    "%s (%s) — has UUID-keyed map but no cleanup method",
                    className, srcRoot.relativize(javaFile)
                ));
            }
        }

        if (!violations.isEmpty()) {
            fail("Classes with UUID-keyed maps missing cleanup methods:\n" +
                violations.stream().collect(Collectors.joining("\n  - ", "  - ", "\n")) +
                "\nFix: Add a cleanup/disconnect method and wire it into " +
                "PlayerJoinListener.onPlayerDisconnect(), OR add to KNOWN_NON_PLAYER_MAPS " +
                "if the UUID key is NOT a player UUID.");
        }
    }
}
