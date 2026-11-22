package com.orijua.mitofus.feature.world;

import com.orijua.mitofus.Mitofus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * What only works in Mitofus. The real world is any dimension that is not Mitofus ({@link MitofusWorld#is}); there the mod's
 * objects are inert (they can't be placed or used). The exceptions are the helmets (how you get to Mitofus) and the
 * account terminal and the phone (how you reach the account and the work from the real world).
 * The ores (wolfram, mithril, blue azurite) don't generate in the real world either ({@link MitofusOnlyPlacement}).
 */
public final class MitofusOnly {
    /** The action-bar message shown when something that only works in Mitofus is tried in the real world. */
    public static final String WARNING_KEY = "message.mitofus.mitofus_only";

    /** The items that work in the real world: the helmets (all tiers), the account terminal and the phone. */
    private static final Set<String> REAL_WORLD_ITEMS = Set.of("helmet", "advanced_helmet", "diamond_helmet",
            "account_terminal", "phone");
    /** The blocks that can be placed in the real world: the sleeping bodies (all tiers) and the account terminal. */
    private static final Set<String> REAL_WORLD_BLOCKS = Set.of("body", "advanced_body", "diamond_body", "account_terminal");

    private MitofusOnly() {
    }

    /** Whether the entity is where the restriction applies: anywhere that is not Mitofus. */
    public static boolean active(Entity entity) {
        return !MitofusWorld.is(entity.level());
    }

    /** An item of the mod that is inert in the real world: everything but the helmets, the terminal and the phone. */
    public static boolean isMitofusItem(ResourceLocation item) {
        return Mitofus.MODID.equals(item.getNamespace()) && !REAL_WORLD_ITEMS.contains(item.getPath());
    }

    /** A block of the mod that can't be placed in the real world: all of them but the sleeping bodies and the terminal. */
    public static boolean blocksPlacement(ResourceLocation block) {
        return Mitofus.MODID.equals(block.getNamespace()) && !REAL_WORLD_BLOCKS.contains(block.getPath());
    }

    /** An entity type of the mod: its right-click interaction is refused in the real world. */
    public static boolean isMitofusEntity(ResourceLocation type) {
        return Mitofus.MODID.equals(type.getNamespace());
    }

    /** Tells the player (action bar) that this only works in Mitofus. */
    public static void warn(Player player) {
        player.displayClientMessage(Component.translatable(WARNING_KEY), true);
    }
}
