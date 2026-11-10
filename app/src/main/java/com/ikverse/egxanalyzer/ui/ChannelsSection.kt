package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.ChatKind
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitCheckbox
import com.ikverse.egxanalyzer.ui.kit.KitRule
import com.ikverse.egxanalyzer.ui.kit.KitTextField
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.theme.palette
import kotlinx.coroutines.launch

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
 * Which of the four steps a Telegram state belongs to, counted from one: the application, signing
 * in, proving it is you, and choosing the chats. Null for the states that are not a step at all -
 * starting up, signing out, an error - where there is nothing to count.
 *
 * The third step holds every way Telegram asks for proof - a code, a password, an email and its
 * code, and registering a new account - because to the reader they are all the same moment: the
 * account is known, and it wants something more before it opens.
 */
internal fun TelegramAuthStep.signInStep(): Int? = when (this) {
    TelegramAuthStep.API_CONFIGURATION -> 1
    TelegramAuthStep.PHONE_NUMBER, TelegramAuthStep.OTHER_DEVICE_CONFIRMATION -> 2
    TelegramAuthStep.VERIFICATION_CODE,
    TelegramAuthStep.TWO_FACTOR_PASSWORD,
    TelegramAuthStep.EMAIL_ADDRESS,
    TelegramAuthStep.EMAIL_CODE,
    TelegramAuthStep.REGISTRATION -> 3
    TelegramAuthStep.READY -> SignInSteps + 1
    TelegramAuthStep.INITIALIZING, TelegramAuthStep.LOGGING_OUT, TelegramAuthStep.ERROR -> null
}

private val StepNames = listOf("Telegram application", "Sign in", "Verification", "Choose the chats to read")

/**
 * How far through signing in the reader is: four segments, the done ones ink, the current one
 * cobalt, and a line saying which. Past the last, every segment is done and it says so.
 */
@Composable
internal fun SignInProgress(step: Int, modifier: Modifier = Modifier) {
    val p = palette
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().semantics {
                contentDescription = if (step > SignInSteps) "Signed in" else "Step $step of $SignInSteps"
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (1..SignInSteps).forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                i < step -> p.ink
                                i == step -> p.hero
                                else -> p.line
                            },
                        ),
                )
            }
        }
        Text(
            if (step > SignInSteps) "Signed in" else "Step $step of $SignInSteps · ${StepNames[step - 1].lowercase()}",
            style = MaterialTheme.typography.labelMedium,
            color = p.muted,
        )
    }
}

/**
 * Every step of signing in to Telegram, from application credentials through to registration.
 *
 * Shared by Analyze and by Settings rather than owned by either: signing out is on Settings, and a
 * sign-out that can only be undone by leaving for another screen is a trap.
 *
 * Drawn as the 5.0 flow: the four steps as a bar, the ones behind as ticked rows, the one in hand on
 * a cobalt tile, and the ones ahead faded. [boxed] is kept for its callers and no longer changes the
 * drawing - every step is a tile of its own in both places. Draws nothing once signed in.
 */
@Composable
internal fun ColumnScope.TelegramSignIn(appState: AppState, @Suppress("UNUSED_PARAMETER") boxed: Boolean = true) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var firstValue by remember(appState.telegramAuthState.step) { mutableStateOf("") }
    var secondValue by remember(appState.telegramAuthState.step) { mutableStateOf("") }
    // Outlives the step, unlike the two above: the phone number is still needed once the code
    // screen has replaced the one it was typed on, for Resend code to ask Telegram for a new one
    // at the same number rather than an empty one.
    var submittedPhone by remember { mutableStateOf("") }
    val step = appState.telegramAuthState.step
    val number = step.signInStep()
    // Loose on the column only for the states that are not a step, where the message is the whole
    // of what the screen has to say. Every step carries it inside its own tile instead.
    if (number == null) {
        Tile(Modifier.fillMaxWidth()) { AuthMessage(appState) }
        return
    }
    if (number > SignInSteps) return

    SignInProgress(number)
    (1 until number).forEach { done -> StepRow(done, StepState.DONE) }

    when (step) {
        TelegramAuthStep.API_CONFIGURATION -> AuthCard("Telegram application", appState, startOver = false) {
            val save = {
                scope.launch {
                    appState.saveTelegramApiConfiguration(firstValue, secondValue)
                    secondValue = ""
                }
                Unit
            }
            AuthField(
                firstValue, { firstValue = it }, "API ID",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            )
            AuthField(secondValue, { secondValue = it }, "API hash", secret = true, onImeAction = save)
            StepButton("Initialize Telegram", save)
            ApiCredentialsHelp()
        }
        TelegramAuthStep.PHONE_NUMBER -> AuthCard("Sign in to Telegram", appState) {
            // Scanning is offered first: it is the shorter path on any device, and on a tablet
            // typing a phone number and then a code is the worst part of setting the app up.
            KitButton(
                "Sign in by QR code",
                onClick = { scope.launch { appState.startTelegramQrSignIn() } },
                kind = ButtonKind.ON_TONE,
                size = ButtonSize.SMALL,
            )
            OrDivider("or with your phone number")
            val send = {
                submittedPhone = firstValue
                scope.launch { appState.submitTelegramPhone(firstValue) }
                Unit
            }
            AuthField(firstValue, { firstValue = it }, "Phone number with country code", keyboardType = KeyboardType.Phone, onImeAction = send)
            StepButton("Send verification code", send)
        }
        TelegramAuthStep.VERIFICATION_CODE -> AuthCard("Verification code", appState) {
            val verify = { scope.launch { appState.submitTelegramCode(firstValue) }; Unit }
            AuthField(firstValue, { firstValue = it }, "Telegram code", keyboardType = KeyboardType.Number, onImeAction = verify)
            StepButton("Verify code", verify)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Telegram resends to the number already on file - asking again would be typing
                // the same digits a second time for no reason the reader can see.
                KitButton(
                    "Resend code",
                    onClick = { scope.launch { appState.submitTelegramPhone(submittedPhone) } },
                    kind = ButtonKind.ON_TONE,
                    size = ButtonSize.SMALL,
                )
                KitButton(
                    "Change number",
                    onClick = { scope.launch { appState.logoutTelegram() } },
                    kind = ButtonKind.ON_TONE,
                    size = ButtonSize.SMALL,
                )
            }
        }
        TelegramAuthStep.TWO_FACTOR_PASSWORD -> AuthCard("Two-step verification", appState) {
            appState.telegramAuthState.hint?.takeIf(String::isNotBlank)?.let {
                Text("Hint: $it", style = MaterialTheme.typography.bodyMedium)
            }
            val submit = {
                scope.launch {
                    appState.submitTelegramPassword(firstValue)
                    firstValue = ""
                }
                Unit
            }
            AuthField(firstValue, { firstValue = it }, "Telegram password", secret = true, onImeAction = submit)
            StepButton("Continue", submit)
        }
        TelegramAuthStep.EMAIL_ADDRESS -> AuthCard("Login email", appState) {
            val send = { scope.launch { appState.submitTelegramEmail(firstValue) }; Unit }
            AuthField(firstValue, { firstValue = it }, "Email address", keyboardType = KeyboardType.Email, onImeAction = send)
            StepButton("Send email code", send)
        }
        TelegramAuthStep.EMAIL_CODE -> AuthCard("Email verification", appState) {
            val verify = { scope.launch { appState.submitTelegramEmailCode(firstValue) }; Unit }
            AuthField(firstValue, { firstValue = it }, "Email code", keyboardType = KeyboardType.Number, onImeAction = verify)
            StepButton("Verify email", verify)
        }
        TelegramAuthStep.REGISTRATION -> AuthCard("Finish registration", appState) {
            val register = {
                scope.launch { appState.registerTelegram(firstValue, secondValue) }
                Unit
            }
            AuthField(
                firstValue, { firstValue = it }, "First name",
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            )
            AuthField(secondValue, { secondValue = it }, "Last name", onImeAction = register)
            StepButton("Register", register)
        }
        TelegramAuthStep.OTHER_DEVICE_CONFIRMATION -> AuthCard("Scan with Telegram", appState) {
            val link = appState.telegramAuthState.link.orEmpty()
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (link.isNotBlank()) {
                    // Pure black on white whatever the theme: a code the camera cannot read in the
                    // dark is not a code.
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(10.dp),
                    ) { QrCode(link, size = 132.dp) }
                }
                Text(
                    "On a device already signed in to Telegram, open Settings, then Devices, then " +
                        "Link Desktop Device, and scan this code.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            KitButton(
                "Use phone number instead",
                onClick = { scope.launch { appState.logoutTelegram() } },
                kind = ButtonKind.ON_TONE,
                size = ButtonSize.SMALL,
            )
        }
        TelegramAuthStep.READY,
        TelegramAuthStep.INITIALIZING,
        TelegramAuthStep.LOGGING_OUT,
        TelegramAuthStep.ERROR -> Unit
    }

    (number + 1..SignInSteps).forEach { ahead -> StepRow(ahead, StepState.AHEAD) }
}

private enum class StepState { DONE, AHEAD }

/** A step behind the reader, ticked, or one ahead, faded and numbered. */
@Composable
private fun StepRow(step: Int, state: StepState) {
    val p = palette
    Tile(
        Modifier.fillMaxWidth().alpha(if (state == StepState.AHEAD) 0.55f else 1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(
                        if (state == StepState.DONE) Modifier.background(p.gain)
                        else Modifier.border(BorderStroke(1.5.dp, p.ink), CircleShape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (state == StepState.DONE) {
                    Icon(Icons.Outlined.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text("$step", style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures), fontWeight = FontWeight.Bold)
                }
            }
            Text(StepNames[step - 1], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
    }
}

/** The step's own submit, full width on the cobalt tile: the one thing the step is for. */
@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    KitButton(label, onClick = onClick, modifier = Modifier.fillMaxWidth(), size = ButtonSize.MEDIUM)
}

@Composable
private fun OrDivider(text: String) {
    val tone = LocalContentColor.current.copy(alpha = 0.35f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KitRule(Modifier.weight(1f), color = tone)
        Text(text, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.85f))
        KitRule(Modifier.weight(1f), color = tone)
    }
}

/** The chats a run reads from, and how many of them are picked. */
@Composable
private fun TelegramChats(appState: AppState) {
    val p = palette
    val selectedCount = appState.channels.count(ChannelSelection::selected)
    // Ticked first, so the chats a run actually reads from are the ones seen without scrolling -
    // the app's own ordering otherwise, stable within each of the two groups. Sorted for display
    // only: `appState.channels` itself keeps Telegram's own order for everything that reads it.
    val chats = remember(appState.channels) { appState.channels.sortedByDescending(ChannelSelection::selected) }
    Tile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Chats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            if (chats.isNotEmpty()) {
                Text(
                    "$selectedCount of ${chats.size} selected",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                    color = p.muted,
                )
            }
        }
        // Every chat is offered, broadcast channel or not: recommendations also arrive in groups
        // and direct messages, and hiding those decided for the user which sources were worth
        // reading.
        if (chats.isEmpty()) {
            Text(
                "No chats loaded. Refresh to pull your Telegram chat list onto this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            // Bounded and scrolled in place: a long chat list otherwise pushes the whole run out
            // of reach.
            BoxWithConstraints(Modifier.padding(top = 8.dp)) {
                val columns = responsiveColumns(minColumnWidth = 300.dp, maxColumns = 3)
                val listScroll = rememberScrollState()
                val fadeBand = with(LocalDensity.current) { ListEdgeFade.toPx() }
                Column(
                    Modifier
                        .heightIn(max = ChatListMaxHeight)
                        .fadeEdges(
                            fade = ListEdgeFade,
                            top = { listScroll.value / fadeBand },
                            bottom = { (listScroll.maxValue - listScroll.value) / fadeBand },
                        )
                        .fadingScrollbar(listScroll)
                        .verticalScroll(listScroll),
                ) {
                    ResponsiveRows(chats, columns, spacing = 0.dp) { chat, cardModifier ->
                        ChannelRow(chat, appState, chats, cardModifier)
                    }
                }
            }
        }
    }
}

/**
 * Where the two application credentials come from, for anyone who has to supply their own.
 *
 * Offered on the step with the fields, as a line with its question mark: someone who has not hit
 * the wall yet is the person best placed to read it.
 */
@Composable
private fun ApiCredentialsHelp() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Where do the API ID and hash come from?",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.85f),
        )
        InfoButton(ApiCredentialsNote, tint = LocalContentColor.current)
    }
}

/**
 * The five steps and the caveat, as one sheet rather than a collapsing group of its own: one thing
 * on the screen that opens to explain something, not two.
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
 * The step in hand, on a cobalt tile: its name, what Telegram has to say right now, its fields and
 * its button, and a way to abandon it.
 */
@Composable
private fun AuthCard(
    title: String,
    appState: AppState,
    /**
     * Offers a way to abandon this step and go back to entering a phone number.
     *
     * Off only for the credentials step: nothing has been submitted to Telegram yet there, so
     * there is no attempt to start over from. Every step reached after it - a code sent, a QR
     * shown, a password asked for - can leave the reader stuck if what they typed was wrong and
     * the only way out was to force-close the app, which is what this replaces.
     */
    startOver: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Tile(Modifier.fillMaxWidth(), tone = TileTone.HERO) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            AuthMessage(appState)
            content()
            if (startOver) StartOverButton(appState)
        }
    }
}

/**
 * Abandons whatever step of sign-in is showing and goes back to entering a phone number.
 *
 * The same action a phone number screen's "Change number" and a QR screen's "Use phone number
 * instead" call, under the name that fits every other step: `logoutTelegram` is TDLib's only way
 * back to `WaitPhoneNumber` from any step short of it, signed in or not, so it is what all three
 * do. None of them touch a report, a trade, a rule or a setting - the record this app keeps is
 * never Telegram's to hold.
 */
@Composable
private fun StartOverButton(appState: AppState) {
    val scope = rememberCoroutineScope()
    Text(
        "Start over",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(Kit.Capsule)
            .selectable(selected = false, role = Role.Button) { scope.launch { appState.logoutTelegram() } }
            .padding(vertical = 8.dp),
    )
}

/**
 * What the sign-in has to say right now: an instruction, or what went wrong.
 *
 * Drawn nothing at all when there is nothing to say, rather than an empty line.
 */
@Composable
private fun AuthMessage(appState: AppState) {
    val state = appState.telegramAuthState
    if (state.message.isBlank()) return
    if (state.isError) {
        // On a tile of its own colour rather than red text on cobalt, which does not read.
        Text(
            state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.ink,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.alert).padding(10.dp),
        )
    } else {
        Text(state.message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    /** Next moves to the field below; Done, the default, submits the step. */
    imeAction: ImeAction = ImeAction.Done,
    /** What the keyboard's own action key does - the step's submit, or moving on to the next field. */
    onImeAction: () -> Unit = {},
) {
    KitTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        figure = keyboardType == KeyboardType.Number || keyboardType == KeyboardType.Phone,
        keyboardType = keyboardType,
        password = secret,
        imeAction = imeAction,
        onImeAction = onImeAction,
    )
}

/** One chat, ticked or not: its picture or its kind, its name, and a checkbox. */
@Composable
private fun ChannelRow(
    channel: ChannelSelection,
    appState: AppState,
    allChannels: List<ChannelSelection>,
    modifier: Modifier = Modifier,
) {
    val p = palette
    // Two chats can carry the same words and differ only by a trailing emoji, which reads as a
    // duplicate. When that happens the id is promoted so the difference is visible.
    val ambiguous = remember(allChannels, channel.id) {
        // A chat with no title has nothing to confuse, so blank names never count as a clash.
        channel.baseName().isNotBlank() && allChannels.count { it.baseName() == channel.baseName() } > 1
    }
    Column(modifier.fillMaxWidth()) {
        KitRule()
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = channel.selected, role = Role.Checkbox, onClick = { appState.toggleChannel(channel) })
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChatAvatar(channel)
            Column(Modifier.weight(1f)) {
                // Not truncated: chats can differ only by a trailing emoji, so cutting the name
                // short can make two different chats look identical.
                Text(channel.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(channel.kind.label, style = MaterialTheme.typography.labelSmall, color = p.muted)
                // The one place the id still earns its keep: two rows carrying the same words and
                // the same kind cannot be told apart by anything else on the row.
                if (ambiguous) {
                    Text(
                        "Another chat has the same name · ${channel.id}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                        color = p.muted,
                    )
                }
            }
            KitCheckbox(checked = channel.selected, onCheckedChange = { appState.toggleChannel(channel) }, label = channel.displayName)
        }
    }
}

/**
 * The head of a chat row: the chat's own picture, where Telegram has one, and the chat's kind as a
 * glyph on a tile colour where it has not.
 */
@Composable
private fun ChatAvatar(channel: ChannelSelection) {
    val p = palette
    val photo = rememberTelegramImage(channel.photoPath, maxPixels = AvatarPixels)
    Box(
        Modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(if (channel.selected) p.highlight else p.raised),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(channel.kind.icon(), contentDescription = null, tint = p.ink, modifier = Modifier.size(18.dp))
        }
    }
}

private val AvatarSize = 36.dp

/**
 * Telegram's small profile photo is 160px square, so this decodes it whole.
 *
 * Sampling it down to fit a 36dp box lands under the pixels a 3x screen actually draws, and a
 * blurred avatar reads as a broken one rather than as a small one.
 */
private const val AvatarPixels = 256

private fun ChatKind.icon(): ImageVector = when (this) {
    ChatKind.CHANNEL -> Icons.Outlined.Campaign
    ChatKind.SUPERGROUP -> Icons.Outlined.Groups
    ChatKind.GROUP -> Icons.Outlined.Group
    ChatKind.DIRECT -> Icons.Outlined.Person
}

/** Name reduced to its letters and digits, so emoji and punctuation do not hide a clash. */
private fun ChannelSelection.baseName(): String = displayName.filter(Char::isLetterOrDigit).lowercase()

/** Tall enough to browse, short enough that the run stays on screen. */
private val ChatListMaxHeight = 320.dp

/** How deep the chat list's foot fades while there is more under it. */
private val ListEdgeFade = 24.dp
