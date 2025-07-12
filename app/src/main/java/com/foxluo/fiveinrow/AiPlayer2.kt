import kotlin.math.abs

typealias Board = Array<IntArray>
typealias Position = Pair<Int, Int>
typealias Direction = Pair<Int, Int>

fun calculateExpertMove(gameData: Board): Position {
    val size = gameData.size
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

    // 7. 检查白方进攻机会
    val attackMoves = findAttackMoves(gameData, directions)
    if (attackMoves.isNotEmpty()) {
        return selectBestAttackMove(gameData, attackMoves)
    }

    // 8. 检查黑方其他威胁
    val defenseMoves = findDefenseMoves(gameData, directions)
    if (defenseMoves.isNotEmpty()) {
        return selectBestDefenseMove(gameData, defenseMoves)
    }

    // 9. 战略布局
    return evaluateBoard(gameData, directions)
}

// 增强：通用跳四威胁检测（支持任意玩家）
fun findJumpFourThreats(board: Board, player: Int, directions: List<Direction>): Map<Position, Int> {
    val threats = mutableMapOf<Position, Int>()
    val size = board.size
    val opponent = -player

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue

            for ((dx, dy) in directions) {
                // 模式1：左跳四 (玩家-空-玩家-玩家)
                if (isValid(x - dx, y - dy, size) && board[x - dx][y - dy] == player &&
                    isValid(x + dx, y + dy, size) && board[x + dx][y + dy] == player &&
                    isValid(x + 2 * dx, y + 2 * dy, size) && board[x + 2 * dx][y + 2 * dy] == player
                ) {

                    // 检查威胁有效性：两端至少有一端开放
                    if (isValid(x - 2 * dx, y - 2 * dy, size) && board[x - 2 * dx][y - 2 * dy] == 0 ||
                        isValid(x + 3 * dx, y + 3 * dy, size) && board[x + 3 * dx][y + 3 * dy] == 0
                    ) {
                        threats[Position(x, y)] = if (player == 1) 900 else 1000
                    }
                }

                // 模式2：右跳四 (玩家-玩家-空-玩家)
                if (isValid(x - 2 * dx, y - 2 * dy, size) && board[x - 2 * dx][y - 2 * dy] == player &&
                    isValid(x - dx, y - dy, size) && board[x - dx][y - dy] == player &&
                    isValid(x + dx, y + dy, size) && board[x + dx][y + dy] == player
                ) {

                    if (isValid(x - 3 * dx, y - 3 * dy, size) && board[x - 3 * dx][y - 3 * dy] == 0 ||
                        isValid(x + 2 * dx, y + 2 * dy, size) && board[x + 2 * dx][y + 2 * dy] == 0
                    ) {
                        threats[Position(x, y)] = if (player == 1) 900 else 1000
                    }
                }

                // 模式3：中间跳四 (玩家-玩家-空-玩家-玩家)
                if (isValid(x - 2 * dx, y - 2 * dy, size) && board[x - 2 * dx][y - 2 * dy] == player &&
                    isValid(x - dx, y - dy, size) && board[x - dx][y - dy] == player &&
                    isValid(x + dx, y + dy, size) && board[x + dx][y + dy] == player &&
                    isValid(x + 2 * dx, y + 2 * dy, size) && board[x + 2 * dx][y + 2 * dy] == player
                ) {

                    threats[Position(x, y)] = if (player == 1) 950 else 1050
                }
            }
        }
    }
    return threats
}

// 新增：检查黑方无阻碍三连（两侧开放）
fun findOpenThreeThreats(board: Board, directions: List<Direction>): Map<Position, Int> {
    val threats = mutableMapOf<Position, Int>()
    val size = board.size

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 1) continue

            for ((dx, dy) in directions) {
                // 跳过非起始位置
                val prevX = x - dx
                val prevY = y - dy
                if (isValid(prevX, prevY, size) && board[prevX][prevY] == 1) {
                    continue
                }

                // 分析连线
                val (length, openEnds, openEndCount) = analyzeLine(board, x, y, dx, dy, 1)

                // 只处理两侧开放的三连
                if (length == 3 && openEndCount == 2) {
                    openEnds.forEach { end ->
                        // 高优先级：必须防守的威胁
                        threats[end] = threats.getOrDefault(end, 0) + 500
                    }
                }
            }
        }
    }
    return threats
}

// 新增：寻找黑方跳四威胁（非连续四子但形成必胜）
private fun findBlackJumpFourThreats(board: Board, directions: List<Direction>): Map<Position, Int> {
    val threats = mutableMapOf<Position, Int>()
    val size = board.size

    // 黑方跳四模式：黑-空-黑-黑 或 黑-黑-空-黑 且两侧开放
    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue

            for ((dx, dy) in directions) {
                // 模式1：左侧黑-空-黑-黑
                if (isValid(x - 3 * dx, y - 3 * dy, size) &&
                    board[x - 3 * dx][y - 3 * dy] == 1 &&
                    board[x - 2 * dx][y - 2 * dy] == 0 &&
                    board[x - dx][y - dy] == 1 &&
                    board[x][y] == 1 &&
                    areEndsOpen(board, x - 2 * dx, y - 2 * dy, dx, dy)
                ) {
                    threats[Position(x - 2 * dx, y - 2 * dy)] = 800 // 高优先级
                }

                // 模式2：右侧黑-黑-空-黑
                if (isValid(x + 3 * dx, y + 3 * dy, size) &&
                    board[x][y] == 1 &&
                    board[x + dx][y + dy] == 1 &&
                    board[x + 2 * dx][y + 2 * dy] == 0 &&
                    board[x + 3 * dx][y + 3 * dy] == 1 &&
                    areEndsOpen(board, x + 2 * dx, y + 2 * dy, dx, dy)
                ) {
                    threats[Position(x + 2 * dx, y + 2 * dy)] = 800
                }

                // 模式3：中间跳四 黑-黑-空-黑-黑
                if (isValid(x - 4 * dx, y - 4 * dy, size) &&
                    board[x - 4 * dx][y - 4 * dy] == 1 &&
                    board[x - 3 * dx][y - 3 * dy] == 1 &&
                    board[x - 2 * dx][y - 2 * dy] == 0 &&
                    board[x - dx][y - dy] == 1 &&
                    board[x][y] == 1 &&
                    areEndsOpen(board, x - 2 * dx, y - 2 * dy, dx, dy)
                ) {
                    threats[Position(x - 2 * dx, y - 2 * dy)] = 900 // 极高优先级
                }
            }
        }
    }
    return threats
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

// 寻找立即获胜的位置（对指定玩家）
fun findWinningMove(board: Board, player: Int, directions: List<Direction>): Position {
    val size = board.size
    for (i in 0 until size) {
        for (j in 0 until size) {
            if (board[i][j] != 0) continue

            // 模拟落子
            board[i][j] = player
            // 检查是否形成五连
            if (checkFiveInRow(board, i, j, player, directions)) {
                board[i][j] = 0 // 恢复棋盘
                return Pair(i, j)
            }
            board[i][j] = 0 // 恢复棋盘
        }
    }
    return Pair(-1, -1)
}

// 检查是否形成五连
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

// 新增：寻找白方特殊棋型（间隔空位的组合）
fun findSpecialWhitePatterns(board: Board, directions: List<Direction>): Map<Position, Int> {
    val specialMoves = mutableMapOf<Position, Int>()
    val size = board.size

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue

            for ((dx, dy) in directions) {
                // 模式1：间隔空位的三连潜力（白-空-白）
                if (isValid(x - 2 * dx, y - 2 * dy, size) &&
                    board[x - 2 * dx][y - 2 * dy] == -1 &&
                    board[x - dx][y - dy] == 0 &&
                    board[x][y] == 0 &&
                    isValid(x + dx, y + dy, size) &&
                    board[x + dx][y + dy] == -1
                ) {
                    specialMoves[Position(x - dx, y - dy)] = specialMoves.getOrDefault(Position(x - dx, y - dy), 0) + 150
                    specialMoves[Position(x, y)] = specialMoves.getOrDefault(Position(x, y), 0) + 200
                }

                // 模式2：间隔空位的四连潜力（白-空-白-白 或 白-白-空-白）
                if (isValid(x - 3 * dx, y - 3 * dy, size) &&
                    board[x - 3 * dx][y - 3 * dy] == -1 &&
                    board[x - 2 * dx][y - 2 * dy] == 0 &&
                    board[x - dx][y - dy] == -1 &&
                    board[x][y] == -1
                ) {
                    specialMoves[Position(x - 2 * dx, y - 2 * dy)] = specialMoves.getOrDefault(Position(x - 2 * dx, y - 2 * dy), 0) + 300
                }

                if (isValid(x + 3 * dx, y + 3 * dy, size) &&
                    board[x][y] == -1 &&
                    board[x + dx][y + dy] == -1 &&
                    board[x + 2 * dx][y + 2 * dy] == 0 &&
                    board[x + 3 * dx][y + 3 * dy] == -1
                ) {
                    specialMoves[Position(x + 2 * dx, y + 2 * dy)] = specialMoves.getOrDefault(Position(x + 2 * dx, y + 2 * dy), 0) + 300
                }

                // 模式3：间隔空位的两侧开放四连
                if (isValid(x - 4 * dx, y - 4 * dy, size) &&
                    board[x - 4 * dx][y - 4 * dy] == -1 &&
                    board[x - 3 * dx][y - 3 * dy] == 0 &&
                    board[x - 2 * dx][y - 2 * dy] == -1 &&
                    board[x - dx][y - dy] == -1 &&
                    board[x][y] == -1 &&
                    areEndsOpen(board, x - 3 * dx, y - 3 * dy, dx, dy)
                ) {
                    specialMoves[Position(x - 3 * dx, y - 3 * dy)] = specialMoves.getOrDefault(Position(x - 3 * dx, y - 3 * dy), 0) + 400
                }
            }
        }
    }
    return specialMoves
}

// 寻找进攻机会（白方）
fun findAttackMoves(board: Board, directions: List<Direction>): Map<Position, Int> {
    return findPotentialMoves(board, -1, directions, attackMode = true)
}

// 寻找防守位置（黑方）
fun findDefenseMoves(board: Board, directions: List<Direction>): Map<Position, Int> {
    return findPotentialMoves(board, 1, directions, attackMode = false)
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

// 分析连线情况（返回长度、开放端点、开放端点数量）
fun analyzeLine(
    board: Board,
    startX: Int, startY: Int,
    dx: Int, dy: Int,
    player: Int
): Triple<Int, List<Position>, Int> {
    var length = 1
    val size = board.size
    val openEnds = mutableListOf<Position>()

    // 检查正方向
    var x = startX + dx
    var y = startY + dy
    var foundEnd = false
    while (x in 0 until size && y in 0 until size) {
        when (board[x][y]) {
            player -> length++
            else -> {
                if (board[x][y] == 0) {
                    openEnds.add(Pair(x, y))
                }
                foundEnd = true
                break
            }
        }
        x += dx
        y += dy
    }
    if (!foundEnd && x in 0 until size && y in 0 until size && board[x][y] == 0) {
        openEnds.add(Pair(x, y))
    }

    // 检查反方向
    x = startX - dx
    y = startY - dy
    foundEnd = false
    while (x in 0 until size && y in 0 until size) {
        when (board[x][y]) {
            player -> length++
            else -> {
                if (board[x][y] == 0) {
                    openEnds.add(Pair(x, y))
                }
                foundEnd = true
                break
            }
        }
        x -= dx
        y -= dy
    }
    if (!foundEnd && x in 0 until size && y in 0 until size && board[x][y] == 0) {
        openEnds.add(Pair(x, y))
    }

    return Triple(length, openEnds.distinct(), openEnds.size)
}

// 辅助函数：检查坐标是否有效
private fun isValid(x: Int, y: Int, size: Int): Boolean {
    return x in 0 until size && y in 0 until size
}

// 选择最佳特殊棋型位置
fun selectBestSpecialMove(board: Board, specialMoves: Map<Position, Int>): Position {
    // 优先处理最高得分机会
    val maxScore = specialMoves.values.maxOrNull() ?: 0
    val bestMoves = specialMoves.filter { it.value == maxScore }.keys

    // 如果有多个机会，选择能最大化白方优势的位置
    return if (bestMoves.size > 1) {
        evaluatePositions(board, bestMoves, -1)
    } else {
        bestMoves.first()
    }
}

// 选择最佳进攻位置
fun selectBestAttackMove(board: Board, opportunities: Map<Position, Int>): Position {
    // 优先处理最高得分机会
    val maxScore = opportunities.values.maxOrNull() ?: 0
    val bestOpportunities = opportunities.filter { it.value == maxScore }.keys

    // 如果有多个机会，选择能最大化白方优势的位置
    return if (bestOpportunities.size > 1) {
        evaluatePositions(board, bestOpportunities, -1)
    } else {
        bestOpportunities.first()
    }
}

// 选择最佳防守位置
fun selectBestDefenseMove(board: Board, threats: Map<Position, Int>): Position {
    // 优先处理最高威胁
    val maxThreat = threats.values.maxOrNull() ?: 0
    val criticalThreats = threats.filter { it.value == maxThreat }.keys

    // 如果有多个威胁，选择能同时增强白方攻势的位置
    return if (criticalThreats.size > 1) {
        evaluatePositions(board, criticalThreats, -1)
    } else {
        criticalThreats.first()
    }
}

// 评估棋盘并选择最佳位置
fun evaluateBoard(board: Board, directions: List<Direction>): Position {
    val size = board.size
    var bestScore = Int.MIN_VALUE
    var bestPosition = Pair(-1, -1)

    for (i in 0 until size) {
        for (j in 0 until size) {
            if (board[i][j] != 0) continue

            // 计算位置得分（进攻权重 > 防守权重）
            val score = calculatePositionScore(board, i, j, directions)
            if (score > bestScore) {
                bestScore = score
                bestPosition = Pair(i, j)
            }
        }
    }

    return bestPosition
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

// 评估特殊棋型潜力
fun evaluateSpecialPatternPotential(board: Board, x: Int, y: Int, directions: List<Direction>): Int {
    var potential = 0
    val size = board.size

    for ((dx, dy) in directions) {
        // 检查间隔空位的三连潜力
        if (isValid(x - dx, y - dy, size) && board[x - dx][y - dy] == -1 &&
            isValid(x + dx, y + dy, size) && board[x + dx][y + dy] == -1
        ) {
            potential += 50
        }

        // 检查间隔空位的四连潜力
        if (isValid(x - 2 * dx, y - 2 * dy, size) && board[x - 2 * dx][y - 2 * dy] == -1 &&
            isValid(x - dx, y - dy, size) && board[x - dx][y - dy] == -1 &&
            isValid(x + dx, y + dy, size) && board[x + dx][y + dy] == -1
        ) {
            potential += 100
        }
    }

    return potential
}

// 评估特定玩家在位置的得分
fun evaluatePlayerScore(board: Board, x: Int, y: Int, player: Int, directions: List<Direction>): Int {
    var totalScore = 0
    val size = board.size

    for ((dx, dy) in directions) {
        var score = 0
        var count = 1 // 当前位置

        // 检查正方向
        var nx = x + dx
        var ny = y + dy
        while (isValid(nx, ny, size) && board[nx][ny] == player) {
            count++
            nx += dx
            ny += dy
        }
        val endOpen = isValid(nx, ny, size) && board[nx][ny] == 0

        // 检查反方向
        nx = x - dx
        ny = y - dy
        while (isValid(nx, ny, size) && board[nx][ny] == player) {
            count++
            nx -= dx
            ny -= dy
        }
        val startOpen = isValid(nx, ny, size) && board[nx][ny] == 0

        // 根据连线和开放情况评分
        when (count) {
            4 -> {
                when {
                    startOpen && endOpen -> score += 500 // 活四（必胜）
                    startOpen || endOpen -> score += 300 // 冲四（立即威胁）
                }
            }

            3 -> {
                when {
                    startOpen && endOpen -> score += 200 // 活三
                    startOpen || endOpen -> score += 80  // 眠三
                }
            }

            2 -> {
                when {
                    startOpen && endOpen -> score += 50  // 活二
                    startOpen || endOpen -> score += 20  // 眠二
                }
            }

            1 -> {
                if (startOpen || endOpen) score += 5 // 单子
            }
        }

        totalScore += score
    }

    return totalScore
}

// 计算周围棋子数量
fun countAdjacentPieces(board: Board, x: Int, y: Int): Int {
    var count = 0
    val size = board.size

    for (dx in -1..1) {
        for (dy in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (isValid(nx, ny, size) && board[nx][ny] != 0) {
                count++
            }
        }
    }

    return count
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