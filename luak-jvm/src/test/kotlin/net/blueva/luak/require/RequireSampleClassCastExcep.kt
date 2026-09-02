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

/**
 * This should fail while trying to load via "require() because it is not a LibFunction"
 * 
 */
class RequireSampleClassCastExcep {
    fun call(): LuaValue {
        return LuaValue.valueOf("require-sample-class-cast-excep")
    }
}
