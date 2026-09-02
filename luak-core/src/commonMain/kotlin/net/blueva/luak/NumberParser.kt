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
 * Reads a Lua numeral out of text, keeping the integer/float distinction.
 *
 * Both the lexer and the string-to-number coercion behind `tonumber` and
 * arithmetic on strings go through here, so a literal and the same text passed
 * to `tonumber` cannot disagree.
 *
 * The order matters and is the one upstream uses: a numeral is tried as an
 * integer first and only then as a float, so `"3"` is the integer `3` while
 * `"3.0"` and `"3e0"` are floats. A decimal integer too large for the 64-bit
 * subtype is not an error - it becomes a float. A *hexadecimal* one wraps
 * around instead, which is what the manual points at for code that wants the
 * pre-5.3 behaviour.
 */
internal object NumberParser {

    /** Digits past which the hex-float accumulator cannot stay exact. */
    private const val MAX_SIGNIFICANT_HEX_DIGITS = 30

    /**
     * The numeral in [text], or `null` when it is not one.
     *
     * @return a [LuaInteger] or a [LuaDouble], never any other type
     */
    fun parse(text: String): LuaValue? {
        parseInteger(text)?.let { return LuaValue.valueOf(it) }
        return parseFloat(text)?.let { LuaValue.valueOf(it) }
    }

    /**
     * The integer denoted by [text], or `null` if it denotes something else.
     *
     * A decimal numeral that overflows answers `null` so the caller can retry
     * it as a float; a hexadecimal one wraps and always succeeds.
     */
    fun parseInteger(text: String): Long? {
        var index = skipSpaces(text, 0)
        var negative = false
        if (index < text.length && (text[index] == '-' || text[index] == '+')) {
            negative = text[index] == '-'
            index++
        }
        var accumulator = 0L // unsigned; overflow past the sign is intended in hex
        var empty = true
        if (index + 1 < text.length && text[index] == '0' &&
            (text[index + 1] == 'x' || text[index + 1] == 'X')
        ) {
            index += 2
            while (index < text.length && isHexDigit(text[index])) {
                accumulator = accumulator * 16L + hexValue(text[index])
                empty = false
                index++
            }
        } else {
            val limit = Long.MAX_VALUE / 10L
            val lastDigit = (Long.MAX_VALUE % 10L).toInt()
            while (index < text.length && text[index] in '0'..'9') {
                val digit = text[index] - '0'
                // One digit short of the limit the sign decides, since the
                // negative range reaches one further than the positive one.
                if (accumulator >= limit &&
                    (accumulator > limit || digit > lastDigit + (if (negative) 1 else 0))
                ) {
                    return null
                }
                accumulator = accumulator * 10L + digit
                empty = false
                index++
            }
        }
        index = skipSpaces(text, index)
        if (empty || index != text.length) return null
        return if (negative) -accumulator else accumulator
    }

    /**
     * The float denoted by [text], or `null` if it denotes something else.
     *
     * `inf` and `nan` are deliberately not accepted: Lua has no literal for
     * either, and letting them through here would invent one.
     */
    fun parseFloat(text: String): Double? {
        val mode = text.firstOrNull { it == '.' || it == 'x' || it == 'X' || it == 'n' || it == 'N' }
        if (mode == 'n' || mode == 'N') return null
        return if (mode == 'x' || mode == 'X') parseHexFloat(text) else parseDecimalFloat(text)
    }

    /** C's `strtod` on a plain decimal numeral, with nothing left over. */
    private fun parseDecimalFloat(text: String): Double? {
        var index = skipSpaces(text, 0)
        val start = index
        if (index < text.length && (text[index] == '-' || text[index] == '+')) index++
        var digits = 0
        while (index < text.length && text[index] in '0'..'9') {
            index++
            digits++
        }
        if (index < text.length && text[index] == '.') {
            index++
            while (index < text.length && text[index] in '0'..'9') {
                index++
                digits++
            }
        }
        if (digits == 0) return null
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            var lookahead = index + 1
            if (lookahead < text.length && (text[lookahead] == '-' || text[lookahead] == '+')) lookahead++
            var exponentDigits = 0
            while (lookahead < text.length && text[lookahead] in '0'..'9') {
                lookahead++
                exponentDigits++
            }
            // An 'e' with no digits after it is not part of the numeral, so it
            // is left behind for the trailing-character check to reject.
            if (exponentDigits > 0) index = lookahead
        }
        val numeral = text.substring(start, index)
        if (skipSpaces(text, index) != text.length) return null
        // The numeral is already known to be well formed, so the platform
        // parser is only being asked to round it correctly.
        return numeral.toDoubleOrNull()
    }

    /** C's `strtod` on a hexadecimal numeral, as `lua_strx2number` reads it. */
    private fun parseHexFloat(text: String): Double? {
        var index = skipSpaces(text, 0)
        var negative = false
        if (index < text.length && (text[index] == '-' || text[index] == '+')) {
            negative = text[index] == '-'
            index++
        }
        if (index + 1 >= text.length || text[index] != '0') return null
        if (text[index + 1] != 'x' && text[index + 1] != 'X') return null
        index += 2

        var mantissa = 0.0
        var significant = 0
        var insignificant = 0
        var exponent = 0
        var seenDot = false
        while (index < text.length) {
            val c = text[index]
            if (c == '.') {
                if (seenDot) break
                seenDot = true
            } else if (isHexDigit(c)) {
                if (significant == 0 && c == '0') {
                    insignificant++
                } else if (++significant <= MAX_SIGNIFICANT_HEX_DIGITS) {
                    mantissa = mantissa * 16.0 + hexValue(c)
                } else {
                    // Past the accumulator's reach: the digit still shifts the
                    // value even though its own contribution is lost.
                    exponent++
                }
                if (seenDot) exponent--
            } else {
                break
            }
            index++
        }
        if (significant + insignificant == 0) return null
        exponent *= 4 // each hex digit is four binary ones

        if (index < text.length && (text[index] == 'p' || text[index] == 'P')) {
            index++
            var negativeExponent = false
            if (index < text.length && (text[index] == '-' || text[index] == '+')) {
                negativeExponent = text[index] == '-'
                index++
            }
            if (index >= text.length || text[index] !in '0'..'9') return null
            var value = 0
            while (index < text.length && text[index] in '0'..'9') {
                value = value * 10 + (text[index] - '0')
                index++
            }
            exponent += if (negativeExponent) -value else value
        }
        if (skipSpaces(text, index) != text.length) return null
        if (negative) mantissa = -mantissa
        return ldexp(mantissa, exponent)
    }

    /**
     * The integer [text] denotes in [base], wrapping on overflow.
     *
     * This is `tonumber`'s two-argument form, which unlike the one-argument
     * form never produces a float.
     */
    fun parseInteger(text: String, base: Int): Long? {
        if (base < 2 || base > 36) return null
        var index = skipSpaces(text, 0)
        var negative = false
        if (index < text.length && (text[index] == '-' || text[index] == '+')) {
            negative = text[index] == '-'
            index++
        }
        var accumulator = 0L
        var empty = true
        while (index < text.length) {
            val digit = digitValue(text[index])
            if (digit < 0 || digit >= base) break
            accumulator = accumulator * base + digit
            empty = false
            index++
        }
        index = skipSpaces(text, index)
        if (empty || index != text.length) return null
        return if (negative) -accumulator else accumulator
    }

    /** `value * 2^exponent`, split so no single step leaves the double range. */
    private fun ldexp(value: Double, exponent: Int): Double {
        var result = value
        var remaining = exponent
        while (remaining > 1000) {
            result *= TWO_POW_1000
            remaining -= 1000
        }
        while (remaining < -1000) {
            result /= TWO_POW_1000
            remaining += 1000
        }
        var step = 1.0
        var factor = 2.0
        var count = if (remaining < 0) -remaining else remaining
        while (count > 0) {
            if (count and 1 == 1) step *= factor
            factor *= factor
            count = count shr 1
        }
        return if (remaining < 0) result / step else result * step
    }

    private val TWO_POW_1000: Double = run {
        var result = 1.0
        repeat(1000) { result *= 2.0 }
        result
    }

    private fun skipSpaces(text: String, from: Int): Int {
        var index = from
        while (index < text.length && isSpace(text[index])) index++
        return index
    }

    /** The characters C's `isspace` accepts, which is what `strtod` skips. */
    private fun isSpace(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\n' || c == '\r' || c.code == 0x0B || c.code == 0x0C

    private fun isHexDigit(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun hexValue(c: Char): Int = when {
        c in '0'..'9' -> c - '0'
        c in 'a'..'f' -> c - 'a' + 10
        else -> c - 'A' + 10
    }

    private fun digitValue(c: Char): Int = when {
        c in '0'..'9' -> c - '0'
        c in 'a'..'z' -> c - 'a' + 10
        c in 'A'..'Z' -> c - 'A' + 10
        else -> -1
    }
}
