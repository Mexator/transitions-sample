package dev.anton.slovo.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GuessEvaluatorTest {

    @Test
    fun `exact match is all correct`() {
        val marks = GuessEvaluator.score("СЛОВО", "СЛОВО")
        assertEquals(List(5) { Mark.CORRECT }, marks)
    }

    @Test
    fun `same letter same index is correct, others absent`() {
        // Target СЛОВО. Guess ГРОЗА: only О(2) stands exactly where it should;
        // Г, Р, З, А do not appear in the target at all.
        val marks = GuessEvaluator.score("ГРОЗА", "СЛОВО")
        assertEquals(
            listOf(Mark.ABSENT, Mark.ABSENT, Mark.CORRECT, Mark.ABSENT, Mark.ABSENT),
            marks,
        )
    }

    @Test
    fun `second copy of a letter is absent when target has one`() {
        // Target ШКАЛА: Ш, К, А, Л, А. Guess КАКАО:
        // К(0) present, А(1) present, К(2) absent (the only К consumed by К(0)),
        // А(3) present (target has a second А), О(4) absent.
        val marks = GuessEvaluator.score("КАКАО", "ШКАЛА")
        assertEquals(
            listOf(Mark.PRESENT, Mark.PRESENT, Mark.ABSENT, Mark.PRESENT, Mark.ABSENT),
            marks,
        )
    }

    @Test
    fun `exact match consumes the letter, earlier copy is absent`() {
        // Target КОШКА: К, О, Ш, К, А. Guess КАРКА:
        // К(0) and К(3) are exact matches, А(4) is an exact match too —
        // so the target's only А is consumed and А(1) in the guess is ABSENT.
        val marks = GuessEvaluator.score("КАРКА", "КОШКА")
        assertEquals(
            listOf(Mark.CORRECT, Mark.ABSENT, Mark.ABSENT, Mark.CORRECT, Mark.CORRECT),
            marks,
        )
    }
}
