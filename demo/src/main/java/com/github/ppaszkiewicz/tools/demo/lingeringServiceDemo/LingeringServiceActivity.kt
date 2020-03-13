package com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.github.ppaszkiewicz.tools.toolbox.service.LingeringServiceConnection
import com.github.ppaszkiewicz.tools.demo.R
import com.github.ppaszkiewicz.tools.toolbox.service.DirectServiceConnection
import kotlinx.android.synthetic.main.activity_service.*

/** Uses lifecycle to automatically handle connection*/
class LingeringServiceActivity : AppCompatActivity(R.layout.activity_service){
    val serviceConn = LingeringServiceConnection.observe<DemoLingeringService>(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textView.text = "Service handled by lifecycle\nsee logs for service state"
        setLogCallbacks(serviceConn)
    }
}

/** Manually handle connection, prevents lingering when activity is finishing. */
class LingeringServiceActivity2 : AppCompatActivity(R.layout.activity_service){
    val serviceConn = LingeringServiceConnection.create<DemoLingeringService>(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textView.text = "Service handled manually - doesn't linger on finish \nsee logs for service state"
        setLogCallbacks(serviceConn)
    }

    override fun onStart() {
        super.onStart()
        serviceConn.bind()
    }

    override fun onStop() {
        super.onStop()
        serviceConn.unbind(isFinishing)
    }
}

/** Uses liveData to automatically handle connection. */
class LingeringServiceActivity3 : AppCompatActivity(R.layout.activity_service){
    val serviceConn = LingeringServiceConnection.liveData<DemoLingeringService>(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textView.text = "Service handled by liveData state\nsee logs for service state"
        setLogCallbacks(serviceConn)
        serviceConn.observe(this, Observer {
            Log.d("DEMO_ACT", "connected to $it")
        })
    }
}

private fun setLogCallbacks(serviceConnection: LingeringServiceConnection<*>){
    serviceConnection.onBind = {
        Log.d("DEMO_ACT", "service is bound")
    }
    serviceConnection.onUnbind = {
        Log.d("DEMO_ACT", "service is unbound")
    }
}