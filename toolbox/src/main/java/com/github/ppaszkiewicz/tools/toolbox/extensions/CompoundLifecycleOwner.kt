package com.github.ppaszkiewicz.tools.toolbox.extensions

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * [LifecycleOwner] that combines state of multiple [lifecycles].
 *
 * Available modes are:
 * - [Mode.AND] _(default)_ assume the lowest state
 * - [Mode.OR] assume the highest state
 */
open class CompoundLifecycleOwner(
    vararg val lifecycles: LifecycleOwner,
    val mode: Mode = Mode.AND
) : LifecycleOwner {
    enum class Mode {
        /** `RESUMED` when everything is resumed, `DESTROYED` when at least one is. */
        AND,

        /** `RESUMED` when at least one is resumed, `DESTROYED` when everything is. */
        OR
    }

    @Suppress("LeakingThis")
    private val _lifeCycle = LifecycleRegistry(this)
    override fun getLifecycle() = _lifeCycle

    private val stateObs = createObserver()

    init {
        require(lifecycles.isNotEmpty())
        lifecycles.forEach { it.lifecycle.addObserver(stateObs) }
    }

    private fun createObserver() = when (mode) {
        Mode.AND -> LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_DESTROY) destroy()
            else _lifeCycle.currentState =
                lifecycles.minBy { lifecycle.currentState }!!.lifecycle.currentState
        }
        Mode.OR -> LifecycleEventObserver { src, ev ->
            _lifeCycle.currentState =
                lifecycles.maxBy { lifecycle.currentState }!!.lifecycle.currentState
        }
    }

    private fun destroy() {
        lifecycles.forEach { it.lifecycle.removeObserver(stateObs) }
        _lifeCycle.currentState = Lifecycle.State.DESTROYED
    }
}

/** Alias for construction of [CompoundLifecycleOwner] in [CompoundLifecycleOwner.Mode.AND]. */
operator fun LifecycleOwner.plus(other: LifecycleOwner) = CompoundLifecycleOwner(this, other)

/** Alias for construction of [CompoundLifecycleOwner] in [CompoundLifecycleOwner.Mode.AND]. */
infix fun LifecycleOwner.and(other: LifecycleOwner) = this + other

/** Alias for construction of [CompoundLifecycleOwner] in [CompoundLifecycleOwner.Mode.OR]. */
infix fun LifecycleOwner.or(other: LifecycleOwner) =
    CompoundLifecycleOwner(this, other, mode = CompoundLifecycleOwner.Mode.OR)