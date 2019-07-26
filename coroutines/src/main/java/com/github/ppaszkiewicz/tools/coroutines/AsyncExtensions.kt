package com.github.ppaszkiewicz.tools.coroutines

import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException

/** Weak reference wrapper. [get] throws [RefLostCancellationException] if reference is lost. */
class WeakRef<out T : Any> internal constructor(obj: T) : ObjectRef<T> {
    private val weakRef = WeakReference(obj)
    override fun get() = weakRef.get() ?: throw RefLostCancellationException()
    override fun clear() = weakRef.clear()
}

/** Hard reference wrapper. [get] never fails until [clear] is called. */
class HardRef<out T : Any> internal constructor(private var obj: T?) :
    ObjectRef<T> {
    override fun get() = obj ?: throw RefLostCancellationException()
    override fun clear() {
        obj = null
    }
}

/** Always returns [Unit] object. Placeholder to satisfy [ObjectRef] interface when references are not needed.  */
object UnitRef : ObjectRef<Unit> {
    override fun get() = Unit
    override fun clear() = Unit
}

/** Wrapped reference to a single object. */
interface ObjectRef<out T : Any> {
    /** Obtain wrapped object reference. */
    fun get(): T

    /** Clear wrapped reference. */
    fun clear()
}

/** Thrown explicitly from [WeakRef.get]. */
class RefLostCancellationException(message: String? = null) : CancellationException(message)

/** Wrap this object with weak reference that will interrupt ongoing coroutine if accessed after cleanup. */
fun <T : Any> T.asWeakRef() = WeakRef(this)

/** Wrap this object with hard reference. This is optional instead of [asWeakRef] */
fun <T : Any> T.asHardRef() = HardRef(this)