package dev.anton.slovar

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.anton.core.dict.Dictionary
import dev.anton.core.dict.SlovarContract
import dev.anton.core.dict.widget.BoardRowView

/**
 * Пример 6: receiving side of the cross-app shared element.
 * M0: static article. M3 adds postponeEnterTransition + simulated loading and a
 * debug toggle that drops transitionName to show the degraded (no) transition.
 */
class ArticleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article)

        val word = (intent.getStringExtra(SlovarContract.EXTRA_WORD) ?: Dictionary.wordOfTheDay())
            .uppercase()

        // Заголовок — ряд «клеток» (общий виджет с полем игры) и он же приёмник
        // cross-app shared element (Пример 6).
        findViewById<BoardRowView>(R.id.article_word_row).apply {
            transitionName = SlovarContract.TRANSITION_WORD
            setWord(word)
        }
        findViewById<TextView>(R.id.article_body).text =
            Dictionary.articleOf(word)?.body ?: getString(R.string.no_article)
        findViewById<android.view.View>(R.id.btn_close).setOnClickListener { finish() }
    }
}
