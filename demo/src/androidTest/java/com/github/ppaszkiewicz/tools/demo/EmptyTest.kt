package com.github.ppaszkiewicz.tools.demo

import androidx.test.runner.AndroidJUnit4
import com.github.ppaszkiewicz.tools.coroutines.asWeakRef
import com.github.ppaszkiewicz.tools.coroutines.impl.FifoCoroutineLoader
import com.github.ppaszkiewicz.tools.coroutines.impl.FifoCoroutineLoaderTask
import com.github.ppaszkiewicz.tools.coroutines.loader.CoroutineLoaderCancellation
import com.github.ppaszkiewicz.tools.coroutines.loader.CoroutineLoaderError
import com.github.ppaszkiewicz.tools.coroutines.loader.CoroutineLoaderProgress
import com.github.ppaszkiewicz.tools.coroutines.loader.CoroutineLoaderResult
import kotlinx.coroutines.delay
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Exception

@RunWith(AndroidJUnit4::class)
class EmptyTest{

    @Test
    fun testExecution() {
        assert(true)
    }
}