package com.villageforge.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.villageforge.config.Buildings
import com.villageforge.config.DayNight
import com.villageforge.config.LightingProbe
import com.villageforge.config.Ore
import com.villageforge.core.EventBus
import com.villageforge.core.InputManager
import com.villageforge.core.SaveManager
import com.villageforge.core.Sheet
import com.villageforge.graphics.CameraRig
import com.villageforge.graphics.FilamentHost
import com.villageforge.state.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun GameScreen(
    host: FilamentHost,
    input: InputManager,
    game: GameState,
    bus: EventBus,
    rig: CameraRig,
    save: SaveManager,
    phase: UiPhase,
    onPhaseChange: (UiPhase) -> Unit,
    onReset: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView(context).apply {
                    LightingProbe.surfaceView = this
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) { host.onSurfaceAvailable(holder.surface) }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { host.onSurfaceChanged(width, height) }
                        override fun surfaceDestroyed(holder: SurfaceHolder) { host.onSurfaceDestroyed() }
                    })
                    setOnTouchListener(input)
                }
            },
        )
        when (phase) {
            UiPhase.TITLE -> TitleScreen(save.hasSave(), onPlay = { onPhaseChange(UiPhase.LOADING) }, onReset = onReset)
            UiPhase.LOADING -> {
                LoadingOverlay()
                LaunchedEffect(host) {
                    val startedAt = System.nanoTime()
                    while (true) {
                        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                        if (host.firstFrameRendered.value && elapsedMs >= 500) break
                        if (elapsedMs >= 8000) break
                        delay(100)
                    }
                    onPhaseChange(UiPhase.GAME)
                }
            }
            UiPhase.GAME -> HudScreen(game, bus, rig, save)
        }
    }
}

@Composable
fun HudScreen(game: GameState, bus: EventBus, rig: CameraRig, save: SaveManager) {
    val carry by game.carryFlow.collectAsState()
    val stock by game.stockpileFlow.collectAsState()
    val coins by game.coinsFlow.collectAsState()
    val binOwned by game.binFlow.collectAsState()
    val upgrades by game.upgradeFlow.collectAsState()
    val soundOn by game.sfxFlow.collectAsState()
    val ingots by game.ingotFlow.collectAsState()
    val items by game.itemFlow.collectAsState()
    val forge by game.forgeFlow.collectAsState()
    val quest by game.questFlow.collectAsState()
    val level by game.levelFlow.collectAsState()
    val miners by game.minerFlow.collectAsState()
    val time by game.timeFlow.collectAsState()
    val saveBroken by save.persistenceWarning.collectAsState()

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var offlineReport by remember { mutableStateOf(game.offlineReport) }

    LaunchedEffect(bus) {
        launch {
            bus.uiRequest.collect { request -> sheet = request.sheet }
        }
    }

    Box(Modifier.fillMaxSize()) {
        TopBar(
            modifier = Modifier.align(Alignment.TopStart),
            coins = coins,
            level = level,
            time = time,
            soundOn = soundOn,
            onToggleSound = { game.enqueue(GameState.Command.ToggleSound) },
        )

        if (saveBroken) {
            BasicText(
                "Saves won't persist",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 56.dp)
                    .background(Color(0xB3E2574C), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = TextStyle(color = Color(0xFFFFF3F0), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
        }

        CarryPanel(carry, stock, binOwned, ingots, items, Modifier.align(Alignment.BottomStart))

        BottomBar(sheet != null, onSelect = { s -> sheet = if (sheet == s) null else s }, Modifier.align(Alignment.BottomCenter))

        if (!binOwned && !forge.furnaceOwned) {
            BuyPanel("Storage Bin", Buildings.BIN_COST, coins,
                { game.enqueue(GameState.Command.BuyBin) }, Modifier.align(Alignment.BottomEnd))
        }

        when (sheet) {
            Sheet.SHOP -> ShopSheet(game, coins, binOwned, upgrades, miners, { sheet = null })
            Sheet.FORGE -> ForgeSheet(game, coins, forge, ingots, items, carry, stock, { sheet = null })
            Sheet.QUESTS -> QuestSheet(
                quest,
                StatsView(
                    oreMined = game.stats.oresMined.sum(),
                    coinsEarned = game.stats.coinsEarnedTotal,
                    ingotsSmelted = game.stats.ingotsSmeltedTotal(),
                    itemsCrafted = game.stats.itemsCraftedTotal(),
                    minersHired = miners.count,
                    playSeconds = game.stats.playSeconds.toInt(),
                ),
                { sheet = null },
            )
            null -> {}
        }

        if (offlineReport != null) {
            OfflineModal(offlineReport!!) {
                game.offlineReport = null
                offlineReport = null
            }
        }

        if (LightingProbe.ENABLED && LightingProbe.activeIndex.intValue >= 0) {
            val preset = LightingProbe.presets[LightingProbe.activeIndex.intValue]
            BasicText(
                "PROBE ${LightingProbe.activeIndex.intValue + 1}/${LightingProbe.presets.size} ${preset.label}",
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = TextStyle(color = Color(0xFFF1C40F), fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
        }

        FloatingTexts(bus, rig)
        TapRipples(bus)
    }
}

// ---- Top bar ---------------------------------------------------------------

@Composable
private fun TopBar(modifier: Modifier, coins: Int, level: GameState.LevelSnapshot, time: Float, soundOn: Boolean, onToggleSound: () -> Unit) {
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .background(Color(UiColors.PANEL), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(11.dp).background(Color(UiColors.COIN), CircleShape))
            Spacer(Modifier.width(7.dp))
            BasicText(
                formatCount(coins),
                style = TextStyle(color = Color(UiColors.HEADER), fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .background(Color(UiColors.PANEL), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "Lv ${level.level}",
                    style = TextStyle(color = Color(UiColors.XP), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.width(6.dp))
                BasicText(
                    "${level.xp}/${level.xpNeeded}",
                    style = TextStyle(color = Color(UiColors.DIM), fontSize = 10.sp),
                )
            }
            Spacer(Modifier.height(3.dp))
            ProgressBar(
                level.xp.toFloat() / level.xpNeeded.coerceAtLeast(1),
                UiColors.XP,
                Modifier.width(86.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        TimeChip(time)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .background(Color(UiColors.PANEL), RoundedCornerShape(10.dp))
                .clickable { onToggleSound() }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            BasicText(if (soundOn) "🔊" else "🔇", style = TextStyle(fontSize = 15.sp))
        }
    }
}

@Composable
private fun TimeChip(t: Float) {
    val (label, color) = dayPhase(t)
    Row(
        Modifier
            .background(Color(UiColors.PANEL), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).background(Color(color), CircleShape))
        Spacer(Modifier.width(6.dp))
        BasicText(label, style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

private fun dayPhase(t: Float): Pair<String, Int> = when {
    t < DayNight.DAWN_END -> "Dawn" to 0xFFF5B041.toInt()
    t < DayNight.DAY_END -> "Day" to 0xFF7DC8E8.toInt()
    t < DayNight.DUSK_END -> "Dusk" to 0xFFE8735A.toInt()
    else -> "Night" to 0xFF8FA0C8.toInt()
}

private fun formatCount(value: Int): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}.${(value % 1_000_000) / 100_000}M c"
    value >= 10_000 -> "${value / 1000}k c"
    else -> "$value c"
}

// ---- Bottom action bar -------------------------------------------------------

@Composable
private fun BottomBar(anyOpen: Boolean, onSelect: (Sheet) -> Unit, modifier: Modifier) {
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = 10.dp),
    ) {
        BarButton("🔨", "Forge", anyOpen) { onSelect(Sheet.FORGE) }
        Spacer(Modifier.width(8.dp))
        BarButton("🛒", "Shop", anyOpen) { onSelect(Sheet.SHOP) }
        Spacer(Modifier.width(8.dp))
        BarButton("📜", "Quests", anyOpen) { onSelect(Sheet.QUESTS) }
    }
}

@Composable
private fun BarButton(glyph: String, label: String, dim: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .background(Color(if (dim) UiColors.PANEL else UiColors.PANEL_RAISED), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(glyph, style = TextStyle(fontSize = 18.sp))
        BasicText(
            label,
            style = TextStyle(color = Color(UiColors.HEADER), fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
    }
}

// ---- Carry panel ---------------------------------------------------------

@Composable
private fun BuyPanel(label: String, cost: Int, coins: Int, onBuy: () -> Unit, modifier: Modifier) {
    val affordable = coins >= cost
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
            .background(Color(UiColors.PANEL), RoundedCornerShape(10.dp))
            .clickable(enabled = affordable) { onBuy() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column {
            BasicText(label, style = TextStyle(color = Color(UiColors.HEADER), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            BasicText(
                if (affordable) "Buy · ${cost} c" else "Need ${cost} c",
                style = TextStyle(color = Color(if (affordable) UiColors.COIN_TEXT else UiColors.DIM), fontSize = 13.sp),
            )
        }
    }
}

@Composable
private fun CarryPanel(
    carry: GameState.CarrySnapshot,
    stock: GameState.StockpileSnapshot,
    binOwned: Boolean,
    ingots: GameState.IngotSnapshot,
    items: GameState.ItemSnapshot,
    modifier: Modifier,
) {
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
            .background(Color(UiColors.PANEL), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicText(
            "Carrying ${carry.total}/${carry.capacity}",
            style = TextStyle(color = Color(UiColors.HEADER), fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(4.dp))
        ProgressBar(carry.total.toFloat() / carry.capacity.coerceAtLeast(1), UiColors.COIN)
        Spacer(Modifier.height(6.dp))
        OreRows(carry.oreCounts)
        if (ingots.total > 0 || items.total > 0) {
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "Ingots ×${ingots.total}",
                    style = TextStyle(color = Color(UiColors.COIN_TEXT), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.width(10.dp))
                BasicText(
                    "Goods ×${items.total}",
                    style = TextStyle(color = Color(UiColors.EMBER), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        if (binOwned) {
            Spacer(Modifier.height(6.dp))
            BasicText(
                "Stockpile ${stock.total}",
                style = TextStyle(color = Color(UiColors.HEADER), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            StockOreRows(stock.oreCounts)
        }
    }
}

@Composable
private fun OreRows(counts: List<Int>) {
    for (ore in Ore.entries) {
        val count = counts[ore.ordinal]
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Box(Modifier.size(8.dp).background(Color(oreColor(ore)), CircleShape))
            Spacer(Modifier.width(6.dp))
            BasicText(
                "${oreName(ore)} ×$count",
                style = TextStyle(
                    color = if (count > 0) Color(UiColors.TEXT) else Color(UiColors.DIM),
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

@Composable
private fun StockOreRows(counts: List<Int>) {
    // Only rows that actually hold something — the full list lives in the HUD already.
    for (ore in Ore.entries) {
        val count = counts[ore.ordinal]
        if (count <= 0) continue
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Box(Modifier.size(8.dp).background(Color(oreColor(ore)), CircleShape))
            Spacer(Modifier.width(6.dp))
            BasicText(
                "${oreName(ore)} ×$count",
                style = TextStyle(color = Color(UiColors.TEXT), fontSize = 12.sp),
            )
        }
    }
}

// ---- Floating combat text --------------------------------------------------

@Composable
private fun FloatingTexts(bus: EventBus, rig: CameraRig) {
    val items = remember { mutableStateListOf<FloatingItem>() }
    val nextId = remember { longArrayOf(0L) }

    LaunchedEffect(bus, rig) {
        launch {
            bus.oreMined.collect { event ->
                val screen = rig.projectToScreen(event.x, event.z)
                addFloating(items, FloatingItem(nextId[0]++, "+${event.amount} ${oreName(event.ore)}", oreColor(event.ore), screen[0], screen[1], 0f))
            }
        }
        launch {
            bus.notices.collect { event ->
                val screen = rig.projectToScreen(event.x, event.z)
                addFloating(items, FloatingItem(nextId[0]++, event.text, event.colorArgb, screen[0], screen[1], event.yOffset))
            }
        }
    }

    items.forEach { item -> FloatingText(item) { items.removeAll { it.id == item.id } } }
}

@Composable
private fun FloatingText(item: FloatingItem, onExpired: () -> Unit) {
    var life by remember(item.id) { mutableFloatStateOf(1f) }
    LaunchedEffect(item.id) {
        val start = withFrameNanos { it }
        while (life > 0f) {
            val now = withFrameNanos { it }
            life = 1f - (now - start) / FLOAT_LIFETIME_NANOS
        }
        onExpired()
    }
    BasicText(
        item.text,
        modifier = Modifier
            .graphicsLayer { alpha = life.coerceIn(0f, 1f) }
            .offset {
                IntOffset(
                    (item.x - 30.dp.toPx()).roundToInt(),
                    (item.y - 18.dp.toPx() - item.yOffsetDp.dp.toPx() - (1f - life.coerceIn(0f, 1f)) * 64.dp.toPx()).roundToInt(),
                )
            },
        style = TextStyle(color = Color(item.colorArgb), fontSize = 15.sp, fontWeight = FontWeight.Bold),
    )
}

private fun addFloating(items: MutableList<FloatingItem>, item: FloatingItem) {
    if (items.size >= 8) items.removeAt(0)
    items.add(item)
}

private data class FloatingItem(
    val id: Long, val text: String, val colorArgb: Int, val x: Float, val y: Float, val yOffsetDp: Float,
)

// ---- Tap ripple --------------------------------------------------------------

@Composable
private fun TapRipples(bus: EventBus) {
    val items = remember { mutableStateListOf<RippleItem>() }
    val nextId = remember { longArrayOf(0L) }

    LaunchedEffect(bus) {
        launch {
            bus.tapMarker.collect { marker ->
                if (items.size >= 4) items.removeAt(0)
                items.add(RippleItem(nextId[0]++, marker.x, marker.y))
            }
        }
    }
    items.forEach { item -> Ripple(item) { items.removeAll { it.id == item.id } } }
}

@Composable
private fun Ripple(item: RippleItem, onExpired: () -> Unit) {
    var life by remember(item.id) { mutableFloatStateOf(1f) }
    LaunchedEffect(item.id) {
        val start = withFrameNanos { it }
        while (life > 0f) {
            val now = withFrameNanos { it }
            life = 1f - (now - start) / RIPPLE_LIFETIME_NANOS
        }
        onExpired()
    }
    val t = life.coerceIn(0f, 1f)
    Box(
        Modifier
            .offset { IntOffset((item.x - 14.dp.toPx()).roundToInt(), (item.y - 14.dp.toPx()).roundToInt()) }
            .graphicsLayer {
                alpha = t
                scaleX = 1f + (1f - t) * 2.2f
                scaleY = 1f + (1f - t) * 2.2f
            }
            .size(28.dp)
            .border(2.dp, Color(UiColors.EMBER), CircleShape)
    )
}

private data class RippleItem(val id: Long, val x: Float, val y: Float)

private const val FLOAT_LIFETIME_NANOS = 900_000_000L
private const val RIPPLE_LIFETIME_NANOS = 450_000_000L
