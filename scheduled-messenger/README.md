# Scheduled Messenger

[![Android 8+](https://img.shields.io/badge/Android-8%2B-green.svg)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com)

Write a text now, send it later. Long-press any message to get a reminder about it at a time you choose.

Primarily made with Claude! Engineering progress is tracked in [`roadmap.md`](roadmap.md).

## Status

Feature complete for a first personal build: inbox import, scheduled sending, reminders with deep-linked notifications, queue management, reboot recovery and runtime permissions. 84 automated tests run on every push. Not yet exercised on a physical phone.

## How it works

1. On first launch the app asks for SMS and notification permissions, then copies your existing conversations from the phone's SMS store.
2. Write a text and either send it now or tap the calendar to pick a date and time. Scheduled texts wait in a background job and go out even if the app is closed.
3. Long-press any message and choose **Remind me about this later**. At the chosen time you get a notification that opens the conversation.
4. The queue screen lists everything upcoming, with edit and cancel, and keeps a history of sent, failed and cancelled items.

The app is not your default SMS app, so texts you send from here appear in this app but not in the stock Messages app.

## Architecture

| Layer | Choice |
| :--- | :--- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Storage | Room (SQLite) |
| Background work | WorkManager |
| Dependency injection | Hilt |

Modules:

* `core` — pure Kotlin. Status transition rules, scheduling arithmetic (what to do with a message whose time passed while the phone was off), recipient validation and SMS segment counting. Tested with plain JUnit.
* `app` — the Android application. Room entities and DAOs, workers, Compose screens.

## Building

Open the `scheduled-messenger` folder in Android Studio (Ladybug or newer) and let it sync. From a terminal:

```
./gradlew :core:test                 # pure Kotlin rules
./gradlew :app:testDebugUnitTest     # Room DAO tests under Robolectric
./gradlew :app:assembleDebug
```

The same two test tasks run on every push in GitHub Actions (`.github/workflows/scheduled-messenger-tests.yml`).

## Permissions

* `SEND_SMS` — dispatch scheduled texts.
* `READ_SMS`, `RECEIVE_SMS` — show conversation threads.
* `POST_NOTIFICATIONS` — reminder notifications.
* `RECEIVE_BOOT_COMPLETED` — re-arm pending messages and reminders after a reboot.

Nothing leaves the device except the SMS you scheduled.
