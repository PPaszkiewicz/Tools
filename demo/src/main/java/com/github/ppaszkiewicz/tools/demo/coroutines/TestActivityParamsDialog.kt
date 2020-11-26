package com.github.ppaszkiewicz.tools.demo.coroutines

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.github.ppaszkiewicz.tools.demo.databinding.ViewParamsEditBinding

/** Dialog for editing [TestActivityParams]. */
object TestActivityParamsDialog {
    fun createAndShow(context: Context, srcParams: TestActivityParams, onChanged: (TestActivityParams) -> Unit) {
        val paramsBinding = ViewParamsEditBinding.inflate(LayoutInflater.from(context))
        paramsBinding.apply {
            srcParams.apply {
                etRows.int = taskCountRows
                etColumns.int = taskCountColumns
                etDuration.int = taskDurationSeconds
                etDurationVariance.int = taskDurationSecondsVariance
                etCrashChance.float = taskRandomCrashChance
                etTaskCount.int = taskCount
                etMaxJobCount.int = maxJobSize
            }
        }
        AlertDialog.Builder(context)
            .setTitle("Edit task params")
            .setView(paramsBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok){_,_ ->
                val newParams = paramsBinding.run {
                    TestActivityParams(
                        etRows.int,
                        etColumns.int,
                        etDuration.int,
                        etDurationVariance.int,
                        etCrashChance.float,
                        etTaskCount.int,
                        etMaxJobCount.int
                    )
                }
                onChanged(newParams)
            }
            .show()
    }

    private var EditText.int: Int
        get() = text.toString().toIntOrNull() ?: 0
        set(number) = setText(number.toString())

    private var EditText.float: Float
        get() = text.toString().toFloatOrNull() ?: 0f
        set(number) = setText(number.toString())
}