package com.orijua.mitofus.feature.talent.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusTab;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.synergy.client.SynergyText;
import com.orijua.mitofus.feature.talent.BuyTalentPayload;
import com.orijua.mitofus.feature.talent.TalentDefinition;
import com.orijua.mitofus.feature.talent.TalentRules;
import com.orijua.mitofus.feature.talent.TalentService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The talent tree of one class at a time (one button per class in a slot). Every node is shown from the start: owned ones in gold,
 * ones that can be bought now in green and the rest greyed. Click a node to buy it; the server checks everything.
 */
public class TalentsTab extends MitofusTab {
    private static final int NODE = 16;
    private static final int CELL_X = 26;
    private static final int CELL_Y = 21;
    private static final int HEADER = 24;
    private static final int OWNED = 0xFFFFD700;
    private static final int BUYABLE = 0xFF6CFF7A;
    private static final int LOCKED = 0xFF6F7C8C;

    /** Position of the shown class in the player's slot list. */
    private int selected;
    /** How far the player has dragged the tree; it is drawn clipped to the window below the header. */
    private final TreePan pan = new TreePan();
    private int shownSelected = -1;
    /** What the widgets were built for: a class placed or removed rebuilds the tab. */
    private String builtFor = "";

    public TalentsTab() {
        super(Component.translatable("tab.mitofus.talents"));
    }

    private static List<ClassSlot> slots() {
        return player().getData(ModAttachments.PLAYER_CLASSES).slots();
    }

    private static String signature() {
        return slots().stream().map(slot -> slot.slot() + ":" + slot.classId()).toList().toString();
    }

    private Optional<ClassSlot> shown() {
        List<ClassSlot> slots = slots();
        return slots.isEmpty() ? Optional.empty() : Optional.of(slots.get(Math.min(selected, slots.size() - 1)));
    }

    @Override
    protected void addWidgets(Consumer<AbstractWidget> addWidget) {
        builtFor = signature();
        List<ClassSlot> slots = slots();
        int buttonWidth = 78;
        for (int i = 0; i < slots.size(); i++) {
            int index = i;
            Component name = SynergyText.className(player().level(), slots.get(i).classId());
            addWidget.accept(Button.builder(name, b -> {
                selected = index;
                pan.reset();
            })
                    .bounds(left + i * (buttonWidth + 4), top, buttonWidth, 18).build(MitofusButton::new));
        }
    }

    @Override
    public boolean needsRebuild() {
        return !builtFor.equals(signature());
    }

    private Map<ResourceLocation, TalentDefinition> tree(ClassSlot slot) {
        Map<ResourceLocation, TalentDefinition> mine = new java.util.HashMap<>();
        TalentService.all(player().level()).forEach((id, node) -> {
            if (slot.classId().equals(node.playerClass())) {
                mine.put(id, node);
            }
        });
        return mine;
    }

    private int nodeX(TalentDefinition node) {
        return left + width / 2 + (node.x()) * CELL_X - NODE / 2 + pan.x();
    }

    private int nodeY(TalentDefinition node) {
        return top + HEADER + 4 + (node.y()) * CELL_Y + pan.y();
    }

    /** Whether a point is in the part of the window where the tree is drawn (below the header). */
    private boolean inTreeArea(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top + HEADER && mouseY < top + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (shown().isPresent() && inTreeArea(mouseX, mouseY)) {
            pan.press(mouseX, mouseY);
        }
        return false;
    }

    @Override
    public void mouseDragged(double mouseX, double mouseY, double dx, double dy) {
        pan.drag(mouseX, mouseY, dx, dy);
    }

    /** A press that did not move buys the node under the mouse; one that moved only dragged the tree. */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY) {
        if (!pan.release()) {
            return false;
        }
        Optional<ClassSlot> slot = shown();
        if (slot.isEmpty() || !inTreeArea(mouseX, mouseY)) {
            return false;
        }
        for (Map.Entry<ResourceLocation, TalentDefinition> entry : tree(slot.get()).entrySet()) {
            if (isOver(entry.getValue(), mouseX, mouseY)) {
                PacketDistributor.sendToServer(new BuyTalentPayload(slot.get().slot(), entry.getKey()));
                return false;
            }
        }
        return false;
    }

    private boolean isOver(TalentDefinition node, double mouseX, double mouseY) {
        int x = nodeX(node);
        int y = nodeY(node);
        return inTreeArea(mouseX, mouseY) && mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Optional<ClassSlot> shown = shown();
        if (shown.isEmpty()) {
            UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.talents_no_class"), left, top + 6, UiStyle.MUTED);
            return;
        }
        ClassSlot slot = shown.get();
        Map<ResourceLocation, TalentDefinition> tree = tree(slot);
        Map<ResourceLocation, TalentDefinition> all = TalentService.all(player().level());
        int points = TalentRules.pointsAvailable(all, slot);
        Component pointsText = Component.translatable("screen.mitofus.talent_points", points);
        UiStyle.text(graphics, font(), pointsText, left + width - font().width(pointsText), top + 5, points > 0 ? UiStyle.GOOD : UiStyle.MUTED);
        if (tree.isEmpty()) {
            UiStyle.text(graphics, font(), Component.translatable("screen.mitofus.talents_no_tree"), left, top + HEADER + 6, UiStyle.MUTED);
            return;
        }
        if (shownSelected != selected) {
            shownSelected = selected;
            pan.reset();
        }
        clampPan(tree);
        var owned = TalentRules.owned(all, slot);
        graphics.enableScissor(left, top + HEADER, left + width, top + height);
        // Lines first, so the nodes sit on top of them.
        tree.forEach((id, node) -> node.requires().forEach(requiredId -> {
            TalentDefinition required = tree.get(requiredId);
            if (required != null) {
                connect(graphics, required, node, owned.contains(requiredId) && owned.contains(id) ? OWNED : LOCKED);
            }
        }));
        List<Component> tooltip = null;
        for (Map.Entry<ResourceLocation, TalentDefinition> entry : tree.entrySet()) {
            TalentDefinition node = entry.getValue();
            TalentRules.Buy status = TalentRules.check(all, slot, entry.getKey());
            boolean isOwned = owned.contains(entry.getKey());
            int color = isOwned ? OWNED : status == TalentRules.Buy.OK ? BUYABLE : LOCKED;
            int x = nodeX(node);
            int y = nodeY(node);
            graphics.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, color);
            graphics.fill(x, y, x + NODE, y + NODE, 0xFF1B2433);
            node.icon().ifPresent(item -> graphics.renderItem(new ItemStack(item), x, y));
            if (!isOwned && status != TalentRules.Buy.OK) {
                graphics.fill(x, y, x + NODE, y + NODE, 0x88101820);
            }
            if (isOver(node, mouseX, mouseY)) {
                tooltip = tooltip(node, status, isOwned);
            }
        }
        graphics.disableScissor();
        if (tooltip != null) {
            graphics.renderComponentTooltip(font(), tooltip, mouseX, mouseY);
        }
    }

    /** Keeps the dragged tree within reach of the window, from where it is drawn with no drag. */
    private void clampPan(Map<ResourceLocation, TalentDefinition> tree) {
        int dragX = pan.x();
        int dragY = pan.y();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (TalentDefinition node : tree.values()) {
            minX = Math.min(minX, nodeX(node) - dragX);
            maxX = Math.max(maxX, nodeX(node) - dragX + NODE);
            minY = Math.min(minY, nodeY(node) - dragY);
            maxY = Math.max(maxY, nodeY(node) - dragY + NODE);
        }
        pan.clamp(minX, maxX, minY, maxY, left, left + width, top + HEADER, top + height);
    }

    private void connect(GuiGraphics graphics, TalentDefinition from, TalentDefinition to, int color) {
        int x1 = nodeX(from) + NODE / 2;
        int y1 = nodeY(from) + NODE / 2;
        int x2 = nodeX(to) + NODE / 2;
        int y2 = nodeY(to) + NODE / 2;
        graphics.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 1, color);
        graphics.fill(x2, Math.min(y1, y2), x2 + 1, Math.max(y1, y2) + 1, color);
    }

    private static List<Component> tooltip(TalentDefinition node, TalentRules.Buy status, boolean owned) {
        List<Component> lines = new ArrayList<>();
        lines.add(node.name().copy().withStyle(owned ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.WHITE));
        lines.add(node.description().copy().withStyle(net.minecraft.ChatFormatting.GRAY));
        if (!owned) {
            lines.add(Component.translatable("screen.mitofus.talent_cost", node.cost()).withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        if (node.minGrade().ordinal() > 0) {
            lines.add(Component.translatable("screen.mitofus.talent_grade", node.minGrade().displayName()).withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
        Component state = switch (status) {
            case OWNED -> Component.translatable("screen.mitofus.talent_owned").withStyle(net.minecraft.ChatFormatting.GOLD);
            case OK -> Component.translatable("screen.mitofus.talent_click").withStyle(net.minecraft.ChatFormatting.GREEN);
            case LOCKED -> Component.translatable("screen.mitofus.talent_locked").withStyle(net.minecraft.ChatFormatting.RED);
            case GRADE -> Component.translatable("screen.mitofus.talent_needs_grade").withStyle(net.minecraft.ChatFormatting.RED);
            case POINTS -> Component.translatable("screen.mitofus.talent_needs_points").withStyle(net.minecraft.ChatFormatting.RED);
            default -> Component.empty();
        };
        lines.add(state);
        return lines;
    }
}
