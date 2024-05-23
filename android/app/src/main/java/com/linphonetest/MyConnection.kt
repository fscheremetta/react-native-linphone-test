package com.linphonetest

import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log

class MyConnection : Connection() {

    private val TAG = "LinphoneModule"

    override fun onAnswer() {
        super.onAnswer()
        Log.d(TAG, "Call answered")
        setActive()
    }

    override fun onDisconnect() {
        super.onDisconnect()
        Log.d(TAG, "Call disconnected")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }
}
