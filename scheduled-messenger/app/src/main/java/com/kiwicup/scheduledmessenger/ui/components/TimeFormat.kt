package com.kiwicup.scheduledmessenger.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object TimeFormat {
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val timeOnly = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dateOnly = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTime.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeOnly.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun date(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateOnly.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Material's DatePicker reports the chosen day as UTC midnight; combine it with a local wall-clock time. */
    fun combine(utcDayMillis: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val day = LocalDate.ofEpochDay(Math.floorDiv(utcDayMillis, 86_400_000L))
        return LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    }

    /** Inverse of [combine] for pre-selecting an existing timestamp in the pickers. */
    fun toUtcDayMillis(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun localTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
}
