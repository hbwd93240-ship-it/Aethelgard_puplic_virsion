package com.aethelgard.survival.core.camera

import com.aethelgard.survival.core.combat.UltimatePhase
import com.aethelgard.survival.core.entities.EnemyAiState
import com.aethelgard.survival.core.entities.EnemyController
import com.aethelgard.survival.core.player.PlayerCharacter
import com.aethelgard.survival.core.world.WorldManager
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class CameraMode(val label: String) {
    THIRD_PERSON("3RD PERSON"),
    FIRST_PERSON("1ST PERSON")
}

/**
 * Manages Third-Person (over-the-shoulder smooth follow with terrain collision awareness),
 * First-Person camera perspectives, and Burst-to-Power style Lock-On Target Tracking
 * for AethelgardSurvival.
 */
class FollowCameraController(
    val camera: PerspectiveCamera,
    private val worldManager: WorldManager
) {
    var cameraMode: CameraMode = CameraMode.THIRD_PERSON
        private set

    /** Currently locked enemy target, or null when free-look camera is active. */
    var lockedTarget: EnemyController? = null
        private set

    /** Maximum distance before an active target lock automatically breaks. */
    val maxLockOnMaintainDistance: Float = 34f

    /** Camera yaw angle in degrees (0 = looking toward -Z from +Z). */
    var yawDegrees: Float = 0f

    /** Camera pitch angle in degrees (+ = looking down from above in 3P, or down in 1P). */
    var pitchDegrees: Float = 16f

    var thirdPersonDistance: Float = 4.4f
    var thirdPersonHeightOffset: Float = 1.52f
    var shoulderOffsetRight: Float = 0.42f

    private val smoothedPivot = Vector3()
    private val desiredCamPos = Vector3()
    private val lookTarget = Vector3()
    private val smoothedFocusTarget = Vector3()
    private var pivotInitialized = false
    private var focusInitialized = false

    // Camera impact shake state
    private var shakeTimer: Float = 0f
    private var shakeDuration: Float = 0.01f
    private var shakeIntensity: Float = 0f
    private var shakeTimePhase: Float = 0f

    // Smooth cinematic framing state for DIVINE ANNIHILATION ultimate ability
    var isUltimateCinematicActive: Boolean = false
        private set
    var ultimateCinematicBlend: Float = 0f
        private set
    private var activeUltimatePhase: UltimatePhase = UltimatePhase.INACTIVE
    private var activeUltimatePhaseProgress: Float = 0f

    val isLockedOn: Boolean
        get() = isTargetValid(lockedTarget)

    fun setUltimateCinematicState(active: Boolean, phase: UltimatePhase, phaseProgress: Float) {
        isUltimateCinematicActive = active
        activeUltimatePhase = phase
        activeUltimatePhaseProgress = phaseProgress.coerceIn(0f, 1f)
    }

    fun triggerCameraShake(intensity: Float, duration: Float) {
        if (intensity >= shakeIntensity || shakeTimer <= 0f) {
            shakeIntensity = intensity
            shakeDuration = duration.coerceAtLeast(0.02f)
            shakeTimer = shakeDuration
        }
    }

    fun setLockOnTarget(target: EnemyController?) {
        lockedTarget = if (isTargetValid(target)) target else null
        focusInitialized = false
    }

    fun clearLockOn() {
        lockedTarget = null
        focusInitialized = false
    }

    fun isTargetValid(target: EnemyController?): Boolean {
        return target != null &&
            target.entity.isActive &&
            target.aiState != EnemyAiState.DEAD &&
            target.health > 0f
    }

    fun toggleCameraMode(): CameraMode {
        cameraMode = if (cameraMode == CameraMode.THIRD_PERSON) {
            pitchDegrees = pitchDegrees.coerceIn(-45f, 45f)
            CameraMode.FIRST_PERSON
        } else {
            pitchDegrees = pitchDegrees.coerceIn(-25f, 58f)
            CameraMode.THIRD_PERSON
        }
        return cameraMode
    }

    fun applyLookDelta(deltaYawDeg: Float, deltaPitchDeg: Float) {
        if (isLockedOn) {
            // While locked on, allow pitch adjustments so vertical camera control is never locked
            pitchDegrees = if (cameraMode == CameraMode.THIRD_PERSON) {
                (pitchDegrees + deltaPitchDeg).coerceIn(-22f, 62f)
            } else {
                (pitchDegrees + deltaPitchDeg).coerceIn(-68f, 68f)
            }
            return
        }
        yawDegrees = (yawDegrees + deltaYawDeg) % 360f
        pitchDegrees = if (cameraMode == CameraMode.THIRD_PERSON) {
            (pitchDegrees + deltaPitchDeg).coerceIn(-22f, 62f)
        } else {
            (pitchDegrees + deltaPitchDeg).coerceIn(-68f, 68f)
        }
    }

    fun resetToDefault(player: PlayerCharacter) {
        clearLockOn()
        shakeTimer = 0f
        shakeIntensity = 0f
        isUltimateCinematicActive = false
        ultimateCinematicBlend = 0f
        activeUltimatePhase = UltimatePhase.INACTIVE
        activeUltimatePhaseProgress = 0f
        camera.fieldOfView = 64f
        yawDegrees = 0f
        pitchDegrees = 16f
        smoothedPivot.set(
            player.position.x,
            player.position.y + thirdPersonHeightOffset,
            player.position.z
        )
        pivotInitialized = true
    }

    fun update(delta: Float, player: PlayerCharacter) {
        // 1. Validate active Lock-On target (auto-unlock if enemy died, disappeared, or out of range)
        val activeTarget = lockedTarget
        if (activeTarget != null) {
            val dx = activeTarget.entity.position.x - player.position.x
            val dz = activeTarget.entity.position.z - player.position.z
            val distSq = dx * dx + dz * dz
            if (!isTargetValid(activeTarget) || distSq > maxLockOnMaintainDistance * maxLockOnMaintainDistance) {
                clearLockOn()
            }
        }

        // 2. Smoothly update player pivot position
        val targetPivotY = player.position.y + thirdPersonHeightOffset
        if (!pivotInitialized) {
            smoothedPivot.set(player.position.x, targetPivotY, player.position.z)
            pivotInitialized = true
        } else {
            val followAlpha = (delta * 14f).coerceIn(0.05f, 1f)
            smoothedPivot.x = MathUtils.lerp(smoothedPivot.x, player.position.x, followAlpha)
            smoothedPivot.y = MathUtils.lerp(smoothedPivot.y, targetPivotY, followAlpha)
            smoothedPivot.z = MathUtils.lerp(smoothedPivot.z, player.position.z, followAlpha)
        }

        // 3. If Locked On, smoothly interpolate yawDegrees & pitchDegrees to track the enemy
        val validTarget = lockedTarget
        if (validTarget != null) {
            val enemyChestX = validTarget.entity.position.x
            val enemyChestY = validTarget.entity.position.y + 1.25f
            val enemyChestZ = validTarget.entity.position.z

            if (!focusInitialized) {
                smoothedFocusTarget.set(enemyChestX, enemyChestY, enemyChestZ)
                focusInitialized = true
            } else {
                val focusAlpha = (delta * 12f).coerceIn(0.05f, 1f)
                smoothedFocusTarget.lerp(Vector3(enemyChestX, enemyChestY, enemyChestZ), focusAlpha)
            }

            val toEnemyX = smoothedFocusTarget.x - smoothedPivot.x
            val toEnemyY = smoothedFocusTarget.y - smoothedPivot.y
            val toEnemyZ = smoothedFocusTarget.z - smoothedPivot.z
            val horizDist = sqrt(toEnemyX * toEnemyX + toEnemyZ * toEnemyZ).coerceAtLeast(0.05f)

            // Since forwardX = -sin(yaw) and forwardZ = -cos(yaw),
            // yaw = atan2(-toEnemyX, -toEnemyZ) points the camera forward vector directly at the enemy
            val desiredYawDeg = Math.toDegrees(atan2((-toEnemyX).toDouble(), (-toEnemyZ).toDouble())).toFloat()
            val rawPitchDeg = Math.toDegrees(atan2((-toEnemyY).toDouble(), horizDist.toDouble())).toFloat()
            // Add a comfortable elevated over-the-shoulder angle in 3rd-person so both player and enemy stay visible
            val elevationBias = if (cameraMode == CameraMode.THIRD_PERSON) 14f else 0f
            val desiredPitchDeg = (rawPitchDeg + elevationBias).coerceIn(-18f, 46f)

            val trackAlpha = (delta * 8.5f).coerceIn(0.04f, 1f)
            yawDegrees = lerpAngleDegrees(yawDegrees, desiredYawDeg, trackAlpha)
            pitchDegrees = MathUtils.lerp(pitchDegrees, desiredPitchDeg, trackAlpha)
        }

        val yawRad = Math.toRadians(yawDegrees.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDegrees.toDouble()).toFloat()

        // Forward look direction vector
        val forwardX = -sin(yawRad) * cos(pitchRad)
        val forwardY = -sin(pitchRad)
        val forwardZ = -cos(yawRad) * cos(pitchRad)

        // Right vector on XZ plane
        val rightX = cos(yawRad)
        val rightZ = -sin(yawRad)

        // Compute decaying impact camera shake offset
        var shakeOffsetX = 0f
        var shakeOffsetY = 0f
        var shakeOffsetZ = 0f
        if (shakeTimer > 0f) {
            shakeTimer = (shakeTimer - delta).coerceAtLeast(0f)
            shakeTimePhase += delta * 62f
            val decay = (shakeTimer / shakeDuration).coerceIn(0f, 1f)
            val amp = shakeIntensity * decay
            shakeOffsetX = (sin(shakeTimePhase * 1.7f) * rightX) * amp
            shakeOffsetY = cos(shakeTimePhase * 2.3f) * amp * 0.85f
            shakeOffsetZ = (sin(shakeTimePhase * 1.7f) * rightZ) * amp
            if (shakeTimer <= 0f) {
                shakeIntensity = 0f
            }
        }

        // Smoothly interpolate ultimate cinematic framing blend (0..1)
        val targetUltBlend = if (isUltimateCinematicActive) 1f else 0f
        ultimateCinematicBlend = MathUtils.lerp(ultimateCinematicBlend, targetUltBlend, (delta * 5.5f).coerceIn(0.04f, 1f))
        if (!isUltimateCinematicActive && ultimateCinematicBlend < 0.01f) {
            ultimateCinematicBlend = 0f
        }

        // Dynamic FOV widening during Phase 4 Convergence and Phase 5 Annihilation beam release
        val extraFov = when (activeUltimatePhase) {
            UltimatePhase.PHASE_4_MERGING -> ultimateCinematicBlend * 5.5f
            UltimatePhase.PHASE_5_ANNIHILATION -> ultimateCinematicBlend * 8.5f
            else -> ultimateCinematicBlend * 3.5f
        }
        camera.fieldOfView = 64f + extraFov

        if (cameraMode == CameraMode.THIRD_PERSON) {
            // Over-the-shoulder pivot offset (slightly wider when locked on or casting ultimate so both spheres + beam stay visible)
            val baseShoulderOffset = if (validTarget != null) shoulderOffsetRight * 1.35f else shoulderOffsetRight
            val activeShoulderOffset = MathUtils.lerp(baseShoulderOffset, shoulderOffsetRight * 1.65f, ultimateCinematicBlend)
            val shoulderPivotX = smoothedPivot.x + rightX * activeShoulderOffset
            val shoulderPivotY = smoothedPivot.y + (if (validTarget != null) 0.18f else 0f) + ultimateCinematicBlend * 0.52f
            val shoulderPivotZ = smoothedPivot.z + rightZ * activeShoulderOffset

            val baseDist = if (validTarget != null) thirdPersonDistance + 0.55f else thirdPersonDistance
            val sprintDist = if (player.isSprinting) baseDist + 0.45f else baseDist
            val dist = sprintDist + ultimateCinematicBlend * 1.45f

            desiredCamPos.set(
                shoulderPivotX - forwardX * dist + shakeOffsetX,
                shoulderPivotY - forwardY * dist + shakeOffsetY,
                shoulderPivotZ - forwardZ * dist + shakeOffsetZ
            )

            // Terrain collision awareness: keep camera above ground height
            val groundAtCam = worldManager.getTerrainHeight(desiredCamPos.x, desiredCamPos.z)
            if (desiredCamPos.y < groundAtCam + 0.65f) {
                desiredCamPos.y = groundAtCam + 0.65f
            }

            camera.position.set(desiredCamPos)
            if (validTarget != null && focusInitialized) {
                // Blend look target 72% on enemy chest and 28% on player pivot so both stay framed
                lookTarget.set(
                    smoothedFocusTarget.x * 0.72f + shoulderPivotX * 0.28f + shakeOffsetX * 0.4f,
                    smoothedFocusTarget.y * 0.72f + shoulderPivotY * 0.28f + shakeOffsetY * 0.4f,
                    smoothedFocusTarget.z * 0.72f + shoulderPivotZ * 0.28f + shakeOffsetZ * 0.4f
                )
            } else {
                lookTarget.set(
                    shoulderPivotX + forwardX * 6f + shakeOffsetX * 0.4f,
                    shoulderPivotY + forwardY * 6f + ultimateCinematicBlend * 0.35f + shakeOffsetY * 0.4f,
                    shoulderPivotZ + forwardZ * 6f + shakeOffsetZ * 0.4f
                )
            }
            camera.up.set(0f, 1f, 0f)
            camera.lookAt(lookTarget)
        } else {
            // First-person eye-level mode
            val eyeX = player.position.x + shakeOffsetX * 0.5f
            val eyeY = player.position.y + player.eyeHeight + ultimateCinematicBlend * 0.12f + shakeOffsetY * 0.5f
            val eyeZ = player.position.z + shakeOffsetZ * 0.5f

            camera.position.set(eyeX, eyeY, eyeZ)
            lookTarget.set(
                eyeX + forwardX * 10f,
                eyeY + forwardY * 10f,
                eyeZ + forwardZ * 10f
            )
            camera.up.set(0f, 1f, 0f)
            camera.lookAt(lookTarget)
        }

        camera.update()
    }

    private fun lerpAngleDegrees(from: Float, to: Float, alpha: Float): Float {
        var diff = (to - from) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        return from + diff * alpha
    }
}
