package com.github.ppaszkiewicz.tools.toolbox.viewModel
//
//import androidx.lifecycle.*
//
// Rough idea that doesn't work properly

///**
// * LiveData that can have its updates paused until all other LiveDatas that share [sync]
// * are updated.
// * */
//fun <T> LiveData<T>.syncedOn(sync: LiveDataSync, distinctOnly: Boolean = false): LiveData<T> {
//    return SyncableLiveData(this, sync, distinctOnly)
//}
//
///**
// * LiveData that can have its updates paused until all other [SyncableLiveData] with same [sync]
// * are updated.
// * */
//private class SyncableLiveData<T>(
//    source: LiveData<T>,
//    /** Sync object that must be shared between other sync instances. */
//    sync: LiveDataSync,
//    /** If true this will only emit distinct changes. */
//    val distinctOnly: Boolean = false
//) : LiveData<T>(), Observer<T> {
//    private var firstTime = true
//    private val syncHook = sync.Hook()
//
//    init {
//        source.observe(syncHook, this)
//    }
//
//    override fun onChanged(t: T) {
//        when {
//            !distinctOnly -> value = t
//            firstTime || value != t -> {
//                firstTime = false
//                value = t
//            }
//        }
//    }
//
//    override fun onActive() {
//        syncHook.resume()
//    }
//
//    override fun onInactive() {
//        syncHook.pause()
//    }
//}
//
///**
// * Lifecycle owner that can be manually paused to sync multiple instances of [SyncableLiveData].
// *
// * Calling [destroy] will detach all synchronized livedatas from their sources.
// * */
//class LiveDataSync : LifecycleOwner {
//    private val registry = LifecycleRegistry(this)
//    override fun getLifecycle() = registry
//
//    init {
//        resume()
//    }
//
//    fun pause() {
//        check(registry.currentState == Lifecycle.State.RESUMED) { "Missed resume call" }
//        check(registry.currentState != Lifecycle.State.DESTROYED)
//        registry.currentState = Lifecycle.State.CREATED
//    }
//
//    fun resume() {
//        check(registry.currentState != Lifecycle.State.DESTROYED)
//        registry.currentState = Lifecycle.State.RESUMED
//    }
//
//    fun destroy(){
//        check(registry.currentState != Lifecycle.State.DESTROYED)
//        registry.currentState = Lifecycle.State.DESTROYED
//    }
//
//    /** Run [block] surrounded with [pause] and [resume]. */
//    inline fun syncUpdated(block: () -> Unit) {
//        pause()
//        block()
//        resume()
//    }
//
//    /** Nested lifecycle hook based on this sync object. */
//    internal inner class Hook : LifecycleOwner, LifecycleObserver {
//        private val hookRegistry = LifecycleRegistry(this)
//        override fun getLifecycle() = hookRegistry
//
//        // resumed IF parent lifecycle is resumed, otherwise started
//        private var shouldBeResumed = true
//
//        init {
//            registry.addObserver(this@Hook)
//        }
//
//        fun pause() {
//            shouldBeResumed = false
//            invalidateState()
//        }
//
//        fun resume() {
//            shouldBeResumed = true
//            invalidateState()
//        }
//
//        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
//        fun onParentStopped() {
//            invalidateState()
//        }
//
//        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
//        fun onParentResumed() {
//            invalidateState()
//        }
//
//        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
//        fun onParentDestroyed(){
//            invalidateState()
//        }
//
//        private fun invalidateState() {
//            hookRegistry.currentState =
//                when {
//                    registry.currentState == Lifecycle.State.DESTROYED -> {
//                        Lifecycle.State.DESTROYED
//                    }
//                    shouldBeResumed && registry.currentState == Lifecycle.State.RESUMED -> {
//                        Lifecycle.State.RESUMED
//                    }
//                    else -> Lifecycle.State.CREATED
//                }
//        }
//    }
//}