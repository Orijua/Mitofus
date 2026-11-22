package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.AppraisalBalance;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.feature.economy.TradeGoods;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.notification.NotificationEvents;
import com.orijua.mitofus.feature.notification.SystemNotification;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What a stack is while it is not appraised in full: the queries both sides use (what is revealed, which rating and options
 * count) and the changes the Eye makes. An unappraised piece works as a plain Normal one (base stats, Normal level, no
 * options) until the last datum is revealed. It reads the economy ({@link TradeGoods}) for the value datum, and the economy
 * reads this class for what a piece is sold as: the two packages depend on each other on purpose.
 */
public final class AppraisalService {
    /** What one use of the Eye did: how many data it revealed and whether the piece is now appraised in full. */
    public record Result(int revealed, boolean complete) {
    }

    private AppraisalService() {
    }

    // ---- Queries (both sides) ----

    /** Whether the piece is rated but not appraised in full. */
    public static boolean isUnappraised(ItemStack stack) {
        return stack.has(ModDataComponents.APPRAISAL) && stack.has(ModDataComponents.ITEM_RATING);
    }

    /** Whether the datum {@code key} is known: always for an appraised piece. */
    public static boolean isRevealed(ItemStack stack, String key) {
        if (!isUnappraised(stack)) {
            return true;
        }
        Appraisal appraisal = stack.get(ModDataComponents.APPRAISAL);
        return appraisal != null && appraisal.revealed().contains(key);
    }

    /** Whether selling the piece would give away a legendary or better one that is still not appraised in full. */
    public static boolean hidesPower(ItemStack stack) {
        ItemRating rating = stack.get(ModDataComponents.ITEM_RATING);
        return isUnappraised(stack) && rating != null && rating.ordinal() >= ItemRating.LEGENDARY.ordinal();
    }

    /** Whether the piece has a rating that is still hidden (unappraised and not revealed). A piece without a rating is never hidden. */
    public static boolean isRatingHidden(ItemStack stack) {
        return isUnappraised(stack) && !isRevealed(stack, AppraisalRules.RATING);
    }

    /** The rating the player may see: empty if the piece has none or it is still hidden. */
    public static Optional<ItemRating> visibleRating(ItemStack stack) {
        ItemRating rating = stack.get(ModDataComponents.ITEM_RATING);
        if (rating == null || (isUnappraised(stack) && !isRevealed(stack, AppraisalRules.RATING))) {
            return Optional.empty();
        }
        return Optional.of(rating);
    }

    /** The rating that counts for bonuses: Normal while unappraised, the real one otherwise (null if the piece has none). */
    @Nullable
    public static ItemRating activeRating(ItemStack stack) {
        return isUnappraised(stack) ? ItemRating.NORMAL : stack.get(ModDataComponents.ITEM_RATING);
    }

    /** The options that count: none while unappraised. */
    public static List<RolledOption> activeOptions(ItemStack stack) {
        return isUnappraised(stack) ? List.of() : stack.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.of());
    }

    /** Every datum of the piece, in tooltip order (see {@link AppraisalRules#keys}). */
    public static List<String> keys(ItemStack stack) {
        int options = stack.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of()).size();
        List<String> stats = EquipLevels.trueRequirements(stack)
                .map(requirements -> Arrays.stream(StatType.values())
                        .filter(requirements.stats()::containsKey)
                        .map(StatType::getSerializedName)
                        .toList())
                .orElse(List.of());
        return AppraisalRules.keys(options, stats, TradeGoods.refusal(stack).isEmpty());
    }

    // ---- Changes ----

    /** Marks a rated piece as unappraised with a seed from {@code random}; leaves anything else untouched. */
    public static void markUnappraised(ItemStack stack, RandomSource random) {
        if (!EquipmentService.isEquipment(stack) || !stack.has(ModDataComponents.ITEM_RATING)) {
            return;
        }
        stack.set(ModDataComponents.APPRAISAL, new Appraisal(random.nextLong(), List.of()));
        EquipmentService.refreshName(stack);
    }

    /** Reveals everything at once, as if the Eye had been used enough times (tests and debugging). */
    public static void appraiseFully(ItemStack stack) {
        stack.remove(ModDataComponents.APPRAISAL);
        EquipmentService.refreshName(stack);
    }

    /** Reveals exactly {@code n} more data without spending anything (debugging); does nothing to an appraised piece. */
    public static AppraisalRules.Step revealNext(ItemStack stack, int n) {
        Appraisal appraisal = stack.get(ModDataComponents.APPRAISAL);
        if (appraisal == null) {
            return new AppraisalRules.Step(List.of(), keys(stack), true);
        }
        AppraisalRules.Step step = AppraisalRules.stepExactly(keys(stack), appraisal.seed(), appraisal.revealed(), n);
        store(stack, appraisal.seed(), step);
        return step;
    }

    /**
     * One use of the Eye on {@code stack}: reveals from {@code balance.minData()} to {@code balance.maxData()} data, shows the
     * result and, if it was the last of them, activates the piece's rating and options. The caller spends the Eye.
     */
    public static Result appraise(ServerPlayer player, ItemStack stack, AppraisalBalance balance) {
        Appraisal appraisal = stack.get(ModDataComponents.APPRAISAL);
        if (appraisal == null) {
            return new Result(0, true);
        }
        AppraisalRules.Step step = AppraisalRules.step(keys(stack), appraisal.seed(), appraisal.revealed(), balance.minData(), balance.maxData());
        store(stack, appraisal.seed(), step);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.7f);
        NotificationEvents.send(player, SystemNotification.Kind.ITEM, List.of(
                Component.translatable("notification.mitofus.appraised", stack.getHoverName()),
                Component.translatable("notification.mitofus.appraisal_revealed", step.newlyRevealed().size()),
                Component.translatable(step.complete() ? "notification.mitofus.appraisal_complete"
                        : "notification.mitofus.appraisal_partial")));
        if (step.complete()) {
            ProgressionService.refreshEffects(player);
        }
        return new Result(step.newlyRevealed().size(), step.complete());
    }

    private static void store(ItemStack stack, long seed, AppraisalRules.Step step) {
        if (step.complete()) {
            stack.remove(ModDataComponents.APPRAISAL);
        } else {
            stack.set(ModDataComponents.APPRAISAL, new Appraisal(seed, step.revealed()));
        }
        EquipmentService.refreshName(stack);
    }
}
