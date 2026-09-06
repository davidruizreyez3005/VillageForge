package com.villageforge.systems

import com.villageforge.config.Buildings
import com.villageforge.config.Crew
import com.villageforge.config.DayNight
import com.villageforge.config.Item
import com.villageforge.config.Metal
import com.villageforge.config.Ore
import com.villageforge.config.PlayerConfig
import com.villageforge.config.Progression
import com.villageforge.config.Quests
import com.villageforge.config.Role
import com.villageforge.config.Town
import com.villageforge.config.UpgradeType
import com.villageforge.config.Upgrades
import com.villageforge.config.Wood
import com.villageforge.config.WorldLayout
import com.villageforge.core.EventBus
import com.villageforge.core.SfxId
import com.villageforge.entities.AnimState
import com.villageforge.entities.Player
import com.villageforge.entities.Rock
import com.villageforge.entities.Worker
import com.villageforge.state.GameState
import kotlin.math.hypot
import kotlin.random.Random

private fun levelsEvent(bus: EventBus, gs: GameState, levels: Int) {
    if (levels > 0) bus.levelUp.tryEmit(EventBus.LevelUp(gs.level))
}

/**
 * The player's own hands: mining (spec §6.1 — 1 damage per swing, ore HP in
 * swings, double-ore chance from Pickaxe Quality) and felling timber.
 */
class Mining(private val bus: EventBus) {

    private var targetRock = -1
    private var targetTree = -1
    private var swingTime = 0f
    private var lastImpactCycle = -1
    private val rng = Random(4)

    fun setTarget(rockIndex: Int) {
        targetRock = rockIndex
        targetTree = -1
        swingTime = 0f
        lastImpactCycle = -1
    }

    fun setTreeTarget(treeIndex: Int) {
        targetTree = treeIndex
        targetRock = -1
        swingTime = 0f
        lastImpactCycle = -1
    }

    fun clearTarget(player: Player) {
        targetRock = -1
        targetTree = -1
        swingTime = 0f
        lastImpactCycle = -1
        player.animState = AnimState.IDLE
        player.swingTime = 0f
        player.faceTargetX = Float.NaN
        player.faceTargetZ = Float.NaN
    }

    fun update(gs: GameState, dt: Float) {
        // Veins and timber regrow.
        for (rock in gs.rocks) {
            if (!rock.alive) {
                rock.respawnTimer -= dt
                if (rock.respawnTimer <= 0f) rock.reset()
            }
        }
        for (tree in gs.trees) {
            if (!tree.alive) {
                tree.respawnTimer -= dt
                if (tree.respawnTimer <= 0f) tree.reset()
            }
        }

        val rockIdx = targetRock
        val treeIdx = targetTree
        if (rockIdx >= 0) stepRock(gs, rockIdx, dt)
        else if (treeIdx >= 0) stepTree(gs, treeIdx, dt)
    }

    private fun stepRock(gs: GameState, index: Int, dt: Float) {
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

        val cycle = (swingTime / gs.swingInterval).toInt()
        val phase = swingTime - cycle * gs.swingInterval
        if (phase >= gs.swingInterval * PlayerConfig.IMPACT_FRACTION && lastImpactCycle != cycle) {
            lastImpactCycle = cycle
            bus.rockStruck.tryEmit(EventBus.RockStruck(index))
            rock.hp -= 1
            if (rock.hp <= 0) breakRock(gs, rock)
        }
    }

    private fun breakRock(gs: GameState, rock: Rock) {
        var amount = 1
        if (rng.nextDouble() < gs.doubleOreChance) amount++
        val added = gs.inventory.add(rock.ore, amount)
        if (added > 0) {
            bus.oreMined.tryEmit(EventBus.OreMined(rock.ore, added, rock.x, rock.z))
            gs.stats.oresMined[rock.ore.ordinal] += added
            levelsEvent(bus, gs, gs.addXp(added * Progression.XP_PER_ORE))
        }
        gs.stats.rocksBroken++
        rock.breakRock()
        clearTarget(gs.player)
    }

    private fun stepTree(gs: GameState, index: Int, dt: Float) {
        val tree = gs.trees[index]
        val player = gs.player
        if (!tree.alive || gs.inventory.isFull) { clearTarget(player); return }
        if (player.distanceTo(tree.x, tree.z) > PlayerConfig.MINING_REACH) return

        player.animState = AnimState.SWING
        player.faceTargetX = tree.x
        player.faceTargetZ = tree.z
        player.clearTarget()

        swingTime += dt
        player.swingTime = swingTime

        val cycle = (swingTime / gs.swingInterval).toInt()
        val phase = swingTime - cycle * gs.swingInterval
        if (phase >= gs.swingInterval * PlayerConfig.IMPACT_FRACTION && lastImpactCycle != cycle) {
            lastImpactCycle = cycle
            bus.treeStruck.tryEmit(EventBus.TreeStruck(index))
            bus.sfx.tryEmit(EventBus.Sfx(SfxId.WOOD_HIT, 0.85f))
            tree.hp -= 1
            if (tree.hp <= 0) {
                val added = gs.inventory.addTimber(1)
                if (added > 0) {
                    gs.stats.timberFelled++
                    bus.notices.tryEmit(EventBus.Notice("+1 timber", EventBus.COLOR_INFO, tree.x, tree.z))
                }
                tree.fell()
                clearTarget(player)
            }
        }
    }
}

/** One sale, shared by the player and the Merchant. */
class Economy(private val bus: EventBus) {

    class SaleResult(val coins: Int, val goodsUnits: Int, val ingotUnits: Int)

    private var sellTarget = false
    fun setTarget() { sellTarget = true }
    fun clearTarget() { sellTarget = false }

    fun update(gs: GameState) {
        if (!sellTarget) return
        val player = gs.player
        if (hypot(WorldLayout.TRADE_POST_X - player.x, WorldLayout.TRADE_POST_Z - player.z) > Buildings.INTERACT_REACH) return
        sellTarget = false
        val result = sellGoods(gs, Int.MAX_VALUE, Int.MAX_VALUE)
        if (result == null) {
            bus.notices.tryEmit(EventBus.Notice("Nothing to sell — the market buys ingots and goods, never raw ore", EventBus.COLOR_WARN, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z))
        }
    }

    /**
     * The market buys ingots and finished goods only. Craft/sell priority
     * always favors the highest-value good first.
     */
    fun sellGoods(gs: GameState, ingotLimit: Int, itemLimit: Int): SaleResult? {
        var total = 0
        var ingotUnits = 0
        var itemUnits = 0
        var saleRenown = 0
        val scale = gs.saleScale

        // Finished goods first (highest value first).
        val itemOrder = Item.entries.sortedByDescending { it.sell }
        for (item in itemOrder) {
            val have = gs.items.countAt(item.ordinal)
            if (have <= 0 || itemUnits >= itemLimit) continue
            val take = have.coerceAtMost(itemLimit - itemUnits)
            gs.items.takeAt(item.ordinal, take)
            total += (take * item.sell * scale).toInt()
            saleRenown += take * Town.renownWeight(item.sell)
            itemUnits += take
            CommissionSystem.onSold(bus, gs, item, take)
        }
        // Then leftover ingots.
        val metalOrder = Metal.entries.sortedByDescending { it.sell }
        for (metal in metalOrder) {
            val have = gs.ingots.countAt(metal.ordinal)
            if (have <= 0 || ingotUnits >= ingotLimit) continue
            val take = have.coerceAtMost(ingotLimit - ingotUnits)
            gs.ingots.takeAt(metal.ordinal, take)
            total += (take * metal.sell * scale).toInt()
            saleRenown += take * Town.renownWeight(metal.sell)
            ingotUnits += take
        }
        if (total == 0 && itemUnits == 0 && ingotUnits == 0) return null

        gs.coins += total
        gs.stats.coinsEarnedTotal += total
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.COINS))
        bus.notices.tryEmit(EventBus.Notice("+$total c", EventBus.COLOR_GOLD, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z))

        // Renown rides on every unit sold, scaled by what the goods are worth.
        var renownGain = saleRenown
        renownGain = (renownGain * Town.renownMul(gs.villageSlots)).toInt().coerceAtLeast(if (total > 0) 1 else 0)
        if (renownGain > 0) {
            gs.renown += renownGain
            gs.stats.renownEarned += renownGain
            bus.notices.tryEmit(EventBus.Notice("+$renownGain renown", EventBus.COLOR_INFO, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z, -52f))
        }
        if (ingotUnits + itemUnits > 0) {
            bus.notices.tryEmit(EventBus.Notice("$ingotUnits ingots · $itemUnits goods", EventBus.COLOR_INFO, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z, -26f))
        }
        levelsEvent(bus, gs, gs.addXp(total / 3 * Progression.XP_PER_3_COINS))
        return SaleResult(total, itemUnits, ingotUnits)
    }


    /** The Merchant carts goods to market and sells them himself. */
    fun merchantSale(gs: GameState, carry: Int) {
        var itemUnits = 0
        val scale = gs.saleScale
        val itemOrder = Item.entries.sortedByDescending { it.sell }
        var total = 0
        var renownGain = 0
        for (item in itemOrder) {
            val have = gs.items.countAt(item.ordinal)
            if (have <= 0 || itemUnits >= carry) continue
            val take = have.coerceAtMost(carry - itemUnits)
            gs.items.takeAt(item.ordinal, take)
            total += (take * item.sell * scale).toInt()
            renownGain += take * Town.renownWeight(item.sell)
            itemUnits += take
            CommissionSystem.onSold(bus, gs, item, take)
        }
        if (total == 0) return
        gs.coins += total
        gs.stats.coinsEarnedTotal += total
        renownGain = (renownGain * Town.renownMul(gs.villageSlots)).toInt()
        gs.renown += renownGain
        gs.stats.renownEarned += renownGain
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.COINS, 0.92f))
    }
}

/** The yard bins and the sawmill's timber hopper: walk over, unload. */
class Buildings(private val bus: EventBus) {

    private var depositTarget = false
    private var woodTarget = false
    fun setDepositTarget() { depositTarget = true }
    fun clearDepositTarget() { depositTarget = false }
    fun setWoodTarget() { woodTarget = true }
    fun clearWoodTarget() { woodTarget = false }

    fun update(gs: GameState) {
        val player = gs.player
        if (depositTarget) {
            if (hypot(WorldLayout.BIN_X - player.x, WorldLayout.BIN_Z - player.z) > Buildings.INTERACT_REACH) return
            depositTarget = false
            val carried = gs.inventory.total
            if (carried == 0) {
                bus.notices.tryEmit(EventBus.Notice("Nothing to deposit", EventBus.COLOR_WARN, WorldLayout.BIN_X, WorldLayout.BIN_Z))
                return
            }
            val counts = gs.inventory.countsArray()
            var added = 0
            for (i in counts.indices) {
                if (counts[i] <= 0) continue
                val moved = gs.bins.add(Ore.entries[i], counts[i])
                if (moved > 0) {
                    gs.inventory.takeAt(Ore.entries[i], moved)
                    added += moved
                }
            }
            if (added < carried) {
                bus.notices.tryEmit(EventBus.Notice("Bins full — $added of $carried stored", EventBus.COLOR_WARN, WorldLayout.BIN_X, WorldLayout.BIN_Z))
            } else {
                bus.notices.tryEmit(EventBus.Notice("$carried ore stored", EventBus.COLOR_INFO, WorldLayout.BIN_X, WorldLayout.BIN_Z))
            }
            if (added > 0) bus.sfx.tryEmit(EventBus.Sfx(SfxId.BUY, 1.2f))
        }
        if (woodTarget) {
            if (hypot(WorldLayout.SAWMILL_X - player.x, WorldLayout.SAWMILL_Z - player.z) > Buildings.INTERACT_REACH + 0.6f) return
            woodTarget = false
            val timber = gs.inventory.timber
            if (timber == 0) {
                bus.notices.tryEmit(EventBus.Notice("No timber to feed the saw", EventBus.COLOR_WARN, WorldLayout.SAWMILL_X, WorldLayout.SAWMILL_Z))
                return
            }
            val space = Wood.SAWMILL_HOPPER_CAP - gs.sawmillHopper
            val moved = gs.inventory.takeTimber(space)
            gs.sawmillHopper += moved
            if (moved < timber) {
                bus.notices.tryEmit(EventBus.Notice("Saw hopper full — $moved of $timber logs fed", EventBus.COLOR_WARN, WorldLayout.SAWMILL_X, WorldLayout.SAWMILL_Z))
            } else {
                bus.notices.tryEmit(EventBus.Notice("$timber logs on the saw", EventBus.COLOR_INFO, WorldLayout.SAWMILL_X, WorldLayout.SAWMILL_Z))
            }
        }
    }
}

/** The sixteen upgrades: `cost = round(base × growth^level)`. */
class UpgradeManager(private val bus: EventBus) {

    fun tryBuy(gs: GameState, type: UpgradeType) {
        val level = gs.upgradeLevel(type)
        if (level >= type.maxLevel) return
        val cost = Upgrades.cost(type, level)
        if (gs.coins < cost) {
            bus.notices.tryEmit(EventBus.Notice("Need $cost c", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
            return
        }
        gs.coins -= cost
        gs.upgradeLevels[type.ordinal] = level + 1
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.BUY))
        bus.notices.tryEmit(
            EventBus.Notice("${type.label} Lv ${level + 1}", EventBus.COLOR_GOLD, gs.player.x, gs.player.z)
        )
    }

    fun syncBonuses(gs: GameState) {
        gs.player.moveSpeedBonus = gs.moveSpeed - PlayerConfig.MOVE_SPEED
    }
}

/**
 * The furnaces: a hopper of raw ore in, one smelt at a time, ingots out to
 * the tray. Furnace II (both furnace upgrades maxed) takes the precious
 * tiers and leaves the common ore to the base fire.
 */
class Forge(private val bus: EventBus) {

    private var loadTarget: Ore? = null
    private var loadTargetFurnace2 = false

    fun clearLoadTarget() { loadTarget = null }

    /** Returns true when the walk-to-furnace trip should start. */
    fun requestLoad(gs: GameState, ore: Ore, furnace2: Boolean): Boolean {
        if (furnace2 && !gs.furnace2Unlocked) return false
        if (!gs.furnaceAccepts(furnace2, ore)) return false
        val furnace = if (furnace2) gs.furnace2 else gs.furnace
        if (furnace.hopperTotal >= gs.hopperCap) {
            bus.notices.tryEmit(EventBus.Notice("Hopper is full", EventBus.COLOR_WARN, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
            return false
        }
        if (availableOre(gs, ore) < 1) {
            bus.notices.tryEmit(EventBus.Notice("No ${ore.label.lowercase()} ore banked", EventBus.COLOR_WARN, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
            return false
        }
        loadTarget = ore
        loadTargetFurnace2 = furnace2
        return true
    }

    fun update(gs: GameState, dt: Float) {
        val pending = loadTarget
        if (pending != null) {
            val fx = if (loadTargetFurnace2) WorldLayout.FURNACE2_X else WorldLayout.FURNACE_X
            val fz = if (loadTargetFurnace2) WorldLayout.FURNACE2_Z else WorldLayout.FURNACE_Z
            val player = gs.player
            if (hypot(fx - player.x, fz - player.z) <= Buildings.FORGE_REACH) {
                val furnace = if (loadTargetFurnace2) gs.furnace2 else gs.furnace
                loadTarget = null
                loadHopper(gs, furnace, pending)
            }
        }
        stepFurnace(gs, gs.furnace, dt, false)
        if (gs.furnace2Unlocked) stepFurnace(gs, gs.furnace2, dt, true)
    }

    /** Moves ore from bins (then pack) into the hopper, up to its cap. */
    private fun loadHopper(gs: GameState, furnace: GameState.FurnaceState, ore: Ore) {
        var space = gs.hopperCap - furnace.hopperTotal
        if (space <= 0) {
            bus.notices.tryEmit(EventBus.Notice("Hopper is full", EventBus.COLOR_WARN, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
            return
        }
        var moved = gs.bins.takeAt(ore, space)
        space -= moved
        if (space > 0) moved += gs.inventory.takeAt(ore, space)
        if (moved > 0) {
            furnace.hopper[ore.ordinal] += moved
            bus.sfx.tryEmit(EventBus.Sfx(SfxId.SMELT, 0.9f))
            bus.notices.tryEmit(EventBus.Notice("Hopper: +$moved ${ore.label.lowercase()}", EventBus.COLOR_INFO, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
        } else {
            bus.notices.tryEmit(EventBus.Notice("No ${ore.label.lowercase()} ore to load", EventBus.COLOR_WARN, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z))
        }
    }

    private fun stepFurnace(gs: GameState, furnace: GameState.FurnaceState, dt: Float, isFurnace2: Boolean) {
        if (furnace.smeltingOre == null) {
            // Pull the next ore (lowest tier first — the player loads what they want).
            for (ore in Ore.entries) {
                if (furnace.hopper[ore.ordinal] > 0 && gs.furnaceAccepts(isFurnace2, ore) && gs.oreUnlocked(ore)) {
                    furnace.hopper[ore.ordinal]--
                    furnace.smeltingOre = ore
                    furnace.smeltRemain = ore.smeltSeconds * gs.smeltScale
                    return
                }
            }
            return
        }
        furnace.smeltRemain -= dt
        if (furnace.smeltRemain <= 0f) {
            val ore = furnace.smeltingOre ?: return
            val metal = metalFor(ore) ?: run { furnace.smeltingOre = null; return }
            if (gs.ingots.total >= gs.trayCap) {
                // Tray full: the pour waits.
                furnace.smeltRemain = 0.05f
                return
            }
            furnace.smeltingOre = null
            gs.ingots.add(metal.ordinal, 1)
            gs.stats.ingotsSmelted[metal.ordinal]++
            bus.smeltDone.tryEmit(EventBus.SmeltDone(metal))
            bus.notices.tryEmit(EventBus.Notice("+1 ${metal.label}", EventBus.COLOR_INFO, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z, -26f))
            levelsEvent(bus, gs, gs.addXp(Progression.XP_PER_INGOT))
        }
    }

    companion object {
        fun availableOre(gs: GameState, ore: Ore): Int =
            gs.bins.countAt(ore) + gs.inventory.countAt(ore)

        fun metalFor(ore: Ore): Metal? = Metal.entries.firstOrNull { it.ore == ore }

        /** Advances the furnaces by [seconds] of offline time; returns ingots gained. */
        fun applyOffline(gs: GameState, seconds: Float): IntArray {
            val gained = IntArray(Metal.entries.size)
            var remaining = seconds
            fun step(furnace: GameState.FurnaceState, isF2: Boolean) {
                while (remaining > 0f) {
                    if (furnace.smeltingOre == null) {
                        var found = false
                        for (ore in Ore.entries) {
                            if (furnace.hopper[ore.ordinal] > 0 && gs.furnaceAccepts(isF2, ore) && gs.oreUnlocked(ore)) {
                                furnace.hopper[ore.ordinal]--
                                furnace.smeltingOre = ore
                                furnace.smeltRemain = ore.smeltSeconds * gs.smeltScale
                                found = true
                                break
                            }
                        }
                        if (!found) return
                    }
                    val ore = furnace.smeltingOre ?: return
                    val metal = metalFor(ore) ?: run { furnace.smeltingOre = null; return }
                    if (gs.ingots.total >= gs.trayCap) return
                    if (furnace.smeltRemain > remaining) {
                        furnace.smeltRemain -= remaining
                        remaining = 0f
                    } else {
                        remaining -= furnace.smeltRemain
                        furnace.smeltingOre = null
                        gs.ingots.add(metal.ordinal, 1)
                        gained[metal.ordinal]++
                    }
                }
            }
            step(gs.furnace, false)
            if (gs.furnace2Unlocked) step(gs.furnace2, true)
            return gained
        }
    }
}

/**
 * The forge: one queue, two lanes. Lane A is the player's (or the
 * Blacksmith's, or the Trip Hammer's floor); Lane B belongs to the Master
 * Smith. Each craft consumes exactly one ingot; Twin Strike can double it.
 */
class Craft(private val bus: EventBus) {

    private val rng = Random(11)

    /** True while the player stands at the anvil hammering. */
    var playerHammering = false

    fun clear(gs: GameState) {
        playerHammering = false
        gs.player.animState = AnimState.IDLE
        gs.player.swingTime = 0f
        gs.player.faceTargetX = Float.NaN
        gs.player.faceTargetZ = Float.NaN
    }

    /** Returns true when the walk-to-anvil trip should start. */
    fun request(gs: GameState, item: Item): Boolean {
        if (gs.craftQueue.size + activeLanes(gs) >= gs.queueCap) {
            bus.notices.tryEmit(EventBus.Notice("Forge queue is full", EventBus.COLOR_WARN, WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z))
            return false
        }
        if (gs.ingots.countAt(item.metal.ordinal) < 1) {
            bus.notices.tryEmit(EventBus.Notice("Need 1 ${item.metal.label}", EventBus.COLOR_WARN, WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z))
            return false
        }
        gs.craftQueue.add(item)
        return true
    }

    private fun activeLanes(gs: GameState): Int {
        var n = 0
        if (gs.laneA != null) n++
        if (gs.laneB != null) n++
        return n
    }

    fun update(gs: GameState, dt: Float, blacksmithAtAnvil: Boolean, masterSmithAtAnvil: Boolean) {
        // The player's hammering: starts the next job and drives it at full speed.
        if (playerHammering) {
            val player = gs.player
            if (hypot(WorldLayout.ANVIL_X - player.x, WorldLayout.ANVIL_Z - player.z) <= Buildings.FORGE_REACH) {
                player.animState = AnimState.SWING
                player.faceTargetX = WorldLayout.ANVIL_X
                player.faceTargetZ = WorldLayout.ANVIL_Z
                player.clearTarget()
                player.swingTime += dt
                val cycle = (player.swingTime / gs.swingInterval).toInt()
                if (cycle != lastHammerCycle) {
                    lastHammerCycle = cycle
                    bus.hammerStruck.tryEmit(EventBus.HammerStruck(WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z))
                    bus.sfx.tryEmit(EventBus.Sfx(SfxId.HAMMER, 0.95f + 0.08f * (cycle % 3)))
                }
            }
        }

        // Pull jobs into idle lanes: an anvil only takes a job once somebody
        // can work it (the player taps it, a smith tends it, or the trip
        // hammer is rigged). The ingot is consumed when the job STARTS.
        val playerAtAnvil = playerHammering && hypot(gs.player.x - WorldLayout.ANVIL_X, gs.player.z - WorldLayout.ANVIL_Z) <= Buildings.FORGE_REACH
        val laneATendered = playerAtAnvil || blacksmithAtAnvil || Town.tripHammerBuilt(gs.villageSlots)
        if (gs.laneA == null && laneATendered) pullJob(gs, laneB = false)
        if (gs.laneB == null && masterSmithAtAnvil) pullJob(gs, laneB = true)

        // Advance lane A: the player hammers fastest; the Blacksmith tends at
        // his own pace; the Trip Hammer is a floor under an idle forge.
        val laneA = gs.laneA
        if (laneA != null) {
            val rate = when {
                playerAtAnvil -> 1f
                blacksmithAtAnvil -> 0.55f
                Town.tripHammerBuilt(gs.villageSlots) -> 0.4f
                else -> 0f
            }
            advanceLane(gs, laneA, rate * dt)
        }
        val laneB = gs.laneB
        if (laneB != null && masterSmithAtAnvil) {
            advanceLane(gs, laneB, 0.55f * dt)
        }
    }

    private var lastHammerCycle = -1

    private fun pullJob(gs: GameState, laneB: Boolean) {
        val masterSmith = gs.workers.any { it.role == Role.MASTER_SMITH && !it.paused }
        val queue = gs.craftQueue
        var chosen: Item? = null
        var chosenIdx = -1
        for (i in queue.indices) {
            val item = queue[i]
            val rare = item.metal.ore.pickLevel >= 3
            val ok = if (laneB) rare else (if (masterSmith) !rare else true)
            if (ok && gs.ingots.countAt(item.metal.ordinal) > 0) {
                chosen = item
                chosenIdx = i
                break
            }
        }
        if (chosen == null) return
        queue.removeAt(chosenIdx)
        if (gs.ingots.takeAt(chosen.metal.ordinal, 1) < 1) return
        val job = GameState.ActiveCraft(chosen, chosen.craftSeconds * gs.craftScale)
        if (laneB) gs.laneB = job else gs.laneA = job
    }

    private fun advanceLane(gs: GameState, job: GameState.ActiveCraft, advance: Float) {
        if (advance <= 0f) return
        if (gs.items.total >= gs.rackCap) return // rack full: the anvil waits
        job.remain -= advance
        if (job.remain <= 0f) {
            var made = 1
            if (rng.nextDouble() < gs.twinStrikeChance) made++
            gs.items.add(job.item.ordinal, made)
            gs.stats.itemsCrafted[job.item.ordinal] += made
            bus.itemCrafted.tryEmit(EventBus.ItemCrafted(job.item))
            bus.sfx.tryEmit(EventBus.Sfx(SfxId.CRAFT))
            if (made > 1) {
                bus.notices.tryEmit(EventBus.Notice("Twin strike! 2 × ${job.item.label}", EventBus.COLOR_GOLD, WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z, -40f))
            }
            levelsEvent(bus, gs, gs.addXp(Progression.XP_PER_ITEM * made))
            if (gs.laneA === job) gs.laneA = null
            if (gs.laneB === job) gs.laneB = null
        }
    }
}

/** The sawmill: one log every 16s (×0.65 with any millrace). */
object SawmillSystem {
    fun update(gs: GameState, dt: Float) {
        if (!gs.sawmillSawing && gs.sawmillHopper > 0) {
            gs.sawmillSawing = true
            gs.sawmillRemain = Wood.SAW_SECONDS_PER_LOG *
                (if (Town.anyMillrace(gs.villageSlots)) Wood.MILLRACE_SAW_SCALE else 1f)
        }
        if (gs.sawmillSawing) {
            gs.sawmillRemain -= dt
            if (gs.sawmillRemain <= 0f) {
                gs.sawmillHopper--
                gs.sawmillSawing = gs.sawmillHopper > 0
                gs.materials[Town.Material.PLANKS.ordinal]++
                gs.stats.planksSawn++
            }
        }
    }
}

/**
 * The crew: every role is a small state machine that re-evaluates what to
 * do. Wages are real — a missed payroll downs tools until it clears.
 */
class CrewSystem(private val bus: EventBus, private val economy: Economy) {

    private enum class State {
        IDLE, WALK_TO_ROCK, MINING,
        TO_PILE, TO_BINS, UNLOAD_BINS,
        TO_FURNACE, LOAD_HOPPER,
        TO_TREE, CHOPPING, TO_SAWMILL, FEED_SAW,
        TEND_ANVIL, TO_MARKET, SELLING,
    }

    private class WorkerAI {
        var state = State.IDLE
        var rockIndex = -1
        var treeIndex = -1
        var swingTime = 0f
        var lastImpactCycle = -1
        var cooldown = 0f
        var carryingOre = IntArray(0)
        var carryingTotal = 0
        var carryingTimber = 0
        var targetPile: WorldLayout.MineField? = null
        var targetFurnace2 = false
    }

    private val ai = ArrayList<WorkerAI>()
    private val rng = Random(97)

    fun hire(gs: GameState, role: Role): Boolean {
        val option = gs.hireOptionFor(role)
        if (option != null && !option.canHire) {
            val reason = option.lockedReason
            bus.notices.tryEmit(EventBus.Notice(reason ?: "Cannot hire", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
            if (reason == null && gs.coins < role.hireCost) {
                bus.notices.tryEmit(EventBus.Notice("Need ${role.hireCost} c", EventBus.COLOR_WARN, gs.player.x, gs.player.z, -24f))
            }
            return false
        }
        if (gs.coins < role.hireCost) {
            bus.notices.tryEmit(EventBus.Notice("Need ${role.hireCost} c", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
            return false
        }
        gs.coins -= role.hireCost
        val w = Worker(gs.workers.size, role, gs.workers.size % com.villageforge.config.Theme.MINER_STYLES.size)
        w.body.x = -2f + gs.workers.size * 1.2f
        w.body.z = 4f
        w.body.prevX = w.body.x
        w.body.prevZ = w.body.z
        gs.workers.add(w)
        ai.add(WorkerAI())
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.BUY))
        bus.notices.tryEmit(EventBus.Notice("${role.label} hired — ${role.wagePerMin} c/min", EventBus.COLOR_INFO, w.body.x, w.body.z))
        return true
    }

    fun fire(gs: GameState, index: Int) {
        if (index < 0 || index >= gs.workers.size) return
        val w = gs.workers.removeAt(index)
        if (index < ai.size) ai.removeAt(index)
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.DENIED))
        bus.notices.tryEmit(EventBus.Notice("${w.role.label} let go", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
    }

    fun update(gs: GameState, dt: Float, craft: Craft) {
        while (ai.size < gs.workers.size) ai.add(WorkerAI())

        // ---- Payroll: once a minute, the wage bill leaves the purse ----
        gs.wageClock += dt
        if (gs.wageClock >= Crew.PAYROLL_SECONDS) {
            gs.wageClock -= Crew.PAYROLL_SECONDS
            if (gs.workers.isEmpty()) {
                gs.wagesUnpaid = false
            } else {
                val bill = gs.wagePerMinute()
                if (gs.coins >= bill) {
                    gs.coins -= bill
                    gs.stats.wagesPaid += bill
                    if (gs.wagesUnpaid) {
                        gs.wagesUnpaid = false
                        bus.notices.tryEmit(EventBus.Notice("Wages paid — the crew is back to work", EventBus.COLOR_INFO, gs.player.x, gs.player.z))
                    }
                } else {
                    if (!gs.wagesUnpaid) {
                        bus.notices.tryEmit(EventBus.Notice("Wages unpaid — the crew downs tools!", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
                        bus.sfx.tryEmit(EventBus.Sfx(SfxId.DENIED))
                    }
                    gs.wagesUnpaid = true
                }
            }
        }

        for (i in gs.workers.indices) stepWorker(gs, gs.workers[i], ai[i], dt)
    }

    fun blacksmithAtAnvil(gs: GameState): Boolean = atPost(gs, Role.BLACKSMITH, WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z)
    fun masterSmithAtAnvil(gs: GameState): Boolean = atPost(gs, Role.MASTER_SMITH, WorldLayout.ANVIL2_X, WorldLayout.ANVIL2_Z)

    private fun atPost(gs: GameState, role: Role, x: Float, z: Float): Boolean {
        for (w in gs.workers) {
            if (w.role != role || w.paused) continue
            if (hypot(w.body.x - x, w.body.z - z) < Buildings.FORGE_REACH + 0.6f) return true
        }
        return false
    }

    private fun stepWorker(gs: GameState, worker: Worker, a: WorkerAI, dt: Float) {
        val body = worker.body
        val role = worker.role
        // Speed = role speed × crew training × field boons; frozen while unpaid.
        body.speedOverride = if (gs.wagesUnpaid || worker.paused) 0.0001f else role.speed * gs.crewSpeedMul

        if (gs.wagesUnpaid || worker.paused) {
            if (body.animState == AnimState.SWING) {
                body.animState = AnimState.IDLE
                body.swingTime = 0f
            }
            a.state = State.IDLE
            a.cooldown = 1f
            body.update(dt)
            return
        }

        when (role) {
            Role.MINER, Role.PIT_MASTER, Role.SPEC_IRON, Role.SPEC_COPPER, Role.SPEC_SILVER, Role.SPEC_GOLD, Role.SPEC_MYTHRIL ->
                stepMiner(gs, worker, a, dt)
            Role.CARRIER -> stepCarrier(gs, worker, a, dt)
            Role.SMELTER, Role.MASTER_SMELTER -> stepSmelter(gs, worker, a, dt)
            Role.LUMBERJACK -> stepLumberjack(gs, worker, a, dt)
            Role.BLACKSMITH -> stepTender(gs, worker, a, WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z, dt)
            Role.MASTER_SMITH -> stepTender(gs, worker, a, WorldLayout.ANVIL2_X, WorldLayout.ANVIL2_Z, dt)
            Role.MERCHANT -> stepMerchant(gs, worker, a, dt)
        }
    }

    private fun pileFor(gs: GameState, field: WorldLayout.MineField): GameState.MinePile = when (field) {
        WorldLayout.MineField.NORTH -> gs.northPile
        WorldLayout.MineField.EAST -> gs.eastPile
        WorldLayout.MineField.HOLLOW -> gs.hollowPile
    }

    private fun allowedFields(gs: GameState, role: Role): List<WorldLayout.MineField> = when (role) {
        Role.PIT_MASTER -> listOf(WorldLayout.MineField.EAST)
        Role.MINER -> listOf(WorldLayout.MineField.NORTH, WorldLayout.MineField.EAST, WorldLayout.MineField.HOLLOW)
        else -> listOf(WorldLayout.MineField.NORTH, WorldLayout.MineField.EAST, WorldLayout.MineField.HOLLOW)
    }

    private fun stepMiner(gs: GameState, worker: Worker, a: WorkerAI, dt: Float) {
        val body = worker.body
        val role = worker.role
        val specOre: Ore? = when (role) {
            Role.SPEC_IRON -> Ore.IRON
            Role.SPEC_COPPER -> Ore.COPPER
            Role.SPEC_SILVER -> Ore.SILVER
            Role.SPEC_GOLD -> Ore.GOLD
            Role.SPEC_MYTHRIL -> Ore.MYTHRIL
            else -> null
        }
        when (a.state) {
            State.IDLE -> {
                a.cooldown -= dt
                if (a.cooldown > 0f) {
                    if (!body.isMoving && rng.nextFloat() < 0.004f) {
                        body.setTarget(body.x + (rng.nextFloat() - 0.5f) * 4f, body.z + (rng.nextFloat() - 0.5f) * 4f)
                    }
                } else {
                    val fields = allowedFields(gs, role)
                    val candidates = gs.rocks.filter { rock ->
                        rock.alive && gs.oreUnlocked(rock.ore) &&
                            (specOre == null || rock.ore == specOre) &&
                            rock.field in fields &&
                            (rock.field != WorldLayout.MineField.EAST || gs.eastCutOpen)
                    }
                    if (candidates.isEmpty()) {
                        a.cooldown = 1.5f
                    } else {
                        val rock = candidates[rng.nextInt(candidates.size)]
                        a.rockIndex = rock.index
                        val d = hypot(rock.x - body.x, rock.z - body.z).coerceAtLeast(0.01f)
                        val t = ((d - 1.5f) / d).coerceAtLeast(0f)
                        body.setRoutedTarget(body.x + (rock.x - body.x) * t, body.z + (rock.z - body.z) * t)
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

                    val swing = role.swingSeconds / gs.crewSpeedMul
                    a.swingTime += dt
                    body.swingTime = a.swingTime
                    val cycle = (a.swingTime / swing).toInt()
                    val phase = a.swingTime - cycle * swing
                    if (phase >= swing * PlayerConfig.IMPACT_FRACTION && a.lastImpactCycle != cycle) {
                        a.lastImpactCycle = cycle
                        bus.minerStruck.tryEmit(EventBus.MinerStruck(a.rockIndex))
                        rock.hp -= 1
                        if (rock.hp <= 0) {
                            rock.breakRock()
                            gs.stats.rocksBroken++
                            var amount = 1
                            if (rng.nextDouble() < gs.doubleOreChance) amount++
                            val pile = pileFor(gs, rock.field)
                            val space = (gs.stockpileCap - pile.total).coerceAtLeast(0)
                            val added = amount.coerceAtMost(space)
                            if (added > 0) pile.add(rock.ore, added)
                            gs.stats.oresMined[rock.ore.ordinal] += added
                            a.state = State.IDLE
                            a.cooldown = Crew.IDLE_COOLDOWN_SECONDS * (0.7f + rng.nextFloat() * 0.6f)
                            body.animState = AnimState.WALK
                            body.swingTime = 0f
                        }
                    }
                }
            }
            else -> { a.state = State.IDLE }
        }
        body.update(dt)
    }

    private fun stepCarrier(gs: GameState, worker: Worker, a: WorkerAI, dt: Float) {
        val body = worker.body
        if (a.carryingOre.size != Ore.entries.size) a.carryingOre = IntArray(Ore.entries.size)
        when (a.state) {
            State.IDLE -> {
                a.cooldown -= dt
                if (a.carryingTotal > 0) {
                    // Still holding ore: head for the bins.
                    body.setRoutedTarget(WorldLayout.BIN_X, WorldLayout.BIN_Z)
                    a.state = State.TO_BINS
                    return body.update(dt)
                }
                if (a.cooldown > 0f) return body.update(dt)
                // The busier pile first (spec §11).
                val piles = listOf(
                    WorldLayout.MineField.NORTH to gs.northPile.total,
                    WorldLayout.MineField.EAST to gs.eastPile.total,
                    WorldLayout.MineField.HOLLOW to gs.hollowPile.total,
                ).filter { it.second > 0 }.sortedByDescending { it.second }
                if (piles.isEmpty()) {
                    a.cooldown = 2f
                    return body.update(dt)
                }
                a.targetPile = piles[0].first
                val (px, pz) = WorldLayout.stockpileOf(piles[0].first)
                body.setRoutedTarget(px, pz)
                a.state = State.TO_PILE
            }
            State.TO_PILE -> {
                if (!body.isMoving) {
                    val field = a.targetPile ?: WorldLayout.MineField.NORTH
                    val pile = pileFor(gs, field)
                    var capacity = gs.crewCarry(worker.role) - a.carryingTotal
                    for (ore in Ore.entries) {
                        if (capacity <= 0) break
                        val taken = pile.takeAt(ore, capacity)
                        if (taken > 0) { a.carryingOre[ore.ordinal] += taken; a.carryingTotal += taken; capacity -= taken }
                    }
                    body.setRoutedTarget(WorldLayout.BIN_X, WorldLayout.BIN_Z)
                    a.state = State.TO_BINS
                }
            }
            State.TO_BINS -> {
                if (!body.isMoving) {
                    if (hypot(body.x - WorldLayout.BIN_X, body.z - WorldLayout.BIN_Z) > 2.4f) {
                        a.state = State.IDLE
                        a.cooldown = 0.8f
                    } else {
                        a.state = State.UNLOAD_BINS
                    }
                }
            }
            State.UNLOAD_BINS -> {
                var remaining = false
                for (ore in Ore.entries) {
                    val have = a.carryingOre[ore.ordinal]
                    if (have <= 0) continue
                    val moved = gs.bins.add(ore, have)
                    a.carryingOre[ore.ordinal] -= moved
                    a.carryingTotal -= moved
                    if (moved < have) remaining = true
                }
                if (a.carryingTotal > 0 && remaining) {
                    // Bins brimming: wait a while, then try the market of last resort — the smith's pack is not theirs to fill.
                    a.state = State.IDLE
                    a.cooldown = 6f
                } else {
                    a.state = State.IDLE
                    a.cooldown = 0.5f
                }
            }
            else -> { a.state = State.IDLE }
        }
        body.update(dt)
    }

    private fun stepSmelter(gs: GameState, worker: Worker, a: WorkerAI, dt: Float) {
        val body = worker.body
        if (a.carryingOre.size != Ore.entries.size) a.carryingOre = IntArray(Ore.entries.size)
        val precious = worker.role == Role.MASTER_SMELTER
        val furnace2 = precious
        if (precious && !gs.furnace2Unlocked) return body.update(dt) // nothing to feed yet
        val furnace = if (furnace2) gs.furnace2 else gs.furnace

        when (a.state) {
            State.IDLE -> {
                a.cooldown -= dt
                if (a.carryingTotal > 0) {
                    val fx = if (furnace2) WorldLayout.FURNACE2_X else WorldLayout.FURNACE_X
                    val fz = if (furnace2) WorldLayout.FURNACE2_Z else WorldLayout.FURNACE_Z
                    body.setRoutedTarget(fx, fz)
                    a.state = State.TO_FURNACE
                    return body.update(dt)
                }
                if (a.cooldown > 0f) return body.update(dt)
                if (furnace.hopperTotal >= gs.hopperCap) {
                    a.cooldown = 2f
                    return body.update(dt)
                }
                // What can this fire take, and do the bins hold any of it?
                var want: Ore? = null
                for (ore in Ore.entries) {
                    if (!gs.furnaceAccepts(furnace2, ore)) continue
                    if (!gs.oreUnlocked(ore)) continue
                    if (ore.pickLevel >= 3 != precious) continue
                    if (gs.bins.countAt(ore) > 0) { want = ore; break }
                }
                if (want == null) {
                    a.cooldown = 2f
                    return body.update(dt)
                }
                body.setRoutedTarget(WorldLayout.BIN_X, WorldLayout.BIN_Z)
                a.state = State.TO_BINS
            }
            State.TO_BINS -> {
                if (!body.isMoving) {
                    if (hypot(body.x - WorldLayout.BIN_X, body.z - WorldLayout.BIN_Z) > 2.4f) {
                        a.state = State.IDLE; a.cooldown = 0.8f
                        return body.update(dt)
                    }
                    var capacity = gs.crewCarry(worker.role) - a.carryingTotal
                    for (ore in Ore.entries) {
                        if (capacity <= 0) break
                        if (!gs.furnaceAccepts(furnace2, ore) || !gs.oreUnlocked(ore)) continue
                        if (ore.pickLevel >= 3 != precious) continue
                        val taken = gs.bins.takeAt(ore, capacity)
                        if (taken > 0) { a.carryingOre[ore.ordinal] += taken; a.carryingTotal += taken; capacity -= taken }
                    }
                    val fx = if (furnace2) WorldLayout.FURNACE2_X else WorldLayout.FURNACE_X
                    val fz = if (furnace2) WorldLayout.FURNACE2_Z else WorldLayout.FURNACE_Z
                    body.setRoutedTarget(fx, fz)
                    a.state = State.TO_FURNACE
                }
            }
            State.TO_FURNACE -> {
                if (!body.isMoving) {
                    val fx = if (furnace2) WorldLayout.FURNACE2_X else WorldLayout.FURNACE_X
                    val fz = if (furnace2) WorldLayout.FURNACE2_Z else WorldLayout.FURNACE_Z
                    if (hypot(body.x - fx, body.z - fz) > 2.4f) {
                        a.state = State.IDLE; a.cooldown = 0.8f
                    } else {
                        a.state = State.LOAD_HOPPER
                    }
                }
            }
            State.LOAD_HOPPER -> {
                var space = gs.hopperCap - furnace.hopperTotal
                for (ore in Ore.entries) {
                    if (space <= 0) break
                    val have = a.carryingOre[ore.ordinal]
                    if (have <= 0) continue
                    val moved = have.coerceAtMost(space)
                    furnace.hopper[ore.ordinal] += moved
                    a.carryingOre[ore.ordinal] -= moved
                    a.carryingTotal -= moved
                    space -= moved
                }
                a.state = State.IDLE
                a.cooldown = 0.6f
            }
            else -> { a.state = State.IDLE }
        }
        body.update(dt)
    }

    private fun stepLumberjack(gs: GameState, worker: Worker, a: WorkerAI, dt: Float) {
        val body = worker.body
        when (a.state) {
            State.IDLE -> {
                a.cooldown -= dt
                if (a.carryingTimber >= gs.crewCarry(worker.role)) {
                    body.setRoutedTarget(WorldLayout.SAWMILL_X, WorldLayout.SAWMILL_Z)
                    a.state = State.TO_SAWMILL
                    return body.update(dt)
                }
                if (a.cooldown > 0f) return body.update(dt)
                if (gs.sawmillHopper >= Wood.SAWMILL_HOPPER_CAP && a.carryingTimber == 0) {
                    a.cooldown = 3f
                    return body.update(dt)
                }
                val candidates = gs.trees.filter { it.alive }
                if (candidates.isEmpty()) {
                    if (a.carryingTimber > 0) {
                        body.setRoutedTarget(WorldLayout.SAWMILL_X, WorldLayout.SAWMILL_Z)
                        a.state = State.TO_SAWMILL
                    } else {
                        a.cooldown = 2f
                    }
                    return body.update(dt)
                }
                val tree = candidates[rng.nextInt(candidates.size)]
                a.treeIndex = tree.index
                val d = hypot(tree.x - body.x, tree.z - body.z).coerceAtLeast(0.01f)
                val t = ((d - 1.4f) / d).coerceAtLeast(0f)
                body.setRoutedTarget(body.x + (tree.x - body.x) * t, body.z + (tree.z - body.z) * t)
                a.state = State.TO_TREE
            }
            State.TO_TREE -> {
                val tree = gs.trees[a.treeIndex]
                when {
                    !tree.alive -> { a.state = State.IDLE; a.cooldown = 0.3f }
                    !body.isMoving -> {
                        if (body.distanceTo(tree.x, tree.z) > 2.0f) {
                            a.state = State.IDLE; a.cooldown = 0.6f
                        } else {
                            a.state = State.CHOPPING
                            a.swingTime = 0f
                            a.lastImpactCycle = -1
                        }
                    }
                }
            }
            State.CHOPPING -> {
                val tree = gs.trees[a.treeIndex]
                if (!tree.alive) {
                    a.state = State.IDLE
                    a.cooldown = 0.3f
                } else {
                    body.animState = AnimState.SWING
                    body.faceTargetX = tree.x
                    body.faceTargetZ = tree.z
                    body.clearTarget()
                    val swing = worker.role.swingSeconds / gs.crewSpeedMul
                    a.swingTime += dt
                    body.swingTime = a.swingTime
                    val cycle = (a.swingTime / swing).toInt()
                    val phase = a.swingTime - cycle * swing
                    if (phase >= swing * PlayerConfig.IMPACT_FRACTION && a.lastImpactCycle != cycle) {
                        a.lastImpactCycle = cycle
                        bus.treeStruck.tryEmit(EventBus.TreeStruck(a.treeIndex))
                        tree.hp -= 1
                        if (tree.hp <= 0) {
                            tree.fell()
                            gs.stats.timberFelled++
                            a.carryingTimber++
                            a.state = State.IDLE
                            a.cooldown = 0.4f
                            body.animState = AnimState.WALK
                            body.swingTime = 0f
                        }
                    }
                }
            }
            State.TO_SAWMILL -> {
                if (!body.isMoving) {
                    if (hypot(body.x - WorldLayout.SAWMILL_X, body.z - WorldLayout.SAWMILL_Z) > 2.8f) {
                        a.state = State.IDLE; a.cooldown = 0.8f
                    } else {
                        a.state = State.FEED_SAW
                    }
                }
            }
            State.FEED_SAW -> {
                val space = Wood.SAWMILL_HOPPER_CAP - gs.sawmillHopper
                val moved = a.carryingTimber.coerceAtMost(space.coerceAtLeast(0))
                gs.sawmillHopper += moved
                a.carryingTimber -= moved
                a.state = State.IDLE
                a.cooldown = 0.5f
            }
            else -> { a.state = State.IDLE }
        }
        body.update(dt)
    }

    private fun stepTender(gs: GameState, worker: Worker, a: WorkerAI, x: Float, z: Float, dt: Float) {
        val body = worker.body
        val busy = gs.laneA != null || gs.laneB != null || gs.craftQueue.isNotEmpty()
        when (a.state) {
            State.IDLE -> {
                a.cooldown -= dt
                if (busy && a.cooldown <= 0f) {
                    val d = hypot(x - body.x, z - body.z)
                    if (d > 2.0f) {
                        body.setRoutedTarget(x + 1.1f, z + 0.9f)
                        a.state = State.TEND_ANVIL
                    } else {
                        a.state = State.TEND_ANVIL
                    }
                } else if (!body.isMoving && rng.nextFloat() < 0.003f) {
                    body.setTarget(body.x + (rng.nextFloat() - 0.5f) * 4f, body.z + (rng.nextFloat() - 0.5f) * 4f)
                }
            }
            State.TEND_ANVIL -> {
                if (!busy) {
                    a.state = State.IDLE
                    a.cooldown = 1.5f
                } else if (!body.isMoving) {
                    body.animState = AnimState.IDLE
                    body.faceTargetX = x
                    body.faceTargetZ = z
                }
            }
            else -> { a.state = State.IDLE }
        }
        body.update(dt)
    }

    private fun stepMerchant(gs: GameState, worker: Worker, a: WorkerAI, dt: Float) {
        val body = worker.body
        val night = !DayNight.merchantsOpen(gs.timeOfDay) && !gs.merchantUnlockedNight
        when (a.state) {
            State.IDLE -> {
                a.cooldown -= dt
                if (a.cooldown > 0f) return body.update(dt)
                if (night) {
                    // Waits out the dark hours by the stall.
                    a.cooldown = 4f
                    return body.update(dt)
                }
                if (gs.items.total > 0) {
                    body.setRoutedTarget(WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z)
                    a.state = State.TO_MARKET
                } else {
                    a.cooldown = 3f
                }
            }
            State.TO_MARKET -> {
                if (!body.isMoving) {
                    if (hypot(body.x - WorldLayout.TRADE_POST_X, body.z - WorldLayout.TRADE_POST_Z) > 2.6f) {
                        a.state = State.IDLE
                        a.cooldown = 0.8f
                    } else {
                        a.state = State.SELLING
                    }
                }
            }
            State.SELLING -> {
                // The cart is already loaded from the rack; sell up to his carry.
                economy.merchantSale(gs, gs.crewCarry(worker.role))
                a.state = State.IDLE
                a.cooldown = 6f
            }
            else -> { a.state = State.IDLE }
        }
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

/**
 * Offline progression (spec §14): the camp runs at 50% of live throughput
 * for a capped window (2h + 1.5h per Night Shift level, × windmill boon).
 * Under a minute away, no report at all.
 */
object OfflineLogic {

    fun apply(gs: GameState, elapsedRaw: Float, bus: EventBus): GameState.OfflineReport? {
        val elapsed = elapsedRaw.coerceAtMost(gs.offlineCapSeconds)
        if (elapsed < 60f) return null
        val half = elapsed * 0.5f
        val rng = Random(elapsed.toLong() * 31L + gs.workers.size)

        // Orders keep their clock while you are away.
        for (c in gs.commissions) c.remain = (c.remain - elapsed).coerceAtLeast(0f)

        // 1) Miners keep digging into their piles.
        val oreGains = IntArray(Ore.entries.size)
        val miners = gs.workers.count { it.role in setOf(Role.MINER, Role.PIT_MASTER) || Crew.isSpecialist(it.role) }
        if (miners > 0) {
            val fields = listOf(gs.northPile, gs.eastPile, gs.hollowPile)
            var minerIdx = 0
            for (w in gs.workers) {
                val isMiner = w.role in setOf(Role.MINER, Role.PIT_MASTER) || Crew.isSpecialist(w.role)
                if (!isMiner) continue
                val swing = w.role.swingSeconds / gs.crewSpeedMul
                val candidates = gs.rocks.filter { it.alive && gs.oreUnlocked(it.ore) && (it.field != WorldLayout.MineField.EAST || gs.eastCutOpen) }
                if (candidates.isEmpty()) continue
                val avgHp = candidates.map { it.ore.rockHp }.average().toFloat()
                val secondsPerOre = avgHp * swing + 12f
                val units = (half / secondsPerOre).toInt()
                val field = when {
                    w.role == Role.PIT_MASTER -> gs.eastPile
                    Crew.isSpecialist(w.role) -> fields[minerIdx % fields.size]
                    else -> fields[minerIdx % fields.size]
                }
                minerIdx++
                var left = units
                while (left > 0 && field.total < gs.stockpileCap) {
                    val rock = candidates[rng.nextInt(candidates.size)]
                    var amount = 1
                    if (rng.nextDouble() < gs.doubleOreChance) amount++
                    amount = amount.coerceAtMost((gs.stockpileCap - field.total).coerceAtLeast(0))
                    if (amount <= 0) break
                    field.add(rock.ore, amount)
                    oreGains[rock.ore.ordinal] += amount
                    gs.stats.oresMined[rock.ore.ordinal] += amount
                    left -= amount
                }
            }
        }

        // 2) Smelters load, furnaces pour — capped by hopper, ore, and tray.
        val smelter = gs.workers.any { it.role == Role.SMELTER && !it.paused }
        if (smelter) {
            // Feed the hoppers from the bins first (coarse).
            for (ore in Ore.entries) {
                if (!gs.furnaceAccepts(false, ore)) continue
                val space = (gs.hopperCap - gs.furnace.hopperTotal).coerceAtLeast(0)
                val moved = gs.bins.takeAt(ore, space).coerceAtMost(space)
                gs.furnace.hopper[ore.ordinal] += moved
            }
            if (gs.furnace2Unlocked) {
                for (ore in Ore.entries) {
                    if (ore.pickLevel < 3) continue
                    val space = (gs.hopperCap - gs.furnace2.hopperTotal).coerceAtLeast(0)
                    val moved = gs.bins.takeAt(ore, space).coerceAtMost(space)
                    gs.furnace2.hopper[ore.ordinal] += moved
                }
            }
        }
        val ingotGains = Forge.applyOffline(gs, half)
        if (ingotGains.any { it > 0 }) {
            for (i in ingotGains.indices) gs.stats.ingotsSmelted[i] += ingotGains[i]
        }

        // 3) The forge works if tended.
        var itemGains = 0
        val smith = gs.workers.any { it.role == Role.BLACKSMITH || it.role == Role.MASTER_SMITH }
        if (smith) {
            val rate = 0.55f * 0.5f * elapsed
            var budget = rate
            val queue = Item.entries.sortedByDescending { it.sell }
            for (item in queue) {
                while (budget >= item.craftSeconds * gs.craftScale && gs.ingots.countAt(item.metal.ordinal) > 0 && gs.items.total < gs.rackCap) {
                    gs.ingots.takeAt(item.metal.ordinal, 1)
                    var made = 1
                    if (rng.nextDouble() < gs.twinStrikeChance) made++
                    gs.items.add(item.ordinal, made)
                    gs.stats.itemsCrafted[item.ordinal] += made
                    itemGains += made
                    budget -= item.craftSeconds * gs.craftScale
                }
            }
        }

        // 4) The Merchant carts goods to market.
        var coinGains = 0
        val merchant = gs.workers.any { it.role == Role.MERCHANT }
        if (merchant && gs.items.total > 0) {
            val trips = (half / 45f).toInt().coerceAtLeast(1)
            val perTrip = gs.crewCarry(Role.MERCHANT)
            var sold = 0
            val order = Item.entries.sortedByDescending { it.sell }
            for (item in order) {
                if (sold >= trips * perTrip) break
                val have = gs.items.countAt(item.ordinal)
                if (have <= 0) continue
                val take = have.coerceAtMost(trips * perTrip - sold)
                gs.items.takeAt(item.ordinal, take)
                coinGains += (take * item.sell * gs.saleScale).toInt()
                sold += take
                val renown = (take * Town.renownWeight(item.sell) * Town.renownMul(gs.villageSlots)).toInt()
                gs.renown += renown
                gs.stats.renownEarned += renown
                CommissionSystem.onSold(bus, gs, item, take)
            }
            if (coinGains > 0) {
                gs.coins += coinGains
                gs.stats.coinsEarnedTotal += coinGains
            }
        }

        // 5) The sawmill cuts while the lumberjack fells.
        var plankGains = 0
        val lumberjack = gs.workers.any { it.role == Role.LUMBERJACK }
        if (lumberjack) {
            val secondsPerLog = 5f * (Role.LUMBERJACK.swingSeconds / gs.crewSpeedMul) + 14f
            val logs = (half / secondsPerLog).toInt()
            val sawSeconds = Wood.SAW_SECONDS_PER_LOG * (if (Town.anyMillrace(gs.villageSlots)) Wood.MILLRACE_SAW_SCALE else 1f)
            val sawable = (half / sawSeconds).toInt().coerceAtMost(logs + gs.sawmillHopper)
            val planks = sawable.coerceAtLeast(0)
            gs.sawmillHopper = (gs.sawmillHopper + logs - sawable).coerceIn(0, Wood.SAWMILL_HOPPER_CAP)
            if (planks > 0) {
                gs.materials[Town.Material.PLANKS.ordinal] += planks
                gs.stats.planksSawn += planks
                gs.stats.timberFelled += logs
                plankGains = planks
            }
        }

        val gained = oreGains.any { it > 0 } || ingotGains.any { it > 0 } || itemGains > 0 || plankGains > 0 || coinGains > 0
        if (gained) gs.stats.offlineGains += oreGains.sum() + ingotGains.sum() + itemGains + plankGains
        return if (gained) GameState.OfflineReport(elapsed, oreGains.toList(), ingotGains.toList(), itemGains, plankGains, coinGains) else null
    }

}

/** Advances time of day; the renderer reads it every frame. */
object DayNightSystem {
    fun update(gs: GameState, dt: Float) {
        gs.timeOfDay = (gs.timeOfDay + dt / DayNight.CYCLE_SECONDS) % 1f
        if (DayNight.isNightish(gs.timeOfDay)) gs.stats.nightSeconds += dt
    }
}

/** Watches every medal metric and pays out coin rewards on unlock. */
class AchievementSystem(private val bus: EventBus) {

    fun update(gs: GameState) {
        for (def in com.villageforge.config.Achievements.all) {
            if (def.id in gs.achievements) continue
            if (com.villageforge.config.Achievements.progress(gs, def) >= def.goal) {
                gs.achievements.add(def.id)
                gs.coins += def.reward
                gs.stats.coinsEarnedTotal += def.reward
                bus.sfx.tryEmit(EventBus.Sfx(SfxId.ACHIEVE))
                bus.achievementUnlocked.tryEmit(
                    EventBus.AchievementUnlocked(def.id, def.title, def.reward)
                )
            }
        }
    }
}
