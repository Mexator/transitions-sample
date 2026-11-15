package ru.mexator.slovar

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.transition.Transition
import android.transition.TransitionValues
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Пример 6: цвет клеток ряда меняется В ПОЛЁТЕ. Летит и рисуется view
 * ПРИЁМНИКА, поэтому «Слово» заранее сообщает цвет источника (EXTRA_GREEN) и
 * приёмник стартует таким же — иначе на взлёте мгновенная подмена цвета.
 * Дальше этот переход ведёт цвет к [endColor]: прилёт в «Словарь» — «стекание»
 * разгаданности в бесцветный ряд; обратный перелёт тем же классом возвращает
 * зелёный к посадке в шапку победы.
 */
class CellColorFlight(private val endColor: Int) : Transition() {

    override fun getTransitionProperties(): Array<String> = arrayOf(PROP_COLORS)

    override fun captureStartValues(transitionValues: TransitionValues) =
        capture(transitionValues)

    override fun captureEndValues(transitionValues: TransitionValues) =
        capture(transitionValues)

    private fun capture(values: TransitionValues) {
        val row = values.view as? LinearLayout ?: return
        val colors = IntArray(row.childCount) { i ->
            (row.getChildAt(i).background as? ColorDrawable)?.color ?: return
        }
        values.values[PROP_COLORS] = colors
    }

    override fun createAnimator(
        sceneRoot: ViewGroup,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator? {
        val row = endValues?.view as? LinearLayout ?: return null
        val start = startValues?.values?.get(PROP_COLORS) as? IntArray ?: return null
        val cells = (0 until row.childCount).mapNotNull { row.getChildAt(it) as? TextView }
        if (cells.size != start.size) return null
        if (start.all { it == endColor }) return null // уже целились — летим как есть

        val evaluator = ArgbEvaluator()
        return ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                cells.forEachIndexed { i, cell ->
                    cell.setBackgroundColor(
                        evaluator.evaluate(fraction, start[i], endColor) as Int,
                    )
                }
            }
        }
    }

    private companion object {
        const val PROP_COLORS = "ru.mexator.slovar:cellColors"
    }
}
