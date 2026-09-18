package com.kiwicup.scheduledmessenger.core

/** Injectable clock so scheduling logic can be tested at a fixed instant. */
fun interface TimeSource {
    fun now(): Long
}

object SystemTimeSource : TimeSource {
    override fun now(): Long = System.currentTimeMillis()
}
