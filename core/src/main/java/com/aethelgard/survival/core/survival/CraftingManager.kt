package com.aethelgard.survival.core.survival

data class CraftingRecipe(
    val id: String,
    val resultItemId: String,
    val resultCount: Int,
    val ingredients: Map<String, Int>,
    val requiresCraftingTable: Boolean,
    val description: String
) {
    val resultDefinition: ItemDefinition?
        get() = ResourceCatalog.get(resultItemId)
}

/**
 * Functional Crafting System for Aethelgard Survival Mode.
 * Checks resource availability, consumes ingredients upon crafting,
 * adds items to player inventory, and supports both hand-crafting and Crafting Table recipes.
 */
class CraftingManager {

    val recipes = mutableListOf<CraftingRecipe>()

    init {
        registerDefaultRecipes()
    }

    private fun registerDefaultRecipes() {
        // Hand-Crafted Basic Survival Recipes (available anywhere)
        recipes.add(
            CraftingRecipe(
                id = "craft_table",
                resultItemId = ResourceCatalog.CRAFTING_TABLE_ITEM.id,
                resultCount = 1,
                ingredients = mapOf("wood" to 4),
                requiresCraftingTable = false,
                description = "Craft a sturdy wooden Crafting Table to unlock advanced recipes."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "campfire",
                resultItemId = ResourceCatalog.CAMPFIRE.id,
                resultCount = 1,
                ingredients = mapOf("wood" to 3, "stone" to 2),
                requiresCraftingTable = false,
                description = "Provides warm light and a basic cooking fire."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "stone_axe_hand",
                resultItemId = ResourceCatalog.STONE_AXE.id,
                resultCount = 1,
                ingredients = mapOf("wood" to 4, "stone" to 3, "fibers" to 2),
                requiresCraftingTable = false,
                description = "Sharp flint stone axe bound with plant fibers. Chops trees 2x faster."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "stone_pickaxe_hand",
                resultItemId = ResourceCatalog.STONE_PICKAXE.id,
                resultCount = 1,
                ingredients = mapOf("wood" to 3, "stone" to 4),
                requiresCraftingTable = false,
                description = "Pointed stone pick for fracturing boulders and mining ores."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "shovel_hand",
                resultItemId = ResourceCatalog.SHOVEL.id,
                resultCount = 1,
                ingredients = mapOf("wood" to 2, "stone" to 2, "fibers" to 1),
                requiresCraftingTable = false,
                description = "Broad spade for digging roots, mushrooms, and rich soil patches."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "sickle_hand",
                resultItemId = ResourceCatalog.SICKLE.id,
                resultCount = 1,
                ingredients = mapOf("wood" to 2, "stone" to 2, "fibers" to 2),
                requiresCraftingTable = false,
                description = "Curved harvesting blade for reaping wild plants, herbs, and fibers."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "knife_hand",
                resultItemId = ResourceCatalog.KNIFE.id,
                resultCount = 1,
                ingredients = mapOf("branches" to 2, "stone" to 2, "fibers" to 1),
                requiresCraftingTable = false,
                description = "Keen survival knife for slicing berries, mushrooms, fibers, and hides."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "builders_hammer_hand",
                resultItemId = ResourceCatalog.BUILDERS_HAMMER.id,
                resultCount = 1,
                ingredients = mapOf("wood" to 3, "stone" to 2),
                requiresCraftingTable = false,
                description = "Specialized Builder's Hammer for inspecting, repairing, upgrading, and dismantling structures."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "herbal_salve",
                resultItemId = ResourceCatalog.HERBAL_SALVE.id,
                resultCount = 1,
                ingredients = mapOf("plants" to 2, "fibers" to 2),
                requiresCraftingTable = false,
                description = "Healing salve that restores 40 Health."
            )
        )

        // Crafting Table Specialized Recipes (requires proximity to Crafting Table)
        recipes.add(
            CraftingRecipe(
                id = "storage_chest",
                resultItemId = ResourceCatalog.STORAGE_CHEST.id,
                resultCount = 1,
                ingredients = mapOf("wood" to 8, "stone" to 4),
                requiresCraftingTable = true,
                description = "Sturdy wooden storage chest with 20 persistent slots."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "iron_axe",
                resultItemId = ResourceCatalog.IRON_AXE.id,
                resultCount = 1,
                ingredients = mapOf("hardwood" to 4, "iron_ore" to 3, "fibers" to 2),
                requiresCraftingTable = true,
                description = "Forged iron axe blade with superior cutting power. Chops trees 3.5x faster."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "iron_pickaxe",
                resultItemId = ResourceCatalog.IRON_PICKAXE.id,
                resultCount = 1,
                ingredients = mapOf("hardwood" to 3, "iron_ore" to 4),
                requiresCraftingTable = true,
                description = "Tempered iron pick capable of mining rare crystals and deep ores."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "roasted_mushrooms",
                resultItemId = ResourceCatalog.ROASTED_MUSHROOMS.id,
                resultCount = 1,
                ingredients = mapOf("mushrooms" to 2),
                requiresCraftingTable = true,
                description = "Cooked woodland mushrooms. Restores 28 Hunger and 10 Health."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "berry_brew",
                resultItemId = ResourceCatalog.BERRY_BREW.id,
                resultCount = 1,
                ingredients = mapOf("berries" to 3, "plants" to 1),
                requiresCraftingTable = true,
                description = "Wholesome berry brew. Quenches 35 Thirst and restores 30 Stamina."
            )
        )
        recipes.add(
            CraftingRecipe(
                id = "essence_tonic",
                resultItemId = ResourceCatalog.ESSENCE_TONIC.id,
                resultCount = 1,
                ingredients = mapOf("monster_essence" to 2, "magic_crystals" to 1),
                requiresCraftingTable = true,
                description = "Distilled alchemical tonic restoring 50 Energy and 25 Health."
            )
        )
    }

    /**
     * Returns all recipes available based on whether the player is near a Crafting Table.
     */
    fun getAvailableRecipes(isNearCraftingTable: Boolean): List<CraftingRecipe> {
        return if (isNearCraftingTable) {
            recipes
        } else {
            recipes.filter { !it.requiresCraftingTable }
        }
    }

    /**
     * Checks if the player can craft this recipe.
     */
    fun canCraft(recipe: CraftingRecipe, inventory: Inventory, isNearCraftingTable: Boolean): Boolean {
        if (recipe.requiresCraftingTable && !isNearCraftingTable) return false
        return inventory.hasAllRequirements(recipe.ingredients)
    }

    /**
     * Executes the crafting operation: validates, consumes ingredients, and adds the result item.
     * Also replaces a broken tool of the same type in-place if inventory is full or repairs it.
     * Returns the crafted ItemStack if successful, or null if failed.
     */
    fun craft(recipe: CraftingRecipe, inventory: Inventory, isNearCraftingTable: Boolean): ItemStack? {
        if (!canCraft(recipe, inventory, isNearCraftingTable)) return null

        // If crafting a tool and the player already has a broken copy of the exact same tool, replace/restore it first
        val resultDef = ResourceCatalog.get(recipe.resultItemId)
        if (resultDef != null && resultDef.category == ItemCategory.TOOL) {
            for (i in 0 until inventory.size) {
                val existing = inventory.slots[i]
                if (existing != null && existing.itemId == recipe.resultItemId && existing.isBroken) {
                    inventory.consumeAllRequirements(recipe.ingredients)
                    existing.durability = resultDef.maxDurability
                    return existing
                }
            }
        }

        // Check if inventory has room for the result item
        val spaceCheck = inventory.addItem(recipe.resultItemId, recipe.resultCount)
        if (spaceCheck > 0) {
            // Revert partial add if no room
            inventory.removeItem(recipe.resultItemId, recipe.resultCount - spaceCheck)
            return null
        }

        // Successfully added, now consume required ingredients
        inventory.consumeAllRequirements(recipe.ingredients)
        return ItemStack(recipe.resultItemId, recipe.resultCount, resultDef?.maxDurability ?: 0)
    }

    /**
     * Checks if a damaged/broken tool in [slotIndex] can be repaired using 1 repair material
     * (e.g. 1 Stone for stone/basic tools, 1 Iron Ore for iron tools, or 1 Wood fallback).
     */
    fun canRepairTool(slotIndex: Int, inventory: Inventory): Boolean {
        if (slotIndex !in 0 until inventory.size) return false
        val stack = inventory.slots[slotIndex] ?: return false
        if (!stack.isTool || stack.durability >= stack.maxDurability) return false
        val primaryMat = stack.definition?.repairMaterialId ?: "stone"
        return inventory.hasItem(primaryMat, 1) || inventory.hasItem("wood", 1)
    }

    /**
     * Repairs a damaged or broken tool in [slotIndex] to full durability by consuming 1 unit
     * of its repair material (or 1 Wood fallback).
     */
    fun repairTool(slotIndex: Int, inventory: Inventory): Boolean {
        if (!canRepairTool(slotIndex, inventory)) return false
        val stack = inventory.slots[slotIndex] ?: return false
        val primaryMat = stack.definition?.repairMaterialId ?: "stone"
        val consumed = if (inventory.hasItem(primaryMat, 1)) {
            inventory.removeItem(primaryMat, 1)
        } else {
            inventory.removeItem("wood", 1)
        }
        if (!consumed) return false
        stack.durability = stack.maxDurability
        return true
    }
}
