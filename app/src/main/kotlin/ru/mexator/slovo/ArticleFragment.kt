package ru.mexator.slovo

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import ru.mexator.core.dict.Dictionary
import ru.mexator.core.dict.SlovarContract
import ru.mexator.slovo.databinding.FragmentArticleBinding
import ru.mexator.slovo.demo.DemoConfig
import ru.mexator.slovo.demo.DemoLayer
import ru.mexator.slovo.transition.DemoTransitions

/**
 * Пример 5: статья внутри игрового приложения. Заголовок — тот же ряд «клеток»,
 * что и шапка экрана победы: со слоем FRAGMENT ряд прилетает сюда shared
 * element'ом; со слоем OFF (путь финала-оверлея) статья открывается без
 * анимаций (Пример 0).
 */
class ArticleFragment : Fragment(R.layout.fragment_article) {

    companion object {
        private const val ARG_WORD = "word"

        fun newInstance(word: String): ArticleFragment = ArticleFragment().apply {
            arguments = Bundle().apply { putString(ARG_WORD, word) }
        }
    }

    private var _binding: FragmentArticleBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentArticleBinding.bind(view)

        val word = requireArguments().getString(ARG_WORD)!!.uppercase()

        if (DemoConfig.isEnabled(DemoLayer.FRAGMENT)) {
            val timings = DemoTransitions.Timings(slowMo = DemoConfig.durationMultiplier)
            sharedElementEnterTransition = DemoTransitions.sharedRowFlight(timings)
            enterTransition = DemoTransitions.hopFade(timings)
        }
        // Закрытие — мгновенное: обратный полёт не нужен, а null обязателен —
        // иначе геттер подставит enter-переход по умолчанию.
        sharedElementReturnTransition = null
        returnTransition = null

        binding.articleWordRow.apply {
            transitionName = SlovarContract.TRANSITION_WORD
            setWord(word)
        }
        binding.articleBody.text =
            Dictionary.articleOf(word)?.body ?: getString(R.string.no_article)
        binding.btnClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
