package ru.mexator.slovo

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import ru.mexator.core.dict.SlovarContract
import ru.mexator.core.ui.R as UiR
import ru.mexator.slovo.databinding.FragmentWinBinding
import ru.mexator.slovo.demo.DemoConfig
import ru.mexator.slovo.demo.DemoLayer
import ru.mexator.slovo.transition.DemoTransitions

/**
 * Пример 5: экран победы как фрагмент. Добавляется ПОВЕРХ GameFragment (add,
 * не replace) c выигравшим рядом в роли shared element — ряд летит из поля в
 * шапку, остальное проявляется Fade. Шапка — не копия чипа, а тот же
 * BoardRowView той же геометрии: отсюда ряд полетит дальше, в шапку статьи
 * (второй перелёт этого примера) и в «Словарь» (Пример 6, уже между процессами).
 */
class WinFragment : Fragment(R.layout.fragment_win) {

    companion object {
        private const val ARG_WORD = "word"
        private const val ARG_WON = "won"

        fun newInstance(word: String, won: Boolean): WinFragment = WinFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_WORD, word)
                putBoolean(ARG_WON, won)
            }
        }
    }

    private val word: String get() = requireArguments().getString(ARG_WORD)!!
    private val won: Boolean get() = requireArguments().getBoolean(ARG_WON)

    private var _binding: FragmentWinBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentWinBinding.bind(view)

        // Переходы фреймворк читает после onViewCreated, когда view входящего
        // фрагмента уже создана, но ещё не добавлена в контейнер — успеваем до
        // захвата shared element.
        if (DemoConfig.isEnabled(DemoLayer.FRAGMENT)) {
            val timings = DemoTransitions.Timings(slowMo = DemoConfig.durationMultiplier)
            sharedElementEnterTransition = DemoTransitions.sharedRowFlight(timings)
            enterTransition = DemoTransitions.hopFade(timings)
            // Статья прячется поверх — экран победы гаснет Fade'ом, как и поле
            // под ним; возврат (pop, «Закрыть») — мгновенный.
            exitTransition = DemoTransitions.hopFade(timings)
        }
        // Возврат — мгновенный и без хитростей: обратный полёт ряда лёг бы в тот
        // же кадр, что и Explode-перезапуск Примера 3, — два прохода на соседних
        // корнях (см. wart в CLAUDE.md). null — это явное «никаких возвратных
        // переходов», иначе геттер подставит enter-переход по умолчанию.
        sharedElementReturnTransition = null
        returnTransition = null
        reenterTransition = null

        binding.winTitle.setText(if (won) R.string.won else R.string.lost)
        binding.winWordRow.apply {
            transitionName = SlovarContract.TRANSITION_WORD
            setWord(word)
            if (won) {
                // Клетки прилетают уже зелёными: летающая view — входящая, и во
                // время полёта видно именно её.
                val correct = requireContext().getColor(UiR.color.mark_correct)
                for (col in 0 until columns) setColor(col, correct)
            }
        }

        binding.btnWinMeaning.setOnClickListener { openArticle() }
        binding.btnWinSlovar.setOnClickListener {
            (activity as? MainActivity)?.openSlovar(word, binding.winWordRow)
        }
        binding.btnWinRestart.setOnClickListener {
            (activity as? MainActivity)?.onPlayAgain()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Второй перелёт Примера 5: шапка экрана победы → шапка статьи. Тот же
     * механизм, что и прилет сюда: источник shared element — фрагмент, УХОДЯЩИЙ
     * в транзакции (hide, не просто add поверх), + addSharedElement + переходы
     * на входящем. Ряд на поле под этим экраном всё ещё носит имя "word" — снять
     * до транзакции, иначе захват увидит два источника.
     */
    private fun openArticle() {
        (parentFragmentManager.findFragmentByTag(MainActivity.GAME_TAG) as? GameFragment)
            ?.clearSharedRowName()
        parentFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .addSharedElement(binding.winWordRow, SlovarContract.TRANSITION_WORD)
            .hide(this)
            .add(R.id.fragment_container, ArticleFragment.newInstance(word), MainActivity.ARTICLE_TAG)
            .addToBackStack(MainActivity.ARTICLE_TAG)
            .commit()
    }
}
