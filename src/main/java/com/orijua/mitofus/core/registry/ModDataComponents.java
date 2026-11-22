package com.orijua.mitofus.core.registry;

import com.mojang.serialization.Codec;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.equipment.Appraisal;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import com.orijua.mitofus.feature.equipment.EquipmentRules;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.UUID;

/** Typed data stored on an ItemStack: saved with the item ("persistent") and sent to clients ("networkSynchronized"). */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Mitofus.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemRating>> ITEM_RATING =
            DATA_COMPONENTS.registerComponentType("item_rating", builder -> builder
                    .persistent(ItemRating.CODEC)
                    .networkSynchronized(ItemRating.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<RolledOption>>> ITEM_OPTIONS =
            DATA_COMPONENTS.registerComponentType("item_options", builder -> builder
                    .persistent(RolledOption.LIST_CODEC)
                    .networkSynchronized(RolledOption.LIST_STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EquipRequirements>> REQUIREMENTS =
            DATA_COMPONENTS.registerComponentType("requirements", builder -> builder
                    .persistent(EquipRequirements.CODEC)
                    .networkSynchronized(EquipRequirements.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENHANCEMENT =
            DATA_COMPONENTS.registerComponentType("enhancement", builder -> builder
                    .persistent(Codec.intRange(0, EquipmentRules.MAX_ENHANCEMENT))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** On a piece not yet appraised in full: the seed and the data revealed so far. No component means appraised. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Appraisal>> APPRAISAL =
            DATA_COMPONENTS.registerComponentType("appraisal", builder -> builder
                    .persistent(Appraisal.CODEC)
                    .networkSynchronized(Appraisal.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> SKILL_BOOK =
            DATA_COMPONENTS.registerComponentType("skill_book", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    /** On an item a quest giver lends: the quest it is lent for; it is taken back when that quest ends. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> LOANED_FOR =
            DATA_COMPONENTS.registerComponentType("loaned_for", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    /** Legacy, no longer written: the found talents live on the player now (ModAttachments.TALENTS_FOUND). Kept so old stacks still load. */
    /** On a VR helmet that has been used: the player it is bound to. No component means nobody yet. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> VR_HELMET_OWNER =
            DATA_COMPONENTS.registerComponentType("vrhelmet_owner", builder -> builder
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<UUID>>> TALENTS_FOUND =
            DATA_COMPONENTS.registerComponentType("talents_found", builder -> builder
                    .persistent(UUIDUtil.CODEC.listOf())
                    .networkSynchronized(UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list())));

    private ModDataComponents() {
    }
}
