package com.github.ppaszkiewicz.tools.demo.views

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import com.github.ppaszkiewicz.tools.demo.R
import com.github.ppaszkiewicz.tools.demo.databinding.ActivitySaveStateTestBinding
import com.github.ppaszkiewicz.tools.toolbox.delegate.fragments
import com.github.ppaszkiewicz.tools.toolbox.delegate.viewBinding
import com.github.ppaszkiewicz.tools.toolbox.extensions.swap

/** See layout file for details of this demo. */
class SaveStateTestActivity : AppCompatActivity(R.layout.activity_frame){
    val testFragment by fragments<SaveStateTestFragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.swap(R.id.container, testFragment)
    }
}

class SaveStateTestFragment : Fragment(R.layout.activity_save_state_test){
    val binding by viewBinding<ActivitySaveStateTestBinding>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("SAVESTATE", "created view")
        binding.btnReattach.setOnClickListener {
            parentFragmentManager.commitNow {
                detach(this@SaveStateTestFragment)
            }
            parentFragmentManager.commitNow {
                attach(this@SaveStateTestFragment)
            }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        requireView().jumpDrawablesToCurrentState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("SAVESTATE", "destroyed view")
    }
}