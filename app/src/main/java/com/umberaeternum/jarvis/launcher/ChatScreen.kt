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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(settings: SettingsStore.Settings, onBack: () -> Unit) {
    var messages by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || thinking) return
        messages = messages + ("user" to text)
        input = ""
        thinking = true
        val history = listOf("system" to SYSTEM_PROMPT) + messages
        scope.launch(Dispatchers.IO) {
            val reply = BrainClient.chat(settings, history)
            withContext(Dispatchers.Main) {
                messages = messages + ("assistant" to reply)
                thinking = false
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
            Text("J.A.R.V.I.S. LINK", color = HudColors.Cyan, fontSize = 14.sp, letterSpacing = 3.sp)
            Box(Modifier.width(64.dp))
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Link online. Type a request, Sir.",
                        color = HudColors.TextDim,
                        fontSize = 13.sp,
                    )
                }
            }
            items(messages.size) { index ->
                val (role, content) = messages[index]
                val isUser = role == "user"
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.86f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isUser) HudColors.Amber.copy(alpha = 0.14f)
                                else HudColors.Panel.copy(alpha = 0.9f)
                            )
                            .border(
                                1.dp,
                                (if (isUser) HudColors.Amber else HudColors.Cyan).copy(alpha = 0.45f),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            content,
                            color = if (isUser) HudColors.Amber else HudColors.Cyan,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            if (thinking) {
                item {
                    Text("J.A.R.V.I.S. is thinking...", color = HudColors.Amber, fontSize = 12.sp)
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
                placeholder = { Text("Type a request, Sir", color = HudColors.TextDim, fontSize = 13.sp) },
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
                    .clickable { send() }
                    .padding(8.dp),
            )
        }
    }
}
