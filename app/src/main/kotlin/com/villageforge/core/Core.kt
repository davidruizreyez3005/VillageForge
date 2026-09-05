package com.villageforge.core

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.villageforge.config.Ore
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

class EventBus {
    data class OreMined(val ore: Ore, val amount: Int, val x: Float, val z: Float)
    data class RockStruck(val rockIndex: Int)
    data class Notice(val text: String, val colorArgb: Int, val x: Float, val z: Float)
    data class Sfx(val id: SfxId, val pitch: Float = 1f)

    val oreMined = MutableSharedFlow<OreMined>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val rockStruck = MutableSharedFlow<RockStruck>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val notices = MutableSharedFlow<Notice>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val sfx = MutableSharedFlow<Sfx>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    companion object {
        val COLOR_WARN: Int = 0xFFE2574C.toInt()
        val COLOR_GOLD: Int = 0xFFF1C40F.toInt()
        val COLOR_INFO: Int = 0xFF9FD08C.toInt()
    }
}

enum class SfxId { ROCK_HIT, ROCK_BREAK, COINS, BUY, DENIED }

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
            if (!scaleDetector.isInProgress) rig.panByPixels(dx, dy)
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
        panDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        return true
    }

    private fun handleTap(x: Float, y: Float, eventTime: Long) {
        if (isTapSpam(eventTime)) return
        val ground = rig.screenToGround(x, y)
        val rockIndex = hitTestRock(ground[0], ground[1])
        when {
            rockIndex >= 0 -> game.enqueue(GameState.Command.Mine(rockIndex))
            isOnStall(ground[0], ground[1]) -> game.enqueue(GameState.Command.Sell)
            isOnBin(ground[0], ground[1]) -> game.enqueue(GameState.Command.Deposit)
            else -> game.enqueue(GameState.Command.MoveTo(ground[0], ground[1]))
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
    )

    val persistenceWarning = MutableStateFlow(false)
    private val file = File(context.filesDir, "villageforge_save.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(game: GameState): Boolean {
        if (!file.exists()) return false
        return try {
            val data = json.decodeFromString(SaveData.serializer(), file.readText())
            if (data.version != VERSION) return false
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
            )
            val tmp = File(file.parentFile, FILE_NAME + ".tmp")
            tmp.writeText(json.encodeToString(SaveData.serializer(), data))
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
            persistenceWarning.value = false
        } catch (e: Exception) { persistenceWarning.value = true }
    }

    companion object { const val VERSION = 1; const val FILE_NAME = "villageforge_save.json" }
}
