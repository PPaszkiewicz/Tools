package com.github.ppaszkiewicz.tools.coroutines.loader

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.android.HandlerDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher

/** Container of all async dispatchers. Allows sharing threads between loaders. */
interface CoroutineLoaderDispatcherProvider{
    /**
     * Master scope of coroutines - this should always be a wrapper of [SupervisorJob]
     * to let jobs run independently.
     * */
    val scope: CoroutineScope
    /**
     * Multithread context for user implementation of loading.
     **/
    val background : ExecutorCoroutineDispatcher
    /** Callback context. */
    val ui : HandlerDispatcher
    /** Always single threaded to ensure cache and ongoing job list is synchronized. */
    val sync : ExecutorCoroutineDispatcher

    /** Destroy dispatchers. */
    fun release()
}

/** Default container of all async dispatchers. */
class DefaultCoroutineLoaderDispatcherProvider(
    bgThreadCount : Int = CoroutineLoader.FIXED_COROUTINE_CONTEXT_THREAD_COUNT,
    tag: String = "DefaultCoroutineLoaderDispatcherProvider")
    : CoroutineLoaderDispatcherProvider {
    override val scope = CoroutineScope(SupervisorJob())
    override val background = newFixedThreadPoolContext(bgThreadCount, "$tag - fixedContext")
    override val ui = Handler(Looper.getMainLooper()).asCoroutineDispatcher("$tag - UIContext")
    override val sync  = newFixedThreadPoolContext(1, "$tag - SyncContext")

    var isActive = true
        private set

    override fun release() {
        if(isActive){
            background.close()
            sync.close()
            isActive = false
        }
    }
}