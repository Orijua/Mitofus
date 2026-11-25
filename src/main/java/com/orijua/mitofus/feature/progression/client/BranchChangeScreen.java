package com.orijua.mitofus.feature.progression.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusConfirmScreen;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.progression.ChangeBranchPayload;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.ClassSlotRules;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.synergy.client.SynergyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The village chief's "change branch" window: one block per open slot with the branch it holds and a button for every other branch
 * that sits in no slot. A branch that sleeps shows its level, because it comes back as it was. Clicking asks for confirmation first.
 */
public class BranchChangeScreen extends Screen implements MitofusStyledScreen {
    private static final int WIDTH = 270;
    private static final int PADDING = 10;
    private static final int BLOCK = 58;

    private final Screen parent;
    private final int npc;
    private int left;
    private int top;
    private int panelHeight;

    public BranchChangeScreen(Screen parent, int npc) {
        super(Component.translatable("screen.mitofus.branch_change"));
        this.parent = parent;
        this.npc = npc;
    }

    private List<Definitions.Entry<PlayerClassDefinition>> options = List.of();

    private PlayerClasses classes() {
        return minecraft.player.getData(ModAttachments.PLAYER_CLASSES);
    }

    @Override
    protected void init() {
        PlayerClasses classes = classes();
        List<ClassSlot> held = classes.slots();
        panelHeight = PADDING + UiStyle.LINE + 8 + Math.max(1, held.size()) * BLOCK + 22 + PADDING;
        left = (width - WIDTH) / 2;
        top = (height - panelHeight) / 2;
        options = Definitions.all(minecraft.level, ModDatapackRegistries.PLAYER_CLASS).stream()
                .filter(entry -> entry.value().selectable() && !classes.hasClass(entry.id())).toList();
        int y = top + PADDING + UiStyle.LINE + 8;
        for (ClassSlot slot : held) {
            if (!options.isEmpty()) {
                int buttonWidth = (WIDTH - 2 * PADDING - (options.size() - 1) * 4) / options.size();
                for (int i = 0; i < options.size(); i++) {
                    Definitions.Entry<PlayerClassDefinition> option = options.get(i);
                    Component label = classes.dormant(option.id()).map(sleeping -> (Component) Component.empty().append(option.value().name())
                            .append(Component.translatable("screen.mitofus.branch_dormant_level", sleeping.classLevel()))).orElse(option.value().name());
                    Button button = Button.builder(label, b -> confirm(slot, option))
                            .bounds(left + PADDING + i * (buttonWidth + 4), y + 22, buttonWidth, 18).build(MitofusButton::new);
                    button.active = ClassSlotRules.checkSwap(classes, slot.slot(), option.id(),
                            ProgressionService.get(minecraft.player).level()) == ClassSlotRules.Swap.OK;
                    addRenderableWidget(button);
                }
            }
            y += BLOCK;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(left + WIDTH / 2 - 40, top + panelHeight - PADDING - 18, 80, 18).build(MitofusButton::new));
    }

    private void confirm(ClassSlot slot, Definitions.Entry<PlayerClassDefinition> option) {
        int cost = ClassSlotRules.swapCost(classes(), slot.slot(), option.value().changeCost());
        Component price = cost == 0 ? Component.translatable("screen.mitofus.branch_free") : MoneyText.iron(Money.iron(cost));
        Component message = Component.translatable("screen.mitofus.branch_confirm", SynergyText.className(minecraft.level, slot.classId()),
                option.value().name(), price);
        Minecraft.getInstance().setScreen(new MitofusConfirmScreen(this, Component.translatable("screen.mitofus.branch_confirm_title"), message,
                () -> PacketDistributor.sendToServer(new ChangeBranchPayload(npc, slot.slot(), option.id()))));
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
        PlayerClasses classes = classes();
        if (classes.slots().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.mitofus.branch_none"), left + WIDTH / 2, top + PADDING + UiStyle.LINE + 12, UiStyle.MUTED);
            return;
        }
        int y = top + PADDING + UiStyle.LINE + 8;
        for (ClassSlot slot : classes.slots()) {
            Component name = SynergyText.className(minecraft.level, slot.classId());
            graphics.drawString(font, Component.translatable("screen.mitofus.branch_slot", slot.slot() + 1, name, slot.classLevel()),
                    left + PADDING, y, UiStyle.TEXT);
            int cheapest = options.stream().mapToInt(o -> o.value().changeCost()).min().orElse(PlayerClassDefinition.DEFAULT_CHANGE_COST);
            int cost = ClassSlotRules.swapCost(classes, slot.slot(), cheapest);
            graphics.drawString(font, cost == 0 ? Component.translatable("screen.mitofus.branch_first_free")
                    : Component.translatable("screen.mitofus.branch_costs", MoneyText.iron(Money.iron(cost))), left + PADDING, y + 11, UiStyle.MUTED);
            y += BLOCK;
        }
    }

    @Override
    public void tick() {
        // The purse or the slots may have changed since the window opened: rebuild so the buttons follow.
        if (minecraft.player != null && !classes().equals(shown)) {
            shown = classes();
            rebuildWidgets();
        }
    }

    private PlayerClasses shown = PlayerClasses.DEFAULT;

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
