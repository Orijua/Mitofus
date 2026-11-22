package com.orijua.mitofus.feature.eibar.client;

import com.orijua.mitofus.core.client.MitofusConfirmScreen;
import com.orijua.mitofus.feature.eibar.ContestRules;
import com.orijua.mitofus.feature.eibar.MenciaNpc;
import com.orijua.mitofus.feature.eibar.StartContestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Starting the contest against Mencia: one piece, against the clock, so it asks first. */
public final class ContestScreen {
    private ContestScreen() {
    }

    public static void confirm(MenciaNpc mencia) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new MitofusConfirmScreen(minecraft.screen, Component.translatable("screen.mitofus.velascos_contest"),
                Component.translatable("screen.mitofus.velascos_contest.message", ContestRules.timeLimitTicks() / 20 / 60),
                () -> PacketDistributor.sendToServer(new StartContestPayload(mencia.getId()))));
    }
}
