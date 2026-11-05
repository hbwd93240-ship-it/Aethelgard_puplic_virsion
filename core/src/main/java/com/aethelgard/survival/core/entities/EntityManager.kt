package com.aethelgard.survival.core.entities

import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.combat.CombatSystem
import com.aethelgard.survival.core.player.PlayerCharacter
import com.aethelgard.survival.core.world.WorldManager
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import kotlin.math.sqrt

data class WorldEntity(
    val id: String,
    val name: String,
    val modelInstance: ModelInstance,
    val position: Vector3,
    var isActive: Boolean = true
)

/**
 * Manages active world entities and enemy controllers in AethelgardSurvival.
 */
class EntityManager {
    private val entities = mutableListOf<WorldEntity>()
    val enemyControllers = mutableListOf<EnemyController>()

    fun registerEntity(entity: WorldEntity) {
        entities.add(entity)
    }

    fun registerEnemyController(controller: EnemyController) {
        enemyControllers.add(controller)
        if (!entities.contains(controller.entity)) {
            entities.add(controller.entity)
        }
    }

    fun primaryEnemy(): EnemyController? = enemyControllers.firstOrNull()

    /**
     * Finds the nearest valid living enemy within [maxRange] meters that is either visible
     * in the camera frustum or in close combat proximity to the player.
     */
    fun findNearestLockOnTarget(
        player: PlayerCharacter,
        camera: PerspectiveCamera,
        maxRange: Float = 28f
    ): EnemyController? {
        var bestCandidate: EnemyController? = null
        var bestScoreSq = Float.MAX_VALUE
        val maxRangeSq = maxRange * maxRange

        for (controller in enemyControllers) {
            if (!controller.entity.isActive || controller.aiState == EnemyAiState.DEAD || controller.health <= 0f) {
                continue
            }
            val ex = controller.entity.position.x
            val ey = controller.entity.position.y + 1.1f
            val ez = controller.entity.position.z

            val dx = ex - player.position.x
            val dz = ez - player.position.z
            val distSq = dx * dx + dz * dz
            if (distSq > maxRangeSq) continue

            val inFrustum = camera.frustum.sphereInFrustumWithoutNearFar(ex, ey, ez, 2.4f)
            val isCloseProximity = distSq <= 12f * 12f
            if (!inFrustum && !isCloseProximity) continue

            if (distSq < bestScoreSq) {
                bestScoreSq = distSq
                bestCandidate = controller
            }
        }
        return bestCandidate
    }

    fun resetAllEnemies(worldManager: WorldManager) {
        for (controller in enemyControllers) {
            controller.resetToSpawn(worldManager)
        }
    }

    fun update(
        delta: Float,
        player: PlayerCharacter,
        worldManager: WorldManager,
        audioManager: AudioManager,
        combatSystem: CombatSystem? = null,
        onPlayerHit: (Float) -> Unit
    ) {
        for (controller in enemyControllers) {
            if (controller.entity.isActive) {
                controller.update(delta, player, worldManager, audioManager, combatSystem, onPlayerHit)
            }
        }
    }

    fun render(modelBatch: ModelBatch, environment: Environment) {
        for (entity in entities) {
            if (entity.isActive) {
                modelBatch.render(entity.modelInstance, environment)
            }
        }
        for (controller in enemyControllers) {
            when (controller) {
                is GreenSlimeController -> controller.renderProjectiles(modelBatch, environment)
                is BlueFlowerMonsterController -> controller.renderOrbs(modelBatch, environment)
                is RedFlowerMonsterController -> controller.renderOrbs(modelBatch, environment)
            }
        }
    }

    fun clear() {
        entities.clear()
        enemyControllers.clear()
    }
}
