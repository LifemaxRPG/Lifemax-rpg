package com.lifemax.rpg;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.PixelCopy;
import android.view.View;
import android.webkit.WebView;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;

/** A small picture of what the page looks like right now, for the blurred preview shown while scrubbing the bottom bar. */
@CapacitorPlugin(name = "PageThumb")
public class PageThumbPlugin extends Plugin {

    private void done(PluginCall call, Bitmap b) {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 55, o);
            b.recycle();
            JSObject r = new JSObject();
            r.put("img", "data:image/jpeg;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP));
            call.resolve(r);
        } catch (Throwable t) {
            call.reject(String.valueOf(t.getMessage()));
        }
    }

    /** Software fallback: draw the WebView into a small canvas. */
    private void drawFallback(PluginCall call, WebView v, int div, int w, int h, String why) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.scale(1f / div, 1f / div);
            v.draw(c);
            done(call, b);
        } catch (Throwable t) {
            call.reject("snapshot failed (" + why + "; draw: " + t + ")");
        }
    }

    @PluginMethod
    public void snap(final PluginCall call) {
        final int div = Math.max(2, call.getInt("div", 4));
        if (getActivity() == null || getBridge() == null) { call.reject("no activity"); return; }
        getActivity().runOnUiThread(() -> {
            try {
                final WebView v = getBridge().getWebView();
                final int w = v.getWidth() / div, h = v.getHeight() / div;
                if (w < 8 || h < 8) { call.reject("no size"); return; }
                if (Build.VERSION.SDK_INT >= 26) {
                    final Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    int[] loc = new int[2];
                    v.getLocationInWindow(loc);
                    Rect src = new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight());
                    try {
                        PixelCopy.request(getActivity().getWindow(), src, b, result -> {
                            if (result == PixelCopy.SUCCESS) done(call, b);
                            else { b.recycle(); drawFallback(call, v, div, w, h, "pixelcopy " + result); }
                        }, new Handler(Looper.getMainLooper()));
                    } catch (Throwable t) {
                        b.recycle();
                        drawFallback(call, v, div, w, h, "pixelcopy " + t);
                    }
                } else {
                    drawFallback(call, v, div, w, h, "old android");
                }
            } catch (Throwable t) {
                call.reject(String.valueOf(t));
            }
        });
    }
}
