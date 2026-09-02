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
 * Subclass of [Varargs] that represents a lua tail call
 * in a Java library function execution environment.
 * 
 * 
 * Since Java doesn't have direct support for tail calls,
 * any lua function whose [Prototype] contains the
 * [Lua.OP_TAILCALL] bytecode needs a mechanism
 * for tail calls when converting lua-bytecode to java-bytecode.
 * 
 * 
 * The tail call holds the next function and arguments,
 * and the client a call to [.eval] executes the function
 * repeatedly until the tail calls are completed.
 * 
 * 
 * Normally, users of Luak need not concern themselves with the
 * details of this mechanism, as it is built into the core
 * execution framework.
 * @see Prototype
 * 
 * @see net.blueva.luak.luajc.LuaJC
 */
class TailcallVarargs : Varargs {
    private var func: LuaValue?
    private var args: Varargs?
    private var result: Varargs? = null

    constructor(f: LuaValue?, args: Varargs?) {
        this.func = f
        this.args = args
    }

    constructor(`object`: LuaValue, methodname: LuaValue?, args: Varargs?) {
        this.func = `object`.get((methodname)!!)
        this.args = LuaValue.varargsOf(`object`, (args)!!)
    }

    override val isTailcall: Boolean
        get() = true

    override fun eval(): Varargs {
        while (result == null) {
            val r: Varargs = func!!.onInvoke(args!!)!!
            if (r.isTailcall) {
                val t = r as TailcallVarargs
                func = t.func
                args = t.args
            } else {
                result = r
                func = null
                args = null
            }
        }
        return result!!
    }

    override suspend fun evalSuspend(): Varargs {
        while (result == null) {
            val r: Varargs = func!!.onInvokeSuspend(args!!)!!
            if (r.isTailcall) {
                val t = r as TailcallVarargs
                func = t.func
                args = t.args
            } else {
                result = r
                func = null
                args = null
            }
        }
        return result!!
    }

    override fun arg(i: Int): LuaValue {
        if (result == null) eval()
        return result!!.arg(i)
    }

    override fun arg1(): LuaValue {
        if (result == null) eval()
        return result!!.arg1()
    }

    override fun narg(): Int {
        if (result == null) eval()
        return result!!.narg()
    }

    override fun subargs(start: Int): Varargs? {
        if (result == null) eval()
        return result!!.subargs(start)
    }
}
