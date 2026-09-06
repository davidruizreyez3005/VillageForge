package com.villageforge.state

import com.villageforge.config.Buildings
import com.villageforge.config.Crew
import com.villageforge.config.DayNight
import com.villageforge.config.DebugConfig
import com.villageforge.config.Item
import com.villageforge.config.Metal
import com.villageforge.config.Ore
import com.villageforge.config.PlayerConfig
import com.villageforge.config.Progression
import com.villageforge.config.QuestMetric
import com.villageforge.config.Quests
import com.villageforge.config.Role
import com.villageforge.config.Town
import com.villageforge.config.UpgradeType
import com.villageforge.config.Upgrades
import com.villageforge.config.Wood
import com.villageforge.config.WorldLayout
import com.villageforge.entities.Player
import com.villageforge.entities.Rock
import com.villageforge.entities.Tree
import com.villageforge.entities.Worker
import java.util.Arrays
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * v3.0 — the premium-port game state: the prototype's furnace hopper/tray
 * model, forge lanes, crew roster with wages, mine stockpiles, materials,
 * and the 16-upgrade tree.
 */
class GameState {
    companion object {
        const val TICK_SECONDS = 0.1f

        /** Copies values into target, padding with zeros / ignoring extras. */
        private fun setCountsPadded(target: IntArray, values: List<Int>) {
            for (i in target.indices) {
                if (i < values.size) target[i] = values[i]
            }
        }
    }

    val player = Player()
    val rocks: List<Rock> = WorldLayout.rocks.mapIndexed { i, s -> Rock(i, s.ore, s.x, s.z, s.field) }
    val trees: List<Tree> = WorldLayout.trees.mapIndexed { i, (x, z) -> Tree(i, x, z) }
    val inventory = Inventory()
    /** The yard bins: ore banked at the storage yard. */
    val bins = Bins()
    /** The furnace tray — every ingot the camp has poured. */
    val ingots = Stash(Metal.entries.size)
    /** The goods rack — every finished good waiting for market. */
    val items = Stash(Item.entries.size)
    val workers = mutableListOf<Worker>()

    /** Construction materials (coins buy them; the sawmill cuts planks). */
    val materials = IntArray(Town.Material.entries.size)

    // ---- Furnaces: hopper in, one ore at a time, tray out --------------------

    class FurnaceState {
        val hopper = IntArray(Ore.entries.size)
        var smeltingOre: Ore? = null
        var smeltRemain = 0f
        val hopperTotal: Int get() { var t = 0; for (c in hopper) t += c; return t }
        val smelting: Boolean get() = smeltingOre != null
    }

    val furnace = FurnaceState()
    val furnace2 = FurnaceState()

    // ---- Forge: one queue, two lanes ------------------------------------------

    class ActiveCraft(val item: Item, var remain: Float)

    /** Pending craft jobs, in order. */
    val craftQueue = mutableListOf<Item>()
    var laneA: ActiveCraft? = null
    var laneB: ActiveCraft? = null

    // ---- Sawmill ----------------------------------------------------------------

    var sawmillHopper = 0
    var sawmillRemain = 0f
    var sawmillSawing = false

    // ---- Mine stockpiles ----------------------------------------------------------

    class MinePile {
        private val counts = IntArray(Ore.entries.size)
        val total: Int get() { var t = 0; for (c in counts) t += c; return t }
        fun countAt(ore: Ore): Int = counts[ore.ordinal]
        fun add(ore: Ore, amount: Int): Int {
            counts[ore.ordinal] += amount
            return amount
        }
        fun takeAt(ore: Ore, amount: Int): Int {
            val taken = counts[ore.ordinal].coerceAtMost(amount)
            if (taken > 0) counts[ore.ordinal] -= taken
            return taken
        }
        fun counts(): List<Int> = counts.toList()
        fun setCounts(values: List<Int>) { setCountsPadded(counts, values) }
    }

    val northPile = MinePile()
    val eastPile = MinePile()
    val hollowPile = MinePile()

    // ---- The purse, the upgrades, the town ----------------------------------------

    var coins = if (DebugConfig.ENABLED) DebugConfig.START_GOLD else 0
    /** Level per [UpgradeType]. */
    val upgradeLevels = IntArray(UpgradeType.entries.size)

    var questIndex = 0
    var xp = 0
    var level = 1
    var timeOfDay = DayNight.START_TIME
    var sfxEnabled = true
    var musicEnabled = true
    var lastTickNanos = 0L

    /** The town's trust in the smith: earned on every sale and commission. */
    var renown = 0
    /** Prestige earned by filling commissions — the only prestige that is not a building. */
    var honour = 0
    /** Build stage per village slot (see [Town.slots]). */
    val villageSlots = IntArray(Town.slots.size)
    /** Live market orders, oldest first. */
    val commissions = ArrayList<Commission>()
    /** Customers walking home after their order ended — visual only, not saved. */
    val departingCustomers = ArrayList<Commission>()
    /** Current rain strength 0..1 (drives light + particles + townsfolk). */
    var weatherRain = 0f
    /** Countdown to the next change in the weather schedule. */
    var weatherClock = Town.Weather.FIRST_DRY_SECONDS
    /** Weather phase: true while a shower is running. */
    var weatherWet = false
    /** Townsfolk living in built homes; bodies only live for the session. */
    val residents = ArrayList<Resident>()

    /** Payroll clock — wages leave the purse once a minute. */
    var wageClock = 0f
    /** True while the crew has downed tools over unpaid wages. */
    var wagesUnpaid = false

    val stats = Stats()

    /** Medal ids unlocked so far. */
    val achievements = LinkedHashSet<String>()

    /** Filled by SaveManager on load when offline progress applies; consumed by the HUD. */
    var offlineReport: OfflineReport? = null

    // ---- Derived numbers (the spec's formulas) -------------------------------------

    fun upgradeLevel(type: UpgradeType): Int = upgradeLevels[type.ordinal]

    val wheelbarrowUnlocked: Boolean
        get() = upgradeLevel(UpgradeType.LEATHER_PACK) >= UpgradeType.LEATHER_PACK.maxLevel &&
            upgradeLevel(UpgradeType.SWIFT_BOOTS) >= UpgradeType.SWIFT_BOOTS.maxLevel

    val carryCapacity: Int
        get() {
            val base = PlayerConfig.CARRY_CAPACITY + 5 * upgradeLevel(UpgradeType.LEATHER_PACK)
            val withBarrow = if (wheelbarrowUnlocked) (base * 1.5f).toInt() else base
            return withBarrow
        }

    val moveSpeed: Float
        get() {
            var s = PlayerConfig.MOVE_SPEED * (1f + 0.08f * upgradeLevel(UpgradeType.SWIFT_BOOTS))
            if (wheelbarrowUnlocked) s *= 1.15f
            return s
        }

    /** Auto-swing interval, tightened 7% per Miner's Rhythm level. */
    val swingInterval: Float
        get() = PlayerConfig.SWING_SECONDS / (1f + 0.07f * upgradeLevel(UpgradeType.MINERS_RHYTHM))

    val doubleOreChance: Float get() = Upgrades.doubleOreChance(upgradeLevel(UpgradeType.PICKAXE_QUALITY))

    fun oreUnlocked(ore: Ore): Boolean = upgradeLevel(UpgradeType.PICKAXE_QUALITY) >= ore.pickLevel

    val pickLevel: Int get() = upgradeLevel(UpgradeType.PICKAXE_QUALITY)

    fun binCap(ore: Ore): Int =
        ((500 + 250 * upgradeLevel(UpgradeType.DEEPER_BINS)) * Town.storageMul(villageSlots)).toInt()

    val stockpileCap: Int
        get() = ((40 + 20 * upgradeLevel(UpgradeType.ORE_HEAP)) * Town.storageMul(villageSlots)).toInt()

    val hopperCap: Int get() = 20 + 10 * upgradeLevel(UpgradeType.WIDE_HOPPER)
    val trayCap: Int get() = 20 + 6 * upgradeLevel(UpgradeType.WIDE_HOPPER)

    /** Furnace II appears once both furnace upgrades are maxed. */
    val furnace2Unlocked: Boolean
        get() = upgradeLevel(UpgradeType.GREAT_BELLOWS) >= UpgradeType.GREAT_BELLOWS.maxLevel &&
            upgradeLevel(UpgradeType.WIDE_HOPPER) >= UpgradeType.WIDE_HOPPER.maxLevel

    /** The tier split: with Furnace II up, the base fire keeps the common ores. */
    fun furnaceAccepts(furnace2: Boolean, ore: Ore): Boolean =
        if (furnace2Unlocked) {
            if (furnace2) ore.pickLevel >= 3 else ore.pickLevel < 3
        } else !furnace2

    val smeltScale: Float
        get() {
            var s = 0.93f.pow(upgradeLevel(UpgradeType.GREAT_BELLOWS))
            if (Town.bellowsPowered(villageSlots)) s *= 0.72f
            return s
        }

    val craftScale: Float get() = 0.94f.pow(upgradeLevel(UpgradeType.MASTER_TONGS))
    val twinStrikeChance: Float get() = 0.04f * upgradeLevel(UpgradeType.TWIN_STRIKE)
    val queueCap: Int get() = 10 + 4 * upgradeLevel(UpgradeType.LONG_RACK)
    val rackCap: Int get() = 8 + 4 * upgradeLevel(UpgradeType.LONG_RACK)

    val saleScale: Float get() = 1f + 0.06f * upgradeLevel(UpgradeType.REPUTATION)

    val offlineCapSeconds: Float
        get() = (7_200f + 5_400f * upgradeLevel(UpgradeType.NIGHT_SHIFT)) * Town.offlineMul(villageSlots)

    /** Crew speed: Crew Training +8%/level, fields +2% each. */
    val crewSpeedMul: Float
        get() = (1f + 0.08f * upgradeLevel(UpgradeType.CREW_TRAINING)) * Town.crewSpeedMul(villageSlots)

    fun crewCarry(role: Role): Int = role.carry + upgradeLevel(UpgradeType.CREW_TRAINING)

    /** Crew wages per minute, trimmed by the farmstead's boon. */
    fun wagePerMinute(): Int {
        var total = 0
        for (w in workers) total += w.role.wagePerMin
        return (total * Town.wageMul(villageSlots)).toInt().coerceAtLeast(1)
    }

    val eastCutOpen: Boolean
        get() = workers.any { it.role == Role.PIT_MASTER }

    val merchantUnlockedNight: Boolean get() = upgradeLevel(UpgradeType.NIGHT_MARKET) > 0

    // ---- Commission / resident runtime classes (unchanged shape) -------------------

    data class SmeltBatch(val metal: Metal, var remain: Float) {
        val total: Float get() = metal.smeltSeconds
    }

    class Stats {
        val oresMined = IntArray(Ore.entries.size)
        var coinsEarnedTotal = 0
        val ingotsSmelted = IntArray(Metal.entries.size)
        val itemsCrafted = IntArray(Item.entries.size)
        var playSeconds = 0f
        var rocksBroken = 0
        var offlineGains = 0
        var nightSeconds = 0f
        // The town counters (the Record).
        var commissionsFilled = 0
        var commissionsExpired = 0
        var renownEarned = 0
        var buildStages = 0
        var rainSeconds = 0f
        // v3.0 — the wood chain and the wage bill.
        var timberFelled = 0
        var planksSawn = 0
        var wagesPaid = 0
        var materialsBought = 0
        fun ingotsSmeltedTotal(): Int = ingotsSmelted.sum()
        fun itemsCraftedTotal(): Int = itemsCrafted.sum()
    }

    /** Derived town standing, shown wherever prestige matters. */
    fun prestige(): Int = Town.prestige(villageSlots, honour)

    /** A customer's standing order; the sale itself fills it. */
    class Commission(
        val id: Int,
        val item: Item,
        val needed: Int,
        var filled: Int,
        var remain: Float,
        val bounty: Int,
        val renown: Int,
        val honour: Int,
    ) {
        /** The customer walking to / waiting at / leaving the market. */
        val customer = Player()
        /** Standing at the stall (drives the rig). */
        var customerAtMarket = false
        var customerLeaving = false
    }

    /** One soul living in a built home: keeps hours, wanders, goes in at dusk. */
    class Resident(val homeX: Float, val homeZ: Float) {
        val body = Player()
        /** False while indoors (rig hidden). */
        var out = false
        /** Seconds until the next wander target. */
        var wanderTimer = 0f
        /** Own clock: when this household rises and turns in. */
        var riseTime = 0.10f
        var sleepTime = 0.78f
    }

    data class OfflineReport(
        val awaySeconds: Float,
        val oreGains: List<Int>,
        val ingotGains: List<Int>,
        val itemGains: Int,
        val plankGains: Int,
        val coinGains: Int,
    )

    sealed class Command {
        data class MoveTo(val x: Float, val z: Float) : Command()
        data class Mine(val rockIndex: Int) : Command()
        data class ChopTree(val treeIndex: Int) : Command()
        object Sell : Command()
        object Deposit : Command()
        object DepositWood : Command()
        data class LoadHopper(val ore: Ore, val furnace2: Boolean) : Command()
        data class Craft(val item: Item) : Command()
        data class BuyUpgrade(val type: UpgradeType) : Command()
        data class HireWorker(val role: Role) : Command()
        data class FireWorker(val index: Int) : Command()
        data class BuyMaterial(val material: Town.Material) : Command()
        data class BuildSlot(val slotIndex: Int) : Command()
        object ToggleSound : Command()
        object ToggleMusic : Command()
    }

    private val commands = ArrayDeque<Command>()
    fun enqueue(command: Command) { if (commands.size < 24) commands.addLast(command) }
    fun drainCommand(): Command? = if (commands.isEmpty()) null else commands.removeFirst()

    // ---- UI snapshots ------------------------------------------------------

    data class CarrySnapshot(val oreCounts: List<Int>, val timber: Int, val total: Int, val capacity: Int)
    data class StockpileSnapshot(val oreCounts: List<Int>, val total: Int)
    data class UpgradeView(
        val type: UpgradeType, val level: Int, val maxLevel: Int,
        val cost: Int, val label: String, val category: String, val effect: String,
    )
    data class IngotSnapshot(val counts: List<Int>, val total: Int, val cap: Int)
    data class ItemSnapshot(val counts: List<Int>, val total: Int, val totalValue: Int, val cap: Int)
    data class FurnaceView(
        val unlocked: Boolean,
        val hopper: List<Int>,
        val hopperTotal: Int,
        val hopperCap: Int,
        val smeltingOre: Ore?,
        val smeltRemain: Float,
        val smeltTotal: Float,
    )
    data class LaneView(val item: Item?, val remain: Float, val total: Float)
    data class ForgeSnapshot(
        val furnace: FurnaceView,
        val furnace2: FurnaceView,
        val furnace2Unlocked: Boolean,
        val queue: List<Item>,
        val queueCap: Int,
        val laneA: LaneView,
        val laneB: LaneView,
        val twinChance: Float,
    )
    data class SawmillSnapshot(val hopper: Int, val hopperCap: Int, val sawing: Boolean, val remain: Float, val planks: Int)
    data class CrewMemberView(val index: Int, val role: Role, val paused: Boolean)
    data class HireOptionView(
        val role: Role, val cost: Int, val wage: Int,
        val canHire: Boolean, val lockedReason: String?, val hired: Boolean,
    )
    data class CrewSnapshot(
        val members: List<CrewMemberView>,
        val hireOptions: List<HireOptionView>,
        val wagePerMin: Int,
        val wagesUnpaid: Boolean,
        val crewSpeedMul: Float,
    )
    data class MaterialView(
        val material: Town.Material, val count: Int, val price: Int,
        val unlocked: Boolean, val renownReq: Int,
    )
    data class MaterialsSnapshot(val materials: List<MaterialView>)
    data class QuestSnapshot(
        val index: Int, val title: String, val desc: String,
        val progress: Int, val goal: Int, val reward: Int, val allDone: Boolean,
    )
    data class LevelSnapshot(val level: Int, val xp: Int, val xpNeeded: Int)
    data class OrderSnapshot(
        val id: Int, val item: Item, val needed: Int, val filled: Int,
        val secsLeft: Float, val totalSecs: Float, val bounty: Int,
        val renown: Int, val honour: Int,
    )
    data class OrdersSnapshot(
        val boardOpen: Boolean, val renownNeeded: Int,
        val orders: List<OrderSnapshot>,
    )
    data class SlotSnapshot(
        val index: Int, val id: String, val title: String, val desc: String,
        val stage: Int, val maxStage: Int, val stageLabel: String?,
        val bill: Int, val suppliesLine: String, val missingLine: String,
        val renownReq: Int, val prestigeReq: Int, val gatesMet: Boolean,
        val affordable: Boolean, val complete: Boolean, val boonLabel: String?,
    )
    data class TownSnapshot(
        val renown: Int, val prestige: Int, val wellTier: Int, val wellLabel: String,
        val residents: Int, val slots: List<SlotSnapshot>,
        val boons: List<String>,
        val powerGen: Int, val powerDraw: Int,
    )

    val carryFlow = MutableStateFlow(CarrySnapshot(List(Ore.entries.size) { 0 }, 0, 0, PlayerConfig.CARRY_CAPACITY))
    val stockpileFlow = MutableStateFlow(StockpileSnapshot(List(Ore.entries.size) { 0 }, 0))
    val coinsFlow = MutableStateFlow(0)
    val sfxFlow = MutableStateFlow(true)
    val upgradeFlow = MutableStateFlow(List(UpgradeType.entries.size) {
        val t = UpgradeType.entries[it]
        UpgradeView(t, 0, t.maxLevel, Upgrades.cost(t, 0), t.label, t.category, t.effect)
    })
    val ingotFlow = MutableStateFlow(IngotSnapshot(List(Metal.entries.size) { 0 }, 0, 20))
    val itemFlow = MutableStateFlow(ItemSnapshot(List(Item.entries.size) { 0 }, 0, 0, 8))
    val forgeFlow = MutableStateFlow(
        ForgeSnapshot(
            FurnaceView(false, List(Ore.entries.size) { 0 }, 0, 20, null, 0f, 0f),
            FurnaceView(false, List(Ore.entries.size) { 0 }, 0, 20, null, 0f, 0f),
            false, emptyList(), 10, LaneView(null, 0f, 0f), LaneView(null, 0f, 0f), 0f,
        )
    )
    val sawmillFlow = MutableStateFlow(SawmillSnapshot(0, Wood.SAWMILL_HOPPER_CAP, false, 0f, 0))
    val crewFlow = MutableStateFlow(CrewSnapshot(emptyList(), emptyList(), 0, false, 1f))
    val materialsFlow = MutableStateFlow(
        MaterialsSnapshot(Town.Material.entries.map { MaterialView(it, 0, it.price, it.renownReq == 0, it.renownReq) })
    )
    val questFlow = MutableStateFlow(questSnapshot())
    val levelFlow = MutableStateFlow(LevelSnapshot(1, 0, Progression.xpForLevel(1)))
    val timeFlow = MutableStateFlow(DayNight.START_TIME)
    val musicFlow = MutableStateFlow(true)
    /** Unlocked medal count — bumps whenever a new achievement lands. */
    val achievementFlow = MutableStateFlow(0)
    /** Live market orders. */
    val ordersFlow = MutableStateFlow(OrdersSnapshot(false, Town.RENOWN_FOR_BOARD, emptyList()))
    /** The build-the-village sheet snapshot. */
    val townFlow = MutableStateFlow(townSnapshot())

    private fun questSnapshot(): QuestSnapshot {
        val idx = questIndex
        if (Quests.isComplete(idx)) return QuestSnapshot(idx, "All quests done", "You are the master of this valley.", 1, 1, 0, true)
        val q = Quests.all[idx]
        return QuestSnapshot(idx, q.title, q.desc, questProgress(q.metric), q.goal, q.reward, false)
    }

    fun questProgress(metric: QuestMetric): Int = when (metric) {
        QuestMetric.IRON_MINED -> stats.oresMined[Ore.IRON.ordinal]
        QuestMetric.INGOTS_SMELTED -> stats.ingotsSmeltedTotal()
        QuestMetric.ITEMS_CRAFTED -> stats.itemsCraftedTotal()
        QuestMetric.PICK_LEVEL -> pickLevel
        QuestMetric.CREW_SIZE -> workers.size
        QuestMetric.SILVER_SMELTED -> stats.ingotsSmelted[Metal.SILVER_INGOT.ordinal]
        QuestMetric.GOLD_SMELTED -> stats.ingotsSmelted[Metal.GOLD_INGOT.ordinal]
        QuestMetric.MYTHRIL_SMELTED -> stats.ingotsSmelted[Metal.MYTHRIL_INGOT.ordinal]
        QuestMetric.CRYSTAL_MINED -> stats.oresMined[Ore.CRYSTAL.ordinal]
        QuestMetric.COMMISSIONS_FILLED -> stats.commissionsFilled
        QuestMetric.PRESTIGE -> prestige()
        QuestMetric.PLANKS_SAWN -> stats.planksSawn
        QuestMetric.EAST_CUT_OPEN -> if (eastCutOpen) 1 else 0
        QuestMetric.POWER_BUILT -> Town.powerGenerated(villageSlots)
    }

    private fun townSnapshot(): TownSnapshot {
        val prestige = prestige()
        val tier = Town.wellTierIndex(prestige)
        val slotViews = Town.slots.mapIndexed { i, slot ->
            val stage = villageSlots[i]
            val complete = stage >= slot.maxStage
            val nextStage = if (complete) null else slot.stages[stage]
            val renownMet = renown >= (nextStage?.renownReq ?: 0)
            val prestigeMet = prestige >= (nextStage?.prestigeReq ?: 0)
            val desc = when (slot.kind) {
                Town.SlotKind.HOUSE -> "A household moves in when the walls are up."
                Town.SlotKind.LAMP -> "Lights the square after dark."
                Town.SlotKind.FARM -> "The farmstead's fields feed the village."
                Town.SlotKind.FIELD -> "Golden rows through the season."
                Town.SlotKind.GRANARY -> "Deeper stores for every haul home."
                Town.SlotKind.WINDMILL -> "Its sails keep working while you sleep."
                Town.SlotKind.CHAPEL -> "The valley's pride, and its gratitude."
                Town.SlotKind.MILLRACE -> "Turns water into power — one unit per wheel."
                Town.SlotKind.BELLOWS_HOUSE -> "Draws 1 power: stacks a further ×0.72 onto smelt time."
                Town.SlotKind.TRIP_HAMMER -> "Draws 1 power: works the primary anvil when nobody will."
            }
            val powerGated = (slot.kind == Town.SlotKind.BELLOWS_HOUSE || slot.kind == Town.SlotKind.TRIP_HAMMER) &&
                Town.powerGenerated(villageSlots) - Town.powerDrawn(villageSlots) < 1
            val titleSuffix = if (slot.kind == Town.SlotKind.HOUSE && complete) slot.houseTierLabel()
            else if (complete) "built" else (nextStage?.label ?: "")
            SlotSnapshot(
                index = i, id = slot.id,
                title = "${slot.kind.label} — $titleSuffix",
                desc = if (slot.kind == Town.SlotKind.HOUSE) "${desc} (${slot.houseTierLabel()})" else desc,
                stage = stage, maxStage = slot.maxStage,
                stageLabel = if (complete) null else nextStage!!.label,
                bill = if (complete) 0 else slot.bill(stage),
                suppliesLine = if (complete) "" else nextStage!!.supplies.joinToString(" · ") { (m, n) -> "$n ${m.label}" },
                missingLine = if (complete) "" else nextStage!!.supplies
                    .filter { (m, n) -> materials[m.ordinal] < n }
                    .joinToString(" · ") { (m, n) -> "short ${n - materials[m.ordinal]} ${m.label}" },
                renownReq = nextStage?.renownReq ?: 0,
                prestigeReq = nextStage?.prestigeReq ?: 0,
                gatesMet = renownMet && prestigeMet && !powerGated,
                affordable = !complete && renownMet && prestigeMet && !powerGated && coins >= slot.bill(stage) &&
                    nextStage!!.supplies.all { (m, n) -> materials[m.ordinal] >= n },
                complete = complete,
                boonLabel = slot.boon?.let { if (Town.isComplete(villageSlots, it)) "✓ ${it.label}" else it.label },
            )
        }
        val boons = Town.Boon.entries.filter { Town.isComplete(villageSlots, it) }.map { it.label }
        return TownSnapshot(
            renown, prestige, tier, Town.wellTiers[tier].label, residents.size, slotViews, boons,
            Town.powerGenerated(villageSlots), Town.powerDrawn(villageSlots),
        )
    }

    fun publishUi() {
        val carry = CarrySnapshot(inventory.oreCounts(), inventory.timber, inventory.total, carryCapacity)
        if (carry != carryFlow.value) carryFlow.value = carry
        val stock = StockpileSnapshot(bins.oreCounts(), bins.total)
        if (stock != stockpileFlow.value) stockpileFlow.value = stock
        if (coins != coinsFlow.value) coinsFlow.value = coins
        if (sfxEnabled != sfxFlow.value) sfxFlow.value = sfxEnabled
        if (musicEnabled != musicFlow.value) musicFlow.value = musicEnabled
        if (achievements.size != achievementFlow.value) achievementFlow.value = achievements.size

        val up = UpgradeType.entries.map { t ->
            val lvl = upgradeLevel(t)
            UpgradeView(t, lvl, t.maxLevel, Upgrades.cost(t, lvl), t.label, t.category, t.effect)
        }
        if (up != upgradeFlow.value) upgradeFlow.value = up

        val ing = IngotSnapshot(ingots.counts(), ingots.total, trayCap)
        if (ing != ingotFlow.value) ingotFlow.value = ing
        val itemVal = Item.entries.foldIndexed(0) { i, acc, it -> acc + items.countAt(i) * it.sell }
        val itm = ItemSnapshot(items.counts(), items.total, itemVal, rackCap)
        if (itm != itemFlow.value) itemFlow.value = itm

        fun furnaceView(unlocked: Boolean, f: FurnaceState): FurnaceView = FurnaceView(
            unlocked, f.hopper.toList(), f.hopperTotal, hopperCap,
            f.smeltingOre, f.smeltRemain,
            f.smeltingOre?.smeltSeconds?.times(smeltScale) ?: 0f,
        )
        val forge = ForgeSnapshot(
            furnaceView(true, furnace),
            furnaceView(furnace2Unlocked, furnace2),
            furnace2Unlocked,
            craftQueue.toList(), queueCap,
            LaneView(laneA?.item, laneA?.remain ?: 0f, laneA?.item?.craftSeconds?.times(craftScale) ?: 0f),
            LaneView(laneB?.item, laneB?.remain ?: 0f, laneB?.item?.craftSeconds?.times(craftScale) ?: 0f),
            twinStrikeChance,
        )
        if (forge != forgeFlow.value) forgeFlow.value = forge

        val saw = SawmillSnapshot(sawmillHopper, Wood.SAWMILL_HOPPER_CAP, sawmillSawing, sawmillRemain, materials[Town.Material.PLANKS.ordinal])
        if (saw != sawmillFlow.value) sawmillFlow.value = saw

        val crew = CrewSnapshot(
            members = workers.mapIndexed { i, w -> CrewMemberView(i, w.role, w.paused) },
            hireOptions = Role.entries.map { role -> hireOptionFor(role) },
            wagePerMin = wagePerMinute(),
            wagesUnpaid = wagesUnpaid,
            crewSpeedMul = crewSpeedMul,
        )
        if (crew != crewFlow.value) crewFlow.value = crew

        val mats = MaterialsSnapshot(Town.Material.entries.map { m ->
            MaterialView(m, materials[m.ordinal], m.price, renown >= m.renownReq, m.renownReq)
        })
        if (mats != materialsFlow.value) materialsFlow.value = mats

        val quest = questSnapshot()
        if (quest != questFlow.value) questFlow.value = quest

        val lvl = LevelSnapshot(level, xp, Progression.xpForLevel(level))
        if (lvl != levelFlow.value) levelFlow.value = lvl

        val quantizedTime = (timeOfDay * 128f).toInt() / 128f
        if (quantizedTime != timeFlow.value) timeFlow.value = quantizedTime

        // The town flows.
        val orders = OrdersSnapshot(
            boardOpen = renown >= Town.RENOWN_FOR_BOARD,
            renownNeeded = Town.RENOWN_FOR_BOARD,
            orders = commissions.map {
                OrderSnapshot(it.id, it.item, it.needed, it.filled, it.remain, Town.commissions.first { d -> d.item == it.item }.secs, it.bounty, it.renown, it.honour)
            },
        )
        if (orders != ordersFlow.value) ordersFlow.value = orders
        val town = townSnapshot()
        if (town != townFlow.value) townFlow.value = town
    }

    fun hireOptionFor(role: Role): HireOptionView {
        val lockedReason = when {
            role == Role.MASTER_SMITH && !Crew.masterSmithUnlocked(upgradeLevels) -> "Crew Training maxed"
            role == Role.MASTER_SMELTER && !Crew.masterSmelterUnlocked(upgradeLevels) -> "Bellows + Hopper maxed"
            role == Role.PIT_MASTER && !Crew.pitMasterUnlocked(upgradeLevels) -> "Pickaxe Quality maxed"
            Crew.isSpecialist(role) -> {
                val ore = when (role) {
                    Role.SPEC_IRON -> Ore.IRON
                    Role.SPEC_COPPER -> Ore.COPPER
                    Role.SPEC_SILVER -> Ore.SILVER
                    Role.SPEC_GOLD -> Ore.GOLD
                    Role.SPEC_MYTHRIL -> Ore.MYTHRIL
                    else -> Ore.CRYSTAL
                }
                if (!oreUnlocked(ore)) "Uncover ${ore.label.lowercase()} first" else null
            }
            else -> null
        }
        val hired = workers.any { it.role == role }
        val oneOfEach = Crew.isSpecialist(role) || role == Role.PIT_MASTER ||
            role == Role.MASTER_SMITH || role == Role.MASTER_SMELTER || role == Role.LUMBERJACK || role == Role.MERCHANT
        val full = oneOfEach && hired
        return HireOptionView(
            role, role.hireCost, role.wagePerMin,
            canHire = lockedReason == null && !full && coins >= role.hireCost,
            lockedReason = lockedReason ?: if (full) "One per village" else null,
            hired = hired,
        )
    }

    /** Adds XP; returns how many levels were gained (bonus coins already applied). */
    fun addXp(amount: Int): Int {
        if (level >= Progression.MAX_LEVEL || amount <= 0) return 0
        xp += amount
        var levels = 0
        while (level < Progression.MAX_LEVEL && xp >= Progression.xpForLevel(level)) {
            xp -= Progression.xpForLevel(level)
            level++
            levels++
            val bonus = Progression.levelUpBonusCoins(level)
            coins += bonus
            stats.coinsEarnedTotal += bonus
        }
        if (level >= Progression.MAX_LEVEL) xp = 0
        return levels
    }

    inner class Inventory {
        private val counts = IntArray(Ore.entries.size)
        /** Timber in the backpack — one per felled tree. */
        var timber = 0
        val total: Int get() { var t = 0; for (c in counts) t += c; return t + timber }
        val isFull: Boolean get() = total >= carryCapacity

        fun add(ore: Ore, amount: Int): Int {
            val space = carryCapacity - total
            val added = if (amount < space) amount else space
            if (added > 0) counts[ore.ordinal] += added
            return added
        }

        fun addTimber(amount: Int): Int {
            val space = carryCapacity - total
            val added = if (amount < space) amount else space
            if (added > 0) timber += added
            return added
        }

        fun countAt(ore: Ore): Int = counts[ore.ordinal]
        fun takeAt(ore: Ore, amount: Int): Int {
            val taken = countAt(ore).coerceAtMost(amount)
            if (taken > 0) counts[ore.ordinal] -= taken
            return taken
        }
        fun takeTimber(amount: Int): Int {
            val taken = timber.coerceAtMost(amount)
            if (taken > 0) timber -= taken
            return taken
        }
        fun countsArray(): IntArray = counts.copyOf()
        fun clearAll() { Arrays.fill(counts, 0); timber = 0 }
        fun oreCounts(): List<Int> = counts.toList()
        fun setCounts(values: List<Int>) { setCountsPadded(counts, values) }
    }

    /** The yard bins: per-ore caps from Deeper Bins (× granary boon). */
    inner class Bins {
        private val counts = IntArray(Ore.entries.size)
        val total: Int get() { var t = 0; for (c in counts) t += c; return t }

        fun add(ore: Ore, amount: Int): Int {
            val space = binCap(ore) - counts[ore.ordinal]
            val added = if (amount < space) amount else space.coerceAtLeast(0)
            if (added > 0) counts[ore.ordinal] += added
            return added
        }

        fun addCounts(from: IntArray): Int {
            var added = 0
            for (i in counts.indices) {
                val space = binCap(Ore.entries[i]) - counts[i]
                val n = from[i].coerceAtMost(space.coerceAtLeast(0))
                counts[i] += n
                added += n
            }
            return added
        }

        fun countAt(ore: Ore): Int = counts[ore.ordinal]
        fun takeAt(ore: Ore, amount: Int): Int {
            val taken = countAt(ore).coerceAtMost(amount)
            if (taken > 0) counts[ore.ordinal] -= taken
            return taken
        }
        fun countsArray(): IntArray = counts.copyOf()
        fun clearAll() = Arrays.fill(counts, 0)
        fun oreCounts(): List<Int> = counts.toList()
        fun setCounts(values: List<Int>) { setCountsPadded(counts, values) }
    }

    /** Simple indexed counter block for ingots and crafted items. */
    class Stash(val size: Int) {
        private val counts = IntArray(size)
        val total: Int get() { var t = 0; for (c in counts) t += c; return t }

        fun add(index: Int, amount: Int) { counts[index] += amount }
        fun addCounts(from: IntArray) { for (i in counts.indices) counts[i] += from[i] }
        fun countAt(index: Int): Int = counts[index]
        fun takeAt(index: Int, amount: Int): Int {
            val taken = counts[index].coerceAtMost(amount)
            if (taken > 0) counts[index] -= taken
            return taken
        }
        fun countsArray(): IntArray = counts.copyOf()
        fun clearAll() = Arrays.fill(counts, 0)
        fun counts(): List<Int> = counts.toList()
        fun setCounts(values: List<Int>) { setCountsPadded(counts, values) }
    }
}
