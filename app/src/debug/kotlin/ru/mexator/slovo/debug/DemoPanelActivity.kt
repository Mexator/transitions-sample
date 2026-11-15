package ru.mexator.slovo.debug

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import ru.mexator.core.dict.Dictionary
import ru.mexator.slovo.MainActivity
import ru.mexator.slovo.R
import ru.mexator.slovo.demo.DemoConfig
import ru.mexator.slovo.demo.DemoLayer

/**
 * Recording cockpit (debug builds only, own launcher icon):
 * layer toggles, slow-mo, scripted word, state jumps into the game.
 */
class DemoPanelActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo_panel)

        layerToggle(R.id.layer_color, DemoLayer.COLOR)
        layerToggle(R.id.layer_container, DemoLayer.CONTAINER)
        layerToggle(R.id.layer_visibility, DemoLayer.VISIBILITY)
        layerToggle(R.id.layer_cancel, DemoLayer.CANCEL)
        layerToggle(R.id.layer_fragment, DemoLayer.FRAGMENT)
        layerToggle(R.id.layer_cross_app, DemoLayer.CROSS_APP)

        findViewById<RadioGroup>(R.id.speed_group).apply {
            check(
                when (DemoConfig.durationMultiplier) {
                    2f -> R.id.speed_x2
                    5f -> R.id.speed_x5
                    else -> R.id.speed_x1
                },
            )
            setOnCheckedChangeListener { _, id ->
                DemoConfig.durationMultiplier = when (id) {
                    R.id.speed_x2 -> 2f
                    R.id.speed_x5 -> 5f
                    else -> 1f
                }
            }
        }

        val spinner = findViewById<Spinner>(R.id.word_spinner)
        val words = listOf(getString(R.string.word_of_the_day)) + Dictionary.articles.map { it.word }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, words)
        spinner.setSelection(0)

        findViewById<Button>(R.id.btn_open_game).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<Button>(R.id.btn_start_game).setOnClickListener {
            DemoConfig.wordOverride = spinner.selectedItemPosition
                .takeIf { it > 0 }?.let { words[it] }
            sendDemoCommand(MainActivity.COMMAND_START)
        }
        findViewById<Button>(R.id.btn_surrender).setOnClickListener {
            sendDemoCommand(MainActivity.COMMAND_SURRENDER)
        }
    }

    private fun sendDemoCommand(command: String) {
        val game = packageManager.getLaunchIntentForPackage("ru.mexator.slovo")
            ?: Intent(this, MainActivity::class.java)
        game.putExtra(MainActivity.EXTRA_DEMO_COMMAND, command)
        startActivity(game)
    }

    private fun layerToggle(id: Int, layer: DemoLayer) {
        findViewById<CheckBox>(id).apply {
            isChecked = DemoConfig.isEnabled(layer)
            setOnCheckedChangeListener { _, checked ->
                if (checked) DemoConfig.enabledLayers += layer else DemoConfig.enabledLayers -= layer
            }
        }
    }
}
