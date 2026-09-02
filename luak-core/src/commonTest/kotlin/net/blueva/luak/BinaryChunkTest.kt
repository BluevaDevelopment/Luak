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
import net.blueva.luak.compiler.LuaC
import net.blueva.luak.lib.BaseLib
import net.blueva.luak.lib.PackageLib
import net.blueva.luak.lib.StringLib

/** `string.dump`/`load` round-trip, checked on every KMP target. */
class BinaryChunkTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun installRuntime() {
        globals = Globals()
        LoadState.install(globals)
        LuaC.install(globals)
        globals.load(BaseLib())
        globals.load(PackageLib())
        globals.load(StringLib())
    }

    @Test
    fun dumpedChunkLoadsAndExecutesIdentically() {
        val script =
            "local function combine(a, b) return a + b * 2 end\n" +
                "local bytes = string.dump(combine)\n" +
                "local loaded = load(bytes, 'dumped-chunk')\n" +
                "return loaded(3, 4)\n"
        val result = globals.load(script, "binary-chunk-roundtrip")!!.call()!!
        assertEquals(11, result.checkint())
    }

    @Test
    fun dumpedBytesCarryTheBinaryChunkSignature() {
        val script =
            "local function f() return 1 end\n" +
                "return string.dump(f)\n"
        val dumped = globals.load(script, "binary-chunk-signature")!!.call()!!.checkjstring()!!
        // Signature is [0x1B, 'L', 'u', 'a']; skip the control byte.
        assertTrue(dumped.length >= 4)
        assertEquals("Lua", dumped.substring(1, 4))
    }

    @Test
    fun strippedDumpDropsDebugInfoButStillExecutes() {
        val script =
            "local function combine(a, b) return a - b end\n" +
                "local stripped = string.dump(combine, true)\n" +
                "local loaded = load(stripped, 'stripped-chunk')\n" +
                "return loaded(10, 4)\n"
        val result = globals.load(script, "binary-chunk-stripped")!!.call()!!
        assertEquals(6, result.checkint())
    }
}
