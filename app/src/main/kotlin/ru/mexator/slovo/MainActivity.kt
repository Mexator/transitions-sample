package ru.mexator.slovo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import ru.mexator.core.dict.SlovarContract
import ru.mexator.slovo.databinding.ActivityMainBinding

/**
 * Тонкая оболочка (Пример 5): единственный fragment_container, в котором всегда
 * живёт [GameFragment] — сцены, финал слоя OFF и все проходы Примеров 0–4
 * переехали в него целиком. Активити остаётся маршрутизатором демо-команд
 * панели и holder'ом операций, требующих Activity (запуск «Словаря»).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DEMO_COMMAND = "demo.command"
        const val COMMAND_START = "start"
        const val COMMAND_SURRENDER = "surrender"

        /** Теги фрагментов в fragment_container. */
        const val GAME_TAG = "game"
        const val WIN_TAG = "win"
        const val ARTICLE_TAG = "article"
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // GameFragment — корневой и вечный житель контейнера: после пересоздания
        // активити менеджер восстанавливает его сам, заново не добавляем.
        if (supportFragmentManager.findFragmentByTag(GAME_TAG) == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, GameFragment(), GAME_TAG)
                .commitNow()
        }

        handleDemoCommand(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDemoCommand(intent)
    }

    /** Sent by the debug demo panel: state jumps for recording. */
    private fun handleDemoCommand(intent: Intent?) {
        val command = intent?.getStringExtra(EXTRA_DEMO_COMMAND) ?: return
        // Команда может прилететь, когда сверху сидит экран победы или статья
        // (сценарий записи M4: игра живёт под панелью) — снимаем их. Возвратных
        // переходов у WinFragment/ArticleFragment нет, съём мгновенный и в один
        // кадр с проходами state-jump'а не складывается.
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE,
            )
        }
        gameFragment().onDemoCommand(command)
    }

    /**
     * «Ещё раз» с экрана победы: сначала WinFragment снимается (мгновенно —
     * возвратных переходов нет), и только потом игра взрывает поле — проходы
     * Примера 3 вооружаются на чистом кадре.
     */
    fun onPlayAgain() {
        supportFragmentManager.popBackStackImmediate()
        gameFragment().startGame()
    }

    /** Пример 6 (M3): shared element across the process boundary into «Словарь». */
    fun openSlovar(word: String) {
        val intent = Intent()
            .setClassName(SlovarContract.PACKAGE, SlovarContract.ACTIVITY)
            .putExtra(SlovarContract.EXTRA_WORD, word)
        try {
            // M3 (Пример 6): ActivityOptions.makeSceneTransitionAnimation с шапкой
            // WinFragment как shared element — рядом ряд «клеток» улетит в «Словарь».
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.install_slovar, Toast.LENGTH_SHORT).show()
        }
    }

    private fun gameFragment(): GameFragment =
        supportFragmentManager.findFragmentByTag(GAME_TAG) as GameFragment
}
