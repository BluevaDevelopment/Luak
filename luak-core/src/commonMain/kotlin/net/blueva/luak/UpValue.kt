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


/** Upvalue used with Closure formulation
 * 
 * 
 * @see LuaClosure
 * 
 * @see Prototype
 */
class UpValue(stack: Array<LuaValue?>, index: Int) {
    var array: Array<LuaValue?> // initially the stack, becomes a holder 
    var index: Int

    /**
     * Create an upvalue relative to a stack
     * @param stack the stack
     * @param index the index on the stack for the upvalue
     */
    init {
        this.array = stack
        this.index = index
    }

    override fun toString(): String {
        return index.toString() + "/" + array.size + " " + array[index]
    }

    /**
     * Convert this upvalue to a Java String
     * @return the Java String for this upvalue.
     * @see LuaValue.tojstring
     */
    fun tojstring(): String {
        return array[index]!!.tojstring()
    }

    /**
     * Get the value of the upvalue
     * @return the [LuaValue] for this upvalue
     */
    fun getValue(): LuaValue? {
        return array[index]
    }

    /**
     * Set the value of the upvalue
     * @param value the [LuaValue] to set it to
     */
    fun setValue(value: LuaValue?) {
        array[index] = value
    }

    /**
     * Close this upvalue so it is no longer on the stack
     */
    fun close() {
        val old: Array<LuaValue?> = array
        array = arrayOf<LuaValue?>(old[index])
        old[index] = null
        index = 0
    }
}
