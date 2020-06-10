package com.github.ppaszkiewicz.tools.toolbox.downloadManager

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.Log
import androidx.collection.LongSparseArray
import kotlinx.coroutines.*
import java.lang.Runnable
import java.lang.ref.WeakReference
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.system.measureNanoTime

/**
 * Listener for download progress updates.
 *
 * Periodically queries download manager for provided IDs instead of relying on broadcast updates.
 */
class DownloadProgressObserver(val context: Context, var mode: Mode = Mode.AUTO) {
    companion object {
        /**
         * Update period. Values lower than 2 seconds don't change anything, update period
         * in DownloadManager table is longer than that.
         * */
        const val DOWNLOAD_UPDATE_PERIOD_MS = 2000L
        const val TAG = "DLProgressObserver"

        /** Cutoff in ms when [Mode.AUTO] will switch to async. */
        const val ASYNC_CUTOFF = 2L

        /** Reusable no-results sparse array. */
        val NO_RESULTS_LIST = LongSparseArray<DownloadProgress>()
            get() = field.apply { clear() }
    }

    // set listener
    constructor(
        context: Context,
        mode: Mode = Mode.AUTO,
        listener: ProgressListener
    ) : this(context, mode) {
        updateListener = listener
    }

    // create listener from lambda
    constructor(
        context: Context,
        mode: Mode = Mode.AUTO,
        onUpdate: (LongSparseArray<DownloadProgress>) -> Unit
    ) : this(
        context, mode, object : ProgressListener {
            override fun onDownloadProgressUpdate(downloadProgresses: LongSparseArray<DownloadProgress>) =
                onUpdate(downloadProgresses)
        }
    )

    /** Operation mode of this observer. */
    enum class Mode {
        /** ALWAYS run synchronously. */
        SYNC,

        /** ALWAYS run asynchronously. */
        ASYNC,

        /** Run first query synchronously then switch depending on runtime. */
        AUTO
    }

    private val runnable = Runnable { runImpl(force = false, forceBlocking = false) }

    /** Whether this observer is currently active. */
    var isActive = false
        private set

    /** Measured runtime of last query. */
    var lastQueryRuntimeMs = 0L
        private set
    private val updateHandler = Handler()
    private val downloadIds = HashSet<Long>()
    private var queryJob: Job? = null

    /** Listener for updates. Will operate even when listener is null. */
    var updateListener: ProgressListener? = null

    /**
     * Start reporting progress for [downloadId]. Returns true if ID was not observed before.
     * */
    fun add(downloadId: Long) = downloadIds.add(downloadId).also { tryRun() }

    /** Start reporting progress for [downloadIds]. */
    fun addAll(vararg downloadIds: Long) {
        downloadIds.forEach {
            this.downloadIds.add(it)
        }
        tryRun()
    }

    /** Stop reporting progress for ids. */
    fun remove(vararg downloadIds: Long) {
        downloadIds.forEach {
            this.downloadIds.remove(it)
        }
    }

    /** Replace observed ID. Removes [oldId] if it's present, then adds [newId]. */
    fun replace(oldId: Long, newId: Long) {
        downloadIds.remove(oldId)
        downloadIds.add(newId)
        tryRun()
    }

    /** Remove all currently observed IDs and replaces them with [newIds]. */
    fun replaceAll(vararg newIds: Long) {
        downloadIds.clear()
        addAll(*newIds)
        tryRun()
    }

    /** Replace observed ID. If [oldId] is not present [newId] is not added. */
    fun replaceOnly(oldId: Long, newId: Long) =
        if (downloadIds.remove(oldId)) {
            downloadIds.add(newId)
            tryRun()
            true
        } else false

    /** Stop tracking all ids. */
    fun removeAll() {
        downloadIds.clear()
    }

    /** Begin update loop. */
    fun start() {
        Log.d(TAG, "started w/ ${downloadIds.joinToString()}")
        updateHandler.removeCallbacks(runnable) // be sure no double callback exists
        isActive = true
        run()   // start instantly
    }

    /** Stop update loop. */
    fun stop() {
        Log.d(TAG, "stopped w/ ${downloadIds.joinToString()}")
        updateHandler.removeCallbacks(runnable)
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
     * Query is performed synchronously/asynchronously based on [mode]. Result is always received on
     * Main thread.
     * */
    fun run() = runImpl(force = true, forceBlocking = false)

    /** Run update query immediately, always synchronously. */
    fun runBlocking() = runImpl(force = true, forceBlocking = true)

    /**
     * Run update immediately and synchronously if there's 1 item in the list (common case).
     *
     * Called implicitly by all add methods.
     * */
    fun tryRun() {
        if (isActive && downloadIds.size == 1) runBlocking()
    }


    /**
     * @param force ignore [isActive] flag and run regardless
     * @param forceBlocking force this query to run synchronously
     * */
    private fun runImpl(force: Boolean = false, forceBlocking: Boolean = false) {
        updateHandler.removeCallbacks(runnable)

        if (isActive)
            updateHandler.postDelayed(runnable, DOWNLOAD_UPDATE_PERIOD_MS)
        else if (!force) {
            Log.w(TAG, "run(): prevented update because observer is not active")
            return
        }

        // copy to prevent concurrent modification errors
        val ids = downloadIds.toLongArray()

        if (ids.isEmpty()) {
            return  // nothing to report
        }

        val runAsync = !forceBlocking && when (mode) {
            Mode.SYNC -> false
            Mode.ASYNC -> true
            Mode.AUTO -> lastQueryRuntimeMs > ASYNC_CUTOFF
        }

        queryJob?.let {
            if (it.isActive)
                Log.w(TAG, "previous query was destroyed")
            it.cancel()
        }

        val dispatcher = if (runAsync) Dispatchers.Default else Dispatchers.Main.immediate
        queryJob = GlobalScope.launch(dispatcher) {
            val nanoStart = System.nanoTime()
            val result = context.downloadManager.getDownloadProgresses(*ids)
            val nanos = System.nanoTime() - nanoStart
            launch(Dispatchers.Main.immediate) {
                lastQueryRuntimeMs = nanos / 1_000_000
                if (isActive && !result.isEmpty)
                    updateListener?.onDownloadProgressUpdate(result)
            }
        }
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

@Volatile
private lateinit var columns: Columns

/** Query [DownloadManager] for progress of [ids]. */
fun DownloadManager.getDownloadProgresses(vararg ids: Long): LongSparseArray<DownloadProgress> {
    val results = LongSparseArray<DownloadProgress>(ids.size)
    val q = DownloadManager.Query().setFilterById(*ids)
    query(q).use { c ->
        if (!::columns.isInitialized) {
            columns = Columns(c)
        }
        c.run {
            while (moveToNext()) {
                val id = getLong(columns.id)
                results.put(
                    id,
                    DownloadProgress(
                        id,
                        getString(columns.path),
                        getInt(columns.status),
                        getInt(columns.reason),
                        getLong(columns.prg),
                        getLong(columns.max)
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

    /** Progress with % sign (ie. 44%) or [undetermined] (default "...") if max not set. */
    fun printProgress(undetermined: String = "...") =
        if (cur > max) undetermined
        else "${percentProgress().toInt()}%"

    /** Percent of progress (between 0.0f - 100.0f). */
    fun percentProgress() = ((cur / max.toFloat()) * 100)

    /** Progress in "readable" format, ie. 512KB / 2MB */
    fun fileSizeProgress(context: Context) =
        "${cur.toFileSize(context)} / ${max.toFileSize(context)}"

    /** Decoded path without leading file:// scheme. */
    fun decodedPath() = filePath?.substringAfter("file://")?.let {
        Uri.decode(it)
    }

    /**
     * Indicates whether this download has started.
     *
     * False implies that removing [id] from download manager should yield no broadcast.
     * */
    fun hasStarted() =
        max != -1L && status != DownloadManager.STATUS_PAUSED && status != DownloadManager.STATUS_PENDING

    /** Readable file size. */
    private fun Long.toFileSize(context: Context) = Formatter.formatShortFileSize(context, this)
}

// cache for column indexes
private class Columns(c: Cursor) {
    val id = c.getColumnIndex(DownloadManager.COLUMN_ID)
    val path = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
    val status = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
    val reason = c.getColumnIndex(DownloadManager.COLUMN_REASON)
    val prg = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
    val max = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
}