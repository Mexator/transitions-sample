package ru.mexator.slovo.transition

import androidx.transition.ChangeBounds
import androidx.transition.Explode
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionSet
import androidx.transition.Visibility
import ru.mexator.slovo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Composition tests: which transition children run for which layer combo, and
 * that no child can fight a sibling for the same view. The bug these guard
 * against shipped once — an untargeted Explode next to a targeted Slide parked
 * the keyboard offscreen — and pixel-hunting it on the phone cost an hour.
 */
class DemoTransitionsTest {

    private val timings = DemoTransitions.Timings(
        color = 600L,
        container = 900L,
        visibility = 450L,
        explode = 500L,
        slowMo = 2f,
    )

    private fun TransitionSet.children(): List<androidx.transition.Transition> =
        (0 until transitionCount).mapNotNull { getTransitionAt(it) }

    // --- gameplay (Пример 1 + Пример 3: submit / surrender) ----------------

    @Test
    fun `gameplay with no layers arms nothing`() {
        assertNull(DemoTransitions.gameplay(colorOn = false, visibilityOn = false, timings = timings))
    }

    @Test
    fun `gameplay color only arms exactly ChangeColor`() {
        val set = DemoTransitions.gameplay(colorOn = true, visibilityOn = false, timings = timings)!!
        assertEquals(1, set.transitionCount)
        assertTrue(set.children()[0] is ChangeColorTransition)
    }

    @Test
    fun `gameplay visibility adds keyboard Slide and finale Fades`() {
        val set = DemoTransitions.gameplay(colorOn = true, visibilityOn = true, timings = timings)!!

        val slides = set.children().filterIsInstance<Slide>()
        assertEquals(1, slides.size)
        assertEquals(listOf(R.id.keyboard), slides[0].targetIds.map { it })

        val fades = set.children().filterIsInstance<Fade>()
        assertEquals(2, fades.size)
        fades.forEach { fade -> assertEquals(listOf(R.id.finale), fade.targetIds.map { it }) }
    }

    @Test
    fun `gameplay slow-mo scales child durations`() {
        val set = DemoTransitions.gameplay(colorOn = true, visibilityOn = true, timings = timings)!!
        val slide = set.children().filterIsInstance<Slide>()[0]
        assertEquals(900L, slide.duration) // 450 * slowMo 2
    }

    // --- sceneSwap (Пример 2 + Пример 3: start → game) ----------------------

    @Test
    fun `sceneSwap without visibility layer is exactly the container pair`() {
        val set = DemoTransitions.sceneSwap(visibilityOn = false, timings = timings)
        assertEquals(2, set.transitionCount)
        val kinds = set.children().map { it.javaClass }.toSet()
        assertEquals(setOf(CenteredChangeBounds::class.java, ChangeBounds::class.java), kinds)
        assertTrue(set.children().filterIsInstance<Visibility>().isEmpty())
    }

    @Test
    fun `sceneSwap fades target only scene-unique views, never the shared pair`() {
        val set = DemoTransitions.sceneSwap(visibilityOn = true, timings = timings)

        val fades = set.children().filterIsInstance<Fade>()
        assertEquals(2, fades.size)
        val faded = fades.flatMap { it.targetIds }.toSet()
        assertEquals(
            setOf(R.id.title, R.id.subtitle, R.id.play_button, R.id.game_status, R.id.hint_toggle),
            faded,
        )
        // The shared elements must stay owned by the bounds pair alone — a fade
        // on them would alpha-dip the board/keyboard mid-flight.
        set.children().filterIsInstance<Visibility>().forEach { visibilityChild ->
            assertTrue(
                "Visibility child targets a shared element",
                R.id.keyboard !in visibilityChild.targetIds,
            )
        }
    }

    // --- the S2 bug class ----------------------------------------------------

    @Test
    fun `untargeted Visibility child beside a targeted sibling fails loudly`() {
        val set = TransitionSet()
            .addTransition(Slide(android.view.Gravity.BOTTOM).addTarget(R.id.keyboard))
            .addTransition(Explode()) // the bug: catches every flip in the pass
        try {
            set.requireTargetedVisibility()
            fail("expected requireTargetedVisibility to throw")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Explode"))
        }
    }

    @Test
    fun `fully targeted set passes the check`() {
        val set = TransitionSet()
            .addTransition(Slide(android.view.Gravity.BOTTOM).addTarget(R.id.keyboard))
            .addTransition(Explode().addTarget(R.id.board))
        set.requireTargetedVisibility() // must not throw
    }
}
