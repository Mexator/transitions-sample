package ru.mexator.slovo.transition

import androidx.transition.ChangeBounds
import androidx.transition.Explode
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionSet
import androidx.transition.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.mexator.core.dict.SlovarContract
import ru.mexator.slovo.R

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
        cancel = 350L,
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
            setOf(R.id.title, R.id.subtitle, R.id.play_button, R.id.game_status, R.id.hint_card),
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

    // --- hintToggle (Пример 4: отмена анимаций) ------------------------------

    @Test
    fun `hintToggle with layer off arms nothing`() {
        assertNull(DemoTransitions.hintToggle(cancelOn = false, timings = timings))
    }

    @Test
    fun `hintToggle arms ChangeBounds on the card and Fade on the text only`() {
        val set = DemoTransitions.hintToggle(cancelOn = true, timings = timings)!!

        val bounds = set.children().filterIsInstance<ChangeBounds>()
        assertEquals(1, bounds.size)
        assertEquals(listOf(R.id.hint_card), bounds[0].targetIds.map { it })

        // Один Fade без явного mode: ловит и появление, и исчезновение текста
        val fades = set.children().filterIsInstance<Fade>()
        assertEquals(1, fades.size)
        assertEquals(listOf(R.id.hint_text), fades[0].targetIds.map { it })

        // Fade не таргетит карточку — иначе альфа-провал контейнера посреди роста
        assertTrue(R.id.hint_card !in fades[0].targetIds)
    }

    @Test
    fun `hintToggle slow-mo scales child durations`() {
        val set = DemoTransitions.hintToggle(cancelOn = true, timings = timings)!!
        assertEquals(700L, set.children()[0].duration) // 350 * slowMo 2
    }

    // --- fragment hop (Пример 5: ряд летит между фрагментами) -----------------

    @Test
    fun `sharedRowFlight is CenteredChangeBounds targeted at the word row only`() {
        val flight = DemoTransitions.sharedRowFlight(timings)
        assertTrue(flight is CenteredChangeBounds)
        assertEquals(listOf(SlovarContract.TRANSITION_WORD), flight.targetNames)
    }

    @Test
    fun `sharedRowFlight uses container timing`() {
        val flight = DemoTransitions.sharedRowFlight(timings)
        assertEquals(1800L, flight.duration) // 900 * slowMo 2
    }

    @Test
    fun `hopFade is a plain Fade with visibility timing`() {
        val fade = DemoTransitions.hopFade(timings)
        assertEquals(900L, fade.duration) // 450 * slowMo 2
        // Без таргетов и это не Set с сиблингами — fade принадлежит только
        // не-shared содержимому входящего фрагмента.
        assertTrue(fade.targets.isEmpty())
        assertTrue(fade.targetIds.isEmpty())
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
