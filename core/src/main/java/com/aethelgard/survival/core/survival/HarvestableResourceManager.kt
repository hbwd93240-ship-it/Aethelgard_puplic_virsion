package com.aethelgard.survival.core.survival

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.combat.CombatSystem
import com.aethelgard.survival.core.entities.GroundPickupManager
import com.aethelgard.survival.core.player.AnimationState
import com.aethelgard.survival.core.player.PlayerCharacter
import com.aethelgard.survival.core.world.ObstacleCollider
import com.aethelgard.survival.core.world.ProceduralModelFactory
import com.aethelgard.survival.core.world.WorldManager
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class HarvestNodeType(val displayName: String) {
    TREE("Fantasy Tree"),
    BOULDER("Mossy Mineral Boulder"),
    CRYSTAL_BOULDER("Crystal Ore Outcrop"),
    NATURE_HERB("Wild Herb & Fiber Patch"),
    BERRY_BUSH("Wild Berry Bush"),
    MUSHROOM_PATCH("Forest Mushroom Cluster"),
    ROOT_PATCH("Ancient Root & Soil Mound"),
    MAGIC_CRYSTAL("Mana Crystal Node"),
    ANCIENT_MONUMENT("Ancient Rune Obelisk"),
    CRAFTING_TABLE("Crafting Table"),
    STORAGE_CHEST("Storage Chest")
}

data class HarvestDrop(
    val itemId: String,
    val minCount: Int,
    val maxCount: Int,
    val chance: Float = 1.0f,
    val minToolTier: Int = 0
)

data class FallingRockFragment(
    val nodeId: String,
    val instance: ModelInstance?,
    val position: Vector3,
    val velocity: Vector3,
    val groundY: Float,
    var rotationDeg: Float = 0f,
    val rotSpeedDeg: Float = 210f,
    var hasLanded: Boolean = false,
    var landedTimer: Float = 0f,
    val dropItemId: String = ResourceCatalog.STONE.id,
    val dropCount: Int = 1,
    val bonusDrops: List<Pair<String, Int>> = emptyList()
)

class HarvestableNode(
    val id: String,
    val type: HarvestNodeType,
    val position: Vector3,
    var instance: ModelInstance?,
    val maxHealth: Float,
    var health: Float = maxHealth,
    var collider: ObstacleCollider? = null,
    val drops: List<HarvestDrop>,
    val destructionBonusDrops: List<HarvestDrop> = emptyList(),
    var isDestroyed: Boolean = false,
    val radius: Float = 1.6f,
    val baseScale: Float = 1.0f,
    val initialYawDeg: Float = 0f,
    val yOffset: Float = -0.15f
) {
    var shakeTimer: Float = 0f
    var shakeIntensity: Float = 0f
    var lastHitDamage: Float = 0f

    // Tree physical falling state
    var isFalling: Boolean = false
    var hasSettledOnGround: Boolean = false
    var hasSpawnedDrops: Boolean = false
    var fallAngleDeg: Float = 0f
    var fallAngularVelocity: Float = 0f
    val maxFallAngleDeg: Float = 86f
    var fallDirX: Float = 1f
    var fallDirZ: Float = 0f
    var pendingWoodTotal: Int = 0
    var lastToolTier: Int = 0
    var lastBonusYield: Int = 0

    // Rock progressive damage / cracking state
    var damageStage: Int = 0
    var crackInstance: ModelInstance? = null
    var pendingStoneTotal: Int = 0
    var lastChanceBoost: Float = 0f

    fun triggerHit(damage: Float) {
        lastHitDamage = damage
        health = (health - damage).coerceAtLeast(0f)
        shakeTimer = 0.28f
        shakeIntensity = minOf(1.0f, 0.35f + (damage / 25f) * 0.45f)
        if (health <= 0f) {
            isDestroyed = true
        }
    }
}

/**
 * Manages all harvestable resources (trees, mineral boulders, crystal ore outcrops,
 * herb/fiber patches, berry bushes, mushroom clusters, root patches, chests, and crafting tables)
 * within the existing world map without replacing the terrain or decorative nature.
 *
 * Implements:
 * - Natural Tree Falling & Wood Log Pickups (no instant disappearance or direct-to-inventory wood before pickup)
 * - Progressive Rock Cracking, Falling 3D Rock Fragments & Stone Pickups (no direct-to-inventory stone before pickup)
 * - Unified Physical Ground Pickups for all harvestable drops
 */
class HarvestableResourceManager {

    val nodes = mutableListOf<HarvestableNode>()
    private val natureInstances = mutableListOf<HarvestableNode>()
    val fallingRockFragments = mutableListOf<FallingRockFragment>()
    private val rng = Random(42)
    var worldManager: WorldManager? = null
        private set
    var groundPickupManager: GroundPickupManager? = null
    private var modelFactory: ProceduralModelFactory? = null

    private var crackStage1Model: Model? = null
    private var crackStage2Model: Model? = null
    private val stoneFragmentModels = mutableListOf<Model>()

    // Dedicated custom placeable structures
    var craftingTableInstance: ModelInstance? = null
    var storageChestInstance: ModelInstance? = null

    var craftingTableNode: HarvestableNode? = null
    var storageChestNode: HarvestableNode? = null

    fun initialize(
        worldManager: WorldManager,
        craftingTableInstance: ModelInstance,
        storageChestInstance: ModelInstance,
        modelFactory: ProceduralModelFactory? = null
    ) {
        this.worldManager = worldManager
        this.modelFactory = modelFactory
        nodes.clear()
        natureInstances.clear()
        fallingRockFragments.clear()
        this.craftingTableInstance = craftingTableInstance
        this.storageChestInstance = storageChestInstance

        if (modelFactory != null) {
            crackStage1Model = modelFactory.createRockCrackOverlayModel(1)
            crackStage2Model = modelFactory.createRockCrackOverlayModel(2)
            stoneFragmentModels.clear()
            stoneFragmentModels.add(modelFactory.createStoneFragmentModel(0))
            stoneFragmentModels.add(modelFactory.createStoneFragmentModel(1))
            stoneFragmentModels.add(modelFactory.createStoneFragmentModel(2))
        }

        // 1. Position Crafting Table and Storage Chest in the clearing along the sanctuary avenue
        val craftX = 3.2f
        val craftZ = 14.5f
        val craftY = worldManager.getWalkableSurfaceHeight(craftX, craftZ)
        craftingTableInstance.transform.idt()
            .translate(craftX, craftY, craftZ)
            .rotate(0f, 1f, 0f, 35f)

        craftingTableNode = HarvestableNode(
            id = "crafting_table_01",
            type = HarvestNodeType.CRAFTING_TABLE,
            position = Vector3(craftX, craftY, craftZ),
            instance = craftingTableInstance,
            maxHealth = 9999f,
            drops = emptyList(),
            radius = 2.4f
        ).also { nodes.add(it) }

        val chestX = -3.2f
        val chestZ = 14.5f
        val chestY = worldManager.getWalkableSurfaceHeight(chestX, chestZ)
        storageChestInstance.transform.idt()
            .translate(chestX, chestY, chestZ)
            .rotate(0f, 1f, 0f, -35f)

        storageChestNode = HarvestableNode(
            id = "storage_chest_01",
            type = HarvestNodeType.STORAGE_CHEST,
            position = Vector3(chestX, chestY, chestZ),
            instance = storageChestInstance,
            maxHealth = 9999f,
            drops = emptyList(),
            radius = 2.4f
        ).also { nodes.add(it) }

        // 2. Wrap existing Trees from WorldGenerator
        val treeRing1Count = 12
        for (i in 0 until treeRing1Count) {
            val angleDeg = i * (360f / treeRing1Count) + 15f
            if (angleDeg in 76f..104f) continue
            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val radius = 21f + (i % 3) * 3.5f
            val tx = cos(rad) * radius
            val tz = sin(rad) * radius
            val ty = worldManager.getTerrainHeight(tx, tz)
            val scale = 0.92f + (i % 4) * 0.14f

            nodes.add(
                HarvestableNode(
                    id = "tree_r1_$i",
                    type = HarvestNodeType.TREE,
                    position = Vector3(tx, ty, tz),
                    instance = null,
                    maxHealth = 60f,
                    drops = listOf(
                        HarvestDrop(ResourceCatalog.WOOD.id, 1, 3, 1.0f),
                        HarvestDrop(ResourceCatalog.BRANCHES.id, 1, 2, 0.85f),
                        HarvestDrop(ResourceCatalog.HARDWOOD.id, 1, 1, 0.30f)
                    ),
                    destructionBonusDrops = listOf(
                        HarvestDrop(ResourceCatalog.HARDWOOD.id, 2, 3, 1.0f),
                        HarvestDrop(ResourceCatalog.ROOTS.id, 1, 2, 1.0f),
                        HarvestDrop(ResourceCatalog.WOOD.id, 4, 8, 1.0f)
                    ),
                    radius = 2.8f,
                    baseScale = scale,
                    initialYawDeg = (i * 67f) % 360f
                )
            )
        }

        // Outer Ring (20 trees)
        val treeRing2Count = 20
        for (i in 0 until treeRing2Count) {
            val angleDeg = i * (360f / treeRing2Count) + 7f
            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val radius = 37f + (i % 4) * 4.8f
            val tx = cos(rad) * radius
            val tz = sin(rad) * radius
            val ty = worldManager.getTerrainHeight(tx, tz)
            val scale = 1.05f + (i % 3) * 0.22f

            nodes.add(
                HarvestableNode(
                    id = "tree_r2_$i",
                    type = HarvestNodeType.TREE,
                    position = Vector3(tx, ty, tz),
                    instance = null,
                    maxHealth = 80f,
                    drops = listOf(
                        HarvestDrop(ResourceCatalog.WOOD.id, 2, 4, 1.0f),
                        HarvestDrop(ResourceCatalog.BRANCHES.id, 1, 3, 0.85f),
                        HarvestDrop(ResourceCatalog.HARDWOOD.id, 1, 2, 0.35f)
                    ),
                    destructionBonusDrops = listOf(
                        HarvestDrop(ResourceCatalog.HARDWOOD.id, 3, 5, 1.0f),
                        HarvestDrop(ResourceCatalog.ROOTS.id, 2, 3, 1.0f),
                        HarvestDrop(ResourceCatalog.WOOD.id, 6, 12, 1.0f)
                    ),
                    radius = 3.2f,
                    baseScale = scale,
                    initialYawDeg = (i * 113f) % 360f
                )
            )
        }

        // Wilderness Trees across Expanded World (42 trees)
        val wildernessTreeCount = 42
        for (i in 0 until wildernessTreeCount) {
            val goldenAngle = i * 137.508f + 19f
            val rad = Math.toRadians(goldenAngle.toDouble()).toFloat()
            val radius = 52f + (i.toFloat() / wildernessTreeCount) * 82f
            val tx = cos(rad) * radius
            val tz = sin(rad) * radius
            val ty = worldManager.getTerrainHeight(tx, tz)
            val scale = 0.95f + (i % 4) * 0.22f

            nodes.add(
                HarvestableNode(
                    id = "tree_wild_$i",
                    type = HarvestNodeType.TREE,
                    position = Vector3(tx, ty, tz),
                    instance = null,
                    maxHealth = 85f,
                    drops = listOf(
                        HarvestDrop(ResourceCatalog.WOOD.id, 2, 5, 1.0f),
                        HarvestDrop(ResourceCatalog.BRANCHES.id, 1, 3, 0.85f),
                        HarvestDrop(ResourceCatalog.HARDWOOD.id, 1, 2, 0.40f)
                    ),
                    destructionBonusDrops = listOf(
                        HarvestDrop(ResourceCatalog.HARDWOOD.id, 3, 6, 1.0f),
                        HarvestDrop(ResourceCatalog.ROOTS.id, 2, 4, 1.0f),
                        HarvestDrop(ResourceCatalog.WOOD.id, 6, 14, 1.0f)
                    ),
                    radius = 3.2f,
                    baseScale = scale,
                    initialYawDeg = (i * 79f) % 360f
                )
            )
        }

        // 3. Wrap existing Boulders & Crystal Ore Outcrops
        val boulderCount = 20
        for (i in 0 until boulderCount) {
            val angleDeg = i * (360f / boulderCount) + 23f
            if (angleDeg in 82f..98f) continue
            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val radius = if (i % 2 == 0) 15.5f + (i % 3) * 2.5f else 30f + (i % 4) * 3.5f
            val bx = cos(rad) * radius
            val bz = sin(rad) * radius
            val by = worldManager.getTerrainHeight(bx, bz)
            val scale = 0.75f + (i % 3) * 0.25f
            val withCrystals = (i % 2 == 0)

            nodes.add(
                HarvestableNode(
                    id = "boulder_$i",
                    type = if (withCrystals) HarvestNodeType.CRYSTAL_BOULDER else HarvestNodeType.BOULDER,
                    position = Vector3(bx, by, bz),
                    instance = null,
                    maxHealth = if (withCrystals) 90f else 70f,
                    drops = if (withCrystals) {
                        listOf(
                            HarvestDrop(ResourceCatalog.STONE.id, 2, 4, 1.0f),
                            HarvestDrop(ResourceCatalog.IRON_ORE.id, 1, 2, 0.90f),
                            HarvestDrop(ResourceCatalog.COPPER_ORE.id, 1, 2, 0.65f),
                            HarvestDrop(ResourceCatalog.COAL.id, 1, 2, 0.55f),
                            HarvestDrop(ResourceCatalog.RARE_ORE.id, 1, 1, 0.45f, minToolTier = 1),
                            HarvestDrop(ResourceCatalog.MAGIC_CRYSTALS.id, 1, 1, 0.45f)
                        )
                    } else {
                        listOf(
                            HarvestDrop(ResourceCatalog.STONE.id, 2, 5, 1.0f),
                            HarvestDrop(ResourceCatalog.COAL.id, 1, 3, 0.85f),
                            HarvestDrop(ResourceCatalog.COPPER_ORE.id, 1, 2, 0.80f),
                            HarvestDrop(ResourceCatalog.IRON_ORE.id, 1, 1, 0.50f)
                        )
                    },
                    destructionBonusDrops = listOf(
                        HarvestDrop(ResourceCatalog.STONE.id, 4, 8, 1.0f),
                        HarvestDrop(ResourceCatalog.COAL.id, 1, 2, 0.90f),
                        HarvestDrop(ResourceCatalog.COPPER_ORE.id, 1, 2, 0.85f),
                        HarvestDrop(ResourceCatalog.IRON_ORE.id, 1, 3, if (withCrystals) 1.0f else 0.70f),
                        HarvestDrop(ResourceCatalog.RARE_ORE.id, 1, 2, if (withCrystals) 0.90f else 0.45f)
                    ),
                    radius = 2.8f,
                    baseScale = scale,
                    initialYawDeg = (i * 83f) % 360f,
                    yOffset = -0.10f
                )
            )
        }

        // Wilderness Boulders across Expanded World (32 boulders)
        val wildernessBoulderCount = 32
        for (i in 0 until wildernessBoulderCount) {
            val goldenAngle = i * 137.508f + 43f
            val rad = Math.toRadians(goldenAngle.toDouble()).toFloat()
            val radius = 48f + (i.toFloat() / wildernessBoulderCount) * 85f
            val bx = cos(rad) * radius
            val bz = sin(rad) * radius
            val by = worldManager.getTerrainHeight(bx, bz)
            val scale = 0.85f + (i % 3) * 0.35f
            val withCrystals = (i % 3 != 1)

            nodes.add(
                HarvestableNode(
                    id = "boulder_wild_$i",
                    type = if (withCrystals) HarvestNodeType.CRYSTAL_BOULDER else HarvestNodeType.BOULDER,
                    position = Vector3(bx, by, bz),
                    instance = null,
                    maxHealth = if (withCrystals) 95f else 75f,
                    drops = if (withCrystals) {
                        listOf(
                            HarvestDrop(ResourceCatalog.STONE.id, 2, 5, 1.0f),
                            HarvestDrop(ResourceCatalog.IRON_ORE.id, 1, 3, 0.90f),
                            HarvestDrop(ResourceCatalog.COPPER_ORE.id, 1, 3, 0.75f),
                            HarvestDrop(ResourceCatalog.COAL.id, 1, 3, 0.65f),
                            HarvestDrop(ResourceCatalog.RARE_ORE.id, 1, 2, 0.50f, minToolTier = 1),
                            HarvestDrop(ResourceCatalog.MAGIC_CRYSTALS.id, 1, 1, 0.45f)
                        )
                    } else {
                        listOf(
                            HarvestDrop(ResourceCatalog.STONE.id, 3, 6, 1.0f),
                            HarvestDrop(ResourceCatalog.COAL.id, 1, 3, 0.85f),
                            HarvestDrop(ResourceCatalog.COPPER_ORE.id, 1, 3, 0.80f),
                            HarvestDrop(ResourceCatalog.IRON_ORE.id, 1, 2, 0.60f)
                        )
                    },
                    destructionBonusDrops = listOf(
                        HarvestDrop(ResourceCatalog.STONE.id, 5, 10, 1.0f),
                        HarvestDrop(ResourceCatalog.COAL.id, 2, 4, 0.90f),
                        HarvestDrop(ResourceCatalog.COPPER_ORE.id, 2, 4, 0.85f),
                        HarvestDrop(ResourceCatalog.IRON_ORE.id, 2, 4, if (withCrystals) 1.0f else 0.70f),
                        HarvestDrop(ResourceCatalog.RARE_ORE.id, 1, 3, if (withCrystals) 0.90f else 0.50f)
                    ),
                    radius = 3.0f,
                    baseScale = scale,
                    initialYawDeg = (i * 97f) % 360f,
                    yOffset = -0.12f
                )
            )
        }

        // 4. Nature Gathering Nodes across the existing clearing
        val herbModel = modelFactory?.createGrassClusterModel(isGlowingVariant = true)
        val berryBushModel = modelFactory?.createBerryBushModel()
        val mushroomModel = modelFactory?.createMushroomClusterModel()
        val rootPatchModel = modelFactory?.createRootPatchModel()

        val natureCount = 16
        for (i in 0 until natureCount) {
            val deg = i * (360f / natureCount) + 18f
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val dist = 8.5f + (i % 4) * 3.6f
            val hx = cos(rad) * dist
            val hz = sin(rad) * dist
            val hy = worldManager.getTerrainHeight(hx, hz)
            val yaw = (i * 53f) % 360f

            val nodeType = when (i % 4) {
                0 -> HarvestNodeType.NATURE_HERB
                1 -> HarvestNodeType.BERRY_BUSH
                2 -> HarvestNodeType.MUSHROOM_PATCH
                else -> HarvestNodeType.ROOT_PATCH
            }

            val modelForType = when (nodeType) {
                HarvestNodeType.NATURE_HERB -> herbModel
                HarvestNodeType.BERRY_BUSH -> berryBushModel
                HarvestNodeType.MUSHROOM_PATCH -> mushroomModel
                HarvestNodeType.ROOT_PATCH -> rootPatchModel
                else -> null
            }

            val inst = modelForType?.let {
                ModelInstance(it).apply {
                    transform.idt()
                        .translate(hx, hy, hz)
                        .rotate(0f, 1f, 0f, yaw)
                }
            }

            val dropsForType = when (nodeType) {
                HarvestNodeType.NATURE_HERB -> listOf(
                    HarvestDrop(ResourceCatalog.PLANTS.id, 2, 4, 1.0f),
                    HarvestDrop(ResourceCatalog.FIBERS.id, 2, 4, 1.0f),
                    HarvestDrop(ResourceCatalog.BERRIES.id, 1, 2, 0.50f)
                )
                HarvestNodeType.BERRY_BUSH -> listOf(
                    HarvestDrop(ResourceCatalog.BERRIES.id, 2, 5, 1.0f),
                    HarvestDrop(ResourceCatalog.PLANTS.id, 1, 2, 0.85f),
                    HarvestDrop(ResourceCatalog.FIBERS.id, 1, 2, 0.70f)
                )
                HarvestNodeType.MUSHROOM_PATCH -> listOf(
                    HarvestDrop(ResourceCatalog.MUSHROOMS.id, 2, 4, 1.0f),
                    HarvestDrop(ResourceCatalog.PLANTS.id, 1, 2, 0.75f),
                    HarvestDrop(ResourceCatalog.ROOTS.id, 1, 2, 0.65f)
                )
                else -> listOf(
                    HarvestDrop(ResourceCatalog.ROOTS.id, 2, 4, 1.0f),
                    HarvestDrop(ResourceCatalog.FIBERS.id, 1, 3, 0.85f),
                    HarvestDrop(ResourceCatalog.MUSHROOMS.id, 1, 2, 0.65f),
                    HarvestDrop(ResourceCatalog.ANIMAL_HIDES.id, 1, 1, 0.40f)
                )
            }

            val natureNode = HarvestableNode(
                id = "nature_herb_$i",
                type = nodeType,
                position = Vector3(hx, hy, hz),
                instance = inst,
                maxHealth = 30f,
                drops = dropsForType,
                destructionBonusDrops = listOf(
                    HarvestDrop(ResourceCatalog.PLANTS.id, 1, 2, 1.0f),
                    HarvestDrop(ResourceCatalog.FIBERS.id, 1, 2, 1.0f),
                    HarvestDrop(ResourceCatalog.ROOTS.id, 1, 2, 1.0f)
                ),
                radius = 2.3f,
                baseScale = 1.0f,
                initialYawDeg = yaw,
                yOffset = 0f
            )
            nodes.add(natureNode)
            natureInstances.add(natureNode)
        }

        // 5. Ancient Rune Monument interaction node
        nodes.add(
            HarvestableNode(
                id = "ancient_obelisk",
                type = HarvestNodeType.ANCIENT_MONUMENT,
                position = Vector3(0f, worldManager.getTerrainHeight(0f, 0f), 0f),
                instance = null,
                maxHealth = 120f,
                drops = listOf(
                    HarvestDrop(ResourceCatalog.MAGIC_CRYSTALS.id, 1, 2, 1.0f),
                    HarvestDrop(ResourceCatalog.ANCIENT_RELICS.id, 1, 1, 0.70f)
                ),
                radius = 3.6f
            )
        )
    }

    /**
     * Finds the nearest active harvestable node within [maxDistance] of [playerPosition].
     */
    fun findNearestNode(playerPosition: Vector3, maxDistance: Float = 3.5f): HarvestableNode? {
        var closest: HarvestableNode? = null
        var closestDistSq = maxDistance * maxDistance

        for (node in nodes) {
            if (node.isDestroyed) continue
            val dx = node.position.x - playerPosition.x
            val dz = node.position.z - playerPosition.z
            val distSq = dx * dx + dz * dz
            val triggerRadius = maxOf(node.radius, 2.4f)
            val maxTriggerSq = triggerRadius * triggerRadius
            if (distSq <= maxTriggerSq && distSq < closestDistSq) {
                closestDistSq = distSq
                closest = node
            }
        }
        return closest
    }

    /**
     * Determines the AnimationState corresponding to a [ToolType].
     */
    fun getAnimationForTool(toolType: ToolType): AnimationState? = when (toolType) {
        ToolType.AXE -> AnimationState.TOOL_AXE_CHOP
        ToolType.PICKAXE -> AnimationState.TOOL_PICKAXE_MINE
        ToolType.SHOVEL -> AnimationState.TOOL_SHOVEL_DIG
        ToolType.SICKLE -> AnimationState.TOOL_SICKLE_HARVEST
        ToolType.KNIFE -> AnimationState.TOOL_KNIFE_SLICE
        ToolType.HAMMER -> AnimationState.TOOL_HAMMER_BUILD
        ToolType.NONE -> null
    }

    /**
     * Harvests / chops / mines / digs / reaps / slices the given node.
     * - Trees do NOT add wood directly to inventory before falling; when HP reaches 0, the tree
     *   physically falls to the ground under gravity, settles, and spawns 3D wood log pickups.
     * - Rocks show progressive 3D cracking as mined and do NOT add stone directly to inventory;
     *   when HP reaches 0, the rock breaks into falling 3D stone fragments that spawn 3D stone pickups on landing.
     */
    fun harvestNode(
        node: HarvestableNode,
        player: PlayerCharacter,
        inventory: Inventory,
        audioManager: AudioManager,
        combatSystem: CombatSystem?,
        onCollected: (String, Int) -> Unit
    ): Boolean {
        if (node.isDestroyed) return false

        val activeSlotItem = inventory.activeHotbarItem
        if (activeSlotItem != null && activeSlotItem.isTool && activeSlotItem.isBroken) {
            audioManager.playUiClick()
            onCollected("TOOL BROKEN (REPAIR/CRAFT)", 0)
            return false
        }

        when (node.type) {
            HarvestNodeType.CRAFTING_TABLE -> return false
            HarvestNodeType.STORAGE_CHEST -> return false

            HarvestNodeType.TREE -> {
                val axeSlot = inventory.findBestUsableToolSlot(ToolType.AXE)
                val knifeSlot = inventory.findBestUsableToolSlot(ToolType.KNIFE)
                val activeDef = activeSlotItem?.takeIf { !it.isBroken }?.definition

                val usedSlot = when {
                    activeDef?.category == ItemCategory.TOOL -> inventory.activeHotbarIndex
                    axeSlot >= 0 -> axeSlot
                    knifeSlot >= 0 -> knifeSlot
                    else -> -1
                }
                val usedStack = if (usedSlot >= 0) inventory.slots[usedSlot] else null
                val usedDef = usedStack?.definition
                val isAxe = usedDef?.toolType == ToolType.AXE
                val isKnife = usedDef?.toolType == ToolType.KNIFE
                val toolPower = usedDef?.toolPower ?: 1.0f
                val toolTier = usedDef?.toolTier ?: 0

                val baseDmg = when {
                    isAxe -> 28f * toolPower
                    isKnife -> 18f * toolPower
                    usedDef?.category == ItemCategory.TOOL -> 16f * toolPower
                    player.isSwordEquipped -> 20f
                    else -> 14f
                }

                val toolAnim = getAnimationForTool(usedDef?.toolType ?: ToolType.NONE)
                if (toolAnim != null) {
                    player.startToolAction(toolAnim, usedDef?.id)
                }

                if (usedSlot >= 0) {
                    inventory.damageToolInSlot(usedSlot, 1)
                }

                node.triggerHit(baseDmg)
                combatSystem?.recordDamagePopup(
                    pos = Vector3(node.position.x, node.position.y + 2.1f, node.position.z),
                    damage = baseDmg,
                    isHeavy = isAxe && toolTier >= 2
                )

                audioManager.playMeleeHit(isHeavy = isAxe)
                combatSystem?.spawnParticleBurst(
                    origin = Vector3(node.position.x, node.position.y + 1.8f, node.position.z),
                    count = if (toolTier >= 2) 12 else 8,
                    speed = 4.2f,
                    variant = 1 // Gold / wood chips
                )

                val bonusYield = if (isAxe && toolTier >= 2) 1 else 0
                node.lastToolTier = maxOf(node.lastToolTier, toolTier)
                node.lastBonusYield = maxOf(node.lastBonusYield, bonusYield)
                node.pendingWoodTotal += 2 + bonusYield

                if (node.isDestroyed) {
                    // Remove upright trunk collider immediately, but DO NOT make the tree disappear!
                    // Start physical falling animation toward the ground away from the player.
                    node.collider?.let { worldManager?.removeObstacleCollider(it) }
                    val dx = node.position.x - player.position.x
                    val dz = node.position.z - player.position.z
                    val len = sqrt(dx * dx + dz * dz)
                    if (len > 0.05f) {
                        node.fallDirX = dx / len
                        node.fallDirZ = dz / len
                    } else {
                        val yawRad = Math.toRadians(node.initialYawDeg.toDouble()).toFloat()
                        node.fallDirX = cos(yawRad)
                        node.fallDirZ = sin(yawRad)
                    }
                    node.isFalling = true
                    node.hasSettledOnGround = false
                    node.fallAngleDeg = 2f
                    node.fallAngularVelocity = 24f

                    audioManager.playSwordHitImpact(isHeavy = true)
                    combatSystem?.spawnParticleBurst(
                        origin = Vector3(node.position.x, node.position.y + 1.0f, node.position.z),
                        count = 14,
                        speed = 4.5f,
                        variant = 1
                    )
                }
                return true
            }

            HarvestNodeType.BOULDER, HarvestNodeType.CRYSTAL_BOULDER -> {
                val pickSlot = inventory.findBestUsableToolSlot(ToolType.PICKAXE)
                val shovelSlot = inventory.findBestUsableToolSlot(ToolType.SHOVEL)
                val activeDef = activeSlotItem?.takeIf { !it.isBroken }?.definition

                val usedSlot = when {
                    activeDef?.category == ItemCategory.TOOL -> inventory.activeHotbarIndex
                    pickSlot >= 0 -> pickSlot
                    shovelSlot >= 0 -> shovelSlot
                    else -> -1
                }
                val usedStack = if (usedSlot >= 0) inventory.slots[usedSlot] else null
                val usedDef = usedStack?.definition
                val isPickaxe = usedDef?.toolType == ToolType.PICKAXE
                val toolPower = usedDef?.toolPower ?: 1.0f
                val toolTier = if (isPickaxe) usedDef?.toolTier ?: 1 else 0

                val baseDmg = when {
                    isPickaxe && toolTier >= 2 -> 28f * toolPower // 98 dmg
                    isPickaxe -> 26f * toolPower // 52 dmg
                    usedDef?.toolType == ToolType.SHOVEL -> 18f * toolPower
                    player.isSwordEquipped -> 14f
                    else -> 10f
                }

                val toolAnim = getAnimationForTool(usedDef?.toolType ?: ToolType.NONE)
                if (toolAnim != null) {
                    player.startToolAction(toolAnim, usedDef?.id)
                }

                if (usedSlot >= 0) {
                    inventory.damageToolInSlot(usedSlot, 1)
                }

                node.triggerHit(baseDmg)
                combatSystem?.recordDamagePopup(
                    pos = Vector3(node.position.x, node.position.y + 1.4f, node.position.z),
                    damage = baseDmg,
                    isHeavy = isPickaxe && toolTier >= 2
                )

                audioManager.playMeleeHit(isHeavy = true)
                combatSystem?.spawnParticleBurst(
                    origin = Vector3(node.position.x, node.position.y + 0.8f, node.position.z),
                    count = if (toolTier >= 2) 14 else 10,
                    speed = 4.8f,
                    variant = if (node.type == HarvestNodeType.CRYSTAL_BOULDER) 0 else 1
                )

                val bonusYield = if (isPickaxe && toolTier >= 2) 1 else 0
                val chanceBoost = if (isPickaxe && toolTier >= 2) 0.35f else if (isPickaxe) 0.15f else 0f
                node.lastToolTier = maxOf(node.lastToolTier, toolTier)
                node.lastBonusYield = maxOf(node.lastBonusYield, bonusYield)
                node.lastChanceBoost = maxOf(node.lastChanceBoost, chanceBoost)
                node.pendingStoneTotal += 2 + bonusYield

                // Progressive rock cracking while being mined (before destruction)
                if (!node.isDestroyed) {
                    val hpRatio = node.health / node.maxHealth
                    val newStage = when {
                        hpRatio <= 0.45f -> 2
                        hpRatio < 1.0f -> maxOf(1, node.damageStage)
                        else -> 0
                    }
                    if (newStage != node.damageStage || node.crackInstance == null) {
                        node.damageStage = newStage
                        val crackModel = if (newStage >= 2) crackStage2Model else crackStage1Model
                        if (crackModel != null) {
                            node.crackInstance = ModelInstance(crackModel).apply {
                                transform.idt()
                                    .translate(node.position.x, node.position.y + node.yOffset, node.position.z)
                                    .rotate(0f, 1f, 0f, node.initialYawDeg)
                                    .scale(node.baseScale, node.baseScale, node.baseScale)
                            }
                        }
                    }
                } else {
                    // Rock destroyed: break into visible 3D stone fragments that fall under gravity
                    node.damageStage = 3
                    node.crackInstance = null
                    destroyNode(node)
                    audioManager.playSwordHitImpact(isHeavy = true)
                    combatSystem?.spawnParticleBurst(node.position, count = 18, speed = 5.2f, variant = 1)
                    spawnFallingRockFragmentsForBoulder(node, isPickaxe && node.lastToolTier >= 2)
                }
                return true
            }

            HarvestNodeType.NATURE_HERB,
            HarvestNodeType.BERRY_BUSH,
            HarvestNodeType.MUSHROOM_PATCH,
            HarvestNodeType.ROOT_PATCH -> {
                val preferredToolType = when (node.type) {
                    HarvestNodeType.NATURE_HERB -> ToolType.SICKLE
                    HarvestNodeType.BERRY_BUSH -> ToolType.KNIFE
                    HarvestNodeType.MUSHROOM_PATCH -> ToolType.KNIFE
                    HarvestNodeType.ROOT_PATCH -> ToolType.SHOVEL
                    else -> ToolType.SICKLE
                }
                val secondaryToolType = when (node.type) {
                    HarvestNodeType.NATURE_HERB -> ToolType.KNIFE
                    HarvestNodeType.BERRY_BUSH -> ToolType.SICKLE
                    HarvestNodeType.MUSHROOM_PATCH -> ToolType.SHOVEL
                    HarvestNodeType.ROOT_PATCH -> ToolType.KNIFE
                    else -> ToolType.KNIFE
                }

                val preferredSlot = inventory.findBestUsableToolSlot(preferredToolType)
                val secondarySlot = inventory.findBestUsableToolSlot(secondaryToolType)
                val activeDef = activeSlotItem?.takeIf { !it.isBroken }?.definition

                val usedSlot = when {
                    activeDef?.category == ItemCategory.TOOL -> inventory.activeHotbarIndex
                    preferredSlot >= 0 -> preferredSlot
                    secondarySlot >= 0 -> secondarySlot
                    else -> -1
                }
                val usedStack = if (usedSlot >= 0) inventory.slots[usedSlot] else null
                val usedDef = usedStack?.definition
                val usedToolType = usedDef?.toolType ?: ToolType.NONE

                val isNatureTool = usedToolType == ToolType.SICKLE ||
                    usedToolType == ToolType.SHOVEL ||
                    usedToolType == ToolType.KNIFE

                val baseDmg = if (isNatureTool) 18f * (usedDef?.toolPower ?: 2.0f) else 15f

                val toolAnim = getAnimationForTool(usedToolType)
                if (toolAnim != null) {
                    player.startToolAction(toolAnim, usedDef?.id)
                }

                if (usedSlot >= 0) {
                    inventory.damageToolInSlot(usedSlot, 1)
                }

                node.triggerHit(baseDmg)
                combatSystem?.recordDamagePopup(
                    pos = Vector3(node.position.x, node.position.y + 0.9f, node.position.z),
                    damage = baseDmg,
                    isHeavy = isNatureTool
                )

                audioManager.playMeleeHit(isHeavy = false)
                combatSystem?.spawnParticleBurst(
                    origin = Vector3(node.position.x, node.position.y + 0.5f, node.position.z),
                    count = if (isNatureTool) 10 else 6,
                    speed = 3.5f,
                    variant = 0
                )

                val bonusYield = if (isNatureTool) 1 else 0
                spawnOrAddDrops(
                    origin = node.position,
                    drops = node.drops,
                    inventory = inventory,
                    onCollected = onCollected,
                    toolTier = 1,
                    bonusYield = bonusYield,
                    chanceBoost = if (isNatureTool) 0.25f else 0f
                )

                // Tool-specific guaranteed nature specialty harvest
                val gpm = groundPickupManager
                when (usedToolType) {
                    ToolType.SICKLE -> {
                        if (gpm != null) {
                            gpm.spawnItemPickup(ResourceCatalog.PLANTS.id, Vector3(node.position.x + 0.25f, node.position.y, node.position.z), 1)
                            gpm.spawnItemPickup(ResourceCatalog.FIBERS.id, Vector3(node.position.x - 0.25f, node.position.y, node.position.z), 1)
                        } else {
                            inventory.addItem(ResourceCatalog.PLANTS.id, 1)
                            onCollected(ResourceCatalog.PLANTS.id, 1)
                            inventory.addItem(ResourceCatalog.FIBERS.id, 1)
                            onCollected(ResourceCatalog.FIBERS.id, 1)
                        }
                    }
                    ToolType.SHOVEL -> {
                        if (gpm != null) {
                            gpm.spawnItemPickup(ResourceCatalog.ROOTS.id, Vector3(node.position.x + 0.25f, node.position.y, node.position.z), 1)
                            gpm.spawnItemPickup(ResourceCatalog.MUSHROOMS.id, Vector3(node.position.x - 0.25f, node.position.y, node.position.z), 1)
                        } else {
                            inventory.addItem(ResourceCatalog.ROOTS.id, 1)
                            onCollected(ResourceCatalog.ROOTS.id, 1)
                            inventory.addItem(ResourceCatalog.MUSHROOMS.id, 1)
                            onCollected(ResourceCatalog.MUSHROOMS.id, 1)
                        }
                    }
                    ToolType.KNIFE -> {
                        if (gpm != null) {
                            gpm.spawnItemPickup(ResourceCatalog.BERRIES.id, Vector3(node.position.x + 0.25f, node.position.y, node.position.z), 1)
                            gpm.spawnItemPickup(ResourceCatalog.MUSHROOMS.id, Vector3(node.position.x - 0.25f, node.position.y, node.position.z), 1)
                        } else {
                            inventory.addItem(ResourceCatalog.BERRIES.id, 1)
                            onCollected(ResourceCatalog.BERRIES.id, 1)
                            inventory.addItem(ResourceCatalog.MUSHROOMS.id, 1)
                            onCollected(ResourceCatalog.MUSHROOMS.id, 1)
                        }
                    }
                    else -> {}
                }

                if (node.isDestroyed) {
                    destroyNode(node)
                    spawnOrAddDrops(
                        origin = node.position,
                        drops = node.destructionBonusDrops,
                        inventory = inventory,
                        onCollected = onCollected,
                        toolTier = 1,
                        bonusYield = bonusYield
                    )
                }
                return true
            }

            HarvestNodeType.MAGIC_CRYSTAL, HarvestNodeType.ANCIENT_MONUMENT -> {
                val pickSlot = inventory.findBestUsableToolSlot(ToolType.PICKAXE)
                val activeDef = activeSlotItem?.takeIf { !it.isBroken }?.definition
                val usedSlot = when {
                    activeDef?.category == ItemCategory.TOOL -> inventory.activeHotbarIndex
                    pickSlot >= 0 -> pickSlot
                    else -> -1
                }
                val usedDef = if (usedSlot >= 0) inventory.slots[usedSlot]?.definition else null
                val toolAnim = getAnimationForTool(usedDef?.toolType ?: ToolType.NONE)
                if (toolAnim != null) {
                    player.startToolAction(toolAnim, usedDef?.id)
                }
                if (usedSlot >= 0) {
                    inventory.damageToolInSlot(usedSlot, 1)
                }

                node.triggerHit(25f)
                audioManager.playMeleeHit(isHeavy = true)
                combatSystem?.spawnParticleBurst(
                    origin = Vector3(node.position.x, node.position.y + 1.2f, node.position.z),
                    count = 12,
                    speed = 5.0f,
                    variant = 0
                )
                spawnOrAddDrops(node.position, node.drops, inventory, onCollected)
                return true
            }
        }
    }

    private fun spawnFallingRockFragmentsForBoulder(node: HarvestableNode, guaranteedIronAndRare: Boolean) {
        val rolledDrops = mutableMapOf<String, Int>()
        val allDrops = node.drops + node.destructionBonusDrops
        for (drop in allDrops) {
            if (node.lastToolTier < drop.minToolTier) continue
            val effectiveChance = (drop.chance + node.lastChanceBoost).coerceAtMost(1.0f)
            if (rng.nextFloat() <= effectiveChance) {
                val baseQty = if (drop.maxCount > drop.minCount) {
                    drop.minCount + rng.nextInt(drop.maxCount - drop.minCount + 1)
                } else {
                    drop.minCount
                }
                val qty = baseQty + node.lastBonusYield
                if (qty > 0) {
                    rolledDrops[drop.itemId] = (rolledDrops[drop.itemId] ?: 0) + qty
                }
            }
        }
        val totalStone = maxOf(5, (rolledDrops.remove(ResourceCatalog.STONE.id) ?: 5) + node.pendingStoneTotal)
        if (guaranteedIronAndRare) {
            rolledDrops[ResourceCatalog.IRON_ORE.id] = maxOf(2, (rolledDrops[ResourceCatalog.IRON_ORE.id] ?: 0) + 1)
            rolledDrops[ResourceCatalog.RARE_ORE.id] = maxOf(1, rolledDrops[ResourceCatalog.RARE_ORE.id] ?: 0)
        }

        val bonusEntries = rolledDrops.entries.map { it.key to it.value }
        val fragmentCount = 5
        val baseStonePerFragment = totalStone / fragmentCount
        val stoneRemainder = totalStone % fragmentCount

        for (i in 0 until fragmentCount) {
            val angleRad = (i.toFloat() / fragmentCount) * (Math.PI.toFloat() * 2f) + 0.25f
            val dirX = cos(angleRad)
            val dirZ = sin(angleRad)
            val startX = node.position.x + dirX * 0.35f
            val startZ = node.position.z + dirZ * 0.35f
            val groundY = worldManager?.getWalkableSurfaceHeight(startX, startZ) ?: node.position.y
            val startY = groundY + 0.95f + (i % 3) * 0.22f

            val fragModel = if (stoneFragmentModels.isNotEmpty()) {
                stoneFragmentModels[i % stoneFragmentModels.size]
            } else {
                null
            }
            val fragInst = fragModel?.let { ModelInstance(it) }
            val stoneShare = baseStonePerFragment + if (i < stoneRemainder) 1 else 0
            val assignedBonuses = bonusEntries.filterIndexed { idx, _ -> idx % fragmentCount == i }

            val fragment = FallingRockFragment(
                nodeId = node.id,
                instance = fragInst,
                position = Vector3(startX, startY, startZ),
                velocity = Vector3(dirX * (1.25f + (i % 2) * 0.35f), 2.2f + (i % 3) * 0.45f, dirZ * (1.25f + (i % 2) * 0.35f)),
                groundY = groundY,
                rotationDeg = i * 52f,
                rotSpeedDeg = 180f + i * 35f,
                dropItemId = ResourceCatalog.STONE.id,
                dropCount = maxOf(1, stoneShare),
                bonusDrops = assignedBonuses
            )
            updateFragmentTransform(fragment)
            fallingRockFragments.add(fragment)
        }
    }

    private fun updateFragmentTransform(fragment: FallingRockFragment) {
        fragment.instance?.transform?.idt()
            ?.translate(fragment.position.x, fragment.position.y, fragment.position.z)
            ?.rotate(1f, 0.5f, 0.2f, fragment.rotationDeg)
    }

    private fun spawnTreeLandedDrops(node: HarvestableNode) {
        if (node.hasSpawnedDrops) return
        node.hasSpawnedDrops = true

        val rolledDrops = mutableMapOf<String, Int>()
        val allDrops = node.drops + node.destructionBonusDrops
        for (drop in allDrops) {
            if (node.lastToolTier < drop.minToolTier) continue
            if (rng.nextFloat() <= drop.chance) {
                val baseQty = if (drop.maxCount > drop.minCount) {
                    drop.minCount + rng.nextInt(drop.maxCount - drop.minCount + 1)
                } else {
                    drop.minCount
                }
                val qty = baseQty + node.lastBonusYield
                if (qty > 0) {
                    rolledDrops[drop.itemId] = (rolledDrops[drop.itemId] ?: 0) + qty
                }
            }
        }

        val totalWood = maxOf(5, (rolledDrops.remove(ResourceCatalog.WOOD.id) ?: 5) + node.pendingWoodTotal)
        val gpm = groundPickupManager
        if (gpm != null) {
            gpm.spawnWoodLogs(
                treeBase = node.position,
                fallDirX = node.fallDirX,
                fallDirZ = node.fallDirZ,
                logCount = 3,
                totalWood = totalWood
            )
            var idx = 0
            for ((itemId, count) in rolledDrops) {
                val dist = 1.4f + idx * 0.9f
                val side = if (idx % 2 == 0) 0.55f else -0.55f
                val px = node.position.x + node.fallDirX * dist - node.fallDirZ * side
                val pz = node.position.z + node.fallDirZ * dist + node.fallDirX * side
                gpm.spawnItemPickup(itemId, Vector3(px, node.position.y, pz), count, yawOffset = idx * 55f)
                idx++
            }
        }
    }

    private fun spawnOrAddDrops(
        origin: Vector3,
        drops: List<HarvestDrop>,
        inventory: Inventory,
        onCollected: (String, Int) -> Unit,
        toolTier: Int = 1,
        bonusYield: Int = 0,
        chanceBoost: Float = 0f
    ) {
        val gpm = groundPickupManager
        var offsetIdx = 0
        for (drop in drops) {
            if (toolTier < drop.minToolTier) continue
            val effectiveChance = (drop.chance + chanceBoost).coerceAtMost(1.0f)
            if (rng.nextFloat() <= effectiveChance) {
                val baseQty = if (drop.maxCount > drop.minCount) {
                    drop.minCount + rng.nextInt(drop.maxCount - drop.minCount + 1)
                } else {
                    drop.minCount
                }
                val qty = baseQty + bonusYield
                if (qty > 0) {
                    if (gpm != null) {
                        val angle = offsetIdx * 1.35f
                        val r = 0.45f + (offsetIdx % 3) * 0.22f
                        val px = origin.x + cos(angle) * r
                        val pz = origin.z + sin(angle) * r
                        gpm.spawnItemPickup(drop.itemId, Vector3(px, origin.y, pz), qty, yawOffset = offsetIdx * 40f)
                        offsetIdx++
                    } else {
                        inventory.addItem(drop.itemId, qty)
                        onCollected(drop.itemId, qty)
                    }
                }
            }
        }
    }

    /**
     * Spawns Monster Essence and Boss Materials upon enemy defeat.
     */
    fun handleMonsterDefeat(
        isBoss: Boolean,
        position: Vector3,
        inventory: Inventory,
        onCollected: (String, Int) -> Unit
    ) {
        val essenceQty = if (isBoss) 4 + rng.nextInt(4) else 1 + rng.nextInt(2)
        val gpm = groundPickupManager
        if (gpm != null) {
            gpm.spawnItemPickup(
                itemId = ResourceCatalog.MONSTER_ESSENCE.id,
                position = Vector3(position.x + 0.45f, position.y, position.z - 0.35f),
                count = essenceQty
            )
            if (isBoss) {
                gpm.spawnItemPickup(
                    itemId = ResourceCatalog.BOSS_MATERIALS.id,
                    position = Vector3(position.x - 0.45f, position.y, position.z + 0.35f),
                    count = 1
                )
            }
        } else {
            inventory.addItem(ResourceCatalog.MONSTER_ESSENCE.id, essenceQty)
            onCollected(ResourceCatalog.MONSTER_ESSENCE.id, essenceQty)
            if (isBoss) {
                inventory.addItem(ResourceCatalog.BOSS_MATERIALS.id, 1)
                onCollected(ResourceCatalog.BOSS_MATERIALS.id, 1)
            }
        }
    }

    fun destroyNode(node: HarvestableNode) {
        node.isDestroyed = true
        node.health = 0f
        node.crackInstance = null
        node.instance?.transform?.setToTranslation(0f, -9999f, 0f)
        node.collider?.let { worldManager?.removeObstacleCollider(it) }
    }

    /**
     * Destroys destructible environmental world objects (trees, harvestable vegetation, boulders, crystals)
     * inside the DIVINE ANNIHILATION beam corridor or terminal impact area during Phase 5.
     * Strictly preserves indestructible terrain, ANCIENT_MONUMENT, CRAFTING_TABLE, STORAGE_CHEST,
     * and never modifies player-built structures in BuildingManager.
     */
    fun destroyDestructibleNodesInUltimatePath(
        beamOrigin: Vector3,
        aimDirection: Vector3,
        beamLength: Float,
        beamHalfWidth: Float,
        impactCenter: Vector3,
        impactRadius: Float,
        inventory: Inventory? = null,
        combatSystem: CombatSystem? = null,
        onCollected: (String, Int) -> Unit = { _, _ -> }
    ): Int {
        val dirX = aimDirection.x
        val dirZ = aimDirection.z
        val dirLenXZ = sqrt(dirX * dirX + dirZ * dirZ).coerceAtLeast(0.0001f)
        val normDirX = dirX / dirLenXZ
        val normDirZ = dirZ / dirLenXZ
        val impactRadiusSq = impactRadius * impactRadius
        val corridorRadiusSq = beamHalfWidth * beamHalfWidth

        var destroyedCount = 0
        for (node in nodes) {
            if (node.isDestroyed) continue
            if (node.type == HarvestNodeType.ANCIENT_MONUMENT ||
                node.type == HarvestNodeType.CRAFTING_TABLE ||
                node.type == HarvestNodeType.STORAGE_CHEST
            ) {
                continue
            }

            val nx = node.position.x
            val nz = node.position.z

            val impDx = nx - impactCenter.x
            val impDz = nz - impactCenter.z
            val inImpact = (impDx * impDx + impDz * impDz) <= impactRadiusSq

            val relX = nx - beamOrigin.x
            val relZ = nz - beamOrigin.z
            val proj = relX * normDirX + relZ * normDirZ
            val perpX = relX - normDirX * proj
            val perpZ = relZ - normDirZ * proj
            val inCorridor = proj in -1.0f..(beamLength + 1.5f) &&
                (perpX * perpX + perpZ * perpZ) <= corridorRadiusSq

            if (inImpact || inCorridor) {
                destroyNode(node)
                destroyedCount++

                if (inventory != null) {
                    for (drop in node.drops) {
                        val qty = drop.minCount.coerceAtLeast(1)
                        val added = inventory.addItem(drop.itemId, qty)
                        if (added > 0) {
                            onCollected(drop.itemId, added)
                        }
                    }
                    for (bonus in node.destructionBonusDrops) {
                        val qty = bonus.minCount.coerceAtLeast(1)
                        val added = inventory.addItem(bonus.itemId, qty)
                        if (added > 0) {
                            onCollected(bonus.itemId, added)
                        }
                    }
                }

                combatSystem?.spawnMeleeHitBurst(
                    hitPoint = Vector3(nx, node.position.y + 0.85f, nz),
                    isHeavy = true,
                    isSword = false
                )
            }
        }
        return destroyedCount
    }

    fun linkWorldData(worldData: com.aethelgard.survival.core.world.GeneratedWorldData) {
        val treeNodes = nodes.filter { it.type == HarvestNodeType.TREE }
        for (i in treeNodes.indices) {
            if (i < worldData.treeInstances.size) {
                val node = treeNodes[i]
                node.instance = worldData.treeInstances[i]
                node.collider = worldData.treeColliders.getOrNull(i)
                if (node.isDestroyed && !node.isFalling && !node.hasSettledOnGround) {
                    node.instance?.transform?.setToTranslation(0f, -9999f, 0f)
                    node.collider?.let { worldManager?.removeObstacleCollider(it) }
                }
            }
        }
        val boulderNodes = nodes.filter { it.type == HarvestNodeType.BOULDER || it.type == HarvestNodeType.CRYSTAL_BOULDER }
        for (i in boulderNodes.indices) {
            if (i < worldData.boulderInstances.size) {
                val node = boulderNodes[i]
                node.instance = worldData.boulderInstances[i]
                node.collider = worldData.boulderColliders.getOrNull(i)
                if (node.isDestroyed) {
                    node.instance?.transform?.setToTranslation(0f, -9999f, 0f)
                    node.collider?.let { worldManager?.removeObstacleCollider(it) }
                }
            }
        }
    }

    fun update(delta: Float) {
        for (node in nodes) {
            // 1. Update physically falling trees
            if (node.type == HarvestNodeType.TREE && (node.isFalling || node.hasSettledOnGround)) {
                if (node.isFalling && !node.hasSettledOnGround) {
                    node.fallAngularVelocity += 135f * delta
                    node.fallAngleDeg += node.fallAngularVelocity * delta
                    if (node.fallAngleDeg >= node.maxFallAngleDeg) {
                        node.fallAngleDeg = node.maxFallAngleDeg
                        node.isFalling = false
                        node.hasSettledOnGround = true
                        spawnTreeLandedDrops(node)
                    }
                }
                val inst = node.instance
                if (inst != null) {
                    val axisX = -node.fallDirZ
                    val axisZ = node.fallDirX
                    inst.transform.idt()
                        .translate(node.position.x, node.position.y + node.yOffset + 0.18f, node.position.z)
                        .rotate(axisX, 0f, axisZ, node.fallAngleDeg)
                        .rotate(0f, 1f, 0f, node.initialYawDeg)
                        .scale(node.baseScale, node.baseScale, node.baseScale)
                }
                continue
            }

            if (node.isDestroyed) continue

            // 2. Update hit wobble on active nodes (and sync rock crack overlays)
            if (node.shakeTimer > 0f) {
                node.shakeTimer = (node.shakeTimer - delta).coerceAtLeast(0f)
                val wobble = sin(node.shakeTimer * 38f) * (node.shakeTimer / 0.28f) * 3.8f * node.shakeIntensity
                node.instance?.let { inst ->
                    inst.transform.idt()
                        .translate(node.position.x, node.position.y + node.yOffset, node.position.z)
                        .rotate(0f, 1f, 0f, node.initialYawDeg)
                        .rotate(1f, 0f, 0f, wobble)
                        .scale(node.baseScale, node.baseScale, node.baseScale)
                }
                node.crackInstance?.let { crack ->
                    crack.transform.idt()
                        .translate(node.position.x, node.position.y + node.yOffset, node.position.z)
                        .rotate(0f, 1f, 0f, node.initialYawDeg)
                        .rotate(1f, 0f, 0f, wobble)
                        .scale(node.baseScale, node.baseScale, node.baseScale)
                }
            } else if (node.shakeIntensity > 0f) {
                node.shakeIntensity = 0f
                node.instance?.let { inst ->
                    inst.transform.idt()
                        .translate(node.position.x, node.position.y + node.yOffset, node.position.z)
                        .rotate(0f, 1f, 0f, node.initialYawDeg)
                        .scale(node.baseScale, node.baseScale, node.baseScale)
                }
                node.crackInstance?.let { crack ->
                    crack.transform.idt()
                        .translate(node.position.x, node.position.y + node.yOffset, node.position.z)
                        .rotate(0f, 1f, 0f, node.initialYawDeg)
                        .scale(node.baseScale, node.baseScale, node.baseScale)
                }
            }
        }

        // 3. Update falling 3D stone fragments from shattered rocks
        val fragIter = fallingRockFragments.iterator()
        while (fragIter.hasNext()) {
            val frag = fragIter.next()
            if (!frag.hasLanded) {
                frag.velocity.y -= 20f * delta
                frag.position.add(
                    frag.velocity.x * delta,
                    frag.velocity.y * delta,
                    frag.velocity.z * delta
                )
                frag.rotationDeg = (frag.rotationDeg + frag.rotSpeedDeg * delta) % 360f

                if (frag.position.y <= frag.groundY + 0.14f) {
                    frag.position.y = frag.groundY + 0.14f
                    frag.velocity.setZero()
                    frag.hasLanded = true
                    val gpm = groundPickupManager
                    if (gpm != null) {
                        gpm.spawnItemPickup(
                            itemId = frag.dropItemId,
                            position = Vector3(frag.position.x, frag.groundY, frag.position.z),
                            count = frag.dropCount,
                            yawOffset = frag.rotationDeg
                        )
                        for ((bonusId, bonusQty) in frag.bonusDrops) {
                            gpm.spawnItemPickup(
                                itemId = bonusId,
                                position = Vector3(frag.position.x + 0.22f, frag.groundY, frag.position.z - 0.18f),
                                count = bonusQty,
                                yawOffset = frag.rotationDeg + 35f
                            )
                        }
                    }
                }
                updateFragmentTransform(frag)
            } else {
                frag.landedTimer += delta
                // Keep settled rock fragment visible briefly as it transitions into the collectible pickup
                if (frag.landedTimer >= 1.25f) {
                    fragIter.remove()
                }
            }
        }
    }

    fun renderStructures(modelBatch: ModelBatch, environment: com.badlogic.gdx.graphics.g3d.Environment) {
        craftingTableInstance?.let { modelBatch.render(it, environment) }
        storageChestInstance?.let { modelBatch.render(it, environment) }
        for (natureNode in natureInstances) {
            if (!natureNode.isDestroyed) {
                natureNode.instance?.let { modelBatch.render(it, environment) }
            }
        }
        // Render progressive 3D rock crack overlays on damaged boulders
        for (node in nodes) {
            if (!node.isDestroyed && node.damageStage > 0) {
                node.crackInstance?.let { modelBatch.render(it, environment) }
            }
        }
        // Render falling 3D stone fragments from shattered rocks
        for (frag in fallingRockFragments) {
            frag.instance?.let { modelBatch.render(it, environment) }
        }
    }

    fun serializeDestroyedNodes(): String {
        return nodes.filter { it.isDestroyed }.joinToString(",") { it.id }
    }

    fun deserializeDestroyedNodes(data: String) {
        if (data.isBlank()) return
        val destroyedSet = data.split(",").toSet()
        for (node in nodes) {
            if (destroyedSet.contains(node.id)) {
                node.isFalling = false
                node.hasSettledOnGround = false
                destroyNode(node)
            }
        }
    }
}
