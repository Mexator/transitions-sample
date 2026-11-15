package dev.anton.core.dict.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import dev.anton.core.dict.R

/**
 * Один ряд игрового поля: N ячеек-TextView, растянутых по ширине весами.
 * Живёт в core-dict, чтобы «Словарь» показывал слово теми же ячейками, что и
 * игра. Вьюшка не знает про игру: только буквы и цвета.
 *
 * Дерево то же, что строилось кодом в Activity (LinearLayout → TextView с
 * весом), поэтому transition'ы Примеров 2/3 продолжают работать без изменений:
 * Explode таргетит ряды-инстансы, ChangeBounds/CenteredChangeBounds — контейнер.
 */
class BoardRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var columns = DEFAULT_COLUMNS
        private set
    val cells = mutableListOf<TextView>()

    private var cellColor = DEFAULT_COLOR
    private var cellSizePx = dp(DEFAULT_CELL_SIZE_DP)
    private var cellMarginPx = dp(DEFAULT_CELL_MARGIN_DP)
    private var cellTextSizePx = sp(DEFAULT_CELL_TEXT_SIZE_SP)

    init {
        orientation = HORIZONTAL
        if (attrs != null) {
            context.obtainStyledAttributes(attrs, R.styleable.BoardRowView).apply {
                columns = getInt(R.styleable.BoardRowView_boardColumns, columns)
                cellSizePx = getDimensionPixelSize(R.styleable.BoardRowView_boardCellSize, cellSizePx)
                cellMarginPx = getDimensionPixelSize(R.styleable.BoardRowView_boardCellMargin, cellMarginPx)
                cellTextSizePx = getDimensionPixelSize(R.styleable.BoardRowView_boardCellTextSize, cellTextSizePx)
                cellColor = getColor(R.styleable.BoardRowView_boardCellColor, cellColor)
                recycle()
            }
            buildCells()
        }
    }

    /** Программная конфигурация — [BoardView] создаёт ряды сам этими параметрами. */
    fun configure(
        columns: Int,
        cellSizePx: Int,
        cellMarginPx: Int,
        cellTextSizePx: Int,
        cellColor: Int = this.cellColor,
    ) {
        this.columns = columns
        this.cellSizePx = cellSizePx
        this.cellMarginPx = cellMarginPx
        this.cellTextSizePx = cellTextSizePx
        this.cellColor = cellColor
        buildCells()
    }

    fun setLetter(col: Int, letter: Char?) {
        cells[col].text = letter?.toString().orEmpty()
    }

    fun setColor(col: Int, color: Int) {
        cells[col].setBackgroundColor(color)
    }

    /** Показать слово; лишние ячейки очищаются. */
    fun setWord(word: String) {
        for (col in 0 until columns) setLetter(col, word.getOrNull(col))
    }

    private fun buildCells() {
        cells.clear()
        removeAllViews()
        repeat(columns) {
            TextView(context).apply {
                layoutParams = LayoutParams(0, cellSizePx, 1f).apply {
                    setMargins(cellMarginPx, cellMarginPx, cellMarginPx, cellMarginPx)
                }
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, cellTextSizePx.toFloat())
                setBackgroundColor(cellColor)
                cells += this
                addView(this)
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun sp(value: Int) = (value * resources.displayMetrics.scaledDensity).toInt()

    private companion object {
        const val DEFAULT_COLUMNS = 5
        const val DEFAULT_CELL_SIZE_DP = 52
        const val DEFAULT_CELL_MARGIN_DP = 3
        const val DEFAULT_CELL_TEXT_SIZE_SP = 22
        val DEFAULT_COLOR = Color.rgb(0xF2, 0xF2, 0xF2)
    }
}
