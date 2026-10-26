package com.lifemax.rpg;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * LifeMax Voice, native side. Zero-cloud policy: audio is only ever handled by the system's ON-DEVICE recognizer
 * (SpeechRecognizer.createOnDeviceSpeechRecognizer, Android 12+). SpeechRecognizer.createSpeechRecognizer is never
 * used, because on many phones it silently falls back to network recognition. If the on-device engine or its language
 * pack is missing the plugin reports OFFLINE_ENGINE_NOT_READY / OFFLINE_MODEL_MISSING and nothing else happens.
 * The only calls JavaScript may make are: capabilities, requestPermission, start, stop, cancel.
 */
@CapacitorPlugin(
        name = "VoiceEngine",
        permissions = { @Permission(strings = { Manifest.permission.RECORD_AUDIO }, alias = "mic") }
)
public class VoiceEnginePlugin extends Plugin {
    private static final int MAX_FIELD = 64;
    private static final Pattern LOCALE = Pattern.compile("^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,4})?$");

    private final Handler main = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private String session = "";
    private int seq = 0;
    private long lastRms = 0;
    private boolean modelMissing = false;
    private boolean listening = false;

    private boolean hasMic() {
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean onDeviceAvailable() {
        return Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext());
    }

    private String status() {
        if (Build.VERSION.SDK_INT < 31) return "UNSUPPORTED_OS_VERSION";
        if (!onDeviceAvailable() || modelMissing) return "MODEL_NOT_INSTALLED";
        if (!hasMic()) return "PERMISSION_MISSING";
        return "READY_ON_DEVICE";
    }

    private void emit(String event, JSObject payload) {
        JSObject o = new JSObject();
        o.put("v", 1);
        o.put("event", event);
        o.put("sessionId", session);
        o.put("timestampElapsedMs", SystemClock.elapsedRealtime());
        o.put("payload", payload == null ? new JSObject() : payload);
        if (o.toString().length() > 16384) return;   // never forward oversized messages
        notifyListeners("voice", o);
    }

    @PluginMethod
    public void capabilities(PluginCall call) {
        JSObject r = new JSObject();
        r.put("v", 1);
        r.put("api", Build.VERSION.SDK_INT);
        r.put("engine", "ANDROID_ON_DEVICE_API31");
        r.put("status", status());
        r.put("permission", hasMic());
        r.put("onDeviceAvailable", onDeviceAvailable());
        call.resolve(r);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (hasMic()) { JSObject r = new JSObject(); r.put("granted", true); call.resolve(r); return; }
        requestPermissionForAlias("mic", call, "permResult");
    }

    @PermissionCallback
    private void permResult(PluginCall call) {
        JSObject r = new JSObject();
        r.put("granted", hasMic());
        call.resolve(r);
    }

    @PluginMethod
    public void start(final PluginCall call) {
        final String sid = call.getString("sessionId", "");
        final String loc = call.getString("locale", "en-US");
        if (sid == null || sid.length() == 0 || sid.length() > MAX_FIELD || loc == null || !LOCALE.matcher(loc).matches()) {
            call.reject("BAD_REQUEST");
            return;
        }
        if (status().equals("UNSUPPORTED_OS_VERSION") || !onDeviceAvailable() || modelMissing) {
            session = sid;
            JSObject p = new JSObject();
            p.put("code", "OFFLINE_ENGINE_NOT_READY");
            p.put("detail", status());
            emit("VOICE_ERROR", p);
            JSObject r = new JSObject();
            r.put("started", false);
            r.put("reason", "OFFLINE_ENGINE_NOT_READY");
            call.resolve(r);
            return;
        }
        if (!hasMic()) { call.reject("PERMISSION_MISSING"); return; }
        main.post(() -> {
            try {
                begin(sid, loc);
                JSObject r = new JSObject();
                r.put("started", true);
                call.resolve(r);
            } catch (Throwable t) {
                call.reject(String.valueOf(t));
            }
        });
    }

    @RequiresApi(31)
    private void begin(String sid, String loc) {
        destroy();
        session = sid;
        seq = 0;
        lastRms = 0;
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(getContext());
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { listening = true; emit("VOICE_READY", null); }
            @Override public void onBeginningOfSpeech() { emit("VOICE_BEGIN", null); }
            @Override public void onRmsChanged(float rmsdB) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastRms < 100) return;            // at most 10 per second
                lastRms = now;
                JSObject p = new JSObject();
                p.put("level", Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f)));
                emit("VOICE_RMS", p);
            }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { listening = false; emit("VOICE_EOS", null); }
            @Override public void onError(int error) {
                listening = false;
                JSObject p = new JSObject();
                String code;
                if (error == 12 || error == 13) { modelMissing = true; code = "OFFLINE_MODEL_MISSING"; }    // LANGUAGE_NOT_SUPPORTED / LANGUAGE_UNAVAILABLE
                else if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) code = "NO_SPEECH";
                else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) code = "PERMISSION_MISSING";
                else code = "ENGINE_ERROR";
                p.put("code", code);
                p.put("detail", error);
                emit("VOICE_ERROR", p);
            }
            @Override public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> t = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                float[] c = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                if (t == null || t.isEmpty()) { JSObject e = new JSObject(); e.put("code", "NO_SPEECH"); emit("VOICE_ERROR", e); return; }
                String text = t.get(0);
                if (text.length() > 2000) text = text.substring(0, 2000);
                JSObject p = new JSObject();
                p.put("text", text);
                p.put("confidence", c != null && c.length > 0 ? c[0] : 0.9);
                p.put("provider", "ANDROID_ON_DEVICE_API31");
                p.put("offlineVerified", true);
                seq++;
                JSObject o = new JSObject();
                p.put("utteranceId", "utt_" + session + "_" + seq);
                emit("VOICE_FINAL_TRANSCRIPT", p);
            }
            @Override public void onPartialResults(Bundle partial) {
                ArrayList<String> t = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (t == null || t.isEmpty()) return;
                String text = t.get(0);
                if (text.length() > 2000) text = text.substring(0, 2000);
                JSObject p = new JSObject();
                p.put("text", text);
                emit("VOICE_PARTIAL", p);
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, loc.replace('_', '-'));
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizer.startListening(i);
    }

    private void destroy() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Throwable ignored) { }
            try { recognizer.destroy(); } catch (Throwable ignored) { }
            recognizer = null;
        }
        listening = false;
    }

    @PluginMethod
    public void stop(final PluginCall call) {
        main.post(() -> {
            try { if (recognizer != null) recognizer.stopListening(); } catch (Throwable ignored) { }
            call.resolve();
        });
    }

    @PluginMethod
    public void cancel(final PluginCall call) {
        main.post(() -> { destroy(); call.resolve(); });
    }

    // The microphone is released whenever the app is not in front.
    @Override protected void handleOnPause() { super.handleOnPause(); main.post(this::destroy); }
    @Override protected void handleOnStop() { super.handleOnStop(); main.post(this::destroy); }
    @Override protected void handleOnDestroy() { super.handleOnDestroy(); main.post(this::destroy); }
}
