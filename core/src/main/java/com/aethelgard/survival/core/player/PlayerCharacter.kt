package com.aethelgard.survival.core.player

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3

/**
 * Represents the player character entity in AethelgardSurvival.
 * Holds position, velocity, facing orientation, grounded status, sprint status,
 * combat health & mana/energy pools, weapon stance (Unarmed Martial Arts vs. Rune Spellsword),
 * active combo/dodge states, and the articulated 3D ModelInstance.
 */
class PlayerCharacter(
    playerModel: Model,
    val spawnPosition: Vector3 = Vector3(0f, 0f, 20f)
) {
    val modelInstance: ModelInstance = ModelInstance(playerModel)
    val animationController: PlayerAnimationController = PlayerAnimationController(modelInstance)

    val position: Vector3 = Vector3(spawnPosition)
    val velocity: Vector3 = Vector3()

    /** Facing yaw in degrees (0 = +Z, 180 = -Z toward central monument). */
    var facingYaw: Float = 180f

    var isGrounded: Boolean = true
    var isSprinting: Boolean = false
    var animationState: AnimationState = AnimationState.IDLE

    // Triple Jump System state (reset upon landing)
    val maxJumps: Int = 3
    var jumpCount: Int = 0

    // Milestone 2: Combat & Stance Properties
    val maxHealth: Float = 100f
    var health: Float = 100f

    val maxEnergy: Float = 100f
    var energy: Float = 100f

    /** False = Hand-to-Hand Martial Arts (`FISTS`), True = Rune Spellsword (`SWORD`). */
    var isSwordEquipped: Boolean = false

    var isDodging: Boolean = false
    var dodgeTimer: Float = 0f
    val dodgeDirection: Vector3 = Vector3()
    var invulnerabilityTimer: Float = 0f
    var hitStunTimer: Float = 0f

    // Limited aerial mobility flags (reset upon landing)
    var hasUsedAirDash: Boolean = false
    var hasUsedAirAttack: Boolean = false

    // Active attack animation progress (0..1)
    var activeAttackProgress: Float = 0f

    // Phase 2: Equipped survival tool & active gathering tool animation state
    var equippedToolId: String? = null
    // Selected holdable inventory item or resource in active hotbar slot
    var equippedItemId: String? = null
    var activeToolAnimationState: AnimationState? = null
    var activeToolTimer: Float = 0f
    var activeToolDuration: Float = 0.42f

    val isUsingTool: Boolean
        get() = activeToolTimer > 0f && activeToolAnimationState != null

    val collisionRadius: Float = 0.45f
    val eyeHeight: Float = 1.56f
    val shoulderPivotHeight: Float = 1.42f

    fun startToolAction(animState: AnimationState, toolId: String?, duration: Float = 0.42f) {
        activeToolAnimationState = animState
        if (toolId != null) {
            equippedToolId = toolId
            equippedItemId = toolId
        }
        activeToolDuration = duration
        activeToolTimer = duration
        activeAttackProgress = 0f
        animationState = animState
    }

    fun triggerToolSwing(animState: AnimationState, duration: Float = 0.36f) {
        startToolAction(animState, equippedToolId ?: "builders_hammer", duration)
    }

    fun syncModelTransform() {
        modelInstance.transform.idt()
            .translate(position.x, position.y, position.z)
            .rotate(0f, 1f, 0f, facingYaw)
    }

    fun applyDamage(amount: Float, knockbackX: Float, knockbackZ: Float): Boolean {
        if (invulnerabilityTimer > 0f) return false
        health = (health - amount).coerceAtLeast(1f) // Keep player alive in test prototype or auto-recover
        velocity.x = knockbackX
        velocity.z = knockbackZ
        hitStunTimer = 0.14f // Cosmetic visual flinch / hit-flash timer; never locks input
        invulnerabilityTimer = 0.55f
        return true
    }

    fun heal(amount: Float) {
        health = (health + amount).coerceAtMost(maxHealth)
    }

    fun resetToSpawn(groundHeight: Float) {
        position.set(spawnPosition.x, groundHeight, spawnPosition.z)
        velocity.setZero()
        facingYaw = 180f
        isGrounded = true
        jumpCount = 0
        isSprinting = false
        health = maxHealth
        energy = maxEnergy
        isDodging = false
        dodgeTimer = 0f
        invulnerabilityTimer = 0f
        hitStunTimer = 0f
        hasUsedAirDash = false
        hasUsedAirAttack = false
        activeAttackProgress = 0f
        activeToolTimer = 0f
        activeToolAnimationState = null
        animationState = AnimationState.IDLE
        syncModelTransform()
    }
}
