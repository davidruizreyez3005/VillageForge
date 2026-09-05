package com.villageforge.config

/**
 * v2.2 — The Village Update.
 *
 * The town layer blended over from the original web build: a commissions
 * board at the market (filled BY SELLING, never by a new chore), a renown /
 * prestige standing that grows with every sale, a village of build slots
 * whose completed buildings pay real boons, townsfolk who live in the
 * houses you raise, and weather that changes the mood without ever taxing
 * a single rate.
 */
object Town {

    // ---- Renown ---------------------------------------------------------------

    /** Renown weight per unit sold, by that unit's face value. */
    fun renownWeight(value: Int): Int = when {
        value >= 2_400 -> 6
        value >= 800 -> 5
        value >= 250 -> 4
        value >= 80 -> 3
        value >= 25 -> 2
        else -> 1
    }

    /** The market takes orders once your name is about. */
    const val RENOWN_FOR_BOARD = 15

    /** How many customers may be waiting at once, by renown. */
    fun boardCapacity(renown: Int): Int = when {
        renown >= 140 -> 3
        renown >= 60 -> 2
        else -> 1
    }

    // ---- Commissions ------------------------------------------------------------

    /**
     * A customer's order. Filled by SELLING that good at the market — your
     * own carry or anything banked — so an order is a goal, never an errand.
     * The bounty is a FLAT payment over the goods' face value; renown and
     * honour (prestige) ride along.
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
        CommissionDef(Item.HORSESHOES, 3, 6, 300f, 1.6f, 4, 1),
        CommissionDef(Item.BRONZE_DAGGER, 2, 4, 330f, 1.6f, 6, 1),
        CommissionDef(Item.IRON_SWORD, 2, 3, 360f, 1.7f, 9, 2),
        CommissionDef(Item.IRON_WARHAMMER, 1, 3, 400f, 1.7f, 13, 2),
        CommissionDef(Item.SILVER_RING, 1, 2, 440f, 1.8f, 20, 3),
        CommissionDef(Item.GOLD_CROWN, 1, 2, 460f, 1.8f, 26, 3),
        CommissionDef(Item.CRYSTAL_BLADE, 1, 1, 480f, 2.0f, 40, 4),
    )

    /**
     * Can the player actually make this good at the current pick tier?
     * Nobody commissions a crystal blade out of ore still in the ground.
     */
    fun craftableAt(item: Item, pickTier: Int): Boolean {
        for ((metal, _) in item.metals) {
            for ((ore, _) in metal.recipe) {
                if (ore.requiredPick.ordinal > pickTier) return false
            }
        }
        if (item.crystal > 0 && Ore.CRYSTAL.requiredPick.ordinal > pickTier) return false
        return true
    }

    // ---- Build supplies -----------------------------------------------------------

    /** Buyable supplies a build consumes; priced into the one-press bill. */
    enum class Material(val label: String, val price: Int) {
        NAILS("nails", 8),
        PLANKS("planks", 14),
        STONE("stone", 18),
        GLASS("glass", 22),
        SEED("seed", 12),
        SHOVEL("shovel", 30),
        LANTERN("lantern", 40),
        CLOTH("sailcloth", 55),
    }

    // ---- Village build slots ---------------------------------------------------------

    enum class SlotKind(val label: String) {
        HOUSE("Cottage"),
        LAMP("Street Lamp"),
        FARM("Farmstead"),
        FIELD("Crop Field"),
        GRANARY("Granary"),
        WINDMILL("Windmill"),
        CHAPEL("Chapel"),
    }

    /** What one completed slot pays back — bounded, never compounding. */
    enum class Boon(val label: String) {
        SALES("Farmstead: +10% sale prices"),
        CARRY("Granary: +25% carry capacity"),
        OFFLINE("Windmill: +15% offline pace"),
        RENOWN("Chapel: +20% renown"),
    }

    data class Stage(
        val label: String,
        val coin: Int,
        val supplies: List<Pair<Material, Int>>,
        val prestige: Int,
    )

    data class Slot(
        val id: String,
        val kind: SlotKind,
        val x: Float,
        val z: Float,
        val renownReq: Int,
        val stages: List<Stage>,
        val boon: Boon? = null,
    ) {
        val maxStage: Int get() = stages.size
        fun suppliesCost(stage: Int): Int = stages[stage].supplies.sumOf { (m, n) -> m.price * n }
        fun bill(stage: Int): Int = stages[stage].coin + suppliesCost(stage)
    }

    private fun houseCost(scale: Int): Int = 90 * scale

    val slots: List<Slot> = listOf(
        // Lighting first — the cheapest way to make the square feel like a town.
        Slot("lamp1", SlotKind.LAMP, -3.2f, 2.5f, 10, listOf(Stage("Hang a street lamp", 55, listOf(Material.LANTERN to 1, Material.NAILS to 2), 2))),
        Slot("lamp2", SlotKind.LAMP, 3.2f, 2.5f, 14, listOf(Stage("Hang a street lamp", 55, listOf(Material.LANTERN to 1, Material.NAILS to 2), 2))),
        Slot("lamp3", SlotKind.LAMP, -3.2f, 9.5f, 20, listOf(Stage("Hang a street lamp", 60, listOf(Material.LANTERN to 1, Material.NAILS to 2), 2))),
        Slot("lamp4", SlotKind.LAMP, 3.2f, 9.5f, 28, listOf(Stage("Hang a street lamp", 60, listOf(Material.LANTERN to 1, Material.NAILS to 2), 2))),
        // The cottage row, east of the workshop — each house moves a household in.
        Slot("house1", SlotKind.HOUSE, 10.5f, 6f, 15, listOf(
            Stage("Dig a plot", houseCost(1), listOf(Material.SHOVEL to 1, Material.PLANKS to 2), 5),
            Stage("Raise the cottage", houseCost(3), listOf(Material.PLANKS to 4, Material.NAILS to 6, Material.GLASS to 2), 9),
        )),
        Slot("house2", SlotKind.HOUSE, 15f, 6f, 35, listOf(
            Stage("Dig a plot", houseCost(2), listOf(Material.SHOVEL to 1, Material.PLANKS to 3), 5),
            Stage("Raise the cottage", houseCost(5), listOf(Material.PLANKS to 6, Material.NAILS to 8, Material.GLASS to 3), 9),
        )),
        Slot("house3", SlotKind.HOUSE, 10.5f, 11.5f, 70, listOf(
            Stage("Dig a plot", houseCost(3), listOf(Material.SHOVEL to 1, Material.PLANKS to 4), 5),
            Stage("Raise the cottage", houseCost(7), listOf(Material.PLANKS to 8, Material.NAILS to 10, Material.GLASS to 4), 9),
        )),
        Slot("house4", SlotKind.HOUSE, 15f, 11.5f, 120, listOf(
            Stage("Dig a plot", houseCost(4), listOf(Material.SHOVEL to 1, Material.PLANKS to 5), 5),
            Stage("Raise the longhouse", houseCost(9), listOf(Material.PLANKS to 10, Material.NAILS to 12, Material.GLASS to 5), 10),
        )),
        // The farmstead, south-east: fields first, then the house that works them.
        Slot("field1", SlotKind.FIELD, 20f, 15f, 55, listOf(Stage("Break a crop field", 95, listOf(Material.SEED to 2, Material.SHOVEL to 1), 3))),
        Slot("field2", SlotKind.FIELD, 24.5f, 15f, 75, listOf(Stage("Break a crop field", 115, listOf(Material.SEED to 2, Material.SHOVEL to 1), 3))),
        Slot("farm", SlotKind.FARM, 20f, 19.5f, 60, listOf(
            Stage("Mark the farmstead", 120, listOf(Material.PLANKS to 2, Material.NAILS to 4), 4),
            Stage("Raise the farmhouse", 260, listOf(Material.PLANKS to 5, Material.NAILS to 8, Material.GLASS to 2), 7),
        ), Boon.SALES),
        // Civic buildings, out along the meadows.
        Slot("granary", SlotKind.GRANARY, 16f, 16.5f, 90, listOf(Stage("Raise the granary", 340, listOf(Material.PLANKS to 6, Material.NAILS to 8, Material.STONE to 4), 9)), Boon.CARRY),
        Slot("windmill", SlotKind.WINDMILL, 22.5f, 5.5f, 110, listOf(Stage("Raise the windmill", 480, listOf(Material.CLOTH to 2, Material.PLANKS to 6, Material.STONE to 4), 12)), Boon.OFFLINE),
        Slot("chapel", SlotKind.CHAPEL, -14f, 12f, 150, listOf(Stage("Raise the chapel", 580, listOf(Material.STONE to 10, Material.GLASS to 4, Material.PLANKS to 4), 14)), Boon.RENOWN),
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

    data class WellTier(val prestige: Int, val label: String)

    /** The town well grows with the village — the one thing that is always there. */
    val wellTiers: List<WellTier> = listOf(
        WellTier(0, "Village Well"),
        WellTier(20, "Stone Well"),
        WellTier(50, "Roofed Well"),
        WellTier(85, "Pump Well"),
        WellTier(112, "Millpond Fountain"),
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

    // ---- Boons --------------------------------------------------------------------

    fun isComplete(stages: IntArray, boon: Boon): Boolean {
        for (i in slots.indices) {
            val slot = slots[i]
            if (slot.boon == boon && stages[i] >= slot.maxStage) return true
        }
        return false
    }

    fun saleMul(stages: IntArray): Float = if (isComplete(stages, Boon.SALES)) 1.10f else 1f
    fun carryMul(stages: IntArray): Float = if (isComplete(stages, Boon.CARRY)) 1.25f else 1f
    fun offlineMul(stages: IntArray): Float = if (isComplete(stages, Boon.OFFLINE)) 1.15f else 1f
    fun renownMul(stages: IntArray): Float = if (isComplete(stages, Boon.RENOWN)) 1.20f else 1f

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

    /** Customers arrive from the south road. */
    const val ROAD_EDGE_X = 0f
    const val ROAD_EDGE_Z = 19.5f

    /** How many souls live in the village once the walls are up. */
    fun residentsFor(stages: IntArray): Int {
        var homes = 0
        for (i in slots.indices) {
            if (slots[i].kind == SlotKind.HOUSE && stages[i] >= slots[i].maxStage) homes += 2
        }
        if (isComplete(stages, Boon.SALES)) homes += 1   // the farmstead's household
        if (isComplete(stages, Boon.RENOWN)) homes += 1  // the chapel's caretaker
        return homes
    }
}
