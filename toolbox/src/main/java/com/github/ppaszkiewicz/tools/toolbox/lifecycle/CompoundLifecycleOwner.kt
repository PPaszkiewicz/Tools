package com.github.ppaszkiewicz.tools.toolbox.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.*
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * [LifecycleOwner] that combines state of multiple [lifecycles].
 *
 * Available modes are:
 * - [CompoundLifecycleOwner.And] assume the lowest state
 * - [CompoundLifecycleOwner.Or] assume the highest state
 */
abstract class CompoundLifecycleOwner(vararg lifecycles: LifecycleOwner) : LifecycleOwner,
    LifecycleEventObserver {
    init {
        require(lifecycles.isNotEmpty())
    }

    /** List of lifecycles, once any of them reach destroyed state it should be discarded. */
    protected val lifecycles = lifecycles.toMutableList()

    /** `True` until lifecycle is prepared. */
    protected var isInitializing = true
        private set

    private val _lifecycle by lazy {
        LifecycleRegistry(this).also {
            initLifecycle(it)
            isInitializing = false
        }
    }

    override fun getLifecycle() = _lifecycle

    /**
     * Called when lazily creating [lifecycle], attach the observers here.
     *
     * This is the only method where this lifecycle can shut down and move
     * immediately from [INITIALIZED] to [DESTROYED].
     *  */
    abstract fun initLifecycle(lifecycle: LifecycleRegistry)

    /** Clean up observers and move down to [DESTROYED] state if possible (not [INITIALIZED]). */
    protected fun destroy() {
        check(lifecycle.currentState != DESTROYED)
        lifecycles.forEach { it.lifecycle.removeObserver(this) }
        lifecycles.clear()
        // cannot move down from INITIALIZED so just leave lifecycle there
        if (lifecycle.currentState.isAtLeast(CREATED))
            lifecycle.currentState = DESTROYED
    }

    /** `RESUMED` when everything is resumed, `DESTROYED` when at least one is. */
    open class And(vararg lifecycles: LifecycleOwner) : CompoundLifecycleOwner(*lifecycles) {

        override fun initLifecycle(lifecycle: LifecycleRegistry) {
            lifecycle.currentState = lifecycles.minOf { it.lifecycle.currentState }
            // if any of provided lifecycles is destroyed just destroy the compound immediately
            if (lifecycle.currentState == DESTROYED) lifecycles.clear()
            else lifecycles.forEach { it.lifecycle.addObserver(this) }
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (isInitializing) return   // suppress callbacks triggered by addObserver
            val state = lifecycles.minOf { it.lifecycle.currentState }
            if (state == DESTROYED) destroy()
            else lifecycle.currentState = state
        }

        // builder operators allowing for chaining
        operator fun plus(another: LifecycleOwner) = And(*lifecycles.toTypedArray(), another)
        infix fun and(another: LifecycleOwner) = this + another
    }

    /** `RESUMED` when at least one is resumed, `DESTROYED` when everything is. */
    open class Or(vararg lifecycles: LifecycleOwner) : CompoundLifecycleOwner(*lifecycles),
        LifecycleEventObserver {
        override fun initLifecycle(lifecycle: LifecycleRegistry) {
            lifecycle.currentState = lifecycles.maxOf { it.lifecycle.currentState }
            // if all of provided lifecycles are destroyed just destroy the compound immediately
            if (lifecycle.currentState == DESTROYED) lifecycles.clear()
            else lifecycles.toTypedArray().forEach {
                if (it.lifecycle.currentState == DESTROYED) {
                    lifecycles.remove(it)
                } else it.lifecycle.addObserver(this)
            }
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (isInitializing) return // suppress callbacks triggered by addObserver
            if (source.lifecycle.currentState == DESTROYED) {
                source.lifecycle.removeObserver(this)
                lifecycles.remove(source)
                if (lifecycles.isEmpty()) {
                    destroy()
                    return
                }
            }
            val maxState = lifecycles.maxOf { it.lifecycle.currentState } // will never be DESTROYED

            // doing direct INITIALIZED -> DESTROYED state change is illegal so don't even account for it
            if (maxState == INITIALIZED && lifecycle.currentState > INITIALIZED) {
                lifecycle.currentState = CREATED    // clamp to CREATED
            } else lifecycle.currentState = maxState
        }

        // builder operators allowing for chaining
        infix fun or(another: LifecycleOwner) = Or(*lifecycles.toTypedArray(), another)
    }
}

/** Alias for construction of [CompoundLifecycleOwner.And]. */
operator fun LifecycleOwner.plus(other: LifecycleOwner) =
    CompoundLifecycleOwner.And(this, other)

/** Alias for construction of [CompoundLifecycleOwner.And]. */
infix fun LifecycleOwner.and(other: LifecycleOwner) = this + other

/** Alias for construction of [CompoundLifecycleOwner.Or]. */
infix fun LifecycleOwner.or(other: LifecycleOwner) =
    CompoundLifecycleOwner.Or(this, other)