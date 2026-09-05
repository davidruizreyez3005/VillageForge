package com.villageforge

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.villageforge.config.BuildInfo
import com.villageforge.config.DayNight
import com.villageforge.config.LightingProbe
import com.villageforge.config.WorldLayout
import java.io.File
import com.villageforge.core.AudioManager
import com.villageforge.core.CrashReport
import com.villageforge.core.EventBus
import com.villageforge.core.InputManager
import com.villageforge.core.SaveManager
import com.villageforge.core.SfxId
import com.villageforge.graphics.FilamentHost
import com.villageforge.graphics.WorldRenderer
import com.villageforge.state.GameState
import com.villageforge.systems.AchievementSystem
import com.villageforge.systems.Buildings
import com.villageforge.systems.CommissionSystem
import com.villageforge.systems.Craft
import com.villageforge.systems.DayNightSystem
import com.villageforge.systems.Economy
import com.villageforge.systems.Forge
import com.villageforge.systems.Mining
import com.villageforge.systems.MinerSystem
import com.villageforge.systems.OfflineLogic
import com.villageforge.systems.QuestSystem
import com.villageforge.systems.TownsfolkSystem
import com.villageforge.systems.UpgradeManager
import com.villageforge.systems.VillageSystem
import com.villageforge.systems.WeatherSystem
import com.villageforge.ui.GameScreen
import com.villageforge.ui.UiPhase
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
    private lateinit var forge: Forge
    private lateinit var craft: Craft
    private lateinit var miners: MinerSystem
    private lateinit var quests: QuestSystem
    private lateinit var achievements: AchievementSystem
    private lateinit var commissions: CommissionSystem
    private lateinit var village: VillageSystem
    private lateinit var weather: WeatherSystem
    private lateinit var folks: TownsfolkSystem
    private var simJob: Job? = null
    private var autosaveTicks = 0
    private var startupFailed = false

    private var uiPhase by mutableStateOf(UiPhase.TITLE)

    private val tickNanos = (GameState.TICK_SECONDS * 1e9f).toLong()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashGuard()
        // Fullscreen from the very first frame — including the crash-report
        // screen path that bypasses startApp below.
        runCatching { enterImmersiveMode() }
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

        // A relaunch with a chosen slot (title picker) or a CI autostart both
        // arrive through the intent; otherwise start at the title screen.
        val wantedSlot = intent?.getIntExtra("slot", -1) ?: -1
        val phase = intent?.getStringExtra("phase")
        val loaded = if (wantedSlot >= 0) {
            save.load(game, wantedSlot)
        } else if (phase == "game") {
            val recent = save.mostRecentSlot()
            if (recent >= 0) save.load(game, recent) else false
        } else false

        // Offline progress: hired miners keep digging, the furnace keeps pouring.
        if (loaded && save.lastPlayedEpochMs > 0L) {
            val elapsed = (System.currentTimeMillis() - save.lastPlayedEpochMs) / 1000f
            game.offlineReport = OfflineLogic.apply(game, elapsed)
        }
        // Prime the UI flows with the loaded state before the HUD appears.
        game.publishUi()

        audio = AudioManager()
        audio.enabled = game.sfxEnabled
        audio.music.enabled = game.musicEnabled

        host = FilamentHost()
        world = WorldRenderer(host.engine, game)
        host.bind(world)
        world.onPickUpgraded(game.pickTier)

        mining = Mining(bus)
        economy = Economy(bus)
        buildings = Buildings(bus)
        upgrades = UpgradeManager(bus)
        forge = Forge(bus)
        craft = Craft(bus)
        miners = MinerSystem(bus)
        quests = QuestSystem(bus)
        achievements = AchievementSystem(bus)
        commissions = CommissionSystem(bus)
        village = VillageSystem(bus)
        weather = WeatherSystem(bus)
        folks = TownsfolkSystem(bus)
        // v2.2 — customers of loaded orders walk back in from the road.
        if (loaded) commissions.restoreWalkers(game)
        upgrades.syncBonuses(game)
        input = InputManager(this, world.cameraRig, game, bus)

        // CI / automation fast-path: launch straight into the game world.
        // Slot pickers relaunch with phase=loading to skip the title.
        when (phase) {
            "game", "loading" -> uiPhase = if (phase == "game") UiPhase.GAME else UiPhase.LOADING
        }

        lifecycleScope.launch {
            launch {
                bus.rockStruck.collect {
                    world.onRockStruck(it.rockIndex)
                    audio.play(SfxId.ROCK_HIT, 0.9f + 0.2f * (it.rockIndex % 5) / 4f)
                }
            }
            launch {
                bus.minerStruck.collect { world.onRockStruck(it.rockIndex) }
            }
            launch { bus.oreMined.collect { audio.play(SfxId.ROCK_BREAK) } }
            launch {
                bus.hammerStruck.collect {
                    world.onHammerStruck(it.x, it.z)
                    audio.play(SfxId.HAMMER, 0.95f + 0.05f * (System.nanoTime() % 3))
                }
            }
            launch { bus.smeltDone.collect { audio.play(SfxId.SMELT) } }
            launch { bus.itemCrafted.collect { audio.play(SfxId.CRAFT) } }
            launch {
                bus.levelUp.collect {
                    audio.play(SfxId.LEVELUP)
                    bus.notices.tryEmit(EventBus.Notice("Level ${it.newLevel}!", EventBus.COLOR_GOLD, game.player.x, game.player.z, -52f))
                }
            }
            launch {
                bus.notices.collect {
                    if (it.colorArgb == EventBus.COLOR_WARN) audio.play(SfxId.DENIED)
                }
            }
            launch { bus.sfx.collect { audio.play(it.id, it.pitch) } }
            launch {
                bus.commissionFilled.collect {
                    bus.notices.tryEmit(
                        EventBus.Notice("${it.itemName} delivered · +${it.renown} renown", EventBus.COLOR_INFO, game.player.x, game.player.z, -66f)
                    )
                }
            }
        }

        setContent {
            GameScreen(
                host, input, game, bus, world.cameraRig, save,
                phase = uiPhase,
                onPhaseChange = { uiPhase = it },
                onChooseSlot = { slot -> openSlot(slot) },
                onDeleteSlot = { slot -> save.delete(slot) },
            )
        }

        if (LightingProbe.ENABLED) {
            LightingProbe.outputDir = File(getExternalFilesDir(null), "probe")
            LightingProbe.outputDir.mkdirs()
            lifecycleScope.launch {
                delay(LightingProbe.START_DELAY_MILLIS)
                while (isActive) {
                    for (i in LightingProbe.presets.indices) {
                        val preset = LightingProbe.presets[i]
                        world.applyProbePreset(preset) { linear -> host.setToneMapping(linear) }
                        LightingProbe.activeIndex.intValue = i
                        delay(4_000)
                        LightingProbe.captureSurface("${i + 1}_${preset.label}")
                        delay(LightingProbe.STEP_MILLIS - 4_000)
                    }
                }
            }
        }
    }

    /** Rebuilds the activity with the chosen village slot — clean world, no leaks. */
    private fun openSlot(slot: Int) {
        // Release THIS activity's Filament engine BEFORE launching the next
        // one. The old and new activities briefly coexist during the relaunch
        // and both engines are heavyweight — on low-RAM devices the overlap
        // could stall or kill the process right when the loading screen is
        // showing. Teardown is fully guarded, so late surface callbacks are
        // harmless no-ops.
        if (!startupFailed) {
            runCatching { host.stop() }
            runCatching { world.destroy() }
            runCatching { host.destroy() }
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("slot", slot)
                .putExtra("phase", "loading")
        )
        finish()
    }

    /** True fullscreen: status bar and navigation buttons hidden. */
    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // Swipe from an edge reveals the bars briefly as an overlay.
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The system likes to restore the bars after dialogs / rotation —
        // re-enter fullscreen every time we regain focus.
        if (hasFocus) enterImmersiveMode()
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
        // Belt and suspenders: a failure while releasing GPU resources must
        // never take the process down (it used to crash the app exactly when
        // the slot picker relaunched into the loading screen).
        if (this::world.isInitialized) runCatching { world.destroy() }
        if (this::host.isInitialized) runCatching { host.destroy() }
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
        // The world idles behind the title screen; the sim only runs in-game.
        if (uiPhase != UiPhase.GAME) return
        drainCommands()
        game.player.update(dt)
        miners.update(game, dt)
        mining.update(game, dt)
        forge.update(game, dt)
        craft.update(game, dt)
        economy.update(game)
        buildings.update(game)
        DayNightSystem.update(game, dt)
        weather.update(game, dt)
        commissions.update(game, dt)
        folks.update(game, dt)
        quests.update(game)
        achievements.update(game)
        audio.music.nightFactor = DayNight.nightness(game.timeOfDay)
        game.stats.playSeconds += dt
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
        forge.clearLoadTarget()
        craft.clear(game)
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
        /** Walks via the zone router so cross-zone trips use the trails. */
        fun walkTo(tx: Float, tz: Float) = player.setRoutedTarget(tx, tz)
        while (true) {
            val command = game.drainCommand() ?: break
            when (command) {
                is GameState.Command.MoveTo -> {
                    clearInteractionTargets()
                    player.setRoutedTarget(command.x, command.z)
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
                    walkTo(stand.first, stand.second)
                    mining.setTarget(command.rockIndex)
                }
                is GameState.Command.Sell -> {
                    clearInteractionTargets()
                    val stand = standPoint(player.x, player.z, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z, 1.9f)
                    walkTo(stand.first, stand.second)
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
                    walkTo(stand.first, stand.second)
                    buildings.setDepositTarget()
                }
                is GameState.Command.LoadFurnace -> {
                    clearInteractionTargets()
                    if (forge.requestLoad(game, command.metal)) {
                        val stand = standPoint(player.x, player.z, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z, 1.9f)
                        walkTo(stand.first, stand.second)
                    }
                }
                is GameState.Command.Craft -> {
                    clearInteractionTargets()
                    if (craft.request(game, command.item)) {
                        val stand = standPoint(player.x, player.z, WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z, 1.7f)
                        walkTo(stand.first, stand.second)
                    }
                }
                is GameState.Command.BuyBin -> buildings.tryBuyBin(game)
                is GameState.Command.BuildSlot -> village.tryBuild(game, command.slotIndex)
                is GameState.Command.BuyForge -> buildings.tryBuyForge(game)
                is GameState.Command.BuyPick -> {
                    upgrades.tryBuyPick(game)
                    world.onPickUpgraded(game.pickTier)
                }
                is GameState.Command.BuyBoots -> upgrades.tryBuyBoots(game)
                is GameState.Command.BuyBackpack -> upgrades.tryBuyBackpack(game)
                is GameState.Command.HireMiner -> miners.hire(game)
                is GameState.Command.ToggleSound -> {
                    game.sfxEnabled = !game.sfxEnabled
                    audio.enabled = game.sfxEnabled
                }
                is GameState.Command.ToggleMusic -> {
                    game.musicEnabled = !game.musicEnabled
                    audio.music.enabled = game.musicEnabled
                }
            }
        }
    }

    companion object {
        const val MAX_LAG_TICKS = 5
        const val AUTOSAVE_TICKS = 300 // 30s at 10 Hz
    }
}
