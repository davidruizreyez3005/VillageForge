package com.villageforge.config

/**
 * v3.0 — the town layer, aligned number-for-number with the prototype:
 * commissions, renown, the material shop, the full 172-prestige build
 * ladder, mechanical power, and the seven-tier well.
 */
object Town {

    // ---- Renown ---------------------------------------------------------------

    /**
     * Renown per unit sold, scaled by that unit's face value: 1 for cheap
     * goods up to 6 for the finest — rare goods build the town's reputation
     * far faster than raw ingots.
     */
    fun renownWeight(value: Int): Int = when {
        value >= 300 -> 6
        value >= 140 -> 5
        value >= 60 -> 4
        value >= 25 -> 3
        value >= 10 -> 2
        else -> 1
    }

    /** The market's order board opens once your name is about. */
    const val RENOWN_FOR_BOARD = 12

    /** Simultaneous customer slots scale with renown: 1 at 0, 2nd at 45, 3rd at 110. */
    fun boardCapacity(renown: Int): Int = when {
        renown >= 110 -> 3
        renown >= 45 -> 2
        else -> 1
    }

    /** First order after the board opens waits 40s; then arrivals every 65–125s. */
    const val FIRST_ORDER_GRACE = 40f
    const val ARRIVE_MIN = 65f
    const val ARRIVE_MAX = 125f

    // ---- Commissions ------------------------------------------------------------

    /**
     * A customer's order. Filled by SELLING that good at the market — from
     * any source — so an order is a goal, never an errand. The bounty is a
     * FLAT payment over the goods' face value; renown and honour ride along.
     */
    data class CommissionDef(
        val item: Item,
        val min: Int,
        val max: Int,
        val secs: Float,
        val coinMul: Float,
        val renown: Int,
        val honour: Int,
    )

    val commissions: List<CommissionDef> = listOf(
        CommissionDef(Item.KNIFE, 3, 6, 300f, 1.6f, 4, 1),
        CommissionDef(Item.COPPER_KETTLE, 2, 4, 330f, 1.6f, 6, 1),
        CommissionDef(Item.SILVER_CUTLERY, 2, 3, 360f, 1.7f, 9, 2),
        CommissionDef(Item.GOLD_ORNAMENT, 1, 3, 400f, 1.7f, 13, 2),
        CommissionDef(Item.MYTHRIL_BLADE, 1, 2, 440f, 1.8f, 20, 3),
        // Flagged Android-exclusive: the Crystal Hollow's own commission.
        CommissionDef(Item.CRYSTAL_BLADE, 1, 1, 480f, 2.0f, 30, 4),
    )

    /** Nobody commissions a blade out of ore still in the ground. */
    fun craftableAt(item: Item, pickLevel: Int): Boolean =
        item.metal.ore.pickLevel <= pickLevel

    // ---- Build supplies -----------------------------------------------------------

    /** Buyable supplies a build consumes; priced into the one-press bill. */
    enum class Material(val label: String, val price: Int, val renownReq: Int) {
        NAILS("nails", 8, 0),
        PLANKS("planks", 14, 0),
        SHOVEL("shovel", 30, 0),
        LANTERN("lantern", 40, 12),
        STONE("stone", 18, 18),
        GLASS("glass", 22, 20),
        SEED("seed", 12, 22),
        CLOTH("sailcloth", 55, 46),
    }

    // ---- Village build slots ---------------------------------------------------------

    enum class SlotKind(val label: String) {
        HOUSE("Home Site"),
        LAMP("Lamp Post"),
        FARM("Farmstead"),
        FIELD("Crop Field"),
        GRANARY("Granary"),
        WINDMILL("Windmill"),
        CHAPEL("Chapel"),
        MILLRACE("Millrace"),
        BELLOWS_HOUSE("Bellows House"),
        TRIP_HAMMER("Trip Hammer"),
    }

    /** What one completed building pays back — bounded, never compounding. */
    enum class Boon(val label: String) {
        WAGES("Farmstead: −10% crew wages"),
        CREW_SPEED("Crop Fields: +2% crew speed each"),
        STORAGE("Granary: +25% storage & stockpile caps"),
        OFFLINE("Windmill: +15% offline duration"),
        RENOWN20("Chapel: +20% renown per sale"),
    }

    data class Stage(
        val label: String,
        val coin: Int,
        val supplies: List<Pair<Material, Int>>,
        val renownReq: Int,
        val prestigeReq: Int,
        val prestige: Int,
    )

    data class Slot(
        val id: String,
        val kind: SlotKind,
        val x: Float,
        val z: Float,
        /** Order among slots of the same kind (house tiers, cost growth). */
        val nth: Int,
        val stages: List<Stage>,
        val boon: Boon? = null,
    ) {
        val maxStage: Int get() = stages.size
        fun suppliesCost(stage: Int): Int = stages[stage].supplies.sumOf { (m, n) -> m.price * n }
        fun bill(stage: Int): Int = stages[stage].coin + suppliesCost(stage)
        fun houseTierLabel(): String = when (nth) {
            0 -> "Cottage"
            1 -> "Dormer Cottage"
            2 -> "Longhouse"
            else -> "Merchant's House"
        }
    }

    /**
     * Repeatable kinds multiply their COIN cost by a fixed growth for every
     * already-completed slot of that kind — the 4th cottage costs roughly
     * 10× the 1st. One-off buildings never scale.
     */
    private const val HOUSE_GROWTH = 2.2f
    private const val FIELD_GROWTH = 1.5f

    private fun houseSlot(id: String, nth: Int, x: Float, z: Float, growth: Float): Slot = Slot(
        id, SlotKind.HOUSE, x, z, nth,
        listOf(
            Stage("Dig Plot", (40 * growth).toInt(), listOf(Material.SHOVEL to 2), 0, 0, 2),
            Stage("Raise Cottage", (120 * growth).toInt(), listOf(Material.PLANKS to 6, Material.NAILS to 8), 5, 0, 8),
        ),
    )

    private fun fieldSlot(id: String, nth: Int, x: Float, z: Float, growth: Float): Slot = Slot(
        id, SlotKind.FIELD, x, z, nth,
        listOf(Stage("Sow Field", (320 * growth).toInt(), listOf(Material.SHOVEL to 1, Material.SEED to 3), 34, 34, 5)),
    )

    val slots: List<Slot> = listOf(
        // Town Square: four lamp posts.
        Slot("lamp1", SlotKind.LAMP, -3.2f, 2.5f, 0, listOf(Stage("Light Lamp", 35, listOf(Material.LANTERN to 1, Material.GLASS to 1), 12, 6, 5))),
        Slot("lamp2", SlotKind.LAMP, 3.2f, 2.5f, 1, listOf(Stage("Light Lamp", 35, listOf(Material.LANTERN to 1, Material.GLASS to 1), 12, 6, 5))),
        Slot("lamp3", SlotKind.LAMP, -3.2f, 9.5f, 2, listOf(Stage("Light Lamp", 35, listOf(Material.LANTERN to 1, Material.GLASS to 1), 12, 6, 5))),
        Slot("lamp4", SlotKind.LAMP, 3.2f, 9.5f, 3, listOf(Stage("Light Lamp", 35, listOf(Material.LANTERN to 1, Material.GLASS to 1), 12, 6, 5))),
        // Cottage Row: each house is a visibly bigger build than the last.
        houseSlot("house1", 0, 10.5f, 6f, 1f),
        houseSlot("house2", 1, 15f, 6f, HOUSE_GROWTH),
        houseSlot("house3", 2, 10.5f, 11.5f, HOUSE_GROWTH * HOUSE_GROWTH),
        houseSlot("house4", 3, 15f, 11.5f, HOUSE_GROWTH * HOUSE_GROWTH * HOUSE_GROWTH),
        // Farmland: the farmstead and its fields.
        fieldSlot("field1", 0, 18f, 13f, 1f),
        fieldSlot("field2", 1, 22.5f, 13f, FIELD_GROWTH),
        fieldSlot("field3", 2, 27f, 13f, FIELD_GROWTH * FIELD_GROWTH),
        fieldSlot("field4", 3, 18f, 17f, FIELD_GROWTH * FIELD_GROWTH * FIELD_GROWTH),
        Slot("farm", SlotKind.FARM, 23.5f, 18.5f, 0, listOf(
            Stage("Clear Ground", 500, listOf(Material.SHOVEL to 3), 30, 20, 4),
            Stage("Raise Farmstead", 2200, listOf(Material.PLANKS to 18, Material.NAILS to 22, Material.STONE to 10), 38, 20, 10),
        ), Boon.WAGES),
        // Civic buildings out along the meadows.
        Slot("granary", SlotKind.GRANARY, 16f, 16.5f, 0, listOf(Stage("Raise Granary", 3000, listOf(Material.PLANKS to 24, Material.NAILS to 20, Material.STONE to 14), 42, 45, 10)), Boon.STORAGE),
        Slot("windmill", SlotKind.WINDMILL, 22.5f, 5.5f, 0, listOf(
            Stage("Raise Mill", 4500, listOf(Material.STONE to 26, Material.PLANKS to 20, Material.NAILS to 24), 48, 55, 9),
            Stage("Fit Sails", 2800, listOf(Material.CLOTH to 10, Material.PLANKS to 12), 52, 60, 7),
        ), Boon.OFFLINE),
        Slot("chapel", SlotKind.CHAPEL, -14f, 12f, 0, listOf(
            Stage("Lay Foundation", 5000, listOf(Material.STONE to 30), 55, 70, 7),
            Stage("Raise Chapel", 9000, listOf(Material.STONE to 34, Material.PLANKS to 22, Material.GLASS to 8), 62, 80, 13),
        ), Boon.RENOWN20),
        // The Woodlot: mechanical power — build the race before the machine.
        Slot("millrace1", SlotKind.MILLRACE, -28f, 1f, 0, listOf(
            Stage("Cut the Leat", 900, listOf(Material.SHOVEL to 2, Material.STONE to 8), 45, 40, 3),
            Stage("Hang the Wheel", 2600, listOf(Material.PLANKS to 16, Material.NAILS to 14), 50, 45, 6),
        )),
        Slot("millrace2", SlotKind.MILLRACE, -21f, -3f, 1, listOf(
            Stage("Cut the Leat", 900, listOf(Material.SHOVEL to 2, Material.STONE to 8), 45, 40, 3),
            Stage("Hang the Wheel", 2600, listOf(Material.PLANKS to 16, Material.NAILS to 14), 50, 45, 6),
        )),
        Slot("bellows", SlotKind.BELLOWS_HOUSE, 8.5f, 7.5f, 0, listOf(
            Stage("Fit Bellows", 6000, listOf(Material.PLANKS to 14, Material.NAILS to 18, Material.CLOTH to 6), 58, 65, 7),
        )),
        Slot("trip", SlotKind.TRIP_HAMMER, 1.5f, 11.5f, 0, listOf(
            Stage("Set Trip Hammer", 7500, listOf(Material.STONE to 18, Material.NAILS to 24, Material.PLANKS to 10), 60, 72, 7),
        )),
    )

    fun slotIndex(id: String): Int = slots.indexOfFirst { it.id == id }

    // ---- Prestige + the well ladder ------------------------------------------------

    /** Prestige is DERIVED: every completed build stage plus commission honour. */
    fun prestige(stages: IntArray, honour: Int): Int {
        var p = honour
        for (i in slots.indices) {
            val stage = stages[i].coerceIn(0, slots[i].maxStage)
            for (s in 0 until stage) p += slots[i].stages[s].prestige
        }
        return p
    }

    /** A fully built village is exactly 172 prestige — the final well tier. */
    const val FULL_VILLAGE_PRESTIGE = 172

    data class WellTier(val prestige: Int, val label: String)

    /** The well grows through seven eras, pegged to the build ladder. */
    val wellTiers: List<WellTier> = listOf(
        WellTier(0, "Stone Well"),
        WellTier(12, "Pulley Well"),
        WellTier(30, "Faucet Monument"),
        WellTier(55, "Birdbath Fountain"),
        WellTier(90, "Grand Fountain"),
        WellTier(140, "Great Fountain"),
        WellTier(172, "Millpond Fountain"),
    )

    fun wellTierIndex(prestige: Int): Int {
        var idx = 0
        for (i in wellTiers.indices) {
            if (prestige >= wellTiers[i].prestige) idx = i else break
        }
        return idx
    }

    const val WELL_X = 0f
    const val WELL_Z = 5f

    // ---- Mechanical power (the endgame layer) --------------------------------------

    /** Each finished Millrace generates 1 power; machines draw 1. */
    fun powerGenerated(stages: IntArray): Int {
        var p = 0
        for (i in slots.indices) {
            if (slots[i].kind == SlotKind.MILLRACE && stages[i] >= slots[i].maxStage) p++
        }
        return p
    }

    fun powerDrawn(stages: IntArray): Int {
        var d = 0
        for (i in slots.indices) {
            val k = slots[i].kind
            if ((k == SlotKind.BELLOWS_HOUSE || k == SlotKind.TRIP_HAMMER) && stages[i] >= slots[i].maxStage) d++
        }
        return d
    }

    fun bellowsPowered(stages: IntArray): Boolean {
        val i = slotIndex("bellows")
        return stages[i] >= slots[i].maxStage && powerGenerated(stages) >= powerDrawn(stages)
    }

    fun tripHammerBuilt(stages: IntArray): Boolean {
        val i = slotIndex("trip")
        return stages[i] >= slots[i].maxStage
    }

    /** Any millrace at all also speeds the sawmill, independent of the budget. */
    fun anyMillrace(stages: IntArray): Boolean = powerGenerated(stages) > 0

    // ---- Boons --------------------------------------------------------------------

    fun isComplete(stages: IntArray, boon: Boon): Boolean {
        for (i in slots.indices) {
            val slot = slots[i]
            if (slot.boon == boon && stages[i] >= slot.maxStage) return true
        }
        return false
    }

    fun completedFields(stages: IntArray): Int {
        var n = 0
        for (i in slots.indices) {
            if (slots[i].kind == SlotKind.FIELD && stages[i] >= slots[i].maxStage) n++
        }
        return n
    }

    /** Farmstead trims wages 10%, floored at 50% — no boon stack makes labor free. */
    fun wageMul(stages: IntArray): Float = if (isComplete(stages, Boon.WAGES)) 0.90f else 1f
    /** Each field: +2% crew speed, max +8%. */
    fun crewSpeedMul(stages: IntArray): Float = 1f + 0.02f * completedFields(stages).coerceAtMost(4)
    fun storageMul(stages: IntArray): Float = if (isComplete(stages, Boon.STORAGE)) 1.25f else 1f
    fun offlineMul(stages: IntArray): Float = if (isComplete(stages, Boon.OFFLINE)) 1.15f else 1f
    fun renownMul(stages: IntArray): Float = if (isComplete(stages, Boon.RENOWN20)) 1.20f else 1f

    // ---- Weather --------------------------------------------------------------------

    /** Rain is a change of mood, not a tax: no rate, cap, or price ever moves. */
    object Weather {
        const val FIRST_DRY_SECONDS = 300f
        const val DRY_MIN = 900f
        const val DRY_MAX = 2200f
        const val WET_MIN = 90f
        const val WET_MAX = 210f
        const val FADE_IN = 14f
        const val FADE_OUT = 22f
        /** How far the light greys at full strength. */
        const val OVERCAST = 0.72f
    }

    // ---- Townsfolk ---------------------------------------------------------------------

    /** Max residents out on the streets at once (rig pool size). */
    const val RESIDENT_RIGS = 4
    /** Max customers standing at the market (rig pool size). */
    const val CUSTOMER_RIGS = 3
    /** Villagers amble. */
    const val WALK_SPEED = 1.6f
    /** Wander target refresh window, seconds. */
    const val WANDER_MIN = 6f
    const val WANDER_MAX = 14f

    /** The plaza and lanes the townsfolk keep to. */
    val WANDER_X = -6f..8f
    val WANDER_Z = 1f..13f

    /** Customers arrive from the south road (when no finished homes yet). */
    const val ROAD_EDGE_X = 0f
    const val ROAD_EDGE_Z = 19.5f

    /** Doors customers come from once homes are finished. */
    fun homeDoors(stages: IntArray): List<Pair<Float, Float>> {
        val doors = ArrayList<Pair<Float, Float>>()
        for (i in slots.indices) {
            val slot = slots[i]
            if (slot.kind == SlotKind.HOUSE && stages[i] >= slot.maxStage) doors.add(slot.x to slot.z + 1.6f)
        }
        return doors
    }

    /**
     * Population is derived, never stored: every finished cottage, the
     * farmstead, and the chapel each move one household (two souls) in.
     */
    fun residentsFor(stages: IntArray): Int {
        var homes = 0
        for (i in slots.indices) {
            if (slots[i].kind == SlotKind.HOUSE && stages[i] >= slots[i].maxStage) homes += 2
        }
        if (isComplete(stages, Boon.WAGES)) homes += 2   // the farmstead's household
        if (isComplete(stages, Boon.RENOWN20)) homes += 2 // the chapel's caretaker
        return homes
    }
}
