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
import java.util.*

open class TableTest : TestCase() {
    protected open val expectsCompactArrayLayout: Boolean = true

    protected open fun new_Table(): LuaTable {
        return LuaTable()
    }

    protected open fun new_Table(n: Int, m: Int): LuaTable {
        return LuaTable(n, m)
    }

    private fun keyCount(t: LuaTable): Int {
        return keys(t).size
    }

    private fun keys(t: LuaTable): Array<LuaValue?> {
        val l = ArrayList<LuaValue?>()
        var k: LuaValue? = LuaValue.NIL
        while (true) {
            val n: Varargs = t.next(k)!!
            if ((n.arg1().also { k = it })!!.isnil()) break
            l.add(k)
        }
        return l.toTypedArray<LuaValue?>()
    }


    fun testInOrderIntegerKeyInsertion() {
        val t = new_Table()

        for (i in 1..32) {
            t.set(i, LuaValue.valueOf("Test Value! " + i))
        }

        // Ensure all keys are still there.
        for (i in 1..32) {
            TestCase.assertEquals("Test Value! " + i, t.get(i).tojstring())
        }


        // Ensure capacities make sense
        if (expectsCompactArrayLayout) {
            TestCase.assertEquals(0, t.getHashLength())
            assertTrue(t.getArrayLength() >= 32)
            assertTrue(t.getArrayLength() <= 64)
        }
    }

    fun testRekeyCount() {
        val t = new_Table()


        // NOTE: This order of insertion is important.
        t.set(3, LuaInteger.valueOf(3))
        t.set(1, LuaInteger.valueOf(1))
        t.set(5, LuaInteger.valueOf(5))
        t.set(4, LuaInteger.valueOf(4))
        t.set(6, LuaInteger.valueOf(6))
        t.set(2, LuaInteger.valueOf(2))

        for (i in 1..5) {
            assertEquals(LuaInteger.valueOf(i), t.get(i))
        }

        assertTrue(t.getArrayLength() >= 3)
        assertTrue(t.getArrayLength() <= 12)
        assertTrue(t.getHashLength() <= 3)
    }

    fun testOutOfOrderIntegerKeyInsertion() {
        val t = new_Table()

        for (i in 32 downTo 1) {
            t.set(i, LuaValue.valueOf("Test Value! " + i))
        }

        // Ensure all keys are still there.
        for (i in 1..32) {
            TestCase.assertEquals("Test Value! " + i, t.get(i).tojstring())
        }


        // Ensure capacities make sense
        if (expectsCompactArrayLayout) {
            TestCase.assertEquals(32, t.getArrayLength())
            TestCase.assertEquals(0, t.getHashLength())
        }
    }

    fun testStringAndIntegerKeys() {
        val t = new_Table()

        for (i in 0..9) {
            val str: LuaString? = LuaValue.valueOf(i.toString())
            t.set(i, str)
            t.set(str, LuaInteger.valueOf(i))
        }

        assertTrue(t.getArrayLength() >= 8) // 1, 2, ..., 9
        assertTrue(t.getArrayLength() <= 16)
        assertTrue(t.getHashLength() >= 11) // 0, "0", "1", ..., "9"
        assertTrue(t.getHashLength() <= 33)

        val keys = keys(t)

        var intKeys = 0
        var stringKeys = 0

        TestCase.assertEquals(20, keys.size)
        for (i in keys.indices) {
            val k = keys[i]

            if (k is LuaInteger) {
                val ik = k.toint()
                assertTrue(ik >= 0 && ik < 10)
                val mask = 1 shl ik
                assertTrue((intKeys and mask) == 0)
                intKeys = intKeys or mask
            } else if (k is LuaString) {
                val ik = k.strvalue().tojstring().toInt()
                TestCase.assertEquals(ik.toString(), k.strvalue().tojstring())
                assertTrue(ik >= 0 && ik < 10)
                val mask = 1 shl ik
                assertTrue("Key \"" + ik + "\" found more than once", (stringKeys and mask) == 0)
                stringKeys = stringKeys or mask
            } else {
                fail("Unexpected type of key found")
            }
        }

        TestCase.assertEquals(0x03FF, intKeys)
        TestCase.assertEquals(0x03FF, stringKeys)
    }

    fun testBadInitialCapacity() {
        val t = new_Table(0, 1)

        t.set("test", LuaValue.valueOf("foo"))
        t.set("explode", LuaValue.valueOf("explode"))
        TestCase.assertEquals(2, keyCount(t))
    }

    fun testRemove0() {
        val t = new_Table(2, 0)

        t.set(1, LuaValue.valueOf("foo"))
        t.set(2, LuaValue.valueOf("bah"))
        assertNotSame(LuaValue.NIL, t.get(1))
        assertNotSame(LuaValue.NIL, t.get(2))
        assertEquals(LuaValue.NIL, t.get(3))

        t.set(1, LuaValue.NIL)
        t.set(2, LuaValue.NIL)
        t.set(3, LuaValue.NIL)
        assertEquals(LuaValue.NIL, t.get(1))
        assertEquals(LuaValue.NIL, t.get(2))
        assertEquals(LuaValue.NIL, t.get(3))
    }

    fun testRemove1() {
        val t = new_Table(0, 1)

        t.set("test", LuaValue.valueOf("foo"))
        t.set("explode", LuaValue.NIL)
        t.set(42, LuaValue.NIL)
        t.set(new_Table(), LuaValue.NIL)
        t.set("test", LuaValue.NIL)
        TestCase.assertEquals(0, keyCount(t))

        t.set(10, LuaInteger.valueOf(5))
        t.set(10, LuaValue.NIL)
        TestCase.assertEquals(0, keyCount(t))
    }

    fun testRemove2() {
        val t = new_Table(0, 1)

        t.set("test", LuaValue.valueOf("foo"))
        t.set("string", LuaInteger.valueOf(10))
        TestCase.assertEquals(2, keyCount(t))

        t.set("string", LuaValue.NIL)
        t.set("three", LuaValue.valueOf(3.14))
        TestCase.assertEquals(2, keyCount(t))

        t.set("test", LuaValue.NIL)
        TestCase.assertEquals(1, keyCount(t))

        t.set(10, LuaInteger.valueOf(5))
        TestCase.assertEquals(2, keyCount(t))

        t.set(10, LuaValue.NIL)
        TestCase.assertEquals(1, keyCount(t))

        t.set("three", LuaValue.NIL)
        TestCase.assertEquals(0, keyCount(t))
    }

    fun testShrinkNonPowerOfTwoArray() {
        val t = new_Table(6, 2)

        t.set(1, "one")
        t.set(2, "two")
        t.set(3, "three")
        t.set(4, "four")
        t.set(5, "five")
        t.set(6, "six")

        t.set("aa", "aaa")
        t.set("bb", "bbb")

        t.set(3, LuaValue.NIL)
        t.set(4, LuaValue.NIL)
        t.set(6, LuaValue.NIL)

        t.set("cc", "ccc")
        t.set("dd", "ddd")

        TestCase.assertEquals(4, t.getArrayLength())
        assertTrue(t.getHashLength() < 10)
        TestCase.assertEquals(5, t.hashEntries)
        TestCase.assertEquals("one", t.get(1).tojstring())
        TestCase.assertEquals("two", t.get(2).tojstring())
        assertEquals(LuaValue.NIL, t.get(3))
        assertEquals(LuaValue.NIL, t.get(4))
        TestCase.assertEquals("five", t.get(5).tojstring())
        assertEquals(LuaValue.NIL, t.get(6))
        TestCase.assertEquals("aaa", t.get("aa")!!.tojstring())
        TestCase.assertEquals("bbb", t.get("bb")!!.tojstring())
        TestCase.assertEquals("ccc", t.get("cc")!!.tojstring())
        TestCase.assertEquals("ddd", t.get("dd")!!.tojstring())
    }

    fun testInOrderLuaLength() {
        val t = new_Table()

        for (i in 1..32) {
            t.set(i, LuaValue.valueOf("Test Value! " + i))
            TestCase.assertEquals(i, t.length())
        }
    }

    fun testOutOfOrderLuaLength() {
        val t = new_Table()

        var j = 8
        while (j < 32) {
            for (i in j downTo 1) {
                t.set(i, LuaValue.valueOf("Test Value! " + i))
            }
            TestCase.assertEquals(j, t.length())
            j += 8
        }
    }

    fun testStringKeysLuaLength() {
        val t = new_Table()

        for (i in 1..32) {
            t.set("str-" + i, LuaValue.valueOf("String Key Test Value! " + i))
            TestCase.assertEquals(0, t.length())
        }
    }

    fun testMixedKeysLuaLength() {
        val t = new_Table()

        for (i in 1..32) {
            t.set("str-" + i, LuaValue.valueOf("String Key Test Value! " + i))
            t.set(i, LuaValue.valueOf("Int Key Test Value! " + i))
            TestCase.assertEquals(i, t.length())
        }
    }

    fun testInsertBeginningOfList() {
        val t = new_Table()
        val v: Vector<Any?> = Vector<Any?>()

        for (i in 1..32) {
            val test: LuaString? = LuaValue.valueOf("Test Value! " + i)
            t.insert(1, test!!)
            v.insertElementAt(test, 0)
            compareLists(t, v)
        }
    }

    fun testInsertEndOfList() {
        val t = new_Table()
        val v: Vector<Any?> = Vector<Any?>()

        for (i in 1..32) {
            val test: LuaString? = LuaValue.valueOf("Test Value! " + i)
            t.insert(0, test!!)
            v.insertElementAt(test, v.size)
            compareLists(t, v)
        }
    }

    fun testInsertMiddleOfList() {
        val t = new_Table()
        val v: Vector<Any?> = Vector<Any?>()

        for (i in 1..32) {
            val test: LuaString? = LuaValue.valueOf("Test Value! " + i)
            val m = i / 2
            t.insert(m + 1, test!!)
            v.insertElementAt(test, m)
            compareLists(t, v)
        }
    }

    fun testRemoveBeginningOfList() {
        val t = new_Table()
        val v: Vector<Any?> = Vector<Any?>()
        prefillLists(t, v)
        for (i in 1..32) {
            t.remove(1)
            v.removeElementAt(0)
            compareLists(t, v)
        }
    }

    fun testRemoveEndOfList() {
        val t = new_Table()
        val v: Vector<Any?> = Vector<Any?>()
        prefillLists(t, v)
        for (i in 1..32) {
            t.remove(0)
            v.removeElementAt(v.size - 1)
            compareLists(t, v)
        }
    }

    fun testRemoveMiddleOfList() {
        val t = new_Table()
        val v: Vector<Any?> = Vector<Any?>()
        prefillLists(t, v)
        for (i in 1..32) {
            val m = v.size / 2
            t.remove(m + 1)
            v.removeElementAt(m)
            compareLists(t, v)
        }
    }

    fun testRemoveWhileIterating() {
        val t = LuaValue.tableOf(
            arrayOf<LuaValue?>(
                LuaValue.valueOf("a"), LuaValue.valueOf("aa"),
                LuaValue.valueOf("b"), LuaValue.valueOf("bb"),
                LuaValue.valueOf("c"), LuaValue.valueOf("cc"),
                LuaValue.valueOf("d"), LuaValue.valueOf("dd"),
                LuaValue.valueOf("e"), LuaValue.valueOf("ee"),
            ), arrayOf<LuaValue?>(
                LuaValue.valueOf("11"),
                LuaValue.valueOf("22"),
                LuaValue.valueOf("33"),
                LuaValue.valueOf("44"),
                LuaValue.valueOf("55"),
            )
        )
        // Find expected order after removal.
        val expected: MutableList<String?> = ArrayList<String?>()
        var n: Varargs
        var i: Int
        n = t.next(LuaValue.NIL)
        i = 0
        while (!n.arg1()!!.isnil()) {
            if (i % 2 == 0) expected.add(n.arg1().toString() + "=" + n.arg(2))
            n = t.next(n.arg1())!!
            ++i
        }
        // Remove every other key while iterating over the table.
        n = t.next(LuaValue.NIL)
        i = 0
        while (!n.arg1()!!.isnil()) {
            if (i % 2 != 0) t.set(n.arg1(), LuaValue.NIL)
            n = t.next(n.arg1())!!
            ++i
        }
        // Iterate over remaining table, and form list of entries still in table.
        val actual: MutableList<String?> = ArrayList<String?>()
        n = t.next(LuaValue.NIL)
        while (!n.arg1()!!.isnil()) {
            actual.add(n.arg1().toString() + "=" + n.arg(2))
            n = t.next(n.arg1())!!
        }
        assertEquals(expected, actual)
    }

    companion object {
        private fun compareLists(t: LuaTable, v: Vector<Any?>) {
            val n = v.size
            TestCase.assertEquals(v.size, t.length())
            for (j in 0..<n) {
                var vj: Any? = v.elementAt(j)
                val tj: Any? = t.get(j + 1).tojstring()
                vj = (vj as LuaString).tojstring()
                assertEquals(vj, tj)
            }
        }

        private fun prefillLists(t: LuaTable, v: Vector<Any?>) {
            for (i in 1..32) {
                val test: LuaString? = LuaValue.valueOf("Test Value! " + i)
                t.insert(0, test!!)
                v.insertElementAt(test, v.size)
            }
        }
    }
}
