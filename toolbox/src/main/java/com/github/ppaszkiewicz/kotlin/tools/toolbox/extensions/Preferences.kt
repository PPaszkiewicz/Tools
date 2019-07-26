package com.github.ppaszkiewicz.kotlin.tools.toolbox.extensions

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/*
    Yet another preferences extensions. Implemented as delegates.
 */

/** Returns default sharedPreferences for this context. Assumes context is not null.*/
val Context?.defaultPrefs: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(this!!)


/** Edit with auto [apply] after block. */
inline fun SharedPreferences.edit(edit: SharedPreferences.Editor.() -> Unit) = edit().apply(edit).apply()

private typealias SP = SharedPreferences
private typealias SPEditor = SharedPreferences.Editor

/** Base for preference property accessor. */
abstract class RWPref<T>(val key: String,
                         val default: T,
                         val setBlock: ((T) -> Unit)? = null,
                         val setter: SPEditor.(String, T) -> SPEditor,
                         val getter: SP.(String, T) -> T) : ReadWriteProperty<Context, T> {

    override fun setValue(thisRef: Context, property: KProperty<*>, value: T) {
        setBlock?.invoke(value)
        thisRef.defaultPrefs.edit { setter(key, value) }
    }

    override fun getValue(thisRef: Context, property: KProperty<*>): T = thisRef.defaultPrefs.getter(key, default)
}

/** Boolean preference. [setBlock] can be run before changing value in preferences.*/
class RWBooleanPref(key: String,
                    default: Boolean,
                    setBlock: ((Boolean) -> Unit)? = null) :
    RWPref<Boolean>(key, default, setBlock,
        SPEditor::putBoolean,
        SP::getBoolean)

/** Long preference. [setBlock] can be run before changing value in preferences.*/
class RWLongPref(key: String,
                 default: Long,
                 setBlock: ((Long) -> Unit)? = null) :
    RWPref<Long>(key, default, setBlock,
        SPEditor::putLong,
        SP::getLong)

/** Int preference. [setBlock] can be run before changing the value in preferences.*/
class RWIntPref(key: String,
                default: Int,
                setBlock: ((Int) -> Unit)? = null) :
    RWPref<Int>(key, default, setBlock,
        SPEditor::putInt,
        SP::getInt)

/** String preference. [setBlock] can be run before changing the value in preferences.*/
class RWStringPref(key: String,
                   default: String,
                   setBlock: ((String) -> Unit)? = null) :
    RWPref<String>(key, default, setBlock,
        SPEditor::putString,
        SP::getString)