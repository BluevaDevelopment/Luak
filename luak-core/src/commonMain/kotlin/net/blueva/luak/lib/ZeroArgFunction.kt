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

/** Abstract base class for Java function implementations that take no arguments and
 * return one value.
 * 
 * 
 * Subclasses need only implement [LuaValue.call] to complete this class,
 * simplifying development.
 * All other uses of [.call], [.invoke],etc,
 * are routed through this method by this class.
 * 
 * 
 * If one or more arguments are required, or variable argument or variable return values,
 * then use one of the related function
 * [OneArgFunction], [TwoArgFunction], [ThreeArgFunction], or [VarArgFunction].
 * 
 * 
 * See [LibFunction] for more information on implementation libraries and library functions.
 * @see .call
 * @see LibFunction
 * 
 * @see OneArgFunction
 * 
 * @see TwoArgFunction
 * 
 * @see ThreeArgFunction
 * 
 * @see VarArgFunction
 */
abstract class ZeroArgFunction
/** Default constructor  */
    : LibFunction() {
    abstract override fun call(): LuaValue?

    override fun call(arg: LuaValue?): LuaValue? {
        return call()!!
    }

    override fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue? {
        return call()
    }

    override fun call(arg1: LuaValue?, arg2: LuaValue?, arg3: LuaValue?): LuaValue? {
        return call()
    }

    override fun invoke(varargs: Varargs): Varargs {
        return call()!!
    }
}
