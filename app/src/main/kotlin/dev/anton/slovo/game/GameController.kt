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

    private fun dispatch() = onStateChanged(state)
}
