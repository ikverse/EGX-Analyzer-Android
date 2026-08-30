package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.ChatKind
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.selection.selectable

/** The Telegram half of the Analyze screen: the chat list once signed in, the way in before that. */
@Composable
internal fun ColumnScope.ChannelsSection(appState: AppState) {
    if (appState.telegramAuthState.step == TelegramAuthStep.READY) {
        TelegramChats(appState)
    } else {
        TelegramSignIn(appState)
    }
}

/**
 * Every step of signing in to Telegram, from application credentials through to registration.
 *
 * Shared by Analyze and by Settings rather than owned by either: signing out is on the Settings
 * card, and a sign-out that can only be undone by leaving for another screen is a trap.
 *
 * [boxed] is how one step sits on the page - a card of its own on Analyze, where it stands alone,
 * and a plain titled block in Settings, where it is already inside the Telegram card and a card
 * within a card reads as two unrelated things. Draws nothing once signed in.
 */
@Composable
internal fun ColumnScope.TelegramSignIn(appState: AppState, boxed: Boolean = true) {
    val scope = rememberCoroutineScope()
    var firstValue by remember(appState.telegramAuthState.step) { mutableStateOf("") }
    var secondValue by remember(appState.telegramAuthState.step) { mutableStateOf("") }
    val step = appState.telegramAuthState.step
    // Loose on the column only for the steps that draw no card, where the message is the whole of
    // what the screen has to say. Every step that does draw one carries it inside instead: above
    // the card it set this column 36dp below the one beside it once the panes sit side by side,
    // and two headings meant to read as one row did not. Once READY the chat card speaks for
    // itself, which is why nothing is drawn here at all.
    if (step == TelegramAuthStep.INITIALIZING ||
        step == TelegramAuthStep.LOGGING_OUT ||
        step == TelegramAuthStep.ERROR
    ) {
        AuthMessage(appState)
    }
    when (step) {
        TelegramAuthStep.API_CONFIGURATION -> {
            AuthCard("Telegram application", boxed, appState) {
                AuthField(firstValue, { firstValue = it }, "API ID")
                AuthField(secondValue, { secondValue = it }, "API hash", secret = true)
                Button(onClick = {
                    scope.launch {
                        appState.saveTelegramApiConfiguration(firstValue, secondValue)
                        secondValue = ""
                    }
                }) { Text("Initialize Telegram") }
                ApiCredentialsHelp()
            }
        }
        TelegramAuthStep.PHONE_NUMBER -> AuthCard("Sign in to Telegram", boxed, appState) {
            // Scanning is offered first: it is the shorter path on any device, and on a tablet
            // typing a phone number and then a code is the worst part of setting the app up.
            Button(onClick = {
                scope.launch { appState.startTelegramQrSignIn() }
            }) { Text("Sign in by QR code") }
            Text(
                "Or sign in with your phone number:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AuthField(firstValue, { firstValue = it }, "Phone number with country code")
            Button(onClick = {
                scope.launch { appState.submitTelegramPhone(firstValue) }
            }) { Text("Send verification code") }
            ApiCredentialsHelp()
        }
        TelegramAuthStep.VERIFICATION_CODE -> AuthCard("Verification code", boxed, appState) {
            AuthField(firstValue, { firstValue = it }, "Telegram code")
            Button(onClick = {
                scope.launch { appState.submitTelegramCode(firstValue) }
            }) { Text("Verify code") }
        }
        TelegramAuthStep.TWO_FACTOR_PASSWORD -> AuthCard("Two-step verification", boxed, appState) {
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
        TelegramAuthStep.EMAIL_ADDRESS -> AuthCard("Login email", boxed, appState) {
            AuthField(firstValue, { firstValue = it }, "Email address")
            Button(onClick = {
                scope.launch { appState.submitTelegramEmail(firstValue) }
            }) { Text("Send email code") }
        }
        TelegramAuthStep.EMAIL_CODE -> AuthCard("Email verification", boxed, appState) {
            AuthField(firstValue, { firstValue = it }, "Email code")
            Button(onClick = {
                scope.launch { appState.submitTelegramEmailCode(firstValue) }
            }) { Text("Verify email") }
        }
        TelegramAuthStep.REGISTRATION -> AuthCard("Finish registration", boxed, appState) {
            AuthField(firstValue, { firstValue = it }, "First name")
            AuthField(secondValue, { secondValue = it }, "Last name")
            Button(onClick = {
                scope.launch { appState.registerTelegram(firstValue, secondValue) }
            }) { Text("Register") }
        }
        TelegramAuthStep.OTHER_DEVICE_CONFIRMATION -> AuthCard("Scan with Telegram", boxed, appState) {
            val link = appState.telegramAuthState.link.orEmpty()
            if (link.isNotBlank()) {
                QrCode(link)
            }
            Text(
                "On a device already signed in to Telegram, open Settings, then Devices, then " +
                    "Link Desktop Device, and scan this code.",
            )
            ApiCredentialsHelp()
        }
        TelegramAuthStep.READY,
        TelegramAuthStep.INITIALIZING,
        TelegramAuthStep.LOGGING_OUT,
        TelegramAuthStep.ERROR -> Unit
    }
}

/** The chats a run reads from, and how many of them are picked. */
@Composable
private fun TelegramChats(appState: AppState) {
    val selectedCount = appState.channels.count(ChannelSelection::selected)
    val chats = appState.channels
    // The list, and the count that summarizes it. Being signed in and signing out are facts about
    // the account rather than steps of a run, so they sit in Settings under Telegram; here they
    // only competed with the chats for the top of the card.
    SectionCard(title = "Telegram", icon = Icons.Outlined.Forum) {
        if (chats.isNotEmpty()) {
            // Only the figure is coloured. The whole line in primary would read as a message about
            // something having gone right, where all it is saying is how many are ticked.
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(selectedCount.toString())
                    }
                    append(" of ${chats.size} chats selected")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Every chat is offered, broadcast channel or not: recommendations also arrive in groups
        // and direct messages, and hiding those decided for the user which sources were worth
        // reading.
        if (chats.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Forum,
                title = "No chats loaded",
                detail = "Refresh to pull your Telegram chat list onto this device.",
            )
        } else {
            // Bounded and scrolled in place: a long chat list otherwise pushes the whole run out
            // of reach.
            BoxWithConstraints {
                val columns = responsiveColumns(minColumnWidth = 300.dp, maxColumns = 3)
                Column(
                    Modifier
                        .heightIn(max = ChatListMaxHeight)
                        .scrollableColumn(),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    ResponsiveRows(chats, columns, spacing = Space.s) { chat, cardModifier ->
                        ChannelCard(chat, appState, chats, cardModifier)
                    }
                }
            }
        }
    }
}

/**
 * Where the two application credentials come from, for anyone who has to supply their own.
 *
 * Offered on the sign-in screens as well as the one with the fields: someone who has not hit the
 * wall yet is the person best placed to read it, and a help note that only appears once you are
 * stuck has already failed.
 */
@Composable
private fun ApiCredentialsHelp() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Where do the API ID and hash come from?",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InfoButton(ApiCredentialsNote)
    }
}

/**
 * The five steps and the caveat, as one sheet rather than a collapsing group of its own.
 *
 * It was a `SubSection`, which is already folded away and was therefore not the crowding this pass
 * was about - but it was a second thing on the screen that opens to explain something, on a screen
 * that now has one. Two affordances meaning "there is more to say here" is the drift a single
 * pattern exists to stop.
 */
private val ApiCredentialsNote = infoNote(
    "Where do the API ID and hash come from?",
    "This build has no application credentials of its own, so it needs a pair from " +
        "my.telegram.org. They are encrypted on this device.",
    "1. Open my.telegram.org and sign in with the phone number on your Telegram account.",
    "2. Enter the code Telegram sends. It arrives in the Telegram app, not by SMS.",
    "3. Choose API development tools.",
    "4. Give the app a title and a short name. Platform and description do not matter.",
    "5. It shows App api_id, a number, and App api_hash, 32 characters. Those are the two fields.",
    "They identify the application, not you - every third-party Telegram client registers a pair " +
        "and ships it, which is why a release of this app already has its own and never asks. " +
        "Treat the hash like a password: anyone holding both can act as this app.",
)

/**
 * One step of the sign-in flow.
 *
 * Boxed it is a card in its own right; unboxed it keeps the same heading and hairline but no
 * surface of its own, for the caller that has already drawn one around it.
 *
 * Carries the step's message under its heading. Both forms do, so the card and the block inside
 * Settings say the same thing in the same place.
 */
@Composable
private fun AuthCard(
    title: String,
    boxed: Boolean,
    appState: AppState,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (boxed) {
        SectionCard(title = title) {
            AuthMessage(appState)
            content()
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AuthMessage(appState)
            content()
        }
    }
}

/**
 * What the sign-in has to say right now: an instruction, or what went wrong.
 *
 * Drawn nothing at all when there is nothing to say, rather than an empty line - blank, it still
 * took a line's height and moved everything under it for no reason.
 */
@Composable
private fun AuthMessage(appState: AppState) {
    val state = appState.telegramAuthState
    if (state.message.isBlank()) return
    Text(
        state.message,
        color = if (state.step == TelegramAuthStep.ERROR) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
    )
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
private fun ChannelCard(
    channel: ChannelSelection,
    appState: AppState,
    allChannels: List<ChannelSelection>,
    modifier: Modifier = Modifier,
) {
    // Two chats can carry the same words and differ only by a trailing emoji, which reads as a
    // duplicate. When that happens the id is promoted so the difference is visible.
    val ambiguous = remember(allChannels, channel.id) {
        // A chat with no title has nothing to confuse, so blank names never count as a clash.
        channel.baseName().isNotBlank() &&
            allChannels.count { it.baseName() == channel.baseName() } > 1
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = channel.selected,
                role = Role.Checkbox,
                onClick = { appState.toggleChannel(channel) },
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (channel.selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                // A step above the card behind it. At surfaceContainer the two were the same
                // colour with no hairline between them, so a dozen chats read as one slab.
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(Space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            KindBadge(channel)
            Column(Modifier.weight(1f)) {
                // Not truncated: chats can differ only by a trailing emoji, so cutting the name
                // short can make two different chats look identical.
                Text(channel.displayName, style = MaterialTheme.typography.titleSmall)
                // Stated for every chat, channels included. Shown only on the ones that were not
                // channels, it read as a warning about those rather than as what kind of chat this
                // is - and the line it replaces, the chat's id, is a number no one here has ever
                // needed to read.
                Text(
                    channel.kind.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (channel.selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                // The one place the id still earns its keep: two rows carrying the same words and
                // the same kind cannot be told apart by anything else on the card.
                if (ambiguous) {
                    Text(
                        "Another chat has the same name · ${channel.id}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = TabularFigures,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Checkbox(checked = channel.selected, onCheckedChange = null)
        }
    }
}

/**
 * The chat's kind as a glyph, at the head of its row.
 *
 * Selection is easiest to read down the left edge, where every row starts, rather than from a tick
 * at the right; the badge carries it as a fill so the checkbox is confirming what the row already
 * says. It repeats the kind named beside it on purpose - the word is what is precise, the glyph is
 * what makes a list of a dozen chats sortable at a glance.
 */
@Composable
private fun KindBadge(channel: ChannelSelection) {
    Surface(
        shape = CircleShape,
        color = if (channel.selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Icon(
            channel.kind.icon(),
            contentDescription = null,
            tint = if (channel.selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(BadgePadding).size(IconSize.Inline),
        )
    }
}

/** Big enough to read as an avatar rather than a stray icon, small enough for a two-line row. */
private val BadgePadding = 8.dp

private fun ChatKind.icon(): ImageVector = when (this) {
    ChatKind.CHANNEL -> Icons.Outlined.Campaign
    ChatKind.SUPERGROUP -> Icons.Outlined.Groups
    ChatKind.GROUP -> Icons.Outlined.Group
    ChatKind.DIRECT -> Icons.Outlined.Person
}

/** Name reduced to its letters and digits, so emoji and punctuation do not hide a clash. */
private fun ChannelSelection.baseName(): String =
    displayName.filter(Char::isLetterOrDigit).lowercase()

/** Tall enough to browse, short enough that the run stays on screen. */
private val ChatListMaxHeight = 320.dp
