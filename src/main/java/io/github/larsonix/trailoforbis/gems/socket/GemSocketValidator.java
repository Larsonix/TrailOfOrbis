package io.github.larsonix.trailoforbis.gems.socket;

import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.model.GemType;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GemSocketValidator {
    @Nonnull
    public ValidationResult canSocketActive(@Nonnull GearData gearData, @Nonnull GemData gem) {
        if (gem.gemType() != GemType.ACTIVE) {
            return ValidationResult.fail("This is not an active gem.");
        }
        return ValidationResult.ok();
    }

    @Nonnull
    public ValidationResult canSocketSupport(@Nonnull Inventory inventory, @Nonnull ItemStack targetGear, @Nonnull GearData gearData, @Nonnull GemData gem) {
        if (gem.gemType() != GemType.SUPPORT) {
            return ValidationResult.fail("This is not a support gem.");
        }
        if (gearData.availableSupportSlots() <= 0) {
            if (gearData.supportSlotCount() == 0) {
                return ValidationResult.fail("No support slots. Use a Socket Stone first.");
            }
            return ValidationResult.fail("All support slots are full.");
        }
        Set<String> usedSupportIds = this.collectUsedSupportGemIds(inventory, targetGear);
        if (usedSupportIds.contains(gem.gemId())) {
            return ValidationResult.fail("This support gem is already used on another gear piece (Unique Rule).");
        }
        return ValidationResult.ok();
    }

    @Nonnull
    public Set<String> collectUsedSupportGemIds(@Nonnull Inventory inventory, @Nonnull ItemStack excludeGear) {
        HashSet<String> usedIds = new HashSet<>();
        ItemContainer armor = inventory.getArmor();
        for (short i = 0; i < armor.getCapacity(); i = (short)(i + 1)) {
            ItemStack item = armor.getItemStack(i);
            if (item == null || item.isEmpty() || item == excludeGear) continue;
            GearUtils.getGearData(item).ifPresent(gear -> gear.supportGems().forEach(gem -> usedIds.add(gem.gemId())));
        }
        ItemStack weapon = inventory.getItemInHand();
        if (weapon != null && !weapon.isEmpty() && weapon != excludeGear) {
            GearUtils.getGearData(weapon).ifPresent(gear -> gear.supportGems().forEach(gem -> usedIds.add(gem.gemId())));
        }
        return usedIds;
    }

    public record ValidationResult(boolean allowed, @Nullable String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
