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

import kotlin.reflect.KClass
/**
 * Class to encapsulate behavior of the singleton instance `nil`
 * 
 * 
 * There will be one instance of this class, [LuaValue.NIL],
 * per Java virtual machine.
 * However, the [Varargs] instance [LuaValue.NONE]
 * which is the empty list,
 * is also considered treated as a nil value by default.
 * 
 * 
 * Although it is possible to test for nil using Java == operator,
 * the recommended approach is to use the method [LuaValue.isnil]
 * instead.  By using that any ambiguities between
 * [LuaValue.NIL] and [LuaValue.NONE] are avoided.
 * @see LuaValue
 * 
 * @see LuaValue.NIL
 */
open class LuaNil internal constructor() : LuaValue() {
    override fun type(): Int {
        return LuaValue.TNIL
    }

    override fun toString(): String {
        return "nil"
    }

    override fun typename(): String? {
        return "nil"
    }

    override fun tojstring(): String {
        return "nil"
    }

    override fun not(): LuaValue {
        return (LuaValue.TRUE)!!
    }

    override fun toboolean(): Boolean {
        return false
    }

    override fun isnil(): Boolean {
        return true
    }

    override fun getmetatable(): LuaValue? {
        return net.blueva.luak.LuaNil.Companion.s_metatable
    }

    override fun equals(o: Any?): Boolean {
        return o is LuaNil
    }

    override fun checknotnil(): LuaValue {
        return (argerror("value"))!!
    }

    override fun isvalidkey(): Boolean {
        return false
    }

    // optional argument conversions - nil alwas falls badk to default value
    override fun optboolean(defval: Boolean): Boolean {
        return defval
    }

    override fun optclosure(defval: LuaClosure?): LuaClosure? {
        return defval
    }

    override fun optdouble(defval: Double): Double {
        return defval
    }

    override fun optfunction(defval: LuaFunction?): LuaFunction? {
        return defval
    }

    override fun optint(defval: Int): Int {
        return defval
    }

    override fun optinteger(defval: LuaInteger?): LuaInteger? {
        return defval
    }

    override fun optlong(defval: Long): Long {
        return defval
    }

    override fun optnumber(defval: LuaNumber?): LuaNumber? {
        return defval
    }

    override fun opttable(defval: LuaTable?): LuaTable? {
        return defval
    }

    override fun optthread(defval: LuaThread?): LuaThread? {
        return defval
    }

    override fun optjstring(defval: String?): String? {
        return defval
    }

    override fun optstring(defval: LuaString?): LuaString? {
        return defval
    }

    override fun optuserdata(defval: Any?): Any? {
        return defval
    }

    override fun optuserdata(c: KClass<*>, defval: Any?): Any? {
        return defval
    }

    override fun optvalue(defval: LuaValue?): LuaValue {
        return (defval)!!
    }

    companion object {
        val _NIL: LuaNil = net.blueva.luak.LuaNil()

        var s_metatable: LuaValue? = null
    }
}
