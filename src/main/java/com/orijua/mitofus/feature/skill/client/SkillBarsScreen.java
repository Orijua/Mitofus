package com.orijua.mitofus.feature.skill.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.ScrollList;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.skill.AssignSkillPayload;
import com.orijua.mitofus.feature.skill.PlayerSkills;
import com.orijua.mitofus.feature.skill.SkillDefinition;
import com.orijua.mitofus.feature.skill.SlotRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/**
 * The window to put skills on the bars, opened from the Skills tab so that tab only lists skills. On the left the skills the
 * player knows; on the right the slots of the page, three by three. Pick a skill and then a slot; right-click a slot to empty it.
 * Client only.
 */
public final class SkillBarsScreen extends Screen implements MitofusStyledScreen {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 196;
    private static final int PADDING = 10;
    private static final int LIST_WIDTH = 150;
    private static final int ROW = 20;
    private static final int VISIBLE_ROWS = 7;
    private static final int CELL = 36;
    private static final int CELL_GAP = 4;
    private static final int GRID = 3;
    private static final int CLOSED = 0xA0101018;
    private static final int OPEN = 0xA0303848;
    private static final int OPEN_HOVER = 0xC04A5878;
    private static final int PICKED = 0xA05A4A18;

    private final Screen parent;
    private final ScrollList scroll = new ScrollList(VISIBLE_ROWS);
    private ResourceLocation picked;
    private int page;
    private int left;
    private int top;
    private Button previousPage;
    private Button nextPage;
    private List<FormattedCharSequence> hint = List.of();

    public SkillBarsScreen(Screen parent) {
        super(Component.translatable("screen.mitofus.skill_bars_title"));
        this.parent = parent;
    }

    private static PlayerSkills skills() {
        return Minecraft.getInstance().player.getData(ModAttachments.PLAYER_SKILLS);
    }

    private static int level() {
        return ProgressionService.get(Minecraft.getInstance().player).level();
    }

    private static Optional<SkillDefinition> definition(ResourceLocation id) {
        return Definitions.get(Minecraft.getInstance().level, ModDatapackRegistries.SKILL, id);
    }

    private int pages() {
        return SlotRules.pages(level());
    }

    private int listLeft() {
        return left + PADDING;
    }

    private int listTop() {
        return top + PADDING + UiStyle.LINE + 8;
    }

    private int gridLeft() {
        return left + WIDTH - PADDING - (GRID * CELL + (GRID - 1) * CELL_GAP);
    }

    private int gridTop() {
        return listTop() + 18;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        page = Math.clamp(page, 0, pages() - 1);
        hint = font.split(Component.translatable("screen.mitofus.skill_bars_hint"), WIDTH - 2 * PADDING);
        int gridWidth = GRID * CELL + (GRID - 1) * CELL_GAP;
        previousPage = addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1))
                .bounds(gridLeft(), listTop() - 2, 18, 16).build(MitofusButton::new));
        nextPage = addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(pages() - 1, page + 1))
                .bounds(gridLeft() + gridWidth - 18, listTop() - 2, 18, 16).build(MitofusButton::new));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(left + WIDTH / 2 - 40, top + HEIGHT - PADDING - 18, 80, 18).build(MitofusButton::new));
    }

    @Override
    public void tick() {
        previousPage.active = page > 0;
        nextPage.active = page < pages() - 1;
        previousPage.visible = nextPage.visible = pages() > 1;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiStyle.panel(graphics, left, top, WIDTH, HEIGHT);
    }

    private int slotAt(double mouseX, double mouseY) {
        for (int cell = 0; cell < GRID * GRID; cell++) {
            int x = gridLeft() + (cell % GRID) * (CELL + CELL_GAP);
            int y = gridTop() + (cell / GRID) * (CELL + CELL_GAP);
            if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                return page * SlotRules.PAGE_SIZE + cell;
            }
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + PADDING, UiStyle.HEADING);
        graphics.fill(left + PADDING, top + PADDING + UiStyle.LINE, left + WIDTH - PADDING, top + PADDING + UiStyle.LINE + 1, UiStyle.LINE_SOFT);

        PlayerSkills skills = skills();
        List<ResourceLocation> learned = skills.learned();
        scroll.clamp(learned.size());
        Component tooltip = null;
        for (int i = scroll.first(); i < scroll.end(learned.size()); i++) {
            ResourceLocation id = learned.get(i);
            Optional<SkillDefinition> skill = definition(id);
            if (skill.isEmpty()) {
                continue;
            }
            int y = listTop() + (i - scroll.first()) * ROW;
            boolean hover = mouseX >= listLeft() && mouseX < listLeft() + LIST_WIDTH && mouseY >= y && mouseY < y + ROW - 2;
            graphics.fill(listLeft(), y, listLeft() + LIST_WIDTH, y + ROW - 2, id.equals(picked) ? PICKED : hover ? OPEN_HOVER : OPEN);
            graphics.renderItem(skill.get().iconStack(), listLeft() + 2, y + 1);
            UiStyle.text(graphics, font, UiStyle.ellipsize(font, skill.get().name(), LIST_WIDTH - 44), listLeft() + 22, y + 2, UiStyle.TEXT);
            int slot = skills.slots().indexOf(Optional.of(id));
            Component where = slot < 0 ? Component.translatable("screen.mitofus.skill_slot_none")
                    : Component.translatable("screen.mitofus.skill_slot", slot / SlotRules.PAGE_SIZE + 1, slot % SlotRules.PAGE_SIZE + 1);
            UiStyle.text(graphics, font, where, listLeft() + 22, y + 11, UiStyle.MUTED);
            if (hover) {
                tooltip = skill.get().description();
            }
        }
        if (learned.isEmpty()) {
            UiStyle.text(graphics, font, Component.translatable("screen.mitofus.no_skills"), listLeft(), listTop(), UiStyle.MUTED);
        }
        scroll.renderBar(graphics, listLeft() + LIST_WIDTH + 3, listTop(), VISIBLE_ROWS * ROW - 2, learned.size());

        if (pages() > 1) {
            Component pageLabel = Component.translatable("screen.mitofus.skill_bars_page", page + 1, pages());
            graphics.drawCenteredString(font, pageLabel, gridLeft() + (GRID * CELL + (GRID - 1) * CELL_GAP) / 2, listTop() + 2, UiStyle.MUTED);
        }
        int hoveredSlot = slotAt(mouseX, mouseY);
        for (int cell = 0; cell < GRID * GRID; cell++) {
            int slot = page * SlotRules.PAGE_SIZE + cell;
            int x = gridLeft() + (cell % GRID) * (CELL + CELL_GAP);
            int y = gridTop() + (cell / GRID) * (CELL + CELL_GAP);
            boolean open = SlotRules.isOpen(level(), slot);
            graphics.fill(x, y, x + CELL, y + CELL, !open ? CLOSED : slot == hoveredSlot && picked != null ? OPEN_HOVER : OPEN);
            Optional<ResourceLocation> inSlot = open ? skills.slot(slot) : Optional.empty();
            if (inSlot.isPresent()) {
                definition(inSlot.get()).ifPresent(skill -> graphics.renderItem(skill.iconStack(), x + (CELL - 16) / 2, y + (CELL - 16) / 2));
            }
            UiStyle.text(graphics, font, Component.literal(open ? String.valueOf(cell + 1) : "×"), x + 3, y + 3, open ? UiStyle.MUTED : UiStyle.BAD);
            if (slot == hoveredSlot) {
                if (!open) {
                    tooltip = Component.translatable("screen.mitofus.skill_bars_locked");
                } else if (inSlot.isPresent()) {
                    tooltip = definition(inSlot.get()).map(SkillDefinition::name).orElse(null);
                }
            }
        }
        int hintY = gridTop() + GRID * (CELL + CELL_GAP) + 2;
        for (FormattedCharSequence line : hint) {
            graphics.drawString(font, line, left + PADDING, hintY, UiStyle.MUTED);
            hintY += 10;
        }
        if (tooltip != null) {
            graphics.renderTooltip(font, font.split(tooltip, 180), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        List<ResourceLocation> learned = skills().learned();
        if (button == 0 && mouseX >= listLeft() && mouseX < listLeft() + LIST_WIDTH && mouseY >= listTop()) {
            int index = scroll.first() + (int) ((mouseY - listTop()) / ROW);
            if (index >= 0 && index < scroll.end(learned.size())) {
                ResourceLocation id = learned.get(index);
                picked = id.equals(picked) ? null : id;
                return true;
            }
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0 && SlotRules.isOpen(level(), slot)) {
            if (button == 1) {
                PacketDistributor.sendToServer(new AssignSkillPayload(slot, Optional.empty()));
                return true;
            }
            if (button == 0) {
                if (picked != null) {
                    PacketDistributor.sendToServer(new AssignSkillPayload(slot, Optional.of(picked)));
                } else if (skills().slot(slot).isPresent()) {
                    // With nothing picked, a click on a filled slot picks its skill up to move it.
                    picked = skills().slot(slot).get();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scroll.scroll(scrollY, skills().learned().size()) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
