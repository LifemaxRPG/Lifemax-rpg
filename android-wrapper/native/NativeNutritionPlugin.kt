package com.lifemax.rpg

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Native Jetpack Compose Nutrition preview (`Plan`, `Log`, and `Shopping`) with
 * Card-Anchored AAA Plasma Ribbon & Circumferential Ring-Torch animation.
 *
 * All displayed calories, logged foods, Smart Diet meals, shopping items, goals, and water
 * come from the authoritative live WebView (`50-native-nutrition.js`).
 * All persistent mutations route back through canonical handlers (`fv2Log`, `fv2AddWater`,
 * `fv2CopySlot`, `sdLogMeal`, `setMealsDay`, `fv2Open`, `sdOpenSwap`).
 */
@CapacitorPlugin(name = "NativeNutrition")
class NativeNutritionPlugin : Plugin() {
    private var session = ""
    private var layer: ComposeView? = null
    private var chrome: LiveOverviewShell? = null
    private var snapshot by mutableStateOf<JSONObject?>(null)
    private var topInsetDp by mutableFloatStateOf(64f)
    private var bottomInsetDp by mutableFloatStateOf(72f)

    @PluginMethod
    fun capabilities(call: PluginCall) {
        call.resolve(JSObject().put("version", 1).put("authoritativeData", true))
    }

    @PluginMethod
    fun open(call: PluginCall) {
        val id = call.getString("session")
        val data = call.getObject("snapshot")
        if (id.isNullOrBlank() || data == null || data.optInt("version") != 1 ||
            data.toString().length > 250000 || !data.has("totals") || !data.has("goals")
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
                view.tag = "native-nutrition-screen"
                view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                view.setContent {
                    snapshot?.let { current ->
                        NativeNutritionScreen(current, topInsetDp, bottomInsetDp) { command, payload ->
                            if (command == "close") {
                                dismiss(true)
                            } else {
                                val evt = JSObject().put("session", session).put("command", command)
                                payload.forEach { (k, v) -> evt.put(k, v) }
                                notifyListeners("action", evt)
                            }
                        }
                    }
                }
                layer = view
                activity.findViewById<ViewGroup>(android.R.id.content)
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
                if (call.getString("session") == session && layer != null &&
                    data != null && data.optInt("version") == 1 && data.toString().length <= 250000 &&
                    data.has("totals") && data.has("goals")
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
            if (call.getString("session") == session) {
                dismiss(false)
            }
            call.resolve()
        }
    }

    override fun handleOnDestroy() {
        dismiss(false)
        super.handleOnDestroy()
    }

    private fun showChrome(scene: JSONObject?) {
        requireNotNull(scene) { "Missing shared Overview chrome" }
        if (chrome == null) {
            chrome = LiveOverviewShell(activity, bridge.webView, chromeOnly = true) { id ->
                notifyListeners("nav-action", JSObject().put("id", id))
            }
        }
        chrome?.show(scene)
        chrome?.contentInsetsDp()?.let { (top, bottom) ->
            topInsetDp = top
            bottomInsetDp = bottom
        }
    }

    private fun dismiss(notify: Boolean) {
        chrome?.close()
        chrome = null
        val id = session
        session = ""
        snapshot = null
        layer?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        layer = null
        if (notify && id.isNotEmpty()) {
            notifyListeners("closed", JSObject().put("session", id))
        }
    }
}

private val BgObsidian = Color(0xFF090B10)
private val CardTop = Color(0xFF141822)
private val CardBottom = Color(0xFF10131B)
private val SunkenTile = Color(0xFF0C0F17)
private val BorderSubtle = Color(0x14FFFFFF)
private val TextPrimary = Color(0xFFF3F7FD)
private val TextMuted = Color(0xFF8B95A7)
private val TextSubtle = Color(0xFF7C8799)
private val PillSelected = Color(0xFF252B3B)
private val BlueAccent = Color(0xFF6E8EFB)
private val ProteinRose = Color(0xFFFF7A8A)
private val CarbsAmber = Color(0xFFFFC15E)
private val FatTeal = Color(0xFF4FD6C0)
private val BreakfastAmber = Color(0xFFF5B851)
private val LunchGreen = Color(0xFF4ADE80)
private val DinnerIndigo = Color(0xFF818CF8)
private val SnacksCoral = Color(0xFFFF7A8A)

private fun fmtNum(n: Int): String = NumberFormat.getNumberInstance(Locale.US).format(n)

@Composable
private fun NativeNutritionScreen(
    data: JSONObject,
    topInsetDp: Float,
    bottomInsetDp: Float,
    dispatch: (command: String, payload: Map<String, Any>) -> Unit
) {
    BackHandler { dispatch("close", emptyMap()) }

    val light = data.optBoolean("light")
    val bg = if (light) Color(0xFFEEF3FA) else BgObsidian
    val fg = if (light) Color(0xFF0F172A) else TextPrimary
    val muted = if (light) Color(0xFF475569) else TextMuted
    val subtle = if (light) Color(0xFF64748B) else TextSubtle

    var activePane by remember(data.optString("pane")) {
        mutableStateOf(data.optString("pane", "plan").ifBlank { "plan" })
    }
    var showUpNextIngredients by remember { mutableStateOf(false) }
    var showNutritionDetails by remember { mutableStateOf(false) }
    var selectedRingMacro by remember { mutableStateOf<String?>(null) }

    val goals = data.optJSONObject("goals") ?: JSONObject()
    val totals = data.optJSONObject("totals") ?: JSONObject()
    val entries = data.optJSONArray("entries") ?: JSONArray()
    val week = data.optJSONArray("week") ?: JSONArray()
    val recents = data.optJSONArray("recents") ?: JSONArray()
    val copyYesterday = data.optJSONObject("copyYesterday") ?: JSONObject()
    val planObj = data.optJSONObject("plan")
    val shopObj = data.optJSONObject("shop")

    val calLogged = totals.optDouble("kcal", 0.0).roundToInt()
    val calGoal = goals.optDouble("cal", 2500.0).roundToInt().coerceAtLeast(1)
    val proLogged = totals.optDouble("p", 0.0).toFloat()
    val proGoal = goals.optDouble("p", 150.0).toFloat().coerceAtLeast(1f)
    val carbLogged = totals.optDouble("c", 0.0).toFloat()
    val carbGoal = goals.optDouble("c", 200.0).toFloat().coerceAtLeast(1f)
    val fatLogged = totals.optDouble("f", 0.0).toFloat()
    val fatGoal = goals.optDouble("f", 67.0).toFloat().coerceAtLeast(1f)

    // AAA Plasma Flight & Circumferential Ring Torch Animation State
    val flightProgress = remember { Animatable(0f) }
    var flightActive by remember { mutableStateOf(false) }
    var prevRatios by remember {
        mutableStateOf(
            Triple(
                (proLogged / proGoal).coerceIn(0f, 1f),
                (carbLogged / carbGoal).coerceIn(0f, 1f),
                (fatLogged / fatGoal).coerceIn(0f, 1f)
            )
        )
    }
    var targetRatios by remember {
        mutableStateOf(
            Triple(
                (proLogged / proGoal).coerceIn(0f, 1f),
                (carbLogged / carbGoal).coerceIn(0f, 1f),
                (fatLogged / fatGoal).coerceIn(0f, 1f)
            )
        )
    }
    val scope = rememberCoroutineScope()

    // Trigger AAA Plasma Flight whenever logged calories increase
    var lastCalLogged by remember { mutableIntStateOf(calLogged) }
    LaunchedEffect(calLogged, proLogged, carbLogged, fatLogged) {
        val nextTriple = Triple(
            (proLogged / proGoal).coerceIn(0f, 1f),
            (carbLogged / carbGoal).coerceIn(0f, 1f),
            (fatLogged / fatGoal).coerceIn(0f, 1f)
        )
        if (calLogged > lastCalLogged) {
            activePane = "log"
            targetRatios = nextTriple
            flightActive = true
            flightProgress.snapTo(0f)
            flightProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1620, easing = LinearEasing)
            )
            flightActive = false
            prevRatios = nextTriple
        } else {
            prevRatios = nextTriple
            targetRatios = nextTriple
        }
        lastCalLogged = calLogged
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = bottomInsetDp.dp + 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(topInsetDp.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderButton(label = "Meals", width = null) {}
                HeaderButton(label = "Recipes", width = null) {
                    dispatch("recipes", emptyMap())
                }
            }
            // 1. Shared Date Header (TODAY / Thursday, Oct 29 | Targets | ‹ | ›)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = data.optString("eyebrow", "TODAY").uppercase(Locale.US),
                        color = subtle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = data.optString("dayLabel", data.optString("day", "Today")),
                        color = fg,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderButton(label = "Targets", width = null) {
                        dispatch("goals", emptyMap())
                    }
                    HeaderButton(label = "‹", width = 42.dp) {
                        dispatch("previous", emptyMap())
                    }
                    HeaderButton(label = "›", width = 42.dp) {
                        dispatch("next", emptyMap())
                    }
                }
            }

            // 2. 7-Day Monday-Sunday Week Strip (M 26 .. T 29 .. S 1)
            if (week.length() > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 0 until week.length()) {
                        val wd = week.optJSONObject(i) ?: continue
                        val isSelected = wd.optBoolean("selected")
                        val dName = wd.optString("w", "M")
                        val dNum = wd.optInt("dNum", i + 1)
                        val dayKey = wd.optString("day")
                        val frac = wd.optDouble("frac", 0.0).toFloat().coerceIn(0f, 1f)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) PillSelected else Color.Transparent)
                                .clickable {
                                    if (dayKey.isNotEmpty()) {
                                        dispatch("selectDay", mapOf("day" to dayKey))
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = dName,
                                color = if (isSelected) Color(0xFFD6DFEE) else subtle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            if (isSelected) {
                                Column(
                                    modifier = Modifier.size(38.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(BlueAccent)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = dNum.toString(),
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.size(38.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val r = size.minDimension / 2f - 3.dp.toPx()
                                        val c = Offset(size.width / 2f, size.height / 2f)
                                        drawCircle(
                                            color = Color(0xFF1A1F2D),
                                            radius = r,
                                            center = c,
                                            style = Stroke(width = 2.6.dp.toPx())
                                        )
                                        if (frac > 0.01f) {
                                            drawArc(
                                                color = BlueAccent,
                                                startAngle = -90f,
                                                sweepAngle = 360f * frac,
                                                useCenter = false,
                                                topLeft = Offset(c.x - r, c.y - r),
                                                size = Size(r * 2f, r * 2f),
                                                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                        }
                                        // 12 o'clock top blue dot matching original screenshot
                                        drawCircle(
                                            color = BlueAccent,
                                            radius = 2.1.dp.toPx(),
                                            center = Offset(c.x, c.y - r)
                                        )
                                    }
                                    Text(
                                        text = dNum.toString(),
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. 3-Way Segmented Switcher (Plan | Log | Shopping)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF12151F))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "plan" to "Plan",
                    "log" to "Log",
                    "shop" to "Shopping"
                ).forEach { (id, label) ->
                    val isOn = activePane == id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (isOn) PillSelected else Color.Transparent)
                            .clickable {
                                activePane = id
                                dispatch("pane", mapOf("pane" to id))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(
                                text = when (id) {
                                    "plan" -> "📅"
                                    "log" -> "☰"
                                    else -> "🛍"
                                },
                                color = if (isOn) BlueAccent else muted,
                                fontSize = 13.sp
                            )
                            Text(
                                text = label,
                                color = if (isOn) Color.White else muted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4A. PLAN PANE (1:1 reproduction of Screenshot_20261029_064132_LifeMax.jpg)
            if (activePane == "plan") {
                val workoutHeading = planObj?.optString("workoutHeading", "TRAINING C · TRAINING DAY")
                    ?: "TRAINING C · TRAINING DAY"
                val planTarget = planObj?.optInt("target", calGoal) ?: calGoal
                val planEaten = planObj?.optInt("eaten", calLogged) ?: calLogged
                val planLeft = max(0, planTarget - planEaten)
                val eatenP = planObj?.optInt("eatenP", proLogged.roundToInt()) ?: proLogged.roundToInt()
                val planP = planObj?.optInt("planP", proGoal.roundToInt()) ?: proGoal.roundToInt()
                val planItems = planObj?.optJSONArray("items") ?: JSONArray()
                val nextIdx = planObj?.optInt("nextIndex", 0) ?: 0
                val nextMeal = if (nextIdx >= 0 && nextIdx < planItems.length()) {
                    planItems.optJSONObject(nextIdx)
                } else null

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Training & Calorie Progress Header
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = workoutHeading,
                                color = subtle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.6.sp
                            )
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = fmtNum(planLeft),
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "kcal to go",
                                    color = subtle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress Track
                        val pct = (planEaten.toFloat() / max(1, planTarget)).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF171B26))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(pct)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(BlueAccent, FatTeal)
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row {
                                Text(
                                    text = fmtNum(planEaten),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = " of ${fmtNum(planTarget)} kcal",
                                    color = subtle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row {
                                Text(
                                    text = "Protein ",
                                    color = subtle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$eatenP",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = " / $planP g",
                                    color = subtle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // UP NEXT Hero Meal Block
                    if (nextMeal != null) {
                        val slotName = nextMeal.optString("slotName", "Breakfast").uppercase(Locale.US)
                        val slotWhen = nextMeal.optString("slotWhen", "Morning").uppercase(Locale.US)
                        val activeMin = nextMeal.optInt("activeMin", 3)
                        val tagsLabel = nextMeal.optString("tagsLabel", "No cooking")
                        val mealName = nextMeal.optString("name", "Cottage Cheese & Fruit Bowl")
                        val mKcal = nextMeal.optInt("kcal", 573)
                        val mP = nextMeal.optInt("p", 39)
                        val mC = nextMeal.optInt("c", 69)
                        val mF = nextMeal.optInt("f", 16)
                        val topIngs = nextMeal.optString(
                            "topIngs",
                            "Cottage cheese · Apple · Rolled oats · Almonds"
                        )
                        val mealIndex = nextMeal.optInt("index", 0)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Eyebrow Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GlowingDot(color = BreakfastAmber)
                                    Text(
                                        text = "UP NEXT · $slotName · $slotWhen",
                                        color = muted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.4.sp
                                    )
                                }
                                Text(
                                    text = "$activeMin min · $tagsLabel",
                                    color = muted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Giant Meal Title
                            Text(
                                text = mealName,
                                color = Color.White,
                                fontSize = 30.sp,
                                lineHeight = 34.sp,
                                fontWeight = FontWeight.Black
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // 4-Column Hairline Macro Table
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0x14FFFFFF))
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                            ) {
                                // KCAL
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 14.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = mKcal.toString(),
                                        color = Color.White,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "KCAL",
                                        color = subtle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.3.sp
                                    )
                                }
                                VerticalHairline()
                                // PROTEIN
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 14.dp, horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = "${mP}g",
                                        color = ProteinRose,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "PROTEIN",
                                        color = subtle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.3.sp
                                    )
                                }
                                VerticalHairline()
                                // CARBS
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 14.dp, horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = "${mC}g",
                                        color = CarbsAmber,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "CARBS",
                                        color = subtle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.3.sp
                                    )
                                }
                                VerticalHairline()
                                // FAT
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 14.dp, horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = "${mF}g",
                                        color = FatTeal,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "FAT",
                                        color = subtle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.3.sp
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0x14FFFFFF))
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Dot-separated Ingredients Line
                            Text(
                                text = topIngs,
                                color = Color(0xFF94A3B8),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Full-width White Pill CTA: Log this meal
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .clickable {
                                        dispatch("sdLog", mapOf("index" to mealIndex))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Log this meal",
                                    color = BgObsidian,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 3-Column Secondary Action Row: Ingredients | Swap | Skip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showUpNextIngredients = !showUpNextIngredients }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (showUpNextIngredients) "Hide details" else "Ingredients",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                VerticalHairline()
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            dispatch("sdSwap", mapOf("index" to mealIndex))
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Swap",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                VerticalHairline()
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            dispatch("sdSkip", mapOf("index" to mealIndex))
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Skip",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (showUpNextIngredients) {
                                val ingsArr = nextMeal.optJSONArray("ings") ?: JSONArray()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFF11141D))
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (k in 0 until ingsArr.length()) {
                                        val ig = ingsArr.optJSONObject(k) ?: continue
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = ig.optString("name"),
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${ig.optInt("grams")} g",
                                                color = muted,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Coaching Tip Card
                            val coachText = planObj?.optString(
                                "coachText",
                                "Start with $mealName. Carbs up front fuel today's session."
                            ) ?: "Start with $mealName. Carbs up front fuel today's session."
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF11141D))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "✦",
                                    color = subtle,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = coachText,
                                    color = muted,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }

            // 4B. LOG PANE (1:1 reproduction of Screenshot_20261029_062450_LifeMax.jpg + AAA Ring Torch)
            if (activePane == "log") {
                val left = calGoal - calLogged

                // Hero Dial Card with Card-Anchored AAA Plasma Ribbon & Ring Torch Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(26.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 320x320 Concentric 3-Ring Dial
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 310.dp)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            val pAnim = flightProgress.value
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val factor = size.minDimension / 320f
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val calRatio = (calLogged.toFloat() / calGoal).coerceIn(0f, 1f)

                                // 72 Outer Calorie Ticks
                                for (i in 0 until 72) {
                                    val a = i.toDouble() / 72.0 * PI * 2.0 - PI / 2.0
                                    val isMajor = i % 6 == 0
                                    val r0 = (if (isMajor) 137f else 141f) * factor
                                    val r1 = 151f * factor
                                    val tickLit = (i.toFloat() / 72f) < calRatio
                                    drawLine(
                                        color = if (tickLit) BlueAccent else Color(0xFF232838),
                                        start = Offset(
                                            cx + cos(a).toFloat() * r0,
                                            cy + sin(a).toFloat() * r0
                                        ),
                                        end = Offset(
                                            cx + cos(a).toFloat() * r1,
                                            cy + sin(a).toFloat() * r1
                                        ),
                                        strokeWidth = (if (isMajor) 2.2f else 1.4f) * factor,
                                        cap = StrokeCap.Round
                                    )
                                }

                                // 3 Concentric Macro Rings (r=126 Protein, r=101 Carbs, r=76 Fat)
                                val ringConfigs = listOf(
                                    Triple(126f, prevRatios.first to targetRatios.first, ProteinRose),
                                    Triple(101f, prevRatios.second to targetRatios.second, CarbsAmber),
                                    Triple(76f, prevRatios.third to targetRatios.third, FatTeal)
                                )
                                val strokeW = 16f * factor

                                ringConfigs.forEachIndexed { idx, (radBase, ratioPair, ringColor) ->
                                    val r = radBase * factor
                                    val topLeft = Offset(cx - r, cy - r)
                                    val arcSize = Size(r * 2f, r * 2f)

                                    // Track
                                    drawArc(
                                        color = Color(0xFF1B2030),
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                    )

                                    // Animated sweep during AAA torch weld
                                    val (prevR, nextR) = ratioPair
                                    val torchLocal = if (flightActive) {
                                        ((pAnim - (0.36f + idx * 0.04f)) / 0.42f).coerceIn(0f, 1f)
                                    } else 1f
                                    val easedTorch = 1f - (1f - torchLocal).pow(3f)
                                    val curRatio = if (flightActive) {
                                        prevR + (nextR - prevR) * easedTorch
                                    } else nextR

                                    if (curRatio > 0.002f) {
                                        drawArc(
                                            color = ringColor,
                                            startAngle = -90f,
                                            sweepAngle = 360f * curRatio,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                        )
                                    }

                                    // AAA Plasma Ribbon & Circumferential Torch Head
                                    if (flightActive) {
                                        val ribbonPhase = ((pAnim - idx * 0.04f) / 0.36f).coerceIn(0f, 1f)
                                        val tipAngleRad = (-PI / 2.0) + (curRatio * 2.0 * PI)
                                        val tipX = cx + cos(tipAngleRad).toFloat() * r
                                        val tipY = cy + sin(tipAngleRad).toFloat() * r

                                        if (ribbonPhase > 0f && ribbonPhase < 1f) {
                                            val startX = cx + (idx - 1) * 95f * factor
                                            val startY = size.height + 36f * factor
                                            val easeR = 1f - (1f - ribbonPhase).pow(2.6f)
                                            val headX = startX + (tipX - startX) * easeR
                                            val headY = startY + (tipY - startY) * easeR

                                            drawCircle(
                                                color = ringColor.copy(alpha = 0.42f),
                                                radius = 18f * factor,
                                                center = Offset(headX, headY)
                                            )
                                            drawCircle(
                                                color = Color.White,
                                                radius = 5.5f * factor,
                                                center = Offset(headX, headY)
                                            )
                                        }

                                        if (torchLocal > 0f && torchLocal < 1f) {
                                            drawCircle(
                                                color = ringColor.copy(alpha = 0.55f),
                                                radius = 22f * factor,
                                                center = Offset(tipX, tipY)
                                            )
                                            drawCircle(
                                                color = Color.White,
                                                radius = 7f * factor,
                                                center = Offset(tipX, tipY)
                                            )
                                        }
                                    }
                                }
                            }

                            // Center Readout
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    dispatch("goals", emptyMap())
                                }
                            ) {
                                Text(
                                    text = fmtNum(abs(left)),
                                    color = Color.White,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = if (left >= 0) "KCAL LEFT" else "KCAL OVER",
                                    color = subtle,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.4.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${fmtNum(calLogged)} of ${fmtNum(calGoal)}",
                                    color = muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // 3 Macro Progress Tiles (PROTEIN | CARBS | FAT) with Glowing Neon Dots
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MacroTileCard(
                                label = "PROTEIN",
                                dotColor = ProteinRose,
                                current = proLogged.roundToInt(),
                                goal = proGoal.roundToInt(),
                                modifier = Modifier.weight(1f)
                            )
                            MacroTileCard(
                                label = "CARBS",
                                dotColor = CarbsAmber,
                                current = carbLogged.roundToInt(),
                                goal = carbGoal.roundToInt(),
                                modifier = Modifier.weight(1f)
                            )
                            MacroTileCard(
                                label = "FAT",
                                dotColor = FatTeal,
                                current = fatLogged.roundToInt(),
                                goal = fatGoal.roundToInt(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // QUICK ADD Section (one tap, <currentSlot>)
                if (recents.length() > 0) {
                    val slotLabel = data.optString("currentSlot", "breakfast").lowercase(Locale.US)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "QUICK ADD",
                            color = subtle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "one tap, $slotLabel",
                            color = subtle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (i in 0 until recents.length()) {
                            val rc = recents.optJSONObject(i) ?: continue
                            val rName = rc.optString("name", "Food")
                            val rKcal = rc.optInt("kcal", 0)
                            val rIdx = rc.optInt("index", i)

                            Column(
                                modifier = Modifier
                                    .width(210.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                                    .clickable {
                                        dispatch("quickAdd", mapOf("index" to rIdx))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = rName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$rKcal kcal",
                                    color = muted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // WATER Card matching Screenshot_20261029_062450_LifeMax.jpg
                val waterMl = data.optInt("waterMl", 0)
                val waterGoal = goals.optInt("water", 2000).coerceAtLeast(1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Outlined water droplet badge (#132238)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF132238)),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(22.dp)) {
                                val w = size.width
                                val h = size.height
                                val p = Path().apply {
                                    moveTo(w * 0.5f, h * 0.12f)
                                    cubicTo(
                                        w * 0.78f, h * 0.42f,
                                        w * 0.82f, h * 0.62f,
                                        w * 0.82f, h * 0.68f
                                    )
                                    cubicTo(
                                        w * 0.82f, h * 0.86f,
                                        w * 0.68f, h * 0.94f,
                                        w * 0.5f, h * 0.94f
                                    )
                                    cubicTo(
                                        w * 0.32f, h * 0.94f,
                                        w * 0.18f, h * 0.86f,
                                        w * 0.18f, h * 0.68f
                                    )
                                    cubicTo(
                                        w * 0.18f, h * 0.62f,
                                        w * 0.22f, h * 0.42f,
                                        w * 0.5f, h * 0.12f
                                    )
                                    close()
                                }
                                drawPath(
                                    path = p,
                                    color = Color(0xFF4DA6FF),
                                    style = Stroke(
                                        width = 2.1.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Water",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${fmtNum(waterMl)} / ${fmtNum(waterGoal)} ml",
                                color = muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WaterPillButton("+250") {
                            dispatch("water", mapOf("ml" to 250))
                        }
                        WaterPillButton("+500") {
                            dispatch("water", mapOf("ml" to 500))
                        }
                    }
                }

                // MEALS Section Header
                Text(
                    text = "MEALS",
                    color = subtle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.6.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // 4 Meal Slot Cards (Breakfast, Lunch, Dinner, Snacks)
                val slotDefs = listOf(
                    Triple("breakfast", "Breakfast", BreakfastAmber),
                    Triple("lunch", "Lunch", LunchGreen),
                    Triple("dinner", "Dinner", DinnerIndigo),
                    Triple("snacks", "Snacks", SnacksCoral)
                )

                slotDefs.forEach { (slotId, slotTitle, slotDotColor) ->
                    val slotEntries = (0 until entries.length())
                        .mapNotNull { entries.optJSONObject(it) }
                        .filter { it.optString("slot").equals(slotId, ignoreCase = true) }
                    val slotKcal = slotEntries.sumOf { it.optDouble("kcal", 0.0) }.roundToInt()
                    val canCopyYesterday = copyYesterday.optBoolean(slotId, false)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                GlowingDot(color = slotDotColor)
                                Text(
                                    text = slotTitle,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (slotEntries.isNotEmpty()) {
                                    Text(
                                        text = "${fmtNum(slotKcal)} kcal",
                                        color = muted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1A1F2D))
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                        .clickable {
                                            dispatch("add", mapOf("slot" to slotId))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+",
                                        color = Color(0xFFBFD4FF),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (slotEntries.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DashedSlotButton(
                                    label = "Add ${slotTitle.lowercase(Locale.US)}",
                                    textColor = Color(0xFF8AB4FF),
                                    modifier = Modifier.weight(if (canCopyYesterday) 1.25f else 1f)
                                ) {
                                    dispatch("add", mapOf("slot" to slotId))
                                }

                                if (canCopyYesterday) {
                                    DashedSlotButton(
                                        label = "Copy yesterday",
                                        textColor = muted,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        dispatch("copySlot", mapOf("slot" to slotId))
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                slotEntries.forEach { entry ->
                                    val entryId = entry.optString("id")
                                    val eName = entry.optString("name", "Food")
                                    val eGrams = entry.optDouble("grams", 100.0).roundToInt()
                                    val eKcal = entry.optDouble("kcal", 0.0).roundToInt()
                                    val eP = entry.optDouble("p", 0.0).roundToInt()
                                    val eC = entry.optDouble("c", 0.0).roundToInt()
                                    val eF = entry.optDouble("f", 0.0).roundToInt()

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(SunkenTile)
                                            .clickable {
                                                if (entryId.isNotEmpty()) {
                                                    dispatch("entry", mapOf("id" to entryId))
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 11.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = eName,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$eGrams g · P $eP · C $eC · F $eF",
                                                color = subtle,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Text(
                                            text = fmtNum(eKcal),
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4C. SHOPPING PANE
            if (activePane == "shop") {
                val shopItems = shopObj?.optJSONArray("items") ?: JSONArray()
                val activeItems = (0 until shopItems.length())
                    .mapNotNull { shopItems.optJSONObject(it) }
                    .filter { !it.optBoolean("home") }
                val checkedCount = activeItems.count { it.optBoolean("checked") }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "7-Day Smart Shopping List",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "$checkedCount of ${activeItems.size} items in basket",
                                color = muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        HeaderButton(label = "Clear ticks", width = null) {
                            dispatch("sdClearShop", emptyMap())
                        }
                    }

                    val aisles = activeItems.map { it.optString("aisleName", "Pantry") }.distinct()
                    aisles.forEach { aisleName ->
                        val rows = activeItems.filter { it.optString("aisleName", "Pantry") == aisleName }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = aisleName.uppercase(Locale.US),
                                color = BlueAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.4.sp
                            )
                            rows.forEach { item ->
                                val itemId = item.optString("id")
                                val checked = item.optBoolean("checked")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(SunkenTile)
                                        .clickable {
                                            if (itemId.isNotEmpty()) {
                                                dispatch("sdCheck", mapOf("itemId" to itemId))
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (checked) LunchGreen else Color.Transparent)
                                                .border(
                                                    1.dp,
                                                    if (checked) LunchGreen else Color(0xFF334155),
                                                    RoundedCornerShape(6.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (checked) {
                                                Text(
                                                    text = "✓",
                                                    color = BgObsidian,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = item.optString("name"),
                                                color = if (checked) subtle else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${item.optInt("grams")}g needed · ${item.optInt("meals")} meals",
                                                color = subtle,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${item.optInt("packs")} × ${item.optString("unit")}",
                                        color = if (checked) subtle else BlueAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom "Search foods, or log a meal" Bar matching original screenshots
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xF5121621))
                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(22.dp))
                    .clickable {
                        dispatch("add", emptyMap())
                    }
                    .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🔍",
                        color = muted,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Search foods, or log a meal",
                        color = muted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF93ADFF), Color(0xFF9F85FF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        color = BgObsidian,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun GlowingDot(color: Color) {
    Box(
        modifier = Modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = color.copy(alpha = 0.35f),
                radius = size.minDimension / 2f
            )
            drawCircle(
                color = color,
                radius = 4.5.dp.toPx()
            )
        }
    }
}

@Composable
private fun MacroTileCard(
    label: String,
    dotColor: Color,
    current: Int,
    goal: Int,
    modifier: Modifier = Modifier
) {
    val left = goal - current
    val pct = (current.toFloat() / max(1, goal)).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SunkenTile)
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GlowingDot(color = dotColor)
            Text(
                text = label,
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.1.sp,
                maxLines = 1
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$current",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = " / $goal g",
                color = TextSubtle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0xFF191E2B))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }

        Text(
            text = if (left >= 0) "$left g left" else "${-left} g over",
            color = TextSubtle,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HeaderButton(
    label: String,
    width: androidx.compose.ui.unit.Dp?,
    onClick: () -> Unit
) {
    val mod = if (width != null) {
        Modifier.size(width, 42.dp)
    } else {
        Modifier.height(42.dp).padding(horizontal = 2.dp)
    }
    Box(
        modifier = mod
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF171B26))
            .border(1.dp, Color(0x16FFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (width == null) 14.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (label == "Targets") Color(0xFF9EC5FF) else Color(0xFFE2E8F0),
            fontSize = if (label == "Targets") 14.sp else 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WaterPillButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF171B26))
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun DashedSlotButton(
    label: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SunkenTile)
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun VerticalHairline() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(Color(0x14FFFFFF))
    )
}
