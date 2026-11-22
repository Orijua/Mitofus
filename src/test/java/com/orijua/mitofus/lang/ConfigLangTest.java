package com.orijua.mitofus.lang;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonObject;
import com.orijua.mitofus.core.config.ClientConfig;
import com.orijua.mitofus.core.config.Config;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NeoForge's config screen names every option and section by its last path element,
 * {@code mitofus.configuration.<name>}, and shows {@code <name>.tooltip} as its help. Without them it shows the
 * raw key, so every option of both configs needs both in each language, and two options may not share a name.
 */
class ConfigLangTest {
    private static final String PREFIX = "mitofus.configuration.";
    private static final List<ModConfigSpec> SPECS = List.of(Config.SPEC, ClientConfig.SPEC);

    @Test
    void everyOptionAndSectionHasANameAndEveryOptionAHelpText() {
        TreeSet<String> missing = new TreeSet<>();
        for (String file : List.of("en_us.json", "es_es.json")) {
            JsonObject lang = LangFiles.load(file);
            for (ModConfigSpec spec : SPECS) {
                walk(spec.getSpec(), (name, isSection) -> {
                    if (!lang.has(PREFIX + name)) {
                        missing.add(file + ": " + PREFIX + name);
                    }
                    if (!isSection && !lang.has(PREFIX + name + ".tooltip")) {
                        missing.add(file + ": " + PREFIX + name + ".tooltip");
                    }
                });
            }
        }
        assertTrue(missing.isEmpty(), "Config keys without translation: " + missing);
    }

    @Test
    void noTwoOptionsShareAName() {
        Set<String> seen = new HashSet<>();
        TreeSet<String> repeated = new TreeSet<>();
        for (ModConfigSpec spec : SPECS) {
            walk(spec.getSpec(), (name, isSection) -> {
                if (!seen.add(name)) {
                    repeated.add(name);
                }
            });
        }
        assertTrue(seen.size() > 100, "The walk found only " + seen.size() + " options and sections: it is not reading the specs");
        assertTrue(repeated.isEmpty(), "These names would share one translation on the config screen: " + repeated);
    }

    private interface Visitor {
        void visit(String name, boolean isSection);
    }

    private static void walk(UnmodifiableConfig config, Visitor visitor) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            if (entry.getRawValue() instanceof UnmodifiableConfig section) {
                visitor.visit(entry.getKey(), true);
                walk(section, visitor);
            } else {
                visitor.visit(entry.getKey(), false);
            }
        }
    }
}
