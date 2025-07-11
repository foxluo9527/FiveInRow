package com.foxluo.fiveinrow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.foxluo.fiveinrow.BoardSerializer.deserializeBoard
import com.foxluo.fiveinrow.BoardSerializer.serializeBoard
import kotlin.math.roundToInt

/**
 * 落子确认回调接口
 */
interface GameCallback {
    /**
     * 请求确认落子
     * @param row 落子行
     * @param col 落子列
     * @param player 落子玩家（1：黑方，-1：白方）
     */
    fun onNeedConfirmMove(row: Int, col: Int, player: Int)

    fun gameOver(blackWin: Boolean)
}

/**
 * 该类用于绘制棋盘与获取游戏数据
 */
class FiveInRowGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var gridLineColor: Int = Color.BLACK
    private var gridLineCount: Int = 13
    private var gridLineWidth = 3f
    var padding: Int = 16.dpToPx()
        set(value) {
            field = value.dpToPx()
            initGridLine()
        }
    private val gridLinePaint by lazy {
        Paint()
    }
    private var lineHeight: Float = 0f
    private var lineWidth: Float = 0f

    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private var _gameData: Array<IntArray> =
        Array<IntArray>(gridLineCount) { IntArray(gridLineCount) }
    private var currentPlayer = 1  // 1: 黑方, -1: 白方
    private var isGameOver = false
    private val blackPiecePaint by lazy {
        Paint().apply {
            color = Color.BLACK; isAntiAlias = true
        }
    }
    private val whitePiecePaint by lazy {
        Paint().apply {
            color = Color.WHITE; isAntiAlias = true; setShadowLayer(2f, 0f, 2f, Color.GRAY)
        }
    }
    private val pieceRadius get() = (lineWidth.coerceAtMost(lineHeight) * 0.4).toFloat()
    private var gameCallback: GameCallback? = null
    private var pendingRow = -1
    private var pendingCol = -1
    private var isWaitingForConfirm = false
    private var needConfirm = true

    fun initGridLine() {
        val availableWidth = measuredWidth - 2 * padding
        val availableHeight = measuredHeight - 2 * padding
        lineHeight = availableHeight.toFloat() / (gridLineCount - 1)
        lineWidth = availableWidth.toFloat() / (gridLineCount - 1)
        gridLinePaint.color = gridLineColor
        gridLinePaint.strokeWidth = gridLineWidth
        gridLinePaint.style = Paint.Style.STROKE
        gridLinePaint.isAntiAlias = true
        invalidate()
    }

    /**
     * 设置序列化游戏数据
     */
    fun initGameData(data: ByteArray) {
        _gameData = deserializeBoard(data)
        invalidate()
    }

    /**
     * 获取游戏数据
     */
    fun getGameData() = serializeBoard(_gameData)

    /**
     * 获取棋盘中纵横位的坐标位置
     * @param row 横向格数
     * @param cls 纵向格数
     */
    private fun getPositionByRowCls(row: Int, cls: Int): PointF {
        val y = padding.toFloat() + row * lineHeight + gridLineWidth / 2
        val x = padding.toFloat() + cls * lineWidth + gridLineWidth / 2
        return PointF(x, y)
    }

    /**
     * 根据点击坐标获取最近的棋盘交叉点
     */
    private fun getRowClsByPosition(x: Float, y: Float): Point? {
        if (x < padding / 2 || x > width - padding / 2 || y < padding / 2 || y > height - padding / 2) {
            return null  // 点击区域超出棋盘范围
        }

        val adjustedX = x - padding
        val adjustedY = y - padding
        val col = (adjustedX / lineWidth).roundToInt().coerceAtMost(gridLineCount - 1)
        val row = (adjustedY / lineHeight).roundToInt().coerceAtMost(gridLineCount - 1)

        // 检查是否在有效范围内
        if (row in 0 until gridLineCount && col in 0 until gridLineCount) {
            return Point(row, col)
        }
        return null
    }

    /**
     * 重置游戏状态
     */
    fun resetGame() {
        _gameData = Array(gridLineCount) { IntArray(gridLineCount) }
        currentPlayer = 1
        isGameOver = false
        isWaitingForConfirm = false
        pendingRow = -1
        pendingCol = -1
        invalidate()
    }

    /**
     * 设置落子确认监听器
     */
    fun setGameListener(listener: GameCallback) {
        this.gameCallback = listener
    }

    fun setNeedConfirm(needConfirm: Boolean) {
        this.needConfirm = needConfirm
    }

    /**
     * 确认落子
     */
    fun confirmMove() {
        if (isWaitingForConfirm && pendingRow != -1 && pendingCol != -1) {
            _gameData[pendingRow][pendingCol] = currentPlayer
            isWaitingForConfirm = false
            pendingRow = -1
            pendingCol = -1
            invalidate()

            // 检查游戏是否结束
            val result = checkGameOver()
            if (result != 0) {
                isGameOver = true
            } else {
                // 切换玩家
                currentPlayer *= -1
            }
        }
    }

    /**
     * 取消落子
     */
    fun cancelMove() {
        if (isWaitingForConfirm) {
            isWaitingForConfirm = false
            pendingRow = -1
            pendingCol = -1
            invalidate()
        }
    }

    /**
     * 检查游戏是否结束，判断胜利者
     * @return 1表示黑方胜利，-1表示白方胜利，0表示游戏继续
     */
    fun checkGameOver(): Int {
        val directions = arrayOf(
            intArrayOf(1, 0),   // 水平方向
            intArrayOf(0, 1),   // 垂直方向
            intArrayOf(1, 1),   // 右下对角线
            intArrayOf(1, -1)   // 右上对角线
        )

        for (i in _gameData.indices) {
            for (j in _gameData[i].indices) {
                val currentPlayer = _gameData[i][j]
                if (currentPlayer == 0) continue  // 跳过空位置

                for ((dx, dy) in directions) {
                    var consecutiveCount = 1

                    // 向当前方向检查后续4个棋子
                    for (step in 1 until 5) {
                        val ni = i + dx * step
                        val nj = j + dy * step

                        // 检查是否在棋盘范围内且棋子相同
                        if (ni in 0 until gridLineCount && nj in 0 until gridLineCount &&
                            _gameData[ni][nj] == currentPlayer
                        ) {
                            consecutiveCount++
                        } else {
                            break
                        }
                    }

                    if (consecutiveCount >= 5) {
                        return currentPlayer
                    }
                }
            }
        }
        return 0  // 未分胜负
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initGridLine()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 绘制网格线
        for (i in 0 until gridLineCount) {
            val y = padding.toFloat() + (i * lineHeight).toFloat()
            val x = padding.toFloat() + (i * lineWidth).toFloat()
            canvas.drawLine(
                padding.toFloat(), y,
                (width - padding).toFloat(), y,
                gridLinePaint
            )
            canvas.drawLine(
                x, padding.toFloat(),
                x, (height - padding).toFloat(),
                gridLinePaint
            )
        }

        // 绘制棋子
        for (i in _gameData.indices) {
            for (j in _gameData[i].indices) {
                val player = _gameData[i][j]
                if (player != 0) {
                    drawPiece(canvas, i, j, player, 255)
                }
            }
        }

        // 绘制等待确认的半透明棋子
        if (isWaitingForConfirm && pendingRow != -1 && pendingCol != -1) {
            drawPiece(canvas, pendingRow, pendingCol, currentPlayer, 128)
        }
    }

    /**
     * 绘制棋子
     * @param canvas 画布
     * @param row 行
     * @param col 列
     * @param player 玩家（1：黑方，-1：白方）
     * @param alpha 透明度（0-255）
     */
    private fun drawPiece(canvas: Canvas, row: Int, col: Int, player: Int, alpha: Int) {
        val pos = getPositionByRowCls(row, col)
        val originalAlpha = if (player == 1) blackPiecePaint.alpha else whitePiecePaint.alpha

        if (player == -1) {
            // 白棋需要黑色边框
            blackPiecePaint.alpha = alpha
            whitePiecePaint.alpha = alpha
            canvas.drawCircle(pos.x, pos.y, pieceRadius, blackPiecePaint)
            canvas.drawCircle(pos.x, pos.y, pieceRadius * 0.9f, whitePiecePaint)
        } else {
            blackPiecePaint.alpha = alpha
            canvas.drawCircle(pos.x, pos.y, pieceRadius, blackPiecePaint)
        }

        // 恢复透明度
        blackPiecePaint.alpha = originalAlpha
        whitePiecePaint.alpha = originalAlpha
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN || isGameOver) return true

        val point = getRowClsByPosition(event.x, event.y)
        point?.let {
            val row = it.x
            val col = it.y
            if (_gameData[row][col] == 0) {
                // 如果正在等待确认，先取消当前确认
                if (isWaitingForConfirm) {
                    cancelMove()
                }
                // 判断是否需要落子确认
                if (needConfirm && gameCallback != null) {
                    // 进入等待确认状态
                    pendingRow = row
                    pendingCol = col
                    isWaitingForConfirm = true
                    invalidate()
                    // 回调通知需要确认
                    gameCallback?.onNeedConfirmMove(row, col, currentPlayer)
                } else {
                    // 无回调直接落子
                    _gameData[row][col] = currentPlayer
                    invalidate()

                    // 检查游戏是否结束
                    val result = checkGameOver()
                    if (result != 0) {
                        isGameOver = true
                        gameCallback?.gameOver(result == 1)
                    } else {
                        // 切换玩家
                        currentPlayer *= -1
                    }
                }
            }
        }
        return true
    }
}