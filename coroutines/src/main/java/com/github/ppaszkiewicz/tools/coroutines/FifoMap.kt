package com.github.ppaszkiewicz.tools.coroutines

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.*
import kotlin.collections.ArrayList

/** Map that stores history of access to its items. */
interface FifoMap<K, V> : MutableMap<K, V>{
    /** Push given key into the front of history. */
    fun pushKey(key: K)

    /**
     * Keep only the newest/last accessed items in this map, removing older.
     *
     * @param count how many items to leave in cache
     * @param tolerance how many items can be over [count] without performing deletion
     * @return list of returned
     * */
    fun keepLast(count: Int, tolerance: Int = 0) : List<V>
}

/** Default FifoMap wrapper implementation. */
class FifoMapWrapper<K, V>(private val map: MutableMap<K,V>) : FifoMap<K, V>, MutableMap<K, V> by map{
    companion object {
        /** Flag that will only push if there's an item - gets returning null won't change history. */
        const val GET_ONLY_UPDATE = false
        /** Put calls will update history. Otherwise use gets only to drive history. This cannot be false if [GET_ONLY_UPDATE] is true. */
        const val UPDATE_ON_PUT = false
    }
    private val history = LinkedList<K>()


    override fun put(key: K, value: V): V? {
        val obj = map.put(key, value)

        @Suppress("ConstantConditionIf")
        if(UPDATE_ON_PUT)
            notifyQueried(key, UPDATE_ON_PUT)

        return obj
    }

    override fun get(key: K): V? {
        val obj = map[key]
        notifyQueried(key, false)
        return obj
    }

    /** Get without pushing fifo list (thread safe). */
    fun getSilent(key: K): V?{
        return map[key]
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun getOrDefault(key: K, defaultValue: V): V {
        val obj = map.getOrDefault(key, defaultValue)
        notifyQueried(key, false)
        return obj
    }

    override fun remove(key: K) : V? {
        val m = map.remove(key)
        history.remove(key)
        return m
    }

    override fun clear() {
        map.clear()
        history.clear()
    }

    /** Moves queried key into the beginning of the array (newest in history) */
    private fun notifyQueried(key: K, forcePush : Boolean) {
        val removed = history.remove(key)

        @Suppress("ConstantConditionIf")
        if(GET_ONLY_UPDATE){
            if(removed || forcePush){
                history.push(key)
            }
        }else
            history.push(key)
    }

    override fun pushKey(key: K) {
        notifyQueried(key, false)
    }

    override fun keepLast(count: Int, tolerance: Int) : List<V> {
        if (count + tolerance >= history.size) return emptyList()
        val sub = history.subList(count, history.size)
        val removed = ArrayList<V>(sub.size)
        sub.forEach{subItem ->
            map.remove(subItem)?.let { removed.add(it) }
        }
        sub.clear()
        return removed
    }

}

/** Wrap this map as FifoMap.*/
fun <K,V> MutableMap<K, V>.asFifoMap() = FifoMapWrapper(this)