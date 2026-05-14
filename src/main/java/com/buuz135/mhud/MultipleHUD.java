package com.buuz135.mhud;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Shim for Buuz135's MultipleHUD plugin API.
 *
 * <p>Delegates directly to HyUI's bundled MHUD implementation at
 * {@code au.ellie.hyui.utils.multiplehud.MultipleHUD}. We CANNOT delegate
 * to {@code MultiHudWrapper} because it does {@code Class.forName("com.buuz135.mhud.MultipleHUD")}
 * which finds THIS class, creating an infinite recursion loop.
 *
 * <p>This class is injected into PartyPro's HudWrapper via reflection so it
 * uses multi-HUD mode instead of the destructive direct setCustomHud() fallback.
 */
public class MultipleHUD {

    private static final MultipleHUD INSTANCE = new MultipleHUD();

    /** HyUI's own bundled MHUD — the actual implementation we delegate to. */
    private final au.ellie.hyui.utils.multiplehud.MultipleHUD delegate =
        au.ellie.hyui.utils.multiplehud.MultipleHUD.getInstance();

    public static MultipleHUD getInstance() {
        return INSTANCE;
    }

    public void setCustomHud(Player player, PlayerRef playerRef, String name, CustomUIHud hud) {
        delegate.setCustomHud(player, playerRef, name, hud);
    }

    public void hideCustomHud(Player player, PlayerRef playerRef, String name) {
        delegate.hideCustomHud(player, playerRef, name);
    }

    public void hideCustomHud(Player player, String name) {
        delegate.hideCustomHud(player, name);
    }
}
