package com.github.ppaszkiewicz.tools.toolbox.downloadManager

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.collection.LongSparseArray
import androidx.lifecycle.LiveData


/*
*   Requires DownloadProgressObserver.kt
*/

/**
 * LiveData wrapper of [DownloadProgressObserver].
 *
 * Emits an empty list when created and when there are no observed IDs left due to
 * [remove], [removeAll] or [replaceAll].
 * */
open class DownloadProgressLiveData(val context: Context) :
    LiveData<LongSparseArray<DownloadProgress>>(),
    DownloadProgressObserver.ProgressListener {

    companion object {
        val DOWNLOAD_MANAGER_INTENT_FILTER = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
    }

    init {
        value = DownloadProgressObserver.NO_RESULTS_LIST
    }

    protected val downloadProgressObserver =
        DownloadProgressObserver(context, DownloadProgressObserver.Mode.AUTO, this)

    /** If true new values will NOT be emitted if updated list is equal to old one. */
    var preventUpdateWithoutChanges = true

    override fun onDownloadProgressUpdate(downloadProgresses: LongSparseArray<DownloadProgress>) {
        if (preventUpdateWithoutChanges) {
            if (value != downloadProgresses) {
                value = downloadProgresses
            }
        } else
            value = downloadProgresses
    }

    /** Forces value update now ignoring current [preventUpdateWithoutChanges]. */
    open fun forceUpdateNow() {
        val prevent = preventUpdateWithoutChanges
        preventUpdateWithoutChanges = false
        downloadProgressObserver.runBlocking()
        preventUpdateWithoutChanges = prevent
    }

    /** Add one id to observe. */
    open fun add(downloadId: Long) = downloadProgressObserver.add(downloadId)

    /** Start reporting progress for ids */
    open fun addAll(vararg downloadIds: Long) = downloadProgressObserver.addAll(*downloadIds)

    /**
     * Stop reporting progress for [downloadIds].
     *
     * @param downloadIds list of IDs to remove from tracking
     * @param emitNothing `true` to [setValue] to empty list if everything is removed (default).
     *                   `false` to keep last result.
     * */
    open fun remove(vararg downloadIds: Long, emitNothing: Boolean = true) {
        downloadProgressObserver.remove(*downloadIds)
        if (emitNothing && downloadProgressObserver.isEmpty())
            value = DownloadProgressObserver.NO_RESULTS_LIST
    }

    /** Replace observed ID. Removes [oldId] if it's present, then adds [newId]. */
    open fun replace(oldId: Long, newId: Long) = downloadProgressObserver.replace(oldId, newId)

    /** Remove all currently observed IDs and replaces them with [newIds].
     *
     * @param emitNothing `true` to [setValue] to empty list if [newIds] is empty (default).
     *                   `false` to keep last result.
     * */
    open fun replaceAll(vararg newIds: Long, emitNothing: Boolean = true) {
        downloadProgressObserver.replaceAll(*newIds)
        if (newIds.isEmpty() && emitNothing)
            value = DownloadProgressObserver.NO_RESULTS_LIST
    }

    /** Replace observed ID. If [oldId] is not present [newId] is not added. */
    open fun replaceOnly(oldId: Long, newId: Long) =
        downloadProgressObserver.replaceOnly(oldId, newId)

    /**
     * Stop tracking all the progress.
     *
     * @param emitNothing `true` to [setValue] to empty list if everything is removed (default).
     *                   `false` to keep last result.
     * */
    open fun removeAll(emitNothing: Boolean = true) {
        downloadProgressObserver.removeAll()
        if (emitNothing)
            value = DownloadProgressObserver.NO_RESULTS_LIST
    }

    /** Check if observed IDs contain given ID. */
    fun isObserving(id: Long) = downloadProgressObserver.isObserving(id)

    /**
     * Callback from download manager broadcasts.
     * */
    open fun onDownloadBroadcast(id: Long, intent: Intent) {
        if (downloadProgressObserver.isObserving(id))
            downloadProgressObserver.run()
    }

    /** Broadcast receiver for dl updates. Allows faster update when download finishes. */
    private val dlManagerBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L)
            onDownloadBroadcast(id, intent)
        }
    }

    override fun onActive() {
        downloadProgressObserver.start()
        context.registerReceiver(dlManagerBroadcastReceiver, DOWNLOAD_MANAGER_INTENT_FILTER)
    }

    override fun onInactive() {
        downloadProgressObserver.stop()
        context.unregisterReceiver(dlManagerBroadcastReceiver)
    }
}