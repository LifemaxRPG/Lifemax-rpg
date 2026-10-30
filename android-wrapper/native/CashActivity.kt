package com.lifemax.rpg

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.lang.ref.WeakReference
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Currency
import kotlin.math.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class CashActivity : ComponentActivity(), SensorEventListener {
    private var session: CashSession? = null
    private var destination = ""
    private var tilt by mutableFloatStateOf(0f)
    private var sensors: SensorManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val s =
            CashHost.session
                ?: run {
                    finish()
                    return
                }
        session = s
        CashHost.screen = WeakReference(this)
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val light = s.snapshot.optBoolean("light")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            },
        )
        setContent {
            val colors =
                if (s.snapshot.optBoolean("light"))
                    lightColorScheme(
                        primary = Color(0xFF007C63),
                        background = Color(0xFFF2F5F3),
                        surface = Color(0xFFFFFFFF),
                        secondary = Color(0xFF795B13),
                    )
                else
                    darkColorScheme(
                        primary = Color(0xFF80E9BE),
                        onPrimary = Color(0xFF06382B),
                        secondary = Color(0xFFF2CD83),
                        background = Color(0xFF0C1519),
                        surface = Color(0xFF142127),
                        surfaceVariant = Color(0xFF1E3036),
                        onBackground = Color(0xFFE4EFEA),
                    )
            MaterialTheme(colorScheme = colors) {
                CashScreen(s, tilt) {
                    destination = it
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        session?.request("refresh")
        if (session?.snapshot?.optBoolean("reduceMotion") == false) {
            sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        sensors?.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(e: SensorEvent) {
        tilt = tilt * .88f + (-e.values[0] / 9.81f).coerceIn(-.5f, .5f) * .12f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        if (isFinishing && CashHost.session === session) {
            val id = session?.id
            val callback = CashHost.closed
            CashHost.session = null
            CashHost.closed = null
            CashHost.screen.clear()
            if (id != null) callback?.invoke(id, destination)
        }
        super.onDestroy()
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun usd(n: Double): String =
    NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance("USD") }.format(n)

private fun dateLabel(raw: String): String =
    try {
        OffsetDateTime.parse(raw).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (_: Exception) {
        raw.take(10)
    }

private fun validAmount(s: String, negative: Boolean = false): Double? =
    s.trim().replace(',', '.').toDoubleOrNull()?.takeIf {
        it.isFinite() && abs(it) <= 1e12 && (negative || it >= 0)
    }

private fun bodyObject(vararg pairs: Pair<String, Any>): JSONObject =
    JSONObject().apply { pairs.forEach { put(it.first, it.second) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CashScreen(s: CashSession, tilt: Float, leave: (String) -> Unit) {
    val data = s.snapshot
    val m = data.optJSONObject("money") ?: JSONObject()
    val balance = data.optDouble("balance", 0.0)
    val goal = m.optDouble("goal", 0.0)
    val tx = m.optJSONArray("tx").objects().sortedByDescending { it.optString("date") }
    val busy = s.busy != null
    val motion = !data.optBoolean("reduceMotion")
    var sheet by rememberSaveable { mutableStateOf("") }
    var delete by remember { mutableStateOf<JSONObject?>(null) }
    var limit by rememberSaveable { mutableIntStateOf(50) }
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val feedback = s.feedback
    LaunchedEffect(feedback?.id) {
        if (feedback != null) {
            if (feedback.ok) {
                sheet = ""
                delete = null
                if (data.optBoolean("haptics"))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            snackbar.showSnackbar(feedback.message)
        }
    }
    // Do not silently unlock a timed-out write: a late acknowledgement could
    // otherwise turn a second tap into a duplicate transfer.
    LaunchedEffect(s.busy) {
        if (s.busy != null) {
            delay(12000)
            if (s.busy != null)
                snackbar.showSnackbar(
                    "Still waiting for your saved data. Close Cash and reopen if needed."
                )
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cash", fontWeight = FontWeight.Bold)
                        Text(
                            "YOUR TREASURY",
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { leave("") }) { Icon(Icons.Outlined.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { sheet = "account" }, enabled = !busy) {
                        Icon(Icons.Outlined.Tune, "Account settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    data.optJSONArray("tabs").objects().forEach { tab ->
                        val id = tab.optString("id")
                        val label = tab.optString("label").ifBlank { id }
                        FilterChip(
                            selected = id == "money",
                            onClick = { if (id != "money") leave(id) },
                            label = { Text(if (id == "money") "Cash" else label) },
                        )
                    }
                }
            }
        },
    ) { inset ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inset),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        m.optString("account").ifBlank { "Savings account" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        usd(balance),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                    )
                    Text(
                        "+${usd(data.optDouble("monthDeposits",0.0))} saved this month",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SavingsJar(
                        if (goal > 0) (balance / goal).toFloat().coerceIn(0f, 1f) else 0f,
                        tilt,
                        motion,
                        goal > 0 && balance >= goal,
                        Modifier.padding(top = 10.dp).fillMaxWidth().height(248.dp),
                    )
                    Text(
                        if (goal > 0)
                            "${((balance/goal)*100).coerceIn(0.0,100.0).roundToInt()}% of ${usd(goal)}"
                        else "Give your savings a destination",
                        fontWeight = FontWeight.Medium,
                    )
                    TextButton(onClick = { sheet = "account" }, enabled = !busy) {
                        Text(
                            if (goal > 0 && balance >= goal) "Goal reached · Set your next goal"
                            else if (goal > 0) "${usd(max(0.0,goal-balance))} to go · Edit goal"
                            else "Set a savings goal"
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { sheet = "deposit" },
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(54.dp),
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add funds")
                    }
                    OutlinedButton(
                        onClick = { sheet = "withdraw" },
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(54.dp),
                    ) {
                        Text("Withdraw")
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            item {
                val pay = m.optJSONObject("pay") ?: JSONObject()
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.WorkOutline,
                                null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Paychecks & wages",
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { sheet = "pay" }, enabled = !busy) {
                                Text("Manage")
                            }
                        }
                        if (pay.optBoolean("on")) {
                            Text(
                                usd(data.optDouble("pendingTotal", 0.0)) + " pending",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                pay.optString("next")
                                    .takeIf { it.isNotBlank() }
                                    ?.let { "Next payday · ${it.replace('T',' ')}" }
                                    ?: "Choose your next payday",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else
                            Text(
                                "Turn finished work shifts into pending pay.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Transactions",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${tx.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tx.isEmpty())
                item {
                    Text(
                        "Your first deposit starts the story. Record money you set aside in your real account.",
                        Modifier.padding(vertical = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            items(tx.take(limit), key = { it.optString("id") }) { row ->
                val dismiss =
                    rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled && !busy) delete = row
                            false
                        }
                    )
                SwipeToDismissBox(
                    state = dismiss,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = !busy,
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(20.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    },
                    content = { TransactionRow(row, busy) { delete = row } },
                )
            }
            if (tx.size > limit)
                item {
                    TextButton(onClick = { limit += 50 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Show more transactions")
                    }
                }
            item {
                Text(
                    "Record your real transfers here. Deposits can earn Business XP. LifeMax does not connect to your bank.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
                TextButton(onClick = { leave("web") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open classic Cash")
                }
            }
        }
    }
    if (sheet.isNotEmpty())
        ModalBottomSheet(
            onDismissRequest = { sheet = "" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .imePadding()
                    .verticalCashScroll()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (sheet) {
                    "deposit",
                    "withdraw" -> TransactionForm(sheet, busy, s::request)
                    "account" -> AccountForm(m, busy, s::request)
                    "pay" -> PayForm(data, busy, s::request)
                    else ->
                        if (sheet.startsWith("job:"))
                            JobForm(data, sheet.removePrefix("job:"), busy, s::request)
                }
                if (sheet == "pay") {
                    val rules = data.optJSONArray("rules").objects()
                    Text("Jobs", style = MaterialTheme.typography.titleMedium)
                    if (rules.isEmpty())
                        Text(
                            "Add work hours in Life Director → My time to set an hourly wage.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    rules.forEach { j ->
                        OutlinedButton(
                            onClick = { sheet = "job:" + j.optString("id") },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(j.optString("title"))
                        }
                    }
                    val pend = m.optJSONArray("pending").objects()
                    if (pend.isNotEmpty()) {
                        Text("Pending shifts", style = MaterialTheme.typography.titleMedium)
                        pend.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.optString("title"))
                                    Text(
                                        "${p.optDouble("hours")} h · ${usd(p.optDouble("gross")*(1-(m.optJSONObject("pay")?.optDouble("tax")?:0.0)/100))}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        delete =
                                            bodyObject("pending" to true, "id" to p.optString("id"))
                                    },
                                    enabled = !busy,
                                ) {
                                    Icon(Icons.Outlined.Close, "Remove pending shift")
                                }
                            }
                        }
                        Button(
                            onClick = { s.request("clearPay") },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Paycheck arrived · Add now")
                        }
                    }
                }
            }
        }
    delete?.let { row ->
        AlertDialog(
            onDismissRequest = { if (!busy) delete = null },
            title = {
                Text(
                    if (row.optBoolean("pending")) "Remove pending shift?"
                    else "Remove transaction?"
                )
            },
            text = {
                Text(
                    if (row.optBoolean("pending")) "This shift will not be counted again."
                    else
                        "The balance will change. Any XP earned by this deposit will also be removed."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        s.request(
                            if (row.optBoolean("pending")) "removePending" else "delete",
                            bodyObject("id" to row.optString("id")),
                        )
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { delete = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun Modifier.verticalCashScroll(): Modifier =
    this.then(Modifier.heightIn(max = 640.dp)).then(Modifier.verticalScrollCash())

@Composable
private fun Modifier.verticalScrollCash(): Modifier =
    this.then(Modifier.verticalScroll(rememberScrollState()))

@Composable
private fun TransactionRow(t: JSONObject, busy: Boolean, remove: () -> Unit) {
    val deposit = t.optString("type") == "deposit"
    val color =
        if (deposit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (deposit) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                null,
                tint = color,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (t.optBoolean("pay")) "Paycheck"
                    else if (deposit) "Deposit" else "Withdrawal",
                    fontWeight = FontWeight.Medium,
                )
                if (t.optString("note").isNotBlank())
                    Text(t.optString("note"), style = MaterialTheme.typography.bodySmall)
                Text(
                    dateLabel(t.optString("date")),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                (if (deposit) "+" else "−") + usd(t.optDouble("amount")),
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            IconButton(onClick = remove, enabled = !busy) {
                Icon(Icons.Outlined.DeleteOutline, "Remove transaction", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TransactionForm(type: String, busy: Boolean, send: (String, JSONObject) -> Unit) {
    var amount by rememberSaveable(type) { mutableStateOf("") }
    var note by rememberSaveable(type) { mutableStateOf("") }
    val n = validAmount(amount)
    Text(
        if (type == "deposit") "Add to your savings" else "Withdraw funds",
        style = MaterialTheme.typography.headlineSmall,
    )
    MoneyField("Amount", amount, { amount = it }, enabled = !busy)
    OutlinedTextField(
        value = note,
        onValueChange = { note = it.take(60) },
        label = { Text("Note (optional)") },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Record a transfer you made in your real account.",
        style = MaterialTheme.typography.bodySmall,
    )
    Button(
        onClick = {
            send("transaction", bodyObject("type" to type, "amount" to (n ?: 0.0), "note" to note))
        },
        enabled = !busy && n != null && n > 0,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(
            if (busy) "Saving…"
            else if (type == "deposit") "Record deposit" else "Record withdrawal"
        )
    }
}

@Composable
private fun AccountForm(m: JSONObject, busy: Boolean, send: (String, JSONObject) -> Unit) {
    var name by rememberSaveable { mutableStateOf(m.optString("account")) }
    var start by rememberSaveable { mutableStateOf(m.optDouble("start", 0.0).toString()) }
    var goal by rememberSaveable { mutableStateOf(m.optDouble("goal", 0.0).toString()) }
    Text("Your savings account", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(40) },
        label = { Text("Account nickname") },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
    MoneyField("Starting balance", start, { start = it }, enabled = !busy)
    MoneyField("Savings goal", goal, { goal = it }, enabled = !busy)
    Text(
        "The starting balance is added to your transactions. Use a nickname, not an account number.",
        style = MaterialTheme.typography.bodySmall,
    )
    Button(
        onClick = {
            send(
                "account",
                bodyObject(
                    "account" to name,
                    "start" to (validAmount(start, true) ?: 0.0),
                    "goal" to (validAmount(goal) ?: 0.0),
                ),
            )
        },
        enabled = !busy && validAmount(start, true) != null && validAmount(goal) != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Save account")
    }
}

@Composable
private fun MoneyField(
    label: String,
    value: String,
    change: (String) -> Unit,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 18) change(it) },
        label = { Text(label) },
        prefix = { Text("$ ") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PayForm(data: JSONObject, busy: Boolean, send: (String, JSONObject) -> Unit) {
    val pay = data.optJSONObject("money")?.optJSONObject("pay") ?: JSONObject()
    var on by rememberSaveable { mutableStateOf(pay.optBoolean("on")) }
    var freq by rememberSaveable { mutableStateOf(pay.optString("freq", "biweekly")) }
    var next by rememberSaveable { mutableStateOf(pay.optString("next").take(10)) }
    var time by rememberSaveable {
        mutableStateOf(pay.optString("next").drop(11).take(5).ifBlank { "09:00" })
    }
    var from by rememberSaveable { mutableStateOf(pay.optString("from", data.optString("today"))) }
    var tax by rememberSaveable { mutableStateOf(pay.optDouble("tax", 0.0).toString()) }
    var lag by rememberSaveable { mutableStateOf(pay.optInt("lag", 0).toString()) }
    val context = LocalContext.current
    fun date(value: String, change: (String) -> Unit) {
        val d =
            try {
                LocalDate.parse(value)
            } catch (_: Exception) {
                LocalDate.now()
            }
        DatePickerDialog(
                context,
                { _, y, m, day -> change(LocalDate.of(y, m + 1, day).toString()) },
                d.year,
                d.monthValue - 1,
                d.dayOfMonth,
            )
            .show()
    }
    Text("Paychecks & wages", style = MaterialTheme.typography.headlineSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Track shift pay", Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = { on = it }, enabled = !busy)
    }
    Text(
        "Finished shifts from Life Director become pending pay, then clear on payday.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text("Pay frequency", style = MaterialTheme.typography.labelLarge)
    Column {
        listOf(
                "weekly" to "Every week",
                "biweekly" to "Every 2 weeks",
                "semimonthly" to "Twice a month",
                "monthly" to "Monthly",
            )
            .forEach { (id, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = freq == id, onClick = { freq = id }, enabled = !busy)
                    Text(label)
                }
            }
    }
    OutlinedButton(
        onClick = { date(next) { next = it } },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (next.isBlank()) "Choose next payday" else "Next payday · $next")
    }
    OutlinedButton(
        onClick = {
            val parts = time.split(':')
            TimePickerDialog(
                    context,
                    { _, h, m -> time = "%02d:%02d".format(h, m) },
                    parts.getOrNull(0)?.toIntOrNull() ?: 9,
                    parts.getOrNull(1)?.toIntOrNull() ?: 0,
                    false,
                )
                .show()
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Time it arrives · $time")
    }
    OutlinedTextField(
        value = tax,
        onValueChange = { tax = it },
        label = { Text("Estimated tax withheld (%) · 0–60") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        enabled = !busy,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = lag,
        onValueChange = { lag = it },
        label = { Text("Hold back newest days · 0–30") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !busy,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { date(from) { from = it } },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Count shifts starting · $from")
    }
    Button(
        onClick = {
            send(
                "paySettings",
                bodyObject(
                    "on" to on,
                    "freq" to freq,
                    "next" to if (next.isBlank()) "" else next + "T" + time,
                    "from" to from,
                    "tax" to (validAmount(tax) ?: 0.0),
                    "lag" to (lag.toIntOrNull() ?: 0),
                ),
            )
        },
        enabled =
            !busy &&
                validAmount(tax)?.let { it <= 60 } == true &&
                lag.toIntOrNull()?.let { it in 0..30 } == true,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Save pay schedule")
    }
}

@Composable
private fun JobForm(
    data: JSONObject,
    id: String,
    busy: Boolean,
    send: (String, JSONObject) -> Unit,
) {
    val job = data.optJSONObject("money")?.optJSONObject("jobs")?.optJSONObject(id) ?: JSONObject()
    val title =
        data.optJSONArray("rules").objects().find { it.optString("id") == id }?.optString("title")
            ?: "Job"
    var wage by rememberSaveable(id) { mutableStateOf(job.optDouble("wage", 0.0).toString()) }
    var brk by rememberSaveable(id) { mutableStateOf(job.optDouble("brk", 0.0).toString()) }
    Text(title, style = MaterialTheme.typography.headlineSmall)
    MoneyField("Hourly wage", wage, { wage = it }, enabled = !busy)
    OutlinedTextField(
        value = brk,
        onValueChange = { brk = it },
        label = { Text("Unpaid break (minutes)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            send(
                "job",
                bodyObject(
                    "id" to id,
                    "wage" to (validAmount(wage) ?: 0.0),
                    "brk" to (validAmount(brk) ?: 0.0),
                ),
            )
        },
        enabled =
            !busy &&
                validAmount(wage)?.let { it <= 1e6 } == true &&
                validAmount(brk)?.let { it <= 1440 } == true,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Save wage")
    }
}

/** Two translucent wave layers clipped to a glass jar. No WebView/canvas bridge. */
@Composable
private fun SavingsJar(
    fraction: Float,
    tilt: Float,
    motion: Boolean,
    complete: Boolean,
    modifier: Modifier = Modifier,
) {
    val level by
        animateFloatAsState(
            fraction,
            if (motion) spring(dampingRatio = .65f, stiffness = 45f) else snap(),
            label = "Water level",
        )
    val phase =
        if (motion) {
            val transition = rememberInfiniteTransition(label = "Water")
            val value by
                transition.animateFloat(
                    0f,
                    (2 * PI).toFloat(),
                    infiniteRepeatable(tween(3400, easing = LinearEasing)),
                    label = "Wave phase",
                )
            value
        } else 0f
    var splash by remember { mutableFloatStateOf(0f) }
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(fraction, splash) {
        if (motion) {
            ripple.snapTo(1f)
            ripple.animateTo(0f, tween(1600))
        }
    }
    val water by
        animateColorAsState(
            if (complete) Color(0xFFF2CD83) else Color(0xFF63DCAF),
            label = "Water color",
        )
    Canvas(
        modifier
            .semantics {
                contentDescription = "Savings jar, ${(fraction*100).roundToInt()} percent full"
            }
            .pointerInput(motion) { detectTapGestures { if (motion) splash += 1f } }
    ) {
        val w = min(size.width * .62f, 230.dp.toPx())
        val h = size.height * .86f
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        val jar =
            Path().apply {
                moveTo(left + w * .18f, top + h * .09f)
                lineTo(left + w * .82f, top + h * .09f)
                cubicTo(
                    left + w * .82f,
                    top + h * .22f,
                    left + w,
                    top + h * .2f,
                    left + w,
                    top + h * .38f,
                )
                lineTo(left + w, top + h * .85f)
                quadraticBezierTo(left + w, top + h, left + w * .82f, top + h)
                lineTo(left + w * .18f, top + h)
                quadraticBezierTo(left, top + h, left, top + h * .85f)
                lineTo(left, top + h * .38f)
                cubicTo(
                    left,
                    top + h * .2f,
                    left + w * .18f,
                    top + h * .22f,
                    left + w * .18f,
                    top + h * .09f,
                )
                close()
            }
        drawOval(
            Brush.radialGradient(listOf(water.copy(alpha = .12f), Color.Transparent)),
            Offset(left - w * .25f, top + h * .63f),
            Size(w * 1.5f, h * .5f),
        )
        drawPath(jar, Color(0xFF9ED8CB).copy(alpha = .05f))
        clipPath(jar) {
            val base = top + h - h * .9f * level.coerceIn(0f, 1f)
            val p = if (motion) phase else 0f
            for (layer in 0..1) {
                val wave = Path()
                wave.moveTo(left, top + h + 1)
                for (i in 0..90) {
                    val x = i / 90f
                    val amp = if (motion) (3 + 5 * ripple.value) * (1 - level * .45f) else 0f
                    val y =
                        base +
                            sin(x * PI * 2 + p + layer * 1.5f).toFloat() * amp +
                            (if (motion) tilt else 0f) * (x - .5f) * w * .55f +
                            layer * 5
                    wave.lineTo(left + x * w, y)
                }
                wave.lineTo(left + w, top + h + 1)
                wave.close()
                if (level > 0)
                    drawPath(
                        wave,
                        Brush.verticalGradient(
                            listOf(
                                water.copy(alpha = if (layer == 0) .35f else .8f),
                                Color(0xFF167963),
                            ),
                            startY = base,
                            endY = top + h,
                        ),
                    )
            }
            for (i in 1..5) {
                val y = top + h - h * .9f * (i / 5f)
                drawLine(
                    Color.White.copy(alpha = .15f),
                    Offset(left + w * .78f, y),
                    Offset(left + w * .88f, y),
                    1.dp.toPx(),
                )
            }
            drawRoundRect(
                Color.White.copy(alpha = .13f),
                Offset(left + w * .09f, top + h * .34f),
                Size(w * .035f, h * .42f),
                androidx.compose.ui.geometry.CornerRadius(8f),
            )
        }
        drawPath(jar, Color(0xFFADDFD2).copy(alpha = .6f), style = Stroke(2.dp.toPx()))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF7D9C91), Color(0xFF344E47))),
            Offset(left + w * .13f, top),
            Size(w * .74f, h * .09f),
            androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
        )
        if (complete) {
            drawCircle(Color(0xFFF2CD83), 5.dp.toPx(), Offset(left + w * .86f, top - h * .04f))
            drawCircle(
                Color(0xFFF2CD83).copy(alpha = .5f),
                3.dp.toPx(),
                Offset(left + w * .06f, top + h * .2f),
            )
        }
    }
}
