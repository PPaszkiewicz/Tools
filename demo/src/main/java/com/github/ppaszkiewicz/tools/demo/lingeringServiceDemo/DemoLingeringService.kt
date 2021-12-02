package com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.*
import com.github.ppaszkiewicz.tools.demo.R
import com.github.ppaszkiewicz.tools.toolbox.extensions.LoopRunnable
import com.github.ppaszkiewicz.tools.services.DirectBindService
import com.github.ppaszkiewicz.tools.services.LingeringService

/** Demo for [LingeringService] and [DirectBindService]. */
class DemoLingeringService : LingeringService(){
    companion object{
        const val TAG = "DEMO_SERVICE"
        /** Connection factory to this service. */
        val connectionFactory = ConnectionFactory<DemoLingeringService>()
    }

    val serviceLifeSpan = MutableLiveData(0)

    private val lifeSpanLoop = LoopRunnable(1000) {
        val incrementValue = serviceLifeSpan.value!! + 1
        serviceLifeSpan.value = incrementValue
        true
    }

    init {
        // reduce timeout to 2 sec down from default 5
        serviceTimeoutMs = 2000L
        //debug...
        this.lifecycle.addObserver(object : LifecycleObserver{
            @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
            fun onLifecycleEvent(source : LifecycleOwner, event: Lifecycle.Event) {
                Log.d(TAG, "observer, event is $event")
            }
        })
        lifeSpanLoop.observe(this)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
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
            .setContentText("Test lingering service")
            .build()
        nm.notify(1, n)
        Toast.makeText(this, "service created", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        val nm = getSystemService<NotificationManager>()!!
        nm.cancel(1)
        Log.d(TAG, "onDestroy")
        Toast.makeText(this, "service destroyed", Toast.LENGTH_SHORT).show()
    }

    override fun onServiceTimeoutStarted() {
        Log.d(TAG, "onServiceTimeoutStarted: ${serviceTimeoutMs}ms")
    }

    override fun onServiceTimeoutFinished() {
        Log.d(TAG, "onServiceTimeoutFinished")
    }
}