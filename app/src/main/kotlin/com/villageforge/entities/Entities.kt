package com.villageforge.entities

import com.villageforge.config.Ore
import com.villageforge.config.PlayerConfig
import com.villageforge.config.WorldLayout
import kotlin.math.atan2
import kotlin.math.hypot

enum class AnimState { IDLE, WALK, SWING }

class Rock(val index: Int, val ore: Ore, val x: Float, val z: Float) {
    var alive = true
    var hp = ore.rockHp
    var respawnTimer = 0f
    fun breakRock() { alive = false; hp = 0; respawnTimer = ore.respawnSeconds.toFloat() }
    fun reset() { alive = true; hp = ore.rockHp }
}

/**
 * Shared walk/turn/pose state for the blacksmith and every hired miner.
 * The simulation moves it; the renderer interpolates prev/current each frame.
 */
class Player {
    var x = WorldLayout.SPAWN_X
    var z = WorldLayout.SPAWN_Z
    var prevX = x
    var prevZ = z
    var facing = Math.PI.toFloat() / 4f
    var prevFacing = facing
    var animState = AnimState.IDLE
    var walkPhase = 0f
    var prevWalkPhase = 0f
    var swingTime = 0f
    var prevSwingTime = 0f
    var faceTargetX = Float.NaN
    var faceTargetZ = Float.NaN
    var moveSpeedBonus = 0f

    private var targetX = Float.NaN
    private var targetZ = Float.NaN

    /** Route legs queued by [setRoute]; walked one at a time before the final goal. */
    private val routeLegs = ArrayDeque<Pair<Float, Float>>()
    private var finalX = Float.NaN
    private var finalZ = Float.NaN

    /** Moving now, or mid-route between legs (never flickers false between waypoints). */
    val isMoving: Boolean get() = !targetX.isNaN() || routeLegs.isNotEmpty() || !finalX.isNaN()

    fun setTarget(x: Float, z: Float) {
        routeLegs.clear()
        finalX = Float.NaN; finalZ = Float.NaN
        targetX = x; targetZ = z
    }

    /** Walks `legs` first, then settles at the final goal — keeps walkers on trails. */
    fun setRoute(legs: List<Pair<Float, Float>>, goalX: Float, goalZ: Float) {
        routeLegs.clear()
        routeLegs.addAll(legs)
        finalX = goalX; finalZ = goalZ
        if (routeLegs.isEmpty()) { targetX = goalX; targetZ = goalZ }
        else {
            val first = routeLegs.removeFirst()
            targetX = first.first; targetZ = first.second
        }
    }

    /** Walks the direct line unless the zones call for trail waypoints. */
    fun setRoutedTarget(goalX: Float, goalZ: Float) {
        setRoute(com.villageforge.config.WorldLayout.routeTo(x, z, goalX, goalZ), goalX, goalZ)
    }

    fun clearTarget() {
        targetX = Float.NaN; targetZ = Float.NaN
        routeLegs.clear()
        finalX = Float.NaN; finalZ = Float.NaN
    }

    fun distanceTo(px: Float, pz: Float): Float = hypot(px - x, pz - z)

    fun update(dt: Float) {
        prevX = x; prevZ = z; prevFacing = facing
        prevWalkPhase = walkPhase; prevSwingTime = swingTime

        if (animState == AnimState.SWING) {
            faceToward(faceTargetX, faceTargetZ, dt)
            return
        }

        if (targetX.isNaN()) {
            // A finished leg may hand off to the next one or the final goal.
            val nextLeg = routeLegs.removeFirstOrNull()
            if (nextLeg != null) {
                targetX = nextLeg.first; targetZ = nextLeg.second
            } else if (!finalX.isNaN()) {
                targetX = finalX; targetZ = finalZ
                finalX = Float.NaN; finalZ = Float.NaN
            } else {
                animState = AnimState.IDLE
                return
            }
        }

        val dx = targetX - x
        val dz = targetZ - z
        val d = hypot(dx, dz)
        if (d < 0.05f) { targetX = Float.NaN; targetZ = Float.NaN; return }  // leg done; next tick continues the route

        faceToward(targetX, targetZ, dt)
        val speed = PlayerConfig.MOVE_SPEED + moveSpeedBonus
        val step = speed * dt
        if (step >= d) { x = targetX; z = targetZ; targetX = Float.NaN; targetZ = Float.NaN }
        else { x += dx / d * step; z += dz / d * step }
        walkPhase += step * PlayerConfig.WALK_PHASE_PER_UNIT
        animState = AnimState.WALK
    }

    private fun faceToward(tx: Float, tz: Float, dt: Float) {
        if (tx.isNaN()) return
        val desired = atan2(tx - x, tz - z)
        var delta = desired - facing
        val twoPi = (2.0 * Math.PI).toFloat()
        while (delta > Math.PI.toFloat()) delta -= twoPi
        while (delta < -Math.PI.toFloat()) delta += twoPi
        val maxStep = PlayerConfig.TURN_RATE * dt
        facing += delta.coerceIn(-maxStep, maxStep)
    }
}

/**
 * A hired miner. Movement/pose lives in [body]; the AI state machine driving
 * it (target rock, carrying, cooldown) lives in systems.MinerSystem.
 */
class Miner(val index: Int, val styleIndex: Int) {
    val body = Player()
}
