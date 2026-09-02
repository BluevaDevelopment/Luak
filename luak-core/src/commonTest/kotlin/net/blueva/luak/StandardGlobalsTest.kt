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

import kotlin.math.PI
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.blueva.luak.lib.LuaPlatform

/**
 * The multiplatform entry point: every target must get the same standard
 * library set out of [LuaPlatform.standardGlobals], with no JVM-only step.
 */
class StandardGlobalsTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
    }

    private fun eval(script: String): LuaValue = globals.load(script, "standard-globals-test")!!.call()!!

    @Test
    fun everyStandardLibraryIsPresent() {
        for (library in arrayOf("string", "table", "math", "os", "io", "coroutine", "utf8", "package")) {
            assertFalse(globals.get(library)!!.isnil(), "missing library: $library")
        }
        for (function in arrayOf("print", "pairs", "pcall", "require", "load", "setmetatable", "warn")) {
            assertFalse(globals.get(function)!!.isnil(), "missing base function: $function")
        }
    }

    @Test
    fun bit32IsNotLoadedByDefault() {
        // Deprecated in 5.3 and removed in 5.4: with 64-bit integers and the
        // operators in the language there is nothing left for it to do.
        assertTrue(globals.get("bit32")!!.isnil(), "bit32 should not be loaded")
    }

    @Test
    fun compilerAndUndumperAreBothInstalled() {
        // load() has to accept source text and the binary chunks string.dump
        // produces from it; standardGlobals installs LuaC and LoadState so a
        // caller does not have to know about either.
        assertEquals(7, eval("return load('return 3 + 4')()").checkint())
        assertEquals(7, eval("return load(string.dump(load('return 3 + 4')))()").checkint())
    }

    @Test
    fun debugGlobalsAddsTheDebugLibrary() {
        assertTrue(globals.get("debug")!!.isnil())
        assertFalse(LuaPlatform.debugGlobals().get("debug")!!.isnil())
    }

    @Test
    fun mathLibraryIsComplete() {
        // These used to exist only in the JVM subclass, leaving every other
        // target with LuaJ's reduced J2ME math library.
        for (name in arrayOf("acos", "asin", "atan", "log")) {
            assertFalse(globals.get("math")!!.get(name)!!.isnil(), "missing math function: $name")
        }
        assertEquals(0.0, eval("return math.acos(1)").checkdouble())
        assertEquals(PI / 2, eval("return math.asin(1)").checkdouble(), 1e-12)
        assertEquals(PI / 4, eval("return math.atan(1)").checkdouble(), 1e-12)
        // atan took over from atan2 when the second argument was added to it.
        assertEquals(PI / 4, eval("return math.atan(1, 1)").checkdouble(), 1e-12)
        assertEquals(1.0, eval("return math.log(math.exp(1))").checkdouble(), 1e-12)
        assertEquals(3.0, eval("return math.log(8, 2)").checkdouble(), 1e-12)
    }

    @Test
    fun theAliasesRemovedIn54AreGone() {
        // math.atan2, cosh, pow, sinh and tanh were deprecated in 5.3 and
        // removed in 5.4; a chunk written for 5.5 must not find them.
        for (name in arrayOf("atan2", "cosh", "pow", "sinh", "tanh")) {
            assertTrue(globals.get("math")!!.get(name)!!.isnil(), "math.$name should be gone")
        }
    }

    @Test
    fun powerIsAccurateRatherThanApproximated() {
        // The inherited J2ME longhand pow() was off by ~1e-6 here; kotlin.math
        // is exact to the last bits on every target.
        assertEquals(1.4142135623730951, eval("return 2 ^ 0.5").checkdouble(), 1e-15)
        assertEquals(1024.0, eval("return 2 ^ 10").checkdouble(), 1e-12)
        assertEquals(0.1, eval("return 10 ^ -1").checkdouble(), 1e-15)
    }

    @Test
    fun exponentialUsesTheStandardLibrary() {
        assertEquals(2.718281828459045, eval("return math.exp(1)").checkdouble(), 1e-15)
    }
}
