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

/**
 * Exact decimal rendering of doubles, equivalent to C's `%e`, `%f`, and `%g`.
 *
 * Lua's own `tostring` for a float, and `string.format`'s float conversions,
 * are specified in terms of C's `printf`. Kotlin has no `printf` in common
 * code, and `Double.toString` answers something different: the *shortest*
 * decimal that round-trips, which is not the same as a fixed number of
 * significant digits. `1/3` prints as `0.3333333333333333` there and as
 * `0.33333333333333331` in Lua.
 *
 * So the digits are derived exactly rather than approximated. Every finite
 * double is `mantissa * 2^exponent` with a 53-bit mantissa, which is exactly
 * `N * 10^shift` for an integer `N`:
 *
 *  * when the exponent is positive, `N = mantissa * 2^exponent` and `shift = 0`;
 *  * when it is negative, `1/2^k` is `5^k/10^k`, so `N = mantissa * 5^k` and
 *    `shift = -k`.
 *
 * `N` needs more than 64 bits, so it is held as base-10^9 chunks. The work is
 * bounded by the exponent range: the widest case, a subnormal, needs about
 * eighty multiply passes over roughly ninety chunks.
 */
internal object DecimalFormat {

    /** Chunk base; 10^9 keeps a chunk-by-small-int product inside a Long. */
    private const val BASE = 1_000_000_000L
    private const val BASE_DIGITS = 9

    /** Exact digits of a finite, non-zero double, most significant first. */
    private class Exact(val digits: String, val pointExponent: Int) {
        /** Exponent `X` in `d.ddd * 10^X`. */
        val scientificExponent: Int get() = digits.length - 1 + pointExponent
    }

    /**
     * C's `%.Pg` for [value].
     *
     * Chooses between `%e` and `%f` the way C does, on the decimal exponent,
     * and drops trailing fractional zeros.
     */
    fun g(value: Double, precision: Int): String {
        if (value.isNaN() || value.isInfinite()) return nonFinite(value, upper = false)
        val p = if (precision <= 0) 1 else precision
        if (value == 0.0) return if (1 / value < 0) "-0" else "0"
        val negative = value < 0
        val exact = exactDigits(if (negative) -value else value)
        val rounded = round(exact, p)
        val exponent = rounded.scientificExponent
        val text = if (exponent < -4 || exponent >= p) {
            scientific(rounded, stripZeros = true)
        } else {
            plain(rounded, stripZeros = true)
        }
        return if (negative) "-$text" else text
    }

    /**
     * Lua's `tostring` for a float.
     *
     * Upstream formats with `%.15g`, reads the result back, and reformats with
     * `%.17g` when it did not round-trip; then appends `.0` if what came out
     * looks like an integer, so a float never prints as one.
     */
    fun luaFloat(value: Double): String {
        if (value.isNaN()) return if (isNegativeNaN(value)) "-nan" else "nan"
        if (value.isInfinite()) return if (value < 0) "-inf" else "inf"
        var text = g(value, 15)
        if (text.toDouble() != value) text = g(value, 17)
        return if (looksLikeInteger(text)) "$text.0" else text
    }

    /**
     * C's `%a`: the exact value in hexadecimal, `0x1.<mantissa>p<exponent>`.
     *
     * Every double has an exact hexadecimal form, so this is the notation to
     * reach for when a value has to survive being written out and read back -
     * which is what `string.format("%q", x)` needs.
     */
    fun hex(value: Double, upper: Boolean, precision: Int = -1): String {
        if (value.isNaN() || value.isInfinite()) return nonFinite(value, upper)
        val bits: Long = value.toRawBits()
        val negative: Boolean = bits < 0
        val exponentField: Int = ((bits ushr 52) and 0x7FF).toInt()
        val mantissaField: Long = bits and 0x000FFFFFFFFFFFFFL
        val lead: Int
        val exponent: Int
        if (exponentField == 0) {
            // Zero and the subnormals, which have no implicit leading one.
            lead = 0
            exponent = if (mantissaField == 0L) 0 else -1022
        } else {
            lead = 1
            exponent = exponentField - 1023
        }
        var fraction: String = mantissaField.toString(16).padStart(13, '0')
        if (precision >= 0) {
            // A precision on %a counts hexadecimal digits after the point.
            if (precision < fraction.length) {
                val cut: Char = fraction[precision]
                fraction = fraction.substring(0, precision)
                if (cut >= '8' && fraction.isNotEmpty()) {
                    fraction = incrementHex(fraction)
                }
            } else {
                fraction = fraction.padEnd(precision, '0')
            }
        } else {
            fraction = fraction.trimEnd('0')
        }
        val body: String = buildString {
            if (negative) append('-')
            append("0x")
            append(lead)
            if (fraction.isNotEmpty()) {
                append('.')
                append(fraction)
            }
            append('p')
            if (exponent >= 0) append('+')
            append(exponent)
        }
        return if (upper) body.uppercase() else body
    }

    /** Adds one to a hexadecimal string, keeping its length. */
    private fun incrementHex(digits: String): String {
        val out = digits.toCharArray()
        var index = out.size - 1
        while (index >= 0) {
            val value: Int = hexValue(out[index]) + 1
            if (value < 16) {
                out[index] = "0123456789abcdef"[value]
                return out.concatToString()
            }
            out[index] = '0'
            index--
        }
        return out.concatToString()
    }

    private fun hexValue(c: Char): Int = when {
        c in '0'..'9' -> c - '0'
        else -> c - 'a' + 10
    }

    /** C's `%.Pe`. */
    fun e(value: Double, precision: Int, upper: Boolean): String {
        if (value.isNaN() || value.isInfinite()) return nonFinite(value, upper)
        val negative = value < 0 || (value == 0.0 && 1 / value < 0)
        val magnitude = if (value < 0) -value else value
        val body = if (magnitude == 0.0) {
            val digits = buildString {
                append('0')
                if (precision > 0) {
                    append('.')
                    repeat(precision) { append('0') }
                }
                append(if (upper) "E+00" else "e+00")
            }
            digits
        } else {
            val rounded = round(exactDigits(magnitude), precision + 1)
            scientific(rounded, stripZeros = false, upper = upper)
        }
        return if (negative) "-$body" else body
    }

    /** C's `%.Pf`. */
    fun f(value: Double, precision: Int): String {
        if (value.isNaN() || value.isInfinite()) return nonFinite(value, false)
        val negative = value < 0 || (value == 0.0 && 1 / value < 0)
        val magnitude = if (value < 0) -value else value
        val body = if (magnitude == 0.0) {
            buildString {
                append('0')
                if (precision > 0) {
                    append('.')
                    repeat(precision) { append('0') }
                }
            }
        } else {
            val exact = exactDigits(magnitude)
            // Round at a fixed number of fractional digits rather than
            // significant ones: keep everything down to 10^-precision.
            val keep = exact.digits.length + exact.pointExponent + precision
            val rounded = if (keep <= 0) Exact("0", -precision) else round(exact, keep)
            plain(rounded, stripZeros = false, minFraction = precision)
        }
        return if (negative) "-$body" else body
    }

    private fun nonFinite(value: Double, upper: Boolean): String {
        val text = when {
            value.isNaN() -> "nan"
            value < 0 -> "-inf"
            else -> "inf"
        }
        return if (upper) text.uppercase() else text
    }

    /** True when [text] carries no '.', exponent, or other non-digit mark. */
    private fun looksLikeInteger(text: String): Boolean =
        text.all { it == '-' || (it in '0'..'9') }

    private fun isNegativeNaN(value: Double): Boolean = value.toRawBits() < 0

    /** Renders as `d.dddde+XX`. */
    private fun scientific(value: Exact, stripZeros: Boolean, upper: Boolean = false): String {
        var fraction = value.digits.substring(1)
        if (stripZeros) fraction = fraction.trimEnd('0')
        val exponent = value.scientificExponent
        val sign = if (exponent < 0) '-' else '+'
        val magnitude = if (exponent < 0) -exponent else exponent
        val exponentText = if (magnitude < 10) "0$magnitude" else magnitude.toString()
        return buildString {
            append(value.digits[0])
            if (fraction.isNotEmpty()) {
                append('.')
                append(fraction)
            }
            append(if (upper) 'E' else 'e')
            append(sign)
            append(exponentText)
        }
    }

    /** Renders without an exponent. */
    private fun plain(value: Exact, stripZeros: Boolean, minFraction: Int = 0): String {
        val digits = value.digits
        val point = digits.length + value.pointExponent // digits before the '.'
        val whole: String
        var fraction: String
        when {
            point <= 0 -> {
                whole = "0"
                fraction = "0".repeat(-point) + digits
            }

            point >= digits.length -> {
                whole = digits + "0".repeat(point - digits.length)
                fraction = ""
            }

            else -> {
                whole = digits.substring(0, point)
                fraction = digits.substring(point)
            }
        }
        if (stripZeros) fraction = fraction.trimEnd('0')
        while (fraction.length < minFraction) fraction += "0"
        return if (fraction.isEmpty()) whole else "$whole.$fraction"
    }

    /** Rounds [value] to [significant] digits, half-to-even on an exact tie. */
    private fun round(value: Exact, significant: Int): Exact {
        val digits = value.digits
        if (significant >= digits.length) return value
        if (significant <= 0) return Exact("0", value.scientificExponent + 1)

        val kept = digits.substring(0, significant)
        val dropped = digits.substring(significant)
        val first = dropped[0]
        val restNonZero = dropped.drop(1).any { it != '0' }
        val roundUp = when {
            first > '5' -> true
            first < '5' -> false
            restNonZero -> true
            else -> (kept.last() - '0') % 2 == 1 // exact tie: to even
        }

        val newPointExponent = value.pointExponent + dropped.length
        if (!roundUp) return Exact(kept, newPointExponent)

        val bumped = increment(kept)
        return if (bumped.length > kept.length) {
            // Carried past the leading digit, as 999 -> 1000.
            Exact(bumped.substring(0, kept.length), newPointExponent + 1)
        } else {
            Exact(bumped, newPointExponent)
        }
    }

    /** Adds one to a decimal string, growing it if it carries out. */
    private fun increment(digits: String): String {
        val out = digits.toCharArray()
        var index = out.size - 1
        while (index >= 0) {
            if (out[index] != '9') {
                out[index] = out[index] + 1
                return out.concatToString()
            }
            out[index] = '0'
            index--
        }
        return "1" + out.concatToString()
    }

    /** The exact decimal digits of a finite, positive double. */
    private fun exactDigits(value: Double): Exact {
        val bits = value.toRawBits()
        val exponentField = ((bits ushr 52) and 0x7FF).toInt()
        val mantissaField = bits and 0x000FFFFFFFFFFFFFL
        val mantissa: Long
        val exponent: Int
        if (exponentField == 0) {
            mantissa = mantissaField // subnormal: no implicit leading bit
            exponent = -1074
        } else {
            mantissa = mantissaField or 0x0010000000000000L
            exponent = exponentField - 1075
        }

        var chunks = fromLong(mantissa)
        val pointExponent: Int
        if (exponent >= 0) {
            // 2^29 is the largest power of two that keeps chunk * factor in a Long.
            var remaining = exponent
            while (remaining > 0) {
                val step = if (remaining > 29) 29 else remaining
                chunks = multiply(chunks, 1L shl step)
                remaining -= step
            }
            pointExponent = 0
        } else {
            // 1/2^k == 5^k / 10^k, so scale by 5^k and shift the point by k.
            var remaining = -exponent
            while (remaining > 0) {
                val step = if (remaining > 13) 13 else remaining
                chunks = multiply(chunks, pow5(step))
                remaining -= step
            }
            pointExponent = exponent
        }
        return Exact(toDecimalString(chunks), pointExponent)
    }

    private fun pow5(exponent: Int): Long {
        var result = 1L
        repeat(exponent) { result *= 5L }
        return result
    }

    private fun fromLong(value: Long): LongArray {
        if (value == 0L) return longArrayOf(0L)
        var remaining = value
        val chunks = ArrayList<Long>(3)
        while (remaining > 0) {
            chunks.add(remaining % BASE)
            remaining /= BASE
        }
        return chunks.toLongArray()
    }

    /** Little-endian base-10^9 multiply by a factor small enough to stay exact. */
    private fun multiply(chunks: LongArray, factor: Long): LongArray {
        val out = LongArray(chunks.size + 3)
        var carry = 0L
        for (index in chunks.indices) {
            val product = chunks[index] * factor + carry
            out[index] = product % BASE
            carry = product / BASE
        }
        var index = chunks.size
        while (carry > 0) {
            out[index++] = carry % BASE
            carry /= BASE
        }
        var size = out.size
        while (size > 1 && out[size - 1] == 0L) size--
        return out.copyOf(size)
    }

    /** Most-significant-first decimal digits, without leading zeros. */
    private fun toDecimalString(chunks: LongArray): String = buildString {
        append(chunks[chunks.size - 1].toString())
        for (index in chunks.size - 2 downTo 0) {
            val chunk = chunks[index].toString()
            repeat(BASE_DIGITS - chunk.length) { append('0') }
            append(chunk)
        }
    }
}
