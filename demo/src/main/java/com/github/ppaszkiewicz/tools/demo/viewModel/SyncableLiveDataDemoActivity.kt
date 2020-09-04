package com.github.ppaszkiewicz.tools.demo.viewModel

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.github.ppaszkiewicz.tools.demo.R
import com.github.ppaszkiewicz.kotlin.tools.services.DirectBindService
import com.github.ppaszkiewicz.tools.toolbox.liveData.LiveDataSync
import com.github.ppaszkiewicz.tools.toolbox.liveData.createFrom
import com.github.ppaszkiewicz.tools.toolbox.liveData.syncedOn
import kotlinx.android.synthetic.main.activity_buttons.*


class SyncableLiveDataDemoActivity : AppCompatActivity(R.layout.activity_buttons){
    companion object{
        const val TAG = "SyncableLiveDataDemo"
    }

    val sourceLiveData = MutableLiveData(0)

    val h = Handler()

    val emitRunnable = object : Runnable{
        override fun run() {
            h.removeCallbacks(this)
            incrementSource()
            h.postDelayed(this, 500)
        }
    }

    val sync = LiveDataSync()

    val observer1 = sync.createFrom(sourceLiveData)
    val observer2 = sourceLiveData.syncedOn(sync)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // init buttons
        button1.text = "Start emitting"
        button2.text = "Stop emitting"

        button3.text = "pause observers"
        button4.text = "resume observers"
        button5.text = "emit once"
        button6.text = "emit synced"

        title = "Synced livedata test"
        textView0.text = "Synced livedatas that can suppress emitting values to ensure other livedatas" +
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
            if(sync.isPaused){
                Toast.makeText(this, "unpause sync first", Toast.LENGTH_SHORT).show()
            }else{
                sync.runWithSync {
                    incrementSource()
                    textView3.text = "incremented in sync"
                }
            }
        }

        observer1.observe(this, Observer {
            textView1.text = "obs1: received $it, other has ${observer2.value}"
        })

        observer2.observe(this, Observer {
            textView2.text = "obs2: received $it, other has ${observer1.value}"
        })
    }

    private fun incrementSource(){
        val v = sourceLiveData.value!! + 1
        Log.d(TAG, "emitting $v")
        sourceLiveData.value = v
        textView00.text = "Source value: $v"
    }

    override fun onDestroy() {
        super.onDestroy()
        sync.destroy()
    }
}

class TestService : com.github.ppaszkiewicz.kotlin.tools.services.DirectBindService.Impl(){
    companion object{
        val connectionFactory = com.github.ppaszkiewicz.kotlin.tools.services.DirectBindService.ConnectionFactory<TestService>()
    }

    fun foo() = "TestService is alive!"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TestService", "STARTED")
//        GlobalScope.launch(Dispatchers.Main){
//            delay(3000)
//            stopSelf()
//        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d("TestService", "BINDING $intent")
        return super.onBind(intent)
    }

    override fun onCreate() {
        Log.d("TestService", "CREATED")
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("TestService", "DESTROYED")
    }
}