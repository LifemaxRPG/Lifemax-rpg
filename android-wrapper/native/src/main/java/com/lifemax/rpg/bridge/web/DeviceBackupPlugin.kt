package com.lifemax.rpg.bridge.web

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.core.platform.DeviceBackupChoice

/** Settings' switch for Android device backup: reads and changes the owner's choice. */
@CapacitorPlugin(name = "DeviceBackup")
class DeviceBackupPlugin : Plugin() {
    @PluginMethod
    fun get(call: PluginCall) {
        call.resolve(JSObject().put("allowed", DeviceBackupChoice.allowed(context)))
    }

    @PluginMethod
    fun set(call: PluginCall) {
        val allowed = call.getBoolean("allowed")
        when {
            allowed == null -> call.reject("allowed must be true or false")
            !DeviceBackupChoice.set(context, allowed) ->
                call.reject("The choice could not be saved")
            else -> call.resolve(JSObject().put("allowed", allowed))
        }
    }
}
