package dev.anton.slovo

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import dev.anton.core.dict.Dictionary
import dev.anton.core.dict.SlovarContract

/** Пример 5: the article inside the game app. M0: static, no shared element yet. */
class ArticleFragment : Fragment(R.layout.fragment_article) {

    companion object {
        private const val ARG_WORD = "word"

        fun newInstance(word: String): ArticleFragment = ArticleFragment().apply {
            arguments = Bundle().apply { putString(ARG_WORD, word) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val word = requireArguments().getString(ARG_WORD)!!.uppercase()

        view.findViewById<TextView>(R.id.article_word).apply {
            transitionName = SlovarContract.TRANSITION_WORD
            text = word
        }
        view.findViewById<TextView>(R.id.article_body).text =
            Dictionary.articleOf(word)?.body ?: getString(R.string.no_article)
        view.findViewById<View>(R.id.btn_close).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
}
