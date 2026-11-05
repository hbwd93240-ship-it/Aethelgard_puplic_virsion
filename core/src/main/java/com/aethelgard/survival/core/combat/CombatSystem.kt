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
import com.badlogic.gdx.math.Vector3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class ProjectileKind {
    ARCANE_MAGIC_BOLT,
    CHARGED_ENERGY_BLAST
}

class ActiveProjectile(
    val instance: ModelInstance,
    val kind: ProjectileKind,
    val position: Vector3,
    val velocity: Vector3,
    val damage: Float,
    val knockbackStrength: Float,
    val hitRadius: Float,
    val scale: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 2.8f,
    var isAlive: Boolean = true
)

class ActiveParticle3D(
    val instance: ModelInstance,
    val position: Vector3,
    val velocity: Vector3,
    val initialScale: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 0.45f
)

class ActiveSlashWave(
    val instance: ModelInstance,
    val position: Vector3,
    val yawDeg: Float,
    val tiltDeg: Float,
    val initialScale: Float,
    val expansionRate: Float,
    var age: Float = 0f,
    val maxLifetime: Float = 0.24f
)

data class DamageFeedbackPopup(
    val worldPosition: Vector3,
    val text: String,
    val isCriticalOrHeavy: Boolean,
    var age: Float = 0f,
    val maxLifetime: Float = 0.95f
)

/**
 * Orchestrates the Milestone 2 Free-Form Anime Combat System:
 * - Hand-to-hand martial arts (Light punches, crosses, spinning kicks, heavy haymakers, aerial dive kicks)
 * - Melee weapon combat (3-hit Rune Spellsword combo slashes, heavy overhead mana cleave, aerial slash)
 * - Tap-for-Arcane-Magic-Bolt & Hold-for-Charged-Energy-Blast projectile system
 * - Real 3D melee hit detection, knockback impulses, 3D slash waves, and impact particles
 */
class CombatSystem(
    private val modelFactory: ProceduralModelFactory,
    private val worldManager: WorldManager,
    private val entityManager: EntityManager,
    private val audioManager: AudioManager
) {
    private lateinit var energyBlastModel: Model
    private lateinit var arcaneBoltModel: Model
    private lateinit var slashWaveModel: Model
    private lateinit var cyanSparkModel: Model
    private lateinit var goldSparkModel: Model
    private lateinit var crimsonSparkModel: Model
    private lateinit var targetLockMarkerModel: Model

    private lateinit var chargingOrbInstance: ModelInstance
    private lateinit var targetLockMarkerInstance: ModelInstance
    private var lockMarkerPulseTime: Float = 0f

    val projectiles = mutableListOf<ActiveProjectile>()
    val particles = mutableListOf<ActiveParticle3D>()
    val slashWaves = mutableListOf<ActiveSlashWave>()
    val damagePopups = mutableListOf<DamageFeedbackPopup>()

    // Dedicated Milestone 3 Sword Combat System (completely separate from Fists)
    val swordCombatController = SwordCombatController(
        modelFactory = modelFactory,
        worldManager = worldManager,
        entityManager = entityManager,
        audioManager = audioManager,
        onRecordDamagePopup = { pos, dmg, isHeavy -> recordDamagePopup(pos, dmg, isHeavy) },
        onSpawnHitSparks = { origin, count, speed, variant -> spawnParticleBurst(origin, count, speed, variant) }
    )

    // Dedicated Milestone 4 Fists / Martial Arts Combat System (completely separate from Sword)
    val fistsCombatController = FistsCombatController(
        modelFactory = modelFactory,
        worldManager = worldManager,
        entityManager = entityManager,
        audioManager = audioManager,
        onRecordDamagePopup = { pos, dmg, isHeavy -> recordDamagePopup(pos, dmg, isHeavy) },
        onSpawnHitSparks = { origin, count, speed, variant -> spawnParticleBurst(origin, count, speed, variant) }
    )

    // Dedicated DIVINE ANNIHILATION Ultimate Ability Controller
    val divineAnnihilationController = DivineAnnihilationController(
        assetManager = modelFactory.assets,
        worldManager = worldManager,
        entityManager = entityManager,
        audioManager = audioManager
    )

    private var activeCameraController: FollowCameraController? = null

    val isUltimateActive: Boolean
        get() = divineAnnihilationController.isActive

    val ultimateCooldown: Float
        get() = divineAnnihilationController.cooldownRemaining

    val ultimateMaxCooldown: Float
        get() = DivineAnnihilationController.COOLDOWN_DURATION

    val isAttacking: Boolean
        get() = fistsCombatController.isFistAttacking || swordCombatController.isSwordAttacking || divineAnnihilationController.isActive

    val hitStopTimer: Float
        get() = maxOf(fistsCombatController.hitStopTimer, swordCombatController.hitStopTimer)

    val currentComboStep: Int
        get() = if (swordCombatController.currentComboStep > 0) {
            swordCombatController.currentComboStep
        } else {
            fistsCombatController.currentComboStep
        }

    fun activeComboStep(player: PlayerCharacter): Int =
        if (player.isSwordEquipped) swordCombatController.currentComboStep else fistsCombatController.currentComboStep

    fun maxComboSteps(player: PlayerCharacter): Int =
        if (player.isSwordEquipped) swordCombatController.maxComboSteps else fistsCombatController.maxComboSteps

    // Energy / Magic Charging State
    var isChargingEnergy: Boolean = false
        private set
    var chargeDuration: Float = 0f
        private set
    val maxChargeTime: Float = 1.25f
    private var castPoseTimer: Float = 0f
    private var chargeParticleAccum: Float = 0f

    // Ability Cooldowns (Fist vs Sword)
    val heavyMaxCooldown: Float = 0.85f
    val heavyCooldown: Float
        get() = maxOf(fistsCombatController.heavyCooldown, swordCombatController.heavyCooldown)

    fun activeHeavyCooldown(player: PlayerCharacter): Float =
        if (player.isSwordEquipped) swordCombatController.heavyCooldown else fistsCombatController.heavyCooldown

    fun activeHeavyMaxCooldown(player: PlayerCharacter): Float =
        if (player.isSwordEquipped) swordCombatController.heavyMaxCooldown else fistsCombatController.heavyMaxCooldown

    var energyCooldown: Float = 0f
        private set
    val energyMaxCooldown: Float = 0.45f

    private var fistDodgeCooldown: Float = 0f
    val dodgeMaxCooldown: Float = 0.55f
    val dodgeCooldown: Float
        get() = maxOf(fistDodgeCooldown, swordCombatController.flashStepCooldown)

    fun activeDodgeCooldown(player: PlayerCharacter): Float =
        if (player.isSwordEquipped) swordCombatController.flashStepCooldown else fistDodgeCooldown

    fun activeDodgeMaxCooldown(player: PlayerCharacter): Float =
        if (player.isSwordEquipped) swordCombatController.flashStepMaxCooldown else dodgeMaxCooldown

    private val rng = Random(777)

    fun initialize() {
        energyBlastModel = modelFactory.createEnergyBlastModel()
        arcaneBoltModel = modelFactory.createArcaneBoltModel()
        slashWaveModel = modelFactory.createSlashWaveModel()
        cyanSparkModel = modelFactory.createCombatSparkModel(0)
        goldSparkModel = modelFactory.createCombatSparkModel(1)
        crimsonSparkModel = modelFactory.createCombatSparkModel(2)
        targetLockMarkerModel = modelFactory.createTargetLockMarkerModel()

        chargingOrbInstance = ModelInstance(energyBlastModel)
        targetLockMarkerInstance = ModelInstance(targetLockMarkerModel)
        swordCombatController.initialize()
        fistsCombatController.initialize()
        divineAnnihilationController.initialize()
    }

    val chargeRatio: Float
        get() = if (isChargingEnergy) (chargeDuration / maxChargeTime).coerceIn(0f, 1f) else 0f

    /**
     * Triggers the DIVINE ANNIHILATION ultimate ability if not already active and not on its 15s post-completion cooldown.
     */
    fun triggerUltimateAbility(player: PlayerCharacter, cameraController: FollowCameraController): Boolean {
        activeCameraController = cameraController
        if (!divineAnnihilationController.canActivate()) return false
        isChargingEnergy = false
        chargeDuration = 0f
        fistsCombatController.reset()
        swordCombatController.reset()
        return divineAnnihilationController.tryActivate(player, cameraController, this)
    }

    fun toggleWeaponStance(player: PlayerCharacter) {
        if (isUltimateActive) return
        player.isSwordEquipped = !player.isSwordEquipped
        // Cleanly reset both independent combat controllers when switching between FISTS and SWORD
        fistsCombatController.reset()
        swordCombatController.reset()
        player.activeAttackProgress = 0f

        audioManager.playWeaponToggle(player.isSwordEquipped)
        spawnParticleBurst(
            origin = Vector3(player.position.x, player.position.y + 1.0f, player.position.z),
            count = 8,
            speed = 3.5f,
            variant = if (player.isSwordEquipped) 1 else 0
        )
    }

    /**
     * Triggers a Light Attack:
     * - If SWORD is equipped: delegates to SwordCombatController (5-step Fast Sword Combo / Phantom Dash Slash / Aerial Sword Strike).
     * - If FISTS are equipped: delegates to FistsCombatController (6-step Fast Martial Arts Combo with contextual finishers).
     */
    fun triggerLightAttack(player: PlayerCharacter, cameraController: FollowCameraController) {
        if (player.isDodging || isUltimateActive) return
        if (isChargingEnergy) {
            isChargingEnergy = false
        }

        if (player.isSwordEquipped) {
            swordCombatController.triggerLightSwordAttack(player, cameraController)
        } else {
            fistsCombatController.triggerLightFistAttack(player, cameraController)
        }
    }

    /**
     * Triggers a Heavy Attack:
     * - If SWORD is equipped: delegates to SwordCombatController (Inferno Crescent, Dragon Tempest, Aerial Sword Strike).
     * - If FISTS are equipped: delegates to FistsCombatController (Dragon Haymaker, Thunder Breaker, Meteor Strike).
     */
    fun triggerHeavyAttack(player: PlayerCharacter, cameraController: FollowCameraController) {
        if (player.isDodging || isUltimateActive) return
        if (isChargingEnergy) isChargingEnergy = false

        if (player.isSwordEquipped) {
            swordCombatController.triggerHeavySwordAttack(player, cameraController)
        } else {
            fistsCombatController.triggerHeavyFistAttack(player, cameraController)
        }
    }

    /**
     * Called when the player presses down the Energy/Magic attack button.
     */
    fun beginEnergyCharge(player: PlayerCharacter, cameraController: FollowCameraController) {
        if (energyCooldown > 0f || player.isDodging || isUltimateActive) return
        if (player.energy < 10f) return

        fistsCombatController.reset()
        isChargingEnergy = true
        chargeDuration = 0f
        orientPlayerForCombat(player, cameraController)
        audioManager.playEnergyChargeStart()
    }

    /**
     * Called when the player releases the Energy/Magic button:
     * - Quick tap (< 0.28s): Fires a fast Arcane Magic Bolt projectile
     * - Hold (>= 0.28s): Unleashes a powerful Charged Energy Blast sphere
     */
    fun releaseEnergyAttack(player: PlayerCharacter, cameraController: FollowCameraController) {
        if (!isChargingEnergy || isUltimateActive) return
        val heldTime = chargeDuration
        isChargingEnergy = false
        chargeDuration = 0f

        orientPlayerForCombat(player, cameraController)
        val rad = Math.toRadians(player.facingYaw.toDouble()).toFloat()
        val fwdX = sin(rad)
        val fwdZ = cos(rad)

        // Prioritize locked target for 3D projectile trajectory; fall back to primary enemy within range
        val lockedEnemy = if (cameraController.isLockedOn) cameraController.lockedTarget else null
        val targetEnemy = lockedEnemy ?: entityManager.primaryEnemy()
        val maxAimDist = if (lockedEnemy != null) 34f else 26f
        var dirX = fwdX
        var dirY = 0f
        var dirZ = fwdZ
        if (targetEnemy != null && targetEnemy.aiState != EnemyAiState.DEAD) {
            val dx = targetEnemy.entity.position.x - player.position.x
            val dy = (targetEnemy.entity.position.y + 1.15f) - (player.position.y + 1.25f)
            val dz = targetEnemy.entity.position.z - player.position.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist in 0.5f..maxAimDist) {
                dirX = dx / dist
                dirY = (dy / dist).coerceIn(-0.45f, 0.45f)
                dirZ = dz / dist
                player.facingYaw = Math.toDegrees(atan2(dirX.toDouble(), dirZ.toDouble())).toFloat()
            }
        }

        val spawnPos = Vector3(
            player.position.x + dirX * 0.75f,
            player.position.y + 1.22f,
            player.position.z + dirZ * 0.75f
        )

        if (heldTime < 0.28f) {
            // Quick Tap -> Arcane Magic Bolt
            val manaCost = 10f
            if (player.energy < manaCost) return
            player.energy = (player.energy - manaCost).coerceAtLeast(0f)
            energyCooldown = 0.28f
            castPoseTimer = 0.22f

            val inst = ModelInstance(arcaneBoltModel)
            val speed = 22f
            projectiles.add(
                ActiveProjectile(
                    instance = inst,
                    kind = ProjectileKind.ARCANE_MAGIC_BOLT,
                    position = spawnPos,
                    velocity = Vector3(dirX * speed, dirY * speed, dirZ * speed),
                    damage = 22f,
                    knockbackStrength = 5.2f,
                    hitRadius = 0.55f,
                    scale = 1.0f
                )
            )
            audioManager.playMagicBoltCast()
            spawnParticleBurst(spawnPos, count = 6, speed = 4.0f, variant = 0)
        } else {
            // Hold & Release -> Charged Energy Blast
            val ratio = (heldTime / maxChargeTime).coerceIn(0.25f, 1.0f)
            val manaCost = 16f + ratio * 14f
            player.energy = (player.energy - manaCost).coerceAtLeast(0f)
            energyCooldown = 0.60f
            castPoseTimer = 0.34f

            // Slight recoil / air hover when firing a charged blast
            if (!player.isGrounded && player.velocity.y < 2.5f) {
                player.velocity.y = 2.8f
            }

            val inst = ModelInstance(energyBlastModel)
            val speed = 18.5f
            val blastScale = 1.15f + ratio * 1.15f
            val blastDamage = 36f + ratio * 34f
            projectiles.add(
                ActiveProjectile(
                    instance = inst,
                    kind = ProjectileKind.CHARGED_ENERGY_BLAST,
                    position = spawnPos,
                    velocity = Vector3(dirX * speed, dirY * speed, dirZ * speed),
                    damage = blastDamage,
                    knockbackStrength = 14.5f,
                    hitRadius = 0.95f * blastScale,
                    scale = blastScale
                )
            )
            audioManager.playChargedBlastRelease()
            spawnSlashWave(spawnPos, player.facingYaw, 90f, 0.9f, 6.5f)
            spawnParticleBurst(spawnPos, count = 14, speed = 6.5f, variant = 1)
        }
    }

    /**
     * Triggers a directional Dodge / Dash burst:
     * - If SWORD is equipped: delegates to SwordCombatController.triggerFlashStep (Flash Step vanish & afterimage reposition).
     * - If FISTS are equipped: executes the Martial Arts Dodge / Dash burst.
     */
    fun triggerDodgeDash(
        player: PlayerCharacter,
        moveInputX: Float,
        moveInputY: Float,
        cameraController: FollowCameraController
    ): Boolean {
        if (isUltimateActive) return false
        if (player.isSwordEquipped) {
            isChargingEnergy = false
            return swordCombatController.triggerFlashStep(player, moveInputX, moveInputY, cameraController)
        }

        if (fistDodgeCooldown > 0f || player.isDodging) return false
        if (!player.isGrounded && player.hasUsedAirDash) return false
        if (player.energy < 8f) return false

        player.energy = (player.energy - 8f).coerceAtLeast(0f)
        fistsCombatController.reset()
        isChargingEnergy = false
        fistDodgeCooldown = dodgeMaxCooldown
        player.isDodging = true
        player.dodgeTimer = 0.25f
        player.invulnerabilityTimer = 0.34f
        if (!player.isGrounded) {
            player.hasUsedAirDash = true
            player.velocity.y = 2.2f
        }

        val lockedTarget = if (cameraController.isLockedOn) cameraController.lockedTarget else null
        var dirX: Float
        var dirZ: Float

        if (lockedTarget != null) {
            val dx = lockedTarget.entity.position.x - player.position.x
            val dz = lockedTarget.entity.position.z - player.position.z
            val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.05f)
            val toTargetX = dx / dist
            val toTargetZ = dz / dist
            val strafeRightX = -toTargetZ
            val strafeRightZ = toTargetX

            var effMoveY = moveInputY
            // If already close to the target, prevent forward dash from colliding into the enemy
            val minDashSafeDist = player.collisionRadius + lockedTarget.collisionRadius + 1.85f
            if (dist <= minDashSafeDist && effMoveY > 0f) {
                effMoveY = 0f
            }

            dirX = strafeRightX * moveInputX + toTargetX * effMoveY
            dirZ = strafeRightZ * moveInputX + toTargetZ * effMoveY
            val len = sqrt(dirX * dirX + dirZ * dirZ)
            if (len > 0.1f) {
                dirX /= len
                dirZ /= len
            } else {
                // Neutral joystick while locked on: gap-close if far, or quick sidestep/backstep if already close
                if (dist > 3.6f) {
                    dirX = toTargetX
                    dirZ = toTargetZ
                } else {
                    dirX = -toTargetX
                    dirZ = -toTargetZ
                }
            }
            // Keep player facing the locked target during dash
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
                // Dash forward along facing direction if joystick is neutral
                val faceRad = Math.toRadians(player.facingYaw.toDouble()).toFloat()
                dirX = sin(faceRad)
                dirZ = cos(faceRad)
            }
            player.facingYaw = Math.toDegrees(atan2(dirX.toDouble(), dirZ.toDouble())).toFloat()
        }

        player.dodgeDirection.set(dirX, 0f, dirZ)
        audioManager.playDodgeDash()
        spawnParticleBurst(
            origin = Vector3(player.position.x, player.position.y + 0.9f, player.position.z),
            count = 10,
            speed = 4.5f,
            variant = 0
        )
        return true
    }

    fun update(delta: Float, player: PlayerCharacter, cameraController: FollowCameraController) {
        activeCameraController = cameraController
        lockMarkerPulseTime += delta

        // Update DIVINE ANNIHILATION ultimate ability state machine & cooldown
        divineAnnihilationController.update(delta, player, cameraController, this)

        // Update 3D overhead Lock-On Target Marker transform when locked on
        val lockedTarget = if (cameraController.isLockedOn) cameraController.lockedTarget else null
        if (lockedTarget != null) {
            val mx = lockedTarget.entity.position.x
            val my = lockedTarget.entity.position.y + 2.65f + sin(lockMarkerPulseTime * 5.5f) * 0.10f
            val mz = lockedTarget.entity.position.z
            val pulseScale = 1.0f + sin(lockMarkerPulseTime * 7f) * 0.08f
            targetLockMarkerInstance.transform.idt()
                .translate(mx, my, mz)
                .rotate(0f, 1f, 0f, (lockMarkerPulseTime * 140f) % 360f)
                .scale(pulseScale, pulseScale, pulseScale)
        }

        // Regenerate player Energy/Mana when not actively charging
        if (!isChargingEnergy) {
            player.energy = (player.energy + delta * 14f).coerceAtMost(player.maxEnergy)
        }

        // Tick Energy & Dodge cooldowns
        if (energyCooldown > 0f) energyCooldown = (energyCooldown - delta).coerceAtLeast(0f)
        if (fistDodgeCooldown > 0f) fistDodgeCooldown = (fistDodgeCooldown - delta).coerceAtLeast(0f)
        if (castPoseTimer > 0f) {
            castPoseTimer = (castPoseTimer - delta).coerceAtLeast(0f)
        }

        // Update Energy Charging state & growing 3D mana orb at the hands
        if (isChargingEnergy) {
            chargeDuration = (chargeDuration + delta).coerceAtMost(maxChargeTime * 1.5f)
            player.animationState = AnimationState.ENERGY_CHARGE
            player.activeAttackProgress = chargeRatio

            if (lockedTarget != null) {
                orientPlayerForCombat(player, cameraController)
            }

            val rad = Math.toRadians(player.facingYaw.toDouble()).toFloat()
            val orbX = player.position.x + sin(rad) * 0.48f
            val orbY = player.position.y + 1.15f
            val orbZ = player.position.z + cos(rad) * 0.48f
            val orbScale = 0.35f + chargeRatio * 0.95f + sin(chargeDuration * 18f) * 0.06f

            chargingOrbInstance.transform.idt()
                .translate(orbX, orbY, orbZ)
                .rotate(0f, 1f, 0f, (chargeDuration * 260f) % 360f)
                .scale(orbScale, orbScale, orbScale)

            chargeParticleAccum += delta
            if (chargeParticleAccum >= 0.07f) {
                chargeParticleAccum = 0f
                spawnParticleBurst(Vector3(orbX, orbY, orbZ), count = 2, speed = 2.8f, variant = if (chargeRatio >= 0.8f) 1 else 0)
            }
        } else if (castPoseTimer > 0f) {
            player.animationState = AnimationState.ENERGY_CAST
            player.activeAttackProgress = 1f - (castPoseTimer / 0.34f).coerceIn(0f, 1f)
        }

        // Update dedicated Milestone 4 Fists Combat System (6-stage martial arts combo, contextual heavy strikes, wind/chi VFX)
        fistsCombatController.update(delta, player, cameraController)

        // Update dedicated Milestone 3 Sword Combat System (active slashes, Flash Step, Inferno Crescent, Dragon Tempest, VFX)
        swordCombatController.update(delta, player, cameraController)

        // Spawn trail motes while dodging in Fist stance
        if (player.isDodging && !player.isSwordEquipped) {
            chargeParticleAccum += delta
            if (chargeParticleAccum >= 0.045f) {
                chargeParticleAccum = 0f
                spawnParticleBurst(
                    origin = Vector3(player.position.x, player.position.y + 0.85f, player.position.z),
                    count = 2,
                    speed = 2.2f,
                    variant = 0
                )
            }
        }

        // Update active 3D projectiles
        val projIter = projectiles.iterator()
        while (projIter.hasNext()) {
            val p = projIter.next()
            p.age += delta
            if (p.age >= p.maxLifetime) {
                projIter.remove()
                continue
            }

            // Homing assist for Arcane Magic Bolt (prioritizes locked target)
            val enemy = lockedTarget ?: entityManager.primaryEnemy()
            val homingRange = if (lockedTarget != null) 28f else 18f
            if (p.kind == ProjectileKind.ARCANE_MAGIC_BOLT && enemy != null && enemy.aiState != EnemyAiState.DEAD) {
                val tx = enemy.entity.position.x - p.position.x
                val ty = (enemy.entity.position.y + 1.1f) - p.position.y
                val tz = enemy.entity.position.z - p.position.z
                val dist = sqrt(tx * tx + ty * ty + tz * tz)
                if (dist in 0.5f..homingRange) {
                    val speed = p.velocity.len()
                    val homingStrength = if (lockedTarget != null) 8.5f else 5f
                    p.velocity.x = com.badlogic.gdx.math.MathUtils.lerp(p.velocity.x, (tx / dist) * speed, delta * homingStrength)
                    p.velocity.y = com.badlogic.gdx.math.MathUtils.lerp(p.velocity.y, (ty / dist) * speed, delta * homingStrength)
                    p.velocity.z = com.badlogic.gdx.math.MathUtils.lerp(p.velocity.z, (tz / dist) * speed, delta * homingStrength)
                }
            }

            p.position.mulAdd(p.velocity, delta)
            val yaw = Math.toDegrees(atan2(p.velocity.x.toDouble(), p.velocity.z.toDouble())).toFloat()
            p.instance.transform.idt()
                .translate(p.position.x, p.position.y, p.position.z)
                .rotate(0f, 1f, 0f, yaw)
                .rotate(0f, 0f, 1f, (p.age * 360f) % 360f)
                .scale(p.scale, p.scale, p.scale)

            // Check collision with enemies
            var hitSomething = false
            for (ec in entityManager.enemyControllers) {
                if (ec.aiState == EnemyAiState.DEAD) continue
                val dx = ec.entity.position.x - p.position.x
                val dz = ec.entity.position.z - p.position.z
                val dy = (ec.entity.position.y + 1.05f) - p.position.y
                val hitDist = ec.collisionRadius + p.hitRadius
                if (dx * dx + dz * dz <= hitDist * hitDist && kotlin.math.abs(dy) <= 1.45f) {
                    val vLen = sqrt(p.velocity.x * p.velocity.x + p.velocity.z * p.velocity.z).coerceAtLeast(0.01f)
                    val kx = (p.velocity.x / vLen) * p.knockbackStrength
                    val kz = (p.velocity.z / vLen) * p.knockbackStrength
                    val isCharged = p.kind == ProjectileKind.CHARGED_ENERGY_BLAST
                    val defeated = ec.receiveDamage(p.damage, kx, kz, isCharged, audioManager)
                    audioManager.playMeleeHit(isHeavy = isCharged)

                    recordDamagePopup(
                        pos = Vector3(ec.entity.position.x, ec.entity.position.y + 2.1f, ec.entity.position.z),
                        damage = p.damage,
                        isHeavy = isCharged
                    )

                    if (isCharged || defeated) {
                        spawnSlashWave(p.position, 0f, 0f, 1.2f, 9.5f)
                        spawnParticleBurst(p.position, count = 18, speed = 7.5f, variant = if (defeated) 2 else 1)
                    } else {
                        spawnParticleBurst(p.position, count = 10, speed = 5.0f, variant = 0)
                    }
                    hitSomething = true
                    break
                }
            }

            // Check terrain ground collision
            val groundY = worldManager.getWalkableSurfaceHeight(p.position.x, p.position.z)
            if (!hitSomething && p.position.y <= groundY + 0.15f) {
                spawnParticleBurst(p.position, count = 8, speed = 4.0f, variant = 0)
                hitSomething = true
            }

            if (hitSomething) {
                projIter.remove()
            }
        }

        // Update 3D slash waves
        val waveIter = slashWaves.iterator()
        while (waveIter.hasNext()) {
            val w = waveIter.next()
            w.age += delta
            if (w.age >= w.maxLifetime) {
                waveIter.remove()
                continue
            }
            val s = w.initialScale + w.age * w.expansionRate
            w.instance.transform.idt()
                .translate(w.position.x, w.position.y, w.position.z)
                .rotate(0f, 1f, 0f, w.yawDeg)
                .rotate(0f, 0f, 1f, w.tiltDeg)
                .scale(s, 1f, s)
        }

        // Update 3D spark particles
        val partIter = particles.iterator()
        while (partIter.hasNext()) {
            val pt = partIter.next()
            pt.age += delta
            if (pt.age >= pt.maxLifetime) {
                partIter.remove()
                continue
            }
            pt.velocity.y -= 9.8f * delta
            pt.position.mulAdd(pt.velocity, delta)
            val lifeRatio = 1f - (pt.age / pt.maxLifetime).coerceIn(0f, 1f)
            val s = pt.initialScale * lifeRatio
            pt.instance.transform.idt()
                .translate(pt.position.x, pt.position.y, pt.position.z)
                .scale(s, s, s)
        }

        // Update floating damage popups
        val popIter = damagePopups.iterator()
        while (popIter.hasNext()) {
            val pop = popIter.next()
            pop.age += delta
            pop.worldPosition.y += delta * 1.35f
            if (pop.age >= pop.maxLifetime) {
                popIter.remove()
            }
        }
    }

    /**
     * Samples the exact current-frame world-space sword blade or martial limb positions after skeletal animation updates.
     */
    fun postAnimationUpdate(delta: Float, player: PlayerCharacter) {
        swordCombatController.postAnimationUpdate(delta, player)
        fistsCombatController.postAnimationUpdate(delta, player)
    }

    fun render(modelBatch: ModelBatch, environment: Environment, cameraController: FollowCameraController) {
        if (cameraController.isLockedOn) {
            modelBatch.render(targetLockMarkerInstance, environment)
        }
        if (isChargingEnergy) {
            modelBatch.render(chargingOrbInstance, environment)
        }
        for (p in projectiles) {
            modelBatch.render(p.instance, environment)
        }
        for (w in slashWaves) {
            modelBatch.render(w.instance, environment)
        }
        for (pt in particles) {
            modelBatch.render(pt.instance, environment)
        }
        swordCombatController.render(modelBatch, environment)
        fistsCombatController.render(modelBatch, environment)
        divineAnnihilationController.render(modelBatch, environment)
    }

    fun reset() {
        isChargingEnergy = false
        chargeDuration = 0f
        energyCooldown = 0f
        fistDodgeCooldown = 0f
        projectiles.clear()
        particles.clear()
        slashWaves.clear()
        damagePopups.clear()
        swordCombatController.reset()
        fistsCombatController.reset()
        divineAnnihilationController.reset( cameraController = activeCameraController)
    }

    fun triggerScreenShake(intensity: Float, duration: Float) {
        activeCameraController?.triggerCameraShake(intensity, duration)
    }

    fun spawnMagicCastBurst(origin: Vector3, isHeavy: Boolean) {
        spawnParticleBurst(
            origin = origin,
            count = if (isHeavy) 16 else 8,
            speed = if (isHeavy) 5.5f else 3.5f,
            variant = if (isHeavy) 1 else 0
        )
    }

    fun spawnMagicImpactBurst(pos: Vector3, isChargedBlast: Boolean) {
        spawnSlashWave(pos, 0f, 0f, if (isChargedBlast) 1.5f else 0.9f, if (isChargedBlast) 12.5f else 6.5f)
        spawnParticleBurst(
            origin = pos,
            count = if (isChargedBlast) 24 else 12,
            speed = if (isChargedBlast) 8.5f else 5.0f,
            variant = if (isChargedBlast) 1 else 0
        )
    }

    fun spawnMeleeHitBurst(hitPoint: Vector3, isHeavy: Boolean, isSword: Boolean) {
        spawnParticleBurst(
            origin = hitPoint,
            count = if (isHeavy) 14 else 8,
            speed = if (isHeavy) 5.5f else 3.8f,
            variant = if (isSword) 0 else 1
        )
    }

    private fun orientPlayerForCombat(player: PlayerCharacter, cameraController: FollowCameraController) {
        // 1. Always prioritize the locked target when Lock-On is active
        val locked = if (cameraController.isLockedOn) cameraController.lockedTarget else null
        if (locked != null && locked.aiState != EnemyAiState.DEAD) {
            val dx = locked.entity.position.x - player.position.x
            val dz = locked.entity.position.z - player.position.z
            if (dx * dx + dz * dz > 0.005f) {
                player.facingYaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
                return
            }
        }

        // 2. Otherwise soft auto-aim toward nearest enemy if within close melee engagement range (6.5m)
        val enemy = entityManager.primaryEnemy()
        if (enemy != null && enemy.aiState != EnemyAiState.DEAD) {
            val dx = enemy.entity.position.x - player.position.x
            val dz = enemy.entity.position.z - player.position.z
            val distSq = dx * dx + dz * dz
            if (distSq <= 6.5f * 6.5f && distSq > 0.01f) {
                player.facingYaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
                return
            }
        }
    }

    fun recordDamagePopup(pos: Vector3, damage: Float, isHeavy: Boolean) {
        val label = if (isHeavy) "-${damage.toInt()}!" else "-${damage.toInt()}"
        damagePopups.add(
            DamageFeedbackPopup(
                worldPosition = Vector3(pos),
                text = label,
                isCriticalOrHeavy = isHeavy
            )
        )
    }

    fun spawnSlashWave(
        pos: Vector3,
        yawDeg: Float,
        tiltDeg: Float,
        initialScale: Float,
        expansionRate: Float
    ) {
        if (!::slashWaveModel.isInitialized) return
        val inst = ModelInstance(slashWaveModel)
        slashWaves.add(
            ActiveSlashWave(
                instance = inst,
                position = Vector3(pos),
                yawDeg = yawDeg,
                tiltDeg = tiltDeg,
                initialScale = initialScale,
                expansionRate = expansionRate
            )
        )
    }

    fun spawnParticleBurst(origin: Vector3, count: Int, speed: Float, variant: Int) {
        if (!::cyanSparkModel.isInitialized) return
        val model = when (variant) {
            1 -> goldSparkModel
            2 -> crimsonSparkModel
            else -> cyanSparkModel
        }
        for (i in 0 until count) {
            val vx = (rng.nextFloat() * 2f - 1f) * speed
            val vy = (rng.nextFloat() * 0.85f + 0.25f) * speed
            val vz = (rng.nextFloat() * 2f - 1f) * speed
            val inst = ModelInstance(model)
            particles.add(
                ActiveParticle3D(
                    instance = inst,
                    position = Vector3(origin),
                    velocity = Vector3(vx, vy, vz),
                    initialScale = 0.7f + rng.nextFloat() * 0.6f,
                    maxLifetime = 0.32f + rng.nextFloat() * 0.22f
                )
            )
        }
    }
}
