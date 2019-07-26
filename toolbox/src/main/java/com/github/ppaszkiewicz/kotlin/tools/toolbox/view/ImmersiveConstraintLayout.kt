package com.github.ppaszkiewicz.kotlin.tools.toolbox.view

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * ConstraintLayout designed as root of activity to keep it full screen and keep display ON.
 */
class ImmersiveConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), View.OnSystemUiVisibilityChangeListener {
    companion object{
        /** Time before auto hiding navigation view. */
        private const val REIMMERSE_TIMER_MS = 5000L
        private const val SAVE_SUPER = "ImmersiveConstraintLayout.save.super"
        private const val SAVE_STATE = "ImmersiveConstraintLayout.save.state"
    }

    /** Runnable*/
    private val reimmerseRunnable = Runnable { if (isNavbarShown) doImmersive() }

    /** Estimated state of navigation bar. */
    var isNavbarShown = true
        private set

    /** If true reimmersion is active, otherwise system ui is unaltered */
    var isReimmersionActive = true

    /** Force layout back into immersed state. **/
    fun doImmersive() {
        if(!isReimmersionActive)
            return
        removeCallbacks(reimmerseRunnable)
        (context as? Activity)?.window?.decorView?.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE
    }

    /** Leave immersive mode and never come back. */
    fun clearImmersive(){
        removeCallbacks(reimmerseRunnable)
        (context as? Activity)?.window?.let {
            it.decorView.setOnSystemUiVisibilityChangeListener(null)
            it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            it.decorView.systemUiVisibility = 0 // this should clear all flags
        }
        isReimmersionActive = false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isNavbarShown && ev.actionMasked == MotionEvent.ACTION_DOWN) {
            doImmersive()
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onAttachedToWindow() {
        if(isReimmersionActive) {
            (context as? Activity)?.window?.let {
                it.decorView.setOnSystemUiVisibilityChangeListener(this)
                it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            doImmersive()
        }
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        (context as? Activity)?.window?.let {
            it.decorView.setOnSystemUiVisibilityChangeListener(null)
            it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        super.onDetachedFromWindow()
    }

    override fun onSystemUiVisibilityChange(visibility: Int) {
        removeCallbacks(reimmerseRunnable)
        if(visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0){
            postDelayed(reimmerseRunnable, REIMMERSE_TIMER_MS)
            isNavbarShown = true   // this is only a prediction, but rather accurate
        }else if (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == View.SYSTEM_UI_FLAG_HIDE_NAVIGATION){
            isNavbarShown = false  // this is only a prediction, but rather accurate
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        return Bundle().apply {
            putParcelable(SAVE_SUPER, super.onSaveInstanceState())
            putBoolean(SAVE_STATE, isReimmersionActive)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        (state as? Bundle)?.apply {
            isReimmersionActive = getBoolean(SAVE_STATE, true)
            super.onRestoreInstanceState(getParcelable(SAVE_SUPER))
        }
    }
}