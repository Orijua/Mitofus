package com.orijua.mitofus.feature.skill.client;

import com.orijua.mitofus.MitofusClient;
import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusTab;
import com.orijua.mitofus.core.client.ScrollList;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.effect.PassiveDefinition;
import com.orijua.mitofus.feature.effect.ActiveTraits;
import com.orijua.mitofus.feature.skill.AssignSkillPayload;
import com.orijua.mitofus.feature.skill.MasteryText;
import com.orijua.mitofus.feature.skill.PlayerSkills;
import com.orijua.mitofus.feature.skill.SlotRules;
import com.orijua.mitofus.feature.skill.SkillDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One list of everything the player can do: active skills (with one button per slot key to equip them; press again to
 * unequip) and then the passives of their class (always on, no slot). A tag marks each row and a filter button cycles
 * all / active / passive.
 */
public class SkillsTab extends MitofusTab {
    private static final int ROW = 22;
    private static final int LIST_TOP = 16;
    private static final int VISIBLE_ROWS = 7;
    private static final int SLOT_BUTTON = 18;
    private static final int SLOT_BUTTON_WIDTH = 64;
    private static final int FILTER_WIDTH = 74;

    /** Which rows the list shows; the button cycles it. */
    enum Filter {
        ALL("screen.mitofus.skills_filter_all"),
        ACTIVE("screen.mitofus.skills_filter_active"),
        PASSIVE("screen.mitofus.skills_filter_passive");

        private final String key;

        Filter(String key) {
            this.key = key;
        }

        Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }

        boolean showsActive() {
            return this != PASSIVE;
        }

        boolean showsPassive() {
            return this != ACTIVE;
        }
    }

    /** An active skill (with its id, for the slot buttons) or a passive (id null). */
    private record Entry(ResourceLocation id, Component name, Component description, ItemStack icon, Component detail) {
        boolean passive() {
            return id == null;
        }
    }

    private record Row(Entry entry, List<Button> slotButtons) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final ScrollList scroll = new ScrollList(VISIBLE_ROWS);
    private Filter filter = Filter.ALL;
    private boolean dirty;
    private int shownCount;
    private int shownLearnedCount;
    private int shownPassiveCount;

    public SkillsTab() {
        super(Component.translatable("tab.mitofus.skills"));
    }

    private static PlayerSkills skills() {
        return player().getData(ModAttachments.PLAYER_SKILLS);
    }

    /** Actives first, then passives, as the filter allows. */
    private List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        if (filter.showsActive()) {
            for (ResourceLocation id : skills().learned()) {
                Definitions.get(player().level(), ModDatapackRegistries.SKILL, id).ifPresent(skill ->
                        result.add(new Entry(id, skill.name(), skill.description(), skill.iconStack(),
                                skill.costLine(false).copy().append(" · ").append(MasteryText.full(player().getData(ModAttachments.SKILL_MASTERY).level(id))))));
            }
        }
        if (filter.showsPassive()) {
            for (PassiveDefinition passive : ActiveTraits.active(player())) {
                result.add(new Entry(null, passive.name(), passive.description(), passive.iconStack(),
                        Component.translatable("screen.mitofus.skill_always_on")));
            }
        }
        return result;
    }

    @Override
    protected void addWidgets(Consumer<AbstractWidget> addWidget) {
        rows.clear();
        dirty = false;
        shownLearnedCount = skills().learned().size();
        shownPassiveCount = ActiveTraits.active(player()).size();
        addWidget.accept(Button.builder(Component.translatable(filter.key), b -> {
            filter = filter.next();
            scroll.clamp(0);
            dirty = true;
        }).bounds(left + width - FILTER_WIDTH, top - 4, FILTER_WIDTH, 14).build(MitofusButton::new));

        List<Entry> entries = entries();
        shownCount = entries.size();
        scroll.clamp(entries.size());
        for (int i = scroll.first(); i < scroll.end(entries.size()); i++) {
            Entry entry = entries.get(i);
            List<Button> buttons = new ArrayList<>();
            if (!entry.passive()) {
                Button button = Button.builder(slotLabel(entry.id()), b -> cycle(entry.id()))
                        .bounds(left + width - SLOT_BUTTON_WIDTH, rowY(rows.size()) - 2, SLOT_BUTTON_WIDTH, SLOT_BUTTON)
                        .build(MitofusButton::new);
                buttons.add(button);
                addWidget.accept(button);
            }
            rows.add(new Row(entry, buttons));
        }
        tick();
    }

    private int rowY(int index) {
        return top + LIST_TOP + index * ROW;
    }

    private static int level() {
        return com.orijua.mitofus.feature.progression.ProgressionService.get(player()).level();
    }

    /** The slot this skill sits in (0-based), or -1. */
    private static int slotOf(ResourceLocation skill) {
        return skills().slots().indexOf(Optional.of(skill));
    }

    /** "Slot 3" (page and number) or "Slot: none". */
    private static Component slotLabel(ResourceLocation skill) {
        int slot = slotOf(skill);
        return slot < 0 ? Component.translatable("screen.mitofus.skill_slot_none")
                : Component.translatable("screen.mitofus.skill_slot", slot / SlotRules.PAGE_SIZE + 1, slot % SlotRules.PAGE_SIZE + 1);
    }

    /** Each click moves the skill to the next open slot; after the last one it comes out of the slots. */
    private static void cycle(ResourceLocation skill) {
        int current = slotOf(skill);
        int next = SlotRules.nextOpen(level(), current);
        if (next < 0 && current < 0) {
            return; // no open slot at all: nothing to do
        }
        PacketDistributor.sendToServer(next < 0 ? new AssignSkillPayload(current, Optional.empty())
                : new AssignSkillPayload(next, Optional.of(skill)));
    }

    @Override
    public boolean needsRebuild() {
        return dirty || skills().learned().size() != shownLearnedCount
                || ActiveTraits.active(player()).size() != shownPassiveCount;
    }

    @Override
    public boolean mouseScrolled(double wheel) {
        return scroll.scroll(wheel, shownCount);
    }

    /** Shows the slot where each skill is equipped. */
    @Override
    public void tick() {
        for (Row row : rows) {
            if (!row.slotButtons().isEmpty()) {
                row.slotButtons().get(0).setMessage(slotLabel(row.entry().id()));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        UiStyle.heading(graphics, font(), Component.translatable("screen.mitofus.skills_header"), left, top);
        if (rows.isEmpty()) {
            UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.no_skills"), left, top + LIST_TOP, UiStyle.MUTED);
            return;
        }
        Row hovered = null;
        for (int i = 0; i < rows.size(); i++) {
            Entry entry = rows.get(i).entry();
            int y = rowY(i);
            graphics.renderItem(entry.icon(), left, y + 1);
            Component tag = Component.translatable(entry.passive() ? "screen.mitofus.skill_tag_passive" : "screen.mitofus.skill_tag_active");
            UiStyle.text(graphics, font(), entry.name(), left + 20, y, UiStyle.TEXT);
            UiStyle.text(graphics, font(), tag, left + 24 + font().width(entry.name()), y, UiStyle.MUTED);
            UiStyle.text(graphics, font(), entry.detail(), left + 20, y + 10, UiStyle.MUTED);
            if (mouseX >= left && mouseX < left + 180 && mouseY >= y - 2 && mouseY < y + ROW - 4) {
                hovered = rows.get(i);
            }
        }
        scroll.renderBar(graphics, left + width + 3, top + LIST_TOP - 2, VISIBLE_ROWS * ROW - 4, shownCount);
        if (hovered != null) {
            graphics.renderTooltip(font(), font().split(hovered.entry().description(), 180), mouseX, mouseY);
        }
    }
}
