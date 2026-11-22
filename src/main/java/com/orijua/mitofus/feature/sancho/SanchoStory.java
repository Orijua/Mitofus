package com.orijua.mitofus.feature.sancho;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.feature.quest.goal.KillGoal;
import com.orijua.mitofus.feature.quest.goal.MilestoneGoal;
import com.orijua.mitofus.feature.quest.goal.PlayerCounters;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * The ids of Sancho's story: Conde Nuno's secret request, the book in the Altamira Cave, its
 * appraisal, Nuno's anger and reading the book. The milestones are counted by {@code GoalTracker.milestone}; quests
 * and titles in the datapack listen to them.
 */
public final class SanchoStory {
    /** Nuno, once the player is somebody (Smith's chain done), tells them of his secret request. */
    public static final ResourceLocation NUNO_SECRET = Mitofus.id("nuno_secret");
    /** Nuno gave the cave map: separate from the secret, because the map only exists once the world has a cave to point to. */
    public static final ResourceLocation NUNO_MAP_GIVEN = Mitofus.id("nuno_map_given");
    /** The book was appraised: Sancho's legacy is revealed, and Nuno comes for it. */
    public static final ResourceLocation APPRAISED = Mitofus.id("appraised_orbe");
    public static final ResourceLocation BETRAYAL = Mitofus.id("nuno_betrayal");
    public static final ResourceLocation READ = Mitofus.id("read_orbe");

    public static final ResourceLocation SECRET_REQUEST = Mitofus.id("nuno_secret_request");
    public static final ResourceLocation ANGER = Mitofus.id("nuno_anger");
    public static final ResourceLocation LEGACY = Mitofus.id("sancho_legacy");
    /** The last quest of Smith's chain: after it, the player is worth Nuno's attention. */
    public static final ResourceLocation SMITH_CHAIN_END = Mitofus.id("quality_test");
    public static final ResourceLocation SUCCESSOR_JOB = Mitofus.id("legendary_blacksmith");
    public static final ResourceLocation BECAME_LEGEND = Mitofus.id("became_legend");
    public static final ResourceLocation NOBLE_SCOURGE = Mitofus.id("noble_scourge");

    /** Sancho's successor starts from level 1 with 20 stat points more than anyone else. */
    public static final int BONUS_POINTS = 20;

    private SanchoStory() {
    }

    /**
     * Whether Nuno and his knights are after this player: they appraised the book and Nuno is still alive for them.
     * Read from the synced counters, so it works on both sides.
     */
    public static boolean isNunosEnemy(Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        PlayerCounters counters = player.getData(ModAttachments.PLAYER_COUNTERS);
        return counters.get(MilestoneGoal.counterKey(BETRAYAL)) > 0 && !killedNuno(player);
    }

    public static boolean killedNuno(Player player) {
        return player.getData(ModAttachments.PLAYER_COUNTERS).get(KillGoal.counterKey(Optional.of(ModEntities.CONDE_NUNO.get()))) > 0;
    }
}
