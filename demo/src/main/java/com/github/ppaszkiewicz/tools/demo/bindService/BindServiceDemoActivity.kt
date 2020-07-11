package com.github.ppaszkiewicz.tools.demo.bindService

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.github.ppaszkiewicz.tools.demo.R
import com.github.ppaszkiewicz.tools.toolbox.extensions.startService
import com.github.ppaszkiewicz.tools.toolbox.extensions.stopService
import com.github.ppaszkiewicz.tools.toolbox.service.DirectBindService
import kotlinx.android.synthetic.main.activity_buttons.*


class BindServiceDemoActivity : AppCompatActivity(R.layout.activity_buttons) {
    companion object {
        const val TAG = "BindDemoActivity"
    }

    val serviceConn = TestService.connectionFactory.manual(this, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        button1.text = "Start service"
        button2.text = "Stop service"
        button3.text = "Bind to service - AUTO_CREATE"
        button4.text = "Bind to service - no flags"
        button5.text = "Unbind from service"
        button6.isGone = true

        title = "Bind service test"
        textView0.text = """
            |Using AUTO_CREATE flag will make service come alive even if it's not started. It will also prevent service from being destroyed by stop.
            |Using NO FLAGS will require service to be started to connect - additionally if it's stopped while bound restarting it will create NEW service object.
        """.trimMargin()

        button1.setOnClickListener {
            startService<TestService>()
            Log.d(TAG, "startService, isBound: ${serviceConn.isBound}")
        }

        button2.setOnClickListener {
            val stopped = stopService<TestService>()
            Log.d(TAG, "service was stopped: $stopped")
        }

        button3.setOnClickListener {
            serviceConn.bind(Context.BIND_AUTO_CREATE)
        }

        button4.setOnClickListener {
            serviceConn.bind(0)
        }

        button5.setOnClickListener {
            serviceConn.unbind()
        }

        // add all possible listeners

        serviceConn.run {
            observe(this@BindServiceDemoActivity, Observer {
                textView1.text = it?.foo() ?: "TestService is null"
            })
            onConnect = {
                Log.d(TAG, "onConnect")
                textView2.text = "Connected"
                it.notifyConnected()
            }
            onFirstConnect = {
                Log.d(TAG, "onFirstConnect")
                it.serviceValue.observe(serviceConn, Observer { bindCount ->
                    textView3.text = "service was connected to $bindCount times"
                })
            }
            onDisconnect = {
                Log.d(TAG, "onDisconnect")
                textView2.text = "Disconnect (by unbind)"
            }
            onConnectionLost = {
                Log.d(TAG, "onSuddenDisconnect")
                textView2.text = "Disconnect (connection lost)"
                false
            }
            onBindingDied = {
                Log.d(TAG, "onBindingDied")
                false
            }
            onNullBinding = {
                Log.d(TAG, "onNullBinding")
            }
            onBind = {
                textView00.text = "Connection bound = true ($currentBindFlags)"
            }
            onUnbind = {
                textView00.text = "Connection bound = false ($currentBindFlags)"
            }
        }
    }

    override fun onStop() {
        // using manual connection for this demo, have to handle unbinding
        serviceConn.unbind()
        super.onStop()
    }
}

class TestService : DirectBindService.Impl() {
    companion object {
        val connectionFactory = DirectBindService.ConnectionFactory<TestService>()
    }

    fun foo() = "TestService is alive!"

    fun notifyConnected(){
        serviceValue.value = (serviceValue.value!! + 1)
    }

    val serviceValue = MutableLiveData(0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TestService", "STARTED")
//        GlobalScope.launch(Dispatchers.Main){
//            delay(3000)
//            stopSelf()
//        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        val binder = super.onBind(intent)
        Log.d("TestService", "BINDING $intent")
        Log.d("TestService", "BINDING with $binder")
        return binder
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