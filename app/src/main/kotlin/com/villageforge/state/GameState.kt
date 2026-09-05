package com.villageforge.state

import com.villageforge.config.Ore
import com.villageforge.config.PlayerConfig
import com.villageforge.config.Upgrades
import com.villageforge.config.WorldLayout
import com.villageforge.entities.Player
import com.villageforge.entities.Rock
import java.util.Arrays
import kotlinx.coroutines.flow.MutableStateFlow

class GameState {
    companion object { const val TICK_SECONDS = 0.1f }

    val player = Player()
    val rocks: List<Rock> = WorldLayout.rocks.mapIndexed { i, s -> Rock(i, s.ore, s.x, s.z) }
    val inventory = Inventory()
    val stockpile = Stockpile()
    var pickTier = 0
    var bootsLevel = 0
    var backpackLevel = 0
    var coins = 0
    var binOwned = false
    var lastTickNanos = 0L
    var sfxEnabled = true

    val carryCapacity: Int get() = Upgrades.BACKPACK_CAPACITIES[backpackLevel]

    sealed class Command {
        data class MoveTo(val x: Float, val z: Float) : Command()
        data class Mine(val rockIndex: Int) : Command()
        object Sell : Command()
        object Deposit : Command()
        object BuyBin : Command()
        object BuyPick : Command()
        object BuyBoots : Command()
        object BuyBackpack : Command()
        object ToggleSound : Command()
    }

    private val commands = ArrayDeque<Command>()
    fun enqueue(command: Command) { if (commands.size < 16) commands.addLast(command) }
    fun drainCommand(): Command? = if (commands.isEmpty()) null else commands.removeFirst()

    data class CarrySnapshot(val oreCounts: List<Int>, val total: Int, val capacity: Int)
    data class StockpileSnapshot(val oreCounts: List<Int>, val total: Int)
    data class UpgradeSnapshot(val pickTier: Int, val bootsLevel: Int, val backpackLevel: Int)

    val carryFlow = MutableStateFlow(CarrySnapshot(List(Ore.entries.size) { 0 }, 0, PlayerConfig.CARRY_CAPACITY))
    val stockpileFlow = MutableStateFlow(StockpileSnapshot(List(Ore.entries.size) { 0 }, 0))
    val coinsFlow = MutableStateFlow(0)
    val binFlow = MutableStateFlow(false)
    val upgradeFlow = MutableStateFlow(UpgradeSnapshot(0, 0, 0))
    val sfxFlow = MutableStateFlow(true)

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
        fun countsArray(): IntArray = counts.copyOf()
        fun clearAll() = Arrays.fill(counts, 0)
        fun oreCounts(): List<Int> = counts.toList()
        fun setCounts(values: List<Int>) {
            if (values.size != counts.size) return
            for (i in counts.indices) counts[i] = values[i]
        }
    }

    /** Ore banked at the bin; selling drains it along with carried ore. */
    inner class Stockpile {
        private val counts = IntArray(Ore.entries.size)
        val total: Int get() { var t = 0; for (c in counts) t += c; return t }

        fun add(ore: Ore, amount: Int) { counts[ore.ordinal] += amount }
        fun addCounts(from: IntArray) { for (i in counts.indices) counts[i] += from[i] }
        fun countAt(ore: Ore): Int = counts[ore.ordinal]
        fun countsArray(): IntArray = counts.copyOf()
        fun clearAll() = Arrays.fill(counts, 0)
        fun oreCounts(): List<Int> = counts.toList()
        fun setCounts(values: List<Int>) {
            if (values.size != counts.size) return
            for (i in counts.indices) counts[i] = values[i]
        }
    }
}
