package com.github.ppaszkiewicz.tools.coroutines.impl

import com.github.ppaszkiewicz.tools.coroutines.FifoMap
import com.github.ppaszkiewicz.tools.coroutines.asFifoMap
import com.github.ppaszkiewicz.tools.coroutines.loader.CoroutineLoaderDispatcherProvider
import com.github.ppaszkiewicz.tools.coroutines.loader.CoroutineLoaderTask
import com.github.ppaszkiewicz.tools.coroutines.loader.DefaultCoroutineLoaderDispatcherProvider
import kotlinx.coroutines.launch

/**
 * CoroutineLoader that uses [DefaultCoroutineLoader] base and Fifo map for cache.
 */
abstract class FifoCoroutineLoader<Q : Any, R : Any> @JvmOverloads constructor(
    /** Internal memory cache size.*/
        val cacheSize: Int = DEFAULT_CACHE_SIZE,
    /**
         * True to generate soft copy of cache that will be queried from UI thread.
         * That cache is not perfectly synchronized, but allows cache hits to return in-line.
         * */
        val useUiCache: Boolean = false,
    coroutineLoaderDispatcherProvider: CoroutineLoaderDispatcherProvider = DefaultCoroutineLoaderDispatcherProvider(
        FIXED_COROUTINE_CONTEXT_THREAD_COUNT
    )
)
    : DefaultCoroutineLoader<Q, R>(coroutineLoaderDispatcherProvider) {
    companion object {
        const val DEFAULT_CACHE_SIZE = 20
    }

    /** Hard cache (maintained by [sync]). Overrideable. */
    open val cache by lazy{ initCache() }
    /** Soft cache (shallow copied during result). */
    var softCache: HashMap<Q, R>? = null
    /** This method is called to create or get cache for the first time. */
    open fun initCache() : FifoMap<Q, R> {
        return HashMap<Q, R>(cacheSize).asFifoMap()
    }

    abstract override fun createTask(key: Q, params: Any?): FifoCoroutineLoaderTask<Q, R>
}

/**
 * Task created by [FifoCoroutineLoader]. Handles cache callbacks as default.
 *
 * Lower [suppressClearResultFromCache] flag to keep cached items until cache is full.
 */
abstract class FifoCoroutineLoaderTask<Q : Any, R : Any> : CoroutineLoaderTask<Q, R>() {
    val fifoLoader: FifoCoroutineLoader<Q, R>
        get() = loader as FifoCoroutineLoader<Q, R>

    /**
     * Don't remove result from cache when [clearResultFromCache] is called -
     * that way it only gets removed when cache is full.
     *
     * By default this is **true**.
     * */
    var suppressClearResultFromCache = true

    override fun preExecute(key: Q, params: Any?): R? {
        return if(fifoLoader.useUiCache) {
            val cacheItem = fifoLoader.softCache?.get(key)
            if(cacheItem != null){
                // need to notify real cache to move item forward
                fifoLoader.scope.launch(fifoLoader.sync){
                    fifoLoader.cache.pushKeyInHistory(key)
                }
            }
            cacheItem
        }else
                null
    }

    override suspend fun queryCache(key: Q, params: Any?) = fifoLoader.cache[key]

    override suspend fun cacheResult(key: Q, params: Any?, result: R) {
        fifoLoader.apply {
            cache[key] = result
            val removedItems = cache.keepLast(cacheSize, cacheSize/5)   // purge caches over size with 20% tolerance
            if(useUiCache){
                //todo: optimize disk cache?
                softCache = HashMap(cache)
            }
            onCacheCleared(removedItems)
        }
    }

    override suspend fun clearResultFromCache(key: Q, params: Any?) {
        if(!suppressClearResultFromCache)
            fifoLoader.cache.remove(key)
    }

    /** Triggered when some items are cleared from the cache. Ran on sync thread. */
    open fun onCacheCleared(removedItems: List<R>){

    }
}