package com.kiwicup.scheduledmessenger.data.system

import android.content.Context
import com.kiwicup.scheduledmessenger.core.PhoneNumberRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device [PhoneNumberRecognizer], backed by the Android port of libphonenumber.
 *
 * Constructed once and held: `createInstance` reads and parses the bundled metadata, which is
 * far too expensive to repeat on every keystroke of a recipient search. QKSMS wraps the same
 * library in the same single injected object for the same reason.
 */
@Singleton
class AndroidPhoneNumberRecognizer @Inject constructor(
    @ApplicationContext context: Context
) : PhoneNumberRecognizer {

    private val util: PhoneNumberUtil = PhoneNumberUtil.createInstance(context)

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
