package com.villageforge

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.villageforge.config.WorldLayout
import com.villageforge.core.AudioManager
import com.villageforge.core.EventBus
import com.villageforge.core.InputManager
import com.villageforge.core.SaveManager
import com.villageforge.core.SfxId
import com.villageforge.graphics.FilamentHost
import com.villageforge.graphics.WorldRenderer
import com.villageforge.state.GameState
import com.villageforge.systems.Buildings
import com.villageforge.systems.Economy
import com.villageforge.systems.Mining
import com.villageforge.systems.UpgradeManager
import com.villageforge.ui.GameScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot

class MainActivity : ComponentActivity() {

    private lateinit var host: FilamentHost
    private lateinit var world: WorldRenderer
    private lateinit var input: InputManager
    private lateinit var game: GameState
    private lateinit var bus: EventBus
    private lateinit var save: SaveManager
    private lateinit var audio: AudioManager
    private lateinit var mining: Mining
    private lateinit var economy: Economy
    private lateinit var buildings: Buildings
    private lateinit var upgrades: UpgradeManager
    private var simJob: Job? = null
    private var autosaveTicks = 0

    private val tickNanos = (GameState.TICK_SECONDS * 1e9f).toLong()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bus = EventBus()
        game = GameState()
        save = SaveManager(this)
        save.load(game)
        audio = AudioManager()
        audio.enabled = game.sfxEnabled

        host = FilamentHost()
        world = WorldRenderer(host.engine, game)
        host.bind(world)
        world.onPickUpgraded(game.pickTier)

        mining = Mining(bus)
        economy = Economy(bus)
        buildings = Buildings(bus)
        upgrades = UpgradeManager(bus)
        upgrades.syncBonuses(game)
        input = InputManager(this, world.cameraRig, game, bus)

        lifecycleScope.launch {
            launch {
                bus.rockStruck.collect {
                    world.onRockStruck(it.rockIndex)
                    audio.play(SfxId.ROCK_HIT, 0.9f + 0.2f * (it.rockIndex % 5) / 4f)
                }
            }
            launch { bus.oreMined.collect { audio.play(SfxId.ROCK_BREAK) } }
            launch {
                bus.notices.collect {
                    if (it.colorArgb == EventBus.COLOR_WARN) audio.play(SfxId.DENIED)
                }
            }
            launch { bus.sfx.collect { audio.play(it.id, it.pitch) } }
        }

        setContent { GameScreen(host, input, game, bus, world.cameraRig, save) }
    }

    override fun onResume() {
        super.onResume()
        host.start()
        audio.start()
        startSimLoop()
    }

    override fun onPause() {
        super.onPause()
        host.stop()
        simJob?.cancel()
        simJob = null
        audio.stop()
        save.save(game)
    }

    override fun onDestroy() {
        if (this::world.isInitialized) world.destroy()
        if (this::host.isInitialized) host.destroy()
        super.onDestroy()
    }

    private fun startSimLoop() {
        if (simJob != null) return
        simJob = lifecycleScope.launch(Dispatchers.Main.immediate) {
            var nextTick = System.nanoTime()
            while (isActive) {
                val now = System.nanoTime()
                if (now >= nextTick) {
                    stepSim(GameState.TICK_SECONDS)
                    game.lastTickNanos = System.nanoTime()
                    nextTick += tickNanos
                    if (nextTick < now - MAX_LAG_TICKS * tickNanos) nextTick = now + tickNanos
                }
                delay(5)
            }
        }
    }

    private fun stepSim(dt: Float) {
        drainCommands()
        game.player.update(dt)
        mining.update(game, dt)
        economy.update(game)
        buildings.update(game)
        game.publishUi()
        if (++autosaveTicks >= AUTOSAVE_TICKS) {
            autosaveTicks = 0
            save.save(game)
        }
    }

    private fun clearInteractionTargets() {
        mining.clearTarget(game.player)
        economy.clearTarget()
        buildings.clearTarget()
    }

    /** Walk-to point `distance` units short of (tx, tz), on the line from the player. */
    private fun standPoint(fromX: Float, fromZ: Float, tx: Float, tz: Float, distance: Float): Pair<Float, Float> {
        val dx = tx - fromX
        val dz = tz - fromZ
        val d = hypot(dx, dz)
        if (d < 0.001f) return fromX to fromZ
        val t = ((d - distance) / d).coerceAtLeast(0f)
        return (fromX + dx * t) to (fromZ + dz * t)
    }

    private fun drainCommands() {
        val player = game.player
        while (true) {
            val command = game.drainCommand() ?: break
            when (command) {
                is GameState.Command.MoveTo -> {
                    clearInteractionTargets()
                    player.setTarget(command.x, command.z)
                }
                is GameState.Command.Mine -> {
                    clearInteractionTargets()
                    val rock = game.rocks[command.rockIndex]
                    if (rock.ore.requiredPick.ordinal > game.pickTier) {
                        bus.notices.tryEmit(EventBus.Notice("Needs ${rock.ore.requiredPick.label}", EventBus.COLOR_WARN, rock.x, rock.z))
                        continue
                    }
                    if (game.inventory.isFull) {
                        bus.notices.tryEmit(EventBus.Notice("Carry full!", EventBus.COLOR_WARN, rock.x, rock.z))
                        continue
                    }
                    val stand = standPoint(player.x, player.z, rock.x, rock.z, 1.6f)
                    player.setTarget(stand.first, stand.second)
                    mining.setTarget(command.rockIndex)
                }
                is GameState.Command.Sell -> {
                    clearInteractionTargets()
                    val stand = standPoint(player.x, player.z, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z, 1.9f)
                    player.setTarget(stand.first, stand.second)
                    economy.setTarget()
                }
                is GameState.Command.Deposit -> {
                    if (!game.binOwned) continue
                    clearInteractionTargets()
                    if (game.inventory.total == 0) {
                        bus.notices.tryEmit(EventBus.Notice("Nothing to carry", EventBus.COLOR_WARN, player.x, player.z))
                        continue
                    }
                    val stand = standPoint(player.x, player.z, WorldLayout.BIN_X, WorldLayout.BIN_Z, 1.9f)
                    player.setTarget(stand.first, stand.second)
                    buildings.setDepositTarget()
                }
                is GameState.Command.BuyBin -> buildings.tryBuyBin(game)
                is GameState.Command.BuyPick -> upgrades.tryBuyPick(game)
                is GameState.Command.BuyBoots -> upgrades.tryBuyBoots(game)
                is GameState.Command.BuyBackpack -> upgrades.tryBuyBackpack(game)
                is GameState.Command.ToggleSound -> {
                    game.sfxEnabled = !game.sfxEnabled
                    audio.enabled = game.sfxEnabled
                }
            }
        }
    }

    companion object {
        const val MAX_LAG_TICKS = 5
        const val AUTOSAVE_TICKS = 300 // 30s at 10 Hz
    }
}
