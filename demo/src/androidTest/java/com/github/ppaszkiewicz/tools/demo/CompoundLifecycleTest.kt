package com.github.ppaszkiewicz.tools.demo

import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.Lifecycle.State.*
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SmallTest
import com.github.ppaszkiewicz.tools.toolbox.lifecycle.or
import com.github.ppaszkiewicz.tools.toolbox.lifecycle.plus
import org.junit.Test

@SmallTest
@UiThreadTest
class CompoundLifecycleTest {

    @Test
    fun testBackward(){
        val first = DummyLife("first")
//        first.lifecycle.addObserver(LifecycleEventObserver {_, ev ->
//            Log.d("TEST","observer: $ev")
//        })
        first.moveTo(DESTROYED) // this fails if theres an observer
    }

    /** two initialized lifecycles move through their state */
    @Test
    fun testCycle(){
        val first = DummyLife("first")
        val second = DummyLife("second")
        val compound = first + second
        compound.lifecycle.addObserver(LifecycleEventObserver {_, ev ->
            Log.d("TEST","observer: $ev")
        })
        first.moveTo(RESUMED)
        second.moveTo(CREATED)
        second.moveTo(STARTED)
        second.moveTo(RESUMED)
        second.moveTo(DESTROYED)
        first.moveTo(DESTROYED)
    }

    /** One existing lifecycle moves through its state */
    @Test
    fun testDestroyed(){
        val first = DummyLife("first")
        val second = DummyLife("second")
        first.moveTo(CREATED)
        val compound = first + second
        compound.lifecycle.addObserver(LifecycleEventObserver {_, ev ->
            Log.d("TEST","observer: $ev")
        })
        first.moveTo(DESTROYED)
        Log.d("TEST", "compound state: ${compound.lifecycle.currentState}")
    }

    /** two initialized lifecycles move through their state but using OR compound*/
    @Test
    fun testOrCycle(){
        val first = DummyLife("first")
        val second = DummyLife("second")
        val compound = first or second
        compound.lifecycle.addObserver(LifecycleEventObserver {_, ev ->
            Log.d("TEST","observer: $ev")
        })
        first.moveTo(RESUMED)
        second.moveTo(CREATED)
        second.moveTo(STARTED)
        second.moveTo(RESUMED)
        second.moveTo(DESTROYED)
        first.moveTo(DESTROYED)
    }

    /** One existing lifecycle moves through its state but using OR compound */
    @Test
    fun testOrDestroyed(){
        val first = DummyLife("first")
        val second = DummyLife("second")
        first.moveTo(CREATED)
        val compound = first or second
        compound.lifecycle.addObserver(LifecycleEventObserver {_, ev ->
            Log.d("TEST","observer: $ev")
        })
        first.moveTo(RESUMED)
        first.moveTo(DESTROYED)
        second.moveTo(CREATED)
        second.moveTo(DESTROYED)
        Log.d("TEST", "compound state: ${compound.lifecycle.currentState}")
    }

    class DummyLife(val tag :String) : LifecycleOwner{
        val registry = LifecycleRegistry(this)
        override fun getLifecycle(): Lifecycle = registry

        fun moveTo(state: Lifecycle.State){
            Log.d("TEST","$tag: ${lifecycle.currentState} -> $state")
            registry.currentState = state
        }
    }
}