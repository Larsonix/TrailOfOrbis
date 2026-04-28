package io.github.larsonix.trailoforbis.mobs.classification.provider;

import com.hypixel.hytale.builtin.tagset.TagSetPlugin;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Production implementation of TagLookupProvider using Hytale's internal API.
 */
public class HytaleTagLookupProvider implements TagLookupProvider {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Deduplicate "not found" warnings — log each missing group name only once
    private final Set<String> warnedGroups = ConcurrentHashMap.newKeySet();

    @Override
    public boolean hasTag(String groupName, int roleIndex) {
        // TagSetPlugin might not be initialized during early setup or tests
        if (TagSetPlugin.get() == null) {
            return false;
        }

        // Look up the internal ID for the tag name
        int groupIndex = NPCGroup.getAssetMap().getIndex(groupName);

        // If tag exists, check membership
        if (groupIndex >= 0) {
            try {
                return TagSetPlugin.get(NPCGroup.class).tagInSet(groupIndex, roleIndex);
            } catch (Exception e) {
                // Handle edge case where tag set lookup fails
                return false;
            }
        }

        // Log missing group names once for diagnostics
        if (warnedGroups.add(groupName)) {
            LOGGER.at(Level.FINE).log("NPCGroup '%s' not found in asset map", groupName);
        }
        return false;
    }
}
