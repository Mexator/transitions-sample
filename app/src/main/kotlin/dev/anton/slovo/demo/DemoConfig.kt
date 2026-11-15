package dev.anton.slovo.demo

import java.util.EnumSet

/**
 * One layer per talk example. The debug panel (src/debug) flips these live;
 * the game reads them on every state change. All layers OFF = Пример 0,
 * the "app without animations" state — recorded straight from the device,
 * no developer-settings animation scale needed.
 */
enum class DemoLayer { COLOR, CONTAINER, VISIBILITY, CANCEL, FRAGMENT, CROSS_APP }

object DemoConfig {
    var enabledLayers: MutableSet<DemoLayer> = EnumSet.allOf(DemoLayer::class.java)

    /** ×1 / ×2 / ×5 — slow-mo for the on-stage reveal moments. */
    var durationMultiplier: Float = 1f

    /** Scripted word for recording; null = word of the day. */
    var wordOverride: String? = null

    fun isEnabled(layer: DemoLayer): Boolean = layer in enabledLayers
}
