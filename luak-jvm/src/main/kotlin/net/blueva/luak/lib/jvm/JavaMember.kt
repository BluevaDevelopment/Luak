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
package net.blueva.luak.lib.jvm

import net.blueva.luak.Varargs
import net.blueva.luak.lib.VarArgFunction
import kotlin.math.max

/**
 * Java method or constructor.
 * 
 * 
 * Primarily handles argument coercion for parameter lists including scoring of compatibility and
 * java varargs handling.
 * 
 * 
 * This class is not used directly.
 * It is an abstract base class for [JavaConstructor] and [JavaMethod].
 * @see JavaConstructor
 * 
 * @see JavaMethod
 * 
 * @see CoerceJavaToLua
 * 
 * @see CoerceLuaToJava
 */
internal abstract class JavaMember protected constructor(params: Array<Class<*>>, modifiers: Int) : VarArgFunction() {
    val fixedargs: Array<CoerceLuaToJava.Coercion?>
    val varargs: CoerceLuaToJava.Coercion?

    init {
        val isvarargs = ((modifiers and METHOD_MODIFIERS_VARARGS) != 0)
        fixedargs = arrayOfNulls<CoerceLuaToJava.Coercion>(if (isvarargs) params.size - 1 else params.size)
        for (i in fixedargs.indices) fixedargs[i] = CoerceLuaToJava.getCoercion(params[i])
        varargs = if (isvarargs) CoerceLuaToJava.getCoercion(params[params.size - 1]) else null
    }

    fun score(args: Varargs): Int {
        val n = args.narg()
        var s = if (n > fixedargs.size) CoerceLuaToJava.SCORE_WRONG_TYPE * (n - fixedargs.size) else 0
        for (j in fixedargs.indices) s += fixedargs[j]!!.score(args.arg(j + 1))
        if (varargs != null) for (k in fixedargs.size..<n) s += varargs.score(args.arg(k + 1))
        return s
    }

    protected fun convertArgs(args: Varargs): Array<Any?> {
        val a: Array<Any?>
        if (varargs == null) {
            a = arrayOfNulls<Any>(fixedargs.size)
            for (i in a.indices) a[i] = fixedargs[i]!!.coerce(args.arg(i + 1))
        } else {
            val n = max(fixedargs.size, args.narg())
            a = arrayOfNulls<Any>(n)
            for (i in fixedargs.indices) a[i] = fixedargs[i]!!.coerce(args.arg(i + 1))
            for (i in fixedargs.size..<n) a[i] = varargs.coerce(args.arg(i + 1))
        }
        return a
    }

    companion object {
        const val METHOD_MODIFIERS_VARARGS: Int = 0x80
    }
}
