package com.villageforge

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.villageforge.config.BuildInfo
import com.villageforge.config.LightingProbe
import com.villageforge.config.WorldLayout
import com.villageforge.core.AudioManager
import com.villageforge.core.CrashReport
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
import java.io.PrintWriter
import java.io.StringWriter
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
    private var startupFailed = false

    private val tickNanos = (GameState.TICK_SECONDS * 1e9f).toLong()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashGuard()
        try {
            startApp(savedInstanceState)
        } catch (t: Throwable) {
            startupFailed = true
            abortStartup(t)
        }
    }

    /**
     * Saves any unexpected (post-startup) crash to disk before the normal
     * crash flow runs; the report is surfaced on the next launch.
     */
    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashReport.save(applicationContext, buildReport(throwable))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun startApp(savedInstanceState: Bundle?) {
        // Surface a saved crash report from a previous run before doing anything
        // else (plain Views only), so mid-game crashes are always diagnosable.
        CrashReport.load(this)?.let { pending ->
            startupFailed = true
            showReportScreen(pending, allowContinue = true)
            return
        }

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

        if (LightingProbe.ENABLED) {
            lifecycleScope.launch {
                delay(LightingProbe.START_DELAY_MILLIS)
                while (isActive) {
                    for (i in LightingProbe.presets.indices) {
                        world.applyProbePreset(LightingProbe.presets[i]) { linear -> host.setToneMapping(linear) }
                        LightingProbe.activeIndex.intValue = i
                        delay(LightingProbe.STEP_MILLIS)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (startupFailed) return
        host.start()
        audio.start()
        startSimLoop()
    }

    override fun onPause() {
        super.onPause()
        if (startupFailed) return
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

    /** Saves and displays the full error report; the app stays open on screen. */
    private fun abortStartup(t: Throwable) {
        val report = buildReport(t)
        try {
            CrashReport.save(applicationContext, report)
        } catch (_: Throwable) {
        }
        try {
            showReportScreen(report, allowContinue = false)
        } catch (inner: Throwable) {
            inner.addSuppressed(t)
            throw inner
        }
    }

    private fun buildReport(t: Throwable): String {
        val glVersion = try {
            (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.deviceConfigurationInfo?.glEsVersion ?: "unknown"
        } catch (_: Throwable) {
            "unknown"
        }
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return buildString {
            appendLine("Village Forge ${BuildInfo.VERSION} — error report")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("OpenGL ES: $glVersion")
            appendLine()
            append(sw.toString())
        }
    }

    /**
     * Plain framework Views only (no Compose, no Filament): this screen must
     * render even when the entire rendering stack failed to initialize.
     */
    private fun showReportScreen(report: String, allowContinue: Boolean) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101014.toInt())
            setPadding(pad, pad, pad, pad)
        }
        column.addView(TextView(this).apply {
            text = "Village Forge ${BuildInfo.VERSION} — problem report"
            setTextColor(0xFFFFD54A.toInt())
            textSize = 18f
            setPadding(0, 0, 0, pad / 2)
        })
        column.addView(TextView(this).apply {
            text = report
            setTextColor(0xFFE8E8E8.toInt())
            textSize = 12f
            setTextIsSelectable(true)
        })
        if (allowContinue) {
            column.addView(TextView(this).apply {
                text = "\nThe report above was captured from the previous run."
                setTextColor(0xFF9AA0A6.toInt())
                textSize = 13f
                setPadding(0, pad / 2, 0, pad / 2)
            })
            column.addView(Button(this).apply {
                text = "Continue"
                setOnClickListener {
                    CrashReport.clear(applicationContext)
                    recreate()
                }
            })
        }
        scroll.addView(column)
        setContentView(scroll)
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
