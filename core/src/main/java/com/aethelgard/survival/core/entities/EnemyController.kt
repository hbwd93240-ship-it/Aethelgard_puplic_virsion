package com.aethelgard.survival.core.entities

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.combat.CombatSystem
import com.aethelgard.survival.core.player.PlayerCharacter
import com.aethelgard.survival.core.world.WorldManager
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class EnemyAiState(val displayLabel: String) {
    PATROL("PATROLLING"),
    TACTICAL_STANCE("TACTICAL STANCE"),
    PURSUE("STALKING"),

    // Legacy fallback states
    ATTACK_WINDUP("CLEAVER WINDUP"),
    ATTACK_STRIKE("CLEAVER STRIKE"),
    ATTACK_RECOVER("COMBAT RESET"),

    // Advanced Sword Combo Chains
    COMBO_SLASH_HORIZONTAL("CROSS SLASH"),
    COMBO_SLASH_DIAGONAL("DIAGONAL CLEAVE"),
    COMBO_RISING_SLASH("RISING EXECUTION"),
    COMBO_OVERHEAD_SLAM("OVERHEAD SMASH"),
    COMBO_FINISHER_THRUST("CORRUPTED PIERCE"),

    // Unique Special Abilities
    SPECIAL_WHIRLWIND("WHIRLWIND ASSAULT"),
    SPECIAL_PHANTOM_CHARGE("PHANTOM CHARGE"),
    SPECIAL_CRESCENT_SLASH("CRESCENT SLASH"),
    SPECIAL_AERIAL_EXECUTION("AERIAL EXECUTION"),
    SPECIAL_COUNTER_RIPOSTE("RIPOSTE COUNTER"),

    // Boss Fury Awakening & Reaction States
    FURY_ROAR("FURY AWAKENING"),
    HIT_STAGGER("STAGGERED"),
    DEAD("DEFEATED — RESPAWNING")
}

data class ActiveEnemyCrescentWave(
    val position: Vector3,
    val velocity: Vector3,
    val damage: Float,
    val yawDeg: Float,
    val origin: Vector3 = Vector3(position),
    val maxRange: Float = 3.85f,
    var age: Float = 0f,
    val maxLifetime: Float = 0.28f,
    var hasHitPlayer: Boolean = false
)

/**
 * Elite Adaptive Sword Fighter AI, physics, articulated limb animation,
 * dynamic combos, unique special abilities, adaptive player tracking,
 * and Fury Phase for the Corrupted Mana Guardian Knight.
 */
open class EnemyController(
    val entity: WorldEntity,
    val spawnOrigin: Vector3 = Vector3(0f, 0f, 8.5f),
    val maxHealth: Float = 280f
) {
    open var health: Float = maxHealth
        protected set

    open var aiState: EnemyAiState = EnemyAiState.PATROL
        protected set

    val velocity: Vector3 = Vector3()
    var facingYaw: Float = 0f
    open var collisionRadius: Float = 0.65f
        protected set

    // Boss & Combat State
    open var isFuryPhase: Boolean = false
        protected set
    var hasActivatedFury: Boolean = false
        protected set

    var currentComboStep: Int = 0
        protected set
    open var activeSkillName: String = ""
        protected set

    // Cooldown timers
    var phantomChargeCooldown: Float = 4.5f
        private set
    var crescentSlashCooldown: Float = 3.5f
        private set
    var aerialExecutionCooldown: Float = 7.0f
        private set
    var whirlwindCooldown: Float = 6.0f
        private set
    var counterRiposteCooldown: Float = 4.0f
        private set
    var comboAttackCooldown: Float = 1.6f
        private set

    // Internal state tracking
    open var stateTimer: Float = 0f
        protected set
    private var activeRecoverDuration: Float = 0.50f
    private var patrolAngle: Float = 0f
    private var animTime: Float = 0f
    private var tacticalTimer: Float = 0f
    private var strafeDirection: Float = 1.0f
    private var strafeTimer: Float = 0f
    private var furyAuraPulseTimer: Float = 0f
    var strikeHitDone1: Boolean = false
        protected set
    var strikeHitDone2: Boolean = false
        protected set
    private var closeComboCycleCount: Int = 0
    private var comboTargetSteps: Int = 3

    open var hitFlashTimer: Float = 0f
        protected set
    open var respawnCountdown: Float = 0f
        protected set

    // Directional hit reaction pose angles
    private var hitReactionYawTwist: Float = 0f
    private var hitReactionPitchTilt: Float = -24f
    private var hitReactionVerticalLift: Float = 0f

    // Articulated hierarchy nodes
    private val pelvisNode: Node? = entity.modelInstance.getNode("enemyPelvis", true)
    private val torsoNode: Node? = entity.modelInstance.getNode("enemyTorso", true)
    private val headNode: Node? = entity.modelInstance.getNode("enemyHead", true)
    private val leftArmNode: Node? = entity.modelInstance.getNode("enemyLeftArm", true)
    private val rightArmNode: Node? = entity.modelInstance.getNode("enemyRightArm", true)
    private val leftLegNode: Node? = entity.modelInstance.getNode("enemyLeftLeg", true)
    private val rightLegNode: Node? = entity.modelInstance.getNode("enemyRightLeg", true)

    // Range & movement parameters (Aggressive pursuit, responsive sword combo range, balanced spacing)
    open val detectionRadius: Float = 18.0f
    val meleeTriggerRange: Float = 3.40f
    val meleeStrikeMaxRange: Float = 2.85f
    private val tacticalSpacingMin: Float = 1.50f
    private val tacticalSpacingMax: Float = 3.10f
    val baseMoveSpeed: Float = 4.6f
    private val basePatrolSpeed: Float = 2.0f
    val furyMoveSpeed: Float = 5.6f

    // Adaptive Player Behavior Tracking
    private var playerRecentAttacks: Int = 0
    private var playerAttackWindowTimer: Float = 0f
    private var playerFarTimeAccum: Float = 0f
    private var playerAirborneAccum: Float = 0f

    // Active 3D projectile waves launched by Knight
    val activeCrescentWaves = mutableListOf<ActiveEnemyCrescentWave>()

    open fun resetToSpawn(worldManager: WorldManager) {
        health = maxHealth
        aiState = EnemyAiState.PATROL
        stateTimer = 0f
        patrolAngle = 0f
        hitFlashTimer = 0f
        respawnCountdown = 0f
        isFuryPhase = false
        hasActivatedFury = false
        currentComboStep = 0
        activeSkillName = ""
        hitReactionYawTwist = 0f
        hitReactionPitchTilt = -24f
        phantomChargeCooldown = 4.5f
        crescentSlashCooldown = 3.5f
        aerialExecutionCooldown = 7.0f
        whirlwindCooldown = 6.0f
        counterRiposteCooldown = 4.0f
        comboAttackCooldown = 1.6f
        closeComboCycleCount = 0
        comboTargetSteps = 3
        activeRecoverDuration = 0.50f
        tacticalTimer = 0f
        strafeTimer = 0f
        playerRecentAttacks = 0
        playerAttackWindowTimer = 0f
        playerFarTimeAccum = 0f
        playerAirborneAccum = 0f
        activeCrescentWaves.clear()
        velocity.setZero()
        val y = worldManager.getWalkableSurfaceHeight(spawnOrigin.x, spawnOrigin.z)
        entity.position.set(spawnOrigin.x, y, spawnOrigin.z)
        facingYaw = 0f // Facing +Z toward player spawn
        entity.isActive = true
        updateArticulatedPose(0.016f, 0f)
        syncTransform()
    }

    /**
     * Applies damage, evaluates poise/stagger resist, directional hit-stagger reaction,
     * and triggers Fury Awakening at <= 30% HP.
     */
    open fun receiveDamage(
        amount: Float,
        knockbackX: Float,
        knockbackZ: Float,
        isHeavyImpact: Boolean,
        audioManager: AudioManager,
        slashYawTwistDeg: Float = 22f,
        slashPitchTiltDeg: Float = -26f,
        verticalJolt: Float = 0f
    ): Boolean {
        if (aiState == EnemyAiState.DEAD) return false

        // During Fury Roar awakening, Knight possesses absolute invulnerability & poise
        if (aiState == EnemyAiState.FURY_ROAR) {
            audioManager.playMeleeHit(isHeavy = false)
            return false
        }

        // Damage calculation (Knight in defensive riposte stance takes 60% reduced damage)
        val finalAmount = if (aiState == EnemyAiState.SPECIAL_COUNTER_RIPOSTE && stateTimer < 0.16f) {
            amount * 0.40f
        } else {
            amount
        }
        health = (health - finalAmount).coerceAtLeast(0f)
        hitFlashTimer = if (isHeavyImpact) 0.28f else 0.18f

        // Check Fury Phase trigger (at <= 30% max health)
        if (health <= maxHealth * 0.30f && !hasActivatedFury && health > 0f) {
            triggerFuryRoar(audioManager)
            return false
        }

        // Unstoppable poise during special heavy lunges/leaps (phantom charge, aerial execution, riposte counter)
        val hasPoise = aiState in listOf(
            EnemyAiState.SPECIAL_PHANTOM_CHARGE,
            EnemyAiState.SPECIAL_AERIAL_EXECUTION,
            EnemyAiState.SPECIAL_COUNTER_RIPOSTE,
            EnemyAiState.FURY_ROAR
        )

        // Clamped knockback impulse
        val maxKbSpeed = if (isHeavyImpact) 14.0f else 8.5f
        val kbMag = sqrt(knockbackX * knockbackX + knockbackZ * knockbackZ)
        if (!hasPoise) {
            if (kbMag > maxKbSpeed && kbMag > 0.001f) {
                val scale = maxKbSpeed / kbMag
                velocity.x = knockbackX * scale
                velocity.z = knockbackZ * scale
            } else {
                velocity.x = knockbackX
                velocity.z = knockbackZ
            }
        }

        hitReactionYawTwist = slashYawTwistDeg.coerceIn(-52f, 52f)
        hitReactionPitchTilt = slashPitchTiltDeg.coerceIn(-48f, 38f)
        hitReactionVerticalLift = verticalJolt.coerceIn(0f, 0.36f)

        if (health <= 0f) {
            aiState = EnemyAiState.DEAD
            stateTimer = 0f
            respawnCountdown = 5.0f
            currentComboStep = 0
            activeSkillName = ""
            audioManager.playEnemyDefeated()
            return true
        } else if (!hasPoise && (isHeavyImpact || !isAttackingState(aiState))) {
            aiState = EnemyAiState.HIT_STAGGER
            stateTimer = if (isHeavyImpact) 0.44f else 0.24f
            currentComboStep = 0
            activeSkillName = ""
        }
        return false
    }

    private fun triggerFuryRoar(audioManager: AudioManager) {
        hasActivatedFury = true
        isFuryPhase = true
        aiState = EnemyAiState.FURY_ROAR
        stateTimer = 0f
        currentComboStep = 0
        activeSkillName = "FURY AWAKENING"
        velocity.setZero()
        phantomChargeCooldown = 0f
        crescentSlashCooldown = 0f
        aerialExecutionCooldown = 0f
        whirlwindCooldown = 0f
        counterRiposteCooldown = 0f
        audioManager.playFistHeavySkill(0)
    }

    private fun isAttackingState(state: EnemyAiState): Boolean = state in listOf(
        EnemyAiState.ATTACK_WINDUP,
        EnemyAiState.ATTACK_STRIKE,
        EnemyAiState.COMBO_SLASH_HORIZONTAL,
        EnemyAiState.COMBO_SLASH_DIAGONAL,
        EnemyAiState.COMBO_RISING_SLASH,
        EnemyAiState.COMBO_OVERHEAD_SLAM,
        EnemyAiState.COMBO_FINISHER_THRUST,
        EnemyAiState.SPECIAL_WHIRLWIND,
        EnemyAiState.SPECIAL_PHANTOM_CHARGE,
        EnemyAiState.SPECIAL_CRESCENT_SLASH,
        EnemyAiState.SPECIAL_AERIAL_EXECUTION,
        EnemyAiState.SPECIAL_COUNTER_RIPOSTE
    )

    open fun update(
        delta: Float,
        player: PlayerCharacter,
        worldManager: WorldManager,
        audioManager: AudioManager,
        onPlayerHit: (Float) -> Unit
    ) {
        update(delta, player, worldManager, audioManager, null, onPlayerHit)
    }

    open fun update(
        delta: Float,
        player: PlayerCharacter,
        worldManager: WorldManager,
        audioManager: AudioManager,
        combatSystem: CombatSystem? = null,
        onPlayerHit: (Float) -> Unit
    ) {
        animTime += delta

        // Tick hit flash & visual timers
        if (hitFlashTimer > 0f) {
            hitFlashTimer = (hitFlashTimer - delta).coerceAtLeast(0f)
        }
        if (hitReactionVerticalLift > 0f) {
            hitReactionVerticalLift = (hitReactionVerticalLift - delta * 1.25f).coerceAtLeast(0f)
        }

        // Tick ability cooldowns (recovering 2x faster in Fury Phase)
        val cdRate = if (isFuryPhase) 1.85f else 1.0f
        if (phantomChargeCooldown > 0f) phantomChargeCooldown = (phantomChargeCooldown - delta * cdRate).coerceAtLeast(0f)
        if (crescentSlashCooldown > 0f) crescentSlashCooldown = (crescentSlashCooldown - delta * cdRate).coerceAtLeast(0f)
        if (aerialExecutionCooldown > 0f) aerialExecutionCooldown = (aerialExecutionCooldown - delta * cdRate).coerceAtLeast(0f)
        if (whirlwindCooldown > 0f) whirlwindCooldown = (whirlwindCooldown - delta * cdRate).coerceAtLeast(0f)
        if (counterRiposteCooldown > 0f) counterRiposteCooldown = (counterRiposteCooldown - delta * cdRate).coerceAtLeast(0f)
        if (comboAttackCooldown > 0f) comboAttackCooldown = (comboAttackCooldown - delta * cdRate).coerceAtLeast(0f)

        // Continuous crimson aura motes when in Fury Phase
        if (isFuryPhase && aiState != EnemyAiState.DEAD) {
            furyAuraPulseTimer += delta
            if (furyAuraPulseTimer >= 0.12f) {
                furyAuraPulseTimer = 0f
                combatSystem?.spawnParticleBurst(
                    origin = Vector3(
                        entity.position.x + (Math.random().toFloat() - 0.5f) * 0.8f,
                        entity.position.y + 0.6f + Math.random().toFloat() * 1.2f,
                        entity.position.z + (Math.random().toFloat() - 0.5f) * 0.8f
                    ),
                    count = 2,
                    speed = 2.4f,
                    variant = 2 // Crimson particles
                )
            }
        }

        // Distance & vector to player
        val dxToPlayer = player.position.x - entity.position.x
        val dzToPlayer = player.position.z - entity.position.z
        val distToPlayer = sqrt(dxToPlayer * dxToPlayer + dzToPlayer * dzToPlayer).coerceAtLeast(0.001f)

        // Track player combat behavior over rolling window
        updateAdaptiveHistory(player, distToPlayer, delta)

        // Update active enemy crescent slash waves
        updateCrescentWaves(delta, player, worldManager, audioManager, combatSystem, onPlayerHit)

        val currentMoveSpeed = if (isFuryPhase) furyMoveSpeed else baseMoveSpeed

        // State Machine Execution
        when (aiState) {
            EnemyAiState.DEAD -> {
                stateTimer += delta
                respawnCountdown = (5.0f - stateTimer).coerceAtLeast(0f)
                velocity.x = MathUtils.lerp(velocity.x, 0f, (delta * 9.5f).coerceAtMost(1f))
                velocity.z = MathUtils.lerp(velocity.z, 0f, (delta * 9.5f).coerceAtMost(1f))
                if (stateTimer >= 5.0f) {
                    resetToSpawn(worldManager)
                    return
                }
            }

            EnemyAiState.FURY_ROAR -> {
                stateTimer += delta
                velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 12f)
                velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 12f)

                // Face the player directly during awakening roar
                val desiredYaw = Math.toDegrees(atan2(dxToPlayer.toDouble(), dzToPlayer.toDouble())).toFloat()
                facingYaw = lerpAngle(facingYaw, desiredYaw, delta * 8f)

                if (stateTimer >= 0.25f && !strikeHitDone1) {
                    strikeHitDone1 = true
                    combatSystem?.spawnSlashWave(entity.position, 0f, 0f, 1.2f, 12f)
                    combatSystem?.spawnParticleBurst(entity.position, count = 28, speed = 8.5f, variant = 2)
                }

                if (stateTimer >= 1.20f) {
                    aiState = EnemyAiState.TACTICAL_STANCE
                    stateTimer = 0f
                    strikeHitDone1 = false
                }
            }

            EnemyAiState.HIT_STAGGER -> {
                stateTimer -= delta
                velocity.x = MathUtils.lerp(velocity.x, 0f, (delta * 9.2f).coerceAtMost(1f))
                velocity.z = MathUtils.lerp(velocity.z, 0f, (delta * 9.2f).coerceAtMost(1f))
                hitReactionYawTwist = MathUtils.lerp(hitReactionYawTwist, 0f, (delta * 5.2f).coerceAtMost(1f))
                hitReactionPitchTilt = MathUtils.lerp(hitReactionPitchTilt, 0f, (delta * 5.2f).coerceAtMost(1f))
                if (stateTimer <= 0f) {
                    aiState = EnemyAiState.TACTICAL_STANCE
                    stateTimer = 0f
                }
            }

            EnemyAiState.PATROL -> {
                if (distToPlayer <= detectionRadius) {
                    if (distToPlayer <= meleeTriggerRange && comboAttackCooldown <= 0f) {
                        evaluateTacticalAttack(distToPlayer, player)
                    } else {
                        aiState = EnemyAiState.TACTICAL_STANCE
                        stateTimer = 0f
                    }
                } else {
                    patrolAngle += delta * 0.55f
                    val targetX = spawnOrigin.x + sin(patrolAngle) * 4.5f
                    val targetZ = spawnOrigin.z + cos(patrolAngle) * 2.8f
                    val pdx = targetX - entity.position.x
                    val pdz = targetZ - entity.position.z
                    val pLen = sqrt(pdx * pdx + pdz * pdz)
                    if (pLen > 0.2f) {
                        val nx = pdx / pLen
                        val nz = pdz / pLen
                        velocity.x = MathUtils.lerp(velocity.x, nx * basePatrolSpeed, delta * 6f)
                        velocity.z = MathUtils.lerp(velocity.z, nz * basePatrolSpeed, delta * 6f)
                        val desiredYaw = Math.toDegrees(atan2(nx.toDouble(), nz.toDouble())).toFloat()
                        facingYaw = lerpAngle(facingYaw, desiredYaw, delta * 6f)
                    }
                }
            }

            EnemyAiState.PURSUE -> {
                if (distToPlayer > detectionRadius * 1.45f) {
                    aiState = EnemyAiState.PATROL
                } else if (distToPlayer <= meleeTriggerRange) {
                    // Within close or medium sword range: attack immediately if ready, otherwise enter tactical stance
                    evaluateTacticalAttack(distToPlayer, player)
                    if (aiState == EnemyAiState.PURSUE) {
                        aiState = EnemyAiState.TACTICAL_STANCE
                        stateTimer = 0f
                    }
                } else {
                    // When the player is far away, use Phantom Charge forward dash to close the distance
                    if (phantomChargeCooldown <= 0f && distToPlayer in 5.2f..15.5f) {
                        initiatePhantomCharge()
                    } else {
                        val nx = dxToPlayer / distToPlayer
                        val nz = dzToPlayer / distToPlayer
                        val pursueSpeed = if (distToPlayer > 5.5f) currentMoveSpeed * 1.18f else currentMoveSpeed
                        velocity.x = MathUtils.lerp(velocity.x, nx * pursueSpeed, delta * 10f)
                        velocity.z = MathUtils.lerp(velocity.z, nz * pursueSpeed, delta * 10f)
                        val desiredYaw = Math.toDegrees(atan2(nx.toDouble(), nz.toDouble())).toFloat()
                        facingYaw = lerpAngle(facingYaw, desiredYaw, delta * 12f)
                    }
                }
            }

            EnemyAiState.TACTICAL_STANCE -> {
                tacticalTimer += delta
                strafeTimer += delta
                if (strafeTimer > (if (isFuryPhase) 1.2f else 2.0f)) {
                    strafeTimer = 0f
                    strafeDirection = if (Math.random() > 0.5) 1.0f else -1.0f
                }

                // Face player decisively
                val desiredYaw = Math.toDegrees(atan2(dxToPlayer.toDouble(), dzToPlayer.toDouble())).toFloat()
                facingYaw = lerpAngle(facingYaw, desiredYaw, delta * 14f)

                // Prioritize attacking immediately when ready instead of repositioning
                evaluateTacticalAttack(distToPlayer, player)

                if (aiState == EnemyAiState.TACTICAL_STANCE) {
                    if (distToPlayer > meleeTriggerRange + 0.6f) {
                        // Player moved out of sword range — switch straight to decisive pursuit
                        aiState = EnemyAiState.PURSUE
                        stateTimer = 0f
                        val nx = dxToPlayer / distToPlayer
                        val nz = dzToPlayer / distToPlayer
                        velocity.x = MathUtils.lerp(velocity.x, nx * currentMoveSpeed, delta * 10f)
                        velocity.z = MathUtils.lerp(velocity.z, nz * currentMoveSpeed, delta * 10f)
                    } else {
                        // Maintain close sword engagement pressure without passive retreating
                        val nx = dxToPlayer / distToPlayer
                        val nz = dzToPlayer / distToPlayer
                        val perpX = -nz * strafeDirection
                        val perpZ = nx * strafeDirection

                        val radialSpeed = when {
                            distToPlayer < tacticalSpacingMin -> -1.2f
                            distToPlayer > 2.25f -> currentMoveSpeed * 0.90f
                            else -> currentMoveSpeed * 0.25f
                        }
                        val strafeSpeed = if (distToPlayer <= 2.5f) (if (isFuryPhase) 2.2f else 1.5f) else 0.6f
                        velocity.x = MathUtils.lerp(velocity.x, perpX * strafeSpeed + nx * radialSpeed, delta * 8f)
                        velocity.z = MathUtils.lerp(velocity.z, perpZ * strafeSpeed + nz * radialSpeed, delta * 8f)
                    }
                }
            }

            // COMBO CHAIN A: Step 1 - Horizontal Cross Slash (Readable Windup & Telegraphed Swing)
            EnemyAiState.COMBO_SLASH_HORIZONTAL -> {
                stateTimer += delta
                if (stateTimer < 0.20f) {
                    // Windup telegraph: raise blade, track player smoothly
                    trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 9f)
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)
                } else {
                    // Active strike swing with forward lunge towards player
                    applyForwardLunge(delta, 2.8f)
                    trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 6f)
                }

                val dmg = if (isFuryPhase) 13f else 10f
                checkMeleeHit(
                    hitWindowStart = 0.20f,
                    hitWindowEnd = 0.35f,
                    player = player,
                    damage = dmg,
                    knockbackSpeed = 2.0f,
                    audioManager = audioManager,
                    onPlayerHit = onPlayerHit,
                    maxRange = meleeStrikeMaxRange,
                    minForwardDot = 0.20f
                )

                if (stateTimer >= (if (isFuryPhase) 0.36f else 0.44f)) {
                    // Reliable combo transition: once initiated, commit to the 2-3 hit combo.
                    // Do not cancel simply because the player shifted slightly closer or was knocked back slightly.
                    val pDx = player.position.x - entity.position.x
                    val pDz = player.position.z - entity.position.z
                    val liveDist = sqrt(pDx * pDx + pDz * pDz)
                    if (liveDist <= 7.5f) {
                        initiateComboStep2()
                    } else {
                        concludeAttack(recoverDuration = 0.45f)
                    }
                }
            }

            // COMBO CHAIN A: Step 2 - Diagonal Cross Cleave
            EnemyAiState.COMBO_SLASH_DIAGONAL -> {
                stateTimer += delta
                if (stateTimer < 0.18f) {
                    trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 9f)
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)
                } else {
                    applyForwardLunge(delta, 3.0f)
                    trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 6f)
                }

                val dmg = if (isFuryPhase) 14f else 11f
                checkMeleeHit(
                    hitWindowStart = 0.18f,
                    hitWindowEnd = 0.34f,
                    player = player,
                    damage = dmg,
                    knockbackSpeed = 2.2f,
                    audioManager = audioManager,
                    onPlayerHit = onPlayerHit,
                    maxRange = meleeStrikeMaxRange,
                    minForwardDot = 0.20f
                )

                if (stateTimer >= (if (isFuryPhase) 0.36f else 0.44f)) {
                    // Chain into Step 3 Rising Execution to complete 2-3 hit sword combo
                    val pDx = player.position.x - entity.position.x
                    val pDz = player.position.z - entity.position.z
                    val liveDist = sqrt(pDx * pDx + pDz * pDz)
                    if (liveDist <= 7.0f) {
                        initiateComboStep3()
                    } else {
                        concludeAttack(recoverDuration = 0.48f)
                    }
                }
            }

            // COMBO CHAIN A: Step 3 - Rising Execution Slash
            EnemyAiState.COMBO_RISING_SLASH -> {
                stateTimer += delta
                if (stateTimer < 0.20f) {
                    trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 9f)
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)
                } else {
                    applyForwardLunge(delta, 2.6f)
                    trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 5f)
                }

                val dmg = if (isFuryPhase) 15f else 12f
                checkMeleeHit(
                    hitWindowStart = 0.20f,
                    hitWindowEnd = 0.36f,
                    player = player,
                    damage = dmg,
                    knockbackSpeed = 4.8f,
                    audioManager = audioManager,
                    onPlayerHit = onPlayerHit,
                    isHeavy = true,
                    upwardKnockback = 2.4f,
                    maxRange = meleeStrikeMaxRange,
                    minForwardDot = 0.22f
                )

                if (stateTimer >= (if (isFuryPhase) 0.38f else 0.46f)) {
                    val pDx = player.position.x - entity.position.x
                    val pDz = player.position.z - entity.position.z
                    val liveDist = sqrt(pDx * pDx + pDz * pDz)
                    // In Fury Phase, optional 4th step finisher; otherwise cleanly conclude the 3-hit combo
                    if (isFuryPhase && liveDist <= 3.2f) {
                        initiateComboStep4()
                    } else {
                        concludeAttack(recoverDuration = 0.50f)
                    }
                }
            }

            // COMBO CHAIN A: Step 4 - Overhead Heavy Smash (Heavy Windup & Telegraphed Slam in Fury)
            EnemyAiState.COMBO_OVERHEAD_SLAM -> {
                stateTimer += delta
                if (stateTimer < 0.30f) {
                    trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 6f)
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)
                } else if (stateTimer in 0.30f..0.46f) {
                    applyForwardLunge(delta, 3.2f)
                } else {
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 10f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 10f)
                }

                if (!strikeHitDone1 && stateTimer >= 0.30f) {
                    strikeHitDone1 = true
                    combatSystem?.spawnSlashWave(entity.position, facingYaw, 90f, 1.1f, 8.5f)
                    combatSystem?.spawnParticleBurst(entity.position, count = 12, speed = 6.0f, variant = 2)
                    audioManager.playSwordHeavySkill(1)
                }

                val dmg = if (isFuryPhase) 18f else 14f
                checkMeleeHit(
                    hitWindowStart = 0.30f,
                    hitWindowEnd = 0.46f,
                    player = player,
                    damage = dmg,
                    knockbackSpeed = 6.5f,
                    audioManager = audioManager,
                    onPlayerHit = onPlayerHit,
                    isHeavy = true,
                    maxRange = 2.65f,
                    minForwardDot = 0.30f
                )

                if (stateTimer >= (if (isFuryPhase) 0.50f else 0.60f)) {
                    concludeAttack(recoverDuration = 0.58f)
                }
            }

            // COMBO CHAIN A: Step 5 - Finisher Corrupted Pierce Thrust (Legacy fallback)
            EnemyAiState.COMBO_FINISHER_THRUST -> {
                stateTimer += delta
                if (stateTimer < 0.26f) {
                    trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 6f)
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)
                } else {
                    applyForwardLunge(delta, 4.2f)
                }

                val dmg = if (isFuryPhase) 18f else 14f
                checkMeleeHit(
                    hitWindowStart = 0.26f,
                    hitWindowEnd = 0.42f,
                    player = player,
                    damage = dmg,
                    knockbackSpeed = 7.0f,
                    audioManager = audioManager,
                    onPlayerHit = onPlayerHit,
                    isHeavy = true,
                    maxRange = 2.65f,
                    minForwardDot = 0.35f
                )

                if (stateTimer >= (if (isFuryPhase) 0.45f else 0.55f)) {
                    concludeAttack(recoverDuration = 0.60f)
                }
            }

            // SPECIAL 1: Whirlwind Assault (Controlled spinning strikes with clear windup)
            EnemyAiState.SPECIAL_WHIRLWIND -> {
                stateTimer += delta
                // Controlled, readable spin rotation and gentle drift
                facingYaw = (facingYaw + delta * 460f) % 360f
                applyForwardLunge(delta, 2.0f)

                val dmg = if (isFuryPhase) 10f else 7f
                val pDx = player.position.x - entity.position.x
                val pDz = player.position.z - entity.position.z
                val liveDist = sqrt(pDx * pDx + pDz * pDz)
                val liveDy = abs(player.position.y - entity.position.y)
                val whirlwindMaxRange = 2.75f

                // First spin hit
                if (!strikeHitDone1 && stateTimer in 0.20f..0.32f && !player.isDodging && liveDist <= whirlwindMaxRange && liveDy <= 1.80f) {
                    strikeHitDone1 = true
                    val hit = player.applyDamage(dmg, 0f, 0f)
                    if (hit) {
                        audioManager.playMeleeHit(isHeavy = false)
                        combatSystem?.spawnParticleBurst(entity.position, count = 8, speed = 5.0f, variant = 2)
                        onPlayerHit(dmg)
                    }
                }
                // Second spin hit
                if (!strikeHitDone2 && stateTimer in 0.46f..0.58f && !player.isDodging && liveDist <= whirlwindMaxRange && liveDy <= 1.80f) {
                    strikeHitDone2 = true
                    val hit = player.applyDamage(dmg, 0f, 0f)
                    if (hit) {
                        audioManager.playMeleeHit(isHeavy = true)
                        combatSystem?.spawnSlashWave(entity.position, 0f, 0f, 1.4f, 10f)
                        combatSystem?.spawnParticleBurst(entity.position, count = 14, speed = 6.5f, variant = 2)
                        onPlayerHit(dmg)
                    }
                }

                if (stateTimer >= (if (isFuryPhase) 0.60f else 0.74f)) {
                    concludeAttack(recoverDuration = 0.55f)
                }
            }

            // SPECIAL 2: Phantom Charge (Supersonic gap closer with telegraph & reasonable impact)
            EnemyAiState.SPECIAL_PHANTOM_CHARGE -> {
                stateTimer += delta
                if (stateTimer < 0.36f) {
                    // Windup stance: crouch low, aim sword at player (readable preparation)
                    trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 8f)
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)
                } else if (stateTimer in 0.36f..0.62f) {
                    // High-speed lunge
                    val rad = Math.toRadians(facingYaw.toDouble()).toFloat()
                    val fwdX = sin(rad)
                    val fwdZ = cos(rad)
                    velocity.x = fwdX * 13.5f
                    velocity.z = fwdZ * 13.5f

                    // Spawn dash trail particles
                    combatSystem?.spawnParticleBurst(
                        origin = Vector3(entity.position.x, entity.position.y + 1.0f, entity.position.z),
                        count = 2,
                        speed = 3.0f,
                        variant = 2
                    )

                    // Impact check with live bounds and dodge avoidance
                    val dmg = if (isFuryPhase) 18f else 14f
                    val pDx = player.position.x - entity.position.x
                    val pDz = player.position.z - entity.position.z
                    val liveDist = sqrt(pDx * pDx + pDz * pDz)
                    val liveDy = abs(player.position.y - entity.position.y)
                    val dot = if (liveDist > 0.001f) (pDx / liveDist) * fwdX + (pDz / liveDist) * fwdZ else 1f

                    if (!strikeHitDone1 && !player.isDodging && liveDist <= 2.45f && liveDy <= 1.80f && dot >= 0.38f) {
                        strikeHitDone1 = true
                        val hit = player.applyDamage(dmg, fwdX * 8.0f, fwdZ * 8.0f)
                        if (hit) {
                            audioManager.playMeleeHit(isHeavy = true)
                            combatSystem?.spawnSlashWave(entity.position, facingYaw, 0f, 1.1f, 9.5f)
                            combatSystem?.spawnParticleBurst(entity.position, count = 16, speed = 7.5f, variant = 2)
                            onPlayerHit(dmg)
                        }
                    }
                } else {
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 10f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 10f)
                }

                if (stateTimer >= (if (isFuryPhase) 0.65f else 0.78f)) {
                    concludeAttack(recoverDuration = 0.58f)
                }
            }

            // SPECIAL 3: Crescent Slash (Fires 3D crimson projectile energy wave with strict 3.85m range bound)
            EnemyAiState.SPECIAL_CRESCENT_SLASH -> {
                stateTimer += delta
                trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 8f)
                velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)

                if (!strikeHitDone1 && stateTimer >= 0.28f) {
                    strikeHitDone1 = true
                    audioManager.playSwordHeavySkill(0)
                    val rad = Math.toRadians(facingYaw.toDouble()).toFloat()
                    val fwdX = sin(rad)
                    val fwdZ = cos(rad)
                    val waveSpeed = 13.5f
                    val waveDmg = if (isFuryPhase) 16f else 12f
                    val spawnPos = Vector3(
                        entity.position.x + fwdX * 0.9f,
                        entity.position.y + 1.15f,
                        entity.position.z + fwdZ * 0.9f
                    )
                    activeCrescentWaves.add(
                        ActiveEnemyCrescentWave(
                            origin = Vector3(entity.position),
                            position = spawnPos,
                            velocity = Vector3(fwdX * waveSpeed, 0f, fwdZ * waveSpeed),
                            damage = waveDmg,
                            yawDeg = facingYaw,
                            maxRange = 3.85f,
                            maxLifetime = 0.28f
                        )
                    )
                    combatSystem?.spawnSlashWave(spawnPos, facingYaw, 0f, 1.3f, 11f)
                    combatSystem?.spawnParticleBurst(spawnPos, count = 12, speed = 5.5f, variant = 2)
                }

                if (stateTimer >= (if (isFuryPhase) 0.46f else 0.58f)) {
                    concludeAttack(recoverDuration = 0.48f)
                }
            }

            // SPECIAL 4: Aerial Execution (Vertical leap -> downward sword slam & shockwave)
            EnemyAiState.SPECIAL_AERIAL_EXECUTION -> {
                stateTimer += delta
                if (stateTimer < 0.24f) {
                    // Crouch prep
                    trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 10f)
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)
                } else if (stateTimer in 0.24f..0.50f) {
                    // Leap in air & advance towards player
                    val liveDist = distToPlayer.coerceAtLeast(0.01f)
                    val nx = dxToPlayer / liveDist
                    val nz = dzToPlayer / liveDist
                    velocity.x = nx * (currentMoveSpeed * 1.25f)
                    velocity.z = nz * (currentMoveSpeed * 1.25f)
                } else {
                    // Plunge downward
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 12f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 12f)
                }

                // Impact frame at 0.52s
                if (!strikeHitDone1 && stateTimer >= 0.52f) {
                    strikeHitDone1 = true
                    audioManager.playMeleeHit(isHeavy = true)
                    audioManager.playSwordHeavySkill(2)
                    combatSystem?.spawnSlashWave(entity.position, 0f, 0f, 1.6f, 12f)
                    combatSystem?.spawnParticleBurst(entity.position, count = 22, speed = 7.5f, variant = 2)

                    // Strict live impact radius (2.45m max, checked against live position and dodge)
                    val dmg = if (isFuryPhase) 20f else 15f
                    val pDx = player.position.x - entity.position.x
                    val pDz = player.position.z - entity.position.z
                    val liveDist = sqrt(pDx * pDx + pDz * pDz)
                    val liveDy = abs(player.position.y - entity.position.y)

                    if (!player.isDodging && liveDist <= 2.45f && liveDy <= 1.80f) {
                        val rad = Math.toRadians(facingYaw.toDouble()).toFloat()
                        val hit = player.applyDamage(dmg, sin(rad) * 7.5f, cos(rad) * 7.5f)
                        if (hit) onPlayerHit(dmg)
                    }
                }

                if (stateTimer >= (if (isFuryPhase) 0.68f else 0.80f)) {
                    concludeAttack(recoverDuration = 0.60f)
                }
            }

            // SPECIAL 5: Counterattack / Riposte (Guards attack opening, then counter-slashes)
            EnemyAiState.SPECIAL_COUNTER_RIPOSTE -> {
                stateTimer += delta
                trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 10f)

                if (stateTimer < 0.20f) {
                    // Defensive guard stance
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 10f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 10f)
                } else {
                    // Retaliatory counter-strike
                    applyForwardLunge(delta, 3.6f)
                    val dmg = if (isFuryPhase) 15f else 11f
                    checkMeleeHit(
                        hitWindowStart = 0.20f,
                        hitWindowEnd = 0.36f,
                        player = player,
                        damage = dmg,
                        knockbackSpeed = 6.0f,
                        audioManager = audioManager,
                        onPlayerHit = onPlayerHit,
                        maxRange = 2.65f,
                        minForwardDot = 0.25f
                    )
                }

                if (stateTimer >= (if (isFuryPhase) 0.40f else 0.50f)) {
                    concludeAttack(recoverDuration = 0.48f)
                }
            }

            // Legacy Fallbacks
            EnemyAiState.ATTACK_WINDUP -> {
                stateTimer += delta
                velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)
                trackPlayerYaw(dxToPlayer, dzToPlayer, delta, 6f)
                if (stateTimer >= 0.42f) {
                    aiState = EnemyAiState.COMBO_SLASH_HORIZONTAL
                    stateTimer = 0f
                    strikeHitDone1 = false
                    audioManager.playMeleeSwing(isSword = true, isHeavy = false)
                }
            }

            EnemyAiState.ATTACK_STRIKE -> {
                stateTimer += delta
                applyForwardLunge(delta, 3.2f)
                val dmg = if (isFuryPhase) 14f else 11f
                checkMeleeHit(
                    hitWindowStart = 0.18f,
                    hitWindowEnd = 0.32f,
                    player = player,
                    damage = dmg,
                    knockbackSpeed = 5.0f,
                    audioManager = audioManager,
                    onPlayerHit = onPlayerHit,
                    maxRange = meleeStrikeMaxRange,
                    minForwardDot = 0.20f
                )
                if (stateTimer >= 0.40f) {
                    concludeAttack(recoverDuration = 0.48f)
                }
            }

            EnemyAiState.ATTACK_RECOVER -> {
                stateTimer += delta
                velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)
                val maxRecover = if (isFuryPhase) (activeRecoverDuration * 0.70f).coerceAtLeast(0.35f) else activeRecoverDuration
                if (stateTimer >= maxRecover) {
                    aiState = EnemyAiState.TACTICAL_STANCE
                    stateTimer = 0f
                    currentComboStep = 0
                    activeSkillName = ""
                }
            }
        }

        // Integrate horizontal position & resolve collisions
        entity.position.x += velocity.x * delta
        entity.position.z += velocity.z * delta
        worldManager.resolveObstacleCollisions(entity.position, collisionRadius)

        // Player-Enemy physical separation (Knight takes majority of pushback so player is never trapped)
        if (aiState != EnemyAiState.DEAD) {
            val dx = entity.position.x - player.position.x
            val dz = entity.position.z - player.position.z
            val minSep = collisionRadius + player.collisionRadius + 0.12f
            val dSq = dx * dx + dz * dz
            if (dSq < minSep * minSep && dSq > 0.0001f) {
                val d = sqrt(dSq)
                val push = (minSep - d)
                entity.position.x += (dx / d) * push * 0.75f
                entity.position.z += (dz / d) * push * 0.75f
                player.position.x -= (dx / d) * push * 0.25f
                player.position.z -= (dz / d) * push * 0.25f
            }
        }

        // Ground height clamping (with aerial jump offset for Aerial Execution)
        val groundY = worldManager.getWalkableSurfaceHeight(entity.position.x, entity.position.z)
        val jumpHeight = if (aiState == EnemyAiState.SPECIAL_AERIAL_EXECUTION && stateTimer in 0.18f..0.50f) {
            val p = ((stateTimer - 0.18f) / 0.32f).coerceIn(0f, 1f)
            sin(p * Math.PI.toFloat()) * 2.6f
        } else 0f
        entity.position.y = groundY + jumpHeight

        val speedMag = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
        updateArticulatedPose(delta, speedMag)
        syncTransform()
    }

    private fun updateAdaptiveHistory(player: PlayerCharacter, distToPlayer: Float, delta: Float) {
        playerAttackWindowTimer += delta
        if (playerAttackWindowTimer >= 2.5f) {
            playerAttackWindowTimer = 0f
            playerRecentAttacks = (playerRecentAttacks - 1).coerceAtLeast(0)
        }

        // Detect player aggressive attacking state
        if (player.animationState.name.contains("SLASH") ||
            player.animationState.name.contains("FIST") ||
            player.animationState.name.contains("ATTACK")
        ) {
            playerRecentAttacks++
        }

        // Track player airborne time (jumping/aerial combat)
        if (!player.isGrounded) {
            playerAirborneAccum += delta
        } else {
            playerAirborneAccum = (playerAirborneAccum - delta * 0.8f).coerceAtLeast(0f)
        }

        // Track player keeping distance
        if (distToPlayer > 6.0f) {
            playerFarTimeAccum += delta
        } else {
            playerFarTimeAccum = (playerFarTimeAccum - delta * 1.2f).coerceAtLeast(0f)
        }
    }

    private fun evaluateTacticalAttack(distToPlayer: Float, player: PlayerCharacter) {
        // Priority 1: In sword range, decisively prioritize the 2-3 hit sword combo
        if (distToPlayer <= meleeTriggerRange && comboAttackCooldown <= 0f) {
            if (whirlwindCooldown <= 0f && Math.random() > 0.65) {
                initiateWhirlwind()
            } else {
                initiateComboStep1()
            }
            return
        }

        // Priority 2: Counterattack / Riposte when player is relentlessly attacking in close range
        if (counterRiposteCooldown <= 0f && distToPlayer <= 2.9f &&
            (playerRecentAttacks >= 2 || player.animationState.name.contains("SLASH") || player.animationState.name.contains("FIST"))
        ) {
            initiateCounterRiposte()
            return
        }

        // Priority 3: Anti-Air / Aerial Execution when player stays airborne or mid-range
        if (aerialExecutionCooldown <= 0f && (playerAirborneAccum > 0.45f || (distToPlayer in 3.0f..6.0f && Math.random() > 0.70))) {
            initiateAerialExecution()
            return
        }

        // Priority 4: Phantom Charge - decisive gap closer when player is at distance
        if (phantomChargeCooldown <= 0f && distToPlayer in 5.2f..14.5f) {
            initiatePhantomCharge()
            return
        }

        // Priority 5: Crescent Slash - mid-range pressure wave
        if (crescentSlashCooldown <= 0f && distToPlayer in 3.4f..7.5f) {
            initiateCrescentSlash()
            return
        }
    }

    private fun initiateComboStep1() {
        aiState = EnemyAiState.COMBO_SLASH_HORIZONTAL
        stateTimer = 0f
        currentComboStep = 1
        activeSkillName = "CROSS SLASH"
        comboAttackCooldown = if (isFuryPhase) 1.0f else 1.45f
        strikeHitDone1 = false
    }

    private fun initiateComboStep2() {
        aiState = EnemyAiState.COMBO_SLASH_DIAGONAL
        stateTimer = 0f
        currentComboStep = 2
        activeSkillName = "DIAGONAL CLEAVE"
        strikeHitDone1 = false
    }

    private fun initiateComboStep3() {
        aiState = EnemyAiState.COMBO_RISING_SLASH
        stateTimer = 0f
        currentComboStep = 3
        activeSkillName = "RISING EXECUTION"
        strikeHitDone1 = false
    }

    private fun initiateComboStep4() {
        aiState = EnemyAiState.COMBO_OVERHEAD_SLAM
        stateTimer = 0f
        currentComboStep = 4
        activeSkillName = "OVERHEAD SMASH"
        strikeHitDone1 = false
    }

    private fun initiateComboStep5() {
        aiState = EnemyAiState.COMBO_FINISHER_THRUST
        stateTimer = 0f
        currentComboStep = 5
        activeSkillName = "CORRUPTED PIERCE"
        strikeHitDone1 = false
    }

    private fun initiateWhirlwind() {
        aiState = EnemyAiState.SPECIAL_WHIRLWIND
        stateTimer = 0f
        currentComboStep = 1
        activeSkillName = "WHIRLWIND ASSAULT"
        whirlwindCooldown = if (isFuryPhase) 3.5f else 5.5f
        strikeHitDone1 = false
        strikeHitDone2 = false
    }

    private fun initiatePhantomCharge() {
        aiState = EnemyAiState.SPECIAL_PHANTOM_CHARGE
        stateTimer = 0f
        currentComboStep = 1
        activeSkillName = "PHANTOM CHARGE"
        phantomChargeCooldown = if (isFuryPhase) 3.0f else 4.5f
        strikeHitDone1 = false
    }

    private fun initiateCrescentSlash() {
        aiState = EnemyAiState.SPECIAL_CRESCENT_SLASH
        stateTimer = 0f
        currentComboStep = 1
        activeSkillName = "CRESCENT SLASH"
        crescentSlashCooldown = if (isFuryPhase) 2.5f else 3.8f
        strikeHitDone1 = false
    }

    private fun initiateAerialExecution() {
        aiState = EnemyAiState.SPECIAL_AERIAL_EXECUTION
        stateTimer = 0f
        currentComboStep = 1
        activeSkillName = "AERIAL EXECUTION"
        aerialExecutionCooldown = if (isFuryPhase) 4.0f else 6.5f
        strikeHitDone1 = false
    }

    private fun initiateCounterRiposte() {
        aiState = EnemyAiState.SPECIAL_COUNTER_RIPOSTE
        stateTimer = 0f
        currentComboStep = 1
        activeSkillName = "RIPOSTE COUNTER"
        counterRiposteCooldown = if (isFuryPhase) 2.2f else 4.0f
        strikeHitDone1 = false
    }

    private fun concludeAttack(recoverDuration: Float) {
        aiState = EnemyAiState.ATTACK_RECOVER
        stateTimer = 0f
        activeRecoverDuration = recoverDuration
        comboAttackCooldown = if (isFuryPhase) 1.0f else 1.45f
    }

    private fun applyForwardLunge(delta: Float, speed: Float) {
        val rad = Math.toRadians(facingYaw.toDouble()).toFloat()
        val fwdX = sin(rad)
        val fwdZ = cos(rad)
        velocity.x = MathUtils.lerp(velocity.x, fwdX * speed, delta * 8f)
        velocity.z = MathUtils.lerp(velocity.z, fwdZ * speed, delta * 8f)
    }

    private fun trackPlayerYaw(dxToPlayer: Float, dzToPlayer: Float, delta: Float, speed: Float) {
        val desiredYaw = Math.toDegrees(atan2(dxToPlayer.toDouble(), dzToPlayer.toDouble())).toFloat()
        facingYaw = lerpAngle(facingYaw, desiredYaw, delta * speed)
    }

    /**
     * Evaluates melee hit detection strictly against the player's LIVE current position at the
     * exact impact moment, enforcing explicit physical range, vertical reach, directional arc,
     * and Dash evasion rules.
     */
    private fun checkMeleeHit(
        hitWindowStart: Float,
        hitWindowEnd: Float,
        player: PlayerCharacter,
        damage: Float,
        knockbackSpeed: Float,
        audioManager: AudioManager,
        onPlayerHit: (Float) -> Unit,
        isHeavy: Boolean = false,
        upwardKnockback: Float = 0f,
        maxRange: Float = meleeStrikeMaxRange,
        minForwardDot: Float = 0.20f
    ) {
        if (strikeHitDone1 || stateTimer < hitWindowStart || stateTimer > hitWindowEnd) return
        if (player.isDodging) return

        val liveDx = player.position.x - entity.position.x
        val liveDz = player.position.z - entity.position.z
        val liveDy = abs(player.position.y - entity.position.y)
        if (liveDy > 1.80f) return

        val liveDistSq = liveDx * liveDx + liveDz * liveDz
        if (liveDistSq > maxRange * maxRange) return

        val liveDist = sqrt(liveDistSq).coerceAtLeast(0.001f)
        val rad = Math.toRadians(facingYaw.toDouble()).toFloat()
        val fwdX = sin(rad)
        val fwdZ = cos(rad)
        val dot = (liveDx / liveDist) * fwdX + (liveDz / liveDist) * fwdZ
        if (dot >= minForwardDot) {
            strikeHitDone1 = true
            val hitApplied = player.applyDamage(damage, fwdX * knockbackSpeed, fwdZ * knockbackSpeed)
            if (hitApplied) {
                if (upwardKnockback > 0f) player.velocity.y = upwardKnockback
                audioManager.playMeleeHit(isHeavy = isHeavy)
                onPlayerHit(damage)
            }
        }
    }

    private fun updateCrescentWaves(
        delta: Float,
        player: PlayerCharacter,
        worldManager: WorldManager,
        audioManager: AudioManager,
        combatSystem: CombatSystem?,
        onPlayerHit: (Float) -> Unit
    ) {
        val iter = activeCrescentWaves.iterator()
        while (iter.hasNext()) {
            val wave = iter.next()
            wave.age += delta
            if (wave.age >= wave.maxLifetime) {
                iter.remove()
                continue
            }
            wave.position.mulAdd(wave.velocity, delta)

            // Enforce strict physical range from wave origin so it never hits distant players
            val travelDx = wave.position.x - wave.origin.x
            val travelDz = wave.position.z - wave.origin.z
            if (travelDx * travelDx + travelDz * travelDz > wave.maxRange * wave.maxRange) {
                iter.remove()
                continue
            }

            // Check obstacle collisions
            var hitObstacle = false
            for (collider in worldManager.generatedData.obstacleColliders) {
                val cdx = wave.position.x - collider.x
                val cdz = wave.position.z - collider.z
                val r = collider.radius + 0.55f
                if (cdx * cdx + cdz * cdz <= r * r) {
                    hitObstacle = true
                    break
                }
            }
            if (hitObstacle) {
                iter.remove()
                continue
            }

            // Check collision with player's live current position
            if (!wave.hasHitPlayer) {
                if (player.isDodging) continue
                val liveDxFromKnight = player.position.x - entity.position.x
                val liveDzFromKnight = player.position.z - entity.position.z
                if (liveDxFromKnight * liveDxFromKnight + liveDzFromKnight * liveDzFromKnight > (wave.maxRange + 0.5f) * (wave.maxRange + 0.5f)) {
                    continue
                }

                val dx = player.position.x - wave.position.x
                val dz = player.position.z - wave.position.z
                val dy = abs((player.position.y + 0.9f) - wave.position.y)
                val hitRadius = player.collisionRadius + 0.65f
                val distSq = dx * dx + dz * dz
                if (distSq <= hitRadius * hitRadius && dy < 1.35f) {
                    wave.hasHitPlayer = true
                    val vLen = sqrt(wave.velocity.x * wave.velocity.x + wave.velocity.z * wave.velocity.z).coerceAtLeast(0.01f)
                    val kx = (wave.velocity.x / vLen) * 6.5f
                    val kz = (wave.velocity.z / vLen) * 6.5f
                    val hit = player.applyDamage(wave.damage, kx, kz)
                    if (hit) {
                        audioManager.playMeleeHit(isHeavy = true)
                        combatSystem?.spawnParticleBurst(wave.position, count = 14, speed = 6.0f, variant = 2)
                        onPlayerHit(wave.damage)
                    }
                    iter.remove()
                }
            }
        }
    }

    private fun updateArticulatedPose(delta: Float, speedMag: Float) {
        var pelvisY = 1.05f
        var torsoPitch = 0f
        var torsoYaw = 0f
        var headPitch = 0f
        var headYaw = 0f
        var lArmPitch = 0f
        var lArmYaw = 0f
        var lArmRoll = 10f
        var rArmPitch = -18f
        var rArmYaw = 0f
        var rArmRoll = -12f
        var lLegPitch = 0f
        var rLegPitch = 0f

        when (aiState) {
            EnemyAiState.PATROL, EnemyAiState.PURSUE, EnemyAiState.TACTICAL_STANCE -> {
                if (speedMag > 0.25f) {
                    val cycle = animTime * (if (isFuryPhase) 9.8f else 6.8f)
                    val s = sin(cycle)
                    val c = cos(cycle)
                    pelvisY = 1.03f + abs(c) * 0.04f
                    torsoPitch = 10f
                    torsoYaw = -s * 8f
                    lLegPitch = -s * 36f
                    rLegPitch = s * 36f
                    lArmPitch = s * 24f
                    rArmPitch = -28f - s * 20f
                } else {
                    val b = sin(animTime * 2.2f)
                    pelvisY = 1.05f + b * 0.015f
                    rArmPitch = -22f + b * 5f
                    torsoPitch = 6f
                }
            }

            EnemyAiState.COMBO_SLASH_HORIZONTAL -> {
                val p = (stateTimer / 0.46f).coerceIn(0f, 1f)
                if (p < 0.48f) {
                    val wp = p / 0.48f
                    pelvisY = 1.02f
                    torsoPitch = 8f
                    torsoYaw = -10f - wp * 35f
                    headYaw = -torsoYaw * 0.4f
                    rArmPitch = -20f - wp * 25f
                    rArmYaw = 10f + wp * 35f
                    lArmPitch = 20f
                    lLegPitch = -15f
                    rLegPitch = 18f
                } else {
                    val sp = (p - 0.48f) / 0.52f
                    pelvisY = 0.98f
                    torsoPitch = 12f
                    torsoYaw = -45f + sp * 95f
                    headYaw = -torsoYaw * 0.5f
                    rArmPitch = -35f - sin(sp * Math.PI.toFloat()) * 45f
                    rArmYaw = 45f - sp * 85f
                    lArmPitch = 30f
                    lLegPitch = -20f
                    rLegPitch = 24f
                }
            }

            EnemyAiState.COMBO_SLASH_DIAGONAL -> {
                val p = (stateTimer / 0.46f).coerceIn(0f, 1f)
                if (p < 0.44f) {
                    val wp = p / 0.44f
                    pelvisY = 1.01f
                    torsoPitch = 12f
                    torsoYaw = 15f + wp * 25f
                    rArmPitch = -40f - wp * 80f
                    rArmYaw = -10f - wp * 25f
                    lArmPitch = -15f
                } else {
                    val sp = (p - 0.44f) / 0.56f
                    pelvisY = 0.96f
                    torsoPitch = 18f
                    torsoYaw = 40f - sp * 80f
                    rArmPitch = -120f + sp * 135f
                    rArmYaw = -35f + sp * 75f
                    lArmPitch = -25f
                    lLegPitch = 24f
                    rLegPitch = -22f
                }
            }

            EnemyAiState.COMBO_RISING_SLASH -> {
                val p = (stateTimer / 0.48f).coerceIn(0f, 1f)
                if (p < 0.46f) {
                    val wp = p / 0.46f
                    pelvisY = 0.94f
                    torsoPitch = 5f + wp * 15f
                    rArmPitch = -20f + wp * 50f
                    lArmPitch = -30f
                } else {
                    val sp = (p - 0.46f) / 0.54f
                    pelvisY = 1.00f + sp * 0.12f
                    torsoPitch = -15f + sp * 25f
                    torsoYaw = -25f + sp * 45f
                    rArmPitch = 30f - sp * 175f
                    lArmPitch = -45f
                    lLegPitch = -26f
                    rLegPitch = 28f
                }
            }

            EnemyAiState.COMBO_OVERHEAD_SLAM -> {
                val p = (stateTimer / 0.62f).coerceIn(0f, 1f)
                if (p < 0.50f) {
                    val wp = p / 0.50f
                    pelvisY = 1.08f
                    torsoPitch = -24f * wp
                    rArmPitch = -18f - wp * 145f
                    lArmPitch = -wp * 120f
                } else {
                    val sp = (p - 0.50f) / 0.50f
                    pelvisY = 0.88f
                    torsoPitch = 38f
                    rArmPitch = -163f + sp * 180f
                    lArmPitch = -120f + sp * 140f
                    lLegPitch = 30f
                    rLegPitch = -34f
                }
            }

            EnemyAiState.COMBO_FINISHER_THRUST -> {
                val p = (stateTimer / 0.56f).coerceIn(0f, 1f)
                if (p < 0.50f) {
                    val wp = p / 0.50f
                    pelvisY = 0.96f
                    torsoPitch = 10f
                    rArmPitch = -30f - wp * 60f
                    lArmPitch = 15f + wp * 20f
                } else {
                    pelvisY = 0.92f
                    torsoPitch = 16f
                    torsoYaw = 35f
                    rArmPitch = -90f
                    rArmYaw = 0f
                    lArmPitch = 35f
                    lLegPitch = -38f
                    rLegPitch = 36f
                }
            }

            EnemyAiState.SPECIAL_WHIRLWIND -> {
                pelvisY = 1.00f
                torsoPitch = 14f
                rArmPitch = -85f
                rArmYaw = 75f
                lArmPitch = -85f
                lArmYaw = -75f
                lLegPitch = sin(animTime * 10f) * 24f
                rLegPitch = -lLegPitch
            }

            EnemyAiState.SPECIAL_PHANTOM_CHARGE -> {
                val p = (stateTimer / 0.80f).coerceIn(0f, 1f)
                if (p < 0.48f) {
                    // Crouch telegraph stance
                    pelvisY = 0.84f
                    torsoPitch = 36f
                    rArmPitch = -95f
                    rArmYaw = 15f
                    lArmPitch = 50f
                } else {
                    pelvisY = 0.88f
                    torsoPitch = 32f
                    rArmPitch = -85f
                    rArmYaw = 10f
                    lArmPitch = 45f
                    lLegPitch = -35f
                    rLegPitch = 35f
                }
            }

            EnemyAiState.SPECIAL_CRESCENT_SLASH -> {
                val p = (stateTimer / 0.62f).coerceIn(0f, 1f)
                if (p < 0.52f) {
                    val wp = p / 0.52f
                    pelvisY = 1.02f
                    torsoPitch = 10f
                    torsoYaw = -20f - wp * 40f
                    rArmPitch = -30f - wp * 25f
                    rArmYaw = 20f + wp * 40f
                    lArmPitch = 25f
                } else {
                    val sp = (p - 0.52f) / 0.48f
                    pelvisY = 0.98f
                    torsoPitch = 14f
                    torsoYaw = -60f + sp * 125f
                    rArmPitch = -55f
                    rArmYaw = 60f - sp * 120f
                    lArmPitch = 35f
                }
            }

            EnemyAiState.SPECIAL_AERIAL_EXECUTION -> {
                if (stateTimer < 0.54f) {
                    pelvisY = 1.15f
                    torsoPitch = -16f
                    rArmPitch = -155f
                    lArmPitch = -140f
                    lLegPitch = -32f
                    rLegPitch = -32f
                } else {
                    pelvisY = 0.82f
                    torsoPitch = 45f
                    rArmPitch = 18f
                    lArmPitch = 24f
                    lLegPitch = 35f
                    rLegPitch = -35f
                }
            }

            EnemyAiState.SPECIAL_COUNTER_RIPOSTE -> {
                if (stateTimer < 0.22f) {
                    // Parry guard pose
                    pelvisY = 0.96f
                    torsoPitch = 12f
                    torsoYaw = -28f
                    lArmPitch = -75f
                    rArmPitch = -70f
                    rArmYaw = -25f
                } else {
                    // Riposte cross slash
                    pelvisY = 0.94f
                    torsoPitch = 22f
                    torsoYaw = 45f
                    rArmPitch = -35f
                    rArmYaw = 65f
                    lArmPitch = 25f
                }
            }

            EnemyAiState.FURY_ROAR -> {
                val p = (stateTimer / 1.20f).coerceIn(0f, 1f)
                val roarPulse = sin(p * Math.PI.toFloat() * 3f) * 0.04f
                pelvisY = 1.05f + roarPulse
                torsoPitch = -30f
                headPitch = -42f
                rArmPitch = -140f
                rArmRoll = 35f
                lArmPitch = -140f
                lArmRoll = -35f
                lLegPitch = -20f
                rLegPitch = 20f
            }

            EnemyAiState.ATTACK_WINDUP -> {
                val p = (stateTimer / 0.38f).coerceIn(0f, 1f)
                pelvisY = 1.02f
                torsoPitch = -16f * p
                torsoYaw = -32f * p
                rArmPitch = -145f * p
                lArmPitch = 28f * p
            }

            EnemyAiState.ATTACK_STRIKE -> {
                val p = (stateTimer / 0.30f).coerceIn(0f, 1f)
                pelvisY = 0.96f
                torsoPitch = 28f
                torsoYaw = 35f * p
                rArmPitch = -145f + p * 125f
                lArmPitch = 40f
            }

            EnemyAiState.ATTACK_RECOVER -> {
                pelvisY = 1.00f
                torsoPitch = 12f
                rArmPitch = -25f
            }

            EnemyAiState.HIT_STAGGER -> {
                pelvisY = 0.99f + hitReactionVerticalLift
                torsoPitch = hitReactionPitchTilt
                torsoYaw = hitReactionYawTwist
                lArmPitch = -48f
                rArmPitch = -55f
                lLegPitch = 16f
                rLegPitch = -16f
            }

            EnemyAiState.DEAD -> {
                val collapse = (stateTimer / 0.55f).coerceIn(0f, 1f)
                pelvisY = MathUtils.lerp(1.05f, 0.25f, collapse)
                torsoPitch = MathUtils.lerp(0f, -78f, collapse)
                rArmPitch = MathUtils.lerp(-18f, -85f, collapse)
                lArmPitch = MathUtils.lerp(0f, -70f, collapse)
            }
        }

        pelvisNode?.translation?.set(0f, pelvisY, 0f)
        torsoNode?.rotation?.setEulerAngles(torsoYaw, torsoPitch, 0f)
        headNode?.rotation?.setEulerAngles(headYaw, headPitch, 0f)
        leftArmNode?.rotation?.setEulerAngles(lArmYaw, lArmPitch, lArmRoll)
        rightArmNode?.rotation?.setEulerAngles(rArmYaw, rArmPitch, rArmRoll)
        leftLegNode?.rotation?.setEulerAngles(0f, lLegPitch, 0f)
        rightLegNode?.rotation?.setEulerAngles(0f, rLegPitch, 0f)

        entity.modelInstance.calculateTransforms()
    }

    private fun syncTransform() {
        val furyPulse = if (isFuryPhase) 1.06f + sin(animTime * 12f) * 0.04f else 1.0f
        val scale = if (aiState == EnemyAiState.DEAD && stateTimer > 4.2f) {
            ((5.0f - stateTimer) / 0.8f).coerceIn(0.01f, 1f)
        } else if (hitFlashTimer > 0f) {
            1.05f * furyPulse
        } else {
            1.0f * furyPulse
        }
        entity.modelInstance.transform.idt()
            .translate(entity.position.x, entity.position.y, entity.position.z)
            .rotate(0f, 1f, 0f, facingYaw)
            .scale(scale, scale, scale)
    }

    private fun lerpAngle(from: Float, to: Float, alpha: Float): Float {
        var diff = (to - from) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        return from + diff * alpha.coerceIn(0f, 1f)
    }
}
