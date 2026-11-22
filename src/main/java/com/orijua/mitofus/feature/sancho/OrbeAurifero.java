package com.orijua.mitofus.feature.sancho;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.quest.PlayerQuests;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.quest.goal.GoalTracker;
import com.orijua.mitofus.feature.skill.PlayerSkills;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.MitofusOnly;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;

/**
 * Reading Sancho's rare book: the reader becomes Sancho's successor, but goes back to level 1 with
 * every stat point and every skill of the old class gone, and {@link SanchoStory#BONUS_POINTS} points to spend. Smithing
 * experience stays. Sancho's legacy is completed on the spot, which grants the legendary job, Guadalete's blueprint and, through its
 * milestone, the title "One Who Became a Legend". The legacy is checked first: a second book is refused untouched
 * (nothing is spent or reset) once the legacy is done, or if it can't be started. It only works in Mitofus: in the
 * real world the book is refused untouched, since the reset is destructive.
 */
public final class OrbeAurifero {
    private OrbeAurifero() {
    }

    /** Reads the book the player carries (and spends it); false if they carry none or the legacy can't be taken. */
    public static boolean read(ServerPlayer player) {
        if (!MitofusWorld.is(player.level())) {
            MitofusOnly.warn(player);
            return false;
        }
        Inventory inventory = player.getInventory();
        int slot = -1;
        for (int i = 0; i < inventory.getContainerSize() && slot < 0; i++) {
            if (inventory.getItem(i).is(ModItems.ORBE_AURIFERO.get())) {
                slot = i;
            }
        }
        if (slot < 0) {
            return false;
        }
        if (QuestService.get(player).isCompleted(SanchoStory.LEGACY)) {
            player.displayClientMessage(Component.translatable("message.mitofus.sancho_book_already_read"), false);
            return false;
        }

        PlayerQuests quests = QuestService.get(player);
        if (!quests.isDiscovered(SanchoStory.LEGACY)) {
            player.setData(ModAttachments.PLAYER_QUESTS, quests.discover(SanchoStory.LEGACY));
        }
        if (!QuestService.get(player).isActive(SanchoStory.LEGACY)) {
            QuestService.accept(player, SanchoStory.LEGACY);
        }
        if (!QuestService.get(player).isActive(SanchoStory.LEGACY)) {
            Mitofus.LOGGER.warn("{} can't start Sancho's legacy: the book is left unread", player.getGameProfile().getName());
            return false;
        }

        inventory.removeItem(slot, 1);
        PlayerStats before = ProgressionService.get(player);
        ProgressionService.set(player, PlayerStats.DEFAULT.withProgress(1, 0, SanchoStory.BONUS_POINTS).withSmithingXp(before.smithingXp()));
        player.setData(ModAttachments.PLAYER_SKILLS, PlayerSkills.EMPTY);

        GoalTracker.milestone(player, SanchoStory.READ);
        QuestService.claim(player, SanchoStory.LEGACY);
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));

        player.displayClientMessage(Component.translatable("message.mitofus.read_orbe"), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }
}
