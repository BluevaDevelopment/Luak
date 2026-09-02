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

import net.blueva.luak.platformProperty
import net.blueva.luak.arrayCopy
import net.blueva.luak.Globals
import net.blueva.luak.Lua
import net.blueva.luak.LuaBoolean
import net.blueva.luak.LuaLightUserdata
import net.blueva.luak.LuaClosure
import net.blueva.luak.LuaError
import net.blueva.luak.LuaFunction
import net.blueva.luak.LuaNil
import net.blueva.luak.LuaNumber
import net.blueva.luak.LuaString
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaThread
import net.blueva.luak.LuaUserdata
import net.blueva.luak.LuaValue
import net.blueva.luak.Print
import net.blueva.luak.Prototype
import net.blueva.luak.Varargs

/**
 * Subclass of [LibFunction] which implements the lua standard `debug`
 * library.
 * 
 * 
 * The debug library in Luak tries to emulate the behavior of the corresponding C-based lua library.
 * To do this, it must maintain a separate stack of calls to [LuaClosure] and [LibFunction]
 * instances.
 * Especially when lua-to-java bytecode compiling is being used
 * via a [net.blueva.luak.Globals.Compiler] such as [net.blueva.luak.luajc.LuaJC],
 * this cannot be done in all cases.
 * 
 * 
 * Typically, this library is included as part of a call to
 * [net.blueva.luak.lib.jvm.JvmPlatform.debugGlobals] or
 * [net.blueva.luak.lib.LuaPlatform.debugGlobals]
 * <pre> `Globals globals = JvmPlatform.debugGlobals(); System.out.println( globals.get("debug").get("traceback").call() ); ` </pre>
 * 
 * 
 * To instantiate and use it directly,
 * link it into your globals table via [LuaValue.load] using code such as:
 * ```kotlin
 * val globals = Globals()
 * globals.load(BaseLib())
 * globals.load(PackageLib())
 * globals.load(DebugLib())
 * println(globals.get("debug").get("traceback").call())
 * ```
 * 
 * 
 * This library exposes the entire state of lua code, and provides method to see and modify
 * all underlying lua values within a Java VM so should not be exposed to client code
 * in a shared server environment.
 * 
 * @see LibFunction
 * 
 * @see net.blueva.luak.lib.jvm.JvmPlatform
 * 
 * @see net.blueva.luak.lib.LuaPlatform
 * 
 * @see [Lua 5.2 Debug Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.10)
 */
class DebugLib : TwoArgFunction() {
    var globals: Globals? = null

    /** Perform one-time initialization on the library by creating a table
     * containing the library functions, adding that table to the supplied environment,
     * adding the table to package.loaded, and returning table as the return value.
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env the environment to load into, which must be a Globals instance.
     */
    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        globals = env!!.checkglobals()
        globals!!.debuglib = this
        val debug: LuaTable = LuaTable()
        debug.set("debug", net.blueva.luak.lib.DebugLib.debug())
        debug.set("gethook", gethook())
        debug.set("getinfo", getinfo())
        debug.set("getlocal", getlocal())
        debug.set("getmetatable", net.blueva.luak.lib.DebugLib.getmetatable())
        debug.set("getregistry", getregistry())
        debug.set("getupvalue", net.blueva.luak.lib.DebugLib.getupvalue())
        debug.set("getuservalue", net.blueva.luak.lib.DebugLib.getuservalue())
        debug.set("sethook", sethook())
        debug.set("setlocal", setlocal())
        debug.set("setmetatable", net.blueva.luak.lib.DebugLib.setmetatable())
        debug.set("setupvalue", net.blueva.luak.lib.DebugLib.setupvalue())
        debug.set("setuservalue", net.blueva.luak.lib.DebugLib.setuservalue())
        debug.set("traceback", traceback())
        debug.set("upvalueid", net.blueva.luak.lib.DebugLib.upvalueid())
        debug.set("upvaluejoin", net.blueva.luak.lib.DebugLib.upvaluejoin())
        env!!.set("debug", debug)
        if (!env!!.get("package")!!.isnil()) env!!.get("package")!!.get("loaded")!!.set("debug", debug)
        return debug
    }

    // debug.debug()
    internal class debug : ZeroArgFunction() {
        override fun call(): LuaValue? {
            return (NONE)!!
        }
    }

    // debug.gethook ([thread])
    internal inner class gethook : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val t: LuaThread = if (args.narg() > 0) args.checkthread(1) else globals!!.running
            val s: LuaThread.State = t.state
            // Nothing installed: one answer, not an empty mask and a zero.
            if (s.hookfunc == null) return NIL!!
            // The letters in the order Lua writes them.
            val mask: String = (if (s.hookcall) "c" else "") +
                (if (s.hookrtrn) "r" else "") +
                (if (s.hookline) "l" else "")
            return varargsOf(hookfunction(t), valueOf(mask), valueOf(s.hookcount))
        }
    }

    //	debug.getinfo ([thread,] f [, what])
    internal inner class getinfo : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            var a = 1
            val thread: LuaThread = if (args.isthread(a)) args.checkthread(a++) else globals!!.running
            var func: LuaValue? = args.arg(a++)
            val what: String = args.optjstring(a++, "flnStu")!!
            // Every letter has to name something this can report; a stray one
            // is a mistake in the call rather than a request to be ignored. A
            // leading '>' is called out on its own, as it means something in
            // the C API that has no equivalent here.
            if (what.startsWith(">")) return (argerror(a - 1, "invalid option '>'"))!!
            for (letter in what) {
                if (letter !in "flnStuLr") return (argerror(a - 1, "invalid option"))!!
            }
            val callstack = callstack(thread)

            // find the stack info
            val frame: CallFrame?
            if (func!!.isnumber()) {
                frame = callstack.getCallFrame(func!!.toint())
                if (frame == null) return (NONE)!!
                func = frame.f
            } else if (func!!.isfunction()) {
                frame = callstack.findCallFrame(func)
            } else {
                return (argerror(a - 2, "function or level"))!!
            }

            // start a table
            val ar = callstack.auxgetinfo(what, func as LuaFunction?, frame)
            val info: LuaTable = LuaTable()
            if (what.indexOf('S') >= 0) {
                // What the function actually is, rather than always "Lua".
                info.set(net.blueva.luak.lib.DebugLib.Companion.WHAT, valueOf(ar.what))
                info.set(net.blueva.luak.lib.DebugLib.Companion.SOURCE, valueOf(ar.source))
                info.set(net.blueva.luak.lib.DebugLib.Companion.SHORT_SRC, valueOf(ar.short_src))
                info.set(net.blueva.luak.lib.DebugLib.Companion.LINEDEFINED, valueOf(ar.linedefined))
                info.set(net.blueva.luak.lib.DebugLib.Companion.LASTLINEDEFINED, valueOf(ar.lastlinedefined))
            }
            if (what.indexOf('l') >= 0) {
                info.set(net.blueva.luak.lib.DebugLib.Companion.CURRENTLINE, valueOf(ar.currentline))
            }
            if (what.indexOf('u') >= 0) {
                info.set(net.blueva.luak.lib.DebugLib.Companion.NUPS, valueOf(ar.nups.toInt()))
                info.set(net.blueva.luak.lib.DebugLib.Companion.NPARAMS, valueOf(ar.nparams.toInt()))
                info.set(net.blueva.luak.lib.DebugLib.Companion.ISVARARG, valueOf(ar.isvararg)!!)
            }
            if (what.indexOf('n') >= 0) {
                // A function looked up by value has no call to be named from,
                // and then the field is absent rather than a placeholder.
                val named: String? = ar.name
                if (named != null) {
                    info.set(net.blueva.luak.lib.DebugLib.Companion.NAME, LuaValue.valueOf(named))
                }
                info.set(net.blueva.luak.lib.DebugLib.Companion.NAMEWHAT, LuaValue.valueOf(ar.namewhat))
            }
            if (what.indexOf('r') >= 0) {
                info.set(net.blueva.luak.lib.DebugLib.Companion.FTRANSFER, valueOf(ar.ftransfer))
                info.set(net.blueva.luak.lib.DebugLib.Companion.NTRANSFER, valueOf(ar.ntransfer))
            }
            if (what.indexOf('t') >= 0) {
                info.set(net.blueva.luak.lib.DebugLib.Companion.ISTAILCALL, valueOf(ar.istailcall)!!)
                info.set(net.blueva.luak.lib.DebugLib.Companion.EXTRAARGS, valueOf(ar.extraargs))
            }
            // A function that is not written in Lua has no lines to report,
            // and leaves the field absent rather than empty.
            if (what.indexOf('L') >= 0 && func != null && func.isclosure()) {
                val lines: LuaTable = LuaTable()
                // Every line an instruction was compiled onto, as a set: the
                // ones a breakpoint can usefully be put on.
                val lineinfo: IntArray? = func.checkclosure()!!.p.lineinfo
                if (lineinfo != null) for (line in lineinfo) lines.set(line, TRUE!!)
                info.set(net.blueva.luak.lib.DebugLib.Companion.ACTIVELINES, lines)
            }
            if (what.indexOf('f') >= 0) {
                if (func != null) info.set(net.blueva.luak.lib.DebugLib.Companion.FUNC, func)
            }
            return info
        }
    }

    //	debug.getlocal ([thread,] f, local)
    internal inner class getlocal : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            var a = 1
            val thread: LuaThread = if (args.isthread(a)) args.checkthread(a++) else globals!!.running
            // A function where a level would go asks for a parameter's name,
            // which lives in the prototype rather than on any stack, so only
            // the name comes back and no value with it.
            if (args.isfunction(a)) {
                val func: LuaValue = args.checkfunction(a)!!
                val index: Int = args.checkint(a + 1)
                if (func !is LuaClosure) return NIL!!
                return (func.p.getlocalname(index, 0) ?: NIL)!!
            }
            val level: Int = args.checkint(a++)
            val local: Int = args.checkint(a++)
            val f = callstack(thread).getCallFrame(level)
            // A level that names no frame is a mistake in the call, not a
            // question with a nil answer.
            if (f == null) LuaValue.argerror(a - 2, "level out of range")
            return (f!!.getLocal(local))!!
        }
    }

    //	debug.getmetatable (value)
    internal class getmetatable : LibFunction() {
        override fun call(v: LuaValue?): LuaValue? {
            val mt: LuaValue? = v!!.getmetatable()
            return if (mt != null) mt else NIL
        }
    }

    //	debug.getregistry ()
    internal inner class getregistry : ZeroArgFunction() {
        override fun call(): LuaValue? = registry()
    }

    /**
     * The registry: a table of the runtime's own, not the globals.
     *
     * It holds what the library needs to keep alongside a program without
     * putting it where the program can trip over it - the hook functions,
     * under `_HOOKKEY`, keyed weakly by thread so that a coroutine's hook
     * goes when the coroutine does.
     */
    fun registry(): LuaTable {
        val existing: LuaTable? = registryTable
        if (existing != null) return existing
        val made = LuaTable()
        val hooks = LuaTable()
        hooks.setmetatable(tableOf(arrayOf<LuaValue?>(valueOf("__mode"), valueOf("k"))))
        made.set(net.blueva.luak.lib.DebugLib.Companion.HOOKKEY!!, hooks)
        registryTable = made
        return made
    }

    /** Where the hook functions are kept, keyed by the thread they belong to. */
    private fun hooks(): LuaTable =
        registry().get(net.blueva.luak.lib.DebugLib.Companion.HOOKKEY!!)!! as LuaTable

    /** The hook installed on [thread], or nil. */
    internal fun hookfunction(thread: LuaThread): LuaValue =
        hooks().get(thread)

    private var registryTable: LuaTable? = null

    //	debug.getupvalue (f, up)
    internal class getupvalue : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val func: LuaValue? = args.checkfunction(1)
            val up: Int = args.checkint(2)
            if (func is LuaClosure) {
                val c: LuaClosure = func as LuaClosure
                val name: LuaString? = net.blueva.luak.lib.DebugLib.Companion.findupvalue(c, up)
                if (name != null) {
                    return (varargsOf(name, (c.upValues[up - 1]!!.getValue())!!))!!
                }
                return NIL
            }
            // What a function of the library's own carries has no name of its
            // own, which is what Lua answers for one: the empty string.
            val carried: Any? = (func as? LuaFunction)?.upvaluestate(up)
            if (carried != null) return (varargsOf(valueOf(""), LuaLightUserdata(carried)))!!
            return NIL
        }
    }

    //	debug.getuservalue (u)
    /**
     * `debug.getuservalue (u [, n])`.
     *
     * A userdata here carries the host object it wraps and nothing else: there
     * are no user values attached to one, so there is never an nth to answer
     * with.
     */
    internal class getuservalue : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = NIL!!
    }


    // debug.sethook ([thread,] hook, mask [, count])
    internal inner class sethook : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            var a = 1
            val t: LuaThread = if (args.isthread(a)) args.checkthread(a++) else globals!!.running
            val func: LuaValue? = args.optfunction(a++, null)
            val str: String = args.optjstring(a++, "")!!
            val count: Int = args.optint(a++, 0)
            var call = false
            var line = false
            var rtrn = false
            for (i in 0..<str.length) when (str[i]) {
                'c' -> call = true
                'l' -> line = true
                'r' -> rtrn = true
            }
            val s: LuaThread.State = t.state
            // A hook with nothing to fire on is no hook at all, which is what
            // Lua takes an empty mask and a zero count to mean.
            val installed: LuaValue? =
                if (call || line || rtrn || count > 0) func else null
            // The function itself lives in the registry, keyed by its thread;
            // the flags stay on the thread so the interpreter's
            // per-instruction check costs nothing.
            hooks().set(t, if (installed == null) NIL!! else installed)
            s.hookfunc = installed
            s.hookcall = call
            s.hookline = line
            s.hookcount = count
            s.hookrtrn = rtrn
            return (NONE)!!
        }
    }

    //	debug.setlocal ([thread,] level, local, value)
    internal inner class setlocal : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            var a = 1
            val thread: LuaThread = if (args.isthread(a)) args.checkthread(a++) else globals!!.running
            val level: Int = args.checkint(a++)
            val local: Int = args.checkint(a++)
            val value: LuaValue? = args.arg(a++)
            val f = callstack(thread).getCallFrame(level)
            if (f == null) LuaValue.argerror(a - 3, "level out of range")
            return (f!!.setLocal(local, value))!!
        }
    }

    //	debug.setmetatable (value, table)
    internal class setmetatable : TwoArgFunction() {
        override fun call(value: LuaValue?, table: LuaValue?): LuaValue? {
            val mt: LuaValue? = table!!.opttable(null)
            when (value!!.type()) {
                TNIL -> LuaNil.s_metatable = mt
                TNUMBER -> LuaNumber.s_metatable = mt
                TBOOLEAN -> LuaBoolean.s_metatable = mt
                TSTRING -> LuaString.s_metatable = mt
                TFUNCTION -> LuaFunction.s_metatable = mt
                TTHREAD -> LuaThread.s_metatable = mt
                else -> value!!.setmetatable(mt)
            }
            return value
        }
    }

    //	debug.setupvalue (f, up, value)
    internal class setupvalue : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val func: LuaValue? = args.checkfunction(1)
            val up: Int = args.checkint(2)
            val value: LuaValue? = args.arg(3)
            if (func is LuaClosure) {
                val c: LuaClosure = func as LuaClosure
                val name: LuaString? = net.blueva.luak.lib.DebugLib.Companion.findupvalue(c, up)
                if (name != null) {
                    c.upValues[up - 1]!!.setValue(value)
                    return name
                }
            }
            return NIL
        }
    }

    //	debug.setuservalue (udata, value)
    internal class setuservalue : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            // It still has to be a full userdata, but there is nowhere to put
            // the value: a userdata here carries the host object it wraps and
            // nothing else, so this always answers that it did not fit.
            val target: LuaValue = args.arg1()!!
            if (target.type() != LuaValue.TUSERDATA || target is LuaLightUserdata) {
                LuaValue.argerror(1, "userdata expected, got " + target.argtypename())
            }
            args.checkvalue(2)
            return (FALSE)!!
        }
    }

    //	debug.traceback ([thread,] [message [, level]])
    internal inner class traceback : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            var a = 1
            val thread: LuaThread = if (args.isthread(a)) args.checkthread(a++) else globals!!.running
            val given: LuaValue = args.arg(a)!!
            // Anything that is not text is handed straight back: a message
            // object of a program's own is not something to build on.
            if (!given.isnil() && !given.isstring()) return given
            val message: String? = args.optjstring(a++, null)
            // Level 1 is the function that asked, so the traceback does not
            // start by naming this one.
            val level: Int = args.optint(a++, if (thread === globals!!.running) 1 else 0)
            val tb = callstack(thread).traceback(level)
            return valueOf(if (message != null) message.toString() + "\n" + tb else tb)
        }
    }

    //	debug.upvalueid (f, n)
    internal class upvalueid : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val func: LuaValue? = args.checkfunction(1)
            val up: Int = args.checkint(2)
            // A bare reference to the storage itself, so two upvalues can be
            // compared: the same one answers the same value every time, and
            // two different ones never collide.
            if (func is LuaClosure) {
                if (up > 0 && up <= func.upValues.size) {
                    return LuaLightUserdata(func.upValues[up - 1]!!)
                }
                return NIL
            }
            val carried: Any? = (func as? LuaFunction)?.upvaluestate(up)
            return if (carried != null) LuaLightUserdata(carried) else NIL
        }
    }

    //	debug.upvaluejoin (f1, n1, f2, n2)
    internal class upvaluejoin : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val f1: LuaClosure = args.checkclosure(1)
            val n1: Int = args.checkint(2)
            val f2: LuaClosure = args.checkclosure(3)
            val n2: Int = args.checkint(4)
            if (n1 < 1 || n1 > f1.upValues.size) argerror("index out of range")
            if (n2 < 1 || n2 > f2.upValues.size) argerror("index out of range")
            f1.upValues[n1 - 1] = f2.upValues[n2 - 1]
            return (NONE)!!
        }
    }

    fun onCall(f: LuaFunction?) {
        onCall(f, null)
    }

    /** As [onCall], remembering the arguments a library function was given. */
    fun onCall(f: LuaFunction?, args: Array<LuaValue?>?) {
        val s: LuaThread.State = globals!!.running.state
        // The frame goes on even inside a hook: code the hook runs counts
        // levels from itself, and skipping the bookkeeping would make it
        // count one too few. Only the hook callback is left out, since a hook
        // must not call itself.
        val frames: CallStack = callstack()
        frames.onCall(f)
        val pushed: CallFrame = frames.frame!![frames.calls - 1]!!
        pushed.extraargs = s.pendingextraargs
        pushed.args = args
        // What the call is handing over, for a hook to read back.
        pushed.ftransfer = 1
        pushed.ntransfer = args?.size ?: 0
        val tail: Boolean = s.pendingtailcall
        pushed.istailcall = tail
        s.pendingtailcall = false
        s.pendingextraargs = 0
        markhookframe(s)
        // A tail call is its own kind of event, since the frame it makes takes
        // the place of the one that called it.
        if (!s.inhook && s.hookcall) {
            callHook(
                s,
                if (tail) net.blueva.luak.lib.DebugLib.Companion.TAILCALL
                else net.blueva.luak.lib.DebugLib.Companion.CALL,
                NIL,
            )
        }
    }

    fun onCall(c: LuaClosure?, varargs: Varargs?, stack: Array<LuaValue?>?) {
        onCall(c, varargs, stack, null)
    }

    /**
     * As [onCall], with the call's arguments in storage of their own.
     *
     * `debug.getlocal` and `debug.setlocal` reach the extra arguments of a
     * vararg function through negative indices, and writing to one has to show
     * through to the `...` the function itself reads, so both work on the same
     * array.
     */
    fun onCall(c: LuaClosure?, varargs: Varargs?, stack: Array<LuaValue?>?, args: Array<LuaValue?>?) {
        val s: LuaThread.State = globals!!.running.state
        val frames: CallStack = callstack()
        frames.onCall(c, varargs, stack)
        val pushed: CallFrame = frames.frame!![frames.calls - 1]!!
        pushed.args = args
        // The declared parameters are what a call hands a Lua function.
        pushed.ftransfer = 1
        pushed.ntransfer = c?.p?.numparams ?: 0
        val tail: Boolean = s.pendingtailcall
        pushed.istailcall = tail
        s.pendingtailcall = false
        pushed.extraargs = s.pendingextraargs
        s.pendingextraargs = 0
        markhookframe(s)
        // A tail call is its own kind of event, since the frame it makes takes
        // the place of the one that called it.
        if (!s.inhook && s.hookcall) {
            callHook(
                s,
                if (tail) net.blueva.luak.lib.DebugLib.Companion.TAILCALL
                else net.blueva.luak.lib.DebugLib.Companion.CALL,
                NIL,
            )
        }
    }

    fun onInstruction(pc: Int, v: Varargs?, top: Int) {
        val s: LuaThread.State = globals!!.running.state
        // Where a frame is stays up to date even inside a hook; only the hook
        // callbacks are held back, since a hook must not call itself.
        callstack().onInstruction(pc, v, top)
        if (s.inhook || s.hookfunc == null) return
        if (s.hookcount > 0) if (++s.bytecodes % s.hookcount === 0) callHook(
            s,
            net.blueva.luak.lib.DebugLib.Companion.COUNT,
            NIL
        )
        if (s.hookline) {
            val frames: CallStack = callstack()
            val frame: CallFrame? = if (frames.calls > 0) frames.frame!![frames.calls - 1] else null
            if (frame != null && frame.reachedNewLine()) {
                val newline: Int = frame.currentline()
                s.lastline = newline
                // Code with no line information has no line to report, and
                // nil is what Lua hands the hook in its place.
                callHook(
                    s,
                    net.blueva.luak.lib.DebugLib.Companion.LINE,
                    if (newline >= 0) LuaValue.valueOf(newline) else NIL,
                )
            }
        }
    }

    /**
     * Counts the `__call` handlers standing in front of what is about to run.
     *
     * Each of them puts the value it was found on in front of the real
     * arguments, and the frame that ends up running reports how many, which is
     * what `debug.getinfo(f, "t").extraargs` answers with.
     */
    fun notecallchain(target: LuaValue) {
        if (target is LuaFunction) return
        val s: LuaThread.State = globals!!.running.state
        var value: LuaValue = target
        var chain = 0
        while (value !is LuaFunction) {
            val handler: LuaValue = value.metatag(LuaValue.CALL)
            // Not callable, or longer than Lua follows: either way the call
            // itself is what reports it, so nothing is noted here.
            if (handler.isnil()) return
            if (++chain > LuaValue.MAX_CALL_CHAIN) return
            value = handler
        }
        s.pendingextraargs = chain
    }

    /** Says that the next frame pushed is one a tail call is making. */
    fun ontailcall() {
        globals!!.running.state.pendingtailcall = true
    }

    /** Marks the frame just pushed as the hook's own, when it is one. */
    private fun markhookframe(s: LuaThread.State) {
        if (s.finalizerframepending) {
            s.finalizerframepending = false
            val frames: CallStack = callstack()
            if (frames.calls > 0) frames.frame!![frames.calls - 1]!!.finalizer = true
        }
        if (!s.hookframepending) return
        s.hookframepending = false
        val frames: CallStack = callstack()
        if (frames.calls > 0) frames.frame!![frames.calls - 1]!!.hooked = true
    }

    /**
     * Records the values a call is about to hand back, for a return hook.
     *
     * They take the place of the arguments on the frame, which is where Lua
     * leaves them too: a hook reads either with `debug.getlocal`.
     */
    fun onResults(results: Varargs?) {
        val frames: CallStack = callstack()
        if (frames.calls == 0) return
        val frame: CallFrame = frames.frame!![frames.calls - 1]!!
        val count: Int = results?.narg() ?: 0
        val values: Array<LuaValue?> = arrayOfNulls(count)
        for (index in 0..<count) values[index] = results!!.arg(index + 1)
        frame.results = values
        frame.ftransfer = 1
        frame.ntransfer = count
    }

    fun onReturn() {
        val s: LuaThread.State = globals!!.running.state
        // A frame a tail call is leaving is not returning, it is being taken
        // over: the call that replaces it reports itself instead.
        if (s.pendingtailcall) {
            callstack().onReturn()
            return
        }
        // The hook runs while the frame is still there, so code inside it can
        // still ask which function is returning.
        if (!s.inhook && s.hookrtrn) callHook(s, net.blueva.luak.lib.DebugLib.Companion.RETURN, NIL)
        callstack().onReturn()
    }

    /**
     * Runs [body] with the innermost call frame out of sight.
     *
     * An error has already left the function by the time its to-be-closed
     * variables are closed, so a `__close` handler asking who called it must
     * be shown that function's caller rather than the function itself.
     */
    fun <T> withoutTopFrame(body: () -> T): T {
        hidetopframe()
        try {
            return body()
        } finally {
            showtopframe()
        }
    }

    /** The frames hidden by [hidetopframe], innermost last. */
    private val hidden: ArrayList<Pair<CallFrame, Array<Any?>>> = ArrayList()

    /** Takes the innermost frame out of sight; see [withoutTopFrame]. */
    fun hidetopframe() {
        val stack: CallStack = callstack()
        if (stack.calls == 0) return
        // The slot is not just hidden but reused by whatever runs next, so
        // what was in it has to be kept and put back afterwards.
        val frame: CallFrame = stack.frame!![stack.calls - 1]!!
        hidden.add(Pair(frame, frame.snapshot()))
        stack.calls--
    }

    /** Puts back what [hidetopframe] took out of sight. */
    fun showtopframe() {
        if (hidden.isEmpty()) return
        val (frame, saved) = hidden.removeAt(hidden.size - 1)
        callstack().calls++
        frame.restore(saved)
    }

    fun traceback(level: Int): String {
        return callstack().traceback(level)
    }

    /**
     * Names the function a library called back into, for an error it raised.
     *
     * A function of the library's own that a library function called has no
     * call in any Lua code to be named after, so Lua names it by where a
     * library keeps it, and reports the error with no place at all: the
     * caller is not written in Lua and has no line to point at.
     */
    fun notecallback(le: LuaError) {
        if (le.positioned) return
        le.nowhere = true
        val message: String = le.message ?: return
        val match = Regex("^bad argument #(\\d+): ([\\s\\S]*)$").find(message) ?: return
        val frames: CallStack = callstack()
        if (frames.calls == 0) return
        val name: String = globalfuncname(frames.frame!![frames.calls - 1]!!.f) ?: "?"
        le.argMessageOverride =
            "bad argument #" + match.groupValues[1] + " to '" + name + "' (" + match.groupValues[2] + ")"
    }

    /**
     * The name [f] answers to in the loaded libraries, or null.
     *
     * A function of the library's own has no name of its own to give: what a
     * traceback calls it is where it is kept, so `print` is "print" and
     * `string.rep` is "string.rep". Only what a library table holds directly
     * is looked at, which is as far as Lua looks.
     */
    fun globalfuncname(f: LuaValue?): String? {
        if (f == null || globals == null) return null
        val loaded: LuaValue = globals!!.get("package")?.get("loaded") ?: return null
        if (!loaded.istable()) return null
        var key: LuaValue = NIL
        while (true) {
            val entry: Varargs = loaded.next(key) ?: return null
            key = entry.arg1() ?: return null
            if (key.isnil()) return null
            if (key.type() != LuaValue.TSTRING) continue
            val module: LuaValue = entry.arg(2) ?: continue
            if (module === f) return key.tojstring()
            if (!module.istable()) continue
            var field: LuaValue = NIL
            while (true) {
                val held: Varargs = module.next(field) ?: break
                field = held.arg1() ?: break
                if (field.isnil()) break
                if (field.type() != LuaValue.TSTRING) continue
                if (held.arg(2) !== f) continue
                val name: String = key.tojstring() + "." + field.tojstring()
                // The globals are kept under "_G", which nobody writes.
                return if (name.startsWith("_G.")) name.substring(3) else name
            }
        }
    }

    /**
     * Writes down the stack [le] was raised on, if it is worth keeping.
     *
     * Called where the error is still standing on it. Only a coroutine's is
     * kept, and only the first time: the frames are popped as the error
     * unwinds, and after that the only way to show them is from here.
     */
    fun notestack(le: LuaError) {
        if (le.luastack != null) return
        if (globals!!.running.isMainThread) return
        le.luastack = callstack().tracebacklines(0)
    }

    fun getCallFrame(level: Int): CallFrame? {
        return callstack().getCallFrame(level)
    }

    fun callHook(s: LuaThread.State, type: LuaValue?, arg: LuaValue?) {
        if (s.inhook || s.hookfunc == null) return
        s.inhook = true
        // The hook's own frame is the one its call pushes; it is only marked
        // as a hook here, so that code inside it counts levels from itself
        // and reports itself as a hook rather than as an ordinary call.
        s.hookframepending = true
        try {
            s.hookfunc!!.call(type, arg)
        } catch (e: LuaError) {
            throw e
        } catch (e: RuntimeException) {
            throw LuaError(e)
        } finally {
            s.hookframepending = false
            s.inhook = false
        }
    }

    @kotlin.jvm.JvmOverloads
    fun callstack(t: LuaThread = globals!!.running): CallStack {
        if (t.callstack == null) {
            val made = net.blueva.luak.lib.DebugLib.CallStack()
            made.main = t.isMainThread
            made.owner = this
            t.callstack = made
        }
        return t.callstack as CallStack
    }

    class DebugInfo {
        var name: String? = null /* (n) */
        var namewhat: String? = null /* (n) 'global', 'local', 'field', 'method' */

        /** (t) how many arguments a `__call` chain put in front of the real ones. */
        var extraargs: Int = 0

        /** (r) the first value being handed over, and how many there are. */
        var ftransfer: Int = 0

        var ntransfer: Int = 0
        var what: String? = null /* (S) 'Lua', 'C', 'main', 'tail' */
        var source: String? = null /* (S) */
        var currentline: Int = 0 /* (l) */
        var linedefined: Int = 0 /* (S) */
        var lastlinedefined: Int = 0 /* (S) */
        var nups: Short = 0 /* (u) number of upvalues */
        var nparams: Short = 0 /* (u) number of parameters */
        var isvararg: Boolean = false /* (u) */
        var istailcall: Boolean = false /* (t) */
        var short_src: String? = null /* (S) */
        var cf: CallFrame? = null /* active function */

        fun funcinfo(f: LuaFunction) {
            if (f.isclosure()) {
                val p: Prototype = f.checkclosure()!!.p
                this.source = if (p.source != null) p.source!!.tojstring() else "=?"
                this.linedefined = p.linedefined
                this.lastlinedefined = p.lastlinedefined
                this.what = if (this.linedefined == 0) "main" else "Lua"
                this.short_src = p.shortsource()
            } else {
                // Reported as Lua reports a function that is not written in
                // Lua. Saying "Java" would be more literal but no portable
                // script looks for it, and every one of them looks for "C".
                this.source = "=[C]"
                this.linedefined = -1
                this.lastlinedefined = -1
                this.what = "C"
                // The source of a function that is not written in Lua is the
                // runtime itself, which Lua names "[C]" whatever the host is.
                this.short_src = "[C]"
            }
        }
    }

    class CallStack internal constructor() {
        /** How many innermost levels a traceback writes before leaving a gap. */
        private val LEVELS1: Int = 10

        /** How many outermost levels it writes after the gap. */
        private val LEVELS2: Int = 11

        var frame: Array<CallFrame?>? = net.blueva.luak.lib.DebugLib.CallStack.Companion.EMPTY
        var calls: Int = 0

        /**
         * True for the stack a program was started on.
         *
         * Only that one has the host below it, which is the frame a traceback
         * ends with; a coroutine's stack ends where its body does.
         */
        var main: Boolean = false

        /**
         * The stack of a coroutine that died of an error, kept for a later
         * traceback; see [LuaError.luastack].
         */
        var frozen: List<String>? = null

        /** The debug library this stack belongs to, for the names it knows. */
        var owner: DebugLib? = null

                fun currentline(): Int {
            return if (calls > 0) frame!![calls - 1]!!.currentline() else -1
        }

                private fun pushcall(): CallFrame? {
            if (calls >= frame!!.size) {
                val n: Int = maxOf(4, frame!!.size * 3 / 2)
                val f = arrayOfNulls<CallFrame>(n)
                val oldFrame = frame!!
                arrayCopy(oldFrame, 0, f, 0, oldFrame.size)
                for (i in frame!!.size..<n) f[i] = net.blueva.luak.lib.DebugLib.CallFrame()
                frame = f
                for (i in 1..<n) f[i]!!.previous = f[i - 1]
            }
            return frame!![calls++]
        }

                fun onCall(function: LuaFunction?) {
            pushcall()!!.set(function)
        }

                fun onCall(function: LuaClosure?, varargs: Varargs?, stack: Array<LuaValue?>?) {
            pushcall()!!.set(function, varargs, stack)
        }

                fun onReturn() {
            if (calls > 0) frame!![--calls]!!.reset()
        }

                fun onInstruction(pc: Int, v: Varargs?, top: Int) {
            if (calls > 0) frame!![calls - 1]!!.instr(pc, v, top)
        }

        /**
         * Get the traceback starting at a specific level.
         * @param level
         * @return String containing the traceback.
         */
                fun traceback(level: Int): String {
            val sb: StringBuilder = StringBuilder()
            sb.append("stack traceback:")
            for (line in tracebacklines(level)) {
                sb.append("\n\t")
                sb.append(line)
            }
            return sb.toString()
        }

        /**
         * One entry per frame from [level] down, as [traceback] writes them.
         *
         * A stack deeper than the two parts together is written with its
         * middle left out, since a traceback is read by a person: the
         * outermost frames say where the trouble is and the innermost say
         * where it came from, and a note stands in for everything between.
         */
        fun tracebacklines(level: Int): List<String> {
            frozen?.let { return if (level <= 0) it else it.drop(level) }
            val lines: ArrayList<String> = ArrayList()
            // Counted the way Lua counts it: the host below the main thread is
            // a level of its own, and is where the last line comes from.
            val last: Int = calls - 1 + (if (main) 1 else 0)
            var level = level
            var show: Int = if (last - level > LEVELS1 + LEVELS2) LEVELS1 else -1
            while (level <= last) {
                val here: Int = level
                level++
                if (show-- == 0) {
                    val skipped: Int = last - level - LEVELS2 + 1
                    lines.add("...\t(skipping " + skipped + " levels)")
                    level += skipped
                    continue
                }
                val c: CallFrame? = getCallFrame(here)
                if (c == null) {
                    // Below the main thread is the host that started it, which
                    // is where a reference build shows its own C entry point.
                    lines.add("[C]: in ?")
                    continue
                }
                val sb: StringBuilder = StringBuilder()
                sb.append(c.shortsource())
                sb.append(':')
                if (c.currentline() > 0) sb.append(c.currentline().toString() + ":")
                sb.append(" in ")
                // Named the way Lua names one, in that order: how the code
                // reached it, then the main chunk, then where a library keeps
                // it, then where it was written, then nothing at all.
                val ar = auxgetinfo("n", c.f, c)
                val namewhat: String = ar.namewhat.orEmpty()
                val known: String? = owner?.globalfuncname(c.f)
                if (ar.name != null && namewhat.isNotEmpty()) {
                    sb.append(namewhat)
                    sb.append(" '")
                    sb.append(ar.name)
                    sb.append('\'')
                } else if (c.f!!.isclosure() && c.linedefined() == 0) {
                    sb.append("main chunk")
                } else if (known != null) {
                    sb.append("function '")
                    sb.append(known)
                    sb.append('\'')
                } else if (c.f!!.isclosure()) {
                    sb.append("function <")
                    sb.append(c.shortsource())
                    sb.append(':')
                    sb.append(c.linedefined())
                    sb.append('>')
                } else {
                    sb.append('?')
                }
                lines.add(sb.toString())
                // A tail call left no frame of its own behind, and the gap it
                // leaves is where Lua says so.
                if (c.istailcall) lines.add("(...tail calls...)")
            }
            return lines
        }

                fun getCallFrame(level: Int): CallFrame? {
            // Level 0 is the function asking, as it is in Lua: every library
            // function has a frame of its own, so the one that called
            // debug.getinfo is one step further down.
            if (level < 0 || level >= calls) return null
            return frame!![calls - 1 - level]
        }

                fun findCallFrame(func: LuaValue?): CallFrame? {
            // Innermost first, and the frame that matched is the one to hand
            // back: returning frame[i] instead was reaching a different one,
            // sometimes an unused slot with no function in it at all.
            for (i in 1..calls) {
                val candidate: CallFrame = frame!![calls - i]!!
                if (candidate.f === func) return candidate
            }
            return null
        }


                fun auxgetinfo(what: String, f: LuaFunction?, ci: CallFrame?): DebugInfo {
            val ar: DebugInfo = net.blueva.luak.lib.DebugLib.DebugInfo()
            var i = 0
            val n: Int = what.length
            while (i < n) {
                when (what[i]) {
                    'S' -> ar.funcinfo((f)!!)
                    'l' -> ar.currentline = if (ci != null && ci.f!!.isclosure()) ci.currentline() else -1
                    'u' -> if (f != null && f.isclosure()) {
                        val p: Prototype = f.checkclosure()!!.p
                        ar.nups = p.upvalues!!.size.toShort()
                        ar.nparams = p.numparams.toShort()
                        ar.isvararg = p.is_vararg !== 0
                    } else {
                        // A function of the library's own takes whatever it is
                        // given and carries whatever state it was built with.
                        ar.nups = ((f as? LuaFunction)?.nupvalues() ?: 0).toShort()
                        ar.isvararg = true
                        ar.nparams = 0
                    }

                    't' -> {
                        ar.istailcall = ci?.istailcall ?: false
                        ar.extraargs = ci?.extraargs ?: 0
                    }

                    'r' -> {
                        ar.ftransfer = ci?.ftransfer ?: 0
                        ar.ntransfer = ci?.ntransfer ?: 0
                    }
                    'n' -> {
                        // A hook was not called from any instruction, so there
                        // is no call site to read a name from.
                        if (ci != null && ci.hooked) {
                            ar.name = "?"
                            ar.namewhat = "hook"
                        } else if (ci != null && ci.finalizer) {
                            // The collector called it, and Lua names it after
                            // the metamethod that put it there.
                            ar.name = "__gc"
                            ar.namewhat = "metamethod"
                        } else if (ci != null && ci.previous != null) {
                            if (ci.previous!!.f!!.isclosure()) {
                                val nw: NameWhat? = net.blueva.luak.lib.DebugLib.Companion.getfuncname(ci.previous!!)
                                if (nw != null) {
                                    ar.name = nw.name
                                    ar.namewhat = nw.namewhat
                                }
                            }
                        }
                        if (ar.namewhat == null) {
                            ar.namewhat = "" /* not found */
                            ar.name = null
                        }
                    }

                    'L', 'f' -> {}
                    else -> {}
                }
                ++i
            }
            return ar
        }

        companion object {
            val EMPTY: Array<CallFrame?> = arrayOf<CallFrame?>()
        }
    }

    class CallFrame {
        var f: LuaFunction? = null
        var pc: Int = 0

        /**
         * Where this frame was one instruction ago, upstream's `oldpc`.
         *
         * The line hook fires when execution reaches a line other than the one
         * this points at, or jumps backwards, which is how a loop body on a
         * single line still reports every pass.
         */
        var oldpc: Int = 0

        /**
         * Every argument the call was given, when the debug library is
         * watching a vararg function, in storage `debug.setlocal` can write
         * through to. Null for every other call.
         */
        var args: Array<LuaValue?>? = null

        /** True when this frame is a hook the runtime called, not a Lua call. */
        var hooked: Boolean = false

        /**
         * True for the frame of a `__gc` handler, which no instruction called
         * and which therefore has no call site to be named from.
         */
        var finalizer: Boolean = false

        /** True when a tail call made this frame, taking its caller's place. */
        var istailcall: Boolean = false

        /** How many `__call` handlers put a value in front of the real arguments. */
        var extraargs: Int = 0

        /**
         * The first of the values being handed over, and how many there are.
         *
         * At a call that is the arguments, at a return the results; it is what
         * `debug.getinfo(f, "r")` reports so a hook can read them back out
         * with `debug.getlocal`.
         */
        var ftransfer: Int = 0

        var ntransfer: Int = 0

        /** The values being handed back, once a call has produced them. */
        var results: Array<LuaValue?>? = null

        var top: Int = 0
        var v: Varargs? = null
        var stack: Array<LuaValue?>? = null
        var previous: CallFrame? = null
        fun set(function: LuaClosure?, varargs: Varargs?, stack: Array<LuaValue?>?) {
            this.f = function
            this.v = varargs
            this.stack = stack
        }

        fun shortsource(): String? {
            return if (f!!.isclosure()) f!!.checkclosure()!!.p.shortsource() else "[C]"
        }

        fun set(function: LuaFunction?) {
            this.f = function
        }

        fun reset() {
            this.f = null
            this.v = null
            this.stack = null
            this.pc = 0
            this.oldpc = 0
            this.args = null
            this.hooked = false
            this.finalizer = false
            this.istailcall = false
            this.extraargs = 0
            this.ftransfer = 0
            this.ntransfer = 0
            this.results = null
        }

        /** Everything [restore] needs to put this frame back as it is now. */
        internal fun snapshot(): Array<Any?> = arrayOf(f, pc, top, v, stack, oldpc, args)

        /** Puts back a frame that something else was allowed to overwrite. */
        @Suppress("UNCHECKED_CAST")
        internal fun restore(saved: Array<Any?>) {
            f = saved[0] as LuaFunction?
            pc = saved[1] as Int
            top = saved[2] as Int
            v = saved[3] as Varargs?
            stack = saved[4] as Array<LuaValue?>?
            oldpc = saved[5] as Int
            args = saved[6] as Array<LuaValue?>?
        }

        fun instr(pc: Int, v: Varargs?, top: Int) {
            this.oldpc = this.pc
            this.pc = pc
            this.v = v
            this.top = top
            if (net.blueva.luak.lib.DebugLib.Companion.TRACE) Print.printState((f!!.checkclosure())!!, pc, stack!!, top, v)
        }

        /** The slot a negative local index names, or -1 if there is none. */
        private fun extraArg(i: Int): Int {
            val values: Array<LuaValue?> = args ?: return -1
            // The declared parameters are already on the stack by the time a
            // frame is pushed, so what is kept here is the extras alone and
            // -1 names the first of them.
            val slot: Int = -i - 1
            return if (slot in values.indices) slot else -1
        }

        fun getLocal(i: Int): Varargs {
            // Once a call has produced its results they are what the indices in
            // the transfer range name, which is what a return hook reads.
            val produced: Array<LuaValue?>? = results
            if (produced != null && i >= ftransfer && i < ftransfer + ntransfer) {
                return varargsOf(valueOf("(temporary)"), produced[i - ftransfer] ?: NIL)!!
            }
            // A function of the library's own has no registers, only the
            // arguments it was handed, which is what Lua reports for one.
            if (f?.isclosure() != true) {
                val given: Array<LuaValue?> = args ?: return NIL!!
                if (i < 1 || i > given.size) return NIL!!
                return varargsOf(valueOf("(C temporary)"), given[i - 1] ?: NIL)!!
            }
            if (i < 0) {
                val slot: Int = extraArg(i)
                if (slot < 0) return NIL!!
                return varargsOf(valueOf("(vararg)"), args!![slot] ?: NIL)!!
            }
            val name: LuaString? = getlocalname(i)
            if (i >= 1 && i <= livelimit() && stack!![i - 1] != null) {
                // A register the function is using but has not named yet holds
                // a temporary, which is what Lua calls it.
                return varargsOf(name ?: valueOf("(temporary)"), stack!![i - 1]!!)!!
            }
            return NIL!!
        }

        fun setLocal(i: Int, value: LuaValue?): Varargs? {
            if (f?.isclosure() != true) {
                val given: Array<LuaValue?> = args ?: return NIL
                if (i < 1 || i > given.size) return NIL
                given[i - 1] = value
                return valueOf("(C temporary)")
            }
            if (i < 0) {
                val slot: Int = extraArg(i)
                if (slot < 0) return NIL
                args!![slot] = value
                return valueOf("(vararg)")
            }
            val name: LuaString? = getlocalname(i)
            if (i >= 1 && i <= stack!!.size && stack!![i - 1] != null) {
                stack!![i - 1] = value
                return if (name == null) NIL else name
            } else {
                return NIL
            }
        }

        /**
         * How many registers of this frame hold something worth looking at.
         *
         * While a call is under way the function being called sits just past
         * them, and what is beyond that belongs to the call, not to this
         * frame.
         */
        private fun livelimit(): Int {
            val room: Int = stack?.size ?: 0
            val closure: LuaClosure = f?.checkclosure() ?: return room
            val code: IntArray = closure.p.code ?: return room
            if (pc < 0 || pc >= code.size) return room
            return when (Lua.GET_OPCODE(code[pc])) {
                Lua.OP_CALL, Lua.OP_TAILCALL -> Lua.GETARG_A(code[pc])
                else -> room
            }
        }

        /**
         * True when the line hook should fire for the instruction about to run.
         *
         * That is when it sits on a different line from the one before it, or
         * when the jump went backwards: a loop written on one line still
         * reports each pass that way.
         */
        internal fun reachedNewLine(): Boolean {
            if (!f!!.isclosure()) return false
            // Asked before the line numbers are, since it is also true of the
            // first instruction of a call: a chunk loaded without debug
            // information has no lines to change, and this is the one report
            // it still makes.
            if (pc <= oldpc) return true
            val li: IntArray = f!!.checkclosure()!!.p.lineinfo ?: return false
            if (pc < 0 || pc >= li.size) return false
            return oldpc < 0 || oldpc >= li.size || li[pc] != li[oldpc]
        }

        fun currentline(): Int {
            if (!f!!.isclosure()) return -1
            val li: IntArray? = f!!.checkclosure()!!.p.lineinfo
            return if (li == null || pc < 0 || pc >= li.size) -1 else li[pc]
        }

        fun sourceline(): String? {
            if (!f!!.isclosure()) return f!!.tojstring()
            return f!!.checkclosure()!!.p.shortsource() + ":" + currentline()
        }

        fun linedefined(): Int {
            return if (f!!.isclosure()) f!!.checkclosure()!!.p.linedefined else -1
        }

        fun getlocalname(index: Int): LuaString? {
            if (!f!!.isclosure()) return null
            return f!!.checkclosure()!!.p.getlocalname(index, pc)
        }
    }

    class NameWhat(val name: String, val namewhat: String)

    companion object {
        var CALLS: Boolean = false
        var TRACE: Boolean = false

        init {
            try {
                net.blueva.luak.lib.DebugLib.Companion.CALLS = (null != platformProperty("CALLS"))
            } catch (e: Exception) {
            }
            try {
                net.blueva.luak.lib.DebugLib.Companion.TRACE = (null != platformProperty("TRACE"))
            } catch (e: Exception) {
            }
        }

        val LUA: LuaString? = valueOf("Lua")
        private val QMARK: LuaString? = valueOf("?")
        private val CALL: LuaString? = valueOf("call")
        private val TAILCALL: LuaString? = valueOf("tail call")
        private val LINE: LuaString? = valueOf("line")
        private val COUNT: LuaString? = valueOf("count")
        private val RETURN: LuaString? = valueOf("return")

        val FUNC: LuaString? = valueOf("func")
        val ISTAILCALL: LuaString? = valueOf("istailcall")
        val ISVARARG: LuaString? = valueOf("isvararg")
        val NUPS: LuaString? = valueOf("nups")
        val NPARAMS: LuaString? = valueOf("nparams")
        val NAME: LuaString? = valueOf("name")
        val NAMEWHAT: LuaString? = valueOf("namewhat")
        val EXTRAARGS: LuaString? = valueOf("extraargs")

        val FTRANSFER: LuaString? = valueOf("ftransfer")
        val NTRANSFER: LuaString? = valueOf("ntransfer")

        /** Where the registry keeps the hook functions, as Lua names it. */
        val HOOKKEY: LuaString? = valueOf("_HOOKKEY")
        val WHAT: LuaString? = valueOf("what")
        val SOURCE: LuaString? = valueOf("source")
        val SHORT_SRC: LuaString? = valueOf("short_src")
        val LINEDEFINED: LuaString? = valueOf("linedefined")
        val LASTLINEDEFINED: LuaString? = valueOf("lastlinedefined")
        val CURRENTLINE: LuaString? = valueOf("currentline")
        val ACTIVELINES: LuaString? = valueOf("activelines")

        fun findupvalue(c: LuaClosure, up: Int): LuaString? {
            if (c.upValues != null && up > 0 && up <= c.upValues.size) {
                if (c.p.upvalues != null && up <= c.p.upvalues!!.size) {
                    // A chunk loaded without debug information still has its
                    // upvalues, it just cannot say what they were called, and
                    // Lua answers that in so many words.
                    return c.p.upvalues!![up - 1]!!.name ?: NO_NAME
                }
                else return LuaString.valueOf("." + up)
            }
            return null
        }

        /** What an upvalue is called where the name was stripped out. */
        private val NO_NAME: LuaString = LuaString.valueOf("(no name)")

        fun lua_assert(x: Boolean) {
            if (!x) throw RuntimeException("lua_assert failed")
        }

        // Return the name info if found, or null if no useful information could be found.
        fun getfuncname(frame: CallFrame): NameWhat? {
            if (!frame.f!!.isclosure()) return net.blueva.luak.lib.DebugLib.NameWhat(frame.f!!.classnamestub(), "Java")
            val p: Prototype = frame.f!!.checkclosure()!!.p
            val pc = frame.pc
            val i: Int = p.code!![pc] /* calling instruction */
            val tm: LuaString
            when (Lua.GET_OPCODE(i)) {
                Lua.OP_CALL, Lua.OP_TAILCALL -> return net.blueva.luak.lib.DebugLib.Companion.getobjname(
                    p,
                    pc,
                    Lua.GETARG_A(i)
                )

                // Both halves read the same, which is how Lua names the
                // function a generic `for` is stepping.
                Lua.OP_TFORCALL -> return net.blueva.luak.lib.DebugLib.NameWhat("for iterator", "for iterator")
                Lua.OP_SELF, Lua.OP_GETTABUP, Lua.OP_GETTABLE -> tm = LuaValue.INDEX
                Lua.OP_SETTABUP, Lua.OP_SETTABLE -> tm = LuaValue.NEWINDEX
                Lua.OP_EQ -> tm = LuaValue.EQ
                Lua.OP_ADD -> tm = LuaValue.ADD
                Lua.OP_SUB -> tm = LuaValue.SUB
                Lua.OP_MUL -> tm = LuaValue.MUL
                Lua.OP_DIV -> tm = LuaValue.DIV
                Lua.OP_IDIV -> tm = LuaValue.IDIV
                Lua.OP_BAND -> tm = LuaValue.BAND
                Lua.OP_BOR -> tm = LuaValue.BOR
                Lua.OP_BXOR -> tm = LuaValue.BXOR
                Lua.OP_SHL -> tm = LuaValue.SHL
                Lua.OP_SHR -> tm = LuaValue.SHR
                Lua.OP_BNOT -> tm = LuaValue.BNOT
                Lua.OP_MOD -> tm = LuaValue.MOD
                Lua.OP_POW -> tm = LuaValue.POW
                Lua.OP_UNM -> tm = LuaValue.UNM
                Lua.OP_LEN -> tm = LuaValue.LEN
                Lua.OP_LT -> tm = LuaValue.LT
                Lua.OP_LE -> tm = LuaValue.LE
                Lua.OP_CONCAT -> tm = LuaValue.CONCAT
                // Leaving a block or a function is where a to-be-closed
                // variable's handler runs, so a frame reached from there is
                // that handler.
                Lua.OP_JMP, Lua.OP_RETURN -> tm = LuaValue.CLOSE
                else -> return null /* else no useful name can be found */
            }
            // The metatag is spelled "__close"; the name reported is "close".
            return net.blueva.luak.lib.DebugLib.NameWhat(tm.tojstring().substring(2), "metamethod")
        }

        // return NameWhat if found, null if not
        fun getobjname(p: Prototype, lastpc: Int, reg: Int): NameWhat? {
            var pc = lastpc // currentpc(L, ci);
            var name: LuaString? = p.getlocalname(reg + 1, pc)
            if (name != null)  /* is a local? */
                return net.blueva.luak.lib.DebugLib.NameWhat(name.tojstring(), "local")

            /* else try symbolic execution */
            pc = net.blueva.luak.lib.DebugLib.Companion.findsetreg(p, lastpc, reg)
            if (pc != -1) { /* could find instruction? */
                val i: Int = p.code!![pc]
                when (Lua.GET_OPCODE(i)) {
                    Lua.OP_MOVE -> {
                        val a: Int = Lua.GETARG_A(i)
                        val b: Int = Lua.GETARG_B(i) /* move from `b' to `a' */
                        if (b < a) return net.blueva.luak.lib.DebugLib.Companion.getobjname(
                            p,
                            pc,
                            b
                        ) /* get name for `b' */
                    }

                    Lua.OP_GETTABUP, Lua.OP_GETTABLE -> {
                        val k: Int = Lua.GETARG_C(i) /* key index */
                        val t: Int = Lua.GETARG_B(i) /* table index */
                        val vn: LuaString? = if (Lua.GET_OPCODE(i) === Lua.OP_GETTABLE)
                            p.getlocalname(t + 1, pc)
                        else
                            (if (t < p.upvalues!!.size) p.upvalues!![t]!!.name else net.blueva.luak.lib.DebugLib.Companion.QMARK)
                        val jname: String = net.blueva.luak.lib.DebugLib.Companion.kname(p, pc, k)
                        return net.blueva.luak.lib.DebugLib.NameWhat(
                            jname,
                            if (vn != null && vn.eq_b(ENV)) "global" else "field"
                        )
                    }

                    Lua.OP_GETUPVAL -> {
                        val u: Int = Lua.GETARG_B(i) /* upvalue index */
                        name =
                            if (u < p.upvalues!!.size) p.upvalues!![u]!!.name else net.blueva.luak.lib.DebugLib.Companion.QMARK
                        return if (name == null) null else net.blueva.luak.lib.DebugLib.NameWhat(
                            name.tojstring(),
                            "upvalue"
                        )
                    }

                    Lua.OP_LOADK, Lua.OP_LOADKX -> {
                        val b: Int = if (Lua.GET_OPCODE(i) === Lua.OP_LOADK)
                            Lua.GETARG_Bx(i)
                        else
                            Lua.GETARG_Ax(p.code!![pc + 1])
                        if (p.k!![b]!!.isstring()) {
                            name = p.k!![b]!!.strvalue()
                            return net.blueva.luak.lib.DebugLib.NameWhat(name!!.tojstring(), "constant")
                        }
                    }

                    Lua.OP_SELF -> {
                        val k: Int = Lua.GETARG_C(i) /* key index */
                        val jname: String = net.blueva.luak.lib.DebugLib.Companion.kname(p, pc, k)
                        return net.blueva.luak.lib.DebugLib.NameWhat(jname, "method")
                    }

                    else -> {}
                }
            }
            return null /* no useful name found */
        }

        fun kname(p: Prototype, pc: Int, c: Int): String {
            if (Lua.ISK(c)) {  /* is 'c' a constant? */
                val k: LuaValue = p.k!![Lua.INDEXK(c)]!!
                if (k.isstring()) {  /* literal constant? */
                    return k.tojstring() /* it is its own name */
                } /* else no reasonable name found */
            } else {  /* 'c' is a register */
                val what: NameWhat? = net.blueva.luak.lib.DebugLib.Companion.getobjname(p, pc, c) /* search for 'c' */
                if (what != null && "constant".equals(what.namewhat)) {  /* found a constant name? */
                    return what.name /* 'name' already filled */
                }
                /* else no reasonable name found */
            }
            return "?" /* no reasonable name found */
        }

        /*
	** try to find last instruction before 'lastpc' that modified register 'reg'
	*/
        fun findsetreg(p: Prototype, lastpc: Int, reg: Int): Int {
            var pc: Int
            var setreg = -1 /* keep last instruction that changed 'reg' */
            pc = 0
            while (pc < lastpc) {
                val i: Int = p.code!![pc]
                val op: Int = Lua.GET_OPCODE(i)
                val a: Int = Lua.GETARG_A(i)
                when (op) {
                    Lua.OP_LOADNIL -> {
                        val b: Int = Lua.GETARG_B(i)
                        if (a <= reg && reg <= a + b)  /* set registers from 'a' to 'a+b' */
                            setreg = pc
                    }

                    Lua.OP_TFORCALL -> {
                        if (reg >= a + 2) setreg = pc /* affect all regs above its base */
                    }

                    Lua.OP_CALL, Lua.OP_TAILCALL -> {
                        if (reg >= a) setreg = pc /* affect all registers above base */
                    }

                    Lua.OP_JMP -> {
                        val b: Int = Lua.GETARG_sBx(i)
                        val dest = pc + 1 + b
                        /* jump is forward and do not skip `lastpc'? */
                        if (pc < dest && dest <= lastpc) pc += b /* do the jump */
                    }

                    Lua.OP_TEST -> {
                        if (reg == a) setreg = pc /* jumped code can change 'a' */
                    }

                    Lua.OP_SETLIST -> {
                        // Lua.testAMode(Lua.OP_SETLIST) == false
                        if (((i shr 14) and 0x1ff) == 0) pc++ // if c == 0 then c stored in next op -> skip
                    }

                    else -> if (Lua.testAMode(op) && reg == a)  /* any instruction that set A */
                        setreg = pc
                }
                pc++
            }
            return setreg
        }
    }
}

/**
 * Calls [f] as a library function calls back into another function.
 *
 * A function of the library's own has no frame of its own to push, so one is
 * pushed for it here: without that a traceback would not name it, the levels
 * a hook counts would skip it, and an error it raises could not say which
 * function it was in. See [DebugLib.notecallback].
 */
internal fun callback(debuglib: DebugLib?, f: LuaValue, args: Varargs): Varargs {
    if (debuglib == null || f !is net.blueva.luak.LuaFunction || f is net.blueva.luak.LuaClosure) {
        return f.invoke(args)!!
    }
    debuglib.onCall(f)
    try {
        return f.invoke(args)!!
    } catch (le: LuaError) {
        debuglib.notecallback(le)
        throw le
    } finally {
        debuglib.onReturn()
    }
}
