package com.aethelgard.survival.core.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Manages the 3D fantasy world environment, lighting, atmospheric fog, animated mana crystals,
 * terrain height queries, central monument step heights, and obstacle collision resolution.
 */
class WorldManager(val worldGenerator: WorldGenerator) {

    val dayNightCycle: DayNightCycle = DayNightCycle()
    val environment: Environment = Environment()
    private lateinit var worldData: GeneratedWorldData
    private val monumentCenterLight = PointLight()
    private val primaryLight = DirectionalLight()
    private val secondaryLight = DirectionalLight()
    private val ambientLightAttr = ColorAttribute(ColorAttribute.AmbientLight, 0.44f, 0.48f, 0.45f, 1f)
    private val fogAttr = ColorAttribute(ColorAttribute.Fog, 0.58f, 0.68f, 0.82f, 1f)
    private var worldTime: Float = 0f

    fun initialize() {
        environment.set(ambientLightAttr)
        environment.set(fogAttr)

        environment.add(primaryLight)
        environment.add(secondaryLight)

        // Central ancient monument glowing mana point light
        monumentCenterLight.set(0.22f, 0.88f, 0.98f, 0f, 4.2f, 0f, 28f)
        environment.add(monumentCenterLight)

        worldData = worldGenerator.generateClearingWorld()
        update(0.016f)
    }

    fun update(delta: Float) {
        worldTime += delta
        dayNightCycle.update(delta)

        // Update ambient and fog colors from the active Day/Night atmosphere
        ambientLightAttr.color.set(dayNightCycle.ambientColor)
        fogAttr.color.set(dayNightCycle.fogColor)

        // Update directional lighting: Sun during daylight, Moon at night
        if (dayNightCycle.isDaytime) {
            primaryLight.set(
                dayNightCycle.sunLightColor,
                -dayNightCycle.sunDirection.x,
                -dayNightCycle.sunDirection.y,
                -dayNightCycle.sunDirection.z
            )
            secondaryLight.set(
                0.32f, 0.38f, 0.42f,
                dayNightCycle.sunDirection.x * 0.4f,
                -0.45f,
                dayNightCycle.sunDirection.z * 0.4f
            )
        } else {
            primaryLight.set(
                dayNightCycle.moonLightColor,
                -dayNightCycle.moonDirection.x,
                -dayNightCycle.moonDirection.y,
                -dayNightCycle.moonDirection.z
            )
            secondaryLight.set(
                0.16f, 0.12f, 0.24f,
                dayNightCycle.moonDirection.x * 0.4f,
                -0.45f,
                dayNightCycle.moonDirection.z * 0.4f
            )
        }

        // Dynamically tint sky dome and celestial bodies
        val assets = worldGenerator.modelFactory.assets
        assets.skyDomeMaterial.set(ColorAttribute.createDiffuse(dayNightCycle.skyZenithColor))
        assets.skyDomeMaterial.set(ColorAttribute.createEmissive(dayNightCycle.skyZenithColor))

        // Pulse central monument mana light gently
        val intensity = 26f + sin(worldTime * 2.2f) * 4.5f
        monumentCenterLight.set(0.22f, 0.88f, 0.98f, 0f, 4.2f, 0f, intensity)

        // Animate orbiting & floating mana crystals
        for (fc in worldData.floatingCrystals) {
            val angle = fc.initialPhase + worldTime * fc.orbitSpeed
            val bob = sin(worldTime * fc.bobFrequency + fc.initialPhase) * fc.bobAmplitude
            val spinDeg = (worldTime * 68f + fc.initialPhase * 57.3f) % 360f

            if (fc.isOrbitingCenter) {
                val x = cos(angle) * fc.orbitRadius
                val z = sin(angle) * fc.orbitRadius
                val y = fc.basePosition.y + bob
                fc.instance.transform.idt()
                    .translate(x, y, z)
                    .rotate(0f, 1f, 0f, spinDeg)
                    .rotate(1f, 0f, 1f, 14f)
            } else {
                val x = fc.basePosition.x + cos(angle) * fc.orbitRadius
                val z = fc.basePosition.z + sin(angle) * fc.orbitRadius
                val y = fc.basePosition.y + bob
                fc.instance.transform.idt()
                    .translate(x, y, z)
                    .rotate(0f, 1f, 0f, spinDeg)
                    .scale(0.65f, 0.65f, 0.65f)
            }
        }
    }

    fun render(modelBatch: ModelBatch, camera: PerspectiveCamera) {
        // Keep sky dome centered around player horizontally, oriented by celestial trajectory
        val celestialAngleDeg = ((dayNightCycle.inGameHoursFloat - 6f) / 24f) * 360f
        worldData.skyDomeInstance.transform
            .setToTranslation(camera.position.x, 0f, camera.position.z)
            .rotate(0f, 0f, 1f, celestialAngleDeg)
        modelBatch.render(worldData.skyDomeInstance)

        modelBatch.render(worldData.terrainInstance, environment)
        modelBatch.render(worldData.centralMonumentInstance, environment)

        for (inst in worldData.staticEnvironmentInstances) {
            modelBatch.render(inst, environment)
        }
        for (fc in worldData.floatingCrystals) {
            modelBatch.render(fc.instance, environment)
        }
    }

    fun getTerrainHeight(x: Float, z: Float): Float {
        return worldGenerator.computeTerrainHeight(x, z)
    }

    /** Optional callback provided by BuildingManager to evaluate walkable floors, stairs, foundations, and roofs. */
    var buildingSurfaceHeightProvider: ((x: Float, z: Float, currentY: Float, naturalGroundY: Float) -> Float)? = null

    /** Optional callback provided by BuildingManager to resolve OBB collisions against walls, pillars, closed doors, and furniture. */
    var buildingCollisionResolver: ((position: Vector3, entityRadius: Float) -> Unit)? = null

    /**
     * Returns the walkable height at (x, z), accounting for the natural terrain heightfield,
     * the 3 stepped octagonal tiers of the Central Ancient Monument at (0, 0, 0), and any walkable player buildings.
     */
    fun getWalkableSurfaceHeight(x: Float, z: Float, currentY: Float = 999f): Float {
        val baseGround = worldGenerator.computeTerrainHeight(x, z)
        val distFromMonument = sqrt(x * x + z * z)
        val monumentStep = when {
            distFromMonument < 1.80f -> 1.18f
            distFromMonument < 2.82f -> 0.85f
            distFromMonument < 3.82f -> 0.50f
            else -> 0f
        }
        val naturalHeight = if (monumentStep > 0f) {
            max(baseGround, worldGenerator.computeTerrainHeight(0f, 0f) + monumentStep)
        } else {
            baseGround
        }
        return buildingSurfaceHeightProvider?.invoke(x, z, currentY, naturalHeight) ?: naturalHeight
    }

    /**
     * Pushes the given position smoothly outside any overlapping circular obstacle colliders and placed building pieces.
     */
    fun resolveObstacleCollisions(position: Vector3, entityRadius: Float) {
        for (collider in worldData.obstacleColliders) {
            val dx = position.x - collider.x
            val dz = position.z - collider.z
            val minDist = collider.radius + entityRadius
            val distSq = dx * dx + dz * dz
            if (distSq < minDist * minDist) {
                val dist = sqrt(distSq)
                if (dist > 0.0001f) {
                    val push = (minDist - dist)
                    position.x += (dx / dist) * push
                    position.z += (dz / dist) * push
                } else {
                    position.z += minDist
                }
            }
        }
        buildingCollisionResolver?.invoke(position, entityRadius)
    }

    val generatedData: GeneratedWorldData
        get() = worldData

    fun removeStaticInstance(instance: ModelInstance) {
        worldData.staticEnvironmentInstances.remove(instance)
    }

    fun removeObstacleCollider(collider: ObstacleCollider) {
        worldData.obstacleColliders.remove(collider)
    }

    fun addStaticInstance(instance: ModelInstance) {
        worldData.staticEnvironmentInstances.add(instance)
    }

    fun addObstacleCollider(collider: ObstacleCollider) {
        worldData.obstacleColliders.add(collider)
    }
}
