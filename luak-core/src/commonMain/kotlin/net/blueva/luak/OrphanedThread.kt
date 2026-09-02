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
package net.blueva.luak

/**
 * [Error] sublcass that indicates a lua thread that is no
 * longer referenced has been detected.
 * 
 * 
 * The java thread in which this is thrown should correspond to a
 * [LuaThread] being used as a coroutine that could not possibly be
 * resumed again because there are no more references to the LuaThread with
 * which it is associated. Rather than locking up resources forever, this error
 * is thrown, and should fall through all the way to the thread's [Thread.run] method.
 * 
 * 
 * Host code mixed with the Luak vm should not catch this error because it may
 * occur when the coroutine is not running, so any processing done during error
 * handling could break the thread-safety of the application because other lua
 * processing could be going on in a different thread.
 */
class OrphanedThread : Error("orphaned thread")
