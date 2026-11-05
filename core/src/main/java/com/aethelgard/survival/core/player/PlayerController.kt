package com.aethelgard.survival.core.player

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.camera.CameraMode
import com.aethelgard.survival.core.camera.FollowCameraController
import com.aethelgard.survival.core.combat.CombatSystem
import com.aethelgard.survival.core.input.TouchInputController
import com.aethelgard.survival.core.world.WorldManager
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.MathUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Handles player movement physics, camera-relative locomotion, walk/run/sprint transitions,
 * jumping, directional Dodge/Dash bursts, integration with CombatSystem actions,
 * terrain height clamping, obstacle collision sliding, and footstep audio timing.
 */
class PlayerController(
    private val player: PlayerCharacter,
    private val worldManager: WorldManager,
    private val audioManager: AudioManager
) {
    private val walkMaxSpeed = 4.6f
    private val runMaxSpeed = 8.4f
    private val sprintMaxSpeed = 13.4f
    private val dodgeDashSpeed = 18.8f
    private val jumpImpulse = 9.6f
    private val gravity = -22.5f
    private val worldBoundaryRadius = 145f

    private var footstepTimer = 0f

    fun update(
        delta: Float,
        input: TouchInputController,
        cameraController: FollowCameraController,
        combatSystem: CombatSystem,
        onToggleLockOn: () -> Unit = {}
    ) {
        // Tick invulnerability and hit-stun timers
        if (player.invulnerabilityTimer > 0f) {
            player.invulnerabilityTimer = (player.invulnerabilityTimer - delta).coerceAtLeast(0f)
        }
        if (player.hitStunTimer > 0f) {
            player.hitStunTimer = (player.hitStunTimer - delta).coerceAtLeast(0f)
        }
        // Passive health regeneration when out of hit-stun
        if (player.hitStunTimer <= 0f && player.health < player.maxHealth) {
            player.health = (player.health + delta * 3.5f).coerceAtMost(player.maxHealth)
        }

        // Combine touch joystick with optional hardware keyboard (WASD / Arrows)
        var moveX = input.moveX
        var moveY = input.moveY

        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) moveX -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) moveX += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) moveY += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) moveY -= 1f

        val rawMag = sqrt(moveX * moveX + moveY * moveY)
        val inputMagnitude = rawMag.coerceAtMost(1f)
        if (rawMag > 1f) {
            moveX /= rawMag
            moveY /= rawMag
        }

        // Feed live movement inputs to FistsCombatController for contextual Finisher & Heavy attack selection
        combatSystem.fistsCombatController.liveMoveInputX = moveX
        combatSystem.fistsCombatController.liveMoveInputY = moveY

        // Check Dodge / Dash request
        if (input.consumeDodgeRequest() || Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            combatSystem.triggerDodgeDash(player, moveX, moveY, cameraController)
        }

        // Optional keyboard shortcuts for combat & lock-on testing
        if (Gdx.input.isKeyJustPressed(Input.Keys.L) || Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            onToggleLockOn()
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.J)) {
            combatSystem.triggerLightAttack(player, cameraController)
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.K)) {
            combatSystem.triggerHeavyAttack(player, cameraController)
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            combatSystem.toggleWeaponStance(player)
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R) || Gdx.input.isKeyJustPressed(Input.Keys.U)) {
            combatSystem.triggerUltimateAbility(player, cameraController)
        }

        // Sprint state (from HUD button or Shift key)
        val sprintRequested = input.isSprintActive || Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
        player.isSprinting = sprintRequested && inputMagnitude > 0.15f && !combatSystem.isChargingEnergy && !combatSystem.isAttacking

        // Determine horizontal movement basis:
        // - When Lock-On is active: orient movement relative to the selected target
        //   (forward = toward target, backward = away from target, left/right = circle around target)
        // - When unlocked: orient movement relative to camera yaw
        val lockedTarget = if (cameraController.isLockedOn) cameraController.lockedTarget else null
        var forwardX: Float
        var forwardZ: Float
        var rightX: Float
        var rightZ: Float
        var effectiveMoveY = moveY
        var lockTargetYawDeg: Float? = null

        if (lockedTarget != null) {
            val dx = lockedTarget.entity.position.x - player.position.x
            val dz = lockedTarget.entity.position.z - player.position.z
            val distToTarget = sqrt(dx * dx + dz * dz)
            if (distToTarget > 0.05f) {
                forwardX = dx / distToTarget
                forwardZ = dz / distToTarget
            } else {
                val camYawRad = Math.toRadians(cameraController.yawDegrees.toDouble()).toFloat()
                forwardX = -sin(camYawRad)
                forwardZ = -cos(camYawRad)
            }
            // Right vector perpendicular to target direction for smooth circular strafing
            rightX = -forwardZ
            rightZ = forwardX
            lockTargetYawDeg = Math.toDegrees(atan2(forwardX.toDouble(), forwardZ.toDouble())).toFloat()

            // Target orientation maintains smooth strafing around locked enemy
        } else {
            val camYawRad = Math.toRadians(cameraController.yawDegrees.toDouble()).toFloat()
            forwardX = -sin(camYawRad)
            forwardZ = -cos(camYawRad)
            rightX = cos(camYawRad)
            rightZ = -sin(camYawRad)
        }

        val worldDirX = rightX * moveX + forwardX * effectiveMoveY
        val worldDirZ = rightZ * moveX + forwardZ * effectiveMoveY
        val dirLen = sqrt(worldDirX * worldDirX + worldDirZ * worldDirZ)
        val effectiveInputMag = sqrt(moveX * moveX + effectiveMoveY * effectiveMoveY).coerceAtMost(1f)

        if (player.isDodging) {
            player.dodgeTimer -= delta
            val activeDashSpeed = if (player.isSwordEquipped) 25.0f else dodgeDashSpeed
            // Prevent forward dash from clipping inside the locked enemy
            if (lockedTarget != null) {
                val dx = lockedTarget.entity.position.x - player.position.x
                val dz = lockedTarget.entity.position.z - player.position.z
                val dist = sqrt(dx * dx + dz * dz)
                val minDashSpacing = player.collisionRadius + lockedTarget.collisionRadius + 0.38f
                val radialDot = if (dist > 0.01f) (player.dodgeDirection.x * dx + player.dodgeDirection.z * dz) / dist else 0f
                if (dist <= minDashSpacing && radialDot > 0.25f) {
                    player.velocity.x = 0f
                    player.velocity.z = 0f
                } else {
                    player.velocity.x = player.dodgeDirection.x * activeDashSpeed
                    player.velocity.z = player.dodgeDirection.z * activeDashSpeed
                }
            } else {
                player.velocity.x = player.dodgeDirection.x * activeDashSpeed
                player.velocity.z = player.dodgeDirection.z * activeDashSpeed
            }
            if (lockTargetYawDeg != null) {
                player.facingYaw = lerpAngleDegrees(player.facingYaw, lockTargetYawDeg, (delta * 18f).coerceAtMost(1f))
            }
            if (player.dodgeTimer <= 0f) {
                player.isDodging = false
            }
        } else if (combatSystem.isUltimateActive) {
            // During DIVINE ANNIHILATION casting, halt horizontal locomotion and consume pending jump requests
            player.velocity.x = 0f
            player.velocity.z = 0f
            input.consumeJumpRequest()
        } else {
            // Player movement is ALWAYS active and responsive — taking a hit never halts or locks joystick input
            val combatMoveScale = when {
                combatSystem.isChargingEnergy -> 0.35f
                combatSystem.fistsCombatController.isFistAttacking -> 0.78f
                combatSystem.isAttacking -> 0.58f
                else -> 1.0f
            }

            val targetSpeed = (when {
                effectiveInputMag < 0.08f -> 0f
                player.isSprinting -> sprintMaxSpeed * effectiveInputMag
                effectiveInputMag < 0.58f -> walkMaxSpeed * (effectiveInputMag / 0.58f)
                else -> MathUtils.lerp(walkMaxSpeed, runMaxSpeed, (effectiveInputMag - 0.58f) / 0.42f)
            }) * combatMoveScale

            if (dirLen > 0.001f && targetSpeed > 0.01f) {
                val nx = worldDirX / dirLen
                val nz = worldDirZ / dirLen
                val accel = if (player.isGrounded) 14f else 6.5f
                player.velocity.x = MathUtils.lerp(player.velocity.x, nx * targetSpeed, (delta * accel).coerceAtMost(1f))
                player.velocity.z = MathUtils.lerp(player.velocity.z, nz * targetSpeed, (delta * accel).coerceAtMost(1f))

                if (lockTargetYawDeg != null) {
                    // While Lock-On is active, always face the locked target (even when retreating backward or strafing)
                    player.facingYaw = lerpAngleDegrees(player.facingYaw, lockTargetYawDeg, (delta * 16f).coerceAtMost(1f))
                } else if (cameraController.cameraMode == CameraMode.THIRD_PERSON) {
                    val targetYaw = Math.toDegrees(atan2(nx.toDouble(), nz.toDouble())).toFloat()
                    val turnSpeed = if (combatSystem.isAttacking) 8.5f else 12f
                    player.facingYaw = lerpAngleDegrees(player.facingYaw, targetYaw, (delta * turnSpeed).coerceAtMost(1f))
                } else if (cameraController.cameraMode == CameraMode.FIRST_PERSON) {
                    player.facingYaw = cameraController.yawDegrees + 180f
                }
            } else {
                val damping = if (combatSystem.isAttacking) 9f else if (player.isGrounded) 12f else 3f
                player.velocity.x = MathUtils.lerp(player.velocity.x, 0f, (delta * damping).coerceAtMost(1f))
                player.velocity.z = MathUtils.lerp(player.velocity.z, 0f, (delta * damping).coerceAtMost(1f))
                if (lockTargetYawDeg != null) {
                    // Maintain target facing while standing still in Lock-On mode
                    player.facingYaw = lerpAngleDegrees(player.facingYaw, lockTargetYawDeg, (delta * 16f).coerceAtMost(1f))
                } else if (cameraController.cameraMode == CameraMode.FIRST_PERSON) {
                    player.facingYaw = cameraController.yawDegrees + 180f
                }
            }
        }

        // Triple Jump System (max 3 jumps before landing: 1st ground jump, 2nd air jump, 3rd final air jump)
        val jumpTriggered = input.consumeJumpRequest() || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
        if (jumpTriggered && !player.isDodging && !combatSystem.isUltimateActive) {
            if (player.isGrounded) {
                player.jumpCount = 1
                player.velocity.y = jumpImpulse
                player.isGrounded = false
                audioManager.playJump()
            } else if (player.jumpCount in 1 until player.maxJumps) {
                player.jumpCount += 1
                player.velocity.y = if (player.jumpCount == 2) jumpImpulse * 0.96f else jumpImpulse * 1.04f
                player.isGrounded = false
                audioManager.playJump()
            }
        }

        val physDelta = if (combatSystem.hitStopTimer > 0f) delta * 0.15f else delta

        // Apply gravity
        if (!player.isGrounded) {
            val gravScale = if (combatSystem.isAttacking && player.velocity.y > -2f) 0.55f else 1.0f
            player.velocity.y += gravity * gravScale * physDelta
        }

        // Integrate position
        player.position.x += player.velocity.x * physDelta
        player.position.z += player.velocity.z * physDelta
        player.position.y += player.velocity.y * physDelta

        // Keep within clearing exploration boundary
        val distFromCenter = sqrt(player.position.x * player.position.x + player.position.z * player.position.z)
        if (distFromCenter > worldBoundaryRadius) {
            val scale = worldBoundaryRadius / distFromCenter
            player.position.x *= scale
            player.position.z *= scale
        }

        // Resolve obstacle collisions
        worldManager.resolveObstacleCollisions(player.position, player.collisionRadius)

        // Resolve ground, central monument step height, and walkable building surfaces
        val surfaceHeight = worldManager.getWalkableSurfaceHeight(player.position.x, player.position.z, player.position.y)
        if (player.velocity.y <= 0f && (player.position.y <= surfaceHeight || (player.isGrounded && player.position.y <= surfaceHeight + 0.14f))) {
            val wasAirborne = !player.isGrounded && player.velocity.y < -2.5f
            player.position.y = surfaceHeight
            player.velocity.y = 0f
            player.isGrounded = true
            player.jumpCount = 0
            player.hasUsedAirDash = false
            player.hasUsedAirAttack = false
            if (wasAirborne) {
                audioManager.playLand()
            }
        } else if (player.position.y > surfaceHeight + 0.14f) {
            if (player.isGrounded && player.jumpCount == 0) {
                player.jumpCount = 1
            }
            player.isGrounded = false
        }

        // Determine locomotion AnimationState before CombatSystem overrides it if attacking/charging
        val horizontalSpeed = sqrt(player.velocity.x * player.velocity.x + player.velocity.z * player.velocity.z)
        player.animationState = when {
            player.hitStunTimer > 0f && horizontalSpeed < 0.6f && !combatSystem.isAttacking -> AnimationState.HIT
            player.isDodging -> {
                when (player.animationState) {
                    AnimationState.DODGE_SIDE,
                    AnimationState.DODGE_BACK,
                    AnimationState.DODGE_LOW,
                    AnimationState.DODGE_FORWARD -> player.animationState
                    else -> if (player.isSwordEquipped) AnimationState.SWORD_FLASH_STEP else AnimationState.DODGE
                }
            }
            !player.isGrounded -> AnimationState.JUMP
            horizontalSpeed < 0.35f -> AnimationState.IDLE
            player.isSprinting && horizontalSpeed > 6.5f -> AnimationState.SPRINT
            horizontalSpeed < 5.2f -> AnimationState.WALK
            else -> AnimationState.RUN
        }

        // Update CombatSystem (may override animationState & activeAttackProgress for active attacks/charge)
        combatSystem.update(delta, player, cameraController)

        // Advance active survival gathering tool animation if not interrupted by combat/dodge
        if (player.activeToolTimer > 0f) {
            if (combatSystem.isAttacking || combatSystem.isChargingEnergy || player.isDodging) {
                player.activeToolTimer = 0f
                player.activeToolAnimationState = null
            } else {
                player.activeToolTimer = (player.activeToolTimer - delta).coerceAtLeast(0f)
                val toolState = player.activeToolAnimationState
                if (toolState != null && player.activeToolTimer > 0f) {
                    player.animationState = toolState
                    player.activeAttackProgress = (1f - (player.activeToolTimer / player.activeToolDuration.coerceAtLeast(0.01f))).coerceIn(0f, 1f)
                } else {
                    player.activeToolAnimationState = null
                    player.activeAttackProgress = 0f
                }
            }
        }

        // Trigger procedural footsteps when moving on the ground
        if (player.isGrounded && horizontalSpeed > 0.65f && !player.isDodging && !combatSystem.isAttacking) {
            val stepInterval = when (player.animationState) {
                AnimationState.SPRINT -> 0.24f
                AnimationState.RUN -> 0.33f
                else -> 0.48f
            }
            footstepTimer += delta
            if (footstepTimer >= stepInterval) {
                footstepTimer = 0f
                audioManager.playFootstep(player.isSprinting)
            }
        } else {
            footstepTimer = 0.2f
        }

        // Sample terrain surface heights in player's local facing frame for slope alignment & foot grounding
        val yawRad = Math.toRadians(player.facingYaw.toDouble()).toFloat()
        val fwdX = sin(yawRad)
        val fwdZ = cos(yawRad)
        val rgtX = cos(yawRad)
        val rgtZ = -sin(yawRad)
        val sampleD = 0.45f

        val hFwd = worldManager.getWalkableSurfaceHeight(player.position.x + fwdX * sampleD, player.position.z + fwdZ * sampleD, player.position.y)
        val hBack = worldManager.getWalkableSurfaceHeight(player.position.x - fwdX * sampleD, player.position.z - fwdZ * sampleD, player.position.y)
        val hRight = worldManager.getWalkableSurfaceHeight(player.position.x + rgtX * sampleD, player.position.z + rgtZ * sampleD, player.position.y)
        val hLeft = worldManager.getWalkableSurfaceHeight(player.position.x - rgtX * sampleD, player.position.z - rgtZ * sampleD, player.position.y)

        val slopePitch = if (player.isGrounded) Math.toDegrees(kotlin.math.atan2((hFwd - hBack).toDouble(), (sampleD * 2f).toDouble())).toFloat().coerceIn(-28f, 28f) else 0f
        val slopeRoll = if (player.isGrounded) Math.toDegrees(kotlin.math.atan2((hRight - hLeft).toDouble(), (sampleD * 2f).toDouble())).toFloat().coerceIn(-20f, 20f) else 0f

        // Update articulated skeletal limb animation & world transform
        player.syncModelTransform()
        player.animationController.update(
            delta = if (combatSystem.hitStopTimer > 0f) delta * 0.20f else delta,
            state = player.animationState,
            horizontalSpeedRatio = (horizontalSpeed / runMaxSpeed).coerceIn(0f, 1.5f),
            verticalVelocity = player.velocity.y,
            isFirstPerson = cameraController.cameraMode == CameraMode.FIRST_PERSON,
            isSwordEquipped = player.isSwordEquipped,
            attackProgress = player.activeAttackProgress,
            equippedToolId = player.equippedToolId ?: player.equippedItemId,
            slopePitch = slopePitch,
            slopeRoll = slopeRoll,
            actualHorizontalSpeed = horizontalSpeed
        )

        // Sample exact world-space drawnSword blade positions to emit real-time 3D blade trails
        combatSystem.postAnimationUpdate(delta, player)
    }

    fun resetPlayerAndCamera(
        cameraController: FollowCameraController,
        combatSystem: CombatSystem
    ) {
        val spawnY = worldManager.getWalkableSurfaceHeight(player.spawnPosition.x, player.spawnPosition.z)
        player.resetToSpawn(spawnY)
        combatSystem.reset()
        cameraController.resetToDefault(player)
        audioManager.playResetChime()
    }

    private fun lerpAngleDegrees(from: Float, to: Float, alpha: Float): Float {
        var diff = (to - from) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        return from + diff * alpha
    }
}
