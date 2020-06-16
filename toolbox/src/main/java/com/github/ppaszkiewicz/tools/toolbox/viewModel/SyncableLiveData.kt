package com.github.ppaszkiewicz.tools.toolbox.viewModel

import androidx.lifecycle.*

// Rough idea that doesn't work properly

/**
 * LiveData that can have its updates paused until all other LiveDatas that share [sync]
 * are updated.
 * */
fun <T> LiveData<T>.syncedOn(sync: LiveDataSync, distinctOnly: Boolean = false): LiveData<T> {
    return SyncableLiveData(this, sync, distinctOnly)
}

/**
 * New LiveData that can have its updates paused until all other LiveDatas that share this sync object
 * are updated.
 * */
fun <T> LiveDataSync.createFrom(source: LiveData<T>, distinctOnly: Boolean = false) : LiveData<T>{
    return SyncableLiveData(source, this, distinctOnly)
}

/**
 * LiveData that can have its updates paused until all other [SyncableLiveData] with same [sync]
 * are updated.
 * */
private class SyncableLiveData<T>(
    source: LiveData<T>,
    /** Sync object that must be shared between other sync instances. */
    val sync: LiveDataSync,
    /** If true this will only emit distinct changes. */
    val distinctOnly: Boolean = false
) : MediatorLiveData<T>(), Observer<T>, LifecycleObserver {
    private var firstTime = true

    // temporary field that in case of pause is returned from getValue before
    // observers are notified of change
    private var tempValue : T? = null
    private var changedDuringPause = false

    init {
        addSource(source, ::onChanged)
        sync.lifecycle.addObserver(this)
    }

    override fun onChanged(t: T?) {
        if (!distinctOnly || firstTime || value != t) {
            firstTime = false
            value = t
        }
    }

    override fun onActive() {
        super.onActive()
        sync.lifecycle.addObserver(this)
    }

    override fun onInactive() {
        super.onInactive()
        sync.lifecycle.removeObserver(this)
    }

    override fun getValue(): T? {
        return tempValue ?: super.getValue()
    }

    override fun setValue(value: T?) {
        if(sync.isPaused) {
            changedDuringPause = true
            tempValue = value
        } else {
            changedDuringPause = false
            tempValue = null
            super.setValue(value)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onSyncResumed(){
        if(changedDuringPause) value = tempValue
    }
}

/**
 * Lifecycle owner that can be manually paused to sync multiple instances of [SyncableLiveData].
 *
 * Calling [destroy] will detach all synchronized livedatas from their sources.
 * */
class LiveDataSync : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override fun getLifecycle() = registry

    val isPaused
        get() = registry.currentState == Lifecycle.State.CREATED

    init {
        resume()
    }

    fun pause() {
        check(registry.currentState == Lifecycle.State.RESUMED) { "Missed resume call" }
        check(registry.currentState != Lifecycle.State.DESTROYED)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        check(registry.currentState != Lifecycle.State.DESTROYED)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy(){
        check(registry.currentState != Lifecycle.State.DESTROYED)
        registry.currentState = Lifecycle.State.DESTROYED
    }

    /** Run [block] surrounded with [pause] and [resume]. */
    inline fun runWithSync(block: () -> Unit) {
        pause()
        block()
        resume()
    }
}