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
import kotlin.math.atan2
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SfxId { ROCK_HIT, ROCK_BREAK, COINS, BUY, DENIED, SMELT, HAMMER, CRAFT, QUEST, LEVELUP }

enum class Sheet { FORGE, SHOP, QUESTS }

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

    // Two-finger orbit tracking
    private var rotating = false
    private var lastRotationAngle = 0f

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
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount == 2) {
                rotating = true
                lastRotationAngle = angleBetween(event)
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.pointerCount <= 2) {
                rotating = false
            }
            MotionEvent.ACTION_MOVE -> if (rotating && event.pointerCount >= 2) {
                val a = angleBetween(event)
                var delta = Math.toDegrees((a - lastRotationAngle).toDouble())
                while (delta > 180.0) delta -= 360.0
                while (delta < -180.0) delta += 360.0
                rig.rotateBy(delta.toFloat())
                lastRotationAngle = a
            }
        }
        // Feed the pan/tap detector only single-pointer streams so the
        // two-finger orbit never pans the world mid-gesture.
        if (event.pointerCount == 1 || event.actionMasked == MotionEvent.ACTION_DOWN) {
            panDetector.onTouchEvent(event)
        }
        return true
    }

    private fun angleBetween(event: MotionEvent): Float {
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return atan2(dy, dx)
    }

    private fun handleTap(x: Float, y: Float, eventTime: Long) {
        if (isTapSpam(eventTime)) return
        val ground = rig.screenToGround(x, y)
        val rockIndex = hitTestRock(ground[0], ground[1])
        when {
            rockIndex >= 0 -> game.enqueue(GameState.Command.Mine(rockIndex))
            isOnFurnace(ground[0], ground[1]) -> bus.uiRequest.tryEmit(EventBus.UiRequest(Sheet.FORGE))
            isOnStall(ground[0], ground[1]) -> game.enqueue(GameState.Command.Sell)
            isOnBin(ground[0], ground[1]) -> game.enqueue(GameState.Command.Deposit)
            else -> {
                bus.tapMarker.tryEmit(EventBus.TapMarker(x, y))
                game.enqueue(GameState.Command.MoveTo(ground[0], ground[1]))
            }
        }
    }

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
    )

    val persistenceWarning = MutableStateFlow(false)
    /** Set by load() so the caller can compute offline progress. */
    var lastPlayedEpochMs: Long = 0L
        private set

    private val file = File(context.filesDir, "villageforge_save.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(game: GameState): Boolean {
        if (!file.exists()) return false
        return try {
            val data = json.decodeFromString(SaveData.serializer(), file.readText())
            if (data.version != VERSION && data.version != V1_VERSION) return false
            game.coins = data.coins
            game.pickTier = data.pickTier
            game.binOwned = data.binOwned
            game.bootsLevel = data.bootsLevel
            game.backpackLevel = data.backpackLevel
            game.sfxEnabled = data.sfxEnabled
            game.inventory.setCounts(data.carriedOre)
            game.stockpile.setCounts(data.stockpiledOre)
            game.player.x = data.playerX
            game.player.z = data.playerZ
            game.player.prevX = data.playerX
            game.player.prevZ = data.playerZ
            game.player.facing = data.playerFacing
            game.player.prevFacing = data.playerFacing
            if (data.rockAlive.size == game.rocks.size) {
                for (i in game.rocks.indices) {
                    game.rocks[i].alive = data.rockAlive[i] != 0
                    game.rocks[i].hp = data.rockHp[i]
                    game.rocks[i].respawnTimer = data.rockRespawn[i]
                }
            }
            // v2 fields (defaults cover v1 saves)
            game.furnaceOwned = data.furnaceOwned
            game.ingots.setCounts(data.ingots)
            game.items.setCounts(data.items)
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
            true
        } catch (e: Exception) { false }
    }

    fun save(game: GameState) {
        try {
            val data = SaveData(
                version = VERSION, coins = game.coins, pickTier = game.pickTier,
                binOwned = game.binOwned, carriedOre = game.inventory.countsArray().toList(),
                stockpiledOre = game.stockpile.countsArray().toList(),
                playerX = game.player.x, playerZ = game.player.z, playerFacing = game.player.facing,
                bootsLevel = game.bootsLevel, backpackLevel = game.backpackLevel,
                sfxEnabled = game.sfxEnabled,
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
            )
            val tmp = File(file.parentFile, FILE_NAME + ".tmp")
            tmp.writeText(json.encodeToString(SaveData.serializer(), data))
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
            persistenceWarning.value = false
        } catch (e: Exception) { persistenceWarning.value = true }
    }

    fun reset() {
        try {
            file.delete()
            persistenceWarning.value = false
        } catch (_: Exception) {
        }
    }

    fun hasSave(): Boolean = file.exists()

    companion object {
        const val VERSION = 2
        const val V1_VERSION = 1
        const val FILE_NAME = "villageforge_save.json"
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
