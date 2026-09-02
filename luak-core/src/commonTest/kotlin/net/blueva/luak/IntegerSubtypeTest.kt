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
import net.blueva.luak.lib.LuaPlatform

/**
 * Lua's 64-bit integer subtype, introduced in 5.3.
 *
 * Luak inherited LuaJ's 32-bit `LuaInteger`, which silently degraded any
 * value outside `Int` range to a float. Every expectation below was taken from
 * the reference interpreter (`lua-5.5.1`), not derived from this code.
 */
class IntegerSubtypeTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
    }

    private fun eval(script: String): LuaValue = globals.load("return $script", "integer-test")!!.call()!!

    @Test
    fun integerLiteralsKeepAllSixtyFourBits() {
        assertEquals(9223372036854775807L, eval("9223372036854775807").tolong())
        assertEquals(1234567890123456789L, eval("1234567890123456789").tolong())
        assertEquals(-9223372036854775807L - 1L, eval("-9223372036854775807 - 1").tolong())
    }

    @Test
    fun integerLiteralsRoundTripThroughTostring() {
        // The old 32-bit path lost precision here, printing ...768.
        assertEquals("1234567890123456789", eval("tostring(1234567890123456789)").tojstring())
        assertEquals("4294967296", eval("tostring(4294967296)").tojstring())
    }

    @Test
    fun integerArithmeticWrapsAroundRatherThanOverflowingToInfinity() {
        // Lua 5.2 produced inf here because every number was a float.
        assertEquals(-9223372036854775807L - 1L, eval("9223372036854775807 + 1").tolong())
        assertEquals(0L, eval("4294967296 * 4294967296").tolong())
        assertEquals(9223372036854775807L, eval("-9223372036854775807 - 2").tolong())
    }

    @Test
    fun hexadecimalIntegerLiteralsWrapAround() {
        // The manual points at hex notation as the way to keep wrap-around.
        assertEquals(-1L, eval("0xFFFFFFFFFFFFFFFF").tolong())
        assertEquals(4294967296L, eval("0x100000000").tolong())
    }

    @Test
    fun largeIntegersWorkAsTableKeys() {
        val script = """
            local t = {}
            t[4294967296] = "big"
            t[-4294967296] = "negative"
            return t[4294967296], t[-4294967296]
        """.trimIndent()
        val result = globals.load(script, "integer-keys")!!.invoke()
        assertEquals("big", result.checkjstring(1))
        assertEquals("negative", result.checkjstring(2))
    }

    @Test
    fun distinctLargeIntegersAreDistinctKeys() {
        // A 32-bit key would have collapsed these two onto one slot.
        val script = """
            local t = {}
            t[4294967296] = "a"
            t[8589934592] = "b"
            return t[4294967296], t[8589934592]
        """.trimIndent()
        val result = globals.load(script, "integer-key-collision")!!.invoke()
        assertEquals("a", result.checkjstring(1))
        assertEquals("b", result.checkjstring(2))
    }

    @Test
    fun valueOfLongNeverDegradesToAFloat() {
        val big = LuaValue.valueOf(1L shl 62)
        assertEquals(1L shl 62, big.tolong())
        assertEquals(1L shl 62, (big as LuaInteger).v)
    }
}
