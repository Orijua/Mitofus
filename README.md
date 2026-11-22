# Mitofus

A role-playing world inside Minecraft (NeoForge 1.21.1). Put on a VR helmet, connect to **Mitofus**, a world of its own, and build a character there with classes, professions, quests, levels and an economy of coins. The real world stays vanilla.

**Status: beta (0.x).** Saves from earlier versions are not guaranteed to load in later ones.

## What it is

The overworld is the real world. With a VR helmet you connect to Mitofus, which is its own dimension with a separate character and inventory.

- **VR helmet and two worlds:** use the helmet, lie down, play in Mitofus and log out safely. Three helmet tiers.
- **Fatigue:** energy that drains while you are connected and recovers in the real world.
- **Levels, stats, titles and skills** with their own profile, quest and journal screens.
- **Classes and professions:** class slots that unlock with your level, and jobs on their own track.
- **Blacksmithing:** smelting and forging minigames, hammers, new metals (wolfram, mithril, blue azurite) and equipment with ratings from Normal to Ancestral and rolled options.
- **Covadonga and Eibar:** the starting village and its quests, the smithy and the production contest.
- **Economy:** a coin purse with four steps (copper, iron, gold, diamond), a terminal and phone, trading and apprentice blacksmiths.
- **Leveled mobs and elites** with rated loot.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1 or newer

## Installation

1. Download the mod jar from the releases page of this repository (or build it with `./gradlew build`; the jar is in `build/libs`).
2. Put it in the `mods` folder of a NeoForge 1.21.1 instance.
3. Start a **new world**: the Mitofus dimension and its places are only generated in new worlds.

## Building and testing

- `./gradlew build`: compiles and runs the unit tests.
- `./gradlew runGameTestServer`: runs the game tests on a headless server.
- `./gradlew runClient`: starts the game with the mod.

## AI usage notice

This project is developed with the assistance of AI tools (code, tests, documentation, design ideas and some textures). The author directs, reviews and is responsible for what is published.

## License

Unless otherwise stated, the original source code and original assets in this repository are licensed under the **Mozilla Public License 2.0 (MPL-2.0)**. See [LICENSE](LICENSE.md) for the full text. Third-party software and assets remain subject to their respective licenses. Not an official Minecraft product; not approved by or associated with Mojang or Microsoft.

## Credits

- Project author: Orijua
