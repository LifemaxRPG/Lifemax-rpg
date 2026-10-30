package com.lifemax.rpg.app.shell

import android.graphics.*
import android.os.Bundle
import android.util.Base64
import android.util.LruCache
import android.view.*
import android.view.accessibility.AccessibilityEvent
import androidx.core.graphics.PathParser
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import kotlin.math.*
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray?.items(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject.f(key: String, fallback: Float = 0f): Float =
    optDouble(key, fallback.toDouble()).toFloat().let { if (it.isFinite()) it else fallback }

private fun JSONObject.rect(): RectF = RectF(f("x"), f("y"), f("x") + f("w"), f("y") + f("h"))

private fun JSONObject.argb(key: String): Int = optLong(key).toInt()

private val bitmapCache =
    object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount + key.length * 2) / 1024
    }

private fun bitmap(data: String): Bitmap? {
    if (!data.startsWith("data:image/") || !data.contains(";base64,")) return null
    bitmapCache.get(data)?.let {
        return it
    }
    val raw = Base64.decode(data.substringAfter(","), Base64.DEFAULT)
    if (raw.size > 8_000_000) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    if (
        bounds.outWidth <= 0 ||
            bounds.outHeight <= 0 ||
            bounds.outWidth.toLong() * bounds.outHeight > 16_000_000
    )
        return null
    return BitmapFactory.decodeByteArray(raw, 0, raw.size)?.also { bitmapCache.put(data, it) }
}

internal class OverviewLayer(val data: JSONObject) {
    val width = data.f("width", 1f)
    val height = data.f("height", 1f)
    val nodes = data.optJSONArray("nodes").items()
    val hits = data.optJSONArray("hits").items()
    private val regions =
        hits.map { hit ->
            try {
                val path = hit.optString("path")
                val transform = hit.optJSONArray("matrix")
                if (path.isBlank() || transform == null) null
                else {
                    val input = PathParser.createPathFromPathData(path)
                    val shape = Path()
                    Paint()
                        .apply {
                            style = Paint.Style.STROKE
                            strokeWidth = hit.f("stroke", 40f)
                        }
                        .getFillPath(input, shape)
                    shape.transform(
                        Matrix().apply {
                            setValues(FloatArray(9) { transform.optDouble(it).toFloat() })
                        }
                    )
                    shape.transform(Matrix().apply { setScale(4f, 4f) })
                    Region().apply { setPath(shape, Region(-10000, -10000, 1000000, 1000000)) }
                }
            } catch (_: Exception) {
                null
            }
        }

    fun contains(index: Int, x: Float, y: Float): Boolean =
        regions[index]?.contains((x * 4).toInt(), (y * 4).toInt())
            ?: hits[index].rect().contains(x, y)

    val images =
        nodes
            .filter { it.optString("kind") in listOf("image", "backgroundImage") }
            .associate { it.optString("data") to bitmap(it.optString("data")) }
}

/**
 * One native drawing surface with native virtual buttons for TalkBack and keyboard. The parent
 * Android scroll views provide scrolling, flinging and system accessibility.
 */
internal class OverviewSurface(
    context: android.content.Context,
    val activate: (String, Boolean) -> Unit,
) : View(context) {
    private var layer: OverviewLayer? = null
    private var sceneWidth = 1f
    private var sceneHeight = 1f
    private var background = Color.BLACK
    private var horizontal = false
    private var scale = 1f
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var draws = 0

    internal fun diagnostics() =
        JSONObject()
            .put("width", width)
            .put("height", height)
            .put("sceneWidth", sceneWidth)
            .put("sceneHeight", sceneHeight)
            .put("scale", scale)
            .put("nodes", layer?.nodes?.size ?: -1)
            .put("draws", draws)
            .put("attached", isAttachedToWindow)
            .put("visible", visibility)

    private val helper =
        object : ExploreByTouchHelper(this) {
            override fun getVirtualViewAt(x: Float, y: Float): Int = hit(x, y)

            override fun getVisibleVirtualViews(ids: MutableList<Int>) {
                layer?.hits?.indices?.forEach { ids.add(it) }
            }

            override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
                val target = layer?.hits?.getOrNull(id)
                node.contentDescription = target?.optString("label")?.ifBlank { "Open" } ?: "Open"
                node.className =
                    if (target?.optString("id").isNullOrBlank()) "android.widget.TextView"
                    else "android.widget.Button"
                node.isClickable = !target?.optString("id").isNullOrBlank()
                if (node.isClickable) node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                val r = target?.rect() ?: RectF(0f, 0f, 1f, 1f)
                node.setBoundsInParent(
                    Rect(
                        (r.left * scale).toInt(),
                        (r.top * scale).toInt(),
                        max((r.right * scale).toInt(), (r.left * scale).toInt() + 1),
                        max((r.bottom * scale).toInt(), (r.top * scale).toInt() + 1),
                    )
                )
            }

            override fun onPerformActionForVirtualView(
                id: Int,
                action: Int,
                args: Bundle?,
            ): Boolean {
                if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
                return click(id)
            }
        }

    init {
        isFocusable = true
        isClickable = true
        ViewCompat.setAccessibilityDelegate(this, helper)
    }

    fun applyScene(value: OverviewLayer, w: Float, h: Float, bg: Int, nav: Boolean = false) {
        layer = value
        sceneWidth = w
        sceneHeight = h
        background = bg
        horizontal = nav
        requestLayout()
        invalidate()
        helper.invalidateRoot()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        scale =
            if (horizontal) resources.displayMetrics.density
            else available / sceneWidth.coerceAtLeast(1f)
        setMeasuredDimension(
            if (horizontal) (sceneWidth * scale).roundToInt() else available,
            (sceneHeight * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private fun hit(x: Float, y: Float, actionsOnly: Boolean = false): Int =
        layer?.hits?.indices?.reversed()?.firstOrNull {
            (!actionsOnly || layer!!.hits[it].optString("id").isNotBlank()) &&
                layer!!.contains(it, x / scale, y / scale)
        } ?: ExploreByTouchHelper.INVALID_ID

    private fun click(id: Int): Boolean {
        val target = layer?.hits?.getOrNull(id) ?: return false
        if (target.optString("id").isBlank()) return false
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
        activate(target.optString("id"), target.optBoolean("stay"))
        return true
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        helper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        helper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        helper.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) < slop && abs(event.y - downY) < slop) {
                    performClick()
                    click(hit(event.x, event.y, true))
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(background)
        val data = layer ?: return
        canvas.save()
        canvas.scale(scale, scale)
        for (node in data.nodes) {
            val kind = node.optString("kind")
            if (kind == "clip") {
                canvas.save()
                canvas.clipPath(
                    Path().apply {
                        addRoundRect(
                            node.rect(),
                            node.f("radius"),
                            node.f("radius"),
                            Path.Direction.CW,
                        )
                    }
                )
                continue
            }
            if (kind == "unclip") {
                canvas.restore()
                continue
            }
            val r = node.rect()
            if (r.width() <= 0 || r.height() <= 0) continue
            paint.reset()
            paint.isAntiAlias = true
            paint.alpha = (255 * node.f("opacity", 1f)).roundToInt().coerceIn(0, 255)
            when (kind) {
                "box" -> drawBox(canvas, node, r)
                "text" -> {
                    val text = node.optString("text")
                    val family = if (node.optBoolean("mono")) "monospace" else "sans-serif"
                    val weight = node.optInt("weight", 400)
                    paint.typeface =
                        if (android.os.Build.VERSION.SDK_INT >= 28)
                            Typeface.create(
                                Typeface.create(family, Typeface.NORMAL),
                                weight,
                                node.optBoolean("italic"),
                            )
                        else
                            Typeface.create(
                                family,
                                if (weight >= 600) Typeface.BOLD else Typeface.NORMAL,
                            )
                    paint.textSize = node.f("font", 15f)
                    paint.color = node.argb("color")
                    paint.alpha =
                        (Color.alpha(paint.color) * node.f("opacity", 1f))
                            .roundToInt()
                            .coerceIn(0, 255)
                    val fm = paint.fontMetrics
                    val baseline = r.top + (r.height() - (fm.descent - fm.ascent)) / 2 - fm.ascent
                    val spacing = node.f("spacing")
                    if (abs(spacing) > .3f) {
                        val glyphs =
                            text.codePoints().toArray().map { String(Character.toChars(it)) }
                        val widths = glyphs.map { paint.measureText(it) }
                        val total = widths.sum()
                        val ratio =
                            if (total > 0)
                                ((r.width() - spacing * glyphs.size) / total).coerceIn(.5f, 2f)
                            else 1f
                        paint.textScaleX = ratio
                        var x = r.left
                        for ((index, glyph) in glyphs.withIndex()) {
                            canvas.drawText(glyph, x, baseline, paint)
                            x += widths[index] * ratio + spacing
                        }
                    } else {
                        val measured = paint.measureText(text)
                        if (measured > 0)
                            paint.textScaleX = (r.width() / measured).coerceIn(.5f, 2f)
                        canvas.drawText(text, r.left, baseline, paint)
                    }
                }
                "image",
                "backgroundImage" ->
                    data.images[node.optString("data")]?.let { image ->
                        imagePaint.alpha =
                            (255 * node.f("opacity", 1f)).roundToInt().coerceIn(0, 255)
                        canvas.save()
                        canvas.clipPath(
                            Path().apply {
                                addRoundRect(
                                    r,
                                    node.f("radius"),
                                    node.f("radius"),
                                    Path.Direction.CW,
                                )
                            }
                        )
                        if (kind == "image") canvas.drawBitmap(image, null, r, imagePaint)
                        else {
                            val size = node.optJSONArray("size")
                            val pos = node.optJSONArray("position")
                            val ratio =
                                if (node.optBoolean("cover"))
                                    max(r.width() / image.width, r.height() / image.height)
                                else 1f
                            val w =
                                if (node.optBoolean("cover")) image.width * ratio
                                else
                                    (size?.optDouble(0)?.toFloat() ?: 0f).takeIf { it > 0 }
                                        ?: r.width()
                            val h =
                                if (node.optBoolean("cover")) image.height * ratio
                                else
                                    (size?.optDouble(1)?.toFloat() ?: 0f).takeIf { it > 0 }
                                        ?: r.height()
                            val x =
                                if (node.optBoolean("cover")) r.left + (r.width() - w) / 2
                                else r.left + (pos?.optDouble(0)?.toFloat() ?: 0f)
                            val y =
                                if (node.optBoolean("cover")) r.top + (r.height() - h) / 2
                                else r.top + (pos?.optDouble(1)?.toFloat() ?: 0f)
                            canvas.drawBitmap(image, null, RectF(x, y, x + w, y + h), imagePaint)
                        }
                        canvas.restore()
                    }
            }
        }
        canvas.restore()
        draws++
        tag = "overview-rendered"
    }

    private fun drawBox(canvas: Canvas, node: JSONObject, r: RectF) {
        val alpha = node.f("opacity", 1f)
        val radius = node.f("radius")
        for (shadow in node.optJSONArray("shadows").items().filter { !it.optBoolean("inset") }) {
            val lengths = shadow.optJSONArray("lengths") ?: continue
            val dx = lengths.optDouble(0).toFloat()
            val dy = lengths.optDouble(1).toFloat()
            val blur = lengths.optDouble(2).toFloat()
            val spread = lengths.optDouble(3).toFloat()
            val outer = RectF(r).apply { inset(-spread, -spread) }
            // Composite outer shadows on their own layer. Clearing the card's
            // interior must never clear text or controls already on the page.
            val extent = blur + abs(dx) + abs(dy) + abs(spread) + 2
            canvas.saveLayer(RectF(r).apply { inset(-extent, -extent) }, null)
            paint.color = shadow.argb("color")
            paint.alpha = (Color.alpha(paint.color) * alpha).roundToInt().coerceIn(0, 255)
            if (blur > 0) paint.setShadowLayer(blur / 2, dx, dy, shadow.argb("color"))
            else {
                paint.color = shadow.argb("color")
                outer.offset(dx, dy)
            }
            canvas.drawRoundRect(outer, radius, radius, paint)
            paint.clearShadowLayer()
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawRoundRect(r, radius, radius, paint)
            paint.xfermode = null
            canvas.restore()
        }
        paint.color = node.argb("color")
        paint.alpha = (Color.alpha(paint.color) * alpha).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(r, radius, radius, paint)
        for (g in node.optJSONArray("gradients").items().reversed()) {
            val values = g.optJSONArray("colors") ?: continue
            if (values.length() < 2) continue
            val colors = IntArray(values.length()) { values.optLong(it).toInt() }
            val stops =
                g.optJSONArray("positions")?.let { positions ->
                    FloatArray(colors.size) {
                        positions
                            .optDouble(it, it.toDouble() / max(1, colors.size - 1))
                            .toFloat()
                            .coerceIn(0f, 1f)
                    }
                }
            val direction = g.optString("direction")
            paint.shader =
                if (g.optBoolean("radial")) {
                    val percentages =
                        Regex("(-?[\\d.]+)%")
                            .findAll(direction)
                            .map { it.groupValues[1].toFloat() / 100 }
                            .toList()
                    val centered = direction.contains(" at ")
                    val cx =
                        if (centered && percentages.size >= 2)
                            r.left + percentages[percentages.size - 2] * r.width()
                        else r.centerX()
                    val cy =
                        if (centered && percentages.size >= 2)
                            r.top + percentages.last() * r.height()
                        else r.centerY()
                    val explicit = Regex("^\\s*([\\d.]+)(px|%)\\s+([\\d.]+)(px|%)").find(direction)
                    val rx =
                        if (explicit != null)
                            explicit.groupValues[1].toFloat() *
                                (if (explicit.groupValues[2] == "%") r.width() / 100 else 1f)
                        else r.width() * .71f
                    val ry =
                        if (explicit != null)
                            explicit.groupValues[3].toFloat() *
                                (if (explicit.groupValues[4] == "%") r.height() / 100 else 1f)
                        else r.height() * .71f
                    RadialGradient(0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(
                            Matrix().apply {
                                setScale(rx.coerceAtLeast(1f), ry.coerceAtLeast(1f))
                                postTranslate(cx, cy)
                            }
                        )
                    }
                } else {
                    val degrees =
                        Regex("(-?[\\d.]+)deg").find(direction)?.groupValues?.get(1)?.toDouble()
                            ?: 180.0
                    val dx = sin(Math.toRadians(degrees)).toFloat()
                    val dy = -cos(Math.toRadians(degrees)).toFloat()
                    val length = (abs(r.width() * dx) + abs(r.height() * dy)) / 2
                    LinearGradient(
                        r.centerX() - dx * length,
                        r.centerY() - dy * length,
                        r.centerX() + dx * length,
                        r.centerY() + dy * length,
                        colors,
                        stops,
                        Shader.TileMode.CLAMP,
                    )
                }
            paint.alpha = (255 * alpha).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(r, radius, radius, paint)
            paint.shader = null
        }
        val borders = node.optJSONArray("borders").items()
        val border = node.f("borderWidth")
        val uniform =
            borders.size != 4 ||
                borders.all {
                    it.f("width") == borders[0].f("width") &&
                        it.argb("color") == borders[0].argb("color")
                }
        if (uniform && border > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = border
            paint.color = node.argb("border")
            paint.alpha = (Color.alpha(paint.color) * alpha).roundToInt().coerceIn(0, 255)
            val inset = RectF(r).apply { inset(border / 2, border / 2) }
            canvas.drawRoundRect(inset, radius, radius, paint)
        } else if (borders.size == 4)
            for ((index, edge) in borders.withIndex()) {
                val width = edge.f("width")
                if (width <= 0) continue
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = width
                paint.color = edge.argb("color")
                paint.alpha = (Color.alpha(paint.color) * alpha).roundToInt().coerceIn(0, 255)
                when (index) {
                    0 ->
                        canvas.drawLine(
                            r.left,
                            r.top + width / 2,
                            r.right,
                            r.top + width / 2,
                            paint,
                        )
                    1 ->
                        canvas.drawLine(
                            r.right - width / 2,
                            r.top,
                            r.right - width / 2,
                            r.bottom,
                            paint,
                        )
                    2 ->
                        canvas.drawLine(
                            r.left,
                            r.bottom - width / 2,
                            r.right,
                            r.bottom - width / 2,
                            paint,
                        )
                    3 ->
                        canvas.drawLine(
                            r.left + width / 2,
                            r.top,
                            r.left + width / 2,
                            r.bottom,
                            paint,
                        )
                }
            }
    }
}
