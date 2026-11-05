package com.aethelgard.survival.core.survival

data class ItemStack(
    val itemId: String,
    var count: Int,
    var durability: Int = ResourceCatalog.get(itemId)?.maxDurability ?: 0
) {
    val definition: ItemDefinition?
        get() = ResourceCatalog.get(itemId)

    val maxDurability: Int
        get() = definition?.maxDurability ?: 0

    val isTool: Boolean
        get() = (definition?.category == ItemCategory.TOOL) && maxDurability > 0

    val isBroken: Boolean
        get() = isTool && durability <= 0

    val durabilityRatio: Float
        get() = if (maxDurability > 0) (durability.toFloat() / maxDurability.toFloat()).coerceIn(0f, 1f) else 1f

    fun canStackWith(other: ItemStack): Boolean {
        if (itemId != other.itemId) return false
        val limit = definition?.stackLimit ?: 99
        if (limit <= 1) return false
        return count < limit
    }
}

/**
 * Terraria-style persistent player inventory for Aethelgard Survival Mode.
 * Holds 25 slots total:
 * - Slots 0..4: Quick-access Hotbar
 * - Slots 5..24: Main Inventory Bag (20 slots)
 */
class Inventory(val size: Int = 25) {

    val slots = Array<ItemStack?>(size) { null }
    var activeHotbarIndex: Int = 0

    val activeHotbarItem: ItemStack?
        get() = if (activeHotbarIndex in 0..4) slots[activeHotbarIndex] else null

    /**
     * Adds [count] of [itemId] into existing matching stacks first, then into empty slots.
     * Returns the quantity that could NOT be added (0 if all was added).
     */
    fun addItem(itemId: String, count: Int): Int {
        val def = ResourceCatalog.get(itemId) ?: return count
        var remaining = count
        val maxStack = def.stackLimit

        // 1. Try to fill existing stacks of the same item (if stackable)
        if (maxStack > 1) {
            for (i in 0 until size) {
                val slot = slots[i]
                if (slot != null && slot.itemId == itemId && slot.count < maxStack) {
                    val space = maxStack - slot.count
                    val add = minOf(space, remaining)
                    slot.count += add
                    remaining -= add
                    if (remaining <= 0) return 0
                }
            }
        }

        // 2. Put remaining into first available empty slots
        for (i in 0 until size) {
            if (slots[i] == null) {
                val add = minOf(maxStack, remaining)
                slots[i] = ItemStack(itemId, add, def.maxDurability)
                remaining -= add
                if (remaining <= 0) return 0
            }
        }

        return remaining
    }

    /**
     * Checks if the inventory contains at least [count] of [itemId].
     */
    fun hasItem(itemId: String, count: Int): Boolean {
        var found = 0
        for (i in 0 until size) {
            val slot = slots[i]
            if (slot != null && slot.itemId == itemId) {
                found += slot.count
                if (found >= count) return true
            }
        }
        return false
    }

    /**
     * Counts the total number of [itemId] currently in the inventory.
     */
    fun countItem(itemId: String): Int {
        var total = 0
        for (i in 0 until size) {
            val slot = slots[i]
            if (slot != null && slot.itemId == itemId) {
                total += slot.count
            }
        }
        return total
    }

    /**
     * Removes [count] of [itemId] across all slots. Returns true if successful.
     */
    fun removeItem(itemId: String, count: Int): Boolean {
        if (!hasItem(itemId, count)) return false
        var toRemove = count
        for (i in 0 until size) {
            val slot = slots[i]
            if (slot != null && slot.itemId == itemId) {
                if (slot.count <= toRemove) {
                    toRemove -= slot.count
                    slots[i] = null
                } else {
                    slot.count -= toRemove
                    toRemove = 0
                }
                if (toRemove <= 0) break
            }
        }
        return true
    }

    /**
     * Swaps two slots or merges them if identical and under stack limit.
     */
    fun swapOrMergeSlots(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in 0 until size || toIndex !in 0 until size || fromIndex == toIndex) return
        val from = slots[fromIndex] ?: return
        val to = slots[toIndex]

        if (to == null) {
            slots[toIndex] = from
            slots[fromIndex] = null
        } else if (from.itemId == to.itemId && (from.definition?.stackLimit ?: 99) > 1) {
            val maxStack = from.definition?.stackLimit ?: 99
            val space = maxStack - to.count
            if (space > 0) {
                val add = minOf(space, from.count)
                to.count += add
                from.count -= add
                if (from.count <= 0) {
                    slots[fromIndex] = null
                }
            } else {
                slots[fromIndex] = to
                slots[toIndex] = from
            }
        } else {
            slots[fromIndex] = to
            slots[toIndex] = from
        }
    }

    fun decreaseSlot(index: Int, amount: Int = 1) {
        if (index !in 0 until size) return
        val slot = slots[index] ?: return
        slot.count -= amount
        if (slot.count <= 0) {
            slots[index] = null
        }
    }

    /**
     * Drops one or all items from a slot.
     */
    fun dropSlot(index: Int, all: Boolean = true): ItemStack? {
        if (index !in 0 until size) return null
        val slot = slots[index] ?: return null
        if (all || slot.count <= 1) {
            slots[index] = null
            return slot
        } else {
            slot.count -= 1
            return ItemStack(slot.itemId, 1, slot.durability)
        }
    }

    /**
     * Checks if all required ingredients in [requirements] are present.
     */
    fun hasAllRequirements(requirements: Map<String, Int>): Boolean {
        for ((itemId, count) in requirements) {
            if (!hasItem(itemId, count)) return false
        }
        return true
    }

    /**
     * Consumes all required ingredients in [requirements].
     */
    fun consumeAllRequirements(requirements: Map<String, Int>): Boolean {
        if (!hasAllRequirements(requirements)) return false
        for ((itemId, count) in requirements) {
            removeItem(itemId, count)
        }
        return true
    }

    /**
     * Finds the slot index of the best usable (non-broken) tool matching [toolType].
     * Prioritizes the currently selected hotbar slot if it matches [toolType] and is not broken.
     * Returns -1 if no usable tool of [toolType] is available.
     */
    fun findBestUsableToolSlot(toolType: ToolType): Int {
        val active = activeHotbarItem
        if (active != null && !active.isBroken && active.definition?.toolType == toolType) {
            return activeHotbarIndex
        }
        var bestSlot = -1
        var bestPower = 0f
        for (i in 0 until size) {
            val stack = slots[i] ?: continue
            if (stack.isBroken) continue
            val def = stack.definition ?: continue
            if (def.category == ItemCategory.TOOL && def.toolType == toolType) {
                if (def.toolPower > bestPower) {
                    bestPower = def.toolPower
                    bestSlot = i
                }
            }
        }
        return bestSlot
    }

    /**
     * Reduces the durability of the tool at [slotIndex] by [amount].
     * Clamps durability at 0 (unusable/broken state).
     * Returns true if the tool was usable prior to the hit.
     */
    fun damageToolInSlot(slotIndex: Int, amount: Int = 1): Boolean {
        if (slotIndex !in 0 until size) return false
        val stack = slots[slotIndex] ?: return false
        if (!stack.isTool || stack.isBroken) return false
        stack.durability = (stack.durability - amount).coerceAtLeast(0)
        return true
    }

    /**
     * Reduces durability of the active hotbar tool by [amount].
     * Returns true if the active hotbar item is a usable tool and was damaged.
     */
    fun damageActiveTool(amount: Int = 1): Boolean {
        return damageToolInSlot(activeHotbarIndex, amount)
    }

    /**
     * Restores the tool in [slotIndex] to full maxDurability.
     */
    fun restoreToolDurability(slotIndex: Int): Boolean {
        if (slotIndex !in 0 until size) return false
        val stack = slots[slotIndex] ?: return false
        if (!stack.isTool) return false
        stack.durability = stack.maxDurability
        return true
    }

    /**
     * Returns the highest tool power for a given category (e.g. WOOD for chopping, STONE for mining),
     * ignoring broken tools (durability <= 0).
     */
    fun getBestToolPower(targetCategory: String): Float {
        var best = 1.0f
        for (i in 0 until size) {
            val stack = slots[i] ?: continue
            if (stack.isBroken) continue
            val def = stack.definition ?: continue
            if (def.category == ItemCategory.TOOL) {
                when (targetCategory) {
                    "WOOD" -> if (def.toolType == ToolType.AXE) best = maxOf(best, def.toolPower)
                    "STONE" -> if (def.toolType == ToolType.PICKAXE) best = maxOf(best, def.toolPower)
                    "SHOVEL" -> if (def.toolType == ToolType.SHOVEL) best = maxOf(best, def.toolPower)
                    "SICKLE" -> if (def.toolType == ToolType.SICKLE) best = maxOf(best, def.toolPower)
                    "KNIFE" -> if (def.toolType == ToolType.KNIFE) best = maxOf(best, def.toolPower)
                }
            }
        }
        return best
    }

    fun clear() {
        for (i in 0 until size) {
            slots[i] = null
        }
        activeHotbarIndex = 0
    }

    /**
     * Serializes to a compact string format: "index:itemId:count[:durability];..."
     */
    fun serialize(): String {
        val sb = StringBuilder()
        for (i in 0 until size) {
            val slot = slots[i]
            if (slot != null && slot.count > 0) {
                if (sb.isNotEmpty()) sb.append(";")
                if (slot.isTool) {
                    sb.append("$i:${slot.itemId}:${slot.count}:${slot.durability}")
                } else {
                    sb.append("$i:${slot.itemId}:${slot.count}")
                }
            }
        }
        return sb.toString()
    }

    fun deserialize(data: String) {
        clear()
        if (data.isBlank()) return
        val entries = data.split(";")
        for (entry in entries) {
            val parts = entry.split(":")
            if (parts.size >= 3) {
                val index = parts[0].toIntOrNull() ?: continue
                val itemId = parts[1]
                val count = parts[2].toIntOrNull() ?: continue
                val def = ResourceCatalog.get(itemId) ?: continue
                val durability = if (parts.size >= 4) {
                    parts[3].toIntOrNull() ?: def.maxDurability
                } else {
                    def.maxDurability
                }
                if (index in 0 until size) {
                    slots[index] = ItemStack(itemId, count, durability)
                }
            }
        }
    }
}
