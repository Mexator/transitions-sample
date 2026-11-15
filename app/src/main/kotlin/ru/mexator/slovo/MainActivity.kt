package ru.mexator.slovo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.transition.ChangeBounds
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.FragmentManager
import ru.mexator.core.dict.SlovarContract
import ru.mexator.slovo.databinding.ActivityMainBinding
import ru.mexator.slovo.demo.DemoConfig
import ru.mexator.slovo.demo.DemoLayer

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
        // Пример 6: window-переходы для cross-app shared element. Фича нужна
        // ОБОИМ окнам — иначе options-бандл молча не оживёт (как и голый add
        // у фрагментов, это не ошибка — просто перехода нет).
        if (!window.hasFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)) {
            window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        }
        // Обратный перелёт («Закрыть»/Back в «Словаре»): шапка принимает ряд
        // тем же ChangeBounds'ом и с той же длительностью, что и прилёт.
        window.sharedElementReenterTransition = ChangeBounds().apply {
            duration = SlovarContract.FLIGHT_DURATION_MS
        }
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

    /**
     * Пример 6: shared element через границу процесса в «Словарь». Источник —
     * ЯВНАЯ пара (view, transitionName) в ActivityOptions: в отличие от
     * фрагментов, где фреймворк выводит пару из диффа транзакции, здесь мы
     * руками указываем, что летит. NEW_TASK|CLEAR_TASK — своя задача и ВСЕГДА
     * свежий запуск (живая задача поднималась бы вперёд без перехода —
     * поймано кадрами как «магия есть через раз»); две карточки в Recents.
     * Слой CROSS_APP off (или нет view-источника) — деградация: обычное
     * открытие без options-бандла.
     */
    fun openSlovar(word: String, sharedView: View?, greenRow: Boolean) {
        val intent = Intent()
            .setClassName(SlovarContract.PACKAGE, SlovarContract.ACTIVITY)
            .putExtra(SlovarContract.EXTRA_WORD, word)
            .putExtra(SlovarContract.EXTRA_GREEN, greenRow)
            // NEW_TASK|CLEAR_TASK — своя задача и ВСЕГДА свежий запуск (живая
            // задача поднималась бы вперёд без перехода — поймано кадрами как
            // «магия есть через раз»); две карточки в Recents. Флаги не влияют
            // на обратный перелёт — см. wart про return в CLAUDE.md.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            val options =
                if (sharedView != null && DemoConfig.isEnabled(DemoLayer.CROSS_APP)) {
                    ActivityOptionsCompat.makeSceneTransitionAnimation(
                        this,
                        sharedView,
                        SlovarContract.TRANSITION_WORD,
                    ).toBundle()
                } else {
                    null
                }
            startActivity(intent, options)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.install_slovar, Toast.LENGTH_SHORT).show()
        }
    }

    private fun gameFragment(): GameFragment =
        supportFragmentManager.findFragmentByTag(GAME_TAG) as GameFragment
}
