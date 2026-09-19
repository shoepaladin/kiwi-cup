package com.kiwicup.scheduledmessenger.core

/** One labelled group of readings. */
data class DiagnosticSection(val title: String, val entries: List<Pair<String, String>>)

/**
 * A plain-text snapshot of everything that decides whether the SMS group can be granted.
 *
 * It exists because the restriction is invisible from inside the app: every route we have reports
 * "denied", and nothing in that answer distinguishes a sideload refusal from the user declining a
 * prompt. The readings gathered here do distinguish them — the installer package, the app-op mode
 * behind each permission, and whether the SMS role is even on offer — so a user who cannot reach
 * adb can still send back something conclusive.
 */
data class DiagnosticReport(val sections: List<DiagnosticSection>) {

    /** Fixed-width keys so the columns line up when pasted into a chat or an issue. */
    fun render(): String = sections.joinToString("\n\n") { section ->
        val width = section.entries.maxOfOrNull { it.first.length } ?: 0
        val body = section.entries.joinToString("\n") { (key, value) ->
            "${key.padEnd(width)}  $value"
        }
        "== ${section.title} ==" + if (body.isEmpty()) "" else "\n$body"
    } + "\n"
}
