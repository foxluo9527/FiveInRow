package com.foxluo.fiveinrow

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.blankj.utilcode.util.SPUtils
import com.blankj.utilcode.util.ToastUtils
import com.foxluo.fiveinrow.ConfirmAnim.getConfirmAnim
import com.foxluo.fiveinrow.DialogCenter.cacheListDialog
import com.foxluo.fiveinrow.DialogCenter.gameOverDialog
import com.foxluo.fiveinrow.DialogCenter.inputTitleDialog
import com.foxluo.fiveinrow.DialogCenter.settingDialog
import com.foxluo.fiveinrow.DialogCenter.showConfirmDialog
import com.foxluo.fiveinrow.databinding.ActivityMainBinding

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity(), GameCallback {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var gameOverDialog: AlertDialog? = null

    private var inputDialog: AlertDialog? = null

    private var cacheListDialog: AlertDialog? = null

    private val cacheAdapter by lazy {
        BoardCacheAdapter().apply {
            clickCallback = {
                binding.gameView.initGameCache(it)
                cacheListDialog?.dismiss()
            }
            longClickCallback = { cache ->
                inputDialog = inputTitleDialog(
                    title = cache.title,
                    negative = { inputDialog?.dismiss() },
                    positive = {
                        inputDialog?.dismiss()
                        BoardCacheManager.editCache(cache.copy(title = it))
                    }).show()
            }
        }
    }

    private val confirmAnim by lazy {
        getConfirmAnim(updateAnim = { value ->
            binding.sure.text = "确认(${value}S)"
            if (value == 0) binding.gameView.confirmMove()
        }, cancelAnim = {
            binding.sure.text = "确认"
            binding.confirm.isEnabled = true
            binding.cancel.isEnabled = false
            binding.sure.isEnabled = false
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.reset.setOnClickListener {
            binding.gameView.resetGame()
        }
        binding.cancel.setOnClickListener {
            binding.gameView.cancelMove()
            confirmAnim.cancel()
        }
        binding.sure.setOnClickListener {
            binding.gameView.confirmMove()
            confirmAnim.cancel()
        }
        binding.confirm.setOnCheckedChangeListener { _, confirm ->
            SPUtils.getInstance().put("confirm_setting", confirm)
            if (!confirm) {
                binding.gameView.setNeedConfirm(true)
            } else {
                binding.gameView.setNeedConfirm(false)
            }
        }
        binding.save.setOnClickListener {
            inputDialog = inputTitleDialog(negative = {
                inputDialog?.dismiss()
            }, positive = { title ->
                inputDialog?.dismiss()
                BoardCacheManager.saveGame(
                    binding.gameView.gameStartTime,
                    title,
                    binding.gameView.getGameData()
                )
            }).show()
        }
        binding.cache.setOnClickListener {
            if (cacheAdapter.list.isEmpty()) {
                ToastUtils.showShort("暂无存档，请先存档！")
                return@setOnClickListener
            }
            cacheListDialog = cacheListDialog(cacheAdapter, delete = {
                showConfirmDialog("确认删除所选存档？") {
                    val allDelete = it.size == cacheAdapter.list.size
                    BoardCacheManager.removeCache(it)
                    if (allDelete) cacheListDialog?.dismiss()
                }
            }).show()
        }
        binding.setting.setOnClickListener {
            settingDialog(binding.gameView).show()
        }
        binding.gameView.setGameListener(this)
        binding.confirm.isChecked = SPUtils.getInstance().getBoolean("confirm_setting", false)
        BoardCacheManager.cacheList.observe(this) {
            cacheAdapter.list = it
        }
        BoardCacheManager.loadGameData()
    }

    override fun onNeedConfirmMove(row: Int, col: Int, player: Int) {
        binding.cancel.isEnabled = true
        binding.sure.isEnabled = true
        binding.confirm.isEnabled = false
        confirmAnim.start()
    }

    override fun gameOver(blackWin: Boolean) {
        binding.save.isEnabled = false
        gameOverDialog = gameOverDialog("${if (blackWin) "黑" else "白"}方胜", negative = {
            gameOverDialog?.dismiss()
        }, positive = {
            gameOverDialog?.dismiss()
            binding.gameView.resetGame()
        }).show()
    }

    override fun gameStart() {
        binding.save.isEnabled = true
    }
}