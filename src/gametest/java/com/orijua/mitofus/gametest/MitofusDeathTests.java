package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.DeathBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetRules;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetState;
import com.orijua.mitofus.feature.vrhelmet.LogoutService;
import com.orijua.mitofus.feature.vrhelmet.MitofusDeathService;
import com.orijua.mitofus.feature.vrhelmet.MitofusDeaths;
import com.orijua.mitofus.feature.equipment.Appraisal;
import com.orijua.mitofus.feature.equipment.AppraisalService;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.notification.SystemNotification;
import com.orijua.mitofus.feature.notification.SystemNotificationPayload;
import com.orijua.mitofus.feature.sancho.OrbeAurifero;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Dying in Mitofus: the player reappears there with everything, and a second death within the window disconnects them
 * and locks the VR helmet for a while (the lock tests are at the end).
 * The test server's overworld plays Mitofus (see {@link VrHelmetTests}); the clock is injected, a fake player does not tick
 * (the ticks are run by hand) and a death is the same sequence the server runs: the death event, then the new player
 * takes what the old one left ({@code restoreFrom}, which fires the clone event) and the respawn event.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class MitofusDeathTests {
    private static final long START = 100_000L;
    private static final long WINDOW = DeathBalance.DEFAULT.windowTicks();
    private static final long LOCK = DeathBalance.DEFAULT.lockTicks();

    private MitofusDeathTests() {
    }

    private static BlockPos covadonga(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 1, 1));
    }

    private static GlobalPos at(GameTestHelper helper, BlockPos relative) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
    }

    /** Runs {@code body} in Mitofus with the game time at {@code now[0]}, and puts the clock back. */
    private static void inMitofusAt(GameTestHelper helper, long[] now, Runnable body) {
        MitofusDeathService.setClock(server -> now[0]);
        try {
            VrHelmetTests.inMitofus(helper, covadonga(helper), body);
        } finally {
            MitofusDeathService.restoreClock();
        }
    }

    private static ServerPlayer connectedPlayer(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 0));
        BlockPos saved = helper.absolutePos(new BlockPos(2, 1, 2));
        player.setData(ModAttachments.VR_HELMET.get(), new VrHelmetState(Optional.of(vrhelmet),
                Optional.of(GlobalPos.of(helper.getLevel().dimension(), player.blockPosition())), Optional.of(saved)));
        return player;
    }

    /** The death as the server runs it: the health is already 0 when the death event is posted. */
    private static LivingDeathEvent kill(ServerPlayer player) {
        player.setHealth(0.0f);
        return NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
    }

    private static boolean itemsOnTheGround(GameTestHelper helper, ServerPlayer player) {
        return !helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                player.getBoundingBox().inflate(16)).isEmpty();
    }

    /** The death event, then the new player taking what the old one left (fires the clone event). Not yet respawned. */
    private static ServerPlayer dieAndClone(GameTestHelper helper, ServerPlayer old) {
        kill(old);
        ServerPlayer fresh = TestSupport.fakePlayer(helper);
        fresh.restoreFrom(old, false);
        return fresh;
    }

    private static void respawn(ServerPlayer fresh) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(fresh, false));
    }

    private static ServerPlayer die(GameTestHelper helper, ServerPlayer old) {
        ServerPlayer fresh = dieAndClone(helper, old);
        respawn(fresh);
        return fresh;
    }

    private static List<ItemStack> slots(ServerPlayer player) {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            all.add(player.getInventory().getItem(i).copy());
        }
        return all;
    }

    private static boolean sameSlots(List<ItemStack> expected, ServerPlayer player) {
        for (int i = 0; i < expected.size(); i++) {
            if (!ItemStack.matches(expected.get(i), player.getInventory().getItem(i))) {
                return false;
            }
        }
        return expected.size() == player.getInventory().getContainerSize();
    }

    private static int totalItems(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : slots(player)) {
            total += stack.getCount();
        }
        return total;
    }

    /** Swaps the connection of a fake player for one that keeps the system notifications it is sent. */
    private static List<SystemNotification> recordNotifications(ServerPlayer player) {
        List<SystemNotification> sent = new ArrayList<>();
        new ServerGamePacketListenerImpl(player.server, new Connection(PacketFlow.CLIENTBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof SystemNotificationPayload payload) {
                    sent.add(payload.notification());
                }
            }
        };
        return sent;
    }

    private static TranslatableContents firstLine(SystemNotification notification) {
        return (TranslatableContents) notification.lines().get(0).getContents();
    }

    private static void fill(GameTestHelper helper, ServerPlayer player) {
        Holder<Enchantment> vanishing = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.VANISHING_CURSE);
        ItemStack cursed = new ItemStack(Items.STICK);
        cursed.enchant(vanishing, 1);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        player.getInventory().setItem(3, cursed);
        player.getInventory().setItem(8, new ItemStack(Items.COAL, 64));
        player.getInventory().setItem(20, new ItemStack(Items.IRON_SWORD));
        player.getInventory().armor.set(3, new ItemStack(Items.IRON_HELMET));
        player.getInventory().offhand.set(0, new ItemStack(Items.SHIELD));
    }

    // ---- the first death ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theFirstDeathKeepsEverythingAndStaysConnected(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer old = connectedPlayer(helper);
            fill(helper, old);
            old.experienceLevel = 5;
            old.totalExperience = 55;
            old.experienceProgress = 0.3f;
            List<ItemStack> expected = slots(old);
            VrHelmetState vrHelmetBefore = VrHelmetService.state(old);

            kill(old);
            helper.assertTrue(old.getInventory().isEmpty(), "nothing is left to drop");
            helper.assertTrue(MitofusDeathService.hasKept(old), "the things are put aside");
            LivingExperienceDropEvent xp = NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(old, null, 35));
            helper.assertTrue(xp.isCanceled() && xp.getDroppedExperience() == 0, "no experience orbs");

            ServerPlayer fresh = TestSupport.fakePlayer(helper);
            fresh.restoreFrom(old, false);
            List<SystemNotification> sent = recordNotifications(fresh);
            respawn(fresh);
            helper.assertTrue(sameSlots(expected, fresh), "the new player has the same things in the same slots");
            helper.assertTrue(fresh.experienceLevel == 5 && fresh.totalExperience == 55 && fresh.experienceProgress == 0.3f, "and the same experience");
            helper.assertFalse(MitofusDeathService.hasKept(fresh), "the new player does not inherit the aside copy");
            helper.assertTrue(VrHelmetService.state(fresh).equals(vrHelmetBefore), "still connected, last save intact");
            helper.assertTrue(MitofusDeathService.deaths(fresh).equals(new MitofusDeaths(START, false, 0L)), "counted: " + MitofusDeathService.deaths(fresh));
            helper.assertTrue(sent.size() == 1 && sent.get(0).kind() == SystemNotification.Kind.MITOFUS_DEATH
                    && firstLine(sent.get(0)).getKey().equals("notification.mitofus.mitofus_death.first"), "the first-death notice: " + sent);

            MitofusDeathService.tick(fresh);
            helper.assertTrue(VrHelmetService.state(fresh).equals(vrHelmetBefore), "and nothing disconnects them");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void noItemIsLostOrDuplicated(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer old = connectedPlayer(helper);
            fill(helper, old);
            int before = totalItems(old);
            ServerPlayer fresh = die(helper, old);
            helper.assertTrue(totalItems(fresh) == before, "same number of items: " + totalItems(fresh) + " of " + before);
            helper.assertFalse(itemsOnTheGround(helper, fresh), "nothing fell to the ground");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anUnappraisedPieceKeepsItsAppraisalThroughADeathInMitofus(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer old = connectedPlayer(helper);
            ItemStack untouched = new ItemStack(Items.IRON_SWORD);
            untouched.set(ModDataComponents.ITEM_RATING, ItemRating.EPIC);
            AppraisalService.markUnappraised(untouched, RandomSource.create(31L));
            ItemStack partial = new ItemStack(Items.IRON_SWORD);
            partial.set(ModDataComponents.ITEM_RATING, ItemRating.LEGENDARY);
            AppraisalService.markUnappraised(partial, RandomSource.create(32L));
            AppraisalService.revealNext(partial, 1);
            Appraisal untouchedBefore = untouched.get(ModDataComponents.APPRAISAL);
            Appraisal partialBefore = partial.get(ModDataComponents.APPRAISAL);
            helper.assertTrue(untouchedBefore.revealed().isEmpty() && partialBefore.revealed().size() == 1, "one untouched piece and one partly revealed");
            old.getInventory().setItem(2, untouched.copy());
            old.getInventory().setItem(7, partial.copy());

            ServerPlayer fresh = die(helper, old);
            helper.assertTrue(ItemStack.matches(fresh.getInventory().getItem(2), untouched), "the untouched piece is in its slot");
            helper.assertTrue(untouchedBefore.equals(fresh.getInventory().getItem(2).get(ModDataComponents.APPRAISAL)), "still with nothing revealed");
            helper.assertTrue(ItemStack.matches(fresh.getInventory().getItem(7), partial), "the partly revealed piece is in its slot");
            helper.assertTrue(partialBefore.equals(fresh.getInventory().getItem(7).get(ModDataComponents.APPRAISAL)), "with the same seed and revealed data");
            helper.assertFalse(itemsOnTheGround(helper, fresh), "nothing fell to the ground");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void dyingInMitofusLeavesTheRealWorldRespawnPointAlone(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer old = connectedPlayer(helper);
            BlockPos bed = helper.absolutePos(new BlockPos(5, 1, 5));
            old.setRespawnPosition(net.minecraft.world.level.Level.NETHER, bed, 45.0f, true, false);
            kill(old);
            helper.assertTrue(old.getRespawnPosition() == null, "vanilla finds no point to use up or to complain about");
            // Vanilla then copies the point from the dead player to the new one.
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent(old,
                    new DimensionTransition(helper.getLevel(), old.position(), net.minecraft.world.phys.Vec3.ZERO, 0.0f, 0.0f, DimensionTransition.DO_NOTHING), false));
            ServerPlayer fresh = TestSupport.fakePlayer(helper);
            fresh.restoreFrom(old, false);
            fresh.copyRespawnPosition(old);
            helper.assertTrue(bed.equals(fresh.getRespawnPosition()) && fresh.getRespawnDimension() == net.minecraft.world.level.Level.NETHER
                    && fresh.getRespawnAngle() == 45.0f && fresh.isRespawnForced(), "the new player has the point back, as it was");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theCursorAndTheCraftingGridAreNotLost(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer old = connectedPlayer(helper);
            old.containerMenu.setCarried(new ItemStack(Items.STONE, 3));
            old.inventoryMenu.getSlot(1).set(new ItemStack(Items.COAL, 2));
            ServerPlayer fresh = die(helper, old);
            helper.assertTrue(fresh.getInventory().countItem(Items.STONE) == 3, "the cursor came back");
            helper.assertTrue(fresh.getInventory().countItem(Items.COAL) == 2, "and so did the 2x2 grid");
            helper.assertFalse(itemsOnTheGround(helper, fresh), "nothing fell to the ground");
            helper.assertTrue(old.inventoryMenu.getCraftSlots().isEmpty() && old.containerMenu.getCarried().isEmpty(), "the old menus are empty");
            helper.assertTrue(old.getHealth() <= 0.0f, "and the health is back to 0");
        });
        helper.succeed();
    }

    // With another menu open the 2x2 grid is emptied apart (the container is closed, not the inventory menu).
    @GameTest(template = TestSupport.EMPTY)
    public static void aCraftingGridIsNotLostWithAnotherMenuOpen(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer old = connectedPlayer(helper);
            // A fake player may not open menus through the connection: the menu is set by hand.
            net.minecraft.world.inventory.CraftingMenu menu = new net.minecraft.world.inventory.CraftingMenu(
                    100, old.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
            old.containerMenu = menu;
            menu.setCarried(new ItemStack(Items.STONE, 3));
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 4));
            old.inventoryMenu.getSlot(1).set(new ItemStack(Items.COAL, 2));
            ServerPlayer fresh = die(helper, old);
            helper.assertTrue(fresh.getInventory().countItem(Items.STONE) == 3, "the cursor came back");
            helper.assertTrue(fresh.getInventory().countItem(Items.IRON_INGOT) == 4, "the open crafting table slots too");
            helper.assertTrue(fresh.getInventory().countItem(Items.COAL) == 2, "and the 2x2 grid");
            helper.assertFalse(itemsOnTheGround(helper, fresh), "nothing fell to the ground");
            helper.assertTrue(old.containerMenu == old.inventoryMenu && old.inventoryMenu.getCraftSlots().isEmpty(), "the old menus are closed and empty");
        });
        helper.succeed();
    }

    // ---- where they reappear ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theRespawnGoesToTheLastSaveOrCovadonga(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, covadonga(helper), () -> {
            BlockPos covadonga = covadonga(helper);
            DimensionTransition plaza = MitofusDeathService.respawnTransition(player).orElseThrow();
            helper.assertTrue(plaza.newLevel() == helper.getLevel(), "in Mitofus");
            BlockPos plazaSpot = VrHelmetService.freeSpot(helper.getLevel(), VrHelmetService.spawnFor(player, helper.getLevel()).orElseThrow().pos(), "respawn", player);
            helper.assertTrue(plaza.pos().equals(new Vec3(plazaSpot.getX() + 0.5, plazaSpot.getY(), plazaSpot.getZ() + 0.5)), "no save: Covadonga's plaza, at " + plaza.pos());
            helper.assertTrue(plaza.pos().distanceTo(Vec3.atCenterOf(covadonga)) < 6, "and it is near Covadonga");
            helper.assertTrue(plaza.yRot() == player.getYRot(), "looking the same way");

            BlockPos saved = helper.absolutePos(new BlockPos(2, 1, 2));
            player.setData(ModAttachments.VR_HELMET.get(), VrHelmetState.EMPTY.disconnected(saved));
            VrHelmetRules.Spawn spawn = VrHelmetService.spawnFor(player, helper.getLevel()).orElseThrow();
            helper.assertTrue(spawn.source() == VrHelmetRules.Source.LAST_SAVE, "there is a save to go to");
            DimensionTransition there = MitofusDeathService.respawnTransition(player).orElseThrow();
            BlockPos savedSpot = VrHelmetService.freeSpot(helper.getLevel(), saved, "respawn", player);
            helper.assertTrue(there.pos().equals(new Vec3(savedSpot.getX() + 0.5, savedSpot.getY(), savedSpot.getZ() + 0.5)), "the last save, at " + there.pos());
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theRespawnEventIsOnlyChangedForADeathInMitofus(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ServerLevel level = helper.getLevel();
        DimensionTransition vanilla = new DimensionTransition(level, new Vec3(0, 200, 0), Vec3.ZERO, 0.0f, 0.0f, DimensionTransition.DO_NOTHING);
        VrHelmetTests.outsideMitofus(helper, () -> {
            helper.assertTrue(MitofusDeathService.respawnTransition(player).isEmpty(), "the real world: vanilla respawn");
            PlayerRespawnPositionEvent event = NeoForge.EVENT_BUS.post(new PlayerRespawnPositionEvent(player, vanilla, false));
            helper.assertTrue(event.getDimensionTransition() == vanilla, "the event is untouched");
        });
        VrHelmetTests.inMitofus(helper, covadonga(helper), () -> {
            PlayerRespawnPositionEvent fromEnd = NeoForge.EVENT_BUS.post(new PlayerRespawnPositionEvent(player, vanilla, true));
            helper.assertTrue(fromEnd.getDimensionTransition() == vanilla, "coming from the End fight: untouched");
            PlayerRespawnPositionEvent normal = NeoForge.EVENT_BUS.post(new PlayerRespawnPositionEvent(player, vanilla, false));
            helper.assertTrue(normal.getDimensionTransition() != vanilla
                            && normal.getDimensionTransition().pos().equals(MitofusDeathService.respawnTransition(player).orElseThrow().pos()),
                    "a death in Mitofus: sent to Mitofus");
            helper.assertTrue(normal.copyOriginalSpawnPosition() == !vanilla.missingRespawnBlock(), "the real bed is left alone");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void withNowhereToReappearTheRespawnIsVanillaAndTheBodyIsReleased(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            helper.assertTrue(VrHelmetService.spawnFor(TestSupport.fakePlayer(helper), helper.getLevel()).isPresent(), "there is a landing");
            WorldSites.forgetLanding(helper.getLevel().getSeed());
            ServerPlayer old = connectedPlayer(helper);
            VrHelmetState withSave = VrHelmetService.state(old);
            old.setData(ModAttachments.VR_HELMET.get(), new VrHelmetState(withSave.vrhelmet(), withSave.exit(), Optional.empty()));
            helper.assertTrue(MitofusDeathService.respawnTransition(old).isEmpty(), "no save and no landing: no change");
            old.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
            ServerPlayer fresh = dieAndClone(helper, old);
            helper.assertTrue(fresh.getInventory().countItem(Items.DIAMOND) == 5, "the things are not lost either way");
            List<SystemNotification> sent = recordNotifications(fresh);
            VrHelmetTests.outsideMitofus(helper, () -> {
                respawn(fresh);
                helper.assertTrue(sent.isEmpty(), "no notice outside Mitofus: " + sent);
                helper.assertFalse(VrHelmetService.state(fresh).isConnected(), "reappearing in the real world frees the body");
            });
        });
        helper.succeed();
    }

    // ---- the real world and the window ----

    @GameTest(template = TestSupport.EMPTY)
    public static void dyingInTheRealWorldIsVanilla(GameTestHelper helper) {
        long[] now = {START};
        MitofusDeathService.setClock(server -> now[0]);
        try {
            VrHelmetTests.outsideMitofus(helper, () -> {
                ServerPlayer player = TestSupport.fakePlayer(helper);
                fill(helper, player);
                List<ItemStack> before = slots(player);
                kill(player);
                helper.assertTrue(sameSlots(before, player), "nothing is put aside: vanilla drops them");
                helper.assertFalse(MitofusDeathService.hasKept(player), "no aside copy");
                helper.assertTrue(MitofusDeathService.deaths(player).equals(MitofusDeaths.EMPTY), "it does not count");
                LivingExperienceDropEvent xp = NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(player, null, 35));
                helper.assertFalse(xp.isCanceled() || xp.getDroppedExperience() == 0, "vanilla experience is untouched");
            });
        } finally {
            MitofusDeathService.restoreClock();
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSecondDeathInsideTheWindowDisconnectsOnTheNextTick(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer first = connectedPlayer(helper);
            fill(helper, first);
            List<ItemStack> expected = slots(first);
            ServerPlayer second = die(helper, first);
            now[0] = START + WINDOW - 1;
            ServerPlayer third = dieAndClone(helper, second);
            List<SystemNotification> sent = recordNotifications(third);
            respawn(third);
            helper.assertTrue(sameSlots(expected, third), "the second death keeps everything too");
            helper.assertTrue(MitofusDeathService.deaths(third).equals(new MitofusDeaths(0L, true, 0L)), "pending: " + MitofusDeathService.deaths(third));
            helper.assertTrue(sent.size() == 1 && firstLine(sent.get(0)).getKey().equals("notification.mitofus.mitofus_death.second")
                    && firstLine(sent.get(0)).getArgs()[0].equals(DeathBalance.current().doubleDeathWindowMinutes()), "the second-death notice with the minutes: " + sent);
            helper.assertTrue(VrHelmetService.state(third).isConnected(), "still in the vrhelmet until the tick");

            BlockPos stood = helper.absolutePos(new BlockPos(4, 1, 4));
            third.moveTo(stood.getX() + 0.5, stood.getY(), stood.getZ() + 0.5);
            MitofusDeathService.tick(third);
            VrHelmetState after = VrHelmetService.state(third);
            helper.assertFalse(after.isConnected(), "the vrhelmet disconnected them");
            helper.assertTrue(after.lastSave().equals(Optional.of(stood)), "where they stood is the next save: " + after.lastSave());
            helper.assertFalse(after.lastSave().equals(Optional.of(helper.absolutePos(new BlockPos(2, 1, 2)))), "and not the previous one");
            // The reason (DEATH) only reaches the action bar, and a fake player does not record it: verification pending (N4).
            helper.assertTrue(MitofusDeathService.deaths(third).equals(new MitofusDeaths(0L, false, START + WINDOW - 1 + LOCK)),
                    "the flag is cleared, counting starts over and the lock starts at the disconnection: " + MitofusDeathService.deaths(third));
            MitofusDeathService.tick(third);
            helper.assertTrue(VrHelmetService.state(third).equals(after), "a second tick does nothing");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDeathExactlyAtTheWindowIsAFirstOne(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer second = die(helper, connectedPlayer(helper));
            now[0] = START + WINDOW;
            ServerPlayer third = die(helper, second);
            helper.assertTrue(MitofusDeathService.deaths(third).equals(new MitofusDeaths(START + WINDOW, false, 0L)), "a first one: " + MitofusDeathService.deaths(third));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aZeroWindowNeverDisconnects(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> DeathBalance.withOverride(new DeathBalance(0, 10), () -> {
            ServerPlayer next = die(helper, connectedPlayer(helper));
            for (int i = 0; i < 3; i++) {
                next = die(helper, next);
                helper.assertFalse(MitofusDeathService.deaths(next).pendingDisconnect(), "never pending with a 0 window");
            }
            MitofusDeathService.tick(next);
            helper.assertTrue(VrHelmetService.state(next).isConnected(), "still connected");
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aRealWorldDeathBetweenTwoMitofusDeathsDoesNotCount(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer one = die(helper, connectedPlayer(helper));
            helper.assertTrue(MitofusDeathService.deaths(one).equals(new MitofusDeaths(START, false, 0L)), "the first one is counted");
            ServerPlayer[] real = new ServerPlayer[1];
            VrHelmetTests.outsideMitofus(helper, () -> {
                now[0] = START + 10;
                real[0] = die(helper, one);
                helper.assertTrue(MitofusDeathService.deaths(real[0]).equals(new MitofusDeaths(START, false, 0L)), "the real-world death does not touch the counter");
                helper.assertFalse(MitofusDeathService.hasKept(real[0]), "and nothing is put aside");
            });
            now[0] = START + 20;
            ServerPlayer fresh = dieAndClone(helper, real[0]);
            List<SystemNotification> sent = recordNotifications(fresh);
            respawn(fresh);
            helper.assertTrue(MitofusDeathService.deaths(fresh).pendingDisconnect(), "the third (second in Mitofus) is pending: " + MitofusDeathService.deaths(fresh));
            helper.assertTrue(sent.size() == 1 && firstLine(sent.get(0)).getKey().equals("notification.mitofus.mitofus_death.second"), "with the second-death notice: " + sent);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDeathDuringTheLogoutCountdownCancelsIt(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer old = connectedPlayer(helper);
            LogoutService.forget(old);
            try {
                helper.assertTrue(LogoutService.start(old) == LogoutService.Start.STARTED, "the countdown starts");
                helper.assertTrue(LogoutService.isCountingDown(old), "counting down");
                ServerPlayer fresh = die(helper, old);
                helper.assertFalse(LogoutService.isCountingDown(old), "the death cancels it");
                helper.assertFalse(LogoutService.isCountingDown(fresh), "and the new player has none");
                helper.assertTrue(VrHelmetService.state(fresh).isConnected(), "still connected");
            } finally {
                LogoutService.forget(old);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theDeathAfterADoubleDeathCountsFromZero(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer one = die(helper, connectedPlayer(helper));
            now[0] = START + 10;
            ServerPlayer two = die(helper, one);
            helper.assertTrue(MitofusDeathService.deaths(two).pendingDisconnect(), "the second one is pending");
            MitofusDeathService.tick(two);
            now[0] = START + 20;
            ServerPlayer three = die(helper, two);
            helper.assertTrue(MitofusDeathService.deaths(three).equals(new MitofusDeaths(START + 20, false, START + 10 + LOCK)), "a first one again, the lock kept: " + MitofusDeathService.deaths(three));
            MitofusDeathService.tick(three);
            helper.assertFalse(MitofusDeathService.deaths(three).pendingDisconnect(), "no disconnection");
        });
        helper.succeed();
    }

    // ---- deaths that do not happen, and repeated ones ----

    @GameTest(template = TestSupport.EMPTY)
    public static void aDeathTheLegendTitlePreventsIsNotCounted(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer legend = connectedPlayer(helper);
            legend.getInventory().add(new ItemStack(ModItems.ORBE_AURIFERO.get()));
            OrbeAurifero.read(legend);
            legend.getInventory().setItem(5, new ItemStack(Items.COAL, 4));
            List<ItemStack> before = slots(legend);
            LivingDeathEvent death = kill(legend);
            helper.assertTrue(death.isCanceled() && legend.isAlive(), "the legend is saved and alive");
            helper.assertTrue(sameSlots(before, legend), "nothing put aside");
            helper.assertFalse(MitofusDeathService.hasKept(legend), "no aside copy");
            helper.assertTrue(MitofusDeathService.deaths(legend).equals(MitofusDeaths.EMPTY), "not counted");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDeathCancelledAfterUsGivesEverythingBackOnTheTick(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer player = connectedPlayer(helper);
            fill(helper, player);
            player.setData(ModAttachments.MITOFUS_DEATHS.get(), new MitofusDeaths(START - 5, false, 0L));
            List<ItemStack> expected = slots(player);
            Consumer<LivingDeathEvent> canceller = event -> {
                event.setCanceled(true);
                event.getEntity().setHealth(1.0f); // as a totem would
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, canceller);
            try {
                LivingDeathEvent death = kill(player);
                helper.assertTrue(death.isCanceled(), "another mod cancelled it after us");
            } finally {
                NeoForge.EVENT_BUS.unregister(canceller);
            }
            helper.assertTrue(player.getInventory().isEmpty() && MitofusDeathService.hasKept(player), "meanwhile their things are aside");
            MitofusDeathService.tick(player);
            helper.assertTrue(sameSlots(expected, player), "the tick gives everything back in place");
            helper.assertFalse(MitofusDeathService.hasKept(player), "nothing left aside");
            helper.assertTrue(MitofusDeathService.deaths(player).equals(new MitofusDeaths(START - 5, false, 0L)), "and the counter is as it was");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aCancelledDeathWithNewItemsGivesBackWithoutLosingAny(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer player = connectedPlayer(helper);
            fill(helper, player);
            int before = totalItems(player);
            Consumer<LivingDeathEvent> canceller = event -> {
                event.setCanceled(true);
                event.getEntity().setHealth(1.0f);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, canceller);
            try {
                kill(player);
            } finally {
                NeoForge.EVENT_BUS.unregister(canceller);
            }
            helper.assertTrue(MitofusDeathService.hasKept(player), "their things are aside");
            player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 2));
            MitofusDeathService.tick(player);
            helper.assertTrue(totalItems(player) == before + 2, "nothing lost, and the new item added: " + totalItems(player) + " of " + before);
            helper.assertTrue(player.getInventory().getItem(0).is(Items.EMERALD), "the new item stays in its slot");
            helper.assertFalse(itemsOnTheGround(helper, player), "nothing fell to the ground");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void twoDeathEventsWithoutARespawnLoseNothing(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer old = connectedPlayer(helper);
            fill(helper, old);
            int before = totalItems(old);
            kill(old);
            kill(old);
            ServerPlayer fresh = TestSupport.fakePlayer(helper);
            fresh.restoreFrom(old, false);
            respawn(fresh);
            helper.assertTrue(totalItems(fresh) == before, "no item lost: " + totalItems(fresh) + " of " + before);
            helper.assertTrue(MitofusDeathService.deaths(fresh).equals(new MitofusDeaths(START, false, 0L)), "a repeated event is one death: " + MitofusDeathService.deaths(fresh));
        });
        helper.succeed();
    }

    // ---- saving ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theThingsPutAsideAreSavedWithThePlayerAndSurviveAReset(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer old = connectedPlayer(helper);
            fill(helper, old);
            List<ItemStack> expected = slots(old);
            kill(old);

            CompoundTag saved = old.saveWithoutId(new CompoundTag());
            ServerPlayer other = TestSupport.fakePlayer(helper);
            other.load(saved);
            helper.assertTrue(MitofusDeathService.hasKept(other), "the aside copy is read back");
            helper.assertTrue(MitofusDeathService.deaths(other).equals(new MitofusDeaths(START, false, 0L)), "and the counter");
            ServerPlayer fresh = TestSupport.fakePlayer(helper);
            fresh.restoreFrom(other, false);
            helper.assertTrue(sameSlots(expected, fresh), "the saved copy still gives everything back");

            ModAttachments.resetAll(old);
            helper.assertTrue(MitofusDeathService.hasKept(old), "a reset keeps their belongings");
            helper.assertTrue(MitofusDeathService.deaths(old).equals(MitofusDeaths.EMPTY), "but clears the counter");
        });
        helper.succeed();
    }

    // ---- the pending disconnection ----

    @GameTest(template = TestSupport.EMPTY)
    public static void aPendingDisconnectionIsDoneInMitofusAndOnlyClearedOutside(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer inside = connectedPlayer(helper);
            inside.setData(ModAttachments.MITOFUS_DEATHS.get(), new MitofusDeaths(0L, true, 0L));
            MitofusDeathService.tick(inside);
            helper.assertFalse(VrHelmetService.state(inside).isConnected(), "inherited by a new player in Mitofus: it disconnects");
            helper.assertFalse(MitofusDeathService.deaths(inside).pendingDisconnect(), "once");

            VrHelmetTests.outsideMitofus(helper, () -> {
                ServerPlayer outside = TestSupport.fakePlayer(helper);
                VrHelmetState state = VrHelmetService.state(outside);
                outside.setData(ModAttachments.MITOFUS_DEATHS.get(), new MitofusDeaths(0L, true, 0L));
                MitofusDeathService.tick(outside);
                helper.assertFalse(MitofusDeathService.deaths(outside).pendingDisconnect(), "outside Mitofus the flag is only cleared");
                helper.assertTrue(VrHelmetService.state(outside).equals(state), "and nothing else changes");
            });
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDisconnectionAnotherModCancelsDoesNotLoop(GameTestHelper helper) {
        ServerLevel other = VrHelmetTests.otherLevel(helper);
        if (other == null) {
            helper.fail("BLOCKED: no second level to leave towards");
            return;
        }
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer player = connectedPlayer(helper);
            VrHelmetState connected = VrHelmetService.state(player);
            // The exit is in another level, so leaving really is a trip (a fake player stays put inside one level).
            VrHelmetState viaOther = connected.connected(connected.vrhelmet().orElseThrow(),
                    GlobalPos.of(other.dimension(), other.getSharedSpawnPos()));
            player.setData(ModAttachments.VR_HELMET.get(), viaOther);
            player.setData(ModAttachments.MITOFUS_DEATHS.get(), new MitofusDeaths(0L, true, 0L));
            Consumer<EntityTravelToDimensionEvent> canceller = event -> event.setCanceled(true);
            NeoForge.EVENT_BUS.addListener(canceller);
            try {
                MitofusDeathService.tick(player);
                helper.assertFalse(MitofusDeathService.deaths(player).pendingDisconnect(), "the flag is cleared even if the trip failed");
                helper.assertTrue(VrHelmetService.state(player).equals(viaOther), "the state is as it was");
                MitofusDeathService.tick(player);
                helper.assertTrue(VrHelmetService.state(player).equals(viaOther), "no second attempt");
            } finally {
                NeoForge.EVENT_BUS.unregister(canceller);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aFatigueDisconnectionAndADeathOneDoNotCollide(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer player = connectedPlayer(helper);
            player.setData(ModAttachments.MITOFUS_DEATHS.get(), new MitofusDeaths(0L, true, 0L));
            helper.assertTrue(VrHelmetService.disconnect(player, VrHelmetService.Reason.FATIGUE), "fatigue disconnects first");
            VrHelmetState after = VrHelmetService.state(player);
            // From here on the player is in the real world.
            VrHelmetTests.outsideMitofus(helper, () -> {
                MitofusDeathService.tick(player);
                helper.assertTrue(VrHelmetService.state(player).equals(after), "no second disconnection");
                helper.assertFalse(MitofusDeathService.deaths(player).pendingDisconnect(), "the flag is cleared");
            });
        });
        helper.succeed();
    }

    // ---- the reconnection lock ----

    /** A double death at {@code now[0] + 10}, and the tick that disconnects: the player is left locked (and in the VR helmet-less state). */
    private static ServerPlayer lockedPlayer(GameTestHelper helper, long[] now) {
        ServerPlayer first = die(helper, connectedPlayer(helper));
        now[0] += 10;
        ServerPlayer second = die(helper, first);
        helper.assertTrue(MitofusDeathService.deaths(second).pendingDisconnect(), "the second death is pending");
        MitofusDeathService.tick(second);
        helper.assertFalse(VrHelmetService.state(second).isConnected(), "the double death disconnected them");
        return second;
    }

    private static int command(GameTestHelper helper, ServerPlayer player, String command) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack().withPermission(4));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError(command + ": " + e.getMessage(), e);
        }
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theDoubleDeathDisconnectionLocksTheVrHelmetForTheLockTime(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer player = lockedPlayer(helper, now);
            long unlockAt = START + 10 + LOCK;
            helper.assertTrue(MitofusDeathService.deaths(player).unlockAt() == unlockAt, "unlocks at the disconnection plus 12,000 ticks: " + MitofusDeathService.deaths(player));
            helper.assertTrue(MitofusDeathService.lockRemaining(player) == LOCK, "the whole lock is left: " + MitofusDeathService.lockRemaining(player));
            helper.assertTrue(MitofusDeathService.deaths(player).lastDeathAt() == 0L, "and the death counter went back to 0");
            now[0] = unlockAt - 1;
            helper.assertTrue(MitofusDeathService.lockRemaining(player) == 1L, "one tick before the edge: still locked");
            now[0] = unlockAt;
            helper.assertTrue(MitofusDeathService.lockRemaining(player) == 0L, "at the edge: free");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void beginRefusesWhileLockedAndWorksAtTheEdge(GameTestHelper helper) {
        ServerLevel other = VrHelmetTests.otherLevel(helper);
        if (other == null) {
            helper.fail("BLOCKED: no second level to play Mitofus");
            return;
        }
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer player = lockedPlayer(helper, now);
            long unlockAt = START + 10 + LOCK;
            BlockPos spot = helper.absolutePos(new BlockPos(0, 1, 1));
            helper.assertTrue(((TranslatableContents) VrHelmetService.lockedMessage(player).getContents()).getArgs()[0].equals("10:00"),
                    "the message shows the time as mm:ss: " + VrHelmetService.lockedMessage(player));
            try {
                MitofusWorld.standIn(other);
                helper.assertTrue(VrHelmetService.begin(player, spot) == VrHelmetService.Begin.LOCKED, "locked: refused");
                helper.assertFalse(VrHelmetService.isConnecting(player), "and nothing started");
                now[0] = unlockAt - 1;
                helper.assertTrue(VrHelmetService.begin(player, spot) == VrHelmetService.Begin.LOCKED, "one tick before the edge: still refused");
                now[0] = unlockAt;
                helper.assertTrue(VrHelmetService.begin(player, spot) == VrHelmetService.Begin.STARTED, "exactly at the edge: allowed");
            } finally {
                VrHelmetService.cancel(player, false);
                MitofusWorld.standIn(helper.getLevel());
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void connectRefusesWhileLockedAndWorksAtTheEdge(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer player = lockedPlayer(helper, now);
            long unlockAt = START + 10 + LOCK;
            GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 0));
            now[0] = unlockAt - 1;
            helper.assertFalse(VrHelmetService.connect(player, vrhelmet), "locked: the connection is refused");
            helper.assertFalse(VrHelmetService.state(player).isConnected(), "and nothing changed");
            now[0] = unlockAt;
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "exactly at the edge it connects");
            helper.assertTrue(VrHelmetService.state(player).isConnected(), "connected");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aClockThatWentBackwardsLocksAtMostTheLock(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer player = lockedPlayer(helper, now);
            long unlockAt = START + 10 + LOCK;
            now[0] = 5L; // the world was restored from an older copy: more than the lock is left
            helper.assertTrue(MitofusDeathService.lockRemaining(player) == LOCK, "the lock is cut down to the lock: " + MitofusDeathService.lockRemaining(player));
            GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 0));
            helper.assertFalse(VrHelmetService.connect(player, vrhelmet), "so it does not connect");
            now[0] = unlockAt - LOCK + 1; // from here on less than the lock is left
            helper.assertTrue(MitofusDeathService.lockRemaining(player) == LOCK - 1, "it counts down again: " + MitofusDeathService.lockRemaining(player));
            now[0] = unlockAt;
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "and at the stored edge it connects");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aLoweredLockConfigCutsTheWaitDown(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer player = lockedPlayer(helper, now); // locked with the default 10 min
            DeathBalance lowered = new DeathBalance(10, 5);
            DeathBalance.withOverride(lowered, () -> {
                helper.assertTrue(MitofusDeathService.lockRemaining(player) == lowered.lockTicks(),
                        "cut down to the new lock: " + MitofusDeathService.lockRemaining(player));
                GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 0));
                // The stored unlock time is START + 10 + LOCK; the cut applies while more than the new lock is left.
                now[0] = START + 10 + LOCK - lowered.lockTicks() + 1;
                helper.assertTrue(MitofusDeathService.lockRemaining(player) == lowered.lockTicks() - 1, "counts down from the new max");
                helper.assertFalse(VrHelmetService.connect(player, vrhelmet), "does not connect yet");
                now[0] = START + 10 + LOCK;
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "connects at the stored edge");
            });
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aLockConfigSetToZeroFreesAnActiveLock(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer player = lockedPlayer(helper, now);
            DeathBalance.withOverride(new DeathBalance(10, 0), () -> {
                helper.assertTrue(MitofusDeathService.lockRemaining(player) == 0L, "no lock configured: nothing to wait");
                helper.assertTrue(VrHelmetService.connect(player, at(helper, new BlockPos(0, 1, 0))), "it connects");
            });
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aZeroLockMinutesMeansNoLock(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> DeathBalance.withOverride(new DeathBalance(10, 0), () -> {
            ServerPlayer player = lockedPlayer(helper, now);
            helper.assertTrue(MitofusDeathService.deaths(player).unlockAt() == 0L, "no unlock time stored");
            helper.assertTrue(MitofusDeathService.lockRemaining(player) == 0L, "nothing to wait");
            helper.assertTrue(VrHelmetService.connect(player, at(helper, new BlockPos(0, 1, 0))), "it connects at once");
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aZeroWindowNeitherDisconnectsNorLocks(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> DeathBalance.withOverride(new DeathBalance(0, 10), () -> {
            ServerPlayer next = die(helper, connectedPlayer(helper));
            for (int i = 0; i < 3; i++) {
                now[0] += 10;
                next = die(helper, next);
                MitofusDeathService.tick(next);
            }
            helper.assertTrue(VrHelmetService.state(next).isConnected(), "never disconnected");
            helper.assertTrue(MitofusDeathService.deaths(next).unlockAt() == 0L && MitofusDeathService.lockRemaining(next) == 0L, "and never locked");
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void onlyADeathDisconnectionLocks(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            for (VrHelmetService.Reason reason : List.of(VrHelmetService.Reason.FATIGUE, VrHelmetService.Reason.LOGOUT,
                    VrHelmetService.Reason.MANUAL, VrHelmetService.Reason.DEBUG)) {
                ServerPlayer player = connectedPlayer(helper);
                helper.assertTrue(VrHelmetService.disconnect(player, reason), reason + " disconnects");
                MitofusDeathService.tick(player);
                helper.assertTrue(MitofusDeathService.deaths(player).unlockAt() == 0L && MitofusDeathService.lockRemaining(player) == 0L,
                        reason + " does not lock: " + MitofusDeathService.deaths(player));
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theLockSurvivesDeathAndSavingButNotAReset(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer locked = lockedPlayer(helper, now);
            long unlockAt = START + 10 + LOCK;
            ServerPlayer reborn = die(helper, locked);
            helper.assertTrue(MitofusDeathService.deaths(reborn).unlockAt() == unlockAt, "kept through a death (clone): " + MitofusDeathService.deaths(reborn));

            CompoundTag saved = reborn.saveWithoutId(new CompoundTag());
            ServerPlayer loaded = TestSupport.fakePlayer(helper);
            loaded.load(saved);
            helper.assertTrue(MitofusDeathService.deaths(loaded).unlockAt() == unlockAt, "kept through saving and loading: " + MitofusDeathService.deaths(loaded));

            ModAttachments.resetAll(loaded);
            helper.assertTrue(MitofusDeathService.deaths(loaded).equals(MitofusDeaths.EMPTY) && MitofusDeathService.lockRemaining(loaded) == 0L,
                    "resetAll clears the lock");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theDebugCommandsShowAndClearTheLockAndGotoIsNotBlocked(GameTestHelper helper) {
        long[] now = {START};
        inMitofusAt(helper, now, () -> {
            ServerPlayer player = lockedPlayer(helper, now);
            helper.assertTrue(command(helper, player, "mitofus death") == 1, "the debug death command works while locked");
            helper.assertTrue(command(helper, player, "mitofus goto sierra_nevada") == 1, "goto is not blocked by the lock");
            helper.assertTrue(MitofusDeathService.lockRemaining(player) == LOCK, "and does not change it");
            helper.assertTrue(command(helper, player, "mitofus death reset") == 1, "the reset command works");
            helper.assertTrue(MitofusDeathService.deaths(player).equals(MitofusDeaths.EMPTY) && MitofusDeathService.lockRemaining(player) == 0L,
                    "and clears the lock: " + MitofusDeathService.deaths(player));
            helper.assertTrue(VrHelmetService.connect(player, at(helper, new BlockPos(0, 1, 0))), "so it connects");
        });
        helper.succeed();
    }

    // ---- killed by another player ----

    /** The test server does not read server.properties: PvP is set on purpose and put back. */
    private static void withPvp(ServerPlayer player, boolean allowed, Runnable body) {
        boolean before = player.server.isPvpAllowed();
        player.server.setPvpAllowed(allowed);
        try {
            body.run();
        } finally {
            player.server.setPvpAllowed(before);
        }
    }

    private static void killedBy(ServerPlayer victim, ServerPlayer killer) {
        victim.setHealth(0.0f);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(victim, victim.damageSources().playerAttack(killer)));
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                total++;
            }
        }
        return total;
    }

    private static List<ItemStack> groundItems(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                player.getBoundingBox().inflate(16)).stream().map(net.minecraft.world.entity.item.ItemEntity::getItem).toList();
    }

    private static ServerPlayer equipped(GameTestHelper helper) {
        ServerPlayer victim = connectedPlayer(helper);
        victim.getInventory().items.set(victim.getInventory().selected, new ItemStack(Items.DIAMOND_SWORD));
        victim.getInventory().armor.set(2, new ItemStack(Items.DIAMOND_CHESTPLATE));
        return victim;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void killedByAPlayerOneEquippedPieceFallsAndIsNotDuplicated(GameTestHelper helper) {
        inMitofusAt(helper, new long[]{START}, () -> DeathBalance.withOverride(new DeathBalance(10, 10, 1.0, 1), () -> {
            ServerPlayer victim = equipped(helper);
            ServerPlayer killer = TestSupport.fakePlayer(helper);
            withPvp(victim, true, () -> killedBy(victim, killer));
            ServerPlayer fresh = TestSupport.fakePlayer(helper);
            fresh.restoreFrom(victim, false);
            List<ItemStack> ground = groundItems(helper, victim);
            helper.assertTrue(ground.size() == 1 && (ground.get(0).is(Items.DIAMOND_SWORD) || ground.get(0).is(Items.DIAMOND_CHESTPLATE)),
                    "exactly one equipped piece fell: " + ground);
            helper.assertTrue(count(fresh, Items.DIAMOND_SWORD) + count(fresh, Items.DIAMOND_CHESTPLATE) == 1,
                    "the victim keeps the other one and not the one that fell");
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void killedByAPlayerNothingFallsWithAZeroChance(GameTestHelper helper) {
        inMitofusAt(helper, new long[]{START}, () -> DeathBalance.withOverride(new DeathBalance(10, 10, 0.0, 1), () -> {
            ServerPlayer victim = equipped(helper);
            withPvp(victim, true, () -> killedBy(victim, TestSupport.fakePlayer(helper)));
            ServerPlayer fresh = TestSupport.fakePlayer(helper);
            fresh.restoreFrom(victim, false);
            helper.assertTrue(groundItems(helper, victim).isEmpty(), "nothing fell");
            helper.assertTrue(count(fresh, Items.DIAMOND_SWORD) == 1 && count(fresh, Items.DIAMOND_CHESTPLATE) == 1, "everything kept");
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void killedByAMobOrWithPvpOffNothingFalls(GameTestHelper helper) {
        inMitofusAt(helper, new long[]{START}, () -> DeathBalance.withOverride(new DeathBalance(10, 10, 1.0, 5), () -> {
            ServerPlayer victim = equipped(helper);
            withPvp(victim, true, () -> kill(victim)); // generic damage: not a player
            helper.assertTrue(groundItems(helper, victim).isEmpty(), "a death that is not by a player drops nothing");
            ServerPlayer second = equipped(helper);
            withPvp(second, false, () -> killedBy(second, TestSupport.fakePlayer(helper)));
            helper.assertTrue(groundItems(helper, second).isEmpty(), "with PvP off nothing falls");
        }));
        helper.succeed();
    }
}
