package io.github.larsonix.trailoforbis.gems.item;

import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.gear.item.CustomItemData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemInstanceId;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.model.GemDefinition;
import io.github.larsonix.trailoforbis.gems.model.GemType;
import io.github.larsonix.trailoforbis.gems.tooltip.GemTooltipBuilder;
import io.github.larsonix.trailoforbis.util.MessageSerializer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GemItemData
implements CustomItemData {
    public static final String BASE_ACTIVE_ITEM_ID = "RPG_Gem_Active";
    public static final String BASE_SUPPORT_ITEM_ID = "RPG_Gem_Support";
    private final CustomItemInstanceId instanceId;
    private final GemDefinition definition;
    private final GemData data;

    public GemItemData(@Nonnull CustomItemInstanceId instanceId, @Nonnull GemDefinition definition, @Nonnull GemData data) {
        this.instanceId = instanceId;
        this.definition = definition;
        this.data = data;
    }

    @Override
    @Nullable
    public CustomItemInstanceId getInstanceId() {
        return this.instanceId;
    }

    @Override
    @Nonnull
    public String getBaseItemId() {
        return this.data.gemType() == GemType.ACTIVE ? BASE_ACTIVE_ITEM_ID : BASE_SUPPORT_ITEM_ID;
    }

    @Override
    @Nonnull
    public String getDisplayName() {
        return this.definition.name();
    }

    @Override
    @Nonnull
    public String getDescription() {
        Message tooltip = GemTooltipBuilder.buildTooltip(this.definition, this.data);
        return MessageSerializer.toFormattedText(tooltip);
    }

    @Override
    @Nonnull
    public CustomItemInstanceId.ItemType getItemType() {
        return CustomItemInstanceId.ItemType.GEM;
    }

    public GemDefinition getDefinition() {
        return this.definition;
    }

    public GemData getData() {
        return this.data;
    }
}
