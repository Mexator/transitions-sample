package ru.mexator.slovo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mexator.core.dict.Dictionary

/**
 * Word bank invariants. A bad word costs a re-recording (Development plan),
 * so the bank is checked here, not on the phone.
 */
class DictionaryTest {

    @Test
    fun `article words and known guesses are valid`() {
        val sample = listOf(
            "АББАТ", "ЗЕМЛЯ", "КОШКА", "ТЕАТР", "СЛОВО", "ПОЕЗД", "ВОЛНА", "ГРОЗА",
        )
        sample.forEach { word -> assertTrue("$word must be valid", Dictionary.isValid(word)) }
        Dictionary.articles.forEach { article ->
            assertTrue("${article.word} must be valid", Dictionary.isValid(article.word))
        }
    }

    @Test
    fun `input is normalized, non-words are rejected`() {
        // isValid нормализует регистр — клавиатура и так печатает заглавными.
        assertTrue(Dictionary.isValid("кошка"))
        assertFalse(Dictionary.isValid("ЪЪЪЪЪ"))
        assertFalse(Dictionary.isValid("СЛОВ"))
        assertFalse(Dictionary.isValid("СЛОВОС"))
        assertFalse(Dictionary.isValid("СЛОВ0"))
    }

    @Test
    fun `word of the day is deterministic and always has an article`() {
        val now = 1_763_000_000_000L
        repeat(30) { day ->
            val word = Dictionary.wordOfTheDay(now + day * 86_400_000L)
            assertEquals(word, Dictionary.wordOfTheDay(now + day * 86_400_000L))
            assertTrue(
                "wordOfTheDay must pick a word with an article (hint + «Словарь»)",
                Dictionary.articleOf(word) != null,
            )
        }
    }

    @Test
    fun `random word always has an article and respects exclude`() {
        val articleWords = Dictionary.articles.map { it.word }.toSet()
        repeat(50) {
            val word = Dictionary.randomWord()
            assertTrue("randomWord must pick a word with an article", word in articleWords)
        }
        // Рестарт не должен повторить только что сыгранное слово
        repeat(20) {
            assertTrue(Dictionary.randomWord(exclude = "СЛОВО") != "СЛОВО")
        }
    }
}
