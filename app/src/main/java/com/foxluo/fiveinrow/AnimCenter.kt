package com.foxluo.fiveinrow

import android.animation.ValueAnimator
import android.animation.ValueAnimator.INFINITE
import android.animation.ValueAnimator.RESTART
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnCancel

object AnimCenter {
    fun getConfirmAnim(updateAnim: ((value: Int) -> Unit), cancelAnim: () -> Unit): ValueAnimator =
        ValueAnimator.ofInt(3, 0).apply {
            setDuration(3000L)
            interpolator = LinearInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Int
                updateAnim(value)
                if (value == 0) cancel()
            }
            doOnCancel {
                cancelAnim()
            }
        }

    fun getRepeatWaitAnim(
        updateAnim: ((value: Int) -> Unit),
        cancelAnim: (() -> Unit)? = null
    ): ValueAnimator =
        ValueAnimator.ofInt(1, 4).apply {
            setDuration(1500L)
            repeatCount = INFINITE
            repeatMode = RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Int
                updateAnim(value)
            }
            doOnCancel {
                cancelAnim?.invoke()
            }
        }


}