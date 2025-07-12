package com.foxluo.fiveinrow

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnCancel

object ConfirmAnim {
    fun getConfirmAnim(updateAnim: ((value: Int) -> Unit), cancelAnim: () -> Unit): ValueAnimator =
        ValueAnimator.ofInt(3, 0).apply {
            setDuration(4000L)
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
}