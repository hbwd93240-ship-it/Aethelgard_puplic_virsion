package com.aethelgard.survival.core.world

import com.aethelgard.survival.core.assets.GameAssetManager
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.ConeShapeBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder
import com.badlogic.gdx.math.Matrix4
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates all 3D models procedurally in code for AethelgardSurvival:
 * - Articulated Anime Fantasy Spellsword character with hierarchical limb nodes & equippable Rune Blade
 * - Articulated 3D Corrupted Mana Guardian Knight enemy
 * - Energy Blast spheres, Arcane Magic Bolts, Melee Slash Arcs, and Hit Spark particles
 * - Sculpted heightfield terrain, Central Monument, Fantasy Trees, Boulders, Grass, Crystals & Sky Dome
 */
class ProceduralModelFactory(val assets: GameAssetManager) {

    private val attrs: Long = (Usage.Position or Usage.Normal or Usage.TextureCoordinates).toLong()

    private fun buildCylinder(
        part: MeshPartBuilder,
        transform: Matrix4,
        width: Float,
        height: Float,
        depth: Float,
        divisions: Int
    ) {
        part.setVertexTransform(transform)
        CylinderShapeBuilder.build(part, width, height, depth, divisions)
        part.setVertexTransform(null)
    }

    private fun buildCone(
        part: MeshPartBuilder,
        transform: Matrix4,
        width: Float,
        height: Float,
        depth: Float,
        divisions: Int
    ) {
        part.setVertexTransform(transform)
        ConeShapeBuilder.build(part, width, height, depth, divisions)
        part.setVertexTransform(null)
    }

    private fun buildSphere(
        part: MeshPartBuilder,
        transform: Matrix4,
        width: Float,
        height: Float,
        depth: Float,
        divisionsU: Int,
        divisionsV: Int
    ) {
        part.setVertexTransform(transform)
        SphereShapeBuilder.build(part, width, height, depth, divisionsU, divisionsV)
        part.setVertexTransform(null)
    }

    private fun buildBox(
        part: MeshPartBuilder,
        transform: Matrix4,
        width: Float,
        height: Float,
        depth: Float
    ) {
        part.setVertexTransform(transform)
        BoxShapeBuilder.build(part, width, height, depth)
        part.setVertexTransform(null)
    }

    /**
     * Builds the articulated 3D Anime Fantasy protagonist (Silver-haired spellsword in dark azure cloak).
     * Includes both the hip scabbard (`sheathedSword` + `sheathedHilt`) and the right-hand `drawnSword`
     * node so the character can switch seamlessly between unarmed martial arts and sword combat.
     */
    fun createAnimePlayerModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        // Node 1: Pelvis (Root)
        val pelvisNode: Node = mb.node()
        pelvisNode.id = "pelvis"
        pelvisNode.translation.set(0f, 0.90f, 0f)
        run {
            val tunicPart = mb.part("pelvis_core", GL20.GL_TRIANGLES, attrs, assets.tunicMaterial)
            BoxShapeBuilder.build(tunicPart, 0f, 0f, 0f, 0.27f, 0.15f, 0.18f)

            val cloakPart = mb.part("hip_tassets", GL20.GL_TRIANGLES, attrs, assets.cloakMaterial)
            buildBox(
                cloakPart,
                m.idt().translate(-0.14f, -0.10f, 0f).rotate(0f, 0f, 1f, 11f),
                0.055f, 0.25f, 0.20f
            )
            buildBox(
                cloakPart,
                m.idt().translate(0.14f, -0.10f, 0f).rotate(0f, 0f, 1f, -11f),
                0.055f, 0.25f, 0.20f
            )
            buildBox(
                cloakPart,
                m.idt().translate(0f, -0.11f, -0.095f).rotate(1f, 0f, 0f, -9f),
                0.27f, 0.24f, 0.045f
            )

            val beltPart = mb.part("belt_gold", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
            BoxShapeBuilder.build(beltPart, 0f, 0.05f, 0f, 0.29f, 0.042f, 0.20f)
            BoxShapeBuilder.build(beltPart, 0f, 0.05f, 0.102f, 0.068f, 0.062f, 0.02f)
        }

        // Node 2: Sheathed Scabbard on Hip (always visible)
        val swordNode: Node = mb.node()
        swordNode.id = "sheathedSword"
        swordNode.translation.set(0.18f, -0.01f, -0.02f)
        run {
            val scabbardPart = mb.part("scabbard", GL20.GL_TRIANGLES, attrs, assets.cloakMaterial)
            buildBox(
                scabbardPart,
                m.idt().rotate(1f, 0f, 0f, -40f).rotate(0f, 0f, 1f, -8f).translate(0f, -0.18f, -0.12f),
                0.032f, 0.70f, 0.06f
            )
        }

        // Node 2B: Sheathed Hilt inside Scabbard (hidden when sword is drawn into right hand)
        val sheathedHiltNode: Node = mb.node()
        sheathedHiltNode.id = "sheathedHilt"
        sheathedHiltNode.translation.set(0f, 0f, 0f)
        run {
            val goldPart = mb.part("sword_gold", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
            buildBox(
                goldPart,
                m.idt().rotate(1f, 0f, 0f, -40f).rotate(0f, 0f, 1f, -8f).translate(0f, 0.18f, -0.12f),
                0.048f, 0.032f, 0.17f
            )
            buildSphere(
                goldPart,
                m.idt().rotate(1f, 0f, 0f, -40f).rotate(0f, 0f, 1f, -8f).translate(0f, 0.35f, -0.12f),
                0.052f, 0.052f, 0.052f, 8, 8
            )

            val gripPart = mb.part("sword_grip", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
            buildCylinder(
                gripPart,
                m.idt().rotate(1f, 0f, 0f, -40f).rotate(0f, 0f, 1f, -8f).translate(0f, 0.26f, -0.12f),
                0.034f, 0.15f, 0.034f, 8
            )

            val gemPart = mb.part("sword_gem", GL20.GL_TRIANGLES, attrs, assets.glowingRuneMaterial)
            buildSphere(
                gemPart,
                m.idt().rotate(1f, 0f, 0f, -40f).rotate(0f, 0f, 1f, -8f).translate(0f, 0.18f, -0.12f),
                0.056f, 0.042f, 0.042f, 8, 8
            )
        }

        // Node 3: Torso (child of pelvis)
        val torsoNode: Node = mb.node()
        torsoNode.id = "torso"
        torsoNode.translation.set(0f, 0.08f, 0f)
        run {
            val tunicPart = mb.part("torso_tunic", GL20.GL_TRIANGLES, attrs, assets.tunicMaterial)
            BoxShapeBuilder.build(tunicPart, 0f, 0.16f, 0f, 0.29f, 0.22f, 0.18f)
            BoxShapeBuilder.build(tunicPart, 0f, 0.31f, 0f, 0.33f, 0.18f, 0.20f)

            val mantlePart = mb.part("torso_mantle", GL20.GL_TRIANGLES, attrs, assets.cloakMaterial)
            BoxShapeBuilder.build(mantlePart, 0f, 0.36f, 0f, 0.37f, 0.11f, 0.22f)
            BoxShapeBuilder.build(mantlePart, 0f, 0.44f, -0.07f, 0.24f, 0.10f, 0.04f)
            BoxShapeBuilder.build(mantlePart, -0.11f, 0.43f, -0.01f, 0.04f, 0.09f, 0.14f)
            BoxShapeBuilder.build(mantlePart, 0.11f, 0.43f, -0.01f, 0.04f, 0.09f, 0.14f)

            val silverPart = mb.part("torso_silver", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
            BoxShapeBuilder.build(silverPart, 0f, 0.29f, 0.102f, 0.22f, 0.14f, 0.02f)
            buildSphere(silverPart, m.idt().translate(-0.14f, 0.38f, 0.09f), 0.055f, 0.055f, 0.04f, 8, 8)
            buildSphere(silverPart, m.idt().translate(0.14f, 0.38f, 0.09f), 0.055f, 0.055f, 0.04f, 8, 8)

            val corePart = mb.part("torso_mana_core", GL20.GL_TRIANGLES, attrs, assets.glowingRuneMaterial)
            buildBox(
                corePart,
                m.idt().translate(0f, 0.30f, 0.115f).rotate(0f, 0f, 1f, 45f),
                0.06f, 0.06f, 0.025f
            )
        }

        // Node 4: Flowing Back Cloak (child of torso)
        val cloakBackNode: Node = mb.node()
        cloakBackNode.id = "cloakBack"
        cloakBackNode.translation.set(0f, 0.38f, -0.11f)
        run {
            val capePart = mb.part("cape_main", GL20.GL_TRIANGLES, attrs, assets.cloakMaterial)
            buildBox(
                capePart,
                m.idt().translate(0f, -0.22f, -0.03f).rotate(1f, 0f, 0f, 8f),
                0.38f, 0.44f, 0.025f
            )
            buildBox(
                capePart,
                m.idt().translate(0f, -0.58f, -0.08f).rotate(1f, 0f, 0f, 13f),
                0.46f, 0.38f, 0.022f
            )
            buildBox(
                capePart,
                m.idt().translate(-0.18f, -0.44f, -0.06f).rotate(1f, 0f, 0f, 11f).rotate(0f, 0f, 1f, 8f),
                0.14f, 0.56f, 0.02f
            )
            buildBox(
                capePart,
                m.idt().translate(0.18f, -0.44f, -0.06f).rotate(1f, 0f, 0f, 11f).rotate(0f, 0f, 1f, -8f),
                0.14f, 0.56f, 0.02f
            )

            val goldEdge = mb.part("cape_emblem", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
            buildBox(
                goldEdge,
                m.idt().translate(0f, -0.34f, -0.055f).rotate(1f, 0f, 0f, 10f).rotate(0f, 0f, 1f, 45f),
                0.11f, 0.11f, 0.015f
            )
        }

        // Node 5: Head & Anime Face + Layered Silver Hair
        val headNode: Node = mb.node()
        headNode.id = "head"
        headNode.translation.set(0f, 0.43f, 0f)
        run {
            val skinPart = mb.part("head_skin", GL20.GL_TRIANGLES, attrs, assets.skinMaterial)
            buildCylinder(skinPart, m.idt().translate(0f, 0.04f, 0f), 0.095f, 0.10f, 0.095f, 10)
            buildSphere(skinPart, m.idt().translate(0f, 0.18f, 0.01f), 0.24f, 0.25f, 0.24f, 14, 14)
            buildCone(
                skinPart,
                m.idt().translate(0f, 0.11f, 0.03f).rotate(1f, 0f, 0f, 180f),
                0.19f, 0.12f, 0.18f, 12
            )

            val lashPart = mb.part("head_lashes", GL20.GL_TRIANGLES, attrs, assets.eyeDarkMaterial)
            BoxShapeBuilder.build(lashPart, -0.052f, 0.185f, 0.122f, 0.046f, 0.014f, 0.015f)
            BoxShapeBuilder.build(lashPart, 0.052f, 0.185f, 0.122f, 0.046f, 0.014f, 0.015f)
            BoxShapeBuilder.build(lashPart, -0.050f, 0.165f, 0.125f, 0.018f, 0.024f, 0.014f)
            BoxShapeBuilder.build(lashPart, 0.050f, 0.165f, 0.125f, 0.018f, 0.024f, 0.014f)

            val irisPart = mb.part("head_iris", GL20.GL_TRIANGLES, attrs, assets.eyeIrisMaterial)
            BoxShapeBuilder.build(irisPart, -0.050f, 0.165f, 0.121f, 0.036f, 0.034f, 0.014f)
            BoxShapeBuilder.build(irisPart, 0.050f, 0.165f, 0.121f, 0.036f, 0.034f, 0.014f)

            val specPart = mb.part("head_eye_spec", GL20.GL_TRIANGLES, attrs, assets.eyeHighlightMaterial)
            BoxShapeBuilder.build(specPart, -0.058f, 0.175f, 0.128f, 0.011f, 0.011f, 0.012f)
            BoxShapeBuilder.build(specPart, 0.042f, 0.175f, 0.128f, 0.011f, 0.011f, 0.012f)

            val hairPart = mb.part("head_hair_front", GL20.GL_TRIANGLES, attrs, assets.hairMaterial)
            buildSphere(hairPart, m.idt().translate(0f, 0.21f, -0.005f), 0.265f, 0.24f, 0.265f, 14, 12)

            val bangAngles = arrayOf(
                floatArrayOf(0f, 0.21f, 0.125f, 15f, 0f, 0.055f, 0.13f),
                floatArrayOf(-0.05f, 0.21f, 0.118f, 14f, 14f, 0.055f, 0.14f),
                floatArrayOf(0.05f, 0.21f, 0.118f, 14f, -14f, 0.055f, 0.14f),
                floatArrayOf(-0.105f, 0.18f, 0.085f, 8f, 18f, 0.055f, 0.19f),
                floatArrayOf(0.105f, 0.18f, 0.085f, 8f, -18f, 0.055f, 0.19f)
            )
            for (b in bangAngles) {
                m.idt().translate(b[0], b[1], b[2])
                    .rotate(1f, 0f, 0f, 180f + b[3])
                    .rotate(0f, 0f, 1f, b[4])
                buildCone(hairPart, m, b[5], b[6], b[5], 8)
            }
            buildCone(
                hairPart,
                m.idt().translate(0f, 0.34f, -0.02f).rotate(1f, 0f, 0f, -25f),
                0.06f, 0.12f, 0.06f, 8
            )
            buildCone(
                hairPart,
                m.idt().translate(-0.07f, 0.31f, -0.04f).rotate(1f, 0f, 0f, -35f).rotate(0f, 0f, 1f, 22f),
                0.07f, 0.13f, 0.06f, 8
            )
            buildCone(
                hairPart,
                m.idt().translate(0.07f, 0.31f, -0.04f).rotate(1f, 0f, 0f, -35f).rotate(0f, 0f, 1f, -22f),
                0.07f, 0.13f, 0.06f, 8
            )
        }

        // Node 6: Articulated Back Hair Spikes
        val hairBackNode: Node = mb.node()
        hairBackNode.id = "hairBack"
        hairBackNode.translation.set(0f, 0.18f, -0.10f)
        run {
            val hairBackPart = mb.part("hair_back_spikes", GL20.GL_TRIANGLES, attrs, assets.hairMaterial)
            val backSpikes = arrayOf(
                floatArrayOf(0f, -0.06f, -0.04f, -148f, 0f, 0.09f, 0.22f),
                floatArrayOf(-0.07f, -0.05f, -0.03f, -150f, 18f, 0.08f, 0.21f),
                floatArrayOf(0.07f, -0.05f, -0.03f, -150f, -18f, 0.08f, 0.21f),
                floatArrayOf(-0.11f, 0.02f, -0.01f, -135f, 32f, 0.07f, 0.18f),
                floatArrayOf(0.11f, 0.02f, -0.01f, -135f, -32f, 0.07f, 0.18f)
            )
            for (s in backSpikes) {
                m.idt().translate(s[0], s[1], s[2])
                    .rotate(1f, 0f, 0f, s[3])
                    .rotate(0f, 0f, 1f, s[4])
                buildCone(hairBackPart, m, s[5], s[6], s[5], 8)
            }
        }

        // Node 7 & 8: Left Upper Arm & Left Forearm
        val leftUpperArmNode: Node = mb.node()
        leftUpperArmNode.id = "leftUpperArm"
        leftUpperArmNode.translation.set(0.22f, 0.35f, 0f)
        run {
            val pauldronPart = mb.part("l_pauldron", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
            buildSphere(pauldronPart, m.idt().translate(0.03f, 0.02f, 0f), 0.13f, 0.11f, 0.14f, 10, 10)

            val sleevePart = mb.part("l_upper_sleeve", GL20.GL_TRIANGLES, attrs, assets.cloakMaterial)
            buildCylinder(sleevePart, m.idt().translate(0.01f, -0.12f, 0f), 0.085f, 0.24f, 0.085f, 10)
        }

        val leftForearmNode: Node = mb.node()
        leftForearmNode.id = "leftForearm"
        leftForearmNode.translation.set(0.01f, -0.24f, 0f)
        run {
            val vambracePart = mb.part("l_vambrace", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
            buildCylinder(vambracePart, m.idt().translate(0f, -0.11f, 0f), 0.076f, 0.21f, 0.076f, 10)

            val handPart = mb.part("l_hand", GL20.GL_TRIANGLES, attrs, assets.skinMaterial)
            buildSphere(handPart, m.idt().translate(0f, -0.24f, 0.01f), 0.078f, 0.088f, 0.078f, 8, 8)
        }

        // Node 9 & 10: Right Upper Arm & Right Forearm + Drawn Rune Spellsword
        val rightUpperArmNode: Node = mb.node()
        rightUpperArmNode.id = "rightUpperArm"
        rightUpperArmNode.translation.set(-0.22f, 0.35f, 0f)
        run {
            val pauldronPart = mb.part("r_pauldron", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
            buildSphere(pauldronPart, m.idt().translate(-0.03f, 0.02f, 0f), 0.13f, 0.11f, 0.14f, 10, 10)

            val sleevePart = mb.part("r_upper_sleeve", GL20.GL_TRIANGLES, attrs, assets.cloakMaterial)
            buildCylinder(sleevePart, m.idt().translate(-0.01f, -0.12f, 0f), 0.085f, 0.24f, 0.085f, 10)
        }

        val rightForearmNode: Node = mb.node()
        rightForearmNode.id = "rightForearm"
        rightForearmNode.translation.set(-0.01f, -0.24f, 0f)
        run {
            val vambracePart = mb.part("r_vambrace", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
            buildCylinder(vambracePart, m.idt().translate(0f, -0.11f, 0f), 0.076f, 0.21f, 0.076f, 10)

            val handPart = mb.part("r_hand", GL20.GL_TRIANGLES, attrs, assets.skinMaterial)
            buildSphere(handPart, m.idt().translate(0f, -0.24f, 0.01f), 0.078f, 0.088f, 0.078f, 8, 8)
        }

        // Node 10B: Drawn Rune Spellsword (child of rightForearm, extends along +Z from hand)
        val drawnSwordNode: Node = mb.node()
        drawnSwordNode.id = "drawnSword"
        drawnSwordNode.translation.set(0f, -0.24f, 0.02f)
        run {
            val gripPart = mb.part("drawn_grip", GL20.GL_TRIANGLES, attrs, assets.tunicMaterial)
            buildCylinder(
                gripPart,
                m.idt().rotate(1f, 0f, 0f, 90f),
                0.032f, 0.18f, 0.032f, 8
            )

            val goldPart = mb.part("drawn_guard_gold", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
            BoxShapeBuilder.build(goldPart, 0f, 0f, 0.09f, 0.19f, 0.036f, 0.035f)
            buildSphere(goldPart, m.idt().translate(0f, 0f, -0.10f), 0.048f, 0.048f, 0.048f, 8, 8)

            val bladePart = mb.part("drawn_blade_silver", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
            BoxShapeBuilder.build(bladePart, 0f, 0f, 0.48f, 0.022f, 0.068f, 0.76f)
            buildCone(
                bladePart,
                m.idt().translate(0f, 0f, 0.94f).rotate(1f, 0f, 0f, 90f),
                0.024f, 0.18f, 0.068f, 6
            )

            val runeFuller = mb.part("drawn_blade_rune", GL20.GL_TRIANGLES, attrs, assets.glowingRuneMaterial)
            BoxShapeBuilder.build(runeFuller, 0f, 0f, 0.44f, 0.028f, 0.022f, 0.64f)
        }

        // Node 10C..10I: Held Survival Gathering Tools (children of rightForearm, extend along +Z from hand)
        val heldStoneAxeNode: Node = mb.node().apply {
            id = "heldStoneAxe"
            translation.set(0f, -0.24f, 0.02f)
            scale.set(0.0001f, 0.0001f, 0.0001f)
        }
        buildToolPartsIntoCurrentNode(mb, m, "stone_axe", "rig_stone_axe")

        val heldIronAxeNode: Node = mb.node().apply {
            id = "heldIronAxe"
            translation.set(0f, -0.24f, 0.02f)
            scale.set(0.0001f, 0.0001f, 0.0001f)
        }
        buildToolPartsIntoCurrentNode(mb, m, "iron_axe", "rig_iron_axe")

        val heldStonePickaxeNode: Node = mb.node().apply {
            id = "heldStonePickaxe"
            translation.set(0f, -0.24f, 0.02f)
            scale.set(0.0001f, 0.0001f, 0.0001f)
        }
        buildToolPartsIntoCurrentNode(mb, m, "stone_pickaxe", "rig_stone_pickaxe")

        val heldIronPickaxeNode: Node = mb.node().apply {
            id = "heldIronPickaxe"
            translation.set(0f, -0.24f, 0.02f)
            scale.set(0.0001f, 0.0001f, 0.0001f)
        }
        buildToolPartsIntoCurrentNode(mb, m, "iron_pickaxe", "rig_iron_pickaxe")

        val heldShovelNode: Node = mb.node().apply {
            id = "heldShovel"
            translation.set(0f, -0.24f, 0.02f)
            scale.set(0.0001f, 0.0001f, 0.0001f)
        }
        buildToolPartsIntoCurrentNode(mb, m, "shovel", "rig_shovel")

        val heldSickleNode: Node = mb.node().apply {
            id = "heldSickle"
            translation.set(0f, -0.24f, 0.02f)
            scale.set(0.0001f, 0.0001f, 0.0001f)
        }
        buildToolPartsIntoCurrentNode(mb, m, "sickle", "rig_sickle")

        val heldKnifeNode: Node = mb.node().apply {
            id = "heldKnife"
            translation.set(0f, -0.24f, 0.02f)
            scale.set(0.0001f, 0.0001f, 0.0001f)
        }
        buildToolPartsIntoCurrentNode(mb, m, "knife", "rig_knife")

        val heldBuildersHammerNode: Node = mb.node().apply {
            id = "heldBuildersHammer"
            translation.set(0f, -0.24f, 0.02f)
            scale.set(0.0001f, 0.0001f, 0.0001f)
        }
        buildToolPartsIntoCurrentNode(mb, m, "builders_hammer", "rig_hammer")

        // Held resource & consumable nodes attached to rightForearm
        val holdableResourceIds = listOf(
            "wood", "hardwood", "branches", "roots",
            "stone", "coal", "copper_ore", "iron_ore", "rare_ore",
            "plants", "mushrooms", "berries", "fibers", "animal_hides",
            "monster_essence", "magic_crystals", "ancient_relics", "boss_materials",
            "green_crystal",
            "roasted_mushrooms", "berry_brew", "herbal_salve", "mana_essence_tonic", "mp_potion", "health_potion"
        )
        val heldResourceNodes = mutableListOf<Node>()
        for (itemId in holdableResourceIds) {
            val itemNode: Node = mb.node().apply {
                id = "heldItem_$itemId"
                translation.set(0f, -0.24f, 0.06f)
                scale.set(0.0001f, 0.0001f, 0.0001f)
            }
            buildToolPartsIntoCurrentNode(mb, m, itemId, "rig_$itemId")
            heldResourceNodes.add(itemNode)
        }

        // Node 11 & 12: Left Thigh & Left Shin/Boot
        val leftThighNode: Node = mb.node()
        leftThighNode.id = "leftThigh"
        leftThighNode.translation.set(0.095f, -0.04f, 0f)
        run {
            val thighPart = mb.part("l_thigh", GL20.GL_TRIANGLES, attrs, assets.tunicMaterial)
            buildCylinder(thighPart, m.idt().translate(0f, -0.20f, 0f), 0.115f, 0.40f, 0.115f, 10)
        }

        val leftShinNode: Node = mb.node()
        leftShinNode.id = "leftShin"
        leftShinNode.translation.set(0f, -0.40f, 0f)
        run {
            val kneePart = mb.part("l_knee", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
            buildSphere(kneePart, m.idt().translate(0f, 0f, 0.055f), 0.08f, 0.09f, 0.05f, 8, 8)

            val bootPart = mb.part("l_boot", GL20.GL_TRIANGLES, attrs, assets.bootMaterial)
            buildCylinder(bootPart, m.idt().translate(0f, -0.21f, 0f), 0.098f, 0.40f, 0.098f, 10)
            BoxShapeBuilder.build(bootPart, 0f, -0.41f, 0.045f, 0.105f, 0.09f, 0.21f)
        }

        // Node 13 & 14: Right Thigh & Right Shin/Boot
        val rightThighNode: Node = mb.node()
        rightThighNode.id = "rightThigh"
        rightThighNode.translation.set(-0.095f, -0.04f, 0f)
        run {
            val thighPart = mb.part("r_thigh", GL20.GL_TRIANGLES, attrs, assets.tunicMaterial)
            buildCylinder(thighPart, m.idt().translate(0f, -0.20f, 0f), 0.115f, 0.40f, 0.115f, 10)
        }

        val rightShinNode: Node = mb.node()
        rightShinNode.id = "rightShin"
        rightShinNode.translation.set(0f, -0.40f, 0f)
        run {
            val kneePart = mb.part("r_knee", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
            buildSphere(kneePart, m.idt().translate(0f, 0f, 0.055f), 0.08f, 0.09f, 0.05f, 8, 8)

            val bootPart = mb.part("r_boot", GL20.GL_TRIANGLES, attrs, assets.bootMaterial)
            buildCylinder(bootPart, m.idt().translate(0f, -0.21f, 0f), 0.098f, 0.40f, 0.098f, 10)
            BoxShapeBuilder.build(bootPart, 0f, -0.41f, 0.045f, 0.105f, 0.09f, 0.21f)
        }

        val model = mb.end()

        // Assemble parent-child skeletal hierarchy
        model.nodes.clear()
        model.nodes.add(pelvisNode)

        pelvisNode.addChild(swordNode)
        swordNode.addChild(sheathedHiltNode)

        pelvisNode.addChild(torsoNode)
        pelvisNode.addChild(leftThighNode)
        pelvisNode.addChild(rightThighNode)

        torsoNode.addChild(cloakBackNode)
        torsoNode.addChild(headNode)
        torsoNode.addChild(leftUpperArmNode)
        torsoNode.addChild(rightUpperArmNode)

        headNode.addChild(hairBackNode)
        leftUpperArmNode.addChild(leftForearmNode)
        rightUpperArmNode.addChild(rightForearmNode)
        rightForearmNode.addChild(drawnSwordNode)
        rightForearmNode.addChild(heldStoneAxeNode)
        rightForearmNode.addChild(heldIronAxeNode)
        rightForearmNode.addChild(heldStonePickaxeNode)
        rightForearmNode.addChild(heldIronPickaxeNode)
        rightForearmNode.addChild(heldShovelNode)
        rightForearmNode.addChild(heldSickleNode)
        rightForearmNode.addChild(heldKnifeNode)
        rightForearmNode.addChild(heldBuildersHammerNode)
        for (itemNode in heldResourceNodes) {
            rightForearmNode.addChild(itemNode)
        }

        leftThighNode.addChild(leftShinNode)
        rightThighNode.addChild(rightShinNode)

        model.calculateTransforms()
        return assets.registerModel(model)
    }

    /**
     * Builds the articulated 3D Corrupted Mana Guardian Knight enemy model.
     * Features obsidian-crimson plate armor, horned dark-knight helm with glowing crimson visor,
     * spiked shoulder pauldrons, glowing corrupted mana chest core, and a massive Runic Cleaver blade.
     */
    fun createCorruptedGuardianEnemyModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val pelvisNode: Node = mb.node()
        pelvisNode.id = "enemyPelvis"
        pelvisNode.translation.set(0f, 1.05f, 0f)
        run {
            val armorPart = mb.part("e_pelvis", GL20.GL_TRIANGLES, attrs, assets.corruptedArmorMaterial)
            BoxShapeBuilder.build(armorPart, 0f, 0f, 0f, 0.38f, 0.20f, 0.26f)

            val tassetPart = mb.part("e_tassets", GL20.GL_TRIANGLES, attrs, assets.corruptedMantleMaterial)
            buildBox(tassetPart, m.idt().translate(-0.20f, -0.14f, 0f).rotate(0f, 0f, 1f, 12f), 0.08f, 0.30f, 0.28f)
            buildBox(tassetPart, m.idt().translate(0.20f, -0.14f, 0f).rotate(0f, 0f, 1f, -12f), 0.08f, 0.30f, 0.28f)
            buildBox(tassetPart, m.idt().translate(0f, -0.15f, -0.13f), 0.38f, 0.32f, 0.06f)
        }

        val torsoNode: Node = mb.node()
        torsoNode.id = "enemyTorso"
        torsoNode.translation.set(0f, 0.10f, 0f)
        run {
            val chestPart = mb.part("e_chest", GL20.GL_TRIANGLES, attrs, assets.corruptedArmorMaterial)
            BoxShapeBuilder.build(chestPart, 0f, 0.22f, 0f, 0.44f, 0.30f, 0.28f)
            BoxShapeBuilder.build(chestPart, 0f, 0.42f, 0f, 0.52f, 0.24f, 0.32f)

            val corePart = mb.part("e_core", GL20.GL_TRIANGLES, attrs, assets.corruptedCrimsonGlowMaterial)
            buildBox(
                corePart,
                m.idt().translate(0f, 0.38f, 0.165f).rotate(0f, 0f, 1f, 45f),
                0.13f, 0.13f, 0.05f
            )
            // Back corrupted crystal spines
            buildCone(
                corePart,
                m.idt().translate(-0.14f, 0.48f, -0.18f).rotate(1f, 0f, 0f, -45f),
                0.09f, 0.28f, 0.09f, 6
            )
            buildCone(
                corePart,
                m.idt().translate(0.14f, 0.48f, -0.18f).rotate(1f, 0f, 0f, -45f),
                0.09f, 0.28f, 0.09f, 6
            )
        }

        val headNode: Node = mb.node()
        headNode.id = "enemyHead"
        headNode.translation.set(0f, 0.58f, 0f)
        run {
            val helmPart = mb.part("e_helm", GL20.GL_TRIANGLES, attrs, assets.corruptedArmorMaterial)
            buildCylinder(helmPart, m.idt().translate(0f, 0.14f, 0f), 0.24f, 0.28f, 0.25f, 10)
            // Horned crown
            buildCone(
                helmPart,
                m.idt().translate(-0.14f, 0.34f, 0.02f).rotate(0f, 0f, 1f, 28f).rotate(1f, 0f, 0f, 18f),
                0.08f, 0.26f, 0.08f, 6
            )
            buildCone(
                helmPart,
                m.idt().translate(0.14f, 0.34f, 0.02f).rotate(0f, 0f, 1f, -28f).rotate(1f, 0f, 0f, 18f),
                0.08f, 0.26f, 0.08f, 6
            )

            val visorPart = mb.part("e_visor", GL20.GL_TRIANGLES, attrs, assets.corruptedCrimsonGlowMaterial)
            BoxShapeBuilder.build(visorPart, 0f, 0.16f, 0.125f, 0.18f, 0.038f, 0.03f)
        }

        val leftArmNode: Node = mb.node()
        leftArmNode.id = "enemyLeftArm"
        leftArmNode.translation.set(0.32f, 0.46f, 0f)
        run {
            val armPart = mb.part("e_l_arm", GL20.GL_TRIANGLES, attrs, assets.corruptedArmorMaterial)
            buildSphere(armPart, m.idt().translate(0.04f, 0.04f, 0f), 0.22f, 0.18f, 0.22f, 8, 8)
            buildCylinder(armPart, m.idt().translate(0.02f, -0.24f, 0f), 0.12f, 0.48f, 0.12f, 8)

            // Shield gauntlet plate on left forearm
            val shieldPart = mb.part("e_l_shield", GL20.GL_TRIANGLES, attrs, assets.corruptedBladeMaterial)
            BoxShapeBuilder.build(shieldPart, 0.09f, -0.28f, 0.04f, 0.04f, 0.38f, 0.24f)
        }

        val rightArmNode: Node = mb.node()
        rightArmNode.id = "enemyRightArm"
        rightArmNode.translation.set(-0.32f, 0.46f, 0f)
        run {
            val armPart = mb.part("e_r_arm", GL20.GL_TRIANGLES, attrs, assets.corruptedArmorMaterial)
            buildSphere(armPart, m.idt().translate(-0.04f, 0.04f, 0f), 0.22f, 0.18f, 0.22f, 8, 8)
            buildCylinder(armPart, m.idt().translate(-0.02f, -0.24f, 0f), 0.12f, 0.48f, 0.12f, 8)

            // Runic Cleaver Greatsword held in right hand extending forward (+Z)
            val cleaverPart = mb.part("e_cleaver", GL20.GL_TRIANGLES, attrs, assets.corruptedBladeMaterial)
            BoxShapeBuilder.build(cleaverPart, -0.02f, -0.46f, 0.56f, 0.04f, 0.16f, 1.05f)

            val edgePart = mb.part("e_cleaver_edge", GL20.GL_TRIANGLES, attrs, assets.corruptedCrimsonGlowMaterial)
            BoxShapeBuilder.build(edgePart, -0.02f, -0.52f, 0.58f, 0.048f, 0.04f, 0.98f)
        }

        val leftLegNode: Node = mb.node()
        leftLegNode.id = "enemyLeftLeg"
        leftLegNode.translation.set(0.13f, -0.05f, 0f)
        run {
            val legPart = mb.part("e_l_leg", GL20.GL_TRIANGLES, attrs, assets.corruptedArmorMaterial)
            buildCylinder(legPart, m.idt().translate(0f, -0.48f, 0f), 0.15f, 0.92f, 0.15f, 8)
            BoxShapeBuilder.build(legPart, 0f, -0.94f, 0.05f, 0.16f, 0.12f, 0.28f)
        }

        val rightLegNode: Node = mb.node()
        rightLegNode.id = "enemyRightLeg"
        rightLegNode.translation.set(-0.13f, -0.05f, 0f)
        run {
            val legPart = mb.part("e_r_leg", GL20.GL_TRIANGLES, attrs, assets.corruptedArmorMaterial)
            buildCylinder(legPart, m.idt().translate(0f, -0.48f, 0f), 0.15f, 0.92f, 0.15f, 8)
            BoxShapeBuilder.build(legPart, 0f, -0.94f, 0.05f, 0.16f, 0.12f, 0.28f)
        }

        val model = mb.end()
        model.nodes.clear()
        model.nodes.add(pelvisNode)
        pelvisNode.addChild(torsoNode)
        pelvisNode.addChild(leftLegNode)
        pelvisNode.addChild(rightLegNode)
        torsoNode.addChild(headNode)
        torsoNode.addChild(leftArmNode)
        torsoNode.addChild(rightArmNode)
        model.calculateTransforms()

        return assets.registerModel(model)
    }

    /**
     * Creates the 3D Energy Blast sphere model (used for charging orb & launched Charged Energy Blast).
     */
    fun createEnergyBlastModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val corePart = mb.part("blast_core", GL20.GL_TRIANGLES, attrs, assets.energyBlastMaterial)
        buildSphere(corePart, m.idt(), 0.52f, 0.52f, 0.52f, 12, 12)

        val ringPart = mb.part("blast_rings", GL20.GL_TRIANGLES, attrs, assets.goldImpactMaterial)
        buildCylinder(ringPart, m.idt().rotate(1f, 0f, 1f, 35f), 0.72f, 0.04f, 0.72f, 14)
        buildCylinder(ringPart, m.idt().rotate(1f, 0f, -1f, -35f), 0.68f, 0.04f, 0.68f, 14)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates the 3D Arcane Magic Bolt projectile model.
     */
    fun createArcaneBoltModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val boltPart = mb.part("arcane_bolt", GL20.GL_TRIANGLES, attrs, assets.arcaneBoltMaterial)
        buildCone(boltPart, m.idt().translate(0f, 0f, 0.18f).rotate(1f, 0f, 0f, 90f), 0.22f, 0.48f, 0.22f, 6)
        buildCone(boltPart, m.idt().translate(0f, 0f, -0.18f).rotate(1f, 0f, 0f, -90f), 0.22f, 0.36f, 0.22f, 6)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a crescent melee slash / martial arts shockwave arc model.
     */
    fun createSlashWaveModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val wavePart = mb.part("slash_wave", GL20.GL_TRIANGLES, attrs, assets.slashWaveMaterial)
        buildCylinder(wavePart, m.idt(), 1.35f, 0.045f, 1.35f, 14)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a diamond 3D spark particle model for combat impacts, dash trails, and energy bursts.
     */
    fun createCombatSparkModel(variant: Int): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()
        val mat = when (variant) {
            1 -> assets.goldImpactMaterial
            2 -> assets.corruptedCrimsonGlowMaterial
            else -> assets.energyBlastMaterial
        }
        val sparkPart = mb.part("combat_spark", GL20.GL_TRIANGLES, attrs, mat)
        buildSphere(sparkPart, m.idt(), 0.22f, 0.22f, 0.22f, 6, 6)
        return assets.registerModel(mb.end())
    }

    /**
     * Creates a 3D overhead Lock-On Target Marker (downward-pointing golden/cyan crystal chevron
     * surrounded by an orbital target ring) that hovers above the locked enemy.
     */
    fun createTargetLockMarkerModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val corePart = mb.part("lock_marker_core", GL20.GL_TRIANGLES, attrs, assets.goldImpactMaterial)
        // Downward-pointing crystal pointer
        buildCone(
            corePart,
            m.idt().translate(0f, -0.16f, 0f).rotate(1f, 0f, 0f, 180f),
            0.34f, 0.42f, 0.34f, 4
        )
        // Upper diamond cap
        buildCone(
            corePart,
            m.idt().translate(0f, 0.14f, 0f),
            0.34f, 0.20f, 0.34f, 4
        )

        val ringPart = mb.part("lock_marker_ring", GL20.GL_TRIANGLES, attrs, assets.energyBlastMaterial)
        buildCylinder(ringPart, m.idt().translate(0f, -0.04f, 0f), 0.72f, 0.045f, 0.72f, 12)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a curved crescent sword energy arc model that follows the blade's slash plane.
     * Built from a sculpted 14-segment crescent arc with a sharp outer cutting edge and glowing core.
     */
    fun createCurvedSwordArcModel(isFiery: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val mat = if (isFiery) assets.swordTrailFireMaterial else assets.swordTrailCyanMaterial
        val part = mb.part("curved_sword_arc", GL20.GL_TRIANGLES, attrs, mat)

        val segments = 14
        val sweepRad = Math.toRadians(145.0).toFloat()
        val startRad = -sweepRad * 0.5f

        val vInner0 = MeshPartBuilder.VertexInfo()
        val vOuter0 = MeshPartBuilder.VertexInfo()
        val vInner1 = MeshPartBuilder.VertexInfo()
        val vOuter1 = MeshPartBuilder.VertexInfo()

        for (i in 0 until segments) {
            val t0 = i.toFloat() / segments
            val t1 = (i + 1).toFloat() / segments
            val a0 = startRad + t0 * sweepRad
            val a1 = startRad + t1 * sweepRad

            // Taper width at both tips of the crescent
            val taper0 = sin(t0 * Math.PI.toFloat()).coerceAtLeast(0.08f)
            val taper1 = sin(t1 * Math.PI.toFloat()).coerceAtLeast(0.08f)

            val outerR = 1.48f
            val innerR0 = outerR - 0.52f * taper0
            val innerR1 = outerR - 0.52f * taper1

            vInner0.setPos(sin(a0) * innerR0, 0f, cos(a0) * innerR0).setNor(0f, 1f, 0f).setUV(t0, 0f)
            vOuter0.setPos(sin(a0) * outerR, 0f, cos(a0) * outerR).setNor(0f, 1f, 0f).setUV(t0, 1f)
            vInner1.setPos(sin(a1) * innerR1, 0f, cos(a1) * innerR1).setNor(0f, 1f, 0f).setUV(t1, 0f)
            vOuter1.setPos(sin(a1) * outerR, 0f, cos(a1) * outerR).setNor(0f, 1f, 0f).setUV(t1, 1f)

            part.rect(vInner0, vOuter0, vOuter1, vInner1)
        }

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a single blade trail ribbon quad segment model (`+Z` from 0 to 1, width along `X` from -0.5 to +0.5)
     * that can be oriented between consecutive world-space `(bladeBase, bladeTip)` samples.
     */
    fun createBladeTrailSegmentModel(isFiery: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val mat = if (isFiery) assets.swordTrailFireMaterial else assets.swordTrailCyanMaterial
        val part = mb.part("blade_trail_seg", GL20.GL_TRIANGLES, attrs, mat)
        BoxShapeBuilder.build(part, 0f, 0f, 0f, 1f, 0.018f, 1f)
        return assets.registerModel(mb.end())
    }

    /**
     * Creates the large flaming Inferno Crescent projectile model (curved fiery outer crescent +
     * golden inner blade core + trailing flame spikes).
     */
    fun createInfernoCrescentProjectileModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val flamePart = mb.part("inferno_crescent_flame", GL20.GL_TRIANGLES, attrs, assets.infernoFlameMaterial)
        val segments = 16
        val sweepRad = Math.toRadians(160.0).toFloat()
        val startRad = -sweepRad * 0.5f

        val vIn0 = MeshPartBuilder.VertexInfo()
        val vOut0 = MeshPartBuilder.VertexInfo()
        val vIn1 = MeshPartBuilder.VertexInfo()
        val vOut1 = MeshPartBuilder.VertexInfo()

        for (i in 0 until segments) {
            val t0 = i.toFloat() / segments
            val t1 = (i + 1).toFloat() / segments
            val a0 = startRad + t0 * sweepRad
            val a1 = startRad + t1 * sweepRad
            val taper0 = sin(t0 * Math.PI.toFloat()).coerceAtLeast(0.10f)
            val taper1 = sin(t1 * Math.PI.toFloat()).coerceAtLeast(0.10f)

            val outerR = 1.95f
            val innerR0 = outerR - 0.78f * taper0
            val innerR1 = outerR - 0.78f * taper1

            vIn0.setPos(sin(a0) * innerR0, 0f, cos(a0) * innerR0).setNor(0f, 1f, 0f).setUV(t0, 0f)
            vOut0.setPos(sin(a0) * outerR, 0f, cos(a0) * outerR).setNor(0f, 1f, 0f).setUV(t0, 1f)
            vIn1.setPos(sin(a1) * innerR1, 0f, cos(a1) * innerR1).setNor(0f, 1f, 0f).setUV(t1, 0f)
            vOut1.setPos(sin(a1) * outerR, 0f, cos(a1) * outerR).setNor(0f, 1f, 0f).setUV(t1, 1f)
            flamePart.rect(vIn0, vOut0, vOut1, vIn1)
            flamePart.rect(vOut0, vIn0, vIn1, vOut1)
        }

        // Trailing flame fangs along the crescent arc
        val fangAngles = floatArrayOf(-50f, -25f, 0f, 25f, 50f)
        for (deg in fangAngles) {
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val fx = sin(rad) * 1.45f
            val fz = cos(rad) * 1.45f
            buildCone(
                flamePart,
                m.idt().translate(fx, 0f, fz).rotate(0f, 1f, 0f, deg).rotate(1f, 0f, 0f, -90f),
                0.22f, 0.75f, 0.14f, 6
            )
        }

        val goldCore = mb.part("inferno_crescent_core", GL20.GL_TRIANGLES, attrs, assets.goldImpactMaterial)
        buildCylinder(goldCore, m.idt().translate(0f, 0f, 0.45f), 2.55f, 0.055f, 1.35f, 14)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates the Dragon Tempest circular wind & energy shockwave model (spinning cyclone rings +
     * rising wind blades).
     */
    fun createDragonTempestShockwaveModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val cyanVortex = mb.part("tempest_vortex", GL20.GL_TRIANGLES, attrs, assets.swordTrailCyanMaterial)
        buildCylinder(cyanVortex, m.idt().translate(0f, 0.18f, 0f), 2.4f, 0.06f, 2.4f, 18)
        buildCylinder(cyanVortex, m.idt().translate(0f, 0.68f, 0f).rotate(1f, 0f, 1f, 12f), 1.95f, 0.05f, 1.95f, 16)
        buildCylinder(cyanVortex, m.idt().translate(0f, 1.18f, 0f).rotate(1f, 0f, -1f, -12f), 1.55f, 0.05f, 1.55f, 16)

        val windPart = mb.part("tempest_wind_blades", GL20.GL_TRIANGLES, attrs, assets.windStreakMaterial)
        val bladeAngles = floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f)
        for (deg in bladeAngles) {
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val bx = cos(rad) * 1.10f
            val bz = sin(rad) * 1.10f
            buildCone(
                windPart,
                m.idt().translate(bx, 0.55f, bz).rotate(0f, 1f, 0f, -deg).rotate(1f, 0f, 0f, 32f),
                0.18f, 1.15f, 0.06f, 5
            )
        }

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a translucent glowing azure-cyan Afterimage Ghost silhouette of the spellsword
     * left along the movement path during Phantom Dash Slash, Flash Step, and Multi-Directional Slash.
     */
    fun createAfterimageGhostModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()
        val ghostPart = mb.part("afterimage_ghost", GL20.GL_TRIANGLES, attrs, assets.afterimageGhostMaterial)

        // Dynamic forward-leaning spellsword silhouette with flowing cape and extended blade
        buildCylinder(ghostPart, m.idt().translate(0f, 0.55f, 0f).rotate(1f, 0f, 0f, 18f), 0.26f, 0.95f, 0.20f, 8)
        buildBox(ghostPart, m.idt().translate(0f, 1.12f, 0.08f).rotate(1f, 0f, 0f, 22f), 0.34f, 0.46f, 0.22f)
        buildSphere(ghostPart, m.idt().translate(0f, 1.48f, 0.16f), 0.22f, 0.24f, 0.22f, 8, 8)
        // Flowing phantom cape
        buildBox(ghostPart, m.idt().translate(0f, 0.96f, -0.22f).rotate(1f, 0f, 0f, 38f), 0.42f, 0.72f, 0.04f)
        // Outstretched phantom rune blade
        buildBox(ghostPart, m.idt().translate(-0.24f, 1.10f, 0.55f).rotate(0f, 1f, 0f, 15f), 0.03f, 0.06f, 0.92f)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a sleek aerodynamic 3D wind streak model for high-speed slashes and Flash Step.
     */
    fun createWindStreakModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()
        val streakPart = mb.part("wind_streak", GL20.GL_TRIANGLES, attrs, assets.windStreakMaterial)
        buildCone(streakPart, m.idt().translate(0f, 0f, 0.45f).rotate(1f, 0f, 0f, 90f), 0.08f, 0.90f, 0.08f, 5)
        buildCone(streakPart, m.idt().translate(0f, 0f, -0.45f).rotate(1f, 0f, 0f, -90f), 0.08f, 0.90f, 0.08f, 5)
        return assets.registerModel(mb.end())
    }

    /**
     * Creates a low-profile expanding ground dust burst model for powerful sword attacks and footwork.
     */
    fun createGroundDustModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()
        val dustPart = mb.part("ground_dust", GL20.GL_TRIANGLES, attrs, assets.groundDustMaterial)
        val angles = floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f)
        for (deg in angles) {
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val px = cos(rad) * 0.42f
            val pz = sin(rad) * 0.42f
            buildSphere(dustPart, m.idt().translate(px, 0.14f, pz), 0.36f, 0.22f, 0.36f, 6, 5)
        }
        return assets.registerModel(mb.end())
    }

    /**
     * Creates a 3D multi-axis star flash rendered at the exact hit location when a sword strike connects.
     */
    fun createHitFlashStarModel(isFiery: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()
        val mat = if (isFiery) assets.goldImpactMaterial else assets.swordTrailCyanMaterial
        val starPart = mb.part("hit_flash_star", GL20.GL_TRIANGLES, attrs, mat)
        buildBox(starPart, m.idt(), 1.15f, 0.07f, 0.07f)
        buildBox(starPart, m.idt(), 0.07f, 1.15f, 0.07f)
        buildBox(starPart, m.idt().rotate(0f, 0f, 1f, 45f), 0.85f, 0.055f, 0.055f)
        buildBox(starPart, m.idt().rotate(0f, 0f, 1f, -45f), 0.85f, 0.055f, 0.055f)
        return assets.registerModel(mb.end())
    }

    // =========================================================================
    // Milestone 4 — Advanced Anime Fists Combat Procedural 3D VFX Models
    // =========================================================================

    /**
     * Creates a 3D wind-trail ribbon segment for punches and kicks (`+Z` along limb, `X` along swing chord).
     */
    fun createFistWindTrailSegmentModel(isGoldenChi: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val mat = if (isGoldenChi) assets.fistChiGoldMaterial else assets.fistWindTrailMaterial
        val part = mb.part("fist_wind_trail_seg", GL20.GL_TRIANGLES, attrs, mat)
        BoxShapeBuilder.build(part, 0f, 0f, 0f, 1f, 0.022f, 1f)
        return assets.registerModel(mb.end())
    }

    /**
     * Creates a sculpted 120-degree martial arts wind crescent arc for kicks and rotational punches.
     */
    fun createFistWindArcModel(isGoldenChi: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val mat = if (isGoldenChi) assets.fistChiGoldMaterial else assets.fistWindTrailMaterial
        val part = mb.part("fist_wind_arc", GL20.GL_TRIANGLES, attrs, mat)

        val segments = 12
        val sweepRad = Math.toRadians(125.0).toFloat()
        val startRad = -sweepRad * 0.5f

        val vIn0 = MeshPartBuilder.VertexInfo()
        val vOut0 = MeshPartBuilder.VertexInfo()
        val vIn1 = MeshPartBuilder.VertexInfo()
        val vOut1 = MeshPartBuilder.VertexInfo()

        for (i in 0 until segments) {
            val t0 = i.toFloat() / segments
            val t1 = (i + 1).toFloat() / segments
            val a0 = startRad + t0 * sweepRad
            val a1 = startRad + t1 * sweepRad
            val taper0 = sin(t0 * Math.PI.toFloat()).coerceAtLeast(0.10f)
            val taper1 = sin(t1 * Math.PI.toFloat()).coerceAtLeast(0.10f)

            val outerR = 1.28f
            val innerR0 = outerR - 0.44f * taper0
            val innerR1 = outerR - 0.44f * taper1

            vIn0.setPos(sin(a0) * innerR0, 0f, cos(a0) * innerR0).setNor(0f, 1f, 0f).setUV(t0, 0f)
            vOut0.setPos(sin(a0) * outerR, 0f, cos(a0) * outerR).setNor(0f, 1f, 0f).setUV(t0, 1f)
            vIn1.setPos(sin(a1) * innerR1, 0f, cos(a1) * innerR1).setNor(0f, 1f, 0f).setUV(t1, 0f)
            vOut1.setPos(sin(a1) * outerR, 0f, cos(a1) * outerR).setNor(0f, 1f, 0f).setUV(t1, 1f)
            part.rect(vIn0, vOut0, vOut1, vIn1)
            part.rect(vOut0, vIn0, vIn1, vOut1)
        }

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a 3D expanding air-pressure shockwave ring (hollow octadecagon pressure wave +
     * directional wind spikes) oriented along the strike vector (`+Z`).
     */
    fun createAirPressureRingModel(isGoldenChi: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()
        val mat = if (isGoldenChi) assets.fistChiGoldMaterial else assets.fistWindTrailMaterial
        val ringPart = mb.part("air_pressure_ring", GL20.GL_TRIANGLES, attrs, mat)

        val segments = 16
        val rInner = 0.58f
        val rOuter = 0.86f
        val vIn0 = MeshPartBuilder.VertexInfo()
        val vOut0 = MeshPartBuilder.VertexInfo()
        val vIn1 = MeshPartBuilder.VertexInfo()
        val vOut1 = MeshPartBuilder.VertexInfo()

        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * (Math.PI.toFloat() * 2f)
            val a1 = ((i + 1).toFloat() / segments) * (Math.PI.toFloat() * 2f)
            val c0 = cos(a0)
            val s0 = sin(a0)
            val c1 = cos(a1)
            val s1 = sin(a1)

            // Ring lies in the XY plane facing +Z so rotating by strike yaw/pitch aligns it with the punch/kick
            vIn0.setPos(c0 * rInner, s0 * rInner, 0.06f).setNor(0f, 0f, 1f).setUV(0f, 0f)
            vOut0.setPos(c0 * rOuter, s0 * rOuter, -0.06f).setNor(0f, 0f, 1f).setUV(1f, 0f)
            vIn1.setPos(c1 * rInner, s1 * rInner, 0.06f).setNor(0f, 0f, 1f).setUV(0f, 1f)
            vOut1.setPos(c1 * rOuter, s1 * rOuter, -0.06f).setNor(0f, 0f, 1f).setUV(1f, 1f)
            ringPart.rect(vIn0, vOut0, vOut1, vIn1)
            ringPart.rect(vOut0, vIn0, vIn1, vOut1)
        }

        // Axial air-pressure jet cones around the ring perimeter
        val jetAngles = floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f)
        for (deg in jetAngles) {
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val jx = cos(rad) * 0.72f
            val jy = sin(rad) * 0.72f
            buildCone(
                ringPart,
                m.idt().translate(jx, jy, -0.18f).rotate(1f, 0f, 0f, -90f),
                0.10f, 0.48f, 0.10f, 5
            )
        }

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a 3D radial chi energy burst model (8 outward energy lances + core shockwave)
     * rendered on heavy martial arts impacts and combo finishers.
     */
    fun createRadialChiBurstModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val goldPart = mb.part("chi_burst_gold", GL20.GL_TRIANGLES, attrs, assets.fistChiGoldMaterial)
        val angles = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
        for (deg in angles) {
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val dirX = cos(rad)
            val dirZ = sin(rad)
            buildCone(
                goldPart,
                m.idt().translate(dirX * 0.55f, 0.10f, dirZ * 0.55f).rotate(0f, 1f, 0f, -deg + 90f).rotate(1f, 0f, 0f, 76f),
                0.16f, 0.92f, 0.12f, 5
            )
        }

        val ringPart = mb.part("chi_burst_ring", GL20.GL_TRIANGLES, attrs, assets.fistWindTrailMaterial)
        buildCylinder(ringPart, m.idt().translate(0f, 0.08f, 0f), 1.65f, 0.045f, 1.65f, 16)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates an unarmed Martial Arts Afterimage Ghost silhouette (dynamic forward-striking posture
     * without any drawn sword) for fast martial arts combos, finishers, and heavy attacks.
     */
    fun createMartialAfterimageGhostModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()
        val ghostPart = mb.part("martial_afterimage_ghost", GL20.GL_TRIANGLES, attrs, assets.martialAfterimageMaterial)

        buildCylinder(ghostPart, m.idt().translate(0f, 0.55f, 0f).rotate(1f, 0f, 0f, 20f), 0.25f, 0.92f, 0.20f, 8)
        buildBox(ghostPart, m.idt().translate(0f, 1.12f, 0.10f).rotate(1f, 0f, 0f, 24f), 0.34f, 0.45f, 0.22f)
        buildSphere(ghostPart, m.idt().translate(0f, 1.48f, 0.18f), 0.22f, 0.24f, 0.22f, 8, 8)
        // Extended striking arm & chambered kick leg silhouette
        buildCylinder(ghostPart, m.idt().translate(-0.18f, 1.22f, 0.42f).rotate(1f, 0f, 0f, 86f), 0.11f, 0.56f, 0.11f, 6)
        buildCylinder(ghostPart, m.idt().translate(0.14f, 0.78f, 0.36f).rotate(1f, 0f, 0f, 68f), 0.14f, 0.64f, 0.14f, 6)
        // Flowing cloak silhouette
        buildBox(ghostPart, m.idt().translate(0f, 0.96f, -0.22f).rotate(1f, 0f, 0f, 40f), 0.40f, 0.70f, 0.04f)

        return assets.registerModel(mb.end())
    }

    /**
     * Generates a smooth 3D heightfield terrain mesh with analytical surface normals and tiled UVs.
     */
    fun createTerrainModel(
        worldSize: Float,
        divisions: Int,
        heightAt: (Float, Float) -> Float
    ): Model {
        val mb = ModelBuilder()
        mb.begin()
        val part = mb.part("terrain_grid", GL20.GL_TRIANGLES, attrs, assets.terrainMaterial)

        val gridCount = divisions + 1
        val half = worldSize * 0.5f
        val step = worldSize / divisions
        val uvScale = (worldSize / 6f) / divisions

        val heights = FloatArray(gridCount * gridCount)
        val normalsX = FloatArray(gridCount * gridCount)
        val normalsY = FloatArray(gridCount * gridCount)
        val normalsZ = FloatArray(gridCount * gridCount)

        // 1. Precompute height at each grid vertex in a single fast pass
        for (gz in 0 until gridCount) {
            val wz = -half + gz * step
            val rowOffset = gz * gridCount
            for (gx in 0 until gridCount) {
                val wx = -half + gx * step
                heights[rowOffset + gx] = heightAt(wx, wz)
            }
        }

        // 2. Precompute normals via central differences on grid neighbors (0 transcendental function calls)
        val inv2Step = 1f / (2f * step)
        for (gz in 0 until gridCount) {
            val rowOffset = gz * gridCount
            val prevRow = (if (gz > 0) gz - 1 else 0) * gridCount
            val nextRow = (if (gz < divisions) gz + 1 else divisions) * gridCount
            val dZFactor = if (gz == 0 || gz == divisions) 1f / step else inv2Step

            for (gx in 0 until gridCount) {
                val prevX = if (gx > 0) gx - 1 else 0
                val nextX = if (gx < divisions) gx + 1 else divisions
                val dXFactor = if (gx == 0 || gx == divisions) 1f / step else inv2Step

                val hL = heights[rowOffset + prevX]
                val hR = heights[rowOffset + nextX]
                val hD = heights[prevRow + gx]
                val hU = heights[nextRow + gx]

                var nx = (hL - hR) * dXFactor
                var ny = 1f
                var nz = (hD - hU) * dZFactor
                val invLen = 1f / sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)

                val idx = rowOffset + gx
                normalsX[idx] = nx * invLen
                normalsY[idx] = ny * invLen
                normalsZ[idx] = nz * invLen
            }
        }

        val v00 = MeshPartBuilder.VertexInfo()
        val v10 = MeshPartBuilder.VertexInfo()
        val v11 = MeshPartBuilder.VertexInfo()
        val v01 = MeshPartBuilder.VertexInfo()

        fun setVertex(vi: MeshPartBuilder.VertexInfo, gx: Int, gz: Int) {
            val idx = gz * gridCount + gx
            val wx = -half + gx * step
            val wz = -half + gz * step
            vi.setPos(wx, heights[idx], wz)
            vi.setNor(normalsX[idx], normalsY[idx], normalsZ[idx])
            vi.setUV(gx * uvScale, gz * uvScale)
        }

        for (gz in 0 until divisions) {
            for (gx in 0 until divisions) {
                setVertex(v00, gx, gz)
                setVertex(v10, gx + 1, gz)
                setVertex(v11, gx + 1, gz + 1)
                setVertex(v01, gx, gz + 1)
                part.rect(v00, v01, v11, v10)
            }
        }

        return assets.registerModel(mb.end())
    }

    /**
     * Creates the Central Ancient Rune Monument with stepped stone sanctuary base,
     * 4 carved rune pillars, glowing obelisk core, and arcane rings.
     */
    fun createCentralMonumentModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val stonePart = mb.part("monument_stone", GL20.GL_TRIANGLES, attrs, assets.stoneMonumentMaterial)
        buildCylinder(stonePart, m.idt().translate(0f, 0.25f, 0f), 7.6f, 0.50f, 7.6f, 8)
        buildCylinder(stonePart, m.idt().translate(0f, 0.65f, 0f), 5.6f, 0.40f, 5.6f, 8)
        buildCylinder(stonePart, m.idt().translate(0f, 1.00f, 0f), 3.6f, 0.35f, 3.6f, 8)
        buildCylinder(stonePart, m.idt().translate(0f, 1.85f, 0f), 1.55f, 1.40f, 1.55f, 8)

        val pillarAngles = floatArrayOf(45f, 135f, 225f, 315f)
        val pillarRadius = 2.85f
        for (deg in pillarAngles) {
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val px = cos(rad) * pillarRadius
            val pz = sin(rad) * pillarRadius
            buildCylinder(stonePart, m.idt().translate(px, 2.75f, pz), 0.62f, 3.6f, 0.62f, 6)
            buildCone(stonePart, m.idt().translate(px, 4.90f, pz), 0.78f, 0.75f, 0.78f, 6)
        }

        val runePart = mb.part("monument_runes", GL20.GL_TRIANGLES, attrs, assets.glowingRuneMaterial)
        buildCylinder(runePart, m.idt().translate(0f, 1.20f, 0f), 3.70f, 0.08f, 3.70f, 16)
        buildCylinder(runePart, m.idt().translate(0f, 2.58f, 0f), 1.66f, 0.12f, 1.66f, 8)

        buildCone(runePart, m.idt().translate(0f, 5.10f, 0f), 1.15f, 3.20f, 1.15f, 6)
        buildCone(
            runePart,
            m.idt().translate(0f, 3.05f, 0f).rotate(1f, 0f, 0f, 180f),
            1.15f, 0.95f, 1.15f, 6
        )

        for (deg in pillarAngles) {
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val px = cos(rad) * pillarRadius
            val pz = sin(rad) * pillarRadius
            buildSphere(runePart, m.idt().translate(px, 5.55f, pz), 0.42f, 0.62f, 0.42f, 8, 8)
        }

        val goldPart = mb.part("monument_gold_rings", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
        buildCylinder(
            goldPart,
            m.idt().translate(0f, 4.35f, 0f).rotate(1f, 0f, 1f, 28f),
            2.35f, 0.10f, 2.35f, 16
        )
        buildCylinder(
            goldPart,
            m.idt().translate(0f, 4.35f, 0f).rotate(1f, 0f, -1f, -28f),
            2.05f, 0.09f, 2.05f, 16
        )

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a multi-canopy Anime Fantasy Tree with organic roots, curved trunk,
     * branches, layered foliage crowns, and glowing bioluminescent lantern pods.
     */
    fun createFantasyTreeModel(isCyanVariant: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val barkPart = mb.part("tree_bark", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
        buildCone(barkPart, m.idt().translate(0f, 0.70f, 0f), 1.65f, 1.50f, 1.65f, 10)
        buildCylinder(
            barkPart,
            m.idt().translate(0f, 2.10f, 0f).rotate(0f, 0f, 1f, 4f),
            0.92f, 2.80f, 0.92f, 10
        )
        buildCylinder(
            barkPart,
            m.idt().translate(-0.14f, 4.20f, 0.06f).rotate(0f, 0f, 1f, -5f),
            0.68f, 2.20f, 0.68f, 10
        )
        buildCylinder(
            barkPart,
            m.idt().translate(0.75f, 4.20f, 0.15f).rotate(0f, 0f, 1f, -48f),
            0.36f, 1.90f, 0.36f, 8
        )
        buildCylinder(
            barkPart,
            m.idt().translate(-0.85f, 4.45f, -0.20f).rotate(0f, 0f, 1f, 44f),
            0.34f, 1.80f, 0.34f, 8
        )

        val canopyMat = if (isCyanVariant) assets.canopyCyanMaterial else assets.canopyVioletMaterial
        val canopyPart = mb.part("tree_canopy", GL20.GL_TRIANGLES, attrs, canopyMat)
        buildSphere(canopyPart, m.idt().translate(0f, 6.0f, 0f), 4.4f, 2.6f, 4.4f, 12, 10)
        buildSphere(canopyPart, m.idt().translate(1.55f, 5.1f, 0.45f), 2.9f, 2.0f, 2.9f, 10, 8)
        buildSphere(canopyPart, m.idt().translate(-1.60f, 5.3f, -0.35f), 3.0f, 2.1f, 3.0f, 10, 8)
        buildSphere(canopyPart, m.idt().translate(0.15f, 7.2f, -0.10f), 3.1f, 2.0f, 3.1f, 10, 8)

        val glowMat = if (isCyanVariant) assets.glowingRuneMaterial else assets.amethystCrystalMaterial
        val glowPart = mb.part("tree_glow_pods", GL20.GL_TRIANGLES, attrs, glowMat)
        buildSphere(glowPart, m.idt().translate(1.45f, 3.95f, 0.40f), 0.28f, 0.48f, 0.28f, 8, 8)
        buildSphere(glowPart, m.idt().translate(-1.45f, 4.15f, -0.30f), 0.28f, 0.48f, 0.28f, 8, 8)
        buildSphere(glowPart, m.idt().translate(0.25f, 4.40f, 1.35f), 0.25f, 0.42f, 0.25f, 8, 8)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a faceted mossy fantasy boulder with optional glowing mana crystal veins.
     */
    fun createBoulderModel(withCrystalCluster: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val rockPart = mb.part("boulder_rock", GL20.GL_TRIANGLES, attrs, assets.boulderMaterial)
        buildSphere(rockPart, m.idt().translate(0f, 0.65f, 0f), 2.2f, 1.5f, 1.9f, 7, 6)
        buildSphere(
            rockPart,
            m.idt().translate(0.75f, 0.45f, 0.35f).rotate(0f, 1f, 0f, 35f),
            1.4f, 1.1f, 1.3f, 6, 5
        )
        buildSphere(
            rockPart,
            m.idt().translate(-0.65f, 0.50f, -0.25f).rotate(0f, 1f, 0f, -25f),
            1.3f, 1.2f, 1.2f, 6, 5
        )

        if (withCrystalCluster) {
            val crystalPart = mb.part("boulder_crystals", GL20.GL_TRIANGLES, attrs, assets.manaCrystalMaterial)
            buildCone(
                crystalPart,
                m.idt().translate(0.35f, 1.35f, 0.15f).rotate(0f, 0f, 1f, -18f),
                0.34f, 1.10f, 0.34f, 6
            )
            buildCone(
                crystalPart,
                m.idt().translate(-0.15f, 1.25f, -0.20f).rotate(1f, 0f, 1f, 22f),
                0.26f, 0.85f, 0.26f, 6
            )
        }

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a crossed multi-blade grass and glowing bioluminescent fern tuft.
     */
    fun createGrassClusterModel(isGlowingVariant: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()
        val mat = if (isGlowingVariant) assets.glowingFernMaterial else assets.grassMaterial
        val grassPart = mb.part("grass_tuft", GL20.GL_TRIANGLES, attrs, mat)

        val bladeAngles = floatArrayOf(0f, 45f, 90f, 135f, 200f, 260f, 315f)
        for (i in bladeAngles.indices) {
            val deg = bladeAngles[i]
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val tilt = 16f + (i % 3) * 8f
            val h = 0.52f + (i % 2) * 0.18f
            m.idt()
                .translate(cos(rad) * 0.12f, h * 0.45f, sin(rad) * 0.12f)
                .rotate(0f, 1f, 0f, deg)
                .rotate(1f, 0f, 0f, tilt)
            buildCone(grassPart, m, 0.12f, h, 0.04f, 5)
        }

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a double-ended hexagonal floating mana crystal shard.
     */
    fun createManaCrystalModel(isAmethyst: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()
        val mat = if (isAmethyst) assets.amethystCrystalMaterial else assets.manaCrystalMaterial
        val crystalPart = mb.part("mana_crystal", GL20.GL_TRIANGLES, attrs, mat)

        buildCone(crystalPart, m.idt().translate(0f, 0.42f, 0f), 0.46f, 0.84f, 0.46f, 6)
        buildCone(
            crystalPart,
            m.idt().translate(0f, -0.42f, 0f).rotate(1f, 0f, 0f, 180f),
            0.46f, 0.84f, 0.46f, 6
        )

        return assets.registerModel(mb.end())
    }

    /**
     * Creates the dynamic celestial sky dome with atmospheric dome, Sun, Moon, and star field.
     */
    fun createSkyDomeModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        // 1. Atmosphere Hemisphere Dome
        val skyPart = mb.part("sky_dome", GL20.GL_TRIANGLES, attrs, assets.skyDomeMaterial)
        buildSphere(skyPart, m.idt(), 380f, 260f, 380f, 24, 16)

        // 2. Celestial Sun Node
        val sunPart = mb.part("sky_sun", GL20.GL_TRIANGLES, attrs, assets.skySunMaterial)
        buildSphere(sunPart, m.idt().translate(0f, 95f, 140f), 22f, 22f, 22f, 14, 14)

        // 3. Celestial Moon Node
        val moonPart = mb.part("sky_moon", GL20.GL_TRIANGLES, attrs, assets.skyMoonMaterial)
        buildSphere(moonPart, m.idt().translate(0f, -95f, -140f), 18f, 18f, 18f, 14, 14)

        // 4. Star Field across the upper hemisphere dome
        val starPart = mb.part("sky_stars", GL20.GL_TRIANGLES, attrs, assets.skyStarsMaterial)
        val starCount = 110
        for (i in 0 until starCount) {
            val goldenAngle = i * 137.508f
            val rad = Math.toRadians(goldenAngle.toDouble()).toFloat()
            val elev = 0.18f + (i.toFloat() / starCount) * 0.78f
            val groundDist = sqrt(1f - elev * elev) * 175f
            val sx = cos(rad) * groundDist
            val sz = sin(rad) * groundDist
            val sy = elev * 125f
            val szDim = 0.75f + (i % 3) * 0.35f
            buildBox(starPart, m.idt().translate(sx, sy, sz), szDim, szDim, szDim)
        }

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a functional fantasy Crafting Table workbench model with wooden tabletop,
     * shelf, rune engraving, and crafting tools.
     */
    fun createCraftingTableModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val woodPart = mb.part("workbench_wood", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
        buildBox(woodPart, m.idt().translate(0f, 0.82f, 0f), 1.35f, 0.16f, 0.90f)
        buildBox(woodPart, m.idt().translate(-0.52f, 0.38f, -0.34f), 0.14f, 0.76f, 0.14f)
        buildBox(woodPart, m.idt().translate(0.52f, 0.38f, -0.34f), 0.14f, 0.76f, 0.14f)
        buildBox(woodPart, m.idt().translate(-0.52f, 0.38f, 0.34f), 0.14f, 0.76f, 0.14f)
        buildBox(woodPart, m.idt().translate(0.52f, 0.38f, 0.34f), 0.14f, 0.76f, 0.14f)
        buildBox(woodPart, m.idt().translate(0f, 0.20f, 0f), 1.15f, 0.08f, 0.72f)

        val metalPart = mb.part("workbench_metal", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
        buildBox(metalPart, m.idt().translate(-0.42f, 0.96f, 0.22f), 0.22f, 0.14f, 0.22f)
        buildCylinder(metalPart, m.idt().translate(0.32f, 0.93f, -0.15f).rotate(0f, 1f, 0f, 25f), 0.08f, 0.32f, 0.08f, 6)

        val runePart = mb.part("workbench_rune", GL20.GL_TRIANGLES, attrs, assets.glowingRuneMaterial)
        buildBox(runePart, m.idt().translate(0f, 0.91f, 0f), 0.26f, 0.02f, 0.26f)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates an ancient banded wooden Storage Chest model with iron bands and golden lock.
     */
    fun createStorageChestModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val woodPart = mb.part("chest_wood", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
        buildBox(woodPart, m.idt().translate(0f, 0.34f, 0f), 1.08f, 0.62f, 0.74f)
        buildBox(woodPart, m.idt().translate(0f, 0.72f, 0f), 1.10f, 0.18f, 0.76f)

        val metalPart = mb.part("chest_iron_bands", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
        buildBox(metalPart, m.idt().translate(-0.38f, 0.44f, 0f), 0.10f, 0.76f, 0.77f)
        buildBox(metalPart, m.idt().translate(0.38f, 0.44f, 0f), 0.10f, 0.76f, 0.77f)
        buildBox(metalPart, m.idt().translate(0f, 0.54f, 0.38f), 0.14f, 0.18f, 0.06f)

        val goldPart = mb.part("chest_gold_lock", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
        buildCylinder(goldPart, m.idt().translate(0f, 0.52f, 0.40f), 0.09f, 0.06f, 0.09f, 8)

        return assets.registerModel(mb.end())
    }

    /**
     * Builds the 3D mesh parts for a given tool [toolId] into the currently active node of [mb].
     * Extends along +Z from the hand grip origin (0, 0, 0).
     */
    private fun buildToolPartsIntoCurrentNode(mb: ModelBuilder, m: Matrix4, toolId: String, prefix: String) {
        when (toolId) {
            "stone_axe", "iron_axe" -> {
                val isIron = toolId == "iron_axe"
                val handlePart = mb.part("${prefix}_shaft", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
                buildCylinder(
                    handlePart,
                    m.idt().translate(0f, 0f, 0.24f).rotate(1f, 0f, 0f, 90f),
                    0.036f, 0.62f, 0.036f, 8
                )
                val bindingPart = mb.part("${prefix}_bind", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
                BoxShapeBuilder.build(bindingPart, 0f, 0f, 0.44f, 0.052f, 0.056f, 0.075f)

                val headMat = if (isIron) assets.silverArmorMaterial else assets.boulderMaterial
                val headPart = mb.part("${prefix}_head", GL20.GL_TRIANGLES, attrs, headMat)
                // Main axe cheek & bearded cutting edge extending downward (-Y)
                BoxShapeBuilder.build(headPart, 0f, -0.07f, 0.45f, 0.034f, 0.19f, 0.15f)
                buildCone(
                    headPart,
                    m.idt().translate(0f, -0.17f, 0.45f).rotate(1f, 0f, 0f, 180f),
                    0.036f, 0.11f, 0.17f, 6
                )
                if (isIron) {
                    val runePart = mb.part("${prefix}_rune", GL20.GL_TRIANGLES, attrs, assets.glowingRuneMaterial)
                    BoxShapeBuilder.build(runePart, 0f, -0.06f, 0.45f, 0.038f, 0.05f, 0.07f)
                }
            }

            "stone_pickaxe", "iron_pickaxe" -> {
                val isIron = toolId == "iron_pickaxe"
                val handlePart = mb.part("${prefix}_shaft", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
                buildCylinder(
                    handlePart,
                    m.idt().translate(0f, 0f, 0.25f).rotate(1f, 0f, 0f, 90f),
                    0.035f, 0.64f, 0.035f, 8
                )
                val collarPart = mb.part("${prefix}_collar", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
                BoxShapeBuilder.build(collarPart, 0f, 0f, 0.48f, 0.054f, 0.058f, 0.07f)

                val headMat = if (isIron) assets.silverArmorMaterial else assets.boulderMaterial
                val pickPart = mb.part("${prefix}_pickhead", GL20.GL_TRIANGLES, attrs, headMat)
                // Cross-bar spanning Y (up/down)
                BoxShapeBuilder.build(pickPart, 0f, 0f, 0.48f, 0.038f, 0.28f, 0.06f)
                // Upper and lower pointed spikes
                buildCone(
                    pickPart,
                    m.idt().translate(0f, -0.21f, 0.48f).rotate(1f, 0f, 0f, 180f),
                    0.044f, 0.16f, 0.054f, 6
                )
                buildCone(
                    pickPart,
                    m.idt().translate(0f, 0.21f, 0.48f),
                    0.044f, 0.16f, 0.054f, 6
                )
                if (isIron) {
                    val runePart = mb.part("${prefix}_rune", GL20.GL_TRIANGLES, attrs, assets.glowingRuneMaterial)
                    BoxShapeBuilder.build(runePart, 0f, 0f, 0.48f, 0.042f, 0.10f, 0.04f)
                }
            }

            "shovel" -> {
                val handlePart = mb.part("${prefix}_shaft", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
                buildCylinder(
                    handlePart,
                    m.idt().translate(0f, 0f, 0.26f).rotate(1f, 0f, 0f, 90f),
                    0.034f, 0.66f, 0.034f, 8
                )
                // D-grip pommel
                BoxShapeBuilder.build(handlePart, 0f, 0f, -0.07f, 0.09f, 0.035f, 0.035f)

                val scoopPart = mb.part("${prefix}_scoop", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
                BoxShapeBuilder.build(scoopPart, 0f, 0f, 0.64f, 0.16f, 0.024f, 0.20f)
                buildCone(
                    scoopPart,
                    m.idt().translate(0f, 0f, 0.78f).rotate(1f, 0f, 0f, 90f),
                    0.15f, 0.10f, 0.026f, 6
                )
            }

            "sickle" -> {
                val handlePart = mb.part("${prefix}_grip", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
                buildCylinder(
                    handlePart,
                    m.idt().translate(0f, 0f, 0.08f).rotate(1f, 0f, 0f, 90f),
                    0.036f, 0.24f, 0.036f, 8
                )
                val guardPart = mb.part("${prefix}_collar", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
                BoxShapeBuilder.build(guardPart, 0f, 0f, 0.20f, 0.055f, 0.055f, 0.035f)

                // Curved crescent harvesting blade built from 3 angled segments
                val bladePart = mb.part("${prefix}_blade", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
                buildBox(bladePart, m.idt().translate(0.03f, 0f, 0.28f).rotate(0f, 1f, 0f, -20f), 0.045f, 0.018f, 0.16f)
                buildBox(bladePart, m.idt().translate(0.09f, 0f, 0.39f).rotate(0f, 1f, 0f, -55f), 0.042f, 0.018f, 0.16f)
                buildCone(
                    bladePart,
                    m.idt().translate(0.04f, 0f, 0.46f).rotate(0f, 1f, 0f, -95f).rotate(1f, 0f, 0f, 90f),
                    0.040f, 0.13f, 0.018f, 6
                )
            }

            "knife" -> {
                val handlePart = mb.part("${prefix}_grip", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
                buildCylinder(
                    handlePart,
                    m.idt().translate(0f, 0f, 0.05f).rotate(1f, 0f, 0f, 90f),
                    0.032f, 0.18f, 0.032f, 8
                )
                val bolsterPart = mb.part("${prefix}_bolster", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
                BoxShapeBuilder.build(bolsterPart, 0f, 0f, 0.14f, 0.065f, 0.032f, 0.025f)

                val bladePart = mb.part("${prefix}_blade", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
                BoxShapeBuilder.build(bladePart, 0f, 0f, 0.25f, 0.018f, 0.046f, 0.20f)
                buildCone(
                    bladePart,
                    m.idt().translate(0f, 0f, 0.39f).rotate(1f, 0f, 0f, 90f),
                    0.018f, 0.10f, 0.046f, 6
                )
            }

            "builders_hammer" -> {
                val handlePart = mb.part("${prefix}_shaft", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
                buildCylinder(
                    handlePart,
                    m.idt().translate(0f, 0f, 0.22f).rotate(1f, 0f, 0f, 90f),
                    0.036f, 0.56f, 0.036f, 8
                )
                val headPart = mb.part("${prefix}_head", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
                BoxShapeBuilder.build(headPart, 0f, 0f, 0.45f, 0.22f, 0.095f, 0.11f)
                val trimPart = mb.part("${prefix}_trim", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
                BoxShapeBuilder.build(trimPart, 0f, 0f, 0.45f, 0.24f, 0.045f, 0.045f)
            }

            "wood" -> {
                val barkPart = mb.part("${prefix}_log", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
                buildCylinder(
                    barkPart,
                    m.idt().translate(0f, 0f, 0.14f).rotate(1f, 0f, 0f, 90f),
                    0.11f, 0.36f, 0.11f, 10
                )
                val corePart = mb.part("${prefix}_core", GL20.GL_TRIANGLES, attrs, assets.mpPotionCorkMaterial)
                buildCylinder(
                    corePart,
                    m.idt().translate(0f, 0f, 0.14f).rotate(1f, 0f, 0f, 90f),
                    0.085f, 0.37f, 0.085f, 8
                )
            }

            "hardwood" -> {
                val barkPart = mb.part("${prefix}_hw", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
                BoxShapeBuilder.build(barkPart, 0f, 0f, 0.15f, 0.12f, 0.12f, 0.36f)
                val ringPart = mb.part("${prefix}_hw_ring", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
                BoxShapeBuilder.build(ringPart, 0f, 0f, 0.24f, 0.13f, 0.13f, 0.04f)
            }

            "branches" -> {
                val branchPart = mb.part("${prefix}_branch", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
                buildCylinder(
                    branchPart,
                    m.idt().translate(0f, 0f, 0.16f).rotate(1f, 0f, 0f, 90f),
                    0.04f, 0.38f, 0.04f, 7
                )
                buildCylinder(
                    branchPart,
                    m.idt().translate(0.05f, 0.02f, 0.24f).rotate(0f, 1f, 0f, 35f).rotate(1f, 0f, 0f, 90f),
                    0.025f, 0.18f, 0.025f, 6
                )
                val leafPart = mb.part("${prefix}_leaf", GL20.GL_TRIANGLES, attrs, assets.grassMaterial)
                buildSphere(leafPart, m.idt().translate(0.08f, 0.03f, 0.32f), 0.09f, 0.06f, 0.09f, 6, 5)
            }

            "roots" -> {
                val rootPart = mb.part("${prefix}_root", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
                buildCone(rootPart, m.idt().translate(0f, 0f, 0.18f).rotate(1f, 0f, 0f, 90f), 0.09f, 0.34f, 0.09f, 7)
                buildCone(rootPart, m.idt().translate(-0.04f, -0.02f, 0.20f).rotate(0f, 1f, 0f, -28f).rotate(1f, 0f, 0f, 90f), 0.05f, 0.22f, 0.05f, 6)
            }

            "stone" -> {
                val stonePart = mb.part("${prefix}_stone", GL20.GL_TRIANGLES, attrs, assets.boulderMaterial)
                buildSphere(stonePart, m.idt().translate(0f, 0f, 0.14f), 0.18f, 0.14f, 0.20f, 6, 5)
            }

            "coal" -> {
                val coalPart = mb.part("${prefix}_coal", GL20.GL_TRIANGLES, attrs, assets.eyeDarkMaterial)
                buildSphere(coalPart, m.idt().translate(0f, 0f, 0.14f), 0.17f, 0.15f, 0.18f, 6, 5)
            }

            "copper_ore" -> {
                val rockPart = mb.part("${prefix}_cu_rock", GL20.GL_TRIANGLES, attrs, assets.boulderMaterial)
                buildSphere(rockPart, m.idt().translate(0f, 0f, 0.14f), 0.17f, 0.14f, 0.18f, 6, 5)
                val veinPart = mb.part("${prefix}_cu_vein", GL20.GL_TRIANGLES, attrs, assets.mpPotionCorkMaterial)
                BoxShapeBuilder.build(veinPart, 0f, 0.03f, 0.16f, 0.12f, 0.08f, 0.12f)
            }

            "iron_ore" -> {
                val rockPart = mb.part("${prefix}_fe_rock", GL20.GL_TRIANGLES, attrs, assets.boulderMaterial)
                buildSphere(rockPart, m.idt().translate(0f, 0f, 0.14f), 0.17f, 0.14f, 0.18f, 6, 5)
                val veinPart = mb.part("${prefix}_fe_vein", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
                BoxShapeBuilder.build(veinPart, 0f, 0.03f, 0.16f, 0.13f, 0.08f, 0.13f)
            }

            "rare_ore" -> {
                val rockPart = mb.part("${prefix}_rare_rock", GL20.GL_TRIANGLES, attrs, assets.boulderMaterial)
                buildSphere(rockPart, m.idt().translate(0f, 0f, 0.14f), 0.16f, 0.14f, 0.16f, 6, 5)
                val crystalPart = mb.part("${prefix}_rare_gem", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
                buildCone(crystalPart, m.idt().translate(0f, 0.06f, 0.18f), 0.10f, 0.18f, 0.10f, 6)
            }

            "plants" -> {
                val herbPart = mb.part("${prefix}_herb", GL20.GL_TRIANGLES, attrs, assets.grassMaterial)
                buildCone(herbPart, m.idt().translate(0f, 0.02f, 0.18f).rotate(1f, 0f, 0f, 75f), 0.12f, 0.28f, 0.06f, 6)
                val glowPart = mb.part("${prefix}_herb_glow", GL20.GL_TRIANGLES, attrs, assets.glowingFernMaterial)
                buildCone(glowPart, m.idt().translate(0.04f, 0.04f, 0.16f).rotate(1f, 0f, 0f, 65f), 0.09f, 0.24f, 0.05f, 6)
            }

            "mushrooms" -> {
                val stemPart = mb.part("${prefix}_mush_stem", GL20.GL_TRIANGLES, attrs, assets.skinMaterial)
                buildCylinder(stemPart, m.idt().translate(0f, -0.02f, 0.14f), 0.06f, 0.16f, 0.06f, 8)
                val capPart = mb.part("${prefix}_mush_cap", GL20.GL_TRIANGLES, attrs, assets.corruptedCrimsonGlowMaterial)
                buildSphere(capPart, m.idt().translate(0f, 0.07f, 0.14f), 0.17f, 0.10f, 0.17f, 8, 6)
            }

            "berries" -> {
                val leafPart = mb.part("${prefix}_berry_leaf", GL20.GL_TRIANGLES, attrs, assets.grassMaterial)
                buildSphere(leafPart, m.idt().translate(0f, 0f, 0.14f), 0.14f, 0.10f, 0.14f, 6, 5)
                val berryPart = mb.part("${prefix}_berry_fruit", GL20.GL_TRIANGLES, attrs, assets.corruptedCrimsonGlowMaterial)
                buildSphere(berryPart, m.idt().translate(0.04f, 0.04f, 0.18f), 0.07f, 0.07f, 0.07f, 6, 5)
                buildSphere(berryPart, m.idt().translate(-0.04f, 0.03f, 0.17f), 0.07f, 0.07f, 0.07f, 6, 5)
            }

            "fibers" -> {
                val fiberPart = mb.part("${prefix}_fiber", GL20.GL_TRIANGLES, attrs, assets.grassMaterial)
                buildCylinder(fiberPart, m.idt().translate(0f, 0f, 0.15f).rotate(1f, 0f, 0f, 90f), 0.08f, 0.26f, 0.08f, 8)
                val bandPart = mb.part("${prefix}_fiber_band", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
                BoxShapeBuilder.build(bandPart, 0f, 0f, 0.15f, 0.095f, 0.095f, 0.05f)
            }

            "animal_hides" -> {
                val hidePart = mb.part("${prefix}_hide", GL20.GL_TRIANGLES, attrs, assets.mpPotionCorkMaterial)
                buildCylinder(hidePart, m.idt().translate(0f, 0f, 0.15f).rotate(1f, 0f, 0f, 90f), 0.10f, 0.28f, 0.10f, 8)
            }

            "monster_essence" -> {
                val essPart = mb.part("${prefix}_essence", GL20.GL_TRIANGLES, attrs, assets.amethystCrystalMaterial)
                buildSphere(essPart, m.idt().translate(0f, 0f, 0.15f), 0.16f, 0.16f, 0.16f, 8, 8)
            }

            "magic_crystals" -> {
                val crysPart = mb.part("${prefix}_mcrys", GL20.GL_TRIANGLES, attrs, assets.manaCrystalMaterial)
                buildCone(crysPart, m.idt().translate(0f, 0.06f, 0.16f), 0.12f, 0.20f, 0.12f, 6)
                buildCone(crysPart, m.idt().translate(0f, -0.06f, 0.16f).rotate(1f, 0f, 0f, 180f), 0.12f, 0.14f, 0.12f, 6)
            }

            "ancient_relics" -> {
                val relicPart = mb.part("${prefix}_relic", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
                BoxShapeBuilder.build(relicPart, 0f, 0f, 0.16f, 0.14f, 0.20f, 0.08f)
                val corePart = mb.part("${prefix}_relic_core", GL20.GL_TRIANGLES, attrs, assets.glowingRuneMaterial)
                BoxShapeBuilder.build(corePart, 0f, 0f, 0.16f, 0.07f, 0.10f, 0.095f)
            }

            "boss_materials" -> {
                val bossPart = mb.part("${prefix}_boss_mat", GL20.GL_TRIANGLES, attrs, assets.corruptedCrimsonGlowMaterial)
                buildCone(bossPart, m.idt().translate(0f, 0f, 0.18f).rotate(1f, 0f, 0f, 90f), 0.13f, 0.28f, 0.13f, 6)
            }

            "green_crystal" -> {
                val gcPart = mb.part("${prefix}_gcrys", GL20.GL_TRIANGLES, attrs, assets.slimeCrystalMaterial)
                buildCone(gcPart, m.idt().translate(0f, 0.06f, 0.16f), 0.12f, 0.22f, 0.12f, 6)
                buildCone(gcPart, m.idt().translate(0f, -0.06f, 0.16f).rotate(1f, 0f, 0f, 180f), 0.12f, 0.14f, 0.12f, 6)
            }

            "mp_potion", "health_potion", "berry_brew", "mana_essence_tonic", "herbal_salve" -> {
                val liquidMat = when (toolId) {
                    "mp_potion" -> assets.mpPotionLiquidMaterial
                    "health_potion" -> assets.healthPotionLiquidMaterial
                    "berry_brew" -> assets.glowingRuneMaterial
                    "mana_essence_tonic" -> assets.amethystCrystalMaterial
                    else -> assets.slimeCrystalMaterial
                }
                val liqPart = mb.part("${prefix}_pot_liq", GL20.GL_TRIANGLES, attrs, liquidMat)
                buildSphere(liqPart, m.idt().translate(0f, -0.02f, 0.15f), 0.13f, 0.13f, 0.13f, 8, 8)
                val glassPart = mb.part("${prefix}_pot_glass", GL20.GL_TRIANGLES, attrs, assets.mpPotionGlassMaterial)
                buildSphere(glassPart, m.idt().translate(0f, -0.02f, 0.15f), 0.15f, 0.15f, 0.15f, 8, 8)
                buildCylinder(glassPart, m.idt().translate(0f, 0.08f, 0.15f), 0.06f, 0.08f, 0.06f, 8)
                val corkPart = mb.part("${prefix}_pot_cork", GL20.GL_TRIANGLES, attrs, assets.mpPotionCorkMaterial)
                buildCylinder(corkPart, m.idt().translate(0f, 0.13f, 0.15f), 0.05f, 0.04f, 0.05f, 6)
            }

            "roasted_mushrooms" -> {
                val stickPart = mb.part("${prefix}_skewer", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
                buildCylinder(stickPart, m.idt().translate(0f, 0f, 0.16f).rotate(1f, 0f, 0f, 90f), 0.025f, 0.30f, 0.025f, 6)
                val roastPart = mb.part("${prefix}_roast_cap", GL20.GL_TRIANGLES, attrs, assets.mpPotionCorkMaterial)
                buildSphere(roastPart, m.idt().translate(0f, 0f, 0.16f), 0.12f, 0.10f, 0.12f, 7, 6)
                buildSphere(roastPart, m.idt().translate(0f, 0f, 0.25f), 0.11f, 0.09f, 0.11f, 7, 6)
            }
        }
    }

    /**
     * Creates a standalone 3D held item or tool model for first-person viewmodel rendering and inspection.
     */
    fun createHeldToolModel(toolId: String): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()
        val node = mb.node()
        node.id = "tool_root_$toolId"
        buildToolPartsIntoCurrentNode(mb, m, toolId, "fp_$toolId")
        return assets.registerModel(mb.end())
    }

    fun createHeldItemModel(itemId: String): Model = createHeldToolModel(itemId)

    /**
     * Creates a lush 3D Wild Berry Bush model studded with glowing magenta-crimson berries.
     */
    fun createBerryBushModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val foliagePart = mb.part("berry_bush_foliage", GL20.GL_TRIANGLES, attrs, assets.canopyCyanMaterial)
        buildSphere(foliagePart, m.idt().translate(0f, 0.42f, 0f), 0.95f, 0.72f, 0.95f, 10, 8)
        buildSphere(foliagePart, m.idt().translate(-0.28f, 0.34f, 0.22f), 0.68f, 0.56f, 0.68f, 8, 8)
        buildSphere(foliagePart, m.idt().translate(0.30f, 0.36f, -0.20f), 0.65f, 0.54f, 0.65f, 8, 8)

        val berryMat = Material(
            ColorAttribute.createDiffuse(Color(0.92f, 0.22f, 0.56f, 1f)),
            ColorAttribute.createEmissive(0.45f, 0.08f, 0.25f, 1f)
        )
        val berryPart = mb.part("berry_fruits", GL20.GL_TRIANGLES, attrs, berryMat)
        val offsets = arrayOf(
            floatArrayOf(0.28f, 0.55f, 0.32f),
            floatArrayOf(-0.32f, 0.48f, 0.28f),
            floatArrayOf(0.12f, 0.68f, -0.26f),
            floatArrayOf(-0.18f, 0.62f, -0.30f),
            floatArrayOf(0.36f, 0.42f, -0.12f),
            floatArrayOf(0.0f, 0.74f, 0.18f)
        )
        for (pos in offsets) {
            buildSphere(berryPart, m.idt().translate(pos[0], pos[1], pos[2]), 0.14f, 0.14f, 0.14f, 6, 6)
        }

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a 3D Forest Mushroom Cluster model with pale stems and glowing spotted caps.
     */
    fun createMushroomClusterModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val stemPart = mb.part("mushroom_stems", GL20.GL_TRIANGLES, attrs, assets.skinMaterial)
        buildCylinder(stemPart, m.idt().translate(0f, 0.22f, 0f), 0.12f, 0.44f, 0.12f, 8)
        buildCylinder(stemPart, m.idt().translate(-0.26f, 0.16f, 0.18f).rotate(0f, 0f, 1f, 12f), 0.09f, 0.32f, 0.09f, 8)
        buildCylinder(stemPart, m.idt().translate(0.24f, 0.14f, -0.16f).rotate(0f, 0f, 1f, -10f), 0.08f, 0.28f, 0.08f, 8)

        val capMat = Material(
            ColorAttribute.createDiffuse(Color(0.52f, 0.82f, 0.22f, 1f)),
            ColorAttribute.createEmissive(0.18f, 0.36f, 0.08f, 1f)
        )
        val capPart = mb.part("mushroom_caps", GL20.GL_TRIANGLES, attrs, capMat)
        buildCone(capPart, m.idt().translate(0f, 0.48f, 0f), 0.42f, 0.24f, 0.42f, 10)
        buildCone(capPart, m.idt().translate(-0.28f, 0.34f, 0.18f), 0.30f, 0.18f, 0.30f, 8)
        buildCone(capPart, m.idt().translate(0.25f, 0.30f, -0.16f), 0.28f, 0.16f, 0.28f, 8)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a 3D Root & Soil Patch model with exposed gnarled roots and shoots for shovel digging.
     */
    fun createRootPatchModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val moundPart = mb.part("root_mound", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
        buildSphere(moundPart, m.idt().translate(0f, 0.08f, 0f), 0.85f, 0.28f, 0.85f, 10, 8)
        buildCylinder(moundPart, m.idt().translate(-0.18f, 0.22f, 0.10f).rotate(0f, 0f, 1f, 38f), 0.08f, 0.48f, 0.08f, 6)
        buildCylinder(moundPart, m.idt().translate(0.20f, 0.20f, -0.12f).rotate(0f, 0f, 1f, -42f), 0.07f, 0.44f, 0.07f, 6)
        buildCylinder(moundPart, m.idt().translate(0.04f, 0.24f, 0.22f).rotate(1f, 0f, 0f, 35f), 0.07f, 0.40f, 0.07f, 6)

        val shootPart = mb.part("root_shoots", GL20.GL_TRIANGLES, attrs, assets.glowingFernMaterial)
        buildCone(shootPart, m.idt().translate(0f, 0.32f, 0f), 0.18f, 0.28f, 0.18f, 6)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates an attractive 3D Green Slime monster model:
     * - Rounded, soft gelatinous emerald body with translucent sheen
     * - Pulsating, glowing neon green inner energy core
     * - Expressive cute eyes with white sclera and glossy dark pupils
     * - Distinct top droplet crown tip that wobbles during motion
     */
    fun createGreenSlimeModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        // Root / Pelvis Node
        val rootNode: Node = mb.node()
        rootNode.id = "slimeRoot"
        rootNode.translation.set(0f, 0.05f, 0f)

        // Main Gelatinous Body Node
        val bodyNode: Node = mb.node()
        bodyNode.id = "slimeBody"
        bodyNode.translation.set(0f, 0f, 0f)
        run {
            val bodyPart = mb.part("slime_gel_body", GL20.GL_TRIANGLES, attrs, assets.slimeBodyMaterial)
            // Lower rounded base
            buildSphere(bodyPart, m.idt().translate(0f, 0.24f, 0f), 0.94f, 0.46f, 0.94f, 16, 12)
            // Middle dome body
            buildSphere(bodyPart, m.idt().translate(0f, 0.44f, 0f), 0.82f, 0.62f, 0.82f, 16, 12)
            // Upper crown droplet tip
            buildCone(bodyPart, m.idt().translate(0f, 0.76f, 0f), 0.24f, 0.32f, 0.24f, 10)
        }

        // Inner Glowing Green Energy Core Node
        val coreNode: Node = mb.node()
        coreNode.id = "slimeCore"
        coreNode.translation.set(0f, 0.36f, 0f)
        run {
            val corePart = mb.part("slime_inner_core", GL20.GL_TRIANGLES, attrs, assets.slimeCoreMaterial)
            buildSphere(corePart, m.idt(), 0.30f, 0.30f, 0.30f, 10, 8)
            buildCone(corePart, m.idt().translate(0f, 0.12f, 0f), 0.20f, 0.18f, 0.20f, 6)
            buildCone(corePart, m.idt().translate(0f, -0.12f, 0f).rotate(1f, 0f, 0f, 180f), 0.20f, 0.18f, 0.20f, 6)
        }

        // Left Eye Node
        val leftEyeNode: Node = mb.node()
        leftEyeNode.id = "slimeEyeLeft"
        leftEyeNode.translation.set(-0.16f, 0.42f, 0.35f)
        run {
            val eyeWhitePart = mb.part("slime_eye_white_l", GL20.GL_TRIANGLES, attrs, assets.slimeEyeWhiteMaterial)
            buildSphere(eyeWhitePart, m.idt(), 0.13f, 0.16f, 0.10f, 10, 8)
            buildSphere(eyeWhitePart, m.idt().translate(0.015f, 0.03f, 0.055f), 0.035f, 0.035f, 0.035f, 6, 4) // Specular dot

            val eyePupilPart = mb.part("slime_eye_pupil_l", GL20.GL_TRIANGLES, attrs, assets.slimeEyePupilMaterial)
            buildSphere(eyePupilPart, m.idt().translate(0f, 0f, 0.045f), 0.075f, 0.10f, 0.05f, 8, 6)
        }

        // Right Eye Node
        val rightEyeNode: Node = mb.node()
        rightEyeNode.id = "slimeEyeRight"
        rightEyeNode.translation.set(0.16f, 0.42f, 0.35f)
        run {
            val eyeWhitePart = mb.part("slime_eye_white_r", GL20.GL_TRIANGLES, attrs, assets.slimeEyeWhiteMaterial)
            buildSphere(eyeWhitePart, m.idt(), 0.13f, 0.16f, 0.10f, 10, 8)
            buildSphere(eyeWhitePart, m.idt().translate(0.015f, 0.03f, 0.055f), 0.035f, 0.035f, 0.035f, 6, 4) // Specular dot

            val eyePupilPart = mb.part("slime_eye_pupil_r", GL20.GL_TRIANGLES, attrs, assets.slimeEyePupilMaterial)
            buildSphere(eyePupilPart, m.idt().translate(0f, 0f, 0.045f), 0.075f, 0.10f, 0.05f, 8, 6)
        }

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a glowing Green Energy Orb 3D projectile model:
     * - Central dense energy plasma sphere
     * - Surrounding rotating magical corona facets
     */
    fun createGreenEnergyOrbModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val orbPart = mb.part("slime_orb_sphere", GL20.GL_TRIANGLES, attrs, assets.slimeOrbMaterial)
        buildSphere(orbPart, m.idt(), 0.48f, 0.48f, 0.48f, 12, 10)
        buildCone(orbPart, m.idt().translate(0f, 0.22f, 0f), 0.34f, 0.22f, 0.34f, 8)
        buildCone(orbPart, m.idt().translate(0f, -0.22f, 0f).rotate(1f, 0f, 0f, 180f), 0.34f, 0.22f, 0.34f, 8)

        val corePart = mb.part("slime_orb_core", GL20.GL_TRIANGLES, attrs, assets.slimeCoreMaterial)
        buildBox(corePart, m.idt().rotate(1f, 1f, 0f, 45f), 0.24f, 0.24f, 0.24f)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a beautiful 3D emerald-green crystal pickup model:
     * - Faceted central bipyramid crystal
     * - Offset satellite crystal shards with glowing starlight core
     */
    fun createGreenCrystalModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val crystalPart = mb.part("emerald_crystal", GL20.GL_TRIANGLES, attrs, assets.slimeCrystalMaterial)
        // Main crystal spire
        buildCone(crystalPart, m.idt().translate(0f, 0.24f, 0f), 0.26f, 0.46f, 0.26f, 6)
        buildCone(crystalPart, m.idt().translate(0f, -0.06f, 0f).rotate(1f, 0f, 0f, 180f), 0.26f, 0.32f, 0.26f, 6)

        // Satellite shards
        buildCone(crystalPart, m.idt().translate(-0.13f, 0.08f, 0.05f).rotate(0f, 0f, 1f, -24f), 0.13f, 0.26f, 0.13f, 5)
        buildCone(crystalPart, m.idt().translate(0.12f, 0.06f, -0.05f).rotate(0f, 0f, 1f, 22f), 0.12f, 0.22f, 0.12f, 5)

        // Inner glowing core
        val corePart = mb.part("emerald_crystal_core", GL20.GL_TRIANGLES, attrs, assets.slimeCoreMaterial)
        buildBox(corePart, m.idt().translate(0f, 0.10f, 0f).rotate(0f, 1f, 0f, 45f), 0.10f, 0.18f, 0.10f)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a 3D dark green grass patch with a lush cluster of varied dark/medium grass blades.
     */
    fun createDarkGrassPatchModel(variant: Int = 0): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val patchPart = mb.part("dark_grass_patch_$variant", GL20.GL_TRIANGLES, attrs, assets.darkGrassPatchMaterial)
        val radiusX = if (variant % 2 == 0) 1.45f else 1.85f
        val radiusZ = if (variant % 2 == 0) 1.65f else 1.35f
        buildCylinder(patchPart, m.idt().translate(0f, 0.015f, 0f), radiusX, 0.03f, radiusZ, 10)

        // Cluster of dark and natural green blades rising from the patch
        val bladePart = mb.part("dark_grass_blades_$variant", GL20.GL_TRIANGLES, attrs, assets.grassMaterial)
        val bladeCount = 9
        for (i in 0 until bladeCount) {
            val angleDeg = i * (360f / bladeCount) + variant * 19f
            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val dist = (0.15f + (i % 3) * 0.22f)
            val bx = cos(rad) * dist
            val bz = sin(rad) * dist
            val bh = 0.34f + (i % 3) * 0.14f
            m.idt()
                .translate(bx, bh * 0.45f, bz)
                .rotate(0f, 1f, 0f, angleDeg)
                .rotate(1f, 0f, 0f, 14f + (i % 3) * 6f)
            buildCone(if (i % 2 == 0) patchPart else bladePart, m, 0.10f, bh, 0.05f, 5)
        }

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a subtle 3D natural dirt/soil ground patch with tiny embedded stones.
     */
    fun createDirtPatchModel(variant: Int = 0): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val dirtPart = mb.part("dirt_patch_$variant", GL20.GL_TRIANGLES, attrs, assets.dirtPatchMaterial)
        val w = if (variant % 2 == 0) 1.6f else 2.1f
        val d = if (variant % 2 == 0) 1.9f else 1.5f
        buildCylinder(dirtPart, m.idt().translate(0f, 0.01f, 0f), w, 0.024f, d, 10)

        val pebblePart = mb.part("dirt_pebbles_$variant", GL20.GL_TRIANGLES, attrs, assets.boulderMaterial)
        buildSphere(pebblePart, m.idt().translate(0.32f, 0.03f, 0.18f), 0.14f, 0.07f, 0.12f, 6, 5)
        buildSphere(pebblePart, m.idt().translate(-0.28f, 0.025f, -0.24f), 0.11f, 0.06f, 0.10f, 6, 5)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates an articulated 3D Blue Flower Monster model:
     * - Leafy green root base ("flowerRoot") and swaying stalk ("flowerStem")
     * - Blooming azure/cobalt flower head ("flowerHead") with articulated petals ("flowerPetals")
     *   and glowing cyan/sapphire stigma mana core & expressive eyes ("flowerFace")
     * - Supports DORMANT (ordinary closed flower in grass) and EMERGING (roots/stem rise and petals open)
     */
    fun createBlueFlowerMonsterModel(): Model {
        return createFlowerMonsterModel(
            petalMaterial = assets.blueFlowerPetalMaterial,
            coreMaterial = assets.blueFlowerCoreMaterial,
            prefix = "blue_flower"
        )
    }

    /**
     * Creates an articulated 3D Red Flower Monster model:
     * - Leafy green root base ("flowerRoot") and swaying stalk ("flowerStem")
     * - Blooming crimson-scarlet flower head ("flowerHead") with articulated petals ("flowerPetals")
     *   and glowing fiery core & expressive eyes ("flowerFace")
     * - Supports DORMANT (ordinary red fantasy flower in grass) and EMERGING (roots/stem rise and petals open)
     */
    fun createRedFlowerMonsterModel(): Model {
        return createFlowerMonsterModel(
            petalMaterial = assets.redFlowerPetalMaterial,
            coreMaterial = assets.redFlowerCoreMaterial,
            prefix = "red_flower"
        )
    }

    private fun createFlowerMonsterModel(
        petalMaterial: Material,
        coreMaterial: Material,
        prefix: String
    ): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val rootNode: Node = mb.node()
        rootNode.id = "flowerRoot"
        rootNode.translation.set(0f, 0.05f, 0f)
        run {
            val basePart = mb.part("${prefix}_base", GL20.GL_TRIANGLES, attrs, assets.blueFlowerStemMaterial)
            buildSphere(basePart, m.idt().translate(0f, 0.16f, 0f), 0.62f, 0.32f, 0.62f, 10, 8)
            // Base leaves
            for (i in 0 until 4) {
                val deg = i * 90f + 45f
                val rad = Math.toRadians(deg.toDouble()).toFloat()
                m.idt()
                    .translate(cos(rad) * 0.34f, 0.18f, sin(rad) * 0.34f)
                    .rotate(0f, 1f, 0f, -deg + 90f)
                    .rotate(1f, 0f, 0f, 65f)
                buildCone(basePart, m, 0.24f, 0.48f, 0.08f, 6)
            }
        }

        val stemNode: Node = mb.node()
        stemNode.id = "flowerStem"
        stemNode.translation.set(0f, 0.24f, 0f)
        run {
            val stemPart = mb.part("${prefix}_stem", GL20.GL_TRIANGLES, attrs, assets.blueFlowerStemMaterial)
            buildCylinder(stemPart, m.idt().translate(0f, 0.42f, 0f), 0.22f, 0.84f, 0.22f, 10)
            // Side stem leaves
            buildCone(
                stemPart,
                m.idt().translate(-0.24f, 0.44f, 0f).rotate(0f, 0f, 1f, 58f),
                0.18f, 0.42f, 0.08f, 6
            )
            buildCone(
                stemPart,
                m.idt().translate(0.24f, 0.52f, 0f).rotate(0f, 0f, 1f, -58f),
                0.18f, 0.42f, 0.08f, 6
            )
        }

        val headNode: Node = mb.node()
        headNode.id = "flowerHead"
        headNode.translation.set(0f, 0.86f, 0f)
        run {
            val calyxPart = mb.part("${prefix}_calyx", GL20.GL_TRIANGLES, attrs, assets.blueFlowerStemMaterial)
            buildSphere(calyxPart, m.idt().translate(0f, 0.06f, 0f), 0.34f, 0.22f, 0.30f, 8, 6)
        }

        val petalsNode: Node = mb.node()
        petalsNode.id = "flowerPetals"
        petalsNode.translation.set(0f, 0.12f, 0.06f)
        run {
            val petalPart = mb.part("${prefix}_petals", GL20.GL_TRIANGLES, attrs, petalMaterial)
            val petalCount = 8
            for (i in 0 until petalCount) {
                val angleDeg = i * (360f / petalCount)
                val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
                val px = cos(rad) * 0.34f
                val py = sin(rad) * 0.34f
                m.idt()
                    .translate(px, py, 0f)
                    .rotate(0f, 0f, 1f, angleDeg - 90f)
                    .rotate(1f, 0f, 0f, -18f)
                buildCone(petalPart, m, 0.26f, 0.46f, 0.09f, 7)
            }
        }

        val faceNode: Node = mb.node()
        faceNode.id = "flowerFace"
        faceNode.translation.set(0f, 0.12f, 0.12f)
        run {
            val corePart = mb.part("${prefix}_core", GL20.GL_TRIANGLES, attrs, coreMaterial)
            buildSphere(corePart, m.idt(), 0.42f, 0.42f, 0.28f, 12, 10)

            val eyePart = mb.part("${prefix}_eyes", GL20.GL_TRIANGLES, attrs, assets.slimeEyePupilMaterial)
            buildSphere(eyePart, m.idt().translate(-0.09f, 0.04f, 0.13f), 0.065f, 0.085f, 0.04f, 8, 6)
            buildSphere(eyePart, m.idt().translate(0.09f, 0.04f, 0.13f), 0.065f, 0.085f, 0.04f, 8, 6)
        }

        val model = mb.end()
        model.nodes.clear()
        model.nodes.add(rootNode)
        rootNode.addChild(stemNode)
        stemNode.addChild(headNode)
        headNode.addChild(petalsNode)
        headNode.addChild(faceNode)
        model.calculateTransforms()
        return assets.registerModel(model)
    }

    /**
     * Creates a glowing Blue Spore / Mana Orb projectile fired by the Blue Flower Monster.
     */
    fun createBlueFlowerSporeOrbModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val orbPart = mb.part("flower_orb", GL20.GL_TRIANGLES, attrs, assets.blueFlowerSporeMaterial)
        buildSphere(orbPart, m.idt(), 0.42f, 0.42f, 0.42f, 10, 8)
        val corePart = mb.part("flower_orb_core", GL20.GL_TRIANGLES, attrs, assets.blueFlowerCoreMaterial)
        buildBox(corePart, m.idt().rotate(1f, 1f, 0f, 45f), 0.22f, 0.22f, 0.22f)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a glowing Crimson-Scarlet Spore Orb projectile fired by the Red Flower Monster.
     */
    fun createRedFlowerSporeOrbModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val orbPart = mb.part("red_flower_orb", GL20.GL_TRIANGLES, attrs, assets.redFlowerSporeMaterial)
        buildSphere(orbPart, m.idt(), 0.38f, 0.38f, 0.38f, 10, 8)
        val corePart = mb.part("red_flower_orb_core", GL20.GL_TRIANGLES, attrs, assets.redFlowerCoreMaterial)
        buildBox(corePart, m.idt().rotate(1f, 1f, 0f, 45f), 0.20f, 0.20f, 0.20f)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a 3D MP Potion bottle model for world drops and pickups:
     * - Rounded crystal glass flask
     * - Glowing azure mana liquid inside
     * - Silver collar ring and cork stopper
     */
    fun createMpPotionModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        // Inner glowing blue MP liquid
        val liquidPart = mb.part("mp_potion_liquid", GL20.GL_TRIANGLES, attrs, assets.mpPotionLiquidMaterial)
        buildSphere(liquidPart, m.idt().translate(0f, 0.18f, 0f), 0.30f, 0.28f, 0.30f, 12, 10)

        // Outer translucent glass flask & neck
        val glassPart = mb.part("mp_potion_glass", GL20.GL_TRIANGLES, attrs, assets.mpPotionGlassMaterial)
        buildSphere(glassPart, m.idt().translate(0f, 0.19f, 0f), 0.35f, 0.34f, 0.35f, 12, 10)
        buildCylinder(glassPart, m.idt().translate(0f, 0.40f, 0f), 0.13f, 0.16f, 0.13f, 10)

        // Silver collar rim
        val collarPart = mb.part("mp_potion_collar", GL20.GL_TRIANGLES, attrs, assets.silverArmorMaterial)
        buildCylinder(collarPart, m.idt().translate(0f, 0.46f, 0f), 0.16f, 0.04f, 0.16f, 10)

        // Cork stopper
        val corkPart = mb.part("mp_potion_cork", GL20.GL_TRIANGLES, attrs, assets.mpPotionCorkMaterial)
        buildCylinder(corkPart, m.idt().translate(0f, 0.51f, 0f), 0.11f, 0.08f, 0.11f, 8)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a 3D Health Potion bottle model for world drops and pickups:
     * - Rounded crystal glass flask
     * - Glowing ruby-crimson health liquid inside
     * - Gold collar ring and cork stopper
     */
    fun createHealthPotionModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        // Inner glowing crimson Health liquid
        val liquidPart = mb.part("hp_potion_liquid", GL20.GL_TRIANGLES, attrs, assets.healthPotionLiquidMaterial)
        buildSphere(liquidPart, m.idt().translate(0f, 0.18f, 0f), 0.30f, 0.28f, 0.30f, 12, 10)

        // Outer translucent glass flask & neck
        val glassPart = mb.part("hp_potion_glass", GL20.GL_TRIANGLES, attrs, assets.mpPotionGlassMaterial)
        buildSphere(glassPart, m.idt().translate(0f, 0.19f, 0f), 0.35f, 0.34f, 0.35f, 12, 10)
        buildCylinder(glassPart, m.idt().translate(0f, 0.40f, 0f), 0.13f, 0.16f, 0.13f, 10)

        // Gold collar rim
        val collarPart = mb.part("hp_potion_collar", GL20.GL_TRIANGLES, attrs, assets.goldTrimMaterial)
        buildCylinder(collarPart, m.idt().translate(0f, 0.46f, 0f), 0.16f, 0.04f, 0.16f, 10)

        // Cork stopper
        val corkPart = mb.part("hp_potion_cork", GL20.GL_TRIANGLES, attrs, assets.mpPotionCorkMaterial)
        buildCylinder(corkPart, m.idt().translate(0f, 0.51f, 0f), 0.11f, 0.08f, 0.11f, 8)

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a realistic 3D Wood Log pickup model with textured bark and exposed timber grain ends.
     */
    fun createWoodLogPickupModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val barkPart = mb.part("pickup_wood_bark", GL20.GL_TRIANGLES, attrs, assets.barkMaterial)
        buildCylinder(
            barkPart,
            m.idt().translate(0f, 0.16f, 0f).rotate(0f, 0f, 1f, 90f),
            0.24f, 0.68f, 0.24f, 10
        )

        val corePart = mb.part("pickup_wood_core", GL20.GL_TRIANGLES, attrs, assets.mpPotionCorkMaterial)
        buildCylinder(
            corePart,
            m.idt().translate(0f, 0.16f, 0f).rotate(0f, 0f, 1f, 90f),
            0.19f, 0.70f, 0.19f, 8
        )

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a jagged 3D Stone Fragment / Rock Chunk model used for falling rock debris and ground stone pickups.
     */
    fun createStoneFragmentModel(variant: Int = 0): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val rockPart = mb.part("stone_fragment_$variant", GL20.GL_TRIANGLES, attrs, assets.boulderMaterial)
        val sx = if (variant % 2 == 0) 0.36f else 0.42f
        val sy = if (variant % 3 == 0) 0.28f else 0.32f
        val sz = if (variant % 2 == 0) 0.40f else 0.34f
        buildSphere(rockPart, m.idt().translate(0f, sy * 0.5f, 0f), sx, sy, sz, 6, 5)
        buildBox(
            rockPart,
            m.idt().translate(0.05f, sy * 0.45f, -0.04f).rotate(1f, 1f, 0f, 28f + variant * 15f),
            sx * 0.72f, sy * 0.68f, sz * 0.72f
        )

        return assets.registerModel(mb.end())
    }

    /**
     * Creates a 3D progressive fracture / crack seam overlay model that wraps around a rock as it is mined.
     */
    fun createRockCrackOverlayModel(stage: Int): Model {
        val mb = ModelBuilder()
        mb.begin()
        val m = Matrix4()

        val crackPart = mb.part("rock_crack_stage_$stage", GL20.GL_TRIANGLES, attrs, assets.crackedRockLineMaterial)
        // Primary diagonal fracture seams visible on outer boulder surface
        buildBox(crackPart, m.idt().translate(0f, 0.55f, 0f).rotate(0f, 1f, 0f, 25f).rotate(0f, 0f, 1f, 32f), 1.38f, 0.055f, 1.38f)
        buildBox(crackPart, m.idt().translate(0f, 0.48f, 0f).rotate(0f, 1f, 0f, -40f).rotate(1f, 0f, 0f, 28f), 1.36f, 0.055f, 1.36f)
        if (stage >= 2) {
            // Secondary deep spiderweb cracks for heavily damaged stage
            buildBox(crackPart, m.idt().translate(0f, 0.64f, 0f).rotate(0f, 1f, 0f, 70f).rotate(0f, 0f, 1f, -38f), 1.42f, 0.065f, 1.42f)
            buildBox(crackPart, m.idt().translate(0f, 0.36f, 0f).rotate(1f, 0f, 1f, 45f), 1.40f, 0.06f, 1.40f)
        }

        return assets.registerModel(mb.end())
    }
}
