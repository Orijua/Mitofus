package com.orijua.mitofus.core.network;

import com.orijua.mitofus.feature.vrhelmet.VrHelmetConnectingPayload;
import com.orijua.mitofus.feature.economy.ClientTerminal;
import com.orijua.mitofus.feature.economy.VrHelmetUnbindPayload;
import com.orijua.mitofus.feature.economy.TerminalBuyOfferPayload;
import com.orijua.mitofus.feature.economy.TerminalStatePayload;
import com.orijua.mitofus.feature.economy.TradeSellPayload;
import com.orijua.mitofus.feature.fatigue.ClientFatigue;
import com.orijua.mitofus.feature.fatigue.FatigueDebugPayload;
import com.orijua.mitofus.feature.vrhelmet.ClientVrHelmet;
import com.orijua.mitofus.feature.notification.ClientNotifications;
import net.minecraft.Util;
import com.orijua.mitofus.feature.notification.SystemNotificationPayload;
import com.orijua.mitofus.feature.npc.ApprenticeActionPayload;
import com.orijua.mitofus.feature.npc.NpcTradePayload;
import com.orijua.mitofus.feature.sancho.ReadOrbePayload;
import com.orijua.mitofus.feature.eibar.StartContestPayload;
import com.orijua.mitofus.feature.progression.AllocateStatPayload;
import com.orijua.mitofus.feature.progression.ChooseClassPayload;
import com.orijua.mitofus.feature.fusion.AbsorbClassPayload;
import com.orijua.mitofus.feature.fusion.FuseClassesPayload;
import com.orijua.mitofus.feature.talent.BuyTalentPayload;
import com.orijua.mitofus.feature.progression.ChooseJobPayload;
import com.orijua.mitofus.feature.quest.NpcQuestPayload;
import com.orijua.mitofus.feature.quest.QuestActionPayload;
import com.orijua.mitofus.feature.vrhelmet.LogoutRequestPayload;
import com.orijua.mitofus.feature.vrhelmet.ResidencePayload;
import com.orijua.mitofus.feature.coach.CoachTripPayload;
import com.orijua.mitofus.feature.storage.StorageInfoPayload;
import com.orijua.mitofus.feature.storage.StoragePayload;
import com.orijua.mitofus.feature.coach.CoachInfoPayload;
import com.orijua.mitofus.feature.quest.QuestsSeenPayload;
import com.orijua.mitofus.feature.form.EquipFormPayload;
import com.orijua.mitofus.feature.skill.AssignSkillPayload;
import com.orijua.mitofus.feature.skill.UseSkillPayload;
import com.orijua.mitofus.feature.smelting.ManualSmeltPayload;
import com.orijua.mitofus.feature.npc.SmithBuyPayload;
import com.orijua.mitofus.feature.npc.SmithShopMenu;
import com.orijua.mitofus.feature.npc.SmithShopStatePayload;
import com.orijua.mitofus.feature.smithing.ManualForgePayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Custom packets. Mostly client → server intents: server → client data syncs itself through the
 * attachments in ModAttachments. The exception is one-off events with no state, like system windows.
 */
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "3";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(UseSkillPayload.TYPE, UseSkillPayload.STREAM_CODEC, ServerPayloadHandler::useSkill);
        registrar.playToServer(AssignSkillPayload.TYPE, AssignSkillPayload.STREAM_CODEC, ServerPayloadHandler::assignSkill);
        registrar.playToClient(com.orijua.mitofus.feature.form.FormMessagePayload.TYPE, com.orijua.mitofus.feature.form.FormMessagePayload.STREAM_CODEC,
                (payload, context) -> com.orijua.mitofus.feature.form.client.FormsTab.showMessage(payload.key()));
        registrar.playToServer(EquipFormPayload.TYPE, EquipFormPayload.STREAM_CODEC, ServerPayloadHandler::equipForm);
        registrar.playToServer(AllocateStatPayload.TYPE, AllocateStatPayload.STREAM_CODEC, ServerPayloadHandler::allocateStat);
        registrar.playToServer(ChooseClassPayload.TYPE, ChooseClassPayload.STREAM_CODEC, ServerPayloadHandler::chooseClass);
        registrar.playToServer(BuyTalentPayload.TYPE, BuyTalentPayload.STREAM_CODEC, ServerPayloadHandler::buyTalent);
        registrar.playToServer(FuseClassesPayload.TYPE, FuseClassesPayload.STREAM_CODEC, ServerPayloadHandler::fuseClasses);
        registrar.playToServer(AbsorbClassPayload.TYPE, AbsorbClassPayload.STREAM_CODEC, ServerPayloadHandler::absorbClass);
        registrar.playToServer(ChooseJobPayload.TYPE, ChooseJobPayload.STREAM_CODEC, ServerPayloadHandler::chooseJob);
        registrar.playToServer(QuestActionPayload.TYPE, QuestActionPayload.STREAM_CODEC, ServerPayloadHandler::questAction);
        registrar.playToServer(LogoutRequestPayload.TYPE, LogoutRequestPayload.STREAM_CODEC, ServerPayloadHandler::logoutRequest);
        registrar.playToServer(ResidencePayload.TYPE, ResidencePayload.STREAM_CODEC, ServerPayloadHandler::residence);
        registrar.playToServer(CoachTripPayload.TYPE, CoachTripPayload.STREAM_CODEC, ServerPayloadHandler::coachTrip);
        registrar.playToServer(StoragePayload.TYPE, StoragePayload.STREAM_CODEC, ServerPayloadHandler::storage);
        registrar.playToServer(QuestsSeenPayload.TYPE, QuestsSeenPayload.STREAM_CODEC, ServerPayloadHandler::questsSeen);
        registrar.playToServer(NpcQuestPayload.TYPE, NpcQuestPayload.STREAM_CODEC, ServerPayloadHandler::npcQuest);
        registrar.playToServer(NpcTradePayload.TYPE, NpcTradePayload.STREAM_CODEC, ServerPayloadHandler::npcTrade);
        registrar.playToServer(ManualForgePayload.TYPE, ManualForgePayload.STREAM_CODEC, ServerPayloadHandler::manualForge);
        registrar.playToServer(ManualSmeltPayload.TYPE, ManualSmeltPayload.STREAM_CODEC, ServerPayloadHandler::manualSmelt);
        registrar.playToServer(ReadOrbePayload.TYPE, ReadOrbePayload.STREAM_CODEC, ServerPayloadHandler::readOrbeAurifero);
        registrar.playToServer(ApprenticeActionPayload.TYPE, ApprenticeActionPayload.STREAM_CODEC, ServerPayloadHandler::apprenticeAction);
        registrar.playToServer(TradeSellPayload.TYPE, TradeSellPayload.STREAM_CODEC, ServerPayloadHandler::tradeSell);
        registrar.playToServer(StartContestPayload.TYPE, StartContestPayload.STREAM_CODEC, ServerPayloadHandler::startContest);
        registrar.playToServer(VrHelmetUnbindPayload.TYPE, VrHelmetUnbindPayload.STREAM_CODEC, ServerPayloadHandler::vrHelmetUnbind);
        registrar.playToServer(TerminalBuyOfferPayload.TYPE, TerminalBuyOfferPayload.STREAM_CODEC, ServerPayloadHandler::terminalBuyOffer);
        registrar.playToServer(SmithBuyPayload.TYPE, SmithBuyPayload.STREAM_CODEC, ServerPayloadHandler::smithBuy);
        // The handler only touches the menu (no client classes), so this is safe on a dedicated server.
        registrar.playToClient(SmithShopStatePayload.TYPE, SmithShopStatePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof SmithShopMenu menu && menu.containerId == payload.containerId()) {
                menu.receive(payload.offers(), payload.message());
            }
        });
        // The windows of the storekeeper and the coachman open when the server sends what they show.
        registrar.playToClient(StorageInfoPayload.TYPE, StorageInfoPayload.STREAM_CODEC,
                (payload, context) -> com.orijua.mitofus.feature.storage.client.StorageScreen.show(payload));
        registrar.playToClient(CoachInfoPayload.TYPE, CoachInfoPayload.STREAM_CODEC,
                (payload, context) -> com.orijua.mitofus.feature.coach.client.CoachScreen.show(payload));
        // Like the one below, the handler only touches a client-state class with no client imports.
        registrar.playToClient(VrHelmetConnectingPayload.TYPE, VrHelmetConnectingPayload.STREAM_CODEC,
                (payload, context) -> ClientVrHelmet.connecting(payload.ticks(), Util.getMillis()));
        registrar.playToClient(FatigueDebugPayload.TYPE, FatigueDebugPayload.STREAM_CODEC,
                (payload, context) -> ClientFatigue.debug(payload));
        // The handler only touches ClientTerminal (no client classes), so this is safe on a dedicated server.
        registrar.playToClient(TerminalStatePayload.TYPE, TerminalStatePayload.STREAM_CODEC,
                (payload, context) -> ClientTerminal.update(payload.containerId(), payload.state()));
        // The handler only touches ClientNotifications (no client classes), so this is safe on a dedicated server.
        registrar.playToClient(SystemNotificationPayload.TYPE, SystemNotificationPayload.STREAM_CODEC,
                (payload, context) -> ClientNotifications.QUEUE.push(payload.notification()));
    }
}
