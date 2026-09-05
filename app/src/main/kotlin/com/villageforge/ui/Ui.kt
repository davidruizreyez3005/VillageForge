package com.villageforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared palette + small widgets for the HUD and its sheets. */
object UiColors {
    val PANEL = 0xCC26201A.toInt()
    val PANEL_RAISED = 0xFF33291F.toInt()
    val PANEL_SUNK = 0xFF1C1611.toInt()
    val HEADER = 0xFFF0E6D2.toInt()
    val TEXT = 0xFFD8CDB8.toInt()
    val DIM = 0xFF8A8072.toInt()
    val COIN = 0xFFF1C40F.toInt()
    val COIN_TEXT = 0xFFF5D76E.toInt()
    val EMBER = 0xFFF0932B.toInt()
    val XP = 0xFF7DC87D.toInt()
    val XP_TRACK = 0xFF2A2420.toInt()
    val SCRIM = 0x88000000.toInt()
    val CHIP_BG = 0xFF5A4632.toInt()
    val CHIP_DISABLED = 0xFF3A3028.toInt()
    val CHIP_DANGER = 0xFF6E3A2C.toInt()
    val DIVIDER = 0xFF453830.toInt()
    val WARN = 0xFFE2574C.toInt()
    val GOOD = 0xFF9FD08C.toInt()
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun oreName(ore: com.villageforge.config.Ore): String =
    ore.name.lowercase().replaceFirstChar { it.uppercase() }

fun oreColor(ore: com.villageforge.config.Ore): Int = when (ore) {
    com.villageforge.config.Ore.COPPER -> 0xFFE0955C.toInt()
    com.villageforge.config.Ore.TIN -> 0xFFD7DEE6.toInt()
    com.villageforge.config.Ore.COAL -> 0xFFA8ADB3.toInt()
    com.villageforge.config.Ore.IRON -> 0xFFB7C1D4.toInt()
    com.villageforge.config.Ore.SILVER -> 0xFFE8ECF2.toInt()
    com.villageforge.config.Ore.GOLD -> 0xFFF5CF3D.toInt()
    com.villageforge.config.Ore.CRYSTAL -> 0xFF8FE0E8.toInt()
}

fun metalColor(metal: com.villageforge.config.Metal): Int = when (metal) {
    com.villageforge.config.Metal.COPPER_INGOT -> 0xFFE0955C.toInt()
    com.villageforge.config.Metal.TIN_INGOT -> 0xFFD7DEE6.toInt()
    com.villageforge.config.Metal.BRONZE_INGOT -> 0xFFD9A552.toInt()
    com.villageforge.config.Metal.IRON_INGOT -> 0xFFB7C1D4.toInt()
    com.villageforge.config.Metal.SILVER_INGOT -> 0xFFE8ECF2.toInt()
    com.villageforge.config.Metal.GOLD_INGOT -> 0xFFF5CF3D.toInt()
}

/** Bottom sheet shell: dim scrim, rounded panel, title + close, scrollable body. */
@Composable
fun BoxScope.SheetShell(
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(UiColors.SCRIM))
            .clickable { onClose() }
    )
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .background(Color(UiColors.PANEL), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                title,
                style = TextStyle(color = Color(UiColors.HEADER), fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(8.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                BasicText("✕", style = TextStyle(color = Color(UiColors.TEXT), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

/** Thin horizontal progress bar. */
@Composable
fun ProgressBar(fraction: Float, barColor: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color(UiColors.XP_TRACK), RoundedCornerShape(3.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(Color(barColor), RoundedCornerShape(3.dp))
        )
    }
}

/** Pill-shaped buy button. */
@Composable
fun ActionChip(label: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Color(when {
                !enabled -> UiColors.CHIP_DISABLED
                danger -> UiColors.CHIP_DANGER
                else -> UiColors.CHIP_BG
            }), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = Color(if (enabled) UiColors.HEADER else UiColors.DIM),
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
fun UpgradeDivider() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(UiColors.DIVIDER)))
    Spacer(Modifier.height(10.dp))
}
