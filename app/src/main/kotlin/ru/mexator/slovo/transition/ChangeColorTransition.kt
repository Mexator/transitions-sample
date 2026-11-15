package ru.mexator.slovo.transition

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.TextView
import androidx.transition.Transition
import androidx.transition.TransitionValues

/**
 * Пример 1: a custom Transition that animates a TextView's background color.
 *
 * The framework captures start/end values for every view in both scenes; here we
 * snapshot the ColorDrawable's color. After the app changes the cell backgrounds,
 * createAnimator interpolates between the captured colors — the library never
 * touches the "old" color itself, it replays it onto the current view.
 */
class ChangeColorTransition : Transition() {

    override fun captureStartValues(transitionValues: TransitionValues) =
        captureValues(transitionValues)

    override fun captureEndValues(transitionValues: TransitionValues) =
        captureValues(transitionValues)

    private fun captureValues(values: TransitionValues) {
        val view = values.view
        if (view is TextView && view.background is ColorDrawable) {
            values.values[PROP_COLOR] = (view.background as ColorDrawable).color
        }
    }

    override fun createAnimator(
        sceneRoot: ViewGroup,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator? {
        val startColor = startValues?.values?.get(PROP_COLOR) as? Int ?: return null
        val endColor = endValues?.values?.get(PROP_COLOR) as? Int ?: return null
        if (startColor == endColor) return null

        val view = endValues!!.view as TextView
        return ValueAnimator.ofArgb(startColor, endColor).apply {
            addUpdateListener { animator ->
                view.setBackgroundColor(animator.animatedValue as Int)
            }
        }
    }

    private companion object {
        const val PROP_COLOR = "ru.mexator.slovo:color"
    }
}
