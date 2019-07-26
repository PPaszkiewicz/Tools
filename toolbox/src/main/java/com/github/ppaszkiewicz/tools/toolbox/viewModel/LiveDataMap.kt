package com.github.ppaszkiewicz.tools.toolbox.viewModel

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.*

/**
 * Map of livedatas.
 *
 * Possible to observe entire map or connect to specific keys.
 * */
abstract class LiveDataMap<K, V> : LiveData<Map<K, LiveData<V>>>() {
    // backing field exposed as observed value
    protected val liveDatas = HashMap<K, LiveData<V>>()
    protected val postJob = SupervisorJob()
    protected val postScope = CoroutineScope(postJob + Dispatchers.Main)

    init {
        value = liveDatas
    }

    /** Connect directly to or create new livedata for [key]. */
    @MainThread
    open fun observeKey(owner: LifecycleOwner, key: K, observer: Observer<V>) {
        get(key).observe(owner, observer)
    }

    /** Iterate over existing livedatas and remove all observers based on [owner]. */
    @MainThread
    open fun removeObserversFromMap(owner: LifecycleOwner) {
        liveDatas.values.forEach { it.removeObservers(owner) }
    }

    /** Entirely clear data for given key from map. Optionally pass lifecycle owner to also remove update observer. */
    @MainThread
    open fun clearObservedDataForKey(key: K, owner: LifecycleOwner? = null) {
        val ld = liveDatas[key] ?: return
        owner?.let { ld.removeObservers(owner) }
        liveDatas.remove(key)
        value = liveDatas   // notify about the change
    }

    /**
     * Clear map of LiveDatas and remove all observers from [owner]. If [owner] is not the only lifecycleOwner within
     * livedatas, they will stay alive.
     * */
    @MainThread
    open fun clear(owner: LifecycleOwner? = null) {
        postJob.cancelChildren()
        owner?.let {o->
            liveDatas.values.forEach { it.removeObservers(o) }
        }
        liveDatas.clear()
        value = liveDatas
    }

    /** Checks if data is empty. */
    fun isEmpty() = liveDatas.isEmpty()

    /** Protected call to perform updates on UI thread. */
    protected open fun post(update: suspend CoroutineScope.() -> Unit) = postScope.launch(block = update)

    /**
     * Get (or create if missing) livedata for given key.
     *
     * Never returns null.
     * */
    open operator fun get(key: K): LiveData<V> {
        var ld = liveDatas[key]
        if (ld != null) return ld
        ld = createNewLiveData(key)
        liveDatas[key] = ld
        value = liveDatas
        return ld
    }

    /** Instantiate new LiveData for given key. */
    abstract fun createNewLiveData(key: K): LiveData<V>
}