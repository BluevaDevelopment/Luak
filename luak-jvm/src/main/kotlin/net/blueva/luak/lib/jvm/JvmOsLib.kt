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

import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs
import net.blueva.luak.lib.OsLib
import java.io.IOException

/**
 * Subclass of [OsLib] which adds the one part of the standard lua `os`
 * library that has no portable implementation: `os.execute`.
 *
 * `os.getenv`, `os.remove`, `os.rename`, and `os.tmpname` are handled by the
 * shared [OsLib] in `luak-core` and behave the same on every Kotlin
 * Multiplatform target; only running a shell command needs the JVM.
 *
 * Typically this library is included as part of a call to
 * [JvmPlatform.standardGlobals]:
 * ```kotlin
 * val globals = JvmPlatform.standardGlobals()
 * println(globals.get("os").get("time").call())
 * ```
 *
 * @see OsLib
 *
 * @see JvmPlatform
 *
 * @see [Lua 5.2 OS Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.9)
 */
class JvmOsLib
/** public constructor  */
    : OsLib() {
    override fun hasshell(): Boolean = true

    override fun execute(command: String?): Varargs {
        var exitValue: Int
        try {
            exitValue = JvmProcess(command, null, globals!!.STDOUT, globals!!.STDERR).waitFor()
        } catch (ioe: IOException) {
            exitValue = EXEC_IOEXCEPTION
        } catch (e: InterruptedException) {
            exitValue = EXEC_INTERRUPTED
        } catch (t: Throwable) {
            exitValue = EXEC_ERROR
        }
        // A command that was killed leaves 128 plus the signal behind, which
        // is the convention every shell reports it by; anything else is an
        // ordinary exit and the number is the status it exited with.
        if (exitValue > 128) return varargsOf(NIL, valueOf("signal"), valueOf(exitValue - 128))
        if (exitValue == 0) return LuaValue.varargsOf(TRUE, valueOf("exit"), ZERO!!)
        return varargsOf(NIL, valueOf("exit"), valueOf(exitValue))
    }

    companion object {
        /** return code indicating the execute() threw an I/O exception  */
        const val EXEC_IOEXCEPTION: Int = 1

        /** return code indicating the execute() was interrupted  */
        val EXEC_INTERRUPTED: Int = -2

        /** return code indicating the execute() threw an unknown exception  */
        val EXEC_ERROR: Int = -3
    }
}
