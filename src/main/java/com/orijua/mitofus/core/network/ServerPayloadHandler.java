package com.orijua.mitofus.core.network;

import com.orijua.mitofus.feature.economy.AccountTerminalBlock;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.feature.economy.VrHelmetUnbind;
import com.orijua.mitofus.feature.economy.VrHelmetUnbindPayload;
import com.orijua.mitofus.feature.economy.TerminalBuyOfferPayload;
import com.orijua.mitofus.feature.economy.TerminalMenu;
import com.orijua.mitofus.feature.economy.TerminalService;
import com.orijua.mitofus.feature.economy.TerminalShop;
import com.orijua.mitofus.feature.economy.TradeSellPayload;
import com.orijua.mitofus.feature.economy.TradeService;
import com.orijua.mitofus.feature.npc.ApprenticeActionPayload;
import com.orijua.mitofus.feature.npc.ApprenticeAssignments;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import com.orijua.mitofus.feature.npc.BlacksmithNpc;
import com.orijua.mitofus.feature.npc.NpcTradePayload;
import com.orijua.mitofus.feature.npc.SmithBuyPayload;
import com.orijua.mitofus.feature.npc.SmithShop;
import com.orijua.mitofus.feature.npc.SmithShopMenu;
import com.orijua.mitofus.feature.sancho.OrbeAurifero;
import com.orijua.mitofus.feature.sancho.ReadOrbePayload;
import com.orijua.mitofus.feature.eibar.MenciaNpc;
import com.orijua.mitofus.feature.eibar.VelascoContest;
import com.orijua.mitofus.feature.eibar.StartContestPayload;
import com.orijua.mitofus.feature.progression.AllocateStatPayload;
import com.orijua.mitofus.feature.progression.ChooseClassPayload;
import com.orijua.mitofus.feature.progression.ChangeBranchPayload;
import com.orijua.mitofus.feature.talent.BuyTalentPayload;
import com.orijua.mitofus.feature.talent.TalentService;
import com.orijua.mitofus.feature.progression.ChooseJobPayload;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.quest.NpcQuestPayload;
import com.orijua.mitofus.feature.quest.QuestActionPayload;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.vrhelmet.LogoutRequestPayload;
import com.orijua.mitofus.feature.vrhelmet.ResidencePayload;
import com.orijua.mitofus.feature.coach.CoachService;
import com.orijua.mitofus.feature.coach.CoachTripPayload;
import com.orijua.mitofus.feature.coach.CoachmanNpc;
import com.orijua.mitofus.feature.storage.StoragePayload;
import com.orijua.mitofus.feature.storage.StorageService;
import com.orijua.mitofus.feature.storage.StorekeeperNpc;
import com.orijua.mitofus.feature.vrhelmet.ResidenceService;
import com.orijua.mitofus.feature.npc.VillageChiefNpc;
import com.orijua.mitofus.feature.vrhelmet.LogoutService;
import com.orijua.mitofus.feature.quest.QuestsSeenPayload;
import com.orijua.mitofus.feature.skill.AssignSkillPayload;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.skill.UseSkillPayload;
import com.orijua.mitofus.feature.smelting.ManualSmeltPayload;
import com.orijua.mitofus.feature.smelting.SmelterMenu;
import com.orijua.mitofus.feature.smithing.ManualForgePayload;
import com.orijua.mitofus.feature.smithing.SmithingAnvilMenu;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Every client → server packet lands here and is forwarded to a service.
 * Payloads only carry data; services re-check every rule, since the client can't be trusted.
 * Every packet that changes Mitofus's state must check {@code MitofusWorld.is(player.level())} (the real world has none of it).
 */
final class ServerPayloadHandler {
    private ServerPayloadHandler() {
    }

    static void useSkill(UseSkillPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            SkillService.use(player, payload.slot());
        }
    }

    static void assignSkill(AssignSkillPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())) {
            SkillService.assign(player, payload.slot(), payload.skill());
        }
    }

    static void equipForm(com.orijua.mitofus.feature.form.EquipFormPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())) {
            com.orijua.mitofus.feature.form.FormService.handle(player, payload.form());
        }
    }

    static void allocateStat(AllocateStatPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())) {
            ProgressionService.allocatePoints(player, payload.stat(), payload.amount());
        }
    }

    static void chooseClass(ChooseClassPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())) {
            ClassService.chooseClass(player, payload.slot(), payload.playerClass());
        }
    }

    static void buyTalent(BuyTalentPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())) {
            TalentService.buy(player, payload.slot(), payload.node());
        }
    }

    /** From the village chief's window: changes the branch of a slot (the player must stand near him; ClassService checks the rest). */
    static void changeBranch(ChangeBranchPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())
                && player.level().getEntity(payload.npc()) instanceof VillageChiefNpc chief
                && chief.distanceToSqr(player) <= QuestService.GIVER_REACH * QuestService.GIVER_REACH) {
            ClassService.changeBranch(player, payload.slot(), payload.branch());
        }
    }

    static void chooseJob(ChooseJobPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())) {
            ClassService.chooseJob(player, payload.job());
        }
    }

    /** Only for the anvil the player has open right now; the menu itself validates the result. */
    static void manualForge(ManualForgePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof SmithingAnvilMenu anvil
                && anvil.containerId == payload.containerId()) {
            anvil.finishManual(player, payload.precision());
        }
    }

    /** Only for the smelter the player has open right now; the menu checks the timing, the smelter the rest. */
    static void manualSmelt(ManualSmeltPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof SmelterMenu smelter
                && smelter.containerId == payload.containerId()) {
            smelter.finishManual(player, payload.precision());
        }
    }

    /** From the Mitofus window's "Disconnect" button: the same countdown as {@code /mitofus logout}. */
    static void logoutRequest(LogoutRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LogoutService.request(player, payload.force());
        }
    }

    /** From the village chief's window: becomes resident of his village (ResidenceService checks reach and cooldown). */
    static void residence(ResidencePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level().getEntity(payload.npc()) instanceof VillageChiefNpc chief) {
            ResidenceService.become(player, chief);
        }
    }

    /** From the coachman's window: a trip to another village (CoachService checks reach, destination and fare). */
    static void coachTrip(CoachTripPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level().getEntity(payload.npc()) instanceof CoachmanNpc coachman) {
            CoachService.travel(player, coachman, payload.destination());
        }
    }

    /** From the storekeeper's window: opens the storage or buys a row (StorageService checks reach and money). */
    static void storage(StoragePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level().getEntity(payload.npc()) instanceof StorekeeperNpc keeper) {
            if (payload.expand()) {
                StorageService.expand(player, keeper);
            } else {
                StorageService.open(player, keeper);
            }
        }
    }

    static void questsSeen(QuestsSeenPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())) {
            QuestService.markDiscoveriesSeen(player);
        }
    }

    /** From the Quests tab: quests with a giver are refused here (QuestService checks it). */
    static void questAction(QuestActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())) {
            switch (payload.action()) {
                case ACCEPT -> QuestService.accept(player, payload.quest());
                case CLAIM -> QuestService.claim(player, payload.quest());
                case ABANDON -> QuestService.abandon(player, payload.quest());
                case TRACK -> QuestService.toggleTracked(player, payload.quest());
            }
        }
    }

    /** From an NPC's quest window; QuestService checks the NPC gives that quest and stands within reach. */
    static void npcQuest(NpcQuestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())) {
            Entity npc = player.level().getEntity(payload.npc());
            switch (payload.action()) {
                case ACCEPT -> QuestService.accept(player, payload.quest(), npc);
                case DELIVER -> QuestService.deliver(player, payload.quest(), npc);
                case CLAIM -> QuestService.claim(player, payload.quest(), npc);
            }
        }
    }

    static void npcTrade(NpcTradePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())
                && player.level().getEntity(payload.npc()) instanceof BlacksmithNpc smith
                && smith.distanceToSqr(player) <= QuestService.GIVER_REACH * QuestService.GIVER_REACH) {
            smith.startTrading(player);
        }
    }

    /** From Smith's shop window: closes one deal. The menu must be the open one of the sender. */
    static void smithBuy(SmithBuyPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.containerMenu instanceof SmithShopMenu menu && menu.containerId == payload.containerId()
                && menu.smith() != null && menu.stillValid(player)) {
            SmithShop.Result result = SmithShop.buy(player, menu.smith(), payload.index(), payload.seenResult(), payload.seenCostA(), payload.seenCostB());
            SmithShop.sendState(player, menu, SmithShop.message(result, payload.seenResult()));
        }
    }

    static void readOrbeAurifero(ReadOrbePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())) {
            OrbeAurifero.read(player);
        }
    }

    /** Starting the contest: at Mencia, standing near her. */
    static void startContest(StartContestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && MitofusWorld.is(player.level())
                && player.level().getEntity(payload.mencia()) instanceof MenciaNpc mencia
                && mencia.distanceToSqr(player) <= QuestService.GIVER_REACH * QuestService.GIVER_REACH) {
            VelascoContest.start(player);
        }
    }

    /** From the Trade tab: sells the pieces the sender chose, in one batch. The Mitofus gate (BUG-050) is in {@code TradeService.sell}. */
    static void tradeSell(TradeSellPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        TradeService.message(TradeService.sell(player, payload)).ifPresent(message -> player.displayClientMessage(message, true));
    }

    /** The player confirmed unbinding the VR helmet in hand at a terminal: same checks as using the block, then the charge. */
    static void vrHelmetUnbind(VrHelmetUnbindPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.level().isLoaded(payload.pos())
                || !(player.level().getBlockState(payload.pos()).getBlock() instanceof AccountTerminalBlock)
                || player.distanceToSqr(payload.pos().getCenter()) > 64.0) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(payload.offHand()
                ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof com.orijua.mitofus.feature.vrhelmet.HelmetItem) {
            VrHelmetUnbind.unbind(player, stack, VrHelmetBalance.current());
        }
    }

    /** From the terminal's window: buys a piece of the daily offer into the stored character. */
    static void terminalBuyOffer(TerminalBuyOfferPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        TerminalShop.Outcome outcome = TerminalShop.buyOffer(player, payload);
        if (player.containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            TerminalService.sendState(player, menu, TerminalShop.message(outcome));
        }
    }

    /** From an apprentice's window: only his master, standing near him. */
    static void apprenticeAction(ApprenticeActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !MitofusWorld.is(player.level())
                || !(player.level().getEntity(payload.apprentice()) instanceof ApprenticeSmith apprentice)
                || !apprentice.isOwnedBy(player) || apprentice.distanceToSqr(player) > ApprenticeSmith.WINDOW_REACH * ApprenticeSmith.WINDOW_REACH) {
            return;
        }
        switch (payload.action()) {
            case PAY_DAY -> apprentice.payDays(player, 1);
            case PAY_5_DAYS -> apprentice.payDays(player, 5);
            case PAY_10_DAYS -> apprentice.payDays(player, 10);
            case CANCEL_ORDER -> apprentice.cancelOrder(player);
            case ASSIGN_MATERIALS -> ApprenticeAssignments.start(player, apprentice, ApprenticeSmith.Slot.MATERIALS);
            case ASSIGN_OUTPUT -> ApprenticeAssignments.start(player, apprentice, ApprenticeSmith.Slot.OUTPUT);
            case CLEAR_CONTAINERS -> apprentice.clearAssignments(player);
            case DISMISS -> apprentice.dismiss(player);
            case GIVE_HAMMER -> apprentice.giveHammer(player);
            case TAKE_HAMMER -> apprentice.takeHammer(player);
        }
    }
}
