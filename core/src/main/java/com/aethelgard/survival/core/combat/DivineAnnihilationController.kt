package com.aethelgard.survival.core.combat

import com.aethelgard.survival.core.assets.GameAssetManager
import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.camera.CameraMode
import com.aethelgard.survival.core.camera.FollowCameraController
import com.aethelgard.survival.core.entities.EnemyAiState
import com.aethelgard.survival.core.entities.EntityManager
import com.aethelgard.survival.core.player.AnimationState
import com.aethelgard.survival.core.player.PlayerCharacter
import com.aethelgard.survival.core.survival.HarvestableResourceManager
import com.aethelgard.survival.core.survival.Inventory
import com.aethelgard.survival.core.world.WorldManager
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.ConeShapeBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distinct execution phases for the DIVINE ANNIHILATION ultimate ability.
 */
enum class UltimatePhase(val label: String) {
    INACTIVE(""),
    PHASE_1_AWAKENING("AWAKENING"),
    PHASE_2_DUAL_SUMMON("DUAL ENERGY SUMMONING"),
    PHASE_3_INCANTATION("INCANTATION"),
    PHASE_4_MERGING("CONVERGENCE"),
    PHASE_5_ANNIHILATION("DIVINE ANNIHILATION")
}

/**
 * Visual typography and aura theme for each on-screen incantation sentence.
 */
enum class IncantationVisualStyle {
    LIGHT_BLUE,
    DARK_PURPLE,
    DUAL_CONVERGENCE,
    FINAL_CATACLYSM
}

/**
 * Represents a single synchronized on-screen incantation line.
 */
data class IncantationStep(
    val index: Int,
    val sentence: String,
    val style: IncantationVisualStyle
)

/**
 * Controls the complete lifecycle, 5-phase cinematic state machine, procedural 3D energy spheres,
 * lightning & void vortex rings, cataclysmic merged beam, synchronized incantation text,
 * single-hit-per-enemy Phase 5 damage, and selective environmental world destruction for
 * the DIVINE ANNIHILATION ultimate ability.
 */
class DivineAnnihilationController(
    private val assetManager: GameAssetManager,
    private val worldManager: WorldManager,
    private val entityManager: EntityManager,
    private val audioManager: AudioManager
) {
    companion object {
        const val ABILITY_NAME: String = "DIVINE ANNIHILATION"

        /** Cooldown is strictly 15.0 seconds AFTER the ability finishes executing. */
        const val COOLDOWN_DURATION: Float = 15.0f

        /** Phase boundary timestamps (in seconds from activation). */
        const val PHASE_1_END: Float = 0.65f
        const val PHASE_2_END: Float = 1.45f
        const val PHASE_3_END: Float = 3.45f
        const val PHASE_4_END: Float = 4.15f
        const val TOTAL_DURATION: Float = 5.25f

        /** Beam & impact geometry parameters. */
        const val BEAM_LENGTH: Float = 32.0f
        const val BEAM_CORRIDOR_HALF_WIDTH: Float = 2.85f
        const val IMPACT_RADIUS: Float = 8.5f
        const val ULTIMATE_DAMAGE: Float = 185.0f

        /** Exact 5 required English incantation sentences in strict sequential order. */
        val INCANTATION_STEPS: List<IncantationStep> = listOf(
            IncantationStep(0, "Heavens above, Abyss below…", IncantationVisualStyle.LIGHT_BLUE),
            IncantationStep(1, "Hear the voice that commands you!", IncantationVisualStyle.DARK_PURPLE),
            IncantationStep(2, "Let Light and Darkness become one!", IncantationVisualStyle.DUAL_CONVERGENCE),
            IncantationStep(3, "OBLITERATE ALL BEFORE ME!", IncantationVisualStyle.DARK_PURPLE),
            IncantationStep(4, "DIVINE… ANNIHILATION!", IncantationVisualStyle.FINAL_CATACLYSM)
        )
    }

    var currentPhase: UltimatePhase = UltimatePhase.INACTIVE
        private set

    var elapsedTime: Float = 0f
        private set

    /** Remaining cooldown in seconds (counts down from 15.0f only after execution completes). */
    var cooldownRemaining: Float = 0f
        private set

    val isActive: Boolean
        get() = currentPhase != UltimatePhase.INACTIVE

    val isCoolingDown: Boolean
        get() = !isActive && cooldownRemaining > 0f

    /** Currently displayed single incantation step (null when outside incantation display window). */
    var activeIncantationStep: IncantationStep? = null
        private set

    /** Progress (0..1) within the currently active single incantation sentence for fade/scale effects. */
    var activeIncantationProgress: Float = 0f
        private set

    /** Records the exact sequence of incantation sentences displayed during the most recent execution. */
    val displayedIncantationLog = mutableListOf<String>()

    /** Screen flash alpha (0..1) triggered on Phase 4 convergence and Phase 5 ignition. */
    var screenFlashAlpha: Float = 0f
        private set

    /** Tracks enemies already damaged during the current ability cast so each enemy is hit only once. */
    private val hitEnemyIds = HashSet<String>()

    /** Ensures Phase 5 world destruction and beam ignition trigger cleanly once per cast. */
    private var hasTriggeredMergeExplosion: Boolean = false
    private var hasTriggeredPhase5Impact: Boolean = false

    /** Cached spatial vectors for zero-allocation update/render loops. */
    val casterOrigin = Vector3()
    val aimDirection = Vector3(0f, 0f, -1f)
    val rightHandWorldPos = Vector3()
    val leftHandWorldPos = Vector3()
    val whiteSpherePos = Vector3()
    val blackSpherePos = Vector3()
    val mergeCenterPos = Vector3()
    val beamImpactPos = Vector3()
    private val tempRight = Vector3()
    private val tempUp = Vector3(0f, 1f, 0f)
    private val tempVec = Vector3()

    /** Optional hook to HarvestableResourceManager and Inventory for Phase 5 world destruction. */
    var harvestableResourceManager: HarvestableResourceManager? = null
    var playerInventory: Inventory? = null
    var onResourceDestroyedNotification: ((String, Int) -> Unit)? = null

    // Pre-allocated 3D procedural models & instances (zero allocation during render)
    private var modelsInitialized: Boolean = false
    private lateinit var whiteCoreInstance: ModelInstance
    private lateinit var blueLightningShellInstance: ModelInstance
    private val blueLightningArcInstances = mutableListOf<ModelInstance>()

    private lateinit var blackCoreInstance: ModelInstance
    private lateinit var darkPurpleShellInstance: ModelInstance
    private val darkPurpleRingInstances = mutableListOf<ModelInstance>()

    private lateinit var mergedSphereCoreInstance: ModelInstance
    private lateinit var mergedSphereOuterInstance: ModelInstance
    private val distortionRingInstances = mutableListOf<ModelInstance>()

    private lateinit var beamInnerCoreInstance: ModelInstance
    private lateinit var beamOuterPlasmaInstance: ModelInstance
    private val beamHelixInstances = mutableListOf<ModelInstance>()
    private lateinit var impactDomeInstance: ModelInstance
    private val groundShockwaveInstances = mutableListOf<ModelInstance>()

    fun initialize() {
        if (modelsInitialized) return
        val attrs = (Usage.Position or Usage.Normal).toLong()
        val m = Matrix4()

        // 1. White Energy Core + Blue Lightning Sphere Models
        val whiteCoreMat = Material(
            ColorAttribute.createDiffuse(Color(0.98f, 0.99f, 1.0f, 0.96f)),
            ColorAttribute.createEmissive(0.92f, 0.97f, 1.0f, 1f),
            ColorAttribute.createSpecular(1f, 1f, 1f, 1f),
            FloatAttribute.createShininess(64f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.96f)
        )
        val blueLightningMat = Material(
            ColorAttribute.createDiffuse(Color(0.18f, 0.68f, 1.0f, 0.72f)),
            ColorAttribute.createEmissive(0.22f, 0.76f, 1.0f, 1f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.74f)
        )
        val electricBoltMat = Material(
            ColorAttribute.createDiffuse(Color(0.55f, 0.90f, 1.0f, 0.92f)),
            ColorAttribute.createEmissive(0.45f, 0.88f, 1.0f, 1f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.92f)
        )

        whiteCoreInstance = ModelInstance(buildSphereModel(0.52f, 14, 14, whiteCoreMat, attrs))
        blueLightningShellInstance = ModelInstance(buildSphereModel(0.76f, 14, 14, blueLightningMat, attrs))

        val lightningArcModel = run {
            val mb = ModelBuilder()
            mb.begin()
            val part = mb.part("blue_lightning_arc", GL20.GL_TRIANGLES, attrs, electricBoltMat)
            // Zig-zagging electric lightning ring segments
            for (i in 0 until 8) {
                val angle = i * 45f
                val tilt = if (i % 2 == 0) 32f else -32f
                m.idt()
                    .rotate(0f, 1f, 0f, angle)
                    .translate(0f, 0f, 0.44f)
                    .rotate(0f, 0f, 1f, tilt)
                part.setVertexTransform(m)
                BoxShapeBuilder.build(part, 0.24f, 0.038f, 0.038f)
                part.setVertexTransform(null)
            }
            assetManager.registerModel(mb.end())
        }
        blueLightningArcInstances.clear()
        repeat(3) {
            blueLightningArcInstances.add(ModelInstance(lightningArcModel))
        }

        // 2. Black Energy Core + Dark Purple Void Sphere Models
        val blackCoreMat = Material(
            ColorAttribute.createDiffuse(Color(0.03f, 0.01f, 0.06f, 0.98f)),
            ColorAttribute.createEmissive(0.08f, 0.02f, 0.16f, 1f),
            ColorAttribute.createSpecular(0.55f, 0.20f, 0.85f, 1f),
            FloatAttribute.createShininess(48f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.98f)
        )
        val darkPurpleShellMat = Material(
            ColorAttribute.createDiffuse(Color(0.46f, 0.10f, 0.82f, 0.72f)),
            ColorAttribute.createEmissive(0.52f, 0.12f, 0.92f, 1f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.72f)
        )
        val darkPurpleStreamMat = Material(
            ColorAttribute.createDiffuse(Color(0.68f, 0.20f, 0.98f, 0.88f)),
            ColorAttribute.createEmissive(0.62f, 0.16f, 0.96f, 1f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.88f)
        )

        blackCoreInstance = ModelInstance(buildSphereModel(0.52f, 14, 14, blackCoreMat, attrs))
        darkPurpleShellInstance = ModelInstance(buildSphereModel(0.78f, 14, 14, darkPurpleShellMat, attrs))

        val darkRingModel = buildTorusRingModel(0.48f, 0.042f, 10, darkPurpleStreamMat, attrs)
        darkPurpleRingInstances.clear()
        repeat(3) {
            darkPurpleRingInstances.add(ModelInstance(darkRingModel))
        }

        // 3. Phase 4 Merged Cataclysmic Sphere & Distortion Rings
        val mergedCoreMat = Material(
            ColorAttribute.createDiffuse(Color(0.95f, 0.96f, 1.0f, 0.98f)),
            ColorAttribute.createEmissive(0.85f, 0.92f, 1.0f, 1f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.98f)
        )
        val mergedOuterMat = Material(
            ColorAttribute.createDiffuse(Color(0.48f, 0.26f, 0.98f, 0.78f)),
            ColorAttribute.createEmissive(0.45f, 0.55f, 1.0f, 1f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.78f)
        )
        mergedSphereCoreInstance = ModelInstance(buildSphereModel(1.15f, 16, 16, mergedCoreMat, attrs))
        mergedSphereOuterInstance = ModelInstance(buildSphereModel(1.68f, 16, 16, mergedOuterMat, attrs))

        val distortionRingModel = buildTorusRingModel(1.25f, 0.065f, 14, electricBoltMat, attrs)
        distortionRingInstances.clear()
        repeat(4) {
            distortionRingInstances.add(ModelInstance(distortionRingModel))
        }

        // 4. Phase 5 Enormous Annihilation Beam, Helical Coils, Impact Dome & Ground Shockwaves
        // Oriented along local -Z from 0 to -1m so scaling Z by BEAM_LENGTH extends cleanly along the aim vector
        beamInnerCoreInstance = ModelInstance(buildBeamCylinderModel(1.35f, mergedCoreMat, attrs))
        beamOuterPlasmaInstance = ModelInstance(buildBeamCylinderModel(2.65f, mergedOuterMat, attrs))

        val blueHelixModel = buildBeamHelixModel(1.65f, electricBoltMat, attrs, phaseSign = 1f)
        val purpleHelixModel = buildBeamHelixModel(1.85f, darkPurpleStreamMat, attrs, phaseSign = -1f)
        beamHelixInstances.clear()
        beamHelixInstances.add(ModelInstance(blueHelixModel))
        beamHelixInstances.add(ModelInstance(purpleHelixModel))

        impactDomeInstance = ModelInstance(buildSphereModel(1.0f, 16, 14, mergedOuterMat, attrs))

        val shockwaveRingModel = buildTorusRingModel(1.0f, 0.08f, 16, electricBoltMat, attrs)
        groundShockwaveInstances.clear()
        repeat(3) {
            groundShockwaveInstances.add(ModelInstance(shockwaveRingModel))
        }

        modelsInitialized = true
    }

    private fun buildSphereModel(radius: Float, divU: Int, divV: Int, material: Material, attrs: Long): Model {
        val mb = ModelBuilder()
        mb.begin()
        val part = mb.part("ult_sphere", GL20.GL_TRIANGLES, attrs, material)
        val d = radius * 2f
        SphereShapeBuilder.build(part, d, d, d, divU, divV)
        return assetManager.registerModel(mb.end())
    }

    private fun buildTorusRingModel(radius: Float, thickness: Float, segments: Int, material: Material, attrs: Long): Model {
        val mb = ModelBuilder()
        mb.begin()
        val part = mb.part("ult_ring", GL20.GL_TRIANGLES, attrs, material)
        val m = Matrix4()
        val stepDeg = 360f / segments
        val segLength = (2f * Math.PI.toFloat() * radius / segments) * 1.12f
        for (i in 0 until segments) {
            val angle = i * stepDeg
            m.idt()
                .rotate(0f, 1f, 0f, angle)
                .translate(0f, 0f, radius)
            part.setVertexTransform(m)
            BoxShapeBuilder.build(part, segLength, thickness, thickness)
            part.setVertexTransform(null)
        }
        return assetManager.registerModel(mb.end())
    }

    private fun buildBeamCylinderModel(diameter: Float, material: Material, attrs: Long): Model {
        val mb = ModelBuilder()
        mb.begin()
        val part = mb.part("ult_beam_cyl", GL20.GL_TRIANGLES, attrs, material)
        val m = Matrix4().idt().translate(0f, 0f, -0.5f).rotate(1f, 0f, 0f, 90f)
        part.setVertexTransform(m)
        CylinderShapeBuilder.build(part, diameter, 1.0f, diameter, 14)
        part.setVertexTransform(null)
        return assetManager.registerModel(mb.end())
    }

    private fun buildBeamHelixModel(radius: Float, material: Material, attrs: Long, phaseSign: Float): Model {
        val mb = ModelBuilder()
        mb.begin()
        val part = mb.part("ult_beam_helix", GL20.GL_TRIANGLES, attrs, material)
        val m = Matrix4()
        val segments = 18
        for (i in 0 until segments) {
            val t = i.toFloat() / segments.toFloat()
            val z = -t
            val angleRad = (t * 4f * Math.PI * phaseSign).toFloat()
            val x = cos(angleRad) * radius * 0.5f
            val y = sin(angleRad) * radius * 0.5f
            m.idt()
                .translate(x, y, z)
                .rotate(0f, 0f, 1f, angleRad * MathUtils.radiansToDegrees)
            part.setVertexTransform(m)
            BoxShapeBuilder.build(part, 0.22f, 0.22f, 1.15f / segments)
            part.setVertexTransform(null)
        }
        return assetManager.registerModel(mb.end())
    }

    /**
     * Returns true if DIVINE ANNIHILATION can be activated right now.
     */
    fun canActivate(): Boolean {
        return !isActive && cooldownRemaining <= 0f
    }

    /**
     * Attempts to activate DIVINE ANNIHILATION.
     * Prevents activation while already executing or while the 15s post-completion cooldown is active.
     */
    fun tryActivate(
        player: PlayerCharacter,
        cameraController: FollowCameraController,
        combatSystem: CombatSystem
    ): Boolean {
        if (!canActivate()) return false

        currentPhase = UltimatePhase.PHASE_1_AWAKENING
        elapsedTime = 0f
        activeIncantationStep = null
        activeIncantationProgress = 0f
        displayedIncantationLog.clear()
        screenFlashAlpha = 0.22f
        hitEnemyIds.clear()
        hasTriggeredMergeExplosion = false
        hasTriggeredPhase5Impact = false

        // Stop normal player movement & ongoing tool/dodge states immediately
        player.velocity.x = 0f
        player.velocity.z = 0f
        player.isSprinting = false
        player.isDodging = false
        player.dodgeTimer = 0f
        player.activeToolTimer = 0f
        player.activeToolAnimationState = null
        player.invulnerabilityTimer = TOTAL_DURATION + 0.35f

        // Compute initial forward aim direction from lock-on target or camera
        updateAimVectors(player, cameraController)

        // Enter Phase 1 Awakening animation
        player.animationState = AnimationState.ULTIMATE_AWAKENING
        player.activeAttackProgress = 0f

        // Engage smooth cinematic camera framing
        cameraController.setUltimateCinematicState(active = true, phase = currentPhase, phaseProgress = 0f)

        // Trigger initial awakening aura particles & sound
        combatSystem.spawnMagicCastBurst(
            origin = Vector3(player.position.x, player.position.y + 1.25f, player.position.z),
            isHeavy = true
        )
        combatSystem.triggerScreenShake(0.18f, 0.28f)
        audioManager.playUltimateAwaken()
        return true
    }

    /**
     * Advances the ultimate state machine or post-ability cooldown timer.
     */
    fun update(
        delta: Float,
        player: PlayerCharacter,
        cameraController: FollowCameraController,
        combatSystem: CombatSystem
    ) {
        if (screenFlashAlpha > 0f) {
            screenFlashAlpha = (screenFlashAlpha - delta * 1.85f).coerceAtLeast(0f)
        }

        if (!isActive) {
            if (cooldownRemaining > 0f) {
                cooldownRemaining = (cooldownRemaining - delta).coerceAtLeast(0f)
            }
            cameraController.setUltimateCinematicState(active = false, phase = UltimatePhase.INACTIVE, phaseProgress = 0f)
            return
        }

        elapsedTime += delta

        // Keep horizontal locomotion halted while executing ultimate
        player.velocity.x = 0f
        player.velocity.z = 0f
        player.isSprinting = false
        player.invulnerabilityTimer = (TOTAL_DURATION - elapsedTime + 0.35f).coerceAtLeast(0.25f)

        updateAimVectors(player, cameraController)
        when {
            elapsedTime < PHASE_1_END -> {
                currentPhase = UltimatePhase.PHASE_1_AWAKENING
                val p = (elapsedTime / PHASE_1_END).coerceIn(0f, 1f)
                player.animationState = AnimationState.ULTIMATE_AWAKENING
                player.activeAttackProgress = p
                activeIncantationStep = null
                activeIncantationProgress = 0f
                updateHandAndSpherePositions(player, cameraController)
                cameraController.setUltimateCinematicState(true, currentPhase, p)
            }

            elapsedTime < PHASE_2_END -> {
                currentPhase = UltimatePhase.PHASE_2_DUAL_SUMMON
                val p = ((elapsedTime - PHASE_1_END) / (PHASE_2_END - PHASE_1_END)).coerceIn(0f, 1f)
                player.animationState = AnimationState.ULTIMATE_DUAL_SUMMON
                player.activeAttackProgress = p
                activeIncantationStep = null
                activeIncantationProgress = 0f
                updateHandAndSpherePositions(player, cameraController)
                cameraController.setUltimateCinematicState(true, currentPhase, p)
            }

            elapsedTime < PHASE_3_END -> {
                currentPhase = UltimatePhase.PHASE_3_INCANTATION
                val phaseSpan = PHASE_3_END - PHASE_2_END
                val phaseElapsed = (elapsedTime - PHASE_2_END).coerceIn(0f, phaseSpan)
                val p = (phaseElapsed / phaseSpan).coerceIn(0f, 1f)
                player.animationState = AnimationState.ULTIMATE_DUAL_SUMMON
                player.activeAttackProgress = p

                // Determine which single incantation sentence (0..4) is active right now
                val stepDuration = phaseSpan / INCANTATION_STEPS.size.toFloat()
                val stepIndex = (phaseElapsed / stepDuration).toInt().coerceIn(0, INCANTATION_STEPS.lastIndex)
                val stepProgress = ((phaseElapsed - stepIndex * stepDuration) / stepDuration).coerceIn(0f, 1f)
                setActiveIncantationIndex(stepIndex, stepProgress)
                updateHandAndSpherePositions(player, cameraController)

                cameraController.setUltimateCinematicState(true, currentPhase, p)
            }

            elapsedTime < PHASE_4_END -> {
                // Ensure all 5 incantation steps were logged even if large delta jumped across end of Phase 3
                ensureAllIncantationStepsLogged()

                currentPhase = UltimatePhase.PHASE_4_MERGING
                val p = ((elapsedTime - PHASE_3_END) / (PHASE_4_END - PHASE_3_END)).coerceIn(0f, 1f)
                player.animationState = AnimationState.ULTIMATE_CONVERGE_MERGE
                player.activeAttackProgress = p
                updateHandAndSpherePositions(player, cameraController)

                // Keep final sentence ("DIVINE… ANNIHILATION!") blazing during convergence
                activeIncantationStep = INCANTATION_STEPS.last()
                activeIncantationProgress = p

                if (p >= 0.55f && !hasTriggeredMergeExplosion) {
                    hasTriggeredMergeExplosion = true
                    screenFlashAlpha = 0.88f
                    combatSystem.triggerScreenShake(0.55f, 0.45f)
                    combatSystem.spawnMagicImpactBurst(mergeCenterPos, isChargedBlast = true)
                    audioManager.playUltimateMergeFlash()
                }
                cameraController.setUltimateCinematicState(true, currentPhase, p)
            }

            elapsedTime < TOTAL_DURATION -> {
                ensureAllIncantationStepsLogged()

                currentPhase = UltimatePhase.PHASE_5_ANNIHILATION
                val p = ((elapsedTime - PHASE_4_END) / (TOTAL_DURATION - PHASE_4_END)).coerceIn(0f, 1f)
                player.animationState = AnimationState.ULTIMATE_RELEASE_BEAM
                player.activeAttackProgress = p
                updateHandAndSpherePositions(player, cameraController)

                // Hold "DIVINE… ANNIHILATION!" during the initial beam release, then fade out
                if (p < 0.65f) {
                    activeIncantationStep = INCANTATION_STEPS.last()
                    activeIncantationProgress = (p / 0.65f).coerceIn(0f, 1f)
                } else {
                    activeIncantationStep = null
                    activeIncantationProgress = 0f
                }

                if (!hasTriggeredPhase5Impact) {
                    hasTriggeredPhase5Impact = true
                    screenFlashAlpha = 1.0f
                    combatSystem.triggerScreenShake(0.95f, 0.68f)
                    audioManager.playUltimateAnnihilationBeam()
                    executePhase5WorldDestruction(combatSystem)
                }

                // Apply damage ONLY during Phase 5, and at most once per enemy per cast
                applyPhase5BeamDamage(player, combatSystem)

                cameraController.setUltimateCinematicState(true, currentPhase, p)
            }

            else -> {
                ensureAllIncantationStepsLogged()
                finishAbilityExecution(player, cameraController)
            }
        }
    }

    private fun setActiveIncantationIndex(index: Int, progress: Float) {
        // Ensure any skipped earlier indices (e.g. from larger frame delta) are recorded in strict sequence
        for (i in 0..index) {
            val sentence = INCANTATION_STEPS[i].sentence
            if (!displayedIncantationLog.contains(sentence)) {
                displayedIncantationLog.add(sentence)
            }
        }
        activeIncantationStep = INCANTATION_STEPS[index]
        activeIncantationProgress = progress
    }

    private fun ensureAllIncantationStepsLogged() {
        for (step in INCANTATION_STEPS) {
            if (!displayedIncantationLog.contains(step.sentence)) {
                displayedIncantationLog.add(step.sentence)
            }
        }
    }

    private fun updateAimVectors(player: PlayerCharacter, cameraController: FollowCameraController) {
        casterOrigin.set(player.position.x, player.position.y + 1.38f, player.position.z)

        val locked = if (cameraController.isLockedOn) cameraController.lockedTarget else null
        if (locked != null && locked.entity.isActive && locked.aiState != EnemyAiState.DEAD) {
            val dx = locked.entity.position.x - player.position.x
            val dz = locked.entity.position.z - player.position.z
            if (dx * dx + dz * dz > 0.001f) {
                player.facingYaw = MathUtils.atan2(dx, dz) * MathUtils.radiansToDegrees
            }
            val dy = (locked.entity.position.y + 1.0f) - (player.position.y + 1.45f)
            aimDirection.set(dx, dy.coerceIn(-4.5f, 2.5f), dz).nor()
        } else {
            // Align player facing to camera yaw
            player.facingYaw = cameraController.yawDegrees + 180f
            val yawRad = player.facingYaw * MathUtils.degreesToRadians
            // Tilt slightly downward toward the ground clearing ahead so beam impacts terrain and enemies cleanly
            aimDirection.set(sin(yawRad), -0.09f, cos(yawRad)).nor()
        }

        tempRight.set(aimDirection).crs(tempUp)
        if (tempRight.len2() < 0.0001f) {
            tempRight.set(1f, 0f, 0f)
        } else {
            tempRight.nor()
        }

        // Compute terminal beam impact position on walkable ground along the beam path
        val endX = casterOrigin.x + aimDirection.x * BEAM_LENGTH
        val endZ = casterOrigin.z + aimDirection.z * BEAM_LENGTH
        val groundY = worldManager.getWalkableSurfaceHeight(endX, endZ)
        beamImpactPos.set(endX, groundY, endZ)
    }

    private fun updateHandAndSpherePositions(player: PlayerCharacter, cameraController: FollowCameraController) {
        // Sync player model transform and query articulated hand positions
        player.syncModelTransform()
        player.animationController.getHandWorldPositions(leftHandWorldPos, rightHandWorldPos)

        // Elevated convergence apex above and slightly in front of the player
        mergeCenterPos.set(
            player.position.x + aimDirection.x * 1.45f,
            player.position.y + 2.85f,
            player.position.z + aimDirection.z * 1.45f
        )

        // In First-Person mode, position the left/right spheres and merge point clearly within the camera's forward field of view
        if (cameraController.cameraMode == CameraMode.FIRST_PERSON) {
            val camPos = cameraController.camera.position
            val camDir = cameraController.camera.direction
            tempRight.set(camDir).crs(tempUp).nor()

            rightHandWorldPos.set(camPos)
                .mulAdd(camDir, 2.35f)
                .mulAdd(tempRight, 0.92f)
                .add(0f, 0.32f, 0f)

            leftHandWorldPos.set(camPos)
                .mulAdd(camDir, 2.35f)
                .mulAdd(tempRight, -0.92f)
                .add(0f, 0.32f, 0f)

            mergeCenterPos.set(camPos)
                .mulAdd(camDir, 2.65f)
                .add(0f, 0.25f, 0f)
        } else {
            // Float each sphere clearly above and outward from the raised left/right hands
            rightHandWorldPos.add(tempRight.x * 0.62f, 0.72f, tempRight.z * 0.62f)
            leftHandWorldPos.add(-tempRight.x * 0.62f, 0.72f, -tempRight.z * 0.62f)
        }

        when (currentPhase) {
            UltimatePhase.PHASE_1_AWAKENING,
            UltimatePhase.PHASE_2_DUAL_SUMMON,
            UltimatePhase.PHASE_3_INCANTATION -> {
                whiteSpherePos.set(rightHandWorldPos)
                blackSpherePos.set(leftHandWorldPos)
            }
            UltimatePhase.PHASE_4_MERGING -> {
                val p = ((elapsedTime - PHASE_3_END) / (PHASE_4_END - PHASE_3_END)).coerceIn(0f, 1f)
                val convergeFactor = (p / 0.58f).coerceIn(0f, 1f)
                // Smooth cubic ease-in-out convergence toward mergeCenterPos
                val smooth = convergeFactor * convergeFactor * (3f - 2f * convergeFactor)
                whiteSpherePos.set(rightHandWorldPos).lerp(mergeCenterPos, smooth)
                blackSpherePos.set(leftHandWorldPos).lerp(mergeCenterPos, smooth)

                // Unstable high-frequency energy jitter while converging, tapering to 0 at fusion
                val jitterAmp = (1f - smooth) * sin(convergeFactor * MathUtils.PI) * 0.08f
                val jitterX = sin(elapsedTime * 62f) * jitterAmp
                val jitterY = cos(elapsedTime * 54f) * jitterAmp
                whiteSpherePos.add(jitterX, jitterY, -jitterX)
                blackSpherePos.add(-jitterX, -jitterY, jitterX)
            }
            UltimatePhase.PHASE_5_ANNIHILATION,
            UltimatePhase.INACTIVE -> {
                whiteSpherePos.set(mergeCenterPos)
                blackSpherePos.set(mergeCenterPos)
            }
        }
    }

    /**
     * Applies massive ultimate damage to all living enemies inside the beam corridor or terminal impact radius.
     * Guaranteed to execute ONLY during PHASE_5_ANNIHILATION and hit each enemy at most ONCE per cast.
     */
    private fun applyPhase5BeamDamage(player: PlayerCharacter, combatSystem: CombatSystem) {
        if (currentPhase != UltimatePhase.PHASE_5_ANNIHILATION) return

        val startX = mergeCenterPos.x
        val startZ = mergeCenterPos.z
        val dirX = aimDirection.x
        val dirZ = aimDirection.z
        val dirLenXZ = sqrt(dirX * dirX + dirZ * dirZ).coerceAtLeast(0.0001f)
        val normDirX = dirX / dirLenXZ
        val normDirZ = dirZ / dirLenXZ

        for (enemy in entityManager.enemyControllers) {
            if (!enemy.entity.isActive || enemy.aiState == EnemyAiState.DEAD || enemy.health <= 0f) continue
            if (hitEnemyIds.contains(enemy.entity.id)) continue

            val ex = enemy.entity.position.x
            val ez = enemy.entity.position.z

            // 1. Check distance to terminal impact zone
            val impDx = ex - beamImpactPos.x
            val impDz = ez - beamImpactPos.z
            val inImpactZone = (impDx * impDx + impDz * impDz) <= (IMPACT_RADIUS * IMPACT_RADIUS)

            // 2. Check distance to directional beam corridor from caster to BEAM_LENGTH
            val relX = ex - startX
            val relZ = ez - startZ
            val proj = relX * normDirX + relZ * normDirZ
            val perpX = relX - normDirX * proj
            val perpZ = relZ - normDirZ * proj
            val perpDistSq = perpX * perpX + perpZ * perpZ
            val effectiveCorridorRadius = BEAM_CORRIDOR_HALF_WIDTH + enemy.collisionRadius
            val inBeamCorridor = proj in -1.5f..(BEAM_LENGTH + 2.0f) &&
                perpDistSq <= (effectiveCorridorRadius * effectiveCorridorRadius)

            if (inImpactZone || inBeamCorridor) {
                hitEnemyIds.add(enemy.entity.id)
                val hpBefore = enemy.health
                val defeated = enemy.receiveDamage(
                    amount = ULTIMATE_DAMAGE,
                    knockbackX = normDirX * 11.5f,
                    knockbackZ = normDirZ * 11.5f,
                    isHeavyImpact = true,
                    audioManager = audioManager
                )
                if (enemy.health < hpBefore || defeated) {
                    val hitPos = Vector3(ex, enemy.entity.position.y + 1.25f, ez)
                    combatSystem.spawnMagicImpactBurst(hitPos, isChargedBlast = true)
                    combatSystem.recordDamagePopup(hitPos, ULTIMATE_DAMAGE, isHeavy = true)
                }
            }
        }
    }

    /**
     * Destroys destructible world objects (trees, vegetation, boulders, crystals) within the beam corridor
     * and impact zone while protecting indestructible terrain, key stations, and player-built structures.
     */
    private fun executePhase5WorldDestruction(combatSystem: CombatSystem) {
        val resourceMgr = harvestableResourceManager ?: return
        resourceMgr.destroyDestructibleNodesInUltimatePath(
            beamOrigin = mergeCenterPos,
            aimDirection = aimDirection,
            beamLength = BEAM_LENGTH,
            beamHalfWidth = BEAM_CORRIDOR_HALF_WIDTH + 1.15f,
            impactCenter = beamImpactPos,
            impactRadius = IMPACT_RADIUS,
            inventory = playerInventory,
            combatSystem = combatSystem,
            onCollected = { itemId, count ->
                onResourceDestroyedNotification?.invoke(itemId, count)
            }
        )
    }

    private fun finishAbilityExecution(
        player: PlayerCharacter,
        cameraController: FollowCameraController
    ) {
        currentPhase = UltimatePhase.INACTIVE
        elapsedTime = 0f
        cooldownRemaining = COOLDOWN_DURATION
        activeIncantationStep = null
        activeIncantationProgress = 0f
        player.activeAttackProgress = 0f
        player.animationState = AnimationState.IDLE
        player.invulnerabilityTimer = 0.25f
        cameraController.setUltimateCinematicState(active = false, phase = UltimatePhase.INACTIVE, phaseProgress = 0f)
    }

    /**
     * Safely cancels or resets the ultimate ability (e.g., on world reset or pause recovery)
     * so the player is never permanently locked.
     */
    fun reset(player: PlayerCharacter? = null, cameraController: FollowCameraController? = null) {
        currentPhase = UltimatePhase.INACTIVE
        elapsedTime = 0f
        cooldownRemaining = 0f
        activeIncantationStep = null
        activeIncantationProgress = 0f
        displayedIncantationLog.clear()
        screenFlashAlpha = 0f
        hitEnemyIds.clear()
        hasTriggeredMergeExplosion = false
        hasTriggeredPhase5Impact = false
        if (player != null) {
            player.activeAttackProgress = 0f
            if (player.animationState.name.startsWith("ULTIMATE_")) {
                player.animationState = AnimationState.IDLE
            }
        }
        cameraController?.setUltimateCinematicState(active = false, phase = UltimatePhase.INACTIVE, phaseProgress = 0f)
    }

    /**
     * Renders the 3D dual energy spheres, orbiting blue lightning arcs, dark purple void rings,
     * merged cataclysmic core, and directional Divine Annihilation beam.
     */
    fun render(modelBatch: ModelBatch, environment: Environment) {
        if (!isActive || !modelsInitialized) return

        when (currentPhase) {
            UltimatePhase.PHASE_1_AWAKENING -> {
                val p = (elapsedTime / PHASE_1_END).coerceIn(0f, 1f)
                // Nascent energy sparks forming above both hands during late Awakening
                if (p > 0.35f) {
                    val seedScale = ((p - 0.35f) / 0.65f) * 0.38f
                    renderLightSphere(whiteSpherePos, seedScale)
                    renderDarkSphere(blackSpherePos, seedScale)
                }
            }

            UltimatePhase.PHASE_2_DUAL_SUMMON -> {
                val p = ((elapsedTime - PHASE_1_END) / (PHASE_2_END - PHASE_1_END)).coerceIn(0f, 1f)
                val sphereScale = 0.38f + p * 0.72f
                renderLightSphere(whiteSpherePos, sphereScale)
                renderDarkSphere(blackSpherePos, sphereScale)
            }

            UltimatePhase.PHASE_3_INCANTATION -> {
                val pulse = 1.10f + sin(elapsedTime * 11f) * 0.09f
                renderLightSphere(whiteSpherePos, pulse)
                renderDarkSphere(blackSpherePos, pulse)
            }

            UltimatePhase.PHASE_4_MERGING -> {
                val p = ((elapsedTime - PHASE_3_END) / (PHASE_4_END - PHASE_3_END)).coerceIn(0f, 1f)
                if (p < 0.58f) {
                    val scale = 1.15f * (1f - 0.25f * (p / 0.58f))
                    renderLightSphere(whiteSpherePos, scale)
                    renderDarkSphere(blackSpherePos, scale)
                }
                if (p >= 0.42f) {
                    val mergeT = ((p - 0.42f) / 0.58f).coerceIn(0f, 1f)
                    renderMergedCataclysmSphere(mergeCenterPos, mergeT, modelBatch, environment)
                }
            }

            UltimatePhase.PHASE_5_ANNIHILATION -> {
                val p = ((elapsedTime - PHASE_4_END) / (TOTAL_DURATION - PHASE_4_END)).coerceIn(0f, 1f)
                renderMergedCataclysmSphere(mergeCenterPos, 1.0f - p * 0.35f, modelBatch, environment)
                renderAnnihilationBeamAndImpact(p, modelBatch, environment)
            }

            UltimatePhase.INACTIVE -> Unit
        }

        // Render active dual spheres when in Phases 1..4
        if (currentPhase == UltimatePhase.PHASE_1_AWAKENING && (elapsedTime / PHASE_1_END) > 0.35f ||
            currentPhase == UltimatePhase.PHASE_2_DUAL_SUMMON ||
            currentPhase == UltimatePhase.PHASE_3_INCANTATION ||
            (currentPhase == UltimatePhase.PHASE_4_MERGING && ((elapsedTime - PHASE_3_END) / (PHASE_4_END - PHASE_3_END)) < 0.58f)
        ) {
            modelBatch.render(whiteCoreInstance, environment)
            modelBatch.render(blueLightningShellInstance, environment)
            for (arc in blueLightningArcInstances) {
                modelBatch.render(arc, environment)
            }

            modelBatch.render(blackCoreInstance, environment)
            modelBatch.render(darkPurpleShellInstance, environment)
            for (ring in darkPurpleRingInstances) {
                modelBatch.render(ring, environment)
            }
        }
    }

    private fun renderLightSphere(pos: Vector3, scale: Float) {
        val corePulse = scale * (0.94f + sin(elapsedTime * 18f) * 0.08f)
        val shellPulse = scale * (1.02f + cos(elapsedTime * 14f) * 0.10f)

        whiteCoreInstance.transform.idt()
            .translate(pos.x, pos.y, pos.z)
            .rotate(0f, 1f, 0f, elapsedTime * 140f)
            .scale(corePulse, corePulse, corePulse)

        blueLightningShellInstance.transform.idt()
            .translate(pos.x, pos.y, pos.z)
            .rotate(1f, 1f, 0f, -elapsedTime * 210f)
            .scale(shellPulse, shellPulse, shellPulse)

        for (i in blueLightningArcInstances.indices) {
            val arcScale = scale * (1.08f + (i * 0.14f) + sin(elapsedTime * 28f + i * 2f) * 0.12f)
            val spinYaw = elapsedTime * (320f + i * 110f) * if (i % 2 == 0) 1f else -1f
            val spinPitch = elapsedTime * (240f + i * 75f) + i * 58f
            blueLightningArcInstances[i].transform.idt()
                .translate(pos.x, pos.y, pos.z)
                .rotate(0f, 1f, 0f, spinYaw)
                .rotate(1f, 0f, 1f, spinPitch)
                .scale(arcScale, arcScale, arcScale)
        }
    }

    private fun renderDarkSphere(pos: Vector3, scale: Float) {
        val corePulse = scale * (0.96f + cos(elapsedTime * 15f) * 0.07f)
        val shellPulse = scale * (1.04f + sin(elapsedTime * 12f) * 0.11f)

        blackCoreInstance.transform.idt()
            .translate(pos.x, pos.y, pos.z)
            .rotate(0f, 1f, 0f, -elapsedTime * 120f)
            .scale(corePulse, corePulse, corePulse)

        darkPurpleShellInstance.transform.idt()
            .translate(pos.x, pos.y, pos.z)
            .rotate(0f, 1f, 1f, elapsedTime * 175f)
            .scale(shellPulse, shellPulse, shellPulse)

        for (i in darkPurpleRingInstances.indices) {
            val ringScale = scale * (1.05f + i * 0.18f + cos(elapsedTime * 16f + i * 1.7f) * 0.10f)
            val spinYaw = -elapsedTime * (220f + i * 85f)
            val spinRoll = elapsedTime * (190f + i * 65f) + i * 62f
            darkPurpleRingInstances[i].transform.idt()
                .translate(pos.x, pos.y, pos.z)
                .rotate(0f, 1f, 0f, spinYaw)
                .rotate(1f, 0f, 0f, spinRoll)
                .scale(ringScale, ringScale, ringScale)
        }
    }

    private fun renderMergedCataclysmSphere(
        pos: Vector3,
        intensity: Float,
        modelBatch: ModelBatch,
        environment: Environment
    ) {
        val coreScale = intensity * (1.15f + sin(elapsedTime * 26f) * 0.14f)
        val outerScale = intensity * (1.32f + cos(elapsedTime * 22f) * 0.18f)

        mergedSphereCoreInstance.transform.idt()
            .translate(pos.x, pos.y, pos.z)
            .rotate(0f, 1f, 0f, elapsedTime * 260f)
            .scale(coreScale, coreScale, coreScale)

        mergedSphereOuterInstance.transform.idt()
            .translate(pos.x, pos.y, pos.z)
            .rotate(1f, 1f, 0f, -elapsedTime * 310f)
            .scale(outerScale, outerScale, outerScale)

        modelBatch.render(mergedSphereCoreInstance, environment)
        modelBatch.render(mergedSphereOuterInstance, environment)

        for (i in distortionRingInstances.indices) {
            val wave = ((elapsedTime * 2.2f + i * 0.25f) % 1.0f)
            val ringScale = intensity * (0.85f + wave * 2.65f)
            distortionRingInstances[i].transform.idt()
                .translate(pos.x, pos.y, pos.z)
                .rotate(0f, 1f, 0f, elapsedTime * (280f + i * 60f))
                .rotate(1f, 0f, 1f, i * 45f + elapsedTime * 160f)
                .scale(ringScale, ringScale, ringScale)
            modelBatch.render(distortionRingInstances[i], environment)
        }
    }

    private fun renderAnnihilationBeamAndImpact(
        phaseProgress: Float,
        modelBatch: ModelBatch,
        environment: Environment
    ) {
        // Compute beam orientation angles from mergeCenterPos along aimDirection
        val dx = aimDirection.x
        val dy = aimDirection.y
        val dz = aimDirection.z
        val horizDist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.0001f)
        val beamYawDeg = atan2(-dx, -dz) * MathUtils.radiansToDegrees
        val beamPitchDeg = atan2(dy, horizDist) * MathUtils.radiansToDegrees

        // Envelope scales rapidly up on ignition and tapers smoothly at the very end
        val envelope = when {
            phaseProgress < 0.12f -> (phaseProgress / 0.12f).coerceIn(0.1f, 1f)
            phaseProgress > 0.82f -> ((1f - phaseProgress) / 0.18f).coerceIn(0.1f, 1f)
            else -> 1.0f + sin(elapsedTime * 38f) * 0.12f
        }
        val currentBeamLength = BEAM_LENGTH * (if (phaseProgress < 0.08f) (phaseProgress / 0.08f) else 1f)

        beamInnerCoreInstance.transform.idt()
            .translate(mergeCenterPos.x, mergeCenterPos.y, mergeCenterPos.z)
            .rotate(0f, 1f, 0f, beamYawDeg)
            .rotate(1f, 0f, 0f, beamPitchDeg)
            .scale(envelope, envelope, currentBeamLength)

        val outerPulse = envelope * (1.12f + cos(elapsedTime * 32f) * 0.14f)
        beamOuterPlasmaInstance.transform.idt()
            .translate(mergeCenterPos.x, mergeCenterPos.y, mergeCenterPos.z)
            .rotate(0f, 1f, 0f, beamYawDeg)
            .rotate(1f, 0f, 0f, beamPitchDeg)
            .scale(outerPulse, outerPulse, currentBeamLength)

        modelBatch.render(beamInnerCoreInstance, environment)
        modelBatch.render(beamOuterPlasmaInstance, environment)

        for (i in beamHelixInstances.indices) {
            val spin = elapsedTime * (540f * if (i == 0) 1f else -1f)
            beamHelixInstances[i].transform.idt()
                .translate(mergeCenterPos.x, mergeCenterPos.y, mergeCenterPos.z)
                .rotate(0f, 1f, 0f, beamYawDeg)
                .rotate(1f, 0f, 0f, beamPitchDeg)
                .rotate(0f, 0f, 1f, spin)
                .scale(envelope * 1.18f, envelope * 1.18f, currentBeamLength)
            modelBatch.render(beamHelixInstances[i], environment)
        }

        // Terminal cataclysmic impact dome & expanding ground shockwaves
        val domeScale = IMPACT_RADIUS * 0.62f * envelope
        impactDomeInstance.transform.idt()
            .translate(beamImpactPos.x, beamImpactPos.y + 0.35f, beamImpactPos.z)
            .rotate(0f, 1f, 0f, elapsedTime * 240f)
            .scale(domeScale, domeScale * 0.68f, domeScale)
        modelBatch.render(impactDomeInstance, environment)

        for (i in groundShockwaveInstances.indices) {
            val waveProgress = ((phaseProgress * 2.4f + i * 0.33f) % 1.0f)
            val ringRadius = 1.5f + waveProgress * IMPACT_RADIUS * 1.15f
            groundShockwaveInstances[i].transform.idt()
                .translate(beamImpactPos.x, beamImpactPos.y + 0.18f + i * 0.08f, beamImpactPos.z)
                .rotate(0f, 1f, 0f, elapsedTime * (180f + i * 90f))
                .scale(ringRadius, 1.2f, ringRadius)
            modelBatch.render(groundShockwaveInstances[i], environment)
        }
    }
}
