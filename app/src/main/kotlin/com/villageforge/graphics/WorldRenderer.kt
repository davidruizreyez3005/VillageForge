package com.villageforge.graphics

import com.google.android.filament.*
import com.villageforge.config.PlayerConfig
import com.villageforge.config.Theme
import com.villageforge.config.WorldLayout
import com.villageforge.entities.AnimState
import com.villageforge.entities.Player
import com.villageforge.state.GameState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** The blacksmith: 9 boxes posed by plain transform math each frame. */
class PlayerRig(engine: Engine, scene: Scene, assets: AssetFactory) {

    private val rm: RenderableManager = engine.renderableManager
    private val tm: TransformManager = engine.transformManager
    private val rigEntities = IntArray(LIMB_COUNT)
    private val limbMatrices = Array(LIMB_COUNT) { FloatArray(16) }
    private val root = FloatArray(16)
    private val temp = FloatArray(16)
    private val temp2 = FloatArray(16)
    private var clock = 0f
    private lateinit var pickHeadInstance: MaterialInstance

    init {
        val skin = assets.material(Theme.PLAYER_SKIN, Theme.ROUGHNESS_PROP)
        val tunic = assets.material(Theme.PLAYER_TUNIC, Theme.ROUGHNESS_PROP)
        val pants = assets.material(Theme.PLAYER_PANTS, Theme.ROUGHNESS_PROP)
        val apron = assets.material(Theme.PLAYER_APRON, Theme.ROUGHNESS_PROP)
        val wood = assets.material(Theme.BARK, Theme.ROUGHNESS_PROP)
        pickHeadInstance = assets.material(Theme.PICK_TINTS[0], Theme.ROUGHNESS_ORE, Theme.METALLIC_ORE)
        val identity = Transforms.trs(0f, 0f, 0f, 1f, 1f, 1f)
        rigEntities[TORSO] = assets.addRenderable(scene, assets.box, tunic, identity)
        rigEntities[APRON] = assets.addRenderable(scene, assets.box, apron, identity)
        rigEntities[HEAD] = assets.addRenderable(scene, assets.box, skin, identity)
        rigEntities[LEFT_LEG] = assets.addRenderable(scene, assets.box, pants, identity)
        rigEntities[RIGHT_LEG] = assets.addRenderable(scene, assets.box, pants, identity)
        rigEntities[LEFT_ARM] = assets.addRenderable(scene, assets.box, tunic, identity)
        rigEntities[RIGHT_ARM] = assets.addRenderable(scene, assets.box, tunic, identity)
        rigEntities[PICK_HANDLE] = assets.addRenderable(scene, assets.box, wood, identity)
        rigEntities[PICK_HEAD] = assets.addRenderable(scene, assets.box, pickHeadInstance, identity)
    }

    /** A bought pick visibly changes on the model. */
    fun setPickTint(tier: Int) {
        val tint = Theme.PICK_TINTS[tier.coerceIn(0, Theme.PICK_TINTS.size - 1)]
        pickHeadInstance.setParameter("baseColor", tint.r, tint.g, tint.b)
    }

    fun update(player: Player, alpha: Float, dt: Float) {
        clock += dt
        val x = lerp(player.prevX, player.x, alpha)
        val z = lerp(player.prevZ, player.z, alpha)
        val facing = lerp(player.prevFacing, player.facing, alpha)
        val y = WorldLayout.groundHeight(x, z)

        var bob = 0f
        var leftLeg = 0f; var rightLeg = 0f
        var leftArm = 0f; var rightArm = 0f

        when (player.animState) {
            AnimState.IDLE -> bob = sin(clock * 2.2f) * 0.012f
            AnimState.WALK -> {
                val phase = lerp(player.prevWalkPhase, player.walkPhase, alpha)
                rightLeg = sin(phase) * 0.62f
                leftLeg = -rightLeg
                rightArm = -sin(phase) * 0.5f
                leftArm = sin(phase) * 0.5f
                bob = abs(sin(phase)) * 0.05f
            }
            AnimState.SWING -> {
                val t = (lerp(player.prevSwingTime, player.swingTime, alpha) / PlayerConfig.SWING_SECONDS) % 1f
                rightArm = swingPose(t)
                leftArm = 0.25f
            }
        }

        Transforms.rootInto(root, x, y + bob, z, facing)
        composeStatic(TORSO, 0f, 0.55f, 0f, 0.52f, 0.62f, 0.32f)
        composeStatic(APRON, 0f, 0.62f, 0.18f, 0.34f, 0.46f, 0.06f)
        composeStatic(HEAD, 0f, 1.17f, 0f, 0.38f, 0.36f, 0.38f)
        composeLimb(LEFT_LEG, root, -0.12f, 0.55f, 0f, leftLeg, 0.17f, 0.58f, 0.17f)
        composeLimb(RIGHT_LEG, root, 0.12f, 0.55f, 0f, rightLeg, 0.17f, 0.58f, 0.17f)
        composeLimb(LEFT_ARM, root, -0.33f, 1.08f, 0f, leftArm, 0.13f, 0.52f, 0.13f)
        composeLimb(RIGHT_ARM, root, 0.33f, 1.08f, 0f, rightArm, 0.13f, 0.52f, 0.13f)
        composeLimb(PICK_HANDLE, limbMatrices[RIGHT_ARM], 0f, -0.50f, 0f, -1.2f, 0.07f, 0.55f, 0.07f)
        composeLimb(PICK_HEAD, limbMatrices[PICK_HANDLE], 0f, -0.52f, 0f, 1.5f, 0.34f, 0.09f, 0.10f)

        for (i in 0 until LIMB_COUNT) tm.setTransform(tm.getInstance(rigEntities[i]), limbMatrices[i])
    }

    private fun swingPose(t: Float): Float = when {
        t < 0.35f -> lerp(0f, 2.1f, easeOut(t / 0.35f))
        t < 0.70f -> lerp(2.1f, -0.7f, easeIn((t - 0.35f) / 0.35f))
        else -> lerp(-0.7f, 0f, (t - 0.70f) / 0.30f)
    }

    private fun composeStatic(index: Int, ox: Float, oy: Float, oz: Float, sx: Float, sy: Float, sz: Float) {
        Transforms.translation(temp, ox, oy, oz)
        Transforms.multiply(temp2, root, temp)
        Transforms.scale(temp, sx, sy, sz)
        Transforms.multiply(limbMatrices[index], temp2, temp)
    }

    private fun composeLimb(index: Int, parent: FloatArray, px: Float, py: Float, pz: Float, angle: Float, w: Float, len: Float, d: Float) {
        Transforms.translation(temp, px, py, pz)
        Transforms.multiply(temp2, parent, temp)
        Transforms.rotationX(temp, angle)
        Transforms.multiply(limbMatrices[index], temp2, temp)
        Transforms.translation(temp, 0f, -len, 0f)
        Transforms.multiply(temp2, limbMatrices[index], temp)
        Transforms.scale(temp, w, len, d)
        Transforms.multiply(limbMatrices[index], temp2, temp)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)
    private fun easeIn(t: Float) = t * t

    private companion object {
        const val LIMB_COUNT = 9
        const val TORSO = 0; const val APRON = 1; const val HEAD = 2
        const val LEFT_LEG = 3; const val RIGHT_LEG = 4
        const val LEFT_ARM = 5; const val RIGHT_ARM = 6
        const val PICK_HANDLE = 7; const val PICK_HEAD = 8
    }
}

class WorldRenderer(private val engine: Engine, private val game: GameState) {

    val scene: Scene = engine.createScene()
    private val assets = AssetFactory(engine)
    val cameraRig = CameraRig(engine)
    val camera: Camera get() = cameraRig.camera
    private val playerRig = PlayerRig(engine, scene, assets)

    private val sunEntity: Int = EntityManager.get().create()
    private var indirectLight: IndirectLight? = null
    private var sky: Skybox? = null

    private val rockCount = game.rocks.size
    private val rockEntities = IntArray(rockCount)
    private val rockBase = Array(rockCount) { FloatArray(16) }
    private val rockParams = Array(rockCount) { FloatArray(5) }
    private val rockVisible = BooleanArray(rockCount)
    private val rockFlinch = FloatArray(rockCount)
    private val flinchScratch = FloatArray(16)

    private var binCrateEntity = 0
    private var binLidEntity = 0
    private var binVisible = false

    init {
        buildLights()
        buildTerrain()
        buildCliffs()
        buildTrees()
        buildRocks()
        buildTradePost()
        buildBin()
    }

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

        val sh = FloatArray(27)
        sh[0] = Theme.AMBIENT_SKY.r; sh[1] = Theme.AMBIENT_SKY.g; sh[2] = Theme.AMBIENT_SKY.b
        // Explicit intensity: the default (30_000 lux) over-brightens the ambient
        // fill; 3_000 keeps it as a subtle blue-tinted fill under the sun.
        indirectLight = IndirectLight.Builder().irradiance(3, sh).intensity(3_000f).build(engine)
        scene.indirectLight = indirectLight

        sky = Skybox.Builder().color(Theme.SKY_COLOR.r, Theme.SKY_COLOR.g, Theme.SKY_COLOR.b, 1f).build(engine)
        scene.skybox = sky
    }

    private fun buildTerrain() {
        for (t in Theme.GRASS.indices) {
            assets.addRenderable(scene, assets.terrainParts[t],
                assets.material(Theme.GRASS[t], Theme.ROUGHNESS_TERRAIN),
                Transforms.trs(0f, 0f, 0f, 1f, 1f, 1f))
        }
    }

    private fun buildCliffs() {
        val instances = Theme.CLIFF.map { assets.material(it, Theme.ROUGHNESS_PROP) }
        val rng = Random(99)
        var x = -WorldLayout.VALLEY_WIDTH / 2f - 2f
        while (x < WorldLayout.VALLEY_WIDTH / 2f + 2f) {
            val w = 4f + rng.nextFloat() * 3.5f
            val h = 7f + rng.nextFloat() * 6f
            val d = 4.5f + rng.nextFloat() * 2f
            val z = WorldLayout.NORTH_Z - d / 2f + 1.5f
            val yaw = (rng.nextFloat() - 0.5f) * 8f
            assets.addRenderable(scene, assets.box, instances[rng.nextInt(instances.size)],
                Transforms.trs(x + w / 2f, WorldLayout.groundHeight(x, z) - 0.6f, z, w, h, d, yaw))
            x += w - 0.8f
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
            rockEntities[i] = assets.addRenderable(scene, mesh,
                assets.material(rock.ore.rockTint, Theme.ROUGHNESS_ORE, Theme.METALLIC_ORE), rockBase[i])
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

    fun onViewport(width: Int, height: Int) { cameraRig.setViewport(width, height) }
    fun onRockStruck(index: Int) { rockFlinch[index] = 1f }
    fun onPickUpgraded(tier: Int) { playerRig.setPickTint(tier) }

    fun update(deltaSeconds: Float) {
        cameraRig.update(deltaSeconds)
        syncRocks(deltaSeconds)
        syncBin()
        val alpha = if (game.lastTickNanos == 0L) 0f
            else ((System.nanoTime() - game.lastTickNanos) * 1e-9f / GameState.TICK_SECONDS).coerceIn(0f, 1f)
        playerRig.update(game.player, alpha, deltaSeconds)
    }

    private fun syncBin() {
        val owned = game.binOwned
        if (owned == binVisible) return
        binVisible = owned
        if (owned) { scene.addEntity(binCrateEntity); scene.addEntity(binLidEntity) }
        else { scene.removeEntity(binCrateEntity); scene.removeEntity(binLidEntity) }
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

    fun destroy() {
        for (i in 0 until rockCount) if (!rockVisible[i]) scene.addEntity(rockEntities[i])
        if (!binVisible) { scene.addEntity(binCrateEntity); scene.addEntity(binLidEntity) }
        assets.destroy(scene)
        engine.destroyEntity(sunEntity)
        indirectLight?.let { engine.destroyIndirectLight(it) }
        sky?.let { engine.destroySkybox(it) }
        cameraRig.destroy()
        engine.destroyScene(scene)
    }
}
