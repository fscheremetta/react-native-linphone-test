package com.linphonetest

import android.content.Intent
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

    override fun getMainComponentName(): String = "linphoneTest"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntentAction(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntentAction(intent)
    }

    private fun handleIntentAction(intent: Intent?) {
        if (intent != null) {
            val action = intent.action
            if ("ACCEPT_CALL" == action) {
                val linphoneModule = getLinphoneModule()
                linphoneModule?.accept()
            } else if ("DECLINE_CALL" == action) {
                val linphoneModule = getLinphoneModule()
                linphoneModule?.decline()
            }
        }
    }

    private fun getLinphoneModule(): LinphoneModule? {
        val reactInstanceManager = reactInstanceManager
        val reactContext = reactInstanceManager.currentReactContext
        return reactContext?.getNativeModule(LinphoneModule::class.java)
    }
}
