package com.aethelgard.survival.core.assets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.sin

/**
 * Central procedural asset manager for AethelgardSurvival.
 * Generates stylized anime-fantasy textures and materials entirely in code
 * with zero external asset files required, and tracks all GPU resources for disposal.
 */
class GameAssetManager : Disposable {

    private val textures = mutableListOf<Texture>()
    private val models = mutableListOf<Model>()

    lateinit var terrainTexture: Texture
        private set
    lateinit var barkTexture: Texture
        private set
    lateinit var canopyCyanTexture: Texture
        private set
    lateinit var canopyVioletTexture: Texture
        private set
    lateinit var stoneRuneTexture: Texture
        private set
    lateinit var cloakTexture: Texture
        private set
    lateinit var hairTexture: Texture
        private set
    lateinit var boulderTexture: Texture
        private set

    lateinit var terrainMaterial: Material
        private set
    lateinit var barkMaterial: Material
        private set
    lateinit var canopyCyanMaterial: Material
        private set
    lateinit var canopyVioletMaterial: Material
        private set
    lateinit var stoneMonumentMaterial: Material
        private set
    lateinit var glowingRuneMaterial: Material
        private set
    lateinit var manaCrystalMaterial: Material
        private set
    lateinit var amethystCrystalMaterial: Material
        private set
    lateinit var goldTrimMaterial: Material
        private set
    lateinit var boulderMaterial: Material
        private set
    lateinit var grassMaterial: Material
        private set
    lateinit var glowingFernMaterial: Material
        private set
    lateinit var skyDomeMaterial: Material
        private set
    lateinit var skySunMaterial: Material
        private set
    lateinit var skyMoonMaterial: Material
        private set
    lateinit var skyStarsMaterial: Material
        private set

    // Anime protagonist materials
    lateinit var skinMaterial: Material
        private set
    lateinit var hairMaterial: Material
        private set
    lateinit var cloakMaterial: Material
        private set
    lateinit var tunicMaterial: Material
        private set
    lateinit var silverArmorMaterial: Material
        private set
    lateinit var bootMaterial: Material
        private set
    lateinit var eyeIrisMaterial: Material
        private set
    lateinit var eyeDarkMaterial: Material
        private set
    lateinit var eyeHighlightMaterial: Material
        private set

    // Milestone 2: Combat & Corrupted Guardian Enemy Materials
    lateinit var corruptedArmorMaterial: Material
        private set
    lateinit var corruptedMantleMaterial: Material
        private set
    lateinit var corruptedCrimsonGlowMaterial: Material
        private set
    lateinit var corruptedBladeMaterial: Material
        private set
    lateinit var energyBlastMaterial: Material
        private set
    lateinit var arcaneBoltMaterial: Material
        private set
    lateinit var slashWaveMaterial: Material
        private set
    lateinit var goldImpactMaterial: Material
        private set

    // Milestone 3: Advanced Anime Sword Combat Materials
    lateinit var swordTrailCyanMaterial: Material
        private set
    lateinit var swordTrailFireMaterial: Material
        private set
    lateinit var afterimageGhostMaterial: Material
        private set
    lateinit var infernoFlameMaterial: Material
        private set
    lateinit var windStreakMaterial: Material
        private set
    lateinit var groundDustMaterial: Material
        private set

    // Milestone 4: Advanced Anime Fists Combat Materials
    lateinit var fistWindTrailMaterial: Material
        private set
    lateinit var fistChiGoldMaterial: Material
        private set
    lateinit var martialAfterimageMaterial: Material
        private set

    // Green Slime & Emerald Crystal Materials
    lateinit var slimeBodyMaterial: Material
        private set
    lateinit var slimeCoreMaterial: Material
        private set
    lateinit var slimeEyeWhiteMaterial: Material
        private set
    lateinit var slimeEyePupilMaterial: Material
        private set
    lateinit var slimeOrbMaterial: Material
        private set
    lateinit var slimeCrystalMaterial: Material
        private set

    // Ground Detail Materials (Dark Green Grass Patches & Earthy Dirt Patches)
    lateinit var darkGrassPatchMaterial: Material
        private set
    lateinit var dirtPatchMaterial: Material
        private set

    // Blue Flower Monster & MP Potion Materials
    lateinit var blueFlowerPetalMaterial: Material
        private set
    lateinit var blueFlowerCoreMaterial: Material
        private set
    lateinit var blueFlowerStemMaterial: Material
        private set
    lateinit var blueFlowerSporeMaterial: Material
        private set
    lateinit var mpPotionGlassMaterial: Material
        private set
    lateinit var mpPotionLiquidMaterial: Material
        private set
    lateinit var mpPotionCorkMaterial: Material
        private set
    lateinit var redFlowerPetalMaterial: Material
        private set
    lateinit var redFlowerCoreMaterial: Material
        private set
    lateinit var redFlowerSporeMaterial: Material
        private set
    lateinit var healthPotionLiquidMaterial: Material
        private set
    lateinit var crackedRockLineMaterial: Material
        private set

    fun initialize() {
        terrainTexture = registerTexture(createTerrainPixmap(128))
        barkTexture = registerTexture(createBarkPixmap(64))
        canopyCyanTexture = registerTexture(createCanopyPixmap(64, isCyan = true))
        canopyVioletTexture = registerTexture(createCanopyPixmap(64, isCyan = false))
        stoneRuneTexture = registerTexture(createStoneRunePixmap(128))
        cloakTexture = registerTexture(createCloakPixmap(64))
        hairTexture = registerTexture(createHairPixmap(64))
        boulderTexture = registerTexture(createBoulderPixmap(64))

        terrainMaterial = Material(
            TextureAttribute.createDiffuse(terrainTexture),
            ColorAttribute.createSpecular(0.12f, 0.18f, 0.09f, 1f),
            FloatAttribute.createShininess(4f)
        )

        barkMaterial = Material(
            TextureAttribute.createDiffuse(barkTexture),
            ColorAttribute.createSpecular(0.14f, 0.11f, 0.08f, 1f),
            FloatAttribute.createShininess(6f)
        )

        canopyCyanMaterial = Material(
            TextureAttribute.createDiffuse(canopyCyanTexture),
            ColorAttribute.createEmissive(0.08f, 0.24f, 0.09f, 1f),
            ColorAttribute.createSpecular(0.22f, 0.45f, 0.20f, 1f),
            FloatAttribute.createShininess(12f)
        )

        canopyVioletMaterial = Material(
            TextureAttribute.createDiffuse(canopyVioletTexture),
            ColorAttribute.createEmissive(0.22f, 0.14f, 0.04f, 1f),
            ColorAttribute.createSpecular(0.45f, 0.35f, 0.16f, 1f),
            FloatAttribute.createShininess(12f)
        )

        stoneMonumentMaterial = Material(
            TextureAttribute.createDiffuse(stoneRuneTexture),
            ColorAttribute.createEmissive(0.03f, 0.10f, 0.16f, 1f),
            ColorAttribute.createSpecular(0.25f, 0.35f, 0.45f, 1f),
            FloatAttribute.createShininess(24f)
        )

        glowingRuneMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.22f, 0.85f, 0.98f, 1f)),
            ColorAttribute.createEmissive(0.18f, 0.68f, 0.88f, 1f),
            ColorAttribute.createSpecular(0.8f, 0.95f, 1.0f, 1f),
            FloatAttribute.createShininess(48f)
        )

        manaCrystalMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.20f, 0.88f, 0.96f, 0.92f)),
            ColorAttribute.createEmissive(0.16f, 0.62f, 0.82f, 1f),
            ColorAttribute.createSpecular(0.9f, 1.0f, 1.0f, 1f),
            FloatAttribute.createShininess(64f),
            BlendingAttribute(true, 0.92f)
        )

        amethystCrystalMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.68f, 0.35f, 0.98f, 0.92f)),
            ColorAttribute.createEmissive(0.42f, 0.18f, 0.72f, 1f),
            ColorAttribute.createSpecular(0.9f, 0.8f, 1.0f, 1f),
            FloatAttribute.createShininess(64f),
            BlendingAttribute(true, 0.92f)
        )

        goldTrimMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.96f, 0.80f, 0.22f, 1f)),
            ColorAttribute.createEmissive(0.22f, 0.16f, 0.04f, 1f),
            ColorAttribute.createSpecular(1.0f, 0.92f, 0.65f, 1f),
            FloatAttribute.createShininess(45f)
        )

        boulderMaterial = Material(
            TextureAttribute.createDiffuse(boulderTexture),
            ColorAttribute.createSpecular(0.12f, 0.18f, 0.22f, 1f),
            FloatAttribute.createShininess(12f)
        )

        grassMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.15f, 0.36f, 0.12f, 1f)),
            ColorAttribute.createEmissive(0.02f, 0.05f, 0.02f, 1f)
        )

        darkGrassPatchMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.10f, 0.26f, 0.08f, 1f)),
            ColorAttribute.createEmissive(0.01f, 0.03f, 0.01f, 1f),
            ColorAttribute.createSpecular(0.05f, 0.09f, 0.04f, 1f),
            FloatAttribute.createShininess(4f)
        )

        dirtPatchMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.28f, 0.21f, 0.14f, 1f)),
            ColorAttribute.createEmissive(0.02f, 0.015f, 0.01f, 1f),
            ColorAttribute.createSpecular(0.06f, 0.05f, 0.03f, 1f),
            FloatAttribute.createShininess(3f)
        )

        glowingFernMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.24f, 0.82f, 0.38f, 1f)),
            ColorAttribute.createEmissive(0.08f, 0.36f, 0.18f, 1f)
        )

        skyDomeMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.18f, 0.38f, 0.72f, 1f)),
            ColorAttribute.createEmissive(0.18f, 0.38f, 0.72f, 1f),
            com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(com.badlogic.gdx.graphics.GL20.GL_NONE)
        )

        skySunMaterial = Material(
            ColorAttribute.createDiffuse(Color(1.0f, 0.95f, 0.80f, 1f)),
            ColorAttribute.createEmissive(1.0f, 0.95f, 0.80f, 1f)
        )

        skyMoonMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.75f, 0.88f, 1.0f, 1f)),
            ColorAttribute.createEmissive(0.70f, 0.85f, 0.98f, 1f)
        )

        skyStarsMaterial = Material(
            ColorAttribute.createDiffuse(Color(1.0f, 1.0f, 1.0f, 0.95f)),
            ColorAttribute.createEmissive(1.0f, 1.0f, 1.0f, 1f),
            BlendingAttribute(true, 0.95f)
        )

        // Anime protagonist palette
        skinMaterial = Material(
            ColorAttribute.createDiffuse(Color(1.0f, 0.88f, 0.82f, 1f)),
            ColorAttribute.createEmissive(0.12f, 0.08f, 0.07f, 1f),
            ColorAttribute.createSpecular(0.18f, 0.14f, 0.12f, 1f),
            FloatAttribute.createShininess(14f)
        )

        hairMaterial = Material(
            TextureAttribute.createDiffuse(hairTexture),
            ColorAttribute.createEmissive(0.08f, 0.10f, 0.15f, 1f),
            ColorAttribute.createSpecular(0.55f, 0.65f, 0.85f, 1f),
            FloatAttribute.createShininess(36f)
        )

        cloakMaterial = Material(
            TextureAttribute.createDiffuse(cloakTexture),
            ColorAttribute.createEmissive(0.03f, 0.07f, 0.18f, 1f),
            ColorAttribute.createSpecular(0.25f, 0.40f, 0.70f, 1f),
            FloatAttribute.createShininess(22f)
        )

        tunicMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.08f, 0.11f, 0.20f, 1f)),
            ColorAttribute.createSpecular(0.20f, 0.28f, 0.42f, 1f),
            FloatAttribute.createShininess(18f)
        )

        silverArmorMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.84f, 0.90f, 0.98f, 1f)),
            ColorAttribute.createEmissive(0.06f, 0.09f, 0.14f, 1f),
            ColorAttribute.createSpecular(0.85f, 0.92f, 1.0f, 1f),
            FloatAttribute.createShininess(52f)
        )

        bootMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.11f, 0.15f, 0.26f, 1f)),
            ColorAttribute.createSpecular(0.30f, 0.36f, 0.50f, 1f),
            FloatAttribute.createShininess(24f)
        )

        eyeIrisMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.22f, 0.78f, 0.98f, 1f)),
            ColorAttribute.createEmissive(0.14f, 0.52f, 0.75f, 1f)
        )

        eyeDarkMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.06f, 0.08f, 0.14f, 1f))
        )

        eyeHighlightMaterial = Material(
            ColorAttribute.createDiffuse(Color.WHITE),
            ColorAttribute.createEmissive(0.8f, 0.9f, 1.0f, 1f)
        )

        // Milestone 2: Combat & Enemy Materials
        corruptedArmorMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.14f, 0.11f, 0.22f, 1f)),
            ColorAttribute.createEmissive(0.05f, 0.02f, 0.08f, 1f),
            ColorAttribute.createSpecular(0.55f, 0.35f, 0.65f, 1f),
            FloatAttribute.createShininess(38f)
        )

        corruptedMantleMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.38f, 0.08f, 0.18f, 1f)),
            ColorAttribute.createEmissive(0.12f, 0.02f, 0.06f, 1f),
            ColorAttribute.createSpecular(0.35f, 0.15f, 0.25f, 1f),
            FloatAttribute.createShininess(18f)
        )

        corruptedCrimsonGlowMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.98f, 0.20f, 0.42f, 1f)),
            ColorAttribute.createEmissive(0.85f, 0.14f, 0.32f, 1f),
            ColorAttribute.createSpecular(1.0f, 0.6f, 0.7f, 1f),
            FloatAttribute.createShininess(56f)
        )

        corruptedBladeMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.26f, 0.22f, 0.34f, 1f)),
            ColorAttribute.createEmissive(0.22f, 0.05f, 0.12f, 1f),
            ColorAttribute.createSpecular(0.85f, 0.55f, 0.75f, 1f),
            FloatAttribute.createShininess(48f)
        )

        energyBlastMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.25f, 0.92f, 1.0f, 0.90f)),
            ColorAttribute.createEmissive(0.35f, 0.85f, 1.0f, 1f),
            ColorAttribute.createSpecular(1.0f, 1.0f, 1.0f, 1f),
            FloatAttribute.createShininess(64f),
            BlendingAttribute(true, 0.90f)
        )

        arcaneBoltMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.75f, 0.38f, 1.0f, 0.92f)),
            ColorAttribute.createEmissive(0.58f, 0.28f, 0.96f, 1f),
            ColorAttribute.createSpecular(0.95f, 0.85f, 1.0f, 1f),
            FloatAttribute.createShininess(64f),
            BlendingAttribute(true, 0.92f)
        )

        slashWaveMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.38f, 0.94f, 1.0f, 0.82f)),
            ColorAttribute.createEmissive(0.42f, 0.88f, 1.0f, 1f),
            BlendingAttribute(true, 0.82f)
        )

        goldImpactMaterial = Material(
            ColorAttribute.createDiffuse(Color(1.0f, 0.88f, 0.32f, 0.92f)),
            ColorAttribute.createEmissive(0.85f, 0.68f, 0.18f, 1f),
            BlendingAttribute(true, 0.92f)
        )

        // Milestone 3: Advanced Anime Sword Combat Materials
        swordTrailCyanMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.32f, 0.95f, 1.0f, 0.86f)),
            ColorAttribute.createEmissive(0.48f, 0.92f, 1.0f, 1f),
            BlendingAttribute(true, 0.86f),
            com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(com.badlogic.gdx.graphics.GL20.GL_NONE)
        )

        swordTrailFireMaterial = Material(
            ColorAttribute.createDiffuse(Color(1.0f, 0.56f, 0.16f, 0.90f)),
            ColorAttribute.createEmissive(0.98f, 0.48f, 0.12f, 1f),
            BlendingAttribute(true, 0.90f),
            com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(com.badlogic.gdx.graphics.GL20.GL_NONE)
        )

        afterimageGhostMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.18f, 0.82f, 0.98f, 0.46f)),
            ColorAttribute.createEmissive(0.24f, 0.72f, 0.96f, 1f),
            BlendingAttribute(true, 0.46f)
        )

        infernoFlameMaterial = Material(
            ColorAttribute.createDiffuse(Color(1.0f, 0.38f, 0.12f, 0.92f)),
            ColorAttribute.createEmissive(0.95f, 0.35f, 0.08f, 1f),
            ColorAttribute.createSpecular(1.0f, 0.85f, 0.45f, 1f),
            FloatAttribute.createShininess(48f),
            BlendingAttribute(true, 0.92f)
        )

        windStreakMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.78f, 0.96f, 1.0f, 0.76f)),
            ColorAttribute.createEmissive(0.62f, 0.90f, 1.0f, 1f),
            BlendingAttribute(true, 0.76f)
        )

        groundDustMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.36f, 0.44f, 0.54f, 0.65f)),
            ColorAttribute.createEmissive(0.10f, 0.16f, 0.24f, 1f),
            BlendingAttribute(true, 0.65f)
        )

        // Milestone 4: Advanced Anime Fists Combat Materials
        fistWindTrailMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.68f, 0.96f, 1.0f, 0.84f)),
            ColorAttribute.createEmissive(0.56f, 0.92f, 1.0f, 1f),
            BlendingAttribute(true, 0.84f),
            com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(com.badlogic.gdx.graphics.GL20.GL_NONE)
        )

        fistChiGoldMaterial = Material(
            ColorAttribute.createDiffuse(Color(1.0f, 0.78f, 0.22f, 0.90f)),
            ColorAttribute.createEmissive(0.96f, 0.68f, 0.16f, 1f),
            ColorAttribute.createSpecular(1.0f, 0.95f, 0.70f, 1f),
            FloatAttribute.createShininess(52f),
            BlendingAttribute(true, 0.90f),
            com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(com.badlogic.gdx.graphics.GL20.GL_NONE)
        )

        martialAfterimageMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.32f, 0.90f, 1.0f, 0.44f)),
            ColorAttribute.createEmissive(0.36f, 0.84f, 0.98f, 1f),
            BlendingAttribute(true, 0.44f)
        )

        // Green Slime Materials
        slimeBodyMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.14f, 0.86f, 0.40f, 0.84f)),
            ColorAttribute.createEmissive(0.08f, 0.35f, 0.16f, 1f),
            ColorAttribute.createSpecular(0.85f, 1.0f, 0.90f, 1f),
            FloatAttribute.createShininess(68f),
            BlendingAttribute(true, 0.84f)
        )

        slimeCoreMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.26f, 1.0f, 0.50f, 1f)),
            ColorAttribute.createEmissive(0.38f, 1.0f, 0.48f, 1f),
            ColorAttribute.createSpecular(1.0f, 1.0f, 1.0f, 1f),
            FloatAttribute.createShininess(80f)
        )

        slimeEyeWhiteMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.96f, 0.98f, 0.96f, 1f)),
            ColorAttribute.createEmissive(0.12f, 0.16f, 0.12f, 1f),
            ColorAttribute.createSpecular(0.6f, 0.6f, 0.6f, 1f),
            FloatAttribute.createShininess(32f)
        )

        slimeEyePupilMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.04f, 0.10f, 0.05f, 1f)),
            ColorAttribute.createEmissive(0.01f, 0.04f, 0.02f, 1f),
            ColorAttribute.createSpecular(0.9f, 0.9f, 0.9f, 1f),
            FloatAttribute.createShininess(55f)
        )

        slimeOrbMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.25f, 1.0f, 0.48f, 0.92f)),
            ColorAttribute.createEmissive(0.38f, 1.0f, 0.52f, 1f),
            ColorAttribute.createSpecular(1.0f, 1.0f, 1.0f, 1f),
            FloatAttribute.createShininess(72f),
            BlendingAttribute(true, 0.92f)
        )

        slimeCrystalMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.18f, 0.96f, 0.46f, 0.92f)),
            ColorAttribute.createEmissive(0.28f, 0.90f, 0.44f, 1f),
            ColorAttribute.createSpecular(1.0f, 1.0f, 0.95f, 1f),
            FloatAttribute.createShininess(64f),
            BlendingAttribute(true, 0.92f)
        )

        // Blue Flower Monster & MP Potion Materials
        blueFlowerPetalMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.14f, 0.48f, 0.96f, 1f)),
            ColorAttribute.createEmissive(0.06f, 0.22f, 0.56f, 1f),
            ColorAttribute.createSpecular(0.55f, 0.78f, 1.0f, 1f),
            FloatAttribute.createShininess(36f)
        )

        blueFlowerCoreMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.32f, 0.88f, 1.0f, 1f)),
            ColorAttribute.createEmissive(0.26f, 0.78f, 0.98f, 1f),
            ColorAttribute.createSpecular(0.95f, 1.0f, 1.0f, 1f),
            FloatAttribute.createShininess(68f)
        )

        blueFlowerStemMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.14f, 0.42f, 0.22f, 1f)),
            ColorAttribute.createEmissive(0.02f, 0.08f, 0.04f, 1f),
            ColorAttribute.createSpecular(0.18f, 0.34f, 0.20f, 1f),
            FloatAttribute.createShininess(16f)
        )

        blueFlowerSporeMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.24f, 0.76f, 1.0f, 0.92f)),
            ColorAttribute.createEmissive(0.32f, 0.82f, 1.0f, 1f),
            ColorAttribute.createSpecular(0.95f, 1.0f, 1.0f, 1f),
            FloatAttribute.createShininess(72f),
            BlendingAttribute(true, 0.92f)
        )

        mpPotionGlassMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.75f, 0.90f, 1.0f, 0.72f)),
            ColorAttribute.createEmissive(0.12f, 0.26f, 0.45f, 1f),
            ColorAttribute.createSpecular(1.0f, 1.0f, 1.0f, 1f),
            FloatAttribute.createShininess(84f),
            BlendingAttribute(true, 0.72f)
        )

        mpPotionLiquidMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.16f, 0.62f, 1.0f, 0.95f)),
            ColorAttribute.createEmissive(0.24f, 0.68f, 1.0f, 1f),
            ColorAttribute.createSpecular(0.90f, 0.98f, 1.0f, 1f),
            FloatAttribute.createShininess(64f)
        )

        mpPotionCorkMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.58f, 0.40f, 0.24f, 1f)),
            ColorAttribute.createSpecular(0.16f, 0.12f, 0.08f, 1f),
            FloatAttribute.createShininess(8f)
        )

        // Red Flower Monster, Health Potion & Cracked Rock Materials
        redFlowerPetalMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.88f, 0.16f, 0.20f, 1f)),
            ColorAttribute.createEmissive(0.32f, 0.04f, 0.06f, 1f),
            ColorAttribute.createSpecular(0.85f, 0.45f, 0.50f, 1f),
            FloatAttribute.createShininess(36f)
        )

        redFlowerCoreMaterial = Material(
            ColorAttribute.createDiffuse(Color(1.0f, 0.52f, 0.18f, 1f)),
            ColorAttribute.createEmissive(0.75f, 0.26f, 0.08f, 1f),
            ColorAttribute.createSpecular(1.0f, 0.85f, 0.70f, 1f),
            FloatAttribute.createShininess(68f)
        )

        redFlowerSporeMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.96f, 0.18f, 0.22f, 0.92f)),
            ColorAttribute.createEmissive(0.85f, 0.12f, 0.16f, 1f),
            ColorAttribute.createSpecular(1.0f, 0.80f, 0.80f, 1f),
            FloatAttribute.createShininess(72f),
            BlendingAttribute(true, 0.92f)
        )

        healthPotionLiquidMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.92f, 0.14f, 0.22f, 0.95f)),
            ColorAttribute.createEmissive(0.65f, 0.08f, 0.14f, 1f),
            ColorAttribute.createSpecular(1.0f, 0.88f, 0.90f, 1f),
            FloatAttribute.createShininess(64f)
        )

        crackedRockLineMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.08f, 0.09f, 0.11f, 1f)),
            ColorAttribute.createEmissive(0.04f, 0.06f, 0.08f, 1f),
            ColorAttribute.createSpecular(0.15f, 0.18f, 0.22f, 1f),
            FloatAttribute.createShininess(6f)
        )
    }

    private val pixmaps = mutableListOf<Pixmap>()

    fun registerTexture(pixmap: Pixmap): Texture {
        pixmaps.add(pixmap)
        val texture = Texture(pixmap, false)
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        textures.add(texture)
        return texture
    }

    fun registerModel(model: Model): Model {
        models.add(model)
        return model
    }

    fun createTerrainPixmap(size: Int): Pixmap {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val nx = x.toFloat() / size
                val ny = y.toFloat() / size
                val wave1 = (sin(nx * 12.56f) * cos(ny * 12.56f) + 1f) * 0.5f
                val wave2 = (sin(nx * 25.13f + 1.2f) * sin(ny * 25.13f + 0.8f) + 1f) * 0.5f
                val wave3 = (cos(nx * 37.70f - 0.6f) * sin(ny * 37.70f + 1.7f) + 1f) * 0.5f
                val detail = (((x * 37 + y * 59) and 31) / 31f)

                // Blend between rich woodland dark grass, medium forest turf, and subtle earthy dirt patches
                val patchBlend = (wave1 * 0.52f + wave2 * 0.30f + wave3 * 0.18f).coerceIn(0f, 1f)

                val r: Float
                val g: Float
                val b: Float
                when {
                    patchBlend < 0.22f -> {
                        // Subtle earthy soil / rich forest loam patch
                        val t = patchBlend / 0.22f
                        r = (0.22f + t * 0.05f + detail * 0.025f).coerceIn(0f, 1f)
                        g = (0.17f + t * 0.06f + detail * 0.025f).coerceIn(0f, 1f)
                        b = (0.11f + t * 0.04f + detail * 0.020f).coerceIn(0f, 1f)
                    }
                    patchBlend < 0.58f -> {
                        // Deep, dark woodland grass patch (reduced brightness & saturation)
                        val t = (patchBlend - 0.22f) / 0.36f
                        r = (0.10f + t * 0.04f + detail * 0.02f).coerceIn(0f, 1f)
                        g = (0.21f + t * 0.08f + detail * 0.03f).coerceIn(0f, 1f)
                        b = (0.08f + t * 0.03f + detail * 0.015f).coerceIn(0f, 1f)
                    }
                    else -> {
                        // Natural forest meadow grass (toned down from bright green)
                        val grassT = (patchBlend - 0.58f) / 0.42f
                        r = (0.14f + grassT * 0.05f + detail * 0.02f).coerceIn(0f, 1f)
                        g = (0.28f + grassT * 0.10f + detail * 0.035f).coerceIn(0f, 1f)
                        b = (0.10f + grassT * 0.04f + detail * 0.015f).coerceIn(0f, 1f)
                    }
                }
                pm.setColor(r, g, b, 1f)
                pm.drawPixel(x, y)
            }
        }
        // Dark green moss & clover tufts
        pm.setColor(0.08f, 0.20f, 0.06f, 1f)
        for (i in 0 until 48) {
            val sx = (i * 47 + 13) % size
            val sy = (i * 73 + 23) % size
            pm.fillCircle(sx, sy, 2)
        }
        // Subtle soil/pebble specks
        pm.setColor(0.26f, 0.20f, 0.14f, 0.92f)
        for (i in 0 until 28) {
            val sx = (i * 89 + 31) % size
            val sy = (i * 61 + 47) % size
            pm.fillCircle(sx, sy, 1)
        }
        return pm
    }

    private fun createBarkPixmap(size: Int): Pixmap {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val stripe = (sin(x * 0.5f + sin(y * 0.2f) * 1.5f) + 1f) * 0.5f
                val r = 0.14f + stripe * 0.08f
                val g = 0.11f + stripe * 0.06f
                val b = 0.20f + stripe * 0.10f
                pm.setColor(r, g, b, 1f)
                pm.drawPixel(x, y)
            }
        }
        return pm
    }

    private fun createCanopyPixmap(size: Int, isCyan: Boolean): Pixmap {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (y in 0 until size) {
            val grad = 1f - (y.toFloat() / size) * 0.40f
            for (x in 0 until size) {
                val leaf = ((sin(x * 0.45f) * cos(y * 0.45f)) + 1f) * 0.5f
                if (isCyan) {
                    // Vibrant Emerald / Forest Green canopy
                    val r = (0.14f + leaf * 0.12f) * grad
                    val g = (0.52f + leaf * 0.32f) * grad
                    val b = (0.16f + leaf * 0.14f) * grad
                    pm.setColor(r, g, b, 1f)
                } else {
                    // Warm Autumn Amber / Golden Grove canopy
                    val r = (0.72f + leaf * 0.22f) * grad
                    val g = (0.46f + leaf * 0.22f) * grad
                    val b = (0.12f + leaf * 0.10f) * grad
                    pm.setColor(r, g, b, 1f)
                }
                pm.drawPixel(x, y)
            }
        }
        return pm
    }

    private fun createStoneRunePixmap(size: Int): Pixmap {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val grain = (((x * 19 + y * 47) and 15) / 15f) * 0.05f
                val border = if (x < 6 || x > size - 7 || y < 6 || y > size - 7) 0.06f else 0f
                pm.setColor(0.16f + grain + border, 0.20f + grain + border, 0.28f + grain + border, 1f)
                pm.drawPixel(x, y)
            }
        }
        pm.setColor(0.22f, 0.85f, 0.98f, 1f)
        val mid = size / 2
        pm.drawRectangle(14, 14, size - 28, size - 28)
        pm.drawRectangle(15, 15, size - 30, size - 30)
        pm.drawCircle(mid, mid, size / 4)
        pm.drawLine(mid, 20, mid, size - 20)
        pm.drawLine(20, mid, size - 20, mid)
        return pm
    }

    private fun createCloakPixmap(size: Int): Pixmap {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (y in 0 until size) {
            val t = y.toFloat() / size
            for (x in 0 until size) {
                val r = 0.08f + t * 0.06f
                val g = 0.18f + t * 0.14f
                val b = 0.44f + t * 0.24f
                pm.setColor(r, g, b, 1f)
                pm.drawPixel(x, y)
            }
        }
        pm.setColor(0.38f, 0.78f, 0.98f, 1f)
        pm.fillRectangle(0, size - 6, size, 4)
        return pm
    }

    private fun createHairPixmap(size: Int): Pixmap {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (y in 0 until size) {
            val sheen = if (y in (size / 3)..(size / 2)) 0.12f else 0f
            for (x in 0 until size) {
                val strand = (sin(x * 0.7f) + 1f) * 0.03f
                val r = (0.82f + strand + sheen).coerceIn(0f, 1f)
                val g = (0.86f + strand + sheen).coerceIn(0f, 1f)
                val b = (0.94f + strand + sheen).coerceIn(0f, 1f)
                pm.setColor(r, g, b, 1f)
                pm.drawPixel(x, y)
            }
        }
        return pm
    }

    private fun createBoulderPixmap(size: Int): Pixmap {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (y in 0 until size) {
            val mossFactor = (1f - (y.toFloat() / size)).coerceIn(0f, 1f)
            for (x in 0 until size) {
                val n = (((x * 23 + y * 61) and 31) / 31f) * 0.07f
                val r = 0.18f + n
                val g = 0.22f + n + mossFactor * 0.14f
                val b = 0.28f + n + mossFactor * 0.10f
                pm.setColor(r, g, b, 1f)
                pm.drawPixel(x, y)
            }
        }
        return pm
    }

    override fun dispose() {
        models.forEach { runCatching { it.dispose() } }
        models.clear()
        textures.forEach { runCatching { it.dispose() } }
        textures.clear()
        pixmaps.forEach { runCatching { it.dispose() } }
        pixmaps.clear()
    }
}
