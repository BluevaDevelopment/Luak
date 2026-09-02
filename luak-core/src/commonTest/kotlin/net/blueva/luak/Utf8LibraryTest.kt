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

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.blueva.luak.lib.LuaPlatform

/**
 * The `utf8` library and the `\u{XXX}` string escape, both from Lua 5.3.
 *
 * Every expectation was taken from the reference interpreter (`lua-5.5.1`),
 * comparing raw bytes rather than rendered text.
 */
class Utf8LibraryTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
    }

    private fun eval(script: String): Varargs = globals.load(script, "utf8-test")!!.invoke()

    /** Hex of the bytes a Lua expression produces, so comparisons are exact. */
    private fun bytesOf(expression: String): String {
        val script = """
            local s = $expression
            local t = {}
            for i = 1, #s do t[#t + 1] = string.format("%02X", s:byte(i)) end
            return table.concat(t, " ")
        """.trimIndent()
        return globals.load(script, "utf8-bytes")!!.call()!!.tojstring()
    }

    @Test
    fun charEncodesEachCodePoint() {
        assertEquals("C3 A9", bytesOf("utf8.char(233)"))
        assertEquals("E4 B8 AD", bytesOf("utf8.char(0x4E2D)"))
        assertEquals("F0 9F 98 80", bytesOf("utf8.char(0x1F600)"))
        assertEquals("48 C3 A9", bytesOf("utf8.char(72, 233)"))
    }

    @Test
    fun theUnicodeEscapeEncodesAsUtf8() {
        assertEquals("C3 A9", bytesOf("\"\\u{E9}\""))
        assertEquals("F0 9F 98 80", bytesOf("\"\\u{1F600}\""))
        // \x stays a single raw byte, unlike \u.
        assertEquals("E9", bytesOf("\"\\xE9\""))
    }

    @Test
    fun lenCountsCharactersNotBytes() {
        assertEquals(5L, eval("return utf8.len('h\\u{E9}llo')").arg(1).tolong())
        assertEquals(3L, eval("return utf8.len('abc')").arg(1).tolong())
    }

    @Test
    fun lenReportsWhereAnInvalidSequenceStarts() {
        val result = eval("return utf8.len('h\\xE9llo')")
        assertTrue(result.isnil(1), "an invalid sequence must give nil")
        assertEquals(2L, result.arg(2).tolong())
    }

    @Test
    fun codepointReadsOneOrManyCharacters() {
        assertEquals(104L, eval("return utf8.codepoint('h\\u{E9}llo', 1)").arg(1).tolong())
        assertEquals(233L, eval("return utf8.codepoint('h\\u{E9}llo', 2)").arg(1).tolong())
        val many = eval("return utf8.codepoint('abc', 1, 3)")
        assertEquals(97L, many.arg(1).tolong())
        assertEquals(98L, many.arg(2).tolong())
        assertEquals(99L, many.arg(3).tolong())
    }

    @Test
    fun offsetReturnsTheStartAndEndOfAnEncoding() {
        // 'é' occupies bytes 2 and 3, so the third character starts at 4.
        val third = eval("return utf8.offset('h\\u{E9}llo', 3)")
        assertEquals(4L, third.arg(1).tolong())
        assertEquals(4L, third.arg(2).tolong())

        val last = eval("return utf8.offset('h\\u{E9}llo', -1)")
        assertEquals(6L, last.arg(1).tolong())
    }

    @Test
    fun codesIteratesPositionsAndCodePoints() {
        val script = """
            local out = {}
            for p, c in utf8.codes('h\u{E9}') do out[#out + 1] = p .. '=' .. c end
            return table.concat(out, ' ')
        """.trimIndent()
        assertEquals("1=104 2=233", globals.load(script, "utf8-codes")!!.call()!!.tojstring())
    }

    @Test
    fun charpatternMatchesOneSequence() {
        // Built from raw bytes; encoding it as text would double the high ones.
        assertEquals(
            "5B 00 2D 7F C2 2D FD 5D 5B 80 2D BF 5D 2A",
            bytesOf("utf8.charpattern"),
        )
        val found = eval("return string.find('h\\u{E9}llo', utf8.charpattern)")
        assertEquals(1L, found.arg(1).tolong())
    }
}
