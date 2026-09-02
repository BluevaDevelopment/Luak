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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import net.blueva.luak.lib.LuaPlatform

/** The host deciding what sequence a lane's `math.random` runs. */
class HostRandomSeedTest {
    private fun draws(globals: Globals): String =
        globals.load(
            """
            local out = {}
            for i = 1, 8 do out[i] = tostring(math.random(1, 1000000)) end
            return table.concat(out, ",")
            """,
            "random-seed-test",
        )!!.call()!!.tojstring()

    @Test
    fun theSameSeedGivesTheSameSequence() {
        val first: Globals = LuaPlatform.standardGlobals().apply { seedrandom(1234, 5678) }
        val second: Globals = LuaPlatform.standardGlobals().apply { seedrandom(1234, 5678) }
        assertEquals(draws(first), draws(second))
    }

    @Test
    fun aDifferentSeedGivesADifferentSequence() {
        val first: Globals = LuaPlatform.standardGlobals().apply { seedrandom(1234, 5678) }
        val second: Globals = LuaPlatform.standardGlobals().apply { seedrandom(1234, 5679) }
        assertNotEquals(draws(first), draws(second))
    }

    @Test
    fun theSecondHalfOfTheSeedDefaultsTheWayLuaDoes() {
        // math.randomseed(x) is math.randomseed(x, 0).
        val fromhost: Globals = LuaPlatform.standardGlobals().apply { seedrandom(99) }
        val fromlua: Globals = LuaPlatform.standardGlobals()
        fromlua.load("math.randomseed(99)", "random-seed-test")!!.call()
        assertEquals(draws(fromlua), draws(fromhost))
    }

    @Test
    fun seedingFromTheHostIsSeedingTheWayAScriptWould() {
        val fromhost: Globals = LuaPlatform.standardGlobals().apply { seedrandom(7, 11) }
        val fromlua: Globals = LuaPlatform.standardGlobals()
        fromlua.load("math.randomseed(7, 11)", "random-seed-test")!!.call()
        assertEquals(draws(fromlua), draws(fromhost))
    }

    @Test
    fun twoUnseededLanesDoNotStartOnTheSameSequence() {
        // A state seeds itself from the host's generator, so a host that never
        // calls seedrandom still does not get two lanes drawing in lockstep.
        val lanes: List<String> = List(4) { draws(LuaPlatform.standardGlobals()) }
        assertEquals(lanes.size, lanes.toSet().size, "two lanes started on the same sequence: $lanes")
    }

    @Test
    fun aScriptCanStillSeedOverTheHost() {
        val globals: Globals = LuaPlatform.standardGlobals().apply { seedrandom(1, 2) }
        globals.load("math.randomseed(1, 2)", "random-seed-test")!!.call()
        val reseeded: String = draws(globals)
        globals.seedrandom(1, 2)
        assertEquals(reseeded, draws(globals))
    }

    @Test
    fun seedingAStateWithoutMathSaysSo() {
        val failure = assertFailsWith<LuaError> { Globals().seedrandom(1, 2) }
        assertTrue(failure.message!!.contains("math library is not loaded"), "was: ${failure.message}")
    }
}
