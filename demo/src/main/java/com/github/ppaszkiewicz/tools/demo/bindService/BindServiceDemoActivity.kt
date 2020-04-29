package com.github.ppaszkiewicz.tools.demo.bindService

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.github.ppaszkiewicz.tools.demo.R
import com.github.ppaszkiewicz.tools.toolbox.extensions.startService
import com.github.ppaszkiewicz.tools.toolbox.extensions.stopService
import com.github.ppaszkiewicz.tools.toolbox.service.DirectBindService
import kotlinx.android.synthetic.main.activity_buttons.*


class BindServiceDemoActivity : AppCompatActivity(R.layout.activity_buttons){
    companion object{
        const val TAG = "BindDemoActivity"
    }
    // always bind without auto create
    val serviceConn = TestService.connBuilder.manual(this, Context.BIND_AUTO_CREATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        button1.text = "Start service"
        button2.text = "Stop service"
        button3.text = "Bind to service"
        button4.text = "Unbind from service"

        title = "Bind service test"
        textView0.text = "Service will be bound without BIND_AUTO_CREATE flag - it will be destroyed as soon" +
                " as you unbind from it."

        button1.setOnClickListener {
            startService<TestService>()
            Log.d(TAG, "startService, isBound: ${serviceConn.isBound}")
        }

        button2.setOnClickListener {
            val stopped = stopService<TestService>()
            Log.d(TAG, "service was stopped: $stopped")
        }

        button3.setOnClickListener {
            serviceConn.bind()
        }

        button4.setOnClickListener {
            serviceConn.unbind()
        }

        // add all possible listeners

        serviceConn.run {
            observe(this@BindServiceDemoActivity, Observer{
                textView1.text = it?.foo() ?: "TestService is null"
            })
            onConnect = {
                Log.d(TAG, "onConnect")
                textView2.text = "Connected"
            }
            onDisconnect = {
                Log.d(TAG, "onDisconnect")
                textView2.text = "Disconnect (by unbind)"
            }
            onSuddenDisconnect = {
                Log.d(TAG, "onSuddenDisconnect")
                textView2.text = "Disconnect (connection lost)"
            }
            onBindingDied = {
                Log.d(TAG, "onBindingDied")
                false
            }
            onNullBinding = {
                Log.d(TAG, "onNullBinding")
            }
            onBind = {
                textView00.text = "Connection Bound = true"
            }
            onUnbind = {
                textView00.text = "Connection Bound = false"
            }
        }
    }

    override fun onStop() {
        // using manual connection for this demo, have to handle unbinding
        serviceConn.unbind()
        super.onStop()
    }
}

class TestService : DirectBindService.Impl(){
    companion object{
        val connBuilder = DirectBindService.ConnectionFactory<TestService>()
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

    override fun onBind(intent: Intent): IBinder {
        Log.d("TestService", "BINDING $intent")
        return super.onBind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("TestService", "DESTROYED")
    }
}