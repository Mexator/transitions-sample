package dev.anton.slovo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.ChangeBounds
import androidx.transition.Explode
import androidx.transition.Fade
import androidx.transition.Scene
import androidx.transition.Slide
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
import dev.anton.slovo.transition.DemoTransitions
import dev.anton.slovo.transition.requireTargetedVisibility
import dev.anton.slovo.widget.KeyboardView

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
        const val VISIBILITY_DURATION = 450L
        const val EXPLODE_DURATION = 500L

        /** Pause between the two Explode passes of a restart (see explodeRestart). */
        const val RESTART_GAP = 60L
    }

    private val demoTimings
        get() = DemoTransitions.Timings(
            color = COLOR_DURATION,
            container = CONTAINER_DURATION,
            visibility = VISIBILITY_DURATION,
            explode = EXPLODE_DURATION,
            slowMo = DemoConfig.durationMultiplier,
        )

    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: GameController
    private val cells: MutableList<MutableList<TextView>> = mutableListOf()
    private var renderedRows = 0

    /** Scene views are re-inflated per scene swap — render() only touches live ones. */
    private var startViews: SceneStartBinding? = null
    private var gameViews: SceneGameBinding? = null

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
                // A state jump, not a showcase: swap the scene WITHOUT a go()
                // transition. Arming go() here and then immediately stacking a
                // beginDelayedTransition in the same frame reliably left the
                // finale VISIBLE in the hierarchy but never drawn (two pending
                // pre-draw passes on sibling roots ate the unanimated flip).
                if (gameViews == null) startGame(animated = false)
                beginGameplayTransition()
                controller.surrender()
            }
        }
    }

    private fun showStartScene() {
        val views = SceneStartBinding.inflate(layoutInflater, binding.sceneRoot, false)
        startViews = views
        buildMiniBoard(views)
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

    private fun startGame(animated: Boolean = true) {
        if (gameViews == null) {
            controller.startNewGame(DemoConfig.wordOverride)
            showGameScene(
                animated = animated && DemoConfig.isEnabled(DemoLayer.CONTAINER),
            )
        } else if (DemoConfig.isEnabled(DemoLayer.VISIBILITY)) {
            explodeRestart()
        } else {
            controller.startNewGame(DemoConfig.wordOverride)
        }
    }

    /**
     * Пример 3: «Ещё раз» clears the board with Explode — a Visibility subclass.
     * A single transition pass can't animate one view both disappearing and
     * appearing, so this is the "intermediate scene" trick: pass 1 explodes the
     * played rows out — they animate in the ViewOverlay, already GONE from the
     * hierarchy — while the finale fades away and the keyboard slides back in;
     * pass 2 explodes fresh empty rows in. The [RESTART_GAP] pause between the
     * passes is the price of the trick.
     */
    private fun explodeRestart() {
        val views = gameViews ?: return
        val rows = (0 until views.board.childCount).map(views.board::getChildAt)

        // Pass 1 — no render() on purpose: the exploding rows must keep their
        // played colors in the overlay copy; the state resets only in pass 2.
        // Explode is targeted at the rows only: an untargeted Visibility would
        // also capture the keyboard/finale flips and fight the targeted Slide
        // and Fade above (two animators summing translations on one view).
        // requireTargetedVisibility() codifies that rule — it crashes loudly if
        // a future edit reintroduces the mistake.
        TransitionManager.beginDelayedTransition(
            binding.root,
            TransitionSet()
                .addTransition(
                    Slide(Gravity.BOTTOM).addTarget(R.id.keyboard).demoDuration(VISIBILITY_DURATION),
                )
                .addTransition(Fade(Fade.OUT).addTarget(R.id.finale).demoDuration(VISIBILITY_DURATION))
                .addTransition(explodeFromBoard(views.board, rows).demoDuration(EXPLODE_DURATION))
                .requireTargetedVisibility(),
        )
        rows.forEach { it.visibility = View.GONE }
        binding.finale.visibility = View.GONE
        views.keyboard.visibility = View.VISIBLE

        binding.root.postDelayed(
            {
                // Pass 2: rows are still GONE, so this Explode catches their
                // appear; startNewGame's render then fills them as empty cells.
                if (gameViews == null) return@postDelayed
                TransitionManager.beginDelayedTransition(
                    binding.root,
                    explodeFromBoard(views.board, rows).demoDuration(EXPLODE_DURATION),
                )
                rows.forEach { it.visibility = View.VISIBLE }
                controller.startNewGame(DemoConfig.wordOverride)
            },
            (EXPLODE_DURATION * DemoConfig.durationMultiplier).toLong() + RESTART_GAP,
        )
    }

    /**
     * Explode with its epicenter at the board's center — rows radiate out of it.
     * Targeted at exactly those rows so it never collides with the Slide/Fade
     * children that own the keyboard and the finale.
     */
    private fun explodeFromBoard(board: LinearLayout, rows: List<View>): Explode {
        val epicenter = Rect()
        board.getGlobalVisibleRect(epicenter)
        return Explode().apply {
            rows.forEach(::addTarget)
            setEpicenterCallback(object : Transition.EpicenterCallback() {
                override fun onGetEpicenter(transition: Transition): Rect = epicenter
            })
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
        views.keyboard.onKey = { key ->
            when (key) {
                is KeyboardView.Key.Letter -> controller.type(key.char)
                KeyboardView.Key.Backspace -> controller.backspace()
                KeyboardView.Key.Submit -> submitGuess()
            }
        }
        views.hintButton.setOnClickListener { toggleHint(views) }

        val scene = Scene(binding.sceneRoot, views.root)
        if (animated) {
            TransitionManager.go(
                scene,
                DemoTransitions.sceneSwap(
                    visibilityOn = DemoConfig.isEnabled(DemoLayer.VISIBILITY),
                    timings = demoTimings,
                ),
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

    /** Horizontal row for the board/mini-board grid (the keyboard builds its own). */
    private fun linearRow() = LinearLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        orientation = LinearLayout.HORIZONTAL
    }

    private fun submitGuess() {
        // Arm the transition set BEFORE submit(): submit dispatches render()
        // synchronously, and those mutations become the transition's end scene.
        beginGameplayTransition()
        when (controller.submit()) {
            SubmitResult.NotAWord ->
                Toast.makeText(this, R.string.not_a_word, Toast.LENGTH_SHORT).show()
            SubmitResult.Accepted, SubmitResult.TooShort, SubmitResult.GameOver -> Unit
        }
    }

    /**
     * Пример 1 + Пример 3 armed in one pass, before a state change that may
     * recolor cells and/or finish the game: the keyboard then slides out and the
     * finale fades in. Each child no-ops unless its views actually change, so
     * arming both layers unconditionally is safe — a rejected guess animates
     * nothing.
     */
    private fun beginGameplayTransition() {
        DemoTransitions.gameplay(
            colorOn = DemoConfig.isEnabled(DemoLayer.COLOR),
            visibilityOn = DemoConfig.isEnabled(DemoLayer.VISIBILITY),
            timings = demoTimings,
        )?.let { TransitionManager.beginDelayedTransition(binding.root, it) }
    }

    private fun render(state: GameState) {
        // Pure view mutation — no transition calls live here. The CALLER decides
        // which pass is animated (beginDelayedTransition before the state change,
        // go() on a scene swap); Transitions interpolates from the values captured
        // before the call to whatever this render leaves on screen.
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
        // Пример 3: the keyboard's GONE/VISIBLE flip is what Slide animates; the
        // finale's is what Fade animates (in whichever armed pass changed them).
        gameViews?.keyboard?.visibility = if (finished) View.GONE else View.VISIBLE
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
        val view = gameViews ?: return
        val best = mutableMapOf<Char, Mark>()
        for (row in state.rows) {
            row.word.forEachIndexed { i, ch -> best[ch] = maxRank(best[ch], row.marks[i]) }
        }
        view.keyboard.tint(
            best.mapValues { (_, mark) ->
                when (mark) {
                    Mark.CORRECT -> correctColor
                    Mark.PRESENT -> presentColor
                    Mark.ABSENT -> absentColor
                }
            },
        )
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
