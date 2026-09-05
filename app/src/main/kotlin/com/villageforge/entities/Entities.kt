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

    val isMoving: Boolean get() = !targetX.isNaN()

    fun setTarget(x: Float, z: Float) { targetX = x; targetZ = z }

    fun clearTarget() { targetX = Float.NaN; targetZ = Float.NaN }

    fun distanceTo(px: Float, pz: Float): Float = hypot(px - x, pz - z)

    fun update(dt: Float) {
        prevX = x; prevZ = z; prevFacing = facing
        prevWalkPhase = walkPhase; prevSwingTime = swingTime

        if (animState == AnimState.SWING) {
            faceToward(faceTargetX, faceTargetZ, dt)
            return
        }

        if (targetX.isNaN()) { animState = AnimState.IDLE; return }

        val dx = targetX - x
        val dz = targetZ - z
        val d = hypot(dx, dz)
        if (d < 0.05f) { clearTarget(); animState = AnimState.IDLE; return }

        faceToward(targetX, targetZ, dt)
        val speed = PlayerConfig.MOVE_SPEED + moveSpeedBonus
        val step = speed * dt
        if (step >= d) { x = targetX; z = targetZ; clearTarget() }
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
