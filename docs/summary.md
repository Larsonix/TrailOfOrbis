I need your help to perfectly implement or debug or modify a feature using all our docs and game code and our code so it's the best possible.

No shortcut, no guesses. I you still have questions after having verified everything, ask away.

Make sure no regression will occur.



* One day = Implement a wheel like the in-game selection wheel, a custom one when used with those specific items in hand in which you can put "Augment" gems for weapons and gear, idk what it does, maybe add modifiers, like completely unique ones or I have no clue we'll see



\- Way of opening a portal for skill tree world

\- Banner for level up

\- Banner for map entering

\- Banner for 3D skilltree entering - done ?



\- Energy Shield HUD bar



\- Have the health bar show a value to know your current vs Max HP



\- Find if we can know if a player is currently attacked by a mob, like is a hostile mob actively aiming at a player, and if he is, deny any teleportatin like skilltree tp until mobs no longer attack him at all (he killed them or escaped) also we should NOT allow skilltree tp from a map/realm from our plugin and should give feedback to the player about it that he can't in a maprealm.

\- Change the config so numbers for loot % are not stupid like 0.00005% per level gained but more like

\- Fix death recap calculation especially regarding armor/defences ?

\- Evasion doesn't have it's conversion into % out of a 100 shown in stats screen



Bug: If a player disconnects during a map/realm, and only reconnects once the timer of the same map/realm is finished, he gets a completely bugged HUD on the top of his screen with tens of ancient gateways in a long row, and has no vanilla HUD at all anymore. I collected as much info as I could but... Sorry, it's not a lot and it's really weird.


The people want a DPS counter on our plugin, ideally they would like either just the counter, and a possibility to have a detailed version with the bottom of our /too combat detail chat with the "damage applied" and total section. Ideally, we could do it the same way we created the HUD in the 3D skill tree sanctum realm when we click on a node. It just needs to be higher if detailed is on to accommodate for the larger text. For the DPS timer and the DPS configurations possible Iq need your help so it's really easily configurable clear, straightforward, but allows all the basic wanted options by players.









For every asking/problem/refactor/change our code needs I'll input under, the goal will be for you to :

\- Fully understand the situation, the context, and what we're creating

\- The current problem in the context of our plugin

\- once you understand everything, you'll look into our code to understand how we did it and everything related

\- Then, you'll look thru all the documentation we created from decompiled client, server, to anything needed using everything we created optimally

\- Finally, you'll brainstorm a full plan on how exactly implement/change/fix what I'm asking in the best possible way in our plugin, while trying as much as possible to do things the less hacky way possible and the most optimum for big servers and a lot of players, while thinking still about robustness and expandability, it's only the start of our plugin.



You are allowed to ask me questions if it's related to how things should work or mechanics or details if it needs clarification





-------------------------------------



\- Stones don't drop right now ? My friend is doing a let's play from level 1 he is 20 he got no stone drop at all even tho I should have boosted their odds in the config ? Can you verify everything thoroughly, find the problem, and the best and most proper way to fix it so everything works.



\- Charged attacks and Signature attacks do more damage in the vanilla game but don't since we rewrote the base vanilla damage. I don't know how it works exactly in the base game you'll have to research it fully, we rewrote over the vanilla game everything about calculations but so because we didn't think about the details of the implementation, charged attacks and signature ones are considered as normal attack damage, we need to implement this entire feature to avoid this problem.



\- The mobs in the maps/realms should have a drop rate of modded items doubled compared to normal so players have more reasons to do maps. also, we need to double the number of monsters spawned in any map compared at any level compared to what it is currently.



\- Some nodes like going from Recovery to Regenerate III or Quick Recovery is not possible, it says "Cannot allocate this node" even tho the node before was correctly allocated and everything should be right. Do we have some connections problems or a specific bug to some nodes ?



\- Force fly mode in sanctum (3D skill tree dimension/realm) whatever happens, ideally player spawn already flying, an't take off flying and ther is no block under him to support him.



\- Indicate more clearly to the player when he tries to take a node but don't have the correct number of skillpoints needed for it. It sends a generic message currently. "Cannot allocate this node" where we have dynamic messages everywhere else. LEt's also make other messages perfect if not are not correct like this one or need an upgrade compared to current others, don't change the commands tho.



\- Sandswept Golem needs to be a FUCKING BOSSS - All other golems too if there is other ones ?



\- When player goes into the skilltree from another world than the overworld, like from forgotten temple which is a vanilla dimension where you deposit memories (but we have to take into account possible modded dimensions too and be compatible with any of them) the game is never able to make them exit the skill tree dimension we create.



\- Nodes in 3d skilltree have their code name when attributed or unattributed in the chat and probably in othe places instead of their name



We still have a problem regarding the vanilla hytale realm/dimension timers. They're active in the realm and show a UI top-right that we've been trying to desactivate and make our own timer work in maps (and no timer at all in the 3D skill realm/sanctum) but so far no luck. Could we look at how we create them, and them look into the source code and API and try to find what's causing them, why, and offer me with the best choices we have explained so I can fully understand and take a decision.



\- Damage from projectiles doesn't work like arrows from NPCs don't damage the players for example, or spells etc.., could be the other way around too, do we even have coded the dmg calculations for projectiles ?



\- When a player enters or exits a world, like the overworld or the skill sanctum, he has a text that shows in chat about the first item he has in his inventory or something similar ? Not the first actually, we tried moving items around but for my friend it's always about a mountains map he has in his inventory for some reason ?



\- Each node has a description field in it's UI in the 3D skilltree and also has them in this same UI the detail that's similar (almost always) to the desc we'll call it desc 2. I would like to delete desc 1 from the UI properly and fully so we resize UIs correctly, and then verify the node desc 2 is accurate to the actual code effect we created. Once we have all that, we can fully plan on fixing all this. Or am I misunderstanding and the nodes like the middle ones and big have both the effect of what I call desc 1 and the synergy desc 2 is another more effect ?



I need you because before going to bed I would like to put you to work regarding the biggest enigma in our plugin : the stats

We've been at work on it for multiple weeks, and have created interlacing systems regarding stats like gear, weapons, player, attributes, skilltree, and probably other things I'm forgetting about.

What I would like is for you to create a robust and detailed mapping of every single stat, we don't need detail we need data.

For example, where does the stat originate from, like is it coming from stats created in gear, skill tre, what's it's code origin if it makes sense and we multiple ones, where is it wired, like will it work perfectly in gear, in skilltree, everywhere possible, combat calculations especially.

And we want to do this for AND players AND hostile mobs.

For this, because it's quite confusing and not good how I wrote it, could you try to understand what I mean and create the best prompt for you so you'll thoroughly do all this and input it all in a .md file once I /clear you and give you the prompt you'll ive me to copy paste ?

The goal is to use all this to : Debug every single stat / Choose the stats we wanna keep, remove, and if we wanna add any / Make sure of where exactly we need to wire or remove everything for a stat to perfectly work during it's first implementation without mistake / understand what's partially wired and needs to be done / Understand what can map on which gear, and where like is it a prefix a suffix, so we can move everything to our desire to balance everything out.



\- Mobs shouldn't spawn on the starting 20 blocks from spawn point in realm/maps



\- Boss mobs should be the specific mobs we input in the ID, but ELITE mobs should be any random hostile mob (including what we currently input as an elite mob, which should just be standard hostile mobs, or boss mobs, idk I have no clue) and it should just be dictated via a stat like Elite % Chance or something if it doesn't already exist, that allows any hostile mob when spawned to spawn as an elite mob. Could be fun if bosses were also allowed to spawn as elite bosses if it's a boss and it rolls the elite chance modifier correctly.



\- If in item tooltips any number goes at 100% or above, we need to stop showing decimals



\- Each node has a description field in it's ui in the 3D skilltree and also has them in this same UI the detail that's similar (almost always) to the desc we'll call it desc 2. I would like to delete desc 1 from the UI properly and fully so we resize UIs correctly, and then verify the node desc 2 is accurate to the actual code effect we created. Once we have all that, we can fully plan on fixing all this. Or am I misunderstanding and the nodes like the middle ones and big have both the effect of what I call desc 1 and the synergy desc 2 is another more effect ?



\- Make sure everything is infinite or at least 1 000 000 as max especially regarding levels in every single config and command we created



\- A lot of stats like Fire/Void Res % and such need to have a % indicator just after the number like "+5% Fire Resistance" instead of "+5 Fire Resistance" in the 3D skill tree 2D HUD and other places, like in stats UI maybe idk, but I know there is a lot of characters missing or typo mistakes in multiple places ?



\- We have a problem regarding the skilltree command and the way our code currently check if the player is in the skilltree realm or not. Ideally, when the command is used, we should actively look in the current code from the API or something where the player is in which dimension and use it to know if we have to TP him to skill tree Realm or back to where he was. It may be either a functionality not coded as intended to work, or a bug, but the detection doesn't work a intended currently.



\- Regression on the skill tree since 10/02 in thje morning/afternoon where ropes in the skill tree don't make one long ropes anymore but have holes in them.



\- void larvae and others are considered non-agressive mobs even tho they are, but they shouldn't give full XP, we should have a new category for specific mobs that should give exactly half (configurable) of normal hostile mobs, those like this that have a really small model but are hostile. Foxes too for exemple ?



\- All hostile mobs can be elite mobs doesn't look like it's fixed especially in the overworld even tho we tried to fix it in the past commits. Some specific mobs are still the ones being elite from our server. Is it because of cached files in the server or is it a code error, problem, or was the intent when we coded it weong or something ?



---> Deployed



\- Quitter le skill tree via le bouton dans /stat ne ferme pas le HUD du skilltree quand on clique un node pour avoir ses détails



\- Signature energy when using weapons has it's bar filling but then it go back to 0 so players can't use signature attacks



\- When going into a realm like the memory realm from the vanilla game and going into the skill tree it works well, but then going from the skilltree back instead sends you into the main overworld, at seemingly random coordinates in the air and drops you to death



\- You shouldn't be able to unallocate the origin node at all never ever. You need to make it so it can't be unallocated never ever.



In the same way we created effects for when players allocate nodes in the skill tree realm, I would like to make it so when a player finishes a map/realm and completes it, it's a victory, then, we drop at least 1 map (+1 to -1 level compared to map just completed) and also a random piece of gear (-3 to +0 from current map level) and a random stone, with all the rarity calculations like depending on player stats, level and everything else like our other systems.



An error appeared in a log of a previous deploy (don't deploy anything to testserver please) just look if the problem is still in our code, and let's perfectly fix it if so, so everything works correctly and like intended and doesn't bug. We want to find the proper correct way to do so.

I don't know if it's related but my friend started to have a huge amount of movespeed for no reason around this timing.

There is also a chance (we're not sure tho) that all this happened around the time my friend came back from the forgotten temple (another realm/dimension from the vanilla game)



\- Items sometimes, but really rarely, have their name be their id like "Lv44 Gear 1770670328298 0 of the Salamander", but we couldn't find why or how or when. let's properly fix this so everything works correctly like originally intended.



\- Horse\_Skeleton and maybe other horses need to be put under the 0.1XP Multiplier category of mobs, they currently have a 1 multiplier in hostile category. Also Wraiths give 0.1 XP Mult instead of giving 1, we need to fix that too.



\- Chaos resistance (and probably other resistances too) don't look like they work at all, and they aren't taken into consideration during calculations. At least, in doesn't show in the death recap, verify everything thoroughly, and if there is a problem, let's correctly and thoroughly fix it so it works as originally intended.



\- My friend won a map he died in, and the realm didn't complete, it was frozen in time, the portal didn't spawn, nothing happened and the log said :

As complementary information, the player shouldn't respawn in the map if he dies in. He should be TPed back to where he took the portal from (the portal should still be active until completion, failure, timer at 0, or other stuff.)



\- We have a problem currently where the vanilla timer from the custom realms we spawn is still active and kicks the player after the timer runs out, while also still showing it's HUD with it's timer. We want to fully deactivate in one way or another this vanilla functionality. Find how to do it correctly, and let's make it proper.



\- Debug tool request from my best friend :

tu peux me créer une commande pour que j'ai le combat log dans le chat ?

Genre un toggle on/off qui m'affiche les calculs comme le death recap pour les hits que je donne et que je prends.

Moi ça me permettra de debug plus efficacement les stats et interactions et pour les joueurs ça leur permettra  d'optiu leur builds

Can we look at how we do correctly nicely formatted message and make one for him that includes everything please ? Also let's try and make it the smallest in size possible, the full chat is not huge.



\- Genesis stone reads as "Adds 1-2 modifiers to an item with none" when it should work like "Adds 1-2 modifiers to an item that doesn't already have the max number of modifiers" or something similar. Do we have to take into account if the player has only 1 space and it rolls 2 as a particular case ?



\- When items are dropped on the ground, they show a halo of light depending on their current rarity. But currently, the color that's chosen is for the vanilla rarity of the item. We need to make it show the color of the custom rarity we apply on our modded items like gear, maps, stones.



\- It looks like Health recovery doesn't affect health regen even tho I feel like it should. Could we correctly make it apply to everything logical ?



\- Lava in the base game does a fix amount of damage. It should do damage based on Max HP instead.



\- I want to add a stats button in the vanilla inventory UI under the left panel that would allow players to open UIs without typing a command, but I know it could be quite tricky. Explore everything you can from the source code and server API and everything possible, and tell me if it is possible, what are the multiple ways we could do it if possible, and what are their pros and cons, and what's the most correct way to create it. don't modify anything for now, and ask me the method I want before creating any plan.



\- The detailed combat log is currently horrendous, shows either full green or full red text, is unreadable, uses non-accepted characters, isn't detailed at all, doesn't show the health modifications of anything, misses details, doesn't take resistances in the calculation even tho it should compared to death recap. It should be even more complete than our death recap.



I found earlier with your help a big problem regarding our plugin I think, could you look into it please ?

  You likely need a cleanup mechanism that periodically purges items where the base item no longer

  exists, rather than just skipping them on load



**--> Deployed**



\- Armor/Gear still apply it's stats contrary to weapons when it's equiupped but the player doesn't have the required level. Gear stats shouldn't apply at all if the player doesn't have the level required, you should really look into how we did it for weapon and copy or reuse it, the best possible



\- The detailed combat log is missing a lot of information regarding what it tells the player. No resistance show, no pen show, and they should even if they're at 0%, same for everything else, like damage multipliers, more damage for everything, and all the defences included evasion and stuff. Ideally show the full calculation for everything so we can debug way easier.

Actually since the last deploy, it looks like only physical damage is showing for some reason instead of the huge detailed log ?



\- Crit doesn't apply to elemental damage even tho it should apply to it the same way as physical does



Looted items from chest have their vanilla stats and tooltips instead of being transformed into our modded items with full stats depending on the player that opened it and his level etc... Could we make it so if a player opens a container any v /actually we need to create the full loot system from start to finish I think, 2 different systems 1 to stop vanilla mobs from looting weapons and gear like they do in the vanilla game, and 1 to control all the loot chests of the game. Ideally yeah we should remove vanilla gear and weapons, and add modded gear and weapons, maps, and stones.



\- Elemental damages should be modified by attack type multiplier, they're currently not. We need to show it in detailed log



* XP needed for player per level should go from 2.5 to 2.2
* Divide mob's damage stat attribution by 2 in their scale and multiply HP by 2, mobs are too frail and do too much dmg at high level

Reduce the exponential scale of mobs, players, gear and weapon stats, by a lot. Currently numbers go from like 20% at level 20 to 5000% at level 100, we need a way less aggressive curve.



-> deployed



The rope/chain calculations currently applied to "accessible nodes" in the 3D skill tree should be the ones applied to "unaccessible ones" because they're good but have holes, and we need to modify the one applied to accessible ones slightly so there will be no holes between every rope asset.



Vanilla Timer UI doesn't show anymore in the skilltree (which is good) (need to check maps) but the timer is still active in the background and sends back players to the overworld when time is up, we don't want any vanilla timer to be active at all, the UI not showing is perfect.



Realms didn't work fixed



Player movement speed is not correctly changed when AT LEAST player unallocates attributes, probably in other places too



\- Change armor value to show correctly instead of the vanilla armor in the inventory UI of the player and other places needed. Also, if it's possible to show the player amor% of our mod instead of the vanilla one in the inventory of the player, can we trace it or something to know when it shows so we can know when the player opens the inventory ? There is currently no way to know when the player opens the inventory.



We correctly visually deleted the vanilla timer in the 3D skill tree realm, but the code timer is still active and kicks the player after some time even tho the HUD doesn't show anymore. Also, in the Maps, somewhat related, the Timer HUD is still appearing. Regarding how the timer reacts in maps I have no clue as map timers are not long enough for me to be kicked out by the timer. We've went back and forth on this system multiple times, please dig up thoroughly every single piece of info you can from the decompiled code, and our current way of doing it, and find the exact best way to make it so Timer HUD never appears and the vanilla map timer is never active/working. We also have multiple commits about this, let's be thorough. I want a real final fix.



* Could you look at the entirety of our plugin commands please ? From what I understand, we have doublon gear commands, some commands are still capped to 100 levels instead of 1 000 000, and there is probably other things missing or in doublon too. Verify everything, look at the proper and correct way of making commands like we did some, and let's make our commands clear, comprehensible, easy, straightforward, without doublon or anything problematic.




We should turn off the spawn protection like 200 block radius by default and all mobs start level1 from the spawn. less confusing for starting players.



Early game is way too hard, mobs hits way too hard etc..., is there some vanilla stats still adding to the mobs spawned, or do we have too high stats early game for players and we should tweak all this ? for now just research everything, don't modify anything, let's see and understand all our options and what's currently happening.



--> Deployed



We need to change Maps Mobs, some of them are fish and don't attack, some are just weird, we need to make a pass on this. Ideally we have 3 mobs per map, in the same style or spirit, even better would be idk like 1 tank 1 melee dps 1 long range dps for mobs or sort it in a way where every map has interesting mobs/mechanics/fights and they're diverse. Ideally no 2 maps with the same mob if possible. If the bos thing is implemented let's find a boss for each of them, if not just 3 hostiles will be fine for now.



Spawn an ancient gateway near spawn for players to use early game



Portals don't close correctly when disappearing from the overworld and don't allow players to put a new map in it, it says "The portal is already active, wait for it to close."



Cap the maps spawn so they're only small until level 10, and the higher level you are the higher chance you have to find a higher size map ? To avoid low level players to have to fight against 70 mobs instantly ?



Rex caves spawn in maps, it's a boss monster normally, a Giant TRex



Sometimes, the ancient gateways we make appear near the world spawn, are on top of trees, bushes, leaves, and other blocks. They should "skip" those blocks, or we should find the correct way to make it so they spawn on the ground, and bypass all weird quirks like this one. Properly.



UIs weren't updated at all for the new elemental things we added and stats we refactored. Let's start by the stats UI, where I'ml not sure we updated correctly and clearly the attr descrptions with the new stats, and because we have a 6th stat now the UI window size wasn't accounted for it so it's too small now.



Stones are not dropping from mobs at least, maybe more, compared to gear and maps which work correctly. We need them to correctly work and drop from mobs, also show in chests, and everywhere else needed - maybe compare to gear and maps how code is done, this will probably help.



Verify map rewards -> Put in a shared chest or one chest per player or 1 chest for all but different items in it somehow ? -> Looking into mim mod here for sure



Refactor the Stone HUD when using a stone to be made with HyUI(???), and like other HUDs to be made exactly with the hytale default style exactly.



Birds in mountain realm, do we have a problem with how mobs spawn ? You should have avoided to put birds in maps when placing mobs, do we have a deeper problem somewhere that causes this ?



\- Experience HUD bar



We need to create a ping in the custom map/realms we make on the portal so that players can come back to the portal even in gigantic maps easily. In the overworld Vanilla hytale there is a constant ping called "Spawn" on the map and it shows on a directional HUD on top of player screen too, could we put one on portal in realms for players except skill sanctum realm of course ? Ideally called Portal, Idk if we can modify that and like steal the icon of something else than spawn ? It should behave codewise the same way as spawn ping but ideally have a different name and icon.



We have been having a problem for multiple days now where it doesn't look like mobs in the overworld and in the realms/maps can drop stones like at all ? They spawn correctly in our chest when a realm is completed, but there clearly is a problem regarding mobs not dropping them ? Like is the drop rate at 0.1% or do we have a deeper more complex problem about stones ? Everything else looks like it works correctly, it's only stones only from mobs ?



When a player hits a mob with weapons, his synergy bar goes slightly backward and removes some charge instead of adding it like in the vanilla game. We already tried a fix for a similar bug before, we need to properly fix this.



\- Show a HUD in the skilltree to show how much node points were allocated already vs how much you currently have. Ideally it should be a really small in height HUD that still has everything from the vanilla game regarding containers, backgrounds etc... for the main container, with colored text updating dynamically something along the lines of : "Unallocated : # | Allocated : # | Total : #"



Crash when trying to open skill nodes since our big refactor of UIs : \[2026/02/15 07:49:51   INFO]                         \[Hytale] c333cb6b-627b-4343-bfd3-6c6abf8ee98b - Larsonix at QuicConnectionAddress{connId=58e35bc316c85ccfed71c0dc7c57a593092c4b35} (/127.255.58.115:63288, streamId=0) left with reason: Crash - Selected element in CustomUI command was not found. Selector: #HYUUIDGroup1053.Anchor



The HUD for the player information regarding skill points needs to be like 50pxs from the right screen and 20pxs more than it currently is from the bottom of the screen, and the Hud needs to be made bigger like higher more vertical, because the text for allocated/unallocated etc... is not in the box currently but under it, because the box is too short.



When hitting multiple nodes, I am sometimes able to crash the client and make it disconnect, but not always. I am unsure about everything, it may have something to do with in which order I hit them or... Maybe it's the speed at which it does so, or the proximity I have with the nodes, but I think it's only when I hit multiple, even tho this could be wrong too... ?
Yeah I got another one like #HYUUIDGroup22.Anchor when hitting 3 with the same swing I think that's it ?
Here's a more compelte one from the same problem :

\[2026/02/15 08:12:22   INFO]           \[SkillSanctumInstance] Nameplate tick: 229 nodes, 15 close, 0 subtitles updated, player at (-3, 68, -10)

\[2026/02/15 08:12:22   INFO]           \[SkillSanctumInstance] Sent 229 interaction hints to player c333cb6b-627b-4343-bfd3-6c6abf8ee98b

\[2026/02/15 08:12:22   INFO]                \[RPGDamageSystem] Opened skill node detail HUD for player=c333cb6b, node=nature\_vitality\_branch

\[2026/02/15 08:12:23   INFO]           \[SkillSanctumInstance] Nameplate tick: 229 nodes, 14 close, 0 subtitles updated, player at (-2, 68, -10)

\[2026/02/15 08:12:23   INFO]                \[RPGDamageSystem] Opened skill node detail HUD for player=c333cb6b, node=nature\_vitality\_2

\[2026/02/15 08:12:23   INFO]                \[RPGDamageSystem] Opened skill node detail HUD for player=c333cb6b, node=nature\_vitality\_branch

\[2026/02/15 08:12:23   INFO]                         \[Hytale] c333cb6b-627b-4343-bfd3-6c6abf8ee98b - Larsonix at QuicConnectionAddress{connId=29e4c2a06b549710cff137c79765eae3264fff9d} (/127.255.58.115:22375, streamId=0) left with reason: Crash - Selected element in CustomUI command was not found. Selector: #HYUUIDGroup96.Anchor

\[2026/02/15 08:12:23   INFO]                         \[Hytale] {Playing(null (null, streamId=0)), c333cb6b-627b-4343-bfd3-6c6abf8ee98b, Larsonix} was closed.

\[2026/02/15 08:12:23   INFO]                     \[Universe|P] Removing player 'Larsonix' (c333cb6b-627b-4343-bfd3-6c6abf8ee98b)





On player connection, if the player has equipment on his current use slot, the stats are not taken into account (eg: if player holds a RPG modified sword in hand on connect no stats from it apply until he scrolls out of it and back to it). The problem could also be in other places I didn't detect.



Stone UI/code when used is not showing/taking into account equipped armor. It needs to be able to modify it and all stat calculations should update correctly accordingly.



Let's completely stop showing "Lv#" in front of every single item name since we're now fully including them in the item tooltip/description



For all UIs :
Before and after every single ":" I want you to add a space to make everything breath. There is a lot of UIs and HUDs to apply this to, let's be methodical and thorough.





\- We need to remove the vanilla drops of monsters regarding gear drops in one way or another, the best and most proper one, because some mobs still have their vanilla loot pool for gear/weapons and give an insane amount of loot to the player. We have our own system that handles gear \& weapons drop that's currently working.





Currently stones use our system with custom ID for each item but we only have a limited constant number of them with not needed dynamically updating tooltips because it's always the same, could we find a more proper way to integrate them into the game ?



The scaling up to level 100 looks really good, but then, numbers become completely stupid. I would like something that inverts the scaling starting at level 100 so that it's the opposite of exponential and it becomes harder and harder to even get 1%. Would that be an easy fix in our plugin ? Would we be able to make a new config for it or something ? Also, because this is scaled in the opposite direction, it somehow needs to apply the same for mobs too.



--> **DEPLOYED**



The stone Fortune's compass doesn't show any map/realm from the inventory or anywhere else to apply it's effect to in it's UI ?



Divide dropped maps by 2 in realms, there is too many that drop currently we need to reduce them.



Items crafted in benches dont get mods, only the ones crafted from the inventory get them, we need to find how and make it correct in our existing implementation.



We probably have a problem regarding when we apply mods to crafted weapons and gear. Basically right now, the first item you craft doesn't get mods applied (I think but I may be wrong it's because we apply it while it's not fully in the inventory or something idk) but the second one you craft make the first one in your inventory transform into a modded one, and the one you receive from the 2nd craft is not modded, and on and on...







