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
package net.blueva.luak

/**
 * A bare host reference: Lua's light userdata.
 *
 * Unlike a full userdata it is only an identity - no metatable of its own and
 * no value attached to it - which is what `debug.upvalueid` hands back so that
 * two upvalues can be told apart or found to be the same one. `type()` reports
 * it as `userdata`, as Lua does; only an argument error names it as light,
 * which is how a script finds out it was given one where a full userdata was
 * wanted.
 */
class LuaLightUserdata(obj: Any) : LuaUserdata(obj) {
    override fun tojstring(): String = "userdata: 0x" + m_instance.hashCode().toString(16)

    override fun getmetatable(): LuaValue? = null

    override fun setmetatable(metatable: LuaValue?): LuaValue? {
        LuaValue.error("cannot change a light userdata's metatable")
        return null
    }

    // Identity, not equality: two light userdata are the same one only when
    // they point at the same thing.
    override fun raweq(`val`: LuaValue?): Boolean =
        `val` is LuaLightUserdata && `val`.m_instance === m_instance

    override fun raweq(`val`: LuaUserdata?): Boolean =
        `val` is LuaLightUserdata && `val`.m_instance === m_instance

    override fun hashCode(): Int = m_instance.hashCode()
}
