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
import kotlin.test.fail
import net.blueva.luak.lib.LuaPlatform

/**
 * `global` declarations, new in Lua 5.5.
 *
 * Every expectation was taken from the reference interpreter (`lua-5.5.1`).
 */
class GlobalDeclarationTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
    }

    /** Runs [source], returning the error message if it does not get through. */
    private fun failureOf(source: String): String? = try {
        globals.load(source, "global-test")?.call()
        null
    } catch (failure: LuaError) {
        failure.message
    }

    private fun eval(source: String): String =
        globals.load(source, "global-test")!!.call()!!.tojstring()

    @Test
    fun withNoDeclarationEveryNameIsStillAGlobal() {
        assertEquals("nil", eval("return tostring(neverAssigned)"))
        assertEquals("7", eval("assigned = 7 return tostring(assigned)"))
    }

    @Test
    fun anUndeclaredNameIsRejectedOnceSomethingIsDeclared() {
        val message = failureOf("global x; y = 1")
            ?: fail("an undeclared global must not compile")
        assertTrue(message.contains("variable 'y' not declared"), message)
    }

    @Test
    fun theCollectiveFormPutsTheDefaultBack() {
        assertEquals("1", eval("global *; y = 1 return tostring(y)"))
        // A collective declaration keeps covering names declared after it.
        assertEquals("ok", eval("global *; global q; q = 1; r = 2 return 'ok'"))
    }

    @Test
    fun aDeclarationLastsOnlyToTheEndOfItsBlock() {
        val script = """
            do
                global a
                a = 1
            end
            b = 2
            return tostring(a) .. "," .. tostring(b)
        """.trimIndent()
        assertEquals("1,2", eval(script))
    }

    @Test
    fun aDeclarationDoesNotEscapeItsFunction() {
        val message = failureOf("local function g() global z; z = 1; w = 2 end g()")
            ?: fail("an undeclared global must not compile")
        assertTrue(message.contains("variable 'w' not declared"), message)
    }

    @Test
    fun aDeclarationCanCarryAnInitializer() {
        assertEquals("10,20", eval("global *; global a, b = 10, 20 return a .. ',' .. b"))
    }

    @Test
    fun declaringTheSameGlobalTwiceIsReportedWhenItRuns() {
        val message = failureOf("global n = 1; global n = 2")
            ?: fail("a second declaration of an assigned global must be reported")
        assertTrue(message.contains("global 'n' already defined"), message)
    }

    @Test
    fun aConstGlobalCannotBeAssigned() {
        val message = failureOf("global <const> c = 1; c = 2")
            ?: fail("assigning to a const global must not compile")
        assertTrue(
            message.contains("const variable") && message.contains("'c'"),
            message,
        )
    }

    @Test
    fun globalFunctionDeclaresAndDefinesInOneStep() {
        assertEquals("7", eval("global *; global function f() return 7 end return tostring(f())"))
    }

    @Test
    fun aGlobalCannotBeToBeClosed() {
        val message = failureOf("global x <close> = nil")
            ?: fail("<close> on a global must not compile")
        assertTrue(message.contains("global variables cannot be to-be-closed"), message)
    }

    @Test
    fun globalIsStillAnOrdinaryNameWhereNoDeclarationFollows() {
        // The word is not reserved, so code that already uses it keeps working.
        assertEquals("3", eval("global = 3 return tostring(global)"))
        assertEquals("6", eval("local global = 5 return tostring(global + 1)"))
    }
}
