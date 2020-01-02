package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.View
import androidx.fragment.app.Fragment
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/*
    Yet another preferences extensions. Implemented as delegates.
 */

/** Returns default sharedPreferences for this context. Assumes context is not null.*/
val Context?.defaultPrefs: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(this!!)

// alias for context delegates
/** Delegate for obtaining data from default shared preferences. */
val Context.preferences
    get() = ContextDelegate.OfContext(this)
/** Delegate for obtaining data from default shared preferences. */
val Fragment.preferences
    get() = ContextDelegate.OfFragment(this)
/** Delegate for obtaining data from default shared preferences. */
val View.preferences
    get() = ContextDelegate.OfView(this)

/** Boolean preference. [setBlock] can be run before changing value in preferences.*/
fun ContextDelegate.boolean(key: String, default: Boolean, setBlock: ((Boolean) -> Unit)? = null) =
    RWBooleanPref(this, key, default, setBlock)

/** Long preference. [setBlock] can be run before changing value in preferences.*/
fun ContextDelegate.long(key: String, default: Long, setBlock: ((Long) -> Unit)? = null) =
    RWLongPref(this, key, default, setBlock)

/** Int preference. [setBlock] can be run before changing value in preferences.*/
fun ContextDelegate.int(key: String, default: Int, setBlock: ((Int) -> Unit)? = null) =
    RWIntPref(this, key, default, setBlock)

/** String preference. [setBlock] can be run before changing value in preferences.*/
fun ContextDelegate.string(key: String, default: String, setBlock: ((String) -> Unit)? = null) =
    RWStringPref(this, key, default, setBlock)

/** Enum preference - mapped to ENUM NAME string. [setBlock] can be run before changing the value in preferences.*/
inline fun <reified T : Enum<T>> ContextDelegate.enum(
    key: String,
    default: T,
    noinline setBlock: ((T) -> Unit)? = null
) = RWEnumPref(this, key, default, T::class.java, setBlock)

/** Edit with auto [apply] after block. */
inline fun SharedPreferences.edit(edit: SharedPreferences.Editor.() -> Unit) =
    edit().apply(edit).apply()

private typealias SP = SharedPreferences
private typealias SPEditor = SharedPreferences.Editor

/*
    Backing classes.
 */

/** Base for preference property accessor. */
abstract class RWPref<T>(
    val context: ContextDelegate,
    val key: String,
    val default: T,
    val setBlock: ((T) -> Unit)? = null
) : ReadWriteProperty<Any, T> {
    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        setBlock?.invoke(value)
        context.getValue(thisRef, property).defaultPrefs.edit { invokeSetter(this, key, value) }
    }

    override fun getValue(thisRef: Any, property: KProperty<*>): T =
        invokeGetter(context.getValue(thisRef, property).defaultPrefs, key, default)

    abstract fun invokeSetter(editor: SPEditor, key: String, value: T): SPEditor
    abstract fun invokeGetter(prefs: SP, key: String, default: T): T
}

/** Boolean preference. [setBlock] can be run before changing value in preferences.*/
class RWBooleanPref(
    context: ContextDelegate,
    key: String,
    default: Boolean,
    setBlock: ((Boolean) -> Unit)? = null
) : RWPref<Boolean>(context, key, default, setBlock) {
    override fun invokeSetter(editor: SPEditor, key: String, value: Boolean) =
        editor.putBoolean(key, value)

    override fun invokeGetter(prefs: SP, key: String, default: Boolean) =
        prefs.getBoolean(key, default)
}

/** Long preference. [setBlock] can be run before changing value in preferences.*/
class RWLongPref(
    context: ContextDelegate,
    key: String,
    default: Long,
    setBlock: ((Long) -> Unit)? = null
) : RWPref<Long>(context, key, default, setBlock) {
    override fun invokeSetter(editor: SPEditor, key: String, value: Long) =
        editor.putLong(key, value)

    override fun invokeGetter(prefs: SP, key: String, default: Long) = prefs.getLong(key, default)
}

/** Int preference. [setBlock] can be run before changing the value in preferences.*/
class RWIntPref(
    context: ContextDelegate,
    key: String,
    default: Int,
    setBlock: ((Int) -> Unit)? = null
) : RWPref<Int>(context, key, default, setBlock) {
    override fun invokeSetter(editor: SPEditor, key: String, value: Int) = editor.putInt(key, value)
    override fun invokeGetter(prefs: SP, key: String, default: Int) = prefs.getInt(key, default)
}

/** String preference. [setBlock] can be run before changing the value in preferences.*/
class RWStringPref(
    context: ContextDelegate,
    key: String,
    default: String,
    setBlock: ((String) -> Unit)? = null
) : RWPref<String>(context, key, default, setBlock) {
    override fun invokeSetter(editor: SPEditor, key: String, value: String) =
        editor.putString(key, value)

    override fun invokeGetter(prefs: SP, key: String, default: String) =
        prefs.getString(key, default)
}

/** Enum preference - mapped to ENUM NAME string. [setBlock] can be run before changing the value in preferences.*/
class RWEnumPref<T : Enum<T>>(
    context: ContextDelegate,
    key: String,
    default: T,
    val enumClass: Class<T>,
    setBlock: ((T) -> Unit)? = null
) : RWPref<T>(context, key, default, setBlock) {
    init {
        require(enumClass.isEnum)
    }

    override fun invokeSetter(editor: SPEditor, key: String, value: T) =
        editor.putString(key, value.name)

    override fun invokeGetter(prefs: SP, key: String, default: T): T {
        val s = prefs.getString(key, null) ?: return default
        val enum = enumClass.enumConstants.find { it.name == s }
        return enum ?: default
    }
}