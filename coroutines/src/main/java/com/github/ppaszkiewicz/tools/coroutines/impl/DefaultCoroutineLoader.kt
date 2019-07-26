package com.github.ppaszkiewicz.tools.coroutines.impl

import com.github.ppaszkiewicz.tools.coroutines.loader.CoroutineLoader
import com.github.ppaszkiewicz.tools.coroutines.loader.CoroutineLoaderDispatcherProvider
import com.github.ppaszkiewicz.tools.coroutines.loader.CoroutineLoaderTask
import com.github.ppaszkiewicz.tools.coroutines.loader.DefaultCoroutineLoaderDispatcherProvider

/**
 * Coroutine loader with default handling of ongoing tasks (with a HashMap).
 */
abstract class DefaultCoroutineLoader<Q : Any, R : Any> @JvmOverloads constructor(
        coroutineLoaderDispatcherProvider: CoroutineLoaderDispatcherProvider = DefaultCoroutineLoaderDispatcherProvider(
            FIXED_COROUTINE_CONTEXT_THREAD_COUNT
        )
) : CoroutineLoader<Q, R>(coroutineLoaderDispatcherProvider) {
    val ongoingTasks = HashMap<Q, CoroutineLoaderTask<Q, R>>()

    override suspend fun getTaskForKey(mutatedKey: Q) = ongoingTasks[mutatedKey]

    override suspend fun addTaskToOngoingList(mutatedKey: Q, task: CoroutineLoaderTask<Q, R>) {
        ongoingTasks[mutatedKey] = task
    }

    override suspend fun removeTaskFromOngoingList(mutatedKey: Q, task: CoroutineLoaderTask<Q, R>) {
        ongoingTasks.remove(mutatedKey)
    }

    override suspend fun getAllOngoingTasksForCancel() = ongoingTasks.values.toList()
}