package com.villageforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.villageforge.config.BuildInfo
import kotlin.math.roundToInt
import kotlin.math.sin

enum class UiPhase { TITLE, LOADING, GAME }

/** Live world behind a warm scrim: the title menu floats over the valley. */
@Composable
fun TitleScreen(hasSave: Boolean, onPlay: () -> Unit, onReset: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(TITLE_SCRIM))
    ) {
        Embers()
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
        ) {
            BasicText(
                "VILLAGE",
                style = TextStyle(color = Color(HEADER_COLOR), fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp),
            )
            BasicText(
                "FORGE",
                style = TextStyle(color = Color(EMBER_COLOR), fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = 10.sp),
            )
            Spacer(Modifier.height(10.dp))
            BasicText(
                "Mine. Smelt. Forge. Trade.",
                style = TextStyle(color = Color(TEXT_COLOR), fontSize = 15.sp, letterSpacing = 1.sp),
            )
            Spacer(Modifier.height(36.dp))
            PlayButton(if (hasSave) "Continue" else "Play", Modifier.align(Alignment.CenterHorizontally), onPlay)
            if (hasSave) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { onReset() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    BasicText(
                        "Reset save",
                        style = TextStyle(color = Color(DIM_COLOR), fontSize = 13.sp),
                    )
                }
            }
        }
        BasicText(
            "v${BuildInfo.VERSION}",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(14.dp),
            style = TextStyle(color = Color(DIM_COLOR), fontSize = 12.sp),
        )
    }
}

@Composable
private fun PlayButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(Color(EMBER_COLOR), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 52.dp, vertical = 14.dp)
    ) {
        BasicText(
            label,
            style = TextStyle(color = Color(0xFF241505), fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
        )
    }
}

/** Slow rising ember sparks dotting the menu. */
@Composable
private fun Embers() {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time = (now - start) * 1e-9f
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        repeat(EMBER_COUNT) { i ->
            val phase = (i * 0.61803398875f) % 1f
            val xFrac = 0.08f + 0.84f * ((i * 0.37777f) % 1f)
            val speed = 0.03f + 0.02f * ((i * 0.2317f) % 1f)
            val frac = (phase + time * speed) % 1f
            val sway = sin((time * 0.8f + i * 2.1f)) * 14f
            val alpha = (1f - frac).coerceIn(0f, 1f) * (0.25f + 0.5f * ((i * 0.531f) % 1f))
            Box(
                Modifier
                    .offset { IntOffset((xFrac * widthPx + sway).roundToInt(), ((1f - frac) * heightPx).roundToInt()) }
                    .graphicsLayer { this.alpha = alpha }
                    .size(EMBER_SIZES[i % EMBER_SIZES.size])
                    .background(Color(EMBER_COLOR), CircleShape)
            )
        }
    }
}

/** Shown while the first frames of the 3D world are still warming up. */
@Composable
fun LoadingOverlay() {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t = (now - start) * 1e-9f
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF120C07))
    ) {
        Column(Modifier.align(Alignment.Center)) {
            BasicText(
                "VILLAGE FORGE",
                style = TextStyle(color = Color(EMBER_COLOR), fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp),
            )
            Spacer(Modifier.height(18.dp))
            BasicText(
                "Forging the valley...",
                style = TextStyle(color = Color(TEXT_COLOR), fontSize = 14.sp),
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .width(200.dp)
                    .height(6.dp)
                    .background(Color(0xFF3A2A18), RoundedCornerShape(3.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.15f + 0.7f * (0.5f + 0.5f * sin(t * 4f)))
                        .height(6.dp)
                        .background(Color(EMBER_COLOR), RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

private const val EMBER_COUNT = 14
private val EMBER_SIZES = listOf(3.dp, 4.dp, 2.dp, 5.dp, 3.dp)
private val TITLE_SCRIM = 0x99120806.toInt()
private val HEADER_COLOR = 0xFFF0E6D2.toInt()
private val TEXT_COLOR = 0xFFD8CDB8.toInt()
private val DIM_COLOR = 0xFF8A8072.toInt()
private val EMBER_COLOR = 0xFFF0932B.toInt()
