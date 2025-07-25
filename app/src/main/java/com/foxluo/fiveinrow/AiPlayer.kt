package com.foxluo.fiveinrow

import calculateDifficultMove
import calculateExpertMove
import com.blankj.utilcode.util.SPUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object AiPlayer {
    // 评估权重配置
    private const val WIN_SCORE = 1000000
    private const val FOUR_SCORE = 100000

    // 增强进攻性权重
    // 增强活三进攻性
    private const val THREE_SCORE = 35000
    private const val TWO_SCORE = 5000
    private const val BLOCK_FOUR_SCORE = 8000
    private const val BLOCK_THREE_SCORE = 50000
    private const val EXPERT_SEARCH_DEPTH = 3
    private const val CENTER_BONUS = 1000 // 降低中心位置奖励，平衡防守优先级

    // AI玩家（默认执白棋）
    private const val AI_PLAYER = 2

    // 人类玩家
    private const val HUMAN_PLAYER = 1

    /**
     * 计算AI下一步最佳落子位置
     * @param gameData 棋盘数据
     * @return 最佳落子坐标 (row, col)
     */
    suspend fun calculateNextMove(gameData: Array<IntArray>): Pair<Int, Int> {
        // 根据难度调整AI行为
        return withContext(Dispatchers.IO) {
            val difficulty = SPUtils.getInstance().getInt("easy_mode", 1)
            when (difficulty) {
                0 -> calculateMediumMove(gameData)
                1 -> calculateHardMove(gameData)
                2 -> calculateExpertMove(gameData)
                else -> calculateDifficultMove(gameData)
            }.also {
                delay((4 - difficulty) * 80L)
            }
        }
    }

    private fun calculateEasyMove(gameData: Array<IntArray>): Pair<Int, Int> {
        // 傻装傻子模式：随机选择空位，20%概率选择最佳位置
        val emptyPositions = mutableListOf<Pair<Int, Int>>()
        for (row in gameData.indices) {
            for (col in gameData[row].indices) {
                if (gameData[row][col] == 0) {
                    emptyPositions.add(Pair(row, col))
                }
            }
        }
        return if (emptyPositions.isNotEmpty() && (Math.random() < 0.2 || emptyPositions.size <= 5)) {
            calculateHardMove(gameData)
        } else {
            emptyPositions.random()
        }
    }

    private fun calculateMediumMove(gameData: Array<IntArray>): Pair<Int, Int> {
        // 简单难度：评估简化棋型，限制搜索范围
        var bestScore = Int.MIN_VALUE
        var bestRow = -1
        var bestCol = -1
        val searchRange = 4
        val (lastRow, lastCol) = findLastMove(gameData) ?: Pair(7, 7)

        for (row in (lastRow - searchRange)..(lastRow + searchRange)) {
            for (col in (lastCol - searchRange)..(lastCol + searchRange)) {
                if (row in gameData.indices && col in gameData[row].indices && gameData[row][col] == 0) {
                    val score = evaluatePosition(gameData, row, col, AI_PLAYER) * 0.8f
                    val blockScore = evaluatePosition(gameData, row, col, HUMAN_PLAYER)
                    val totalScore = (score + blockScore).toInt()

                    if (totalScore > bestScore) {
                        bestScore = totalScore
                        bestRow = row
                        bestCol = col
                    }
                }
            }
        }

        return if (bestRow == -1 || bestCol == -1) calculateEasyMove(gameData) else Pair(
            bestRow,
            bestCol
        )
    }

    private fun calculateHardMove(gameData: Array<IntArray>): Pair<Int, Int> {
        //正常难度
        var bestScore = Int.MIN_VALUE
        var bestRow = -1
        var bestCol = -1

        // 遍历整个棋盘评估每个空位
        for (row in gameData.indices) {
            for (col in gameData[row].indices) {
                if (gameData[row][col] == 0) {
                    // 评估当前位置对AI的价值
                    val score = evaluatePosition(gameData, row, col, AI_PLAYER)
                    // 同时考虑阻挡玩家的高分位置
                    val blockScore = evaluatePosition(gameData, row, col, HUMAN_PLAYER)
                    val totalScore = score + blockScore * 0.8f

                    // 更新最佳位置
                    if (totalScore > bestScore) {
                        bestScore = totalScore.toInt()
                        bestRow = row
                        bestCol = col
                    }
                }
            }
        }

        // 如果没有找到最佳位置（棋盘已满），返回默认值
        return if (bestRow == -1 || bestCol == -1) {
            Pair(7, 7) // 默认中心位置
        } else {
            Pair(bestRow, bestCol)
        }
    }


    /**
     * 检查当前棋局的获胜者
     */
    // 新增三连检测功能
    private fun checkLineThreat(gameData: Array<IntArray>, player: Int, targetCount: Int): Boolean {
        for (row in gameData.indices) {
            for (col in gameData[row].indices) {
                if (gameData[row][col] == player) {
                    // 检查四个方向
                    if (checkDirection(gameData, row, col, 0, 1, player, targetCount) ||
                        checkDirection(gameData, row, col, 1, 0, player, targetCount) ||
                        checkDirection(gameData, row, col, 1, 1, player, targetCount) ||
                        checkDirection(gameData, row, col, 1, -1, player, targetCount)
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun checkWinner(gameData: Array<IntArray>): Int {
        // 检查横向
        for (row in gameData.indices) {
            for (col in 0..gameData[row].size - 5) {
                val current = gameData[row][col]
                if (current != 0 && current == gameData[row][col + 1] && current == gameData[row][col + 2] &&
                    current == gameData[row][col + 3] && current == gameData[row][col + 4]
                ) {
                    return current
                }
            }
        }

        // 检查纵向
        for (col in gameData[0].indices) {
            for (row in 0..gameData.size - 5) {
                val current = gameData[row][col]
                if (current != 0 && current == gameData[row + 1][col] && current == gameData[row + 2][col] &&
                    current == gameData[row + 3][col] && current == gameData[row + 4][col]
                ) {
                    return current
                }
            }
        }

        // 检查对角线（右下）
        for (row in 0..gameData.size - 5) {
            for (col in 0..gameData[row].size - 5) {
                val current = gameData[row][col]
                if (current != 0 && current == gameData[row + 1][col + 1] && current == gameData[row + 2][col + 2] &&
                    current == gameData[row + 3][col + 3] && current == gameData[row + 4][col + 4]
                ) {
                    return current
                }
            }
        }

        // 检查对角线（左下）
        for (row in 0..gameData.size - 5) {
            for (col in 4 until gameData[row].size) {
                val current = gameData[row][col]
                if (current != 0 && current == gameData[row + 1][col - 1] && current == gameData[row + 2][col - 2] &&
                    current == gameData[row + 3][col - 3] && current == gameData[row + 4][col - 4]
                ) {
                    return current
                }
            }
        }

        return 0 // 无获胜者
    }

    /**
     * 评估整个棋盘的分数
     */
    private fun evaluateBoard(gameData: Array<IntArray>): Double {
        var score = 0.0

        // 评估AI和玩家的棋型分数
        for (row in gameData.indices) {
            for (col in gameData[row].indices) {
                if (gameData[row][col] == AI_PLAYER) {
                    // 加强主动进攻评估
                    score += evaluatePosition(gameData, row, col, AI_PLAYER) * 1.2
                } else if (gameData[row][col] == HUMAN_PLAYER) {
                    // 增强对手威胁检测灵敏度
                    score -= evaluatePosition(gameData, row, col, HUMAN_PLAYER) * 1.5
                }
            }
        }

        return score
    }

    /**
     * 评估指定位置的价值
     */
    private fun evaluatePosition(gameData: Array<IntArray>, row: Int, col: Int, player: Int): Int {
        // 模拟落子
        gameData[row][col] = player

        // 检查四个方向的棋型
        val horizontalScore = evaluateLine(gameData, row, col, 0, 1, player)
        val verticalScore = evaluateLine(gameData, row, col, 1, 0, player)
        val diagonal1Score = evaluateLine(gameData, row, col, 1, 1, player)
        val diagonal2Score = evaluateLine(gameData, row, col, 1, -1, player)

        // 撤销模拟落子
        gameData[row][col] = 0

        // 计算位置中心奖励（距离中心越近奖励越高）
        val boardSize = gameData.size
        val centerDistance = Math.abs(row - boardSize / 2) + Math.abs(col - boardSize / 2)
        val positionBonus = (boardSize - centerDistance) * CENTER_BONUS / boardSize

        // 返回总评分（棋型分 + 位置分）
        return horizontalScore + verticalScore + diagonal1Score + diagonal2Score + positionBonus
    }

    /**
     * 评估指定方向上的棋型价值
     */
    private fun findLastMove(gameData: Array<IntArray>): Pair<Int, Int>? {
        // 找到最后落子位置
        for (row in gameData.indices.reversed()) {
            for (col in gameData[row].indices.reversed()) {
                if (gameData[row][col] != 0) {
                    return Pair(row, col)
                }
            }
        }
        return null
    }

    // 改进斜向棋型识别
    private fun checkDirection(
        gameData: Array<IntArray>,
        row: Int,
        col: Int,
        rowDir: Int,
        colDir: Int,
        player: Int,
        targetCount: Int
    ): Boolean {
        var count = 1
        var r = row + rowDir
        var c = col + colDir

        // 正向检测
        while (r in gameData.indices && c in gameData[r].indices && gameData[r][c] == player) {
            count++
            r += rowDir
            c += colDir
            if (count >= targetCount) return true
        }

        // 反向检测
        r = row - rowDir
        c = col - colDir
        while (r in gameData.indices && c in gameData[r].indices && gameData[r][c] == player) {
            count++
            r -= rowDir
            c -= colDir
            if (count >= targetCount) return true
        }

        return count >= targetCount
    }

    private fun evaluateLine(
        gameData: Array<IntArray>,
        row: Int,
        col: Int,
        rowDir: Int,
        colDir: Int,
        player: Int
    ): Int {
        var score = 0
        val opponent = if (player == AI_PLAYER) HUMAN_PLAYER else AI_PLAYER

        // 检查连续的己方棋子
        var count = 1 // 当前位置已有一个棋子
        var space = 0 // 连续棋子两端的空格数
        var blocked = 0 // 连续棋子两端的阻挡数

        // 向一个方向检查
        var r = row + rowDir
        var c = col + colDir
        while (r in gameData.indices && c in gameData[r].indices && (gameData[r][c] == player || gameData[r][c] == 0)) {
            if (gameData[r][c] == player) {
                count++
            } else {
                space++
                break // 遇到空格停止该方向检查
            }
            r += rowDir
            c += colDir
        }
        // 检查是否被阻挡
        if (r in gameData.indices && c in gameData[r].indices && gameData[r][c] == opponent) {
            blocked++
        }

        // 向相反方向检查
        r = row - rowDir
        c = col - colDir
        while (r in gameData.indices && c in gameData[r].indices && (gameData[r][c] == player || gameData[r][c] == 0)) {
            if (gameData[r][c] == player) {
                count++
            } else {
                space++
                break // 遇到空格停止该方向检查
            }
            r -= rowDir
            c -= colDir
        }
        // 检查是否被阻挡
        if (r in gameData.indices && c in gameData[r].indices && gameData[r][c] == opponent) {
            blocked++
        }

        // 改进棋型评估逻辑，增强进攻性
        score = when (count) {
            5 -> WIN_SCORE // 五连珠，获胜
            4 -> when {
                blocked == 0 -> FOUR_SCORE // 活四
                space >= 1 -> BLOCK_FOUR_SCORE // 冲四（有发展空间）
                else -> 0
            }

            3 -> when {
                blocked == 0 && space >= 2 -> THREE_SCORE * 2 // 活三（两端开放）
                (blocked == 0 && space >= 1) || (blocked == 1 && space >= 1) -> THREE_SCORE // 冲三（一端开放）
                else -> BLOCK_THREE_SCORE
            }

            2 -> when {
                blocked == 0 && space >= 2 -> TWO_SCORE * 2 // 活二（两端开放）
                blocked == 1 && space >= 1 -> TWO_SCORE // 冲二
                else -> 0
            }

            else -> 0
        }

        return score
    }
}