package com.orijua.mitofus.feature.progression.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusTab;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetRules;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetText;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import com.orijua.mitofus.feature.fatigue.FatigueRules;
import com.orijua.mitofus.feature.fatigue.client.FatigueHudLayer;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.view.Observation;
import com.orijua.mitofus.feature.progression.AllocateStatPayload;
import com.orijua.mitofus.core.config.WeightBalance;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.equipment.ItemWeights;
import com.orijua.mitofus.feature.equipment.WeightRules;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionRules;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatEffects;
import com.orijua.mitofus.feature.progression.StatModifier;
import com.orijua.mitofus.feature.progression.StatSource;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.progression.XpRate;
import com.orijua.mitofus.feature.progression.XpRateRules;
import com.orijua.mitofus.feature.progression.XpRateService;
import com.orijua.mitofus.feature.progression.XpRateText;
import com.orijua.mitofus.core.config.PassiveXpBalance;
import java.util.OptionalLong;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import com.orijua.mitofus.core.client.MitofusConfirmScreen;
import com.orijua.mitofus.feature.vrhelmet.LogoutRequestPayload;
import com.orijua.mitofus.feature.quest.QuestService;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Level, XP, resources and the five stats with "+" buttons. */
public class StatusTab extends MitofusTab {
    private static final int STATS_TOP = 66;
    private static final int STAT_ROW = 18;

    private final List<Button> plusButtons = new ArrayList<>();

    public StatusTab() {
        super(Component.translatable("tab.mitofus.status"));
    }

    private static PlayerStats stats() {
        return player().getData(ModAttachments.PLAYER_STATS);
    }

    @Override
    protected void addWidgets(Consumer<AbstractWidget> addWidget) {
        plusButtons.clear();
        StatType[] types = StatType.values();
        for (int i = 0; i < types.length; i++) {
            StatType stat = types[i];
            Button button = Button.builder(Component.literal("+"),
                            b -> PacketDistributor.sendToServer(new AllocateStatPayload(stat, pointsPerClick())))
                    .bounds(left + width - 18, top + STATS_TOP + i * STAT_ROW - 4, 16, 16)
                    .tooltip(Tooltip.create(Component.translatable("screen.mitofus.allocate_hint")))
                    .build(MitofusButton::new);
            plusButtons.add(button);
            addWidget.accept(button);
        }
        addWidget.accept(Button.builder(Component.translatable("screen.mitofus.logout"), b -> {
                    if (QuestService.get(player()).deadlines().isEmpty()) {
                        PacketDistributor.sendToServer(LogoutRequestPayload.INSTANCE);
                        Minecraft.getInstance().setScreen(null);
                    } else {
                        // A quest has a deadline running: leaving fails it, so the player has to confirm.
                        Minecraft minecraft = Minecraft.getInstance();
                        minecraft.setScreen(new MitofusConfirmScreen(minecraft.screen,
                                Component.translatable("screen.mitofus.logout_forced_title"),
                                Component.translatable("screen.mitofus.logout_forced"),
                                () -> PacketDistributor.sendToServer(LogoutRequestPayload.FORCED)));
                    }
                })
                .bounds(left, top + height - 18, 110, 16)
                .tooltip(Tooltip.create(Component.translatable("screen.mitofus.logout.tooltip")))
                .build(MitofusButton::new));
        tick();
    }

    @Override
    public void tick() {
        boolean hasPoints = stats().freePoints() > 0;
        plusButtons.forEach(button -> button.active = hasPoints);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        PlayerStats stats = stats();
        PlayerStats effective = ProgressionService.effectiveStats(player());
        float mana = player().getData(ModAttachments.MANA);
        int half = left + width / 2;

        UiStyle.heading(graphics, font(), Component.translatable("screen.mitofus.level", stats.level()), left, top);
        ProgressionRules.XpProgress progress = ProgressionRules.progress(Balance.current(), stats);
        Component xpText = Component.translatable("screen.mitofus.xp", progress.xp(), progress.needed());
        int xpTextX = left + width - font().width(xpText);
        UiStyle.bar(graphics, half, top + 2, xpTextX - 6 - half, 4, progress.xp() / (float) progress.needed(), UiStyle.XP);
        UiStyle.text(graphics, font(), xpText, xpTextX, top, UiStyle.TEXT);

        boolean overRate = renderXpRate(graphics, mouseX, mouseY, progress);

        UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.mana", (int) mana, (int) ProgressionService.maxMana(player())), left, top + 28, UiStyle.TEXT);
        UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.smithing", ProgressionService.effectiveSmithingLevel(player())), half, top + 28, UiStyle.MUTED);
        UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.free_points", stats.freePoints()), left, top + 42,
                stats.freePoints() > 0 ? UiStyle.GOOD : UiStyle.MUTED);
        int swordBonus = Observation.effectiveView(player()) - Observation.view(player());
        Component view = Component.translatable("screen.mitofus.view", Observation.view(player()))
                .append(swordBonus > 0 ? Component.literal(" +" + swordBonus).withColor(UiStyle.GOOD) : Component.empty());
        UiStyle.text(graphics, font(), view, half, top + 42, UiStyle.MUTED);
        boolean overView = mouseX >= half && mouseX < half + font().width(view) && mouseY >= top + 40 && mouseY < top + 52;

        boolean overEnergy = renderEnergy(graphics, mouseX, mouseY, half);

        StatType[] types = StatType.values();
        StatType hovered = null;
        for (int i = 0; i < types.length; i++) {
            int y = top + STATS_TOP + i * STAT_ROW;
            UiStyle.text(graphics, font(), types[i].displayName(), left, y, UiStyle.TEXT);
            UiStyle.text(graphics, font(), Component.literal(String.valueOf(stats.get(types[i]))), left + width - 60, y, UiStyle.GOLD);
            int bonus = effective.get(types[i]) - stats.get(types[i]);
            // A penalty (title "Noble Scourge": -50 intelligence) shows in red; the total never goes below 0.
            if (bonus != 0) {
                UiStyle.text(graphics, font(), Component.literal((bonus > 0 ? "+" : "") + bonus), left + width - 42, y, bonus > 0 ? UiStyle.GOOD : UiStyle.BAD);
            }
            if (mouseX >= left && mouseX < left + width - 20 && mouseY >= y - 4 && mouseY < y + STAT_ROW - 4) {
                hovered = types[i];
            }
        }
        Component weightTip = renderMoneyAndWeight(graphics, mouseX, mouseY, effective);
        if (weightTip != null) {
            graphics.renderTooltip(font(), font().split(weightTip, 200), mouseX, mouseY);
        } else if (overRate) {
            PassiveXpBalance passive = PassiveXpBalance.current();
            Component peaceful = passive.firstReducedLevel() >= passive.oneXpFromLevel()
                    ? Component.translatable("screen.mitofus.xp_rate.tooltip.step", passive.oneXpFromLevel())
                    : Component.translatable("screen.mitofus.xp_rate.tooltip.passive", passive.firstReducedLevel(), passive.oneXpFromLevel());
            Component tooltip = Component.translatable("screen.mitofus.xp_rate.tooltip", XpRateRules.WINDOW_MINUTES)
                    .append("\n").append(peaceful);
            graphics.renderTooltip(font(), font().split(tooltip, 220), mouseX, mouseY);
        } else if (overEnergy) {
            graphics.renderTooltip(font(), font().split(energyTooltip(), 220), mouseX, mouseY);
        } else if (hovered != null) {
            graphics.renderTooltip(font(), tooltip(hovered), mouseX, mouseY);
        } else if (overView) {
            graphics.renderTooltip(font(), font().split(Component.translatable("screen.mitofus.view.tooltip",
                    String.format("%.1f", Observation.observeTicks(player()) / 20.0)), 200), mouseX, mouseY);
        }
    }

    /**
     * The bottom row, right of the logout button: the weight carried against the limit with a small bar (only when weight is
     * on) and, at the far right, the money. Returns the weight's tooltip when the mouse is over it, else null.
     */
    private Component renderMoneyAndWeight(GuiGraphics graphics, int mouseX, int mouseY, PlayerStats effective) {
        int rowY = top + height - 14;
        Component money = MoneyText.iron(Wallet.balance(player()));
        int moneyX = left + width - font().width(money);
        UiStyle.text(graphics, font(), money, moneyX, rowY, UiStyle.GOLD);
        WeightBalance balance = WeightBalance.current();
        if (!balance.enabled()) {
            return null;
        }
        long limit = WeightRules.limitGrams(balance, effective.stamina());
        long carried = ItemWeights.carried(player().getInventory());
        boolean over = carried > limit;
        Component text = Component.translatable("screen.mitofus.weight", WeightRules.formatKg(carried), WeightRules.formatKg(limit));
        int textWidth = font().width(text);
        int x = moneyX - 10 - textWidth;
        UiStyle.text(graphics, font(), text, x, rowY - 2, over ? UiStyle.BAD : UiStyle.MUTED);
        float fraction = limit <= 0L ? 1.0f : Math.min(1.0f, carried / (float) limit);
        UiStyle.bar(graphics, x, rowY + 8, textWidth, 3, fraction, over ? UiStyle.BAD : UiStyle.GOOD);
        if (mouseX >= x && mouseX < x + textWidth && mouseY >= rowY - 4 && mouseY < rowY + 12) {
            return Component.translatable("screen.mitofus.weight.tooltip");
        }
        return null;
    }

    /** The XP still missing with the time it would take, and the XP per hour on the right. Returns whether the mouse is over the row. */
    private boolean renderXpRate(GuiGraphics graphics, int mouseX, int mouseY, ProgressionRules.XpProgress progress) {
        XpRate rate = XpRateService.get(player());
        OptionalLong perHour = XpRateRules.xpPerHour(rate);
        OptionalLong eta = XpRateText.etaMinutes(rate, progress.remaining());
        Component remaining = eta.isPresent()
                ? Component.translatable("screen.mitofus.xp_remaining.eta", progress.remaining(), XpRateText.duration(eta.getAsLong()))
                : Component.translatable("screen.mitofus.xp_remaining", progress.remaining());
        Component rateText = perHour.isPresent()
                ? Component.translatable("screen.mitofus.xp_rate", perHour.getAsLong())
                : Component.translatable("screen.mitofus.xp_rate.measuring");
        int y = top + 14;
        UiStyle.text(graphics, font(), remaining, left, y, UiStyle.TEXT);
        UiStyle.text(graphics, font(), rateText, left + width - font().width(rateText), y, perHour.isPresent() ? UiStyle.TEXT : UiStyle.MUTED);
        return mouseX >= left && mouseX < left + width && mouseY >= y - 2 && mouseY < y + 10;
    }

    /** The energy line: percentage, minutes left at normal play and a small bar. Returns whether the mouse is over it. */
    private boolean renderEnergy(GuiGraphics graphics, int mouseX, int mouseY, int half) {
        FatigueBalance balance = FatigueBalance.current();
        if (!balance.enabled()) {
            return false;
        }
        float energy = player().getData(ModAttachments.FATIGUE);
        int percent = FatigueRules.percent(energy);
        var minutes = FatigueRules.minutesLeft(balance, energy, FatigueRules.Activity.NORMAL, modifiers(balance));
        Component text = minutes.isPresent()
                ? Component.translatable("screen.mitofus.fatigue", percent, VrHelmetText.minutes(minutes.getAsDouble()))
                : Component.translatable("screen.mitofus.fatigue.plain", percent);
        int y = top + 54;
        UiStyle.text(graphics, font(), text, left, y, UiStyle.TEXT);
        UiStyle.bar(graphics, half, y + 2, Math.min(70, width / 2 - 8), 4, energy, FatigueHudLayer.colorFor(energy, balance));
        return mouseX >= left && mouseX < half + 70 && mouseY >= y - 2 && mouseY < y + 10;
    }

    /** How long the energy lasts at each activity, and how it comes back. */
    private Component energyTooltip() {
        FatigueBalance balance = FatigueBalance.current();
        float energy = player().getData(ModAttachments.FATIGUE);
        VrHelmetTier tier = player().getData(ModAttachments.VR_HELMET_TIER);
        MutableComponent tooltip = Component.translatable("screen.mitofus.status.vrhelmet_tier", tier.tierName()).append("\n")
                .append(Component.translatable("screen.mitofus.fatigue.tooltip",
                        minutesOrDash(balance, energy, FatigueRules.Activity.REST),
                        minutesOrDash(balance, energy, FatigueRules.Activity.NORMAL),
                        minutesOrDash(balance, energy, FatigueRules.Activity.COMBAT),
                        VrHelmetText.minutes(VrHelmetBalance.current().recoveryMinutes(tier, balance)), FatigueRules.percent(balance.minEnergyToConnect())));
        int wait = VrHelmetRules.cooldownLeft(player().getData(ModAttachments.RESTED_COOLDOWN), VrHelmetBalance.current());
        if (wait > 0) {
            tooltip.append("\n").append(Component.translatable("screen.mitofus.status.rested_cooldown", VrHelmetText.clock(wait)));
        }
        return tooltip;
    }

    /** The session modifiers of the VR helmet tier the player last connected with (the client knows the synced tier and config). */
    private FatigueRules.SessionModifiers modifiers(FatigueBalance balance) {
        return VrHelmetRules.modifiers(player().getData(ModAttachments.VR_HELMET_TIER), balance, VrHelmetBalance.current());
    }

    private String minutesOrDash(FatigueBalance balance, float energy, FatigueRules.Activity activity) {
        var minutes = FatigueRules.minutesLeft(balance, energy, activity, modifiers(balance));
        return minutes.isPresent() ? VrHelmetText.minutes(minutes.getAsDouble()) : "-";
    }

    /** Control: 100 points, Shift: 10, plain click: 1. The server spends only what's left. */
    private static int pointsPerClick() {
        return Screen.hasControlDown() ? 100 : Screen.hasShiftDown() ? 10 : 1;
    }

    /** What one point does, then where the current value comes from ("Allocated: 5", "Warrior: +3"...). */
    private List<FormattedCharSequence> tooltip(StatType stat) {
        List<FormattedCharSequence> lines = new ArrayList<>(font().split(describe(stat), 200));
        List<Component> sources = new ArrayList<>();
        for (StatModifier modifier : ProgressionService.modifiers(player())) {
            if (modifier.stat() == stat && modifier.value() != 0) {
                sources.add(Component.translatable("screen.mitofus.stat_source", modifier.source().name(), amount(modifier))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (!sources.isEmpty()) {
            lines.add(FormattedCharSequence.EMPTY);
            lines.add(Component.translatable("screen.mitofus.stat_sources").getVisualOrderText());
            sources.forEach(line -> lines.add(line.getVisualOrderText()));
        }
        return lines;
    }

    /** "5" for allocated points, "+3" for a flat bonus, "+10%" for a percentage. */
    private static String amount(StatModifier modifier) {
        String sign = modifier.source() == StatSource.ALLOCATED || modifier.value() < 0 ? "" : "+";
        return switch (modifier.operation()) {
            case ADD -> sign + number(modifier.value());
            case MULTIPLY -> sign + number(modifier.value() * 100) + "%";
        };
    }

    /** "Per point: ..." built from the real numbers in StatEffects and Balance, so it never goes stale. */
    private static Component describe(StatType stat) {
        Balance balance = Balance.current();
        String key = "stat.mitofus." + stat.getSerializedName() + ".desc";
        return switch (stat) {
            case STRENGTH, STAMINA -> Component.translatable(key, number(StatEffects.currentPerPoint(stat)));
            case AGILITY, DEXTERITY -> Component.translatable(key, number(StatEffects.currentPerPoint(stat) * 100));
            case INTELLIGENCE -> Component.translatable(key, number(balance.manaPerIntelligence()), number(balance.manaRegenPerIntelligence()));
        };
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(Math.round(value * 100) / 100.0);
    }
}
