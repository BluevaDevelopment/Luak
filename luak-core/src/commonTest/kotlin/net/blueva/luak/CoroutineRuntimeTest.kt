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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.blueva.luak.compiler.LuaC
import net.blueva.luak.lib.BaseLib
import net.blueva.luak.lib.CoroutineLib
import net.blueva.luak.lib.PackageLib
import net.blueva.luak.lib.TableLib

/**
 * Coverage for the suspend-based coroutine runtime (LuaThread/LuaClosure),
 * which replaced the old thread-blocking CoroutineRunner so coroutines work
 * for real on every KMP target, including JS and Wasm where there is no
 * thread to block. This file lives in commonTest specifically so every one
 * of these runs on JVM, JS (Node), Wasm JS (Node) and Native alike.
 */
class CoroutineRuntimeTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun installRuntime() {
        globals = Globals()
        LuaC.install(globals)
        globals.load(BaseLib())
        globals.load(PackageLib())
        globals.load(TableLib())
        globals.load(CoroutineLib())
    }

    private fun run(script: String): Varargs {
        return globals.load(script.trimIndent(), "coroutine-test")!!.invoke()
    }

    @Test
    fun preservesExecutionStateAcrossYieldAndResume() {
        val result = run(
            """
            local coroutine = coroutine
            local thread = coroutine.create(function(initial)
                local resumed = coroutine.yield(initial + 1)
                return resumed + 2
            end)
            local firstOk, firstValue = coroutine.resume(thread, 10)
            local secondOk, secondValue = coroutine.resume(thread, 20)
            return firstOk, firstValue, secondOk, secondValue, coroutine.status(thread)
            """,
        )

        assertTrue(result.arg(1)!!.toboolean())
        assertEquals(11, result.arg(2)!!.checkint())
        assertTrue(result.arg(3)!!.toboolean(), result.arg(4)!!.tojstring())
        assertEquals(22, result.arg(4)!!.checkint())
        assertEquals("dead", result.arg(5)!!.checkjstring())
    }

    @Test
    fun firstResumeArgumentsBecomeTheBodysArguments() {
        val result = run(
            """
            local thread = coroutine.create(function(a, b, c)
                return a, b, c
            end)
            return coroutine.resume(thread, 'x', 'y', 'z')
            """,
        )
        assertTrue(result.arg(1)!!.toboolean())
        assertEquals("x", result.arg(2)!!.tojstring())
        assertEquals("y", result.arg(3)!!.tojstring())
        assertEquals("z", result.arg(4)!!.tojstring())
    }

    @Test
    fun manyYieldResumeCyclesEachSeeTheRightArguments() {
        val result = run(
            """
            local thread = coroutine.create(function()
                local total = 0
                for i = 1, 5 do
                    total = total + coroutine.yield(total)
                end
                return total
            end)
            local values = {}
            local ok, v = coroutine.resume(thread)
            local n = 0
            while ok and coroutine.status(thread) ~= 'dead' do
                n = n + 1
                values[n] = v
                ok, v = coroutine.resume(thread, n)
            end
            n = n + 1
            values[n] = v
            return n, table.concat(values, ',')
            """,
        )
        // yields: 0, then 0+1=1, 1+2=3, 3+3=6, 6+4=10, final return 10+5=15
        assertEquals(6, result.arg(1)!!.checkint())
        assertEquals("0,1,3,6,10,15", result.arg(2)!!.tojstring())
    }

    @Test
    fun yieldReturnsTheExactArgumentsPassedToResume() {
        val result = run(
            """
            local thread = coroutine.create(function()
                local a, b = coroutine.yield()
                return a, b
            end)
            coroutine.resume(thread)
            local ok, a, b = coroutine.resume(thread, 'first', 'second')
            return ok, a, b
            """,
        )
        assertTrue(result.arg(1)!!.toboolean())
        assertEquals("first", result.arg(2)!!.tojstring())
        assertEquals("second", result.arg(3)!!.tojstring())
    }

    @Test
    fun multipleValuesAreYieldedAndReturned() {
        val result = run(
            """
            local thread = coroutine.create(function()
                local a, b, c = coroutine.yield(1, 2, 3)
                return a + 1, b + 1, c + 1
            end)
            local ok1, y1, y2, y3 = coroutine.resume(thread)
            local ok2, r1, r2, r3 = coroutine.resume(thread, 10, 20, 30)
            return y1, y2, y3, r1, r2, r3
            """,
        )
        assertEquals(1, result.arg(1)!!.checkint())
        assertEquals(2, result.arg(2)!!.checkint())
        assertEquals(3, result.arg(3)!!.checkint())
        assertEquals(11, result.arg(4)!!.checkint())
        assertEquals(21, result.arg(5)!!.checkint())
        assertEquals(31, result.arg(6)!!.checkint())
    }

    @Test
    fun nestedCoroutinesEachTrackTheirOwnState() {
        val result = run(
            """
            local inner = coroutine.create(function()
                return coroutine.yield('inner-1') .. '-resumed'
            end)
            local outer = coroutine.create(function()
                local ok, innerVal = coroutine.resume(inner)
                local fromCaller = coroutine.yield('outer-got-' .. innerVal)
                local ok2, innerResult = coroutine.resume(inner, 'inner-arg')
                return fromCaller, innerResult
            end)
            local ok1, v1 = coroutine.resume(outer)
            local ok2, v2, v3 = coroutine.resume(outer, 'outer-arg')
            return v1, v2, v3
            """,
        )
        assertEquals("outer-got-inner-1", result.arg(1)!!.tojstring())
        assertEquals("outer-arg", result.arg(2)!!.tojstring())
        assertEquals("inner-arg-resumed", result.arg(3)!!.tojstring())
    }

    @Test
    fun errorBeforeAnyYieldIsReportedByResume() {
        val result = run(
            """
            local thread = coroutine.create(function()
                error('boom-before-yield')
            end)
            local ok, msg = coroutine.resume(thread)
            return ok, msg, coroutine.status(thread)
            """,
        )
        assertFalse(result.arg(1)!!.toboolean())
        assertTrue(result.arg(2)!!.tojstring().contains("boom-before-yield"))
        assertEquals("dead", result.arg(3)!!.checkjstring())
    }

    @Test
    fun errorAfterYieldIsReportedByTheResumeThatTriggersIt() {
        val result = run(
            """
            local thread = coroutine.create(function()
                coroutine.yield('checkpoint')
                error('boom-after-yield')
            end)
            local ok1, v1 = coroutine.resume(thread)
            local ok2, v2 = coroutine.resume(thread)
            return ok1, v1, ok2, v2, coroutine.status(thread)
            """,
        )
        assertTrue(result.arg(1)!!.toboolean())
        assertEquals("checkpoint", result.arg(2)!!.tojstring())
        assertFalse(result.arg(3)!!.toboolean())
        assertTrue(result.arg(4)!!.tojstring().contains("boom-after-yield"))
        assertEquals("dead", result.arg(5)!!.checkjstring())
    }

    @Test
    fun statusTransitionsThroughRunningSuspendedNormalAndDead() {
        val result = run(
            """
            local statuses = {}
            local thread
            thread = coroutine.create(function()
                statuses[#statuses + 1] = coroutine.status(thread) -- running
                coroutine.yield()
                statuses[#statuses + 1] = coroutine.status(thread) -- running (resumed)
            end)
            statuses[#statuses + 1] = coroutine.status(thread) -- suspended (not started)
            coroutine.resume(thread)
            statuses[#statuses + 1] = coroutine.status(thread) -- suspended (yielded)
            coroutine.resume(thread)
            statuses[#statuses + 1] = coroutine.status(thread) -- dead
            return table.concat(statuses, ',')
            """,
        )
        assertEquals("suspended,running,suspended,running,dead", result.arg1()!!.tojstring())
    }

    @Test
    fun resumerShowsNormalStatusWhileItsCoroutineIsRunning() {
        val result = run(
            """
            local outer
            local inner = coroutine.create(function()
                return coroutine.status(outer)
            end)
            outer = coroutine.create(function()
                local ok, outerStatusFromInner = coroutine.resume(inner)
                return outerStatusFromInner
            end)
            local ok, result = coroutine.resume(outer)
            return result
            """,
        )
        assertEquals("normal", result.arg1()!!.tojstring())
    }

    @Test
    fun coroutineWrapReturnsValuesDirectlyAndPropagatesErrors() {
        val result = run(
            """
            local gen = coroutine.wrap(function()
                coroutine.yield(1)
                coroutine.yield(2)
                error('wrapped-error')
            end)
            local a = gen()
            local b = gen()
            local ok, err = pcall(gen)
            return a, b, ok, err
            """,
        )
        assertEquals(1, result.arg(1)!!.checkint())
        assertEquals(2, result.arg(2)!!.checkint())
        assertFalse(result.arg(3)!!.toboolean())
        assertTrue(result.arg(4)!!.tojstring().contains("wrapped-error"))
    }

    @Test
    fun yieldPropagatesThroughPcall() {
        // Real Lua 5.2 lets a coroutine yield across a pcall boundary, unlike
        // most other C-call boundaries (e.g. table.sort's comparator).
        val result = run(
            """
            local thread = coroutine.create(function()
                local ok, y = pcall(function()
                    return coroutine.yield('from-inside-pcall')
                end)
                return ok, y
            end)
            local ok1, v1 = coroutine.resume(thread)
            local ok2, v2, v3 = coroutine.resume(thread, 'resume-value')
            return v1, v2, v3
            """,
        )
        assertEquals("from-inside-pcall", result.arg(1)!!.tojstring())
        assertTrue(result.arg(2)!!.toboolean())
        assertEquals("resume-value", result.arg(3)!!.tojstring())
    }

    @Test
    fun yieldFromALibraryFunctionCallbackHitsTheCCallBoundary() {
        // table.sort's comparator is a genuine C-call boundary in real Lua
        // too: yielding through it is not supported.
        val result = run(
            """
            local thread = coroutine.create(function()
                local t = {3, 1, 2}
                table.sort(t, function(a, b)
                    coroutine.yield()
                    return a < b
                end)
                return 'unreachable'
            end)
            return coroutine.resume(thread)
            """,
        )
        assertFalse(result.arg(1)!!.toboolean())
        assertTrue(result.arg(2)!!.tojstring().contains("yield"))
    }

    @Test
    fun cannotResumeADeadCoroutine() {
        val result = run(
            """
            local thread = coroutine.create(function() return 1 end)
            coroutine.resume(thread)
            local ok, err = coroutine.resume(thread)
            return ok, err, coroutine.status(thread)
            """,
        )
        assertFalse(result.arg(1)!!.toboolean())
        assertTrue(result.arg(2)!!.tojstring().contains("dead"))
        assertEquals("dead", result.arg(3)!!.checkjstring())
    }

    @Test
    fun cannotResumeARunningOrNormalCoroutine() {
        val result = run(
            """
            local thread
            thread = coroutine.create(function()
                return coroutine.resume(thread)
            end)
            local ok, innerOk, innerErr = coroutine.resume(thread)
            return innerOk, innerErr
            """,
        )
        assertFalse(result.arg(1)!!.toboolean())
        assertTrue(result.arg(2)!!.tojstring().contains("non-suspended"))
    }
}
