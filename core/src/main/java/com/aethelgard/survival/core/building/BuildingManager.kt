package com.aethelgard.survival.core.building

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.player.AnimationState
import com.aethelgard.survival.core.player.PlayerCharacter
import com.aethelgard.survival.core.survival.ChestManager
import com.aethelgard.survival.core.survival.Inventory
import com.aethelgard.survival.core.survival.SurvivalAttributes
import com.aethelgard.survival.core.world.WorldManager
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class PlacementValidationResult(
    val isValid: Boolean,
    val statusMessage: String,
    val missingResources: Map<String, Int> = emptyMap(),
    val snappedToPieceId: String? = null
) {
    val reason: String
        get() = statusMessage
}

data class BuildingInspectionReport(
    val pieceId: String,
    val name: String,
    val categoryName: String,
    val materialName: String,
    val health: Float,
    val maxHealth: Float,
    val conditionLabel: String,
    val isSupportingUpperPieces: Boolean,
    val canRepair: Boolean,
    val repairCostItemId: String,
    val repairCostCount: Int,
    val upgradeTarget: BuildingPieceDefinition?,
    val dismantleRefund: Map<String, Int>
)

/**
 * Core Freeform Building System manager for Aethelgard Survival.
 * Handles:
 * - Build Mode state, piece selection, rotation, pillar height increments, and drag offsets
 * - Hybrid socket/edge snapping and free placement
 * - Structural support verification (ground, foundations, pillars, walls, roofs, bridges)
 * - Overlap and vertical wall-stacking prevention
 * - Resource cost validation and deduction
 * - Builder's Hammer inspection, repair, material upgrade, and safe dismantling with partial refunds
 * - Building health, monster damage resistance, and chest inventory preservation
 * - Walkable floors/stairs and oriented bounding box (OBB) collision resolution
 * - Full serialization and backward-compatible persistence via SurvivalSaveManager
 */
class BuildingManager(
    private val worldManager: WorldManager,
    private val buildingModelFactory: BuildingModelFactory? = null
) {
    val placedPieces = mutableListOf<PlacedBuildingPiece>()
    private var nextPieceCounter: Int = 1

    // Build Mode state
    var isBuildModeActive: Boolean = false
    var selectedCategory: BuildingCategory = BuildingCategory.FLOORS
        private set
    var selectedPiece: BuildingPieceDefinition = BuildingCatalog.WOOD_FLOOR
        private set
    var isSnapEnabled: Boolean = true
    var previewRotationYaw: Float = 0f
    var previewPillarHeight: Float = 3.0f

    val isGhostValid: Boolean
        get() = lastValidation.isValid

    val ghostInvalidReason: String
        get() = lastValidation.statusMessage

    val currentPillarHeight: Float
        get() = previewPillarHeight

    var isSnappingEnabled: Boolean
        get() = isSnapEnabled
        set(value) {
            isSnapEnabled = value
        }

    // Player finger-drag placement offset in world XZ (and optional Y)
    var dragOffsetX: Float = 0f
    var dragOffsetZ: Float = 0f
    var dragOffsetY: Float = 0f

    val previewPosition = Vector3()
    var lastSnappedPieceId: String? = null
        private set
    var lastValidation: PlacementValidationResult = PlacementValidationResult(false, "NOT INITIALIZED")
        private set

    // Inspected piece via Builder's Hammer
    var inspectedPiece: PlacedBuildingPiece? = null
        private set
    var lastActionFeedback: String = ""
        private set

    private val tmpVec = Vector3()
    private val tmpForward = Vector3()

    init {
        // Connect walkable floor/stair surface heights and solid wall/door/fence collisions into WorldManager
        worldManager.buildingSurfaceHeightProvider = { x, z, currentY, baseGroundY ->
            computeWalkableBuildingHeight(x, z, currentY, baseGroundY)
        }
        worldManager.buildingCollisionResolver = { pos, radius ->
            resolveBuildingCollisions(pos, radius, pos.y)
        }
    }

    fun toggleBuildMode(): Boolean {
        isBuildModeActive = !isBuildModeActive
        if (isBuildModeActive) {
            resetDragOffsets()
        }
        return isBuildModeActive
    }

    fun setBuildMode(active: Boolean) {
        isBuildModeActive = active
        if (active) {
            resetDragOffsets()
        }
    }

    fun selectCategory(category: BuildingCategory) {
        selectedCategory = category
        val list = BuildingCatalog.getByCategory(category)
        if (list.isNotEmpty() && selectedPiece.category != category) {
            selectPiece(list.first())
        }
    }

    fun selectPiece(piece: BuildingPieceDefinition) {
        selectedPiece = piece
        selectedCategory = piece.category
        previewPillarHeight = piece.height
        resetDragOffsets()
    }

    fun rotatePreview(deltaDegrees: Float = 90f) {
        previewRotationYaw = ((previewRotationYaw + deltaDegrees) % 360f + 360f) % 360f
    }

    fun toggleSnapping(): Boolean {
        isSnapEnabled = !isSnapEnabled
        return isSnapEnabled
    }

    fun getPiecesForSelectedCategory(): List<BuildingPieceDefinition> =
        BuildingCatalog.getByCategory(selectedCategory)

    fun hasResourcesFor(def: BuildingPieceDefinition, inventory: Inventory): Boolean =
        inventory.hasAllRequirements(def.resourceCosts)

    fun clearAllPieces() {
        placedPieces.clear()
        inspectedPiece = null
    }

    fun adjustPillarHeight(stepDelta: Int): Float =
        adjustPillarHeight(stepDelta * selectedPiece.heightIncrement)

    fun adjustPillarHeight(deltaHeight: Float): Float {
        if (!selectedPiece.supportsAdjustableHeight) return selectedPiece.height
        val raw = (previewPillarHeight + deltaHeight).coerceIn(selectedPiece.minHeight, selectedPiece.maxHeight)
        val steps = ((raw - selectedPiece.minHeight) / selectedPiece.heightIncrement).roundToInt()
        previewPillarHeight = (selectedPiece.minHeight + steps * selectedPiece.heightIncrement)
            .coerceIn(selectedPiece.minHeight, selectedPiece.maxHeight)
        return previewPillarHeight
    }

    fun applyDragAdjustment(deltaRight: Float, deltaForward: Float, cameraYawDeg: Float = 0f) {
        val rad = Math.toRadians(cameraYawDeg.toDouble()).toFloat()
        val sinYaw = sin(rad)
        val cosYaw = cos(rad)
        // Right vector (-cosYaw, 0, sinYaw) and Forward vector (-sinYaw, 0, -cosYaw)
        val worldDX = -deltaRight * cosYaw - deltaForward * sinYaw
        val worldDZ = deltaRight * sinYaw - deltaForward * cosYaw
        dragOffsetX = (dragOffsetX + worldDX).coerceIn(-6.0f, 6.0f)
        dragOffsetZ = (dragOffsetZ + worldDZ).coerceIn(-6.0f, 6.0f)
    }

    fun resetDragOffsets() {
        dragOffsetX = 0f
        dragOffsetZ = 0f
        dragOffsetY = 0f
    }

    /**
     * Updates the Ghost Preview position from the player's position and camera aim,
     * applies socket snapping if enabled, and validates placement rules & resources.
     */
    fun updateGhostPreview(
        player: PlayerCharacter,
        camera: PerspectiveCamera,
        inventory: Inventory
    ): PlacementValidationResult {
        // Compute aim anchor ~4.2m in front of player/camera
        tmpForward.set(camera.direction.x, 0f, camera.direction.z)
        if (tmpForward.len2() < 0.001f) {
            val rad = Math.toRadians(player.facingYaw.toDouble()).toFloat()
            tmpForward.set(sin(rad), 0f, cos(rad))
        } else {
            tmpForward.nor()
        }

        val reachDist = 4.2f
        val targetX = player.position.x + tmpForward.x * reachDist + dragOffsetX
        val targetZ = player.position.z + tmpForward.z * reachDist + dragOffsetZ
        val groundY = worldManager.getTerrainHeight(targetX, targetZ)
        var targetY = groundY + dragOffsetY

        // Default elevated height if aiming a roof without drag offset
        if (selectedPiece.category == BuildingCategory.ROOFS && abs(dragOffsetY) < 0.05f) {
            targetY = groundY + 3.0f
        }

        previewPosition.set(targetX, targetY, targetZ)
        lastSnappedPieceId = null

        if (isSnapEnabled) {
            val snapCandidate = findBestSnapTransform(selectedPiece, previewPosition, previewRotationYaw)
            if (snapCandidate != null) {
                previewPosition.set(snapCandidate.position)
                previewRotationYaw = snapCandidate.yaw
                lastSnappedPieceId = snapCandidate.targetPieceId
            }
        }

        lastValidation = validatePlacement(
            def = selectedPiece,
            position = previewPosition,
            rotationYaw = previewRotationYaw,
            pillarHeight = previewPillarHeight,
            inventory = inventory,
            requireResources = true
        )
        return lastValidation
    }

    fun updateGhostPreview(
        player: PlayerCharacter,
        cameraController: com.aethelgard.survival.core.camera.FollowCameraController,
        inventory: Inventory
    ): PlacementValidationResult {
        val yawRad = Math.toRadians(cameraController.yawDegrees.toDouble()).toFloat()
        val reachDist = 4.2f
        val fwdX = -sin(yawRad)
        val fwdZ = -cos(yawRad)
        val targetX = player.position.x + fwdX * reachDist + dragOffsetX
        val targetZ = player.position.z + fwdZ * reachDist + dragOffsetZ
        val groundY = worldManager.getTerrainHeight(targetX, targetZ)
        var targetY = groundY + dragOffsetY
        if (selectedPiece.category == BuildingCategory.ROOFS && abs(dragOffsetY) < 0.05f) {
            targetY = groundY + 3.0f
        }
        previewPosition.set(targetX, targetY, targetZ)
        lastSnappedPieceId = null

        if (isSnapEnabled) {
            val snapCandidate = findBestSnapTransform(selectedPiece, previewPosition, previewRotationYaw)
            if (snapCandidate != null) {
                previewPosition.set(snapCandidate.position)
                previewRotationYaw = snapCandidate.yaw
                lastSnappedPieceId = snapCandidate.targetPieceId
            }
        }

        lastValidation = validatePlacement(
            def = selectedPiece,
            position = previewPosition,
            rotationYaw = previewRotationYaw,
            pillarHeight = previewPillarHeight,
            inventory = inventory,
            requireResources = true
        )
        return lastValidation
    }

    data class SnapTransform(
        val position: Vector3,
        val yaw: Float,
        val targetPieceId: String
    )

    /**
     * Evaluates compatible connection sockets on nearby placed building pieces.
     */
    fun findBestSnapTransform(
        candidateDef: BuildingPieceDefinition,
        rawTargetPos: Vector3,
        candidateYaw: Float,
        candidateHeight: Float
    ): SnapTransform? = computeBestSnapTransform(
        def = candidateDef,
        rawPos = rawTargetPos,
        currentYaw = candidateYaw,
        snapRadius = 2.2f
    )

    fun findBestSnapTransform(
        def: BuildingPieceDefinition,
        rawPos: Vector3,
        currentYaw: Float
    ): SnapTransform? = computeBestSnapTransform(def, rawPos, currentYaw, 2.2f)

    private fun computeBestSnapTransform(
        def: BuildingPieceDefinition,
        rawPos: Vector3,
        currentYaw: Float,
        snapRadius: Float
    ): SnapTransform? {
        var bestDistSq = snapRadius * snapRadius
        var bestSnap: SnapTransform? = null

        for (existing in placedPieces) {
            val exDef = existing.definition
            val exPos = existing.position
            val horizDistSq = (rawPos.x - exPos.x) * (rawPos.x - exPos.x) + (rawPos.z - exPos.z) * (rawPos.z - exPos.z)
            if (horizDistSq > 36f) continue

            val candidates = computeSnapCandidatesForPair(def, existing, currentYaw)
            for (cand in candidates) {
                val dx = rawPos.x - cand.position.x
                val dy = (rawPos.y - cand.position.y) * 0.65f
                val dz = rawPos.z - cand.position.z
                val dSq = dx * dx + dy * dy + dz * dz
                if (dSq < bestDistSq) {
                    bestDistSq = dSq
                    bestSnap = cand
                }
            }
        }
        return bestSnap
    }

    private fun computeSnapCandidatesForPair(
        newDef: BuildingPieceDefinition,
        existing: PlacedBuildingPiece,
        currentYaw: Float
    ): List<SnapTransform> {
        val list = mutableListOf<SnapTransform>()
        val exDef = existing.definition
        val exPos = existing.position
        val exYaw = existing.rotationYaw
        val rad = Math.toRadians(exYaw.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)

        fun localToWorld(lx: Float, ly: Float, lz: Float): Vector3 {
            return Vector3(
                exPos.x + (lx * c + lz * s),
                exPos.y + ly,
                exPos.z + (-lx * s + lz * c)
            )
        }

        when (newDef.category) {
            BuildingCategory.FLOORS, BuildingCategory.FOUNDATIONS -> {
                if (exDef.category == BuildingCategory.FLOORS || exDef.category == BuildingCategory.FOUNDATIONS) {
                    val step = (exDef.width + newDef.width) * 0.5f
                    val targetY = if (newDef.category == BuildingCategory.FLOORS && exDef.category == BuildingCategory.FOUNDATIONS) {
                        exPos.y + exDef.height
                    } else {
                        exPos.y
                    }
                    // 4 cardinal edge snaps + directly on top of foundation
                    if (newDef.category == BuildingCategory.FLOORS && exDef.category == BuildingCategory.FOUNDATIONS) {
                        list.add(SnapTransform(Vector3(exPos.x, exPos.y + exDef.height, exPos.z), exYaw, existing.instanceId))
                    }
                    list.add(SnapTransform(Vector3(exPos.x + s * step, targetY, exPos.z + c * step), exYaw, existing.instanceId))
                    list.add(SnapTransform(Vector3(exPos.x - s * step, targetY, exPos.z - c * step), exYaw, existing.instanceId))
                    list.add(SnapTransform(Vector3(exPos.x + c * step, targetY, exPos.z - s * step), exYaw, existing.instanceId))
                    list.add(SnapTransform(Vector3(exPos.x - c * step, targetY, exPos.z + s * step), exYaw, existing.instanceId))
                } else if (exDef.category == BuildingCategory.PILLARS) {
                    // Elevated floor snaps on top of pillar
                    val topY = exPos.y + existing.effectiveHeight
                    list.add(SnapTransform(Vector3(exPos.x, topY, exPos.z), currentYaw, existing.instanceId))
                } else if (exDef.category == BuildingCategory.WALLS || exDef.category == BuildingCategory.DOORS || exDef.category == BuildingCategory.WINDOWS) {
                    // Elevated floor snaps to top edge of wall
                    val topY = exPos.y + existing.effectiveHeight
                    val halfD = newDef.depth * 0.5f
                    list.add(SnapTransform(localToWorld(0f, existing.effectiveHeight, halfD), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(0f, existing.effectiveHeight, -halfD), exYaw, existing.instanceId))
                } else if (exDef.category == BuildingCategory.STAIRS) {
                    // Floor snaps to top of stairs
                    list.add(SnapTransform(localToWorld(0f, exDef.height, -exDef.depth), exYaw, existing.instanceId))
                }
            }

            BuildingCategory.WALLS, BuildingCategory.DOORS, BuildingCategory.WINDOWS -> {
                if (exDef.category == BuildingCategory.FLOORS || exDef.category == BuildingCategory.FOUNDATIONS) {
                    // Snap to the 4 perimeter edges of the floor/foundation
                    val halfW = exDef.width * 0.5f
                    val halfD = exDef.depth * 0.5f
                    val topY = exDef.height
                    list.add(SnapTransform(localToWorld(0f, topY, -halfD), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(0f, topY, halfD), (exYaw + 180f) % 360f, existing.instanceId))
                    list.add(SnapTransform(localToWorld(halfW, topY, 0f), (exYaw + 90f) % 360f, existing.instanceId))
                    list.add(SnapTransform(localToWorld(-halfW, topY, 0f), (exYaw + 270f) % 360f, existing.instanceId))
                } else if (exDef.category == BuildingCategory.WALLS || exDef.category == BuildingCategory.DOORS || exDef.category == BuildingCategory.WINDOWS) {
                    // Side-by-side wall continuation only (NEVER vertical wall stacking)
                    val span = (exDef.width + newDef.width) * 0.5f
                    list.add(SnapTransform(localToWorld(-span, 0f, 0f), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(span, 0f, 0f), exYaw, existing.instanceId))
                } else if (exDef.category == BuildingCategory.PILLARS) {
                    val halfW = newDef.width * 0.5f
                    list.add(SnapTransform(localToWorld(halfW, 0f, 0f), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(-halfW, 0f, 0f), exYaw, existing.instanceId))
                }
            }

            BuildingCategory.ROOFS -> {
                if (exDef.category == BuildingCategory.WALLS || exDef.category == BuildingCategory.DOORS || exDef.category == BuildingCategory.WINDOWS) {
                    // Snap roof over the bay adjacent to the wall top
                    val topY = existing.effectiveHeight
                    val halfD = 1.5f
                    list.add(SnapTransform(localToWorld(0f, topY, halfD), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(0f, topY, -halfD), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(0f, topY, 0f), exYaw, existing.instanceId))
                } else if (exDef.category == BuildingCategory.PILLARS) {
                    val topY = existing.effectiveHeight
                    list.add(SnapTransform(Vector3(exPos.x, exPos.y + topY, exPos.z), currentYaw, existing.instanceId))
                } else if (exDef.category == BuildingCategory.ROOFS) {
                    val step = 3.0f
                    list.add(SnapTransform(localToWorld(step, 0f, 0f), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(-step, 0f, 0f), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(0f, 0f, step), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(0f, 0f, -step), exYaw, existing.instanceId))
                }
            }

            BuildingCategory.PILLARS -> {
                if (exDef.category == BuildingCategory.FLOORS || exDef.category == BuildingCategory.FOUNDATIONS) {
                    val halfW = exDef.width * 0.5f - 0.15f
                    val halfD = exDef.depth * 0.5f - 0.15f
                    val topY = exDef.height
                    list.add(SnapTransform(localToWorld(halfW, topY, halfD), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(-halfW, topY, halfD), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(halfW, topY, -halfD), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(-halfW, topY, -halfD), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(0f, topY, 0f), exYaw, existing.instanceId))
                } else if (exDef.category == BuildingCategory.PILLARS) {
                    list.add(SnapTransform(Vector3(exPos.x, exPos.y + existing.effectiveHeight, exPos.z), exYaw, existing.instanceId))
                }
            }

            BuildingCategory.STAIRS -> {
                if (exDef.category == BuildingCategory.FLOORS || exDef.category == BuildingCategory.FOUNDATIONS) {
                    val span = (exDef.depth + newDef.depth) * 0.5f
                    list.add(SnapTransform(localToWorld(0f, 0f, span), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(0f, 0f, -span), (exYaw + 180f) % 360f, existing.instanceId))
                }
            }

            BuildingCategory.FENCES -> {
                if (exDef.category == BuildingCategory.FENCES || exDef.id == "wood_gate") {
                    val span = (exDef.width + newDef.width) * 0.5f
                    list.add(SnapTransform(localToWorld(span, 0f, 0f), exYaw, existing.instanceId))
                    list.add(SnapTransform(localToWorld(-span, 0f, 0f), exYaw, existing.instanceId))
                }
            }

            else -> {
                // Furniture or Territory Marker: if near a floor, snap Y to floor surface
                if (exDef.category == BuildingCategory.FLOORS || exDef.category == BuildingCategory.FOUNDATIONS) {
                    val dx = abs(existing.position.x - previewPosition.x)
                    val dz = abs(existing.position.z - previewPosition.z)
                    if (dx <= exDef.width * 0.48f && dz <= exDef.depth * 0.48f) {
                        list.add(
                            SnapTransform(
                                Vector3(previewPosition.x, exPos.y + exDef.height, previewPosition.z),
                                currentYaw,
                                existing.instanceId
                            )
                        )
                    }
                }
            }
        }
        return list
    }

    /**
     * Validates whether a building piece can be placed at [position] with [rotationYaw].
     */
    fun validatePlacement(
        def: BuildingPieceDefinition,
        position: Vector3,
        rotationYaw: Float,
        customHeight: Float,
        player: PlayerCharacter,
        inventory: Inventory
    ): PlacementValidationResult = validatePlacement(
        def = def,
        position = position,
        rotationYaw = rotationYaw,
        pillarHeight = customHeight,
        inventory = inventory,
        requireResources = true
    )

    fun validatePlacement(
        def: BuildingPieceDefinition,
        position: Vector3,
        rotationYaw: Float,
        pillarHeight: Float = def.height,
        inventory: Inventory? = null,
        requireResources: Boolean = true,
        ignorePieceId: String? = null
    ): PlacementValidationResult {
        // 1. Check required resources
        val missing = mutableMapOf<String, Int>()
        if (requireResources && inventory != null) {
            for ((itemId, requiredCount) in def.resourceCosts) {
                val have = inventory.countItem(itemId)
                if (have < requiredCount) {
                    missing[itemId] = requiredCount - have
                }
            }
            if (missing.isNotEmpty()) {
                val firstMissing = missing.entries.first()
                return PlacementValidationResult(
                    isValid = false,
                    statusMessage = "MISSING ${firstMissing.value} ${firstMissing.key.uppercase(Locale.US)}",
                    missingResources = missing,
                    snappedToPieceId = lastSnappedPieceId
                )
            }
        }

        // 2. Enforce Wall Fixed Height & No Vertical Wall Stacking
        if (def.category == BuildingCategory.WALLS || def.category == BuildingCategory.DOORS || def.category == BuildingCategory.WINDOWS) {
            if (!def.allowVerticalWallStacking) {
                for (other in placedPieces) {
                    if (other.instanceId == ignorePieceId) continue
                    val oCat = other.definition.category
                    if (oCat == BuildingCategory.WALLS || oCat == BuildingCategory.DOORS || oCat == BuildingCategory.WINDOWS) {
                        val dx = position.x - other.position.x
                        val dz = position.z - other.position.z
                        val horizDist = sqrt(dx * dx + dz * dz)
                        val otherTopY = other.position.y + other.effectiveHeight
                        if (horizDist < 1.15f && position.y >= otherTopY - 0.45f && position.y <= otherTopY + 0.65f) {
                            return PlacementValidationResult(
                                isValid = false,
                                statusMessage = "WALLS CANNOT STACK VERTICALLY",
                                snappedToPieceId = lastSnappedPieceId
                            )
                        }
                    }
                }
            }
        }

        // 3. Check Overlap / Invalid Intersections with existing pieces
        val effH = if (def.supportsAdjustableHeight) pillarHeight.coerceIn(def.minHeight, def.maxHeight) else def.height
        for (other in placedPieces) {
            if (other.instanceId == ignorePieceId) continue
            if (doesOverlapInvalidly(def, position, rotationYaw, effH, other)) {
                return PlacementValidationResult(
                    isValid = false,
                    statusMessage = "OVERLAPS EXISTING ${other.definition.name.uppercase(Locale.US)}",
                    snappedToPieceId = lastSnappedPieceId
                )
            }
        }

        // 4. Validate Structural Support
        val supportCheck = checkStructuralSupport(def, position, ignorePieceId)
        if (!supportCheck.first) {
            return PlacementValidationResult(
                isValid = false,
                statusMessage = supportCheck.second,
                snappedToPieceId = lastSnappedPieceId
            )
        }

        return PlacementValidationResult(
            isValid = true,
            statusMessage = "VALID PLACEMENT",
            snappedToPieceId = lastSnappedPieceId
        )
    }

    private fun doesOverlapInvalidly(
        def: BuildingPieceDefinition,
        pos: Vector3,
        yaw: Float,
        effH: Float,
        other: PlacedBuildingPiece
    ): Boolean {
        val oDef = other.definition
        val oPos = other.position
        val oH = other.effectiveHeight

        // Vertical overlap check (allow 0.08m tolerance so pieces resting on top of floors/pillars don't trigger overlap)
        val yMinA = pos.y + 0.08f
        val yMaxA = pos.y + effH - 0.08f
        val yMinB = oPos.y + 0.08f
        val yMaxB = oPos.y + oH - 0.08f
        val verticalOverlap = (yMinA < yMaxB && yMaxA > yMinB) || abs(pos.y - oPos.y) < 0.12f
        if (!verticalOverlap) return false

        val dx = pos.x - oPos.x
        val dz = pos.z - oPos.z
        val dist = sqrt(dx * dx + dz * dz)

        // Identical category at nearly the same center
        if (def.category == oDef.category) {
            val minCenterDist = when (def.category) {
                BuildingCategory.FLOORS, BuildingCategory.FOUNDATIONS, BuildingCategory.ROOFS -> 2.15f
                BuildingCategory.WALLS, BuildingCategory.DOORS, BuildingCategory.WINDOWS, BuildingCategory.FENCES -> {
                    // Perpendicular walls meeting at a corner have centers ~2.12m apart; parallel overlapping walls are < 0.85m
                    val angleDiff = abs(((yaw - other.rotationYaw) % 180f + 180f) % 180f)
                    if (angleDiff in 65f..115f) 0.55f else 1.45f
                }
                BuildingCategory.PILLARS -> 0.38f
                BuildingCategory.FURNITURE -> (max(def.width, def.depth) + max(oDef.width, oDef.depth)) * 0.38f
                else -> 0.85f
            }
            if (dist < minCenterDist) return true
        }

        // Wall vs Door/Window at same wall slot
        val isWallLikeA = def.category == BuildingCategory.WALLS || def.category == BuildingCategory.DOORS || def.category == BuildingCategory.WINDOWS
        val isWallLikeB = oDef.category == BuildingCategory.WALLS || oDef.category == BuildingCategory.DOORS || oDef.category == BuildingCategory.WINDOWS
        if (isWallLikeA && isWallLikeB && dist < 1.15f) {
            val angleDiff = abs(((yaw - other.rotationYaw) % 180f + 180f) % 180f)
            if (angleDiff < 35f || angleDiff > 145f) return true
        }

        // Furniture clipping into solid walls, pillars, or doors
        if (def.category == BuildingCategory.FURNITURE && (isWallLikeB || oDef.category == BuildingCategory.PILLARS)) {
            if (isPointNearWallOrPillar(pos.x, pos.z, max(def.width, def.depth) * 0.42f, other)) {
                return true
            }
        }
        if (oDef.category == BuildingCategory.FURNITURE && (isWallLikeA || def.category == BuildingCategory.PILLARS)) {
            if (dist < (max(oDef.width, oDef.depth) * 0.45f + 0.30f)) {
                return true
            }
        }

        return false
    }

    private fun isPointNearWallOrPillar(x: Float, z: Float, radius: Float, wallOrPillar: PlacedBuildingPiece): Boolean {
        val rad = Math.toRadians(wallOrPillar.rotationYaw.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        val dx = x - wallOrPillar.position.x
        val dz = z - wallOrPillar.position.z
        val localX = dx * c - dz * s
        val localZ = dx * s + dz * c
        val halfW = wallOrPillar.definition.width * 0.5f + radius
        val halfD = wallOrPillar.definition.depth * 0.5f + radius
        return abs(localX) < halfW && abs(localZ) < halfD
    }

    /**
     * Checks simplified realistic structural support for [def] at [pos].
     */
    fun checkStructuralSupport(
        def: BuildingPieceDefinition,
        pos: Vector3,
        ignorePieceId: String? = null
    ): Pair<Boolean, String> {
        val terrainY = worldManager.getTerrainHeight(pos.x, pos.z)
        val walkableGroundY = max(terrainY, worldManager.getWalkableSurfaceHeight(pos.x, pos.z))
        // Compute natural terrain ground (excluding building floors)
        val distFromMonument = sqrt(pos.x * pos.x + pos.z * pos.z)
        val monumentStep = when {
            distFromMonument < 1.80f -> 1.18f
            distFromMonument < 2.82f -> 0.85f
            distFromMonument < 3.82f -> 0.50f
            else -> 0f
        }
        val naturalGroundY = if (monumentStep > 0f) {
            max(terrainY, worldManager.getTerrainHeight(0f, 0f) + monumentStep)
        } else {
            terrainY
        }
        val isOnNaturalGround = abs(pos.y - naturalGroundY) <= 0.68f

        when (def.supportRequirement) {
            SupportRequirement.GROUND_ONLY -> {
                return if (isOnNaturalGround) {
                    true to "GROUND SUPPORTED"
                } else {
                    false to "REQUIRES DIRECT GROUND SUPPORT"
                }
            }

            SupportRequirement.WALL_OR_PILLAR_ONLY -> {
                // Roofs require walls, doors, windows, or pillars (or an adjacent supported roof)
                for (other in placedPieces) {
                    if (other.instanceId == ignorePieceId) continue
                    val oCat = other.definition.category
                    val dx = pos.x - other.position.x
                    val dz = pos.z - other.position.z
                    val horizDist = sqrt(dx * dx + dz * dz)

                    if (oCat == BuildingCategory.WALLS ||
                        oCat == BuildingCategory.DOORS ||
                        oCat == BuildingCategory.WINDOWS ||
                        oCat == BuildingCategory.PILLARS
                    ) {
                        val otherTopY = other.position.y + other.effectiveHeight
                        if (abs(pos.y - otherTopY) <= 0.55f && horizDist <= 2.45f) {
                            return true to "SUPPORTED BY ${other.definition.name.uppercase(Locale.US)}"
                        }
                    } else if (oCat == BuildingCategory.ROOFS) {
                        if (abs(pos.y - other.position.y) <= 0.45f && horizDist <= 3.35f) {
                            // Verify the adjacent roof is directly supported by a wall or pillar
                            if (hasDirectWallOrPillarSupport(other, ignorePieceId)) {
                                return true to "SUPPORTED BY ADJACENT ROOF"
                            }
                        }
                    }
                }
                return false to "ROOF REQUIRES WALL OR PILLAR SUPPORT"
            }

            SupportRequirement.FLOOR_OR_GROUND -> {
                if (isOnNaturalGround) return true to "GROUND SUPPORTED"
                for (other in placedPieces) {
                    if (other.instanceId == ignorePieceId) continue
                    val oCat = other.definition.category
                    if (oCat == BuildingCategory.FLOORS || oCat == BuildingCategory.FOUNDATIONS) {
                        val surfaceY = other.position.y + other.definition.height
                        val dx = abs(pos.x - other.position.x)
                        val dz = abs(pos.z - other.position.z)
                        if (abs(pos.y - surfaceY) <= 0.42f && dx <= other.definition.width * 0.55f && dz <= other.definition.depth * 0.55f) {
                            return true to "SUPPORTED BY FLOOR"
                        }
                    }
                }
                return false to "FURNITURE REQUIRES FLOOR OR GROUND"
            }

            SupportRequirement.BRIDGE_SPAN -> {
                if (isOnNaturalGround) return true to "GROUND ANCHORED"
                for (other in placedPieces) {
                    if (other.instanceId == ignorePieceId) continue
                    val oCat = other.definition.category
                    val dx = pos.x - other.position.x
                    val dz = pos.z - other.position.z
                    val horizDist = sqrt(dx * dx + dz * dz)
                    if (oCat == BuildingCategory.FLOORS || oCat == BuildingCategory.FOUNDATIONS) {
                        if (abs(pos.y - other.position.y) <= 0.48f && horizDist <= 3.35f) {
                            return true to "BRIDGE SPAN SUPPORTED"
                        }
                    } else if (oCat == BuildingCategory.PILLARS || oCat == BuildingCategory.WALLS) {
                        val topY = other.position.y + other.effectiveHeight
                        if (abs(pos.y - topY) <= 0.50f && horizDist <= 2.35f) {
                            return true to "BRIDGE SUPPORTED BY PILLAR"
                        }
                    }
                }
                return false to "BRIDGE REQUIRES ANCHOR OR PILLAR"
            }

            SupportRequirement.GROUND_OR_SUPPORT -> {
                if (isOnNaturalGround) return true to "GROUND SUPPORTED"

                for (other in placedPieces) {
                    if (other.instanceId == ignorePieceId) continue
                    val oCat = other.definition.category
                    val dx = pos.x - other.position.x
                    val dz = pos.z - other.position.z
                    val horizDist = sqrt(dx * dx + dz * dz)
                    val otherTopY = other.position.y + other.effectiveHeight

                    when (oCat) {
                        BuildingCategory.FOUNDATIONS, BuildingCategory.FLOORS -> {
                            // Resting on top of floor/foundation (e.g., wall, pillar, stairs) or edge-connected floor
                            if (abs(pos.y - otherTopY) <= 0.45f && horizDist <= 2.35f) {
                                return true to "SUPPORTED BY ${other.definition.name.uppercase(Locale.US)}"
                            }
                            if ((def.category == BuildingCategory.FLOORS || def.category == BuildingCategory.STAIRS) &&
                                abs(pos.y - other.position.y) <= 0.45f && horizDist <= 3.35f &&
                                isPieceGroundedOrSupported(other, ignorePieceId)
                            ) {
                                return true to "SUPPORTED BY ADJACENT FLOOR"
                            }
                        }

                        BuildingCategory.PILLARS -> {
                            // Elevated floor, wall, or stacked pillar supported by pillar top
                            if (abs(pos.y - otherTopY) <= 0.50f && horizDist <= 2.35f) {
                                return true to "SUPPORTED BY PILLAR"
                            }
                        }

                        BuildingCategory.WALLS, BuildingCategory.DOORS, BuildingCategory.WINDOWS -> {
                            // Elevated floor or stairs resting on top of a wall
                            if ((def.category == BuildingCategory.FLOORS || def.category == BuildingCategory.STAIRS) &&
                                abs(pos.y - otherTopY) <= 0.50f && horizDist <= 2.35f
                            ) {
                                return true to "SUPPORTED BY WALL"
                            }
                        }

                        BuildingCategory.STAIRS -> {
                            if (def.category == BuildingCategory.FLOORS &&
                                abs(pos.y - otherTopY) <= 0.50f && horizDist <= 3.25f
                            ) {
                                return true to "SUPPORTED BY STAIRS"
                            }
                        }

                        else -> {}
                    }
                }
                return false to "UNSUPPORTED FLOATING PIECE"
            }
        }
    }

    private fun hasDirectWallOrPillarSupport(roofPiece: PlacedBuildingPiece, ignorePieceId: String?): Boolean {
        for (other in placedPieces) {
            if (other.instanceId == ignorePieceId || other.instanceId == roofPiece.instanceId) continue
            val oCat = other.definition.category
            if (oCat == BuildingCategory.WALLS ||
                oCat == BuildingCategory.DOORS ||
                oCat == BuildingCategory.WINDOWS ||
                oCat == BuildingCategory.PILLARS
            ) {
                val dx = roofPiece.position.x - other.position.x
                val dz = roofPiece.position.z - other.position.z
                val horizDist = sqrt(dx * dx + dz * dz)
                val otherTopY = other.position.y + other.effectiveHeight
                if (abs(roofPiece.position.y - otherTopY) <= 0.55f && horizDist <= 2.45f) {
                    return true
                }
            }
        }
        return false
    }

    private fun isPieceGroundedOrSupported(piece: PlacedBuildingPiece, ignorePieceId: String?): Boolean {
        if (piece.isGroundedDirectly) return true
        for (other in placedPieces) {
            if (other.instanceId == ignorePieceId || other.instanceId == piece.instanceId) continue
            val oCat = other.definition.category
            if (oCat == BuildingCategory.PILLARS || oCat == BuildingCategory.FOUNDATIONS || oCat == BuildingCategory.WALLS) {
                val dx = piece.position.x - other.position.x
                val dz = piece.position.z - other.position.z
                val horizDist = sqrt(dx * dx + dz * dz)
                val topY = other.position.y + other.effectiveHeight
                if (abs(piece.position.y - topY) <= 0.50f && horizDist <= 2.35f) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Attempts to place the currently selected piece at the current preview position,
     * or a specific piece at an explicit position/rotation.
     * Deducts resources ONLY upon valid placement.
     */
    fun placePiece(
        player: PlayerCharacter,
        inventory: Inventory,
        audioManager: AudioManager? = null
    ): PlacedBuildingPiece? = placeCurrentPreview(inventory, audioManager, player)

    fun placePieceAt(
        def: BuildingPieceDefinition,
        position: Vector3,
        rotationYaw: Float,
        customHeight: Float = def.height,
        player: PlayerCharacter? = null,
        inventory: Inventory,
        audioManager: AudioManager? = null
    ): PlacedBuildingPiece? = placePiece(
        def = def,
        position = position,
        rotationYaw = rotationYaw,
        pillarHeight = customHeight,
        inventory = inventory,
        audioManager = audioManager,
        player = player,
        requireResources = true
    )

    fun placeCurrentPreview(
        inventory: Inventory,
        audioManager: AudioManager? = null,
        player: PlayerCharacter? = null
    ): PlacedBuildingPiece? {
        return placePiece(
            def = selectedPiece,
            position = Vector3(previewPosition),
            rotationYaw = previewRotationYaw,
            pillarHeight = previewPillarHeight,
            inventory = inventory,
            audioManager = audioManager,
            player = player
        )
    }

    fun placePiece(
        def: BuildingPieceDefinition,
        position: Vector3,
        rotationYaw: Float,
        pillarHeight: Float = def.height,
        inventory: Inventory,
        audioManager: AudioManager? = null,
        player: PlayerCharacter? = null,
        requireResources: Boolean = true
    ): PlacedBuildingPiece? {
        val validation = validatePlacement(
            def = def,
            position = position,
            rotationYaw = rotationYaw,
            pillarHeight = pillarHeight,
            inventory = inventory,
            requireResources = requireResources
        )
        lastValidation = validation
        if (!validation.isValid) {
            lastActionFeedback = validation.statusMessage
            return null
        }

        if (requireResources) {
            val deducted = inventory.consumeAllRequirements(def.resourceCosts)
            if (!deducted) {
                lastActionFeedback = "MISSING RESOURCES"
                return null
            }
        }

        val id = "bld_${nextPieceCounter++}"
        val clampedPillarH = if (def.supportsAdjustableHeight) {
            pillarHeight.coerceIn(def.minHeight, def.maxHeight)
        } else {
            def.height
        }
        val modelInst = buildingModelFactory?.createSolidInstance(def)
        val piece = PlacedBuildingPiece(
            instanceId = id,
            definition = def,
            position = Vector3(position),
            rotationYaw = ((rotationYaw % 360f) + 360f) % 360f,
            pillarHeight = clampedPillarH,
            health = def.maxHealth,
            isOpen = false,
            isLit = def.emitsLight,
            modelInstance = modelInst
        )
        val groundY = worldManager.getTerrainHeight(position.x, position.z)
        piece.isGroundedDirectly = abs(position.y - groundY) <= 0.48f
        piece.updateTransform()
        placedPieces.add(piece)

        player?.triggerToolSwing(AnimationState.TOOL_HAMMER_BUILD, 0.36f)
        audioManager?.playMeleeHit(isHeavy = true)
        lastActionFeedback = "PLACED ${def.name.uppercase(Locale.US)}"
        resetDragOffsets()
        return piece
    }

    /**
     * Finds the nearest placed building piece within [maxDistance] of [origin].
     */
    fun findNearestPiece(origin: Vector3, maxDistance: Float = 4.2f): PlacedBuildingPiece? {
        var bestDistSq = maxDistance * maxDistance
        var best: PlacedBuildingPiece? = null
        for (p in placedPieces) {
            val dx = origin.x - p.position.x
            val dy = (origin.y - p.position.y) * 0.5f
            val dz = origin.z - p.position.z
            val dSq = dx * dx + dy * dy + dz * dz
            if (dSq < bestDistSq) {
                bestDistSq = dSq
                best = p
            }
        }
        return best
    }

    /**
     * Finds the nearest interactive building piece (Door, Bed, Storage Chest, Crafting Table, Torch/Lantern, Chair, Ward)
     * within [maxDistance] of the player.
     */
    fun findNearestInteractivePiece(origin: Vector3, maxDistance: Float = 3.6f): PlacedBuildingPiece? {
        var bestDistSq = maxDistance * maxDistance
        var best: PlacedBuildingPiece? = null
        for (p in placedPieces) {
            if (p.definition.interactionType == BuildingInteractionType.NONE) continue
            val dx = origin.x - p.position.x
            val dy = (origin.y - p.position.y) * 0.5f
            val dz = origin.z - p.position.z
            val dSq = dx * dx + dy * dy + dz * dz
            if (dSq < bestDistSq) {
                bestDistSq = dSq
                best = p
            }
        }
        return best
    }

    /**
     * Returns true if the player is within [maxDistance] of ANY placed Artisan Workbench (`furniture_crafting_table`).
     */
    fun isNearPlacedCraftingTable(playerPos: Vector3, maxDistance: Float = 3.8f): Boolean {
        val maxSq = maxDistance * maxDistance
        for (p in placedPieces) {
            if (p.definition.interactionType == BuildingInteractionType.CRAFTING_TABLE) {
                val dx = playerPos.x - p.position.x
                val dz = playerPos.z - p.position.z
                if (dx * dx + dz * dz <= maxSq) return true
            }
        }
        return false
    }

    fun isInsideProtectedTerritory(pos: Vector3, radius: Float = 24f): Boolean =
        isPositionInProtectedTerritory(pos, radius)

    /**
     * Returns true if [pos] is inside the protection radius (24m) of an active Territory Marker.
     */
    fun isPositionInProtectedTerritory(pos: Vector3, radius: Float = 24f): Boolean {
        val rSq = radius * radius
        for (p in placedPieces) {
            if (p.definition.category == BuildingCategory.TERRITORY && !p.isDestroyed) {
                val dx = pos.x - p.position.x
                val dz = pos.z - p.position.z
                if (dx * dx + dz * dz <= rSq) return true
            }
        }
        return false
    }

    /**
     * Executes functional interaction with a placed building piece or furniture item.
     */
    fun interactWithPiece(
        piece: PlacedBuildingPiece,
        player: PlayerCharacter,
        attributes: SurvivalAttributes,
        audioManager: AudioManager? = null
    ): String {
        return when (piece.definition.interactionType) {
            BuildingInteractionType.DOOR_TOGGLE -> {
                piece.isOpen = !piece.isOpen
                piece.updateTransform()
                audioManager?.playUiClick()
                val stateLabel = if (piece.isOpen) "OPENED" else "CLOSED"
                lastActionFeedback = "$stateLabel ${piece.definition.name.uppercase(Locale.US)}"
                lastActionFeedback
            }

            BuildingInteractionType.SLEEP_BED -> {
                player.heal(45f)
                player.energy = player.maxEnergy
                attributes.restoreStamina(100f)
                audioManager?.playResetChime()
                lastActionFeedback = "RESTED IN BED (+HP & STAMINA)"
                lastActionFeedback
            }

            BuildingInteractionType.STORAGE_CHEST -> {
                audioManager?.playUiClick()
                "OPEN_CHEST"
            }

            BuildingInteractionType.CRAFTING_TABLE -> {
                audioManager?.playUiClick()
                "OPEN_CRAFTING"
            }

            BuildingInteractionType.LIGHT_TOGGLE -> {
                piece.isLit = !piece.isLit
                audioManager?.playUiClick()
                lastActionFeedback = if (piece.isLit) "LIT ${piece.definition.name.uppercase(Locale.US)}" else "EXTINGUISHED ${piece.definition.name.uppercase(Locale.US)}"
                lastActionFeedback
            }

            BuildingInteractionType.CHAIR_SIT -> {
                attributes.restoreStamina(40f)
                player.heal(10f)
                audioManager?.playUiClick()
                lastActionFeedback = "RESTED ON CHAIR (+STAMINA)"
                lastActionFeedback
            }

            BuildingInteractionType.TERRITORY_INSPECT -> {
                audioManager?.playResetChime()
                lastActionFeedback = "SANCTUARY WARD ACTIVE (24M RADIUS)"
                lastActionFeedback
            }

            BuildingInteractionType.NONE -> ""
        }
    }

    // =========================================================================
    // 8. BUILDER'S HAMMER: INSPECT, REPAIR, UPGRADE, AND SAFE DISMANTLE
    // =========================================================================

    fun inspectPiece(piece: PlacedBuildingPiece): String {
        inspectedPiece = piece
        val ratio = piece.healthRatio
        val condition = when {
            ratio >= 0.99f -> "PRISTINE"
            ratio >= 0.50f -> "DAMAGED"
            else -> "CRITICAL"
        }
        return "${piece.definition.name} (${piece.definition.material.displayName}) - ${piece.health.toInt()}/${piece.maxHealth.toInt()} HP [$condition]"
    }

    fun inspectPiece(piece: PlacedBuildingPiece, inventory: Inventory): BuildingInspectionReport {
        inspectedPiece = piece
        val ratio = piece.healthRatio
        val condition = when {
            ratio >= 0.99f -> "PRISTINE"
            ratio >= 0.50f -> "DAMAGED"
            else -> "CRITICAL"
        }
        val supportingUpper = !canSafelyDismantle(piece)
        val repItem = piece.definition.material.primaryResourceId
        val canRep = piece.isDamaged && (inventory.hasItem(repItem, 1) || inventory.hasItem("wood", 1))
        val upgTarget = piece.definition.upgradeTargetId?.let { BuildingCatalog.get(it) }
        val refund = computeDismantleRefund(piece.definition)

        return BuildingInspectionReport(
            pieceId = piece.instanceId,
            name = piece.definition.name,
            categoryName = piece.definition.category.displayName,
            materialName = piece.definition.material.displayName,
            health = piece.health,
            maxHealth = piece.maxHealth,
            conditionLabel = condition,
            isSupportingUpperPieces = supportingUpper,
            canRepair = canRep,
            repairCostItemId = repItem,
            repairCostCount = 1,
            upgradeTarget = upgTarget,
            dismantleRefund = refund
        )
    }

    /**
     * Repairs a damaged building piece using required materials and Builder's Hammer.
     */
    fun repairPiece(
        piece: PlacedBuildingPiece,
        inventory: Inventory,
        audioManager: AudioManager? = null
    ): Boolean = repairPiece(piece, inventory, null, audioManager)

    fun repairPiece(
        piece: PlacedBuildingPiece,
        inventory: Inventory,
        player: PlayerCharacter?,
        audioManager: AudioManager?
    ): Boolean {
        if (!piece.isDamaged) {
            lastActionFeedback = "PIECE AT FULL HEALTH"
            return false
        }
        val repMat = piece.definition.material.primaryResourceId
        val consumed = when {
            inventory.hasItem(repMat, 1) -> inventory.removeItem(repMat, 1)
            inventory.hasItem("wood", 1) -> inventory.removeItem("wood", 1)
            else -> false
        }
        if (!consumed) {
            lastActionFeedback = "NEED 1 ${repMat.uppercase(Locale.US)} TO REPAIR"
            return false
        }

        piece.health = piece.maxHealth
        piece.shakeTimer = 0.18f
        consumeEquippedHammerDurability(inventory)
        player?.triggerToolSwing(AnimationState.TOOL_HAMMER_BUILD, 0.38f)
        audioManager?.playResetChime()
        lastActionFeedback = "REPAIRED ${piece.definition.name.uppercase(Locale.US)}"
        return true
    }

    /**
     * Upgrades a compatible building piece (e.g., Wood -> Stone -> Brick -> Metal).
     * Validates structural compatibility and deducts target resources.
     */
    fun upgradePiece(
        piece: PlacedBuildingPiece,
        inventory: Inventory,
        audioManager: AudioManager? = null
    ): PlacedBuildingPiece? {
        return if (upgradePiece(piece, inventory, null, audioManager)) piece else null
    }

    fun upgradePiece(
        piece: PlacedBuildingPiece,
        inventory: Inventory,
        player: PlayerCharacter?,
        audioManager: AudioManager?
    ): Boolean {
        val targetId = piece.definition.upgradeTargetId
        if (targetId == null) {
            lastActionFeedback = "MAX MATERIAL TIER REACHED"
            return false
        }
        val targetDef = BuildingCatalog.get(targetId) ?: return false

        // Validate structural compatibility at current position
        val supportOk = checkStructuralSupport(targetDef, piece.position, ignorePieceId = piece.instanceId).first
        if (!supportOk) {
            lastActionFeedback = "UPGRADE STRUCTURALLY INCOMPATIBLE"
            return false
        }

        if (!inventory.hasAllRequirements(targetDef.resourceCosts)) {
            val firstMiss = targetDef.resourceCosts.entries.firstOrNull { (id, req) -> inventory.countItem(id) < req }
            lastActionFeedback = if (firstMiss != null) {
                "NEED ${firstMiss.value} ${firstMiss.key.uppercase(Locale.US)} FOR UPGRADE"
            } else {
                "MISSING UPGRADE MATERIALS"
            }
            return false
        }

        inventory.consumeAllRequirements(targetDef.resourceCosts)
        piece.definition = targetDef
        piece.health = targetDef.maxHealth
        piece.modelInstance = buildingModelFactory?.createSolidInstance(targetDef)
        piece.updateTransform()
        consumeEquippedHammerDurability(inventory)
        player?.triggerToolSwing(AnimationState.TOOL_HAMMER_BUILD, 0.42f)
        audioManager?.playResetChime()
        lastActionFeedback = "UPGRADED TO ${targetDef.name.uppercase(Locale.US)}"
        return true
    }

    /**
     * Checks if dismantling [piece] would leave any upper/dependent structure floating without support.
     */
    fun canSafelyDismantle(piece: PlacedBuildingPiece): Boolean {
        for (other in placedPieces) {
            if (other.instanceId == piece.instanceId) continue
            // Grounded pieces on natural terrain never float
            val terrainY = worldManager.getTerrainHeight(other.position.x, other.position.z)
            val isOnGround = abs(other.position.y - terrainY) <= 0.48f &&
                other.definition.supportRequirement != SupportRequirement.WALL_OR_PILLAR_ONLY
            if (isOnGround) continue

            val stillSupported = checkStructuralSupport(
                def = other.definition,
                pos = other.position,
                ignorePieceId = piece.instanceId
            ).first
            if (!stillSupported) {
                return false
            }
        }
        return true
    }

    fun computeDismantleRefund(def: BuildingPieceDefinition): Map<String, Int> {
        val refund = mutableMapOf<String, Int>()
        for ((itemId, count) in def.resourceCosts) {
            val refundedCount = ceil(count * 0.5f).toInt().coerceAtLeast(1)
            refund[itemId] = refundedCount
        }
        return refund
    }

    /**
     * Dismantles a placed building piece if structurally safe, refunding 50% of resources
     * and preserving any stored chest items.
     */
    fun dismantlePiece(
        piece: PlacedBuildingPiece,
        inventory: Inventory,
        audioManager: AudioManager? = null
    ): Map<String, Int> {
        if (!canSafelyDismantle(piece)) {
            lastActionFeedback = "CANNOT DISMANTLE: SUPPORTS UPPER STRUCTURE"
            return emptyMap()
        }
        val refund = computeDismantleRefund(piece.definition)
        val ok = dismantlePiece(piece, inventory, null, null, audioManager)
        return if (ok) refund else emptyMap()
    }

    fun dismantlePiece(
        piece: PlacedBuildingPiece,
        inventory: Inventory,
        chestManager: ChestManager?,
        player: PlayerCharacter?,
        audioManager: AudioManager?
    ): Boolean {
        if (!canSafelyDismantle(piece)) {
            lastActionFeedback = "CANNOT DISMANTLE: SUPPORTS UPPER STRUCTURE"
            return false
        }

        val removed = placedPieces.remove(piece)
        if (!removed) return false

        // Refund 50% of materials
        val refund = computeDismantleRefund(piece.definition)
        for ((itemId, count) in refund) {
            inventory.addItem(itemId, count)
        }

        // Preserve any items in local storage backup
        preservePieceStorage(piece, chestManager, inventory)

        if (inspectedPiece?.instanceId == piece.instanceId) {
            inspectedPiece = null
        }
        consumeEquippedHammerDurability(inventory)
        player?.triggerToolSwing(AnimationState.TOOL_HAMMER_BUILD, 0.38f)
        audioManager?.playMeleeHit(isHeavy = true)
        lastActionFeedback = "DISMANTLED ${piece.definition.name.uppercase(Locale.US)}"
        return true
    }

    private fun consumeEquippedHammerDurability(inventory: Inventory) {
        val active = inventory.activeHotbarItem
        if (active != null && active.itemId == "builders_hammer" && !active.isBroken) {
            inventory.damageToolInSlot(inventory.activeHotbarIndex, 1)
        } else {
            for (i in 0 until inventory.size) {
                val slot = inventory.slots[i]
                if (slot != null && slot.itemId == "builders_hammer" && !slot.isBroken) {
                    inventory.damageToolInSlot(i, 1)
                    break
                }
            }
        }
    }

    // =========================================================================
    // 9. BUILDING HEALTH, MONSTER DAMAGE & CHEST INVENTORY PRESERVATION
    // =========================================================================

    fun damagePiece(
        piece: PlacedBuildingPiece,
        rawMonsterDamage: Float,
        audioManager: AudioManager? = null,
        combatSystem: com.aethelgard.survival.core.combat.CombatSystem? = null
    ): Boolean {
        if (piece.isDestroyed) return true
        val territoryFactor = if (isPositionInProtectedTerritory(piece.position)) 0.80f else 1.0f
        val scaled = (rawMonsterDamage * piece.definition.material.monsterDamageReduction * territoryFactor)
            .coerceAtLeast(1f)
        piece.health = (piece.health - scaled).coerceAtLeast(0f)
        piece.shakeTimer = 0.28f
        piece.updateTransform()
        audioManager?.playMeleeHit(isHeavy = true)
        combatSystem?.recordDamagePopup(
            pos = Vector3(piece.position.x, piece.position.y + piece.effectiveHeight * 0.6f, piece.position.z),
            damage = scaled,
            isHeavy = false
        )

        if (piece.health <= 0f) {
            preservePieceStorage(piece, null, null)
            placedPieces.remove(piece)
            if (inspectedPiece?.instanceId == piece.instanceId) {
                inspectedPiece = null
            }
            lastActionFeedback = "${piece.definition.name.uppercase(Locale.US)} DESTROYED!"
            return true
        }
        return false
    }

    private fun preservePieceStorage(
        piece: PlacedBuildingPiece,
        chestManager: ChestManager?,
        playerInventory: Inventory?
    ) {
        for (i in 0 until piece.localStorageBackup.size) {
            val stack = piece.localStorageBackup.slots[i] ?: continue
            var remaining = stack.count
            if (chestManager != null) {
                remaining = chestManager.chestInventory.addItem(stack.itemId, remaining)
            }
            if (remaining > 0 && playerInventory != null) {
                playerInventory.addItem(stack.itemId, remaining)
            }
            piece.localStorageBackup.slots[i] = null
        }
    }

    // =========================================================================
    // WALKABLE SURFACES, COLLISIONS, UPDATE & RENDERING
    // =========================================================================

    fun computeWalkableBuildingHeight(x: Float, z: Float, currentY: Float, baseGroundY: Float): Float {
        var highestWalkable = baseGroundY
        for (piece in placedPieces) {
            if (!piece.definition.isWalkableSurface || piece.isDestroyed) continue
            val rad = Math.toRadians(piece.rotationYaw.toDouble()).toFloat()
            val c = cos(rad)
            val s = sin(rad)
            val dx = x - piece.position.x
            val dz = z - piece.position.z
            val localX = dx * c - dz * s
            val localZ = dx * s + dz * c

            val halfW = piece.definition.width * 0.5f + 0.12f
            val halfD = piece.definition.depth * 0.5f + 0.12f
            if (abs(localX) <= halfW && abs(localZ) <= halfD) {
                val pieceSurfaceY = if (piece.definition.category == BuildingCategory.STAIRS) {
                    // Ramp rises from z = +halfD (base) to z = -halfD (top)
                    val t = ((halfD - localZ) / (2f * halfD)).coerceIn(0f, 1f)
                    piece.position.y + t * piece.definition.height
                } else {
                    piece.position.y + piece.definition.height
                }
                // Allow stepping onto surface if player is above or within step-up reach (0.85m)
                if (currentY + 0.85f >= pieceSurfaceY && pieceSurfaceY > highestWalkable) {
                    highestWalkable = pieceSurfaceY
                }
            }
        }
        return highestWalkable
    }

    fun resolveBuildingCollisions(position: Vector3, entityRadius: Float, entityY: Float = position.y) {
        for (piece in placedPieces) {
            if (!piece.definition.isSolidObstacle || piece.isDestroyed) continue
            // Open doors and gates let entities pass freely
            if ((piece.definition.category == BuildingCategory.DOORS ||
                    piece.definition.interactionType == BuildingInteractionType.DOOR_TOGGLE) && piece.isOpen
            ) {
                continue
            }

            // Vertical overlap check
            val pBottom = piece.position.y
            val pTop = piece.position.y + piece.effectiveHeight
            if (entityY + 1.65f < pBottom || entityY > pTop - 0.15f) continue

            val rad = Math.toRadians(piece.rotationYaw.toDouble()).toFloat()
            val c = cos(rad)
            val s = sin(rad)
            val dx = position.x - piece.position.x
            val dz = position.z - piece.position.z

            // Transform delta into piece-local coordinates
            val localX = dx * c - dz * s
            val localZ = dx * s + dz * c

            val halfW = piece.definition.width * 0.5f
            val halfD = piece.definition.depth * 0.5f

            val clampedX = localX.coerceIn(-halfW, halfW)
            val clampedZ = localZ.coerceIn(-halfD, halfD)
            val diffX = localX - clampedX
            val diffZ = localZ - clampedZ
            val distSq = diffX * diffX + diffZ * diffZ

            if (distSq < entityRadius * entityRadius) {
                val dist = sqrt(distSq)
                val pushLocalX: Float
                val pushLocalZ: Float
                if (dist > 0.0001f) {
                    val overlap = entityRadius - dist
                    pushLocalX = (diffX / dist) * overlap
                    pushLocalZ = (diffZ / dist) * overlap
                } else {
                    // Inside box center: push out along shallower axis (usually Z thickness for walls)
                    val penX = (halfW + entityRadius) - abs(localX)
                    val penZ = (halfD + entityRadius) - abs(localZ)
                    if (penZ <= penX) {
                        pushLocalX = 0f
                        pushLocalZ = if (localZ >= 0f) penZ else -penZ
                    } else {
                        pushLocalX = if (localX >= 0f) penX else -penX
                        pushLocalZ = 0f
                    }
                }
                // Rotate push vector back to world XZ
                position.x += pushLocalX * c + pushLocalZ * s
                position.z += -pushLocalX * s + pushLocalZ * c
            }
        }
    }

    fun update(delta: Float) {
        for (piece in placedPieces) {
            if (piece.shakeTimer > 0f) {
                piece.shakeTimer = (piece.shakeTimer - delta).coerceAtLeast(0f)
                piece.updateTransform()
            }
        }
    }

    fun render(modelBatch: ModelBatch, environment: Environment) {
        for (piece in placedPieces) {
            val inst = piece.modelInstance ?: continue
            modelBatch.render(inst, environment)
        }

        if (isBuildModeActive && buildingModelFactory != null) {
            val ghostInst: ModelInstance = buildingModelFactory.createGhostInstance(selectedPiece, lastValidation.isValid)
            val yScale = if (selectedPiece.supportsAdjustableHeight && selectedPiece.height > 0.01f) {
                previewPillarHeight.coerceIn(selectedPiece.minHeight, selectedPiece.maxHeight) / selectedPiece.height
            } else {
                1f
            }
            ghostInst.transform.idt()
                .translate(previewPosition.x, previewPosition.y, previewPosition.z)
                .rotate(0f, 1f, 0f, previewRotationYaw)
                .scale(1f, yScale, 1f)
            modelBatch.render(ghostInst, environment)
        }
    }

    // =========================================================================
    // 13. SAVE AND LOAD PERSISTENCE
    // =========================================================================

    fun serialize(): String {
        if (placedPieces.isEmpty()) return ""
        val sb = StringBuilder()
        for (i in placedPieces.indices) {
            val p = placedPieces[i]
            if (p.isDestroyed) continue
            if (sb.isNotEmpty()) sb.append(";")
            sb.append(p.instanceId).append("|")
                .append(p.definition.id).append("|")
                .append(String.format(Locale.US, "%.3f", p.position.x)).append("|")
                .append(String.format(Locale.US, "%.3f", p.position.y)).append("|")
                .append(String.format(Locale.US, "%.3f", p.position.z)).append("|")
                .append(String.format(Locale.US, "%.1f", p.rotationYaw)).append("|")
                .append(String.format(Locale.US, "%.2f", p.pillarHeight)).append("|")
                .append(String.format(Locale.US, "%.1f", p.health)).append("|")
                .append(if (p.isOpen) "1" else "0").append("|")
                .append(if (p.isLit) "1" else "0")
        }
        return sb.toString()
    }

    fun deserialize(data: String) {
        placedPieces.clear()
        inspectedPiece = null
        if (data.isBlank()) return

        val seenIds = mutableSetOf<String>()
        var maxNumericId = 0

        val entries = data.split(";")
        for (entry in entries) {
            val parts = entry.trim().split("|")
            if (parts.size < 8) continue
            try {
                val instanceId = parts[0].trim()
                if (instanceId.isEmpty() || !seenIds.add(instanceId)) continue

                val defId = parts[1].trim()
                val def = BuildingCatalog.get(defId) ?: continue

                val x = parts[2].toFloat()
                val y = parts[3].toFloat()
                val z = parts[4].toFloat()
                val yaw = parts[5].toFloat()
                val pillarH = parts[6].toFloat().coerceIn(def.minHeight, def.maxHeight)
                val hp = parts[7].toFloat().coerceIn(1f, def.maxHealth)
                val isOpen = parts.getOrNull(8) == "1"
                val isLit = if (parts.size >= 10) parts[9] == "1" else def.emitsLight

                val modelInst = buildingModelFactory?.createSolidInstance(def)
                val piece = PlacedBuildingPiece(
                    instanceId = instanceId,
                    definition = def,
                    position = Vector3(x, y, z),
                    rotationYaw = ((yaw % 360f) + 360f) % 360f,
                    pillarHeight = pillarH,
                    health = hp,
                    isOpen = isOpen,
                    isLit = isLit,
                    modelInstance = modelInst
                )
                val groundY = worldManager.getTerrainHeight(x, z)
                piece.isGroundedDirectly = abs(y - groundY) <= 0.48f
                piece.updateTransform()
                placedPieces.add(piece)

                val numSuffix = instanceId.removePrefix("bld_").toIntOrNull()
                if (numSuffix != null && numSuffix > maxNumericId) {
                    maxNumericId = numSuffix
                }
            } catch (_: Throwable) {
                // Skip malformed entry safely for backward compatibility
            }
        }
        nextPieceCounter = max(nextPieceCounter, maxNumericId + 1)
    }
}
