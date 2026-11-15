package dev.anton.slovo.game

enum class GameStatus { PLAYING, WON, LOST }

data class SubmittedRow(
    val word: String,
    val marks: List<Mark>,
)

data class GameState(
    val target: String,
    val rows: List<SubmittedRow> = emptyList(),
    val currentInput: String = "",
    val maxAttempts: Int = 6,
) {
    val attempts: Int get() = rows.size
    val wordLength: Int get() = target.length

    val status: GameStatus
        get() = when {
            rows.any { it.word == target } -> GameStatus.WON
            attempts >= maxAttempts -> GameStatus.LOST
            else -> GameStatus.PLAYING
        }
}
