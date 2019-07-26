package com.github.ppaszkiewicz.tools.toolbox.downloadManager

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.text.format.Formatter
import android.util.Log
import androidx.annotation.MainThread
import androidx.collection.LongSparseArray
import kotlinx.coroutines.*
import java.lang.Runnable
import java.lang.ref.WeakReference

/**
 * Listener for download progress updates.
 *
 * Periodically queries download manager for provided IDs instead of relying on broadcast updates.
 */
@MainThread
class DownloadProgressObserver(val context: Context) : Runnable {
    companion object {
        /**
         * Update period. Values lower than 2 seconds don't change anything, update period
         * in DownloadManager table is longer than that.
         * */
        const val DOWNLOAD_UPDATE_PERIOD_MS = 2000L
        const val TAG = "DLProgressObserver"
        /** Reusable no-results sparse array. */
        val NO_RESULTS_LIST = LongSparseArray<DownloadProgress>()
            get() = field.apply { clear() }
    }

    // set listener
    constructor(context: Context, listener: ProgressListener) : this(context) {
        updateListener = listener
    }

    // create listener from lambda
    constructor(context: Context, onUpdate: (LongSparseArray<DownloadProgress>) -> Unit) : this(
        context,
        object : ProgressListener {
            override fun onDownloadProgressUpdate(downloadProgresses: LongSparseArray<DownloadProgress>) =
                onUpdate(downloadProgresses)
        }
    )

    /** Whether this observer is currently active. */
    var isActive = false
        private set
    private val updateHandler = Handler()
    private val downloadIds = HashSet<Long>()
    private var queryJob: Job? = null

    /** Listener for updates. Will operate even when listener is null. */
    var updateListener: ProgressListener? = null

    /** Start reporting progress for ids */
    fun add(vararg downloadIds: Long) {
        downloadIds.forEach {
            this.downloadIds.add(it)
        }
    }

    /** Stop reporting progress for ids */
    fun remove(vararg downloadIds: Long) {
        downloadIds.forEach {
            this.downloadIds.remove(it)
        }
    }

    /** Observe one id only, removes others when called. */
    fun observe(downloadId: Long) {
        downloadIds.clear()
        downloadIds.add(downloadId)
        run()
    }

    /** Replace observed IDs. Removes [oldId] if it's present, then adds [newId]. */
    fun replace(oldId: Long, newId: Long){
        downloadIds.remove(oldId)
        downloadIds.add(newId)
        if(downloadIds.size == 1) run()
    }

    /** Replace observed IDs. If [oldId] is not present new is not added. */
    fun replaceOnly(oldId: Long, newId: Long) =
        if (downloadIds.remove(oldId)) {
            downloadIds.add(newId)
            if (downloadIds.size == 1) run()
            true
        } else false

    /** Remove all currently observed IDs and replaces then with [newIds]. */
    fun replaceAll(vararg newIds : Long){
        downloadIds.clear()
        add(*newIds)
    }

    /** Stop tracking all the progress. */
    fun removeAll() {
        downloadIds.clear()
    }

    /** Begin update loop. */
    fun start() {
        Log.d(TAG, "started w/ ${downloadIds.joinToString()}")
        updateHandler.removeCallbacks(this) // be sure no double callback exists
        isActive = true
        run()   // start instantly
    }

    /** Stop update loop. */
    fun stop() {
        Log.d(TAG, "stopped w/ ${downloadIds.joinToString()}")
        updateHandler.removeCallbacks(this)
        queryJob?.cancel()
        isActive = false
    }

    /** True if this is not observing anything. */
    fun isEmpty() = downloadIds.isEmpty()

    /** Check if observed IDs contain given ID. */
    fun isObserving(id: Long) = downloadIds.contains(id)

    /**
     * Run update query immediately instead of waiting for next update cycle.
     *
     * Query is performed synchronously if there's only 1 observed item or asynchronously otherwise. Result
     * is always received on UI thread.
     * */
    override fun run() = run(true)

    /** Run update query immediately, always synchronously. */
    fun runBlocking() = run(false)

    /** [allowAsync] - run query for multiple IDs asynchronously. Ignored if there's only 1 item (always blocking). */
    private fun run(allowAsync: Boolean) {
        updateHandler.removeCallbacks(this)

        if (isActive)
            updateHandler.postDelayed(this, DOWNLOAD_UPDATE_PERIOD_MS)
        else {
            Log.w(TAG, "run(): prevented update because observer is not active")
            return
        }

        // copy to prevent concurrent modification errors
        val ids = downloadIds.toLongArray()

        if (ids.isEmpty()) {
            return  // nothing to report
        } else if (ids.size == 1) {
            // update for single ID - blocking so it's faster
            updateListener?.onDownloadProgressUpdate(context.downloadManager.getDownloadProgresses(ids[0]))
            return
        }

        // for multiple IDs it's possible to run async
        queryJob?.cancel()
        if (allowAsync) {
            val weakContext = WeakReference(context)
            queryJob = GlobalScope.launch {
                dispatchProgressAsync(this, weakContext, ids)
            }
        } else {
            queryJob = null
            updateListener?.onDownloadProgressUpdate(context.downloadManager.getDownloadProgresses(*ids))
        }
    }

    // runs cursor asynchronously and switches to UI thread on result. Thread safe (holds weak context reference)
    @Suppress("RedundantSuspendModifier")
    private suspend fun dispatchProgressAsync(
        scope: CoroutineScope,
        weakContext: WeakReference<Context>,
        ids: LongArray
    ) {
        val results = weakContext.get()?.downloadManager?.getDownloadProgresses(*ids)
            ?: throw CancellationException("Ref lost")

        if (!results.isEmpty)
            scope.launch(Dispatchers.Main) {
                if (isActive) {
                    // interrupt if missing to prevent leak
                    weakContext.get() ?: throw CancellationException("Ref lost")
                    updateListener?.onDownloadProgressUpdate(results)
                }
            }
        else
            Log.e(TAG, "no results for $ids")
    }

    /** Interface for [DownloadProgressObserver] updates. */
    interface ProgressListener {
        fun onDownloadProgressUpdate(downloadProgresses: LongSparseArray<DownloadProgress>)
    }
}

/** Download manager for this context. */
val Context.downloadManager
    get() = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

/** Query [DownloadManager] for progress of [id]. Might return null if it doesn't exist. */
fun DownloadManager.getDownloadProgress(id: Long) = getDownloadProgresses(id)[id]

/** Query [DownloadManager] for progress of [ids]. */
fun DownloadManager.getDownloadProgresses(vararg ids: Long): LongSparseArray<DownloadProgress> {
    val results = LongSparseArray<DownloadProgress>(ids.size)
    val q = DownloadManager.Query().setFilterById(*ids)
    query(q).use { c ->
        val idColumn = c.getColumnIndex(DownloadManager.COLUMN_ID)
        val pathColumn = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        val statusColumn = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val reasonColumn = c.getColumnIndex(DownloadManager.COLUMN_REASON)
        val prgColumn = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val maxColumn = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

        c.run {
            while (moveToNext()) {
                val id = getLong(idColumn)
                results.put(id,
                    DownloadProgress(
                        id,
                        getString(pathColumn),
                        getInt(statusColumn),
                        getInt(reasonColumn),
                        getLong(prgColumn),
                        getLong(maxColumn)
                    )
                )
            }
        }
    }
    return results
}

/** Long sparse array content comparision. */
fun <T> LongSparseArray<T>.contentEquals(other: LongSparseArray<T>): Boolean {
    if (size() != other.size()) return false
    repeat(size()) {
        if (keyAt(it) != other.keyAt(it) || valueAt(it) != other.valueAt(it)) return false
    }
    return true
}

/** Mapped columns from [DownloadManager]. */
data class DownloadProgress(
    /** Download ID */
    @JvmField
    val id: Long,
    /** Local path with downloaded file. This is encoded file:// uri, for decoded path invoke [decodedPath]. */
    @JvmField
    val filePath: String?,
    /** Download status */
    @JvmField
    val status: Int,
    /** Error reason. Field value is undefined unless [isFailed]. */
    @JvmField
    val reason: Int,
    /** Current progress. */
    @JvmField
    val cur: Long,
    /** Max progress. */
    @JvmField
    val max: Long
) {
    /** True if [status] is [DownloadManager.STATUS_SUCCESSFUL]. */
    fun isSuccess() = status == DownloadManager.STATUS_SUCCESSFUL

    /** True if [status] is failed and [reason] is valid. */
    fun isFailed() = status == DownloadManager.STATUS_FAILED

    /** [status] is success or failed. */
    fun isFinished() = isSuccess() || isFailed()

    /** Download has not finished yet. This does not imply the task is being actively
     * processed at this moment (might be queued or paused). */
    fun isActive() = !isFinished()

    /** Progress with % sign (ie. 44%) or "..." if max not set. */
    fun printProgress() = if (cur > max) "..." else "${percentProgress().toInt()}%"

    /** Percent of progress (between 0.0f - 100.0f). */
    fun percentProgress() = ((cur / max.toFloat()) * 100)

    /** Progress in "readable" format, ie. 512KB / 2MB */
    fun fileSizeProgress(context: Context) =
        "${cur.toFileSize(context)} / ${max.toFileSize(context)}"

    /** Decoded path without leading file:// scheme.*/
    fun decodedPath() = filePath?.substringAfter("file://")?.let {
        Uri.decode(it)
    }

    /**
     * Indicates whether this download has started.
     *
     * False implies that removing [id] from download manager should yield no broadcast.
     * */
    fun hasStarted() = max != -1L && status != DownloadManager.STATUS_PAUSED && status != DownloadManager.STATUS_PENDING

    /** Readable file size. */
    private fun Long.toFileSize(context: Context) = Formatter.formatShortFileSize(context, this)
}