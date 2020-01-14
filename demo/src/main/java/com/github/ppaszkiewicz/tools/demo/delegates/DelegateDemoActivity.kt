package com.github.ppaszkiewicz.tools.demo.delegates

import android.util.Log
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.github.ppaszkiewicz.tools.toolbox.extensions.*

class DelegateDemoActivity : AppCompatActivity() {
    // with no arg constructor
    /** this uses property name (f1) as tag **/
    val f1 by fragmentManager<MyFragmentOne>()
    /** this uses argument (CustomTag) as tag **/
    val f2 by fragmentManager<MyFragmentOne>("CustomTag")
    /** this uses [MyFragmentOne.TAG] as tag **/
    val f3 by fragmentManager<MyFragmentOne>().useTag()

    // with builders
    /** this uses property name (no tag argument) as tag with custom fragment builder. */
    val f10 by fragmentManager {
        MyFragmentOne.newInstance("testArgument")
    }
    /** this uses argument as tag with custom fragment builder. */
    val f20 by fragmentManager("CustomTag2") {
        MyFragmentOne.newInstance("testArgument")
    }
    /** this uses [MyFragmentOne.TAG] as tag with custom fragment builder. */
    val f30 by fragmentManager {
        MyFragmentOne.newInstance("testArgument")
    }.useTag()

    // preference delegates
    val prop by preferences.string("PREF_1", "novalue")
    val prop2 by preferences.enum("PREF_2", MyTestEnum.VAL1){
        // listener
        Log.d("DEBUG","PREF_2 is now $it")
    }
}

class MyFragmentOne : Fragment() {
    companion object {
        @Keep
        const val TAG = "MyFragmentOneTag"

        // new instance builder
        fun newInstance(argument: String) = MyFragmentOne().withArguments {
            putString("ARG", argument)
        }
    }

    /** this uses property name (f1) as tag **/
    val nestedF1 by childFragmentManager<MyFragmentTwo>()

    /** this references f2 of parent activity fragment manager */
    val parentF1 by parentFragmentManager<MyFragmentTwo>("CustomTag")

    /** Invalid call that will crash (MyFragmentTwo does not have a static TAG) */
    val nestedFragment2 by childFragmentManager<MyFragmentTwo>().useTag()

    // preference delegates
    val prop by preferences.string("PREF_1", "novalue")
    val prop2 by preferences.enum("PREF_2", MyTestEnum.VAL1)
}

class MyFragmentTwo : Fragment(){
    /** Fragment inside parent (FragmentOne) fragment manager. */
    val nestedFragment by parentFragmentManager<MyFragmentThree>("NestedCustomTag")
    /** Even deeper fragment nesting. */
    val nestedNestedFragment by childFragmentManager<MyFragmentThree>()
    /** Fragment from parent activity. */
    val activityFragment by activityFragmentManager<MyFragmentThree>()
}

// final dummy fragment
class MyFragmentThree : Fragment(){
    // some more options to find fragments
    val childF by parentFragmentManager<MyFragmentThree>().findOnly()
    val childF2 by parentFragmentManager<MyFragmentThree>().findNullable()
    val parentRoot by activityFragmentManager<MyFragmentOne>().useTagFindOnly()
    val parentNotExisting by activityFragmentManager<MyFragmentOne>().useTagFindNullable()
}

enum class MyTestEnum{
    VAL1, VAL2, VAL3
}