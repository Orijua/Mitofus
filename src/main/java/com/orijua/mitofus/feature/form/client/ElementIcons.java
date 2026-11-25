package com.orijua.mitofus.feature.form.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** The 16x16 icon of an elemental type: {@code textures/gui/element/<type>.png} in the type's own namespace. */
public final class ElementIcons {
    public static final int SIZE = 16;

    private ElementIcons() {
    }

    /** The six types are few and drawn every frame, so each texture id is built once. */
    private static final java.util.Map<ResourceLocation, ResourceLocation> TEXTURES = new java.util.concurrent.ConcurrentHashMap<>();

    public static ResourceLocation texture(ResourceLocation type) {
        return TEXTURES.computeIfAbsent(type,
                t -> ResourceLocation.fromNamespaceAndPath(t.getNamespace(), "textures/gui/element/" + t.getPath() + ".png"));
    }

    public static void draw(GuiGraphics graphics, ResourceLocation type, int x, int y) {
        graphics.blit(texture(type), x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
    }
}
