package dev.anton.slovo.transition

import android.view.Gravity
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionSet
import androidx.transition.Visibility
import dev.anton.slovo.R

/**
 * Every transition pass of the demo, composed in one file — one slide per
 * example. Factories take layer flags and [Timings], so composition itself is
 * unit-testable: which children run for which layer combo, and that no child
 * can fight another for the same view.
 */
object DemoTransitions {

    const val TRANSITION_BOARD = "board"
    const val TRANSITION_KEYBOARD = "keyboard"

    /** Base durations in ms plus the demo panel's slow-mo multiplier. */
    data class Timings(
        val color: Long,
        val container: Long,
        val visibility: Long,
        val explode: Long,
        val slowMo: Float = 1f,
    ) {
        fun scaled(base: Long): Long = (base * slowMo).toLong()
    }

    /**
     * Пример 1 + Пример 3 armed before a submit/surrender: cells may recolor,
     * the game may end (keyboard slides out, finale fades in). Each child no-ops
     * unless its views change. Null when no layer is enabled (Пример 0).
     */
    fun gameplay(colorOn: Boolean, visibilityOn: Boolean, timings: Timings): TransitionSet? {
        val set = TransitionSet()
        if (colorOn) {
            set.addTransition(ChangeColorTransition().timed(timings.color, timings))
        }
        if (visibilityOn) {
            set.addTransition(
                Slide(Gravity.BOTTOM).addTarget(R.id.keyboard).timed(timings.visibility, timings),
            )
            set.addTransition(
                Fade(Fade.IN).addTarget(R.id.finale).timed(timings.visibility, timings),
            )
            set.addTransition(
                Fade(Fade.OUT).addTarget(R.id.finale).timed(timings.visibility, timings),
            )
        }
        return set.takeIf { it.transitionCount > 0 }?.requireTargetedVisibility()
    }

    /**
     * Пример 2 + Пример 3 set for the start→game scene swap. Matched views
     * (board, keyboard) animate their bounds; with the visibility layer on, the
     * views that exist in only one scene fade out in the ViewOverlay / fade in —
     * targeted by id so the fades can never adopt a matched view.
     */
    fun sceneSwap(visibilityOn: Boolean, timings: Timings): TransitionSet {
        val set = TransitionSet()
            .addTransition(
                CenteredChangeBounds()
                    .addTarget(TRANSITION_BOARD)
                    .timed(timings.container, timings),
            )
            .addTransition(
                ChangeBounds()
                    .addTarget(TRANSITION_KEYBOARD)
                    .timed(timings.container, timings),
            )
        if (visibilityOn) {
            set.addTransition(
                Fade(Fade.OUT)
                    .addTarget(R.id.title)
                    .addTarget(R.id.subtitle)
                    .addTarget(R.id.play_button)
                    .timed(timings.visibility, timings),
            )
            set.addTransition(
                Fade(Fade.IN)
                    .addTarget(R.id.game_status)
                    .addTarget(R.id.hint_button)
                    .timed(timings.visibility, timings),
            )
        }
        return set.requireTargetedVisibility()
    }

    private fun <T : Transition> T.timed(base: Long, timings: Timings): T =
        apply { duration = timings.scaled(base) }
}

/**
 * A Visibility child with no targets captures EVERY appear/disappear in the
 * pass — including flips already owned by a targeted sibling. That bug shipped
 * once: an untargeted Explode beside a targeted Slide gave the keyboard two
 * animators whose translations summed and parked it offscreen until the end.
 * Every Visibility child in a set with targeted siblings must name its targets;
 * this check turns a whole bug class into an immediate, readable crash.
 */
fun TransitionSet.requireTargetedVisibility(): TransitionSet {
    for (i in 0 until transitionCount) {
        val child = getTransitionAt(i) ?: continue
        if (child is Visibility && child.targetIds.isEmpty() && child.targets.isEmpty()) {
            throw IllegalStateException(
                "${child.javaClass.simpleName} has no targets; it will fight its " +
                    "targeted siblings for every unowned visibility flip",
            )
        }
    }
    return this
}
