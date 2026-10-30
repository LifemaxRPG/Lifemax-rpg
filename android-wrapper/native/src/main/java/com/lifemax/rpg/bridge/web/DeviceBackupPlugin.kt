package com.lifemax.rpg.bridge.web

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.backup.DeviceBackupChoice

/**
 * Settings' switch for Android device backup: reads and changes the owner's choice, and what
 * Android last did.
 */
@CapacitorPlugin(name = "DeviceBackup")
class DeviceBackupPlugin : Plugin() {
    @PluginMethod
    fun get(call: PluginCall) {
        val record = DeviceBackupChoice.record(context)
        val answer = JSObject().put("allowed", DeviceBackupChoice.allowed(context))
        if (record.has(DeviceBackupChoice.ASKED_AT)) {
            answer.put(
                "lastRequest",
                JSObject()
                    .put("at", record.getLong(DeviceBackupChoice.ASKED_AT))
                    .put("transfer", record.optBoolean(DeviceBackupChoice.TRANSFER))
                    .put("copied", record.optBoolean(DeviceBackupChoice.COPIED)),
            )
        }
        if (record.has(DeviceBackupChoice.TOO_BIG_BYTES)) {
            answer.put(
                "tooBig",
                JSObject()
                    .put("bytes", record.getLong(DeviceBackupChoice.TOO_BIG_BYTES))
                    .put("quota", record.optLong(DeviceBackupChoice.QUOTA_BYTES)),
            )
        }
        call.resolve(answer)
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
