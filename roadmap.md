# Engineering Roadmap & Execution Tracker

## System Architecture & Technical Choices
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Database**: Room Persistence Library (SQLite)
- **Background Dispatcher**: WorkManager
- **DI Framework**: Hilt
- **Project location**: `scheduled-messenger/` (monorepo convention: one Gradle project per app)
- **Module split**: `app` (Android: Room, WorkManager, Compose, Hilt) and `core` (pure Kotlin JVM: status transitions, scheduling policy, SMS validation) so business rules are testable without an Android SDK.

## Architecture Risk Assessment & Mitigation Log
| Potential Issue / Edge Case | Risk Level | Mitigation Strategy | Status |
| :--- | :--- | :--- | :--- |
| WorkManager delay under Doze Mode | High | Use expedited jobs or exact alarm delegation where appropriate | Unreviewed |
| System Reboot (`BOOT_COMPLETED`) | High | Re-enqueue active pending jobs from Room on reboot | Unreviewed |
| Missing `SEND_SMS` / `POST_NOTIFICATIONS` | Medium | Runtime permission checks & graceful fallback UI | Unreviewed |
| Race condition (Database delete vs. Worker execution) | Medium | Transactions with optimistic locking or status checks | Unreviewed |
| Build environment cannot reach `dl.google.com` (Android SDK + AndroidX) | High (blocks verification) | Options: run tests on GitHub Actions; allow host in environment network policy; or verify `core` only here and Android tests in Android Studio | Awaiting user decision |

## Core Modules & Status
- [ ] Phase 1: Core Database & Persistence Setup
- [ ] Phase 2: WorkManager Background Dispatcher (SMS & Reminders Engine)
- [ ] Phase 3: Jetpack Compose UI & Queue Management
- [ ] Phase 4: Permissions, Boot Receivers & Resilience

## Test Matrix & Execution Log
| Module | Test Type | Target File / Case | Status | Evidence / Terminal Command |
| :--- | :--- | :--- | :--- | :--- |
| Database | Unit Test | Room DAO CRUD & Flow Queries | Pending | `./gradlew testDebugUnitTest` |
| Worker | Integration Test | WorkManager Scheduled Dispatch | Pending | `./gradlew testDebugUnitTest` |
| UI | Instrumentation | Compose UI Actions & Queue Menu | Pending | `./gradlew connectedAndroidTest` |
| Core logic | Unit Test | Status transitions, scheduling policy, SMS validation | Pending | `./gradlew :core:test` |

## Current Execution Focus
- [x] Initialize Android project structure and establish `roadmap.md`.
- [ ] Resolve build-verification environment (see risk log) before Task 1 tests can be evidenced.
- [ ] Task 1: Room entities, DAOs, in-memory DAO tests.
