package ru.mexator.slovar

import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionSet
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import ru.mexator.core.dict.Dictionary
import ru.mexator.core.dict.SlovarContract
import ru.mexator.core.ui.widget.BoardRowView
import ru.mexator.core.ui.R as UiR

/**
 * Пример 6: приёмная сторона cross-app shared element. Отправитель («Слово»)
 * передаёт пару (view, transitionName) в ActivityOptions; здесь ряд «клеток» —
 * заголовок статьи — ловит прилёт по тому же имени. Загрузка статьи имитируется
 * задержкой: postpone держит переход, пока контент не готов, — без него ряд
 * прилетал бы на пустой экран, а текст «хлопнулся» бы поверх после.
 */
class ArticleActivity : AppCompatActivity() {

    private companion object {
        /** Имитация загрузки статьи — переход ждёт готовности текста. */
        const val LOAD_DELAY_MS = 400L
    }

    /**
     * Полётный набор: ChangeBounds ведёт геометрию (ряды одинаковой геометрии,
     * полёт чисто трансляционный; системный дефолт-Move скрыт и ~300 мс),
     * [CellColorFlight] ведёт цвет к [endColor]. Длительность — в контракте:
     * у обоих окон она должна совпадать, иначе рука-off на границе дёргается.
     */
    private fun rowFlight(endColor: Int): TransitionSet =
        TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(CellColorFlight(endColor))
            duration = SlovarContract.FLIGHT_DURATION_MS
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
        val greenRow = intent.getBooleanExtra(SlovarContract.EXTRA_GREEN, false)

        // Летит и рисуется view ПРИЁМНИКА — она обязана совпадать с источником
        // пиксель-в-пиксель, иначе на взлёте зелёный ряд победы молча
        // подменяется серым (поймано кадрами) и иллюзия «той же вьюшки»
        // умирает на первом кадре.
        findViewById<BoardRowView>(R.id.article_word_row).apply {
            transitionName = SlovarContract.TRANSITION_WORD
            setWord(word)
            if (greenRow) {
                val correct = getColor(UiR.color.mark_correct)
                for (col in 0 until columns) setColor(col, correct)
            }
        }

        supportPostponeEnterTransition()
        // Полёт = геометрия + ЦВЕТ: ряд прилетает в «Словарь» бесцветным
        // (стекание разгаданности в дороге), а обратным перелётом возвращает
        // зелёный к шапке победы. С серым источником (чип финала) оба
        // цветовых прохода вырождаются в чистую геометрию.
        window.sharedElementEnterTransition =
            rowFlight(getColor(UiR.color.board_cell_default))
        window.sharedElementReturnTransition =
            rowFlight(if (greenRow) getColor(UiR.color.mark_correct) else getColor(UiR.color.board_cell_default))
        // Герой один: тело статьи проявляется во ВТОРОЙ половине полёта и
        // достраивается к прилёту ряда, а не опережает его на пол-экрана.
        window.enterTransition = Fade().apply {
            startDelay = SlovarContract.FLIGHT_DURATION_MS / 2
            duration = SlovarContract.FLIGHT_DURATION_MS / 2
        }

        val body = findViewById<TextView>(R.id.article_body)
        window.decorView.postDelayed(
            {
                body.text = Dictionary.articleOf(word)?.body ?: getString(R.string.no_article)
                supportStartPostponedEnterTransition()
            },
            LOAD_DELAY_MS,
        )

        findViewById<android.view.View>(R.id.btn_close).setOnClickListener { close() }
    }

    /**
     * finishAfterTransition, а не finish: только так запускается ОБРАТНЫЙ
     * перелёт ряда в «Слово». Обычный finish — жёсткий кат без перехода
     * (поймано кадрами: и кнопка, и системный Back). При открытии без
     * transition-бандла деградирует в обычный finish.
     */
    private fun close() {
        ActivityCompat.finishAfterTransition(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        close()
    }
}
