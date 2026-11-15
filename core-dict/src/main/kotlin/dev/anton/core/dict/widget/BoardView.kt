package dev.anton.core.dict.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.LinearLayout
import dev.anton.core.dict.R

/**
 * Игровое поле целиком: вертикальная стойка из [BoardRowView]. Кладётся в
 * layout XML как есть —
 * `<dev.anton.core.dict.widget.BoardView app:boardRows="6" app:boardColumns="5" …/>` —
 * и строит ряды сама. Это и есть shared element Примеров 2/3 (transitionName
 * "board" назначается на контейнер в сцене).
 */
class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    val rows = mutableListOf<BoardRowView>()

    init {
        orientation = VERTICAL
        var rowCount = DEFAULT_ROWS
        var columns = DEFAULT_COLUMNS
        var cellSizePx = 0
        var cellMarginPx = 0
        var cellTextSizePx = 0
        var cellColor = Color.rgb(0xF2, 0xF2, 0xF2)
        context.obtainStyledAttributes(attrs, R.styleable.BoardView).apply {
            rowCount = getInt(R.styleable.BoardView_boardRows, rowCount)
            columns = getInt(R.styleable.BoardView_boardColumns, columns)
            cellSizePx = getDimensionPixelSize(
                R.styleable.BoardView_boardCellSize,
                dp(DEFAULT_CELL_SIZE_DP),
            )
            cellMarginPx = getDimensionPixelSize(
                R.styleable.BoardView_boardCellMargin,
                dp(DEFAULT_CELL_MARGIN_DP),
            )
            cellTextSizePx = getDimensionPixelSize(
                R.styleable.BoardView_boardCellTextSize,
                sp(DEFAULT_CELL_TEXT_SIZE_SP),
            )
            cellColor = getColor(R.styleable.BoardView_boardCellColor, cellColor)
            recycle()
        }
        repeat(rowCount) {
            val row = BoardRowView(context).apply {
                configure(columns, cellSizePx, cellMarginPx, cellTextSizePx, cellColor)
            }
            rows += row
            addView(row)
        }
    }

    fun setLetter(row: Int, col: Int, letter: Char?) = rows[row].setLetter(col, letter)

    fun setColor(row: Int, col: Int, color: Int) = rows[row].setColor(col, color)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun sp(value: Int) = (value * resources.displayMetrics.scaledDensity).toInt()

    private companion object {
        const val DEFAULT_ROWS = 6
        const val DEFAULT_COLUMNS = 5
        const val DEFAULT_CELL_SIZE_DP = 52
        const val DEFAULT_CELL_MARGIN_DP = 3
        const val DEFAULT_CELL_TEXT_SIZE_SP = 22
    }
}
