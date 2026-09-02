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

/**
 * Test argument type check errors
 * 
 * Results are compared for exact match with
 * the installed C-based lua environment.
 */
class ErrorsTest : ScriptDrivenTest(PlatformType.JVM, dir) {
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
    }

    fun testCoroutineLibArgs() {
        runTest("coroutinelibargs")
    }

    fun testModuleLibArgs() {
        runTest("modulelibargs")
    }

    fun testOperators() {
        runTest("operators")
    }

    fun testTableLibArgs() {
        runTest("tablelibargs")
    }

    companion object {
        private const val dir = "errors/"
    }
}
