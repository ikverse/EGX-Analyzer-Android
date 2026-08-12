package com.ikverse.egxanalyzer.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.ChatKind
import com.ikverse.egxanalyzer.model.cleanChannelName
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.TelegramAuthState
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.TelegramChat
import com.ikverse.egxanalyzer.model.TelegramSourceBatch
import com.ikverse.egxanalyzer.BuildConfig
import com.ikverse.egxanalyzer.data.SyncedRun
import com.ikverse.egxanalyzer.data.Tombstone
import org.json.JSONObject
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateClosing
import dev.g000sha256.tdl.dto.AuthorizationStateLoggingOut
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitEmailAddress
import dev.g000sha256.tdl.dto.AuthorizationStateWaitEmailCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitOtherDeviceConfirmation
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitRegistration
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.EmailAddressAuthenticationCode
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.PhoneNumberAuthenticationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TelegramRepository(
    private val context: Context,
    private val secretStore: NamedSecretStore,
) {
    private val preferences =
        context.getSharedPreferences("egx_telegram_settings", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client = TdlClient.create()
    private val clientJobs = mutableListOf<Job>()
    private val chatCache = linkedMapOf<Long, Chat>()
    private val _authState = MutableStateFlow(TelegramAuthState())
    private val _chats = MutableStateFlow<List<TelegramChat>>(emptyList())

    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()
    val chats: StateFlow<List<TelegramChat>> = _chats.asStateFlow()

    init {
        startClientCollectors()
    }

    private fun startClientCollectors() {
        clientJobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.authorizationStateUpdates.collect { update ->
                handleAuthorizationState(update.authorizationState)
            }
        }
        clientJobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.newChatUpdates.collect { update ->
                chatCache[update.chat.id] = update.chat
                publishChats()
            }
        }
        clientJobs += scope.launch {
            runCatching { client.getAuthorizationState().requireValue<AuthorizationState>() }
                .onSuccess { handleAuthorizationState(it) }
                .onFailure { showError(it) }
        }
    }

    suspend fun saveApiConfiguration(apiIdText: String, apiHashText: String) {
        val apiId = apiIdText.trim().toIntOrNull()
            ?: return setError("Telegram API ID must be numeric.")
        if (apiId <= 0 || apiHashText.isBlank()) return setError("Enter the Telegram API ID and API hash.")
        preferences.edit().putInt(KEY_API_ID, apiId).apply()
        secretStore.saveSecret(KEY_API_HASH, apiHashText.trim().toCharArray())
        initializeTdlib(apiId, apiHashText.trim())
    }

    suspend fun resetApiConfiguration() {
        val closingClient = client
        setState(TelegramAuthStep.INITIALIZING, "Resetting Telegram application credentials…")
        val closed = withTimeoutOrNull(CLIENT_CLOSE_TIMEOUT_MS) {
            coroutineScope {
                val closeUpdate = async(start = CoroutineStart.UNDISPATCHED) {
                    closingClient.authorizationStateUpdates.firstOrNull {
                        it.authorizationState is AuthorizationStateClosed
                    }
                }
                closingClient.close().requireValue<Any?>()
                closeUpdate.await()
            }
            true
        } == true
        if (!closed) {
            return setError("Telegram could not reset safely. Restart the app and try again.")
        }

        clientJobs.forEach(Job::cancel)
        clientJobs.clear()
        preferences.edit().remove(KEY_API_ID).apply()
        secretStore.removeSecret(KEY_API_HASH)
        chatCache.clear()
        _chats.value = emptyList()
        client = TdlClient.create()
        startClientCollectors()
    }

    /**
     * Signs in by showing a QR code for an already-signed-in Telegram to scan.
     *
     * Nothing to type, which matters most on a tablet. Telegram answers with a `tg://login` link,
     * which the screen renders as the code; scanning it there completes the sign-in here.
     */
    suspend fun startQrSignIn() {
        setState(TelegramAuthStep.INITIALIZING, "Asking Telegram for a sign-in code…")
        execute { client.requestQrCodeAuthentication(longArrayOf()) }
    }

    suspend fun submitPhoneNumber(phoneNumber: String) {
        if (phoneNumber.isBlank()) return setError("Enter your phone number with country code.")
        execute {
            client.setAuthenticationPhoneNumber(
                phoneNumber.trim(),
                PhoneNumberAuthenticationSettings(
                    allowFlashCall = false,
                    allowMissedCall = false,
                    isCurrentPhoneNumber = true,
                    hasUnknownPhoneNumber = false,
                    allowSmsRetrieverApi = false,
                    firebaseAuthenticationSettings = null,
                    authenticationTokens = emptyArray(),
                ),
            )
        }
    }

    suspend fun submitVerificationCode(code: String) =
        execute { client.checkAuthenticationCode(code.trim()) }

    suspend fun submitPassword(password: String) =
        execute { client.checkAuthenticationPassword(password) }

    suspend fun submitEmailAddress(email: String) =
        execute { client.setAuthenticationEmailAddress(email.trim()) }

    suspend fun submitEmailCode(code: String) =
        execute { client.checkAuthenticationEmailCode(EmailAddressAuthenticationCode(code.trim())) }

    suspend fun register(firstName: String, lastName: String) =
        execute { client.registerUser(firstName.trim(), lastName.trim(), false) }

    suspend fun logout() {
        execute { client.logOut() }
        chatCache.clear()
        _chats.value = emptyList()
    }

    /**
     * Loads the whole chat list.
     *
     * `loadChats` pulls one batch from the server and answers 404 once there is nothing left, so a
     * single call returns only whatever was already cached - which quietly dropped groups the user
     * really is in. Looping until that 404 is how TDLib expects the list to be exhausted.
     */
    suspend fun refreshChats() {
        if (_authState.value.step != TelegramAuthStep.READY) return
        while (true) {
            val more = client.loadChats(ChatListMain(), CHAT_PAGE_SIZE)
            if (more is TdlResult.Failure) break
        }
        val chatIds = client.getChats(ChatListMain(), MAX_CHATS)
            .requireValue<dev.g000sha256.tdl.dto.Chats>()
        chatIds.chatIds.forEach { id ->
            chatCache[id] = client.getChat(id).requireValue<Chat>()
        }
        publishChats()
    }

    suspend fun collectSources(
        channelIds: List<Long>,
        start: Instant,
        endExclusive: Instant,
        contentTypes: Set<AnalysisContentType>,
    ): TelegramSourceBatch {
        require(_authState.value.step == TelegramAuthStep.READY) { "Sign in to Telegram first." }
        val inputs = mutableListOf<AnalysisInput>()
        val traces = mutableListOf<SourceTrace>()
        val collected = CollectedImages()
        var examined = 0
        var silentChats = 0
        channelIds.forEach { chatId ->
            val chat = chatCache[chatId] ?: client.getChat(chatId).requireValue<Chat>()
            var fromMessageId = 0L
            var keepLoading = true
            var emptyPages = 0
            var readHere = 0
            while (keepLoading && readHere < MAX_MESSAGES_PER_CHAT) {
                val history = client.getChatHistory(
                    chatId = chatId,
                    fromMessageId = fromMessageId,
                    offset = 0,
                    limit = HISTORY_PAGE_SIZE,
                    onlyLocal = false,
                ).requireValue<dev.g000sha256.tdl.dto.Messages>()
                val messages = history.messages.filterNotNull()
                if (messages.isEmpty()) {
                    // An empty page is not the same as the end of the chat. The first request for a
                    // chat only asks the server to fetch it, and answers with nothing while that is
                    // in flight - which read as "no messages" and made the preview come back empty
                    // until it was pressed enough times to warm the cache. Ask again before
                    // concluding there is nothing there.
                    if (emptyPages++ >= HISTORY_RETRIES) break
                    delay(HISTORY_RETRY_DELAY_MS)
                    continue
                }
                emptyPages = 0
                examined += messages.size
                readHere += messages.size
                messages.forEach { message ->
                    val publishedAt = Instant.ofEpochSecond(message.date.toLong())
                    when {
                        publishedAt.isBefore(start) -> keepLoading = false
                        publishedAt.isBefore(endExclusive) ->
                            appendMessage(chat, message, contentTypes, inputs, traces, collected)
                    }
                }
                fromMessageId = messages.last().id
            }
            if (emptyPages > HISTORY_RETRIES) silentChats++
        }
        return TelegramSourceBatch(
            inputs = inputs,
            traces = traces.distinctBy(SourceTrace::sourceId),
            examined = examined,
            silentChats = silentChats,
        )
    }

    private suspend fun appendMessage(
        chat: Chat,
        message: Message,
        contentTypes: Set<AnalysisContentType>,
        inputs: MutableList<AnalysisInput>,
        traces: MutableList<SourceTrace>,
        collected: CollectedImages,
    ) {
        val sourceId = "tg:${chat.id}:${message.id}"
        val content = message.content
        var repost = false
        val preview = when (content) {
            is MessageText -> {
                if (AnalysisContentType.TEXT in contentTypes) {
                    inputs += AnalysisInput.Text(sourceId, content.text.text)
                }
                content.text.text
            }
            is MessagePhoto -> {
                if (AnalysisContentType.IMAGES in contentTypes) {
                    val photo = content.photo.sizes.maxByOrNull { it.width * it.height }?.photo
                    if (photo != null) {
                        val local = download(photo.id)
                        // The same picture posted twice - reposted, or forwarded back into the
                        // channel - downloads to one file. Sending it again bills a second image
                        // and has the model read every card on it twice, which is where a report
                        // full of duplicate rows comes from.
                        repost = !collected.accept(local.path)
                        if (!repost) {
                            inputs += AnalysisInput.Image(sourceId, Uri.fromFile(local), "image/jpeg")
                        }
                    }
                }
                // A caption belongs to its photo rather than being a text source of its own, so
                // it travels with the image whatever the content-type choice. Without it a card
                // captioned "توصية سابقة" reaches the model stripped of the one thing that says
                // it is a follow-up on an earlier call rather than a new one. By the same token a
                // repost's caption stays behind: its picture is not in the request, and a caption
                // with no card under it is the model's problem to guess at.
                val caption = content.caption.text
                if (caption.isNotBlank() && !repost && AnalysisContentType.IMAGES in contentTypes) {
                    inputs += AnalysisInput.Text(sourceId, caption)
                }
                caption.ifBlank { "Photo" }
            }
            is MessageVoiceNote -> {
                if (AnalysisContentType.AUDIO in contentTypes) {
                    val voice = content.voiceNote
                    val local = download(voice.voice.id)
                    inputs += AnalysisInput.Voice(
                        sourceId = sourceId,
                        uri = Uri.fromFile(local),
                        mimeType = voice.mimeType,
                        durationMilliseconds = voice.duration * 1_000L,
                    )
                }
                // As with photos: the caption is the voice note's own label, not separate text.
                val caption = content.caption.text
                if (caption.isNotBlank() && AnalysisContentType.AUDIO in contentTypes) {
                    inputs += AnalysisInput.Text(sourceId, caption)
                }
                caption.ifBlank { "Voice message" }
            }
            else -> return
        }
        // A repost contributes no input of its own, but it is still a message that was read, and
        // the source list is where that shows.
        if (repost || inputs.any { it.sourceId == sourceId }) {
            traces += SourceTrace(
                sourceId = sourceId,
                channelId = chat.id,
                channelName = cleanChannelName(chat.title),
                messageId = message.id,
                timestamp = Instant.ofEpochSecond(message.date.toLong()),
                contentType = when (content) {
                    is MessageText -> AnalysisContentType.TEXT
                    is MessagePhoto -> AnalysisContentType.IMAGES
                    else -> AnalysisContentType.AUDIO
                },
                preview = preview.asPreview(),
            )
        }
    }

    /**
     * The private channel this app keeps its reports in, created once and remembered.
     *
     * A channel of its own rather than Saved Messages: sync traffic should not be mixed in with
     * whatever else someone keeps there, and a named chat can be found, muted or deleted without
     * touching anything else.
     */
    private suspend fun syncChatId(): Long = syncChatLock.withLock { resolveSyncChat() }

    /**
     * One caller at a time may go looking for the channel.
     *
     * Publishing is automatic now - a recorded trade and a finished run can reach here at the same
     * moment - and two callers finding no channel would each create one. That is exactly how three
     * duplicates ended up in the owner's Telegram; the lock is what stops it happening again.
     */
    private val syncChatLock = Mutex()

    private suspend fun resolveSyncChat(): Long {
        preferences.getLong(KEY_SYNC_CHAT, 0L).takeIf { it != 0L }?.let { stored ->
            // Confirm it still exists; a chat deleted on another device must not swallow uploads.
            if (runCatching { client.getChat(stored).requireValue<Chat>() }.isSuccess) return stored
        }
        // Ask Telegram whether the channel already exists before making one. Without this every
        // device created its own and none of them ever met: the phone uploaded six reports and the
        // tablet, looking at a channel of its own, reported itself already in sync with nothing.
        // Where uploads go: the busiest of the duplicates, so everything converges on one channel
        // rather than splitting further, and deterministically when they are all empty.
        val existing = findSyncChats()
        if (existing.isNotEmpty()) {
            val busiest = existing.maxByOrNull { reportsIn(it).size } ?: existing.min()
            preferences.edit().putLong(KEY_SYNC_CHAT, busiest).apply()
            return busiest
        }
        val created = client.createNewSupergroupChat(
            SYNC_CHAT_TITLE,
            false,
            true,
            "Reports from EGX Analyzer. Created by the app; safe to mute.",
            null,
            0,
            false,
        ).requireValue<Chat>()
        preferences.edit().putLong(KEY_SYNC_CHAT, created.id).apply()
        return created.id
    }

    /**
     * Every channel by this name, because there can be more than one.
     *
     * Two devices that both created one before either could find the other leave duplicates, and
     * picking whichever came back first meant a device could adopt the empty one and report itself
     * in sync while six reports sat in the other. Reading them all makes that self-correcting.
     */
    private suspend fun findSyncChats(): List<Long> {
        // From the loaded chat list rather than a server search. searchChatsOnServer returned
        // nothing for these - they are private supergroups with no username - and a failed search
        // silently read as "none exist", so every attempt created another channel. Three of them
        // before this was caught. The list the app already holds cannot lie in that direction.
        if (chatCache.isEmpty()) runCatching { refreshChats() }
        return chatCache.values
            .filter { it.title == SYNC_CHAT_TITLE }
            .map(Chat::id)
    }

    /**
     * Every report anywhere under this name, by the id its file name carries.
     *
     * Read across every duplicate: a report is worth having whichever channel it landed in, and a
     * device that once uploaded to a channel of its own should not lose those reports now.
     */
    suspend fun listSyncedReports(): Map<String, Int> {
        val target = syncChatId()
        val chats = (findSyncChats() + target).distinct()
        return chats.fold(linkedMapOf()) { all, chat ->
            reportsIn(chat).forEach { (id, fileId) -> all.putIfAbsent(id, fileId) }
            all
        }
    }

    private suspend fun reportsIn(chatId: Long): Map<String, Int> =
        contentsOf(chatId).reports.mapValues { (_, entry) -> entry.fileId }

    /** One file in the channel: enough to download it, and enough to delete it. */
    private data class ChannelFile(val fileId: Int, val messageId: Long)

    private data class ChannelContents(
        val reports: Map<String, ChannelFile>,
        val tombstones: Map<String, ChannelFile>,
        /** Every revision of every rule, newest first, because the merge needs them all. */
        val ruleRevisions: List<ChannelFile>,
        /** Every revision of every position, for the same reason. */
        val positionRevisions: List<ChannelFile>,
        /**
         * Every settings revision, by file name.
         *
         * By name rather than as a list, because the name carries both facts the merge decides on -
         * when they were written and by which device. Settings change far more often than they are
         * read, so downloading every revision to find the newest would grow into the slowest part
         * of a sync for an answer one file name already gives.
         */
        val settingsRevisions: Map<String, ChannelFile>,
        /** Every generated prompt, by id. A prompt never changes, so one copy is the whole story. */
        val promptVersions: Map<String, ChannelFile>,
    )

    private suspend fun contentsOf(chatId: Long): ChannelContents {
        val reports = linkedMapOf<String, ChannelFile>()
        val tombstones = linkedMapOf<String, ChannelFile>()
        val ruleRevisions = mutableListOf<ChannelFile>()
        val positionRevisions = mutableListOf<ChannelFile>()
        val settingsRevisions = linkedMapOf<String, ChannelFile>()
        val promptVersions = linkedMapOf<String, ChannelFile>()
        var fromMessageId = 0L
        while (true) {
            val page = client.getChatHistory(chatId, fromMessageId, 0, HISTORY_PAGE_SIZE, false)
                .requireValue<dev.g000sha256.tdl.dto.Messages>()
            val messages = page.messages.filterNotNull()
            if (messages.isEmpty()) break
            messages.forEach { message ->
                val document = (message.content as? dev.g000sha256.tdl.dto.MessageDocument)?.document
                val name = document?.fileName ?: return@forEach
                val file = ChannelFile(document.document.id, message.id)
                // A tombstone is named for the report it buries, so it must be recognised first:
                // "deleted-<id>.json" would otherwise read as a report called "deleted-<id>".
                val buried = Tombstone.requestIdOf(name)
                if (buried != null) {
                    tombstones.putIfAbsent(buried, file)
                    return@forEach
                }
                // Every revision is kept: which one wins is the merge's decision, not the order
                // Telegram happened to return them in.
                if (SyncedRule.ruleIdOf(name) != null) {
                    ruleRevisions += file
                    return@forEach
                }
                // Before the report branch, like a rule: every name ends in .json, so a position
                // left to fall through would be read as a report called "position-AMOC_2026-...".
                if (SyncedPosition.positionIdOf(name) != null) {
                    positionRevisions += file
                    return@forEach
                }
                // Settings and prompts before the report branch for the same reason as the two
                // above: every one of these names ends in .json, so anything left to fall through
                // is read as a report called "settings-..." and downloaded as one.
                if (SettingsSnapshot.stampOf(name) != null) {
                    settingsRevisions.putIfAbsent(name, file)
                    return@forEach
                }
                SyncedPromptVersion.promptIdOf(name)?.let {
                    promptVersions.putIfAbsent(it, file)
                    return@forEach
                }
                SyncedRun.requestIdOf(name)?.let { reports.putIfAbsent(it, file) }
            }
            fromMessageId = messages.last().id
        }
        return ChannelContents(
            reports,
            tombstones,
            ruleRevisions,
            positionRevisions,
            settingsRevisions,
            promptVersions,
        )
    }

    /** Every report the channel says was deleted, so no device brings one back. */
    suspend fun listTombstones(): Set<String> {
        val chats = (findSyncChats() + syncChatId()).distinct()
        return chats.flatMap { contentsOf(it).tombstones.keys }.toSet()
    }

    /**
     * Removes a report from the channel and publishes the marker that keeps it gone.
     *
     * Both halves matter: deleting the file stops a device that has not seen it from fetching it,
     * and the marker stops a device that already holds it from uploading it back.
     */
    suspend fun buryReport(requestId: String) {
        val chats = (findSyncChats() + syncChatId()).distinct()
        chats.forEach { chat ->
            val contents = contentsOf(chat)
            contents.reports[requestId]?.let { file ->
                runCatching {
                    client.deleteMessages(chat, longArrayOf(file.messageId), true).requireValue<Any?>()
                }
            }
        }
        val target = syncChatId()
        if (contentsOf(target).tombstones.containsKey(requestId)) return
        val marker = Tombstone(requestId)
        val staged = File(context.cacheDir, marker.fileName).apply {
            writeText(JSONObject().put("deleted", requestId).toString())
        }
        try {
            execute {
                client.sendMessage(
                    target,
                    null,
                    null,
                    null,
                    null,
                    dev.g000sha256.tdl.dto.InputMessageDocument(
                        dev.g000sha256.tdl.dto.InputDocument(
                            dev.g000sha256.tdl.dto.InputFileLocal(staged.path),
                            null,
                            true,
                        ),
                        dev.g000sha256.tdl.dto.FormattedText("deleted $requestId", emptyArray()),
                    ),
                )
            }
        } finally {
            staged.delete()
        }
    }

    /** Every rule revision the channel holds, merged so each rule appears once. */
    suspend fun syncedRules(): List<SyncedRule> {
        val chats = (findSyncChats() + syncChatId()).distinct()
        val revisions = chats.flatMap { chat ->
            contentsOf(chat).ruleRevisions.mapNotNull { file ->
                runCatching { SyncedRule.fromDocument(download(file.fileId).readText()) }.getOrNull()
            }
        }
        return mergeRules(revisions)
    }

    /** Puts one rule revision in the channel. Revisions accumulate; the merge picks the winner. */
    suspend fun uploadRule(revision: SyncedRule) {
        val chatId = syncChatId()
        val staged = File(context.cacheDir, revision.fileName).apply { writeText(revision.toDocument()) }
        try {
            execute {
                client.sendMessage(
                    chatId,
                    null,
                    null,
                    null,
                    null,
                    dev.g000sha256.tdl.dto.InputMessageDocument(
                        dev.g000sha256.tdl.dto.InputDocument(
                            dev.g000sha256.tdl.dto.InputFileLocal(staged.path),
                            null,
                            true,
                        ),
                        dev.g000sha256.tdl.dto.FormattedText(
                            "${if (revision.deleted) "deleted rule" else "rule"} ${revision.rule.phrase}",
                            emptyArray(),
                        ),
                    ),
                )
            }
        } finally {
            staged.delete()
        }
    }

    /** Every position revision the channel holds, merged so each position appears once. */
    suspend fun syncedPositions(): List<SyncedPosition> {
        val chats = (findSyncChats() + syncChatId()).distinct()
        val revisions = chats.flatMap { chat ->
            contentsOf(chat).positionRevisions.mapNotNull { file ->
                runCatching { SyncedPosition.fromDocument(download(file.fileId).readText()) }
                    .getOrNull()
            }
        }
        return mergePositions(revisions)
    }

    /** Puts one position revision in the channel. Revisions accumulate; the merge picks the winner. */
    suspend fun uploadPosition(revision: SyncedPosition) {
        val chatId = syncChatId()
        val staged = File(context.cacheDir, revision.fileName).apply {
            writeText(revision.toDocument())
        }
        try {
            execute {
                client.sendMessage(
                    chatId,
                    null,
                    null,
                    null,
                    null,
                    dev.g000sha256.tdl.dto.InputMessageDocument(
                        dev.g000sha256.tdl.dto.InputDocument(
                            dev.g000sha256.tdl.dto.InputFileLocal(staged.path),
                            null,
                            true,
                        ),
                        dev.g000sha256.tdl.dto.FormattedText(
                            listOfNotNull(
                                if (revision.deleted) "removed position" else "position",
                                revision.position.ticker,
                                revision.position.recommendationDate.toString(),
                            ).joinToString(" "),
                            emptyArray(),
                        ),
                    ),
                )
            }
        } finally {
            staged.delete()
        }
    }

    /** Puts one report in the channel. The file name is the identity; nothing else is read back. */
    suspend fun uploadReport(run: SyncedRun) {
        val chatId = syncChatId()
        val staged = File(context.cacheDir, run.fileName).apply { writeText(run.toDocument()) }
        try {
            execute {
                client.sendMessage(
                    chatId,
                    null,
                    null,
                    null,
                    null,
                    dev.g000sha256.tdl.dto.InputMessageDocument(
                        dev.g000sha256.tdl.dto.InputDocument(
                            dev.g000sha256.tdl.dto.InputFileLocal(staged.path),
                            null,
                            true,
                        ),
                        dev.g000sha256.tdl.dto.FormattedText(run.requestId, emptyArray()),
                    ),
                )
            }
        } finally {
            staged.delete()
        }
    }

    /** Reads one report back out of the channel. */
    suspend fun downloadReport(fileId: Int): SyncedRun? =
        SyncedRun.fromDocument(download(fileId).readText())

    /**
     * The settings the channel holds, or null when nobody has published any.
     *
     * Only the newest revision in each channel is downloaded, and which one that is comes out of
     * the file names: settings change far more often than they are read, so a channel carrying a
     * year of them costs one download rather than hundreds. Read across duplicate channels like
     * reports are, and merged rather than picked, so a newest file that will not parse loses one
     * channel's answer instead of all of them.
     */
    suspend fun syncedSettings(): SettingsSnapshot? {
        val chats = (findSyncChats() + syncChatId()).distinct()
        val newestInEach = chats.mapNotNull { chat ->
            contentsOf(chat).settingsRevisions.entries
                .mapNotNull { (name, file) -> SettingsSnapshot.stampOf(name)?.let { it to file } }
                .maxByOrNull { (stamp, _) -> stamp }
                ?.second
        }
        return mergeSettings(
            newestInEach.mapNotNull { file ->
                runCatching { SettingsSnapshot.fromDocument(download(file.fileId).readText()) }
                    .getOrNull()
            },
        )
    }

    /** Puts one settings revision in the channel. Revisions accumulate; the newest one wins. */
    suspend fun uploadSettings(snapshot: SettingsSnapshot) {
        val chatId = syncChatId()
        val staged = File(context.cacheDir, snapshot.fileName).apply {
            writeText(snapshot.toDocument())
        }
        try {
            execute {
                client.sendMessage(
                    chatId,
                    null,
                    null,
                    null,
                    null,
                    dev.g000sha256.tdl.dto.InputMessageDocument(
                        dev.g000sha256.tdl.dto.InputDocument(
                            dev.g000sha256.tdl.dto.InputFileLocal(staged.path),
                            null,
                            true,
                        ),
                        dev.g000sha256.tdl.dto.FormattedText(
                            "settings from ${snapshot.updatedBy}",
                            emptyArray(),
                        ),
                    ),
                )
            }
        } finally {
            staged.delete()
        }
    }

    /** Every generated prompt anywhere under this name, by the id its file name carries. */
    suspend fun listSyncedPromptVersions(): Map<String, Int> {
        val chats = (findSyncChats() + syncChatId()).distinct()
        return chats.fold(linkedMapOf()) { all, chat ->
            contentsOf(chat).promptVersions.forEach { (id, file) ->
                all.putIfAbsent(id, file.fileId)
            }
            all
        }
    }

    suspend fun downloadPromptVersion(fileId: Int): SyncedPromptVersion? =
        SyncedPromptVersion.fromDocument(download(fileId).readText())

    /** Puts one generated prompt in the channel. The id is the identity; it is never rewritten. */
    suspend fun uploadPromptVersion(version: SyncedPromptVersion) {
        val chatId = syncChatId()
        val staged = File(context.cacheDir, version.fileName).apply {
            writeText(version.toDocument())
        }
        try {
            execute {
                client.sendMessage(
                    chatId,
                    null,
                    null,
                    null,
                    null,
                    dev.g000sha256.tdl.dto.InputMessageDocument(
                        dev.g000sha256.tdl.dto.InputDocument(
                            dev.g000sha256.tdl.dto.InputFileLocal(staged.path),
                            null,
                            true,
                        ),
                        dev.g000sha256.tdl.dto.FormattedText(
                            "prompt v${version.version.sequence}",
                            emptyArray(),
                        ),
                    ),
                )
            }
        } finally {
            staged.delete()
        }
    }

    private suspend fun download(fileId: Int): File {
        val downloaded = client.downloadFile(fileId, 16, 0, 0, true)
            .requireValue<dev.g000sha256.tdl.dto.File>()
        require(downloaded.local.isDownloadingCompleted && downloaded.local.path.isNotBlank()) {
            "Telegram media download did not complete."
        }
        return File(downloaded.local.path)
    }

    private suspend fun handleAuthorizationState(state: AuthorizationState) {
        when (state) {
            is AuthorizationStateWaitTdlibParameters -> {
                // Built in where the build supplies them: an api_id names the application, not the
                // person, so making each user register one only stood between them and signing in.
                // A build without them falls back to asking, so a checkout without the file works.
                val bundledId = BuildConfig.TELEGRAM_API_ID
                val bundledHash = BuildConfig.TELEGRAM_API_HASH
                if (bundledId > 0 && bundledHash.isNotBlank()) {
                    initializeTdlib(bundledId, bundledHash)
                    return
                }
                val apiId = preferences.getInt(KEY_API_ID, 0)
                val hash = secretStore.readSecret(KEY_API_HASH)
                if (apiId == 0 || hash == null) {
                    _authState.value = TelegramAuthState(
                        TelegramAuthStep.API_CONFIGURATION,
                        "Enter the Telegram application credentials from my.telegram.org.",
                    )
                } else {
                    try {
                        initializeTdlib(apiId, String(hash))
                    } finally {
                        hash.fill('\u0000')
                    }
                }
            }
            is AuthorizationStateWaitPhoneNumber -> setState(
                TelegramAuthStep.PHONE_NUMBER,
                "Enter the phone number for your Telegram account.",
            )
            is AuthorizationStateWaitCode -> setState(
                TelegramAuthStep.VERIFICATION_CODE,
                "Enter the verification code sent by Telegram.",
            )
            is AuthorizationStateWaitPassword -> _authState.value = TelegramAuthState(
                TelegramAuthStep.TWO_FACTOR_PASSWORD,
                "Enter your Telegram two-step verification password.",
                hint = state.passwordHint,
            )
            is AuthorizationStateWaitEmailAddress -> setState(
                TelegramAuthStep.EMAIL_ADDRESS,
                "Telegram requires a login email address.",
            )
            is AuthorizationStateWaitEmailCode -> setState(
                TelegramAuthStep.EMAIL_CODE,
                "Enter the code sent to your email address.",
            )
            is AuthorizationStateWaitRegistration -> setState(
                TelegramAuthStep.REGISTRATION,
                "Enter your name to finish Telegram registration.",
            )
            is AuthorizationStateWaitOtherDeviceConfirmation -> _authState.value = TelegramAuthState(
                TelegramAuthStep.OTHER_DEVICE_CONFIRMATION,
                "Confirm this sign-in from another Telegram device.",
                link = state.link,
            )
            is AuthorizationStateReady -> {
                setState(TelegramAuthStep.READY, "Signed in to Telegram.")
                runCatching { refreshChats() }.onFailure { showError(it) }
            }
            is AuthorizationStateLoggingOut -> setState(
                TelegramAuthStep.LOGGING_OUT,
                "Signing out of Telegram…",
            )
            is AuthorizationStateClosing, is AuthorizationStateClosed -> setState(
                TelegramAuthStep.INITIALIZING,
                "Telegram session closed.",
            )
            else -> setState(TelegramAuthStep.INITIALIZING, "Preparing Telegram sign-in…")
        }
    }

    private suspend fun initializeTdlib(apiId: Int, apiHash: String) {
        setState(TelegramAuthStep.INITIALIZING, "Initializing encrypted Telegram storage…")
        val encryptionKey = databaseEncryptionKey()
        execute {
            client.setTdlibParameters(
                useTestDc = false,
                databaseDirectory = File(context.filesDir, "tdlib/database").apply(File::mkdirs).path,
                filesDirectory = File(context.filesDir, "tdlib/files").apply(File::mkdirs).path,
                databaseEncryptionKey = encryptionKey,
                useFileDatabase = true,
                useChatInfoDatabase = true,
                useMessageDatabase = true,
                useSecretChats = false,
                apiId = apiId,
                apiHash = apiHash,
                systemLanguageCode = context.resources.configuration.locales[0].toLanguageTag(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                systemVersion = "Android ${Build.VERSION.RELEASE}",
                applicationVersion = "0.1.0",
            )
        }
        encryptionKey.fill(0)
    }

    private fun databaseEncryptionKey(): ByteArray {
        secretStore.readSecret(KEY_DATABASE_ENCRYPTION)?.let { stored ->
            return try {
                Base64.decode(String(stored), Base64.NO_WRAP)
            } finally {
                stored.fill('\u0000')
            }
        }
        val generated = ByteArray(32).also(SecureRandom()::nextBytes)
        secretStore.saveSecret(
            KEY_DATABASE_ENCRYPTION,
            Base64.encodeToString(generated, Base64.NO_WRAP).toCharArray(),
        )
        return generated
    }

    private suspend fun execute(block: suspend () -> TdlResult<*>) {
        runCatching { block().requireValue<Any?>() }.onFailure { showError(it) }
    }

    private fun publishChats() {
        _chats.value = chatCache.values
            // TDLib caches chats the client no longer shows - ones left, archived, or hidden like a
            // channel's linked discussion group. Only a non-zero order in the main list means the
            // chat is really there, which is the same list the user sees in Telegram.
            .mapNotNull { chat -> mainListOrder(chat)?.let { order -> chat to order } }
            .sortedByDescending { (_, order) -> order }
            .map { (chat, _) ->
                val supergroup = chat.type as? ChatTypeSupergroup
                val kind = when {
                    supergroup?.isChannel == true -> ChatKind.CHANNEL
                    supergroup != null -> ChatKind.SUPERGROUP
                    chat.type is ChatTypeBasicGroup -> ChatKind.GROUP
                    else -> ChatKind.DIRECT
                }
                TelegramChat(
                    id = chat.id,
                    // Cleaned here so every screen and every saved analysis sees one label per chat.
                    // Telegram leaves the title empty once an account is deleted and labels it in
                    // its own client, so the raw id is not what the user is looking for.
                    title = cleanChannelName(
                        chat.title,
                        fallback = if (kind == ChatKind.DIRECT) "Deleted account" else chat.id.toString(),
                    ),
                    kind = kind,
                )
            }
    }

    /** Where the chat sits in the main list, or null when it is not in it at all. */
    private fun mainListOrder(chat: Chat): Long? = chat.positions
        .firstOrNull { it.list is ChatListMain && it.order != 0L }
        ?.order

    private fun setState(step: TelegramAuthStep, message: String) {
        _authState.value = TelegramAuthState(step, message)
    }

    private fun setError(message: String) {
        _authState.value = _authState.value.copy(message = message)
    }

    private fun showError(error: Throwable) {
        setError(error.message ?: "Telegram operation failed.")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> TdlResult<*>.requireValue(): T = when (this) {
        is TdlResult.Success<*> -> result as T
        is TdlResult.Failure -> error("Telegram error $code: $message")
    }

    private companion object {
        const val KEY_API_ID = "telegram_api_id"
        const val KEY_API_HASH = "telegram_api_hash"
        const val KEY_DATABASE_ENCRYPTION = "telegram_database_encryption"
        /** How many chats each `loadChats` call pulls from the server. */
        const val CHAT_PAGE_SIZE = 100

        /** The desktop applies no limit; this is high enough to be one in name only. */
        const val MAX_CHATS = 1_000
        const val HISTORY_PAGE_SIZE = 100

        /** How many empty answers to accept before believing a chat really is exhausted. */
        const val HISTORY_RETRIES = 4
        const val HISTORY_RETRY_DELAY_MS = 700L

        /**
         * A ceiling per chat, since paging now ends only at the window or an exhausted chat.
         *
         * TDLib decides for itself how many messages a page holds and routinely returns fewer than
         * asked for, so a short page cannot mean the history is finished - treating it that way
         * collected exactly one message per chat. Reaching the start of the window is what stops
         * the walk; this only stops it running away on a chat that never gets there.
         */
        const val MAX_MESSAGES_PER_CHAT = 2_000
        const val CLIENT_CLOSE_TIMEOUT_MS = 10_000L
        const val KEY_SYNC_CHAT = "telegram_sync_chat"
        const val SYNC_CHAT_TITLE = "EGX Analyzer sync"
    }
}

/** How much of a message is kept as its label in the source list. */
internal const val PREVIEW_LENGTH = 160

/**
 * Which picture files a run has already collected.
 *
 * Telegram hands the same photo out under a different message id when it is reposted, and TDLib
 * downloads all of them to one file.
 */
internal class CollectedImages {
    private val paths = mutableSetOf<String>()

    /** True the first time a file is offered, false for every repost of it. */
    fun accept(path: String): Boolean = paths.add(path)
}

/**
 * The opening of a message, trimmed to [PREVIEW_LENGTH] and free of broken emoji.
 *
 * An emoji is two UTF-16 units, so trimming by character count can cut one in half. The stray half
 * then pairs with whatever follows it once the trace is written out: in two runs saved on 2 August
 * it merged with the quote closing its own JSON string - a lone U+D83D and a `"` becoming a single
 * turtle - and both payloads were left unparseable and their reports unreachable.
 */
internal fun String.asPreview(): String {
    val source = this
    val preview = StringBuilder(minOf(source.length, PREVIEW_LENGTH))
    var index = 0
    while (index < source.length && preview.length < PREVIEW_LENGTH) {
        val character = source[index]
        val pairs = character.isHighSurrogate() &&
            index + 1 < source.length &&
            source[index + 1].isLowSurrogate()
        when {
            pairs -> {
                if (preview.length + 2 > PREVIEW_LENGTH) break
                preview.append(character).append(source[index + 1])
                index += 2
            }
            // Half of a pair with nothing to join: it can only do damage downstream.
            character.isSurrogate() -> index++
            else -> {
                preview.append(character)
                index++
            }
        }
    }
    return preview.toString()
}
