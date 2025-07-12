package com.foxluo.fiveinrow

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.SPUtils
import com.blankj.utilcode.util.ToastUtils
import com.foxluo.fiveinrow.AnimCenter.getConfirmAnim
import com.foxluo.fiveinrow.AnimCenter.getRepeatWaitAnim
import com.foxluo.fiveinrow.DialogCenter.cacheListDialog
import com.foxluo.fiveinrow.DialogCenter.gameOverDialog
import com.foxluo.fiveinrow.DialogCenter.inputTitleDialog
import com.foxluo.fiveinrow.DialogCenter.newGameDialog
import com.foxluo.fiveinrow.DialogCenter.settingDialog
import com.foxluo.fiveinrow.DialogCenter.showConfirmDialog
import com.foxluo.fiveinrow.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity(), GameCallback {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var gameOverDialog: AlertDialog? = null

    private var inputDialog: AlertDialog? = null

    private var cacheListDialog: AlertDialog? = null

    private var newGameDialog: AlertDialog? = null

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

    private val waitAIAnim by lazy {
        getRepeatWaitAnim(updateAnim = { value ->
            var waitStr = "AI推算中"
            for (i in 0 until value) {
                waitStr += "."
            }
            binding.title.text = waitStr
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
            newGameDialog = newGameDialog {
                binding.gameView.resetGame(!it)
                newGameDialog?.dismiss()
            }.show()
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
                val saveResult = BoardCacheManager.saveGame(
                    binding.gameView.gameStartTime,
                    title,
                    binding.gameView.getGameDataCache(),
                    binding.gameView.playWithComputer
                )
                ToastUtils.showShort(if (saveResult) "存档成功" else "存档失败")
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

    override fun onComputerConfirm() {
        lifecycleScope.launch {
            binding.title.text = "AI推算中"
            waitAIAnim.start()
            val originalGameData = binding.gameView.getGameData()
            // 创建棋盘深拷贝用于AI计算，避免修改原始数据导致UI闪烁
            val gameData = originalGameData.map { it.clone() }.toTypedArray()
            val (bestRow, bestCol) = AiPlayer.calculateNextMove(gameData)
            waitAIAnim.cancel()
            binding.title.text = "请您出棋"
            binding.gameView.computerConfirm(bestRow, bestCol)
        }
    }

    override fun onStep(player: Int) {
        if (!(binding.gameView.playWithComputer)) {
            binding.title.text = "请${if (player == 1) "黑" else "白"}方出棋"
        } else {
            if (player == 1) {
                binding.title.text = "请您出棋"
            } else {
                onComputerConfirm()
            }
        }
    }

    override fun gameOver(blackWin: Boolean) {
        binding.save.isEnabled = false
        val win = if (blackWin) "黑" else "白"
        gameOverDialog = gameOverDialog("${win}方胜", negative = {
            gameOverDialog?.dismiss()
        }, positive = {
            gameOverDialog?.dismiss()
            binding.gameView.resetGame()
        }).show()
        binding.title.text = "${win}方获胜"
    }

    override fun gameStart() {
        binding.save.isEnabled = true
    }

    override fun gameReady() {
        if (!(binding.gameView.playWithComputer)) {
            binding.title.text = "请黑方出棋"
        } else {
            binding.title.text = "请您出棋"
        }
    }
}