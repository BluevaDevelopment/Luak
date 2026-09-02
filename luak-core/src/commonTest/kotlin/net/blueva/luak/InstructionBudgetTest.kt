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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.blueva.luak.lib.LuaPlatform
import net.blueva.luak.lib.ZeroArgFunction

/**
 * A host bounding untrusted code without the `debug` library in the picture.
 *
 * The point of [Budget] is that a sandbox gets to keep `debug` unloaded, so
 * every test here builds plain [LuaPlatform.standardGlobals] and checks that
 * the ceiling still holds.
 */
class InstructionBudgetTest {
    private lateinit var globals: Globals
    private lateinit var budget: Budget

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
        budget = Budget()
        globals.budget = budget
    }

    private fun run(script: String): LuaValue = globals.load(script, "budget-test")!!.call()!!

    @Test
    fun aCeilingStopsALoopThatWouldNeverEnd() {
        budget.instructions = 100_000
        val failure = assertFailsWith<LuaError> { run("while true do end") }
        assertTrue(
            failure.message!!.contains("instruction budget exhausted"),
            "unexpected message: ${failure.message}",
        )
    }

    @Test
    fun theDebugLibraryIsNotWhatIsCounting() {
        // The whole point: a sandbox refuses to load debug, and the ceiling
        // has to work anyway.
        assertTrue(globals.get("debug")!!.isnil(), "debug should not be loaded")
        assertEquals(null, globals.debuglib, "no DebugLib should be installed")
        budget.instructions = 50_000
        assertFailsWith<LuaError> { run("while true do end") }
    }

    @Test
    fun theErrorIsAnOrdinaryOneWithAPlaceInTheChunk() {
        // Ordinary means a script sees "chunk:line: message", the shape every
        // other runtime error arrives in - not a host exception.
        budget.instructions = 100_000
        val failure = assertFailsWith<LuaError> { run("while true do end") }
        assertEquals(
            "[string \"budget-test\"]:1: instruction budget exhausted",
            failure.message,
        )
    }

    @Test
    fun workThatFitsUnderTheCeilingRunsToTheEnd() {
        budget.instructions = 1_000_000
        assertEquals(500_500L, run("local s = 0 for i = 1, 1000 do s = s + i end return s").tolong())
    }

    @Test
    fun aChunkCannotBuyItselfMoreByCatchingTheError() {
        // pcall catches it, as it catches any error, and then the very next
        // instruction raises again: the budget stays spent.
        budget.instructions = 100_000
        assertFailsWith<LuaError> {
            run("while true do pcall(function() while true do end end) end")
        }
        assertTrue(budget.exhausted, "the budget should still be spent")
    }

    @Test
    fun aCoroutineSpendsTheSameCeiling() {
        // Otherwise a script would only have to run its loop inside one.
        budget.instructions = 200_000
        assertFailsWith<LuaError> {
            run(
                """
                local co = coroutine.create(function() while true do end end)
                assert(coroutine.resume(co))
                """,
            )
        }
    }

    @Test
    fun eachResumptionGetsTheWholeCeilingBack() {
        budget.instructions = 1_000_000
        val chunk: LuaValue = globals.load("local s = 0 for i = 1, 1000 do s = s + i end return s", "budget-test")!!
        repeat(5) { assertEquals(500_500L, chunk.call()!!.tolong()) }
        assertFalse(budget.exhausted, "a fresh resumption should not start spent")
    }

    @Test
    fun anInterruptStopsWhateverIsRunning() {
        // Stands in for a watchdog: the host asks the interpreter to stop, and
        // it stops at the next instruction rather than at the next call.
        globals.set(
            "watchdog",
            object : ZeroArgFunction() {
                override fun call(): LuaValue? {
                    budget.interrupt()
                    return LuaValue.NIL
                }
            },
        )
        val failure = assertFailsWith<LuaError> { run("watchdog() while true do end") }
        assertTrue(failure.message!!.contains("interrupted"), "unexpected message: ${failure.message}")
    }

    @Test
    fun anInterruptIsClearedByTheNextResumption() {
        budget.interrupt()
        assertTrue(budget.exhausted, "an interrupt should leave the budget spent")
        assertEquals(3L, run("return 1 + 2").tolong())
    }

    @Test
    fun noBudgetLeavesTheStateUnbounded() {
        globals.budget = null
        assertEquals(500_500L, run("local s = 0 for i = 1, 1000 do s = s + i end return s").tolong())
    }
}
