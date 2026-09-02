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
import kotlin.test.assertTrue
import net.blueva.luak.lib.LuaPlatform

/**
 * What stops a plugin taking the process down by filling a table.
 *
 * See [Globals.memoryceiling] for what the figure counts and why it is a
 * conservative bound rather than an exact live one.
 */
class MemoryCeilingTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
        globals.startmemorycount()
    }

    private fun run(script: String): LuaValue = globals.load(script, "memory-test")!!.call()!!

    @Test
    fun fillingATableForeverStopsAtTheCeiling() {
        globals.memoryceiling = 1024 * 1024
        val failure = assertFailsWith<LuaError> {
            run("local t = {} local i = 1 while true do t[i] = i i = i + 1 end")
        }
        assertTrue(
            failure.message!!.contains("not enough memory"),
            "unexpected message: ${failure.message}",
        )
    }

    @Test
    fun buildingStringsForeverStopsToo() {
        globals.memoryceiling = 1024 * 1024
        assertFailsWith<LuaError> {
            run("local s = '' while true do s = s .. 'xxxxxxxxxxxxxxxx' end")
        }
    }

    @Test
    fun workThatFitsUnderTheCeilingRunsToTheEnd() {
        globals.memoryceiling = 4 * 1024 * 1024
        assertEquals(1000L, run("local t = {} for i = 1, 1000 do t[i] = i end return #t").tolong())
    }

    @Test
    fun aScriptCannotClearTheCeilingWithCollectgarbage() {
        // The tally behind collectgarbage("count") is reset by collecting;
        // this one is not, or a sandbox would be one library call from
        // unlimited.
        globals.memoryceiling = 1024 * 1024
        assertFailsWith<LuaError> {
            run(
                """
                local t = {}
                local i = 1
                while true do
                  t[i] = i
                  i = i + 1
                  if i % 1000 == 0 then collectgarbage() collectgarbage("step") end
                end
                """,
            )
        }
    }

    @Test
    fun catchingTheErrorDoesNotBuyMoreRoom() {
        globals.memoryceiling = 1024 * 1024
        assertFailsWith<LuaError> {
            run(
                """
                local t = {}
                local i = 1
                while true do
                  pcall(function() t[i] = i i = i + 1 end)
                end
                """,
            )
        }
        assertTrue(globals.memorycharged > globals.memoryceiling, "the tally should still be over the ceiling")
    }

    @Test
    fun theHostPutsTheStateBackToWork() {
        globals.memoryceiling = 1024 * 1024
        assertFailsWith<LuaError> { run("local t = {} local i = 1 while true do t[i] = i i = i + 1 end") }
        globals.startmemorycount()
        assertEquals(0L, globals.memorycharged)
        assertEquals(3L, run("return 1 + 2").tolong())
    }

    @Test
    fun oneLaneDoesNotSpendAnothersCeiling() {
        val other: Globals = LuaPlatform.standardGlobals()
        other.memoryceiling = 1024 * 1024
        other.startmemorycount()

        globals.load("local t = {} for i = 1, 20000 do t[i] = i end", "memory-test")!!.call()

        // The runaway lane above charged its own state, not this one, so this
        // one still has the whole of its ceiling.
        assertTrue(
            other.memorycharged < 1024,
            "an idle lane should have been charged almost nothing, was ${other.memorycharged}",
        )
        assertEquals(1000L, other.load("local t = {} for i = 1, 1000 do t[i] = i end return #t")!!.call()!!.tolong())
    }

    @Test
    fun oneLanesCountIsNotAnothers() {
        // collectgarbage("count") answers for the state that asks, so a busy
        // neighbour does not make a quiet lane look like it is holding memory.
        val other: Globals = LuaPlatform.standardGlobals()
        val quietcount: LuaValue = other.load("return collectgarbage('count')", "memory-test")!!
        val busycount: LuaValue = globals.load("return collectgarbage('count')", "memory-test")!!
        val fill: LuaValue = globals.load("local t = {} for i = 1, 5000 do t[i] = i end", "memory-test")!!

        val quietbefore: Double = quietcount.call()!!.todouble()
        val busybefore: Double = busycount.call()!!.todouble()
        fill.call()
        val quietafter: Double = quietcount.call()!!.todouble()
        val busyafter: Double = busycount.call()!!.todouble()

        assertTrue(
            busyafter - busybefore > 32.0,
            "the lane that filled the table should count the storage: $busybefore -> $busyafter",
        )
        assertTrue(
            quietafter - quietbefore < 1.0,
            "the idle lane should have counted next to nothing: $quietbefore -> $quietafter",
        )
    }

    @Test
    fun noCeilingIsTheDefault() {
        assertEquals(0L, globals.memoryceiling)
        assertEquals(1000L, run("local t = {} for i = 1, 1000 do t[i] = i end return #t").tolong())
    }
}
