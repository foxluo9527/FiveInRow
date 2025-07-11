package com.foxluo.fiveinrow

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.blankj.utilcode.util.SPUtils
import com.blankj.utilcode.util.ToastUtils
import com.foxluo.fiveinrow.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val confirmListener by lazy {
        object : GameCallback {
            override fun onNeedConfirmMove(row: Int, col: Int, player: Int) {
                binding.cancel.isEnabled = true
                binding.sure.isEnabled = true
                binding.confirm.isEnabled = false
            }

            override fun gameOver(blackWin: Boolean) {
                ToastUtils.showLong("${if (blackWin) "黑" else "白"}方胜")
                binding.gameView.postDelayed({
                    binding.gameView.resetGame()
                }, 3000L)
            }
        }
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
            binding.confirm.isEnabled = true
            binding.cancel.isEnabled = false
            binding.sure.isEnabled = false
        }
        binding.sure.setOnClickListener {
            binding.gameView.confirmMove()
            binding.confirm.isEnabled = true
            binding.cancel.isEnabled = false
            binding.sure.isEnabled = false
        }
        binding.confirm.setOnCheckedChangeListener { _, confirm ->
            SPUtils.getInstance().put("confirm_setting", confirm)
            if (!confirm) {
                binding.gameView.setNeedConfirm(true)
            } else {
                binding.gameView.setNeedConfirm(false)
            }
        }
        binding.gameView.setGameListener(confirmListener)
        binding.confirm.isChecked = SPUtils.getInstance().getBoolean("confirm_setting", false)
    }
}