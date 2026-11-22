package com.orijua.mitofus.feature.eibar.client;

import com.orijua.mitofus.feature.eibar.MenciaNpc;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Mencia: the slim player model with her smithing hammer. Provisional: a vanilla player skin until she has her own art. */
public final class MenciaRenderer extends HumanoidMobRenderer<MenciaNpc, HumanoidModel<MenciaNpc>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png");

    public MenciaRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(MenciaNpc entity) {
        return TEXTURE;
    }
}
