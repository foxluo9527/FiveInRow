import kotlin.math.abs

private const val WIN_SCORE = 10000000
private const val LOSS_SCORE = -10000000


typealias MoveWithScore = Pair<Position, Int>
val ALL_DIRECTIONS = listOf(
    Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(1, -1)
)

fun calculateDifficultMove(gameData: Board): Position {
    val directions = ALL_DIRECTIONS

    if (countMoves(gameData) == 0) {
        return Position(gameData.size / 2, gameData.size / 2)
    }

    val winMove = findWinningMove(gameData, -1, directions)
    if (winMove.first != -1) return winMove

    val blockMove = findWinningMove(gameData, 1, directions)
    if (blockMove.first != -1) return blockMove

    if (countMoves(gameData) >= 6) {
        val vcfResult = vcfSearch(gameData, 10, true, directions)
        if (vcfResult.first != -1) return vcfResult
    }

    val blackJumpFourThreats = findJumpFourThreats(gameData, 1, directions)
    if (blackJumpFourThreats.isNotEmpty()) {
        return selectBestDefenseMove(gameData, blackJumpFourThreats)
    }

    val whiteJumpFourOpportunities = findJumpFourThreats(gameData, -1, directions)
    if (whiteJumpFourOpportunities.isNotEmpty()) {
        return selectBestAttackMove(gameData, whiteJumpFourOpportunities)
    }

    if (countMoves(gameData) >= 8) {
        val vctResult = vctSearch(gameData, 8, true, directions)
        if (vctResult.first != -1) return vctResult
    }

    val specialWhiteMoves = findSpecialWhitePatterns(gameData, directions)
    if (specialWhiteMoves.isNotEmpty()) {
        return selectBestSpecialMove(gameData, specialWhiteMoves)
    }

    val openThreeThreats = findOpenThreeThreats(gameData, directions)
    if (openThreeThreats.isNotEmpty()) {
        return selectBestDefenseMove(gameData, openThreeThreats)
    }

    val criticalThreat = findCriticalThreats(gameData, directions)
    if (criticalThreat.first != -1) return criticalThreat

    val moveCount = countMoves(gameData)
    if (moveCount < 50) {
        val depth = when {
            moveCount < 6 -> 2
            moveCount < 12 -> 3
            moveCount < 25 -> 4
            else -> 5
        }

        val (bestMove, _) = minimaxWithIterativeDeepening(gameData, depth, true, directions)
        if (bestMove.first != -1) return bestMove
    }

    val attackMoves = findAttackMoves(gameData, directions)
    if (attackMoves.isNotEmpty()) {
        return selectBestAttackMove(gameData, attackMoves)
    }

    val defenseMoves = findDefenseMoves(gameData, directions)
    if (defenseMoves.isNotEmpty()) {
        return selectBestDefenseMove(gameData, defenseMoves)
    }

    return evaluateBoard(gameData, directions)
}

private fun getOpeningMove(board: Board): Position {
    val size = board.size
    val center = size / 2
    val moveCount = countMoves(board)

    return when (moveCount) {
        0 -> Position(center, center)
        1 -> {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    if (board[x][y] != 0) {
                        val dx = abs(x - center)
                        val dy = abs(y - center)
                        return when {
                            dx <= 1 && dy <= 1 -> Position(center + 1, center + 1)
                            x < center || y < center -> Position(
                                minOf(center + 2, size - 1),
                                minOf(center + 2, size - 1)
                            )
                            else -> Position(maxOf(center - 2, 0), maxOf(center - 2, 0))
                        }
                    }
                }
            }
            Position(center + 1, center + 1)
        }
        else -> {
            val bestPos = findBestOpeningPosition(board)
            if (bestPos.first != -1) bestPos else Position(center, center - 1)
        }
    }
}

private fun findBestOpeningPosition(board: Board): Position {
    val size = board.size
    val center = size / 2
    var bestScore = Int.MIN_VALUE
    var bestPos = Position(-1, -1)

    for (dx in -2..2) {
        for (dy in -2..2) {
            val x = center + dx
            val y = center + dy
            if (x in 0 until size && y in 0 until size && board[x][y] == 0) {
                var score = 200 * (size - abs(x - center) - abs(y - center))
                score += 80 * countAdjacentPieces(board, x, y)

                board[x][y] = -1
                score += evaluatePatternsForPlayer(board, x, y, -1, ALL_DIRECTIONS) * 3
                board[x][y] = 0

                if (score > bestScore) {
                    bestScore = score
                    bestPos = Position(x, y)
                }
            }
        }
    }
    return bestPos
}

private fun vcfSearch(board: Board, maxDepth: Int, isAttacking: Boolean, directions: List<Direction>): Position {
    val attacker = if (isAttacking) -1 else 1
    val result = vcfDFS(board, maxDepth, isAttacking, attacker, directions)
    return result.first
}

private fun vcfDFS(
    board: Board,
    depth: Int,
    isAttacking: Boolean,
    attacker: Int,
    directions: List<Direction>
): Pair<Position, Boolean> {
    if (depth <= 0) return Pair(Position(-1, -1), false)

    val defender = -attacker
    val fourMoves = mutableListOf<Position>()
    val size = board.size

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue
            board[x][y] = attacker
            val hasFour = checkHasFour(board, x, y, attacker, directions)
            board[x][y] = 0
            if (hasFour) fourMoves.add(Position(x, y))
        }
    }

    if (fourMoves.isEmpty()) return Pair(Position(-1, -1), false)

    for (move in fourMoves) {
        val (x, y) = move
        board[x][y] = attacker

        if (checkWin(board, x, y, attacker, directions)) {
            board[x][y] = 0
            return Pair(move, true)
        }

        val defenderWins = findWinningMove(board, defender, directions)
        if (defenderWins.first != -1) {
            board[x][y] = 0
            continue
        }

        val defenderFours = mutableListOf<Position>()
        for (dx in 0 until size) {
            for (dy in 0 until size) {
                if (board[dx][dy] != 0) continue
                board[dx][dy] = defender
                if (checkHasFour(board, dx, dy, defender, directions)) {
                    defenderFours.add(Position(dx, dy))
                }
                board[dx][dy] = 0
            }
        }

        var canDefendAll = true
        for (defMove in defenderFours) {
            val (dx, dy) = defMove
            board[dx][dy] = defender
            val (_, success) = vcfDFS(board, depth - 2, isAttacking, attacker, directions)
            board[dx][dy] = 0
            if (!success) {
                canDefendAll = false
                break
            }
        }

        if (defenderFours.isEmpty() || canDefendAll) {
            val (_, deeperSuccess) = vcfDFS(board, depth - 1, isAttacking, attacker, directions)
            board[x][y] = 0
            if (deeperSuccess) return Pair(move, true)
        } else {
            board[x][y] = 0
        }
    }

    return Pair(Position(-1, -1), false)
}

private fun vctSearch(board: Board, maxDepth: Int, isAttacking: Boolean, directions: List<Direction>): Position {
    val attacker = if (isAttacking) -1 else 1
    val result = vctDFS(board, maxDepth, isAttacking, attacker, directions)
    return result.first
}

private fun vctDFS(
    board: Board,
    depth: Int,
    isAttacking: Boolean,
    attacker: Int,
    directions: List<Direction>
): Pair<Position, Boolean> {
    if (depth <= 0) return Pair(Position(-1, -1), false)

    val threeMoves = findLiveThreeMoves(board, attacker, directions)

    if (threeMoves.isEmpty()) return Pair(Position(-1, -1), false)

    for (move in threeMoves) {
        val (x, y) = move
        board[x][y] = attacker

        val vcfResult = vcfSearch(board, minOf(6, depth), isAttacking, directions)
        if (vcfResult.first != -1) {
            board[x][y] = 0
            return Pair(move, true)
        }

        val defender = -attacker
        val defenderWins = findWinningMove(board, defender, directions)
        if (defenderWins.first != -1) {
            board[x][y] = 0
            continue
        }

        val (_, deeperSuccess) = vctDFS(board, depth - 1, isAttacking, attacker, directions)
        board[x][y] = 0
        if (deeperSuccess) return Pair(move, true)
    }

    return Pair(Position(-1, -1), false)
}

private fun findLiveThreeMoves(board: Board, player: Int, directions: List<Direction>): List<Position> {
    val moves = mutableSetOf<Position>()
    val size = board.size

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue

            board[x][y] = player
            for ((dx, dy) in directions) {
                val (length, _, openEndCount) = analyzeLine(board, x, y, dx, dy, player)
                if (length == 3 && openEndCount >= 2) {
                    moves.add(Position(x, y))
                    break
                }
            }
            board[x][y] = 0
        }
    }
    return moves.toList()
}

private fun checkHasFour(board: Board, x: Int, y: Int, player: Int, directions: List<Direction>): Boolean {
    for ((dx, dy) in directions) {
        var count = 1
        var nx = x + dx
        var ny = y + dy
        while (isValid(nx, ny, board.size) && board[nx][ny] == player) { count++; nx += dx; ny += dy }
        nx = x - dx; ny = y - dy
        while (isValid(nx, ny, board.size) && board[nx][ny] == player) { count++; nx -= dx; ny -= dy }
        if (count >= 4) return true
    }
    return false
}

private fun checkWin(board: Board, x: Int, y: Int, player: Int, directions: List<Direction>): Boolean {
    for ((dx, dy) in directions) {
        var count = 1
        var nx = x + dx; var ny = y + dy
        while (isValid(nx, ny, board.size) && board[nx][ny] == player) { count++; nx += dx; ny += dy }
        nx = x - dx; ny = y - dy
        while (isValid(nx, ny, board.size) && board[nx][ny] == player) { count++; nx -= dx; ny -= dy }
        if (count >= 5) return true
    }
    return false
}

private fun minimaxWithIterativeDeepening(
    board: Board,
    maxDepth: Int,
    maximizing: Boolean,
    directions: List<Direction>
): MoveWithScore {
    var bestMove = Position(-1, -1)
    var bestScore = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE

    for (depth in 1..maxDepth) {
        val (move, score) = minimaxSearch(board, depth, Int.MIN_VALUE, Int.MAX_VALUE, maximizing, directions)
        if (move.first != -1) {
            bestMove = move
            bestScore = score
        }
        if (abs(score) >= WIN_SCORE / 10) break
    }

    return Pair(bestMove, bestScore)
}

private fun minimaxSearch(
    board: Board,
    depth: Int,
    alpha: Int,
    beta: Int,
    maximizingPlayer: Boolean,
    directions: List<Direction>
): MoveWithScore {
    if (depth == 0 || isGameOver(board)) {
        return Pair(Pair(-1, -1), evaluateBoardStateAdvanced(board, directions))
    }

    val player = if (maximizingPlayer) -1 else 1
    val candidateMoves = generateCandidateMovesAdvanced(board, player, directions)

    if (candidateMoves.isEmpty()) {
        return Pair(Pair(-1, -1), evaluateBoardStateAdvanced(board, directions))
    }

    var bestMove = candidateMoves.first().first
    var currentAlpha = alpha
    var currentBeta = beta

    if (maximizingPlayer) {
        var maxScore = Int.MIN_VALUE
        for ((pos, _) in candidateMoves) {
            val (x, y) = pos
            if (board[x][y] != 0) continue

            board[x][y] = player
            val winCheck = checkWinQuick(board, x, y, player)
            if (winCheck) {
                board[x][y] = 0
                return Pair(pos, WIN_SCORE - (maxDepth - depth))
            }

            val (_, score) = minimaxSearch(board, depth - 1, currentAlpha, currentBeta, false, directions)
            board[x][y] = 0

            if (score > maxScore) {
                maxScore = score
                bestMove = pos
            }
            currentAlpha = maxOf(currentAlpha, score)
            if (currentBeta <= currentAlpha) break
        }
        return Pair(bestMove, maxScore)
    } else {
        var minScore = Int.MAX_VALUE
        for ((pos, _) in candidateMoves) {
            val (x, y) = pos
            if (board[x][y] != 0) continue

            board[x][y] = player
            val winCheck = checkWinQuick(board, x, y, player)
            if (winCheck) {
                board[x][y] = 0
                return Pair(pos, LOSS_SCORE + (maxDepth - depth))
            }

            val (_, score) = minimaxSearch(board, depth - 1, currentAlpha, currentBeta, true, directions)
            board[x][y] = 0

            if (score < minScore) {
                minScore = score
                bestMove = pos
            }
            currentBeta = minOf(currentBeta, score)
            if (currentBeta <= currentAlpha) break
        }
        return Pair(bestMove, minScore)
    }
}

private const val maxDepth = 20

private fun checkWinQuick(board: Board, x: Int, y: Int, player: Int): Boolean {
    for ((dx, dy) in ALL_DIRECTIONS) {
        var count = 1
        var nx = x + dx; var ny = y + dy
        while (isValid(nx, ny, board.size) && board[nx][ny] == player) { count++; nx += dx; ny += dy }
        nx = x - dx; ny = y - dy
        while (isValid(nx, ny, board.size) && board[nx][ny] == player) { count++; nx -= dx; ny -= dy }
        if (count >= 5) return true
    }
    return false
}

private fun generateCandidateMovesAdvanced(board: Board, player: Int, directions: List<Direction>): List<Pair<Position, Int>> {
    val candidates = mutableListOf<Triple<Int, Int, Int>>()
    val size = board.size
    val nearbyRadius = 2

    val hasPieces = Array(size) { BooleanArray(size) }
    for (i in 0 until size) {
        for (j in 0 until size) {
            if (board[i][j] != 0) {
                for (di in -nearbyRadius..nearbyRadius) {
                    for (dj in -nearbyRadius..nearbyRadius) {
                        val ni = i + di
                        val nj = j + dj
                        if (ni in 0 until size && nj in 0 until size) {
                            hasPieces[ni][nj] = true
                        }
                    }
                }
            }
        }
    }

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0 || !hasPieces[x][y]) continue

            var threatScore = 0
            var patternScore = 0

            board[x][y] = player
            for ((dx, dy) in directions) {
                val (length, _, openEndCount) = analyzeLine(board, x, y, dx, dy, player)
                when {
                    length >= 4 && openEndCount > 0 -> threatScore += 100000
                    length == 3 && openEndCount == 2 -> threatScore += 50000
                    length == 3 && openEndCount == 1 -> threatScore += 5000
                    length == 2 && openEndCount == 2 -> threatScore += 1000
                    length == 2 && openEndCount == 1 -> threatScore += 100
                    length == 1 && openEndCount >= 1 -> threatScore += 10
                }
                patternScore += getPatternScore(length, openEndCount)
            }
            board[x][y] = 0

            val opponent = -player
            board[x][y] = opponent
            var oppThreatScore = 0
            for ((dx, dy) in directions) {
                val (length, _, openEndCount) = analyzeLine(board, x, y, dx, dy, opponent)
                when {
                    length >= 4 && openEndCount > 0 -> oppThreatScore += 80000
                    length == 3 && openEndCount == 2 -> oppThreatScore += 40000
                    length == 3 && openEndCount == 1 -> oppThreatScore += 4000
                    length == 2 && openEndCount == 2 -> oppThreatScore += 800
                }
            }
            board[x][y] = 0

            val centerBonus = (size - abs(x - size / 2) - abs(y - size / 2)) * 15
            val adjacentBonus = countAdjacentPieces(board, x, y) * 10

            val totalScore = threatScore + oppThreatScore + centerBonus + adjacentBonus + patternScore
            candidates.add(Triple(x, y, totalScore))
        }
    }

    return candidates.sortedByDescending { it.third }.take(20).map { Pair(Pair(it.first, it.second), it.third) }
}

private fun getPatternScore(length: Int, openEndCount: Int): Int {
    return when (length) {
        5 -> WIN_SCORE
        4 -> when (openEndCount) {
            2 -> 1000000
            1 -> 100000
            else -> 0
        }
        3 -> when (openEndCount) {
            2 -> 50000
            1 -> 5000
            else -> 500
        }
        2 -> when (openEndCount) {
            2 -> 2000
            1 -> 200
            else -> 0
        }
        1 -> when (openEndCount) {
            2 -> 20
            1 -> 5
            else -> 0
        }
        else -> 0
    }
}

private fun evaluateBoardStateAdvanced(board: Board, directions: List<Direction>): Int {
    val whiteScore = evaluatePlayerComprehensive(board, -1, directions)
    val blackScore = evaluatePlayerComprehensive(board, 1, directions)

    val whiteThreats = calculateThreatLevel(board, -1, directions)
    val blackThreats = calculateThreatLevel(board, 1, directions)

    val positionScore = evaluatePositionControl(board)

    val totalScore = (whiteScore - blackScore * 1.2).toInt() +
            (whiteThreats - blackThreats * 1.5).toInt() +
            positionScore

    return totalScore.coerceIn(LOSS_SCORE + 1000, WIN_SCORE - 1000)
}

private fun evaluatePlayerComprehensive(board: Board, player: Int, directions: List<Direction>): Int {
    var score = 0
    val size = board.size
    val processed = mutableSetOf<Triple<Int, Int, Int>>()

    for (i in 0 until size) {
        for (j in 0 until size) {
            if (board[i][j] != player) continue

            for ((dirIdx, dir) in directions.withIndex()) {
                val (dx, dy) = dir
                val key = Triple(i, j, dirIdx)
                if (key in processed) continue

                val prevX = i - dx
                val prevY = j - dy
                if (isValid(prevX, prevY, size) && board[prevX][prevY] == player) continue

                val (length, _, openEndCount) = analyzeLine(board, i, j, dx, dy, player)

                // Mark all cells in this line as processed for this direction
                var step = 0
                var cx = i
                var cy = j
                while (step < length && isValid(cx, cy, size) && board[cx][cy] == player) {
                    processed.add(Triple(cx, cy, dirIdx))
                    cx += dx
                    cy += dy
                    step++
                }

                score += getPatternScore(length, openEndCount)
            }
        }
    }

    val comboBonus = evaluateCombinationPatterns(board, player, directions)
    score += comboBonus

    return score
}

private fun evaluateCombinationPatterns(board: Board, player: Int, directions: List<Direction>): Int {
    var bonus = 0
    val size = board.size

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue

            board[x][y] = player
            var liveThreeCount = 0
            var fourCount = 0
            var sleepFourCount = 0

            for ((dx, dy) in directions) {
                val (length, _, openEndCount) = analyzeLine(board, x, y, dx, dy, player)
                when {
                    length >= 4 && openEndCount == 2 -> fourCount++
                    length >= 4 && openEndCount == 1 -> sleepFourCount++
                    length == 3 && openEndCount == 2 -> liveThreeCount++
                }
            }

            board[x][y] = 0

            when {
                liveThreeCount >= 2 -> bonus += 200000
                fourCount >= 1 && liveThreeCount >= 1 -> bonus += 150000
                fourCount >= 2 -> bonus += 300000
                sleepFourCount >= 2 -> bonus += 80000
                sleepFourCount >= 1 && liveThreeCount >= 1 -> bonus += 60000
            }
        }
    }

    return bonus
}

private fun calculateThreatLevel(board: Board, player: Int, directions: List<Direction>): Int {
    var threatLevel = 0
    val size = board.size

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue

            board[x][y] = player
            var fours = 0
            var liveThrees = 0

            for ((dx, dy) in directions) {
                val (length, _, openEndCount) = analyzeLine(board, x, y, dx, dy, player)
                if (length >= 4 && openEndCount > 0) fours++
                if (length == 3 && openEndCount == 2) liveThrees++
            }

            board[x][y] = 0

            when {
                fours >= 2 -> threatLevel += 500000
                fours >= 1 && liveThrees >= 1 -> threatLevel += 300000
                liveThrees >= 2 -> threatLevel += 200000
                fours >= 1 -> threatLevel += 50000
                liveThrees >= 1 -> threatLevel += 10000
            }
        }
    }

    return threatLevel
}

private fun evaluatePositionControl(board: Board): Int {
    var score = 0
    val size = board.size
    val center = size / 2

    for (i in 0 until size) {
        for (j in 0 until size) {
            if (board[i][j] == 0) continue
            val dist = abs(i - center) + abs(j - center)
            val controlValue = (size - dist) * 2
            score += if (board[i][j] == -1) controlValue else -controlValue
        }
    }

    return score
}

private fun evaluatePatternsForPlayer(board: Board, x: Int, y: Int, player: Int, directions: List<Direction>): Int {
    var score = 0
    for ((dx, dy) in directions) {
        val (length, _, openEndCount) = analyzeLine(board, x, y, dx, dy, player)
        score += getPatternScore(length, openEndCount)
    }
    return score
}


private fun checkFiveInRow(board: Board, x: Int, y: Int, player: Int, directions: List<Direction>): Boolean {
    for ((dx, dy) in directions) {
        var count = 1
        var nx = x + dx; var ny = y + dy
        while (isValid(nx, ny, board.size) && board[nx][ny] == player) { count++; nx += dx; ny += dy }
        nx = x - dx; ny = y - dy
        while (isValid(nx, ny, board.size) && board[nx][ny] == player) { count++; nx -= dx; ny -= dy }
        if (count >= 5) return true
    }
    return false
}

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
                val prevX = i - dx
                val prevY = j - dy
                if (prevX in 0 until size && prevY in 0 until size && board[prevX][prevY] == player) {
                    continue
                }

                val (length, openEnds, openEndCount) = analyzeLine(board, i, j, dx, dy, player)

                if (attackMode) {
                    when {
                        length == 4 && openEndCount > 0 -> openEnds.forEach { end ->
                            opportunities[end] = opportunities.getOrDefault(end, 0) + 1000
                        }
                        length == 3 && openEndCount == 2 -> openEnds.forEach { end ->
                            opportunities[end] = opportunities.getOrDefault(end, 0) + 500
                        }
                        length == 3 && openEndCount == 1 -> openEnds.forEach { end ->
                            opportunities[end] = opportunities.getOrDefault(end, 0) + 100
                        }
                        length == 2 && openEndCount == 2 -> openEnds.forEach { end ->
                            opportunities[end] = opportunities.getOrDefault(end, 0) + 50
                        }
                    }
                } else {
                    when {
                        length == 4 && openEndCount > 0 -> openEnds.forEach { end ->
                            opportunities[end] = opportunities.getOrDefault(end, 0) + 1000
                        }
                        length == 3 && openEndCount == 2 -> openEnds.forEach { end ->
                            opportunities[end] = opportunities.getOrDefault(end, 0) + 300
                        }
                    }
                }
            }
        }
    }
    return opportunities
}

private fun areEndsOpen(board: Board, x: Int, y: Int, dx: Int, dy: Int): Boolean {
    val size = board.size
    var nx = x + dx
    var ny = y + dy
    if (isValid(nx, ny, size) && board[nx][ny] != 0) return false

    nx = x - dx
    ny = y - dy
    if (isValid(nx, ny, size) && board[nx][ny] != 0) return false

    return true
}

private fun calculatePositionScore(board: Board, x: Int, y: Int, directions: List<Direction>): Int {
    var score = 0
    score += evaluatePlayerScore(board, x, y, -1, directions) * 12
    score += evaluatePlayerScore(board, x, y, 1, directions) * 6

    val size = board.size
    val centerDist = abs(x - size / 2) + abs(y - size / 2)
    score += (size - centerDist) * 4
    score += countAdjacentPieces(board, x, y) * 10
    score += evaluateSpecialPatternPotential(board, x, y, directions) * 6

    return score
}

private fun evaluatePositions(board: Board, positions: Collection<Position>, player: Int): Position {
    var bestScore = Int.MIN_VALUE
    var bestPosition = positions.first()

    for (position in positions) {
        val (x, y) = position
        var score = 0

        score += evaluatePlayerScore(board, x, y, -1, ALL_DIRECTIONS) * 10
        score += evaluatePlayerScore(board, x, y, 1, ALL_DIRECTIONS) * 4

        val size = board.size
        score += (size - abs(x - size / 2) - abs(y - size / 2)) * 3

        score += evaluateSpecialPatternPotential(board, x, y, ALL_DIRECTIONS) * 6

        if (score > bestScore) {
            bestScore = score
            bestPosition = position
        }
    }

    return bestPosition
}

private fun findCriticalThreats(board: Board, directions: List<Direction>): Position {
    val doubleThrees = findDoubleThreats(board, 1, directions)
    if (doubleThrees.isNotEmpty()) {
        return selectBestDefenseMove(board, doubleThrees)
    }

    val fourThreeThreat = findFourThreeThreat(board, directions)
    if (fourThreeThreat.first != -1) {
        return fourThreeThreat
    }

    return Pair(-1, -1)
}

private fun findDoubleThreats(board: Board, player: Int, directions: List<Direction>): Map<Position, Int> {
    val threats = mutableMapOf<Position, Int>()
    val size = board.size

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue

            board[x][y] = player
            var threatCount = 0

            for ((dx, dy) in directions) {
                val (length, _, openEndCount) = analyzeLine(board, x, y, dx, dy, player)
                if (length == 3 && openEndCount == 2) {
                    threatCount++
                }
            }

            board[x][y] = 0

            if (threatCount >= 2) {
                threats[Position(x, y)] = 1200
            }
        }
    }

    return threats
}

private fun findFourThreeThreat(board: Board, directions: List<Direction>): Position {
    val size = board.size

    for (x in 0 until size) {
        for (y in 0 until size) {
            if (board[x][y] != 0) continue

            board[x][y] = 1

            var hasFour = false
            var hasThree = false

            for ((dx, dy) in directions) {
                val (length, _, openEndCount) = analyzeLine(board, x, y, dx, dy, 1)
                if (length >= 4 && openEndCount > 0) hasFour = true
                if (length == 3 && openEndCount == 2) hasThree = true
            }

            board[x][y] = 0

            if (hasFour && hasThree) {
                return Position(x, y)
            }
        }
    }

    return Pair(-1, -1)
}

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
    return findWinningMove(board, -1, ALL_DIRECTIONS).first != -1 ||
            findWinningMove(board, 1, ALL_DIRECTIONS).first != -1
}

private fun isValid(x: Int, y: Int, size: Int): Boolean {
    return x in 0 until size && y in 0 until size
}