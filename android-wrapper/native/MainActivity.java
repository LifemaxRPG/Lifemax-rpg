package com.lifemax.rpg;

import android.graphics.Color;
import android.os.Build;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;
import com.lifemax.rpg.app.launch.LaunchRules;
import com.lifemax.rpg.bridge.web.DeviceBackupPlugin;
import com.lifemax.rpg.bridge.web.HealthSyncPlugin;
import com.lifemax.rpg.bridge.web.NativeCashPlugin;
import com.lifemax.rpg.bridge.web.NativeNutritionPlugin;
import com.lifemax.rpg.bridge.web.NativeOverviewPlugin;

public class MainActivity extends BridgeActivity {
    static final String EXTRA_OPEN = "lma_open";
    /** Set by the home-screen widget (only a screen LaunchRules knows); the web app reads and clears it. */
    static volatile String launchTarget = null;

    /*
     * A new intent while the app runs (a widget tap, a lifemax:// link). Capacitor also calls this from onCreate with the
     * launch intent, once the bridge exists (BridgeActivity.load); super hands a link to the App plugin (appUrlOpen).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        take(intent);
    }

    /* Note the screen the intent asks for, then mark the intent delivered so nothing can act on it a second time. */
    private void take(Intent intent) {
        if (intent == null) return;
        try {
            String target = LaunchRules.openTarget(intent.getStringExtra(EXTRA_OPEN));
            if (target != null) launchTarget = target;
        } catch (RuntimeException e) {
            /* an extra another app packed so it cannot be read: ignore it, never fail to start the home screen */
        }
        setIntent(consumed(intent));
    }

    /** The same intent without what it asked for: no lifemax:// link and no screen to open. */
    static Intent consumed(Intent intent) {
        Intent out = new Intent(intent);
        out.setData(null);
        try {
            out.removeExtra(EXTRA_OPEN);
        } catch (RuntimeException e) {
            out.replaceExtras((Bundle) null);
        }
        return out;
    }

    private static boolean launchedFromHistory(Intent intent) {
        return (intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AppLauncherPlugin.class);
        registerPlugin(FileSaverPlugin.class);
        registerPlugin(UpdaterPlugin.class);
        registerPlugin(BackupFolderPlugin.class);
        registerPlugin(DeviceBackupPlugin.class);
        registerPlugin(HealthSyncPlugin.class);
        registerPlugin(WidgetBridgePlugin.class);
        registerPlugin(NavHapticsPlugin.class);
        registerPlugin(PageThumbPlugin.class);
        registerPlugin(VoiceEnginePlugin.class);
        registerPlugin(BiometricLockPlugin.class);
        registerPlugin(LiveWebPlugin.class);
        registerPlugin(CalendarSyncPlugin.class);
        registerPlugin(NativeCashPlugin.class);
        registerPlugin(NativeOverviewPlugin.class);
        registerPlugin(com.lifemax.rpg.bridge.web.NativeCharacterPlugin.class);
        registerPlugin(com.lifemax.rpg.bridge.web.NativeLogPlugin.class);
        registerPlugin(com.lifemax.rpg.bridge.web.NativePlanPlugin.class);
        registerPlugin(com.lifemax.rpg.bridge.web.NativeLifeDirectorPlugin.class);
        registerPlugin(com.lifemax.rpg.bridge.web.NativeMediaPlugin.class);
        registerPlugin(NativeNutritionPlugin.class);
        // Backlog NAV-4: after process death, a configuration change or a reopen from Recents, Android hands back the
        // intent that first started the activity. Drop its link and screen before the bridge reads them (the bridge keeps
        // getIntent().getData() as the launch URL), so a widget tap or a lifemax:// link does not run twice.
        Intent launch = getIntent();
        if (launch != null && (savedInstanceState != null || launchedFromHistory(launch))) setIntent(consumed(launch));
        LiveWeb.prepare(this);          /* decide which web build to show before the page starts */
        super.onCreate(savedInstanceState);
        LiveWeb.apply(getBridge(), this);

        // Draw edge to edge, then keep the page clear of the status bar, camera cut-out,
        // gesture bar and keyboard by padding the content area by the system insets.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // Transparent bars over the app's own background colour (JS retints it for light mode).
        final int bg = Color.parseColor("#0c0e12");
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        getWindow().getDecorView().setBackgroundColor(bg);
        WindowInsetsControllerCompat ctl = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        ctl.setAppearanceLightStatusBars(false);
        ctl.setAppearanceLightNavigationBars(false);
        final View content = findViewById(android.R.id.content);
        content.setBackgroundColor(bg);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);

        // Back (button or gesture) is handled by the web app: it closes a popup or returns to the
        // previous tab. As a home screen it must never close the app, so nothing else happens.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView w = getBridge() == null ? null : getBridge().getWebView();
                if (w == null) return;
                w.evaluateJavascript(
                        "(function(){try{return window.lmaBack?window.lmaBack():false}catch(e){return false}})()",
                        null);
            }
        });
    }
}


