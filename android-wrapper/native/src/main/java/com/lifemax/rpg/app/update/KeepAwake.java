package com.lifemax.rpg.app.update;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.view.WindowManager;

/**
 * Keeps the screen on, the processor running and the Wi-Fi radio at full power while an update downloads, so a slow
 * network (minutes, not seconds) is not cut by the screen timing out. [release] undoes all of it; every part is
 * optional, so a lock that cannot be taken never stops an update.
 */
public final class KeepAwake {
    /** A download that takes longer than this is not going to finish; the lock then lets go by itself. */
    private static final long MAX_MS = 30 * 60 * 1000L;

    private PowerManager.WakeLock cpu;
    private WifiManager.WifiLock wifi;
    private Activity activity;

    public static KeepAwake start(Activity activity) {
        KeepAwake k = new KeepAwake();
        k.activity = activity;
        try {
            Context app = activity.getApplicationContext();
            k.cpu = ((PowerManager) app.getSystemService(Context.POWER_SERVICE))
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lifemax:update");
            k.cpu.setReferenceCounted(false);
            k.cpu.acquire(MAX_MS);
        } catch (RuntimeException e) { k.cpu = null; }
        try {
            Context app = activity.getApplicationContext();
            k.wifi = ((WifiManager) app.getSystemService(Context.WIFI_SERVICE))
                    .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "lifemax:update");
            k.wifi.setReferenceCounted(false);
            k.wifi.acquire();
        } catch (RuntimeException e) { k.wifi = null; }
        activity.runOnUiThread(() -> activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        return k;
    }

    public void release() {
        try { if (cpu != null && cpu.isHeld()) cpu.release(); } catch (RuntimeException e) { /* already let go */ }
        try { if (wifi != null && wifi.isHeld()) wifi.release(); } catch (RuntimeException e) { /* already let go */ }
        activity.runOnUiThread(() -> activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
    }
}
