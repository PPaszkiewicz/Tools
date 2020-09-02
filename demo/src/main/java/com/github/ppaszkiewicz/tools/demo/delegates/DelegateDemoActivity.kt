package com.github.ppaszkiewicz.tools.demo.delegates

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.github.ppaszkiewicz.tools.toolbox.extensions.*
import com.github.ppaszkiewicz.tools.toolbox.delegate.activityFragments
import com.github.ppaszkiewicz.tools.toolbox.delegate.fragments
import com.github.ppaszkiewicz.tools.toolbox.delegate.parentFragments
import com.github.ppaszkiewicz.tools.toolbox.delegate.preferences

class DelegateDemoActivity : AppCompatActivity() {
    // with no arg constructor
    /** this uses class name of [MyFragmentOne] as tag **/
    val f1 by fragments<MyFragmentOne>()

    /** this uses argument (CustomTag) as tag **/
    val f2 by fragments<MyFragmentOne>("CustomTag")

    /** this uses property name (f3) as tag **/
    val f3 by fragments<MyFragmentOne>(true)

    // with builders
    /** this uses class name (no tag argument) as tag with custom fragment builder. */
    val f10 by fragments {
        MyFragmentOne.newInstance("testArgument")
    }

    /** this uses argument as tag with custom fragment builder. */
    val f20 by fragments("CustomTag2") {
        MyFragmentOne.newInstance("testArgument")
    }

    /** this uses property name (f30) as tag with custom fragment builder. */
    val f30 by fragments(true) {
        MyFragmentOne.newInstance("testArgument")
    }

    // preference delegates
    val prop by preferences().string("PREF_1", "novalue")
    val prop2 by preferences("sharedprefskey").enum("PREF_2", MyTestEnum.VAL1) {
        // listener
        Log.d("DEBUG", "PREF_2 is now $it")
    }
}

class MyFragmentOne : Fragment() {
    companion object {
        // new instance builder
        fun newInstance(argument: String) = MyFragmentOne().withArguments {
            putString("ARG", argument)
        }
    }

    /** this uses class name (nestedF1) as tag **/
    val nestedF1 by fragments<MyFragmentTwo>()

    /** this references f2 of parent activity fragment manager */
    val parentF1 by parentFragments<MyFragmentTwo>("CustomTag")

    /** nested using property name */
    val nestedFragment2 by fragments<MyFragmentTwo>(true)

    // preference delegates
    val prop by preferences().string("PREF_1", "novalue")
    val prop2 by preferences().enum("PREF_2", MyTestEnum.VAL1)
}

class MyFragmentTwo : Fragment() {
    /** Fragment inside parent (FragmentOne) fragment manager. */
    val nestedFragment by parentFragments<MyFragmentThree>("NestedCustomTag")

    /** Even deeper fragment nesting. */
    val nestedNestedFragment by fragments<MyFragmentThree>()

    /** Fragment from parent activity. */
    val activityFragment by activityFragments<MyFragmentThree>()
}

// final dummy fragment
class MyFragmentThree : Fragment() {
    // some more options to find fragments
    val childF by parentFragments<MyFragmentThree>().required()
    val childF2 by parentFragments<MyFragmentThree>().nullable()
    val parentRoot by activityFragments<MyFragmentOne>(true).required()
    val parentNotExisting by activityFragments<MyFragmentOne>(false).nullable()
}

enum class MyTestEnum {
    VAL1, VAL2, VAL3
}