package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.feature.npc.BlacksmithNpc;
import com.orijua.mitofus.feature.eibar.VelascoNpc;
import com.orijua.mitofus.feature.world.AdventurerCamp;
import com.orijua.mitofus.feature.world.Covadonga;
import com.orijua.mitofus.feature.world.PoolPieces;
import com.orijua.mitofus.feature.world.StartingVillage;
import com.orijua.mitofus.feature.world.StructurePieces;
import com.orijua.mitofus.feature.world.TemplateBuilder;
import com.orijua.mitofus.feature.world.Eibar;
import com.orijua.mitofus.feature.world.WorldSitePlacement;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The structure pipeline: generated pieces, the hand-written worldgen JSON around them, and the camp and Covadonga they make. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class StructureTests {
    /**
     * The farthest, in chunks, Covadonga's site may lie from the usual spawn on the 40 test seeds. Measured: 25 (the search
     * itself stops at TestSupport.VANILLA_TERRAIN_SEARCH_RADIUS = 32), plus a margin of 4. Adjust it if the search or biomes change on purpose.
     */
    private static final int MAX_COVADONGA_DRIFT = 29;

    private StructureTests() {
    }

    /** A test's template is loaded one block above its structure block, which is what the helper's positions count from. */
    private static BlockPos inPiece(int x, int y, int z) {
        return new BlockPos(x, y + 1, z);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void campIsRegisteredWithItsPoolsSetAndBiomes(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(registries.registryOrThrow(Registries.STRUCTURE).containsKey(AdventurerCamp.STRUCTURE), "structure");
        helper.assertTrue(registries.registryOrThrow(Registries.STRUCTURE_SET).containsKey(AdventurerCamp.STRUCTURE), "structure set");
        for (String pool : List.of("adventurer_camp/center", "adventurer_camp/sides")) {
            helper.assertTrue(registries.registryOrThrow(Registries.TEMPLATE_POOL).containsKey(Mitofus.id(pool)), "pool " + pool);
        }
        TagKey<Biome> biomes = TagKey.create(Registries.BIOME, Mitofus.id("has_structure/adventurer_camp"));
        helper.assertTrue(registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS).is(biomes), "spawns in plains");
        helper.assertFalse(registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT).is(biomes), "not in deserts");
        helper.succeed();
    }

    /** Every piece in code has its .nbt (written by gradlew runData), with the same size: rerun datagen if this fails. */
    @GameTest(template = TestSupport.EMPTY)
    public static void everyPieceHasItsNbtFile(GameTestHelper helper) {
        for (Map.Entry<String, TemplateBuilder> piece : StructurePieces.all().entrySet()) {
            StructureTemplate loaded = helper.getLevel().getStructureManager().get(Mitofus.id(piece.getKey())).orElse(null);
            helper.assertTrue(loaded != null, "missing " + piece.getKey() + ".nbt");
            var size = piece.getValue().build(0).getList("size", Tag.TAG_INT);
            helper.assertTrue(loaded.getSize().equals(new Vec3i(size.getInt(0), size.getInt(1), size.getInt(2))),
                    piece.getKey() + " size " + loaded.getSize());
        }
        helper.succeed();
    }

    /** Runs the real jigsaw assembly (no blocks placed): the center plus at least one side piece. */
    @GameTest(template = TestSupport.EMPTY)
    public static void campAssemblesFromItsPieces(GameTestHelper helper) {
        StructureStart start = assemble(helper, AdventurerCamp.STRUCTURE);
        helper.assertTrue(start.isValid(), "the camp should assemble");
        List<StructurePiece> pieces = start.getPieces();
        helper.assertTrue(pieces.size() >= 2 && pieces.size() <= 5, "center + 1 to 4 sides, got " + pieces.size());
        helper.assertTrue(PoolPieces.templateOf(pieces.getFirst()).filter(Mitofus.id(AdventurerCamp.CENTER)::equals).isPresent(),
                "starts at the center: " + pieces.getFirst());
        helper.succeed();
    }

    /** The tent piece itself as the test area: its blocks and its loot chest land in the world. */
    @GameTest(template = AdventurerCamp.TENT)
    public static void tentPieceLoadsWithItsChest(GameTestHelper helper) {
        helper.assertBlockPresent(Blocks.WHITE_WOOL, inPiece(2, 3, 0));
        helper.assertBlockPresent(Blocks.JIGSAW, inPiece(2, 0, 4));
        ChestBlockEntity chest = helper.getBlockEntity(inPiece(2, 1, 1));
        ResourceKey<?> loot = chest.getLootTable();
        helper.assertTrue(loot != null && loot.location().getPath().equals("chests/village/village_weaponsmith"), "loot " + loot);
        helper.succeed();
    }

    @GameTest(template = AdventurerCamp.FORGE)
    public static void forgePieceHasTheSmithingAnvil(GameTestHelper helper) {
        helper.assertBlockPresent(ModBlocks.SMITHING_ANVIL.get(), inPiece(2, 1, 1));
        helper.assertBlockPresent(Blocks.FURNACE, inPiece(1, 1, 0));
        helper.succeed();
    }

    /** Covadonga generates only at the site its world saved, and /locate can follow it there. */
    @GameTest(template = TestSupport.EMPTY)
    public static void covadongaGeneratesOnlyAtItsWorldsSite(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(registries.registryOrThrow(Registries.STRUCTURE).containsKey(Covadonga.STRUCTURE), "structure");
        StructureSet set = registries.registryOrThrow(Registries.STRUCTURE_SET).get(Covadonga.STRUCTURE);
        helper.assertTrue(set != null && set.placement() instanceof WorldSitePlacement site && site.site().equals(WorldSites.COVADONGA), "placed at Covadonga's site");
        WorldSitePlacement placement = (WorldSitePlacement) set.placement();

        long seed = -7_123_456_789L; // no real world in the test server has it
        helper.assertFalse(placement.applyAdditionalChunkRestrictions(0, 0, seed), "a world without a site has no Covadonga");
        ChunkPos site = new ChunkPos(12, -40);
        WorldSites.remember(seed, WorldSites.COVADONGA, site);
        try {
            helper.assertTrue(placement.applyAdditionalChunkRestrictions(0, 0, seed), "a world with a site has it");
            for (ChunkPos region : List.of(new ChunkPos(0, 0), new ChunkPos(500, -300), new ChunkPos(-64, 64))) {
                helper.assertTrue(site.equals(placement.getPotentialStructureChunk(seed, region.x, region.z)),
                        "every region points /locate at the site, from " + region);
            }
        } finally {
            WorldSites.forget(seed, WorldSites.COVADONGA);
        }
        TagKey<Biome> biomes = TagKey.create(Registries.BIOME, Mitofus.id("has_structure/covadonga"));
        helper.assertTrue(registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS).is(biomes), "plains");
        helper.assertFalse(registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.FOREST).is(biomes), "not among trees");
        helper.succeed();
    }

    /**
     * On real overworld terrain (not the flat test world), every one of 40 seeds gets a Covadonga site near its usual spawn:
     * the whole village on dry land, in its biomes. With the old ring placement only 26 of these 40 seeds had one.
     */
    @GameTest(template = TestSupport.EMPTY, timeoutTicks = 4000)
    public static void covadongaFindsASiteInEveryRealWorld(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Holder<NoiseGeneratorSettings> settings = registries.registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        BiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
        LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        int farthest = 0;
        for (long seed = 1; seed <= 40; seed++) {
            RandomState random = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), seed);
            ChunkPos usualSpawn = new ChunkPos(random.sampler().findSpawnPosition());
            Optional<StartingVillage.Site> site = StartingVillage.findSite(registries, generator, random,
                    helper.getLevel().getStructureManager(), seed, height, usualSpawn, TestSupport.VANILLA_TERRAIN_SEARCH_RADIUS);
            helper.assertTrue(site.isPresent(), "no Covadonga site for seed " + seed);
            int distance = usualSpawn.getChessboardDistance(site.get().chunk());
            helper.assertTrue(distance <= MAX_COVADONGA_DRIFT, "Covadonga for seed " + seed + " is " + distance
                    + " chunks from the usual spawn, more than " + MAX_COVADONGA_DRIFT);
            farthest = Math.max(farthest, distance);
        }
        Mitofus.LOGGER.info("Covadonga: a site in all 40 seeds, at most {} chunks from the usual spawn", farthest);
        helper.succeed();
    }

    /** The square, and on its sides Smith's smithy, the chief's house, the mine and at least one house. */
    @GameTest(template = TestSupport.EMPTY)
    public static void covadongaAssemblesAroundItsSquare(GameTestHelper helper) {
        StructureStart start = assemble(helper, Covadonga.STRUCTURE);
        helper.assertTrue(start.isValid(), "Covadonga should assemble");
        List<ResourceLocation> pieces = templates(start);
        helper.assertTrue(pieces.getFirst().equals(Mitofus.id(Covadonga.PLAZA)), "starts at the square: " + pieces);
        for (String building : List.of(Covadonga.SMITHY, Covadonga.CHIEF_HOUSE, Covadonga.MINE)) {
            helper.assertTrue(pieces.stream().filter(Mitofus.id(building)::equals).count() == 1, "one " + building + " in " + pieces);
        }
        helper.assertTrue(pieces.contains(Mitofus.id(Covadonga.HOUSE)) || pieces.contains(Mitofus.id(Covadonga.GARDEN_HOUSE)),
                "houses in " + pieces);
        helper.succeed();
    }

    @GameTest(template = Covadonga.SMITHY)
    public static void smithyPieceHasSmithAtTheAnvil(GameTestHelper helper) {
        helper.assertBlockPresent(ModBlocks.SMITHING_ANVIL.get(), inPiece(3, 1, 3));
        helper.assertBlockPresent(ModBlocks.SMELTER.get(), inPiece(1, 1, 1));
        List<BlacksmithNpc> smiths = helper.getEntities(ModEntities.BLACKSMITH.get());
        helper.assertTrue(smiths.size() == 1, "one blacksmith, got " + smiths.size());
        helper.assertTrue(smiths.getFirst().getCustomName() != null
                && Covadonga.SMITH_NAME.equals(smiths.getFirst().getCustomName().getString()), "named Smith");
        helper.succeed();
    }

    @GameTest(template = Covadonga.CHIEF_HOUSE)
    public static void chiefHousePieceHasTheChief(GameTestHelper helper) {
        helper.assertTrue(helper.getEntities(ModEntities.VILLAGE_CHIEF.get()).size() == 1, "the village chief is home");
        helper.assertBlockPresent(Blocks.LECTERN, inPiece(7, 1, 1));
        helper.succeed();
    }

    @GameTest(template = Covadonga.PLAZA)
    public static void covadongasPlazaHasTheCoachmanAndTheStorekeeper(GameTestHelper helper) {
        helper.assertTrue(helper.getEntities(ModEntities.COACHMAN.get()).size() == 1, "one coachman");
        helper.assertTrue(helper.getEntities(ModEntities.STOREKEEPER.get()).size() == 1, "one storekeeper");
        helper.succeed();
    }

    @GameTest(template = Eibar.SQUARE)
    public static void eibarsSquareHasTheChiefTheCoachmanAndTheStorekeeper(GameTestHelper helper) {
        helper.assertTrue(helper.getEntities(ModEntities.VILLAGE_CHIEF.get()).size() == 1, "Eibar's chief");
        helper.assertTrue(helper.getEntities(ModEntities.COACHMAN.get()).size() == 1, "one coachman");
        helper.assertTrue(helper.getEntities(ModEntities.STOREKEEPER.get()).size() == 1, "one storekeeper");
        helper.succeed();
    }

    /** Eibar's square, and on its sides Velasco's smithy, the Compañía de Levante's office and at least one house. */
    @GameTest(template = TestSupport.EMPTY)
    public static void eibarAssemblesAroundItsSquare(GameTestHelper helper) {
        StructureStart start = assemble(helper, Eibar.STRUCTURE);
        helper.assertTrue(start.isValid(), "Eibar should assemble");
        List<ResourceLocation> pieces = templates(start);
        helper.assertTrue(pieces.getFirst().equals(Mitofus.id(Eibar.SQUARE)), "starts at the square: " + pieces);
        for (String building : List.of(Eibar.VELASCO_SMITHY, Eibar.LEVANTE_OFFICE)) {
            helper.assertTrue(pieces.stream().filter(Mitofus.id(building)::equals).count() == 1, "one " + building + " in " + pieces);
        }
        helper.assertTrue(pieces.stream().filter(Mitofus.id(Eibar.HOUSE)::equals).count() >= 2, "houses in " + pieces);
        StructureSet set = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(Eibar.STRUCTURE);
        helper.assertTrue(set != null && set.placement() instanceof WorldSitePlacement site && site.site().equals(WorldSites.EIBAR),
                "placed at Eibar's site");
        helper.succeed();
    }

    /** The square and Velasco's smithy are found among the placed pieces, and Mencia's spot lies inside the square. */
    @GameTest(template = TestSupport.EMPTY)
    public static void eibarPiecesAreFoundWherePlaced(GameTestHelper helper) {
        StructureStart start = assemble(helper, Eibar.STRUCTURE);
        Optional<Eibar.PlacedPiece> square = Eibar.piece(start, Eibar.SQUARE);
        helper.assertTrue(square.isPresent() && Eibar.piece(start, Eibar.VELASCO_SMITHY).isPresent(), "square and smithy found");
        BlockPos spot = square.get().at(Eibar.RIVAL_SPOT.getX(), Eibar.RIVAL_SPOT.getY(), Eibar.RIVAL_SPOT.getZ());
        helper.assertTrue(start.getPieces().getFirst().getBoundingBox().isInside(spot), "Mencia's spot " + spot + " is on the square");
        helper.succeed();
    }

    /** Once the contest is won, the rebuilt smithy goes over the ruined one: a new anvil, no cobwebs, Velasco still home. */
    @GameTest(template = Eibar.VELASCO_SMITHY)
    public static void velascosSmithyIsRebuiltOverTheRuins(GameTestHelper helper) {
        Eibar.PlacedPiece smithy = new Eibar.PlacedPiece(helper.absolutePos(inPiece(0, 0, 0)), Rotation.NONE);
        helper.assertTrue(smithy.place(helper.getLevel(), Eibar.VELASCO_SMITHY_RESTORED), "placed");
        helper.assertBlockPresent(ModBlocks.SMITHING_ANVIL.get(), inPiece(4, 1, 3));
        helper.assertBlockPresent(Blocks.WATER_CAULDRON, inPiece(2, 1, 5));
        helper.assertBlockNotPresent(Blocks.COBWEB, inPiece(1, 3, 7));
        helper.assertTrue(helper.getEntities(ModEntities.VELASCO.get()).size() == 1, "Velasco is still there, alone");
        helper.succeed();
    }

    /** The contest stage on the square has an anvil for each smith. */
    @GameTest(template = Eibar.SQUARE)
    public static void eibarSquareHasTheContestStage(GameTestHelper helper) {
        helper.assertBlockPresent(ModBlocks.SMITHING_ANVIL.get(), inPiece(6, 2, 13));
        helper.assertBlockPresent(ModBlocks.SMITHING_ANVIL.get(), inPiece(10, 2, 13));
        helper.succeed();
    }

    @GameTest(template = Eibar.VELASCO_SMITHY)
    public static void velascoDrinksInHisRuinedSmithy(GameTestHelper helper) {
        helper.assertBlockPresent(Blocks.DAMAGED_ANVIL, inPiece(4, 1, 3));
        List<VelascoNpc> velascos = helper.getEntities(ModEntities.VELASCO.get());
        helper.assertTrue(velascos.size() == 1, "one Velasco, got " + velascos.size());
        helper.assertTrue(velascos.getFirst().getCustomName() != null
                && Eibar.VELASCO_NAME.equals(velascos.getFirst().getCustomName().getString()), "named Velasco");
        helper.succeed();
    }

    /** Pins the format PoolPieces relies on: a Minecraft update that changes SinglePoolElement.toString() fails here. */
    @GameTest(template = TestSupport.EMPTY)
    public static void poolPiecesReadTheTemplateOfASinglePoolElement(GameTestHelper helper) {
        StructurePoolElement element = StructurePoolElement.single(Mitofus.id("eibar/square").toString())
                .apply(StructureTemplatePool.Projection.RIGID);
        helper.assertTrue(PoolPieces.templateOf(element).filter(Mitofus.id("eibar/square")::equals).isPresent(),
                "could not read the template out of " + element);
        helper.assertTrue(PoolPieces.templateOf(StructurePoolElement.empty().apply(StructureTemplatePool.Projection.RIGID)).isEmpty(), "the empty element has no template");
        helper.succeed();
    }

    /** The template of each piece of a start, in order. */
    private static List<ResourceLocation> templates(StructureStart start) {
        return start.getPieces().stream().map(PoolPieces::templateOf).flatMap(Optional::stream).toList();
    }

    /** Runs a structure's real jigsaw assembly at a fixed seed and spot, without placing any block. */
    private static StructureStart assemble(GameTestHelper helper, ResourceLocation id) {
        ServerLevel level = helper.getLevel();
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return structure.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), 12345L, new ChunkPos(400, 400), 0, level,
                biome -> true);
    }
}
