package com.foxluo.fiveinrow

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import com.blankj.utilcode.util.SPUtils
import com.blankj.utilcode.util.SizeUtils
import com.blankj.utilcode.util.TimeUtils
import com.blankj.utilcode.util.ToastUtils
import com.foxluo.fiveinrow.databinding.DialogCacheListBinding
import com.foxluo.fiveinrow.databinding.DialogEasyModeBinding
import com.foxluo.fiveinrow.databinding.DialogGameOverBinding
import com.foxluo.fiveinrow.databinding.DialogNewGameBinding
import com.foxluo.fiveinrow.databinding.DialogSettingBinding

object DialogCenter {
    fun Activity.showConfirmDialog(
        content: String,
        positive: () -> Unit
    ): AlertDialog {
        val binding = DialogGameOverBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this, R.style.DialogAnim)
            .setView(binding.root)
            .setCancelable(false)
            .show()
        binding.title.setText(R.string.prompt)
        binding.content.text = content
        binding.negative.setText(R.string._cancel)
        binding.content.visibility = View.VISIBLE
        binding.positive.setText(R.string.sure)
        binding.negative.setOnClickListener {
            dialog.dismiss()
        }
        binding.positive.setOnClickListener {
            positive()
            dialog.dismiss()
        }
        return dialog
    }

    fun Activity.gameOverDialog(
        content: String,
        negative: () -> Unit,
        positive: () -> Unit
    ): AlertDialog.Builder {
        val binding = DialogGameOverBinding.inflate(layoutInflater)
        binding.title.setText(R.string.game_over)
        binding.content.text = content
        binding.negative.setText(R.string._cancel)
        binding.content.visibility = View.VISIBLE
        binding.positive.setText(R.string.continue_game)
        binding.negative.setOnClickListener {
            negative()
        }
        binding.positive.setOnClickListener {
            positive()
        }
        val builder = AlertDialog.Builder(this, R.style.DialogAnim)
            .setView(binding.root)
            .setCancelable(false)
        return builder
    }

    fun Activity.inputTitleDialog(
        title: String = TimeUtils.getNowString(),
        hint: String = "请输入存档标题",
        negative: () -> Unit,
        positive: (String) -> Unit
    ): AlertDialog.Builder {
        val binding = DialogGameOverBinding.inflate(layoutInflater)
        binding.title.setText(R.string.save_edit)
        binding.input.hint = hint
        binding.input.setText(title)
        binding.negative.setText(R.string._cancel)
        binding.input.visibility = View.VISIBLE
        binding.positive.setText(R.string.sure)
        binding.negative.setOnClickListener {
            negative()
        }
        binding.positive.setOnClickListener {
            positive(binding.input.text.toString())
        }
        val builder = AlertDialog.Builder(this, R.style.DialogAnim)
            .setView(binding.root)
            .setCancelable(false)
        return builder
    }

    fun Activity.cacheListDialog(
        adapter: BoardCacheAdapter,
        delete: (List<BoardCache>) -> Unit
    ): AlertDialog.Builder {
        val binding = DialogCacheListBinding.inflate(layoutInflater)
        binding.rvList.adapter = adapter
        binding.delete.setOnClickListener {
            adapter.list.filter { it.selected }.let {
                if (it.isEmpty()) {
                    ToastUtils.showShort("请先勾选需要删除的存档")
                } else {
                    delete.invoke(it)
                }
            }
        }
        val builder = AlertDialog.Builder(this, R.style.DialogAnim)
            .setView(binding.root)
            .setCancelable(true)
        return builder
    }

    fun Activity.newGameDialog(
        start: (Boolean) -> Unit
    ): AlertDialog.Builder {
        val binding = DialogNewGameBinding.inflate(layoutInflater)
        binding.pvp.setOnClickListener {
            start(true)
        }
        binding.pve.setOnClickListener {
            start(false)
        }
        val builder = AlertDialog.Builder(this, R.style.DialogAnim)
            .setView(binding.root)
            .setCancelable(true)
        return builder
    }

    fun Activity.easyModeDialog(choose: (Int) -> Unit): AlertDialog.Builder {
        val binding = DialogEasyModeBinding.inflate(layoutInflater)
        binding.easy0.setOnClickListener {
            choose.invoke(0)
        }
        binding.easy1.setOnClickListener {
            choose.invoke(1)
        }
        binding.easy2.setOnClickListener {
            choose.invoke(2)
        }
        binding.easy3.setOnClickListener {
            choose.invoke(3)
        }
        val builder = AlertDialog.Builder(this, R.style.DialogAnim)
            .setView(binding.root)
            .setCancelable(true)
        return builder
    }

    fun Activity.settingDialog(gameView: FiveInRowGameView): AlertDialog.Builder {
        val binding = DialogSettingBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(this, R.style.DialogAnim)
            .setView(binding.root)
            .setCancelable(true)
        binding.paddingSeek.setProgress(SizeUtils.px2dp(gameView.padding.toFloat()), false)
        binding.paddingSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                gameView.padding = SizeUtils.dp2px(progress.toFloat())
            }
        })
        binding.borderSeek.setProgress(gameView.gridLineWidth.toInt(), false)
        binding.borderSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                gameView.gridLineWidth = progress.toFloat()
            }
        })
        val difficultyItems = arrayOf("弱智", "有点傻", "正常", "会玩", "专家")
        var easyDialog: AlertDialog? = null
        val aiEasyBuilder = easyModeDialog { easy ->
            SPUtils.getInstance().put("easy_mode", easy)
            easyDialog?.dismiss()
            binding.easyMode.text = difficultyItems[easy]
        }
        binding.easy.setOnClickListener {
            if (easyDialog == null) {
                easyDialog = aiEasyBuilder.show()
            } else {
                easyDialog.show()
            }
        }
        binding.easyMode.text = difficultyItems[SPUtils.getInstance().getInt("easy_mode", 1)]
        val colorViews = arrayOf(
            binding.black, binding.gray, binding.blue, binding.yellow,
            binding.red, binding.green, binding.cyan, binding.magenta
        )
        val colors = arrayOf(
            Color.BLACK, Color.GRAY, Color.BLUE, Color.YELLOW,
            Color.RED, Color.GREEN, Color.CYAN, Color.MAGENTA
        )
        val click = { v: View ->
            colorViews.forEach { it.isSelected = false }
            val index = colorViews.indexOf(v)
            if (index >= 0) {
                gameView.gridLineColor = colors[index]
                v.isSelected = true
            }
        }
        binding.black.setOnClickListener(click)
        binding.gray.setOnClickListener(click)
        binding.blue.setOnClickListener(click)
        binding.yellow.setOnClickListener(click)
        binding.red.setOnClickListener(click)
        binding.green.setOnClickListener(click)
        binding.cyan.setOnClickListener(click)
        binding.magenta.setOnClickListener(click)
        val currentColorIndex = colors.indexOf(gameView.gridLineColor)
        if (currentColorIndex >= 0) {
            colorViews[currentColorIndex].isSelected = true
        }
        return builder
    }
}