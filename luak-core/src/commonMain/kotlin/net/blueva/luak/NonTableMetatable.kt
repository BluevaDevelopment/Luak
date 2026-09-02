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

import net.blueva.luak.LuaTable.Slot

internal class NonTableMetatable(value: LuaValue?) : Metatable {
    private val value: LuaValue?

    init {
        this.value = value
    }

    override fun useWeakKeys(): Boolean {
        return false
    }

    override fun useWeakValues(): Boolean {
        return false
    }

    override fun toLuaValue(): LuaValue? {
        return value
    }

    override fun entry(key: LuaValue?, value: LuaValue?): Slot {
        return LuaTable.defaultEntry(key!!, value!!)
    }

    override fun wrap(value: LuaValue?): LuaValue? {
        return value
    }

    override fun arrayget(array: Array<LuaValue?>?, index: Int): LuaValue? {
        return array!![index]
    }
}
