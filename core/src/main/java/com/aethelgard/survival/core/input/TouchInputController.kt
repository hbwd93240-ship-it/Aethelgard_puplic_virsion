package com.aethelgard.survival.core.input

import com.badlogic.gdx.InputAdapter
import kotlin.math.sqrt

data class CircleButtonBounds(
    var centerX: Float = 0f,
    var centerY: Float = 0f,
    var radius: Float = 64f
) {
    fun contains(x: Float, y: Float): Boolean {
        val dx = x - centerX
        val dy = y - centerY
        return (dx * dx + dy * dy) <= radius * radius
    }
}

data class RectButtonBounds(
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Float = 140f,
    var height: Float = 48f
) {
    fun set(nx: Float, ny: Float, nw: Float, nh: Float) {
        x = nx
        y = ny
        width = nw
        height = nh
    }

    fun contains(px: Float, py: Float): Boolean {
        return px >= x && px <= (x + width) && py >= y && py <= (y + height)
    }
}

/**
 * Multi-touch Android input controller for AethelgardSurvival (Milestone 1 + Milestone 2 Combat).
 * Supports simultaneous:
 * - Left-side Dynamic Floating Virtual Joystick
 * - Right-side Camera Look drag
 * - Dedicated Combat Buttons: Light Attack (ATK), Heavy Attack (HVY), Energy/Magic Attack (NRG tap/hold),
 *   Dodge/Dash (DASH), Jump (JUMP), Sprint (SPR), and Weapon Stance Toggle (FISTS / SWORD).
 */
class TouchInputController(
    private val onLookDelta: (deltaYawDeg: Float, deltaPitchDeg: Float) -> Unit,
    private val onToggleCameraMode: () -> Unit,
    private val onResetRequested: () -> Unit,
    private val onToggleAudio: () -> Unit,
    private val onSprintStateChanged: (Boolean) -> Unit,
    private val onLightAttack: () -> Unit = {},
    private val onHeavyAttack: () -> Unit = {},
    private val onEnergyPress: () -> Unit = {},
    private val onEnergyRelease: () -> Unit = {},
    private val onToggleWeaponStance: () -> Unit = {},
    private val onToggleLockOn: () -> Unit = {},
    private val onUltimateAttack: () -> Unit = {},
    private val onTogglePredictionHaki: () -> Unit = {}
) : InputAdapter() {

    var screenWidth: Float = 1280f
        private set
    var screenHeight: Float = 720f
        private set

    // Dynamic floating joystick state (Y-up screen coordinates)
    var joystickPointer: Int = -1
        private set
    var joystickOriginX: Float = 200f
        private set
    var joystickOriginY: Float = 200f
        private set
    var joystickKnobX: Float = 200f
        private set
    var joystickKnobY: Float = 200f
        private set
    var joystickMaxRadius: Float = 105f
        private set

    /** Normalized horizontal movement (-1 = left, +1 = right). */
    var moveX: Float = 0f
        private set

    /** Normalized forward movement (-1 = backward, +1 = forward). */
    var moveY: Float = 0f
        private set

    // Camera look touch tracking
    private var lookPointer: Int = -1
    private var lastLookX: Float = 0f
    private var lastLookY: Float = 0f
    private val lookSensitivityDegPerPx: Float = 0.22f

    // Action button states & pointers
    private var jumpRequested: Boolean = false
    var isJumpPressed: Boolean = false
        private set
    private var jumpPointer: Int = -1

    private var dodgeRequested: Boolean = false
    var isDodgePressed: Boolean = false
        private set
    private var dodgePointer: Int = -1

    var isLightAttackPressed: Boolean = false
        private set
    private var lightAttackPointer: Int = -1

    var isHeavyAttackPressed: Boolean = false
        private set
    private var heavyAttackPointer: Int = -1

    var isEnergyPressed: Boolean = false
        private set
    private var energyPointer: Int = -1

    var isLockOnPressed: Boolean = false
        private set
    private var lockOnPointer: Int = -1

    var isUltimatePressed: Boolean = false
        private set
    private var ultimatePointer: Int = -1

    var isHakiPressed: Boolean = false
        private set
    private var hakiPointer: Int = -1

    var isSprintActive: Boolean = false
        private set

    // Responsive button bounds (updated on resize)
    val lightAttackButton = CircleButtonBounds()
    val heavyAttackButton = CircleButtonBounds()
    val energyAttackButton = CircleButtonBounds()
    val dodgeButton = CircleButtonBounds()
    val jumpButton = CircleButtonBounds()
    val sprintButton = CircleButtonBounds()
    val lockOnButton = CircleButtonBounds()
    val ultimateButton = CircleButtonBounds()
    val hakiButton = CircleButtonBounds()

    val weaponStanceButton = RectButtonBounds()
    val camModeButton = RectButtonBounds()
    val resetButton = RectButtonBounds()
    val audioButton = RectButtonBounds()

    // Optional Build Mode ghost preview finger-drag adjustment
    private var buildDragPointer: Int = -1
    private var lastBuildDragX: Float = 0f
    private var lastBuildDragY: Float = 0f
    var isBuildModeActive: () -> Boolean = { false }
    var onBuildDragDelta: ((deltaRight: Float, deltaForward: Float) -> Unit)? = null

    var onTouchIntercept: ((Float, Float) -> Boolean)? = null
    var isModalOpen: () -> Boolean = { false }

    fun updateLayout(width: Int, height: Int) {
        screenWidth = width.toFloat().coerceAtLeast(1f)
        screenHeight = height.toFloat().coerceAtLeast(1f)

        val unit = (screenHeight / 720f).coerceIn(0.65f, 2.2f)
        joystickMaxRadius = 94f * unit

        if (joystickPointer == -1) {
            joystickOriginX = 180f * unit
            joystickOriginY = 170f * unit
            joystickKnobX = joystickOriginX
            joystickKnobY = joystickOriginY
        }

        // Ergonomic Right-Side Anime Action-RPG Combat Cluster
        // Primary Light Attack button (anchor of the thumb arc)
        lightAttackButton.radius = 60f * unit
        lightAttackButton.centerX = screenWidth - 205f * unit
        lightAttackButton.centerY = 148f * unit

        // Jump button (bottom-right corner)
        jumpButton.radius = 48f * unit
        jumpButton.centerX = screenWidth - 82f * unit
        jumpButton.centerY = 98f * unit

        // Dodge / Dash button (lower-left of primary ATK button)
        dodgeButton.radius = 48f * unit
        dodgeButton.centerX = screenWidth - 332f * unit
        dodgeButton.centerY = 92f * unit

        // Heavy Attack button (upper-left arc above ATK)
        heavyAttackButton.radius = 50f * unit
        heavyAttackButton.centerX = screenWidth - 305f * unit
        heavyAttackButton.centerY = 235f * unit

        // Energy / Magic Attack button (upper-right arc above ATK)
        energyAttackButton.radius = 52f * unit
        energyAttackButton.centerX = screenWidth - 180f * unit
        energyAttackButton.centerY = 278f * unit

        // Sprint button (far right middle-low arc)
        sprintButton.radius = 42f * unit
        sprintButton.centerX = screenWidth - 74f * unit
        sprintButton.centerY = 216f * unit

        // Dedicated Target Lock-On button (upper arc above Sprint / right of Energy)
        lockOnButton.radius = 44f * unit
        lockOnButton.centerX = screenWidth - 78f * unit
        lockOnButton.centerY = 330f * unit

        // Dedicated DIVINE ANNIHILATION Ultimate button (upper-left arc above Heavy/Energy)
        ultimateButton.radius = 50f * unit
        ultimateButton.centerX = screenWidth - 295f * unit
        ultimateButton.centerY = 352f * unit

        // Dedicated Prediction Haki Button (distinct arc left of Dodge / below Heavy)
        hakiButton.radius = 46f * unit
        hakiButton.centerX = screenWidth - 410f * unit
        hakiButton.centerY = 168f * unit

        // Top-Right Utility Pill Row (Weapon Stance, Camera Mode, Reset, Audio)
        val pillH = 44f * unit
        val topY = screenHeight - 62f * unit
        val gap = 10f * unit

        resetButton.width = 128f * unit
        resetButton.height = pillH
        resetButton.x = screenWidth - resetButton.width - 22f * unit
        resetButton.y = topY

        camModeButton.width = 122f * unit
        camModeButton.height = pillH
        camModeButton.x = resetButton.x - camModeButton.width - gap
        camModeButton.y = topY

        weaponStanceButton.width = 144f * unit
        weaponStanceButton.height = pillH
        weaponStanceButton.x = camModeButton.x - weaponStanceButton.width - gap
        weaponStanceButton.y = topY

        audioButton.width = 104f * unit
        audioButton.height = pillH
        audioButton.x = weaponStanceButton.x - audioButton.width - gap
        audioButton.y = topY
    }

    fun consumeJumpRequest(): Boolean {
        val req = jumpRequested
        jumpRequested = false
        return req
    }

    fun consumeDodgeRequest(): Boolean {
        val req = dodgeRequested
        dodgeRequested = false
        return req
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val x = screenX.toFloat()
        val y = screenHeight - screenY.toFloat() // Convert to Y-up

        // Check if survival HUD overlay or open modals intercept the touch
        if (onTouchIntercept?.invoke(x, y) == true) {
            return true
        }

        // 1. Check top-right utility pill buttons
        if (weaponStanceButton.contains(x, y)) {
            onToggleWeaponStance()
            return true
        }
        if (camModeButton.contains(x, y)) {
            onToggleCameraMode()
            return true
        }
        if (resetButton.contains(x, y)) {
            isSprintActive = false
            onResetRequested()
            return true
        }
        if (audioButton.contains(x, y)) {
            onToggleAudio()
            return true
        }

        // 2. Check bottom-right Combat & Movement Action Buttons
        if (ultimateButton.contains(x, y)) {
            ultimatePointer = pointer
            isUltimatePressed = true
            onUltimateAttack()
            return true
        }
        if (lockOnButton.contains(x, y)) {
            lockOnPointer = pointer
            isLockOnPressed = true
            onToggleLockOn()
            return true
        }
        if (lightAttackButton.contains(x, y)) {
            lightAttackPointer = pointer
            isLightAttackPressed = true
            onLightAttack()
            return true
        }
        if (heavyAttackButton.contains(x, y)) {
            heavyAttackPointer = pointer
            isHeavyAttackPressed = true
            onHeavyAttack()
            return true
        }
        if (energyAttackButton.contains(x, y)) {
            energyPointer = pointer
            isEnergyPressed = true
            onEnergyPress()
            return true
        }
        if (dodgeButton.contains(x, y)) {
            dodgePointer = pointer
            isDodgePressed = true
            dodgeRequested = true
            return true
        }
        if (hakiButton.contains(x, y)) {
            hakiPointer = pointer
            isHakiPressed = true
            onTogglePredictionHaki()
            return true
        }
        if (jumpButton.contains(x, y)) {
            jumpPointer = pointer
            isJumpPressed = true
            jumpRequested = true
            return true
        }
        if (sprintButton.contains(x, y)) {
            isSprintActive = !isSprintActive
            onSprintStateChanged(isSprintActive)
            return true
        }

        // 3. Left half of screen -> Dynamic Floating Virtual Joystick
        if (x < screenWidth * 0.45f && joystickPointer == -1) {
            joystickPointer = pointer
            val margin = joystickMaxRadius + 24f
            joystickOriginX = x.coerceIn(margin, screenWidth * 0.43f)
            joystickOriginY = y.coerceIn(margin, screenHeight * 0.65f)
            joystickKnobX = joystickOriginX
            joystickKnobY = joystickOriginY
            moveX = 0f
            moveY = 0f
            return true
        }

        // 3b. Center build zone when Build Mode is active -> Finger-drag Ghost Preview position adjustment
        if (isBuildModeActive() &&
            x in (screenWidth * 0.45f)..(screenWidth * 0.66f) &&
            y in (screenHeight * 0.32f)..(screenHeight * 0.82f) &&
            buildDragPointer == -1
        ) {
            buildDragPointer = pointer
            lastBuildDragX = x
            lastBuildDragY = y
            return true
        }

        // 4. Right half of screen -> Camera Look control
        if (lookPointer == -1) {
            lookPointer = pointer
            lastLookX = x
            lastLookY = y
            return true
        }

        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (isModalOpen()) return true
        val x = screenX.toFloat()
        val y = screenHeight - screenY.toFloat()

        if (pointer == joystickPointer) {
            val dx = x - joystickOriginX
            val dy = y - joystickOriginY
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > 0.001f) {
                val clamped = dist.coerceAtMost(joystickMaxRadius)
                val nx = dx / dist
                val ny = dy / dist
                joystickKnobX = joystickOriginX + nx * clamped
                joystickKnobY = joystickOriginY + ny * clamped
                moveX = (nx * (clamped / joystickMaxRadius)).coerceIn(-1f, 1f)
                moveY = (ny * (clamped / joystickMaxRadius)).coerceIn(-1f, 1f)
            } else {
                joystickKnobX = joystickOriginX
                joystickKnobY = joystickOriginY
                moveX = 0f
                moveY = 0f
            }
            if (moveX * moveX + moveY * moveY < 0.02f) {
                isSprintActive = false
            }
            return true
        }

        if (pointer == buildDragPointer) {
            val dx = x - lastBuildDragX
            val dy = y - lastBuildDragY
            lastBuildDragX = x
            lastBuildDragY = y
            onBuildDragDelta?.invoke(dx * 0.015f, dy * 0.015f)
            return true
        }

        if (pointer == lookPointer) {
            val dx = x - lastLookX
            val dy = y - lastLookY
            lastLookX = x
            lastLookY = y
            onLookDelta(-dx * lookSensitivityDegPerPx, -dy * lookSensitivityDegPerPx)
            return true
        }

        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer == joystickPointer) {
            joystickPointer = -1
            moveX = 0f
            moveY = 0f
            isSprintActive = false
            val unit = (screenHeight / 720f).coerceIn(0.65f, 2.2f)
            joystickOriginX = 180f * unit
            joystickOriginY = 170f * unit
            joystickKnobX = joystickOriginX
            joystickKnobY = joystickOriginY
            return true
        }

        if (pointer == buildDragPointer) {
            buildDragPointer = -1
            return true
        }

        if (pointer == lookPointer) {
            lookPointer = -1
            return true
        }

        if (pointer == ultimatePointer) {
            ultimatePointer = -1
            isUltimatePressed = false
            return true
        }

        if (pointer == lockOnPointer) {
            lockOnPointer = -1
            isLockOnPressed = false
            return true
        }

        if (pointer == hakiPointer) {
            hakiPointer = -1
            isHakiPressed = false
            return true
        }

        if (pointer == lightAttackPointer) {
            lightAttackPointer = -1
            isLightAttackPressed = false
            return true
        }

        if (pointer == heavyAttackPointer) {
            heavyAttackPointer = -1
            isHeavyAttackPressed = false
            return true
        }

        if (pointer == energyPointer) {
            energyPointer = -1
            isEnergyPressed = false
            onEnergyRelease()
            return true
        }

        if (pointer == dodgePointer) {
            dodgePointer = -1
            isDodgePressed = false
            return true
        }

        if (pointer == jumpPointer) {
            jumpPointer = -1
            isJumpPressed = false
            return true
        }

        return false
    }
}
