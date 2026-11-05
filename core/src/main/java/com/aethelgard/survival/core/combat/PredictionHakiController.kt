package com.aethelgard.survival.core.combat

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.entities.BlueFlowerMonsterController
import com.aethelgard.survival.core.entities.EnemyAiState
import com.aethelgard.survival.core.entities.EnemyController
import com.aethelgard.survival.core.entities.EntityManager
import com.aethelgard.survival.core.entities.GreenSlimeController
import com.aethelgard.survival.core.entities.RedFlowerMonsterController
import com.aethelgard.survival.core.player.AnimationState
import com.aethelgard.survival.core.player.PlayerCharacter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Prediction Haki Directional Dodge types.
 */
enum class HakiDodgeKind(val animState: AnimationState) {
    SIDE_LEFT(AnimationState.DODGE_SIDE),
    SIDE_RIGHT(AnimationState.DODGE_SIDE),
    BACKWARD(AnimationState.DODGE_BACK),
    LOW(AnimationState.DODGE_LOW),
    FORWARD(AnimationState.DODGE_FORWARD)
}

/**
 * Encapsulates the Prediction Haki system:
 * - Manual activation and deactivation toggle via dedicated HUD button
 * - Subtle blue perception aura while active
 * - Continuous evaluation of incoming enemy melee attacks and projectiles
 * - Precise physical dodge selection (SIDE, BACK, LOW, FORWARD) only when a genuine hit is imminent
 * - Exactly 8 charges per activation (consumed ONLY on successful evasions)
 * - Exhaustion state when charges reach zero with recharge cooldown
 * - Purely physical evasion using existing movement and animation architecture
 * - Distinctive blue visual effects and audio cue on evasion
 */
class PredictionHakiController(
    val player: PlayerCharacter,
    val entityManager: EntityManager,
    val combatSystem: CombatSystem,
    val audioManager: AudioManager
) {
    companion object {
        const val MAX_CHARGES: Int = 8
        const val EXHAUSTION_COOLDOWN_DURATION: Float = 10.0f
        const val EVADE_COOLDOWN_WINDOW: Float = 0.38f
        const val DODGE_ACTION_DURATION: Float = 0.28f
        const val INVULNERABILITY_WINDOW: Float = 0.35f
        const val PHYSICAL_EVADE_SPEED: Float = 11.2f
    }

    var isActive: Boolean = false
        private set

    var remainingCharges: Int = MAX_CHARGES
        private set

    var exhaustionCooldown: Float = 0f
        private set

    var evadeCooldownTimer: Float = 0f
        private set

    // Visual effect timers
    var activeAuraPulseTimer: Float = 0f
        private set
    var recentEvadeVfxTimer: Float = 0f
        private set

    val isExhausted: Boolean
        get() = exhaustionCooldown > 0f

    val auraColor: Color = Color(0.20f, 0.72f, 1.0f, 0.75f)

    /**
     * Toggles manual activation / deactivation.
     * Returns true if state changed, false if blocked (e.g. while exhausted).
     */
    fun toggleActive(): Boolean {
        if (isExhausted) {
            return false
        }
        isActive = !isActive
        if (isActive && remainingCharges <= 0) {
            remainingCharges = MAX_CHARGES
        }
        audioManager.playHakiActivate(isActive)
        return true
    }

    fun activate(): Boolean {
        if (isExhausted || isActive) return false
        isActive = true
        if (remainingCharges <= 0) {
            remainingCharges = MAX_CHARGES
        }
        audioManager.playHakiActivate(true)
        return true
    }

    fun deactivate() {
        if (!isActive) return
        isActive = false
        audioManager.playHakiActivate(false)
    }

    fun reset() {
        isActive = false
        remainingCharges = MAX_CHARGES
        exhaustionCooldown = 0f
        evadeCooldownTimer = 0f
        activeAuraPulseTimer = 0f
        recentEvadeVfxTimer = 0f
    }

    fun update(delta: Float) {
        // 1. Tick cooldowns & visual timers
        if (exhaustionCooldown > 0f) {
            exhaustionCooldown = (exhaustionCooldown - delta).coerceAtLeast(0f)
            if (exhaustionCooldown <= 0f) {
                remainingCharges = MAX_CHARGES
            }
        }

        if (evadeCooldownTimer > 0f) {
            evadeCooldownTimer = (evadeCooldownTimer - delta).coerceAtLeast(0f)
        }

        if (recentEvadeVfxTimer > 0f) {
            recentEvadeVfxTimer = (recentEvadeVfxTimer - delta).coerceAtLeast(0f)
        }

        if (isActive) {
            activeAuraPulseTimer += delta
            // Subtle ambient particle while aura active
            if (activeAuraPulseTimer >= 0.18f) {
                activeAuraPulseTimer = 0f
                combatSystem.spawnParticleBurst(
                    origin = Vector3(player.position.x, player.position.y + 0.9f, player.position.z),
                    count = 2,
                    speed = 1.4f,
                    variant = 0
                )
            }
        }

        // If inactive, currently dodging, in hit stun, or on evade cooldown, do not trigger evasion
        if (!isActive || player.isDodging || evadeCooldownTimer > 0f || remainingCharges <= 0) {
            return
        }

        // 2. Evaluate incoming threats: Enemy Melee and Projectiles
        evaluateAndEvadeIncomingThreats()
    }

    private fun evaluateAndEvadeIncomingThreats() {
        // Collect all enemy controllers
        val enemies = entityManager.enemyControllers

        // Evaluate enemy melee attacks first
        for (enemy in enemies) {
            if (!enemy.entity.isActive || enemy.health <= 0f || enemy.aiState == EnemyAiState.DEAD) continue

            val imminentDodge = detectImminentMeleeAttack(enemy)
            if (imminentDodge != null) {
                triggerEvasion(imminentDodge.first, imminentDodge.second)
                return
            }
        }

        // Evaluate enemy crescent waves (Knight)
        for (enemy in enemies) {
            if (!enemy.entity.isActive || enemy.health <= 0f) continue
            for (wave in enemy.activeCrescentWaves) {
                if (wave.hasHitPlayer || wave.age >= wave.maxLifetime) continue
                val evade = detectImminentCrescentWave(wave)
                if (evade != null) {
                    triggerEvasion(evade.first, evade.second)
                    return
                }
            }
        }

        // Evaluate Green Slime orbs
        for (enemy in enemies) {
            if (enemy is GreenSlimeController) {
                for (orb in enemy.activeOrbs) {
                    if (!orb.isAlive || orb.age >= orb.maxLifetime) continue
                    val evade = detectImminentSlimeOrb(orb)
                    if (evade != null) {
                        triggerEvasion(evade.first, evade.second)
                        return
                    }
                }
            }
        }

        // Evaluate Red Flower Monster spores
        for (enemy in enemies) {
            if (enemy is RedFlowerMonsterController) {
                for (orb in enemy.activeOrbs) {
                    if (orb.hasHit || orb.age >= orb.maxLifetime) continue
                    val evade = detectImminentRedFlowerOrb(orb)
                    if (evade != null) {
                        triggerEvasion(evade.first, evade.second)
                        return
                    }
                }
            }
        }

        // Evaluate Blue Flower Monster spores
        for (enemy in enemies) {
            if (enemy is BlueFlowerMonsterController) {
                for (orb in enemy.activeOrbs) {
                    if (orb.hasHit || orb.age >= orb.maxLifetime) continue
                    val evade = detectImminentBlueFlowerOrb(orb)
                    if (evade != null) {
                        triggerEvasion(evade.first, evade.second)
                        return
                    }
                }
            }
        }
    }

    /**
     * Checks if an enemy's active melee attack state has hit active frames and is within real strike radius.
     * Selects appropriate physical dodge direction:
     * - Horizontal slash: Low dodge (duck) or backward step
     * - Diagonal / Rising slash / Overhead slam: Side slip
     * - Whirlwind / Aerial execution: Backward leap
     * - Phantom Charge: Side slip
     */
    private fun detectImminentMeleeAttack(enemy: EnemyController): Pair<HakiDodgeKind, Vector3>? {
        val dx = player.position.x - enemy.entity.position.x
        val dz = player.position.z - enemy.entity.position.z
        val dist = sqrt(dx * dx + dz * dz)

        // Direction from enemy to player
        val enemyYawRad = Math.toRadians(enemy.facingYaw.toDouble()).toFloat()
        val enemyFwdX = sin(enemyYawRad)
        val enemyFwdZ = cos(enemyYawRad)
        val toPlayerX = if (dist > 0.001f) dx / dist else enemyFwdX
        val toPlayerZ = if (dist > 0.001f) dz / dist else enemyFwdZ
        val forwardDot = enemyFwdX * toPlayerX + enemyFwdZ * toPlayerZ

        val state = enemy.aiState
        val timer = enemy.stateTimer

        // Perpendicular unit vector to enemy forward (lateral axis)
        val enemyRightX = cos(enemyYawRad)
        val enemyRightZ = -sin(enemyYawRad)
        val lateralDot = toPlayerX * enemyRightX + toPlayerZ * enemyRightZ
        val sideSign = if (lateralDot >= 0f) 1f else -1f

        when (state) {
            EnemyAiState.COMBO_SLASH_HORIZONTAL -> {
                // Horizontal sweep hits between 0.16s and 0.35s
                if (timer in 0.16f..0.35f && dist <= enemy.meleeStrikeMaxRange && forwardDot >= 0.20f) {
                    // Duck low under horizontal slash or step back
                    val dodgeDir = Vector3(-toPlayerX, 0f, -toPlayerZ).nor()
                    return Pair(HakiDodgeKind.LOW, dodgeDir)
                }
            }
            EnemyAiState.COMBO_SLASH_DIAGONAL,
            EnemyAiState.COMBO_RISING_SLASH -> {
                // Diagonal / rising vertical cleaves hit between 0.14s and 0.36s
                if (timer in 0.14f..0.36f && dist <= enemy.meleeStrikeMaxRange && forwardDot >= 0.20f) {
                    // Lateral side slip perpendicular to attack
                    val dodgeDir = Vector3(enemyRightX * sideSign, 0f, enemyRightZ * sideSign).nor()
                    return Pair(if (sideSign > 0) HakiDodgeKind.SIDE_RIGHT else HakiDodgeKind.SIDE_LEFT, dodgeDir)
                }
            }
            EnemyAiState.COMBO_OVERHEAD_SLAM -> {
                // Overhead slam hits at 0.24s..0.46s
                if (timer in 0.24f..0.46f && dist <= enemy.meleeStrikeMaxRange && forwardDot >= 0.20f) {
                    // Lateral side slip
                    val dodgeDir = Vector3(enemyRightX * sideSign, 0f, enemyRightZ * sideSign).nor()
                    return Pair(if (sideSign > 0) HakiDodgeKind.SIDE_RIGHT else HakiDodgeKind.SIDE_LEFT, dodgeDir)
                }
            }
            EnemyAiState.COMBO_FINISHER_THRUST -> {
                // Linear thrust hits at 0.20s..0.42s
                if (timer in 0.20f..0.42f && dist <= 3.2f && forwardDot >= 0.35f) {
                    // Side slip away from linear thrust vector
                    val dodgeDir = Vector3(enemyRightX * sideSign, 0f, enemyRightZ * sideSign).nor()
                    return Pair(if (sideSign > 0) HakiDodgeKind.SIDE_RIGHT else HakiDodgeKind.SIDE_LEFT, dodgeDir)
                }
            }
            EnemyAiState.SPECIAL_WHIRLWIND -> {
                // 360-degree whirlwind hits at 0.16s..0.32s and 0.40s..0.58s
                if ((timer in 0.16f..0.32f || timer in 0.40f..0.58f) && dist <= 2.85f) {
                    // Backward leap out of spin radius
                    val dodgeDir = Vector3(toPlayerX, 0f, toPlayerZ).nor()
                    return Pair(HakiDodgeKind.BACKWARD, dodgeDir)
                }
            }
            EnemyAiState.SPECIAL_PHANTOM_CHARGE -> {
                // High-speed charge hits at 0.32s..0.62s
                if (timer in 0.32f..0.62f && dist <= 3.5f && forwardDot >= 0.30f) {
                    // Side dodge out of charge path
                    val dodgeDir = Vector3(enemyRightX * sideSign, 0f, enemyRightZ * sideSign).nor()
                    return Pair(if (sideSign > 0) HakiDodgeKind.SIDE_RIGHT else HakiDodgeKind.SIDE_LEFT, dodgeDir)
                }
            }
            EnemyAiState.SPECIAL_AERIAL_EXECUTION -> {
                // Aerial plunge slam hits at 0.44s..0.54s
                if (timer in 0.44f..0.54f && dist <= 2.8f) {
                    val dodgeDir = Vector3(toPlayerX, 0f, toPlayerZ).nor()
                    return Pair(HakiDodgeKind.BACKWARD, dodgeDir)
                }
            }
            EnemyAiState.SPECIAL_COUNTER_RIPOSTE -> {
                if (timer in 0.16f..0.36f && dist <= enemy.meleeStrikeMaxRange && forwardDot >= 0.20f) {
                    val dodgeDir = Vector3(toPlayerX, 0f, toPlayerZ).nor()
                    return Pair(HakiDodgeKind.BACKWARD, dodgeDir)
                }
            }
            EnemyAiState.ATTACK_STRIKE -> {
                if (timer in 0.08f..0.30f && dist <= enemy.meleeStrikeMaxRange && forwardDot >= 0.20f) {
                    val dodgeDir = Vector3(enemyRightX * sideSign, 0f, enemyRightZ * sideSign).nor()
                    return Pair(if (sideSign > 0) HakiDodgeKind.SIDE_RIGHT else HakiDodgeKind.SIDE_LEFT, dodgeDir)
                }
            }
            else -> {}
        }
        return null
    }

    private fun detectImminentCrescentWave(wave: com.aethelgard.survival.core.entities.ActiveEnemyCrescentWave): Pair<HakiDodgeKind, Vector3>? {
        return evaluateLinearProjectile(
            projPos = wave.position,
            projVel = wave.velocity,
            hitRadius = 0.65f,
            maxPredictionTime = 0.24f
        )
    }

    private fun detectImminentSlimeOrb(orb: com.aethelgard.survival.core.entities.ActiveGreenEnergyOrb): Pair<HakiDodgeKind, Vector3>? {
        return evaluateLinearProjectile(
            projPos = orb.position,
            projVel = orb.velocity,
            hitRadius = orb.radius,
            maxPredictionTime = 0.26f
        )
    }

    private fun detectImminentRedFlowerOrb(orb: com.aethelgard.survival.core.entities.ActiveRedFlowerSporeOrb): Pair<HakiDodgeKind, Vector3>? {
        return evaluateLinearProjectile(
            projPos = orb.position,
            projVel = orb.velocity,
            hitRadius = 0.42f,
            maxPredictionTime = 0.22f
        )
    }

    private fun detectImminentBlueFlowerOrb(orb: com.aethelgard.survival.core.entities.ActiveBlueFlowerSporeOrb): Pair<HakiDodgeKind, Vector3>? {
        return evaluateLinearProjectile(
            projPos = orb.position,
            projVel = orb.velocity,
            hitRadius = 0.45f,
            maxPredictionTime = 0.25f
        )
    }

    /**
     * Evaluates whether a projectile is genuinely going to collide with the player's current bounding cylinder
     * within the imminent time window [0.01s .. maxPredictionTime].
     * If imminent, calculates a perpendicular dodge vector to slip safely out of the projectile's trajectory.
     */
    private fun evaluateLinearProjectile(
        projPos: Vector3,
        projVel: Vector3,
        hitRadius: Float,
        maxPredictionTime: Float
    ): Pair<HakiDodgeKind, Vector3>? {
        val speed = projVel.len()
        if (speed < 0.5f) return null

        val dirX = projVel.x / speed
        val dirY = projVel.y / speed
        val dirZ = projVel.z / speed

        // Vector from projectile to player center (approximating torso at y + 0.85m)
        val targetY = player.position.y + 0.85f
        val toPlayerX = player.position.x - projPos.x
        val toPlayerY = targetY - projPos.y
        val toPlayerZ = player.position.z - projPos.z

        // Projection along velocity ray
        val projAlong = toPlayerX * dirX + toPlayerY * dirY + toPlayerZ * dirZ
        if (projAlong <= 0.05f) return null // Behind projectile

        val timeToImpact = projAlong / speed
        if (timeToImpact > maxPredictionTime) return null // Too far into the future

        // Closest approach distance
        val closestX = projPos.x + dirX * projAlong
        val closestY = projPos.y + dirY * projAlong
        val closestZ = projPos.z + dirZ * projAlong

        val distToClosestSq = (player.position.x - closestX) * (player.position.x - closestX) +
                (targetY - closestY) * (targetY - closestY) +
                (player.position.z - closestZ) * (player.position.z - closestZ)

        val totalRadius = hitRadius + player.collisionRadius + 0.15f
        if (distToClosestSq <= totalRadius * totalRadius) {
            // Truly imminent collision!
            // Calculate lateral perpendicular vector in XZ plane
            val perpX = -dirZ
            val perpZ = dirX

            // Pick side direction that moves away from the projectile ray
            val offsetToPlayerX = player.position.x - closestX
            val offsetToPlayerZ = player.position.z - closestZ
            val dotPerp = offsetToPlayerX * perpX + offsetToPlayerZ * perpZ
            val sideSign = if (dotPerp >= 0f) 1f else -1f

            val dodgeDir = Vector3(perpX * sideSign, 0f, perpZ * sideSign).nor()
            val dodgeKind = if (sideSign > 0) HakiDodgeKind.SIDE_RIGHT else HakiDodgeKind.SIDE_LEFT
            return Pair(dodgeKind, dodgeDir)
        }
        return null
    }

    /**
     * Executes a physical evasion using the existing player movement and animation architecture:
     * - Consumes exactly 1 charge (out of 8)
     * - Sets player.isDodging = true, dodgeTimer = 0.28s, invulnerabilityTimer = 0.35s
     * - Sets player.dodgeDirection to the chosen evasion vector
     * - Sets player.animationState to the directional dodge state
     * - Spawns distinctive blue perception particles & aura burst
     * - Plays high-harmonic anime evasion audio cue
     */
    fun triggerEvasion(kind: HakiDodgeKind, direction: Vector3) {
        if (remainingCharges <= 0) return

        // Consume exactly 1 charge
        remainingCharges -= 1

        // Physical dodge parameters
        player.isDodging = true
        player.dodgeTimer = DODGE_ACTION_DURATION
        player.invulnerabilityTimer = INVULNERABILITY_WINDOW
        evadeCooldownTimer = EVADE_COOLDOWN_WINDOW
        recentEvadeVfxTimer = 0.40f

        val len = direction.len()
        if (len > 0.001f) {
            player.dodgeDirection.set(direction.x / len, 0f, direction.z / len)
        } else {
            // Default backward
            val yawRad = Math.toRadians(player.facingYaw.toDouble()).toFloat()
            player.dodgeDirection.set(-sin(yawRad), 0f, -cos(yawRad))
        }

        // Apply physical evasion velocity
        player.velocity.x = player.dodgeDirection.x * PHYSICAL_EVADE_SPEED
        player.velocity.z = player.dodgeDirection.z * PHYSICAL_EVADE_SPEED

        // Set directional dodge animation state
        player.animationState = kind.animState

        // Update player facing toward evasion or maintain orientation
        if (kind == HakiDodgeKind.BACKWARD || kind == HakiDodgeKind.LOW) {
            // Keep facing threat while moving back/ducking
        } else {
            val targetYaw = Math.toDegrees(atan2(player.dodgeDirection.x.toDouble(), player.dodgeDirection.z.toDouble())).toFloat()
            player.facingYaw = targetYaw
        }

        // Distinctive blue visual particle burst and slash wave
        val burstOrigin = Vector3(player.position.x, player.position.y + 0.85f, player.position.z)
        combatSystem.spawnParticleBurst(
            origin = burstOrigin,
            count = 14,
            speed = 4.8f,
            variant = 0 // Cyan / Electric Blue
        )
        combatSystem.spawnSlashWave(
            pos = burstOrigin,
            yawDeg = player.facingYaw,
            tiltDeg = 0f,
            initialScale = 0.95f,
            expansionRate = 7.5f
        )

        // Sound effect
        audioManager.playHakiEvade()

        // If charges depleted, become exhausted
        if (remainingCharges <= 0) {
            isActive = false
            exhaustionCooldown = EXHAUSTION_COOLDOWN_DURATION
        }
    }
}
