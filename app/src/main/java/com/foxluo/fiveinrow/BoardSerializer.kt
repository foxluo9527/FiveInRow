package com.foxluo.fiveinrow

import java.io.ByteArrayOutputStream

object BoardSerializer {
    fun serializeBoard(board: Array<IntArray>): ByteArray {
        require(board.size == 13 && board.all { it.size == 13 }) { "Board must be 13x13" }
        
        val output = ByteArrayOutputStream()
        var currentByte = 0
        var bitPosition = 0
        
        for (i in 0 until 13) {
            for (j in 0 until 13) {
                val value = board[i][j]
                val bits = when (value) {
                    -1 -> 0b00
                    0 -> 0b01
                    1 -> 0b10
                    else -> throw IllegalArgumentException("Invalid board value: $value")
                }
                
                currentByte = (currentByte shl 2) or bits
                bitPosition += 2
                
                if (bitPosition >= 8) {
                    output.write(currentByte)
                    currentByte = 0
                    bitPosition = 0
                }
            }
        }
        
        if (bitPosition > 0) {
            currentByte = currentByte shl (8 - bitPosition)
            output.write(currentByte)
        }
        
        return output.toByteArray()
    }
    
    fun deserializeBoard(data: ByteArray): Array<IntArray> {
        require(data.size == 43) { "Invalid data length for 13x13 board" }
        
        val board = Array(13) { IntArray(13) }
        var byteIndex = 0
        var bitPosition = 0
        
        for (i in 0 until 13) {
            for (j in 0 until 13) {
                if (bitPosition >= 8) {
                    byteIndex++
                    bitPosition = 0
                }
                
                val currentByte = data[byteIndex].toInt() and 0xFF
                val bits = (currentByte shr (8 - bitPosition - 2)) and 0b11
                bitPosition += 2
                
                board[i][j] = when (bits) {
                    0b00 -> -1
                    0b01 -> 0
                    0b10 -> 1
                    else -> 0
                }
            }
        }
        
        return board
    }
}