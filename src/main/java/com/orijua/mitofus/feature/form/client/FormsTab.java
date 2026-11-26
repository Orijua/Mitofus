package com.orijua.mitofus.feature.form.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusEmptyState;
import com.orijua.mitofus.core.client.MitofusRow;
import com.orijua.mitofus.core.client.MitofusTab;
import com.orijua.mitofus.core.client.PanelFit;
import com.orijua.mitofus.core.client.ScrollList;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.form.ElementTypeDefinition;
import com.orijua.mitofus.feature.form.EquipFormPayload;
import com.orijua.mitofus.feature.form.FormDefinition;
import com.orijua.mitofus.feature.form.FormRows;
import com.orijua.mitofus.feature.form.FormRules;
import com.orijua.mitofus.feature.form.PlayerForms;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The forms of the player's classes: one row each with its type, level and damage bonus, a button to wear it or take it off, and
 * the detail (skills by form level, uses to the next level, how to unlock it) on hover. Forms not owned yet are dimmed.
 */
public class FormsTab extends MitofusTab {
    private static final int ROW = 28;
    private static final int LIST_TOP = 16;
    private static final int BUTTON_WIDTH = 56;

    private final List<FormRows.Row> visible = new ArrayList<>();
    private final ScrollList scroll = new ScrollList(1);
    private List<FormRows.Row> all = List.of();
    private PlayerForms shownForms = PlayerForms.EMPTY;
    private List<com.orijua.mitofus.feature.progression.ClassSlot> shownSlots = List.of();
    private int visibleRows = 1;

    /** The refusal of the last click, shown for a few seconds at the bottom of the tab. */
    private static Component message;
    private static long messageUntil;
    private static final long MESSAGE_MILLIS = 4000;

    public static void showMessage(String key) {
        message = Component.translatable(key);
        messageUntil = Util.getMillis() + MESSAGE_MILLIS;
    }

    public FormsTab() {
        super(Component.translatable("tab.mitofus.forms"));
    }

    private static PlayerForms forms() {
        return player().getData(ModAttachments.PLAYER_FORMS);
    }

    private static List<FormRows.Row> compute() {
        return FormRows.rows(Definitions.all(player().level(), ModDatapackRegistries.FORM),
                slots(), forms());
    }

    private static List<com.orijua.mitofus.feature.progression.ClassSlot> slots() {
        return player().getData(ModAttachments.PLAYER_CLASSES).slots();
    }

    @Override
    protected void addWidgets(Consumer<AbstractWidget> addWidget) {
        visible.clear();
        all = compute();
        shownForms = forms();
        shownSlots = slots();
        visibleRows = PanelFit.rows(height - LIST_TOP, ROW);
        scroll.setVisibleRows(visibleRows);
        scroll.clamp(all.size());
        for (int i = scroll.first(); i < scroll.end(all.size()); i++) {
            FormRows.Row row = all.get(i);
            visible.add(row);
            if (row.state() == FormRows.State.LOCKED) {
                continue;
            }
            boolean worn = row.state() == FormRows.State.EQUIPPED;
            Optional<ResourceLocation> target = worn ? Optional.empty() : Optional.of(row.id());
            addWidget.accept(Button.builder(Component.translatable(worn ? "screen.mitofus.form_unequip" : "screen.mitofus.form_equip"),
                            b -> PacketDistributor.sendToServer(new EquipFormPayload(target)))
                    .bounds(left + width - 6 - BUTTON_WIDTH - 2, rowY(visible.size() - 1) + 4, BUTTON_WIDTH, 14).build(MitofusButton::new));
        }
    }

    private int rowY(int index) {
        return top + LIST_TOP + index * ROW;
    }

    @Override
    public boolean needsRebuild() {
        // Attachments are replaced when they change, so the same object means nothing changed.
        return forms() != shownForms || slots() != shownSlots;
    }

    @Override
    public boolean mouseScrolled(double wheel) {
        return scroll.scroll(wheel, all.size());
    }

    @Override
    public boolean keyPressed(int keyCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            return scroll.page(1, all.size());
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            return scroll.page(-1, all.size());
        }
        return false;
    }

    private static Optional<ElementTypeDefinition> type(FormDefinition form) {
        return Definitions.get(player().level(), ModDatapackRegistries.ELEMENT_TYPE, form.type());
    }

    private static Component summary(FormRows.Row row) {
        Component typeName = type(row.form()).map(ElementTypeDefinition::name).orElse(Component.empty());
        MutableComponent text;
        if (row.state() == FormRows.State.LOCKED) {
            text = switch (row.lock()) {
                case PREVIOUS_FORM -> Component.translatable("screen.mitofus.form_lock_previous", row.blockedBy()
                        .flatMap(id -> Definitions.get(player().level(), ModDatapackRegistries.FORM, id)).map(FormDefinition::name).orElse(Component.empty()));
                case GRADE_QUEST -> Component.translatable("screen.mitofus.form_lock_quest", row.form().unlockLevel());
                default -> Component.translatable("screen.mitofus.form_lock_level", row.form().unlockLevel());
            };
        } else {
            text = Component.translatable("screen.mitofus.form_summary", typeName, row.level(),
                    String.format("%.1f", FormRules.typeBonus(row.form(), row.level()) * 100));
        }
        return text;
    }

    private static Component skillName(FormDefinition form, int index) {
        ResourceLocation id = form.skills().get(index);
        if (index == FormDefinition.PASSIVE_INDEX) {
            return Definitions.get(player().level(), ModDatapackRegistries.PASSIVE, id)
                    .map(p -> p.name()).orElse(Component.literal(id.toString()));
        }
        return Definitions.get(player().level(), ModDatapackRegistries.SKILL, id)
                .map(s -> s.name()).orElse(Component.literal(id.toString()));
    }

    /** The hover: description, the skills with the form level each one comes at, and the uses to the next level. */
    private static Component detail(FormRows.Row row) {
        MutableComponent text = row.form().description().copy();
        for (int i = 0; i < row.form().skills().size() && i < FormRules.SKILL_LEVELS.size(); i++) {
            int needed = FormRules.SKILL_LEVELS.get(i);
            boolean reached = row.state() != FormRows.State.LOCKED && row.level() >= needed;
            text.append("\n").append(Component.translatable("screen.mitofus.form_skill_line", needed, skillName(row.form(), i))
                    .withStyle(reached ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        if (row.state() != FormRows.State.LOCKED) {
            text.append("\n");
            if (row.level() >= FormRules.MAX_LEVEL) {
                text.append(Component.translatable("screen.mitofus.form_max"));
            } else {
                text.append(Component.translatable("screen.mitofus.form_next", row.xp() - FormRules.usesToReach(row.form().curve(), row.level()),
                        FormRules.usesToAdvance(row.form().curve(), row.level())));
            }
        }
        return text;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        UiStyle.heading(graphics, font(), Component.translatable("screen.mitofus.forms_header"), left, top);
        if (all.isEmpty()) {
            MitofusEmptyState.draw(graphics, font(), left, top + LIST_TOP, width, height - LIST_TOP,
                    Component.translatable("screen.mitofus.no_forms"), MitofusEmptyState.Kind.EMPTY);
            return;
        }
        FormRows.Row hovered = null;
        int cardWidth = width - 6 - BUTTON_WIDTH - 6;
        for (int i = 0; i < visible.size(); i++) {
            FormRows.Row row = visible.get(i);
            int accent = type(row.form()).map(ElementTypeDefinition::color).orElse(0xFFFFFF);
            Component title = row.state() == FormRows.State.EQUIPPED
                    ? row.form().name().copy().append(" · ").append(Component.translatable("screen.mitofus.form_worn")) : row.form().name();
            boolean over = MitofusRow.draw(graphics, font(), left, rowY(i), cardWidth, accent, title, summary(row),
                    row.state() == FormRows.State.LOCKED, row.state() == FormRows.State.EQUIPPED, mouseX, mouseY, ElementIcons.SIZE + 2);
            ElementIcons.draw(graphics, row.form().type(), left + cardWidth - ElementIcons.SIZE - 3, rowY(i) + (MitofusRow.height() - ElementIcons.SIZE) / 2);
            if (over) {
                hovered = row;
            }
        }
        if (message != null && Util.getMillis() < messageUntil) {
            graphics.drawString(font(), message, left, top + height - 10, UiStyle.BAD);
        }
        scroll.renderBar(graphics, left + width + 3, top + LIST_TOP - 2, visibleRows * ROW - 4, all.size());
        if (hovered != null) {
            graphics.renderTooltip(font(), font().split(detail(hovered), 200), mouseX, mouseY);
        }
    }
}
