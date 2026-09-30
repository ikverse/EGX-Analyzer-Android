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
}

data class CloudConfiguration(
    val provider: CloudProvider = CloudProvider.QWEN,
    val endpoint: String = CloudProvider.QWEN.defaultEndpoint,
    val model: String = CloudProvider.QWEN.defaultModel,
    val hasCredential: Boolean = false,
)
