package com.foxluo.fiveinrow

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.TimeUtils
import com.foxluo.fiveinrow.databinding.ItemBoardCacheBinding

class BoardCacheAdapter : RecyclerView.Adapter<CacheViewHolder>() {
    var list: List<BoardCache> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var clickCallback: ((BoardCache) -> Unit)? = null
    var longClickCallback: ((BoardCache) -> Unit)? = null
    override fun onCreateViewHolder(
        p0: ViewGroup,
        p1: Int
    ): CacheViewHolder {
        return CacheViewHolder(
            ItemBoardCacheBinding.inflate(
                LayoutInflater.from(p0.context),
                p0,
                false
            )
        )
    }

    override fun onBindViewHolder(p0: CacheViewHolder, p1: Int) {
        p0.loadCache(list[p1], click = {
            clickCallback?.invoke(list[p1])
        }, longClickListener = {
            if (longClickCallback == null) {
                false
            } else {
                longClickCallback?.invoke(list[p1])
                true
            }
        })
    }

    override fun getItemCount(): Int {
        return list.size
    }

}

class CacheViewHolder(val binding: ItemBoardCacheBinding) : RecyclerView.ViewHolder(binding.root) {
    fun loadCache(
        cache: BoardCache,
        click: View.OnClickListener,
        longClickListener: View.OnLongClickListener
    ) {
        binding.title.text = cache.title
        binding.time.text = TimeUtils.getFriendlyTimeSpanByNow(cache.time)
        binding.continueGame.setOnClickListener(click)
        binding.selected.setOnCheckedChangeListener { _, checked ->
            cache.selected = checked
        }
        binding.player.text = if (cache.aiPlayer) "AI对战" else "玩家对战"
        binding.root.setOnLongClickListener(longClickListener)
    }
}