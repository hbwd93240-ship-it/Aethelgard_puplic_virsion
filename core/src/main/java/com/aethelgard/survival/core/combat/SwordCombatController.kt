package com.aethelgard.survival.core.combat

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.camera.FollowCameraController
import com.aethelgard.survival.core.entities.EnemyAiState
import com.aethelgard.survival.core.entities.EnemyController
import com.aethelgard.survival.core.entities.EntityManager
import com.aethelgard.survival.core.player.AnimationState
import com.aethelgard.survival.core.player.PlayerCharacter
import com.aethelgard.survival.core.world.ProceduralModelFactory
import com.aethelgard.survival.core.world.WorldManager
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class ActiveBladeTrailSegment(
    val instance: ModelInstance,
    val center: Vector3,
    val bladeDir: Vector3,
    val swingDir: Vector3,
    val bladeLen: Float,
    val chordWidth: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 0.17f
)

class ActiveCurvedArc(
    val instance: ModelInstance,
    val position: Vector3,
    val velocity: Vector3,
    val yawDeg: Float,
    val pitchDeg: Float,
    val rollTiltDeg: Float,
    val initialScale: Float,
    val expansionRate: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 0.24f
)

class ActiveInfernoCrescent(
    val instance: ModelInstance,
    val position: Vector3,
    val velocity: Vector3,
    val yawDeg: Float,
    val damage: Float,
    val knockback: Float,
    val hitRadius: Float = 1.65f,
    var age: Float = 0f,
    val maxLifetime: Float = 1.65f,
    val hitEnemies: MutableSet<String> = mutableSetOf()
)

class ActiveDragonTempest(
    val instance: ModelInstance,
    val position: Vector3,
    var spinYaw: Float = 0f,
    var currentScale: Float = 0.85f,
    val expansionRate: Float = 7.8f,
    var age: Float = 0f,
    val maxLifetime: Float = 0.48f
)

class ActiveAfterimage(
    val instance: ModelInstance,
    val position: Vector3,
    val yawDeg: Float,
    val forwardLeanDeg: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 0.30f
)

class ActiveWindStreak(
    val instance: ModelInstance,
    val position: Vector3,
    val velocity: Vector3,
    val yawDeg: Float,
    val pitchDeg: Float,
    val initialLengthScale: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 0.22f
)

class ActiveGroundDust(
    val instance: ModelInstance,
    val position: Vector3,
    val initialScale: Float,
    val expansionRate: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 0.34f
)

class ActiveHitFlashStar(
    val instance: ModelInstance,
    val position: Vector3,
    val initialScale: Float,
    var spinDeg: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 0.16f
)

private data class ScheduledSwordHitWindow(
    val triggerProgress: Float,
    val damage: Float,
    val range: Float,
    val knockback: Float,
    val is360AoE: Boolean,
    val isHeavy: Boolean,
    val isFiery: Boolean,
    val slashRollTiltDeg: Float,
    val enemyYawTwistDeg: Float,
    val enemyPitchTiltDeg: Float,
    val enemyVerticalJolt: Float,
    val shakeIntensity: Float,
    val hitStopDuration: Float,
    var hasTriggered: Boolean = false
)

/**
 * Milestone 3 Dedicated Anime Sword Combat Controller inspired by Where Winds Meet.
 * Completely separates Sword Combat from Unarmed Fist Combat with:
 * - 5-step fluid fast sword combo (Horizontal Slash, Diagonal Cross, Rising Slash, 360 Spin, Rapid Multi-Slash Flurry)
 * - Phantom Movement (Phantom Dash Slash with afterimages, Flash Step vanish/reposition, Multi-Directional orbital steps)
 * - 3 Heavy Sword Skills (Inferno Crescent flaming wave projectile, Dragon Tempest spinning cyclone shockwave, Aerial Sword Strike)
 * - Real-time 3D blade-following ribbon trails, curved energy arcs, wind streaks, ground dust, and hit-only impact flashes
 * - Directional enemy hit reactions, brief hit-stop frames, and impact-gated camera shake
 */
class SwordCombatController(
    private val modelFactory: ProceduralModelFactory,
    private val worldManager: WorldManager,
    private val entityManager: EntityManager,
    private val audioManager: AudioManager,
    private val onRecordDamagePopup: (Vector3, Float, Boolean) -> Unit,
    private val onSpawnHitSparks: (Vector3, Int, Float, Int) -> Unit
) {
    private lateinit var curvedArcCyanModel: Model
    private lateinit var curvedArcFireModel: Model
    private lateinit var bladeTrailCyanModel: Model
    private lateinit var bladeTrailFireModel: Model
    private lateinit var infernoCrescentModel: Model
    private lateinit var dragonTempestModel: Model
    private lateinit var afterimageGhostModel: Model
    private lateinit var windStreakModel: Model
    private lateinit var groundDustModel: Model
    private lateinit var hitFlashCyanModel: Model
    private lateinit var hitFlashFireModel: Model

    val bladeTrailSegments = mutableListOf<ActiveBladeTrailSegment>()
    val curvedArcs = mutableListOf<ActiveCurvedArc>()
    val infernoCrescents = mutableListOf<ActiveInfernoCrescent>()
    val dragonTempests = mutableListOf<ActiveDragonTempest>()
    val afterimages = mutableListOf<ActiveAfterimage>()
    val windStreaks = mutableListOf<ActiveWindStreak>()
    val groundDusts = mutableListOf<ActiveGroundDust>()
    val hitFlashStars = mutableListOf<ActiveHitFlashStar>()

    // Active Sword Action State
    var isSwordAttacking: Boolean = false
        private set
    var activeSwordState: AnimationState = AnimationState.IDLE
        private set
    private var actionTimer: Float = 0f
    private var actionDuration: Float = 0.34f
    private var currentActionIsFiery: Boolean = false

    // 5-Step Sword Combo Chain (1..5)
    var currentComboStep: Int = 0
        private set
    val maxComboSteps: Int = 5
    private var comboResetTimer: Float = 0f

    // Cycling Heavy Sword Skill (0 = Inferno Crescent, 1 = Dragon Tempest, 2 = Aerial Sword Strike)
    var heavySkillIndex: Int = 0
        private set

    val nextHeavySkillLabel: String
        get() = when (heavySkillIndex) {
            0 -> "CRESCENT"
            1 -> "TEMPEST"
            else -> "SKY STRIKE"
        }

    // Cooldowns & Hit-Stop
    var heavyCooldown: Float = 0f
        private set
    val heavyMaxCooldown: Float = 0.90f

    var flashStepCooldown: Float = 0f
        private set
    val flashStepMaxCooldown: Float = 0.48f

    var hitStopTimer: Float = 0f
        private set

    // Flash Step State
    var isFlashStepping: Boolean = false
        private set
    private var flashStepTimer: Float = 0f
    private val flashStepDuration: Float = 0.20f
    private val flashStepSpeed: Float = 25.0f
    private var afterimageSpawnAccum: Float = 0f
    private var hasSpawnedFlashArrivalVfx: Boolean = false

    // Multi-directional orbital step state around locked target
    private var orbitalStepDir: Float = 0f // -1f = left orbit, +1f = right orbit, 0f = none
    private var orbitalStepSpeed: Float = 0f

    // Scheduled hit windows within the active sword animation
    private val scheduledHits = mutableListOf<ScheduledSwordHitWindow>()

    // Real-time 3D blade tracking state
    private val prevBladeBase = Vector3()
    private val prevBladeTip = Vector3()
    private val currBladeBase = Vector3()
    private val currBladeTip = Vector3()
    private var hasPrevBladeSample: Boolean = false
    private val tmpQuat = Quaternion()
    private val rng = Random(913)

    fun initialize() {
        curvedArcCyanModel = modelFactory.createCurvedSwordArcModel(isFiery = false)
        curvedArcFireModel = modelFactory.createCurvedSwordArcModel(isFiery = true)
        bladeTrailCyanModel = modelFactory.createBladeTrailSegmentModel(isFiery = false)
        bladeTrailFireModel = modelFactory.createBladeTrailSegmentModel(isFiery = true)
        infernoCrescentModel = modelFactory.createInfernoCrescentProjectileModel()
        dragonTempestModel = modelFactory.createDragonTempestShockwaveModel()
        afterimageGhostModel = modelFactory.createAfterimageGhostModel()
        windStreakModel = modelFactory.createWindStreakModel()
        groundDustModel = modelFactory.createGroundDustModel()
        hitFlashCyanModel = modelFactory.createHitFlashStarModel(isFiery = false)
        hitFlashFireModel = modelFactory.createHitFlashStarModel(isFiery = true)
    }

    /**
     * Triggers a Fast Sword Attack:
     * - If airborne: performs Aerial Sword Strike downward cleave.
     * - If sprinting/flash-stepping or at mid-range gap-closing distance (3.4m..11.2m) from the target:
     *   performs Phantom Dash Slash with afterimages and wind streaks.
     * - Otherwise advances the fluid 5-step Fast Sword Combo:
     *   1. Horizontal Slash -> 2. Diagonal Cross Slash -> 3. Rising Sword Slash ->
     *   4. Spinning 360 Slash (with orbital step) -> 5. Rapid Multi-Slash Flurry (with orbital step).
     */
    fun triggerLightSwordAttack(
        player: PlayerCharacter,
        cameraController: FollowCameraController
    ) {
        // Allow smooth combo chaining once the current sword attack passes 58% progress
        if (isSwordAttacking && actionTimer < actionDuration * 0.58f) return

        if (isFlashStepping) {
            isFlashStepping = false
            player.isDodging = false
        }

        val targetEnemy = resolveCombatTarget(player, cameraController)
        orientPlayerTowardTarget(player, targetEnemy)

        // 1. Airborne Sword Strike when jumping (not mid-combo hop)
        if (!player.isGrounded && currentComboStep == 0) {
            if (player.hasUsedAirAttack) return
            player.hasUsedAirAttack = true
            startAerialSwordStrike(player, cameraController, targetEnemy, isFromGroundLeap = false)
            return
        }

        // 2. Check Phantom Dash Slash condition (gap-closer when target is 3.4m..11.2m away or while sprinting)
        val distToEnemy = if (targetEnemy != null && targetEnemy.aiState != EnemyAiState.DEAD) {
            val dx = targetEnemy.entity.position.x - player.position.x
            val dz = targetEnemy.entity.position.z - player.position.z
            sqrt(dx * dx + dz * dz)
        } else {
            0f
        }

        val shouldPhantomDash = (targetEnemy != null && distToEnemy in 3.4f..11.2f && currentComboStep == 0) ||
            (player.isSprinting && distToEnemy >= 2.4f)

        if (shouldPhantomDash) {
            currentComboStep = 1
            comboResetTimer = 1.15f
            startPhantomDashSlash(player, targetEnemy, cameraController)
            return
        }

        // 3. Advance 5-step Fast Sword Combo
        currentComboStep = if (comboResetTimer > 0f) {
            (currentComboStep % maxComboSteps) + 1
        } else {
            1
        }
        comboResetTimer = 1.15f

        when (currentComboStep) {
            1 -> startHorizontalSlash(player, targetEnemy, cameraController)
            2 -> startDiagonalCrossSlash(player, targetEnemy, cameraController)
            3 -> startRisingSwordSlash(player, targetEnemy, cameraController)
            4 -> startSpinning360Slash(player, targetEnemy, cameraController)
            else -> startRapidMultiSlashFlurry(player, targetEnemy, cameraController)
        }
    }

    /**
     * Triggers a Heavy Sword Attack cycling through the 3 signature Where Winds Meet heavy arts:
     * 0 -> Inferno Crescent (launches a large blazing crescent projectile + heavy cleave)
     * 1 -> Dragon Tempest (720-degree spinning wind & energy cyclone shockwave)
     * 2 (or Airborne) -> Aerial Sword Strike (high leap toward target + downward fiery cleave)
     */
    fun triggerHeavySwordAttack(
        player: PlayerCharacter,
        cameraController: FollowCameraController
    ) {
        if (heavyCooldown > 0f) return
        if (isSwordAttacking && actionTimer < actionDuration * 0.70f) return

        if (isFlashStepping) {
            isFlashStepping = false
            player.isDodging = false
        }

        val targetEnemy = resolveCombatTarget(player, cameraController)
        orientPlayerTowardTarget(player, targetEnemy)

        heavyCooldown = heavyMaxCooldown
        currentComboStep = 0
        comboResetTimer = 0f

        if (!player.isGrounded) {
            player.hasUsedAirAttack = true
            startAerialSwordStrike(player, cameraController, targetEnemy, isFromGroundLeap = false)
            return
        }

        when (heavySkillIndex) {
            0 -> startInfernoCrescent(player, targetEnemy, cameraController)
            1 -> startDragonTempest(player, targetEnemy, cameraController)
            else -> startAerialSwordStrike(player, cameraController, targetEnemy, isFromGroundLeap = true)
        }
        heavySkillIndex = (heavySkillIndex + 1) % 3
    }

    /**
     * Triggers Flash Step (Sword stance DASH):
     * Ultra-fast anime phantom step with vanish/reappear effect, ghost afterimages, wind streaks,
     * and ground dust bursts while maintaining Lock-On target orientation.
     */
    fun triggerFlashStep(
        player: PlayerCharacter,
        moveInputX: Float,
        moveInputY: Float,
        cameraController: FollowCameraController
    ): Boolean {
        if (flashStepCooldown > 0f || isFlashStepping) return false
        if (!player.isGrounded && player.hasUsedAirDash) return false
        if (player.energy < 8f) return false

        player.energy = (player.energy - 8f).coerceAtLeast(0f)
        isSwordAttacking = false
        scheduledHits.clear()
        flashStepCooldown = flashStepMaxCooldown

        isFlashStepping = true
        flashStepTimer = flashStepDuration
        hasSpawnedFlashArrivalVfx = false
        afterimageSpawnAccum = 0f

        player.isDodging = true
        player.dodgeTimer = flashStepDuration
        player.invulnerabilityTimer = 0.36f
        player.animationState = AnimationState.SWORD_FLASH_STEP
        player.activeAttackProgress = 0f

        if (!player.isGrounded) {
            player.hasUsedAirDash = true
            player.velocity.y = 2.4f
        }

        val lockedTarget = if (cameraController.isLockedOn) cameraController.lockedTarget else null
        var dirX: Float
        var dirZ: Float

        if (lockedTarget != null && lockedTarget.aiState != EnemyAiState.DEAD) {
            val dx = lockedTarget.entity.position.x - player.position.x
            val dz = lockedTarget.entity.position.z - player.position.z
            val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.05f)
            val toTargetX = dx / dist
            val toTargetZ = dz / dist
            val strafeRightX = -toTargetZ
            val strafeRightZ = toTargetX

            var effMoveY = moveInputY
            val minSafeDist = player.collisionRadius + lockedTarget.collisionRadius + 1.85f
            if (dist <= minSafeDist && effMoveY > 0f) {
                effMoveY = 0f
            }

            dirX = strafeRightX * moveInputX + toTargetX * effMoveY
            dirZ = strafeRightZ * moveInputX + toTargetZ * effMoveY
            val len = sqrt(dirX * dirX + dirZ * dirZ)
            if (len > 0.1f) {
                dirX /= len
                dirZ /= len
            } else {
                // Neutral joystick while locked on: flash-step orbit diagonally around the enemy if close, or gap-close if far
                if (dist > 4.2f) {
                    dirX = toTargetX * 0.92f + strafeRightX * 0.38f
                    dirZ = toTargetZ * 0.92f + strafeRightZ * 0.38f
                } else {
                    val side = if (rng.nextBoolean()) 1f else -1f
                    dirX = strafeRightX * side * 0.88f - toTargetX * 0.46f
                    dirZ = strafeRightZ * side * 0.88f - toTargetZ * 0.46f
                }
                val nLen = sqrt(dirX * dirX + dirZ * dirZ).coerceAtLeast(0.01f)
                dirX /= nLen
                dirZ /= nLen
            }
            player.facingYaw = Math.toDegrees(atan2(toTargetX.toDouble(), toTargetZ.toDouble())).toFloat()
        } else {
            val camYawRad = Math.toRadians(cameraController.yawDegrees.toDouble()).toFloat()
            val forwardX = -sin(camYawRad)
            val forwardZ = -cos(camYawRad)
            val rightX = cos(camYawRad)
            val rightZ = -sin(camYawRad)

            dirX = rightX * moveInputX + forwardX * moveInputY
            dirZ = rightZ * moveInputX + forwardZ * moveInputY
            val len = sqrt(dirX * dirX + dirZ * dirZ)
            if (len > 0.1f) {
                dirX /= len
                dirZ /= len
            } else {
                val faceRad = Math.toRadians(player.facingYaw.toDouble()).toFloat()
                dirX = sin(faceRad)
                dirZ = cos(faceRad)
            }
            player.facingYaw = Math.toDegrees(atan2(dirX.toDouble(), dirZ.toDouble())).toFloat()
        }

        player.dodgeDirection.set(dirX, 0f, dirZ)
        audioManager.playFlashStep()

        // Departure afterimage + ground dust burst + wind streaks
        spawnAfterimage(player.position, player.facingYaw, 24f)
        spawnGroundDust(player.position, initialScale = 0.85f, expansionRate = 4.6f)
        spawnWindStreakBurst(player.position, dirX, dirZ, count = 4)
        onSpawnHitSparks(Vector3(player.position.x, player.position.y + 0.95f, player.position.z), 8, 4.8f, 0)

        return true
    }

    // =========================================================================
    // Dedicated Sword Attack Starters
    // =========================================================================

    private fun startHorizontalSlash(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        cameraController: FollowCameraController
    ) {
        beginSwordAction(
            player = player,
            state = AnimationState.SWORD_HORIZONTAL_SLASH,
            duration = 0.28f,
            isFiery = false
        )
        applyForwardLunge(player, targetEnemy, lungeSpeed = 6.2f)
        audioManager.playSwordComboSlash(1)

        val fwd = facingVector(player.facingYaw)
        spawnCurvedSwordArc(
            pos = Vector3(player.position.x + fwd.x * 1.05f, player.position.y + 1.08f, player.position.z + fwd.z * 1.05f),
            vel = Vector3(fwd.x * 3.8f, 0f, fwd.z * 3.8f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            rollTiltDeg = 8f,
            initialScale = 1.05f,
            expansionRate = 3.8f,
            isFiery = false
        )

        scheduledHits.add(
            ScheduledSwordHitWindow(
                triggerProgress = 0.34f,
                damage = 18f,
                range = 3.10f,
                knockback = 4.2f,
                is360AoE = false,
                isHeavy = false,
                isFiery = false,
                slashRollTiltDeg = 8f,
                enemyYawTwistDeg = -26f,
                enemyPitchTiltDeg = 8f,
                enemyVerticalJolt = 0.04f,
                shakeIntensity = 0.07f,
                hitStopDuration = 0.040f
            )
        )
    }

    private fun startDiagonalCrossSlash(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        cameraController: FollowCameraController
    ) {
        beginSwordAction(
            player = player,
            state = AnimationState.SWORD_DIAGONAL_CROSS,
            duration = 0.34f,
            isFiery = false
        )
        applyForwardLunge(player, targetEnemy, lungeSpeed = 6.8f)
        audioManager.playSwordComboSlash(2)

        val fwd = facingVector(player.facingYaw)
        // First diagonal slash arc (\)
        spawnCurvedSwordArc(
            pos = Vector3(player.position.x + fwd.x * 1.08f, player.position.y + 1.15f, player.position.z + fwd.z * 1.08f),
            vel = Vector3(fwd.x * 4.2f, 0f, fwd.z * 4.2f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            rollTiltDeg = 38f,
            initialScale = 1.08f,
            expansionRate = 4.0f,
            isFiery = false
        )

        // Two rapid diagonal cross hits
        scheduledHits.add(
            ScheduledSwordHitWindow(
                triggerProgress = 0.28f,
                damage = 12f,
                range = 3.15f,
                knockback = 3.2f,
                is360AoE = false,
                isHeavy = false,
                isFiery = false,
                slashRollTiltDeg = 38f,
                enemyYawTwistDeg = 24f,
                enemyPitchTiltDeg = 10f,
                enemyVerticalJolt = 0.03f,
                shakeIntensity = 0.06f,
                hitStopDuration = 0.032f
            )
        )
        scheduledHits.add(
            ScheduledSwordHitWindow(
                triggerProgress = 0.64f,
                damage = 15f,
                range = 3.20f,
                knockback = 4.6f,
                is360AoE = false,
                isHeavy = false,
                isFiery = false,
                slashRollTiltDeg = -38f,
                enemyYawTwistDeg = -28f,
                enemyPitchTiltDeg = 12f,
                enemyVerticalJolt = 0.05f,
                shakeIntensity = 0.08f,
                hitStopDuration = 0.042f
            )
        )
    }

    private fun startRisingSwordSlash(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        cameraController: FollowCameraController
    ) {
        beginSwordAction(
            player = player,
            state = AnimationState.SWORD_RISING_SLASH,
            duration = 0.34f,
            isFiery = false
        )
        applyForwardLunge(player, targetEnemy, lungeSpeed = 7.4f)
        // Slight upward hop momentum on the rising dragon slash
        if (player.isGrounded) {
            player.velocity.y = 3.2f
        }
        audioManager.playSwordComboSlash(3)

        val fwd = facingVector(player.facingYaw)
        spawnGroundDust(player.position, initialScale = 0.75f, expansionRate = 4.2f)
        spawnCurvedSwordArc(
            pos = Vector3(player.position.x + fwd.x * 1.05f, player.position.y + 1.05f, player.position.z + fwd.z * 1.05f),
            vel = Vector3(fwd.x * 4.5f, 2.2f, fwd.z * 4.5f),
            yawDeg = player.facingYaw,
            pitchDeg = -18f,
            rollTiltDeg = 76f,
            initialScale = 1.15f,
            expansionRate = 4.5f,
            isFiery = false
        )

        scheduledHits.add(
            ScheduledSwordHitWindow(
                triggerProgress = 0.36f,
                damage = 23f,
                range = 3.20f,
                knockback = 6.2f,
                is360AoE = false,
                isHeavy = false,
                isFiery = false,
                slashRollTiltDeg = 76f,
                enemyYawTwistDeg = 12f,
                enemyPitchTiltDeg = -22f, // Tilts enemy upward/back
                enemyVerticalJolt = 0.24f,
                shakeIntensity = 0.10f,
                hitStopDuration = 0.048f
            )
        )
    }

    private fun startSpinning360Slash(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        cameraController: FollowCameraController
    ) {
        beginSwordAction(
            player = player,
            state = AnimationState.SWORD_SPIN_360,
            duration = 0.38f,
            isFiery = false
        )
        // Multi-directional orbital step around locked enemy while spinning
        if (targetEnemy != null && cameraController.isLockedOn) {
            orbitalStepDir = if (rng.nextBoolean()) 1f else -1f
            orbitalStepSpeed = 8.2f
        } else {
            applyForwardLunge(player, targetEnemy, lungeSpeed = 7.6f)
        }
        audioManager.playSwordComboSlash(4)

        spawnGroundDust(player.position, initialScale = 0.95f, expansionRate = 5.4f)
        spawnWindStreakBurst(player.position, sin(Math.toRadians(player.facingYaw.toDouble())).toFloat(), cos(Math.toRadians(player.facingYaw.toDouble())).toFloat(), count = 4)
        spawnCurvedSwordArc(
            pos = Vector3(player.position.x, player.position.y + 1.05f, player.position.z),
            vel = Vector3.Zero,
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            rollTiltDeg = 4f,
            initialScale = 1.35f,
            expansionRate = 6.2f,
            isFiery = false
        )

        scheduledHits.add(
            ScheduledSwordHitWindow(
                triggerProgress = 0.42f,
                damage = 26f,
                range = 3.55f,
                knockback = 7.8f,
                is360AoE = true,
                isHeavy = true,
                isFiery = false,
                slashRollTiltDeg = 0f,
                enemyYawTwistDeg = 32f,
                enemyPitchTiltDeg = 14f,
                enemyVerticalJolt = 0.10f,
                shakeIntensity = 0.13f,
                hitStopDuration = 0.052f
            )
        )
    }

    private fun startRapidMultiSlashFlurry(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        cameraController: FollowCameraController
    ) {
        beginSwordAction(
            player = player,
            state = AnimationState.SWORD_RAPID_FLURRY,
            duration = 0.54f,
            isFiery = true
        )
        if (targetEnemy != null && cameraController.isLockedOn) {
            orbitalStepDir = if (orbitalStepDir == 0f) 1f else -orbitalStepDir
            orbitalStepSpeed = 9.0f
        } else {
            applyForwardLunge(player, targetEnemy, lungeSpeed = 8.4f)
        }
        audioManager.playSwordComboSlash(5)
        spawnAfterimage(player.position, player.facingYaw, 20f)

        // 4 rapid multi-slash hits culminating in a fiery cross finisher
        val angles = floatArrayOf(28f, -32f, 52f, -12f)
        val progresses = floatArrayOf(0.18f, 0.38f, 0.58f, 0.80f)
        for (i in 0 until 4) {
            val isFinal = (i == 3)
            scheduledHits.add(
                ScheduledSwordHitWindow(
                    triggerProgress = progresses[i],
                    damage = if (isFinal) 18f else 8f,
                    range = 3.40f,
                    knockback = if (isFinal) 10.5f else 2.2f,
                    is360AoE = isFinal,
                    isHeavy = isFinal,
                    isFiery = isFinal,
                    slashRollTiltDeg = angles[i],
                    enemyYawTwistDeg = if (i % 2 == 0) 24f else -24f,
                    enemyPitchTiltDeg = if (isFinal) 18f else 8f,
                    enemyVerticalJolt = if (isFinal) 0.18f else 0.04f,
                    shakeIntensity = if (isFinal) 0.18f else 0.06f,
                    hitStopDuration = if (isFinal) 0.065f else 0.024f
                )
            )
        }
    }

    private fun startPhantomDashSlash(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        cameraController: FollowCameraController
    ) {
        beginSwordAction(
            player = player,
            state = AnimationState.SWORD_PHANTOM_DASH_SLASH,
            duration = 0.34f,
            isFiery = false
        )
        player.invulnerabilityTimer = 0.30f
        audioManager.playFlashStep()
        audioManager.playSwordComboSlash(5)

        val fwd = facingVector(player.facingYaw)
        spawnGroundDust(player.position, initialScale = 0.90f, expansionRate = 5.2f)
        spawnWindStreakBurst(player.position, fwd.x, fwd.z, count = 5)

        // High-speed phantom dash toward the target without passing through obstacles or clipping inside the enemy
        val startPos = Vector3(player.position)
        val desiredTravel = if (targetEnemy != null && targetEnemy.aiState != EnemyAiState.DEAD) {
            val dx = targetEnemy.entity.position.x - player.position.x
            val dz = targetEnemy.entity.position.z - player.position.z
            val dist = sqrt(dx * dx + dz * dz)
            val safeStop = player.collisionRadius + targetEnemy.collisionRadius + 1.05f
            (dist - safeStop).coerceIn(1.5f, 7.2f)
        } else {
            4.8f
        }

        // Spawn 3 ghost afterimages along the phantom dash path and advance player safely
        val steps = 3
        for (s in 1..steps) {
            val frac = s.toFloat() / (steps + 1).toFloat()
            val ghostPos = Vector3(
                startPos.x + fwd.x * desiredTravel * frac,
                worldManager.getWalkableSurfaceHeight(startPos.x + fwd.x * desiredTravel * frac, startPos.z + fwd.z * desiredTravel * frac),
                startPos.z + fwd.z * desiredTravel * frac
            )
            worldManager.resolveObstacleCollisions(ghostPos, player.collisionRadius)
            spawnAfterimage(ghostPos, player.facingYaw, 26f)
        }

        // Set burst velocity for the remaining glide into the strike
        player.velocity.x = fwd.x * (desiredTravel / 0.16f).coerceIn(12f, 24f)
        player.velocity.z = fwd.z * (desiredTravel / 0.16f).coerceIn(12f, 24f)

        spawnCurvedSwordArc(
            pos = Vector3(player.position.x + fwd.x * 1.35f, player.position.y + 1.08f, player.position.z + fwd.z * 1.35f),
            vel = Vector3(fwd.x * 8.0f, 0f, fwd.z * 8.0f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            rollTiltDeg = -14f,
            initialScale = 1.25f,
            expansionRate = 5.5f,
            isFiery = false
        )

        scheduledHits.add(
            ScheduledSwordHitWindow(
                triggerProgress = 0.42f,
                damage = 28f,
                range = 3.55f,
                knockback = 7.5f,
                is360AoE = true,
                isHeavy = true,
                isFiery = false,
                slashRollTiltDeg = -14f,
                enemyYawTwistDeg = -30f,
                enemyPitchTiltDeg = 14f,
                enemyVerticalJolt = 0.12f,
                shakeIntensity = 0.14f,
                hitStopDuration = 0.055f
            )
        )
    }

    private fun startInfernoCrescent(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        cameraController: FollowCameraController
    ) {
        beginSwordAction(
            player = player,
            state = AnimationState.SWORD_INFERNO_CRESCENT,
            duration = 0.46f,
            isFiery = true
        )
        applyForwardLunge(player, targetEnemy, lungeSpeed = 7.8f)
        audioManager.playSwordHeavySkill(0)

        val fwd = facingVector(player.facingYaw)
        spawnGroundDust(player.position, initialScale = 1.05f, expansionRate = 5.8f)
        spawnWindStreakBurst(player.position, fwd.x, fwd.z, count = 5)

        // Launch the large flaming Inferno Crescent wave projectile
        val spawnPos = Vector3(
            player.position.x + fwd.x * 1.15f,
            player.position.y + 1.12f,
            player.position.z + fwd.z * 1.15f
        )
        val projSpeed = 19.5f
        var velY = 0f
        if (targetEnemy != null && targetEnemy.aiState != EnemyAiState.DEAD) {
            val dx = targetEnemy.entity.position.x - player.position.x
            val dy = (targetEnemy.entity.position.y + 1.05f) - spawnPos.y
            val dz = targetEnemy.entity.position.z - player.position.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.5f)
            velY = ((dy / dist) * projSpeed).coerceIn(-6f, 6f)
        }

        infernoCrescents.add(
            ActiveInfernoCrescent(
                instance = ModelInstance(infernoCrescentModel),
                position = spawnPos,
                velocity = Vector3(fwd.x * projSpeed, velY, fwd.z * projSpeed),
                yawDeg = player.facingYaw,
                damage = 32f,
                knockback = 12.5f
            )
        )

        // Also register close-range fiery sword cleave hit
        scheduledHits.add(
            ScheduledSwordHitWindow(
                triggerProgress = 0.36f,
                damage = 26f,
                range = 3.45f,
                knockback = 9.5f,
                is360AoE = false,
                isHeavy = true,
                isFiery = true,
                slashRollTiltDeg = -8f,
                enemyYawTwistDeg = -32f,
                enemyPitchTiltDeg = 16f,
                enemyVerticalJolt = 0.15f,
                shakeIntensity = 0.18f,
                hitStopDuration = 0.065f
            )
        )
    }

    private fun startDragonTempest(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        cameraController: FollowCameraController
    ) {
        beginSwordAction(
            player = player,
            state = AnimationState.SWORD_DRAGON_TEMPEST,
            duration = 0.52f,
            isFiery = true
        )
        applyForwardLunge(player, targetEnemy, lungeSpeed = 8.2f)
        audioManager.playSwordHeavySkill(1)

        spawnGroundDust(player.position, initialScale = 1.25f, expansionRate = 6.8f)
        spawnAfterimage(player.position, player.facingYaw, 22f)

        val fwd = facingVector(player.facingYaw)
        spawnWindStreakBurst(player.position, fwd.x, fwd.z, count = 6)

        dragonTempests.add(
            ActiveDragonTempest(
                instance = ModelInstance(dragonTempestModel),
                position = Vector3(player.position.x, player.position.y + 0.15f, player.position.z)
            )
        )

        // Two-stage spinning cyclone AoE hits
        scheduledHits.add(
            ScheduledSwordHitWindow(
                triggerProgress = 0.26f,
                damage = 18f,
                range = 4.10f,
                knockback = 5.5f,
                is360AoE = true,
                isHeavy = false,
                isFiery = false,
                slashRollTiltDeg = 12f,
                enemyYawTwistDeg = 28f,
                enemyPitchTiltDeg = 12f,
                enemyVerticalJolt = 0.14f,
                shakeIntensity = 0.12f,
                hitStopDuration = 0.038f
            )
        )
        scheduledHits.add(
            ScheduledSwordHitWindow(
                triggerProgress = 0.66f,
                damage = 28f,
                range = 4.35f,
                knockback = 13.5f,
                is360AoE = true,
                isHeavy = true,
                isFiery = true,
                slashRollTiltDeg = -12f,
                enemyYawTwistDeg = -35f,
                enemyPitchTiltDeg = 20f,
                enemyVerticalJolt = 0.28f,
                shakeIntensity = 0.22f,
                hitStopDuration = 0.072f
            )
        )
    }

    private fun startAerialSwordStrike(
        player: PlayerCharacter,
        cameraController: FollowCameraController,
        targetEnemy: EnemyController?,
        isFromGroundLeap: Boolean
    ) {
        beginSwordAction(
            player = player,
            state = AnimationState.SWORD_AERIAL_STRIKE,
            duration = 0.48f,
            isFiery = true
        )
        audioManager.playSwordHeavySkill(2)

        val fwd = facingVector(player.facingYaw)
        spawnAfterimage(player.position, player.facingYaw, 18f)
        spawnWindStreakBurst(player.position, fwd.x, fwd.z, count = 5)

        if (isFromGroundLeap) {
            spawnGroundDust(player.position, initialScale = 0.95f, expansionRate = 5.2f)
            player.isGrounded = false
            player.velocity.y = 6.8f
            applyForwardLunge(player, targetEnemy, lungeSpeed = 10.5f)
        } else {
            // Mid-air downward dive cleave
            player.velocity.y = -8.5f
            applyForwardLunge(player, targetEnemy, lungeSpeed = 9.2f)
        }

        spawnCurvedSwordArc(
            pos = Vector3(player.position.x + fwd.x * 1.15f, player.position.y + 1.25f, player.position.z + fwd.z * 1.15f),
            vel = Vector3(fwd.x * 5.0f, -4.0f, fwd.z * 5.0f),
            yawDeg = player.facingYaw,
            pitchDeg = 24f,
            rollTiltDeg = 88f,
            initialScale = 1.30f,
            expansionRate = 5.8f,
            isFiery = true
        )

        scheduledHits.add(
            ScheduledSwordHitWindow(
                triggerProgress = 0.52f,
                damage = 42f,
                range = 3.65f,
                knockback = 13.0f,
                is360AoE = true,
                isHeavy = true,
                isFiery = true,
                slashRollTiltDeg = 88f,
                enemyYawTwistDeg = 16f,
                enemyPitchTiltDeg = 28f, // Strong downward slam tilt
                enemyVerticalJolt = -0.10f,
                shakeIntensity = 0.22f,
                hitStopDuration = 0.075f
            )
        )
    }

    private fun beginSwordAction(
        player: PlayerCharacter,
        state: AnimationState,
        duration: Float,
        isFiery: Boolean
    ) {
        isSwordAttacking = true
        activeSwordState = state
        actionTimer = 0f
        actionDuration = duration
        currentActionIsFiery = isFiery
        orbitalStepDir = 0f
        orbitalStepSpeed = 0f
        scheduledHits.clear()
        hasPrevBladeSample = false
        player.animationState = state
        player.activeAttackProgress = 0f
    }

    // =========================================================================
    // Per-Frame Update & Post-Animation Real-Time Blade Trail Sampling
    // =========================================================================

    fun update(
        delta: Float,
        player: PlayerCharacter,
        cameraController: FollowCameraController
    ) {
        if (heavyCooldown > 0f) heavyCooldown = (heavyCooldown - delta).coerceAtLeast(0f)
        if (flashStepCooldown > 0f) flashStepCooldown = (flashStepCooldown - delta).coerceAtLeast(0f)
        if (hitStopTimer > 0f) hitStopTimer = (hitStopTimer - delta).coerceAtLeast(0f)

        if (comboResetTimer > 0f) {
            comboResetTimer = (comboResetTimer - delta).coerceAtLeast(0f)
            if (comboResetTimer == 0f && !isSwordAttacking) {
                currentComboStep = 0
            }
        }

        val lockedTarget = if (cameraController.isLockedOn) cameraController.lockedTarget else null

        // 1. Update Flash Step phantom movement & afterimages
        if (isFlashStepping) {
            flashStepTimer -= delta
            val progress = (1f - (flashStepTimer / flashStepDuration)).coerceIn(0f, 1f)
            player.animationState = AnimationState.SWORD_FLASH_STEP
            player.activeAttackProgress = progress

            // Spawn ghost afterimages along Flash Step trajectory
            afterimageSpawnAccum += delta
            if (afterimageSpawnAccum >= 0.055f) {
                afterimageSpawnAccum = 0f
                spawnAfterimage(player.position, player.facingYaw, 24f)
            }

            // Reappear arrival burst near end of Flash Step
            if (!hasSpawnedFlashArrivalVfx && progress >= 0.82f) {
                hasSpawnedFlashArrivalVfx = true
                spawnGroundDust(player.position, initialScale = 0.80f, expansionRate = 4.4f)
                onSpawnHitSparks(Vector3(player.position.x, player.position.y + 0.95f, player.position.z), 6, 3.8f, 0)
            }

            if (flashStepTimer <= 0f || !player.isDodging) {
                isFlashStepping = false
                player.activeAttackProgress = 0f
            }
        }

        // 2. Update Active Sword Attack, Multi-Directional Orbital Footwork, and Hit Windows
        if (isSwordAttacking) {
            // Brief hit-stop slows down the attack timer slightly for crisp impact weight
            val effectiveDelta = if (hitStopTimer > 0f) delta * 0.18f else delta
            actionTimer += effectiveDelta
            val progress = (actionTimer / actionDuration).coerceIn(0f, 1f)
            player.animationState = activeSwordState
            player.activeAttackProgress = progress

            // Keep tracking locked target orientation during multi-slash or aerial strike
            if (lockedTarget != null && lockedTarget.aiState != EnemyAiState.DEAD) {
                orientPlayerTowardTarget(player, lockedTarget)

                // Apply multi-directional orbital footwork around the locked enemy during 360 Spin & Rapid Flurry
                if (orbitalStepSpeed > 0.1f && progress < 0.78f) {
                    val dx = lockedTarget.entity.position.x - player.position.x
                    val dz = lockedTarget.entity.position.z - player.position.z
                    val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
                    val toTargetX = dx / dist
                    val toTargetZ = dz / dist
                    val tangentX = -toTargetZ * orbitalStepDir
                    val tangentZ = toTargetX * orbitalStepDir
                    // Maintain ideal 2.15m sword engagement radius while orbiting
                    val radialCorrection = ((dist - 2.15f) * 2.5f).coerceIn(-3.5f, 4.5f)
                    player.velocity.x = tangentX * orbitalStepSpeed + toTargetX * radialCorrection
                    player.velocity.z = tangentZ * orbitalStepSpeed + toTargetZ * radialCorrection
                }
            }

            // Downward dive acceleration during the second half of a ground-leaped Aerial Sword Strike
            if (activeSwordState == AnimationState.SWORD_AERIAL_STRIKE && progress in 0.42f..0.85f && !player.isGrounded) {
                if (player.velocity.y > -11.5f) {
                    player.velocity.y = -11.5f
                }
            }

            // Evaluate scheduled sword hit windows
            for (hitWin in scheduledHits) {
                if (!hitWin.hasTriggered && progress >= hitWin.triggerProgress) {
                    hitWin.hasTriggered = true
                    // Spawn companion slash arc on multi-hit sub-swings
                    if (activeSwordState == AnimationState.SWORD_DIAGONAL_CROSS && hitWin.triggerProgress > 0.5f) {
                        val fwd = facingVector(player.facingYaw)
                        spawnCurvedSwordArc(
                            pos = Vector3(player.position.x + fwd.x * 1.10f, player.position.y + 1.12f, player.position.z + fwd.z * 1.10f),
                            vel = Vector3(fwd.x * 4.5f, 0f, fwd.z * 4.5f),
                            yawDeg = player.facingYaw,
                            pitchDeg = 0f,
                            rollTiltDeg = hitWin.slashRollTiltDeg,
                            initialScale = 1.12f,
                            expansionRate = 4.2f,
                            isFiery = false
                        )
                    } else if (activeSwordState == AnimationState.SWORD_RAPID_FLURRY) {
                        val fwd = facingVector(player.facingYaw)
                        spawnCurvedSwordArc(
                            pos = Vector3(player.position.x + fwd.x * 1.18f, player.position.y + 1.10f, player.position.z + fwd.z * 1.18f),
                            vel = Vector3(fwd.x * 5.8f, 0f, fwd.z * 5.8f),
                            yawDeg = player.facingYaw + (rng.nextFloat() - 0.5f) * 18f,
                            pitchDeg = 0f,
                            rollTiltDeg = hitWin.slashRollTiltDeg,
                            initialScale = if (hitWin.isFiery) 1.32f else 1.05f,
                            expansionRate = if (hitWin.isFiery) 5.8f else 4.2f,
                            isFiery = hitWin.isFiery
                        )
                        if (hitWin.isFiery) {
                            spawnGroundDust(player.position, initialScale = 1.05f, expansionRate = 5.5f)
                        }
                    } else if (activeSwordState == AnimationState.SWORD_AERIAL_STRIKE) {
                        spawnGroundDust(player.position, initialScale = 1.20f, expansionRate = 6.5f)
                    }

                    evaluateSwordHitWindow(player, cameraController, hitWin)
                }
            }

            if (actionTimer >= actionDuration) {
                isSwordAttacking = false
                orbitalStepDir = 0f
                orbitalStepSpeed = 0f
                hasPrevBladeSample = false
                player.activeAttackProgress = 0f
            }
        } else {
            hasPrevBladeSample = false
        }

        // 3. Update active 3D Sword VFX
        updateVfx(delta, cameraController)
    }

    /**
     * Called right after `player.animationController.update(...)` so `drawnSwordNode.globalTransform`
     * reflects the exact current-frame skeletal pose. Spawns 3D ribbon trail segments along the actual blade trajectory.
     */
    fun postAnimationUpdate(delta: Float, player: PlayerCharacter) {
        if (!player.isSwordEquipped || !isSwordAttacking) {
            hasPrevBladeSample = false
            return
        }

        val progress = (actionTimer / actionDuration).coerceIn(0f, 1f)
        // Emit glowing blade ribbon during the active swing portion of the animation
        if (progress !in 0.08f..0.90f) {
            hasPrevBladeSample = false
            return
        }

        player.animationController.getBladeWorldSegment(currBladeBase, currBladeTip)

        if (hasPrevBladeSample) {
            val tipDx = currBladeTip.x - prevBladeTip.x
            val tipDy = currBladeTip.y - prevBladeTip.y
            val tipDz = currBladeTip.z - prevBladeTip.z
            val tipMoveDist = sqrt(tipDx * tipDx + tipDy * tipDy + tipDz * tipDz)

            if (tipMoveDist >= 0.035f) {
                val midBaseX = (prevBladeBase.x + currBladeBase.x) * 0.5f
                val midBaseY = (prevBladeBase.y + currBladeBase.y) * 0.5f
                val midBaseZ = (prevBladeBase.z + currBladeBase.z) * 0.5f

                val midTipX = (prevBladeTip.x + currBladeTip.x) * 0.5f
                val midTipY = (prevBladeTip.y + currBladeTip.y) * 0.5f
                val midTipZ = (prevBladeTip.z + currBladeTip.z) * 0.5f

                val center = Vector3(
                    (midBaseX + midTipX) * 0.5f,
                    (midBaseY + midTipY) * 0.5f,
                    (midBaseZ + midTipZ) * 0.5f
                )
                val bladeVec = Vector3(midTipX - midBaseX, midTipY - midBaseY, midTipZ - midBaseZ)
                val bladeLen = bladeVec.len().coerceIn(0.65f, 1.35f)
                bladeVec.nor()

                val swingVec = Vector3(tipDx / tipMoveDist, tipDy / tipMoveDist, tipDz / tipMoveDist)
                val model = if (currentActionIsFiery) bladeTrailFireModel else bladeTrailCyanModel
                val seg = ActiveBladeTrailSegment(
                    instance = ModelInstance(model),
                    center = center,
                    bladeDir = bladeVec,
                    swingDir = swingVec,
                    bladeLen = bladeLen,
                    chordWidth = (tipMoveDist * 1.45f).coerceIn(0.18f, 0.68f),
                    maxLifetime = if (currentActionIsFiery) 0.21f else 0.16f
                )
                updateBladeTrailSegmentTransform(seg, 1f)
                bladeTrailSegments.add(seg)
            }
        }

        prevBladeBase.set(currBladeBase)
        prevBladeTip.set(currBladeTip)
        hasPrevBladeSample = true
    }

    private fun evaluateSwordHitWindow(
        player: PlayerCharacter,
        cameraController: FollowCameraController,
        hitWin: ScheduledSwordHitWindow
    ) {
        val fwd = facingVector(player.facingYaw)
        var connectedAny = false

        for (ec in entityManager.enemyControllers) {
            if (ec.aiState == EnemyAiState.DEAD) continue
            val dx = ec.entity.position.x - player.position.x
            val dz = ec.entity.position.z - player.position.z
            val dy = abs(ec.entity.position.y - player.position.y)
            val dist = sqrt(dx * dx + dz * dz)

            if (dist <= hitWin.range + ec.collisionRadius && dy <= 2.4f) {
                val dot = if (dist > 0.01f) (dx / dist) * fwd.x + (dz / dist) * fwd.z else 1f
                val minDot = if (hitWin.is360AoE) -1.0f else -0.10f
                if (dot >= minDot) {
                    connectedAny = true
                    val dirX = if (dist > 0.01f) dx / dist else fwd.x
                    val dirZ = if (dist > 0.01f) dz / dist else fwd.z
                    val kx = dirX * hitWin.knockback
                    val kz = dirZ * hitWin.knockback

                    val defeated = ec.receiveDamage(
                        amount = hitWin.damage,
                        knockbackX = kx,
                        knockbackZ = kz,
                        isHeavyImpact = hitWin.isHeavy,
                        audioManager = audioManager,
                        slashYawTwistDeg = hitWin.enemyYawTwistDeg,
                        slashPitchTiltDeg = hitWin.enemyPitchTiltDeg,
                        verticalJolt = hitWin.enemyVerticalJolt
                    )

                    val impactPos = Vector3(
                        ec.entity.position.x - dirX * 0.28f,
                        ec.entity.position.y + 1.15f,
                        ec.entity.position.z - dirZ * 0.28f
                    )

                    // Distinct 3D Hit Flash Star & sparks ONLY when an attack connects
                    spawnHitFlashStar(impactPos, isFiery = hitWin.isFiery || hitWin.isHeavy || defeated)
                    onSpawnHitSparks(
                        impactPos,
                        if (defeated) 22 else if (hitWin.isHeavy) 15 else 10,
                        if (hitWin.isHeavy) 6.6f else 4.8f,
                        if (defeated) 2 else if (hitWin.isFiery || hitWin.isHeavy) 1 else 0
                    )

                    onRecordDamagePopup(
                        Vector3(ec.entity.position.x, ec.entity.position.y + 2.15f, ec.entity.position.z),
                        hitWin.damage,
                        hitWin.isHeavy
                    )
                }
            }
        }

        // Trigger sword hit impact sound, brief hit-stop, and camera shake ONLY when an enemy was actually hit
        if (connectedAny) {
            audioManager.playSwordHitImpact(isHeavy = hitWin.isHeavy)
            hitStopTimer = maxOf(hitStopTimer, hitWin.hitStopDuration)
            cameraController.triggerCameraShake(
                intensity = hitWin.shakeIntensity,
                duration = if (hitWin.isHeavy) 0.22f else 0.12f
            )
        }
    }

    private fun updateVfx(delta: Float, cameraController: FollowCameraController) {
        // 1. Real-time 3D Blade Trail Ribbon Segments
        val trailIter = bladeTrailSegments.iterator()
        while (trailIter.hasNext()) {
            val seg = trailIter.next()
            seg.age += delta
            if (seg.age >= seg.maxLifetime) {
                trailIter.remove()
                continue
            }
            val lifeRatio = (1f - seg.age / seg.maxLifetime).coerceIn(0f, 1f)
            updateBladeTrailSegmentTransform(seg, lifeRatio)
        }

        // 2. Curved Sword Energy Arcs
        val arcIter = curvedArcs.iterator()
        while (arcIter.hasNext()) {
            val arc = arcIter.next()
            arc.age += delta
            if (arc.age >= arc.maxLifetime) {
                arcIter.remove()
                continue
            }
            arc.position.mulAdd(arc.velocity, delta)
            val lifeRatio = (1f - arc.age / arc.maxLifetime).coerceIn(0f, 1f)
            val s = arc.initialScale + arc.age * arc.expansionRate
            arc.instance.transform.idt()
                .translate(arc.position.x, arc.position.y, arc.position.z)
                .rotate(0f, 1f, 0f, arc.yawDeg)
                .rotate(1f, 0f, 0f, arc.pitchDeg)
                .rotate(0f, 0f, 1f, arc.rollTiltDeg)
                .scale(s, lifeRatio.coerceAtLeast(0.15f), s)
        }

        // 3. Inferno Crescent Flaming Wave Projectiles
        val infIter = infernoCrescents.iterator()
        while (infIter.hasNext()) {
            val crescent = infIter.next()
            crescent.age += delta
            if (crescent.age >= crescent.maxLifetime) {
                infIter.remove()
                continue
            }

            // Gentle homing toward locked enemy if active
            val locked = if (cameraController.isLockedOn) cameraController.lockedTarget else null
            if (locked != null && locked.aiState != EnemyAiState.DEAD) {
                val tx = locked.entity.position.x - crescent.position.x
                val ty = (locked.entity.position.y + 1.05f) - crescent.position.y
                val tz = locked.entity.position.z - crescent.position.z
                val dist = sqrt(tx * tx + ty * ty + tz * tz)
                if (dist in 0.5f..28f) {
                    val spd = crescent.velocity.len()
                    crescent.velocity.x = MathUtils.lerp(crescent.velocity.x, (tx / dist) * spd, delta * 5.5f)
                    crescent.velocity.y = MathUtils.lerp(crescent.velocity.y, (ty / dist) * spd, delta * 5.5f)
                    crescent.velocity.z = MathUtils.lerp(crescent.velocity.z, (tz / dist) * spd, delta * 5.5f)
                }
            }

            crescent.position.mulAdd(crescent.velocity, delta)
            val yaw = Math.toDegrees(atan2(crescent.velocity.x.toDouble(), crescent.velocity.z.toDouble())).toFloat()
            val scale = 1.15f + crescent.age * 0.85f
            crescent.instance.transform.idt()
                .translate(crescent.position.x, crescent.position.y, crescent.position.z)
                .rotate(0f, 1f, 0f, yaw)
                .scale(scale, 1.05f, scale)

            // Check enemy collisions
            for (ec in entityManager.enemyControllers) {
                if (ec.aiState == EnemyAiState.DEAD || crescent.hitEnemies.contains(ec.entity.id)) continue
                val dx = ec.entity.position.x - crescent.position.x
                val dz = ec.entity.position.z - crescent.position.z
                val dy = abs((ec.entity.position.y + 1.05f) - crescent.position.y)
                val hitDist = ec.collisionRadius + crescent.hitRadius
                if (dx * dx + dz * dz <= hitDist * hitDist && dy <= 1.65f) {
                    crescent.hitEnemies.add(ec.entity.id)
                    val vLen = sqrt(crescent.velocity.x * crescent.velocity.x + crescent.velocity.z * crescent.velocity.z).coerceAtLeast(0.01f)
                    val kx = (crescent.velocity.x / vLen) * crescent.knockback
                    val kz = (crescent.velocity.z / vLen) * crescent.knockback

                    val defeated = ec.receiveDamage(
                        amount = crescent.damage,
                        knockbackX = kx,
                        knockbackZ = kz,
                        isHeavyImpact = true,
                        audioManager = audioManager,
                        slashYawTwistDeg = -34f,
                        slashPitchTiltDeg = 20f,
                        verticalJolt = 0.22f
                    )
                    audioManager.playSwordHitImpact(isHeavy = true)
                    hitStopTimer = maxOf(hitStopTimer, 0.068f)
                    cameraController.triggerCameraShake(0.22f, 0.24f)

                    val hitPos = Vector3(ec.entity.position.x, ec.entity.position.y + 1.18f, ec.entity.position.z)
                    spawnHitFlashStar(hitPos, isFiery = true)
                    spawnGroundDust(ec.entity.position, initialScale = 1.1f, expansionRate = 5.6f)
                    onSpawnHitSparks(hitPos, if (defeated) 24 else 18, 7.2f, if (defeated) 2 else 1)
                    onRecordDamagePopup(
                        Vector3(ec.entity.position.x, ec.entity.position.y + 2.2f, ec.entity.position.z),
                        crescent.damage,
                        true
                    )
                }
            }
        }

        // 4. Dragon Tempest Expanding Cyclone Shockwaves
        val tempIter = dragonTempests.iterator()
        while (tempIter.hasNext()) {
            val dt = tempIter.next()
            dt.age += delta
            if (dt.age >= dt.maxLifetime) {
                tempIter.remove()
                continue
            }
            dt.spinYaw = (dt.spinYaw + delta * 780f) % 360f
            dt.currentScale += delta * dt.expansionRate
            val heightRatio = (1f - (dt.age / dt.maxLifetime) * 0.55f).coerceIn(0.25f, 1.2f)
            dt.instance.transform.idt()
                .translate(dt.position.x, dt.position.y, dt.position.z)
                .rotate(0f, 1f, 0f, dt.spinYaw)
                .scale(dt.currentScale, heightRatio, dt.currentScale)
        }

        // 5. Phantom Afterimages
        val ghostIter = afterimages.iterator()
        while (ghostIter.hasNext()) {
            val g = ghostIter.next()
            g.age += delta
            if (g.age >= g.maxLifetime) {
                ghostIter.remove()
                continue
            }
            val lifeRatio = (1f - g.age / g.maxLifetime).coerceIn(0.1f, 1f)
            g.instance.transform.idt()
                .translate(g.position.x, g.position.y, g.position.z)
                .rotate(0f, 1f, 0f, g.yawDeg)
                .rotate(1f, 0f, 0f, g.forwardLeanDeg)
                .scale(lifeRatio, 0.92f + lifeRatio * 0.08f, lifeRatio)
        }

        // 6. Fast Wind Streaks
        val windIter = windStreaks.iterator()
        while (windIter.hasNext()) {
            val ws = windIter.next()
            ws.age += delta
            if (ws.age >= ws.maxLifetime) {
                windIter.remove()
                continue
            }
            ws.position.mulAdd(ws.velocity, delta)
            val lifeRatio = (1f - ws.age / ws.maxLifetime).coerceIn(0.1f, 1f)
            ws.instance.transform.idt()
                .translate(ws.position.x, ws.position.y, ws.position.z)
                .rotate(0f, 1f, 0f, ws.yawDeg)
                .rotate(1f, 0f, 0f, ws.pitchDeg)
                .scale(lifeRatio, lifeRatio, ws.initialLengthScale * (0.6f + lifeRatio * 0.6f))
        }

        // 7. Ground Dust Bursts
        val dustIter = groundDusts.iterator()
        while (dustIter.hasNext()) {
            val gd = dustIter.next()
            gd.age += delta
            if (gd.age >= gd.maxLifetime) {
                dustIter.remove()
                continue
            }
            val lifeRatio = (1f - gd.age / gd.maxLifetime).coerceIn(0.1f, 1f)
            val s = gd.initialScale + gd.age * gd.expansionRate
            gd.instance.transform.idt()
                .translate(gd.position.x, gd.position.y + 0.05f, gd.position.z)
                .rotate(0f, 1f, 0f, gd.age * 160f)
                .scale(s, lifeRatio, s)
        }

        // 8. Hit Flash Stars (at impact point)
        val flashIter = hitFlashStars.iterator()
        while (flashIter.hasNext()) {
            val hf = flashIter.next()
            hf.age += delta
            if (hf.age >= hf.maxLifetime) {
                flashIter.remove()
                continue
            }
            val lifeRatio = (1f - hf.age / hf.maxLifetime).coerceIn(0.1f, 1f)
            val s = hf.initialScale * (0.65f + (1f - lifeRatio) * 1.15f)
            hf.spinDeg = (hf.spinDeg + delta * 420f) % 360f
            hf.instance.transform.idt()
                .translate(hf.position.x, hf.position.y, hf.position.z)
                .rotate(0f, 1f, 0f, hf.spinDeg)
                .scale(s, s * lifeRatio, s)
        }
    }

    fun render(modelBatch: ModelBatch, environment: Environment) {
        for (seg in bladeTrailSegments) {
            modelBatch.render(seg.instance, environment)
        }
        for (arc in curvedArcs) {
            modelBatch.render(arc.instance, environment)
        }
        for (crescent in infernoCrescents) {
            modelBatch.render(crescent.instance, environment)
        }
        for (dt in dragonTempests) {
            modelBatch.render(dt.instance, environment)
        }
        for (g in afterimages) {
            modelBatch.render(g.instance, environment)
        }
        for (ws in windStreaks) {
            modelBatch.render(ws.instance, environment)
        }
        for (gd in groundDusts) {
            modelBatch.render(gd.instance, environment)
        }
        for (hf in hitFlashStars) {
            modelBatch.render(hf.instance, environment)
        }
    }

    fun reset() {
        isSwordAttacking = false
        isFlashStepping = false
        currentComboStep = 0
        comboResetTimer = 0f
        heavyCooldown = 0f
        flashStepCooldown = 0f
        hitStopTimer = 0f
        orbitalStepDir = 0f
        orbitalStepSpeed = 0f
        hasPrevBladeSample = false
        scheduledHits.clear()
        bladeTrailSegments.clear()
        curvedArcs.clear()
        infernoCrescents.clear()
        dragonTempests.clear()
        afterimages.clear()
        windStreaks.clear()
        groundDusts.clear()
        hitFlashStars.clear()
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun updateBladeTrailSegmentTransform(seg: ActiveBladeTrailSegment, lifeRatio: Float) {
        val yaw = Math.toDegrees(atan2(seg.bladeDir.x.toDouble(), seg.bladeDir.z.toDouble())).toFloat()
        val pitch = Math.toDegrees(- kotlin.math.asin(seg.bladeDir.y.coerceIn(-1f, 1f).toDouble())).toFloat()
        val roll = Math.toDegrees(atan2(seg.swingDir.y.toDouble(), sqrt((seg.swingDir.x * seg.swingDir.x + seg.swingDir.z * seg.swingDir.z).toDouble()).coerceAtLeast(0.01))).toFloat()

        tmpQuat.idt()
        tmpQuat.setEulerAngles(yaw, pitch, roll)
        seg.instance.transform.idt()
            .translate(seg.center.x, seg.center.y, seg.center.z)
            .rotate(tmpQuat)
            .scale(
                (seg.chordWidth * (0.45f + 0.55f * lifeRatio)).coerceAtLeast(0.06f),
                lifeRatio.coerceAtLeast(0.2f),
                seg.bladeLen
            )
    }

    private fun resolveCombatTarget(
        player: PlayerCharacter,
        cameraController: FollowCameraController
    ): EnemyController? {
        val locked = if (cameraController.isLockedOn) cameraController.lockedTarget else null
        if (locked != null && locked.aiState != EnemyAiState.DEAD) {
            return locked
        }
        val primary = entityManager.primaryEnemy()
        if (primary != null && primary.aiState != EnemyAiState.DEAD) {
            val dx = primary.entity.position.x - player.position.x
            val dz = primary.entity.position.z - player.position.z
            if (dx * dx + dz * dz <= 12f * 12f) {
                return primary
            }
        }
        return null
    }

    private fun orientPlayerTowardTarget(player: PlayerCharacter, target: EnemyController?) {
        if (target == null || target.aiState == EnemyAiState.DEAD) return
        val dx = target.entity.position.x - player.position.x
        val dz = target.entity.position.z - player.position.z
        if (dx * dx + dz * dz > 0.005f) {
            player.facingYaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
        }
    }

    private fun applyForwardLunge(
        player: PlayerCharacter,
        target: EnemyController?,
        lungeSpeed: Float
    ) {
        val fwd = facingVector(player.facingYaw)
        var effectiveSpeed = lungeSpeed
        if (target != null && target.aiState != EnemyAiState.DEAD) {
            val dx = target.entity.position.x - player.position.x
            val dz = target.entity.position.z - player.position.z
            val dist = sqrt(dx * dx + dz * dz)
            val minContactDist = player.collisionRadius + target.collisionRadius + 0.65f
            if (dist <= minContactDist) {
                effectiveSpeed = 0f
            } else if (dist <= minContactDist + 1.35f) {
                effectiveSpeed = lungeSpeed * ((dist - minContactDist) / 1.35f).coerceIn(0f, 1f)
            }
        }
        player.velocity.x = fwd.x * effectiveSpeed
        player.velocity.z = fwd.z * effectiveSpeed
    }

    private fun facingVector(yawDeg: Float): Vector3 {
        val rad = Math.toRadians(yawDeg.toDouble()).toFloat()
        return Vector3(sin(rad), 0f, cos(rad))
    }

    private fun spawnCurvedSwordArc(
        pos: Vector3,
        vel: Vector3,
        yawDeg: Float,
        pitchDeg: Float,
        rollTiltDeg: Float,
        initialScale: Float,
        expansionRate: Float,
        isFiery: Boolean
    ) {
        val model = if (isFiery) curvedArcFireModel else curvedArcCyanModel
        curvedArcs.add(
            ActiveCurvedArc(
                instance = ModelInstance(model),
                position = Vector3(pos),
                velocity = Vector3(vel),
                yawDeg = yawDeg,
                pitchDeg = pitchDeg,
                rollTiltDeg = rollTiltDeg,
                initialScale = initialScale,
                expansionRate = expansionRate
            )
        )
    }

    private fun spawnAfterimage(pos: Vector3, yawDeg: Float, forwardLeanDeg: Float) {
        afterimages.add(
            ActiveAfterimage(
                instance = ModelInstance(afterimageGhostModel),
                position = Vector3(pos),
                yawDeg = yawDeg,
                forwardLeanDeg = forwardLeanDeg
            )
        )
    }

    private fun spawnGroundDust(pos: Vector3, initialScale: Float, expansionRate: Float) {
        val groundY = worldManager.getWalkableSurfaceHeight(pos.x, pos.z)
        groundDusts.add(
            ActiveGroundDust(
                instance = ModelInstance(groundDustModel),
                position = Vector3(pos.x, groundY, pos.z),
                initialScale = initialScale,
                expansionRate = expansionRate
            )
        )
    }

    private fun spawnWindStreakBurst(origin: Vector3, dirX: Float, dirZ: Float, count: Int) {
        val baseYaw = Math.toDegrees(atan2(dirX.toDouble(), dirZ.toDouble())).toFloat()
        for (i in 0 until count) {
            val sideOffset = (rng.nextFloat() * 2f - 1f) * 0.85f
            val heightOffset = 0.45f + rng.nextFloat() * 1.15f
            val rightX = -dirZ
            val rightZ = dirX
            val startPos = Vector3(
                origin.x + rightX * sideOffset,
                origin.y + heightOffset,
                origin.z + rightZ * sideOffset
            )
            val spd = 11f + rng.nextFloat() * 6f
            windStreaks.add(
                ActiveWindStreak(
                    instance = ModelInstance(windStreakModel),
                    position = startPos,
                    velocity = Vector3(dirX * spd, (rng.nextFloat() - 0.5f) * 1.8f, dirZ * spd),
                    yawDeg = baseYaw,
                    pitchDeg = (rng.nextFloat() - 0.5f) * 12f,
                    initialLengthScale = 0.85f + rng.nextFloat() * 0.55f
                )
            )
        }
    }

    private fun spawnHitFlashStar(pos: Vector3, isFiery: Boolean) {
        val model = if (isFiery) hitFlashFireModel else hitFlashCyanModel
        hitFlashStars.add(
            ActiveHitFlashStar(
                instance = ModelInstance(model),
                position = Vector3(pos),
                initialScale = if (isFiery) 1.15f else 0.82f,
                spinDeg = rng.nextFloat() * 360f
            )
        )
    }
}
