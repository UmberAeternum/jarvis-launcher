package com.umberaeternum.jarvis.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Loop: command the desktop's full agent from the phone. Events
 * (status/partial/reply/confirm_request) stream in via long-poll; gated actions
 * surface as APPROVE/DENY - the same confirmation gate as the terminal. While
 * the agent composes, "partial" events feed a dimmed streaming bubble that the
 * final "reply" replaces.
 */
@Composable
fun DesktopScreen(settings: SettingsStore.Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var desktopUrl by remember { mutableStateOf(settings.desktopUrl) }
    var desktopToken by remember { mutableStateOf(settings.desktopToken) }
    var connected by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf(listOf("DESKTOP LINK - connect first, Sir.")) }
    var partialText by remember { mutableStateOf<String?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastEventId by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }

    fun line(text: String, accent: Boolean = false) {
        lines = lines + (if (accent) "» $text" else text)
    }

    fun startPolling(config: DesktopClient.DesktopConfig) {
        scope.launch(Dispatchers.IO) {
            while (connected) {
                val events = DesktopClient.eventsSince(config, lastEventId)
                if (events == null) {
                    withContext(Dispatchers.Main) {
                        line("link lost - reconnecting...", accent = true)
                    }
                    delay(1500)
                    continue
                }
                for (index in 0 until events.length()) {
                    val event = events.getJSONObject(index)
                    lastEventId = maxOf(lastEventId, event.optInt("id", lastEventId))
                    when (event.optString("type")) {
                        "user" -> withContext(Dispatchers.Main) {
                            partialText = null   // a new turn clears any stale streaming bubble
                            line("YOU: " + event.optString("text"))
                        }
                        "partial" -> withContext(Dispatchers.Main) {
                            partialText = event.optString("text") + " ▌"
                        }
                        "reply" -> withContext(Dispatchers.Main) {
                            thinking = false
                            partialText = null
                            line("J.A.R.V.I.S.: " + event.optString("text"))
                        }
                        "status" -> withContext(Dispatchers.Main) {
                            line("· " + event.optString("text"))
                        }
                        "confirm_request" -> withContext(Dispatchers.Main) {
                            pendingConfirm = Pair(
                                event.optString("confirm_id"),
                                event.optString("description"),
                            )
                        }
                    }
                }
                delay(250)
            }
        }
    }

    fun connect() {
        val config = DesktopClient.DesktopConfig(desktopUrl, desktopToken)
        SettingsStore.save(
            context,
            settings.copy(desktopUrl = desktopUrl, desktopToken = desktopToken),
        )
        scope.launch(Dispatchers.IO) {
            val probe = DesktopClient.eventsSince(config, 0)
            withContext(Dispatchers.Main) {
                if (probe != null) {
                    connected = true
                    linkError = null
                    line("Bridge link established.")
                    startPolling(config)
                } else {
                    linkError = "could not reach the bridge - check URL, token, and that 'jarvis serve' is running"
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudButton("◄ HUD", onBack)
            Text("DESKTOP LINK", color = HudColors.Cyan, fontSize = 14.sp, letterSpacing = 3.sp)
            Box(Modifier.width(64.dp))
        }

        if (!connected) {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Text(
                    "Connect to the desktop Bridge ('jarvis serve' on your PC).",
                    color = HudColors.TextDim,
                    fontSize = 12.sp,
                )
                TextField(
                    value = desktopUrl,
                    onValueChange = { desktopUrl = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    placeholder = {
                        Text("http://192.168.x.x:8772", color = HudColors.TextDim, fontSize = 13.sp)
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = HudColors.Panel,
                        unfocusedContainerColor = HudColors.Panel,
                        focusedTextColor = HudColors.Cyan,
                        unfocusedTextColor = HudColors.Cyan,
                        cursorColor = HudColors.Amber,
                        focusedIndicatorColor = HudColors.Cyan.copy(alpha = 0.5f),
                        unfocusedIndicatorColor = HudColors.Cyan.copy(alpha = 0.25f),
                    ),
                    shape = RoundedCornerShape(10.dp),
                )
                TextField(
                    value = desktopToken,
                    onValueChange = { desktopToken = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    placeholder = { Text("session token", color = HudColors.TextDim, fontSize = 13.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = HudColors.Panel,
                        unfocusedContainerColor = HudColors.Panel,
                        focusedTextColor = HudColors.Cyan,
                        unfocusedTextColor = HudColors.Cyan,
                        cursorColor = HudColors.Amber,
                        focusedIndicatorColor = HudColors.Cyan.copy(alpha = 0.5f),
                        unfocusedIndicatorColor = HudColors.Cyan.copy(alpha = 0.25f),
                    ),
                    shape = RoundedCornerShape(10.dp),
                )
                HudButton("CONNECT") { connect() }
                linkError?.let {
                    Text(
                        it,
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            return@Column
        }

        val listState = rememberLazyListState()
        // Keep the growing streaming bubble in view as it accumulates text.
        LaunchedEffect(partialText) {
            if (partialText != null) {
                listState.animateScrollToItem(lines.size)
            }
        }
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lines.size) { index ->
                Text(
                    lines[index],
                    color = if (lines[index].startsWith("»")) HudColors.Amber else HudColors.Cyan,
                    fontSize = 13.sp,
                )
            }
            partialText?.let { partial ->
                item {
                    Text(
                        "J.A.R.V.I.S.: " + partial,
                        color = HudColors.Cyan.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                    )
                }
            }
            if (thinking) {
                item { Text("desktop agent is working...", color = HudColors.Amber, fontSize = 12.sp) }
            }
        }

        pendingConfirm?.let { (confirmId, description) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HudColors.Amber.copy(alpha = 0.14f))
                    .border(1.dp, HudColors.Amber, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    "CONFIRM REQUIRED",
                    color = HudColors.Amber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    description,
                    color = HudColors.Cyan,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val config = DesktopClient.DesktopConfig(desktopUrl, desktopToken)
                    HudButton("DENY") {
                        scope.launch(Dispatchers.IO) {
                            DesktopClient.confirm(config, confirmId, approved = false)
                            withContext(Dispatchers.Main) { pendingConfirm = null }
                        }
                    }
                    HudButton("APPROVE") {
                        scope.launch(Dispatchers.IO) {
                            DesktopClient.confirm(config, confirmId, approved = true)
                            withContext(Dispatchers.Main) { pendingConfirm = null }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Command the desktop, Sir", color = HudColors.TextDim, fontSize = 13.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = HudColors.Panel,
                    unfocusedContainerColor = HudColors.Panel,
                    focusedTextColor = HudColors.Cyan,
                    unfocusedTextColor = HudColors.Cyan,
                    cursorColor = HudColors.Amber,
                    focusedIndicatorColor = HudColors.Cyan.copy(alpha = 0.5f),
                    unfocusedIndicatorColor = HudColors.Cyan.copy(alpha = 0.25f),
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Text(
                "SEND",
                color = HudColors.Amber,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clickable {
                        val text = input.trim()
                        if (text.isNotEmpty() && connected && !thinking) {
                            input = ""
                            thinking = true
                            val config = DesktopClient.DesktopConfig(desktopUrl, desktopToken)
                            scope.launch(Dispatchers.IO) { DesktopClient.command(config, text) }
                        }
                    }
                    .padding(8.dp),
            )
        }
    }
}
