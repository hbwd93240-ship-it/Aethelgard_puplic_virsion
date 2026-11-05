package com.aethelgard.survival.core.building

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.combat.CombatSystem
import com.aethelgard.survival.core.entities.EnemyAiState
import com.aethelgard.survival.core.entities.EnemyController
import com.aethelgard.survival.core.entities.EntityManager
import com.aethelgard.survival.core.player.PlayerCharacter
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.sqrt

enum class MonsterArchetype(val displayName: String, val structuralDamageMultiplier: Float) {
    SCOUT_STALKER("Corrupted Stalker", 0.55f),
    CORRUPTED_KNIGHT("Corrupted Mana Guardian", 1.0f),
    SIEGE_BRUTE("Abyssal Siege Brute", 1.45f)
}

/**
 * Manages conditional, restrained environmental monster raids and structure-breaching behavior (Requirement 10):
 * - Evaluates threat buildup from:
 *   1. Building near monster territory / Central Ancient Monument
 *   2. Noise from mining, chopping, and combat
 *   3. Light emitted by placed lit torches & lanterns
 *   4. World cycle phase (twilight / night peaks)
 * - Enforces a generous cooldown so raids feel organic and restrained rather than a constant nuisance.
 * - Directs monsters to seek entrances (doors/gates) first and attack obstructing doors/walls when blocked.
 * - Triggers tactical retreat when a monster is badly injured (< 18% HP).
 */
class BuildingRaidManager(
    private val buildingManager: BuildingManager,
    private val entityManager: EntityManager,
    private val audioManager: AudioManager
) {
    var recentNoiseLevel: Float = 0f
        private set
    var threatLevel: Float = 0f
        private set
    var raidCooldownTimer: Float = 45f
        private set
    var isRaidActive: Boolean = false
        private set
    var activeRaidTimer: Float = 0f
        private set
    var activeRaidTargetPieceId: String? = null
        private set

    private var worldCycleTime: Float = 0f
    private var structureAttackCooldown: Float = 0f
    private val retractedEnemyIds = mutableSetOf<String>()
    private var enemyRetreatTimer: Float = 0f

    /** Records noise from mining boulders, chopping trees, or combat clashes near player buildings. */
    fun recordNoiseEvent(amount: Float, position: Vector3) {
        val nearestBuilding = buildingManager.placedPieces.minByOrNull { it.position.dst2(position) }
        val distSq = nearestBuilding?.position?.dst2(position) ?: Float.MAX_VALUE
        val proximityFactor = if (distSq <= 28f * 28f) 1.0f else 0.35f
        recentNoiseLevel = (recentNoiseLevel + amount * proximityFactor).coerceIn(0f, 100f)
    }

    /**
     * Computes the current environmental raid threat score [0..100] based on placed structures,
     * proximity to the Central Monument lair (0,0,0), active light sources, noise, and world cycle.
     */
    fun evaluateEnvironmentalThreat(): Float {
        val pieces = buildingManager.placedPieces
        if (pieces.isEmpty()) return 0f

        var proximityScore = 0f
        var lightScore = 0f
        for (p in pieces) {
            val distFromLair = sqrt(p.position.x * p.position.x + p.position.z * p.position.z)
            if (distFromLair < 16f) {
                proximityScore += (16f - distFromLair) * 1.4f
            } else if (distFromLair < 26f) {
                proximityScore += (26f - distFromLair) * 0.45f
            }
            if (p.definition.interactionType == BuildingInteractionType.LIGHT_SOURCE && p.isLit) {
                lightScore += 4.5f
            }
        }

        val nightFactor = 1.0f + 0.35f * kotlin.math.sin(worldCycleTime * 0.08f)
        val rawThreat = (proximityScore.coerceAtMost(45f) + lightScore.coerceAtMost(25f) + recentNoiseLevel * 0.65f) * nightFactor

        // Territory Marker slightly dampens passive monster attraction inside claimed homesteads
        val hasProtectedPieces = pieces.any { buildingManager.isInsideProtectedTerritory(it.position) }
        val mitigation = if (hasProtectedPieces) 0.75f else 1.0f
        return (rawThreat * mitigation).coerceIn(0f, 100f)
    }

    fun update(
        delta: Float,
        player: PlayerCharacter,
        combatSystem: CombatSystem? = null,
        onRaidNotification: (String) -> Unit = {}
    ) {
        worldCycleTime += delta
        recentNoiseLevel = (recentNoiseLevel - delta * 2.2f).coerceAtLeast(0f)
        if (raidCooldownTimer > 0f) {
            raidCooldownTimer = (raidCooldownTimer - delta).coerceAtLeast(0f)
        }
        if (structureAttackCooldown > 0f) {
            structureAttackCooldown = (structureAttackCooldown - delta).coerceAtLeast(0f)
        }

        threatLevel = evaluateEnvironmentalThreat()

        // Trigger a restrained environmental raid only if threat >= 65, cooldown is 0, and player has built a structure
        if (!isRaidActive && raidCooldownTimer <= 0f && threatLevel >= 65f && buildingManager.placedPieces.size >= 3) {
            triggerRaid(player, onRaidNotification)
        }

        if (isRaidActive) {
            activeRaidTimer -= delta
            if (activeRaidTimer <= 0f || buildingManager.placedPieces.isEmpty()) {
                isRaidActive = false
                activeRaidTargetPieceId = null
                raidCooldownTimer = 95f // Long breathing room between raids
            }
        }

        // Update monster interaction with buildings (door/wall breaching + injured tactical retreat)
        for (enemy in entityManager.enemyControllers) {
            if (!enemy.entity.isActive || enemy.aiState == EnemyAiState.DEAD || enemy.health <= 0f) {
                retractedEnemyIds.remove(enemy.entity.id)
                continue
            }

            // 1. Injured Monster Retreat (< 18% HP): monster briefly retreats away from player/structures once per fight
            val healthRatio = enemy.health / enemy.maxHealth.coerceAtLeast(1f)
            if (healthRatio <= 0.18f && !retractedEnemyIds.contains(enemy.entity.id)) {
                retractedEnemyIds.add(enemy.entity.id)
                enemyRetreatTimer = 2.8f
                onRaidNotification("MONSTER BADLY INJURED - RETREATING!")
            }

            if (enemyRetreatTimer > 0f && retractedEnemyIds.contains(enemy.entity.id)) {
                enemyRetreatTimer = (enemyRetreatTimer - delta).coerceAtLeast(0f)
                val awayX = enemy.entity.position.x - player.position.x
                val awayZ = enemy.entity.position.z - player.position.z
                val dist = sqrt(awayX * awayX + awayZ * awayZ).coerceAtLeast(0.01f)
                enemy.velocity.x = (awayX / dist) * 4.2f
                enemy.velocity.z = (awayZ / dist) * 4.2f
                continue
            }

            // 2. Check if a closed Door or Wall/Fence obstructs the monster's path to the player (or active raid target)
            if (buildingManager.placedPieces.isNotEmpty() && structureAttackCooldown <= 0f) {
                val obstructingPiece = findObstructingStructure(enemy.entity.position, player.position)
                if (obstructingPiece != null) {
                    structureAttackCooldown = 1.65f
                    val baseRawDamage = if (enemy.isFuryPhase) 32f else 22f
                    val destroyed = attackBuildingWithMonster(
                        piece = obstructingPiece,
                        rawDamage = baseRawDamage,
                        archetype = MonsterArchetype.CORRUPTED_KNIGHT,
                        combatSystem = combatSystem
                    )
                    if (destroyed) {
                        onRaidNotification("${obstructingPiece.definition.name.uppercase()} BREACHED!")
                    }
                }
            }
        }
    }

    /**
     * Triggers an environmental raid on the player's outpost, prioritizing entrances (Doors/Gates) first,
     * then outer walls/fences.
     */
    fun triggerRaid(player: PlayerCharacter, onRaidNotification: (String) -> Unit = {}): PlacedBuildingPiece? {
        if (buildingManager.placedPieces.isEmpty()) return null
        // Prioritize doors/gates (entrances) first, then walls/fences, then other pieces
        val target = buildingManager.placedPieces.minByOrNull { piece ->
            val priorityBonus = when (piece.definition.category) {
                BuildingCategory.DOORS -> -50f
                BuildingCategory.FENCES -> -25f
                BuildingCategory.WALLS, BuildingCategory.WINDOWS -> -15f
                else -> 0f
            }
            piece.position.dst(player.position) + priorityBonus
        } ?: return null

        isRaidActive = true
        activeRaidTimer = 28f
        activeRaidTargetPieceId = target.instanceId
        raidCooldownTimer = 95f
        recentNoiseLevel = 0f
        onRaidNotification("MONSTER RAID APPROACHING: ${target.definition.name.uppercase()}!")
        return target
    }

    /**
     * Finds a closed Door, Wall, Window, or Fence blocking the monster within melee reach (2.75m)
     * along its approach toward the player, prioritizing Doors/Gates (entrances) first.
     */
    fun findObstructingStructure(
        monsterPos: Vector3,
        targetPos: Vector3,
        reachRadius: Float = 2.75f
    ): PlacedBuildingPiece? {
        val toTargetX = targetPos.x - monsterPos.x
        val toTargetZ = targetPos.z - monsterPos.z
        val distToTarget = sqrt(toTargetX * toTargetX + toTargetZ * toTargetZ)
        if (distToTarget < 0.2f) return null
        val dirX = toTargetX / distToTarget
        val dirZ = toTargetZ / distToTarget

        var bestCandidate: PlacedBuildingPiece? = null
        var bestScore = Float.MAX_VALUE

        for (piece in buildingManager.placedPieces) {
            val cat = piece.definition.category
            val isBarrier = cat == BuildingCategory.DOORS ||
                cat == BuildingCategory.WALLS ||
                cat == BuildingCategory.WINDOWS ||
                cat == BuildingCategory.FENCES
            if (!isBarrier) continue
            // Open doors do not block movement
            if (cat == BuildingCategory.DOORS && piece.isDoorOpen) continue

            // Vertical overlap check
            if (abs(piece.position.y - monsterPos.y) > 2.5f) continue

            val dx = piece.position.x - monsterPos.x
            val dz = piece.position.z - monsterPos.z
            val dist = sqrt(dx * dx + dz * dz)
            if (dist > reachRadius) continue

            // Must lie generally between the monster and the target (dot > 0.15)
            val dot = (dx * dirX + dz * dirZ) / dist.coerceAtLeast(0.01f)
            if (dot < 0.15f) continue

            // Prioritize doors/entrances over solid walls when both are nearby
            val entrancePreference = if (cat == BuildingCategory.DOORS) -0.9f else 0f
            val score = dist + entrancePreference
            if (score < bestScore) {
                bestScore = score
                bestCandidate = piece
            }
        }
        return bestCandidate
    }

    /**
     * Applies monster structural damage to a building piece, respecting material resistance
     * and ensuring a single hit can never destroy a full-health house wall or door.
     */
    fun attackBuildingWithMonster(
        piece: PlacedBuildingPiece,
        rawDamage: Float,
        archetype: MonsterArchetype = MonsterArchetype.CORRUPTED_KNIGHT,
        combatSystem: CombatSystem? = null
    ): Boolean {
        val scaledRaw = rawDamage * archetype.structuralDamageMultiplier
        // Cap single-hit structural damage at 45% of the piece's maxHealth so a single hit NEVER destroys a full-health structure
        val maxAllowedEffective = piece.maxHealth * 0.45f
        val resistance = piece.definition.material.monsterDamageResistance.coerceAtLeast(0.1f)
        val clampedRaw = scaledRaw.coerceAtMost(maxAllowedEffective / resistance)

        return buildingManager.damagePiece(
            piece = piece,
            rawMonsterDamage = clampedRaw,
            audioManager = audioManager,
            combatSystem = combatSystem
        )
    }
}
