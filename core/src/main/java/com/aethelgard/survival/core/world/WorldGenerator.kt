package com.aethelgard.survival.core.world

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ObstacleCollider(
    val x: Float,
    val z: Float,
    val radius: Float,
    val label: String
)

data class FloatingCrystalInstance(
    val instance: ModelInstance,
    val basePosition: Vector3,
    val orbitRadius: Float,
    val orbitSpeed: Float,
    val initialPhase: Float,
    val bobAmplitude: Float,
    val bobFrequency: Float,
    val isOrbitingCenter: Boolean
)

data class GeneratedWorldData(
    val terrainInstance: ModelInstance,
    val skyDomeInstance: ModelInstance,
    val centralMonumentInstance: ModelInstance,
    val staticEnvironmentInstances: MutableList<ModelInstance>,
    val floatingCrystals: List<FloatingCrystalInstance>,
    val obstacleColliders: MutableList<ObstacleCollider>,
    val treeInstances: MutableList<ModelInstance> = mutableListOf(),
    val treeColliders: MutableList<ObstacleCollider> = mutableListOf(),
    val boulderInstances: MutableList<ModelInstance> = mutableListOf(),
    val boulderColliders: MutableList<ObstacleCollider> = mutableListOf(),
    val darkGrassPatchInstances: MutableList<ModelInstance> = mutableListOf(),
    val dirtPatchInstances: MutableList<ModelInstance> = mutableListOf()
)

/**
 * Deterministically composes the Fantasy Forest & Uneven Natural World for AethelgardSurvival:
 * - Natural uneven terrain with gentle slopes, rolling hills, small valleys, and organic ground undulations
 * - Preserves the central Ancient Rune Monument at (0, 0, 0) and spawn sightline
 * - Ringed groves of multi-canopy cyan & violet bioluminescent fantasy trees
 * - Mossy boulders & crystal rock formations
 * - Dark green grass patches, small grass clusters, and subtle earthy dirt patches
 * - Orbiting mana shards and drifting ambient mana motes
 */
class WorldGenerator(val modelFactory: ProceduralModelFactory) {

    fun computeTerrainHeight(x: Float, z: Float): Float {
        // Keep exact central monument altar (0, 0) and central stone walkway (0, 15) level at 0f
        if (kotlin.math.abs(x) <= 1.8f && z >= -1.8f && z <= 16.2f) {
            return 0f
        }

        val dist = sqrt(x * x + z * z)
        // Smooth transition from the narrow central altar walkway into uneven natural terrain
        val dxWalkway = (kotlin.math.abs(x) - 1.8f).coerceAtLeast(0f)
        val dzWalkway = when {
            z < -1.8f -> -1.8f - z
            z > 16.2f -> z - 16.2f
            else -> 0f
        }
        val distFromWalkway = sqrt(dxWalkway * dxWalkway + dzWalkway * dzWalkway)
        val walkwayBlend = (distFromWalkway / 5.0f).coerceIn(0f, 1f)
        val smoothWalkwayBlend = walkwayBlend * walkwayBlend * (3f - 2f * walkwayBlend)

        // Outer macro hills blend
        val clearingBlend = ((dist - 14f) / 22f).coerceIn(0f, 1f)
        val smoothBlend = clearingBlend * clearingBlend * (3f - 2f * clearingBlend)

        // 1. Clearing & meadow uneven ground: gentle slopes, rolling knolls, and shallow dips
        val clearingUndulation = sin(x * 0.095f + 0.4f) * cos(z * 0.088f - 0.3f) * 0.72f +
            cos(x * 0.14f - z * 0.12f + 0.7f) * 0.44f +
            sin(x * 0.21f + z * 0.19f) * 0.22f
        val clearingEastHill = kotlin.math.exp(-((x - 14f) * (x - 14f) + (z - 8f) * (z - 8f)) / 130f) * 1.25f
        val clearingWestValley = -kotlin.math.exp(-((x + 14f) * (x + 14f) + (z - 11f) * (z - 11f)) / 120f) * 0.82f
        val clearingSouthMound = kotlin.math.exp(-((x - 7f) * (x - 7f) + (z - 25f) * (z - 25f)) / 150f) * 0.95f

        // 2. Rolling natural hills, ridges, small valleys, and elevation variation across the world
        val primaryHills = sin(x * 0.042f) * cos(z * 0.042f) * 3.8f
        val secondaryRoll = sin(x * 0.085f + 1.2f) * sin(z * 0.078f + 0.8f) * 1.65f
        val valleyDip = -kotlin.math.exp(-((x + 38f) * (x + 38f) + (z - 34f) * (z - 34f)) / 900f) * 1.85f
        val northElevation = if (z < -16f) (-z - 16f) * 0.038f else 0f
        val naturalHills = (primaryHills + secondaryRoll + valleyDip + northElevation) * smoothBlend

        // 3. Mountain amphitheater boundary rising far out at dist > 140m
        val rimRise = ((dist - 140f).coerceAtLeast(0f) / 24f).let { it * it * 12.0f }

        val totalHeight = (clearingUndulation + clearingEastHill + clearingWestValley + clearingSouthMound + naturalHills) * smoothWalkwayBlend + rimRise
        return totalHeight
    }

    fun generateClearingWorld(): GeneratedWorldData {
        val terrainModel = modelFactory.createTerrainModel(
            worldSize = 360f,
            divisions = 108,
            heightAt = ::computeTerrainHeight
        )
        val terrainInstance = ModelInstance(terrainModel)

        val skyDomeModel = modelFactory.createSkyDomeModel()
        val skyDomeInstance = ModelInstance(skyDomeModel)

        val monumentModel = modelFactory.createCentralMonumentModel()
        val centralMonumentInstance = ModelInstance(monumentModel)
        val centerHeight = computeTerrainHeight(0f, 0f)
        centralMonumentInstance.transform.setToTranslation(0f, centerHeight, 0f)

        val treeCyanModel = modelFactory.createFantasyTreeModel(isCyanVariant = true)
        val treeVioletModel = modelFactory.createFantasyTreeModel(isCyanVariant = false)
        val boulderPlainModel = modelFactory.createBoulderModel(withCrystalCluster = false)
        val boulderCrystalModel = modelFactory.createBoulderModel(withCrystalCluster = true)
        val grassNormalModel = modelFactory.createGrassClusterModel(isGlowingVariant = false)
        val grassGlowingModel = modelFactory.createGrassClusterModel(isGlowingVariant = true)
        val darkGrassPatchModel0 = modelFactory.createDarkGrassPatchModel(variant = 0)
        val darkGrassPatchModel1 = modelFactory.createDarkGrassPatchModel(variant = 1)
        val dirtPatchModel0 = modelFactory.createDirtPatchModel(variant = 0)
        val dirtPatchModel1 = modelFactory.createDirtPatchModel(variant = 1)
        val cyanCrystalModel = modelFactory.createManaCrystalModel(isAmethyst = false)
        val amethystCrystalModel = modelFactory.createManaCrystalModel(isAmethyst = true)

        val staticInstances = mutableListOf<ModelInstance>()
        val floatingCrystals = mutableListOf<FloatingCrystalInstance>()
        val colliders = mutableListOf<ObstacleCollider>()
        val treeInstances = mutableListOf<ModelInstance>()
        val treeColliders = mutableListOf<ObstacleCollider>()
        val boulderInstances = mutableListOf<ModelInstance>()
        val boulderColliders = mutableListOf<ObstacleCollider>()

        // Central monument altar & 4 rune pillar colliders
        colliders.add(ObstacleCollider(0f, 0f, 1.65f, "Ancient Obelisk Core"))
        val pillarAngles = floatArrayOf(45f, 135f, 225f, 315f)
        for (deg in pillarAngles) {
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            colliders.add(
                ObstacleCollider(
                    x = cos(rad) * 2.85f,
                    z = sin(rad) * 2.85f,
                    radius = 0.68f,
                    label = "Monument Pillar"
                )
            )
        }

        // 1. Inner & Outer Rings of Multi-Canopy Fantasy Trees (leaving spawn sightline at Z=+20 open)
        val treeRing1Count = 12
        for (i in 0 until treeRing1Count) {
            val angleDeg = i * (360f / treeRing1Count) + 15f
            // Keep southern entrance corridor (around 90 deg -> +Z) wider so player has a clear view
            if (angleDeg in 76f..104f) continue
            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val radius = 21f + (i % 3) * 3.5f
            val tx = cos(rad) * radius
            val tz = sin(rad) * radius
            val ty = computeTerrainHeight(tx, tz)
            val scale = 0.92f + (i % 4) * 0.14f
            val isCyan = (i % 2 == 0)

            val instance = ModelInstance(if (isCyan) treeCyanModel else treeVioletModel)
            instance.transform
                .setToTranslation(tx, ty - 0.15f, tz)
                .rotate(0f, 1f, 0f, (i * 67f) % 360f)
                .scale(scale, scale, scale)
            staticInstances.add(instance)
            val col = ObstacleCollider(tx, tz, 0.85f * scale, "Fantasy Tree")
            colliders.add(col)
            treeInstances.add(instance)
            treeColliders.add(col)
        }

        val treeRing2Count = 20
        for (i in 0 until treeRing2Count) {
            val angleDeg = i * (360f / treeRing2Count) + 7f
            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val radius = 37f + (i % 4) * 4.8f
            val tx = cos(rad) * radius
            val tz = sin(rad) * radius
            val ty = computeTerrainHeight(tx, tz)
            val scale = 1.05f + (i % 3) * 0.22f
            val isCyan = (i % 3 != 0)

            val instance = ModelInstance(if (isCyan) treeCyanModel else treeVioletModel)
            instance.transform
                .setToTranslation(tx, ty - 0.20f, tz)
                .rotate(0f, 1f, 0f, (i * 113f) % 360f)
                .scale(scale, scale, scale)
            staticInstances.add(instance)
            val col = ObstacleCollider(tx, tz, 0.90f * scale, "Ancient Forest Tree")
            colliders.add(col)
            treeInstances.add(instance)
            treeColliders.add(col)
        }

        // 2. Expanded Wilderness Trees across Outer Biomes (West Grove, North Highlands, East Woodlands)
        val wildernessTreeCount = 42
        for (i in 0 until wildernessTreeCount) {
            val goldenAngle = i * 137.508f + 19f
            val rad = Math.toRadians(goldenAngle.toDouble()).toFloat()
            val radius = 52f + (i.toFloat() / wildernessTreeCount) * 82f
            val tx = cos(rad) * radius
            val tz = sin(rad) * radius
            val ty = computeTerrainHeight(tx, tz)
            val scale = 0.95f + (i % 4) * 0.22f
            val isCyan = (i % 2 == 0)

            val instance = ModelInstance(if (isCyan) treeCyanModel else treeVioletModel)
            instance.transform
                .setToTranslation(tx, ty - 0.22f, tz)
                .rotate(0f, 1f, 0f, (i * 79f) % 360f)
                .scale(scale, scale, scale)
            staticInstances.add(instance)
            val col = ObstacleCollider(tx, tz, 0.92f * scale, "Wilderness Tree")
            colliders.add(col)
            treeInstances.add(instance)
            treeColliders.add(col)
        }

        // 3. Mossy Boulders & Crystal Rock Outcrops
        val boulderCount = 20
        for (i in 0 until boulderCount) {
            val angleDeg = i * (360f / boulderCount) + 23f
            if (angleDeg in 82f..98f) continue // Keep spawn corridor clear
            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val radius = if (i % 2 == 0) 15.5f + (i % 3) * 2.5f else 30f + (i % 4) * 3.5f
            val bx = cos(rad) * radius
            val bz = sin(rad) * radius
            val by = computeTerrainHeight(bx, bz)
            val scale = 0.75f + (i % 3) * 0.25f
            val withCrystals = (i % 2 == 0)

            val instance = ModelInstance(if (withCrystals) boulderCrystalModel else boulderPlainModel)
            instance.transform
                .setToTranslation(bx, by - 0.1f, bz)
                .rotate(0f, 1f, 0f, (i * 83f) % 360f)
                .scale(scale, scale, scale)
            staticInstances.add(instance)
            val col = ObstacleCollider(bx, bz, 1.05f * scale, "Mossy Boulder")
            colliders.add(col)
            boulderInstances.add(instance)
            boulderColliders.add(col)
        }

        // 4. Expanded Wilderness Boulders & Mineral Outcrops across the landscape
        val wildernessBoulderCount = 32
        for (i in 0 until wildernessBoulderCount) {
            val goldenAngle = i * 137.508f + 43f
            val rad = Math.toRadians(goldenAngle.toDouble()).toFloat()
            val radius = 48f + (i.toFloat() / wildernessBoulderCount) * 85f
            val bx = cos(rad) * radius
            val bz = sin(rad) * radius
            val by = computeTerrainHeight(bx, bz)
            val scale = 0.85f + (i % 3) * 0.35f
            val withCrystals = (i % 3 != 1)

            val instance = ModelInstance(if (withCrystals) boulderCrystalModel else boulderPlainModel)
            instance.transform
                .setToTranslation(bx, by - 0.12f, bz)
                .rotate(0f, 1f, 0f, (i * 97f) % 360f)
                .scale(scale, scale, scale)
            staticInstances.add(instance)
            val col = ObstacleCollider(bx, bz, 1.15f * scale, "Wilderness Boulder")
            colliders.add(col)
            boulderInstances.add(instance)
            boulderColliders.add(col)
        }

        // 5. Grass & Bioluminescent Fern Clusters across the entire expanded landscape
        val grassCount = 148
        for (i in 0 until grassCount) {
            val goldenAngle = i * 137.508f
            val rad = Math.toRadians(goldenAngle.toDouble()).toFloat()
            val radius = 5.2f + sqrt(i.toFloat() / grassCount) * 128f
            val gx = cos(rad) * radius
            val gz = sin(rad) * radius
            val gy = computeTerrainHeight(gx, gz)
            val scale = 0.85f + (i % 4) * 0.20f
            val isGlowing = (i % 4 == 0)

            val instance = ModelInstance(if (isGlowing) grassGlowingModel else grassNormalModel)
            instance.transform
                .setToTranslation(gx, gy, gz)
                .rotate(0f, 1f, 0f, (i * 47f) % 360f)
                .scale(scale, scale, scale)
            staticInstances.add(instance)
        }

        // 5b. Dark Green Grass Patches & Small Grass Clusters + Subtle Dirt Patches
        val darkGrassPatches = mutableListOf<ModelInstance>()
        val darkPatchCount = 110
        for (i in 0 until darkPatchCount) {
            val goldenAngle = i * 137.508f + 41f
            val rad = Math.toRadians(goldenAngle.toDouble()).toFloat()
            val radius = if (i < 45) {
                4.8f + (i % 15) * 1.9f
            } else {
                30.0f + ((i - 45).toFloat() / (darkPatchCount - 45)) * 110f
            }
            val px = cos(rad) * radius
            val pz = sin(rad) * radius
            val py = computeTerrainHeight(px, pz)
            val scale = 0.80f + (i % 5) * 0.24f
            val yaw = (i * 67f) % 360f
            val patchModel = if (i % 2 == 0) darkGrassPatchModel0 else darkGrassPatchModel1
            val patch = ModelInstance(patchModel)
            patch.transform
                .setToTranslation(px, py, pz)
                .rotate(0f, 1f, 0f, yaw)
                .scale(scale, 1f, scale)
            staticInstances.add(patch)
            darkGrassPatches.add(patch)
        }

        val dirtPatches = mutableListOf<ModelInstance>()
        val dirtPatchCount = 64
        for (i in 0 until dirtPatchCount) {
            val goldenAngle = i * 137.508f + 83f
            val rad = Math.toRadians(goldenAngle.toDouble()).toFloat()
            val radius = if (i < 26) {
                5.2f + (i % 13) * 2.1f
            } else {
                32.0f + ((i - 26).toFloat() / (dirtPatchCount - 26)) * 105f
            }
            val dx = cos(rad) * radius
            val dz = sin(rad) * radius
            val dy = computeTerrainHeight(dx, dz)
            val scale = 0.75f + (i % 4) * 0.22f
            val yaw = (i * 97f) % 360f
            val dirtModel = if (i % 2 == 0) dirtPatchModel0 else dirtPatchModel1
            val dirt = ModelInstance(dirtModel)
            dirt.transform
                .setToTranslation(dx, dy, dz)
                .rotate(0f, 1f, 0f, yaw)
                .scale(scale, 1f, scale)
            staticInstances.add(dirt)
            dirtPatches.add(dirt)
        }

        // 6. Orbiting Mana Crystals around the Central Ancient Monument
        val orbitCrystalCount = 6
        for (i in 0 until orbitCrystalCount) {
            val phase = (i * (Math.PI * 2.0 / orbitCrystalCount)).toFloat()
            val isAmethyst = (i % 2 == 1)
            val inst = ModelInstance(if (isAmethyst) amethystCrystalModel else cyanCrystalModel)
            floatingCrystals.add(
                FloatingCrystalInstance(
                    instance = inst,
                    basePosition = Vector3(0f, centerHeight + 3.4f, 0f),
                    orbitRadius = 4.6f,
                    orbitSpeed = 0.55f,
                    initialPhase = phase,
                    bobAmplitude = 0.38f,
                    bobFrequency = 1.9f,
                    isOrbitingCenter = true
                )
            )
        }

        // 7. Ambient Floating Bioluminescent Mana Crystals scattered around the sanctuary
        val ambientCrystalCount = 18
        for (i in 0 until ambientCrystalCount) {
            val deg = i * (360f / ambientCrystalCount) + 12f
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val dist = 10.5f + (i % 3) * 7.5f
            val cx = cos(rad) * dist
            val cz = sin(rad) * dist
            val cy = computeTerrainHeight(cx, cz) + 1.65f + (i % 3) * 0.55f
            val isAmethyst = (i % 3 == 0)
            val inst = ModelInstance(if (isAmethyst) amethystCrystalModel else cyanCrystalModel)

            floatingCrystals.add(
                FloatingCrystalInstance(
                    instance = inst,
                    basePosition = Vector3(cx, cy, cz),
                    orbitRadius = 0.25f,
                    orbitSpeed = 0.8f,
                    initialPhase = i * 0.9f,
                    bobAmplitude = 0.28f,
                    bobFrequency = 1.5f + (i % 3) * 0.3f,
                    isOrbitingCenter = false
                )
            )
        }

        return GeneratedWorldData(
            terrainInstance = terrainInstance,
            skyDomeInstance = skyDomeInstance,
            centralMonumentInstance = centralMonumentInstance,
            staticEnvironmentInstances = staticInstances,
            floatingCrystals = floatingCrystals,
            obstacleColliders = colliders,
            treeInstances = treeInstances,
            treeColliders = treeColliders,
            boulderInstances = boulderInstances,
            boulderColliders = boulderColliders,
            darkGrassPatchInstances = darkGrassPatches,
            dirtPatchInstances = dirtPatches
        )
    }
}
