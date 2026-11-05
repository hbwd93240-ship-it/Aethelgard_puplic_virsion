package com.example

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aethelgard.survival.core.AethelgardGame
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Native Android launcher for AethelgardSurvival.
 * Hosts the platform-independent LibGDX 3D OpenGL ES engine from the :core module
 * in full-screen landscape orientation.
 *
 * Includes built-in crash diagnostics and safe exception recovery so any
 * initialization or driver failure logs to Logcat under tag "AethelgardCrash"
 * and displays actionable diagnostic information instead of silently terminating.
 */
class MainActivity : AndroidApplication() {

    private val logTag = "AethelgardCrash"

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set solid black window background immediately so WindowManager doesn't stall waiting for surface sync
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Configure full-screen display cutout for modern Android 14/15/16 devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Install process-wide uncaught exception handler
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val trace = sw.toString()
            Log.e(logTag, "FATAL UNCAUGHT EXCEPTION on thread [${thread.name}]: $trace")
            saveCrashLog("Uncaught on ${thread.name}", trace)
            previousHandler?.uncaughtException(thread, throwable)
        }

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useWakelock = true
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useGL30 = false
            r = 8
            g = 8
            b = 8
            a = 0
            depth = 16
            stencil = 0
            numSamples = 0
        }

        try {
            Log.i(logTag, "Initializing AethelgardGame with LibGDX...")
            val game = AethelgardGame(deferInitForSurfaceSync = true) { stage, throwable ->
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val trace = sw.toString()
                Log.e(logTag, "AethelgardGame error at $stage:\n$trace")
                saveCrashLog(stage, trace)
            }
            initialize(game, config)
            Log.i(logTag, "LibGDX initialize() finished successfully.")
        } catch (t: Throwable) {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val trace = sw.toString()
            Log.e(logTag, "CRITICAL ERROR during MainActivity.initialize(): $trace")
            saveCrashLog("MainActivity.initialize()", trace)
            showCrashUi(trace)
        }
    }

    private fun saveCrashLog(stage: String, trace: String) {
        try {
            val file = File(filesDir, "aethelgard_last_crash.txt")
            file.writeText("Stage: $stage\nSDK: ${Build.VERSION.SDK_INT}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\n\n$trace")
        } catch (e: Exception) {
            Log.e(logTag, "Failed to write crash log file", e)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showCrashUi(stackTrace: String) {
        runOnUiThread {
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                setBackgroundColor(0xFF10141E.toInt())
            }

            val titleView = TextView(this).apply {
                text = "AethelgardSurvival — Diagnostic Safe Mode"
                textSize = 20f
                setTextColor(0xFFFF5252.toInt())
                setPadding(0, 0, 0, 16)
            }
            root.addView(titleView)

            val infoView = TextView(this).apply {
                text = "An exception prevented normal 3D startup. The stack trace below was logged to Logcat (tag: AethelgardCrash) and saved to internal storage:"
                textSize = 14f
                setTextColor(0xFFE2E8F0.toInt())
                setPadding(0, 0, 0, 24)
            }
            root.addView(infoView)

            val scrollView = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            val traceView = TextView(this).apply {
                text = stackTrace
                textSize = 11f
                setTextColor(0xFFFFCDD2.toInt())
                setTextIsSelectable(true)
            }
            scrollView.addView(traceView)
            root.addView(scrollView)

            val restartButton = Button(this).apply {
                text = "Restart Game"
                setOnClickListener {
                    recreate()
                }
            }
            root.addView(restartButton)

            setContentView(root)
        }
    }
}
