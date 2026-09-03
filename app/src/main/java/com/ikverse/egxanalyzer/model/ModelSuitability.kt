package com.ikverse.egxanalyzer.model

/** Whether a model can do the job a run needs, as far as anything here can tell. */
enum class ModelSuitability {
    /** It reads images, so it can read a card. */
    SUITABLE,

    /** It cannot: an embedder, a reranker, a voice model, or a text-only chat model. */
    UNSUITABLE,

    /** Nothing said either way. Offered anyway: only [UNSUITABLE] is held back. */
    UNKNOWN,
}

/**
 * What one model takes in, and whether that is enough for a run.
 *
 * [stated] separates what the provider published from what the id was read to mean. The first is a
 * fact; the second is a guess, and a guess is allowed to hide a row but never to refuse one - the
 * picker's filter can always be turned off, and typing an id has always worked.
 */
data class ModelCapabilities(
    val modalities: Set<ModelModality>,
    val suitability: ModelSuitability,
    val stated: Boolean,
) {
    /** The inputs worth naming on a row. Text is every model and says nothing. */
    fun inputLabel(): String? {
        val named = buildList {
            if (ModelModality.IMAGE in modalities) add("images")
            if (ModelModality.AUDIO in modalities) add("audio")
        }
        return named.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

/**
 * Which models are worth offering, for the providers that answer with bare ids.
 *
 * A run sends screenshots of Telegram cards, so **image input is the bar**: a model that cannot see
 * one cannot do this job at any price. OpenRouter states its modalities and is simply believed.
 * Qwen, OpenAI and Hugging Face return a name and nothing else, so the name is read - an allowlist
 * of the families known to have vision, and a rejection list for the ids that are plainly something
 * else. Anything unrecognised is [ModelSuitability.UNKNOWN] rather than rejected, and the picker
 * offers it: this list will be out of date the week after it is written, and a new model must never
 * become invisible for having a name written after this build.
 */
object ModelSuitabilityRules {

    fun capabilitiesOf(info: CloudModelInfo): ModelCapabilities {
        if (info.statedModalities.isNotEmpty()) {
            return ModelCapabilities(
                modalities = info.statedModalities,
                suitability = if (ModelModality.IMAGE in info.statedModalities) {
                    ModelSuitability.SUITABLE
                } else {
                    ModelSuitability.UNSUITABLE
                },
                stated = true,
            )
        }
        val id = info.id.lowercase()
        val words = id.split(*Separators).filter(String::isNotEmpty)
        if (words.any(::namesSomethingElse) || TextOnlyFamilies.any(words::contains)) {
            return ModelCapabilities(emptySet(), ModelSuitability.UNSUITABLE, stated = false)
        }
        val sees = words.any(VisionWords::contains) || VisionFamilies.any(id::contains)
        if (!sees) return ModelCapabilities(emptySet(), ModelSuitability.UNKNOWN, stated = false)
        val modalities = buildSet {
            add(ModelModality.TEXT)
            add(ModelModality.IMAGE)
            if ("omni" in words) add(ModelModality.AUDIO)
        }
        return ModelCapabilities(modalities, ModelSuitability.SUITABLE, stated = false)
    }

    /**
     * Whether one word of an id names something that is not a chat model.
     *
     * A trailing version is ignored - `wanx2.1-t2i-turbo` splits to `wanx2`, and an image generator
     * with a 2 after its name is still an image generator.
     */
    private fun namesSomethingElse(word: String): Boolean = NotAModelForThis.any { name ->
        word == name || (word.startsWith(name) && word.drop(name.length).all(Char::isDigit))
    }

    /** Ids that are not a chat model at all, whatever else they are. */
    private val NotAModelForThis = setOf(
        "embed", "embedding", "embeddings", "rerank", "reranker", "reranking",
        "tts", "asr", "stt", "whisper", "paraformer", "sambert", "cosyvoice", "speech", "voice",
        "wan", "wanx", "sd", "sdxl", "flux", "diffusion", "dall", "dalle", "imagen", "midjourney",
        "moderation", "moderations", "guard", "safety", "classifier", "ocr",
        // Text-to-image and the two video forms of it, which providers mark in the id itself.
        "t2i", "t2v", "i2v",
    )

    /** Families that chat, and cannot see. Named rather than left unknown so the count is honest. */
    private val TextOnlyFamilies = setOf("audio", "coder", "code", "math", "translation", "reranker")

    /** A word anywhere in the id that means vision on its own. */
    private val VisionWords = setOf("vl", "vision", "multimodal", "omni", "qvq", "4v", "4o")

    /**
     * Families whose name carries no such word.
     *
     * Matched against the whole id, because these are prefixes and vendor names rather than words -
     * `openai/gpt-4.1-mini` and `google/gemini-2.5-flash` both have to be found.
     */
    private val VisionFamilies = listOf(
        "internvl", "llava", "pixtral", "gemini", "claude", "moondream", "minicpm-v", "cogvlm",
        "idefics", "step-1v", "gpt-5", "gpt-4.1", "gpt-4-turbo", "llama-4", "llama4", "grok-4",
        "nova-lite", "nova-pro",
    )

    private val Separators = charArrayOf(' ', '\t', '-', '_', '/', '.', ':', ',', '@')
}
