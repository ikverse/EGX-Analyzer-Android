package com.ikverse.egxanalyzer.model

/**
 * Splits one run's sources into requests small enough for the model to keep track of.
 *
 * Sending every image in a single request made the model lose count. Over 32 images it cited
 * IMAGE_REF 4 for a card that was image 15, so Results showed an excluded past-recommendations
 * table beside a valid call while the card actually read appeared nowhere, and 13 of 37 exclusions
 * named an image that was not the one they described. Reading a card was never the problem -
 * remembering which card it was looking at was.
 *
 * A chunk keeps IMAGE_REF in single digits, which is bookkeeping the model can hold. Chunk-local
 * numbering is mapped back to the run's numbering by the caller, where it is arithmetic rather
 * than recall.
 */
object AnalysisChunking {

    /** Small enough that references stay reliable, large enough to keep prompt repeats down. */
    const val IMAGES_PER_CHUNK = 8

    fun chunk(
        inputs: List<AnalysisInput>,
        imagesPerChunk: Int = IMAGES_PER_CHUNK,
    ): List<List<AnalysisInput>> = plan(
        sourceIds = inputs.map(AnalysisInput::sourceId),
        images = inputs.map { it is AnalysisInput.Image },
        imagesPerChunk = imagesPerChunk,
    ).map(inputs::slice)

    /**
     * The rule itself: where to cut, given only which source each entry belongs to and which
     * entries are images. Nothing here depends on an input's contents, which is why it is stated
     * over ids rather than over inputs.
     *
     * A message is never split across chunks. Its caption arrives as a separate input after its
     * photo and carries the one word that distinguishes a new call from a follow-up on an old one,
     * so the two have to be judged together. A single message holding more images than
     * [imagesPerChunk] gets its own oversized chunk for the same reason.
     */
    internal fun plan(
        sourceIds: List<String>,
        images: List<Boolean>,
        imagesPerChunk: Int = IMAGES_PER_CHUNK,
    ): List<IntRange> {
        require(imagesPerChunk > 0) { "A chunk has to hold at least one image." }
        require(sourceIds.size == images.size) { "Every entry needs a source and a kind." }
        val chunks = mutableListOf<IntRange>()
        var chunkStart = 0
        var chunkImages = 0
        var messageStart = 0
        var messageImages = 0

        fun closeMessage(endExclusive: Int) {
            if (endExclusive == messageStart) return
            if (chunkStart < messageStart && chunkImages + messageImages > imagesPerChunk) {
                chunks += chunkStart until messageStart
                chunkStart = messageStart
                chunkImages = 0
            }
            chunkImages += messageImages
            messageStart = endExclusive
            messageImages = 0
        }

        for (index in sourceIds.indices) {
            if (index > messageStart && sourceIds[index] != sourceIds[messageStart]) {
                closeMessage(index)
            }
            if (images[index]) messageImages += 1
        }
        closeMessage(sourceIds.size)
        if (chunkStart < sourceIds.size) chunks += chunkStart until sourceIds.size
        return chunks
    }
}
