package com.villageforge.graphics

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Scene
import com.villageforge.config.PlayerConfig
import com.villageforge.config.Theme
import com.villageforge.entities.AnimState
import com.villageforge.entities.Player
import com.villageforge.config.WorldLayout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Colour/feature set that separates the blacksmith from hired miners. */
class RigStyle(
    val skin: Theme.Rgb,
    val tunic: Theme.Rgb,
    val pants: Theme.Rgb,
    val hair: Theme.Rgb?,
    val cap: Theme.Rgb,
    val beard: Boolean,
    val apron: Boolean,
    val hasSack: Boolean,
    val hasPick: Boolean,
    val scale: Float,
    val hasTorch: Boolean = false,
) {
    companion object {
        fun player() = RigStyle(
            Theme.PLAYER_SKIN, Theme.PLAYER_TUNIC, Theme.PLAYER_PANTS,
            Theme.PLAYER_HAIR, Theme.MINER_CAP, beard = true, apron = true,
            hasSack = true, hasPick = true, scale = 1f,
            hasTorch = true,
        )

        fun miner(styleIndex: Int): RigStyle {
            val tunic = Theme.MINER_STYLES[styleIndex % Theme.MINER_STYLES.size]
            return RigStyle(
                Theme.PLAYER_SKIN, tunic, Theme.PLAYER_PANTS,
                null, Theme.MINER_CAP, beard = false, apron = false,
                hasSack = false, hasPick = true, scale = 0.92f,
                hasTorch = true,
            )
        }

        /** v2.2 — the townsfolk and market customers: plain folk, no tools. */
        private val VILLAGER_TUNICS = listOf(
            Theme.Rgb(0.42f, 0.34f, 0.24f), Theme.Rgb(0.36f, 0.40f, 0.26f),
            Theme.Rgb(0.48f, 0.40f, 0.52f), Theme.Rgb(0.50f, 0.36f, 0.22f),
            Theme.Rgb(0.30f, 0.36f, 0.44f), Theme.Rgb(0.55f, 0.48f, 0.30f),
        )
        private val VILLAGER_PANTS = listOf(
            Theme.Rgb(0.28f, 0.24f, 0.20f), Theme.Rgb(0.22f, 0.26f, 0.30f),
            Theme.Rgb(0.32f, 0.26f, 0.18f),
        )
        private val VILLAGER_HAIR = listOf(
            Theme.Rgb(0.20f, 0.14f, 0.07f), Theme.Rgb(0.55f, 0.42f, 0.20f),
            Theme.Rgb(0.16f, 0.16f, 0.18f), Theme.Rgb(0.75f, 0.72f, 0.66f),
        )

        fun villager(variant: Int): RigStyle {
            val v = variant.coerceAtLeast(0)
            return RigStyle(
                Theme.PLAYER_SKIN,
                VILLAGER_TUNICS[v % VILLAGER_TUNICS.size],
                VILLAGER_PANTS[v % VILLAGER_PANTS.size],
                VILLAGER_HAIR[v % VILLAGER_HAIR.size],
                Theme.PLAYER_BELT, beard = v % 3 == 0, apron = false,
                hasSack = false, hasPick = false, scale = 0.90f + 0.06f * (v % 3),
            )
        }
    }
}

/**
 * A posed humanoid built from boxes: torso, apron, head, hair/cap, beard,
 * legs, boots, arms, belt, pick, and (for the player) a growing ore sack.
 * All parts are transformed every frame with plain matrix math.
 */
class HumanoidRig(
    engine: Engine,
    scene: Scene,
    assets: AssetFactory,
    private val style: RigStyle,
) {
    private val tm = engine.transformManager
    private val rigEntities = IntArray(LIMB_COUNT)
    private val limbMatrices = Array(LIMB_COUNT) { FloatArray(16) }
    /** Joint chains carry NO scale, so attached parts (boots, pick) keep true size. */
    private val joints = Array(LIMB_COUNT) { FloatArray(16) }
    private val root = FloatArray(16)
    private val temp = FloatArray(16)
    private val temp2 = FloatArray(16)
    private val torchJoint = FloatArray(16)
    private var clock = 0f
    private lateinit var pickHeadInstance: MaterialInstance
    private lateinit var sackInstance: MaterialInstance
    private lateinit var torchFlameInstance: MaterialInstance
    /** v2.3 — 0 unlit, 1 full flame; ramps with dusk. */
    private var torchLevel = 0f
    private val flamePhase = Math.random().toFloat() * 6.28f

    init {
        val skin = assets.material(style.skin, Theme.ROUGHNESS_PROP)
        val tunic = assets.material(style.tunic, Theme.ROUGHNESS_PROP)
        val pants = assets.material(style.pants, Theme.ROUGHNESS_PROP)
        val apron = assets.material(Theme.PLAYER_APRON, Theme.ROUGHNESS_PROP)
        val hair = style.hair?.let { assets.material(it, Theme.ROUGHNESS_PROP) }
        val cap = assets.material(style.cap, Theme.ROUGHNESS_PROP)
        val belt = assets.material(Theme.PLAYER_BELT, Theme.ROUGHNESS_PROP)
        val boots = assets.material(Theme.PLAYER_BOOT, Theme.ROUGHNESS_PROP)
        val wood = assets.material(Theme.BARK, Theme.ROUGHNESS_PROP)
        pickHeadInstance = assets.material(Theme.PICK_TINTS[0], Theme.ROUGHNESS_ORE, Theme.METALLIC_ORE)
        sackInstance = assets.material(Theme.SACK, Theme.ROUGHNESS_PROP)
        torchFlameInstance = assets.material(
            Theme.TORCH_FLAME, Theme.ROUGHNESS_PROP, Theme.METALLIC_DEFAULT,
            emissive = Theme.TORCH_FLAME, emissiveStrength = 0f,
        )
        val identity = Transforms.trs(0f, 0f, 0f, 1f, 1f, 1f)
        rigEntities[TORSO] = assets.addRenderable(scene, assets.roundedBox, tunic, identity)
        rigEntities[APRON] = assets.addRenderable(scene, assets.box, apron, identity)
        rigEntities[HEAD] = assets.addRenderable(scene, assets.roundedBox, skin, identity)
        rigEntities[HAIR] = assets.addRenderable(scene, assets.box, hair ?: cap, identity)
        rigEntities[BEARD] = assets.addRenderable(scene, assets.box, hair ?: cap, identity)
        rigEntities[LEFT_LEG] = assets.addRenderable(scene, assets.box, pants, identity)
        rigEntities[RIGHT_LEG] = assets.addRenderable(scene, assets.box, pants, identity)
        rigEntities[LEFT_BOOT] = assets.addRenderable(scene, assets.box, boots, identity)
        rigEntities[RIGHT_BOOT] = assets.addRenderable(scene, assets.box, boots, identity)
        rigEntities[LEFT_ARM] = assets.addRenderable(scene, assets.box, tunic, identity)
        rigEntities[RIGHT_ARM] = assets.addRenderable(scene, assets.box, tunic, identity)
        rigEntities[BELT] = assets.addRenderable(scene, assets.box, belt, identity)
        rigEntities[PICK_HANDLE] = assets.addRenderable(scene, assets.box, wood, identity)
        rigEntities[PICK_HEAD] = assets.addRenderable(scene, assets.box, pickHeadInstance, identity)
        rigEntities[SACK] = assets.addRenderable(scene, assets.box, sackInstance, identity)
        if (style.hasTorch) {
            val wrap = assets.material(Theme.TORCH_WRAP, Theme.ROUGHNESS_PROP)
            rigEntities[TORCH_HANDLE] = assets.addRenderable(scene, assets.cyl6, wrap, identity)
            rigEntities[TORCH_FLAME] = assets.addRenderable(scene, assets.gem, torchFlameInstance, identity)
        }
    }

    /** A bought pick visibly changes on the model. */
    fun setPickTint(tier: Int) {
        val tint = Theme.PICK_TINTS[tier.coerceIn(0, Theme.PICK_TINTS.size - 1)]
        pickHeadInstance.setParameter("baseColor", tint.r, tint.g, tint.b)
    }

    /** v2.3 — dusk ramps the carried torch up from unlit to a full flame. */
    fun setTorchLevel(level: Float) { torchLevel = level }

    /** v2.2 — hides the whole rig (zero scale, far below ground) when its walker is indoors. */
    fun park() {
        val parked = Transforms.trs(0f, -100f, 0f, 0.001f, 0.001f, 0.001f)
        for (i in 0 until LIMB_COUNT) {
            if (rigEntities[i] != 0) tm.setTransform(tm.getInstance(rigEntities[i]), parked)
        }
    }

    fun update(walker: Player, alpha: Float, dt: Float, carryFill: Float) {
        clock += dt
        val x = lerp(walker.prevX, walker.x, alpha)
        val z = lerp(walker.prevZ, walker.z, alpha)
        val facing = lerp(walker.prevFacing, walker.facing, alpha)
        val y = WorldLayout.groundHeight(x, z)

        var bob = 0f
        var leftLeg = 0f; var rightLeg = 0f
        var leftArm = 0f; var rightArm = 0f
        var lean = 0f
        var headSway = 0f

        when (walker.animState) {
            AnimState.IDLE -> {
                bob = sin(clock * 2.2f) * 0.012f
                leftArm = sin(clock * 1.1f) * 0.04f
                rightArm = -sin(clock * 1.1f) * 0.04f
                headSway = sin(clock * 0.7f) * 0.05f
            }
            AnimState.WALK -> {
                val phase = lerp(walker.prevWalkPhase, walker.walkPhase, alpha)
                rightLeg = sin(phase) * 0.62f
                leftLeg = -rightLeg
                rightArm = -sin(phase) * 0.5f
                leftArm = sin(phase) * 0.5f
                bob = abs(sin(phase)) * 0.05f
                lean = 0.10f
                headSway = sin(phase) * 0.03f
            }
            AnimState.SWING -> {
                val t = (lerp(walker.prevSwingTime, walker.swingTime, alpha) / PlayerConfig.SWING_SECONDS) % 1f
                rightArm = swingPose(t)
                leftArm = 0.25f
                lean = 0.06f
            }
        }

        Transforms.rootInto(root, x, y + bob, z, facing, lean)
        if (style.scale != 1f) {
            Transforms.scale(temp, style.scale, style.scale, style.scale)
            Transforms.multiply(temp2, root, temp)
            System.arraycopy(temp2, 0, root, 0, 16)
        }

        composeStatic(TORSO, 0f, 0.55f, 0f, 0.52f, 0.62f, 0.32f)
        composeStatic(BELT, 0f, 0.33f, 0f, 0.55f, 0.08f, 0.35f)
        if (style.apron) composeStatic(APRON, 0f, 0.62f, 0.18f, 0.34f, 0.46f, 0.06f)
        else composeStatic(APRON, 0f, -2f, 0f, 0.001f, 0.001f, 0.001f)
        composeStatic(HEAD, 0f, 1.17f, 0f, 0.38f, 0.36f, 0.38f)

        if (style.hair != null) {
            composeStatic(HAIR, 0f, 1.33f, -0.02f, 0.40f, 0.13f, 0.40f)
            if (style.beard) composeStatic(BEARD, 0f, 1.02f, 0.17f, 0.30f, 0.16f, 0.08f)
            else composeStatic(BEARD, 0f, -2f, 0f, 0.001f, 0.001f, 0.001f)
        } else {
            // Miner flat cap with a small brim in front.
            composeStatic(HAIR, 0f, 1.36f, 0.02f, 0.42f, 0.09f, 0.44f)
            composeStatic(BEARD, 0f, 1.30f, 0.21f, 0.30f, 0.05f, 0.10f)
        }

        composeLimb(LEFT_LEG, root, -0.12f, 0.55f, 0f, leftLeg, 0.17f, 0.58f, 0.17f)
        composeLimb(RIGHT_LEG, root, 0.12f, 0.55f, 0f, rightLeg, 0.17f, 0.58f, 0.17f)
        // Boots sit at the leg tips, counter-rotated so they stay level —
        // attached to the JOINT so they keep their true size (v2.1 fix).
        composeLimb(LEFT_BOOT, joints[LEFT_LEG], 0f, -0.52f, 0.05f, -leftLeg, 0.20f, 0.13f, 0.30f)
        composeLimb(RIGHT_BOOT, joints[RIGHT_LEG], 0f, -0.52f, 0.05f, -rightLeg, 0.20f, 0.13f, 0.30f)
        composeLimb(LEFT_ARM, root, -0.33f, 1.08f, 0f, leftArm, 0.13f, 0.52f, 0.13f)
        composeLimb(RIGHT_ARM, root, 0.33f, 1.08f, 0f, rightArm, 0.13f, 0.52f, 0.13f)
        // The pick hangs from the hand (arm tip) at a fixed grip angle; the
        // handle is CENTERED on the hand so it pokes above and below it.
        if (style.hasPick) composePick(rightArm) else {
            composeStatic(PICK_HANDLE, 0f, -2f, 0f, 0.001f, 0.001f, 0.001f)
            composeStatic(PICK_HEAD, 0f, -2f, 0f, 0.001f, 0.001f, 0.001f)
        }

        // Ore sack on the back grows with how full the backpack is.
        if (style.hasSack) {
            val fill = carryFill.coerceIn(0f, 1f)
            if (fill > 0.04f) {
                val s = 0.30f + 0.70f * fill
                composeStatic(SACK, 0f, 0.78f, -0.26f, 0.42f * s, 0.50f * s, 0.26f * s)
            } else {
                composeStatic(SACK, 0f, -2f, 0f, 0.001f, 0.001f, 0.001f)
            }
        } else {
            composeStatic(SACK, 0f, -2f, 0f, 0.001f, 0.001f, 0.001f)
        }

        // v2.3 — the torch rides the LEFT hand while the pick holds the right.
        if (style.hasTorch && torchLevel > 0.02f) composeTorch(leftArm) else {
            composeStatic(TORCH_HANDLE, 0f, -2f, 0f, 0.001f, 0.001f, 0.001f)
            composeStatic(TORCH_FLAME, 0f, -2f, 0f, 0.001f, 0.001f, 0.001f)
        }

        // Tiny head sway sells the walk cycle.
        if (headSway != 0f) {
            Transforms.translation(temp, headSway * 0.15f, 0f, 0f)
            Transforms.multiply(temp2, limbMatrices[HEAD], temp)
            System.arraycopy(temp2, 0, limbMatrices[HEAD], 0, 16)
        }

        for (i in 0 until LIMB_COUNT) {
            if (rigEntities[i] != 0) tm.setTransform(tm.getInstance(rigEntities[i]), limbMatrices[i])
        }
    }

    private fun swingPose(t: Float): Float = when {
        t < 0.35f -> lerp(0f, 2.1f, easeOut(t / 0.35f))          // raise behind the shoulder
        t < 0.70f -> lerp(2.1f, -0.85f, easeIn((t - 0.35f) / 0.35f)) // overhead chop into the swing plane
        else -> lerp(-0.85f, 0f, (t - 0.70f) / 0.30f)           // recover
    }

    /**
     * Pickaxe assembly (v2.1 fix). The handle rotates with the arm plus a
     * fixed +0.35 rad grip, so the windup lifts the head up-back and the
     * strike sweeps it DOWN into the ground in front of the smith — the
     * classic overhead mining chop.
     */
    private fun composePick(rightArm: Float) {
        // Handle joint at the hand (arm tip, slightly forward).
        Transforms.translation(temp, 0f, -0.50f, 0.03f)
        Transforms.multiply(temp2, joints[RIGHT_ARM], temp)
        Transforms.rotationX(temp, rightArm + PICK_GRIP_ANGLE)
        Transforms.multiply(joints[PICK_HANDLE], temp2, temp)
        // Handle box centered on the hand: spans [-HANDLE_LEN/2, +HANDLE_LEN/2].
        Transforms.translation(temp, 0f, HANDLE_LEN / 2f, 0f)
        Transforms.multiply(temp2, joints[PICK_HANDLE], temp)
        Transforms.scale(temp, 0.055f, HANDLE_LEN, 0.055f)
        Transforms.multiply(limbMatrices[PICK_HANDLE], temp2, temp)

        // Head joint near the handle's striking tip.
        Transforms.translation(temp, 0f, -HANDLE_LEN / 2f + 0.06f, 0f)
        Transforms.multiply(temp2, joints[PICK_HANDLE], temp)
        Transforms.rotationX(temp, PICK_HEAD_ANGLE)
        Transforms.multiply(joints[PICK_HEAD], temp2, temp)
        Transforms.translation(temp, 0f, -0.09f, 0f)
        Transforms.multiply(temp2, joints[PICK_HEAD], temp)
        Transforms.scale(temp, 0.13f, 0.42f, 0.12f)
        Transforms.multiply(limbMatrices[PICK_HEAD], temp2, temp)
    }

    /**
     * v2.3 — the carried torch. The handle is counter-rotated against the
     * arm swing so it stays upright while walking, and the flame gem
     * flickers on its own clock (phase offset per rig).
     */
    private fun composeTorch(leftArm: Float) {
        // Joint at the left hand (arm tip, slightly forward), counter-rotated
        // so the torch stays vertical while the arm swings.
        Transforms.translation(temp, 0f, -0.50f, 0.05f)
        Transforms.multiply(temp2, joints[LEFT_ARM], temp)
        Transforms.rotationX(temp, -leftArm)
        Transforms.multiply(torchJoint, temp2, temp)

        // Handle: 6-sided post from the hand up.
        Transforms.translation(temp, 0f, 0.0f, 0f)
        Transforms.multiply(temp2, torchJoint, temp)
        Transforms.scale(temp, 0.055f, 0.52f, 0.055f)
        Transforms.multiply(limbMatrices[TORCH_HANDLE], temp2, temp)

        // Flame: gem sits on the handle top and breathes.
        val flick = 0.85f + 0.15f * sin(clock * 11f + flamePhase) + 0.07f * sin(clock * 27f + flamePhase * 2f)
        Transforms.translation(temp, 0f, 0.50f + 0.06f * flick, 0f)
        Transforms.multiply(temp2, torchJoint, temp)
        Transforms.scale(temp, 0.085f * flick, 0.26f * flick, 0.085f * flick)
        Transforms.multiply(limbMatrices[TORCH_FLAME], temp2, temp)
        torchFlameInstance.setParameter("emissiveStrength", torchLevel * (3.4f + 2.6f * flick))
    }

    private fun composeStatic(index: Int, ox: Float, oy: Float, oz: Float, sx: Float, sy: Float, sz: Float) {
        Transforms.translation(temp, ox, oy, oz)
        Transforms.multiply(temp2, root, temp)
        System.arraycopy(temp2, 0, joints[index], 0, 16)
        Transforms.scale(temp, sx, sy, sz)
        Transforms.multiply(limbMatrices[index], joints[index], temp)
    }

    private fun composeLimb(index: Int, parentJoint: FloatArray, px: Float, py: Float, pz: Float, angle: Float, w: Float, len: Float, d: Float) {
        Transforms.translation(temp, px, py, pz)
        Transforms.multiply(temp2, parentJoint, temp)
        Transforms.rotationX(temp, angle)
        System.arraycopy(temp2, 0, joints[index], 0, 16)
        Transforms.multiply(joints[index], temp2, temp)
        Transforms.translation(temp, 0f, -len, 0f)
        Transforms.multiply(temp2, joints[index], temp)
        Transforms.scale(temp, w, len, d)
        Transforms.multiply(limbMatrices[index], temp2, temp)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)
    private fun easeIn(t: Float) = t * t

    private companion object {
        const val LIMB_COUNT = 17
        const val TORSO = 0; const val APRON = 1; const val HEAD = 2
        const val HAIR = 3; const val BEARD = 4
        const val LEFT_LEG = 5; const val RIGHT_LEG = 6
        const val LEFT_BOOT = 7; const val RIGHT_BOOT = 8
        const val LEFT_ARM = 9; const val RIGHT_ARM = 10
        const val BELT = 11; const val PICK_HANDLE = 12; const val PICK_HEAD = 13
        const val SACK = 14
        const val TORCH_HANDLE = 15; const val TORCH_FLAME = 16

        const val HANDLE_LEN = 0.88f
        /** Fixed wrist grip: keeps the pick's arc in the swing plane. */
        const val PICK_GRIP_ANGLE = 0.35f
        /** Head bar sits across the handle's tip. */
        const val PICK_HEAD_ANGLE = 1.5f
    }
}
