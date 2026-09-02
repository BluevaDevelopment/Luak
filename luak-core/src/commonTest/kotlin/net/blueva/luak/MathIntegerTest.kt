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
 * The `math` entries that exist because of the integer subtype, and the table
 * storage they exposed.
 *
 * Every expectation was taken from the reference interpreter (`lua-5.5.1`).
 */
class MathIntegerTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
    }

    private fun eval(script: String): LuaValue = globals.load("return $script", "math-test")!!.call()!!

    @Test
    fun integerLimitsAreExposedAndWrapIntoEachOther() {
        assertEquals(Long.MAX_VALUE, eval("math.maxinteger").tolong())
        assertEquals(Long.MIN_VALUE, eval("math.mininteger").tolong())
        assertTrue(eval("math.maxinteger + 1 == math.mininteger").toboolean())
        assertTrue(eval("math.mininteger - 1 == math.maxinteger").toboolean())
    }

    @Test
    fun theLimitsKeepTheirSubtype() {
        // They travel through a table on their way into `math`, which used to
        // turn them into floats.
        assertEquals("integer", eval("math.type(math.maxinteger)").tojstring())
        assertEquals("integer", eval("math.type(math.mininteger)").tojstring())
    }

    @Test
    fun mathTypeDistinguishesTheSubtypes() {
        assertEquals("integer", eval("math.type(1)").tojstring())
        assertTrue(eval("math.type('1')").isnil())
        assertTrue(eval("math.type(nil)").isnil())
        assertTrue(eval("math.type({})").isnil())
    }

    @Test
    fun tointegerConvertsOnlyExactValues() {
        assertEquals(3L, eval("math.tointeger(3.0)").tolong())
        assertTrue(eval("math.tointeger(3.5)").isnil())
        assertTrue(eval("math.tointeger({})").isnil())
    }

    @Test
    fun ultComparesAsUnsigned() {
        assertTrue(eval("math.ult(1, 2)").toboolean())
        // -1 has every bit set, so unsigned it is the largest value there is.
        assertTrue(eval("math.ult(-1, 2)").toboolean().not())
        assertTrue(eval("math.ult(2, -1)").toboolean())
    }

    @Test
    fun absCeilAndFloorAnswerWithIntegers() {
        assertEquals(5L, eval("math.abs(-5)").tolong())
        assertEquals(3L, eval("math.floor(3.7)").tolong())
        assertEquals(4L, eval("math.ceil(3.2)").tolong())
        // Negating mininteger is not representable, so it wraps to itself.
        assertEquals(Long.MIN_VALUE, eval("math.abs(math.mininteger)").tolong())
    }

    @Test
    fun fmodKeepsIntegerOperandsExact() {
        assertEquals(1L, eval("math.fmod(7, 3)").tolong())
        assertEquals(-1L, eval("math.fmod(-7, 3)").tolong())
    }

    @Test
    fun largeIntegersSurviveBeingStoredInATable() {
        // A table with a non-integer key used to unpack numeric values into a
        // raw double field, silently rounding anything past 2^53.
        val script = """
            local t = {}
            t.big = 1234567890123456789
            t["other"] = -1234567890123456789
            return t.big, t.other, math.type(t.big)
        """.trimIndent()
        val result = globals.load(script, "table-integer")!!.invoke()
        assertEquals(1234567890123456789L, result.arg(1).tolong())
        assertEquals(-1234567890123456789L, result.arg(2).tolong())
        assertEquals("integer", result.checkjstring(3))
    }
}
