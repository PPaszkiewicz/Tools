package com.github.ppaszkiewicz.tools.demo.views

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.ppaszkiewicz.tools.demo.databinding.ActivityStableTextviewBinding
import com.github.ppaszkiewicz.tools.toolbox.delegate.viewBinding

class StableTextViewActivity : AppCompatActivity() {
    val v by viewBinding<ActivityStableTextviewBinding>()
    private var colorAnim : Animator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v.root.onLayout = { _ ->
            colorAnim?.cancel()
            v.txtStableDetail.setBackgroundColor(Color.LTGRAY)
            colorAnim = ValueAnimator.ofArgb(Color.LTGRAY, Color.TRANSPARENT).apply {
                addUpdateListener {
                    duration = 150
                    v.txtStableDetail.setBackgroundColor(it.animatedValue as Int)
                }
                start()
            }
        }

        v.btnRegularTextView.setOnClickListener {
            v.txtRegularTextView.text = v.txtRegularTextView.text.reversed()
        }

        v.btnStableTextView.setOnClickListener {
            v.txtStableTextView.text = v.txtStableTextView.text.reversed()
        }
    }
}

class LayoutListenerConstraintLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr){
    var onLayout : ((LayoutListenerConstraintLayout) -> Unit)? = null

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        onLayout?.invoke(this)
    }
}