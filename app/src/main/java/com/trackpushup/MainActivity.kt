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

    /** Which exercise the user chose on the selection screen. */
    private enum class WorkoutType { PUSHUP, SQUAT }

    private var selectedWorkoutType: WorkoutType = WorkoutType.PUSHUP
    private var activeCounter: ExerciseCounter = PushupCounter()

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
        /** Approximate kcal burned per push-up for an average adult. */
        private const val CALORIES_PER_PUSHUP = 0.32
        /** Approximate kcal burned per squat for an average adult. */
        private const val CALORIES_PER_SQUAT  = 0.35
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

        binding.btnSelectPushup.setOnClickListener { selectWorkout(WorkoutType.PUSHUP) }
        binding.btnSelectSquat.setOnClickListener  { selectWorkout(WorkoutType.SQUAT)  }
        binding.btnStartSession.setOnClickListener { startSession() }
        binding.btnStopSession.setOnClickListener  { stopSession()  }
        binding.btnNewSession.setOnClickListener   { newSession()   }

        showWorkoutSelectionState()

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

    // ── Workout selection ─────────────────────────────────────────────────────

    private fun selectWorkout(type: WorkoutType) {
        selectedWorkoutType = type
        activeCounter = if (type == WorkoutType.SQUAT) SquatCounter() else PushupCounter()
        showReadyState()
    }

    // ── Session management ────────────────────────────────────────────────────

    private fun startSession() {
        activeCounter.reset()
        sessionActive = true
        sessionStartTimeMs = System.currentTimeMillis()
        elapsedSeconds = 0L
        timerHandler.post(timerRunnable)
        showActiveState()
        updateUI(0, activeCounter.feedback)
    }

    private fun stopSession() {
        sessionActive = false
        timerHandler.removeCallbacks(timerRunnable)
        showSummaryState()
    }

    private fun newSession() {
        showWorkoutSelectionState()
    }

    // ── UI state helpers ──────────────────────────────────────────────────────

    private fun showWorkoutSelectionState() {
        binding.workoutSelectionOverlay.visibility = View.VISIBLE
        binding.summaryOverlay.visibility = View.GONE
        binding.btnStartSession.visibility = View.GONE
        binding.btnStopSession.visibility = View.GONE
        binding.tvTimer.text = getString(R.string.timer_default)
        binding.tvCount.text = "0"
        binding.tvFeedback.text = getString(R.string.session_hint)
    }

    private fun showReadyState() {
        binding.workoutSelectionOverlay.visibility = View.GONE
        binding.summaryOverlay.visibility = View.GONE
        binding.btnStartSession.visibility = View.VISIBLE
        binding.btnStopSession.visibility = View.GONE
        binding.tvTimer.text = getString(R.string.timer_default)
        binding.tvCount.text = "0"
        binding.tvFeedback.text = getString(R.string.session_hint)

        // Update labels to reflect selected workout
        val isPushup = selectedWorkoutType == WorkoutType.PUSHUP
        binding.tvTitle.text = getString(
            if (isPushup) R.string.title_pushup else R.string.title_squat
        )
        binding.tvPushups.text = getString(
            if (isPushup) R.string.push_ups_done else R.string.squats_done
        )
    }

    private fun showActiveState() {
        binding.workoutSelectionOverlay.visibility = View.GONE
        binding.summaryOverlay.visibility = View.GONE
        binding.btnStartSession.visibility = View.GONE
        binding.btnStopSession.visibility = View.VISIBLE
    }

    private fun showSummaryState() {
        val finalCount = activeCounter.count
        val caloriesPerRep =
            if (selectedWorkoutType == WorkoutType.SQUAT) CALORIES_PER_SQUAT
            else CALORIES_PER_PUSHUP
        val calories = finalCount * caloriesPerRep
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60

        binding.tvSummaryPushups.text = finalCount.toString()
        binding.tvSummaryRepsLabel.text = getString(
            if (selectedWorkoutType == WorkoutType.SQUAT) R.string.squats_done
            else R.string.push_ups_done
        )
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
                            val newCount = activeCounter.processLandmarks(landmarks)
                            runOnUiThread { updateUI(newCount, activeCounter.feedback) }
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

