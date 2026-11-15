package dev.anton.slovo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import dev.anton.core.dict.Dictionary
import dev.anton.core.dict.SlovarContract
import dev.anton.slovo.databinding.ActivityMainBinding
import dev.anton.slovo.game.GameController
import dev.anton.slovo.game.GameStatus
import dev.anton.slovo.game.Mark
import dev.anton.slovo.game.SubmitResult

/**
 * M0 baseline: fully playable, zero animations — the "before" state of the talk.
 * Every animation lands in later milestones on top of this render(state) loop.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: GameController
    private val cells: MutableList<MutableList<TextView>> = mutableListOf()

    // M1: move into shared styles so same-looking views share their look across scenes
    private val emptyColor = Color.rgb(0xF2, 0xF2, 0xF2)
    private val correctColor = Color.rgb(0x6A, 0xAA, 0x64)
    private val presentColor = Color.rgb(0xC9, 0xB4, 0x58)
    private val absentColor = Color.rgb(0x9A, 0x9A, 0x9A)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = GameController(Dictionary, ::render)

        binding.playButton.setOnClickListener { startGame() }
        binding.btnMeaning.setOnClickListener { openMeaning() }
        binding.btnSlovar.setOnClickListener { openSlovar() }
        binding.btnRestart.setOnClickListener { startGame() }
        binding.hintButton.setOnClickListener { toggleHint() }

        // M3 (Пример 5): the fragment container shows/hides with the back stack
        supportFragmentManager.addOnBackStackChangedListener {
            val show = supportFragmentManager.backStackEntryCount > 0
            binding.articleContainer.visibility = if (show) View.VISIBLE else View.GONE
        }

        buildBoard()
        buildKeyboard()
        render(controller.state)
    }

    private fun startGame() {
        controller.startNewGame()
        binding.startScene.visibility = View.GONE
        binding.gameScene.visibility = View.VISIBLE
        binding.finale.visibility = View.GONE
        binding.hintText.visibility = View.GONE
        binding.hintText.text = Dictionary.articleOf(controller.state.target)?.hint.orEmpty()
    }

    /** Cells are created once and updated in place — the substrate Transitions API needs later. */
    private fun buildBoard() {
        repeat(controller.state.maxAttempts) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                orientation = LinearLayout.HORIZONTAL
            }
            val rowCells = mutableListOf<TextView>()
            repeat(controller.state.wordLength) {
                val cell = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    }
                    gravity = Gravity.CENTER
                    textSize = 22f
                }
                rowCells += cell
                row.addView(cell)
            }
            cells += rowCells
            binding.board.addView(row)
        }
    }

    private fun buildKeyboard() {
        listOf("ЙЦУКЕНГШЩЗХЪ", "ФЫВАПРОЛДЖЭ").forEach { letters ->
            val row = linearRow()
            letters.forEach { c -> row.addView(key(c.toString()) { controller.type(c) }) }
            binding.keyboard.addView(row)
        }
        val bottomRow = linearRow()
        bottomRow.addView(key("⌫", 1.5f) { controller.backspace() })
        "ЯЧСМИТЬБЮ".forEach { c -> bottomRow.addView(key(c.toString()) { controller.type(c) }) }
        bottomRow.addView(key("⏎", 1.5f) { submitGuess() })
        binding.keyboard.addView(bottomRow)
    }

    private fun linearRow() = LinearLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        orientation = LinearLayout.HORIZONTAL
    }

    /**
     * TextView, not Button: Material Button reserves insets for its label and clips
     * it at this height, which made the letters unreadable. A bold TextView with a
     * gray background and a ripple foreground behaves and reads like a key.
     */
    private fun key(label: String, weight: Float = 1f, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, dp(52), weight).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(0xDF, 0xE1, 0xE4))
            val ripple = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            foreground = resources.getDrawable(ripple.resourceId, theme)
            setOnClickListener { onClick() }
        }

    private fun submitGuess() {
        when (controller.submit()) {
            SubmitResult.NotAWord ->
                Toast.makeText(this, R.string.not_a_word, Toast.LENGTH_SHORT).show()
            SubmitResult.Accepted, SubmitResult.TooShort, SubmitResult.GameOver -> Unit
        }
    }

    private fun render(state: dev.anton.slovo.game.GameState) {
        cells.forEachIndexed { rowIdx, rowCells ->
            val submitted = state.rows.getOrNull(rowIdx)
            val isCurrent = rowIdx == state.rows.size && state.status == GameStatus.PLAYING
            rowCells.forEachIndexed { colIdx, cell ->
                val letter = when {
                    submitted != null -> submitted.word.getOrNull(colIdx)
                    isCurrent -> state.currentInput.getOrNull(colIdx)
                    else -> null
                }
                cell.text = letter?.toString().orEmpty()
                cell.setBackgroundColor(
                    if (submitted != null) {
                        when (submitted.marks[colIdx]) {
                            Mark.CORRECT -> correctColor
                            Mark.PRESENT -> presentColor
                            Mark.ABSENT -> absentColor
                        }
                    } else {
                        emptyColor
                    },
                )
            }
        }

        binding.gameStatus.text = when (state.status) {
            GameStatus.PLAYING -> getString(R.string.attempts_left, state.maxAttempts - state.attempts)
            GameStatus.WON -> getString(R.string.won)
            GameStatus.LOST -> getString(R.string.lost)
        }

        val finished = state.status != GameStatus.PLAYING
        binding.finale.visibility = if (finished) View.VISIBLE else View.GONE
        if (finished) {
            binding.finaleTitle.setText(
                if (state.status == GameStatus.WON) R.string.won else R.string.lost,
            )
            binding.wordChip.transitionName = SlovarContract.TRANSITION_WORD
            binding.wordChip.text = state.target
        }
    }

    private fun toggleHint() {
        binding.hintText.visibility =
            if (binding.hintText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    /** Пример 5 (M3): shared element into ArticleFragment. M0: plain add, no animations. */
    private fun openMeaning() {
        supportFragmentManager.beginTransaction()
            .add(R.id.article_container, ArticleFragment.newInstance(controller.state.target))
            .addToBackStack(null)
            .commit()
    }

    /** Пример 6 (M3): shared element across the process boundary into «Словарь». */
    private fun openSlovar() {
        val intent = Intent()
            .setClassName(SlovarContract.PACKAGE, SlovarContract.ACTIVITY)
            .putExtra(SlovarContract.EXTRA_WORD, controller.state.target)
        try {
            // M3: startActivity(intent, ActivityOptionsCompat.makeSceneTransitionAnimation(
            //     this, binding.wordChip, SlovarContract.TRANSITION_WORD).toBundle())
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.install_slovar, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
