package com.orijua.mitofus.feature.world;

import com.orijua.mitofus.Mitofus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The Altamira Cave, where Cid finds Sancho's rare book. One
 * per world, far to the north of Covadonga, in a cold biome when there is one within reach. The cave is not on the
 * map: we put it on the snowy north coast of the West Continent, near the cold target. The place and what is in it
 * are; the layout, where exactly it is and the trap on the way down are our own design.
 * <p>
 * Four pieces joined by jigsaws, each one deeper: an icy mound with the cave mouth, a long descent with a powder snow
 * trap and a stray spawner, the hall of gear (piles and armor stands, all protected: {@link AltamiraProtection}),
 * and the sanctum with the golden table.
 */
public final class Altamira {
    public static final ResourceLocation STRUCTURE = Mitofus.id("altamira");
    public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE, STRUCTURE);
    /** Armor stand {@code DisabledSlots} with every slot locked, to take, swap and put. */
    static final int ALL_SLOTS_DISABLED = 4144959;
    public static final String ENTRANCE = "altamira/entrance";
    public static final String DESCENT = "altamira/descent";
    public static final String HALL = "altamira/hall";
    public static final String SANCTUM = "altamira/sanctum";
    /** Entity tag of the gear in the hall: the system won't let anyone take it. */
    public static final String RELIC_TAG = Mitofus.MODID + ".relic";

    /**
     * Where to look: the cold target on the map, in source pixels. That
     * pixel is in the sea (35 chunks north of Covadonga): it is where the map puts the glacier.
     */
    public static final int MAP_PIXEL_X = 1288;
    public static final int MAP_PIXEL_Z = 178;
    /** Never less than this, in chunks, north of Covadonga (the label is 35 chunks north, in the sea; the snowy coast is nearer). */
    public static final int MIN_DISTANCE = 16;
    /** How many blocks of difference the ground under the mound may have. */
    private static final int MAX_SLOPE = 3;
    /** On the map, land with this relief or more is mountain: the mound almost never fits on it (MAX_SLOPE). */
    static final double MAX_MAP_RELIEF = 8;
    /** The cold biomes the cave prefers (the structure itself may generate anywhere). */
    static final TagKey<Biome> COLD = TagKey.create(Registries.BIOME, Mitofus.id("has_structure/altamira"));

    /** How many blocks the descent goes down. */
    public static final int DROP = 16;
    /** Pieces join where the passage leaves a piece (OUT, deeper) and where the next one starts (IN). */
    private static final String OUT = Mitofus.MODID + ":cave_out";
    private static final String IN = Mitofus.MODID + ":cave_in";
    private static final String NO_POOL = "minecraft:empty";
    private static final String AIR = "minecraft:air";
    static final String TABLE = Mitofus.MODID + ":golden_table[book=true]";

    private Altamira() {
    }

    /** Every piece by its template path (data/mitofus/structure/&lt;path&gt;.nbt). */
    public static Map<String, TemplateBuilder> pieces() {
        Map<String, TemplateBuilder> pieces = new LinkedHashMap<>();
        pieces.put(ENTRANCE, entrance());
        pieces.put(DESCENT, descent());
        pieces.put(HALL, hall());
        pieces.put(SANCTUM, sanctum());
        return pieces;
    }

    /**
     * 15x12x15 mound of ice and stone, its bottom three layers in the ground (the structure starts 3 below the surface).
     * The mouth faces south; a straight passage leads to the back, where the descent joins.
     */
    static TemplateBuilder entrance() {
        TemplateBuilder piece = new TemplateBuilder(15, 12, 15);
        rock(piece, 0, 0, 0, 14, 2, 14);
        rock(piece, 0, 3, 0, 14, 6, 12);
        rock(piece, 1, 7, 0, 13, 8, 11);
        rock(piece, 3, 9, 1, 11, 10, 9);
        rock(piece, 5, 11, 2, 9, 11, 7);
        piece.fill(0, 7, 12, 14, 7, 12, "minecraft:snow_block")
                .fill(1, 9, 10, 13, 9, 11, "minecraft:snow_block")
                .fill(3, 11, 8, 11, 11, 9, "minecraft:snow_block")
                // The mouth and the passage, a snowy floor at the surface.
                .fill(6, 2, 0, 8, 2, 14, "minecraft:snow_block")
                .fill(6, 3, 0, 8, 5, 14, AIR)
                .fill(5, 3, 12, 9, 6, 14, AIR)
                .set(5, 3, 11, "minecraft:bone_block[axis=x]")
                .set(9, 4, 11, "minecraft:cobweb")
                .set(7, 5, 6, "minecraft:soul_lantern[hanging=true]");
        return piece.jigsaw(7, 2, 0, "north_up", OUT, IN, pool("descent"), "minecraft:snow_block");
    }

    /**
     * A 5-wide passage that goes {@link #DROP} blocks down to the north, one step per block, with a powder snow pit half
     * way (one block deep: it slows and freezes, it doesn't trap) and a stray spawner in the wall at the bottom.
     */
    static TemplateBuilder descent() {
        int top = DROP + 1;
        int sizeZ = DROP + 7;
        TemplateBuilder piece = new TemplateBuilder(5, DROP + 6, sizeZ);
        rock(piece, 0, 0, 0, 4, DROP + 5, sizeZ - 1);
        for (int z = 0; z < sizeZ; z++) {
            int floor = stepFloor(z);
            String step = z >= 4 && z < 4 + DROP ? "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight]"
                    : "minecraft:stone_bricks";
            piece.fill(1, floor, z, 3, floor, z, step).fill(1, floor + 1, z, 3, floor + 3, z, AIR);
        }
        int pit = 4 + DROP / 2;
        piece.fill(1, stepFloor(pit), pit, 3, stepFloor(pit), pit, "minecraft:powder_snow")
                .set(2, stepFloor(12) + 3, 12, "minecraft:soul_lantern[hanging=true]")
                .set(2, stepFloor(4 + DROP) + 3, 4 + DROP, "minecraft:soul_lantern[hanging=true]")
                .spawner(0, 2, 2, "minecraft:stray")
                .set(4, 2, 1, "minecraft:bone_block[axis=y]");
        piece.jigsaw(2, top, sizeZ - 1, "south_up", IN, OUT, NO_POOL, "minecraft:stone_bricks");
        return piece.jigsaw(2, 1, 0, "north_up", OUT, IN, pool("hall"), "minecraft:stone_bricks");
    }

    /** The floor of the descent at row {@code z}: flat at the bottom (z 0-3), one step up per row, flat again at the top. */
    static int stepFloor(int z) {
        return 1 + Math.clamp(z - 4L, 0, DROP);
    }

    /**
     * The hall of gear: 21x9x21 inside, ice pillars in the corners, four piles of old equipment with an armored stand on
     * each, and armored stands lining the aisle to the sanctum. None of it can be taken.
     */
    static TemplateBuilder hall() {
        TemplateBuilder piece = new TemplateBuilder(23, 12, 23);
        rock(piece, 0, 0, 0, 22, 11, 22);
        piece.fill(1, 2, 1, 21, 10, 21, AIR);
        for (int z = 1; z <= 21; z++) {
            for (int x = 1; x <= 21; x++) {
                piece.set(x, 1, z, Math.floorMod(x * 7 + z * 13, 4) == 0 ? "minecraft:packed_ice" : "minecraft:polished_andesite");
            }
        }
        piece.fill(10, 1, 0, 12, 1, 22, "minecraft:stone_bricks")
                .fill(10, 2, 0, 12, 4, 0, AIR)
                .fill(10, 2, 22, 12, 4, 22, AIR);
        for (int[] corner : new int[][]{{2, 2}, {20, 2}, {2, 20}, {20, 20}}) {
            piece.fill(corner[0], 2, corner[1], corner[0], 10, corner[1], "minecraft:packed_ice");
        }
        for (int[] lamp : new int[][]{{6, 4}, {16, 4}, {11, 11}, {6, 18}, {16, 18}}) {
            piece.set(lamp[0], 10, lamp[1], "minecraft:soul_lantern[hanging=true]");
        }
        String[] sets = {"iron", "chainmail", "golden", "diamond"};
        int[][] piles = {{5, 8}, {5, 15}, {17, 8}, {17, 15}};
        for (int i = 0; i < piles.length; i++) {
            int top = pile(piece, piles[i][0], piles[i][1], 3);
            relic(piece, piles[i][0], top, piles[i][1], sets[i], i * 90f);
        }
        // The aisle: stands facing it from both sides (yaw -90 faces east, 90 west).
        for (int z = 4, i = 0; z <= 18; z += 7, i++) {
            relic(piece, 9, 2, z, sets[i % sets.length], -90f);
            relic(piece, 13, 2, z, sets[(i + 2) % sets.length], 90f);
        }
        piece.jigsaw(11, 1, 22, "south_up", IN, OUT, NO_POOL, "minecraft:stone_bricks");
        return piece.jigsaw(11, 1, 0, "north_up", OUT, IN, pool("sanctum"), "minecraft:stone_bricks");
    }

    /** The innermost room: quartz pillars, a ring of red carpet and, in the middle, the golden table with Sancho's book. */
    static TemplateBuilder sanctum() {
        TemplateBuilder piece = new TemplateBuilder(13, 9, 13);
        rock(piece, 0, 0, 0, 12, 8, 12);
        piece.fill(1, 2, 1, 11, 7, 11, AIR)
                .fill(1, 1, 1, 11, 1, 11, "minecraft:polished_deepslate")
                .fill(5, 2, 12, 7, 4, 12, AIR)
                .fill(5, 1, 12, 7, 1, 12, "minecraft:polished_deepslate");
        for (int[] corner : new int[][]{{2, 2}, {10, 2}, {2, 10}, {10, 10}}) {
            piece.fill(corner[0], 2, corner[1], corner[0], 7, corner[1], "minecraft:quartz_pillar[axis=y]");
        }
        piece.fill(5, 2, 5, 7, 2, 7, "minecraft:red_carpet")
                .set(6, 2, 6, TABLE)
                .set(6, 7, 6, "minecraft:soul_lantern[hanging=true]")
                .set(3, 7, 3, "minecraft:soul_lantern[hanging=true]")
                .set(9, 7, 9, "minecraft:soul_lantern[hanging=true]");
        return piece.jigsaw(6, 1, 12, "south_up", IN, OUT, NO_POOL, "minecraft:polished_deepslate");
    }

    /** Where the cave is looked for on the map, as chunks from Covadonga (Covadonga's chunk is the map's origin). */
    public static ChunkPos mapOffset(MitofusMapData map) {
        return MitofusPlaces.chunkOffset(map, MAP_PIXEL_X, MAP_PIXEL_Z);
    }

    /**
     * Whether the cave assembles at this chunk with its mound in a cold biome, on dry ground that is not too steep: what
     * a fixed site asks of its place. False if the generator doesn't have the cave.
     */
    public static boolean fitsAt(RegistryAccess registries, ChunkGenerator generator, RandomState random, StructureTemplateManager templates,
                                 long seed, LevelHeightAccessor height, ChunkPos chunk) {
        Structure cave = registries.registryOrThrow(Registries.STRUCTURE).get(KEY);
        Predicate<Holder<Biome>> cold = registries.registryOrThrow(Registries.BIOME).getTag(COLD)
                .<Predicate<Holder<Biome>>>map(set -> set::contains).orElse(biome -> false);
        return cave != null && fits(cave, registries, generator, random, templates, seed, height, chunk, cold);
    }

    /** Whether the cave assembles here with its mound in a biome {@code biomes} accepts, on dry ground that is not too steep. */
    private static boolean fits(Structure cave, RegistryAccess registries, ChunkGenerator generator, RandomState random,
                                StructureTemplateManager templates, long seed, LevelHeightAccessor height, ChunkPos chunk,
                                Predicate<Holder<Biome>> biomes) {
        StructureStart start = cave.generate(registries, generator, generator.getBiomeSource(), random, templates, seed, chunk, 0,
                height, biomes);
        if (!start.isValid()) {
            return false;
        }
        BoundingBox mound = start.getPieces().getFirst().getBoundingBox();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (BlockPos corner : List.of(new BlockPos(mound.minX(), 0, mound.minZ()), new BlockPos(mound.maxX(), 0, mound.minZ()),
                new BlockPos(mound.minX(), 0, mound.maxZ()), new BlockPos(mound.maxX(), 0, mound.maxZ()), mound.getCenter())) {
            if (!SiteTerrain.dryAt(generator, random, height, corner.getX(), corner.getZ())) {
                return false; // water
            }
            int surface = generator.getBaseHeight(corner.getX(), corner.getZ(), Heightmap.Types.WORLD_SURFACE_WG, height, random);
            lowest = Math.min(lowest, surface);
            highest = Math.max(highest, surface);
        }
        return highest - lowest <= MAX_SLOPE;
    }

    /** A pile of old gear, highest in the middle; returns the y just above its top, where its stand goes. */
    private static int pile(TemplateBuilder piece, int cx, int cz, int radius) {
        String[] gear = {"minecraft:chain[axis=x]", "minecraft:iron_bars", "minecraft:damaged_anvil[facing=north]",
                "minecraft:chain[axis=z]", "minecraft:chipped_anvil[facing=east]", "minecraft:lightning_rod[facing=up]",
                "minecraft:grindstone[face=floor,facing=north]"};
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int height = radius - Math.max(Math.abs(dx), Math.abs(dz)) + 1;
                for (int y = 2; y < 2 + height; y++) {
                    piece.set(cx + dx, y, cz + dz, gear[Math.floorMod(dx * 3 + dz * 5 + y * 11, gear.length)]);
                }
            }
        }
        return 2 + radius + 1;
    }

    /** An armor stand in a full {@code material} set with a sword, locked and unbreakable, tagged as a relic. */
    private static void relic(TemplateBuilder piece, int x, int y, int z, String material, float yaw) {
        String sword = material.equals("chainmail") ? "stone" : material;
        ListTag armor = new ListTag();
        for (String part : new String[]{"boots", "leggings", "chestplate", "helmet"}) {
            armor.add(stack("minecraft:" + material + "_" + part));
        }
        ListTag hands = new ListTag();
        hands.add(stack("minecraft:" + sword + "_sword"));
        hands.add(new CompoundTag());
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(yaw));
        rotation.add(FloatTag.valueOf(0));
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(RELIC_TAG));
        CompoundTag stand = new CompoundTag();
        stand.put("ArmorItems", armor);
        stand.put("HandItems", hands);
        stand.put("Rotation", rotation);
        stand.put("Tags", tags);
        stand.putBoolean("ShowArms", true);
        stand.putBoolean("Invulnerable", true);
        stand.putInt("DisabledSlots", ALL_SLOTS_DISABLED);
        piece.entity(x, y, z, "minecraft:armor_stand", stand);
    }

    private static CompoundTag stack(String item) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", item);
        stack.putInt("count", 1);
        return stack;
    }

    private static String pool(String name) {
        return Mitofus.MODID + ":altamira/" + name;
    }

    /** Mixed stone, packed ice and andesite, always the same for the same position. */
    private static void rock(TemplateBuilder piece, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int y = y1; y <= y2; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    int mix = Math.floorMod(x * 31 + y * 17 + z * 7, 6);
                    piece.set(x, y, z, mix == 4 ? "minecraft:packed_ice" : mix == 5 ? "minecraft:andesite" : "minecraft:stone");
                }
            }
        }
    }
}
