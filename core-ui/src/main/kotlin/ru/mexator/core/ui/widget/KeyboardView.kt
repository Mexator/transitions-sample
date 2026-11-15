package ru.mexator.core.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import ru.mexator.core.ui.R

/**
 * Русская экранная клавиатура как одна вьючка: кладётся в layout XML целиком —
 * `<ru.mexator.core.ui.widget.KeyboardView … android:transitionName="keyboard"/>` —
 * и строит три ряда клавиш сама. Без [onKey] клавиши инертны (стартовая сцена).
 * Раскраска — через [tint] готовыми цветами; вьюшка про игру не знает.
 *
 * Наследует LinearLayout и строит то же дерево, что раньше собиралось кодом в
 * Activity (контейнер → ряды → клавиши), поэтому переходы Примеров 2/3
 * продолжают матчить её по transitionName/id без изменений.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    /** Что нажали на клавиатуре. */
    sealed interface Key {
        data class Letter(val char: Char) : Key
        object Backspace : Key
        object Submit : Key
    }

    /** Нет слушателя — клавиши инертны (стартовая сцена). */
    var onKey: ((Key) -> Unit)? = null

    private val keyViews = mutableMapOf<Char, TextView>()
    private val keyColor = context.getColor(R.color.keyboard_key)

    /** ⏎ и ⌫ — не буквы: подсвечены, чтобы отличались одним взглядом. */
    private val actionKeyColor = context.getColor(R.color.keyboard_key_action)

    init {
        // Клавиатура всегда вертикальная стойка рядов — не настраивается.
        orientation = VERTICAL
        buildRows()
    }

    /** Перекрасить клавиши по буквам; остальные вернуть в базовый цвет. */
    fun tint(colors: Map<Char, Int>) {
        keyViews.forEach { (letter, view) ->
            view.setBackgroundColor(colors[letter] ?: keyColor)
        }
    }

    private fun buildRows() {
        listOf("ЙЦУКЕНГШЩЗХЪ", "ФЫВАПРОЛДЖЭ").forEach { letters ->
            val row = row()
            letters.forEach { c ->
                val k = key(c.toString(), 1f, keyColor) { onKey?.invoke(Key.Letter(c)) }
                keyViews[c] = k
                row.addView(k)
            }
            addView(row)
        }
        val bottom = row()
        bottom.addView(key("⏎", 1.5f, actionKeyColor) { onKey?.invoke(Key.Submit) })
        "ЯЧСМИТЬБЮ".forEach { c ->
            val k = key(c.toString(), 1f, keyColor) { onKey?.invoke(Key.Letter(c)) }
            keyViews[c] = k
            bottom.addView(k)
        }
        bottom.addView(key("⌫", 1.5f, actionKeyColor) { onKey?.invoke(Key.Backspace) })
        addView(bottom)
    }

    private fun row() = LinearLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        orientation = HORIZONTAL
    }

    /**
     * TextView, не Button: Material Button reserves insets for its label and clips
     * it at this height, which made the letters unreadable. A bold TextView with a
     * gray background and a ripple foreground behaves and reads like a key.
     */
    private fun key(label: String, weight: Float, background: Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            layoutParams = LayoutParams(0, dp(52), weight).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            setBackgroundColor(background)
            val ripple = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            foreground = resources.getDrawable(ripple.resourceId, context.theme)
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
