package com.aethelgard.survival.core.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural audio manager for AethelgardSurvival.
 * Synthesizes 16-bit PCM WAV sound effects directly in code (footsteps, jump whoosh,
 * landing thud, sprint burst, melee swings, heavy hits, energy charge, magic bolts,
 * charged energy blasts, dodge dashes, enemy defeat, and target lock-on/unlock chimes)
 * so zero external audio files are required.
 */
class AudioManager : Disposable {

    var isAudioEnabled: Boolean = true
    @Volatile
    private var initialized: Boolean = false

    private var footstepSound: Sound? = null
    private var jumpSound: Sound? = null
    private var landSound: Sound? = null
    private var sprintSound: Sound? = null
    private var uiClickSound: Sound? = null
    private var resetChimeSound: Sound? = null

    // Milestone 2, 3 & 4 combat, sword skill, martial arts, & lock-on sounds
    private var meleeSwingSound: Sound? = null
    private var kickWhooshSound: Sound? = null
    private var fistHeavyRoarSound: Sound? = null
    private var fistSonicImpactSound: Sound? = null
    private var swordSlashSound: Sound? = null
    private var swordHeavyWaveSound: Sound? = null
    private var flashStepSound: Sound? = null
    private var swordCleaveHitSound: Sound? = null
    private var meleeHitSound: Sound? = null
    private var heavyHitSound: Sound? = null
    private var magicBoltSound: Sound? = null
    private var energyChargeSound: Sound? = null
    private var energyBlastSound: Sound? = null
    private var dodgeDashSound: Sound? = null
    private var enemyDefeatSound: Sound? = null
    private var lockOnSound: Sound? = null
    private var hakiActivateSound: Sound? = null
    private var hakiEvadeSound: Sound? = null

    private var footstepPitchToggle = false
    private var comboPitchStep = 0

    fun initialize(async: Boolean = false) {
        if (async) {
            Thread({
                loadAllSounds()
            }, "AethelgardAudioInit").apply {
                isDaemon = true
                start()
            }
        } else {
            loadAllSounds()
        }
    }

    private fun loadAllSounds() {
        try {
            val sampleRate = 22050
            footstepSound = createSoundFromPcm("sfx_footstep.wav", sampleRate) { generateFootstepSamples(sampleRate) }
            jumpSound = createSoundFromPcm("sfx_jump.wav", sampleRate) { generateJumpSamples(sampleRate) }
            landSound = createSoundFromPcm("sfx_land.wav", sampleRate) { generateLandSamples(sampleRate) }
            sprintSound = createSoundFromPcm("sfx_sprint.wav", sampleRate) { generateSprintSamples(sampleRate) }
            uiClickSound = createSoundFromPcm("sfx_ui.wav", sampleRate) { generateUiChimeSamples(sampleRate) }
            resetChimeSound = createSoundFromPcm("sfx_reset.wav", sampleRate) { generateResetChordSamples(sampleRate) }

            meleeSwingSound = createSoundFromPcm("sfx_melee_swing.wav", sampleRate) { generateMeleeSwingSamples(sampleRate) }
            kickWhooshSound = createSoundFromPcm("sfx_kick_whoosh.wav", sampleRate) { generateKickWhooshSamples(sampleRate) }
            fistHeavyRoarSound = createSoundFromPcm("sfx_fist_heavy.wav", sampleRate) { generateFistHeavyRoarSamples(sampleRate) }
            fistSonicImpactSound = createSoundFromPcm("sfx_fist_sonic_hit.wav", sampleRate) { generateFistSonicImpactSamples(sampleRate) }
            swordSlashSound = createSoundFromPcm("sfx_sword_slash.wav", sampleRate) { generateSwordSlashSamples(sampleRate) }
            swordHeavyWaveSound = createSoundFromPcm("sfx_sword_heavy.wav", sampleRate) { generateSwordHeavyWaveSamples(sampleRate) }
            flashStepSound = createSoundFromPcm("sfx_flash_step.wav", sampleRate) { generateFlashStepSamples(sampleRate) }
            swordCleaveHitSound = createSoundFromPcm("sfx_sword_hit.wav", sampleRate) { generateSwordImpactSamples(sampleRate) }
            meleeHitSound = createSoundFromPcm("sfx_melee_hit.wav", sampleRate) { generateMeleeHitSamples(sampleRate, isHeavy = false) }
            heavyHitSound = createSoundFromPcm("sfx_heavy_hit.wav", sampleRate) { generateMeleeHitSamples(sampleRate, isHeavy = true) }
            magicBoltSound = createSoundFromPcm("sfx_magic_bolt.wav", sampleRate) { generateMagicBoltSamples(sampleRate) }
            energyChargeSound = createSoundFromPcm("sfx_energy_charge.wav", sampleRate) { generateEnergyChargeSamples(sampleRate) }
            energyBlastSound = createSoundFromPcm("sfx_energy_blast.wav", sampleRate) { generateEnergyBlastSamples(sampleRate) }
            dodgeDashSound = createSoundFromPcm("sfx_dodge_dash.wav", sampleRate) { generateDodgeDashSamples(sampleRate) }
            enemyDefeatSound = createSoundFromPcm("sfx_enemy_defeat.wav", sampleRate) { generateEnemyDefeatSamples(sampleRate) }
            lockOnSound = createSoundFromPcm("sfx_lock_on.wav", sampleRate) { generateLockOnSamples(sampleRate) }
            hakiActivateSound = createSoundFromPcm("sfx_haki_activate.wav", sampleRate) { generateHakiActivateSamples(sampleRate) }
            hakiEvadeSound = createSoundFromPcm("sfx_haki_evade.wav", sampleRate) { generateHakiEvadeSamples(sampleRate) }

            initialized = true
        } catch (_: Throwable) {
            initialized = false
        }
    }

    fun playFootstep(isSprinting: Boolean) {
        if (!isAudioEnabled || !initialized) return
        try {
            footstepPitchToggle = !footstepPitchToggle
            val basePitch = if (isSprinting) 1.18f else 0.96f
            val pitch = basePitch + if (footstepPitchToggle) 0.06f else -0.04f
            footstepSound?.play(if (isSprinting) 0.32f else 0.22f, pitch, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playJump() {
        if (!isAudioEnabled || !initialized) return
        try {
            jumpSound?.play(0.42f, 1.0f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playLand() {
        if (!isAudioEnabled || !initialized) return
        try {
            landSound?.play(0.35f, 0.95f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playSprintToggle(active: Boolean) {
        if (!isAudioEnabled || !initialized) return
        try {
            sprintSound?.play(0.38f, if (active) 1.12f else 0.86f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playUiClick() {
        if (!isAudioEnabled || !initialized) return
        try {
            uiClickSound?.play(0.40f, 1.05f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playResetChime() {
        if (!isAudioEnabled || !initialized) return
        try {
            resetChimeSound?.play(0.48f, 1.0f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playMeleeSwing(isSword: Boolean, isHeavy: Boolean) {
        if (!isAudioEnabled || !initialized) return
        try {
            comboPitchStep = (comboPitchStep + 1) % 3
            val pitch = (if (isHeavy) 0.84f else 0.96f + comboPitchStep * 0.07f)
            if (isSword) {
                swordSlashSound?.play(0.46f, pitch, 0f)
            } else {
                meleeSwingSound?.play(0.44f, pitch, 0f)
            }
        } catch (_: Throwable) {
        }
    }

    fun playFistComboSwing(step: Int, isKick: Boolean) {
        if (!isAudioEnabled || !initialized) return
        try {
            val pitch = when (step) {
                1 -> 1.08f // Right Jab
                2 -> 0.96f // Left Cross
                3 -> 0.92f // Side Kick
                4 -> 1.04f // Spinning Roundhouse
                5 -> 1.14f // Rising High Kick
                else -> 0.86f // Finisher
            }
            if (isKick) {
                kickWhooshSound?.play(0.52f, pitch, 0f)
            } else {
                meleeSwingSound?.play(0.48f, pitch, 0f)
            }
        } catch (_: Throwable) {
        }
    }

    fun playFistHeavySkill(skillIndex: Int) {
        if (!isAudioEnabled || !initialized) return
        try {
            val pitch = when (skillIndex) {
                0 -> 0.90f // Dragon Haymaker
                1 -> 1.06f // Thunder Breaker
                else -> 0.82f // Meteor Strike
            }
            fistHeavyRoarSound?.play(0.62f, pitch, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playFistHitImpact(isKick: Boolean, isHeavy: Boolean, isFinisher: Boolean) {
        if (!isAudioEnabled || !initialized) return
        try {
            when {
                isHeavy || isFinisher -> {
                    fistSonicImpactSound?.play(0.66f, if (isHeavy) 0.88f else 0.96f, 0f)
                }
                isKick -> {
                    heavyHitSound?.play(0.56f, 1.06f, 0f)
                }
                else -> {
                    meleeHitSound?.play(0.50f, 1.04f, 0f)
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun playSwordComboSlash(step: Int) {
        if (!isAudioEnabled || !initialized) return
        try {
            val pitch = when (step) {
                1 -> 1.00f
                2 -> 1.12f
                3 -> 0.92f
                4 -> 1.18f
                else -> 1.28f
            }
            swordSlashSound?.play(0.50f, pitch, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playSwordHeavySkill(skillIndex: Int) {
        if (!isAudioEnabled || !initialized) return
        try {
            val pitch = when (skillIndex) {
                0 -> 0.92f // Inferno Crescent
                1 -> 1.08f // Dragon Tempest
                else -> 0.84f // Aerial Sword Strike
            }
            swordHeavyWaveSound?.play(0.62f, pitch, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playFlashStep() {
        if (!isAudioEnabled || !initialized) return
        try {
            flashStepSound?.play(0.54f, 1.06f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playSwordHitImpact(isHeavy: Boolean) {
        if (!isAudioEnabled || !initialized) return
        try {
            swordCleaveHitSound?.play(if (isHeavy) 0.65f else 0.52f, if (isHeavy) 0.88f else 1.08f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playMeleeHit(isHeavy: Boolean) {
        if (!isAudioEnabled || !initialized) return
        try {
            if (isHeavy) {
                heavyHitSound?.play(0.58f, 0.95f, 0f)
            } else {
                meleeHitSound?.play(0.48f, 1.0f + (comboPitchStep * 0.05f), 0f)
            }
        } catch (_: Throwable) {
        }
    }

    fun playMagicBoltCast() {
        if (!isAudioEnabled || !initialized) return
        try {
            magicBoltSound?.play(0.46f, 1.04f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playEnergyChargeStart() {
        if (!isAudioEnabled || !initialized) return
        try {
            energyChargeSound?.play(0.40f, 1.0f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playChargedBlastRelease() {
        if (!isAudioEnabled || !initialized) return
        try {
            energyBlastSound?.play(0.60f, 0.96f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playUltimateAwaken() {
        if (!isAudioEnabled || !initialized) return
        try {
            energyChargeSound?.play(0.68f, 0.78f, 0f)
            flashStepSound?.play(0.48f, 0.85f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playUltimateMergeFlash() {
        if (!isAudioEnabled || !initialized) return
        try {
            fistSonicImpactSound?.play(0.75f, 0.76f, 0f)
            energyChargeSound?.play(0.70f, 1.24f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playUltimateAnnihilationBeam() {
        if (!isAudioEnabled || !initialized) return
        try {
            energyBlastSound?.play(0.88f, 0.72f, 0f)
            swordHeavyWaveSound?.play(0.78f, 0.68f, 0f)
            fistHeavyRoarSound?.play(0.76f, 0.74f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playDodgeDash() {
        if (!isAudioEnabled || !initialized) return
        try {
            dodgeDashSound?.play(0.45f, 1.08f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playWeaponToggle(swordEquipped: Boolean) {
        if (!isAudioEnabled || !initialized) return
        try {
            swordSlashSound?.play(0.38f, if (swordEquipped) 1.25f else 0.85f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playEnemyDefeated() {
        if (!isAudioEnabled || !initialized) return
        try {
            enemyDefeatSound?.play(0.55f, 0.92f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playLockOnToggle(locked: Boolean) {
        if (!isAudioEnabled || !initialized) return
        try {
            lockOnSound?.play(0.46f, if (locked) 1.15f else 0.82f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playHakiActivate(active: Boolean) {
        if (!isAudioEnabled || !initialized) return
        try {
            hakiActivateSound?.play(0.55f, if (active) 1.15f else 0.85f, 0f)
        } catch (_: Throwable) {
        }
    }

    fun playHakiEvade() {
        if (!isAudioEnabled || !initialized) return
        try {
            hakiEvadeSound?.play(0.62f, 1.08f, 0f)
        } catch (_: Throwable) {
        }
    }

    private fun createSoundFromPcm(fileName: String, sampleRate: Int, generator: () -> ShortArray): Sound? {
        val files = Gdx.files ?: return null
        val audio = Gdx.audio ?: return null
        val handle = files.local("procedural_sfx/$fileName") ?: return null
        if (!handle.exists() || handle.length() <= 44L) {
            val wavBytes = encodeWav16Mono(sampleRate, generator())
            handle.writeBytes(wavBytes, false)
        }
        return audio.newSound(handle)
    }

    private fun generateFootstepSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.09f).toInt()
        val out = ShortArray(count)
        val rng = Random(101)
        var filtered = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 42f)
            val noise = (rng.nextFloat() * 2f - 1f)
            filtered = filtered * 0.78f + noise * 0.22f
            val thump = sin(2.0 * PI * 95.0 * t).toFloat() * exp(-t * 55f)
            val sample = ((filtered * 0.65f + thump * 0.55f) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 24000).toInt().toShort()
        }
        return out
    }

    private fun generateJumpSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.22f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / count
            val freq = 190.0 + progress * 340.0
            phase += 2.0 * PI * freq / sampleRate
            val env = sin(progress * PI).toFloat() * exp(-t * 6.5f)
            val wave = (sin(phase) * 0.65 + sin(phase * 1.5) * 0.35).toFloat()
            out[i] = ((wave * env).coerceIn(-1f, 1f) * 22000).toInt().toShort()
        }
        return out
    }

    private fun generateLandSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.14f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val freq = (130.0 - (i.toDouble() / count) * 75.0).coerceAtLeast(45.0)
            phase += 2.0 * PI * freq / sampleRate
            val env = exp(-t * 26f)
            val wave = sin(phase).toFloat()
            out[i] = ((wave * env).coerceIn(-1f, 1f) * 24000).toInt().toShort()
        }
        return out
    }

    private fun generateSprintSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.20f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 14f)
            val c1 = sin(2.0 * PI * 523.25 * t)
            val c2 = sin(2.0 * PI * 659.25 * t)
            val c3 = sin(2.0 * PI * 783.99 * t)
            val sample = (((c1 + c2 + c3) / 3.0).toFloat() * env).coerceIn(-1f, 1f)
            out[i] = (sample * 20000).toInt().toShort()
        }
        return out
    }

    private fun generateUiChimeSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.12f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 24f)
            val wave = (sin(2.0 * PI * 880.0 * t) * 0.7 + sin(2.0 * PI * 1318.5 * t) * 0.3).toFloat()
            out[i] = ((wave * env).coerceIn(-1f, 1f) * 20000).toInt().toShort()
        }
        return out
    }

    private fun generateResetChordSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.42f).toInt()
        val out = ShortArray(count)
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 6.5f)
            var sum = 0.0
            for (n in notes.indices) {
                val delay = n * 0.045f
                if (t >= delay) {
                    val localT = t - delay
                    sum += sin(2.0 * PI * notes[n] * localT) * exp(-localT * 7.0)
                }
            }
            val sample = ((sum / notes.size).toFloat() * env).coerceIn(-1f, 1f)
            out[i] = (sample * 23000).toInt().toShort()
        }
        return out
    }

    private fun generateMeleeSwingSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.15f).toInt()
        val out = ShortArray(count)
        val rng = Random(202)
        var lp = 0f
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            val freq = 320.0 - p * 210.0
            phase += 2.0 * PI * freq / sampleRate
            val noise = rng.nextFloat() * 2f - 1f
            lp = lp * 0.72f + noise * 0.28f
            val env = sin(p * PI).toFloat() * exp(-t * 11f)
            val sample = ((lp * 0.65f + sin(phase).toFloat() * 0.45f) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 23000).toInt().toShort()
        }
        return out
    }

    private fun generateSwordSlashSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.18f).toInt()
        val out = ShortArray(count)
        val rng = Random(303)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            val freq = 980.0 - p * 540.0
            phase += 2.0 * PI * freq / sampleRate
            val ring = sin(2.0 * PI * 1760.0 * t).toFloat() * exp(-t * 18f)
            val air = (rng.nextFloat() * 2f - 1f) * sin(p * PI).toFloat() * 0.35f
            val env = exp(-t * 12f)
            val sample = ((sin(phase).toFloat() * 0.55f + ring * 0.35f + air) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 24000).toInt().toShort()
        }
        return out
    }

    private fun generateMeleeHitSamples(sampleRate: Int, isHeavy: Boolean): ShortArray {
        val dur = if (isHeavy) 0.24f else 0.14f
        val count = (sampleRate * dur).toInt()
        val out = ShortArray(count)
        val rng = Random(404)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val baseFreq = if (isHeavy) 155.0 else 210.0
            val freq = (baseFreq * exp(-t * 18.0)).coerceAtLeast(38.0)
            phase += 2.0 * PI * freq / sampleRate
            val punch = sin(phase).toFloat()
            val crack = (rng.nextFloat() * 2f - 1f) * exp(-t * 36f)
            val env = exp(-t * (if (isHeavy) 11f else 19f))
            val sample = ((punch * 0.75f + crack * 0.45f) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 26000).toInt().toShort()
        }
        return out
    }

    private fun generateMagicBoltSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.22f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            val freq = 880.0 - p * 460.0 + sin(t * 90.0) * 65.0
            phase += 2.0 * PI * freq / sampleRate
            val shimmer = sin(2.0 * PI * 1320.0 * t).toFloat() * 0.35f
            val env = exp(-t * 10.5f)
            val sample = ((sin(phase).toFloat() * 0.65f + shimmer) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 23000).toInt().toShort()
        }
        return out
    }

    private fun generateEnergyChargeSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.35f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            val freq = 220.0 + p * 360.0
            phase += 2.0 * PI * freq / sampleRate
            val env = sin(p * PI).toFloat()
            val hum = (sin(phase) * 0.6 + sin(phase * 2.0) * 0.4).toFloat()
            out[i] = ((hum * env).coerceIn(-1f, 1f) * 21000).toInt().toShort()
        }
        return out
    }

    private fun generateEnergyBlastSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.38f).toInt()
        val out = ShortArray(count)
        val rng = Random(505)
        var phase = 0.0
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            val freq = (420.0 - p * 310.0).coerceAtLeast(65.0)
            phase += 2.0 * PI * freq / sampleRate
            val noise = rng.nextFloat() * 2f - 1f
            lp = lp * 0.82f + noise * 0.18f
            val beam = sin(phase).toFloat() * 0.6f + sin(2.0 * PI * 660.0 * t).toFloat() * 0.25f
            val env = exp(-t * 6.2f)
            val sample = ((beam + lp * 0.45f) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 26000).toInt().toShort()
        }
        return out
    }

    private fun generateDodgeDashSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.16f).toInt()
        val out = ShortArray(count)
        val rng = Random(606)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            val freq = 260.0 + sin(p * PI) * 420.0
            phase += 2.0 * PI * freq / sampleRate
            val wind = (rng.nextFloat() * 2f - 1f) * 0.35f
            val env = sin(p * PI).toFloat() * exp(-t * 8f)
            val sample = ((sin(phase).toFloat() * 0.65f + wind) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 22000).toInt().toShort()
        }
        return out
    }

    private fun generateEnemyDefeatSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.45f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            val freq = (340.0 - p * 240.0).coerceAtLeast(55.0)
            phase += 2.0 * PI * freq / sampleRate
            val env = exp(-t * 5.2f)
            val wave = (sin(phase) * 0.65 + sin(phase * 1.5) * 0.35).toFloat()
            out[i] = ((wave * env).coerceIn(-1f, 1f) * 24000).toInt().toShort()
        }
        return out
    }

    private fun generateLockOnSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.14f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val freq = if (i < count / 2) 740.0 else 1108.73
            val env = exp(-t * 16f)
            val wave = (sin(2.0 * PI * freq * t) * 0.75 + sin(2.0 * PI * freq * 1.5 * t) * 0.25).toFloat()
            out[i] = ((wave * env).coerceIn(-1f, 1f) * 21000).toInt().toShort()
        }
        return out
    }

    private fun generateSwordHeavyWaveSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.36f).toInt()
        val out = ShortArray(count)
        val rng = Random(707)
        var phase = 0.0
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            val freq = 640.0 - p * 440.0 + sin(t * 72.0) * 45.0
            phase += 2.0 * PI * freq / sampleRate
            val bladeChime = sin(2.0 * PI * 1480.0 * t).toFloat() * exp(-t * 14f) * 0.32f
            val roarNoise = rng.nextFloat() * 2f - 1f
            lp = lp * 0.78f + roarNoise * 0.22f
            val env = sin((p * 0.85 + 0.15).coerceAtMost(1.0) * PI).toFloat() * exp(-t * 5.8f)
            val sample = ((sin(phase).toFloat() * 0.55f + bladeChime + lp * 0.48f) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 25500).toInt().toShort()
        }
        return out
    }

    private fun generateFlashStepSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.16f).toInt()
        val out = ShortArray(count)
        val rng = Random(808)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            val freq = 520.0 + sin(p * PI) * 920.0
            phase += 2.0 * PI * freq / sampleRate
            val air = (rng.nextFloat() * 2f - 1f) * sin(p * PI).toFloat() * 0.38f
            val env = exp(-t * 11f)
            val sample = ((sin(phase).toFloat() * 0.62f + air) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 23500).toInt().toShort()
        }
        return out
    }

    private fun generateSwordImpactSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.22f).toInt()
        val out = ShortArray(count)
        val rng = Random(909)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val subFreq = (180.0 * exp(-t * 16.0)).coerceAtLeast(42.0)
            phase += 2.0 * PI * subFreq / sampleRate
            val steelRing = (sin(2.0 * PI * 1920.0 * t) * 0.5 + sin(2.0 * PI * 2640.0 * t) * 0.5).toFloat() * exp(-t * 24f)
            val slice = (rng.nextFloat() * 2f - 1f) * exp(-t * 32f)
            val env = exp(-t * 10.5f)
            val sample = ((sin(phase).toFloat() * 0.68f + steelRing * 0.38f + slice * 0.35f) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 26500).toInt().toShort()
        }
        return out
    }

    private fun generateKickWhooshSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.19f).toInt()
        val out = ShortArray(count)
        val rng = Random(919)
        var phase = 0.0
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            val freq = 260.0 - p * 165.0 + sin(p * PI) * 85.0
            phase += 2.0 * PI * freq / sampleRate
            val noise = rng.nextFloat() * 2f - 1f
            lp = lp * 0.76f + noise * 0.24f
            val env = sin(p * PI).toFloat() * exp(-t * 8.5f)
            val sample = ((sin(phase).toFloat() * 0.58f + lp * 0.52f) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 24500).toInt().toShort()
        }
        return out
    }

    private fun generateFistHeavyRoarSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.32f).toInt()
        val out = ShortArray(count)
        val rng = Random(929)
        var phase = 0.0
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            val freq = (310.0 - p * 220.0).coerceAtLeast(52.0)
            phase += 2.0 * PI * freq / sampleRate
            val chiHarmonic = sin(2.0 * PI * 440.0 * t).toFloat() * exp(-t * 10f) * 0.30f
            val noise = rng.nextFloat() * 2f - 1f
            lp = lp * 0.80f + noise * 0.20f
            val env = sin((p * 0.85 + 0.15).coerceAtMost(1.0) * PI).toFloat() * exp(-t * 6.2f)
            val sample = ((sin(phase).toFloat() * 0.62f + chiHarmonic + lp * 0.45f) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 25500).toInt().toShort()
        }
        return out
    }

    private fun generateFistSonicImpactSamples(sampleRate: Int): ShortArray {
        val count = (sampleRate * 0.26f).toInt()
        val out = ShortArray(count)
        val rng = Random(939)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val subFreq = (195.0 * exp(-t * 14.0)).coerceAtLeast(36.0)
            phase += 2.0 * PI * subFreq / sampleRate
            val shockwaveBoom = sin(phase).toFloat() * 0.78f
            val sonicSnap = sin(2.0 * PI * 860.0 * t).toFloat() * exp(-t * 32f) * 0.35f
            val crack = (rng.nextFloat() * 2f - 1f) * exp(-t * 28f) * 0.40f
            val env = exp(-t * 9.5f)
            val sample = ((shockwaveBoom + sonicSnap + crack) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 26800).toInt().toShort()
        }
        return out
    }

    private fun generateHakiActivateSamples(sampleRate: Int): ShortArray {
        // High-frequency resonant perception ping / awakening tone
        val count = (sampleRate * 0.32f).toInt()
        val out = ShortArray(count)
        var phase1 = 0.0
        var phase2 = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toDouble() / count
            val f1 = 880.0 + p * 340.0
            val f2 = 1760.0 + p * 220.0
            phase1 += 2.0 * PI * f1 / sampleRate
            phase2 += 2.0 * PI * f2 / sampleRate
            val tone = (sin(phase1) * 0.55 + sin(phase2) * 0.35 + sin(phase1 * 0.5) * 0.20).toFloat()
            val env = (sin(p.coerceIn(0.0, 1.0) * PI) * exp(-t * 4.5f)).toFloat()
            val sample = (tone * env).coerceIn(-1f, 1f)
            out[i] = (sample * 23000).toInt().toShort()
        }
        return out
    }

    private fun generateHakiEvadeSamples(sampleRate: Int): ShortArray {
        // Crisp sonic slipstream whoosh with perception snap
        val count = (sampleRate * 0.20f).toInt()
        val out = ShortArray(count)
        val rng = Random(404)
        var phase = 0.0
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toDouble() / count
            val freq = (620.0 - p * 360.0).coerceAtLeast(80.0)
            phase += 2.0 * PI * freq / sampleRate
            val noise = (rng.nextFloat() * 2f - 1f)
            lp = lp * 0.70f + noise * 0.30f
            val snap = sin(2.0 * PI * 1200.0 * t).toFloat() * exp(-t * 45f) * 0.40f
            val whoosh = sin(phase).toFloat() * 0.50f + lp * 0.40f
            val env = exp(-t * 11.0f)
            val sample = ((whoosh + snap) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 25000).toInt().toShort()
        }
        return out
    }

    private fun encodeWav16Mono(sampleRate: Int, samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val totalSize = 36 + dataSize
        val bos = ByteArrayOutputStream(44 + dataSize)

        fun writeAscii(s: String) {
            for (ch in s) bos.write(ch.code)
        }

        fun writeIntLE(v: Int) {
            bos.write(v and 0xFF)
            bos.write((v ushr 8) and 0xFF)
            bos.write((v ushr 16) and 0xFF)
            bos.write((v ushr 24) and 0xFF)
        }

        fun writeShortLE(v: Int) {
            bos.write(v and 0xFF)
            bos.write((v ushr 8) and 0xFF)
        }

        writeAscii("RIFF")
        writeIntLE(totalSize)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeIntLE(16)
        writeShortLE(1)
        writeShortLE(1)
        writeIntLE(sampleRate)
        writeIntLE(sampleRate * 2)
        writeShortLE(2)
        writeShortLE(16)
        writeAscii("data")
        writeIntLE(dataSize)

        for (sample in samples) {
            writeShortLE(sample.toInt())
        }
        return bos.toByteArray()
    }

    override fun dispose() {
        runCatching { footstepSound?.dispose() }
        runCatching { jumpSound?.dispose() }
        runCatching { landSound?.dispose() }
        runCatching { sprintSound?.dispose() }
        runCatching { uiClickSound?.dispose() }
        runCatching { resetChimeSound?.dispose() }
        runCatching { meleeSwingSound?.dispose() }
        runCatching { kickWhooshSound?.dispose() }
        runCatching { fistHeavyRoarSound?.dispose() }
        runCatching { fistSonicImpactSound?.dispose() }
        runCatching { swordSlashSound?.dispose() }
        runCatching { swordHeavyWaveSound?.dispose() }
        runCatching { flashStepSound?.dispose() }
        runCatching { swordCleaveHitSound?.dispose() }
        runCatching { meleeHitSound?.dispose() }
        runCatching { heavyHitSound?.dispose() }
        runCatching { magicBoltSound?.dispose() }
        runCatching { energyChargeSound?.dispose() }
        runCatching { energyBlastSound?.dispose() }
        runCatching { dodgeDashSound?.dispose() }
        runCatching { enemyDefeatSound?.dispose() }
        runCatching { lockOnSound?.dispose() }
        runCatching { hakiActivateSound?.dispose() }
        runCatching { hakiEvadeSound?.dispose() }
    }
}
