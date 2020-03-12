package com.github.ppaszkiewicz.tools.toolbox.extensions

import java.util.regex.Pattern
import kotlin.math.absoluteValue

private val POSITIVE_NUMBERS_PATTERN = Pattern.compile("\\d+")
private val ALL_NUMBERS_PATTERN = Pattern.compile("-?\\d+")

/**
 * Find first number in this string and return it as-is.
 * [findNegatives] to consider "-" before number as negative value (true by default).
 * */
fun String.firstNumber(findNegatives: Boolean = true): String? {
    val pat = if (findNegatives) ALL_NUMBERS_PATTERN else POSITIVE_NUMBERS_PATTERN
    val matcher = pat.matcher(this)
    return if (matcher.find()) matcher.group() else null
}

/** Find first integer in this string.
 * [findNegatives] to consider "-" before number as negative value (true by default). */
fun String.firstInteger(findNegatives: Boolean = true) = firstNumber(findNegatives)?.toInt()

/** Find first long in this string.
 * [findNegatives] to consider "-" before number as negative value (true by default). */
fun String.firstLong(findNegatives: Boolean = true) = firstNumber(findNegatives)?.toLong()

/** Find all integers in this string.
 * [findNegatives] to consider "-" before numbers as negative value (true by default).*/
fun String.allIntegers(findNegatives: Boolean = true): List<Int> {
    val l = mutableListOf<Int>()
    val pat = if (findNegatives) ALL_NUMBERS_PATTERN else POSITIVE_NUMBERS_PATTERN
    val matcher = pat.matcher(this)
    while (matcher.find()) l.add(matcher.group().toInt())
    return l
}

/** Space out the number using [separator]: for example 2500 -> 2 500. */
fun Int.spaced(separator: Char = ' '): CharSequence {
    return absoluteValue.let { abs ->
        if (abs < 1000) toString()  // no reformat needed
        else abs.toString().spaced(3, separator, "-".takeIf { this < 0 })
    }
}

/** Space out the number using [separator]: for example 2500 -> 2 500. */
fun Long.spaced(separator: Char = ' '): CharSequence {
    return absoluteValue.let { abs ->
        if (abs < 1000L) toString()  // no reformat needed
        else abs.toString().spaced(3, separator, "-".takeIf { this < 0 })
    }
}

/**
 * Format this sequence into chunks of [chunkSize] separated by [separator]. If this sequence
 * length is not divisible by size, first chunk will be smaller.
 *
 * Prepends [prefix] if not null.
 * */
fun CharSequence.spaced(
    chunkSize: Int,
    separator: Char = ' ',
    prefix: String? = null
): CharSequence {
    StringBuilder().let { sb ->
        prefix?.let { sb.append(it) }
        if (length <= chunkSize) return sb.append(this).toString()

        var i = length % chunkSize
        if (i == 0) i = chunkSize
        sb.append(substring(0, i))

        while (i < length) {
            sb.append(separator)
            sb.append(substring(i, i + chunkSize))
            i += chunkSize
        }
        return sb.toString()
    }
}

/** Long sumby. */
inline fun <T> Iterable<T>.sumBy(selector: (T) -> Long): Long {
    var sum = 0L
    for (element in this) {
        sum += selector(element)
    }
    return sum
}