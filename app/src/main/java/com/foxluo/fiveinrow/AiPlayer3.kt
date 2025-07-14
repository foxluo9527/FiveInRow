import kotlin.math.abs

typealias MoveWithScore = Pair<Position, Int>

fun calculateDifficultMove(gameData: Board): Position {
    if (countMoves(gameData) < 3) {
        return getOpeningMove(gameData) // 专门处理前3步的开局库
    }
    val directions = listOf(
        Pair(1, 0),  // 水平
        Pair(0, 1),  // 垂直
        Pair(1, 1),  // 右下斜线
        Pair(1, -1)  // 右上斜线
    )

    // 1. 优先检查白方立即获胜的机会
    val winMove = findWinningMove(gameData, -1, directions)
    if (winMove.first != -1) return winMove

    // 2. 检查黑方立即获胜的威胁（包括跳四）
    val blockMove = findWinningMove(gameData, 1, directions)
    if (blockMove.first != -1) return blockMove

    // 3. 高优先级：检查黑方跳四威胁（必须立即防守）
    val blackJumpFourThreats = findJumpFourThreats(gameData, 1, directions)
    if (blackJumpFourThreats.isNotEmpty()) {
        return selectBestDefenseMove(gameData, blackJumpFourThreats)
    }

    // 4. 高优先级：检查白方跳四机会（立即获胜）
    val whiteJumpFourOpportunities = findJumpFourThreats(gameData, -1, directions)
    if (whiteJumpFourOpportunities.isNotEmpty()) {
        return selectBestAttackMove(gameData, whiteJumpFourOpportunities)
    }

    // 5. 检查白方特殊棋型
    val specialWhiteMoves = findSpecialWhitePatterns(gameData, directions)
    if (specialWhiteMoves.isNotEmpty()) {
        return selectBestSpecialMove(gameData, specialWhiteMoves)
    }

    // 6. 检查黑方无阻碍三连（必须防守）
    val openThreeThreats = findOpenThreeThreats(gameData, directions)
    if (openThreeThreats.isNotEmpty()) {
        return selectBestDefenseMove(gameData, openThreeThreats)
    }

    // 7. 深度搜索（带α-β剪枝的Minimax）
    if (countMoves(gameData) < 40) { // 前40步使用深度搜索
        val depth = when {
            countMoves(gameData) < 5 -> 1  // 前5步使用较浅深度
            countMoves(gameData) < 10 -> 2
            countMoves(gameData) < 20 -> 3
            else -> 4
        }

        val (bestMove, _) = minimaxSearch(gameData, depth, Int.MIN_VALUE, Int.MAX_VALUE, true, directions)
        if (bestMove.first != -1) return bestMove
    }

    // 8. 检查白方进攻机会
    val attackMoves = findAttackMoves(gameData, directions)
    if (attackMoves.isNotEmpty()) {
        return selectBestAttackMove(gameData, attackMoves)
    }

    // 9. 检查黑方其他威胁
    val defenseMoves = findDefenseMoves(gameData, directions)
    if (defenseMoves.isNotEmpty()) {
        return selectBestDefenseMove(gameData, defenseMoves)
    }

    // 10. 战略布局
    return evaluateBoard(gameData, directions)
}
// 添加开局库函数
private fun getOpeningMove(board: Board): Position {
    val size = board.size
    val center = size / 2

    // 第一步：中心或附近
    if (countMoves(board) == 0) return Position(center, center)

    // 第二步：对称落子
    if (countMoves(board) == 1) {
        for (x in 0 until size) {
            for (y in 0 until size) {
                if (board[x][y] != 0) {
                    // 对称位置
                    val symX = size - 1 - x
                    val symY = size - 1 - y
                    if (board[symX][symY] == 0) {
                        return Position(symX, symY)
                    }
                }
            }
        }
    }

    // 第三步：形成基础棋型
    return Position(center, center - 1) // 示例位置
}
// ======================
// 深度搜索算法（Minimax with Alpha-Beta Pruning）
// ======================
private const val WIN_SCORE = 1000000
private const val LOSS_SCORE = -1000000

private fun minimaxSearch(
    board: Board,
    depth: Int,
    alpha: Int,
    beta: Int,
    maximizingPlayer: Boolean,
    directions: List<Direction>
): MoveWithScore {
    // 终止条件：达到深度限制或游戏结束
    if (depth == 0 || isGameOver(board)) {
        return Pair(Pair(-1, -1), evaluateBoardState(board, directions, maximizingPlayer))
    }

    val size = board.size
    var bestMove = Pair(-1, -1)
    var bestScore = if (maximizingPlayer) Int.MIN_VALUE else Int.MAX_VALUE
    var currentAlpha = alpha
    var currentBeta = beta

    // 生成候选位置（基于启发式评估）
    val candidateMoves = generateCandidateMoves(board, if (maximizingPlayer) -1 else 1, directions)

    for ((pos, _) in candidateMoves) {
        val (x, y) = pos

        // 跳过无效位置
        if (board[x][y] != 0) continue

        // 模拟落子
        board[x][y] = if (maximizingPlayer) -1 else 1

        // 递归搜索
        val (_, score) = minimaxSearch(board, depth - 1, currentAlpha, currentBeta, !maximizingPlayer, directions)

        // 撤销落子
        board[x][y] = 0

        // 更新最佳选择
        if (maximizingPlayer) {
            if (score > bestScore) {
                bestScore = score
                bestMove = pos
            }
            if (bestScore > currentAlpha) {
                currentAlpha = bestScore
            }
            if (currentBeta <= currentAlpha) {
                break // α-β剪枝
            }
        } else {
            if (score < bestScore) {
                bestScore = score
                bestMove = pos
            }
            if (bestScore < currentBeta) {
                currentBeta = bestScore
            }
            if (currentBeta <= currentAlpha) {
                break // α-β剪枝
            }
        }
    }

    return Pair(bestMove, bestScore)
}

// 生成候选位置（基于启发式评估）
private fun generateCandidateMoves(board: Board, player: Int, directions: List<Direction>): List<Pair<Position, Int>> {
    val moveCount = countMoves(board)

    // 开局阶段使用简化计算
    if (moveCount < 5) {
        return generateEarlyGameMoves(board)
    }

    val candidates = mutableListOf<Pair<Position, Int>>()
    val size = board.size

    // 收集所有空位并评分
    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue

            // 基本评分：位置价值和邻域活动
            var score = 100 * (size - abs(x - size / 2) - abs(y - size / 2)) // 中心价值
            score += 50 * countAdjacentPieces(board, x, y) // 邻域活动

            // 进攻/防守潜力
            if (player == -1) {
                // 白方：进攻潜力
                score += evaluatePlayerScore(board, x, y, -1, directions)
                score += evaluateSpecialPatternPotential(board, x, y, directions)
            } else {
                // 黑方：防守潜力
                score += evaluatePlayerScore(board, x, y, 1, directions)
            }

            candidates.add(Pair(Position(x, y), score))
        }
    }

    // 返回按评分排序的前15个候选位置
    return candidates.sortedByDescending { it.second }.take(15)
}

private fun generateEarlyGameMoves(board: Board): List<Pair<Position, Int>> {
    val size = board.size
    val center = size / 2
    val candidates = mutableListOf<Pair<Position, Int>>()

    // 中心区域候选点
    for (dx in -2..2) {
        for (dy in -2..2) {
            val x = center + dx
            val y = center + dy
            if (x in 0 until size && y in 0 until size && board[x][y] == 0) {
                // 简化评分：只计算基本位置价值
                val score = 100 * (size - abs(x - center) - abs(y - center))
                candidates.add(Pair(Position(x, y), score))
            }
        }
    }

    return candidates.sortedByDescending { it.second }.take(5) // 只取前5个
}

// 评估棋盘状态
private fun evaluateBoardState(board: Board, directions: List<Direction>, forWhite: Boolean): Int {
    var score = 0
    val size = board.size

    // 1. 基础评估：材料优势
    var whiteCount = 0
    var blackCount = 0
    for (i in 0 until size) {
        for (j in 0 until size) {
            when (board[i][j]) {
                -1 -> whiteCount++
                1 -> blackCount++
            }
        }
    }
    score += (whiteCount - blackCount) * 10

    // 2. 位置控制评估
    val center = size / 2
    for (i in 0 until size) {
        for (j in 0 until size) {
            if (board[i][j] == 0) continue

            val distanceToCenter = abs(i - center) + abs(j - center)
            val controlValue = (size - distanceToCenter) * 3

            if (board[i][j] == -1) {
                score += controlValue
            } else {
                score -= controlValue
            }
        }
    }

    // 3. 棋型评估
    score += evaluatePatterns(board, -1, directions) // 白方棋型
    score -= (evaluatePatterns(board, 1, directions) * 1.5).toInt() // 黑方棋型（更高权重）

    // 4. 威胁评估
    score += evaluateThreats(board, directions)

    return if (forWhite) score else -score
}

// 评估棋型
private fun evaluatePatterns(board: Board, player: Int, directions: List<Direction>): Int {
    // 开局阶段跳过复杂棋型检测
    if (countMoves(board) < 8) return 0

    var score = 0
    val size = board.size

    for (i in 0 until size) {
        for (j in 0 until size) {
            if (board[i][j] != player) continue

            for ((dx, dy) in directions) {
                // 跳过非起始位置
                val prevX = i - dx
                val prevY = j - dy
                if (isValid(prevX, prevY, size) && board[prevX][prevY] == player) {
                    continue
                }

                val (length, openEnds, openEndCount) = analyzeLine(board, i, j, dx, dy, player)

                when {
                    // 活四 (必胜)
                    length >= 4 && openEndCount == 2 -> score += 10000
                    // 冲四
                    length >= 4 && openEndCount == 1 -> score += 1000
                    // 活三
                    length == 3 && openEndCount == 2 -> score += 500
                    // 眠三
                    length == 3 && openEndCount == 1 -> score += 100
                    // 活二
                    length == 2 && openEndCount == 2 -> score += 50
                    // 眠二
                    length == 2 && openEndCount == 1 -> score += 10
                }
            }
        }
    }

    return score
}

// 评估威胁
private fun evaluateThreats(board: Board, directions: List<Direction>): Int {
    var score = 0

    // 白方威胁
    score += findJumpFourThreats(board, -1, directions).values.sum() * 2
    score += findDoubleThreats(board, -1, directions).values.sum() * 3

    // 黑方威胁（负分）
    score -= findJumpFourThreats(board, 1, directions).values.sum() * 3
    score -= findDoubleThreats(board, 1, directions).values.sum() * 4

    return score
}

// 检测双三威胁
private fun findDoubleThreats(board: Board, player: Int, directions: List<Direction>): Map<Position, Int> {
    val threats = mutableMapOf<Position, Int>()
    val size = board.size

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue

            // 模拟落子
            board[x][y] = player
            var threatCount = 0

            // 检查是否形成多个活三
            for ((dx, dy) in directions) {
                val (length, openEnds, openEndCount) = analyzeLine(board, x, y, dx, dy, player)
                if (length == 3 && openEndCount == 2) {
                    threatCount++
                }
            }

            // 撤销落子
            board[x][y] = 0

            // 如果形成两个或更多活三，则是双三威胁
            if (threatCount >= 2) {
                threats[Position(x, y)] = 1000
            }
        }
    }

    return threats
}

// 检测四三威胁
private fun findFourThreeThreat(board: Board, directions: List<Direction>): Position {
    val size = board.size

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue

            // 模拟黑方落子
            board[x][y] = 1

            // 检查是否同时形成冲四和活三
            var hasFour = false
            var hasThree = false

            for ((dx, dy) in directions) {
                val (length, openEnds, openEndCount) = analyzeLine(board, x, y, dx, dy, 1)

                if (length >= 4 && openEndCount > 0) {
                    hasFour = true
                }
                if (length == 3 && openEndCount == 2) {
                    hasThree = true
                }
            }

            // 撤销落子
            board[x][y] = 0

            if (hasFour && hasThree) {
                // 四三威胁 - 必须防守
                return Position(x, y)
            }
        }
    }

    return Pair(-1, -1)
}

// 高级威胁检测系统
private fun findCriticalThreats(board: Board, directions: List<Direction>): Position {
    // 1. 双三威胁检测（黑方同时形成两个活三）
    val doubleThrees = findDoubleThreats(board, 1, directions)
    if (doubleThrees.isNotEmpty()) {
        return selectBestDefenseMove(board, doubleThrees)
    }

    // 2. 四三威胁（黑方形成冲四和活三）
    val fourThreeThreat = findFourThreeThreat(board, directions)
    if (fourThreeThreat.first != -1) {
        return fourThreeThreat
    }

    return Pair(-1, -1)
}

// 辅助函数
private fun countMoves(board: Board): Int {
    var count = 0
    for (row in board) {
        for (cell in row) {
            if (cell != 0) count++
        }
    }
    return count
}

private fun isGameOver(board: Board): Boolean {
    // 检查是否有五连珠
    return findWinningMove(board, -1, listOf()) != Pair(-1, -1) ||
            findWinningMove(board, 1, listOf()) != Pair(-1, -1)
}

private fun checkFiveInRow(board: Board, x: Int, y: Int, player: Int, directions: List<Direction>): Boolean {
    for ((dx, dy) in directions) {
        var count = 1 // 当前落子

        // 正方向计数
        var nx = x + dx
        var ny = y + dy
        while (isValid(nx, ny, board.size) && board[nx][ny] == player) {
            count++
            nx += dx
            ny += dy
        }

        // 反方向计数
        nx = x - dx
        ny = y - dy
        while (isValid(nx, ny, board.size) && board[nx][ny] == player) {
            count++
            nx -= dx
            ny -= dy
        }

        if (count >= 5) return true
    }
    return false
}

private fun isValid(x: Int, y: Int, size: Int): Boolean {
    return x in 0 until size && y in 0 until size
}

// 检查棋型两端是否开放
private fun areEndsOpen(board: Board, x: Int, y: Int, dx: Int, dy: Int): Boolean {
    val size = board.size
    // 检查正方向
    var nx = x + dx
    var ny = y + dy
    if (isValid(nx, ny, size) && board[nx][ny] != 0) {
        return false
    }

    // 检查反方向
    nx = x - dx
    ny = y - dy
    if (isValid(nx, ny, size) && board[nx][ny] != 0) {
        return false
    }

    return true
}


// 寻找潜在走法（通用）
private fun findPotentialMoves(
    board: Board,
    player: Int,
    directions: List<Direction>,
    attackMode: Boolean
): Map<Position, Int> {
    val opportunities = mutableMapOf<Position, Int>()
    val size = board.size

    for (i in 0 until size) {
        for (j in 0 until size) {
            if (board[i][j] != player) continue

            for ((dx, dy) in directions) {
                // 跳过非起始位置
                val prevX = i - dx
                val prevY = j - dy
                if (prevX in 0 until size && prevY in 0 until size && board[prevX][prevY] == player) {
                    continue
                }

                // 分析连线
                val (length, openEnds, openEndCount) = analyzeLine(board, i, j, dx, dy, player)

                // 进攻模式：优先处理白方机会
                if (attackMode) {
                    when {
                        length == 4 && openEndCount > 0 -> {
                            // 冲四：立即获胜机会
                            openEnds.forEach { end -> opportunities[end] = opportunities.getOrDefault(end, 0) + 1000 }
                        }

                        length == 3 && openEndCount == 2 -> {
                            // 活三：必胜机会
                            openEnds.forEach { end -> opportunities[end] = opportunities.getOrDefault(end, 0) + 500 }
                        }

                        length == 3 && openEndCount == 1 -> {
                            // 眠三：潜在机会
                            openEnds.forEach { end -> opportunities[end] = opportunities.getOrDefault(end, 0) + 100 }
                        }

                        length == 2 && openEndCount == 2 -> {
                            // 活二：发展机会
                            openEnds.forEach { end -> opportunities[end] = opportunities.getOrDefault(end, 0) + 50 }
                        }
                    }
                }
                // 防守模式：处理黑方威胁（但忽略已被部分防守的）
                else {
                    when {
                        length == 4 && openEndCount > 0 -> {
                            // 黑方冲四：必须防守
                            openEnds.forEach { end -> opportunities[end] = opportunities.getOrDefault(end, 0) + 1000 }
                        }

                        length == 3 && openEndCount == 2 -> {
                            // 黑方活三：高优先级防守
                            openEnds.forEach { end -> opportunities[end] = opportunities.getOrDefault(end, 0) + 300 }
                        }
                        // 忽略已被部分防守的黑方三连（openEndCount == 1）
                    }
                }
            }
        }
    }
    return opportunities
}


// 计算位置得分
private fun calculatePositionScore(board: Board, x: Int, y: Int, directions: List<Direction>): Int {
    var score = 0

    // 进攻得分：评估白方机会（更高权重）
    score += evaluatePlayerScore(board, x, y, -1, directions) * 10

    // 防守得分：评估黑方威胁
    score += evaluatePlayerScore(board, x, y, 1, directions) * 5

    // 位置价值：中心位置有更高价值
    val size = board.size
    val centerDist = abs(x - size / 2) + abs(y - size / 2)
    score += (size - centerDist) * 3

    // 邻域活动：周围棋子越多价值越高
    score += countAdjacentPieces(board, x, y) * 8

    // 新增：特殊棋型潜力加分
    score += evaluateSpecialPatternPotential(board, x, y, directions) * 5

    return score
}

// 评估多个位置并选择最佳
private fun evaluatePositions(board: Board, positions: Collection<Position>, player: Int): Position {
    var bestScore = Int.MIN_VALUE
    var bestPosition = positions.first()

    for (position in positions) {
        val (x, y) = position
        var score = 0

        // 评估进攻潜力（白方）
        score += evaluatePlayerScore(
            board, x, y, -1, listOf(
                Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(1, -1)
            )
        ) * 8

        // 评估防守需求（黑方）
        score += evaluatePlayerScore(
            board, x, y, 1, listOf(
                Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(1, -1)
            )
        ) * 3

        // 位置价值
        val size = board.size
        score += (size - abs(x - size / 2) - abs(y - size / 2)) * 2

        // 新增：特殊棋型潜力
        score += evaluateSpecialPatternPotential(
            board, x, y, listOf(
                Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(1, -1)
            )
        ) * 5

        if (score > bestScore) {
            bestScore = score
            bestPosition = position
        }
    }

    return bestPosition
}