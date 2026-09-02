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
 *  Based on LuaJ (https://luaj.org)
 *  Original work Copyright (c) 2009 Luaj.org
 *  Modifications Copyright (c) 2026 Blueva Development
 *
 *  SPDX-License-Identifier: MIT
 ******************************************************************************/
package net.blueva.luak

/**
 * Base class for representing numbers as lua values directly.
 * 
 * 
 * The main subclasses are [LuaInteger] which holds values that fit in a java int,
 * and [LuaDouble] which holds all other number values.
 * @see LuaInteger
 * 
 * @see LuaDouble
 * 
 * @see LuaValue
 */
abstract
class LuaNumber : LuaValue() {
    override fun type(): Int {
        return TNUMBER
    }

    override fun typename(): String? {
        return "number"
    }

    override fun checknumber(): LuaNumber {
        return this
    }

    override fun checknumber(errmsg: String?): LuaNumber {
        return this
    }

    override fun optnumber(defval: LuaNumber?): LuaNumber {
        return this
    }

    override fun tonumber(): LuaValue {
        return this
    }

    override fun isnumber(): Boolean {
        return true
    }

    override fun isstring(): Boolean {
        return true
    }

    override fun getmetatable(): LuaValue? {
        return net.blueva.luak.LuaNumber.Companion.s_metatable
    }

    override fun concat(rhs: LuaValue): LuaValue {
        return rhs.concatTo(this)
    }

    override fun concat(rhs: Buffer): Buffer {
        return rhs.concatTo(this)
    }

    override fun concatTo(lhs: LuaNumber): LuaValue {
        return strvalue()!!.concatTo((lhs.strvalue())!!)
    }

    override fun concatTo(lhs: LuaString): LuaValue {
        return strvalue()!!.concatTo(lhs)
    }

    companion object {
        /** Shared static metatable for all number values represented in lua.  */
        var s_metatable: LuaValue? = null
    }
}
/**
 * `//` over two values already known to be numbers.
 *
 * Two integers floor-divide as integers (rounding towards negative infinity,
 * and wrapping for `mininteger // -1` exactly as the reference does); anything
 * else is computed as a float. Kept next to the number types because
 * [LuaInteger], [LuaDouble], and [LuaString] all need the same answer.
 */
/**
 * Lua's `%`, which is a floored modulo rather than C's truncated one.
 *
 * Two integers give an integer; anything else gives a float. The sign of the
 * result follows the divisor, so `-5 % 3` is `1`, not `-2`.
 */
internal fun luaMod(lhs: LuaValue, rhs: LuaValue): LuaValue {
    if (lhs.isinttype() && rhs.isinttype()) {
        return LuaValue.valueOf(luaIntegerMod(lhs.tolong(), rhs.tolong()))
    }
    return LuaValue.valueOf(luaFloatMod(lhs.todouble(), rhs.todouble()))
}

/** Floored modulo of two integers, raising on a zero divisor as Lua does. */
internal fun luaIntegerMod(x: Long, y: Long): Long {
    if (y == 0L) LuaValue.error("attempt to perform 'n%0'")
    if (y == -1L) return 0L // avoids overflow on the minimum integer
    val remainder = x % y
    // C truncates towards zero, so a remainder whose sign disagrees with the
    // divisor is one divisor short of the floored answer.
    return if (remainder != 0L && (remainder xor y) < 0L) remainder + y else remainder
}

/** Floored modulo of two floats, matching upstream's `luai_nummod`. */
internal fun luaFloatMod(x: Double, y: Double): Double {
    var remainder = x % y
    if (if (remainder > 0) y < 0 else (remainder < 0 && y > 0)) remainder += y
    return remainder
}

internal fun luaFloorDiv(lhs: LuaValue, rhs: LuaValue): LuaValue {
    if (lhs.isinttype() && rhs.isinttype()) {
        val x: Long = lhs.tolong()
        val y: Long = rhs.tolong()
        if (y == 0L) LuaValue.error("attempt to divide by zero")
        if (y == -1L) return LuaValue.valueOf(-x) // avoids overflow on mininteger
        var quotient = x / y
        if ((x xor y) < 0L && quotient * y != x) quotient--
        return LuaValue.valueOf(quotient)
    }
    return LuaValue.valueOf(kotlin.math.floor(lhs.todouble() / rhs.todouble()))
}

/**
 * The integer a bitwise operand denotes, or an error if it denotes none.
 *
 * Since 5.3 the bitwise operators work on 64-bit integers. A float is accepted
 * when its value is integral (`3.0 & 1` is fine) and rejected otherwise; unlike
 * the arithmetic operators, strings are not coerced, a restriction 5.4 made
 * explicit.
 */
internal fun luaBitwiseOperand(value: LuaValue): Long {
    if (value.isinttype()) return value.tolong()
    if (value.isnumber() && value !is LuaString) {
        val asDouble: Double = value.todouble()
        if (fitsInteger(asDouble)) return asDouble.toLong()
        LuaValue.error("number has no integer representation")
    }
    LuaValue.error("attempt to perform bitwise operation on a " + value.objtypename() + " value")
    return 0L
}

/**
 * `x << y`, with `x >> y` expressed as `luaShiftLeft(x, -y)`.
 *
 * A shift of 64 bits or more clears the value, and a negative count reverses
 * the direction. Right shifts are logical, not arithmetic, which is why
 * `-1 >> 1` is `maxinteger` rather than `-1`.
 */
internal fun luaShiftLeft(x: Long, y: Long): Long {
    if (y < 0L) {
        if (y <= -64L) return 0L
        return x ushr (-y).toInt()
    }
    if (y >= 64L) return 0L
    return x shl y.toInt()
}

/**
 * Whether [value] denotes an integer, without raising if it does not.
 *
 * The compiler needs this to decide whether a bitwise expression can be folded:
 * `1.5 & 1` must compile and fail at run time, where `pcall` can see it, rather
 * than failing the compilation.
 */
internal fun luaHasIntegerRepresentation(value: LuaValue): Boolean {
    if (value.isinttype()) return true
    if (!value.isnumber() || value is LuaString) return false
    return fitsInteger(value.todouble())
}

/**
 * True when [value] is exactly some 64-bit integer.
 *
 * The range has to be checked as well as the round trip: converting a double
 * outside it saturates at the nearest end, and converting that back lands on
 * the same double again, so a round trip alone would accept `2^63`.
 */
private fun fitsInteger(value: Double): Boolean {
    if (value < -9.2233720368547758E18 || value >= 9.2233720368547758E18) return false
    return value.toLong().toDouble() == value
}

/**
 * Whether the integer [i] and the float [f] denote the same number.
 *
 * Going through `i.toDouble()` would be wrong past 2^53, where distinct
 * integers share a double; the comparison is done in integer space instead,
 * which is exact for every value a Long can hold.
 */
internal fun luaIntegerEqualsFloat(i: Long, f: Double): Boolean {
    if (f.isNaN() || f.isInfinite()) return false
    if (f != kotlin.math.floor(f)) return false
    // 2^63 is the first float above the integer range; -2^63 is representable.
    if (f < -9.2233720368547758E18 || f >= 9.2233720368547758E18) return false
    return f.toLong() == i
}

/**
 * Exact ordering between the two number subtypes.
 *
 * Converting the integer to a double first would be wrong past 2^53, where the
 * conversion rounds; comparing against the float's floor or ceiling keeps every
 * case exact. Which of the two to use differs per operator: `i < f` holds when
 * `i` is below the smallest integer at or above `f`, while `i <= f` holds when
 * `i` is at most the largest integer at or below it.
 */
internal fun luaIntegerLessThanFloat(i: Long, f: Double): Boolean {
    if (f.isNaN()) return false
    if (f >= 9.2233720368547758E18) return true // above every representable integer
    if (f < -9.2233720368547758E18) return false
    return i < kotlin.math.ceil(f).toLong() || (kotlin.math.ceil(f) != f && i == kotlin.math.floor(f).toLong())
}

/** Whether the integer [i] is less than or equal to the float [f], exactly. */
internal fun luaIntegerLessOrEqualFloat(i: Long, f: Double): Boolean {
    if (f.isNaN()) return false
    if (f >= 9.2233720368547758E18) return true
    if (f < -9.2233720368547758E18) return false
    return i <= kotlin.math.floor(f).toLong()
}

/** Whether the float [f] is strictly less than the integer [i], exactly. */
internal fun luaFloatLessThanInteger(f: Double, i: Long): Boolean {
    if (f.isNaN()) return false
    if (f >= 9.2233720368547758E18) return false
    if (f < -9.2233720368547758E18) return true
    return kotlin.math.floor(f).toLong() < i
}

/** Whether the float [f] is less than or equal to the integer [i], exactly. */
internal fun luaFloatLessOrEqualInteger(f: Double, i: Long): Boolean {
    if (f.isNaN()) return false
    if (f >= 9.2233720368547758E18) return false
    if (f < -9.2233720368547758E18) return true
    return kotlin.math.ceil(f).toLong() <= i
}
