package com.orijua.mitofus.feature.progression.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusTab;
import com.orijua.mitofus.core.client.ScrollList;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.fusion.client.FusionScreen;
import com.orijua.mitofus.feature.fusion.client.FusionText;
import com.orijua.mitofus.feature.progression.ChooseClassPayload;
import com.orijua.mitofus.feature.progression.ChooseJobPayload;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.DefenseRules;
import com.orijua.mitofus.feature.progression.ProgressionRules;
import com.orijua.mitofus.feature.progression.ClassSlotRules;
import com.orijua.mitofus.feature.progression.JobDefinition;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.progression.PlayerIdentity;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.progression.TitleDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/** Classes (one per open slot, chosen once) and job (chosen once) on top, earned titles below. As in the original design, a title stays secret until it is earned. */
public class ProfileTab extends MitofusTab {
    /** Where the title rows start when the class description fits in two lines; a longer one pushes them down. */
    private static final int TITLES_TOP = 64;
    private static final int TITLE_ROW = 12;
    /** Space between a title's name and its bonus column. */
    private static final int NAME_GAP = 6;

    private final ScrollList titleScroll = new ScrollList(9);

    public ProfileTab() {
        super(Component.translatable("tab.mitofus.profile"));
    }

    private static PlayerIdentity identity() {
        return player().getData(ModAttachments.PLAYER_IDENTITY);
    }

    private static List<Definitions.Entry<TitleDefinition>> earnedTitles() {
        PlayerIdentity identity = identity();
        return Definitions.all(player().level(), ModDatapackRegistries.TITLE).stream()
                .filter(entry -> identity.hasTitle(entry.id()))
                .toList();
    }

    private static final int LINE = 10;
    private static final int BUTTON_HEIGHT = 18;

    /** What the widgets were built for: the classes, the job and the open slots; a change rebuilds the tab. */
    private String builtFor = "";

    private static PlayerClasses classes() {
        return player().getData(ModAttachments.PLAYER_CLASSES);
    }

    private static String signature() {
        return classes() + "/" + ClassSlotRules.unlockedSlots(ProgressionService.get(player()).level());
    }

    private static int freeSlot() {
        return ClassSlotRules.firstFreeSlot(classes(), ProgressionService.get(player()).level());
    }

    /** Vertical offsets (from the top of the tab) of each block; the buttons are built where the render draws the labels. */
    private record Layout(int classLines, int classButtons, int jobHeading, int jobRow, int defenseHeading, int titles) {
    }

    private static Layout layout() {
        int y = 14;
        int classLines = y;
        y += classes().slots().size() * LINE;
        int classButtons = -1;
        if (freeSlot() >= 0) {
            y += 2 + LINE;
            classButtons = y;
            y += BUTTON_HEIGHT + 4;
        }
        int jobHeading = y + 2;
        int jobRow = jobHeading + 14;
        y = jobRow + (classes().job().isPresent() ? LINE : BUTTON_HEIGHT) + 6;
        int defenseHeading = y + 4;
        // The defense block: its heading, then the armor line and the line of where it comes from.
        int titles = defenseHeading + 14 + 2 * LINE + 14;
        return new Layout(classLines, classButtons, jobHeading, jobRow, defenseHeading, Math.max(TITLES_TOP, titles));
    }

    @Override
    protected void addWidgets(Consumer<AbstractWidget> addWidget) {
        builtFor = signature();
        Layout layout = layout();
        int slot = freeSlot();
        if (slot >= 0) {
            List<Definitions.Entry<PlayerClassDefinition>> classes = Definitions.all(player().level(), ModDatapackRegistries.PLAYER_CLASS).stream()
                    .filter(entry -> entry.value().selectable() && !classes().hasClass(entry.id()))
                    .toList();
            addRow(addWidget, classes.size(), layout.classButtons(), i -> {
                Definitions.Entry<PlayerClassDefinition> entry = classes.get(i);
                return Button.builder(entry.value().name(), b -> PacketDistributor.sendToServer(new ChooseClassPayload(slot, entry.id())))
                        .tooltip(Tooltip.create(classTooltip(entry.id(), entry.value())));
            });
        }
        if (!Definitions.all(player().level(), ModDatapackRegistries.FUSION).isEmpty()) {
            addWidget.accept(Button.builder(Component.translatable("screen.mitofus.fusions"),
                            b -> Minecraft.getInstance().setScreen(new FusionScreen(Minecraft.getInstance().screen)))
                    .bounds(left + width - 70, top - 2, 70, 14).build(MitofusButton::new));
        }
        if (classes().job().isEmpty()) {
            List<Definitions.Entry<JobDefinition>> jobs = Definitions.all(player().level(), ModDatapackRegistries.JOB).stream()
                    .filter(entry -> entry.value().selectable())
                    .toList();
            addRow(addWidget, jobs.size(), layout.jobRow(), i -> {
                Definitions.Entry<JobDefinition> entry = jobs.get(i);
                return Button.builder(entry.value().name(), b -> PacketDistributor.sendToServer(new ChooseJobPayload(entry.id())))
                        .tooltip(Tooltip.create(jobTooltip(entry.id(), entry.value())));
            });
        }
    }

    /** A row of {@code count} equal buttons across the tab at {@code y} below the top. */
    private void addRow(Consumer<AbstractWidget> addWidget, int count, int y, IntFunction<Button.Builder> button) {
        int buttonWidth = count == 0 ? 0 : (width - (count - 1) * 4) / count;
        for (int i = 0; i < count; i++) {
            addWidget.accept(button.apply(i).bounds(left + i * (buttonWidth + 4), top + y, buttonWidth, BUTTON_HEIGHT).build(MitofusButton::new));
        }
    }

    @Override
    public boolean mouseScrolled(double wheel) {
        return titleScroll.scroll(wheel, earnedTitles().size());
    }

    @Override
    public boolean needsRebuild() {
        return !builtFor.equals(signature());
    }

    /** The class description and bonuses, then what it would give with the player's job at each step. */
    private static Component classTooltip(ResourceLocation id, PlayerClassDefinition definition) {
        MutableComponent text = definition.description().copy();
        if (!definition.bonusStats().isEmpty()) {
            text.append("\n").append(bonusText(definition.bonusStats()));
        }
        classes().job().ifPresent(job -> text.append(FusionText.synergyLines(player().level(), id, job)));
        return text;
    }

    /** The job description, then what each class the player has placed would give with it at each step. */
    private static Component jobTooltip(ResourceLocation id, JobDefinition definition) {
        MutableComponent text = definition.description().copy();
        for (ClassSlot placed : classes().slots()) {
            text.append(FusionText.synergyLines(player().level(), placed.classId(), id));
        }
        return text;
    }

    private static Component bonusText(Map<StatType, Integer> bonuses) {
        MutableComponent text = Component.empty();
        bonuses.forEach((stat, value) -> text.append(Component.literal((value > 0 ? "+" : "") + value + " ")).append(stat.displayName()).append("  "));
        return text;
    }

    /** A title's stat bonuses, then its smithing experience bonus if it has one. */
    private static Component titleBonusText(TitleDefinition title) {
        MutableComponent text = bonusText(title.bonusStats()).copy();
        if (title.smithingXpBonus() > 0) {
            text.append(Component.translatable("screen.mitofus.smithing_xp_bonus", Math.round(title.smithingXpBonus() * 100)));
        }
        return text;
    }

    /** A defense value with no useless ".0": 12 or 12.5. */
    private static String number(double value) {
        return Math.abs(value - Math.rint(value)) < 0.05 ? String.valueOf((long) Math.rint(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static Component sourceName(DefenseRules.Source source) {
        return Component.translatable("screen.mitofus.defense.source." + source.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** The total armor and toughness of the player, live, and where they come from; the hover lists the pieces. */
    private void renderDefense(GuiGraphics graphics, int headingY, int mouseX, int mouseY) {
        DefenseRules.Breakdown armor = DefenseRules.of(player(), Attributes.ARMOR);
        DefenseRules.Breakdown toughness = DefenseRules.of(player(), Attributes.ARMOR_TOUGHNESS);
        UiStyle.heading(graphics, font(), Component.translatable("screen.mitofus.defense"), left, top + headingY);
        int y = top + headingY + 14;
        UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.defense.total", number(armor.total()), number(toughness.total())),
                left, y, armor.total() > 0 ? UiStyle.TEXT : UiStyle.MUTED);
        MutableComponent sources = Component.empty();
        for (DefenseRules.Source source : DefenseRules.Source.values()) {
            double value = armor.of(source);
            if (Math.abs(value) >= 0.05) {
                if (!sources.getSiblings().isEmpty()) {
                    sources.append(Component.literal("  ·  "));
                }
                sources.append(Component.translatable("screen.mitofus.defense.part", sourceName(source), (value > 0 ? "+" : "") + number(value)));
            }
        }
        if (sources.getSiblings().isEmpty()) {
            sources.append(Component.translatable("screen.mitofus.defense.none"));
        }
        UiStyle.text(graphics, font(), UiStyle.ellipsize(font(), sources, width), left, y + LINE, UiStyle.MUTED);
        if (mouseX >= left && mouseX < left + width && mouseY >= y - 1 && mouseY < y + 2 * LINE) {
            graphics.renderComponentTooltip(font(), defenseTooltip(armor, toughness), mouseX, mouseY);
        }
    }

    /** Every part of armor and toughness, and each piece worn with the armor it gives. */
    private static List<Component> defenseTooltip(DefenseRules.Breakdown armor, DefenseRules.Breakdown toughness) {
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("screen.mitofus.defense.tip.armor", number(armor.total())).withStyle(net.minecraft.ChatFormatting.GOLD));
        for (DefenseRules.Source source : DefenseRules.Source.values()) {
            if (Math.abs(armor.of(source)) >= 0.05) {
                lines.add(Component.translatable("screen.mitofus.defense.part", sourceName(source), number(armor.of(source))));
            }
        }
        lines.add(Component.translatable("screen.mitofus.defense.tip.toughness", number(toughness.total())).withStyle(net.minecraft.ChatFormatting.GOLD));
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = player().getItemBySlot(slot);
            if (worn.isEmpty()) {
                continue;
            }
            double base = worn.getAttributeModifiers().modifiers().stream()
                    .filter(entry -> entry.attribute().equals(Attributes.ARMOR) && entry.slot().test(slot)
                            && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE)
                    .mapToDouble(entry -> entry.modifier().amount()).sum();
            lines.add(Component.translatable("screen.mitofus.defense.tip.piece", worn.getHoverName(), number(base)));
        }
        return lines;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Layout layout = layout();
        UiStyle.heading(graphics, font(), Component.translatable("screen.mitofus.class"), left, top);
        int classY = top + layout.classLines();
        for (ClassSlot placed : classes().slots()) {
            Component name = FusionText.slotName(player().level(), placed);
            UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.class_slot_level", placed.slot() + 1, name,
                    placed.classLevel(), placed.grade().displayName()), left, classY, UiStyle.TEXT);
            Component xp = Component.translatable("screen.mitofus.class_xp", placed.classXp(),
                    ProgressionRules.xpToNextLevel(Balance.current(), placed.classLevel()));
            UiStyle.text(graphics, font(), xp, left + width - font().width(xp), classY, UiStyle.MUTED);
            classY += LINE;
        }
        if (layout.classButtons() >= 0) {
            UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.choose_class", freeSlot() + 1),
                    left, top + layout.classButtons() - LINE - 2, UiStyle.MUTED);
        }
        UiStyle.heading(graphics, font(), Component.translatable("screen.mitofus.job"), left, top + layout.jobHeading());
        classes().job().ifPresentOrElse(
                id -> UiStyle.text(graphics, font(), Definitions.get(player().level(), ModDatapackRegistries.JOB, id)
                        .map(JobDefinition::name).orElse(Component.literal(id.toString())), left, top + layout.jobRow(), UiStyle.TEXT),
                () -> UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.choose_job"), left + 50, top + layout.jobHeading(), UiStyle.MUTED));
        renderDefense(graphics, layout.defenseHeading(), mouseX, mouseY);
        int titlesTop = layout.titles();

        UiStyle.heading(graphics, font(), Component.translatable("screen.mitofus.titles"), left, top + titlesTop - 14);
        List<Definitions.Entry<TitleDefinition>> titles = earnedTitles();
        if (titles.isEmpty()) {
            UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.no_titles"), left, top + titlesTop, UiStyle.MUTED);
            return;
        }
        int visibleTitles = (height - titlesTop) / TITLE_ROW;
        titleScroll.setVisibleRows(visibleTitles);
        titleScroll.clamp(titles.size());
        for (int i = titleScroll.first(); i < titleScroll.end(titles.size()); i++) {
            TitleDefinition title = titles.get(i).value();
            int y = top + titlesTop + (i - titleScroll.first()) * TITLE_ROW;
            // A long name is cut before the bonus column; the tooltip gives it whole.
            Component name = UiStyle.ellipsize(font(), Component.literal("✔ ").append(title.name()), width / 2 - NAME_GAP);
            UiStyle.text(graphics, font(), name, left, y, UiStyle.GOLD);
            UiStyle.text(graphics, font(), titleBonusText(title), left + width / 2, y, title.bonusStats().values().stream().anyMatch(v -> v < 0) ? UiStyle.BAD : UiStyle.GOOD);
            if (mouseX >= left && mouseX < left + width && mouseY >= y - 1 && mouseY < y + TITLE_ROW - 1) {
                graphics.renderTooltip(font(), List.of(title.name().getVisualOrderText(), title.description().getVisualOrderText(), title.goal().describe().getVisualOrderText()), mouseX, mouseY);
            }
        }
        titleScroll.renderBar(graphics, left + width + 3, top + titlesTop - 1, visibleTitles * TITLE_ROW, titles.size());
    }
}
