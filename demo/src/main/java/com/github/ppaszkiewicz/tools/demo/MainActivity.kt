package com.github.ppaszkiewicz.tools.demo

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.github.ppaszkiewicz.tools.demo.coroutines.TestActivityBase
import com.github.ppaszkiewicz.tools.demo.coroutines.TestActivityParams
import com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo.LingeringServiceActivity
import com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo.LingeringServiceActivity2
import com.github.ppaszkiewicz.tools.demo.coroutines.loaderDemo.LoaderActivity
import com.github.ppaszkiewicz.tools.demo.coroutines.taskServiceDemo.TaskServiceActivity
import com.github.ppaszkiewicz.tools.demo.coroutines.TestActivityParamsDialog
import com.github.ppaszkiewicz.tools.demo.views.SaveStateTestActivity
import com.github.ppaszkiewicz.tools.toolbox.delegate.preferences
import kotlinx.android.synthetic.main.activity_main.*

/**
 * Activity for selecting test.
 * */
class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val SAVE_PARAMS = "SAVE_PARAMS"
    }

    enum class TestEnum{
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

    // boolean stored in shared preferences. maybe add test for this later.
    var storedPreference by preferences().boolean("Bool", false)
    var storedPrefEnum by preferences().enum("EnumPref", TestEnum.OFF)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        savedInstanceState?.let {
            // restore edited params
            params = it.getParcelable(SAVE_PARAMS)!!
        }

        // task tests
        btnLoaderTest1.setOnClickListener {
            startActivity(
                Intent(this@MainActivity, LoaderActivity::class.java)
                    .putExtra(TestActivityBase.EXTRA_LOADER_ARGS, params)
            )
        }
        btnServiceTest1.setOnClickListener {
            startActivity(
                Intent(this@MainActivity, TaskServiceActivity::class.java)
                    .putExtra(TestActivityBase.EXTRA_LOADER_ARGS, params)
            )
        }

        // lingering service tests
        btnLingeringTest1.setOnClickListener {
            startActivity(Intent(this@MainActivity, LingeringServiceActivity::class.java))
        }
        btnLingeringTest2.setOnClickListener {
            startActivity(Intent(this@MainActivity, LingeringServiceActivity2::class.java))
        }

        // layouts test
        btnViewsTest1.setOnClickListener {
            startActivity(Intent(this@MainActivity, SaveStateTestActivity::class.java))
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
                storedPrefEnum = if(item.isChecked) TestEnum.ON else TestEnum.OFF
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
