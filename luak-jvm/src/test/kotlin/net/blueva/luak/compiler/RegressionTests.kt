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
package net.blueva.luak.compiler

/**
 * Framework to add regression tests as problem areas are found.
 * 
 * To add a new regression test:
 * 1) run "unpack.sh" in the project root
 * 2) add a new "lua" file in the "regressions" subdirectory
 * 3) run "repack.sh" in the project root
 * 4) add a line to the source file naming the new test
 * 
 * After adding a test, check in the zip file
 * rather than the individual regression test files.
 * 
 * @author jrosebor
 */
class RegressionTests : AbstractUnitTests("test/lua", "luaj3.0-tests.zip", "regressions") {
    fun testModulo() {
        doTest("modulo.lua")
    }

    fun testConstruct() {
        doTest("construct.lua")
    }

    fun testBigAttrs() {
        doTest("bigattr.lua")
    }

    fun testControlChars() {
        doTest("controlchars.lua")
    }

    fun testComparators() {
        doTest("comparators.lua")
    }

    fun testMathRandomseed() {
        doTest("mathrandomseed.lua")
    }

    fun testVarargs() {
        doTest("varargs.lua")
    }
}
