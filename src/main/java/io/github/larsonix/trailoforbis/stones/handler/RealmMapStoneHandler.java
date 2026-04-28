package io.github.larsonix.trailoforbis.stones.handler;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierRoller;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierRoller.RollResult;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import io.github.larsonix.trailoforbis.stones.StoneActionResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Handler for realm map stone operations.
 *
 * <p>Wraps {@link RealmModifierRoller} behind the {@link ItemTypeHandler}
 * interface. The roller works with split prefix/suffix lists via
 * {@link RollResult}, so this handler extracts lists from {@link RealmMapData},
 * feeds them through the roller, and rebuilds the data.
 *
 * <p>Also implements map-only operations: {@code identify()} and
 * {@code changeBiome()}.
 */
public class RealmMapStoneHandler implements ItemTypeHandler<RealmMapData> {

    private final RealmModifierRoller roller;

    /**
     * Creates a realm map stone handler.
     *
     * @param roller The realm modifier roller for modifier operations
     */
    public RealmMapStoneHandler(@Nonnull RealmModifierRoller roller) {
        this.roller = Objects.requireNonNull(roller, "roller cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER ROLLER OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public StoneActionResult rerollValues(@Nonnull RealmMapData item, @Nonnull Random random) {
        RollResult rerolled = roller.rerollValuesSplit(
            item.prefixes(), item.suffixes(), random, item.level());
        RealmMapData result = item.withPrefixes(rerolled.prefixes())
                                   .withSuffixes(rerolled.suffixes());
        return StoneActionResult.success(result, "Modifier values rerolled.");
    }

    @Override
    @Nonnull
    public StoneActionResult rerollOneValue(@Nonnull RealmMapData item, @Nonnull Random random) {
        List<RealmModifier> prefixes = item.prefixes();
        List<RealmModifier> suffixes = item.suffixes();

        // Find unlocked modifiers with their list position and local index
        record UnlockedMod(boolean isPrefix, int localIndex, RealmModifier mod) {}
        List<UnlockedMod> unlocked = new ArrayList<>();

        for (int i = 0; i < prefixes.size(); i++) {
            if (!prefixes.get(i).locked()) {
                unlocked.add(new UnlockedMod(true, i, prefixes.get(i)));
            }
        }
        for (int i = 0; i < suffixes.size(); i++) {
            if (!suffixes.get(i).locked()) {
                unlocked.add(new UnlockedMod(false, i, suffixes.get(i)));
            }
        }

        if (unlocked.isEmpty()) {
            return StoneActionResult.noUnlockedModifiers();
        }

        // Pick one random unlocked modifier
        UnlockedMod target = unlocked.get(random.nextInt(unlocked.size()));

        // Reroll just that one modifier's value
        RealmModifier rerolled = roller.rollModifier(target.mod().type(), random, item.level());
        if (target.mod().locked()) {
            rerolled = rerolled.withLockedState(true);
        }

        // Update the correct list
        RealmMapData result;
        if (target.isPrefix()) {
            List<RealmModifier> newPrefixes = new ArrayList<>(prefixes);
            newPrefixes.set(target.localIndex(), rerolled);
            result = item.withPrefixes(newPrefixes);
        } else {
            List<RealmModifier> newSuffixes = new ArrayList<>(suffixes);
            newSuffixes.set(target.localIndex(), rerolled);
            result = item.withSuffixes(newSuffixes);
        }

        return StoneActionResult.success(result,
            "Rerolled: " + target.mod().type().getDisplayName());
    }

    @Override
    @Nonnull
    public StoneActionResult rerollTypes(@Nonnull RealmMapData item, @Nonnull Random random) {
        RollResult rerolled = roller.rerollTypesSplit(
            item.prefixes(), item.suffixes(), random, item.level());
        RealmMapData result = item.withPrefixes(rerolled.prefixes())
                                   .withSuffixes(rerolled.suffixes());
        return StoneActionResult.success(result, "Modifiers rerolled.");
    }

    @Override
    @Nonnull
    public StoneActionResult addModifier(@Nonnull RealmMapData item, @Nonnull Random random) {
        RollResult newMods = roller.addModifierSplit(
            item.prefixes(), item.suffixes(), item.maxModifiers(), random, item.level());

        if (newMods.totalCount() == item.modifierCount()) {
            return StoneActionResult.failure("No compatible modifiers available.");
        }

        RealmMapData result = item.withPrefixes(newMods.prefixes())
                                   .withSuffixes(newMods.suffixes());
        return StoneActionResult.success(result, "Added a new modifier.");
    }

    @Override
    @Nonnull
    public StoneActionResult removeModifier(@Nonnull RealmMapData item, @Nonnull Random random) {
        RollResult newMods = roller.removeModifierSplit(
            item.prefixes(), item.suffixes(), random);
        RealmMapData result = item.withPrefixes(newMods.prefixes())
                                   .withSuffixes(newMods.suffixes());
        return StoneActionResult.success(result, "Removed a random modifier.");
    }

    @Override
    @Nonnull
    public StoneActionResult clearUnlockedModifiers(@Nonnull RealmMapData item) {
        List<RealmModifier> lockedPrefixes = item.prefixes().stream()
            .filter(RealmModifier::locked)
            .toList();
        List<RealmModifier> lockedSuffixes = item.suffixes().stream()
            .filter(RealmModifier::locked)
            .toList();

        RealmMapData result = item.withPrefixes(lockedPrefixes)
                                   .withSuffixes(lockedSuffixes);

        int removed = item.modifierCount() - result.modifierCount();
        return StoneActionResult.success(result,
            "Removed " + removed + " modifier" + (removed != 1 ? "s" : "") + ".");
    }

    @Override
    @Nonnull
    public StoneActionResult transmute(@Nonnull RealmMapData item, @Nonnull Random random) {
        // First remove one random unlocked modifier
        RollResult afterRemove = roller.removeModifierSplit(
            item.prefixes(), item.suffixes(), random);

        if (afterRemove.totalCount() == item.modifierCount()) {
            return StoneActionResult.noUnlockedModifiers();
        }

        // Then add a new modifier
        RollResult afterAdd = roller.addModifierSplit(
            afterRemove.prefixes(), afterRemove.suffixes(), item.maxModifiers(), random, item.level());

        if (afterAdd.totalCount() == afterRemove.totalCount()) {
            // Couldn't add — still apply the removal
            RealmMapData result = item.withPrefixes(afterRemove.prefixes())
                                       .withSuffixes(afterRemove.suffixes());
            return StoneActionResult.success(result,
                "Removed a modifier but no replacement available.");
        }

        RealmMapData result = item.withPrefixes(afterAdd.prefixes())
                                   .withSuffixes(afterAdd.suffixes());
        return StoneActionResult.success(result,
            "Swapped: removed one modifier and added a new one.");
    }

    @Override
    @Nonnull
    public StoneActionResult fillModifiers(@Nonnull RealmMapData item, @Nonnull Random random) {
        RollResult newMods = roller.fillModifierSlots(
            item.rarity(), item.prefixes(), item.suffixes(), random, item.level());

        // Merge new modifiers with existing
        List<RealmModifier> mergedPrefixes = new ArrayList<>(item.prefixes());
        mergedPrefixes.addAll(newMods.prefixes());
        List<RealmModifier> mergedSuffixes = new ArrayList<>(item.suffixes());
        mergedSuffixes.addAll(newMods.suffixes());

        RealmMapData result = item.withPrefixes(mergedPrefixes)
                                   .withSuffixes(mergedSuffixes);
        int added = newMods.totalCount();
        if (added == 0) {
            return StoneActionResult.failure("No compatible modifiers available.");
        }
        return StoneActionResult.success(result,
            "Filled " + added + " modifier slot" + (added != 1 ? "s" : "") + ".");
    }

    @Override
    @Nonnull
    public StoneActionResult lockModifier(@Nonnull RealmMapData item, int combinedIndex) {
        String modName = item.modifiers().get(combinedIndex).displayName();
        int prefixCount = item.prefixCount();
        RealmMapData result;

        if (combinedIndex < prefixCount) {
            List<RealmModifier> newPrefixes = new ArrayList<>(item.prefixes());
            newPrefixes.set(combinedIndex, newPrefixes.get(combinedIndex).withLockedState(true));
            result = item.withPrefixes(newPrefixes);
        } else {
            int suffixIndex = combinedIndex - prefixCount;
            List<RealmModifier> newSuffixes = new ArrayList<>(item.suffixes());
            newSuffixes.set(suffixIndex, newSuffixes.get(suffixIndex).withLockedState(true));
            result = item.withSuffixes(newSuffixes);
        }

        return StoneActionResult.success(result, "Locked: " + modName);
    }

    @Override
    @Nonnull
    public StoneActionResult unlockModifier(@Nonnull RealmMapData item, int combinedIndex) {
        String modName = item.modifiers().get(combinedIndex).displayName();
        int prefixCount = item.prefixCount();
        RealmMapData result;

        if (combinedIndex < prefixCount) {
            List<RealmModifier> newPrefixes = new ArrayList<>(item.prefixes());
            newPrefixes.set(combinedIndex, newPrefixes.get(combinedIndex).withLockedState(false));
            result = item.withPrefixes(newPrefixes);
        } else {
            int suffixIndex = combinedIndex - prefixCount;
            List<RealmModifier> newSuffixes = new ArrayList<>(item.suffixes());
            newSuffixes.set(suffixIndex, newSuffixes.get(suffixIndex).withLockedState(false));
            result = item.withSuffixes(newSuffixes);
        }

        return StoneActionResult.success(result, "Unlocked: " + modName);
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPLEX MULTI-OUTCOME OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public StoneActionResult corrupt(@Nonnull RealmMapData item, @Nonnull Random random) {
        int roll = random.nextInt(100);
        RealmMapData result;

        if (roll < 35) {
            // 35%: Just corrupt, no other change
            result = item.corrupt();
            return StoneActionResult.success(result, "Item corrupted.");
        } else if (roll < 60) {
            // 25%: Reroll all modifiers and corrupt
            RollResult newMods = roller.rollModifiersSplit(item.rarity(), random, item.level());
            result = item.withPrefixes(newMods.prefixes())
                          .withSuffixes(newMods.suffixes())
                          .corrupt();
            return StoneActionResult.success(result,
                "Item corrupted with new modifiers !");
        } else if (roll < 85) {
            // 25%: Add a corruption modifier (SPECIAL category = prefix)
            RealmModifierType corruptMod = getRandomCorruptionModifier(random);
            if (corruptMod != null && item.canAddModifier()) {
                RealmModifier newMod = roller.rollModifier(corruptMod, random, item.level());
                List<RealmModifier> newPrefixes = new ArrayList<>(item.prefixes());
                newPrefixes.add(newMod);
                result = item.withPrefixes(newPrefixes).corrupt();
                return StoneActionResult.success(result,
                    "Item corrupted with : " + newMod.formatForTooltip());
            }
            result = item.corrupt();
            return StoneActionResult.success(result, "Item corrupted.");
        } else {
            // 15%: Upgrade rarity and corrupt
            GearRarity next = getNextRarity(item.rarity());
            if (next != null) {
                result = item.withRarity(next).corrupt();
                return StoneActionResult.success(result,
                    "Item corrupted and upgraded to " + next.getHytaleQualityId() + " !");
            }
            result = item.corrupt();
            return StoneActionResult.success(result, "Item corrupted.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TYPE-SPECIFIC OPERATIONS (map-only)
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public StoneActionResult identify(@Nonnull RealmMapData item) {
        if (item.isIdentified()) {
            return StoneActionResult.alreadyIdentified();
        }
        RealmMapData result = item.identify();
        return StoneActionResult.success(result, "Item identified.");
    }

    @Override
    @Nonnull
    public StoneActionResult changeBiome(@Nonnull RealmMapData item, @Nonnull Random random) {
        RealmBiomeType[] allBiomes = RealmBiomeType.values();
        if (allBiomes.length <= 1) {
            return StoneActionResult.failure("No alternative biomes available.");
        }

        // Remove current biome from choices
        RealmBiomeType currentBiome = item.biome();
        List<RealmBiomeType> choices = new ArrayList<>();
        for (RealmBiomeType b : allBiomes) {
            if (b != currentBiome) {
                choices.add(b);
            }
        }

        if (choices.isEmpty()) {
            return StoneActionResult.failure("No alternative biomes available.");
        }

        RealmBiomeType newBiome = choices.get(random.nextInt(choices.size()));
        RealmMapData result = item.withBiome(newBiome);
        return StoneActionResult.success(result,
            "Biome changed to " + newBiome.getDisplayName() + ".");
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    @Nullable
    private GearRarity getNextRarity(@Nonnull GearRarity current) {
        return switch (current) {
            case COMMON -> GearRarity.UNCOMMON;
            case UNCOMMON -> GearRarity.RARE;
            case RARE -> GearRarity.EPIC;
            case EPIC -> GearRarity.LEGENDARY;
            case LEGENDARY -> GearRarity.MYTHIC;
            case MYTHIC, UNIQUE -> null;
        };
    }

    @Nullable
    private RealmModifierType getRandomCorruptionModifier(@Nonnull Random random) {
        var prefixMods = RealmModifierType.byCategory(RealmModifierType.Category.PREFIX);
        if (prefixMods.isEmpty()) {
            return null;
        }
        return prefixMods.get(random.nextInt(prefixMods.size()));
    }
}
