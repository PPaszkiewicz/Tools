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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.github.ppaszkiewicz.kotlin.tools.services.BindServiceConnection
import com.github.ppaszkiewicz.kotlin.tools.services.BindServiceConnectionCallbacks
import com.github.ppaszkiewicz.tools.demo.R
import com.github.ppaszkiewicz.tools.toolbox.extensions.LoopRunnable
import com.github.ppaszkiewicz.tools.toolbox.extensions.startService
import com.github.ppaszkiewicz.tools.toolbox.extensions.stopService
import com.github.ppaszkiewicz.kotlin.tools.services.DirectBindService
import com.github.ppaszkiewicz.tools.demo.databinding.ActivityButtonsBinding
import com.github.ppaszkiewicz.tools.toolbox.delegate.viewBinding


class BindServiceDemoActivity : AppCompatActivity() {
    companion object {
        const val TAG = "BindDemoActivity"
    }

    val serviceConn = TestService.connectionFactory.manual(this){
        defaultBindFlags = 0
        // uncomment to prevent auto rebinding when service is stopped
        //deadBindingBehavior = BindServiceConnection.DeadBindingBehavior.CALLBACK_ONLY
    }

    val binding by viewBinding<ActivityButtonsBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with(binding) {
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
                //NOTE: this will return TRUE even if service is not started but it has active binding
                //  even if that binding does not carry BIND_AUTO_CREATE flag
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
                if(!serviceConn.isConnected)
                    textView2.text = null
                serviceConn.unbind()
            }

            serviceConn.observe(this@BindServiceDemoActivity) {
                textView1.text = it?.foo() ?: "TestService is null"
            }
            // add all possible listeners - two ways to do so
            //setLambdaListeners()
            setInterfaceCallbacks()

            // observe lifecycle
            textView4.text = "Event: --, state: none"
            textView4andHalf.text = "State: --, state: ${serviceConn.stateLifecycle.currentState}"
        }
    }

    private fun observeState() {
        serviceConn.connectionLifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                binding.textView4.text =
                    "Conn: ${event.name}, state: ${source.lifecycle.currentState.name}"
            }
        })
        serviceConn.stateLifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                binding.textView4andHalf.text =
                    "State: ${event.name}, state: ${source.lifecycle.currentState.name}"
            }
        })
    }

    private fun onFirstConnect(service: TestService) {
        Log.d(TAG, "onFirstConnect")
        checkLeakedObservers(service)
        // lifecycle of activity, connection and service itself can be combined when observing
        // livedata inside the service
        //val compoundLifecycleOwner = this@BindServiceDemoActivity + serviceConn

        service.serviceValue.observe(serviceConn.connectionLifecycleOwner) { bindCount ->
            binding.textView3.text = "service was connected to $bindCount times"
        }
        service.serviceLifeSpan.observe(serviceConn.connectionLifecycleOwner) { time ->
            binding.textView5.text = "Service is alive for $time seconds."
            Log.d("T", "Service is alive for $time")
        }
        observeState()
    }

    private fun checkLeakedObservers(service: TestService) {
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

    private fun setLambdaListeners() {
        serviceConn.run {
            onConnect = {
                Log.d(TAG, "onConnect")
                binding.textView2.text = "Connected"
                it.notifyConnected()
            }
            onFirstConnect = ::onFirstConnect // delegate to local method
            onDisconnect = {
                Log.d(TAG, "onDisconnect")
                binding.textView2.text = "Disconnect (by unbind)"
            }
            onConnectionLost = {
                Log.d(TAG, "onSuddenDisconnect")
                binding.textView2.text = "Disconnect (connection lost)"
                true
            }
            onBindingDied = {
                Log.d(TAG, "onBindingDied")
                if(!serviceConn.config.deadBindingBehavior.rebind){
                    "Connection bound = false ($currentBindFlags)"
                }
                true
            }
            onNullBinding = {
                Log.d(TAG, "onNullBinding")
            }
            onBind = {
                binding.textView00.text = "Connection bound = true ($currentBindFlags)"
            }
            onUnbind = {
                binding.textView00.text = "Connection bound = false ($currentBindFlags)"
            }
            onNotConnected = {
                binding.textView2.text = "Awaiting connection"

            }
        }
    }

    // alternative way to setup callbacks - by using an interface
    private fun setInterfaceCallbacks() {
        val callbackObj = object : BindServiceConnectionCallbacks<TestService> {
            override fun onFirstConnect(service: TestService) {
                this@BindServiceDemoActivity.onFirstConnect(service)
            }

            override fun onConnect(service: TestService) {
                Log.d(TAG, "onConnect")
                binding.textView2.text = "Connected"
                service.notifyConnected()
            }

            override fun onDisconnect(service: TestService) {
                Log.d(TAG, "onDisconnect")
                binding.textView2.text = "Disconnect (by unbind)"
            }

            override fun onConnectionLost(service: TestService): Boolean {
                Log.d(TAG, "onSuddenDisconnect")
                binding.textView2.text = "Disconnect (connection lost)"
                return true
            }

            override fun onBind() {
                binding.textView00.text =
                    "Connection bound = true (${serviceConn.currentBindFlags})"
            }

            override fun onUnbind() {
                binding.textView00.text =
                    "Connection bound = false (${serviceConn.currentBindFlags})"
            }

            override fun onBindingDied(): Boolean {
                Log.d(TAG, "onBindingDied")
                return true
            }

            override fun onNullBinding() {
                Log.d(TAG, "onNullBinding")
            }

            override fun onNotConnected() {
                Log.d(TAG, "onNotConnected")
                binding.textView2.text = "Awaiting connection"
            }

            override fun onBindingFailed(exception: BindServiceConnection.BindingException) {
                throw exception
            }
        }

        serviceConn.setCallbackInterface(callbackObj)
    }

    override fun onStop() {
        // using manual connection for this demo, have to handle unbinding
        serviceConn.unbind()
        super.onStop()
    }

    override fun onDestroy() {
        // using manual connection for this demo, have to handle destruction
        serviceConn.release()
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

    override fun onRebind(intent: Intent?) {
        Log.d("TestService", "REBINDING $intent")
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("TestService", "UNBINDING $intent")
        return true
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
            .setSmallIcon(R.drawable.ic_service)
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