package com.kiwicup.scheduledmessenger.data.system

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * The two-letter region libphonenumber should assume when a typed number has no country code —
 * so "5551234567" parses as a US number on a US phone rather than being rejected outright.
 *
 * Preference order is network, then SIM, then the device locale: the network ISO tracks where the
 * phone actually is, which matters more while travelling than where the SIM was issued.
 */
object DefaultRegion {

    fun forDevice(context: Context): String {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val fromNetwork = runCatching { telephony?.networkCountryIso }.getOrNull()
        val fromSim = runCatching { telephony?.simCountryIso }.getOrNull()
        val fromLocale = runCatching { Locale.getDefault().country }.getOrNull()
        return listOf(fromNetwork, fromSim, fromLocale)
            .firstOrNull { !it.isNullOrBlank() }
            ?.uppercase(Locale.ROOT)
            ?: FALLBACK
    }

    private const val FALLBACK = "US"
}
