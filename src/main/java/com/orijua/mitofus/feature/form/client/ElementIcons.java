package com.orijua.mitofus.feature.form.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** The 16x16 icon of an elemental type: {@code textures/gui/element/<type>.png} in the type's own namespace. */
public final class ElementIcons {
    public static final int SIZE = 16;

    private ElementIcons() {
    }

    public static ResourceLocation texture(ResourceLocation type) {
        return ResourceLocation.fromNamespaceAndPath(type.getNamespace(), "textures/gui/element/" + type.getPath() + ".png");
    }

    public static void draw(GuiGraphics graphics, ResourceLocation type, int x, int y) {
        graphics.blit(texture(type), x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
    }
}
