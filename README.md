# wordle-transitions

Демо-приложение к докладу про **Transitions API** (Nizhny Novgorod mobile meetup, 26 ноября 2026).

«Слово» — клон Wordle на русском: 6×5 поле, экранная клавиатура, финал со словом. Одно приложение, один экран, ноль Compose — **только View/XML**, как и весь доклад. Каждая анимация доклада — отдельный слой, который включается и выключается тумблером в отладочной панели.

## Примеры → код

Kotlin-корни модулей: `:app` и `:app-slovar` — `src/main/kotlin/ru/mexator/…`, `:core-dict` — `src/main/kotlin/ru/mexator/core/dict/…`.

| Пример | Что показывает | Ключевой код | Тег |
|---|---|---|---|
| 0. Без анимаций | «До»: как выглядит приложение без переходов. Все слои панели выключены | `MainActivity.render()` — чистая мутация вью без анимаций | [`ex0`](../../tree/ex0) |
| 1. Смена цвета | Кастомный `Transition` + `beginDelayedTransition`: отправленное слово перекрашивает клетки и клавиши | `:app` → `transition/ChangeColorTransition.kt`, `transition/DemoTransitions.gameplay()` | [`ex1`](../../tree/ex1) |
| 2. Container transform | `Scene` + `TransitionManager#go()`: мини-поле стартового экрана вырастает в доску **от центра**, клавиатура стоит на месте — оба shared element | `:app` → `transition/CenteredChangeBounds.kt`, `MainActivity.showGameScene()`, `res/layout/scene_start.xml` + `scene_game.xml` | [`ex2`](../../tree/ex2) |
| 3. Появление/исчезновение | `Visibility` + `ViewOverlay`: клавиатура уезжает вниз (`Slide`), финал проявляется (`Fade`), на «Ещё раз» доска разлетается и собирается (`Explode`, два прохода) | `:app` → `transition/DemoTransitions.kt`, `MainActivity.explodeRestart()` | [`ex3`](../../tree/ex3) |
| 4. Отмена анимаций | Быстрые переключения сворачиваемой подсказки — отмену предыдущего перехода берёт на себя фреймворк | — | запланирован |
| 5. Переход между фрагментами | Chip со словом → заголовок `ArticleFragment` (shared element) | — | запланирован |
| 6. Переход между приложениями | `ActivityOptions.makeSceneTransitionAnimation` через процессную границу в отдельный APK «Словарь» | `:core-dict` → `SlovarContract.kt`, `:app-slovar` | запланирован |

Дифф с примером N на GitHub — `…/compare/exN−1...exN` (например, `compare/ex2...ex3` — весь Пример 3). Именно эти ссылки — на слайдах.

## Демо-панель

В debug-сборке у игры есть вторая иконка — «Демо-панель (Слово)»: тумблер на каждый слой примера, замедление ×1/×2/×5 (для съёмки промежуточных кадров) и прыжки состояния (`start`, `surrender` через `demo.command`-интенты). Все слои выключены = Пример 0, без настроек разработчика.

## Сборка и запуск

```bash
./gradlew :app:assembleDebug :app-slovar:assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app-slovar/build/outputs/apk/debug/app-slovar-debug.apk
```

## Модули

- **`:app`** — игра «Слово» (`ru.mexator.slovo`). Экраны — Scene-лейауты (`scene_start` / `scene_game`), сменяются `TransitionManager#go()` внутри одной Activity. Все переходы собраны в `transition/DemoTransitions.kt` (+ `CenteredChangeBounds` и `ChangeColorTransition` рядом)
- **`:app-slovar`** — «Словарь» (`ru.mexator.slovar`), отдельный APK: приёмник cross-app shared element из Примера 6
- **`:core-dict`** — общее: слова, статьи, `SlovarContract` (интент + общие `transitionName`) и общие вьюшки `BoardView`/`BoardRowView` — «Словарь» показывает слово тем же рядом «клеток», из которого собрано поле игры

Банк слов — 2394 существительных по 5 букв из [wordle_ru](https://github.com/KarKarbI4/wordle_ru) (там он собран из частотного словаря [dict.ruslang.ru](http://dict.ruslang.ru/freq.php)); статьи для демонстрационных слов написаны вручную.
