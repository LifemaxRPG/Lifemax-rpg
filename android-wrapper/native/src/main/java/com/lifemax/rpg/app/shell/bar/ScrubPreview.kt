package com.lifemax.rpg.app.shell.bar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
    private val icon =
        object : View(context) {
            var cells: List<FloatArray> = emptyList()
            private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val unit = minOf(width, height) / 8f
                for (r in cells) canvas.drawRect(
                    r[0] * unit,
                    r[1] * unit,
                    (r[0] + r[2]) * unit,
                    (r[1] + r[3]) * unit,
                    ink,
                )
            }
        }
    private var generation = 0
    private var key: String? = null

    init {
        setBackgroundColor(Color.rgb(16, 20, 29))
        addView(picture, LayoutParams(-1, -1))
        addView(View(context).apply { setBackgroundColor(0x66000000) }, LayoutParams(-1, -1))
        val density = resources.displayMetrics.density
        val caption =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(
                    icon,
                    LinearLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt()),
                )
                addView(
                    title,
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = (16 * density).toInt() },
                )
            }
        addView(caption, LayoutParams(-1, -2, Gravity.CENTER))
        if (Build.VERSION.SDK_INT >= 31)
            picture.setRenderEffect(
                RenderEffect.createBlurEffect(18f * density, 18f * density, Shader.TileMode.CLAMP)
            )
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
        icon.cells = emptyList()
        icon.invalidate()
        title.text = tab.key.replace('-', ' ').replaceFirstChar { it.uppercase() }
        visibility = VISIBLE
        val quoted = JSONObject.quote(tab.key)
        // Journal previews are deliberately omitted, including previously cached unlocked images.
        val script =
            """(function(){var k=$quoted,b=document.querySelector('.navbtn[data-view="'+k+'"]');
            var cells=b?Array.from(b.querySelectorAll('.ico svg rect')).slice(0,64).map(function(r){return ['x','y','width','height'].map(function(a){return Number(r.getAttribute(a)||0)})}):[];
            return JSON.stringify({icon:cells,label:b?b.textContent.trim():k,img:k==='history'?'':(typeof lmaThumbs==='object'&&lmaThumbs[k]||'')});})()"""
        web.evaluateJavascript(script) { raw ->
            if (ticket != generation || !isAttachedToWindow) return@evaluateJavascript
            try {
                val data =
                    JSONObject(JSONTokener(raw).nextValue() as? String ?: return@evaluateJavascript)
                title.text = data.optString("label", tab.key)
                val cells = data.optJSONArray("icon")
                icon.cells =
                    (0 until minOf(cells?.length() ?: 0, 64)).mapNotNull { i ->
                        val row = cells?.optJSONArray(i) ?: return@mapNotNull null
                        if (row.length() != 4) return@mapNotNull null
                        FloatArray(4) { row.optDouble(it, 0.0).toFloat().coerceIn(0f, 8f) }
                    }
                icon.invalidate()
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
                        inSampleSize = maxOf(1, bounds.outWidth / 320, bounds.outHeight / 1280)
                    }
                val decoded =
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        ?: return@evaluateJavascript
                // Retain thumbnail detail; blur pixels rather than magnifying a tiny mosaic.
                val soft = if (Build.VERSION.SDK_INT >= 31) decoded else blurLegacy(decoded)
                if (soft !== decoded) decoded.recycle()
                picture.setImageBitmap(soft)
                picture.alpha = .65f
            } catch (_: Exception) {
                // A missing or stale cached picture keeps the labelled, tinted fallback.
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun blurLegacy(source: Bitmap): Bitmap {
        val rs = RenderScript.create(context)
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        var input: Allocation? = null
        var target: Allocation? = null
        var blur: ScriptIntrinsicBlur? = null
        try {
            input = Allocation.createFromBitmap(rs, source)
            target = Allocation.createFromBitmap(rs, output)
            blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blur.setRadius(12f)
            blur.setInput(input)
            blur.forEach(target)
            target.copyTo(output)
            return output
        } catch (error: Exception) {
            output.recycle()
            throw error
        } finally {
            blur?.destroy()
            target?.destroy()
            input?.destroy()
            rs.destroy()
        }
    }

    override fun onDetachedFromWindow() {
        show(null)
        super.onDetachedFromWindow()
    }
}
