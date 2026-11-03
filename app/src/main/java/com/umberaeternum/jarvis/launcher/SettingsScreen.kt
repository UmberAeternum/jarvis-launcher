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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(saved: SettingsStore.Settings, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(saved.provider) }
    var baseUrl by remember { mutableStateOf(saved.baseUrl) }
    var key by remember { mutableStateOf(saved.key) }
    var model by remember { mutableStateOf(saved.model) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudButton("◄ HUD", onBack)
            Text("BRAIN CONFIG", color = HudColors.Cyan, fontSize = 14.sp, letterSpacing = 3.sp)
            Box(Modifier.padding(16.dp))
        }

        Text("PROVIDER", color = HudColors.TextDim, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 16.dp))
        SettingsStore.presets().forEach { (label, url) ->
            val selected = provider == label
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) HudColors.Cyan.copy(alpha = 0.14f)
                        else HudColors.Panel.copy(alpha = 0.7f)
                    )
                    .border(
                        1.dp,
                        (if (selected) HudColors.Cyan else HudColors.TextDim).copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable {
                        provider = label
                        if (url.isNotEmpty()) baseUrl = url
                    }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, color = if (selected) HudColors.Cyan else HudColors.TextDim, fontSize = 13.sp)
                if (selected) Text("●", color = HudColors.Amber, fontSize = 13.sp)
            }
        }

        SettingField("BASE URL", baseUrl) { baseUrl = it }
        SettingField("API KEY", key, visualTransformation = PasswordVisualTransformation()) { key = it }
        SettingField("MODEL", model) { model = it }

        Text(
            if (saved.encrypted) "Key storage: encrypted (Android Keystore)"
            else "Key storage: plain private preferences (crypto unavailable on this device)",
            color = HudColors.TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HudButton("SAVE") {
                SettingsStore.save(
                    context,
                    SettingsStore.Settings(
                        provider = provider, baseUrl = baseUrl, key = key,
                        model = model, encrypted = saved.encrypted,
                        configured = key.isNotBlank() || provider == "Ollama (local)",
                    ),
                )
                status = "Saved."
                onSaved()
            }
            HudButton("TEST LINK") {
                if (testing) return@HudButton
                testing = true
                status = "Testing link..."
                val candidate = SettingsStore.Settings(
                    provider = provider, baseUrl = baseUrl, key = key,
                    model = model, encrypted = saved.encrypted, configured = true,
                )
                scope.launch(Dispatchers.IO) {
                    val reply = BrainClient.chat(candidate, listOf("user" to "ping"))
                    withContext(Dispatchers.Main) {
                        testing = false
                        status = if (reply.startsWith("ERROR")) reply else "Link OK - brain replied."
                    }
                }
            }
        }

        status?.let {
            Text(
                it,
                color = if (it.startsWith("ERROR")) Color(0xFFFF6B6B) else HudColors.Cyan,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Text(
            "Privacy: the key is stored only on this device and is sent only to the "
                + "provider you chose. It is never part of a chat message.",
            color = HudColors.TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
fun SettingField(
    label: String,
    value: String,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    onValueChange: (String) -> Unit,
) {
    Text(
        label,
        color = HudColors.TextDim,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 14.dp),
    )
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        singleLine = true,
        visualTransformation = visualTransformation,
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
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    )
}
