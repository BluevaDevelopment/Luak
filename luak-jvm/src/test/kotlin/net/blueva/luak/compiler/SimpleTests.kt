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

import junit.framework.TestCase
import net.blueva.luak.Globals
import net.blueva.luak.LuaDouble
import net.blueva.luak.LuaInteger
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaValue
import net.blueva.luak.lib.jvm.JvmPlatform.standardGlobals

class SimpleTests : TestCase() {
    private var globals: Globals? = null

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        globals = standardGlobals()
    }

    private fun doTest(script: String?) {
        try {
            val c: LuaValue = globals!!.load(script!!, "script")!!
            c.call()
        } catch (e: Exception) {
            fail("i/o exception: " + e)
        }
    }

    fun testTrivial() {
        val s = "print( 2 )\n"
        doTest(s)
    }

    fun testAlmostTrivial() {
        val s = "print( 2 )\n" +
                "print( 3 )\n"
        doTest(s)
    }

    fun testSimple() {
        val s = "print( 'hello, world' )\n" +
                "for i = 2,4 do\n" +
                "	print( 'i', i )\n" +
                "end\n"
        doTest(s)
    }

    fun testBreak() {
        val s = "a=1\n" +
                "while true do\n" +
                "  if a>10 then\n" +
                "     break\n" +
                "  end\n" +
                "  a=a+1\n" +
                "  print( a )\n" +
                "end\n"
        doTest(s)
    }

    fun testShebang() {
        // A '#!' line is stripped while Lua reads a *file*, so the chunk has to
        // be named as one; `load` of the same text is an ordinary chunk that
        // starts with the length operator and does not compile.
        val s = "#!../lua\n" +
                "print( 2 )\n"
        try {
            globals!!.load(s, "@script")!!.call()
        } catch (e: Exception) {
            fail("i/o exception: " + e)
        }
    }

    fun testInlineTable() {
        val s = "A = {g=10}\n" +
                "print( A )\n"
        doTest(s)
    }

    fun testEqualsAnd() {
        val s = "print( 1 == b and b )\n"
        doTest(s)
    }

    /**
     * An integer and a float of the same value hash apart, and index alike.
     *
     * They used to be the same object, so equal hash codes were unavoidable.
     * Now they are distinct values and the compiler's constant pool relies on
     * telling them apart, so their hash codes are free to differ - what still
     * has to hold is the Lua-level rule that `t[2]` and `t[2.0]` are one key.
     */
    fun testIntegerAndFloatKeysAgree() {
        for (i in samehash.indices) {
            val integer: LuaValue = LuaInteger.valueOf(samehash[i])!!
            val float: LuaValue = LuaDouble.valueOf(samehash[i].toDouble())!!
            TestCase.assertFalse("subtypes must stay apart", integer == float)

            val table = LuaTable()
            table.set(integer, LuaValue.valueOf("by integer"))
            TestCase.assertEquals("by integer", table.get(float).tojstring())
            table.set(float, LuaValue.valueOf("by float"))
            TestCase.assertEquals("by float", table.get(integer).tojstring())
            TestCase.assertEquals(1, table.keys().size)
        }
        var i = 0
        while (i < diffhash.size) {
            val c: LuaValue = LuaValue.valueOf(diffhash[i + 0])
            val d: LuaValue = LuaValue.valueOf(diffhash[i + 1])
            val hc = c.hashCode()
            val hd = d.hashCode()
            assertTrue("hash codes are same: " + hc, hc != hd)
            i += 2
        }
    }


    companion object {
        private val samehash = intArrayOf(0, 1, -1, 2, -2, 4, 8, 16, 32, Int.MAX_VALUE, Int.MIN_VALUE)
        private val diffhash = doubleArrayOf(.5, 1.0, 1.5, 1.0, .5, 1.5, 1.25, 2.5)
    }
}
