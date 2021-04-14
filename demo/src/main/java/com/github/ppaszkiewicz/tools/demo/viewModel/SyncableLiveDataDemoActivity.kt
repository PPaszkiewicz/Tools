package com.github.ppaszkiewicz.tools.demo.viewModel

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.github.ppaszkiewicz.tools.demo.databinding.ActivityButtonsBinding
import com.github.ppaszkiewicz.tools.toolbox.delegate.viewBinding
import com.github.ppaszkiewicz.tools.toolbox.liveData.LiveDataSync
import com.github.ppaszkiewicz.tools.toolbox.liveData.createFrom
import com.github.ppaszkiewicz.tools.toolbox.liveData.syncedOn


class SyncableLiveDataDemoActivity : AppCompatActivity() {
    companion object {
        const val TAG = "SyncableLiveDataDemo"
    }

    val sourceLiveData = MutableLiveData(0)

    val h = Handler(Looper.getMainLooper())

    val emitRunnable = object : Runnable {
        override fun run() {
            h.removeCallbacks(this)
            incrementSource()
            h.postDelayed(this, 500)
        }
    }

    val sync = LiveDataSync()

    val observer1 = sync.createFrom(sourceLiveData)
    val observer2 = sourceLiveData.syncedOn(sync)
    val binding by viewBinding<ActivityButtonsBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with(binding) {
            // init buttons
            button1.text = "Start emitting"
            button2.text = "Stop emitting"

            button3.text = "pause observers"
            button4.text = "resume observers"
            button5.text = "emit once"
            button6.text = "emit synced"

            title = "Synced livedata test"
            textView0.text =
                "Synced livedatas that can suppress emitting values to ensure other livedatas" +
                        "are also updated"
            textView3.text = "sync disabled"

            button1.setOnClickListener {
                h.postDelayed(emitRunnable, 5000)
                Log.d(TAG, "started runnable")
            }

            button2.setOnClickListener {
                h.removeCallbacks(emitRunnable)
                Log.d(TAG, "stopped runnable")
            }

            button3.setOnClickListener {
                sync.pause()
                textView3.text = "updates paused"
            }

            button4.setOnClickListener {
                sync.resume()
                textView3.text = "updates resumed"
            }

            button5.setOnClickListener {
                incrementSource()
            }

            button6.setOnClickListener {
                if (sync.isPaused) {
                    Toast.makeText(
                        this@SyncableLiveDataDemoActivity,
                        "unpause sync first",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    sync.runWithSync {
                        incrementSource()
                        textView3.text = "incremented in sync"
                    }
                }
            }

            observer1.observe(this@SyncableLiveDataDemoActivity, Observer {
                textView1.text = "obs1: received $it, other has ${observer2.value}"
            })

            observer2.observe(this@SyncableLiveDataDemoActivity, Observer {
                textView2.text = "obs2: received $it, other has ${observer1.value}"
            })
        }
    }

    private fun incrementSource() {
        val v = sourceLiveData.value!! + 1
        Log.d(TAG, "emitting $v")
        sourceLiveData.value = v
        binding.textView00.text = "Source value: $v"
    }

    override fun onDestroy() {
        super.onDestroy()
        sync.destroy()
    }
}