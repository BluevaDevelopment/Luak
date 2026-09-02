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

import net.blueva.luak.Globals
import net.blueva.luak.LuaString
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs

/**
 * Subclass of [LibFunction] which implements the lua standard `utf8` library,
 * added to the language in Lua 5.3.
 *
 * Lua strings are byte strings; this library interprets them as UTF-8 without
 * changing that. Positions are byte positions throughout, and every function
 * works on the raw bytes rather than on any host string type, so it behaves
 * identically on every Kotlin Multiplatform target.
 *
 * Decoding is strict by default: an ill-formed sequence, a surrogate, or a
 * value above `0x7FFFFFFF` is rejected. Passing a true `lax` argument accepts
 * the extended range the reference calls lax, matching upstream.
 *
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * globals.get("utf8").get("char").call(LuaValue.valueOf(0x4E2D))
 * ```
 *
 * @see LibFunction
 *
 * @see net.blueva.luak.lib.LuaPlatform
 *
 * @see [Lua 5.5 UTF-8 Lib Reference](http://www.lua.org/manual/5.5/manual.html#6.5)
 */
class Utf8Lib : TwoArgFunction() {
    private var globals: Globals? = null

    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        globals = env!!.checkglobals()
        val utf8: LuaTable = LuaTable()
        utf8.set("charpattern", net.blueva.luak.lib.Utf8Lib.CHAR_PATTERN)
        // Qualified: `len()` would otherwise resolve to LuaValue's own length
        // operator, which this class inherits.
        utf8.set("char", net.blueva.luak.lib.Utf8Lib.char())
        utf8.set("codepoint", net.blueva.luak.lib.Utf8Lib.codepoint())
        utf8.set("len", net.blueva.luak.lib.Utf8Lib.len())
        utf8.set("offset", net.blueva.luak.lib.Utf8Lib.offset())
        utf8.set("codes", net.blueva.luak.lib.Utf8Lib.codes())
        env.set("utf8", utf8)
        if (!env.get("package")!!.isnil()) env.get("package")!!.get("loaded")!!.set("utf8", utf8)
        return utf8
    }

    /** `utf8.char(...)`: each argument encoded, then concatenated. */
    internal class char : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val bytes = ArrayList<Byte>()
            for (i in 1..args.narg()) {
                encode(args.checklong(i), bytes, i)
            }
            return LuaString.valueUsing(bytes.toByteArray())
        }
    }

    /** `utf8.codepoint(s [, i [, j [, lax]]])`. */
    internal class codepoint : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val s: LuaString = args.checkstring(1)!!
            val length: Int = s.m_length
            val first: Int = position(args.optint(2, 1), length, 1)
            val last: Int = position(args.optint(3, first), length, 1)
            val lax: Boolean = args.optboolean(4, false)
            if (first < 1) LuaValue.argerror(2, "out of bounds")
            if (last > length) LuaValue.argerror(3, "out of bounds")

            val points = ArrayList<LuaValue>()
            var at = first
            while (at <= last) {
                val decoded: Long = decode(s, at, lax)
                    ?: LuaValue.error("invalid UTF-8 code").let { return NONE!! }
                points.add(LuaValue.valueOf(decoded))
                at += sequenceLength(s, at)
            }
            return LuaValue.varargsOf(points.toTypedArray())!!
        }
    }

    /** `utf8.len(s [, i [, j [, lax]]])`, answering nil plus a position on failure. */
    internal class len : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val s: LuaString = args.checkstring(1)!!
            val length: Int = s.m_length
            // Zero is out of bounds rather than a stand-in for one: a
            // position counts from 1, or from the end when negative.
            val first: Int = position(args.optint(2, 1), length, 0)
            val last: Int = position(args.optint(3, -1), length, 0)
            val lax: Boolean = args.optboolean(4, false)
            if (first < 1 || first > length + 1) LuaValue.argerror(2, "initial position out of bounds")
            if (last > length) LuaValue.argerror(3, "final position out of bounds")

            var count = 0
            var at = first
            while (at <= last) {
                if (decode(s, at, lax) == null) {
                    return LuaValue.varargsOf(NIL, LuaValue.valueOf(at.toLong()))!!
                }
                at += sequenceLength(s, at)
                count++
            }
            return LuaValue.valueOf(count.toLong())
        }
    }

    /** `utf8.offset(s, n [, i])`, returning the start and end of the encoding. */
    internal class offset : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val s: LuaString = args.checkstring(1)!!
            val length: Int = s.m_length
            val n: Int = args.checkint(2)
            val default: Int = if (n >= 0) 1 else length + 1
            var at: Int = position(args.optint(3, default), length, default)
            if (at < 1 || at > length + 1) LuaValue.argerror(3, "position out of bounds")

            var remaining = n
            if (remaining == 0) {
                // Back up to the start of whatever character contains byte i.
                while (at > 1 && isContinuation(s, at)) at--
                return span(s, at, length)
            }
            if (remaining > 0) {
                if (isContinuation(s, at)) LuaValue.error("initial position is a continuation byte")
                remaining--
                while (remaining > 0 && at <= length) {
                    at++
                    while (at <= length && isContinuation(s, at)) at++
                    remaining--
                }
                if (remaining > 0) return NIL
                return span(s, at, length)
            }
            if (at <= length && isContinuation(s, at)) LuaValue.error("initial position is a continuation byte")
            while (remaining < 0 && at > 1) {
                at--
                while (at > 1 && isContinuation(s, at)) at--
                remaining++
            }
            if (remaining < 0) return NIL
            return span(s, at, length)
        }

        private fun span(s: LuaString, start: Int, length: Int): Varargs {
            // Landing on a continuation byte means the walk went past the start
            // of the string and there is no character here to report.
            if (start in 1..length && isContinuation(s, start)) {
                LuaValue.error("initial position is a continuation byte")
            }
            // The end is found by following the continuation bytes that are
            // actually there, not by trusting the length the lead byte claims:
            // a truncated sequence reports what the string does contain.
            var end: Int = start
            if (start <= length) {
                while (end + 1 <= length && isContinuation(s, end + 1)) end++
            }
            return LuaValue.varargsOf(LuaValue.valueOf(start.toLong()), LuaValue.valueOf(end.toLong()))!!
        }
    }

    /** `utf8.codes(s [, lax])`, returning the iterator triple. */
    internal class codes : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val s: LuaString = args.checkstring(1)!!
            val lax: Boolean = args.optboolean(2, false)
            // A string that begins mid-sequence has no first character to
            // report, so the mistake is in the argument rather than in the
            // iteration that would follow.
            args.argcheck(s.m_length == 0 || !isContinuation(s, 1), 1, "invalid UTF-8 code")
            return LuaValue.varargsOf(iterator(lax), s, LuaValue.valueOf(0L))!!
        }
    }

    /** The stateless iterator `utf8.codes` hands back. */
    internal class iterator(private val lax: Boolean) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val s: LuaString = args.checkstring(1)!!
            val length: Int = s.m_length
            var at: Int = args.checkint(2)
            // Skip the character the previous step reported.
            if (at > 0) {
                at++
                while (at <= length && isContinuation(s, at)) at++
            } else {
                at = 1
            }
            if (at > length) return NIL
            val decoded: Long = decode(s, at, lax)
                ?: LuaValue.error("invalid UTF-8 code").let { return NONE!! }
            // A continuation byte where the next character should start means
            // the sequence just read was followed by stray bytes.
            val after: Int = at + sequenceLength(s, at)
            if (after <= length && isContinuation(s, after)) LuaValue.error("invalid UTF-8 code")
            return LuaValue.varargsOf(LuaValue.valueOf(at.toLong()), LuaValue.valueOf(decoded))!!
        }
    }

    companion object {
        /**
         * Matches exactly one UTF-8 sequence in a well-formed subject:
         * `"[\0-\x7F\xC2-\xFD][\x80-\xBF]*"`.
         *
         * Built from raw bytes rather than from a Kotlin string, because the
         * bytes above 0x7F would otherwise be UTF-8 encoded into two bytes each
         * and the pattern would not match what upstream's does.
         */
        val CHAR_PATTERN: LuaString = LuaString.valueUsing(
            byteArrayOf(
                '['.code.toByte(), 0x00, '-'.code.toByte(), 0x7F.toByte(),
                0xC2.toByte(), '-'.code.toByte(), 0xFD.toByte(), ']'.code.toByte(),
                '['.code.toByte(), 0x80.toByte(), '-'.code.toByte(), 0xBF.toByte(),
                ']'.code.toByte(), '*'.code.toByte(),
            ),
        )

        private const val MAX_STRICT: Long = 0x10FFFF
        private const val MAX_LAX: Long = 0x7FFFFFFF

        /** Byte at one-based [at], or -1 past the end. */
        private fun byteAt(s: LuaString, at: Int): Int =
            if (at < 1 || at > s.m_length) -1 else s.m_bytes[s.m_offset + at - 1].toInt() and 0xFF

        private fun isContinuation(s: LuaString, at: Int): Boolean {
            val b: Int = byteAt(s, at)
            return b in 0x80..0xBF
        }

        /** Bytes in the sequence starting at [at]; 1 for anything ill-formed. */
        internal fun sequenceLength(s: LuaString, at: Int): Int {
            val b: Int = byteAt(s, at)
            return when {
                b < 0x80 -> 1
                b < 0xC0 -> 1
                b < 0xE0 -> 2
                b < 0xF0 -> 3
                b < 0xF8 -> 4
                b < 0xFC -> 5
                else -> 6
            }
        }

        /** The code point starting at [at], or null if the sequence is invalid. */
        internal fun decode(s: LuaString, at: Int, lax: Boolean): Long? {
            val first: Int = byteAt(s, at)
            if (first < 0) return null
            if (first < 0x80) return first.toLong()
            if (first < 0xC0) return null // a continuation byte cannot start a sequence
            val count: Int = sequenceLength(s, at)
            var value: Long = (first and (0x7F shr count)).toLong()
            for (offset in 1 until count) {
                val next: Int = byteAt(s, at + offset)
                if (next < 0x80 || next > 0xBF) return null
                value = (value shl 6) or (next and 0x3F).toLong()
            }
            val limit: Long = if (lax) MAX_LAX else MAX_STRICT
            if (value > limit) return null
            if (!lax && value in 0xD800..0xDFFF) return null // surrogates
            if (count > 1 && value < MINIMUM[count]) return null // overlong encoding
            return value
        }

        /** Smallest code point each sequence length is allowed to encode. */
        private val MINIMUM = longArrayOf(0, 0, 0x80, 0x800, 0x10000, 0x200000, 0x4000000)

        /** Appends the UTF-8 encoding of [value] to [out]. */
        internal fun encode(value: Long, out: MutableList<Byte>, argument: Int) {
            if (value < 0 || value > MAX_LAX) LuaValue.argerror(argument, "value out of range")
            when {
                value < 0x80 -> out.add(value.toByte())
                value < 0x800 -> {
                    out.add((0xC0 or (value ushr 6).toInt()).toByte())
                    out.add(continuation(value, 0))
                }

                value < 0x10000 -> {
                    out.add((0xE0 or (value ushr 12).toInt()).toByte())
                    out.add(continuation(value, 6))
                    out.add(continuation(value, 0))
                }

                value < 0x200000 -> {
                    out.add((0xF0 or (value ushr 18).toInt()).toByte())
                    out.add(continuation(value, 12))
                    out.add(continuation(value, 6))
                    out.add(continuation(value, 0))
                }

                value < 0x4000000 -> {
                    out.add((0xF8 or (value ushr 24).toInt()).toByte())
                    out.add(continuation(value, 18))
                    out.add(continuation(value, 12))
                    out.add(continuation(value, 6))
                    out.add(continuation(value, 0))
                }

                else -> {
                    out.add((0xFC or (value ushr 30).toInt()).toByte())
                    out.add(continuation(value, 24))
                    out.add(continuation(value, 18))
                    out.add(continuation(value, 12))
                    out.add(continuation(value, 6))
                    out.add(continuation(value, 0))
                }
            }
        }

        private fun continuation(value: Long, shift: Int): Byte =
            (0x80 or ((value ushr shift).toInt() and 0x3F)).toByte()

        /** Turns a Lua string position, possibly negative, into a byte index. */
        internal fun position(given: Int, length: Int, whenZero: Int): Int = when {
            given > 0 -> given
            given == 0 -> whenZero
            -given > length -> 0
            else -> length + given + 1
        }
    }
}
