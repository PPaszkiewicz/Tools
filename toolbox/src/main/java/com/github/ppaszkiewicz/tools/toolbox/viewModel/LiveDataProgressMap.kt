package com.github.ppaszkiewicz.tools.toolbox.viewModel

import androidx.annotation.MainThread
import androidx.lifecycle.LiveData

/*
    Requires LiveDataMap.kt
 */

/**
 * Map of LiveData for tracking list of values that emit progress.
 * */
open class LiveDataProgressMap<KEY, PROGRESS, RESULT> : LiveDataMap<KEY, LoadableData<PROGRESS, RESULT>>() {

    // implement creation
    override fun createNewLiveData(key: KEY) = LoadableLiveData<PROGRESS, RESULT>()

    // cast get() to our custom type of livedata
    override operator fun get(key: KEY) : LoadableLiveData<PROGRESS, RESULT> =
        super.get(key) as LoadableLiveData<PROGRESS, RESULT>

    /** Set value of given liveData. */
    @MainThread
    fun setProgress(key: KEY, progress: PROGRESS) = get(key).setProgress(progress)

    /** Set value of given liveData. */
    @MainThread
    fun setResult(key: KEY, result: RESULT) = get(key).setResult(result)

    /** Set value of given liveData. */
    @MainThread
    fun setError(key: KEY, error: Throwable)  = get(key).setError(error)

    /** Thread safe [setProgress].*/
    fun postProgress(key: KEY, progress: PROGRESS) = post{setProgress(key, progress)}

    /** Thread safe [setResult].*/
    fun postResult(key: KEY, result: RESULT) = post{setResult(key, result)}

    /** Thread safe [setError].*/
    fun postError(key: KEY, error: Throwable) = post{setError(key, error)}
}

/** LiveData serving same but modified [LoadableData] object. */
open class LoadableLiveData<PROGRESS, RESULT> : LiveData<LoadableData<PROGRESS, RESULT>>(){
    init {
        value = LoadableData()
    }

    fun setProgress(progress: PROGRESS){
        value!!.let {
            it.progress = progress
            value = it
        }
    }

    fun setResult(result: RESULT){
        value!!.let {
            it.result = result
            it.progress = null
            value = it
        }
    }

    fun setError(error: Throwable){
        value!!.let {
            it.error =error
            value = it
        }
    }
}

/** Data that is loaded with explicit progress. */
class LoadableData<PROGRESS, RESULT>{
    /** Result of loading. If this is set [progress] is null.*/
    var result: RESULT? = null
        internal set
    /** Loading progress. If [result] is set, this is set to null. If this is null as well loading was not initialized. */
    var progress: PROGRESS? = null
        internal set
    /** Exception or cancellation thrown while loading. */
    var error: Throwable? = null
        internal set

    fun isSuccesfull() = result != null
}