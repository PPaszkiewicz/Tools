package com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.github.ppaszkiewicz.tools.demo.R
import com.github.ppaszkiewicz.tools.toolbox.service.BindServiceConnection
import com.github.ppaszkiewicz.tools.toolbox.service.BindServiceConnectionCallbacks
import com.github.ppaszkiewicz.tools.toolbox.service.LingeringLifecycleServiceConnection
import kotlinx.android.synthetic.main.activity_service.*

/** Uses lifecycle to automatically handle connection*/
class LingeringServiceActivity : AppCompatActivity(R.layout.activity_service){
    val serviceConn = DemoLingeringService.connectionFactory.lifecycle(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textView.text = "Service handled by lifecycle\nsee logs or notification for service state"
        logCallbacks().injectInto(serviceConn)
    }
}

/** Manually handle connection, prevents lingering when activity is finishing. */
class LingeringServiceActivity2 : AppCompatActivity(R.layout.activity_service){
    val serviceConn = DemoLingeringService.connectionFactory.manual(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textView.text = "Service handled manually - doesn't linger on finish \nsee logs or notification for service state"
        logCallbacks().injectInto(serviceConn)
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
    val serviceConn = DemoLingeringService.connectionFactory.observable(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textView.text = "Service handled by liveData state\nsee logs or notification for service state"
        logCallbacks().injectInto(serviceConn)
        serviceConn.observe(this, Observer {
            Log.d("DEMO_ACT", "connected to $it")
        })
    }
}

private fun logCallbacks() = object : BindServiceConnectionCallbacks<DemoLingeringService>(){
    override fun onBind() {
        Log.d("DEMO_ACT", "service is bound")
    }

    override fun onUnbind() {
        Log.d("DEMO_ACT", "service is unbound")
    }

    override fun onFirstConnect(service: DemoLingeringService) {
        Log.d("DEMO_ACT", "connected first time to $service")
    }

    override fun onConnect(service: DemoLingeringService) {
        Log.d("DEMO_ACT", "connected or reconnected to $service")
    }
}