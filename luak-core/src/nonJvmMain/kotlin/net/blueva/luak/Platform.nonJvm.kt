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
package net.blueva.luak

import kotlin.reflect.KClass

internal actual fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
internal actual fun platformProperty(name: String): String? = null
internal actual fun platformExit(code: Int) = Unit
internal actual fun platformCollectGarbage() = Unit

// Nothing here can bring back an object the host is reclaiming, so an object
// is never handed to its `__gc` handler and the handler never runs.
internal actual fun watchForFinalization(target: LuaValue, pending: MutableList<LuaValue>): Any? = null

internal actual fun takeFinalized(pending: MutableList<LuaValue>): List<LuaValue> = emptyList()
internal actual fun platformUsedMemory(): Long = 0L
internal actual fun platformLoadLibrary(className: String, globals: Globals): LuaValue? = null
internal actual fun platformTypeName(type: KClass<*>): String = type.simpleName ?: "userdata"

/**
 * Always false: neither JavaScript nor Wasm reports exhaustion as a throwable
 * this code can recognise, so there is nothing to translate.
 */
internal actual fun platformIsStackOverflow(failure: Throwable): Boolean = false
