package com.aethelgard.survival.core.survival

import com.aethelgard.survival.core.building.BuildingManager
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Robust local persistence manager for Aethelgard Survival Mode.
 * Saves player inventory, hotbar selection, storage chest contents,
 * survival attributes (hunger, thirst, stamina), destroyed world tree/rock states,
 * and all placed Freeform Building pieces using LibGDX Preferences (backed by Android SharedPreferences on Android).
 */
class SurvivalSaveManager(
    private val prefName: String = "aethelgard_survival_save"
) {
    private var prefs: Preferences? = null

    var woodChoppedCount: Int = 0
    var stoneMinedCount: Int = 0
    var itemsCraftedCount: Int = 0

    private var autoSaveTimer: Float = 0f

    private fun getPrefs(): Preferences? {
        if (prefs == null) {
            try {
                prefs = Gdx.app?.getPreferences(prefName)
            } catch (_: Throwable) {
            }
        }
        return prefs
    }

    fun save(
        inventory: Inventory,
        chestManager: ChestManager,
        attributes: SurvivalAttributes,
        resourceManager: HarvestableResourceManager,
        buildingManager: BuildingManager? = null,
        dayNightCycle: com.aethelgard.survival.core.world.DayNightCycle? = null
    ) {
        val p = getPrefs() ?: return
        try {
            p.putString("player_inventory", inventory.serialize())
            p.putInteger("hotbar_index", inventory.activeHotbarIndex)
            p.putString("chest_inventory", chestManager.serialize())
            p.putString("survival_attributes", attributes.serialize())
            p.putString("destroyed_nodes", resourceManager.serializeDestroyedNodes())
            if (buildingManager != null) {
                p.putString("placed_buildings", buildingManager.serialize())
            }
            if (dayNightCycle != null) {
                p.putString("day_night_cycle", dayNightCycle.serialize())
            }
            p.putInteger("stat_wood", woodChoppedCount)
            p.putInteger("stat_stone", stoneMinedCount)
            p.putInteger("stat_crafted", itemsCraftedCount)
            p.flush()
        } catch (t: Throwable) {
            Gdx.app?.error("SurvivalSave", "Failed to save survival data", t)
        }
    }

    fun load(
        inventory: Inventory,
        chestManager: ChestManager,
        attributes: SurvivalAttributes,
        resourceManager: HarvestableResourceManager,
        buildingManager: BuildingManager? = null,
        dayNightCycle: com.aethelgard.survival.core.world.DayNightCycle? = null
    ) {
        val p = getPrefs() ?: return
        try {
            if (p.contains("player_inventory")) {
                val invData = p.getString("player_inventory", "")
                inventory.deserialize(invData)
            } else {
                // Initial Starter Kit: Starter Stone Axe, 10 Wood, 6 Berries
                inventory.addItem(ResourceCatalog.STONE_AXE.id, 1)
                inventory.addItem(ResourceCatalog.WOOD.id, 10)
                inventory.addItem(ResourceCatalog.BERRIES.id, 6)
            }

            inventory.activeHotbarIndex = p.getInteger("hotbar_index", 0).coerceIn(0, 4)

            if (p.contains("chest_inventory")) {
                val chestData = p.getString("chest_inventory", "")
                chestManager.deserialize(chestData)
            } else {
                // Initial chest seed: 8 Stone, 4 Plants, 2 Mushrooms
                chestManager.chestInventory.addItem(ResourceCatalog.STONE.id, 8)
                chestManager.chestInventory.addItem(ResourceCatalog.PLANTS.id, 4)
                chestManager.chestInventory.addItem(ResourceCatalog.MUSHROOMS.id, 2)
            }

            if (p.contains("survival_attributes")) {
                attributes.deserialize(p.getString("survival_attributes", ""))
            }

            if (p.contains("destroyed_nodes")) {
                resourceManager.deserializeDestroyedNodes(p.getString("destroyed_nodes", ""))
            }

            if (buildingManager != null) {
                if (p.contains("placed_buildings")) {
                    buildingManager.deserialize(p.getString("placed_buildings", ""))
                } else {
                    buildingManager.clearAllPieces()
                }
            }

            if (dayNightCycle != null && p.contains("day_night_cycle")) {
                dayNightCycle.deserialize(p.getString("day_night_cycle", ""))
            }

            woodChoppedCount = p.getInteger("stat_wood", 0)
            stoneMinedCount = p.getInteger("stat_stone", 0)
            itemsCraftedCount = p.getInteger("stat_crafted", 0)
        } catch (t: Throwable) {
            Gdx.app?.error("SurvivalSave", "Failed to load survival data", t)
        }
    }

    fun saveAll(
        inventory: Inventory,
        chestManager: ChestManager,
        attributes: SurvivalAttributes,
        resourceManager: HarvestableResourceManager,
        buildingManager: BuildingManager? = null,
        dayNightCycle: com.aethelgard.survival.core.world.DayNightCycle? = null
    ) = save(inventory, chestManager, attributes, resourceManager, buildingManager, dayNightCycle)

    fun loadAll(
        inventory: Inventory,
        chestManager: ChestManager,
        attributes: SurvivalAttributes,
        resourceManager: HarvestableResourceManager,
        buildingManager: BuildingManager? = null,
        dayNightCycle: com.aethelgard.survival.core.world.DayNightCycle? = null
    ) = load(inventory, chestManager, attributes, resourceManager, buildingManager, dayNightCycle)

    fun updateAutoSave(
        delta: Float,
        inventory: Inventory,
        chestManager: ChestManager,
        attributes: SurvivalAttributes,
        resourceManager: HarvestableResourceManager,
        buildingManager: BuildingManager? = null,
        dayNightCycle: com.aethelgard.survival.core.world.DayNightCycle? = null
    ) {
        autoSaveTimer += delta
        if (autoSaveTimer >= 12.0f) {
            autoSaveTimer = 0f
            save(inventory, chestManager, attributes, resourceManager, buildingManager, dayNightCycle)
        }
    }

    fun clearAll(
        inventory: Inventory,
        chestManager: ChestManager,
        attributes: SurvivalAttributes,
        resourceManager: HarvestableResourceManager,
        buildingManager: BuildingManager? = null
    ) {
        val p = getPrefs() ?: return
        try {
            p.clear()
            p.flush()
            inventory.clear()
            chestManager.chestInventory.clear()
            attributes.resetToFull()
            buildingManager?.clearAllPieces()
            load(inventory, chestManager, attributes, resourceManager, buildingManager)
        } catch (_: Throwable) {
        }
    }
}
