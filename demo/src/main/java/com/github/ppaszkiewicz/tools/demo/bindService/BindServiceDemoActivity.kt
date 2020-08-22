package com.github.ppaszkiewicz.tools.demo.bindService

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.view.isGone
import androidx.lifecycle.*
import com.github.ppaszkiewicz.tools.demo.R
import com.github.ppaszkiewicz.tools.toolbox.extensions.LoopRunnable
import com.github.ppaszkiewicz.tools.toolbox.extensions.startService
import com.github.ppaszkiewicz.tools.toolbox.extensions.stopService
import com.github.ppaszkiewicz.tools.toolbox.lifecycle.plus
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
                checkLeakedObservers(it)
                // lifecycle of activity, connection and service itself can be combined when observing
                // livedata inside the service
                //val compoundLifecycleOwner = this@BindServiceDemoActivity + serviceConn

                it.serviceValue.observe(serviceConn, Observer { bindCount ->
                    textView3.text = "service was connected to $bindCount times"
                })
                it.serviceLifeSpan.observe(serviceConn, Observer { time ->
                    textView5.text = "Service is alive for $time seconds."
                    Log.d("T", "Service is alive for $time")
                })
                observeState()
            }
            onDisconnect = {
                Log.d(TAG, "onDisconnect")
                textView2.text = "Disconnect (by unbind)"
            }
            onConnectionLost = {
                Log.d(TAG, "onSuddenDisconnect")
                textView2.text = "Disconnect (connection lost)"
                true
            }
            onBindingDied = {
                Log.d(TAG, "onBindingDied")
                true
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

        // observe lifecycle
        textView4.text = "Event: --, state: none"
    }

    private fun observeState() {
        serviceConn.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                textView4.text =
                    "Event: ${event.name}, state: ${source.lifecycle.currentState.name}"
            }

        })
    }

    private fun checkLeakedObservers(service : TestService){
        if (service.serviceValue.hasObservers()) {
            Log.e(
                TAG,
                if (service.serviceValue.hasActiveObservers()) {
                    "Service has leaked active observers!"
                } else
                    "Service has leaked (inactive) observers!"
            )
        } else Log.d(TAG, "Service has no leaks.")
    }

    override fun onStop() {
        // using manual connection for this demo, have to handle unbinding
        serviceConn.unbind()
        super.onStop()
    }

    override fun onDestroy() {
        // using manual connection for this demo, have to handle lifecycle destruction
        serviceConn.dispatchDestroyLifecycle()
        super.onDestroy()
    }
}

class TestService : DirectBindService.Impl() {
    companion object {
        val connectionFactory = DirectBindService.ConnectionFactory<TestService>()
    }

    fun foo() = "TestService is alive!"

    fun notifyConnected() {
        serviceValue.value = (serviceValue.value!! + 1)
    }

    val serviceValue = MutableLiveData(0)
    val serviceLifeSpan = MutableLiveData(0)

    private val lifeSpanLoop = LoopRunnable(1000) {
        val incrementValue = serviceLifeSpan.value!! + 1
        serviceLifeSpan.value = incrementValue
        true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TestService", "STARTED")
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
        val nm = getSystemService<NotificationManager>()!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel("CHANNEL_ID") == null
        ) {
            val channel = NotificationChannel(
                "CHANNEL_ID",
                "CHANNEL_NAME",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            // disable sound
            channel.setSound(null, null)
            nm.createNotificationChannel(channel)
        }
        val n = NotificationCompat.Builder(this, "CHANNEL_ID")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentText("Test service")
            .build()
        nm.notify(0, n)
        lifeSpanLoop.startDelayed()
    }

    override fun onDestroy() {
        val nm = getSystemService<NotificationManager>()!!
        nm.cancel(0)
        lifeSpanLoop.stop()
        super.onDestroy()
        Log.d("TestService", "DESTROYED")
    }
}