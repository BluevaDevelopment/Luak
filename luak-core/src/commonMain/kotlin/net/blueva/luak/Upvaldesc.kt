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

class Upvaldesc(name: LuaString?, instack: Boolean, idx: Int, kind: Int = 0) {
    /* upvalue name (for debug information) */
    var name: LuaString?

    /* whether it is in stack */
    val instack: Boolean

    /* index of upvalue (in stack or in outer function's list) */
    val idx: Short

    /**
     * How the captured variable was declared: plain, `<const>` or `<close>`.
     *
     * Carried through so that assigning to a `<const>` from an inner function -
     * where it is an upvalue rather than a local - is caught just the same.
     */
    val kind: Int

    init {
        this.name = name
        this.instack = instack
        this.idx = idx.toShort()
        this.kind = kind
    }

    override fun toString(): String {
        return idx.toString() + (if (instack) " instack " else " closed ") + (name ?: "null")
    }
}
