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
    /**
     * Where this chat's profile photo sits on the device, or null when Telegram has none for it or
     * has not handed it over yet.
     *
     * A path rather than the bytes: Telegram already stores the file, and a list of a hundred chats
     * has no business holding a hundred bitmaps alive to draw thirty-six pixels each.
     */
    val photoPath: String? = null,
)

data class TelegramSourceBatch(
    val inputs: List<AnalysisInput>,
    val traces: List<SourceTrace>,
    /** How many messages were read to find these, so an empty result can explain itself. */
    val examined: Int = 0,
    /** Chats that returned nothing at all, which is different from returning nothing recent. */
    val silentChats: Int = 0,
)

/** How the desktop classifies a dialog, so both apps hide the same chats. */
enum class ChatKind(val label: String) {
    CHANNEL("Channel"),
    SUPERGROUP("Supergroup"),
    GROUP("Group"),
    DIRECT("Private chat"),
}
