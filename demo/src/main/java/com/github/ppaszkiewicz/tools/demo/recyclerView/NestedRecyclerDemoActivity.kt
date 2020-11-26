package com.github.ppaszkiewicz.tools.demo.recyclerView

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ppaszkiewicz.tools.demo.R
import com.github.ppaszkiewicz.tools.demo.databinding.ActivityRecyclerBinding
import com.github.ppaszkiewicz.tools.toolbox.delegate.viewBinding
import com.github.ppaszkiewicz.tools.toolbox.recyclerView.NestedWrapLayoutManager

/**
 * Showcases [NestedWrapLayoutManager].
 *
 * It measured only one item and multiplies its size to determine total recyclerview size and actually
 * recycle the views.
 *
 * This does not happen with [LinearLayoutManager] as it does not assume uniform view size so it
 * abandons recycling and binds everything at once.
 * */
class NestedRecyclerDemoActivity : AppCompatActivity() {
    val binding by viewBinding<ActivityRecyclerBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with(binding) {
            btnUseNested.setOnClickListener {
                injectLayoutManager(NestedWrapLayoutManager(layNestedScroll))
            }
            btnUseLinear.setOnClickListener {
                injectLayoutManager(LinearLayoutManager(this@NestedRecyclerDemoActivity))
            }
            btnScrollTo.setOnClickListener {
                recyclerView.scrollToPosition(500)
            }
            btnScrollToSmooth.setOnClickListener {
                recyclerView.smoothScrollToPosition(500)
            }
        }
    }

    private fun injectLayoutManager(layoutManager: RecyclerView.LayoutManager) {
        with(binding) {
            // clear out any existing components
            recyclerView.layoutManager = null
            recyclerView.adapter = null
            recyclerView.recycledViewPool.clear()
            txtNestedText2.isVisible = true // show a view below recycler

            // assign new adapter and layout manager
            recyclerView.adapter = NumberAdapter(1000)
            recyclerView.layoutManager = layoutManager

            // now measure the time
            val start = System.currentTimeMillis()
            recyclerView.post {
                val time = System.currentTimeMillis() - start
                txtNestedText.text =
                    "First layout of ${layoutManager::class.java.simpleName} took ${time}ms." +
                            "\nRecyclerview created ${recyclerView.childCount} children"
            }
        }
    }
}

class NumberAdapter(val numberCount: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var createdViewHolders = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v =
            LayoutInflater.from(parent.context).inflate(R.layout.item_recycler_text, parent, false)
        v.tag = createdViewHolders++
        return object : RecyclerView.ViewHolder(v) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder.itemView as TextView).text = "$position in holder ${holder.itemView.tag}"
    }

    override fun getItemCount() = numberCount
}