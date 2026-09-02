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

import net.blueva.luak.lib.MathLib

/**
 * Extension of [LuaNumber] which can hold a Java double as its value.
 * 
 * 
 * These instance are not instantiated directly by clients, but indirectly
 * via the static functions [LuaValue.valueOf] or [LuaValue.valueOf]
 * functions.  This ensures that values which can be represented as int
 * are wrapped in [LuaInteger] instead of [LuaDouble].
 * 
 * 
 * Almost all API's implemented in LuaDouble are defined and documented in [LuaValue].
 * 
 * 
 * However the constants [.NAN], [.POSINF], [.NEGINF],
 * [.JSTR_NAN], [.JSTR_POSINF], and [.JSTR_NEGINF] may be useful
 * when dealing with Nan or Infinite values.
 * 
 * 
 * LuaDouble also defines functions for handling the unique math rules of lua devision and modulo in
 * 
 *  * [.ddiv]
 *  * [.ddiv_d]
 *  * [.dmod]
 *  * [.dmod_d]
 * 
 * 
 * 
 * @see LuaValue
 * 
 * @see LuaNumber
 * 
 * @see LuaInteger
 * 
 * @see LuaValue.valueOf
 * @see LuaValue.valueOf
 */
class LuaDouble
/** Don't allow ints to be boxed by DoubleValues   */ private constructor(
    /** The value being held by this instance.  */
    val v: Double
) : LuaNumber() {
    override fun hashCode(): Int {
        val l: Long = (v + 1).toBits()
        return ((l shr 32).toInt()) + l.toInt()
    }

    override fun islong(): Boolean {
        return v == v.toLong().toDouble()
    }

    override fun tobyte(): Byte {
        return v.toLong().toByte()
    }

    override fun tochar(): Char {
        return Char(v.toLong().toUShort())
    }

    override fun todouble(): Double {
        return v
    }

    override fun tofloat(): Float {
        return v.toFloat()
    }

    override fun toint(): Int {
        return v.toLong().toInt()
    }

    override fun tolong(): Long {
        return v.toLong()
    }

    override fun toshort(): Short {
        return v.toLong().toShort()
    }

    override fun optdouble(defval: Double): Double {
        return v
    }

    // The opt forms differ from the check forms only in what a missing
    // argument does, which cannot happen once there is a value here, so they
    // hold this float to the same standard.
    override fun optint(defval: Int): Int {
        return checkint()
    }

    override fun optinteger(defval: LuaInteger?): LuaInteger {
        return checkinteger()
    }

    override fun optlong(defval: Long): Long {
        return checklong()
    }

    override fun checkinteger(): LuaInteger {
        return (LuaInteger.valueOf(checklong()))!!
    }

    // unary operators
    override fun neg(): LuaValue {
        return (net.blueva.luak.LuaDouble.Companion.valueOf(-v))!!
    }

    // object equality, used for key comparison
    override fun equals(o: Any?): Boolean {
        return if (o is LuaDouble) o.v == v else false
    }

    // equality w/ metatable processing
    override fun eq(`val`: LuaValue?): LuaValue {
        val `val` = `val`!!
        return (if (`val`.raweq(v)) TRUE else FALSE)!!
    }

    override fun eq_b(`val`: LuaValue?): Boolean {
        val `val` = `val`!!
        return `val`.raweq(v)
    }

    // equality w/o metatable processing
    override fun raweq(`val`: LuaValue?): Boolean {
        val `val` = `val`!!
        return `val`.raweq(v)
    }

    override fun raweq(`val`: Double): Boolean {
        val `val` = `val`!!
        return v == `val`
    }

    override fun raweq(`val`: Long): Boolean {
        return luaIntegerEqualsFloat(`val`, v)
    }

    // basic binary arithmetic
    override fun add(rhs: LuaValue): LuaValue {
        return rhs.add(v)
    }

    override fun add(lhs: Double): LuaValue {
        return (net.blueva.luak.LuaDouble.Companion.valueOf(lhs + v))!!
    }

    override fun add(rhs: Long): LuaValue {
        return (net.blueva.luak.LuaDouble.Companion.valueOf(rhs + v))!!
    }

    override fun sub(rhs: LuaValue): LuaValue {
        return rhs.subFrom(v)
    }

    override fun sub(rhs: Double): LuaValue? {
        return net.blueva.luak.LuaDouble.Companion.valueOf(v - rhs)
    }

    override fun sub(rhs: Long): LuaValue? {
        return net.blueva.luak.LuaDouble.Companion.valueOf(v - rhs)
    }

    override fun subFrom(lhs: Double): LuaValue {
        return (net.blueva.luak.LuaDouble.Companion.valueOf(lhs - v))!!
    }

    override fun subFrom(lhs: Long): LuaValue {
        return (net.blueva.luak.LuaDouble.Companion.valueOf(lhs - v))!!
    }

    override fun mul(rhs: LuaValue): LuaValue {
        return rhs.mul(v)
    }

    override fun mul(lhs: Double): LuaValue {
        return (net.blueva.luak.LuaDouble.Companion.valueOf(lhs * v))!!
    }

    override fun mul(lhs: Long): LuaValue {
        return (net.blueva.luak.LuaDouble.Companion.valueOf(lhs * v))!!
    }

    override fun pow(rhs: LuaValue): LuaValue {
        return rhs.powWith(v)
    }

    override fun pow(rhs: Double): LuaValue {
        return MathLib.dpow(v, rhs)
    }

    override fun pow(rhs: Long): LuaValue {
        return MathLib.dpow(v, (rhs).toDouble())
    }

    override fun powWith(lhs: Double): LuaValue {
        return MathLib.dpow(lhs, v)
    }

    override fun powWith(lhs: Long): LuaValue {
        return MathLib.dpow((lhs).toDouble(), v)
    }

    override fun band(rhs: LuaValue): LuaValue = bitwise(net.blueva.luak.LuaValue.Companion.BAND, rhs)
    override fun bor(rhs: LuaValue): LuaValue = bitwise(net.blueva.luak.LuaValue.Companion.BOR, rhs)
    override fun bxor(rhs: LuaValue): LuaValue = bitwise(net.blueva.luak.LuaValue.Companion.BXOR, rhs)
    override fun shl(rhs: LuaValue): LuaValue = bitwise(net.blueva.luak.LuaValue.Companion.SHL, rhs)
    override fun shr(rhs: LuaValue): LuaValue = bitwise(net.blueva.luak.LuaValue.Companion.SHR, rhs)

    override fun bnot(): LuaValue = LuaValue.valueOf(luaBitwiseOperand(this).inv())

    private fun bitwise(tag: LuaString, rhs: LuaValue): LuaValue {
        if (!rhs.isnumber() || rhs is LuaString) return arithmt(tag, rhs)
        val x: Long = luaBitwiseOperand(this)
        val y: Long = luaBitwiseOperand(rhs)
        return when (tag) {
            net.blueva.luak.LuaValue.Companion.BAND -> LuaValue.valueOf(x and y)
            net.blueva.luak.LuaValue.Companion.BOR -> LuaValue.valueOf(x or y)
            net.blueva.luak.LuaValue.Companion.BXOR -> LuaValue.valueOf(x xor y)
            net.blueva.luak.LuaValue.Companion.SHL -> LuaValue.valueOf(luaShiftLeft(x, y))
            else -> LuaValue.valueOf(luaShiftLeft(x, -y))
        }
    }

    override fun idiv(rhs: LuaValue): LuaValue {
        val other: LuaValue = rhs.tonumber()
        if (other.isnil()) return arithmt(net.blueva.luak.LuaValue.Companion.IDIV, rhs)
        return luaFloorDiv(this, other)
    }

    override fun div(rhs: LuaValue): LuaValue {
        return rhs.divInto(v)
    }

    override fun div(rhs: Double): LuaValue? {
        return net.blueva.luak.LuaDouble.Companion.ddiv(v, rhs)
    }

    override fun div(rhs: Long): LuaValue? {
        return net.blueva.luak.LuaDouble.Companion.ddiv(v, rhs.toDouble())
    }

    override fun divInto(lhs: Double): LuaValue {
        return (net.blueva.luak.LuaDouble.Companion.ddiv(lhs, v))!!
    }

    override fun mod(rhs: LuaValue): LuaValue {
        return rhs.modFrom(v)
    }

    override fun mod(rhs: Double): LuaValue? {
        return net.blueva.luak.LuaDouble.Companion.dmod(v, rhs)
    }

    override fun mod(rhs: Long): LuaValue? {
        return net.blueva.luak.LuaDouble.Companion.dmod(v, rhs.toDouble())
    }

    override fun modFrom(lhs: Double): LuaValue {
        return (net.blueva.luak.LuaDouble.Companion.dmod(lhs, v))!!
    }


    // relational operators
    override fun lt(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaNumber) (if (rhs.gt_b(v)) TRUE else FALSE) else super.lt(rhs))!!
    }

    override fun lt(rhs: Double): LuaValue {
        return (if (v < rhs) TRUE else FALSE)!!
    }

    override fun lt(rhs: Long): LuaValue {
        return (if (luaFloatLessThanInteger(v, rhs)) TRUE else FALSE)!!
    }

    override fun lt_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaNumber) rhs.gt_b(v) else super.lt_b(rhs)
    }

    override fun lt_b(rhs: Long): Boolean {
        return luaFloatLessThanInteger(v, rhs)
    }

    override fun lt_b(rhs: Double): Boolean {
        return v < rhs
    }

    override fun lteq(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaNumber) (if (rhs.gteq_b(v)) TRUE else FALSE) else super.lteq(rhs))!!
    }

    override fun lteq(rhs: Double): LuaValue {
        return (if (v <= rhs) TRUE else FALSE)!!
    }

    override fun lteq(rhs: Long): LuaValue {
        return (if (luaFloatLessOrEqualInteger(v, rhs)) TRUE else FALSE)!!
    }

    override fun lteq_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaNumber) rhs.gteq_b(v) else super.lteq_b(rhs)
    }

    override fun lteq_b(rhs: Long): Boolean {
        return luaFloatLessOrEqualInteger(v, rhs)
    }

    override fun lteq_b(rhs: Double): Boolean {
        return v <= rhs
    }

    override fun gt(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaNumber) (if (rhs.lt_b(v)) TRUE else FALSE) else super.gt(rhs))!!
    }

    override fun gt(rhs: Double): LuaValue {
        return (if (v > rhs) TRUE else FALSE)!!
    }

    override fun gt(rhs: Long): LuaValue {
        return (if (luaIntegerLessThanFloat(rhs, v)) TRUE else FALSE)!!
    }

    override fun gt_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaNumber) rhs.lt_b(v) else super.gt_b(rhs)
    }

    override fun gt_b(rhs: Long): Boolean {
        return luaIntegerLessThanFloat(rhs, v)
    }

    override fun gt_b(rhs: Double): Boolean {
        return v > rhs
    }

    override fun gteq(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaNumber) (if (rhs.lteq_b(v)) TRUE else FALSE) else super.gteq(rhs))!!
    }

    override fun gteq(rhs: Double): LuaValue {
        return (if (v >= rhs) TRUE else FALSE)!!
    }

    override fun gteq(rhs: Long): LuaValue {
        return (if (luaIntegerLessOrEqualFloat(rhs, v)) TRUE else FALSE)!!
    }

    override fun gteq_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaNumber) rhs.lteq_b(v) else super.gteq_b(rhs)
    }

    override fun gteq_b(rhs: Long): Boolean {
        return luaIntegerLessOrEqualFloat(rhs, v)
    }

    override fun gteq_b(rhs: Double): Boolean {
        return v >= rhs
    }

    // string comparison
    override fun strcmp(rhs: LuaString?): Int {
        typerror("attempt to compare number with string")
        return 0
    }

    override fun tojstring(): String {
        return DecimalFormat.luaFloat(v)
    }

    override fun strvalue(): LuaString {
        return LuaString.valueOf(tojstring())
    }

    override fun optstring(defval: LuaString?): LuaString {
        return LuaString.valueOf(tojstring())
    }

    override fun tostring(): LuaValue {
        return LuaString.valueOf(tojstring())
    }

    override fun optjstring(defval: String?): String? {
        return tojstring()
    }

    override fun optnumber(defval: LuaNumber?): LuaNumber {
        return this
    }

    override fun isnumber(): Boolean {
        return true
    }

    override fun isstring(): Boolean {
        return true
    }

    override fun tonumber(): LuaValue {
        return this
    }

    override fun checkint(): Int {
        return checklong().toInt()
    }

    /**
     * The integer this float denotes, or an error if it denotes none.
     *
     * Truncating silently would let `string.rep("x", 2.5)` mean `2`, where Lua
     * requires a value that is exactly an integer.
     */
    override fun checklong(): Long {
        val whole: Long = v.toLong()
        if (whole.toDouble() != v) LuaValue.error("number has no integer representation")
        return whole
    }

    override fun checknumber(): LuaNumber {
        return this
    }

    override fun checkdouble(): Double {
        return v
    }

    override fun checkjstring(): String? {
        return tojstring()
    }

    override fun checkstring(): LuaString {
        return LuaString.valueOf(tojstring())
    }

    override fun isvalidkey(): Boolean {
        return !(v).isNaN()
    }

    companion object {
        /** Constant LuaDouble representing NaN (not a number)  */
        val NAN: LuaDouble = net.blueva.luak.LuaDouble(Double.NaN)

        /** Constant LuaDouble representing positive infinity  */
        val POSINF: LuaDouble = net.blueva.luak.LuaDouble(Double.POSITIVE_INFINITY)

        /** Constant LuaDouble representing negative infinity  */
        val NEGINF: LuaDouble = net.blueva.luak.LuaDouble(Double.NEGATIVE_INFINITY)

        /** Constant String representation for NaN (not a number), "nan"  */
        val JSTR_NAN: String = "nan"

        /** Constant String representation for positive infinity, "inf"  */
        val JSTR_POSINF: String = "inf"

        /** Constant String representation for negative infinity, "-inf"  */
        val JSTR_NEGINF: String = "-inf"

        /**
         * A float stays a float.
         *
         * Luak inherited LuaJ's habit of folding a double with an integral
         * value into a [LuaInteger], which made sense when Lua 5.2 had a single
         * number type. Since 5.3 the two subtypes are distinguishable from Lua
         * (`math.type`, `2.0` printing as `2.0`, `1 // 0.0` giving `inf`), so
         * the fold has to go.
         */
        fun valueOf(d: Double): LuaNumber? {
            return net.blueva.luak.LuaDouble(d)
        }

        /** Divide two double numbers according to lua math, and return a [LuaValue] result.
         * @param lhs Left-hand-side of the division.
         * @param rhs Right-hand-side of the division.
         * @return [LuaValue] for the result of the division,
         * taking into account positive and negiative infinity, and Nan
         * @see .ddiv_d
         */
        fun ddiv(lhs: Double, rhs: Double): LuaValue? {
            // Plain IEEE division. Special-casing a zero divisor lost the sign
            // of the zero, so 1/-0.0 came out positive.
            return net.blueva.luak.LuaDouble.Companion.valueOf(lhs / rhs)
        }

        /** Divide two double numbers according to lua math, and return a double result.
         * @param lhs Left-hand-side of the division.
         * @param rhs Right-hand-side of the division.
         * @return Value of the division, taking into account positive and negative infinity, and Nan
         * @see .ddiv
         */
        fun ddiv_d(lhs: Double, rhs: Double): Double {
            return lhs / rhs
        }

        /** Take modulo double numbers according to lua math, and return a [LuaValue] result.
         * @param lhs Left-hand-side of the modulo.
         * @param rhs Right-hand-side of the modulo.
         * @return [LuaValue] for the result of the modulo,
         * using lua's rules for modulo
         * @see .dmod_d
         * @see luaFloatMod
         */
        fun dmod(lhs: Double, rhs: Double): LuaValue? {
            return net.blueva.luak.LuaDouble.Companion.valueOf(luaFloatMod(lhs, rhs))
        }

        /** Take modulo for double numbers according to lua math, and return a double result.
         * @param lhs Left-hand-side of the modulo.
         * @param rhs Right-hand-side of the modulo.
         * @return double value for the result of the modulo,
         * using lua's rules for modulo
         * @see .dmod
         */
        fun dmod_d(lhs: Double, rhs: Double): Double {
            return luaFloatMod(lhs, rhs)
        }
    }
}
