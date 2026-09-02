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
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.blueva.luak.lib.LuaPlatform

/**
 * Starting a lane without compiling its plugin again; see [Globals.compile].
 */
class CompileOncePerLaneTest {
    private val template: Globals = LuaPlatform.standardGlobals()

    @Test
    fun aChunkCompiledOnceRunsInEveryLane() {
        val plugin: Prototype = template.compile("return 21 * 2", "@plugin.lua")
        repeat(4) {
            val lane: Globals = LuaPlatform.standardGlobals()
            assertEquals(42L, lane.bind(plugin).call()!!.tolong())
        }
    }

    @Test
    fun eachLaneSeesItsOwnGlobals() {
        val plugin: Prototype = template.compile("counter = (counter or 0) + 1 return counter", "@plugin.lua")
        val first: Globals = LuaPlatform.standardGlobals()
        val second: Globals = LuaPlatform.standardGlobals()
        assertEquals(1L, first.bind(plugin).call()!!.tolong())
        assertEquals(1L, second.bind(plugin).call()!!.tolong())
        assertEquals(2L, first.bind(plugin).call()!!.tolong())
        assertTrue(second.get("counter")!!.tolong() == 1L, "the lanes should not share a global")
    }

    @Test
    fun eachBindGetsItsOwnUpvalues() {
        // Two functions off the same prototype must not share the local the
        // closure captured, or one lane would count for another.
        val plugin: Prototype = template.compile(
            "local n = 0 return function() n = n + 1 return n end",
            "@plugin.lua",
        )
        val first: LuaValue = template.bind(plugin).call()!!
        val second: LuaValue = template.bind(plugin).call()!!
        assertEquals(1L, first.call()!!.tolong())
        assertEquals(2L, first.call()!!.tolong())
        assertEquals(1L, second.call()!!.tolong())
    }

    @Test
    fun theLaneSuppliesTheEnvironmentTheChunkSees() {
        val plugin: Prototype = template.compile("return _G", "@plugin.lua")
        val lane: Globals = LuaPlatform.standardGlobals()
        assertSame(lane, lane.bind(plugin).call())
        assertNotSame(lane, template.bind(plugin).call())
    }

    @Test
    fun anErrorPointsAtTheNameItWasCompiledUnder() {
        val plugin: Prototype = template.compile("local t = nil return t.x", "@plugin.lua")
        val lane: Globals = LuaPlatform.standardGlobals()
        val failure = assertFailsWith<LuaError> { lane.bind(plugin).call() }
        assertTrue(failure.message!!.startsWith("plugin.lua:1:"), "was: ${failure.message}")
    }

    @Test
    fun aLaneMayBeGivenAnEnvironmentOfItsOwn() {
        // What a chunk sees as its globals need not be the state it runs in,
        // which is how one lane hands a plugin a cut-down _ENV.
        val plugin: Prototype = template.compile("return answer", "@plugin.lua")
        val lane: Globals = LuaPlatform.standardGlobals()
        val sandbox = LuaTable()
        sandbox.set("answer", 42)
        assertEquals(42L, lane.bind(plugin, sandbox).call()!!.tolong())
        assertTrue(lane.bind(plugin).call()!!.isnil(), "the lane's own globals have no answer")
    }

    @Test
    fun aChunkThatDoesNotCompileSaysSo() {
        val failure = assertFailsWith<LuaError> { template.compile("return ((", "@broken.lua") }
        assertTrue(failure.message!!.isNotEmpty(), "expected a message")
    }

    @Test
    fun bindingIntoAStateWithNoLoaderSaysSo() {
        val plugin: Prototype = template.compile("return 1", "@plugin.lua")
        val failure = assertFailsWith<LuaError> { Globals().bind(plugin) }
        assertTrue(failure.message!!.contains("No loader"), "was: ${failure.message}")
    }

    @Test
    fun whatABoundClosureCostsIsChargedToItsOwnLane() {
        val plugin: Prototype = template.compile("return 1", "@plugin.lua")
        val lane: Globals = LuaPlatform.standardGlobals()
        lane.startmemorycount()
        lane.bind(plugin)
        assertTrue(lane.memorycharged > 0, "the closure should have been charged to the lane it went into")
    }
}
