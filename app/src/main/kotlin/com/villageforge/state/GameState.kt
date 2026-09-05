package com.villageforge.state

import com.villageforge.config.Buildings
import com.villageforge.config.DayNight
import com.villageforge.config.Item
import com.villageforge.config.Metal
import com.villageforge.config.Ore
import com.villageforge.config.Picks
import com.villageforge.config.PlayerConfig
import com.villageforge.config.Progression
import com.villageforge.config.QuestMetric
import com.villageforge.config.Quests
import com.villageforge.config.Town
import com.villageforge.config.Upgrades
import com.villageforge.config.WorldLayout
import com.villageforge.entities.Miner
import com.villageforge.entities.Player
import com.villageforge.entities.Rock
import java.util.Arrays
import kotlinx.coroutines.flow.MutableStateFlow

class GameState {
    companion object {
        const val TICK_SECONDS = 0.1f

        /** Copies values into target, padding with zeros / ignoring extras, so v1 saves load into the bigger v2 arrays. */
        private fun setCountsPadded(target: IntArray, values: List<Int>) {
            for (i in target.indices) {
                if (i < values.size) target[i] = values[i]
            }
        }
    }

    val player = Player()
    val rocks: List<Rock> = WorldLayout.rocks.mapIndexed { i, s -> Rock(i, s.ore, s.x, s.z) }
    val inventory = Inventory()
    val stockpile = Stockpile()
    val ingots = Stash(Metal.entries.size)
    val items = Stash(Item.entries.size)
    val miners = mutableListOf<Miner>()

    /** Pending furnace batches; the head of the list is the one melting now. */
    val smeltQueue = mutableListOf<SmeltBatch>()

    var pickTier = 0
    var bootsLevel = 0
    var backpackLevel = 0
    var coins = 0
    var binOwned = false
    var furnaceOwned = false
    var questIndex = 0
    var xp = 0
    var level = 1
    var timeOfDay = DayNight.START_TIME
    var sfxEnabled = true
    var musicEnabled = true
    var lastTickNanos = 0L

    // ---- v2.2 town layer ----

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

    val stats = Stats()

    /** Medal ids unlocked so far (v2.1). */
    val achievements = LinkedHashSet<String>()

    /** Filled by SaveManager on load when offline progress applies; consumed by the HUD. */
    var offlineReport: OfflineReport? = null

    val carryCapacity: Int get() {
        val base = Upgrades.BACKPACK_CAPACITIES[backpackLevel]
        return (base * Town.carryMul(villageSlots)).toInt().coerceAtLeast(base)
    }

    data class SmeltBatch(val metal: Metal, var remain: Float) {
        val total: Float get() = metal.smeltSeconds
    }

    class Stats {
        val oresMined = IntArray(Ore.entries.size)
        var oreSold = 0
        var coinsEarnedTotal = 0
        val ingotsSmelted = IntArray(Metal.entries.size)
        val itemsCrafted = IntArray(Item.entries.size)
        var playSeconds = 0f
        var rocksBroken = 0
        var offlineGains = 0
        var nightSeconds = 0f
        // v2.2 — the town counters (the Record).
        var commissionsFilled = 0
        var commissionsExpired = 0
        var renownEarned = 0
        var buildStages = 0
        var rainSeconds = 0f
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
        val customer = com.villageforge.entities.Player()
        /** Standing at the stall (drives the rig). */
        var customerAtMarket = false
        var customerLeaving = false
    }

    /** One soul living in a built home: keeps hours, wanders, goes in at dusk. */
    class Resident(val homeX: Float, val homeZ: Float) {
        val body = com.villageforge.entities.Player()
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
    )

    sealed class Command {
        data class MoveTo(val x: Float, val z: Float) : Command()
        data class Mine(val rockIndex: Int) : Command()
        object Sell : Command()
        object Deposit : Command()
        object BuyBin : Command()
        object BuyForge : Command()
        object BuyPick : Command()
        object BuyBoots : Command()
        object BuyBackpack : Command()
        object HireMiner : Command()
        data class LoadFurnace(val metal: Metal) : Command()
        data class Craft(val item: Item) : Command()
        object ToggleSound : Command()
        object ToggleMusic : Command()
        data class BuildSlot(val slotIndex: Int) : Command()
    }

    private val commands = ArrayDeque<Command>()
    fun enqueue(command: Command) { if (commands.size < 24) commands.addLast(command) }
    fun drainCommand(): Command? = if (commands.isEmpty()) null else commands.removeFirst()

    // ---- UI snapshots ------------------------------------------------------

    data class CarrySnapshot(val oreCounts: List<Int>, val total: Int, val capacity: Int)
    data class StockpileSnapshot(val oreCounts: List<Int>, val total: Int)
    data class UpgradeSnapshot(val pickTier: Int, val bootsLevel: Int, val backpackLevel: Int)
    data class IngotSnapshot(val counts: List<Int>, val total: Int)
    data class ItemSnapshot(val counts: List<Int>, val total: Int, val totalValue: Int)
    data class BatchSnapshot(val metal: Metal, val remain: Float, val total: Float)
    data class ForgeSnapshot(
        val furnaceOwned: Boolean,
        val queue: List<BatchSnapshot>,
        val queueCapacity: Int,
        val smelting: Boolean,
    )
    data class QuestSnapshot(
        val index: Int, val title: String, val desc: String,
        val progress: Int, val goal: Int, val reward: Int, val allDone: Boolean,
    )
    data class LevelSnapshot(val level: Int, val xp: Int, val xpNeeded: Int)
    data class MinerSnapshot(val count: Int, val max: Int, val nextCost: Int)
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
        val bill: Int, val suppliesLine: String,
        val renownReq: Int, val renownMet: Boolean, val affordable: Boolean,
        val complete: Boolean, val boonLabel: String?,
    )
    data class TownSnapshot(
        val renown: Int, val prestige: Int, val wellTier: Int, val wellLabel: String,
        val residents: Int, val slots: List<SlotSnapshot>,
        val boons: List<String>,
    )

    val carryFlow = MutableStateFlow(CarrySnapshot(List(Ore.entries.size) { 0 }, 0, PlayerConfig.CARRY_CAPACITY))
    val stockpileFlow = MutableStateFlow(StockpileSnapshot(List(Ore.entries.size) { 0 }, 0))
    val coinsFlow = MutableStateFlow(0)
    val binFlow = MutableStateFlow(false)
    val upgradeFlow = MutableStateFlow(UpgradeSnapshot(0, 0, 0))
    val sfxFlow = MutableStateFlow(true)
    val ingotFlow = MutableStateFlow(IngotSnapshot(List(Metal.entries.size) { 0 }, 0))
    val itemFlow = MutableStateFlow(ItemSnapshot(List(Item.entries.size) { 0 }, 0, 0))
    val forgeFlow = MutableStateFlow(ForgeSnapshot(false, emptyList(), Buildings.FURNACE_QUEUE, false))
    val questFlow = MutableStateFlow(questSnapshot())
    val levelFlow = MutableStateFlow(LevelSnapshot(1, 0, Progression.xpForLevel(1)))
    val minerFlow = MutableStateFlow(MinerSnapshot(0, com.villageforge.config.Miners.HIRE_COSTS.size, -1))
    val timeFlow = MutableStateFlow(DayNight.START_TIME)
    val musicFlow = MutableStateFlow(true)
    /** Unlocked medal count — bumps whenever a new achievement lands. */
    val achievementFlow = MutableStateFlow(0)
    /** Live market orders (v2.2). */
    val ordersFlow = MutableStateFlow(OrdersSnapshot(false, Town.RENOWN_FOR_BOARD, emptyList()))
    /** The build-the-village sheet snapshot (v2.2). */
    val townFlow = MutableStateFlow(townSnapshot())

    private fun questSnapshot(): QuestSnapshot {
        val idx = questIndex
        if (Quests.isComplete(idx)) return QuestSnapshot(idx, "All quests done", "You are the master of this valley.", 1, 1, 0, true)
        val q = Quests.all[idx]
        return QuestSnapshot(idx, q.title, q.desc, questProgress(q.metric), q.goal, q.reward, false)
    }

    fun questProgress(metric: QuestMetric): Int = when (metric) {
        QuestMetric.COPPER_MINED -> stats.oresMined[Ore.COPPER.ordinal]
        QuestMetric.ORE_SOLD -> stats.oreSold
        QuestMetric.PICK_TIER -> pickTier
        QuestMetric.FORGE_BUILT -> if (furnaceOwned) 1 else 0
        QuestMetric.INGOTS_SMELTED -> stats.ingotsSmeltedTotal()
        QuestMetric.ITEMS_CRAFTED -> stats.itemsCraftedTotal()
        QuestMetric.MINERS_HIRED -> miners.size
        QuestMetric.GOLD_SMELTED -> stats.ingotsSmelted[Metal.GOLD_INGOT.ordinal]
        QuestMetric.STEEL_PICK -> if (pickTier >= Picks.STEEL.ordinal) 1 else 0
        QuestMetric.CRYSTAL_BLADE -> stats.itemsCrafted[Item.CRYSTAL_BLADE.ordinal]
        QuestMetric.LEVEL -> level
        QuestMetric.CRYSTAL_PICK -> if (pickTier >= Picks.CRYSTAL.ordinal) 1 else 0
        QuestMetric.CRYSTAL_MINED -> stats.oresMined[Ore.CRYSTAL.ordinal]
        QuestMetric.COMMISSIONS_FILLED -> stats.commissionsFilled
        QuestMetric.PRESTIGE -> prestige()
    }

    private fun townSnapshot(): TownSnapshot {
        val prestige = prestige()
        val tier = Town.wellTierIndex(prestige)
        val slotViews = Town.slots.mapIndexed { i, slot ->
            val stage = villageSlots[i]
            val complete = stage >= slot.maxStage
            val nextStage = if (complete) null else slot.stages[stage]
            val renownMet = renown >= slot.renownReq
            SlotSnapshot(
                index = i, id = slot.id,
                title = "${slot.kind.label} — " + if (complete) "built" else nextStage!!.label,
                desc = when (slot.kind) {
                    Town.SlotKind.HOUSE -> "A household moves in when the walls are up."
                    Town.SlotKind.LAMP -> "Lights the square after dark."
                    Town.SlotKind.FARM -> "The farmstead's fields feed the village."
                    Town.SlotKind.FIELD -> "Golden rows through the season."
                    Town.SlotKind.GRANARY -> "Deeper stores for every haul home."
                    Town.SlotKind.WINDMILL -> "Its sails keep working while you sleep."
                    Town.SlotKind.CHAPEL -> "The valley's pride, and its gratitude."
                },
                stage = stage, maxStage = slot.maxStage,
                stageLabel = if (complete) null else nextStage!!.label,
                bill = if (complete) 0 else slot.bill(stage),
                suppliesLine = if (complete) "" else nextStage!!.supplies.joinToString(" · ") { (m, n) -> "$n ${m.label}" },
                renownReq = slot.renownReq, renownMet = renownMet,
                affordable = !complete && renownMet && coins >= slot.bill(stage),
                complete = complete,
                boonLabel = slot.boon?.let { if (Town.isComplete(villageSlots, it)) "✓ ${it.label}" else it.label },
            )
        }
        val boons = Town.Boon.entries.filter { Town.isComplete(villageSlots, it) }.map { it.label }
        return TownSnapshot(renown, prestige, tier, Town.wellTiers[tier].label, residents.size, slotViews, boons)
    }

    fun publishUi() {
        val carry = CarrySnapshot(inventory.oreCounts(), inventory.total, carryCapacity)
        if (carry != carryFlow.value) carryFlow.value = carry
        val stock = StockpileSnapshot(stockpile.oreCounts(), stockpile.total)
        if (stock != stockpileFlow.value) stockpileFlow.value = stock
        if (coins != coinsFlow.value) coinsFlow.value = coins
        if (binOwned != binFlow.value) binFlow.value = binOwned
        val up = UpgradeSnapshot(pickTier, bootsLevel, backpackLevel)
        if (up != upgradeFlow.value) upgradeFlow.value = up
        if (sfxEnabled != sfxFlow.value) sfxFlow.value = sfxEnabled
        if (musicEnabled != musicFlow.value) musicFlow.value = musicEnabled
        if (achievements.size != achievementFlow.value) achievementFlow.value = achievements.size

        val ing = IngotSnapshot(ingots.counts(), ingots.total)
        if (ing != ingotFlow.value) ingotFlow.value = ing
        val itemVal = Item.entries.foldIndexed(0) { i, acc, it -> acc + items.countAt(i) * it.sell }
        val itm = ItemSnapshot(items.counts(), items.total, itemVal)
        if (itm != itemFlow.value) itemFlow.value = itm

        val batches = smeltQueue.map { BatchSnapshot(it.metal, it.remain, it.total) }
        val forge = ForgeSnapshot(furnaceOwned, batches, Buildings.FURNACE_QUEUE, smeltQueue.isNotEmpty())
        if (forge != forgeFlow.value) forgeFlow.value = forge

        val quest = questSnapshot()
        if (quest != questFlow.value) questFlow.value = quest

        val lvl = LevelSnapshot(level, xp, Progression.xpForLevel(level))
        if (lvl != levelFlow.value) levelFlow.value = lvl

        val nextCost = com.villageforge.config.Miners.HIRE_COSTS.getOrNull(miners.size) ?: -1
        val mn = MinerSnapshot(miners.size, com.villageforge.config.Miners.HIRE_COSTS.size, nextCost)
        if (mn != minerFlow.value) minerFlow.value = mn

        val quantizedTime = (timeOfDay * 128f).toInt() / 128f
        if (quantizedTime != timeFlow.value) timeFlow.value = quantizedTime

        // v2.2 — the town flows.
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
        val total: Int get() { var t = 0; for (c in counts) t += c; return t }
        val isFull: Boolean get() = total >= carryCapacity

        fun add(ore: Ore, amount: Int): Int {
            val space = carryCapacity - total
            val added = if (amount < space) amount else space
            if (added > 0) counts[ore.ordinal] += added
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

    /** Ore banked at the bin; selling drains it along with carried ore. */
    inner class Stockpile {
        private val counts = IntArray(Ore.entries.size)
        val total: Int get() { var t = 0; for (c in counts) t += c; return t }

        fun add(ore: Ore, amount: Int) { counts[ore.ordinal] += amount }
        fun addCounts(from: IntArray) { for (i in counts.indices) counts[i] += from[i] }
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
