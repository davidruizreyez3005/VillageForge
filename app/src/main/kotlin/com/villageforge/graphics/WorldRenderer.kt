package com.villageforge.graphics

import com.google.android.filament.*
import com.villageforge.config.DayNight
import com.villageforge.config.LightingProbe
import com.villageforge.config.Theme
import com.villageforge.config.WorldLayout
import com.villageforge.state.GameState
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class WorldRenderer(private val engine: Engine, private val game: GameState) {

    val scene: Scene = engine.createScene()
    private val assets = AssetFactory(engine)
    val cameraRig = CameraRig(engine)
    val camera: Camera get() = cameraRig.camera
    private val playerRig = HumanoidRig(engine, scene, assets, RigStyle.player())
    private val effects = Effects(engine, scene, assets)

    private val sunEntity: Int = EntityManager.get().create()
    private val furnaceLightEntity: Int = EntityManager.get().create()
    private var sunLightInstance = 0
    private var furnaceLightInstance = 0
    private var indirectLight: IndirectLight? = null
    private var sky: Skybox? = null

    private val rockCount = game.rocks.size
    private val rockEntities = IntArray(rockCount)
    private val rockBase = Array(rockCount) { FloatArray(16) }
    private val rockParams = Array(rockCount) { FloatArray(5) }
    private val rockVisible = BooleanArray(rockCount)
    private val rockFlinch = FloatArray(rockCount)
    private val rockMaterialInstances = arrayOfNulls<MaterialInstance>(rockCount)
    private val flinchScratch = FloatArray(16)

    private var binCrateEntity = 0
    private var binLidEntity = 0
    private var binVisible = false

    private val furnaceEntities = ArrayList<Int>()
    private var furnaceVisible = false
    private var mouthInstance: MaterialInstance? = null
    private var lanternInstance: MaterialInstance? = null
    private var smokeTimer = 0f

    // v2.1 — Crystal Hollow.
    private val crystalInstances = ArrayList<MaterialInstance>()
    private var monolithInstance: MaterialInstance? = null
    private val monolithLightEntity: Int = EntityManager.get().create()
    private var monolithLightInstance = 0

    private val minerRigs = ArrayList<HumanoidRig>()
    private var clock = 0f

    init {
        buildLights()
        buildTerrain()
        buildCliffs()
        buildGate()
        buildTrees()
        buildRocks()
        buildTradePost()
        buildBin()
        buildFurnace()
        buildHollow()
        playerRig.setPickTint(game.pickTier)
    }

    // ---- World assembly ----------------------------------------------------

    private fun buildLights() {
        val dir = Theme.SUN_DIRECTION
        val len = sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2])
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(Theme.SUN_COLOR.r, Theme.SUN_COLOR.g, Theme.SUN_COLOR.b)
            .intensity(Theme.SUN_INTENSITY_LUX)
            .direction(dir[0] / len, dir[1] / len, dir[2] / len)
            .castShadows(true)
            .build(engine, sunEntity)
        scene.addEntity(sunEntity)
        sunLightInstance = engine.lightManager.getInstance(sunEntity)

        val sh = FloatArray(27)
        sh[0] = Theme.AMBIENT_SKY.r; sh[1] = Theme.AMBIENT_SKY.g; sh[2] = Theme.AMBIENT_SKY.b
        indirectLight = IndirectLight.Builder().irradiance(3, sh).intensity(3_000f).build(engine)
        scene.indirectLight = indirectLight

        sky = Skybox.Builder().color(Theme.SKY_COLOR.r, Theme.SKY_COLOR.g, Theme.SKY_COLOR.b, 1f).build(engine)
        scene.skybox = sky

        // The forge fire: a warm point light at the furnace mouth.
        LightManager.Builder(LightManager.Type.POINT)
            .color(Theme.FURNACE_EMBER.r, Theme.FURNACE_EMBER.g, Theme.FURNACE_EMBER.b)
            .intensity(1_400f)
            .position(WorldLayout.FURNACE_X, 1.05f, WorldLayout.FURNACE_Z)
            .falloff(9f)
            .build(engine, furnaceLightEntity)
        furnaceLightInstance = engine.lightManager.getInstance(furnaceLightEntity)
    }

    private fun buildTerrain() {
        // terrainParts = 5 valley variants then 3 canyon variants.
        for (t in Theme.GRASS.indices) {
            assets.addRenderable(scene, assets.terrainParts[t],
                assets.material(Theme.GRASS[t], Theme.ROUGHNESS_TERRAIN),
                Transforms.trs(0f, 0f, 0f, 1f, 1f, 1f))
        }
        for (c in Theme.CANYON_GRASS.indices) {
            assets.addRenderable(scene, assets.terrainParts[Theme.GRASS.size + c],
                assets.material(Theme.CANYON_GRASS[c], Theme.ROUGHNESS_TERRAIN),
                Transforms.trs(0f, 0f, 0f, 1f, 1f, 1f))
        }
    }

    private fun addCliffRow(rng: Random, instances: List<MaterialInstance>, x0: Float, x1: Float, z: Float) {
        var x = x0
        while (x < x1) {
            val w = 4f + rng.nextFloat() * 3.5f
            val h = 7f + rng.nextFloat() * 6f
            val d = 4.5f + rng.nextFloat() * 2f
            val yaw = (rng.nextFloat() - 0.5f) * 8f
            assets.addRenderable(scene, assets.box, instances[rng.nextInt(instances.size)],
                Transforms.trs(x + w / 2f, WorldLayout.groundHeight(x, z) - 0.6f, z, w, h, d, yaw))
            x += w - 0.8f
        }
    }

    /** Cliff boxes stacked along a Z run at fixed X (for east/west walls). */
    private fun addCliffColumn(rng: Random, instances: List<MaterialInstance>, z0: Float, z1: Float, x: Float) {
        var z = z0
        while (z < z1) {
            val d = 4f + rng.nextFloat() * 3f
            val h = 7f + rng.nextFloat() * 6f
            val w = 4.5f + rng.nextFloat() * 2f
            val yaw = (rng.nextFloat() - 0.5f) * 8f
            assets.addRenderable(scene, assets.box, instances[rng.nextInt(instances.size)],
                Transforms.trs(x, WorldLayout.groundHeight(x, z) - 0.6f, z + d / 2f, w, h, d, yaw))
            z += d - 0.8f
        }
    }

    private fun buildCliffs() {
        val instances = Theme.CLIFF.map { assets.material(it, Theme.ROUGHNESS_PROP) }
        val rng = Random(99)
        val half = WorldLayout.VALLEY_WIDTH / 2f
        // Far wall behind the north canyon and the hollow.
        addCliffRow(rng, instances, WorldLayout.HOLLOW_X_MIN - 2.5f, half + 2f, WorldLayout.CANYON_Z_MIN - 2.2f)
        // Valley north rim, leaving the canyon pass open around |x| < 8.5.
        addCliffRow(rng, instances, -half - 2f, -8.5f, WorldLayout.VALLEY_Z_MIN - 1.5f)
        addCliffRow(rng, instances, 8.5f, half + 2f, WorldLayout.VALLEY_Z_MIN - 1.5f)
        // Hollow rims: south lip and the sheer west wall.
        addCliffRow(rng, instances, WorldLayout.HOLLOW_X_MIN - 1.5f, WorldLayout.HOLLOW_X_MAX - 1f, WorldLayout.HOLLOW_Z_MAX + 0.5f)
        addCliffColumn(rng, instances, WorldLayout.HOLLOW_Z_MIN + 1f, WorldLayout.HOLLOW_Z_MAX + 1f, WorldLayout.HOLLOW_X_MIN - 1.5f)
    }

    private fun buildGate() {
        val wood = assets.material(Theme.GATE_WOOD, Theme.ROUGHNESS_PROP)
        val trim = assets.material(Theme.STALL_TRIM, Theme.ROUGHNESS_PROP)
        lanternInstance = assets.material(Theme.LANTERN_GLOW, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT, Theme.LANTERN_GLOW, 0.05f)
        val z = WorldLayout.GATE_Z
        val y = WorldLayout.groundHeight(0f, z)
        for (dx in floatArrayOf(-6.2f, 6.2f)) {
            assets.addRenderable(scene, assets.box, wood, Transforms.trs(dx, y, z, 0.5f, 3.6f, 0.5f))
        }
        assets.addRenderable(scene, assets.box, wood, Transforms.trs(0f, y + 3.5f, z, 13.2f, 0.4f, 0.5f))
        // Hanging sign board.
        assets.addRenderable(scene, assets.box, trim, Transforms.trs(0f, y + 2.7f, z + 0.1f, 3.4f, 0.85f, 0.12f))
        // Torch flames on both posts.
        for (dx in floatArrayOf(-6.2f, 6.2f)) {
            assets.addRenderable(scene, assets.box, lanternInstance!!, Transforms.trs(dx, y + 3.95f, z, 0.22f, 0.3f, 0.22f))
        }
    }

    private fun buildTrees() {
        val bark = assets.material(Theme.BARK, Theme.ROUGHNESS_PROP)
        val canopies = Theme.CANOPY.map { assets.material(it, Theme.ROUGHNESS_PROP) }
        val rng = Random(2024)
        for ((x, z) in WorldLayout.trees) {
            val yaw = rng.nextFloat() * 360f
            val scale = 0.85f + rng.nextFloat() * 0.5f
            val trunkHeight = 1.4f + rng.nextFloat() * 0.8f
            val y = WorldLayout.groundHeight(x, z) - 0.1f
            assets.addRenderable(scene, assets.box, bark,
                Transforms.trs(x, y, z, 0.55f * scale, trunkHeight, 0.55f * scale, yaw))
            val c1 = 2.1f * scale
            assets.addRenderable(scene, assets.box, canopies[rng.nextInt(canopies.size)],
                Transforms.trs(x, y + trunkHeight - 0.15f, z, c1, c1 * 0.85f, c1, yaw + 3f))
            val c2 = 1.2f * scale
            assets.addRenderable(scene, assets.box, canopies[rng.nextInt(canopies.size)],
                Transforms.trs(x, y + trunkHeight + c1 * 0.85f - 0.35f, z, c2, c2, c2, yaw + 9f))
        }
        // Sparse dark pines inside the canyon.
        val pineCanopies = Theme.PINE_CANOPY.map { assets.material(it, Theme.ROUGHNESS_PROP) }
        for ((x, z) in WorldLayout.canyonPines) {
            val yaw = rng.nextFloat() * 360f
            val scale = 0.8f + rng.nextFloat() * 0.4f
            val y = WorldLayout.groundHeight(x, z) - 0.1f
            val trunk = 1.9f * scale
            assets.addRenderable(scene, assets.box, bark, Transforms.trs(x, y, z, 0.4f * scale, trunk, 0.4f * scale, yaw))
            var cy = y + trunk - 0.2f
            var c = 2.0f * scale
            for (layer in 0 until 3) {
                assets.addRenderable(scene, assets.box, pineCanopies[rng.nextInt(pineCanopies.size)],
                    Transforms.trs(x, cy, z, c, c * 0.7f, c, yaw + layer * 25f))
                cy += c * 0.45f
                c *= 0.68f
            }
        }
    }

    private fun buildRocks() {
        val rng = Random(7)
        for (i in 0 until rockCount) {
            val rock = game.rocks[i]
            val mesh = assets.rockVariants[rng.nextInt(assets.rockVariants.size)]
            val yaw = rng.nextFloat() * 360f
            val scale = rock.ore.rockScale * (0.9f + rng.nextFloat() * 0.35f)
            val y = WorldLayout.groundHeight(rock.x, rock.z) - 0.15f
            rockParams[i] = floatArrayOf(rock.x, y, rock.z, scale, yaw)
            rockBase[i] = Transforms.trs(rock.x, y, rock.z, scale, scale, scale, yaw)
            rockVisible[i] = rock.alive
            val instance = assets.material(
                rock.ore.rockTint, Theme.ROUGHNESS_ORE, Theme.METALLIC_ORE,
                emissive = rock.ore.rockTint, emissiveStrength = rock.ore.rockGlow,
            )
            rockMaterialInstances[i] = instance
            rockEntities[i] = assets.addRenderable(scene, mesh, instance, rockBase[i])
            if (!rockVisible[i]) scene.removeEntity(rockEntities[i])
        }
    }

    private fun buildTradePost() {
        val x = WorldLayout.TRADE_POST_X
        val z = WorldLayout.TRADE_POST_Z
        val y = WorldLayout.groundHeight(x, z)
        val wood = assets.material(Theme.STALL_WOOD, Theme.ROUGHNESS_PROP)
        val trim = assets.material(Theme.STALL_TRIM, Theme.ROUGHNESS_PROP)
        val awning = assets.material(Theme.STALL_AWNING, Theme.ROUGHNESS_PROP)

        assets.addRenderable(scene, assets.box, wood, Transforms.trs(x, y, z, 2.0f, 0.95f, 0.9f))
        assets.addRenderable(scene, assets.box, trim, Transforms.trs(x, y + 0.95f, z, 2.3f, 0.10f, 1.15f))
        for (dx in floatArrayOf(-1.05f, 1.05f)) {
            for (dz in floatArrayOf(-0.6f, 0.6f)) {
                assets.addRenderable(scene, assets.box, wood, Transforms.trs(x + dx, y, z + dz, 0.14f, 2.4f, 0.14f))
            }
        }
        assets.addRenderable(scene, assets.box, awning, Transforms.trs(x, y + 2.45f, z - 0.15f, 2.7f, 0.14f, 1.7f))
        assets.addRenderable(scene, assets.box, trim, Transforms.trs(x, y + 2.45f, z + 0.55f, 2.7f, 0.14f, 0.28f))

        if (lanternInstance == null) {
            lanternInstance = assets.material(Theme.LANTERN_GLOW, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT, Theme.LANTERN_GLOW, 0.05f)
        }
        for (dx in floatArrayOf(-1.75f, 1.75f)) {
            val lz = z + 1.3f
            assets.addRenderable(scene, assets.box, wood, Transforms.trs(x + dx, y, lz, 0.13f, 2.1f, 0.13f))
            assets.addRenderable(scene, assets.box, lanternInstance!!, Transforms.trs(x + dx, y + 2.25f, lz, 0.24f, 0.32f, 0.24f))
        }
    }

    private fun buildBin() {
        val x = WorldLayout.BIN_X
        val z = WorldLayout.BIN_Z
        val y = WorldLayout.groundHeight(x, z) - 0.05f
        val crate = assets.material(Theme.BIN_WOOD, Theme.ROUGHNESS_PROP)
        val lid = assets.material(Theme.BIN_LID, Theme.ROUGHNESS_PROP)

        binCrateEntity = assets.addRenderable(scene, assets.box, crate,
            Transforms.trs(x, y, z, 1.15f, 0.85f, 1.15f, 8f))
        binLidEntity = assets.addRenderable(scene, assets.box, lid,
            Transforms.trs(x, y + 0.85f, z, 1.3f, 0.12f, 1.3f, -4f))

        binVisible = game.binOwned
        if (!binVisible) { scene.removeEntity(binCrateEntity); scene.removeEntity(binLidEntity) }
    }

    private fun buildFurnace() {
        val fx = WorldLayout.FURNACE_X
        val fz = WorldLayout.FURNACE_Z
        val gy = WorldLayout.groundHeight(fx, fz)
        val stone = assets.material(Theme.FURNACE_STONE, Theme.ROUGHNESS_PROP)
        val stoneDark = assets.material(Theme.FURNACE_STONE_DARK, Theme.ROUGHNESS_PROP)
        val anvil = assets.material(Theme.ANVIL, Theme.ROUGHNESS_METAL, Theme.METALLIC_INGOT)
        val stump = assets.material(Theme.ANVIL_STUMP, Theme.ROUGHNESS_PROP)
        val coal = assets.material(Theme.COAL_PILE, Theme.ROUGHNESS_ORE)
        mouthInstance = assets.material(
            Theme.FURNACE_EMBER, Theme.ROUGHNESS_ORE, Theme.METALLIC_DEFAULT,
            emissive = Theme.FURNACE_EMBER, emissiveStrength = 0.5f,
        )

        // Stone furnace body with a shrinking chimney.
        furnaceEntities.add(assets.addRenderable(scene, assets.box, stone, Transforms.trs(fx, gy, fz, 1.8f, 1.3f, 1.6f)))
        furnaceEntities.add(assets.addRenderable(scene, assets.box, stone, Transforms.trs(fx, gy + 1.3f, fz, 1.4f, 0.7f, 1.2f)))
        furnaceEntities.add(assets.addRenderable(scene, assets.box, stoneDark, Transforms.trs(fx, gy + 2.0f, fz, 0.95f, 1.5f, 0.95f)))
        furnaceEntities.add(assets.addRenderable(scene, assets.box, stoneDark, Transforms.trs(fx, gy + 3.5f, fz, 0.7f, 0.4f, 0.7f)))
        // Glowing mouth on the +z face.
        furnaceEntities.add(assets.addRenderable(scene, assets.box, mouthInstance!!, Transforms.trs(fx, gy + 0.62f, fz + 0.72f, 0.95f, 0.58f, 0.28f)))

        // Anvil on a stump beside it.
        val ax = WorldLayout.ANVIL_X
        val az = WorldLayout.ANVIL_Z
        val ay = WorldLayout.groundHeight(ax, az)
        furnaceEntities.add(assets.addRenderable(scene, assets.box, stump, Transforms.trs(ax, ay, az, 0.55f, 0.5f, 0.55f)))
        furnaceEntities.add(assets.addRenderable(scene, assets.box, anvil, Transforms.trs(ax, ay + 0.5f, az, 0.30f, 0.30f, 0.30f)))
        furnaceEntities.add(assets.addRenderable(scene, assets.box, anvil, Transforms.trs(ax, ay + 0.8f, az, 0.78f, 0.22f, 0.34f)))
        // Coal pile.
        for (i in 0 until 3) {
            furnaceEntities.add(assets.addRenderable(scene, assets.box, coal,
                Transforms.trs(fx + 1.2f, gy + 0.08f, fz - 0.4f + i * 0.35f, 0.55f, 0.28f, 0.45f, i * 30f)))
        }

        furnaceVisible = game.furnaceOwned
        if (!furnaceVisible) {
            for (e in furnaceEntities) scene.removeEntity(e)
            scene.removeEntity(furnaceLightEntity)
        } else {
            scene.addEntity(furnaceLightEntity)
        }
    }

    // ---- Crystal Hollow (v2.1) ----------------------------------------------

    /**
     * The westward side-canyon: glowing crystal clusters, a giant luminous
     * monolith with its own point light, an old timbered mine entrance in the
     * north wall, standing stones, and lantern posts at the link mouth.
     */
    private fun buildHollow() {
        val rng = Random(2121)

        // Emissive crystal clusters: 2-3 tilted shards each, alternating hues.
        for ((cx, cz, scale) in WorldLayout.crystalClusters) {
            val base = WorldLayout.groundHeight(cx, cz)
            val hue = if ((cx * 7f + cz).toInt() % 2 == 0) Theme.CRYSTAL_A else Theme.CRYSTAL_B
            val instance = assets.material(
                hue, Theme.ROUGHNESS_ORE, Theme.METALLIC_ORE,
                emissive = hue, emissiveStrength = 0.35f,
            )
            crystalInstances.add(instance)
            val shards = 2 + (rng.nextInt(2))
            for (s in 0 until shards) {
                val h = (0.7f + rng.nextFloat() * 0.9f) * scale
                val w = 0.28f * scale * (1f - 0.25f * s)
                val yaw = rng.nextFloat() * 90f + 15f
                val ox = (rng.nextFloat() - 0.5f) * 1.1f
                val oz = (rng.nextFloat() - 0.5f) * 1.1f
                assets.addRenderable(scene, assets.box, instance,
                    Transforms.trs(cx + ox, base, cz + oz, w, h, w, yaw))
            }
        }

        // The great crystal monolith: three stacked, rotated prisms + light.
        val mx = WorldLayout.MONOLITH_X
        val mz = WorldLayout.MONOLITH_Z
        val my = WorldLayout.groundHeight(mx, mz)
        monolithInstance = assets.material(
            Theme.MONOLITH, Theme.ROUGHNESS_ORE, Theme.METALLIC_ORE,
            emissive = Theme.MONOLITH, emissiveStrength = 0.6f,
        )
        assets.addRenderable(scene, assets.box, monolithInstance!!, Transforms.trs(mx, my, mz, 1.5f, 3.6f, 1.5f, 18f))
        assets.addRenderable(scene, assets.box, monolithInstance!!, Transforms.trs(mx, my + 3.4f, mz, 1.05f, 2.2f, 1.05f, -12f))
        assets.addRenderable(scene, assets.box, monolithInstance!!, Transforms.trs(mx, my + 5.4f, mz, 0.62f, 1.4f, 0.62f, 40f))
        LightManager.Builder(LightManager.Type.POINT)
            .color(Theme.CRYSTAL_A.r, Theme.CRYSTAL_A.g, Theme.CRYSTAL_A.b)
            .intensity(900f)
            .position(mx, my + 2.4f, mz)
            .falloff(14f)
            .build(engine, monolithLightEntity)
        scene.addEntity(monolithLightEntity)
        monolithLightInstance = engine.lightManager.getInstance(monolithLightEntity)

        // Old mine entrance tucked into the hollow's north wall.
        val ex = WorldLayout.HOLLOW_X_MIN + 8.5f
        val ez = WorldLayout.HOLLOW_Z_MIN + 1.2f
        val ey = WorldLayout.groundHeight(ex, ez)
        val timber = assets.material(Theme.MINE_TIMBER, Theme.ROUGHNESS_PROP)
        val dark = assets.material(Theme.MINE_DARK, Theme.ROUGHNESS_PROP)
        assets.addRenderable(scene, assets.box, dark, Transforms.trs(ex, ey, ez, 3.2f, 2.6f, 1.2f))
        for (dx in floatArrayOf(-1.5f, 1.5f)) {
            assets.addRenderable(scene, assets.box, timber, Transforms.trs(ex + dx, ey, ez, 0.4f, 2.6f, 0.4f))
        }
        assets.addRenderable(scene, assets.box, timber, Transforms.trs(ex, ey + 2.5f, ez, 3.5f, 0.4f, 0.5f))

        // Weathered standing stones.
        val stone = assets.material(Theme.STANDING_STONE, Theme.ROUGHNESS_PROP)
        for ((sx, sz, h) in WorldLayout.standingStones) {
            val sy = WorldLayout.groundHeight(sx, sz)
            val yaw = rng.nextFloat() * 360f
            assets.addRenderable(scene, assets.box, stone, Transforms.trs(sx, sy, sz, 0.7f, h, 0.45f, yaw))
            assets.addRenderable(scene, assets.box, stone, Transforms.trs(sx, sy + h, sz, 0.95f, 0.25f, 0.6f, yaw + 20f))
        }

        // Lantern posts marking the link mouth — the trail into the hollow.
        if (lanternInstance == null) {
            lanternInstance = assets.material(Theme.LANTERN_GLOW, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT, Theme.LANTERN_GLOW, 0.05f)
        }
        val wood = assets.material(Theme.GATE_WOOD, Theme.ROUGHNESS_PROP)
        for (lx in floatArrayOf(-31.5f, -17.5f)) {
            val lz = WorldLayout.LINK_Z_MAX + 2.2f
            val ly = WorldLayout.groundHeight(lx, lz)
            assets.addRenderable(scene, assets.box, wood, Transforms.trs(lx, ly, lz, 0.14f, 2.3f, 0.14f))
            assets.addRenderable(scene, assets.box, lanternInstance!!, Transforms.trs(lx, ly + 2.45f, lz, 0.26f, 0.34f, 0.26f))
        }
    }

    // ---- Per-frame sync ----------------------------------------------------

    fun onViewport(width: Int, height: Int) { cameraRig.setViewport(width, height) }

    fun onRockStruck(index: Int) {
        rockFlinch[index] = 1f
        val rock = game.rocks[index]
        effects.rockBurst(rock.x, WorldLayout.groundHeight(rock.x, rock.z), rock.z, rockMaterialInstances[index]!!)
    }

    fun onHammerStruck(x: Float, z: Float) {
        effects.sparks(x, WorldLayout.groundHeight(x, z), z)
    }

    fun onPickUpgraded(tier: Int) { playerRig.setPickTint(tier) }

    /** TEMPORARY lighting calibration probe. */
    fun applyProbePreset(p: LightingProbe.Preset, toneMapping: (Boolean) -> Unit) {
        engine.lightManager.setIntensity(sunLightInstance, p.sunLux)
        indirectLight?.setIntensity(p.ambientLux)
        cameraRig.setProbeExposure(p.aperture, p.shutter, p.iso)
        toneMapping(p.linearToneMapping)
        sky?.let { engine.destroySkybox(it) }
        sky = Skybox.Builder().color(p.skyR, p.skyG, p.skyB, 1f).build(engine)
        scene.skybox = sky
    }

    fun update(deltaSeconds: Float) {
        clock += deltaSeconds
        cameraRig.update(deltaSeconds)
        syncRocks(deltaSeconds)
        syncBin()
        syncFurnace(deltaSeconds)
        syncMiners(deltaSeconds)
        applyDayNight()
        effects.update(deltaSeconds)
        val alpha = tickAlpha()
        val carryFill = if (game.carryCapacity > 0) game.inventory.total.toFloat() / game.carryCapacity else 0f
        playerRig.update(game.player, alpha, deltaSeconds, carryFill)
    }

    private fun tickAlpha(): Float = if (game.lastTickNanos == 0L) 0f
        else ((System.nanoTime() - game.lastTickNanos) * 1e-9f / GameState.TICK_SECONDS).coerceIn(0f, 1f)

    private fun syncBin() {
        val owned = game.binOwned
        if (owned == binVisible) return
        binVisible = owned
        if (owned) { scene.addEntity(binCrateEntity); scene.addEntity(binLidEntity) }
        else { scene.removeEntity(binCrateEntity); scene.removeEntity(binLidEntity) }
    }

    private fun syncFurnace(dt: Float) {
        val owned = game.furnaceOwned
        if (owned != furnaceVisible) {
            furnaceVisible = owned
            if (owned) {
                for (e in furnaceEntities) scene.addEntity(e)
                scene.addEntity(furnaceLightEntity)
            } else {
                for (e in furnaceEntities) scene.removeEntity(e)
                scene.removeEntity(furnaceLightEntity)
            }
        }

        val smelting = owned && game.smeltQueue.isNotEmpty()
        val night = DayNight.nightness(game.timeOfDay)
        val flicker = 0.85f + 0.15f * sin(clock * 13f) + 0.08f * sin(clock * 29f)

        // Fire light breathes harder at night so the workshop glows.
        if (owned) {
            val intensity = (if (smelting) 1_400f + 5_200f * night else 700f + 1_800f * night) * flicker
            engine.lightManager.setIntensity(furnaceLightInstance, intensity)
        }
        val emberStrength = when {
            !owned -> 0f
            smelting -> 2.2f + 3.0f * night + flicker * 1.6f
            else -> 0.4f + 1.1f * night
        }
        mouthInstance?.setParameter("emissiveStrength", emberStrength)

        if (smelting) {
            smokeTimer -= dt
            if (smokeTimer <= 0f) {
                smokeTimer = 0.45f
                val fy = WorldLayout.groundHeight(WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z)
                effects.smoke(WorldLayout.FURNACE_X, fy + 3.7f, WorldLayout.FURNACE_Z)
            }
        }
    }

    private fun syncMiners(dt: Float) {
        while (minerRigs.size < game.miners.size) {
            val miner = game.miners[minerRigs.size]
            minerRigs.add(HumanoidRig(engine, scene, assets, RigStyle.miner(miner.styleIndex)))
        }
        val alpha = tickAlpha()
        for (i in minerRigs.indices) {
            minerRigs[i].update(game.miners[i].body, alpha, dt, 0f)
        }
    }

    private fun syncRocks(dt: Float) {
        for (i in 0 until rockCount) {
            val alive = game.rocks[i].alive
            if (alive != rockVisible[i]) {
                rockVisible[i] = alive
                if (alive) {
                    engine.transformManager.setTransform(engine.transformManager.getInstance(rockEntities[i]), rockBase[i])
                    scene.addEntity(rockEntities[i])
                } else {
                    scene.removeEntity(rockEntities[i])
                }
            }
            if (rockFlinch[i] > 0f) {
                rockFlinch[i] = max(0f, rockFlinch[i] - dt * 5f)
                if (!alive) rockFlinch[i] = 0f
                else if (rockFlinch[i] > 0f) applyFlinch(i)
                else engine.transformManager.setTransform(engine.transformManager.getInstance(rockEntities[i]), rockBase[i])
            }
        }
    }

    private fun applyFlinch(i: Int) {
        val p = rockParams[i]
        val f = rockFlinch[i]
        val angle = i * 2.39996f
        val ox = cos(angle) * 0.12f * f
        val oz = sin(angle) * 0.12f * f
        val s = p[3] * (1f + 0.10f * f)
        Transforms.trsInto(flinchScratch, p[0] + ox, p[1], p[2] + oz, s, s, s, p[4])
        engine.transformManager.setTransform(engine.transformManager.getInstance(rockEntities[i]), flinchScratch)
    }

    // ---- Day / night -------------------------------------------------------

    /**
     * v2.1 fix: the daylight curve is CONTINUOUS and floored. The old curve
     * snapped from 0.85 to ~0.06 at the dawn/day boundary (the "super dark at
     * dawn" bug) and night dropped to near-black. Now: night holds a bright
     * moonlit floor, dawn/dusk ramp between moonlight and golden hour, and
     * mid-day never dips — the valley stays readable around the clock.
     */
    private fun applyDayNight() {
        val t = game.timeOfDay
        val night = DayNight.nightness(t)
        val daylight: Float
        val dayT: Float
        when {
            t < DayNight.DAWN_END -> {
                val k = smoothStep(t / DayNight.DAWN_END)
                daylight = lerp(DayNight.MOONLIGHT, DayNight.GOLDEN, k)
                dayT = 0f
            }
            t < DayNight.DAY_END -> {
                dayT = (t - DayNight.DAWN_END) / (DayNight.DAY_END - DayNight.DAWN_END)
                daylight = DayNight.GOLDEN + (1f - DayNight.GOLDEN) * sin(Math.PI * dayT).toFloat()
            }
            t < DayNight.DUSK_END -> {
                val k = smoothStep((t - DayNight.DAY_END) / (DayNight.DUSK_END - DayNight.DAY_END))
                daylight = lerp(DayNight.GOLDEN, DayNight.MOONLIGHT, k)
                dayT = 1f
            }
            else -> { daylight = DayNight.MOONLIGHT; dayT = 1f }
        }

        val elevationParam = sin(Math.PI * dayT.toDouble()).toFloat().coerceIn(0f, 1f)
        val horizonWarm = (elevationParam * 3.5f).coerceIn(0f, 1f)

        val sunLux = lerp(DayNight.NIGHT_SUN_LUX, DayNight.DAY_SUN_LUX, daylight)
        val ambientLux = lerp(DayNight.NIGHT_AMBIENT_LUX, DayNight.DAY_AMBIENT_LUX, daylight)

        // Warm horizon sun that eases into cool moonlight without popping.
        val dayColor = mixRgb(DayNight.DUSK_SUN, DayNight.DAY_SUN, horizonWarm)
        val sunColor = mixRgb(dayColor, DayNight.NIGHT_SUN, night)
        val skyDay = mixRgb(DayNight.DUSK_SKY, DayNight.DAY_SKY, horizonWarm)
        val skyColor = mixRgb(skyDay, DayNight.NIGHT_SKY, night)

        val lm = engine.lightManager
        lm.setColor(sunLightInstance, sunColor.r, sunColor.g, sunColor.b)
        lm.setIntensity(sunLightInstance, sunLux)
        // Sun path by day; a fixed high moon by deep night; blended between.
        val azDeg = lerp(75f, 285f, dayT)
        val elDeg = 10f + 60f * elevationParam
        val az = Math.toRadians(azDeg.toDouble())
        val el = Math.toRadians(elDeg.toDouble())
        val px = (cos(az) * cos(el)).toFloat()
        val py = sin(el).toFloat()
        val pz = (sin(az) * cos(el)).toFloat()
        val moonX = -0.32f; val moonY = -0.72f; val moonZ = -0.61f
        val dx = lerp(-px, moonX, night)
        val dy = lerp(-py, moonY, night)
        val dz = lerp(-pz, moonZ, night)
        lm.setDirection(sunLightInstance, dx, dy, dz)
        indirectLight?.setIntensity(ambientLux)
        sky?.setColor(skyColor.r, skyColor.g, skyColor.b, 1f)

        // Lanterns, torches, crystals, and the monolith come alive at dusk.
        lanternInstance?.setParameter("emissiveStrength", 0.05f + 3.2f * night)
        val crystalGlow = 0.30f + 1.60f * night
        for (ci in crystalInstances) ci.setParameter("emissiveStrength", crystalGlow)
        monolithInstance?.setParameter("emissiveStrength", 0.55f + 2.4f * night)
        engine.lightManager.setIntensity(monolithLightInstance, 700f + 2600f * night)
    }

    private fun smoothStep(x: Float): Float = x * x * (3f - 2f * x)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun mixRgb(a: Theme.Rgb, b: Theme.Rgb, t: Float) = Theme.Rgb(
        a.r + (b.r - a.r) * t,
        a.g + (b.g - a.g) * t,
        a.b + (b.b - a.b) * t,
    )

    fun destroy() {
        for (i in 0 until rockCount) if (!rockVisible[i]) scene.addEntity(rockEntities[i])
        if (!binVisible) { scene.addEntity(binCrateEntity); scene.addEntity(binLidEntity) }
        if (!furnaceVisible) { for (e in furnaceEntities) scene.addEntity(e) }
        assets.destroy(scene)
        engine.destroyEntity(sunEntity)
        engine.destroyEntity(furnaceLightEntity)
        engine.destroyEntity(monolithLightEntity)
        indirectLight?.let { engine.destroyIndirectLight(it) }
        sky?.let { engine.destroySkybox(it) }
        cameraRig.destroy()
        engine.destroyScene(scene)
    }
}
