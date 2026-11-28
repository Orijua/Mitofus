package com.orijua.mitofus.feature.character;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Everything of a character that is swapped between the two worlds, as a tag. It uses the vanilla serialization
 * (the same calls as {@code player.dat}), never a codec of our own: enchantments are registry holders in 1.21.1 and a
 * plain codec fails on them. The provider is always the player's registry access.
 *
 * <p>Not covered: slots that other mods add (e.g. Curios). They stay with the player in both worlds.
 */
public final class CharacterSnapshot {
    /**
     * The game's data version when the snapshot was taken. Nothing reads it yet, on purpose: it is there so that a
     * future change of format (or a Minecraft update that needs a data fixer) can tell an old snapshot from a new one.
     */
    private static final String VERSION = "data_version";
    private static final String INVENTORY = "inventory";
    private static final String ENDER_CHEST = "ender_chest";
    private static final String XP_LEVEL = "xp_level";
    private static final String XP_PROGRESS = "xp_progress";
    private static final String XP_TOTAL = "xp_total";
    private static final String HEALTH = "health";
    private static final String ABSORPTION = "absorption";
    private static final String EFFECTS = "effects";
    private static final String FOOD = "food";

    private CharacterSnapshot() {
    }

    /** The raw inventory list of a validated snapshot. Copy before changing it. */
    public static ListTag inventory(CompoundTag snapshot) {
        return snapshot.getList(INVENTORY, Tag.TAG_COMPOUND);
    }

    /** A copy of the snapshot with its inventory list replaced by {@code inventory}; everything else is the same. */
    public static CompoundTag withInventory(CompoundTag snapshot, ListTag inventory) {
        CompoundTag copy = snapshot.copy();
        copy.put(INVENTORY, inventory.copy());
        return copy;
    }

    /** Reads the player; changes nothing. */
    public static CompoundTag capture(ServerPlayer player) {
        HolderLookup.Provider provider = player.registryAccess();
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        tag.put(INVENTORY, player.getInventory().save(new ListTag()));
        tag.put(ENDER_CHEST, player.getEnderChestInventory().createTag(provider));
        tag.putInt(XP_LEVEL, player.experienceLevel);
        tag.putFloat(XP_PROGRESS, player.experienceProgress);
        tag.putInt(XP_TOTAL, player.totalExperience);
        tag.putFloat(HEALTH, player.getHealth());
        tag.putFloat(ABSORPTION, player.getAbsorptionAmount());
        ListTag effects = new ListTag();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.add(effect.save());
        }
        tag.put(EFFECTS, effects);
        CompoundTag food = new CompoundTag();
        player.getFoodData().addAdditionalSaveData(food);
        tag.put(FOOD, food);
        return tag;
    }

    /** Checks both stored inventories before a crossing or a terminal operation. */
    public static boolean isReadable(CompoundTag tag, HolderLookup.Provider provider) {
        return decodeInventories(tag, provider).isPresent();
    }

    record Inventories(List<ItemStack> inventory, List<ItemStack> enderChest) {
    }

    static Optional<Inventories> decodeInventories(CompoundTag tag, HolderLookup.Provider provider) {
        var ops = provider.createSerializationContext(NbtOps.INSTANCE);
        return decodeInventory(tag.get(INVENTORY), ops, Inventory.SLOT_OFFHAND + 1, CharacterSnapshot::playerSlot)
                .flatMap(inventory -> decodeInventory(tag.get(ENDER_CHEST), ops, 27, slot -> slot)
                        .map(enderChest -> new Inventories(inventory, enderChest)));
    }

    private static Optional<List<ItemStack>> decodeInventory(Tag raw, RegistryOps<Tag> ops, int size,
                                                             IntUnaryOperator slotIndex) {
        if (!(raw instanceof ListTag list)) {
            return Optional.empty();
        }
        List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
        for (Tag element : list) {
            if (!(element instanceof CompoundTag entry) || !entry.contains("Slot", Tag.TAG_BYTE)) {
                return Optional.empty();
            }
            int slot = slotIndex.applyAsInt(Byte.toUnsignedInt(entry.getByte("Slot")));
            if (slot < 0 || slot >= size || !stacks.get(slot).isEmpty()) {
                return Optional.empty();
            }
            ItemStack stack = decodeStack(entry, ops);
            if (stack.isEmpty()) {
                return Optional.empty();
            }
            stacks.set(slot, stack);
        }
        return Optional.of(stacks);
    }

    /** Maps the saved vanilla slot IDs to the indices used by Inventory#setItem. */
    private static int playerSlot(int slot) {
        return switch (slot) {
            case 100, 101, 102, 103 -> Inventory.INVENTORY_SIZE + slot - 100;
            case 150 -> Inventory.SLOT_OFFHAND;
            default -> slot < Inventory.INVENTORY_SIZE ? slot : -1;
        };
    }

    private static ItemStack decodeStack(CompoundTag entry, RegistryOps<Tag> ops) {
        if (!hasValidStackData(entry)) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.STRICT_CODEC.parse(ops, entry).result().orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            // A component codec may throw on malformed data instead of returning a failed DataResult.
            return ItemStack.EMPTY;
        }
    }

    private static boolean hasValidStackData(CompoundTag entry) {
        // Vanilla defaults missing counts to one. It also defaults failed decodes and can truncate numeric tags.
        Tag count = entry.get("count");
        if (count != null && !isWholeNumberInRange(count, 1, 99)) {
            return false;
        }
        // These vanilla components embed stacks using the same count codec. The container codec also overwrites
        // repeated slots. Check the raw values first, then leave registry resolution and component decoding to vanilla.
        if (entry.get("components") instanceof CompoundTag components) {
            for (String key : components.getAllKeys()) {
                boolean valid = switch (key) {
                    case "minecraft:container", "container" -> hasValidEmbeddedStacks(components.get(key), true);
                    case "minecraft:bundle_contents", "bundle_contents",
                         "minecraft:charged_projectiles", "charged_projectiles" -> hasValidEmbeddedStacks(components.get(key), false);
                    default -> true;
                };
                if (!valid) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasValidEmbeddedStacks(Tag raw, boolean slotted) {
        if (!(raw instanceof ListTag list)) {
            return false;
        }
        Set<Integer> slots = new HashSet<>();
        for (Tag element : list) {
            if (!(element instanceof CompoundTag entry)) {
                return false;
            }
            if (slotted) {
                if (!isWholeNumberInRange(entry.get("slot"), 0, 255) || !slots.add(entry.getInt("slot"))
                        || !(entry.get("item") instanceof CompoundTag item) || !hasValidStackData(item)) {
                    return false;
                }
            } else if (!hasValidStackData(entry)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWholeNumberInRange(Tag tag, int min, int max) {
        if (!(tag instanceof NumericTag number)) {
            return false;
        }
        double value = number.getAsDouble();
        return value >= min && value <= max && value == number.getAsInt();
    }

    /**
     * Replaces what the player carries (inventory, armor, offhand, Ender chest) and their vanilla XP, hunger and effects
     * by the snapshot's. A part the snapshot lacks (the character of a world never visited) is a fresh start: no XP,
     * full hunger, no effects. Health is NOT set here: it depends on the maximum health, which changes with the world,
     * so {@link #applyHealth} runs after the bonuses are recomputed.
     *
     * <p>Effects are frozen while stored: their remaining time does not run in the other world.
     */
    public static void apply(ServerPlayer player, CompoundTag tag) {
        Inventories decoded = decodeInventories(tag, player.registryAccess())
                .orElseThrow(() -> new IllegalArgumentException("Unreadable character inventories"));
        apply(player, tag, decoded);
    }

    static void apply(ServerPlayer player, CompoundTag tag, Inventories decoded) {
        for (int slot = 0; slot < decoded.inventory().size(); slot++) {
            player.getInventory().setItem(slot, decoded.inventory().get(slot));
        }
        for (int slot = 0; slot < decoded.enderChest().size(); slot++) {
            player.getEnderChestInventory().setItem(slot, decoded.enderChest().get(slot));
        }

        player.experienceLevel = tag.getInt(XP_LEVEL);
        player.experienceProgress = tag.getFloat(XP_PROGRESS);
        player.totalExperience = tag.getInt(XP_TOTAL);
        // Makes the server send the bar again at its next tick (the value it last sent may be the same).
        player.giveExperienceLevels(0);

        player.removeAllEffects();
        for (Tag saved : tag.getList(EFFECTS, Tag.TAG_COMPOUND)) {
            MobEffectInstance effect = MobEffectInstance.load((CompoundTag) saved);
            if (effect != null) {
                player.addEffect(effect);
            }
        }
        player.setAbsorptionAmount(tag.getFloat(ABSORPTION));
        player.setRemainingFireTicks(0);

        FoodData fresh = new FoodData();
        if (tag.contains(FOOD, Tag.TAG_COMPOUND)) {
            fresh.readAdditionalSaveData(tag.getCompound(FOOD));
        }
        player.getFoodData().setFoodLevel(fresh.getFoodLevel());
        player.getFoodData().setSaturation(fresh.getSaturationLevel());
        player.getFoodData().setExhaustion(fresh.getExhaustionLevel());
    }

    /** The saved health, capped at the maximum of the world the player is in now; a fresh character starts at full. */
    public static void applyHealth(ServerPlayer player, CompoundTag tag) {
        float max = player.getMaxHealth();
        player.setHealth(tag.contains(HEALTH, Tag.TAG_ANY_NUMERIC) ? Math.max(1.0f, Math.min(tag.getFloat(HEALTH), max)) : max);
    }

    /** A snapshot of a character with nothing: what the other world is before the player has ever been there. */
    public static CompoundTag empty() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        tag.put(INVENTORY, new ListTag());
        tag.put(ENDER_CHEST, new ListTag());
        return tag;
    }
}
