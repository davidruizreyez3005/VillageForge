package com.villageforge.entities

import com.villageforge.config.Ore
import com.villageforge.config.PlayerConfig
import com.villageforge.config.Role
import com.villageforge.config.Wood
import com.villageforge.config.WorldLayout
import kotlin.math.atan2
import kotlin.math.hypot

enum class AnimState { IDLE, WALK, SWING }

class Rock(
    val index: Int,
    val ore: Ore,
    val x: Float,
    val z: Float,
    val field: WorldLayout.MineField = WorldLayout.MineField.NORTH,
) {
    var alive = true
    var hp = ore.rockHp
    var respawnTimer = 0f
    fun breakRock() { alive = false; hp = 0; respawnTimer = ore.respawnSeconds.toFloat() }
    fun reset() { alive = true; hp = ore.rockHp }
}

/** A valley tree: real timber now — 5 HP, 16s respawn. */
class Tree(val index: Int, val x: Float, val z: Float) {
    var alive = true
    var hp = Wood.TREE_HP
    var respawnTimer = 0f
    fun fell() { alive = false; hp = 0; respawnTimer = Wood.TREE_RESPAWN_SECONDS.toFloat() }
    fun reset() { alive = true; hp = Wood.TREE_HP }
}

/**
 * Shared walk/turn/pose state for the blacksmith, every hired hand, the
 * townsfolk, and the customers. The simulation moves it; the renderer
 * interpolates prev/current each frame.
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
    /** v3.0 — workers walk at their own role speed, not the player's. */
    var speedOverride = Float.NaN

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
        setRoute(WorldLayout.routeTo(x, z, goalX, goalZ), goalX, goalZ)
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
        val speed = if (!speedOverride.isNaN()) speedOverride else PlayerConfig.MOVE_SPEED + moveSpeedBonus
        val step = speed * dt
        if (step >= d) { stepTo(targetX, targetZ); targetX = Float.NaN; targetZ = Float.NaN }
        else stepTo(x + dx / d * step, z + dz / d * step)
        walkPhase += step * PlayerConfig.WALK_PHASE_PER_UNIT
        animState = if (isMoving) AnimState.WALK else AnimState.IDLE
    }

    /**
     * v3.1 — the invisible barrier. Steep slopes are walls: a move that would
     * land on unwalkable ground is refused, sliding along whichever axis is
     * still open (so hugging a wall still carries you down its length). When
     * both axes are blocked the current goal is dropped — the next tick
     * either pulls the following route leg or the walker simply stops.
     */
    private fun stepTo(nx: Float, nz: Float) {
        if (WorldLayout.isWalkable(nx, nz)) { x = nx; z = nz; return }
        var slid = false
        if (WorldLayout.isWalkable(nx, z)) { x = nx; slid = true }
        if (WorldLayout.isWalkable(x, nz)) { z = nz; slid = true }
        if (slid) return
        // Fully walled in: give up on this goal, keep the route queue alive.
        targetX = Float.NaN; targetZ = Float.NaN
        if (finalX.isNaN() && routeLegs.isEmpty()) animState = AnimState.IDLE
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
 * A hired hand. Movement/pose lives in [body]; the role AI driving it
 * (targets, carrying, wages pause) lives in systems.CrewSystem.
 */
class Worker(val index: Int, val role: Role, val styleIndex: Int) {
    val body = Player()
    /** Downs tools while the payroll is short; resumed when wages clear. */
    var paused = false
}
