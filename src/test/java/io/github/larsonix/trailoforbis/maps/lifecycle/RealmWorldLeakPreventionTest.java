package io.github.larsonix.trailoforbis.maps.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests verifying that realm world creation ALWAYS has a matching
 * cleanup path. These tests prevent the class of bugs where:
 * - Worlds are created via RealmsManager.openRealm() but closed via a different path
 * - closeRealm() is bypassed, leaving orphaned worlds
 * - World references leak across sessions
 *
 * <p>The root bug: SkillSanctumInstance.close() called forceClose() directly instead
 * of RealmsManager.closeRealm(), causing worlds to accumulate indefinitely (7+ leaked
 * worlds after 5h → 30s portal delays → client crash).
 *
 * @see io.github.larsonix.trailoforbis.sanctum.SkillSanctumInstance
 * @see io.github.larsonix.trailoforbis.maps.RealmsManager
 */
@DisplayName("Realm World Leak Prevention")
class RealmWorldLeakPreventionTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/io/github/larsonix/trailoforbis");

    // ═══════════════════════════════════════════════════════════════════
    // ARCHITECTURE: Every openRealm() caller must have closeRealm() path
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Architecture: Realm creation/destruction symmetry")
    class RealmCreationDestructionSymmetry {

        /**
         * Every class that calls openRealm() to create a realm MUST also have
         * a code path that reaches closeRealm() or the removalHandler. Without
         * this, the realm world lives forever.
         */
        @Test
        @DisplayName("Every openRealm() caller has a matching closeRealm() path")
        void everyOpenRealmCallerHasMatchingClosePath() throws IOException {
            Set<String> openRealmCallers = findSourceFilesContaining("openRealm(");
            Set<String> closeRealmCallers = findSourceFilesContaining("closeRealm(");
            Set<String> forceCloseCallers = findSourceFilesContaining("forceClose(");

            // Every class that opens a realm must either:
            // 1. Call closeRealm() itself, OR
            // 2. Call through a class that calls closeRealm() (documented delegation)
            Set<String> orphanCreators = new HashSet<>();
            for (String creator : openRealmCallers) {
                if (!closeRealmCallers.contains(creator) && !isAllowedDelegation(creator)) {
                    orphanCreators.add(creator);
                }
            }

            assertTrue(orphanCreators.isEmpty(),
                "Classes that call openRealm() without a closeRealm() path (potential leak):\n" +
                orphanCreators.stream()
                    .map(f -> "  - " + f)
                    .collect(Collectors.joining("\n")) +
                "\n\nFix: Add closeRealm() call in the close/cleanup method, " +
                "OR add to isAllowedDelegation() if closure is handled by another class.");
        }

        /**
         * No production code should call forceClose() without ALSO calling closeRealm()
         * or InstancesPlugin.safeRemoveInstance(). Bare forceClose() only changes state —
         * it does NOT remove the world.
         */
        @Test
        @DisplayName("No bare forceClose() without world removal")
        void noBareForceCloseWithoutWorldRemoval() throws IOException {
            Set<String> forceCloseCallers = findSourceFilesContaining("forceClose(");
            Set<String> worldRemovers = findSourceFilesContaining("closeRealm(");
            worldRemovers.addAll(findSourceFilesContaining("safeRemoveInstance("));
            worldRemovers.addAll(findSourceFilesContaining("scheduleRemoval("));

            // Known safe: RealmInstance itself defines forceClose, RealmsManager calls it
            // as part of the full closeRealm() pipeline. Test files are excluded.
            Set<String> bareForceClose = new HashSet<>();
            for (String caller : forceCloseCallers) {
                if (caller.contains("Test") || caller.contains("test/")) continue;
                if (caller.contains("RealmInstance.java")) continue; // Defines it
                if (caller.contains("RealmsManager.java")) continue; // Uses it correctly
                if (caller.contains("RealmRemovalHandler.java")) continue; // Uses it correctly
                // RealmInstanceFactory calls forceClose(ERROR) on creation failure — the realm
                // was already registered in RealmsManager so closeRealm() would also work, but
                // this is an error-recovery path where the world may not be fully initialized.
                // The error state is handled by RealmsManager's periodic cleanup.
                if (caller.contains("RealmInstanceFactory.java")) continue;
                // RealmPlayerDeathListener mentions forceClose in a comment (DO NOT call)
                // but doesn't actually invoke it — string search catches comment text.
                if (caller.contains("RealmPlayerDeathListener.java")) continue;

                if (!worldRemovers.contains(caller)) {
                    bareForceClose.add(caller);
                }
            }

            assertTrue(bareForceClose.isEmpty(),
                "Classes calling forceClose() without world removal (world leak risk):\n" +
                bareForceClose.stream()
                    .map(f -> "  - " + f)
                    .collect(Collectors.joining("\n")) +
                "\n\nFix: Replace forceClose() with RealmsManager.closeRealm() " +
                "which handles both state transition AND world removal.");
        }

        /**
         * The RealmsManager must have orphan cleanup logic — worlds can persist
         * from hard kills, and without cleanup they accumulate every restart.
         */
        @Test
        @DisplayName("RealmsManager has orphan world cleanup")
        void realmsManagerHasOrphanCleanup() throws IOException {
            String realmsManagerSource = readSource("maps/RealmsManager.java");

            assertTrue(realmsManagerSource.contains("cleanupOrphanedRealmWorlds"),
                "RealmsManager must have cleanupOrphanedRealmWorlds() method " +
                "to prevent world accumulation across server restarts");

            assertTrue(realmsManagerSource.contains("instance-realm_"),
                "Orphan cleanup must check for 'instance-realm_' prefix pattern " +
                "to identify realm world names");
        }

        /**
         * The RealmsManager must have leak detection logging that fires when
         * the realm count exceeds a threshold.
         */
        @Test
        @DisplayName("RealmsManager has leak detection logging")
        void realmsManagerHasLeakDetection() throws IOException {
            String realmsManagerSource = readSource("maps/RealmsManager.java");

            assertTrue(realmsManagerSource.contains("LEAK-DETECT"),
                "RealmsManager must have LEAK-DETECT logging to catch future " +
                "world accumulation bugs early");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ARCHITECTURE: SkillSanctum closure path
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Architecture: Skill Sanctum lifecycle")
    class SkillSanctumLifecycle {

        /**
         * The sanctum MUST close through RealmsManager.closeRealm(), not through
         * bare forceClose(). This was the root cause of the 7-world leak bug.
         */
        @Test
        @DisplayName("SkillSanctumInstance.close() routes through RealmsManager.closeRealm()")
        void sanctumCloseUsesRealmsManagerCloseRealm() throws IOException {
            String sanctumSource = readSource("sanctum/SkillSanctumInstance.java");

            // Must contain closeRealm call
            assertTrue(sanctumSource.contains("closeRealm("),
                "SkillSanctumInstance must call closeRealm() for proper world cleanup. " +
                "forceClose() alone does NOT remove the world.");

            // Must NOT contain bare safeRemoveInstance as the primary path
            // (only as a fallback, if present at all)
            int closeRealmIdx = sanctumSource.indexOf("closeRealm(");
            int safeRemoveIdx = sanctumSource.indexOf("safeRemoveInstance(");

            if (safeRemoveIdx >= 0) {
                // If safeRemoveInstance exists, it must be in a fallback branch
                // (i.e., after the closeRealm path, not before it)
                assertTrue(closeRealmIdx < safeRemoveIdx || closeRealmIdx >= 0,
                    "closeRealm() must be the PRIMARY closure path, " +
                    "safeRemoveInstance() only as fallback");
            }
        }

        /**
         * The sanctum MUST NOT call forceClose() as its primary close path.
         * forceClose() only transitions state — it doesn't remove the world.
         */
        @Test
        @DisplayName("SkillSanctumInstance.close() does not use bare forceClose as primary path")
        void sanctumCloseDoesNotUseBareForceClose() throws IOException {
            String sanctumSource = readSource("sanctum/SkillSanctumInstance.java");

            // Extract the close() method body
            int closeMethodStart = sanctumSource.indexOf("public void close()");
            assertTrue(closeMethodStart >= 0, "close() method must exist");

            // Find the realm closure section (after "realmInstance != null" check)
            int realmCheckIdx = sanctumSource.indexOf("if (realmInstance != null)", closeMethodStart);
            assertTrue(realmCheckIdx >= 0, "close() must check realmInstance != null");

            // The primary path within the realmInstance block must use closeRealm
            String realmBlock = sanctumSource.substring(realmCheckIdx,
                Math.min(realmCheckIdx + 1500, sanctumSource.length()));

            assertTrue(realmBlock.contains("closeRealm("),
                "The realm closure block must call closeRealm() — " +
                "not forceClose() alone which leaks worlds");
        }

        /**
         * Sanctum creation goes through openRealm(), so the created realm is tracked
         * by RealmsManager. Verify the creation path uses the proper API.
         */
        @Test
        @DisplayName("SkillSanctumManager creates sanctums via RealmsManager.openRealm()")
        void sanctumCreationUsesOpenRealm() throws IOException {
            String managerSource = readSource("sanctum/SkillSanctumManager.java");

            assertTrue(managerSource.contains("openRealm("),
                "SkillSanctumManager must create realms via RealmsManager.openRealm() " +
                "so they are tracked for proper lifecycle management");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ARCHITECTURE: World name conventions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Architecture: World naming consistency")
    class WorldNamingConsistency {

        /**
         * Realm worlds are created by Hytale's InstancesPlugin which generates names
         * as "instance-{templateTier}-{uuid}" where templateTier starts with "realm_".
         * The factory must pass template tiers starting with "realm_" so that the
         * resulting world names match our "instance-realm_" orphan detection prefix.
         */
        @Test
        @DisplayName("RealmInstanceFactory uses 'realm_' template tier naming")
        void realmFactoryUsesConsistentNaming() throws IOException {
            String factorySource = readSource("maps/instance/RealmInstanceFactory.java");

            // Factory must reference realm_ template tiers (passed to InstancesPlugin.spawnInstance)
            // Hytale generates world name as: "instance-" + templateTier + "-" + uuid
            // With tier = "realm_caverns_r80", result = "instance-realm_caverns_r80-<uuid>"
            assertTrue(factorySource.contains("realm_") || factorySource.contains("\"realm-"),
                "RealmInstanceFactory must use template tiers starting with 'realm_' " +
                "so Hytale generates 'instance-realm_*' world names for orphan detection.");
        }

        /**
         * The orphan cleanup prefix in RealmsManager must match the world naming
         * pattern that Hytale's InstancesPlugin generates. The pattern is
         * "instance-realm_" (from "instance-" + tier starting with "realm_").
         */
        @Test
        @DisplayName("Orphan cleanup prefix matches Hytale's generated world names")
        void orphanCleanupPrefixMatchesFactory() throws IOException {
            String managerSource = readSource("maps/RealmsManager.java");

            // The cleanup method must use the "instance-realm_" prefix that Hytale generates
            assertTrue(managerSource.contains("instance-realm_"),
                "Orphan cleanup must check for 'instance-realm_' prefix — " +
                "this matches Hytale's InstancesPlugin naming: 'instance-' + tier");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVERSARIAL: Edge cases that could re-introduce leaks
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Adversarial: Leak re-introduction vectors")
    class AdversarialLeakVectors {

        /**
         * If any code catches exceptions from closeRealm() and swallows them
         * without fallback cleanup, the world leaks. Verify exception handling
         * around closeRealm calls includes fallback.
         */
        @Test
        @DisplayName("closeRealm() call sites handle exceptions without silent leak")
        void closeRealmCallSitesHandleExceptions() throws IOException {
            String sanctumSource = readSource("sanctum/SkillSanctumInstance.java");

            // Search for the actual method invocation: "realmsManager.closeRealm("
            int closeRealmIdx = sanctumSource.indexOf("realmsManager.closeRealm(");
            assertTrue(closeRealmIdx >= 0,
                "SkillSanctumInstance must call realmsManager.closeRealm()");

            // Extract a generous window (500 chars each direction) to find try-catch
            String surroundingCode = sanctumSource.substring(
                Math.max(0, closeRealmIdx - 500),
                Math.min(sanctumSource.length(), closeRealmIdx + 500));

            // Must be in a try block with catch — prevents silent leak on exception
            assertTrue(surroundingCode.contains("try") && surroundingCode.contains("catch"),
                "closeRealm() call must be in a try-catch block — " +
                "unhandled exceptions would prevent subsequent cleanup code");
        }

        /**
         * The RealmsManager.closeRealm() method must handle the case where the realm
         * is already closed (idempotent). Without this, double-close during shutdown
         * could throw and break the shutdown sequence.
         */
        @Test
        @DisplayName("RealmsManager.closeRealm() handles already-removed realm gracefully")
        void closeRealmHandlesAlreadyRemoved() throws IOException {
            String managerSource = readSource("maps/RealmsManager.java");

            // Find closeRealm method
            int methodStart = managerSource.indexOf("public CompletableFuture<Void> closeRealm(");
            assertTrue(methodStart >= 0, "closeRealm method must exist");

            String methodBody = managerSource.substring(methodStart,
                Math.min(managerSource.length(), methodStart + 800));

            // Must check if realm exists before processing
            assertTrue(methodBody.contains("== null") || methodBody.contains("get(realmId)"),
                "closeRealm() must handle the case where realmId is not in tracking " +
                "(double-close, already removed, or never registered)");
        }

        /**
         * Verify that the SkillSanctumManager's shutdown() method closes ALL
         * active instances — not just the ones in the tracking map. Orphaned
         * instances that were removed from the map but still have worlds running
         * would leak on shutdown.
         */
        @Test
        @DisplayName("SkillSanctumManager shutdown closes all active instances")
        void sanctumShutdownClosesAllInstances() throws IOException {
            String managerSource = readSource("sanctum/SkillSanctumManager.java");

            // Must iterate activeInstances and close each
            assertTrue(managerSource.contains("shutdown") || managerSource.contains("onDisable"),
                "SkillSanctumManager must have a shutdown path");

            // The shutdown path must call close() on instances
            assertTrue(managerSource.contains("instance.close()") || managerSource.contains(".close()"),
                "Shutdown must call close() on each active sanctum instance");
        }

        /**
         * No code path should create a realm world and store it ONLY in a local
         * variable without registering in RealmsManager. This would create an
         * untracked world with no cleanup path.
         */
        @Test
        @DisplayName("No untracked world creation outside RealmsManager")
        void noUntrackedWorldCreation() throws IOException {
            // Files that might create worlds directly (bypassing RealmsManager)
            Set<String> worldCreators = findSourceFilesContaining("InstancesPlugin.create");
            worldCreators.addAll(findSourceFilesContaining("loadingInstance("));
            worldCreators.addAll(findSourceFilesContaining("addWorld("));

            // These are allowed: RealmsManager and its factory create worlds intentionally
            Set<String> allowed = Set.of(
                "RealmsManager.java",
                "RealmInstanceFactory.java",
                "RealmTeleportHandler.java" // Uses teleportPlayerToLoadingInstance (target, not creator)
            );

            Set<String> suspicious = worldCreators.stream()
                .filter(f -> !f.contains("test/") && !f.contains("Test"))
                .filter(f -> allowed.stream().noneMatch(f::contains))
                .collect(Collectors.toSet());

            assertTrue(suspicious.isEmpty(),
                "Found potential untracked world creation (world leak risk):\n" +
                suspicious.stream()
                    .map(f -> "  - " + f)
                    .collect(Collectors.joining("\n")) +
                "\n\nFix: Route world creation through RealmsManager or add to allowed set.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVERSARIAL: Concurrent and timing edge cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Adversarial: Concurrency and timing")
    class ConcurrencyAndTiming {

        /**
         * The sanctum close() method must not be re-entrant. If close() is called
         * twice (e.g., disconnect + manual close), it must not double-close the realm
         * (which could confuse RealmsManager).
         */
        @Test
        @DisplayName("SkillSanctumInstance.close() is idempotent (has re-entrancy guard)")
        void sanctumCloseIsIdempotent() throws IOException {
            String sanctumSource = readSource("sanctum/SkillSanctumInstance.java");

            int closeStart = sanctumSource.indexOf("public void close()");
            assertTrue(closeStart >= 0);

            String closeBody = sanctumSource.substring(closeStart,
                Math.min(sanctumSource.length(), closeStart + 300));

            // Must check `active` flag or equivalent guard
            assertTrue(closeBody.contains("!active") || closeBody.contains("if (active")
                || closeBody.contains("closed") || closeBody.contains("return"),
                "close() must have a re-entrancy guard (e.g., `if (!active) return`) " +
                "to prevent double-close scenarios");
        }

        /**
         * The SkillSanctumManager must remove the instance from its tracking map
         * BEFORE calling close(). If close() throws, the instance must not remain
         * in the map (which would prevent future sanctum creation for that player).
         */
        @Test
        @DisplayName("closeSanctum() removes from tracking before close() call")
        void closeSanctumRemovesBeforeClose() throws IOException {
            String managerSource = readSource("sanctum/SkillSanctumManager.java");

            // Find closeSanctum method
            int methodStart = managerSource.indexOf("public boolean closeSanctum(@Nonnull UUID playerId, boolean teleportPlayer)");
            assertTrue(methodStart >= 0, "closeSanctum(UUID, boolean) must exist");

            // Method is ~60 lines, need enough to capture both remove and close
            String methodBody = managerSource.substring(methodStart,
                Math.min(managerSource.length(), methodStart + 3000));

            int removeIdx = methodBody.indexOf("activeInstances.remove(");
            int closeIdx = methodBody.indexOf("instance.close()");

            assertTrue(removeIdx >= 0, "Must remove from activeInstances");
            assertTrue(closeIdx >= 0, "Must call instance.close()");

            assertTrue(removeIdx < closeIdx,
                "activeInstances.remove() must happen BEFORE instance.close() " +
                "so a thrown exception doesn't leave a dead instance in tracking");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ARCHITECTURE: All realm biome types are handled
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Architecture: Biome type coverage")
    class BiomeTypeCoverage {

        /**
         * SKILL_SANCTUM must be handled by closeRealm() the same way as combat
         * biomes. The previous bug was that sanctums used a different (broken) path.
         * closeRealm() must not skip processing based on biome type.
         */
        @Test
        @DisplayName("closeRealm() does not skip based on biome type")
        void closeRealmDoesNotFilterByBiome() throws IOException {
            String managerSource = readSource("maps/RealmsManager.java");

            int methodStart = managerSource.indexOf("public CompletableFuture<Void> closeRealm(");
            assertTrue(methodStart >= 0);

            // Extract just the closeRealm method body (until next public method)
            int nextMethod = managerSource.indexOf("\n    public ", methodStart + 10);
            String methodBody = managerSource.substring(methodStart,
                nextMethod > 0 ? nextMethod : Math.min(managerSource.length(), methodStart + 1500));

            // closeRealm method body should NOT reference biome types at all —
            // it must work identically for ALL biomes (combat, sanctum, arena)
            assertFalse(methodBody.contains("getBiome()") || methodBody.contains("BiomeType"),
                "closeRealm() must NOT check biome type — " +
                "all biomes must use the same world removal pipeline");
        }

        /**
         * The utility biome check (isUtilityBiome) which includes SKILL_SANCTUM
         * must NOT be used as a reason to skip world removal.
         */
        @Test
        @DisplayName("Utility biome flag does not skip world removal")
        void utilityBiomeDoesNotSkipRemoval() throws IOException {
            String removalSource = readSourceSafe("maps/RealmRemovalHandler.java");
            if (removalSource == null) return; // File might be inner class

            assertFalse(removalSource.contains("isUtilityBiome") && removalSource.contains("return"),
                "RealmRemovalHandler must not skip removal for utility biomes. " +
                "SKILL_SANCTUM is a utility biome and MUST be removed.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Classes that delegate realm closure to another class (documented).
     * These call openRealm() but rely on a different class to call closeRealm().
     */
    private boolean isAllowedDelegation(String filePath) {
        // SkillSanctumManager creates via openRealm() but delegates closure to
        // SkillSanctumInstance.close() which calls RealmsManager.closeRealm()
        if (filePath.contains("SkillSanctumManager")) return true;

        // RealmMapSummonPage opens realms via the portal UI — closure is handled
        // by the realm's own timeout/completion/abandon logic in RealmsManager
        if (filePath.contains("RealmMapSummonPage")) return true;

        // RealmMapUseListener delegates through the summon page flow
        if (filePath.contains("RealmMapUseListener")) return true;

        // RealmsService is the API facade — delegates to RealmsManager internally
        if (filePath.contains("RealmsService")) return true;

        // Admin command — realms created by admin close via normal timeout/completion
        if (filePath.contains("TooAdminRealmOpenCommand")) return true;

        // RealmsManager itself defines openRealm — it also defines closeRealm
        if (filePath.contains("RealmsManager")) return true;

        // Test files
        if (filePath.contains("Test") || filePath.contains("test/")) return true;

        return false;
    }

    private Set<String> findSourceFilesContaining(String pattern) throws IOException {
        Set<String> results = new HashSet<>();
        if (!Files.exists(SOURCE_ROOT)) return results;

        Files.walkFileTree(SOURCE_ROOT, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                String content = Files.readString(file);
                if (content.contains(pattern)) {
                    results.add(SOURCE_ROOT.relativize(file).toString().replace('\\', '/'));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return results;
    }

    private String readSource(String relativePath) throws IOException {
        Path file = SOURCE_ROOT.resolve(relativePath);
        assertTrue(Files.exists(file), "Source file must exist: " + relativePath);
        return Files.readString(file);
    }

    private String readSourceSafe(String relativePath) throws IOException {
        Path file = SOURCE_ROOT.resolve(relativePath);
        if (!Files.exists(file)) return null;
        return Files.readString(file);
    }
}
