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
package net.blueva.luak.lib

import net.blueva.luak.LuaString
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs

/**
 * `string.pack`, `string.unpack`, and `string.packsize`, from Lua 5.3.
 *
 * These read and write the binary layouts a C program would produce, driven by
 * a format string: `"i4"` is a four-byte integer, `"<I8"` a little-endian
 * unsigned eight-byte one, `"s4"` a string behind a four-byte length, `"z"` a
 * zero-terminated one, and so on. The manual's section on them is the reference
 * for the notation.
 *
 * Lua takes the widths of `b`, `h`, `l`, `j` and friends from the C compiler it
 * was built with. There is no C compiler here, so they are fixed at the widths
 * of the platform the reference interpreter is normally built for: `short` two
 * bytes, `int` four, and `long`, `lua_Integer`, `size_t` and `lua_Number` eight.
 * Only `l`/`L` would differ elsewhere - they are four bytes on Windows - and a
 * chunk that cares should say `i8` rather than `l` in any case.
 */
internal object StringPack {

    /** Options a format string can ask for, once classified. */
    private enum class Kind {
        INT, UINT, FLOAT, NUMBER, DOUBLE, CHAR, STRING, ZSTR, PADDING, PADDALIGN, NOP
    }

    /** Widest integer `pack` will read or write, in bytes. */
    private const val MAX_INTEGER_SIZE = 16

    /** Width of a Lua integer, in bytes. */
    private const val LUA_INTEGER_SIZE = 8

    /** Strictest alignment a format may ask for, upstream's `LUAI_MAXALIGN`. */
    private const val MAX_ALIGNMENT = 8

    private const val PAD_BYTE: Byte = 0

    /** How a format string is being read: byte order and alignment so far. */
    private class Header {
        /** Little-endian by default, which is the native order everywhere Lua runs. */
        var little: Boolean = true
        var maxalign: Int = 1
    }

    /** One classified option, with the size and padding it asks for. */
    /** A classified option. [size] is a Long because `c` may ask for any count. */
    private class Option(val kind: Kind, val size: Long, val toalign: Int)

    /** A cursor over the format string, so options can consume their numerals. */
    private class Format(val text: LuaString) {
        var index: Int = 0
        fun atEnd(): Boolean = index >= text.length()
        fun peek(): Int = if (atEnd()) -1 else text.luaByte(index)
        fun next(): Int = text.luaByte(index++)
    }

    /** Bytes being assembled by `pack`. */
    private class Packed {
        var bytes: ByteArray = ByteArray(32)
        var length: Int = 0

        fun add(b: Byte) {
            if (length == bytes.size) bytes = bytes.copyOf(bytes.size * 2)
            bytes[length++] = b
        }

        fun add(value: LuaString) {
            for (i in 0..<value.length()) add(value.luaByte(i).toByte())
        }

        fun pad(count: Int) {
            for (i in 0..<count) add(PAD_BYTE)
        }

        fun result(): LuaString = LuaString.valueOf(bytes, 0, length)
    }

    fun pack(args: Varargs): Varargs {
        val format = Format(args.checkstring(1)!!)
        val header = Header()
        val out = Packed()
        var arg = 1
        var total = 0L
        while (!format.atEnd()) {
            val option: Option = details(header, total, format)
            // The running total is checked as it grows, so a format that could
            // never be built is refused before any of it is.
            if (option.size > Int.MAX_VALUE.toLong() - total - option.toalign) {
                args.argcheck(false, 1, "result too long")
            }
            total += option.toalign + option.size
            out.pad(option.toalign)
            arg++
            when (option.kind) {
                Kind.INT -> {
                    val width: Int = option.size.toInt()
                    val n: Long = args.checklong(arg)
                    if (width < LUA_INTEGER_SIZE) {
                        val limit: Long = 1L shl (width * 8 - 1)
                        args.argcheck(-limit <= n && n < limit, arg, "integer overflow")
                    }
                    packInteger(out, n, header.little, width, n < 0)
                }

                Kind.UINT -> {
                    val width: Int = option.size.toInt()
                    val n: Long = args.checklong(arg)
                    if (width < LUA_INTEGER_SIZE) {
                        val limit: Long = 1L shl (width * 8)
                        args.argcheck(n >= 0 && n < limit, arg, "unsigned overflow")
                    }
                    packInteger(out, n, header.little, width, false)
                }

                Kind.FLOAT -> packBits(
                    out,
                    (args.checkdouble(arg).toFloat().toRawBits().toLong() and 0xFFFFFFFFL),
                    header.little,
                    4,
                )

                Kind.NUMBER, Kind.DOUBLE ->
                    packBits(out, args.checkdouble(arg).toRawBits(), header.little, 8)

                Kind.CHAR -> {
                    val s: LuaString = args.checkstring(arg)!!
                    args.argcheck(s.length() <= option.size, arg, "string longer than given size")
                    // A size no buffer could hold is refused before anything is
                    // written, rather than after trying to pad to it.
                    args.argcheck(option.size <= Int.MAX_VALUE.toLong(), 1, "result too long")
                    out.add(s)
                    out.pad((option.size - s.length()).toInt())
                }

                Kind.STRING -> {
                    val s: LuaString = args.checkstring(arg)!!
                    val width: Int = option.size.toInt()
                    args.argcheck(
                        width >= LUA_INTEGER_SIZE ||
                            s.length().toLong() < (1L shl (width * 8)),
                        arg,
                        "string length does not fit in given size",
                    )
                    packInteger(out, s.length().toLong(), header.little, width, false)
                    out.add(s)
                    total += s.length()
                }

                Kind.ZSTR -> {
                    val s: LuaString = args.checkstring(arg)!!
                    args.argcheck(s.indexOf(0.toByte(), 0) < 0, arg, "string contains zeros")
                    out.add(s)
                    out.add(0)
                    total += s.length() + 1
                }

                Kind.PADDING -> {
                    out.add(PAD_BYTE)
                    arg--
                }

                Kind.PADDALIGN, Kind.NOP -> arg--
            }
        }
        return out.result()
    }

    fun packsize(args: Varargs): Varargs {
        val format = Format(args.checkstring(1)!!)
        val header = Header()
        var total = 0L
        while (!format.atEnd()) {
            val option: Option = details(header, total, format)
            args.argcheck(
                option.kind != Kind.STRING && option.kind != Kind.ZSTR,
                1,
                "variable-length format",
            )
            // The total is checked as it grows: two sizes that each fit can
            // still add up to something no size can name.
            if (option.size > Long.MAX_VALUE - total - option.toalign) {
                args.argcheck(false, 1, "format result too large")
            }
            total += option.toalign + option.size
        }
        return LuaValue.valueOf(total)
    }

    fun unpack(args: Varargs): Varargs {
        val format = Format(args.checkstring(1)!!)
        val data: LuaString = args.checkstring(2)!!
        val length: Int = data.length()
        var position: Int = positionOf(args.optlong(3, 1L), length)
        args.argcheck(position <= length, 3, "initial position out of string")
        val header = Header()
        val results: ArrayList<LuaValue> = ArrayList()
        while (!format.atEnd()) {
            val option: Option = details(header, position.toLong(), format)
            args.argcheck(
                option.toalign.toLong() + option.size <= (length - position).toLong(),
                2,
                "data string too short",
            )
            position += option.toalign
            val width: Int = if (option.size <= Int.MAX_VALUE.toLong()) option.size.toInt() else 0
            when (option.kind) {
                Kind.INT, Kind.UINT -> results.add(
                    LuaValue.valueOf(
                        unpackInteger(data, position, header.little, width, option.kind == Kind.INT),
                    ),
                )

                Kind.FLOAT -> results.add(
                    LuaValue.valueOf(
                        Float.fromBits(unpackBits(data, position, header.little, 4).toInt()).toDouble(),
                    ),
                )

                Kind.NUMBER, Kind.DOUBLE -> results.add(
                    LuaValue.valueOf(Double.fromBits(unpackBits(data, position, header.little, 8))),
                )

                Kind.CHAR -> results.add(data.substring(position, position + width))

                Kind.STRING -> {
                    val size: Long = unpackInteger(data, position, header.little, width, false)
                    args.argcheck(
                        size >= 0 && size <= (length - position - width).toLong(),
                        2,
                        "data string too short",
                    )
                    val start: Int = position + width
                    results.add(data.substring(start, start + size.toInt()))
                    position += size.toInt()
                }

                Kind.ZSTR -> {
                    val end: Int = data.indexOf(0.toByte(), position)
                    args.argcheck(end >= 0, 2, "unfinished string for format 'z'")
                    results.add(data.substring(position, end))
                    position = end + 1
                }

                Kind.PADDALIGN, Kind.PADDING, Kind.NOP -> {}
            }
            position += width
        }
        results.add(LuaValue.valueOf((position + 1).toLong()))
        return LuaValue.varargsOf(results.toTypedArray())!!
    }

    /** Turns a possibly negative or zero index into a one-based offset. */
    private fun positionOf(position: Long, length: Int): Int {
        if (position > 0) return (position - 1).toInt()
        if (position == 0L) return 0
        return if (-position > length) 0 else (length + position).toInt()
    }

    /** Classifies the next option and works out the padding it needs. */
    private fun details(header: Header, total: Long, format: Format): Option {
        val classified: Pair<Kind, Long> = option(header, format)
        val kind: Kind = classified.first
        val size: Long = classified.second
        var align: Long = size
        if (kind == Kind.PADDALIGN) {
            // 'X' has no size of its own: it takes its alignment from whatever
            // option comes next, which is then discarded.
            if (format.atEnd()) LuaValue.Companion.argerror(1, "invalid next option for option 'X'")
            val following: Pair<Kind, Long> = option(header, format)
            align = following.second
            if (following.first == Kind.CHAR || align == 0L) {
                LuaValue.Companion.argerror(1, "invalid next option for option 'X'")
            }
        }
        if (align <= 1 || kind == Kind.CHAR) return Option(kind, size, 0)
        if (align > header.maxalign) align = header.maxalign.toLong()
        if (align and (align - 1) != 0L) {
            LuaValue.Companion.argerror(1, "format asks for alignment not power of 2")
        }
        val over: Long = total and (align - 1)
        return Option(kind, size, ((align - over) and (align - 1)).toInt())
    }

    /** Reads one option letter and whatever size numeral follows it. */
    private fun option(header: Header, format: Format): Pair<Kind, Long> {
        when (format.next()) {
            'b'.code -> return Pair(Kind.INT, 1L)
            'B'.code -> return Pair(Kind.UINT, 1L)
            'h'.code -> return Pair(Kind.INT, 2L)
            'H'.code -> return Pair(Kind.UINT, 2L)
            'l'.code, 'j'.code -> return Pair(Kind.INT, 8L)
            'L'.code, 'J'.code, 'T'.code -> return Pair(Kind.UINT, 8L)
            'f'.code -> return Pair(Kind.FLOAT, 4L)
            'n'.code -> return Pair(Kind.NUMBER, 8L)
            'd'.code -> return Pair(Kind.DOUBLE, 8L)
            'i'.code -> return Pair(Kind.INT, limitedNumeral(format, 4).toLong())
            'I'.code -> return Pair(Kind.UINT, limitedNumeral(format, 4).toLong())
            's'.code -> return Pair(Kind.STRING, limitedNumeral(format, 8).toLong())
            'c'.code -> {
                val size: Long = numeral(format, -1L)
                if (size < 0) LuaValue.Companion.error("missing size for format option 'c'")
                return Pair(Kind.CHAR, size)
            }

            'z'.code -> return Pair(Kind.ZSTR, 0L)
            'x'.code -> return Pair(Kind.PADDING, 1L)
            'X'.code -> return Pair(Kind.PADDALIGN, 0L)
            ' '.code -> return Pair(Kind.NOP, 0L)
            '<'.code -> {
                header.little = true
                return Pair(Kind.NOP, 0L)
            }

            '>'.code -> {
                header.little = false
                return Pair(Kind.NOP, 0L)
            }

            '='.code -> {
                header.little = true
                return Pair(Kind.NOP, 0L)
            }

            '!'.code -> {
                header.maxalign = limitedNumeral(format, MAX_ALIGNMENT)
                return Pair(Kind.NOP, 0L)
            }

            else -> {
                val letter: Char = format.text.luaByte(format.index - 1).toChar()
                LuaValue.Companion.error("invalid format option '" + letter + "'")
                return Pair(Kind.NOP, 0L)
            }
        }
    }

    /** A decimal numeral in the format string, or [default] if there is none. */
    private fun numeral(format: Format, default: Long): Long {
        if (format.peek() < '0'.code || format.peek() > '9'.code) return default
        var value = 0L
        while (!format.atEnd() && format.peek() >= '0'.code && format.peek() <= '9'.code) {
            val digit: Int = format.next() - '0'.code
            value = value * 10 + digit
            // Stops once another digit could not fit, leaving the rest of the
            // numeral in the format - where it is read as an option and
            // reported as the invalid one it is.
            if (value > (Long.MAX_VALUE - 9) / 10) break
        }
        return value
    }

    /** A numeral that names an integer width, which has a hard upper bound. */
    private fun limitedNumeral(format: Format, default: Int): Int {
        val size: Long = numeral(format, default.toLong())
        if (size < 1 || size > MAX_INTEGER_SIZE) {
            LuaValue.Companion.error(
                "integral size (" + size + ") out of limits [1," + MAX_INTEGER_SIZE + "]",
            )
        }
        return size.toInt()
    }

    /** Writes [n] over [size] bytes, sign-extending past a Lua integer. */
    private fun packInteger(out: Packed, n: Long, little: Boolean, size: Int, negative: Boolean) {
        val bytes = ByteArray(size)
        var value = n
        for (i in 0..<size) {
            val b: Byte = (value and 0xFF).toByte()
            bytes[if (little) i else size - 1 - i] = b
            value = value ushr 8
        }
        if (negative && size > LUA_INTEGER_SIZE) {
            for (i in LUA_INTEGER_SIZE..<size) bytes[if (little) i else size - 1 - i] = 0xFF.toByte()
        }
        for (b in bytes) out.add(b)
    }

    /** Writes the low [size] bytes of a bit pattern, honouring byte order. */
    private fun packBits(out: Packed, bits: Long, little: Boolean, size: Int) {
        val bytes = ByteArray(size)
        var value = bits
        for (i in 0..<size) {
            bytes[if (little) i else size - 1 - i] = (value and 0xFF).toByte()
            value = value ushr 8
        }
        for (b in bytes) out.add(b)
    }

    /** Reads [size] bytes as a bit pattern, honouring byte order. */
    private fun unpackBits(data: LuaString, at: Int, little: Boolean, size: Int): Long {
        var value = 0L
        for (i in size - 1 downTo 0) {
            value = (value shl 8) or data.luaByte(at + (if (little) i else size - 1 - i)).toLong()
        }
        return value
    }

    /**
     * Reads [size] bytes as an integer.
     *
     * A narrow signed field is sign-extended; a field wider than a Lua integer
     * has to have nothing but the sign in its extra bytes, or it does not fit.
     */
    private fun unpackInteger(
        data: LuaString,
        at: Int,
        little: Boolean,
        size: Int,
        signed: Boolean,
    ): Long {
        val limit: Int = if (size <= LUA_INTEGER_SIZE) size else LUA_INTEGER_SIZE
        var value = 0L
        for (i in limit - 1 downTo 0) {
            value = (value shl 8) or data.luaByte(at + (if (little) i else size - 1 - i)).toLong()
        }
        if (size < LUA_INTEGER_SIZE) {
            if (signed) {
                val mask: Long = 1L shl (size * 8 - 1)
                value = (value xor mask) - mask
            }
        } else if (size > LUA_INTEGER_SIZE) {
            val fill: Int = if (!signed || value >= 0) 0 else 0xFF
            for (i in limit..<size) {
                if (data.luaByte(at + (if (little) i else size - 1 - i)) != fill) {
                    LuaValue.Companion.error(size.toString() + "-byte integer does not fit into Lua Integer")
                }
            }
        }
        return value
    }
}
