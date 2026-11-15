package dev.anton.slovo.game

import dev.anton.core.dict.Dictionary

sealed interface SubmitResult {
    data object Accepted : SubmitResult
    data object TooShort : SubmitResult
    data object NotAWord : SubmitResult
    data object GameOver : SubmitResult
}

/**
 * Pure game state machine. The Activity only renders [GameState]; when animations
 * arrive (M1+), rendering will switch from "rebuild" to "update existing views",
 * which is what Transitions API needs to match start/end views.
 */
class GameController(
    private val dictionary: Dictionary,
    private val onStateChanged: (GameState) -> Unit,
) {
    var state: GameState = GameState(dictionary.wordOfTheDay())
        private set

    init {
        dispatch()
    }

    fun startNewGame(word: String? = null) {
        state = GameState(word?.uppercase() ?: dictionary.wordOfTheDay())
        dispatch()
    }

    fun type(letter: Char) {
        if (state.status != GameStatus.PLAYING) return
        if (state.currentInput.length >= state.wordLength) return
        state = state.copy(currentInput = state.currentInput + letter.uppercase())
        dispatch()
    }

    fun backspace() {
        if (state.currentInput.isEmpty()) return
        state = state.copy(currentInput = state.currentInput.dropLast(1))
        dispatch()
    }

    fun submit(): SubmitResult {
        if (state.status != GameStatus.PLAYING) return SubmitResult.GameOver
        val guess = state.currentInput
        if (guess.length < state.wordLength) return SubmitResult.TooShort
        if (!dictionary.isValid(guess)) return SubmitResult.NotAWord

        state = state.copy(
            rows = state.rows + SubmittedRow(guess, GuessEvaluator.score(guess, state.target)),
            currentInput = "",
        )
        dispatch()
        return SubmitResult.Accepted
    }

    /** Demo-panel shortcut: fill all attempts with losing words to reach the finale instantly. */
    fun surrender() {
        if (state.status != GameStatus.PLAYING) return
        val losers = listOf("КОШКА", "ЗЕМЛЯ", "ГОРОД", "ВОЛНА", "ГРОЗА", "СПИНА", "МЫШКА", "КНИГА")
        val rows = state.rows.toMutableList()
        for (word in losers) {
            if (rows.size >= state.maxAttempts) break
            if (word == state.target || !dictionary.isValid(word)) continue
            rows += SubmittedRow(word, GuessEvaluator.score(word, state.target))
        }
        state = state.copy(rows = rows, currentInput = "")
        dispatch()
    }

    private fun dispatch() = onStateChanged(state)
}
