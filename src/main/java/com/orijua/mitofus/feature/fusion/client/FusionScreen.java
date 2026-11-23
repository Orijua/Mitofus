package com.orijua.mitofus.feature.fusion.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusConfirmScreen;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.economy.MoneyText;
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
    private List<Definitions.Entry<FusionDefinition>> fusions = List.of();
    private int left;
    private int top;
    private int panelHeight;
    private final java.util.List<Button> buttons = new java.util.ArrayList<>();

    public FusionScreen(Screen parent) {
        super(Component.translatable("screen.mitofus.fusions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var level = minecraft.level;
        fusions = Definitions.all(level, ModDatapackRegistries.FUSION);
        buttons.clear();
        panelHeight = PADDING + UiStyle.LINE + 6 + Math.max(1, fusions.size()) * ROW + 22 + PADDING;
        panelHeight = Math.min(panelHeight, height - 10);
        left = (width - WIDTH) / 2;
        top = (height - panelHeight) / 2;
        int y = top + PADDING + UiStyle.LINE + 6;
        for (Definitions.Entry<FusionDefinition> entry : fusions) {
            FusionDefinition fusion = entry.value();
            Component name = Component.translatable("screen.mitofus.hybrid_name", FusionText.className(level, fusion.resultClass()),
                    FusionText.className(level, fusion.parents().get(0)), FusionText.className(level, fusion.parents().get(1)));
            boolean available = FusionService.missing(minecraft.player, fusion).isEmpty();
            Button button = Button.builder(name, b -> confirm(entry.id(), fusion))
                    .bounds(left + PADDING, y, WIDTH - 2 * PADDING, 18).build(MitofusButton::new);
            button.active = available;
            addRenderableWidget(button);
            buttons.add(button);
            y += ROW;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(left + WIDTH / 2 - 40, top + panelHeight - PADDING - 18, 80, 18).build(MitofusButton::new));
    }

    private void confirm(net.minecraft.resources.ResourceLocation id, FusionDefinition fusion) {
        var level = minecraft.level;
        Component message = Component.translatable("screen.mitofus.fusion_confirm", FusionText.className(level, fusion.parents().get(0)),
                FusionText.className(level, fusion.parents().get(1)), FusionText.className(level, fusion.resultClass()),
                MoneyText.iron(FusionService.costInIron(fusion)));
        Minecraft.getInstance().setScreen(new MitofusConfirmScreen(this, Component.translatable("screen.mitofus.fusion_confirm_title"), message,
                () -> PacketDistributor.sendToServer(new FuseClassesPayload(id))));
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
        int y = top + PADDING + UiStyle.LINE + 6 + 20;
        for (Definitions.Entry<FusionDefinition> entry : fusions) {
            Set<FusionRules.Missing> missing = FusionService.missing(minecraft.player, entry.value());
            Component status = missing.isEmpty()
                    ? Component.translatable("screen.mitofus.fusion_ready").append(" · ").append(Component.translatable("screen.mitofus.fusion_cost", MoneyText.iron(FusionService.costInIron(entry.value()))))
                    : Component.translatable("screen.mitofus.fusion_missing." + missing.iterator().next().name().toLowerCase());
            graphics.drawString(font, font.plainSubstrByWidth(status.getString(), WIDTH - 2 * PADDING), left + PADDING, y, missing.isEmpty() ? UiStyle.GOOD : UiStyle.MUTED);
            y += ROW;
        }
    }

    @Override
    public void tick() {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).active = FusionService.missing(minecraft.player, fusions.get(i).value()).isEmpty();
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
