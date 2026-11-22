package com.orijua.mitofus.feature.eibar.client;

import com.orijua.mitofus.feature.eibar.LevanteThug;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The Compañía de Levante's thugs: the player's model with a leather jerkin and a sword. Provisional: a vanilla player skin
 * until they have their own art.
 */
public final class LevanteThugRenderer extends HumanoidMobRenderer<LevanteThug, HumanoidModel<LevanteThug>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/zuri.png");

    public LevanteThugRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(LevanteThug entity) {
        return TEXTURE;
    }
}
