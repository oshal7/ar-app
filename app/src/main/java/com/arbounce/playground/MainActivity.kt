package com.arbounce.playground

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.arbounce.playground.ar.ArRenderer
import com.arbounce.playground.ar.CameraPermissionHelper
import com.arbounce.playground.ar.GameEvents
import com.arbounce.playground.data.PlayStats
import com.arbounce.playground.data.StatsRepository
import com.arbounce.playground.databinding.ActivityArBinding
import com.arbounce.playground.databinding.ActivityFallbackBinding
import com.arbounce.playground.ui.Feedback
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Single entry point. Picks AR mode when ARCore is available, otherwise the non-AR
 * tilt-and-bounce fallback. Both modes write to the same lifetime [StatsRepository].
 */
class MainActivity : AppCompatActivity() {

    private enum class Mode { UNDECIDED, AR, FALLBACK }

    private var mode = Mode.UNDECIDED

    private lateinit var stats: StatsRepository
    private lateinit var feedback: Feedback

    // AR mode
    private var arBinding: ActivityArBinding? = null
    private var glView: GLSurfaceView? = null
    private var renderer: ArRenderer? = null
    private var session: Session? = null
    private var installRequested = false

    // Fallback mode
    private var fallbackBinding: ActivityFallbackBinding? = null

    private var flushJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stats = StatsRepository(this, lifecycleScope)
        feedback = Feedback(this)
        decideMode()
    }

    /** ARCore availability can be transient; re-check until it settles. */
    private fun decideMode() {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (availability.isTransient) {
            handler.postDelayed({ decideMode() }, 200)
            return
        }
        if (availability.isSupported) setupArMode() else setupFallbackMode()
    }

    // ---------------- AR mode ----------------
    private fun setupArMode() {
        mode = Mode.AR
        val binding = ActivityArBinding.inflate(layoutInflater)
        arBinding = binding
        setContentView(binding.root)

        val r = ArRenderer(this, gameEvents)
        renderer = r

        binding.surfaceView.apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(r)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> r.startAim()
                    MotionEvent.ACTION_UP -> { r.releaseThrow(); feedback.launch(); v.performClick() }
                    MotionEvent.ACTION_CANCEL -> r.cancelAim()
                }
                true
            }
        }
        glView = binding.surfaceView

        binding.btnReset.setOnClickListener { r.requestReset() }
        binding.btnBall.setOnClickListener { /* ball is the active entity */ }
        val soonMsg = "${getString(R.string.btn_plane)} / ${getString(R.string.btn_bird)}: ${getString(R.string.soon)}"
        binding.btnPlane.setOnClickListener { toast(soonMsg) }
        binding.btnBird.setOnClickListener { toast(soonMsg) }

        observeStats { binding.tvStats.text = it }
    }

    // ---------------- Fallback mode ----------------
    private fun setupFallbackMode() {
        mode = Mode.FALLBACK
        val binding = ActivityFallbackBinding.inflate(layoutInflater)
        fallbackBinding = binding
        setContentView(binding.root)

        binding.fallbackView.onThrow = { stats.onThrow("ball2d") }
        binding.fallbackView.onBounce = { impact ->
            stats.onBounce()
            feedback.bounce(impact)
        }
        observeStats { binding.tvStats.text = it }
    }

    private fun observeStats(apply: (String) -> Unit) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                stats.stats.collectLatest { s -> apply(formatStats(s)) }
            }
        }
    }

    private fun formatStats(s: PlayStats): String =
        "Throws: ${s.totalThrows}   Bounces: ${s.totalBounces}   Best air: ${"%.1f".format(s.bestAirTimeMs / 1000f)}s"

    // ---------------- Lifecycle ----------------
    override fun onResume() {
        super.onResume()
        when (mode) {
            Mode.AR -> resumeAr()
            Mode.FALLBACK -> fallbackBinding?.fallbackView?.start()
            Mode.UNDECIDED -> {}
        }
        startPeriodicFlush()
    }

    private fun resumeAr() {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this)
            return
        }
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                }
                val s = Session(this)
                s.configure(
                    Config(s).apply {
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = Config.FocusMode.AUTO
                        depthMode = Config.DepthMode.DISABLED
                        lightEstimationMode = Config.LightEstimationMode.DISABLED
                    },
                )
                session = s
                renderer?.session = s
            } catch (e: UnavailableException) {
                toast(getString(R.string.ar_unavailable))
                switchToFallback()
                return
            } catch (e: Exception) {
                toast(getString(R.string.ar_unavailable))
                switchToFallback()
                return
            }
        }
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            session = null
            renderer?.session = null
            toast(getString(R.string.camera_needed))
            return
        }
        glView?.onResume()
        renderer?.onResume()
    }

    override fun onPause() {
        super.onPause()
        when (mode) {
            Mode.AR -> {
                renderer?.onPause()
                glView?.onPause()
                session?.pause()
            }
            Mode.FALLBACK -> fallbackBinding?.fallbackView?.stop()
            Mode.UNDECIDED -> {}
        }
        stopPeriodicFlush()
        lifecycleScope.launch { stats.flush() }
    }

    override fun onDestroy() {
        session?.close()
        session = null
        feedback.release()
        super.onDestroy()
    }

    private fun switchToFallback() {
        session?.close()
        session = null
        renderer = null
        glView = null
        arBinding = null
        setupFallbackMode()
        fallbackBinding?.fallbackView?.start()
    }

    private fun startPeriodicFlush() {
        if (flushJob?.isActive == true) return
        flushJob = lifecycleScope.launch {
            while (true) {
                delay(4000)
                stats.flush()
            }
        }
    }

    private fun stopPeriodicFlush() {
        flushJob?.cancel()
        flushJob = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CameraPermissionHelper.CAMERA_PERMISSION_CODE) {
            if (CameraPermissionHelper.hasCameraPermission(this)) {
                resumeAr()
            } else {
                toast(getString(R.string.camera_needed))
                switchToFallback()
            }
        }
    }

    private val gameEvents = object : GameEvents {
        override fun onThrow() {
            stats.onThrow("ball")
        }

        override fun onBounce(impactSpeed: Float, airMs: Long) {
            feedback.bounce(impactSpeed)
            stats.onBounce()
            stats.onAirTime(airMs)
        }

        override fun onTracking(tracking: Boolean, planeCount: Int) {
            val hint = when {
                !tracking -> getString(R.string.hud_scanning)
                planeCount == 0 -> getString(R.string.hud_scanning)
                else -> getString(R.string.hud_ready)
            }
            runOnUiThread { arBinding?.tvHint?.text = hint }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
