# Trackpushup

An Android app for **live pushup tracking** using your phone's front camera. It counts pushup repetitions **only when your form is correct**.

## Features

- 📸 **Real-time pose detection** using [Google ML Kit Pose Detection](https://developers.google.com/ml-kit/vision/pose-detection)
- ✅ **Form validation** – only counts a rep when your body is in a straight plank position
- 🔢 **Live rep counter** displayed on screen
- 💬 **Feedback messages** to guide your form ("Keep your body straight!", "Push up!", etc.)
- 🔄 **Reset button** to start a new set

## How It Works

1. Point your phone's front camera so your full body is visible from the side.
2. Get into the push-up starting position (arms extended).
3. Perform push-ups. The app will:
   - Detect your body pose each frame using ML Kit.
   - Calculate your elbow angle and body alignment.
   - Count a rep **only if**:
     - Your elbow angle drops below **90°** (down position) and returns above **160°** (up position).
     - Your body remains **straight** (shoulder–hip–ankle angle ≥ 150°) during the rep.

## Tech Stack

| Component | Library |
|-----------|---------|
| Camera    | CameraX 1.3.1 |
| Pose Detection | ML Kit Pose Detection 18.0.0-beta4 |
| Language  | Kotlin |
| Min SDK   | 24 (Android 7.0) |

## Project Structure

```
app/src/main/java/com/trackpushup/
├── MainActivity.kt      # Camera lifecycle & UI updates
├── PoseAnalyzer.kt      # CameraX ImageAnalysis → ML Kit bridge
└── PushupCounter.kt     # Form validation & rep counting logic
```

## Build

```bash
./gradlew assembleDebug
```

## Permissions

- `android.permission.CAMERA` – required for real-time pose detection.
