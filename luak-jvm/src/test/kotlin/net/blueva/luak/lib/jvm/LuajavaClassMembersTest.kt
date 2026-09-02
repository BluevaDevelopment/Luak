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
import net.blueva.luak.LuaError
import net.blueva.luak.LuaValue
import net.blueva.luak.lib.jvm.CoerceJavaToLua.coerce
import net.blueva.luak.lib.jvm.JavaClass.Companion.forClass

class LuajavaClassMembersTest : TestCase() {
    open class A
    open class B : A {
        @JvmField
        var m_byte_field: Byte = 0
        @JvmField
        var m_int_field: Int = 0
        @JvmField
        var m_double_field: Double = 0.0
        @JvmField
        var m_string_field: String? = null

        constructor()
        constructor(i: Int) {
            m_int_field = i
        }

        fun setString(x: String): String {
            return "setString(String) " + x
        }

        val string: String
            get() = "abc"

        open fun getint(): Int {
            return 100000
        }

        fun uniq(): String {
            return "uniq()"
        }

        fun uniqs(s: String): String {
            return "uniqs(string:" + s + ")"
        }

        fun uniqi(i: Int): String {
            return "uniqi(int:" + i + ")"
        }

        fun uniqsi(s: String, i: Int): String {
            return "uniqsi(string:" + s + ",int:" + i + ")"
        }

        fun uniqis(i: Int, s: String?): String {
            return "uniqis(int:" + i + ",string:" + s + ")"
        }

        fun pick(): String {
            return "pick()"
        }

        open fun pick(s: String): String {
            return "pick(string:" + s + ")"
        }

        open fun pick(i: Int): String {
            return "pick(int:" + i + ")"
        }

        fun pick(s: String, i: Int): String {
            return "pick(string:" + s + ",int:" + i + ")"
        }

        fun pick(i: Int, s: String?): String {
            return "pick(int:" + i + ",string:" + s + ")"
        }

        companion object {
            @JvmStatic
            fun staticpick(): String {
                return "static-pick()"
            }

            @JvmStatic
            fun staticpick(s: String): String {
                return "static-pick(string:" + s + ")"
            }

            @JvmStatic
            fun staticpick(i: Int): String {
                return "static-pick(int:" + i + ")"
            }

            @JvmStatic
            fun staticpick(s: String, i: Int): String {
                return "static-pick(string:" + s + ",int:" + i + ")"
            }

            @JvmStatic
            fun staticpick(i: Int, s: String?): String {
                return "static-pick(int:" + i + ",string:" + s + ")"
            }
        }
    }

    class C : B {
        constructor()
        constructor(s: String?) {
            m_string_field = s
        }

        constructor(i: Int) {
            m_int_field = i
        }

        constructor(s: String?, i: Int) {
            m_string_field = s
            m_int_field = i
        }

        override fun getint(): Int {
            return 200000
        }

        override fun pick(s: String): String {
            return "class-c-pick(string:" + s + ")"
        }

        override fun pick(i: Int): String {
            return "class-c-pick(int:" + i + ")"
        }

        object D {
            fun name(): String {
                return "name-of-D"
            }
        }
    }

    fun testSetByteField() {
        val b = B()
        val i = JavaInstance(b)
        i.set("m_byte_field", ONE)
        TestCase.assertEquals(1, b.m_byte_field.toInt())
        assertEquals(ONE, i.get("m_byte_field"))
        i.set("m_byte_field", PI)
        TestCase.assertEquals(3, b.m_byte_field.toInt())
        assertEquals(THREE, i.get("m_byte_field"))
        i.set("m_byte_field", ABC)
        TestCase.assertEquals(0, b.m_byte_field.toInt())
        assertEquals(ZERO, i.get("m_byte_field"))
    }

    fun testSetDoubleField() {
        val b = B()
        val i = JavaInstance(b)
        // A Java double field reads back as a Lua float whatever its value, so
        // 1.0 comes across as the float 1.0 and not as the integer 1.
        i.set("m_double_field", ONE)
        assertEquals(1.0, b.m_double_field)
        assertEquals(LuaValue.valueOf(1.0), i.get("m_double_field"))
        i.set("m_double_field", PI)
        assertEquals(Math.PI, b.m_double_field)
        assertEquals(PI, i.get("m_double_field"))
        i.set("m_double_field", ABC)
        assertEquals(0.0, b.m_double_field)
        assertEquals(LuaValue.valueOf(0.0), i.get("m_double_field"))
    }

    fun testNoFactory() {
        val c = forClass(A::class.java)
        try {
            c.call()
            fail("did not throw lua error as expected")
        } catch (e: LuaError) {
        }
    }

    fun testUniqueFactoryCoercible() {
        val c = forClass(B::class.java)
        assertEquals(JavaClass::class.java, c.javaClass)
        val constr: LuaValue = c.get("new")!!
        assertEquals(JavaConstructor.Overload::class.java, constr.javaClass)
        val v: LuaValue = constr.call(NUMS)!!
        val b: Any = v.touserdata()!!
        assertEquals(B::class.java, b.javaClass)
        TestCase.assertEquals(123, (b as B).m_int_field)
        val b0: Any = constr.call()!!.touserdata()!!
        assertEquals(B::class.java, b0.javaClass)
        TestCase.assertEquals(0, (b0 as B).m_int_field)
    }

    fun testUniqueFactoryUncoercible() {
        val f = forClass(B::class.java)
        val constr: LuaValue = f.get("new")!!
        assertEquals(JavaConstructor.Overload::class.java, constr.javaClass)
        try {
            val v = constr.call(LuaValue.userdataOf(Any()))
            val b: Any = v!!.touserdata()!!
            // fail( "did not throw lua error as expected" );
            TestCase.assertEquals(0, (b as B).m_int_field)
        } catch (e: LuaError) {
        }
    }

    fun testOverloadedFactoryCoercible() {
        val f = forClass(C::class.java)
        val constr: LuaValue = f.get("new")!!
        assertEquals(JavaConstructor.Overload::class.java, constr.javaClass)
        val c: Any = constr.call()!!.touserdata()!!
        val ci: Any = constr.call(LuaValue.valueOf(123))!!.touserdata()!!
        val cs: Any = constr.call(LuaValue.valueOf("abc"))!!.touserdata()!!
        val csi: Any = constr.call(LuaValue.valueOf("def"), LuaValue.valueOf(456))!!.touserdata()!!
        assertEquals(C::class.java, c.javaClass)
        assertEquals(C::class.java, ci.javaClass)
        assertEquals(C::class.java, cs.javaClass)
        assertEquals(C::class.java, csi.javaClass)
        TestCase.assertEquals(null, (c as C).m_string_field)
        TestCase.assertEquals(0, c.m_int_field)
        TestCase.assertEquals("abc", (cs as C).m_string_field)
        TestCase.assertEquals(0, cs.m_int_field)
        TestCase.assertEquals(null, (ci as C).m_string_field)
        TestCase.assertEquals(123, ci.m_int_field)
        TestCase.assertEquals("def", (csi as C).m_string_field)
        TestCase.assertEquals(456, csi.m_int_field)
    }

    fun testOverloadedFactoryUncoercible() {
        val f = forClass(C::class.java)
        try {
            val c: Any = f.call(LuaValue.userdataOf(Any()))!!

            // fail( "did not throw lua error as expected" );
            TestCase.assertEquals(0, (c as C).m_int_field)
            TestCase.assertEquals(null, c.m_string_field)
        } catch (e: LuaError) {
        }
    }

    fun testNoAttribute() {
        val f = forClass(A::class.java)
        val v: LuaValue = f.get("bogus")!!
        assertEquals(v, LuaValue.NIL)
        try {
            f.set("bogus", ONE)
            fail("did not throw lua error as expected")
        } catch (e: LuaError) {
        }
    }

    fun testFieldAttributeCoercible() {
        var i = JavaInstance(B())
        i.set("m_int_field", ONE)
        TestCase.assertEquals(1, i.get("m_int_field")!!.toint())
        i.set("m_int_field", THREE)
        TestCase.assertEquals(3, i.get("m_int_field")!!.toint())
        i = JavaInstance(C())
        i.set("m_int_field", ONE)
        TestCase.assertEquals(1, i.get("m_int_field")!!.toint())
        i.set("m_int_field", THREE)
        TestCase.assertEquals(3, i.get("m_int_field")!!.toint())
    }

    fun testUniqueMethodAttributeCoercible() {
        val b = B()
        val ib = JavaInstance(b)
        val b_getString: LuaValue = ib.get("getString")!!
        val b_getint: LuaValue = ib.get("getint")!!
        assertEquals(JavaMethod::class.java, b_getString.javaClass)
        assertEquals(JavaMethod::class.java, b_getint.javaClass)
        TestCase.assertEquals("abc", b_getString.call(SOMEB)!!.tojstring())
        TestCase.assertEquals(100000, b_getint.call(SOMEB)!!.toint())
        TestCase.assertEquals("abc", b_getString.call(SOMEC)!!.tojstring())
        TestCase.assertEquals(200000, b_getint.call(SOMEC)!!.toint())
    }

    fun testUniqueMethodAttributeArgsCoercible() {
        val b = B()
        val ib = JavaInstance(b)
        val uniq: LuaValue = ib.get("uniq")!!
        val uniqs: LuaValue = ib.get("uniqs")!!
        val uniqi: LuaValue = ib.get("uniqi")!!
        val uniqsi: LuaValue = ib.get("uniqsi")!!
        val uniqis: LuaValue = ib.get("uniqis")!!
        assertEquals(JavaMethod::class.java, uniq.javaClass)
        assertEquals(JavaMethod::class.java, uniqs.javaClass)
        assertEquals(JavaMethod::class.java, uniqi.javaClass)
        assertEquals(JavaMethod::class.java, uniqsi.javaClass)
        assertEquals(JavaMethod::class.java, uniqis.javaClass)
        TestCase.assertEquals("uniq()", uniq.call(SOMEB)!!.tojstring())
        TestCase.assertEquals("uniqs(string:abc)", uniqs.call(SOMEB, ABC)!!.tojstring())
        TestCase.assertEquals("uniqi(int:1)", uniqi.call(SOMEB, ONE)!!.tojstring())
        TestCase.assertEquals("uniqsi(string:abc,int:1)", uniqsi.call(SOMEB, ABC, ONE)!!.tojstring())
        TestCase.assertEquals("uniqis(int:1,string:abc)", uniqis.call(SOMEB, ONE, ABC)!!.tojstring())
        TestCase.assertEquals(
            "uniqis(int:1,string:abc)", uniqis.invoke(
                net.blueva.luak.LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                        LuajavaClassMembersTest.Companion.SOMEB,
                        LuajavaClassMembersTest.Companion.ONE,
                        LuajavaClassMembersTest.Companion.ABC,
                        LuajavaClassMembersTest.Companion.ONE
                    )
                )!!
            )!!.arg1()!!.tojstring()
        )
    }

    fun testOverloadedMethodAttributeCoercible() {
        val b = B()
        val ib = JavaInstance(b)
        val p: LuaValue = ib.get("pick")!!
        TestCase.assertEquals("pick()", p.call(SOMEB)!!.tojstring())
        TestCase.assertEquals("pick(string:abc)", p.call(SOMEB, ABC)!!.tojstring())
        TestCase.assertEquals("pick(int:1)", p.call(SOMEB, ONE)!!.tojstring())
        TestCase.assertEquals("pick(string:abc,int:1)", p.call(SOMEB, ABC, ONE)!!.tojstring())
        TestCase.assertEquals("pick(int:1,string:abc)", p.call(SOMEB, ONE, ABC)!!.tojstring())
        TestCase.assertEquals(
            "pick(int:1,string:abc)", p.invoke(
                net.blueva.luak.LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                        LuajavaClassMembersTest.Companion.SOMEB,
                        LuajavaClassMembersTest.Companion.ONE,
                        LuajavaClassMembersTest.Companion.ABC,
                        LuajavaClassMembersTest.Companion.ONE
                    )
                )!!
            )!!.arg1()!!.tojstring()
        )
    }

    fun testUnboundOverloadedMethodAttributeCoercible() {
        val b = B()
        val ib = JavaInstance(b)
        val p: LuaValue = ib.get("pick")!!
        assertEquals(JavaMethod.Overload::class.java, p.javaClass)
        TestCase.assertEquals("pick()", p.call(SOMEC)!!.tojstring())
        TestCase.assertEquals("class-c-pick(string:abc)", p.call(SOMEC, ABC)!!.tojstring())
        TestCase.assertEquals("class-c-pick(int:1)", p.call(SOMEC, ONE)!!.tojstring())
        TestCase.assertEquals("pick(string:abc,int:1)", p.call(SOMEC, ABC, ONE)!!.tojstring())
        TestCase.assertEquals("pick(int:1,string:abc)", p.call(SOMEC, ONE, ABC)!!.tojstring())
        TestCase.assertEquals(
            "pick(int:1,string:abc)", p.invoke(
                net.blueva.luak.LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                        LuajavaClassMembersTest.Companion.SOMEC,
                        LuajavaClassMembersTest.Companion.ONE,
                        LuajavaClassMembersTest.Companion.ABC,
                        LuajavaClassMembersTest.Companion.ONE
                    )
                )!!
            )!!.arg1()!!.tojstring()
        )
    }

    fun testOverloadedStaticMethodAttributeCoercible() {
        val b = B()
        val ib = JavaInstance(b)
        val p: LuaValue = ib.get("staticpick")!!
        TestCase.assertEquals("static-pick()", p.call(SOMEB)!!.tojstring())
        TestCase.assertEquals("static-pick(string:abc)", p.call(SOMEB, ABC)!!.tojstring())
        TestCase.assertEquals("static-pick(int:1)", p.call(SOMEB, ONE)!!.tojstring())
        TestCase.assertEquals("static-pick(string:abc,int:1)", p.call(SOMEB, ABC, ONE)!!.tojstring())
        TestCase.assertEquals("static-pick(int:1,string:abc)", p.call(SOMEB, ONE, ABC)!!.tojstring())
        TestCase.assertEquals(
            "static-pick(int:1,string:abc)", p.invoke(
                net.blueva.luak.LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                        LuajavaClassMembersTest.Companion.SOMEB,
                        LuajavaClassMembersTest.Companion.ONE,
                        LuajavaClassMembersTest.Companion.ABC,
                        LuajavaClassMembersTest.Companion.ONE
                    )
                )!!
            )!!.arg1()!!.tojstring()
        )
    }

    fun testGetInnerClass() {
        val c = C()
        val ic = JavaInstance(c)
        val d: LuaValue = ic.get("D")!!
        assertFalse(d.isnil())
        assertSame(d, forClass(C.D::class.java))
        val e: LuaValue = ic.get("E")!!
        assertTrue(e.isnil())
    }

    companion object {
        var ZERO: LuaValue? = LuaValue.ZERO
        var ONE: LuaValue? = LuaValue.ONE
        var PI: LuaValue? = LuaValue.valueOf(Math.PI)
        var THREE: LuaValue? = LuaValue.valueOf(3)
        var NUMS: LuaValue? = LuaValue.valueOf(123)
        var ABC: LuaValue = LuaValue.valueOf("abc")
        var SOMEA: LuaValue? = coerce(A())
        var SOMEB: LuaValue? = coerce(B())
        var SOMEC: LuaValue? = coerce(C())
    }
}
