package com.linphonetest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build

import androidx.core.app.NotificationCompat

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

import android.util.Log

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.bridge.WritableMap

import org.linphone.core.*

import com.linphonetest.MyConnection
import com.linphonetest.MyConnectionService

class LinphoneModule(reactContext: ReactApplicationContext): ReactContextBaseJavaModule(reactContext) {
    override fun getName() = "LinphoneModule"

    private val TAG = "LinphoneModule"
    private var core: Core = Factory.instance().createCore(null, null, reactApplicationContext)

    private val coreListener: CoreListener = object : CoreListenerStub( ) {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            Log.d(TAG, "onCallStateChanged: $state $message")

            val params = Arguments.createMap().apply {
                putString("state", state.toString())
                putString("message", message)
            }
            sendEvent(reactContext, "callstate", params)
        }
    }

    /**
     * React Native Event Emitter
     * */

    private var listenerCount = 0;

    private fun sendEvent(reactContext: ReactApplicationContext, eventName: String, params: WritableMap) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, params)
    }

    @ReactMethod fun addListener(_eventName: String) {
        if( listenerCount == 0) core.addListener(coreListener)
        listenerCount += 1;
    }

    @ReactMethod fun removeListener() {
        listenerCount -= 1;
        if( listenerCount == 0) core.removeListener(coreListener)
    }

    @ReactMethod fun removeListeners() {
        listenerCount = 0;
        core.removeListener(coreListener)
    }

    /**
     * End of React native Event Emitter
     * */

    private fun onCreateIncomingConnection(phoneAccount: PhoneAccountHandle, request: ConnectionRequest): Connection {
        val connection = MyConnection()
        connection.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()
        Log.d(TAG, "incoming call created")
        return connection
    }

    private fun onCreateOutgoingConnection(phoneAccount: PhoneAccountHandle, request: ConnectionRequest): Connection {
        val connection = MyConnection()
        connection.setDialing()
        Log.d(TAG, "outgoing call created")
        return connection 
    }

    @ReactMethod fun register(username: String, password: String, domain: String, transport: String, promise: Promise) {
        val authInfo = Factory.instance().createAuthInfo(username, null, password, null, null, null);
        val accountParams = core.createAccountParams();

        val identity = Factory.instance().createAddress("sip:${username}@${domain}")
        accountParams.identityAddress = identity;

        val address = Factory.instance().createAddress("sip:${domain}")

        address?.transport = TransportType.valueOf(transport)
        accountParams.serverAddress = address
        accountParams.isRegisterEnabled = true

        val account = core.createAccount(accountParams)
        core.addAuthInfo(authInfo)
        core.addAccount(account)
        core.defaultAccount = account

        val listener = { _: Account, state: RegistrationState, message: String ->
            if (state !== RegistrationState.Ok && state !== RegistrationState.Progress) {
                Log.d(TAG, "Registration fail $state: $message")
                promise.reject(state.toString(), message)
            } else if (state == RegistrationState.Ok) {
                promise.resolve("Registration successful")
            }
        } 

        account.addListener(listener)
        core.start()
        registerPhoneAccount()
    }

    @ReactMethod fun unregister(promise: Promise) {
        val account = core.defaultAccount
        account ?: return

        val params = account.params
        val clonedParams = params.clone()

        clonedParams.isRegisterEnabled = false
        account.params = clonedParams

        account.addListener{ _, state, message ->
            if (state !== RegistrationState.Cleared && state !== RegistrationState.Progress) {
                promise.reject(state.toString(), message)
            } else if (state == RegistrationState.Cleared) {
                promise.resolve("Unregister successful")
            }
        }
    }

    @ReactMethod fun delete(promise: Promise) {
        val account = core.defaultAccount
        account ?: return

        core.removeAccount(account)

        promise.resolve("Delete successful")
    }

    @ReactMethod fun accept() {
        core.currentCall?.accept()

        cancelIncomingCallNotification()
    }

    @ReactMethod fun terminate() {
        core.currentCall?.terminate()

        cancelIncomingCallNotification()
    }

    @ReactMethod fun decline() {
        core.currentCall?.decline(Reason.Declined)

        cancelIncomingCallNotification()
    }

    @ReactMethod fun call(address: String, promise: Promise) {
        val callParams = core.createCallParams(null)
        callParams ?: return promise.reject("Call-creation", "Call params creation failed")

        callParams.mediaEncryption = MediaEncryption.SRTP

        val remoteAddress = Factory.instance().createAddress(address)
        remoteAddress ?: return promise.reject("Call-creation", "Address creation failed")

        val call = core.inviteAddressWithParams(remoteAddress, callParams)

        if (call == null) {
            promise.reject("Call-creation", "Call invite failed")
        } else {
            promise.resolve("Call successful")
        }
    }

    private fun showIncomingCallNotification() {
        val context = reactApplicationContext.applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "incoming_call_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Incoming Call")
            .setContentText("You have an incoming call.")
            .setSmallIcon(R.drawable.ic_call)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_accept, "Accept", getPendingIntentForAction("ACCEPT_CALL"))
            .addAction(R.drawable.ic_decline, "Decline", getPendingIntentForAction("DECLINE_CALL"))
            .build()

        notificationManager.notify(1, notification)
    }

    private fun getPendingIntentForAction(action: String): PendingIntent {
        val intent = Intent(reactApplicationContext, MainActivity::class.java).apply {
            this.action = action
        }
        return PendingIntent.getActivity(reactApplicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun cancelIncomingCallNotification() {
        val notificationManager = reactApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)
    }

    private fun registerPhoneAccount() {
        val telecomManager = reactApplicationContext.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        
        val phoneAccountHandle = PhoneAccountHandle(ComponentName(reactApplicationContext, MyConnectionService::class.java), "MyConnectionService")
        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "My Connection Service")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .build()
        telecomManager.registerPhoneAccount(phoneAccount)
    }
}
