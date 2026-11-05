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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One past conversation on the desktop Bridge. */
data class HistorySession(val id: Int, val title: String, val startedAt: String, val turns: Int)

/** One message of a desktop conversation. */
data class HistoryMessage(val role: String, val content: String)

private fun parseSessions(array: JSONArray?): List<HistorySession> {
    val sessions = mutableListOf<HistorySession>()
    if (array != null) {
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            sessions += HistorySession(
                id = obj.optInt("id", -1),
                title = obj.optString("title").ifBlank { "untitled session" },
                startedAt = obj.optString("started_at").ifBlank { "unknown time" },
                turns = obj.optInt("turns", 0),
            )
        }
    }
    return sessions
}

private fun parseMessages(array: JSONArray?): List<HistoryMessage> {
    val messages = mutableListOf<HistoryMessage>()
    if (array != null) {
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            messages += HistoryMessage(
                role = obj.optString("role"),
                content = obj.optString("content"),
            )
        }
    }
    return messages
}

/**
 * The Log: the desktop's past conversations, straight from the Bridge
 * (GET /api/history, GET /api/session). Session list first; a tap opens the
 * role-tagged transcript. Same token gate as the Loop.
 */
@Composable
fun HistoryScreen(settings: SettingsStore.Settings, onBack: () -> Unit) {
    var selected by remember { mutableStateOf<HistorySession?>(null) }
    var sessions by remember { mutableStateOf<List<HistorySession>>(emptyList()) }
    var messages by remember { mutableStateOf<List<HistoryMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(selected?.id, reload) {
        error = null
        loading = true
        val config = DesktopClient.DesktopConfig(settings.desktopUrl, settings.desktopToken)
        val failure =
            "could not reach the desktop - check URL, token, and that 'jarvis serve' is running"
        val target = selected
        if (target == null) {
            val result = withContext(Dispatchers.IO) { DesktopClient.history(config) }
            loading = false
            if (result == null) {
                error = failure
            } else {
                sessions = parseSessions(result.optJSONArray("sessions"))
            }
        } else {
            val result = withContext(Dispatchers.IO) { DesktopClient.session(config, target.id) }
            loading = false
            if (result == null) {
                error = failure
            } else {
                messages = parseMessages(result.optJSONArray("messages"))
            }
        }
    }

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
            if (selected == null) {
                HudButton("◄ HUD", onBack)
            } else {
                HudButton("◄ LOG") { selected = null }
            }
            Text("CONVERSATION LOG", color = HudColors.Cyan, fontSize = 14.sp, letterSpacing = 3.sp)
            Box(Modifier.width(64.dp))
        }

        selected?.let { current ->
            Text(
                "${current.title}  ·  ${current.startedAt}",
                color = HudColors.Amber,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 8.dp),
            )
        }

        val errorState = error
        when {
            loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("querying the desktop log...", color = HudColors.Amber, fontSize = 12.sp)
            }
            errorState != null -> Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(errorState, color = Color(0xFFFF6B6B), fontSize = 12.sp)
                HudButton("RETRY") { reload += 1 }
            }
            selected != null && messages.isEmpty() -> Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("no messages recorded in this session, Sir.", color = HudColors.TextDim, fontSize = 12.sp)
            }
            selected != null -> LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages.size) { index ->
                    val message = messages[index]
                    val isUser = message.role == "user"
                    Box(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier
                                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                                .fillMaxWidth(0.86f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isUser) HudColors.Amber.copy(alpha = 0.10f)
                                    else HudColors.Panel.copy(alpha = 0.85f)
                                )
                                .border(
                                    1.dp,
                                    if (isUser) HudColors.Amber.copy(alpha = 0.45f)
                                    else HudColors.Cyan.copy(alpha = 0.45f),
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                if (isUser) "YOU" else "J.A.R.V.I.S.",
                                color = if (isUser) HudColors.Amber else HudColors.Cyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            )
                            SelectionContainer {
                                Text(
                                    message.content,
                                    color = if (isUser) HudColors.TextDim else HudColors.Cyan,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            sessions.isEmpty() -> Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("no conversations recorded yet, Sir.", color = HudColors.TextDim, fontSize = 12.sp)
            }
            else -> LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sessions.size) { index ->
                    val session = sessions[index]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(HudColors.Panel.copy(alpha = 0.8f))
                            .border(1.dp, HudColors.Cyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .clickable { selected = session }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            session.title,
                            color = HudColors.Cyan,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                        Row(
                            Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(session.startedAt, color = HudColors.TextDim, fontSize = 11.sp)
                            Text("${session.turns} turns", color = HudColors.TextDim, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
