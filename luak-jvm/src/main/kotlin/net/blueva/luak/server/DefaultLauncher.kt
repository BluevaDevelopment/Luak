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
package net.blueva.luak.server

import net.blueva.luak.lib.jvm.asLuaReader
import net.blueva.luak.Globals
import net.blueva.luak.LuaValue
import net.blueva.luak.lib.jvm.CoerceJavaToLua
import net.blueva.luak.lib.jvm.JvmPlatform
import java.io.InputStream
import java.io.Reader

/**
 * Default [Launcher] instance that creates standard globals
 * and runs the supplied scripts with chunk name 'main'.
 * <P>
 * Arguments are coerced into lua using [CoerceJavaToLua.coerce].
</P> * <P>
 * Return values with simple types are coerced into Java simple types.
 * Tables, threads, and functions are returned as lua objects.
 * 
 * @see Launcher
 * 
 * @see LuaClassLoader
 * 
 * @see LuaClassLoader.NewLauncher
 * @see LuaClassLoader.NewLauncher
 * @since luaj 3.0.1
</P> */
class DefaultLauncher : Launcher {
    protected var g: Globals

    init {
        g = JvmPlatform.standardGlobals()
    }

    /** Launches the script with chunk name 'main'  */
    override fun launch(script: String?, arg: Array<Any?>?): Array<Any?>? {
        return launchChunk(g.load(script!!, "main")!!, arg)
    }

    /** Launches the script with chunk name 'main' and loading using modes 'bt'  */
    override fun launch(script: InputStream?, arg: Array<Any?>?): Array<Any?>? {
        return launchChunk(g.load(script!!, "main", "bt", g)!!, arg)
    }

    /** Launches the script with chunk name 'main'  */
    override fun launch(script: Reader?, arg: Array<Any?>?): Array<Any?>? {
        return launchChunk(g.load(script!!.asLuaReader(), "main")!!, arg)
    }

    private fun launchChunk(chunk: LuaValue, arg: Array<Any?>?): Array<Any?>? {
        val args: Array<LuaValue?> = arrayOfNulls<LuaValue>(arg?.size ?: 0)
        for (i in args.indices) args[i] = CoerceJavaToLua.coerce(arg?.get(i))
        val results = chunk.invoke(LuaValue.varargsOf(args))

        val n = results.narg()
        val return_values: Array<Any?> = arrayOfNulls<Any>(n)
        for (i in 0 until n) {
            val r = results.arg(i + 1)
            when (r.type()) {
                LuaValue.TBOOLEAN -> return_values[i] = r.toboolean()
                LuaValue.TNUMBER -> return_values[i] = r.todouble()
                LuaValue.TINT -> return_values[i] = r.toint()
                LuaValue.TNIL -> return_values[i] = null
                LuaValue.TSTRING -> return_values[i] = r.tojstring()
                LuaValue.TUSERDATA -> return_values[i] = r.touserdata()
                else -> return_values[i] = r
            }
        }
        return return_values
    }
}
