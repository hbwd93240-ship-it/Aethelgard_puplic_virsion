package com.aethelgard.survival.core.combat

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.camera.FollowCameraController
import com.aethelgard.survival.core.entities.EnemyAiState
import com.aethelgard.survival.core.entities.EnemyController
import com.aethelgard.survival.core.entities.EntityManager
import com.aethelgard.survival.core.player.AnimationState
import com.aethelgard.survival.core.player.MartialLimb
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
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class ActiveFistLimbTrailSegment(
    val instance: ModelInstance,
    val center: Vector3,
    val limbDir: Vector3,
    val swingDir: Vector3,
    val limbLen: Float,
    val chordWidth: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 0.15f
)

class ActiveFistWindArc(
    val instance: ModelInstance,
    val position: Vector3,
    val velocity: Vector3,
    val yawDeg: Float,
    val pitchDeg: Float,
    val rollTiltDeg: Float,
    val initialScale: Float,
    val expansionRate: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 0.22f
)

class ActiveAirPressureRing(
    val instance: ModelInstance,
    val position: Vector3,
    val velocity: Vector3,
    val yawDeg: Float,
    val pitchDeg: Float,
    val initialScale: Float,
    val expansionRate: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 0.28f
)

class ActiveRadialChiBurst(
    val instance: ModelInstance,
    val position: Vector3,
    var spinYaw: Float = 0f,
    var currentScale: Float = 0.78f,
    val expansionRate: Float = 7.4f,
    var age: Float = 0f,
    val maxLifetime: Float = 0.40f
)

private data class ScheduledFistHitWindow(
    val triggerProgress: Float,
    val damage: Float,
    val range: Float,
    val knockback: Float,
    val is360AoE: Boolean,
    val isKick: Boolean,
    val isHeavy: Boolean,
    val isFinisher: Boolean,
    val isGoldenChi: Boolean,
    val arcRollTiltDeg: Float,
    val enemyYawTwistDeg: Float,
    val enemyPitchTiltDeg: Float,
    val enemyVerticalJolt: Float,
    val shakeIntensity: Float,
    val hitStopDuration: Float,
    var hasTriggered: Boolean = false
)

/**
 * Milestone 4 Dedicated Anime Fists / Martial Arts Combat Controller.
 * Matches the architectural quality, fluidity, responsiveness, and impact of SwordCombatController
 * while keeping FISTS and SWORD combat completely independent:
 *
 * - 6-Stage Fast Martial Arts Combo (ATTACK button):
 *   1. Fast Right Jab (FIST_RIGHT_JAB)
 *   2. Left Cross with forward movement (FIST_LEFT_CROSS)
 *   3. Powerful Side Kick (FIST_SIDE_KICK)
 *   4. Spinning Roundhouse Kick (FIST_SPIN_ROUNDHOUSE)
 *   5. Rising High Kick (FIST_RISING_HIGH_KICK)
 *   6. Contextual Spectacular Finisher:
 *      - Cyclone Hurricane Kick (FIST_FINISHER_CYCLONE_KICK) when neutral or circling/strafing
 *      - Flying Dragon Strike (FIST_FINISHER_FLYING_STRIKE) when advancing forward, sprinting, or airborne
 *
 * - Contextual Heavy Martial Arts Special Strikes (HEAVY button):
 *   - Dragon Haymaker (FIST_HEAVY_DRAGON_HAYMAKER): Coiled rotational punch + air-pressure wave
 *   - Thunder Breaker (FIST_HEAVY_THUNDER_BREAKER): 360-degree heavy spinning kick + golden chi shockwave
 *   - Meteor Strike (FIST_HEAVY_METEOR_STRIKE): High leap / aerial dive kick + landing shockwave & ground dust
 *
 * - Real-time 3D limb-following wind trails, air-pressure rings, radial chi bursts, unarmed afterimages,
 *   ground dust, hit-only 3D impact flash stars, hit-stop, and camera shake.
 */
class FistsCombatController(
    private val modelFactory: ProceduralModelFactory,
    private val worldManager: WorldManager,
    private val entityManager: EntityManager,
    private val audioManager: AudioManager,
    private val onRecordDamagePopup: (Vector3, Float, Boolean) -> Unit,
    private val onSpawnHitSparks: (Vector3, Int, Float, Int) -> Unit
) {
    private lateinit var limbTrailWindModel: Model
    private lateinit var limbTrailChiModel: Model
    private lateinit var windArcModel: Model
    private lateinit var chiArcModel: Model
    private lateinit var airPressureWindModel: Model
    private lateinit var airPressureChiModel: Model
    private lateinit var radialChiBurstModel: Model
    private lateinit var martialAfterimageModel: Model
    private lateinit var windStreakModel: Model
    private lateinit var groundDustModel: Model
    private lateinit var hitFlashWindModel: Model
    private lateinit var hitFlashChiModel: Model

    val limbTrailSegments = mutableListOf<ActiveFistLimbTrailSegment>()
    val windArcs = mutableListOf<ActiveFistWindArc>()
    val airPressureRings = mutableListOf<ActiveAirPressureRing>()
    val radialChiBursts = mutableListOf<ActiveRadialChiBurst>()
    val afterimages = mutableListOf<ActiveAfterimage>()
    val windStreaks = mutableListOf<ActiveWindStreak>()
    val groundDusts = mutableListOf<ActiveGroundDust>()
    val hitFlashStars = mutableListOf<ActiveHitFlashStar>()

    // Active Fist Action State
    var isFistAttacking: Boolean = false
        private set
    var activeFistState: AnimationState = AnimationState.IDLE
        private set
    private var actionTimer: Float = 0f
    private var actionDuration: Float = 0.28f
    private var currentActionIsGoldenChi: Boolean = false
    private var currentActionIsHeavy: Boolean = false
    private var activeTrackedLimb: MartialLimb = MartialLimb.RIGHT_FIST
    private var secondaryTrackedLimb: MartialLimb? = null

    // 6-Stage Martial Arts Combo Chain (1..6)
    var currentComboStep: Int = 0
        private set
    val maxComboSteps: Int = 6
    private var comboResetTimer: Float = 0f

    // Contextual / Cycling Heavy Martial Arts Skill (0 = Dragon Haymaker, 1 = Thunder Breaker, 2 = Meteor Strike)
    var heavySkillIndex: Int = 0
        private set

    // Live movement input snapshot updated each frame from PlayerController for contextual attack selection
    var liveMoveInputX: Float = 0f
    var liveMoveInputY: Float = 0f

    /**
     * Returns the contextual HUD label for the HEAVY button when Fists are equipped.
     */
    fun resolveHeavySkillHudLabel(player: PlayerCharacter): String {
        if (!player.isGrounded || currentComboStep == 5) return "METEOR"
        if (liveMoveInputY > 0.30f || currentComboStep in 1..2) return "DRAGON"
        if (abs(liveMoveInputX) > 0.30f || liveMoveInputY < -0.25f || currentComboStep in 3..4) return "THUNDER"
        return when (heavySkillIndex) {
            0 -> "DRAGON"
            1 -> "THUNDER"
            else -> "METEOR"
        }
    }

    // Cooldowns & Hit-Stop
    var heavyCooldown: Float = 0f
        private set
    val heavyMaxCooldown: Float = 0.82f

    var hitStopTimer: Float = 0f
        private set

    // Multi-directional orbital footwork around locked target
    private var orbitalStepDir: Float = 0f
    private var orbitalStepSpeed: Float = 0f

    // Scheduled hit windows within the active martial arts animation
    private val scheduledHits = mutableListOf<ScheduledFistHitWindow>()

    // Real-time 3D martial limb tracking state
    private val prevLimbBase = Vector3()
    private val prevLimbTip = Vector3()
    private val currLimbBase = Vector3()
    private val currLimbTip = Vector3()
    private var hasPrevLimbSample: Boolean = false
    private val tmpQuat = Quaternion()
    private val rng = Random(404)

    fun initialize() {
        limbTrailWindModel = modelFactory.createFistWindTrailSegmentModel(isGoldenChi = false)
        limbTrailChiModel = modelFactory.createFistWindTrailSegmentModel(isGoldenChi = true)
        windArcModel = modelFactory.createFistWindArcModel(isGoldenChi = false)
        chiArcModel = modelFactory.createFistWindArcModel(isGoldenChi = true)
        airPressureWindModel = modelFactory.createAirPressureRingModel(isGoldenChi = false)
        airPressureChiModel = modelFactory.createAirPressureRingModel(isGoldenChi = true)
        radialChiBurstModel = modelFactory.createRadialChiBurstModel()
        martialAfterimageModel = modelFactory.createMartialAfterimageGhostModel()
        windStreakModel = modelFactory.createWindStreakModel()
        groundDustModel = modelFactory.createGroundDustModel()
        hitFlashWindModel = modelFactory.createHitFlashStarModel(isFiery = false)
        hitFlashChiModel = modelFactory.createHitFlashStarModel(isFiery = true)
    }

    /**
     * Triggers the Fast 6-Stage Martial Arts Combo using the existing ATTACK button:
     * 1. Fast Right Jab (FIST_RIGHT_JAB)
     * 2. Left Cross with forward movement (FIST_LEFT_CROSS)
     * 3. Powerful Side Kick (FIST_SIDE_KICK)
     * 4. Spinning Roundhouse Kick (FIST_SPIN_ROUNDHOUSE)
     * 5. Rising High Kick (FIST_RISING_HIGH_KICK)
     * 6. Contextual Spectacular Finisher:
     *    - Flying Dragon Strike (FIST_FINISHER_FLYING_STRIKE) if advancing forward, sprinting, or airborne
     *    - Cyclone Hurricane Kick (FIST_FINISHER_CYCLONE_KICK) if neutral or circling/strafing
     */
    fun triggerLightFistAttack(
        player: PlayerCharacter,
        cameraController: FollowCameraController
    ) {
        if (player.isDodging) return
        // Allow responsive combo chaining once the current martial arts strike passes 56% progress
        if (isFistAttacking && actionTimer < actionDuration * 0.56f) return

        val targetEnemy = resolveCombatTarget(player, cameraController)
        orientPlayerForMartialStrike(player, targetEnemy, cameraController)

        // 1. Mid-Air Martial Arts Strike -> Flying Dragon Strike when jumping (not mid-combo hop)
        if (!player.isGrounded && currentComboStep == 0) {
            if (player.hasUsedAirAttack) return
            player.hasUsedAirAttack = true
            startFlyingDragonStrikeFinisher(player, targetEnemy, isFromAir = true)
            return
        }

        // 2. Advance 6-Stage Martial Arts Combo Chain
        currentComboStep = if (comboResetTimer > 0f) {
            (currentComboStep % maxComboSteps) + 1
        } else {
            1
        }
        comboResetTimer = 1.15f

        when (currentComboStep) {
            1 -> startRightJab(player, targetEnemy)
            2 -> startLeftCross(player, targetEnemy)
            3 -> startSideKick(player, targetEnemy)
            4 -> startSpinningRoundhouseKick(player, targetEnemy, cameraController)
            5 -> startRisingHighKick(player, targetEnemy)
            else -> {
                // Step 6: Contextual Finisher selection based on live movement & sprint state
                val isAdvancingForward = liveMoveInputY > 0.25f || player.isSprinting
                if (isAdvancingForward) {
                    startFlyingDragonStrikeFinisher(player, targetEnemy, isFromAir = false)
                } else {
                    startCycloneHurricaneKickFinisher(player, targetEnemy, cameraController)
                }
            }
        }
    }

    /**
     * Triggers a Contextual Heavy Martial Arts Special Attack using the existing HEAVY button:
     * - Airborne or after Rising High Kick (Step 5) -> Meteor Strike (high leap / diving kick + shockwave)
     * - Advancing forward or after Punch Combo (Steps 1..2) -> Dragon Haymaker (coiled rotational Dragon Fist + air-pressure wave)
     * - Strafing/circling/retreating or after Kick Combo (Steps 3..4) -> Thunder Breaker (360 heavy spinning kick + golden chi burst)
     * - Neutral standing -> Cycles through Dragon Haymaker -> Thunder Breaker -> Meteor Strike
     */
    fun triggerHeavyFistAttack(
        player: PlayerCharacter,
        cameraController: FollowCameraController
    ) {
        if (heavyCooldown > 0f || player.isDodging) return
        if (isFistAttacking && actionTimer < actionDuration * 0.68f) return

        val targetEnemy = resolveCombatTarget(player, cameraController)
        orientPlayerForMartialStrike(player, targetEnemy, cameraController)

        heavyCooldown = heavyMaxCooldown
        val prevCombo = currentComboStep
        currentComboStep = 0
        comboResetTimer = 0f

        if (!player.isGrounded) {
            player.hasUsedAirAttack = true
            startMeteorStrike(player, targetEnemy, isFromGroundLeap = false)
            return
        }

        val chosenSkill = when {
            prevCombo == 5 -> 2 // Follow up Rising High Kick with Meteor Strike
            liveMoveInputY > 0.30f || prevCombo in 1..2 -> 0 // Forward movement or punch chain -> Dragon Haymaker
            abs(liveMoveInputX) > 0.30f || liveMoveInputY < -0.25f || prevCombo in 3..4 -> 1 // Circling or kick chain -> Thunder Breaker
            else -> {
                val idx = heavySkillIndex
                heavySkillIndex = (heavySkillIndex + 1) % 3
                idx
            }
        }

        when (chosenSkill) {
            0 -> startDragonHaymaker(player, targetEnemy)
            1 -> startThunderBreaker(player, targetEnemy, cameraController)
            else -> startMeteorStrike(player, targetEnemy, isFromGroundLeap = true)
        }
    }

    // =========================================================================
    // 6-Stage Fast Martial Arts Combo Starters
    // =========================================================================

    private fun startRightJab(
        player: PlayerCharacter,
        targetEnemy: EnemyController?
    ) {
        beginFistAction(
            player = player,
            state = AnimationState.FIST_RIGHT_JAB,
            duration = 0.23f,
            primaryLimb = MartialLimb.RIGHT_FIST,
            secondaryLimb = null,
            isGoldenChi = false,
            isHeavy = false
        )
        applyForwardStepMomentum(player, targetEnemy, stepSpeed = 4.6f)
        audioManager.playFistComboSwing(step = 1, isKick = false)

        val fwd = facingVector(player.facingYaw)
        spawnAirPressureRing(
            pos = Vector3(player.position.x + fwd.x * 0.92f, player.position.y + 1.24f, player.position.z + fwd.z * 0.92f),
            vel = Vector3(fwd.x * 5.2f, 0f, fwd.z * 5.2f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            initialScale = 0.52f,
            expansionRate = 3.2f,
            isGoldenChi = false
        )

        scheduledHits.add(
            ScheduledFistHitWindow(
                triggerProgress = 0.36f,
                damage = 11f,
                range = 2.35f,
                knockback = 2.4f,
                is360AoE = false,
                isKick = false,
                isHeavy = false,
                isFinisher = false,
                isGoldenChi = false,
                arcRollTiltDeg = 6f,
                enemyYawTwistDeg = -18f,
                enemyPitchTiltDeg = 6f,
                enemyVerticalJolt = 0.02f,
                shakeIntensity = 0.04f,
                hitStopDuration = 0.028f
            )
        )
    }

    private fun startLeftCross(
        player: PlayerCharacter,
        targetEnemy: EnemyController?
    ) {
        beginFistAction(
            player = player,
            state = AnimationState.FIST_LEFT_CROSS,
            duration = 0.27f,
            primaryLimb = MartialLimb.LEFT_FIST,
            secondaryLimb = null,
            isGoldenChi = false,
            isHeavy = false
        )
        // Left Cross drives forward with strong waist & shoulder rotation
        applyForwardStepMomentum(player, targetEnemy, stepSpeed = 6.8f)
        audioManager.playFistComboSwing(step = 2, isKick = false)

        val fwd = facingVector(player.facingYaw)
        spawnAirPressureRing(
            pos = Vector3(player.position.x + fwd.x * 1.02f, player.position.y + 1.22f, player.position.z + fwd.z * 1.02f),
            vel = Vector3(fwd.x * 6.4f, 0f, fwd.z * 6.4f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            initialScale = 0.65f,
            expansionRate = 3.8f,
            isGoldenChi = false
        )
        spawnFistWindArc(
            pos = Vector3(player.position.x + fwd.x * 0.95f, player.position.y + 1.20f, player.position.z + fwd.z * 0.95f),
            vel = Vector3(fwd.x * 4.2f, 0f, fwd.z * 4.2f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            rollTiltDeg = -16f,
            initialScale = 0.85f,
            expansionRate = 3.5f,
            isGoldenChi = false
        )

        scheduledHits.add(
            ScheduledFistHitWindow(
                triggerProgress = 0.38f,
                damage = 14f,
                range = 2.45f,
                knockback = 3.6f,
                is360AoE = false,
                isKick = false,
                isHeavy = false,
                isFinisher = false,
                isGoldenChi = false,
                arcRollTiltDeg = -16f,
                enemyYawTwistDeg = 24f,
                enemyPitchTiltDeg = 8f,
                enemyVerticalJolt = 0.04f,
                shakeIntensity = 0.06f,
                hitStopDuration = 0.035f
            )
        )
    }

    private fun startSideKick(
        player: PlayerCharacter,
        targetEnemy: EnemyController?
    ) {
        beginFistAction(
            player = player,
            state = AnimationState.FIST_SIDE_KICK,
            duration = 0.31f,
            primaryLimb = MartialLimb.RIGHT_FOOT,
            secondaryLimb = null,
            isGoldenChi = false,
            isHeavy = false
        )
        applyForwardStepMomentum(player, targetEnemy, stepSpeed = 6.2f)
        audioManager.playFistComboSwing(step = 3, isKick = true)

        val fwd = facingVector(player.facingYaw)
        spawnGroundDust(player.position, initialScale = 0.68f, expansionRate = 3.6f)
        spawnAirPressureRing(
            pos = Vector3(player.position.x + fwd.x * 1.12f, player.position.y + 1.05f, player.position.z + fwd.z * 1.12f),
            vel = Vector3(fwd.x * 7.5f, 0f, fwd.z * 7.5f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            initialScale = 0.78f,
            expansionRate = 4.6f,
            isGoldenChi = false
        )

        scheduledHits.add(
            ScheduledFistHitWindow(
                triggerProgress = 0.40f,
                damage = 18f,
                range = 2.65f,
                knockback = 6.2f, // Stronger kick displacement than punches
                is360AoE = false,
                isKick = true,
                isHeavy = false,
                isFinisher = false,
                isGoldenChi = false,
                arcRollTiltDeg = 8f,
                enemyYawTwistDeg = -22f,
                enemyPitchTiltDeg = 14f,
                enemyVerticalJolt = 0.07f,
                shakeIntensity = 0.09f,
                hitStopDuration = 0.044f
            )
        )
    }

    private fun startSpinningRoundhouseKick(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        cameraController: FollowCameraController
    ) {
        beginFistAction(
            player = player,
            state = AnimationState.FIST_SPIN_ROUNDHOUSE,
            duration = 0.35f,
            primaryLimb = MartialLimb.LEFT_FOOT,
            secondaryLimb = null,
            isGoldenChi = false,
            isHeavy = false
        )
        // Subtle lateral orbital step around locked enemy during roundhouse spin
        if (targetEnemy != null && cameraController.isLockedOn) {
            orbitalStepDir = if (liveMoveInputX < -0.15f) -1f else if (liveMoveInputX > 0.15f) 1f else 1f
            orbitalStepSpeed = 6.8f
        } else {
            applyForwardStepMomentum(player, targetEnemy, stepSpeed = 6.6f)
        }
        audioManager.playFistComboSwing(step = 4, isKick = true)

        val fwd = facingVector(player.facingYaw)
        spawnGroundDust(player.position, initialScale = 0.80f, expansionRate = 4.4f)
        spawnFistWindArc(
            pos = Vector3(player.position.x + fwd.x * 0.85f, player.position.y + 1.12f, player.position.z + fwd.z * 0.85f),
            vel = Vector3(fwd.x * 3.8f, 0f, fwd.z * 3.8f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            rollTiltDeg = 12f,
            initialScale = 1.15f,
            expansionRate = 5.0f,
            isGoldenChi = false
        )

        scheduledHits.add(
            ScheduledFistHitWindow(
                triggerProgress = 0.44f,
                damage = 21f,
                range = 2.80f,
                knockback = 6.8f,
                is360AoE = true,
                isKick = true,
                isHeavy = false,
                isFinisher = false,
                isGoldenChi = false,
                arcRollTiltDeg = 12f,
                enemyYawTwistDeg = 32f,
                enemyPitchTiltDeg = 12f,
                enemyVerticalJolt = 0.10f,
                shakeIntensity = 0.11f,
                hitStopDuration = 0.048f
            )
        )
    }

    private fun startRisingHighKick(
        player: PlayerCharacter,
        targetEnemy: EnemyController?
    ) {
        beginFistAction(
            player = player,
            state = AnimationState.FIST_RISING_HIGH_KICK,
            duration = 0.34f,
            primaryLimb = MartialLimb.RIGHT_FOOT,
            secondaryLimb = null,
            isGoldenChi = false,
            isHeavy = false
        )
        applyForwardStepMomentum(player, targetEnemy, stepSpeed = 6.5f)
        // Slight vertical hop during rising axe/flash high kick
        if (player.isGrounded) {
            player.velocity.y = 3.4f
        }
        audioManager.playFistComboSwing(step = 5, isKick = true)

        val fwd = facingVector(player.facingYaw)
        spawnGroundDust(player.position, initialScale = 0.75f, expansionRate = 4.2f)
        spawnFistWindArc(
            pos = Vector3(player.position.x + fwd.x * 1.00f, player.position.y + 1.10f, player.position.z + fwd.z * 1.00f),
            vel = Vector3(fwd.x * 4.0f, 2.5f, fwd.z * 4.0f),
            yawDeg = player.facingYaw,
            pitchDeg = -22f,
            rollTiltDeg = 82f,
            initialScale = 1.12f,
            expansionRate = 4.6f,
            isGoldenChi = false
        )

        scheduledHits.add(
            ScheduledFistHitWindow(
                triggerProgress = 0.40f,
                damage = 24f,
                range = 2.75f,
                knockback = 6.4f,
                is360AoE = false,
                isKick = true,
                isHeavy = false,
                isFinisher = false,
                isGoldenChi = false,
                arcRollTiltDeg = 82f,
                enemyYawTwistDeg = -14f,
                enemyPitchTiltDeg = -28f, // Tilts enemy upward/back
                enemyVerticalJolt = 0.28f, // Vertical launch jolt
                shakeIntensity = 0.12f,
                hitStopDuration = 0.052f
            )
        )
    }

    // =========================================================================
    // Step 6 Spectacular Contextual Finishers
    // =========================================================================

    private fun startCycloneHurricaneKickFinisher(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        cameraController: FollowCameraController
    ) {
        beginFistAction(
            player = player,
            state = AnimationState.FIST_FINISHER_CYCLONE_KICK,
            duration = 0.46f,
            primaryLimb = MartialLimb.RIGHT_FOOT,
            secondaryLimb = MartialLimb.LEFT_FOOT,
            isGoldenChi = true,
            isHeavy = true
        )
        if (targetEnemy != null && cameraController.isLockedOn) {
            orbitalStepDir = if (orbitalStepDir == 0f) 1f else -orbitalStepDir
            orbitalStepSpeed = 7.8f
        } else {
            applyForwardStepMomentum(player, targetEnemy, stepSpeed = 7.8f)
        }
        if (player.isGrounded) {
            player.velocity.y = 4.2f
        }
        audioManager.playFistComboSwing(step = 6, isKick = true)

        val fwd = facingVector(player.facingYaw)
        spawnMartialAfterimage(player.position, player.facingYaw, 20f)
        spawnGroundDust(player.position, initialScale = 1.05f, expansionRate = 5.6f)
        spawnWindStreakBurst(player.position, fwd.x, fwd.z, count = 5)
        spawnFistWindArc(
            pos = Vector3(player.position.x, player.position.y + 1.15f, player.position.z),
            vel = Vector3(fwd.x * 2.5f, 0.5f, fwd.z * 2.5f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            rollTiltDeg = 8f,
            initialScale = 1.32f,
            expansionRate = 6.2f,
            isGoldenChi = true
        )

        // Two-stage 540-degree hurricane kick hits
        scheduledHits.add(
            ScheduledFistHitWindow(
                triggerProgress = 0.30f,
                damage = 14f,
                range = 2.95f,
                knockback = 4.2f,
                is360AoE = true,
                isKick = true,
                isHeavy = false,
                isFinisher = true,
                isGoldenChi = false,
                arcRollTiltDeg = 14f,
                enemyYawTwistDeg = 28f,
                enemyPitchTiltDeg = 12f,
                enemyVerticalJolt = 0.14f,
                shakeIntensity = 0.10f,
                hitStopDuration = 0.036f
            )
        )
        scheduledHits.add(
            ScheduledFistHitWindow(
                triggerProgress = 0.66f,
                damage = 24f,
                range = 3.15f,
                knockback = 11.2f,
                is360AoE = true,
                isKick = true,
                isHeavy = true,
                isFinisher = true,
                isGoldenChi = true,
                arcRollTiltDeg = -14f,
                enemyYawTwistDeg = -36f,
                enemyPitchTiltDeg = 18f,
                enemyVerticalJolt = 0.24f,
                shakeIntensity = 0.18f,
                hitStopDuration = 0.068f
            )
        )
    }

    private fun startFlyingDragonStrikeFinisher(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        isFromAir: Boolean
    ) {
        beginFistAction(
            player = player,
            state = AnimationState.FIST_FINISHER_FLYING_STRIKE,
            duration = 0.44f,
            primaryLimb = MartialLimb.RIGHT_FOOT,
            secondaryLimb = MartialLimb.RIGHT_FIST,
            isGoldenChi = true,
            isHeavy = true
        )
        applyForwardStepMomentum(player, targetEnemy, stepSpeed = 9.6f)
        if (!isFromAir && player.isGrounded) {
            player.velocity.y = 4.5f
        } else if (isFromAir && player.velocity.y < 3.0f) {
            player.velocity.y = 3.5f
        }
        audioManager.playFistComboSwing(step = 6, isKick = true)

        val fwd = facingVector(player.facingYaw)
        spawnMartialAfterimage(player.position, player.facingYaw, 26f)
        if (!isFromAir) {
            spawnGroundDust(player.position, initialScale = 0.92f, expansionRate = 5.0f)
        }
        spawnWindStreakBurst(player.position, fwd.x, fwd.z, count = 5)
        spawnAirPressureRing(
            pos = Vector3(player.position.x + fwd.x * 1.15f, player.position.y + 1.18f, player.position.z + fwd.z * 1.15f),
            vel = Vector3(fwd.x * 9.5f, 0f, fwd.z * 9.5f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            initialScale = 0.85f,
            expansionRate = 5.8f,
            isGoldenChi = true
        )

        scheduledHits.add(
            ScheduledFistHitWindow(
                triggerProgress = 0.46f,
                damage = 32f,
                range = 2.95f,
                knockback = 11.8f,
                is360AoE = false,
                isKick = true,
                isHeavy = true,
                isFinisher = true,
                isGoldenChi = true,
                arcRollTiltDeg = 0f,
                enemyYawTwistDeg = 22f,
                enemyPitchTiltDeg = 22f,
                enemyVerticalJolt = 0.22f,
                shakeIntensity = 0.19f,
                hitStopDuration = 0.068f
            )
        )
    }

    // =========================================================================
    // Contextual Heavy Martial Arts Special Starters
    // =========================================================================

    private fun startDragonHaymaker(
        player: PlayerCharacter,
        targetEnemy: EnemyController?
    ) {
        beginFistAction(
            player = player,
            state = AnimationState.FIST_HEAVY_DRAGON_HAYMAKER,
            duration = 0.48f,
            primaryLimb = MartialLimb.RIGHT_FIST,
            secondaryLimb = null,
            isGoldenChi = true,
            isHeavy = true
        )
        applyForwardStepMomentum(player, targetEnemy, stepSpeed = 9.2f)
        audioManager.playFistHeavySkill(0)

        val fwd = facingVector(player.facingYaw)
        spawnMartialAfterimage(player.position, player.facingYaw, 24f)
        spawnGroundDust(player.position, initialScale = 1.05f, expansionRate = 5.8f)
        spawnWindStreakBurst(player.position, fwd.x, fwd.z, count = 6)

        // Twin expanding air-pressure shockwave rings ahead of the Dragon Fist
        spawnAirPressureRing(
            pos = Vector3(player.position.x + fwd.x * 1.15f, player.position.y + 1.18f, player.position.z + fwd.z * 1.15f),
            vel = Vector3(fwd.x * 11.0f, 0f, fwd.z * 11.0f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            initialScale = 0.92f,
            expansionRate = 6.4f,
            isGoldenChi = true
        )
        spawnAirPressureRing(
            pos = Vector3(player.position.x + fwd.x * 0.85f, player.position.y + 1.18f, player.position.z + fwd.z * 0.85f),
            vel = Vector3(fwd.x * 7.5f, 0f, fwd.z * 7.5f),
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            initialScale = 0.72f,
            expansionRate = 4.8f,
            isGoldenChi = false
        )

        scheduledHits.add(
            ScheduledFistHitWindow(
                triggerProgress = 0.48f,
                damage = 36f,
                range = 3.15f,
                knockback = 13.5f,
                is360AoE = false,
                isKick = false,
                isHeavy = true,
                isFinisher = true,
                isGoldenChi = true,
                arcRollTiltDeg = 10f,
                enemyYawTwistDeg = -38f,
                enemyPitchTiltDeg = 22f,
                enemyVerticalJolt = 0.18f,
                shakeIntensity = 0.22f,
                hitStopDuration = 0.072f
            )
        )
    }

    private fun startThunderBreaker(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        cameraController: FollowCameraController
    ) {
        beginFistAction(
            player = player,
            state = AnimationState.FIST_HEAVY_THUNDER_BREAKER,
            duration = 0.50f,
            primaryLimb = MartialLimb.RIGHT_FOOT,
            secondaryLimb = MartialLimb.LEFT_FOOT,
            isGoldenChi = true,
            isHeavy = true
        )
        if (targetEnemy != null && cameraController.isLockedOn) {
            orbitalStepDir = if (liveMoveInputX < 0f) -1f else 1f
            orbitalStepSpeed = 8.4f
        } else {
            applyForwardStepMomentum(player, targetEnemy, stepSpeed = 8.4f)
        }
        audioManager.playFistHeavySkill(1)

        val fwd = facingVector(player.facingYaw)
        spawnMartialAfterimage(player.position, player.facingYaw, 22f)
        spawnGroundDust(player.position, initialScale = 1.20f, expansionRate = 6.5f)
        spawnWindStreakBurst(player.position, fwd.x, fwd.z, count = 6)

        radialChiBursts.add(
            ActiveRadialChiBurst(
                instance = ModelInstance(radialChiBurstModel),
                position = Vector3(player.position.x, player.position.y + 0.12f, player.position.z)
            )
        )
        spawnFistWindArc(
            pos = Vector3(player.position.x, player.position.y + 1.10f, player.position.z),
            vel = Vector3.Zero,
            yawDeg = player.facingYaw,
            pitchDeg = 0f,
            rollTiltDeg = -10f,
            initialScale = 1.40f,
            expansionRate = 6.6f,
            isGoldenChi = true
        )

        scheduledHits.add(
            ScheduledFistHitWindow(
                triggerProgress = 0.48f,
                damage = 38f,
                range = 3.55f,
                knockback = 13.8f,
                is360AoE = true,
                isKick = true,
                isHeavy = true,
                isFinisher = true,
                isGoldenChi = true,
                arcRollTiltDeg = -10f,
                enemyYawTwistDeg = 42f,
                enemyPitchTiltDeg = 20f,
                enemyVerticalJolt = 0.26f,
                shakeIntensity = 0.23f,
                hitStopDuration = 0.074f
            )
        )
    }

    private fun startMeteorStrike(
        player: PlayerCharacter,
        targetEnemy: EnemyController?,
        isFromGroundLeap: Boolean
    ) {
        beginFistAction(
            player = player,
            state = AnimationState.FIST_HEAVY_METEOR_STRIKE,
            duration = 0.50f,
            primaryLimb = MartialLimb.RIGHT_FOOT,
            secondaryLimb = null,
            isGoldenChi = true,
            isHeavy = true
        )
        audioManager.playFistHeavySkill(2)

        val fwd = facingVector(player.facingYaw)
        spawnMartialAfterimage(player.position, player.facingYaw, 22f)
        spawnWindStreakBurst(player.position, fwd.x, fwd.z, count = 5)

        if (isFromGroundLeap) {
            spawnGroundDust(player.position, initialScale = 0.95f, expansionRate = 5.2f)
            player.isGrounded = false
            player.velocity.y = 7.2f
            applyForwardStepMomentum(player, targetEnemy, stepSpeed = 10.2f)
        } else {
            player.velocity.y = -9.5f
            applyForwardStepMomentum(player, targetEnemy, stepSpeed = 9.0f)
        }

        spawnAirPressureRing(
            pos = Vector3(player.position.x + fwd.x * 1.05f, player.position.y + 1.20f, player.position.z + fwd.z * 1.05f),
            vel = Vector3(fwd.x * 6.5f, -5.5f, fwd.z * 6.5f),
            yawDeg = player.facingYaw,
            pitchDeg = 32f,
            initialScale = 0.88f,
            expansionRate = 5.6f,
            isGoldenChi = true
        )

        scheduledHits.add(
            ScheduledFistHitWindow(
                triggerProgress = 0.56f,
                damage = 42f,
                range = 3.50f,
                knockback = 13.5f,
                is360AoE = true,
                isKick = true,
                isHeavy = true,
                isFinisher = true,
                isGoldenChi = true,
                arcRollTiltDeg = 85f,
                enemyYawTwistDeg = 18f,
                enemyPitchTiltDeg = 32f, // Heavy downward slam reaction
                enemyVerticalJolt = 0.30f,
                shakeIntensity = 0.24f,
                hitStopDuration = 0.076f
            )
        )
    }

    private fun beginFistAction(
        player: PlayerCharacter,
        state: AnimationState,
        duration: Float,
        primaryLimb: MartialLimb,
        secondaryLimb: MartialLimb?,
        isGoldenChi: Boolean,
        isHeavy: Boolean
    ) {
        isFistAttacking = true
        activeFistState = state
        actionTimer = 0f
        actionDuration = duration
        activeTrackedLimb = primaryLimb
        secondaryTrackedLimb = secondaryLimb
        currentActionIsGoldenChi = isGoldenChi
        currentActionIsHeavy = isHeavy
        orbitalStepDir = 0f
        orbitalStepSpeed = 0f
        scheduledHits.clear()
        hasPrevLimbSample = false
        player.animationState = state
        player.activeAttackProgress = 0f
    }

    // =========================================================================
    // Per-Frame Update & Post-Animation Real-Time Limb Trail Sampling
    // =========================================================================

    fun update(
        delta: Float,
        player: PlayerCharacter,
        cameraController: FollowCameraController
    ) {
        if (heavyCooldown > 0f) heavyCooldown = (heavyCooldown - delta).coerceAtLeast(0f)
        if (hitStopTimer > 0f) hitStopTimer = (hitStopTimer - delta).coerceAtLeast(0f)

        if (comboResetTimer > 0f) {
            comboResetTimer = (comboResetTimer - delta).coerceAtLeast(0f)
            if (comboResetTimer == 0f && !isFistAttacking) {
                currentComboStep = 0
            }
        }

        val lockedTarget = if (cameraController.isLockedOn) cameraController.lockedTarget else null

        if (isFistAttacking) {
            val effectiveDelta = if (hitStopTimer > 0f) delta * 0.18f else delta
            actionTimer += effectiveDelta
            val progress = (actionTimer / actionDuration).coerceIn(0f, 1f)
            player.animationState = activeFistState
            player.activeAttackProgress = progress

            // Switch tracked limb mid-animation for dual-limb finishers (Cyclone Kick & Flying Strike)
            val secLimb = secondaryTrackedLimb
            if (secLimb != null && progress >= 0.48f && activeTrackedLimb != secLimb) {
                activeTrackedLimb = secLimb
                hasPrevLimbSample = false
            }

            // Maintain target facing and orbital footwork when Lock-On is active
            if (lockedTarget != null && lockedTarget.aiState != EnemyAiState.DEAD) {
                orientPlayerTowardTarget(player, lockedTarget)

                if (orbitalStepSpeed > 0.1f && progress < 0.76f) {
                    val dx = lockedTarget.entity.position.x - player.position.x
                    val dz = lockedTarget.entity.position.z - player.position.z
                    val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
                    val toTargetX = dx / dist
                    val toTargetZ = dz / dist
                    val tangentX = -toTargetZ * orbitalStepDir
                    val tangentZ = toTargetX * orbitalStepDir
                    val radialCorrection = ((dist - 1.85f) * 2.5f).coerceIn(-3.2f, 4.2f)
                    player.velocity.x = tangentX * orbitalStepSpeed + toTargetX * radialCorrection
                    player.velocity.z = tangentZ * orbitalStepSpeed + toTargetZ * radialCorrection
                }
            }

            // Downward dive acceleration during the plunge phase of Meteor Strike
            if (activeFistState == AnimationState.FIST_HEAVY_METEOR_STRIKE && progress in 0.42f..0.88f && !player.isGrounded) {
                if (player.velocity.y > -12.5f) {
                    player.velocity.y = -12.5f
                }
            }

            // Evaluate scheduled martial arts hit windows
            for (hitWin in scheduledHits) {
                if (!hitWin.hasTriggered && progress >= hitWin.triggerProgress) {
                    hitWin.hasTriggered = true

                    // Spawn landing shockwave & ground dust when Meteor Strike or Cyclone Kick lands its final hit
                    if (activeFistState == AnimationState.FIST_HEAVY_METEOR_STRIKE) {
                        spawnGroundDust(player.position, initialScale = 1.35f, expansionRate = 7.2f)
                        radialChiBursts.add(
                            ActiveRadialChiBurst(
                                instance = ModelInstance(radialChiBurstModel),
                                position = Vector3(player.position.x, player.position.y + 0.10f, player.position.z)
                            )
                        )
                    } else if (activeFistState == AnimationState.FIST_FINISHER_CYCLONE_KICK && hitWin.triggerProgress > 0.5f) {
                        val fwd = facingVector(player.facingYaw)
                        spawnFistWindArc(
                            pos = Vector3(player.position.x + fwd.x * 0.90f, player.position.y + 1.15f, player.position.z + fwd.z * 0.90f),
                            vel = Vector3(fwd.x * 5.0f, 0f, fwd.z * 5.0f),
                            yawDeg = player.facingYaw,
                            pitchDeg = 0f,
                            rollTiltDeg = hitWin.arcRollTiltDeg,
                            initialScale = 1.25f,
                            expansionRate = 5.8f,
                            isGoldenChi = true
                        )
                    }

                    evaluateFistHitWindow(player, cameraController, hitWin)
                }
            }

            if (actionTimer >= actionDuration) {
                isFistAttacking = false
                orbitalStepDir = 0f
                orbitalStepSpeed = 0f
                hasPrevLimbSample = false
                player.activeAttackProgress = 0f
            }
        } else {
            hasPrevLimbSample = false
        }

        // Update active 3D Martial Arts VFX
        updateVfx(delta)
    }

    /**
     * Called immediately after `player.animationController.update(...)` so the forearm/shin nodes'
     * `globalTransform` reflects the exact current-frame martial arts pose.
     * Emits real-time 3D wind/chi ribbon trail segments along the actual fist or foot trajectory.
     */
    fun postAnimationUpdate(delta: Float, player: PlayerCharacter) {
        if (player.isSwordEquipped || !isFistAttacking) {
            hasPrevLimbSample = false
            return
        }

        val progress = (actionTimer / actionDuration).coerceIn(0f, 1f)
        if (progress !in 0.08f..0.88f) {
            hasPrevLimbSample = false
            return
        }

        player.animationController.getMartialLimbWorldSegment(activeTrackedLimb, currLimbBase, currLimbTip)

        if (hasPrevLimbSample) {
            val tipDx = currLimbTip.x - prevLimbTip.x
            val tipDy = currLimbTip.y - prevLimbTip.y
            val tipDz = currLimbTip.z - prevLimbTip.z
            val tipMoveDist = sqrt(tipDx * tipDx + tipDy * tipDy + tipDz * tipDz)

            if (tipMoveDist >= 0.028f) {
                val midBaseX = (prevLimbBase.x + currLimbBase.x) * 0.5f
                val midBaseY = (prevLimbBase.y + currLimbBase.y) * 0.5f
                val midBaseZ = (prevLimbBase.z + currLimbBase.z) * 0.5f

                val midTipX = (prevLimbTip.x + currLimbTip.x) * 0.5f
                val midTipY = (prevLimbTip.y + currLimbTip.y) * 0.5f
                val midTipZ = (prevLimbTip.z + currLimbTip.z) * 0.5f

                val center = Vector3(
                    (midBaseX + midTipX) * 0.5f,
                    (midBaseY + midTipY) * 0.5f,
                    (midBaseZ + midTipZ) * 0.5f
                )
                val limbVec = Vector3(midTipX - midBaseX, midTipY - midBaseY, midTipZ - midBaseZ)
                val limbLen = limbVec.len().coerceIn(0.42f, 0.98f)
                limbVec.nor()

                val swingVec = Vector3(tipDx / tipMoveDist, tipDy / tipMoveDist, tipDz / tipMoveDist)
                val model = if (currentActionIsGoldenChi) limbTrailChiModel else limbTrailWindModel
                val seg = ActiveFistLimbTrailSegment(
                    instance = ModelInstance(model),
                    center = center,
                    limbDir = limbVec,
                    swingDir = swingVec,
                    limbLen = limbLen,
                    chordWidth = (tipMoveDist * 1.40f).coerceIn(0.16f, 0.58f),
                    maxLifetime = if (currentActionIsGoldenChi) 0.19f else 0.14f
                )
                updateLimbTrailSegmentTransform(seg, 1f)
                limbTrailSegments.add(seg)
            }
        }

        prevLimbBase.set(currLimbBase)
        prevLimbTip.set(currLimbTip)
        hasPrevLimbSample = true
    }

    private fun evaluateFistHitWindow(
        player: PlayerCharacter,
        cameraController: FollowCameraController,
        hitWin: ScheduledFistHitWindow
    ) {
        val fwd = facingVector(player.facingYaw)
        var connectedAny = false

        for (ec in entityManager.enemyControllers) {
            if (ec.aiState == EnemyAiState.DEAD) continue
            val dx = ec.entity.position.x - player.position.x
            val dz = ec.entity.position.z - player.position.z
            val dy = abs(ec.entity.position.y - player.position.y)
            val dist = sqrt(dx * dx + dz * dz)

            if (dist <= hitWin.range + ec.collisionRadius && dy <= 2.35f) {
                val dot = if (dist > 0.01f) (dx / dist) * fwd.x + (dz / dist) * fwd.z else 1f
                val minDot = if (hitWin.is360AoE) -1.0f else -0.08f
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
                        isHeavyImpact = hitWin.isHeavy || hitWin.isFinisher,
                        audioManager = audioManager,
                        slashYawTwistDeg = hitWin.enemyYawTwistDeg,
                        slashPitchTiltDeg = hitWin.enemyPitchTiltDeg,
                        verticalJolt = hitWin.enemyVerticalJolt
                    )

                    val impactPos = Vector3(
                        ec.entity.position.x - dirX * 0.26f,
                        ec.entity.position.y + 1.15f,
                        ec.entity.position.z - dirZ * 0.26f
                    )

                    // Controlled 3D Hit Flash Star & sparks ONLY when an attack connects
                    spawnHitFlashStar(impactPos, isGoldenChi = hitWin.isGoldenChi || hitWin.isHeavy || defeated)
                    if (hitWin.isHeavy || hitWin.isFinisher) {
                        spawnAirPressureRing(
                            pos = impactPos,
                            vel = Vector3(dirX * 3.5f, 0f, dirZ * 3.5f),
                            yawDeg = player.facingYaw,
                            pitchDeg = 0f,
                            initialScale = 0.75f,
                            expansionRate = 5.4f,
                            isGoldenChi = hitWin.isGoldenChi
                        )
                    }
                    onSpawnHitSparks(
                        impactPos,
                        if (defeated) 20 else if (hitWin.isHeavy || hitWin.isFinisher) 14 else 8,
                        if (hitWin.isHeavy) 6.4f else 4.6f,
                        if (defeated) 2 else if (hitWin.isGoldenChi || hitWin.isHeavy) 1 else 0
                    )

                    onRecordDamagePopup(
                        Vector3(ec.entity.position.x, ec.entity.position.y + 2.15f, ec.entity.position.z),
                        hitWin.damage,
                        hitWin.isHeavy || hitWin.isFinisher
                    )
                }
            }
        }

        // Trigger distinct punch/kick/heavy impact sound, brief hit-stop, and camera shake ONLY when a hit connects
        if (connectedAny) {
            audioManager.playFistHitImpact(
                isKick = hitWin.isKick,
                isHeavy = hitWin.isHeavy,
                isFinisher = hitWin.isFinisher
            )
            hitStopTimer = maxOf(hitStopTimer, hitWin.hitStopDuration)
            cameraController.triggerCameraShake(
                intensity = hitWin.shakeIntensity,
                duration = if (hitWin.isHeavy || hitWin.isFinisher) 0.20f else 0.10f
            )
        }
    }

    private fun updateVfx(delta: Float) {
        // 1. Real-time 3D Limb Wind/Chi Trail Segments
        val trailIter = limbTrailSegments.iterator()
        while (trailIter.hasNext()) {
            val seg = trailIter.next()
            seg.age += delta
            if (seg.age >= seg.maxLifetime) {
                trailIter.remove()
                continue
            }
            val lifeRatio = (1f - seg.age / seg.maxLifetime).coerceIn(0f, 1f)
            updateLimbTrailSegmentTransform(seg, lifeRatio)
        }

        // 2. Curved Wind / Chi Arcs
        val arcIter = windArcs.iterator()
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

        // 3. Expanding Directional Air-Pressure Rings
        val ringIter = airPressureRings.iterator()
        while (ringIter.hasNext()) {
            val ring = ringIter.next()
            ring.age += delta
            if (ring.age >= ring.maxLifetime) {
                ringIter.remove()
                continue
            }
            ring.position.mulAdd(ring.velocity, delta)
            val lifeRatio = (1f - ring.age / ring.maxLifetime).coerceIn(0.1f, 1f)
            val s = ring.initialScale + ring.age * ring.expansionRate
            ring.instance.transform.idt()
                .translate(ring.position.x, ring.position.y, ring.position.z)
                .rotate(0f, 1f, 0f, ring.yawDeg)
                .rotate(1f, 0f, 0f, ring.pitchDeg)
                .scale(s, s, lifeRatio.coerceAtLeast(0.2f))
        }

        // 4. Radial Chi Energy Bursts
        val chiIter = radialChiBursts.iterator()
        while (chiIter.hasNext()) {
            val cb = chiIter.next()
            cb.age += delta
            if (cb.age >= cb.maxLifetime) {
                chiIter.remove()
                continue
            }
            cb.spinYaw = (cb.spinYaw + delta * 720f) % 360f
            cb.currentScale += delta * cb.expansionRate
            val heightRatio = (1f - (cb.age / cb.maxLifetime) * 0.6f).coerceIn(0.25f, 1.15f)
            cb.instance.transform.idt()
                .translate(cb.position.x, cb.position.y, cb.position.z)
                .rotate(0f, 1f, 0f, cb.spinYaw)
                .scale(cb.currentScale, heightRatio, cb.currentScale)
        }

        // 5. Unarmed Martial Artist Afterimages
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
        for (seg in limbTrailSegments) {
            modelBatch.render(seg.instance, environment)
        }
        for (arc in windArcs) {
            modelBatch.render(arc.instance, environment)
        }
        for (ring in airPressureRings) {
            modelBatch.render(ring.instance, environment)
        }
        for (cb in radialChiBursts) {
            modelBatch.render(cb.instance, environment)
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
        isFistAttacking = false
        currentComboStep = 0
        comboResetTimer = 0f
        heavyCooldown = 0f
        hitStopTimer = 0f
        orbitalStepDir = 0f
        orbitalStepSpeed = 0f
        hasPrevLimbSample = false
        scheduledHits.clear()
        limbTrailSegments.clear()
        windArcs.clear()
        airPressureRings.clear()
        radialChiBursts.clear()
        afterimages.clear()
        windStreaks.clear()
        groundDusts.clear()
        hitFlashStars.clear()
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun updateLimbTrailSegmentTransform(seg: ActiveFistLimbTrailSegment, lifeRatio: Float) {
        val yaw = Math.toDegrees(atan2(seg.limbDir.x.toDouble(), seg.limbDir.z.toDouble())).toFloat()
        val pitch = Math.toDegrees(-asin(seg.limbDir.y.coerceIn(-1f, 1f).toDouble())).toFloat()
        val roll = Math.toDegrees(
            atan2(
                seg.swingDir.y.toDouble(),
                sqrt((seg.swingDir.x * seg.swingDir.x + seg.swingDir.z * seg.swingDir.z).toDouble()).coerceAtLeast(0.01)
            )
        ).toFloat()

        tmpQuat.idt()
        tmpQuat.setEulerAngles(yaw, pitch, roll)
        seg.instance.transform.idt()
            .translate(seg.center.x, seg.center.y, seg.center.z)
            .rotate(tmpQuat)
            .scale(
                (seg.chordWidth * (0.45f + 0.55f * lifeRatio)).coerceAtLeast(0.06f),
                lifeRatio.coerceAtLeast(0.2f),
                seg.limbLen
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
            if (dx * dx + dz * dz <= 8.5f * 8.5f) {
                return primary
            }
        }
        return null
    }

    private fun orientPlayerForMartialStrike(
        player: PlayerCharacter,
        target: EnemyController?,
        cameraController: FollowCameraController
    ) {
        // 1. If Locked On or a target is in close range, orient toward the target
        if (target != null && target.aiState != EnemyAiState.DEAD) {
            orientPlayerTowardTarget(player, target)
            return
        }

        // 2. If unlocked and moving with the joystick, smoothly align strike direction with movement input
        val inputMag = sqrt(liveMoveInputX * liveMoveInputX + liveMoveInputY * liveMoveInputY)
        if (inputMag > 0.20f) {
            val camYawRad = Math.toRadians(cameraController.yawDegrees.toDouble()).toFloat()
            val forwardX = -sin(camYawRad)
            val forwardZ = -cos(camYawRad)
            val rightX = cos(camYawRad)
            val rightZ = -sin(camYawRad)
            val dirX = rightX * liveMoveInputX + forwardX * liveMoveInputY
            val dirZ = rightZ * liveMoveInputX + forwardZ * liveMoveInputY
            if (dirX * dirX + dirZ * dirZ > 0.01f) {
                player.facingYaw = Math.toDegrees(atan2(dirX.toDouble(), dirZ.toDouble())).toFloat()
            }
        }
    }

    private fun orientPlayerTowardTarget(player: PlayerCharacter, target: EnemyController?) {
        if (target == null || target.aiState == EnemyAiState.DEAD) return
        val dx = target.entity.position.x - player.position.x
        val dz = target.entity.position.z - player.position.z
        if (dx * dx + dz * dz > 0.005f) {
            player.facingYaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
        }
    }

    private fun applyForwardStepMomentum(
        player: PlayerCharacter,
        target: EnemyController?,
        stepSpeed: Float
    ) {
        val fwd = facingVector(player.facingYaw)
        var effectiveSpeed = stepSpeed
        if (target != null && target.aiState != EnemyAiState.DEAD) {
            val dx = target.entity.position.x - player.position.x
            val dz = target.entity.position.z - player.position.z
            val dist = sqrt(dx * dx + dz * dz)
            val minContactDist = player.collisionRadius + target.collisionRadius + 0.52f
            if (dist <= minContactDist) {
                effectiveSpeed = 0f
            } else if (dist <= minContactDist + 1.25f) {
                effectiveSpeed = stepSpeed * ((dist - minContactDist) / 1.25f).coerceIn(0f, 1f)
            }
        }
        player.velocity.x = fwd.x * effectiveSpeed
        player.velocity.z = fwd.z * effectiveSpeed
    }

    private fun facingVector(yawDeg: Float): Vector3 {
        val rad = Math.toRadians(yawDeg.toDouble()).toFloat()
        return Vector3(sin(rad), 0f, cos(rad))
    }

    private fun spawnFistWindArc(
        pos: Vector3,
        vel: Vector3,
        yawDeg: Float,
        pitchDeg: Float,
        rollTiltDeg: Float,
        initialScale: Float,
        expansionRate: Float,
        isGoldenChi: Boolean
    ) {
        val model = if (isGoldenChi) chiArcModel else windArcModel
        windArcs.add(
            ActiveFistWindArc(
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

    private fun spawnAirPressureRing(
        pos: Vector3,
        vel: Vector3,
        yawDeg: Float,
        pitchDeg: Float,
        initialScale: Float,
        expansionRate: Float,
        isGoldenChi: Boolean
    ) {
        val model = if (isGoldenChi) airPressureChiModel else airPressureWindModel
        airPressureRings.add(
            ActiveAirPressureRing(
                instance = ModelInstance(model),
                position = Vector3(pos),
                velocity = Vector3(vel),
                yawDeg = yawDeg,
                pitchDeg = pitchDeg,
                initialScale = initialScale,
                expansionRate = expansionRate
            )
        )
    }

    private fun spawnMartialAfterimage(pos: Vector3, yawDeg: Float, forwardLeanDeg: Float) {
        afterimages.add(
            ActiveAfterimage(
                instance = ModelInstance(martialAfterimageModel),
                position = Vector3(pos),
                yawDeg = yawDeg,
                forwardLeanDeg = forwardLeanDeg,
                maxLifetime = 0.24f
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
            val sideOffset = (rng.nextFloat() * 2f - 1f) * 0.78f
            val heightOffset = 0.45f + rng.nextFloat() * 1.10f
            val rightX = -dirZ
            val rightZ = dirX
            val startPos = Vector3(
                origin.x + rightX * sideOffset,
                origin.y + heightOffset,
                origin.z + rightZ * sideOffset
            )
            val spd = 10.5f + rng.nextFloat() * 5.5f
            windStreaks.add(
                ActiveWindStreak(
                    instance = ModelInstance(windStreakModel),
                    position = startPos,
                    velocity = Vector3(dirX * spd, (rng.nextFloat() - 0.5f) * 1.6f, dirZ * spd),
                    yawDeg = baseYaw,
                    pitchDeg = (rng.nextFloat() - 0.5f) * 10f,
                    initialLengthScale = 0.80f + rng.nextFloat() * 0.50f
                )
            )
        }
    }

    private fun spawnHitFlashStar(pos: Vector3, isGoldenChi: Boolean) {
        val model = if (isGoldenChi) hitFlashChiModel else hitFlashWindModel
        hitFlashStars.add(
            ActiveHitFlashStar(
                instance = ModelInstance(model),
                position = Vector3(pos),
                initialScale = if (isGoldenChi) 1.08f else 0.78f,
                spinDeg = rng.nextFloat() * 360f
            )
        )
    }
}
