package com.orijua.mitofus.feature.vrhelmet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orijua.mitofus.feature.vrhelmet.SleepingBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Draws a {@link SleepingBody}: the owner's skin, lying on their back with the head towards where the entity faces. */
public final class SleepingBodyRenderer extends EntityRenderer<SleepingBody> {
    /** How high the lying model's back sits above the ground (the model is 4 pixels thick, centred on its middle). */
    private static final double LIE_HEIGHT = 0.19;

    private final PlayerModel<?> wide;
    private final PlayerModel<?> slim;

    public SleepingBodyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0.4f;
    }

    private static PlayerSkin skinOf(SleepingBody body) {
        UUID owner = body.owner().orElse(null);
        if (owner == null) {
            return DefaultPlayerSkin.get(new UUID(0L, 0L));
        }
        PlayerInfo info = Minecraft.getInstance().getConnection() == null ? null : Minecraft.getInstance().getConnection().getPlayerInfo(owner);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }

    @Override
    public ResourceLocation getTextureLocation(SleepingBody body) {
        return skinOf(body).texture();
    }

    @Override
    public void render(SleepingBody body, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        PlayerSkin skin = skinOf(body);
        PlayerModel<?> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        poseStack.pushPose();
        poseStack.translate(0.0, LIE_HEIGHT, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
        // The usual flip of an entity model (it is drawn upside down in model space).
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);
        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(skin.texture())), packedLight, OverlayTexture.NO_OVERLAY, -1);
        poseStack.popPose();
        super.render(body, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
