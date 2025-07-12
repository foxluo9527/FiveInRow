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
import com.blankj.utilcode.util.SPUtils
import com.blankj.utilcode.util.SizeUtils
import com.blankj.utilcode.util.ToastUtils
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * 游戏进程回调接口
 */
interface GameCallback {
    /**
     * 请求确认落子
     * @param row 落子行
     * @param col 落子列
     * @param player 落子玩家（1：黑方，-1：白方）
     */
    fun onNeedConfirmMove(row: Int, col: Int, player: Int)

    /**
     * 请求电脑下一步棋
     */
    fun onComputerConfirm()

    fun onStep(player: Int)

    fun gameOver(blackWin: Boolean)

    fun gameStart()

    fun gameReady()
}

/**
 * 该类用于绘制棋盘与获取游戏数据
 */
class FiveInRowGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    var gridLineColor: Int = SPUtils.getInstance().getInt("setting_grid_color", Color.BLACK)
        set(value) {
            field = value
            SPUtils.getInstance().put("setting_grid_color", value)
            initGridLine()
        }

    var gridLineWidth = SPUtils.getInstance().getFloat("setting_grid_width", 3f)
        set(value) {
            field = value
            SPUtils.getInstance().put("setting_grid_width", value)
            initGridLine()
        }

    var padding: Int = SPUtils.getInstance().getInt("setting_grid_padding", SizeUtils.dp2px(16f))
        set(value) {
            field = value
            SPUtils.getInstance().put("setting_grid_padding", value)
            initGridLine()
        }

    private val gridLineCount: Int = 13

    private val gridLinePaint by lazy {
        Paint()
    }

    var playWithComputer: Boolean = false

    @Volatile
    private var waitForComputer = false

    private var lineHeight: Float = 0f
    private var lineWidth: Float = 0f
    var gameStartTime = 0L

    private var _gameData: Array<IntArray> =
        Array<IntArray>(gridLineCount) { IntArray(gridLineCount) }

    private var currentPlayer = 1  // 1: 黑方, -1: 白方
    private var _isGameOver = true
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
    fun initGameCache(cache: BoardCache) {
        gameStartTime = cache.id
        val data: ByteArray = BoardCache.dataToByte(cache.data)
        val gameDataSize = 43  // 棋盘数据固定为43字节

        // 检查数据是否有效
        if (data.size < gameDataSize) {
            // 数据无效，重置游戏
            resetGame()
            return
        }

        val gameDataBytes = data.copyOfRange(0, gameDataSize)
        val playerBytes = data.copyOfRange(gameDataSize, data.size)
        _gameData = BoardSerializer.deserializeBoard(gameDataBytes)
        playWithComputer = cache.aiPlayer
        isWaitingForConfirm = false
        // 检查玩家数据是否有效
        currentPlayer = if (playerBytes.size >= Int.SIZE_BYTES) {
            ByteBuffer.wrap(playerBytes).int
        } else {
            1  // 默认黑方先手
        }
        _isGameOver = false
        invalidate()
        gameCallback?.gameReady()
        gameCallback?.gameStart()
        gameCallback?.onStep(currentPlayer)
    }

    /**
     * 获取游戏存档数据
     */
    fun getGameDataCache(): ByteArray {
        val gameDataBytes = BoardSerializer.serializeBoard(_gameData)
        val playerBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(currentPlayer).array()
        return gameDataBytes + playerBytes
    }

    /**
     * 获取游戏进度数据
     */
    fun getGameData() = _gameData

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
    fun resetGame(playWithComputer: Boolean = this.playWithComputer) {
        this.playWithComputer = playWithComputer
        _gameData = Array(gridLineCount) { IntArray(gridLineCount) }
        currentPlayer = 1
        _isGameOver = false
        isWaitingForConfirm = false
        pendingRow = -1
        pendingCol = -1
        gameStartTime = 0L
        invalidate()
        gameCallback?.gameReady()
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
     * 执行电脑下棋
     */
    fun computerConfirm(row: Int, col: Int) {
        if (_isGameOver || !playWithComputer) return

        // 检查位置是否有效
        if (row in 0 until gridLineCount && col in 0 until gridLineCount && _gameData[row][col] == 0) {
            _gameData[row][col] = currentPlayer
            judgeGameStart()
            invalidate()
            // 检查游戏是否结束
            checkGameOver()
            waitForComputer = false
        }
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
            judgeGameStart()
            invalidate()
            // 检查游戏是否结束
            checkGameOver()
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
    fun checkGameOver() {
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
                        _isGameOver = true
                        gameCallback?.gameOver(currentPlayer == 1)
                    }
                }
            }
        }
        // 切换玩家
        currentPlayer *= -1  // 未分胜负

        // 如果是电脑对战且当前是电脑回合，请求电脑下棋
        if (playWithComputer && currentPlayer == -1 && !_isGameOver) {
            waitForComputer = true
            gameCallback?.onComputerConfirm()
        } else {
            waitForComputer = false
        }
        if (!playWithComputer && !_isGameOver) {
            gameCallback?.onStep(currentPlayer)
        }
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
        if (event.action != MotionEvent.ACTION_DOWN || _isGameOver || waitForComputer) return true

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
                    judgeGameStart()
                    invalidate()
                    // 检查游戏是否结束
                    checkGameOver()
                }
            }
        }
        return true
    }

    /**
     * 判断是否只有一颗棋子
     */
    private fun judgeGameStart() {
        var pieceCountLeast = 0
        for (pieces in _gameData) {
            pieceCountLeast += pieces.count { it != 0 }
            if (pieceCountLeast == 1 && gameStartTime == 0L) {
                gameCallback?.gameStart()
                gameStartTime = System.currentTimeMillis()
                return
            }
        }
    }
}