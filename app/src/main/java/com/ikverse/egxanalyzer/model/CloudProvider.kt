package com.ikverse.egxanalyzer.model

data class CloudEndpointPreset(
    val displayName: String,
    val endpoint: String,
)

enum class CloudProvider(
    val displayName: String,
    val defaultEndpoint: String,
    val defaultModel: String,
    val endpointPresets: List<CloudEndpointPreset>,
) {
    QWEN(
        displayName = "Qwen Cloud",
        defaultEndpoint = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen3.5-omni-plus",
        endpointPresets = listOf(
            CloudEndpointPreset(
                "QwenCloud / International (Singapore)",
                "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
            ),
            CloudEndpointPreset(
                "Alibaba Cloud / China (Beijing)",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
            ),
            CloudEndpointPreset(
                "Alibaba Cloud / US (Virginia)",
                "https://dashscope-us.aliyuncs.com/compatible-mode/v1",
            ),
            CloudEndpointPreset(
                "Alibaba Cloud / China (Hong Kong)",
                "https://cn-hongkong.dashscope.aliyuncs.com/compatible-mode/v1",
            ),
        ),
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultEndpoint = "https://openrouter.ai/api/v1",
        defaultModel = "openrouter/free",
        endpointPresets = emptyList(),
    ),
    HUGGING_FACE(
        displayName = "Hugging Face",
        defaultEndpoint = "https://router.huggingface.co/v1",
        defaultModel = "",
        endpointPresets = emptyList(),
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultEndpoint = "https://api.openai.com/v1",
        defaultModel = "",
        endpointPresets = emptyList(),
    ),

    /**
     * Claude, through ClaudeIn on the owner's PC, on the owner's Claude plan. No endpoint and no
     * key: the phone is paired with the PC instead (see [ClaudeInPairing]), and the "credential"
     * stored for this provider is that pairing. When the PC is not there the app says so and runs
     * nothing - there is no fallback to another provider, which is ClaudeIn's own rule.
     */
    CLAUDEIN(
        displayName = "ClaudeIn",
        defaultEndpoint = "",
        defaultModel = ClaudeInModels.DEFAULT,
        endpointPresets = emptyList(),
    ),
    ;

    /** Paired with a PC rather than given an endpoint and an API key. */
    val pairs: Boolean get() = this == CLAUDEIN
}

data class CloudConfiguration(
    val provider: CloudProvider = CloudProvider.QWEN,
    val endpoint: String = CloudProvider.QWEN.defaultEndpoint,
    val model: String = CloudProvider.QWEN.defaultModel,
    val hasCredential: Boolean = false,
)
