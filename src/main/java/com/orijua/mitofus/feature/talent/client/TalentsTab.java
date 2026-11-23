package com.orijua.mitofus.feature.talent.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusTab;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.fusion.client.FusionText;
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
            Component name = FusionText.className(player().level(), slots.get(i).classId());
            addWidget.accept(Button.builder(name, b -> selected = index)
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
            if (slot.treeClasses().contains(node.playerClass())) {
                mine.put(id, node);
            }
        });
        offsets = offsets(slot, mine);
        return mine;
    }

    /** Where each class's nodes are moved on screen: nowhere for a normal class; for a hybrid, one parent to the left, one to the right and the trunk below. */
    private Map<ResourceLocation, int[]> offsets = Map.of();

    private static Map<ResourceLocation, int[]> offsets(ClassSlot slot, Map<ResourceLocation, TalentDefinition> tree) {
        Map<ResourceLocation, int[]> result = new java.util.HashMap<>();
        if (!slot.isHybrid()) {
            return result;
        }
        int lowestParentRow = 0;
        for (int i = 0; i < slot.origin().size(); i++) {
            ResourceLocation parent = slot.origin().get(i);
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            for (TalentDefinition node : tree.values()) {
                if (node.playerClass().equals(parent)) {
                    minX = Math.min(minX, node.x());
                    maxX = Math.max(maxX, node.x());
                    lowestParentRow = Math.max(lowestParentRow, node.y());
                }
            }
            if (maxX != Integer.MIN_VALUE) {
                result.put(parent, new int[]{i == 0 ? -1 - maxX : 1 - minX, 0});
            }
        }
        result.put(slot.classId(), new int[]{0, lowestParentRow + 2});
        return result;
    }

    private int offsetX(TalentDefinition node) {
        int[] offset = offsets.get(node.playerClass());
        return offset == null ? 0 : offset[0];
    }

    private int offsetY(TalentDefinition node) {
        int[] offset = offsets.get(node.playerClass());
        return offset == null ? 0 : offset[1];
    }

    private int nodeX(TalentDefinition node) {
        return left + width / 2 + (node.x() + offsetX(node)) * CELL_X - NODE / 2;
    }

    private int nodeY(TalentDefinition node) {
        return top + HEADER + 4 + (node.y() + offsetY(node)) * CELL_Y;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        Optional<ClassSlot> slot = shown();
        if (slot.isEmpty()) {
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
        return mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE;
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
        var owned = TalentRules.owned(all, slot);
        // Lines first, so the nodes sit on top of them.
        tree.forEach((id, node) -> node.requires().forEach(requiredId -> {
            TalentDefinition required = tree.get(requiredId);
            if (required != null) {
                connect(graphics, required, node, owned.contains(requiredId) && owned.contains(id) ? OWNED : LOCKED);
            }
        }));
        // The two parent trees join the merge node of the hybrid: their last nodes (the ones nothing needs) lead to it.
        if (slot.isHybrid()) {
            tree.entrySet().stream().filter(e -> e.getValue().playerClass().equals(slot.classId()) && e.getValue().isRoot()).findFirst().ifPresent(merge ->
                    tree.forEach((id, node) -> {
                        boolean parent = slot.origin().contains(node.playerClass());
                        boolean leaf = tree.values().stream().noneMatch(other -> other.requires().contains(id));
                        if (parent && leaf) {
                            connect(graphics, node, merge.getValue(), owned.contains(id) ? OWNED : LOCKED);
                        }
                    }));
            Component origin = Component.translatable("screen.mitofus.talent_trunk_origin", Component.translatable("screen.mitofus.hybrid_origin",
                    FusionText.className(player().level(), slot.origin().get(0)), FusionText.className(player().level(), slot.origin().get(1))));
            UiStyle.text(graphics, font(), origin, left, top + HEADER - 5, UiStyle.MUTED);
        }
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
        if (tooltip != null) {
            graphics.renderComponentTooltip(font(), tooltip, mouseX, mouseY);
        }
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
