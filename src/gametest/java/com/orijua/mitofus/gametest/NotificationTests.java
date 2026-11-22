package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.event.MitofusPlayerEvent;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.notification.SystemNotification;
import com.orijua.mitofus.feature.notification.SystemNotificationPayload;
import com.orijua.mitofus.feature.npc.BlacksmithNpc;
import com.orijua.mitofus.feature.progression.ClassChangedEvent;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.TitleDefinition;
import com.orijua.mitofus.feature.progression.TitleEarnedEvent;
import com.orijua.mitofus.feature.quest.QuestCompletedEvent;
import com.orijua.mitofus.feature.quest.QuestDiscoveredEvent;
import com.orijua.mitofus.feature.quest.QuestService;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class NotificationTests {
    private NotificationTests() {
    }

    /** Collects the domain events posted for one player while it is registered on the game bus. */
    public static final class Recorder {
        private final ServerPlayer player;
        final List<MitofusPlayerEvent> events = new ArrayList<>();

        Recorder(ServerPlayer player) {
            this.player = player;
        }

        @SubscribeEvent
        public void onTitle(TitleEarnedEvent event) {
            record(event);
        }

        @SubscribeEvent
        public void onQuest(QuestCompletedEvent event) {
            record(event);
        }

        @SubscribeEvent
        public void onClass(ClassChangedEvent event) {
            record(event);
        }

        @SubscribeEvent
        public void onDiscovery(QuestDiscoveredEvent event) {
            record(event);
        }

        private void record(MitofusPlayerEvent event) {
            if (event.player() == player) {
                events.add(event);
            }
        }
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void titleQuestAndClassPostTheirEvents(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ResourceLocation commission = Mitofus.id("first_commission");
        BlacksmithNpc smith = TestSupport.smith(helper);
        TestSupport.completed(player, "smith_wolfram_sword");
        Recorder recorder = new Recorder(player);
        NeoForge.EVENT_BUS.register(recorder);
        try {
            ClassService.chooseClass(player, 0, Mitofus.id("swordsman"));
            QuestService.accept(player, commission, smith);
            TestSupport.forge(player, ItemRating.EPIC); // also earns the epic_forger title
            QuestService.claim(player, commission, smith);
        } finally {
            NeoForge.EVENT_BUS.unregister(recorder);
        }
        helper.assertTrue(recorder.events.stream().anyMatch(e -> e instanceof ClassChangedEvent c
                && c.playerClass().equals(Mitofus.id("swordsman")) && !c.granted()), "choosing a class: " + recorder.events);
        helper.assertTrue(recorder.events.stream().anyMatch(e -> e instanceof TitleEarnedEvent t
                && t.title().equals(Mitofus.id("epic_forger"))), "an epic forge earns epic_forger: " + recorder.events);
        helper.assertTrue(recorder.events.stream().anyMatch(e -> e instanceof QuestCompletedEvent q
                && q.quest().equals(commission)), "claiming the quest: " + recorder.events);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void discoveringAHiddenQuestPostsItsEventOnce(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Recorder recorder = new Recorder(player);
        NeoForge.EVENT_BUS.register(recorder);
        try {
            for (int i = 0; i < 32; i++) {
                TestSupport.kill(helper, player, EntityType.SKELETON);
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(recorder);
        }
        long discoveries = recorder.events.stream().filter(e -> e instanceof QuestDiscoveredEvent d
                && d.quest().equals(Mitofus.id("call_of_the_night"))).count();
        helper.assertTrue(discoveries == 1, "one discovery at the 30th kill, not one per kill after it: " + discoveries);
        helper.succeed();
    }

    /** The window goes through the network as a packet: it must decode to what was sent. */
    @GameTest(template = TestSupport.EMPTY)
    public static void notificationSurvivesTheNetwork(GameTestHelper helper) {
        SystemNotification sent = new SystemNotification(SystemNotification.Kind.CLASS,
                List.of(Component.translatable("class.mitofus.warrior"), Component.literal("Line two")));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        SystemNotificationPayload.STREAM_CODEC.encode(buffer, new SystemNotificationPayload(sent));
        SystemNotification received = SystemNotificationPayload.STREAM_CODEC.decode(buffer).notification();
        helper.assertTrue(received.equals(sent), "sent " + sent + " but got " + received);
        helper.succeed();
    }

    /** A grand window carries its icon (the forged piece, a nether star...) to the client. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aGrandNotificationKeepsItsIcon(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(ModDataComponents.ITEM_RATING, ItemRating.LEGENDARY);
        SystemNotification sent = new SystemNotification(SystemNotification.Kind.LEGENDARY_ITEM, List.of(Component.literal("Guadalete")), Optional.of(sword));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        SystemNotificationPayload.STREAM_CODEC.encode(buffer, new SystemNotificationPayload(sent));
        SystemNotification received = SystemNotificationPayload.STREAM_CODEC.decode(buffer).notification();
        helper.assertTrue(received.equals(sent) && received.kind().grand(), "sent " + sent + " but got " + received);
        helper.assertTrue(received.icon().orElseThrow().get(ModDataComponents.ITEM_RATING) == ItemRating.LEGENDARY, "with its rating");
        helper.succeed();
    }

    /** The big titles are rare (grand window); the everyday ones are not. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theBigTitlesAreRare(GameTestHelper helper) {
        helper.assertTrue(title(helper, "became_legend").rare() && title(helper, "noble_scourge").rare(), "legend and noble scourge");
        helper.assertFalse(title(helper, "epic_forger").rare(), "an epic forge is not");
        helper.succeed();
    }

    private static TitleDefinition title(GameTestHelper helper, String id) {
        return Definitions.get(helper.getLevel(), ModDatapackRegistries.TITLE, Mitofus.id(id)).orElseThrow();
    }
}
