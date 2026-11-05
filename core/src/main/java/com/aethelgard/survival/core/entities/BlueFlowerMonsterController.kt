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

enum class FlowerMonsterState(val displayName: String) {
    DORMANT("DORMANT"),
    EMERGING("EMERGING"),
    IDLE("IDLE"),
    MOVE("MOVE"),
    CHARGE("CHARGE"),
    ATTACK("ATTACK"),
    HIT("HIT"),
    DEATH("DEATH")
}

data class ActiveBlueFlowerSporeOrb(
    val position: Vector3,
    val velocity: Vector3,
    val damage: Float,
    val modelInstance: ModelInstance,
    var age: Float = 0f,
    val maxLifetime: Float = 3.2f,
    var hasHit: Boolean = false
)

/**
 * 3D Blue Flower Monster Enemy Controller:
 * - Dormant Appearance: Initially appears as an ordinary blue fantasy flower growing naturally in grassy areas.
 * - Awakening Behavior: When the player approaches within detection range, roots and stem rise from the ground
 *   and petals open to reveal its creature face (`EMERGING` -> active states).
 * - Combat Balance (vs Red Flower Monster):
 *   - Slower blue projectiles (`projectileSpeed = 8.2f` m/s)
 *   - Higher direct damage per projectile (`projectileDamage = 18.0f` HP, direct damage only, no poison/DoT)
 *   - Less frequent ranged attacks (`baseAttackCooldown = 2.85f`s, `chargeDuration = 0.78f`s)
 * - Guarantees a single MP Potion ("mp_potion") 3D ground pickup drop upon defeat without duplicating drops.
 */
class BlueFlowerMonsterController(
    entity: WorldEntity,
    spawnOrigin: Vector3,
    private val modelFactory: ProceduralModelFactory,
    private val groundPickupManager: GroundPickupManager? = null,
    maxHealth: Float = 110f
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

    val emergeDuration: Float = 0.55f

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

    // Distinct Blue Flower Monster combat balance: slower projectiles, higher damage, less frequent attacks
    val projectileSpeed: Float = 8.2f
    val projectileDamage: Float = 18.0f
    val baseAttackCooldown: Float = 2.85f
    val chargeDuration: Float = 0.78f
    override val detectionRadius: Float = 12.5f
    val shootingRange: Float = 9.0f
    val moveSpeed: Float = 2.15f

    var attackCooldown: Float = 1.2f
    private var chargeShotFired: Boolean = false
    var hasFiredAnyOrb: Boolean = false
        private set
    var hasDroppedMpPotion: Boolean = false
        private set

    val activeOrbs = mutableListOf<ActiveBlueFlowerSporeOrb>()
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
            orbModel = modelFactory.createBlueFlowerSporeOrbModel()
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
        attackCooldown = 1.2f
        chargeShotFired = false
        hasDroppedMpPotion = false
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

            // Guaranteed single MP Potion drop upon defeat
            if (!hasDroppedMpPotion) {
                hasDroppedMpPotion = true
                val dropLocation = Vector3(entity.position.x, entity.position.y, entity.position.z)
                groundPickupManager?.spawnMpPotion(dropLocation)
            }
            updateFlowerTransform()
            return true
        } else {
            aiState = EnemyAiState.HIT_STAGGER
            flowerState = FlowerMonsterState.HIT
            stateTimer = if (isHeavyImpact) 0.36f else 0.22f
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

        // Handle DORMANT and EMERGING lifecycle before standard active combat states
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
                facingYaw = lerpYaw(facingYaw, desiredYaw, delta * 8f)

                val stretch = sin(emergeProgress * Math.PI.toFloat()) * 0.14f
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

                val wobble = sin(animTime * 34f) * 0.14f
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

                val breath = sin(animTime * 2.8f) * 0.05f
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
                    facingYaw = lerpYaw(facingYaw, desiredYaw, delta * 9f)

                    if (distToPlayer <= shootingRange && attackCooldown <= 0f) {
                        aiState = EnemyAiState.ATTACK_WINDUP
                        flowerState = FlowerMonsterState.CHARGE
                        stateTimer = 0f
                        chargeShotFired = false
                        activeSkillName = "MANA SPORE CHARGE"
                        velocity.setZero()
                    } else if (distToPlayer > 3.2f) {
                        flowerState = FlowerMonsterState.MOVE
                        val nx = dxToPlayer / distToPlayer
                        val nz = dzToPlayer / distToPlayer
                        velocity.x = MathUtils.lerp(velocity.x, nx * moveSpeed, delta * 7f)
                        velocity.z = MathUtils.lerp(velocity.z, nz * moveSpeed, delta * 7f)
                    } else {
                        flowerState = FlowerMonsterState.IDLE
                        velocity.x = MathUtils.lerp(velocity.x, 0f, delta * 7f)
                        velocity.z = MathUtils.lerp(velocity.z, 0f, delta * 7f)
                    }

                    val sway = sin(animTime * 6.5f) * 0.06f
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
                facingYaw = lerpYaw(facingYaw, desiredYaw, delta * 10f)

                val pulse = sin(stateTimer * 24f) * 0.08f
                scaleX = 1.15f + pulse
                scaleY = 0.88f - pulse * 0.5f
                scaleZ = 1.15f + pulse

                if (stateTimer >= chargeDuration) {
                    aiState = EnemyAiState.ATTACK_STRIKE
                    flowerState = FlowerMonsterState.ATTACK
                    stateTimer = 0f
                    chargeShotFired = false
                    activeSkillName = "AZURE SPORE ORB"
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

                scaleX = MathUtils.lerp(0.88f, 1.0f, (stateTimer / 0.35f).coerceIn(0f, 1f))
                scaleY = MathUtils.lerp(1.18f, 1.0f, (stateTimer / 0.35f).coerceIn(0f, 1f))
                scaleZ = MathUtils.lerp(0.88f, 1.0f, (stateTimer / 0.35f).coerceIn(0f, 1f))

                if (stateTimer >= 0.35f) {
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
            ActiveBlueFlowerSporeOrb(
                position = spawnPos,
                velocity = vel,
                damage = projectileDamage,
                modelInstance = instance
            )
        )

        audioManager.playMeleeSwing(isSword = false, isHeavy = true)
        combatSystem?.spawnParticleBurst(spawnPos, count = 8, speed = 3.5f, variant = 0)
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

            val spinDeg = (orb.age * 300f) % 360f
            val pulse = 1.0f + sin(orb.age * 20f) * 0.12f
            orb.modelInstance.transform.idt()
                .translate(orb.position.x, orb.position.y, orb.position.z)
                .rotate(0f, 1f, 0f, spinDeg)
                .scale(pulse, pulse, pulse)

            val groundY = worldManager.getTerrainHeight(orb.position.x, orb.position.z)
            if (orb.position.y <= groundY + 0.12f) {
                orb.hasHit = true
                combatSystem?.spawnParticleBurst(orb.position, count = 8, speed = 3.6f, variant = 0)
                iter.remove()
                continue
            }

            val pdx = player.position.x - orb.position.x
            val pdy = (player.position.y + 0.90f) - orb.position.y
            val pdz = player.position.z - orb.position.z
            val hitRadius = player.collisionRadius + 0.42f
            if (pdx * pdx + pdy * pdy + pdz * pdz <= hitRadius * hitRadius) {
                // If player is dashing/dodging, projectile is cleanly dodged
                if (player.isDodging) {
                    continue
                }
                orb.hasHit = true
                // Direct damage only (no poison or status effects)
                val hit = player.applyDamage(orb.damage, orb.velocity.x * 0.35f, orb.velocity.z * 0.35f)
                if (hit) {
                    audioManager.playMeleeHit(isHeavy = false)
                    combatSystem?.spawnParticleBurst(orb.position, count = 12, speed = 4.5f, variant = 0)
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
            val swayAmp = if (openFactor < 0.2f) 1.5f else 7f
            val swayZ = sin(animTime * 3.2f) * swayAmp
            val swayX = cos(animTime * 2.6f) * (swayAmp * 0.7f)
            stem.rotation.setEulerAngles(0f, swayX, swayZ)
        }
        headNode?.let { head ->
            val nod = if (openFactor < 0.2f) -55f else sin(animTime * 4.0f) * 8f
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
