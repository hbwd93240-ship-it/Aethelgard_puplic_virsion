package com.aethelgard.survival.core.entities

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.combat.CombatSystem
import com.aethelgard.survival.core.player.PlayerCharacter
import com.aethelgard.survival.core.world.ProceduralModelFactory
import com.aethelgard.survival.core.world.WorldManager
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents an active flying Green Energy Orb projectile launched by a Green Slime.
 * Features real 3D trajectory, ground & obstacle collision, player damage, green comet trails,
 * and magical impact explosions.
 */
class ActiveGreenEnergyOrb(
    val position: Vector3,
    val velocity: Vector3,
    val damage: Float = 14f,
    val radius: Float = 0.34f,
    val modelInstance: ModelInstance,
    var age: Float = 0f,
    val maxLifetime: Float = 3.6f,
    var isAlive: Boolean = true,
    var trailTimer: Float = 0f
)

/**
 * Controller for the GREEN SLIME enemy in Aethelgard Survival.
 *
 * Implements:
 * - Gelatinous 3D squash, stretch, wobble, and breathing animations
 * - Discrete bouncing locomotion toward the player with parabolic leaps and squash landings
 * - Signature Green Energy Orb charging, particle gathering, and projectile launching
 * - Terrain height and obstacle collision integration
 * - Poise, directional hit reactions, and dissolution death animation
 * - 100% guaranteed single Green Crystal drop at death location (strictly guarded against duplicate drops)
 */
class GreenSlimeController(
    entity: WorldEntity,
    spawnOrigin: Vector3,
    maxHealth: Float = 95f,
    private val modelFactory: ProceduralModelFactory,
    val onDropCrystal: (Vector3) -> Unit
) : EnemyController(
    entity = entity,
    spawnOrigin = spawnOrigin,
    maxHealth = maxHealth
) {
    override var collisionRadius: Float = 0.58f

    // AI & Combat Parameters
    override val detectionRadius = 15.0f
    private val minAttackRange = 3.0f
    private val maxAttackRange = 12.0f
    private val bounceCycleDuration = 0.95f
    private val leapForwardSpeed = 4.2f

    // Timers & States
    private var stateTime: Float = 0f
    private var animTime: Float = 0f
    private var bounceTimer: Float = 0f
    var attackCooldown: Float = 2.0f
    private var chargeDuration: Float = 1.05f
    private var chargeGatherTimer: Float = 0f
    private var patrolAngle: Float = (Math.random() * Math.PI * 2).toFloat()
    private var patrolPauseTimer: Float = 0.5f

    // Squash & Stretch Scale factors
    var scaleX: Float = 1.0f
        private set
    var scaleY: Float = 1.0f
        private set
    var scaleZ: Float = 1.0f
        private set
    var jumpOffsetY: Float = 0f
        private set

    // Internal Model Nodes
    private val bodyNode: Node? = entity.modelInstance.getNode("slimeBody", true)
    private val coreNode: Node? = entity.modelInstance.getNode("slimeCore", true)
    private val leftEyeNode: Node? = entity.modelInstance.getNode("slimeEyeLeft", true)
    private val rightEyeNode: Node? = entity.modelInstance.getNode("slimeEyeRight", true)

    // Charging & Flying Projectiles
    private var orbModel: Model? = null
    private var chargingOrbInstance: ModelInstance? = null
    val activeOrbs = mutableListOf<ActiveGreenEnergyOrb>()
    var hasFiredAnyOrb: Boolean = false
        private set

    // Guaranteed Drop Guard: exactly one crystal per killed slime
    var hasDroppedCrystal: Boolean = false
        private set

    init {
        activeSkillName = "BOUNCE"
    }

    private fun getOrLoadOrbModel(): Model {
        if (orbModel == null) {
            orbModel = modelFactory.createGreenEnergyOrbModel()
        }
        return orbModel!!
    }

    override fun resetToSpawn(worldManager: WorldManager) {
        health = maxHealth
        aiState = EnemyAiState.PATROL
        stateTime = 0f
        animTime = 0f
        bounceTimer = 0f
        attackCooldown = 2.0f
        hitFlashTimer = 0f
        respawnCountdown = 0f
        activeSkillName = "IDLE"
        hasDroppedCrystal = false
        jumpOffsetY = 0f
        scaleX = 1f
        scaleY = 1f
        scaleZ = 1f
        velocity.setZero()
        activeOrbs.clear()
        chargingOrbInstance = null

        val y = worldManager.getWalkableSurfaceHeight(spawnOrigin.x, spawnOrigin.z)
        entity.position.set(spawnOrigin.x, y, spawnOrigin.z)
        facingYaw = (Math.random() * 360f).toFloat()
        entity.isActive = true
        syncTransform()
    }

    override fun receiveDamage(
        amount: Float,
        knockbackX: Float,
        knockbackZ: Float,
        isHeavyImpact: Boolean,
        audioManager: AudioManager,
        slashYawTwistDeg: Float,
        slashPitchTiltDeg: Float,
        verticalJolt: Float
    ): Boolean {
        if (aiState == EnemyAiState.DEAD) return false

        health = (health - amount).coerceAtLeast(0f)
        hitFlashTimer = if (isHeavyImpact) 0.28f else 0.18f

        // Apply knockback impulse
        val kbMag = sqrt(knockbackX * knockbackX + knockbackZ * knockbackZ)
        val maxKb = if (isHeavyImpact) 10.0f else 6.5f
        if (kbMag > 0.01f) {
            val scale = (if (kbMag > maxKb) maxKb / kbMag else 1.0f)
            velocity.x = knockbackX * scale
            velocity.z = knockbackZ * scale
        }

        // Violent gelatinous wobble on hit
        scaleY = 0.55f
        scaleX = 1.35f
        scaleZ = 1.35f

        if (health <= 0f) {
            aiState = EnemyAiState.DEAD
            stateTime = 0f
            respawnCountdown = 25.0f
            activeSkillName = "DISSOLVED"
            velocity.setZero()
            chargingOrbInstance = null

            // Drop GUARANTEED Green Crystal exactly once
            if (!hasDroppedCrystal) {
                hasDroppedCrystal = true
                onDropCrystal(Vector3(entity.position.x, entity.position.y, entity.position.z))
            }

            audioManager.playEnemyDefeated()
            return true
        } else {
            aiState = EnemyAiState.HIT_STAGGER
            stateTime = 0f
            activeSkillName = "STAGGERED"
            chargingOrbInstance = null
        }
        return false
    }

    override fun update(
        delta: Float,
        player: PlayerCharacter,
        worldManager: WorldManager,
        audioManager: AudioManager,
        combatSystem: CombatSystem?,
        onPlayerHit: (Float) -> Unit
    ) {
        animTime += delta
        stateTime += delta

        // Tick hit flash
        if (hitFlashTimer > 0f) {
            hitFlashTimer = (hitFlashTimer - delta).coerceAtLeast(0f)
        }
        if (attackCooldown > 0f) {
            attackCooldown = (attackCooldown - delta).coerceAtLeast(0f)
        }

        // Vector to player
        val dxToPlayer = player.position.x - entity.position.x
        val dzToPlayer = player.position.z - entity.position.z
        val distToPlayer = sqrt(dxToPlayer * dxToPlayer + dzToPlayer * dzToPlayer).coerceAtLeast(0.001f)

        // Update active flying green energy orbs
        updateActiveProjectiles(delta, player, worldManager, audioManager, combatSystem, onPlayerHit)

        // State Machine
        when (aiState) {
            EnemyAiState.DEAD -> {
                // Death collapse & dissolution animation
                val p = (stateTime / 0.70f).coerceIn(0f, 1f)
                scaleY = MathUtils.lerp(0.9f, 0.05f, p)
                scaleX = MathUtils.lerp(1.0f, 1.45f, p)
                scaleZ = MathUtils.lerp(1.0f, 1.45f, p)
                jumpOffsetY = 0f
                velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 10f)
                velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 10f)

                // Emit green gelatin dissolution bursts
                if (stateTime in 0.05f..0.30f && Math.random() > 0.6) {
                    combatSystem?.spawnParticleBurst(
                        origin = Vector3(
                            entity.position.x + (Math.random().toFloat() - 0.5f) * 0.8f,
                            entity.position.y + 0.2f,
                            entity.position.z + (Math.random().toFloat() - 0.5f) * 0.8f
                        ),
                        count = 3,
                        speed = 3.2f,
                        variant = 0
                    )
                }

                if (stateTime >= 0.70f) {
                    entity.isActive = false
                }

                respawnCountdown = (25.0f - stateTime).coerceAtLeast(0f)
                if (stateTime >= 25.0f) {
                    resetToSpawn(worldManager)
                    return
                }
            }

            EnemyAiState.HIT_STAGGER -> {
                // Gelatinous jiggle recovery
                val p = (stateTime / 0.28f).coerceIn(0f, 1f)
                val jiggle = sin(stateTime * 32f) * (1f - p) * 0.35f
                scaleY = 1.0f - jiggle
                scaleX = 1.0f + jiggle
                scaleZ = 1.0f + jiggle
                jumpOffsetY = 0f

                velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)

                if (stateTime >= 0.28f) {
                    aiState = EnemyAiState.PURSUE
                    stateTime = 0f
                    activeSkillName = "BOUNCE"
                }
            }

            EnemyAiState.PATROL -> {
                activeSkillName = "IDLE"
                jumpOffsetY = 0f

                // If player is detected within detection radius, transition to PURSUE
                if (distToPlayer <= detectionRadius) {
                    aiState = EnemyAiState.PURSUE
                    stateTime = 0f
                    bounceTimer = 0f
                    activeSkillName = "BOUNCE"
                } else {
                    // Gentle breathing wobble when idling
                    patrolPauseTimer -= delta
                    if (patrolPauseTimer > 0f) {
                        val breath = sin(animTime * 3.2f) * 0.08f
                        scaleY = 1.0f + breath
                        scaleX = 1.0f - breath * 0.5f
                        scaleZ = 1.0f - breath * 0.5f
                        velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 6f)
                        velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 6f)
                    } else {
                        // Occasional soft hop around spawn origin
                        bounceTimer += delta
                        val p = (bounceTimer / 1.1f).coerceIn(0f, 1f)
                        if (p < 0.15f) {
                            // Prep squash
                            scaleY = 0.76f
                            scaleX = 1.18f
                            scaleZ = 1.18f
                        } else if (p < 0.70f) {
                            // Leap
                            val leapP = (p - 0.15f) / 0.55f
                            jumpOffsetY = sin(leapP * Math.PI.toFloat()) * 0.85f
                            scaleY = 1.25f - 0.15f * leapP
                            scaleX = 0.88f
                            scaleZ = 0.88f
                            val fwdX = sin(Math.toRadians(facingYaw.toDouble()).toFloat())
                            val fwdZ = cos(Math.toRadians(facingYaw.toDouble()).toFloat())
                            velocity.x = fwdX * 2.2f
                            velocity.z = fwdZ * 2.2f
                        } else if (p < 0.88f) {
                            // Landing squash
                            jumpOffsetY = 0f
                            scaleY = 0.68f
                            scaleX = 1.28f
                            scaleZ = 1.28f
                            velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 10f)
                            velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 10f)
                        } else {
                            scaleY = 1.0f
                            scaleX = 1.0f
                            scaleZ = 1.0f
                            velocity.setZero()
                        }

                        if (bounceTimer >= 1.1f) {
                            bounceTimer = 0f
                            patrolPauseTimer = 2.0f + (Math.random() * 2.0f).toFloat()
                            patrolAngle += 0.9f
                            facingYaw = (facingYaw + 45f) % 360f
                        }
                    }
                }
            }

            EnemyAiState.PURSUE -> {
                activeSkillName = "BOUNCE"

                if (distToPlayer > detectionRadius * 1.5f) {
                    aiState = EnemyAiState.PATROL
                    stateTime = 0f
                    return
                }

                // Check Special Attack – GREEN ENERGY ORB trigger condition
                if (attackCooldown <= 0f && distToPlayer in minAttackRange..maxAttackRange) {
                    initiateOrbCharge()
                    return
                }

                // Turn to face player
                val desiredYaw = Math.toDegrees(atan2(dxToPlayer.toDouble(), dzToPlayer.toDouble())).toFloat()
                facingYaw = lerpAngle(facingYaw, desiredYaw, delta * 8f)

                // Execute bouncing locomotion towards player
                bounceTimer += delta
                val p = (bounceTimer / bounceCycleDuration).coerceIn(0f, 1f)

                if (p < 0.15f) {
                    // PREPARATION: Slime crouches & squashes down
                    val wp = p / 0.15f
                    scaleY = MathUtils.lerp(1.0f, 0.70f, wp)
                    scaleX = MathUtils.lerp(1.0f, 1.25f, wp)
                    scaleZ = MathUtils.lerp(1.0f, 1.25f, wp)
                    jumpOffsetY = 0f
                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 8f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 8f)
                } else if (p < 0.68f) {
                    // JUMP & LEAP: Slime launches into the air toward the player
                    val leapP = (p - 0.15f) / 0.53f
                    jumpOffsetY = sin(leapP * Math.PI.toFloat()) * 1.25f

                    // Body stretches vertically while ascending, returns while descending
                    if (leapP < 0.5f) {
                        val stretchP = leapP / 0.5f
                        scaleY = MathUtils.lerp(0.70f, 1.35f, stretchP)
                        scaleX = MathUtils.lerp(1.25f, 0.82f, stretchP)
                        scaleZ = MathUtils.lerp(1.25f, 0.82f, stretchP)
                    } else {
                        val returnP = (leapP - 0.5f) / 0.5f
                        scaleY = MathUtils.lerp(1.35f, 1.05f, returnP)
                        scaleX = MathUtils.lerp(0.82f, 0.98f, returnP)
                        scaleZ = MathUtils.lerp(0.82f, 0.98f, returnP)
                    }

                    // Forward impulse toward player
                    val fwdX = dxToPlayer / distToPlayer
                    val fwdZ = dzToPlayer / distToPlayer
                    velocity.x = MathUtils.lerp(velocity.x, fwdX * leapForwardSpeed, delta * 10f)
                    velocity.z = MathUtils.lerp(velocity.z, fwdZ * leapForwardSpeed, delta * 10f)
                } else if (p < 0.86f) {
                    // LANDING: Body compresses and spreads slightly upon hitting ground
                    val landP = (p - 0.68f) / 0.18f
                    jumpOffsetY = 0f
                    scaleY = MathUtils.lerp(1.05f, 0.62f, landP)
                    scaleX = MathUtils.lerp(0.98f, 1.34f, landP)
                    scaleZ = MathUtils.lerp(0.98f, 1.34f, landP)

                    velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 12f)
                    velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 12f)

                    // Subtle squish impact on initial landing frame
                    if (p in 0.68f..0.72f && Math.random() > 0.5) {
                        combatSystem?.spawnParticleBurst(
                            origin = Vector3(entity.position.x, entity.position.y + 0.1f, entity.position.z),
                            count = 2,
                            speed = 1.5f,
                            variant = 0
                        )
                    }
                } else {
                    // REBOUND: Returns smoothly to normal shape
                    val rebP = (p - 0.86f) / 0.14f
                    jumpOffsetY = 0f
                    scaleY = MathUtils.lerp(0.62f, 1.0f, rebP)
                    scaleX = MathUtils.lerp(1.34f, 1.0f, rebP)
                    scaleZ = MathUtils.lerp(1.34f, 1.0f, rebP)
                    velocity.setZero()
                }

                if (bounceTimer >= bounceCycleDuration) {
                    bounceTimer = 0f
                }
            }

            EnemyAiState.ATTACK_WINDUP -> {
                // CHARGE: Stops moving, core glows intensely, particles gather, orb forms and grows
                activeSkillName = "CHARGING ORB"
                jumpOffsetY = 0f
                velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 10f)
                velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 10f)

                // Face player
                val desiredYaw = Math.toDegrees(atan2(dxToPlayer.toDouble(), dzToPlayer.toDouble())).toFloat()
                facingYaw = lerpAngle(facingYaw, desiredYaw, delta * 10f)

                // Micro vibration of gelatinous body
                val chargeP = (stateTime / chargeDuration).coerceIn(0f, 1f)
                val jitter = sin(animTime * 34f) * 0.05f * chargeP
                scaleY = 1.0f + jitter
                scaleX = 1.0f - jitter
                scaleZ = 1.0f - jitter

                // Rapid pulsation of inner green core
                coreNode?.scale?.set(
                    1.4f + sin(animTime * 24f) * 0.35f,
                    1.4f + sin(animTime * 24f) * 0.35f,
                    1.4f + sin(animTime * 24f) * 0.35f
                )

                // Green energy particles gather in front of body
                chargeGatherTimer += delta
                if (chargeGatherTimer >= 0.08f) {
                    chargeGatherTimer = 0f
                    val rad = Math.toRadians(facingYaw.toDouble()).toFloat()
                    val fwdX = sin(rad)
                    val fwdZ = cos(rad)
                    val orbPos = Vector3(
                        entity.position.x + fwdX * 0.65f,
                        entity.position.y + 0.42f,
                        entity.position.z + fwdZ * 0.65f
                    )
                    combatSystem?.spawnParticleBurst(
                        origin = Vector3(
                            orbPos.x + (Math.random().toFloat() - 0.5f) * 0.5f,
                            orbPos.y + (Math.random().toFloat() - 0.5f) * 0.4f,
                            orbPos.z + (Math.random().toFloat() - 0.5f) * 0.5f
                        ),
                        count = 2,
                        speed = 2.0f,
                        variant = 0
                    )
                }

                // Update charging orb model instance scaling in front of slime
                val rad = Math.toRadians(facingYaw.toDouble()).toFloat()
                val fwdX = sin(rad)
                val fwdZ = cos(rad)
                val orbScale = MathUtils.lerp(0.15f, 0.85f, chargeP)
                if (chargingOrbInstance == null) {
                    chargingOrbInstance = ModelInstance(getOrLoadOrbModel())
                }
                chargingOrbInstance?.transform?.idt()
                    ?.translate(entity.position.x + fwdX * 0.65f, entity.position.y + 0.42f, entity.position.z + fwdZ * 0.65f)
                    ?.rotate(0f, 1f, 0f, (animTime * 180f) % 360f)
                    ?.scale(orbScale, orbScale, orbScale)

                if (stateTime >= chargeDuration) {
                    launchGreenEnergyOrb(player, audioManager)
                }
            }

            EnemyAiState.ATTACK_STRIKE -> {
                // ATTACK RECOIL
                activeSkillName = "ORB RELEASE"
                val p = (stateTime / 0.35f).coerceIn(0f, 1f)
                scaleY = MathUtils.lerp(1.22f, 1.0f, p)
                scaleX = MathUtils.lerp(0.85f, 1.0f, p)
                scaleZ = MathUtils.lerp(0.85f, 1.0f, p)
                jumpOffsetY = 0f

                if (stateTime >= 0.35f) {
                    aiState = EnemyAiState.PURSUE
                    stateTime = 0f
                    bounceTimer = 0f
                    attackCooldown = 4.8f // Reasonable cooldown before next ranged shot
                    activeSkillName = "BOUNCE"
                }
            }

            else -> {
                // Fallback reset to pursue
                aiState = EnemyAiState.PURSUE
                stateTime = 0f
            }
        }

        // Horizontal velocity integration & obstacle collision resolution
        entity.position.x += velocity.x * delta
        entity.position.z += velocity.z * delta
        worldManager.resolveObstacleCollisions(entity.position, collisionRadius)

        // Physical separation with player so neither entity overlaps
        if (aiState != EnemyAiState.DEAD) {
            val dx = entity.position.x - player.position.x
            val dz = entity.position.z - player.position.z
            val minSep = collisionRadius + player.collisionRadius + 0.08f
            val dSq = dx * dx + dz * dz
            if (dSq < minSep * minSep && dSq > 0.0001f) {
                val d = sqrt(dSq)
                val push = minSep - d
                entity.position.x += (dx / d) * push * 0.80f
                entity.position.z += (dz / d) * push * 0.80f
                player.position.x -= (dx / d) * push * 0.20f
                player.position.z -= (dz / d) * push * 0.20f
            }
        }

        // Clamp to ground surface height + vertical leap offset
        val groundY = worldManager.getWalkableSurfaceHeight(entity.position.x, entity.position.z)
        entity.position.y = groundY + jumpOffsetY

        // Calculate and apply internal model hierarchy nodes (body wobble, eyes, pulsating core)
        updateArticulatedPose()
        syncTransform()
    }

    private fun initiateOrbCharge() {
        aiState = EnemyAiState.ATTACK_WINDUP
        stateTime = 0f
        chargeGatherTimer = 0f
        velocity.setZero()
        activeSkillName = "CHARGING ORB"
    }

    private fun launchGreenEnergyOrb(player: PlayerCharacter, audioManager: AudioManager) {
        aiState = EnemyAiState.ATTACK_STRIKE
        stateTime = 0f
        chargingOrbInstance = null
        hasFiredAnyOrb = true

        val rad = Math.toRadians(facingYaw.toDouble()).toFloat()
        val fwdX = sin(rad)
        val fwdZ = cos(rad)

        // Launch from front of slime toward player center
        val spawnPos = Vector3(
            entity.position.x + fwdX * 0.70f,
            entity.position.y + 0.45f,
            entity.position.z + fwdZ * 0.70f
        )
        val targetPos = Vector3(player.position.x, player.position.y + 0.85f, player.position.z)
        val dir = targetPos.sub(spawnPos).nor()
        val orbSpeed = 11.2f
        val orbVelocity = dir.scl(orbSpeed)

        val orbInstance = ModelInstance(getOrLoadOrbModel())
        activeOrbs.add(
            ActiveGreenEnergyOrb(
                position = spawnPos,
                velocity = orbVelocity,
                damage = 14f,
                radius = 0.35f,
                modelInstance = orbInstance
            )
        )

        audioManager.playMeleeSwing(isSword = false, isHeavy = true)
    }

    private fun updateActiveProjectiles(
        delta: Float,
        player: PlayerCharacter,
        worldManager: WorldManager,
        audioManager: AudioManager,
        combatSystem: CombatSystem?,
        onPlayerHit: (Float) -> Unit
    ) {
        val iter = activeOrbs.iterator()
        while (iter.hasNext()) {
            val orb = iter.next()
            orb.age += delta
            if (orb.age >= orb.maxLifetime) {
                iter.remove()
                continue
            }

            // Move position along velocity
            orb.position.mulAdd(orb.velocity, delta)

            // Update 3D model instance transform & rotation
            orb.modelInstance.transform.idt()
                .translate(orb.position.x, orb.position.y, orb.position.z)
                .rotate(0f, 1f, 0f, (orb.age * 280f) % 360f)
                .scale(0.85f, 0.85f, 0.85f)

            // Emit visible glowing green trail
            orb.trailTimer += delta
            if (orb.trailTimer >= 0.045f) {
                orb.trailTimer = 0f
                combatSystem?.spawnParticleBurst(
                    origin = Vector3(orb.position.x, orb.position.y, orb.position.z),
                    count = 2,
                    speed = 1.2f,
                    variant = 0 // Cyan/green sparkling trail
                )
            }

            // 1. Terrain Collision Check: cannot pass through ground
            val groundY = worldManager.getWalkableSurfaceHeight(orb.position.x, orb.position.z)
            if (orb.position.y <= groundY + 0.15f) {
                audioManager.playMeleeHit(isHeavy = true)
                combatSystem?.spawnParticleBurst(orb.position, count = 16, speed = 5.2f, variant = 0)
                iter.remove()
                continue
            }

            // 2. World Obstacle Collision Check: trees, boulders, buildings
            var hitObstacle = false
            for (collider in worldManager.generatedData.obstacleColliders) {
                val cdx = orb.position.x - collider.x
                val cdz = orb.position.z - collider.z
                val hitDist = collider.radius + orb.radius
                if (cdx * cdx + cdz * cdz <= hitDist * hitDist) {
                    hitObstacle = true
                    break
                }
            }
            if (hitObstacle) {
                audioManager.playMeleeHit(isHeavy = false)
                combatSystem?.spawnParticleBurst(orb.position, count = 14, speed = 4.8f, variant = 0)
                iter.remove()
                continue
            }

            // 3. Player Collision Check
            val pdx = player.position.x - orb.position.x
            val pdz = player.position.z - orb.position.z
            val pdy = abs((player.position.y + 0.85f) - orb.position.y)
            val playerDistSq = pdx * pdx + pdz * pdz
            val playerHitRadius = player.collisionRadius + orb.radius

            if (playerDistSq <= playerHitRadius * playerHitRadius && pdy <= 1.35f) {
                // If player is actively dashing / dodging, projectile misses cleanly
                if (player.isDodging) {
                    continue
                }
                val vLen = sqrt(orb.velocity.x * orb.velocity.x + orb.velocity.z * orb.velocity.z).coerceAtLeast(0.01f)
                val kx = (orb.velocity.x / vLen) * 6.5f
                val kz = (orb.velocity.z / vLen) * 6.5f

                val applied = player.applyDamage(orb.damage, kx, kz)
                if (applied) {
                    audioManager.playMeleeHit(isHeavy = true)
                    combatSystem?.spawnParticleBurst(orb.position, count = 22, speed = 6.5f, variant = 0)
                    onPlayerHit(orb.damage)
                }
                iter.remove()
            }
        }
    }

    private fun updateArticulatedPose() {
        // Internal core breathing and subtle bob
        val coreBob = sin(animTime * 3.8f) * 0.02f
        coreNode?.translation?.set(0f, 0.36f + coreBob, 0f)

        // Left and Right eyes follow body squash
        leftEyeNode?.translation?.set(-0.16f * scaleX, 0.42f * scaleY, 0.35f * scaleZ)
        rightEyeNode?.translation?.set(0.16f * scaleX, 0.42f * scaleY, 0.35f * scaleZ)

        entity.modelInstance.calculateTransforms()
    }

    private fun syncTransform() {
        val flashScale = if (hitFlashTimer > 0f) 1.08f else 1.0f
        entity.modelInstance.transform.idt()
            .translate(entity.position.x, entity.position.y, entity.position.z)
            .rotate(0f, 1f, 0f, facingYaw)
            .scale(scaleX * flashScale, scaleY * flashScale, scaleZ * flashScale)
    }

    /**
     * Renders any charging green energy orbs and active flying projectiles for this slime.
     */
    fun renderProjectiles(modelBatch: ModelBatch, environment: Environment) {
        chargingOrbInstance?.let {
            modelBatch.render(it, environment)
        }
        for (orb in activeOrbs) {
            modelBatch.render(orb.modelInstance, environment)
        }
    }

    private fun lerpAngle(from: Float, to: Float, alpha: Float): Float {
        var diff = (to - from) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        return from + diff * alpha.coerceIn(0f, 1f)
    }
}
