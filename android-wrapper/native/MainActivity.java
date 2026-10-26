package com.lifemax.rpg;

import android.graphics.Color;
import android.os.Build;
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

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AppLauncherPlugin.class);
        registerPlugin(FileSaverPlugin.class);
        registerPlugin(UpdaterPlugin.class);
        registerPlugin(BackupFolderPlugin.class);
        registerPlugin(HealthSyncPlugin.class);
        super.onCreate(savedInstanceState);

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
