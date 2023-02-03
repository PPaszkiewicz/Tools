package com.github.ppaszkiewicz.tools.demo.coroutines

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.github.ppaszkiewicz.tools.demo.MainActivity
import com.github.ppaszkiewicz.tools.demo.R
import com.github.ppaszkiewicz.tools.demo.databinding.ViewParamsEditBinding
import com.github.ppaszkiewicz.tools.toolbox.viewBinding.viewBinding
import com.github.ppaszkiewicz.tools.toolbox.viewBinding.viewValue

/** Dialog for editing [TestActivityParams]. */
class TestActivityParamsDialog : DialogFragment(R.layout.view_params_edit){
    val srcParams
        get() = arguments?.getParcelable<TestActivityParams>(TestActivityBase.EXTRA_LOADER_ARGS)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = ViewParamsEditBinding.inflate(LayoutInflater.from(requireContext()))
        return AlertDialog.Builder(requireContext(), theme)
            .setTitle("Edit task params")
            .setView(binding.root)
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
            }.create()
    }

    private var EditText.int: Int
        get() = text.toString().toIntOrNull() ?: 0
        set(number) = setText(number.toString())

    private var EditText.float: Float
        get() = text.toString().toFloatOrNull() ?: 0f
        set(number) = setText(number.toString())
}