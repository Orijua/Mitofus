package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.core.validation.ContentIndex;
import com.orijua.mitofus.core.validation.ContentScanner;
import com.orijua.mitofus.core.validation.ContentValidator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class ContentTests {
    private ContentTests() {
    }

    /** Fails CI when a JSON points to a skill or class that doesn't exist. */
    @GameTest(template = TestSupport.EMPTY)
    public static void realContentHasNoBrokenReferences(GameTestHelper helper) {
        List<String> problems = ContentValidator.validate(ContentScanner.index(helper.getLevel().registryAccess(), helper.getLevel().getRecipeManager()));
        helper.assertTrue(problems.isEmpty(), "content problems:\n" + String.join("\n", problems));
        helper.succeed();
    }

    /** Guards the test above: an index that read nothing would pass it without checking anything. */
    @GameTest(template = TestSupport.EMPTY)
    public static void scannerReadsTheRealContent(GameTestHelper helper) {
        ContentIndex content = ContentScanner.index(helper.getLevel().registryAccess(), helper.getLevel().getRecipeManager());
        helper.assertTrue(content.skills().contains(Mitofus.id("sancho_link")), "skills should be read");
        helper.assertTrue(content.advancedJobs().contains(Mitofus.id("legendary_blacksmith")), "advanced jobs should be read");
        helper.assertTrue(content.rewardedJobs().contains(Mitofus.id("legendary_blacksmith")), "job rewards should be read");
        helper.assertTrue(content.references().stream().anyMatch(r -> r.from().startsWith("class ")), "class → skill references");
        helper.assertTrue(content.references().stream().anyMatch(r -> r.from().startsWith("quest ")
                && r.kind() == ContentIndex.Kind.SKILL), "quest skill-book rewards");
        helper.assertTrue(content.references().stream().anyMatch(r -> r.from().equals("blacksmith trades")), "blacksmith books");
        helper.assertTrue(Mitofus.id("smith_firewood").equals(content.questRequirements().get(Mitofus.id("smith_iron"))),
                "quest chains (requires_quest)");
        helper.assertTrue(Mitofus.id("blacksmith").equals(content.questGivers().get(Mitofus.id("smith_firewood"))),
                "quest givers");
        helper.assertTrue(content.questGiverNpcs().contains(Mitofus.id("village_chief")), "NPCs with a quest window");
        helper.assertTrue(content.itemOptions().contains(Mitofus.id("guadalete_bisection")), "item options");
        helper.assertTrue(content.references().stream().anyMatch(r -> r.from().startsWith("item option ")
                && r.kind() == ContentIndex.Kind.SKILL), "skill options → skills");
        helper.assertTrue(content.references().stream().anyMatch(r -> r.from().startsWith("forging recipe ")
                && r.kind() == ContentIndex.Kind.OPTION), "recipes' fixed options");
        helper.assertTrue(content.references().stream().anyMatch(r -> r.from().startsWith("forging recipe ")
                && r.kind() == ContentIndex.Kind.JOB), "recipes' required job");
        helper.assertTrue(content.talents().contains(Mitofus.id("swordsman_root")), "talent nodes");
        helper.assertTrue(content.talentRules().stream().anyMatch(r -> r.playerClass().equals(Mitofus.id("swordsman"))
                && !r.boostedSkills().isEmpty()), "talent boosts of the swordsman");
        helper.succeed();
    }

    /** The swordsman's tree decodes from its JSON (the datapack registry would drop a node that does not decode). */
    @GameTest(template = TestSupport.EMPTY)
    public static void swordsmanTalentTreeLoads(GameTestHelper helper) {
        long nodes = Definitions.all(helper.getLevel(), ModDatapackRegistries.TALENT).stream()
                .filter(entry -> entry.value().playerClass().equals(Mitofus.id("swordsman"))).count();
        helper.assertTrue(nodes == 31, "the swordsman tree should have 31 nodes, found " + nodes);
        helper.succeed();
    }
}
