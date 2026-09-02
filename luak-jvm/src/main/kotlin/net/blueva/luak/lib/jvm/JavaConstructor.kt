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
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs
import net.blueva.luak.lib.VarArgFunction
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.util.*

/**
 * LuaValue that represents a particular public Java constructor.
 * 
 * 
 * May be called with arguments to return a JavaInstance
 * created by calling the constructor.
 * 
 * 
 * This class is not used directly.
 * It is returned by calls to [JavaClass.new]
 * when the value of key is "new".
 * @see CoerceJavaToLua
 * 
 * @see CoerceLuaToJava
 */
internal class JavaConstructor private constructor(val constructor: Constructor<*>) : JavaMember(
    constructor.getParameterTypes(), constructor.getModifiers()
) {
    override fun invoke(args: Varargs): Varargs {
        val a = convertArgs(args)
        try {
            return CoerceJavaToLua.coerce(constructor.newInstance(*a))!!
        } catch (e: InvocationTargetException) {
            throw LuaError(e.getTargetException())
        } catch (e: Exception) {
            return error("coercion error " + e)!!
        }
    }

    /**
     * LuaValue that represents an overloaded Java constructor.
     * 
     * 
     * On invocation, will pick the best method from the list, and invoke it.
     * 
     * 
     * This class is not used directly.
     * It is returned by calls to calls to [JavaClass.get]
     * when key is "new" and there is more than one public constructor.
     */
    internal class Overload(val constructors: Array<JavaConstructor?>) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            var best: JavaConstructor? = null
            var score = CoerceLuaToJava.SCORE_UNCOERCIBLE
            for (i in constructors.indices) {
                val s = constructors[i]!!.score(args)
                if (s < score) {
                    score = s
                    best = constructors[i]
                    if (score == 0) break
                }
            }


            // any match?
            if (best == null) error("no coercible public method")


            // invoke it
            return best!!.invoke(args)
        }
    }

    companion object {
        val constructors: MutableMap<Any?, Any?> = Collections.synchronizedMap<Any?, Any?>(HashMap<Any?, Any?>())

        fun forConstructor(c: Constructor<*>): JavaConstructor {
            var j = constructors.get(c) as JavaConstructor?
            if (j == null) constructors.put(c, JavaConstructor(c).also { j = it })
            return j!!
        }

        fun forConstructors(array: Array<JavaConstructor?>): LuaValue {
            return Overload(array)
        }
    }
}
