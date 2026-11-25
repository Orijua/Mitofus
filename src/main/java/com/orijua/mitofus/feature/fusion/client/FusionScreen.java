package com.orijua.mitofus.feature.fusion.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusConfirmScreen;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.PanelFit;
import com.orijua.mitofus.core.client.ScrollList;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.fusion.AbsorbClassPayload;
import com.orijua.mitofus.feature.fusion.FuseClassesPayload;
import com.orijua.mitofus.feature.fusion.FusionDefinition;
import com.orijua.mitofus.feature.fusion.FusionRules;
import com.orijua.mitofus.feature.fusion.FusionService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;

/**
 * The list of every fusion, from the start: the ones that can be done now are clickable; the others are greyed with what is missing.
 * Clicking one asks for confirmation first, because a fusion cannot be undone.
 */
public class FusionScreen extends Screen implements MitofusStyledScreen {
    private static final int WIDTH = 250;
    private static final int PADDING = 10;
    private static final int ROW = 34;

    private final Screen parent;
    /** One line of the list: a fusion of two classes, or a hybrid absorbing a lone class. */
    private record Item(Component name, java.util.function.Supplier<Set<FusionRules.Missing>> missing, long cost, Component title,
                        Component message, Runnable send) {
    }

    private List<Item> fusions = List.of();
    /** The class the list is filtered by (only the fusions it takes part in are shown), or null for every fusion. */
    private net.minecraft.resources.ResourceLocation selected;
    private int left;
    private int top;
    private int panelHeight;
    private final ScrollList scroll = new ScrollList(1);
    /** Everything in the window that is not a fusion row: padding, title, filters, the back button. */
    private static final int CHROME = PADDING + UiStyle.LINE + 6 + 22 + 22 + PADDING;
    private final java.util.List<Button> buttons = new java.util.ArrayList<>();

    public FusionScreen(Screen parent) {
        super(Component.translatable("screen.mitofus.fusions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var level = minecraft.level;
        var placed = minecraft.player.getData(com.orijua.mitofus.core.registry.ModAttachments.PLAYER_CLASSES).slots().stream()
                .filter(slot -> !slot.isHybrid()).map(com.orijua.mitofus.feature.progression.ClassSlot::classId).toList();
        if (selected != null && !placed.contains(selected)) {
            selected = null;
        }
        List<Item> items = new java.util.ArrayList<>();
        for (Definitions.Entry<FusionDefinition> entry : Definitions.all(level, ModDatapackRegistries.FUSION)) {
            FusionDefinition fusion = entry.value();
            if (selected != null && !fusion.parents().contains(selected)) {
                continue;
            }
            Component name = Component.translatable("screen.mitofus.hybrid_name", FusionText.className(level, fusion.resultClass()),
                    FusionText.className(level, fusion.parents().get(0)), FusionText.className(level, fusion.parents().get(1)));
            Component message = Component.translatable("screen.mitofus.fusion_confirm", FusionText.className(level, fusion.parents().get(0)),
                    FusionText.className(level, fusion.parents().get(1)), FusionText.className(level, fusion.resultClass()),
                    MoneyText.iron(FusionService.costInIron(fusion)));
            items.add(new Item(name, () -> FusionService.missing(minecraft.player, fusion), FusionService.costInIron(fusion),
                    Component.translatable("screen.mitofus.fusion_confirm_title"), message,
                    () -> PacketDistributor.sendToServer(new FuseClassesPayload(entry.id()))));
        }
        var classes = minecraft.player.getData(com.orijua.mitofus.core.registry.ModAttachments.PLAYER_CLASSES);
        for (var hybrid : classes.slots().stream().filter(com.orijua.mitofus.feature.progression.ClassSlot::isHybrid).toList()) {
            for (var lone : placed) {
                var cost = FusionService.absorbCost(minecraft.player, lone);
                if (cost.isEmpty() || (selected != null && !selected.equals(lone)) || hybrid.origin().contains(lone)) {
                    continue;
                }
                Component name = Component.translatable("screen.mitofus.absorb_name", FusionText.className(level, hybrid.classId()),
                        FusionText.className(level, lone));
                Component message = Component.translatable("screen.mitofus.absorb_confirm", FusionText.className(level, lone),
                        FusionText.className(level, hybrid.classId()), MoneyText.iron(cost.get()));
                int slot = hybrid.slot();
                items.add(new Item(name, () -> FusionService.missingAbsorb(minecraft.player, slot, lone), cost.get(),
                        Component.translatable("screen.mitofus.absorb_title"), message,
                        () -> PacketDistributor.sendToServer(new AbsorbClassPayload(slot, lone))));
            }
        }
        fusions = items;
        buttons.clear();
        int fitting = PanelFit.rows(height - 2 * PanelFit.MARGIN - CHROME, ROW);
        int shown = Math.max(1, Math.min(fusions.size(), fitting));
        scroll.setVisibleRows(shown);
        scroll.clamp(fusions.size());
        panelHeight = CHROME + shown * ROW;
        left = (width - WIDTH) / 2;
        top = (height - panelHeight) / 2;
        int filterY = top + PADDING + UiStyle.LINE + 4;
        if (!placed.isEmpty()) {
            int filterWidth = (WIDTH - 2 * PADDING - (placed.size() - 1) * 4) / placed.size();
            for (int i = 0; i < placed.size(); i++) {
                var id = placed.get(i);
                Component label = FusionText.className(level, id);
                if (id.equals(selected)) {
                    label = Component.literal("✔ ").append(label);
                }
                addRenderableWidget(Button.builder(label, b -> {
                    selected = id.equals(selected) ? null : id;
                    rebuildWidgets();
                }).bounds(left + PADDING + i * (filterWidth + 4), filterY, filterWidth, 16).build(MitofusButton::new));
            }
        }
        int y = filterY + 22;
        for (int i = scroll.first(); i < scroll.end(fusions.size()); i++) {
            Item item = fusions.get(i);
            boolean available = item.missing().get().isEmpty();
            Button button = Button.builder(item.name(), b -> confirm(item))
                    .bounds(left + PADDING, y, WIDTH - 2 * PADDING, 18).build(MitofusButton::new);
            button.active = available;
            addRenderableWidget(button);
            buttons.add(button);
            y += ROW;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(left + WIDTH / 2 - 40, top + panelHeight - PADDING - 18, 80, 18).build(MitofusButton::new));
    }

    private void confirm(Item item) {
        Minecraft.getInstance().setScreen(new MitofusConfirmScreen(this, item.title(), item.message(), item.send()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiStyle.panel(graphics, left, top, WIDTH, panelHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + PADDING, UiStyle.HEADING);
        if (fusions.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.mitofus.fusions_none"), left + WIDTH / 2, top + PADDING + UiStyle.LINE + 12, UiStyle.MUTED);
            return;
        }
        int y = top + PADDING + UiStyle.LINE + 4 + 22 + 20;
        for (int i = scroll.first(); i < scroll.end(fusions.size()); i++) {
            Item item = fusions.get(i);
            Set<FusionRules.Missing> missing = item.missing().get();
            Component status = missing.isEmpty()
                    ? Component.translatable("screen.mitofus.fusion_ready").append(" · ").append(Component.translatable("screen.mitofus.fusion_cost", MoneyText.iron(item.cost())))
                    : Component.translatable("screen.mitofus.fusion_missing." + missing.iterator().next().name().toLowerCase());
            graphics.drawString(font, font.plainSubstrByWidth(status.getString(), WIDTH - 2 * PADDING), left + PADDING, y, missing.isEmpty() ? UiStyle.GOOD : UiStyle.MUTED);
            y += ROW;
        }
        scroll.renderBar(graphics, left + WIDTH - PADDING + 3, top + PADDING + UiStyle.LINE + 4 + 22, scroll.visibleRows() * ROW - 2, fusions.size());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scroll.scroll(scrollY, fusions.size())) {
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).active = fusions.get(scroll.first() + i).missing().get().isEmpty();
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
