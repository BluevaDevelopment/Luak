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

/**
 * Provides operations that depend on the __mode key of the metatable.
 */
interface Metatable {
    /** Return whether or not this table's keys are weak.  */
    fun useWeakKeys(): Boolean

    /** Return whether or not this table's values are weak.  */
    fun useWeakValues(): Boolean

    /** Return this metatable as a LuaValue.  */
    fun toLuaValue(): LuaValue?

    /** Return an instance of Slot appropriate for the given key and value.  */
    fun entry(key: LuaValue?, value: LuaValue?): Slot?

    /** Returns the given value wrapped in a weak reference if appropriate.  */
    fun wrap(value: LuaValue?): LuaValue?

    /**
     * Returns the value at the given index in the array, or null if it is a weak reference that
     * has been dropped.
     */
    fun arrayget(array: Array<LuaValue?>?, index: Int): LuaValue?
}
