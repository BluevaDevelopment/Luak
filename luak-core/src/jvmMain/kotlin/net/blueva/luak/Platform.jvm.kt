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

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
internal actual fun platformProperty(name: String): String? = System.getProperty(name)
internal actual fun platformEnvironment(name: String): String? = System.getenv(name)
internal actual fun platformExit(code: Int) = System.exit(code)
internal actual fun platformCollectGarbage() = System.gc()

/**
 * Reached from the object it watches, so it is collected along with it.
 *
 * Its own strong reference to the object is what brings the object back when
 * the host is about to reclaim it, which is the only way to hand a `__gc`
 * handler the object it is being asked to finalize. The host runs this once
 * and only once, which is also how often Lua runs a finalizer.
 */
private class Finalizable(
    private val target: LuaValue,
    private val pending: MutableList<LuaValue>,
) {
    @Suppress("removal", "DEPRECATION")
    protected fun finalize() {
        // Another thread entirely, so the list is the handover point and
        // nothing else here touches Lua.
        synchronized(pending) { pending.add(target) }
    }
}

internal actual fun watchForFinalization(target: LuaValue, pending: MutableList<LuaValue>): Any? =
    Finalizable(target, pending)

internal actual fun takeFinalized(pending: MutableList<LuaValue>): List<LuaValue> =
    synchronized(pending) {
        if (pending.isEmpty()) {
            emptyList()
        } else {
            val taken: List<LuaValue> = ArrayList(pending)
            pending.clear()
            taken
        }
    }
internal actual fun platformUsedMemory(): Long = Runtime.getRuntime().run { totalMemory() - freeMemory() }
internal actual fun platformLoadLibrary(className: String, globals: Globals): LuaValue? {
    val value = Class.forName(className).getDeclaredConstructor().newInstance() as? LuaValue ?: return null
    if (value is LuaFunction) value.initupvalue1(globals)
    return value
}
internal actual fun platformTypeName(type: KClass<*>): String =
    type.qualifiedName ?: type.simpleName ?: "userdata"

internal actual fun platformIsStackOverflow(failure: Throwable): Boolean =
    // A class first reached at the bottom of an exhausted stack cannot be
    // initialised, and stays that way for the rest of the run: every later use
    // of it raises a LinkageError instead. That is the same exhaustion showing
    // up a step later, so it is reported as such rather than as a host fault.
    failure is StackOverflowError || failure is LinkageError
