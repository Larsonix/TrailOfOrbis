package io.github.larsonix.trailoforbis.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.commands.tooadmin.*;
import io.github.larsonix.trailoforbis.commands.tooadmin.entity.TooAdminEntityCommand;
import io.github.larsonix.trailoforbis.commands.tooadmin.gear.TooAdminGearCommand;
import io.github.larsonix.trailoforbis.commands.tooadmin.give.TooAdminGiveCommand;
import io.github.larsonix.trailoforbis.commands.tooadmin.loot.TooAdminLootCommand;
import io.github.larsonix.trailoforbis.commands.tooadmin.map.TooAdminMapCommand;
import io.github.larsonix.trailoforbis.commands.tooadmin.realm.TooAdminRealmCommand;
import io.github.larsonix.trailoforbis.commands.tooadmin.sanctum.TooAdminSanctumCommand;
import io.github.larsonix.trailoforbis.commands.tooadmin.skilltree.TooAdminSkillTreeCommand;
import io.github.larsonix.trailoforbis.commands.tooadmin.stone.TooAdminStoneCommand;

/**
 * Root command collection for Trail of Orbis admin commands.
 *
 * <p>This command provides administrative tools for managing player RPG data.
 * Requires the "too.admin" permission.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/tooadmin inspect &lt;player&gt; - Full player inspection</li>
 *   <li>/tooadmin reload - Reload configuration</li>
 *   <li>/tooadmin xp &lt;add|remove|set&gt; &lt;player&gt; &lt;amount&gt; - XP management</li>
 *   <li>/tooadmin level &lt;add|remove|set|check&gt; &lt;player&gt; [amount] - Level management</li>
 *   <li>/tooadmin points &lt;add|remove|set&gt; &lt;player&gt; &lt;amount&gt; - Unallocated points</li>
 *   <li>/tooadmin attr &lt;add|remove|set&gt; &lt;player&gt; &lt;type&gt; &lt;amount&gt; - Attribute management</li>
 *   <li>/tooadmin skillpoints &lt;add|remove|set&gt; &lt;player&gt; &lt;amount&gt; - Skill points</li>
 *   <li>/tooadmin skilltree - Skill tree admin collection</li>
 *   <li>/tooadmin reset &lt;player&gt; - Full player reset</li>
 *   <li>/tooadmin resetconfirm &lt;player&gt; - Confirm reset</li>
 *   <li>/tooadmin realm - Realm admin collection</li>
 *   <li>/tooadmin sanctum - Sanctum admin collection</li>
 *   <li>/tooadmin gear - Gear admin collection</li>
 *   <li>/tooadmin map - Map admin collection (info, copy, paste)</li>
 *   <li>/tooadmin stone - Stone admin collection (info, copy, paste)</li>
 *   <li>/tooadmin loot - Loot discovery admin collection</li>
 *   <li>/tooadmin entity - Entity discovery admin collection</li>
 *   <li>/tooadmin give - Item giving collection (gear, map, stone)</li>
 * </ul>
 */
public final class TooAdminCommand extends AbstractCommandCollection {

    public TooAdminCommand(TrailOfOrbis plugin) {
        super("tooadmin", "Admin commands for Trail of Orbis");
        this.addAliases("tooa");

        // Leaf admin commands
        this.addSubCommand(new TooAdminInspectCommand(plugin));
        this.addSubCommand(new TooAdminReloadCommand(plugin));
        this.addSubCommand(new TooAdminXpCommand(plugin));
        this.addSubCommand(new TooAdminLevelCommand(plugin));
        this.addSubCommand(new TooAdminPointsCommand(plugin));
        this.addSubCommand(new TooAdminAttrCommand(plugin));
        this.addSubCommand(new TooAdminSkillPointsCommand(plugin));
        this.addSubCommand(new TooAdminResetCommand(plugin));
        this.addSubCommand(new TooAdminResetConfirmCommand(plugin));
        this.addSubCommand(new TooAdminGuideCommand(plugin));

        // Subcommand collections
        this.addSubCommand(new TooAdminSkillTreeCommand(plugin));
        this.addSubCommand(new TooAdminRealmCommand(plugin));
        this.addSubCommand(new TooAdminSanctumCommand(plugin));
        this.addSubCommand(new TooAdminGearCommand(plugin));
        this.addSubCommand(new TooAdminMapCommand(plugin));
        this.addSubCommand(new TooAdminStoneCommand(plugin));
        this.addSubCommand(new TooAdminLootCommand(plugin));
        this.addSubCommand(new TooAdminEntityCommand(plugin));
        this.addSubCommand(new TooAdminGiveCommand(plugin));
        this.addSubCommand(new TooAdminTestColorCommand());

        this.requirePermission("too.admin");
    }
}
