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
    val f1 by childFragmentManager<MyFragmentOne>()

    /** this references f2 of parent activity fragment manager */
    val f2 by parentFragmentManager<MyFragmentOne>("CustomTag")

    // preference delegates
    val prop by preferences.string("PREF_1", "novalue")
    val prop2 by preferences.enum("PREF_2", MyTestEnum.VAL1)
}

enum class MyTestEnum{
    VAL1, VAL2, VAL3
}