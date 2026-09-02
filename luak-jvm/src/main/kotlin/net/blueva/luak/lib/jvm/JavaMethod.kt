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

import net.blueva.luak.LuaError
import net.blueva.luak.LuaFunction
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*

/**
 * LuaValue that represents a Java method.
 * 
 * 
 * Can be invoked via call(LuaValue...) and related methods.
 * 
 * 
 * This class is not used directly.
 * It is returned by calls to calls to [JavaInstance.get]
 * when a method is named.
 * @see CoerceJavaToLua
 * 
 * @see CoerceLuaToJava
 */
internal class JavaMethod private constructor(val method: Method) : JavaMember(
    method.getParameterTypes(), method.getModifiers()
) {
    init {
        try {
            if (!method.isAccessible()) method.setAccessible(true)
        } catch (s: SecurityException) {
        }
    }

    override fun call(): LuaValue {
        return error("method cannot be called without instance")!!
    }

    override fun call(arg: LuaValue?): LuaValue? {
        return invokeMethod(arg!!.checkuserdata(), NONE!!)
    }

    override fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue? {
        return invokeMethod(arg1!!.checkuserdata(), arg2!!)
    }

    override fun call(arg1: LuaValue?, arg2: LuaValue?, arg3: LuaValue?): LuaValue? {
        return invokeMethod(arg1!!.checkuserdata(), varargsOf(arg2, arg3!!)!!)
    }

    override fun invoke(args: Varargs): Varargs {
        return invokeMethod(args.checkuserdata(1), args.subargs(2)!!)!!
    }

    fun invokeMethod(instance: Any?, args: Varargs): LuaValue? {
        val a = convertArgs(args)
        try {
            return CoerceJavaToLua.coerce(method.invoke(instance, *a))
        } catch (e: InvocationTargetException) {
            throw LuaError(e.getTargetException())
        } catch (e: Exception) {
            return error("coercion error " + e)
        }
    }

    /**
     * LuaValue that represents an overloaded Java method.
     * 
     * 
     * On invocation, will pick the best method from the list, and invoke it.
     * 
     * 
     * This class is not used directly.
     * It is returned by calls to calls to [JavaInstance.get]
     * when an overloaded method is named.
     */
    internal class Overload(val methods: Array<JavaMethod?>) : LuaFunction() {
        override fun call(): LuaValue {
            return error("method cannot be called without instance")!!
        }

        override fun call(arg: LuaValue?): LuaValue? {
            return invokeBestMethod(arg!!.checkuserdata(), NONE!!)
        }

        override fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue? {
            return invokeBestMethod(arg1!!.checkuserdata(), arg2!!)
        }

        override fun call(arg1: LuaValue?, arg2: LuaValue?, arg3: LuaValue?): LuaValue? {
            return invokeBestMethod(arg1!!.checkuserdata(), varargsOf(arg2, arg3!!)!!)
        }

        override fun invoke(args: Varargs): Varargs {
            return invokeBestMethod(args.checkuserdata(1), args.subargs(2)!!)!!
        }

        private fun invokeBestMethod(instance: Any?, args: Varargs): LuaValue? {
            var best: JavaMethod? = null
            var score = CoerceLuaToJava.SCORE_UNCOERCIBLE
            for (i in methods.indices) {
                val s = methods[i]!!.score(args)
                if (s < score) {
                    score = s
                    best = methods[i]
                    if (score == 0) break
                }
            }


            // any match? 
            if (best == null) error("no coercible public method")


            // invoke it
            return best!!.invokeMethod(instance, args)
        }
    }

    companion object {
        val methods: MutableMap<Any?, Any?> = Collections.synchronizedMap<Any?, Any?>(HashMap<Any?, Any?>())

        fun forMethod(m: Method): JavaMethod {
            var j = methods.get(m) as JavaMethod?
            if (j == null) methods.put(m, JavaMethod(m).also { j = it })
            return j!!
        }

        fun forMethods(m: Array<JavaMethod?>): LuaFunction {
            return Overload(m)
        }
    }
}
