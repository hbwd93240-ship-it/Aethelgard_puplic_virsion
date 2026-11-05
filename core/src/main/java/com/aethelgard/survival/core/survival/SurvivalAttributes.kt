package com.aethelgard.survival.core.survival

import com.aethelgard.survival.core.player.PlayerCharacter

/**
 * Hardcore Survival Attributes for Aethelgard:
 * - Hunger (0..100)
 * - Thirst (0..100)
 * - Stamina (0..100)
 *
 * Implements balanced decay, regeneration, and real gameplay penalties
 * (starvation damage, reduced stamina regen, sprint/dodge locking).
 */
class SurvivalAttributes(
    val maxHunger: Float = 100f,
    val maxThirst: Float = 100f,
    val maxStamina: Float = 100f
) {
    var hunger: Float = maxHunger
    var thirst: Float = maxThirst
    var stamina: Float = maxStamina

    // Status effect flags
    val isStarving: Boolean
        get() = hunger <= 0.05f

    val isDehydrated: Boolean
        get() = thirst <= 0.05f

    val isExhausted: Boolean
        get() = stamina < 10.0f

    // Internal timers
    private var starvationTimer: Float = 0f

    fun update(
        delta: Float,
        player: PlayerCharacter,
        isSprinting: Boolean = false,
        isHarvesting: Boolean = false
    ) {
        // Balanced natural decay rates per second
        // Hunger: ~0.7 points per minute base (0.0117/s), x1.4 when sprinting/harvesting
        val hungerDrainMultiplier = if (isSprinting || isHarvesting) 1.5f else 1.0f
        hunger = (hunger - delta * 0.014f * hungerDrainMultiplier).coerceIn(0f, maxHunger)

        // Thirst: ~1.0 points per minute base (0.018/s), x1.6 when sprinting/harvesting
        val thirstDrainMultiplier = if (isSprinting || isHarvesting) 1.6f else 1.0f
        thirst = (thirst - delta * 0.022f * thirstDrainMultiplier).coerceIn(0f, maxThirst)

        // Stamina logic
        if (isSprinting) {
            stamina = (stamina - delta * 8.5f).coerceAtLeast(0f)
            if (stamina <= 0f) {
                player.isSprinting = false
            }
        } else {
            // Regeneration rate: 16/s normal, reduced to 4/s if dehydrated
            val regenRate = if (isDehydrated) 4.0f else 16.0f
            stamina = (stamina + delta * regenRate).coerceAtMost(maxStamina)
        }

        // Gameplay penalties:
        // When starving, passive health regeneration is disabled, and player takes periodic damage
        if (isStarving) {
            starvationTimer += delta
            if (starvationTimer >= 5.0f) {
                starvationTimer = 0f
                player.applyDamage(1.5f, 0f, 0f)
            }
        } else {
            starvationTimer = 0f
        }
    }

    /**
     * Checks if stamina can afford a cost and deducts it. Returns true if allowed.
     */
    fun consumeStamina(cost: Float): Boolean {
        if (stamina >= cost) {
            stamina = (stamina - cost).coerceAtLeast(0f)
            return true
        }
        return false
    }

    fun consumeFood(amount: Float) {
        hunger = (hunger + amount).coerceIn(0f, maxHunger)
    }

    fun consumeDrink(amount: Float) {
        thirst = (thirst + amount).coerceIn(0f, maxThirst)
    }

    fun restoreStamina(amount: Float) {
        stamina = (stamina + amount).coerceIn(0f, maxStamina)
    }

    /**
     * Consumes food, drink, or medicine from [item], applying its recovery effects.
     */
    fun consume(item: ItemDefinition, player: PlayerCharacter): Boolean {
        var consumed = false
        if (item.hungerRestore > 0f) {
            hunger = (hunger + item.hungerRestore).coerceAtMost(maxHunger)
            consumed = true
        }
        if (item.thirstRestore > 0f) {
            thirst = (thirst + item.thirstRestore).coerceAtMost(maxThirst)
            consumed = true
        }
        if (item.staminaRestore > 0f) {
            stamina = (stamina + item.staminaRestore).coerceAtMost(maxStamina)
            consumed = true
        }
        if (item.healthRestore > 0f) {
            player.health = (player.health + item.healthRestore).coerceAtMost(player.maxHealth)
            consumed = true
        }
        if (item.energyRestore > 0f) {
            player.energy = (player.energy + item.energyRestore).coerceAtMost(player.maxEnergy)
            consumed = true
        }
        return consumed
    }

    fun resetToFull() {
        hunger = maxHunger
        thirst = maxThirst
        stamina = maxStamina
        starvationTimer = 0f
    }

    fun serialize(): String {
        return "$hunger,$thirst,$stamina"
    }

    fun deserialize(data: String) {
        if (data.isBlank()) return
        val parts = data.split(",")
        if (parts.size >= 3) {
            hunger = parts[0].toFloatOrNull()?.coerceIn(0f, maxHunger) ?: maxHunger
            thirst = parts[1].toFloatOrNull()?.coerceIn(0f, maxThirst) ?: maxThirst
            stamina = parts[2].toFloatOrNull()?.coerceIn(0f, maxStamina) ?: maxStamina
        }
    }
}
