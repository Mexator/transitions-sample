package ru.mexator.slovo.transition

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.ViewGroup
import androidx.transition.Transition
import androidx.transition.TransitionValues

/**
 * Пример 2: ChangeBounds for a container whose children are laid out at final
 * size animates the container's bounds only — the children stay at final size
 * and get clipped to the shrinking frame mid-flight. This variant never touches
 * the layout bounds: it replays the start bounds onto the final view with
 * scale + translation around the center, so the view grows out of the start
 * rect symmetrically and the children scale along with it. Nothing is clipped.
 *
 * Target it at one shared element: `CenteredChangeBounds().addTarget("board")`.
 * Like ChangeBounds, it matches views across scenes by transitionName.
 */
class CenteredChangeBounds : Transition() {

    override fun getTransitionProperties(): Array<String> = arrayOf(PROP_BOUNDS)

    override fun captureStartValues(transitionValues: TransitionValues) =
        captureValues(transitionValues)

    override fun captureEndValues(transitionValues: TransitionValues) =
        captureValues(transitionValues)

    private fun captureValues(values: TransitionValues) {
        val view = values.view
        if (view.width == 0 || view.height == 0) return
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        values.values[PROP_BOUNDS] = Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height,
        )
    }

    override fun createAnimator(
        sceneRoot: ViewGroup,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator? {
        val start = startValues?.values?.get(PROP_BOUNDS) as? Rect ?: return null
        val end = endValues?.values?.get(PROP_BOUNDS) as? Rect ?: return null
        if (start == end) return null
        val view = endValues!!.view

        // At fraction 0 the final view is scaled down to the start size and
        // shifted so its center sits on the start rect's center — a faithful
        // copy of the small field, but with live (scaled) children. From there
        // scale and translation run to identity: the interpolated center moves
        // linearly between the two centers, so the growth is always symmetric
        // around it.
        val startScaleX = start.width().toFloat() / view.width
        val startScaleY = start.height().toFloat() / view.height
        val startDx = start.exactCenterX() - end.exactCenterX()
        val startDy = start.exactCenterY() - end.exactCenterY()

        fun apply(fraction: Float) {
            view.pivotX = view.width / 2f
            view.pivotY = view.height / 2f
            view.scaleX = lerp(startScaleX, 1f, fraction)
            view.scaleY = lerp(startScaleY, 1f, fraction)
            view.translationX = lerp(startDx, 0f, fraction)
            view.translationY = lerp(startDy, 0f, fraction)
        }
        apply(0f)
        return ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animator -> apply(animator.animatedValue as Float) }
        }
    }

    private fun lerp(from: Float, to: Float, fraction: Float): Float =
        from + (to - from) * fraction

    private companion object {
        const val PROP_BOUNDS = "ru.mexator.slovo:bounds"
    }
}
