package com.villageforge.systems

import com.villageforge.config.DayNight
import com.villageforge.config.Item
import com.villageforge.config.PlayerConfig
import com.villageforge.config.Town
import com.villageforge.config.WorldLayout
import com.villageforge.core.EventBus
import com.villageforge.core.SfxId
import com.villageforge.entities.AnimState
import com.villageforge.state.GameState
import kotlin.math.hypot
import kotlin.random.Random

/**
 * v3.0 — the town layer, aligned with the prototype:
 *
 * CommissionSystem places orders at the market (filled BY SELLING, from any
 * source). The board opens at 12 renown; simultaneous slots scale 1/2/3 at
 * 0/45/110; the first order waits 40s after unlock, then arrivals every
 * 65–125s; customers keep the Merchant's hours and don't set out in the
 * rain. VillageSystem owns the build ladder: both renown AND prestige gates,
 * the one-press whole bill (coins + materials), and the power budget.
 */
class CommissionSystem(private val bus: EventBus) {

    private val rng = Random(2028)
    private var spawnClock = Town.FIRST_ORDER_GRACE
    private var nextId = 1

    fun update(gs: GameState, dt: Float) {
        val boardOpen = gs.renown >= Town.RENOWN_FOR_BOARD
        val cap = Town.boardCapacity(gs.renown)

        // Expire quietly: an order you ignore costs you nothing but the bounty.
        val expired = gs.commissions.filter { it.remain <= 0f }
        if (expired.isNotEmpty()) {
            for (c in expired) {
                gs.commissions.remove(c)
                gs.stats.commissionsExpired++
                dismissCustomer(gs, c)
            }
            bus.notices.tryEmit(
                EventBus.Notice("A customer gave up waiting", EventBus.COLOR_WARN, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z, -30f)
            )
        }

        // A sale may have filled an order; pay out and clear it here.
        for (c in gs.commissions.toList()) {
            c.remain -= dt
            if (c.filled >= c.needed) complete(gs, c)
        }

        // Walk-in customers place new orders while the board has room, the
        // market is open, and it isn't raining (orders already placed wait it out).
        val marketOpen = DayNight.merchantsOpen(gs.timeOfDay)
        val dry = gs.weatherRain < 0.35f
        if (boardOpen && marketOpen && dry && gs.commissions.size < cap) {
            spawnClock -= dt
            if (spawnClock <= 0f) {
                spawnClock = Town.ARRIVE_MIN + rng.nextFloat() * (Town.ARRIVE_MAX - Town.ARRIVE_MIN)
                tryPlace(gs)
            }
        } else if (!boardOpen) {
            // The grace delay restarts relative to unlock.
            spawnClock = Town.FIRST_ORDER_GRACE
        }

        // Departed customers finish their walk to the road edge, then vanish.
        val arrived = ArrayList<GameState.Commission>()
        for (c in gs.departingCustomers) {
            if (!c.customer.isMoving) arrived.add(c)
            else c.customer.update(dt)
        }
        gs.departingCustomers.removeAll(arrived)
    }

    private fun tryPlace(gs: GameState) {
        // Customers only ask for goods whose ore is already uncovered,
        // weighted toward the finer goods the town has grown into.
        val craftable = Town.commissions.filter { Town.craftableAt(it.item, gs.pickLevel) }
        if (craftable.isEmpty()) return
        val weights = craftable.map { def ->
            val value = def.item.sell
            1 + Town.renownWeight(value)
        }
        var pick = rng.nextInt(weights.sum())
        var chosen = craftable[0]
        for (i in craftable.indices) {
            pick -= weights[i]
            if (pick < 0) { chosen = craftable[i]; break }
        }
        val def = chosen
        val needed = def.min + rng.nextInt(def.max - def.min + 1)
        val bounty = (needed * def.item.sell * def.coinMul).toInt().coerceAtLeast(5)
        val c = GameState.Commission(
            id = nextId++, item = def.item, needed = needed, filled = 0,
            remain = def.secs, bounty = bounty, renown = def.renown, honour = def.honour,
        )
        gs.commissions.add(c)
        spawnCustomer(gs, c)
        bus.orderPlaced.tryEmit(EventBus.OrderPlaced(def.item.label, needed))
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.ORDER, 0.95f))
        bus.notices.tryEmit(
            EventBus.Notice(
                "Order: $needed × ${def.item.label}",
                EventBus.COLOR_INFO, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z, -30f,
            )
        )
    }

    /** Anything that reaches the market counts against live orders for that good. */
    private fun complete(gs: GameState, c: GameState.Commission) {
        gs.commissions.remove(c)
        gs.coins += c.bounty
        gs.stats.coinsEarnedTotal += c.bounty
        gs.stats.commissionsFilled++
        gs.renown += c.renown
        gs.stats.renownEarned += c.renown
        gs.honour += c.honour
        dismissCustomer(gs, c)
        levelsEvent(bus, gs, gs.addXp(c.bounty / 4))
        bus.commissionFilled.tryEmit(EventBus.CommissionFilled(c.item.label, c.bounty, c.renown))
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.QUEST))
        bus.notices.tryEmit(
            EventBus.Notice(
                "Order filled: +${c.bounty} c",
                EventBus.COLOR_GOLD, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z, -30f,
            )
        )
    }

    /**
     * Customers come from finished homes (each cottage is a customer's front
     * door); with none yet, they walk in from the map edge.
     */
    private fun spawnCustomer(gs: GameState, c: GameState.Commission) {
        val body = c.customer
        body.moveSpeedBonus = Town.WALK_SPEED - PlayerConfig.MOVE_SPEED
        val doors = Town.homeDoors(gs.villageSlots)
        if (doors.isNotEmpty()) {
            val home = doors[c.id % doors.size]
            body.x = home.first
            body.z = home.second
        } else {
            body.x = Town.ROAD_EDGE_X
            body.z = Town.ROAD_EDGE_Z
        }
        body.prevX = body.x
        body.prevZ = body.z
        body.animState = AnimState.WALK
        val idx = (c.id % 3)
        body.setTarget(-1.9f + idx * 1.7f, 12.4f + (idx % 2) * 0.8f)
        c.customerAtMarket = false
    }

    /** The commission is over (filled or lapsed); its customer walks home. */
    private fun dismissCustomer(gs: GameState, c: GameState.Commission) {
        c.customerAtMarket = false
        c.customerLeaving = true
        c.customer.setTarget(Town.ROAD_EDGE_X, Town.ROAD_EDGE_Z)
        gs.departingCustomers.add(c)
    }

    /** Puts loaded commissions' customers back on the road (after a save load). */
    fun restoreWalkers(gs: GameState) {
        gs.departingCustomers.clear()
        for (c in gs.commissions) spawnCustomer(gs, c)
    }

    companion object {
        /**
         * Called by Economy on every sale: units of [item] that reached the
         * market count against every live order for that good — from any
         * source: the player's carry, the Merchant, or offline simulation.
         */
        fun onSold(bus: EventBus, gs: GameState, item: Item, count: Int) {
            var changed = false
            for (c in gs.commissions.toList()) {
                if (c.item != item || c.filled >= c.needed) continue
                c.filled = (c.filled + count).coerceAtMost(c.needed)
                changed = true
            }
            if (changed) bus.sfx.tryEmit(EventBus.Sfx(SfxId.ORDER, 1.15f))
        }
    }
}

private fun levelsEvent(bus: EventBus, gs: GameState, levels: Int) {
    if (levels > 0) bus.levelUp.tryEmit(EventBus.LevelUp(gs.level))
}

/**
 * Owns the build slots: one press quotes the whole bill — coins AND
 * materials — all-or-nothing. Gated by renown AND prestige, and machines
 * cannot be built without spare power capacity.
 */
class VillageSystem(private val bus: EventBus) {

    fun tryBuild(gs: GameState, slotIndex: Int) {
        val slot = Town.slots.getOrNull(slotIndex) ?: return
        val stage = gs.villageSlots[slotIndex]
        if (stage >= slot.maxStage) return
        val next = slot.stages[stage]
        if (gs.renown < next.renownReq || gs.prestige() < next.prestigeReq) {
            bus.notices.tryEmit(
                EventBus.Notice(
                    "Needs ${next.renownReq} renown · ${next.prestigeReq} prestige",
                    EventBus.COLOR_WARN, slot.x, slot.z,
                )
            )
            return
        }
        val isMachine = slot.kind == Town.SlotKind.BELLOWS_HOUSE || slot.kind == Town.SlotKind.TRIP_HAMMER
        if (isMachine && Town.powerGenerated(gs.villageSlots) - Town.powerDrawn(gs.villageSlots) < 1) {
            bus.notices.tryEmit(
                EventBus.Notice("No spare water power — build a millrace first", EventBus.COLOR_WARN, slot.x, slot.z)
            )
            return
        }
        // The whole bill, one press: coins + every material, all-or-nothing.
        val coinBill = slot.stages[stage].coin
        if (gs.coins < coinBill) {
            bus.notices.tryEmit(EventBus.Notice("Need $coinBill c", EventBus.COLOR_WARN, slot.x, slot.z))
            return
        }
        for ((material, need) in next.supplies) {
            if (gs.materials[material.ordinal] < need) {
                bus.notices.tryEmit(
                    EventBus.Notice("Short ${need - gs.materials[material.ordinal]} ${material.label}", EventBus.COLOR_WARN, slot.x, slot.z)
                )
                return
            }
        }
        gs.coins -= coinBill
        for ((material, need) in next.supplies) gs.materials[material.ordinal] -= need
        gs.villageSlots[slotIndex] = stage + 1
        gs.stats.buildStages++
        val complete = gs.villageSlots[slotIndex] >= slot.maxStage
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.BUY))
        bus.notices.tryEmit(
            EventBus.Notice(
                if (complete) "${slot.kind.label} raised!" else next.label,
                EventBus.COLOR_GOLD, slot.x, slot.z, -30f,
            )
        )
        bus.villageBuilt.tryEmit(EventBus.VillageBuilt(next.label, complete))
        levelsEvent(bus, gs, gs.addXp(40 + 20 * (stage + 1)))
        if (complete && slot.boon != null) {
            bus.sfx.tryEmit(EventBus.Sfx(SfxId.ACHIEVE))
        }
    }

    /** The material shop: coins buy supplies, renown-gated per material. */
    fun tryBuyMaterial(gs: GameState, material: Town.Material) {
        if (gs.renown < material.renownReq) {
            bus.notices.tryEmit(EventBus.Notice("Needs ${material.renownReq} renown", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
            return
        }
        if (gs.coins < material.price) {
            bus.notices.tryEmit(EventBus.Notice("Need ${material.price} c", EventBus.COLOR_WARN, gs.player.x, gs.player.z))
            return
        }
        gs.coins -= material.price
        gs.materials[material.ordinal]++
        gs.stats.materialsBought++
        bus.sfx.tryEmit(EventBus.Sfx(SfxId.BUY, 1.1f))
    }
}

/**
 * The weather schedule: rare, short showers that grey the light and send the
 * townsfolk home. No rate, cap, or price ever moves — a mood, not a tax.
 */
class WeatherSystem(private val bus: EventBus) {

    private val rng = Random(4242)
    private var announced = false
    private var wetDuration = 0f

    fun update(gs: GameState, dt: Float) {
        gs.weatherClock -= dt
        if (!gs.weatherWet) {
            if (gs.weatherClock <= 0f) {
                gs.weatherWet = true
                wetDuration = Town.Weather.WET_MIN + rng.nextFloat() * (Town.Weather.WET_MAX - Town.Weather.WET_MIN)
                gs.weatherClock = wetDuration + Town.Weather.FADE_OUT
                announced = false
            }
        } else {
            val elapsedInWet = wetDuration + Town.Weather.FADE_OUT - gs.weatherClock
            gs.weatherRain = when {
                elapsedInWet < Town.Weather.FADE_IN -> elapsedInWet / Town.Weather.FADE_IN
                gs.weatherClock > Town.Weather.FADE_OUT -> 1f
                else -> (gs.weatherClock / Town.Weather.FADE_OUT).coerceIn(0f, 1f)
            }
            if (!announced && gs.weatherRain > 0.25f) {
                announced = true
                bus.notices.tryEmit(EventBus.Notice("Rain rolls over the valley", EventBus.COLOR_INFO, gs.player.x, gs.player.z, -34f))
            }
            if (gs.weatherRain > 0.5f) gs.stats.rainSeconds += dt
            if (gs.weatherClock <= 0f) {
                gs.weatherWet = false
                gs.weatherRain = 0f
                gs.weatherClock = Town.Weather.DRY_MIN + rng.nextFloat() * (Town.Weather.DRY_MAX - Town.Weather.DRY_MIN)
            }
        }
    }
}

/**
 * Townsfolk: every completed home moves a household in. Out on the streets
 * through the day on their own clocks, home at dusk or in the rain, windows
 * lit once they are in.
 */
class TownsfolkSystem(private val bus: EventBus) {

    private val rng = Random(5150)
    private var residentSignature = -1

    fun update(gs: GameState, dt: Float) {
        syncResidents(gs)
        val raining = gs.weatherRain > 0.35f
        val t = gs.timeOfDay
        var outCount = 0
        for (r in gs.residents) outCount += if (r.out) 1 else 0

        for (r in gs.residents) {
            val body = r.body
            val daylit = t > r.riseTime && t < r.sleepTime
            when {
                !r.out -> {
                    if (daylit && !raining && outCount < Town.RESIDENT_RIGS) {
                        r.out = true
                        outCount++
                        body.x = r.homeX
                        body.z = r.homeZ
                        body.prevX = body.x
                        body.prevZ = body.z
                        wanderTo(body)
                    }
                }
                !daylit || raining -> {
                    // Head home; the rig hides once they arrive.
                    if (!body.isMoving) {
                        if (hypot(body.x - r.homeX, body.z - r.homeZ) < 0.8f) {
                            r.out = false
                            outCount--
                        } else {
                            body.setTarget(r.homeX, r.homeZ)
                        }
                    }
                }
                else -> {
                    if (!body.isMoving) {
                        r.wanderTimer -= dt
                        if (r.wanderTimer <= 0f) {
                            r.wanderTimer = Town.WANDER_MIN + rng.nextFloat() * (Town.WANDER_MAX - Town.WANDER_MIN)
                            wanderTo(body)
                        }
                    }
                }
            }
            body.update(dt)
        }

        // Commission customers walk their own legs.
        for (c in gs.commissions) stepCustomer(c, dt)
    }

    private fun stepCustomer(c: GameState.Commission, dt: Float) {
        val body = c.customer
        if (c.customerLeaving) return // CommissionSystem walks the departing out
        if (!body.isMoving && !c.customerAtMarket) {
            c.customerAtMarket = true
        }
        if (c.customerAtMarket && !body.isMoving) {
            body.animState = AnimState.IDLE
            body.faceTargetX = WorldLayout.TRADE_POST_X
            body.faceTargetZ = WorldLayout.TRADE_POST_Z
        }
        body.update(dt)
    }

    private fun wanderTo(body: com.villageforge.entities.Player) {
        val x = rng.nextFloat() * (Town.WANDER_X.endInclusive - Town.WANDER_X.start) + Town.WANDER_X.start
        val z = rng.nextFloat() * (Town.WANDER_Z.endInclusive - Town.WANDER_Z.start) + Town.WANDER_Z.start
        body.setTarget(x, z)
    }

    /** Grows or trims the resident list to match the built homes. */
    private fun syncResidents(gs: GameState) {
        val want = Town.residentsFor(gs.villageSlots)
        if (want == residentSignature) return
        residentSignature = want
        while (gs.residents.size > want) gs.residents.removeAt(gs.residents.size - 1)
        while (gs.residents.size < want) {
            val homes = ArrayList<Pair<Float, Float>>()
            val farmIdx = Town.slotIndex("farm")
            val chapelIdx = Town.slotIndex("chapel")
            for (i in Town.slots.indices) {
                val slot = Town.slots[i]
                if (slot.kind == Town.SlotKind.HOUSE && gs.villageSlots[i] >= slot.maxStage) {
                    homes.add(slot.x to slot.z - 1.7f)
                    homes.add(slot.x + 0.9f to slot.z - 1.7f)
                }
            }
            if (gs.villageSlots[farmIdx] >= Town.slots[farmIdx].maxStage) {
                homes.add(Town.slots[farmIdx].x to Town.slots[farmIdx].z - 1.7f)
                homes.add(Town.slots[farmIdx].x + 0.9f to Town.slots[farmIdx].z - 1.7f)
            }
            if (gs.villageSlots[chapelIdx] >= Town.slots[chapelIdx].maxStage) {
                homes.add(Town.slots[chapelIdx].x + 2.2f to Town.slots[chapelIdx].z)
                homes.add(Town.slots[chapelIdx].x + 2.9f to Town.slots[chapelIdx].z)
            }
            val idx = gs.residents.size
            val home = homes.getOrNull(idx % homes.size.coerceAtLeast(1)) ?: (0f to 8f)
            val r = GameState.Resident(home.first, home.second)
            r.riseTime = 0.08f + rng.nextFloat() * 0.16f
            r.sleepTime = 0.70f + rng.nextFloat() * 0.14f
            r.body.moveSpeedBonus = Town.WALK_SPEED - PlayerConfig.MOVE_SPEED
            gs.residents.add(r)
        }
    }
}
