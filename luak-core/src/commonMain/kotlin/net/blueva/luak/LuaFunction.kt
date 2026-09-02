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
 * Base class for functions implemented in Java.
 * 
 * 
 * Direct subclass include [net.blueva.luak.lib.LibFunction]
 * which is the base class for
 * all built-in library functions coded in Java,
 * and [LuaClosure], which represents a lua closure
 * whose bytecode is interpreted when the function is invoked.
 * @see LuaValue
 * 
 * @see LuaClosure
 * 
 * @see net.blueva.luak.lib.LibFunction
 */
abstract
class LuaFunction : LuaValue() {
    /** See [LuaValue.pinned]; a function can be a weak key. */
    internal override var pinned: Any? = null

    /**
     * How many pieces of state this function carries: its upvalues.
     *
     * A Lua closure counts what it captured. A function of the library's own
     * counts the state it was built with, which is none for most of them and
     * one for the few - an iterator, a wrapper - that exist only to carry a
     * position or a handle from one call to the next.
     */
    open fun nupvalues(): Int = 0

    /**
     * The state behind upvalue [n], counted from one.
     *
     * Only its identity is meant to be used: it is what `debug.upvalueid`
     * answers with, so two functions can be found to share state.
     */
    open fun upvaluestate(n: Int): Any? = null

    override fun type(): Int {
        return TFUNCTION
    }

    override fun typename(): String? {
        return "function"
    }

    override fun isfunction(): Boolean {
        return true
    }

    override fun checkfunction(): LuaFunction? {
        return this
    }

    override fun optfunction(defval: LuaFunction?): LuaFunction? {
        return this
    }

    override fun getmetatable(): LuaValue? {
        return net.blueva.luak.LuaFunction.Companion.s_metatable
    }

    override fun tojstring(): String {
        return "function: " + classnamestub()
    }

    override fun strvalue(): LuaString? {
        return valueOf(tojstring())
    }

    /** Return the last part of the class name, to be used as a function name in tojstring and elsewhere.
     * @return String naming the last part of the class name after the last dot (.) or dollar sign ($).
     * If the first character is '_', it is skipped.
     */
    fun classnamestub(): String {
        val s: String = this::class.simpleName ?: "function"
        var offset: Int = maxOf(s.lastIndexOf('.'), s.lastIndexOf('$')) + 1
        if (s[offset] == '_') offset++
        return s.substring(offset)
    }

    /** Return a human-readable name for this function.  Returns the last part of the class name by default.
     * Is overridden by LuaClosure to return the source file and line, and by LibFunctions to return the name.
     * @return common name for this function.
     */
    open fun name(): String {
        return classnamestub()
    }

    companion object {
        /** Shared static metatable for all functions and closures.  */
        var s_metatable: LuaValue? = null
    }
}
