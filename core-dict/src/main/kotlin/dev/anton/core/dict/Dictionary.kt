package dev.anton.core.dict

/**
 * Shared data for both apps: the game (:app) validates guesses and shows hints,
 * «Словарь» (:app-slovar) shows the article.
 */
data class Article(
    val word: String,
    val hint: String,
    val body: String,
)

/**
 * Contract between «Слово» and «Словарь». Both APKs are built from this repo,
 * but they are separate apps: the shared element crosses a process boundary.
 */
object SlovarContract {
    const val PACKAGE = "dev.anton.slovar"
    const val ACTIVITY = "dev.anton.slovar.ArticleActivity"
    const val EXTRA_WORD = "word"

    /** transitionName shared by the word chip (game) and the article header («Словарь»). */
    const val TRANSITION_WORD = "word"
}

object Dictionary {

    val articles: List<Article> = listOf(
        Article("СЛОВО", "То, что здесь нужно угадать.", "Слово — базовая единица языка: сочетание звуков, у которого есть значение. В этой игре их ровно пять букв."),
        Article("БУКВА", "Их в слове ровно пять.", "Буква — отдельный символ алфавита. Из букв собираются слоги, из слогов — слова, из слов — вот этот доклад."),
        Article("ДОСКА", "На ней пишут мелом.", "Доска — плоский лист дерева или пластика. На классной доске пишут мелом, на доске для событий — заметки со стикерами."),
        Article("КОШКА", "Гуляет сама по себе.", "Кошка — домашнее хищное животное семейства кошачьих. Считается, что одомашнила себя самостоятельно."),
        Article("МЕСТО", "Есть у каждого в очереди.", "Место — пространство, которое кто-то или что-то занимает. У каждой буквы в слове — своё место, и на нём бывает всё сложно."),
        Article("ВЕТЕР", "Дует, куда хочет.", "Ветер — горизонтальное движение воздуха. На силу ветра жалуются и моряки, и разработчики, у которых «поплыл» макет."),
        Article("ПЕСНЯ", "Слова плюс музыка.", "Песня — музыкальное произведение с текстом. Слово из песни, как известно, не выкинешь — в отличие от неправильной буквы."),
        Article("РУЧКА", "И ею пишут, и за неё держатся.", "Ручка — инструмент для письма чернилами, а также выступ, за который держат предмет. Многозначность — страшная сила."),
        Article("КНИГА", "Состоит из слов, как и эта игра.", "Книга — печатное издание объёмом больше 48 страниц. Ровно столько же страниц занимает changelog AndroidX Transition."),
        Article("ЛАМПА", "Светит в темноте.", "Лампа — источник искусственного света. Лампой также называют электронный прибор, что делает слово ещё на одну профессию многозначнее."),
        Article("ОКЕАН", "Вода от горизонта до горизонта.", "Океан — весь мировой солёный океан как целое, минуя деления на части. Содержит примерно 97% всей воды Земли."),
        Article("САХАР", "Сладкий и белый.", "Сахар — бытовое название сахарозы. В коде сахаром обычно называют синтаксический, и он тоже бывает вреден в больших количествах."),
        Article("ТЕАТР", "Место, где играют.", "Театр — вид искусства, где события разыгрываются живыми актёрами. По легенде, начинается с вешалки, а заканчивается аплодисментами."),
        Article("МЫШКА", "Компьютерная и живущая в норе.", "Мышь — маленький грызун; он же — устройство ввода. Компьютерная мышка живёт заметно дольше, если её не ронять."),
        Article("ПОЕЗД", "Едет по расписанию. Иногда.", "Поезд — сформированный состав вагонов с локомотивом. Идеальная метафора отложенного перехода: ждёшь отправления, пока всё готово."),
    )

    /** Extra valid guesses so the player is not limited to the 15 answer words. */
    private val extraWords = setOf(
        "ЗЕМЛЯ", "ГОРОД", "МЫСЛЬ", "СТЕНА", "ТРАВА",
        "ХОЛОД", "УЛИЦА", "ВОЛНА", "ГРОЗА", "СПИНА",
    )

    private val knownWords = articles.map { it.word }.toSet() + extraWords

    fun isValid(word: String): Boolean = word.uppercase() in knownWords

    fun articleOf(word: String): Article? = articles.firstOrNull { it.word == word.uppercase() }

    /** Deterministic pick, changes once per day. No java.time — minSdk 24 without desugaring. */
    fun wordOfTheDay(nowMillis: Long = System.currentTimeMillis()): String {
        val day = nowMillis / 86_400_000L
        val idx = ((day % articles.size) + articles.size).toInt() % articles.size
        return articles[idx].word
    }
}
