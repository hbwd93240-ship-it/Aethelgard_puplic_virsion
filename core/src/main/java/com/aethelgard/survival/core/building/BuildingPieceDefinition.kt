package com.aethelgard.survival.core.building

import com.aethelgard.survival.core.survival.Inventory
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

enum class BuildingCategory(val displayName: String, val shortLabel: String) {
    FLOORS("Floors & Bridges", "FLR"),
    WALLS("Walls", "WAL"),
    ROOFS("Roofs", "ROF"),
    PILLARS("Pillars", "PIL"),
    FOUNDATIONS("Foundations", "FND"),
    DOORS("Doors & Gates", "DOR"),
    WINDOWS("Windows", "WIN"),
    STAIRS("Stairs", "STR"),
    FENCES("Fences & Barriers", "FNC"),
    FURNITURE("Furniture & Decor", "FRN"),
    TERRITORY("Territory Marker", "WRD");

    val shortCode: String
        get() = shortLabel
}

enum class BuildingMaterial(
    val displayName: String,
    val durabilityMultiplier: Float,
    val monsterDamageReduction: Float,
    val primaryResourceId: String,
    val badgeColorHex: String
) {
    WOOD("Wood", 1.0f, 1.0f, "wood", "#D97706"),
    STONE("Stone", 2.4f, 0.55f, "stone", "#94A3B8"),
    BRICK("Brick", 2.8f, 0.45f, "stone", "#EF4444"),
    METAL("Metal", 3.8f, 0.30f, "iron_ore", "#38BDF8");

    val monsterDamageResistance: Float
        get() = monsterDamageReduction

    fun nextUpgradeTier(): BuildingMaterial? = when (this) {
        WOOD -> STONE
        STONE -> BRICK
        BRICK -> METAL
        METAL -> null
    }
}

enum class BuildingInteractionType(val promptPrefix: String) {
    NONE(""),
    DOOR_TOGGLE("OPEN / CLOSE DOOR"),
    SLEEP_BED("REST IN BED"),
    STORAGE_CHEST("OPEN STORAGE CHEST"),
    CRAFTING_TABLE("USE CRAFTING TABLE"),
    LIGHT_TOGGLE("TOGGLE LIGHT"),
    CHAIR_SIT("REST ON CHAIR"),
    TERRITORY_INSPECT("INSPECT WARD");

    companion object {
        val BED_REST = SLEEP_BED
        val CRAFTING_STATION = CRAFTING_TABLE
        val SIT_REST = CHAIR_SIT
        val LIGHT_SOURCE = LIGHT_TOGGLE
        val TERRITORY_MARKER = TERRITORY_INSPECT
    }
}

enum class SocketType {
    FLOOR_EDGE,
    WALL_BOTTOM,
    WALL_TOP,
    WALL_SIDE,
    PILLAR_TOP,
    PILLAR_BOTTOM,
    ROOF_EDGE,
    DOOR_OPENING,
    WINDOW_OPENING,
    FENCE_END,
    STAIR_BASE,
    STAIR_TOP
}

data class BuildingConnectionPoint(
    val id: String,
    val socketType: SocketType,
    val localOffset: Vector3,
    val yawOffsetDeg: Float = 0f
)

enum class SupportRequirement(val description: String) {
    GROUND_ONLY("Requires solid ground"),
    GROUND_OR_SUPPORT("Requires ground, foundation, floor, wall, or pillar"),
    WALL_OR_PILLAR_ONLY("Requires wall, pillar, or supported roof edge"),
    FLOOR_OR_GROUND("Requires floor, foundation, or level ground"),
    BRIDGE_SPAN("Requires ground, floor, or pillar anchor within bridge span")
}

/**
 * Scalable definition for a modular placeable building piece or interior furniture item.
 */
data class BuildingPieceDefinition(
    val id: String,
    val name: String,
    val category: BuildingCategory,
    val material: BuildingMaterial,
    val resourceCosts: Map<String, Int>,
    val width: Float,
    val height: Float,
    val depth: Float,
    val maxHealth: Float,
    val supportRequirement: SupportRequirement,
    val connectionPoints: List<BuildingConnectionPoint>,
    val interactionType: BuildingInteractionType = BuildingInteractionType.NONE,
    val modelId: String = id,
    val upgradeTargetId: String? = null,
    val allowVerticalWallStacking: Boolean = false,
    val supportsAdjustableHeight: Boolean = false,
    val minHeight: Float = height,
    val maxHeight: Float = height,
    val heightIncrement: Float = 0.5f,
    val isSolidObstacle: Boolean = true,
    val isWalkableSurface: Boolean = false,
    val emitsLight: Boolean = false,
    val lightRadius: Float = 0f,
    val badgeSymbol: String = "BLD"
) {
    val costs: Map<String, Int>
        get() = resourceCosts

    val isAdjustableHeight: Boolean
        get() = supportsAdjustableHeight
}

/**
 * Live runtime instance of a placed building piece in the survival world.
 */
class PlacedBuildingPiece(
    val instanceId: String,
    var definition: BuildingPieceDefinition,
    val position: Vector3,
    var rotationYaw: Float,
    var pillarHeight: Float = definition.height,
    var health: Float = definition.maxHealth,
    var isOpen: Boolean = false,
    var isLit: Boolean = definition.emitsLight,
    var modelInstance: ModelInstance? = null
) {
    var isDoorOpen: Boolean
        get() = isOpen
        set(value) {
            isOpen = value
            updateTransform()
        }

    var customHeight: Float
        get() = pillarHeight
        set(value) {
            pillarHeight = value
            updateTransform()
        }

    val maxHealth: Float
        get() = definition.maxHealth

    val isDamaged: Boolean
        get() = health < maxHealth - 0.01f

    val isDestroyed: Boolean
        get() = health <= 0f

    val healthRatio: Float
        get() = (health / maxHealth.coerceAtLeast(1f)).coerceIn(0f, 1f)

    val effectiveHeight: Float
        get() = if (definition.supportsAdjustableHeight) {
            pillarHeight.coerceIn(definition.minHeight, definition.maxHeight)
        } else {
            definition.height
        }

    var shakeTimer: Float = 0f
    var isGroundedDirectly: Boolean = false

    // Optional local storage buffer so destroying a chest/wall never deletes stored items
    val localStorageBackup: Inventory = Inventory(20)

    fun worldSocketPosition(socket: BuildingConnectionPoint, out: Vector3 = Vector3()): Vector3 {
        val rad = Math.toRadians(rotationYaw.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        val lx = socket.localOffset.x
        val ly = if (definition.supportsAdjustableHeight && socket.socketType == SocketType.PILLAR_TOP) {
            effectiveHeight
        } else {
            socket.localOffset.y
        }
        val lz = socket.localOffset.z
        out.set(
            position.x + (lx * c + lz * s),
            position.y + ly,
            position.z + (-lx * s + lz * c)
        )
        return out
    }

    fun updateTransform() {
        val inst = modelInstance ?: return
        val yScale = if (definition.supportsAdjustableHeight && definition.height > 0.01f) {
            effectiveHeight / definition.height
        } else {
            1f
        }
        val doorYawOffset = if (
            (definition.category == BuildingCategory.DOORS || definition.interactionType == BuildingInteractionType.DOOR_TOGGLE) && isOpen
        ) {
            85f
        } else {
            0f
        }
        val wobble = if (shakeTimer > 0f) sin(shakeTimer * 48f) * 3.5f else 0f
        inst.transform.idt()
            .translate(position.x, position.y, position.z)
            .rotate(0f, 1f, 0f, rotationYaw + doorYawOffset + wobble)
            .scale(1f, yScale, 1f)
    }
}

object BuildingCatalog {

    private fun floorSockets(halfW: Float = 1.5f, halfD: Float = 1.5f, thickness: Float = 0.20f): List<BuildingConnectionPoint> = listOf(
        BuildingConnectionPoint("edge_n", SocketType.FLOOR_EDGE, Vector3(0f, 0f, -halfD), 0f),
        BuildingConnectionPoint("edge_s", SocketType.FLOOR_EDGE, Vector3(0f, 0f, halfD), 180f),
        BuildingConnectionPoint("edge_e", SocketType.FLOOR_EDGE, Vector3(halfW, 0f, 0f), 90f),
        BuildingConnectionPoint("edge_w", SocketType.FLOOR_EDGE, Vector3(-halfW, 0f, 0f), 270f),
        BuildingConnectionPoint("wall_n", SocketType.WALL_BOTTOM, Vector3(0f, thickness, -halfD), 0f),
        BuildingConnectionPoint("wall_s", SocketType.WALL_BOTTOM, Vector3(0f, thickness, halfD), 180f),
        BuildingConnectionPoint("wall_e", SocketType.WALL_BOTTOM, Vector3(halfW, thickness, 0f), 90f),
        BuildingConnectionPoint("wall_w", SocketType.WALL_BOTTOM, Vector3(-halfW, thickness, 0f), 270f),
        BuildingConnectionPoint("corner_ne", SocketType.PILLAR_BOTTOM, Vector3(halfW, thickness, -halfD), 0f),
        BuildingConnectionPoint("corner_nw", SocketType.PILLAR_BOTTOM, Vector3(-halfW, thickness, -halfD), 0f),
        BuildingConnectionPoint("corner_se", SocketType.PILLAR_BOTTOM, Vector3(halfW, thickness, halfD), 0f),
        BuildingConnectionPoint("corner_sw", SocketType.PILLAR_BOTTOM, Vector3(-halfW, thickness, halfD), 0f)
    )

    private fun wallSockets(halfW: Float = 1.5f, wallH: Float = 3.0f): List<BuildingConnectionPoint> = listOf(
        BuildingConnectionPoint("bottom", SocketType.WALL_BOTTOM, Vector3(0f, 0f, 0f), 0f),
        BuildingConnectionPoint("top", SocketType.WALL_TOP, Vector3(0f, wallH, 0f), 0f),
        BuildingConnectionPoint("side_l", SocketType.WALL_SIDE, Vector3(-halfW, 0f, 0f), 0f),
        BuildingConnectionPoint("side_r", SocketType.WALL_SIDE, Vector3(halfW, 0f, 0f), 0f),
        BuildingConnectionPoint("roof_mount", SocketType.ROOF_EDGE, Vector3(0f, wallH, 0f), 0f)
    )

    private fun pillarSockets(defaultH: Float = 3.0f): List<BuildingConnectionPoint> = listOf(
        BuildingConnectionPoint("bottom", SocketType.PILLAR_BOTTOM, Vector3(0f, 0f, 0f), 0f),
        BuildingConnectionPoint("top", SocketType.PILLAR_TOP, Vector3(0f, defaultH, 0f), 0f)
    )

    private fun roofSockets(halfW: Float = 1.5f, halfD: Float = 1.5f): List<BuildingConnectionPoint> = listOf(
        BuildingConnectionPoint("edge_n", SocketType.ROOF_EDGE, Vector3(0f, 0f, -halfD), 0f),
        BuildingConnectionPoint("edge_s", SocketType.ROOF_EDGE, Vector3(0f, 0f, halfD), 180f),
        BuildingConnectionPoint("edge_e", SocketType.ROOF_EDGE, Vector3(halfW, 0f, 0f), 90f),
        BuildingConnectionPoint("edge_w", SocketType.ROOF_EDGE, Vector3(-halfW, 0f, 0f), 270f)
    )

    private fun fenceSockets(halfW: Float = 1.5f): List<BuildingConnectionPoint> = listOf(
        BuildingConnectionPoint("end_l", SocketType.FENCE_END, Vector3(-halfW, 0f, 0f), 0f),
        BuildingConnectionPoint("end_r", SocketType.FENCE_END, Vector3(halfW, 0f, 0f), 0f)
    )

    // 1. FLOORS
    val WOOD_FLOOR = BuildingPieceDefinition(
        id = "wood_floor",
        name = "Wooden Floor",
        category = BuildingCategory.FLOORS,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 2),
        width = 3.0f,
        height = 0.22f,
        depth = 3.0f,
        maxHealth = 140f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = floorSockets(thickness = 0.22f),
        upgradeTargetId = "stone_floor",
        isSolidObstacle = false,
        isWalkableSurface = true,
        badgeSymbol = "FLW"
    )

    val STONE_FLOOR = BuildingPieceDefinition(
        id = "stone_floor",
        name = "Stone Slab Floor",
        category = BuildingCategory.FLOORS,
        material = BuildingMaterial.STONE,
        resourceCosts = mapOf("stone" to 5),
        width = 3.0f,
        height = 0.22f,
        depth = 3.0f,
        maxHealth = 320f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = floorSockets(thickness = 0.22f),
        upgradeTargetId = "brick_floor",
        isSolidObstacle = false,
        isWalkableSurface = true,
        badgeSymbol = "FLS"
    )

    val BRICK_FLOOR = BuildingPieceDefinition(
        id = "brick_floor",
        name = "Runic Brick Floor",
        category = BuildingCategory.FLOORS,
        material = BuildingMaterial.BRICK,
        resourceCosts = mapOf("stone" to 4, "coal" to 2),
        width = 3.0f,
        height = 0.22f,
        depth = 3.0f,
        maxHealth = 380f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = floorSockets(thickness = 0.22f),
        isSolidObstacle = false,
        isWalkableSurface = true,
        badgeSymbol = "FLB"
    )

    val WOOD_BRIDGE = BuildingPieceDefinition(
        id = "wood_bridge_floor",
        name = "Timber Bridge Span",
        category = BuildingCategory.FLOORS,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 5, "fibers" to 2),
        width = 3.0f,
        height = 0.24f,
        depth = 3.0f,
        maxHealth = 180f,
        supportRequirement = SupportRequirement.BRIDGE_SPAN,
        connectionPoints = floorSockets(thickness = 0.24f),
        isSolidObstacle = false,
        isWalkableSurface = true,
        badgeSymbol = "BRG"
    )

    // 2. WALLS (Fixed 3.0m height, vertical wall stacking prohibited)
    val WOOD_WALL = BuildingPieceDefinition(
        id = "wood_wall",
        name = "Wood Wall",
        category = BuildingCategory.WALLS,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 3),
        width = 3.0f,
        height = 3.0f,
        depth = 0.32f,
        maxHealth = 180f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = wallSockets(),
        upgradeTargetId = "stone_wall",
        allowVerticalWallStacking = false,
        isSolidObstacle = true,
        badgeSymbol = "WLW"
    )

    val STONE_WALL = BuildingPieceDefinition(
        id = "stone_wall",
        name = "Stone Wall",
        category = BuildingCategory.WALLS,
        material = BuildingMaterial.STONE,
        resourceCosts = mapOf("stone" to 6),
        width = 3.0f,
        height = 3.0f,
        depth = 0.36f,
        maxHealth = 420f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = wallSockets(),
        upgradeTargetId = "brick_wall",
        allowVerticalWallStacking = false,
        isSolidObstacle = true,
        badgeSymbol = "WLS"
    )

    val BRICK_WALL = BuildingPieceDefinition(
        id = "brick_wall",
        name = "Masonry Brick Wall",
        category = BuildingCategory.WALLS,
        material = BuildingMaterial.BRICK,
        resourceCosts = mapOf("stone" to 5, "copper_ore" to 2),
        width = 3.0f,
        height = 3.0f,
        depth = 0.36f,
        maxHealth = 500f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = wallSockets(),
        upgradeTargetId = "metal_wall",
        allowVerticalWallStacking = false,
        isSolidObstacle = true,
        badgeSymbol = "WLB"
    )

    val METAL_WALL = BuildingPieceDefinition(
        id = "metal_wall",
        name = "Reinforced Iron Wall",
        category = BuildingCategory.WALLS,
        material = BuildingMaterial.METAL,
        resourceCosts = mapOf("iron_ore" to 4, "stone" to 2),
        width = 3.0f,
        height = 3.0f,
        depth = 0.34f,
        maxHealth = 680f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = wallSockets(),
        allowVerticalWallStacking = false,
        isSolidObstacle = true,
        badgeSymbol = "WLM"
    )

    // 3. ROOFS (Require Wall or Pillar support)
    val WOOD_ROOF = BuildingPieceDefinition(
        id = "wood_roof",
        name = "Timber Gable Roof",
        category = BuildingCategory.ROOFS,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 4),
        width = 3.2f,
        height = 1.35f,
        depth = 3.2f,
        maxHealth = 150f,
        supportRequirement = SupportRequirement.WALL_OR_PILLAR_ONLY,
        connectionPoints = roofSockets(1.5f, 1.5f),
        upgradeTargetId = "stone_roof",
        isSolidObstacle = false,
        badgeSymbol = "RFW"
    )

    val WOOD_FLAT_ROOF: BuildingPieceDefinition
        get() = WOOD_ROOF

    val STONE_ROOF = BuildingPieceDefinition(
        id = "stone_roof",
        name = "Slate Tile Roof",
        category = BuildingCategory.ROOFS,
        material = BuildingMaterial.STONE,
        resourceCosts = mapOf("stone" to 5, "wood" to 2),
        width = 3.2f,
        height = 1.35f,
        depth = 3.2f,
        maxHealth = 340f,
        supportRequirement = SupportRequirement.WALL_OR_PILLAR_ONLY,
        connectionPoints = roofSockets(1.5f, 1.5f),
        upgradeTargetId = "brick_roof",
        isSolidObstacle = false,
        badgeSymbol = "RFS"
    )

    val BRICK_ROOF = BuildingPieceDefinition(
        id = "brick_roof",
        name = "Crimson Shingle Roof",
        category = BuildingCategory.ROOFS,
        material = BuildingMaterial.BRICK,
        resourceCosts = mapOf("stone" to 4, "coal" to 2),
        width = 3.2f,
        height = 1.35f,
        depth = 3.2f,
        maxHealth = 400f,
        supportRequirement = SupportRequirement.WALL_OR_PILLAR_ONLY,
        connectionPoints = roofSockets(1.5f, 1.5f),
        isSolidObstacle = false,
        badgeSymbol = "RFB"
    )

    // 4. PILLARS (Manually adjustable heights in 0.5m increments: 1.5m .. 4.5m)
    val WOOD_PILLAR = BuildingPieceDefinition(
        id = "wood_pillar",
        name = "Timber Support Pillar",
        category = BuildingCategory.PILLARS,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 3),
        width = 0.42f,
        height = 3.0f,
        depth = 0.42f,
        maxHealth = 160f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = pillarSockets(3.0f),
        upgradeTargetId = "stone_pillar",
        supportsAdjustableHeight = true,
        minHeight = 1.5f,
        maxHeight = 4.5f,
        heightIncrement = 0.5f,
        isSolidObstacle = true,
        badgeSymbol = "PLW"
    )

    val STONE_PILLAR = BuildingPieceDefinition(
        id = "stone_pillar",
        name = "Carved Stone Column",
        category = BuildingCategory.PILLARS,
        material = BuildingMaterial.STONE,
        resourceCosts = mapOf("stone" to 4),
        width = 0.46f,
        height = 3.0f,
        depth = 0.46f,
        maxHealth = 380f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = pillarSockets(3.0f),
        upgradeTargetId = "metal_pillar",
        supportsAdjustableHeight = true,
        minHeight = 1.5f,
        maxHeight = 4.5f,
        heightIncrement = 0.5f,
        isSolidObstacle = true,
        badgeSymbol = "PLS"
    )

    val METAL_PILLAR = BuildingPieceDefinition(
        id = "metal_pillar",
        name = "Iron Girder Pillar",
        category = BuildingCategory.PILLARS,
        material = BuildingMaterial.METAL,
        resourceCosts = mapOf("iron_ore" to 3, "stone" to 2),
        width = 0.44f,
        height = 3.0f,
        depth = 0.44f,
        maxHealth = 580f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = pillarSockets(3.0f),
        supportsAdjustableHeight = true,
        minHeight = 1.5f,
        maxHeight = 4.5f,
        heightIncrement = 0.5f,
        isSolidObstacle = true,
        badgeSymbol = "PLM"
    )

    // 5. FOUNDATIONS (Optional sturdy bases)
    val WOOD_FOUNDATION = BuildingPieceDefinition(
        id = "wood_foundation",
        name = "Timber Deck Foundation",
        category = BuildingCategory.FOUNDATIONS,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 6),
        width = 3.0f,
        height = 0.60f,
        depth = 3.0f,
        maxHealth = 220f,
        supportRequirement = SupportRequirement.GROUND_ONLY,
        connectionPoints = floorSockets(thickness = 0.60f),
        upgradeTargetId = "stone_foundation",
        isSolidObstacle = false,
        isWalkableSurface = true,
        badgeSymbol = "FNW"
    )

    val STONE_FOUNDATION = BuildingPieceDefinition(
        id = "stone_foundation",
        name = "Granite Block Foundation",
        category = BuildingCategory.FOUNDATIONS,
        material = BuildingMaterial.STONE,
        resourceCosts = mapOf("stone" to 7),
        width = 3.0f,
        height = 0.60f,
        depth = 3.0f,
        maxHealth = 520f,
        supportRequirement = SupportRequirement.GROUND_ONLY,
        connectionPoints = floorSockets(thickness = 0.60f),
        isSolidObstacle = false,
        isWalkableSurface = true,
        badgeSymbol = "FNS"
    )

    // 6. DOORS AND GATES
    val WOOD_DOOR = BuildingPieceDefinition(
        id = "wood_door",
        name = "Wooden Doorway",
        category = BuildingCategory.DOORS,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 4),
        width = 3.0f,
        height = 3.0f,
        depth = 0.34f,
        maxHealth = 160f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = wallSockets() + BuildingConnectionPoint("door_slot", SocketType.DOOR_OPENING, Vector3(0f, 0f, 0f)),
        interactionType = BuildingInteractionType.DOOR_TOGGLE,
        upgradeTargetId = "stone_door",
        allowVerticalWallStacking = false,
        isSolidObstacle = true,
        badgeSymbol = "DRW"
    )

    val STONE_DOOR = BuildingPieceDefinition(
        id = "stone_door",
        name = "Stone Arch Doorway",
        category = BuildingCategory.DOORS,
        material = BuildingMaterial.STONE,
        resourceCosts = mapOf("stone" to 6, "wood" to 2),
        width = 3.0f,
        height = 3.0f,
        depth = 0.36f,
        maxHealth = 380f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = wallSockets() + BuildingConnectionPoint("door_slot", SocketType.DOOR_OPENING, Vector3(0f, 0f, 0f)),
        interactionType = BuildingInteractionType.DOOR_TOGGLE,
        upgradeTargetId = "metal_door",
        allowVerticalWallStacking = false,
        isSolidObstacle = true,
        badgeSymbol = "DRS"
    )

    val METAL_DOOR = BuildingPieceDefinition(
        id = "metal_door",
        name = "Ironbound Vault Door",
        category = BuildingCategory.DOORS,
        material = BuildingMaterial.METAL,
        resourceCosts = mapOf("iron_ore" to 4, "hardwood" to 2),
        width = 3.0f,
        height = 3.0f,
        depth = 0.36f,
        maxHealth = 620f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = wallSockets() + BuildingConnectionPoint("door_slot", SocketType.DOOR_OPENING, Vector3(0f, 0f, 0f)),
        interactionType = BuildingInteractionType.DOOR_TOGGLE,
        allowVerticalWallStacking = false,
        isSolidObstacle = true,
        badgeSymbol = "DRM"
    )

    val WOOD_GATE = BuildingPieceDefinition(
        id = "wood_gate",
        name = "Palisade Yard Gate",
        category = BuildingCategory.DOORS,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 4, "branches" to 2),
        width = 3.0f,
        height = 1.6f,
        depth = 0.30f,
        maxHealth = 140f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = fenceSockets(),
        interactionType = BuildingInteractionType.DOOR_TOGGLE,
        isSolidObstacle = true,
        badgeSymbol = "GAT"
    )

    // 7. WINDOWS
    val WOOD_WINDOW = BuildingPieceDefinition(
        id = "wood_window",
        name = "Wooden Window Wall",
        category = BuildingCategory.WINDOWS,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 4, "branches" to 1),
        width = 3.0f,
        height = 3.0f,
        depth = 0.32f,
        maxHealth = 155f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = wallSockets() + BuildingConnectionPoint("win_slot", SocketType.WINDOW_OPENING, Vector3(0f, 1.4f, 0f)),
        upgradeTargetId = "stone_window",
        allowVerticalWallStacking = false,
        isSolidObstacle = true,
        badgeSymbol = "WNW"
    )

    val STONE_WINDOW = BuildingPieceDefinition(
        id = "stone_window",
        name = "Stone Mullion Window",
        category = BuildingCategory.WINDOWS,
        material = BuildingMaterial.STONE,
        resourceCosts = mapOf("stone" to 5, "wood" to 1),
        width = 3.0f,
        height = 3.0f,
        depth = 0.36f,
        maxHealth = 360f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = wallSockets() + BuildingConnectionPoint("win_slot", SocketType.WINDOW_OPENING, Vector3(0f, 1.4f, 0f)),
        upgradeTargetId = "brick_window",
        allowVerticalWallStacking = false,
        isSolidObstacle = true,
        badgeSymbol = "WNS"
    )

    val BRICK_WINDOW = BuildingPieceDefinition(
        id = "brick_window",
        name = "Arched Brick Window",
        category = BuildingCategory.WINDOWS,
        material = BuildingMaterial.BRICK,
        resourceCosts = mapOf("stone" to 4, "coal" to 2),
        width = 3.0f,
        height = 3.0f,
        depth = 0.36f,
        maxHealth = 440f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = wallSockets() + BuildingConnectionPoint("win_slot", SocketType.WINDOW_OPENING, Vector3(0f, 1.4f, 0f)),
        allowVerticalWallStacking = false,
        isSolidObstacle = true,
        badgeSymbol = "WNB"
    )

    // 8. STAIRS
    val WOOD_STAIRS = BuildingPieceDefinition(
        id = "wood_stairs",
        name = "Wooden Staircase",
        category = BuildingCategory.STAIRS,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 5),
        width = 2.2f,
        height = 1.6f,
        depth = 3.0f,
        maxHealth = 165f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = listOf(
            BuildingConnectionPoint("base", SocketType.STAIR_BASE, Vector3(0f, 0f, 1.5f), 0f),
            BuildingConnectionPoint("top", SocketType.STAIR_TOP, Vector3(0f, 1.6f, -1.5f), 0f)
        ),
        upgradeTargetId = "stone_stairs",
        isSolidObstacle = false,
        isWalkableSurface = true,
        badgeSymbol = "STW"
    )

    val STONE_STAIRS = BuildingPieceDefinition(
        id = "stone_stairs",
        name = "Chiseled Stone Steps",
        category = BuildingCategory.STAIRS,
        material = BuildingMaterial.STONE,
        resourceCosts = mapOf("stone" to 6),
        width = 2.2f,
        height = 1.6f,
        depth = 3.0f,
        maxHealth = 380f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = listOf(
            BuildingConnectionPoint("base", SocketType.STAIR_BASE, Vector3(0f, 0f, 1.5f), 0f),
            BuildingConnectionPoint("top", SocketType.STAIR_TOP, Vector3(0f, 1.6f, -1.5f), 0f)
        ),
        isSolidObstacle = false,
        isWalkableSurface = true,
        badgeSymbol = "STS"
    )

    // 9. FENCES AND DEFENSIVE BARRIERS
    val WOOD_FENCE = BuildingPieceDefinition(
        id = "wood_fence",
        name = "Wooden Split-Rail Fence",
        category = BuildingCategory.FENCES,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 2, "branches" to 2),
        width = 3.0f,
        height = 1.25f,
        depth = 0.26f,
        maxHealth = 110f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = fenceSockets(),
        upgradeTargetId = "spiked_palisade",
        isSolidObstacle = true,
        badgeSymbol = "FNW"
    )

    val SPIKED_PALISADE = BuildingPieceDefinition(
        id = "spiked_palisade",
        name = "Spiked Timber Barricade",
        category = BuildingCategory.FENCES,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("hardwood" to 3, "branches" to 3),
        width = 3.0f,
        height = 1.55f,
        depth = 0.38f,
        maxHealth = 220f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = fenceSockets(),
        upgradeTargetId = "stone_wall_barrier",
        isSolidObstacle = true,
        badgeSymbol = "SPK"
    )

    val STONE_BARRIER = BuildingPieceDefinition(
        id = "stone_wall_barrier",
        name = "Stone Parapet Barrier",
        category = BuildingCategory.FENCES,
        material = BuildingMaterial.STONE,
        resourceCosts = mapOf("stone" to 4),
        width = 3.0f,
        height = 1.40f,
        depth = 0.40f,
        maxHealth = 340f,
        supportRequirement = SupportRequirement.GROUND_OR_SUPPORT,
        connectionPoints = fenceSockets(),
        isSolidObstacle = true,
        badgeSymbol = "BNS"
    )

    // 10. FURNITURE AND INTERIOR DECORATIONS
    val FURNITURE_BED = BuildingPieceDefinition(
        id = "furniture_bed",
        name = "Survivor's Rest Bed",
        category = BuildingCategory.FURNITURE,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 4, "fibers" to 3),
        width = 1.4f,
        height = 0.75f,
        depth = 2.1f,
        maxHealth = 100f,
        supportRequirement = SupportRequirement.FLOOR_OR_GROUND,
        connectionPoints = emptyList(),
        interactionType = BuildingInteractionType.SLEEP_BED,
        isSolidObstacle = true,
        badgeSymbol = "BED"
    )

    val FURNITURE_CHEST = BuildingPieceDefinition(
        id = "furniture_chest",
        name = "Built Storage Chest",
        category = BuildingCategory.FURNITURE,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 6, "stone" to 2),
        width = 1.15f,
        height = 0.85f,
        depth = 0.80f,
        maxHealth = 160f,
        supportRequirement = SupportRequirement.FLOOR_OR_GROUND,
        connectionPoints = emptyList(),
        interactionType = BuildingInteractionType.STORAGE_CHEST,
        isSolidObstacle = true,
        badgeSymbol = "CST"
    )

    val FURNITURE_CRAFTING_TABLE = BuildingPieceDefinition(
        id = "furniture_crafting_table",
        name = "Artisan Workbench",
        category = BuildingCategory.FURNITURE,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 5, "stone" to 2),
        width = 1.45f,
        height = 0.95f,
        depth = 0.95f,
        maxHealth = 170f,
        supportRequirement = SupportRequirement.FLOOR_OR_GROUND,
        connectionPoints = emptyList(),
        interactionType = BuildingInteractionType.CRAFTING_TABLE,
        isSolidObstacle = true,
        badgeSymbol = "WKB"
    )

    val FURNITURE_CHAIR = BuildingPieceDefinition(
        id = "furniture_chair",
        name = "Carved Oak Chair",
        category = BuildingCategory.FURNITURE,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 2),
        width = 0.75f,
        height = 0.95f,
        depth = 0.75f,
        maxHealth = 75f,
        supportRequirement = SupportRequirement.FLOOR_OR_GROUND,
        connectionPoints = emptyList(),
        interactionType = BuildingInteractionType.CHAIR_SIT,
        isSolidObstacle = true,
        badgeSymbol = "CHR"
    )

    val FURNITURE_TABLE = BuildingPieceDefinition(
        id = "furniture_table",
        name = "Timber Banquet Table",
        category = BuildingCategory.FURNITURE,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 4),
        width = 1.6f,
        height = 0.85f,
        depth = 1.2f,
        maxHealth = 110f,
        supportRequirement = SupportRequirement.FLOOR_OR_GROUND,
        connectionPoints = emptyList(),
        isSolidObstacle = true,
        badgeSymbol = "TBL"
    )

    val FURNITURE_TORCH = BuildingPieceDefinition(
        id = "furniture_torch",
        name = "Standing Torch Brazier",
        category = BuildingCategory.FURNITURE,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 2, "coal" to 1),
        width = 0.45f,
        height = 1.45f,
        depth = 0.45f,
        maxHealth = 65f,
        supportRequirement = SupportRequirement.FLOOR_OR_GROUND,
        connectionPoints = emptyList(),
        interactionType = BuildingInteractionType.LIGHT_TOGGLE,
        isSolidObstacle = false,
        emitsLight = true,
        lightRadius = 11f,
        badgeSymbol = "TRC"
    )

    val FURNITURE_LANTERN = BuildingPieceDefinition(
        id = "furniture_lantern",
        name = "Runic Mana Lantern",
        category = BuildingCategory.FURNITURE,
        material = BuildingMaterial.METAL,
        resourceCosts = mapOf("iron_ore" to 1, "magic_crystals" to 1),
        width = 0.48f,
        height = 0.95f,
        depth = 0.48f,
        maxHealth = 110f,
        supportRequirement = SupportRequirement.FLOOR_OR_GROUND,
        connectionPoints = emptyList(),
        interactionType = BuildingInteractionType.LIGHT_TOGGLE,
        isSolidObstacle = false,
        emitsLight = true,
        lightRadius = 15f,
        badgeSymbol = "LNT"
    )

    val FURNITURE_SHELF = BuildingPieceDefinition(
        id = "furniture_shelf",
        name = "Storage Supply Rack",
        category = BuildingCategory.FURNITURE,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("wood" to 3, "branches" to 1),
        width = 1.3f,
        height = 1.5f,
        depth = 0.55f,
        maxHealth = 95f,
        supportRequirement = SupportRequirement.FLOOR_OR_GROUND,
        connectionPoints = emptyList(),
        isSolidObstacle = true,
        badgeSymbol = "SHF"
    )

    val FURNITURE_BANNER = BuildingPieceDefinition(
        id = "furniture_banner",
        name = "Sanctuary Crest Banner",
        category = BuildingCategory.FURNITURE,
        material = BuildingMaterial.WOOD,
        resourceCosts = mapOf("branches" to 2, "fibers" to 2),
        width = 0.85f,
        height = 1.95f,
        depth = 0.35f,
        maxHealth = 80f,
        supportRequirement = SupportRequirement.FLOOR_OR_GROUND,
        connectionPoints = emptyList(),
        isSolidObstacle = false,
        badgeSymbol = "BNR"
    )

    // 11. OPTIONAL TERRITORY MARKER
    val TERRITORY_MARKER = BuildingPieceDefinition(
        id = "territory_marker",
        name = "Sanctuary Ward Obelisk",
        category = BuildingCategory.TERRITORY,
        material = BuildingMaterial.STONE,
        resourceCosts = mapOf("stone" to 5, "magic_crystals" to 1),
        width = 0.95f,
        height = 2.2f,
        depth = 0.95f,
        maxHealth = 450f,
        supportRequirement = SupportRequirement.GROUND_ONLY,
        connectionPoints = emptyList(),
        interactionType = BuildingInteractionType.TERRITORY_INSPECT,
        isSolidObstacle = true,
        emitsLight = true,
        lightRadius = 18f,
        badgeSymbol = "WRD"
    )

    val ALL_PIECES: List<BuildingPieceDefinition> = listOf(
        WOOD_FLOOR, STONE_FLOOR, BRICK_FLOOR, WOOD_BRIDGE,
        WOOD_WALL, STONE_WALL, BRICK_WALL, METAL_WALL,
        WOOD_ROOF, STONE_ROOF, BRICK_ROOF,
        WOOD_PILLAR, STONE_PILLAR, METAL_PILLAR,
        WOOD_FOUNDATION, STONE_FOUNDATION,
        WOOD_DOOR, STONE_DOOR, METAL_DOOR, WOOD_GATE,
        WOOD_WINDOW, STONE_WINDOW, BRICK_WINDOW,
        WOOD_STAIRS, STONE_STAIRS,
        WOOD_FENCE, SPIKED_PALISADE, STONE_BARRIER,
        FURNITURE_BED, FURNITURE_CHEST, FURNITURE_CRAFTING_TABLE,
        FURNITURE_CHAIR, FURNITURE_TABLE, FURNITURE_TORCH,
        FURNITURE_LANTERN, FURNITURE_SHELF, FURNITURE_BANNER,
        TERRITORY_MARKER
    )

    private val pieceById: Map<String, BuildingPieceDefinition> = ALL_PIECES.associateBy { it.id }

    fun get(id: String): BuildingPieceDefinition? = pieceById[id]

    fun getByCategory(category: BuildingCategory): List<BuildingPieceDefinition> =
        ALL_PIECES.filter { it.category == category }

    fun byCategory(category: BuildingCategory): List<BuildingPieceDefinition> =
        getByCategory(category)

    fun findUpgradeTarget(def: BuildingPieceDefinition): BuildingPieceDefinition? =
        def.upgradeTargetId?.let { get(it) }
}
