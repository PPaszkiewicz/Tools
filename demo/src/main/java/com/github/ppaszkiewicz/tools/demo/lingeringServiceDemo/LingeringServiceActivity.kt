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
class LingeringServiceActivity : AppCompatActivity(){
    val serviceConn = LingeringServiceConnection.observe<DemoLingeringService>(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service)
        textView.text = "Service handled by lifecycle\nsee logs for service state"
        serviceConn.onBind = {
            Log.d("DEMO_ACT", "service is bound")
        }
        serviceConn.onUnbind = {
            Log.d("DEMO_ACT", "service is unbound")
        }
    }
}

/** Manually handle connection to prevent lingering. */
class LingeringServiceActivity2 : AppCompatActivity(){
    val serviceConn = LingeringServiceConnection.create<DemoLingeringService>(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service)
        textView.text = "Service handled manually - doesn't linger on finish \nsee logs for service state"
        serviceConn.onBind = {
            Log.d("DEMO_ACT", "service is bound")
        }
        serviceConn.onUnbind = {
            Log.d("DEMO_ACT", "service is unbound")
        }
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

/** Uses livedata to automatically handle connection*/
class LingeringServiceActivity3 : AppCompatActivity(){
    val serviceConn = LingeringServiceConnection.liveData<DemoLingeringService>(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service)
        textView.text = "Service handled by lifecycle\nsee logs for service state"
        serviceConn.onBind = {
            Log.d("DEMO_ACT", "service is bound")
        }
        serviceConn.onUnbind = {
            Log.d("DEMO_ACT", "service is unbound")
        }
        serviceConn.observe(this, Observer {
            Log.d("DEMO_ACT", "connected to $it")
        })
    }
}