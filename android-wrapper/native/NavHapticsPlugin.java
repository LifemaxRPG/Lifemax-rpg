package com.lifemax.rpg;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.getcapacitor.JSArray;
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

    private Vibrator vibrator() {
        Context c = getContext();
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager m = (VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return m == null ? null : m.getDefaultVibrator();
        }
        return (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /** timings: [wait, on, off, on ...] in ms; amplitude 1..255 for every "on" part. This is how the haptic strength setting is made real. */
    @SuppressWarnings("deprecation")
    private void buzz(long[] timings, int amp) {
        Vibrator v = vibrator();
        if (v == null || !v.hasVibrator()) return;
        amp = Math.max(1, Math.min(255, amp));
        if (Build.VERSION.SDK_INT >= 26) {
            int[] amps = new int[timings.length];
            for (int i = 0; i < timings.length; i++) amps[i] = (i % 2 == 1) ? amp : 0;
            v.vibrate(VibrationEffect.createWaveform(timings, amps, -1));
        } else {
            v.vibrate(timings, -1);
        }
    }

    @PluginMethod
    public void pulse(PluginCall call) {
        int ms = Math.max(1, Math.min(400, call.getInt("ms", 12)));
        buzz(new long[]{0, ms}, call.getInt("amp", 150));
        call.resolve();
    }

    @PluginMethod
    public void pattern(PluginCall call) {
        try {
            JSArray a = call.getArray("timings");
            int n = Math.min(a == null ? 0 : a.length(), 24);
            if (n == 0) { call.resolve(); return; }
            long[] t = new long[n + 1];
            t[0] = 0;
            for (int i = 0; i < n; i++) t[i + 1] = Math.max(0, Math.min(600, a.getLong(i)));
            buzz(t, call.getInt("amp", 150));
        } catch (Exception ignored) { /* a bad pattern is just not played */ }
        call.resolve();
    }
}
