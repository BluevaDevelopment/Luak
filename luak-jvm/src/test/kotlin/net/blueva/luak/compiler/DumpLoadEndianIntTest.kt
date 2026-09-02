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

import net.blueva.luak.lib.jvm.asLuaReader
import junit.framework.TestCase
import net.blueva.luak.Globals
import net.blueva.luak.LuaClosure
import net.blueva.luak.LuaFunction
import net.blueva.luak.lib.jvm.JvmPlatform.standardGlobals
import java.io.*

class DumpLoadEndianIntTest : TestCase() {
    private var globals: Globals? = null

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        globals = standardGlobals()
        DumpState.ALLOW_INTEGER_CASTING = false
    }

    fun testBigDoubleCompile() {
        doTest(
            false,
            DumpState.NUMBER_FORMAT_FLOATS_OR_DOUBLES,
            false,
            mixedscript,
            withdoubles,
            withdoubles,
            SHOULDPASS
        )
        doTest(
            false,
            DumpState.NUMBER_FORMAT_FLOATS_OR_DOUBLES,
            true,
            mixedscript,
            withdoubles,
            withdoubles,
            SHOULDPASS
        )
    }

    fun testLittleDoubleCompile() {
        doTest(
            true,
            DumpState.NUMBER_FORMAT_FLOATS_OR_DOUBLES,
            false,
            mixedscript,
            withdoubles,
            withdoubles,
            SHOULDPASS
        )
        doTest(true, DumpState.NUMBER_FORMAT_FLOATS_OR_DOUBLES, true, mixedscript, withdoubles, withdoubles, SHOULDPASS)
    }

    fun testBigIntCompile() {
        DumpState.ALLOW_INTEGER_CASTING = true
        doTest(false, DumpState.NUMBER_FORMAT_INTS_ONLY, false, mixedscript, withdoubles, withints, SHOULDPASS)
        doTest(false, DumpState.NUMBER_FORMAT_INTS_ONLY, true, mixedscript, withdoubles, withints, SHOULDPASS)
        DumpState.ALLOW_INTEGER_CASTING = false
        doTest(false, DumpState.NUMBER_FORMAT_INTS_ONLY, false, mixedscript, withdoubles, withints, SHOULDFAIL)
        doTest(false, DumpState.NUMBER_FORMAT_INTS_ONLY, true, mixedscript, withdoubles, withints, SHOULDFAIL)
        doTest(false, DumpState.NUMBER_FORMAT_INTS_ONLY, false, intscript, withints, withints, SHOULDPASS)
        doTest(false, DumpState.NUMBER_FORMAT_INTS_ONLY, true, intscript, withints, withints, SHOULDPASS)
    }

    fun testLittleIntCompile() {
        DumpState.ALLOW_INTEGER_CASTING = true
        doTest(true, DumpState.NUMBER_FORMAT_INTS_ONLY, false, mixedscript, withdoubles, withints, SHOULDPASS)
        doTest(true, DumpState.NUMBER_FORMAT_INTS_ONLY, true, mixedscript, withdoubles, withints, SHOULDPASS)
        DumpState.ALLOW_INTEGER_CASTING = false
        doTest(true, DumpState.NUMBER_FORMAT_INTS_ONLY, false, mixedscript, withdoubles, withints, SHOULDFAIL)
        doTest(true, DumpState.NUMBER_FORMAT_INTS_ONLY, true, mixedscript, withdoubles, withints, SHOULDFAIL)
        doTest(true, DumpState.NUMBER_FORMAT_INTS_ONLY, false, intscript, withints, withints, SHOULDPASS)
        doTest(true, DumpState.NUMBER_FORMAT_INTS_ONLY, true, intscript, withints, withints, SHOULDPASS)
    }

    fun testBigNumpatchCompile() {
        doTest(false, DumpState.NUMBER_FORMAT_NUM_PATCH_INT32, false, mixedscript, withdoubles, withdoubles, SHOULDPASS)
        doTest(false, DumpState.NUMBER_FORMAT_NUM_PATCH_INT32, true, mixedscript, withdoubles, withdoubles, SHOULDPASS)
    }

    fun testLittleNumpatchCompile() {
        doTest(true, DumpState.NUMBER_FORMAT_NUM_PATCH_INT32, false, mixedscript, withdoubles, withdoubles, SHOULDPASS)
        doTest(true, DumpState.NUMBER_FORMAT_NUM_PATCH_INT32, true, mixedscript, withdoubles, withdoubles, SHOULDPASS)
    }

    fun doTest(
        littleEndian: Boolean, numberFormat: Int, stripDebug: Boolean,
        script: String, expectedPriorDump: String?, expectedPostDump: String?, shouldPass: Boolean
    ) {
        try {
            // compile into prototype

            val reader: Reader = StringReader(script)
            val p = globals!!.compilePrototype(reader.asLuaReader(), "script")


            // double check script result before dumping
            var f: LuaFunction = LuaClosure(p!!, globals)
            var r = f.call()
            var actual: String? = r!!.tojstring()
            TestCase.assertEquals(expectedPriorDump, actual)


            // dump into bytes
            val baos = ByteArrayOutputStream()
            try {
                DumpState.dump(p, baos, stripDebug, numberFormat, littleEndian)
                if (!shouldPass) fail("dump should not have succeeded")
            } catch (e: Exception) {
                if (shouldPass) fail("dump threw " + e)
                else return
            }
            val dumped = baos.toByteArray()


            // load again using compiler
            val `is`: InputStream = ByteArrayInputStream(dumped)
            f = globals!!.load(`is`, "dumped", "b", globals)!!.checkfunction()!!
            r = f.call()
            actual = r!!.tojstring()
            TestCase.assertEquals(expectedPostDump, actual)

            // write test chunk
            if (System.getProperty(SAVECHUNKS) != null && script == mixedscript) {
                File("build").mkdirs()
                val filename = ("build/test-"
                        + (if (littleEndian) "little-" else "big-")
                        + (if (numberFormat == DumpState.NUMBER_FORMAT_FLOATS_OR_DOUBLES) "double-" else if (numberFormat == DumpState.NUMBER_FORMAT_INTS_ONLY) "int-" else if (numberFormat == DumpState.NUMBER_FORMAT_NUM_PATCH_INT32) "numpatch4-" else "???-")
                        + (if (stripDebug) "nodebug-" else "debug-")
                        + "bin.lua")
                val fos = FileOutputStream(filename)
                fos.write(dumped)
                fos.close()
            }
        } catch (e: IOException) {
            fail(e.toString())
        }
    }

    companion object {
        private const val SAVECHUNKS = "SAVECHUNKS"

        private const val SHOULDPASS = true
        private const val SHOULDFAIL = false
        private const val mixedscript = "return tostring(1234)..'-#!-'..tostring(23.75)"
        private const val intscript = "return tostring(1234)..'-#!-'..tostring(23)"
        private const val withdoubles = "1234-#!-23.75"
        private const val withints = "1234-#!-23"
    }
}
