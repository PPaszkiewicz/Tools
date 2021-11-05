package com.github.ppaszkiewicz.tools.toolbox.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.DESTROYED
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
@Suppress("LeakingThis")
abstract class CompoundLifecycleOwner(vararg lifecycles: LifecycleOwner) : LifecycleOwner {
    init {
        require(lifecycles.isNotEmpty())
    }

    /** List of lifecycles, once any of them reach destroyed state it should be discarded. */
    protected val lifecycles = lifecycles.toMutableList()

    private val _lifecycle = LifecycleRegistry(this)

    override fun getLifecycle() = _lifecycle

    init {
        initLifecycle(_lifecycle)
    }

    /**
     * Called when lazily creating [lifecycle], attach the observers here.
     *
     * This is the only method where this lifecycle can shut down and move
     * immediately from [INITIALIZED] to [DESTROYED].
     *  */
    abstract fun initLifecycle(lifecycle: LifecycleRegistry)

    /** `RESUMED` when everything is resumed, `DESTROYED` when at least one is. */
    open class And(vararg lifecycles: LifecycleOwner) : CompoundLifecycleOwner(*lifecycles),
        LifecycleEventObserver {

        override fun initLifecycle(lifecycle: LifecycleRegistry) {
            lifecycle.currentState = lifecycles.minOf { it.lifecycle.currentState }
            // if any of provided lifecycles is destroyed just destroy the compound immediately
            if (lifecycle.currentState == DESTROYED) lifecycles.clear()
            else lifecycles.forEach { it.lifecycle.addObserver(this) }
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            val state = lifecycles.minOf { it.lifecycle.currentState }
            if (state == DESTROYED) destroy()
            else lifecycle.currentState = state
        }

        private fun destroy() {
            check(lifecycle.currentState != DESTROYED)
            lifecycles.forEach { it.lifecycle.removeObserver(this) }
            lifecycles.clear()
            // cannot move down from INITIALIZED so just leave lifecycle there
            if (lifecycle.currentState.isAtLeast(CREATED))
                lifecycle.currentState = DESTROYED
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
            if (source.lifecycle.currentState == DESTROYED) {
                source.lifecycle.removeObserver(this)
                lifecycles.remove(source)
                if (lifecycles.isEmpty() && lifecycle.currentState.isAtLeast(CREATED)) {
                    lifecycle.currentState = DESTROYED // last lifecycle was destroyed
                    return
                }
            }
            val maxState = lifecycles.maxOf { it.lifecycle.currentState }

            // note: it's impossible to perform direct INITIALIZED -> DESTROYED state change
            if (maxState.isAtLeast(CREATED)) {
                lifecycle.currentState = maxState   // OK just update the state
            } else if (lifecycle.currentState.isAtLeast(CREATED)) {
                lifecycle.currentState = CREATED    // clamp to CREATED
            }
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