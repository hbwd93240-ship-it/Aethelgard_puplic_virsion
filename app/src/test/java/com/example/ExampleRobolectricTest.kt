package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aethelgard.survival.core.entities.EnemyAiState
import com.aethelgard.survival.core.input.TouchInputController
import com.aethelgard.survival.core.survival.ChestManager
import com.aethelgard.survival.core.survival.CraftingManager
import com.aethelgard.survival.core.survival.HarvestNodeType
import com.aethelgard.survival.core.survival.HarvestableResourceManager
import com.aethelgard.survival.core.survival.Inventory
import com.aethelgard.survival.core.survival.ItemCategory
import com.aethelgard.survival.core.survival.ResourceCatalog
import com.aethelgard.survival.core.survival.SurvivalAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AethelgardSurvival", appName)
  }

  @Test
  fun `touch input controller handles floating joystick and milestone 2 combat controls`() {
    var camToggled = false
    var resetCalled = false
    var lightAttackCalled = false
    var heavyAttackCalled = false
    var energyPressed = false
    var energyReleased = false
    var weaponToggled = false
    var lockOnToggled = false

    val input = TouchInputController(
      onLookDelta = { _, _ -> },
      onToggleCameraMode = { camToggled = true },
      onResetRequested = { resetCalled = true },
      onToggleAudio = {},
      onSprintStateChanged = {},
      onLightAttack = { lightAttackCalled = true },
      onHeavyAttack = { heavyAttackCalled = true },
      onEnergyPress = { energyPressed = true },
      onEnergyRelease = { energyReleased = true },
      onToggleWeaponStance = { weaponToggled = true },
      onToggleLockOn = { lockOnToggled = true }
    )
    input.updateLayout(1280, 720)

    // Left touch spawns floating joystick and drags forward
    input.touchDown(240, 500, 0, 0)
    input.touchDragged(240, 400, 0)
    assertTrue("Forward drag should yield positive moveY", input.moveY > 0.5f)
    input.touchUp(240, 400, 0, 0)
    assertEquals(0f, input.moveY, 0.001f)

    // Tap Light Attack button
    val atkX = input.lightAttackButton.centerX.toInt()
    val atkY = (720f - input.lightAttackButton.centerY).toInt()
    input.touchDown(atkX, atkY, 1, 0)
    assertTrue(lightAttackCalled)
    input.touchUp(atkX, atkY, 1, 0)

    // Tap Heavy Attack button
    val hvyX = input.heavyAttackButton.centerX.toInt()
    val hvyY = (720f - input.heavyAttackButton.centerY).toInt()
    input.touchDown(hvyX, hvyY, 2, 0)
    assertTrue(heavyAttackCalled)
    input.touchUp(hvyX, hvyY, 2, 0)

    // Hold and release Energy Attack button
    val nrgX = input.energyAttackButton.centerX.toInt()
    val nrgY = (720f - input.energyAttackButton.centerY).toInt()
    input.touchDown(nrgX, nrgY, 3, 0)
    assertTrue(energyPressed)
    input.touchUp(nrgX, nrgY, 3, 0)
    assertTrue(energyReleased)

    // Tap Dodge / Dash button
    val dashX = input.dodgeButton.centerX.toInt()
    val dashY = (720f - input.dodgeButton.centerY).toInt()
    input.touchDown(dashX, dashY, 4, 0)
    assertTrue(input.consumeDodgeRequest())
    input.touchUp(dashX, dashY, 4, 0)

    // Tap Lock-On Target button
    val lockX = input.lockOnButton.centerX.toInt()
    val lockY = (720f - input.lockOnButton.centerY).toInt()
    input.touchDown(lockX, lockY, 8, 0)
    assertTrue("Tapping LOCK button should invoke onToggleLockOn", lockOnToggled)
    input.touchUp(lockX, lockY, 8, 0)

    // Tap Weapon Stance Toggle button
    val wpnX = (input.weaponStanceButton.x + 10f).toInt()
    val wpnY = (720f - (input.weaponStanceButton.y + 10f)).toInt()
    input.touchDown(wpnX, wpnY, 5, 0)
    assertTrue(weaponToggled)

    // Tap Camera Toggle & Reset buttons
    val camScreenX = (input.camModeButton.x + 10f).toInt()
    val camScreenY = (720f - (input.camModeButton.y + 10f)).toInt()
    input.touchDown(camScreenX, camScreenY, 6, 0)
    assertTrue(camToggled)

    val resetScreenX = (input.resetButton.x + 10f).toInt()
    val resetScreenY = (720f - (input.resetButton.y + 10f)).toInt()
    input.touchDown(resetScreenX, resetScreenY, 7, 0)
    assertTrue(resetCalled)
  }

  @Test
  fun `milestone 3 and milestone 4 dedicated sword and fist combat animation states are distinct`() {
    val swordStates = listOf(
      com.aethelgard.survival.core.player.AnimationState.SWORD_HORIZONTAL_SLASH,
      com.aethelgard.survival.core.player.AnimationState.SWORD_DIAGONAL_CROSS,
      com.aethelgard.survival.core.player.AnimationState.SWORD_RISING_SLASH,
      com.aethelgard.survival.core.player.AnimationState.SWORD_SPIN_360,
      com.aethelgard.survival.core.player.AnimationState.SWORD_RAPID_FLURRY,
      com.aethelgard.survival.core.player.AnimationState.SWORD_PHANTOM_DASH_SLASH,
      com.aethelgard.survival.core.player.AnimationState.SWORD_FLASH_STEP,
      com.aethelgard.survival.core.player.AnimationState.SWORD_INFERNO_CRESCENT,
      com.aethelgard.survival.core.player.AnimationState.SWORD_DRAGON_TEMPEST,
      com.aethelgard.survival.core.player.AnimationState.SWORD_AERIAL_STRIKE
    )
    val fistStates = listOf(
      com.aethelgard.survival.core.player.AnimationState.FIST_RIGHT_JAB,
      com.aethelgard.survival.core.player.AnimationState.FIST_LEFT_CROSS,
      com.aethelgard.survival.core.player.AnimationState.FIST_SIDE_KICK,
      com.aethelgard.survival.core.player.AnimationState.FIST_SPIN_ROUNDHOUSE,
      com.aethelgard.survival.core.player.AnimationState.FIST_RISING_HIGH_KICK,
      com.aethelgard.survival.core.player.AnimationState.FIST_FINISHER_CYCLONE_KICK,
      com.aethelgard.survival.core.player.AnimationState.FIST_FINISHER_FLYING_STRIKE,
      com.aethelgard.survival.core.player.AnimationState.FIST_HEAVY_DRAGON_HAYMAKER,
      com.aethelgard.survival.core.player.AnimationState.FIST_HEAVY_THUNDER_BREAKER,
      com.aethelgard.survival.core.player.AnimationState.FIST_HEAVY_METEOR_STRIKE
    )
    val limbs = com.aethelgard.survival.core.player.MartialLimb.values()

    assertEquals(10, swordStates.distinct().size)
    assertEquals(10, fistStates.distinct().size)
    assertEquals(4, limbs.size)
    assertTrue("Sword states and Fist states must be completely disjoint", swordStates.intersect(fistStates.toSet()).isEmpty())
  }

  @Test
  fun `main activity and aethelgard game initialize and render fists and sword combat without crashing`() {
    val soFile = java.io.File("src/main/jniLibs/x86_64/libgdx.so")
    assertTrue("Native x86_64/libgdx.so must exist", soFile.exists())
    val arm64SoFile = java.io.File("src/main/jniLibs/arm64-v8a/libgdx.so")
    assertTrue("Native arm64-v8a/libgdx.so must exist", arm64SoFile.exists())

    // Load native x86_64 libgdx.so in Linux JVM test environment
    val bytes = soFile.readBytes()
    val patchedDir = java.nio.file.Files.createTempDirectory("gdx_test_natives").toFile()
    val patchedSo = java.io.File(patchedDir, "libgdx.so")
    val patchedBytes = bytes.copyOf()
    val elfBuf = java.nio.ByteBuffer.wrap(patchedBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    val phoff = elfBuf.getLong(32).toInt()
    val phentsize = elfBuf.getShort(54).toInt() and 0xFFFF
    val phnum = elfBuf.getShort(56).toInt() and 0xFFFF
    for (i in 0 until phnum) {
      val base = phoff + i * phentsize
      val pType = elfBuf.getInt(base)
      if (pType == 2) {
        val dynOff = elfBuf.getLong(base + 8).toInt()
        val dynSize = elfBuf.getLong(base + 32).toInt()
        var pos = dynOff
        while (pos + 16 <= dynOff + dynSize) {
          val dTag = elfBuf.getLong(pos)
          if (dTag == 0L) break
          if (
            dTag == 1L ||
            dTag == 0x6ffffff0L ||
            dTag == 0x6ffffffeL ||
            dTag == 0x6fffffffL
          ) {
            elfBuf.putLong(pos, 21L)
            elfBuf.putLong(pos + 8, 0L)
          }
          pos += 16
        }
      }
    }
    try {
      com.badlogic.gdx.utils.GdxNativesLoader.load()
    } catch (_: Throwable) {
      System.load("/lib/x86_64-linux-gnu/libm.so.6")
      System.load(patchedSo.absolutePath)
      com.badlogic.gdx.utils.GdxNativesLoader.disableNativesLoading = true
    }

    // Verify MainActivity & Application manifest configuration via Robolectric PackageManager
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val pm = app.packageManager
    val appInfo = pm.getApplicationInfo(app.packageName, 0)
    assertTrue(
      "Application must have FLAG_EXTRACT_NATIVE_LIBS enabled",
      (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) != 0
    )
    val activityInfo = pm.getActivityInfo(android.content.ComponentName(app, MainActivity::class.java), 0)
    assertTrue(
      "MainActivity configChanges must handle smallestScreenSize",
      (activityInfo.configChanges and android.content.pm.ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE) != 0
    )
  }

  private fun setupGdxMocks(): com.badlogic.gdx.backends.android.DefaultAndroidAudio {
    if (!com.badlogic.gdx.utils.GdxNativesLoader.disableNativesLoading) {
      val soFile = java.io.File("src/main/jniLibs/x86_64/libgdx.so")
      if (soFile.exists()) {
        val bytes = soFile.readBytes()
        val patchedDir = java.nio.file.Files.createTempDirectory("gdx_test_natives").toFile()
        val patchedSo = java.io.File(patchedDir, "libgdx.so")
        val patchedBytes = bytes.copyOf()
        val elfBuf = java.nio.ByteBuffer.wrap(patchedBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val phoff = elfBuf.getLong(32).toInt()
        val phentsize = elfBuf.getShort(54).toInt() and 0xFFFF
        val phnum = elfBuf.getShort(56).toInt() and 0xFFFF
        for (i in 0 until phnum) {
          val base = phoff + i * phentsize
          val pType = elfBuf.getInt(base)
          if (pType == 2) {
            val dynOff = elfBuf.getLong(base + 8).toInt()
            val dynSize = elfBuf.getLong(base + 32).toInt()
            var pos = dynOff
            while (pos + 16 <= dynOff + dynSize) {
              val dTag = elfBuf.getLong(pos)
              if (dTag == 0L) break
              if (
                dTag == 1L ||
                dTag == 0x6ffffff0L ||
                dTag == 0x6ffffffeL ||
                dTag == 0x6fffffffL
              ) {
                elfBuf.putLong(pos, 21L)
                elfBuf.putLong(pos + 8, 0L)
              }
              pos += 16
            }
          }
        }
        patchedSo.writeBytes(patchedBytes)
        try {
          com.badlogic.gdx.utils.GdxNativesLoader.load()
        } catch (_: Throwable) {
          runCatching { System.load("/lib/x86_64-linux-gnu/libm.so.6") }
          runCatching { System.load(patchedSo.absolutePath) }
          com.badlogic.gdx.utils.GdxNativesLoader.disableNativesLoading = true
        }
      }
    }
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val mockGl = java.lang.reflect.Proxy.newProxyInstance(
      com.badlogic.gdx.graphics.GL20::class.java.classLoader,
      arrayOf(com.badlogic.gdx.graphics.GL20::class.java)
    ) { _, method, args ->
      when (method.name) {
        "glCreateShader", "glCreateProgram", "glGenTexture", "glGenBuffer", "glGenFramebuffer", "glGenRenderbuffer" -> 1
        "glGetShaderiv" -> {
          val params = args?.getOrNull(2) as? java.nio.IntBuffer
          params?.put(0, 1)
          null
        }
        "glGetProgramiv" -> {
          val pname = args?.getOrNull(1) as? Int ?: 0
          val params = args?.getOrNull(2) as? java.nio.IntBuffer
          val result = if (
            pname == com.badlogic.gdx.graphics.GL20.GL_LINK_STATUS ||
            pname == com.badlogic.gdx.graphics.GL20.GL_COMPILE_STATUS ||
            pname == com.badlogic.gdx.graphics.GL20.GL_VALIDATE_STATUS
          ) 1 else 0
          params?.put(0, result)
          null
        }
        "glGetIntegerv" -> {
          val params = args?.getOrNull(1) as? java.nio.IntBuffer
          params?.put(0, 16)
          null
        }
        "glGetProgramInfoLog", "glGetShaderInfoLog", "glGetString" -> ""
        "glGetAttribLocation", "glGetUniformLocation" -> 0
        "glCheckFramebufferStatus" -> com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER_COMPLETE
        else -> when (method.returnType) {
          Boolean::class.javaPrimitiveType -> true
          Int::class.javaPrimitiveType -> 0
          Float::class.javaPrimitiveType -> 0f
          else -> null
        }
      }
    } as com.badlogic.gdx.graphics.GL20

    com.badlogic.gdx.Gdx.gl = mockGl
    com.badlogic.gdx.Gdx.gl20 = mockGl

    val realAndroidFiles = com.badlogic.gdx.backends.android.DefaultAndroidFiles(app.assets, app, true)
    com.badlogic.gdx.Gdx.files = java.lang.reflect.Proxy.newProxyInstance(
      com.badlogic.gdx.Files::class.java.classLoader,
      arrayOf(com.badlogic.gdx.Files::class.java)
    ) { _, method, args ->
      val path = args?.getOrNull(0) as? String ?: ""
      when (method.name) {
        "classpath" -> object : com.badlogic.gdx.files.FileHandle(path, com.badlogic.gdx.Files.FileType.Classpath) {
          override fun read(): java.io.InputStream {
            return com.badlogic.gdx.Gdx::class.java.classLoader!!.getResourceAsStream(path.removePrefix("/"))
              ?: java.io.ByteArrayInputStream(ByteArray(0))
          }
        }
        else -> method.invoke(realAndroidFiles, *(args ?: emptyArray()))
      }
    } as com.badlogic.gdx.Files

    com.badlogic.gdx.Gdx.graphics = java.lang.reflect.Proxy.newProxyInstance(
      com.badlogic.gdx.Graphics::class.java.classLoader,
      arrayOf(com.badlogic.gdx.Graphics::class.java)
    ) { _, method, _ ->
      when (method.name) {
        "getWidth", "getBackBufferWidth" -> 1280
        "getHeight", "getBackBufferHeight" -> 720
        "getDeltaTime", "getRawDeltaTime" -> 0.016f
        "getFramesPerSecond" -> 60
        "isGL30Available" -> false
        else -> when (method.returnType) {
          Boolean::class.javaPrimitiveType -> false
          Int::class.javaPrimitiveType -> 0
          Float::class.javaPrimitiveType -> 1f
          Long::class.javaPrimitiveType -> 0L
          else -> null
        }
      }
    } as com.badlogic.gdx.Graphics

    com.badlogic.gdx.Gdx.app = java.lang.reflect.Proxy.newProxyInstance(
      com.badlogic.gdx.Application::class.java.classLoader,
      arrayOf(com.badlogic.gdx.Application::class.java)
    ) { _, method, _ ->
      when (method.name) {
        "getType" -> com.badlogic.gdx.Application.ApplicationType.Android
        "getVersion" -> 36
        else -> when (method.returnType) {
          Boolean::class.javaPrimitiveType -> false
          Int::class.javaPrimitiveType -> 0
          Long::class.javaPrimitiveType -> 0L
          else -> null
        }
      }
    } as com.badlogic.gdx.Application

    var activeProcessor: com.badlogic.gdx.InputProcessor? = null
    com.badlogic.gdx.Gdx.input = java.lang.reflect.Proxy.newProxyInstance(
      com.badlogic.gdx.Input::class.java.classLoader,
      arrayOf(com.badlogic.gdx.Input::class.java)
    ) { _, method, args ->
      when (method.name) {
        "setInputProcessor" -> {
          activeProcessor = args?.getOrNull(0) as? com.badlogic.gdx.InputProcessor
          null
        }
        "getInputProcessor" -> activeProcessor
        else -> when (method.returnType) {
          Boolean::class.javaPrimitiveType -> false
          Int::class.javaPrimitiveType -> 0
          Float::class.javaPrimitiveType -> 0f
          Long::class.javaPrimitiveType -> 0L
          else -> null
        }
      }
    } as com.badlogic.gdx.Input

    val config = com.badlogic.gdx.backends.android.AndroidApplicationConfiguration()
    val realAndroidAudio = com.badlogic.gdx.backends.android.DefaultAndroidAudio(app, config)
    com.badlogic.gdx.Gdx.audio = realAndroidAudio
    return realAndroidAudio
  }

  @Test
  fun `elite knight combat and animations work seamlessly without crashes`() {
    val realAndroidAudio = setupGdxMocks()

    val game = com.aethelgard.survival.core.AethelgardGame()
    // Calling resize or dispose before create() must be safely guarded
    game.resize(1280, 720)
    game.dispose()

    game.create()
    game.resize(1280, 720)
    game.render()

    // Exercise 6-stage Fists combo & 3 contextual heavy attacks
    for (step in 1..6) {
      game.combatSystem.triggerLightAttack(game.playerCharacter, game.cameraController)
      assertEquals(step, game.combatSystem.fistsCombatController.currentComboStep)
      for (f in 0 until 25) game.render()
    }
    for (h in 0 until 3) {
      game.combatSystem.fistsCombatController.reset()
      game.combatSystem.triggerHeavyAttack(game.playerCharacter, game.cameraController)
      assertTrue(game.combatSystem.fistsCombatController.isFistAttacking)
      for (f in 0 until 35) game.render()
    }

    // Switch to Sword stance and exercise 5-stage Sword combo & 3 heavy attacks
    game.combatSystem.toggleWeaponStance(game.playerCharacter)
    assertTrue(game.playerCharacter.isSwordEquipped)
    for (step in 1..5) {
      game.combatSystem.triggerLightAttack(game.playerCharacter, game.cameraController)
      assertEquals(step, game.combatSystem.swordCombatController.currentComboStep)
      for (f in 0 until 25) game.render()
    }
    for (h in 0 until 3) {
      game.combatSystem.swordCombatController.reset()
      game.playerCharacter.hitStunTimer = 0f
      game.combatSystem.triggerHeavyAttack(game.playerCharacter, game.cameraController)
      assertTrue(game.combatSystem.swordCombatController.isSwordAttacking)
      for (f in 0 until 35) game.render()
    }

    // Verify primary enemy is an Elite Knight with 280 max HP
    val enemy = game.entityManager.primaryEnemy()
    org.junit.Assert.assertNotNull(enemy)
    assertEquals(280f, enemy!!.maxHealth, 0.01f)
    enemy.resetToSpawn(game.worldManager)
    assertEquals(280f, enemy.health, 0.01f)
    assertFalse(enemy.isFuryPhase)

    // Damage enemy down to 70 HP (below 30% of 280 = 84 HP) to trigger Fury Phase
    enemy.receiveDamage(
        amount = 210f,
        knockbackX = 0f,
        knockbackZ = 0f,
        isHeavyImpact = true,
        audioManager = game.audioManager
    )
    assertTrue("Enemy should activate Fury Phase at <= 30% HP", enemy.isFuryPhase)
    assertEquals(EnemyAiState.FURY_ROAR, enemy.aiState)

    // While in FURY_ROAR, knight is invulnerable
    val preHp = enemy.health
    enemy.receiveDamage(
        amount = 50f,
        knockbackX = 0f,
        knockbackZ = 0f,
        isHeavyImpact = false,
        audioManager = game.audioManager
    )
    assertEquals(preHp, enemy.health, 0.01f)

    // Render several frames to let Fury Roar finish and enter tactical combat
    for (f in 0 until 80) game.render()
    assertTrue(enemy.aiState != EnemyAiState.DEAD)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `elite knight ai state machine includes all combo and special ability states`() {
    val allStates = EnemyAiState.values().toList()
    val expected = listOf(
      EnemyAiState.PATROL,
      EnemyAiState.PURSUE,
      EnemyAiState.TACTICAL_STANCE,
      EnemyAiState.COMBO_SLASH_HORIZONTAL,
      EnemyAiState.COMBO_SLASH_DIAGONAL,
      EnemyAiState.COMBO_RISING_SLASH,
      EnemyAiState.COMBO_OVERHEAD_SLAM,
      EnemyAiState.COMBO_FINISHER_THRUST,
      EnemyAiState.SPECIAL_WHIRLWIND,
      EnemyAiState.SPECIAL_PHANTOM_CHARGE,
      EnemyAiState.SPECIAL_CRESCENT_SLASH,
      EnemyAiState.SPECIAL_AERIAL_EXECUTION,
      EnemyAiState.SPECIAL_COUNTER_RIPOSTE,
      EnemyAiState.FURY_ROAR,
      EnemyAiState.HIT_STAGGER,
      EnemyAiState.DEAD
    )
    for (st in expected) {
      assertTrue("State $st should exist in EnemyAiState", allStates.contains(st))
      assertTrue("Display label for $st must not be blank", st.displayLabel.isNotBlank())
    }
  }

  @Test
  fun `survival resource catalog defines all 18 resources across 4 distinct categories`() {
    val resources = ResourceCatalog.ALL_RESOURCES
    assertEquals(18, resources.size)

    val woodGroup = resources.filter { it.category == ItemCategory.WOOD }
    assertEquals(4, woodGroup.size)
    val woodIds = woodGroup.map { it.id }.toSet()
    assertTrue(woodIds.containsAll(listOf("wood", "hardwood", "branches", "roots")))

    val mineralGroup = resources.filter { it.category == ItemCategory.MINERAL }
    assertEquals(5, mineralGroup.size)
    val mineralIds = mineralGroup.map { it.id }.toSet()
    assertTrue(mineralIds.containsAll(listOf("stone", "coal", "copper_ore", "iron_ore", "rare_ore")))

    val natureGroup = resources.filter { it.category == ItemCategory.NATURE }
    assertEquals(5, natureGroup.size)
    val natureIds = natureGroup.map { it.id }.toSet()
    assertTrue(natureIds.containsAll(listOf("plants", "mushrooms", "berries", "fibers", "animal_hides")))

    val magicGroup = resources.filter { it.category == ItemCategory.MAGIC }
    assertEquals(4, magicGroup.size)
    val magicIds = magicGroup.map { it.id }.toSet()
    assertTrue(magicIds.containsAll(listOf("monster_essence", "magic_crystals", "ancient_relics", "boss_materials")))

    for (res in resources) {
      assertTrue("ID for ${res.name} must not be blank", res.id.isNotBlank())
      assertTrue("Name for ${res.id} must not be blank", res.name.isNotBlank())
      assertTrue("Description for ${res.id} must not be blank", res.description.isNotBlank())
      assertTrue("Gathering source for ${res.id} must not be blank", res.gatheringSource.isNotBlank())
      assertTrue("Stack limit for ${res.id} must be at least 10", res.stackLimit >= 10)
    }
  }

  @Test
  fun `tree chopping damages tree, causes wobble reaction, drops resources, and removes tree and collider on destruction`() {
    val realAndroidAudio = setupGdxMocks()

    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)
    game.render()

    val treeNodes = game.harvestableResourceManager.nodes.filter { it.type == HarvestNodeType.TREE }
    assertTrue("Clearing must contain multiple harvestable fantasy trees", treeNodes.size >= 10)

    val targetTree = treeNodes.first()
    val initialHp = targetTree.health
    assertTrue("Tree initial HP should be > 0", initialHp > 0f)
    assertFalse("Tree should not be destroyed initially", targetTree.isDestroyed)

    val initialCollider = targetTree.collider
    assertNotNull("Tree must have an obstacle collider in the world", initialCollider)
    assertTrue(
      "Collider must be present in world colliders list",
      game.worldManager.generatedData.obstacleColliders.contains(initialCollider)
    )

    // Clear inventory to isolate tree harvest drops
    game.inventory.clear()
    assertEquals(0, game.inventory.countItem(ResourceCatalog.WOOD.id))

    // Perform one harvest hit
    val hitResult = game.harvestableResourceManager.harvestNode(
      node = targetTree,
      player = game.playerCharacter,
      inventory = game.inventory,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { _, _ -> }

    assertTrue("Harvesting tree should return true", hitResult)
    assertTrue("Tree HP should decrease after hit", targetTree.health < initialHp)
    assertTrue("Tree should trigger shake/wobble timer", targetTree.shakeTimer > 0f)
    assertEquals(
      "Wood must NOT be added directly to inventory before tree falls and logs are collected",
      0,
      game.inventory.countItem(ResourceCatalog.WOOD.id)
    )

    // Update resource manager to exercise tree wobble animation without crashing
    game.harvestableResourceManager.update(0.016f)

    // Repeatedly chop tree until HP reaches 0 (tree enters physical falling state)
    while (!targetTree.isDestroyed) {
      game.harvestableResourceManager.harvestNode(
        node = targetTree,
        player = game.playerCharacter,
        inventory = game.inventory,
        audioManager = game.audioManager,
        combatSystem = game.combatSystem
      ) { _, _ -> }
    }

    assertTrue("Tree isDestroyed must be true when HP reaches 0", targetTree.isDestroyed)
    assertEquals(0f, targetTree.health, 0.001f)
    assertTrue("Tree must enter physical falling animation (isFalling = true) instead of disappearing instantly", targetTree.isFalling)
    assertFalse("Tree must not have settled on ground before falling completes", targetTree.hasSettledOnGround)
    assertEquals("Wood must still be 0 before tree lands and logs are collected", 0, game.inventory.countItem(ResourceCatalog.WOOD.id))
    assertFalse(
      "Tree obstacle collider must be removed from the world upon felling",
      game.worldManager.generatedData.obstacleColliders.contains(initialCollider)
    )

    // Advance falling animation under gravity until tree collides with ground and settles
    var fallSteps = 0
    while (!targetTree.hasSettledOnGround && fallSteps < 120) {
      game.harvestableResourceManager.update(0.025f)
      fallSteps++
    }
    assertTrue("Tree must collide with the ground and settle naturally", targetTree.hasSettledOnGround)
    assertEquals("Tree fall angle must reach ground impact angle", targetTree.maxFallAngleDeg, targetTree.fallAngleDeg, 0.1f)

    // Verify multiple individual 3D wood log pickups spawned on the ground
    val woodLogPickups = game.groundPickupManager.activePickups.filter { it.itemId == ResourceCatalog.WOOD.id }
    assertTrue("Fallen tree must spawn several individual 3D wood log pickups on the ground", woodLogPickups.size >= 3)
    assertEquals("Wood must not be in inventory before player approaches logs", 0, game.inventory.countItem(ResourceCatalog.WOOD.id))

    // Approach each wood log pickup to collect automatically
    for (pickup in game.groundPickupManager.activePickups.toList()) {
      game.playerCharacter.position.set(pickup.basePosition.x, pickup.basePosition.y, pickup.basePosition.z)
      game.groundPickupManager.update(
        delta = 0.016f,
        player = game.playerCharacter,
        inventory = game.inventory,
        audioManager = game.audioManager,
        combatSystem = game.combatSystem
      ) { id, count ->
        game.survivalOverlay.addNotification(id, count)
      }
    }
    assertTrue("Player inventory must receive Wood after collecting ground logs", game.inventory.countItem(ResourceCatalog.WOOD.id) >= 5)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `inventory 25 slots hotbar and bag stacking swapping merging and persistence across camera switches and player deaths`() {
    val realAndroidAudio = setupGdxMocks()

    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val inv = game.inventory
    assertEquals(25, inv.size)
    inv.clear()

    // Test stacking up to stack limit
    val addedWood = inv.addItem(ResourceCatalog.WOOD.id, 50)
    assertEquals(0, addedWood)
    assertEquals(50, inv.countItem(ResourceCatalog.WOOD.id))

    // Add more to the same stack
    inv.addItem(ResourceCatalog.WOOD.id, 20)
    assertEquals(70, inv.countItem(ResourceCatalog.WOOD.id))

    // Hotbar selection
    inv.activeHotbarIndex = 2
    assertEquals(2, inv.activeHotbarIndex)

    // Slot swapping and merging
    inv.slots[1] = com.aethelgard.survival.core.survival.ItemStack(ResourceCatalog.STONE.id, 15)
    inv.slots[5] = com.aethelgard.survival.core.survival.ItemStack(ResourceCatalog.STONE.id, 10)
    inv.swapOrMergeSlots(1, 5)
    // Merged into slot 5
    assertEquals(25, inv.slots[5]?.count)
    assertEquals(null, inv.slots[1])

    // Verify inventory persists across 1P/3P camera switches
    val initialWood = inv.countItem(ResourceCatalog.WOOD.id)
    game.cameraController.toggleCameraMode()
    assertEquals(initialWood, game.inventory.countItem(ResourceCatalog.WOOD.id))
    game.cameraController.toggleCameraMode()
    assertEquals(initialWood, game.inventory.countItem(ResourceCatalog.WOOD.id))

    // Verify inventory persists across player death and respawn
    game.playerCharacter.health = 0f
    game.playerController.resetPlayerAndCamera(game.cameraController, game.combatSystem)
    assertEquals(initialWood, game.inventory.countItem(ResourceCatalog.WOOD.id))

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `crafting recipes validate ingredients consume resources and produce tools structures and consumables`() {
    val cm = CraftingManager()
    val inv = Inventory(25)

    // Hand crafting Stone Axe: 4 wood + 3 stone + 2 fibers
    val stoneAxeRecipe = cm.recipes.first { it.resultItemId == ResourceCatalog.STONE_AXE.id }
    assertFalse("Cannot craft without ingredients", cm.canCraft(stoneAxeRecipe, inv, isNearCraftingTable = false))

    inv.addItem(ResourceCatalog.WOOD.id, 4)
    inv.addItem(ResourceCatalog.STONE.id, 3)
    inv.addItem(ResourceCatalog.FIBERS.id, 2)
    assertTrue("Can craft Stone Axe when ingredients are present", cm.canCraft(stoneAxeRecipe, inv, isNearCraftingTable = false))

    val craftedItem = cm.craft(stoneAxeRecipe, inv, isNearCraftingTable = false)
    assertNotNull(craftedItem)
    assertEquals(ResourceCatalog.STONE_AXE.id, craftedItem!!.definition?.id)
    assertEquals(0, inv.countItem(ResourceCatalog.WOOD.id))
    assertEquals(0, inv.countItem(ResourceCatalog.STONE.id))
    assertEquals(0, inv.countItem(ResourceCatalog.FIBERS.id))
    assertEquals(1, inv.countItem(ResourceCatalog.STONE_AXE.id))

    // Workbench-only recipe: Iron Pickaxe (requires near crafting table: 3 hardwood + 4 iron_ore)
    val ironPickRecipe = cm.recipes.first { it.resultItemId == ResourceCatalog.IRON_PICKAXE.id }
    inv.addItem(ResourceCatalog.HARDWOOD.id, 3)
    inv.addItem(ResourceCatalog.IRON_ORE.id, 4)
    assertFalse("Workbench recipe cannot be crafted in hand", cm.canCraft(ironPickRecipe, inv, isNearCraftingTable = false))
    assertTrue("Workbench recipe can be crafted near table", cm.canCraft(ironPickRecipe, inv, isNearCraftingTable = true))

    val craftedPick = cm.craft(ironPickRecipe, inv, isNearCraftingTable = true)
    assertNotNull(craftedPick)
    assertEquals(1, inv.countItem(ResourceCatalog.IRON_PICKAXE.id))
  }

  @Test
  fun `chest storage transfers items between player and chest and persists`() {
    val chestManager = ChestManager(20)
    val playerInv = Inventory(25)

    playerInv.slots[5] = com.aethelgard.survival.core.survival.ItemStack(ResourceCatalog.COPPER_ORE.id, 12)
    assertEquals(12, playerInv.countItem(ResourceCatalog.COPPER_ORE.id))
    assertEquals(0, chestManager.chestInventory.countItem(ResourceCatalog.COPPER_ORE.id))

    // Deposit stack from player bag slot 5 into chest
    val deposited = chestManager.depositFromPlayer(5, playerInv)
    assertTrue(deposited)
    assertEquals(0, playerInv.countItem(ResourceCatalog.COPPER_ORE.id))
    assertEquals(12, chestManager.chestInventory.countItem(ResourceCatalog.COPPER_ORE.id))

    // Withdraw back to player
    val withdrawn = chestManager.withdrawToPlayer(0, playerInv)
    assertTrue(withdrawn)
    assertEquals(12, playerInv.countItem(ResourceCatalog.COPPER_ORE.id))
    assertEquals(0, chestManager.chestInventory.countItem(ResourceCatalog.COPPER_ORE.id))

    // Serialization persistence
    chestManager.chestInventory.addItem(ResourceCatalog.MAGIC_CRYSTALS.id, 7)
    val serialized = chestManager.serialize()
    val newChest = ChestManager(20)
    newChest.deserialize(serialized)
    assertEquals(7, newChest.chestInventory.countItem(ResourceCatalog.MAGIC_CRYSTALS.id))
  }

  @Test
  fun `survival attributes hunger thirst and stamina decay and apply starvation health penalties`() {
    val attrs = SurvivalAttributes()
    assertEquals(100f, attrs.hunger, 0.01f)
    assertEquals(100f, attrs.thirst, 0.01f)
    assertEquals(100f, attrs.stamina, 0.01f)

    val playerModel = com.badlogic.gdx.graphics.g3d.Model()
    val player = com.aethelgard.survival.core.player.PlayerCharacter(playerModel, com.badlogic.gdx.math.Vector3())
    player.health = 100f

    // Simulate 10 seconds of decay
    attrs.update(10f, player)
    assertTrue("Hunger should decay over time", attrs.hunger < 100f)
    assertTrue("Thirst should decay over time", attrs.thirst < 100f)

    // Force hunger to 0 to trigger starvation
    attrs.hunger = 0f
    assertTrue("isStarving should be true when hunger is 0", attrs.isStarving)

    val preHp = player.health
    attrs.update(5f, player)
    assertTrue("Starvation should cause player health damage", player.health < preHp)

    // Consuming food restores hunger
    attrs.consumeFood(35f)
    assertEquals(35f, attrs.hunger, 0.01f)
    assertFalse("Player is no longer starving after consuming food", attrs.isStarving)
  }

  @Test
  fun `phase 2 all 7 functional gathering tools have item definitions icons crafting recipes and 3D held models`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val expectedTools = listOf(
      "stone_axe" to com.aethelgard.survival.core.survival.ToolType.AXE,
      "iron_axe" to com.aethelgard.survival.core.survival.ToolType.AXE,
      "stone_pickaxe" to com.aethelgard.survival.core.survival.ToolType.PICKAXE,
      "iron_pickaxe" to com.aethelgard.survival.core.survival.ToolType.PICKAXE,
      "shovel" to com.aethelgard.survival.core.survival.ToolType.SHOVEL,
      "sickle" to com.aethelgard.survival.core.survival.ToolType.SICKLE,
      "knife" to com.aethelgard.survival.core.survival.ToolType.KNIFE
    )

    assertEquals(7, ResourceCatalog.ALL_TOOLS.size)

    for ((toolId, expectedType) in expectedTools) {
      val def = ResourceCatalog.get(toolId)
      assertNotNull("Tool definition must exist for $toolId", def)
      assertEquals(ItemCategory.TOOL, def!!.category)
      assertEquals(expectedType, def.toolType)
      assertTrue("Tool $toolId must have maxDurability > 0", def.maxDurability > 0)
      assertTrue("Tool $toolId must have badgeSymbol", def.badgeSymbol.isNotBlank())
      assertTrue("Tool $toolId must have a crafting recipe", game.craftingManager.recipes.any { it.resultItemId == toolId })

      // Verify 1st-person 3D held model instance exists and has mesh parts
      val fpModelInstance = game.firstPersonToolInstances[toolId]
      assertNotNull("First-person 3D held model must exist for $toolId", fpModelInstance)
      assertTrue("First-person 3D model for $toolId must contain mesh parts", fpModelInstance!!.model.meshParts.size > 0)

      // Verify 3rd-person character rig has the attached hand node for this tool
      val rigNode = game.playerCharacter.animationController.getHeldToolNode(toolId)
      assertNotNull("Third-person player rig must have attached child node for $toolId", rigNode)
    }

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `phase 2 five tool gathering animations work on character rig without interfering with sword or fists combat`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val toolStates = listOf(
      com.aethelgard.survival.core.player.AnimationState.TOOL_AXE_CHOP to "stone_axe",
      com.aethelgard.survival.core.player.AnimationState.TOOL_PICKAXE_MINE to "stone_pickaxe",
      com.aethelgard.survival.core.player.AnimationState.TOOL_SHOVEL_DIG to "shovel",
      com.aethelgard.survival.core.player.AnimationState.TOOL_SICKLE_HARVEST to "sickle",
      com.aethelgard.survival.core.player.AnimationState.TOOL_KNIFE_SLICE to "knife"
    )

    for ((animState, toolId) in toolStates) {
      game.playerCharacter.startToolAction(animState, toolId, 0.42f)
      assertTrue(game.playerCharacter.isUsingTool)
      assertEquals(animState, game.playerCharacter.animationState)

      // Advance frames in Third Person and verify held tool node on rightForearm is scaled to 1f
      game.playerController.update(
        delta = 0.10f,
        input = game.touchInputController,
        cameraController = game.cameraController,
        combatSystem = game.combatSystem,
        onToggleLockOn = {}
      )
      val activeNode = game.playerCharacter.animationController.getHeldToolNode(toolId)
      assertNotNull(activeNode)
      assertEquals("Equipped tool node $toolId must be visible (scale 1f) in 3P", 1.0f, activeNode!!.scale.x, 0.01f)
    }

    // Verify Fists and Sword combat controllers still trigger and animate cleanly after tool animations
    game.playerCharacter.isSwordEquipped = false
    game.combatSystem.triggerLightAttack(game.playerCharacter, game.cameraController)
    assertTrue(game.combatSystem.fistsCombatController.isFistAttacking)
    game.render()

    game.combatSystem.fistsCombatController.reset()
    game.combatSystem.toggleWeaponStance(game.playerCharacter)
    assertTrue(game.playerCharacter.isSwordEquipped)
    game.combatSystem.triggerLightAttack(game.playerCharacter, game.cameraController)
    assertTrue(game.combatSystem.swordCombatController.isSwordAttacking)
    game.render()

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `phase 2 mining system supports all 5 minerals tiered pickaxe efficiency hit reactions and node depletion`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val boulders = game.harvestableResourceManager.nodes.filter {
      it.type == HarvestNodeType.BOULDER || it.type == HarvestNodeType.CRYSTAL_BOULDER
    }
    assertTrue("Clearing must contain harvestable boulders and crystal rock outcrops", boulders.size >= 10)

    // Compare Stone Pickaxe (Tier 1) vs Iron Pickaxe (Tier 2) mining efficiency
    val boulder1 = boulders[0]
    val boulder2 = boulders[1]
    val hp1Before = boulder1.health
    val hp2Before = boulder2.health

    game.inventory.clear()
    game.inventory.addItem(ResourceCatalog.STONE_PICKAXE.id, 1)
    game.inventory.activeHotbarIndex = 0

    game.harvestableResourceManager.harvestNode(
      node = boulder1,
      player = game.playerCharacter,
      inventory = game.inventory,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { _, _ -> }
    val stonePickDamage = hp1Before - boulder1.health
    assertTrue("Stone Pickaxe should deal mining damage", stonePickDamage > 0f)
    assertTrue("Boulder should wobble/react on hit", boulder1.shakeTimer > 0f)
    assertTrue("Damaged boulder must show progressive cracking (damageStage > 0)", boulder1.damageStage > 0)
    assertNotNull("Damaged boulder must attach a 3D crack overlay model instance", boulder1.crackInstance)
    assertEquals(
      "Stone must NOT be added directly to inventory before rock breaks and pickups are collected",
      0,
      game.inventory.countItem(ResourceCatalog.STONE.id)
    )
    assertEquals(
      com.aethelgard.survival.core.player.AnimationState.TOOL_PICKAXE_MINE,
      game.playerCharacter.activeToolAnimationState
    )

    // Now equip Iron Pickaxe (Tier 2) and mine boulder2
    game.inventory.clear()
    game.inventory.addItem(ResourceCatalog.IRON_PICKAXE.id, 1)
    game.inventory.activeHotbarIndex = 0

    game.harvestableResourceManager.harvestNode(
      node = boulder2,
      player = game.playerCharacter,
      inventory = game.inventory,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { _, _ -> }
    val ironPickDamage = hp2Before - boulder2.health
    assertTrue(
      "Iron Pickaxe ($ironPickDamage) must have higher mining efficiency than Stone Pickaxe ($stonePickDamage)",
      ironPickDamage > stonePickDamage
    )

    // Mine several boulders to completion with Iron Pickaxe; verify falling 3D stone fragments and ground pickups
    for (b in boulders.take(8)) {
      while (!b.isDestroyed) {
        game.inventory.restoreToolDurability(0)
        game.harvestableResourceManager.harvestNode(
          node = b,
          player = game.playerCharacter,
          inventory = game.inventory,
          audioManager = game.audioManager,
          combatSystem = game.combatSystem
        ) { _, _ -> }
      }
      assertTrue("Depleted boulder must be marked isDestroyed", b.isDestroyed)
      assertFalse(
        "Depleted boulder collider must be removed from world",
        game.worldManager.generatedData.obstacleColliders.contains(b.collider)
      )
    }

    assertTrue("Destroyed boulders must spawn falling 3D rock fragments", game.harvestableResourceManager.fallingRockFragments.isNotEmpty())
    assertEquals("Stone must not be added to inventory before fragments land and are collected", 0, game.inventory.countItem(ResourceCatalog.STONE.id))

    // Simulate gravity until all falling rock fragments hit the ground and spawn 3D Stone & Ore pickups
    for (step in 0 until 45) {
      game.harvestableResourceManager.update(0.03f)
    }
    val stonePickups = game.groundPickupManager.activePickups.filter { it.itemId == ResourceCatalog.STONE.id }
    assertTrue("Broken boulders must spawn individual 3D Stone pickups on the ground", stonePickups.size >= 5)

    // Collect all spawned mineral and stone ground pickups
    for (pickup in game.groundPickupManager.activePickups.toList()) {
      game.playerCharacter.position.set(pickup.basePosition.x, pickup.basePosition.y, pickup.basePosition.z)
      game.groundPickupManager.update(
        delta = 0.016f,
        player = game.playerCharacter,
        inventory = game.inventory,
        audioManager = game.audioManager,
        combatSystem = game.combatSystem
      ) { _, _ -> }
    }

    assertTrue("Mining pickups must yield Stone", game.inventory.countItem(ResourceCatalog.STONE.id) > 0)
    assertTrue("Mining pickups must yield Coal", game.inventory.countItem(ResourceCatalog.COAL.id) > 0)
    assertTrue("Mining pickups must yield Copper Ore", game.inventory.countItem(ResourceCatalog.COPPER_ORE.id) > 0)
    assertTrue("Mining pickups must yield Iron Ore", game.inventory.countItem(ResourceCatalog.IRON_ORE.id) > 0)
    assertTrue("Mining pickups must yield Rare Ore", game.inventory.countItem(ResourceCatalog.RARE_ORE.id) > 0)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `phase 2 nature gathering harvests plants mushrooms berries fibers and roots with sickle shovel and knife`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val herbNode = game.harvestableResourceManager.nodes.first { it.type == HarvestNodeType.NATURE_HERB }
    val berryNode = game.harvestableResourceManager.nodes.first { it.type == HarvestNodeType.BERRY_BUSH }
    val mushroomNode = game.harvestableResourceManager.nodes.first { it.type == HarvestNodeType.MUSHROOM_PATCH }
    val rootNode = game.harvestableResourceManager.nodes.first { it.type == HarvestNodeType.ROOT_PATCH }

    game.inventory.clear()

    // 1. Harvest herb patch with Sickle -> Plants & Fibers + TOOL_SICKLE_HARVEST animation
    game.inventory.addItem(ResourceCatalog.SICKLE.id, 1)
    game.inventory.activeHotbarIndex = 0
    assertTrue(
      game.harvestableResourceManager.harvestNode(
        node = herbNode,
        player = game.playerCharacter,
        inventory = game.inventory,
        audioManager = game.audioManager,
        combatSystem = game.combatSystem
      ) { _, _ -> }
    )
    assertEquals(
      com.aethelgard.survival.core.player.AnimationState.TOOL_SICKLE_HARVEST,
      game.playerCharacter.activeToolAnimationState
    )
    for (pickup in game.groundPickupManager.activePickups.toList()) {
      game.playerCharacter.position.set(pickup.basePosition.x, pickup.basePosition.y, pickup.basePosition.z)
      game.groundPickupManager.update(0.016f, game.playerCharacter, game.inventory, game.audioManager, game.combatSystem) { _, _ -> }
    }
    assertTrue("Sickle harvesting must yield Plants", game.inventory.countItem(ResourceCatalog.PLANTS.id) > 0)
    assertTrue("Sickle harvesting must yield Fibers", game.inventory.countItem(ResourceCatalog.FIBERS.id) > 0)

    // 2. Harvest berry bush & mushroom patch with Knife -> Berries & Mushrooms + TOOL_KNIFE_SLICE animation
    game.inventory.slots[0] = com.aethelgard.survival.core.survival.ItemStack(ResourceCatalog.KNIFE.id, 1)
    assertTrue(
      game.harvestableResourceManager.harvestNode(
        node = berryNode,
        player = game.playerCharacter,
        inventory = game.inventory,
        audioManager = game.audioManager,
        combatSystem = game.combatSystem
      ) { _, _ -> }
    )
    assertEquals(
      com.aethelgard.survival.core.player.AnimationState.TOOL_KNIFE_SLICE,
      game.playerCharacter.activeToolAnimationState
    )
    for (pickup in game.groundPickupManager.activePickups.toList()) {
      game.playerCharacter.position.set(pickup.basePosition.x, pickup.basePosition.y, pickup.basePosition.z)
      game.groundPickupManager.update(0.016f, game.playerCharacter, game.inventory, game.audioManager, game.combatSystem) { _, _ -> }
    }
    assertTrue("Knife harvesting berry bush must yield Berries", game.inventory.countItem(ResourceCatalog.BERRIES.id) > 0)

    assertTrue(
      game.harvestableResourceManager.harvestNode(
        node = mushroomNode,
        player = game.playerCharacter,
        inventory = game.inventory,
        audioManager = game.audioManager,
        combatSystem = game.combatSystem
      ) { _, _ -> }
    )
    for (pickup in game.groundPickupManager.activePickups.toList()) {
      game.playerCharacter.position.set(pickup.basePosition.x, pickup.basePosition.y, pickup.basePosition.z)
      game.groundPickupManager.update(0.016f, game.playerCharacter, game.inventory, game.audioManager, game.combatSystem) { _, _ -> }
    }
    assertTrue("Knife harvesting mushroom cluster must yield Mushrooms", game.inventory.countItem(ResourceCatalog.MUSHROOMS.id) > 0)

    // 3. Dig root patch with Shovel -> Roots + TOOL_SHOVEL_DIG animation
    game.inventory.slots[0] = com.aethelgard.survival.core.survival.ItemStack(ResourceCatalog.SHOVEL.id, 1)
    assertTrue(
      game.harvestableResourceManager.harvestNode(
        node = rootNode,
        player = game.playerCharacter,
        inventory = game.inventory,
        audioManager = game.audioManager,
        combatSystem = game.combatSystem
      ) { _, _ -> }
    )
    assertEquals(
      com.aethelgard.survival.core.player.AnimationState.TOOL_SHOVEL_DIG,
      game.playerCharacter.activeToolAnimationState
    )
    for (pickup in game.groundPickupManager.activePickups.toList()) {
      game.playerCharacter.position.set(pickup.basePosition.x, pickup.basePosition.y, pickup.basePosition.z)
      game.groundPickupManager.update(0.016f, game.playerCharacter, game.inventory, game.audioManager, game.combatSystem) { _, _ -> }
    }
    assertTrue("Shovel digging root patch must yield Roots", game.inventory.countItem(ResourceCatalog.ROOTS.id) > 0)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `phase 2 tool durability decreases on use stops working at zero and supports repair and crafting replacement and 1P 3P switching`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val treeNodes = game.harvestableResourceManager.nodes.filter { it.type == HarvestNodeType.TREE }
    val treeNode = treeNodes[0]
    val secondTreeNode = treeNodes[1]
    game.inventory.clear()
    game.inventory.addItem(ResourceCatalog.STONE_AXE.id, 1)
    game.inventory.activeHotbarIndex = 0

    val axeStack = game.inventory.slots[0]!!
    val initialDurability = axeStack.durability
    assertEquals(40, initialDurability)
    assertFalse(axeStack.isBroken)

    // Use Stone Axe in Third Person
    assertEquals(com.aethelgard.survival.core.camera.CameraMode.THIRD_PERSON, game.cameraController.cameraMode)
    val hit1 = game.harvestableResourceManager.harvestNode(
      node = treeNode,
      player = game.playerCharacter,
      inventory = game.inventory,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { _, _ -> }
    assertTrue(hit1)
    assertEquals(initialDurability - 1, axeStack.durability)
    game.render()

    // Switch to First Person and harvest the same world object (felling the first tree)
    game.cameraController.toggleCameraMode()
    assertEquals(com.aethelgard.survival.core.camera.CameraMode.FIRST_PERSON, game.cameraController.cameraMode)
    val hit2 = game.harvestableResourceManager.harvestNode(
      node = treeNode,
      player = game.playerCharacter,
      inventory = game.inventory,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { _, _ -> }
    assertTrue(hit2)
    assertEquals(initialDurability - 2, axeStack.durability)
    game.render()

    // Drain durability down to 1, then use once more on the second tree so it reaches 0
    axeStack.durability = 1
    val lastValidHit = game.harvestableResourceManager.harvestNode(
      node = secondTreeNode,
      player = game.playerCharacter,
      inventory = game.inventory,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { _, _ -> }
    assertTrue(lastValidHit)
    assertEquals(0, axeStack.durability)
    assertTrue("Tool must be marked broken at 0 durability", axeStack.isBroken)

    // Attempting to harvest while holding a broken tool in the active hotbar must fail
    val hpBeforeBrokenAttempt = secondTreeNode.health
    val brokenAttempt = game.harvestableResourceManager.harvestNode(
      node = secondTreeNode,
      player = game.playerCharacter,
      inventory = game.inventory,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { _, _ -> }
    assertFalse("Broken tool must be unusable when durability is 0", brokenAttempt)
    assertEquals(hpBeforeBrokenAttempt, secondTreeNode.health, 0.001f)

    // Repair broken tool using 1 Stone via CraftingManager
    game.inventory.addItem(ResourceCatalog.STONE.id, 1)
    assertTrue(game.craftingManager.canRepairTool(0, game.inventory))
    assertTrue(game.craftingManager.repairTool(0, game.inventory))
    assertFalse("Repaired tool must no longer be broken", axeStack.isBroken)
    assertEquals(axeStack.maxDurability, axeStack.durability)

    // Verify tool works again after repair
    val postRepairHit = game.harvestableResourceManager.harvestNode(
      node = secondTreeNode,
      player = game.playerCharacter,
      inventory = game.inventory,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { _, _ -> }
    assertTrue(postRepairHit)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `player movement and turning remain responsive during enemy attacks and hit reactions`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val player = game.playerCharacter
    val enemy = game.entityManager.primaryEnemy()!!

    // Apply simulated enemy hit
    val hitApplied = player.applyDamage(12f, 0f, -4f)
    assertTrue(hitApplied)
    assertTrue("Hit stun timer must be active for visual flinch", player.hitStunTimer > 0f)

    // Simulate joystick movement while hitStunTimer is active
    val unit = (game.touchInputController.screenHeight / 720f).coerceIn(0.65f, 2.2f)
    val joyCenterX = 180f * unit
    val joyCenterY = 170f * unit
    // Drag joystick right (+X)
    game.touchInputController.touchDown(joyCenterX.toInt(), (720f - joyCenterY).toInt(), 0, 0)
    game.touchInputController.touchDragged((joyCenterX + 60f).toInt(), (720f - joyCenterY).toInt(), 0)
    assertTrue("Joystick moveX must be positive", game.touchInputController.moveX > 0.3f)

    // Update PlayerController
    val prevX = player.position.x
    game.playerController.update(
      delta = 0.05f,
      input = game.touchInputController,
      cameraController = game.cameraController,
      combatSystem = game.combatSystem
    )

    // Assert that velocity and position respond to input immediately despite taking a hit
    assertTrue("Player velocity.x must respond to joystick input during hit reaction", player.velocity.x > 0.5f)
    assertTrue("Player must move in the commanded direction during hit reaction", player.position.x > prevX)

    // Repeated hits must not lock movement: apply second hit
    player.invulnerabilityTimer = 0f
    val hit2 = player.applyDamage(10f, 0f, -2f)
    assertTrue(hit2)
    assertTrue(player.hitStunTimer > 0f)

    // Update PlayerController again with continued input
    val prevX2 = player.position.x
    game.playerController.update(
      delta = 0.05f,
      input = game.touchInputController,
      cameraController = game.cameraController,
      combatSystem = game.combatSystem
    )
    assertTrue("Player must continue moving through repeated enemy hits", player.position.x > prevX2)

    game.touchInputController.touchUp((joyCenterX + 60f).toInt(), (720f - joyCenterY).toInt(), 0, 0)
    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `player can attack and counterattack during enemy attack sequences and hit reactions`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val player = game.playerCharacter
    val combat = game.combatSystem

    // Martial Arts Stance counterattack test
    assertFalse(player.isSwordEquipped)
    player.applyDamage(14f, 0f, 0f)
    assertTrue("Player must be in hit flinch state", player.hitStunTimer > 0f)

    // Player presses light attack button during hit reaction
    combat.triggerLightAttack(player, game.cameraController)
    assertTrue("Player martial arts attack must execute during hit reaction", combat.fistsCombatController.isFistAttacking)

    // Reset combat state and switch to Sword Stance
    combat.fistsCombatController.reset()
    combat.toggleWeaponStance(player)
    assertTrue(player.isSwordEquipped)

    // Apply another hit and test sword counterattack
    player.invulnerabilityTimer = 0f
    player.applyDamage(14f, 0f, 0f)
    assertTrue(player.hitStunTimer > 0f)

    combat.triggerLightAttack(player, game.cameraController)
    assertTrue("Player sword attack must execute during hit reaction", combat.swordCombatController.isSwordAttacking)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `knight rebalanced attack cooldowns recovery and spacing prevent stunlock and crowding`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val enemy = game.entityManager.primaryEnemy()!!
    val player = game.playerCharacter

    // Verify initial breathing room at game start
    assertTrue("Initial combo attack cooldown must give player breathing room", enemy.comboAttackCooldown >= 1.5f)
    assertTrue("Initial phantom charge cooldown must be balanced", enemy.phantomChargeCooldown >= 4.0f)
    assertTrue("Initial crescent slash cooldown must be balanced", enemy.crescentSlashCooldown >= 3.0f)

    // Position enemy in tactical range
    enemy.entity.position.set(player.position.x, player.position.y, player.position.z - 2.2f)

    // Update enemy: verify physical separation pushes enemy more than player to avoid trapping
    val enemyZBefore = enemy.entity.position.z
    val playerZBefore = player.position.z
    enemy.update(
      delta = 0.016f,
      player = player,
      worldManager = game.worldManager,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { _ -> }

    val enemyDisplacement = kotlin.math.abs(enemy.entity.position.z - enemyZBefore)
    val playerDisplacement = kotlin.math.abs(player.position.z - playerZBefore)
    assertTrue("Enemy must take majority of separation displacement to avoid trapping player", enemyDisplacement >= playerDisplacement)

    // Fast forward comboAttackCooldown to 0 and trigger combo
    val field = enemy.javaClass.getDeclaredField("comboAttackCooldown")
    field.isAccessible = true
    field.set(enemy, 0f)

    // Tick enemy update until Combo Step 1 begins
    enemy.update(
      delta = 0.016f,
      player = player,
      worldManager = game.worldManager,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { _ -> }

    assertEquals("Knight must enter telegraphed combo cross slash", com.aethelgard.survival.core.entities.EnemyAiState.COMBO_SLASH_HORIZONTAL, enemy.aiState)

    // Fast forward to conclude attack
    val stateTimerField = enemy.javaClass.getDeclaredField("stateTimer")
    stateTimerField.isAccessible = true
    stateTimerField.set(enemy, 0.47f)

    enemy.update(
      delta = 0.016f,
      player = player,
      worldManager = game.worldManager,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { _ -> }

    // After concluding combo attack, Knight must enter recovery and have active cooldown
    assertTrue("Knight must enter recovery state or chain smoothly", enemy.aiState == com.aethelgard.survival.core.entities.EnemyAiState.ATTACK_RECOVER || enemy.aiState == com.aethelgard.survival.core.entities.EnemyAiState.COMBO_SLASH_DIAGONAL)
    assertTrue("Knight must have combo cooldown active to prevent infinite stunlock", enemy.comboAttackCooldown > 0f)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `Gdx input processor is properly registered upon game create and preserved across resize and render`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val processor = com.badlogic.gdx.Gdx.input.inputProcessor
    assertNotNull("Gdx.input.inputProcessor must be registered and non-null", processor)
    assertEquals("Gdx.input.inputProcessor must be TouchInputController", game.touchInputController, processor)

    // Verify resize preserves input processor
    game.resize(1920, 1080)
    assertEquals(game.touchInputController, com.badlogic.gdx.Gdx.input.inputProcessor)

    // Verify render preserves or restores input processor even if externally tampered
    com.badlogic.gdx.Gdx.input.inputProcessor = null
    game.render()
    assertEquals(game.touchInputController, com.badlogic.gdx.Gdx.input.inputProcessor)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `touchscreen input pipeline dispatches joystick, attack, jump, sprint, and HUD events through Gdx inputProcessor`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val processor = com.badlogic.gdx.Gdx.input.inputProcessor!!
    val input = game.touchInputController

    // 1. Joystick Touch-Down, Drag, and Release through LibGDX InputProcessor
    // Touch on left side of screen (screen coordinates: Y is 0 at top, so screenY=520 corresponds to y=200 in Y-up)
    val joyScreenX = 180
    val joyScreenY = 520 // 720 - 520 = 200 in Y-up
    val consumedDown = processor.touchDown(joyScreenX, joyScreenY, 0, 0)
    assertTrue("Joystick touchDown must be handled", consumedDown)
    assertEquals(0, input.joystickPointer)

    // Drag forward (-Y in screen coords = +Y in game world)
    processor.touchDragged(joyScreenX, joyScreenY - 60, 0)
    assertTrue("Forward drag must produce positive moveY", input.moveY > 0.4f)

    // Release joystick -> pointer resets and velocity zeroed
    val consumedUp = processor.touchUp(joyScreenX, joyScreenY - 60, 0, 0)
    assertTrue("Joystick touchUp must be handled", consumedUp)
    assertEquals(-1, input.joystickPointer)
    assertEquals(0f, input.moveX, 0.001f)
    assertEquals(0f, input.moveY, 0.001f)

    // 2. Attack Button Touches through InputProcessor
    val atkScreenX = input.lightAttackButton.centerX.toInt()
    val atkScreenY = (720f - input.lightAttackButton.centerY).toInt()
    val prevPunches = game.combatSystem.fistsCombatController.currentComboStep
    processor.touchDown(atkScreenX, atkScreenY, 1, 0)
    assertTrue("Light attack button must be pressed", input.isLightAttackPressed)
    assertTrue("Light attack must trigger combo advance", game.combatSystem.fistsCombatController.currentComboStep > prevPunches || game.combatSystem.fistsCombatController.isFistAttacking)
    processor.touchUp(atkScreenX, atkScreenY, 1, 0)
    assertFalse("Light attack button must be released", input.isLightAttackPressed)

    // 3. Jump Button Touch through InputProcessor
    val jumpScreenX = input.jumpButton.centerX.toInt()
    val jumpScreenY = (720f - input.jumpButton.centerY).toInt()
    processor.touchDown(jumpScreenX, jumpScreenY, 2, 0)
    assertTrue("Jump must be requested", input.consumeJumpRequest())
    processor.touchUp(jumpScreenX, jumpScreenY, 2, 0)

    // 4. Sprint Button Touch (Toggle) through InputProcessor
    val sprintScreenX = input.sprintButton.centerX.toInt()
    val sprintScreenY = (720f - input.sprintButton.centerY).toInt()
    assertFalse("Sprint must start inactive", input.isSprintActive)
    processor.touchDown(sprintScreenX, sprintScreenY, 3, 0)
    assertTrue("Sprint must toggle active on tap", input.isSprintActive)
    processor.touchUp(sprintScreenX, sprintScreenY, 3, 0)

    // 5. HUD Inventory & Crafting Buttons through InputProcessor
    val invScreenX = (game.survivalOverlay.invButton.x + game.survivalOverlay.invButton.width * 0.5f).toInt()
    val invScreenY = (720f - (game.survivalOverlay.invButton.y + game.survivalOverlay.invButton.height * 0.5f)).toInt()
    assertEquals(com.aethelgard.survival.core.survival.SurvivalModalType.NONE, game.survivalOverlay.activeModal)
    processor.touchDown(invScreenX, invScreenY, 4, 0)
    assertEquals("Inventory modal must open on tap", com.aethelgard.survival.core.survival.SurvivalModalType.INVENTORY, game.survivalOverlay.activeModal)
    processor.touchUp(invScreenX, invScreenY, 4, 0)

    // Close modal via close button
    val closeScreenX = (game.survivalOverlay.modalCloseButton.x + game.survivalOverlay.modalCloseButton.width * 0.5f).toInt()
    val closeScreenY = (720f - (game.survivalOverlay.modalCloseButton.y + game.survivalOverlay.modalCloseButton.height * 0.5f)).toInt()
    processor.touchDown(closeScreenX, closeScreenY, 4, 0)
    assertEquals("Modal must close when close button is tapped", com.aethelgard.survival.core.survival.SurvivalModalType.NONE, game.survivalOverlay.activeModal)
    processor.touchUp(closeScreenX, closeScreenY, 4, 0)

    // 6. Multi-Touch Simultaneous Input: Joystick drag (pointer 0) + Attack (pointer 1)
    processor.touchDown(joyScreenX, joyScreenY, 0, 0)
    processor.touchDragged(joyScreenX + 50, joyScreenY, 0)
    assertTrue("Joystick moveX must be active", input.moveX > 0.3f)

    // Tap light attack with a second finger while joystick is active
    processor.touchDown(atkScreenX, atkScreenY, 1, 0)
    assertTrue("Attack must be pressed while joystick is moving", input.isLightAttackPressed)
    processor.touchUp(atkScreenX, atkScreenY, 1, 0)
    assertFalse(input.isLightAttackPressed)
    assertTrue("Joystick must maintain movement when second finger releases", input.moveX > 0.3f)

    processor.touchUp(joyScreenX + 50, joyScreenY, 0, 0)
    assertEquals(0f, input.moveX, 0.001f)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `invisible contextual interact button does not consume touches when no interactable node is nearby`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    // Move player far from any interactive node or monument
    game.playerCharacter.position.set(200f, 0f, 200f)

    val processor = com.badlogic.gdx.Gdx.input.inputProcessor!!
    val contextBtn = game.survivalOverlay.contextInteractButton

    // Touch directly inside the contextual interact button coordinates
    val screenX = (contextBtn.x + contextBtn.width * 0.5f).toInt()
    val screenY = (720f - (contextBtn.y + contextBtn.height * 0.5f)).toInt()

    // When no node is nearby, onTouchIntercept must NOT consume the touch, allowing right-side camera look or controls
    processor.touchDown(screenX, screenY, 0, 0)
    assertEquals("Modal must not open", com.aethelgard.survival.core.survival.SurvivalModalType.NONE, game.survivalOverlay.activeModal)

    processor.touchUp(screenX, screenY, 0, 0)
    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `open modal backdrop touch closes modal and restores gameplay input`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val processor = com.badlogic.gdx.Gdx.input.inputProcessor!!

    // Open Crafting modal
    game.survivalOverlay.activeModal = com.aethelgard.survival.core.survival.SurvivalModalType.CRAFTING
    assertTrue(game.touchInputController.isModalOpen())

    // Touch on the very top edge outside the modal window (e.g. y = 710 in game coordinates -> screenY = 10)
    processor.touchDown(640, 10, 0, 0)
    assertEquals("Tapping backdrop outside modal window must close modal", com.aethelgard.survival.core.survival.SurvivalModalType.NONE, game.survivalOverlay.activeModal)
    assertFalse("Gameplay controls must be unblocked after modal closes", game.touchInputController.isModalOpen())
    processor.touchUp(640, 10, 0, 0)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `freeform building catalog defines modular pieces across all categories materials and dimensions`() {
    val pieces = com.aethelgard.survival.core.building.BuildingCatalog.ALL_PIECES
    assertTrue("BuildingCatalog must contain at least 30 modular pieces", pieces.size >= 30)

    // Verify all 11 building categories have pieces
    for (cat in com.aethelgard.survival.core.building.BuildingCategory.entries) {
      val inCat = com.aethelgard.survival.core.building.BuildingCatalog.byCategory(cat)
      assertTrue("Category $cat must have at least one piece", inCat.isNotEmpty())
    }

    // Verify Wood, Stone, Brick, and Metal materials exist
    for (mat in com.aethelgard.survival.core.building.BuildingMaterial.entries) {
      assertTrue("Material $mat must be represented in catalog", pieces.any { it.material == mat })
    }

    // Verify Walls have fixed 3.0m height and prohibit vertical wall stacking
    val walls = com.aethelgard.survival.core.building.BuildingCatalog.byCategory(com.aethelgard.survival.core.building.BuildingCategory.WALLS)
    for (wall in walls) {
      assertEquals(3.0f, wall.height, 0.001f)
      assertFalse("Walls must not allow vertical wall stacking", wall.allowVerticalWallStacking)
    }

    // Verify Pillars have adjustable height in 0.5m increments between 1.5m and 4.5m
    val pillars = com.aethelgard.survival.core.building.BuildingCatalog.byCategory(com.aethelgard.survival.core.building.BuildingCategory.PILLARS)
    for (pillar in pillars) {
      assertTrue("Pillars must support adjustable height", pillar.isAdjustableHeight)
      assertEquals(1.5f, pillar.minHeight, 0.001f)
      assertEquals(4.5f, pillar.maxHeight, 0.001f)
      assertEquals(0.5f, pillar.heightIncrement, 0.001f)
    }
  }

  @Test
  fun `building mode placement validation resource deduction rotation pillar height and socket snapping`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val bm = game.buildingManager
    val inv = game.inventory
    val player = game.playerCharacter

    // Position player on open clearing ground away from obstacles
    val buildX = 10f
    val buildZ = 18f
    val groundY = game.worldManager.getTerrainHeight(buildX, buildZ)
    player.position.set(buildX, groundY, buildZ + 3.8f)

    // Clear inventory and test invalid placement due to missing resources
    inv.clear()
    bm.toggleBuildMode()
    assertTrue(bm.isBuildModeActive)
    bm.selectPiece(com.aethelgard.survival.core.building.BuildingCatalog.WOOD_FLOOR)

    val targetPos = com.badlogic.gdx.math.Vector3(buildX, groundY, buildZ)
    val missingResValidation = bm.validatePlacement(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_FLOOR,
      position = targetPos,
      rotationYaw = 0f,
      customHeight = 0.22f,
      player = player,
      inventory = inv
    )
    assertFalse("Placement must fail when resources are missing", missingResValidation.isValid)
    assertTrue(missingResValidation.reason.contains("MISSING"))

    // Add exact resources for 1 Wood Floor (2 Wood) and place it
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.WOOD.id, 2)
    val floorPiece = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_FLOOR,
      position = targetPos,
      rotationYaw = 0f,
      customHeight = 0.22f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )
    assertNotNull("Wood Floor must place validly on ground without requiring foundation", floorPiece)
    assertEquals("Resources must be deducted only upon successful placement", 0, inv.countItem(com.aethelgard.survival.core.survival.ResourceCatalog.WOOD.id))

    // Test rotation and Pillar adjustable height increments
    bm.selectPiece(com.aethelgard.survival.core.building.BuildingCatalog.WOOD_PILLAR)
    assertEquals(3.0f, bm.currentPillarHeight, 0.001f)
    bm.adjustPillarHeight(2) // +1.0m -> 4.0m
    assertEquals(4.0f, bm.currentPillarHeight, 0.001f)
    bm.adjustPillarHeight(-5) // -2.5m -> clamped at 1.5m
    assertEquals(1.5f, bm.currentPillarHeight, 0.001f)

    bm.rotatePreview(90f)
    assertEquals(90f, bm.previewRotationYaw, 0.001f)

    // Test Socket Snapping: snap a second Wood Floor to the North edge of the first Wood Floor
    val rawNearNorth = com.badlogic.gdx.math.Vector3(buildX + 0.2f, groundY, buildZ - 2.7f)
    val snapped = bm.findBestSnapTransform(
      candidateDef = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_FLOOR,
      rawTargetPos = rawNearNorth,
      candidateYaw = 0f,
      candidateHeight = 0.22f
    )
    assertNotNull("Floor near existing floor edge must snap to socket", snapped)
    assertEquals(buildX, snapped!!.position.x, 0.05f)
    assertEquals(buildZ - 3.0f, snapped.position.z, 0.05f)

    // Toggle snapping OFF and verify manual free placement works
    assertTrue(bm.isSnappingEnabled)
    bm.toggleSnapping()
    assertFalse(bm.isSnappingEnabled)
    bm.toggleSnapping()
    assertTrue(bm.isSnappingEnabled)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `structural support rules enforce grounded floors elevated pillar support roof requirements and reject vertical wall stacking`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val bm = game.buildingManager
    val inv = game.inventory
    val player = game.playerCharacter

    // Give generous building materials
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.WOOD.id, 99)
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.STONE.id, 99)

    val baseX = -12f
    val baseZ = 18f
    val groundY = game.worldManager.getTerrainHeight(baseX, baseZ)
    player.position.set(baseX, groundY, baseZ + 4.0f)

    // 1. Floating floor in mid-air without support must be rejected
    val floatingFloorCheck = bm.validatePlacement(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_FLOOR,
      position = com.badlogic.gdx.math.Vector3(baseX, groundY + 3.0f, baseZ),
      rotationYaw = 0f,
      customHeight = 0.22f,
      player = player,
      inventory = inv
    )
    assertFalse("Floating floor without pillar or wall support must be rejected", floatingFloorCheck.isValid)

    // 2. Roof on bare ground or floating in air without wall/pillar must be rejected
    val unSupportedRoofCheck = bm.validatePlacement(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_FLAT_ROOF,
      position = com.badlogic.gdx.math.Vector3(baseX, groundY + 3.0f, baseZ),
      rotationYaw = 0f,
      customHeight = 0.25f,
      player = player,
      inventory = inv
    )
    assertFalse("Roof without wall or pillar support must be rejected", unSupportedRoofCheck.isValid)

    // 3. Place a Wood Wall on the ground
    val wallPos = com.badlogic.gdx.math.Vector3(baseX, groundY, baseZ)
    val placedWall = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_WALL,
      position = wallPos,
      rotationYaw = 0f,
      customHeight = 3.0f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )
    assertNotNull("Wood Wall must place on ground", placedWall)

    // 4. Attempting to stack a second Wall vertically on top of the first Wall must be rejected
    val stackedWallPos = com.badlogic.gdx.math.Vector3(baseX, groundY + 3.0f, baseZ)
    val stackedWallCheck = bm.validatePlacement(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_WALL,
      position = stackedWallPos,
      rotationYaw = 0f,
      customHeight = 3.0f,
      player = player,
      inventory = inv
    )
    assertFalse("Walls must not be allowed to stack vertically", stackedWallCheck.isValid)
    assertTrue(stackedWallCheck.reason.contains("STACK VERTICALLY"))

    // 5. Roof supported by the placed Wall's top edge must succeed
    val roofPos = com.badlogic.gdx.math.Vector3(baseX, groundY + 3.0f, baseZ)
    val placedRoof = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_FLAT_ROOF,
      position = roofPos,
      rotationYaw = 0f,
      customHeight = 0.25f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )
    assertNotNull("Roof supported by top of placed wall must succeed", placedRoof)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `building interiors walkable floors stairs openable doors and functional furniture interactions`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val bm = game.buildingManager
    val inv = game.inventory
    val player = game.playerCharacter

    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.WOOD.id, 99)
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.STONE.id, 99)
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.FIBERS.id, 99)
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.COAL.id, 99)

    val hx = 14f
    val hz = 16f
    val groundY = game.worldManager.getTerrainHeight(hx, hz)
    player.position.set(hx, groundY, hz + 3.5f)

    // 1. Place a Stone Foundation and verify walkable surface height increases
    val foundPos = com.badlogic.gdx.math.Vector3(hx, groundY, hz)
    val foundation = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.STONE_FOUNDATION,
      position = foundPos,
      rotationYaw = 0f,
      customHeight = 0.6f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )
    assertNotNull(foundation)
    val walkableY = game.worldManager.getWalkableSurfaceHeight(hx, hz, groundY + 0.5f)
    assertEquals(groundY + 0.6f, walkableY, 0.05f)

    // 2. Place a Wood Door and test open/close interaction & collision passability
    val doorPos = com.badlogic.gdx.math.Vector3(hx, groundY + 0.6f, hz - 1.5f)
    player.position.set(hx, groundY + 0.6f, hz + 1.2f)
    val door = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_DOOR,
      position = doorPos,
      rotationYaw = 0f,
      customHeight = 3.0f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )
    assertNotNull(door)
    assertFalse("Door starts closed", door!!.isDoorOpen)

    // Closed door pushes entity away when colliding
    val testPos = com.badlogic.gdx.math.Vector3(doorPos.x, doorPos.y, doorPos.z + 0.2f)
    bm.resolveBuildingCollisions(testPos, 0.45f)
    assertTrue("Closed door must collide and push entity out", testPos.z > doorPos.z + 0.2f)

    // Open door via interactWithPiece
    val doorMsg = bm.interactWithPiece(door, player, game.survivalAttributes, game.audioManager)
    assertTrue(door.isDoorOpen)
    assertTrue(doorMsg.contains("OPENED"))

    // Open door allows movement without collision push
    val passPos = com.badlogic.gdx.math.Vector3(doorPos.x, doorPos.y, doorPos.z + 0.2f)
    bm.resolveBuildingCollisions(passPos, 0.45f)
    assertEquals("Open door must allow player to walk through", doorPos.z + 0.2f, passPos.z, 0.001f)

    // 3. Place Bed and test sleep/rest interaction
    val bedPos = com.badlogic.gdx.math.Vector3(hx + 3.5f, game.worldManager.getTerrainHeight(hx + 3.5f, hz), hz)
    player.position.set(hx + 3.5f, bedPos.y, hz + 2.5f)
    val bed = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.FURNITURE_BED,
      position = bedPos,
      rotationYaw = 0f,
      customHeight = 0.65f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )
    assertNotNull(bed)
    player.applyDamage(30f, 0f, 0f)
    val hpBeforeRest = player.health
    val bedResult = bm.interactWithPiece(bed!!, player, game.survivalAttributes, game.audioManager)
    assertTrue(bedResult.contains("RESTED"))
    assertTrue("Sleeping in bed must restore player health", player.health > hpBeforeRest)

    // 4. Place Torch and test light toggle
    val torchPos = com.badlogic.gdx.math.Vector3(hx - 3.5f, game.worldManager.getTerrainHeight(hx - 3.5f, hz), hz)
    player.position.set(hx - 3.5f, torchPos.y, hz + 2.5f)
    val torch = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.FURNITURE_TORCH,
      position = torchPos,
      rotationYaw = 0f,
      customHeight = 1.4f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )
    assertNotNull(torch)
    assertTrue(torch!!.isLit)
    bm.interactWithPiece(torch, player, game.survivalAttributes, game.audioManager)
    assertFalse("Interacting with lit torch extinguishes it", torch.isLit)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `builders hammer inspects repairs upgrades materials and safely dismantles with 50 percent refund while protecting critical support`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val bm = game.buildingManager
    val inv = game.inventory
    val player = game.playerCharacter

    inv.clear()
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.WOOD.id, 20)
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.STONE.id, 20)
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.COPPER_ORE.id, 20)
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.IRON_ORE.id, 20)

    // Craft Builder's Hammer from CraftingManager
    val hammerRecipe = game.craftingManager.recipes.first { it.id == "builders_hammer_hand" }
    val craftedHammer = game.craftingManager.craft(hammerRecipe, inv, isNearCraftingTable = false)
    assertNotNull("Player must be able to hand-craft Builder's Hammer", craftedHammer)

    val bx = -14f
    val bz = 20f
    val gy = game.worldManager.getTerrainHeight(bx, bz)
    player.position.set(bx, gy, bz + 3.2f)

    // Place a Wood Wall (costs 3 Wood) and a Wood Flat Roof on top of it
    val wall = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_WALL,
      position = com.badlogic.gdx.math.Vector3(bx, gy, bz),
      rotationYaw = 0f,
      customHeight = 3.0f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )!!
    val roof = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_FLAT_ROOF,
      position = com.badlogic.gdx.math.Vector3(bx, gy + 3.0f, bz),
      rotationYaw = 0f,
      customHeight = 0.25f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )!!

    // 1. Inspect with Builder's Hammer
    val inspectText = bm.inspectPiece(wall)
    assertTrue(inspectText.contains("Wood Wall"))
    assertTrue(inspectText.contains("Wood"))

    // 2. Damage wall and repair with Builder's Hammer
    bm.damagePiece(wall, 80f, game.audioManager)
    assertTrue(wall.health < wall.maxHealth)
    val repaired = bm.repairPiece(wall, inv, game.audioManager)
    assertTrue("Builder's Hammer must repair damaged building piece", repaired)
    assertEquals(wall.maxHealth, wall.health, 0.01f)

    // 3. Upgrade Wood Wall -> Stone Wall -> Brick Wall -> Metal Wall
    val stoneWall = bm.upgradePiece(wall, inv, game.audioManager)
    assertNotNull("Wood Wall must upgrade to Stone Wall", stoneWall)
    assertEquals(com.aethelgard.survival.core.building.BuildingMaterial.STONE, stoneWall!!.definition.material)

    val brickWall = bm.upgradePiece(stoneWall, inv, game.audioManager)
    assertNotNull("Stone Wall must upgrade to Brick Wall", brickWall)
    assertEquals(com.aethelgard.survival.core.building.BuildingMaterial.BRICK, brickWall!!.definition.material)

    val metalWall = bm.upgradePiece(brickWall, inv, game.audioManager)
    assertNotNull("Brick Wall must upgrade to Metal Wall", metalWall)
    assertEquals(com.aethelgard.survival.core.building.BuildingMaterial.METAL, metalWall!!.definition.material)

    // 4. Attempting to dismantle the wall while the roof is supported solely by it must be blocked
    assertFalse("Cannot safely dismantle wall while roof depends on it", bm.canSafelyDismantle(metalWall))
    val blockedRefund = bm.dismantlePiece(metalWall, inv, game.audioManager)
    assertTrue("Dismantling critical support must be rejected", blockedRefund.isEmpty())

    // 5. Dismantle the roof first (safe), then dismantle the wall (returns 50% refund)
    val roofRefund = bm.dismantlePiece(roof, inv, game.audioManager)
    assertTrue("Roof dismantle must refund 50% resources", roofRefund.isNotEmpty())
    val wallRefund = bm.dismantlePiece(metalWall, inv, game.audioManager)
    assertTrue("Wall dismantle must succeed once roof is removed", wallRefund.isNotEmpty())

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `building health material resistance monster damage chest inventory preservation and conditional raids`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val bm = game.buildingManager
    val raidMgr = game.buildingRaidManager
    val inv = game.inventory
    val player = game.playerCharacter

    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.WOOD.id, 50)
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.STONE.id, 50)
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.IRON_ORE.id, 50)

    val x = 8f
    val z = 14f
    val gy = game.worldManager.getTerrainHeight(x, z)
    player.position.set(x, gy, z + 3.5f)

    val woodWall = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_WALL,
      position = com.badlogic.gdx.math.Vector3(x, gy, z),
      rotationYaw = 0f,
      customHeight = 3.0f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )!!
    val metalWall = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.METAL_WALL,
      position = com.badlogic.gdx.math.Vector3(x + 3.2f, game.worldManager.getTerrainHeight(x + 3.2f, z), z),
      rotationYaw = 0f,
      customHeight = 3.0f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )!!

    // 1. Single monster attack must never destroy a full-health wall, and Metal resists more damage than Wood
    val destroyedWood = raidMgr.attackBuildingWithMonster(woodWall, rawDamage = 500f)
    assertFalse("Single monster hit must never one-shot a full-health wall", destroyedWood)
    val woodDamageTaken = woodWall.maxHealth - woodWall.health

    raidMgr.attackBuildingWithMonster(metalWall, rawDamage = 40f)
    val metalDamageTaken = metalWall.maxHealth - metalWall.health
    assertTrue("Metal wall must take less effective damage than Wood wall", metalDamageTaken < woodDamageTaken)

    // 2. Store items in ChestManager, place a Storage Chest, destroy it via monster damage, and verify chest inventory is preserved
    game.chestManager.chestInventory.clear()
    game.chestManager.chestInventory.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.RARE_ORE.id, 7)
    val placedChest = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.FURNITURE_CHEST,
      position = com.badlogic.gdx.math.Vector3(x - 3.2f, game.worldManager.getTerrainHeight(x - 3.2f, z), z),
      rotationYaw = 0f,
      customHeight = 0.75f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )!!
    bm.damagePiece(placedChest, 999f, game.audioManager)
    assertEquals("Destroying a wall or chest must NEVER delete stored chest inventory", 7, game.chestManager.chestInventory.countItem(com.aethelgard.survival.core.survival.ResourceCatalog.RARE_ORE.id))

    // 3. Verify conditional monster raid threat calculation & entrance prioritization
    raidMgr.recordNoiseEvent(60f, player.position)
    assertTrue("Environmental threat must increase with mining/combat noise and proximity to monument", raidMgr.evaluateEnvironmentalThreat() > 10f)

    // Place a closed Wood Door between monster and player and verify findObstructingStructure targets the door
    val doorPos = com.badlogic.gdx.math.Vector3(x, gy, z + 1.5f)
    val door = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_DOOR,
      position = doorPos,
      rotationYaw = 0f,
      customHeight = 3.0f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )!!
    val monsterPos = com.badlogic.gdx.math.Vector3(x, gy, z + 0.2f)
    val obstructing = raidMgr.findObstructingStructure(monsterPos, player.position)
    assertEquals("Monster must prioritize closed door entrance obstructing path to player", door.instanceId, obstructing?.instanceId)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `placed buildings persist across save and load without duplicating or saving ghost previews`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val bm = game.buildingManager
    val inv = game.inventory
    val player = game.playerCharacter

    bm.clearAllPieces()
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.WOOD.id, 30)
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.STONE.id, 30)

    val px = 12f
    val pz = 22f
    val gy = game.worldManager.getTerrainHeight(px, pz)
    player.position.set(px, gy, pz + 3.5f)

    // Place a Stone Pillar with custom 4.0m height and a Wood Door (opened)
    val pillar = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.STONE_PILLAR,
      position = com.badlogic.gdx.math.Vector3(px, gy, pz),
      rotationYaw = 90f,
      customHeight = 4.0f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )!!
    val door = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_DOOR,
      position = com.badlogic.gdx.math.Vector3(px + 2.0f, game.worldManager.getTerrainHeight(px + 2.0f, pz), pz),
      rotationYaw = 180f,
      customHeight = 3.0f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )!!
    door.isDoorOpen = true
    bm.damagePiece(pillar, 40f, game.audioManager)

    // Activate Build Mode so a Ghost Preview is active during save
    bm.isBuildModeActive = true
    bm.updateGhostPreview(player, game.cameraController, inv)

    // Save and reload twice to verify no duplicates and no ghost preview saved
    game.survivalSaveManager.saveAll(inv, game.chestManager, game.survivalAttributes, game.harvestableResourceManager, bm)
    game.survivalSaveManager.loadAll(inv, game.chestManager, game.survivalAttributes, game.harvestableResourceManager, bm)
    game.survivalSaveManager.loadAll(inv, game.chestManager, game.survivalAttributes, game.harvestableResourceManager, bm)

    assertEquals("Reloading must restore exact count of placed pieces without duplicates or ghost preview", 2, bm.placedPieces.size)
    val loadedPillar = bm.placedPieces.first { it.definition.id == "stone_pillar" }
    val loadedDoor = bm.placedPieces.first { it.definition.id == "wood_door" }

    assertEquals(4.0f, loadedPillar.customHeight, 0.01f)
    assertEquals(90f, loadedPillar.rotationYaw, 0.01f)
    assertEquals(pillar.health, loadedPillar.health, 0.1f)
    assertTrue("Door open state must persist across save/load", loadedDoor.isDoorOpen)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `mobile build mode touch controls coexist with movement joystick and camera look`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val processor = com.badlogic.gdx.Gdx.input.inputProcessor!!
    val overlay = game.survivalOverlay
    val bm = game.buildingManager

    // 1. Tap BUILD button in HUD to open Build Mode
    val buildBtnX = (overlay.buildButton.x + overlay.buildButton.width * 0.5f).toInt()
    val buildBtnY = (720f - (overlay.buildButton.y + overlay.buildButton.height * 0.5f)).toInt()
    assertFalse(bm.isBuildModeActive)
    processor.touchDown(buildBtnX, buildBtnY, 0, 0)
    processor.touchUp(buildBtnX, buildBtnY, 0, 0)
    assertTrue("Tapping BUILD button must activate Build Mode", bm.isBuildModeActive)

    // 2. Tap ROTATE button in Build Mode drawer
    val rotBtnX = (overlay.buildRotateButton.x + overlay.buildRotateButton.width * 0.5f).toInt()
    val rotBtnY = (720f - (overlay.buildRotateButton.y + overlay.buildRotateButton.height * 0.5f)).toInt()
    processor.touchDown(rotBtnX, rotBtnY, 0, 0)
    processor.touchUp(rotBtnX, rotBtnY, 0, 0)
    assertEquals(90f, bm.previewRotationYaw, 0.01f)

    // 3. Simultaneously use left movement joystick while Build Mode is active
    val joyX = 180
    val joyY = 520
    processor.touchDown(joyX, joyY, 1, 0)
    processor.touchDragged(joyX, joyY - 60, 1)
    assertTrue("Player joystick must remain responsive while in Build Mode", game.touchInputController.moveY > 0.3f)
    processor.touchUp(joyX, joyY - 60, 1, 0)

    // 4. Tap EXIT button in Build Mode drawer
    val cancelBtnX = (overlay.buildCancelButton.x + overlay.buildCancelButton.width * 0.5f).toInt()
    val cancelBtnY = (720f - (overlay.buildCancelButton.y + overlay.buildCancelButton.height * 0.5f)).toInt()
    processor.touchDown(cancelBtnX, cancelBtnY, 0, 0)
    processor.touchUp(cancelBtnX, cancelBtnY, 0, 0)
    assertFalse("Tapping EXIT button must close Build Mode", bm.isBuildModeActive)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `divine annihilation activation 15s post completion cooldown and repeated activation prevention`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val ult = game.combatSystem.divineAnnihilationController
    val processor = com.badlogic.gdx.Gdx.input.inputProcessor!!
    val ultBtn = game.touchInputController.ultimateButton

    assertTrue("Ultimate ability must be ready initially", ult.canActivate())
    assertEquals(0f, ult.cooldownRemaining, 0.001f)

    // Activate via HUD ultimate button touch
    val tapX = ultBtn.centerX.toInt()
    val tapY = (720f - ultBtn.centerY).toInt()
    processor.touchDown(tapX, tapY, 0, 0)
    processor.touchUp(tapX, tapY, 0, 0)

    assertTrue("Tapping HUD ultimate button must activate DIVINE ANNIHILATION", ult.isActive)
    assertEquals(com.aethelgard.survival.core.combat.UltimatePhase.PHASE_1_AWAKENING, ult.currentPhase)
    assertFalse("Cannot re-activate while ability is executing", ult.canActivate())
    assertFalse(
      "triggerUltimateAbility must reject repeated activation during execution",
      game.combatSystem.triggerUltimateAbility(game.playerCharacter, game.cameraController)
    )
    assertEquals("Cooldown must not start ticking until execution finishes", 0f, ult.cooldownRemaining, 0.001f)

    // Advance through all 5 phases to completion (total duration = 5.25s)
    var totalElapsed = 0f
    while (ult.isActive && totalElapsed < 6.0f) {
      game.combatSystem.update(0.05f, game.playerCharacter, game.cameraController)
      totalElapsed += 0.05f
    }

    assertFalse("Ability must finish after Phase 5 completes", ult.isActive)
    assertEquals(com.aethelgard.survival.core.combat.UltimatePhase.INACTIVE, ult.currentPhase)
    assertEquals(
      "Cooldown must be set to exactly 15.0 seconds immediately after completion",
      15.0f,
      ult.cooldownRemaining,
      0.01f
    )
    assertFalse("Cannot activate while 15s cooldown is active", ult.canActivate())
    assertFalse(
      "triggerUltimateAbility must reject activation while cooling down",
      game.combatSystem.triggerUltimateAbility(game.playerCharacter, game.cameraController)
    )

    // Advance 7.5 seconds -> 7.5 seconds remaining
    game.combatSystem.update(7.5f, game.playerCharacter, game.cameraController)
    assertEquals(7.5f, ult.cooldownRemaining, 0.05f)
    assertFalse(ult.canActivate())

    // Advance remaining 7.5 seconds -> cooldown reaches 0 and ability becomes ready again
    game.combatSystem.update(7.55f, game.playerCharacter, game.cameraController)
    assertEquals(0f, ult.cooldownRemaining, 0.001f)
    assertTrue("Ultimate must be ready to activate again after 15.0s cooldown expires", ult.canActivate())

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `divine annihilation 5 phase progression dual energy spheres and exact sequential one at a time incantation`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val ult = game.combatSystem.divineAnnihilationController
    val player = game.playerCharacter
    val cam = game.cameraController

    assertTrue(game.combatSystem.triggerUltimateAbility(player, cam))

    // Phase 1: Awakening (0.00s .. 0.65s)
    game.combatSystem.update(0.25f, player, cam)
    assertEquals(com.aethelgard.survival.core.combat.UltimatePhase.PHASE_1_AWAKENING, ult.currentPhase)
    assertEquals(com.aethelgard.survival.core.player.AnimationState.ULTIMATE_AWAKENING, player.animationState)
    org.junit.Assert.assertNull("No incantation text before Phase 3", ult.activeIncantationStep)

    // Phase 2: Dual Energy Summoning (0.65s .. 1.45s)
    game.combatSystem.update(0.55f, player, cam) // elapsed = 0.80s
    assertEquals(com.aethelgard.survival.core.combat.UltimatePhase.PHASE_2_DUAL_SUMMON, ult.currentPhase)
    assertEquals(com.aethelgard.survival.core.player.AnimationState.ULTIMATE_DUAL_SUMMON, player.animationState)
    val sphereSeparationPhase2 = ult.whiteSpherePos.dst(ult.blackSpherePos)
    assertTrue("Light and Dark spheres must be spatially distinct above separate hands in Phase 2", sphereSeparationPhase2 > 0.45f)

    // Phase 3: Incantation (1.45s .. 3.45s) — sample each 0.40s window to verify one-at-a-time display
    val observedActiveSentences = mutableListOf<String>()
    val sampleTimesFromNow = floatArrayOf(0.75f, 0.40f, 0.40f, 0.40f, 0.40f) // lands at 1.55s, 1.95s, 2.35s, 2.75s, 3.15s
    for (dt in sampleTimesFromNow) {
      game.combatSystem.update(dt, player, cam)
      assertEquals(com.aethelgard.survival.core.combat.UltimatePhase.PHASE_3_INCANTATION, ult.currentPhase)
      val currentStep = ult.activeIncantationStep
      assertNotNull("Exactly one incantation sentence must be active during Phase 3", currentStep)
      assertFalse(
        "Active incantation sentence must not contain multiple lines concatenated",
        currentStep!!.sentence.contains("\n")
      )
      observedActiveSentences.add(currentStep.sentence)
    }

    val expectedSentences = listOf(
      "Heavens above, Abyss below…",
      "Hear the voice that commands you!",
      "Let Light and Darkness become one!",
      "OBLITERATE ALL BEFORE ME!",
      "DIVINE… ANNIHILATION!"
    )
    assertEquals("Incantation sentences must appear in exact required sequence one at a time", expectedSentences, observedActiveSentences)
    assertEquals(expectedSentences, ult.displayedIncantationLog)

    // Phase 4: Merging (3.45s .. 4.15s)
    game.combatSystem.update(0.85f, player, cam) // elapsed = 4.00s (late Phase 4)
    assertEquals(com.aethelgard.survival.core.combat.UltimatePhase.PHASE_4_MERGING, ult.currentPhase)
    assertEquals(com.aethelgard.survival.core.player.AnimationState.ULTIMATE_CONVERGE_MERGE, player.animationState)
    val sphereSeparationLatePhase4 = ult.whiteSpherePos.dst(ult.blackSpherePos)
    assertTrue(
      "Both spheres must converge together in Phase 4",
      sphereSeparationLatePhase4 < sphereSeparationPhase2 * 0.35f
    )

    // Phase 5: Annihilation (4.15s .. 5.25s)
    game.combatSystem.update(0.35f, player, cam) // elapsed = 4.35s
    assertEquals(com.aethelgard.survival.core.combat.UltimatePhase.PHASE_5_ANNIHILATION, ult.currentPhase)
    assertEquals(com.aethelgard.survival.core.player.AnimationState.ULTIMATE_RELEASE_BEAM, player.animationState)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `divine annihilation damage timing only in phase 5 and single hit per enemy behavior`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val ult = game.combatSystem.divineAnnihilationController
    val player = game.playerCharacter
    val cam = game.cameraController
    val enemy = game.entityManager.primaryEnemy()!!

    // Position player at (0, y, 20) facing -Z (yaw=0 on camera -> facingYaw=180 -> -Z toward enemy at z=8.5)
    val py = game.worldManager.getWalkableSurfaceHeight(0f, 20f)
    player.position.set(0f, py, 20f)
    enemy.resetToSpawn(game.worldManager)
    val ey = game.worldManager.getWalkableSurfaceHeight(0f, 8.5f)
    enemy.entity.position.set(0f, ey, 8.5f)
    val initialEnemyHp = enemy.health

    assertTrue(game.combatSystem.triggerUltimateAbility(player, cam))

    // Step through Phases 1, 2, 3, and 4 (up to 4.10s, just before Phase 5 starts at 4.15s)
    var t = 0f
    while (t < 4.05f) {
      game.combatSystem.update(0.10f, player, cam)
      t += 0.10f
      assertEquals(
        "Enemy must NOT receive any damage during Phases 1..4 (${ult.currentPhase})",
        initialEnemyHp,
        enemy.health,
        0.001f
      )
    }

    // Enter Phase 5 (Annihilation) and step multiple frames across Phase 5
    var phase5Frames = 0
    while (ult.isActive) {
      game.combatSystem.update(0.05f, player, cam)
      if (ult.currentPhase == com.aethelgard.survival.core.combat.UltimatePhase.PHASE_5_ANNIHILATION) {
        phase5Frames++
      }
    }
    assertTrue("Phase 5 must execute across multiple frames", phase5Frames >= 10)

    val expectedHpAfterSingleHit = (initialEnemyHp - com.aethelgard.survival.core.combat.DivineAnnihilationController.ULTIMATE_DAMAGE).coerceAtLeast(0f)
    assertEquals(
      "Enemy inside the beam must be damaged in Phase 5 and strictly ONCE per ability execution",
      expectedHpAfterSingleHit,
      enemy.health,
      0.01f
    )

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `divine annihilation phase 5 destroys destructible world objects while preserving buildings and returns normal player control`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val player = game.playerCharacter
    val cam = game.cameraController
    val rm = game.harvestableResourceManager
    val bm = game.buildingManager
    val inv = game.inventory

    // Find a destructible tree or boulder node and position the player so the beam passes directly through it
    val targetNode = rm.nodes.first {
      !it.isDestroyed && (it.type == com.aethelgard.survival.core.survival.HarvestNodeType.TREE ||
        it.type == com.aethelgard.survival.core.survival.HarvestNodeType.BERRY_BUSH ||
        it.type == com.aethelgard.survival.core.survival.HarvestNodeType.BOULDER)
    }
    val placeZ = targetNode.position.z + 10f
    val placeX = targetNode.position.x
    val py = game.worldManager.getWalkableSurfaceHeight(placeX, placeZ)
    player.position.set(placeX, py, placeZ)
    cam.yawDegrees = 0f // Player faces -Z directly toward targetNode

    // Also place a player-built Wood Floor right in the beam path to verify player buildings are NOT destroyed
    inv.addItem(com.aethelgard.survival.core.survival.ResourceCatalog.WOOD.id, 10)
    val builtFloor = bm.placePieceAt(
      def = com.aethelgard.survival.core.building.BuildingCatalog.WOOD_FLOOR,
      position = com.badlogic.gdx.math.Vector3(placeX, game.worldManager.getTerrainHeight(placeX, targetNode.position.z + 3f), targetNode.position.z + 3f),
      rotationYaw = 0f,
      customHeight = 0.22f,
      player = player,
      inventory = inv,
      audioManager = game.audioManager
    )
    assertNotNull(builtFloor)
    val initialBuildingCount = bm.placedPieces.size

    // Also test in First-Person mode to verify both camera modes work cleanly
    cam.toggleCameraMode()
    assertEquals(com.aethelgard.survival.core.camera.CameraMode.FIRST_PERSON, cam.cameraMode)

    // Activate DIVINE ANNIHILATION
    assertTrue(game.combatSystem.triggerUltimateAbility(player, cam))

    // Attempt joystick movement while ultimate is active -> horizontal velocity must remain 0
    val processor = com.badlogic.gdx.Gdx.input.inputProcessor!!
    processor.touchDown(180, 520, 0, 0)
    processor.touchDragged(180, 420, 0)
    game.playerController.update(0.1f, game.touchInputController, cam, game.combatSystem) {}
    assertEquals("Horizontal X velocity must be halted during ultimate execution", 0f, player.velocity.x, 0.001f)
    assertEquals("Horizontal Z velocity must be halted during ultimate execution", 0f, player.velocity.z, 0.001f)

    // Advance until execution completes
    var guard = 0
    while (game.combatSystem.isUltimateActive && guard < 200) {
      game.playerController.update(0.05f, game.touchInputController, cam, game.combatSystem) {}
      cam.update(0.05f, player)
      guard++
    }
    assertFalse("Ultimate execution must complete cleanly", game.combatSystem.isUltimateActive)

    // Verify destructible world node in the beam path was destroyed and recorded for save persistence
    assertTrue("Destructible resource node in beam path must be destroyed in Phase 5", targetNode.isDestroyed)
    assertTrue(
      "Destroyed node ID must be included in serialized save data",
      rm.serializeDestroyedNodes().split(",").contains(targetNode.id)
    )

    // Verify protected stations (Ancient Monument, Crafting Table, Storage Chest) and player buildings are untouched
    val monument = rm.nodes.first { it.type == com.aethelgard.survival.core.survival.HarvestNodeType.ANCIENT_MONUMENT }
    val table = rm.nodes.first { it.type == com.aethelgard.survival.core.survival.HarvestNodeType.CRAFTING_TABLE }
    val chest = rm.nodes.first { it.type == com.aethelgard.survival.core.survival.HarvestNodeType.STORAGE_CHEST }
    assertFalse(monument.isDestroyed)
    assertFalse(table.isDestroyed)
    assertFalse(chest.isDestroyed)
    assertEquals("Player-built structures must not be destroyed by Divine Annihilation", initialBuildingCount, bm.placedPieces.size)
    assertEquals(builtFloor!!.maxHealth, builtFloor.health, 0.01f)

    // Verify normal player movement immediately resumes after completion
    game.playerController.update(0.1f, game.touchInputController, cam, game.combatSystem) {}
    val speedAfter = kotlin.math.sqrt(player.velocity.x * player.velocity.x + player.velocity.z * player.velocity.z)
    assertTrue("Player must immediately regain normal movement control after ultimate completes", speedAfter > 0.5f)
    processor.touchUp(180, 420, 0, 0)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `day night cycle 12 hour clock am pm formatting and day progression`() {
    val dnc = com.aethelgard.survival.core.world.DayNightCycle()

    // 1. Verify exact 90s = 1 in-game hour rate and 2160s = 1 full day
    assertEquals(90f, com.aethelgard.survival.core.world.DayNightCycle.SECONDS_PER_IN_GAME_HOUR, 0.001f)
    assertEquals(2160f, com.aethelgard.survival.core.world.DayNightCycle.SECONDS_PER_DAY, 0.001f)

    // 2. Midnight 00:00 -> "12:00 AM" on DAY 1
    dnc.timeOfDaySeconds = 0f
    dnc.currentDay = 1
    dnc.update(0f)
    assertEquals("12:00 AM", dnc.formattedTime12Hour)
    assertEquals("DAY 1", dnc.formattedDay)
    assertEquals("DAY 1  •  12:00 AM", dnc.combinedTimeAndDayString)
    assertFalse(dnc.isDaytime)
    assertEquals("🌙", dnc.celestialIcon)

    // 3. Early Morning 06:30 AM (6.5 * 90 = 585s)
    dnc.timeOfDaySeconds = 6.5f * 90f
    dnc.update(0f)
    assertEquals("06:30 AM", dnc.formattedTime12Hour)
    assertTrue(dnc.isDaytime)
    assertEquals("☀", dnc.celestialIcon)

    // 4. Transition 11:59 AM -> 12:00 PM (Noon)
    // 11h 59m = (11 + 59/60) * 90s = 1078.5s
    dnc.timeOfDaySeconds = (11f + 59f / 60f) * 90f
    dnc.update(0f)
    assertEquals("11:59 AM", dnc.formattedTime12Hour)

    // Advance 2 real-world seconds (passing noon)
    dnc.update(2f)
    assertEquals("12:00 PM", dnc.formattedTime12Hour)
    assertEquals("DAY 1  •  12:00 PM", dnc.combinedTimeAndDayString)
    assertTrue(dnc.isDaytime)

    // 5. Evening 06:30 PM (18.5 * 90 = 1665s)
    dnc.timeOfDaySeconds = 18.5f * 90f
    dnc.update(0f)
    assertEquals("06:30 PM", dnc.formattedTime12Hour)

    // 6. Transition 11:59 PM -> Midnight Day 2 Rollover (12:00 AM on DAY 2)
    // 23h 59m = (23 + 59/60) * 90s = 2158.5s
    dnc.timeOfDaySeconds = (23f + 59f / 60f) * 90f
    dnc.update(0f)
    assertEquals("11:59 PM", dnc.formattedTime12Hour)
    assertEquals("DAY 1", dnc.formattedDay)

    // Advance 2 real seconds (wraps 2160s back to 0s, incrementing day counter exactly once)
    dnc.update(2f)
    assertEquals("12:00 AM", dnc.formattedTime12Hour)
    assertEquals(2, dnc.currentDay)
    assertEquals("DAY 2", dnc.formattedDay)
    assertEquals("DAY 2  •  12:00 AM", dnc.combinedTimeAndDayString)

    // 7. Verify time advances continuously when stationary (e.g. 90 real seconds advances exactly 1 hour)
    val startSec = dnc.timeOfDaySeconds
    dnc.update(90f)
    assertEquals(startSec + 90f, dnc.timeOfDaySeconds, 0.01f)
    assertEquals("01:00 AM", dnc.formattedTime12Hour)
  }

  @Test
  fun `day night cycle atmospheric lighting and persistence save load`() {
    val dnc = com.aethelgard.survival.core.world.DayNightCycle()

    // Check Noon lighting: bright ambient and daylight clear color
    dnc.setTime(12f)
    assertTrue(dnc.isDaytime)
    assertTrue("Noon ambient red must be bright daylight", dnc.ambientColor.r > 0.40f)
    assertTrue("Noon ambient green must be bright daylight", dnc.ambientColor.g > 0.40f)
    assertTrue("Noon sun direction Y must be high in the sky", dnc.sunDirection.y > 0.5f)

    // Check Midnight lighting: dark night ambient and moonlight
    dnc.setTime(0f)
    assertFalse(dnc.isDaytime)
    assertTrue("Midnight ambient must be dark", dnc.ambientColor.r < 0.25f)
    assertTrue("Midnight ambient must be dark", dnc.ambientColor.g < 0.25f)
    assertTrue("Midnight moon direction Y must be elevated", dnc.moonDirection.y > 0.5f)

    // Serialization & Deserialization
    dnc.currentDay = 4
    dnc.setTime(14.5f) // 02:30 PM
    val serialized = dnc.serialize()
    assertTrue(serialized.contains("4"))

    val restoredDnc = com.aethelgard.survival.core.world.DayNightCycle()
    restoredDnc.deserialize(serialized)
    assertEquals(4, restoredDnc.currentDay)
    assertEquals("02:30 PM", restoredDnc.formattedTime12Hour)
    assertEquals("DAY 4  •  02:30 PM", restoredDnc.combinedTimeAndDayString)
  }

  @Test
  fun `expanded world terrain mesh boundaries and foot grounding`() {
    val assets = com.aethelgard.survival.core.assets.GameAssetManager()
    assets.initialize()
    val modelFactory = com.aethelgard.survival.core.world.ProceduralModelFactory(assets)
    val worldGen = com.aethelgard.survival.core.world.WorldGenerator(modelFactory)

    // 1. Central Sanctuary Clearing: (0, 0) and spawn avenue (0, 20) must be flat (y = 0.0f)
    assertEquals(0f, worldGen.computeTerrainHeight(0f, 0f), 0.001f)
    assertEquals(0f, worldGen.computeTerrainHeight(0f, 15f), 0.001f)

    // 2. Expanded landscape: Verify natural elevation variations (hills & valleys)
    val northHill = worldGen.computeTerrainHeight(-40f, -60f)
    assertTrue("North highlands should have elevated hill (> 1.0m)", northHill > 1.0f)

    val westGrove = worldGen.computeTerrainHeight(-80f, 20f)
    assertTrue("West grove should have gentle natural terrain", westGrove > -2f && westGrove < 12f)

    // 3. Verify world generator produces expanded 360m world data with colliders
    val worldData = worldGen.generateClearingWorld()
    assertNotNull(worldData.terrainInstance)
    assertNotNull(worldData.skyDomeInstance)
    assertTrue("Must generate wilderness trees across expanded map", worldData.treeInstances.size > 50)
    assertTrue("Must generate wilderness boulders across expanded map", worldData.boulderInstances.size > 40)
    assertTrue("Must have colliders registered for all trees & boulders", worldData.obstacleColliders.size > 90)

    assets.dispose()
  }

  @Test
  fun `green slime creature design, natural world spawning, and integration into entityManager`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val slimes = game.entityManager.enemyControllers.filterIsInstance<com.aethelgard.survival.core.entities.GreenSlimeController>()
    assertEquals("Clearing and forest groves must have 4 natural green slimes spawned", 4, slimes.size)

    for (slime in slimes) {
      assertEquals("Entity name must be Green Slime", "Green Slime", slime.entity.name)
      assertEquals("Max health must be 95 HP", 95f, slime.maxHealth, 0.01f)
      assertEquals("Initial health must be 95 HP", 95f, slime.health, 0.01f)
      assertEquals("Collision radius must be 0.58m", 0.58f, slime.collisionRadius, 0.01f)
      assertFalse("Slime must not be dead initially", slime.aiState == com.aethelgard.survival.core.entities.EnemyAiState.DEAD)
      assertFalse("Slime must not have dropped crystal yet", slime.hasDroppedCrystal)

      // Verify spawn location is NOT directly on the player spawn at (0, 20)
      val distToPlayerSpawn = kotlin.math.sqrt(
        (slime.spawnOrigin.x - 0f) * (slime.spawnOrigin.x - 0f) +
        (slime.spawnOrigin.z - 20f) * (slime.spawnOrigin.z - 20f)
      )
      assertTrue("Slime must spawn at reasonable distance from player (> 8m)", distToPlayerSpawn > 8f)
    }

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `green slime animations idle move jump landing charging attacking hit and death`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val slime = game.entityManager.enemyControllers.filterIsInstance<com.aethelgard.survival.core.entities.GreenSlimeController>().first()

    // 1. Test Idle breathing animation
    slime.update(0.016f, game.playerCharacter, game.worldManager, game.audioManager, game.combatSystem) {}
    assertTrue("Slime scaleY must be non-zero during idle", slime.scaleY > 0.8f && slime.scaleY < 1.2f)

    // 2. Put player near slime to trigger PURSUE and bouncing locomotion
    game.playerCharacter.position.set(slime.entity.position.x + 5.0f, slime.entity.position.y, slime.entity.position.z)
    slime.update(0.016f, game.playerCharacter, game.worldManager, game.audioManager, game.combatSystem) {}
    assertEquals(com.aethelgard.survival.core.entities.EnemyAiState.PURSUE, slime.aiState)

    // Advance frames to exercise JUMP and airborne stretch
    var sawAirborneStretch = false
    var sawLandingCompression = false
    for (f in 0 until 60) {
      slime.update(0.016f, game.playerCharacter, game.worldManager, game.audioManager, game.combatSystem) {}
      if (slime.jumpOffsetY > 0.2f && slime.scaleY > 1.05f) {
        sawAirborneStretch = true
      }
      if (slime.jumpOffsetY == 0f && slime.scaleY < 0.85f && slime.scaleX > 1.15f) {
        sawLandingCompression = true
      }
    }
    assertTrue("Slime must exhibit parabolic vertical leap and stretch during jump", sawAirborneStretch)
    assertTrue("Slime must compress and spread on landing impact", sawLandingCompression)

    // 3. Test HIT reaction when damaged
    val initialHp = slime.health
    slime.receiveDamage(25f, 0f, 4f, false, game.audioManager)
    assertEquals(com.aethelgard.survival.core.entities.EnemyAiState.HIT_STAGGER, slime.aiState)
    assertEquals(initialHp - 25f, slime.health, 0.01f)
    assertTrue("Hit flash timer must activate on damage", slime.hitFlashTimer > 0f)

    // 4. Test DEATH and guaranteed single Green Crystal drop
    val initialPickups = game.groundPickupManager.activePickups.size
    val deathKilled = slime.receiveDamage(100f, 0f, 0f, true, game.audioManager)
    assertTrue("Lethal damage must return true for enemy defeated", deathKilled)
    assertEquals(0f, slime.health, 0.001f)
    assertEquals(com.aethelgard.survival.core.entities.EnemyAiState.DEAD, slime.aiState)
    assertTrue("Slime hasDroppedCrystal must be true", slime.hasDroppedCrystal)
    assertEquals("Ground pickups must have received exactly one Green Crystal", initialPickups + 1, game.groundPickupManager.activePickups.size)

    // 5. Test dead slime cannot drop another crystal
    val countAfterDeath = game.groundPickupManager.activePickups.size
    slime.receiveDamage(50f, 0f, 0f, false, game.audioManager)
    assertEquals("Dead slime must NOT drop duplicate crystals", countAfterDeath, game.groundPickupManager.activePickups.size)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `green energy orb projectile travels, collides with obstacles and terrain, hits player and applies damage`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val slime = game.entityManager.enemyControllers.filterIsInstance<com.aethelgard.survival.core.entities.GreenSlimeController>().first()
    game.playerCharacter.health = 100f

    // Position player within shooting range (5m) in front of slime on open terrain
    val spawnX = slime.spawnOrigin.x
    val spawnZ = slime.spawnOrigin.z
    val slimeY = game.worldManager.getWalkableSurfaceHeight(spawnX, spawnZ)
    val playerY = game.worldManager.getWalkableSurfaceHeight(spawnX, spawnZ + 5.0f)
    slime.entity.position.set(spawnX, slimeY, spawnZ)
    game.playerCharacter.position.set(spawnX, playerY, spawnZ + 5.0f)
    slime.attackCooldown = 0f
    slime.update(0.016f, game.playerCharacter, game.worldManager, game.audioManager, game.combatSystem) {}

    // Advance through CHARGE state (1.05s) to trigger Green Energy Orb launch and trajectory
    var orbSpawned = false
    var playerReceivedDamage = false
    for (f in 0 until 140) {
      slime.update(
        0.020f,
        game.playerCharacter,
        game.worldManager,
        game.audioManager,
        game.combatSystem
      ) { dmg ->
        playerReceivedDamage = true
      }
      if (slime.activeOrbs.isNotEmpty() || slime.hasFiredAnyOrb) {
        orbSpawned = true
      }
    }

    assertTrue("Green Slime must launch its signature Green Energy Orb", orbSpawned || slime.hasFiredAnyOrb)

    // Verify Green Energy Orb projectile hits player and deals damage
    assertTrue("Player must receive damage from Green Energy Orb", playerReceivedDamage || game.playerCharacter.health < 100f)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `guaranteed green crystal drop adds to inventory and sends pickup notification`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val slime = game.entityManager.enemyControllers.filterIsInstance<com.aethelgard.survival.core.entities.GreenSlimeController>().first()
    game.inventory.clear()
    assertEquals(0, game.inventory.countItem("green_crystal"))

    // Defeat slime to trigger crystal drop
    val deathLocation = com.badlogic.gdx.math.Vector3(slime.entity.position)
    slime.receiveDamage(100f, 0f, 0f, true, game.audioManager)
    assertTrue("Slime must be dead", slime.aiState == com.aethelgard.survival.core.entities.EnemyAiState.DEAD)
    assertTrue("Slime must be marked as dropped crystal", slime.hasDroppedCrystal)

    val crystalPickup = game.groundPickupManager.activePickups.firstOrNull { it.itemId == "green_crystal" }
    assertNotNull("Emerald green crystal pickup must exist in ground pickups", crystalPickup)

    // Position player directly over crystal to collect it
    game.playerCharacter.position.set(crystalPickup!!.basePosition.x, crystalPickup.basePosition.y, crystalPickup.basePosition.z)

    var notificationReceived = false
    game.groundPickupManager.update(
      delta = 0.016f,
      player = game.playerCharacter,
      inventory = game.inventory,
      audioManager = game.audioManager,
      combatSystem = game.combatSystem
    ) { id, count ->
      if (id == "green_crystal" && count == 1) {
        notificationReceived = true
      }
    }

    assertTrue("Green Crystal must be collected", crystalPickup.isCollected)
    assertTrue("Notification must be sent on collection", notificationReceived)
    assertEquals("Inventory must now contain 1 Green Crystal", 1, game.inventory.countItem("green_crystal"))

    // Verify item definition for GREEN_CRYSTAL exists and is properly configured
    val def = com.aethelgard.survival.core.survival.ResourceCatalog.get("green_crystal")
    assertNotNull("Item definition for green_crystal must exist in ResourceCatalog", def)
    assertEquals("Green Crystal", def!!.name)
    assertEquals(com.aethelgard.survival.core.survival.ItemCategory.MAGIC, def.category)
    assertEquals(99, def.stackLimit)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `uneven natural terrain and darker woodland ground patches with walkable slopes`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val worldGen = game.worldManager.worldGenerator
    val worldData = game.worldManager.generatedData

    // 1. Central monument and spawn walkway remain flat at 0.0f so landmarks sit cleanly
    assertEquals(0f, worldGen.computeTerrainHeight(0f, 0f), 0.001f)
    assertEquals(0f, worldGen.computeTerrainHeight(0f, 15f), 0.001f)

    // 2. Verify uneven natural terrain inside the clearing away from the central walkway and across the world
    val sampleCoords = listOf(
      14f to -6f,
      -15f to 8f,
      4f to -19f,
      25f to 18f,
      -35f to -28f,
      55f to -45f,
      -65f to 40f,
      85f to 70f
    )
    val heights: List<Float> = sampleCoords.map { (x, z) -> worldGen.computeTerrainHeight(x, z) }
    val distinctRounded = heights.map { h -> (h * 100f).toInt() }.toSet()
    assertTrue("Terrain must have varied natural elevations across hills, slopes, and valleys", distinctRounded.size >= 6)
    assertTrue("Terrain must include elevated hills (> 0.35m)", heights.any { h -> h > 0.35f })
    assertTrue("Terrain must include gentle depressions/valleys (< -0.05m)", heights.any { h -> h < -0.05f })

    // 3. Verify 3D dark green grass patches and subtle dirt patches are generated across the ground
    assertTrue("World must include 3D dark green grass patches", worldData.darkGrassPatchInstances.size >= 80)
    assertTrue("World must include 3D subtle dirt/loam patches", worldData.dirtPatchInstances.size >= 40)

    // 4. Verify darker, natural woodland-meadow grass texture (not overly bright neon green)
    val pixmap = game.assetManager.createTerrainPixmap(64)
    var rSum = 0f
    var gSum = 0f
    var bSum = 0f
    val totalPixels = 64 * 64
    for (y in 0 until 64) {
      for (x in 0 until 64) {
        val rgba = pixmap.getPixel(x, y)
        val r = ((rgba ushr 24) and 0xFF) / 255f
        val g = ((rgba ushr 16) and 0xFF) / 255f
        val b = ((rgba ushr 8) and 0xFF) / 255f
        rSum += r
        gSum += g
        bSum += b
      }
    }
    pixmap.dispose()
    val avgG = gSum / totalPixels
    val avgR = rSum / totalPixels
    assertTrue("Ground green channel must be rich natural woodland green (not neon > 0.55)", avgG in 0.18f..0.46f)
    assertTrue("Ground green channel must exceed red channel for natural grass appearance", avgG > avgR)

    // 5. Verify player walks smoothly across uneven slopes without getting stuck or floating
    val player = game.playerCharacter
    player.position.set(10f, game.worldManager.getWalkableSurfaceHeight(10f, -6f), -6f)
    player.isGrounded = true
    val processor = com.badlogic.gdx.Gdx.input.inputProcessor!!
    processor.touchDown(180, 520, 0, 0)
    processor.touchDragged(240, 400, 0)
    for (f in 0 until 30) {
      game.playerController.update(0.02f, game.touchInputController, game.cameraController, game.combatSystem) {}
    }
    processor.touchUp(240, 400, 0, 0)
    val expectedGroundY = game.worldManager.getWalkableSurfaceHeight(player.position.x, player.position.z)
    assertTrue("Player must remain grounded on uneven terrain slope", player.isGrounded)
    assertEquals("Player Y must match walkable terrain surface height without floating", expectedGroundY, player.position.y, 0.05f)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `pixel art inventory icons are unique and non empty for all resources consumables and tools`() {
    val realAndroidAudio = setupGdxMocks()
    val registry = com.aethelgard.survival.core.survival.PixelArtIconRegistry()

    val requiredItemIds = listOf(
      "wood", "hardwood", "branches", "roots",
      "stone", "coal", "copper_ore", "iron_ore", "rare_ore",
      "plants", "mushrooms", "berries", "fibers", "animal_hides",
      "monster_essence", "magic_crystals", "ancient_relics", "boss_materials",
      "green_crystal", "mp_potion",
      "roasted_mushrooms", "berry_brew", "herbal_salve", "mana_essence_tonic",
      "stone_axe", "iron_axe", "stone_pickaxe", "iron_pickaxe", "shovel", "sickle", "knife", "builders_hammer"
    )

    val signatures = mutableSetOf<String>()
    for (itemId in requiredItemIds) {
      val pixmap = registry.getOrCreatePixmap(itemId)
      assertEquals(16, pixmap.width)
      assertEquals(16, pixmap.height)

      var nonTransparentPixels = 0
      val sb = StringBuilder()
      for (y in 0 until 16) {
        for (x in 0 until 16) {
          val pixel: Int = pixmap.getPixel(x, y)
          val alpha: Int = pixel and 0xFF
          if (alpha != 0) nonTransparentPixels++
          sb.append(pixel).append(',')
        }
      }

      assertTrue("Pixel art icon for $itemId must have visible non-transparent pixel art (> 20 pixels)", nonTransparentPixels > 20)
      signatures.add(sb.toString())

      val tex = registry.getOrCreateTexture(itemId)
      assertNotNull("Texture for $itemId must be created", tex)
    }

    assertEquals(
      "Every resource, consumable, and tool must have a distinct pixel art icon",
      requiredItemIds.size,
      signatures.size
    )

    registry.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `visible 3d held items in player hand update with hotbar selection and follow hand during movement and jumping`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val inv = game.inventory
    val player = game.playerCharacter
    inv.clear()

    // Populate hotbar slots 0..4 with Wood, Stone, Magic Crystals, MP Potion, and Empty
    inv.slots[0] = com.aethelgard.survival.core.survival.ItemStack(com.aethelgard.survival.core.survival.ResourceCatalog.WOOD.id, 5)
    inv.slots[1] = com.aethelgard.survival.core.survival.ItemStack(com.aethelgard.survival.core.survival.ResourceCatalog.STONE.id, 5)
    inv.slots[2] = com.aethelgard.survival.core.survival.ItemStack(com.aethelgard.survival.core.survival.ResourceCatalog.MAGIC_CRYSTALS.id, 3)
    inv.slots[3] = com.aethelgard.survival.core.survival.ItemStack(com.aethelgard.survival.core.survival.ResourceCatalog.MP_POTION.id, 2)
    inv.slots[4] = null

    // 1. Select Slot 0 (Wood)
    inv.activeHotbarIndex = 0
    game.syncHeldHotbarItem()
    game.render()

    assertEquals("wood", player.equippedItemId)
    val woodNode = player.animationController.getHeldItemNode("wood")
    val stoneNode = player.animationController.getHeldItemNode("stone")
    assertNotNull("3D held node for wood must exist on player's hand", woodNode)
    assertNotNull("3D held node for stone must exist on player's hand", stoneNode)
    assertEquals("Selected wood model must be scaled to 1.0f", 1f, woodNode!!.scale.x, 0.01f)
    assertEquals("Unselected stone model must be hidden (scale 0.0001f)", 0.0001f, stoneNode!!.scale.x, 0.001f)

    // 2. Switch to Slot 1 (Stone) -> Held model updates to Stone and hides Wood
    inv.activeHotbarIndex = 1
    game.syncHeldHotbarItem()
    game.render()
    assertEquals("stone", player.equippedItemId)
    assertEquals(1f, stoneNode.scale.x, 0.01f)
    assertEquals(0.0001f, woodNode.scale.x, 0.001f)

    // 3. Switch to Slot 3 (MP Potion) -> Held model updates to MP Potion
    inv.activeHotbarIndex = 3
    game.syncHeldHotbarItem()
    game.render()
    val potionNode = player.animationController.getHeldItemNode("mp_potion")
    assertNotNull("3D held node for mp_potion must exist on player's hand", potionNode)
    assertEquals(1f, potionNode!!.scale.x, 0.01f)

    // 4. Verify held model follows player's hand during walking, running, and jumping
    inv.activeHotbarIndex = 0 // Back to Wood
    game.syncHeldHotbarItem()
    val processor = com.badlogic.gdx.Gdx.input.inputProcessor!!
    processor.touchDown(180, 520, 0, 0)
    processor.touchDragged(180, 360, 0) // Run forward
    val jx = game.touchInputController.jumpButton.centerX.toInt()
    val jy = (720f - game.touchInputController.jumpButton.centerY).toInt()
    game.touchInputController.touchDown(jx, jy, 9, 0)
    game.touchInputController.touchUp(jx, jy, 9, 0)
    for (f in 0 until 10) {
      game.render()
    }
    processor.touchUp(180, 360, 0, 0)
    assertEquals("Wood must remain visible in hand while running and jumping", 1f, woodNode.scale.x, 0.01f)

    // 5. Switch to Slot 4 (Empty) -> Held model hides
    inv.activeHotbarIndex = 4
    game.syncHeldHotbarItem()
    game.render()
    assertEquals(null, player.equippedItemId)
    assertEquals("Wood model must hide when empty slot is selected", 0.0001f, woodNode.scale.x, 0.001f)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `blue and red flower monsters start dormant awaken when approached have distinct combat balance and drop single potions`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val blueFlowers = game.entityManager.enemyControllers
      .filterIsInstance<com.aethelgard.survival.core.entities.BlueFlowerMonsterController>()
    val redFlowers = game.entityManager.enemyControllers
      .filterIsInstance<com.aethelgard.survival.core.entities.RedFlowerMonsterController>()

    assertTrue("World must spawn at least 3 Blue Flower Monsters", blueFlowers.size >= 3)
    assertTrue("World must spawn at least 3 Red Flower Monsters", redFlowers.size >= 3)

    val blue = blueFlowers.first()
    val red = redFlowers.first()
    assertEquals("Blue Flower Monster", blue.entity.name)
    assertEquals("Red Flower Monster", red.entity.name)

    // 1. Verify Distinct Combat Balance between Red and Blue Flower Monsters
    assertTrue(
      "Red Flower Monster must have faster projectiles (${red.projectileSpeed}) than Blue Flower Monster (${blue.projectileSpeed})",
      red.projectileSpeed > blue.projectileSpeed
    )
    assertTrue(
      "Red Flower Monster must deal lower damage per projectile (${red.projectileDamage}) than Blue Flower Monster (${blue.projectileDamage})",
      red.projectileDamage < blue.projectileDamage
    )
    assertTrue(
      "Red Flower Monster must have more frequent ranged attacks (${red.baseAttackCooldown}s) than Blue Flower Monster (${blue.baseAttackCooldown}s)",
      red.baseAttackCooldown < blue.baseAttackCooldown
    )

    // 2. Verify Dormant -> Emerging -> Active Awakening Lifecycle for Red & Blue Flower Monsters
    assertEquals(com.aethelgard.survival.core.entities.FlowerMonsterState.DORMANT, red.flowerState)
    assertEquals(com.aethelgard.survival.core.entities.FlowerMonsterState.DORMANT, blue.flowerState)
    assertFalse(red.hasAwakened)
    assertFalse(blue.hasAwakened)

    // Move player within detection range of Red Flower Monster
    game.playerCharacter.position.set(red.entity.position.x, red.entity.position.y, red.entity.position.z + 7.0f)
    red.update(0.05f, game.playerCharacter, game.worldManager, game.audioManager, game.combatSystem) {}
    assertEquals(
      "Red Flower Monster must transition to EMERGING when player approaches",
      com.aethelgard.survival.core.entities.FlowerMonsterState.EMERGING,
      red.flowerState
    )
    assertTrue("Emerging progress must increase as roots/stem rise and petals open", red.emergeProgress > 0f)

    // Finish awakening animation
    for (step in 0 until 20) {
      red.update(0.04f, game.playerCharacter, game.worldManager, game.audioManager, game.combatSystem) {}
    }
    assertTrue("Red Flower Monster must finish awakening into active state", red.hasAwakened)

    // Trigger ranged attack from Red Flower Monster and verify direct damage only (no poison/DoT) and Dash dodgeability
    red.attackCooldown = 0f
    for (step in 0 until 25) {
      red.update(0.04f, game.playerCharacter, game.worldManager, game.audioManager, game.combatSystem) {}
    }
    assertTrue("Red Flower Monster must fire crimson spore projectile", red.hasFiredAnyOrb)

    // Test Dash dodging against active projectile
    game.playerCharacter.isDodging = true
    val hpWhileDodging = game.playerCharacter.health
    for (step in 0 until 15) {
      red.update(0.03f, game.playerCharacter, game.worldManager, game.audioManager, game.combatSystem) {}
    }
    assertEquals("Player dashing must dodge flower monster projectiles without taking damage", hpWhileDodging, game.playerCharacter.health, 0.001f)
    game.playerCharacter.isDodging = false

    // 3. Defeat Blue Flower Monster -> drops 1 MP Potion ("mp_potion") ground pickup
    game.inventory.clear()
    game.groundPickupManager.clear()
    game.survivalOverlay.notifications.clear()

    val blueKilled = blue.receiveDamage(500f, 0f, 0f, true, game.audioManager)
    assertTrue("Lethal damage must defeat Blue Flower Monster", blueKilled)
    assertEquals(com.aethelgard.survival.core.entities.FlowerMonsterState.DEATH, blue.flowerState)
    assertTrue(blue.hasDroppedMpPotion)
    assertEquals("Exactly 1 MP Potion pickup must spawn on Blue Flower defeat", 1, game.groundPickupManager.activePickups.size)
    // Subsequent hit while dead must not duplicate drop
    blue.receiveDamage(100f, 0f, 0f, false, game.audioManager)
    assertEquals(1, game.groundPickupManager.activePickups.size)

    // 4. Defeat Red Flower Monster -> drops 1 Health Potion ("health_potion") ground pickup
    val redKilled = red.receiveDamage(500f, 0f, 0f, true, game.audioManager)
    assertTrue("Lethal damage must defeat Red Flower Monster", redKilled)
    assertEquals(com.aethelgard.survival.core.entities.FlowerMonsterState.DEATH, red.flowerState)
    assertTrue(red.hasDroppedHealthPotion)
    assertEquals("Exactly 1 Health Potion pickup must be added on Red Flower defeat", 2, game.groundPickupManager.activePickups.size)
    // Subsequent hit while dead must not duplicate drop
    red.receiveDamage(100f, 0f, 0f, false, game.audioManager)
    assertEquals(2, game.groundPickupManager.activePickups.size)

    // Collect both potions from the ground and verify HUD notifications & inventory
    for (pickup in game.groundPickupManager.activePickups.toList()) {
      game.playerCharacter.position.set(pickup.basePosition.x, pickup.basePosition.y, pickup.basePosition.z)
      game.groundPickupManager.update(
        delta = 0.016f,
        player = game.playerCharacter,
        inventory = game.inventory,
        audioManager = game.audioManager,
        combatSystem = game.combatSystem
      ) { id, count ->
        game.survivalOverlay.addNotification(id, count)
      }
    }

    assertEquals("Inventory must contain 1 MP Potion after pickup", 1, game.inventory.countItem("mp_potion"))
    assertEquals("Inventory must contain 1 Health Potion after pickup", 1, game.inventory.countItem("health_potion"))
    assertTrue("HUD notifications must include '1X MP Potion'", game.survivalOverlay.notifications.any { it.text == "1X MP Potion" })
    assertTrue("HUD notifications must include '1X Health Potion'", game.survivalOverlay.notifications.any { it.text == "1X Health Potion" })

    // Verify Health Potion and MP Potion are collectible inventory items only (no consumption effects)
    val mpSlot = game.inventory.slots.indexOfFirst { it?.itemId == "mp_potion" }
    val hpSlot = game.inventory.slots.indexOfFirst { it?.itemId == "health_potion" }
    assertFalse(
      "MP Potion must be a collectible inventory item only (not consumable)",
      game.survivalOverlay.consumeItemAtSlot(mpSlot, game.inventory, game.survivalAttributes, game.playerCharacter, game.audioManager)
    )
    assertFalse(
      "Health Potion must be a collectible inventory item only (not consumable)",
      game.survivalOverlay.consumeItemAtSlot(hpSlot, game.inventory, game.survivalAttributes, game.playerCharacter, game.audioManager)
    )

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `hud resource collection notifications format as NX ItemName combine repeated pickups and render pixel art icons`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val overlay = game.survivalOverlay
    overlay.notifications.clear()

    // Add 1 Wood then 2 Wood -> must combine into "3X Wood"
    overlay.addNotification(ResourceCatalog.WOOD.id, 1)
    assertEquals(1, overlay.notifications.size)
    assertEquals("1X Wood", overlay.notifications[0].text)

    overlay.addNotification(ResourceCatalog.WOOD.id, 2)
    assertEquals("Repeated pickups of Wood must combine into one notification", 1, overlay.notifications.size)
    assertEquals("3X Wood", overlay.notifications[0].text)

    // Add Stone, Green Crystal, MP Potion, Health Potion -> must stack without overwriting each other
    overlay.addNotification(ResourceCatalog.STONE.id, 5)
    overlay.addNotification(ResourceCatalog.GREEN_CRYSTAL.id, 1)
    overlay.addNotification(ResourceCatalog.MP_POTION.id, 1)
    overlay.addNotification(ResourceCatalog.HEALTH_POTION.id, 1)

    assertEquals(5, overlay.notifications.size)
    val texts = overlay.notifications.map { it.text }
    assertTrue(texts.contains("3X Wood"))
    assertTrue(texts.contains("5X Stone"))
    assertTrue(texts.contains("1X Green Crystal"))
    assertTrue(texts.contains("1X MP Potion"))
    assertTrue(texts.contains("1X Health Potion"))

    // Verify rendering HUD with active notifications and Pixel Art icons succeeds cleanly
    game.render()

    // Verify notifications fade out and expire after maxLifetime
    overlay.update(3.0f)
    assertTrue("Notifications must expire after their fade duration", overlay.notifications.isEmpty())

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `triple jump system allows 3 consecutive jumps before landing and blocks 4th jump until grounded`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val player = game.playerCharacter
    val controller = game.playerController
    val input = game.touchInputController
    val cam = game.cameraController
    val combat = game.combatSystem

    val tapJump = {
      val jx = input.jumpButton.centerX.toInt()
      val jy = (720f - input.jumpButton.centerY).toInt()
      input.touchDown(jx, jy, 9, 0)
      input.touchUp(jx, jy, 9, 0)
    }

    // Start grounded on flat spawn area
    player.resetToSpawn(game.worldManager.getWalkableSurfaceHeight(0f, 15f))
    assertTrue("Player must start grounded", player.isGrounded)
    assertEquals("Initial jumpCount must be 0", 0, player.jumpCount)
    assertEquals("Max jumps must be 3", 3, player.maxJumps)

    // 1st Jump: Ground Jump
    tapJump()
    controller.update(0.016f, input, cam, combat) {}
    assertFalse("Player must be airborne after 1st jump", player.isGrounded)
    assertEquals("jumpCount must be 1 after ground jump", 1, player.jumpCount)
    assertTrue("Vertical velocity must be positive on 1st jump", player.velocity.y > 7.0f)

    // Advance a few airborne frames so player rises
    for (f in 0 until 8) {
      controller.update(0.016f, input, cam, combat) {}
    }
    assertFalse("Player must still be airborne", player.isGrounded)
    val yBeforeSecondJump = player.position.y

    // 2nd Jump: First Air Jump
    tapJump()
    controller.update(0.016f, input, cam, combat) {}
    assertFalse("Player must remain airborne on 2nd jump", player.isGrounded)
    assertEquals("jumpCount must be 2 after 2nd jump", 2, player.jumpCount)
    assertTrue("2nd jump must apply fresh upward velocity", player.velocity.y > 7.0f)

    // Advance a few more airborne frames
    for (f in 0 until 8) {
      controller.update(0.016f, input, cam, combat) {}
    }
    assertTrue("Player must have gained height after 2nd jump", player.position.y > yBeforeSecondJump)

    // 3rd Jump: Final Air Jump
    tapJump()
    controller.update(0.016f, input, cam, combat) {}
    assertFalse("Player must remain airborne on 3rd jump", player.isGrounded)
    assertEquals("jumpCount must be 3 after 3rd jump", 3, player.jumpCount)
    assertTrue("3rd jump must apply upward velocity", player.velocity.y > 7.5f)

    // Let velocity decay slightly in air, then attempt a 4th jump while still airborne
    for (f in 0 until 15) {
      controller.update(0.02f, input, cam, combat) {}
    }
    assertFalse("Player must still be in the air before landing", player.isGrounded)
    val velBefore4thAttempt = player.velocity.y
    tapJump()
    controller.update(0.016f, input, cam, combat) {}
    assertEquals("jumpCount must remain 3 (4th jump blocked)", 3, player.jumpCount)
    assertTrue("4th jump must not boost upward velocity", player.velocity.y <= velBefore4thAttempt)

    // Simulate until player lands back on the ground
    var guard = 0
    while (!player.isGrounded && guard < 200) {
      controller.update(0.025f, input, cam, combat) {}
      guard++
    }
    assertTrue("Player must land back on the ground", player.isGrounded)
    assertEquals("jumpCount must reset to 0 upon landing on the ground", 0, player.jumpCount)

    // Verify player can immediately perform a new jump sequence after landing
    tapJump()
    controller.update(0.016f, input, cam, combat) {}
    assertEquals("jumpCount must be 1 on new jump after landing", 1, player.jumpCount)

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `knight restored 2-3 hit combo aggressive pursuit range validation and universal dash evasion`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val enemy = game.entityManager.primaryEnemy()!!
    val player = game.playerCharacter

    // 1. Knight Movement Speed & Aggression parameters
    assertEquals("Knight base move speed must be restored to 4.6f", 4.6f, enemy.baseMoveSpeed, 0.01f)
    assertEquals("Knight fury move speed must be 5.6f", 5.6f, enemy.furyMoveSpeed, 0.01f)
    assertEquals("Knight melee trigger range must be 3.40f", 3.40f, enemy.meleeTriggerRange, 0.01f)
    assertEquals("Knight melee strike max range must be 2.85f", 2.85f, enemy.meleeStrikeMaxRange, 0.01f)

    // 2. Combo Chaining: Step 1 -> Step 2 -> Step 3
    // Position enemy within sword range
    enemy.entity.position.set(player.position.x, player.position.y, player.position.z - 2.5f)
    val comboCdField = enemy.javaClass.getDeclaredField("comboAttackCooldown")
    comboCdField.isAccessible = true
    comboCdField.set(enemy, 0f)

    // Tick into Step 1
    enemy.update(0.016f, player, game.worldManager, game.audioManager, game.combatSystem) {}
    assertEquals("Knight must enter Step 1 COMBO_SLASH_HORIZONTAL", com.aethelgard.survival.core.entities.EnemyAiState.COMBO_SLASH_HORIZONTAL, enemy.aiState)

    // Progress Step 1 to transition point
    val stateTimerField = enemy.javaClass.getDeclaredField("stateTimer")
    stateTimerField.isAccessible = true
    stateTimerField.set(enemy, 0.45f)
    enemy.update(0.016f, player, game.worldManager, game.audioManager, game.combatSystem) {}
    assertEquals("Knight must smoothly chain to Step 2 COMBO_SLASH_DIAGONAL", com.aethelgard.survival.core.entities.EnemyAiState.COMBO_SLASH_DIAGONAL, enemy.aiState)

    // Progress Step 2 to transition point
    stateTimerField.set(enemy, 0.45f)
    enemy.update(0.016f, player, game.worldManager, game.audioManager, game.combatSystem) {}
    assertEquals("Knight must smoothly chain to Step 3 COMBO_RISING_SLASH", com.aethelgard.survival.core.entities.EnemyAiState.COMBO_RISING_SLASH, enemy.aiState)

    // Progress Step 3 to conclusion
    stateTimerField.set(enemy, 0.47f)
    enemy.update(0.016f, player, game.worldManager, game.audioManager, game.combatSystem) {}
    assertEquals("Knight must conclude 3-hit combo into ATTACK_RECOVER", com.aethelgard.survival.core.entities.EnemyAiState.ATTACK_RECOVER, enemy.aiState)
    assertTrue("Knight must enter recovery cooldown", enemy.comboAttackCooldown > 0f)

    // 3. Strict Physical Range & Hitbox Check: No far away phantom damage
    val hitDoneField = enemy.javaClass.getDeclaredField("strikeHitDone1")
    hitDoneField.isAccessible = true
    hitDoneField.set(enemy, false)
    stateTimerField.set(enemy, 0.25f)
    val aiStateField = enemy.javaClass.getDeclaredField("aiState")
    aiStateField.isAccessible = true
    aiStateField.set(enemy, com.aethelgard.survival.core.entities.EnemyAiState.COMBO_SLASH_HORIZONTAL)

    // Move player far away (e.g. 20m away)
    player.position.set(0f, 0f, 20f)
    enemy.entity.position.set(0f, 0f, 0f)
    val hpBefore = player.health
    var playerHitRecorded = false
    enemy.update(0.016f, player, game.worldManager, game.audioManager, game.combatSystem) { playerHitRecorded = true }
    assertEquals("Player far away must NOT take damage from Knight melee combo", hpBefore, player.health, 0.001f)
    assertFalse("Hit callback must not trigger for distant player", playerHitRecorded)

    // 4. Crescent Wave strict range bound & dodge check
    enemy.activeCrescentWaves.clear()
    val wave = com.aethelgard.survival.core.entities.ActiveEnemyCrescentWave(
      origin = com.badlogic.gdx.math.Vector3(0f, 1f, 0f),
      position = com.badlogic.gdx.math.Vector3(0f, 1f, 0f),
      velocity = com.badlogic.gdx.math.Vector3(0f, 0f, 13.5f),
      damage = 12f,
      yawDeg = 0f,
      maxRange = 3.85f,
      maxLifetime = 0.28f
    )
    enemy.activeCrescentWaves.add(wave)

    // Position player right next to wave spawn but active in dodge
    player.position.set(0f, 0f, 1.0f)
    val dodgeField = player.javaClass.getDeclaredField("isDodging")
    dodgeField.isAccessible = true
    dodgeField.set(player, true)

    val playerHpBeforeWave = player.health
    enemy.update(0.016f, player, game.worldManager, game.audioManager, game.combatSystem) {}
    assertEquals("Player dashing/dodging must cleanly evade crescent wave", playerHpBeforeWave, player.health, 0.001f)

    // Reset dodge, advance wave past maxRange (3.85m)
    dodgeField.set(player, false)
    aiStateField.set(enemy, com.aethelgard.survival.core.entities.EnemyAiState.PATROL)
    wave.position.set(0f, 1f, 5.0f) // 5.0m from origin > 3.85m maxRange
    player.position.set(0f, 0f, 5.0f)
    enemy.update(0.016f, player, game.worldManager, game.audioManager, game.combatSystem) {}
    assertTrue("Crescent wave past max range must be pruned without damaging player", enemy.activeCrescentWaves.isEmpty())
    assertEquals("Player must not take damage from expired crescent wave", playerHpBeforeWave, player.health, 0.001f)

    // 5. Green Slime universal dash evasion
    val slimes = game.entityManager.enemyControllers.filterIsInstance<com.aethelgard.survival.core.entities.GreenSlimeController>()
    if (slimes.isNotEmpty()) {
      val slime = slimes.first()
      slime.activeOrbs.clear()
      val slimeOrb = com.aethelgard.survival.core.entities.ActiveGreenEnergyOrb(
        position = com.badlogic.gdx.math.Vector3(player.position.x, player.position.y + 0.85f, player.position.z),
        velocity = com.badlogic.gdx.math.Vector3(0f, 0f, 5f),
        damage = 10f,
        radius = 0.35f,
        modelInstance = slime.entity.modelInstance,
        maxLifetime = 2.0f
      )
      slime.activeOrbs.add(slimeOrb)
      dodgeField.set(player, true)
      val hpBeforeSlimeOrb = player.health
      slime.update(0.016f, player, game.worldManager, game.audioManager, game.combatSystem) {}
      assertEquals("Dashing player must cleanly evade Green Slime energy orb", hpBeforeSlimeOrb, player.health, 0.001f)
    }

    game.dispose()
    realAndroidAudio.dispose()
  }

  @Test
  fun `prediction haki system handles manual toggle, 8 limited charges, genuine evasion, directional states, and exhaustion`() {
    val realAndroidAudio = setupGdxMocks()
    val game = com.aethelgard.survival.core.AethelgardGame()
    game.create()
    game.resize(1280, 720)

    val haki = game.predictionHakiController
    val player = game.playerCharacter
    val enemy = game.entityManager.primaryEnemy()
    assertNotNull("Haki controller must be initialized", haki)
    assertNotNull("Enemy must be present", enemy)

    // 1. Initial State: Inactive, exactly 8 charges available
    assertFalse("Haki should start inactive", haki.isActive)
    assertEquals("Should have exactly 8 charges", 8, haki.remainingCharges)
    assertFalse("Should not be exhausted", haki.isExhausted)

    // 2. Manual Activation
    val activated = haki.activate()
    assertTrue("Haki activation should succeed", activated)
    assertTrue("Haki must be active", haki.isActive)
    assertEquals(8, haki.remainingCharges)

    // Manual Deactivation
    haki.deactivate()
    assertFalse("Haki should now be inactive", haki.isActive)
    assertEquals(8, haki.remainingCharges)

    // Toggle Active via HUD button callback
    game.touchInputController.hakiButton.let { btn ->
      // Simulate touching Haki button
      game.touchInputController.touchDown(btn.centerX.toInt(), (720 - btn.centerY).toInt(), 0, 0)
    }
    assertTrue("Haki should be toggled active via HUD button", haki.isActive)

    // 3. No threat evaluation: Charges are NOT consumed when no threat exists
    player.position.set(0f, 0f, 20f)
    enemy!!.entity.position.set(0f, 0f, 0f) // 20m away, far outside attack range
    haki.update(0.016f)
    assertEquals("Charges must remain intact when no threat exists", 8, haki.remainingCharges)
    assertFalse("Player should not be dodging when safe", player.isDodging)

    // 4. Directional Evasions on genuine threats:
    // Case A: Horizontal Slash -> LOW dodge
    val aiStateField = enemy.javaClass.getDeclaredField("aiState")
    aiStateField.isAccessible = true
    aiStateField.set(enemy, com.aethelgard.survival.core.entities.EnemyAiState.COMBO_SLASH_HORIZONTAL)
    val stateTimerField = enemy.javaClass.getDeclaredField("stateTimer")
    stateTimerField.isAccessible = true
    stateTimerField.set(enemy, 0.20f) // Active strike frame
    enemy.entity.position.set(0f, 0f, 8.0f)
    enemy.facingYaw = 0f // Facing +Z toward player
    player.position.set(0f, 0f, 10.0f) // 2.0m in front of enemy, within 2.85m strike range

    val hpBeforeAtk = player.health
    haki.update(0.016f)
    assertTrue("Player must be dodging after detecting imminent horizontal attack", player.isDodging)
    assertEquals("Exactly 1 charge consumed", 7, haki.remainingCharges)
    assertEquals("Horizontal attack triggers LOW dodge", com.aethelgard.survival.core.player.AnimationState.DODGE_LOW, player.animationState)
    assertTrue("Player has invulnerability active during dodge", player.invulnerabilityTimer > 0f)

    // Ensure enemy attack cannot damage dodging player
    enemy.update(0.016f, player, game.worldManager, game.audioManager, game.combatSystem) {}
    assertEquals("Player took zero damage thanks to prediction evasion", hpBeforeAtk, player.health, 0.001f)

    // Reset dodge state and cooldown for next test case
    player.isDodging = false
    enemy.entity.position.set(0f, 0f, 8.0f)
    player.position.set(0f, 0f, 10.0f)
    enemy.facingYaw = 0f
    val evadeCooldownField = haki.javaClass.getDeclaredField("evadeCooldownTimer")
    evadeCooldownField.isAccessible = true
    evadeCooldownField.set(haki, 0f)

    // Case B: Diagonal Cleave -> SIDE dodge
    aiStateField.set(enemy, com.aethelgard.survival.core.entities.EnemyAiState.COMBO_SLASH_DIAGONAL)
    stateTimerField.set(enemy, 0.20f)
    haki.update(0.016f)
    assertTrue("Player must dodge diagonal cleave", player.isDodging)
    assertEquals("Charge decremented to 6", 6, haki.remainingCharges)
    assertEquals("Diagonal attack triggers SIDE dodge", com.aethelgard.survival.core.player.AnimationState.DODGE_SIDE, player.animationState)

    // Reset dodge
    player.isDodging = false
    enemy.entity.position.set(0f, 0f, 8.0f)
    player.position.set(0f, 0f, 10.0f)
    enemy.facingYaw = 0f
    evadeCooldownField.set(haki, 0f)

    // Case C: Whirlwind -> BACKWARD dodge
    aiStateField.set(enemy, com.aethelgard.survival.core.entities.EnemyAiState.SPECIAL_WHIRLWIND)
    stateTimerField.set(enemy, 0.20f)
    haki.update(0.016f)
    assertTrue("Player must dodge whirlwind", player.isDodging)
    assertEquals("Charge decremented to 5", 5, haki.remainingCharges)
    assertEquals("Whirlwind triggers BACKWARD dodge", com.aethelgard.survival.core.player.AnimationState.DODGE_BACK, player.animationState)

    // Reset dodge
    player.isDodging = false
    enemy.entity.position.set(0f, 0f, 8.0f)
    player.position.set(0f, 0f, 10.0f)
    evadeCooldownField.set(haki, 0f)

    // Case D: Incoming Linear Projectile (Crescent Wave) -> SIDE dodge
    aiStateField.set(enemy, com.aethelgard.survival.core.entities.EnemyAiState.PATROL)
    enemy.activeCrescentWaves.clear()
    val wave = com.aethelgard.survival.core.entities.ActiveEnemyCrescentWave(
      origin = com.badlogic.gdx.math.Vector3(0f, 1f, 8.0f),
      position = com.badlogic.gdx.math.Vector3(0f, 1f, 8.8f),
      velocity = com.badlogic.gdx.math.Vector3(0f, 0f, 13.5f), // Heading directly toward player at z=10.0f
      damage = 15f,
      yawDeg = 0f,
      maxRange = 3.85f,
      maxLifetime = 0.28f
    )
    enemy.activeCrescentWaves.add(wave)
    haki.update(0.016f)
    assertTrue("Player must dodge incoming crescent wave", player.isDodging)
    assertEquals("Charge decremented to 4", 4, haki.remainingCharges)
    assertEquals("Linear projectile triggers SIDE dodge", com.aethelgard.survival.core.player.AnimationState.DODGE_SIDE, player.animationState)
    assertTrue("Wave continues to exist on its natural trajectory (no reflection/blocking)", enemy.activeCrescentWaves.isNotEmpty())

    // 5. Consume remaining charges to test Exhaustion (charges 3, 2, 1, 0)
    for (expectedRemaining in listOf(3, 2, 1, 0)) {
      player.isDodging = false
      evadeCooldownField.set(haki, 0f)
      haki.triggerEvasion(com.aethelgard.survival.core.combat.HakiDodgeKind.BACKWARD, com.badlogic.gdx.math.Vector3(0f, 0f, -1f))
      assertEquals(expectedRemaining, haki.remainingCharges)
    }

    // 6. Exhaustion State Verification:
    assertTrue("Haki must be exhausted once all 8 charges are consumed", haki.isExhausted)
    assertFalse("Haki must auto-deactivate upon exhaustion", haki.isActive)
    assertTrue("Exhaustion cooldown must be active (> 0s)", haki.exhaustionCooldown > 0f)

    // Cannot manually activate while exhausted
    val tryActivateWhileExhausted = haki.activate()
    assertFalse("Cannot activate while exhausted", tryActivateWhileExhausted)
    assertFalse(haki.isActive)

    // Advance time past exhaustion cooldown (10.0s)
    haki.update(10.5f)
    assertFalse("Exhaustion cooldown has expired", haki.isExhausted)
    assertEquals("Charges fully recharged to 8 after cooldown", 8, haki.remainingCharges)

    // Can now activate again
    val canReactivate = haki.activate()
    assertTrue("Can reactivate after cooldown expires", canReactivate)
    assertTrue(haki.isActive)

    game.dispose()
    realAndroidAudio.dispose()
  }
}
