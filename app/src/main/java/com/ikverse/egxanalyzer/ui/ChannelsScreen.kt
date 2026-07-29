package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import kotlinx.coroutines.launch

@Composable
internal fun ChannelsScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    var firstValue by remember(appState.telegramAuthState.step) { mutableStateOf("") }
    var secondValue by remember(appState.telegramAuthState.step) { mutableStateOf("") }
    Screen(
        title = "Channels",
        subtitle = "Select chats; the target-date rules determine the exact Cairo source window.",
    ) {
        Text(
            appState.telegramAuthState.message,
            color = if (appState.telegramAuthState.step == TelegramAuthStep.ERROR) {
                MaterialTheme.colorScheme.error
            } else MaterialTheme.colorScheme.primary,
        )
        when (appState.telegramAuthState.step) {
            TelegramAuthStep.API_CONFIGURATION -> {
                AuthCard("Telegram application") {
                    Text(
                        "Create an application at my.telegram.org and enter its API ID and API hash. " +
                            "They are encrypted on this device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AuthField(firstValue, { firstValue = it }, "API ID")
                    AuthField(secondValue, { secondValue = it }, "API hash", secret = true)
                    Button(onClick = {
                        scope.launch {
                            appState.saveTelegramApiConfiguration(firstValue, secondValue)
                            secondValue = ""
                        }
                    }) { Text("Initialize Telegram") }
                }
            }
            TelegramAuthStep.PHONE_NUMBER -> AuthCard("Phone number") {
                AuthField(firstValue, { firstValue = it }, "Phone number with country code")
                Button(onClick = {
                    scope.launch { appState.submitTelegramPhone(firstValue) }
                }) { Text("Send verification code") }
                TextButton(onClick = {
                    firstValue = ""
                    scope.launch { appState.resetTelegramApiConfiguration() }
                }) { Text("Change Telegram app ID / hash") }
            }
            TelegramAuthStep.VERIFICATION_CODE -> AuthCard("Verification code") {
                AuthField(firstValue, { firstValue = it }, "Telegram code")
                Button(onClick = {
                    scope.launch { appState.submitTelegramCode(firstValue) }
                }) { Text("Verify code") }
            }
            TelegramAuthStep.TWO_FACTOR_PASSWORD -> AuthCard("Two-step verification") {
                appState.telegramAuthState.hint?.takeIf(String::isNotBlank)?.let {
                    Text("Hint: $it")
                }
                AuthField(firstValue, { firstValue = it }, "Telegram password", secret = true)
                Button(onClick = {
                    scope.launch {
                        appState.submitTelegramPassword(firstValue)
                        firstValue = ""
                    }
                }) { Text("Continue") }
            }
            TelegramAuthStep.EMAIL_ADDRESS -> AuthCard("Login email") {
                AuthField(firstValue, { firstValue = it }, "Email address")
                Button(onClick = {
                    scope.launch { appState.submitTelegramEmail(firstValue) }
                }) { Text("Send email code") }
            }
            TelegramAuthStep.EMAIL_CODE -> AuthCard("Email verification") {
                AuthField(firstValue, { firstValue = it }, "Email code")
                Button(onClick = {
                    scope.launch { appState.submitTelegramEmailCode(firstValue) }
                }) { Text("Verify email") }
            }
            TelegramAuthStep.REGISTRATION -> AuthCard("Finish registration") {
                AuthField(firstValue, { firstValue = it }, "First name")
                AuthField(secondValue, { secondValue = it }, "Last name")
                Button(onClick = {
                    scope.launch { appState.registerTelegram(firstValue, secondValue) }
                }) { Text("Register") }
            }
            TelegramAuthStep.OTHER_DEVICE_CONFIRMATION -> AuthCard("Confirm on another device") {
                Text(appState.telegramAuthState.link.orEmpty())
                Text("Open this link in Telegram on an already signed-in device.")
            }
            TelegramAuthStep.READY -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = {
                        scope.launch { appState.refreshTelegramChats() }
                    }) { Text("Refresh chats") }
                    TextButton(onClick = {
                        scope.launch { appState.logoutTelegram() }
                    }) { Text("Sign out") }
                }
                Text(
                    "Target: ${appState.recommendationTargetDate} · " +
                        if (appState.analysisMode == AnalysisMode.NEXT_DAY) {
                            "current / next EGX session"
                        } else {
                            "historical analysis"
                        },
                    color = MaterialTheme.colorScheme.primary,
                )
                if (appState.channels.isEmpty()) {
                    Text("No Telegram chats loaded yet.")
                } else {
                    appState.channels.forEach { channel -> ChannelCard(channel, appState) }
                }
                Button(
                    onClick = {
                        scope.launch { appState.syncTelegramSources() }
                    },
                    enabled = appState.channels.any(ChannelSelection::selected),
                ) { Text("Load analysis source window") }
                appState.telegramSyncMessage?.let { Text(it) }
            }
            TelegramAuthStep.INITIALIZING,
            TelegramAuthStep.LOGGING_OUT,
            TelegramAuthStep.ERROR -> Unit
        }
    }
}

@Composable
private fun AuthCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = if (secret) PasswordVisualTransformation() else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChannelCard(channel: ChannelSelection, appState: AppState) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = channel.selected,
                onCheckedChange = { appState.toggleChannel(channel) },
            )
            Column {
                Text(channel.name, fontWeight = FontWeight.Bold)
                Text(channel.id.toString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
