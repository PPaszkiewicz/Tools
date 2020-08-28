package com.github.ppaszkiewicz.tools.toolbox.delegate

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/* Requires ContextDelegate from Context.kt*/

/*
    Yet another preferences extensions. Implemented as delegates.
 */

/** Returns default sharedPreferences for this context. Assumes context is not null when called.*/
val Context?.defaultPrefs: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(this!!)

/* Factory methods */

/** Get [SharedPreferencesProvider] for this this shared preferences. */
fun SharedPreferences.delegates(): SPP = SharedPreferencesProvider.Direct(this)

/** Get [SharedPreferencesProvider] for default shared preferences. */
fun Context.preferences(): SPP = SharedPreferencesProvider.Default(this.contextDelegate)

/** Get [SharedPreferencesProvider] for given shared preferences. */
fun Context.preferences(key: String, mode: Int = 0): SPP =
    SharedPreferencesProvider.Custom(this.contextDelegate, key, mode)

/** Get [SharedPreferencesProvider] for default shared preferences. */
fun AndroidViewModel.preferences(): SPP = getApplication<Application>().preferences()

/** Get [SharedPreferencesProvider] for given shared preferences. */
fun AndroidViewModel.preferences(key: String, mode: Int = 0): SPP =
    getApplication<Application>().preferences(key, mode)

/** Get [SharedPreferencesProvider] for default shared preferences. */
fun Fragment.preferences(): SPP = SharedPreferencesProvider.Default(this.contextDelegate)

/** Get [SharedPreferencesProvider] for given shared preferences. */
fun Fragment.preferences(key: String, mode: Int = 0): SPP =
    SharedPreferencesProvider.Custom(this.contextDelegate, key, mode)

/** Get [SharedPreferencesProvider] for default shared preferences. */
fun View.preferences(): SPP = SharedPreferencesProvider.Default(this.contextDelegate)

/** Get [SharedPreferencesProvider] for given shared preferences. */
fun View.preferences(key: String, mode: Int = 0): SPP =
    SharedPreferencesProvider.Custom(this.contextDelegate, key, mode)

/** Edit with auto [apply] after block. */
inline fun SharedPreferences.edit(edit: SharedPreferences.Editor.() -> Unit) =
    edit().apply(edit).apply()

// convenience typealiases
private typealias SP = SharedPreferences
private typealias SPEditor = SharedPreferences.Editor
private typealias SPP = SharedPreferencesProvider

/** Lazy provider of [SharedPreferences] and factory of delegates for specific values. */
sealed class SharedPreferencesProvider : ReadOnlyProperty<Any, SharedPreferences> {
    final override fun getValue(thisRef: Any, property: KProperty<*>) = get()
    abstract fun get(): SharedPreferences

    internal class Direct(private val prefs: SP) : SPP() {
        override fun get() = prefs
    }

    internal class Default(val context: ContextDelegate) : SPP() {
        override fun get() = context.get().defaultPrefs
    }

    internal class Custom(val context: ContextDelegate, val key: String, val mode: Int) : SPP() {
        override fun get(): SP = context.get().getSharedPreferences(key, mode)
    }

    /** Boolean preference. [setListener] can be run before changing value in preferences.*/
    fun boolean(
        key: String,
        default: Boolean,
        setListener: ((Boolean) -> Unit)? = null
    ) = RWBooleanPref(this, key, default, setListener)

    /** Long preference. [setListener] can be run before changing value in preferences.*/
    fun long(key: String, default: Long, setListener: ((Long) -> Unit)? = null) =
        RWLongPref(this, key, default, setListener)

    /** Int preference. [setListener] can be run before changing value in preferences.*/
    fun int(key: String, default: Int, setListener: ((Int) -> Unit)? = null) =
        RWIntPref(this, key, default, setListener)

    /** Float preference. [setListener] can be run before changing value in preferences.*/
    fun float(key: String, default: Float, setListener: ((Float) -> Unit)? = null) =
        RWFloatPref(this, key, default, setListener)

    /** String preference. [setListener] can be run before changing value in preferences.*/
    fun string(key: String, default: String, setListener: ((String) -> Unit)? = null) =
        RWStringPref(this, key, default, setListener)

    /** String set preference. If [default] is not set returns empty set by default. [setListener] can be run before changing value in preferences.*/
    fun stringSet(
        key: String,
        default: Set<String> = emptySet(),
        setListener: ((Set<String>) -> Unit)? = null
    ) = RWStringSetPref(this, key, default, setListener)

    /** Enum preference - mapped to ENUM NAME string. [setBlock] can be run before changing the value in preferences.*/
    inline fun <reified T : Enum<T>> enum(
        key: String,
        default: T,
        noinline setBlock: ((T) -> Unit)? = null
    ) = RWEnumPref(
        this, key, default, T::class.java, setBlock
    )
}

/*
    Backing classes.
 */

/** Single property kept in shared preferences under [key]. */
abstract class RWPref<T>(
    /** Object to access preferences. */
    val prefsProvider: SharedPreferencesProvider,
    /** Key of this preference. */
    val key: String,
    /** Default value of this preference. */
    val default: T,
    /** Setter listener. */
    val setBlock: ((T) -> Unit)? = null
) : ReadWriteProperty<Any, T> {
    protected val prefs by prefsProvider

    /** LiveData object for observing this preference. When observed and preference for [key] is
     * missing, default value is emitted immediately. Otherwise emits only distinct changes.  */
    val liveData: LiveData<T> by lazy { PrefLiveData() }

    // delegate interface operator implementation
    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) = set(value)
    override fun getValue(thisRef: Any, property: KProperty<*>) = get()

    /** Run a transaction that changes this preference to [value]. */
    fun set(value: T) = prefs.edit { set(this, value) }

    /** Change this preference to [value] within [editor] transaction. */
    fun set(editor: SPEditor, value: T) {
        setBlock?.invoke(value)
        editor.set(key, value)
    }

    /** Get this preference value or return [default] if it doesn't exist. */
    fun get(): T = prefs.get(key, default)

    /** Get this preference value or return null if it doesn't exist. */
    fun getOrNull(): T? = if (prefs.contains(key)) prefs.get(key, default) else null

    /** Run a transaction that clears this preference (delete the key). */
    fun clear() = prefs.edit { remove(key) }

    /** Clear this preference (delete the key) within [editor] transaction. */
    fun clear(editor: SPEditor): SPEditor = editor.remove(key)

    /** Actual set that modifies shared preferences. */
    protected abstract fun SPEditor.set(key: String, value: T): SPEditor

    /** Actual get that modifies shared preferences. */
    protected abstract fun SP.get(key: String, default: T): T

    // preference livedata impl based on this preference accessor
    protected open inner class PrefLiveData : LiveData<T>(),
        SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onActive() {
            prefs.registerOnSharedPreferenceChangeListener(this)
            val newValue = prefs.get(key, default)
            if (value != newValue) value = newValue
        }

        override fun onInactive() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SP, key: String) {
            if (key == this@RWPref.key) {
                val newValue = sharedPreferences.get(key, default)
                if (value != newValue) value = newValue
            }
        }
    }
}

/** Boolean preference. [setBlock] can be run before changing value in preferences.*/
class RWBooleanPref(
    prefsProvider: SharedPreferencesProvider,
    key: String,
    default: Boolean,
    setBlock: ((Boolean) -> Unit)? = null
) : RWPref<Boolean>(prefsProvider, key, default, setBlock) {
    /** Invert current value of this preference. */
    fun toggle() = set(!get())
    override fun SPEditor.set(key: String, value: Boolean): SPEditor = putBoolean(key, value)
    override fun SP.get(key: String, default: Boolean) = getBoolean(key, default)
}

/** Long preference. [setBlock] can be run before changing value in preferences.*/
class RWLongPref(
    prefsProvider: SharedPreferencesProvider,
    key: String,
    default: Long,
    setBlock: ((Long) -> Unit)? = null
) : RWPref<Long>(prefsProvider, key, default, setBlock) {
    override fun SPEditor.set(key: String, value: Long): SPEditor = putLong(key, value)
    override fun SP.get(key: String, default: Long) = getLong(key, default)
}

/** Int preference. [setBlock] can be run before changing the value in preferences.*/
class RWIntPref(
    prefsProvider: SharedPreferencesProvider,
    key: String,
    default: Int,
    setBlock: ((Int) -> Unit)? = null
) : RWPref<Int>(prefsProvider, key, default, setBlock) {
    override fun SPEditor.set(key: String, value: Int): SPEditor = putInt(key, value)
    override fun SP.get(key: String, default: Int) = getInt(key, default)
}

/** Float preference. [setBlock] can be run before changing the value in preferences.*/
class RWFloatPref(
    prefsProvider: SharedPreferencesProvider,
    key: String,
    default: Float,
    setBlock: ((Float) -> Unit)? = null
) : RWPref<Float>(prefsProvider, key, default, setBlock) {
    override fun SPEditor.set(key: String, value: Float): SPEditor = putFloat(key, value)
    override fun SP.get(key: String, default: Float) = getFloat(key, default)
}

/** String preference. [setBlock] can be run before changing the value in preferences.*/
class RWStringPref(
    prefsProvider: SharedPreferencesProvider,
    key: String,
    default: String,
    setBlock: ((String) -> Unit)? = null
) : RWPref<String>(prefsProvider, key, default, setBlock) {
    override fun SPEditor.set(key: String, value: String): SPEditor = putString(key, value)
    override fun SP.get(key: String, default: String): String = getString(key, default)!!
}

/** String set preference. [setBlock] can be run before changing the value in preferences.*/
class RWStringSetPref(
    prefsProvider: SharedPreferencesProvider,
    key: String,
    default: Set<String>,
    setBlock: ((Set<String>) -> Unit)? = null
) : RWPref<Set<String>>(prefsProvider, key, default, setBlock) {
    override fun SPEditor.set(key: String, value: Set<String>): SPEditor = putStringSet(key, value)
    override fun SP.get(key: String, default: Set<String>): Set<String> =
        getStringSet(key, default)!!
}

/** Enum preference - mapped to ENUM NAME string. [setBlock] can be run before changing the value in preferences.*/
class RWEnumPref<T : Enum<T>>(
    prefsProvider: SharedPreferencesProvider,
    key: String,
    default: T,
    val enumClass: Class<T>,
    setBlock: ((T) -> Unit)? = null
) : RWPref<T>(prefsProvider, key, default, setBlock) {
    override fun SPEditor.set(key: String, value: T): SPEditor = putString(key, value.name)

    override fun SP.get(key: String, default: T): T {
        val s = getString(key, null) ?: return default
        val enum = enumClass.enumConstants!!.find { it.name == s }
        return enum ?: default
    }
}