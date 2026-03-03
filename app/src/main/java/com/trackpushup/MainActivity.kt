package com.trackpushup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.trackpushup.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private val pushupCounter = PushupCounter()

    private var sessionActive = false
    private var sessionStartTimeMs = 0L
    private var elapsedSeconds = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (sessionActive) {
                elapsedSeconds = (System.currentTimeMillis() - sessionStartTimeMs) / 1000
                updateTimerDisplay()
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    companion object {
        /** Approximate kcal burned per push-up for an average adult. Actual value
         *  varies with body weight, form, and pace. */
        private const val CALORIES_PER_PUSHUP = 0.32
    }

    // ── Permission launcher ───────────────────────────────────────────────────

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
                binding.tvStatus.text = "Camera permission denied"
            }
        }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnStartSession.setOnClickListener { startSession() }
        binding.btnStopSession.setOnClickListener { stopSession() }
        binding.btnNewSession.setOnClickListener { newSession() }

        showIdleState()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        cameraExecutor.shutdown()
    }

    // ── Session management ────────────────────────────────────────────────────

    private fun startSession() {
        pushupCounter.reset()
        sessionActive = true
        sessionStartTimeMs = System.currentTimeMillis()
        elapsedSeconds = 0L
        timerHandler.post(timerRunnable)
        showActiveState()
        updateUI(0, pushupCounter.feedback)
    }

    private fun stopSession() {
        sessionActive = false
        timerHandler.removeCallbacks(timerRunnable)
        showSummaryState()
    }

    private fun newSession() {
        showIdleState()
    }

    // ── UI state helpers ──────────────────────────────────────────────────────

    private fun showIdleState() {
        binding.summaryOverlay.visibility = View.GONE
        binding.btnStartSession.visibility = View.VISIBLE
        binding.btnStopSession.visibility = View.GONE
        binding.tvTimer.text = getString(R.string.timer_default)
        binding.tvCount.text = "0"
        binding.tvFeedback.text = getString(R.string.session_hint)
    }

    private fun showActiveState() {
        binding.summaryOverlay.visibility = View.GONE
        binding.btnStartSession.visibility = View.GONE
        binding.btnStopSession.visibility = View.VISIBLE
    }

    private fun showSummaryState() {
        val finalCount = pushupCounter.count
        val calories = finalCount * CALORIES_PER_PUSHUP
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60

        binding.tvSummaryPushups.text = finalCount.toString()
        binding.tvSummaryCalories.text = String.format(Locale.getDefault(), "%.1f", calories)
        binding.tvSummaryTime.text =
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        binding.summaryOverlay.visibility = View.VISIBLE
        binding.btnStartSession.visibility = View.GONE
        binding.btnStopSession.visibility = View.GONE
    }

    private fun updateTimerDisplay() {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        binding.tvTimer.text =
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    // ── Camera setup ──────────────────────────────────────────────────────────

    @ExperimentalGetImage
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, PoseAnalyzer { landmarks ->
                        if (sessionActive) {
                            val newCount = pushupCounter.processLandmarks(landmarks)
                            runOnUiThread { updateUI(newCount, pushupCounter.feedback) }
                        }
                    })
                }

            // Use the front camera so the user can see themselves
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                binding.tvStatus.visibility = View.GONE
            } catch (e: Exception) {
                binding.tvStatus.text = "Camera error: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun updateUI(count: Int, feedback: String) {
        binding.tvCount.text = count.toString()
        binding.tvFeedback.text = feedback
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
}
