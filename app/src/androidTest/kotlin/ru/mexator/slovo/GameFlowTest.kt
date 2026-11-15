package ru.mexator.slovo

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.mexator.core.dict.Dictionary
import ru.mexator.core.ui.widget.BoardRowView
import ru.mexator.core.ui.widget.BoardView
import ru.mexator.slovo.demo.DemoConfig
import ru.mexator.slovo.demo.DemoLayer
import java.util.EnumSet
import ru.mexator.core.ui.R as UiR

/**
 * Happy path игры одним инструментальным тестом: старт → подсказка → ввод с
 * правкой → неправильная попытка с разметкой → победа → финал → «Ещё раз».
 *
 * Все слои DemoConfig выключены (= Пример 0): рендеры синхронные, ни одного
 * beginDelayedTransition — тесту не нужно ждать анимаций. Анимационные слои
 * проверяются кадрами (ui-verify), а не здесь; зато прохождение теста — это
 * само по себе proof of life: мёртвый процесс или stale-кадр не «набирают»
 * буквы в клетках.
 *
 * Слова детерминированы через DemoConfig.wordOverride: цель — СЛОВО
 * (Dictionary.articles.first()), неверная попытка — БУКВА: валидное слово,
 * против СЛОВО даёт известную разметку (В на 4-й позиции — зелёная, Б — серая).
 */
@RunWith(AndroidJUnit4::class)
class GameFlowTest {

    private companion object {
        const val TARGET = "СЛОВО"
        const val WRONG_GUESS = "БУКВА"
    }

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        // Тест идёт в процессе приложения: конфиг читается теми же статиками.
        DemoConfig.enabledLayers = EnumSet.noneOf(DemoLayer::class.java)
        DemoConfig.durationMultiplier = 1f
        DemoConfig.wordOverride = TARGET
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        DemoConfig.wordOverride = null
        DemoConfig.enabledLayers = EnumSet.allOf(DemoLayer::class.java)
        scenario.close()
    }

    @Test
    fun happyPath_fromStartScreenToWinAndRestart() {
        // 1. Стартовая сцена: кнопка «Играть»
        onView(allOf(withId(R.id.play_button), withText(R.string.play)))
            .check(matches(isDisplayed()))
        onView(withId(R.id.play_button)).perform(click())

        // 2. Игровая сцена: полный запас попыток
        onView(withId(R.id.game_status))
            .check(matches(withText(targetContext.getString(R.string.attempts_left, 6))))

        // 3. Подсказка (Пример 4): тап по строке-ссылке → текст подсказки статьи,
        //    лейбл переключился на «Скрыть». Слой CANCEL выключен — флип мгновенный.
        onView(withId(R.id.hint_toggle)).perform(click())
        onView(withId(R.id.hint_text)).check(
            matches(
                allOf(
                    isDisplayed(),
                    withText(Dictionary.articleOf(TARGET)!!.hint),
                ),
            ),
        )
        onView(withId(R.id.hint_toggle)).check(matches(withText(R.string.hide_hint)))

        // 4. Ввод: полное слово эхом падает в текущий ряд
        WRONG_GUESS.forEach { tapKey(it.toString()) }
        onView(withId(R.id.board)).check(matches(rowShows(0, WRONG_GUESS)))

        // 5. ⏎: попытка принята, ряд размечен, счётчик уменьшился
        tapKey("⏎")
        onView(withId(R.id.board)).check(
            matches(
                allOf(
                    cellColorIs(0, 0, UiR.color.mark_absent),   // Б нет в СЛОВО
                    cellColorIs(0, 3, UiR.color.mark_correct),  // В на своём месте
                ),
            ),
        )
        onView(withId(R.id.game_status))
            .check(matches(withText(targetContext.getString(R.string.attempts_left, 5))))

        // 6. Победа: финал с заголовком, слово в чипе, кнопки действий
        TARGET.forEach { tapKey(it.toString()) }
        tapKey("⏎")
        onView(withId(R.id.finale)).check(matches(isDisplayed()))
        onView(withId(R.id.finale_title)).check(matches(withText(R.string.won)))
        onView(withId(R.id.word_chip)).check(matches(chipShows(TARGET)))
        onView(allOf(withId(R.id.btn_restart), withText(R.string.restart)))
            .check(matches(isDisplayed()))

        // 7. «Ещё раз»: поле пустое, клавиатура на месте, финал скрыт
        onView(withId(R.id.btn_restart)).perform(click())
        onView(withId(R.id.game_status))
            .check(matches(withText(targetContext.getString(R.string.attempts_left, 6))))
        onView(withId(R.id.board)).check(matches(rowShows(0, "")))
        onView(withId(R.id.keyboard)).check(matches(isDisplayed()))
        onView(withId(R.id.finale))
            .check(matches(withEffectiveVisibility(androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE)))
    }

    @Test
    fun backspace_erasesLastLetter_andRetypingCompletesWord() {
        onView(withId(R.id.play_button)).perform(click())
        onView(withId(R.id.game_status))
            .check(matches(withText(targetContext.getString(R.string.attempts_left, 6))))

        // Набираем слово целиком, стираем последнюю букву, добираем обратно
        WRONG_GUESS.forEach { tapKey(it.toString()) }
        onView(withId(R.id.board)).check(matches(rowShows(0, WRONG_GUESS)))
        tapKey("⌫")
        onView(withId(R.id.board)).check(matches(rowShows(0, "БУКВ")))
        tapKey("А")
        onView(withId(R.id.board)).check(matches(rowShows(0, WRONG_GUESS)))
    }

    /** Тап по клавише экранной клавиатуры: ключи — TextView с текстом буквы. */
    private fun tapKey(label: String) {
        onView(allOf(withText(label), isDescendantOfA(withId(R.id.keyboard)))).perform(click())
    }

    /** Ряд поля показывает слово: клетки BoardRowView склеиваются в строку. */
    private fun rowShows(row: Int, expected: String): Matcher<View> =
        object : BoundedMatcher<View, BoardView>(BoardView::class.java) {
            override fun matchesSafely(board: BoardView): Boolean =
                board.rows[row].cells.joinToString("") { it.text.toString() } == expected

            override fun describeTo(description: Description) {
                description.appendText("board row ").appendValue(row)
                    .appendText(" shows ").appendValue(expected)
            }

            override fun describeMismatch(item: Any?, description: Description) {
                val board = item as? BoardView
                if (board == null) {
                    super.describeMismatch(item, description)
                } else {
                    description.appendText("rows were ")
                    board.rows.forEachIndexed { i, r ->
                        description.appendValue("$i:'${r.cells.joinToString("") { it.text.toString() }}'")
                    }
                }
            }
        }

    /** Клетка [row]/[col] залита цветом [colorRes]. */
    private fun cellColorIs(row: Int, col: Int, colorRes: Int): Matcher<View> {
        val expected = targetContext.getColor(colorRes)
        return object : BoundedMatcher<View, BoardView>(BoardView::class.java) {
            override fun matchesSafely(board: BoardView): Boolean =
                (board.rows[row].cells[col].background as? ColorDrawable)?.color == expected

            override fun describeTo(description: Description) {
                description.appendText("cell [").appendValue(row).appendValue(col)
                    .appendText("] painted with color resource ")
                    .appendValue(targetContext.resources.getResourceName(colorRes))
            }
        }
    }

    /** Чип финала показывает слово теми же клетками BoardRowView. */
    private fun chipShows(expected: String): Matcher<View> =
        object : BoundedMatcher<View, BoardRowView>(BoardRowView::class.java) {
            override fun matchesSafely(chip: BoardRowView): Boolean =
                chip.cells.joinToString("") { it.text.toString() } == expected

            override fun describeTo(description: Description) {
                description.appendText("word chip shows ").appendValue(expected)
            }
        }
}
