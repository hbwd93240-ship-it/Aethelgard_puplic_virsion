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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ActiveRedFlowerSporeOrb(
    val position: Vector3,
    val velocity: Vector3,
    val damage: Float,
    val modelInstance: ModelInstance,
    var age: Float = 0f,
    val maxLifetime: Float = 2.6f,
    var hasHit: Boolean = false
)

/**
 * 3D Red Flower Monster Enemy Controller:
 * - Dormant Appearance: Initially appears as an ordinary red fantasy flower growing naturally in grassy areas.
 * - Awakening Behavior: When the player approaches within detection range, roots and stem rise naturally
 *   from the ground and petals open to reveal its creature face (`EMERGING` -> active states).
 * - Combat Behavior & Balance (vs Blue Flower Monster):
 *   - Mobile enemy that chases and repositions while pursuing the player (`moveSpeed = 2.65f` m/s).
 *   - Faster red poisonous-looking projectiles (`projectileSpeed = 13.5f` m/s).
 *   - Lower direct damage per projectile (`projectileDamage = 9.0f` HP, direct damage only — NO poison, DoT, or status effects).
 *   - More frequent ranged attacks (`baseAttackCooldown = 1.45f`s, `chargeDuration = 0.48f`s).
 *   - Projectiles can be dodged via movement and Dash (`player.isDodging`).
 * - Guarantees a single Health Potion ("health_potion") 3D ground pickup drop upon defeat without duplicating drops.
 */
class RedFlowerMonsterController(
    entity: WorldEntity,
    spawnOrigin: Vector3,
    private val modelFactory: ProceduralModelFactory,
    private val groundPickupManager: GroundPickupManager? = null,
    maxHealth: Float = 95f
) : EnemyController(
    entity = entity,
    spawnOrigin = spawnOrigin,
    maxHealth = maxHealth
) {
    override var collisionRadius: Float = 0.56f

    var flowerState: FlowerMonsterState = FlowerMonsterState.DORMANT
        private set

    var hasAwakened: Boolean = false
        private set

    var emergeProgress: Float = 0f
        private set

    val emergeDuration: Float = 0.50f

    var animTime: Float = 0f
        private set
    override var stateTimer: Float = 0f
        protected set

    var scaleX: Float = 1.0f
        private set
    var scaleY: Float = 1.0f
        private set
    var scaleZ: Float = 1.0f
        private set

    // Distinct Red Flower Monster combat balance: faster projectiles, lower damage, more frequent attacks
    val projectileSpeed: Float = 13.5f
    val projectileDamage: Float = 9.0f
    val baseAttackCooldown: Float = 1.45f
    val chargeDuration: Float = 0.48f
    override val detectionRadius: Float = 12.5f
    val shootingRange: Float = 9.5f
    val moveSpeed: Float = 2.65f

    var attackCooldown: Float = 0.9f
    private var chargeShotFired: Boolean = false
    var hasFiredAnyOrb: Boolean = false
        private set
    var hasDroppedHealthPotion: Boolean = false
        private set

    val activeOrbs = mutableListOf<ActiveRedFlowerSporeOrb>()
    private var orbModel: Model? = null

    private val rootNode: Node? = entity.modelInstance.getNode("flowerRoot", true)
    private val stemNode: Node? = entity.modelInstance.getNode("flowerStem", true)
    private val headNode: Node? = entity.modelInstance.getNode("flowerHead", true)
    private val petalsNode: Node? = entity.modelInstance.getNode("flowerPetals", true)
    private val faceNode: Node? = entity.modelInstance.getNode("flowerFace", true)

    init {
        updateFlowerTransform()
    }

    private fun getOrLoadOrbModel(): Model {
        if (orbModel == null) {
            orbModel = modelFactory.createRedFlowerSporeOrbModel()
        }
        return orbModel!!
    }

    override fun resetToSpawn(worldManager: WorldManager) {
        health = maxHealth
        aiState = EnemyAiState.PATROL
        flowerState = FlowerMonsterState.DORMANT
        hasAwakened = false
        emergeProgress = 0f
        stateTimer = 0f
        hitFlashTimer = 0f
        respawnCountdown = 0f
        isFuryPhase = false
        activeSkillName = ""
        attackCooldown = 0.9f
        chargeShotFired = false
        hasDroppedHealthPotion = false
        scaleX = 1f
        scaleY = 1f
        scaleZ = 1f
        velocity.setZero()
        activeOrbs.clear()
        val y = worldManager.getWalkableSurfaceHeight(spawnOrigin.x, spawnOrigin.z)
        entity.position.set(spawnOrigin.x, y, spawnOrigin.z)
        facingYaw = 0f
        entity.isActive = true
        updateFlowerTransform()
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

        // Immediate awakening if struck while dormant or emerging
        hasAwakened = true
        emergeProgress = 1f

        health = (health - amount).coerceAtLeast(0f)
        hitFlashTimer = if (isHeavyImpact) 0.28f else 0.18f

        val maxKb = if (isHeavyImpact) 7.5f else 4.5f
        val kbMag = sqrt(knockbackX * knockbackX + knockbackZ * knockbackZ)
        if (kbMag > 0.001f) {
            val scale = (kbMag.coerceAtMost(maxKb)) / kbMag
            velocity.x = knockbackX * scale
            velocity.z = knockbackZ * scale
        }

        if (health <= 0f) {
            aiState = EnemyAiState.DEAD
            flowerState = FlowerMonsterState.DEATH
            stateTimer = 0f
            respawnCountdown = 8.5f
            activeSkillName = ""
            audioManager.playEnemyDefeated()

            // Guaranteed single Health Potion drop upon defeat
            if (!hasDroppedHealthPotion) {
                hasDroppedHealthPotion = true
                val dropLocation = Vector3(entity.position.x, entity.position.y, entity.position.z)
                groundPickupManager?.spawnHealthPotion(dropLocation)
            }
            updateFlowerTransform()
            return true
        } else {
            aiState = EnemyAiState.HIT_STAGGER
            flowerState = FlowerMonsterState.HIT
            stateTimer = if (isHeavyImpact) 0.34f else 0.20f
            activeSkillName = ""
            updateFlowerTransform()
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

        if (hitFlashTimer > 0f) {
            hitFlashTimer = (hitFlashTimer - delta).coerceAtLeast(0f)
        }
        if (attackCooldown > 0f && hasAwakened) {
            attackCooldown = (attackCooldown - delta).coerceAtLeast(0f)
        }

        updateActiveOrbs(delta, player, worldManager, audioManager, combatSystem, onPlayerHit)

        val dxToPlayer = player.position.x - entity.position.x
        val dzToPlayer = player.position.z - entity.position.z
        val distToPlayer = sqrt(dxToPlayer * dxToPlayer + dzToPlayer * dzToPlayer).coerceAtLeast(0.001f)

        // Handle DORMANT and EMERGING lifecycle before active combat states
        if (!hasAwakened && aiState != EnemyAiState.DEAD && aiState != EnemyAiState.HIT_STAGGER) {
            if (flowerState == FlowerMonsterState.DORMANT) {
                aiState = EnemyAiState.PATROL
                velocity.setZero()
                emergeProgress = 0f
                scaleX = 1.0f
                scaleY = 1.0f
                scaleZ = 1.0f

                if (distToPlayer <= detectionRadius) {
                    flowerState = FlowerMonsterState.EMERGING
                    stateTimer = delta
                    emergeProgress = (stateTimer / emergeDuration).coerceIn(0f, 1f)
                    activeSkillName = "AWAKENING"
                    audioManager.playMeleeSwing(isSword = false, isHeavy = false)
                }
                val groundY = worldManager.getWalkableSurfaceHeight(entity.position.x, entity.position.z)
                entity.position.y = groundY
                updateFlowerTransform()
                return
            } else if (flowerState == FlowerMonsterState.EMERGING) {
                stateTimer += delta
                emergeProgress = (stateTimer / emergeDuration).coerceIn(0f, 1f)
                val desiredYaw = Math.toDegrees(atan2(dxToPlayer.toDouble(), dzToPlayer.toDouble())).toFloat()
                facingYaw = lerpYaw(facingYaw, desiredYaw, delta * 9f)

                val stretch = sin(emergeProgress * Math.PI.toFloat()) * 0.16f
                scaleX = 1.0f - stretch * 0.4f
                scaleY = 1.0f + stretch
                scaleZ = 1.0f - stretch * 0.4f

                if (emergeProgress >= 1f) {
                    hasAwakened = true
                    flowerState = FlowerMonsterState.IDLE
                    aiState = EnemyAiState.PURSUE
                    stateTimer = 0f
                    activeSkillName = ""
                }
                val groundY = worldManager.getWalkableSurfaceHeight(entity.position.x, entity.position.z)
                entity.position.y = groundY
                updateFlowerTransform()
                return
            }
        }

        when (aiState) {
            EnemyAiState.DEAD -> {
                flowerState = FlowerMonsterState.DEATH
                stateTimer += delta
                respawnCountdown = (8.5f - stateTimer).coerceAtLeast(0f)
                velocity.x = MathUtils.lerp(velocity.x, 0f, (delta * 10f).coerceAtMost(1f))
                velocity.z = MathUtils.lerp(velocity.z, 0f, (delta * 10f).coerceAtMost(1f))

                val collapseProgress = (stateTimer / 0.55f).coerceIn(0f, 1f)
                scaleX = MathUtils.lerp(1.0f, 1.35f, collapseProgress)
                scaleY = MathUtils.lerp(1.0f, 0.08f, collapseProgress)
                scaleZ = MathUtils.lerp(1.0f, 1.35f, collapseProgress)

                if (stateTimer >= 8.5f) {
                    resetToSpawn(worldManager)
                    return
                }
            }

            EnemyAiState.HIT_STAGGER -> {
                flowerState = FlowerMonsterState.HIT
                stateTimer -= delta
                velocity.x = MathUtils.lerp(velocity.x, 0f, (delta * 9f).coerceAtMost(1f))
                velocity.z = MathUtils.lerp(velocity.z, 0f, (delta * 9f).coerceAtMost(1f))

                val wobble = sin(animTime * 36f) * 0.14f
                scaleX = 1.12f + wobble
                scaleY = 0.84f - wobble * 0.5f
                scaleZ = 1.12f + wobble

                if (stateTimer <= 0f) {
                    aiState = EnemyAiState.PURSUE
                    flowerState = FlowerMonsterState.IDLE
                    stateTimer = 0f
                }
            }

            EnemyAiState.PATROL -> {
                flowerState = FlowerMonsterState.IDLE
                velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 6f)
                velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 6f)

                val breath = sin(animTime * 3.1f) * 0.05f
                scaleX = 1.0f - breath * 0.4f
                scaleY = 1.0f + breath
                scaleZ = 1.0f - breath * 0.4f

                if (distToPlayer <= detectionRadius) {
                    aiState = EnemyAiState.PURSUE
                    flowerState = FlowerMonsterState.MOVE
                    stateTimer = 0f
                }
            }

            EnemyAiState.PURSUE, EnemyAiState.TACTICAL_STANCE -> {
                if (distToPlayer > detectionRadius * 1.45f) {
                    aiState = EnemyAiState.PATROL
                    flowerState = FlowerMonsterState.IDLE
                    stateTimer = 0f
                } else {
                    val desiredYaw = Math.toDegrees(atan2(dxToPlayer.toDouble(), dzToPlayer.toDouble())).toFloat()
                    facingYaw = lerpYaw(facingYaw, desiredYaw, delta * 10f)

                    if (distToPlayer <= shootingRange && attackCooldown <= 0f) {
                        aiState = EnemyAiState.ATTACK_WINDUP
                        flowerState = FlowerMonsterState.CHARGE
                        stateTimer = 0f
                        chargeShotFired = false
                        activeSkillName = "CRIMSON SPORE CHARGE"
                        velocity.setZero()
                    } else if (distToPlayer > 2.8f) {
                        flowerState = FlowerMonsterState.MOVE
                        val nx = dxToPlayer / distToPlayer
                        val nz = dzToPlayer / distToPlayer
                        // Subtle repositioning weave while chasing player
                        val strafe = sin(animTime * 3.4f) * 0.32f
                        val targetVx = (nx - nz * strafe) * moveSpeed
                        val targetVz = (nz + nx * strafe) * moveSpeed
                        velocity.x = MathUtils.lerp(velocity.x, targetVx, delta * 8f)
                        velocity.z = MathUtils.lerp(velocity.z, targetVz, delta * 8f)
                    } else {
                        flowerState = FlowerMonsterState.IDLE
                        velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 7f)
                        velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 7f)
                    }

                    val sway = sin(animTime * 7.2f) * 0.06f
                    scaleX = 1.0f + sway
                    scaleY = 1.0f - sway * 0.5f
                    scaleZ = 1.0f + sway
                }
            }

            EnemyAiState.ATTACK_WINDUP -> {
                flowerState = FlowerMonsterState.CHARGE
                stateTimer += delta
                velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 10f)
                velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 10f)

                val desiredYaw = Math.toDegrees(atan2(dxToPlayer.toDouble(), dzToPlayer.toDouble())).toFloat()
                facingYaw = lerpYaw(facingYaw, desiredYaw, delta * 11f)

                val pulse = sin(stateTimer * 28f) * 0.09f
                scaleX = 1.16f + pulse
                scaleY = 0.86f - pulse * 0.5f
                scaleZ = 1.16f + pulse

                if (stateTimer >= chargeDuration) {
                    aiState = EnemyAiState.ATTACK_STRIKE
                    flowerState = FlowerMonsterState.ATTACK
                    stateTimer = 0f
                    chargeShotFired = false
                    activeSkillName = "CRIMSON SPORE DART"
                }
            }

            EnemyAiState.ATTACK_STRIKE -> {
                flowerState = FlowerMonsterState.ATTACK
                stateTimer += delta
                velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 10f)
                velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 10f)

                if (!chargeShotFired) {
                    chargeShotFired = true
                    hasFiredAnyOrb = true
                    fireSporeOrbAtPlayer(player, audioManager, combatSystem)
                }

                scaleX = MathUtils.lerp(0.86f, 1.0f, (stateTimer / 0.30f).coerceIn(0f, 1f))
                scaleY = MathUtils.lerp(1.20f, 1.0f, (stateTimer / 0.30f).coerceIn(0f, 1f))
                scaleZ = MathUtils.lerp(0.86f, 1.0f, (stateTimer / 0.30f).coerceIn(0f, 1f))

                if (stateTimer >= 0.30f) {
                    aiState = EnemyAiState.PURSUE
                    flowerState = FlowerMonsterState.IDLE
                    stateTimer = 0f
                    activeSkillName = ""
                    attackCooldown = baseAttackCooldown
                }
            }

            else -> {
                aiState = EnemyAiState.PURSUE
                flowerState = FlowerMonsterState.IDLE
                stateTimer = 0f
            }
        }

        // Integrate horizontal position & resolve collisions
        entity.position.x += velocity.x * delta
        entity.position.z += velocity.z * delta
        worldManager.resolveObstacleCollisions(entity.position, collisionRadius)

        // Player-Enemy physical separation
        if (aiState != EnemyAiState.DEAD) {
            val dx = entity.position.x - player.position.x
            val dz = entity.position.z - player.position.z
            val minSep = collisionRadius + player.collisionRadius + 0.10f
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

        val groundY = worldManager.getWalkableSurfaceHeight(entity.position.x, entity.position.z)
        entity.position.y = groundY

        updateFlowerTransform()
    }

    private fun fireSporeOrbAtPlayer(
        player: PlayerCharacter,
        audioManager: AudioManager,
        combatSystem: CombatSystem?
    ) {
        val rad = Math.toRadians(facingYaw.toDouble()).toFloat()
        val fwdX = sin(rad)
        val fwdZ = cos(rad)

        val spawnPos = Vector3(
            entity.position.x + fwdX * 0.50f,
            entity.position.y + 0.92f,
            entity.position.z + fwdZ * 0.50f
        )

        val targetY = player.position.y + 0.90f
        val dx = player.position.x - spawnPos.x
        val dy = targetY - spawnPos.y
        val dz = player.position.z - spawnPos.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001f)

        val vel = Vector3(
            (dx / dist) * projectileSpeed,
            (dy / dist) * projectileSpeed,
            (dz / dist) * projectileSpeed
        )

        val instance = ModelInstance(getOrLoadOrbModel())
        instance.transform.setToTranslation(spawnPos)

        activeOrbs.add(
            ActiveRedFlowerSporeOrb(
                position = spawnPos,
                velocity = vel,
                damage = projectileDamage,
                modelInstance = instance
            )
        )

        audioManager.playMeleeSwing(isSword = false, isHeavy = false)
        combatSystem?.spawnParticleBurst(spawnPos, count = 8, speed = 4.2f, variant = 2)
    }

    private fun updateActiveOrbs(
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
            if (orb.age >= orb.maxLifetime || orb.hasHit) {
                iter.remove()
                continue
            }

            orb.position.x += orb.velocity.x * delta
            orb.position.y += orb.velocity.y * delta
            orb.position.z += orb.velocity.z * delta

            val spinDeg = (orb.age * 360f) % 360f
            val pulse = 1.0f + sin(orb.age * 24f) * 0.12f
            orb.modelInstance.transform.idt()
                .translate(orb.position.x, orb.position.y, orb.position.z)
                .rotate(0f, 1f, 0f, spinDeg)
                .scale(pulse, pulse, pulse)

            val groundY = worldManager.getTerrainHeight(orb.position.x, orb.position.z)
            if (orb.position.y <= groundY + 0.12f) {
                orb.hasHit = true
                combatSystem?.spawnParticleBurst(orb.position, count = 8, speed = 3.8f, variant = 2)
                iter.remove()
                continue
            }

            val pdx = player.position.x - orb.position.x
            val pdy = (player.position.y + 0.90f) - orb.position.y
            val pdz = player.position.z - orb.position.z
            val hitRadius = player.collisionRadius + 0.40f
            if (pdx * pdx + pdy * pdy + pdz * pdz <= hitRadius * hitRadius) {
                // Dodgeable via Dash / movement
                if (player.isDodging) {
                    continue
                }
                orb.hasHit = true
                // Direct damage only — no poison, damage-over-time, or status effects
                val hit = player.applyDamage(orb.damage, orb.velocity.x * 0.28f, orb.velocity.z * 0.28f)
                if (hit) {
                    audioManager.playMeleeHit(isHeavy = false)
                    combatSystem?.spawnParticleBurst(orb.position, count = 12, speed = 4.6f, variant = 2)
                    onPlayerHit(orb.damage)
                }
                iter.remove()
            }
        }
    }

    private fun updateFlowerTransform() {
        val openFactor = if (hasAwakened) 1.0f else emergeProgress.coerceIn(0f, 1f)
        val stemBaseY = MathUtils.lerp(-0.32f, 0.24f, openFactor)

        stemNode?.let { stem ->
            stem.translation.set(0f, stemBaseY, 0f)
            val swayAmp = if (openFactor < 0.2f) 1.5f else 7.5f
            val swayZ = sin(animTime * 3.5f) * swayAmp
            val swayX = cos(animTime * 2.8f) * (swayAmp * 0.7f)
            stem.rotation.setEulerAngles(0f, swayX, swayZ)
        }
        headNode?.let { head ->
            val nod = if (openFactor < 0.2f) -55f else sin(animTime * 4.4f) * 8f
            head.rotation.setEulerAngles(0f, nod, 0f)
        }
        petalsNode?.let { petals ->
            val pScaleXY = MathUtils.lerp(0.62f, 1.0f, openFactor)
            val pScaleZ = MathUtils.lerp(1.45f, 1.0f, openFactor)
            petals.scale.set(pScaleXY, pScaleXY, pScaleZ)
        }
        faceNode?.let { face ->
            val fScale = MathUtils.lerp(0.001f, 1.0f, openFactor)
            face.scale.set(fScale, fScale, fScale)
        }
        rootNode?.translation?.set(0f, 0.05f, 0f)
        entity.modelInstance.calculateTransforms()

        entity.modelInstance.transform.idt()
            .translate(entity.position.x, entity.position.y, entity.position.z)
            .rotate(0f, 1f, 0f, facingYaw)
            .scale(scaleX, scaleY, scaleZ)
    }

    fun renderOrbs(modelBatch: ModelBatch, environment: Environment) {
        for (orb in activeOrbs) {
            if (!orb.hasHit) {
                modelBatch.render(orb.modelInstance, environment)
            }
        }
    }

    private fun lerpYaw(fromDeg: Float, toDeg: Float, alpha: Float): Float {
        var diff = (toDeg - fromDeg) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        return fromDeg + diff * alpha.coerceIn(0f, 1f)
    }
}
