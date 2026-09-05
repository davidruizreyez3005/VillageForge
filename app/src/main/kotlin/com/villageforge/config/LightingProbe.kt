package com.villageforge.config

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.runtime.mutableIntStateOf
import java.io.File
import java.io.FileOutputStream

/**
 * TEMPORARY lighting calibration probe (removed once values are locked in).
 * Cycles a fixed list of lighting presets and shows the active preset index
 * in the HUD so CI screenshots can be matched to a preset unambiguously.
 *
 * v2: additionally saves PixelCopy captures of the SurfaceView itself —
 * screencap() proved to NOT composite the Filament surface on this emulator,
 * so we copy the surface buffer directly for ground truth.
 */
object LightingProbe {
    const val ENABLED = true
    const val STEP_MILLIS = 7_000L
    const val START_DELAY_MILLIS = 10_000L

    /** Shown in the HUD; -1 while no probe is active. */
    val activeIndex = mutableIntStateOf(-1)

    /** Set by GameScreen so the probe can PixelCopy the render surface. */
    var surfaceView: SurfaceView? = null

    lateinit var outputDir: File

    /**
     * Copies the current SurfaceView content into probe_<label>.png.
     * Returns true on success. PixelCopy needs API 26+.
     */
    fun captureSurface(label: String): Boolean {
        val sv = surfaceView ?: return false
        if (Build.VERSION.SDK_INT < 26 || sv.width == 0 || sv.height == 0) return false
        return try {
            val bmp = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
            val done = java.util.concurrent.CountDownLatch(1)
            var ok = false
            PixelCopy.request(sv, bmp, { result ->
                ok = result == PixelCopy.SUCCESS
                done.countDown()
            }, Handler(Looper.getMainLooper()))
            done.await(2, java.util.concurrent.TimeUnit.SECONDS)
            if (ok) {
                File(outputDir, "surface_$label.png").outputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            ok
        } catch (t: Throwable) {
            false
        }
    }

    data class Preset(
        val label: String,
        val sunLux: Float,
        val ambientLux: Float,
        val aperture: Float,
        val shutter: Float,
        val iso: Float,
        val linearToneMapping: Boolean,
        val skyR: Float, val skyG: Float, val skyB: Float,
    )

    val presets = listOf(
        // 1: shipped v1.3 baseline
        Preset("BASE", 11_000f, 3_000f, 16f, 1f / 125f, 100f, false, 0.52f, 0.68f, 0.84f),
        // 2-4: exposure ladder, 2/4/6 stops darker
        Preset("EXP-2", 11_000f, 3_000f, 16f, 1f / 500f, 100f, false, 0.52f, 0.68f, 0.84f),
        Preset("EXP-4", 11_000f, 3_000f, 16f, 1f / 2000f, 100f, false, 0.52f, 0.68f, 0.84f),
        Preset("EXP-6", 11_000f, 3_000f, 16f, 1f / 8000f, 100f, false, 0.52f, 0.68f, 0.84f),
        // 5: linear tonemapping diagnostic
        Preset("LINEAR", 11_000f, 3_000f, 16f, 1f / 125f, 100f, true, 0.52f, 0.68f, 0.84f),
        // 6: lights OFF + pure-green skybox -> isolates the background pipeline
        Preset("GREENSKY", 0.01f, 0.01f, 16f, 1f / 125f, 100f, false, 0f, 1f, 0f),
        // 7: green skybox with normal lights -> green must persist if bg path is stable
        Preset("GREENSKY2", 11_000f, 3_000f, 16f, 1f / 125f, 100f, false, 0f, 1f, 0f),
        // 8: lights OFF + blue sky -> what remains when nothing is lit
        Preset("DARKSKY", 0.01f, 0.01f, 16f, 1f / 125f, 100f, false, 0.52f, 0.68f, 0.84f),
        // 9: everything normal but a RED skybox -> sky must turn red if visible
        Preset("REDSKY", 11_000f, 3_000f, 16f, 1f / 125f, 100f, false, 1f, 0f, 0f),
    )
}
