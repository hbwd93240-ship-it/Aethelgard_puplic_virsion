package com.aethelgard.survival.core.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 24-Hour Day/Night Cycle and Atmospheric Lighting Engine for Aethelgard Survival.
 *
 * Explicit Specifications:
 * - 1 in-game hour = 90 real-world seconds
 * - 1 full 24-hour cycle = 2,160 real-world seconds (36 minutes)
 * - 12-hour clock with AM / PM formatting (e.g. 06:30 AM, 12:00 PM, 06:30 PM, 12:00 AM)
 * - Two-digit minutes, proper AM/PM transitions at noon and midnight
 * - Day counter increments exactly once per full cycle at midnight (12:00 AM)
 * - Dynamic environmental colors for Dawn, Noon, Sunset, and Night
 */
class DayNightCycle {

    companion object {
        const val SECONDS_PER_IN_GAME_HOUR = 90f
        const val HOURS_PER_DAY = 24f
        const val SECONDS_PER_DAY = SECONDS_PER_IN_GAME_HOUR * HOURS_PER_DAY // 2160 seconds

        // Default start: 08:00 AM on Day 1
        const val DEFAULT_START_SECONDS = 8f * SECONDS_PER_IN_GAME_HOUR // 720 seconds
    }

    /** Accumulated time of day in real-world seconds, range [0f, 2160f). */
    var timeOfDaySeconds: Float = DEFAULT_START_SECONDS

    /** Current in-game day counter, starting at 1. Increments at midnight (12:00 AM). */
    var currentDay: Int = 1

    // Preallocated vectors & colors to avoid GC allocations during render
    val sunDirection = Vector3()
    val moonDirection = Vector3()
    val ambientColor = Color(0.42f, 0.48f, 0.52f, 1f)
    val sunLightColor = Color(1f, 0.95f, 0.85f, 1f)
    val moonLightColor = Color(0.35f, 0.45f, 0.70f, 1f)
    val fogColor = Color(0.55f, 0.68f, 0.82f, 1f)
    val clearColor = Color(0.32f, 0.52f, 0.78f, 1f)
    val skyZenithColor = Color(0.18f, 0.38f, 0.72f, 1f)
    val skyHorizonColor = Color(0.65f, 0.78f, 0.92f, 1f)

    /** Fractional in-game hours [0.0f, 24.0f). */
    val inGameHoursFloat: Float
        get() = (timeOfDaySeconds / SECONDS_PER_DAY) * HOURS_PER_DAY

    /** Current in-game hour in 24-hour range [0, 23]. */
    val inGameHour: Int
        get() = inGameHoursFloat.toInt().coerceIn(0, 23)

    /** Current in-game minute in range [0, 59]. */
    val inGameMinute: Int
        get() {
            val fractional = inGameHoursFloat - inGameHour
            return (fractional * 60f).toInt().coerceIn(0, 59)
        }

    /** Formats the time in strict 12-hour format with AM or PM and two-digit minutes (e.g. "06:30 AM", "12:00 PM"). */
    val formattedTime12Hour: String
        get() {
            val h24 = inGameHour
            val m = inGameMinute
            val isPm = h24 >= 12
            val h12 = when {
                h24 == 0 -> 12
                h24 > 12 -> h24 - 12
                else -> h24
            }
            val amPm = if (isPm) "PM" else "AM"
            return String.format(Locale.US, "%02d:%02d %s", h12, m, amPm)
        }

    /** Formatted Day header (e.g. "DAY 1"). */
    val formattedDay: String
        get() = "DAY $currentDay"

    /** Combined header string for HUD (e.g. "DAY 1  •  08:30 AM"). */
    val combinedTimeAndDayString: String
        get() = "$formattedDay  •  $formattedTime12Hour"

    /** Returns true if current in-game time is considered daytime (06:00 AM to 06:00 PM). */
    val isDaytime: Boolean
        get() = inGameHoursFloat in 6.0f..18.0f

    /** Returns appropriate celestial icon for HUD ("☀" or "🌙"). */
    val celestialIcon: String
        get() = if (inGameHoursFloat in 5.5f..18.5f) "☀" else "🌙"

    /**
     * Advances the clock continuously according to the 90s = 1 hour rule.
     * Increments the day counter at midnight (when wrapping from 2160 back to 0).
     */
    fun update(delta: Float) {
        timeOfDaySeconds += delta
        while (timeOfDaySeconds >= SECONDS_PER_DAY) {
            timeOfDaySeconds -= SECONDS_PER_DAY
            currentDay += 1
        }
        updateAtmosphere()
    }

    /**
     * Computes the dynamic celestial positions and environmental lighting colors.
     */
    fun updateAtmosphere() {
        val h = inGameHoursFloat

        // Sun celestial orbit angle: 0h (midnight) = -90 deg (underground), 6h (dawn) = 0 deg (east horizon),
        // 12h (noon) = 90 deg (zenith overhead), 18h (sunset) = 180 deg (west horizon).
        val sunAngleRad = ((h - 6f) / 24f) * (MathUtils.PI * 2f)
        val sunElev = sin(sunAngleRad)
        val sunAzim = -cos(sunAngleRad)

        // Direction towards the sun
        sunDirection.set(sunAzim * 0.85f, sunElev, 0.35f).nor()
        // Direction towards the moon (opposite to the sun in the celestial dome)
        moonDirection.set(-sunDirection.x, -sunDirection.y, -sunDirection.z).nor()

        // Atmosphere phases:
        // Night: 21:00 - 04:30
        // Dawn: 04:30 - 07:30
        // Day: 07:30 - 17:30
        // Sunset / Dusk: 17:30 - 21:00
        when {
            // Dawn (04:30 - 07:30): soft rose-golden dawn, gradual brightening
            h in 4.5f..7.5f -> {
                val t = (h - 4.5f) / 3.0f // 0 -> 1
                ambientColor.set(
                    MathUtils.lerp(0.18f, 0.44f, t),
                    MathUtils.lerp(0.20f, 0.46f, t),
                    MathUtils.lerp(0.32f, 0.42f, t),
                    1f
                )
                sunLightColor.set(
                    MathUtils.lerp(0.85f, 1.0f, t),
                    MathUtils.lerp(0.55f, 0.94f, t),
                    MathUtils.lerp(0.40f, 0.82f, t),
                    1f
                )
                fogColor.set(
                    MathUtils.lerp(0.16f, 0.58f, t),
                    MathUtils.lerp(0.20f, 0.64f, t),
                    MathUtils.lerp(0.32f, 0.76f, t),
                    1f
                )
                clearColor.set(
                    MathUtils.lerp(0.08f, 0.42f, t),
                    MathUtils.lerp(0.12f, 0.58f, t),
                    MathUtils.lerp(0.24f, 0.82f, t),
                    1f
                )
                skyZenithColor.set(
                    MathUtils.lerp(0.06f, 0.22f, t),
                    MathUtils.lerp(0.10f, 0.46f, t),
                    MathUtils.lerp(0.25f, 0.78f, t),
                    1f
                )
                skyHorizonColor.set(
                    MathUtils.lerp(0.25f, 0.92f, t),
                    MathUtils.lerp(0.20f, 0.75f, t),
                    MathUtils.lerp(0.35f, 0.62f, t),
                    1f
                )
            }
            // Full Day (07:30 - 17:30): clear vibrant daylight, warm sunlight, natural green landscape
            h in 7.5f..17.5f -> {
                val noonFactor = 1f - kotlin.math.abs(h - 12f) / 5.5f
                ambientColor.set(
                    0.44f + noonFactor * 0.08f,
                    0.48f + noonFactor * 0.08f,
                    0.45f + noonFactor * 0.05f,
                    1f
                )
                sunLightColor.set(
                    1.0f,
                    0.96f + noonFactor * 0.04f,
                    0.88f + noonFactor * 0.08f,
                    1f
                )
                fogColor.set(
                    0.58f + noonFactor * 0.06f,
                    0.68f + noonFactor * 0.08f,
                    0.82f + noonFactor * 0.08f,
                    1f
                )
                clearColor.set(
                    0.38f + noonFactor * 0.08f,
                    0.58f + noonFactor * 0.08f,
                    0.86f + noonFactor * 0.08f,
                    1f
                )
                skyZenithColor.set(0.18f, 0.44f, 0.82f, 1f)
                skyHorizonColor.set(0.68f, 0.82f, 0.96f, 1f)
            }
            // Sunset / Dusk (17:30 - 21:00): warm crimson/amber horizon, lengthening shadows
            h in 17.5f..21.0f -> {
                val t = (h - 17.5f) / 3.5f // 0 -> 1
                ambientColor.set(
                    MathUtils.lerp(0.48f, 0.16f, t),
                    MathUtils.lerp(0.46f, 0.18f, t),
                    MathUtils.lerp(0.44f, 0.28f, t),
                    1f
                )
                sunLightColor.set(
                    MathUtils.lerp(1.0f, 0.92f, t),
                    MathUtils.lerp(0.85f, 0.38f, t),
                    MathUtils.lerp(0.65f, 0.18f, t),
                    1f
                )
                fogColor.set(
                    MathUtils.lerp(0.62f, 0.12f, t),
                    MathUtils.lerp(0.65f, 0.14f, t),
                    MathUtils.lerp(0.78f, 0.24f, t),
                    1f
                )
                clearColor.set(
                    MathUtils.lerp(0.44f, 0.07f, t),
                    MathUtils.lerp(0.48f, 0.09f, t),
                    MathUtils.lerp(0.72f, 0.18f, t),
                    1f
                )
                skyZenithColor.set(
                    MathUtils.lerp(0.18f, 0.05f, t),
                    MathUtils.lerp(0.35f, 0.07f, t),
                    MathUtils.lerp(0.68f, 0.18f, t),
                    1f
                )
                skyHorizonColor.set(
                    MathUtils.lerp(0.95f, 0.22f, t),
                    MathUtils.lerp(0.55f, 0.12f, t),
                    MathUtils.lerp(0.25f, 0.24f, t),
                    1f
                )
            }
            // Night (21:00 - 04:30): deep dark starry sky, soft silver-blue moonlight
            else -> {
                ambientColor.set(0.16f, 0.19f, 0.28f, 1f)
                sunLightColor.set(0.10f, 0.12f, 0.20f, 1f)
                moonLightColor.set(0.45f, 0.58f, 0.82f, 1f)
                fogColor.set(0.08f, 0.11f, 0.18f, 1f)
                clearColor.set(0.05f, 0.07f, 0.14f, 1f)
                skyZenithColor.set(0.03f, 0.05f, 0.12f, 1f)
                skyHorizonColor.set(0.08f, 0.12f, 0.22f, 1f)
            }
        }
    }

    fun setTime(hours: Float) {
        val clampedHours = hours % HOURS_PER_DAY
        timeOfDaySeconds = clampedHours * SECONDS_PER_IN_GAME_HOUR
        updateAtmosphere()
    }

    fun serialize(): String {
        return "$timeOfDaySeconds,$currentDay"
    }

    fun deserialize(data: String) {
        if (data.isBlank()) return
        try {
            val parts = data.split(",")
            if (parts.isNotEmpty()) {
                timeOfDaySeconds = parts[0].toFloatOrNull() ?: DEFAULT_START_SECONDS
            }
            if (parts.size >= 2) {
                currentDay = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
            }
            updateAtmosphere()
        } catch (_: Throwable) {
        }
    }
}
