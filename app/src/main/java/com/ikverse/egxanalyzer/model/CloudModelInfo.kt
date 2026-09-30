package com.ikverse.egxanalyzer.model

/**
 * What a model will take as input.
 *
 * Only the kinds this app can send. A provider naming anything else - `file`, `pdf` - is telling us
 * about a capability no run uses, so it is dropped rather than carried as a modality nothing reads.
 */
enum class ModelModality {
    TEXT,
    IMAGE,
    AUDIO,
    ;

    companion object {
        fun from(value: String): ModelModality? = when (value.trim().lowercase()) {
            "text" -> TEXT
            "image", "images", "vision" -> IMAGE
            "audio", "speech" -> AUDIO
            else -> null
        }
    }
}

/**
 * One model as the provider described it.
 *
 * The id used to be all this app kept, which is why the picker could only ever offer the whole
 * catalogue: with nothing but a name there is no way to tell a vision model from an embedder, and
 * OpenRouter lists hundreds of both. [statedModalities] is what the provider itself said - empty
 * where it said nothing, which is every provider but OpenRouter.
 */
data class CloudModelInfo(
    val id: String,
    val statedModalities: Set<ModelModality> = emptySet(),
    val contextLength: Int? = null,
)
