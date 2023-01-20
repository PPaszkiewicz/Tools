package com.github.ppaszkiewicz.tools.coroutines

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.*

/** Map that stores history of access to its items. */
interface FifoMap<K, V> : MutableMap<K, V> {
    /** Push given key that exists in history into the front of if. */
    fun pushKeyInHistory(key: K)

    /**
     * Keep only the newest/last accessed items in this map, removing older.
     *
     * @param count how many items to leave in map - based on history, might be less if there are
     *       keys in history without associated map value
     * @param tolerance how many items can be over [count] without performing any deletion
     * @return list of removed items
     * */
    fun keepLast(count: Int, tolerance: Int = 0): List<V>

    /** Trim history by removing all keys that have no associated value in map. */
    fun trimHistory()
}

/** Default FifoMap wrapper implementation. */
class FifoMapWrapper<K, V>(
    private val map: MutableMap<K, V>,
    val fifoPolicy: FifoPolicy = FifoPolicy.ON_NULL_GET
) : FifoMap<K, V>, MutableMap<K, V> by map {
    enum class FifoPolicy(val onget: Boolean = false, val onput: Boolean = false) {
        /** Combination of [ON_NULL_GET] and [ON_PUT]. */
        ALWAYS(true, true),

        /** Allow history to be pushed when key without associated value is queried. */
        ON_NULL_GET(onget = true),

        /** Update history only when new value is input. */
        ON_PUT(onput = true),

        /** No implicit history manipulation is done, use [get] and [put] overloads with boolean argument. */
        NEVER
    }

    private val history = LinkedList<K>()

    override fun put(key: K, value: V): V? {
        return put(key, value, fifoPolicy.onput)
    }

    fun put(key: K, value: V, pushHistory: Boolean): V? {
        val obj = map.put(key, value)
        if (pushHistory) notifyQueried(key, true)
        return obj
    }

    override fun get(key: K): V? {
        return get(key, fifoPolicy.onget)
    }

    fun get(key: K, pushHistoryIfNull: Boolean): V? {
        val obj = map[key]
        notifyQueried(key, pushHistoryIfNull)
        return obj
    }

    /** Get without updating fifo history. */
    fun getWithoutPush(key: K): V? {
        return map[key]
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun getOrDefault(key: K, defaultValue: V): V {
        val obj = map.getOrDefault(key, defaultValue)
        notifyQueried(key, false)
        return obj
    }

    override fun remove(key: K): V? {
        val m = map.remove(key)
        history.remove(key)
        return m
    }

    override fun clear() {
        map.clear()
        history.clear()
    }

    /** Moves queried key into the beginning of the array (newest in history) */
    private fun notifyQueried(key: K, pushEmpty: Boolean) {
        val removed = history.remove(key)
        if (removed || pushEmpty) {
            history.push(key)
        }
    }

    override fun pushKeyInHistory(key: K) {
        notifyQueried(key, false)
    }

    override fun keepLast(count: Int, tolerance: Int): List<V> {
        if (count + tolerance >= history.size) return emptyList()
        val sub = history.subList(count, history.size)
        val removed = ArrayList<V>(sub.size)
        sub.forEach { subItem ->
            map.remove(subItem)?.let { removed.add(it) }
        }
        sub.clear()
        return removed
    }

    override fun trimHistory() {
        history.removeAll { !map.containsKey(it) }
    }
}

/** Wrap this map as [FifoMap].*/
fun <K, V> MutableMap<K, V>.asFifoMap() = FifoMapWrapper(this)