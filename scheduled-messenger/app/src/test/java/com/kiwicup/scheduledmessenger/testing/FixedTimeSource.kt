package com.kiwicup.scheduledmessenger.testing

import com.kiwicup.scheduledmessenger.core.TimeSource

class FixedTimeSource(var current: Long) : TimeSource {
    override fun now(): Long = current
    fun advanceBy(millis: Long) { current += millis }
}
