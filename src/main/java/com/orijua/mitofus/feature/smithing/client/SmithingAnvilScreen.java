package com.orijua.mitofus.feature.smithing.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.EnhanceBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.equipment.EquipLevelRules;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import com.orijua.mitofus.feature.equipment.AppraisalService;
import com.orijua.mitofus.feature.equipment.EnhanceMaterial;
import com.orijua.mitofus.feature.equipment.EnhanceRules;
import com.orijua.mitofus.feature.equipment.EquipmentRules;
import com.orijua.mitofus.feature.equipment.EquipmentService;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.RatingRoller;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.smithing.ForgingExtras;
import com.orijua.mitofus.feature.smithing.ForgingMinigame;
import com.orijua.mitofus.feature.smithing.ForgingRecipe;
import com.orijua.mitofus.feature.smithing.ForgingRecipeInput;
import com.orijua.mitofus.feature.smithing.ForgingRules;
import com.orijua.mitofus.feature.smithing.ManualForgePayload;
import com.orijua.mitofus.feature.smithing.SmithingAnvilMenu;
import com.orijua.mitofus.feature.smithing.SmithingHammers;
import com.orijua.mitofus.feature.eibar.VelascoContest;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The smithing anvil as a Mitofus window. The slots keep vanilla's furnace positions; the result slot only
 * previews the item. <b>Auto</b> forges it after a short progress bar; <b>Manual</b> opens the forging
 * minigame over the window (click or space; Esc cancels) and sends its precision to the server.
 * A side panel shows the player's rating odds (automatic and with a perfect manual forge) and, once the
 * inputs match a recipe, the result's requirements ticked against the player's stats.
 * The "Enhance" button appears when the menu holds equipment + an enhancement stone.
 */
public class SmithingAnvilScreen extends AbstractContainerScreen<SmithingAnvilMenu> implements MitofusStyledScreen {
    private static final int SIDE_GAP = 4;
    private static final int SIDE_WIDTH = 170;
    private static final int PADDING = 6;
    private static final int LINE = 10;
    /** Space before the great success row, with a line across it. */
    private static final int GREAT_GAP = 6;
    /** Screen rectangle {left, top, right, bottom} of the great-success row, for its hover; null while not drawn. */
    private int[] greatHover;
    /** Same for the "Manual*" column header, whose asterisk is explained on hover. */
    private int[] manualHover;
    private static final int SLOT_BACKGROUND = 0xC01B2A40;
    private static final int GAME_WIDTH = 220;
    private static final int BAR_WIDTH = 180;
    private static final int HEAT_COLD = 0xFFFFB347;
    private static final int HEAT_HOT = 0xFFE0432B;
    /** How long the final precision stays on screen before the minigame window closes. */
    private static final long RESULT_MILLIS = 1500;

    private Button enhanceButton;
    /** Which tooltip the enhance button shows now (key and arguments), so it is only rebuilt when that changes. */
    private String enhanceTooltipKey = "";
    private Button autoButton;
    /** Whether the Auto tooltip now says the contest forbids it. */
    private boolean autoBlockedByContest;
    private Button manualButton;
    private Button batchButton;
    /** Picks between recipes that take the same items (pickaxe or axe); only shown when there is a choice. */
    private Button recipeButton;
    /** Which tooltip the batch button shows now, so it is only rebuilt when that changes. */
    private String batchTooltipKey = "";
    private double[] autoOdds = new double[ItemRating.values().length];
    private double[] manualOdds = new double[ItemRating.values().length];
    private double greatAuto;
    private double greatManual;
    /** The forging recipe the inputs match, if any (found on the client, even one for another class). */
    private Optional<ForgingRecipe> recipe = Optional.empty();

    /** The manual minigame being played over the window, or null. */
    private ForgingMinigame minigame;
    private long resultShownUntil = -1;
    /** Ignores the key-repeat presses while space is held, so holding it doesn't strike again and again. */
    private boolean spaceDown;

    public SmithingAnvilScreen(SmithingAnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // Center the window and its side panel together.
        leftPos = (width - imageWidth - SIDE_GAP - SIDE_WIDTH) / 2;
        enhanceButton = addRenderableWidget(Button.builder(Component.empty(), b -> pressButton(SmithingAnvilMenu.ENHANCE_BUTTON))
                .bounds(leftPos + 96, topPos + 56, 72, 14)
                .build(MitofusButton::new));
        autoButton = addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.forge_auto"),
                        b -> pressButton(menu.autoForging() ? SmithingAnvilMenu.STOP_BUTTON : SmithingAnvilMenu.AUTO_FORGE_BUTTON))
                .bounds(leftPos + 86, topPos + 58, 38, 14)
                .tooltip(Tooltip.create(Component.translatable("screen.mitofus.forge_auto.tooltip")))
                .build(MitofusButton::new));
        manualButton = addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.forge_manual"), b -> startMinigame())
                .bounds(leftPos + 127, topPos + 58, 42, 14)
                .tooltip(Tooltip.create(Component.translatable("screen.mitofus.forge_manual.tooltip")))
                .build(MitofusButton::new));
        // Right of the result slot: how many items one press of Auto makes.
        batchButton = addRenderableWidget(Button.builder(Component.empty(), b -> pressButton(SmithingAnvilMenu.BATCH_BUTTON))
                .bounds(leftPos + 138, topPos + 37, 30, 14)
                .build(MitofusButton::new));
        recipeButton = addRenderableWidget(Button.builder(Component.empty(), b -> pressButton(SmithingAnvilMenu.RECIPE_BUTTON))
                .bounds(leftPos + 86, topPos + 17, 82, 14)
                .tooltip(Tooltip.create(Component.translatable("screen.mitofus.recipe.tooltip")))
                .build(MitofusButton::new));
        recipeButton.visible = false;
        batchTooltipKey = "";
        containerTick();
    }

    private void pressButton(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        PlayerStats stats = ProgressionService.effectiveStats(minecraft.player);
        int smithing = ProgressionService.effectiveSmithingLevel(minecraft.player);
        updateEnhanceButton(stats);
        boolean forgeable = menu.canForge() && !enhanceButton.visible;
        autoButton.visible = forgeable;
        manualButton.visible = forgeable;
        batchButton.visible = forgeable;
        boolean idle = !menu.autoForging() && minigame == null;
        // While forging it is the Stop button. In Velasco's contest only a manual forge counts, so Auto is off.
        boolean contest = VelascoContest.forcesManual(minecraft.player);
        autoButton.active = minigame == null && (menu.autoForging() || !contest);
        if (contest != autoBlockedByContest) {
            autoBlockedByContest = contest;
            autoButton.setTooltip(Tooltip.create(Component.translatable(contest ? "screen.mitofus.forge_auto.contest"
                    : "screen.mitofus.forge_auto.tooltip")));
        }
        autoButton.setMessage(Component.translatable(menu.autoForging() ? "screen.mitofus.forge_stop" : "screen.mitofus.forge_auto"));
        manualButton.active = idle;
        batchButton.active = idle && ForgingRules.batchUnlocked(1, smithing);
        updateBatchButton(smithing);

        // Recipes are synced to the client, so the screen can find the one the server will use.
        ForgingRecipeInput input = new ForgingRecipeInput(menu.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem(),
                menu.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem());
        int choices = ForgingRecipe.candidates(minecraft.level.getRecipeManager(), input, minecraft.level).size();
        recipe = ForgingRecipe.find(minecraft.level.getRecipeManager(), input, minecraft.level, menu.recipeChoice())
                .map(RecipeHolder::value);
        recipeButton.visible = forgeable && choices > 1;
        recipeButton.active = idle;
        recipeButton.setMessage(Component.translatable("screen.mitofus.recipe", Math.floorMod(menu.recipeChoice(), Math.max(1, choices)) + 1, choices));
        // A recipe with a rating range (Guadalete: Unique or Legendary) shows the odds within it.
        ForgingExtras extras = recipe.map(ForgingRecipe::extras).orElse(ForgingExtras.NONE);
        // The hammer in either hand (the better one) counts in both, as when the server forges. Read every frame, so
        // taking it out or putting it on with the anvil open updates the odds at once.
        double hammer = SmithingHammers.skillBonus(minecraft.level.registryAccess(),
                SmithingHammers.heldHammerStack(minecraft.level.registryAccess(), minecraft.player));
        autoOdds = RatingRoller.odds(stats.dexterity(), smithing, hammer, extras.minRating(), extras.maxRating());
        manualOdds = RatingRoller.odds(stats.dexterity(), smithing, ForgingRules.manualSkillBonus(1) + hammer, extras.minRating(), extras.maxRating());
        greatAuto = ForgingRules.greatSuccessChance(stats.dexterity(), smithing, -1);
        greatManual = ForgingRules.greatSuccessChance(stats.dexterity(), smithing, 1);
    }

    /** Label, enabled state and the warning tooltip of the Enhance button, from what the inputs hold. */
    private void updateEnhanceButton(PlayerStats stats) {
        SmithingAnvilMenu.EnhanceState state = menu.enhanceState();
        enhanceButton.visible = state != SmithingAnvilMenu.EnhanceState.NONE;
        if (!enhanceButton.visible) {
            enhanceTooltipKey = "";
            return;
        }
        EnhanceBalance enhance = EnhanceBalance.current();
        ItemStack piece = menu.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem();
        int level = menu.currentEnhancement();
        // Only a hidden rank hides the chance: a piece with its rank revealed shows the real one.
        boolean ratingHidden = AppraisalService.isRatingHidden(piece);
        boolean enabled = false;
        Component label;
        String key;
        Object[] args = new Object[0];
        switch (state) {
            case WRONG_MATERIAL -> {
                label = Component.translatable("screen.mitofus.enhance_wrong_material");
                if (SmithingHammers.isHammer(piece)) {
                    key = "screen.mitofus.enhance.wrong_hammer";
                } else {
                    key = EquipmentService.pieceType(piece).orElse(EnhanceRules.PieceType.WEAPON) == EnhanceRules.PieceType.WEAPON
                            ? "screen.mitofus.enhance.wrong_weapon" : "screen.mitofus.enhance.wrong_armor";
                }
            }
            case MAXED -> {
                label = Component.translatable("screen.mitofus.enhance_maxed", EquipmentRules.MAX_ENHANCEMENT);
                key = "";
            }
            default -> {
                EnhanceMaterial material = menu.enhanceMaterial().orElseThrow();
                EnhanceMaterial.Kind kind = material.kind();
                // The real chance is only shown for a piece whose rank is known: an unappraised one shows "?".
                ItemRating visible = AppraisalService.visibleRating(piece).orElse(null);
                double shown = EnhanceRules.chance(Balance.current(), enhance, kind, level, stats.dexterity(), visible);
                // Enabled by what is seen, so a hidden rank is not given away; the server decides with the real one.
                enabled = EnhanceRules.hasChance(shown);
                int lost = EnhanceRules.levelsLost(enhance, kind);
                if (kind == EnhanceMaterial.Kind.ANCIENT_SCROLL) {
                    label = Component.translatable("screen.mitofus.enhance_ancient");
                    key = "screen.mitofus.enhance.ancient";
                    args = new Object[]{enhance.ancientMinGain(), enhance.ancientMaxGain()};
                } else {
                    label = ratingHidden
                            ? Component.translatable("screen.mitofus.enhance_unknown", level + 1)
                            : Component.translatable("screen.mitofus.enhance", level + 1, EquipmentRules.formatPercent(shown));
                    if (!enabled) {
                        key = "message.mitofus.enhance_no_chance";
                    } else if (level > 0 && lost > 0) {
                        key = EnhanceRules.levelsKey("screen.mitofus.enhance.risk", lost);
                        args = new Object[]{lost, EnhanceRules.levelAfterFailure(level, lost)};
                    } else {
                        key = "screen.mitofus.enhance.safe";
                    }
                }
            }
        }
        if (ratingHidden && state == SmithingAnvilMenu.EnhanceState.READY
                && menu.enhanceMaterial().orElseThrow().kind() != EnhanceMaterial.Kind.ANCIENT_SCROLL) {
            key = key + "+unknown";
        }
        enhanceButton.setMessage(label);
        enhanceButton.active = enabled;
        String tooltipKey = key + Arrays.toString(args);
        if (!tooltipKey.equals(enhanceTooltipKey)) {
            enhanceTooltipKey = tooltipKey;
            boolean extraUnknown = key.endsWith("+unknown");
            String base = extraUnknown ? key.substring(0, key.length() - "+unknown".length()) : key;
            if (base.isEmpty()) {
                enhanceButton.setTooltip(null);
            } else {
                MutableComponent text = Component.translatable(base, args);
                if (extraUnknown) {
                    text.append("\n").append(Component.translatable("screen.mitofus.enhance.unknown").withStyle(s -> s.withColor(UiStyle.MUTED)));
                }
                enhanceButton.setTooltip(Tooltip.create(text));
            }
        }
    }

    /** "×5" (or "Máx") while idle, "3/10" while a batch runs; the tooltip says what it does and the next unlock. */
    private void updateBatchButton(int smithing) {
        int choice = menu.batchChoice();
        if (menu.batchCount() > 1) {
            batchButton.setMessage(Component.literal(Math.min(menu.batchDone() + 1, menu.batchCount()) + "/" + menu.batchCount()));
        } else {
            batchButton.setMessage(batchLabel(choice));
        }
        int next = choice + 1;
        while (next < ForgingRules.BATCH_SIZES.length && ForgingRules.batchUnlocked(next, smithing)) {
            next++;
        }
        String key = choice + ":" + next;
        if (key.equals(batchTooltipKey)) {
            return;
        }
        batchTooltipKey = key;
        MutableComponent text = Component.translatable(ForgingRules.batchUnlocked(1, smithing)
                ? "screen.mitofus.batch.tooltip" : "screen.mitofus.batch.locked");
        if (next < ForgingRules.BATCH_SIZES.length) {
            text.append("\n").append(Component.translatable("screen.mitofus.batch.next",
                    batchLabel(next), ForgingRules.BATCH_UNLOCK_LEVELS[next]).withStyle(s -> s.withColor(UiStyle.MUTED)));
        }
        batchButton.setTooltip(Tooltip.create(text));
    }

    private static Component batchLabel(int index) {
        int size = ForgingRules.BATCH_SIZES[index];
        return size == ForgingRules.BATCH_ALL ? Component.translatable("screen.mitofus.batch.all") : Component.literal("×" + size);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        UiStyle.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, SLOT_BACKGROUND);
            graphics.renderOutline(x, y, 18, 18, UiStyle.LINE_SOFT);
        }
        arrow(graphics, leftPos + 81, topPos + 38);
        if (menu.autoForging()) {
            UiStyle.bar(graphics, leftPos + 81, topPos + 52, 22, 2, menu.autoProgress(), UiStyle.XP);
        }
        graphics.fill(leftPos + 7, topPos + 76, leftPos + imageWidth - 7, topPos + 77, UiStyle.LINE_SOFT);
        sidePanel(graphics, leftPos + imageWidth + SIDE_GAP, topPos);
    }

    /** A right-pointing arrow between the inputs and the result. */
    private static void arrow(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y + 4, x + 15, y + 6, UiStyle.LINE_BRIGHT);
        for (int i = 0; i < 6; i++) {
            graphics.fill(x + 15 + i, y + i - 1, x + 16 + i, y + 11 - i, UiStyle.LINE_BRIGHT);
        }
    }

    /**
     * The ratings the odds list shows: up to Unique. Legendary and Ancestral stay out (the player never forges them on
     * purpose), unless the recipe restricts the range, like Guadalete's Unique-Legendary, where they are what it gives.
     */
    private List<ItemRating> shownRatings() {
        ForgingExtras extras = recipe.map(ForgingRecipe::extras).orElse(ForgingExtras.NONE);
        boolean restricted = extras.minRating() != ItemRating.NORMAL || extras.maxRating() != ItemRating.ANCESTRAL;
        List<ItemRating> shown = new java.util.ArrayList<>();
        for (ItemRating rating : ItemRating.values()) {
            if (rating.ordinal() <= ItemRating.UNIQUE.ordinal() || restricted && autoOdds[rating.ordinal()] > 0) {
                shown.add(rating);
            }
        }
        return shown;
    }

    private void sidePanel(GuiGraphics graphics, int x, int y) {
        int left = x + PADDING;
        int right = x + SIDE_WIDTH - PADDING;
        // A long class name ("Legendary Smith Successor") wraps, leaving room for the tick on its first line.
        List<FormattedCharSequence> classLines = requiredClassText()
                .map(text -> font.split(text, right - left - font.width("✘") - 4))
                .orElse(List.of());
        List<ItemRating> shownRatings = shownRatings();
        int lines = 3 + shownRatings.size() + classLines.size()
                + recipe.map(r -> 3 + r.requirements().map(req -> req.stats().size()).orElse(0)).orElse(0);
        greatHover = null;
        manualHover = null;
        UiStyle.frame(graphics, x, y, SIDE_WIDTH, PADDING * 2 + lines * LINE + GREAT_GAP, 1.0f);
        int autoColumn = right - 54;
        int row = y + PADDING;

        graphics.drawString(font, Component.translatable("screen.mitofus.anvil_odds"), left, row, UiStyle.HEADING);
        row += LINE;
        rightAligned(graphics, Component.translatable("screen.mitofus.anvil_col_auto"), autoColumn, row, UiStyle.MUTED);
        rightAligned(graphics, Component.translatable("screen.mitofus.anvil_col_manual"), right, row, UiStyle.MUTED);
        int manualWidth = font.width(Component.translatable("screen.mitofus.anvil_col_manual"));
        manualHover = new int[]{right - manualWidth, row, right, row + LINE};
        row += LINE;
        for (ItemRating rating : shownRatings) {
            graphics.drawString(font, rating.displayName(), left, row, UiStyle.TEXT);
            rightAligned(graphics, Component.literal(EquipmentRules.formatPercent(autoOdds[rating.ordinal()])), autoColumn, row, UiStyle.TEXT);
            rightAligned(graphics, Component.literal(EquipmentRules.formatPercent(manualOdds[rating.ordinal()])), right, row, UiStyle.GOLD);
            row += LINE;
        }
        // The chance of a great success (the piece comes out one rating higher), set apart so it doesn't read as a rating.
        graphics.fill(left, row + GREAT_GAP / 2 - 1, right, row + GREAT_GAP / 2, UiStyle.LINE_SOFT);
        row += GREAT_GAP;
        // Clipped so a long name never runs over the Auto percentage; the explanation is a hover (see render).
        int greatWidth = autoColumn - left - font.width(EquipmentRules.formatPercent(greatAuto)) - 4;
        graphics.drawString(font, UiStyle.ellipsize(font, Component.translatable("screen.mitofus.anvil_great_success"), greatWidth), left, row, UiStyle.GOLD);
        greatHover = new int[]{left, row, right, row + LINE};
        rightAligned(graphics, Component.literal(EquipmentRules.formatPercent(greatAuto)), autoColumn, row, UiStyle.TEXT);
        rightAligned(graphics, Component.literal(EquipmentRules.formatPercent(greatManual)), right, row, UiStyle.GOLD);
        row += LINE;

        if (recipe.isEmpty()) {
            return;
        }
        row += LINE;
        graphics.drawString(font, Component.translatable("screen.mitofus.anvil_requirements"), left, row, UiStyle.HEADING);
        row += LINE;
        if (!classLines.isEmpty()) {
            Optional<ResourceLocation> requiredJob = recipe.get().extras().requiredJob();
            boolean isClass = minecraft.player.getData(ModAttachments.PLAYER_CLASSES).job().equals(requiredJob);
            int color = isClass ? UiStyle.GOOD : UiStyle.BAD;
            String mark = isClass ? "✔" : "✘";
            graphics.drawString(font, mark, right - font.width(mark), row, color);
            for (FormattedCharSequence line : classLines) {
                graphics.drawString(font, line, left, row, color);
                row += LINE;
            }
        }
        Optional<EquipRequirements> requirements = recipe.get().requirements();
        EquipLevelRules.Range levels = ForgingRecipeText.levelRange(recipe.get());
        PlayerStats stats = ProgressionService.effectiveStats(minecraft.player);
        Component levelText = ForgingRecipeText.levelText(levels);
        // Met when the player reaches the worst case (the highest level this recipe can give).
        requirement(graphics, levelText, stats.level() >= levels.high(), left, right, row);
        for (Map.Entry<StatType, Integer> stat : requirements.map(EquipRequirements::stats).orElse(Map.of()).entrySet()) {
            row += LINE;
            requirement(graphics, ForgingRecipeText.statText(stat.getKey(), stat.getValue()),
                    stats.get(stat.getKey()) >= stat.getValue(), left, right, row);
        }
    }

    private Optional<Component> requiredClassText() {
        return recipe.flatMap(r -> ForgingRecipeText.requiredJob(minecraft.level, r));
    }

    private void rightAligned(GuiGraphics graphics, Component text, int right, int y, int color) {
        graphics.drawString(font, text, right - font.width(text), y, color);
    }

    /** One requirement line, green with a tick when the player meets it, red with a cross when not. */
    private void requirement(GuiGraphics graphics, Component text, boolean met, int left, int right, int row) {
        int color = met ? UiStyle.GOOD : UiStyle.BAD;
        graphics.drawString(font, text, left, row, color);
        String mark = met ? "✔" : "✘";
        graphics.drawString(font, mark, right - font.width(mark), row, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, UiStyle.HEADING);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiStyle.MUTED);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (minigame == null) {
            renderTooltip(graphics, mouseX, mouseY);
            if (greatHover != null && mouseX >= greatHover[0] && mouseX < greatHover[2] && mouseY >= greatHover[1] && mouseY < greatHover[3]) {
                graphics.renderTooltip(font, Component.translatable("screen.mitofus.anvil_great_success_note"), mouseX, mouseY);
            }
            if (manualHover != null && mouseX >= manualHover[0] && mouseX < manualHover[2] && mouseY >= manualHover[1] && mouseY < manualHover[3]) {
                graphics.renderTooltip(font, Component.translatable("screen.mitofus.anvil_manual_note"), mouseX, mouseY);
            }
        } else {
            renderMinigame(graphics, Util.getMillis());
        }
    }

    // ---- Manual forging minigame ----

    private void startMinigame() {
        pressButton(SmithingAnvilMenu.MANUAL_FORGE_BUTTON); // the server notes when it started
        minigame = new ForgingMinigame(0.5 + minecraft.player.getRandom().nextDouble() * 0.3);
        resultShownUntil = -1;
    }

    private void press(long now) {
        if (minigame.phase() == ForgingMinigame.Phase.HEAT) {
            minigame.startHeating(now);
        } else if (minigame.phase() == ForgingMinigame.Phase.HAMMER) {
            minigame.strike(now);
            minecraft.player.playSound(SoundEvents.ANVIL_PLACE, 0.4f, 0.8f + (float) minigame.lastStrike() * 0.6f);
        }
    }

    private void release(long now) {
        minigame.stopHeating(now);
    }

    /** Sends the result once the last strike lands, then leaves it on screen for a moment. */
    private void updateMinigame(long now) {
        minigame.update(now);
        if (minigame.phase() == ForgingMinigame.Phase.DONE && resultShownUntil < 0) {
            PacketDistributor.sendToServer(new ManualForgePayload(menu.containerId, (float) minigame.precision()));
            resultShownUntil = now + RESULT_MILLIS;
        }
        if (resultShownUntil >= 0 && now >= resultShownUntil) {
            minigame = null;
        }
    }

    private void renderMinigame(GuiGraphics graphics, long now) {
        updateMinigame(now);
        if (minigame == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400); // above the items in the slots
        graphics.fill(0, 0, width, height, 0xA0000000);

        List<FormattedCharSequence> instructions = font.split(instruction(), GAME_WIDTH - 2 * PADDING);
        int panelHeight = PADDING + LINE + 4 + instructions.size() * LINE + 6 + 10 + 6 + LINE + LINE + PADDING;
        int x = (width - GAME_WIDTH) / 2;
        int y = (height - panelHeight) / 2;
        int center = x + GAME_WIDTH / 2;
        UiStyle.panel(graphics, x, y, GAME_WIDTH, panelHeight);

        int row = y + PADDING;
        graphics.drawCenteredString(font, Component.translatable("minigame.mitofus.title"), center, row, UiStyle.HEADING);
        row += LINE;
        graphics.fill(x + PADDING, row, x + GAME_WIDTH - PADDING, row + 1, UiStyle.LINE_SOFT);
        row += 4;
        for (FormattedCharSequence line : instructions) {
            graphics.drawCenteredString(font, line, center, row, UiStyle.TEXT);
            row += LINE;
        }
        row += 6;
        drawBar(graphics, center - BAR_WIDTH / 2, row, now);
        row += 10 + 6;
        Component feedback = feedback();
        if (feedback != null) {
            graphics.drawCenteredString(font, feedback, center, row, UiStyle.TEXT);
        }
        row += LINE;
        graphics.drawCenteredString(font, Component.translatable("minigame.mitofus.cancel"), center, row, UiStyle.MUTED);
        graphics.pose().popPose();
    }

    private Component instruction() {
        return switch (minigame.phase()) {
            case HEAT -> Component.translatable("minigame.mitofus.heat");
            case HAMMER -> Component.translatable("minigame.mitofus.hammer", minigame.strikesDone() + 1, ForgingMinigame.STRIKES);
            case DONE -> Component.translatable("minigame.mitofus.result", Math.round(minigame.precision() * 100));
        };
    }

    /** The temperature bar with its golden band, or the strike bar with its middle zone and the marker. */
    private void drawBar(GuiGraphics graphics, int x, int y, long now) {
        int height = 8;
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + height + 1, UiStyle.LINE_SOFT);
        graphics.fill(x, y, x + BAR_WIDTH, y + height, SLOT_BACKGROUND);
        if (minigame.phase() == ForgingMinigame.Phase.HEAT) {
            int zoneLeft = x + (int) (minigame.zoneStart() * BAR_WIDTH);
            graphics.fill(zoneLeft, y, zoneLeft + (int) (ForgingMinigame.ZONE_WIDTH * BAR_WIDTH), y + height, 0x90FFD700);
            int filled = (int) (minigame.temperature(now) * BAR_WIDTH);
            graphics.fillGradient(x, y + 2, x + filled, y + height - 2, HEAT_COLD, HEAT_HOT);
        } else {
            int middle = x + BAR_WIDTH / 2;
            int perfect = (int) (ForgingMinigame.PERFECT_MARGIN * BAR_WIDTH);
            graphics.fill(middle - BAR_WIDTH / 6, y, middle + BAR_WIDTH / 6, y + height, 0x4055FF55);
            graphics.fill(middle - perfect, y, middle + perfect, y + height, 0x90FFD700);
            int marker = x + (int) (minigame.markerPosition(now) * BAR_WIDTH);
            graphics.fill(marker - 1, y - 2, marker + 1, y + height + 2, UiStyle.LINE_BRIGHT);
        }
    }

    /** How the last strike (or the heating) went, or null before there is anything to say. */
    private Component feedback() {
        double score = minigame.strikesDone() > 0 ? minigame.lastStrike()
                : minigame.phase() == ForgingMinigame.Phase.HAMMER ? minigame.heatScore() : -1;
        if (score < 0) {
            return null;
        }
        if (score >= 0.95) {
            return Component.translatable("minigame.mitofus.perfect").withStyle(s -> s.withColor(UiStyle.GOLD));
        }
        if (score >= 0.6) {
            return Component.translatable("minigame.mitofus.good").withStyle(s -> s.withColor(UiStyle.GOOD));
        }
        if (score >= 0.3) {
            return Component.translatable("minigame.mitofus.fair");
        }
        return Component.translatable("minigame.mitofus.miss").withStyle(s -> s.withColor(UiStyle.BAD));
    }

    // While the minigame is open it takes every click and key, so nothing else in the anvil reacts.

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (minigame != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                press(Util.getMillis());
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (minigame != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                release(Util.getMillis());
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return minigame != null || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minigame != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                minigame = null; // cancelled: nothing is sent, nothing is spent
            } else if (keyCode == GLFW.GLFW_KEY_SPACE && !spaceDown) {
                spaceDown = true;
                press(Util.getMillis());
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            spaceDown = false;
            if (minigame != null) {
                release(Util.getMillis());
                return true;
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
}
