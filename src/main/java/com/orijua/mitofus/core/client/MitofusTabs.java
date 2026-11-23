package com.orijua.mitofus.core.client;

import com.orijua.mitofus.feature.economy.client.TradeTab;
import com.orijua.mitofus.feature.npc.client.WorkshopTab;
import com.orijua.mitofus.feature.progression.client.ProfileTab;
import com.orijua.mitofus.feature.progression.client.StatusTab;
import com.orijua.mitofus.feature.quest.client.QuestsTab;
import com.orijua.mitofus.feature.skill.client.SkillsTab;
import com.orijua.mitofus.feature.talent.client.TalentsTab;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.function.Supplier;

/** The tabs of the Mitofus window, in display order. Add a page by writing a MitofusTab and listing it here. */
public final class MitofusTabs {
    private static final List<Supplier<MitofusTab>> TABS = List.of(
            StatusTab::new,
            SkillsTab::new,
            TalentsTab::new,
            ProfileTab::new,
            QuestsTab::new,
            WorkshopTab::new,
            TradeTab::new);

    private MitofusTabs() {
    }

    public static MitofusScreen createScreen() {
        return new MitofusScreen(TABS);
    }

    public static void openQuests() {
        Minecraft.getInstance().setScreen(createScreen().showing(QuestsTab.class));
    }

    public static void openWorkshop() {
        Minecraft.getInstance().setScreen(createScreen().showing(WorkshopTab.class));
    }
}
