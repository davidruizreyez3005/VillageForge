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
    var lastTickNanos = 0L

    val stats = Stats()

    /** Filled by SaveManager on load when offline progress applies; consumed by the HUD. */
    var offlineReport: OfflineReport? = null

    val carryCapacity: Int get() = Upgrades.BACKPACK_CAPACITIES[backpackLevel]

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
        fun ingotsSmeltedTotal(): Int = ingotsSmelted.sum()
        fun itemsCraftedTotal(): Int = itemsCrafted.sum()
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
