package com.ikverse.egxanalyzer.model

/**
 * Which images a run sends, and under which of the run's own numbers.
 *
 * `IMAGE_REF n` has to resolve to entry `n - 1` of `AnalysisResult.imagePaths`, which is every image
 * the run carries - so the numbering is assigned over all of them and not over the ones that are
 * actually sent. A source answered out of an earlier run's reading is not sent and still occupies
 * its own place in that list, which is the whole reason this is stated apart from the chunking: the
 * two questions are "what number is this image" and "which images travel together", and only the
 * second one cares what is being skipped.
 *
 * Pure, like [AnalysisChunking], and for the same reason: getting this wrong does not fail, it
 * files one channel's levels under another channel's card in a report that looks entirely ordinary.
 */
internal class ExtractionPlan private constructor(
    /** Each image's reference, keyed by its position in the run's inputs. */
    val referenceOf: Map<Int, Int>,
    /** Each source's references, in the order the run carries them. */
    val referencesBySource: Map<String, List<Int>>,
    /** Which source each reference belongs to. */
    val sourceOf: Map<Int, String>,
    private val sourceIds: List<String>,
    private val images: List<Boolean>,
) {

    /** One request's worth: where its entries sit in the run's inputs, and their references. */
    data class Chunk(val positions: List<Int>, val references: List<Int>)

    /**
     * The requests to send, with [reused] sources left out of them entirely.
     *
     * Chunked over what is actually being sent rather than over the whole run, so a run whose every
     * source has been read before sends nothing at all rather than a request full of nothing.
     */
    fun chunks(
        reused: Set<String> = emptySet(),
        imagesPerChunk: Int = AnalysisChunking.IMAGES_PER_CHUNK,
    ): List<Chunk> {
        val kept = sourceIds.indices.filterNot { sourceIds[it] in reused }
        return AnalysisChunking.plan(
            sourceIds = kept.map(sourceIds::get),
            images = kept.map(images::get),
            imagesPerChunk = imagesPerChunk,
        ).map { range ->
            val positions = range.map(kept::get)
            Chunk(positions = positions, references = positions.mapNotNull(referenceOf::get))
        }
    }

    companion object {
        /**
         * The numbering, over every input the run carries.
         *
         * Stated over which entries are images and which source each belongs to, like
         * [AnalysisChunking.plan], because nothing about it depends on an input's contents.
         */
        fun of(sourceIds: List<String>, images: List<Boolean>): ExtractionPlan {
            require(sourceIds.size == images.size) { "Every entry needs a source and a kind." }
            val referenceOf = LinkedHashMap<Int, Int>()
            val referencesBySource = LinkedHashMap<String, MutableList<Int>>()
            val sourceOf = LinkedHashMap<Int, String>()
            for (position in sourceIds.indices) {
                if (!images[position]) continue
                val reference = referenceOf.size + 1
                referenceOf[position] = reference
                referencesBySource.getOrPut(sourceIds[position]) { mutableListOf() } += reference
                sourceOf[reference] = sourceIds[position]
            }
            return ExtractionPlan(referenceOf, referencesBySource, sourceOf, sourceIds, images)
        }
    }
}
