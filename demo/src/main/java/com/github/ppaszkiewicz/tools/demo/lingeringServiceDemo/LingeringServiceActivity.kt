package com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.ppaszkiewicz.tools.toolbox.service.LingeringServiceConnection
import com.github.ppaszkiewicz.tools.demo.R
import kotlinx.android.synthetic.main.activity_service.*

/** Uses lifecycle to automatically handle connection*/
class LingeringServiceActivity : AppCompatActivity(){
    val serviceConn = LingeringServiceConnection.observe<DemoLingeringService>(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service)
        textView.text = "Service handled by lifecycle\nsee logs for service state"
    }
}

/** Manually handle connection to prevent lingering. */
class LingeringServiceActivity2 : AppCompatActivity(){
    val serviceConn = LingeringServiceConnection.create<DemoLingeringService>(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service)
        textView.text = "Service handled manually - doesn't linger on finish \nsee logs for service state"
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