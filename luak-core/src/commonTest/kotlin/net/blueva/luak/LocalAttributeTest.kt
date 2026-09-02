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
 * Local variable attributes, `local x <const>` and `local x <close>`, from
 * Lua 5.4.
 *
 * Every expectation was taken from the reference interpreter (`lua-5.5.1`).
 */
class LocalAttributeTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
    }

    /** Compiles [source], returning the error message if it does not compile. */
    private fun compileError(source: String): String? = try {
        globals.load(source, "attribute-test")
        null
    } catch (failure: LuaError) {
        failure.message
    }

    @Test
    fun constLocalsBehaveLikeOrdinaryLocals() {
        val script = """
            local answer <const> = 42
            local other <const>, plain = 1, 2
            return answer, answer + 1, other + plain
        """.trimIndent()
        val result = globals.load(script, "const-read")!!.invoke()
        assertEquals(42L, result.arg(1).tolong())
        assertEquals(43L, result.arg(2).tolong())
        assertEquals(3L, result.arg(3).tolong())
    }

    @Test
    fun assigningToAConstLocalIsACompileError() {
        val message = compileError("local x <const> = 42; x = 1")
            ?: fail("assigning to a const local must not compile")
        assertTrue(
            message.contains("const variable") && message.contains("'x'"),
            "message should name the variable, was: $message",
        )
    }

    @Test
    fun assigningToAConstLocalIsCaughtInAMultipleAssignment() {
        val message = compileError("local a <const> = 1; local b = 2; b, a = 3, 4")
            ?: fail("assigning to a const local must not compile")
        assertTrue(message.contains("const variable"), message)
    }

    @Test
    fun aPlainLocalIsStillAssignable() {
        val script = """
            local x = 1
            x = x + 1
            return x
        """.trimIndent()
        assertEquals(2L, globals.load(script, "plain-local")!!.call()!!.tolong())
    }

    @Test
    fun unknownAttributesAreRejected() {
        val message = compileError("local x <bogus> = 1")
            ?: fail("an unknown attribute must not compile")
        assertTrue(message.contains("unknown attribute") && message.contains("bogus"), message)
    }

    @Test
    fun closeVariablesAreClosedInReverseOrderOnLeavingTheBlock() {
        val script = """
            local log = {}
            local function res(name)
                return setmetatable({}, {__close = function() log[#log + 1] = name end})
            end
            do
                local a <close> = res("a")
                local b <close> = res("b")
                log[#log + 1] = "body"
            end
            return table.concat(log, ",")
        """.trimIndent()
        assertEquals("body,b,a", globals.load(script, "close-order")!!.call()!!.tojstring())
    }

    @Test
    fun closeHandlersRunWhileAnErrorUnwindsAndSeeIt() {
        val script = """
            local seen
            local ok, err = pcall(function()
                local a <close> = setmetatable({}, {__close = function(_, e) seen = e end})
                error("boom", 0)
            end)
            return tostring(ok) .. "|" .. tostring(err) .. "|" .. tostring(seen)
        """.trimIndent()
        assertEquals("false|boom|boom", globals.load(script, "close-error")!!.call()!!.tojstring())
    }

    @Test
    fun breakAndReturnBothCloseOnTheWayOut() {
        val script = """
            local log = {}
            local function res(name)
                return setmetatable({}, {__close = function() log[#log + 1] = name end})
            end
            for i = 1, 3 do
                local a <close> = res("loop" .. i)
                if i == 2 then break end
            end
            local function f()
                local a <close> = res("ret")
                return "value"
            end
            local v = f()
            return v .. "|" .. table.concat(log, ",")
        """.trimIndent()
        assertEquals("value|loop1,loop2,ret", globals.load(script, "close-exits")!!.call()!!.tojstring())
    }

    @Test
    fun falseAndNilNeedNoCloseMetamethod() {
        val script = """
            do
                local a <close> = nil
                local b <close> = false
            end
            return "ok"
        """.trimIndent()
        assertEquals("ok", globals.load(script, "close-falsy")!!.call()!!.tojstring())
    }

    @Test
    fun aValueWithNoCloseMetamethodIsRejectedAtTheDeclaration() {
        val failure = runCatching { globals.load("local x <close> = 42", "close-bad")!!.call() }
            .exceptionOrNull() as? LuaError
            ?: fail("a non-closable value must be reported")
        assertTrue(
            failure.message!!.contains("variable 'x' got a non-closable value"),
            "message should name the variable, was: ${failure.message}",
        )
    }

    @Test
    fun onlyOneCloseVariablePerLocalStatement() {
        val message = compileError("local x <close>, y <close> = a, b")
            ?: fail("two <close> variables in one statement must not compile")
        assertTrue(message.contains("multiple to-be-closed variables"), message)
    }

    @Test
    fun assigningToACloseLocalIsACompileError() {
        val message = compileError("local x <close> = nil; x = 1")
            ?: fail("assigning to a close local must not compile")
        assertTrue(message.contains("const variable") && message.contains("'x'"), message)
    }
}
