package com.github.ppaszkiewicz.kotlin.tools.toolbox.downloadManager

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
 * When there are no observed IDs left due to [remove] or [removeAll], empty list is emitted.
 * */
open class DownloadProgressLiveData(val context: Context) : LiveData<LongSparseArray<DownloadProgress>>(),
    DownloadProgressObserver.ProgressListener {
    protected val downloadProgressObserver = DownloadProgressObserver(context, this)

    /** If true new values will NOT be emitted if updated list is equal to old one. */
    var preventUpdateWithoutChanges = true

    init {
        value = DownloadProgressObserver.NO_RESULTS_LIST
    }

    override fun onDownloadProgressUpdate(downloadProgresses: LongSparseArray<DownloadProgress>) {
        if (preventUpdateWithoutChanges) {
            if (value != downloadProgresses) {
                value = downloadProgresses
            }
        } else
            value = downloadProgresses
    }

    /** Forces value update now ignoring current [preventUpdateWithoutChanges]. */
    open fun forceUpdateNow(){
        val prevent = preventUpdateWithoutChanges
        downloadProgressObserver.runBlocking()
        preventUpdateWithoutChanges = prevent
    }

    /** Start reporting progress for ids */
    open fun add(vararg downloadIds: Long) = downloadProgressObserver.add(*downloadIds)

    /**
     * Stop reporting progress for ids.
     * @param downloadIds list of IDs to remove from tracking
     * @param emitNothing true to [setValue] to empty list afterwards (default). False to keep last result.
     * */
    open fun remove(vararg downloadIds: Long, emitNothing: Boolean = true) {
        downloadProgressObserver.remove(*downloadIds)
        if (emitNothing && downloadProgressObserver.isEmpty())
            value = DownloadProgressObserver.NO_RESULTS_LIST
    }

    /** Observe one id only, removes others when called. */
    open fun observe(downloadId: Long) = downloadProgressObserver.observe(downloadId)

    /** Replace observed IDs. Removes [oldId] if it's present, then adds [newId]. */
    open fun replace(oldId: Long, newId: Long) = downloadProgressObserver.replace(oldId, newId)

    /** Replace observed IDs. If [oldId] is not present new one is not added. */
    open fun replaceOnly(oldId: Long, newId: Long) = downloadProgressObserver.replaceOnly(oldId, newId)

    /** Remove all currently observed IDs and replaces then with [newIds]. */
    open fun replaceAll(vararg newIds : Long) = downloadProgressObserver.replaceAll(*newIds)

    /**
     * Stop tracking all the progress.
     * @param emitNothing true to [setValue] to empty list afterwards (default). False to keep last result.
     * */
    open fun removeAll(emitNothing: Boolean = true) {
        downloadProgressObserver.removeAll()
        if (emitNothing)
            value = DownloadProgressObserver.NO_RESULTS_LIST
    }

    /**
     * Callback from download manager broadcasts.
     * */
    open fun onDownloadBroadcast(id: Long, intent: Intent){
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
        context.registerReceiver(
            dlManagerBroadcastReceiver, IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE
            )
        )
    }

    override fun onInactive() {
        downloadProgressObserver.stop()
        context.unregisterReceiver(dlManagerBroadcastReceiver)
    }
}