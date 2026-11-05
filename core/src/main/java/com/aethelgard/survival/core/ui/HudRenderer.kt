package com.aethelgard.survival.core.ui

import com.aethelgard.survival.core.camera.CameraMode
import com.aethelgard.survival.core.combat.CombatSystem
import com.aethelgard.survival.core.combat.DivineAnnihilationController
import com.aethelgard.survival.core.combat.IncantationVisualStyle
import com.aethelgard.survival.core.combat.UltimatePhase
import com.aethelgard.survival.core.entities.EnemyAiState
import com.aethelgard.survival.core.entities.EnemyController
import com.aethelgard.survival.core.input.CircleButtonBounds
import com.aethelgard.survival.core.input.RectButtonBounds
import com.aethelgard.survival.core.input.TouchInputController
import com.aethelgard.survival.core.player.PlayerCharacter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import java.util.Locale
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders the development telemetry HUD, player HP & Energy bars, enemy health & status bar,
 * floating 3D-projected damage numbers, dynamic floating joystick, and right-side combat buttons
 * with live cooldown and energy-charge indicators.
 */
class HudRenderer : Disposable {

    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private val glyphLayout = GlyphLayout()
    private val projectionMatrix = Matrix4()
    private val tmpScreenPos = Vector3()

    private lateinit var solidWhiteTex: Texture
    private lateinit var circleRingTex: Texture
    private lateinit var circleDiscTex: Texture
    private lateinit var pillBgTex: Texture

    private var screenWidth = 1280f
    private var screenHeight = 720f
    private var uiScale = 1f

    fun initialize() {
        batch = SpriteBatch()
        font = BitmapFont()
        font.setUseIntegerPositions(false)
        font.region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)

        solidWhiteTex = createSolidWhiteTexture()
        circleRingTex = createCircleRingTexture(128)
        circleDiscTex = createCircleDiscTexture(128)
        pillBgTex = createRoundedPillTexture(160, 56)
    }

    fun resize(width: Int, height: Int) {
        screenWidth = width.toFloat().coerceAtLeast(1f)
        screenHeight = height.toFloat().coerceAtLeast(1f)
        uiScale = (screenHeight / 720f).coerceIn(0.65f, 2.2f)
        projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        batch.projectionMatrix = projectionMatrix
    }

    fun render(
        camera: PerspectiveCamera,
        player: PlayerCharacter,
        cameraMode: CameraMode,
        input: TouchInputController,
        combatSystem: CombatSystem,
        primaryEnemy: EnemyController?,
        lockedTarget: EnemyController?,
        isAudioEnabled: Boolean,
        survivalOverlay: com.aethelgard.survival.core.survival.SurvivalHudOverlay? = null,
        survivalAttributes: com.aethelgard.survival.core.survival.SurvivalAttributes? = null,
        inventory: com.aethelgard.survival.core.survival.Inventory? = null,
        chestManager: com.aethelgard.survival.core.survival.ChestManager? = null,
        craftingManager: com.aethelgard.survival.core.survival.CraftingManager? = null,
        nearestNode: com.aethelgard.survival.core.survival.HarvestableNode? = null,
        buildingManager: com.aethelgard.survival.core.building.BuildingManager? = null,
        nearestBuildingPiece: com.aethelgard.survival.core.building.PlacedBuildingPiece? = null,
        isHammerEquipped: Boolean = false,
        dayNightCycle: com.aethelgard.survival.core.world.DayNightCycle? = null,
        predictionHakiController: com.aethelgard.survival.core.combat.PredictionHakiController? = null
    ) {
        val isLockedOn = lockedTarget != null && lockedTarget.aiState != EnemyAiState.DEAD
        batch.projectionMatrix = projectionMatrix
        batch.begin()

        // 1. Top-Left Development & Player Status HUD Panel
        val panelX = 20f * uiScale
        val panelW = 395f * uiScale
        val panelH = 152f * uiScale
        val panelY = screenHeight - panelH - 14f * uiScale

        batch.setColor(0.05f, 0.08f, 0.18f, 0.76f)
        batch.draw(pillBgTex, panelX, panelY, panelW, panelH)

        batch.setColor(0.22f, 0.85f, 0.98f, 0.88f)
        batch.draw(solidWhiteTex, panelX + 16f * uiScale, panelY + panelH - 4f * uiScale, panelW - 32f * uiScale, 2.5f * uiScale)

        font.data.setScale(1.04f * uiScale)
        font.color = Color(0.38f, 0.88f, 1.0f, 1f)
        val stanceLabel = if (player.isSwordEquipped) "SWORD" else "MARTIAL ARTS"
        val hakiStatus = if (predictionHakiController?.isActive == true) " | HAKI ON (${predictionHakiController.remainingCharges}/8)" else ""
        font.draw(
            batch,
            "AETHELGARD  |  FPS: ${Gdx.graphics.framesPerSecond}  |  $stanceLabel$hakiStatus",
            panelX + 16f * uiScale,
            panelY + panelH - 14f * uiScale
        )

        font.data.setScale(0.94f * uiScale)
        font.color = Color(0.92f, 0.96f, 1.0f, 0.95f)
        val camStatusLabel = if (isLockedOn) "${cameraMode.label} [LOCK]" else cameraMode.label
        val posStr = String.format(
            Locale.US,
            "POS X: %5.1f  Y: %4.1f  Z: %5.1f  |  %s",
            player.position.x,
            player.position.y,
            player.position.z,
            camStatusLabel
        )
        font.draw(batch, posStr, panelX + 16f * uiScale, panelY + panelH - 38f * uiScale)

        font.color = Color(0.75f, 0.86f, 0.98f, 0.92f)
        val curCombo = combatSystem.activeComboStep(player)
        val maxCombo = combatSystem.maxComboSteps(player)
        val comboText = if (curCombo > 0) "  |  COMBO $curCombo/$maxCombo" else ""
        font.draw(
            batch,
            "STATE: ${player.animationState.displayName}$comboText",
            panelX + 16f * uiScale,
            panelY + panelH - 61f * uiScale
        )

        // Player HP Bar
        drawStatusBar(
            x = panelX + 16f * uiScale,
            y = panelY + 46f * uiScale,
            width = panelW - 32f * uiScale,
            height = 16f * uiScale,
            ratio = (player.health / player.maxHealth).coerceIn(0f, 1f),
            fillColor = Color(0.18f, 0.88f, 0.56f, 0.95f),
            label = "HP ${player.health.toInt()}/${player.maxHealth.toInt()}"
        )

        // Player Mana / Energy Bar
        val energyFill = if (combatSystem.isChargingEnergy) {
            Color(0.98f, 0.84f, 0.25f, 0.98f)
        } else {
            Color(0.22f, 0.78f, 0.98f, 0.95f)
        }
        val energyLabel = if (combatSystem.isChargingEnergy) {
            "CHARGING BLAST ${(combatSystem.chargeRatio * 100).toInt()}%"
        } else {
            "ENERGY ${player.energy.toInt()}/${player.maxEnergy.toInt()}"
        }
        drawStatusBar(
            x = panelX + 16f * uiScale,
            y = panelY + 18f * uiScale,
            width = panelW - 32f * uiScale,
            height = 16f * uiScale,
            ratio = (player.energy / player.maxEnergy).coerceIn(0f, 1f),
            fillColor = energyFill,
            label = energyLabel
        )

        // 2. Enemy Target Status Bar & Lock-On Target Reticle (prioritizes lockedTarget)
        val displayEnemy = lockedTarget ?: primaryEnemy
        if (displayEnemy != null) {
            renderEnemyStatusOverlay(camera, displayEnemy, isLockedOn)
        }

        // 3. Floating 3D-Projected Damage Popups
        renderDamagePopups(camera, combatSystem)

        // 4. Top-Right Utility Pill Row (WPN Stance, CAM, RESET, SFX)
        drawRectPillButton(
            bounds = input.weaponStanceButton,
            label = if (player.isSwordEquipped) "WPN: SWORD" else "WPN: FISTS",
            isHighlighted = player.isSwordEquipped
        )
        drawRectPillButton(
            bounds = input.camModeButton,
            label = if (cameraMode == CameraMode.THIRD_PERSON) "CAM: 3P" else "CAM: 1P",
            isHighlighted = cameraMode == CameraMode.FIRST_PERSON
        )
        drawRectPillButton(
            bounds = input.resetButton,
            label = "RESET",
            isHighlighted = false
        )
        drawRectPillButton(
            bounds = input.audioButton,
            label = if (isAudioEnabled) "SFX: ON" else "SFX: OFF",
            isHighlighted = isAudioEnabled
        )

        // 5. Left-Side Dynamic Floating Virtual Joystick
        val isJoystickActive = input.joystickPointer != -1
        val ringAlpha = if (isJoystickActive) 0.88f else 0.36f
        val r = input.joystickMaxRadius

        batch.setColor(0.22f, 0.82f, 0.98f, ringAlpha)
        batch.draw(
            circleRingTex,
            input.joystickOriginX - r,
            input.joystickOriginY - r,
            r * 2f,
            r * 2f
        )

        val knobR = r * 0.42f
        batch.setColor(
            if (isJoystickActive) 0.35f else 0.20f,
            if (isJoystickActive) 0.92f else 0.70f,
            1.0f,
            if (isJoystickActive) 0.92f else 0.45f
        )
        batch.draw(
            circleDiscTex,
            input.joystickKnobX - knobR,
            input.joystickKnobY - knobR,
            knobR * 2f,
            knobR * 2f
        )

        if (!isJoystickActive) {
            drawCenteredText(
                "MOVE",
                input.joystickOriginX,
                input.joystickOriginY - r - 12f * uiScale,
                0.88f * uiScale,
                Color(0.68f, 0.86f, 0.98f, 0.65f)
            )
        }

        // 6. Right-Side Combat & Movement Action Buttons
        val atkLabel = if (player.isSwordEquipped) "SLASH" else "ATK"
        drawCircleCombatButton(
            bounds = input.lightAttackButton,
            title = atkLabel,
            subtitle = if (curCombo > 0) "HIT $curCombo/$maxCombo" else "COMBO",
            isActive = input.isLightAttackPressed || combatSystem.isAttacking,
            cooldownRatio = 0f,
            accentColor = Color(0.28f, 0.92f, 1.0f, 1f)
        )

        val curHvyCd = combatSystem.activeHeavyCooldown(player)
        val maxHvyCd = combatSystem.activeHeavyMaxCooldown(player)
        val hvyCooldownRatio = (curHvyCd / maxHvyCd).coerceIn(0f, 1f)
        val hvyReadyLabel = if (player.isSwordEquipped) {
            if (!player.isGrounded) "SKY STRIKE" else combatSystem.swordCombatController.nextHeavySkillLabel
        } else {
            combatSystem.fistsCombatController.resolveHeavySkillHudLabel(player)
        }
        drawCircleCombatButton(
            bounds = input.heavyAttackButton,
            title = "HEAVY",
            subtitle = if (hvyCooldownRatio > 0f) String.format(Locale.US, "%.1fs", curHvyCd) else hvyReadyLabel,
            isActive = input.isHeavyAttackPressed,
            cooldownRatio = hvyCooldownRatio,
            accentColor = Color(0.98f, 0.62f, 0.22f, 1f)
        )

        val nrgCooldownRatio = (combatSystem.energyCooldown / combatSystem.energyMaxCooldown).coerceIn(0f, 1f)
        val nrgSubtitle = when {
            combatSystem.isChargingEnergy -> "${(combatSystem.chargeRatio * 100).toInt()}%"
            nrgCooldownRatio > 0f -> String.format(Locale.US, "%.1fs", combatSystem.energyCooldown)
            else -> "TAP/HOLD"
        }
        drawCircleCombatButton(
            bounds = input.energyAttackButton,
            title = "ENERGY",
            subtitle = nrgSubtitle,
            isActive = input.isEnergyPressed || combatSystem.isChargingEnergy,
            cooldownRatio = nrgCooldownRatio,
            accentColor = if (combatSystem.isChargingEnergy) Color(1.0f, 0.90f, 0.28f, 1f) else Color(0.75f, 0.42f, 1.0f, 1f)
        )

        val curDashCd = combatSystem.activeDodgeCooldown(player)
        val maxDashCd = combatSystem.activeDodgeMaxCooldown(player)
        val dashCooldownRatio = (curDashCd / maxDashCd).coerceIn(0f, 1f)
        val dashTitle = if (player.isSwordEquipped) "FLASH" else "DASH"
        val dashSub = if (dashCooldownRatio > 0f) {
            String.format(Locale.US, "%.1fs", curDashCd)
        } else if (player.isSwordEquipped) {
            "STEP"
        } else {
            "DODGE"
        }
        drawCircleCombatButton(
            bounds = input.dodgeButton,
            title = dashTitle,
            subtitle = dashSub,
            isActive = input.isDodgePressed || player.isDodging,
            cooldownRatio = dashCooldownRatio,
            accentColor = Color(0.24f, 0.94f, 0.78f, 1f)
        )

        drawCircleCombatButton(
            bounds = input.jumpButton,
            title = "JUMP",
            subtitle = if (!player.isGrounded) "AIR" else "",
            isActive = input.isJumpPressed || !player.isGrounded,
            cooldownRatio = 0f,
            accentColor = Color(0.38f, 0.86f, 0.98f, 1f)
        )

        drawCircleCombatButton(
            bounds = input.sprintButton,
            title = "SPR",
            subtitle = if (input.isSprintActive) "ON" else "OFF",
            isActive = input.isSprintActive,
            cooldownRatio = 0f,
            accentColor = Color(0.98f, 0.82f, 0.24f, 1f)
        )

        drawCircleCombatButton(
            bounds = input.lockOnButton,
            title = "LOCK",
            subtitle = if (isLockedOn) "ON" else "TARGET",
            isActive = isLockedOn || input.isLockOnPressed,
            cooldownRatio = 0f,
            accentColor = if (isLockedOn) Color(1.0f, 0.86f, 0.24f, 1f) else Color(0.45f, 0.88f, 1.0f, 1f)
        )

        // Dedicated DIVINE ANNIHILATION Ultimate Ability Button with 15.0s post-completion cooldown indicator
        val ultController = combatSystem.divineAnnihilationController
        val ultCd = ultController.cooldownRemaining
        val ultCooldownRatio = (ultCd / DivineAnnihilationController.COOLDOWN_DURATION).coerceIn(0f, 1f)
        val ultSubtitle = when {
            ultController.isActive -> "CASTING"
            ultCd > 0f -> String.format(Locale.US, "%.1fs", ultCd)
            else -> "DIVINE"
        }
        drawCircleCombatButton(
            bounds = input.ultimateButton,
            title = "ULT",
            subtitle = ultSubtitle,
            isActive = input.isUltimatePressed || ultController.isActive,
            cooldownRatio = ultCooldownRatio,
            accentColor = if (ultController.isActive) {
                Color(0.88f, 0.96f, 1.0f, 1f)
            } else {
                Color(0.68f, 0.44f, 1.0f, 1f)
            }
        )

        // Dedicated Prediction Haki Button: Displays "HAKI", charges remaining ("8/8"), or "EXHAUSTED" / cooldown
        val haki = predictionHakiController
        val hakiCharges = haki?.remainingCharges ?: 8
        val hakiActive = haki?.isActive == true
        val hakiExhausted = haki?.isExhausted == true
        val hakiCd = haki?.exhaustionCooldown ?: 0f
        val hakiCooldownRatio = if (hakiExhausted) (hakiCd / 10.0f).coerceIn(0f, 1f) else 0f
        val hakiSubtitle = when {
            hakiExhausted -> String.format(Locale.US, "%.0fs", hakiCd)
            hakiActive -> "$hakiCharges/8"
            else -> "$hakiCharges/8"
        }
        val hakiAccent = when {
            hakiExhausted -> Color(0.85f, 0.35f, 0.35f, 0.95f)
            hakiActive -> Color(0.25f, 0.85f, 1.0f, 1f)
            else -> Color(0.35f, 0.70f, 0.95f, 0.85f)
        }
        drawCircleCombatButton(
            bounds = input.hakiButton,
            title = "HAKI",
            subtitle = hakiSubtitle,
            isActive = hakiActive || input.isHakiPressed,
            cooldownRatio = hakiCooldownRatio,
            accentColor = hakiAccent
        )

        // 7. Subtle center reticle in First-Person mode or while Charging Energy
        if (cameraMode == CameraMode.FIRST_PERSON || combatSystem.isChargingEnergy) {
            val cx = screenWidth * 0.5f
            val cy = screenHeight * 0.5f
            val cr = (if (combatSystem.isChargingEnergy) 14f + combatSystem.chargeRatio * 10f else 8f) * uiScale
            batch.setColor(0.38f, 0.92f, 1.0f, 0.78f)
            batch.draw(circleRingTex, cx - cr, cy - cr, cr * 2f, cr * 2f)
        }

        // 8. Render Survival Mode Overlay (Meters, Hotbar, Build Mode, Hammer Bar, Modals, Floating notices)
        if (survivalOverlay != null && survivalAttributes != null && inventory != null && chestManager != null && craftingManager != null) {
            survivalOverlay.render(
                batch = batch,
                font = font,
                solidWhiteTex = solidWhiteTex,
                pillBgTex = pillBgTex,
                attributes = survivalAttributes,
                inventory = inventory,
                chestManager = chestManager,
                craftingManager = craftingManager,
                nearestNode = nearestNode,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                uiScale = uiScale,
                buildingManager = buildingManager,
                nearestBuildingPiece = nearestBuildingPiece,
                isHammerEquipped = isHammerEquipped,
                dayNightCycle = dayNightCycle
            )
        }

        // 9. Render DIVINE ANNIHILATION Cinematic Incantation Overlay & Convergence Flash
        renderDivineAnnihilationCinematicOverlay(ultController)

        batch.setColor(Color.WHITE)
        batch.end()
    }

    private fun renderEnemyStatusOverlay(
        camera: PerspectiveCamera,
        enemy: EnemyController,
        isLockedOn: Boolean
    ) {
        // Top-Center Enemy Boss / Target Bar
        val barW = 350f * uiScale
        val barH = 56f * uiScale
        val barX = (screenWidth - barW) * 0.5f + 28f * uiScale
        val barY = screenHeight - barH - 70f * uiScale

        batch.setColor(0.08f, 0.05f, 0.14f, if (isLockedOn) 0.90f else 0.80f)
        batch.draw(pillBgTex, barX, barY, barW, barH)

        if (isLockedOn) {
            batch.setColor(1.0f, 0.86f, 0.26f, 0.94f)
            batch.draw(solidWhiteTex, barX + 16f * uiScale, barY + barH - 3.5f * uiScale, barW - 32f * uiScale, 2.5f * uiScale)
        }

        val isSlime = enemy.entity.name.contains("Slime", ignoreCase = true)
        val isBlueFlower = enemy.entity.name.contains("Blue Flower", ignoreCase = true)
        val isRedFlower = enemy.entity.name.contains("Red Flower", ignoreCase = true)
        val enemyDisplayName = when {
            isSlime -> "GREEN SLIME"
            isBlueFlower -> "BLUE FLOWER MONSTER"
            isRedFlower -> "RED FLOWER MONSTER"
            else -> "ELITE CORRUPTED KNIGHT"
        }
        val hpRatio = (enemy.health / enemy.maxHealth).coerceIn(0f, 1f)
        val lockPrefix = if (isLockedOn) "[LOCKED] " else ""
        val furyPrefix = if (enemy.isFuryPhase) "[FURY PHASE] " else ""
        val skillSuffix = if (enemy.activeSkillName.isNotEmpty() && enemy.aiState != EnemyAiState.DEAD) {
            "  •  ${enemy.activeSkillName}"
        } else ""
        val title = if (enemy.aiState == EnemyAiState.DEAD) {
            String.format(Locale.US, "$enemyDisplayName DEFEATED (RESPAWN %.1fs)", enemy.respawnCountdown)
        } else {
            "${furyPrefix}${lockPrefix}$enemyDisplayName  [${enemy.aiState.displayLabel}]$skillSuffix"
        }

        val titleColor = when {
            enemy.hitFlashTimer > 0f -> Color(1f, 0.95f, 0.75f, 1f)
            enemy.isFuryPhase -> Color(1.0f, 0.35f, 0.35f, 1f)
            isLockedOn -> Color(1.0f, 0.88f, 0.32f, 1f)
            isSlime -> Color(0.28f, 0.96f, 0.52f, 1f)
            isBlueFlower -> Color(0.38f, 0.78f, 1.0f, 1f)
            isRedFlower -> Color(1.0f, 0.42f, 0.46f, 1f)
            else -> Color(0.98f, 0.65f, 0.75f, 1f)
        }

        drawCenteredText(
            text = title,
            centerX = barX + barW * 0.5f,
            centerY = barY + barH - 10f * uiScale,
            scale = 0.82f * uiScale,
            color = titleColor
        )

        val barFillColor = when {
            enemy.hitFlashTimer > 0f -> Color.WHITE
            enemy.isFuryPhase -> Color(1.0f, 0.18f, 0.22f, 0.98f)
            isSlime -> Color(0.18f, 0.88f, 0.42f, 0.95f)
            isBlueFlower -> Color(0.22f, 0.62f, 1.0f, 0.95f)
            isRedFlower -> Color(0.96f, 0.20f, 0.28f, 0.95f)
            else -> Color(0.96f, 0.26f, 0.46f, 0.95f)
        }

        drawStatusBar(
            x = barX + 18f * uiScale,
            y = barY + 10f * uiScale,
            width = barW - 36f * uiScale,
            height = 15f * uiScale,
            ratio = hpRatio,
            fillColor = barFillColor,
            label = "${enemy.health.toInt()} / ${enemy.maxHealth.toInt()} HP"
        )

        // Draw floating world-space health bar & Lock-On Target Marker above/on the enemy
        if (enemy.aiState != EnemyAiState.DEAD) {
            val headHeight = if (isSlime || isBlueFlower || isRedFlower) 1.35f else 2.35f
            tmpScreenPos.set(enemy.entity.position.x, enemy.entity.position.y + headHeight, enemy.entity.position.z)
            val camToEnemyX = tmpScreenPos.x - camera.position.x
            val camToEnemyY = tmpScreenPos.y - camera.position.y
            val camToEnemyZ = tmpScreenPos.z - camera.position.z
            val dot = camToEnemyX * camera.direction.x + camToEnemyY * camera.direction.y + camToEnemyZ * camera.direction.z
            if (dot > 0.2f) {
                camera.project(tmpScreenPos)
                val sx = tmpScreenPos.x
                val sy = tmpScreenPos.y
                if (sx in 60f..(screenWidth - 60f) && sy in 60f..(screenHeight - 60f)) {
                    val miniW = (if (isLockedOn) 124f else 104f) * uiScale
                    val miniH = (if (isLockedOn) 10f else 8f) * uiScale
                    batch.setColor(0.05f, 0.05f, 0.12f, 0.88f)
                    batch.draw(solidWhiteTex, sx - miniW * 0.5f, sy, miniW, miniH)
                    val miniColor = when {
                        isSlime -> Color(0.20f, 0.90f, 0.44f, 0.95f)
                        isBlueFlower -> Color(0.24f, 0.68f, 1.0f, 0.95f)
                        isRedFlower -> Color(0.98f, 0.22f, 0.30f, 0.95f)
                        else -> Color(0.98f, 0.24f, 0.44f, 0.95f)
                    }
                    batch.setColor(miniColor)
                    batch.draw(solidWhiteTex, sx - miniW * 0.5f + 1f, sy + 1f, (miniW - 2f) * hpRatio, miniH - 2f)

                    if (isLockedOn) {
                        drawCenteredText(
                            text = "▼ TARGET LOCKED ▼",
                            centerX = sx,
                            centerY = sy + miniH + 16f * uiScale,
                            scale = 0.78f * uiScale,
                            color = Color(1.0f, 0.88f, 0.28f, 0.98f)
                        )
                    }
                }
            }

            // Project 2D Lock-On Target Reticle onto enemy center when Locked On
            if (isLockedOn) {
                val reticleY = if (isSlime) 0.45f else 1.15f
                tmpScreenPos.set(enemy.entity.position.x, enemy.entity.position.y + reticleY, enemy.entity.position.z)
                val cx = tmpScreenPos.x - camera.position.x
                val cy = tmpScreenPos.y - camera.position.y
                val cz = tmpScreenPos.z - camera.position.z
                if (cx * camera.direction.x + cy * camera.direction.y + cz * camera.direction.z > 0.2f) {
                    camera.project(tmpScreenPos)
                    val rx = tmpScreenPos.x
                    val ry = tmpScreenPos.y
                    val retR = 32f * uiScale
                    batch.setColor(1.0f, 0.86f, 0.22f, 0.92f)
                    batch.draw(circleRingTex, rx - retR, ry - retR, retR * 2f, retR * 2f)

                    // Cardinal reticle ticks
                    val tickLen = 10f * uiScale
                    val tickThick = 2.5f * uiScale
                    batch.setColor(0.32f, 0.96f, 1.0f, 0.95f)
                    batch.draw(solidWhiteTex, rx - retR - tickLen * 0.5f, ry - tickThick * 0.5f, tickLen, tickThick)
                    batch.draw(solidWhiteTex, rx + retR - tickLen * 0.5f, ry - tickThick * 0.5f, tickLen, tickThick)
                    batch.draw(solidWhiteTex, rx - tickThick * 0.5f, ry - retR - tickLen * 0.5f, tickThick, tickLen)
                    batch.draw(solidWhiteTex, rx - tickThick * 0.5f, ry + retR - tickLen * 0.5f, tickThick, tickLen)
                }
            }
        }
    }

    private fun renderDamagePopups(camera: PerspectiveCamera, combatSystem: CombatSystem) {
        for (pop in combatSystem.damagePopups) {
            tmpScreenPos.set(pop.worldPosition)
            val dx = tmpScreenPos.x - camera.position.x
            val dy = tmpScreenPos.y - camera.position.y
            val dz = tmpScreenPos.z - camera.position.z
            val dot = dx * camera.direction.x + dy * camera.direction.y + dz * camera.direction.z
            if (dot > 0.2f) {
                camera.project(tmpScreenPos)
                val alpha = (1f - (pop.age / pop.maxLifetime)).coerceIn(0f, 1f)
                val scale = (if (pop.isCriticalOrHeavy) 1.38f else 1.12f) * uiScale
                val color = if (pop.isCriticalOrHeavy) {
                    Color(1.0f, 0.90f, 0.28f, alpha)
                } else {
                    Color(0.38f, 0.95f, 1.0f, alpha)
                }
                drawCenteredText(pop.text, tmpScreenPos.x, tmpScreenPos.y, scale, color)
            }
        }
    }

    private fun drawStatusBar(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        ratio: Float,
        fillColor: Color,
        label: String
    ) {
        batch.setColor(0.02f, 0.04f, 0.10f, 0.88f)
        batch.draw(solidWhiteTex, x, y, width, height)

        batch.setColor(fillColor.r, fillColor.g, fillColor.b, fillColor.a)
        batch.draw(solidWhiteTex, x + 2f * uiScale, y + 2f * uiScale, (width - 4f * uiScale) * ratio, height - 4f * uiScale)

        drawCenteredText(
            text = label,
            centerX = x + width * 0.5f,
            centerY = y + height - 3f * uiScale,
            scale = 0.78f * uiScale,
            color = Color.WHITE
        )
    }

    private fun drawCircleCombatButton(
        bounds: CircleButtonBounds,
        title: String,
        subtitle: String,
        isActive: Boolean,
        cooldownRatio: Float,
        accentColor: Color
    ) {
        val r = bounds.radius
        val isReady = cooldownRatio <= 0.01f

        if (isActive) {
            batch.setColor(accentColor.r, accentColor.g, accentColor.b, 0.45f)
        } else if (!isReady) {
            batch.setColor(0.04f, 0.05f, 0.10f, 0.72f)
        } else {
            batch.setColor(0.06f, 0.10f, 0.22f, 0.66f)
        }
        batch.draw(circleDiscTex, bounds.centerX - r, bounds.centerY - r, r * 2f, r * 2f)

        // Cooldown fill overlay
        if (!isReady) {
            val cdR = r * cooldownRatio
            batch.setColor(0.95f, 0.30f, 0.40f, 0.32f)
            batch.draw(circleDiscTex, bounds.centerX - cdR, bounds.centerY - cdR, cdR * 2f, cdR * 2f)
        }

        // Outer glowing border ring
        if (isActive) {
            batch.setColor(accentColor.r, accentColor.g, accentColor.b, 0.98f)
        } else if (!isReady) {
            batch.setColor(0.45f, 0.50f, 0.62f, 0.50f)
        } else {
            batch.setColor(accentColor.r, accentColor.g, accentColor.b, 0.82f)
        }
        batch.draw(circleRingTex, bounds.centerX - r, bounds.centerY - r, r * 2f, r * 2f)

        val titleColor = if (!isReady) Color(0.62f, 0.68f, 0.78f, 0.85f) else Color.WHITE
        if (subtitle.isNotEmpty()) {
            drawCenteredText(title, bounds.centerX, bounds.centerY + 11f * uiScale, 0.96f * uiScale, titleColor)
            drawCenteredText(
                subtitle,
                bounds.centerX,
                bounds.centerY - 7f * uiScale,
                0.74f * uiScale,
                Color(accentColor.r, accentColor.g, accentColor.b, 0.92f)
            )
        } else {
            drawCenteredText(title, bounds.centerX, bounds.centerY + 5f * uiScale, 0.98f * uiScale, titleColor)
        }
    }

    private fun drawRectPillButton(
        bounds: RectButtonBounds,
        label: String,
        isHighlighted: Boolean
    ) {
        if (isHighlighted) {
            batch.setColor(0.14f, 0.38f, 0.58f, 0.88f)
        } else {
            batch.setColor(0.06f, 0.10f, 0.22f, 0.76f)
        }
        batch.draw(pillBgTex, bounds.x, bounds.y, bounds.width, bounds.height)

        if (isHighlighted) {
            batch.setColor(0.38f, 0.94f, 1.0f, 0.96f)
        } else {
            batch.setColor(0.24f, 0.68f, 0.90f, 0.65f)
        }
        batch.draw(solidWhiteTex, bounds.x + 12f * uiScale, bounds.y + bounds.height - 3f * uiScale, bounds.width - 24f * uiScale, 2f * uiScale)

        val textColor = if (isHighlighted) Color(0.65f, 0.98f, 1.0f, 1f) else Color(0.88f, 0.94f, 1.0f, 0.95f)
        drawCenteredText(
            text = label,
            centerX = bounds.x + bounds.width * 0.5f,
            centerY = bounds.y + bounds.height * 0.5f + 5f * uiScale,
            scale = 0.92f * uiScale,
            color = textColor
        )
    }

    private fun drawCenteredText(
        text: String,
        centerX: Float,
        centerY: Float,
        scale: Float,
        color: Color
    ) {
        font.data.setScale(scale)
        font.color = color
        glyphLayout.setText(font, text)
        font.draw(batch, glyphLayout, centerX - glyphLayout.width * 0.5f, centerY)
    }

    /**
     * Renders the dramatic on-screen incantation dialogue (strictly one sentence at a time)
     * and the convergence/beam flash overlay for DIVINE ANNIHILATION.
     */
    private fun renderDivineAnnihilationCinematicOverlay(ult: DivineAnnihilationController) {
        if (ult.screenFlashAlpha > 0.01f) {
            val flashAlpha = (ult.screenFlashAlpha * 0.52f).coerceIn(0f, 0.65f)
            batch.setColor(0.86f, 0.92f, 1.0f, flashAlpha)
            batch.draw(solidWhiteTex, 0f, 0f, screenWidth, screenHeight)
        }

        if (!ult.isActive) return

        // Subtle top/bottom cinematic letterbox bars during ultimate execution
        val barH = 26f * uiScale
        batch.setColor(0.02f, 0.01f, 0.06f, 0.72f)
        batch.draw(solidWhiteTex, 0f, 0f, screenWidth, barH)
        batch.draw(solidWhiteTex, 0f, screenHeight - barH, screenWidth, barH)

        // Render active incantation sentence (strictly one sentence at a time)
        val step = ult.activeIncantationStep ?: return
        val progress = ult.activeIncantationProgress.coerceIn(0f, 1f)
        val fadeAlpha = when {
            progress < 0.14f -> (progress / 0.14f).coerceIn(0.15f, 1f)
            progress > 0.88f && step.style != IncantationVisualStyle.FINAL_CATACLYSM ->
                ((1f - progress) / 0.12f).coerceIn(0.15f, 1f)
            else -> 1f
        }

        val bannerW = (screenWidth * 0.66f).coerceAtLeast(480f * uiScale)
        val bannerH = (if (step.style == IncantationVisualStyle.FINAL_CATACLYSM) 92f else 74f) * uiScale
        val bannerX = (screenWidth - bannerW) * 0.5f
        val bannerY = screenHeight * 0.22f

        // Backdrop & glowing dual-energy border keyed to the sentence's visual theme
        val (bgColor, borderTopColor, borderBottomColor, glowColor, mainTextColor) = when (step.style) {
            IncantationVisualStyle.LIGHT_BLUE -> listOf(
                Color(0.04f, 0.10f, 0.24f, 0.86f * fadeAlpha),
                Color(0.55f, 0.92f, 1.0f, 0.96f * fadeAlpha),
                Color(0.20f, 0.72f, 1.0f, 0.88f * fadeAlpha),
                Color(0.24f, 0.78f, 1.0f, 0.65f * fadeAlpha),
                Color(0.96f, 0.99f, 1.0f, fadeAlpha)
            )
            IncantationVisualStyle.DARK_PURPLE -> listOf(
                Color(0.05f, 0.01f, 0.11f, 0.90f * fadeAlpha),
                Color(0.68f, 0.20f, 0.98f, 0.96f * fadeAlpha),
                Color(0.38f, 0.06f, 0.68f, 0.90f * fadeAlpha),
                Color(0.58f, 0.14f, 0.92f, 0.72f * fadeAlpha),
                Color(0.92f, 0.80f, 1.0f, fadeAlpha)
            )
            IncantationVisualStyle.DUAL_CONVERGENCE -> listOf(
                Color(0.05f, 0.04f, 0.18f, 0.88f * fadeAlpha),
                Color(0.45f, 0.90f, 1.0f, 0.96f * fadeAlpha),
                Color(0.72f, 0.24f, 1.0f, 0.96f * fadeAlpha),
                Color(0.55f, 0.55f, 1.0f, 0.72f * fadeAlpha),
                Color(0.98f, 0.96f, 1.0f, fadeAlpha)
            )
            IncantationVisualStyle.FINAL_CATACLYSM -> listOf(
                Color(0.04f, 0.02f, 0.14f, 0.94f * fadeAlpha),
                Color(0.65f, 0.96f, 1.0f, 1.0f * fadeAlpha),
                Color(0.78f, 0.26f, 1.0f, 1.0f * fadeAlpha),
                Color(0.42f, 0.84f, 1.0f, 0.85f * fadeAlpha),
                Color(1.0f, 1.0f, 1.0f, fadeAlpha)
            )
        }

        batch.setColor(bgColor)
        batch.draw(pillBgTex, bannerX, bannerY, bannerW, bannerH)

        // Top Light/Blue lightning trim & bottom Dark/Purple abyss trim
        batch.setColor(borderTopColor)
        batch.draw(solidWhiteTex, bannerX + 18f * uiScale, bannerY + bannerH - 3.5f * uiScale, bannerW - 36f * uiScale, 3.5f * uiScale)
        batch.setColor(borderBottomColor)
        batch.draw(solidWhiteTex, bannerX + 18f * uiScale, bannerY, bannerW - 36f * uiScale, 3.5f * uiScale)

        // Left & Right dual-energy emblem discs inside the banner
        val emblemR = 16f * uiScale
        val emblemY = bannerY + bannerH * 0.5f
        batch.setColor(0.85f, 0.96f, 1.0f, 0.90f * fadeAlpha)
        batch.draw(circleDiscTex, bannerX + 28f * uiScale - emblemR, emblemY - emblemR, emblemR * 2f, emblemR * 2f)
        batch.setColor(0.56f, 0.16f, 0.94f, 0.90f * fadeAlpha)
        batch.draw(circleDiscTex, bannerX + bannerW - 28f * uiScale - emblemR, emblemY - emblemR, emblemR * 2f, emblemR * 2f)

        val pulse = 1.0f + sin(ult.elapsedTime * 16f) * (if (step.style == IncantationVisualStyle.FINAL_CATACLYSM) 0.06f else 0.025f)
        val baseScale = if (step.style == IncantationVisualStyle.FINAL_CATACLYSM) 1.72f * uiScale else 1.34f * uiScale
        val textScale = baseScale * pulse
        val centerX = screenWidth * 0.5f
        val textY = bannerY + bannerH * 0.58f

        // Multi-pass outer glow halo around the incantation sentence
        val glowOffset = 2.2f * uiScale
        drawCenteredText(step.sentence, centerX - glowOffset, textY - glowOffset, textScale, glowColor)
        drawCenteredText(step.sentence, centerX + glowOffset, textY + glowOffset, textScale, borderBottomColor)
        drawCenteredText(step.sentence, centerX, textY, textScale, mainTextColor)

        // Phase header caption above the incantation line
        val headerLabel = if (step.style == IncantationVisualStyle.FINAL_CATACLYSM) {
            "ULTIMATE CONVERGENCE  •  LIGHT & ABYSS UNLEASHED"
        } else {
            "DIVINE ANNIHILATION  •  ${ult.currentPhase.label}"
        }
        drawCenteredText(
            text = headerLabel,
            centerX = centerX,
            centerY = bannerY + bannerH - 10f * uiScale,
            scale = 0.72f * uiScale,
            color = Color(borderTopColor.r, borderTopColor.g, borderTopColor.b, 0.88f * fadeAlpha)
        )
    }

    private fun createSolidWhiteTexture(): Texture {
        val pm = Pixmap(4, 4, Pixmap.Format.RGBA8888)
        pm.setColor(Color.WHITE)
        pm.fill()
        val tex = Texture(pm)
        pm.dispose()
        return tex
    }

    private fun createCircleRingTexture(size: Int): Texture {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val center = size * 0.5f
        val maxR = size * 0.47f
        val minR = size * 0.40f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x + 0.5f - center
                val dy = y + 0.5f - center
                val d = sqrt(dx * dx + dy * dy)
                if (d in minR..maxR) {
                    val edgeAlpha = 1f - (kotlin.math.abs(d - (minR + maxR) * 0.5f) / ((maxR - minR) * 0.5f)).coerceIn(0f, 1f)
                    pm.setColor(1f, 1f, 1f, (edgeAlpha * 1.3f).coerceIn(0f, 1f))
                    pm.drawPixel(x, y)
                } else if (d < minR) {
                    pm.setColor(1f, 1f, 1f, 0.10f)
                    pm.drawPixel(x, y)
                }
            }
        }
        val tex = Texture(pm)
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pm.dispose()
        return tex
    }

    private fun createCircleDiscTexture(size: Int): Texture {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val center = size * 0.5f
        val maxR = size * 0.46f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x + 0.5f - center
                val dy = y + 0.5f - center
                val d = sqrt(dx * dx + dy * dy)
                if (d <= maxR) {
                    val radial = (1f - (d / maxR) * 0.35f).coerceIn(0f, 1f)
                    val edgeSmooth = ((maxR - d) / 3f).coerceIn(0f, 1f)
                    pm.setColor(1f, 1f, 1f, radial * edgeSmooth)
                    pm.drawPixel(x, y)
                }
            }
        }
        val tex = Texture(pm)
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pm.dispose()
        return tex
    }

    private fun createRoundedPillTexture(w: Int, h: Int): Texture {
        val pm = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val r = h / 2
        pm.setColor(Color.WHITE)
        pm.fillRectangle(r, 0, w - r * 2, h)
        pm.fillCircle(r, r, r - 1)
        pm.fillCircle(w - r, r, r - 1)
        val tex = Texture(pm)
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pm.dispose()
        return tex
    }

    override fun dispose() {
        runCatching { batch.dispose() }
        runCatching { font.dispose() }
        runCatching { solidWhiteTex.dispose() }
        runCatching { circleRingTex.dispose() }
        runCatching { circleDiscTex.dispose() }
        runCatching { pillBgTex.dispose() }
    }
}
