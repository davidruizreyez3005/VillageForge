package com.villageforge.config

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

object BuildInfo {
    const val VERSION = "2.0"
}

object Theme {
    data class Rgb(val r: Float, val g: Float, val b: Float)

    val GRASS = listOf(
        Rgb(0.15f, 0.32f, 0.08f), Rgb(0.19f, 0.38f, 0.10f), Rgb(0.13f, 0.28f, 0.07f),
        Rgb(0.23f, 0.41f, 0.12f), Rgb(0.17f, 0.30f, 0.09f),
    )
    /** Drier, rockier floor tint for the north canyon. */
    val CANYON_GRASS = listOf(
        Rgb(0.21f, 0.24f, 0.09f), Rgb(0.25f, 0.27f, 0.11f), Rgb(0.17f, 0.20f, 0.08f),
    )
    val CLIFF = listOf(Rgb(0.40f, 0.36f, 0.32f), Rgb(0.33f, 0.30f, 0.27f))
    val BARK = Rgb(0.24f, 0.16f, 0.09f)
    val CANOPY = listOf(Rgb(0.07f, 0.27f, 0.09f), Rgb(0.10f, 0.34f, 0.12f))
    /** Canyon pines: cooler, darker green. */
    val PINE_CANOPY = listOf(Rgb(0.05f, 0.20f, 0.10f), Rgb(0.07f, 0.26f, 0.13f))

    val ORE_COPPER = Rgb(0.72f, 0.34f, 0.16f)
    val ORE_TIN = Rgb(0.80f, 0.82f, 0.85f)
    val ORE_COAL = Rgb(0.07f, 0.07f, 0.08f)
    val ORE_IRON = Rgb(0.30f, 0.33f, 0.40f)
    val ORE_SILVER = Rgb(0.88f, 0.90f, 0.94f)
    val ORE_GOLD = Rgb(0.95f, 0.74f, 0.20f)
    val ORE_CRYSTAL = Rgb(0.52f, 0.82f, 0.86f)

    val PLAYER_SKIN = Rgb(0.87f, 0.62f, 0.47f)
    val PLAYER_TUNIC = Rgb(0.58f, 0.19f, 0.13f)
    val PLAYER_PANTS = Rgb(0.16f, 0.15f, 0.19f)
    val PLAYER_APRON = Rgb(0.21f, 0.13f, 0.09f)
    val PLAYER_HAIR = Rgb(0.23f, 0.15f, 0.08f)
    val PLAYER_BELT = Rgb(0.13f, 0.09f, 0.06f)
    val PLAYER_BOOT = Rgb(0.30f, 0.20f, 0.11f)
    val SACK = Rgb(0.55f, 0.44f, 0.28f)

    /** One tunic/cap colour pair per hired miner. */
    val MINER_STYLES = listOf(
        Rgb(0.16f, 0.30f, 0.55f), Rgb(0.18f, 0.42f, 0.38f), Rgb(0.48f, 0.36f, 0.14f),
        Rgb(0.38f, 0.22f, 0.46f), Rgb(0.30f, 0.32f, 0.36f),
    )
    val MINER_CAP = Rgb(0.14f, 0.11f, 0.09f)

    val PICK_TINTS = listOf(
        Rgb(0.38f, 0.33f, 0.28f), Rgb(0.72f, 0.34f, 0.16f), Rgb(0.72f, 0.50f, 0.25f),
        Rgb(0.30f, 0.33f, 0.40f), Rgb(0.66f, 0.69f, 0.74f), Rgb(0.85f, 0.66f, 0.22f),
        Rgb(0.45f, 0.78f, 0.82f),
    )

    val STALL_WOOD = Rgb(0.35f, 0.24f, 0.15f)
    val STALL_AWNING = Rgb(0.45f, 0.15f, 0.12f)
    val STALL_TRIM = Rgb(0.85f, 0.78f, 0.62f)
    val BIN_WOOD = Rgb(0.48f, 0.36f, 0.22f)
    val BIN_LID = Rgb(0.30f, 0.21f, 0.12f)

    val FURNACE_STONE = Rgb(0.34f, 0.31f, 0.28f)
    val FURNACE_STONE_DARK = Rgb(0.26f, 0.24f, 0.22f)
    val FURNACE_EMBER = Rgb(1.0f, 0.45f, 0.12f)
    val ANVIL = Rgb(0.16f, 0.17f, 0.20f)
    val ANVIL_STUMP = Rgb(0.30f, 0.21f, 0.12f)
    val COAL_PILE = Rgb(0.05f, 0.05f, 0.06f)
    val GATE_WOOD = Rgb(0.30f, 0.21f, 0.12f)
    val LANTERN_GLOW = Rgb(1.0f, 0.62f, 0.20f)
    val SMOKE = Rgb(0.55f, 0.55f, 0.58f)

    const val ROUGHNESS_TERRAIN = 0.95f
    const val ROUGHNESS_PROP = 0.80f
    const val ROUGHNESS_ORE = 0.55f
    const val ROUGHNESS_METAL = 0.35f
    const val METALLIC_ORE = 0.15f
    const val METALLIC_INGOT = 0.85f
    const val METALLIC_DEFAULT = 0.0f

    val SUN_COLOR = Rgb(1.0f, 0.95f, 0.86f)
    const val SUN_INTENSITY_LUX = 11_000f
    val SUN_DIRECTION = floatArrayOf(0.55f, -0.72f, 0.42f)
    val AMBIENT_SKY = Rgb(0.28f, 0.36f, 0.52f)
    val SKY_COLOR = Rgb(0.52f, 0.68f, 0.84f)

    const val CAMERA_YAW_DEGREES = 45f
    const val CAMERA_PITCH_DEGREES = 32f
}

/** Raw ore veins. The last three only exist in the north canyon. */
enum class Ore(
    val rockTint: Theme.Rgb,
    val rockHp: Int,
    val respawnSeconds: Int,
    val rawSell: Int,
    val rockScale: Float,
    val requiredPick: Picks,
    val rockGlow: Float,
) {
    COPPER(Theme.ORE_COPPER, 4, 8, 3, 1.0f, Picks.RUSTY, 0f),
    TIN(Theme.ORE_TIN, 8, 12, 6, 1.0f, Picks.COPPER, 0f),
    COAL(Theme.ORE_COAL, 6, 15, 3, 1.1f, Picks.COPPER, 0f),
    IRON(Theme.ORE_IRON, 15, 20, 15, 1.35f, Picks.IRON, 0f),
    SILVER(Theme.ORE_SILVER, 22, 28, 45, 1.05f, Picks.IRON, 0.05f),
    GOLD(Theme.ORE_GOLD, 30, 35, 110, 1.15f, Picks.STEEL, 0.12f),
    CRYSTAL(Theme.ORE_CRYSTAL, 45, 45, 300, 1.45f, Picks.MASTERWORK, 0.35f),
}

/** Smelting products; the furnace turns ore into these over time. */
enum class Metal(
    val label: String,
    val recipe: List<Pair<Ore, Int>>,
    val smeltSeconds: Float,
    val sell: Int,
    val tint: Theme.Rgb,
) {
    COPPER_INGOT("Copper Ingot", listOf(Ore.COPPER to 2, Ore.COAL to 1), 10f, 11, Theme.ORE_COPPER),
    TIN_INGOT("Tin Ingot", listOf(Ore.TIN to 2, Ore.COAL to 1), 12f, 17, Theme.Rgb(0.83f, 0.85f, 0.88f)),
    BRONZE_INGOT("Bronze Ingot", listOf(Ore.COPPER to 1, Ore.TIN to 1, Ore.COAL to 1), 16f, 18, Theme.Rgb(0.72f, 0.50f, 0.25f)),
    IRON_INGOT("Iron Ingot", listOf(Ore.IRON to 2, Ore.COAL to 2), 22f, 44, Theme.Rgb(0.35f, 0.38f, 0.45f)),
    SILVER_INGOT("Silver Ingot", listOf(Ore.SILVER to 2, Ore.COAL to 2), 30f, 110, Theme.Rgb(0.90f, 0.92f, 0.96f)),
    GOLD_INGOT("Gold Ingot", listOf(Ore.GOLD to 2, Ore.COAL to 2), 40f, 250, Theme.Rgb(0.98f, 0.78f, 0.22f)),
}

/** Finished goods hammered out at the anvil. */
enum class Item(
    val label: String,
    val metals: List<Pair<Metal, Int>>,
    val crystal: Int,
    val craftSeconds: Float,
    val sell: Int,
) {
    HORSESHOES("Horseshoes", listOf(Metal.COPPER_INGOT to 2), 0, 8f, 30),
    BRONZE_DAGGER("Bronze Dagger", listOf(Metal.BRONZE_INGOT to 2, Metal.COPPER_INGOT to 1), 0, 12f, 60),
    IRON_SWORD("Iron Sword", listOf(Metal.IRON_INGOT to 2), 0, 14f, 100),
    IRON_WARHAMMER("Iron Warhammer", listOf(Metal.IRON_INGOT to 3, Metal.COPPER_INGOT to 1), 0, 18f, 140),
    SILVER_RING("Silver Ring", listOf(Metal.SILVER_INGOT to 2), 0, 16f, 300),
    GOLD_CROWN("Gold Crown", listOf(Metal.GOLD_INGOT to 3, Metal.SILVER_INGOT to 1), 0, 24f, 1050),
    CRYSTAL_BLADE("Crystal Blade", listOf(Metal.IRON_INGOT to 2, Metal.GOLD_INGOT to 2), 3, 30f, 2400),
}

enum class Picks(val label: String, val damage: Int, val doubleOreChance: Float, val cost: Int) {
    RUSTY("Rusty Pick", 1, 0.00f, 0),
    COPPER("Copper Pick", 2, 0.00f, 60),
    BRONZE("Bronze Pick", 4, 0.10f, 450),
    IRON("Iron Pick", 8, 0.10f, 2_400),
    STEEL("Steel Pick", 14, 0.25f, 10_000),
    MASTERWORK("Masterwork Pick", 24, 0.30f, 45_000),
    CRYSTAL("Crystal Pick", 40, 0.40f, 160_000),
}

object PlayerConfig {
    const val MOVE_SPEED = 4.0f
    const val CARRY_CAPACITY = 5
    const val TAPS_PER_SECOND_LIMIT = 8
    const val MINING_REACH = 2.2f
    const val SWING_SECONDS = 0.45f
    const val IMPACT_FRACTION = 0.65f
    const val TURN_RATE = 9.0f
    const val WALK_PHASE_PER_UNIT = 3.5f
}

object Buildings {
    const val BIN_COST = 200
    const val FURNACE_COST = 500
    const val FURNACE_QUEUE = 5
    const val INTERACT_REACH = 2.2f
    const val FORGE_REACH = 2.4f
}

object Upgrades {
    const val BOOTS_SPEED_PER_LEVEL = 0.75f
    val BOOTS_COSTS = intArrayOf(150, 188, 235, 294, 368, 460, 575)
    val BACKPACK_COSTS = intArrayOf(120, 168, 235, 330, 460, 640)
    val BACKPACK_CAPACITIES = intArrayOf(5, 8, 12, 20, 32, 48, 72)
    fun moveSpeed(bootsLevel: Int): Float = PlayerConfig.MOVE_SPEED + bootsLevel * BOOTS_SPEED_PER_LEVEL
}

object Miners {
    val HIRE_COSTS = intArrayOf(2_500, 6_500, 15_000, 35_000, 80_000)
    /** Hired hands borrow your workshop's tools, so they scale with your best pick. */
    fun damage(pickTier: Int): Int = 2 + pickTier / 2
    const val SWING_SECONDS = 0.55f
    /** One ore per hired miner per this many seconds while you are away. */
    const val OFFLINE_SECONDS_PER_ORE = 100f
    const val OFFLINE_CAP_SECONDS = 6f * 3600f
    const val IDLE_COOLDOWN_SECONDS = 3.5f
    /** Ore drop weights for offline mining (filtered by what your pick can mine). */
    val OFFLINE_WEIGHTS = intArrayOf(40, 22, 24, 14, 8, 4, 1)
}

object Progression {
    const val XP_PER_ORE = 4
    const val XP_PER_INGOT = 12
    const val XP_PER_ITEM = 30
    const val XP_PER_3_COINS = 1
    const val MAX_LEVEL = 30
    fun xpForLevel(level: Int): Int = (60f * level.toFloat().pow(1.5f)).toInt().coerceAtLeast(30)
    fun levelUpBonusCoins(level: Int): Int = level * 15
}

/**
 * Slow day/night cycle. `t` in [0,1): dawn ramp, long day, dusk ramp, night.
 * The 20-second CI screenshot lands mid-morning with the default start time.
 */
object DayNight {
    const val CYCLE_SECONDS = 300f
    const val START_TIME = 0.30f
    const val DAWN_END = 0.06f
    const val DAY_END = 0.72f
    const val DUSK_END = 0.80f

    const val DAY_SUN_LUX = 11_000f
    const val NIGHT_SUN_LUX = 140f
    const val DAY_AMBIENT_LUX = 3_000f
    const val NIGHT_AMBIENT_LUX = 420f

    val DAY_SKY = Theme.Rgb(0.52f, 0.68f, 0.84f)
    val DUSK_SKY = Theme.Rgb(0.84f, 0.48f, 0.34f)
    val NIGHT_SKY = Theme.Rgb(0.04f, 0.06f, 0.12f)
    val DAY_SUN = Theme.Rgb(1.0f, 0.95f, 0.86f)
    val DUSK_SUN = Theme.Rgb(1.0f, 0.52f, 0.22f)
    val NIGHT_SUN = Theme.Rgb(0.36f, 0.46f, 0.70f)

    /** 0 = full day, 1 = deep night. Used to drive lantern/fire glow. */
    fun nightness(t: Float): Float = when {
        t < DAWN_END -> 1f - t / DAWN_END * 0.5f          // night easing into dawn
        t < DAY_END -> 0f
        t < DUSK_END -> (t - DAY_END) / (DUSK_END - DAY_END)
        else -> 1f
    }

    fun isNightish(t: Float): Boolean = nightness(t) > 0.5f
}

object WorldLayout {
    const val VALLEY_WIDTH = 60f
    const val VALLEY_Z_MAX = 20f
    const val VALLEY_Z_MIN = -20f
    const val CANYON_Z_MIN = -46f
    const val CANYON_Z_MAX = -25f
    const val CANYON_HALF_W = 13f
    const val PASS_HALF_W = 7.5f
    const val PASS_Z_MIN = -25f
    const val PASS_Z_MAX = -19f
    const val NORTH_Z = VALLEY_Z_MIN
    const val TERRAIN_CELL = 1.5f

    const val SPAWN_X = 0f
    const val SPAWN_Z = 10f
    const val TRADE_POST_X = 0f
    const val TRADE_POST_Z = 14f
    const val BIN_X = -3f
    const val BIN_Z = 6f
    const val FURNACE_X = 5.2f
    const val FURNACE_Z = 9.5f
    const val ANVIL_X = 3.1f
    const val ANVIL_Z = 8.2f
    const val GATE_Z = -19.2f

    data class RockSpawn(val ore: Ore, val x: Float, val z: Float)

    val rocks: List<RockSpawn> = listOf(
        // South valley
        RockSpawn(Ore.COPPER, -7f, 10f), RockSpawn(Ore.COPPER, 7f, 10f),
        RockSpawn(Ore.COPPER, 0f, 17f), RockSpawn(Ore.COPPER, -6f, 3f),
        RockSpawn(Ore.COPPER, 6f, 3f),
        RockSpawn(Ore.TIN, 22f, -2f), RockSpawn(Ore.TIN, 26f, -8f),
        RockSpawn(Ore.TIN, 19f, -10f),
        RockSpawn(Ore.COAL, -22f, -2f), RockSpawn(Ore.COAL, -26f, -8f),
        RockSpawn(Ore.COAL, -19f, -10f),
        RockSpawn(Ore.IRON, -6f, -14f), RockSpawn(Ore.IRON, 2f, -16f),
        RockSpawn(Ore.IRON, 9f, -13f),
        // North canyon
        RockSpawn(Ore.SILVER, -6f, -30f), RockSpawn(Ore.SILVER, 5f, -33f),
        RockSpawn(Ore.SILVER, -2f, -38f), RockSpawn(Ore.SILVER, 9f, -28f),
        RockSpawn(Ore.GOLD, -9f, -36f), RockSpawn(Ore.GOLD, 0f, -41f),
        RockSpawn(Ore.GOLD, 7f, -37f),
        RockSpawn(Ore.CRYSTAL, -5f, -42f), RockSpawn(Ore.CRYSTAL, 10f, -34f),
    )

    const val TREE_COUNT = 44
    const val TREE_SEED = 1337

    /** Hand-placed pines inside the canyon. */
    val canyonPines: List<Pair<Float, Float>> = listOf(
        -11f to -27f, 11.5f to -27.5f, -9f to -34f, 10.5f to -40f,
        -11.5f to -42f, 4f to -44f, 11f to -44f, -4f to -26.5f, 8f to -31f,
    )

    val trees: List<Pair<Float, Float>> by lazy {
        val rng = kotlin.random.Random(TREE_SEED)
        val placed = ArrayList<Pair<Float, Float>>(TREE_COUNT)
        var attempts = 0
        while (placed.size < TREE_COUNT && attempts < 4_000) {
            attempts++
            val x = (rng.nextFloat() * 2f - 1f) * (VALLEY_WIDTH / 2f - 4f)
            val z = rng.nextFloat() * (VALLEY_Z_MAX - 6f) + (VALLEY_Z_MIN + 6f)
            if (z < VALLEY_Z_MIN + 6f) continue
            if (dist(x, z, SPAWN_X, SPAWN_Z) < 6f) continue
            if (dist(x, z, TRADE_POST_X, TRADE_POST_Z) < 5f) continue
            if (dist(x, z, BIN_X, BIN_Z) < 4f) continue
            if (dist(x, z, FURNACE_X, FURNACE_Z) < 4f) continue
            if (dist(x, z, ANVIL_X, ANVIL_Z) < 3f) continue
            if (rocks.any { dist(x, z, it.x, it.z) < 5f }) continue
            if (placed.any { dist(x, z, it.first, it.second) < 3.5f }) continue
            placed.add(x to z)
        }
        placed
    }

    fun dist(ax: Float, az: Float, bx: Float, bz: Float): Float =
        Math.hypot((ax - bx).toDouble(), (az - bz).toDouble()).toFloat()

    /** Outside distance from the canyon+pass corridor; 0 inside. */
    private fun rectOutside(x: Float, z: Float, x0: Float, x1: Float, z0: Float, z1: Float): Float {
        val dx = max(0f, max(x0 - x, x - x1))
        val dz = max(0f, max(z0 - z, z - z1))
        return hypot(dx, dz)
    }

    fun corridorOutsideDistance(x: Float, z: Float): Float = minOf(
        rectOutside(x, z, -CANYON_HALF_W, CANYON_HALF_W, CANYON_Z_MIN, CANYON_Z_MAX),
        rectOutside(x, z, -PASS_HALF_W, PASS_HALF_W, PASS_Z_MIN, PASS_Z_MAX),
    )

    fun inCanyonZone(x: Float, z: Float): Boolean =
        z < PASS_Z_MAX + 1f && abs(x) < CANYON_HALF_W + 2.5f

    fun groundHeight(x: Float, z: Float): Float {
        var h = noise(x / 9f, z / 9f, 3.7f) * 0.45f
        val d = corridorOutsideDistance(x, z)
        if (d > 0f) {
            // Canyon and pass walls rise steeply out of the corridor.
            h += (d * 1.1f + d * d * 0.20f).coerceAtMost(12f)
        }
        if (d > 2f) {
            // Original valley rims (kept off inside the corridor).
            val edgeX = max(0f, abs(x) - VALLEY_WIDTH / 2f + 10f) / 10f
            val edgeS = max(0f, z - (VALLEY_Z_MAX - 8f)) / 8f
            val edgeN = if (z < VALLEY_Z_MIN) (VALLEY_Z_MIN - z) / 10f else 0f
            h += (edgeX * edgeX + edgeS * edgeS + edgeN * edgeN) * 1.6f
        }
        // Rockier floor detail inside the canyon proper.
        if (z < CANYON_Z_MAX + 1f && abs(x) < CANYON_HALF_W - 1f && d <= 0.5f) {
            h += noise(x / 4f, z / 4f, 9.1f) * 0.30f
        }
        return h
    }

    fun noise(x: Float, z: Float, seed: Float): Float {
        val gx = floor(x.toDouble()).toInt()
        val gz = floor(z.toDouble()).toInt()
        val fx = x - gx.toFloat()
        val fz = z - gz.toFloat()
        val sx = fx * fx * (3f - 2f * fx)
        val sz = fz * fz * (3f - 2f * fz)
        val a = hash01(gx, gz, seed)
        val b = hash01(gx + 1, gz, seed)
        val c = hash01(gx, gz + 1, seed)
        val d = hash01(gx + 1, gz + 1, seed)
        return (a + (b - a) * sx + (c - a) * sz + (a - b - c + d) * sx * sz) * 2f - 1f
    }

    fun hash01(x: Int, z: Int, seed: Float): Float {
        var h = x * 374761393 + z * 668265263 + (seed * 10_000f).toInt() * 1442695041
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return (h and 0xFFFF).toFloat() / 65535f
    }
}
