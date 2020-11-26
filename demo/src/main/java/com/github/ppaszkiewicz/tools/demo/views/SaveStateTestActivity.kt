package com.github.ppaszkiewicz.tools.demo.views

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.ppaszkiewicz.tools.demo.databinding.ActivitySaveStateTestBinding
import com.github.ppaszkiewicz.tools.toolbox.delegate.viewBinding

/** See layout file for details of this demo. */
class SaveStateTestActivity : AppCompatActivity(){
    val binding by viewBinding<ActivitySaveStateTestBinding>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.btnRecreate.setOnClickListener { recreate() }
    }
}