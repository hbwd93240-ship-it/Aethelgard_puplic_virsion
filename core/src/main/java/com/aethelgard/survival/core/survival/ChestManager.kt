package com.aethelgard.survival.core.survival

/**
 * Manages functional storage chest inventory (20 slots), transfers between player inventory
 * and chest, and persistence across camera switches and gameplay sessions.
 */
class ChestManager(val capacity: Int = 20) {

    val chestInventory: Inventory = Inventory(capacity)

    /**
     * Transfers an entire stack from the player's inventory slot [playerSlotIndex] into the chest.
     */
    fun depositFromPlayer(playerSlotIndex: Int, playerInventory: Inventory): Boolean {
        if (playerSlotIndex !in 0 until playerInventory.size) return false
        val item = playerInventory.slots[playerSlotIndex] ?: return false

        val remaining = chestInventory.addItem(item.itemId, item.count)
        val added = item.count - remaining
        if (added > 0) {
            item.count -= added
            if (item.count <= 0) {
                playerInventory.slots[playerSlotIndex] = null
            }
            return true
        }
        return false
    }

    /**
     * Transfers an entire stack from the chest's slot [chestSlotIndex] into the player's inventory.
     */
    fun withdrawToPlayer(chestSlotIndex: Int, playerInventory: Inventory): Boolean {
        if (chestSlotIndex !in 0 until chestInventory.size) return false
        val item = chestInventory.slots[chestSlotIndex] ?: return false

        val remaining = playerInventory.addItem(item.itemId, item.count)
        val added = item.count - remaining
        if (added > 0) {
            item.count -= added
            if (item.count <= 0) {
                chestInventory.slots[chestSlotIndex] = null
            }
            return true
        }
        return false
    }

    /**
     * Transfers all items from player inventory (excluding hotbar slots 0..4) into chest.
     */
    fun depositAllBagItems(playerInventory: Inventory): Int {
        var totalTransferred = 0
        for (i in 5 until playerInventory.size) {
            val item = playerInventory.slots[i] ?: continue
            val remaining = chestInventory.addItem(item.itemId, item.count)
            val added = item.count - remaining
            if (added > 0) {
                totalTransferred += added
                item.count -= added
                if (item.count <= 0) {
                    playerInventory.slots[i] = null
                }
            }
        }
        return totalTransferred
    }

    /**
     * Transfers all items from chest into player inventory.
     */
    fun withdrawAllItems(playerInventory: Inventory): Int {
        var totalTransferred = 0
        for (i in 0 until chestInventory.size) {
            val item = chestInventory.slots[i] ?: continue
            val remaining = playerInventory.addItem(item.itemId, item.count)
            val added = item.count - remaining
            if (added > 0) {
                totalTransferred += added
                item.count -= added
                if (item.count <= 0) {
                    chestInventory.slots[i] = null
                }
            }
        }
        return totalTransferred
    }

    fun serialize(): String {
        return chestInventory.serialize()
    }

    fun deserialize(data: String) {
        chestInventory.deserialize(data)
    }
}
