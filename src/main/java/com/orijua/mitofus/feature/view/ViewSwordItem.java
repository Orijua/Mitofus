package com.orijua.mitofus.feature.view;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.quest.QuestDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A sword that sharpens its bearer's eye. The
 * speeds, the View bonuses and "only for recognised lords" are own design. Held in the main hand, observing starts from {@code observeTicks} instead of ten seconds (and View
 * shortens it further, see {@link ViewRules#observeTicks}), and NPC windows show
 * as much as {@code viewBonus} more View would. With Talent Search, using it reveals the talents around.
 */
public class ViewSwordItem extends SwordItem {
    private final int observeTicks;
    private final int viewBonus;
    private final boolean talentSearch;

    public ViewSwordItem(Tier tier, int observeTicks, int viewBonus, boolean talentSearch, Properties properties) {
        super(tier, properties);
        this.observeTicks = observeTicks;
        this.viewBonus = viewBonus;
        this.talentSearch = talentSearch;
    }

    public int observeTicks() {
        return observeTicks;
    }

    /** How much faster it makes observing than bare-handed, in percent: the same at any View (both shrink alike) until the 2 s floor. */
    public int observeSpeedPercent() {
        return (int) Math.round((1 - observeTicks / (double) ViewRules.OBSERVE_TICKS) * 100);
    }

    public int viewBonus() {
        return viewBonus;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack sword = player.getItemInHand(hand);
        if (!talentSearch || hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(sword);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            TalentSearch.search(serverPlayer, sword);
            player.getCooldowns().addCooldown(this, TalentSearch.COOLDOWN_TICKS);
        }
        return InteractionResultHolder.sidedSuccess(sword, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mitofus.view_sword.observation", observeSpeedPercent(), viewBonus)
                .withStyle(ChatFormatting.AQUA));
        if (talentSearch) {
            tooltip.add(Component.translatable("item.mitofus.view_sword.talent_search").withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        ResourceLocation loan = stack.get(ModDataComponents.LOANED_FOR);
        if (loan != null && context.registries() != null) {
            Component quest = Definitions.get(context.registries(), ModDatapackRegistries.QUEST, loan).map(QuestDefinition::name)
                    .orElse(Component.literal(loan.toString()));
            tooltip.add(Component.translatable("item.mitofus.loaned", quest).withStyle(ChatFormatting.GOLD));
        }
    }
}
