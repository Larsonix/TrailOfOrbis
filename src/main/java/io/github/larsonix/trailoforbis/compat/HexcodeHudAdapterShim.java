package io.github.larsonix.trailoforbis.compat;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.riprod.hexcode.core.common.hud.api.HudAdapter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * MHUD-aware adapter injected into Hexcode's {@code HudController} via reflection.
 *
 * <p>Hexcode v0.7.0 added a "yield to foreign HUDs" mechanism: {@code ensureHud()} calls
 * {@code adapter.getCurrentHud()} and if it returns a HUD that isn't Hexcode's own, Hexcode
 * silently hides its HUD. With MHUD active, {@code getCustomHud()} returns the MCHUD
 * composite wrapper, which always triggers the yield.
 *
 * <p>This adapter neutralizes the yield by returning Hexcode's own {@code HexcodeHud} from
 * the {@code activeHuds} map (instead of querying the HudManager), and routes
 * {@code setHud}/{@code clearHud} through MHUD for proper multi-HUD coexistence.
 */
public final class HexcodeHudAdapterShim implements HudAdapter {

    private static final String HUD_NAME = "HexcodeHud";

    private final Map<UUID, ? extends CustomUIHud> activeHuds;

    @SuppressWarnings("unchecked")
    public HexcodeHudAdapterShim(@Nonnull Map<UUID, ?> activeHuds) {
        this.activeHuds = (Map<UUID, ? extends CustomUIHud>) activeHuds;
    }

    @Nullable
    @Override
    public CustomUIHud getCurrentHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        // Return Hexcode's own HexcodeHud for this player (if one exists).
        // This makes ensureHud()'s fast-path succeed: current == existing → true.
        // The yield check (current != null && current != existing) never fires.
        return activeHuds.get(playerRef.getUuid());
    }

    @Override
    public void setHud(@Nonnull Player player, @Nonnull PlayerRef playerRef, @Nonnull CustomUIHud hud) {
        MultipleHUD.getInstance().setCustomHud(player, playerRef, HUD_NAME, hud);
    }

    @Override
    public void clearHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        MultipleHUD.getInstance().hideCustomHud(player, playerRef, HUD_NAME);
    }
}
