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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.blueva.luak.lib.LuaPlatform

/**
 * The bitwise operators `& | ~ << >>` and unary `~`, added in Lua 5.3.
 *
 * Every expectation was taken from the reference interpreter (`lua-5.5.1`).
 * They operate on the 64-bit integer subtype, which is why they could not
 * exist while numbers were 32-bit.
 */
class BitwiseOperatorTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
    }

    private fun eval(script: String): LuaValue = globals.load("return $script", "bitwise-test")!!.call()!!

    @Test
    fun andOrExclusiveOrAndNot() {
        assertEquals(48L, eval("0xF0 & 0x3C").tolong())
        assertEquals(255L, eval("0xF0 | 0x0F").tolong())
        assertEquals(15L, eval("0xF0 ~ 0xFF").tolong())
        assertEquals(-1L, eval("~0").tolong())
        assertEquals(-6L, eval("~5").tolong())
    }

    @Test
    fun shiftsAreLogicalNotArithmetic() {
        assertEquals(16L, eval("1 << 4").tolong())
        assertEquals(16L, eval("256 >> 4").tolong())
        // The sign bit is shifted in as a zero, so this is maxinteger.
        assertEquals(9223372036854775807L, eval("-1 >> 1").tolong())
    }

    @Test
    fun oversizedAndNegativeShiftCounts() {
        assertEquals(0L, eval("1 << 64").tolong())
        assertEquals(Long.MIN_VALUE, eval("1 << 63").tolong())
        // A negative count reverses the direction rather than erroring.
        assertEquals(0L, eval("1 << -1").tolong())
        assertEquals(2L, eval("1 >> -1").tolong())
    }

    @Test
    fun precedenceMatchesTheReference() {
        assertEquals(9L, eval("5 & 3 | 8").tolong()) // '&' binds tighter than '|'
        assertEquals(8L, eval("1 << 2 + 1").tolong()) // '+' binds tighter than '<<'
        assertEquals(3L, eval("2 ~ 3 & 1").tolong()) // '&' binds tighter than binary '~'
    }

    @Test
    fun floatsWithAnIntegralValueAreAccepted() {
        assertEquals(1L, eval("3.0 & 1").tolong())
    }

    @Test
    fun floatsWithAFractionalPartFailAtRunTime() {
        // Crucially at run time, not compile time: constant folding must leave
        // this alone so pcall can catch it.
        val result = globals.load("return pcall(function() return 1.5 & 1 end)", "bitwise-frac")!!.invoke()
        assertFalse(result.arg(1).toboolean())
        assertTrue(result.checkjstring(2).contains("no integer representation"))
    }

    @Test
    fun stringsAreNotCoercedForBitwiseOperations() {
        // Arithmetic coerces numeric strings; bitwise does not, as of 5.4.
        val result = globals.load("return pcall(function() return '3' & 1 end)", "bitwise-string")!!.invoke()
        assertFalse(result.arg(1).toboolean())
    }

    @Test
    fun fallsBackToTheBitwiseMetamethods() {
        val script = """
            local t = setmetatable({}, {
                __band = function() return "band" end,
                __bor = function() return "bor" end,
                __bxor = function() return "bxor" end,
                __shl = function() return "shl" end,
                __shr = function() return "shr" end,
                __bnot = function() return "bnot" end,
            })
            return t & 1, t | 1, t ~ 1, t << 1, t >> 1, ~t
        """.trimIndent()
        val result = globals.load(script, "bitwise-mm")!!.invoke()
        assertEquals("band", result.checkjstring(1))
        assertEquals("bor", result.checkjstring(2))
        assertEquals("bxor", result.checkjstring(3))
        assertEquals("shl", result.checkjstring(4))
        assertEquals("shr", result.checkjstring(5))
        assertEquals("bnot", result.checkjstring(6))
    }

    @Test
    fun worksOnValuesFromRegistersNotJustConstants() {
        // Constant folding covers the literal form; this exercises the VM path.
        val script = """
            local x = 0xFF
            local y = 4
            return x & 0x0F, x >> y, x << 1, ~x
        """.trimIndent()
        val result = globals.load(script, "bitwise-registers")!!.invoke()
        assertEquals(15L, result.arg(1).tolong())
        assertEquals(15L, result.arg(2).tolong())
        assertEquals(510L, result.arg(3).tolong())
        assertEquals(-256L, result.arg(4).tolong())
    }
}
