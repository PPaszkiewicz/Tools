package com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.github.ppaszkiewicz.kotlin.tools.services.BindServiceConnectionCallbacks
import com.github.ppaszkiewicz.tools.demo.databinding.ActivityServiceBinding
import com.github.ppaszkiewicz.tools.toolbox.delegate.viewBinding

/** Uses lifecycle to automatically handle connection*/
class LingeringServiceActivity : AppCompatActivity(){
    val serviceConn = DemoLingeringService.connectionFactory.lifecycle(this)
    val binding by viewBinding<ActivityServiceBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.textView.text = "Service handled by lifecycle\nsee logs or notification for service state"
        serviceConn.setCallbackInterface(logCallbacks())
    }
}

/** Manually handle connection, prevents lingering when activity is finishing. */
class LingeringServiceActivity2 : AppCompatActivity(){
    val serviceConn = DemoLingeringService.connectionFactory.manual(this)
    val binding by viewBinding<ActivityServiceBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.textView.text = "Service handled manually - doesn't linger on finish \nsee logs or notification for service state"
        serviceConn.setCallbackInterface(logCallbacks())
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
class LingeringServiceActivity3 : AppCompatActivity(){
    val serviceConn = DemoLingeringService.connectionFactory.observable(this)
    val binding by viewBinding<ActivityServiceBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.textView.text = "Service handled by liveData state\nsee logs or notification for service state"
        serviceConn.setCallbackInterface(logCallbacks())
        serviceConn.observe(this, Observer {
            Log.d("DEMO_ACT", "connected to $it")
        })
    }
}

private fun logCallbacks() = object : BindServiceConnectionCallbacks.Adapter<DemoLingeringService>(){
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