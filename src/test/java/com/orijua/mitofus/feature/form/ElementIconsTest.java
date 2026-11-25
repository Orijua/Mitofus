package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.feature.form.client.ElementIcons;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementIconsTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void everyElementTypeHasA16x16Icon() throws IOException {
        List<String> types;
        try (Stream<Path> files = Files.list(RESOURCES.resolve("data/mitofus/mitofus/element_type"))) {
            types = files.map(p -> p.getFileName().toString().replace(".json", "")).toList();
        }
        assertEquals(6, types.size());
        for (String type : types) {
            ResourceLocation texture = ElementIcons.texture(ResourceLocation.fromNamespaceAndPath("mitofus", type));
            Path file = RESOURCES.resolve("assets/" + texture.getNamespace() + "/" + texture.getPath());
            assertTrue(Files.exists(file), "icon of " + type);
            var image = ImageIO.read(file.toFile());
            assertEquals(ElementIcons.SIZE, image.getWidth(), type);
            assertEquals(ElementIcons.SIZE, image.getHeight(), type);
        }
    }
}
