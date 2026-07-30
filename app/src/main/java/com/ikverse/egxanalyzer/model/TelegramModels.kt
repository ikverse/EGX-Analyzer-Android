package com.ikverse.egxanalyzer.model

enum class TelegramAuthStep {
    API_CONFIGURATION,
    INITIALIZING,
    PHONE_NUMBER,
    VERIFICATION_CODE,
    TWO_FACTOR_PASSWORD,
    EMAIL_ADDRESS,
    EMAIL_CODE,
    REGISTRATION,
    OTHER_DEVICE_CONFIRMATION,
    READY,
    LOGGING_OUT,
    ERROR,
}

data class TelegramAuthState(
    val step: TelegramAuthStep = TelegramAuthStep.INITIALIZING,
    val message: String = "Starting Telegram…",
    val hint: String? = null,
    val link: String? = null,
)

data class TelegramChat(
    val id: Long,
    val title: String,
    val kind: ChatKind,
) {
    /** Broadcast channel, as opposed to a group or a private chat. */
    val isChannel: Boolean get() = kind == ChatKind.CHANNEL
}

data class TelegramSourceBatch(
    val inputs: List<AnalysisInput>,
    val traces: List<SourceTrace>,
)

/** How the desktop classifies a dialog, so both apps hide the same chats. */
enum class ChatKind(val label: String) {
    CHANNEL("Channel"),
    SUPERGROUP("Supergroup"),
    GROUP("Group"),
    DIRECT("Private chat"),
}
