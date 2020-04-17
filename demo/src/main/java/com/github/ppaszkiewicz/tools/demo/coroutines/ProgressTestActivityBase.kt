package com.github.ppaszkiewicz.tools.demo.coroutines

import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.Gravity
import android.widget.GridLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setMargins
import com.github.ppaszkiewicz.tools.demo.R
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.activity_test.*

// extras for activity  and service intents

// base for progress test activities
abstract class TestActivityBase : AppCompatActivity() {
    companion object {
        const val EXTRA_LOADER_ARGS = "EXTRA_LOADER_ARGS"
    }

    val testActivityParams by lazy<TestActivityParams> {
        intent.getParcelableExtra(EXTRA_LOADER_ARGS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        Log.d("TestActivity", "starting activity: ${this.javaClass.simpleName}, $testActivityParams")

        val rows = testActivityParams.taskCountRows
        val columns = testActivityParams.taskCountColumns
        val maxTaskCount = testActivityParams.taskCount

        layTestLoaderContainer.apply {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            rowCount = if (isLandscape) columns else rows
            columnCount = if (isLandscape) rows else columns

            // fill the grid layout with evenly spread views
            repeat(rowCount) { row ->
                repeat(columnCount) { column ->
                    val v = ProgressTextView(context)
                    val p = GridLayout.LayoutParams(
                        GridLayout.spec(row, 1f), GridLayout.spec(column, 1f)
                    ).apply {
                        width = 0
                        height = 0
                        setMargins(2)   // miniscule 2 pixel margin
                    }
                    val taskId = row * columnCount + column
                    // displays text which is equal to task id which is equal to view child index
                    v.text = (taskId % maxTaskCount).toString()
                    v.tag = taskId
                    p.setGravity(Gravity.FILL)
                    addView(v, p)
                }
            }
        }
    }
}

/** Wrapper for activity params. */
@Parcelize
data class TestActivityParams(
    /** Count of rows to lay out tasks in. */
    val taskCountRows: Int,
    /** Count of rows to lay out tasks in. */
    val taskCountColumns: Int,
    /** Default duration of task (seconds). */
    val taskDurationSeconds: Int,
    /** Random extra time (seconds) per task. */
    val taskDurationSecondsVariance: Int = 0,
    /** Chance for each task to crash (per second). 0.0f - 1.0f. */
    val taskRandomCrashChance: Float = .0f,
    /**
     * Number of tasks to run.
     *
     * - If there's more views than tasks ([taskCountRows] * [taskCountColumns]) views with ids above task count will request tasks starting from id 0 (modulo.
     * - If there's less views than tasks, some tasks will be omitted (won't start).
     * */
    val taskCount: Int = taskCountRows * taskCountColumns,
    /** Max jobs size (service only) - only that many tasks will be handled at once, others will wait in queue.*/
    val maxJobSize : Int = 4
) : Parcelable