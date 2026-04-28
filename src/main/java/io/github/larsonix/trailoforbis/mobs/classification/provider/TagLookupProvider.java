package io.github.larsonix.trailoforbis.mobs.classification.provider;

/**
 * Abstraction for Hytale's TagSet system to allow unit testing.
 *
 * <p>In production, this delegates to {@link com.hypixel.hytale.builtin.tagset.TagSetPlugin}.
 * In tests, this can be mocked.
 */
public interface TagLookupProvider {
    /**
     * Checks if a role index belongs to a given tag group.
     *
     * @param groupName The name of the tag group (e.g., "undead")
     * @param roleIndex The index of the role to check
     * @return true if the role has the tag
     */
    boolean hasTag(String groupName, int roleIndex);
}
