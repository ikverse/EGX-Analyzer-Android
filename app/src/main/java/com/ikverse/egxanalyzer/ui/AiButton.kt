package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.theme.extraColors
import kotlin.math.abs

/**
 * How an [AskAiButton] is drawn, which is decided by what pressing it costs.
 *
 * [Filled] spends money; [Outlined] reopens something already paid for. They are deliberately not
 * two weights of one treatment - a filled pill and a hairline ring are told apart before the label
 * is read, and the label is the only other thing separating a billed request from a free one.
 */
internal enum class AiLook { Filled, Outlined }

/**
 * The one control in this app that is not reporting a measurement.
 *
 * Everything else on a call card is a price, a count or a date, and the palette is fixed so those
 * always mean the same thing. This is an opinion from a model, so it gets the hue nothing else
 * claims - violet through magenta, with the mark in gold - and it is the only thing on these
 * screens allowed to move while it sits still.
 *
 * @param working sweeps the gradient instead of breathing, for as long as the request is out. It is
 *   the progress indicator: the button said "Asking…" with nothing moving before.
 * @param phaseKey seeds where in the breath this button starts. A screen can show twenty of these,
 *   and twenty haloes swelling on one beat is a Christmas tree rather than a list. Pass something
 *   stable per card - the ticker - so the cycle does not restart on every recomposition.
 */
@Composable
internal fun AskAiButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    look: AiLook = AiLook.Filled,
    enabled: Boolean = true,
    working: Boolean = false,
    phaseKey: Any? = null,
) {
    // Split rather than branched inside one body: the ring neither glows nor sweeps, and building
    // its animations only to leave them unread would keep the frame clock awake for every saved
    // answer on the screen.
    when (look) {
        AiLook.Filled -> FilledPill(label, onClick, modifier, enabled, working, phaseKey)
        AiLook.Outlined -> OutlinedPill(label, onClick, modifier, enabled)
    }
}

@Composable
private fun FilledPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    working: Boolean,
    phaseKey: Any?,
) {
    val ai = extraColors
    val motion = rememberAiMotion(ai.aiFill, phaseKey)

    Pill(
        label = label,
        onClick = onClick,
        enabled = enabled,
        textColor = ai.aiOnFill,
        spark = ai.aiSpark,
        // Both animations are read here rather than in the composable body on purpose. Read at
        // composition they would recompose every card sixty times a second; read in the draw
        // lambda the frame costs a repaint and nothing else.
        painted = Modifier.drawBehind {
            val corner = size.height / 2f
            drawAiHalo(ai.aiGlow, corner, motion.breath(working))
            drawRoundRect(motion.fill(size.width, working), cornerRadius = CornerRadius(corner))
        },
        modifier = modifier,
    )
}

@Composable
private fun OutlinedPill(label: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    val ai = extraColors
    Pill(
        label = label,
        onClick = onClick,
        enabled = enabled,
        textColor = ai.aiText,
        spark = ai.aiSparkOnCard,
        painted = Modifier.border(OutlineWidth, Brush.horizontalGradient(ai.aiLine), CircleShape),
        inset = AiPadding - 1.dp,
        modifier = modifier,
    )
}

@Composable
private fun Pill(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    textColor: Color,
    spark: List<Color>,
    painted: Modifier,
    modifier: Modifier = Modifier,
    inset: Dp = AiPadding,
) {
    Row(
        modifier
            // The pill is shorter than a fingertip. This keeps the target the full 48dp without
            // making the button look like one.
            .minimumInteractiveComponentSize()
            .height(PillHeight)
            .then(painted)
            // After the paint, so the ripple is bounded by the pill while the halo drawn above is
            // free to fall outside it.
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AiGap),
    ) {
        Spark(spark)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The mark, filled with a gradient rather than a colour.
 *
 * An `Icon` tint is one colour, so the gold is laid over the glyph and masked back to it. The
 * offscreen layer is what makes the mask work: [BlendMode.SrcIn] needs something to be "in", and
 * without a layer of its own the blend would find the whole card underneath.
 */
@Composable
internal fun Spark(gold: List<Color>, size: Dp = SparkSize, modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.AutoAwesome,
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = modifier
            .size(size)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(Brush.linearGradient(gold), blendMode = BlendMode.SrcIn)
            },
    )
}

/**
 * The two animations the AI treatment is made of, resolved once for whatever draws it.
 *
 * Handed back as state rather than as numbers so the caller can read them inside a draw lambda:
 * read in a composable body they recompose their caller sixty times a second, on every card in a
 * list. Every method here is meant to be called from a draw lambda for that reason.
 *
 * @param stops the fill's colours - `aiFill`'s three. The pill is the only caller: the screen's
 *   action stopped wearing this treatment when the violet came off it, and reads [ActionMotion]
 *   instead.
 * @param phaseKey seeds where in the breath this one starts. A screen can show twenty of these, and
 *   twenty haloes swelling on one beat is a Christmas tree rather than a list. Pass something stable
 *   per card - the ticker - so the cycle does not restart on every recomposition.
 */
@Composable
internal fun rememberAiMotion(stops: List<Color>, phaseKey: Any? = null): AiMotion {
    val pulse = rememberInfiniteTransition(label = "ai")

    // Derived from the key rather than drawn at random, so a card returns to the same phase after a
    // scroll or a fold instead of jumping.
    val phase = remember(phaseKey) { abs(phaseKey.hashCode()) % BreathHalfMs }

    val glow = pulse.animateFloat(
        initialValue = 1f,
        targetValue = GlowPeak,
        animationSpec = infiniteRepeatable(
            tween(BreathHalfMs),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(phase),
        ),
        label = "glow",
    )
    val sweep = pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SweepMs, easing = LinearEasing)),
        label = "sweep",
    )
    // Resolves against the draw size, so it survives every frame and every re-measure untouched.
    val resting = remember(stops) { Brush.horizontalGradient(stops) }
    return remember(stops, resting, glow, sweep) { AiMotion(stops, resting, glow, sweep) }
}

/** What [rememberAiMotion] hands back. Not built directly. */
@Stable
internal class AiMotion(
    private val stops: List<Color>,
    private val resting: Brush,
    private val glow: State<Float>,
    private val sweep: State<Float>,
) {
    /**
     * What to scale the halo by this frame.
     *
     * Flat while [working], because the sweep is doing the reporting then and a control breathing
     * and sliding at once is two clocks disagreeing.
     */
    fun breath(working: Boolean): Float = if (working) 1f else glow.value

    /**
     * The fill for a control [width] pixels across: still when idle, sliding while [working].
     *
     * The sweep is a window one control wide slid across twice that distance, mirrored so the
     * gradient never shows a seam where it wraps.
     */
    fun fill(width: Float, working: Boolean): Brush = if (!working) {
        resting
    } else {
        val travel = sweep.value * 2f * width - width
        Brush.linearGradient(
            stops,
            start = Offset(travel, 0f),
            end = Offset(travel + width, 0f),
            tileMode = TileMode.Mirror,
        )
    }
}

/**
 * The halo, as stacked round rects rather than a blur.
 *
 * `Modifier.blur` would put the control in a layer of its own and re-render it on every frame the
 * halo moves, on every card in the list. These cost one draw call each and fall off the same way.
 *
 * @param cornerPx the corner of the shape it is coming from - half the height for a pill, the
 *   shape's own radius for anything else. Each ring out grows with its spread, so the halo keeps
 *   that shape all the way out instead of rounding off into an oval.
 */
internal fun DrawScope.drawAiHalo(color: Color, cornerPx: Float, breath: Float) {
    repeat(GlowRings) { ring ->
        val t = (ring + 1f) / GlowRings
        val spread = GlowSpread.toPx() * t
        drawRoundRect(
            color = color.copy(alpha = GlowAlpha * (1f - t) * breath),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + spread * 2f, size.height + spread * 2f),
            cornerRadius = CornerRadius(cornerPx + spread),
        )
    }
}

/**
 * The motion the screen's action is made of: one breath, three drifting lights, and a turning mark.
 *
 * Deliberately not [rememberAiMotion]. That one carries a sweep this control never draws, and this
 * one carries drifts and a rotation the pill never draws - and a screen can hold twenty pills, where
 * building animations only to leave them unread keeps the frame clock awake for every one of them.
 * There is only ever one action on screen, so nothing here is seeded off a key the way the pill's
 * breath is; there is nothing to stagger it against.
 *
 * Everything is handed back as state so the caller reads it inside a draw or a `graphicsLayer`
 * lambda. Read in a composable body these recompose the whole screen sixty times a second.
 */
@Composable
internal fun rememberActionMotion(): ActionMotion {
    val pulse = rememberInfiniteTransition(label = "action")

    val glow = pulse.animateFloat(
        initialValue = 1f,
        targetValue = GlowPeak,
        animationSpec = infiniteRepeatable(tween(BreathHalfMs), RepeatMode.Reverse),
        label = "glow",
    )
    // Three periods that do not divide into one another, so what the lights make together does not
    // come back round on any cycle short enough to be noticed. On equal periods they would move as
    // one shape and the fill would read as a gradient sliding, which is what this replaced.
    val drifts = DriftMs.mapIndexed { index, period ->
        pulse.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(period), RepeatMode.Reverse),
            label = "drift$index",
        )
    }
    val spin = pulse.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(SpinMs, easing = LinearEasing)),
        label = "spin",
    )
    return remember(glow, drifts, spin) { ActionMotion(glow, drifts, spin) }
}

/** What [rememberActionMotion] hands back. Not built directly. */
@Stable
internal class ActionMotion(
    private val glow: State<Float>,
    private val drifts: List<State<Float>>,
    private val spin: State<Float>,
) {
    /**
     * What to scale the halo by this frame.
     *
     * Only ever read at rest. While a run is going the aurora is what says the control is alive, and
     * a halo breathing under a fill that is already moving is two clocks disagreeing.
     */
    fun breath(): Float = glow.value

    /** The mark's angle. Slow enough to read as alive rather than as a spinner counting something. */
    val angle: Float get() = spin.value

    /**
     * Where the three lights sit this frame, on a control [width] by [height] pixels.
     *
     * The paths are placed by hand rather than derived. Each crosses the button on a different
     * diagonal so no two ever stack up, and each starts or ends outside the bounds so a light is
     * always arriving or leaving - one parked in the middle reads as a smudge on the fill rather
     * than as light passing through it.
     */
    fun lights(width: Float, height: Float): List<Offset> = listOf(
        Offset(mix(-0.10f, 0.55f, drifts[0].value) * width, mix(-0.35f, 0.55f, drifts[0].value) * height),
        Offset(mix(0.75f, 0.20f, drifts[1].value) * width, mix(0.15f, -0.25f, drifts[1].value) * height),
        Offset(mix(1.05f, 0.40f, drifts[2].value) * width, mix(0.35f, 0.85f, drifts[2].value) * height),
    )
}

private fun mix(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/**
 * The action's fill while a run is going: a ground with soft lights drifting over it.
 *
 * Circles with a radial brush rather than `Modifier.blur`, for the same reason [drawAiHalo] stacks
 * round rects instead of blurring: a blur puts the control in a layer of its own and re-renders it
 * on every frame the lights move. A radial gradient ending at zero alpha is already a soft edge and
 * costs one draw call.
 *
 * No corner radius here, unlike [drawAiHalo]. This draws inside the surface rather than outside it,
 * and `FloatingSurface` clips to its own shape - so the lights are cut to the button's corners
 * without this having to be told what they are.
 *
 * @param base carries alpha 0.94 in the colour itself, which is the bar's figure. The lights are low
 *   enough over it that where they overlap the fill still reads at the bar's strength rather than
 *   above it.
 */
internal fun DrawScope.drawActionAurora(base: Color, lights: List<Color>, at: List<Offset>) {
    drawRect(base)
    val radius = size.height * LightRadius
    lights.forEachIndexed { index, colour ->
        val centre = at[index]
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colour, colour.copy(alpha = 0f)),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
}

/**
 * How wide one light is, as a multiple of the control's height.
 *
 * Wider than the button is tall on purpose: at anything under about 1.2 the circles read as three
 * discs sliding around, and what is wanted is one field of light that happens to move unevenly.
 */
private const val LightRadius = 1.6f

/** The three drift periods. See [rememberActionMotion] for why they are not equal. */
private val DriftMs = listOf(9_000, 11_000, 13_000)

/** One turn of the mark. */
private const val SpinMs = 3_400

private val AiPadding: Dp = 12.dp
private val AiGap: Dp = 6.dp
private val SparkSize: Dp = 14.dp
private val OutlineWidth: Dp = 1.5.dp

/** How far the halo reaches past the pill, and how it thins on the way out. */
private val GlowSpread: Dp = 10.dp
private const val GlowRings = 6

/**
 * How strong the innermost ring is, before the falloff and the breath scale it.
 *
 * Down from 0.13 to 0.065 to here. At the old strengths the halo was the loudest thing on a card of
 * prices, which is the wrong order: the button is worth noticing, not worth looking at first. This
 * lands where the violet reads as a lit edge rather than as light spilling onto the card.
 */
private const val GlowAlpha = 0.038f

/** Half a cycle: the breath is four seconds out and back. */
private const val BreathHalfMs = 2000

/**
 * How much the halo swells at the top of the breath.
 *
 * Shallow on purpose. The breath is there to say the control is alive, and past about a third the
 * swell stops reading as breathing and starts reading as blinking - which on a list of twenty cards
 * is movement the eye has to keep dismissing.
 */
private const val GlowPeak = 1.30f
private const val SweepMs = 2200
