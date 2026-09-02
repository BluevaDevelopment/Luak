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
package net.blueva.luak.lib.jvm

import net.blueva.luak.LuaTable
import net.blueva.luak.LuaUserdata
import net.blueva.luak.LuaValue
import net.blueva.luak.lib.OneArgFunction
import java.lang.reflect.Array

/**
 * LuaValue that represents a Java instance of array type.
 * 
 * 
 * Can get elements by their integer key index, as well as the length.
 * 
 * 
 * This class is not used directly.
 * It is returned by calls to [CoerceJavaToLua.coerce]
 * when an array is supplied.
 * @see CoerceJavaToLua
 * 
 * @see CoerceLuaToJava
 */
internal class JavaArray(instance: Any?) : LuaUserdata(instance!!) {
    private class LenFunction : OneArgFunction() {
        override fun call(u: LuaValue?): LuaValue? {
            return valueOf(Array.getLength((u!! as LuaUserdata).m_instance))
        }
    }

    init {
        setmetatable(array_metatable)
    }

    override fun get(key: LuaValue): LuaValue {
        if (key == LENGTH) return valueOf(Array.getLength(m_instance))
        if (key!!.isint()) {
            val i = key.toint() - 1
            return if (i >= 0 && i < Array.getLength(m_instance)) CoerceJavaToLua.coerce(
                Array.get(
                    m_instance,
                    key.toint() - 1
                )
            )!! else NIL!!
        }
        return super.get(key)
    }

    override fun set(key: LuaValue?, value: LuaValue?) {
        val key = key!!
        if (key.isint()) {
            val i = key.toint() - 1
            if (i >= 0 && i < Array.getLength(m_instance)) Array.set(
                m_instance,
                i,
                CoerceLuaToJava.coerce(value, m_instance.javaClass.getComponentType())
            )
            else if (m_metatable == null || !settable(this, key, value)) error("array index out of bounds")
        } else super.set(key, value)
    }

    companion object {
        val LENGTH: LuaValue = valueOf("length")

        val array_metatable: LuaTable

        init {
            array_metatable = LuaTable()
            array_metatable.rawset(LEN, LenFunction())
        }
    }
}
