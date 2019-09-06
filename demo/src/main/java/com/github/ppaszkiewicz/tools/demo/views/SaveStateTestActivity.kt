package com.github.ppaszkiewicz.tools.demo.views

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.ppaszkiewicz.tools.demo.R
import kotlinx.android.synthetic.main.activity_save_state_test.*

/** See layout file for details of this demo. */
class SaveStateTestActivity : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_save_state_test)
        btnRecreate.setOnClickListener { recreate() }
    }
}