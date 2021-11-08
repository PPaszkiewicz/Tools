package com.github.ppaszkiewicz.tools.demo

import androidx.lifecycle.MutableLiveData
import androidx.test.filters.SmallTest
import com.github.ppaszkiewicz.tools.toolbox.extensions.awaitValue
import com.github.ppaszkiewicz.tools.toolbox.extensions.awaitValueForever
import com.github.ppaszkiewicz.tools.toolbox.extensions.awaitNull
import com.github.ppaszkiewicz.tools.toolbox.extensions.awaitValueOrNull
import kotlinx.coroutines.*
import org.junit.Test

@SmallTest
class SuspendLiveDataTest {

    @Test
    fun testExecution() = runBlocking {
        val ld = MutableLiveData<TestData?>()
        val ld2 = MutableLiveData<TestData>(TestData(99))
        val ld3 = MutableLiveData<TestData?>(null)
        val ld4 = MutableLiveData<TestData>(TestData(9))
        val ld5 = MutableLiveData<TestData?>(TestData(0))

        GlobalScope.launch(Dispatchers.Main) {
            delay(500)
            println("setting value y")
            ld.value = null
        }

        println("waiting for value y...")
        // wait for value to be set to null since unitialized livedata doesn't count
        val y = ld.awaitNull()

        GlobalScope.launch(Dispatchers.Main) {
            delay(500)
            println("setting value z")
            ld2.value = TestData(55)
        }

        println("waiting for value z...")
        val z = ld2.awaitValue { it.x == 55 }    // wait for condition

        GlobalScope.launch(Dispatchers.Main) {
            delay(500)
            println("setting value w")
            ld3.value = TestData(10)
        }

        println("waiting for value w...")
        val w = ld3.awaitValue()    // wait for non-null

        println("waiting for value p...")
        val p = ld4.awaitValueForever() // already initalized so there should be no suspension

        println("waiting for value n...")
        val n = ld5.awaitNull() // value is not null and won't be set, this will cause timeout

        println("test finished with y=$y, z=$z, w=$w, p=$p, n=$n")
        assert(y)
        assert(z.x == 55)
        assert(w != null)
        assert(p != null)
        assert(!n)
    }

    @Test
    fun testCancellation() = runBlocking {
        val ld = MutableLiveData<TestData>()
        val job = async{ ld.awaitValueOrNull() }
        delay(300)
        job.cancel()
        val result = try {
            job.await()
        }catch (exception : CancellationException){
            exception
        }
        println("cancellation result: $result")
    }

    @Test
    fun testTimeout() = runBlocking {
        val ld = MutableLiveData<TestData>()
        val job = async{ ld.awaitValueOrNull() }
        delay(1200)
        val result = try {
            job.await()
        }catch (exception : TimeoutCancellationException){
            exception
        }
        println("timeout result: $result")
    }

    // see what happens if coroutine is cancelled/times out before it can attach an observer
    @Test
    fun testMainThreadClog() = runBlocking {
        val ld = object : MutableLiveData<TestData>(){
            override fun onActive() {
                println("observer got attached")
            }
        }
        val clogJob = launch(Dispatchers.Main){
            println("clogging up main thread")
            Thread.sleep(1200)  // if clog is longer than 1000 ms await will timeout
            ld.value = TestData(123)
        }
        val jobToCancel = async{ ld.awaitValueOrNull() }
        val result = try {
            jobToCancel.await()
        }catch (exception : TimeoutCancellationException){
            exception
        }
        clogJob.join()
        println("clog result: $result")
    }

    data class TestData(val x: Int)
}