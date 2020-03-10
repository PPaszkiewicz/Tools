package com.github.ppaszkiewicz.tools.toolbox.delegate

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.View
import androidx.fragment.app.Fragment
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/*
    Yet another preferences extensions. Implemented as delegates.
 */

/** Returns default sharedPreferences for this context. Assumes context is not null when called.*/
val Context?.defaultPrefs: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(this!!)

/* Factory methods */

/** Delegates for obtaining data from this shared preferences. */
fun SharedPreferences.delegates() =
    SharedPreferencesProvider.Direct(
        this
    )

/** Delegate for obtaining data from default shared preferences. */
fun Context.preferences() =
    SharedPreferencesProvider.ContextDef(
        this
    )

/** Delegate for obtaining data from shared preferences. */
fun Context.preferences(key: String, mode: Int = 0) =
    SharedPreferencesProvider.Context(
        this,
        key,
        mode
    )

/** Delegate for obtaining data from default shared preferences. */
fun Fragment.preferences() =
    SharedPreferencesProvider.FragmentDef(
        this
    )

/** Delegate for obtaining data from shared preferences. */
fun Fragment.preferences(key: String, mode: Int = 0) =
    SharedPreferencesProvider.Fragment(
        this,
        key,
        mode
    )

/** Delegate for obtaining data from default shared preferences. */
fun View.preferences() =
    SharedPreferencesProvider.ViewDef(
        this
    )

/** Delegate for obtaining data from shared preferences. */
fun View.preferences(key: String, mode: Int = 0) =
    SharedPreferencesProvider.View(
        this,
        key,
        mode
    )

/** Edit with auto [apply] after block. */
inline fun SharedPreferences.edit(edit: SharedPreferences.Editor.() -> Unit) =
    edit().apply(edit).apply()

private typealias SP = SharedPreferences
private typealias SPEditor = SharedPreferences.Editor


/** Provider of preference manager and delegate factory. */
sealed class SharedPreferencesProvider : ReadOnlyProperty<Any, SharedPreferences> {
    final override fun getValue(thisRef: Any, property: KProperty<*>) = get()
    abstract fun get(): SharedPreferences

    class Direct(private val prefs: SharedPreferences) : SharedPreferencesProvider() {
        override fun get() = prefs
    }

    class Context(
        private val context: android.content.Context,
        val preferencesKey: String,
        val mode: Int
    ) : SharedPreferencesProvider() {
        override fun get(): SharedPreferences = context.getSharedPreferences(preferencesKey, mode)
    }

    class ContextDef(private val context: android.content.Context) : SharedPreferencesProvider() {
        override fun get() = context.defaultPrefs
    }

    class Fragment(
        private val fragment: androidx.fragment.app.Fragment,
        val preferencesKey: String,
        val mode: Int
    ) : SharedPreferencesProvider() {
        override fun get(): SharedPreferences =
            fragment.requireContext().getSharedPreferences(preferencesKey, mode)
    }

    class FragmentDef(private val fragment: androidx.fragment.app.Fragment) :
        SharedPreferencesProvider() {
        override fun get() = fragment.requireContext().defaultPrefs
    }

    class View(
        private val view: android.view.View,
        val preferencesKey: String? = null,
        val mode: Int = 0
    ) : SharedPreferencesProvider() {
        override fun get() =
            preferencesKey?.let { view.context.getSharedPreferences(preferencesKey, mode) }
                ?: view.context.defaultPrefs
    }

    class ViewDef(private val view: android.view.View) : SharedPreferencesProvider() {
        override fun get() = view.context.defaultPrefs
    }

    /** Boolean preference. [setListener] can be run before changing value in preferences.*/
    fun boolean(
        key: String,
        default: Boolean,
        setListener: ((Boolean) -> Unit)? = null
    ) = RWBooleanPref(
        this,
        key,
        default,
        setListener
    )

    /** Long preference. [setListener] can be run before changing value in preferences.*/
    fun long(key: String, default: Long, setListener: ((Long) -> Unit)? = null) =
        RWLongPref(
            this,
            key,
            default,
            setListener
        )

    /** Int preference. [setListener] can be run before changing value in preferences.*/
    fun int(key: String, default: Int, setListener: ((Int) -> Unit)? = null) =
        RWIntPref(
            this,
            key,
            default,
            setListener
        )

    /** Float preference. [setListener] can be run before changing value in preferences.*/
    fun float(key: String, default: Float, setListener: ((Float) -> Unit)? = null) =
        RWFloatPref(
            this,
            key,
            default,
            setListener
        )

    /** String preference. [setListener] can be run before changing value in preferences.*/
    fun string(key: String, default: String, setListener: ((String) -> Unit)? = null) =
        RWStringPref(
            this,
            key,
            default,
            setListener
        )

    /** String set preference. If [default] is not set returns empty set by default. [setListener] can be run before changing value in preferences.*/
    fun stringSet(
        key: String,
        default: Set<String> = emptySet(),
        setListener: ((Set<String>) -> Unit)? = null
    ) = RWStringSetPref(
        this,
        key,
        default,
        setListener
    )

    /** Enum preference - mapped to ENUM NAME string. [setBlock] can be run before changing the value in preferences.*/
    inline fun <reified T : Enum<T>> enum(
        key: String,
        default: T,
        noinline setBlock: ((T) -> Unit)? = null
    ) = RWEnumPref(
        this,
        key,
        default,
        T::class.java,
        setBlock
    )
}

/*
    Backing classes.
 */

/** Base for preference property accessor. */
abstract class RWPref<T>(
    val prefsProvider: SharedPreferencesProvider,
    val key: String,
    val default: T,
    val setBlock: ((T) -> Unit)? = null
) : ReadWriteProperty<Any, T> {
    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        setBlock?.invoke(value)
        prefsProvider.get().edit { set(key, value) }
    }

    override fun getValue(thisRef: Any, property: KProperty<*>): T =
        prefsProvider.get().get(key, default)

    protected abstract fun SPEditor.set(key: String, value: T): SPEditor
    protected abstract fun SP.get(key: String, default: T): T
}

/** Boolean preference. [setBlock] can be run before changing value in preferences.*/
class RWBooleanPref(
    prefsProvider: SharedPreferencesProvider,
    key: String,
    default: Boolean,
    setBlock: ((Boolean) -> Unit)? = null
) : RWPref<Boolean>(prefsProvider, key, default, setBlock) {
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
        val enum = enumClass.enumConstants.find { it.name == s }
        return enum ?: default
    }
}