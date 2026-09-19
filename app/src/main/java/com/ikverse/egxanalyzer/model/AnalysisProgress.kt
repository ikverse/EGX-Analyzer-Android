package com.ikverse.egxanalyzer.model

/**
 * How far along a run is, as far as a run can honestly say.
 *
 * Until this existed the repository reported nothing at all between the press and the report, so
 * the screen counted elapsed seconds and said no more - which was the right answer while the only
 * alternative was a figure nobody had measured. It is measured now: [ExtractionPlan.chunks] hands
 * back the whole list of batches before the first one is sent, so the run knows how many there are
 * at the moment it starts.
 *
 * **The reading is countable and the writing is not, and that split is the whole design.** A run
 * sends its images in batches it can count, and then asks the model to write one report out of what
 * came back - one request whose length nothing here can predict, and which becomes two or three if
 * the answer fails validation and is sent back for correction. So [fraction] is a real figure while
 * the batches are going and **null** once the writing starts, and the screen draws a determinate
 * bar for the first and an indeterminate one for the second. A bar that claimed to know how long
 * the writing takes would be the invented figure all over again, and a bar that counted the
 * writing as one step of many would walk backwards the moment a correction was needed.
 */
data class AnalysisProgress(
    val stage: Stage,
    /** Batches whose answer is in, out of [batches]. Zero until the first one comes back. */
    val batchesDone: Int = 0,
    val batches: Int = 0,
    /** Images inside those batches, which is what the reader actually recognises as the work. */
    val imagesDone: Int = 0,
    val images: Int = 0,
    /**
     * Which correction this is, or zero for the first attempt at writing the report.
     *
     * Said out loud because a correction is a second paid request for the same report, and a run
     * that quietly took three of them would otherwise look like one slow one.
     */
    val correction: Int = 0,
) {
    enum class Stage {
        /** Sending batches of images and collecting what the model read out of each. */
        READING,

        /** Turning everything read into one report. One request, or more if it needs correcting. */
        WRITING,
    }

    /**
     * How much of the run is done, or null where that cannot be known.
     *
     * Measured in **images rather than in batches**, because the last batch is usually short: eight
     * images per request means a run of fifty-six lands evenly and a run of fifty-seven ends on a
     * batch of one, which as a batch count would jump the bar as far for that one image as for the
     * eight before it.
     */
    val fraction: Float?
        get() = when {
            stage != Stage.READING -> null
            images <= 0 -> null
            else -> (imagesDone.toFloat() / images.toFloat()).coerceIn(0f, 1f)
        }

    /** Which batch is in flight, counting from one, and never past the last. */
    private val batch: Int get() = (batchesDone + 1).coerceAtMost(batches.coerceAtLeast(1))

    /**
     * What the run is doing, in the reader's own terms.
     *
     * Batches rather than chunks or requests: "chunk" is this app's word for its own plumbing, and
     * what the reader recognises is that their messages go off a handful at a time.
     */
    fun line(): String = when (stage) {
        Stage.READING -> {
            val read = "Reading batch $batch of $batches"
            if (images > 0) "$read · $imagesDone of $images images read" else read
        }

        Stage.WRITING -> when (correction) {
            0 -> "Writing the report"
            1 -> "Correcting the report"
            else -> "Correcting the report, attempt $correction"
        }
    }

    /**
     * The same thing short enough to sit on the action button beside the clock.
     *
     * A second accessor rather than trimming [line] at the call site, because the two are read in
     * different places for different reasons: the page has room to say how many images are behind
     * the figure, and the button has one line shared with the elapsed time.
     */
    fun badge(): String = when (stage) {
        Stage.READING -> "Batch $batch of $batches"
        Stage.WRITING -> if (correction == 0) "Writing" else "Correcting"
    }
}
