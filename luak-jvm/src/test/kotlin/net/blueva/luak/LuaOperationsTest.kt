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

import net.blueva.luak.lib.jvm.asLuaReader
import junit.framework.TestCase
import net.blueva.luak.lib.ZeroArgFunction
import net.blueva.luak.lib.jvm.JvmPlatform.standardGlobals
import java.io.Reader
import java.io.StringReader
import java.lang.reflect.InvocationTargetException

class LuaOperationsTest : TestCase() {
    private val sampleint = 77
    private val samplelong = 123400000000L
    private val sampledouble = 55.25
    private val samplestringstring = "abcdef"
    private val samplestringint = sampleint.toString()
    private val samplestringlong = samplelong.toString()
    private val samplestringdouble = sampledouble.toString()
    private val sampleobject = Any()
    private val sampledata = TypeTest.MyData()

    private val somenil: LuaValue? = LuaValue.NIL
    private val sometrue: LuaValue? = LuaValue.TRUE
    private val somefalse: LuaValue? = LuaValue.FALSE
    private val zero: LuaValue? = LuaValue.ZERO
    private val intint: LuaValue? = LuaValue.valueOf(sampleint)
    private val longdouble: LuaValue? = LuaValue.valueOf(samplelong.toDouble())
    private val doubledouble: LuaValue? = LuaValue.valueOf(sampledouble)
    private val stringstring: LuaValue = LuaValue.valueOf(samplestringstring)
    private val stringint: LuaValue = LuaValue.valueOf(samplestringint)
    private val stringlong: LuaValue = LuaValue.valueOf(samplestringlong)
    private val stringdouble: LuaValue = LuaValue.valueOf(samplestringdouble)
    private val table = LuaValue.listOf(arrayOf<LuaValue>(LuaValue.valueOf("aaa"), LuaValue.valueOf("bbb")))
    private val somefunc: LuaValue = object : ZeroArgFunction() {
        override fun call(): LuaValue? {
            return NONE
        }
    }
    private val thread = LuaThread(Globals(), somefunc)
    private val proto = Prototype(1)
    private val someclosure = LuaClosure(proto, table)
    private val userdataobj = LuaValue.userdataOf(sampleobject)
    private val userdatacls = LuaValue.userdataOf(sampledata)

    private fun throwsLuaError(methodName: String, obj: Any?) {
        try {
            LuaValue::class.java.getMethod(methodName).invoke(obj)
            fail("failed to throw LuaError as required")
        } catch (e: InvocationTargetException) {
            if (e.getTargetException() !is LuaError) fail("not a LuaError: " + e.getTargetException())
            return  // pass
        } catch (e: Exception) {
            fail("bad exception: " + e)
        }
    }

    private fun throwsLuaError(methodName: String, obj: Any?, arg: Any?) {
        try {
            LuaValue::class.java.getMethod(methodName, LuaValue::class.java).invoke(obj, arg)
            fail("failed to throw LuaError as required")
        } catch (e: InvocationTargetException) {
            if (e.getTargetException() !is LuaError) fail("not a LuaError: " + e.getTargetException())
            return  // pass
        } catch (e: Exception) {
            fail("bad exception: " + e)
        }
    }

    fun testLen() {
        throwsLuaError("len", somenil)
        throwsLuaError("len", sometrue)
        throwsLuaError("len", somefalse)
        throwsLuaError("len", zero)
        throwsLuaError("len", intint)
        throwsLuaError("len", longdouble)
        throwsLuaError("len", doubledouble)
        assertEquals(LuaInteger.valueOf(samplestringstring.length), stringstring.len())
        assertEquals(LuaInteger.valueOf(samplestringint.length), stringint.len())
        assertEquals(LuaInteger.valueOf(samplestringlong.length), stringlong.len())
        assertEquals(LuaInteger.valueOf(samplestringdouble.length), stringdouble.len())
        assertEquals(LuaInteger.valueOf(2), table.len())
        throwsLuaError("len", somefunc)
        throwsLuaError("len", thread)
        throwsLuaError("len", someclosure)
        throwsLuaError("len", userdataobj)
        throwsLuaError("len", userdatacls)
    }

    fun testLength() {
        throwsLuaError("length", somenil)
        throwsLuaError("length", sometrue)
        throwsLuaError("length", somefalse)
        throwsLuaError("length", zero)
        throwsLuaError("length", intint)
        throwsLuaError("length", longdouble)
        throwsLuaError("length", doubledouble)
        TestCase.assertEquals(samplestringstring.length, stringstring.length())
        TestCase.assertEquals(samplestringint.length, stringint.length())
        TestCase.assertEquals(samplestringlong.length, stringlong.length())
        TestCase.assertEquals(samplestringdouble.length, stringdouble.length())
        TestCase.assertEquals(2, table.length())
        throwsLuaError("length", somefunc)
        throwsLuaError("length", thread)
        throwsLuaError("length", someclosure)
        throwsLuaError("length", userdataobj)
        throwsLuaError("length", userdatacls)
    }

    fun createPrototype(script: String, name: String?): Prototype? {
        try {
            val globals = standardGlobals()
            val reader: Reader = StringReader(script)
            return globals.compilePrototype(reader.asLuaReader(), name)
        } catch (e: Exception) {
            // TODO Auto-generated catch block
            e.printStackTrace()
            fail(e.toString())
            return null
        }
    }

    fun testFunctionClosureThreadEnv() {
        // set up suitable environments for execution

        val aaa: LuaValue = LuaValue.valueOf("aaa")
        val eee: LuaValue = LuaValue.valueOf("eee")
        val globals = standardGlobals()
        val newenv = LuaValue.tableOf(
            arrayOf<LuaValue>(
                LuaValue.valueOf("a"), LuaValue.valueOf("aaa"),
                LuaValue.valueOf("b"), LuaValue.valueOf("bbb"),
            )
        )
        val mt = LuaValue.tableOf(arrayOf<LuaValue>(LuaValue.INDEX, globals))
        newenv.setmetatable(mt)
        globals.set("a", aaa)
        newenv.set("a", eee)

        // function tests
        run {
            val f: LuaFunction = object : ZeroArgFunction() {
                override fun call(): LuaValue? {
                    return globals.get("a")
                }
            }
            assertEquals(aaa, f.call())
        }


        // closure tests
        run {
            val p = createPrototype("return a\n", "closuretester")
            var c = LuaClosure(p!!, globals)


            // Test that a clusure with a custom enviroment uses that environment.
            assertEquals(aaa, c.call())
            c = LuaClosure(p, newenv)
            assertEquals(newenv, c.upValues[0]!!.getValue())
            assertEquals(eee, c.call())
        }
    }
}
