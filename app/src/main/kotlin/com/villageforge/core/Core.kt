package com.villageforge.core

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.villageforge.config.DayNight
import com.villageforge.config.Item
import com.villageforge.config.Metal
import com.villageforge.config.PlayerConfig
import com.villageforge.config.WorldLayout
import com.villageforge.graphics.CameraRig
import com.villageforge.state.GameState
import java.io.File
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SfxId { ROCK_HIT, ROCK_BREAK, COINS, BUY, DENIED, SMELT, HAMMER, CRAFT, QUEST, LEVELUP, ACHIEVE, ORDER }

enum class Sheet { FORGE, SHOP, QUESTS, MEDALS, TOWN, ORDERS }

class EventBus {
    data class OreMined(val ore: com.villageforge.config.Ore, val amount: Int, val x: Float, val z: Float)
    data class RockStruck(val rockIndex: Int)
    data class Notice(val text: String, val colorArgb: Int, val x: Float, val z: Float, val yOffset: Float = 0f)
    data class Sfx(val id: SfxId, val pitch: Float = 1f)
    data class SmeltDone(val metal: Metal)
    data class ItemCrafted(val item: Item)
    data class HammerStruck(val x: Float, val z: Float)
    data class MinerStruck(val rockIndex: Int)
    data class LevelUp(val newLevel: Int)
    data class TapMarker(val x: Float, val y: Float)
    data class UiRequest(val sheet: Sheet)
    data class AchievementUnlocked(val id: String, val title: String, val reward: Int)
    /** v2.2 — a customer's order was placed (item, qty). */
    data class OrderPlaced(val itemName: String, val needed: Int)
    /** v2.2 — an order was completed by a sale (item, bounty coins). */
    data class CommissionFilled(val itemName: String, val bounty: Int, val renown: Int)
    /** v2.2 — a village slot advanced a stage (what it now says on the sign). */
    data class VillageBuilt(val label: String, val complete: Boolean)

    val oreMined = MutableSharedFlow<OreMined>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val rockStruck = MutableSharedFlow<RockStruck>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val notices = MutableSharedFlow<Notice>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val sfx = MutableSharedFlow<Sfx>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val smeltDone = MutableSharedFlow<SmeltDone>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val itemCrafted = MutableSharedFlow<ItemCrafted>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val hammerStruck = MutableSharedFlow<HammerStruck>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val minerStruck = MutableSharedFlow<MinerStruck>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val levelUp = MutableSharedFlow<LevelUp>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val tapMarker = MutableSharedFlow<TapMarker>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val uiRequest = MutableSharedFlow<UiRequest>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val achievementUnlocked = MutableSharedFlow<AchievementUnlocked>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val orderPlaced = MutableSharedFlow<OrderPlaced>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commissionFilled = MutableSharedFlow<CommissionFilled>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val villageBuilt = MutableSharedFlow<VillageBuilt>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    companion object {
        val COLOR_WARN: Int = 0xFFE2574C.toInt()
        val COLOR_GOLD: Int = 0xFFF1C40F.toInt()
        val COLOR_INFO: Int = 0xFF9FD08C.toInt()
    }
}

class InputManager(
    context: Context,
    private val rig: CameraRig,
    private val game: GameState,
    private val bus: EventBus,
) : View.OnTouchListener {

    private val scaleDetector: ScaleGestureDetector
    private val tapTimes = LongArray(PlayerConfig.TAPS_PER_SECOND_LIMIT)
    private var tapTimesIndex = 0

    private val panDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true
        override fun onScroll(begin: MotionEvent?, end: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!scaleDetector.isInProgress && end.pointerCount < 2) rig.panByPixels(dx, dy)
            return true
        }
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            handleTap(event.x, event.y, event.eventTime)
            return true
        }
    })

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                rig.zoomBy(detector.scaleFactor)
                return true
            }
        })
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        // Feed the pan/tap detector only single-pointer streams so the
        // pinch zoom never pans the world mid-gesture.
        if (event.pointerCount == 1 || event.actionMasked == MotionEvent.ACTION_DOWN) {
            panDetector.onTouchEvent(event)
        }
        return true
    }

    private fun handleTap(x: Float, y: Float, eventTime: Long) {
        if (isTapSpam(eventTime)) return
        val ground = rig.screenToGround(x, y)
        val rockIndex = hitTestRock(ground[0], ground[1])
        when {
            rockIndex >= 0 -> game.enqueue(GameState.Command.Mine(rockIndex))
            isOnFurnace(ground[0], ground[1]) -> bus.uiRequest.tryEmit(EventBus.UiRequest(Sheet.FORGE))
            isOnWell(ground[0], ground[1]) -> bus.uiRequest.tryEmit(EventBus.UiRequest(Sheet.TOWN))
            isOnStall(ground[0], ground[1]) -> game.enqueue(GameState.Command.Sell)
            isOnBin(ground[0], ground[1]) -> game.enqueue(GameState.Command.Deposit)
            else -> {
                bus.tapMarker.tryEmit(EventBus.TapMarker(x, y))
                game.enqueue(GameState.Command.MoveTo(ground[0], ground[1]))
            }
        }
    }

    /** The plaza well opens the town sheet — it IS the village ladder. */
    private fun isOnWell(x: Float, z: Float): Boolean =
        hypot(x - com.villageforge.config.Town.WELL_X, z - com.villageforge.config.Town.WELL_Z) < 1.8f

    private fun hitTestRock(x: Float, z: Float): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (rock in game.rocks) {
            if (!rock.alive) continue
            val d = hypot(rock.x - x, rock.z - z)
            if (d < 1.6f && d < bestDist) { best = rock.index; bestDist = d }
        }
        return best
    }

    private fun isOnStall(x: Float, z: Float): Boolean =
        hypot(x - WorldLayout.TRADE_POST_X, z - WorldLayout.TRADE_POST_Z) < 2.2f

    private fun isOnBin(x: Float, z: Float): Boolean =
        game.binOwned && hypot(x - WorldLayout.BIN_X, z - WorldLayout.BIN_Z) < 1.5f

    private fun isOnFurnace(x: Float, z: Float): Boolean =
        game.furnaceOwned && hypot(x - WorldLayout.FURNACE_X, z - WorldLayout.FURNACE_Z) < 2.2f

    private fun isTapSpam(now: Long): Boolean {
        var recent = 0
        for (t in tapTimes) if (now - t < 1000L) recent++
        if (recent >= PlayerConfig.TAPS_PER_SECOND_LIMIT) return true
        tapTimes[tapTimesIndex] = now
        tapTimesIndex = (tapTimesIndex + 1) % tapTimes.size
        return false
    }
}

class SaveManager(context: Context) {
    @Serializable
    data class SaveData(
        val version: Int, val coins: Int, val pickTier: Int, val binOwned: Boolean,
        val carriedOre: List<Int>, val playerX: Float, val playerZ: Float, val playerFacing: Float,
        val bootsLevel: Int = 0, val backpackLevel: Int = 0, val sfxEnabled: Boolean = true,
        val stockpiledOre: List<Int> = emptyList(),
        val rockAlive: List<Int> = emptyList(), val rockHp: List<Int> = emptyList(),
        val rockRespawn: List<Float> = emptyList(),
        // v2 additions
        val furnaceOwned: Boolean = false,
        val ingots: List<Int> = emptyList(),
        val items: List<Int> = emptyList(),
        val smeltQueueMetal: List<Int> = emptyList(),
        val smeltQueueRemain: List<Float> = emptyList(),
        val minersHired: Int = 0,
        val questIndex: Int = 0,
        val xp: Int = 0,
        val level: Int = 1,
        val timeOfDay: Float = DayNight.START_TIME,
        val lastPlayedEpochMs: Long = 0L,
        val statsOresMined: List<Int> = emptyList(),
        val statsOreSold: Int = 0,
        val statsCoinsEarned: Int = 0,
        val statsIngotsSmelted: List<Int> = emptyList(),
        val statsItemsCrafted: List<Int> = emptyList(),
        val statsPlaySeconds: Float = 0f,
        // v3 additions (2.1)
        val musicEnabled: Boolean = true,
        val achievements: List<String> = emptyList(),
        val statsRocksBroken: Int = 0,
        val statsOfflineGains: Int = 0,
        val statsNightSeconds: Float = 0f,
        // v4 additions (2.2 — the town layer)
        val renown: Int = 0,
        val honour: Int = 0,
        val villageSlots: List<Int> = emptyList(),
        val commissionItem: List<Int> = emptyList(),
        val commissionNeeded: List<Int> = emptyList(),
        val commissionFilled: List<Int> = emptyList(),
        val commissionRemain: List<Float> = emptyList(),
        val commissionBounty: List<Int> = emptyList(),
        val commissionRenown: List<Int> = emptyList(),
        val commissionHonour: List<Int> = emptyList(),
        val weatherClock: Float = 0f,
        val weatherWet: Boolean = false,
        val statsCommissionsFilled: Int = 0,
        val statsCommissionsExpired: Int = 0,
        val statsRenownEarned: Int = 0,
        val statsBuildStages: Int = 0,
        val statsRainSeconds: Float = 0f,
    )

    /** What one slot row shows in the village picker. */
    data class SlotSummary(
        val slot: Int,
        val level: Int,
        val coins: Int,
        val playSeconds: Float,
        val lastPlayedMs: Long,
        val miners: Int,
        val questIndex: Int,
    )

    val persistenceWarning = MutableStateFlow(false)
    /** Live slot overview for the title screen; null = empty slot. */
    val slotsFlow = MutableStateFlow(List(SLOT_COUNT) { null as SlotSummary? })
    /** Set by load() so the caller can compute offline progress. */
    var lastPlayedEpochMs: Long = 0L
        private set

    /** The slot the current session saves into. */
    var activeSlot = 0

    private val dir = context.filesDir
    private val json = Json { ignoreUnknownKeys = true }

    init {
        migrateLegacySave()
        refreshSummaries()
    }

    private fun fileFor(slot: Int): File = File(dir, "${FILE_PREFIX}${slot.coerceIn(0, SLOT_COUNT - 1)}.json")

    /** v2.0 wrote a single fixed-name file; adopt it as slot 0 exactly once. */
    private fun migrateLegacySave() {
        try {
            val legacy = File(dir, LEGACY_FILE_NAME)
            if (legacy.exists() && (0 until SLOT_COUNT).none { fileFor(it).exists() }) {
                legacy.renameTo(fileFor(0))
            }
        } catch (_: Exception) {
        }
    }

    fun refreshSummaries() {
        slotsFlow.value = (0 until SLOT_COUNT).map { slot ->
            readSummary(slot)
        }
    }

    private fun readSummary(slot: Int): SlotSummary? {
        val file = fileFor(slot)
        if (!file.exists()) return null
        return try {
            val data = json.decodeFromString(SaveData.serializer(), file.readText())
            SlotSummary(
                slot = slot, level = data.level, coins = data.coins,
                playSeconds = data.statsPlaySeconds, lastPlayedMs = data.lastPlayedEpochMs,
                miners = data.minersHired, questIndex = data.questIndex,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Most recently played slot, or -1 when the device has no villages. */
    fun mostRecentSlot(): Int {
        var best = -1
        var bestMs = -1L
        for (slot in 0 until SLOT_COUNT) {
            val s = readSummary(slot) ?: continue
            if (s.lastPlayedMs > bestMs) { bestMs = s.lastPlayedMs; best = slot }
        }
        return best
    }

    fun hasSave(): Boolean = mostRecentSlot() >= 0

    fun load(game: GameState, slot: Int = activeSlot): Boolean {
        activeSlot = slot
        val file = fileFor(slot)
        if (!file.exists()) return false
        return try {
            val data = json.decodeFromString(SaveData.serializer(), file.readText())
            if (data.version != VERSION && data.version != V2_VERSION && data.version != V1_VERSION && data.version != V3_VERSION) return false
            game.coins = data.coins
            game.pickTier = data.pickTier
            game.binOwned = data.binOwned
            game.bootsLevel = data.bootsLevel
            game.backpackLevel = data.backpackLevel
            game.sfxEnabled = data.sfxEnabled
            game.musicEnabled = data.musicEnabled
            game.inventory.setCounts(data.carriedOre)
            game.stockpile.setCounts(data.stockpiledOre)
            game.player.x = data.playerX
            game.player.z = data.playerZ
            game.player.prevX = data.playerX
            game.player.prevZ = data.playerZ
            game.player.facing = data.playerFacing
            game.player.prevFacing = data.playerFacing
            if (data.rockAlive.isNotEmpty()) {
                // Size-padded so v2 saves (24 rocks) load into the 32-rock v2.1 world.
                for (i in game.rocks.indices) {
                    game.rocks[i].alive = i < data.rockAlive.size && data.rockAlive[i] != 0
                    game.rocks[i].hp = if (i < data.rockHp.size) data.rockHp[i] else game.rocks[i].ore.rockHp
                    game.rocks[i].respawnTimer = if (i < data.rockRespawn.size) data.rockRespawn[i] else 0f
                }
            }
            // v2 fields (defaults cover v1 saves)
            game.furnaceOwned = data.furnaceOwned
            game.ingots.setCounts(data.ingots)
            game.items.setCounts(data.items)
            game.smeltQueue.clear()
            if (data.smeltQueueMetal.size == data.smeltQueueRemain.size) {
                for (i in data.smeltQueueMetal.indices) {
                    val metal = Metal.entries.getOrNull(data.smeltQueueMetal[i]) ?: continue
                    game.smeltQueue.add(GameState.SmeltBatch(metal, data.smeltQueueRemain[i]))
                }
            }
            if (data.minersHired > 0) {
                val styleCount = com.villageforge.config.Theme.MINER_STYLES.size
                repeat(data.minersHired) { idx ->
                    val miner = com.villageforge.entities.Miner(game.miners.size, game.miners.size % styleCount)
                    miner.body.x = -2f + idx * 1.4f
                    miner.body.z = 4f
                    miner.body.prevX = miner.body.x
                    miner.body.prevZ = miner.body.z
                    game.miners.add(miner)
                }
            }
            game.questIndex = data.questIndex
            game.xp = data.xp
            game.level = data.level.coerceAtLeast(1)
            game.timeOfDay = data.timeOfDay
            lastPlayedEpochMs = data.lastPlayedEpochMs
            game.stats.setOresMined(data.statsOresMined)
            game.stats.oreSold = data.statsOreSold
            game.stats.coinsEarnedTotal = data.statsCoinsEarned
            game.stats.setIngots(data.statsIngotsSmelted)
            game.stats.setItems(data.statsItemsCrafted)
            game.stats.playSeconds = data.statsPlaySeconds
            // v3 fields (defaults cover v1/v2 saves)
            game.achievements.clear()
            game.achievements.addAll(data.achievements)
            game.stats.rocksBroken = data.statsRocksBroken
            game.stats.offlineGains = data.statsOfflineGains
            game.stats.nightSeconds = data.statsNightSeconds
            // v4 fields (defaults cover v1/v2/v3 saves)
            game.renown = data.renown
            game.honour = data.honour
            for (i in game.villageSlots.indices) {
                if (i < data.villageSlots.size) game.villageSlots[i] = data.villageSlots[i].coerceIn(0, com.villageforge.config.Town.slots[i].maxStage)
            }
            game.commissions.clear()
            if (data.commissionItem.size == data.commissionNeeded.size &&
                data.commissionItem.size == data.commissionRemain.size &&
                data.commissionItem.size == data.commissionBounty.size) {
                for (i in data.commissionItem.indices) {
                    val item = Item.entries.getOrNull(data.commissionItem[i]) ?: continue
                    val def = com.villageforge.config.Town.commissions.firstOrNull { it.item == item } ?: continue
                    val needed = data.commissionNeeded[i].coerceAtLeast(1)
                    game.commissions.add(
                        GameState.Commission(
                            id = i,
                            item = item,
                            needed = needed,
                            filled = (data.commissionFilled.getOrNull(i) ?: 0).coerceIn(0, needed),
                            remain = data.commissionRemain[i].coerceIn(0f, def.secs),
                            bounty = data.commissionBounty[i],
                            renown = data.commissionRenown.getOrNull(i) ?: def.renown,
                            honour = data.commissionHonour.getOrNull(i) ?: def.honour,
                        )
                    )
                }
            }
            game.weatherClock = data.weatherClock.coerceAtLeast(30f)
            game.weatherWet = false // a shower is a live-session mood; sessions start dry
            game.stats.commissionsFilled = data.statsCommissionsFilled
            game.stats.commissionsExpired = data.statsCommissionsExpired
            game.stats.renownEarned = data.statsRenownEarned
            game.stats.buildStages = data.statsBuildStages
            game.stats.rainSeconds = data.statsRainSeconds
            true
        } catch (e: Exception) { false }
    }

    fun save(game: GameState) {
        try {
            val file = fileFor(activeSlot)
            val data = SaveData(
                version = VERSION, coins = game.coins, pickTier = game.pickTier,
                binOwned = game.binOwned, carriedOre = game.inventory.countsArray().toList(),
                stockpiledOre = game.stockpile.countsArray().toList(),
                playerX = game.player.x, playerZ = game.player.z, playerFacing = game.player.facing,
                bootsLevel = game.bootsLevel, backpackLevel = game.backpackLevel,
                sfxEnabled = game.sfxEnabled,
                musicEnabled = game.musicEnabled,
                rockAlive = game.rocks.map { if (it.alive) 1 else 0 },
                rockHp = game.rocks.map { it.hp },
                rockRespawn = game.rocks.map { it.respawnTimer },
                furnaceOwned = game.furnaceOwned,
                ingots = game.ingots.counts(),
                items = game.items.counts(),
                smeltQueueMetal = game.smeltQueue.map { it.metal.ordinal },
                smeltQueueRemain = game.smeltQueue.map { it.remain },
                minersHired = game.miners.size,
                questIndex = game.questIndex,
                xp = game.xp,
                level = game.level,
                timeOfDay = game.timeOfDay,
                lastPlayedEpochMs = System.currentTimeMillis(),
                statsOresMined = game.stats.oresMined.toList(),
                statsOreSold = game.stats.oreSold,
                statsCoinsEarned = game.stats.coinsEarnedTotal,
                statsIngotsSmelted = game.stats.ingotsSmelted.toList(),
                statsItemsCrafted = game.stats.itemsCrafted.toList(),
                statsPlaySeconds = game.stats.playSeconds,
                achievements = game.achievements.toList(),
                statsRocksBroken = game.stats.rocksBroken,
                statsOfflineGains = game.stats.offlineGains,
                statsNightSeconds = game.stats.nightSeconds,
                // v4 — the town layer
                renown = game.renown,
                honour = game.honour,
                villageSlots = game.villageSlots.toList(),
                commissionItem = game.commissions.map { it.item.ordinal },
                commissionNeeded = game.commissions.map { it.needed },
                commissionFilled = game.commissions.map { it.filled },
                commissionRemain = game.commissions.map { it.remain },
                commissionBounty = game.commissions.map { it.bounty },
                commissionRenown = game.commissions.map { it.renown },
                commissionHonour = game.commissions.map { it.honour },
                weatherClock = if (game.weatherWet) 180f else game.weatherClock.coerceAtLeast(60f),
                weatherWet = false,
                statsCommissionsFilled = game.stats.commissionsFilled,
                statsCommissionsExpired = game.stats.commissionsExpired,
                statsRenownEarned = game.stats.renownEarned,
                statsBuildStages = game.stats.buildStages,
                statsRainSeconds = game.stats.rainSeconds,
            )
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(SaveData.serializer(), data))
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
            persistenceWarning.value = false
            refreshSummaries()
        } catch (e: Exception) { persistenceWarning.value = true }
    }

    fun delete(slot: Int) {
        try {
            fileFor(slot).delete()
            persistenceWarning.value = false
            refreshSummaries()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val SLOT_COUNT = 3
        const val VERSION = 4
        const val V3_VERSION = 3
        const val V2_VERSION = 2
        const val V1_VERSION = 1
        const val LEGACY_FILE_NAME = "villageforge_save.json"
        const val FILE_PREFIX = "villageforge_slot"
    }
}

/** Small mutable-list setter helpers for Stats arrays (save load path). */
private fun GameState.Stats.setOresMined(values: List<Int>) {
    for (i in oresMined.indices) if (i < values.size) oresMined[i] = values[i]
}

private fun GameState.Stats.setIngots(values: List<Int>) {
    for (i in ingotsSmelted.indices) if (i < values.size) ingotsSmelted[i] = values[i]
}

private fun GameState.Stats.setItems(values: List<Int>) {
    for (i in itemsCrafted.indices) if (i < values.size) itemsCrafted[i] = values[i]
}
