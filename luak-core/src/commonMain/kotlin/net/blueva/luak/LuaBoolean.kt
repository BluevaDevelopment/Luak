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

/**
 * Extension of [LuaValue] which can hold a Java boolean as its value.
 * 
 * 
 * These instance are not instantiated directly by clients.
 * Instead, there are exactly twon instances of this class,
 * [LuaValue.TRUE] and [LuaValue.FALSE]
 * representing the lua values `true` and `false`.
 * The function [LuaValue.valueOf] will always
 * return one of these two values.
 * 
 * 
 * Any [LuaValue] can be converted to its equivalent
 * boolean representation using [LuaValue.toboolean]
 * 
 * 
 * @see LuaValue
 * 
 * @see LuaValue.valueOf
 * @see LuaValue.TRUE
 * 
 * @see LuaValue.FALSE
 */
class LuaBoolean internal constructor(
    /** The value of the boolean  */
    val v: Boolean
) : LuaValue() {
    override fun type(): Int {
        return LuaValue.TBOOLEAN
    }

    override fun typename(): String {
        return "boolean"
    }

    override fun isboolean(): Boolean {
        return true
    }

    override fun not(): LuaValue {
        return (if (v) FALSE else LuaValue.TRUE)!!
    }

    /**
     * Return the boolean value for this boolean
     * @return value as a Java boolean
     */
    fun booleanValue(): Boolean {
        return v
    }

    override fun toboolean(): Boolean {
        return v
    }

    override fun tojstring(): String {
        return if (v) "true" else "false"
    }

    override fun optboolean(defval: Boolean): Boolean {
        return this.v
    }

    override fun checkboolean(): Boolean {
        return v
    }

    override fun getmetatable(): LuaValue? {
        return net.blueva.luak.LuaBoolean.Companion.s_metatable
    }

    companion object {
        /** The singleton instance representing lua `true`  */
        val _TRUE: LuaBoolean = net.blueva.luak.LuaBoolean(true)

        /** The singleton instance representing lua `false`  */
        val _FALSE: LuaBoolean = net.blueva.luak.LuaBoolean(false)

        /** Shared static metatable for boolean values represented in lua.  */
        var s_metatable: LuaValue? = null
    }
}
