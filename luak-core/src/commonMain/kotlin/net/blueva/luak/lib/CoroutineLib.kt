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

import net.blueva.luak.Globals
import net.blueva.luak.LuaError
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaThread
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs

/**
 * Subclass of [LibFunction] which implements the lua standard `coroutine`
 * library.
 * 
 * 
 * The coroutine library in Luak has the same behavior as the
 * coroutine library in C, but is implemented using Java Threads to maintain
 * the call state between invocations.  Therefore it can be yielded from anywhere,
 * similar to the "Coco" yield-from-anywhere patch available for C-based lua.
 * However, coroutines that are yielded but never resumed to complete their execution
 * may not be collected by the garbage collector.
 * 
 * 
 * Typically, this library is included as part of a call to
 * [net.blueva.luak.lib.LuaPlatform.standardGlobals]
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * println(globals.get("coroutine").get("running").call())
 * ```
 * 
 * 
 * To instantiate and use it directly,
 * link it into your globals table via [LuaValue.load] using code such as:
 * ```kotlin
 * val globals = Globals()
 * globals.load(BaseLib())
 * globals.load(PackageLib())
 * globals.load(CoroutineLib())
 * println(globals.get("coroutine").get("running").call())
 * ```
 * 
 * 
 * @see LibFunction
 * 
 * @see net.blueva.luak.lib.jvm.JvmPlatform
 * 
 * @see net.blueva.luak.lib.LuaPlatform
 * 
 * @see [Lua 5.2 Coroutine Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.2)
 */
class CoroutineLib : TwoArgFunction() {
    var globals: Globals? = null

    /** Perform one-time initialization on the library by creating a table
     * containing the library functions, adding that table to the supplied environment,
     * adding the table to package.loaded, and returning table as the return value.
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env the environment to load into, which must be a Globals instance.
     */
    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        globals = env!!.checkglobals()
        val coroutine: LuaTable = LuaTable()
        coroutine.set("create", create())
        coroutine.set("resume", net.blueva.luak.lib.CoroutineLib.resume())
        coroutine.set("running", running())
        coroutine.set("status", net.blueva.luak.lib.CoroutineLib.status())
        coroutine.set("yield", YieldFunction())
        coroutine.set("wrap", wrap())
        coroutine.set("close", close())
        coroutine.set("isyieldable", isyieldable())
        env!!.set("coroutine", coroutine)
        if (!env!!.get("package")!!.isnil()) env!!.get("package")!!.get("loaded")!!.set("coroutine", coroutine)
        return coroutine
    }

    internal inner class create : LibFunction() {
        override fun call(f: LuaValue?): LuaValue? {
            return LuaThread((globals)!!, f!!.checkfunction())
        }
    }

    internal class resume : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val t: LuaThread = args.checkthread(1)
            return t.resume(args.subargs(2))
        }
    }

    internal inner class running : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val r: LuaThread = globals!!.running
            return varargsOf(arrayOf(r, valueOf(r.isMainThread)!!))
        }
    }

    internal class status : LibFunction() {
        override fun call(t: LuaValue?): LuaValue? {
            val lt: LuaThread = t!!.checkthread()!!
            return valueOf(lt.status)
        }
    }

    /**
     * `coroutine.close (co)`, from Lua 5.4.
     *
     * Ends a suspended or dead coroutine, running any to-be-closed variables it
     * was still holding.
     */
    internal inner class close : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            // With no argument it is the running coroutine that is closed.
            val thread: LuaThread =
                if (args.isnoneornil(1)) globals!!.running else args.checkthread(1)
            return thread.close()
        }
    }

    /**
     * `coroutine.isyieldable ([co])`, from Lua 5.2.
     *
     * True when [co], or the running coroutine if none is given, could yield -
     * that is, when it is not the main thread and no library call it is inside
     * of stands in the way.
     */
    internal inner class isyieldable : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val thread: LuaThread = if (args.isnoneornil(1)) globals!!.running else args.checkthread(1)
            return valueOf(!thread.isMainThread && thread.state.noyield == 0)!!
        }
    }

    internal inner class YieldFunction : VarArgFunction() {
        // Reached only when yield() is called from outside the suspend-aware
        // interpreter dispatch (e.g. from a library function's own callback,
        // like table.sort's comparator) - there's nowhere to suspend to.
        override fun invoke(args: Varargs): Varargs {
            throw LuaError("attempt to yield across metamethod/C-call boundary")
        }

        override suspend fun invokeSuspend(args: Varargs): Varargs {
            return globals!!.yieldSuspend(args)
        }
    }

    internal inner class wrap : LibFunction() {
        override fun call(f: LuaValue?): LuaValue? {
            val func: LuaValue? = f!!.checkfunction()
            val thread: LuaThread = LuaThread((globals)!!, func)
            return net.blueva.luak.lib.CoroutineLib.wrapper(thread)
        }
    }

    internal class wrapper(luathread: LuaThread) : VarArgFunction() {
        val luathread: LuaThread

        init {
            this.luathread = luathread
        }

        // The coroutine is what this wrapper carries, which is what an upvalue
        // is.
        override fun nupvalues(): Int = 1

        override fun upvaluestate(n: Int): Any? = if (n == 1) luathread else null

        override fun invoke(args: Varargs): Varargs {
            val result: Varargs = luathread.resume(args)
            if (result.arg1()!!.toboolean()) {
                return (result.subargs(2))!!
            }
            // Raised as it stands, object and all: a wrapped coroutine passes
            // its failure straight on to whoever called it.
            throw LuaError(result.arg(2)!!)
        }

        // The coroutine's own run is where a yield inside it belongs, so this
        // must not stand in the way; see BaseLib.pcall.invokeSuspend().
        override suspend fun invokeSuspend(args: Varargs): Varargs = invoke(args)
    }

    companion object {
        var coroutine_count: Int = 0
    }
}
