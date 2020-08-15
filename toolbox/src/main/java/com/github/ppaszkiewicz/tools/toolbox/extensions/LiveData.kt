package com.github.ppaszkiewicz.tools.toolbox.extensions

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/** One shot observer that removes itself after one call to [onChanged] or when lifecycle owner is destroyed. */
fun <T> LiveData<T>.observeOneShot(owner: LifecycleOwner, onChanged: (T) -> Unit) {
    val oneShotObserver = object : Observer<T> {
        override fun onChanged(it: T) {
            onChanged(it)
            removeObserver(this)
        }
    }
    observe(owner, oneShotObserver)
}

/** Observer that removes itself after [consumeOnChanged] returns `true` or when lifecycle owner is destroyed. */
fun <T> LiveData<T>.observeUntil(owner: LifecycleOwner, consumeOnChanged: (T) -> Boolean) {
    val oneShotObserver = object : Observer<T> {
        override fun onChanged(it: T) {
            if (consumeOnChanged(it))
                removeObserver(this)
        }
    }
    observe(owner, oneShotObserver)
}