package dev.anton.slovo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.ChangeBounds
import androidx.transition.Scene
import androidx.transition.Transition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import dev.anton.core.dict.Dictionary
import dev.anton.core.dict.SlovarContract
import dev.anton.slovo.databinding.ActivityMainBinding
import dev.anton.slovo.databinding.SceneGameBinding
import dev.anton.slovo.databinding.SceneStartBinding
import dev.anton.slovo.demo.DemoConfig
import dev.anton.slovo.demo.DemoLayer
import dev.anton.slovo.game.GameController
import dev.anton.slovo.game.GameState
import dev.anton.slovo.game.GameStatus
import dev.anton.slovo.game.Mark
import dev.anton.slovo.game.SubmitResult
import dev.anton.slovo.transition.CenteredChangeBounds
import dev.anton.slovo.transition.ChangeColorTransition

/**
 * The game renders [GameState] into whichever scene is on screen. Scenes are
 * swapped with TransitionManager (Пример 2), recoloring runs through a custom
 * Transition (Пример 1). Every animated layer is gated by [DemoConfig] so the
 * debug panel can turn the app back into its "no animations" Пример 0 state.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DEMO_COMMAND = "demo.command"
        const val COMMAND_START = "start"
        const val COMMAND_SURRENDER = "surrender"
        const val COLOR_DURATION = 600L
        const val CONTAINER_DURATION = 900L

        /** transitionName of the field in scene_start ↔ scene_game. */
        const val TRANSITION_BOARD = "board"

        /** transitionName of the keyboard, present in both scenes. */
        const val TRANSITION_KEYBOARD = "keyboard"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: GameController
    private val cells: MutableList<MutableList<TextView>> = mutableListOf()
    private val keys = mutableMapOf<Char, TextView>()
    private var renderedRows = 0

    /** Scene views are re-inflated per scene swap — render() only touches live ones. */
    private var startViews: SceneStartBinding? = null
    private var gameViews: SceneGameBinding? = null

    // M1: move into shared styles so same-looking views share their look across scenes
    private val emptyColor = Color.rgb(0xF2, 0xF2, 0xF2)
    private val correctColor = Color.rgb(0x6A, 0xAA, 0x64)
    private val presentColor = Color.rgb(0xC9, 0xB4, 0x58)
    private val absentColor = Color.rgb(0x9A, 0x9A, 0x9A)
    private val keyColor = Color.rgb(0xDF, 0xE1, 0xE4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = GameController(Dictionary, ::render)

        showStartScene()

        binding.btnMeaning.setOnClickListener { openMeaning() }
        binding.btnSlovar.setOnClickListener { openSlovar() }
        binding.btnRestart.setOnClickListener { startGame() }

        // M3 (Пример 5): the fragment container shows/hides with the back stack
        supportFragmentManager.addOnBackStackChangedListener {
            val show = supportFragmentManager.backStackEntryCount > 0
            binding.articleContainer.visibility = if (show) View.VISIBLE else View.GONE
        }

        render(controller.state)
        handleDemoCommand(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDemoCommand(intent)
    }

    /** Sent by the debug demo panel: state jumps for recording. */
    private fun handleDemoCommand(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_DEMO_COMMAND)) {
            COMMAND_START -> startGame()
            COMMAND_SURRENDER -> {
                if (gameViews == null) startGame()
                controller.surrender()
            }
        }
    }

    private fun showStartScene() {
        val views = SceneStartBinding.inflate(layoutInflater, binding.sceneRoot, false)
        startViews = views
        buildMiniBoard(views)
        buildKeyboard(views.keyboard, interactive = false)
        Scene(binding.sceneRoot, views.root).enter() // first show: no transition
        views.playButton.setOnClickListener { startGame() }
    }

    /** The start scene shows a small copy of the field — empty cells, same frame. */
    private fun buildMiniBoard(views: SceneStartBinding) {
        repeat(controller.state.maxAttempts) {
            val row = linearRow()
            repeat(controller.state.wordLength) {
                row.addView(
                    TextView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, dp(22), 1f).apply {
                            setMargins(dp(2), dp(2), dp(2), dp(2))
                        }
                        setBackgroundColor(emptyColor)
                    },
                )
            }
            views.boardPreview.addView(row)
        }
    }

    private fun startGame() {
        val needScene = gameViews == null
        controller.startNewGame(DemoConfig.wordOverride)
        binding.finale.visibility = View.GONE
        if (needScene) {
            showGameScene(animated = DemoConfig.isEnabled(DemoLayer.CONTAINER))
        } else {
            render(controller.state)
        }
    }

    /**
     * Пример 2: the mini field morphs into the board via Scene + go(). Views are
     * matched by transitionName across the scenes. The keyboard sits at identical
     * bounds in both scenes, so its ChangeBounds is a no-op — it stays docked at
     * the bottom. The board frame is animated by [CenteredChangeBounds]: it
     * replays the mini field's bounds onto the final view with scale +
     * translation around the center, so the cells scale along with the frame
     * instead of being clipped inside animating bounds (that clipped variant was
     * the pre-fix state — plain ChangeBounds animates layout bounds only).
     */
    private fun showGameScene(animated: Boolean) {
        val views = SceneGameBinding.inflate(layoutInflater, binding.sceneRoot, false)
        gameViews = views
        buildBoard(views)
        buildKeyboard(views.keyboard, interactive = true)
        views.hintButton.setOnClickListener { toggleHint(views) }

        val scene = Scene(binding.sceneRoot, views.root)
        if (animated) {
            TransitionManager.go(
                scene,
                TransitionSet()
                    .addTransition(CenteredChangeBounds().addTarget(TRANSITION_BOARD))
                    .addTransition(ChangeBounds().addTarget(TRANSITION_KEYBOARD))
                    .demoDuration(CONTAINER_DURATION),
            )
        } else {
            scene.enter()
        }
        render(controller.state)
    }

    /** Cells are created once per game and updated in place — the substrate Transitions API needs. */
    private fun buildBoard(views: SceneGameBinding) {
        cells.clear()
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
            views.board.addView(row)
        }
    }

    /**
     * The keyboard exists in both scenes as a shared element. On the start scene
     * the keys are inert copies (no game yet); on the game scene they are live
     * and registered in [keys] for tinting.
     */
    private fun buildKeyboard(container: LinearLayout, interactive: Boolean) {
        if (interactive) keys.clear()
        listOf("ЙЦУКЕНГШЩЗХЪ", "ФЫВАПРОЛДЖЭ").forEach { letters ->
            val row = linearRow()
            letters.forEach { c ->
                val k = key(c.toString()) { if (interactive) controller.type(c) }
                if (interactive) keys[c] = k
                row.addView(k)
            }
            container.addView(row)
        }
        val bottomRow = linearRow()
        bottomRow.addView(key("⌫", 1.5f) { if (interactive) controller.backspace() })
        "ЯЧСМИТЬБЮ".forEach { c ->
            val k = key(c.toString()) { if (interactive) controller.type(c) }
            if (interactive) keys[c] = k
            bottomRow.addView(k)
        }
        bottomRow.addView(key("⏎", 1.5f) { if (interactive) submitGuess() })
        container.addView(bottomRow)
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
            setBackgroundColor(keyColor)
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

    private fun render(state: GameState) {
        // Пример 1: a freshly submitted row recolors cells and keyboard keys through
        // a custom Transition. beginDelayedTransition captures current colors now;
        // the mutations below become the transition's end scene.
        val justSubmitted = state.rows.size > renderedRows
        if (justSubmitted && DemoConfig.isEnabled(DemoLayer.COLOR)) {
            TransitionManager.beginDelayedTransition(
                binding.root,
                ChangeColorTransition().demoDuration(COLOR_DURATION),
            )
        }

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
        tintKeyboard(state)

        gameViews?.gameStatus?.text = when (state.status) {
            GameStatus.PLAYING -> getString(R.string.attempts_left, state.maxAttempts - state.attempts)
            GameStatus.WON -> getString(R.string.won)
            GameStatus.LOST -> getString(R.string.lost)
        }
        gameViews?.hintText?.text = Dictionary.articleOf(state.target)?.hint.orEmpty()

        val finished = state.status != GameStatus.PLAYING
        binding.finale.visibility = if (finished) View.VISIBLE else View.GONE
        if (finished) {
            binding.finaleTitle.setText(
                if (state.status == GameStatus.WON) R.string.won else R.string.lost,
            )
            binding.wordChip.transitionName = SlovarContract.TRANSITION_WORD
            binding.wordChip.text = state.target
        }
        renderedRows = state.rows.size
    }

    /** Keyboard keys take the best mark their letter ever received. */
    private fun tintKeyboard(state: GameState) {
        if (keys.isEmpty()) return
        val best = mutableMapOf<Char, Mark>()
        for (row in state.rows) {
            row.word.forEachIndexed { i, ch -> best[ch] = maxRank(best[ch], row.marks[i]) }
        }
        keys.forEach { (ch, view) ->
            view.setBackgroundColor(
                when (best[ch]) {
                    Mark.CORRECT -> correctColor
                    Mark.PRESENT -> presentColor
                    Mark.ABSENT -> absentColor
                    null -> keyColor
                },
            )
        }
    }

    private fun maxRank(a: Mark?, b: Mark): Mark = when {
        a == null -> b
        rank(a) >= rank(b) -> a
        else -> b
    }

    private fun rank(mark: Mark): Int = when (mark) {
        Mark.CORRECT -> 2
        Mark.PRESENT -> 1
        Mark.ABSENT -> 0
    }

    private fun toggleHint(views: SceneGameBinding) {
        views.hintText.visibility =
            if (views.hintText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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

    private fun Transition.demoDuration(base: Long): Transition = apply {
        duration = (base * DemoConfig.durationMultiplier).toLong()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
