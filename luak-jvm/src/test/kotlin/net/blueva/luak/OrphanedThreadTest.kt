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
 *  Based on LuaJ (https://luaj.org)
 *  Original work Copyright (c) 2009 Luaj.org
 *  Modifications Copyright (c) 2026 Blueva Development
 *
 *  SPDX-License-Identifier: MIT
 ******************************************************************************/
package net.blueva.luak

import junit.framework.TestCase
import net.blueva.luak.lib.jvm.JvmPlatform.standardGlobals
import java.lang.ref.WeakReference

class OrphanedThreadTest : TestCase() {
    var globals: Globals? = null
    var luathread: LuaThread? = null
    var luathr_ref: WeakReference<*>? = null
    var function: LuaValue? = null
    var func_ref: WeakReference<*>? = null

    @Throws(Exception::class)
    override fun setUp() {
        LuaThread.thread_orphan_check_interval = 5
        globals = standardGlobals()
    }

    override fun tearDown() {
        LuaThread.thread_orphan_check_interval = 30000
    }

    // These three used to be hand-written Kotlin functions that called
    // globals.yield() directly from a regular (non-suspend) call() override -
    // relying on "yield from literally anywhere", a capability that only
    // exists with real OS threads backing each coroutine. Real Lua doesn't
    // support that either (arbitrary C functions can't be yielded through),
    // so now that coroutines are suspend-based instead of thread-based, they
    // are expressed as plain Lua scripts, which is exactly what real Lua
    // coroutines support natively.
    @Throws(Exception::class)
    fun testCollectOrphanedNormalThread() {
        val script =
            "print('in normal.1, arg is', ...)\n" +
                    "local arg = coroutine.yield(1)\n" +
                    "print('in normal.2, arg is', arg)\n" +
                    "arg = coroutine.yield(0)\n" +
                    "print('leakage in normal.3, arg is', arg)\n"
        function = globals!!.load(script, "script")
        doTest(LuaValue.TRUE, LuaValue.ZERO)
    }

    @Throws(Exception::class)
    fun testCollectOrphanedEarlyCompletionThread() {
        val script =
            "print('in early.1, arg is', ...)\n" +
                    "local arg = coroutine.yield(1)\n" +
                    "print('in early.2, arg is', arg)\n" +
                    "return 0\n"
        function = globals!!.load(script, "script")
        doTest(LuaValue.TRUE, LuaValue.ZERO)
    }

    @Throws(Exception::class)
    fun testCollectOrphanedAbnormalThread() {
        val script =
            "print('in abnormal.1, arg is', ...)\n" +
                    "local arg = coroutine.yield(1)\n" +
                    "print('in abnormal.2, arg is', arg)\n" +
                    "error('abnormal condition', 0)\n"
        function = globals!!.load(script, "script")
        // Level 0 asks for the message exactly as written, so no position is
        // added to it.
        doTest(LuaValue.FALSE, LuaValue.valueOf("abnormal condition"))
    }

    @Throws(Exception::class)
    fun testCollectOrphanedClosureThread() {
        val script =
            "print('in closure, arg is '..(...))\n" +
                    "arg = coroutine.yield(1)\n" +
                    "print('in closure.2, arg is '..arg)\n" +
                    "arg = coroutine.yield(0)\n" +
                    "print('leakage in closure.3, arg is '..arg)\n" +
                    "return 'done'\n"
        function = globals!!.load(script, "script")
        doTest(LuaValue.TRUE, LuaValue.ZERO)
    }

    @Throws(Exception::class)
    fun testCollectOrphanedPcallClosureThread() {
        val script =
            "f = function(x)\n" +
                    "  print('in pcall-closure, arg is '..(x))\n" +
                    "  arg = coroutine.yield(1)\n" +
                    "  print('in pcall-closure.2, arg is '..arg)\n" +
                    "  arg = coroutine.yield(0)\n" +
                    "  print('leakage in pcall-closure.3, arg is '..arg)\n" +
                    "  return 'done'\n" +
                    "end\n" +
                    "print( 'pcall-closre.result:', pcall( f, ... ) )\n"
        function = globals!!.load(script, "script")
        doTest(LuaValue.TRUE, LuaValue.ZERO)
    }

    // Yielding from *inside* load()'s reader callback itself doesn't work
    // (matches real Lua: that reader is a C-call boundary too), so this
    // exercises load() producing a closure and yielding from that closure's
    // own execution instead, which is the supported, real-Lua-equivalent
    // pattern.
    @Throws(Exception::class)
    fun testCollectOrphanedLoadCloasureThread() {
        val script =
            "local t = { \"return coroutine.yield(1)\" }\n" +
                    "local i = 0\n" +
                    "local function reader()\n" +
                    "  i = i + 1\n" +
                    "  return t[i]\n" +
                    "end\n" +
                    "local loaded = load(reader)\n" +
                    "print('in load-closure, arg is', ...)\n" +
                    "local result = loaded()\n" +
                    "print('in load-closure.2, result is', result)\n" +
                    "return 1\n"
        function = globals!!.load(script, "script")
        doTest(LuaValue.TRUE, LuaValue.ONE)
    }

    @Throws(Exception::class)
    private fun doTest(status2: LuaValue?, value2: LuaValue?) {
        luathread = LuaThread(globals!!, function)
        luathr_ref = WeakReference<Any?>(luathread)
        func_ref = WeakReference<Any?>(function)
        assertNotNull(luathr_ref!!.get())


        // resume two times
        var a = luathread!!.resume(LuaValue.valueOf("foo"))
        assertEquals(LuaValue.ONE, a.arg(2))
        assertEquals(LuaValue.TRUE, a.arg1())
        a = luathread!!.resume(LuaValue.valueOf("bar"))
        assertEquals(value2, a.arg(2))
        assertEquals(status2, a.arg1())


        // drop strong references
        luathread = null
        function = null


        // gc
        var i = 0
        while (i < 100 && (luathr_ref!!.get() != null || func_ref!!.get() != null)) {
            Runtime.getRuntime().gc()
            Thread.sleep(5)
            i++
        }


        // check reference
        assertNull(luathr_ref!!.get())
        assertNull(func_ref!!.get())
    }
}
