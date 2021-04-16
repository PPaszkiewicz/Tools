package com.github.ppaszkiewicz.tools.demo.recyclerView

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
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
    private var adapter : NumberAdapter? = null

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
            // big pool so mass-removal doesn't cause too many creations
            recyclerView.recycledViewPool.setMaxRecycledViews(0, 50)
            txtNestedText2.isVisible = true // show a view below recycler

            // assign new adapter and layout manager
            adapter = NumberAdapter(1000)
            recyclerView.adapter = adapter
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

    // some structure change showcases, note: they're not checking for items size so it may crash
    // on extensive use
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when(keyCode){
            KeyEvent.KEYCODE_Q -> { // test: small move ( in screen)
                adapter?.let{
                    val i = it.items.removeAt(0)
                    it.items.add(2, i)
                    it.notifyItemMoved(0, 2)
                }
                true
            }
            KeyEvent.KEYCODE_W -> { // test: large move (out of screen)
                adapter?.let{
                    val i = it.items.removeAt(0)
                    it.items.add(20, i)
                    it.notifyItemMoved(0, 19)
                }
                true
            }
            KeyEvent.KEYCODE_R -> { // test: deletion
                adapter?.let{
                    val i = it.items.removeAt(1)
                    it.notifyItemRemoved(1)
                }
                true
            }
            KeyEvent.KEYCODE_F -> { // test: deletion
                adapter?.let{
                    it.items.subList(1,6).clear()
                    it.notifyItemRangeRemoved(1, 5)
                }
                true
            }
            KeyEvent.KEYCODE_G -> { // test: large add (to move items into screen from top)
                adapter?.let{
                    val insertCount = 16
                    it.items.addAll(2, IntArray(insertCount){ _ -> it.items.count()}.toList())
                    it.notifyItemRangeInserted(2, insertCount)
                }
                true
            }
            KeyEvent.KEYCODE_T -> { // test: insertion
                adapter?.let{
                    it.items.add(2, it.items.count())
                    it.notifyItemInserted(2)
                }
                true
            }
            KeyEvent.KEYCODE_A -> {
                adapter?.notifyItemChanged(2, Color.RED)
                true
            }
            KeyEvent.KEYCODE_Z -> { // complex modification: same as the one presented in tests
                adapter?.let{
                    it.items.add(1,  it.items.count())
                    val i = it.items.removeAt(1)
                    it.items.add(10, i)
                    it.items.removeAt(0)
                    it.items.removeAt(0)
                    it.notifyItemInserted(1)
                    it.notifyItemMoved(1, 10)
                    it.notifyItemRangeRemoved(0, 2)
                }
                true
            }
            KeyEvent.KEYCODE_X -> {
                binding.recyclerView.scrollToPosition(0)
                true
            }
            KeyEvent.KEYCODE_C -> {
                binding.recyclerView.scrollToPosition(adapter?.itemCount ?: 0)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

class NumberAdapter(val numberCount: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var createdViewHolders = 0
    var items = mutableListOf(numberCount).apply { repeat(numberCount){ add(it, it) } }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v =
            LayoutInflater.from(parent.context).inflate(R.layout.item_recycler_text, parent, false)
        v.tag = createdViewHolders++
        return object : RecyclerView.ViewHolder(v) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder.itemView.background = null
        (holder.itemView as TextView).text = "Pos: $position, item: ${items[position]} holder: ${holder.itemView.tag}"
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if(payloads.isNotEmpty()){
            holder.itemView.setBackgroundColor(Color.RED)
        }else super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount() = items.size
}