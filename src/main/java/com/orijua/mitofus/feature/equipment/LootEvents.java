package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.EnhanceBalance;
import com.orijua.mitofus.core.config.RuleBalance;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.mob.BoneLord;
import com.orijua.mitofus.feature.mob.MobLevelService;
import com.orijua.mitofus.feature.progression.ProgressionEvents;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Elites killed by a player sometimes drop a rated piece of equipment. Its material and quality depend on the elite's
 * level, see {@link EliteDropRules}: any material can come out, the best ones are rarer, and the higher the level the
 * harder it is to get something bad.
 */
public final class LootEvents {
    /** The pieces an elite can drop (filled in datagen: wooden to netherite swords, axes and armor). Their material comes from the item id. */
    public static final TagKey<Item> ELITE_DROPS = ItemTags.create(Mitofus.id("elite_drops"));
    private LootEvents() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(LootEvents::onLivingDrops);
        gameBus.addListener(LootEvents::onBossDrops);
    }

    /** The Bone Lord, killed by a player in Mitofus, may drop an ancient enhancement scroll. */
    private static void onBossDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof BoneLord boneLord) || ProgressionEvents.creditedKiller(boneLord, event.getSource()) == null
                || !MitofusWorld.is(boneLord.level())) {
            return;
        }
        ancientScrollDrop(boneLord.getRandom(), EnhanceBalance.current().boneLordAncientScrollChance()).ifPresent(stack ->
                event.getDrops().add(new ItemEntity(boneLord.level(), boneLord.getX(), boneLord.getY(), boneLord.getZ(), stack)));
    }

    /** With the given chance, an ancient scroll for a weapon or for armor (half each); otherwise empty. */
    public static Optional<ItemStack> ancientScrollDrop(RandomSource random, double chance) {
        if (random.nextDouble() >= chance) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(random.nextBoolean()
                ? ModItems.ANCIENT_WEAPON_ENHANCEMENT_SCROLL.get() : ModItems.ANCIENT_ARMOR_ENHANCEMENT_SCROLL.get()));
    }

    private static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || ProgressionEvents.creditedKiller(mob, event.getSource()) == null
                || !MitofusWorld.is(mob.level()) || !MobLevelService.get(mob).elite()) {
            return;
        }
        RandomSource random = mob.getRandom();
        RuleBalance rules = RuleBalance.current();
        double chance = EliteDropRules.chance(Balance.current().eliteEquipmentChance(), rules.eliteDropMinChance(),
                rules.eliteDropDecay(), MobLevelService.get(mob).level());
        if (random.nextDouble() >= chance) {
            return;
        }
        eliteDrop((ServerLevel) mob.level(), random, MobLevelService.get(mob).level()).ifPresent(stack ->
                event.getDrops().add(new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(), stack)));
    }

    /**
     * A piece from {@link #ELITE_DROPS} for an elite of {@code mobLevel}: the material is drawn by the level's weights
     * ({@link EliteDropRules#pickMaterial}), the piece within it at random, and the rating rolls with the level as luck
     * and the level's minimum. Empty if the tag has no usable items.
     */
    public static Optional<ItemStack> eliteDrop(ServerLevel level, RandomSource random, int mobLevel) {
        Map<EliteDropRules.Material, List<Holder<Item>>> byMaterial = new EnumMap<>(EliteDropRules.Material.class);
        for (Holder<Item> piece : BuiltInRegistries.ITEM.getTag(ELITE_DROPS).map(tag -> tag.stream().toList()).orElse(List.of())) {
            EliteDropRules.Material.ofItemPath(BuiltInRegistries.ITEM.getKey(piece.value()).getPath())
                    .ifPresent(material -> byMaterial.computeIfAbsent(material, key -> new ArrayList<>()).add(piece));
        }
        Optional<EliteDropRules.Material> material = EliteDropRules.pickMaterial(mobLevel, byMaterial.keySet(), random.nextDouble());
        if (material.isEmpty()) {
            return Optional.empty();
        }
        List<Holder<Item>> pieces = byMaterial.get(material.get());
        ItemStack stack = new ItemStack(pieces.get(random.nextInt(pieces.size())));
        EquipmentService.rateLoot(level, stack, EliteDropRules.luck(mobLevel), EliteDropRules.minimumRating(mobLevel), random);
        return Optional.of(stack);
    }
}
