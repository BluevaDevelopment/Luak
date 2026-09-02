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
package net.blueva.luak.lib.jvm

import net.blueva.luak.io.Reader as LuaReader
import java.io.Reader

internal fun Reader.asLuaReader(): LuaReader = object : LuaReader() {
    override fun read(chars: CharArray, offset: Int, length: Int): Int =
        this@asLuaReader.read(chars, offset, length)

    override fun close() = this@asLuaReader.close()
}
