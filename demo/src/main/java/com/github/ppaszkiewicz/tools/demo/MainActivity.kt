package com.github.ppaszkiewicz.tools.demo

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.github.ppaszkiewicz.tools.demo.bindService.BindServiceDemoActivity
import com.github.ppaszkiewicz.tools.demo.coroutines.TestActivityBase
import com.github.ppaszkiewicz.tools.demo.coroutines.TestActivityParams
import com.github.ppaszkiewicz.tools.demo.coroutines.TestActivityParamsDialog
import com.github.ppaszkiewicz.tools.demo.coroutines.loaderDemo.LoaderActivity
import com.github.ppaszkiewicz.tools.demo.coroutines.taskServiceDemo.TaskServiceActivity
import com.github.ppaszkiewicz.tools.demo.databinding.ActivityMainBinding
import com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo.LingeringServiceActivity
import com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo.LingeringServiceActivity2
import com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo.LingeringServiceActivity3
import com.github.ppaszkiewicz.tools.demo.recyclerView.NestedRecyclerDemoActivity
import com.github.ppaszkiewicz.tools.demo.viewModel.SyncableLiveDataDemoActivity
import com.github.ppaszkiewicz.tools.demo.views.SaveStateTestActivity
import com.github.ppaszkiewicz.tools.demo.views.StableTextViewActivity
import com.github.ppaszkiewicz.tools.toolbox.delegate.preferences
import com.github.ppaszkiewicz.tools.toolbox.delegate.viewBinding
import com.github.ppaszkiewicz.tools.toolbox.extensions.startActivity

/**
 * Activity for selecting test.
 * */
class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val SAVE_PARAMS = "SAVE_PARAMS"
    }

    enum class TestEnum {
        ON, OFF
    }

    // default params
    var params = TestActivityParams(
        8,
        10,
        5,
        5,
        0.05f,
        10    // less tasks than progress views
    )

    // boolean stored in shared preferences
    var storedPreference by preferences().boolean("Bool", false)

    // delegate object storing enum in shared preferences: kept explicitly bc livedata will be used too
    val enumPref = preferences().enum("EnumPref", TestEnum.OFF)
    var storedPrefEnum by enumPref

    // viewbinding (created by delegate)
    val binding by viewBinding<ActivityMainBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with(binding) {
            setSupportActionBar(toolbar)

            savedInstanceState?.let {
                // restore edited params
                params = it.getParcelable(SAVE_PARAMS)!!
            }

            // task tests
            btnLoaderTest1.setOnClickListener {
                startActivity<LoaderActivity> {
                    putExtra(TestActivityBase.EXTRA_LOADER_ARGS, params)
                }
            }
            btnServiceTest1.setOnClickListener {
                startActivity<TaskServiceActivity> {
                    putExtra(TestActivityBase.EXTRA_LOADER_ARGS, params)
                }
            }

            // binding service test
            btnBoundService.setOnClickListener { startActivity<BindServiceDemoActivity>() }

            // lingering service tests
            btnLingeringTest1.setOnClickListener { startActivity<LingeringServiceActivity>() }
            btnLingeringTest2.setOnClickListener { startActivity<LingeringServiceActivity2>() }
            btnLingeringTest3.setOnClickListener { startActivity<LingeringServiceActivity3>() }

            // layouts test
            btnViewsTest1.setOnClickListener { startActivity<SaveStateTestActivity>() }
            btnSyncLiveDataTest1.setOnClickListener { startActivity<SyncableLiveDataDemoActivity>() }
            btnRecyclerTest.setOnClickListener { startActivity<NestedRecyclerDemoActivity>() }
            btnStableTextViewTest.setOnClickListener { startActivity<StableTextViewActivity>() }

            // preference observer
            enumPref.liveData.observe(this@MainActivity, Observer {
                txtPreferenceValue.text = it.toString()
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SAVE_PARAMS, params)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.action_test_enum).isChecked = storedPrefEnum == TestEnum.ON
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_edit_params -> {
                TestActivityParamsDialog.createAndShow(this, params) {
                    params = it
                }
                true
            }
            R.id.action_test_enum -> {
                item.isChecked = !item.isChecked
                storedPrefEnum = if (item.isChecked) TestEnum.ON else TestEnum.OFF
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
