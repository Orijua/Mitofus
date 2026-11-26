# Changelog

All notable changes to this mod are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the versions follow [Semantic Versioning](https://semver.org/) (while the version starts with 0, anything can change between releases).

## [Unreleased]

### Added
- **Own item art, batch 1:** draft textures for the three VR helmets (now flat items, like the basic one), the three smithing hammers, the Commander's and Ruler's swords, the wolfram ingot, raw azurite, the four enhancement stones and the three enhancement scrolls. Items of a family share a silhouette and change palette. They are drafts to be repainted by hand. Redrawn with real 32x32 detail: hammers with a proper head and collar, swords with no gaps in the blade.
- **Talent trees:** each class earns 1 talent point per class level and spends it in its own tree (stat points, better skills and passives, new skills), shown in a new Talents tab. The swordsman has the first tree. A new Talent Scroll (paper, lapis lazuli and amethyst shard) refunds talents; the respec scroll keeps refunding only stat points. Mana cost and cooldown in the Skills tab and HUD include the talents.
- **Classes as branches:** the six classes are now branches. The three slots (levels 1, 20 and 50) hold three different branches in any order, and the player's class is the combination of branches they hold, with its own name (six are written by hand, the rest are named after their branches). Each branch keeps its own level, talents, grade and forms. At the village chief, "Change branch" puts another branch in a slot: the first change of each slot is free, the next ones cost 1 gold coin (set per branch in data), and the branch that leaves sleeps with all its progress and comes back as it was. This replaces manual fusions, which are gone together with the hybrid classes and inherited forms.
- **Status synergies between branches:** every branch has a passive that applies a status with a 20 % chance on hit and one that deals 15 % more damage to a target with another status (swordsman: bleed and slowed; spearman: slow and weakened; squire: weaken and bleeding; mage: burn and cursed; archer: shock and burning; necromancer: curse and shocked). Any branch that applies a status therefore works with whoever takes advantage of it. Chances and bonuses are in the passive JSON files.
- **Class and job synergies:** holding a class and a job together gives small stat bonuses in three steps (class grade and job level). Five class + job pairs have their own and every job has a default. The Profile tooltips show them before choosing.
- **Catch-up XP:** a class far below the player's level gets double class XP (set per class in data).

## [0.1.0-beta.1] - first beta

First version of Mitofus, with its own design.

### Added
- **VR helmet and the world of Mitofus:** a helmet you use lying down to connect to Mitofus, a dimension with its own character and inventory, safe logout, fatigue and three helmet tiers.
- **Classes and jobs:** class slots at levels 1, 20 and 50, jobs on a separate track, and grades every 100 class levels.
- **Coin purse:** four coin steps (copper, iron, gold, diamond; 1 = 100), a terminal and a phone.
- **Blacksmithing:** smelting and forging minigames, rated equipment with options, appraisal with the Appraiser Eye, and apprentice blacksmiths.
- **Covadonga and Eibar:** the starting village, its quests and the smithy contest.
- **Leveled mobs and elites** with rated loot.
- **Optional JEI and Jade support.**
