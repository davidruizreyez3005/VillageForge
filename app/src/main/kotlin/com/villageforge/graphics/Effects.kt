package com.villageforge.graphics

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.TransformManager
import com.villageforge.config.Theme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pooled box particles: rock debris (tinted per rock), anvil sparks (bright
 * emissive), and furnace smoke puffs (unlit translucent, one material
 * instance each so alpha animates independently). Inactive particles are
 * shrunk to a zero-scale transform instead of removed from the scene.
 */
class Effects(
    private val engine: Engine,
    private val scene: Scene,
    assets: AssetFactory,
) {
    private val transformManager: TransformManager = engine.transformManager
    private val renderableManager: RenderableManager = engine.renderableManager
    private val factory: AssetFactory = assets

    private class Particle(val entity: Int, val fixedInstance: MaterialInstance?) {
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
        var life = 0f; var maxLife = 1f
        var baseScale = 0.1f
        var active = false
    }

    private val sparkInstance = factory.material(
        Theme.FURNACE_EMBER, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT,
        emissive = Theme.FURNACE_EMBER, emissiveStrength = 5f,
    )
    private val boxMesh = factory.box

    private val debris = ArrayList<Particle>()
    private val sparks = ArrayList<Particle>()
    private val smoke = ArrayList<Particle>()
    private val rng = java.util.Random(2027)
    private val scratch = FloatArray(16)

    init {
        for (i in 0 until DEBRIS_COUNT) debris.add(Particle(createParticle(sparkInstance), null))
        for (i in 0 until SPARK_COUNT) sparks.add(Particle(createParticle(sparkInstance), null))
        for (i in 0 until SMOKE_COUNT) {
            val instance = factory.smokeInstance()
            smoke.add(Particle(createParticle(instance), instance))
        }
    }

    private fun createParticle(instance: MaterialInstance): Int {
        val identity = Transforms.trs(0f, -10f, 0f, 0.001f, 0.001f, 0.001f)
        return factory.addRenderable(scene, boxMesh, instance, identity)
    }

    /** Chunks fly out of a struck rock with the rock's own tint. */
    fun rockBurst(x: Float, y: Float, z: Float, tint: MaterialInstance) {
        var spawned = 0
        for (p in debris) {
            if (spawned >= 5) break
            if (p.active) continue
            spawnChunk(p, x, y + 0.55f, z, 0.5f)
            renderableManager.setMaterialInstanceAt(renderableManager.getInstance(p.entity), 0, tint)
            spawned++
        }
    }

    /** Bright embers popping off the anvil while hammering. */
    fun sparks(x: Float, y: Float, z: Float) {
        var spawned = 0
        for (p in sparks) {
            if (spawned >= 7) break
            if (p.active) continue
            spawnChunk(p, x, y + 0.85f, z, 1.4f)
            spawned++
        }
    }

    /** One slow translucent puff from the chimney. */
    fun smoke(x: Float, y: Float, z: Float) {
        for (p in smoke) {
            if (p.active) continue
            p.active = true
            p.x = x; p.y = y; p.z = z
            p.vx = (rng.nextFloat() - 0.5f) * 0.25f
            p.vy = 0.55f + rng.nextFloat() * 0.25f
            p.vz = (rng.nextFloat() - 0.5f) * 0.25f
            p.maxLife = 2.4f
            p.life = p.maxLife
            p.baseScale = 0.28f
            return
        }
    }

    private fun spawnChunk(p: Particle, x: Float, y: Float, z: Float, power: Float) {
        p.active = true
        p.x = x; p.y = y; p.z = z
        val angle = rng.nextFloat() * 6.2831853f
        val speed = 1.2f + rng.nextFloat() * 1.6f
        p.vx = cos(angle) * speed * 0.6f * power
        p.vz = sin(angle) * speed * 0.6f * power
        p.vy = (1.6f + rng.nextFloat() * 1.4f) * power
        p.maxLife = 0.55f + rng.nextFloat() * 0.30f
        p.life = p.maxLife
        p.baseScale = 0.08f + rng.nextFloat() * 0.05f
    }

    fun update(dt: Float) {
        for (p in debris) stepChunk(p, dt)
        for (p in sparks) stepChunk(p, dt)
        for (p in smoke) stepSmoke(p, dt)
    }

    private fun stepChunk(p: Particle, dt: Float) {
        if (!p.active) return
        p.life -= dt
        if (p.life <= 0f) { retire(p); return }
        p.vy -= 9f * dt
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.z += p.vz * dt
        val k = (p.life / p.maxLife).coerceIn(0f, 1f)
        val s = p.baseScale * (0.4f + 0.6f * k)
        Transforms.trsInto(scratch, p.x, p.y, p.z, s, s, s, p.life * 220f)
        transformManager.setTransform(transformManager.getInstance(p.entity), scratch)
    }

    private fun stepSmoke(p: Particle, dt: Float) {
        if (!p.active) return
        p.life -= dt
        if (p.life <= 0f) { retire(p); return }
        p.x += p.vx * dt + sin(p.life * 3f) * 0.5f * dt
        p.y += p.vy * dt
        p.z += p.vz * dt
        val k = (p.life / p.maxLife).coerceIn(0f, 1f)
        val s = p.baseScale + (1f - k) * 0.45f
        Transforms.trsInto(scratch, p.x, p.y, p.z, s, s, s, k * 90f)
        transformManager.setTransform(transformManager.getInstance(p.entity), scratch)
        p.fixedInstance?.setParameter("baseColor", Theme.SMOKE.r, Theme.SMOKE.g, Theme.SMOKE.b, 0.45f * k)
    }

    private fun retire(p: Particle) {
        p.active = false
        Transforms.trsInto(scratch, 0f, -10f, 0f, 0.001f, 0.001f, 0.001f, 0f)
        transformManager.setTransform(transformManager.getInstance(p.entity), scratch)
        if (p.fixedInstance != null) {
            p.fixedInstance.setParameter("baseColor", Theme.SMOKE.r, Theme.SMOKE.g, Theme.SMOKE.b, 0f)
        }
    }
}

private const val DEBRIS_COUNT = 24
private const val SPARK_COUNT = 14
private const val SMOKE_COUNT = 8
