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
 *  Copyright (c) 2026 Blueva Development
 *
 *  SPDX-License-Identifier: MIT
 ******************************************************************************/
package net.blueva.luak.require

import net.blueva.luak.LuaValue
import net.blueva.luak.lib.TwoArgFunction

/**
 * This should succeed as a library that can be loaded dynamically via "require()"
 */
class RequireSampleSuccess : TwoArgFunction() {
    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue {
        env!!.checkglobals()
        return valueOf("require-sample-success-" + modname!!.tojstring())
    }
}
