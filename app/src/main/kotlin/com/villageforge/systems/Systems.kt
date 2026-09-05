package com.villageforge.systems

import com.villageforge.config.Buildings as BuildingData
import com.villageforge.config.DayNight
import com.villageforge.config.Item
import com.villageforge.config.Metal
import com.villageforge.config.Miners
import com.villageforge.config.Ore
import com.villageforge.config.Picks
import com.villageforge.config.PlayerConfig
import com.villageforge.config.Progression
import com.villageforge.config.Quests
import com.villageforge.config.Upgrades
import com.villageforge.config.WorldLayout
import com.villageforge.core.EventBus
import com.villageforge.core.SfxId
import com.villageforge.entities.AnimState
import com.villageforge.entities.Miner
import com.villageforge.entities.Player
import com.villageforge.entities.Rock
import com.villageforge.state.GameState
import kotlin.math.hypot
import kotlin.random.Random

private fun levelsEvent(bus: EventBus, gs: GameState, levels: Int) {
    if (levels > 0) bus.levelUp.tryEmit(EventBus.LevelUp(gs.level))
}

class Mining(private val bus: EventBus) {

    private var targetRock = -1
    private var swingTime = 0f
    private var lastImpactCycle = -1
    private val rng = Random(4)

    fun setTarget(rockIndex: Int) {
        targetRock = rockIndex
        swingTime = 0f
        lastImpactCycle = -1
    }

    fun clearTarget(player: Player) {
        targetRock = -1
        swingTime = 0f
        player.animState = AnimState.IDLE
        player.swingTime = 0f
        player.faceTargetX = Float.NaN
        player.faceTargetZ = Float.NaN
    }

    fun update(gs: GameState, dt: Float) {
        for (rock in gs.rocks) {
            if (!rock.alive) {
                rock.respawnTimer -= dt
                if (rock.respawnTimer <= 0f) rock.reset()
            }
        }

        val index = targetRock
        if (index < 0) return
        val rock = gs.rocks[index]
        val player = gs.player

        if (!rock.alive || gs.inventory.isFull) { clearTarget(player); return }
        if (player.distanceTo(rock.x, rock.z) > PlayerConfig.MINING_REACH) return

        player.animState = AnimState.SWING
        player.faceTargetX = rock.x
        player.faceTargetZ = rock.z
        player.clearTarget()

        swingTime += dt
        player.swingTime = swingTime

        val cycle = (swingTime / PlayerConfig.SWING_SECONDS).toInt()
        val phase = swingTime - cycle * PlayerConfig.SWING_SECONDS
        if (phase >= PlayerConfig.SWING_SECONDS * PlayerConfig.IMPACT_FRACTION && lastImpactCycle != cycle) {
            lastImpactCycle = cycle
            bus.rockStruck.tryEmit(EventBus.RockStruck(index))
            rock.hp -= Picks.entries[gs.pickTier].damage
            if (rock.hp <= 0) breakRock(gs, rock)
        }
    }

    private fun breakRock(gs: GameState, rock: Rock) {
        var amount = 1
        if (rock.ore == Ore.IRON && rng.nextBoolean()) amount++
        if (rng.nextDouble() < Picks.entries[gs.pickTier].doubleOreChance) amount++
        val added = gs.inventory.add(rock.ore, amount)
        if (added > 0) {
            bus.oreMined.tryEmit(EventBus.OreMined(rock.ore, added, rock.x, rock.z))
            gs.stats.oresMined[rock.ore.ordinal] += added
            levelsEvent(bus, gs, gs.addXp(added * Progression.XP_PER_ORE))
        }
        rock.breakRock()
        clearTarget(gs.player)
    }
}

class Economy(private val bus: EventBus) {

    private var sellTarget = false
    fun setTarget() { sellTarget = true }
    fun clearTarget() { sellTarget = false }

    fun update(gs: GameState) {
        if (!sellTarget) return
        val player = gs.player
        if (hypot(WorldLayout.TRADE_POST_X - player.x, WorldLayout.TRADE_POST_Z - player.z) > BuildingData.INTERACT_REACH) return
        sellTarget = false
        sellAll(gs)
    }

    private fun sellAll(gs: GameState) {
        var oreCount = 0
        var total = 0
        for (ore in Ore.entries) {
            val count = gs.inventory.countAt(ore) + gs.stockpile.countAt(ore)
            oreCount += count
            total += count * ore.rawSell
        }
        var ingotCount = 0
        for (metal in Metal.entries) {
            val count = gs.ingots.countAt(metal.ordinal)
            ingotCount += count
            total += count * metal.sell
        }
        var itemCount = 0
        for (item in Item.entries) {
            val count = gs.items.countAt(item.ordinal)
            itemCount += count
            total += count * item.sell
        }
        if (total == 0) {
            bus.notices.tryEmit(EventBus.Notice("Nothing to sell", EventBus.COLOR_WARN, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z))
            return
        }
        gs.inventory.clearAll()
        gs.stockpile.clearAll()
        gs.ingots.clearAll()
        gs.items.clearAll()
        gs.coins += total
        gs.stats.coinsEarnedTotal += total
        gs.stats.oreSold += oreCount
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.COINS))
        bus.notices.tryEmit(EventBus.Notice("+$total c", EventBus.COLOR_GOLD, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z))
        if (ingotCount + itemCount > 0) {
            bus.notices.tryEmit(EventBus.Notice("$ingotCount ingots · $itemCount items", EventBus.COLOR_INFO, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z, -26f))
        }
        levelsEvent(bus, gs, gs.addXp(total / 3 * Progression.XP_PER_3_COINS))
    }
}

class Buildings(private val bus: EventBus) {

    private var depositTarget = false
    fun setDepositTarget() { depositTarget = true }
    fun clearTarget() { depositTarget = false }

    fun tryBuyBin(gs: GameState) {
        if (gs.binOwned) return
        if (gs.coins < BuildingData.BIN_COST) {
            bus.notices.tryEmit(EventBus.Notice("Need ${BuildingData.BIN_COST} c", EventBus.COLOR_WARN, WorldLayout.BIN_X, WorldLayout.BIN_Z))
            return
        }
        gs.coins -= BuildingData.BIN_COST
        gs.binOwned = true
        depositTarget = false
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.BUY))
        bus.notices.tryEmit(EventBus.Notice("Storage bin built!", EventBus.COLOR_INFO, WorldLayout.BIN_X, WorldLayout.BIN_Z))
    }

    fun tryBuyForge(gs: GameState) {
        if (gs.furnaceOwned) return
        if (gs.coins < BuildingData.FURNACE_COST) {
            bus.notices.tryEmit(EventBus.Notice("Need ${BuildingData.FURNACE_COST} c", EventBus.COLOR_WARN, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
            return
        }
        gs.coins -= BuildingData.FURNACE_COST
        gs.furnaceOwned = true
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.BUY))
        bus.notices.tryEmit(EventBus.Notice("The forge is lit!", EventBus.COLOR_GOLD, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
        levelsEvent(bus, gs, gs.addXp(60))
    }

    fun update(gs: GameState) {
        if (!depositTarget) return
        if (!gs.binOwned) { depositTarget = false; return }
        val player = gs.player
        if (hypot(WorldLayout.BIN_X - player.x, WorldLayout.BIN_Z - player.z) > BuildingData.INTERACT_REACH) return
        depositTarget = false
        deposit(gs)
    }

    private fun deposit(gs: GameState) {
        val carried = gs.inventory.total
        if (carried == 0) {
            bus.notices.tryEmit(EventBus.Notice("Nothing to deposit", EventBus.COLOR_WARN, WorldLayout.BIN_X, WorldLayout.BIN_Z))
            return
        }
        gs.stockpile.addCounts(gs.inventory.countsArray())
        gs.inventory.clearAll()
        bus.notices.tryEmit(EventBus.Notice("$carried ore stored", EventBus.COLOR_INFO, WorldLayout.BIN_X, WorldLayout.BIN_Z))
    }
}

class UpgradeManager(private val bus: EventBus) {

    fun tryBuyPick(gs: GameState) {
        val next = gs.pickTier + 1
        if (next >= Picks.entries.size) return
        val cost = Picks.entries[next].cost
        if (gs.coins < cost) {
            bus.notices.tryEmit(EventBus.Notice("Need $cost c", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
            return
        }
        gs.coins -= cost
        gs.pickTier = next
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.BUY))
        bus.notices.tryEmit(EventBus.Notice("${Picks.entries[next].label}!", EventBus.COLOR_GOLD, gs.player.x, gs.player.z))
    }

    fun tryBuyBoots(gs: GameState) {
        if (gs.bootsLevel >= Upgrades.BOOTS_COSTS.size) return
        val cost = Upgrades.BOOTS_COSTS[gs.bootsLevel]
        if (gs.coins < cost) {
            bus.notices.tryEmit(EventBus.Notice("Need $cost c", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
            return
        }
        gs.coins -= cost
        gs.bootsLevel++
        gs.player.moveSpeedBonus = gs.bootsLevel * Upgrades.BOOTS_SPEED_PER_LEVEL
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.BUY))
        bus.notices.tryEmit(EventBus.Notice("Boots Lv ${gs.bootsLevel}", EventBus.COLOR_INFO, gs.player.x, gs.player.z))
    }

    fun tryBuyBackpack(gs: GameState) {
        if (gs.backpackLevel >= Upgrades.BACKPACK_COSTS.size) return
        val cost = Upgrades.BACKPACK_COSTS[gs.backpackLevel]
        if (gs.coins < cost) {
            bus.notices.tryEmit(EventBus.Notice("Need $cost c", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
            return
        }
        gs.coins -= cost
        gs.backpackLevel++
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.BUY))
        bus.notices.tryEmit(EventBus.Notice("Carries ${Upgrades.BACKPACK_CAPACITIES[gs.backpackLevel]}", EventBus.COLOR_INFO, gs.player.x, gs.player.z))
    }

    fun syncBonuses(gs: GameState) {
        gs.player.moveSpeedBonus = gs.bootsLevel * Upgrades.BOOTS_SPEED_PER_LEVEL
    }
}

/** The furnace: load ore batches (walk over), smelt over real time, even while away. */
class Forge(private val bus: EventBus) {

    private var loadTarget: Metal? = null

    fun clearLoadTarget() { loadTarget = null }

    /** Returns true when the walk-to-furnace trip should start. */
    fun requestLoad(gs: GameState, metal: Metal): Boolean {
        if (!gs.furnaceOwned) {
            bus.notices.tryEmit(EventBus.Notice("Build the forge first", EventBus.COLOR_WARN, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
            return false
        }
        if (gs.smeltQueue.size >= BuildingData.FURNACE_QUEUE) {
            bus.notices.tryEmit(EventBus.Notice("Furnace queue is full", EventBus.COLOR_WARN, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
            return false
        }
        for ((ore, need) in metal.recipe) {
            if (availableOre(gs, ore) < need) {
                bus.notices.tryEmit(EventBus.Notice("Need $need ${ore.name.lowercase()}", EventBus.COLOR_WARN, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
                return false
            }
        }
        loadTarget = metal
        return true
    }

    fun update(gs: GameState, dt: Float) {
        val pending = loadTarget
        if (pending != null) {
            val player = gs.player
            val d = hypot(WorldLayout.FURNACE_X - player.x, WorldLayout.FURNACE_Z - player.z)
            if (d <= BuildingData.FORGE_REACH) {
                loadTarget = null
                loadBatch(gs, pending)
            }
        }
        if (gs.smeltQueue.isEmpty()) return
        val batch = gs.smeltQueue[0]
        batch.remain -= dt
        if (batch.remain <= 0f) {
            gs.smeltQueue.removeAt(0)
            gs.ingots.add(batch.metal.ordinal, 1)
            gs.stats.ingotsSmelted[batch.metal.ordinal]++
            bus.smeltDone.tryEmit(EventBus.SmeltDone(batch.metal))
            bus.notices.tryEmit(EventBus.Notice("+1 ${batch.metal.label}", EventBus.COLOR_INFO, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z, -26f))
            levelsEvent(bus, gs, gs.addXp(Progression.XP_PER_INGOT))
        }
    }

    private fun loadBatch(gs: GameState, metal: Metal) {
        var ok = true
        for ((ore, need) in metal.recipe) {
            var remaining = need
            remaining -= gs.stockpile.takeAt(ore, remaining)
            remaining -= gs.inventory.takeAt(ore, remaining)
            if (remaining > 0) ok = false
        }
        if (ok) {
            gs.smeltQueue.add(GameState.SmeltBatch(metal, metal.smeltSeconds))
            bus.sfx.tryEmit(EventBus.Sfx(SfxId.SMELT, 0.9f))
            bus.notices.tryEmit(EventBus.Notice("Furnace: ${metal.label}", EventBus.COLOR_INFO, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
        } else {
            bus.notices.tryEmit(EventBus.Notice("Missing ore", EventBus.COLOR_WARN, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
        }
    }

    companion object {
        fun availableOre(gs: GameState, ore: Ore): Int =
            gs.stockpile.countAt(ore) + gs.inventory.countAt(ore)

        /** Advances the smelt queue by [seconds] of offline time; returns ingots gained. */
        fun applyOffline(gs: GameState, seconds: Float): IntArray {
            val gained = IntArray(Metal.entries.size)
            var remaining = seconds
            while (gs.smeltQueue.isNotEmpty() && remaining > 0f) {
                val batch = gs.smeltQueue[0]
                if (batch.remain > remaining) {
                    batch.remain -= remaining
                    remaining = 0f
                } else {
                    remaining -= batch.remain
                    gs.smeltQueue.removeAt(0)
                    gained[batch.metal.ordinal]++
                }
            }
            return gained
        }
    }
}

/** The anvil: crafting takes real hammering time at the forge. */
class Craft(private val bus: EventBus) {

    private var target: Item? = null
    private var hammerTime = 0f
    private var lastImpactCycle = -1

    fun clear(gs: GameState) {
        target = null
        hammerTime = 0f
        lastImpactCycle = -1
        gs.player.animState = AnimState.IDLE
        gs.player.swingTime = 0f
        gs.player.faceTargetX = Float.NaN
        gs.player.faceTargetZ = Float.NaN
    }

    /** Returns true when the walk-to-anvil trip should start. */
    fun request(gs: GameState, item: Item): Boolean {
        if (!gs.furnaceOwned) {
            bus.notices.tryEmit(EventBus.Notice("Build the forge first", EventBus.COLOR_WARN, WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z))
            return false
        }
        val missing = firstMissing(gs, item)
        if (missing != null) {
            bus.notices.tryEmit(EventBus.Notice("Need $missing", EventBus.COLOR_WARN, WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z))
            return false
        }
        target = item
        hammerTime = 0f
        lastImpactCycle = -1
        return true
    }

    fun update(gs: GameState, dt: Float) {
        val item = target ?: return
        val player = gs.player
        if (hypot(WorldLayout.ANVIL_X - player.x, WorldLayout.ANVIL_Z - player.z) > BuildingData.FORGE_REACH) return

        if (firstMissing(gs, item) != null) {
            bus.notices.tryEmit(EventBus.Notice("Materials were used up", EventBus.COLOR_WARN, WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z))
            clear(gs)
            return
        }

        player.animState = AnimState.SWING
        player.faceTargetX = WorldLayout.ANVIL_X
        player.faceTargetZ = WorldLayout.ANVIL_Z
        player.clearTarget()

        hammerTime += dt
        player.swingTime = hammerTime

        val cycle = (hammerTime / PlayerConfig.SWING_SECONDS).toInt()
        val phase = hammerTime - cycle * PlayerConfig.SWING_SECONDS
        if (phase >= PlayerConfig.SWING_SECONDS * PlayerConfig.IMPACT_FRACTION && lastImpactCycle != cycle) {
            lastImpactCycle = cycle
            bus.hammerStruck.tryEmit(EventBus.HammerStruck(WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z))
            bus.sfx.tryEmit(EventBus.Sfx(SfxId.HAMMER, 0.95f + 0.08f * (cycle % 3)))
        }

        if (hammerTime >= item.craftSeconds) {
            consume(gs, item)
            gs.items.add(item.ordinal, 1)
            gs.stats.itemsCrafted[item.ordinal]++
            bus.itemCrafted.tryEmit(EventBus.ItemCrafted(item))
            bus.sfx.tryEmit(EventBus.Sfx(SfxId.CRAFT))
            bus.notices.tryEmit(EventBus.Notice("Crafted ${item.label}!", EventBus.COLOR_GOLD, WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z, -26f))
            levelsEvent(bus, gs, gs.addXp(Progression.XP_PER_ITEM))
            clear(gs)
        }
    }

    private fun firstMissing(gs: GameState, item: Item): String? {
        for ((metal, need) in item.metals) {
            if (gs.ingots.countAt(metal.ordinal) < need) return "$need ${metal.label}"
        }
        if (item.crystal > 0) {
            val have = Forge.availableOre(gs, Ore.CRYSTAL)
            if (have < item.crystal) return "${item.crystal} crystal ore"
        }
        return null
    }

    private fun consume(gs: GameState, item: Item) {
        for ((metal, need) in item.metals) gs.ingots.takeAt(metal.ordinal, need)
        if (item.crystal > 0) {
            var remaining = item.crystal
            remaining -= gs.stockpile.takeAt(Ore.CRYSTAL, remaining)
            gs.inventory.takeAt(Ore.CRYSTAL, remaining)
        }
    }
}

/** Hired miners: walk to a rock, mine it, haul the ore to the stockpile. */
class MinerSystem(private val bus: EventBus) {

    private enum class State { IDLE, WALK_TO_ROCK, MINING, RETURNING }

    private class MinerAI {
        var state = State.IDLE
        var rockIndex = -1
        var swingTime = 0f
        var lastImpactCycle = -1
        var cooldown = 0f
        var carrying: Ore? = null
        var carryingCount = 0
    }

    private val ai = ArrayList<MinerAI>()
    private val rng = Random(97)

    fun hire(gs: GameState): Boolean {
        if (gs.miners.size >= Miners.HIRE_COSTS.size) {
            bus.notices.tryEmit(EventBus.Notice("Crew is full", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
            return false
        }
        val cost = Miners.HIRE_COSTS[gs.miners.size]
        if (gs.coins < cost) {
            bus.notices.tryEmit(EventBus.Notice("Need $cost c", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
            return false
        }
        gs.coins -= cost
        val index = gs.miners.size
        val miner = Miner(index, index % com.villageforge.config.Theme.MINER_STYLES.size)
        miner.body.x = -2f + index * 1.4f
        miner.body.z = 4f
        miner.body.prevX = miner.body.x
        miner.body.prevZ = miner.body.z
        gs.miners.add(miner)
        ai.add(MinerAI())
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.BUY))
        bus.notices.tryEmit(EventBus.Notice("Miner hired!", EventBus.COLOR_INFO, miner.body.x, miner.body.z))
        return true
    }

    fun update(gs: GameState, dt: Float) {
        while (ai.size < gs.miners.size) ai.add(MinerAI())
        for (i in gs.miners.indices) stepMiner(gs, gs.miners[i], ai[i], dt)
    }

    private fun stepMiner(gs: GameState, miner: Miner, a: MinerAI, dt: Float) {
        val body = miner.body
        when (a.state) {
            State.IDLE -> {
                a.cooldown -= dt
                if (a.cooldown > 0f) {
                    if (!body.isMoving && rng.nextFloat() < 0.004f) {
                        body.setTarget(body.x + (rng.nextFloat() - 0.5f) * 6f, body.z + (rng.nextFloat() - 0.5f) * 6f)
                    }
                } else {
                    val candidates = gs.rocks.filter { it.alive && it.ore.requiredPick.ordinal <= gs.pickTier }
                    if (candidates.isEmpty()) {
                        a.cooldown = 1.5f
                    } else {
                        val rock = candidates[rng.nextInt(candidates.size)]
                        a.rockIndex = rock.index
                        val d = hypot(rock.x - body.x, rock.z - body.z).coerceAtLeast(0.01f)
                        val t = ((d - 1.5f) / d).coerceAtLeast(0f)
                        body.setTarget(body.x + (rock.x - body.x) * t, body.z + (rock.z - body.z) * t)
                        a.state = State.WALK_TO_ROCK
                    }
                }
            }
            State.WALK_TO_ROCK -> {
                val rock = gs.rocks[a.rockIndex]
                when {
                    !rock.alive -> { a.state = State.IDLE; a.cooldown = 0.4f }
                    !body.isMoving -> {
                        if (body.distanceTo(rock.x, rock.z) > 2.0f) {
                            a.state = State.IDLE
                            a.cooldown = 0.8f
                        } else {
                            a.state = State.MINING
                            a.swingTime = 0f
                            a.lastImpactCycle = -1
                        }
                    }
                }
            }
            State.MINING -> {
                val rock = gs.rocks[a.rockIndex]
                if (!rock.alive) {
                    a.state = State.IDLE
                    a.cooldown = 0.3f
                } else {
                    body.animState = AnimState.SWING
                    body.faceTargetX = rock.x
                    body.faceTargetZ = rock.z
                    body.clearTarget()

                    a.swingTime += dt
                    body.swingTime = a.swingTime
                    val cycle = (a.swingTime / Miners.SWING_SECONDS).toInt()
                    val phase = a.swingTime - cycle * Miners.SWING_SECONDS
                    if (phase >= Miners.SWING_SECONDS * PlayerConfig.IMPACT_FRACTION && a.lastImpactCycle != cycle) {
                        a.lastImpactCycle = cycle
                        bus.minerStruck.tryEmit(EventBus.MinerStruck(a.rockIndex))
                        rock.hp -= Miners.damage(gs.pickTier)
                        if (rock.hp <= 0) {
                            rock.breakRock()
                            a.carrying = rock.ore
                            a.carryingCount = if (rng.nextDouble() < 0.15f) 2 else 1
                            a.state = State.RETURNING
                            body.setTarget(
                                WorldLayout.BIN_X + (rng.nextFloat() - 0.5f) * 2.5f,
                                WorldLayout.BIN_Z + (rng.nextFloat() - 0.5f) * 2.5f,
                            )
                            body.animState = AnimState.WALK
                            body.swingTime = 0f
                        }
                    }
                }
            }
            State.RETURNING -> {
                if (!body.isMoving) {
                    val ore = a.carrying
                    if (ore != null) gs.stockpile.add(ore, a.carryingCount)
                    a.carrying = null
                    a.carryingCount = 0
                    a.state = State.IDLE
                    a.cooldown = Miners.IDLE_COOLDOWN_SECONDS * (0.7f + rng.nextFloat() * 0.6f)
                }
            }
        }
        // Always advance the walker: turns, moves, and interpolates animation state.
        body.update(dt)
    }
}

/** Watches the active quest's metric and pays out when the goal is met. */
class QuestSystem(private val bus: EventBus) {

    fun update(gs: GameState) {
        if (Quests.isComplete(gs.questIndex)) return
        val quest = Quests.all[gs.questIndex]
        if (gs.questProgress(quest.metric) >= quest.goal) {
            gs.coins += quest.reward
            gs.stats.coinsEarnedTotal += quest.reward
            gs.questIndex++
            bus.sfx.tryEmit(EventBus.Sfx(SfxId.QUEST))
            bus.notices.tryEmit(EventBus.Notice("Quest done: +${quest.reward} c", EventBus.COLOR_GOLD, gs.player.x, gs.player.z, -40f))
            levelsEvent(bus, gs, gs.addXp(quest.reward / 4))
        }
    }
}

/** Applies elapsed offline time to the smelt queue and hired miners. */
object OfflineLogic {

    fun apply(gs: GameState, elapsedRaw: Float): GameState.OfflineReport? {
        val elapsed = elapsedRaw.coerceAtMost(Miners.OFFLINE_CAP_SECONDS)
        if (elapsed < 90f) return null

        val ingotGains = Forge.applyOffline(gs, elapsed)
        if (ingotGains.any { it > 0 }) {
            gs.ingots.addCounts(ingotGains)
            for (i in ingotGains.indices) gs.stats.ingotsSmelted[i] += ingotGains[i]
        }

        val oreGains = IntArray(Ore.entries.size)
        if (gs.miners.isNotEmpty()) {
            val cycles = (elapsed / Miners.OFFLINE_SECONDS_PER_ORE).toInt()
            val weights = IntArray(Ore.entries.size) { i ->
                if (Ore.entries[i].requiredPick.ordinal <= gs.pickTier) Miners.OFFLINE_WEIGHTS[i] else 0
            }
            val totalWeight = weights.sum()
            val rng = Random(elapsed.toLong() * 31L + gs.miners.size)
            if (totalWeight > 0 && cycles > 0) {
                repeat(gs.miners.size * cycles) {
                    var pick = rng.nextInt(totalWeight)
                    var oreIdx = weights.size - 1
                    for (i in weights.indices) {
                        pick -= weights[i]
                        if (pick < 0) { oreIdx = i; break }
                    }
                    oreGains[oreIdx]++
                }
            }
        }
        if (oreGains.any { it > 0 }) gs.stockpile.addCounts(oreGains)

        val gained = oreGains.any { it > 0 } || ingotGains.any { it > 0 }
        return if (gained) GameState.OfflineReport(elapsed, oreGains.toList(), ingotGains.toList()) else null
    }
}

/** Advances time of day; the renderer reads it every frame. */
object DayNightSystem {
    fun update(gs: GameState, dt: Float) {
        gs.timeOfDay = (gs.timeOfDay + dt / DayNight.CYCLE_SECONDS) % 1f
    }
}
