package com.aethelgard.survival.core.player

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class MartialLimb {
    RIGHT_FIST,
    LEFT_FIST,
    RIGHT_FOOT,
    LEFT_FOOT
}

enum class AnimationState(val displayName: String) {
    IDLE("IDLE"),
    WALK("WALK"),
    RUN("RUN"),
    SPRINT("SPRINT"),
    JUMP("JUMP"),
    DODGE("DASH / DODGE"),
    DODGE_SIDE("HAKI: SIDE SLIP"),
    DODGE_BACK("HAKI: BACK STEP"),
    DODGE_LOW("HAKI: LOW WEAVE"),
    DODGE_FORWARD("HAKI: FORWARD SLIP"),
    // Legacy unarmed aliases preserved for compatibility
    LIGHT_ATTACK_1("FIST: JAB"),
    LIGHT_ATTACK_2("FIST: CROSS"),
    LIGHT_ATTACK_3("FIST: SPIN KICK"),
    HEAVY_ATTACK("FIST: HAYMAKER"),
    AERIAL_ATTACK("FIST: DIVE KICK"),
    // Dedicated Milestone 4 Anime Fists Martial Arts states (FISTS)
    FIST_RIGHT_JAB("FIST: RIGHT JAB"),
    FIST_LEFT_CROSS("FIST: LEFT CROSS"),
    FIST_SIDE_KICK("FIST: SIDE KICK"),
    FIST_SPIN_ROUNDHOUSE("FIST: SPIN ROUNDHOUSE"),
    FIST_RISING_HIGH_KICK("FIST: RISING HIGH KICK"),
    FIST_FINISHER_CYCLONE_KICK("FINISHER: CYCLONE KICK"),
    FIST_FINISHER_FLYING_STRIKE("FINISHER: FLYING STRIKE"),
    FIST_HEAVY_DRAGON_HAYMAKER("DRAGON HAYMAKER"),
    FIST_HEAVY_THUNDER_BREAKER("THUNDER BREAKER"),
    FIST_HEAVY_METEOR_STRIKE("METEOR STRIKE"),
    // Dedicated Milestone 3 Anime Sword Combat states (SWORD)
    SWORD_HORIZONTAL_SLASH("SWORD: HORIZONTAL SLASH"),
    SWORD_DIAGONAL_CROSS("SWORD: DIAGONAL CROSS"),
    SWORD_RISING_SLASH("SWORD: RISING SLASH"),
    SWORD_SPIN_360("SWORD: 360 WHIRLWIND"),
    SWORD_RAPID_FLURRY("SWORD: RAPID FLURRY"),
    SWORD_PHANTOM_DASH_SLASH("PHANTOM DASH SLASH"),
    SWORD_FLASH_STEP("FLASH STEP"),
    SWORD_INFERNO_CRESCENT("INFERNO CRESCENT"),
    SWORD_DRAGON_TEMPEST("DRAGON TEMPEST"),
    SWORD_AERIAL_STRIKE("AERIAL SWORD STRIKE"),
    // Phase 2 Survival Gathering Tool states
    TOOL_AXE_CHOP("TOOL: AXE CHOP"),
    TOOL_PICKAXE_MINE("TOOL: PICKAXE MINE"),
    TOOL_SHOVEL_DIG("TOOL: SHOVEL DIG"),
    TOOL_SICKLE_HARVEST("TOOL: SICKLE HARVEST"),
    TOOL_KNIFE_SLICE("TOOL: KNIFE SLICE"),
    TOOL_HAMMER_BUILD("TOOL: HAMMER BUILD"),
    // Energy, Ultimate & Hit states
    ENERGY_CHARGE("CHARGING MANA"),
    ENERGY_CAST("ENERGY BLAST"),
    ULTIMATE_AWAKENING("DIVINE: AWAKENING"),
    ULTIMATE_DUAL_SUMMON("DIVINE: DUAL SUMMON"),
    ULTIMATE_CONVERGE_MERGE("DIVINE: CONVERGENCE"),
    ULTIMATE_RELEASE_BEAM("DIVINE: ANNIHILATION"),
    HIT("STAGGER"),
    DIE("DEFEATED")
}

/**
 * Controls articulated skeletal limb and blade animations on the procedural Anime Fantasy protagonist.
 * Strictly separates Milestone 4 Unarmed Martial Arts (`FISTS`) animations from Milestone 3
 * Anime Sword Combat (`SWORD`) animations, supports Phase 2 Survival Gathering Tool animations,
 * and computes exact world-space blade or limb coordinates for real-time 3D trail generation.
 */
class PlayerAnimationController(private val modelInstance: ModelInstance) {

    private val pelvisNode: Node? = modelInstance.getNode("pelvis", true)
    private val torsoNode: Node? = modelInstance.getNode("torso", true)
    private val headNode: Node? = modelInstance.getNode("head", true)
    private val hairBackNode: Node? = modelInstance.getNode("hairBack", true)
    private val cloakBackNode: Node? = modelInstance.getNode("cloakBack", true)
    private val swordNode: Node? = modelInstance.getNode("sheathedSword", true)
    private val sheathedHiltNode: Node? = modelInstance.getNode("sheathedHilt", true)
    private val drawnSwordNode: Node? = modelInstance.getNode("drawnSword", true)

    private val heldStoneAxeNode: Node? = modelInstance.getNode("heldStoneAxe", true)
    private val heldIronAxeNode: Node? = modelInstance.getNode("heldIronAxe", true)
    private val heldStonePickaxeNode: Node? = modelInstance.getNode("heldStonePickaxe", true)
    private val heldIronPickaxeNode: Node? = modelInstance.getNode("heldIronPickaxe", true)
    private val heldShovelNode: Node? = modelInstance.getNode("heldShovel", true)
    private val heldSickleNode: Node? = modelInstance.getNode("heldSickle", true)
    private val heldKnifeNode: Node? = modelInstance.getNode("heldKnife", true)
    private val heldBuildersHammerNode: Node? = modelInstance.getNode("heldBuildersHammer", true)

    private val heldResourceIds = listOf(
        "wood", "hardwood", "branches", "roots",
        "stone", "coal", "copper_ore", "iron_ore", "rare_ore",
        "plants", "mushrooms", "berries", "fibers", "animal_hides",
        "monster_essence", "magic_crystals", "ancient_relics", "boss_materials",
        "green_crystal",
        "roasted_mushrooms", "berry_brew", "herbal_salve", "mana_essence_tonic", "mp_potion", "health_potion"
    )
    private val heldResourceNodeMap: Map<String, Node?> = heldResourceIds.associateWith { itemId ->
        modelInstance.getNode("heldItem_$itemId", true)
    }

    private val leftUpperArm: Node? = modelInstance.getNode("leftUpperArm", true)
    private val leftForearm: Node? = modelInstance.getNode("leftForearm", true)
    private val rightUpperArm: Node? = modelInstance.getNode("rightUpperArm", true)
    private val rightForearm: Node? = modelInstance.getNode("rightForearm", true)

    private val leftThigh: Node? = modelInstance.getNode("leftThigh", true)
    private val leftShin: Node? = modelInstance.getNode("leftShin", true)
    private val rightThigh: Node? = modelInstance.getNode("rightThigh", true)
    private val rightShin: Node? = modelInstance.getNode("rightShin", true)

    fun getHeldToolNode(toolId: String): Node? = when (toolId) {
        "stone_axe" -> heldStoneAxeNode
        "iron_axe" -> heldIronAxeNode
        "stone_pickaxe" -> heldStonePickaxeNode
        "iron_pickaxe" -> heldIronPickaxeNode
        "shovel" -> heldShovelNode
        "sickle" -> heldSickleNode
        "knife" -> heldKnifeNode
        "builders_hammer" -> heldBuildersHammerNode
        else -> heldResourceNodeMap[toolId]
    }

    fun getHeldItemNode(itemId: String): Node? = getHeldToolNode(itemId)

    private var animPhase: Float = 0f
    private var idleTime: Float = 0f

    // Smoothed joint angles (degrees)
    private var curPelvisY = 0.90f
    private var curPelvisYaw = 0f
    private var curPelvisPitch = 0f
    private var curPelvisRoll = 0f
    private var curTorsoPitch = 0f
    private var curTorsoYaw = 0f
    private var curHeadPitch = 0f
    private var curCloakPitch = 8f
    private var curCloakRoll = 0f
    private var curHairPitch = 0f

    private var curLeftArmPitch = 0f
    private var curLeftArmYaw = 0f
    private var curLeftArmRoll = 8f
    private var curLeftForearmPitch = -12f

    private var curRightArmPitch = 0f
    private var curRightArmYaw = 0f
    private var curRightArmRoll = -8f
    private var curRightForearmPitch = -12f

    // Dedicated sword wrist/blade orientation angles on drawnSwordNode
    private var curSwordPitch = 0f
    private var curSwordYaw = 0f
    private var curSwordRoll = 0f

    private var curLeftThighPitch = 0f
    private var curLeftThighRoll = 0f
    private var curLeftShinPitch = 0f
    private var curRightThighPitch = 0f
    private var curRightThighRoll = 0f
    private var curRightShinPitch = 0f

    private val tmpMat = Matrix4()

    /**
     * Computes the exact 3D world-space position of the drawn sword's guard (base) and blade tip
     * directly from the hierarchical skeletal `drawnSword` node transform.
     */
    fun getBladeWorldSegment(outBase: Vector3, outTip: Vector3) {
        val sword = drawnSwordNode
        if (sword != null) {
            tmpMat.set(modelInstance.transform).mul(sword.globalTransform)
            outBase.set(0f, 0f, 0.10f).mul(tmpMat)
            outTip.set(0f, 0f, 1.04f).mul(tmpMat)
        } else {
            outBase.set(0f, 1.1f, 0.3f).mul(modelInstance.transform)
            outTip.set(0f, 1.1f, 1.2f).mul(modelInstance.transform)
        }
    }

    /**
     * Computes the exact 3D world-space segment `(outBase, outTip)` of the active martial arts
     * striking limb (elbow-to-fist or knee-to-foot) for real-time punch and kick wind trail ribbons.
     */
    fun getMartialLimbWorldSegment(limb: MartialLimb, outBase: Vector3, outTip: Vector3) {
        val node = when (limb) {
            MartialLimb.RIGHT_FIST -> rightForearm
            MartialLimb.LEFT_FIST -> leftForearm
            MartialLimb.RIGHT_FOOT -> rightShin
            MartialLimb.LEFT_FOOT -> leftShin
        }
        if (node != null) {
            tmpMat.set(modelInstance.transform).mul(node.globalTransform)
            when (limb) {
                MartialLimb.RIGHT_FIST, MartialLimb.LEFT_FIST -> {
                    outBase.set(0f, -0.04f, 0f).mul(tmpMat)
                    outTip.set(0f, -0.34f, 0.12f).mul(tmpMat)
                }
                MartialLimb.RIGHT_FOOT, MartialLimb.LEFT_FOOT -> {
                    outBase.set(0f, -0.06f, 0f).mul(tmpMat)
                    outTip.set(0f, -0.48f, 0.16f).mul(tmpMat)
                }
            }
        } else {
            outBase.set(0f, 1.05f, 0.25f).mul(modelInstance.transform)
            outTip.set(0f, 1.05f, 0.85f).mul(modelInstance.transform)
        }
    }

    /**
     * Computes the exact 3D world-space positions of the left and right hands directly from
     * the articulated `leftForearm` and `rightForearm` skeletal nodes for dual sphere tracking.
     */
    fun getHandWorldPositions(outLeftHand: Vector3, outRightHand: Vector3) {
        val lForearm = leftForearm
        val rForearm = rightForearm
        if (lForearm != null && rForearm != null) {
            tmpMat.set(modelInstance.transform).mul(lForearm.globalTransform)
            outLeftHand.set(0f, -0.34f, 0.08f).mul(tmpMat)
            tmpMat.set(modelInstance.transform).mul(rForearm.globalTransform)
            outRightHand.set(0f, -0.34f, 0.08f).mul(tmpMat)
        } else {
            outLeftHand.set(-0.55f, 1.85f, 0.25f).mul(modelInstance.transform)
            outRightHand.set(0.55f, 1.85f, 0.25f).mul(modelInstance.transform)
        }
    }

    fun update(
        delta: Float,
        state: AnimationState,
        horizontalSpeedRatio: Float,
        verticalVelocity: Float,
        isFirstPerson: Boolean,
        isSwordEquipped: Boolean = false,
        attackProgress: Float = 0f,
        equippedToolId: String? = null,
        slopePitch: Float = 0f,
        slopeRoll: Float = 0f,
        actualHorizontalSpeed: Float = 0f
    ) {
        idleTime += delta
        val isSwordAction = state == AnimationState.SWORD_HORIZONTAL_SLASH ||
            state == AnimationState.SWORD_DIAGONAL_CROSS ||
            state == AnimationState.SWORD_RISING_SLASH ||
            state == AnimationState.SWORD_SPIN_360 ||
            state == AnimationState.SWORD_RAPID_FLURRY ||
            state == AnimationState.SWORD_PHANTOM_DASH_SLASH ||
            state == AnimationState.SWORD_FLASH_STEP ||
            state == AnimationState.SWORD_INFERNO_CRESCENT ||
            state == AnimationState.SWORD_DRAGON_TEMPEST ||
            state == AnimationState.SWORD_AERIAL_STRIKE

        val isFistAction = state == AnimationState.FIST_RIGHT_JAB ||
            state == AnimationState.FIST_LEFT_CROSS ||
            state == AnimationState.FIST_SIDE_KICK ||
            state == AnimationState.FIST_SPIN_ROUNDHOUSE ||
            state == AnimationState.FIST_RISING_HIGH_KICK ||
            state == AnimationState.FIST_FINISHER_CYCLONE_KICK ||
            state == AnimationState.FIST_FINISHER_FLYING_STRIKE ||
            state == AnimationState.FIST_HEAVY_DRAGON_HAYMAKER ||
            state == AnimationState.FIST_HEAVY_THUNDER_BREAKER ||
            state == AnimationState.FIST_HEAVY_METEOR_STRIKE ||
            state == AnimationState.LIGHT_ATTACK_1 ||
            state == AnimationState.LIGHT_ATTACK_2 ||
            state == AnimationState.LIGHT_ATTACK_3 ||
            state == AnimationState.HEAVY_ATTACK ||
            state == AnimationState.AERIAL_ATTACK

        val isToolAction = state == AnimationState.TOOL_AXE_CHOP ||
            state == AnimationState.TOOL_PICKAXE_MINE ||
            state == AnimationState.TOOL_SHOVEL_DIG ||
            state == AnimationState.TOOL_SICKLE_HARVEST ||
            state == AnimationState.TOOL_KNIFE_SLICE ||
            state == AnimationState.TOOL_HAMMER_BUILD

        val isUltimateAction = state == AnimationState.ULTIMATE_AWAKENING ||
            state == AnimationState.ULTIMATE_DUAL_SUMMON ||
            state == AnimationState.ULTIMATE_CONVERGE_MERGE ||
            state == AnimationState.ULTIMATE_RELEASE_BEAM

        val isCombatAction = isSwordAction ||
            isFistAction ||
            isToolAction ||
            isUltimateAction ||
            state == AnimationState.ENERGY_CHARGE ||
            state == AnimationState.ENERGY_CAST ||
            state == AnimationState.DODGE ||
            state == AnimationState.HIT

        val blend = (delta * if (isSwordAction || isFistAction || isToolAction) 32f else if (isCombatAction) 24f else 14f).coerceIn(0.10f, 1f)

        val p = attackProgress.coerceIn(0f, 1f)
        val swingArc = sin(p * MathUtils.PI)

        val isHoldingNonToolResource = equippedToolId != null && heldResourceNodeMap.containsKey(equippedToolId)
        val visibleToolId = if (isUltimateAction || isSwordAction || isFistAction) {
            null
        } else if (isToolAction) {
            equippedToolId ?: when (state) {
                AnimationState.TOOL_AXE_CHOP -> "stone_axe"
                AnimationState.TOOL_PICKAXE_MINE -> "stone_pickaxe"
                AnimationState.TOOL_SHOVEL_DIG -> "shovel"
                AnimationState.TOOL_SICKLE_HARVEST -> "sickle"
                AnimationState.TOOL_KNIFE_SLICE -> "knife"
                AnimationState.TOOL_HAMMER_BUILD -> "builders_hammer"
                else -> null
            }
        } else if (isHoldingNonToolResource || !isSwordEquipped) {
            equippedToolId
        } else {
            null
        }
        val showSwordInHand = isSwordEquipped && !isToolAction && !isUltimateAction && visibleToolId == null

        // Toggle visibility of sheathed hilt vs. right-hand drawn sword
        sheathedHiltNode?.let {
            val s = if (showSwordInHand) 0.0001f else 1f
            it.scale.set(s, s, s)
        }

        var targetPelvisY = 0.90f
        var targetPelvisYaw = 0f
        var targetPelvisPitch = slopePitch
        var targetPelvisRoll = slopeRoll
        var targetTorsoPitch = 0f
        var targetTorsoYaw = 0f
        var targetHeadPitch = 0f
        var targetCloakPitch = 8f
        var targetCloakRoll = 0f
        var targetHairPitch = 0f

        var targetLArmPitch = 0f
        var targetLArmYaw = 0f
        var targetLArmRoll = 8f
        var targetLForearmPitch = -12f

        var targetRArmPitch = 0f
        var targetRArmYaw = 0f
        var targetRArmRoll = -8f
        var targetRForearmPitch = if (isSwordEquipped) -26f else -12f

        var targetSwordPitch = 0f
        var targetSwordYaw = 0f
        var targetSwordRoll = 0f

        var targetLThighPitch = 0f
        var targetLThighRoll = 0f
        var targetLShinPitch = 0f
        var targetRThighPitch = 0f
        var targetRThighRoll = 0f
        var targetRShinPitch = 0f

        when (state) {
            AnimationState.IDLE -> {
                val breath = sin(idleTime * 2.4f)
                val breeze = sin(idleTime * 1.7f + 0.6f)
                targetPelvisY = 0.90f + breath * 0.012f
                targetTorsoPitch = if (isSwordEquipped) 4f + breath * 1.8f else 3f + breath * 2.0f
                targetTorsoYaw = if (isSwordEquipped) -12f else 14f
                targetHeadPitch = -breath * 1.5f
                targetCloakPitch = 8f + (breeze + 1f) * 4.5f
                targetCloakRoll = cos(idleTime * 1.3f) * 3.5f
                targetHairPitch = (breeze + 1f) * 3.2f

                // In Fists stance, adopt an agile anime martial arts guard ready stance
                targetLArmPitch = if (isSwordEquipped) -16f + breath * 2f else -36f + breath * 2.5f
                targetLArmYaw = if (isSwordEquipped) 0f else -12f
                targetLArmRoll = if (isSwordEquipped) 18f else 14f
                targetLForearmPitch = if (isSwordEquipped) -34f else -68f

                targetRArmPitch = if (isSwordEquipped) -24f - breath * 2f else -44f - breath * 2.5f
                targetRArmYaw = if (isSwordEquipped) -14f else 12f
                targetRArmRoll = if (isSwordEquipped) -22f else -14f
                targetRForearmPitch = if (isSwordEquipped) -28f else -74f
                targetSwordPitch = if (isSwordEquipped) -12f else 0f
                targetSwordYaw = if (isSwordEquipped) 10f else 0f

                targetLThighPitch = if (isSwordEquipped) -8f else -10f
                targetLShinPitch = if (isSwordEquipped) 10f else 12f
                targetRThighPitch = if (isSwordEquipped) 8f else 10f
                targetRShinPitch = if (isSwordEquipped) 6f else 8f
            }

            AnimationState.WALK -> {
                val strideLength = 1.35f
                val physicalDisplacement = if (actualHorizontalSpeed > 0.05f) actualHorizontalSpeed * delta else (horizontalSpeedRatio * 4.6f * delta)
                val strideDelta = (physicalDisplacement / strideLength) * (MathUtils.PI * 2f)
                animPhase += strideDelta
                val s = sin(animPhase)
                val c = cos(animPhase)

                targetPelvisY = 0.895f + abs(c) * 0.022f
                targetTorsoPitch = 5f + abs(s) * 2f
                targetTorsoYaw = -s * 5f
                targetHeadPitch = -3f
                targetCloakPitch = 18f + abs(c) * 6f
                targetCloakRoll = s * 5f
                targetHairPitch = 8f + abs(c) * 4f

                targetLThighPitch = -s * 28f + (slopePitch * 0.4f)
                targetRThighPitch = s * 28f + (slopePitch * 0.4f)
                targetLThighRoll = (slopeRoll * 0.5f)
                targetRThighRoll = (slopeRoll * 0.5f)
                targetLShinPitch = max(0f, c) * 30f + 6f
                targetRShinPitch = max(0f, -c) * 30f + 6f

                targetLArmPitch = s * 24f
                targetRArmPitch = if (isSwordEquipped) 10f - s * 10f else -s * 24f
                targetLArmRoll = 8f
                targetRArmRoll = if (isSwordEquipped) -20f else -8f
                targetLForearmPitch = -20f - max(0f, -s) * 16f
                targetRForearmPitch = if (isSwordEquipped) -22f else -20f - max(0f, s) * 16f
                targetSwordPitch = if (isSwordEquipped) -18f else 0f
            }

            AnimationState.RUN -> {
                val runStrideLength = 2.45f
                val physicalRunDisplacement = if (actualHorizontalSpeed > 0.05f) actualHorizontalSpeed * delta else (horizontalSpeedRatio * 8.4f * delta)
                val runStrideDelta = (physicalRunDisplacement / runStrideLength) * (MathUtils.PI * 2f)
                animPhase += runStrideDelta
                val s = sin(animPhase)
                val c = cos(animPhase)

                targetPelvisY = 0.88f + abs(c) * 0.045f
                targetTorsoPitch = 14f + abs(c) * 3f
                targetTorsoYaw = -s * 8f
                targetHeadPitch = -9f
                targetCloakPitch = 32f + abs(s) * 10f
                targetCloakRoll = s * 8f
                targetHairPitch = 16f + abs(s) * 7f

                targetLThighPitch = -s * 48f + (slopePitch * 0.4f)
                targetRThighPitch = s * 48f + (slopePitch * 0.4f)
                targetLThighRoll = (slopeRoll * 0.5f)
                targetRThighRoll = (slopeRoll * 0.5f)
                targetLShinPitch = max(0f, c) * 56f + 12f
                targetRShinPitch = max(0f, -c) * 56f + 12f

                targetLArmPitch = s * 44f
                targetRArmPitch = if (isSwordEquipped) 28f - s * 12f else -s * 44f
                targetLArmRoll = 12f
                targetRArmRoll = if (isSwordEquipped) -26f else -12f
                targetLForearmPitch = -48f - max(0f, -s) * 22f
                targetRForearmPitch = if (isSwordEquipped) -18f else -48f - max(0f, s) * 22f
                targetSwordPitch = if (isSwordEquipped) -26f else 0f
                targetSwordYaw = if (isSwordEquipped) -18f else 0f
            }

            AnimationState.SPRINT -> {
                val sprintStrideLength = 3.35f
                val physicalSprintDisplacement = if (actualHorizontalSpeed > 0.05f) actualHorizontalSpeed * delta else (horizontalSpeedRatio * 13.4f * delta)
                val sprintStrideDelta = (physicalSprintDisplacement / sprintStrideLength) * (MathUtils.PI * 2f)
                animPhase += sprintStrideDelta
                val s = sin(animPhase)
                val c = cos(animPhase)

                targetPelvisY = 0.85f + abs(c) * 0.058f
                targetTorsoPitch = 24f + abs(c) * 4f
                targetTorsoYaw = -s * 10f
                targetHeadPitch = -16f
                targetCloakPitch = 48f + abs(s) * 12f
                targetCloakRoll = s * 10f
                targetHairPitch = 26f + abs(s) * 9f

                targetLThighPitch = -s * 62f + (slopePitch * 0.4f)
                targetRThighPitch = s * 62f + (slopePitch * 0.4f)
                targetLThighRoll = (slopeRoll * 0.5f)
                targetRThighRoll = (slopeRoll * 0.5f)
                targetLShinPitch = max(0f, c) * 68f + 16f
                targetRShinPitch = max(0f, -c) * 68f + 16f

                targetLArmPitch = s * 54f + 12f
                targetRArmPitch = if (isSwordEquipped) 42f else -s * 54f + 12f
                targetLArmRoll = 15f
                targetRArmRoll = if (isSwordEquipped) -28f else -18f
                targetLForearmPitch = -62f
                targetRForearmPitch = if (isSwordEquipped) -14f else -62f
                targetSwordPitch = if (isSwordEquipped) -34f else 0f
                targetSwordYaw = if (isSwordEquipped) -24f else 0f
            }

            AnimationState.JUMP -> {
                val airFactor = (verticalVelocity / 9.5f).coerceIn(-1f, 1f)
                targetPelvisY = 0.92f
                targetTorsoPitch = if (airFactor > 0f) -6f * airFactor else 12f * (-airFactor)
                targetHeadPitch = -targetTorsoPitch * 0.6f
                targetCloakPitch = 38f + (1f - airFactor) * 18f
                targetHairPitch = 20f + (1f - airFactor) * 12f

                targetLThighPitch = -38f
                targetLShinPitch = 56f
                targetRThighPitch = 18f
                targetRShinPitch = 32f

                targetLArmPitch = 22f
                targetRArmPitch = if (isSwordEquipped) -32f else 22f
                targetLArmRoll = 28f
                targetRArmRoll = -28f
                targetLForearmPitch = -36f
                targetRForearmPitch = -24f
            }

            AnimationState.DODGE -> {
                targetPelvisY = 0.80f
                targetTorsoPitch = 34f
                targetHeadPitch = -22f
                targetCloakPitch = 64f
                targetHairPitch = 35f

                targetLArmPitch = 48f
                targetRArmPitch = 48f
                targetLArmRoll = 22f
                targetRArmRoll = -22f
                targetLForearmPitch = -30f
                targetRForearmPitch = -30f

                targetLThighPitch = -42f
                targetLShinPitch = 50f
                targetRThighPitch = 32f
                targetRShinPitch = 24f
            }

            AnimationState.DODGE_SIDE -> {
                // Sideways evasive slip: upper torso leans, arms raised in tight guard
                targetPelvisY = 0.82f
                targetPelvisRoll = slopeRoll + 18f
                targetTorsoPitch = 12f
                targetTorsoYaw = 16f
                targetHeadPitch = -6f
                targetCloakPitch = 42f
                targetCloakRoll = -24f
                targetHairPitch = 22f

                targetLArmPitch = 28f
                targetRArmPitch = 24f
                targetLArmRoll = 36f
                targetRArmRoll = -18f
                targetLForearmPitch = -55f
                targetRForearmPitch = -45f

                targetLThighPitch = -18f
                targetLThighRoll = 24f
                targetLShinPitch = 38f
                targetRThighPitch = 14f
                targetRThighRoll = -16f
                targetRShinPitch = 28f
            }

            AnimationState.DODGE_BACK -> {
                // Backward hop/retreat step: torso tilts back slightly, feet spring backward
                targetPelvisY = 0.86f
                targetTorsoPitch = -16f
                targetHeadPitch = 14f
                targetCloakPitch = 20f
                targetHairPitch = 12f

                targetLArmPitch = -18f
                targetRArmPitch = -18f
                targetLArmRoll = 24f
                targetRArmRoll = -24f
                targetLForearmPitch = -42f
                targetRForearmPitch = -42f

                targetLThighPitch = 26f
                targetLShinPitch = 34f
                targetRThighPitch = -22f
                targetRShinPitch = 48f
            }

            AnimationState.DODGE_LOW -> {
                // Low crouch weave / duck: deep bend at knees and hips, blade/arms tucked
                targetPelvisY = 0.62f
                targetTorsoPitch = 44f
                targetHeadPitch = -28f
                targetCloakPitch = 70f
                targetHairPitch = 40f

                targetLArmPitch = 38f
                targetRArmPitch = 38f
                targetLArmRoll = 30f
                targetRArmRoll = -30f
                targetLForearmPitch = -65f
                targetRForearmPitch = -65f

                targetLThighPitch = -55f
                targetLShinPitch = 72f
                targetRThighPitch = -48f
                targetRShinPitch = 68f
            }

            AnimationState.DODGE_FORWARD -> {
                // Forward diagonal slide / slip: aggressive low forward burst
                targetPelvisY = 0.76f
                targetTorsoPitch = 38f
                targetHeadPitch = -26f
                targetCloakPitch = 72f
                targetHairPitch = 38f

                targetLArmPitch = 52f
                targetRArmPitch = 32f
                targetLArmRoll = 20f
                targetRArmRoll = -26f
                targetLForearmPitch = -28f
                targetRForearmPitch = -34f

                targetLThighPitch = -52f
                targetLShinPitch = 58f
                targetRThighPitch = 36f
                targetRShinPitch = 22f
            }

            // =========================================================================
            // MILESTONE 4 — ADVANCED ANIME FISTS MARTIAL ARTS ANIMATIONS
            // Full-body articulation with anticipation, acceleration, contact & recovery
            // =========================================================================
            AnimationState.FIST_RIGHT_JAB, AnimationState.LIGHT_ATTACK_1 -> {
                // Step 1: Fast Right Jab — chambered coil (0..0.22), snapping extension (0.22..0.60), guard recoil (0.60..1.0)
                val strike = punchCurve(p, peakAt = 0.42f)
                targetPelvisY = 0.86f - strike * 0.03f
                targetPelvisYaw = -16f + strike * 34f
                targetTorsoPitch = 8f + strike * 8f
                targetTorsoYaw = -18f + strike * 46f
                targetHeadPitch = -6f
                targetCloakPitch = 24f + strike * 16f
                targetCloakRoll = 14f * strike

                // Right arm snaps straight out from guard to full extension
                targetRArmPitch = -38f - strike * 62f
                targetRArmYaw = 10f - strike * 26f
                targetRArmRoll = -12f
                targetRForearmPitch = -76f * (1f - strike) - 4f

                // Left arm stays chambered high protecting the chin
                targetLArmPitch = -42f + strike * 18f
                targetLArmYaw = -16f
                targetLArmRoll = 18f
                targetLForearmPitch = -82f

                // Lead step with right leg driving forward
                targetRThighPitch = -28f * strike - 8f
                targetRShinPitch = 26f * strike + 10f
                targetLThighPitch = 24f * strike + 6f
                targetLShinPitch = 14f
            }

            AnimationState.FIST_LEFT_CROSS, AnimationState.LIGHT_ATTACK_2 -> {
                // Step 2: Power Left Cross with forward movement — deep hip rotation & rear-foot drive
                val coil = (p / 0.24f).coerceIn(0f, 1f)
                val strike = punchCurve(p, peakAt = 0.48f)
                targetPelvisY = 0.83f - strike * 0.04f
                targetPelvisYaw = 24f * coil - strike * 58f
                targetTorsoPitch = 14f + strike * 10f
                targetTorsoYaw = 28f * coil - strike * 72f
                targetHeadPitch = -10f
                targetCloakPitch = 32f + strike * 22f
                targetCloakRoll = -22f * strike
                targetHairPitch = 18f + strike * 10f

                // Left arm drives forward with full shoulder & torso rotation
                targetLArmPitch = -28f - strike * 76f
                targetLArmYaw = -14f + strike * 30f
                targetLArmRoll = 14f
                targetLForearmPitch = -78f * (1f - strike) - 4f

                // Right arm pulls back to hip in martial arts chamber
                targetRArmPitch = -24f + strike * 48f
                targetRArmYaw = 18f
                targetRArmRoll = -22f
                targetRForearmPitch = -72f

                targetLThighPitch = -38f * strike
                targetLShinPitch = 40f * strike + 8f
                targetRThighPitch = 34f * strike
                targetRShinPitch = 18f
            }

            AnimationState.FIST_SIDE_KICK -> {
                // Step 3: Powerful Side Kick — knee chamber (0..0.28) -> explosive horizontal side kick extension (0.28..0.68) -> recovery
                val chamber = (p / 0.28f).coerceIn(0f, 1f)
                val kickExt = punchCurve(p, peakAt = 0.50f)
                targetPelvisY = 0.88f + kickExt * 0.05f
                targetPelvisYaw = -32f * chamber - kickExt * 44f
                // Lean torso away from the kicking leg as natural martial arts counterbalance
                targetTorsoPitch = -18f * kickExt
                targetTorsoYaw = 28f * kickExt
                targetHeadPitch = 10f * kickExt
                targetCloakPitch = 44f + kickExt * 20f
                targetCloakRoll = 28f * kickExt
                targetHairPitch = 22f

                // Right leg chambers high then snaps out horizontally toward target
                targetRThighPitch = -56f * chamber - kickExt * 46f
                targetRThighRoll = -38f * kickExt
                targetRShinPitch = 82f * chamber * (1f - kickExt) + 6f

                // Supporting left leg planted firmly
                targetLThighPitch = 12f * kickExt
                targetLThighRoll = 12f * kickExt
                targetLShinPitch = 18f

                // Counterbalance guard arms
                targetRArmPitch = -54f * kickExt
                targetRArmRoll = -34f
                targetRForearmPitch = -36f
                targetLArmPitch = 34f * kickExt
                targetLArmRoll = 28f
                targetLForearmPitch = -52f
            }

            AnimationState.FIST_SPIN_ROUNDHOUSE, AnimationState.LIGHT_ATTACK_3 -> {
                // Step 4: Spinning 360-degree Roundhouse Kick with sweeping hip rotation & extended right leg arc
                val ease = smoothStep(p)
                targetPelvisY = 0.92f + swingArc * 0.10f
                targetPelvisYaw = ease * 360f
                targetTorsoPitch = -18f * swingArc
                targetTorsoYaw = 24f * swingArc
                targetCloakPitch = 58f
                targetCloakRoll = 34f * swingArc
                targetHairPitch = 28f

                targetRThighPitch = -102f * swingArc
                targetRThighRoll = -32f * swingArc
                targetRShinPitch = 54f * (1f - swingArc) + 6f

                targetLThighPitch = 10f
                targetLShinPitch = 16f

                targetLArmPitch = -48f
                targetLArmRoll = 38f
                targetLForearmPitch = -34f
                targetRArmPitch = 40f
                targetRArmRoll = -34f
                targetRForearmPitch = -40f
            }

            AnimationState.FIST_RISING_HIGH_KICK -> {
                // Step 5: Rising High Kick (Vertical Flash / Axe-Rising Kick) — low crouch into high vertical leg arc
                val crouch = (p / 0.24f).coerceIn(0f, 1f)
                val riseKick = punchCurve(p, peakAt = 0.54f)
                targetPelvisY = 0.80f - crouch * 0.06f + riseKick * 0.22f
                targetPelvisYaw = 18f * riseKick
                targetTorsoPitch = 20f * crouch * (1f - riseKick) - 28f * riseKick
                targetTorsoYaw = -22f * riseKick
                targetHeadPitch = 14f * riseKick
                targetCloakPitch = 52f + riseKick * 18f
                targetHairPitch = 28f

                // Left leg sweeps from ground straight overhead (128 degrees)
                targetLThighPitch = -128f * riseKick
                targetLThighRoll = 14f * riseKick
                targetLShinPitch = 48f * (1f - riseKick) + 4f

                targetRThighPitch = 22f * riseKick
                targetRShinPitch = 20f

                targetLArmPitch = 42f * riseKick
                targetLArmRoll = 26f
                targetRArmPitch = -64f * riseKick
                targetRArmRoll = -24f
                targetRForearmPitch = -42f
            }

            AnimationState.FIST_FINISHER_CYCLONE_KICK -> {
                // Step 6 Finisher A: Spectacular 540-degree Airborne Cyclone Hurricane Kick
                val spinEase = smoothStep(p)
                targetPelvisY = 0.94f + swingArc * 0.16f
                targetPelvisYaw = spinEase * 540f
                targetTorsoPitch = -22f * swingArc
                targetTorsoYaw = 30f
                targetCloakPitch = 68f
                targetCloakRoll = 42f
                targetHairPitch = 34f

                // Alternating dual-leg hurricane kick extension across the 540 spin
                val firstHalf = if (p < 0.5f) sin(p * 2f * MathUtils.PI) else 0f
                val secondHalf = if (p >= 0.5f) sin((p - 0.5f) * 2f * MathUtils.PI) else 0f

                targetLThighPitch = -98f * firstHalf - 28f * secondHalf
                targetLThighRoll = 28f * firstHalf
                targetLShinPitch = 10f + 45f * (1f - firstHalf)

                targetRThighPitch = -108f * secondHalf - 24f * firstHalf
                targetRThighRoll = -34f * secondHalf
                targetRShinPitch = 8f + 42f * (1f - secondHalf)

                targetLArmPitch = -56f
                targetLArmRoll = 48f
                targetRArmPitch = -56f
                targetRArmRoll = -48f
            }

            AnimationState.FIST_FINISHER_FLYING_STRIKE, AnimationState.AERIAL_ATTACK -> {
                // Step 6 Finisher B / Aerial Strike: Leaping Flying Dragon Kick & Twin-Palm Impulse
                val coil = (p / 0.25f).coerceIn(0f, 1f)
                val thrust = punchCurve(p, peakAt = 0.56f)
                targetPelvisY = 0.92f + thrust * 0.12f
                targetPelvisYaw = -22f * (1f - thrust) + 18f * thrust
                targetTorsoPitch = 16f * coil - 26f * thrust
                targetTorsoYaw = 24f * thrust
                targetCloakPitch = 64f
                targetCloakRoll = -20f * thrust
                targetHairPitch = 32f

                // Right leg fully extended in a flying dragon kick while left knee is tucked tight
                targetRThighPitch = -42f * coil - 62f * thrust
                targetRThighRoll = -16f * thrust
                targetRShinPitch = 68f * coil * (1f - thrust) + 4f

                targetLThighPitch = -64f * thrust
                targetLShinPitch = 88f * thrust + 18f

                targetRArmPitch = -86f * thrust
                targetRArmYaw = -12f
                targetRForearmPitch = -14f
                targetLArmPitch = 44f * thrust
                targetLArmRoll = 30f
                targetLForearmPitch = -45f
            }

            AnimationState.FIST_HEAVY_DRAGON_HAYMAKER, AnimationState.HEAVY_ATTACK -> {
                // Heavy 1: Dragon Haymaker — Dramatic coiled body rotation (0..0.34) -> explosive lunging Dragon Punch (0.34..1.0)
                val windup = (p / 0.34f).coerceIn(0f, 1f)
                val burst = smoothStep(((p - 0.32f) / 0.42f).coerceIn(0f, 1f))
                val settle = ((p - 0.78f) / 0.22f).coerceIn(0f, 1f)
                val power = burst * (1f - settle * 0.35f)

                targetPelvisY = 0.78f - windup * 0.05f + power * 0.06f
                targetPelvisYaw = -48f * windup + power * 96f
                targetTorsoPitch = -12f * windup * (1f - burst) + 32f * power
                targetTorsoYaw = -58f * windup + power * 118f
                targetHeadPitch = 8f * windup * (1f - burst) - 18f * power
                targetCloakPitch = 56f + power * 18f
                targetCloakRoll = -28f * windup + power * 58f
                targetHairPitch = 32f

                // Right arm coils deep behind the torso then unleashes a sweeping rotational haymaker
                targetRArmPitch = 38f * windup * (1f - burst) - 106f * power
                targetRArmYaw = -46f * windup * (1f - burst) + 28f * power
                targetRArmRoll = -34f * windup + 12f * power
                targetRForearmPitch = -84f * windup * (1f - burst) - 8f * power

                // Left arm counter-pulls to hip
                targetLArmPitch = -64f * windup * (1f - burst) + 52f * power
                targetLArmYaw = 24f * windup
                targetLArmRoll = 28f
                targetLForearmPitch = -58f

                targetRThighPitch = 28f * windup * (1f - burst) - 48f * power
                targetRShinPitch = 24f + 32f * power
                targetLThighPitch = -32f * windup * (1f - burst) + 42f * power
                targetLShinPitch = 22f
            }

            AnimationState.FIST_HEAVY_THUNDER_BREAKER -> {
                // Heavy 2: Thunder Breaker — Coiled stance (0..0.28) -> devastating 360 spinning heavy axe-roundhouse kick
                val coil = (p / 0.28f).coerceIn(0f, 1f)
                val spinPhase = smoothStep(((p - 0.24f) / 0.76f).coerceIn(0f, 1f))
                val kickArc = sin(spinPhase * MathUtils.PI)

                targetPelvisY = 0.80f - coil * 0.05f + kickArc * 0.18f
                targetPelvisYaw = -36f * coil * (1f - spinPhase) + spinPhase * 360f
                targetTorsoPitch = 18f * coil * (1f - spinPhase) - 24f * kickArc
                targetTorsoYaw = -32f * coil + 42f * kickArc
                targetCloakPitch = 64f
                targetCloakRoll = 38f * kickArc
                targetHairPitch = 34f

                targetRThighPitch = -118f * kickArc
                targetRThighRoll = -36f * kickArc
                targetRShinPitch = 64f * coil * (1f - kickArc) + 6f

                targetLThighPitch = 18f * kickArc
                targetLShinPitch = 22f

                targetLArmPitch = -62f * kickArc
                targetLArmRoll = 44f
                targetRArmPitch = 46f * kickArc
                targetRArmRoll = -38f
            }

            AnimationState.FIST_HEAVY_METEOR_STRIKE -> {
                // Heavy 3: Meteor Strike — High aerial tuck (0..0.38) -> blazing downward diving heel slam (0.38..1.0)
                val rise = (p / 0.38f).coerceIn(0f, 1f)
                val dive = smoothStep(((p - 0.36f) / 0.64f).coerceIn(0f, 1f))
                targetPelvisY = 0.96f + rise * 0.08f - dive * 0.18f
                targetPelvisYaw = 22f * dive
                targetTorsoPitch = -28f * rise * (1f - dive) + 42f * dive
                targetTorsoYaw = -24f * rise + 34f * dive
                targetHeadPitch = -16f
                targetCloakPitch = 70f
                targetHairPitch = 38f

                // Right leg chambers high at the apex, then drives straight down into the ground
                targetRThighPitch = -112f * rise * (1f - dive) - 52f * dive
                targetRThighRoll = -14f
                targetRShinPitch = 84f * rise * (1f - dive) + 12f * dive

                targetLThighPitch = -56f * rise + 38f * dive
                targetLShinPitch = 72f * rise * (1f - dive) + 28f * dive

                targetRArmPitch = -140f * rise * (1f - dive) + 44f * dive
                targetRArmRoll = -28f
                targetLArmPitch = -140f * rise * (1f - dive) - 48f * dive
                targetLArmRoll = 28f
            }

            // =========================================================================
            // MILESTONE 3 — DEDICATED ANIME SWORD COMBAT ANIMATIONS
            // =========================================================================
            AnimationState.SWORD_HORIZONTAL_SLASH -> {
                // Wide right-to-left horizontal sword sweep at chest level
                val ease = smoothStep(p)
                targetPelvisY = 0.83f
                targetTorsoPitch = 14f
                targetTorsoYaw = -56f + ease * 118f
                targetHeadPitch = -8f
                targetCloakPitch = 36f + swingArc * 22f
                targetCloakRoll = -22f + ease * 44f

                targetRArmPitch = -86f
                targetRArmYaw = -64f + ease * 132f
                targetRArmRoll = -72f
                targetRForearmPitch = -8f
                // Rotate blade horizontal and sweeping forward along the arc
                targetSwordPitch = 6f
                targetSwordYaw = -35f + ease * 78f
                targetSwordRoll = 85f

                targetLArmPitch = 28f
                targetLArmYaw = 32f
                targetLArmRoll = 26f
                targetLForearmPitch = -32f

                targetRThighPitch = -36f
                targetRShinPitch = 38f
                targetLThighPitch = 28f
                targetLShinPitch = 18f
            }

            AnimationState.SWORD_DIAGONAL_CROSS -> {
                // Two-phase X-Cross Slash:
                // Phase 1 (0..0.5): High-Left to Low-Right diagonal cut
                // Phase 2 (0.5..1.0): Low-Left to High-Right rising cross cut
                if (p < 0.5f) {
                    val sub = smoothStep(p * 2f)
                    targetPelvisY = 0.84f
                    targetTorsoPitch = 12f + sub * 10f
                    targetTorsoYaw = 48f - sub * 96f
                    targetCloakPitch = 40f
                    targetCloakRoll = 22f - sub * 44f

                    targetRArmPitch = -132f + sub * 88f
                    targetRArmYaw = 52f - sub * 108f
                    targetRArmRoll = -38f
                    targetRForearmPitch = -10f
                    targetSwordPitch = -24f + sub * 56f
                    targetSwordYaw = 32f - sub * 68f
                    targetSwordRoll = -45f
                } else {
                    val sub = smoothStep((p - 0.5f) * 2f)
                    targetPelvisY = 0.82f + sub * 0.06f
                    targetTorsoPitch = 18f - sub * 12f
                    targetTorsoYaw = -48f + sub * 102f
                    targetCloakPitch = 46f
                    targetCloakRoll = -24f + sub * 48f

                    targetRArmPitch = -42f - sub * 94f
                    targetRArmYaw = -54f + sub * 112f
                    targetRArmRoll = -44f
                    targetRForearmPitch = -8f
                    targetSwordPitch = 30f - sub * 64f
                    targetSwordYaw = -36f + sub * 74f
                    targetSwordRoll = 48f
                }
                targetLArmPitch = 32f
                targetLArmRoll = 28f
                targetLThighPitch = -32f
                targetLShinPitch = 34f
                targetRThighPitch = 26f
                targetRShinPitch = 18f
            }

            AnimationState.SWORD_RISING_SLASH -> {
                // Deep low crouch coiling upward into a dramatic vertical rising dragon slash
                val ease = smoothStep(p)
                targetPelvisY = 0.76f + ease * 0.22f
                targetTorsoPitch = 28f - ease * 42f
                targetTorsoYaw = -38f + ease * 68f
                targetHeadPitch = -12f + ease * 18f
                targetCloakPitch = 54f
                targetHairPitch = 28f

                targetRArmPitch = -22f - ease * 138f
                targetRArmYaw = -28f + ease * 48f
                targetRArmRoll = -18f
                targetRForearmPitch = -14f
                targetSwordPitch = 42f - ease * 88f
                targetSwordYaw = 0f
                targetSwordRoll = 12f

                targetLArmPitch = 36f - ease * 60f
                targetLArmRoll = 24f

                targetRThighPitch = -46f + ease * 24f
                targetRShinPitch = 52f - ease * 32f
                targetLThighPitch = 30f - ease * 42f
                targetLShinPitch = 24f
            }

            AnimationState.SWORD_SPIN_360 -> {
                // Full 360-degree spinning horizontal blade whirlwind
                targetPelvisY = 0.85f + swingArc * 0.06f
                targetPelvisYaw = p * 360f
                targetTorsoPitch = 18f
                targetTorsoYaw = 24f
                targetCloakPitch = 64f
                targetCloakRoll = 32f * swingArc

                targetRArmPitch = -90f
                targetRArmYaw = 18f
                targetRArmRoll = -78f
                targetRForearmPitch = -4f
                targetSwordPitch = 0f
                targetSwordYaw = 28f
                targetSwordRoll = 90f

                targetLArmPitch = -70f
                targetLArmRoll = 68f
                targetLThighPitch = -34f
                targetLShinPitch = 36f
                targetRThighPitch = 28f
                targetRShinPitch = 22f
            }

            AnimationState.SWORD_RAPID_FLURRY -> {
                // High-speed 4-cut anime multi-slash sequence + final horizontal cleave
                val cycle = sin(p * MathUtils.PI * 4f)
                val cosCycle = cos(p * MathUtils.PI * 4f)
                targetPelvisY = 0.82f
                targetTorsoPitch = 20f
                targetTorsoYaw = cycle * 54f
                targetCloakPitch = 58f
                targetCloakRoll = -cycle * 28f
                targetHairPitch = 30f

                targetRArmPitch = -88f + cosCycle * 36f
                targetRArmYaw = cycle * 68f
                targetRArmRoll = -52f
                targetRForearmPitch = -6f
                targetSwordPitch = cosCycle * 38f
                targetSwordYaw = cycle * 48f
                targetSwordRoll = if (cycle >= 0f) 65f else -65f

                targetLArmPitch = 36f
                targetLArmRoll = 30f
                targetLThighPitch = -38f
                targetLShinPitch = 42f
                targetRThighPitch = 34f
                targetRShinPitch = 20f
            }

            AnimationState.SWORD_PHANTOM_DASH_SLASH -> {
                // Ultra-fast iaido dash lunge sweeping from left hip across to wide right extension
                val ease = smoothStep(p)
                targetPelvisY = 0.78f
                targetTorsoPitch = 32f
                targetTorsoYaw = 52f - ease * 116f
                targetHeadPitch = -20f
                targetCloakPitch = 68f
                targetCloakRoll = 26f - ease * 52f
                targetHairPitch = 36f

                targetRArmPitch = -84f
                targetRArmYaw = 62f - ease * 136f
                targetRArmRoll = -76f
                targetRForearmPitch = -6f
                targetSwordPitch = 8f
                targetSwordYaw = 42f - ease * 88f
                targetSwordRoll = -86f

                targetLArmPitch = 44f
                targetLArmRoll = 28f
                targetLThighPitch = -48f
                targetLShinPitch = 54f
                targetRThighPitch = 38f
                targetRShinPitch = 22f
            }

            AnimationState.SWORD_FLASH_STEP -> {
                // Sleek phantom vanish-and-reappear dash pose with reverse-held guard
                targetPelvisY = 0.79f
                targetTorsoPitch = 36f
                targetTorsoYaw = -24f
                targetHeadPitch = -24f
                targetCloakPitch = 70f
                targetHairPitch = 38f

                targetRArmPitch = 36f
                targetRArmYaw = -22f
                targetRArmRoll = -32f
                targetRForearmPitch = -18f
                targetSwordPitch = -35f
                targetSwordRoll = 45f

                targetLArmPitch = 46f
                targetLArmRoll = 24f
                targetLThighPitch = -44f
                targetLShinPitch = 52f
                targetRThighPitch = 34f
                targetRShinPitch = 24f
            }

            AnimationState.SWORD_INFERNO_CRESCENT -> {
                // Heavy Skill 1: Deep windup on right side -> explosive horizontal fiery crescent cleave
                val windup = (p / 0.30f).coerceIn(0f, 1f)
                val cleave = smoothStep(((p - 0.30f) / 0.70f).coerceIn(0f, 1f))
                targetPelvisY = 0.80f
                targetTorsoPitch = 18f + cleave * 10f
                targetTorsoYaw = -68f * windup + cleave * 142f
                targetHeadPitch = -12f
                targetCloakPitch = 58f
                targetCloakRoll = -30f + cleave * 64f

                targetRArmPitch = -92f
                targetRArmYaw = -74f * windup + cleave * 152f
                targetRArmRoll = -78f
                targetRForearmPitch = -6f
                targetSwordPitch = 4f
                targetSwordYaw = -42f + cleave * 94f
                targetSwordRoll = 88f

                targetLArmPitch = -68f + cleave * 96f
                targetLArmYaw = -40f + cleave * 82f
                targetLThighPitch = -42f
                targetLShinPitch = 46f
                targetRThighPitch = 36f
                targetRShinPitch = 22f
            }

            AnimationState.SWORD_DRAGON_TEMPEST -> {
                // Heavy Skill 2: 720-degree double-rotation Dragon Tempest cyclone slash
                targetPelvisY = 0.84f + swingArc * 0.10f
                targetPelvisYaw = p * 720f
                targetTorsoPitch = 22f
                targetTorsoYaw = 30f
                targetCloakPitch = 72f
                targetCloakRoll = 38f
                targetHairPitch = 36f

                targetRArmPitch = -92f
                targetRArmYaw = 20f
                targetRArmRoll = -82f
                targetRForearmPitch = -4f
                targetSwordPitch = 0f
                targetSwordYaw = 34f
                targetSwordRoll = 90f

                targetLArmPitch = -82f
                targetLArmRoll = 74f
                targetLThighPitch = -36f
                targetLShinPitch = 40f
                targetRThighPitch = 32f
                targetRShinPitch = 24f
            }

            AnimationState.SWORD_AERIAL_STRIKE -> {
                // Heavy Skill 3: Leaping overhead somersault into a devastating vertical downward cleave
                val rise = (p / 0.38f).coerceIn(0f, 1f)
                val plunge = smoothStep(((p - 0.38f) / 0.62f).coerceIn(0f, 1f))
                targetPelvisY = 0.90f - plunge * 0.12f
                targetTorsoPitch = -24f * rise + 56f * plunge
                targetTorsoYaw = 12f * (1f - plunge)
                targetHeadPitch = -16f
                targetCloakPitch = 68f
                targetHairPitch = 36f

                targetRArmPitch = -168f * (1f - plunge) - 24f * plunge
                targetRArmYaw = 8f * (1f - plunge)
                targetRArmRoll = -14f
                targetRForearmPitch = -8f
                targetSwordPitch = -18f + plunge * 52f
                targetSwordYaw = 0f
                targetSwordRoll = 0f

                targetLArmPitch = -145f * (1f - plunge) + 38f * plunge
                targetLArmRoll = 22f
                targetLThighPitch = -46f
                targetLShinPitch = 56f
                targetRThighPitch = 34f
                targetRShinPitch = 28f
            }

            AnimationState.ENERGY_CHARGE -> {
                val pulse = sin(idleTime * 14f) * 3f
                targetPelvisY = 0.82f
                targetTorsoPitch = 10f
                targetTorsoYaw = -22f
                targetHeadPitch = -6f
                targetCloakPitch = 42f + abs(pulse) * 2f
                targetHairPitch = 24f + abs(pulse) * 2f

                targetLArmPitch = -58f + pulse
                targetLArmYaw = 34f
                targetLArmRoll = -12f
                targetLForearmPitch = -42f

                targetRArmPitch = -52f - pulse
                targetRArmYaw = -28f
                targetRArmRoll = 12f
                targetRForearmPitch = -48f

                targetLThighPitch = -28f
                targetLShinPitch = 34f
                targetRThighPitch = 24f
                targetRShinPitch = 28f
            }

            AnimationState.ENERGY_CAST -> {
                targetPelvisY = 0.85f
                targetTorsoPitch = 18f
                targetTorsoYaw = 0f
                targetCloakPitch = 56f
                targetHairPitch = 30f

                targetLArmPitch = -92f
                targetLArmYaw = 14f
                targetLForearmPitch = -8f

                targetRArmPitch = -92f
                targetRArmYaw = -14f
                targetRForearmPitch = -8f

                targetLThighPitch = -34f
                targetLShinPitch = 38f
                targetRThighPitch = 30f
                targetRShinPitch = 22f
            }

            // =========================================================================
            // PHASE 2 SURVIVAL GATHERING TOOL ANIMATIONS
            // =========================================================================
            AnimationState.TOOL_AXE_CHOP -> {
                // Forceful diagonal lumberjack axe cleave from right shoulder across torso
                val chopCurve = punchCurve(p, 0.42f)
                val windup = if (p < 0.28f) smoothStep(p / 0.28f) else 1f - smoothStep((p - 0.28f) / 0.72f)
                targetPelvisY = 0.85f - chopCurve * 0.06f
                targetTorsoPitch = -10f * windup + 22f * chopCurve
                targetTorsoYaw = -34f * windup + 48f * chopCurve
                targetHeadPitch = 8f * chopCurve
                targetCloakPitch = 20f + chopCurve * 16f

                targetRArmPitch = -105f * windup - 38f * chopCurve
                targetRArmYaw = -36f * windup + 52f * chopCurve
                targetRArmRoll = -24f
                targetRForearmPitch = -55f * windup - 16f * chopCurve

                targetLArmPitch = -70f * windup - 42f * chopCurve
                targetLArmYaw = 18f * windup + 28f * chopCurve
                targetLArmRoll = 18f
                targetLForearmPitch = -48f

                targetSwordPitch = 14f * chopCurve
                targetSwordRoll = -28f * windup + 35f * chopCurve

                targetLThighPitch = -18f
                targetLShinPitch = 22f
                targetRThighPitch = 16f
                targetRShinPitch = 18f
            }

            AnimationState.TOOL_PICKAXE_MINE -> {
                // High overhead two-phase mining arch followed by downward rock-shattering strike
                val strike = punchCurve(p, 0.48f)
                val raise = if (p < 0.36f) smoothStep(p / 0.36f) else 1f - smoothStep((p - 0.36f) / 0.64f)
                targetPelvisY = 0.86f - strike * 0.08f
                targetTorsoPitch = -18f * raise + 32f * strike
                targetTorsoYaw = -12f * raise + 10f * strike
                targetHeadPitch = -12f * raise + 18f * strike
                targetCloakPitch = 24f + strike * 18f

                targetRArmPitch = -148f * raise - 24f * strike
                targetRArmYaw = -10f * raise + 6f * strike
                targetRArmRoll = -12f
                targetRForearmPitch = -64f * raise - 12f * strike

                targetLArmPitch = -110f * raise - 32f * strike
                targetLArmYaw = 14f
                targetLArmRoll = 16f
                targetLForearmPitch = -52f

                targetSwordPitch = -22f * raise + 38f * strike
                targetSwordYaw = 0f
                targetSwordRoll = 0f

                targetLThighPitch = -20f * strike
                targetLShinPitch = 26f * strike
                targetRThighPitch = 18f * strike
                targetRShinPitch = 20f * strike
            }

            AnimationState.TOOL_SHOVEL_DIG -> {
                // Low earth scoop followed by upward pry & toss motion
                val scoop = if (p < 0.45f) smoothStep(p / 0.45f) else 1f - smoothStep((p - 0.45f) / 0.55f)
                val toss = if (p >= 0.40f) punchCurve((p - 0.40f) / 0.60f, 0.50f) else 0f
                targetPelvisY = 0.80f - scoop * 0.10f + toss * 0.06f
                targetTorsoPitch = 34f * scoop - 14f * toss
                targetTorsoYaw = -16f * scoop + 18f * toss
                targetHeadPitch = 20f * scoop - 8f * toss
                targetCloakPitch = 18f + toss * 16f

                targetRArmPitch = -28f * scoop - 92f * toss
                targetRArmYaw = -14f * scoop + 12f * toss
                targetRArmRoll = -14f
                targetRForearmPitch = -24f * scoop - 58f * toss

                targetLArmPitch = -46f * scoop - 76f * toss
                targetLArmYaw = 22f
                targetLArmRoll = 16f
                targetLForearmPitch = -42f

                targetSwordPitch = 36f * scoop - 28f * toss
                targetSwordRoll = 12f * toss

                targetLThighPitch = -26f * scoop
                targetLShinPitch = 34f * scoop
                targetRThighPitch = 20f * scoop
                targetRShinPitch = 24f * scoop
            }

            AnimationState.TOOL_SICKLE_HARVEST -> {
                // Agile low horizontal crescent sweep across flora and tall grass
                val sweep = punchCurve(p, 0.44f)
                val sweepAngle = (p - 0.5f) * 2f // -1 to +1
                targetPelvisY = 0.82f - sweep * 0.07f
                targetTorsoPitch = 22f * sweep
                targetTorsoYaw = sweepAngle * 42f
                targetHeadPitch = 14f * sweep
                targetCloakPitch = 22f + sweep * 14f

                targetRArmPitch = -56f - sweep * 18f
                targetRArmYaw = -48f + p * 96f
                targetRArmRoll = -32f
                targetRForearmPitch = -28f

                targetLArmPitch = -28f
                targetLArmYaw = -22f
                targetLArmRoll = 24f
                targetLForearmPitch = -44f

                targetSwordYaw = 24f * sweep
                targetSwordRoll = -72f

                targetLThighPitch = -22f * sweep
                targetLShinPitch = 28f * sweep
                targetRThighPitch = 18f * sweep
                targetRShinPitch = 22f * sweep
            }

            AnimationState.TOOL_KNIFE_SLICE -> {
                // Precise close-range forward-and-across utility carving/slicing stroke
                val slice = punchCurve(p, 0.38f)
                val cross = sin(p * MathUtils.PI * 2f)
                targetPelvisY = 0.86f - slice * 0.04f
                targetTorsoPitch = 14f * slice
                targetTorsoYaw = 24f * slice + cross * 12f
                targetHeadPitch = 10f * slice
                targetCloakPitch = 16f + slice * 10f

                targetRArmPitch = -68f - slice * 24f
                targetRArmYaw = 18f * slice + cross * 28f
                targetRArmRoll = -18f
                targetRForearmPitch = -36f + slice * 22f

                targetLArmPitch = -34f
                targetLArmYaw = -16f
                targetLArmRoll = 16f
                targetLForearmPitch = -52f

                targetSwordPitch = 18f * slice
                targetSwordYaw = cross * 20f
                targetSwordRoll = -45f

                targetLThighPitch = -14f * slice
                targetLShinPitch = 18f * slice
                targetRThighPitch = 12f * slice
                targetRShinPitch = 16f * slice
            }

            AnimationState.TOOL_HAMMER_BUILD -> {
                // Sturdy overhead mallet strike for building repair, upgrade, and construction
                val strike = punchCurve(p, 0.42f)
                targetPelvisY = 0.85f - strike * 0.06f
                targetTorsoPitch = -14f * (1f - p) + 28f * strike
                targetTorsoYaw = 16f * strike
                targetHeadPitch = 12f * strike
                targetCloakPitch = 18f + strike * 14f

                targetRArmPitch = -105f + strike * 82f
                targetRArmYaw = -8f + strike * 16f
                targetRArmRoll = -12f
                targetRForearmPitch = -48f + strike * 34f

                targetLArmPitch = -32f
                targetLArmYaw = -16f
                targetLArmRoll = 18f
                targetLForearmPitch = -48f

                targetSwordPitch = 28f * strike
                targetLThighPitch = -16f * strike
                targetLShinPitch = 20f * strike
                targetRThighPitch = 14f * strike
                targetRShinPitch = 18f * strike
            }

            AnimationState.ULTIMATE_AWAKENING -> {
                // Phase 1: Grounded wide stance, sweeping both arms skyward in a dramatic summoning pose
                val rise = smoothStep(p)
                targetPelvisY = 0.84f + rise * 0.08f
                targetTorsoPitch = -14f * rise
                targetHeadPitch = -22f * rise
                targetCloakPitch = 28f + sin(idleTime * 16f) * 8f
                targetHairPitch = 18f + sin(idleTime * 18f) * 6f

                targetLArmPitch = -35f - rise * 105f
                targetLArmYaw = -28f * rise
                targetLArmRoll = 38f + rise * 14f
                targetLForearmPitch = -32f + rise * 18f

                targetRArmPitch = -35f - rise * 105f
                targetRArmYaw = 28f * rise
                targetRArmRoll = -38f - rise * 14f
                targetRForearmPitch = -32f + rise * 18f

                targetLThighPitch = -14f
                targetLThighRoll = 12f
                targetLShinPitch = 18f
                targetRThighPitch = 14f
                targetRThighRoll = -12f
                targetRShinPitch = 18f
            }

            AnimationState.ULTIMATE_DUAL_SUMMON -> {
                // Phases 2 & 3: Levitating / braced twin-arm high summoning pose supporting both energy spheres
                val hover = sin(idleTime * 7.5f) * 0.04f
                targetPelvisY = 0.94f + hover
                targetTorsoPitch = -16f + sin(idleTime * 6f) * 3f
                targetHeadPitch = -18f
                targetCloakPitch = 36f + sin(idleTime * 20f) * 10f
                targetHairPitch = 24f + cos(idleTime * 19f) * 8f

                targetLArmPitch = -142f + sin(idleTime * 9f) * 4f
                targetLArmYaw = -32f
                targetLArmRoll = 48f
                targetLForearmPitch = -16f

                targetRArmPitch = -142f + cos(idleTime * 9f) * 4f
                targetRArmYaw = 32f
                targetRArmRoll = -48f
                targetRForearmPitch = -16f

                targetLThighPitch = -10f
                targetLThighRoll = 10f
                targetLShinPitch = 22f
                targetRThighPitch = 12f
                targetRThighRoll = -10f
                targetRShinPitch = 20f
            }

            AnimationState.ULTIMATE_CONVERGE_MERGE -> {
                // Phase 4: Drawing both raised hands inward above and in front of the chest to fuse the spheres
                val conv = smoothStep(p)
                targetPelvisY = 0.92f - conv * 0.06f
                targetTorsoPitch = -16f + conv * 12f
                targetHeadPitch = -18f + conv * 14f
                targetCloakPitch = 42f + sin(idleTime * 26f) * 12f
                targetHairPitch = 28f + sin(idleTime * 24f) * 10f

                targetLArmPitch = -142f + conv * 34f
                targetLArmYaw = -32f + conv * 54f
                targetLArmRoll = 48f - conv * 36f
                targetLForearmPitch = -16f - conv * 26f

                targetRArmPitch = -142f + conv * 34f
                targetRArmYaw = 32f - conv * 54f
                targetRArmRoll = -48f + conv * 36f
                targetRForearmPitch = -16f - conv * 26f

                targetLThighPitch = -16f
                targetLThighRoll = 12f
                targetLShinPitch = 24f
                targetRThighPitch = 16f
                targetRThighRoll = -12f
                targetRShinPitch = 22f
            }

            AnimationState.ULTIMATE_RELEASE_BEAM -> {
                // Phase 5: Powerful braced twin-palm forward projection unleashing the Divine Annihilation beam
                val thrust = (p / 0.16f).coerceIn(0f, 1f)
                val vib = sin(idleTime * 42f) * 2.5f
                targetPelvisY = 0.80f
                targetTorsoPitch = 22f * thrust
                targetHeadPitch = -10f * thrust
                targetCloakPitch = 48f + sin(idleTime * 30f) * 12f
                targetHairPitch = 32f + cos(idleTime * 28f) * 10f

                targetLArmPitch = -92f + vib
                targetLArmYaw = 18f
                targetLArmRoll = 10f
                targetLForearmPitch = -8f

                targetRArmPitch = -92f - vib
                targetRArmYaw = -18f
                targetRArmRoll = -10f
                targetRForearmPitch = -8f

                targetLThighPitch = -28f
                targetLThighRoll = 10f
                targetLShinPitch = 34f
                targetRThighPitch = 26f
                targetRThighRoll = -10f
                targetRShinPitch = 24f
            }

            AnimationState.HIT -> {
                targetPelvisY = 0.86f
                targetTorsoPitch = -18f
                targetHeadPitch = -14f
                targetCloakPitch = 12f
                targetLArmPitch = -32f
                targetRArmPitch = -32f
                targetLArmRoll = 32f
                targetRArmRoll = -32f
                targetLThighPitch = 14f
                targetRThighPitch = -14f
            }

            AnimationState.DIE -> {
                targetPelvisY = 0.35f
                targetTorsoPitch = -65f
            }
        }

        // Smoothly interpolate all joint angles
        val isSpinState = state == AnimationState.LIGHT_ATTACK_3 ||
            state == AnimationState.SWORD_SPIN_360 ||
            state == AnimationState.SWORD_DRAGON_TEMPEST ||
            state == AnimationState.FIST_SPIN_ROUNDHOUSE ||
            state == AnimationState.FIST_FINISHER_CYCLONE_KICK ||
            state == AnimationState.FIST_HEAVY_THUNDER_BREAKER

        curPelvisY = MathUtils.lerp(curPelvisY, targetPelvisY, blend)
        curPelvisYaw = if (isSpinState) {
            targetPelvisYaw
        } else {
            MathUtils.lerp(curPelvisYaw, targetPelvisYaw, blend)
        }
        curPelvisPitch = MathUtils.lerp(curPelvisPitch, targetPelvisPitch, blend)
        curPelvisRoll = MathUtils.lerp(curPelvisRoll, targetPelvisRoll, blend)
        curTorsoPitch = MathUtils.lerp(curTorsoPitch, targetTorsoPitch, blend)
        curTorsoYaw = MathUtils.lerp(curTorsoYaw, targetTorsoYaw, blend)
        curHeadPitch = MathUtils.lerp(curHeadPitch, targetHeadPitch, blend)
        curCloakPitch = MathUtils.lerp(curCloakPitch, targetCloakPitch, blend)
        curCloakRoll = MathUtils.lerp(curCloakRoll, targetCloakRoll, blend)
        curHairPitch = MathUtils.lerp(curHairPitch, targetHairPitch, blend)

        curLeftArmPitch = MathUtils.lerp(curLeftArmPitch, targetLArmPitch, blend)
        curLeftArmYaw = MathUtils.lerp(curLeftArmYaw, targetLArmYaw, blend)
        curLeftArmRoll = MathUtils.lerp(curLeftArmRoll, targetLArmRoll, blend)
        curLeftForearmPitch = MathUtils.lerp(curLeftForearmPitch, targetLForearmPitch, blend)

        curRightArmPitch = MathUtils.lerp(curRightArmPitch, targetRArmPitch, blend)
        curRightArmYaw = MathUtils.lerp(curRightArmYaw, targetRArmYaw, blend)
        curRightArmRoll = MathUtils.lerp(curRightArmRoll, targetRArmRoll, blend)
        curRightForearmPitch = MathUtils.lerp(curRightForearmPitch, targetRForearmPitch, blend)

        curSwordPitch = MathUtils.lerp(curSwordPitch, targetSwordPitch, blend)
        curSwordYaw = MathUtils.lerp(curSwordYaw, targetSwordYaw, blend)
        curSwordRoll = MathUtils.lerp(curSwordRoll, targetSwordRoll, blend)

        curLeftThighPitch = MathUtils.lerp(curLeftThighPitch, targetLThighPitch, blend)
        curLeftThighRoll = MathUtils.lerp(curLeftThighRoll, targetLThighRoll, blend)
        curLeftShinPitch = MathUtils.lerp(curLeftShinPitch, targetLShinPitch, blend)
        curRightThighPitch = MathUtils.lerp(curRightThighPitch, targetRThighPitch, blend)
        curRightThighRoll = MathUtils.lerp(curRightThighRoll, targetRThighRoll, blend)
        curRightShinPitch = MathUtils.lerp(curRightShinPitch, targetRShinPitch, blend)

        // Apply transforms to hierarchical nodes
        pelvisNode?.let {
            it.translation.set(0f, curPelvisY, 0f)
            it.rotation.setEulerAngles(curPelvisYaw, curPelvisPitch, curPelvisRoll)
            // Brief vanish silhouette compression during mid-Flash Step
            val vanishScale = if (state == AnimationState.SWORD_FLASH_STEP && p in 0.22f..0.72f) {
                0.15f
            } else {
                1.0f
            }
            it.scale.set(vanishScale, vanishScale, vanishScale)
        }

        torsoNode?.rotation?.setEulerAngles(curTorsoYaw, curTorsoPitch, 0f)

        headNode?.let {
            it.rotation.setEulerAngles(0f, curHeadPitch, 0f)
            val s = if (isFirstPerson) 0.0001f else 1f
            it.scale.set(s, s, s)
        }

        hairBackNode?.rotation?.setEulerAngles(0f, curHairPitch, 0f)
        cloakBackNode?.rotation?.setEulerAngles(0f, curCloakPitch, curCloakRoll)
        swordNode?.rotation?.setEulerAngles(0f, curCloakPitch * 0.25f, 0f)

        leftUpperArm?.rotation?.setEulerAngles(curLeftArmYaw, curLeftArmPitch, curLeftArmRoll)
        leftForearm?.rotation?.setEulerAngles(0f, curLeftForearmPitch, 0f)

        rightUpperArm?.rotation?.setEulerAngles(curRightArmYaw, curRightArmPitch, curRightArmRoll)
        rightForearm?.rotation?.setEulerAngles(0f, curRightForearmPitch, 0f)

        drawnSwordNode?.let {
            val s = if (showSwordInHand) 1f else 0.0001f
            it.scale.set(s, s, s)
            it.rotation.setEulerAngles(curSwordYaw, curSwordPitch, curSwordRoll)
        }

        val toolNodes = listOf(
            "stone_axe" to heldStoneAxeNode,
            "iron_axe" to heldIronAxeNode,
            "stone_pickaxe" to heldStonePickaxeNode,
            "iron_pickaxe" to heldIronPickaxeNode,
            "shovel" to heldShovelNode,
            "sickle" to heldSickleNode,
            "knife" to heldKnifeNode,
            "builders_hammer" to heldBuildersHammerNode
        )
        for ((toolId, node) in toolNodes) {
            node?.let {
                val s = if (visibleToolId == toolId) 1f else 0.0001f
                it.scale.set(s, s, s)
                it.rotation.setEulerAngles(curSwordYaw, curSwordPitch, curSwordRoll)
            }
        }
        for ((itemId, node) in heldResourceNodeMap) {
            node?.let {
                val s = if (visibleToolId == itemId) 1f else 0.0001f
                it.scale.set(s, s, s)
                it.rotation.setEulerAngles(curSwordYaw, curSwordPitch, curSwordRoll)
            }
        }

        leftThigh?.rotation?.setEulerAngles(0f, curLeftThighPitch, curLeftThighRoll)
        leftShin?.rotation?.setEulerAngles(0f, curLeftShinPitch, 0f)

        rightThigh?.rotation?.setEulerAngles(0f, curRightThighPitch, curRightThighRoll)
        rightShin?.rotation?.setEulerAngles(0f, curRightShinPitch, 0f)

        modelInstance.calculateTransforms()
    }

    private fun punchCurve(p: Float, peakAt: Float): Float {
        val clamped = p.coerceIn(0f, 1f)
        return if (clamped <= peakAt) {
            smoothStep(clamped / peakAt.coerceAtLeast(0.01f))
        } else {
            1f - smoothStep((clamped - peakAt) / (1f - peakAt).coerceAtLeast(0.01f))
        }
    }

    private fun smoothStep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
