package com.kiwicup.scheduledmessenger.core

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * A [PhoneNumberRecognizer] backed by Google's plain Java libphonenumber, for tests only.
 *
 * The app ships the Android port instead; this exists so the assertions here are made against the
 * library's real behaviour rather than a hand-written stub that would happily agree with whatever
 * the code under test believes.
 */
class LibPhoneNumberRecognizer : PhoneNumberRecognizer {

    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    override fun isPossible(query: String, defaultRegion: String): Boolean = try {
        util.isPossibleNumber(util.parse(query, defaultRegion))
    } catch (e: NumberParseException) {
        false
    }

    override fun format(query: String, defaultRegion: String): String = try {
        util.format(util.parse(query, defaultRegion), PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
    } catch (e: NumberParseException) {
        query
    }
}
