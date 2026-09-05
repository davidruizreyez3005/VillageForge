package com.villageforge.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
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
import com.villageforge.config.LightingProbe
import com.villageforge.config.Ore
import com.villageforge.config.Picks
import com.villageforge.config.Upgrades
import com.villageforge.core.EventBus
import com.villageforge.core.InputManager
import com.villageforge.core.SaveManager
import com.villageforge.graphics.CameraRig
import com.villageforge.graphics.FilamentHost
import com.villageforge.state.GameState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun GameScreen(host: FilamentHost, input: InputManager, game: GameState, bus: EventBus, rig: CameraRig, save: SaveManager) {
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
        HudScreen(game, bus, rig, save)
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
    val saveBroken by save.persistenceWarning.collectAsState()

    var shopOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
        ) {
            CoinsPanel(coins)
            Spacer(Modifier.height(8.dp))
            SheetTab("Shop") { shopOpen = !shopOpen }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                .background(Color(PANEL_COLOR), RoundedCornerShape(10.dp))
                .clickable { game.enqueue(GameState.Command.ToggleSound) }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            BasicText(if (soundOn) "🔊" else "🔇", style = TextStyle(fontSize = 15.sp))
        }
        if (saveBroken) {
            BasicText(
                "Saves won't persist",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 6.dp)
                    .background(Color(WARN_BANNER_COLOR), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = TextStyle(color = Color(0xFFFFF3F0.toInt()), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
        }
        CarryPanel(carry, stock, binOwned, Modifier.align(Alignment.BottomStart))
        if (!binOwned) {
            BuyPanel("Storage Bin", Buildings.BIN_COST, coins,
                { game.enqueue(GameState.Command.BuyBin) }, Modifier.align(Alignment.BottomEnd))
        }
        if (shopOpen) {
            Scrim { shopOpen = false }
            ShopSheet(game, coins, upgrades, { shopOpen = false }, Modifier.align(Alignment.BottomCenter))
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
    }
}

@Composable
private fun CoinsPanel(coins: Int) {
    Row(
        Modifier
            .background(Color(PANEL_COLOR), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(11.dp).background(Color(COIN_COLOR), CircleShape))
        Spacer(Modifier.width(7.dp))
        BasicText("$coins c", style = TextStyle(color = Color(HEADER_COLOR), fontSize = 16.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun SheetTab(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Color(PANEL_COLOR), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        BasicText(label, style = TextStyle(color = Color(HEADER_COLOR), fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun BuyPanel(label: String, cost: Int, coins: Int, onBuy: () -> Unit, modifier: Modifier) {
    val affordable = coins >= cost
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
            .background(Color(PANEL_COLOR), RoundedCornerShape(10.dp))
            .clickable(enabled = affordable) { onBuy() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column {
            BasicText(label, style = TextStyle(color = Color(HEADER_COLOR), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            BasicText(
                if (affordable) "Buy · ${cost} c" else "Need ${cost} c",
                style = TextStyle(color = Color(if (affordable) COIN_TEXT_COLOR else DIM_COLOR), fontSize = 13.sp),
            )
        }
    }
}

@Composable
private fun Scrim(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(SCRIM_COLOR))
            .clickable { onClose() }
    )
}

@Composable
private fun CloseCorner(onClose: () -> Unit) {
    Box(
        Modifier
            .background(Color(PANEL_RAISED), RoundedCornerShape(8.dp))
            .clickable { onClose() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        BasicText("✕", style = TextStyle(color = Color(TEXT_COLOR), fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun ShopSheet(
    game: GameState,
    coins: Int,
    upgrades: GameState.UpgradeSnapshot,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(PANEL_COLOR), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .clickable { }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Upgrades", style = TextStyle(color = Color(HEADER_COLOR), fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            CloseCorner(onClose)
        }
        Spacer(Modifier.height(10.dp))

        val pick = Picks.entries[upgrades.pickTier]
        val nextPick = Picks.entries.getOrNull(upgrades.pickTier + 1)
        UpgradeRow(
            title = "Pickaxe — ${pick.label}",
            detail = if (nextPick == null) "Max tier · ${pick.damage} damage/swing"
            else "Next: ${nextPick.label} · ${nextPick.damage} dmg/swing" +
                if (nextPick.doubleOreChance > 0f) " · +${(nextPick.doubleOreChance * 100).toInt()}% double ore" else "",
            buyLabel = nextPick?.let { "${it.cost} c" },
            canBuy = nextPick != null && coins >= nextPick.cost,
            onBuy = { game.enqueue(GameState.Command.BuyPick) },
        )
        UpgradeDivider()

        val bootsMax = upgrades.bootsLevel >= Upgrades.BOOTS_COSTS.size
        UpgradeRow(
            title = "Boots — Lv ${upgrades.bootsLevel}/${Upgrades.BOOTS_COSTS.size}",
            detail = if (bootsMax) "Max · ${Upgrades.moveSpeed(upgrades.bootsLevel)} speed"
            else "Speed ${Upgrades.moveSpeed(upgrades.bootsLevel)} → ${Upgrades.moveSpeed(upgrades.bootsLevel + 1)}",
            buyLabel = if (bootsMax) null else "${Upgrades.BOOTS_COSTS[upgrades.bootsLevel]} c",
            canBuy = !bootsMax && coins >= Upgrades.BOOTS_COSTS[upgrades.bootsLevel],
            onBuy = { game.enqueue(GameState.Command.BuyBoots) },
        )
        UpgradeDivider()

        val packMax = upgrades.backpackLevel >= Upgrades.BACKPACK_COSTS.size
        UpgradeRow(
            title = "Backpack — Lv ${upgrades.backpackLevel}/${Upgrades.BACKPACK_COSTS.size}",
            detail = if (packMax) "Max · carries ${Upgrades.BACKPACK_CAPACITIES[upgrades.backpackLevel]}"
            else "Carries ${Upgrades.BACKPACK_CAPACITIES[upgrades.backpackLevel]} → ${Upgrades.BACKPACK_CAPACITIES[upgrades.backpackLevel + 1]}",
            buyLabel = if (packMax) null else "${Upgrades.BACKPACK_COSTS[upgrades.backpackLevel]} c",
            canBuy = !packMax && coins >= Upgrades.BACKPACK_COSTS[upgrades.backpackLevel],
            onBuy = { game.enqueue(GameState.Command.BuyBackpack) },
        )
    }
}

@Composable
private fun UpgradeRow(title: String, detail: String, buyLabel: String?, canBuy: Boolean, onBuy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(color = Color(HEADER_COLOR), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            BasicText(detail, style = TextStyle(color = Color(TEXT_COLOR), fontSize = 12.sp))
        }
        if (buyLabel != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .background(Color(if (canBuy) CHIP_BG else CHIP_DISABLED), RoundedCornerShape(8.dp))
                    .clickable(enabled = canBuy) { onBuy() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                BasicText(
                    buyLabel,
                    style = TextStyle(
                        color = Color(if (canBuy) HEADER_COLOR else DIM_COLOR),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun UpgradeDivider() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(DIVIDER_COLOR)))
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun CarryPanel(
    carry: GameState.CarrySnapshot,
    stock: GameState.StockpileSnapshot,
    binOwned: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
            .background(Color(PANEL_COLOR), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicText(
            "Carrying ${carry.total}/${carry.capacity}",
            style = TextStyle(color = Color(HEADER_COLOR), fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
        OreRows(carry.oreCounts)
        if (binOwned) {
            Spacer(Modifier.height(7.dp))
            BasicText("Stockpile", style = TextStyle(color = Color(HEADER_COLOR), fontSize = 13.sp, fontWeight = FontWeight.Bold))
            OreRows(stock.oreCounts)
        }
    }
}

@Composable
private fun OreRows(counts: List<Int>) {
    for (ore in Ore.entries) {
        val count = counts[ore.ordinal]
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
            Box(Modifier.size(9.dp).background(Color(oreColor(ore)), CircleShape))
            Spacer(Modifier.width(6.dp))
            BasicText(
                "${oreName(ore)} ×$count",
                style = TextStyle(
                    color = if (count > 0) Color(TEXT_COLOR) else Color(DIM_COLOR),
                    fontSize = 13.sp,
                ),
            )
        }
    }
}

@Composable
private fun FloatingTexts(bus: EventBus, rig: CameraRig) {
    val items = remember { mutableStateListOf<FloatingItem>() }
    val nextId = remember { longArrayOf(0L) }

    LaunchedEffect(bus, rig) {
        launch {
            bus.oreMined.collect { event ->
                val screen = rig.projectToScreen(event.x, event.z)
                addFloating(items, FloatingItem(nextId[0]++, "+${event.amount} ${oreName(event.ore)}", oreColor(event.ore), screen[0], screen[1]))
            }
        }
        launch {
            bus.notices.collect { event ->
                val screen = rig.projectToScreen(event.x, event.z)
                addFloating(items, FloatingItem(nextId[0]++, event.text, event.colorArgb, screen[0], screen[1]))
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
                    (item.y - 18.dp.toPx() - (1f - life.coerceIn(0f, 1f)) * 64.dp.toPx()).roundToInt(),
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
    val id: Long, val text: String, val colorArgb: Int, val x: Float, val y: Float,
)

private fun oreName(ore: Ore) = ore.name.lowercase().replaceFirstChar { it.uppercase() }

private fun oreColor(ore: Ore): Int = when (ore) {
    Ore.COPPER -> 0xFFE0955C.toInt()
    Ore.TIN -> 0xFFD7DEE6.toInt()
    Ore.COAL -> 0xFFA8ADB3.toInt()
    Ore.IRON -> 0xFFB7C1D4.toInt()
}

private const val FLOAT_LIFETIME_NANOS = 900_000_000L
private val PANEL_COLOR = 0xCC26201A.toInt()
private val PANEL_RAISED = 0xFF33291F.toInt()
private val WARN_BANNER_COLOR = 0xB3E2574C.toInt()
private val HEADER_COLOR = 0xFFF0E6D2.toInt()
private val TEXT_COLOR = 0xFFD8CDB8.toInt()
private val DIM_COLOR = 0xFF706958.toInt()
private val COIN_COLOR = 0xFFF1C40F.toInt()
private val COIN_TEXT_COLOR = 0xFFF5D76E.toInt()
private val SCRIM_COLOR = 0x88000000.toInt()
private val CHIP_BG = 0xFF5A4632.toInt()
private val CHIP_DISABLED = 0xFF3A3028.toInt()
private val DIVIDER_COLOR = 0xFF453830.toInt()
