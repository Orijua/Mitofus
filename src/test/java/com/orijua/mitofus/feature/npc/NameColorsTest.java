package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.feature.mob.BoneLord;
import com.orijua.mitofus.feature.eibar.MenciaNpc;
import com.orijua.mitofus.feature.eibar.VelascoNpc;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NameColorsTest {
    @Test
    void namedNpcsAndBossesAreGold() {
        assertEquals(ChatFormatting.GOLD, NameColors.of(BlacksmithNpc.class));
        assertEquals(ChatFormatting.GOLD, NameColors.of(VillageChiefNpc.class));
        assertEquals(ChatFormatting.GOLD, NameColors.of(VelascoNpc.class));
        assertEquals(ChatFormatting.GOLD, NameColors.of(MenciaNpc.class));
        assertEquals(ChatFormatting.GOLD, NameColors.of(BoneLord.class));
    }

    @Test
    void apprenticesAreGreen() {
        assertEquals(ChatFormatting.GREEN, NameColors.of(ApprenticeSmith.class));
    }

    @Test
    void playersAndOrdinaryMobsKeepTheirColor() {
        assertNull(NameColors.of(Player.class));
        assertNull(NameColors.of(Zombie.class));
        assertNull(NameColors.of(Villager.class));
    }
}
