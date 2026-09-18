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
| WorkManager delay under Doze Mode | High | `OneTimeWorkRequest` with `setInitialDelay` for the common case; for targets < 15 min away or when the user opts in, delegate to `AlarmManager.setExactAndAllowWhileIdle` (needs `SCHEDULE_EXACT_ALARM` on API 31+, `USE_EXACT_ALARM` not allowed for this app category). Worker marks `setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)` so it runs promptly once due. Decision deferred to Phase 2 with a policy switch in `core`. | Planned (Phase 2) |
| System Reboot (`BOOT_COMPLETED`) | High | WorkManager already persists its own queue across reboot, but its delays are wall-clock relative and can be lost if the app is force-stopped. `BootCompletedReceiver` reads every `PENDING` row and re-enqueues with `ExistingWorkPolicy.REPLACE` keyed by `WorkNames.scheduledSms(id)`, so duplicates are impossible. `SchedulingPolicy.recoveryAction` decides: future -> re-delay; overdue <= 6 h -> send now; overdue > 6 h -> `FAILED("missed while device was off")`. | Core logic done & tested; receiver in Phase 4 |
| Device powered off at target timestamp | High | Same path as reboot: the 6 h lateness window (`SchedulingPolicy.maxLatenessMillis`) bounds how stale a message can be and still go out. Window is a constructor parameter for tuning. | Core logic done & tested |
| Missing `SEND_SMS` / `POST_NOTIFICATIONS` | Medium | Worker checks `checkSelfPermission` before dispatch and marks `FAILED("SEND_SMS permission revoked")` rather than crashing; UI gates the compose box and shows a rationale + settings deep link. Notification permission missing -> reminder still marked complete but a persistent in-app banner is shown. | Planned (Phase 4) |
| Race condition (Database delete vs. Worker execution) | Medium | No optimistic-locking column needed: every status change is a guarded `UPDATE ... WHERE id = ? AND status = ?` that returns the affected row count. Worker must get `claimForSending == 1` before touching the radio; user cancel must get `cancel == 1`. SQLite executes each statement atomically so exactly one side wins. Rows are never hard-deleted while `PENDING`/`SENDING`; cancel is a status. | Done & tested (`ScheduledMessageDaoTest.claimIsGrantedExactlyOnce`, `cancelBeatsLateWorker`) |
| Status consistency under signal loss | Medium | `SmsManager.sendTextMessage` is fire-and-forget; success/failure arrives via the `sentIntent` broadcast. Worker will register a `PendingIntent` per part and suspend until all parts report (with timeout). Transient radio errors (`RESULT_ERROR_NO_SERVICE`, `RESULT_ERROR_RADIO_OFF`) -> `releaseClaim` back to `PENDING` + `Result.retry()` with exponential backoff (max 3 attempts); permanent errors -> `FAILED(reason)`. Multipart: `sendMultipartTextMessage` so all parts share one status. | Planned (Phase 2) |
| Duplicate notifications for one reminder | Low | `ReminderDao.markCompleted` is guarded (`isCompleted = 0`) and the worker posts the notification only when it returns 1. | Done & tested |
| Build environment cannot reach `dl.google.com` (Android SDK + AndroidX) | High (blocks local verification) | Decision (user, 2026-09-18): run Android unit tests on GitHub Actions via `.github/workflows/scheduled-messenger-tests.yml`. Pure-Kotlin `core` tests additionally run locally. | Mitigated |
| Wrong library versions (cannot be checked locally) | Medium | Version catalog pinned to widely-used mid-2025 releases; CI is the oracle. | Mitigated: all artifacts resolved, KSP + Hilt + Room + Robolectric compile and run on CI |

## Core Modules & Status
- [x] Phase 1: Core Database & Persistence Setup (CI green, run #3, 2026-09-18)
- [ ] Phase 2: WorkManager Background Dispatcher (SMS & Reminders Engine)
- [ ] Phase 3: Jetpack Compose UI & Queue Management
- [ ] Phase 4: Permissions, Boot Receivers & Resilience

## Test Matrix & Execution Log
| Module | Test Type | Target File / Case | Status | Evidence / Terminal Command |
| :--- | :--- | :--- | :--- | :--- |
| Database | Unit Test | `ScheduledMessageDaoTest` (12), `ReminderDaoTest` (6), `SmsMessageDaoTest` (4) with in-memory Room under Robolectric | PASS (CI run #3, 2026-09-18) | `./gradlew :app:testDebugUnitTest` -> `BUILD SUCCESSFUL in 1m 47s`, 22 PASSED / 0 FAILED. https://github.com/shoepaladin/kiwi-cup/actions/runs/35382045595 |
| Worker | Integration Test | WorkManager Scheduled Dispatch | Pending | `./gradlew testDebugUnitTest` |
| UI | Instrumentation | Compose UI Actions & Queue Menu | Pending | `./gradlew connectedAndroidTest` |
| Core logic | Unit Test | `StatusTransitionsTest`, `SchedulingPolicyTest`, `RecipientValidatorTest`, `SmsTextAnalyzerTest`, `WorkNamesTest` (23 tests) | PASS (local + CI run #3, 2026-09-18) | `./gradlew :core:test` -> `BUILD SUCCESSFUL in 1m 22s`, 23 PASSED / 0 FAILED |

## Phase 1 Design Notes (Staff Engineer Critique)
- **Execution flow, data layer**: UI -> ViewModel -> DAO (Room) -> SQLite. Room `Flow` queries drive every list so the queue screen and the conversation view never poll. Enum columns are stored by name so DAO SQL can reference `'PENDING'` literally and stay readable.
- **Execution flow, background engine (Phase 2)**: enqueue (`WorkNames.scheduledSms(id)`, REPLACE) -> `ScheduledSmsWorker` -> `claimForSending` (must return 1) -> `SmsManager.sendMultipartTextMessage` -> await `sentIntent` results -> `markSent` / `markFailed` / `releaseClaim + retry`.
- **Status model**: spec's `PENDING / SENT / FAILED` is extended with `SENDING` (claimed by a worker) and `CANCELLED` (user action kept as history). `StatusTransitions` in `core` is the single legal-transition table; DAO guards mirror it.
- **Module split rationale**: `core` has zero Android dependencies so scheduling arithmetic, transition rules, recipient validation and SMS segment counting are unit-tested in milliseconds and cannot regress silently behind Robolectric.
- **Schema export** is off for v1 (`exportSchema = false`) with destructive fallback; it will be enabled with a `schemas/` directory before the first public release so migrations can be tested.

## Current Execution Focus
- [x] Initialize Android project structure and establish `roadmap.md`.
- [x] Phase 1 architecture critique recorded above.
- [x] Task 1 code: Room entities, DAOs, `AppDatabase`, Hilt `DatabaseModule`, `core` rules.
- [x] `core` tests pass locally (23 tests, evidence below).
- [x] Task 1 Android DAO tests: 22/22 pass on GitHub Actions (run #3).
- [x] CI history: run #1 failed on `SmsMessageDao.observeThreadSummaries` (count computed after filtering to newest row); fixed with a correlated subquery. Run #2 was the same failure on an unrelated build-script tidy-up. Run #3 green.
- [ ] STOP: Task 1 presented for review. Task 2 (WorkManager engines) starts on user confirmation.

## CI Evidence Log
### Run #3, commit 84bf109, 2026-09-18 (https://github.com/shoepaladin/kiwi-cup/actions/runs/35382045595)
```
./gradlew :core:test --no-daemon --stacktrace
> Task :core:test
RecipientValidatorTest > formatting characters are stripped PASSED
RecipientValidatorTest > international short code and local numbers are valid PASSED
RecipientValidatorTest > junk is rejected PASSED
SchedulingPolicyTest > zero lateness window means anything in the past expires PASSED
SchedulingPolicyTest > delay is target minus now and never negative PASSED
SchedulingPolicyTest > negative lateness window is rejected PASSED
SchedulingPolicyTest > user may schedule now or later but not in the past PASSED
SchedulingPolicyTest > target older than the lateness window expires PASSED
SchedulingPolicyTest > future target dispatches later with exact delay PASSED
SchedulingPolicyTest > slightly late target dispatches immediately PASSED
SmsTextAnalyzerTest > extension characters cost two septets PASSED
SmsTextAnalyzerTest > empty body is one empty segment and not sendable PASSED
SmsTextAnalyzerTest > emoji forces ucs2 with 70 char segments PASSED
SmsTextAnalyzerTest > plain ascii is gsm and fits one segment up to 160 PASSED
SmsTextAnalyzerTest > 161 gsm characters need two segments of 153 PASSED
StatusTransitionsTest > only pending needs a work request PASSED
StatusTransitionsTest > pending can be claimed cancelled or failed PASSED
StatusTransitionsTest > failed and cancelled can be rescheduled PASSED
StatusTransitionsTest > sent is final PASSED
StatusTransitionsTest > require throws on illegal transition and returns target on legal one PASSED
StatusTransitionsTest > sending resolves to sent failed or back to pending for retry PASSED
WorkNamesTest > foreign names do not parse PASSED
WorkNamesTest > names round-trip through ids PASSED
BUILD SUCCESSFUL in 1m 22s

./gradlew :app:testDebugUnitTest --no-daemon --stacktrace
> Task :app:kspDebugKotlin
> Task :app:compileDebugKotlin
> Task :app:hiltJavaCompileDebug
> Task :app:testDebugUnitTest
ReminderDaoTest > editReactivatesAndClearsWork PASSED
ReminderDaoTest > perThreadFlowTracksChanges PASSED
ReminderDaoTest > insertReadUpdateDelete PASSED
ReminderDaoTest > activeIsOrderedAndExcludesCompleted PASSED
ReminderDaoTest > markCompletedFiresOnlyOnce PASSED
ReminderDaoTest > clearCompletedRemovesOnlyDone PASSED
ScheduledMessageDaoTest > historyAndCounts PASSED
ScheduledMessageDaoTest > insertAndReadBack PASSED
ScheduledMessageDaoTest > releaseClaimReturnsToPending PASSED
ScheduledMessageDaoTest > workRequestIdIsPersisted PASSED
ScheduledMessageDaoTest > editOnlyWhilePending PASSED
ScheduledMessageDaoTest > claimIsGrantedExactlyOnce PASSED
ScheduledMessageDaoTest > pendingFlowEmitsOnChanges PASSED
ScheduledMessageDaoTest > updateAndDelete PASSED
ScheduledMessageDaoTest > dueReturnsOnlyPendingAtOrBeforeNow PASSED
ScheduledMessageDaoTest > failedRecordsReasonAndCanBeRescheduled PASSED
ScheduledMessageDaoTest > cancelBeatsLateWorker PASSED
ScheduledMessageDaoTest > pendingQueriesAreOrderedByTargetTime PASSED
SmsMessageDaoTest > summariesShowLatestPerThreadNewestFirst PASSED
SmsMessageDaoTest > insertReadDelete PASSED
SmsMessageDaoTest > threadFlowEmitsAndDeleteThreadClears PASSED
SmsMessageDaoTest > threadIsChronological PASSED
BUILD SUCCESSFUL in 1m 47s
36 actionable tasks: 34 executed, 2 up-to-date
```
