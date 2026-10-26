package com.lifemax.rpg;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Date;

/**
 * Home-screen widget showing the Life Director dial. The web app draws the plain dial (rings,
 * sessions, labels) into dial.png; this class adds the parts that change by the minute: the
 * shaded past, the moving hand and the centre text. It re-draws every few minutes on its own.
 */
public class LifeDialWidget extends AppWidgetProvider {
    static final String TICK = "com.lifemax.rpg.WIDGET_TICK";
    private static final float VB = 380f;   // the picture covers SVG units -10 .. 370

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        render(ctx, mgr, ids);
        schedule(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (TICK.equals(intent.getAction())) {
            updateAll(ctx);
            schedule(ctx);
        }
    }

    @Override
    public void onEnabled(Context ctx) { schedule(ctx); }

    @Override
    public void onDisabled(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tickIntent(ctx));
    }

    static PendingIntent tickIntent(Context ctx) {
        Intent i = new Intent(ctx, LifeDialWidget.class).setAction(TICK);
        return PendingIntent.getBroadcast(ctx, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + 10 * 60 * 1000L, tickIntent(ctx));
    }

    static int[] ids(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, LifeDialWidget.class));
    }

    static void updateAll(Context ctx) {
        int[] ids = ids(ctx);
        if (ids.length > 0) render(ctx, AppWidgetManager.getInstance(ctx), ids);
    }

    static String read(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    static void render(Context ctx, AppWidgetManager mgr, int[] ids) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_dial);
        Bitmap bmp = null;
        try { bmp = compose(ctx); } catch (Throwable t) { /* fall through to the plain picture */ }
        if (bmp != null) rv.setImageViewBitmap(R.id.widget_dial, bmp);
        Intent open = new Intent(ctx, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN, "life-director");
        rv.setOnClickPendingIntent(R.id.widget_root,
                PendingIntent.getActivity(ctx, 8, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        for (int id : ids) mgr.updateAppWidget(id, rv);
    }

    private static String clock(Context ctx, long epochSec) {
        return android.text.format.DateFormat.getTimeFormat(ctx).format(new Date(epochSec * 1000L));
    }

    private static String span(long sec) {
        long m = Math.max(0, Math.round(sec / 60.0));
        if (m < 1) return "under a minute";
        if (m < 60) return m + " min";
        return (m / 60) + "h" + (m % 60 > 0 ? " " + (m % 60) + "m" : "");
    }

    static Bitmap compose(Context ctx) throws Exception {
        File png = new File(ctx.getFilesDir(), "dial.png");
        if (!png.exists()) return null;
        Bitmap src = BitmapFactory.decodeFile(png.getPath());
        if (src == null) return null;
        Bitmap bmp = src.copy(Bitmap.Config.ARGB_8888, true);
        src.recycle();
        Canvas cv = new Canvas(bmp);
        float k = bmp.getWidth() / VB;
        float c = bmp.getWidth() / 2f;
        String js = read(new File(ctx.getFilesDir(), "dial.json"));
        if (js == null) return bmp;
        JSONObject o = new JSONObject(js);
        long now = System.currentTimeMillis() / 1000L, d0 = o.getLong("d0"), d1 = o.getLong("d1");
        JSONArray ss = o.optJSONArray("sessions");

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean live = now >= d0 && now < d1;
        if (live) {
            float an = (now - d0) * 360f / (d1 - d0);
            // the part of the day that has gone
            if (an > 1f) {
                float r0 = 89f * k, r1 = 141f * k, mid = (r0 + r1) / 2f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r1 - r0);
                p.setColor(Color.argb(115, 8, 10, 16));
                cv.drawArc(new RectF(c - mid, c - mid, c + mid, c + mid), -90f, an, false, p);
            }
            // the hand
            double t = Math.toRadians(an - 90);
            float cx = (float) Math.cos(t), cy = (float) Math.sin(t);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(2.4f * k);
            p.setColor(Color.rgb(125, 211, 252));
            p.setShadowLayer(4f * k, 0, 0, Color.rgb(56, 189, 248));
            cv.drawLine(c + cx * 84f * k, c + cy * 84f * k, c + cx * 156f * k, c + cy * 156f * k, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(224, 242, 254));
            cv.drawCircle(c + cx * 156f * k, c + cy * 156f * k, 3.8f * k, p);
            p.clearShadowLayer();
        }

        // centre: what is on now, or what comes next
        String kick = "", title = "", sub = "";
        int kcol = Color.rgb(148, 163, 184);
        if (!live) {
            kick = "OPEN LIFEMAX";
            title = "Tap to refresh";
            sub = "today's plan";
        } else {
            JSONObject cur = null, next = null;
            for (int i = 0; ss != null && i < ss.length(); i++) {
                JSONObject s = ss.getJSONObject(i);
                if (s.getLong("s") <= now && s.getLong("e") > now) { if (cur == null) cur = s; }
                else if (s.getLong("s") > now && next == null) next = s;
            }
            if (cur != null) {
                kick = "ON NOW"; kcol = Color.rgb(52, 211, 153);
                title = cur.getString("t");
                sub = span(cur.getLong("e") - now) + " left";
            } else if (next != null) {
                kick = "UP NEXT · IN " + span(next.getLong("s") - now).toUpperCase();
                title = next.getString("t");
                sub = clock(ctx, next.getLong("s")) + " – " + clock(ctx, next.getLong("e"));
            } else {
                kick = clock(ctx, now);
                title = "Nothing planned";
                sub = "for the rest of today";
            }
        }
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextAlign(Paint.Align.LEFT);
        float w = 150f * k;
        float x = c - w / 2f;
        tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tp.setTextSize(9.5f * k);
        tp.setColor(kcol);
        StaticLayout kl = layout(kick, tp, (int) w, 1);
        tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tp.setTextSize(17f * k);
        tp.setColor(Color.rgb(240, 244, 250));
        StaticLayout tl = layout(title, tp, (int) w, 2);
        tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tp.setTextSize(11f * k);
        tp.setColor(Color.rgb(170, 182, 200));
        StaticLayout sl = layout(sub, tp, (int) w, 1);
        float gap = 3f * k, total = kl.getHeight() + gap + tl.getHeight() + gap + sl.getHeight();
        float y = c - total / 2f;
        for (StaticLayout l : new StaticLayout[] { kl, tl, sl }) {
            cv.save();
            cv.translate(x, y);
            l.draw(cv);
            cv.restore();
            y += l.getHeight() + gap;
        }
        return bmp;
    }

    private static StaticLayout layout(String text, TextPaint tp, int width, int maxLines) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), tp, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }
}
