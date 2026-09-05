package com.villageforge.systems

import com.villageforge.config.Buildings as BuildingData
import com.villageforge.config.Ore
import com.villageforge.config.Picks
import com.villageforge.config.PlayerConfig
import com.villageforge.config.Upgrades
import com.villageforge.config.WorldLayout
import com.villageforge.core.EventBus
import com.villageforge.core.SfxId
import com.villageforge.entities.AnimState
import com.villageforge.entities.Player
import com.villageforge.entities.Rock
import com.villageforge.state.GameState
import kotlin.math.hypot
import kotlin.random.Random

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
        if (added > 0) bus.oreMined.tryEmit(EventBus.OreMined(rock.ore, added, rock.x, rock.z))
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
        var total = 0
        for (ore in Ore.entries) {
            total += (gs.inventory.countAt(ore) + gs.stockpile.countAt(ore)) * ore.rawSell
        }
        if (total == 0) {
            bus.notices.tryEmit(EventBus.Notice("Nothing to sell", EventBus.COLOR_WARN, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z))
            return
        }
        gs.inventory.clearAll()
        gs.stockpile.clearAll()
        gs.coins += total
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.COINS))
        bus.notices.tryEmit(EventBus.Notice("+$total c", EventBus.COLOR_GOLD, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z))
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
