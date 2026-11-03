package com.umberaeternum.jarvis.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object HudColors {
    val Background = Color(0xFF050A12)
    val Cyan = Color(0xFF66F0FF)
    val Amber = Color(0xFFFFB454)
    val Panel = Color(0xFF0E2233)
    val TextDim = Color(0xFF7FA8B8)
}

const val SYSTEM_PROMPT =
    "You are J.A.R.V.I.S., the user's phone assistant. Address the user as Sir. " +
        "Be brief, factual and helpful; no filler openers."

// ---------------------------------------------------------------- root

enum class HudScreen { HOME, DRAWER, CHAT, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var screen by remember { mutableStateOf(HudScreen.HOME) }
            val context = LocalContext.current
            var settings by remember { mutableStateOf(SettingsStore.load(context)) }
            var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
            LaunchedEffect(Unit) { apps = AppCatalog.load(context) }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(HudColors.Background)
            ) {
                when (screen) {
                    HudScreen.HOME -> HomeScreen(
                        apps = apps,
                        settings = settings,
                        onOpenDrawer = { screen = HudScreen.DRAWER },
                        onOpenChat = { screen = HudScreen.CHAT },
                        onOpenSettings = { screen = HudScreen.SETTINGS },
                    )
                    HudScreen.DRAWER -> DrawerScreen(
                        apps = apps,
                        onBack = { screen = HudScreen.HOME },
                        onOpenSettings = { screen = HudScreen.SETTINGS },
                    )
                    HudScreen.CHAT -> ChatScreen(
                        settings = settings,
                        onBack = { screen = HudScreen.HOME },
                    )
                    HudScreen.SETTINGS -> SettingsScreen(
                        saved = settings,
                        onBack = { screen = HudScreen.HOME },
                        onSaved = { settings = SettingsStore.load(context) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    settings: SettingsStore.Settings,
    onOpenDrawer: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dock = remember { VoiceDock(context) }
    var heard by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf<String?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(HudColors.Background)
    ) {
        StatusStrip(onOpenSettings = onOpenSettings)

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            RadialApps(
                apps = apps.take(6),
                modifier = Modifier.fillMaxSize(),
                onTap = { AppCatalog.launch(context, it) },
            )
            AiCore(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(210.dp),
                onTap = onOpenChat,
            )
            // transcript / reply bubble
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .fillMaxWidth(0.92f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (heard != null) {
                    Text(
                        "heard: $heard",
                        color = HudColors.TextDim,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (thinking) {
                    Text("J.A.R.V.I.S. is thinking...", color = HudColors.Amber, fontSize = 12.sp)
                }
                if (reply != null && !thinking) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(HudColors.Panel.copy(alpha = 0.85f))
                            .border(1.dp, HudColors.Cyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(reply ?: "", color = HudColors.Cyan, fontSize = 13.sp)
                    }
                }
            }
        }

        HoldToTalkDock(
            listening = listening,
            thinking = thinking,
            available = dock.available,
            onPressStart = {
                listening = true
                reply = null
                dock.start { result ->
                    listening = false
                    if (result == null) {
                        heard = dock.lastError ?: "nothing heard"
                        return@start
                    }
                    heard = result
                    thinking = true
                    scope.launch(Dispatchers.IO) {
                        val text = BrainClient.chat(
                            settings,
                            listOf("system" to SYSTEM_PROMPT, "user" to result),
                        )
                        withContext(Dispatchers.Main) {
                            thinking = false
                            reply = text
                            dock.speak(text)
                        }
                    }
                }
            },
            onPressEnd = { listening = false },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            HudButton("APPS ▲", onOpenDrawer)
            Spacer(Modifier.width(14.dp))
            HudButton("CHAT ✦", onOpenChat)
        }
    }
}

// ---------------------------------------------------------------- status

@Composable
fun StatusStrip(onOpenSettings: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = LocalTime.now()
        }
    }
    var battery by remember { mutableStateOf(-1) }
    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val level = intent?.getIntExtra("level", -1) ?: -1
                val scale = intent?.getIntExtra("scale", -1) ?: -1
                if (level >= 0 && scale > 0) battery = level * 100 / scale
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm")),
            color = HudColors.Cyan,
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
        )
        Text(
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEE  dd MMM")),
            color = HudColors.TextDim,
            fontSize = 12.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (battery >= 0) "PWR $battery%" else "PWR --",
                color = HudColors.TextDim,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "⚙",
                color = HudColors.TextDim,
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = onOpenSettings),
            )
        }
    }
}

// ---------------------------------------------------------------- core + ring

@Composable
fun AiCore(modifier: Modifier = Modifier, onTap: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "core")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "pulse",
    )
    Box(
        modifier
            .clip(CircleShape)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            // breathing outer ring
            drawCircle(
                HudColors.Cyan.copy(alpha = 0.08f + 0.16f * pulse),
                radius = r * (0.72f + 0.22f * pulse),
                style = Stroke(width = 2.dp.toPx()),
            )
            // fixed HUD ring
            drawCircle(
                HudColors.Cyan.copy(alpha = 0.35f),
                radius = r * 0.52f,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            // inner glow
            drawCircle(HudColors.Cyan.copy(alpha = 0.10f + 0.12f * pulse), radius = r * 0.30f)
            // reactor
            drawCircle(HudColors.Amber.copy(alpha = 0.85f), radius = r * (0.13f - 0.03f * pulse))
        }
        Text(
            "J.A.R.V.I.S.",
            color = HudColors.Cyan,
            fontSize = 12.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
fun RadialApps(apps: List<AppEntry>, modifier: Modifier = Modifier, onTap: (AppEntry) -> Unit) {
    BoxWithConstraints(modifier) {
        val density = LocalContext.current.resources.displayMetrics.density
        val centerX = constraints.maxWidth / 2f
        val centerY = constraints.maxHeight / 2f
        val radius = minOf(constraints.maxWidth, constraints.maxHeight) * 0.34f
        apps.forEachIndexed { index, entry ->
            val angle = Math.toRadians((index * 60.0) - 90.0)
            val x = (centerX + radius * cos(angle) - 32 * density).roundToInt()
            val y = (centerY + radius * sin(angle) - 32 * density).roundToInt()
            AppIcon(
                entry = entry,
                modifier = Modifier.offset { IntOffset(x, y) },
                onTap = { onTap(entry) },
            )
        }
    }
}

@Composable
fun AppIcon(entry: AppEntry, modifier: Modifier = Modifier, onTap: () -> Unit) {
    Column(
        modifier
            .size(64.dp)
            .clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconBitmap = remember(entry) {
            runCatching {
                entry.icon.toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = entry.label,
                modifier = Modifier.size(44.dp),
            )
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(HudColors.Panel)
                    .border(1.dp, HudColors.Cyan.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(entry.label.take(1), color = HudColors.Cyan, fontSize = 18.sp)
            }
        }
        Text(
            entry.label,
            color = HudColors.TextDim,
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

// ---------------------------------------------------------------- dock

class VoiceDock(context: Context) {
    val available: Boolean = SpeechRecognizer.isRecognitionAvailable(context)
    var lastError: String = if (available) "" else "speech recognition unavailable on this device"

    private val recognizer = if (available) SpeechRecognizer.createSpeechRecognizer(context) else null
    private val tts = TextToSpeech(context) { }
    private var callback: ((String?) -> Unit)? = null
    private val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    fun start(onResult: (String?) -> Unit) {
        val sr = recognizer
        if (sr == null) {
            onResult(null)
            return
        }
        callback = onResult
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                lastError = "speech error $error"
                callback?.invoke(null)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                callback?.invoke(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        sr.startListening(listenIntent)
    }

    fun speak(text: String) {
        val spoken = text
            .replace(Regex("```[\\s\\S]*?```"), " code block omitted ")
            .replace(Regex("[#>*_`|]"), " ")
            .trim()
            .take(400)
        if (spoken.isNotEmpty()) {
            tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        }
    }
}

@Composable
fun HoldToTalkDock(
    listening: Boolean,
    thinking: Boolean,
    available: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(
                    if (listening) HudColors.Amber.copy(alpha = 0.25f)
                    else HudColors.Cyan.copy(alpha = 0.12f)
                )
                .border(
                    2.dp,
                    if (listening) HudColors.Amber else HudColors.Cyan,
                    CircleShape,
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPressStart()
                            tryAwaitRelease()
                            onPressEnd()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (listening) {
                WaveformBars()
            } else {
                Text(
                    if (available) "HOLD" else "NO MIC",
                    color = if (available) HudColors.Cyan else HudColors.TextDim,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                )
            }
        }
        Text(
            when {
                thinking -> "PROCESSING..."
                listening -> "LISTENING - SPEAK, SIR"
                available -> "HOLD TO TALK"
                else -> "SPEECH UNAVAILABLE"
            },
            color = HudColors.TextDim,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun WaveformBars() {
    val transition = rememberInfiniteTransition(label = "wave")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(5) { bar ->
            val height by transition.animateFloat(
                initialValue = 8f,
                targetValue = 30f,
                animationSpec = infiniteRepeatable(
                    tween(260 + bar * 70, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "bar$bar",
            )
            Box(
                Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .background(HudColors.Amber, RoundedCornerShape(2.dp))
            )
        }
    }
}

// ---------------------------------------------------------------- drawer

@Composable
fun DrawerScreen(apps: List<AppEntry>, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(HudColors.Background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudButton("◄ HUD", onBack)
            Text("APPLICATIONS", color = HudColors.Cyan, fontSize = 14.sp, letterSpacing = 3.sp)
            HudButton("⚙", onOpenSettings)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(apps.size) { index ->
                AppIcon(
                    entry = apps[index],
                    modifier = Modifier.fillMaxWidth(),
                    onTap = { AppCatalog.launch(context, apps[index]) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
fun HudButton(label: String, onTap: () -> Unit) {
    Text(
        label,
        color = HudColors.Cyan,
        fontSize = 13.sp,
        letterSpacing = 2.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HudColors.Panel.copy(alpha = 0.8f))
            .border(1.dp, HudColors.Cyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
