package com.aethelgard.survival.core.entities

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.combat.CombatSystem
import com.aethelgard.survival.core.player.PlayerCharacter
import com.aethelgard.survival.core.survival.Inventory
import com.aethelgard.survival.core.survival.ResourceCatalog
import com.aethelgard.survival.core.world.ProceduralModelFactory
import com.aethelgard.survival.core.world.WorldManager
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class GroundPickup(
    val id: String,
    val itemId: String,
    val basePosition: Vector3,
    val modelInstance: ModelInstance,
    var count: Int = 1,
    val yawOffset: Float = 0f,
    var age: Float = 0f,
    var isCollected: Boolean = false,
    var particleTimer: Float = 0f
)

/**
 * Unified Physical Ground Pickup System for Aethelgard.
 * Manages all 3D world item drops:
 * - Wood logs from fallen trees
 * - Stone fragments & mineral ores from shattered rocks
 * - Green Crystal from Green Slime
 * - MP Potion from Blue Flower Monster
 * - Health Potion from Red Flower Monster
 * - Herbs, mushrooms, berries, roots, crystals, and monster drops
 *
 * All pickups use recognizable 3D world models, rest on the terrain at the drop site,
 * float/rotate subtly without flying away, remain indefinitely until collected,
 * and add to inventory (with HUD notification) only upon player proximity pickup.
 */
class GroundPickupManager(
    private val modelFactory: ProceduralModelFactory,
    private val worldManager: WorldManager
) {
    private val pickups = mutableListOf<GroundPickup>()
    private val modelCache = mutableMapOf<String, Model>()
    private var nextPickupId = 1

    val activePickups: List<GroundPickup>
        get() = pickups

    private fun getOrLoadPickupModel(itemId: String): Model {
        return modelCache.getOrPut(itemId) {
            when (itemId) {
                ResourceCatalog.GREEN_CRYSTAL.id -> modelFactory.createGreenCrystalModel()
                ResourceCatalog.MP_POTION.id -> modelFactory.createMpPotionModel()
                ResourceCatalog.HEALTH_POTION.id -> modelFactory.createHealthPotionModel()
                ResourceCatalog.WOOD.id -> modelFactory.createWoodLogPickupModel()
                ResourceCatalog.STONE.id -> modelFactory.createStoneFragmentModel(0)
                else -> modelFactory.createHeldItemModel(itemId)
            }
        }
    }

    /**
     * Spawns a physical 3D ground pickup for any item at [position], snapped onto the walkable terrain surface.
     */
    fun spawnItemPickup(
        itemId: String,
        position: Vector3,
        count: Int = 1,
        yawOffset: Float = 0f
    ): GroundPickup {
        val safeCount = max(1, count)
        val model = getOrLoadPickupModel(itemId)
        val instance = ModelInstance(model)
        val groundY = worldManager.getWalkableSurfaceHeight(position.x, position.z)
        val spawnPos = Vector3(position.x, groundY, position.z)

        val pickup = GroundPickup(
            id = "${itemId}_${nextPickupId++}",
            itemId = itemId,
            basePosition = spawnPos,
            modelInstance = instance,
            count = safeCount,
            yawOffset = yawOffset
        )
        pickups.add(pickup)
        updateTransform(pickup)
        return pickup
    }

    /**
     * Spawns several individual 3D wood log pickups along a fallen tree trunk after the tree lands.
     */
    fun spawnWoodLogs(
        treeBase: Vector3,
        fallDirX: Float,
        fallDirZ: Float,
        logCount: Int = 3,
        totalWood: Int = logCount
    ): List<GroundPickup> {
        val actualLogs = logCount.coerceIn(2, 5)
        val safeTotal = max(actualLogs, totalWood)
        val basePerLog = safeTotal / actualLogs
        val remainder = safeTotal % actualLogs
        val spawned = mutableListOf<GroundPickup>()

        for (i in 0 until actualLogs) {
            val distAlongTrunk = 1.1f + i * 1.15f
            val sideJitter = (if (i % 2 == 0) 1f else -1f) * 0.28f
            val lx = treeBase.x + fallDirX * distAlongTrunk - fallDirZ * sideJitter
            val lz = treeBase.z + fallDirZ * distAlongTrunk + fallDirX * sideJitter
            val qty = basePerLog + if (i < remainder) 1 else 0
            val pickup = spawnItemPickup(
                itemId = ResourceCatalog.WOOD.id,
                position = Vector3(lx, treeBase.y, lz),
                count = qty,
                yawOffset = i * 47f
            )
            spawned.add(pickup)
        }
        return spawned
    }

    /**
     * Spawns several individual 3D stone pickups around a shattered rock site.
     */
    fun spawnStonePickups(
        center: Vector3,
        pickupCount: Int = 3,
        totalStone: Int = pickupCount
    ): List<GroundPickup> {
        val actualPickups = pickupCount.coerceIn(2, 5)
        val safeTotal = max(actualPickups, totalStone)
        val basePerPickup = safeTotal / actualPickups
        val remainder = safeTotal % actualPickups
        val spawned = mutableListOf<GroundPickup>()

        for (i in 0 until actualPickups) {
            val angleRad = (i.toFloat() / actualPickups) * (Math.PI.toFloat() * 2f) + 0.35f
            val radius = 0.68f + (i % 2) * 0.28f
            val sx = center.x + cos(angleRad) * radius
            val sz = center.z + sin(angleRad) * radius
            val qty = basePerPickup + if (i < remainder) 1 else 0
            val pickup = spawnItemPickup(
                itemId = ResourceCatalog.STONE.id,
                position = Vector3(sx, center.y, sz),
                count = qty,
                yawOffset = i * 62f
            )
            spawned.add(pickup)
        }
        return spawned
    }

    /**
     * Spawns exactly one guaranteed 3D emerald-green crystal pickup at the specified position.
     */
    fun spawnGreenCrystal(position: Vector3): GroundPickup {
        return spawnItemPickup(ResourceCatalog.GREEN_CRYSTAL.id, position, count = 1)
    }

    /**
     * Spawns exactly one guaranteed 3D MP Potion pickup at the specified position when a Blue Flower Monster is defeated.
     */
    fun spawnMpPotion(position: Vector3): GroundPickup {
        return spawnItemPickup(ResourceCatalog.MP_POTION.id, position, count = 1)
    }

    /**
     * Spawns exactly one guaranteed 3D Health Potion pickup at the specified position when a Red Flower Monster is defeated.
     */
    fun spawnHealthPotion(position: Vector3): GroundPickup {
        return spawnItemPickup(ResourceCatalog.HEALTH_POTION.id, position, count = 1)
    }

    fun update(
        delta: Float,
        player: PlayerCharacter,
        inventory: Inventory,
        audioManager: AudioManager,
        combatSystem: CombatSystem? = null,
        onNotify: (String, Int) -> Unit
    ) {
        val iter = pickups.iterator()
        while (iter.hasNext()) {
            val pickup = iter.next()
            pickup.age += delta
            updateTransform(pickup)

            // Emit subtle floating sparkles around pickups
            pickup.particleTimer += delta
            if (pickup.particleTimer >= 0.26f) {
                pickup.particleTimer = 0f
                combatSystem?.spawnParticleBurst(
                    origin = Vector3(
                        pickup.basePosition.x + (Math.random().toFloat() - 0.5f) * 0.35f,
                        pickup.basePosition.y + 0.42f + Math.random().toFloat() * 0.32f,
                        pickup.basePosition.z + (Math.random().toFloat() - 0.5f) * 0.35f
                    ),
                    count = 1,
                    speed = 1.4f,
                    variant = if (pickup.itemId == ResourceCatalog.HEALTH_POTION.id) 2 else 0
                )
            }

            // Proximity check for automatic player collection (2.4m pickup radius)
            val dx = player.position.x - pickup.basePosition.x
            val dz = player.position.z - pickup.basePosition.z
            val distSq = dx * dx + dz * dz

            if (distSq <= 2.4f * 2.4f && !pickup.isCollected) {
                val remaining = inventory.addItem(pickup.itemId, pickup.count)
                val collectedAmount = pickup.count - remaining
                if (collectedAmount > 0) {
                    onNotify(pickup.itemId, collectedAmount)
                    audioManager.playUiClick()

                    combatSystem?.spawnParticleBurst(
                        origin = Vector3(pickup.basePosition.x, pickup.basePosition.y + 0.55f, pickup.basePosition.z),
                        count = 10,
                        speed = 3.8f,
                        variant = 0
                    )
                }
                if (remaining <= 0) {
                    pickup.isCollected = true
                    iter.remove()
                } else {
                    pickup.count = remaining
                }
            }
        }
    }

    private fun updateTransform(pickup: GroundPickup) {
        val bob = sin(pickup.age * 3.2f + pickup.yawOffset * 0.05f) * 0.09f
        val hoverOffset = when (pickup.itemId) {
            ResourceCatalog.WOOD.id -> 0.28f
            ResourceCatalog.STONE.id -> 0.26f
            else -> 0.42f
        }
        val currentY = pickup.basePosition.y + hoverOffset + bob
        val yaw = (pickup.yawOffset + pickup.age * 58f) % 360f
        val scale = when (pickup.itemId) {
            ResourceCatalog.GREEN_CRYSTAL.id,
            ResourceCatalog.MP_POTION.id,
            ResourceCatalog.HEALTH_POTION.id,
            ResourceCatalog.WOOD.id,
            ResourceCatalog.STONE.id -> 1.0f
            else -> 1.35f
        }

        pickup.modelInstance.transform.idt()
            .translate(pickup.basePosition.x, currentY, pickup.basePosition.z)
            .rotate(0f, 1f, 0f, yaw)
            .scale(scale, scale, scale)
    }

    fun render(modelBatch: ModelBatch, environment: Environment) {
        for (pickup in pickups) {
            if (!pickup.isCollected) {
                modelBatch.render(pickup.modelInstance, environment)
            }
        }
    }

    fun clear() {
        pickups.clear()
    }
}
