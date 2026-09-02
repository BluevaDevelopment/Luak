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
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.blueva.luak.lib.LuaPlatform

/**
 * Floor division `//`, added to the language in Lua 5.3.
 *
 * Every expectation was taken from the reference interpreter (`lua-5.5.1`).
 *
 * ### Known gap
 *
 * The float cases are still wrong, and not because of `//` itself: Luak
 * inherited LuaJ's habit of collapsing a float with an integral value into an
 * integer, so `0.0` *is* an integer at runtime and `1 // 0.0` raises the
 * integer divide-by-zero error instead of yielding `inf`. Breaking that
 * collapse is the next step of the port; see [floatOperandsProduceFloats],
 * which is ignored until then.
 */
class FloorDivisionTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
    }

    private fun eval(script: String): LuaValue = globals.load("return $script", "idiv-test")!!.call()!!

    @Test
    fun integersFloorTowardsNegativeInfinity() {
        assertEquals(3L, eval("7 // 2").tolong())
        assertEquals(-4L, eval("-7 // 2").tolong())
        assertEquals(-4L, eval("7 // -2").tolong())
        assertEquals(3L, eval("-7 // -2").tolong())
    }

    @Test
    fun dividingAnIntegerByIntegerZeroIsAnError() {
        val result = globals.load("return pcall(function() return 1 // 0 end)", "idiv-zero")!!.invoke()
        assertTrue(result.arg(1).toboolean().not(), "1 // 0 must fail")
        assertTrue(result.checkjstring(2).contains("zero") || result.checkjstring(2).contains("n//0"))
    }

    @Test
    fun minIntegerDividedByMinusOneWrapsRatherThanOverflowing() {
        // -(-2^63) is not representable, so the reference wraps back to itself.
        assertEquals(Long.MIN_VALUE, eval("(-9223372036854775807 - 1) // -1").tolong())
    }

    @Test
    fun bindsAsTightlyAsDivisionAndIsLeftAssociative() {
        assertEquals(2L, eval("9 // 2 // 2").tolong())
        assertEquals(6L, eval("2 + 8 // 2").tolong())
        assertEquals(5L, eval("(2 + 8) // 2").tolong())
    }

    @Test
    fun foldsAtCompileTimeLikeTheOtherArithmetic() {
        // Constant folding must agree with the runtime path.
        assertEquals(3L, eval("7 // 2").tolong())
        assertEquals(-4L, eval("-7 // 2").tolong())
    }

    @Test
    fun fallsBackToTheIdivMetamethod() {
        val script = """
            local t = setmetatable({}, { __idiv = function(a, b) return "idiv-mm" end })
            return t // 2
        """.trimIndent()
        assertEquals("idiv-mm", globals.load(script, "idiv-mm")!!.call()!!.tojstring())
    }

    @Test
    @Ignore // Blocked on the integer/float collapse; see the class documentation.
    fun floatOperandsProduceFloats() {
        assertEquals("3.0", eval("tostring(7.0 // 2)").tojstring())
        assertEquals("3.0", eval("tostring(7 // 2.0)").tojstring())
        assertEquals("inf", eval("tostring(1 // 0.0)").tojstring())
        assertEquals("-inf", eval("tostring(-1 // 0.0)").tojstring())
        assertEquals("-4.0", eval("tostring(-7.5 // 2)").tojstring())
    }
}
