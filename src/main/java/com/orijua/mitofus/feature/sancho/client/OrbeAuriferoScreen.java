package com.orijua.mitofus.feature.sancho.client;

import com.orijua.mitofus.core.client.MitofusConfirmScreen;
import com.orijua.mitofus.feature.sancho.ReadOrbePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Reading Sancho's book can't be undone (the level and stats go back to the start), so it asks first. */
public final class OrbeAuriferoScreen {
    private OrbeAuriferoScreen() {
    }

    public static void confirm() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new MitofusConfirmScreen(minecraft.screen, Component.translatable("screen.mitofus.read_orbe"),
                Component.translatable("screen.mitofus.read_orbe.message"),
                () -> PacketDistributor.sendToServer(ReadOrbePayload.INSTANCE)));
    }
}
