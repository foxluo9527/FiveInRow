package com.foxluo.fiveinrow

import android.util.Base64

import java.io.Serializable

data class BoardCache(
    val id: Long,
    val title: String,
    val time: Long,
    val data: String,
    val aiPlayer: Boolean,
    var selected: Boolean = false
) : Serializable {
    companion object {
        fun dataToByte(data: String): ByteArray {
            return Base64.decode(data, Base64.NO_WRAP)
        }

        fun bytesToDataStr(bytes: ByteArray): String {
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
}
