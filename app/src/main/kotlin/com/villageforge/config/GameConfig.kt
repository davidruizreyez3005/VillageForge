package com.villageforge.config

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

object BuildInfo {
    const val VERSION = "1.3"
}

object Theme {
    data class Rgb(val r: Float, val g: Float, val b: Float)

    val GRASS = listOf(
        Rgb(0.15f, 0.32f, 0.08f), Rgb(0.19f, 0.38f, 0.10f), Rgb(0.13f, 0.28f, 0.07f),
        Rgb(0.23f, 0.41f, 0.12f), Rgb(0.17f, 0.30f, 0.09f),
    )
    val CLIFF = listOf(Rgb(0.40f, 0.36f, 0.32f), Rgb(0.33f, 0.30f, 0.27f))
    val BARK = Rgb(0.24f, 0.16f, 0.09f)
    val CANOPY = listOf(Rgb(0.07f, 0.27f, 0.09f), Rgb(0.10f, 0.34f, 0.12f))
    val ORE_COPPER = Rgb(0.72f, 0.34f, 0.16f)
    val ORE_TIN = Rgb(0.80f, 0.82f, 0.85f)
    val ORE_COAL = Rgb(0.07f, 0.07f, 0.08f)
    val ORE_IRON = Rgb(0.30f, 0.33f, 0.40f)

    val PLAYER_SKIN = Rgb(0.87f, 0.62f, 0.47f)
    val PLAYER_TUNIC = Rgb(0.58f, 0.19f, 0.13f)
    val PLAYER_PANTS = Rgb(0.16f, 0.15f, 0.19f)
    val PLAYER_APRON = Rgb(0.21f, 0.13f, 0.09f)

    val PICK_TINTS = listOf(
        Rgb(0.38f, 0.33f, 0.28f), Rgb(0.72f, 0.34f, 0.16f), Rgb(0.72f, 0.50f, 0.25f),
        Rgb(0.30f, 0.33f, 0.40f), Rgb(0.66f, 0.69f, 0.74f), Rgb(0.85f, 0.66f, 0.22f),
    )

    val STALL_WOOD = Rgb(0.35f, 0.24f, 0.15f)
    val STALL_AWNING = Rgb(0.45f, 0.15f, 0.12f)
    val STALL_TRIM = Rgb(0.85f, 0.78f, 0.62f)
    val BIN_WOOD = Rgb(0.48f, 0.36f, 0.22f)
    val BIN_LID = Rgb(0.30f, 0.21f, 0.12f)

    const val ROUGHNESS_TERRAIN = 0.95f
    const val ROUGHNESS_PROP = 0.80f
    const val ROUGHNESS_ORE = 0.55f
    const val METALLIC_ORE = 0.15f
    const val METALLIC_DEFAULT = 0.0f

    val SUN_COLOR = Rgb(1.0f, 0.95f, 0.86f)
    // Tuned against emulator screenshots: 110_000 lux blew the scene out to a
    // near-white wash even with sunny-f/16 exposure. 11_000 lands mid-green.
    const val SUN_INTENSITY_LUX = 11_000f
    val SUN_DIRECTION = floatArrayOf(0.55f, -0.72f, 0.42f)
    val AMBIENT_SKY = Rgb(0.28f, 0.36f, 0.52f)
    val SKY_COLOR = Rgb(0.52f, 0.68f, 0.84f)

    const val CAMERA_YAW_DEGREES = 45f
    const val CAMERA_PITCH_DEGREES = 32f
}

enum class Ore(
    val rockTint: Theme.Rgb,
    val rockHp: Int,
    val respawnSeconds: Int,
    val rawSell: Int,
    val rockScale: Float,
    val requiredPick: Picks,
) {
    COPPER(Theme.ORE_COPPER, 4, 8, 3, 1.0f, Picks.RUSTY),
    TIN(Theme.ORE_TIN, 8, 12, 6, 1.0f, Picks.COPPER),
    COAL(Theme.ORE_COAL, 6, 15, 3, 1.1f, Picks.COPPER),
    IRON(Theme.ORE_IRON, 15, 20, 15, 1.35f, Picks.IRON),
}

enum class Picks(val label: String, val damage: Int, val doubleOreChance: Float, val cost: Int) {
    RUSTY("Rusty Pick", 1, 0.00f, 0),
    COPPER("Copper Pick", 2, 0.00f, 60),
    BRONZE("Bronze Pick", 4, 0.10f, 450),
    IRON("Iron Pick", 8, 0.10f, 3_000),
    STEEL("Steel Pick", 14, 0.25f, 18_000),
    MASTERWORK("Masterwork Pick", 24, 0.25f, 90_000),
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
    const val INTERACT_REACH = 2.2f
}

object Upgrades {
    const val BOOTS_SPEED_PER_LEVEL = 0.75f
    val BOOTS_COSTS = intArrayOf(150, 188, 235, 294, 368)
    val BACKPACK_COSTS = intArrayOf(120, 168, 235, 330)
    val BACKPACK_CAPACITIES = intArrayOf(5, 8, 12, 20, 32)
    fun moveSpeed(bootsLevel: Int): Float = PlayerConfig.MOVE_SPEED + bootsLevel * BOOTS_SPEED_PER_LEVEL
}

object WorldLayout {
    const val VALLEY_WIDTH = 60f
    const val VALLEY_DEPTH = 40f
    const val NORTH_Z = -VALLEY_DEPTH / 2f
    const val SPAWN_X = 0f
    const val SPAWN_Z = 10f
    const val TRADE_POST_X = 0f
    const val TRADE_POST_Z = 14f
    const val BIN_X = -3f
    const val BIN_Z = 6f

    data class RockSpawn(val ore: Ore, val x: Float, val z: Float)

    val rocks: List<RockSpawn> = listOf(
        RockSpawn(Ore.COPPER, -7f, 10f), RockSpawn(Ore.COPPER, 7f, 10f),
        RockSpawn(Ore.COPPER, 0f, 17f), RockSpawn(Ore.COPPER, -6f, 3f),
        RockSpawn(Ore.COPPER, 6f, 3f),
        RockSpawn(Ore.TIN, 22f, -2f), RockSpawn(Ore.TIN, 26f, -8f),
        RockSpawn(Ore.TIN, 19f, -10f),
        RockSpawn(Ore.COAL, -22f, -2f), RockSpawn(Ore.COAL, -26f, -8f),
        RockSpawn(Ore.COAL, -19f, -10f),
        RockSpawn(Ore.IRON, -6f, -14f), RockSpawn(Ore.IRON, 2f, -16f),
        RockSpawn(Ore.IRON, 9f, -13f),
    )

    const val TREE_COUNT = 44
    const val TREE_SEED = 1337

    val trees: List<Pair<Float, Float>> by lazy {
        val rng = kotlin.random.Random(TREE_SEED)
        val placed = ArrayList<Pair<Float, Float>>(TREE_COUNT)
        var attempts = 0
        while (placed.size < TREE_COUNT && attempts < 4_000) {
            attempts++
            val x = (rng.nextFloat() * 2f - 1f) * (VALLEY_WIDTH / 2f - 4f)
            val z = (rng.nextFloat() * 2f - 1f) * (VALLEY_DEPTH / 2f - 4f)
            if (z < NORTH_Z + 6f) continue
            if (dist(x, z, SPAWN_X, SPAWN_Z) < 6f) continue
            if (dist(x, z, TRADE_POST_X, TRADE_POST_Z) < 5f) continue
            if (dist(x, z, BIN_X, BIN_Z) < 4f) continue
            if (rocks.any { dist(x, z, it.x, it.z) < 5f }) continue
            if (placed.any { dist(x, z, it.first, it.second) < 3.5f }) continue
            placed.add(x to z)
        }
        placed
    }

    fun dist(ax: Float, az: Float, bx: Float, bz: Float): Float =
        Math.hypot((ax - bx).toDouble(), (az - bz).toDouble()).toFloat()

    fun groundHeight(x: Float, z: Float): Float {
        val edgeX = max(0f, abs(x) - VALLEY_WIDTH / 2f + 10f) / 10f
        val edgeZ = max(0f, abs(z) - VALLEY_DEPTH / 2f + 8f) / 8f
        val rim = (edgeX * edgeX + edgeZ * edgeZ) * 1.6f
        return rim + noise(x / 9f, z / 9f, 3.7f) * 0.45f
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
