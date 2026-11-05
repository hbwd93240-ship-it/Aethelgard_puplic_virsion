package com.aethelgard.survival.core

import com.aethelgard.survival.core.assets.GameAssetManager
import com.aethelgard.survival.core.audio.AudioManager
import com.aethelgard.survival.core.building.BuildingInteractionType
import com.aethelgard.survival.core.building.BuildingManager
import com.aethelgard.survival.core.building.BuildingModelFactory
import com.aethelgard.survival.core.building.BuildingRaidManager
import com.aethelgard.survival.core.building.PlacedBuildingPiece
import com.aethelgard.survival.core.camera.FollowCameraController
import com.aethelgard.survival.core.combat.CombatSystem
import com.aethelgard.survival.core.entities.BlueFlowerMonsterController
import com.aethelgard.survival.core.entities.EnemyController
import com.aethelgard.survival.core.entities.EntityManager
import com.aethelgard.survival.core.entities.GreenSlimeController
import com.aethelgard.survival.core.entities.GroundPickupManager
import com.aethelgard.survival.core.entities.RedFlowerMonsterController
import com.aethelgard.survival.core.entities.WorldEntity
import com.aethelgard.survival.core.input.TouchInputController
import com.aethelgard.survival.core.player.PlayerCharacter
import com.aethelgard.survival.core.player.PlayerController
import com.aethelgard.survival.core.survival.ChestManager
import com.aethelgard.survival.core.survival.CraftingManager
import com.aethelgard.survival.core.survival.HarvestNodeType
import com.aethelgard.survival.core.survival.HarvestableNode
import com.aethelgard.survival.core.survival.HarvestableResourceManager
import com.aethelgard.survival.core.survival.Inventory
import com.aethelgard.survival.core.survival.ResourceCatalog
import com.aethelgard.survival.core.survival.SurvivalAttributes
import com.aethelgard.survival.core.survival.SurvivalHudOverlay
import com.aethelgard.survival.core.survival.SurvivalModalType
import com.aethelgard.survival.core.survival.SurvivalSaveManager
import com.aethelgard.survival.core.ui.HudRenderer
import com.aethelgard.survival.core.world.ProceduralModelFactory
import com.aethelgard.survival.core.world.WorldGenerator
import com.aethelgard.survival.core.world.WorldManager
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
import com.badlogic.gdx.math.Vector3

/**
 * Main platform-independent LibGDX 3D game class for AethelgardSurvival (Milestone 1 + Milestone 2 + Freeform Building).
 * Orchestrates procedural 3D model generation, the twilight fantasy forest clearing,
 * the articulated Anime Fantasy spellsword protagonist, the Free-Form Combat System
 * (Hand-to-Hand Martial Arts, Rune Spellsword, Arcane Magic Bolts, Charged Energy Blasts, Dodge/Dash),
 * the Freeform Building System, the Corrupted Mana Guardian Knight test enemy, third/first-person camera, HUD, and synthesized audio.
 */
class AethelgardGame(
    private val deferInitForSurfaceSync: Boolean = false,
    private val onFatalError: ((String, Throwable) -> Unit)? = null
) : ApplicationAdapter() {

    lateinit var assetManager: GameAssetManager
        private set
    lateinit var audioManager: AudioManager
        private set
    lateinit var modelFactory: ProceduralModelFactory
        private set
    lateinit var worldGenerator: WorldGenerator
        private set
    lateinit var worldManager: WorldManager
        private set
    lateinit var entityManager: EntityManager
        private set
    lateinit var combatSystem: CombatSystem
        private set
    lateinit var playerCharacter: PlayerCharacter
        private set
    lateinit var playerController: PlayerController
        private set
    lateinit var cameraController: FollowCameraController
        private set
    lateinit var touchInputController: TouchInputController
        private set
    lateinit var hudRenderer: HudRenderer
        private set
    lateinit var predictionHakiController: com.aethelgard.survival.core.combat.PredictionHakiController
        private set

    // Survival Mode & Freeform Building Core Systems
    lateinit var inventory: Inventory
        private set
    lateinit var survivalAttributes: SurvivalAttributes
        private set
    lateinit var craftingManager: CraftingManager
        private set
    lateinit var chestManager: ChestManager
        private set
    lateinit var harvestableResourceManager: HarvestableResourceManager
        private set
    lateinit var survivalSaveManager: SurvivalSaveManager
        private set
    lateinit var survivalOverlay: SurvivalHudOverlay
        private set
    lateinit var buildingModelFactory: BuildingModelFactory
        private set
    lateinit var buildingManager: BuildingManager
        private set
    lateinit var buildingRaidManager: BuildingRaidManager
        private set
    lateinit var groundPickupManager: GroundPickupManager
        private set

    // First-person held 3D tool viewmodel instances keyed by toolId
    val firstPersonToolInstances = mutableMapOf<String, ModelInstance>()
    private val fpForward = Vector3()
    private val fpRight = Vector3()
    private val fpUp = Vector3()
    private val fpHandPos = Vector3()

    private var autoSaveTimer: Float = 0f
    private var knightWasDead: Boolean = false

    private lateinit var camera: PerspectiveCamera
    private lateinit var modelBatch: ModelBatch
    private var isGameInitialized: Boolean = false
    private var surfaceSyncFramesPresented: Int = 0
    private var pendingWidth: Int = 0
    private var pendingHeight: Int = 0
    private var hasLoggedFirstFrame: Boolean = false
    private var fatalFailureOccurred: Boolean = false

    override fun create() {
        pendingWidth = Gdx.graphics.width.coerceAtLeast(1)
        pendingHeight = Gdx.graphics.height.coerceAtLeast(1)
        if (deferInitForSurfaceSync) {
            Gdx.app?.log("AethelgardDiag", "Deferring heavy 3D initialization until initial SurfaceSyncGroup frames are swapped...")
            return
        }
        initializeAllSystems()
    }

    private fun initializeAllSystems() {
        if (isGameInitialized) return
        try {
            Gdx.app?.log("AethelgardDiag", "AethelgardGame.initializeAllSystems() starting...")
            val width = (if (pendingWidth > 0) pendingWidth else Gdx.graphics.width).coerceAtLeast(1)
            val height = (if (pendingHeight > 0) pendingHeight else Gdx.graphics.height).coerceAtLeast(1)

            // 1. Initialize 3D Perspective Camera
            camera = PerspectiveCamera(64f, width.toFloat(), height.toFloat()).apply {
                near = 0.18f
                far = 260f
            }

            // 2. Initialize ModelBatch with shader support for directional + point lights and driver fallback
            val shaderConfig = com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config().apply {
                numDirectionalLights = 2
                numPointLights = 2
                numSpotLights = 0
            }
            val fallbackShaderConfig = com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config().apply {
                numDirectionalLights = 1
                numPointLights = 0
                numSpotLights = 0
            }
            modelBatch = ModelBatch(object : DefaultShaderProvider(shaderConfig) {
                override fun createShader(renderable: com.badlogic.gdx.graphics.g3d.Renderable): com.badlogic.gdx.graphics.g3d.Shader {
                    return try {
                        val shader = com.badlogic.gdx.graphics.g3d.shaders.DefaultShader(renderable, config)
                        if (!shader.program.isCompiled) {
                            Gdx.app?.error("AethelgardDiag", "Primary 3D shader failed to compile, using fallback: ${shader.program.log}")
                            shader.dispose()
                            com.badlogic.gdx.graphics.g3d.shaders.DefaultShader(renderable, fallbackShaderConfig)
                        } else {
                            shader
                        }
                    } catch (t: Throwable) {
                        Gdx.app?.error("AethelgardDiag", "Primary 3D shader threw exception, using fallback", t)
                        com.badlogic.gdx.graphics.g3d.shaders.DefaultShader(renderable, fallbackShaderConfig)
                    }
                }
            })

            // 3. Initialize procedural assets, audio, world, entities, and combat systems
            assetManager = GameAssetManager().also { it.initialize() }
            audioManager = AudioManager().also { it.initialize(async = deferInitForSurfaceSync) }
            modelFactory = ProceduralModelFactory(assetManager)
        worldGenerator = WorldGenerator(modelFactory)
        worldManager = WorldManager(worldGenerator).also { it.initialize() }
        entityManager = EntityManager()

        combatSystem = CombatSystem(
            modelFactory = modelFactory,
            worldManager = worldManager,
            entityManager = entityManager,
            audioManager = audioManager
        ).also {
            it.initialize()
        }

        // 4. Create articulated 3D Corrupted Mana Guardian Knight test enemy in the clearing
        val enemySpawn = Vector3(0f, worldManager.getWalkableSurfaceHeight(0f, 8.5f), 8.5f)
        val enemyModel = modelFactory.createCorruptedGuardianEnemyModel()
        val enemyEntity = WorldEntity(
            id = "guardian_knight_01",
            name = "Corrupted Mana Guardian",
            modelInstance = ModelInstance(enemyModel),
            position = Vector3(enemySpawn)
        )
        val guardianController = EnemyController(
            entity = enemyEntity,
            spawnOrigin = enemySpawn,
            maxHealth = 280f
        ).also {
            it.resetToSpawn(worldManager)
        }
        entityManager.registerEnemyController(guardianController)

        // 4b. Initialize Ground Pickup Manager & Spawn Green Slimes across grassy woodland clearings
        groundPickupManager = GroundPickupManager(modelFactory, worldManager)
        val slimeSpawns = listOf(
            Vector3(18.0f, 0f, 12.0f),
            Vector3(-18.5f, 0f, 10.0f),
            Vector3(-15.0f, 0f, -14.0f),
            Vector3(16.5f, 0f, -13.5f)
        )
        val slimeModel = modelFactory.createGreenSlimeModel()
        for ((idx, spawn) in slimeSpawns.withIndex()) {
            val groundY = worldManager.getWalkableSurfaceHeight(spawn.x, spawn.z)
            spawn.y = groundY
            val slimeEntity = WorldEntity(
                id = "green_slime_${idx + 1}",
                name = "Green Slime",
                modelInstance = ModelInstance(slimeModel),
                position = Vector3(spawn)
            )
            val slimeController = GreenSlimeController(
                entity = slimeEntity,
                spawnOrigin = Vector3(spawn),
                maxHealth = 95f,
                modelFactory = modelFactory,
                onDropCrystal = { deathPos ->
                    groundPickupManager.spawnGreenCrystal(deathPos)
                }
            ).also {
                it.resetToSpawn(worldManager)
            }
            entityManager.registerEnemyController(slimeController)
        }

        // 4c. Spawn Blue Flower Monsters across woodland edges (drop MP Potions when defeated)
        val blueFlowerSpawns = listOf(
            Vector3(14.5f, 0f, 18.0f),
            Vector3(-16.0f, 0f, 17.5f),
            Vector3(12.0f, 0f, -18.0f)
        )
        val blueFlowerModel = modelFactory.createBlueFlowerMonsterModel()
        for ((idx, spawn) in blueFlowerSpawns.withIndex()) {
            worldManager.resolveObstacleCollisions(spawn, 0.9f)
            val groundY = worldManager.getWalkableSurfaceHeight(spawn.x, spawn.z)
            spawn.y = groundY
            val flowerEntity = WorldEntity(
                id = "blue_flower_${idx + 1}",
                name = "Blue Flower Monster",
                modelInstance = ModelInstance(blueFlowerModel),
                position = Vector3(spawn)
            )
            val flowerController = BlueFlowerMonsterController(
                entity = flowerEntity,
                spawnOrigin = Vector3(spawn),
                modelFactory = modelFactory,
                groundPickupManager = groundPickupManager,
                maxHealth = 110f
            ).also {
                it.resetToSpawn(worldManager)
            }
            entityManager.registerEnemyController(flowerController)
        }

        // 4d. Spawn Red Flower Monsters across grassy forest clearings (drop Health Potions when defeated)
        val redFlowerSpawns = listOf(
            Vector3(-13.5f, 0f, 22.5f),
            Vector3(15.5f, 0f, 23.0f),
            Vector3(-11.5f, 0f, -19.5f)
        )
        val redFlowerModel = modelFactory.createRedFlowerMonsterModel()
        for ((idx, spawn) in redFlowerSpawns.withIndex()) {
            worldManager.resolveObstacleCollisions(spawn, 0.9f)
            val groundY = worldManager.getWalkableSurfaceHeight(spawn.x, spawn.z)
            spawn.y = groundY
            val redFlowerEntity = WorldEntity(
                id = "red_flower_${idx + 1}",
                name = "Red Flower Monster",
                modelInstance = ModelInstance(redFlowerModel),
                position = Vector3(spawn)
            )
            val redFlowerController = RedFlowerMonsterController(
                entity = redFlowerEntity,
                spawnOrigin = Vector3(spawn),
                modelFactory = modelFactory,
                groundPickupManager = groundPickupManager,
                maxHealth = 95f
            ).also {
                it.resetToSpawn(worldManager)
            }
            entityManager.registerEnemyController(redFlowerController)
        }

        // 5. Create articulated 3D Anime Fantasy protagonist at the clearing entrance
        val spawnPos = Vector3(0f, worldManager.getWalkableSurfaceHeight(0f, 20f), 20f)
        val playerModel = modelFactory.createAnimePlayerModel()
        playerCharacter = PlayerCharacter(playerModel, spawnPos).also {
            it.resetToSpawn(spawnPos.y)
        }

        cameraController = FollowCameraController(camera, worldManager).also {
            it.resetToDefault(playerCharacter)
        }

        playerController = PlayerController(playerCharacter, worldManager, audioManager)

        // 6. Initialize Multi-Touch Input & Combat HUD
        touchInputController = TouchInputController(
            onLookDelta = { dYaw, dPitch ->
                cameraController.applyLookDelta(dYaw, dPitch)
            },
            onToggleCameraMode = {
                cameraController.toggleCameraMode()
                audioManager.playUiClick()
            },
            onResetRequested = {
                playerController.resetPlayerAndCamera(cameraController, combatSystem)
                entityManager.resetAllEnemies(worldManager)
                groundPickupManager.clear()
                predictionHakiController.reset()
            },
            onToggleAudio = {
                audioManager.isAudioEnabled = !audioManager.isAudioEnabled
                if (audioManager.isAudioEnabled) {
                    audioManager.playUiClick()
                }
            },
            onSprintStateChanged = { active ->
                audioManager.playSprintToggle(active)
            },
            onLightAttack = {
                combatSystem.triggerLightAttack(playerCharacter, cameraController)
            },
            onHeavyAttack = {
                combatSystem.triggerHeavyAttack(playerCharacter, cameraController)
            },
            onEnergyPress = {
                combatSystem.beginEnergyCharge(playerCharacter, cameraController)
            },
            onEnergyRelease = {
                combatSystem.releaseEnergyAttack(playerCharacter, cameraController)
            },
            onToggleWeaponStance = {
                combatSystem.toggleWeaponStance(playerCharacter)
            },
            onToggleLockOn = {
                toggleLockOnTarget()
            },
            onUltimateAttack = {
                combatSystem.triggerUltimateAbility(playerCharacter, cameraController)
            },
            onTogglePredictionHaki = {
                predictionHakiController.toggleActive()
            }
        ).also {
            it.updateLayout(width, height)
        }

        // 6b. Initialize Prediction Haki Controller
        predictionHakiController = com.aethelgard.survival.core.combat.PredictionHakiController(
            player = playerCharacter,
            entityManager = entityManager,
            combatSystem = combatSystem,
            audioManager = audioManager
        )

        // 7. Initialize Survival Mode & Freeform Building Core Systems
        inventory = Inventory(25)
        survivalAttributes = SurvivalAttributes()
        craftingManager = CraftingManager()
        chestManager = ChestManager(20)
        harvestableResourceManager = HarvestableResourceManager()
        survivalSaveManager = SurvivalSaveManager()
        survivalOverlay = SurvivalHudOverlay()
        buildingModelFactory = BuildingModelFactory(assetManager)
        buildingManager = BuildingManager(worldManager, buildingModelFactory)
        buildingRaidManager = BuildingRaidManager(buildingManager, entityManager, audioManager)

        // Create 3D held item & tool models for First-Person viewmodel rendering
        firstPersonToolInstances.clear()
        for (itemDef in ResourceCatalog.ALL_HOLDABLE_ITEMS) {
            val itemModel = modelFactory.createHeldToolModel(itemDef.id)
            firstPersonToolInstances[itemDef.id] = ModelInstance(itemModel)
        }

        val tableModel = modelFactory.createCraftingTableModel()
        val chestModel = modelFactory.createStorageChestModel()
        val tableInstance = ModelInstance(tableModel)
        val chestInstance = ModelInstance(chestModel)

        harvestableResourceManager.groundPickupManager = groundPickupManager
        harvestableResourceManager.initialize(worldManager, tableInstance, chestInstance, modelFactory)
        harvestableResourceManager.linkWorldData(worldManager.generatedData)

        // Connect DIVINE ANNIHILATION ultimate ability with destructible world resources & inventory
        combatSystem.divineAnnihilationController.harvestableResourceManager = harvestableResourceManager
        combatSystem.divineAnnihilationController.playerInventory = inventory
        combatSystem.divineAnnihilationController.onResourceDestroyedNotification = { id, count ->
            survivalOverlay.addNotification(id, count)
        }

        // Load persisted survival & placed building state
        survivalSaveManager.loadAll(
            inventory = inventory,
            chestManager = chestManager,
            attributes = survivalAttributes,
            resourceManager = harvestableResourceManager,
            buildingManager = buildingManager,
            dayNightCycle = worldManager.dayNightCycle
        )

        // Provide starter survival tools & provisions if brand new save
        if (inventory.countItem(ResourceCatalog.STONE_AXE.id) == 0 && inventory.countItem(ResourceCatalog.WOOD.id) == 0) {
            inventory.addItem(ResourceCatalog.STONE_AXE.id, 1)
            inventory.addItem(ResourceCatalog.STONE_PICKAXE.id, 1)
            inventory.addItem(ResourceCatalog.BUILDERS_HAMMER.id, 1)
            inventory.addItem(ResourceCatalog.BERRIES.id, 5)
            inventory.addItem(ResourceCatalog.HERBAL_SALVE.id, 2)
        }
        syncHeldHotbarItem()

        // Connect touch input controller with survival HUD overlay & Freeform Building controls
        touchInputController.isBuildModeActive = {
            buildingManager.isBuildModeActive
        }
        touchInputController.onBuildDragDelta = { dRight, dForward ->
            buildingManager.applyDragAdjustment(dRight, dForward, cameraController.yawDegrees)
        }
        touchInputController.onTouchIntercept = { x, y ->
            val nearest = harvestableResourceManager.findNearestNode(playerCharacter.position, 3.5f)
            val nearestPiece = buildingManager.findNearestPiece(playerCharacter.position, 4.2f)
            val isNearTable = (nearest?.type == HarvestNodeType.CRAFTING_TABLE) ||
                buildingManager.isNearPlacedCraftingTable(playerCharacter.position)
            val isHammerEquipped = (playerCharacter.equippedToolId == ResourceCatalog.BUILDERS_HAMMER.id)
            val handled = survivalOverlay.handleTouch(
                x = x,
                y = y,
                inventory = inventory,
                attributes = survivalAttributes,
                craftingManager = craftingManager,
                chestManager = chestManager,
                player = playerCharacter,
                audioManager = audioManager,
                isNearCraftingTable = isNearTable,
                isNearInteractableNode = (nearest != null),
                buildingManager = buildingManager,
                nearestBuildingPiece = nearestPiece,
                isHammerEquipped = isHammerEquipped,
                onHammerAction = { action, piece ->
                    handleHammerAction(action, piece)
                },
                onHarvestOrInteract = {
                    handleContextualInteract(nearest, nearestPiece)
                }
            )
            syncHeldHotbarItem()
            handled
        }
        touchInputController.isModalOpen = {
            survivalOverlay.activeModal != SurvivalModalType.NONE
        }

        hudRenderer = HudRenderer().also {
            it.initialize()
            it.resize(width, height)
        }
        val uiScale = (height.toFloat() / 720f).coerceIn(0.65f, 2.2f)
        survivalOverlay.updateLayout(width.toFloat(), height.toFloat(), uiScale)

        // Register TouchInputController as the primary LibGDX input processor
        Gdx.input.inputProcessor = touchInputController
        isGameInitialized = true
        Gdx.app?.log("AethelgardDiag", "AethelgardGame.initializeAllSystems() finished successfully, InputProcessor registered.")
    } catch (t: Throwable) {
        fatalFailureOccurred = true
        Gdx.app?.error("AethelgardCrash", "FATAL ERROR during AethelgardGame.create()", t)
        onFatalError?.invoke("AethelgardGame.create()", t)
        throw t
    }
}

    fun handleHammerAction(action: String, piece: PlacedBuildingPiece) {
        playerCharacter.triggerToolSwing(com.aethelgard.survival.core.player.AnimationState.TOOL_HAMMER_BUILD, 0.36f)
        when (action) {
            "repair" -> {
                val repaired = buildingManager.repairPiece(piece, inventory, audioManager)
                if (repaired) {
                    survivalOverlay.addNotification("REPAIRED ${piece.definition.name}", 1)
                } else {
                    survivalOverlay.addNotification("FULL HP OR MISSING MAT", 0)
                }
            }
            "upgrade" -> {
                val upgraded = buildingManager.upgradePiece(piece, inventory, audioManager)
                if (upgraded != null) {
                    survivalOverlay.addNotification("UPGRADED TO ${upgraded.definition.name}", 1)
                } else {
                    survivalOverlay.addNotification("CANNOT UPGRADE / MISSING MATS", 0)
                }
            }
            "dismantle" -> {
                val refunded = buildingManager.dismantlePiece(piece, inventory, audioManager)
                if (refunded.isNotEmpty()) {
                    for ((resId, count) in refunded) {
                        survivalOverlay.addNotification(resId, count)
                    }
                } else {
                    survivalOverlay.addNotification("CRITICAL SUPPORT - CANNOT DISMANTLE", 0)
                }
            }
        }
    }

    fun handleContextualInteract(nearest: HarvestableNode?, nearestPiece: PlacedBuildingPiece? = null) {
        if (nearest != null) {
            when (nearest.type) {
                HarvestNodeType.CRAFTING_TABLE -> {
                    survivalOverlay.activeModal = SurvivalModalType.CRAFTING
                    audioManager.playUiClick()
                }
                HarvestNodeType.STORAGE_CHEST -> {
                    survivalOverlay.activeModal = SurvivalModalType.CHEST
                    audioManager.playUiClick()
                }
                else -> {
                    if (survivalAttributes.stamina < 3f) {
                        survivalOverlay.addNotification("EXHAUSTED", 0)
                        return
                    }
                    survivalAttributes.consumeStamina(4.5f)
                    buildingRaidManager.recordNoiseEvent(8f, playerCharacter.position)
                    val success = harvestableResourceManager.harvestNode(
                        node = nearest,
                        player = playerCharacter,
                        inventory = inventory,
                        audioManager = audioManager,
                        combatSystem = combatSystem
                    ) { id, count ->
                        survivalOverlay.addNotification(id, count)
                    }
                    if (success && !playerCharacter.isUsingTool) {
                        combatSystem.triggerLightAttack(playerCharacter, cameraController)
                    }
                }
            }
            return
        }

        if (nearestPiece != null && nearestPiece.definition.interactionType != BuildingInteractionType.NONE) {
            val result = buildingManager.interactWithPiece(
                piece = nearestPiece,
                player = playerCharacter,
                attributes = survivalAttributes,
                audioManager = audioManager
            )
            when (result) {
                "OPEN_CHEST" -> survivalOverlay.activeModal = SurvivalModalType.CHEST
                "OPEN_CRAFTING" -> survivalOverlay.activeModal = SurvivalModalType.CRAFTING
                else -> if (result.isNotEmpty()) survivalOverlay.addNotification(result, 1)
            }
            return
        }

        audioManager.playUiClick()
    }

    private fun toggleLockOnTarget() {
        if (cameraController.isLockedOn) {
            cameraController.clearLockOn()
            audioManager.playLockOnToggle(false)
        } else {
            val candidate = entityManager.findNearestLockOnTarget(playerCharacter, camera)
            if (candidate != null) {
                cameraController.setLockOnTarget(candidate)
                audioManager.playLockOnToggle(true)
            } else {
                audioManager.playUiClick()
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        pendingWidth = width
        pendingHeight = height
        if (!isGameInitialized) return
        if (!::camera.isInitialized || !::touchInputController.isInitialized || !::hudRenderer.isInitialized) return
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
        touchInputController.updateLayout(width, height)
        if (::survivalOverlay.isInitialized) {
            val uiScale = (height.toFloat() / 720f).coerceIn(0.65f, 2.2f)
            survivalOverlay.updateLayout(width.toFloat(), height.toFloat(), uiScale)
        }
        hudRenderer.resize(width, height)
        Gdx.input.inputProcessor = touchInputController
    }

    override fun resume() {
        super.resume()
        if (::touchInputController.isInitialized) {
            Gdx.input.inputProcessor = touchInputController
        }
    }

    override fun render() {
        if (fatalFailureOccurred) return
        if (deferInitForSurfaceSync && !isGameInitialized) {
            if (surfaceSyncFramesPresented < 2) {
                surfaceSyncFramesPresented++
                val w = Gdx.graphics.width.coerceAtLeast(1)
                val h = Gdx.graphics.height.coerceAtLeast(1)
                Gdx.gl.glViewport(0, 0, w, h)
                Gdx.gl.glClearColor(0.05f, 0.08f, 0.16f, 1f)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                return
            }
            initializeAllSystems()
            if (fatalFailureOccurred) return
        }
        if (!::camera.isInitialized || !::modelBatch.isInitialized || !::hudRenderer.isInitialized) return
        try {
            // Guard against input processor being cleared or overwritten
            if (Gdx.input.inputProcessor != touchInputController) {
                Gdx.input.inputProcessor = touchInputController
            }

            // Clear OpenGL color & depth buffers using dynamic atmospheric sky color
            val cc = worldManager.dayNightCycle.clearColor
            Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
            Gdx.gl.glClearColor(cc.r, cc.g, cc.b, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

            val delta = Gdx.graphics.deltaTime.coerceIn(0.001f, 0.05f)
            val wasLockedOn = cameraController.isLockedOn

            // Sync equipped tool & visible held item from active hotbar slot when not mid-tool-swing
            syncHeldHotbarItem()

            // Update world, player, combat, enemies, and camera
            worldManager.update(delta)
            playerController.update(
                delta = delta,
                input = touchInputController,
                cameraController = cameraController,
                combatSystem = combatSystem,
                onToggleLockOn = { toggleLockOnTarget() }
            )
            predictionHakiController.update(delta)
            if (combatSystem.isAttacking) {
                buildingRaidManager.recordNoiseEvent(delta * 10f, playerCharacter.position)
            }
            val enemyDelta = if (combatSystem.hitStopTimer > 0f) delta * 0.15f else delta
            entityManager.update(
                delta = enemyDelta,
                player = playerCharacter,
                worldManager = worldManager,
                audioManager = audioManager,
                combatSystem = combatSystem,
                onPlayerHit = { dmg ->
                    combatSystem.recordDamagePopup(
                        pos = Vector3(playerCharacter.position.x, playerCharacter.position.y + 1.95f, playerCharacter.position.z),
                        damage = dmg,
                        isHeavy = true
                    )
                }
            )
            cameraController.update(delta, playerCharacter)

            // Update Freeform Building Ghost Preview & Conditional Monster Raid System
            if (buildingManager.isBuildModeActive) {
                buildingManager.updateGhostPreview(playerCharacter, cameraController, inventory)
            }
            buildingRaidManager.update(delta, playerCharacter, combatSystem) { raidMsg ->
                survivalOverlay.addNotification(raidMsg, 1)
            }

            // Play unlock tone if target died or became invalid automatically this frame
            if (wasLockedOn && !cameraController.isLockedOn) {
                audioManager.playLockOnToggle(false)
            }

            // Update survival systems
            survivalAttributes.update(delta, playerCharacter)
            if (touchInputController.isSprintActive) {
                survivalAttributes.consumeStamina(14f * delta)
            }
            harvestableResourceManager.update(delta)
            survivalOverlay.update(delta)
            groundPickupManager.update(delta, playerCharacter, inventory, audioManager, combatSystem) { id, count ->
                survivalOverlay.addNotification(id, count)
            }

            // Check Corrupted Mana Guardian Knight defeat for Monster Essence & Boss Materials
            val enemy = entityManager.primaryEnemy()
            if (enemy != null) {
                val isDead = enemy.aiState == com.aethelgard.survival.core.entities.EnemyAiState.DEAD || enemy.health <= 0f
                if (isDead && !knightWasDead) {
                    knightWasDead = true
                    harvestableResourceManager.handleMonsterDefeat(
                        isBoss = true,
                        position = enemy.entity.position,
                        inventory = inventory
                    ) { id, count ->
                        survivalOverlay.addNotification(id, count)
                    }
                } else if (!isDead && knightWasDead) {
                    knightWasDead = false
                }
            }

            // Periodic auto-save every 10 seconds
            autoSaveTimer += delta
            if (autoSaveTimer >= 10f) {
                autoSaveTimer = 0f
                survivalSaveManager.saveAll(inventory, chestManager, survivalAttributes, harvestableResourceManager, buildingManager, worldManager.dayNightCycle)
            }

            // Render 3D World, Structures (Crafting Table, Chest, Nature Nodes), Player Buildings & Ghost Preview, Guardian Enemy, Player, and 3D Combat Projectiles/VFX
            val isVanishedInFlashStep = playerCharacter.animationState == com.aethelgard.survival.core.player.AnimationState.SWORD_FLASH_STEP &&
                playerCharacter.activeAttackProgress in 0.22f..0.72f

            modelBatch.begin(camera)
            worldManager.render(modelBatch, camera)
            harvestableResourceManager.renderStructures(modelBatch, worldManager.environment)
            buildingManager.render(modelBatch, worldManager.environment)
            groundPickupManager.render(modelBatch, worldManager.environment)
            entityManager.render(modelBatch, worldManager.environment)
            if (!isVanishedInFlashStep) {
                modelBatch.render(playerCharacter.modelInstance, worldManager.environment)
            }
            // First-person held item / tool viewmodel rendering
            if (cameraController.cameraMode == com.aethelgard.survival.core.camera.CameraMode.FIRST_PERSON) {
                val activeHeldId = playerCharacter.equippedToolId ?: playerCharacter.equippedItemId
                val fpInstance = if (activeHeldId != null) firstPersonToolInstances[activeHeldId] else null
                if (fpInstance != null && (!playerCharacter.isSwordEquipped || playerCharacter.isUsingTool || playerCharacter.equippedItemId != null)) {
                    updateFirstPersonToolTransform(fpInstance)
                    modelBatch.render(fpInstance, worldManager.environment)
                }
            }
            combatSystem.render(modelBatch, worldManager.environment, cameraController)
            modelBatch.end()

            // Find nearest harvestable resource node or placed building piece for contextual interact / Builder's Hammer
            val nearestNode = harvestableResourceManager.findNearestNode(playerCharacter.position, 3.5f)
            val nearestPiece = buildingManager.findNearestPiece(playerCharacter.position, 4.2f)
            val isHammerEquipped = (playerCharacter.equippedToolId == ResourceCatalog.BUILDERS_HAMMER.id)

            // Render 2D Combat HUD, Survival Meters, Hotbar, Build Mode Controls, Builder's Hammer Bar, and Interactive Modals
            hudRenderer.render(
                camera = camera,
                player = playerCharacter,
                cameraMode = cameraController.cameraMode,
                input = touchInputController,
                combatSystem = combatSystem,
                primaryEnemy = entityManager.primaryEnemy(),
                lockedTarget = if (cameraController.isLockedOn) cameraController.lockedTarget else null,
                isAudioEnabled = audioManager.isAudioEnabled,
                survivalOverlay = survivalOverlay,
                survivalAttributes = survivalAttributes,
                inventory = inventory,
                chestManager = chestManager,
                craftingManager = craftingManager,
                nearestNode = nearestNode,
                buildingManager = buildingManager,
                nearestBuildingPiece = nearestPiece,
                isHammerEquipped = isHammerEquipped,
                dayNightCycle = worldManager.dayNightCycle,
                predictionHakiController = predictionHakiController
            )

            if (!hasLoggedFirstFrame) {
                hasLoggedFirstFrame = true
                Gdx.app?.log("AethelgardDiag", "AethelgardGame first frame rendered successfully!")
            }
        } catch (t: Throwable) {
            fatalFailureOccurred = true
            Gdx.app?.error("AethelgardCrash", "FATAL ERROR during AethelgardGame.render()", t)
            onFatalError?.invoke("AethelgardGame.render()", t)
            throw t
        }
    }

    private fun updateFirstPersonToolTransform(fpInstance: ModelInstance) {
        fpForward.set(camera.direction).nor()
        fpRight.set(fpForward).crs(camera.up).nor()
        fpUp.set(fpRight).crs(fpForward).nor()

        val p = if (playerCharacter.isUsingTool) playerCharacter.activeAttackProgress.coerceIn(0f, 1f) else 0f
        val swingArc = kotlin.math.sin((p * Math.PI).toDouble()).toFloat()

        // Base right-hand viewmodel offset in front of 1P camera
        var rightOffset = 0.28f
        var upOffset = -0.24f
        var forwardOffset = 0.48f
        var pitchOffset = -28f
        var yawOffset = -16f
        var rollOffset = 0f

        when (playerCharacter.activeToolAnimationState) {
            com.aethelgard.survival.core.player.AnimationState.TOOL_AXE_CHOP -> {
                rightOffset -= swingArc * 0.18f
                upOffset -= swingArc * 0.08f
                forwardOffset += swingArc * 0.14f
                pitchOffset += swingArc * 52f
                yawOffset += swingArc * 44f
                rollOffset = -25f * swingArc
            }
            com.aethelgard.survival.core.player.AnimationState.TOOL_PICKAXE_MINE,
            com.aethelgard.survival.core.player.AnimationState.TOOL_HAMMER_BUILD -> {
                upOffset += if (p < 0.35f) (p / 0.35f) * 0.14f else -swingArc * 0.16f
                forwardOffset += swingArc * 0.16f
                pitchOffset += if (p < 0.35f) -35f * (p / 0.35f) else 68f * swingArc
            }
            com.aethelgard.survival.core.player.AnimationState.TOOL_SHOVEL_DIG -> {
                upOffset += if (p < 0.45f) -0.12f * swingArc else 0.14f * swingArc
                forwardOffset += swingArc * 0.18f
                pitchOffset += if (p < 0.45f) 38f * swingArc else -42f * swingArc
            }
            com.aethelgard.survival.core.player.AnimationState.TOOL_SICKLE_HARVEST -> {
                rightOffset += (0.5f - p) * 0.28f * swingArc
                forwardOffset += swingArc * 0.14f
                yawOffset += (p - 0.5f) * 85f * swingArc
                rollOffset = -55f
            }
            com.aethelgard.survival.core.player.AnimationState.TOOL_KNIFE_SLICE -> {
                rightOffset -= swingArc * 0.14f
                forwardOffset += swingArc * 0.18f
                pitchOffset += swingArc * 34f
                yawOffset += swingArc * 38f
            }
            else -> {}
        }

        fpHandPos.set(camera.position)
            .mulAdd(fpForward, forwardOffset)
            .mulAdd(fpRight, rightOffset)
            .mulAdd(fpUp, upOffset)

        fpInstance.transform.idt()
            .translate(fpHandPos.x, fpHandPos.y, fpHandPos.z)
            .rotate(0f, 1f, 0f, cameraController.yawDegrees + yawOffset)
            .rotate(1f, 0f, 0f, cameraController.pitchDegrees + pitchOffset)
            .rotate(0f, 0f, 1f, rollOffset)
            .scale(0.85f, 0.85f, 0.85f)
    }

    override fun pause() {
        if (::survivalSaveManager.isInitialized && ::inventory.isInitialized) {
            try {
                val bm = if (::buildingManager.isInitialized) buildingManager else null
                survivalSaveManager.saveAll(inventory, chestManager, survivalAttributes, harvestableResourceManager, bm)
            } catch (_: Throwable) {
            }
        }
    }

    override fun dispose() {
        if (::survivalSaveManager.isInitialized && ::inventory.isInitialized) {
            try {
                val bm = if (::buildingManager.isInitialized) buildingManager else null
                survivalSaveManager.saveAll(inventory, chestManager, survivalAttributes, harvestableResourceManager, bm)
            } catch (_: Throwable) {
            }
        }
        if (::buildingModelFactory.isInitialized) runCatching { buildingModelFactory.dispose() }
        if (::survivalOverlay.isInitialized) runCatching { survivalOverlay.dispose() }
        if (::modelBatch.isInitialized) runCatching { modelBatch.dispose() }
        if (::hudRenderer.isInitialized) runCatching { hudRenderer.dispose() }
        if (::audioManager.isInitialized) runCatching { audioManager.dispose() }
        if (::assetManager.isInitialized) runCatching { assetManager.dispose() }
        if (::entityManager.isInitialized) entityManager.clear()
    }

    fun syncHeldHotbarItem() {
        if (!playerCharacter.isUsingTool) {
            val activeSlot = inventory.activeHotbarItem
            playerCharacter.equippedToolId = if (activeSlot != null && activeSlot.isTool && !activeSlot.isBroken) {
                activeSlot.itemId
            } else {
                null
            }
            playerCharacter.equippedItemId = if (activeSlot != null &&
                !activeSlot.isBroken &&
                ResourceCatalog.isHoldableInHand(activeSlot.itemId)
            ) {
                activeSlot.itemId
            } else {
                null
            }
        }
    }
}
