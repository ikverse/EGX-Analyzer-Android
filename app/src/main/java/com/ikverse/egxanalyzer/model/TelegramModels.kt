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
    val isChannel: Boolean,
)

data class TelegramSourceBatch(
    val inputs: List<AnalysisInput>,
    val traces: List<SourceTrace>,
)
