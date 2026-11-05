package com.aethelgard.survival.core.survival

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.building.BuildingCatalog
import com.aethelgard.survival.core.building.BuildingCategory
import com.aethelgard.survival.core.building.BuildingInteractionType
import com.aethelgard.survival.core.building.BuildingManager
import com.aethelgard.survival.core.building.PlacedBuildingPiece
import com.aethelgard.survival.core.combat.CombatSystem
import com.aethelgard.survival.core.input.RectButtonBounds
import com.aethelgard.survival.core.player.PlayerCharacter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import java.util.Locale

enum class SurvivalModalType {
    NONE,
    INVENTORY,
    CRAFTING,
    CHEST
}

data class FloatingPickupNotice(
    val itemId: String,
    val itemName: String,
    var count: Int,
    val color: Color,
    val isSystemMessage: Boolean = false,
    var age: Float = 0f,
    val maxLifetime: Float = 2.4f
) {
    val text: String
        get() = if (isSystemMessage) itemName else "${count}X $itemName"
}

/**
 * Renders the Survival Mode HUD elements, Freeform Building Mode controls, Builder's Hammer panel,
 * and interactive touch modals:
 * - Hunger, Thirst, and Stamina meters
 * - 5-Slot Hotbar with active selection and stack counters
 * - Dedicated BUILD Mode toggle, Category & Piece selector, Ghost Preview status, Rotation, Pillar Height +/-, Snap toggle, Place/Cancel
 * - Builder's Hammer inspection, repair, material upgrade, and safe dismantle controls
 * - Contextual Harvest / Interact action button
 * - Floating resource acquisition notices
 * - Full Terraria-style 25-slot Inventory Modal with item details & action buttons
 * - Crafting Table Modal with recipes & ingredient validation
 * - Storage Chest Modal with dual-inventory transfers
 */
class SurvivalHudOverlay {

    var activeModal: SurvivalModalType = SurvivalModalType.NONE
    private val glyphLayout = GlyphLayout()

    // Touch interactive button bounds
    val invButton = RectButtonBounds()
    val craftButton = RectButtonBounds()
    val buildButton = RectButtonBounds()
    val contextInteractButton = RectButtonBounds()

    // Freeform Building Mode touch bounds
    val buildPlaceButton = RectButtonBounds()
    val buildRotateButton = RectButtonBounds()
    val buildSnapButton = RectButtonBounds()
    val buildCancelButton = RectButtonBounds()
    val buildHeightMinusButton = RectButtonBounds()
    val buildHeightPlusButton = RectButtonBounds()
    val buildCategoryBounds = Array(BuildingCategory.entries.size) { RectButtonBounds() }
    val buildPieceBounds = mutableListOf<RectButtonBounds>()

    // Builder's Hammer action bounds
    val hammerRepairButton = RectButtonBounds()
    val hammerUpgradeButton = RectButtonBounds()
    val hammerDismantleButton = RectButtonBounds()

    // Modal close button
    val modalCloseButton = RectButtonBounds()

    // Hotbar touch bounds (5 slots)
    val hotbarSlotBounds = Array(5) { RectButtonBounds() }

    // Inventory modal slot bounds (25 slots)
    val invModalSlotBounds = Array(25) { RectButtonBounds() }

    // Chest modal slot bounds (20 chest slots + 25 player slots)
    val chestSlotBounds = Array(20) { RectButtonBounds() }

    // Modal action buttons
    val useItemButton = RectButtonBounds()
    val dropItemButton = RectButtonBounds()
    val craftExecuteButton = RectButtonBounds()
    val storeAllButton = RectButtonBounds()
    val takeAllButton = RectButtonBounds()

    // Recipe selection bounds
    val recipeBounds = mutableListOf<RectButtonBounds>()

    var selectedInvSlot: Int = -1
    var selectedChestSlot: Int = -1
    var selectedRecipeIndex: Int = 0

    var currentScreenWidth: Float = 1280f
        private set
    var currentScreenHeight: Float = 720f
        private set
    var currentUiScale: Float = 1f
        private set

    val notifications = mutableListOf<FloatingPickupNotice>()
    val iconRegistry = PixelArtIconRegistry()

    fun dispose() {
        iconRegistry.dispose()
    }

    fun updateLayout(screenWidth: Float, screenHeight: Float, uiScale: Float) {
        currentScreenWidth = screenWidth
        currentScreenHeight = screenHeight
        currentUiScale = uiScale

        val pillH = 40f * uiScale
        val topY = screenHeight - 110f * uiScale

        // Top-Left Navigation / Modal Buttons (placed cleanly below system panel)
        invButton.x = 20f * uiScale
        invButton.y = topY
        invButton.width = 96f * uiScale
        invButton.height = pillH

        craftButton.x = invButton.x + invButton.width + 8f * uiScale
        craftButton.y = topY
        craftButton.width = 104f * uiScale
        craftButton.height = pillH

        buildButton.x = craftButton.x + craftButton.width + 8f * uiScale
        buildButton.y = topY
        buildButton.width = 96f * uiScale
        buildButton.height = pillH

        // 5-Slot Hotbar (centered horizontally at the top, between player status and utility row)
        val hotbarSlotW = 54f * uiScale
        val hotbarSlotH = 50f * uiScale
        val hotbarGap = 8f * uiScale
        val totalHotbarW = 5 * hotbarSlotW + 4 * hotbarGap
        val hotbarStartX = (screenWidth - totalHotbarW) * 0.5f

        for (i in 0 until 5) {
            hotbarSlotBounds[i].x = hotbarStartX + i * (hotbarSlotW + hotbarGap)
            hotbarSlotBounds[i].y = screenHeight - hotbarSlotH - 12f * uiScale
            hotbarSlotBounds[i].width = hotbarSlotW
            hotbarSlotBounds[i].height = hotbarSlotH
        }

        // Contextual Interact Button (bottom-right above combat & ultimate arc)
        contextInteractButton.x = screenWidth - 360f * uiScale
        contextInteractButton.y = 420f * uiScale
        contextInteractButton.width = 180f * uiScale
        contextInteractButton.height = 48f * uiScale

        // Freeform Building Mode Bottom-Center Control Drawer (between left joystick and right combat cluster)
        val drawerX = 310f * uiScale
        val drawerW = (screenWidth - 670f * uiScale).coerceAtLeast(360f * uiScale)
        val catCount = BuildingCategory.entries.size
        val catGap = 3f * uiScale
        val catW = (drawerW - (catCount - 1) * catGap) / catCount
        val catH = 28f * uiScale
        val catY = 148f * uiScale
        for (i in 0 until catCount) {
            buildCategoryBounds[i].x = drawerX + i * (catW + catGap)
            buildCategoryBounds[i].y = catY
            buildCategoryBounds[i].width = catW
            buildCategoryBounds[i].height = catH
        }

        // Build Action Row at bottom of drawer (y = 14f * uiScale)
        val actY = 14f * uiScale
        val actH = 38f * uiScale
        val actGap = 6f * uiScale
        val actBtnW = (drawerW - 5 * actGap) / 6f

        buildPlaceButton.set(drawerX, actY, actBtnW * 1.25f, actH)
        buildRotateButton.set(buildPlaceButton.x + buildPlaceButton.width + actGap, actY, actBtnW * 0.95f, actH)
        buildSnapButton.set(buildRotateButton.x + buildRotateButton.width + actGap, actY, actBtnW * 1.05f, actH)
        buildHeightMinusButton.set(buildSnapButton.x + buildSnapButton.width + actGap, actY, actBtnW * 0.85f, actH)
        buildHeightPlusButton.set(buildHeightMinusButton.x + buildHeightMinusButton.width + actGap, actY, actBtnW * 0.85f, actH)
        buildCancelButton.set(buildHeightPlusButton.x + buildHeightPlusButton.width + actGap, actY, actBtnW * 0.95f, actH)

        // Builder's Hammer Action Bar (bottom-center when holding Builder's Hammer)
        val hmrW = 132f * uiScale
        val hmrH = 40f * uiScale
        val hmrGap = 10f * uiScale
        val hmrTotalW = 3 * hmrW + 2 * hmrGap
        val hmrStartX = (screenWidth - hmrTotalW) * 0.5f
        val hmrY = 22f * uiScale
        hammerRepairButton.set(hmrStartX, hmrY, hmrW, hmrH)
        hammerUpgradeButton.set(hmrStartX + hmrW + hmrGap, hmrY, hmrW, hmrH)
        hammerDismantleButton.set(hmrStartX + 2 * (hmrW + hmrGap), hmrY, hmrW, hmrH)

        // Modal Close Button
        modalCloseButton.width = 50f * uiScale
        modalCloseButton.height = 42f * uiScale
        modalCloseButton.x = screenWidth - 110f * uiScale
        modalCloseButton.y = screenHeight - 65f * uiScale

        // Inventory Modal: 5 columns x 5 rows
        val invStartX = 70f * uiScale
        val invStartY = screenHeight - 170f * uiScale
        val slotDim = 52f * uiScale
        val slotPad = 6f * uiScale

        for (r in 0 until 5) {
            for (c in 0 until 5) {
                val idx = r * 5 + c
                invModalSlotBounds[idx].x = invStartX + c * (slotDim + slotPad)
                invModalSlotBounds[idx].y = invStartY - r * (slotDim + slotPad)
                invModalSlotBounds[idx].width = slotDim
                invModalSlotBounds[idx].height = slotDim
            }
        }

        // Item Detail Action Buttons
        val detailX = invStartX + 5 * (slotDim + slotPad) + 40f * uiScale
        useItemButton.x = detailX
        useItemButton.y = invStartY - 190f * uiScale
        useItemButton.width = 140f * uiScale
        useItemButton.height = 42f * uiScale

        dropItemButton.x = detailX + 155f * uiScale
        dropItemButton.y = invStartY - 190f * uiScale
        dropItemButton.width = 120f * uiScale
        dropItemButton.height = 42f * uiScale

        // Crafting Execute Button
        craftExecuteButton.x = screenWidth - 320f * uiScale
        craftExecuteButton.y = 110f * uiScale
        craftExecuteButton.width = 200f * uiScale
        craftExecuteButton.height = 52f * uiScale

        // Chest Transfer Buttons
        storeAllButton.x = screenWidth * 0.5f - 145f * uiScale
        storeAllButton.y = 95f * uiScale
        storeAllButton.width = 135f * uiScale
        storeAllButton.height = 40f * uiScale

        takeAllButton.x = screenWidth * 0.5f + 10f * uiScale
        takeAllButton.y = 95f * uiScale
        takeAllButton.width = 135f * uiScale
        takeAllButton.height = 40f * uiScale

        // Chest 20 Slots (4 rows x 5 columns)
        val chestStartX = screenWidth * 0.5f + 40f * uiScale
        for (r in 0 until 4) {
            for (c in 0 until 5) {
                val idx = r * 5 + c
                chestSlotBounds[idx].x = chestStartX + c * (slotDim + slotPad)
                chestSlotBounds[idx].y = invStartY - r * (slotDim + slotPad)
                chestSlotBounds[idx].width = slotDim
                chestSlotBounds[idx].height = slotDim
            }
        }
    }

    fun addNotification(itemId: String, count: Int) {
        val def = ResourceCatalog.get(itemId)
        val color = when (def?.category) {
            ItemCategory.WOOD -> Color(0.95f, 0.78f, 0.42f, 1f)
            ItemCategory.MINERAL -> Color(0.82f, 0.88f, 0.96f, 1f)
            ItemCategory.NATURE -> Color(0.45f, 0.96f, 0.58f, 1f)
            ItemCategory.MAGIC -> Color(0.78f, 0.62f, 1.0f, 1f)
            else -> Color(0.38f, 0.92f, 1.0f, 1f)
        }

        if (def != null && count > 0) {
            val existing = notifications.firstOrNull { it.itemId == itemId && !it.isSystemMessage }
            if (existing != null) {
                existing.count += count
                existing.age = 0f
                return
            }
            notifications.add(
                FloatingPickupNotice(
                    itemId = def.id,
                    itemName = def.name,
                    count = count,
                    color = color,
                    isSystemMessage = false
                )
            )
        } else {
            val sysText = if (def != null && count < 0) "${count}X ${def.name}" else itemId
            notifications.add(
                FloatingPickupNotice(
                    itemId = itemId,
                    itemName = sysText,
                    count = count,
                    color = color,
                    isSystemMessage = true
                )
            )
        }
    }

    fun update(delta: Float) {
        val iter = notifications.iterator()
        while (iter.hasNext()) {
            val n = iter.next()
            n.age += delta
            if (n.age >= n.maxLifetime) {
                iter.remove()
            }
        }
    }

    fun render(
        batch: SpriteBatch,
        font: BitmapFont,
        solidWhiteTex: Texture,
        pillBgTex: Texture,
        attributes: SurvivalAttributes,
        inventory: Inventory,
        chestManager: ChestManager,
        craftingManager: CraftingManager,
        nearestNode: HarvestableNode?,
        screenWidth: Float,
        screenHeight: Float,
        uiScale: Float,
        buildingManager: BuildingManager? = null,
        nearestBuildingPiece: PlacedBuildingPiece? = null,
        isHammerEquipped: Boolean = false,
        dayNightCycle: com.aethelgard.survival.core.world.DayNightCycle? = null
    ) {
        updateLayout(screenWidth, screenHeight, uiScale)

        // 1. Survival Attributes Panel (Hunger, Thirst, Stamina)
        // Rendered below the top-left player stats panel
        val panelX = 20f * uiScale
        val panelW = 395f * uiScale
        val panelH = 74f * uiScale
        val panelY = screenHeight - 152f * uiScale - 14f * uiScale - panelH - 8f * uiScale

        batch.setColor(0.04f, 0.07f, 0.16f, 0.78f)
        batch.draw(pillBgTex, panelX, panelY, panelW, panelH)

        // Hunger Bar
        val barW = (panelW - 48f * uiScale) / 3f
        val barH = 12f * uiScale
        val hungerRatio = (attributes.hunger / attributes.maxHunger).coerceIn(0f, 1f)
        drawMiniStatusBar(
            batch = batch,
            font = font,
            solidWhiteTex = solidWhiteTex,
            x = panelX + 16f * uiScale,
            y = panelY + 36f * uiScale,
            w = barW,
            h = barH,
            ratio = hungerRatio,
            color = if (attributes.isStarving) Color(1.0f, 0.25f, 0.25f, 0.95f) else Color(0.96f, 0.65f, 0.20f, 0.95f),
            label = "HUNGER ${attributes.hunger.toInt()}%",
            uiScale = uiScale
        )

        // Thirst Bar
        val thirstRatio = (attributes.thirst / attributes.maxThirst).coerceIn(0f, 1f)
        drawMiniStatusBar(
            batch = batch,
            font = font,
            solidWhiteTex = solidWhiteTex,
            x = panelX + 16f * uiScale + barW + 8f * uiScale,
            y = panelY + 36f * uiScale,
            w = barW,
            h = barH,
            ratio = thirstRatio,
            color = if (attributes.isDehydrated) Color(1.0f, 0.25f, 0.25f, 0.95f) else Color(0.25f, 0.75f, 1.0f, 0.95f),
            label = "THIRST ${attributes.thirst.toInt()}%",
            uiScale = uiScale
        )

        // Stamina Bar
        val staminaRatio = (attributes.stamina / attributes.maxStamina).coerceIn(0f, 1f)
        drawMiniStatusBar(
            batch = batch,
            font = font,
            solidWhiteTex = solidWhiteTex,
            x = panelX + 16f * uiScale + (barW + 8f * uiScale) * 2f,
            y = panelY + 36f * uiScale,
            w = barW,
            h = barH,
            ratio = staminaRatio,
            color = if (attributes.isExhausted) Color(1.0f, 0.35f, 0.25f, 0.95f) else Color(0.25f, 0.92f, 0.55f, 0.95f),
            label = "STAMINA ${attributes.stamina.toInt()}%",
            uiScale = uiScale
        )

        // Status Warnings
        val statusText = when {
            attributes.isStarving && attributes.isDehydrated -> "WARNING: STARVING & DEHYDRATED (-HP / -STAMINA)"
            attributes.isStarving -> "WARNING: STARVING (PASSIVE REGEN HALTED, -HP)"
            attributes.isDehydrated -> "WARNING: DEHYDRATED (STAMINA RECOVERY 25%)"
            attributes.isExhausted -> "EXHAUSTED: SPRINT & DODGE LOCKED"
            else -> "STATUS: NOURISHED & HYDRATED"
        }
        val statusColor = if (attributes.isStarving || attributes.isDehydrated || attributes.isExhausted) {
            Color(1.0f, 0.40f, 0.40f, 0.95f)
        } else {
            Color(0.68f, 0.88f, 1.0f, 0.85f)
        }
        font.data.setScale(0.72f * uiScale)
        font.color = statusColor
        font.draw(batch, statusText, panelX + 16f * uiScale, panelY + 22f * uiScale)

        // 2. Navigation Pills: BAG, CRAFT, BUILD
        drawPillButton(
            batch = batch,
            font = font,
            solidWhiteTex = solidWhiteTex,
            pillBgTex = pillBgTex,
            bounds = invButton,
            label = "BAG (${inventory.slots.count { it != null }}/25)",
            isHighlighted = activeModal == SurvivalModalType.INVENTORY,
            uiScale = uiScale
        )

        val isNearTable = nearestNode?.type == HarvestNodeType.CRAFTING_TABLE ||
            nearestBuildingPiece?.definition?.interactionType == BuildingInteractionType.CRAFTING_STATION
        drawPillButton(
            batch = batch,
            font = font,
            solidWhiteTex = solidWhiteTex,
            pillBgTex = pillBgTex,
            bounds = craftButton,
            label = if (isNearTable) "CRAFT [TABLE]" else "CRAFT",
            isHighlighted = activeModal == SurvivalModalType.CRAFTING || isNearTable,
            uiScale = uiScale
        )

        val buildActive = buildingManager?.isBuildModeActive == true
        drawPillButton(
            batch = batch,
            font = font,
            solidWhiteTex = solidWhiteTex,
            pillBgTex = pillBgTex,
            bounds = buildButton,
            label = if (buildActive) "BUILD [ON]" else "BUILD",
            isHighlighted = buildActive,
            uiScale = uiScale
        )

        // 3. Top-Center Hotbar (5 Slots)
        for (i in 0 until 5) {
            val b = hotbarSlotBounds[i]
            val isSelected = (inventory.activeHotbarIndex == i)
            val item = inventory.slots[i]

            batch.setColor(0.06f, 0.09f, 0.18f, if (isSelected) 0.92f else 0.72f)
            batch.draw(pillBgTex, b.x, b.y, b.width, b.height)

            if (isSelected) {
                batch.setColor(1.0f, 0.85f, 0.25f, 0.96f)
                batch.draw(solidWhiteTex, b.x + 2f, b.y + 2f, b.width - 4f, 2f)
                batch.draw(solidWhiteTex, b.x + 2f, b.y + b.height - 4f, b.width - 4f, 2f)
                batch.draw(solidWhiteTex, b.x + 2f, b.y + 2f, 2f, b.height - 4f)
                batch.draw(solidWhiteTex, b.x + b.width - 4f, b.y + 2f, 2f, b.height - 4f)
            }

            if (item != null) {
                val def = item.definition
                val iconTex = iconRegistry.getOrCreateTexture(item.itemId)
                val iconDim = (b.height - 14f * uiScale).coerceAtLeast(16f * uiScale)
                val iconX = b.x + (b.width - iconDim) * 0.5f
                val iconY = b.y + (b.height - iconDim) * 0.5f + 3f * uiScale
                if (item.isBroken) {
                    batch.setColor(1.0f, 0.45f, 0.45f, 0.85f)
                } else {
                    batch.setColor(1f, 1f, 1f, 1f)
                }
                batch.draw(iconTex, iconX, iconY, iconDim, iconDim)

                if (item.isTool) {
                    val ratio = item.durabilityRatio
                    val durBarW = b.width - 8f * uiScale
                    val durBarH = 4f * uiScale
                    batch.setColor(0.08f, 0.10f, 0.15f, 0.95f)
                    batch.draw(solidWhiteTex, b.x + 4f * uiScale, b.y + 4f * uiScale, durBarW, durBarH)
                    val durColor = when {
                        item.isBroken -> Color(0.95f, 0.20f, 0.20f, 1f)
                        ratio < 0.30f -> Color(1.0f, 0.35f, 0.20f, 1f)
                        ratio < 0.65f -> Color(0.98f, 0.80f, 0.20f, 1f)
                        else -> Color(0.25f, 0.95f, 0.50f, 1f)
                    }
                    batch.setColor(durColor)
                    batch.draw(solidWhiteTex, b.x + 4f * uiScale, b.y + 4f * uiScale, durBarW * ratio, durBarH)

                    font.data.setScale(0.56f * uiScale)
                    font.color = if (item.isBroken) Color(1.0f, 0.35f, 0.35f, 1f) else Color(0.85f, 0.95f, 1.0f, 0.95f)
                    val durLabel = if (item.isBroken) "BRK" else "${item.durability}"
                    font.draw(batch, durLabel, b.x + 5f * uiScale, b.y + 16f * uiScale)
                } else {
                    font.data.setScale(0.68f * uiScale)
                    font.color = Color.WHITE
                    font.draw(batch, "x${item.count}", b.x + b.width - 24f * uiScale, b.y + 14f * uiScale)
                }
            } else {
                font.data.setScale(0.68f * uiScale)
                font.color = Color(0.45f, 0.55f, 0.65f, 0.55f)
                drawCenteredText(batch, font, "${i + 1}", b.x + b.width * 0.5f, b.y + b.height * 0.5f + 4f * uiScale)
            }
        }

        // 3b. Day & 12-Hour AM/PM Clock Badge (Centered directly below the Hotbar)
        if (dayNightCycle != null) {
            val clockW = 210f * uiScale
            val clockH = 28f * uiScale
            val clockX = (screenWidth - clockW) * 0.5f
            val clockY = screenHeight - 50f * uiScale - 12f * uiScale - clockH - 6f * uiScale

            batch.setColor(0.04f, 0.08f, 0.16f, 0.86f)
            batch.draw(pillBgTex, clockX, clockY, clockW, clockH)

            // Accent border line (Golden for daytime, Silvery-Cyan for night)
            val accentColor = if (dayNightCycle.isDaytime) Color(1.0f, 0.85f, 0.25f, 0.92f) else Color(0.48f, 0.72f, 1.0f, 0.92f)
            batch.setColor(accentColor)
            batch.draw(solidWhiteTex, clockX + 12f * uiScale, clockY + clockH - 2f * uiScale, clockW - 24f * uiScale, 1.8f * uiScale)

            font.data.setScale(0.85f * uiScale)
            font.color = Color(0.95f, 0.97f, 1.0f, 0.98f)
            val timeText = "${dayNightCycle.celestialIcon}  ${dayNightCycle.combinedTimeAndDayString}"
            glyphLayout.setText(font, timeText)
            val textX = clockX + (clockW - glyphLayout.width) * 0.5f
            val textY = clockY + (clockH + glyphLayout.height) * 0.5f - 1f * uiScale
            font.draw(batch, timeText, textX, textY)
        }

        // 4. Contextual Interact / Harvest Action Button (World Nodes or Interactive Placed Building Pieces)
        if (activeModal == SurvivalModalType.NONE && !buildActive) {
            if (nearestNode != null) {
                val hpTag = "${nearestNode.health.toInt()}/${nearestNode.maxHealth.toInt()}"
                val label = when (nearestNode.type) {
                    HarvestNodeType.TREE -> "CHOP TREE [$hpTag]"
                    HarvestNodeType.BOULDER -> "MINE BOULDER [$hpTag]"
                    HarvestNodeType.CRYSTAL_BOULDER -> "MINE CRYSTAL ORE [$hpTag]"
                    HarvestNodeType.NATURE_HERB -> "REAP HERBS & FIBERS [$hpTag]"
                    HarvestNodeType.BERRY_BUSH -> "HARVEST BERRIES [$hpTag]"
                    HarvestNodeType.MUSHROOM_PATCH -> "GATHER MUSHROOMS [$hpTag]"
                    HarvestNodeType.ROOT_PATCH -> "DIG ROOTS [$hpTag]"
                    HarvestNodeType.MAGIC_CRYSTAL -> "HARVEST MANA CRYSTAL [$hpTag]"
                    HarvestNodeType.ANCIENT_MONUMENT -> "INTERACT [ANCIENT RELIC]"
                    HarvestNodeType.CRAFTING_TABLE -> "OPEN CRAFTING TABLE"
                    HarvestNodeType.STORAGE_CHEST -> "OPEN STORAGE CHEST"
                }
                val accentColor = when (nearestNode.type) {
                    HarvestNodeType.TREE -> Color(0.95f, 0.68f, 0.25f, 0.95f)
                    HarvestNodeType.BOULDER, HarvestNodeType.CRYSTAL_BOULDER -> Color(0.38f, 0.85f, 1.0f, 0.95f)
                    HarvestNodeType.NATURE_HERB,
                    HarvestNodeType.BERRY_BUSH,
                    HarvestNodeType.MUSHROOM_PATCH,
                    HarvestNodeType.ROOT_PATCH -> Color(0.35f, 0.95f, 0.55f, 0.95f)
                    HarvestNodeType.CRAFTING_TABLE, HarvestNodeType.STORAGE_CHEST -> Color(1.0f, 0.85f, 0.28f, 0.95f)
                    else -> Color(0.85f, 0.55f, 1.0f, 0.95f)
                }
                drawActionPill(
                    batch = batch,
                    font = font,
                    solidWhiteTex = solidWhiteTex,
                    pillBgTex = pillBgTex,
                    bounds = contextInteractButton,
                    label = label,
                    accentColor = accentColor,
                    uiScale = uiScale
                )
            } else if (nearestBuildingPiece != null && nearestBuildingPiece.definition.interactionType != BuildingInteractionType.NONE) {
                val bLabel = when (nearestBuildingPiece.definition.interactionType) {
                    BuildingInteractionType.DOOR_TOGGLE -> if (nearestBuildingPiece.isDoorOpen) "CLOSE ${nearestBuildingPiece.definition.name.uppercase()}" else "OPEN ${nearestBuildingPiece.definition.name.uppercase()}"
                    BuildingInteractionType.SLEEP_BED -> "SLEEP / REST IN BED"
                    BuildingInteractionType.STORAGE_CHEST -> "OPEN PLACED CHEST"
                    BuildingInteractionType.CRAFTING_TABLE -> "USE CRAFTING TABLE"
                    BuildingInteractionType.CHAIR_SIT -> "REST ON ${nearestBuildingPiece.definition.name.uppercase()}"
                    BuildingInteractionType.LIGHT_TOGGLE -> if (nearestBuildingPiece.isLit) "EXTINGUISH ${nearestBuildingPiece.definition.name.uppercase()}" else "LIGHT ${nearestBuildingPiece.definition.name.uppercase()}"
                    BuildingInteractionType.TERRITORY_INSPECT -> "INSPECT TERRITORY WARD"
                    BuildingInteractionType.NONE -> ""
                }
                if (bLabel.isNotEmpty()) {
                    drawActionPill(
                        batch = batch,
                        font = font,
                        solidWhiteTex = solidWhiteTex,
                        pillBgTex = pillBgTex,
                        bounds = contextInteractButton,
                        label = bLabel,
                        accentColor = Color(0.35f, 0.95f, 0.65f, 0.95f),
                        uiScale = uiScale
                    )
                }
            }
        }

        // 4b. Freeform Building Mode Control Drawer OR Builder's Hammer Action Bar
        if (activeModal == SurvivalModalType.NONE) {
            if (buildingManager != null && buildingManager.isBuildModeActive) {
                renderBuildModeDrawer(batch, font, solidWhiteTex, pillBgTex, buildingManager, inventory, uiScale)
            } else if (isHammerEquipped && nearestBuildingPiece != null) {
                renderBuildersHammerPanel(batch, font, solidWhiteTex, pillBgTex, nearestBuildingPiece, uiScale)
            }
        }

        // 5. Resource Collection Notifications (HUD Corner with Pixel Art Icon & "3X Wood" Format)
        var notifY = screenHeight - 286f * uiScale
        for (n in notifications) {
            val progress = (n.age / n.maxLifetime).coerceIn(0f, 1f)
            val fadeStart = 0.65f
            val alpha = if (progress <= fadeStart) {
                1.0f
            } else {
                (1.0f - (progress - fadeStart) / (1.0f - fadeStart)).coerceIn(0f, 1f)
            }
            val rowH = 30f * uiScale
            val rowX = 20f * uiScale

            font.data.setScale(0.86f * uiScale)
            glyphLayout.setText(font, n.text)
            val iconSize = if (!n.isSystemMessage) 22f * uiScale else 0f
            val rowW = (iconSize + glyphLayout.width + 28f * uiScale).coerceAtLeast(136f * uiScale)

            batch.setColor(0.04f, 0.08f, 0.16f, 0.82f * alpha)
            batch.draw(pillBgTex, rowX, notifY, rowW, rowH)

            if (!n.isSystemMessage) {
                val iconTex = iconRegistry.getOrCreateTexture(n.itemId)
                batch.setColor(1f, 1f, 1f, alpha)
                batch.draw(
                    iconTex,
                    rowX + 8f * uiScale,
                    notifY + (rowH - iconSize) * 0.5f,
                    iconSize,
                    iconSize
                )
            }

            font.color = Color(n.color.r, n.color.g, n.color.b, alpha)
            val textX = if (!n.isSystemMessage) rowX + 14f * uiScale + iconSize else rowX + 10f * uiScale
            val textY = notifY + (rowH + glyphLayout.height) * 0.5f - 1f * uiScale
            font.draw(batch, n.text, textX, textY)

            notifY -= (rowH + 6f * uiScale)
        }

        // 6. Active Modals (Inventory, Crafting, Chest)
        when (activeModal) {
            SurvivalModalType.INVENTORY -> renderInventoryModal(batch, font, solidWhiteTex, pillBgTex, inventory, screenWidth, screenHeight, uiScale)
            SurvivalModalType.CRAFTING -> renderCraftingModal(batch, font, solidWhiteTex, pillBgTex, craftingManager, inventory, isNearTable, screenWidth, screenHeight, uiScale)
            SurvivalModalType.CHEST -> renderChestModal(batch, font, solidWhiteTex, pillBgTex, chestManager, inventory, screenWidth, screenHeight, uiScale)
            SurvivalModalType.NONE -> { /* No modal open */ }
        }
    }

    private fun renderBuildModeDrawer(
        batch: SpriteBatch,
        font: BitmapFont,
        solidWhiteTex: Texture,
        pillBgTex: Texture,
        buildingManager: BuildingManager,
        inventory: Inventory,
        uiScale: Float
    ) {
        val drawerX = 304f * uiScale
        val drawerW = (currentScreenWidth - 658f * uiScale).coerceAtLeast(372f * uiScale)
        val drawerY = 8f * uiScale
        val drawerH = 176f * uiScale

        // Backdrop
        batch.setColor(0.03f, 0.06f, 0.14f, 0.88f)
        batch.draw(pillBgTex, drawerX, drawerY, drawerW, drawerH)

        // Status banner at top of drawer (Green = valid placement, Red = invalid placement / missing mats)
        val isValid = buildingManager.isGhostValid
        val selPiece = buildingManager.selectedPiece
        val costParts = selPiece.costs.entries.joinToString("  ") { (resId, req) ->
            val have = inventory.countItem(resId)
            val rName = ResourceCatalog.get(resId)?.name ?: resId
            "$rName $have/$req"
        }
        val heightTag = if (selPiece.isAdjustableHeight) String.format(Locale.US, " | H:%.1fm", buildingManager.currentPillarHeight) else ""
        val statusHeader = "${selPiece.name.uppercase()} [${selPiece.material.displayName}$heightTag]  •  COST: $costParts  •  ${buildingManager.ghostInvalidReason}"
        font.data.setScale(0.66f * uiScale)
        font.color = if (isValid) Color(0.32f, 0.98f, 0.52f, 1f) else Color(1.0f, 0.38f, 0.38f, 1f)
        drawCenteredText(batch, font, statusHeader, drawerX + drawerW * 0.5f, drawerY + drawerH - 10f * uiScale)

        // Category Tabs Row
        val categories = BuildingCategory.entries
        for (i in categories.indices) {
            val cat = categories[i]
            val b = buildCategoryBounds[i]
            val isCatSel = (buildingManager.selectedCategory == cat)
            batch.setColor(
                if (isCatSel) 0.18f else 0.07f,
                if (isCatSel) 0.44f else 0.12f,
                if (isCatSel) 0.65f else 0.24f,
                0.92f
            )
            batch.draw(solidWhiteTex, b.x, b.y, b.width, b.height)
            font.data.setScale(0.60f * uiScale)
            font.color = if (isCatSel) Color(0.75f, 0.98f, 1.0f, 1f) else Color(0.78f, 0.86f, 0.95f, 0.85f)
            drawCenteredText(batch, font, cat.shortCode, b.x + b.width * 0.5f, b.y + b.height * 0.5f + 3f * uiScale)
        }

        // Piece Selector Row for Active Category
        val piecesInCat = buildingManager.getPiecesForSelectedCategory()
        buildPieceBounds.clear()
        val innerX = drawerX + 6f * uiScale
        val innerW = drawerW - 12f * uiScale
        val count = piecesInCat.size.coerceAtLeast(1)
        val pGap = 5f * uiScale
        val pW = ((innerW - (count - 1) * pGap) / count).coerceAtMost(145f * uiScale)
        val pH = 44f * uiScale
        val pY = 58f * uiScale

        for (i in piecesInCat.indices) {
            val pieceDef = piecesInCat[i]
            val px = innerX + i * (pW + pGap)
            val bounds = RectButtonBounds(px, pY, pW, pH)
            buildPieceBounds.add(bounds)
            val isSel = (buildingManager.selectedPiece.id == pieceDef.id)
            val canAfford = buildingManager.hasResourcesFor(pieceDef, inventory)

            batch.setColor(
                if (isSel) 0.16f else 0.08f,
                if (isSel) 0.40f else 0.13f,
                if (isSel) 0.58f else 0.25f,
                0.92f
            )
            batch.draw(pillBgTex, bounds.x, bounds.y, bounds.width, bounds.height)

            font.data.setScale(0.62f * uiScale)
            font.color = if (canAfford) Color.WHITE else Color(1.0f, 0.48f, 0.48f, 0.92f)
            drawCenteredText(batch, font, pieceDef.name, bounds.x + bounds.width * 0.5f, bounds.y + bounds.height * 0.66f)

            val shortCost = pieceDef.costs.entries.joinToString(",") { (k, v) -> "${v}${k.take(2).uppercase()}" }
            font.data.setScale(0.55f * uiScale)
            font.color = if (canAfford) Color(0.55f, 0.96f, 0.68f, 0.95f) else Color(1.0f, 0.42f, 0.42f, 0.95f)
            drawCenteredText(batch, font, shortCost, bounds.x + bounds.width * 0.5f, bounds.y + bounds.height * 0.28f)
        }

        // Bottom Action Controls: PLACE, ROTATE 90, SNAP ON/OFF, HEIGHT -/+, CANCEL
        drawPillButton(batch, font, solidWhiteTex, pillBgTex, buildPlaceButton, "PLACE", isValid, uiScale)
        drawPillButton(batch, font, solidWhiteTex, pillBgTex, buildRotateButton, "ROT ${buildingManager.previewRotationYaw.toInt()}°", false, uiScale)
        drawPillButton(
            batch = batch,
            font = font,
            solidWhiteTex = solidWhiteTex,
            pillBgTex = pillBgTex,
            bounds = buildSnapButton,
            label = if (buildingManager.isSnappingEnabled) "SNAP: ON" else "SNAP: OFF",
            isHighlighted = buildingManager.isSnappingEnabled,
            uiScale = uiScale
        )
        val isHeightAdj = selPiece.isAdjustableHeight
        drawPillButton(batch, font, solidWhiteTex, pillBgTex, buildHeightMinusButton, "H -0.5m", isHeightAdj, uiScale)
        drawPillButton(batch, font, solidWhiteTex, pillBgTex, buildHeightPlusButton, "H +0.5m", isHeightAdj, uiScale)
        drawPillButton(batch, font, solidWhiteTex, pillBgTex, buildCancelButton, "EXIT", false, uiScale)
    }

    private fun renderBuildersHammerPanel(
        batch: SpriteBatch,
        font: BitmapFont,
        solidWhiteTex: Texture,
        pillBgTex: Texture,
        targetPiece: PlacedBuildingPiece,
        uiScale: Float
    ) {
        val barX = hammerRepairButton.x - 14f * uiScale
        val barW = (hammerDismantleButton.x + hammerDismantleButton.width) - hammerRepairButton.x + 28f * uiScale
        val barY = 14f * uiScale
        val barH = 74f * uiScale

        batch.setColor(0.04f, 0.08f, 0.18f, 0.88f)
        batch.draw(pillBgTex, barX, barY, barW, barH)

        val info = "HAMMER TARGET: ${targetPiece.definition.name.uppercase()} [${targetPiece.health.toInt()}/${targetPiece.maxHealth.toInt()} HP | ${targetPiece.definition.material.displayName.uppercase()}]"
        font.data.setScale(0.68f * uiScale)
        font.color = Color(0.45f, 0.95f, 1.0f, 1f)
        drawCenteredText(batch, font, info, barX + barW * 0.5f, barY + barH - 12f * uiScale)

        val canRepair = targetPiece.health < targetPiece.maxHealth
        val canUpgrade = targetPiece.definition.material.nextUpgradeTier() != null &&
            BuildingCatalog.findUpgradeTarget(targetPiece.definition) != null
        drawPillButton(batch, font, solidWhiteTex, pillBgTex, hammerRepairButton, "REPAIR", canRepair, uiScale)
        drawPillButton(batch, font, solidWhiteTex, pillBgTex, hammerUpgradeButton, "UPGRADE", canUpgrade, uiScale)
        drawPillButton(batch, font, solidWhiteTex, pillBgTex, hammerDismantleButton, "DISMANTLE", true, uiScale)
    }

    private fun renderInventoryModal(
        batch: SpriteBatch,
        font: BitmapFont,
        solidWhiteTex: Texture,
        pillBgTex: Texture,
        inventory: Inventory,
        screenWidth: Float,
        screenHeight: Float,
        uiScale: Float
    ) {
        // Modal Background Backdrop
        val modalW = screenWidth - 100f * uiScale
        val modalH = screenHeight - 90f * uiScale
        val modalX = 50f * uiScale
        val modalY = 45f * uiScale

        batch.setColor(0.03f, 0.05f, 0.12f, 0.94f)
        batch.draw(solidWhiteTex, modalX, modalY, modalW, modalH)
        batch.setColor(0.24f, 0.82f, 0.98f, 0.90f)
        batch.draw(solidWhiteTex, modalX + 4f, modalY + modalH - 4f, modalW - 8f, 2.5f)

        // Modal Title
        font.data.setScale(1.15f * uiScale)
        font.color = Color(0.38f, 0.88f, 1.0f, 1f)
        font.draw(batch, "TERRARIA INVENTORY  (25 SLOTS)", modalX + 24f * uiScale, modalY + modalH - 18f * uiScale)

        // Close Button
        drawCloseButton(batch, font, solidWhiteTex, modalCloseButton, uiScale)

        // 25 Slots Grid
        for (i in 0 until 25) {
            val b = invModalSlotBounds[i]
            val item = inventory.slots[i]
            val isSelected = (selectedInvSlot == i)
            val isHotbar = (i in 0..4)

            batch.setColor(if (isSelected) 0.18f else 0.08f, if (isSelected) 0.35f else 0.12f, if (isSelected) 0.55f else 0.22f, 0.88f)
            batch.draw(pillBgTex, b.x, b.y, b.width, b.height)

            if (isHotbar) {
                batch.setColor(1.0f, 0.85f, 0.25f, 0.40f)
                batch.draw(solidWhiteTex, b.x + 2f, b.y + b.height - 3f, b.width - 4f, 2f)
            }

            if (item != null) {
                val iconTex = iconRegistry.getOrCreateTexture(item.itemId)
                val iconDim = (b.height - 14f * uiScale).coerceAtLeast(16f * uiScale)
                val iconX = b.x + (b.width - iconDim) * 0.5f
                val iconY = b.y + (b.height - iconDim) * 0.5f + 3f * uiScale
                if (item.isBroken) {
                    batch.setColor(1.0f, 0.45f, 0.45f, 0.85f)
                } else {
                    batch.setColor(1f, 1f, 1f, 1f)
                }
                batch.draw(iconTex, iconX, iconY, iconDim, iconDim)

                if (item.isTool) {
                    val ratio = item.durabilityRatio
                    val durBarW = b.width - 8f * uiScale
                    val durBarH = 4f * uiScale
                    batch.setColor(0.08f, 0.10f, 0.15f, 0.95f)
                    batch.draw(solidWhiteTex, b.x + 4f * uiScale, b.y + 4f * uiScale, durBarW, durBarH)
                    val durColor = when {
                        item.isBroken -> Color(0.95f, 0.20f, 0.20f, 1f)
                        ratio < 0.30f -> Color(1.0f, 0.35f, 0.20f, 1f)
                        ratio < 0.65f -> Color(0.98f, 0.80f, 0.20f, 1f)
                        else -> Color(0.25f, 0.95f, 0.50f, 1f)
                    }
                    batch.setColor(durColor)
                    batch.draw(solidWhiteTex, b.x + 4f * uiScale, b.y + 4f * uiScale, durBarW * ratio, durBarH)

                    font.data.setScale(0.56f * uiScale)
                    font.color = if (item.isBroken) Color(1.0f, 0.35f, 0.35f, 1f) else Color.WHITE
                    val durText = if (item.isBroken) "BRK" else "${item.durability}/${item.maxDurability}"
                    font.draw(batch, durText, b.x + 4f * uiScale, b.y + 15f * uiScale)
                } else {
                    font.data.setScale(0.68f * uiScale)
                    font.color = Color.WHITE
                    font.draw(batch, "${item.count}", b.x + b.width - 20f * uiScale, b.y + 14f * uiScale)
                }
            }
        }

        // Selected Item Detail Card
        val selectedItem = if (selectedInvSlot in 0 until 25) inventory.slots[selectedInvSlot] else null
        val detailX = invModalSlotBounds[4].x + invModalSlotBounds[4].width + 35f * uiScale
        val detailY = modalY + modalH - 70f * uiScale

        if (selectedItem != null) {
            val def = selectedItem.definition
            val detailIconTex = iconRegistry.getOrCreateTexture(selectedItem.itemId)
            val detailIconSize = 36f * uiScale
            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(detailIconTex, detailX, detailY - 26f * uiScale, detailIconSize, detailIconSize)

            font.data.setScale(1.02f * uiScale)
            font.color = Color.valueOf(def?.badgeColorHex ?: "#FFFFFF")
            font.draw(batch, "${def?.name ?: selectedItem.itemId} (x${selectedItem.count})", detailX + detailIconSize + 10f * uiScale, detailY)

            font.data.setScale(0.82f * uiScale)
            font.color = Color(0.75f, 0.85f, 0.95f, 0.90f)
            font.draw(batch, "CATEGORY: ${def?.category?.displayName ?: "Resource"}", detailX + detailIconSize + 10f * uiScale, detailY - 24f * uiScale)
            if (selectedItem.isTool) {
                val statusTag = if (selectedItem.isBroken) " [BROKEN - UNUSABLE]" else ""
                font.color = if (selectedItem.isBroken) Color(1.0f, 0.40f, 0.40f, 1f) else Color(0.35f, 0.95f, 0.55f, 1f)
                font.draw(
                    batch,
                    "DURABILITY: ${selectedItem.durability} / ${selectedItem.maxDurability}$statusTag  |  POWER: ${def?.toolPower}x",
                    detailX,
                    detailY - 48f * uiScale
                )
            } else {
                font.draw(batch, "STACK LIMIT: ${def?.stackLimit ?: 99}", detailX, detailY - 48f * uiScale)
            }

            font.data.setScale(0.78f * uiScale)
            font.color = Color(0.92f, 0.96f, 1.0f, 0.95f)
            font.draw(batch, "DESCRIPTION: ${def?.description ?: ""}", detailX, detailY - 74f * uiScale)
            font.draw(batch, "SOURCE: ${def?.sourceDescription ?: ""}", detailX, detailY - 98f * uiScale)

            // Action Buttons: USE / EAT / REPAIR TOOL / EQUIP HOTBAR, DROP
            val canConsume = def?.category == ItemCategory.CONSUMABLE ||
                (def?.hungerRestore ?: 0f) > 0f ||
                (def?.thirstRestore ?: 0f) > 0f ||
                (def?.healthRestore ?: 0f) > 0f ||
                (def?.energyRestore ?: 0f) > 0f ||
                (def?.staminaRestore ?: 0f) > 0f
            val primaryLabel = when {
                canConsume -> "USE / EAT"
                selectedItem.isTool && selectedItem.durability < selectedItem.maxDurability -> "REPAIR (1 MAT)"
                else -> "EQUIP HOTBAR"
            }
            drawPillButton(batch, font, solidWhiteTex, pillBgTex, useItemButton, primaryLabel, true, uiScale)
            drawPillButton(batch, font, solidWhiteTex, pillBgTex, dropItemButton, "DROP (x1)", false, uiScale)
        } else {
            font.data.setScale(0.88f * uiScale)
            font.color = Color(0.60f, 0.70f, 0.85f, 0.70f)
            font.draw(batch, "Select an item slot to view details, repair tools, consume, or drop.", detailX, detailY)
        }
    }

    private fun renderCraftingModal(
        batch: SpriteBatch,
        font: BitmapFont,
        solidWhiteTex: Texture,
        pillBgTex: Texture,
        craftingManager: CraftingManager,
        inventory: Inventory,
        isNearCraftingTable: Boolean,
        screenWidth: Float,
        screenHeight: Float,
        uiScale: Float
    ) {
        val modalW = screenWidth - 100f * uiScale
        val modalH = screenHeight - 90f * uiScale
        val modalX = 50f * uiScale
        val modalY = 45f * uiScale

        batch.setColor(0.03f, 0.05f, 0.12f, 0.94f)
        batch.draw(solidWhiteTex, modalX, modalY, modalW, modalH)
        batch.setColor(0.96f, 0.75f, 0.28f, 0.90f)
        batch.draw(solidWhiteTex, modalX + 4f, modalY + modalH - 4f, modalW - 8f, 2.5f)

        font.data.setScale(1.15f * uiScale)
        font.color = Color(1.0f, 0.85f, 0.32f, 1f)
        val title = if (isNearCraftingTable) "CRAFTING TABLE  [NEAR WORKBENCH]" else "HAND CRAFTING  [SURVIVAL TOOLS & BASICS]"
        font.draw(batch, title, modalX + 24f * uiScale, modalY + modalH - 18f * uiScale)

        drawCloseButton(batch, font, solidWhiteTex, modalCloseButton, uiScale)

        // Recipe List on the left (2 columns of up to 8 rows = 16 recipes)
        val availableRecipes = craftingManager.getAvailableRecipes(isNearCraftingTable)
        recipeBounds.clear()

        val listX = modalX + 20f * uiScale
        val topListY = modalY + modalH - 62f * uiScale
        val colW = 215f * uiScale
        val recipeH = 38f * uiScale
        val rowGap = 6f * uiScale
        val colGap = 10f * uiScale

        for (i in availableRecipes.indices) {
            val r = availableRecipes[i]
            val col = i / 8
            val row = i % 8
            val rx = listX + col * (colW + colGap)
            val ry = topListY - row * (recipeH + rowGap)
            val isSelected = (selectedRecipeIndex == i)
            val canCraft = craftingManager.canCraft(r, inventory, isNearCraftingTable)
            val bounds = RectButtonBounds(rx, ry, colW, recipeH)
            recipeBounds.add(bounds)

            batch.setColor(if (isSelected) 0.22f else 0.08f, if (isSelected) 0.38f else 0.12f, if (isSelected) 0.52f else 0.20f, 0.88f)
            batch.draw(pillBgTex, bounds.x, bounds.y, bounds.width, bounds.height)

            val def = r.resultDefinition
            val recIconTex = iconRegistry.getOrCreateTexture(r.resultItemId)
            val recIconSize = 24f * uiScale
            batch.setColor(1f, 1f, 1f, if (canCraft) 1f else 0.55f)
            batch.draw(recIconTex, bounds.x + 8f * uiScale, bounds.y + (bounds.height - recIconSize) * 0.5f, recIconSize, recIconSize)

            font.data.setScale(0.74f * uiScale)
            font.color = if (canCraft) Color.WHITE else Color(0.65f, 0.65f, 0.70f, 0.70f)
            font.draw(batch, "${def?.name ?: r.resultItemId} x${r.resultCount}", bounds.x + 8f * uiScale + recIconSize + 6f * uiScale, bounds.y + bounds.height * 0.5f + 5f * uiScale)
        }

        // Selected Recipe Detail & Requirements Checklist on the right
        val selectedRecipe = availableRecipes.getOrNull(selectedRecipeIndex)
        val colsUsed = if (availableRecipes.size > 8) 2 else 1
        val detailX = listX + colsUsed * (colW + colGap) + 20f * uiScale
        val detailY = modalY + modalH - 65f * uiScale

        if (selectedRecipe != null) {
            val def = selectedRecipe.resultDefinition
            font.data.setScale(1.05f * uiScale)
            font.color = Color(1.0f, 0.88f, 0.35f, 1f)
            font.draw(batch, "CRAFT: ${def?.name ?: selectedRecipe.resultItemId}", detailX, detailY)

            font.data.setScale(0.82f * uiScale)
            font.color = Color(0.85f, 0.92f, 1.0f, 0.95f)
            font.draw(batch, selectedRecipe.description, detailX, detailY - 26f * uiScale)

            font.data.setScale(0.92f * uiScale)
            font.color = Color(0.38f, 0.88f, 1.0f, 1f)
            font.draw(batch, "REQUIRED INGREDIENTS:", detailX, detailY - 60f * uiScale)

            var reqY = detailY - 86f * uiScale
            for ((matId, count) in selectedRecipe.ingredients) {
                val matDef = ResourceCatalog.get(matId)
                val inBag = inventory.countItem(matId)
                val hasEnough = inBag >= count
                val checkmark = if (hasEnough) "[OK]" else "[MISSING]"

                font.data.setScale(0.85f * uiScale)
                font.color = if (hasEnough) Color(0.35f, 0.95f, 0.55f, 1f) else Color(1.0f, 0.40f, 0.40f, 1f)
                font.draw(batch, "• ${matDef?.name ?: matId}: $inBag / $count  $checkmark", detailX + 10f * uiScale, reqY)
                reqY -= 22f * uiScale
            }

            // Craft Execution Button
            val canCraft = craftingManager.canCraft(selectedRecipe, inventory, isNearCraftingTable)
            drawPillButton(
                batch = batch,
                font = font,
                solidWhiteTex = solidWhiteTex,
                pillBgTex = pillBgTex,
                bounds = craftExecuteButton,
                label = if (canCraft) "CRAFT ITEM" else "MISSING RESOURCES",
                isHighlighted = canCraft,
                uiScale = uiScale
            )
        }
    }

    private fun renderChestModal(
        batch: SpriteBatch,
        font: BitmapFont,
        solidWhiteTex: Texture,
        pillBgTex: Texture,
        chestManager: ChestManager,
        inventory: Inventory,
        screenWidth: Float,
        screenHeight: Float,
        uiScale: Float
    ) {
        val modalW = screenWidth - 100f * uiScale
        val modalH = screenHeight - 90f * uiScale
        val modalX = 50f * uiScale
        val modalY = 45f * uiScale

        batch.setColor(0.03f, 0.05f, 0.12f, 0.94f)
        batch.draw(solidWhiteTex, modalX, modalY, modalW, modalH)
        batch.setColor(0.96f, 0.65f, 0.20f, 0.90f)
        batch.draw(solidWhiteTex, modalX + 4f, modalY + modalH - 4f, modalW - 8f, 2.5f)

        font.data.setScale(1.15f * uiScale)
        font.color = Color(1.0f, 0.75f, 0.25f, 1f)
        font.draw(batch, "STORAGE CHEST  (TAP TO TRANSFER)", modalX + 24f * uiScale, modalY + modalH - 18f * uiScale)

        drawCloseButton(batch, font, solidWhiteTex, modalCloseButton, uiScale)

        // Left: Player Inventory Slots (20 bag slots)
        font.data.setScale(0.92f * uiScale)
        font.color = Color(0.38f, 0.88f, 1.0f, 1f)
        font.draw(batch, "PLAYER INVENTORY", invModalSlotBounds[0].x, invModalSlotBounds[0].y + invModalSlotBounds[0].height + 18f * uiScale)

        for (i in 0 until 20) {
            val b = invModalSlotBounds[i]
            val item = inventory.slots[i]
            batch.setColor(0.08f, 0.12f, 0.22f, 0.88f)
            batch.draw(pillBgTex, b.x, b.y, b.width, b.height)

            if (item != null) {
                val iconTex = iconRegistry.getOrCreateTexture(item.itemId)
                val iconDim = (b.height - 14f * uiScale).coerceAtLeast(16f * uiScale)
                batch.setColor(1f, 1f, 1f, 1f)
                batch.draw(iconTex, b.x + (b.width - iconDim) * 0.5f, b.y + (b.height - iconDim) * 0.5f + 3f * uiScale, iconDim, iconDim)
                font.data.setScale(0.68f * uiScale)
                font.color = Color.WHITE
                font.draw(batch, "${item.count}", b.x + b.width - 20f * uiScale, b.y + 14f * uiScale)
            }
        }

        // Right: Storage Chest Slots (20 chest slots)
        font.data.setScale(0.92f * uiScale)
        font.color = Color(1.0f, 0.75f, 0.25f, 1f)
        font.draw(batch, "CHEST STORAGE", chestSlotBounds[0].x, chestSlotBounds[0].y + chestSlotBounds[0].height + 18f * uiScale)

        for (i in 0 until 20) {
            val b = chestSlotBounds[i]
            val item = chestManager.chestInventory.slots[i]
            batch.setColor(0.14f, 0.10f, 0.06f, 0.88f)
            batch.draw(pillBgTex, b.x, b.y, b.width, b.height)

            if (item != null) {
                val iconTex = iconRegistry.getOrCreateTexture(item.itemId)
                val iconDim = (b.height - 14f * uiScale).coerceAtLeast(16f * uiScale)
                batch.setColor(1f, 1f, 1f, 1f)
                batch.draw(iconTex, b.x + (b.width - iconDim) * 0.5f, b.y + (b.height - iconDim) * 0.5f + 3f * uiScale, iconDim, iconDim)
                font.data.setScale(0.68f * uiScale)
                font.color = Color.WHITE
                font.draw(batch, "${item.count}", b.x + b.width - 20f * uiScale, b.y + 14f * uiScale)
            }
        }

        // Convenience Transfer Buttons
        drawPillButton(batch, font, solidWhiteTex, pillBgTex, storeAllButton, "STORE BAG", false, uiScale)
        drawPillButton(batch, font, solidWhiteTex, pillBgTex, takeAllButton, "TAKE ALL", true, uiScale)
    }

    private fun drawMiniStatusBar(
        batch: SpriteBatch,
        font: BitmapFont,
        solidWhiteTex: Texture,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        ratio: Float,
        color: Color,
        label: String,
        uiScale: Float
    ) {
        batch.setColor(0.08f, 0.10f, 0.18f, 0.90f)
        batch.draw(solidWhiteTex, x, y, w, h)
        batch.setColor(color)
        batch.draw(solidWhiteTex, x + 1f, y + 1f, (w - 2f) * ratio, h - 2f)

        font.data.setScale(0.62f * uiScale)
        font.color = Color.WHITE
        font.draw(batch, label, x + 4f * uiScale, y + h - 2f * uiScale)
    }

    private fun drawPillButton(
        batch: SpriteBatch,
        font: BitmapFont,
        solidWhiteTex: Texture,
        pillBgTex: Texture,
        bounds: RectButtonBounds,
        label: String,
        isHighlighted: Boolean,
        uiScale: Float
    ) {
        batch.setColor(if (isHighlighted) 0.14f else 0.06f, if (isHighlighted) 0.38f else 0.10f, if (isHighlighted) 0.58f else 0.22f, 0.88f)
        batch.draw(pillBgTex, bounds.x, bounds.y, bounds.width, bounds.height)

        if (isHighlighted) {
            batch.setColor(0.38f, 0.94f, 1.0f, 0.96f)
            batch.draw(solidWhiteTex, bounds.x + 8f * uiScale, bounds.y + bounds.height - 2.5f * uiScale, bounds.width - 16f * uiScale, 2f * uiScale)
        }

        font.data.setScale(0.78f * uiScale)
        font.color = if (isHighlighted) Color(0.65f, 0.98f, 1.0f, 1f) else Color(0.88f, 0.94f, 1.0f, 0.92f)
        drawCenteredText(batch, font, label, bounds.x + bounds.width * 0.5f, bounds.y + bounds.height * 0.5f + 4f * uiScale)
    }

    private fun drawActionPill(
        batch: SpriteBatch,
        font: BitmapFont,
        solidWhiteTex: Texture,
        pillBgTex: Texture,
        bounds: RectButtonBounds,
        label: String,
        accentColor: Color,
        uiScale: Float
    ) {
        batch.setColor(0.06f, 0.10f, 0.24f, 0.92f)
        batch.draw(pillBgTex, bounds.x, bounds.y, bounds.width, bounds.height)

        batch.setColor(accentColor.r, accentColor.g, accentColor.b, 0.95f)
        batch.draw(solidWhiteTex, bounds.x + 8f * uiScale, bounds.y + bounds.height - 3f * uiScale, bounds.width - 16f * uiScale, 2.5f * uiScale)

        font.data.setScale(0.82f * uiScale)
        font.color = Color.WHITE
        drawCenteredText(batch, font, label, bounds.x + bounds.width * 0.5f, bounds.y + bounds.height * 0.5f + 4f * uiScale)
    }

    private fun drawCloseButton(
        batch: SpriteBatch,
        font: BitmapFont,
        solidWhiteTex: Texture,
        bounds: RectButtonBounds,
        uiScale: Float
    ) {
        batch.setColor(0.85f, 0.20f, 0.25f, 0.90f)
        batch.draw(solidWhiteTex, bounds.x, bounds.y, bounds.width, bounds.height)
        font.data.setScale(1.0f * uiScale)
        font.color = Color.WHITE
        drawCenteredText(batch, font, "X", bounds.x + bounds.width * 0.5f, bounds.y + bounds.height * 0.5f + 6f * uiScale)
    }

    private fun drawCenteredText(batch: SpriteBatch, font: BitmapFont, text: String, cx: Float, cy: Float) {
        glyphLayout.setText(font, text)
        font.draw(batch, text, cx - glyphLayout.width * 0.5f, cy + glyphLayout.height * 0.5f)
    }

    fun handleTouch(
        x: Float,
        y: Float,
        inventory: Inventory,
        attributes: SurvivalAttributes,
        craftingManager: CraftingManager,
        chestManager: ChestManager,
        player: PlayerCharacter,
        audioManager: AudioManager,
        isNearCraftingTable: Boolean,
        isNearInteractableNode: Boolean = false,
        buildingManager: BuildingManager? = null,
        nearestBuildingPiece: PlacedBuildingPiece? = null,
        isHammerEquipped: Boolean = false,
        onHammerAction: ((String, PlacedBuildingPiece) -> Unit)? = null,
        onHarvestOrInteract: () -> Unit
    ): Boolean {
        // 1. If no modal is open
        if (activeModal == SurvivalModalType.NONE) {
            if (invButton.contains(x, y)) {
                buildingManager?.isBuildModeActive = false
                activeModal = SurvivalModalType.INVENTORY
                audioManager.playUiClick()
                return true
            }
            if (craftButton.contains(x, y)) {
                buildingManager?.isBuildModeActive = false
                activeModal = SurvivalModalType.CRAFTING
                audioManager.playUiClick()
                return true
            }
            if (buildButton.contains(x, y)) {
                buildingManager?.toggleBuildMode()
                audioManager.playUiClick()
                return true
            }
            for (i in 0 until 5) {
                if (hotbarSlotBounds[i].contains(x, y)) {
                    inventory.activeHotbarIndex = i
                    audioManager.playUiClick()
                    return true
                }
            }

            // Freeform Building Mode controls (when Build Mode is active)
            if (buildingManager != null && buildingManager.isBuildModeActive) {
                val categories = BuildingCategory.entries
                for (i in categories.indices) {
                    if (buildCategoryBounds[i].contains(x, y)) {
                        buildingManager.selectCategory(categories[i])
                        audioManager.playUiClick()
                        return true
                    }
                }
                val piecesInCat = buildingManager.getPiecesForSelectedCategory()
                for (i in buildPieceBounds.indices) {
                    if (buildPieceBounds[i].contains(x, y) && i < piecesInCat.size) {
                        buildingManager.selectPiece(piecesInCat[i])
                        audioManager.playUiClick()
                        return true
                    }
                }
                if (buildPlaceButton.contains(x, y)) {
                    val placed = buildingManager.placePiece(player, inventory, audioManager)
                    if (placed != null) {
                        addNotification("BUILT ${placed.definition.name}", 1)
                    } else {
                        addNotification(buildingManager.ghostInvalidReason, 0)
                    }
                    return true
                }
                if (buildRotateButton.contains(x, y)) {
                    buildingManager.rotatePreview(90f)
                    audioManager.playUiClick()
                    return true
                }
                if (buildSnapButton.contains(x, y)) {
                    buildingManager.toggleSnapping()
                    audioManager.playUiClick()
                    return true
                }
                if (buildHeightMinusButton.contains(x, y)) {
                    if (buildingManager.selectedPiece.isAdjustableHeight) {
                        buildingManager.adjustPillarHeight(-1)
                    }
                    audioManager.playUiClick()
                    return true
                }
                if (buildHeightPlusButton.contains(x, y)) {
                    if (buildingManager.selectedPiece.isAdjustableHeight) {
                        buildingManager.adjustPillarHeight(1)
                    }
                    audioManager.playUiClick()
                    return true
                }
                if (buildCancelButton.contains(x, y)) {
                    buildingManager.isBuildModeActive = false
                    audioManager.playUiClick()
                    return true
                }
                return false
            }

            // Builder's Hammer action bar (when holding Builder's Hammer near a placed piece)
            if (isHammerEquipped && nearestBuildingPiece != null) {
                if (hammerRepairButton.contains(x, y)) {
                    onHammerAction?.invoke("repair", nearestBuildingPiece)
                    return true
                }
                if (hammerUpgradeButton.contains(x, y)) {
                    onHammerAction?.invoke("upgrade", nearestBuildingPiece)
                    return true
                }
                if (hammerDismantleButton.contains(x, y)) {
                    onHammerAction?.invoke("dismantle", nearestBuildingPiece)
                    return true
                }
            }

            // Contextual interact button is only interactive when an interactable node or building piece is nearby
            val hasBuildingInteract = nearestBuildingPiece != null &&
                nearestBuildingPiece.definition.interactionType != BuildingInteractionType.NONE
            if ((isNearInteractableNode || hasBuildingInteract) && contextInteractButton.contains(x, y)) {
                onHarvestOrInteract()
                return true
            }
            return false
        }

        // 2. A modal is open -> Close button always closes; tapping backdrop outside modal window also closes
        val modalW = currentScreenWidth - 100f * currentUiScale
        val modalH = currentScreenHeight - 90f * currentUiScale
        val modalX = 50f * currentUiScale
        val modalY = 45f * currentUiScale
        if (modalCloseButton.contains(x, y) || x < modalX || x > (modalX + modalW) || y < modalY || y > (modalY + modalH)) {
            activeModal = SurvivalModalType.NONE
            selectedInvSlot = -1
            selectedChestSlot = -1
            audioManager.playUiClick()
            return true
        }

        when (activeModal) {
            SurvivalModalType.INVENTORY -> {
                for (i in 0 until 25) {
                    if (invModalSlotBounds[i].contains(x, y)) {
                        if (selectedInvSlot == -1) {
                            if (inventory.slots[i] != null) {
                                selectedInvSlot = i
                                audioManager.playUiClick()
                            }
                        } else if (selectedInvSlot == i) {
                            selectedInvSlot = -1
                            audioManager.playUiClick()
                        } else {
                            inventory.swapOrMergeSlots(selectedInvSlot, i)
                            selectedInvSlot = -1
                            audioManager.playUiClick()
                        }
                        return true
                    }
                }

                if (useItemButton.contains(x, y)) {
                    if (selectedInvSlot in 0 until 25) {
                        val stack = inventory.slots[selectedInvSlot]
                        if (stack != null) {
                            val def = stack.definition
                            if (def != null && (def.category == ItemCategory.CONSUMABLE || def.foodRestore > 0f || def.drinkRestore > 0f || def.healAmount > 0f || def.energyRestore > 0f || def.staminaRestore > 0f)) {
                                consumeItemAtSlot(selectedInvSlot, inventory, attributes, player, audioManager)
                                if (inventory.slots[selectedInvSlot] == null) selectedInvSlot = -1
                                return true
                            } else if (stack.isTool && stack.durability < stack.maxDurability) {
                                val repaired = craftingManager.repairTool(selectedInvSlot, inventory)
                                if (repaired) {
                                    audioManager.playResetChime()
                                    addNotification("REPAIRED ${def?.name ?: stack.itemId}", 1)
                                } else {
                                    audioManager.playUiClick()
                                    addNotification("NEED REPAIR MAT", 0)
                                }
                                return true
                            } else {
                                // Equip to active hotbar slot
                                if (selectedInvSlot in 0..4) {
                                    inventory.activeHotbarIndex = selectedInvSlot
                                } else {
                                    inventory.swapOrMergeSlots(selectedInvSlot, inventory.activeHotbarIndex)
                                    selectedInvSlot = inventory.activeHotbarIndex
                                }
                                audioManager.playUiClick()
                                return true
                            }
                        }
                    }
                }

                if (dropItemButton.contains(x, y)) {
                    if (selectedInvSlot in 0 until 25) {
                        val stack = inventory.slots[selectedInvSlot]
                        if (stack != null) {
                            val def = stack.definition
                            inventory.slots[selectedInvSlot] = null
                            selectedInvSlot = -1
                            audioManager.playMeleeHit(false)
                            def?.let { addNotification(it.id, -stack.count) }
                            return true
                        }
                    }
                }
                return true
            }

            SurvivalModalType.CRAFTING -> {
                val availableRecipes = craftingManager.getAvailableRecipes(isNearCraftingTable)
                for (i in recipeBounds.indices) {
                    if (recipeBounds[i].contains(x, y)) {
                        selectedRecipeIndex = i
                        audioManager.playUiClick()
                        return true
                    }
                }

                if (craftExecuteButton.contains(x, y)) {
                    val recipe = availableRecipes.getOrNull(selectedRecipeIndex)
                    if (recipe != null) {
                        val result = craftingManager.craft(recipe, inventory, isNearCraftingTable)
                        if (result != null) {
                            audioManager.playSwordHitImpact(true)
                            addNotification(result.definition?.id ?: recipe.resultItemId, recipe.resultCount)
                        } else {
                            audioManager.playUiClick()
                        }
                    }
                    return true
                }
                return true
            }

            SurvivalModalType.CHEST -> {
                for (i in 0 until 20) {
                    if (invModalSlotBounds[i].contains(x, y)) {
                        val success = chestManager.depositFromPlayer(i, inventory)
                        if (success) {
                            audioManager.playUiClick()
                        }
                        return true
                    }
                }

                for (i in 0 until 20) {
                    if (chestSlotBounds[i].contains(x, y)) {
                        val success = chestManager.withdrawToPlayer(i, inventory)
                        if (success) {
                            audioManager.playUiClick()
                        }
                        return true
                    }
                }

                if (storeAllButton.contains(x, y)) {
                    val transferred = chestManager.depositAllBagItems(inventory)
                    if (transferred > 0) audioManager.playUiClick()
                    return true
                }

                if (takeAllButton.contains(x, y)) {
                    val transferred = chestManager.withdrawAllItems(inventory)
                    if (transferred > 0) audioManager.playUiClick()
                    return true
                }
                return true
            }

            SurvivalModalType.NONE -> return false
        }
    }

    /**
     * Consumes one unit of the consumable item in [slotIndex], applying food, drink, health,
     * stamina, and MP/Energy restoration (e.g., MP Potion restores +50 Energy).
     */
    fun consumeItemAtSlot(
        slotIndex: Int,
        inventory: Inventory,
        attributes: SurvivalAttributes,
        player: PlayerCharacter,
        audioManager: AudioManager
    ): Boolean {
        if (slotIndex !in 0 until inventory.slots.size) return false
        val stack = inventory.slots[slotIndex] ?: return false
        val def = stack.definition ?: return false
        val isConsumable = def.category == ItemCategory.CONSUMABLE ||
            def.foodRestore > 0f ||
            def.drinkRestore > 0f ||
            def.healAmount > 0f ||
            def.energyRestore > 0f ||
            def.staminaRestore > 0f
        if (!isConsumable) return false

        if (def.foodRestore > 0f) attributes.consumeFood(def.foodRestore)
        if (def.drinkRestore > 0f) attributes.consumeDrink(def.drinkRestore)
        if (def.healAmount > 0f) player.heal(def.healAmount)
        if (def.staminaRestore > 0f) attributes.restoreStamina(def.staminaRestore)
        if (def.energyRestore > 0f) {
            player.energy = (player.energy + def.energyRestore).coerceAtMost(player.maxEnergy)
        }
        inventory.decreaseSlot(slotIndex, 1)
        audioManager.playResetChime()
        addNotification(def.id, -1)
        return true
    }
}
