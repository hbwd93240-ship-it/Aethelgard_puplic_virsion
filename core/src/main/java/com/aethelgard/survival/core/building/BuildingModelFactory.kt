package com.aethelgard.survival.core.building

import com.aethelgard.survival.core.assets.GameAssetManager
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.ConeShapeBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder
import com.badlogic.gdx.math.Matrix4

/**
 * Generates and caches procedural 3D models for all modular building pieces, furniture items,
 * territory markers, and green/red translucent placement ghost previews.
 * Models are cached by piece ID so zero allocations or mesh rebuilds occur during the frame loop.
 */
class BuildingModelFactory(private val assets: GameAssetManager) {

    private val attrs: Long = (Usage.Position or Usage.Normal or Usage.TextureCoordinates).toLong()

    private val solidModelCache = mutableMapOf<String, Model>()
    private val validGhostModelCache = mutableMapOf<String, Model>()
    private val invalidGhostModelCache = mutableMapOf<String, Model>()

    val woodTimberMaterial = Material(
        ColorAttribute.createDiffuse(Color(0.56f, 0.36f, 0.16f, 1f)),
        ColorAttribute.createSpecular(0.12f, 0.10f, 0.08f, 1f)
    )
    val darkWoodTrimMaterial = Material(
        ColorAttribute.createDiffuse(Color(0.36f, 0.22f, 0.10f, 1f))
    )
    val stoneMasonryMaterial = Material(
        ColorAttribute.createDiffuse(Color(0.50f, 0.56f, 0.64f, 1f)),
        ColorAttribute.createSpecular(0.22f, 0.24f, 0.28f, 1f)
    )
    val brickMasonryMaterial = Material(
        ColorAttribute.createDiffuse(Color(0.68f, 0.26f, 0.22f, 1f)),
        ColorAttribute.createSpecular(0.18f, 0.12f, 0.12f, 1f)
    )
    val metalPlateMaterial = Material(
        ColorAttribute.createDiffuse(Color(0.58f, 0.68f, 0.78f, 1f)),
        ColorAttribute.createSpecular(0.65f, 0.72f, 0.85f, 1f)
    )
    val fabricBedMaterial = Material(
        ColorAttribute.createDiffuse(Color(0.18f, 0.48f, 0.78f, 1f))
    )
    val pillowMaterial = Material(
        ColorAttribute.createDiffuse(Color(0.92f, 0.94f, 0.98f, 1f))
    )
    val torchFlameMaterial = Material(
        ColorAttribute.createDiffuse(Color(1.0f, 0.62f, 0.14f, 1f)),
        ColorAttribute.createEmissive(0.95f, 0.48f, 0.08f, 1f)
    )
    val validGhostMaterial = Material(
        ColorAttribute.createDiffuse(Color(0.15f, 0.92f, 0.55f, 0.52f)),
        ColorAttribute.createEmissive(0.10f, 0.65f, 0.38f, 0.52f),
        BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.52f),
        IntAttribute.createCullFace(GL20.GL_NONE)
    )
    val invalidGhostMaterial = Material(
        ColorAttribute.createDiffuse(Color(0.96f, 0.22f, 0.26f, 0.58f)),
        ColorAttribute.createEmissive(0.75f, 0.12f, 0.15f, 0.58f),
        BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.58f),
        IntAttribute.createCullFace(GL20.GL_NONE)
    )

    fun createSolidInstance(def: BuildingPieceDefinition): ModelInstance {
        val model = solidModelCache.getOrPut(def.id) {
            buildPieceModel(def, ghostOverride = null)
        }
        return ModelInstance(model)
    }

    fun createGhostInstance(def: BuildingPieceDefinition, isValid: Boolean): ModelInstance {
        val cache = if (isValid) validGhostModelCache else invalidGhostModelCache
        val mat = if (isValid) validGhostMaterial else invalidGhostMaterial
        val model = cache.getOrPut(def.id) {
            buildPieceModel(def, ghostOverride = mat)
        }
        return ModelInstance(model)
    }

    private fun primaryMaterialFor(def: BuildingPieceDefinition, ghostOverride: Material?): Material {
        if (ghostOverride != null) return ghostOverride
        return when (def.material) {
            BuildingMaterial.WOOD -> woodTimberMaterial
            BuildingMaterial.STONE -> stoneMasonryMaterial
            BuildingMaterial.BRICK -> brickMasonryMaterial
            BuildingMaterial.METAL -> metalPlateMaterial
        }
    }

    private fun trimMaterialFor(def: BuildingPieceDefinition, ghostOverride: Material?): Material {
        if (ghostOverride != null) return ghostOverride
        return when (def.material) {
            BuildingMaterial.WOOD -> darkWoodTrimMaterial
            BuildingMaterial.STONE -> assets.stoneMonumentMaterial
            BuildingMaterial.BRICK -> stoneMasonryMaterial
            BuildingMaterial.METAL -> assets.goldTrimMaterial
        }
    }

    private fun buildBox(part: MeshPartBuilder, m: Matrix4, x: Float, y: Float, z: Float, w: Float, h: Float, d: Float) {
        part.setVertexTransform(m.idt().translate(x, y, z))
        BoxShapeBuilder.build(part, w, h, d)
        part.setVertexTransform(null)
    }

    private fun buildRotatedBox(
        part: MeshPartBuilder,
        m: Matrix4,
        x: Float,
        y: Float,
        z: Float,
        rx: Float,
        ry: Float,
        rz: Float,
        angleDeg: Float,
        w: Float,
        h: Float,
        d: Float
    ) {
        part.setVertexTransform(m.idt().translate(x, y, z).rotate(rx, ry, rz, angleDeg))
        BoxShapeBuilder.build(part, w, h, d)
        part.setVertexTransform(null)
    }

    private fun buildCylinder(part: MeshPartBuilder, m: Matrix4, x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, div: Int = 10) {
        part.setVertexTransform(m.idt().translate(x, y, z))
        CylinderShapeBuilder.build(part, w, h, d, div)
        part.setVertexTransform(null)
    }

    private fun buildCone(part: MeshPartBuilder, m: Matrix4, x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, div: Int = 8) {
        part.setVertexTransform(m.idt().translate(x, y, z))
        ConeShapeBuilder.build(part, w, h, d, div)
        part.setVertexTransform(null)
    }

    private fun buildSphere(part: MeshPartBuilder, m: Matrix4, x: Float, y: Float, z: Float, w: Float, h: Float, d: Float) {
        part.setVertexTransform(m.idt().translate(x, y, z))
        SphereShapeBuilder.build(part, w, h, d, 8, 8)
        part.setVertexTransform(null)
    }

    private fun buildPieceModel(def: BuildingPieceDefinition, ghostOverride: Material?): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()
        val mainMat = primaryMaterialFor(def, ghostOverride)
        val trimMat = trimMaterialFor(def, ghostOverride)

        when (def.category) {
            BuildingCategory.FLOORS -> {
                val mainPart = mb.part("${def.id}_main", GL20.GL_TRIANGLES, attrs, mainMat)
                buildBox(mainPart, m, 0f, def.height * 0.5f, 0f, def.width, def.height, def.depth)

                val trimPart = mb.part("${def.id}_trim", GL20.GL_TRIANGLES, attrs, trimMat)
                // Edge joists & plank grooves
                buildBox(trimPart, m, 0f, def.height * 0.5f, -def.depth * 0.48f, def.width, def.height * 1.08f, 0.12f)
                buildBox(trimPart, m, 0f, def.height * 0.5f, def.depth * 0.48f, def.width, def.height * 1.08f, 0.12f)
                if (def.id == "wood_bridge_floor") {
                    // Low side bridge curbs
                    buildBox(trimPart, m, -def.width * 0.46f, def.height + 0.18f, 0f, 0.14f, 0.36f, def.depth)
                    buildBox(trimPart, m, def.width * 0.46f, def.height + 0.18f, 0f, 0.14f, 0.36f, def.depth)
                }
            }

            BuildingCategory.FOUNDATIONS -> {
                val mainPart = mb.part("${def.id}_base", GL20.GL_TRIANGLES, attrs, mainMat)
                buildBox(mainPart, m, 0f, def.height * 0.5f, 0f, def.width, def.height, def.depth)
                val trimPart = mb.part("${def.id}_posts", GL20.GL_TRIANGLES, attrs, trimMat)
                val cOff = def.width * 0.46f
                buildBox(trimPart, m, -cOff, def.height * 0.5f, -cOff, 0.30f, def.height * 1.06f, 0.30f)
                buildBox(trimPart, m, cOff, def.height * 0.5f, -cOff, 0.30f, def.height * 1.06f, 0.30f)
                buildBox(trimPart, m, -cOff, def.height * 0.5f, cOff, 0.30f, def.height * 1.06f, 0.30f)
                buildBox(trimPart, m, cOff, def.height * 0.5f, cOff, 0.30f, def.height * 1.06f, 0.30f)
            }

            BuildingCategory.WALLS -> {
                val mainPart = mb.part("${def.id}_panel", GL20.GL_TRIANGLES, attrs, mainMat)
                buildBox(mainPart, m, 0f, def.height * 0.5f, 0f, def.width - 0.16f, def.height - 0.12f, def.depth)

                val trimPart = mb.part("${def.id}_frame", GL20.GL_TRIANGLES, attrs, trimMat)
                // Left & right support studs + top & bottom beams
                buildBox(trimPart, m, -def.width * 0.47f, def.height * 0.5f, 0f, 0.18f, def.height, def.depth * 1.18f)
                buildBox(trimPart, m, def.width * 0.47f, def.height * 0.5f, 0f, 0.18f, def.height, def.depth * 1.18f)
                buildBox(trimPart, m, 0f, def.height - 0.08f, 0f, def.width, 0.16f, def.depth * 1.15f)
                buildBox(trimPart, m, 0f, 0.08f, 0f, def.width, 0.16f, def.depth * 1.15f)
                buildBox(trimPart, m, 0f, def.height * 0.5f, 0f, def.width - 0.2f, 0.12f, def.depth * 1.10f)
            }

            BuildingCategory.PILLARS -> {
                val mainPart = mb.part("${def.id}_shaft", GL20.GL_TRIANGLES, attrs, mainMat)
                buildCylinder(mainPart, m, 0f, def.height * 0.5f, 0f, def.width * 0.88f, def.height, def.depth * 0.88f, 10)
                val trimPart = mb.part("${def.id}_caps", GL20.GL_TRIANGLES, attrs, trimMat)
                buildBox(trimPart, m, 0f, 0.10f, 0f, def.width * 1.12f, 0.20f, def.depth * 1.12f)
                buildBox(trimPart, m, 0f, def.height - 0.10f, 0f, def.width * 1.12f, 0.20f, def.depth * 1.12f)
            }

            BuildingCategory.ROOFS -> {
                val mainPart = mb.part("${def.id}_slopes", GL20.GL_TRIANGLES, attrs, mainMat)
                // Two pitched gable roof slopes meeting at a ridge beam
                buildRotatedBox(mainPart, m, -0.78f, 0.62f, 0f, 0f, 0f, 1f, 36f, 1.95f, 0.16f, def.depth)
                buildRotatedBox(mainPart, m, 0.78f, 0.62f, 0f, 0f, 0f, 1f, -36f, 1.95f, 0.16f, def.depth)

                val trimPart = mb.part("${def.id}_ridge", GL20.GL_TRIANGLES, attrs, trimMat)
                buildBox(trimPart, m, 0f, 1.18f, 0f, 0.24f, 0.22f, def.depth * 1.02f)
                buildBox(trimPart, m, 0f, 0.08f, -def.depth * 0.48f, def.width * 0.96f, 0.16f, 0.16f)
                buildBox(trimPart, m, 0f, 0.08f, def.depth * 0.48f, def.width * 0.96f, 0.16f, 0.16f)
            }

            BuildingCategory.DOORS -> {
                val framePart = mb.part("${def.id}_frame", GL20.GL_TRIANGLES, attrs, trimMat)
                if (def.id == "wood_gate") {
                    buildBox(framePart, m, -1.40f, 0.80f, 0f, 0.20f, 1.60f, 0.30f)
                    buildBox(framePart, m, 1.40f, 0.80f, 0f, 0.20f, 1.60f, 0.30f)
                    val leafPart = mb.part("${def.id}_leaf", GL20.GL_TRIANGLES, attrs, mainMat)
                    buildBox(leafPart, m, 0f, 0.75f, 0f, 2.60f, 1.35f, 0.18f)
                } else {
                    // Left & right wall jambs + top lintel header + inner hinged door panel
                    buildBox(framePart, m, -1.10f, 1.50f, 0f, 0.80f, 3.0f, def.depth)
                    buildBox(framePart, m, 1.10f, 1.50f, 0f, 0.80f, 3.0f, def.depth)
                    buildBox(framePart, m, 0f, 2.60f, 0f, 1.40f, 0.80f, def.depth)

                    val doorLeafPart = mb.part("${def.id}_door", GL20.GL_TRIANGLES, attrs, mainMat)
                    buildBox(doorLeafPart, m, 0f, 1.10f, 0f, 1.32f, 2.20f, def.depth * 0.65f)
                }
            }

            BuildingCategory.WINDOWS -> {
                val wallPart = mb.part("${def.id}_wall", GL20.GL_TRIANGLES, attrs, mainMat)
                // Lower wall apron, upper lintel, left & right piers leaving a clear window opening in the center
                buildBox(wallPart, m, 0f, 0.50f, 0f, def.width, 1.00f, def.depth)
                buildBox(wallPart, m, 0f, 2.55f, 0f, def.width, 0.90f, def.depth)
                buildBox(wallPart, m, -1.05f, 1.55f, 0f, 0.90f, 1.10f, def.depth)
                buildBox(wallPart, m, 1.05f, 1.55f, 0f, 0.90f, 1.10f, def.depth)

                val trimPart = mb.part("${def.id}_sill", GL20.GL_TRIANGLES, attrs, trimMat)
                buildBox(trimPart, m, 0f, 1.02f, 0f, 1.30f, 0.10f, def.depth * 1.25f)
                buildBox(trimPart, m, 0f, 2.08f, 0f, 1.30f, 0.10f, def.depth * 1.25f)
                buildBox(trimPart, m, 0f, 1.55f, 0f, 0.08f, 1.05f, def.depth * 0.8f)
            }

            BuildingCategory.STAIRS -> {
                val mainPart = mb.part("${def.id}_steps", GL20.GL_TRIANGLES, attrs, mainMat)
                val stepCount = 6
                val stepDepth = def.depth / stepCount
                val stepHeight = def.height / stepCount
                for (i in 0 until stepCount) {
                    val sy = (i + 0.5f) * stepHeight
                    val sz = (def.depth * 0.5f) - (i + 0.5f) * stepDepth
                    buildBox(mainPart, m, 0f, sy * 0.5f, sz, def.width - 0.16f, sy, stepDepth * 0.96f)
                }
                val railPart = mb.part("${def.id}_rails", GL20.GL_TRIANGLES, attrs, trimMat)
                buildRotatedBox(railPart, m, -def.width * 0.46f, def.height * 0.55f, 0f, 1f, 0f, 0f, 28f, 0.14f, 0.22f, def.depth * 1.10f)
                buildRotatedBox(railPart, m, def.width * 0.46f, def.height * 0.55f, 0f, 1f, 0f, 0f, 28f, 0.14f, 0.22f, def.depth * 1.10f)
            }

            BuildingCategory.FENCES -> {
                val mainPart = mb.part("${def.id}_fence", GL20.GL_TRIANGLES, attrs, mainMat)
                val trimPart = mb.part("${def.id}_posts", GL20.GL_TRIANGLES, attrs, trimMat)
                if (def.id == "spiked_palisade") {
                    for (i in -3..3) {
                        val px = i * 0.40f
                        buildCylinder(mainPart, m, px, 0.65f, 0f, 0.24f, 1.30f, 0.24f, 8)
                        buildCone(trimPart, m, px, 1.42f, 0f, 0.24f, 0.32f, 0.24f, 8)
                    }
                } else {
                    buildBox(trimPart, m, -1.38f, def.height * 0.5f, 0f, 0.22f, def.height, def.depth * 1.1f)
                    buildBox(trimPart, m, 0f, def.height * 0.5f, 0f, 0.20f, def.height, def.depth * 1.1f)
                    buildBox(trimPart, m, 1.38f, def.height * 0.5f, 0f, 0.22f, def.height, def.depth * 1.1f)
                    buildBox(mainPart, m, 0f, def.height * 0.78f, 0f, def.width, 0.18f, def.depth * 0.8f)
                    buildBox(mainPart, m, 0f, def.height * 0.38f, 0f, def.width, 0.18f, def.depth * 0.8f)
                }
            }

            BuildingCategory.FURNITURE -> {
                buildFurnitureGeometry(mb, m, def, mainMat, trimMat, ghostOverride)
            }

            BuildingCategory.TERRITORY -> {
                val basePart = mb.part("${def.id}_obelisk", GL20.GL_TRIANGLES, attrs, mainMat)
                buildCylinder(basePart, m, 0f, 0.25f, 0f, 0.95f, 0.50f, 0.95f, 8)
                buildCylinder(basePart, m, 0f, 1.05f, 0f, 0.52f, 1.20f, 0.52f, 8)
                val crystalMat = ghostOverride ?: assets.manaCrystalMaterial
                val crystalPart = mb.part("${def.id}_crystal", GL20.GL_TRIANGLES, attrs, crystalMat)
                buildCone(crystalPart, m, 0f, 1.95f, 0f, 0.46f, 0.65f, 0.46f, 8)
            }
        }

        return assets.registerModel(mb.end())
    }

    private fun buildFurnitureGeometry(
        mb: ModelBuilder,
        m: Matrix4,
        def: BuildingPieceDefinition,
        mainMat: Material,
        trimMat: Material,
        ghostOverride: Material?
    ) {
        when (def.id) {
            "furniture_bed" -> {
                val framePart = mb.part("bed_frame", GL20.GL_TRIANGLES, attrs, mainMat)
                buildBox(framePart, m, 0f, 0.22f, 0f, 1.38f, 0.24f, 2.06f)
                buildBox(framePart, m, 0f, 0.52f, -0.96f, 1.38f, 0.65f, 0.14f)
                val sheetMat = ghostOverride ?: fabricBedMaterial
                val sheetPart = mb.part("bed_sheet", GL20.GL_TRIANGLES, attrs, sheetMat)
                buildBox(sheetPart, m, 0f, 0.38f, 0.06f, 1.28f, 0.16f, 1.82f)
                val pilMat = ghostOverride ?: pillowMaterial
                val pilPart = mb.part("bed_pillow", GL20.GL_TRIANGLES, attrs, pilMat)
                buildBox(pilPart, m, 0f, 0.48f, -0.66f, 0.92f, 0.12f, 0.36f)
            }

            "furniture_chest" -> {
                val boxPart = mb.part("chest_body", GL20.GL_TRIANGLES, attrs, mainMat)
                buildBox(boxPart, m, 0f, 0.38f, 0f, 1.10f, 0.72f, 0.74f)
                val bandMat = ghostOverride ?: assets.goldTrimMaterial
                val bandPart = mb.part("chest_bands", GL20.GL_TRIANGLES, attrs, bandMat)
                buildBox(bandPart, m, -0.36f, 0.39f, 0f, 0.08f, 0.75f, 0.77f)
                buildBox(bandPart, m, 0.36f, 0.39f, 0f, 0.08f, 0.75f, 0.77f)
                buildBox(bandPart, m, 0f, 0.48f, 0.38f, 0.16f, 0.20f, 0.06f)
            }

            "furniture_crafting_table" -> {
                val woodPart = mb.part("wb_wood", GL20.GL_TRIANGLES, attrs, mainMat)
                buildBox(woodPart, m, 0f, 0.82f, 0f, 1.40f, 0.16f, 0.92f)
                buildBox(woodPart, m, -0.55f, 0.40f, -0.35f, 0.14f, 0.78f, 0.14f)
                buildBox(woodPart, m, 0.55f, 0.40f, -0.35f, 0.14f, 0.78f, 0.14f)
                buildBox(woodPart, m, -0.55f, 0.40f, 0.35f, 0.14f, 0.78f, 0.14f)
                buildBox(woodPart, m, 0.55f, 0.40f, 0.35f, 0.14f, 0.78f, 0.14f)
                val runeMat = ghostOverride ?: assets.glowingRuneMaterial
                val runePart = mb.part("wb_rune", GL20.GL_TRIANGLES, attrs, runeMat)
                buildBox(runePart, m, 0f, 0.91f, 0f, 0.32f, 0.03f, 0.32f)
            }

            "furniture_chair" -> {
                val part = mb.part("chair_wood", GL20.GL_TRIANGLES, attrs, mainMat)
                buildBox(part, m, 0f, 0.44f, 0f, 0.64f, 0.08f, 0.64f)
                buildBox(part, m, 0f, 0.74f, -0.28f, 0.64f, 0.54f, 0.08f)
                buildBox(part, m, -0.26f, 0.22f, -0.26f, 0.08f, 0.44f, 0.08f)
                buildBox(part, m, 0.26f, 0.22f, -0.26f, 0.08f, 0.44f, 0.08f)
                buildBox(part, m, -0.26f, 0.22f, 0.26f, 0.08f, 0.44f, 0.08f)
                buildBox(part, m, 0.26f, 0.22f, 0.26f, 0.08f, 0.44f, 0.08f)
            }

            "furniture_table" -> {
                val part = mb.part("table_wood", GL20.GL_TRIANGLES, attrs, mainMat)
                buildBox(part, m, 0f, 0.78f, 0f, 1.56f, 0.12f, 1.16f)
                buildBox(part, m, -0.64f, 0.38f, -0.44f, 0.12f, 0.76f, 0.12f)
                buildBox(part, m, 0.64f, 0.38f, -0.44f, 0.12f, 0.76f, 0.12f)
                buildBox(part, m, -0.64f, 0.38f, 0.44f, 0.12f, 0.76f, 0.12f)
                buildBox(part, m, 0.64f, 0.38f, 0.44f, 0.12f, 0.76f, 0.12f)
            }

            "furniture_torch" -> {
                val standPart = mb.part("torch_stand", GL20.GL_TRIANGLES, attrs, mainMat)
                buildCylinder(standPart, m, 0f, 0.58f, 0f, 0.14f, 1.15f, 0.14f, 8)
                val flameMat = ghostOverride ?: torchFlameMaterial
                val flamePart = mb.part("torch_flame", GL20.GL_TRIANGLES, attrs, flameMat)
                buildCone(flamePart, m, 0f, 1.28f, 0f, 0.28f, 0.36f, 0.28f, 8)
            }

            "furniture_lantern" -> {
                val framePart = mb.part("lantern_frame", GL20.GL_TRIANGLES, attrs, trimMat)
                buildCylinder(framePart, m, 0f, 0.10f, 0f, 0.38f, 0.16f, 0.38f, 8)
                buildCylinder(framePart, m, 0f, 0.78f, 0f, 0.42f, 0.16f, 0.42f, 8)
                val coreMat = ghostOverride ?: assets.glowingRuneMaterial
                val corePart = mb.part("lantern_core", GL20.GL_TRIANGLES, attrs, coreMat)
                buildSphere(corePart, m, 0f, 0.44f, 0f, 0.32f, 0.48f, 0.32f)
            }

            "furniture_shelf" -> {
                val part = mb.part("shelf_wood", GL20.GL_TRIANGLES, attrs, mainMat)
                buildBox(part, m, -0.56f, 0.75f, 0f, 0.10f, 1.50f, 0.50f)
                buildBox(part, m, 0.56f, 0.75f, 0f, 0.10f, 1.50f, 0.50f)
                buildBox(part, m, 0f, 0.35f, 0f, 1.20f, 0.08f, 0.48f)
                buildBox(part, m, 0f, 0.85f, 0f, 1.20f, 0.08f, 0.48f)
                buildBox(part, m, 0f, 1.35f, 0f, 1.20f, 0.08f, 0.48f)
            }

            else -> {
                // Banner / decorative piece
                val polePart = mb.part("banner_pole", GL20.GL_TRIANGLES, attrs, trimMat)
                buildCylinder(polePart, m, 0f, 0.95f, 0f, 0.09f, 1.90f, 0.09f, 8)
                buildBox(polePart, m, 0f, 1.78f, 0f, 0.84f, 0.08f, 0.08f)
                val clothMat = ghostOverride ?: fabricBedMaterial
                val clothPart = mb.part("banner_cloth", GL20.GL_TRIANGLES, attrs, clothMat)
                buildBox(clothPart, m, 0f, 1.15f, 0.05f, 0.72f, 1.18f, 0.04f)
            }
        }
    }

    fun dispose() {
        solidModelCache.clear()
        validGhostModelCache.clear()
        invalidGhostModelCache.clear()
    }
}
