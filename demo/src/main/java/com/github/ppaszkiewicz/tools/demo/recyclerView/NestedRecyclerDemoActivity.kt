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
import com.github.ppaszkiewicz.tools.toolbox.recyclerView.NestedWrapLayoutManager
import kotlinx.android.synthetic.main.activity_recycler.*

/**
 * Showcases [NestedWrapLayoutManager].
 *
 * It measured only one item and multiplies its size to determine total recyclerview size and actually
 * recycle the views.
 *
 * This does not happen with [LinearLayoutManager] as it does not assume uniform view size so it
 * abandons recycling and binds everything at once.
 * */
class NestedRecyclerDemoActivity : AppCompatActivity(R.layout.activity_recycler) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        btnUseNested.setOnClickListener {
            injectLayoutManager(NestedWrapLayoutManager(layNestedScroll))
        }
        btnUseLinear.setOnClickListener {
            injectLayoutManager(LinearLayoutManager(this))
        }
    }

    //todo: scrollto/smoothscrollto is not supported (yet)
//    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
//        return when(event.keyCode){
//            KeyEvent.KEYCODE_Q -> {
//                recyclerView.scrollToPosition(0)
//                true
//            }
//            KeyEvent.KEYCODE_A -> {
//                recyclerView.scrollToPosition((recyclerView.adapter?.itemCount ?: 1) -1)
//                true
//            }
//            else -> super.dispatchKeyEvent(event)
//        }
//    }

    private fun injectLayoutManager(layoutManager: RecyclerView.LayoutManager) {
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