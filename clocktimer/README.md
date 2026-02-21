# VisualTimer & Stopwatch ⏱️

[![Android 15+](https://img.shields.io/badge/Android-15%2B-green.svg)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com)

A minimalist, tactile, and highly customizable Android timer and stopwatch. Unlike standard digital clocks, **VisualTimer** uses a physical-inspired clock face that allows users to "set" time by dragging their finger across the dial, providing an intuitive sense of remaining time.

## ✨ Features

* **Tactile Gesture Control:** Drag your finger around the clock face to intuitively set the timer duration.
* **Dual Mode:** Seamlessly toggle between a **Visual Timer** (Sweep dial) and a precise **Hand-Style Stopwatch**.
* **Customizable Aesthetics:**
    * **Theming:** Change background colors (Light, Dark, Off-white).
    * **Dynamic Accents:** Set custom colors for Timer and Stopwatch modes independently.
* **Haptic Feedback:** Physical "ticks" vibrate via the device motor as you scroll through time increments.
* **Persistent Looping Alarm:** Features a custom Ringtone Picker that loops the audio until you hit "Stop."
* **Quick Toggles:** One-tap presets to add time or switch between Minute and Second precision.

---

## 📸 Preview
`![Timer Mode](demo/timer_shot.png)`
`![Stopwatch Mode](demo/stopwatch_shot.png)`
`![Settings](demo/settings.png)`

---

## 🚀 Installation

Since this app is open-source, you can install it by downloading the APK directly or building from source.

1. Go to the **[Releases](https://github.com/shoepaladin/kiwi-cup/releases)** page of this repository.
2. Download the `clocktimer.apk`.
3. Open the file on your Android device. 
   > **Note:** You may need to enable "Install from Unknown Sources" in your browser or file manager settings.

---

## 🛠️ Technical Breakdown

This app is built using **100% Kotlin** and **Jetpack Compose**.

* **Canvas API:** The clock face, tick marks, and the "Sweep" progress indicator are custom-drawn for high-performance rendering.
* **Trigonometry-based Input:** Uses `atan2` math to translate 2D touch coordinates into radial angles, which are then mapped to time values.
* **Material 3:** Implements modern Google design components for dialogs, cards, and buttons.
* **State Management:** Uses `LaunchedEffect` with 100ms delay cycles for smooth, battery-efficient clock hand animation.

### Project Dependencies
* `androidx.compose.ui`: Custom drawing and gesture detection.
* `android.media.RingtoneManager`: System-level alarm and ringtone integration.
* `androidx.activity.compose`: Modern activity-to-compose architecture.

---

## 🏗️ Building from Source

If you want to modify the app or build it yourself:

1. Clone the repository:
   ```
   bash
   git clone [https://github.com/shoepaladin/kiwi-cup.git](https://github.com/shoepaladin/kiwi-cup.git)
   ```
2. Open the project in Android Studio (Ladybug or newer).
3. Ensure you have the Android 15 (API 35) SDK installed.
4. Click Run to deploy to your physical device or emulator.

