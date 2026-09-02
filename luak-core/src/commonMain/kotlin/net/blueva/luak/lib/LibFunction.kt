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
package net.blueva.luak.lib

import net.blueva.luak.LuaError
import net.blueva.luak.LuaFunction
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs

/**
 * Subclass of [LuaFunction] common to Java functions exposed to lua.
 * 
 * 
 * To provide for common implementations in JME and JVM,
 * library functions are typically grouped on one or more library classes
 * and an opcode per library function is defined and used to key the switch
 * to the correct function within the library.
 * 
 * 
 * Since lua functions can be called with too few or too many arguments,
 * and there are overloaded [LuaValue.call] functions with varying
 * number of arguments, a Java function exposed in lua needs to handle  the
 * argument fixup when a function is called with a number of arguments
 * differs from that expected.
 * 
 * 
 * To simplify the creation of library functions,
 * there are 5 direct subclasses to handle common cases based on number of
 * argument values and number of return return values.
 * 
 *  * [ZeroArgFunction]
 *  * [OneArgFunction]
 *  * [TwoArgFunction]
 *  * [ThreeArgFunction]
 *  * [VarArgFunction]
 * 
 * 
 * 
 * To be a Java library that can be loaded via `require`, it should have
 * a public constructor that returns a [LuaValue] that, when executed,
 * initializes the library.
 * 
 * 
 * For example, the following code will implement a library called "hyperbolic"
 * with two functions, "sinh", and "cosh":
 * <pre> `import net.blueva.luak.LuaValue; import net.blueva.luak.lib.*; public class hyperbolic extends TwoArgFunction {	public hyperbolic() {}	public LuaValue call(LuaValue modname, LuaValue env) {		LuaValue library = tableOf();		library.set( "sinh", new sinh() );		library.set( "cosh", new cosh() );		env.set( "hyperbolic", library );		return library;	}	static class sinh extends OneArgFunction {		public LuaValue call(LuaValue x) {			return LuaValue.valueOf(Math.sinh(x.checkdouble()));		}	}	static class cosh extends OneArgFunction {		public LuaValue call(LuaValue x) {			return LuaValue.valueOf(Math.cosh(x.checkdouble()));		}	}}`</pre>
 * The default constructor is used to instantiate the library
 * in response to `require 'hyperbolic'` statement,
 * provided it is on Java&quot;s class path.
 * This instance is then invoked with 2 arguments: the name supplied to require(),
 * and the environment for this function.  The library may ignore these, or use
 * them to leave side effects in the global environment, for example.
 * In the previous example, two functions are created, 'sinh', and 'cosh', and placed
 * into a global table called 'hyperbolic' using the supplied 'env' argument.
 * 
 * 
 * To test it, a script such as this can be used:
 * <pre> `local t = require('hyperbolic') print( 't', t ) print( 'hyperbolic', hyperbolic ) for k,v in pairs(t) do 	print( 'k,v', k,v ) end print( 'sinh(.5)', hyperbolic.sinh(.5) ) print( 'cosh(.5)', hyperbolic.cosh(.5) ) `</pre>
 * 
 * 
 * It should produce something like:
 * <pre> `t	table: 3dbbd23f hyperbolic	table: 3dbbd23f k,v	cosh	function: 3dbbd128 k,v	sinh	function: 3dbbd242 sinh(.5)	0.5210953 cosh(.5)	1.127626 `</pre>
 * 
 * 
 * See the source code in any of the library functions
 * such as [BaseLib] or [TableLib] for other examples.
 */
abstract class LibFunction
/** Default constructor for use by subclasses  */
protected constructor() : LuaFunction() {
    /** User-defined opcode to differentiate between instances of the library function class.
     * 
     * 
     * Subclass will typicall switch on this value to provide the specific behavior for each function.
     */
    protected var opcode: Int = 0

    /** The common name for this function, useful for debugging.
     * 
     * 
     * Binding functions initialize this to the name to which it is bound.
     */
    protected var name: String? = null

    override fun tojstring(): String {
        return if (name != null) "function: " + name else super.tojstring()
    }

    /**
     * Bind a set of library functions.
     * 
     * 
     * An array of names is provided, and the first name is bound
     * with opcode = 0, second with 1, etc.
     * @param env The environment to apply to each bound function
     * @param factory the Class to instantiate for each bound function
     * @param names array of String names, one for each function.
     * @see .bind
     */
    protected fun bind(env: LuaValue, factory: () -> LibFunction, names: Array<String?>) {
        bind(env, factory, names, 0)
    }

    /**
     * Bind a set of library functions, with an offset
     * 
     * 
     * An array of names is provided, and the first name is bound
     * with opcode = `firstopcode`, second with `firstopcode+1`, etc.
     * @param env The environment to apply to each bound function
     * @param factory the Class to instantiate for each bound function
     * @param names array of String names, one for each function.
     * @param firstopcode the first opcode to use
     * @see .bind
     */
    protected fun bind(env: LuaValue, factory: () -> LibFunction, names: Array<String?>, firstopcode: Int) {
        try {
            var i = 0
            val n = names.size
            while (i < n) {
                val f = factory()
                f.opcode = firstopcode + i
                f.name = names[i]
                env.set(f.name, f)
                i++
            }
        } catch (e: Exception) {
            throw LuaError("bind failed: " + e)
        }
    }

    open override fun call(): LuaValue? {
        return (argerror(1, "value expected"))!!
    }

    open override fun call(a: LuaValue?): LuaValue? {
        return call()!!
    }

    open override fun call(a: LuaValue?, b: LuaValue?): LuaValue? {
        return call(a)
    }

    open override fun call(a: LuaValue?, b: LuaValue?, c: LuaValue?): LuaValue? {
        return call(a, b)
    }

    open fun call(a: LuaValue?, b: LuaValue?, c: LuaValue?, d: LuaValue?): LuaValue? {
        return call(a, b, c)
    }

    open override fun invoke(args: Varargs): Varargs {
        when (args.narg()) {
            0 -> return call()!!
            1 -> return call(args.arg1())!!
            2 -> return call(args.arg1(), args.arg(2))!!
            3 -> return call(args.arg1(), args.arg(2), args.arg(3))!!
            else -> return call(args.arg1(), args.arg(2), args.arg(3), args.arg(4))!!
        }
    }

    companion object {
        /** Java code generation utility to allocate storage for upvalue, leave it empty  */
        @kotlin.jvm.JvmStatic
        protected fun newupe(): Array<LuaValue?> {
            return arrayOfNulls<LuaValue>(1)
        }

        /** Java code generation utility to allocate storage for upvalue, initialize with nil  */
        @kotlin.jvm.JvmStatic
        protected fun newupn(): Array<LuaValue?> {
            return arrayOf<LuaValue?>(NIL)
        }

        /** Java code generation utility to allocate storage for upvalue, initialize with value  */
        @kotlin.jvm.JvmStatic
        protected fun newupl(v: LuaValue?): Array<LuaValue?> {
            return arrayOf<LuaValue?>(v)
        }
    }
}
