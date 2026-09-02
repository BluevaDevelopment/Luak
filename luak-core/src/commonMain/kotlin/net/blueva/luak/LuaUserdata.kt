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

open class LuaUserdata : LuaValue {
    /** See [LuaValue.gckeeper]; a userdata is one of the two kinds that can have one. */
    internal override var gckeeper: Any? = null

    /** See [LuaValue.pinned]; a value of this kind can be a weak key. */
    internal override var pinned: Any? = null

    var m_instance: Any
    var m_metatable: LuaValue? = null

    constructor(obj: Any) {
        m_instance = obj
    }

    constructor(obj: Any, metatable: LuaValue?) {
        m_instance = obj
        m_metatable = metatable
    }

    override fun tojstring(): String {
        return (m_instance).toString()
    }

    override fun type(): Int {
        return LuaValue.TUSERDATA
    }

    override fun typename(): String? {
        return "userdata"
    }

    override fun hashCode(): Int {
        return m_instance.hashCode()
    }

    fun userdata(): Any {
        return m_instance
    }

    override fun isuserdata(): Boolean {
        return true
    }

    override fun isuserdata(c: KClass<*>?): Boolean {
        return c!!.isInstance(m_instance)
    }

    override fun touserdata(): Any {
        return m_instance
    }

    override fun touserdata(c: KClass<*>?): Any? {
        return if (c!!.isInstance(m_instance)) m_instance else null
    }

    override fun optuserdata(defval: Any?): Any {
        return m_instance
    }

    override fun optuserdata(c: KClass<*>, defval: Any?): Any {
        if (!c!!.isInstance(m_instance)) typerror(platformTypeName(c))
        return m_instance
    }

    override fun getmetatable(): LuaValue? {
        return m_metatable
    }

    override fun setmetatable(metatable: LuaValue?): LuaValue? {
        this.m_metatable = metatable
        return this
    }

    override fun checkuserdata(): Any {
        return m_instance
    }

    override fun checkuserdata(c: KClass<*>?): Any {
        if (c!!.isInstance(m_instance)) return m_instance
        return (typerror(platformTypeName(c)))!!
    }

    override fun get(key: LuaValue): LuaValue {
        return if (m_metatable != null) LuaValue.gettable(this, key) else NIL
    }

    override fun set(key: LuaValue?, value: LuaValue?) {
        if (m_metatable == null || !LuaValue.settable(this, key, value)) error("cannot set " + key + " for userdata")
    }

    override fun equals(`val`: Any?): Boolean {
        if (this === `val`) return true
        if (`val` !is LuaUserdata) return false
        val u = `val`
        return m_instance.equals(u.m_instance)
    }

    // equality w/ metatable processing
    override fun eq(`val`: LuaValue?): LuaValue {
        val `val` = `val`!!
        return (if (eq_b(`val`)) TRUE else FALSE)!!
    }

    override fun eq_b(`val`: LuaValue?): Boolean {
        val `val` = `val`!!
        if (`val`.raweq(this)) return true
        if (!`val`.isuserdata()) return false
        return LuaValue.eqmtcall(this, `val`)
    }

    // equality w/o metatable processing
    override fun raweq(`val`: LuaValue?): Boolean {
        val `val` = `val`!!
        return `val`.raweq(this)
    }

    override fun raweq(`val`: LuaUserdata?): Boolean {
        val `val` = `val`!!
        return this === `val` || (m_metatable === `val`.m_metatable && m_instance.equals(`val`.m_instance))
    }

    // __eq metatag processing
    fun eqmt(`val`: LuaValue): Boolean {
        return `val`.isuserdata() && LuaValue.eqmtcall(this, `val`)
    }
}
