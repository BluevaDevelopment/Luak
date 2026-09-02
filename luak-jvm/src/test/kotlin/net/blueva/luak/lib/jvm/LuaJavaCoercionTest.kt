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
package net.blueva.luak.lib.jvm

import junit.framework.TestCase
import net.blueva.luak.*
import net.blueva.luak.lib.jvm.CoerceJavaToLua.coerce
import net.blueva.luak.lib.jvm.CoerceLuaToJava.coerce
import net.blueva.luak.lib.jvm.CoerceLuaToJava.getCoercion
import net.blueva.luak.lib.jvm.CoerceLuaToJava.inheritanceLevels
import net.blueva.luak.lib.jvm.JvmPlatform.standardGlobals

class LuaJavaCoercionTest : TestCase() {
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        globals = standardGlobals()
    }

    fun testJavaIntToLuaInt() {
        val i = 777
        val v = coerce(i)
        assertEquals(LuaInteger::class.java, v!!.javaClass)
        TestCase.assertEquals(777, v.toint())
    }

    fun testLuaIntToJavaInt() {
        val i = LuaInteger.valueOf(777)
        var o = CoerceLuaToJava.coerce(i, Int::class.javaPrimitiveType!!)
        assertEquals(Int::class.javaObjectType, o!!.javaClass)
        TestCase.assertEquals(777, (o as Number).toInt())
        o = coerce(i, Int::class.javaObjectType)
        assertEquals(Int::class.javaObjectType, o!!.javaClass)
        assertEquals(777, o)
    }

    fun testJavaStringToLuaString() {
        val s = "777"
        val v = coerce(s)
        assertEquals(LuaString::class.java, v!!.javaClass)
        TestCase.assertEquals("777", v.toString())
    }

    fun testLuaStringToJavaString() {
        val s = LuaValue.valueOf("777")
        val o = coerce(s, String::class.java)
        assertEquals(String::class.java, o!!.javaClass)
        assertEquals("777", o)
    }

    fun testJavaClassToLuaUserdata() {
        val va = coerce(ClassA::class.java)
        val va1 = coerce(ClassA::class.java)
        val vb = coerce(ClassB::class.java)
        assertSame(va, va1)
        assertNotSame(va, vb)
        val vi = coerce(ClassA())
        assertNotSame(va, vi)
        assertTrue(vi!!.isuserdata())
        assertTrue(vi.isuserdata(ClassA::class))
        assertFalse(vi.isuserdata(ClassB::class))
        val vj = coerce(ClassB())
        assertNotSame(vb, vj)
        assertTrue(vj!!.isuserdata())
        assertFalse(vj.isuserdata(ClassA::class))
        assertTrue(vj.isuserdata(ClassB::class))
    }

    internal class ClassA

    internal class ClassB

    fun testJavaIntArrayToLuaTable() {
        val i = intArrayOf(222, 333)
        val v = coerce(i)
        assertEquals(JavaArray::class.java, v!!.javaClass)
        assertEquals(LuaInteger.valueOf(222), v.get(ONE!!))
        assertEquals(LuaInteger.valueOf(333), v.get(TWO!!))
        assertEquals(TWO, v.get(LENGTH))
        assertEquals(LuaValue.NIL, v.get(THREE!!))
        assertEquals(LuaValue.NIL, v.get(ZERO!!))
        v.set(ONE, LuaInteger.valueOf(444))
        v.set(TWO, LuaInteger.valueOf(555))
        TestCase.assertEquals(444, i[0])
        TestCase.assertEquals(555, i[1])
        assertEquals(LuaInteger.valueOf(444), v.get(ONE))
        assertEquals(LuaInteger.valueOf(555), v.get(TWO))
        try {
            v.set(ZERO, LuaInteger.valueOf(777))
            fail("array bound exception not thrown")
        } catch (lee: LuaError) {
            // expected
        }
        try {
            v.set(THREE, LuaInteger.valueOf(777))
            fail("array bound exception not thrown")
        } catch (lee: LuaError) {
            // expected
        }
    }

    fun testLuaTableToJavaIntArray() {
        val t = LuaTable()
        t.set(1, LuaInteger.valueOf(222))
        t.set(2, LuaInteger.valueOf(333))
        var i: IntArray? = null
        val o = coerce(t, IntArray::class.java)
        assertEquals(IntArray::class.java, o!!.javaClass)
        i = o as IntArray
        TestCase.assertEquals(2, i.size)
        TestCase.assertEquals(222, i[0])
        TestCase.assertEquals(333, i[1])
    }

    fun testIntArrayScoringTables() {
        val a = 5
        val la: LuaValue = LuaInteger.valueOf(a)!!
        val tb = LuaTable()
        tb.set(1, la)
        val tc = LuaTable()
        tc.set(1, tb)

        val saa = CoerceLuaToJava.getCoercion(Int::class.javaPrimitiveType!!).score(la)
        val sab = getCoercion(IntArray::class.java).score(la)
        val sac = getCoercion(Array<IntArray>::class.java).score(la)
        assertTrue(saa < sab)
        assertTrue(saa < sac)
        val sba = CoerceLuaToJava.getCoercion(Int::class.javaPrimitiveType!!).score(tb)
        val sbb = getCoercion(IntArray::class.java).score(tb)
        val sbc = getCoercion(Array<IntArray>::class.java).score(tb)
        assertTrue(sbb < sba)
        assertTrue(sbb < sbc)
        val sca = CoerceLuaToJava.getCoercion(Int::class.javaPrimitiveType!!).score(tc)
        val scb = getCoercion(IntArray::class.java).score(tc)
        val scc = getCoercion(Array<IntArray>::class.java).score(tc)
        assertTrue(scc < sca)
        assertTrue(scc < scb)
    }

    fun testIntArrayScoringUserdata() {
        val a = 5
        val b = intArrayOf(44, 66)
        val c = arrayOf<IntArray?>(intArrayOf(11, 22), intArrayOf(33, 44))
        val va = coerce(a)
        val vb = coerce(b)
        val vc = coerce(c)

        val vaa = CoerceLuaToJava.getCoercion(Int::class.javaPrimitiveType!!).score(va)
        val vab = getCoercion(IntArray::class.java).score(va)
        val vac = getCoercion(Array<IntArray>::class.java).score(va)
        assertTrue(vaa < vab)
        assertTrue(vaa < vac)
        val vba = CoerceLuaToJava.getCoercion(Int::class.javaPrimitiveType!!).score(vb)
        val vbb = getCoercion(IntArray::class.java).score(vb)
        val vbc = getCoercion(Array<IntArray>::class.java).score(vb)
        assertTrue(vbb < vba)
        assertTrue(vbb < vbc)
        val vca = CoerceLuaToJava.getCoercion(Int::class.javaPrimitiveType!!).score(vc)
        val vcb = getCoercion(IntArray::class.java).score(vc)
        val vcc = getCoercion(Array<IntArray>::class.java).score(vc)
        assertTrue(vcc < vca)
        assertTrue(vcc < vcb)
    }

    class SampleClass {
        fun sample(): String {
            return "void-args"
        }

        fun sample(a: Int): String {
            return "int-args " + a
        }

        fun sample(a: IntArray): String {
            return "int-array-args " + a[0] + "," + a[1]
        }

        fun sample(a: Array<IntArray?>): String {
            return "int-array-array-args " + a[0]!![0] + "," + a[0]!![1] + "," + a[1]!![0] + "," + a[1]!![1]
        }
    }

    fun testMatchVoidArgs() {
        val v = coerce(SampleClass())
        val result = v!!.method("sample")
        TestCase.assertEquals("void-args", result.toString())
    }

    fun testMatchIntArgs() {
        val v = coerce(SampleClass())
        val arg = coerce(123)
        val result = v!!.method("sample", arg)
        TestCase.assertEquals("int-args 123", result.toString())
    }

    fun testMatchIntArrayArgs() {
        val v = coerce(SampleClass())
        val arg = coerce(intArrayOf(345, 678))
        val result = v!!.method("sample", arg)
        TestCase.assertEquals("int-array-args 345,678", result.toString())
    }

    fun testMatchIntArrayArrayArgs() {
        val v = coerce(SampleClass())
        val arg = coerce(arrayOf<IntArray?>(intArrayOf(22, 33), intArrayOf(44, 55)))
        val result = v!!.method("sample", arg)
        TestCase.assertEquals("int-array-array-args 22,33,44,55", result.toString())
    }

    class SomeException(message: String?) : RuntimeException(message)

    object SomeClass {
        @JvmStatic
        fun someMethod() {
            throw SomeException("this is some message")
        }
    }

    fun testExceptionMessage() {
        val script = "local c = luajava.bindClass( \"" + SomeClass::class.java.getName() + "\" )\n" +
                "return pcall( c.someMethod, c )"
        val vresult: Varargs = globals!!.get("load")!!.call(LuaValue.valueOf(script))!!
            .invoke(net.blueva.luak.LuaValue.NONE!!)!!
        val status = vresult.arg1()
        val message: LuaValue = vresult.arg(2)!!
        assertEquals(LuaValue.FALSE, status)
        val index = message.toString().indexOf("this is some message")
        assertTrue("bad message: " + message, index >= 0)
    }

    fun testLuaErrorCause() {
        val script = "luajava.bindClass( \"" + SomeClass::class.java.getName() + "\"):someMethod()"
        val chunk: LuaValue = globals!!.get("load")!!.call(LuaValue.valueOf(script))!!
        try {
            chunk.invoke(LuaValue.NONE!!)
            fail("call should not have succeeded")
        } catch (lee: LuaError) {
            val c: Throwable = lee.cause!!
            assertEquals(SomeException::class.java, c.javaClass)
        }
    }

    interface VarArgsInterface {
        fun varargsMethod(a: String?, vararg v: String?): String?
        fun arrayargsMethod(a: String?, v: Array<out String?>?): String?
    }

    fun testVarArgsProxy() {
        val script = "return luajava.createProxy( \"" + VarArgsInterface::class.java.getName() + "\", \n" +
                "{\n" +
                "	varargsMethod = function(a,...)\n" +
                "		return table.concat({a,...},'-')\n" +
                "	end,\n" +
                "	arrayargsMethod = function(a,array)\n" +
                "		return tostring(a)..(array and \n" +
                "			('-'..tostring(array.length)\n" +
                "			..'-'..tostring(array[1])\n" +
                "			..'-'..tostring(array[2])\n" +
                "			) or '-nil')\n" +
                "	end,\n" +
                "} )\n"
        val chunk: Varargs = globals!!.get("load")!!.call(LuaValue.valueOf(script))!!
        if (!chunk.arg1()!!.toboolean()) fail(chunk.arg(2).toString())
        val result = chunk.arg1()!!.call()
        val u: Any? = result!!.touserdata()
        val v = u as VarArgsInterface
        TestCase.assertEquals("foo", v.varargsMethod("foo"))
        TestCase.assertEquals("foo-bar", v.varargsMethod("foo", "bar"))
        TestCase.assertEquals("foo-bar-etc", v.varargsMethod("foo", "bar", "etc"))
        TestCase.assertEquals("foo-0-nil-nil", v.arrayargsMethod("foo", arrayOfNulls<String>(0)))
        TestCase.assertEquals("foo-1-bar-nil", v.arrayargsMethod("foo", arrayOf<String>("bar")))
        TestCase.assertEquals("foo-2-bar-etc", v.arrayargsMethod("foo", arrayOf<String>("bar", "etc")))
        TestCase.assertEquals("foo-3-bar-etc", v.arrayargsMethod("foo", arrayOf<String>("bar", "etc", "etc")))
        TestCase.assertEquals("foo-nil", v.arrayargsMethod("foo", null))
    }

    fun testBigNum() {
        val script =
            "bigNumA = luajava.newInstance('java.math.BigDecimal','12345678901234567890');\n" +
                    "bigNumB = luajava.newInstance('java.math.BigDecimal','12345678901234567890');\n" +
                    "bigNumC = bigNumA:multiply(bigNumB);\n" +  //"print(bigNumA:toString())\n" +
                    //"print(bigNumB:toString())\n" +
                    //"print(bigNumC:toString())\n" +
                    "return bigNumA:toString(), bigNumB:toString(), bigNumC:toString()"
        val chunk: Varargs = globals!!.get("load")!!.call(LuaValue.valueOf(script))!!
        if (!chunk.arg1()!!.toboolean()) fail(chunk.arg(2).toString())
        val results: Varargs = chunk.arg1()!!.invoke()!!
        val nresults = results.narg()
        val sa: String? = results.tojstring(1)
        val sb: String? = results.tojstring(2)
        val sc: String? = results.tojstring(3)
        TestCase.assertEquals(3, nresults)
        TestCase.assertEquals("12345678901234567890", sa)
        TestCase.assertEquals("12345678901234567890", sb)
        TestCase.assertEquals("152415787532388367501905199875019052100", sc)
    }

    interface IA
    interface IB : IA
    interface IC : IB

    open class A : IA
    open class B : A(), IB {
        fun set(x: Any?): String {
            return "set(Object) "
        }

        fun set(x: String): String {
            return "set(String) " + x
        }

        fun set(x: A?): String {
            return "set(A) "
        }

        fun set(x: B?): String {
            return "set(B) "
        }

        fun set(x: C?): String {
            return "set(C) "
        }

        fun set(x: Byte): String {
            return "set(byte) " + x
        }

        fun set(x: Char): String {
            return "set(char) " + x.code
        }

        fun set(x: Short): String {
            return "set(short) " + x
        }

        fun set(x: Int): String {
            return "set(int) " + x
        }

        fun set(x: Long): String {
            return "set(long) " + x
        }

        fun set(x: Float): String {
            return "set(float) " + x
        }

        fun set(x: Double): String {
            return "set(double) " + x
        }

        fun setr(x: Double): String {
            return "setr(double) " + x
        }

        fun setr(x: Float): String {
            return "setr(float) " + x
        }

        fun setr(x: Long): String {
            return "setr(long) " + x
        }

        fun setr(x: Int): String {
            return "setr(int) " + x
        }

        fun setr(x: Short): String {
            return "setr(short) " + x
        }

        fun setr(x: Char): String {
            return "setr(char) " + x.code
        }

        fun setr(x: Byte): String {
            return "setr(byte) " + x
        }

        fun setr(x: C?): String {
            return "setr(C) "
        }

        fun setr(x: B?): String {
            return "setr(B) "
        }

        fun setr(x: A?): String {
            return "setr(A) "
        }

        fun setr(x: String): String {
            return "setr(String) " + x
        }

        fun setr(x: Any?): String {
            return "setr(Object) "
        }

        val `object`: Any
            get() = Any()
        val string: String
            get() = "abc"

        fun getbytearray(): ByteArray? {
            return byteArrayOf(1, 2, 3)
        }

        val a: A
            get() = A()
        val b: B
            get() = B()
        val c: C
            get() = C()

        fun getbyte(): Byte {
            return 1
        }

        fun getchar(): Char {
            return 65000.toChar()
        }

        fun getshort(): Short {
            return -32000
        }

        fun getint(): Int {
            return 100000
        }

        fun getlong(): Long {
            return 50000000000L
        }

        fun getfloat(): Float {
            return 6.5f
        }

        fun getdouble(): Double {
            return Math.PI
        }
    }

    open class C : B(), IC
    class D : C(), IA

    fun testOverloadedJavaMethodObject() {
        doOverloadedMethodTest("Object", "")
    }

    fun testOverloadedJavaMethodString() {
        doOverloadedMethodTest("String", "abc")
    }

    fun testOverloadedJavaMethodA() {
        doOverloadedMethodTest("A", "")
    }

    fun testOverloadedJavaMethodB() {
        doOverloadedMethodTest("B", "")
    }

    fun testOverloadedJavaMethodC() {
        doOverloadedMethodTest("C", "")
    }

    fun testOverloadedJavaMethodByte() {
        doOverloadedMethodTest("byte", "1")
    }

    fun testOverloadedJavaMethodChar() {
        doOverloadedMethodTest("char", "65000")
    }

    fun testOverloadedJavaMethodShort() {
        doOverloadedMethodTest("short", "-32000")
    }

    fun testOverloadedJavaMethodInt() {
        doOverloadedMethodTest("int", "100000")
    }

    fun testOverloadedJavaMethodLong() {
        doOverloadedMethodTest("long", "50000000000")
    }

    fun testOverloadedJavaMethodFloat() {
        doOverloadedMethodTest("float", "6.5")
    }

    fun testOverloadedJavaMethodDouble() {
        doOverloadedMethodTest("double", "3.141592653589793")
    }

    private fun doOverloadedMethodTest(typename: String?, value: String?) {
        val script =
            "local a = luajava.newInstance('" + B::class.java.getName() + "');\n" +
                    "local b = a:set(a:get" + typename + "())\n" +
                    "local c = a:setr(a:get" + typename + "())\n" +
                    "return b,c"
        val chunk: Varargs = globals!!.get("load")!!.call(LuaValue.valueOf(script))!!
        if (!chunk.arg1()!!.toboolean()) fail(chunk.arg(2).toString())
        val results: Varargs = chunk.arg1()!!.invoke()!!
        val nresults = results.narg()
        TestCase.assertEquals(2, nresults)
        val b: LuaValue = results.arg(1)!!
        val c: LuaValue = results.arg(2)!!
        val sb: String? = b.tojstring()
        val sc: String? = c.tojstring()
        TestCase.assertEquals("set(" + typename + ") " + value, sb)
        TestCase.assertEquals("setr(" + typename + ") " + value, sc)
    }

    fun testClassInheritanceLevels() {
        TestCase.assertEquals(0, inheritanceLevels(Any::class.java, Any::class.java))
        TestCase.assertEquals(1, inheritanceLevels(Any::class.java, String::class.java))
        TestCase.assertEquals(1, inheritanceLevels(Any::class.java, A::class.java))
        TestCase.assertEquals(2, inheritanceLevels(Any::class.java, B::class.java))
        TestCase.assertEquals(3, inheritanceLevels(Any::class.java, C::class.java))

        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(A::class.java, Any::class.java))
        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(A::class.java, String::class.java))
        TestCase.assertEquals(0, inheritanceLevels(A::class.java, A::class.java))
        TestCase.assertEquals(1, inheritanceLevels(A::class.java, B::class.java))
        TestCase.assertEquals(2, inheritanceLevels(A::class.java, C::class.java))

        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(B::class.java, Any::class.java))
        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(B::class.java, String::class.java))
        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(B::class.java, A::class.java))
        TestCase.assertEquals(0, inheritanceLevels(B::class.java, B::class.java))
        TestCase.assertEquals(1, inheritanceLevels(B::class.java, C::class.java))

        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(C::class.java, Any::class.java))
        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(C::class.java, String::class.java))
        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(C::class.java, A::class.java))
        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(C::class.java, B::class.java))
        TestCase.assertEquals(0, inheritanceLevels(C::class.java, C::class.java))
    }

    fun testInterfaceInheritanceLevels() {
        TestCase.assertEquals(1, inheritanceLevels(IA::class.java, A::class.java))
        TestCase.assertEquals(1, inheritanceLevels(IB::class.java, B::class.java))
        TestCase.assertEquals(2, inheritanceLevels(IA::class.java, B::class.java))
        TestCase.assertEquals(1, inheritanceLevels(IC::class.java, C::class.java))
        TestCase.assertEquals(2, inheritanceLevels(IB::class.java, C::class.java))
        TestCase.assertEquals(3, inheritanceLevels(IA::class.java, C::class.java))
        TestCase.assertEquals(1, inheritanceLevels(IA::class.java, D::class.java))
        TestCase.assertEquals(2, inheritanceLevels(IC::class.java, D::class.java))
        TestCase.assertEquals(3, inheritanceLevels(IB::class.java, D::class.java))

        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(IB::class.java, A::class.java))
        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(IC::class.java, A::class.java))
        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(IC::class.java, B::class.java))
        TestCase.assertEquals(CoerceLuaToJava.SCORE_UNCOERCIBLE, inheritanceLevels(IB::class.java, IA::class.java))
        TestCase.assertEquals(1, inheritanceLevels(IA::class.java, IB::class.java))
    }

    fun testCoerceJavaToLuaLuaValue() {
        assertSame(LuaValue.NIL, coerce(LuaValue.NIL))
        assertSame(LuaValue.ZERO, coerce(LuaValue.ZERO))
        assertSame(LuaValue.ONE, coerce(LuaValue.ONE))
        assertSame(LuaValue.INDEX, coerce(LuaValue.INDEX))
        val table = LuaValue.tableOf()
        assertSame(table, coerce(table))
    }

    fun testCoerceJavaToLuaByeArray() {
        val bytes = "abcd".toByteArray()
        val value = coerce(bytes)
        assertEquals(LuaString::class.java, value!!.javaClass)
        assertEquals(LuaValue.valueOf("abcd"), value)
    }

    companion object {
        private var globals: LuaValue? = null
        private val ZERO: LuaValue? = LuaValue.ZERO
        private val ONE: LuaValue? = LuaValue.ONE
        private val TWO: LuaValue? = LuaValue.valueOf(2)
        private val THREE: LuaValue? = LuaValue.valueOf(3)
        private val LENGTH = LuaString.valueOf("length")
    }
}
