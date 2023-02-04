package com.github.ppaszkiewicz.tools.demo.coroutines

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.github.ppaszkiewicz.tools.demo.MainActivity
import com.github.ppaszkiewicz.tools.demo.databinding.ViewParamsEditBinding
import com.github.ppaszkiewicz.tools.viewBinding.dialogViewBinding
import com.github.ppaszkiewicz.tools.viewBinding.setView

/** Dialog for editing [TestActivityParams]. */
class TestActivityParamsDialog : DialogFragment(){
    val binding by dialogViewBinding<ViewParamsEditBinding>()

    val srcParams
        get() = arguments?.getParcelable<TestActivityParams>(TestActivityBase.EXTRA_LOADER_ARGS)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext(), theme)
            .setTitle("Edit task params")
            .setView(binding){
                srcParams!!.apply {
                    etRows.int = taskCountRows
                    etColumns.int = taskCountColumns
                    etDuration.int = taskDurationSeconds
                    etDurationVariance.int = taskDurationSecondsVariance
                    etCrashChance.float = taskRandomCrashChance
                    etTaskCount.int = taskCount
                    etMaxJobCount.int = maxJobSize
                }
                root.jumpDrawablesToCurrentState()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newParams = binding.run {
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
                (activity as MainActivity).onUpdateParams(newParams)
            }
            .create()
    }

    private var EditText.int: Int
        get() = text.toString().toIntOrNull() ?: 0
        set(number) = setText(number.toString())

    private var EditText.float: Float
        get() = text.toString().toFloatOrNull() ?: 0f
        set(number) = setText(number.toString())
}