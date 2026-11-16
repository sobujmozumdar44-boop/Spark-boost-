# SparkBoost for TECNO SPARK 40 — Native Android Setup

## 1. Build APK in Android Studio
1. Create a new **Empty Activity (Compose)** project in Android Studio with package name `com.sparkboost.tecno`.
2. Replace `AndroidManifest.xml`, `app/build.gradle.kts`, and add the Kotlin files from `app/src/main/java/com/sparkboost/tecno/`.
3. Connect your **TECNO SPARK 40** with USB Debugging enabled and click **Run** (or **Build > Build APK(s)**).

## 2. Grant Required Permissions on TECNO HiOS
- **Display over other apps**: Settings > Apps > Special app access > Display over other apps > Enable **SparkBoost**.
- **Battery Unrestricted** (prevents HiOS from killing the floating window): Settings > Battery > App battery optimization > SparkBoost > **Unrestricted**.
- **Usage Access** (for per-app RAM & cache inspection): Settings > Security > Usage access > Enable **SparkBoost**.

## 3. Optional ADB Tweaks for Maximum Game FPS (No Root Required)
Run from your PC with USB Debugging enabled:
```bash
# Lock 120Hz / 90Hz minimum refresh rate on Spark 40 display
adb shell settings put system min_refresh_rate 120.0
adb shell settings put system peak_refresh_rate 120.0

# Disable HiOS phantom process killer & free game background thread limits
adb shell device_config put activity_manager max_phantom_processes 2147483647

# Grant Usage Stats permission directly
adb shell appops set com.sparkboost.tecno GET_USAGE_STATS allow
```
