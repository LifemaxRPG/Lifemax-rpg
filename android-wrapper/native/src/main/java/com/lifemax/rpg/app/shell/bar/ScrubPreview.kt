package com.lifemax.rpg.app.shell.bar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.json.JSONObject
import org.json.JSONTokener

/** Disposable, low-resolution previews from the same cache used by web bar scrubbing. */
internal class ScrubPreview(context: Context, private val web: WebView) : FrameLayout(context) {
    private val picture = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val title =
        TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(Color.WHITE)
            setShadowLayer(10f, 0f, 2f, Color.BLACK)
        }
    private var generation = 0
    private var key: String? = null

    init {
        setBackgroundColor(Color.rgb(16, 20, 29))
        addView(picture, LayoutParams(-1, -1))
        addView(View(context).apply { setBackgroundColor(0x66000000) }, LayoutParams(-1, -1))
        addView(title, LayoutParams(-1, -1))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = true
        visibility = GONE
    }

    fun show(tab: BarTab?) {
        if (tab == null) {
            generation++
            key = null
            visibility = GONE
            picture.setImageDrawable(null)
            return
        }
        if (key == tab.key) return
        key = tab.key
        val ticket = ++generation
        picture.setImageDrawable(null)
        title.text = tab.key.replace('-', ' ').replaceFirstChar { it.uppercase() }
        visibility = VISIBLE
        val quoted = JSONObject.quote(tab.key)
        // Journal previews are deliberately omitted, including previously cached unlocked images.
        val script =
            """(function(){var k=$quoted,b=document.querySelector('.navbtn[data-view="'+k+'"]');
            return JSON.stringify({label:b?b.textContent.trim():k,img:k==='history'?'':(typeof lmaThumbs==='object'&&lmaThumbs[k]||'')});})()"""
        web.evaluateJavascript(script) { raw ->
            if (ticket != generation || !isAttachedToWindow) return@evaluateJavascript
            try {
                val data =
                    JSONObject(JSONTokener(raw).nextValue() as? String ?: return@evaluateJavascript)
                title.text = data.optString("label", tab.key)
                val encoded = data.optString("img")
                if (!encoded.startsWith("data:image/") || encoded.length > 524288)
                    return@evaluateJavascript
                val bytes = Base64.decode(encoded.substringAfter(','), Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (
                    bounds.outWidth !in 1..4096 ||
                        bounds.outHeight !in 1..4096 ||
                        bounds.outWidth.toLong() * bounds.outHeight > 4_000_000
                )
                    return@evaluateJavascript
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = maxOf(1, bounds.outWidth / 24, bounds.outHeight / 96)
                    }
                val decoded =
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        ?: return@evaluateJavascript
                val soft =
                    Bitmap.createScaledBitmap(
                        decoded,
                        24,
                        (24 * decoded.height / decoded.width).coerceIn(1, 96),
                        true,
                    )
                if (soft !== decoded) decoded.recycle()
                picture.setImageBitmap(soft)
                picture.alpha = .65f
            } catch (_: Exception) {
                // A missing or stale cached picture keeps the labelled, tinted fallback.
            }
        }
    }

    override fun onDetachedFromWindow() {
        show(null)
        super.onDetachedFromWindow()
    }
}
