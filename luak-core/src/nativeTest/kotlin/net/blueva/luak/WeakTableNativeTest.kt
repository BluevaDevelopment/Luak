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

import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals

// Confirms real weak-table semantics now that WeakReference is real on Native.
class WeakTableNativeTest {
    private fun populateWithDroppableValue(t: LuaTable) {
        val value: LuaValue = LuaValue.userdataOf(Any())
        t.set("key", value)
        assertEquals(value, t.get("key"))
    }

    @OptIn(NativeRuntimeApi::class)
    @Test
    fun weakValuesAreDroppedAfterCollection() {
        val t = WeakTable.make(false, true)
        populateWithDroppableValue(t)

        GC.collect()

        assertEquals(LuaValue.NIL, t.get("key"))
    }

    private fun populateWithDroppableKey(t: LuaTable) {
        val key: LuaValue = LuaValue.userdataOf(Any())
        t.set(key, LuaValue.valueOf("value"))
        assertEquals(LuaValue.valueOf("value"), t.get(key))
    }

    @OptIn(NativeRuntimeApi::class)
    @Test
    fun weakKeysAreDroppedAfterCollection() {
        val t = WeakTable.make(true, false)
        populateWithDroppableKey(t)

        GC.collect()

        var size = 0
        var k: LuaValue = t.next(LuaValue.NIL).arg1()!!
        while (!k.isnil()) {
            size++
            k = t.next(k).arg1()!!
        }
        assertEquals(0, size)
    }
}
