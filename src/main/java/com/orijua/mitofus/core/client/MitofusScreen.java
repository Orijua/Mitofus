package com.orijua.mitofus.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/** The Mitofus window (key K): a panel with one button per tab on top and the active tab below. */
public class MitofusScreen extends Screen implements MitofusStyledScreen {
    /** Width of the window unless the open tab asks for more ({@link MitofusTab#panelWidth}). */
    public static final int PANEL_WIDTH = 320;
    /** Height of the window unless the screen is smaller ({@link PanelFit}). */
    private static final int WANTED_HEIGHT = 210;
    private static final int TAB_BAR_HEIGHT = 26;
    /** Room around a tab title inside its button. */
    private static final int TAB_PADDING = 14;
    private static int lastTab;

    private final List<MitofusTab> tabs;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public MitofusScreen(List<Supplier<MitofusTab>> tabFactories) {
        super(Component.translatable("screen.mitofus.status"));
        this.tabs = tabFactories.stream().map(Supplier::get).toList();
        lastTab = Math.min(lastTab, tabs.size() - 1);
    }

    /** Opens on the first tab of the given type, e.g. the Quests tab when talking to an NPC. */
    public MitofusScreen showing(Class<? extends MitofusTab> type) {
        for (int i = 0; i < tabs.size(); i++) {
            if (type.isInstance(tabs.get(i))) {
                lastTab = i;
            }
        }
        return this;
    }

    private MitofusTab current() {
        return tabs.get(lastTab);
    }

    @Override
    protected void init() {
        // Wide enough for every tab title, so labels are not cut ("Skills", "Habilidades").
        int titlesWidth = 8;
        for (MitofusTab tab : tabs) {
            titlesWidth += font.width(tab.title()) + TAB_PADDING;
        }
        PanelFit.Size size = PanelFit.fit(Math.max(current().panelWidth(), titlesWidth), WANTED_HEIGHT, width, height);
        panelWidth = size.width();
        panelHeight = size.height();
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;

        int tabWidth = (panelWidth - 8) / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            int index = i;
            // On a narrow screen a long title is cut with "…" and its full text is in the hover.
            Component title = tabs.get(i).title();
            Component shown = UiStyle.ellipsize(font, title, tabWidth - TAB_PADDING / 2 - 4);
            Button button = Button.builder(shown, b -> select(index))
                    .bounds(left + 4 + i * tabWidth, top + 4, tabWidth - 2, 18)
                    .tooltip(Tooltip.create(title))
                    .build(MitofusButton::new);
            button.active = i != lastTab;
            addRenderableWidget(button);
        }
        current().init(left + 8, top + TAB_BAR_HEIGHT + 4, panelWidth - 16, panelHeight - TAB_BAR_HEIGHT - 12,
                this::addRenderableWidget);
    }

    private void select(int index) {
        lastTab = index;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        if (current().needsRebuild()) {
            rebuildWidgets();
        }
        current().tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (current().mouseScrolled(scrollY)) {
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && current().mouseClicked(mouseX, mouseY)) {
            rebuildWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            current().mouseDragged(mouseX, mouseY, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean changed = button == 0 && current().mouseReleased(mouseX, mouseY);
        if (changed) {
            rebuildWidgets();
        }
        return super.mouseReleased(mouseX, mouseY, button) || changed;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiStyle.panel(graphics, left, top, panelWidth, panelHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        current().render(graphics, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
