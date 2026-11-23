package com.orijua.mitofus.feature.talent;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Buying and refunding talent nodes. The nodes bought live in the player's {@link ClassSlot}, so they are saved, synced and
 * cleared together with the class. The server checks every purchase with {@link TalentRules}; the client only asks.
 */
public final class TalentService {
    private TalentService() {
    }

    /** Every talent node that is loaded, by id; the same on server and client. */
    public static Map<ResourceLocation, TalentDefinition> all(Level level) {
        Map<ResourceLocation, TalentDefinition> all = new HashMap<>();
        Definitions.all(level, ModDatapackRegistries.TALENT).forEach(entry -> all.put(entry.id(), entry.value()));
        return all;
    }

    /**
     * Buys {@code node} for the class in {@code slot}. @return the result of the check; anything but OK changed nothing and the
     * player is told why. Only works in the world of Mitofus.
     */
    public static TalentRules.Buy buy(ServerPlayer player, int slot, ResourceLocation node) {
        if (!MitofusWorld.is(player.level())) {
            return TalentRules.Buy.UNKNOWN;
        }
        PlayerClasses classes = ClassService.classes(player);
        Optional<ClassSlot> placed = classes.slot(slot);
        if (placed.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.mitofus.talent_no_class"), true);
            return TalentRules.Buy.UNKNOWN;
        }
        Map<ResourceLocation, TalentDefinition> all = all(player.level());
        TalentRules.Buy result = TalentRules.check(all, placed.get(), node);
        if (result != TalentRules.Buy.OK) {
            player.displayClientMessage(Component.translatable("message.mitofus.talent_" + switch (result) {
                case OWNED -> "owned";
                case LOCKED -> "locked";
                case GRADE -> "grade";
                case POINTS -> "points";
                default -> "unknown";
            }), true);
            return result;
        }
        player.setData(ModAttachments.PLAYER_CLASSES, classes.withSlot(TalentRules.buy(placed.get(), node)));
        for (TalentEffect effect : all.get(node).effects()) {
            if (effect instanceof GrantSkillTalentEffect grant) {
                SkillService.learn(player, grant.skill());
            }
        }
        return result;
    }

    /** Gives back every bought node of every class. @return how many talent points that frees (0 if nothing was bought). */
    public static int refundAll(ServerPlayer player) {
        PlayerClasses classes = ClassService.classes(player);
        Map<ResourceLocation, TalentDefinition> all = all(player.level());
        int refunded = 0;
        PlayerClasses result = classes;
        for (ClassSlot slot : classes.slots()) {
            if (slot.talents().isEmpty()) {
                continue;
            }
            refunded += TalentRules.pointsSpent(all, slot);
            forgetGrantedSkills(player, all, slot);
            result = result.withSlot(slot.withTalents(List.of()));
        }
        if (result != classes) {
            player.setData(ModAttachments.PLAYER_CLASSES, result);
        }
        return refunded;
    }

    /** Takes back the skills the bought nodes taught, unless the class itself gives them. */
    private static void forgetGrantedSkills(ServerPlayer player, Map<ResourceLocation, TalentDefinition> all, ClassSlot slot) {
        List<ResourceLocation> ownSkills = Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, slot.classId())
                .map(playerClass -> playerClass.skills()).orElse(List.of());
        for (ResourceLocation skill : TalentEffects.grantedSkills(all, slot)) {
            if (!ownSkills.contains(skill)) {
                SkillService.forget(player, skill);
            }
        }
    }

    /** Whether any class of the player has bought a node, to know if a refund would do anything. */
    public static boolean hasBought(ServerPlayer player) {
        return ClassService.classes(player).slots().stream().anyMatch(slot -> !slot.talents().isEmpty());
    }
}
