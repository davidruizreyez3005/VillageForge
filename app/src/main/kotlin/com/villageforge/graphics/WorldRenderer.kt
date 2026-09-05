package com.villageforge.graphics

import com.google.android.filament.*
import com.villageforge.config.DayNight
import com.villageforge.config.LightingProbe
import com.villageforge.config.Theme
import com.villageforge.config.Town
import com.villageforge.config.WorldLayout
import com.villageforge.state.GameState
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
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

    // v2.3 — the smith's carried torch: a warm point light that follows the
    // player and breathes with the flame once dusk gathers.
    private val torchLightEntity: Int = EntityManager.get().create()
    private var torchLightInstance = 0
    private var torchLightInScene = false

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

    // v2.2 — The Village Update.
    /** Renderables per slot / per build stage; visible while stage is bought. */
    private val slotGroups = Array(Town.slots.size) { i -> Array(Town.slots[i].maxStage) { ArrayList<Int>() } }
    private val slotVisible = Array(Town.slots.size) { i -> IntArray(Town.slots[i].maxStage) }
    /** The well ladder grows cumulatively with prestige. */
    private val wellGroups = Array(Town.wellTiers.size) { ArrayList<Int>() }
    private val wellVisible = IntArray(Town.wellTiers.size)
    private var windowInstance: MaterialInstance? = null
    private val windmillSailEntities = IntArray(4)
    private val windmillSparEntities = IntArray(4)
    private var windmillHubX = 0f
    private var windmillHubY = 0f
    private var windmillHubZ = 0f
    private var sailPhase = 0f
    private var fountainJetEntity = 0
    private val villagerRigs = ArrayList<HumanoidRig>()
    private val rigParked = ArrayList<Boolean>()

    // v2.2 build-time scratch + facing — MUST be declared before the init
    // block: buildVillage() runs from init and Kotlin initializes fields in
    // declaration order (declaring these after init left them null on device).
    /** All buildings face the camera's three-quarter view (45° yaw). */
    private val FACING = 45f
    private val roofM = FloatArray(16)
    private val roofM2 = FloatArray(16)
    private val roofM3 = FloatArray(16)

    private companion object {
        const val RAIN_COUNT = 64
        const val RAIN_AREA = 17f
        const val RAIN_TOP = 13f
        const val RAIN_FALL_SPEED = 16f
        const val ROOF_PITCH = 0.62f
    }

    private class RainStreak(val entity: Int) {
        var x = 0f; var y = 0f; var z = 0f
        var speed = 1f
        var active = false
    }

    private val rainStreaks = ArrayList<RainStreak>()
    private val rainInstance: MaterialInstance by lazy { assets.smokeInstance() }
    private val rainScratch = FloatArray(16)
    private val rainScratch2 = FloatArray(16)
    private val rainM = FloatArray(16)
    private val sailM = FloatArray(16)

    init {
        buildLights()
        buildTerrain()
        buildCliffs()
        buildScatter()
        buildGate()
        buildTrees()
        buildRocks()
        buildTradePost()
        buildBin()
        buildFurnace()
        buildHollow()
        buildVillage()
        buildRain()
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

        // v2.3 — the player's torch light. Created unlit; updateTorch() adds
        // it to the scene and tracks the player as the evening comes on.
        LightManager.Builder(LightManager.Type.POINT)
            .color(Theme.TORCH_FLAME.r, Theme.TORCH_FLAME.g, Theme.TORCH_FLAME.b)
            .intensity(0f)
            .position(WorldLayout.SPAWN_X, 1.45f, WorldLayout.SPAWN_Z)
            .falloff(11f)
            .build(engine, torchLightEntity)
        torchLightInstance = engine.lightManager.getInstance(torchLightEntity)
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
            // v2.3 — craggy tapered slabs instead of plain boxes.
            val mesh = assets.cliffVariants[rng.nextInt(assets.cliffVariants.size)]
            assets.addRenderable(scene, mesh, instances[rng.nextInt(instances.size)],
                Transforms.trs(x + w / 2f, WorldLayout.groundHeight(x, z) - 0.6f, z, w, h, d, yaw))
            x += w - 0.8f
        }
    }

    /** Cliff slabs stacked along a Z run at fixed X (for east/west walls). */
    private fun addCliffColumn(rng: Random, instances: List<MaterialInstance>, z0: Float, z1: Float, x: Float) {
        var z = z0
        while (z < z1) {
            val d = 4f + rng.nextFloat() * 3f
            val h = 7f + rng.nextFloat() * 6f
            val w = 4.5f + rng.nextFloat() * 2f
            val yaw = (rng.nextFloat() - 0.5f) * 8f
            val mesh = assets.cliffVariants[rng.nextInt(assets.cliffVariants.size)]
            assets.addRenderable(scene, mesh, instances[rng.nextInt(instances.size)],
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

    /** v2.3 — two merged meshes of grass tufts and pebble chips dressing the valley floor. */
    private fun buildScatter() {
        assets.addRenderable(
            scene, assets.tuftScatter,
            assets.material(Theme.GRASS_TUFT, Theme.ROUGHNESS_TERRAIN),
            Transforms.trs(0f, 0f, 0f, 1f, 1f, 1f),
            castShadows = false,
        )
        assets.addRenderable(
            scene, assets.pebbleScatter,
            assets.material(Theme.PEBBLE, Theme.ROUGHNESS_PROP),
            Transforms.trs(0f, 0f, 0f, 1f, 1f, 1f),
            castShadows = false,
        )
    }

    private fun buildGate() {
        val wood = assets.material(Theme.GATE_WOOD, Theme.ROUGHNESS_PROP)
        val trim = assets.material(Theme.STALL_TRIM, Theme.ROUGHNESS_PROP)
        lanternInstance = assets.material(Theme.LANTERN_GLOW, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT, Theme.LANTERN_GLOW, 0.05f)
        val z = WorldLayout.GATE_Z
        val y = WorldLayout.groundHeight(0f, z)
        // Posts are 6-sided timbers; the flames are gem-shaped now.
        for (dx in floatArrayOf(-6.2f, 6.2f)) {
            assets.addRenderable(scene, assets.cyl6, wood, Transforms.trs(dx, y, z, 0.42f, 3.6f, 0.42f))
        }
        assets.addRenderable(scene, assets.roundedBox, wood, Transforms.trs(0f, y + 3.5f, z, 13.2f, 0.4f, 0.5f))
        // Hanging sign board.
        assets.addRenderable(scene, assets.roundedBox, trim, Transforms.trs(0f, y + 2.7f, z + 0.1f, 3.4f, 0.85f, 0.12f))
        // Torch flames on both posts.
        for (dx in floatArrayOf(-6.2f, 6.2f)) {
            assets.addRenderable(scene, assets.gem, lanternInstance!!, Transforms.trs(dx, y + 3.9f, z, 0.24f, 0.38f, 0.24f))
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
            // v2.3 — round trunk + faceted canopy blobs.
            assets.addRenderable(scene, assets.cyl6, bark,
                Transforms.trs(x, y, z, 0.55f * scale, trunkHeight, 0.55f * scale, yaw))
            val c1 = 2.1f * scale
            val blob1 = assets.canopyBlobs[rng.nextInt(assets.canopyBlobs.size)]
            assets.addRenderable(scene, blob1, canopies[rng.nextInt(canopies.size)],
                Transforms.trs(x, y + trunkHeight - 0.15f + c1 * 0.425f, z, c1, c1 * 0.85f, c1, yaw + 3f))
            val c2 = 1.2f * scale
            val blob2 = assets.canopyBlobs[rng.nextInt(assets.canopyBlobs.size)]
            assets.addRenderable(scene, blob2, canopies[rng.nextInt(canopies.size)],
                Transforms.trs(x, y + trunkHeight + c1 * 0.85f - 0.35f + c2 * 0.5f, z, c2, c2, c2, yaw + 9f))
        }
        // Sparse dark pines inside the canyon — proper conical layers now.
        val pineCanopies = Theme.PINE_CANOPY.map { assets.material(it, Theme.ROUGHNESS_PROP) }
        for ((x, z) in WorldLayout.canyonPines) {
            val yaw = rng.nextFloat() * 360f
            val scale = 0.8f + rng.nextFloat() * 0.4f
            val y = WorldLayout.groundHeight(x, z) - 0.1f
            val trunk = 1.9f * scale
            assets.addRenderable(scene, assets.cyl6, bark, Transforms.trs(x, y, z, 0.4f * scale, trunk, 0.4f * scale, yaw))
            var cy = y + trunk - 0.2f
            var c = 2.0f * scale
            for (layer in 0 until 3) {
                assets.addRenderable(scene, assets.cone6, pineCanopies[rng.nextInt(pineCanopies.size)],
                    Transforms.trs(x, cy, z, c, c * 1.05f, c, yaw + layer * 25f))
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

        assets.addRenderable(scene, assets.roundedBox, wood, Transforms.trs(x, y, z, 2.0f, 0.95f, 0.9f))
        assets.addRenderable(scene, assets.roundedBox, trim, Transforms.trs(x, y + 0.95f, z, 2.3f, 0.10f, 1.15f))
        for (dx in floatArrayOf(-1.05f, 1.05f)) {
            for (dz in floatArrayOf(-0.6f, 0.6f)) {
                assets.addRenderable(scene, assets.cyl6, wood, Transforms.trs(x + dx, y, z + dz, 0.14f, 2.4f, 0.14f))
            }
        }
        assets.addRenderable(scene, assets.roundedBox, awning, Transforms.trs(x, y + 2.45f, z - 0.15f, 2.7f, 0.14f, 1.7f))
        assets.addRenderable(scene, assets.roundedBox, trim, Transforms.trs(x, y + 2.45f, z + 0.55f, 2.7f, 0.14f, 0.28f))

        if (lanternInstance == null) {
            lanternInstance = assets.material(Theme.LANTERN_GLOW, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT, Theme.LANTERN_GLOW, 0.05f)
        }
        for (dx in floatArrayOf(-1.75f, 1.75f)) {
            val lz = z + 1.3f
            assets.addRenderable(scene, assets.cyl6, wood, Transforms.trs(x + dx, y, lz, 0.13f, 2.1f, 0.13f))
            assets.addRenderable(scene, assets.gem, lanternInstance!!, Transforms.trs(x + dx, y + 2.2f, lz, 0.26f, 0.36f, 0.26f))
        }
    }

    private fun buildBin() {
        val x = WorldLayout.BIN_X
        val z = WorldLayout.BIN_Z
        val y = WorldLayout.groundHeight(x, z) - 0.05f
        val crate = assets.material(Theme.BIN_WOOD, Theme.ROUGHNESS_PROP)
        val lid = assets.material(Theme.BIN_LID, Theme.ROUGHNESS_PROP)

        binCrateEntity = assets.addRenderable(scene, assets.roundedBox, crate,
            Transforms.trs(x, y, z, 1.15f, 0.85f, 1.15f, 8f))
        binLidEntity = assets.addRenderable(scene, assets.roundedBox, lid,
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

        // Stone furnace body with a tapering round chimney and an arched mouth.
        furnaceEntities.add(assets.addRenderable(scene, assets.roundedBox, stone, Transforms.trs(fx, gy, fz, 1.8f, 1.3f, 1.6f)))
        furnaceEntities.add(assets.addRenderable(scene, assets.roundedBox, stone, Transforms.trs(fx, gy + 1.3f, fz, 1.4f, 0.7f, 1.2f)))
        furnaceEntities.add(assets.addRenderable(scene, assets.taper8, stoneDark, Transforms.trs(fx, gy + 2.0f, fz, 0.95f, 1.5f, 0.95f)))
        furnaceEntities.add(assets.addRenderable(scene, assets.roundedBox, stoneDark, Transforms.trs(fx, gy + 3.5f, fz, 0.7f, 0.4f, 0.7f)))
        // Glowing arched mouth on the +z face.
        furnaceEntities.add(assets.addRenderable(scene, assets.archPanel, mouthInstance!!, Transforms.trs(fx, gy + 0.32f, fz + 0.81f, 1f, 1f, 1f)))

        // Anvil on a stump beside it.
        val ax = WorldLayout.ANVIL_X
        val az = WorldLayout.ANVIL_Z
        val ay = WorldLayout.groundHeight(ax, az)
        furnaceEntities.add(assets.addRenderable(scene, assets.roundedBox, stump, Transforms.trs(ax, ay, az, 0.55f, 0.5f, 0.55f)))
        furnaceEntities.add(assets.addRenderable(scene, assets.roundedBox, anvil, Transforms.trs(ax, ay + 0.5f, az, 0.30f, 0.30f, 0.30f)))
        furnaceEntities.add(assets.addRenderable(scene, assets.roundedBox, anvil, Transforms.trs(ax, ay + 0.8f, az, 0.78f, 0.22f, 0.34f)))
        // Coal pile — little rock chunks.
        for (i in 0 until 3) {
            furnaceEntities.add(assets.addRenderable(scene, assets.rockVariants[i % assets.rockVariants.size], coal,
                Transforms.trs(fx + 1.35f, gy - 0.08f, fz - 0.4f + i * 0.35f, 0.34f, 0.30f, 0.34f, i * 30f)))
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

        // Emissive crystal clusters: 2-3 tilted gem shards each, alternating hues.
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
                val w = 0.30f * scale * (1f - 0.25f * s)
                val yaw = rng.nextFloat() * 90f + 15f
                val ox = (rng.nextFloat() - 0.5f) * 1.1f
                val oz = (rng.nextFloat() - 0.5f) * 1.1f
                assets.addRenderable(scene, assets.gem, instance,
                    Transforms.trs(cx + ox, base, cz + oz, w, h, w, yaw))
            }
        }

        // The great crystal monolith: three stacked, rotated tapers + light.
        val mx = WorldLayout.MONOLITH_X
        val mz = WorldLayout.MONOLITH_Z
        val my = WorldLayout.groundHeight(mx, mz)
        monolithInstance = assets.material(
            Theme.MONOLITH, Theme.ROUGHNESS_ORE, Theme.METALLIC_ORE,
            emissive = Theme.MONOLITH, emissiveStrength = 0.6f,
        )
        assets.addRenderable(scene, assets.taper8, monolithInstance!!, Transforms.trs(mx, my, mz, 1.5f, 3.6f, 1.5f, 18f))
        assets.addRenderable(scene, assets.taper8, monolithInstance!!, Transforms.trs(mx, my + 3.4f, mz, 1.05f, 2.2f, 1.05f, -12f))
        assets.addRenderable(scene, assets.taper8, monolithInstance!!, Transforms.trs(mx, my + 5.4f, mz, 0.62f, 1.4f, 0.62f, 40f))
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
        assets.addRenderable(scene, assets.roundedBox, dark, Transforms.trs(ex, ey, ez, 3.2f, 2.6f, 1.2f))
        for (dx in floatArrayOf(-1.5f, 1.5f)) {
            assets.addRenderable(scene, assets.cyl6, timber, Transforms.trs(ex + dx, ey, ez, 0.42f, 2.6f, 0.42f))
        }
        assets.addRenderable(scene, assets.roundedBox, timber, Transforms.trs(ex, ey + 2.5f, ez, 3.5f, 0.4f, 0.5f))

        // Weathered standing stones — round-shouldered tapers now.
        val stone = assets.material(Theme.STANDING_STONE, Theme.ROUGHNESS_PROP)
        for ((sx, sz, h) in WorldLayout.standingStones) {
            val sy = WorldLayout.groundHeight(sx, sz)
            val yaw = rng.nextFloat() * 360f
            assets.addRenderable(scene, assets.taper8, stone, Transforms.trs(sx, sy, sz, 0.75f, h, 0.5f, yaw))
            assets.addRenderable(scene, assets.roundedBox, stone, Transforms.trs(sx, sy + h, sz, 0.95f, 0.25f, 0.6f, yaw + 20f))
        }

        // Lantern posts marking the link mouth — the trail into the hollow.
        if (lanternInstance == null) {
            lanternInstance = assets.material(Theme.LANTERN_GLOW, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT, Theme.LANTERN_GLOW, 0.05f)
        }
        val wood = assets.material(Theme.GATE_WOOD, Theme.ROUGHNESS_PROP)
        for (lx in floatArrayOf(-31.5f, -17.5f)) {
            val lz = WorldLayout.LINK_Z_MAX + 2.2f
            val ly = WorldLayout.groundHeight(lx, lz)
            assets.addRenderable(scene, assets.cyl6, wood, Transforms.trs(lx, ly, lz, 0.15f, 2.3f, 0.15f))
            assets.addRenderable(scene, assets.gem, lanternInstance!!, Transforms.trs(lx, ly + 2.38f, lz, 0.28f, 0.38f, 0.28f))
        }
    }

    // ---- The Village (v2.2) --------------------------------------------------

    private fun addPart(
        group: ArrayList<Int>, x: Float, y: Float, z: Float,
        w: Float, h: Float, d: Float, instance: MaterialInstance, yaw: Float = FACING,
        mesh: AssetFactory.Mesh = assets.roundedBox,
    ): Int {
        val e = assets.addRenderable(scene, mesh, instance, Transforms.trs(x, y, z, w, h, d, yaw))
        group.add(e)
        return e
    }

    /**
     * v2.3 — a proper solid gable-roof mesh (one renderable) instead of two
     * tilted boxes and a ridge cap: real eaves, real ridge, capped gable ends.
     */
    private fun addRoof(
        group: ArrayList<Int>, x: Float, y: Float, z: Float, w: Float, d: Float,
        instance: MaterialInstance, yaw: Float = FACING, pitch: Float = ROOF_PITCH,
    ) {
        val mesh = assets.gableRoof(w, d, pitch, 0.30f)
        val e = assets.addRenderable(scene, mesh, instance, Transforms.trs(x, y, z, 1f, 1f, 1f, yaw))
        group.add(e)
    }

    /** Stage 0 of a house: a dug plot with stacked timber waiting. */
    private fun buildHousePlot(group: ArrayList<Int>, x: Float, z: Float) {
        val y = WorldLayout.groundHeight(x, z) - 0.02f
        val soil = assets.material(Theme.SOIL, Theme.ROUGHNESS_TERRAIN)
        val wood = assets.material(Theme.TIMBER, Theme.ROUGHNESS_PROP)
        addPart(group, x, y, z, 2.8f, 0.08f, 2.4f, soil, 0f)
        for (dx in floatArrayOf(-1.2f, 1.2f)) for (dz in floatArrayOf(-1.0f, 1.0f)) {
            addPart(group, x + dx, y, z + dz, 0.13f, 1.0f, 0.13f, wood, FACING, assets.cyl6)
        }
        addPart(group, x - 0.3f, y + 0.1f, z, 1.6f, 0.22f, 0.5f, wood)
        addPart(group, x + 0.4f, y + 0.1f, z + 0.2f, 1.1f, 0.22f, 0.5f, wood, FACING + 8f)
    }

    /** Stage 1: the cottage itself — plaster, timber, glowing windows, tiled roof. */
    private fun buildCottage(group: ArrayList<Int>, x: Float, z: Float, longhouse: Boolean) {
        val y = WorldLayout.groundHeight(x, z) - 0.02f
        val w = if (longhouse) 3.1f else 2.6f
        val d = 2.2f
        val wallH = 1.9f
        val plaster = assets.material(Theme.PLASTER, Theme.ROUGHNESS_PROP)
        val timber = assets.material(Theme.TIMBER, Theme.ROUGHNESS_PROP)
        val door = assets.material(Theme.DOOR_WOOD, Theme.ROUGHNESS_PROP)
        val roof = assets.material(Theme.ROOF_TILE, Theme.ROUGHNESS_PROP)
        windowInstance = windowInstance ?: assets.material(
            Theme.WINDOW_GLOW, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT,
            emissive = Theme.WINDOW_GLOW, emissiveStrength = 0.05f,
        )

        addPart(group, x, y, z, w, wallH, d, plaster)
        // Corner posts + a door and two shuttered windows on the street face.
        for (dx in floatArrayOf(-w / 2f + 0.07f, w / 2f - 0.07f)) {
            addPart(group, x + dx, y, z - d / 2f + 0.07f, 0.15f, wallH, 0.15f, timber, FACING, assets.cyl6)
            addPart(group, x + dx, y, z + d / 2f - 0.07f, 0.15f, wallH, 0.15f, timber, FACING, assets.cyl6)
        }
        addPart(group, x - w / 4f, y, z + d / 2f + 0.02f, 0.62f, 1.25f, 0.1f, door)
        addPart(group, x + w / 4f, y + 0.75f, z + d / 2f + 0.03f, 0.5f, 0.55f, 0.06f, windowInstance!!, yaw = FACING)
        addPart(group, x + w / 2f + 0.03f, y + 0.75f, z - 0.2f, 0.06f, 0.55f, 0.5f, windowInstance!!)
        addPart(group, x, y + wallH, z, w, 0.22f, d + 0.2f, timber)
        addRoof(group, x, y + wallH + 0.2f, z, w, d, roof)
        // Chimney on the longhouse.
        if (longhouse) {
            addPart(group, x - w / 3f, y + wallH + 0.2f, z - d / 5f, 0.36f, 1.5f, 0.36f, assets.material(Theme.WELL_STONE, Theme.ROUGHNESS_PROP), FACING, assets.taper8)
        }
    }

    private fun buildLamp(group: ArrayList<Int>, x: Float, z: Float) {
        val y = WorldLayout.groundHeight(x, z)
        val wood = assets.material(Theme.GATE_WOOD, Theme.ROUGHNESS_PROP)
        lanternInstance = lanternInstance ?: assets.material(Theme.LANTERN_GLOW, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT, Theme.LANTERN_GLOW, 0.05f)
        addPart(group, x, y, z, 0.15f, 2.15f, 0.15f, wood, 0f, assets.cyl6)
        addPart(group, x, y + 2.15f, z, 0.30f, 0.08f, 0.30f, wood, 0f)
        addPart(group, x, y + 2.24f, z, 0.26f, 0.38f, 0.26f, lanternInstance!!, 0f, assets.gem)
    }

    private fun buildField(group: ArrayList<Int>, x: Float, z: Float) {
        val y = WorldLayout.groundHeight(x, z) - 0.02f
        val soil = assets.material(Theme.SOIL, Theme.ROUGHNESS_TERRAIN)
        val wheat = assets.material(Theme.WHEAT, Theme.ROUGHNESS_PROP)
        val wood = assets.material(Theme.TIMBER, Theme.ROUGHNESS_PROP)
        addPart(group, x, y, z, 2.9f, 0.1f, 2.6f, soil, 0f)
        for (row in 0 until 3) {
            addPart(group, x, y + 0.08f, z - 0.8f + row * 0.8f, 2.5f, 0.16f, 0.42f, soil, 0f)
            for (i in 0 until 4) {
                val wx = x - 1.0f + i * 0.66f + (row % 2) * 0.18f
                addPart(group, wx, y + 0.2f, z - 0.8f + row * 0.8f, 0.18f, 0.6f, 0.18f, wheat, 0f, assets.cone4)
            }
        }
        for (dx in floatArrayOf(-1.35f, 1.35f)) for (dz in floatArrayOf(-1.2f, 1.2f)) {
            addPart(group, x + dx, y, z + dz, 0.12f, 0.7f, 0.12f, wood, 0f, assets.cyl6)
        }
    }

    private fun buildGranary(group: ArrayList<Int>, x: Float, z: Float) {
        val y = WorldLayout.groundHeight(x, z) - 0.02f
        val wood = assets.material(Theme.BIN_WOOD, Theme.ROUGHNESS_PROP)
        val thatch = assets.material(Theme.ROOF_THATCH, Theme.ROUGHNESS_PROP)
        val timber = assets.material(Theme.TIMBER, Theme.ROUGHNESS_PROP)
        // Raised on posts so the grain stays dry.
        for (dx in floatArrayOf(-0.95f, 0.95f)) for (dz in floatArrayOf(-0.75f, 0.75f)) {
            addPart(group, x + dx, y, z + dz, 0.16f, 1.0f, 0.16f, timber, FACING, assets.cyl6)
        }
        addPart(group, x, y + 1.0f, z, 2.3f, 1.15f, 1.9f, wood)
        addRoof(group, x, y + 2.15f, z, 2.3f, 1.9f, thatch)
        // A little ladder up the side.
        addPart(group, x - 1.2f, y + 0.2f, z + 0.55f, 0.08f, 1.9f, 0.08f, timber, 12f)
        addPart(group, x - 1.2f, y + 0.2f, z - 0.15f, 0.08f, 1.9f, 0.08f, timber, 12f)
        for (r in 0 until 3) addPart(group, x - 1.2f, y + 0.45f + r * 0.55f, z + 0.2f, 0.62f, 0.07f, 0.07f, timber, 12f)
    }

    private fun buildWindmill(group: ArrayList<Int>, x: Float, z: Float) {
        val y = WorldLayout.groundHeight(x, z) - 0.05f
        val plaster = assets.material(Theme.PLASTER, Theme.ROUGHNESS_PROP)
        val timber = assets.material(Theme.TIMBER, Theme.ROUGHNESS_PROP)
        val sail = assets.material(Theme.SAIL_CLOTH, Theme.ROUGHNESS_PROP)
        val metal = assets.material(Theme.ANVIL, Theme.ROUGHNESS_METAL, Theme.METALLIC_INGOT)
        // v2.3 — one smooth tapered round tower instead of three stacked boxes.
        addPart(group, x, y, z, 2.1f, 6.0f, 2.1f, plaster, FACING, assets.prism(8, 0.62f))
        // Timber band rings where the old lifts met.
        for (lift in 0 until 2) addPart(group, x, y + lift * 2.2f + 2.1f, z, 2.2f - lift * 0.4f, 0.14f, 2.2f - lift * 0.4f, timber, FACING, assets.cyl8)
        // Dome cap + door + window.
        addPart(group, x, y + 6.0f, z, 1.5f, 0.85f, 1.5f, timber, FACING, assets.dome)
        addPart(group, x, y, z + 1.0f, 0.7f, 1.3f, 0.1f, assets.material(Theme.DOOR_WOOD, Theme.ROUGHNESS_PROP))
        windowInstance = windowInstance ?: assets.material(
            Theme.WINDOW_GLOW, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT,
            emissive = Theme.WINDOW_GLOW, emissiveStrength = 0.05f,
        )
        addPart(group, x, y + 2.7f, z + 0.85f, 0.45f, 0.5f, 0.06f, windowInstance!!)
        // The windshaft and hub on the tower's flank.
        windmillHubX = x + 1.05f
        windmillHubY = y + 5.1f
        windmillHubZ = z
        addPart(group, windmillHubX - 0.3f, windmillHubY - 0.25f, windmillHubZ, 0.9f, 0.5f, 0.5f, timber)
        val hub = assets.addRenderable(scene, assets.cyl6, metal, Transforms.trs(windmillHubX, windmillHubY - 0.25f, windmillHubZ - 0.25f, 0.36f, 0.36f, 0.36f))
        group.add(hub)
        // v2.3 — tapered cloth sails (single slab meshes) + a wooden spar each.
        val park = Transforms.trs(windmillHubX, -100f, windmillHubZ, 0.001f, 0.001f, 0.001f)
        for (i in 0 until 4) {
            val e = assets.addRenderable(
                scene, assets.sail, sail,
                park.copyOf(),
                castShadows = true,
            )
            windmillSailEntities[i] = e
            group.add(e)
            val spar = assets.addRenderable(scene, assets.cyl6, timber, park.copyOf())
            windmillSparEntities[i] = spar
            group.add(spar)
        }
    }

    private fun buildChapel(group: ArrayList<Int>, x: Float, z: Float) {
        val y = WorldLayout.groundHeight(x, z) - 0.02f
        val stone = assets.material(Theme.CHAPEL_STONE, Theme.ROUGHNESS_PROP)
        val roof = assets.material(Theme.ROOF_TILE, Theme.ROUGHNESS_PROP)
        val dark = assets.material(Theme.MINE_DARK, Theme.ROUGHNESS_PROP)
        windowInstance = windowInstance ?: assets.material(
            Theme.WINDOW_GLOW, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT,
            emissive = Theme.WINDOW_GLOW, emissiveStrength = 0.05f,
        )
        // Nave.
        addPart(group, x, y, z, 2.5f, 3.0f, 3.3f, stone)
        addRoof(group, x, y + 3.0f, z, 2.5f, 3.3f, roof, pitch = 0.68f)
        // Buttresses down both flanks.
        for (dz in floatArrayOf(-1.1f, 0.2f)) for (dx in floatArrayOf(-1.35f, 1.35f)) {
            addPart(group, x + dx, y, z + dz, 0.32f, 1.7f, 0.36f, stone, FACING, assets.taper8)
        }
        // Door of two orders + a rose window.
        addPart(group, x - 0.55f, y, z + 1.65f, 0.28f, 1.5f, 0.14f, stone)
        addPart(group, x + 0.55f, y, z + 1.65f, 0.28f, 1.5f, 0.14f, stone)
        addPart(group, x, y + 1.55f, z + 1.68f, 0.5f, 0.34f, 0.1f, dark)
        addPart(group, x, y + 1.95f, z + 1.68f, 0.38f, 0.55f, 0.08f, windowInstance!!, FACING, assets.gem)
        // Bell tower + open belfry + spire.
        addPart(group, x - 1.5f, y, z - 1.6f, 1.1f, 4.2f, 1.1f, stone, FACING, assets.prism(8, 0.78f))
        addPart(group, x - 1.5f, y + 4.2f, z - 1.6f, 1.3f, 0.22f, 1.3f, stone)
        for (dx in floatArrayOf(-0.45f, 0.45f)) for (dz in floatArrayOf(-0.45f, 0.45f)) {
            addPart(group, x - 1.5f + dx, y + 4.4f, z - 1.6f + dz, 0.14f, 0.7f, 0.14f, dark, FACING, assets.cyl6)
        }
        // The bell itself, hanging where it can be seen.
        addPart(group, x - 1.5f, y + 4.7f, z - 1.6f, 0.30f, 0.36f, 0.30f, assets.material(Theme.ORE_GOLD, Theme.ROUGHNESS_METAL, Theme.METALLIC_INGOT), FACING, assets.taper8)
        addPart(group, x - 1.5f, y + 5.05f, z - 1.6f, 1.0f, 0.95f, 1.0f, roof, FACING, assets.cone6)
        addPart(group, x - 1.5f, y + 6.0f, z - 1.6f, 0.6f, 1.0f, 0.6f, roof, FACING, assets.cone6)
        addPart(group, x - 1.5f, y + 7.0f, z - 1.6f, 0.16f, 0.7f, 0.16f, dark, FACING, assets.cyl6)
    }

    /** Era 0 — the plaza pad and the low octagonal ring of the first well. */
    private fun buildWellCore(group: ArrayList<Int>, x: Float, z: Float) {
        val y = WorldLayout.groundHeight(x, z) - 0.02f
        val stone = assets.material(Theme.WELL_STONE, Theme.ROUGHNESS_PROP)
        val water = assets.material(Theme.WELL_WATER, Theme.ROUGHNESS_PROP)
        addPart(group, x, y, z, 5.5f, 0.07f, 5.5f, assets.material(Theme.PATH, Theme.ROUGHNESS_TERRAIN), 0f)
        // v2.3 — an eight-segment octagon instead of four straight walls.
        for (k in 0 until 8) {
            val a = k * 0.78539816f
            addPart(
                group, x + cos(a) * 0.78f, y, z + sin(a) * 0.78f,
                0.64f, 0.55f, 0.30f, stone,
                90f - Math.toDegrees(a.toDouble()).toFloat(),
            )
        }
        addPart(group, x, y + 0.18f, z, 1.3f, 0.06f, 1.3f, water, 0f, assets.cyl8)
    }

    /** Era 1 — a proper stone well: raised rim, apron, and the market paths. */
    private fun buildStoneWell(group: ArrayList<Int>, x: Float, z: Float) {
        val y = WorldLayout.groundHeight(x, z) - 0.02f
        val stone = assets.material(Theme.WELL_STONE, Theme.ROUGHNESS_PROP)
        val path = assets.material(Theme.PATH, Theme.ROUGHNESS_TERRAIN)
        buildWellCore(group, x, z)
        for (k in 0 until 8) {
            val a = k * 0.78539816f
            addPart(
                group, x + cos(a) * 0.82f, y + 0.55f, z + sin(a) * 0.82f,
                0.66f, 0.30f, 0.32f, stone,
                90f - Math.toDegrees(a.toDouble()).toFloat(),
            )
        }
        addPart(group, x, y, z, 2.6f, 0.1f, 2.6f, path, 0f)
        // Path to the market.
        addPart(group, x, y, z + 4.0f, 2.2f, 0.06f, 4.4f, path, 0f)
        addPart(group, x, y, z + 11.5f, 1.9f, 0.06f, 8.6f, path, 0f)
    }

    /**
     * v2.2.1 — every well era is SELF-CONTAINED and replaces the last: the
     * old cumulative stack drew the ring, the raised rim, the roof, the
     * pump, and the fountain all on the same spot at once, which read as a
     * pile of floating masonry. Each tier now rebuilds the base it stands
     * on, so exactly one well is ever visible.
     */
    private fun buildWellTier(tier: Int) {
        val group = wellGroups[tier]
        val x = Town.WELL_X
        val z = Town.WELL_Z
        val y = WorldLayout.groundHeight(x, z) - 0.02f
        val wood = assets.material(Theme.GATE_WOOD, Theme.ROUGHNESS_PROP)
        val roof = assets.material(Theme.ROOF_THATCH, Theme.ROUGHNESS_PROP)
        when (tier) {
            0 -> {
                // A dug well and a trampled plaza pad.
                buildWellCore(group, x, z)
            }
            1 -> {
                // Stone Well: raised rim, apron, and the market paths.
                buildStoneWell(group, x, z)
            }
            2 -> {
                // Roofed Well: posts, crossbar, and a bucket on a rope.
                buildStoneWell(group, x, z)
                for (dx in floatArrayOf(-0.85f, 0.85f)) addPart(group, x + dx, y, z - 0.75f, 0.13f, 2.4f, 0.13f, wood, 0f, assets.cyl6)
                addPart(group, x, y + 2.4f, z - 0.75f, 1.9f, 0.14f, 0.16f, wood, 0f)
                addRoof(group, x, y + 2.5f, z - 0.4f, 1.9f, 1.7f, roof, 0f)
                addPart(group, x, y + 2.1f, z - 0.75f, 0.05f, 0.6f, 0.05f, wood, 0f)
                addPart(group, x, y + 1.5f, z - 0.75f, 0.3f, 0.28f, 0.3f, wood, 0f)
            }
            3 -> {
                // Pump Well: an iron pump on a plinth beside the ring.
                buildStoneWell(group, x, z)
                val metal = assets.material(Theme.ANVIL, Theme.ROUGHNESS_METAL, Theme.METALLIC_INGOT)
                addPart(group, x + 1.35f, y + 0.85f, z, 0.44f, 0.9f, 0.44f, metal, 0f, assets.taper8)
                addPart(group, x + 1.35f, y + 1.55f, z, 0.14f, 0.5f, 0.14f, metal, -25f)
                addPart(group, x + 1.2f, y + 1.3f, z + 0.35f, 0.2f, 0.2f, 0.4f, metal, 0f)
            }
            else -> {
                // The Millpond Fountain: an octagonal stone basin whose wall
                // is grounded on the plaza, a wide water surface, a pillar,
                // and a live jet.
                val stone = assets.material(Theme.WELL_STONE, Theme.ROUGHNESS_PROP)
                val water = assets.material(Theme.WELL_WATER, Theme.ROUGHNESS_PROP)
                addPart(group, x, y, z, 6.2f, 0.07f, 6.2f, assets.material(Theme.PATH, Theme.ROUGHNESS_TERRAIN), 0f)
                val r = 1.45f
                for (k in 0 until 8) {
                    val a = k * 0.78539816f
                    addPart(
                        group, x + cos(a) * r, y + 0.1f, z + sin(a) * r,
                        1.2f, 0.6f, 0.35f, stone,
                        90f - Math.toDegrees(a.toDouble()).toFloat(),
                    )
                }
                addPart(group, x, y + 0.42f, z, 2.4f, 0.06f, 2.4f, water, 0f, assets.cyl8)
                addPart(group, x, y + 0.45f, z, 0.38f, 1.1f, 0.38f, stone, 0f, assets.cyl6)
                addPart(group, x, y + 1.5f, z, 0.6f, 0.22f, 0.6f, stone, 0f, assets.dome)
                fountainJetEntity = assets.addRenderable(
                    scene, assets.cyl8, water,
                    Transforms.trs(x, y + 1.7f, z, 0.1f, 1.0f, 0.1f),
                    castShadows = false,
                )
                group.add(fountainJetEntity)
                for (i in 0 until 4) {
                    val a = i * 1.5708f + 0.7854f
                    addPart(group, x + cos(a) * 0.85f, y + 0.8f, z + sin(a) * 0.85f, 0.16f, 0.34f, 0.16f, water, 0f, assets.gem)
                }
            }
        }
    }

    private fun buildVillage() {
        for (i in Town.slots.indices) {
            val slot = Town.slots[i]
            for (stage in 0 until slot.maxStage) {
                val group = slotGroups[i][stage]
                when (slot.kind) {
                    Town.SlotKind.HOUSE ->
                        if (stage == 0) buildHousePlot(group, slot.x, slot.z)
                        else buildCottage(group, slot.x, slot.z, slot.id == "house4")
                    Town.SlotKind.LAMP -> buildLamp(group, slot.x, slot.z)
                    Town.SlotKind.FIELD -> buildField(group, slot.x, slot.z)
                    Town.SlotKind.FARM ->
                        if (stage == 0) buildHousePlot(group, slot.x, slot.z)
                        else buildCottage(group, slot.x, slot.z, false)
                    Town.SlotKind.GRANARY -> buildGranary(group, slot.x, slot.z)
                    Town.SlotKind.WINDMILL -> buildWindmill(group, slot.x, slot.z)
                    Town.SlotKind.CHAPEL -> buildChapel(group, slot.x, slot.z)
                }
            }
        }
        for (tier in Town.wellTiers.indices) buildWellTier(tier)
        // v2.2.1 fix — "the town is already built": addRenderable()
        // registers every entity in the scene the moment it is created, so
        // every slot stage (plot AND cottage overlapping), every lamp and
        // chapel, and all five well eras rendered on day one no matter what
        // the save said. Park ALL of it off-scene here; syncVillage() just
        // below re-adds exactly the stages the player has paid for.
        for (groups in slotGroups) for (group in groups) for (e in group) scene.removeEntity(e)
        for (group in wellGroups) for (e in group) scene.removeEntity(e)
        syncVillage()
    }

    private fun buildRain() {
        val rng = Random(31337)
        for (i in 0 until RAIN_COUNT) {
            val e = assets.addRenderable(
                scene, assets.box, rainInstance,
                Transforms.trs(0f, -100f, 0f, 0.001f, 0.001f, 0.001f),
                castShadows = false,
            )
            val streak = RainStreak(e)
            streak.x = (rng.nextFloat() * 2f - 1f) * RAIN_AREA
            streak.z = (rng.nextFloat() * 2f - 1f) * RAIN_AREA
            streak.y = rng.nextFloat() * RAIN_TOP
            streak.speed = 0.8f + rng.nextFloat() * 0.5f
            rainStreaks.add(streak)
        }
        rainInstance.setParameter("baseColor", Theme.RAIN.r, Theme.RAIN.g, Theme.RAIN.b, 0f)
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
        syncVillage()
        syncTownsfolk(deltaSeconds)
        updateRain(deltaSeconds)
        applyDayNight()
        effects.update(deltaSeconds)
        val alpha = tickAlpha()
        val carryFill = if (game.carryCapacity > 0) game.inventory.total.toFloat() / game.carryCapacity else 0f
        val torch = DayNight.torchLevel(game.timeOfDay)
        playerRig.setTorchLevel(torch)
        playerRig.update(game.player, alpha, deltaSeconds, carryFill)
        updateTorchLight(torch, alpha)
    }

    /**
     * v2.3 — the smith's torch light: follows the player, flickers like the
     * flame gem on the rig, and only exists in the scene between dusk and
     * dawn. Miners carry torch VISUALS but no light of their own (the
     * budget stays flat on low-end GPUs).
     */
    private fun updateTorchLight(torch: Float, alpha: Float) {
        if (torch <= 0.01f) {
            if (torchLightInScene) {
                torchLightInScene = false
                runCatching { scene.removeEntity(torchLightEntity) }
            }
            return
        }
        if (!torchLightInScene) {
            torchLightInScene = true
            runCatching { scene.addEntity(torchLightEntity) }
        }
        val px = lerp(game.player.prevX, game.player.x, alpha)
        val pz = lerp(game.player.prevZ, game.player.z, alpha)
        val py = WorldLayout.groundHeight(px, pz)
        val lm = engine.lightManager
        runCatching {
            lm.setPosition(torchLightInstance, px, py + 1.45f, pz)
        }
        val flick = 0.88f + 0.12f * sin(clock * 11f) + 0.06f * sin(clock * 29f)
        lm.setIntensity(torchLightInstance, 4_600f * torch * flick)
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
        val torch = DayNight.torchLevel(game.timeOfDay)
        for (i in minerRigs.indices) {
            minerRigs[i].setTorchLevel(torch)
            minerRigs[i].update(game.miners[i].body, alpha, dt, 0f)
        }
    }

    // ---- The Village, live (v2.2) --------------------------------------------

    private fun syncVillage() {
        for (i in Town.slots.indices) {
            val built = game.villageSlots[i]
            for (s in Town.slots[i].stages.indices) {
                val visible = built > s
                if (visible != (slotVisible[i][s] == 1)) {
                    slotVisible[i][s] = if (visible) 1 else 0
                    for (e in slotGroups[i][s]) {
                        if (visible) scene.addEntity(e) else scene.removeEntity(e)
                    }
                }
            }
        }
        val tier = Town.wellTierIndex(game.prestige())
        for (t in Town.wellTiers.indices) {
            // v2.2.1 — well eras REPLACE one another (see buildWellTier):
            // exactly one well is ever on screen.
            val visible = tier == t
            if (visible != (wellVisible[t] == 1)) {
                wellVisible[t] = if (visible) 1 else 0
                for (e in wellGroups[t]) {
                    if (visible) scene.addEntity(e) else scene.removeEntity(e)
                }
            }
        }
        // The windmill only turns once it is raised; the fountain jet only
        // breathes once the well has grown into one.
        val windmillIdx = Town.slotIndex("windmill")
        if (game.villageSlots[windmillIdx] >= 1) {
            sailPhase += 0.55f * (1f - 0.7f * game.weatherRain.coerceIn(0f, 1f))
            val tm = engine.transformManager
            for (i in 0 until 4) {
                val theta = sailPhase + i * 1.5708f
                // Cloth sail: the mesh already spans y 0.2..2.5 from the hub,
                // so it needs only the rotation about the windshaft.
                Transforms.rootInto(roofM, windmillHubX, windmillHubY - 0.1f, windmillHubZ, 0f, theta)
                tm.setTransform(tm.getInstance(windmillSailEntities[i]), roofM)
                // Wooden spar behind each sail.
                Transforms.translation(roofM2, 0f, 0.1f, 0f)
                Transforms.multiply(roofM3, roofM, roofM2)
                Transforms.scale(roofM2, 0.06f, 2.7f, 0.06f)
                Transforms.multiply(sailM, roofM3, roofM2)
                tm.setTransform(tm.getInstance(windmillSparEntities[i]), sailM)
            }
        }
        if (fountainJetEntity != 0 && wellVisible.last() == 1) {
            val pulse = 0.75f + 0.25f * sin(clock * 3.1f)
            val tm = engine.transformManager
            val x = Town.WELL_X
            val z = Town.WELL_Z
            val y = WorldLayout.groundHeight(x, z) + 1.7f
            tm.setTransform(
                tm.getInstance(fountainJetEntity),
                Transforms.trs(x, y, z, 0.1f, 1.15f * pulse, 0.1f),
            )
        }
    }

    /** Residents out on the streets + market customers, through one small rig pool. */
    private fun syncTownsfolk(dt: Float) {
        val walkers = ArrayList<com.villageforge.entities.Player>(Town.RESIDENT_RIGS + Town.CUSTOMER_RIGS)
        for (r in game.residents) {
            if (r.out) walkers.add(r.body)
            if (walkers.size >= Town.RESIDENT_RIGS) break
        }
        var customers = 0
        for (c in game.commissions) {
            if (customers >= Town.CUSTOMER_RIGS) break
            walkers.add(c.customer)
            customers++
        }
        for (c in game.departingCustomers) {
            if (customers >= Town.CUSTOMER_RIGS) break
            walkers.add(c.customer)
            customers++
        }
        while (villagerRigs.size < walkers.size.coerceAtLeast(Town.RESIDENT_RIGS)) {
            val idx = villagerRigs.size
            villagerRigs.add(HumanoidRig(engine, scene, assets, RigStyle.villager(idx + 2)))
            rigParked.add(true)
        }
        val alpha = tickAlpha()
        for (i in villagerRigs.indices) {
            if (i < walkers.size) {
                rigParked[i] = false
                villagerRigs[i].update(walkers[i], alpha, dt, 0f)
            } else if (!rigParked[i]) {
                rigParked[i] = true
                villagerRigs[i].park()
            }
        }
    }

    private fun updateRain(dt: Float) {
        val rain = game.weatherRain.coerceIn(0f, 1f)
        if (rain <= 0.02f) {
            if (rainStreaks.isNotEmpty() && rainStreaks[0].active) {
                for (s in rainStreaks) { s.active = false; parkStreak(s) }
                rainInstance.setParameter("baseColor", Theme.RAIN.r, Theme.RAIN.g, Theme.RAIN.b, 0f)
            }
            return
        }
        val focus = cameraRig.focus()
        val activeCount = (RAIN_COUNT * (0.35f + 0.65f * rain)).toInt().coerceAtLeast(8)
        val tm = engine.transformManager
        for (i in rainStreaks.indices) {
            val s = rainStreaks[i]
            if (i >= activeCount) {
                if (s.active) { s.active = false; parkStreak(s) }
                continue
            }
            if (!s.active) {
                s.active = true
                s.x = focus[0] + (Math.random().toFloat() * 2f - 1f) * RAIN_AREA
                s.z = focus[1] + (Math.random().toFloat() * 2f - 1f) * RAIN_AREA
                s.y = RAIN_TOP * (0.4f + 0.6f * Math.random().toFloat())
            }
            s.y -= RAIN_FALL_SPEED * s.speed * dt
            val ground = WorldLayout.groundHeight(s.x, s.z)
            if (s.y < ground) {
                s.x = focus[0] + (Math.random().toFloat() * 2f - 1f) * RAIN_AREA
                s.z = focus[1] + (Math.random().toFloat() * 2f - 1f) * RAIN_AREA
                s.y = RAIN_TOP
            }
            // Slight forward slant so the shower reads as weather, not texture.
            Transforms.rootInto(rainScratch, s.x, s.y, s.z, 0f, 0.14f)
            Transforms.scale(rainScratch2, 0.03f, 0.62f, 0.03f)
            Transforms.multiply(rainM, rainScratch, rainScratch2)
            tm.setTransform(tm.getInstance(s.entity), rainM)
        }
        rainInstance.setParameter("baseColor", Theme.RAIN.r, Theme.RAIN.g, Theme.RAIN.b, 0.38f * rain)
    }

    private fun parkStreak(s: RainStreak) {
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(s.entity), Transforms.trs(0f, -100f, 0f, 0.001f, 0.001f, 0.001f))
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

        // v2.2 — a shower greys the light flat; the direct sun drops away,
        // but never so far that the world stops being readable.
        val rain = game.weatherRain.coerceIn(0f, 1f)
        val sunLux = lerp(DayNight.NIGHT_SUN_LUX, DayNight.DAY_SUN_LUX, daylight) * (1f - 0.75f * rain)
        val ambientLux = lerp(DayNight.NIGHT_AMBIENT_LUX, DayNight.DAY_AMBIENT_LUX, daylight) * (1f - 0.30f * rain)

        // Warm horizon sun that eases into cool moonlight without popping.
        val dayColor = mixRgb(DayNight.DUSK_SUN, DayNight.DAY_SUN, horizonWarm)
        val sunColor = mixRgb(mixRgb(dayColor, DayNight.NIGHT_SUN, night), Theme.OVERCAST_SKY, 0.55f * rain)
        val skyDay = mixRgb(DayNight.DUSK_SKY, DayNight.DAY_SKY, horizonWarm)
        val skyColor = mixRgb(mixRgb(skyDay, DayNight.NIGHT_SKY, night), Theme.OVERCAST_SKY, 0.75f * rain)

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
        // v2.2 — and so do the windows of every built home: somebody is indoors.
        windowInstance?.setParameter("emissiveStrength", 0.05f + 3.0f * night)
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

    private var destroyed = false

    fun destroy() {
        if (destroyed) return
        destroyed = true
        // Exception-hardened teardown: a rendering-resource failure during
        // activity destroy must NEVER crash the process — it used to kill the
        // freshly relaunched slot-picker activity on its loading screen.
        runCatching {
            for (i in 0 until rockCount) if (!rockVisible[i]) scene.addEntity(rockEntities[i])
            if (!binVisible) { scene.addEntity(binCrateEntity); scene.addEntity(binLidEntity) }
            if (!furnaceVisible) { for (e in furnaceEntities) scene.addEntity(e) }
            for (i in Town.slots.indices) for (s in Town.slots[i].stages.indices) {
                if (slotVisible[i][s] == 0) for (e in slotGroups[i][s]) scene.addEntity(e)
            }
            for (t in Town.wellTiers.indices) {
                if (wellVisible[t] == 0) for (e in wellGroups[t]) scene.addEntity(e)
            }
        }
        runCatching { assets.destroy(scene) }
        runCatching { engine.destroyEntity(sunEntity) }
        runCatching { engine.destroyEntity(furnaceLightEntity) }
        runCatching { engine.destroyEntity(monolithLightEntity) }
        runCatching { engine.destroyEntity(torchLightEntity) }
        runCatching { indirectLight?.let { engine.destroyIndirectLight(it) } }
        runCatching { sky?.let { engine.destroySkybox(it) } }
        runCatching { cameraRig.destroy() }
        runCatching { engine.destroyScene(scene) }
    }
}
