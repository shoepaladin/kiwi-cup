# Engineering Roadmap & Execution Tracker

## System Architecture & Technical Choices
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Database**: Room Persistence Library (SQLite)
- **Background Dispatcher**: WorkManager
- **DI Framework**: Hilt
- **Project location**: `scheduled-messenger/` (monorepo convention: one Gradle project per app); this tracker lives alongside it at `scheduled-messenger/roadmap.md`
- **Module split**: `app` (Android: Room, WorkManager, Compose, Hilt) and `core` (pure Kotlin JVM: status transitions, scheduling policy, SMS validation) so business rules are testable without an Android SDK.

## Architecture Risk Assessment & Mitigation Log
| Potential Issue / Edge Case | Risk Level | Mitigation Strategy | Status |
| :--- | :--- | :--- | :--- |
| WorkManager delay under Doze Mode | High | `OneTimeWorkRequest` with `setInitialDelay` for the common case; for targets < 15 min away or when the user opts in, delegate to `AlarmManager.setExactAndAllowWhileIdle` (needs `SCHEDULE_EXACT_ALARM` on API 31+, `USE_EXACT_ALARM` not allowed for this app category). Worker marks `setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)` so it runs promptly once due. **Phase 2 decision**: v1 uses plain `OneTimeWorkRequest` + `setInitialDelay` only. `setExpedited` is not allowed together with a delay, and exact alarms need a user-granted special permission on Android 12+. Expected worst case in deep Doze: delivery a few minutes late, never lost. Exact-alarm delegation stays a Phase 4 option if testing on a real device shows unacceptable lag. | Decided (v1: WorkManager delay) |
| System Reboot (`BOOT_COMPLETED`) | High | WorkManager already persists its own queue across reboot, but its delays are wall-clock relative and can be lost if the app is force-stopped. `BootCompletedReceiver` reads every `PENDING` row and re-enqueues with `ExistingWorkPolicy.REPLACE` keyed by `WorkNames.scheduledSms(id)`, so duplicates are impossible. `SchedulingPolicy.recoveryAction` decides: future -> re-delay; overdue <= 6 h -> send now; overdue > 6 h -> `FAILED("missed while device was off")`. | Done & tested end to end (`BootCompletedReceiverTest`): receiver -> `RearmWorker` -> repositories -> WorkManager. `Application.onCreate` also enqueues the same unique work (covers app updates and force-stops). |
| Device powered off at target timestamp | High | Same path as reboot: the 6 h lateness window (`SchedulingPolicy.maxLatenessMillis`) bounds how stale a message can be and still go out. Window is a constructor parameter for tuning. | Core logic done & tested |
| Missing `SEND_SMS` / `POST_NOTIFICATIONS` | Medium | `PermissionGate` blocks the UI until READ/RECEIVE/SEND_SMS are granted, asks once automatically, explains each permission, and deep-links to app settings after a permanent denial. POST_NOTIFICATIONS is requested but optional. Workers independently re-check permissions at run time (`ScheduledSmsWorker` -> FAILED with reason; `ReminderWorker` -> leaves reminder active). | Done & tested (`PermissionsScreenTest`, worker tests) |
| Race condition (Database delete vs. Worker execution) | Medium | No optimistic-locking column needed: every status change is a guarded `UPDATE ... WHERE id = ? AND status = ?` that returns the affected row count. Worker must get `claimForSending == 1` before touching the radio; user cancel must get `cancel == 1`. SQLite executes each statement atomically so exactly one side wins. Rows are never hard-deleted while `PENDING`/`SENDING`; cancel is a status. | Done & tested (`ScheduledMessageDaoTest.claimIsGrantedExactlyOnce`, `cancelBeatsLateWorker`) |
| Status consistency under signal loss | Medium | `SmsManager.sendTextMessage` is fire-and-forget; success/failure arrives via the `sentIntent` broadcast. Worker will register a `PendingIntent` per part and suspend until all parts report (with timeout). Transient radio errors (`RESULT_ERROR_NO_SERVICE`, `RESULT_ERROR_RADIO_OFF`) -> `releaseClaim` back to `PENDING` + `Result.retry()` with exponential backoff (max 3 attempts); permanent errors -> `FAILED(reason)`. Multipart: `sendMultipartTextMessage` so all parts share one status. Implemented in `AndroidSmsSender` (60 s receipt timeout -> transient). Worker retries at most 3 times with exponential backoff (30 s base) before `FAILED`. | Done; worker paths tested with a fake sender. `AndroidSmsSender` itself needs a device test (Robolectric cannot emulate the radio). |
| Duplicate notifications for one reminder | Low | `ReminderDao.markCompleted` is guarded (`isCompleted = 0`) and the worker posts the notification only when it returns 1. | Done & tested (`ReminderWorkerTest.secondRunDoesNotNotifyAgain`) |
| Reminder due while notifications are blocked | Low | Worker leaves the reminder active (not completed) and returns failure so nothing is silently lost; the queue screen will show it as overdue. | Done & tested |
| Scheduled row deleted while its job is queued | Low | Worker treats a missing row as a no-op success; repository `delete` cancels the unique work first. | Done & tested |
| Build environment cannot reach `dl.google.com` (Android SDK + AndroidX) | High (blocks local verification) | Decision (user, 2026-09-18): run Android unit tests on GitHub Actions via `.github/workflows/scheduled-messenger-tests.yml`. Pure-Kotlin `core` tests additionally run locally. | Mitigated |
| No inbox: app cannot show existing conversations | High (product) | `SmsInboxImporter` reads the phone's SMS store (`Telephony.Sms`) incrementally by system row id on every app open; `SmsReceiver` stores new texts as they arrive. The app is not the default SMS app, so our own sent copies stay in Room and join the conversation by phone number. | Done & tested (`SmsInboxImporterTest`, `IncomingSmsHandlerTest`) |
| Not the default SMS app | Medium (product) | Reading and receiving SMS works for any app holding the permissions; only writing into the system store is reserved for the default app. Consequence: texts sent from this app are visible here but not in the phone's stock Messages app. Becoming default is a possible v2 (requires implementing MMS/WAP receivers and a full messaging UI). | Accepted for v1 |
| Wrong library versions (cannot be checked locally) | Medium | Version catalog pinned to widely-used mid-2025 releases; CI is the oracle. | Mitigated: all artifacts resolved, KSP + Hilt + Room + Robolectric compile and run on CI |

## Core Modules & Status
- [x] Phase 1: Core Database & Persistence Setup (CI green, run #3, 2026-09-18)
- [x] Phase 2: WorkManager Background Dispatcher (SMS & Reminders Engine) (CI green, run #5, 2026-09-18)
- [x] Phase 3: Jetpack Compose UI & Queue Management (CI green, run #9, 2026-09-18)
- [x] Phase 4: Permissions, Boot Receivers & Resilience (CI green, run #12, 2026-09-18)
- [x] Phase 5: Theming & per-conversation styles (CI green, run #16, 2026-09-18)
- [ ] Phase 6: Default SMS app + MMS (groups, pictures)
- [ ] Phase 7: Expert review, hardening, release APK

## Test Matrix & Execution Log
| Module | Test Type | Target File / Case | Status | Evidence / Terminal Command |
| :--- | :--- | :--- | :--- | :--- |
| Database | Unit Test | `ScheduledMessageDaoTest` (12), `ReminderDaoTest` (6), `SmsMessageDaoTest` (4) with in-memory Room under Robolectric | PASS (CI run #3, 2026-09-18) | `./gradlew :app:testDebugUnitTest` -> `BUILD SUCCESSFUL in 1m 47s`, 22 PASSED / 0 FAILED. https://github.com/shoepaladin/kiwi-cup/actions/runs/35382045595 |
| Worker | Unit Test | `ScheduledSmsWorkerTest` (8), `ReminderWorkerTest` (4) via `TestListenableWorkerBuilder` | PASS (CI run #5) | `./gradlew :app:testDebugUnitTest` -> 12 PASSED |
| Worker | Integration Test | `SchedulingIntegrationTest` (8): repository -> WorkManager test driver (`setInitialDelayMet`) -> worker -> Room | PASS (CI run #5) | `./gradlew :app:testDebugUnitTest` -> `BUILD SUCCESSFUL in 1m 14s`, 40 app tests PASSED / 0 FAILED. https://github.com/shoepaladin/kiwi-cup/actions/runs/35383891912 |
| UI | Compose UI test (Robolectric-hosted, runs in CI) | `MessageInputBarTest` (4), `QueueScreenTest` (4), `ThreadScreenTest` (2): send/schedule buttons, date+time picker flow, validation error, queue sections and actions, edit dialog, long-press -> Remind me -> reminder created | PASS (CI run #9) | `./gradlew :app:testDebugUnitTest` -> `BUILD SUCCESSFUL in 1m 57s`, 52 app tests PASSED / 0 FAILED. https://github.com/shoepaladin/kiwi-cup/actions/runs/35391197931 |
| Theming | Unit + UI | `ThemeColorsTest` (7, core), `SettingsRepositoryTest` (2), `ConversationStyleRepositoryTest` (4), `SettingsScreenTest` (1), `ConversationStyleTest` (1) | PASS (CI run #16) | https://github.com/shoepaladin/kiwi-cup/actions/runs/35404637057 -> 30 core + 69 app tests PASSED |
| UI | Instrumentation (device) | Same screens via `connectedAndroidTest` | Not run (no emulator in CI); Robolectric-hosted Compose tests cover the same assertions | `./gradlew connectedAndroidTest` on a device |
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
- [x] Task 2 code: `SmsSender` seam + `AndroidSmsSender`, `ScheduledSmsWorker`, `ReminderWorker`, `ReminderNotifier` (deep link), `WorkScheduler`, `ScheduledMessageRepository`, `ReminderRepository`, `AppModule`.
- [x] Task 2 tests: 20 new tests, all green on CI run #5. Run #4 failed once on a race in the reminder re-arm test (zero-delay work fired before the assertion); fixed by using future-dated reminders.
- [x] CI history: run #1 failed on `SmsMessageDao.observeThreadSummaries` (count computed after filtering to newest row); fixed with a correlated subquery. Run #2 was the same failure on an unrelated build-script tidy-up. Run #3 green.
- [x] Task 1 review presented; user confirmed ("go").
- [x] Task 2 review presented; user confirmed.
- [x] Task 3 code: `ConversationsScreen`, `ThreadScreen` (bubbles, long-press context menu, reminder banner), `MessageInputBar` + `DateTimePickerDialog` (calendar then clock), `ComposeScreen`, `QueueScreen` (upcoming/history, edit, cancel, reschedule, done, delete, clear), `AppNavHost`, deep-link handling in `MainActivity`; one Hilt ViewModel per screen.
- [x] Task 3 tests: 10 Compose UI tests green on CI run #9. Runs #7-#8 failed on test-side issues only (rows below the fold on Robolectric's small display; duplicate text match in the edit dialog); the app code compiled and behaved correctly from run #7.
- [x] Task 3 review presented; user confirmed and asked for an inbox path.
- [x] Task 4 code: `SmsInboxImporter`, `IncomingSmsHandler`, `SmsReceiver`, `BootCompletedReceiver`, `RearmWorker`, `PermissionGate` / `PermissionsScreen` / `AppPermissions`, `SchedulerApi` seam, DB schema v2 (`systemId`).
- [x] Task 4 tests: 9 new tests green on CI run #12 (84 total). Run #11 failed on one test-side compile error (Robolectric's `setCursor` wants its own cursor type); replaced with a fake content provider.
- [x] Task 4 presented; user chose (c): visual polish first, then default-SMS + MMS, then an expert review before an APK.
- [x] Task 5 (theming): `ThemeColors` (core), `SettingsRepository` (DataStore), `ConversationStyle` table (DB v3), `ConversationStyleRepository` (wallpaper copied into app storage), `SettingsScreen`, `ConversationStyleDialog`, theme wiring in `MainActivity`. Runs #14-#15 failed on test-side off-screen taps (small Robolectric display); run #16 green with 99 tests.
- [ ] Task 6: default SMS app + MMS.

## Remaining Work Before Daily Use (not in the four phases)
- **Device smoke test** (user): install debug APK, grant permissions, confirm the inbox imports, send a scheduled text to yourself, set a reminder, reboot and confirm the queue survives.
- **Exact alarms** if WorkManager's delay proves too loose under Doze on the user's phone (`SCHEDULE_EXACT_ALARM`, contained change in `WorkScheduler`).
- **Schema export + migrations** before the first shared release (`exportSchema = true`, drop `fallbackToDestructiveMigration`).
- **Contact names** instead of raw numbers (needs `READ_CONTACTS`).
- **Release build**: signing config, `isMinifyEnabled = true` with keep rules for Room/Hilt/WorkManager.

## CI Evidence Log
### Run #12, commit 1a8cb6e, 2026-09-18 (https://github.com/shoepaladin/kiwi-cup/actions/runs/35402186017)
```
./gradlew :core:test --no-daemon --stacktrace        -> 23 PASSED, BUILD SUCCESSFUL in 1m 20s
./gradlew :app:testDebugUnitTest --no-daemon --stacktrace
DAO tests (22), worker tests (12), scheduling integration (8), Compose UI (10) ... PASSED
IncomingSmsHandlerTest > joinsExistingThreadByAddress PASSED
IncomingSmsHandlerTest > createsNewThreadForUnknownAddressAndDedupes PASSED
SmsInboxImporterTest > importsInboxAndSentRowsWithSystemThreadIds PASSED
SmsInboxImporterTest > secondImportIsIncrementalAndSkipsKnownRows PASSED
SmsInboxImporterTest > withoutPermissionNothingIsRead PASSED
BootCompletedReceiverTest > bootReenqueuesPendingMessagesAndActiveReminders PASSED
BootCompletedReceiverTest > unrelatedBroadcastIsIgnored PASSED
PermissionsScreenTest > listsMissingPermissionsAndRequestsOnTap PASSED
PermissionsScreenTest > permanentDenialOffersSettings PASSED
BUILD SUCCESSFUL in 2m 5s   (61 app tests PASSED, 0 FAILED; 84 total with core)
```

### Run #9, commit c9e1bb1, 2026-09-18 (https://github.com/shoepaladin/kiwi-cup/actions/runs/35391197931)
```
./gradlew :core:test --no-daemon --stacktrace        -> 23 PASSED, BUILD SUCCESSFUL in 1m 20s
./gradlew :app:testDebugUnitTest --no-daemon --stacktrace
ReminderDaoTest (6), ScheduledMessageDaoTest (12), SmsMessageDaoTest (4) ...... PASSED
ReminderWorkerTest (4), ScheduledSmsWorkerTest (8), SchedulingIntegrationTest (8) PASSED
MessageInputBarTest > buttonsDisabledUntilTextIsTyped PASSED
MessageInputBarTest > scheduleWalksThroughDateAndTimeAndEmitsFutureTimestamp PASSED
MessageInputBarTest > sendNowInvokesCallback PASSED
MessageInputBarTest > validationErrorKeepsDialogOpen PASSED
QueueScreenTest > emptyQueueShowsHint PASSED
QueueScreenTest > cancelAndDoneInvokeCallbacks PASSED
QueueScreenTest > rendersUpcomingAndHistorySections PASSED
QueueScreenTest > editOpensDialogPrefilledWithMessage PASSED
ThreadScreenTest > longPressOpensRemindMenuAndCreatesReminder PASSED
ThreadScreenTest > rendersBubblesAndReminderBanner PASSED
BUILD SUCCESSFUL in 1m 57s   (52 app tests PASSED, 0 FAILED; 75 total with core)
```

### Run #5, commit dbfb6c1, 2026-09-18 (https://github.com/shoepaladin/kiwi-cup/actions/runs/35383891912)
```
./gradlew :core:test --no-daemon --stacktrace        -> 23 PASSED, BUILD SUCCESSFUL in 1m 5s
./gradlew :app:testDebugUnitTest --no-daemon --stacktrace
> Task :app:testDebugUnitTest
ReminderDaoTest (6) ........................................ PASSED
ScheduledMessageDaoTest (12) ............................... PASSED
SmsMessageDaoTest (4) ...................................... PASSED
ReminderWorkerTest > blockedNotificationsLeaveReminderActive PASSED
ReminderWorkerTest > secondRunDoesNotNotifyAgain PASSED
ReminderWorkerTest > postsNotificationWithDeepLinkAndCompletesReminder PASSED
ReminderWorkerTest > unknownReminderIsANoOp PASSED
ScheduledSmsWorkerTest > missingPermissionFailsWithoutTouchingRadio PASSED
ScheduledSmsWorkerTest > deletedMessageIsANoOp PASSED
ScheduledSmsWorkerTest > successfulSendMarksSentAndRecordsThreadCopy PASSED
ScheduledSmsWorkerTest > permanentFailureMarksFailedWithReason PASSED
ScheduledSmsWorkerTest > cancelledMessageIsNeverSent PASSED
ScheduledSmsWorkerTest > sentCopyJoinsExistingThreadForSameAddress PASSED
ScheduledSmsWorkerTest > transientFailureOnLastAttemptGivesUp PASSED
ScheduledSmsWorkerTest > transientFailureReleasesClaimAndRetries PASSED
SchedulingIntegrationTest > rebootReplayDispatchesSlightlyLateMessagesAndExpiresStaleOnes PASSED
SchedulingIntegrationTest > cancelRemovesWorkAndMarksCancelled PASSED
SchedulingIntegrationTest > rescheduleAfterFailureReplacesWorkUnderSameUniqueName PASSED
SchedulingIntegrationTest > reminderFiresWhenDelayIsMetAndCompleteCancelsWork PASSED
SchedulingIntegrationTest > reenqueueAllActiveRearmsEveryOpenReminder PASSED
SchedulingIntegrationTest > scheduleEnqueuesDelayedWorkAndSendsWhenDelayIsMet PASSED
SchedulingIntegrationTest > editReplacesWorkAndKeepsSingleJob PASSED
SchedulingIntegrationTest > validationRejectsBadInput PASSED
BUILD SUCCESSFUL in 1m 14s
36 actionable tasks: 34 executed, 2 up-to-date
```

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
