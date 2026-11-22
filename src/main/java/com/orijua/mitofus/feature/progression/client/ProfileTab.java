package com.orijua.mitofus.feature.progression.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusTab;
import com.orijua.mitofus.core.client.ScrollList;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.progression.ChooseClassPayload;
import com.orijua.mitofus.feature.progression.ChooseJobPayload;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.ClassSlotRules;
import com.orijua.mitofus.feature.progression.JobDefinition;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.progression.PlayerIdentity;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.progression.TitleDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
    private record Layout(int classLines, int classButtons, int jobHeading, int jobRow, int titles) {
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
        return new Layout(classLines, classButtons, jobHeading, jobRow, Math.max(TITLES_TOP, y + 14));
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
                        .tooltip(Tooltip.create(classTooltip(entry.value())));
            });
        }
        if (classes().job().isEmpty()) {
            List<Definitions.Entry<JobDefinition>> jobs = Definitions.all(player().level(), ModDatapackRegistries.JOB).stream()
                    .filter(entry -> entry.value().selectable())
                    .toList();
            addRow(addWidget, jobs.size(), layout.jobRow(), i -> {
                Definitions.Entry<JobDefinition> entry = jobs.get(i);
                return Button.builder(entry.value().name(), b -> PacketDistributor.sendToServer(new ChooseJobPayload(entry.id())))
                        .tooltip(Tooltip.create(entry.value().description()));
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

    private static Component classTooltip(PlayerClassDefinition definition) {
        MutableComponent text = definition.description().copy();
        if (!definition.bonusStats().isEmpty()) {
            text.append("\n").append(bonusText(definition.bonusStats()));
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Layout layout = layout();
        UiStyle.heading(graphics, font(), Component.translatable("screen.mitofus.class"), left, top);
        int classY = top + layout.classLines();
        for (ClassSlot placed : classes().slots()) {
            Component name = Definitions.get(player().level(), ModDatapackRegistries.PLAYER_CLASS, placed.classId())
                    .map(PlayerClassDefinition::name).orElse(Component.literal(placed.classId().toString()));
            UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.class_slot_level", placed.slot() + 1, name,
                    placed.classLevel(), placed.grade().displayName()), left, classY, UiStyle.TEXT);
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
