package com.kiwicup.scheduledmessenger.testing

import com.kiwicup.scheduledmessenger.data.sms.SendResult
import com.kiwicup.scheduledmessenger.data.sms.SmsSender

/** Records every send and answers with a scripted result. */
class FakeSmsSender(var nextResult: SendResult = SendResult.Sent) : SmsSender {
    data class Call(val address: String, val body: String)

    val calls = mutableListOf<Call>()

    override suspend fun send(address: String, body: String): SendResult {
        calls += Call(address, body)
        return nextResult
    }
}
