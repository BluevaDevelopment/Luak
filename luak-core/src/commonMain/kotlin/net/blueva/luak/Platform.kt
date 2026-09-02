/******************************************************************************
 *  _                _
 * | |   _   _  __ _| | __
 * | |  | | | |/ _` | |/ /
 * | |__| |_| | (_| |   <
 * |_____\__,_|\__,_|_|\_\
 *
 *  Luak
 *  https://github.com/BluevaDevelopment/Luak
 *
 *  Copyright (c) 2026 Blueva Development
 *
 *  SPDX-License-Identifier: MIT
 ******************************************************************************/
package net.blueva.luak

import kotlin.reflect.KClass

internal expect fun currentTimeMillis(): Long
internal expect fun platformProperty(name: String): String?

/** Value of the process environment variable [name], or null if the host has none. */
internal expect fun platformEnvironment(name: String): String?
internal expect fun platformExit(code: Int)
internal expect fun platformCollectGarbage()

/**
 * Watches [target] so that it joins [pending] once nothing refers to it.
 *
 * This is what stands in for Lua marking an object for finalization. The
 * answer is a keeper the caller has to hang on to from [target] itself: it
 * lives exactly as long as the object does, and hands the object back when
 * that ends, which is the resurrection a `__gc` handler needs to be given the
 * object it is finalizing.
 *
 * Only a host that can resurrect an object it is about to reclaim can do this;
 * where the host cannot, the answer is null and `__gc` never runs.
 */
internal expect fun watchForFinalization(target: LuaValue, pending: MutableList<LuaValue>): Any?

/** Takes what has been collected out of [pending], emptying it. */
internal expect fun takeFinalized(pending: MutableList<LuaValue>): List<LuaValue>

/**
 * True when [failure] is the host running out of call stack.
 *
 * The interpreter recurses on the host's stack, so a Lua program that recurses
 * without bound exhausts that rather than a stack of Lua's own. That stack is
 * this port's counterpart of the C stack a reference build runs out of, so it
 * is reported the same way - "C stack overflow", which a `pcall` can catch -
 * instead of letting a host error escape.
 */
internal expect fun platformIsStackOverflow(failure: Throwable): Boolean
internal expect fun platformUsedMemory(): Long
internal expect fun platformLoadLibrary(className: String, globals: Globals): LuaValue?
internal expect fun platformTypeName(type: KClass<*>): String

internal data class DateParts(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val weekday: Int,
    val yearDay: Int,
)

internal fun dateParts(epochSeconds: Long): DateParts {
    val days = floorDiv(epochSeconds, 86_400L)
    var seconds = epochSeconds - days * 86_400L
    if (seconds < 0) seconds += 86_400L
    val civil = civilFromDays(days)
    val yearDay = (days - daysFromCivil(civil.first, 1, 1) + 1).toInt()
    val weekday = floorMod(days + 4, 7).toInt() + 1
    return DateParts(
        civil.first,
        civil.second,
        civil.third,
        (seconds / 3_600L).toInt(),
        (seconds % 3_600L / 60L).toInt(),
        (seconds % 60L).toInt(),
        weekday,
        yearDay,
    )
}

internal fun epochSeconds(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
    daysFromCivil(year, month, day) * 86_400L + hour * 3_600L + minute * 60L + second

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val adjustedYear = year - if (month <= 2) 1 else 0
    val era = floorDiv(adjustedYear.toLong(), 400L)
    val yearOfEra = adjustedYear - (era * 400L).toInt()
    val adjustedMonth = month + if (month > 2) -3 else 9
    val dayOfYear = (153 * adjustedMonth + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146_097L + dayOfEra - 719_468L
}

private fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
    val z = daysSinceEpoch + 719_468L
    val era = floorDiv(z, 146_097L)
    val dayOfEra = (z - era * 146_097L).toInt()
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    var year = yearOfEra + (era * 400L).toInt()
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPrime = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthPrime + 2) / 5 + 1
    val month = monthPrime + if (monthPrime < 10) 3 else -9
    year += if (month <= 2) 1 else 0
    return Triple(year, month, day)
}

private fun floorDiv(value: Long, divisor: Long): Long {
    var quotient = value / divisor
    if ((value xor divisor) < 0 && quotient * divisor != value) quotient--
    return quotient
}

private fun floorMod(value: Long, divisor: Long): Long = value - floorDiv(value, divisor) * divisor
