package com.lifemax.rpg;

import android.os.Build;
import android.view.WindowManager;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * The optional Journal lock. Uses the phone's own fingerprint or face unlock, with the screen lock (PIN, pattern,
 * password) as the fallback, so it works on a phone with no fingerprint set up. Nothing is stored by the app: Android
 * does the checking. {@code secure} hides the screen from the app switcher and screenshots while the Journal is open.
 */
@CapacitorPlugin(name = "BiometricLock")
public class BiometricLockPlugin extends Plugin {

    /** Fingerprint/face or the screen lock on Android 11+; fingerprint/face only below that (the combination is not supported). */
    private int authenticators() {
        return Build.VERSION.SDK_INT >= 30
                ? BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL
                : BiometricManager.Authenticators.BIOMETRIC_WEAK;
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject r = new JSObject();
        try {
            int res = BiometricManager.from(getContext()).canAuthenticate(authenticators());
            r.put("available", res == BiometricManager.BIOMETRIC_SUCCESS);
            r.put("code", res);
            r.put("kind", res == BiometricManager.BIOMETRIC_SUCCESS ? "ok" : (res == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ? "none_enrolled" : "unavailable"));
        } catch (Exception e) {
            r.put("available", false);
            r.put("kind", "unavailable");
        }
        call.resolve(r);
    }

    @PluginMethod
    public void authenticate(final PluginCall call) {
        final String title = call.getString("title", "Unlock");
        final String subtitle = call.getString("subtitle", "");
        if (!(getActivity() instanceof FragmentActivity)) { fail(call, "no_activity", false); return; }
        final FragmentActivity act = (FragmentActivity) getActivity();
        act.runOnUiThread(() -> {
            try {
                BiometricPrompt.PromptInfo.Builder b = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title).setSubtitle(subtitle).setAllowedAuthenticators(authenticators());
                if (Build.VERSION.SDK_INT < 30) b.setNegativeButtonText("Cancel");
                BiometricPrompt prompt = new BiometricPrompt(act, ContextCompat.getMainExecutor(act), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        JSObject r = new JSObject(); r.put("ok", true); call.resolve(r);
                    }
                    @Override public void onAuthenticationError(int code, CharSequence msg) {
                        boolean cancelled = code == BiometricPrompt.ERROR_USER_CANCELED || code == BiometricPrompt.ERROR_NEGATIVE_BUTTON || code == BiometricPrompt.ERROR_CANCELED;
                        fail(call, String.valueOf(msg), cancelled);
                    }
                });
                prompt.authenticate(b.build());
            } catch (Exception e) {
                fail(call, String.valueOf(e.getMessage()), false);
            }
        });
    }

    private void fail(PluginCall call, String error, boolean cancelled) {
        JSObject r = new JSObject(); r.put("ok", false); r.put("error", error); r.put("cancelled", cancelled); call.resolve(r);
    }

    /** FLAG_SECURE: blank in the app switcher and in screenshots while the Journal is showing. */
    @PluginMethod
    public void secure(final PluginCall call) {
        final boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        if (getActivity() == null) { call.resolve(); return; }
        getActivity().runOnUiThread(() -> {
            try {
                if (on) getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                else getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } catch (Exception ignored) { /* ignore */ }
            call.resolve();
        });
    }
}
