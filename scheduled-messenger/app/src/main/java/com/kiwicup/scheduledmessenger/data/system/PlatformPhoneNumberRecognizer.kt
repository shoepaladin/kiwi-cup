package com.kiwicup.scheduledmessenger.data.system

import android.telephony.PhoneNumberUtils
import com.kiwicup.scheduledmessenger.core.PhoneNumberRecognizer
import com.kiwicup.scheduledmessenger.diagnostics.AppLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device [PhoneNumberRecognizer], backed by the platform's own `android.telephony
 * .PhoneNumberUtils` rather than a third-party library.
 *
 * This app went through `io.michaelrocks:libphonenumber-android` first, the same port QKSMS
 * ships. It cost three separate failures in one evening: a theorized metadata-loading crash on
 * the plain (non-Android) build, a `checkDebugAarMetadata` failure because the port's AAR needs
 * compileSdk 36 and this app deliberately stays on 35, and then a report of the compose screen
 * crashing outright with no stack trace yet to confirm why. Checking QKSMS's own use of the same
 * library ruled out one theory — they call `createInstance` just as eagerly and unguarded, at app
 * launch rather than per-screen, with no history of it failing — but a library that has cost this
 * many separate build and runtime failures against one specific app is a poor bet to keep
 * defending, especially once a reference app shows the feature does not need it.
 *
 * Fossify Messages validates and formats numbers with nothing but this platform class — see its
 * `SmsSender.kt`. It ships no phone-number library at all. `PhoneNumberUtils.isWellFormedSmsAddress`
 * is a purpose-built check for exactly this question ("is this typed string usable as an SMS
 * destination?"), and `formatNumber` renders it for display. Neither takes a `Context` or loads
 * bundled metadata, so there is nothing here that can fail at construction — the previous crash
 * class is structurally impossible, not just guarded against.
 *
 * The trade is real and worth naming: this is looser than libphonenumber's E.164 validation, so
 * it will occasionally treat something as number-shaped that a stricter parser would reject. That
 * is an acceptable cost for a suggestion row — [core.RecipientValidator] still normalizes and
 * validates before anything is actually sent, unaffected by this class.
 */
@Singleton
class PlatformPhoneNumberRecognizer @Inject constructor() : PhoneNumberRecognizer {

    override fun isPossible(query: String, defaultRegion: String): Boolean = try {
        PhoneNumberUtils.isWellFormedSmsAddress(query.trim())
    } catch (e: Exception) {
        AppLog.w("PhoneNumberRecognizer", "isPossible threw for a query of length ${query.length}", e)
        false
    }

    override fun format(query: String, defaultRegion: String): String = try {
        PhoneNumberUtils.formatNumber(query, defaultRegion) ?: query
    } catch (e: Exception) {
        query
    }
}
