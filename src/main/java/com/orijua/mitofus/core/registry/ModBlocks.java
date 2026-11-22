package com.orijua.mitofus.core.registry;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetBlock;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import com.orijua.mitofus.feature.economy.AccountTerminalBlock;
import com.orijua.mitofus.feature.sancho.GoldenTableBlock;
import com.orijua.mitofus.feature.smelting.SmelterBlock;
import com.orijua.mitofus.feature.smithing.SmithingAnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mitofus.MODID);

    public static final DeferredBlock<SmithingAnvilBlock> SMITHING_ANVIL = BLOCKS.registerBlock("smithing_anvil",
            SmithingAnvilBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.ANVIL)
                    .noOcclusion());

    // The smelter: a furnace with a temperature for advanced metals (block entity: SmelterBlockEntity).
    public static final DeferredBlock<SmelterBlock> SMELTER = BLOCKS.registerBlock("smelter",
            SmelterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(SmelterBlock.LIT) ? 13 : 0));

    // The sleeping body of a player who put on the connection helmet (feature/VR helmet): it exists only while they are
    // connected; the helmet item places it and takes it back (it has no item of its own).
    public static final DeferredBlock<VrHelmetBlock> BODY = BLOCKS.registerBlock("body",
            properties -> new VrHelmetBlock(VrHelmetTier.BASIC, properties), vrHelmetProperties());
    public static final DeferredBlock<VrHelmetBlock> ADVANCED_BODY = BLOCKS.registerBlock("advanced_body",
            properties -> new VrHelmetBlock(VrHelmetTier.ADVANCED, properties), vrHelmetProperties());
    public static final DeferredBlock<VrHelmetBlock> DIAMOND_BODY = BLOCKS.registerBlock("diamond_body",
            properties -> new VrHelmetBlock(VrHelmetTier.DIAMOND, properties), vrHelmetProperties());

    /** The body block of a helmet tier. */
    public static VrHelmetBlock bodyOf(VrHelmetTier tier) {
        return switch (tier) {
            case BASIC -> BODY.get();
            case ADVANCED -> ADVANCED_BODY.get();
            case DIAMOND -> DIAMOND_BODY.get();
        };
    }

    private static BlockBehaviour.Properties vrHelmetProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 6.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .noOcclusion();
    }

    // The account terminal: shows the account and the work (feature/economy); works in the real world, like the VR helmet.
    public static final DeferredBlock<AccountTerminalBlock> ACCOUNT_TERMINAL = BLOCKS.registerBlock("account_terminal",
            AccountTerminalBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    // Wolfram ore. Iron tier: around iron height, a bit rarer, plus
    // guaranteed veins in Covadonga's mine (worldgen/placed_feature/ore_wolfram.json + neoforge/biome_modifier).
    public static final DeferredBlock<Block> WOLFRAM_ORE = BLOCKS.registerSimpleBlock("wolfram_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f, 3.0f).requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> DEEPSLATE_WOLFRAM_ORE = BLOCKS.registerSimpleBlock("deepslate_wolfram_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5f, 3.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE));

    // Blue azurite ore: rare and deep
    // under forests (worldgen/placed_feature/ore_azurite.json + neoforge/biome_modifier). Needs a diamond pickaxe.
    public static final DeferredBlock<Block> AZURITE_ORE = BLOCKS.registerSimpleBlock("azurite_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(5.0f, 6.0f).requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> DEEPSLATE_AZURITE_ORE = BLOCKS.registerSimpleBlock("deepslate_azurite_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(6.5f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE));

    // Mithril ore: generated in the Overworld (worldgen/placed_feature/ore_mithril.json + neoforge/biome_modifier).
    public static final DeferredBlock<Block> MITHRIL_ORE = BLOCKS.registerSimpleBlock("mithril_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f, 3.0f).requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> DEEPSLATE_MITHRIL_ORE = BLOCKS.registerSimpleBlock("deepslate_mithril_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5f, 3.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE));

    // The golden table at the back of the Altamira Cave, with Sancho's book on it. Unbreakable and
    // drops nothing; it glows while the book is still there.
    public static final DeferredBlock<GoldenTableBlock> GOLDEN_TABLE = BLOCKS.registerBlock("golden_table",
            GoldenTableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(GoldenTableBlock.BOOK) ? 10 : 4));

    private ModBlocks() {
    }
}
