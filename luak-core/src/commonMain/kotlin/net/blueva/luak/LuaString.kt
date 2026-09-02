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
import net.blueva.luak.io.ByteArrayInputStream
import net.blueva.luak.io.DataOutputStream
import net.blueva.luak.io.IOException
import net.blueva.luak.io.InputStream
import net.blueva.luak.io.PrintStream

/**
 * Subclass of [LuaValue] for representing lua strings.
 * 
 * 
 * Because lua string values are more nearly sequences of bytes than
 * sequences of characters or unicode code points, the [LuaString]
 * implementation holds the string value in an internal byte array.
 * 
 * 
 * [LuaString] values are not considered mutable once constructed,
 * so multiple [LuaString] values can chare a single byte array.
 * 
 * 
 * Currently [LuaString]s are pooled via a centrally managed weak table.
 * To ensure that as many string values as possible take advantage of this,
 * Constructors are not exposed directly.  As with number, booleans, and nil,
 * instance construction should be via [LuaValue.valueOf] or similar API.
 * 
 * 
 * Because of this pooling, users of LuaString *must not directly alter the
 * bytes in a LuaString*, or undefined behavior will result.
 * 
 * 
 * When Java Strings are used to initialize [LuaString] data, the UTF8 encoding is assumed.
 * The functions
 * [.lengthAsUtf8],
 * [.encodeToUtf8], and
 * [.decodeAsUtf8]
 * are used to convert back and forth between UTF8 byte arrays and character arrays.
 * 
 * @see LuaValue
 * 
 * @see LuaValue.valueOf
 * @see LuaValue.valueOf
 */
class LuaString private constructor(
    /** The bytes for the string.  These ***must not be mutated directly*** because
     * the backing may be shared by multiple LuaStrings, and the hash code is
     * computed only at construction time.
     * It is exposed only for performance and legacy reasons.  */
    val m_bytes: ByteArray,
    /** The offset into the byte array, 0 means start at the first byte  */
    val m_offset: Int,
    /** The number of bytes that comprise this string  */
    val m_length: Int
) : LuaValue() {
    /** The hashcode for this string.  Computed at construct time.  */
    private val m_hashcode: Int

    /** Simple cache of recently created strings that are short.
     * This is simply a list of strings, indexed by their hash codes modulo the cache size
     * that have been recently constructed.  If a string is being constructed frequently
     * from different contexts, it will generally show up as a cache hit and resolve
     * to the same value.   */
    private object RecentShortStrings {
        internal val recent_short_strings: Array<LuaString?>? =
            arrayOfNulls<LuaString>(net.blueva.luak.LuaString.Companion.RECENT_STRINGS_CACHE_SIZE)
    }

    /** Construct a [LuaString] around a byte array without copying the contents.
     * 
     * 
     * The array is used directly after this is called, so clients must not change contents.
     * 
     * 
     * @param bytes byte buffer
     * @param offset offset into the byte buffer
     * @param length length of the byte buffer
     * @return [LuaString] wrapping the byte buffer
     */
    init {
        this.m_hashcode = net.blueva.luak.LuaString.Companion.hashCode(m_bytes, m_offset, m_length)
        Memory.current.account(Memory.STRING + m_length)
    }

    override fun isstring(): Boolean {
        return true
    }

    override fun getmetatable(): LuaValue? {
        return net.blueva.luak.LuaString.Companion.s_metatable
    }

    override fun type(): Int {
        return LuaValue.TSTRING
    }

    override fun typename(): String? {
        return "string"
    }

    override fun tojstring(): String {
        return net.blueva.luak.LuaString.Companion.decodeAsUtf8(m_bytes, m_offset, m_length)
    }

    /**
     * The numeral this string denotes, for use in arithmetic.
     *
     * A string operand is converted to a number and the operation is then
     * redone on that number, rather than on a double standing in for it. That
     * keeps the subtype: `"10" + 5` is the integer `15`, while `"10.0" + 5` is
     * the float `15.0`.
     *
     * A string that is not a numeral answers `nil`, and the caller hands the
     * operation to the metatable, where `StringLib` has registered handlers
     * that report the failure the way Lua does and give the other operand's
     * own metamethod a turn.
     */
    private fun arithNumeral(): LuaValue = tonumber()

    // unary operators
    override fun neg(): LuaValue {
        val numeral: LuaValue = tonumber()
        return if (numeral.isnil()) super.neg() else numeral.neg()
    }

    // basic binary arithmetic
    override fun add(rhs: LuaValue): LuaValue {
        // Both operands have to be numerals for the shortcut. If the other one
        // is not, the metatable handler takes over: it is the one that knows
        // how to name both types in the error and how to offer the other
        // operand its own metamethod.
        val numeral: LuaValue = tonumber()
        return if (numeral.isnil() || rhs.tonumber().isnil()) arithmt(ADD, rhs) else numeral.add(rhs)
    }

    override fun add(rhs: Double): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmtwith(ADD, (rhs).toDouble()) else numeral.add(rhs)
    }

    override fun add(rhs: Long): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmtwith(ADD, (rhs).toDouble()) else numeral.add(rhs)
    }

    override fun sub(rhs: LuaValue): LuaValue {
        // Both operands have to be numerals for the shortcut. If the other one
        // is not, the metatable handler takes over: it is the one that knows
        // how to name both types in the error and how to offer the other
        // operand its own metamethod.
        val numeral: LuaValue = tonumber()
        return if (numeral.isnil() || rhs.tonumber().isnil()) arithmt(SUB, rhs) else numeral.sub(rhs)
    }

    override fun sub(rhs: Double): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmt(SUB, valueOf(rhs)) else numeral.sub(rhs)!!
    }

    override fun sub(rhs: Long): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmt(SUB, valueOf(rhs)) else numeral.sub(rhs)!!
    }

    override fun subFrom(lhs: Double): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmtwith(SUB, (lhs).toDouble()) else valueOf(lhs).sub(numeral)
    }

    override fun subFrom(lhs: Long): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmtwith(SUB, (lhs).toDouble()) else valueOf(lhs).sub(numeral)
    }

    override fun mul(rhs: LuaValue): LuaValue {
        // Both operands have to be numerals for the shortcut. If the other one
        // is not, the metatable handler takes over: it is the one that knows
        // how to name both types in the error and how to offer the other
        // operand its own metamethod.
        val numeral: LuaValue = tonumber()
        return if (numeral.isnil() || rhs.tonumber().isnil()) arithmt(MUL, rhs) else numeral.mul(rhs)
    }

    override fun mul(rhs: Double): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmtwith(MUL, (rhs).toDouble()) else numeral.mul(rhs)
    }

    override fun mul(rhs: Long): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmtwith(MUL, (rhs).toDouble()) else numeral.mul(rhs)
    }

    override fun pow(rhs: LuaValue): LuaValue {
        // Both operands have to be numerals for the shortcut. If the other one
        // is not, the metatable handler takes over: it is the one that knows
        // how to name both types in the error and how to offer the other
        // operand its own metamethod.
        val numeral: LuaValue = tonumber()
        return if (numeral.isnil() || rhs.tonumber().isnil()) arithmt(POW, rhs) else numeral.pow(rhs)
    }

    override fun pow(rhs: Double): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmt(POW, valueOf(rhs)) else numeral.pow(rhs)!!
    }

    override fun pow(rhs: Long): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmt(POW, valueOf(rhs)) else numeral.pow(rhs)!!
    }

    override fun powWith(lhs: Double): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmtwith(POW, (lhs).toDouble()) else valueOf(lhs).pow(numeral)
    }

    override fun powWith(lhs: Long): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmtwith(POW, (lhs).toDouble()) else valueOf(lhs).pow(numeral)
    }

    override fun div(rhs: LuaValue): LuaValue {
        // Both operands have to be numerals for the shortcut. If the other one
        // is not, the metatable handler takes over: it is the one that knows
        // how to name both types in the error and how to offer the other
        // operand its own metamethod.
        val numeral: LuaValue = tonumber()
        return if (numeral.isnil() || rhs.tonumber().isnil()) arithmt(DIV, rhs) else numeral.div(rhs)
    }

    override fun div(rhs: Double): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmt(DIV, valueOf(rhs)) else numeral.div(rhs)!!
    }

    override fun div(rhs: Long): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmt(DIV, valueOf(rhs)) else numeral.div(rhs)!!
    }

    override fun divInto(lhs: Double): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmtwith(DIV, (lhs).toDouble()) else valueOf(lhs).div(numeral)
    }

    // A string is coerced for arithmetic but never for a bitwise operation:
    // upstream reads bitwise operands straight off the stack as integers, so a
    // string there is an error rather than something to convert.
    override fun idiv(rhs: LuaValue): LuaValue {
        // Both operands have to be numerals for the shortcut. If the other one
        // is not, the metatable handler takes over: it is the one that knows
        // how to name both types in the error and how to offer the other
        // operand its own metamethod.
        val numeral: LuaValue = tonumber()
        return if (numeral.isnil() || rhs.tonumber().isnil()) arithmt(IDIV, rhs) else numeral.idiv(rhs)
    }

    override fun mod(rhs: LuaValue): LuaValue {
        // Both operands have to be numerals for the shortcut. If the other one
        // is not, the metatable handler takes over: it is the one that knows
        // how to name both types in the error and how to offer the other
        // operand its own metamethod.
        val numeral: LuaValue = tonumber()
        return if (numeral.isnil() || rhs.tonumber().isnil()) arithmt(MOD, rhs) else numeral.mod(rhs)
    }

    override fun mod(rhs: Double): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmt(MOD, valueOf(rhs)) else numeral.mod(rhs)!!
    }

    override fun mod(rhs: Long): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmt(MOD, valueOf(rhs)) else numeral.mod(rhs)!!
    }

    override fun modFrom(lhs: Double): LuaValue {
        val numeral: LuaValue = arithNumeral()
        return if (numeral.isnil()) arithmtwith(MOD, (lhs).toDouble()) else valueOf(lhs).mod(numeral)
    }

    // Relational operators only work between two strings: a number is a
    // string as far as 'isstring' is concerned, but ordering one against a
    // string is an error rather than a coercion.
    override fun lt(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaString) (if (rhs.strcmp(this) > 0) LuaValue.TRUE else FALSE) else super.lt(rhs))!!
    }

    override fun lt_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaString) rhs.strcmp(this) > 0 else super.lt_b(rhs)
    }

    override fun lt_b(rhs: Long): Boolean {
        LuaValue.error("attempt to compare string with number")
        return false
    }

    override fun lt_b(rhs: Double): Boolean {
        LuaValue.error("attempt to compare string with number")
        return false
    }

    override fun lteq(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaString) (if (rhs.strcmp(this) >= 0) LuaValue.TRUE else FALSE) else super.lteq(rhs))!!
    }

    override fun lteq_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaString) rhs.strcmp(this) >= 0 else super.lteq_b(rhs)
    }

    override fun lteq_b(rhs: Long): Boolean {
        LuaValue.error("attempt to compare string with number")
        return false
    }

    override fun lteq_b(rhs: Double): Boolean {
        LuaValue.error("attempt to compare string with number")
        return false
    }

    override fun gt(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaString) (if (rhs.strcmp(this) < 0) LuaValue.TRUE else FALSE) else super.gt(rhs))!!
    }

    override fun gt_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaString) rhs.strcmp(this) < 0 else super.gt_b(rhs)
    }

    override fun gt_b(rhs: Long): Boolean {
        // The compiler turns 'a > b' into 'b < a', so the number is the one
        // named first.
        LuaValue.error("attempt to compare number with string")
        return false
    }

    override fun gt_b(rhs: Double): Boolean {
        // The compiler turns 'a > b' into 'b < a', so the number is the one
        // named first.
        LuaValue.error("attempt to compare number with string")
        return false
    }

    override fun gteq(rhs: LuaValue): LuaValue {
        return (if (rhs is LuaString) (if (rhs.strcmp(this) <= 0) LuaValue.TRUE else FALSE) else super.gteq(rhs))!!
    }

    override fun gteq_b(rhs: LuaValue): Boolean {
        return if (rhs is LuaString) rhs.strcmp(this) <= 0 else super.gteq_b(rhs)
    }

    override fun gteq_b(rhs: Long): Boolean {
        // The compiler turns 'a > b' into 'b < a', so the number is the one
        // named first.
        LuaValue.error("attempt to compare number with string")
        return false
    }

    override fun gteq_b(rhs: Double): Boolean {
        // The compiler turns 'a > b' into 'b < a', so the number is the one
        // named first.
        LuaValue.error("attempt to compare number with string")
        return false
    }

    // concatenation
    override fun concat(rhs: LuaValue): LuaValue {
        return rhs.concatTo(this)
    }

    override fun concat(rhs: Buffer): Buffer {
        return rhs.concatTo(this)
    }

    override fun concatTo(lhs: LuaNumber): LuaValue {
        return concatTo((lhs.strvalue())!!)
    }

    override fun concatTo(lhs: LuaString): LuaValue {
        val b = ByteArray(lhs.m_length + this.m_length)
        arrayCopy(lhs.m_bytes, lhs.m_offset, b, 0, lhs.m_length)
        arrayCopy(this.m_bytes, this.m_offset, b, lhs.m_length, this.m_length)
        return net.blueva.luak.LuaString.Companion.valueUsing(b, 0, b.size)
    }

    // string comparison
    override fun strcmp(lhs: LuaValue?): Int {
        val lhs = lhs!!
        return -lhs.strcmp(this)
    }

    override fun strcmp(rhs: LuaString?): Int {
        val rhs = rhs!!
        var i = 0
        var j = 0
        while (i < m_length && j < rhs.m_length) {
            if (m_bytes[m_offset + i] != rhs.m_bytes[rhs.m_offset + j]) {
                return (m_bytes[m_offset + i].toInt()) - (rhs.m_bytes[rhs.m_offset + j].toInt())
            }
            ++i
            ++j
        }
        return m_length - rhs.m_length
    }

    /** Check for number in arithmetic, or throw aritherror  */
    private fun checkarith(): Double {
        val d = scannumber()
        if ((d).isNaN()) aritherror()
        return d
    }

    /** The numeral this string denotes, or an argument error if it is none. */
    private fun checknumeral(message: String?): LuaValue {
        val numeral: LuaValue = tonumber()
        if (numeral.isnil()) {
            if (message == null) argerror("number") else error(message)
        }
        return numeral
    }

    override fun checkint(): Int {
        return checklong().toInt()
    }

    override fun checkinteger(): LuaInteger? {
        return net.blueva.luak.LuaInteger.valueOf(checklong())
    }

    override fun checklong(): Long {
        // Through the numeral rather than through a double, so a 64-bit
        // integer written out in full does not lose its low bits on the way,
        // and a fractional one is rejected rather than truncated.
        return checknumeral(null).checklong()
    }

    override fun checkdouble(): Double {
        val d = scannumber()
        if ((d).isNaN()) argerror("number")
        return d
    }

    override fun checknumber(): LuaNumber? {
        return checknumeral(null) as LuaNumber
    }

    override fun checknumber(msg: String?): LuaNumber? {
        return checknumeral(msg) as LuaNumber
    }

    override fun isnumber(): Boolean {
        val d = scannumber()
        return !(d).isNaN()
    }

    override fun isint(): Boolean {
        val numeral: LuaValue = tonumber()
        if (numeral.isnil()) return false
        val d: Double = numeral.todouble()
        return d.toInt().toDouble() == d
    }

    override fun islong(): Boolean {
        val numeral: LuaValue = tonumber()
        if (numeral.isnil()) return false
        if (numeral.isinttype()) return true
        val d: Double = numeral.todouble()
        return d.toLong().toDouble() == d
    }

    override fun tobyte(): Byte {
        return toint().toByte()
    }

    override fun tochar(): Char {
        return toint().toChar()
    }

    override fun todouble(): Double {
        val d = scannumber()
        return if ((d).isNaN()) 0.0 else d
    }

    override fun tofloat(): Float {
        return todouble().toFloat()
    }

    override fun toint(): Int {
        return tolong().toInt()
    }

    override fun tolong(): Long {
        return (tonumber().takeUnless { it.isnil() } ?: return 0L).tolong()
    }

    override fun toshort(): Short {
        return toint().toShort()
    }

    override fun optdouble(defval: Double): Double {
        return checkdouble()
    }

    override fun optint(defval: Int): Int {
        return checkint()
    }

    override fun optinteger(defval: LuaInteger?): LuaInteger? {
        return checkinteger()
    }

    override fun optlong(defval: Long): Long {
        return checklong()
    }

    override fun optnumber(defval: LuaNumber?): LuaNumber? {
        return checknumber()
    }

    override fun optstring(defval: LuaString?): LuaString {
        return this
    }

    override fun tostring(): LuaValue {
        return this
    }

    override fun optjstring(defval: String?): String {
        return tojstring()
    }

    override fun strvalue(): LuaString {
        return this
    }

    /** Take a substring using Java zero-based indexes for begin and end or range.
     * @param beginIndex  The zero-based index of the first character to include.
     * @param endIndex  The zero-based index of position after the last character.
     * @return LuaString which is a substring whose first character is at offset
     * beginIndex and extending for (endIndex - beginIndex ) characters.
     */
    fun substring(beginIndex: Int, endIndex: Int): LuaString {
        val off = m_offset + beginIndex
        val len = endIndex - beginIndex
        return if (len >= m_length / 2) net.blueva.luak.LuaString.Companion.valueUsing(
            m_bytes,
            off,
            len
        ) else net.blueva.luak.LuaString.Companion.valueOf(m_bytes, off, len)
    }

    override fun hashCode(): Int {
        return m_hashcode
    }

    // object comparison, used in key comparison
    override fun equals(o: Any?): Boolean {
        if (o is LuaString) {
            return raweq(o as LuaString?)
        }
        return false
    }

    // equality w/ metatable processing
    override fun eq(`val`: LuaValue?): LuaValue {
        val `val` = `val`!!
        return (if (`val`.raweq(this)) TRUE else FALSE)!!
    }

    override fun eq_b(`val`: LuaValue?): Boolean {
        val `val` = `val`!!
        return `val`.raweq(this)
    }

    // equality w/o metatable processing
    override fun raweq(`val`: LuaValue?): Boolean {
        val `val` = `val`!!
        return `val`.raweq(this)
    }

    override fun raweq(s: LuaString?): Boolean {
        val s = s!!
        if (this === s) return true
        if (s.m_length != m_length) return false
        if (s.m_bytes == m_bytes && s.m_offset == m_offset) return true
        if (s.hashCode() != hashCode()) return false
        for (i in 0..<m_length) if (s.m_bytes[s.m_offset + i] != m_bytes[m_offset + i]) return false
        return true
    }

    /** Return true if the bytes in the supplied range match this LuaStrings bytes.  */
    private fun byteseq(bytes: ByteArray, off: Int, len: Int): Boolean {
        return (m_length == len && net.blueva.luak.LuaString.Companion.equals(m_bytes, m_offset, bytes, off, len))
    }

    @kotlin.Throws(IOException::class)
    fun write(writer: DataOutputStream, i: Int, len: Int) {
        writer.write(m_bytes, m_offset + i, len)
    }

    override fun len(): LuaValue {
        return (LuaInteger.valueOf(m_length))!!
    }

    override fun length(): Int {
        return m_length
    }

    override fun rawlen(): Int {
        return m_length
    }

    fun luaByte(index: Int): Int {
        return m_bytes[m_offset + index].toInt() and 0x0FF
    }

    fun charAt(index: Int): Int {
        if (index < 0 || index >= m_length) throw IndexOutOfBoundsException()
        return luaByte(index)
    }

    override fun checkjstring(): String {
        return tojstring()
    }

    override fun checkstring(): LuaString {
        return this
    }

    /** Convert value to an input stream.
     * 
     * @return [InputStream] whose data matches the bytes in this [LuaString]
     */
    fun toInputStream(): InputStream? {
        return ByteArrayInputStream(m_bytes, m_offset, m_length)
    }

    /**
     * Copy the bytes of the string into the given byte array.
     * @param strOffset offset from which to copy
     * @param bytes destination byte array
     * @param arrayOffset offset in destination
     * @param len number of bytes to copy
     */
    fun copyInto(strOffset: Int, bytes: ByteArray?, arrayOffset: Int, len: Int) {
        arrayCopy(m_bytes, m_offset + strOffset, bytes, arrayOffset, len)
    }

    /** Java version of strpbrk - find index of any byte that in an accept string.
     * @param accept [LuaString] containing characters to look for.
     * @return index of first match in the `accept` string, or -1 if not found.
     */
    fun indexOfAny(accept: LuaString): Int {
        val ilimit = m_offset + m_length
        val jlimit = accept.m_offset + accept.m_length
        for (i in m_offset..<ilimit) {
            for (j in accept.m_offset..<jlimit) {
                if (m_bytes[i] == accept.m_bytes[j]) {
                    return i - m_offset
                }
            }
        }
        return -1
    }

    /**
     * Find the index of a byte starting at a point in this string
     * @param b the byte to look for
     * @param start the first index in the string
     * @return index of first match found, or -1 if not found.
     */
    fun indexOf(b: Byte, start: Int): Int {
        for (i in start..<m_length) {
            if (m_bytes[m_offset + i] == b) return i
        }
        return -1
    }

    /**
     * Find the index of a string starting at a point in this string
     * @param s the string to search for
     * @param start the first index in the string
     * @return index of first match found, or -1 if not found.
     */
    fun indexOf(s: LuaString, start: Int): Int {
        val slen = s.length()
        val limit = m_length - slen
        for (i in start..limit) {
            if (net.blueva.luak.LuaString.Companion.equals(m_bytes, m_offset + i, s.m_bytes, s.m_offset, slen)) return i
        }
        return -1
    }

    /**
     * Find the last index of a string in this string
     * @param s the string to search for
     * @return index of last match found, or -1 if not found.
     */
    fun lastIndexOf(s: LuaString): Int {
        val slen = s.length()
        val limit = m_length - slen
        for (i in limit downTo 0) {
            if (net.blueva.luak.LuaString.Companion.equals(m_bytes, m_offset + i, s.m_bytes, s.m_offset, slen)) return i
        }
        return -1
    }


    val isValidUtf8: Boolean
        /** Check that a byte sequence is valid UTF-8
         * @return true if it is valid UTF-8, otherwise false
         * @see .lengthAsUtf8
         * @see .encodeToUtf8
         * @see .decodeAsUtf8
         */
        get() {
            var i = m_offset
            val j = m_offset + m_length
            while (i < j) {
                val c = m_bytes[i++].toInt()
                if (c >= 0) {
                    continue
                }
                if (((c and 0xE0) == 0xC0)
                    && i < j && (m_bytes[i++].toInt() and 0xC0) == 0x80
                ) {
                    continue
                }
                if (((c and 0xF0) == 0xE0)
                    && i + 1 < j && (m_bytes[i++].toInt() and 0xC0) == 0x80 && (m_bytes[i++].toInt() and 0xC0) == 0x80
                ) {
                    continue
                }
                return false
            }
            return true
        }

    // --------------------- number conversion -----------------------
    /**
     * convert to a number using baee 10 or base 16 if it starts with '0x',
     * or NIL if it can't be converted
     * @return IntValue, DoubleValue, or NIL depending on the content of the string.
     * @see LuaValue.tonumber
     */
    override fun tonumber(): LuaValue {
        return scannumeral() ?: NIL
    }

    /**
     * convert to a number using a supplied base, or NIL if it can't be converted
     * @param base the base to use, such as 10
     * @return IntValue, DoubleValue, or NIL depending on the content of the string.
     * @see LuaValue.tonumber
     */
    fun tonumber(base: Int): LuaValue? {
        val value: Long = net.blueva.luak.NumberParser.parseInteger(tojstring(), base) ?: return NIL
        return valueOf(value)
    }

    /**
     * Convert to a number in base 10, or base 16 if the string starts with '0x',
     * or return Double.NaN if it cannot be converted to a number.
     * @return double value if conversion is valid, or Double.NaN if not
     */
    fun scannumber(): Double {
        val numeral: LuaValue = scannumeral() ?: return Double.NaN
        return numeral.todouble()
    }

    /**
     * The numeral this string denotes, keeping its subtype, or `null`.
     *
     * @return a [LuaInteger] or a [LuaDouble], or `null` if this is not a numeral
     */
    private fun scannumeral(): LuaValue? {
        // Rule out the common non-numeric string before decoding it: a numeral
        // can only start with a digit, a sign, or a decimal point.
        var i = m_offset
        val end = m_offset + m_length
        while (i < end && isSpaceByte(m_bytes[i])) ++i
        if (i >= end) return null
        val first = m_bytes[i].toInt()
        val plausible = (first >= '0'.code && first <= '9'.code) ||
            first == '-'.code || first == '+'.code || first == '.'.code
        if (!plausible) return null
        return net.blueva.luak.NumberParser.parse(tojstring())
    }

    private fun isSpaceByte(b: Byte): Boolean {
        val c = b.toInt()
        return c == ' '.code || c == 0x09 || c == 0x0A || c == 0x0B || c == 0x0C || c == 0x0D
    }

    /**
     * Convert to a number in a base, or return Double.NaN if not a number.
     * @param base the base to use between 2 and 36
     * @return double value if conversion is valid, or Double.NaN if not
     */
    fun scannumber(base: Int): Double {
        val value: Long = net.blueva.luak.NumberParser.parseInteger(tojstring(), base)
            ?: return Double.NaN
        return value.toDouble()
    }



    /**
     * Print the bytes of the LuaString to a PrintStream as if it were
     * an ASCII string, quoting and escaping control characters.
     * @param ps PrintStream to print to.
     */
    fun printToStream(ps: PrintStream) {
        var i = 0
        val n = m_length
        while (i < n) {
            val c = m_bytes[m_offset + i].toInt()
            ps.print(c.toChar())
            i++
        }
    }

    companion object {
        /** The singleton instance for string metatables that forwards to the string functions.
         * Typically, this is set to the string metatable as a side effect of loading the string
         * library, and is read-write to provide flexible behavior by default.  When used in a
         * server environment where there may be roge scripts, this should be replaced with a
         * read-only table since it is shared across all lua code in this Java VM.
         */
        var s_metatable: LuaValue? = null

        /** Size of cache of recent short strings. This is the maximum number of LuaStrings that
         * will be retained in the cache of recent short strings.  Exposed to package for testing.  */
        const val RECENT_STRINGS_CACHE_SIZE: Int = 128

        /** Maximum length of a string to be considered for recent short strings caching.
         * This effectively limits the total memory that can be spent on the recent strings cache,
         * because no LuaString whose backing exceeds this length will be put into the cache.
         * Exposed to package for testing.  */
        const val RECENT_STRINGS_MAX_LENGTH: Int = 32

        /**
         * Get a [LuaString] instance whose bytes match
         * the supplied Java String using the UTF8 encoding.
         * @param string Java String containing characters to encode as UTF8
         * @return [LuaString] with UTF8 bytes corresponding to the supplied String
         */
        @kotlin.jvm.JvmStatic
        fun valueOf(string: String): LuaString {
            val c: CharArray = string.toCharArray()
            val b = ByteArray(net.blueva.luak.LuaString.Companion.lengthAsUtf8(c))
            net.blueva.luak.LuaString.Companion.encodeToUtf8(c, c.size, b, 0)
            return net.blueva.luak.LuaString.Companion.valueUsing(b, 0, b.size)
        }

        /** Construct a [LuaString] for a portion of a byte array.
         * 
         * 
         * The array is first be used as the backing for this object, so clients must not change contents.
         * If the supplied value for 'len' is more than half the length of the container, the
         * supplied byte array will be used as the backing, otherwise the bytes will be copied to a
         * new byte array, and cache lookup may be performed.
         * 
         * 
         * @param bytes byte buffer
         * @param off offset into the byte buffer
         * @param len length of the byte buffer
         * @return [LuaString] wrapping the byte buffer
         */
        /** Construct a [LuaString] for all the bytes in a byte array.
         * 
         * 
         * The LuaString returned will either be a new LuaString containing a copy
         * of the bytes array, or be an existing LuaString used already having the same value.
         * 
         * 
         * @param bytes byte buffer
         * @return [LuaString] wrapping the byte buffer
         */
        @kotlin.jvm.JvmOverloads
        @kotlin.jvm.JvmStatic
        fun valueOf(bytes: ByteArray, off: Int = 0, len: Int = bytes.size): LuaString {
            if (len > net.blueva.luak.LuaString.Companion.RECENT_STRINGS_MAX_LENGTH) return net.blueva.luak.LuaString.Companion.valueFromCopy(
                bytes,
                off,
                len
            )
            val hash: Int = net.blueva.luak.LuaString.Companion.hashCode(bytes, off, len)
            val bucket = hash and (net.blueva.luak.LuaString.Companion.RECENT_STRINGS_CACHE_SIZE - 1)
            val t: LuaString? = net.blueva.luak.LuaString.RecentShortStrings.recent_short_strings!![bucket]
            if (t != null && t.m_hashcode == hash && t.byteseq(bytes, off, len)) return t
            val s: LuaString = net.blueva.luak.LuaString.Companion.valueFromCopy(bytes, off, len)
            net.blueva.luak.LuaString.RecentShortStrings.recent_short_strings!![bucket] = s
            return s
        }

        /** Construct a new LuaString using a copy of the bytes array supplied  */
        private fun valueFromCopy(bytes: ByteArray?, off: Int, len: Int): LuaString {
            val copy = ByteArray(len)
            arrayCopy(bytes, off, copy, 0, len)
            return net.blueva.luak.LuaString(copy, 0, len)
        }

        /** Construct a [LuaString] around, possibly using the the supplied
         * byte array as the backing store.
         * 
         * 
         * The caller must ensure that the array is not mutated after the call.
         * However, if the string is short enough the short-string cache is checked
         * for a match which may be used instead of the supplied byte array.
         * 
         * 
         * @param bytes byte buffer
         * @return [LuaString] wrapping the byte buffer, or an equivalent string.
         */
        /** Construct a [LuaString] for all the bytes in a byte array, possibly using
         * the supplied array as the backing store.
         * 
         * 
         * The LuaString returned will either be a new LuaString containing the byte array,
         * or be an existing LuaString used already having the same value.
         * 
         * 
         * The caller must not mutate the contents of the byte array after this call, as
         * it may be used elsewhere due to recent short string caching.
         * @param bytes byte buffer
         * @return [LuaString] wrapping the byte buffer
         */
        @kotlin.jvm.JvmOverloads
        fun valueUsing(bytes: ByteArray, off: Int = 0, len: Int = bytes.size): LuaString {
            if (bytes.size > net.blueva.luak.LuaString.Companion.RECENT_STRINGS_MAX_LENGTH) return net.blueva.luak.LuaString(
                bytes,
                off,
                len
            )
            val hash: Int = net.blueva.luak.LuaString.Companion.hashCode(bytes, off, len)
            val bucket = hash and (net.blueva.luak.LuaString.Companion.RECENT_STRINGS_CACHE_SIZE - 1)
            val t: LuaString? = net.blueva.luak.LuaString.RecentShortStrings.recent_short_strings!![bucket]
            if (t != null && t.m_hashcode == hash && t.byteseq(bytes, off, len)) return t
            val s: LuaString = net.blueva.luak.LuaString(bytes, off, len)
            net.blueva.luak.LuaString.RecentShortStrings.recent_short_strings!![bucket] = s
            return s
        }

        /** Construct a [LuaString] using the supplied characters as byte values.
         * 
         * 
         * Only the low-order 8-bits of each character are used, the remainder is ignored.
         * 
         * 
         * This is most useful for constructing byte sequences that do not conform to UTF8.
         * @param bytes array of char, whose values are truncated at 8-bits each and put into a byte array.
         * @return [LuaString] wrapping a copy of the byte buffer
         */
        /** Construct a [LuaString] using the supplied characters as byte values.
         * 
         * 
         * Only the low-order 8-bits of each character are used, the remainder is ignored.
         * 
         * 
         * This is most useful for constructing byte sequences that do not conform to UTF8.
         * @param bytes array of char, whose values are truncated at 8-bits each and put into a byte array.
         * @return [LuaString] wrapping a copy of the byte buffer
         */
        @kotlin.jvm.JvmOverloads
        @kotlin.jvm.JvmStatic
        fun valueOf(bytes: CharArray, off: Int = 0, len: Int = bytes.size): LuaString {
            val b = ByteArray(len)
            for (i in 0..<len) b[i] = bytes[i + off].code.toByte()
            return net.blueva.luak.LuaString.Companion.valueUsing(b, 0, len)
        }

        /** Compute the hash code of a sequence of bytes within a byte array using
         * lua's rules for string hashes.  For long strings, not all bytes are hashed.
         * @param bytes  byte array containing the bytes.
         * @param offset  offset into the hash for the first byte.
         * @param length number of bytes starting with offset that are part of the string.
         * @return hash for the string defined by bytes, offset, and length.
         */
        fun hashCode(bytes: ByteArray, offset: Int, length: Int): Int {
            var h = length /* seed */
            val step = (length shr 5) + 1 /* if string is too long, don't hash all its chars */
            var l1 = length
            while (l1 >= step) {
                /* compute hash */
                h = h xor ((h shl 5) + (h shr 2) + ((bytes[offset + l1 - 1].toInt()) and 0x0FF))
                l1 -= step
            }
            return h
        }

        fun equals(a: LuaString, i: Int, b: LuaString, j: Int, n: Int): Boolean {
            return net.blueva.luak.LuaString.Companion.equals(a.m_bytes, a.m_offset + i, b.m_bytes, b.m_offset + j, n)
        }

        fun equals(a: ByteArray, i: Int, b: ByteArray, j: Int, n: Int): Boolean {
            var i = i
            var j = j
            var n = n
            if (a.size < i + n || b.size < j + n) return false
            while (--n >= 0) if (a[i++] != b[j++]) return false
            return true
        }

        /**
         * Convert to Java String interpreting as utf8 characters.
         * 
         * @param bytes byte array in UTF8 encoding to convert
         * @param offset starting index in byte array
         * @param length number of bytes to convert
         * @return Java String corresponding to the value of bytes interpreted using UTF8
         * @see .lengthAsUtf8
         * @see .encodeToUtf8
         * @see .isValidUtf8
         */
        fun decodeAsUtf8(bytes: ByteArray, offset: Int, length: Int): String {
            var i: Int
            var j: Int
            var n: Int
            var b: Int
            i = offset
            j = offset + length
            n = 0
            while (i < j) {
                when (0xE0 and bytes[i++].toInt()) {
                    0xE0 -> {
                        ++i
                        ++i
                    }

                    0xC0 -> ++i
                }
                ++n
            }
            val chars = CharArray(n)
            i = offset
            j = offset + length
            n = 0
            while (i < j) {
                chars[n++] = (if ((bytes[i++].also {
                        b = it.toInt()
                    }) >= 0 || i >= j) b else if (b < -32 || i + 1 >= j) (((b and 0x3f) shl 6) or (bytes[i++].toInt() and 0x3f)) else (((b and 0xf) shl 12) or ((bytes[i++].toInt() and 0x3f) shl 6) or (bytes[i++].toInt() and 0x3f))).toChar()
            }
            return chars.concatToString()
        }

        /**
         * Count the number of bytes required to encode the string as UTF-8.
         * @param chars Array of unicode characters to be encoded as UTF-8
         * @return count of bytes needed to encode using UTF-8
         * @see .encodeToUtf8
         * @see .decodeAsUtf8
         * @see .isValidUtf8
         */
        fun lengthAsUtf8(chars: CharArray): Int {
            var i: Int
            var b: Int
            var c: Char
            i = chars.size.also { b = it }
            while (--i >= 0) {
                if ((chars[i].also { c = it }).code >= 0x80) b += if (c.code >= 0x800) 2 else 1
            }
            return b
        }

        /**
         * Encode the given Java string as UTF-8 bytes, writing the result to bytes
         * starting at offset.
         * 
         * 
         * The string should be measured first with lengthAsUtf8
         * to make sure the given byte array is large enough.
         * @param chars Array of unicode characters to be encoded as UTF-8
         * @param nchars Number of characters in the array to convert.
         * @param bytes byte array to hold the result
         * @param off offset into the byte array to start writing
         * @return number of bytes converted.
         * @see .lengthAsUtf8
         * @see .decodeAsUtf8
         * @see .isValidUtf8
         */
        fun encodeToUtf8(chars: CharArray, nchars: Int, bytes: ByteArray, off: Int): Int {
            var c: Char
            var j = off
            for (i in 0..<nchars) {
                if ((chars[i].also { c = it }).code < 0x80) {
                    bytes[j++] = c.code.toByte()
                } else if (c.code < 0x800) {
                    bytes[j++] = (0xC0 or ((c.code shr 6) and 0x1f)).toByte()
                    bytes[j++] = (0x80 or (c.code and 0x3f)).toByte()
                } else {
                    bytes[j++] = (0xE0 or ((c.code shr 12) and 0x0f)).toByte()
                    bytes[j++] = (0x80 or ((c.code shr 6) and 0x3f)).toByte()
                    bytes[j++] = (0x80 or (c.code and 0x3f)).toByte()
                }
            }
            return j - off
        }
    }
}
