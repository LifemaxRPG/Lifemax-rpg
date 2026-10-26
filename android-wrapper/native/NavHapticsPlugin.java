package com.lifemax.rpg;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Crisp system haptics for the bottom bar: a tick for each tab you pass, a firmer pulse at the start and end. */
@CapacitorPlugin(name = "NavHaptics")
public class NavHapticsPlugin extends Plugin {

    @SuppressWarnings("deprecation")
    private void fire(final int kind, final PluginCall call) {
        if (getActivity() == null || getBridge() == null) { call.resolve(); return; }
        getActivity().runOnUiThread(() -> {
            try {
                View v = getBridge().getWebView();
                if (v != null) v.performHapticFeedback(kind, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignored) { /* no haptics on this device */ }
            call.resolve();
        });
    }

    @PluginMethod
    public void tick(PluginCall call) { fire(HapticFeedbackConstants.CLOCK_TICK, call); }

    @PluginMethod
    public void start(PluginCall call) {
        fire(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.GESTURE_START : HapticFeedbackConstants.LONG_PRESS, call);
    }

    @PluginMethod
    public void end(PluginCall call) {
        fire(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.GESTURE_END : HapticFeedbackConstants.VIRTUAL_KEY, call);
    }
}
