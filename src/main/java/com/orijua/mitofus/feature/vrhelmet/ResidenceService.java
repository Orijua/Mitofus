package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.npc.VillageChiefNpc;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;

/** Becoming resident of a village through its chief: the place the player comes back to life after dying in Mitofus. */
public final class ResidenceService {
    private ResidenceService() {
    }

    public static Residence of(ServerPlayer player) {
        return player.getData(ModAttachments.RESIDENCE.get());
    }

    public static Optional<BlockPos> posOf(ServerPlayer player) {
        return of(player).pos();
    }

    /** The village the chief belongs to: the nearest of Covadonga and Eibar (Covadonga if neither is known). */
    public static String siteNear(ServerLevel level, BlockPos pos) {
        ChunkPos here = new ChunkPos(pos);
        String best = WorldSites.COVADONGA;
        long bestDistance = Long.MAX_VALUE;
        for (String site : new String[]{WorldSites.COVADONGA, WorldSites.EIBAR}) {
            Optional<ChunkPos> chunk = WorldSites.siteFor(level.getSeed(), site);
            if (chunk.isPresent()) {
                long dx = chunk.get().x - here.x;
                long dz = chunk.get().z - here.z;
                long distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = site;
                }
            }
        }
        return best;
    }

    /** The player asked the chief to be resident: checks the place, the village and the cooldown, then saves it. */
    public static ResidenceRules.Result become(ServerPlayer player, VillageChiefNpc chief) {
        if (!MitofusWorld.is(player.level()) || chief.distanceToSqr(player) > QuestService.GIVER_REACH * QuestService.GIVER_REACH) {
            return ResidenceRules.Result.OK;
        }
        ServerLevel level = player.serverLevel();
        String site = siteNear(level, chief.blockPosition());
        long now = level.getGameTime();
        Residence current = of(player);
        ResidenceRules.Result result = ResidenceRules.check(current, site, now);
        switch (result) {
            case OK -> {
                player.setData(ModAttachments.RESIDENCE.get(), new Residence(Optional.of(player.blockPosition()), site, now));
                player.displayClientMessage(Component.translatable("message.mitofus.residence.set",
                        Component.translatable("site.mitofus." + site)), false);
            }
            case SAME_PLACE -> player.displayClientMessage(Component.translatable("message.mitofus.residence.same",
                    Component.translatable("site.mitofus." + site)), true);
            case COOLDOWN -> player.displayClientMessage(Component.translatable("message.mitofus.residence.cooldown",
                    (ResidenceRules.ticksLeft(current, now) + 1199L) / 1200L), true);
        }
        return result;
    }
}
