package com.github.ppaszkiewicz.tools.toolbox.lifecycle

import androidx.lifecycle.Lifecycle
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
abstract class CompoundLifecycleOwner(vararg lifecycles: LifecycleOwner) : LifecycleOwner {
    init {
        require(lifecycles.isNotEmpty())
    }

    /** List of lifecycles, once any of them reach destroyed state it should be discarded. */
    protected val lifecycles = lifecycles.toMutableList()

    @Suppress("LeakingThis")
    private val _lifecycle by lazy {
        val lifecycle = LifecycleRegistry(this)
        initLifecycle(lifecycle)
        lifecycle
    }

    override fun getLifecycle() = _lifecycle

    /**
     * Called when lazily creating lifecycle, attach the observers here.
     *
     * [getLifecycle] will throw an exception inside this, use provided [lifecycle] argument if needed.
     *  */
    abstract fun initLifecycle(lifecycle: LifecycleRegistry)

    /** `RESUMED` when everything is resumed, `DESTROYED` when at least one is. */
    open class And(vararg lifecycles: LifecycleOwner) : CompoundLifecycleOwner(*lifecycles),
        LifecycleEventObserver {

        override fun initLifecycle(lifecycle: LifecycleRegistry) {
            // if any of provided lifecycles is destroyed just destroy the compound immediately
            val destroyed =
                lifecycles.find { it.lifecycle.currentState == Lifecycle.State.DESTROYED }
            if (destroyed != null) {
                lifecycle.currentState = Lifecycle.State.DESTROYED
                lifecycles.clear()
            } else lifecycles.forEach { it.lifecycle.addObserver(this) }
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_DESTROY) destroy()
            else lifecycle.currentState = lifecycles.minOf { it.lifecycle.currentState }
        }

        private fun destroy() {
            lifecycles.forEach { it.lifecycle.removeObserver(this) }
            lifecycle.currentState = Lifecycle.State.DESTROYED
            lifecycles.clear()
        }

        // builder operators allowing for chaining
        operator fun plus(another: LifecycleOwner) = And(*lifecycles.toTypedArray(), another)
        infix fun and(another: LifecycleOwner) = this + another
    }

    /** `RESUMED` when at least one is resumed, `DESTROYED` when everything is. */
    open class Or(vararg lifecycles: LifecycleOwner) : CompoundLifecycleOwner(*lifecycles),
        LifecycleEventObserver {
        override fun initLifecycle(lifecycle: LifecycleRegistry) {
            // run iteration on copy because list will be altered if something is in destroyed state
            lifecycles.toTypedArray().forEach { it.lifecycle.addObserver(this) }
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            lifecycle.currentState = lifecycles.maxOf { it.lifecycle.currentState }
            if (event == Lifecycle.Event.ON_DESTROY) {
                source.lifecycle.removeObserver(this)
                lifecycles.remove(source)
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