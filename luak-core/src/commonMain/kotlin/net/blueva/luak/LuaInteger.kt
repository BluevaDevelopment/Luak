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
 * Extension of [LuaNumber] which can hold a Java int as its value.
 * 
 * 
 * These instance are not instantiated directly by clients, but indirectly
 * via the static functions [LuaValue.valueOf] or [LuaValue.valueOf]
 * functions.  This ensures that policies regarding pooling of instances are
 * encapsulated.
 * 
 * 
 * There are no API's specific to LuaInteger that are useful beyond what is already
 * exposed in [LuaValue].
 * 
 * @see LuaValue
 * 
 * @see LuaNumber
 * 
 * @see LuaDouble
 * 
 * @see LuaValue.valueOf
 * @see LuaValue.valueOf
 */
class LuaInteger
/**
 * Package protected constructor.
 * @see LuaValue.valueOf
 */ internal constructor(
    /** The value being held by this instance.  */
    val v: Long
) : LuaNumber() {
    override fun isint(): Boolean {
        return true
    }

    override fun isinttype(): Boolean {
        return true
    }

    override fun islong(): Boolean {
        return true
    }

    override fun tobyte(): Byte {
        return v.toByte()
    }

    override fun tochar(): Char {
        return v.toInt().toChar()
    }

    override fun todouble(): Double {
        return v.toDouble()
    }

    override fun tofloat(): Float {
        return v.toFloat()
    }

    override fun toint(): Int {
        return v.toInt()
    }

    override fun tolong(): Long {
        return v
    }

    override fun toshort(): Short {
        return v.toShort()
    }

    override fun optdouble(defval: Double): Double {
        return v.toDouble()
    }

    override fun optint(defval: Int): Int {
        return v.toInt()
    }

    override fun optinteger(defval: LuaInteger?): LuaInteger {
        return this
    }

    override fun optlong(defval: Long): Long {
        return v
    }

    override fun tojstring(): String {
        return v.toString()
    }

    override fun strvalue(): LuaString {
        return LuaString.valueOf(v.toString())
    }

    override fun optstring(defval: LuaString?): LuaString {
        return LuaString.valueOf(v.toString())
    }

    override fun tostring(): LuaValue {
        return LuaString.valueOf(v.toString())
    }

    override fun optjstring(defval: String?): String {
        return v.toString()
    }

    override fun checkinteger(): LuaInteger {
        return this
    }

    override fun isstring(): Boolean {
        return true
    }

    override fun hashCode(): Int {
        return net.blueva.luak.LuaInteger.Companion.hashCode(v)
    }

    // unary operators
    override fun neg(): LuaValue {
        return (net.blueva.luak.LuaInteger.Companion.valueOf(-v))!!
    }

    // object equality, used for key comparison
    override fun equals(o: Any?): Boolean {
        return if (o is LuaInteger) o.v == v else false
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
        return luaIntegerEqualsFloat(v, `val`)
    }

    override fun raweq(`val`: Long): Boolean {
        val `val` = `val`!!
        return v == `val`
    }

    // arithmetic operators
    override fun add(rhs: LuaValue): LuaValue {
        return rhs.add(v)
    }

    override fun add(lhs: Double): LuaValue {
        return (LuaDouble.valueOf(lhs + v))!!
    }

    override fun add(lhs: Long): LuaValue {
        return (net.blueva.luak.LuaInteger.Companion.valueOf(lhs + v.toLong()))!!
    }

    override fun sub(rhs: LuaValue): LuaValue {
        return rhs.subFrom(v)
    }

    override fun sub(rhs: Double): LuaValue {
        return (LuaDouble.valueOf(v - rhs))!!
    }

    override fun sub(rhs: Long): LuaValue {
        return (net.blueva.luak.LuaInteger.Companion.valueOf(v - rhs))!!
    }

    override fun subFrom(lhs: Double): LuaValue {
        return (LuaDouble.valueOf(lhs - v))!!
    }

    override fun subFrom(lhs: Long): LuaValue {
        return (net.blueva.luak.LuaInteger.Companion.valueOf(lhs - v.toLong()))!!
    }

    override fun mul(rhs: LuaValue): LuaValue {
        return rhs.mul(v)
    }

    override fun mul(lhs: Double): LuaValue {
        return (LuaDouble.valueOf(lhs * v))!!
    }

    override fun mul(lhs: Long): LuaValue {
        return (net.blueva.luak.LuaInteger.Companion.valueOf(lhs * v.toLong()))!!
    }

    override fun pow(rhs: LuaValue): LuaValue {
        return rhs.powWith(v)
    }

    override fun pow(rhs: Double): LuaValue {
        return MathLib.dpow((v).toDouble(), rhs)
    }

    override fun pow(rhs: Long): LuaValue {
        return MathLib.dpow((v).toDouble(), (rhs).toDouble())
    }

    override fun powWith(lhs: Double): LuaValue {
        return MathLib.dpow(lhs, (v).toDouble())
    }

    override fun powWith(lhs: Long): LuaValue {
        return MathLib.dpow((lhs).toDouble(), (v).toDouble())
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
        // Read as a number first so a metamethod on the other side is handed
        // this operand as the integer it is, rather than a float of it.
        val other: LuaValue = rhs.tonumber()
        if (other.isnil()) return arithmt(net.blueva.luak.LuaValue.Companion.DIV, rhs)
        return (LuaDouble.ddiv((v).toDouble(), other.todouble()))!!
    }

    override fun div(rhs: Double): LuaValue {
        return (LuaDouble.ddiv((v).toDouble(), rhs))!!
    }

    override fun div(rhs: Long): LuaValue {
        return (LuaDouble.ddiv((v).toDouble(), (rhs).toDouble()))!!
    }

    override fun divInto(lhs: Double): LuaValue {
        return (LuaDouble.ddiv(lhs, (v).toDouble()))!!
    }

    override fun mod(rhs: LuaValue): LuaValue {
        val other: LuaValue = rhs.tonumber()
        if (other.isnil()) return arithmt(net.blueva.luak.LuaValue.Companion.MOD, rhs)
        return luaMod(this, other)
    }

    override fun mod(rhs: Double): LuaValue {
        return (LuaDouble.dmod((v).toDouble(), rhs))!!
    }

    override fun mod(rhs: Long): LuaValue {
        return (net.blueva.luak.LuaInteger.Companion.valueOf(luaIntegerMod(v, rhs)))!!
    }

    override fun modFrom(lhs: Double): LuaValue {
        return (LuaDouble.dmod(lhs, (v).toDouble()))!!
    }

    // relational operators
    override fun lt(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaNumber) (if (rhs.gt_b(v)) TRUE else FALSE) else super.lt(rhs))!!
    }

    override fun lt(rhs: Double): LuaValue {
        return (if (luaIntegerLessThanFloat(v, rhs)) TRUE else FALSE)!!
    }

    override fun lt(rhs: Long): LuaValue {
        return (if (v < rhs) TRUE else FALSE)!!
    }

    override fun lt_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaNumber) rhs.gt_b(v) else super.lt_b(rhs)
    }

    override fun lt_b(rhs: Long): Boolean {
        return v < rhs
    }

    override fun lt_b(rhs: Double): Boolean {
        return luaIntegerLessThanFloat(v, rhs)
    }

    override fun lteq(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaNumber) (if (rhs.gteq_b(v)) TRUE else FALSE) else super.lteq(rhs))!!
    }

    override fun lteq(rhs: Double): LuaValue {
        return (if (luaIntegerLessOrEqualFloat(v, rhs)) TRUE else FALSE)!!
    }

    override fun lteq(rhs: Long): LuaValue {
        return (if (v <= rhs) TRUE else FALSE)!!
    }

    override fun lteq_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaNumber) rhs.gteq_b(v) else super.lteq_b(rhs)
    }

    override fun lteq_b(rhs: Long): Boolean {
        return v <= rhs
    }

    override fun lteq_b(rhs: Double): Boolean {
        return luaIntegerLessOrEqualFloat(v, rhs)
    }

    override fun gt(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaNumber) (if (rhs.lt_b(v)) TRUE else FALSE) else super.gt(rhs))!!
    }

    override fun gt(rhs: Double): LuaValue {
        return (if (luaFloatLessThanInteger(rhs, v)) TRUE else FALSE)!!
    }

    override fun gt(rhs: Long): LuaValue {
        return (if (v > rhs) TRUE else FALSE)!!
    }

    override fun gt_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaNumber) rhs.lt_b(v) else super.gt_b(rhs)
    }

    override fun gt_b(rhs: Long): Boolean {
        return v > rhs
    }

    override fun gt_b(rhs: Double): Boolean {
        return luaFloatLessThanInteger(rhs, v)
    }

    override fun gteq(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaNumber) (if (rhs.lteq_b(v)) TRUE else FALSE) else super.gteq(rhs))!!
    }

    override fun gteq(rhs: Double): LuaValue {
        return (if (luaFloatLessOrEqualInteger(rhs, v)) TRUE else FALSE)!!
    }

    override fun gteq(rhs: Long): LuaValue {
        return (if (v >= rhs) TRUE else FALSE)!!
    }

    override fun gteq_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaNumber) rhs.lteq_b(v) else super.gteq_b(rhs)
    }

    override fun gteq_b(rhs: Long): Boolean {
        return v >= rhs
    }

    override fun gteq_b(rhs: Double): Boolean {
        return luaFloatLessOrEqualInteger(rhs, v)
    }

    // string comparison
    override fun strcmp(rhs: LuaString?): Int {
        typerror("attempt to compare number with string")
        return 0
    }

    override fun checkint(): Int {
        return v.toInt()
    }

    override fun checklong(): Long {
        return v.toLong()
    }

    override fun checkdouble(): Double {
        return v.toDouble()
    }

    override fun checkjstring(): String {
        return (v).toString()
    }

    override fun checkstring(): LuaString? {
        return valueOf((v).toString())
    }

    companion object {
        private const val CACHE_LOW = -256L
        private const val CACHE_HIGH = 255L
        private val intValues = arrayOfNulls<LuaInteger>(512)

        init {
            for (i in 0..511) {
                net.blueva.luak.LuaInteger.Companion.intValues[i] = net.blueva.luak.LuaInteger((i - 256).toLong())
            }
        }

        fun valueOf(i: Int): LuaInteger? =
            net.blueva.luak.LuaInteger.Companion.valueOf(i.toLong())

        /** Return the LuaInteger that represents the value provided.
         *
         * Since Lua 5.3 the integer subtype is 64 bits wide, so every [Long] is
         * representable and none of them degrade to a float.
         *
         * @param l long value to represent.
         * @return LuaInteger representing l
         * @see LuaValue.valueOf
         */
        fun valueOf(l: Long): LuaInteger? {
            if (l in CACHE_LOW..CACHE_HIGH) {
                return net.blueva.luak.LuaInteger.Companion.intValues[(l - CACHE_LOW).toInt()]
            }
            return net.blueva.luak.LuaInteger(l)
        }

        /** Hash of an integer key, matching what [LuaInteger.hashCode] produces. */
        fun hashCode(x: Long): Int {
            return x.toInt()
        }

        fun hashCode(x: Int): Int {
            return x
        }
    }
}
