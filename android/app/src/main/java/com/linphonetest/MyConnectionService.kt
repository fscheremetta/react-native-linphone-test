package com.linphonetest

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

class MyConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        phoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val connection = MyConnection()
        connection.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()
        Log.d("MyConnectionService", "Incoming call created")
        return connection
    }

    override fun onCreateOutgoingConnection(
        phoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val connection = MyConnection()
        connection.setDialing()
        Log.d("MyConnectionService", "Outgoing call created")
        return connection
    }
}
