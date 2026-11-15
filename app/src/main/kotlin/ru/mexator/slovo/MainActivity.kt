package ru.mexator.slovo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import ru.mexator.core.dict.Dictionary
import ru.mexator.core.dict.SlovarContract
import ru.mexator.slovo.databinding.ActivityMainBinding
import ru.mexator.slovo.databinding.SceneGameBinding
import ru.mexator.slovo.databinding.SceneStartBinding
import ru.mexator.slovo.demo.DemoConfig
import ru.mexator.slovo.demo.DemoLayer
import ru.mexator.slovo.game.GameController
import ru.mexator.slovo.game.GameState
import ru.mexator.slovo.game.GameStatus
import ru.mexator.slovo.game.Mark
import ru.mexator.slovo.game.SubmitResult
import ru.mexator.core.ui.widget.KeyboardView
import ru.mexator.slovo.transition.CenteredChangeBounds
import ru.mexator.slovo.transition.ChangeColorTransition
import ru.mexator.slovo.transition.DemoTransitions
import ru.mexator.slovo.transition.requireTargetedVisibility
import ru.mexator.core.ui.R as UiR

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
        const val CANCEL_DURATION = 350L

        /** Pause between the two Explode passes of a restart (see explodeRestart). */
        const val RESTART_GAP = 60L
    }

    private val demoTimings
        get() = DemoTransitions.Timings(
            color = COLOR_DURATION,
            container = CONTAINER_DURATION,
            visibility = VISIBILITY_DURATION,
            explode = EXPLODE_DURATION,
            cancel = CANCEL_DURATION,
            slowMo = DemoConfig.durationMultiplier,
        )

    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: GameController
    private var renderedRows = 0

    /** Scene views are re-inflated per scene swap — render() only touches live ones. */
    private var startViews: SceneStartBinding? = null
    private var gameViews: SceneGameBinding? = null

    // Палитра — в ресурсах core-ui (colors.xml): одни цвета на поле, финал, «Словарь».
    // lazy — базовый контекст Activity ещё не прикреплен на этапе инициализации полей.
    private val emptyColor by lazy { getColor(UiR.color.board_cell_default) }
    private val correctColor by lazy { getColor(UiR.color.mark_correct) }
    private val presentColor by lazy { getColor(UiR.color.mark_present) }
    private val absentColor by lazy { getColor(UiR.color.mark_absent) }

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
        Scene(binding.sceneRoot, views.root).enter() // first show: no transition
        views.playButton.setOnClickListener { startGame() }
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
        val rows: List<View> = views.board.rows.toList()

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
    private fun explodeFromBoard(board: View, rows: List<View>): Explode {
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
        views.keyboard.onKey = { key ->
            when (key) {
                is KeyboardView.Key.Letter -> controller.type(key.char)
                KeyboardView.Key.Backspace -> controller.backspace()
                KeyboardView.Key.Submit -> submitGuess()
            }
        }
        views.hintCard.setOnClickListener { toggleHint(views) }

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
        gameViews?.board?.rows?.forEachIndexed { rowIdx, row ->
            val submitted = state.rows.getOrNull(rowIdx)
            val isCurrent = rowIdx == state.rows.size && state.status == GameStatus.PLAYING
            for (colIdx in 0 until row.columns) {
                val letter = when {
                    submitted != null -> submitted.word.getOrNull(colIdx)
                    isCurrent -> state.currentInput.getOrNull(colIdx)
                    else -> null
                }
                row.setLetter(colIdx, letter)
                row.setColor(
                    colIdx,
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
            binding.wordChip.setWord(state.target)
        }
        // Пример 4: рестарт — рядов стало меньше, чем в прошлом рендере —
        // возвращаем карточку подсказки в свёрнутое состояние. На
        // exploded-перезапуске флип snapping-ом: проход 2 таргетит Explode
        // только на ряды. Во время живой игры (rows не убывали) не трогаем.
        if (state.rows.size < renderedRows) {
            gameViews?.hintText?.visibility = View.GONE
            gameViews?.hintToggle?.setText(R.string.show_hint)
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

    /**
     * Пример 4: тап по строке-ссылке внутри карточки раскрывает/сворачивает
     * подсказку. Вооружаем проход ДО мутаций (как в submitGuess); кода отмены
     * нет — быстрые тапы просто вооружают новые проходы, библиотека сама
     * разворачивает анимацию с текущего кадра. Слой CANCEL выключен —
     * hintToggle вернёт null и флип будет мгновенным (Пример 0).
     */
    private fun toggleHint(views: SceneGameBinding) {
        val show = views.hintText.visibility != View.VISIBLE
        DemoTransitions.hintToggle(
            cancelOn = DemoConfig.isEnabled(DemoLayer.CANCEL),
            timings = demoTimings,
        )?.let { TransitionManager.beginDelayedTransition(binding.root, it) }
        views.hintText.visibility = if (show) View.VISIBLE else View.GONE
        views.hintToggle.setText(if (show) R.string.hide_hint else R.string.show_hint)
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
}
