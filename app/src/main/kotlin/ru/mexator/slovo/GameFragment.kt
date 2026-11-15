package ru.mexator.slovo

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.transition.Scene
import androidx.transition.Transition
import androidx.transition.TransitionManager
import ru.mexator.core.dict.Dictionary
import ru.mexator.core.dict.SlovarContract
import ru.mexator.core.ui.widget.KeyboardView
import ru.mexator.slovo.databinding.FragmentGameBinding
import ru.mexator.slovo.databinding.SceneGameBinding
import ru.mexator.slovo.databinding.SceneStartBinding
import ru.mexator.slovo.demo.DemoConfig
import ru.mexator.slovo.demo.DemoLayer
import ru.mexator.slovo.game.GameController
import ru.mexator.slovo.game.GameState
import ru.mexator.slovo.game.GameStatus
import ru.mexator.slovo.game.Mark
import ru.mexator.slovo.game.SubmitResult
import ru.mexator.slovo.transition.DemoTransitions
import ru.mexator.core.ui.R as UiR

/**
 * The game renders [GameState] into whichever scene is on screen. Scenes are
 * swapped with TransitionManager (Пример 2), recoloring runs through a custom
 * Transition (Пример 1). Every animated layer is gated by [DemoConfig] so the
 * debug panel can turn the app back into its "no animations" Пример 0 state.
 *
 * Пример 5: живёт в fragment_container и никогда не снимается — WinFragment и
 * ArticleFragment добавляются ПОВЕРХ (hide + add), поэтому инстансы рядов поля
 * переживают экран победы. Выигрыш со слоем FRAGMENT — навигация на WinFragment,
 * и ряд, угадавший слово, летит в шапку shared element'ом. «Ещё раз» по решению
 * Антона мгновенный: поле не двигается, готовая пустая доска (двухпроходный
 * Explode-рестарт Примера 3 жил до тега ex5 — материал для слайда про
 * промежуточные сцены ищи там).
 */
class GameFragment : Fragment(R.layout.fragment_game) {

    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!

    private lateinit var controller: GameController
    private var renderedRows = 0

    /**
     * Команда панели, пришедшая раньше, чем создано view фрагмента (холодный
     * старт с demo.command extra: commitNow добавляет фрагмент, но.onViewCreated
     * executes later) — исполним, как только сможем рендерить.
     */
    private var pendingDemoCommand: String? = null

    /** Scene views are re-inflated per scene swap — render() only touches live ones. */
    private var startViews: SceneStartBinding? = null
    private var gameViews: SceneGameBinding? = null

    // Палитра — в ресурсах core-ui (colors.xml): одни цвета на поле, финал, «Словарь».
    private val emptyColor by lazy { requireContext().getColor(UiR.color.board_cell_default) }
    private val correctColor by lazy { requireContext().getColor(UiR.color.mark_correct) }
    private val presentColor by lazy { requireContext().getColor(UiR.color.mark_present) }
    private val absentColor by lazy { requireContext().getColor(UiR.color.mark_absent) }

    private val demoTimings
        get() = DemoTransitions.Timings(slowMo = DemoConfig.durationMultiplier)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGameBinding.bind(view)

        // Пример 5: когда WinFragment прячется поверх (hide ниже в showWinFragment),
        // поле гаснет Fade'ом, а не исчезает мигом — иначе под рядом, летящим в
        // шапку, мелькнёт фон окна. Возврат (pop бэкстека) — мгновенный.
        exitTransition = DemoTransitions.hopFade(
            DemoTransitions.Timings(slowMo = DemoConfig.durationMultiplier),
        )
        reenterTransition = null

        controller = GameController(Dictionary, ::render)

        showStartScene()

        binding.btnMeaning.setOnClickListener { openMeaning() }
        binding.btnSlovar.setOnClickListener { openSlovar() }
        binding.btnRestart.setOnClickListener { startGame() }

        render(controller.state)

        pendingDemoCommand?.let { command ->
            pendingDemoCommand = null
            onDemoCommand(command)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        gameViews = null
        startViews = null
    }

    /** Sent by the debug demo panel via MainActivity: state jumps for recording. */
    fun onDemoCommand(command: String) {
        if (!::controller.isInitialized) {
            pendingDemoCommand = command
            return
        }
        when (command) {
            MainActivity.COMMAND_START -> startGame()
            MainActivity.COMMAND_SURRENDER -> {
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

    /**
     * Убирает transitionName с ряда-победителя. Имя нужно только для захвата
     * при перелёте WinFragment → ArticleFragment под этим экраном остаётся ещё
     * один ряд с тем же именем — второй источник сломал бы захват.
     */
    fun clearSharedRowName() {
        gameViews?.board?.rows?.forEach { it.transitionName = null }
    }

    private fun showStartScene() {
        val views = SceneStartBinding.inflate(layoutInflater, binding.sceneRoot, false)
        startViews = views
        Scene(binding.sceneRoot, views.root).enter() // first show: no transition
        views.playButton.setOnClickListener { startGame() }
    }

    fun startGame(animated: Boolean = true) {
        // Ряд-победитель прошлого матча больше не shared element (см. openArticle
        // в WinFragment — второй носитель имени ломает захват).
        clearSharedRowName()
        if (gameViews == null) {
            controller.startNewGame(DemoConfig.wordOverride)
            showGameScene(
                animated = animated && DemoConfig.isEnabled(DemoLayer.CONTAINER),
            )
        } else {
            // Мгновенный рестарт по решению Антона: поле не двигается, «Ещё раз»
            // оставляет готовую пустую доску. Никаких проходов на этот флип не
            // вооружаем — render() чистит ряды, возвращает клавиатуру и валит финал.
            controller.startNewGame(DemoConfig.wordOverride)
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
                Toast.makeText(requireContext(), R.string.not_a_word, Toast.LENGTH_SHORT).show()
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
        val fragmentOn = DemoConfig.isEnabled(DemoLayer.FRAGMENT)
        // Пример 3: the keyboard's GONE/VISIBLE flip is what Slide animates; the
        // finale's is what Fade animates (in whichever armed pass changed them).
        // Слоем FRAGMENT финал замещён WinFragment — оверлей остаётся GONE.
        gameViews?.keyboard?.visibility = if (finished) View.GONE else View.VISIBLE
        binding.finale.visibility =
            if (finished && !fragmentOn) View.VISIBLE else View.GONE
        if (finished) {
            if (fragmentOn) {
                scheduleWinScreen(state)
            } else {
                binding.finaleTitle.setText(
                    if (state.status == GameStatus.WON) R.string.won else R.string.lost,
                )
                binding.wordChip.transitionName = SlovarContract.TRANSITION_WORD
                binding.wordChip.setWord(state.target)
            }
        }
        // Пример 4: рестарт — рядов стало меньше, чем в прошлом рендере —
        // возвращаем карточку подсказки в свёрнутое состояние (мгновенный
        // флип, без прохода). Во время живой игры (rows не убывали) не трогаем.
        if (state.rows.size < renderedRows) {
            gameViews?.hintText?.visibility = View.GONE
            gameViews?.hintToggle?.setText(R.string.show_hint)
        }
        renderedRows = state.rows.size
    }

    /**
     * Пример 5: конец игры — навигация на WinFragment. Полёт ряда стартует
     * ПОСЛЕ прохода раскраски/видимости (см. wart про два прохода в одном кадре
     * в CLAUDE.md) — postDelayed на длительность вооружённого прохода, тот же
     * приём, что и в explodeRestart. На проигрыше лететь нечего: WinFragment
     * просто проявляется, имя на ряд не вешается.
     */
    private fun scheduleWinScreen(state: GameState) {
        if (state.status == GameStatus.WON) {
            gameViews?.board?.rows?.getOrNull(state.rows.size - 1)?.transitionName =
                SlovarContract.TRANSITION_WORD
        }
        val timings = demoTimings
        val delay = when {
            DemoConfig.isEnabled(DemoLayer.COLOR) -> timings.scaled(timings.color)
            DemoConfig.isEnabled(DemoLayer.VISIBILITY) -> timings.scaled(timings.visibility)
            else -> 0L
        }
        binding.root.postDelayed(
            {
                // За время задержки игра могла перезапуститься (демо-панель,
                // «Ещё раз») — навигация только если матч всё ещё закончен.
                if (controller.state.status == GameStatus.PLAYING) return@postDelayed
                showWinFragment(state.target, state.status == GameStatus.WON)
            },
            delay,
        )
    }

    private fun showWinFragment(word: String, won: Boolean) {
        val sharedRow = gameViews?.board?.rows
            ?.firstOrNull { it.transitionName == SlovarContract.TRANSITION_WORD }
        val transaction = parentFragmentManager.beginTransaction().setReorderingAllowed(true)
        if (won && sharedRow != null) {
            transaction.addSharedElement(sharedRow, SlovarContract.TRANSITION_WORD)
        }
        // hide, а не просто add поверх: источник shared element фреймворк ищет
        // только среди фрагментов, УХОДЯЩИХ в этой транзакции. Голый add поверх
        // живого GameFragment источника не захватывает — ряд просто проявляется
        // fade'ом на месте шапки (поймано кадрами на телефоне). Поп бэкстека
        // вернёт show сам.
        transaction
            .hide(this)
            .add(R.id.fragment_container, WinFragment.newInstance(word, won), MainActivity.WIN_TAG)
            .addToBackStack(MainActivity.WIN_TAG)
            .commit()
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

    /**
     * Пример 5, путь слоя OFF: финал-оверлей → статья без анимаций (Пример 0).
     * Со слоем ON этот же ArticleFragment открывает WinFragment с перелётом.
     */
    private fun openMeaning() {
        parentFragmentManager.beginTransaction()
            .add(R.id.fragment_container, ArticleFragment.newInstance(controller.state.target))
            .addToBackStack(null)
            .commit()
    }

    private fun openSlovar() {
        // Путь слоя FRAGMENT OFF: источник cross-app перелёта — чип финала
        // (то же имя "word", та же геометрия ряда).
        (activity as? MainActivity)?.openSlovar(controller.state.target, binding.wordChip)
    }

    private fun Transition.demoDuration(base: Long): Transition = apply {
        duration = (base * DemoConfig.durationMultiplier).toLong()
    }
}
