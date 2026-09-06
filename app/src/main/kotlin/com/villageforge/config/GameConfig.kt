package com.villageforge.config

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

object BuildInfo {
    const val VERSION = "3.1"
}

/**
 * The playtest harness, ON by default: every FRESH game starts with a full
 * purse so builds can be exercised without the grind. Loading a save always
 * restores the saved purse over this.
 */
object DebugConfig {
    const val ENABLED = true
    const val START_GOLD = 20_000
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
    val ORE_IRON = Rgb(0.30f, 0.33f, 0.40f)
    val ORE_SILVER = Rgb(0.88f, 0.90f, 0.94f)
    val ORE_GOLD = Rgb(0.95f, 0.74f, 0.20f)
    /** v3.0 — mythril: a pale, faintly luminous blue-white metal. */
    val ORE_MYTHRIL = Rgb(0.62f, 0.72f, 0.88f)
    val ORE_CRYSTAL = Rgb(0.52f, 0.82f, 0.86f)

    val PLAYER_SKIN = Rgb(0.87f, 0.62f, 0.47f)
    val PLAYER_TUNIC = Rgb(0.58f, 0.19f, 0.13f)
    val PLAYER_PANTS = Rgb(0.16f, 0.15f, 0.19f)
    val PLAYER_APRON = Rgb(0.21f, 0.13f, 0.09f)
    val PLAYER_HAIR = Rgb(0.23f, 0.15f, 0.08f)
    val PLAYER_BELT = Rgb(0.13f, 0.09f, 0.06f)
    val PLAYER_BOOT = Rgb(0.30f, 0.20f, 0.11f)
    val SACK = Rgb(0.55f, 0.44f, 0.28f)

    /** One tunic colour per crew role index (roster order). */
    val MINER_STYLES = listOf(
        Rgb(0.16f, 0.30f, 0.55f), Rgb(0.18f, 0.42f, 0.38f), Rgb(0.48f, 0.36f, 0.14f),
        Rgb(0.38f, 0.22f, 0.46f), Rgb(0.30f, 0.32f, 0.36f), Rgb(0.55f, 0.40f, 0.20f),
    )
    val MINER_CAP = Rgb(0.14f, 0.11f, 0.09f)

    /** Pickaxe-head tint by quality level 0..8. */
    val PICK_TINTS = listOf(
        Rgb(0.38f, 0.33f, 0.28f), Rgb(0.38f, 0.33f, 0.28f), Rgb(0.38f, 0.33f, 0.28f),
        Rgb(0.72f, 0.34f, 0.16f), Rgb(0.72f, 0.50f, 0.25f), Rgb(0.72f, 0.50f, 0.25f),
        Rgb(0.30f, 0.33f, 0.40f), Rgb(0.66f, 0.69f, 0.74f), Rgb(0.62f, 0.72f, 0.88f),
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

    // The Crystal Hollow (v2.1, kept as the Android-exclusive endgame pocket).
    val CRYSTAL_A = Rgb(0.42f, 0.85f, 0.88f)
    val CRYSTAL_B = Rgb(0.62f, 0.55f, 0.92f)
    val MONOLITH = Rgb(0.48f, 0.80f, 0.85f)
    val MINE_TIMBER = Rgb(0.26f, 0.18f, 0.11f)
    val MINE_DARK = Rgb(0.04f, 0.05f, 0.07f)
    val STANDING_STONE = Rgb(0.30f, 0.34f, 0.30f)

    // The town quarter.
    val PLASTER = Rgb(0.86f, 0.80f, 0.70f)
    val TIMBER = Rgb(0.30f, 0.21f, 0.12f)
    val ROOF_TILE = Rgb(0.48f, 0.24f, 0.16f)
    val ROOF_THATCH = Rgb(0.62f, 0.50f, 0.28f)
    val DOOR_WOOD = Rgb(0.24f, 0.16f, 0.09f)
    val WINDOW_GLOW = Rgb(1.0f, 0.75f, 0.42f)
    val WELL_STONE = Rgb(0.55f, 0.53f, 0.50f)
    val WELL_WATER = Rgb(0.24f, 0.44f, 0.55f)
    val SOIL = Rgb(0.33f, 0.25f, 0.16f)
    val WHEAT = Rgb(0.85f, 0.70f, 0.30f)
    val SAIL_CLOTH = Rgb(0.90f, 0.87f, 0.80f)
    val CHAPEL_STONE = Rgb(0.80f, 0.78f, 0.74f)
    val PATH = Rgb(0.55f, 0.47f, 0.36f)
    val RAIN = Rgb(0.62f, 0.70f, 0.80f)
    val OVERCAST_SKY = Rgb(0.46f, 0.49f, 0.54f)

    // v3.0 — the wood chain + mechanical power.
    val SAWMILL_WOOD = Rgb(0.42f, 0.29f, 0.17f)
    val SAW_BLADE = Rgb(0.70f, 0.72f, 0.75f)
    val TIMBER_PILE = Rgb(0.50f, 0.36f, 0.20f)
    val LEAT_WATER = Rgb(0.26f, 0.48f, 0.58f)
    val WATER_WHEEL = Rgb(0.34f, 0.23f, 0.13f)

    // Carried torches for the smith and the hired hands.
    val TORCH_FLAME = Rgb(1.0f, 0.66f, 0.25f)
    val TORCH_WRAP = Rgb(0.45f, 0.18f, 0.10f)
    val GRASS_TUFT = Rgb(0.20f, 0.38f, 0.11f)
    val PEBBLE = Rgb(0.45f, 0.43f, 0.40f)

    const val ROUGHNESS_TERRAIN = 0.95f
    const val ROUGHNESS_PROP = 0.80f
    const val ROUGHNESS_ORE = 0.55f
    const val ROUGHNESS_METAL = 0.35f
    const val METALLIC_ORE = 0.15f
    const val METALLIC_INGOT = 0.85f
    const val METALLIC_DEFAULT = 0.0f

    val SUN_COLOR = Rgb(1.0f, 0.95f, 0.86f)
    const val SUN_INTENSITY_LUX = 11_000f
    /**
     * v3.1 — the key light now comes from the camera's side (south-east)
     * instead of the north-west. The old back-lit sun left every face the
     * player actually sees — building fronts, the canyon's near wall, the
     * whole south-eastern rim — in ambient-only shade, which read as a dark
     * ring around the map. Front-lit is the classic isometric key.
     */
    val SUN_DIRECTION = floatArrayOf(-0.35f, -0.71f, -0.61f)
    val AMBIENT_SKY = Rgb(0.38f, 0.45f, 0.58f)
    val SKY_COLOR = Rgb(0.52f, 0.68f, 0.84f)

    /** v3.0 — the prototype camera: fixed orthographic, yaw 45°, pitch ≈32°. */
    const val CAMERA_YAW_DEGREES = 45f
    const val CAMERA_PITCH_DEGREES = 32f
}

/**
 * v3.0 — the prototype's ore table, ported exactly. HP is in swings at
 * mining power 1; raw ore has NO sale value (it must be smelted); each ore
 * smelts 1:1 into its ingot. Pickaxe Quality uncovers tiers at Lv 3/6/8.
 *
 * DELIBERATELY KEPT (flagged): CRYSTAL as a sixth, Android-exclusive tier
 * living in the Crystal Hollow — it sits above mythril and changes nothing
 * about the five spec tiers' numbers.
 */
enum class Ore(
    val label: String,
    val rockTint: Theme.Rgb,
    val rockHp: Int,
    val respawnSeconds: Int,
    val smeltSeconds: Float,
    val pickLevel: Int,
    val rockScale: Float,
    val rockGlow: Float,
) {
    IRON("Iron", Theme.ORE_IRON, 4, 9, 3f, 0, 1.0f, 0f),
    COPPER("Copper", Theme.ORE_COPPER, 6, 13, 5f, 0, 1.05f, 0f),
    SILVER("Silver", Theme.ORE_SILVER, 9, 18, 8f, 3, 1.0f, 0.05f),
    GOLD("Gold", Theme.ORE_GOLD, 14, 26, 12f, 6, 1.1f, 0.12f),
    MYTHRIL("Mythril", Theme.ORE_MYTHRIL, 22, 40, 18f, 8, 1.2f, 0.18f),
    CRYSTAL("Crystal", Theme.ORE_CRYSTAL, 28, 46, 22f, 8, 1.45f, 0.35f),
}

/** One ore smelts into one ingot — no alloying, no fuel recipes. */
enum class Metal(
    val label: String,
    val ore: Ore,
    val sell: Int,
    val tint: Theme.Rgb,
) {
    IRON_INGOT("Iron Ingot", Ore.IRON, 2, Theme.ORE_IRON),
    COPPER_INGOT("Copper Ingot", Ore.COPPER, 4, Theme.ORE_COPPER),
    SILVER_INGOT("Silver Ingot", Ore.SILVER, 7, Theme.ORE_SILVER),
    GOLD_INGOT("Gold Ingot", Ore.GOLD, 12, Theme.ORE_GOLD),
    MYTHRIL_INGOT("Mythril Ingot", Ore.MYTHRIL, 28, Theme.ORE_MYTHRIL),
    CRYSTAL_INGOT("Crystal Ingot", Ore.CRYSTAL, 40, Theme.ORE_CRYSTAL);

    val smeltSeconds: Float get() = ore.smeltSeconds
}

/** Each recipe consumes exactly one ingot of its type. */
enum class Item(
    val label: String,
    val metal: Metal,
    val craftSeconds: Float,
    val sell: Int,
) {
    KNIFE("Knife", Metal.IRON_INGOT, 6f, 10),
    COPPER_KETTLE("Copper Kettle", Metal.COPPER_INGOT, 7f, 22),
    SILVER_CUTLERY("Silver Cutlery", Metal.SILVER_INGOT, 9f, 45),
    GOLD_ORNAMENT("Gold Ornament", Metal.GOLD_INGOT, 12f, 90),
    MYTHRIL_BLADE("Mythril Blade", Metal.MYTHRIL_INGOT, 16f, 220),
    CRYSTAL_BLADE("Crystal Blade", Metal.CRYSTAL_INGOT, 20f, 340),
}

/**
 * v3.0 — the prototype's sixteen upgrades, ported exactly:
 * `cost = round(base × growth^level)`, effects additive/linear so the
 * economy stays sub-exponential.
 */
enum class UpgradeType(
    val label: String,
    val category: String,
    val maxLevel: Int,
    val baseCost: Int,
    val growth: Float,
    val effect: String,
) {
    SWIFT_BOOTS("Swift Boots", "Player", 8, 30, 1.25f, "+8% move speed per level"),
    MINERS_RHYTHM("Miner's Rhythm", "Player", 8, 25, 1.25f, "+7% swing speed per level"),
    LEATHER_PACK("Leather Pack", "Player", 6, 40, 1.30f, "Carry capacity 10 + 5 per level"),
    PICKAXE_QUALITY("Pickaxe Quality", "Player", 8, 60, 1.30f, "+4% double-ore per level; Lv 3/6/8 uncover silver/gold/mythril"),
    DEEPER_BINS("Deeper Bins", "Storage", 6, 80, 1.25f, "Ore bin capacity 500 + 250 per level"),
    ORE_HEAP("Ore Heap", "Storage", 6, 120, 1.25f, "Mine stockpile capacity 40 + 20 per level"),
    GREAT_BELLOWS("Great Bellows", "Furnace", 8, 70, 1.25f, "Smelt time ×0.93 per level"),
    WIDE_HOPPER("Wide Hopper", "Furnace", 5, 60, 1.30f, "Hopper 20 + 10 per level; tray 20 + 6 per level"),
    MASTER_TONGS("Master Tongs", "Forge", 8, 70, 1.25f, "Craft time ×0.94 per level"),
    TWIN_STRIKE("Twin Strike", "Forge", 6, 90, 1.30f, "+4% twin-craft (2-for-1) chance per level"),
    LONG_RACK("Long Rack", "Forge", 4, 55, 1.30f, "Forge queue 10 + 4 per level; rack 8 + 4 per level"),
    REPUTATION("Reputation", "Market", 10, 100, 1.28f, "+6% sale prices per level"),
    NIGHT_MARKET("Night Market", "Market", 1, 900, 1f, "The Merchant trades after dark (one-time)"),
    NIGHT_SHIFT("Night Shift", "Market", 6, 400, 1.35f, "Offline window 2h + 1.5h per level"),
    CREW_TRAINING("Crew Training", "Crew", 5, 150, 1.30f, "Crew +8% speed and +1 carry per level"),
}

object Upgrades {
    /** Spec §8: `cost = round(base × growth^level)`. */
    fun cost(type: UpgradeType, level: Int): Int =
        (type.baseCost * type.growth.pow(level)).let { kotlin.math.round(it).toInt() }.coerceAtLeast(1)

    /** Double-ore chance per swing: +4% per Pickaxe Quality level. */
    fun doubleOreChance(pickLevel: Int): Float = 0.04f * pickLevel
}

/** The player's own numbers, straight from the prototype. */
object PlayerConfig {
    const val MOVE_SPEED = 4.6f
    const val CARRY_CAPACITY = 10
    const val MINING_REACH = 2.2f
    /** Auto-swing interval while holding on a rock. */
    const val SWING_SECONDS = 0.55f
    /** Fastest a tap-spammed swing can land. */
    const val TAP_SWING_SECONDS = 0.26f
    const val IMPACT_FRACTION = 0.68f
    const val TURN_RATE = 9.0f
    const val WALK_PHASE_PER_UNIT = 3.5f
}

object Buildings {
    const val INTERACT_REACH = 2.2f
    const val FORGE_REACH = 2.4f
}

/**
 * v3.0 — the prototype's crew, ported exactly. Automation buys freedom, it
 * never replaces the smith: every role is slower than the player, and wages
 * are real. A worker whose wage cannot be paid downs tools until it can.
 */
enum class Role(
    val label: String,
    val hireCost: Int,
    val wagePerMin: Int,
    val speed: Float,
    val carry: Int,
    val swingSeconds: Float,
    val blurb: String,
) {
    MINER("Miner", 50, 4, 2.3f, 4, 2.6f, "General-purpose — works any vein at either cut."),
    CARRIER("Carrier", 80, 3, 2.8f, 8, 0f, "Hauls the mine stockpiles into your yard bins."),
    SMELTER("Smelter", 120, 4, 2.6f, 6, 0f, "Keeps the furnace hopper loaded from the bins."),
    LUMBERJACK("Lumberjack", 140, 4, 2.4f, 5, 2.4f, "Runs the whole wood chain alone — fell, haul, feed the saw."),
    BLACKSMITH("Blacksmith", 200, 6, 2.5f, 4, 2.4f, "Tends the primary anvil while you are elsewhere."),
    MERCHANT("Merchant", 300, 5, 2.7f, 6, 0f, "Carts finished goods to market and sells them."),
    MASTER_SMELTER("Master Smelter", 1_500, 11, 2.6f, 6, 0f, "Feeds Furnace II with precious ore only."),
    MASTER_SMITH("Master Smith", 2_000, 12, 2.5f, 4, 2.4f, "Works a second anvil — every rare ingot, in parallel."),
    PIT_MASTER("Pit Master", 4_000, 14, 2.3f, 4, 2.6f, "Digs the East Cut — and opens it for everyone."),
    SPEC_IRON("Iron Specialist", 150, 5, 1.5f, 4, 3.35f, "One hire, iron only — slow but tireless."),
    SPEC_COPPER("Copper Specialist", 300, 7, 1.5f, 4, 3.55f, "One hire, copper only."),
    SPEC_SILVER("Silver Specialist", 700, 10, 1.4f, 4, 3.9f, "One hire, silver only."),
    SPEC_GOLD("Gold Specialist", 1_500, 14, 1.4f, 4, 4.3f, "One hire, gold only."),
    SPEC_MYTHRIL("Mythril Specialist", 3_500, 20, 1.3f, 4, 4.85f, "One hire, mythril only."),
}

object Crew {
    /** Wages leave the purse once a minute; miss one and the crew downs tools. */
    const val PAYROLL_SECONDS = 60f
    const val IDLE_COOLDOWN_SECONDS = 3.5f

    /** Upgrade gates for the premium hires (spec §9.2). */
    fun masterSmithUnlocked(levels: IntArray): Boolean =
        levels[UpgradeType.CREW_TRAINING.ordinal] >= UpgradeType.CREW_TRAINING.maxLevel
    fun masterSmelterUnlocked(levels: IntArray): Boolean =
        levels[UpgradeType.GREAT_BELLOWS.ordinal] >= UpgradeType.GREAT_BELLOWS.maxLevel &&
            levels[UpgradeType.WIDE_HOPPER.ordinal] >= UpgradeType.WIDE_HOPPER.maxLevel
    fun pitMasterUnlocked(levels: IntArray): Boolean =
        levels[UpgradeType.PICKAXE_QUALITY.ordinal] >= UpgradeType.PICKAXE_QUALITY.maxLevel

    /** Specialist miners appear in the hire list once their ore is uncovered. */
    fun specRoleFor(ore: Ore): Role? = when (ore) {
        Ore.IRON -> Role.SPEC_IRON
        Ore.COPPER -> Role.SPEC_COPPER
        Ore.SILVER -> Role.SPEC_SILVER
        Ore.GOLD -> Role.SPEC_GOLD
        Ore.MYTHRIL -> Role.SPEC_MYTHRIL
        Ore.CRYSTAL -> null
    }

    /** Specialists are strictly one-of-each. */
    fun isSpecialist(role: Role): Boolean = role.name.startsWith("SPEC_")
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
 * Day/night: one full day is 480 seconds; a session opens mid-morning at 30%.
 * v3.1 — the night floor rises from the prototype's 0.42 to 0.55: on-device
 * play on a dim phone screen found moonlit nights still too hard to read.
 * Sky, ambient, and torch timing all brightened to match — a flagged,
 * deliberate deviation from spec §13.3 driven by player feedback.
 */
object DayNight {
    const val CYCLE_SECONDS = 480f
    const val START_TIME = 0.30f
    const val DAWN_END = 0.06f
    const val DAY_END = 0.72f
    const val DUSK_END = 0.80f

    const val DAY_SUN_LUX = 11_000f
    const val NIGHT_SUN_LUX = 1_200f
    const val DAY_AMBIENT_LUX = 4_200f
    const val NIGHT_AMBIENT_LUX = 3_600f

    /** v3.1 — the night floor, raised from spec's 0.42 for phone readability. */
    const val MOONLIGHT = 0.55f
    const val GOLDEN = 0.55f

    val DAY_SKY = Theme.Rgb(0.52f, 0.68f, 0.84f)
    val DUSK_SKY = Theme.Rgb(0.84f, 0.48f, 0.34f)
    val NIGHT_SKY = Theme.Rgb(0.22f, 0.27f, 0.42f)
    val DAY_SUN = Theme.Rgb(1.0f, 0.95f, 0.86f)
    val DUSK_SUN = Theme.Rgb(1.0f, 0.52f, 0.22f)
    val NIGHT_SUN = Theme.Rgb(0.50f, 0.58f, 0.80f)

    /** 0 = full day, 1 = deep night. Drives lantern/fire glow and torches. */
    fun nightness(t: Float): Float = when {
        t < DAWN_END -> 1f - t / DAWN_END * 0.5f          // night easing into dawn
        t < DAY_END -> 0f
        t < DUSK_END -> (t - DAY_END) / (DUSK_END - DAY_END)
        else -> 1f
    }

    fun isNightish(t: Float): Boolean = nightness(t) > 0.5f

    /**
     * The Merchant's hours (spec §13.3): trade stops once the sun drops below
     * a fixed elevation, roughly a fifth of the day. The Night Market upgrade
     * lifts it for the hired Merchant only — never for new walk-in orders,
     * and never for the player, who can always sell.
     */
    fun merchantsOpen(t: Float): Boolean = !isNightish(t)

    /** Carried torches: on as dusk gathers, full by deep night (v3.1: earlier). */
    fun torchLevel(t: Float): Float =
        ((nightness(t) - TORCH_NIGHTNESS_START) / (TORCH_NIGHTNESS_FULL - TORCH_NIGHTNESS_START)).coerceIn(0f, 1f)

    const val TORCH_NIGHTNESS_START = 0.22f
    const val TORCH_NIGHTNESS_FULL = 0.55f
}

/**
 * The wood chain (spec §10.3): trees are 5 HP and respawn in 16s; the sawmill
 * cuts one log into one plank every 16s, ×0.65 once any millrace turns.
 */
object Wood {
    const val TREE_HP = 5
    const val TREE_RESPAWN_SECONDS = 16
    const val SAW_SECONDS_PER_LOG = 16f
    const val MILLRACE_SAW_SCALE = 0.65f
    const val SAWMILL_HOPPER_CAP = 8
}

object WorldLayout {
    /**
     * v3.1 — The Wider Valley. The valley floor nearly doubled (88 wide,
     * 42 deep) and the North Cut was pulled eight units south so the whole
     * mining belt hugs the town instead of hiding in the far corner. The
     * camera and the invisible barriers keep play inside the curated bowl;
     * beyond it the terrain rises into backdrop mountains on every side.
     */
    const val VALLEY_WIDTH = 88f
    const val VALLEY_Z_MAX = 30f
    const val VALLEY_Z_MIN = -12f
    const val CANYON_Z_MIN = -38f
    const val CANYON_Z_MAX = -17f
    const val CANYON_HALF_W = 13f
    const val PASS_HALF_W = 7.5f
    const val PASS_Z_MIN = -17f
    const val PASS_Z_MAX = -11f
    const val NORTH_Z = VALLEY_Z_MIN
    const val TERRAIN_CELL = 1.5f

    /** v3.1 — the visual bowl: ground exists from here, rims rising past it. */
    const val TERRAIN_X_MIN = -58f
    const val TERRAIN_X_MAX = 60f
    const val TERRAIN_Z_MIN = -54f
    const val TERRAIN_Z_MAX = 46f

    /**
     * v3.1 — the invisible barrier. Slopes steeper than this read as walls:
     * the walkable world is the valley floor plus the mine corridors, and
     * every rim mountain politely refuses to be climbed.
     */
    const val WALK_MAX_GROUND = 2.0f

    fun isWalkable(x: Float, z: Float): Boolean = groundHeight(x, z) <= WALK_MAX_GROUND

    /**
     * v3.1 — nearest standable spot to (x, z): a small spiral search used on
     * save restore so a body parked on now-steeper ground snaps down safe.
     */
    fun nearestWalkable(x: Float, z: Float): Pair<Float, Float> {
        if (isWalkable(x, z)) return x to z
        for (r in 1..5) {
            for (i in 0 until 8) {
                val a = i * 0.78539816f + r * 0.3f
                val nx = x + cos(a) * r
                val nz = z + sin(a) * r
                if (isWalkable(nx, nz)) return nx to nz
            }
        }
        return SPAWN_X to SPAWN_Z
    }

    // The Crystal Hollow: a westward side-canyon full of crystal veins.
    const val HOLLOW_X_MIN = -54f
    const val HOLLOW_X_MAX = -33f
    const val HOLLOW_Z_MIN = -45f
    const val HOLLOW_Z_MAX = -29f
    const val LINK_X_MIN = -33f
    const val LINK_X_MAX = -13f
    const val LINK_Z_MIN = -43.5f
    const val LINK_Z_MAX = -34f
    const val MONOLITH_X = -48.5f
    const val MONOLITH_Z = -41.5f

    // v3.0 — the East Cut: the second mine field, opened by the Pit Master.
    const val EAST_X_MIN = 19f
    const val EAST_X_MAX = 29f
    const val EAST_Z_MIN = -14f
    const val EAST_Z_MAX = -1f
    const val EAST_SIGN_X = 24f
    const val EAST_SIGN_Z = 0.5f

    // v3.0 — the sawmill and the woodlot it serves.
    const val SAWMILL_X = -24f
    const val SAWMILL_Z = 8f

    /** Playable ground spans used by tap projection and scatter. */
    const val PLAY_X_MIN = HOLLOW_X_MIN + 1f
    const val PLAY_X_MAX = VALLEY_WIDTH / 2f - 1f
    const val PLAY_Z_MIN = minOf(CANYON_Z_MIN, HOLLOW_Z_MIN) + 1f
    const val PLAY_Z_MAX = VALLEY_Z_MAX - 1f

    const val SPAWN_X = 0f
    const val SPAWN_Z = 10f
    const val TRADE_POST_X = 0f
    const val TRADE_POST_Z = 14f
    const val BIN_X = -3f
    const val BIN_Z = 6f
    const val FURNACE_X = 5.2f
    const val FURNACE_Z = 9.5f
    /** Furnace II stands a little east of the first, same yard. */
    const val FURNACE2_X = 8.6f
    const val FURNACE2_Z = 10.5f
    const val ANVIL_X = 3.1f
    const val ANVIL_Z = 8.2f
    /** The second anvil — Lane B, the Master Smith's. */
    const val ANVIL2_X = 4.6f
    const val ANVIL2_Z = 6.4f
    const val GATE_Z = -11.2f
    /** The south road customers arrive on. */
    const val ROAD_SOUTH_X = 0f
    const val ROAD_SOUTH_Z = 16.5f

    /** Which cut a vein belongs to — each cut keeps its own stockpile. */
    enum class MineField { NORTH, EAST, HOLLOW }

    data class RockSpawn(val ore: Ore, val x: Float, val z: Float, val field: MineField)

    /**
     * v3.0 vein layout — the two mirrored cuts of the prototype, plus the
     * Crystal Hollow pocket. The East Cut's rocks exist from the start but
     * only answer to the Pit Master's crew (and open to everyone once hired).
     * v3.1: the North Cut veins moved eight units south with their canyon.
     */
    val rocks: List<RockSpawn> = listOf(
        // South valley starter veins (North Cut's nearest face)
        RockSpawn(Ore.IRON, -7f, 3f, MineField.NORTH), RockSpawn(Ore.IRON, 7f, 3f, MineField.NORTH),
        RockSpawn(Ore.COPPER, -6f, 10f, MineField.NORTH), RockSpawn(Ore.COPPER, 6f, 10f, MineField.NORTH),
        RockSpawn(Ore.COPPER, 0f, 17f, MineField.NORTH),
        // The North Cut (main canyon): the primary mine, always open
        RockSpawn(Ore.IRON, -6f, -19f, MineField.NORTH), RockSpawn(Ore.IRON, 5f, -21f, MineField.NORTH),
        RockSpawn(Ore.IRON, -9f, -25f, MineField.NORTH), RockSpawn(Ore.IRON, 10f, -23f, MineField.NORTH),
        RockSpawn(Ore.COPPER, -2f, -18.5f, MineField.NORTH), RockSpawn(Ore.COPPER, 8f, -19.5f, MineField.NORTH),
        RockSpawn(Ore.COPPER, -6f, -22f, MineField.NORTH), RockSpawn(Ore.COPPER, 5f, -25f, MineField.NORTH),
        RockSpawn(Ore.SILVER, -2f, -30f, MineField.NORTH), RockSpawn(Ore.SILVER, 9f, -20f, MineField.NORTH),
        RockSpawn(Ore.SILVER, -6f, -30.5f, MineField.NORTH), RockSpawn(Ore.SILVER, 2f, -26f, MineField.NORTH),
        RockSpawn(Ore.GOLD, -9f, -28f, MineField.NORTH), RockSpawn(Ore.GOLD, 0f, -33f, MineField.NORTH),
        RockSpawn(Ore.GOLD, 7f, -29f, MineField.NORTH),
        RockSpawn(Ore.MYTHRIL, -5f, -34f, MineField.NORTH), RockSpawn(Ore.MYTHRIL, 10f, -26f, MineField.NORTH),
        // The East Cut (opens with the Pit Master)
        RockSpawn(Ore.IRON, 21f, -11f, MineField.EAST), RockSpawn(Ore.IRON, 27f, -6f, MineField.EAST),
        RockSpawn(Ore.COPPER, 22.5f, -5.5f, MineField.EAST), RockSpawn(Ore.COPPER, 27.5f, -11.5f, MineField.EAST),
        RockSpawn(Ore.SILVER, 24f, -9f, MineField.EAST), RockSpawn(Ore.SILVER, 28.5f, -3.5f, MineField.EAST),
        RockSpawn(Ore.GOLD, 20.5f, -7.5f, MineField.EAST), RockSpawn(Ore.GOLD, 26f, -12.5f, MineField.EAST),
        RockSpawn(Ore.MYTHRIL, 23.5f, -13f, MineField.EAST),
        // The Crystal Hollow — the Android-exclusive deep pocket
        RockSpawn(Ore.CRYSTAL, -40f, -42.5f, MineField.HOLLOW),
        RockSpawn(Ore.CRYSTAL, -44f, -33f, MineField.HOLLOW),
        RockSpawn(Ore.CRYSTAL, -48f, -38f, MineField.HOLLOW),
        RockSpawn(Ore.CRYSTAL, -50.5f, -31.5f, MineField.HOLLOW),
        RockSpawn(Ore.CRYSTAL, -51.5f, -35.5f, MineField.HOLLOW),
        RockSpawn(Ore.CRYSTAL, -45f, -41f, MineField.HOLLOW),
        RockSpawn(Ore.SILVER, -35f, -31.5f, MineField.HOLLOW),
        RockSpawn(Ore.GOLD, -36.5f, -38f, MineField.HOLLOW),
        RockSpawn(Ore.MYTHRIL, -52f, -43f, MineField.HOLLOW),
    )

    const val TREE_COUNT = 52
    const val TREE_SEED = 1337

    /** Hand-placed pines inside the canyon — scenery, not timber. v3.1: moved south with the canyon. */
    val canyonPines: List<Pair<Float, Float>> = listOf(
        -11f to -19f, 11.5f to -19.5f, -9f to -26f, 10.5f to -32f,
        -11.5f to -34f, 4f to -36f, 11f to -36f, -4f to -18.5f, 8f to -23f,
    )

    /** Glowing crystal clusters scattered around the hollow floor and walls. */
    val crystalClusters: List<Triple<Float, Float, Float>> = listOf(
        Triple(-36.0f, -31.0f, 0.9f), Triple(-38.5f, -43.5f, 1.1f),
        Triple(-42.5f, -36.0f, 0.8f), Triple(-45.5f, -43.8f, 1.3f),
        Triple(-49.0f, -33.0f, 1.0f), Triple(-51.0f, -38.5f, 0.9f),
        Triple(-53.5f, -43.5f, 1.2f), Triple(-53.0f, -30.5f, 0.8f),
        Triple(-41.0f, -29.5f, 0.7f), Triple(-35.5f, -40.5f, 1.0f),
    )

    /** Weathered standing stones ringing the hollow. */
    val standingStones: List<Triple<Float, Float, Float>> = listOf(
        Triple(-37.5f, -34.5f, 1.9f), Triple(-44.5f, -31.0f, 2.3f),
        Triple(-50.0f, -41.0f, 2.1f),
    )

    /**
     * The woodlot: valley trees are real timber now (5 HP, 16s respawn).
     * Positions double as the fellable-tree list.
     */
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
            if (dist(x, z, FURNACE2_X, FURNACE2_Z) < 4f) continue
            if (dist(x, z, ANVIL_X, ANVIL_Z) < 3f) continue
            if (dist(x, z, ANVIL2_X, ANVIL2_Z) < 3f) continue
            if (dist(x, z, SAWMILL_X, SAWMILL_Z) < 5.5f) continue
            if (x > EAST_X_MIN - 2f && z < EAST_Z_MAX + 2f && z > EAST_Z_MIN - 2f) continue
            // Keep the trees off the town quarter: build slots, the plaza
            // well, and the south road the market customers walk in on.
            if (dist(x, z, Town.WELL_X, Town.WELL_Z) < 3.2f) continue
            if (dist(x, z, ROAD_SOUTH_X, ROAD_SOUTH_Z) < 3f) continue
            if (Town.slots.any { dist(x, z, it.x, it.z) < 3.2f }) continue
            if (rocks.any { dist(x, z, it.x, it.z) < 4.5f }) continue
            if (placed.any { dist(x, z, it.first, it.second) < 3.5f }) continue
            placed.add(x to z)
        }
        placed
    }

    fun dist(ax: Float, az: Float, bx: Float, bz: Float): Float =
        Math.hypot((ax - bx).toDouble(), (az - bz).toDouble()).toFloat()

    /** Where each cut keeps its stockpile (carriers service these). */
    fun stockpileOf(field: MineField): Pair<Float, Float> = when (field) {
        MineField.NORTH -> 0f to CANYON_Z_MAX - 1.5f       // at the canyon mouth, on the carrier trail
        MineField.EAST -> EAST_SIGN_X to EAST_Z_MAX + 1.2f // at the cut mouth, on the flat
        MineField.HOLLOW -> (HOLLOW_X_MIN + HOLLOW_X_MAX) / 2f to HOLLOW_Z_MAX - 2f
    }

    /** Outside distance from the canyon+pass+hollow corridor; 0 inside. */
    private fun rectOutside(x: Float, z: Float, x0: Float, x1: Float, z0: Float, z1: Float): Float {
        val dx = max(0f, max(x0 - x, x - x1))
        val dz = max(0f, max(z0 - z, z - z1))
        return hypot(dx, dz)
    }

    fun corridorOutsideDistance(x: Float, z: Float): Float = minOf(
        rectOutside(x, z, -CANYON_HALF_W, CANYON_HALF_W, CANYON_Z_MIN, CANYON_Z_MAX),
        rectOutside(x, z, -PASS_HALF_W, PASS_HALF_W, PASS_Z_MIN, PASS_Z_MAX),
        rectOutside(x, z, HOLLOW_X_MIN, HOLLOW_X_MAX, HOLLOW_Z_MIN, HOLLOW_Z_MAX),
        rectOutside(x, z, LINK_X_MIN, LINK_X_MAX, LINK_Z_MIN, LINK_Z_MAX),
        rectOutside(x, z, EAST_X_MIN, EAST_X_MAX, EAST_Z_MIN, EAST_Z_MAX),
    )

    fun inHollowZone(x: Float, z: Float): Boolean =
        x > HOLLOW_X_MIN - 1.5f && x < HOLLOW_X_MAX + 1.5f && z > HOLLOW_Z_MIN - 1.5f && z < HOLLOW_Z_MAX + 1.5f

    private fun inLinkZone(x: Float, z: Float): Boolean =
        x > LINK_X_MIN - 1.5f && x < LINK_X_MAX + 1.5f && z > LINK_Z_MIN - 1.5f && z < LINK_Z_MAX + 1.5f

    fun inEastZone(x: Float, z: Float): Boolean =
        x > EAST_X_MIN - 1.5f && x < EAST_X_MAX + 1.5f && z > EAST_Z_MIN - 1.5f && z < EAST_Z_MAX + 1.5f

    fun inCanyonZone(x: Float, z: Float): Boolean =
        (z < PASS_Z_MAX + 1f && abs(x) < CANYON_HALF_W + 2.5f) || inHollowZone(x, z) || inLinkZone(x, z) || inEastZone(x, z)

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
        // Rockier floor detail inside the canyon and hollow proper.
        if (d <= 0.5f && (z < CANYON_Z_MAX + 1f && abs(x) < CANYON_HALF_W - 1f || inHollowZone(x, z))) {
            h += noise(x / 4f, z / 4f, 9.1f) * 0.30f
        }
        return h
    }

    // ---- Zone routing ---------------------------------------------------------

    private const val ZONE_VALLEY = 0
    private const val ZONE_CANYON = 1
    private const val ZONE_LINK = 2
    private const val ZONE_HOLLOW = 3
    private const val ZONE_EAST = 4

    /** 0 valley, 1 main canyon, 2 link corridor, 3 crystal hollow, 4 east cut. */
    fun zoneOf(x: Float, z: Float): Int = when {
        inHollowZone(x, z) -> ZONE_HOLLOW
        inLinkZone(x, z) -> ZONE_LINK
        inEastZone(x, z) -> ZONE_EAST
        z < PASS_Z_MAX + 1f && abs(x) < CANYON_HALF_W + 1f -> ZONE_CANYON
        else -> ZONE_VALLEY
    }

    private val LINK_WEST_MOUTH = -31f to -36f
    private val LINK_EAST_MOUTH = -17f to -36f
    private val EAST_MOUTH = 18.5f to -7.5f

    /**
     * Waypoints that keep walkers on the valley-pass-canyon-hollow-east
     * trails instead of trudging over cliff walls. Returns an EMPTY list
     * when the straight line already works (same zone).
     */
    fun routeTo(fromX: Float, fromZ: Float, toX: Float, toZ: Float): List<Pair<Float, Float>> {
        val from = zoneOf(fromX, fromZ)
        val to = zoneOf(toX, toZ)
        if (from == to) return emptyList()
        val legs = ArrayList<Pair<Float, Float>>(4)
        val px = toX.coerceIn(-4.5f, 4.5f)  // bias the pass crossing toward the destination
        fun toValley() { legs.add(px to (PASS_Z_MIN + PASS_Z_MAX) / 2f) }
        fun toCanyon() { legs.add(px to CANYON_Z_MAX - 1.5f) }
        when (from) {
            ZONE_VALLEY -> when (to) {
                ZONE_CANYON -> { toValley(); toCanyon() }
                ZONE_LINK, ZONE_HOLLOW -> {
                    toValley(); toCanyon()
                    legs.add(LINK_EAST_MOUTH); legs.add(LINK_WEST_MOUTH)
                }
                ZONE_EAST -> legs.add(EAST_MOUTH)
            }
            ZONE_CANYON -> when (to) {
                ZONE_VALLEY -> toValley()
                ZONE_LINK, ZONE_HOLLOW -> { legs.add(LINK_EAST_MOUTH); legs.add(LINK_WEST_MOUTH) }
                ZONE_EAST -> { toValley(); legs.add(EAST_MOUTH) }
            }
            ZONE_LINK -> when (to) {
                ZONE_VALLEY -> { legs.add(LINK_EAST_MOUTH); toCanyon(); toValley() }
                ZONE_CANYON -> legs.add(LINK_EAST_MOUTH)
                ZONE_HOLLOW -> legs.add(LINK_WEST_MOUTH)
                ZONE_EAST -> { legs.add(LINK_EAST_MOUTH); toCanyon(); toValley(); legs.add(EAST_MOUTH) }
            }
            ZONE_HOLLOW -> when (to) {
                ZONE_VALLEY -> {
                    legs.add(LINK_WEST_MOUTH); legs.add(LINK_EAST_MOUTH)
                    toCanyon(); toValley()
                }
                ZONE_CANYON -> { legs.add(LINK_WEST_MOUTH); legs.add(LINK_EAST_MOUTH) }
                ZONE_LINK -> legs.add(LINK_WEST_MOUTH)
                ZONE_EAST -> { legs.add(LINK_WEST_MOUTH); legs.add(LINK_EAST_MOUTH); toCanyon(); toValley(); legs.add(EAST_MOUTH) }
            }
            ZONE_EAST -> when (to) {
                ZONE_VALLEY -> legs.add(EAST_MOUTH)
                ZONE_CANYON -> { legs.add(EAST_MOUTH); toValley(); toCanyon() }
                ZONE_LINK, ZONE_HOLLOW -> { legs.add(EAST_MOUTH); toValley(); toCanyon(); legs.add(LINK_EAST_MOUTH); legs.add(LINK_WEST_MOUTH) }
            }
        }
        return legs
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
