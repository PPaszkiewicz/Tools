package com.github.ppaszkiewicz.tools.toolbox.extensions

import java.util.regex.Pattern
import kotlin.math.absoluteValue

private val POSITIVE_NUMBERS_PATTERN =  Pattern.compile("\\d+")
private val ALL_NUMBERS_PATTERN = Pattern.compile("-?\\d+")

/**
 * Find first number in this string and return it as-is.
 * [findNegatives] to consider "-" before number as negative value (true by default).
 * */
fun String.firstNumber(findNegatives: Boolean = true): String? {
    val pat = if(findNegatives) ALL_NUMBERS_PATTERN else POSITIVE_NUMBERS_PATTERN
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
    while(matcher.find()) l.add(matcher.group().toInt())
    return l
}

/** Space out the number using [separator]: for example 2500 -> 2 500. */
fun Int.spaced(separator : Char = ' ') : String{
    var x = this.absoluteValue
    if(x < 1000) return this.toString()
    val sb = StringBuilder()
    while(x > 0){
        sb.insert(0, x % 1000)
        sb.insert(0, separator)
        x/=1000
    }
    sb.deleteCharAt(0)
    if(this < 0) sb.insert(0, '-')
    return sb.toString()
}

/** Long sumby. */
inline fun <T> Iterable<T>.sumBy(selector: (T) -> Long): Long {
    var sum = 0L
    for (element in this) {
        sum += selector(element)
    }
    return sum
}