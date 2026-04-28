package io.github.larsonix.trailoforbis.maps.modifiers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RealmModifier")
class RealmModifierTest {

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("Should create modifier with valid values")
        void createValid() {
            RealmModifier mod = new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 50, false);

            assertEquals(RealmModifierType.MONSTER_DAMAGE, mod.type());
            assertEquals(50, mod.value());
            assertFalse(mod.locked());
        }

        @Test
        @DisplayName("Should reject null type")
        void rejectNullType() {
            assertThrows(NullPointerException.class,
                () -> new RealmModifier(null, 50, false));
        }
    }

    @Nested
    @DisplayName("of factory")
    class OfFactory {

        @Test
        @DisplayName("Should create unlocked modifier")
        void createUnlocked() {
            RealmModifier mod = RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 25);

            assertEquals(RealmModifierType.ITEM_QUANTITY, mod.type());
            assertEquals(25, mod.value());
            assertFalse(mod.locked());
        }
    }

    @Nested
    @DisplayName("ItemModifier interface")
    class ItemModifierInterface {

        @Test
        @DisplayName("id should return type name")
        void idReturnsTypeName() {
            RealmModifier mod = RealmModifier.of(RealmModifierType.ITEM_RARITY, 30);
            assertEquals("ITEM_RARITY", mod.id());
        }

        @Test
        @DisplayName("displayName should return type display name")
        void displayNameReturnsTypeDisplayName() {
            RealmModifier mod = RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 50);
            assertEquals("Monster Life", mod.displayName());
        }

        @Test
        @DisplayName("getValue should return value as double")
        void getValueReturnsDouble() {
            RealmModifier mod = RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 40);
            assertEquals(40.0, mod.getValue());
        }

        @Test
        @DisplayName("withLocked should create new instance")
        void withLockedCreatesNew() {
            RealmModifier original = RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20);
            RealmModifier locked = (RealmModifier) original.withLocked(true);

            assertFalse(original.locked());
            assertTrue(locked.locked());
            assertEquals(original.type(), locked.type());
            assertEquals(original.value(), locked.value());
        }

        @Test
        @DisplayName("withValue should create new instance")
        void withValueCreatesNew() {
            RealmModifier original = RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 50);
            RealmModifier modified = (RealmModifier) original.withValue(75.0);

            assertEquals(50, original.value());
            assertEquals(75, modified.value());
        }
    }

    @Nested
    @DisplayName("formatForTooltip")
    class FormatForTooltip {

        @Test
        @DisplayName("Should format unlocked modifier")
        void formatUnlocked() {
            RealmModifier mod = RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 40);
            assertEquals("+40% Monster Damage", mod.formatForTooltip());
        }

        @Test
        @DisplayName("Should add lock icon for locked modifier")
        void formatLocked() {
            RealmModifier mod = new RealmModifier(RealmModifierType.ITEM_QUANTITY, 20, true);
            assertTrue(mod.formatForTooltip().startsWith("🔒"));
        }
    }

    @Nested
    @DisplayName("getValuePercentile")
    class GetValuePercentile {

        @Test
        @DisplayName("Should return 0 for minimum value")
        void minValueIs0() {
            RealmModifier mod = new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 10, false);
            assertEquals(0.0f, mod.getValuePercentile(), 0.01f);
        }

        @Test
        @DisplayName("Should return 1 for maximum value")
        void maxValueIs1() {
            RealmModifier mod = new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 100, false);
            assertEquals(1.0f, mod.getValuePercentile(), 0.01f);
        }

        @Test
        @DisplayName("Should return 0.5 for middle value")
        void middleValueIsHalf() {
            RealmModifier mod = new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 55, false);
            assertEquals(0.5f, mod.getValuePercentile(), 0.01f);
        }
    }

    @Nested
    @DisplayName("category helpers")
    class CategoryHelpers {

        @Test
        @DisplayName("ITEM_QUANTITY should be reward modifier")
        void itemQuantityIsReward() {
            RealmModifier mod = RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20);
            assertTrue(mod.isRewardModifier());
            assertFalse(mod.increasesDifficulty());
        }

        @Test
        @DisplayName("MONSTER_DAMAGE should increase difficulty")
        void monsterDamageIncreasesDifficulty() {
            RealmModifier mod = RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 50);
            assertFalse(mod.isRewardModifier());
            assertTrue(mod.increasesDifficulty());
        }
    }

    @Nested
    @DisplayName("isPrefix/isSuffix")
    class IsPrefixIsSuffix {

        @Test
        @DisplayName("MONSTER category modifiers should be prefix")
        void monsterCategoryShouldBePrefix() {
            RealmModifier damage = RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 50);
            RealmModifier health = RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 50);
            RealmModifier speed = RealmModifier.of(RealmModifierType.MONSTER_SPEED, 20);

            assertTrue(damage.isPrefix(), "MONSTER_DAMAGE should be prefix");
            assertTrue(health.isPrefix(), "MONSTER_HEALTH should be prefix");
            assertTrue(speed.isPrefix(), "MONSTER_SPEED should be prefix");

            assertFalse(damage.isSuffix(), "MONSTER_DAMAGE should not be suffix");
            assertFalse(health.isSuffix(), "MONSTER_HEALTH should not be suffix");
            assertFalse(speed.isSuffix(), "MONSTER_SPEED should not be suffix");
        }

        @Test
        @DisplayName("Reclassified modifiers should be suffix")
        void reclassifiedModifiersShouldBeSuffix() {
            RealmModifier extraMonsters = RealmModifier.of(RealmModifierType.EXTRA_MONSTERS, 30);
            RealmModifier eliteChance = RealmModifier.of(RealmModifierType.ELITE_CHANCE, 15);

            assertTrue(extraMonsters.isSuffix(), "EXTRA_MONSTERS should be suffix");
            assertTrue(eliteChance.isSuffix(), "ELITE_CHANCE should be suffix");

            assertFalse(extraMonsters.isPrefix(), "EXTRA_MONSTERS should not be prefix");
            assertFalse(eliteChance.isPrefix(), "ELITE_CHANCE should not be prefix");
        }

        @Test
        @DisplayName("REDUCED_TIME should be prefix")
        void reducedTimeShouldBePrefix() {
            RealmModifier reducedTime = RealmModifier.of(RealmModifierType.REDUCED_TIME, 20);

            assertTrue(reducedTime.isPrefix(), "REDUCED_TIME should be prefix");
            assertFalse(reducedTime.isSuffix(), "REDUCED_TIME should not be suffix");
        }

        @Test
        @DisplayName("NO_REGENERATION should be prefix")
        void noRegenShouldBePrefix() {
            RealmModifier noRegen = RealmModifier.of(RealmModifierType.NO_REGENERATION, 1);

            assertTrue(noRegen.isPrefix(), "NO_REGENERATION should be prefix");
            assertFalse(noRegen.isSuffix(), "NO_REGENERATION should not be suffix");
        }

        @Test
        @DisplayName("REWARD category modifiers should be suffix")
        void rewardCategoryShouldBeSuffix() {
            RealmModifier iiq = RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 25);
            RealmModifier iir = RealmModifier.of(RealmModifierType.ITEM_RARITY, 30);
            RealmModifier xp = RealmModifier.of(RealmModifierType.EXPERIENCE_BONUS, 20);
            RealmModifier stones = RealmModifier.of(RealmModifierType.STONE_DROP_BONUS, 15);

            assertTrue(iiq.isSuffix(), "ITEM_QUANTITY should be suffix");
            assertTrue(iir.isSuffix(), "ITEM_RARITY should be suffix");
            assertTrue(xp.isSuffix(), "EXPERIENCE_BONUS should be suffix");
            assertTrue(stones.isSuffix(), "STONE_DROP_BONUS should be suffix");

            assertFalse(iiq.isPrefix(), "ITEM_QUANTITY should not be prefix");
            assertFalse(iir.isPrefix(), "ITEM_RARITY should not be prefix");
            assertFalse(xp.isPrefix(), "EXPERIENCE_BONUS should not be prefix");
            assertFalse(stones.isPrefix(), "STONE_DROP_BONUS should not be prefix");
        }

        @Test
        @DisplayName("isPrefix and isSuffix should be mutually exclusive")
        void shouldBeMutuallyExclusive() {
            for (RealmModifierType type : RealmModifierType.values()) {
                RealmModifier mod = RealmModifier.of(type, type.getMinValue());
                boolean isPrefix = mod.isPrefix();
                boolean isSuffix = mod.isSuffix();

                assertTrue(isPrefix != isSuffix,
                    type.name() + " should be either prefix or suffix, not both or neither");
            }
        }
    }
}
