# wordle-transitions

Демо-приложение к докладу про **Transitions API** (Nizhny Novgorod mobile meetup, 26 ноября 2026).

«Слово» — клон Wordle на русском: 6×5 поле, экранная клавиатура, финал со словом. Одно приложение, один экран, ноль Compose — **только View/XML**, как и весь доклад. Каждая анимация доклада — отдельный слой, который включается и выключается тумблером в отладочной панели.

## Примеры → код

| Пример | Что показывает | Код |
|---|---|---|
| 0. Без анимаций | «До»: как выглядит приложение без переходов. Все слои панели выключены | [MainActivity.kt](app/src/main/kotlin/ru/mexator/slovo/MainActivity.kt) — `render()` |
| 1. Смена цвета | Кастомный `Transition` + `beginDelayedTransition`: отправленное слово перекрашивает клетки и клавиши | [ChangeColorTransition.kt](app/src/main/kotlin/ru/mexator/slovo/transition/ChangeColorTransition.kt), [DemoTransitions.kt](app/src/main/kotlin/ru/mexator/slovo/transition/DemoTransitions.kt) — `gameplay()` |
| 2. Container transform | `Scene` + `TransitionManager#go()`: мини-поле стартового экрана вырастает в доску **от центра**, клавиатура стоит на месте — оба shared element | [CenteredChangeBounds.kt](app/src/main/kotlin/ru/mexator/slovo/transition/CenteredChangeBounds.kt), [MainActivity.kt](app/src/main/kotlin/ru/mexator/slovo/MainActivity.kt) — `showGameScene()`, [scene_start.xml](app/src/main/res/layout/scene_start.xml), [scene_game.xml](app/src/main/res/layout/scene_game.xml) |
| 3. Появление/исчезновение | `Visibility` + `ViewOverlay`: клавиатура уезжает вниз (`Slide`), финал проявляется (`Fade`), на «Ещё раз» доска разлетается и собирается (`Explode`, два прохода) | [DemoTransitions.kt](app/src/main/kotlin/ru/mexator/slovo/transition/DemoTransitions.kt), [MainActivity.kt](app/src/main/kotlin/ru/mexator/slovo/MainActivity.kt) — `explodeRestart()` |
| 4. Отмена анимаций | Быстрые переключения сворачиваемой подсказки — отмену предыдущего перехода берёт на себя фреймворк: код лишь вооружает новый проход, реверс идёт с текущего кадра | [DemoTransitions.kt](app/src/main/kotlin/ru/mexator/slovo/transition/DemoTransitions.kt) — `hintToggle()`, [MainActivity.kt](app/src/main/kotlin/ru/mexator/slovo/MainActivity.kt) — `toggleHint()`, [scene_game.xml](app/src/main/res/layout/scene_game.xml) — карточка `hint_card` |
| 5. Переход между фрагментами | Chip со словом → заголовок `ArticleFragment` (shared element) | [ArticleFragment.kt](app/src/main/kotlin/ru/mexator/slovo/ArticleFragment.kt) *(в работе)* |
| 6. Переход между приложениями | `ActivityOptions.makeSceneTransitionAnimation` через процессную границу в отдельный APK «Словарь» | [Dictionary.kt](core-dict/src/main/kotlin/ru/mexator/core/dict/Dictionary.kt) — `SlovarContract`, [ArticleActivity.kt](app-slovar/src/main/kotlin/ru/mexator/slovar/ArticleActivity.kt) *(в работе)* |

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
- **`:core-ui`** — общие вьюшки `BoardView`/`BoardRowView` и `KeyboardView` (пакет `ru.mexator.core.ui.widget`) и палитра в `colors.xml` — «Словарь» показывает слово тем же рядом «клеток», из которого собрано поле игры
- **`:core-dict`** — общее: слова, статьи, `SlovarContract` (интент + общие `transitionName`)

Банк слов — 2394 существительных по 5 букв из [wordle_ru](https://github.com/KarKarbI4/wordle_ru) (там он собран из частотного словаря [dict.ruslang.ru](http://dict.ruslang.ru/freq.php)); статьи для демонстрационных слов написаны вручную.
