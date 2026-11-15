package ru.mexator.slovar

import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ru.mexator.core.dict.Dictionary
import ru.mexator.core.dict.SlovarContract
import ru.mexator.core.ui.widget.BoardRowView

/**
 * Пример 6: приёмная сторона cross-app shared element. Отправитель («Слово»)
 * передаёт пару (view, transitionName) в ActivityOptions; здесь ряд «клеток» —
 * заголовок статьи — ловит прилёт по тому же имени. Загрузка статьи имитируется
 * задержкой: [androidx.fragment.app.FragmentActivity.supportPostponeEnterTransition]
 * держит переход, пока контент не готов, — без него ряд прилетал бы на пустой
 * экран, а текст «хлопнулся» бы поверх после.
 */
class ArticleActivity : AppCompatActivity() {

    private companion object {
        /** Имитация загрузки статьи — переход ждёт готовности текста. */
        const val LOAD_DELAY_MS = 600L

        /**
         * Ручная длительность прилёта: системный дефолт ~300 мс — слишком
         * быстро, чтобы наловить кадров для proof'ов и ×1-записи.
         */
        const val FLIGHT_DURATION_MS = 900L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Window-переходы должны быть включены у ОБОИХ окон — у отправителя
        // (MainActivity «Слова») и здесь. Иначе options-бандл молча не оживёт.
        if (!window.hasFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)) {
            window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article)

        val word = (intent.getStringExtra(SlovarContract.EXTRA_WORD) ?: Dictionary.wordOfTheDay())
            .uppercase()

        // Заголовок — тот же ряд «клеток», из которого собрано поле игры
        // (общий виджет core-ui); приёмник shared element.
        findViewById<BoardRowView>(R.id.article_word_row).apply {
            transitionName = SlovarContract.TRANSITION_WORD
            setWord(word)
        }

        supportPostponeEnterTransition()
        // Системный дефолт (скрытый Move ~300 мс) заменяем публичным ChangeBounds
        // той же природы — геометрия рядов отправителя и приёмника идентична,
        // полёт чисто трансляционный.
        window.sharedElementEnterTransition =
            ChangeBounds().apply { duration = FLIGHT_DURATION_MS }
        window.enterTransition = Fade()

        val body = findViewById<TextView>(R.id.article_body)
        window.decorView.postDelayed(
            {
                body.text = Dictionary.articleOf(word)?.body ?: getString(R.string.no_article)
                supportStartPostponedEnterTransition()
            },
            LOAD_DELAY_MS,
        )

        findViewById<android.view.View>(R.id.btn_close).setOnClickListener { finish() }
    }
}
