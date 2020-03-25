package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.text.format.DateUtils
import android.util.Log

/**
 * Parses time period based on [duration] of milliseconds.
 *
 * This is used instead of calendar/date because it simply divides milliseconds and
 * is not affected by any time zones etc.
 * */
class TimePeriodParser(val duration: Long) {
    /** TOTAL hours. */
    val hours = duration / DateUtils.HOUR_IN_MILLIS
    /** TOTAL minutes. */
    val minutesTotal = duration / DateUtils.MINUTE_IN_MILLIS
    /** TOTAL seconds. */
    val secondsTotal = duration / DateUtils.SECOND_IN_MILLIS
    /** TOTAL millis. */
    val millisTotal
        get() = duration

    /** Minutes within the hour. */
    val minutes = duration % DateUtils.HOUR_IN_MILLIS / DateUtils.MINUTE_IN_MILLIS
    /** Seconds within the minute.*/
    val seconds = duration % DateUtils.MINUTE_IN_MILLIS / DateUtils.SECOND_IN_MILLIS
    /** Millis within the second.*/
    val millis = duration % DateUtils.SECOND_IN_MILLIS

    /** Check if total time has at least 1 hour. */
    fun hasHours() = hours > 0

    /** Check if total time has at least 1 minute. */
    fun hasMinutes() = minutesTotal > 0

    /** Check if total time has at least 1 second. */
    fun hasSeconds() = secondsTotal > 0

    companion object {
        /**
         * Parse [string] formatted as hh:mm:ss.millis or any variation of it
         * (mm:ss.millis, ss.millis, hh:mm:ss, mm:ss, ss).
         *
         * Returns 0 on errors.
         * */
        fun parse(string: String): Long {
            var millis = 0L
            val millisSplit = string.split(".")
            millisSplit.getOrNull(1)?.let {
                millis += it.padEnd(3, '0').toInt()
            }
            val times = millisSplit[0].split(":")
            if (times.size > 3) {
                Log.e("ETP", "ElapsedTimeParser.parse: invalid input string: $string")
                return 0
            }
            val sec = times.last().toInt() * DateUtils.SECOND_IN_MILLIS
            val min =
                (times.getOrNull(times.size - 2)?.toIntOrNull() ?: 0) * DateUtils.MINUTE_IN_MILLIS
            val hr =
                (times.getOrNull(times.size - 3)?.toIntOrNull() ?: 0) * DateUtils.HOUR_IN_MILLIS
            return hr + min + sec + millis
        }

        /** Get [TimePeriodParser] parsed from [string]. Returns 0 on errors.*/
        fun from(string: String) = TimePeriodParser(parse(string))
    }
}