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
 * Data class to hold debug information relating to local variables for a [Prototype]
 */
class LocVars(varname: LuaString?, startpc: Int, endpc: Int) {
    /** The local variable name  */
    var varname: LuaString?

    /** The instruction offset when the variable comes into scope  */
    var startpc: Int

    /** The instruction offset when the variable goes out of scope  */
    var endpc: Int

    /**
     * Construct a LocVars instance.
     * @param varname The local variable name
     * @param startpc The instruction offset when the variable comes into scope
     * @param endpc The instruction offset when the variable goes out of scope
     */
    init {
        this.varname = varname
        this.startpc = startpc
        this.endpc = endpc
    }

    fun tojstring(): String? {
        return varname.toString() + " " + startpc + "-" + endpc
    }
}
