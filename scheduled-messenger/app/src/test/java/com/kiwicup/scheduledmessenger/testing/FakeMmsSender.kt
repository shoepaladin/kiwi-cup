package com.kiwicup.scheduledmessenger.testing

import com.kiwicup.scheduledmessenger.data.sms.MmsSender
import com.kiwicup.scheduledmessenger.data.sms.OutgoingMms
import com.kiwicup.scheduledmessenger.data.sms.SendResult

class FakeMmsSender(var nextResult: SendResult = SendResult.Sent) : MmsSender {
    val calls = mutableListOf<OutgoingMms>()
    override suspend fun send(message: OutgoingMms): SendResult {
        calls += message
        return nextResult
    }
}
