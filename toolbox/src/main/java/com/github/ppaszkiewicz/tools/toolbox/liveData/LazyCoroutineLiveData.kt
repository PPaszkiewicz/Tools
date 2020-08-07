package com.github.ppaszkiewicz.tools.toolbox.liveData

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*

/** Simple liveData that prepares data asynchronously when first observed/invalidated. */
abstract class LazyCoroutineLiveData<T> : LiveData<T>() {
    private val _state = MutableLiveData<State>(State.Idle)

    /** Observable current loading state */
    val stateLiveData: LiveData<State>
        get() = _state

    var state: State
        get() = _state.value!!
        protected set(value) {
            _state.value = value
        }

    private var asyncJob: Job? = null

    /** Suspending block that prepares the data or throws an exception. */
    abstract suspend fun prepareData(scope: CoroutineScope): T

    open fun getAsyncScope() = GlobalScope
    open fun getAsyncDispatcher() = Dispatchers.IO

    /**
     * Invalidate or initialize current value and load new one.
     *
     * @param force start loading now, discards previous value/interrupts current loading if needed
     * */
    fun invalidate(force: Boolean = false) {
        if (force) {  // remove previous loading data
            if (state.isLoading) {
                asyncJob?.cancel()
                asyncJob = null
            }
            if (state.isSuccess) {
                value = null
            }
        } else if (stateIsValid()) return
        state = State.Loading
        asyncJob = getAsyncScope().launch(getAsyncDispatcher()) {
            try {
                postValue(prepareData(this))
                _state.postValue(State.Success)
            } catch (e: Exception) {
                _state.postValue(State.Error(e))
            }
        }
    }

    /** Check if current data is valid or should be reloaded. **/
    protected open fun stateIsValid(): Boolean {
        return state.isLoading || state.isSuccess
    }

    override fun onActive() {
        invalidate()
    }

    sealed class State(
        val isLoading: Boolean,
        val isFinished: Boolean = false,
        val error: Exception? = null
    ) {
        val isSuccess = !isLoading && isFinished && error == null

        object Idle : State(false)
        object Loading : State(true)
        object Success : State(false, true)
        class Error(error: Exception?) : State(false, true, error)
    }
}