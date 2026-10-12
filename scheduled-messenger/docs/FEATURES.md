# Feature reference

How each feature works in this app, how the established open-source SMS apps do the same thing, and what is still missing. Use it before building or changing a feature. Check how Fossify, QKSMS and Simple SMS solved it first, and record what we did and why.

**Keeping it current:**
- Any commit that adds, changes or fixes a feature updates its entry here, in the same commit.
- Close items in the work queue by marking them fixed with the commit hash. Do not delete them.
- When a decision departs from the reference apps, say why under "Why".

**Reference snapshots** (local clones used for the citations below):

| App | Local path | Snapshot | Notes |
|---|---|---|---|
| Fossify Messages | `/home/user/fossifyorg/messages` | `7af607d` (2026-09-01) | Maintained; same MMS library as us |
| QKSMS | `/home/user/moezbhatti/qksms` | `c86ea10` (2021-10-28) | Older; Klinker android-smsmms |
| Simple SMS Messenger | `/home/user/simplemobiletools/sms` | `84094a4` (2024-06-11) | Archived; Fossify's ancestor |
| org.fossify:mmslib | github.com/FossifyOrg/mmslib | `f84b010` (tag 1.0.0) | Our MMS library |

Fossify and Simple SMS keep avatars, colour customisation, blocking and date formatting in a shared "Commons" library that was not available, so those entries cite the call site only.

**Legend:** P0 = broken or risks data loss · P1 = everyday gap · P2 = nice to have.
- **Verified** means checked by hand against the code during this project.
- **Surveyed** means it comes from the research surveys, with file:line citations, but has not yet been re-checked independently.

---

## 1. Work queue

### Bugs in our code

| ID | Bug | Pri | Evidence | Status |
|---|---|---|---|---|
| B1 | A failed MMS is recorded and shown as sent, once for each retry. The sent receiver imports before the library's base class writes the box, and the base class always writes SENT. | P0 | Surveyed (§3 "Outright bugs" B1) | Open |
| B2 | Duplicate sends. The exact alarm's `runSmsNow` uses `ExistingWorkPolicy.REPLACE`, which can cancel a worker that is already sending. That worker then releases its claim, and the replacement sends again. | P0 | **Verified** (`WorkScheduler.kt` `runSmsNow`, `ScheduledSmsWorker.kt` CancellationException → `releaseClaim`) | Open |
| B3 | A message can stay "Sending…" forever after the process dies mid-send. Nothing sweeps SENDING rows. | P0 | Surveyed (§3 B4) | Open |
| B4 | A failed "Send now" disappears. The draft is cleared, and on failure only the Schedule entry is marked failed. Nothing appears in the conversation and no notification is posted. | P0 | **Verified** (`ScheduledSmsWorker.kt` failure branches only call `markFailed`) | Open |
| B5 | Compressed photos lose their EXIF rotation, so portrait photos arrive sideways. | P0 | **Verified** | **Fixed** in `52e45b7` |
| B6 | Two picture messages in flight at once lose one receipt, and the resulting timeout and retry sends a duplicate. The library's PendingIntent always uses request code 0. | P1 | Surveyed (§3 library note 2, B7) | Open |
| B7 | Undownloaded incoming MMS rows (type 130) are imported as empty "ghost" bubbles. | P1 | Surveyed (§3 B3) | Open |
| B8 | Per-conversation colours follow whoever sent the last message, not the conversation. In groups they change with each speaker and overwrite that member's own style. | P1 | **Verified** (`ThreadViewModel.kt` `address` = last message's address, used for style read and write) | Open |
| B9 | Incoming texts on an `SMS_DELIVER` path can go unwritten. The receiver work is cancelled after 8 s, and the write depends on our own default check, even though receiving `SMS_DELIVER` already proves we are default. | P1 | Surveyed (§3 B6) | Open |
| B10 | Picture-only messages have no long-press menu, so Remind and Mark unread don't work on photos. | P1 | Surveyed (§4 bug 5) | Open |
| B11 | Each new text replaces the previous one in the notification shade. | P1 | Surveyed (§4 bug 8) | Open |
| B12 | The wallpaper-dim slider writes to the database and re-decodes the wallpaper on every drag tick. | P2 | Surveyed (§4 bug 4) | Open |
| B13 | The contacts lookup for the thread title runs on the main thread (first open only, then cached). | P2 | Surveyed (§4 bug 7) | Open |
| B14 | Settings says "long-press a conversation title" to style it, but no such gesture exists. | P2 | Surveyed (§4 bug 9) | Open |
| B15 | The SMS segment counter shows in group threads, which actually send as MMS. | P2 | Surveyed (§4 bug 10) | Open |
| B16 | Draft, queued and outbox rows from the phone's store import as SENT. | P2 | Surveyed (§3 B8) | Open |

### Missing features, by everyday impact

| # | Gap | Pri | Reference to follow |
|---|---|---|---|
| G1 | Message actions: copy, select text, delete, forward, share, details, resend. Copying a one-time code is currently impossible. | P0 | §4 "Message long-press actions" |
| G2 | Contact names and avatars in the list, group titles and group sender labels | P0 | §4 "Avatars", "Conversation title" |
| G3 | Dates: separators in the thread; today/yesterday in the list | P0 | §4 "Date separators", "Timestamp format" |
| G4 | Sending, failed and delivered states in the thread, with resend | P0/P1 | §3 "Sending SMS"; §4 "Delivery / sending indicators" |
| G5 | Notifications: MessagingStyle history, inline reply, Mark-as-read, conversation shortcuts, per-conversation channel | P1 | §3 "Notifications"; §4 §5 |
| G6 | Drafts that survive leaving the conversation | P1 | §4 "Draft saving" |
| G7 | Conversation management: delete (written back to the phone store), archive, pin, block, search, multi-select | P1 | §3 "Threads"; §4 §1 |
| G8 | Two-way sync: deletes and status/read changes made by other apps reach the app | P1 | §3 "Sync with the telephony provider" |
| G9 | Media: full-screen viewer, camera, keyboard GIFs, clickable links/numbers/emails, jump-to-bottom | P1 | §4 "Attachment rendering", "Attachment picker", "Linkification" |
| G10 | Scheduled messages shown inside their own conversation, not only on the Schedule screen | P1 | §4 "Scheduled messages visible inside the thread" |
| G11 | MMS size setting or carrier-config mode; video compression | P2 | §2 "MMS image size budget" |
| G12 | Dual-SIM choice, blocking, backup, share-target intents, vCards | P2 | §3 sections 7, 8, 10, 13, 14 |

---

## 2. Verified features and decision records

### Default SMS app detection
- **Ours:** `DefaultSmsApp.isDefault()` decides by `RoleManager.isRoleHeld(ROLE_SMS)` where RoleManager can answer, falling back to `Telephony.Sms.getDefaultSmsPackage()` only on API 26–28. Rule in `core/.../DefaultSmsDecision.kt`; both readings shown in Settings → Diagnostics and logged on change.
- **Why:** on the owner's Pixel 8a / Android 17, `getDefaultSmsPackage()` returned null while the role was held (device log 2026-09-25: `role held=true, system default=none`). The old check blocked MMS and kept the "make default" banner up. Deliberately not an OR of the two: a false "yes" makes `SmsReceiver` drop `SMS_RECEIVED` and lose texts.
- **Status:** fixed in c6f1f65, confirmed on device.

### MMS image size budget
- **Ours:** one 300 KB budget per message shared by all pictures (`core/.../MmsBudget.kt`), JPEG re-encode loop in `AttachmentStore.readBytesForMms`; send log records per-part before/after size and the carrier's reported max (`AndroidMmsSender`).
- **Fossify:** user setting, default 200 KB — `helpers/Config.kt:56-57` (`mmsFileSizeLimit`, `FILE_SIZE_200_KB`), applied in `activities/ThreadActivity.kt:1508`; sends one MMS per attachment.
- **QKSMS:** user setting, default 300 KB — `domain/.../util/Preferences.kt:119`; option `-1` uses the carrier's `MMS_CONFIG_MAX_MESSAGE_SIZE`; one `remainingBytes` budget across all parts — `data/.../repository/MessageRepositoryImpl.kt:343-352`.
- **Library (org.fossify:mmslib 1.0.0):** passes its own limit to Android as `MMS_CONFIG_MAX_MESSAGE_SIZE` (`Transaction.java:677`): 800 KB (`MmsConfig.java:50`) until `MmsConfig.init` loads the per-carrier `mms_config.xml` (generic: 307200), which only `PushReceiver` triggers. Does no compression itself.
- **Status:** fixed in 831adef (was 900 KB per picture); EXIF rotation applied before re-encoding in `52e45b7` (Fossify `helpers/ImageCompressor.kt:124-134` approach). Open: no user setting or carrier-config mode (QKSMS `-1`); video is not compressed (rejected over 700 KB at pick time).

### MMS store diagnostics
- **Ours:** `diagnostics/MmsStoreProbe` logs the newest MMS-store rows (id, box, PDU type, thread, age) after send receipts, post-send import, downloads and on "Save app log". A leftover type 130 row means an incoming picture was announced but never downloaded.
- **Others:** no equivalent; Fossify shows a toast on download error only (`receivers/MmsReceiver.kt`).

### Contact search in the recipient field
- **Ours:** bulk-load `Phone.CONTENT_URI` once (`ContactsRepository`), fold accents once into `ContactIndex`, filter on `Dispatchers.Default`; suggestions deduplicated by normalized address (crash 2026-09-23: duplicate LazyColumn key from a contact synced from two accounts).
- **QKSMS:** bulk load `CursorToContactImpl.kt` (no selection), filter in memory `filter/ContactFilter.kt`, recents/starred/groups/people order in `feature/contacts/ContactsViewModel.kt:95-165`, on `Schedulers.computation()`.
- **Fossify:** in-memory filter + prefix-first sort, `activities/NewConversationActivity.kt:93-108`.
- **Status:** matches both. Open: no avatars, no number-type labels (Mobile/Work), no groups.

### Phone-number recognition for "new number" suggestions
- **Ours:** platform `android.telephony.PhoneNumberUtils.isWellFormedSmsAddress` / `formatNumber` (`PlatformPhoneNumberRecognizer`), behind the `core` `PhoneNumberRecognizer` interface.
- **Fossify:** platform `PhoneNumberUtils` only (e.g. `messaging/SmsSender.kt:33`), no phone-number library.
- **QKSMS:** `io.michaelrocks:libphonenumber-android` wrapped in an injected singleton (`data/.../util/PhoneNumberUtils.kt:29-33`).
- **Why ours:** the libphonenumber Android port cost three failures here (theorized metadata crash, compileSdk 36 requirement from 9.0.30, then a compose-screen crash); Fossify shows the platform class is enough for this feature.

### Read / unread state
- **Ours:** per-message `isRead` (Room migration 1→2, history defaults read), incoming arrives unread, opening a thread reads it and draws a "New" divider at the first unread, long-press "Mark unread"/"Mark read" on one message, synced to the telephony provider when default (`SystemMessageStore.setMessageRead`). Visit logic in `core/.../ReadState.kt`.
- **Others:** conversation-level read/unread only (no per-message mark-unread); see UI survey below for their list styling.
- **Status:** shipped d84c2a3. Open: "New" line does not scroll into view.

### Diagnostics (no adb)
- **Ours:** `CrashHandler` writes stack + app log tail + own-pid logcat to `filesDir/diagnostics/last_crash.txt` then defers to the platform handler; `AppLog` rolling breadcrumb file; Settings → Diagnostics saves either to Downloads via `DiagnosticFile`.
- **Others:** none of the three ships an in-app crash export.
- **Open:** only the last crash is kept; the unread feature writes no breadcrumbs.

---

## 3. Messaging & data survey (2026-10-12)


Read-only survey. Snapshots used:

| Code | Path prefix used below | Snapshot |
|---|---|---|
| **Ours** | `app:` = `/home/user/kiwi-cup/scheduled-messenger/app/src/main/java/com/kiwicup/scheduledmessenger/`; `core:` = `.../scheduled-messenger/core/src/main/kotlin/com/kiwicup/scheduledmessenger/core/`; manifest = `.../app/src/main/AndroidManifest.xml` | git `831adef` (includes "Shrink picture messages to a 300 KB budget", 2026-10-12) |
| **Fossify** | `FOS:` = `/home/user/fossifyorg/messages/app/src/main/kotlin/org/fossify/messages/`; manifest = `.../app/src/main/AndroidManifest.xml` | git `7af607d` (2026-09-01) |
| **QKSMS** | `QK-data:` = `/home/user/moezbhatti/qksms/data/src/main/java/com/moez/QKSMS/`, `QK-domain:` = `.../domain/src/main/java/com/moez/QKSMS/`, `QK-pres:` = `.../presentation/src/main/java/com/moez/QKSMS/`, `QK-lib:` = `.../android-smsmms/src/main/java/` | git `c86ea10` (2021-10-28) |
| **Simple SMS** | `SSM:` = `/home/user/simplemobiletools/sms/app/src/main/kotlin/com/simplemobiletools/smsmessenger/` | git `84094a4` (2024-06-11, archived) — uses `com.github.tibbi:android-smsmms`, not `org.fossify:mmslib` |
| **mmslib** (ours + Fossify) | `LIB:` = `library/src/main/java/` in a clone of github.com/FossifyOrg/mmslib | tag `1.0.0` = `f84b010` |

"Not found" means I searched and did not find it. It does not mean the feature is definitely absent. Fossify and Simple SMS both call `isNumberBlocked` / `blockUnknownNumbers` from their *commons* library, whose source is not on this machine, so I have not verified how those calls work.

---

#### 1. Sending SMS (multipart, sent/delivered status, retries, counter, groups)
- **Ours:** `app:data/sms/AndroidSmsSender.kt` splits with `SmsManager.divideMessage` and sends with `sendMultipartTextMessage(address, null, parts, sentIntents, null)` (L66). Each send uses its own action string, and every PendingIntent is `FLAG_IMMUTABLE` (L62). The call then suspends until every part reports back, or until a 60 s timeout (L103) that counts as a transient failure. `failureFor` (L90) maps result codes to Transient or Permanent. **There is no delivery-report PendingIntent (the `null` at L66) and no subscription id.** Nothing is written to the provider before sending (no OUTBOX row). After a confirmed send, `app:data/inbox/SentMessageRecorder.kt` inserts a `TYPE_SENT` row (`app:data/system/AndroidSystemMessageStore.kt` L58-L80, only when we hold the role) plus a Room row. A failed send never reaches the provider or the conversation; it shows only in the Queue screen. Retries come from WorkManager: `app:work/ScheduledSmsWorker.kt` `MAX_ATTEMPTS = 3` (L119) with exponential 30 s backoff (`app:work/WorkScheduler.kt` L50). The counter is `core:SmsTextAnalyzer.kt`, our own GSM-7/UCS-2 arithmetic, shown by `app:ui/components/MessageInputBar.kt` L49. Groups: if there is more than one recipient, the message always goes as one group MMS (`ScheduledSmsWorker.kt` L67). Sending to each person as an individual SMS is not offered.
- **Fossify:** `FOS:messaging/MessagingUtils.kt` L32-L85 inserts an **OUTBOX** row into `Sms.CONTENT_URI` *before* sending, with `SUBSCRIPTION_ID`, and then calls `SmsSender.sendMessage` (L88-L124). `FOS:messaging/SmsSender.kt` builds one sent PendingIntent per part and a delivery PendingIntent on the last part when delivery reports are on (L66-L89). The intent data is the message URI, the flags are `FLAG_MUTABLE` on S+ (L61-L64), and the send uses the subscription's SmsManager (`FOS:messaging/SmsManager.kt`). `FOS:receivers/SmsStatusSentReceiver.kt` L64-L69 turns the row into SENT or FAILED in the provider and in Room, and posts a "sending failed" notification when the app is in the background (L84, L95-L107). `FOS:receivers/SmsStatusDeliveredReceiver.kt` L128-L169 writes `STATUS` and `DATE_SENT`, including the CDMA status mapping. There is no automatic retry; the user resends from the failed bubble (`messageToResend`, ThreadActivity L1682). The counter is the platform's `SmsMessage.calculateLength` (ThreadActivity L893). Groups: `GroupMessageSendDialog` asks once whether to send group MMS or individual SMS (ThreadActivity L1608-L1628), remembered in `config.sendGroupMessageMMS` (`FOS:messaging/Messaging.kt` L28). When individual SMS is chosen, it writes a "dummy" SENT row into the group thread plus one SMS per address (MessagingUtils L95-L123).
- **QKSMS:** `QK-data:repository/MessageRepositoryImpl.kt` `insertSentSms` (L501-L556) writes an OUTBOX row to Realm and to the provider, with sub id. `sendSms` (L437-L470) passes sent intents, plus delivery intents when `prefs.delivery` is on (default false, `QK-domain:util/Preferences.kt` L113). `QK-data:receiver/SmsSentReceiver.kt` routes to `MarkSent` or `MarkFailed`. Manual retry goes through `QK-domain:interactor/RetrySending.kt`. QKSMS also has an optional **send delay / undo** (`prefs.sendDelay`, AlarmManager, MessageRepositoryImpl L324-L335). Groups: a per-compose toggle `sendAsGroup` (Preferences L91, `QK-pres:feature/compose/ComposeViewModel.kt` L654-L700); when it is off, each address gets its own SMS. Counter: `SmsMessage.calculateLength` (ComposeViewModel L593).
- **Simple SMS:** Same design as Fossify (`SSM:messaging/MessagingUtils.kt`, `SSM:messaging/SmsSender.kt`). It has no group-send confirmation dialog; only the setting exists.
- **Gap / risk for us:** *Bug risks:* (a) a receipt timeout is treated as transient and retried, so a message that went out late is sent twice. (b) No OUTBOX row is written: if the process dies after the radio hand-off, the sent text never appears in history (see bug B4). *Missing features:* delivery reports, a failed bubble with resend in the conversation, and a choice between group MMS and individual SMS. `FLAG_IMMUTABLE` on the sent PendingIntent also means the platform cannot attach the `errorCode` extra.
- **Suggested priority:** P1

#### 2. Sending MMS (size limits, compression, parts, attachment types, failure status, subject)
- **Ours:** `app:data/sms/AndroidMmsSender.kt` refuses unless we hold the role (L54). Settings are `useSystemSending=true`, `group=true`, `deliveryReports=false` (L70-L73), with no subscription id. **All attachments go in one MMS.** Size: since `831adef`, `core:MmsBudget.kt` fixes one **300 KB budget per message**, shared by the pictures after the text and non-image parts are subtracted (minimum 40 KB per picture). The carrier's `MMS_CONFIG_MAX_MESSAGE_SIZE` is read but **only logged** (`carrierMaxMessageBytes`, AndroidMmsSender ~L116). Compression is `app:data/system/AttachmentStore.kt` `readBytesForMms` (L56 onward): `BitmapFactory.decodeByteArray`, sampled to 1600 px, then re-encoded as JPEG at quality 85→40, halving the size until it fits. **EXIF orientation is not applied.** Non-image files go out untouched; at pick time they are refused above 700 KB (`NON_IMAGE_MAX_BYTES`). Parts are named with `libMessage.addMedia(bytes, mime)` and no name (L91), so the library names every part after its MIME major type (`"image"`, `"video"`; `LIB:com/klinker/android/send_message/Transaction.java` L442-L446). Two pictures therefore get the same Content-Location and Content-ID. The picker allows images and video only (`app:ui/navigation/AppNavHost.kt` L134, L201); there is no audio, vCard or file option. Failure handling: `app:receivers/MmsSentReceiverImpl.kt` relays the result code to the waiting sender, which treats any non-OK as **Transient** and retries up to 3 times. There is no subject field.
- **Fossify:** `FOS:messaging/Messaging.kt` L52-L66 decides MMS when there are attachments, a long text with "send long as MMS" on, or a group with group MMS on. It **sends one MMS per attachment**, with the text attached only to the last one (L55-L66). The limit is a **user setting** `mmsFileSizeLimit`, default **200 KB** (`FOS:helpers/Config.kt` L56-L58; options in `helpers/Constants.kt` L78-L84). Images are compressed at attach time by `FOS:helpers/ImageCompressor.kt` (quality first, then 0.6× resolution steps, with **EXIF rotation** in `determineImageRotation` L124), called from `FOS:adapters/AttachmentsAdapter.kt` L136-L140. GIFs and non-images over the limit are refused (ThreadActivity L1505-L1513). Parts are named with the file name: `message.addMedia(bytes, mime, name, name)` (`FOS:messaging/MessagingUtils.kt` L181-L182). Plain text goes as `application/txt` (L176-L180). Any file type is allowed, and vCards have a viewer (`activities/VCardViewerActivity.kt`). Failure: Fossify's **own** `FOS:receivers/MmsSentReceiver.kt` (it does not extend the library's) sets `MESSAGE_BOX_FAILED` on non-OK (L234-L247) and deletes the original when resending (L253-L255). Subject: not sent. Carrier config: not used.
- **QKSMS:** One MMS holds all parts (`QK-data:repository/MessageRepositoryImpl.kt` L340-L434). The size comes from `prefs.mmsSize`, default **300 KB** (Preferences L119). A value of **-1 uses the carrier's `getCarrierConfigValues()` `MMS_CONFIG_MAX_MESSAGE_SIZE` / `MAX_IMAGE_WIDTH/HEIGHT`** (L343-L353), and 0 means no limit. One `remainingBytes` budget (×0.9) is shared and split across images in proportion to their size, with iterative area scaling (L380-L421). Scaling uses Glide (`QK-data:util/ImageUtils.kt` L42-L50), which honours EXIF. Supported attachments are images, GIFs and contacts (`"contact"`, `text/vcard`, L360-L367); there is no video. Parts are named `"text"`, `"contact"` and `"image"` (all images share the name `"image"`). Failure: `QK-data:receiver/MmsSentReceiver.kt` sets SENT (L53) or FAILED (L63) and then syncs. Subject: null (L433).
- **Simple SMS:** Same as Fossify, except the default limit is **600 KB** (`SSM:helpers/Config.kt` L47-L49).
- **Gap / risk for us:** *Bug:* our receiver inherits the library's `MmsSentReceiver.updateInInternalDatabase`, which **always** writes `MESSAGE_BOX_SENT` whatever the result code (`LIB:.../MmsSentReceiver.java` L46). Our import also runs *before* that update (`LIB:.../StatusUpdatedReceiver.java` L21-L22), and an outbox row imports as SENT (`SmsInboxImporter.kt` L137). A failed MMS therefore shows as sent, once per retry attempt (bug B1). *Bug:* photos lose their EXIF rotation when re-encoded, so portrait camera shots arrive sideways (B5). *Risk:* every part has the same name, so some recipient clients may collapse multi-picture messages; this is unverified on device. *Risk:* the library passes `MMS_CONFIG_MAX_MESSAGE_SIZE = MmsConfig.getMaxMessageSize()` to the platform (`LIB:.../Transaction.java` L677). That value is **800 KB until `MmsConfig.init` has run in the process**, which only happens in `PushReceiver` (L279). After the first incoming MMS it becomes the mcc/mnc `res/xml*/mms_config.xml` value (the generic one is **307200**). So the commit's "the library tells Android 800 KB" holds only for processes that have not received an MMS. *Missing:* a user or carrier-driven limit, audio/vCard/file attachments, and video compression.
- **Suggested priority:** P0 (failed MMS reported as sent), P1 (EXIF), P2 (rest)

#### 3. Receiving SMS
- **Ours:** One receiver, `app:receivers/SmsReceiver.kt`, handles both `SMS_RECEIVED` and `SMS_DELIVER` (manifest L120-L121). When we hold the role, `SMS_RECEIVED` is ignored (L31). The parts are reassembled using `first.timestampMillis`, the SMSC time (L47). Work runs under `ReceiverScope` (goAsync plus an **8 s `withTimeout`**, `app:receivers/ReceiverScope.kt` L17, L24). `app:data/inbox/IncomingSmsHandler.kt` first de-duplicates against *local-only* rows (L32). It then inserts into the provider only if `isDefaultSmsApp()` is true (`AndroidSystemMessageStore.kt` L58). The insert sets `DATE=now`, `DATE_SENT=SMSC`, `READ=0`, **`SEEN=1`**, and no `SUBSCRIPTION_ID` or `SUBJECT` (L60-L72). It then writes Room `isRead=false` (L48), and a notification is posted for `SMS_DELIVER` only (`SmsReceiver.kt` L36).
- **Fossify:** `FOS:receivers/SmsReceiver.kt` listens to `SMS_DELIVER` only (manifest L184-L191). It calls `goAsync` (L33), applies the keyword, blocked-number and unknown-number filters (L48-L55), and inserts with `SUBSCRIPTION_ID` and `SUBJECT` (`FOS:extensions/Context.kt` `insertNewSMS` L821-L849, `DATE=now`, `READ=0`, no SEEN). It then updates Room, unarchives (L140-L141) and notifies. There is no duplicate check.
- **QKSMS:** `QK-data:receiver/SmsReceiver.kt` (`SMS_DELIVER`, goAsync L41-L42) passes to `QK-domain:interactor/ReceiveSms.kt` L45-L95. That checks the blocking client, with optional drop (L52-L59), inserts into Realm and `Sms.Inbox` with sub id (`MessageRepositoryImpl.kt` L558-L597; `read` is true if the thread is on screen), unarchives, and updates notification, shortcuts and badge.
- **Simple SMS:** Like Fossify, but **no `goAsync`** (`SSM:receivers/SmsReceiver.kt` L21-L34 uses `ensureBackgroundThread` only), so the process may be killed mid-write.
- **Gap / risk for us:** *Risk:* if the 8 s deadline fires before the provider insert, an `SMS_DELIVER` text is lost, because the platform deletes its raw PDU once the broadcast finishes. The provider write is also gated on our own default check, when the arrival of `SMS_DELIVER` already proves we are the default (B6). *Missing:* `SUBSCRIPTION_ID` on received rows, which dual-SIM replies need. *Minor:* Room shows SMSC time while the provider shows receipt time.
- **Suggested priority:** P1

#### 4. Receiving MMS (WAP push, download, retry, stalled m_type 130 rows, no data)
- **Ours:** The library's `com.android.mms.transaction.PushReceiver` is declared for `WAP_PUSH_DELIVER` (manifest L77-L86). It persists the NotificationInd (m_type 130) to the inbox and calls `SmsManager.downloadMultimediaMessage` (`LIB:com/android/mms/transaction/PushReceiver.java` L172-L229; `LIB:.../DownloadManager.java` L45-L98). The download result goes to `MmsReceivedReceiver`, which persists the RetrieveConf as a **new row** and deletes the 130 row (`LIB:com/android/mms/service_alt/DownloadRequest.java` `persist`, the delete is near L239). It then calls our `app:receivers/MmsReceivedReceiverImpl.kt`, which imports and notifies (L34-L43). On failure the library records `RETRIEVE_STATUS` on the 130 row and **does not call `onError`** unless there was an I/O exception (`LIB:.../MmsReceivedReceiver.java` L138-L176); our `onError` only logs (L49). **There is no retry:** nothing starts `TransactionService`, and `PushReceiver`'s static `downloadedUrls` and `isDuplicateNotification` (L172, L200) block a re-download in the same process. Auto-download is effectively always on: `PushReceiver` downloads unconditionally, and `allowAutoDownload` only changes whether the 130 row gets a thread (L185). Without mobile data it depends on the platform MmsService and the carrier's MMS APN; there is no app-side handling. The importer has **no m_type filter** (`app:data/inbox/SmsInboxImporter.kt` L109), so a 130 row is imported as an empty incoming MMS.
- **Fossify:** Uses the same library. `FOS:receivers/MmsReceiver.kt` overrides `isAddressBlocked` and `isContentBlocked` (L186-L199) and shows a toast "couldn't download MMS" from `onError` (L215). There is no retry or download button (no "download" UI found), and `getMMS` has no m_type filter either (`FOS:extensions/Context.kt` L219-L240).
- **QKSMS:** Its own fork `QK-lib`. `QK-data:receiver/MmsReceivedReceiver.kt` hands to `QK-domain:interactor/ReceiveMms.kt` (sync the URI, then block or drop, L47-L62). The fork still contains `TransactionService` and `RetryScheduler` (`QK-lib:com/android/mms/transaction/TransactionService.java` L286-L316), but I found no code in the app that starts them. No download-retry UI was found.
- **Simple SMS:** Same as Fossify.
- **Gap / risk for us:** *Bug:* a 130 row imported before its download finishes, or one that never downloads, becomes a permanent empty bubble. The importer goes only by id and never deletes, so when the download lands as a new row the empty "ghost" stays (B3). *Missing (in all four apps):* retry or manual "tap to download" for stalled 130 rows, and a user-visible error. We would be ahead of all three if we filtered `m_type=130` out of the import and offered a retry that calls `DownloadManager.downloadMultimediaMessage` again.
- **Suggested priority:** P1

#### 5. Sync with the telephony provider
- **Ours:** `app:data/inbox/SmsInboxImporter.kt` copies rows with `_id > MAX(systemId)` for SMS and `> MAX(mmsSystemId)` for MMS (L39-L54, L60, L109) into Room. Triggers: every resume while the permission gate is READY (`app:ui/permissions/PermissionGate.kt` L149), after an MMS is downloaded or sent, and after the worker sends an MMS. **There is no ContentObserver, no `EXTERNAL_PROVIDER_CHANGE` handler and no `DEFAULT_SMS_PACKAGE_CHANGED` handler.** Deletes, read flags, box or status changes and edits made by other apps are **never** picked up, and rows below the max id are never re-read. Thread ids are the provider's `thread_id`. Local-only threads, created while we are not the default, use negative ids and are merged by `ThreadResolver.mergeLocalInto`. Read flags: the provider `READ` is read once at import (L96-L97). Opening a thread writes `READ/SEEN=1` (`AndroidSystemMessageStore.kt` L48-L55), and per-message unread writes `READ=0` (L29-L41). There is no import lock, and each MMS row runs 3 or more extra queries (addr, parts, conversations plus canonical addresses), which is slow on a large first import.
- **Fossify:** Room holds a cache. `FOS:activities/MainActivity.kt` `getCachedConversations` / `getNewConversations` (L289-L367) re-query the provider each time the activity is created. Conversations that vanished from the provider are deleted from the cache (L334), and changed ones are updated. `ThreadActivity.setupThread` re-reads the thread's messages from the provider on every open (L474-L495). Read-state writes cover both provider and Room (`FOS:extensions/Context.kt` L1008-L1043). There is no ContentObserver.
- **QKSMS:** A full re-sync wipes and rebuilds Realm, keeping archived, pinned, blocked and name flags (`QK-data:repository/SyncRepositoryImpl.kt` L74-L213). It runs on first launch and when permissions arrive (`QK-pres:feature/main/MainViewModel.kt` L113-L115, L159), from Settings → Sync, and on **`DEFAULT_SMS_PACKAGE_CHANGED`** (`QK-data:receiver/DefaultSmsChangedReceiver.kt` L41). Per-message `syncMessage(uri)` (L216-L264) runs from **`EXTERNAL_PROVIDER_CHANGE`** (`QK-data:receiver/SmsProviderChangedReceiver.kt` L52; manifest L164-L168), from MMS sent/updated, and on receive.
- **Simple SMS:** Same as Fossify.
- **Gap / risk for us:** *Bug:* Room only ever grows. A deletion or status change in the provider (by us via another path, by another app before or while we are default, or by restore tools) is never mirrored. Combined with 2 and 4, Room drifts from the truth. *Missing:* a reconciliation pass (re-query on resume like Fossify, or `EXTERNAL_PROVIDER_CHANGE` plus a periodic full diff like QKSMS), and a re-sync when the role is regained.
- **Suggested priority:** P1

#### 6. Threads: delete, archive, pin, drafts, search
- **Ours:** **Delete:** absent from the UI. `SmsMessageDao.deleteThread`/`deleteAll` exist (`app:data/local/dao/SmsMessageDao.kt` L148, L151) but nothing calls them, and nothing deletes from the provider. **Archive:** absent. **Pin:** absent. **Drafts:** in memory only (`app:ui/thread/ThreadViewModel.kt` L94, `MutableStateFlow("")`); a draft is lost on back-navigation or process death, and staged attachments are lost from the list though their files remain in `filesDir/attachments`. **Search across messages:** absent; only contact search exists in `RecipientField`. The message long-press menu offers only "Remind me" and "Mark read/unread" (`app:ui/components/MessageBubble.kt` L108-L127); there is no copy, delete or forward.
- **Fossify:** **Delete** writes through to the provider: a conversation via `Sms`/`Mms` `THREAD_ID` deletes (`FOS:extensions/Context.kt` `deleteConversation` L865-L893), a message via `deleteMessage` L976-L986. There is an optional **recycle bin**, a Room table with 30-day purge (L895-L947, `config.useRecycleBin`). **Archive** writes the provider `Threads.ARCHIVED` column, with a fallback when it is missing (L949-L974); incoming messages unarchive (`FOS:receivers/SmsReceiver.kt` L140-L141). **Pin:** prefs set `pinnedConversations` (`FOS:helpers/Config.kt` L60-L66). **Drafts:** Room `DraftsDao` (`FOS:extensions/Context.kt` L1134-L1179). **Search:** Room `LIKE` over messages and conversations (`FOS:activities/MainActivity.kt` L521-L541; `FOS:interfaces/MessagesDao.kt` L57).
- **QKSMS:** **Delete** writes through: messages via `MessageRepositoryImpl.deleteMessages` (L714-L728), conversations via a `Threads` URI delete (`QK-data:repository/ConversationRepositoryImpl.kt` L406-L421). There is also auto-delete of old messages (`QK-data:service/AutoDeleteService.kt`, `DeleteOldMessages`). **Archive and pin:** Realm only (ConversationRepositoryImpl L325, L349). **Drafts:** Realm `Conversation.draft` (L288-L300). **Search:** Realm `contains` over `body` and `parts.text` (L132-L150).
- **Simple SMS:** Same as Fossify for delete, archive, pin, recycle bin and search. **Drafts are stored in the provider** (`Sms.Draft.CONTENT_URI`, `SSM:extensions/Context.kt` L913-L964), unlike Fossify's Room table.
- **Gap / risk for us:** *Missing (daily use):* deleting messages and conversations, written to the provider; persistent drafts; message search. Archive and pin are nice to have.
- **Suggested priority:** P1 (delete, drafts, search), P2 (archive, pin)

#### 7. Blocking & spam
- **Ours:** Absent at the app level: no number blocking or keyword filter, and `MmsReceivedReceiverImpl` does not override `isAddressBlocked`/`isContentBlocked`. (Not verified in this repo: the platform's own `BlockedNumberContract` list is applied by telephony before `SMS_DELIVER` / WAP push reaches the default app.)
- **Fossify:** Blocked numbers through commons `isNumberBlocked`, plus a "block unknown numbers" option (`FOS:receivers/SmsReceiver.kt` L49-L55; `FOS:receivers/MmsReceiver.kt` L186-L195). **Keyword blocking** is in `FOS:helpers/ReceiverUtils.kt` L8-L16 (managed in `activities/ManageBlockedKeywordsActivity.kt`, import/export in `helpers/BlockedKeywords*`), and MMS goes through the mmslib `isContentBlocked` hook (`MmsReceiver.kt` L197-L199). No spam detection was found.
- **QKSMS:** A pluggable `BlockingClient`: built-in Realm list (`QK-data:blocking/QksmsBlockingClient.kt`), or the third-party Call Control, Should I Answer or CallBlocker apps (`QK-data:blocking/*BlockingClient.kt`, chosen in `BlockingManager.kt` L22). Blocked messages are either dropped or kept in a blocked folder (`ReceiveSms.kt` L52-L76). No keyword blocking was found.
- **Simple SMS:** Numbers through commons, plus keyword blocking (`SSM:receivers/SmsReceiver.kt` L69, L126-L127). The keyword check is not applied to MMS (`SSM:receivers/MmsReceiver.kt` L27 checks the number only).
- **Gap / risk for us:** *Missing:* an in-app block list (ideally reading and writing `BlockedNumberContract`, so it matches the dialer) and keyword filters. None of the four apps uses `BlockedNumberContract` directly in its own source.
- **Suggested priority:** P2

#### 8. Dual-SIM / subscription selection
- **Ours:** Absent. SMS uses the context `SmsManager` (`AndroidSmsSender.kt` L82-L88 `smsManager()`), MMS uses `Settings.subscriptionId` with its default of -1, and no `SUBSCRIPTION_ID` is stored on sent or received rows.
- **Fossify:** A SIM toggle in the composer from `activeSubscriptionInfoList` (`FOS:activities/ThreadActivity.kt` L1072-L1131). The choice is remembered per number (`config.getUseSIMIdAtNumber`, `FOS:helpers/Config.kt` L17) and passed as `subId` to SMS and MMS (`Messaging.kt` L47-L49). Received SIM is stored (`SmsReceiver.kt` L59).
- **QKSMS:** The composer defaults to the SIM of the last message in the thread and offers a toggle (`QK-pres:feature/compose/ComposeViewModel.kt` L220-L225, L616-L643). Sub id is stored on rows when `canUseSubId` is set (`MessageRepositoryImpl.kt` L532-L534, L585-L587).
- **Simple SMS:** Same as Fossify.
- **Gap / risk for us:** *Missing.* *Risk (unverified):* with "Ask every time" as the system SMS SIM, a background scheduled send via the default SmsManager may fail. Fossify maps `RESULT_NO_DEFAULT_SMS_APP` to "SIM not available" (`MessagingUtils.kt` L210). We would treat it as Transient, retry 3 times and then fail.
- **Suggested priority:** P2 (P1 if the user has two active SIMs)

#### 9. Scheduled sending
- **Ours:** Room `scheduled_messages` holds the queue (`app:data/local/entity/ScheduledMessage.kt`). Status changes are guarded `UPDATE`s (`app:data/local/dao/ScheduledMessageDao.kt`), with a 10-minute stale-claim takeover (L68, L126). A WorkManager one-time job per message carries the delay (`app:work/WorkScheduler.kt` L41-L61). **Plus** an exact alarm, `setExactAndAllowWhileIdle` (`app:work/ExactAlarms.kt` L35), whose receiver re-enqueues the job with **REPLACE** (`WorkScheduler.kt` L64-L71). Reboot, update, time-change and timezone-change re-arm through `BootCompletedReceiver` → `RearmWorker`, and on every app start (`ScheduledMessengerApp.onCreate`). A message more than 6 h late is marked "Missed" (`core:SchedulingPolicy.kt` L39-L50; `ScheduledMessageRepository.kt` L131). Pending, sent, failed and cancelled messages show in the Queue screen; they are **not shown inline in the thread**. "Send now" also goes through this pipeline with delay 0 (`ThreadViewModel.kt` L266), as non-expedited work.
- **Fossify:** The scheduled message is a Room `Message` with `is_scheduled=1`, in a temporary thread if the conversation is new (`FOS:extensions/Context.kt` `createTemporaryThread` ~L1283), and it is **shown inline in the thread**; tapping it lets the user edit, delete or send now (ThreadActivity L691, L725-L727). Sending uses **AlarmManager only**: `AlarmManagerCompat.setExactAndAllowWhileIdle`, request code = message id (`FOS:messaging/ScheduledMessage.kt` L17-L32). `FOS:receivers/ScheduledMessageReceiver.kt` takes a 1-min wakelock and `goAsync` (L30). It posts `sendMessageCompat` to the main thread, then **immediately deletes the scheduled row**, before the send result is known (L47-L66), so the outcome is fire-and-forget. Reboot is covered by `RescheduleAlarmsReceiver` (manifest L234-L244, BOOT/MY_PACKAGE_REPLACED/TIME_SET/TIMEZONE_CHANGED) → `rescheduleAllScheduledMessages` (`Context.kt` L1349-L1358). That re-arms every row, and alarms in the past fire immediately. **Data-loss behaviour:** on app launch, `clearExpiredScheduledMessages` silently **deletes** scheduled messages more than 1 minute overdue (`Context.kt` L1328-L1347, called from MainActivity L310 and ThreadActivity L449).
- **QKSMS:** A Realm `ScheduledMessage` (`QK-data:repository/ScheduledMessageRepositoryImpl.kt`) is shown in a separate `QK-pres:feature/scheduled/ScheduledActivity.kt`. It uses an AlarmManager exact alarm (`QK-data:manager/AlarmManagerImpl.kt` L35-L42). `BootReceiver` (L30-L37) → `UpdateScheduledMessageAlarms` (`QK-domain:interactor/UpdateScheduledMessageAlarms.kt` L32-L42) re-arms everything and **sends overdue ones immediately, however late**. `SendScheduledMessage` honours the per-message `sendAsGroup` and deletes the row after handing off (L1-L30 of the interactor). Scheduling is behind QKSMS+ (ComposeViewModel "compose_schedule" upsell).
- **Simple SMS:** Same AlarmManager design as Fossify, but there is **no boot receiver, so alarms are lost on reboot** (no `BOOT_COMPLETED` in the manifest, no `rescheduleAll`), no `goAsync`, and only a 3 s wakelock (`SSM:receivers/ScheduledMessageReceiver.kt` L22-L30).
- **Gap / risk for us:** Ours is the most robust: it is the only one with a staleness policy and a persisted SENT/FAILED outcome. *Bugs:* (B2) the exact-alarm REPLACE can cancel a worker that is already sending, and the cancellation path releases the claim, so the message is sent twice. (B4) If the process dies mid-send, the row stays `SENDING` for good when WorkManager re-runs the job within 10 minutes, and the Queue offers no action for SENDING (`QueueScreen.kt` L214). *Missing:* pending scheduled messages shown inline in the thread, as Fossify does.
- **Suggested priority:** P0 (B2, B4), P2 (inline display)

#### 10. Backup / export / import
- **Ours:** Absent. `android:allowBackup="false"` (manifest L29), so the scheduled queue, reminders and per-conversation styles are not even included in Android backup. Only diagnostics export exists (Settings).
- **Fossify:** JSON export of SMS and MMS including parts and addresses (`FOS:helpers/MessagesReader.kt`, `FOS:dialogs/ExportMessagesDialog.kt`). Import accepts JSON or **XML (SMS Backup & Restore format)** (`FOS:helpers/MessagesImporter.kt` L48, L118) and writes to the provider with existence checks (`FOS:helpers/MessagesWriter.kt` L25-L60). Blocked keywords have their own export/import.
- **QKSMS:** JSON backup of Realm messages to a file (`QK-data:repository/BackupRepositoryImpl.kt` L94-L135). Restore inserts into `Telephony.Sms.CONTENT_URI` (L174-L235, insert at L217) and then runs a full sync. Gated behind QKSMS+ (`QK-pres:feature/backup/BackupPresenter.kt` L96).
- **Simple SMS:** Same as Fossify.
- **Gap / risk for us:** *Missing.* At minimum, export and import of our own Room-only data (queue, reminders, styles). Message export is lower priority because the provider remains the source of truth.
- **Suggested priority:** P2

#### 11. Default-SMS-app handling
- **Ours:** `app:data/system/DefaultSmsApp.kt` reads both `RoleManager.isRoleHeld(ROLE_SMS)` (L24) and `Telephony.Sms.getDefaultSmsPackage` (L29). `core:DefaultSmsDecision.kt` L23 lets **the role decide whenever it can answer** (`roleHeld ?: (pkg == ours)`), and logs any disagreement. The request goes through `RoleManager.createRequestRoleIntent` on Q+, else `ACTION_CHANGE_DEFAULT` (L63-L75). The `PermissionGate` offers the role first, once, with diagnostics (`app:ui/permissions/PermissionGate.kt`; `core:RestrictedPermissions.kt` `gateState` L225-L247). The conversations list re-checks on resume and shows a "Set as default" banner (`AppNavHost.kt` L81-L85). Required components: `HeadlessSmsSendService` with `RESPOND_VIA_MESSAGE` (manifest L59-L71; `app:services/HeadlessSmsSendService.kt` sends each recipient as SMS immediately, L45-L49), `SENDTO`/`VIEW` for sms/smsto/mms/mmsto, `SMS_DELIVER`, and `WAP_PUSH_DELIVER`. Losing the role: the app keeps running in "non-default" mode (it listens to `SMS_RECEIVED`, keeps local-only rows, and refuses MMS). There is no `DEFAULT_SMS_PACKAGE_CHANGED` receiver, so the state is noticed only on resume or receive.
- **Fossify:** `FOS:activities/MainActivity.kt` L217-L237: on Q+ `isRoleAvailable` / `isRoleHeld` / `createRequestRoleIntent`, else `getDefaultSmsPackage == packageName` plus `ACTION_CHANGE_DEFAULT`. **If the user declines, the activity calls `finish()`** (L197-L205), so the app cannot be used without the role. Headless service: manifest L163-L176, sends via `sendMessageCompat` with the default sub (`FOS:services/HeadlessSmsSendService.kt` L18-L23). There is no other default check in the app.
- **QKSMS:** `QK-data:manager/PermissionManagerImpl.kt` L32-L38: **`RoleManager.isRoleHeld` on Q+**, `getDefaultSmsPackage` below Q. The request goes through `QK-pres:common/Navigator.kt` L89-L92. On losing the role, the main screen shows a "set default" prompt (`MainViewModel.kt` bindView) and `DefaultSmsChangedReceiver` re-syncs when the role is regained. Headless: `QK-data:service/HeadlessSmsSendService.kt` L35-L45 (IntentService).
- **Simple SMS:** Same as Fossify (`SSM:activities/MainActivity.kt` L219, L230).
- **Gap / risk for us:** We match the best practice (role first), and we are the only app that diagnoses the Pixel 8a / Android 17 case where `getDefaultSmsPackage` returns null while the role is held. **Caveat:** mmslib still uses `getDefaultSmsPackage` internally (`LIB:com/klinker/android/send_message/Utils.java` L428-L431), via `TransactionService` (L183) and the library's SMS path (`Transaction.java` L395). Our MMS path does not go through those today, but anything that starts `TransactionService` (for example a future download-retry built on it) would silently refuse on that phone. *Missing:* a `DEFAULT_SMS_PACKAGE_CHANGED` receiver to re-import when the role is regained.
- **Suggested priority:** P2

#### 12. Notifications (added: not in the original list)
- **Ours:** `app:notifications/IncomingMessageNotifier.kt` posts one notification per thread, replacing the previous one each time (L63). The title is the contact name and the text is the newest body only (L34-L63). There are **no reply or mark-read actions**, no `MessagingStyle` and no per-conversation channel. The notification is cancelled when the thread opens.
- **Fossify:** `FOS:helpers/NotificationHelper.kt`: `MessagingStyle` that keeps earlier lines (L254-L283), **direct reply** with `RemoteInput` → `DirectReplyReceiver` (L98-L157), **mark as read** (L70), **delete** (L81), a lock-screen visibility setting, and a "sending failed" notification (L192).
- **QKSMS:** `QK-pres:common/util/NotificationManagerImpl.kt`: configurable actions including mark read (L261) and reply (L268-L277), and per-conversation notification settings.
- **Simple SMS:** Same as Fossify.
- **Gap / risk for us:** *Missing:* reply and mark-read from the notification, and stacked message history.
- **Suggested priority:** P1

#### 13. Inbound share / compose intents (added)
- **Ours:** `SENDTO`/`VIEW` only (manifest L46-L55). There is **no `ACTION_SEND`/`SEND_MULTIPLE`**, so the app cannot be a share target for photos or text.
- **Fossify:** `SEND` and `SEND_MULTIPLE` (manifest L93, L100). **QKSMS:** `SEND`/`SEND_MULTIPLE` plus a chooser-target service (manifest L66-L94, L207). **Simple SMS:** Same as Fossify.
- **Gap / risk for us:** *Missing.* This is the usual way to send a photo from the gallery.
- **Suggested priority:** P2

#### 14. Received-content fidelity (added)
- **Ours:** The importer keeps only `text/plain`, `image/*`, `video/*` and `audio/*` parts (`SmsInboxImporter.kt` L190-L210). **vCards and other `application/*` parts are silently dropped.** The MMS subject (`sub`) is not read. SMS `TYPE` draft (3), outbox (4) and queued (6), and MMS box drafts and outbox, are all imported as **SENT** (L99-L103, L134-L138).
- **Fossify:** Keeps all parts as attachments, has a vCard viewer, stores the SMS pseudo-subject. **QKSMS:** Keeps all parts (`CursorToPart`) and has vCard support. **Simple SMS:** Same as Fossify.
- **Gap / risk for us:** *Bug (minor):* draft and queued rows show as sent bubbles. *Missing:* vCard and other attachment display.
- **Suggested priority:** P2

---

### MMS library behaviours that affect us (mmslib 1.0.0)
1. `MmsSentReceiver.updateInInternalDatabase` always sets `MESSAGE_BOX_SENT` (`LIB:com/klinker/android/send_message/MmsSentReceiver.java` L46), and it runs *after* the subclass hook (`StatusUpdatedReceiver.java` L21-L22). Fossify and QKSMS both use their own receivers that write FAILED.
2. The sent-MMS PendingIntent uses **request code 0 with `FLAG_CANCEL_CURRENT`** and the same explicit component every time (`Transaction.java` L646-L648); the download PendingIntent does the same (`DownloadManager.java` L83). Two MMS sends at once mean the first send's receipt is cancelled. Our sender waits 3 minutes for that receipt and then retries, which means a duplicate MMS. QKSMS's fork has the same pattern (`QK-lib:com/klinker/android/send_message/Transaction.kt` L104).
3. `PushReceiver.onReceive` runs its work on an executor **without `goAsync`** (`PushReceiver.java` L280). A static `downloadedUrls` set and `isDuplicateNotification` stop re-downloads (L172, L200).
4. `MmsConfig.getMaxMessageSize()` is 800 KB until `MmsConfig.init` loads `res/xml*/mms_config.xml`. The generic file says **307200**, and carrier folders range from 100 KB to 8 MB. Only `PushReceiver` calls `init` (L279). The value is passed to the platform as `MMS_CONFIG_MAX_MESSAGE_SIZE` (`Transaction.java` L677).
5. The library's `Utils.isDefaultSmsApp` uses `getDefaultSmsPackage` (`Utils.java` L428-L431). See section 11.
6. Download failures do not call `onError` unless there is an I/O exception; HTTP 400 and 404 delete the 130 row (`MmsReceivedReceiver.java` L167, L208-L221).

---

### Outright bugs in OUR code (separate from missing features)
- **B1. A failed MMS is shown as sent, once per retry.** `app:receivers/MmsSentReceiverImpl.kt` L28-L40 imports before the base class writes the box. The base class then writes SENT whatever happened (`LIB:.../MmsSentReceiver.java` L46). The importer maps box OUTBOX to SENT (`app:data/inbox/SmsInboxImporter.kt` L137) and never revisits rows, and the worker retries a TransientFailure up to 3 times, each time creating a new outbox row (`app:work/ScheduledSmsWorker.kt` L104-L107). Fix: in `onMessageStatusUpdated`, write `MESSAGE_BOX_FAILED` on non-OK (the base class still overwrites it, so override `updateInInternalDatabase` the way Fossify's own receiver does), and re-import or update by `mmsSystemId` afterwards.
- **B2. The exact alarm can double-send.** `app:work/WorkScheduler.kt` L64-L71 `runSmsNow` always uses `ExistingWorkPolicy.REPLACE`. If the delayed job is already RUNNING (alarm late in Doze, or a tie), the running worker is cancelled. `ScheduledSmsWorker.kt` L74-L77 then releases the claim on `CancellationException`, even though the SMS or MMS may already be with the radio, and the replacement worker claims and sends again. Fix: use KEEP when RUNNING (as `existingPolicy` already does), and never release a claim after the radio hand-off.
- **B3. Ghost empty MMS bubbles.** `SmsInboxImporter.kt` L106-L148 imports `m_type=130` notification rows (no `MESSAGE_TYPE` filter at L109). When the download completes, the library inserts a new row and deletes the 130 row (`DownloadRequest.persist`), but Room keeps the empty one forever. Failed downloads show the same way, with no explanation.
- **B4. A message can be stuck in "Sending…" forever.** If the process dies while the worker waits for a receipt, WorkManager re-runs the job. If that happens within `STALE_CLAIM_MILLIS` (10 min, `ScheduledMessageDao.kt` L126), `claimForSending` returns 0 and the worker returns `Result.success()` (`ScheduledSmsWorker.kt` L60-L62). Nothing else sweeps `SENDING` rows: `RearmWorker` re-enqueues only PENDING, and `QueueScreen.kt` L214 gives SENDING no action. Also, a confirmed SMS that was never recorded (death between send and `SentMessageRecorder.record`) leaves no trace in history.
- **B5. Photos lose their EXIF orientation.** `app:data/system/AttachmentStore.kt` L56-L76 decodes with `BitmapFactory` and re-encodes as JPEG without applying EXIF rotation. With the new 300 KB budget almost every camera photo is re-encoded, so most portrait photos will arrive rotated. (Fossify `ImageCompressor.kt` L124 and QKSMS via Glide handle this.)
- **B6. Possible loss on SMS_DELIVER (risk).** `app:receivers/ReceiverScope.kt` L17/L24 cancels the work after 8 s. `IncomingSmsHandler` (L33) → `AndroidSystemMessageStore.insert` (L58) writes to the provider only if *our own* default check passes, although receiving `SMS_DELIVER` already proves we are the default. Either path can leave a delivered SMS unwritten, and the platform has already deleted its copy.
- **B7. Duplicate MMS when two are in flight (library-triggered).** See library note 2. Our 3-minute wait plus retry turns a lost receipt into a re-send.
- **B8. Draft, queued and outbox rows import as SENT** (`SmsInboxImporter.kt` L99-L103, L134-L138). This is minor.

### Top gaps, ranked
1. **P0** Failed MMS recorded and shown as sent, multiplied by retries (B1).
2. **P0** Duplicate sends from exact-alarm REPLACE cancelling an in-flight worker, and from claim release after hand-off (B2, plus B7).
3. **P0** Messages stuck in SENDING after process death (B4).
4. **P1** No delete for messages or conversations (and none written to the provider), no persistent drafts, no message search (section 6).
5. **P1** One-way, append-only sync: provider deletes and status/read changes never reach Room; ghost 130-row MMS (sections 5 and 4, B3).
6. **P1** EXIF rotation lost on compressed photos (B5); MMS size should follow carrier config (QKSMS `-1` mode) or a setting, not a constant.
7. **P1** Notifications lack reply and mark-read actions and message history (section 12).
8. **P1** Send-path fidelity: no OUTBOX row before send, no failed bubble with resend in the thread, no delivery reports, no group-as-individual-SMS option (section 1).

(Next in line: P2 dual-SIM selection; P2 blocking and keyword filters; P2 backup of Room-only data with `allowBackup=false`; P2 share-target intents; P2 vCard and other attachment receive and send; P2 retry for stalled MMS downloads, which none of the three reference apps has either.)

---

## 4. UI, UX & notifications survey (2026-10-12)


Snapshot of what was read:
- **Ours**: `/home/user/kiwi-cup/scheduled-messenger` (working tree as of 2026-10-12)
- **Fossify Messages**: `/home/user/fossifyorg/messages` @ `7af607d` (2026-09-01)
- **QKSMS**: `/home/user/moezbhatti/qksms` @ `c86ea10` (2021-10-28)
- **Simple SMS Messenger**: `/home/user/simplemobiletools/sms` @ `84094a4` (2024-06-11, archived)

Path abbreviations used in citations:

| Prefix | Absolute path |
|---|---|
| `SM/` | `/home/user/kiwi-cup/scheduled-messenger/app/src/main/java/com/kiwicup/scheduledmessenger/` |
| `SMcore/` | `/home/user/kiwi-cup/scheduled-messenger/core/src/main/kotlin/com/kiwicup/scheduledmessenger/core/` |
| `F/` | `/home/user/fossifyorg/messages/app/src/main/kotlin/org/fossify/messages/` |
| `F-res/` | `/home/user/fossifyorg/messages/app/src/main/res/` |
| `Q/` | `/home/user/moezbhatti/qksms/presentation/src/main/java/com/moez/QKSMS/` |
| `Q-res/` | `/home/user/moezbhatti/qksms/presentation/src/main/res/` |
| `Q-data/` | `/home/user/moezbhatti/qksms/data/src/main/java/com/moez/QKSMS/` |
| `Q-domain/` | `/home/user/moezbhatti/qksms/domain/src/main/java/com/moez/QKSMS/` |
| `S/` | `/home/user/simplemobiletools/sms/app/src/main/kotlin/com/simplemobiletools/smsmessenger/` |
| `S-res/` | `/home/user/simplemobiletools/sms/app/src/main/res/` |

Caveat on Fossify and Simple SMS: both delegate a lot to their shared "Commons" library (`org.fossify.commons` / `com.simplemobiletools.commons`): letter avatars, the colour customisation screen, the blocked-numbers screen, date formatting and RadioGroupDialog. **Neither Commons library is checked out locally.** Where a feature lives there, this file cites the call site in the app and says "(Commons, not local)".

---

### 1. Conversation list

#### Avatars (contact photo / coloured initials)
- **Ours:** absent. The list row is a bare `ListItem` with headline, preview and trailing date. There is no `leadingContent` (`SM/ui/conversations/ConversationsScreen.kt:114-138`).
- **Fossify:** contact photo via Glide, with a letter placeholder generated from the title: `SimpleContactsHelper(activity).loadContactImage(path = conversation.photoUri, …, placeholderName = conversation.title, …)` (`F/adapters/BaseConversationsAdapter.kt:202-207`). Groups get a coloured group icon instead of a letter (`:195-200`, `getColoredGroupIcon`). The colour and initial algorithm is in Commons `SimpleContactsHelper` (not local). Layout: `F-res/layout/item_conversation.xml:18` (`conversation_image`).
- **QKSMS:** `AvatarView` (`Q/common/widget/AvatarView.kt:36-110`). The initials are the first and last word initials of the contact name (`:87-101`), with a person icon when there is no name, and the photo is drawn over them via Glide (`:103-108`). The background tint is the per-recipient theme colour (`:69,83`). That colour comes from `Colors.theme(recipient)` (`Q/common/util/Colors.kt:81-95`): with "auto colour" on, it is `randomColors[address.hashCode() % size]` (`:137-139`). Groups use `GroupAvatarView` (`Q/common/widget/GroupAvatarView.kt`). It is bound at `Q/feature/conversations/ConversationsAdapter.kt:108`.
- **Simple SMS:** same as Fossify (`S/adapters/BaseConversationsAdapter.kt:157,162`).
- **Gap / risk for us:** this is a missing feature. The list is hard to scan without faces or colours. Textra-style coloured initials need a deterministic colour per address, as QKSMS does in `Colors.kt:137-139`.
- **Suggested priority:** P1

#### Conversation title / contact name resolution
- **Ours:** the title is `ThreadSummary.title`, which is `recipients` (comma-joined) or `address`, the **raw phone number** (`SM/data/local/dao/SmsMessageDao.kt:27-28`). It is rendered at `SM/ui/conversations/ConversationsScreen.kt:115`. There is no `ContactNames` lookup anywhere on the list path (`SM/ui/conversations/ConversationsViewModel.kt:17-18`). A one-to-one thread title does resolve the contact name (`SM/ui/thread/ThreadViewModel.kt:74,226`). A group thread title joins raw numbers (`:74`).
- **Fossify:** `conversation.title` is stored as resolved contact names, and groups can be renamed (`F/adapters/BaseConversationsAdapter.kt:157-160`, `F/adapters/ConversationsAdapter.kt:235`).
- **QKSMS:** `conversation.getTitle()` (contact names, or the custom group name), with an ellipsizing `collapseEnabled` for groups (`Q/feature/conversations/ConversationsAdapter.kt:109-115`).
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** the list shows numbers while the thread header shows names, which is inconsistent (see Bugs). This is the single biggest "doesn't feel like a messaging app" item.
- **Suggested priority:** P0

#### Unread styling
- **Ours:** bold title and preview, preview colour switched to `onSurface`, bold date, and an M3 `Badge` with the count capped at "99+" (`SM/ui/conversations/ConversationsScreen.kt:113-133`). The count comes from a SQL subquery (`SM/data/local/dao/SmsMessageDao.kt:89`).
- **Fossify:** bold (bold-italic if scheduled) title, preview and date. Read previews are at alpha 0.7 (`F/adapters/BaseConversationsAdapter.kt:177-192`). A count badge is tinted with the primary colour, capped at 99+ (`:194, 211-224`; layout `F-res/layout/item_conversation.xml:103`).
- **QKSMS:** a separate view type for unread rows: bold title, snippet and date, the snippet expanded to 5 lines, and a dot tinted with the contact's colour (`Q/feature/conversations/ConversationsAdapter.kt:61-74, 123, 130-132`). There is no count.
- **Simple SMS:** bold styling like Fossify, but **no count badge** (no badge code in `S/adapters/BaseConversationsAdapter.kt`).
- **Gap / risk for us:** at parity or better, since the per-message unread count is our extra. QKSMS's 5-line unread preview is worth considering.
- **Suggested priority:** P2

#### Timestamp format (list)
- **Ours:** always `TimeFormat.date()` = `ofLocalizedDate(MEDIUM)`, e.g. "Oct 12, 2026", **even for messages received today** (`SM/ui/conversations/ConversationsScreen.kt:127`, `SM/ui/components/TimeFormat.kt:15,23-24`).
- **Fossify:** `formatDateOrTime(hideTimeOnOtherDays = true, showCurrentYear = false)` (Commons): the time today, the date otherwise, without the current year (`F/adapters/BaseConversationsAdapter.kt:167-175`). The date format is user-selectable (`F/activities/SettingsActivity.kt:233`).
- **QKSMS:** `DateFormatter.getConversationTimestamp`: "h:mm a" today, the weekday this week, "MMM d" this year, "MM/d/yy" otherwise. It respects the 24h setting (`Q/common/util/DateFormatter.kt:38-49, 72-83`).
- **Simple SMS:** same as Fossify (`S/adapters/BaseConversationsAdapter.kt:136`).
- **Gap / risk for us:** today's messages show only a date, so you cannot tell 9am from 9pm. Port QKSMS's 4-tier rule into `TimeFormat` (it is pure logic and fits `SMcore/`).
- **Suggested priority:** P1

#### Swipe actions
- **Ours:** absent.
- **Fossify:** not found (no `ItemTouchHelper` or swipe code in `F/`; the only "swipe" hit is pull-to-refresh in `F-res/layout/activity_thread.xml:118`).
- **QKSMS:** `ConversationItemTouchCallback` with configurable left and right actions: none, archive, delete, block, call, read or unread (`Q/feature/conversations/ConversationItemTouchCallback.kt:72-79, 138-156`; constants `Q-domain/util/Preferences.kt:67-73`; defaults archive/archive `:110-111`). They are dispatched in `Q/feature/main/MainViewModel.kt:424-434`, and archive shows an undo snackbar (`Q/feature/main/MainActivity.kt:395`). The settings screen is `Q/feature/settings/swipe/SwipeActionsController.kt:40`.
- **Simple SMS:** not found.
- **Gap / risk for us:** Textra-style quick archive or delete is missing. It depends on archive and delete existing first (see below).
- **Suggested priority:** P2

#### Long-press multi-select actions (list)
- **Ours:** absent. Rows are `clickable` only (`SM/ui/conversations/ConversationsScreen.kt:136`).
- **Fossify:** a contextual action bar (`R.menu.cab_conversations`) with add-to-contact, block, dial, copy number, delete, archive, rename (groups), details, mark read, mark unread, pin, unpin and select-all (`F/adapters/ConversationsAdapter.kt:40-88`). Delete is confirmed and cancels the notification (`:145-171`).
- **QKSMS:** long-press toggles selection (`Q/feature/conversations/ConversationsAdapter.kt:84-89`). The toolbar shows archive, unarchive, delete, add contact, pin, unpin, read, unread and block (`Q/feature/main/MainActivity.kt:233-241`; menu `Q-res/menu/main.xml`).
- **Simple SMS:** same as Fossify, minus "conversation details" in the CAB (`S/adapters/ConversationsAdapter.kt:34-64`).
- **Gap / risk for us:** there is no way to delete, block or bulk mark-read a conversation. `SmsMessageDao.deleteThread` exists (`SM/data/local/dao/SmsMessageDao.kt:147-148`) but nothing in the UI calls it.
- **Suggested priority:** P1

#### Pinning
- **Ours:** absent.
- **Fossify:** pinned thread ids are kept in prefs (`F/helpers/Config.kt:60-80`), sorted first (`F/activities/MainActivity.kt:406-411`), and shown with a pin icon (`F/adapters/BaseConversationsAdapter.kt:150-153`).
- **QKSMS:** a `pinned` column, sorted `pinned DESC, draft DESC, date DESC` (`Q-data/repository/ConversationRepositoryImpl.kt:70-71`), with an indicator (`Q/feature/conversations/ConversationsAdapter.kt:122`).
- **Simple SMS:** same as Fossify (`S/adapters/BaseConversationsAdapter.kt:120`).
- **Suggested priority:** P2

#### Archive / recycle bin
- **Ours:** absent.
- **Fossify:** archive (`F/activities/ArchivedConversationsActivity.kt`, CAB `F/adapters/ConversationsAdapter.kt:159`), an optional recycle bin (`F/activities/RecycleBinConversationsActivity.kt`, `F/helpers/Config.kt:118`), and a "keep conversations archived" option (`Config.kt:150`).
- **QKSMS:** archive via swipe, CAB or the drawer page (`Q/feature/main/MainActivity.kt:233-234, 281`).
- **Simple SMS:** same as Fossify.
- **Suggested priority:** P2

#### Search
- **Ours:** absent.
- **Fossify:** a search bar in the top menu (`F/activities/MainActivity.kt:163-175`). It does a SQL `LIKE` over messages and conversations (`:521-541`) and shows results in `SearchResultsAdapter` (`:597`). A result jumps to the message (`F/activities/ThreadActivity.kt:750`).
- **QKSMS:** a toolbar search field (`Q/feature/main/MainActivity.kt:87,230`; `Q/feature/main/MainViewModel.kt:190`), plus **in-thread search** with previous and next (`Q/feature/compose/ComposeActivity.kt:209-211`).
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** finding an old address or code means scrolling. Room FTS over `sms_messages.body` would be cheap to add.
- **Suggested priority:** P1

#### Empty state
- **Ours:** "No conversations yet" plus a hint (`SM/ui/conversations/ConversationsScreen.kt:92-105`). There is also a default-SMS-app banner card (`:73-91`).
- **Fossify:** a placeholder and a "start a conversation" link (`F/activities/MainActivity.kt:445-449`; `F-res/layout/activity_main.xml:45,60`), with a "loading messages" state on first run (`:437-438`).
- **QKSMS:** different text per page (inbox, search, archived) (`Q/feature/main/MainActivity.kt:251-281`), and a snackbar for default-app or permission problems (`:297-325`).
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** at parity. A first-import "loading" state is missing, so on first run the list may show "No conversations yet" while the import runs.
- **Suggested priority:** P2

#### FAB
- **Ours:** `FloatingActionButton` with "+" opens Compose (`SM/ui/conversations/ConversationsScreen.kt:64-68`).
- **Fossify:** `F/activities/MainActivity.kt:284`; `F-res/layout/activity_main.xml:140`.
- **QKSMS:** `compose` FAB tinted with the theme (`Q/feature/main/MainActivity.kt:88,183-186,250`).
- **Simple SMS:** same as Fossify.
- **Suggested priority:** none (parity)

#### Draft indicator / "You:" prefix in list
- **Ours:** absent. There are no drafts at all (see §3).
- **Fossify:** a "Draft" label, and the draft text replaces the snippet (`F/adapters/BaseConversationsAdapter.kt:146-148,163`).
- **QKSMS:** a coloured " Draft" suffix on the title, the draft as snippet, and "You: …" for outgoing (`Q/feature/conversations/ConversationsAdapter.kt:110-121`).
- **Simple SMS:** same as Fossify (`S/adapters/BaseConversationsAdapter.kt:117-118`).
- **Suggested priority:** P2 (comes with drafts)

---

### 2. Thread screen

#### Bubble styling & grouping of consecutive messages
- **Ours:** each message is its own `Surface` with fixed corners (16dp, with a 4dp tail corner on the sender side), max width 300dp, and 3dp vertical padding. There is **no grouping**: every bubble has its own tail and its own timestamp line (`SM/ui/components/MessageBubble.kt:58-107`). Colours come from the per-conversation style, then the app default, then the theme (`:46-56`; `SM/ui/thread/ThreadViewModel.kt:193-201`).
- **Fossify:** a fixed bubble drawable per side (`F-res/drawable/item_received_background.xml`; `item_sent_background` tinted with the primary colour, `F/adapters/ThreadAdapter.kt:470-473`). Max width is 80% of the screen (`:103`). A sender photo appears on **every** received message (`:419-449`). There is no first/middle/last grouping.
- **QKSMS:** true grouping. `BubbleUtils.canGroup` means same sender and less than 10 minutes apart (`Q/feature/compose/BubbleUtils.kt:27-33`). `getBubble` picks first, middle, last or only drawables (`:35-43`). The avatar shows only on the last bubble of a run (`Q/feature/compose/MessagesAdapter.kt:241-246`). The bottom padding collapses inside a run (`:237-239`). Incoming bubbles are tinted with the contact's colour (`:246`).
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** this is the most visible "Textra feel" gap. Grouping is pure presentation: pass `prev` and `next` into `MessageBubble` and switch the shape.
- **Suggested priority:** P1

#### Date separators / timestamp display / tap-to-show timestamp
- **Ours:** every bubble shows **time only** (`TimeFormat.time`, `SM/ui/components/MessageBubble.kt:98-107`). There are **no date headers**, so in a thread spanning days nothing shows which day a message is from (see Bugs). Tapping does nothing (`combinedClickable(onClick = {})`, `:89`).
- **Fossify:** a `ThreadDateTime` row is inserted when the gap is ≥ 300 s or the SIM changed (`F/activities/ThreadActivity.kt:1326-1333, 1357-1362, 2206`). The row shows date and time plus the SIM number (`F/adapters/ThreadAdapter.kt:586-606`; `F-res/layout/item_thread_date_time.xml`). There is no tap-to-reveal.
- **QKSMS:** the timestamp shows when the gap is ≥ 10 minutes or the SIM changed (`Q/feature/compose/MessagesAdapter.kt:224-235`). The format is relative: today, weekday, "MMM d", year (`Q/common/util/DateFormatter.kt:59-70`). **Tapping a bubble toggles a status line** (`MessagesAdapter.kt:172`; `bindStatus` `:292-318`) showing the time, or "Delivered <time>", or the sender name in groups.
- **Simple SMS:** same as Fossify (`S/activities/ThreadActivity.kt:80` `MIN_DATE_TIME_DIFF_SECS = 300`).
- **Gap / risk for us:** date-less history is a correctness problem, not just polish. The fix is a QKSMS-style gap rule plus a date header, with the per-bubble time hidden inside a group and revealed on tap.
- **Suggested priority:** P0

#### Message long-press actions (copy, delete, forward, share, select text, details, resend)
- **Ours:** a long-press `DropdownMenu` with only "Remind me about this later" and "Mark unread/read" (`SM/ui/components/MessageBubble.kt:108-127`). There is **no copy, select text, delete, forward, share or details**. The body is a plain `Text` with no `SelectionContainer` (`:92-96`). Picture-only messages have **no long-press target at all** (`:79`; see Bugs).
- **Fossify:** a multi-select CAB (`R.menu.cab_thread`) with copy (`F/adapters/ThreadAdapter.kt:224`), save attachment (`:249`), share (`:256`), select text (dialog, `:261`; `F/dialogs/SelectTextDialog.kt:9`), details (`:268`; `F/dialogs/MessageDetailsDialog.kt:17`, which shows sender/receiver, SIM and sent/received time), delete with confirmation (`:273`), forward (`:326`), restore from the recycle bin, and select all (menu wiring `:117-153`).
- **QKSMS:** long-press enters selection (`Q/feature/compose/MessagesAdapter.kt:177-182`). The toolbar has copy (multi), details (single), delete and forward (`Q/feature/compose/ComposeActivity.kt:205-208`; handlers `Q/feature/compose/ComposeViewModel.kt:314-355`). Details use `Q/common/util/MessageDetailsFormatter.kt`. There is no share or select-text.
- **Simple SMS:** same as Fossify (`S/adapters/ThreadAdapter.kt:78-96`).
- **Gap / risk for us:** copying a one-time code or an address is impossible. This is the highest-impact daily gap. Minimum set: Copy, Select text (wrap the body in `SelectionContainer`, or use a dialog like Fossify), Delete, Forward, Share and Details. Keep Remind and Mark unread as our extras.
- **Suggested priority:** P0

#### Failed-message affordance / resend
- **Ours:** a " · Not sent" suffix in error colour (`SM/ui/components/MessageBubble.kt:100,105`), with no retry action. Rows only become `FAILED` when imported from the system store (`SM/data/inbox/SmsInboxImporter.kt:101,136`). **A failed "Send now" from this app never appears in the thread**: the worker marks only the `scheduled_messages` row as failed (`SM/work/ScheduledSmsWorker.kt:96-108`), and nothing is posted to the thread or the notification shade (see Bugs).
- **Fossify:** a `ThreadError` row under the failed message (`F/activities/ThreadActivity.kt:1366-1368`; `F-res/layout/item_thread_error.xml`). Tapping it puts the text back in the composer and sets `messageToResend` (`F/activities/ThreadActivity.kt:689-697`). There is also a "message not sent" notification (`F/helpers/NotificationHelper.kt:191-230`).
- **QKSMS:** a status line "Failed" that is always visible (`Q/feature/compose/MessagesAdapter.kt:299,312`). **Tapping a failed message retries it** (`Q/feature/compose/ComposeViewModel.kt:414-419`, `retrySending`). There is a failure notification `notifyFailed` (`Q/common/util/NotificationManagerImpl.kt:320`).
- **Simple SMS:** same as Fossify (`S/activities/ThreadActivity.kt:520, 1125`).
- **Suggested priority:** P0

#### Delivery / sending indicators
- **Ours:** absent. There is no "Sending…" bubble: the outgoing row is inserted only after the radio reports success (`SM/data/inbox/SentMessageRecorder.kt:14-29`, called from `SM/work/ScheduledSmsWorker.kt:93`). Delivery reports are not requested (`deliveryIntents = null`, `SM/data/sms/AndroidSmsSender.kt:66`). The only feedback is a "Sending…" snackbar (`SM/ui/thread/ThreadViewModel.kt:266`).
- **Fossify:** a `ThreadSending` row while in the outbox (`F/activities/ThreadActivity.kt:1370-1372`). For the last sent message only, `ThreadSent` shows a single or double check for delivered (`:1380-1387`; `F/adapters/ThreadAdapter.kt:608-613`). Delivery reports are a toggle (`F/activities/SettingsActivity.kt:285`).
- **QKSMS:** a status line with "Sending…", "Delivered <time>" or "Failed" (`Q/feature/compose/MessagesAdapter.kt:292-318`). Delivered status shows on the last delivered message within 10 minutes (`:315`). There is a delivery toggle (`Q-res/layout/settings_controller.xml:147`). It also has a **send delay with a cancel ring** (`Q/feature/compose/MessagesAdapter.kt:199-220`).
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** sending goes through WorkManager, so latency and retries are invisible. The message seems to vanish from the composer and reappears only on success. Insert a `SENDING` row immediately and update it.
- **Suggested priority:** P1

#### Link / phone / email linkification
- **Ours:** absent (plain `Text`, `SM/ui/components/MessageBubble.kt:92-96`; no `LinkAnnotation` or `Linkify` anywhere).
- **Fossify:** `android:autoLink="email|web"`, so **phone numbers are not linked** (`F-res/layout/item_message.xml:61`). The link colour is the primary colour or the contrast colour (`F/adapters/ThreadAdapter.kt:431, 475`).
- **QKSMS:** `android:autoLink="email|phone|web"` (`Q-res/layout/message_list_item_in.xml:97`, `message_list_item_out.xml:90`).
- **Simple SMS:** `autoLink="email|web"` (`S-res/layout/item_message.xml:58`).
- **Gap / risk for us:** Compose has no autoLink. Use `AnnotatedString` with `LinkAnnotation.Url` built from `android.util.Patterns` (WEB_URL, EMAIL_ADDRESS, PHONE). Long-press must still reach the bubble menu.
- **Suggested priority:** P1

#### Emoji-only large rendering
- **Ours:** absent.
- **Fossify:** not found.
- **QKSMS:** `EMOJI_REGEX` match switches the text to `SIZE_EMOJI` (32sp) and a transparent `message_emoji` bubble (`Q/feature/compose/MessagesAdapter.kt:93, 272-283`; `Q/common/util/TextViewStyler.kt:56,92`; `Q/feature/compose/BubbleUtils.kt:37`).
- **Simple SMS:** not found.
- **Suggested priority:** P2

#### Attachment rendering (image grid, video, vCard, audio) & full-screen viewer
- **Ours:** each attachment is a 220dp square `AttachmentThumb`. Images are cropped via Coil. Anything else is a text placeholder ("Video", "Audio", …) (`SM/ui/components/MessageBubble.kt:73-78`; `SM/ui/components/AttachmentStrip.kt:48-75`). Thumbnails are **not clickable**, so there is no viewer. There is no video poster frame, vCard card or audio player.
- **Fossify:** images and videos at full bubble width with a fit-center Glide image and a play overlay for video (`F/adapters/ThreadAdapter.kt:389-400, 493-539`). A tap opens an **external** viewer via `launchViewIntent` (`:526-532`). vCard gets a preview card and the in-app `VCardViewerActivity` (`:541-562`). Other files (including audio) get a document row that opens externally (`:564-584`). A failed image load removes the view (`:513-517`).
- **QKSMS:** `PartsAdapter` with `MediaBinder` for image and video, `VCardBinder` and `FileBinder` (`Q/feature/compose/part/*.kt`). A media tap opens the **in-app gallery pager** `GalleryActivity` (`Q/feature/compose/ComposeViewModel.kt:422-427`; `Q/feature/gallery/GalleryActivity.kt:44`). Other parts are saved and opened externally (`ComposeViewModel.kt:429-440`).
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** received photos cannot be viewed full size or saved. At minimum, tap should open `ACTION_VIEW` with a `FileProvider` or content URI (Fossify approach). Better is an in-app zoomable pager (QKSMS approach).
- **Suggested priority:** P1

#### Scroll-to-unread / jump-to-bottom button
- **Ours:** the list is laid out in reverse and auto-scrolls on new messages only when already at the bottom or after a send (`SM/ui/thread/ThreadScreen.kt:97-106`; rule in `SMcore/ThreadScrolling.kt:24-25`). A "New" divider marks the first unread at open (`SM/ui/thread/ThreadScreen.kt:198, 246-264`). There is **no jump-to-bottom FAB** and no scroll to the divider when the unread run is taller than the screen.
- **Fossify:** `scroll_to_bottom_fab` appears when more than 20 items from the bottom (`F-res/layout/activity_thread.xml:137`; `F/activities/ThreadActivity.kt:672-686, 964-968, 2207`). It does not scroll to unread.
- **QKSMS:** not found (no FAB). The list stacks from the end.
- **Simple SMS:** same as Fossify (`S-res/layout/activity_thread.xml:125`; `S/activities/ThreadActivity.kt:509-511, 746`).
- **Gap / risk for us:** add a jump-to-bottom FAB, with an unread count badge in Slack style to match our "New" divider. Also scroll to the divider on open if it is off-screen. That would beat all three apps.
- **Suggested priority:** P1

#### Scheduled messages visible inside the thread
- **Ours:** absent. Pending scheduled texts appear only on the Queue screen (`SM/ui/queue/QueueScreen.kt`). `ThreadViewModel` observes reminders for the thread (`SM/ui/thread/ThreadViewModel.kt:210`) but not scheduled messages, and `ScheduledMessageDao` has no per-thread query (`SM/data/local/dao/ScheduledMessageDao.kt:36-55`).
- **Fossify:** scheduled messages render inline as italic sent bubbles with a clock icon (`F/adapters/ThreadAdapter.kt:477-487`). A tap shows info, edit or cancel (`F/activities/ThreadActivity.kt:689-691, 1926-1975`). A "scheduled" banner sits above the composer (`F-res/layout/layout_thread_send_message_holder.xml:10-63`).
- **QKSMS:** a scheduled banner with time and cancel in the composer (`Q-res/layout/compose_activity.xml:165-212`). The list itself is on a separate screen (`Q/feature/scheduled/ScheduledActivity.kt`).
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** scheduling is our headline feature, yet inside a conversation you cannot see what is queued for it. Show pending items as ghost bubbles or a banner, in the same way active reminders already get a banner (`SM/ui/thread/ThreadScreen.kt:172-182`).
- **Suggested priority:** P1

---

### 3. Compose / input bar

#### Attachment picker (gallery, camera, file, contact, location, GIF)
- **Ours:** a single "+" button opens the system `PickVisualMedia(ImageAndVideo)` (`SM/ui/components/MessageInputBar.kt:55-59`; `SM/ui/navigation/AppNavHost.kt:76-78, 98, 133-135, 165`). There is no camera, file, contact, location or keyboard-GIF support.
- **Fossify:** an inline picker panel with choose photo, choose video, take photo, record video, record audio, file, contact (vCard) and schedule (`F-res/layout/layout_attachment_picker.xml:19,51,83,115,147,179,212,245`; wiring `F/activities/ThreadActivity.kt:2072-2140`; capture intents `:1421-1456`). There is no location or GIF.
- **QKSMS:** an attach menu with contact, schedule, gallery and camera (`Q-res/layout/compose_activity.xml:384,418,452,486,520`; `Q/feature/compose/ComposeActivity.kt:286-330`). **Keyboard GIFs and stickers** come through `InputConnectionCompat.OnCommitContentListener` (`Q/common/widget/QkEditText.kt:91-92`). There is no location.
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** add a camera capture and a "paste or insert from keyboard" path. In Compose, `Modifier.contentReceiver` gives GIF and sticker insertion from Gboard (QKSMS parity). Location: none of the three apps has it.
- **Suggested priority:** P1 (camera and keyboard GIF), P2 (file, contact, audio)

#### Emoji button
- **Ours:** absent.
- **Fossify:** not found.
- **QKSMS:** not found. There is an "auto emoji" setting that converts `:)` into emoji (`Q-res/layout/settings_controller.xml:122`).
- **Simple SMS:** not found.
- **Gap / risk for us:** all four rely on the IME. Low value.
- **Suggested priority:** P2

#### Character / segment counter
- **Ours:** shown whenever there is text and no attachment, as "length/capacity · N parts" (`SM/ui/components/MessageInputBar.kt:85-90`). The calculation is in pure Kotlin (`SMcore/SmsTextAnalyzer.kt:36-53`). It is also shown in **group** threads, which actually send as MMS (see Bugs).
- **Fossify:** optional (`F/helpers/Config.kt:19`, `F/activities/SettingsActivity.kt:261`). It shows `SmsMessage.calculateLength` as "remaining/segments" (`F/activities/ThreadActivity.kt:885-895`). There is a "use simple characters" option that strips diacritics to stay GSM-7 (`:888-892`; `Config.kt:24`).
- **QKSMS:** shown only when ≤ 10 characters remain or the text is multi-part, as "remaining" or "remaining / parts" (`Q/feature/compose/ComposeViewModel.kt:590-606`). It has a "strip accents" option (`Q-res/layout/settings_controller.xml:163`).
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** an always-on counter is noisy. QKSMS's "only near a boundary" rule is the Textra-like behaviour. A "strip diacritics" option is P2.
- **Suggested priority:** P2

#### Send button behaviour
- **Ours:** separate "Schedule" (calendar) and "Send now" icon buttons, both disabled until sendable (`SM/ui/components/MessageInputBar.kt:48, 70-83`). Send goes through `ScheduledMessageRepository.schedule(now)` and WorkManager (`SM/ui/thread/ThreadViewModel.kt:266-278`). There is no send-on-enter option.
- **Fossify:** tap sends. **Long-press opens the schedule dialog** (`F/activities/ThreadActivity.kt:877-882`). Send-on-enter is optional (`:897-916`; `Config.kt:29`). Group sends ask for confirmation (`:1608`; `F/dialogs/GroupMessageSendDialog.kt`).
- **QKSMS:** tap sends, with an optional send delay of 3, 5 or 10 s and a cancel ring (`Q/feature/compose/MessagesAdapter.kt:199-220`; setting `Q-res/layout/settings_controller.xml:130`).
- **Simple SMS:** same as Fossify (`S/activities/ThreadActivity.kt:663-665`).
- **Gap / risk for us:** the explicit schedule button is fine as a differentiator. Consider long-press on Send as an additional shortcut, and QKSMS-style "undo send" (cheap for us, since sends already go through a queue).
- **Suggested priority:** P2

#### Schedule entry point
- **Ours:** a calendar icon in the input bar opens `DateTimePickerDialog`, with validation (`SM/ui/components/MessageInputBar.kt:70-76, 101-112`). There is a dedicated Queue screen for scheduled texts and reminders (`SM/ui/queue/QueueScreen.kt`).
- **Fossify:** long-press Send, or the picker's "Schedule" tile (`F-res/layout/layout_attachment_picker.xml:245`; `F/dialogs/ScheduleMessageDialog.kt`).
- **QKSMS:** the "Schedule" tile in the attach menu (`Q-res/layout/compose_activity.xml:418`; `Q/feature/compose/ComposeActivity.kt:268`).
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** ours is the most discoverable of the four. Keep it.
- **Suggested priority:** none

#### Draft saving
- **Ours:** absent. The draft is an in-memory `MutableStateFlow` in the ViewModel (`SM/ui/thread/ThreadViewModel.kt:94, 242`), so leaving the thread loses it. The same holds for pending attachments (`:96`).
- **Fossify:** saved on `onPause` and `onStop` (`F/activities/ThreadActivity.kt:317-327, 344-352`), restored on resume (`:301-305`), stored in the Room `DraftsDao` (`F/interfaces/DraftsDao.kt`), and shown in the list (`F/adapters/BaseConversationsAdapter.kt:146-148`).
- **QKSMS:** saved when the activity backgrounds (`Q/feature/compose/ComposeViewModel.kt:477-485`) into `conversationRepo.saveDraft`, and restored at `:565-577`.
- **Simple SMS:** same as Fossify (`S/activities/ThreadActivity.kt:204`).
- **Gap / risk for us:** this loses user-typed text every time someone backs out to check something. Persist the draft per thread in Room, debounced, and show it in the list.
- **Suggested priority:** P1

#### SIM picker
- **Ours:** absent (no `SubscriptionManager` use; sends via the default `SmsManager`, `SM/data/sms/AndroidSmsSender.kt`).
- **Fossify:** a SIM toggle button cycles active subscriptions, remembered per number (`F/activities/ThreadActivity.kt:1071-1150`; `F/helpers/Config.kt:13-17`; `F-res/layout/layout_thread_send_message_holder.xml:121,138`). The SIM is shown in the date header (`F/adapters/ThreadAdapter.kt:596-603`).
- **QKSMS:** a SIM button with a content description (`Q/feature/compose/ComposeActivity.kt:113, 236-238`; `Q-res/layout/compose_activity.xml:257-270`). The SIM index is shown on messages (`Q/feature/compose/MessagesAdapter.kt:229-235`).
- **Simple SMS:** same as Fossify (`S/activities/ThreadActivity.kt:856`).
- **Gap / risk for us:** only matters for dual-SIM users. A scheduled send would also need to store the subscription id.
- **Suggested priority:** P2

---

### 4. New conversation / recipient picker

#### Contact list, recents, groups, number formatting
- **Ours:** a "To" search field with chips above it (`SM/ui/components/RecipientField.kt:50-110`). The dropdown shows "Recent" (with names when known) and "Contacts", with a star marking favourites (`:95-106, 133-140`; logic `SMcore/ContactSuggestions.kt:59-78`). A typed number becomes a synthetic suggestion (`SM/ui/compose/ComposeViewModel.kt:155-177`). Contacts are bulk-loaded once (`:90-105`). There are **no avatars**, no full browsable contact list, no contact groups, no letter fast-scroller, and no as-you-type number formatting.
- **Fossify:** `NewConversationActivity` has a full contact list with avatars (`F/adapters/ContactsAdapter.kt`; `F/activities/NewConversationActivity.kt:171-207`), an A–Z fast-scroller (`:254`), and a horizontal "suggestions" strip of frequent contacts (`:213-252`). Adding more people uses a chip row in the thread (`F-res/layout/activity_thread.xml:37-103`; `F/activities/ThreadActivity.kt:1240-1290`).
- **QKSMS:** `ContactsActivity` and `ContactsViewModel` show Recent, Starred, **contact Groups**, then all contacts (`Q/feature/contacts/ContactsViewModel.kt:61-66, 100-158`; item types `Q/feature/compose/editing/ComposeItem.kt:27-49`). A typed number is formatted via `phoneNumberUtils.formatNumber` (`ContactsViewModel.kt:135-138`). Chips are in `Q/feature/compose/editing/ChipsAdapter.kt`.
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** functionally adequate and arguably better for search. Missing avatars, a browsable list and contact groups.
- **Suggested priority:** P2

---

### 5. Notifications

#### Channels (single vs per-conversation)
- **Ours:** one channel, `messages` (IMPORTANCE_HIGH), recreated on every post (`SM/notifications/IncomingMessageNotifier.kt:37-42, 72`). A separate `reminders` channel (`SM/notifications/ReminderNotifier.kt:33-40, 71`). There are no per-conversation channels.
- **Fossify:** a default channel, plus an **opt-in per-thread channel** (id = threadId) toggled in Conversation Details (`F/helpers/NotificationHelper.kt:49-55`; `F/activities/ConversationDetailsActivity.kt:87-139`). The details screen deep-links to the system channel settings (`:107-113`).
- **QKSMS:** a per-thread channel created on demand (`Q/common/util/NotificationManagerImpl.kt:381-415`, `getChannelIdForNotification` `:438`), plus in-app per-thread prefs (`Q/feature/notificationprefs/NotificationPrefsActivity.kt:47`; `Q-res/layout/notification_prefs_activity.xml:62-151`).
- **Simple SMS:** a single channel only (no per-conversation option in `S/activities/ConversationDetailsActivity.kt:63,82`).
- **Gap / risk for us:** you cannot give your partner's thread its own sound. On API 30+, a conversation shortcut (see below) lets the user set per-conversation importance without us creating channels.
- **Suggested priority:** P1

#### MessagingStyle with Person / avatar
- **Ours:** absent. Uses `BigTextStyle` of **only the latest message**, with the title set to the contact name (`SM/notifications/IncomingMessageNotifier.kt:52-61`). There is no large icon, no `Person`, and no group-conversation title. Because the notification id is per thread (`:75`), each new text **replaces** the previous one in the shade.
- **Fossify:** `MessagingStyle(user)` with a `Person(name, key = address)`. It **merges the previous messages** from the active notification (`extractMessagingStyleFromNotification`) (`F/helpers/NotificationHelper.kt:249-286`). The large icon is the contact photo or a letter icon (`:123-131`).
- **QKSMS:** the notification is rebuilt from the DB on every update (`getUnreadUnseenMessages`), so it lists every unread message (`Q/common/util/NotificationManagerImpl.kt:91-104, 153-182`). Each message has a `Person` with name, circle-cropped photo icon and contact lookup URI (`:155-172`). Groups set `isGroupConversation` and `conversationTitle` (`:147-151`). An inline image preview comes from `setData` (`:176-179`). It also uses `setNumber` and `addPerson("tel:")` so DND exceptions work (`:135, 221-224`).
- **Simple SMS:** same as Fossify (`S/helpers/NotificationHelper.kt:192-221`).
- **Gap / risk for us:** QKSMS's "rebuild from DB" model suits us best, because we already track `isRead` per message. It also keeps the notification correct when a message is marked unread or read elsewhere.
- **Suggested priority:** P0 (losing earlier texts from the shade)

#### Inline reply (RemoteInput) & how it is sent
- **Ours:** absent.
- **Fossify:** a `RemoteInput` reply action, hidden for alphanumeric short codes (`F/helpers/NotificationHelper.kt:94-121`), and only shown when lock-screen visibility is "sender and message" (`:156-158`). `DirectReplyReceiver` sends **synchronously via `sendMessageCompat`**, with the SIM chosen per number. It then re-posts the notification with the reply (`alertOnlyOnce`) and marks the thread read (`F/receivers/DirectReplyReceiver.kt:21-74`).
- **QKSMS:** a `RemoteInput` with canned choices before API 28 (`Q/common/util/NotificationManagerImpl.kt:357-375`). `RemoteMessagingReceiver` marks read, then `SendMessage` (`Q-data/receiver/RemoteMessagingReceiver.kt:44-58`). Alternatively there is the **QK Reply popup** activity (`Q/feature/qkreply/QkReplyActivity.kt:46`; `NotificationManagerImpl.kt:296-306`).
- **Simple SMS:** same as Fossify (`S/helpers/NotificationHelper.kt:75, 127`).
- **Gap / risk for us:** for us, the reply should go through `ScheduledMessageRepository.schedule(now)` (expedited WorkManager) so retry and the claim-once guarantee still apply. The notification must be updated to show the reply, or Android leaves a spinner.
- **Suggested priority:** P0

#### "Mark as read" action
- **Ours:** absent.
- **Fossify:** always added (`F/helpers/NotificationHelper.kt:160-164`). `MarkAsReadReceiver` cancels the notification and marks the thread read (`F/receivers/MarkAsReadReceiver.kt:14-27`). For no-reply short codes a "Delete" action is added (`NotificationHelper.kt:166-172`).
- **QKSMS:** up to 3 user-configurable actions: archive, delete, block, read, reply or call, each with a `SemanticAction` (`Q/common/util/NotificationManagerImpl.kt:226-293`). Dismissing marks the messages "seen" (`MarkSeenReceiver` delete intent, `:119-121, 138`).
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** mark-read must also update the system store (`SystemMessageStore.markThreadRead`) as `ThreadViewModel` does (`SM/ui/thread/ThreadViewModel.kt:116-120`).
- **Suggested priority:** P0

#### Lock-screen privacy
- **Ours:** absent. There is no `setVisibility` or `setPublicVersion`; the system default for the channel applies (`SM/notifications/IncomingMessageNotifier.kt:52-61`).
- **Fossify:** a setting for "sender and message", "sender only" or "nothing" (`F/activities/SettingsActivity.kt:317-332`; `F/helpers/Config.kt:51`). Applied in `F/helpers/NotificationHelper.kt:129-143`. Note that it changes the content everywhere, not just on the lock screen.
- **QKSMS:** a per-thread "previews" setting: all, name or none (`Q/common/util/NotificationManagerImpl.kt:196-218`; `Q-domain/util/Preferences.kt:50-52`).
- **Simple SMS:** same as Fossify (`S/activities/SettingsActivity.kt:292`).
- **Gap / risk for us:** the better implementation is `setVisibility(VISIBILITY_PRIVATE)` plus a `setPublicVersion` that says "New message". It is cheap and none of the references do it properly.
- **Suggested priority:** P2

#### Sound / vibration per conversation
- **Ours:** absent (single channel).
- **Fossify:** via the per-thread channel (see Channels). Sound and vibration are then edited in system settings (`F/activities/ConversationDetailsActivity.kt:107-135`).
- **QKSMS:** in-app per-thread ringtone, vibration and wake-screen settings (`Q/common/util/NotificationManagerImpl.kt:124-144, 309-316`; `Q-domain/util/Preferences.kt:162-198`). It also writes them to the per-thread channel on API 26+.
- **Simple SMS:** not found.
- **Suggested priority:** P1 (same fix as Channels)

#### Grouping / summary
- **Ours:** absent (no `setGroup`).
- **Fossify:** not found (no `setGroup` or `setGroupSummary` in `F/`).
- **QKSMS:** not found.
- **Simple SMS:** not found.
- **Gap / risk for us:** Android auto-groups 4 or more notifications from one app anyway. Low value.
- **Suggested priority:** P2

#### Cancel when read
- **Ours:** cancelled when the thread opens (`SM/ui/thread/ThreadViewModel.kt:119`). Posting is suppressed while that thread is resumed on screen (`SM/notifications/IncomingMessageNotifier.kt:36`; `SM/notifications/VisibleThread.kt`). There is no cancel or update when messages are marked read by other means, because no other means exist yet.
- **Fossify:** cancelled on thread open (`F/activities/ThreadActivity.kt:290`), on refresh (`:1869`), by Mark-as-read (`F/receivers/MarkAsReadReceiver.kt:19`), and on delete or archive (`F/adapters/ConversationsAdapter.kt:182, 213`).
- **QKSMS:** `update(threadId)` cancels when no unread or unseen messages remain (`Q/common/util/NotificationManagerImpl.kt:97-104`).
- **Simple SMS:** same as Fossify.
- **Suggested priority:** P2 (fine today; revisit with conversation-level mark-read)

#### Bubbles (Android Bubbles API)
- **Ours:** absent.
- **Fossify:** not found.
- **QKSMS:** not found. Its `QkReplyActivity` popup is the pre-Bubbles equivalent.
- **Simple SMS:** not found.
- **Gap / risk for us:** this needs a conversation shortcut first. It would be a differentiator, but it is P2.
- **Suggested priority:** P2

#### Conversation shortcuts (ShortcutManager / "Conversations" section)
- **Ours:** absent. Without a long-lived conversation shortcut, notifications on Android 11+ do not appear in the shade's **Conversations** section, cannot be marked "Priority", and cannot bubble.
- **Fossify:** `ShortcutHelper.buildShortcut` uses `setIsConversation()`, `setLongLived(true)`, `setPersons`, an icon from the person photo or a letter/group icon, and capability bindings (`F/helpers/ShortcutHelper.kt:34-91`). It is pushed via `pushDynamicShortcut` and usage is reported for send and receive (`:135-154`). It is attached with `builder.setShortcutInfo(shortcut)` (`F/helpers/NotificationHelper.kt:174-188`). There is also a launcher "new conversation" shortcut (`F/activities/MainActivity.kt:484-499`).
- **QKSMS:** plain dynamic launcher shortcuts for the top conversations, **not** conversation shortcuts (no long-lived or persons) (`Q/common/util/ShortcutManagerImpl.kt:49-90`). There is a Direct Share chooser target (`Q/common/util/QkChooserTargetService.kt`).
- **Simple SMS:** only the launcher "new conversation" shortcut (`S/activities/MainActivity.kt:467-482`). Not found for conversations.
- **Gap / risk for us:** Fossify's `ShortcutHelper` is the template to copy. It unlocks Priority conversations, per-conversation importance without channels, and later Bubbles.
- **Suggested priority:** P1

#### Launcher badge
- **Ours:** absent (relies on the system dot).
- **Fossify:** not found.
- **QKSMS:** `ShortcutManagerImpl.updateBadge` (`Q/common/util/ShortcutManagerImpl.kt:44`).
- **Simple SMS:** `ShortcutBadger` count (`S/extensions/Context.kt:828-830`).
- **Suggested priority:** P2

---

### 6. Settings

#### Theming / colour customisation (app-wide and per-conversation)
- **Ours:** light, dark or system mode, Material You dynamic colour (API 31+), and a seed accent from presets or hex (`SM/ui/settings/SettingsScreen.kt:75-103`). The scheme is derived from the seed (`SM/ui/theme/Theme.kt:19-31`; `SMcore/ThemeColors.kt`). App-wide default incoming and outgoing bubble colours are set at `SettingsScreen.kt:117-134`. **Per-conversation** bubble colours, wallpaper and dim are in `SM/ui/components/ConversationStyleDialog.kt:24-78`, stored in `conversation_styles` keyed by address (`SM/data/local/entity/ConversationStyle.kt`), with a readable text colour auto-picked (`SM/ui/components/MessageBubble.kt:52-56`).
- **Fossify:** "Customize colours" opens the Commons `CustomizationActivity` (`F/activities/SettingsActivity.kt:166-170`; Commons, not local). It covers text, background, primary and accent colours, the app icon colour and a Material You "dynamic" option (`F/activities/ThreadActivity.kt:2171`, `isDynamicTheme()`). Sent bubbles take the primary colour (`F/adapters/ThreadAdapter.kt:470-473`). Received bubbles take a fixed `activated_item_foreground` (`F-res/drawable/item_received_background.xml:11`). **There are no per-conversation colours or wallpaper.**
- **QKSMS:** a theme picker with Material swatches plus an HSV picker (`Q/feature/themepicker/ThemePickerPresenter.kt:44`, `HSVPickerView.kt`). Colour can be set **per recipient** (`prefs.theme(recipientId)`, reached from Conversation Info via `showThemePicker(recipientId)` (`Q/feature/conversationinfo/ConversationInfoController.kt:113`)). "Auto colour" hashes the address into a palette (`Q/common/util/Colors.kt:93, 137-139`). There is a night mode schedule and pure black (`Q-res/layout/settings_controller.xml:41-73`). There is no wallpaper.
- **Simple SMS:** same as Fossify (`S/activities/SettingsActivity.kt:156`).
- **Gap / risk for us:** ours is the strongest of the four (per-conversation colours plus wallpaper). The missing pieces are auto-colour per contact (needed for avatars anyway) and AMOLED black.
- **Suggested priority:** P2

#### Font size
- **Ours:** absent in-app. Compose `sp` typography follows the system font scale (`SM/ui/theme/Theme.kt:51` uses default `MaterialTheme` typography).
- **Fossify:** small, medium, large or extra large (`F/activities/SettingsActivity.kt:241-259`). It is applied through `getTextSize()` in the list (`F/adapters/BaseConversationsAdapter.kt:45, 159, 164, 174`) and the thread (`F/adapters/ThreadAdapter.kt:371`; `F/activities/ThreadActivity.kt:869-872`).
- **QKSMS:** a `textSize` pref (`Q-domain/util/Preferences.kt:101`; `Q-res/layout/settings_controller.xml:81`) applied by `TextViewStyler` (`Q/common/util/TextViewStyler.kt:86-92`). There is also a "system font" toggle (`settings_controller.xml:96`).
- **Simple SMS:** same as Fossify (`S/activities/SettingsActivity.kt:227`).
- **Gap / risk for us:** Textra users expect an in-app text size. In Compose this is a `Typography` scale multiplier in `ScheduledMessengerTheme`.
- **Suggested priority:** P2

#### Swipe configuration
- **Ours:** absent (no swipe).
- **Fossify:** not found.
- **QKSMS:** `Q/feature/settings/swipe/SwipeActionsController.kt:40`; `Q-res/layout/settings_controller.xml:138`.
- **Simple SMS:** not found.
- **Suggested priority:** P2

#### Notification settings
- **Ours:** absent (no entry point). The system app notification settings are the only control.
- **Fossify:** a "Customize notifications" entry opens the system app notification settings (`F/activities/SettingsActivity.kt:172-176`), plus lock-screen visibility (`:317-332`).
- **QKSMS:** an in-app notification prefs screen, global and per thread: on/off, previews, wake, vibration, ringtone, 3 actions, QK Reply (`Q-res/layout/notification_prefs_activity.xml:62-151`).
- **Simple SMS:** same as Fossify (`S/activities/SettingsActivity.kt:163`).
- **Suggested priority:** P2

#### Blocked numbers screen
- **Ours:** absent. No block action anywhere. The platform still drops SMS from numbers blocked system-wide before delivery, but users cannot block from our UI.
- **Fossify:** `ManageBlockedNumbersActivity` (Commons, not local) launched from `F/activities/SettingsActivity.kt:203-216`, plus **blocked keywords** (`F/activities/ManageBlockedKeywordsActivity.kt`; `F/helpers/Config.kt:82-92`). Block from the CAB is at `F/adapters/ConversationsAdapter.kt:91-130`.
- **QKSMS:** `Q/feature/blocking/numbers/BlockedNumbersController.kt:39`, a blocked-messages list (`Q/feature/blocking/messages/`), and a choice of blocking engine (`Q/feature/blocking/manager/`).
- **Simple SMS:** same as Fossify, but gated behind the paid "Thank You" app (`S/adapters/ConversationsAdapter.kt:34`, `addLockedLabelIfNeeded`).
- **Gap / risk for us:** spam handling is expected. `BlockedNumberContract` writes need the default-SMS role, which we hold.
- **Suggested priority:** P1

#### MMS size limit
- **Ours:** absent (no setting; behaviour depends on `AttachmentStore` and `AndroidMmsSender`).
- **Fossify:** none, 2MB, 1MB, 600KB, 300KB, 200KB or 100KB (`F/activities/SettingsActivity.kt:341-361`), with `F/helpers/ImageCompressor.kt`.
- **QKSMS:** `Q-res/layout/settings_controller.xml:198` (`mmsSize`).
- **Simple SMS:** same as Fossify (`S/activities/SettingsActivity.kt:316`).
- **Suggested priority:** P2 (P1 if carriers reject our MMS; out of scope for this UI survey)

#### Delivery reports toggle
- **Ours:** absent. Delivery intents are null (`SM/data/sms/AndroidSmsSender.kt:66`).
- **Fossify:** `F/activities/SettingsActivity.kt:285`; `F/helpers/Config.kt:33`.
- **QKSMS:** `Q-res/layout/settings_controller.xml:147`.
- **Simple SMS:** same as Fossify (`S/activities/SettingsActivity.kt:268`).
- **Suggested priority:** P2

#### "Send long messages as MMS"
- **Ours:** absent. Long text always goes as multipart SMS (`SM/data/sms/AndroidSmsSender.kt:66`).
- **Fossify:** `F/activities/SettingsActivity.kt:293` (and group as MMS `:301`).
- **QKSMS:** `Q-res/layout/settings_controller.xml:189` (`longAsMms`).
- **Simple SMS:** same as Fossify (`S/activities/SettingsActivity.kt:276, 284`).
- **Suggested priority:** P2

#### Signature
- **Ours:** absent.
- **Fossify:** not found.
- **QKSMS:** a signature dialog (`Q/feature/settings/SettingsController.kt:79-80, 172-173`), appended at send time (`Q-data/repository/MessageRepositoryImpl.kt:305`).
- **Simple SMS:** not found.
- **Suggested priority:** P2

#### Settings screen naming
- **Ours:** the screen is titled "Appearance", and the list's gear icon is labelled "Appearance" (`SM/ui/settings/SettingsScreen.kt:62`; `SM/ui/conversations/ConversationsScreen.kt:59`). It also holds Scheduling and Diagnostics (`SettingsScreen.kt:104-136`).
- **Gap / risk for us:** rename it to "Settings" before adding the sections above.
- **Suggested priority:** P2

---

### 7. Accessibility

#### Content descriptions / semantics
- **Ours:** icon buttons are labelled ("Scheduled & reminders", "New message", "Attach picture", "Schedule send", "Send now", "Remove …", "Back", "More") (`SM/ui/conversations/ConversationsScreen.kt:56-66`; `SM/ui/components/MessageInputBar.kt:57,75,82`; `SM/ui/components/RecipientField.kt:159`). Gaps:
  - Bubbles have no merged semantics, so TalkBack reads body, then time, then flags separately, with no "You said" or "<Name> said" (`SM/ui/components/MessageBubble.kt:64-107`).
  - Long-press actions are not exposed as custom accessibility actions (`:89`).
  - Colour swatches have no description or selected state (`SM/ui/components/ColorSwatches.kt:80-93`).
  - The unread badge reads a bare number (`SM/ui/conversations/ConversationsScreen.kt:129-131`).
  - Image attachments are all "Picture" (`SM/ui/components/AttachmentStrip.kt:62`).
- **Fossify:** sparse. Only 5 `contentDescription` attributes across `F-res/layout/*.xml`, for example `layout_thread_send_message_holder.xml:63,97,176`.
- **QKSMS:** 12 in layouts, including compose actions and the SIM (`Q-res/layout/compose_activity.xml:261,303,390,424,458,492,525`), and a dynamic SIM description (`Q/feature/compose/ComposeActivity.kt:237`).
- **Simple SMS:** same as Fossify.
- **Gap / risk for us:** we already beat Fossify on buttons. Add `semantics(mergeDescendants = true)` with a composed description and `customActions` on the bubble and list row. This is cheap and makes us best-in-class.
- **Suggested priority:** P2

#### Large fonts
- **Ours:** Compose `sp` scales with the system font size. Fixed `dp` sizes may clip at large scale: the swatch is 40dp (`ColorSwatches.kt:85`), the thumbnail 220dp (`MessageBubble.kt:76`), and the bubble max width 300dp (`MessageBubble.kt:88`). Not verified on a device.
- **Fossify:** an in-app font size (§6). The system scale also applies via `sp`.
- **QKSMS:** an in-app text size and a system font toggle (§6).
- **Simple SMS:** same as Fossify.
- **Suggested priority:** P2

---

### 8. Per-conversation settings / details screen

#### Participants, custom notification, block, delete, rename
- **Ours:** absent as a screen. The thread overflow has only "Style this conversation" (`SM/ui/thread/ThreadScreen.kt:118-129`), which opens `ConversationStyleDialog` (colours, wallpaper, dim). The group participant list exists in state (`SM/ui/thread/ThreadViewModel.kt:63, 225`) but is shown only as a comma-joined title of numbers (`:74`). There is no rename, block, delete, call or add-to-contacts, and no per-thread notification setting.
- **Fossify:** `ConversationDetailsActivity` has the name with a rename dialog (`F/activities/ConversationDetailsActivity.kt:141-165`; `F/dialogs/RenameConversationDialog.kt`), participants that open the contact card (`:167-179`), and custom notifications with a link to the channel settings (`:87-139`). The thread overflow has block, delete, archive, rename, details, add to contact, copy number, dial, manage people and mark as unread (`F/activities/ThreadActivity.kt:355-405, 1152-1320`).
- **QKSMS:** `ConversationInfoController` lists recipients (tap or long-press, `:99-100`), sets the theme per recipient (`:101, 113`), names the group (`:102-103`; `Q-res/layout/conversation_info_settings.xml:33`), opens per-thread notification prefs (`:41`), archive (`:48`), block (`:55`) and delete (`:62`), and shows a **shared media grid** (`Q/feature/conversationinfo/ConversationInfoController.kt:109`).
- **Simple SMS:** a details screen with name/rename and participants only, without custom notifications (`S/activities/ConversationDetailsActivity.kt:63, 82`). The thread menu matches Fossify's.
- **Gap / risk for us:** a details screen is the natural home for our extras (style, wallpaper, reminders, and the scheduled queue for this thread) alongside the standard items: participants, notifications, block, delete and call. QKSMS's layout is the closest model.
- **Suggested priority:** P1

---

### Other features found in references (not in the brief)

| Feature | Ours | Reference | Priority |
|---|---|---|---|
| Undo-send / send delay | absent | QKSMS `Q/feature/compose/MessagesAdapter.kt:199-220`, setting `Q-res/layout/settings_controller.xml:130` | P2 (cheap for us: sends already queue) |
| Quick-reply popup over other apps | absent | QKSMS `Q/feature/qkreply/QkReplyActivity.kt:46` | P2 |
| In-thread search | absent | QKSMS `Q/feature/compose/ComposeActivity.kt:209-211` | P2 |
| Call button in thread toolbar | absent | QKSMS `ComposeActivity.kt:201`; Fossify `F/activities/ThreadActivity.kt:402` (dial) | P2 |
| Recycle bin | absent | Fossify `F/activities/RecycleBinConversationsActivity.kt`, `Config.kt:118` | P2 |
| Home-screen widget | absent | QKSMS `Q/feature/widget/WidgetProvider.kt` | P2 |
| Shared-media grid | absent | QKSMS `ConversationInfoController.kt:109` | P2 |
| Conversation-level "mark unread" | absent (per-message only, which is our extra) | Fossify `F/activities/ThreadActivity.kt:1293`; CAB `F/adapters/ConversationsAdapter.kt:266` | P2 |
| Group-send confirmation (MMS vs individual) | absent | Fossify `F/activities/ThreadActivity.kt:1608`; QKSMS "send as group" toggle `Q-res/layout/compose_activity.xml:74-128` | P2 |

---

### Bugs in OUR code (separate from missing features)

1. **A failed "Send now" disappears from the conversation.** `ThreadViewModel.schedule` clears the draft and shows "Sending…" as soon as the queue row is accepted (`SM/ui/thread/ThreadViewModel.kt:266, 274-276`). If the worker then hits `PermanentFailure` or exhausts its 3 attempts, it only calls `scheduledMessageDao.markFailed` (`SM/work/ScheduledSmsWorker.kt:96-108`). No `FAILED` `SmsMessage` row is written, and no notification is posted (no notifier in `SM/work/ScheduledSmsWorker.kt`). The "Not sent" label (`SM/ui/components/MessageBubble.kt:100`) can therefore only ever appear for rows imported from the system store (`SM/data/inbox/SmsInboxImporter.kt:101, 136`). The user's text is silently gone from the thread. It is visible only on the Queue screen.
2. **No dates anywhere in thread history.** Each bubble shows `TimeFormat.time` only (`SM/ui/components/MessageBubble.kt:99`), and there are no date separators (`SM/ui/thread/ThreadScreen.kt:194-207`). Messages from different days are indistinguishable.
3. **Per-conversation style is keyed by the latest message's address, not by the conversation.** `address` = `messages.lastOrNull()?.address` (`SM/ui/thread/ThreadViewModel.kt:185, 218`). The style is looked up by it (`:188-190`) and written by it (`:289-299`). In a group, incoming rows carry the **sender's** address (`SM/data/inbox/SmsInboxImporter.kt:125`). So the group's look changes depending on who spoke last, styling a group overwrites that member's 1:1 style, and vice versa. The dialog title also shows that one number (`SM/ui/components/ConversationStyleDialog.kt:36`).
4. **Dragging the wallpaper-dim slider re-decodes the full wallpaper on every tick.** `Slider.onValueChange` calls `onDim` per frame (`SM/ui/components/ConversationStyleDialog.kt:64-71`). That becomes a DB write (`SM/ui/thread/ThreadViewModel.kt:292`), which changes the style row, which re-runs `.map { style -> style to decodeWallpaper(...) }` (`:188-192`), decoding up to 1440×2560 from disk each time (`:231-239`). The likely result is jank, GC churn and a DB write per frame. The fix is to decode keyed on `wallpaperPath` only, and to persist on `onValueChangeFinished`.
5. **Picture-only messages have no long-press menu.** The `combinedClickable` sits only on the text `Surface`, which is skipped when `body.isBlank() && media.isNotEmpty()` (`SM/ui/components/MessageBubble.kt:79-90`). Thumbnails have no click handler (`:74-78`). As a result, "Remind me" and "Mark unread", our differentiators, cannot be used on photos.
6. **Contact names are inconsistent across screens.** The 1:1 thread title resolves the name (`SM/ui/thread/ThreadViewModel.kt:74, 226`). The conversation list (`SM/data/local/dao/SmsMessageDao.kt:27-28` → `SM/ui/conversations/ConversationsScreen.kt:115`), group titles (`ThreadViewModel.kt:74`, `participants.joinToString`) and group sender labels (`SM/ui/components/MessageBubble.kt:65-71`, `message.address`) all show raw numbers.
7. **Contacts provider query on the main thread.** `contacts.displayName(...)` (a `ContentResolver.query`, `SM/data/system/ContactNames.kt:20-31`) runs inside the `combine` transform of `ThreadViewModel.state` (`SM/ui/thread/ThreadViewModel.kt:226`). That is collected in `viewModelScope`, which is Main. The result is cached after the first hit per address, but the first open of each thread queries on the UI thread. Medium confidence that this causes visible jank; it is a StrictMode violation regardless.
8. **Each incoming text replaces the previous one in the notification shade.** The notification id is constant per thread (`SM/notifications/IncomingMessageNotifier.kt:63, 75`), and the content is `BigTextStyle` of only the newest message (`:52-56`). With 3 unread texts, the shade shows only the last. (Also listed under §5 as a missing MessagingStyle.)
9. **Stale instruction text.** Settings says "Long-press a conversation title to style just that one." (`SM/ui/settings/SettingsScreen.kt:118`). No such gesture exists; the entry is the thread overflow, "Style this conversation" (`SM/ui/thread/ThreadScreen.kt:122-127`).
10. **The segment counter is shown for group threads that actually send as MMS.** The counter hides only when attachments exist (`SM/ui/components/MessageInputBar.kt:85-97`), but the worker sends MMS when `recipients.size > 1` (`SM/work/ScheduledSmsWorker.kt:67`). It is misleading, and the "Sends as MMS" hint is missing for groups.
11. **(Minor) Accessibility mislabel.** The gear icon's description is "Appearance" (`SM/ui/conversations/ConversationsScreen.kt:59`), but the screen also holds Scheduling and Diagnostics (`SM/ui/settings/SettingsScreen.kt:104-136`).
12. **(Minor) The conversation-list date omits the time for today** (`SM/ui/conversations/ConversationsScreen.kt:127` → `TimeFormat.date`). This is UX rather than a crash; it is listed here because it reads as wrong ("Oct 12, 2026" for a text from 2 minutes ago).
