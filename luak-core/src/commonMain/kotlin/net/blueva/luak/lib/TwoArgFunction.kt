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

import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs

/** Abstract base class for Java function implementations that take two arguments and
 * return one value.
 * 
 * 
 * Subclasses need only implement [LuaValue.call] to complete this class,
 * simplifying development.
 * All other uses of [.call], [.invoke],etc,
 * are routed through this method by this class,
 * dropping or extending arguments with `nil` values as required.
 * 
 * 
 * If more or less than two arguments are required,
 * or variable argument or variable return values,
 * then use one of the related function
 * [ZeroArgFunction], [OneArgFunction], [ThreeArgFunction], or [VarArgFunction].
 * 
 * 
 * See [LibFunction] for more information on implementation libraries and library functions.
 * @see .call
 * @see LibFunction
 * 
 * @see ZeroArgFunction
 * 
 * @see OneArgFunction
 * 
 * @see ThreeArgFunction
 * 
 * @see VarArgFunction
 */
abstract class TwoArgFunction
/** Default constructor  */
    : LibFunction() {
    abstract override fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue?

    override fun call(): LuaValue? {
        return call(NIL, NIL)
    }

    override fun call(arg: LuaValue?): LuaValue? {
        return call(arg, NIL)
    }

    override fun call(arg1: LuaValue?, arg2: LuaValue?, arg3: LuaValue?): LuaValue? {
        return call(arg1, arg2)
    }

    override fun invoke(varargs: Varargs): Varargs {
        return call(varargs.arg1(), varargs.arg(2))!!
    }
}
