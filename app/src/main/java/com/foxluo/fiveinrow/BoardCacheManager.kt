package com.foxluo.fiveinrow

import androidx.lifecycle.MutableLiveData
import com.blankj.utilcode.util.GsonUtils

import com.blankj.utilcode.util.SPUtils
import com.blankj.utilcode.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object BoardCacheManager {
    val cacheList by lazy {
        MutableLiveData<List<BoardCache>>()
    }

    fun saveGame(
        id: Long,
        title: String = TimeUtils.getNowString(),
        data: ByteArray
    ): Boolean {
        return runBlocking(Dispatchers.IO) {
            val cache =
                BoardCache(id, title, System.currentTimeMillis(), BoardCache.bytesToDataStr(data))
            runCatching {
                SPUtils.getInstance("game-cache").put(id.toString(), GsonUtils.toJson(cache))
                loadGameData()
            }.getOrNull() != null
        }
    }

    fun removeCache(caches: List<BoardCache>) {
        runBlocking(Dispatchers.IO) {
            caches.forEach {
                SPUtils.getInstance("game-cache").remove(it.id.toString())
            }
            loadGameData()
        }
    }

    fun editCache(cache: BoardCache) {
        runBlocking(Dispatchers.IO) {
            SPUtils.getInstance("game-cache").put(cache.id.toString(), GsonUtils.toJson(cache))
            loadGameData()
        }
    }

    fun loadGameData() {
        runBlocking(Dispatchers.IO) {
            val dataList = SPUtils.getInstance("game-cache").all.map {
                val value = it.value as String
                GsonUtils.fromJson<BoardCache>(value, BoardCache::class.java).apply {
                    selected = false
                }
            }.sortedByDescending {
                it.time
            }
            cacheList.postValue(dataList)
        }
    }
}