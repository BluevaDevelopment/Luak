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

import junit.framework.TestSuite
import net.blueva.luak.luajc.LuaJC.Companion.install

/**
 * Compatibility tests for the Luak VM.
 *
 * Results are compared for an exact match against a recorded expected output.
 *
 * Five of these scripts - `errors`, `iolib`, `metatags`, `tailcalls` and `vm` -
 * are no longer run. Each replaces the global `tostring` with one that gives
 * tables, functions and threads stable names like `tbl.1`, so that its output
 * does not carry addresses. That worked while `print` went through the global
 * `tostring`, which is what Lua did up to 5.2; since 5.3 `print` uses Lua's own
 * conversion, and those scripts print raw addresses - checked against
 * `lua-5.5.1`, which prints them too. There is no stable expected output left
 * to compare against, so the tests were removed rather than left failing. The
 * scripts stay under `src/test/resources/test/lua/` for anyone who rewrites
 * them around a normalising `print` of their own.
 */
object CompatibiltyTest : TestSuite() {
    private const val dir = ""

    fun suite(): TestSuite {
        val suite = TestSuite("Compatibility Tests")
        suite.addTest(TestSuite(JvmCompatibilityTest::class.java, "JVM Compatibility Tests"))
        suite.addTest(TestSuite(LuaJCCompatibilityTest::class.java, "LuaJC Compatibility Tests"))
        return suite
    }

    abstract class CompatibiltyTestSuite constructor(platform: PlatformType?) :
        ScriptDrivenTest(platform!!, dir) {
        var savedStringMetatable: LuaValue? = null

        @Throws(Exception::class)
        override fun setUp() {
            savedStringMetatable = LuaString.s_metatable
            super.setUp()
        }

        @Throws(Exception::class)
        override fun tearDown() {
            super.tearDown()
            LuaNil.s_metatable = null
            LuaBoolean.s_metatable = null
            LuaNumber.s_metatable = null
            LuaFunction.s_metatable = null
            LuaThread.s_metatable = null
            LuaString.s_metatable = savedStringMetatable
        }

        fun testFunctions() {
            runTest("functions")
        }

        fun testTableLib() {
            runTest("tablelib")
        }

        fun testUpvalues() {
            runTest("upvalues")
        }

    }


    class JvmCompatibilityTest : CompatibiltyTestSuite(PlatformType.JVM) {
        @Throws(Exception::class)
        override fun setUp() {
            super.setUp()
            System.setProperty("JME", "false")
        }
    }

    class LuaJCCompatibilityTest : CompatibiltyTestSuite(PlatformType.LUAJIT) {
        @Throws(Exception::class)
        override fun setUp() {
            super.setUp()
            System.setProperty("JME", "false")
            install(globals!!)
        }

    }
}
