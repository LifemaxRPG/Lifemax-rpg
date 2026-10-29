package com.lifemax.rpg

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

/**
 * First integrated Native Nutrition parity screen. This is presentation only: all displayed
 * calories, logged foods, goals and water come from the existing live WebView. All persistent
 * mutations return through its canonical handlers. Advanced / unmigrated subflows open the original
 * Food UI without copying data.
 */
@CapacitorPlugin(name = "NativeNutrition")
class NativeNutritionPlugin : Plugin() {
    private var session = ""
    private var layer: ComposeView? = null
    private var chrome: LiveOverviewShell? = null
    private var snapshot by mutableStateOf<JSONObject?>(null)

    @PluginMethod
    fun capabilities(call: PluginCall) {
        call.resolve(JSObject().put("version", 1).put("authoritativeData", true))
    }

    @PluginMethod
    fun open(call: PluginCall) {
        val id = call.getString("session")
        val data = call.getObject("snapshot")
        if (
            id.isNullOrBlank() ||
                data == null ||
                data.optInt("version") != 1 ||
                data.toString().length > 250000 ||
                !data.has("totals") ||
                !data.has("goals")
        ) {
            call.reject("Invalid native Nutrition snapshot")
            return
        }
        activity.runOnUiThread {
            try {
                dismiss(false)
                session = id
                snapshot = JSONObject(data.toString())
                val view = ComposeView(activity)
                view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                view.setContent {
                    snapshot?.let { current ->
                        NativeNutritionScreen(current) { command ->
                            if (command == "close") dismiss(true)
                            else
                                notifyListeners(
                                    "action",
                                    JSObject().put("session", session).put("command", command),
                                )
                        }
                    }
                }
                layer = view
                activity
                    .findViewById<ViewGroup>(android.R.id.content)
                    .addView(view, FrameLayout.LayoutParams(-1, -1))
                showChrome(call.getObject("chromeScene"))
                call.resolve()
            } catch (e: Exception) {
                dismiss(false)
                call.reject("Native Nutrition unavailable", e)
            }
        }
    }

    @PluginMethod
    fun update(call: PluginCall) {
        val data = call.getObject("snapshot")
        activity.runOnUiThread {
            try {
                if (
                    call.getString("session") == session &&
                        layer != null &&
                        data != null &&
                        data.optInt("version") == 1 &&
                        data.toString().length <= 250000 &&
                        data.has("totals") &&
                        data.has("goals")
                ) {
                    snapshot = JSONObject(data.toString())
                    showChrome(call.getObject("chromeScene"))
                }
                call.resolve()
            } catch (e: Exception) {
                call.reject("Native Nutrition refresh failed", e)
            }
        }
    }

    @PluginMethod
    fun close(call: PluginCall) {
        activity.runOnUiThread {
            if (call.getString("session") == session) dismiss(true)
            call.resolve()
        }
    }

    private fun showChrome(scene: JSONObject?) {
        requireNotNull(scene) { "Missing shared Overview chrome" }
        if (chrome == null)
            chrome =
                LiveOverviewShell(activity, bridge.webView, chromeOnly = true) { id ->
                    notifyListeners("nav-action", JSObject().put("id", id))
                }

        chrome?.show(scene)
    }

    private fun dismiss(notify: Boolean) {
        chrome?.close()
        chrome = null
        val old = session
        layer?.let { (it.parent as? ViewGroup)?.removeView(it) }
        layer = null
        snapshot = null
        session = ""
        if (notify && old.isNotBlank()) notifyListeners("closed", JSObject().put("session", old))
    }

    override fun handleOnDestroy() {
        dismiss(false)
        super.handleOnDestroy()
    }
}

private val dark = Color(0xFF0B0E15)
private val surface = Color(0xFF151B27)
private val rule = Color(0xFF293246)
private val dim = Color(0xFF93A1B8)
private val protein = Color(0xFFFF7A8A)
private val carbs = Color(0xFFFFC15E)
private val fat = Color(0xFF4FD6C0)
private val accent = Color(0xFF849CFF)

@Composable
private fun NativeNutritionScreen(data: JSONObject, act: (String) -> Unit) {
    BackHandler { act("close") }
    val light = data.optBoolean("light")
    val bg = if (light) Color(0xFFF6F7FA) else dark
    val panel = if (light) Color.White else surface
    val fg = if (light) Color(0xFF182132) else Color(0xFFF5F7FC)
    val muted = if (light) Color(0xFF64748B) else dim
    val border = if (light) Color(0xFFDDE3ED) else rule
    val goals = data.optJSONObject("goals") ?: JSONObject()
    val totals = data.optJSONObject("totals") ?: JSONObject()
    val items = data.optJSONArray("entries") ?: JSONArray()
    val logged = totals.optDouble("kcal").roundToInt()
    val goal = goals.optDouble("cal").roundToInt()
    val left = goal - logged
    Column(
        Modifier.fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(64.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            NutButton("Meals", fg, panel, accent) {}
            NutButton("Recipes ↗", muted, panel, border) { act("recipes") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NutButton("‹", fg, panel, border) { act("previous") }
            Text(
                data.optString("dayLabel", data.optString("day")),
                color = fg,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            NutButton("›", fg, panel, border) { act("next") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            NutButton("Plan ↗", muted, panel, border) { act("plan") }
            NutButton("Log", fg, panel, accent) {}
            NutButton("Shopping ↗", muted, panel, border) { act("shop") }
        }
        Column(
            Modifier.fillMaxWidth()
                .background(panel, RoundedCornerShape(26.dp))
                .border(1.dp, border, RoundedCornerShape(26.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().widthIn(max = 320.dp).aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val factor = size.minDimension / 320f
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val progress = if (goal > 0) (logged.toFloat() / goal).coerceIn(0f, 1f) else 0f
                    for (i in 0 until 72) {
                        val a = i.toDouble() / 72 * Math.PI * 2 - Math.PI / 2
                        val r0 = (if (i % 6 == 0) 137f else 141f) * factor
                        val r1 = 151f * factor
                        drawLine(
                            if (i.toFloat() / 72 < progress) accent else border,
                            Offset(cx + cos(a).toFloat() * r0, cy + sin(a).toFloat() * r0),
                            Offset(cx + cos(a).toFloat() * r1, cy + sin(a).toFloat() * r1),
                            (if (i % 6 == 0) 2.2f else 1.3f) * factor,
                            cap = StrokeCap.Round,
                        )
                    }
                    val rings =
                        listOf(
                            Triple(
                                126f,
                                Pair(totals.optDouble("p"), goals.optDouble("p")),
                                protein,
                            ),
                            Triple(101f, Pair(totals.optDouble("c"), goals.optDouble("c")), carbs),
                            Triple(76f, Pair(totals.optDouble("f"), goals.optDouble("f")), fat),
                        )
                    for ((radius, values, color) in rings) {
                        val r = radius * factor
                        val at = Offset(cx - r, cy - r)
                        val sz = Size(r * 2, r * 2)
                        val st = Stroke(width = 16f * factor, cap = StrokeCap.Round)
                        drawArc(border.copy(alpha = .5f), -90f, 360f, false, at, sz, style = st)
                        val f =
                            if (values.second > 0)
                                (values.first / values.second).coerceIn(0.0, 1.0).toFloat()
                            else 0f
                        if (f > 0) drawArc(color, -90f, 360f * f, false, at, sz, style = st)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        kotlin.math.abs(left).toString(),
                        color = fg,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (left >= 0) "KCAL LEFT" else "KCAL OVER",
                        color = muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("$logged / $goal kcal", color = muted, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                NutMacro(
                    "PROTEIN",
                    totals.optDouble("p"),
                    goals.optDouble("p"),
                    protein,
                    panel,
                    fg,
                    muted,
                    border,
                    Modifier.weight(1f),
                )
                NutMacro(
                    "CARBS",
                    totals.optDouble("c"),
                    goals.optDouble("c"),
                    carbs,
                    panel,
                    fg,
                    muted,
                    border,
                    Modifier.weight(1f),
                )
                NutMacro(
                    "FAT",
                    totals.optDouble("f"),
                    goals.optDouble("f"),
                    fat,
                    panel,
                    fg,
                    muted,
                    border,
                    Modifier.weight(1f),
                )
            }
            NutButton("Edit targets ↗", fg, panel, border) { act("goals") }
        }
        Row(
            Modifier.fillMaxWidth()
                .background(panel, RoundedCornerShape(20.dp))
                .border(1.dp, border, RoundedCornerShape(20.dp))
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Water", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    data.optInt("waterMl").toString() + " / " + goals.optInt("water") + " ml",
                    color = muted,
                    fontSize = 12.sp,
                )
            }
            NutButton("+250 ml", fg, panel, accent) { act("water") }
        }
        for (slot in listOf("breakfast", "lunch", "dinner", "snacks")) {
            val matches =
                (0 until items.length())
                    .mapNotNull { items.optJSONObject(it) }
                    .filter { it.optString("slot").equals(slot, ignoreCase = true) }
            Column(
                Modifier.fillMaxWidth()
                    .background(panel, RoundedCornerShape(18.dp))
                    .border(1.dp, border, RoundedCornerShape(18.dp))
                    .clickable { act("add") }
                    .padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        slot.replaceFirstChar { it.uppercase() },
                        color = fg,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        matches.sumOf { it.optDouble("kcal") }.roundToInt().toString() + " kcal",
                        color = muted,
                        fontSize = 12.sp,
                    )
                }
                if (matches.isEmpty())
                    Text("Nothing logged · Tap to add", color = muted, fontSize = 12.sp)
                for (e in matches) Row(Modifier.fillMaxWidth()) {
                    Text(
                        e.optString("name", "Food"),
                        color = muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        e.optDouble("kcal").roundToInt().toString() + " kcal",
                        color = fg,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        NutButton("+ Add food ↗", fg, panel, accent) { act("add") }
        Text(
            "Native beta reads your actual food log. Editing, Smart Diet and Recipes currently open the original Food screen.",
            color = muted,
            fontSize = 11.sp,
        )
        NutButton("Open original Food screen", fg, panel, border) { act("web") }
    }
}

@Composable
private fun NutMacro(
    label: String,
    value: Double,
    goal: Double,
    tint: Color,
    panel: Color,
    fg: Color,
    muted: Color,
    border: Color,
    modifier: Modifier,
) {
    Column(
        modifier
            .background(panel, RoundedCornerShape(16.dp))
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        Text(label, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            value.roundToInt().toString() + "g",
            color = fg,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("of " + goal.roundToInt() + "g", color = muted, fontSize = 10.sp)
    }
}

@Composable
private fun NutButton(label: String, fg: Color, panel: Color, border: Color, click: () -> Unit) {
    Box(
        Modifier.background(panel, RoundedCornerShape(13.dp))
            .border(1.dp, border, RoundedCornerShape(13.dp))
            .clickable(onClick = click)
            .padding(horizontal = 13.dp, vertical = 10.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
