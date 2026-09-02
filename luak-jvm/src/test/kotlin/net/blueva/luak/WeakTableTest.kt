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
import java.lang.ref.WeakReference

abstract class WeakTableTest : TableTest() {
    class MyData(val value: Int) {
        override fun hashCode(): Int {
            return value
        }

        override fun equals(o: Any?): Boolean {
            return (o is MyData) && o.value == value
        }

        override fun toString(): String {
            return "mydata-" + value
        }
    }

    class WeakValueTableTest : WeakTableTest() {
        override val expectsCompactArrayLayout: Boolean = false

        override fun new_Table(): LuaTable {
            return WeakTable.make(false, true)
        }

        override fun new_Table(n: Int, m: Int): LuaTable {
            return WeakTable.make(false, true)
        }

        fun testWeakValuesTable() {
            val t = new_Table()

            var obj: Any? = Any()
            var tableValue: LuaTable? = LuaTable()
            var stringValue: LuaString? = LuaString.valueOf("this is a test")
            var tableValue2: LuaTable? = LuaTable()

            t.set("table", tableValue)
            t.set("userdata", LuaValue.userdataOf(obj, null))
            t.set("string", stringValue)
            t.set("string2", LuaValue.valueOf("another string"))
            t.set(1, tableValue2)
            assertTrue("table must have at least 4 elements", t.getHashLength() >= 4)

            // check that table can be used to get elements 
            assertEquals(tableValue, t.get("table"))
            assertEquals(stringValue, t.get("string"))
            assertEquals(obj, t.get("userdata")!!.checkuserdata())
            assertEquals(tableValue2, t.get(1))

            // nothing should be collected, since we have strong references here
            collectGarbage()


            // check that elements are still there 
            assertEquals(tableValue, t.get("table"))
            assertEquals(stringValue, t.get("string"))
            assertEquals(obj, t.get("userdata")!!.checkuserdata())
            assertEquals(tableValue2, t.get(1))

            // drop our strong references
            obj = null
            tableValue = null
            tableValue2 = null
            stringValue = null


            // Garbage collection should cause weak entries to be dropped.
            collectGarbage()


            // check that they are dropped
            assertEquals(LuaValue.NIL, t.get("table"))
            assertEquals(LuaValue.NIL, t.get("userdata"))
            assertEquals(LuaValue.NIL, t.get(1))
            assertFalse("strings should not be in weak references", t.get("string")!!.isnil())
        }
    }

    class WeakKeyTableTest : WeakTableTest() {
        override fun new_Table(): LuaTable {
            return WeakTable.make(true, false)
        }

        override fun new_Table(n: Int, m: Int): LuaTable {
            return WeakTable.make(true, false)
        }

        fun testWeakKeysTable() {
            val t = WeakTable.make(true, false)

            var key: LuaValue = LuaValue.userdataOf(MyData(111))
            var `val`: LuaValue = LuaValue.userdataOf(MyData(222))


            // set up the table
            t.set(key, `val`)
            assertEquals(`val`, t.get(key))
            System.gc()
            assertEquals(`val`, t.get(key))

            // drop key and value references, replace them with new ones
            val origkey: WeakReference<*> = WeakReference<Any?>(key)
            val origval: WeakReference<*> = WeakReference<Any?>(`val`)
            key = LuaValue.userdataOf(MyData(111))
            `val` = LuaValue.userdataOf(MyData(222))

            // new key and value should be interchangeable (feature of this test class)
            assertEquals(key, origkey.get())
            assertEquals(`val`, origval.get())
            assertEquals(`val`, t.get(key))
            assertEquals(`val`, t.get((origkey.get() as net.blueva.luak.LuaValue?)!!))
            assertEquals(origval.get(), t.get(key))

            // value should not be reachable after gc
            collectGarbage()
            assertEquals(null, origkey.get())
            assertEquals(LuaValue.NIL, t.get(key))
            collectGarbage()
            assertEquals(null, origval.get())
        }

        fun testNext() {
            val t = WeakTable.make(true, true)

            val key: LuaValue = LuaValue.userdataOf(MyData(111))
            val `val`: LuaValue = LuaValue.userdataOf(MyData(222))
            var key2: LuaValue? = LuaValue.userdataOf(MyData(333))
            var val2: LuaValue? = LuaValue.userdataOf(MyData(444))
            val key3: LuaValue = LuaValue.userdataOf(MyData(555))
            val val3: LuaValue = LuaValue.userdataOf(MyData(666))


            // set up the table
            t.set(key, `val`)
            t.set(key2, val2)
            t.set(key3, val3)


            // forget one of the keys
            key2 = null
            val2 = null
            collectGarbage()


            // table should have 2 entries
            var size = 0
            var k: LuaValue = t.next(LuaValue.NIL).arg1()!!
            while (!k.isnil()
            ) {
                size++
                k = t.next(k).arg1()!!
            }
            TestCase.assertEquals(2, size)
        }
    }

    class WeakKeyValueTableTest : WeakTableTest() {
        override val expectsCompactArrayLayout: Boolean = false

        override fun new_Table(): LuaTable {
            return WeakTable.make(true, true)
        }

        override fun new_Table(n: Int, m: Int): LuaTable {
            return WeakTable.make(true, true)
        }

        fun testWeakKeysValuesTable() {
            val t = WeakTable.make(true, true)

            var key: LuaValue = LuaValue.userdataOf(MyData(111))
            var `val`: LuaValue = LuaValue.userdataOf(MyData(222))
            var key2: LuaValue = LuaValue.userdataOf(MyData(333))
            var val2: LuaValue? = LuaValue.userdataOf(MyData(444))
            var key3: LuaValue? = LuaValue.userdataOf(MyData(555))
            var val3: LuaValue = LuaValue.userdataOf(MyData(666))


            // set up the table
            t.set(key, `val`)
            t.set(key2, val2)
            t.set(key3, val3)
            assertEquals(`val`, t.get(key))
            assertEquals(val2, t.get(key2))
            assertEquals(val3, t.get(key3!!))
            System.gc()
            assertEquals(`val`, t.get(key))
            assertEquals(val2, t.get(key2))
            assertEquals(val3, t.get(key3))

            // drop key and value references, replace them with new ones
            val origkey: WeakReference<*> = WeakReference<Any?>(key)
            val origval: WeakReference<*> = WeakReference<Any?>(`val`)
            val origkey2: WeakReference<*> = WeakReference<Any?>(key2)
            val origval2: WeakReference<*> = WeakReference<Any?>(val2)
            val origkey3: WeakReference<*> = WeakReference<Any?>(key3)
            val origval3: WeakReference<*> = WeakReference<Any?>(val3)
            key = LuaValue.userdataOf(MyData(111))
            `val` = LuaValue.userdataOf(MyData(222))
            key2 = LuaValue.userdataOf(MyData(333))
            // don't drop val2, or key3
            val3 = LuaValue.userdataOf(MyData(666))

            // no values should be reachable after gc
            collectGarbage()
            assertEquals(null, origkey.get())
            assertEquals(null, origval.get())
            assertEquals(null, origkey2.get())
            assertEquals(null, origval3.get())
            assertEquals(LuaValue.NIL, t.get(key))
            assertEquals(LuaValue.NIL, t.get(key2))
            assertEquals(LuaValue.NIL, t.get(key3))

            // all originals should be gone after gc, then access
            val2 = null
            key3 = null
            collectGarbage()
            assertEquals(null, origval2.get())
            assertEquals(null, origkey3.get())
        }

        fun testReplace() {
            val t = WeakTable.make(true, true)

            val key: LuaValue = LuaValue.userdataOf(MyData(111))
            val `val`: LuaValue = LuaValue.userdataOf(MyData(222))
            val key2: LuaValue = LuaValue.userdataOf(MyData(333))
            val val2: LuaValue = LuaValue.userdataOf(MyData(444))
            val key3: LuaValue = LuaValue.userdataOf(MyData(555))
            val val3: LuaValue = LuaValue.userdataOf(MyData(666))


            // set up the table
            t.set(key, `val`)
            t.set(key2, val2)
            t.set(key3, val3)

            val val4: LuaValue = LuaValue.userdataOf(MyData(777))
            t.set(key2, val4)


            // table should have 3 entries
            var size = 0
            var k: LuaValue = t.next(LuaValue.NIL).arg1()!!
            while (!k.isnil() && size < 1000
            ) {
                size++
                k = t.next(k).arg1()!!
            }
            TestCase.assertEquals(3, size)
        }
    }

    companion object {
        fun collectGarbage() {
            val rt = Runtime.getRuntime()
            rt.gc()
            try {
                Thread.sleep(20)
                rt.gc()
                Thread.sleep(20)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            rt.gc()
        }
    }
}
