package dev.anton.slovo.game

/** Result for a single letter of a submitted guess. */
enum class Mark { CORRECT, PRESENT, ABSENT }

/**
 * Classic Wordle scoring with correct duplicate handling:
 * 1) mark exact matches, consuming target letters;
 * 2) mark remaining letters as PRESENT while unconsumed copies remain in the target.
 */
object GuessEvaluator {

    fun score(guess: String, target: String): List<Mark> {
        val g = guess.uppercase()
        val t = target.uppercase()
        require(g.length == t.length) { "Guess and target must be the same length" }

        val result = MutableList(g.length) { Mark.ABSENT }
        val remaining = mutableMapOf<Char, Int>()

        t.forEachIndexed { i, c ->
            if (g[i] == c) result[i] = Mark.CORRECT else remaining[c] = (remaining[c] ?: 0) + 1
        }
        g.forEachIndexed { i, c ->
            if (result[i] != Mark.CORRECT && (remaining[c] ?: 0) > 0) {
                result[i] = Mark.PRESENT
                remaining[c] = remaining[c]!! - 1
            }
        }
        return result
    }
}
