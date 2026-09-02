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

import net.blueva.luak.platformUsedMemory
import net.blueva.luak.platformCollectGarbage
import net.blueva.luak.Globals
import net.blueva.luak.Lua
import net.blueva.luak.LuaError
import net.blueva.luak.LuaString
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaThread
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs
import net.blueva.luak.io.ByteArrayInputStream
import net.blueva.luak.io.IOException
import net.blueva.luak.io.InputStream
import net.blueva.luak.io.PlatformFileHandle
import net.blueva.luak.io.PlatformFileMode
import net.blueva.luak.io.platformFilesSupported
import net.blueva.luak.io.platformOpenFile
import net.blueva.luak.io.platformResource
import net.blueva.luak.io.platformStandardInput

/**
 * Subclass of [LibFunction] which implements the lua basic library functions.
 *
 *
 * This contains every function listed as a "basic function" in the Lua manual.
 * `dofile` and `loadfile` resolve names through the [Globals.finder] instance;
 * [BaseLib] is itself the default [ResourceFinder] and looks for an ordinary
 * host file before falling back to the platform's resource namespace, so a
 * plain relative script name works the same on every target. The default
 * loader chain in [PackageLib] uses the same finder.
 *
 *
 * Loading this library also wires [Globals.STDIN] to the host's standard
 * input where it has one.
 *
 *
 * Typically this library is included as part of a call to
 * [net.blueva.luak.lib.LuaPlatform.standardGlobals]:
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * globals.get("print").call(LuaValue.valueOf("hello, world"))
 * ```
 *
 *
 * For special cases where the smallest possible footprint is desired,
 * a minimal set of libraries could be loaded directly via [Globals.load]:
 * ```kotlin
 * val globals = Globals()
 * globals.load(BaseLib())
 * globals.get("print").call(LuaValue.valueOf("hello, world"))
 * ```
 * Doing so will ensure the library is properly initialized
 * and loaded into the globals table.
 *
 *
 * This is a direct port of the corresponding library in C.
 *
 * @see ResourceFinder
 *
 * @see Globals.finder
 *
 * @see LibFunction
 *
 * @see net.blueva.luak.lib.LuaPlatform
 *
 * @see [Lua 5.2 Base Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.1)
 */
open class BaseLib : TwoArgFunction(), ResourceFinder {
    var globals: Globals? = null

    /** Whether `warn` currently emits anything; warnings start switched off. */
    internal var warningsOn: Boolean = false

    /**
     * Emits [text] as a warning, the way `warn` would.
     *
     * The runtime reports what it cannot raise - an error inside a `__gc`
     * handler, which has no caller to raise to - and, like `warn`, says
     * nothing at all until warnings have been switched on.
     */
    internal fun warning(text: String) {
        if (!warningsOn) return
        globals!!.STDERR!!.println("Lua warning: " + text)
    }


    /** Perform one-time initialization on the library by adding base functions
     * to the supplied environment, and returning it as the return value.
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env the environment to load into, which must be a Globals instance.
     */
    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        globals = env!!.checkglobals()
        globals!!.finder = this
        globals!!.baselib = this
        if (globals!!.STDIN == null) globals!!.STDIN = platformStandardInput()
        env!!.set("_G", env)
        env!!.set("_VERSION", Lua._VERSION)
        env!!.set("assert", net.blueva.luak.lib.BaseLib._assert())
        env!!.set("collectgarbage", net.blueva.luak.lib.BaseLib.collectgarbage(this))
        env!!.set("warn", warn(this))
        env!!.set("dofile", dofile())
        env!!.set("error", net.blueva.luak.lib.BaseLib.error())
        env!!.set("getmetatable", net.blueva.luak.lib.BaseLib.getmetatable())
        env!!.set("load", load())
        env!!.set("loadfile", loadfile())
        env!!.set("pcall", pcall())
        env!!.set("print", print(this))
        env!!.set("rawequal", net.blueva.luak.lib.BaseLib.rawequal())
        env!!.set("rawget", net.blueva.luak.lib.BaseLib.rawget())
        env!!.set("rawlen", net.blueva.luak.lib.BaseLib.rawlen())
        env!!.set("rawset", net.blueva.luak.lib.BaseLib.rawset())
        env!!.set("select", net.blueva.luak.lib.BaseLib.select())
        env!!.set("setmetatable", net.blueva.luak.lib.BaseLib.setmetatable(this))
        env!!.set("tonumber", net.blueva.luak.lib.BaseLib.tonumber())
        env!!.set("tostring", net.blueva.luak.lib.BaseLib.tostring())
        env!!.set("type", net.blueva.luak.lib.BaseLib.type())
        env!!.set("xpcall", xpcall())

        val next: next?
        env!!.set("next", net.blueva.luak.lib.BaseLib.next().also { next = it })
        env!!.set("pairs", net.blueva.luak.lib.BaseLib.pairs((next)!!))
        env!!.set("ipairs", net.blueva.luak.lib.BaseLib.ipairs())

        return env
    }

    /**
     * [ResourceFinder] implementation: an ordinary host file first, then the
     * platform's own resource namespace (the classpath on the JVM, the working
     * directory or a pre-opened directory elsewhere).
     *
     * Looking on the filesystem first is what makes `require`, `dofile`, and
     * `loadfile` resolve a plain relative script name identically on every
     * target rather than only where a classpath exists.
     */
    open override fun findResource(filename: String?): InputStream? {
        val name: String = filename ?: return null
        if (platformFilesSupported) {
            try {
                val handle: PlatformFileHandle = platformOpenFile(name, PlatformFileMode.READ)
                try {
                    val bytes = ByteArray(handle.size().toInt())
                    var read = 0
                    while (read < bytes.size) {
                        val count: Int = handle.read(bytes, read, bytes.size - read)
                        if (count < 0) break
                        read += count
                    }
                    return ByteArrayInputStream(bytes, 0, read)
                } finally {
                    handle.close()
                }
            } catch (ignored: IOException) {
                // Not a readable file; fall through to the resource namespace.
            }
        }
        return platformResource(name)
    }


    // "assert", // ( v [,message] ) -> v, message | ERR
    internal class _assert : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            if (args.arg1()!!.toboolean()) return args
            // There has to be something to test in the first place, which is
            // the one complaint `assert` makes about its own arguments.
            if (args.narg() == 0) argerror(1, "value expected")
            // Whatever was given as the message is raised as it stands - only
            // the second argument, and only when there was one - so a table
            // reaches the caller as a table. Without one, the message is the
            // usual text, which being a string is given a place to point at.
            val message: LuaValue = if (args.narg() > 1) args.arg(2)!! else valueOf("assertion failed!")!!
            // A nil message becomes text where it is raised, as `error` does
            // with one, so that a handler always has something to report.
            if (message.isnil()) {
                val failure = LuaError(valueOf("<no error object>"))
                failure.level = 0
                throw failure
            }
            if (message.type() != LuaValue.TSTRING) {
                val failure = LuaError(message)
                failure.level = 0
                throw failure
            }
            throw LuaError(message.tojstring(), 1)
        }
    }

    // "collectgarbage", // ( opt [,arg] ) -> value
    /**
     * `warn (msg1, ...)`, from Lua 5.4.
     *
     * Emits a warning built by joining the arguments. Warnings start switched
     * off and are turned on and off by the control messages `"@on"` and
     * `"@off"`, which are single arguments beginning with `@` and are never
     * shown themselves.
     */
    internal class warn(private val baselib: BaseLib) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val n: Int = args.narg()
            for (i in 1..n) args.checkstring(i)
            if (n == 1) {
                val control: String = args.checkjstring(1)!!
                if (control.startsWith("@")) {
                    if (control == "@on") baselib.warningsOn = true
                    else if (control == "@off") baselib.warningsOn = false
                    return NONE!!
                }
            }
            if (!baselib.warningsOn) return NONE!!
            val message: StringBuilder = StringBuilder("Lua warning: ")
            for (i in 1..n) message.append(args.checkjstring(i))
            baselib.globals!!.STDERR!!.println(message.toString())
            return NONE!!
        }
    }

    internal class collectgarbage(private val baselib: BaseLib) : VarArgFunction() {
        companion object {
            /** The collector mode last asked for; 5.5 starts generational. */
            var mode: String = "generational"

            /** How much of a cycle the steps asked for so far add up to. */
            var stepped: Int = 0

            /** What a cycle's worth of steps comes to. */
            const val CYCLE: Int = 100

            /** The tunables and their Lua 5.5 defaults. */
            val parameters: MutableMap<String, Long> = mutableMapOf(
                "minormul" to 20L,
                "majorminor" to 50L,
                "pause" to 250L,
                "stepmul" to 200L,
                "stepsize" to 9600L,
            )
        }

        override fun invoke(args: Varargs): Varargs {
            val s: String? = args.optjstring(1, "collect")
            if ("collect".equals(s)) {
                platformCollectGarbage()
                baselib.globals!!.runfinalizers()
                baselib.globals!!.memory.collected()
                return (ZERO)!!
            } else if ("count".equals(s)) {
                val used: Long = baselib.globals!!.memory.used()
                return (varargsOf(valueOf(used / 1024.0), valueOf((used % 1024).toInt())))!!
            } else if ("step".equals(s)) {
                // A step of the size asked for, and the answer says whether it
                // was the one that finished a cycle. The host collector runs
                // whole cycles of its own, so what is stepped through here is
                // the debt Lua would have worked off before running one.
                platformCollectGarbage()
                baselib.globals!!.runfinalizers()
                val size: Int = args.optint(2, 0)
                net.blueva.luak.lib.BaseLib.collectgarbage.stepped += if (size > 0) size else 1
                if (net.blueva.luak.lib.BaseLib.collectgarbage.stepped <
                    net.blueva.luak.lib.BaseLib.collectgarbage.CYCLE
                ) {
                    return (LuaValue.FALSE)!!
                }
                net.blueva.luak.lib.BaseLib.collectgarbage.stepped = 0
                baselib.globals!!.memory.collected()
                return (LuaValue.TRUE)!!
            } else if ("isrunning".equals(s)) {
                return (valueOf(baselib.globals!!.memory.running))!!
            } else if ("stop".equals(s)) {
                baselib.globals!!.memory.running = false
                return (ZERO)!!
            } else if ("restart".equals(s)) {
                baselib.globals!!.memory.running = true
                return (ZERO)!!
            } else if ("param".equals(s)) {
                // The host collector is not tunable from here, so a parameter
                // is only remembered. Lua answers the value that was in force.
                val name: String = args.checkjstring(2)!!
                val previous: Long = net.blueva.luak.lib.BaseLib.collectgarbage.parameters[name]
                    ?: return (argerror(2, "invalid option '" + name + "'"))!!
                if (!args.isnoneornil(3)) {
                    net.blueva.luak.lib.BaseLib.collectgarbage.parameters[name] = args.checklong(3)
                }
                return valueOf(previous)!!
            } else if ("generational".equals(s) || "incremental".equals(s)) {
                // The host collector picks its own strategy, so the mode is
                // only remembered, not applied. Lua answers the mode that was
                // in force before the call.
                val previous: String = net.blueva.luak.lib.BaseLib.collectgarbage.mode
                net.blueva.luak.lib.BaseLib.collectgarbage.mode = s!!
                return valueOf(previous)!!
            } else {
                argerror(1, "invalid option '" + s + "'")
            }
            return NIL
        }
    }

    // "dofile", // ( filename ) -> result1, ...
    internal inner class dofile : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            args.argcheck(args.isstring(1) || args.isnil(1), 1, "filename must be string or nil")
            val filename: String? = if (args.isstring(1)) args.tojstring(1) else null
            val v: Varargs = if (filename == null) loadStream(
                globals!!.STDIN,
                "=stdin",
                "bt",
                globals
            ) else loadFile(args.checkjstring(1), "bt", globals)
            return (if (v.isnil(1)) error(v.tojstring(2)) else v.arg1()!!.invoke())!!
        }

        // The chunk runs as part of this call, so a yield inside it has to
        // pass through; see BaseLib.pcall.invokeSuspend().
        override suspend fun invokeSuspend(args: Varargs): Varargs {
            args.argcheck(args.isstring(1) || args.isnil(1), 1, "filename must be string or nil")
            val filename: String? = if (args.isstring(1)) args.tojstring(1) else null
            val v: Varargs = if (filename == null) loadStream(
                globals!!.STDIN,
                "=stdin",
                "bt",
                globals
            ) else loadFile(args.checkjstring(1), "bt", globals)
            return (if (v.isnil(1)) error(v.tojstring(2)) else v.arg1()!!.invokeSuspend(NONE!!))!!
        }
    }

    /**
     * Names the function in a bare "bad argument #N" message.
     *
     * A protected call reaches its callee directly, so there is no call site
     * for the interpreter to read a name from and the message arrives without
     * one. Lua falls back to the name the function goes by in
     * `package.loaded`, which is how `pcall(string.pack, ...)` still reports
     * "bad argument #2 to 'string.pack'".
     */
    internal fun nameArgumentError(failure: LuaError, called: LuaValue) {
        // An index may already have been stamped on; a name has not, and that
        // is what is missing when the call came in through here.
        if (failure.argMessageOverride?.contains(" to '") == true) return
        val message: String = failure.message ?: return
        val match = Regex("^bad argument #(\\d+): ([\\s\\S]*)$").find(message) ?: return
        val name: String = loadedName(called) ?: "?"
        failure.argMessageOverride =
            "bad argument #" + match.groupValues[1] + " to '" + name + "' (" + match.groupValues[2] + ")"
    }

    /**
     * The name [target] goes by in `package.loaded`, such as `"string.pack"`.
     *
     * Only the modules themselves and their immediate fields are searched, as
     * upstream searches, and a function of `_G` keeps its bare name.
     */
    private fun loadedName(target: LuaValue): String? {
        val loaded: LuaValue = globals?.get("package")?.get("loaded") ?: return null
        if (!loaded.istable()) return null
        var moduleKey: Varargs = loaded.next(NIL)!!
        while (!moduleKey.arg1()!!.isnil()) {
            val key: LuaValue = moduleKey.arg1()!!
            val module: LuaValue = moduleKey.arg(2)!!
            if (key.isstring()) {
                val prefix: String = key.tojstring()
                if (module === target) return prefix
                if (module.istable()) {
                    var fieldKey: Varargs = module.next(NIL)!!
                    while (!fieldKey.arg1()!!.isnil()) {
                        if (fieldKey.arg(2) === target && fieldKey.arg1()!!.isstring()) {
                            val field: String = fieldKey.arg1()!!.tojstring()
                            return if (prefix == "_G") field else prefix + "." + field
                        }
                        fieldKey = module.next(fieldKey.arg1()!!)!!
                    }
                }
            }
            moduleKey = loaded.next(key)!!
        }
        return null
    }

    companion object {
        /**
         * Lua's own rendering of a value as a string, upstream's `luaL_tolstring`.
         *
         * A `__tostring` metamethod decides if there is one, and must answer
         * something string-like. Otherwise a value with no text of its own is
         * named by its `__name` metafield, falling back to its type, followed
         * by an identity.
         */
        internal fun tolstring(value: LuaValue): LuaValue {
            val handler: LuaValue = value.metatag(TOSTRING)
            if (!handler.isnil()) {
                val rendered: LuaValue = handler.call(value)!!
                if (!rendered.isstring()) LuaValue.error("'__tostring' must return a string")
                return rendered
            }
            when (value.type()) {
                // A string is already its own rendering, and handing back the
                // same bytes matters: decoding and re-encoding them would
                // mangle any that are not valid UTF-8.
                LuaValue.TSTRING -> return value

                // The other primitives render as themselves, whatever metatable
                // a shared one may carry.
                LuaValue.TNIL, LuaValue.TBOOLEAN, LuaValue.TNUMBER ->
                    return valueOf(value.tojstring())!!
            }
            val own: LuaValue = value.tostring()
            if (!own.isnil()) return own
            val name: LuaValue = value.metatag(net.blueva.luak.LuaValue.NAME)
            if (name.isstring()) {
                val rendered: String = value.tojstring()
                return valueOf(name.tojstring() + ": " + rendered.substringAfter(": ", rendered))!!
            }
            // Otherwise the value's own rendering, which on this runtime is
            // already "type: identity" and, for a Java object behind a
            // userdata, that object's own text.
            return valueOf(value.tojstring())!!
        }
    }

    // "error", // ( message [,level] ) -> ERR
    internal class error : TwoArgFunction() {
        override fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue? {
            // A nil error object becomes text at the point it is raised, so a
            // handler always has something to report.
            if (arg1!!.isnil()) throw LuaError(valueOf("<no error object>"))
            val level: Int = arg2!!.optint(1)
            if (arg1.type() != LuaValue.TSTRING) {
                // Only a string ever gets a position: anything else is the
                // error object itself and has to reach the handler untouched.
                val failure = LuaError(arg1)
                failure.level = 0
                throw failure
            }
            if (level == 0) {
                // Level 0 asks for the message exactly as written, with no
                // position added to it - not even by the interpreter's own
                // error hook, which is what the level records for it.
                val failure = LuaError(arg1)
                failure.level = 0
                throw failure
            }
            throw LuaError(arg1.tojstring(), level)
        }
    }

    // "getmetatable", // ( object ) -> table
    internal class getmetatable : LibFunction() {
        override fun call(): LuaValue? {
            return (argerror(1, "value expected"))!!
        }

        override fun call(arg: LuaValue?): LuaValue? {
            val mt: LuaValue? = arg!!.getmetatable()
            return if (mt != null) mt.rawget(METATABLE).optvalue(mt) else NIL
        }
    }

    // "load", // ( ld [, source [, mode [, env]]] ) -> chunk | nil, msg
    internal inner class load : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val ld: LuaValue = args.arg1()!!
            if (!ld.isstring() && !ld.isfunction()) {
                throw LuaError("bad argument #1 to 'load' (string or function expected, got " + ld.typename() + ")")
            }
            val source: String? = args.optjstring(2, if (ld.isstring()) ld.tojstring() else "=(load)")
            val mode: String? = args.optjstring(3, "bt")
            // 'B' asks for a fixed buffer, which only the C API can supply.
            if (mode != null && mode.indexOf('B') >= 0) argerror(3, "invalid mode")
            val env: LuaValue? = args.optvalue(4, globals)
            return loadStream(
                if (ld.isstring()) ld.strvalue()
                    !!.toInputStream() else net.blueva.luak.lib.BaseLib.StringInputStream((ld.checkfunction())!!),
                source,
                mode,
                env
            )
        }
    }

    // "loadfile", // ( [filename [, mode [, env]]] ) -> chunk | nil, msg
    internal inner class loadfile : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            args.argcheck(args.isstring(1) || args.isnil(1), 1, "filename must be string or nil")
            val filename: String? = if (args.isstring(1)) args.tojstring(1) else null
            val mode: String? = args.optjstring(2, "bt")
            val env: LuaValue? = args.optvalue(3, globals)
            return if (filename == null) loadStream(globals!!.STDIN, "=stdin", mode, env) else loadFile(
                filename,
                mode,
                env
            )
        }
    }

    // "pcall", // (f, arg1, ...) -> status, result1, ...
    internal inner class pcall : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val func: LuaValue = args.checkvalue(1)!!
            // Shadow any outer xpcall's message handler while this pcall's own
            // protected region is active: an error raised in here belongs to
            // THIS pcall, not to an unrelated, further-out xpcall, matching
            // real Lua's "nearest enclosing protected call" semantics. Without
            // this, LuaClosure.errorHook() would still see the outer handler
            // via LuaThread.errorfunc and wrongly invoke it for an error this
            // pcall is about to catch itself.
            val t: LuaThread? = globals?.running
            val preverror: LuaValue? = t?.errorfunc
            if (t != null) t.errorfunc = null
            try {
                return (varargsOf(TRUE, (protectedCall(t, func, args.subargs(2)!!))!!))!!
            } catch (le: LuaError) {
                nameArgumentError(le, func)
                val m: LuaValue? = le.messageObject
                return (varargsOf(FALSE, if (m != null) m else NIL))!!
            } catch (e: Exception) {
                val m: String? = e.message
                return (varargsOf(FALSE, valueOf(if (m != null) m else e.toString())))!!
            } catch (deep: Throwable) {
                // Unbounded recursion exhausts the host's stack rather than a
                // stack of Lua's own; a protected call is where that becomes
                // the ordinary Lua error the caller expects. The conversion
                // happens here, not deeper in, because building the error needs
                // some stack back.
                if (!net.blueva.luak.platformIsStackOverflow(deep)) throw deep
                return (varargsOf(FALSE, valueOf(overflowmessage(t))))!!
            } finally {
                if (t != null) t.errorfunc = preverror
            }
        }

        // Real Lua 5.2 lets a coroutine yield across a pcall boundary; route
        // the protected call through invokeSuspend() (instead of the plain
        // invoke() above) so a nested coroutine.yield() propagates correctly
        // instead of hitting the C-call boundary error.
        override suspend fun invokeSuspend(args: Varargs): Varargs {
            val func: LuaValue = args.checkvalue(1)!!
            // See the non-suspend invoke() above for why errorfunc is shadowed.
            val t: LuaThread? = globals?.running
            val preverror: LuaValue? = t?.errorfunc
            if (t != null) t.errorfunc = null
            try {
                return (varargsOf(TRUE, (protectedCallSuspend(t, func, args.subargs(2)!!))!!))!!
            } catch (le: LuaError) {
                nameArgumentError(le, func)
                val m: LuaValue? = le.messageObject
                return (varargsOf(FALSE, if (m != null) m else NIL))!!
            } catch (e: Exception) {
                val m: String? = e.message
                return (varargsOf(FALSE, valueOf(if (m != null) m else e.toString())))!!
            } catch (deep: Throwable) {
                // Unbounded recursion exhausts the host's stack rather than a
                // stack of Lua's own; a protected call is where that becomes
                // the ordinary Lua error the caller expects. The conversion
                // happens here, not deeper in, because building the error needs
                // some stack back.
                if (!net.blueva.luak.platformIsStackOverflow(deep)) throw deep
                return (varargsOf(FALSE, valueOf(overflowmessage(t))))!!
            } finally {
                if (t != null) t.errorfunc = preverror
            }
        }
    }

    // "print", // (...) -> void
    internal inner class print(val baselib: BaseLib) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            // Rendered here rather than through the global `tostring`: Lua
            // uses its own conversion, so replacing that global changes what
            // scripts see from `tostring` and leaves `print` alone.
            var i = 1
            val n: Int = args.narg()
            while (i <= n) {
                if (i > 1) globals!!.STDOUT!!.print('\t')
                val s: LuaString = net.blueva.luak.lib.BaseLib.tolstring(args.arg(i)!!).strvalue()!!
                globals!!.STDOUT!!.print(s.tojstring())
                i++
            }
            globals!!.STDOUT!!.print('\n')
            return (NONE)!!
        }
    }


    // "rawequal", // (v1, v2) -> boolean
    internal class rawequal : LibFunction() {
        override fun call(): LuaValue? {
            return (argerror(1, "value expected"))!!
        }

        override fun call(arg: LuaValue?): LuaValue? {
            return (argerror(2, "value expected"))!!
        }

        override fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue? {
            return (valueOf(arg1!!.raweq(arg2)))!!
        }
    }

    // "rawget", // (table, index) -> value
    internal class rawget : TableLibFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            return (argerror(2, "value expected"))!!
        }

        override fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue? {
            return arg1!!.checktable()!!.rawget(arg2)
        }
    }


    // "rawlen", // (v) -> value
    internal class rawlen : LibFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            return valueOf(arg!!.rawlen())
        }
    }

    // "rawset", // (table, index, value) -> table
    internal class rawset : TableLibFunction() {
        override fun call(table: LuaValue?): LuaValue? {
            return (argerror(2, "value expected"))!!
        }

        override fun call(table: LuaValue?, index: LuaValue?): LuaValue? {
            return (argerror(3, "value expected"))!!
        }

        override fun call(table: LuaValue?, index: LuaValue?, value: LuaValue?): LuaValue? {
            val t: LuaTable = table!!.checktable()!!
            if (!index!!.isvalidkey()) argerror(2, "table index is nil")
            t.rawset(index, value)
            return t
        }
    }

    // "select", // (f, ...) -> value1, ...
    internal class select : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val n: Int = args.narg() - 1
            if (args.arg1()!!.equals(valueOf("#"))) return valueOf(n)
            val i: Int = args.checkint(1)
            if (i == 0 || i < -n) argerror(1, "index out of range")
            return (args.subargs(if (i < 0) n + i + 2 else i + 1))!!
        }
    }

    // "setmetatable", // (table, metatable) -> table
    internal class setmetatable(private val baselib: BaseLib) : TableLibFunction() {
        override fun call(table: LuaValue?): LuaValue? {
            // What it was given is looked at first, as Lua looks at it: being
            // handed something that is not a table is the more useful thing to
            // be told about than the missing second argument. Named as the
            // argument it is, so that a library function given this one as a
            // callback can be told which argument was wrong.
            if (!table!!.istable()) {
                argerror(1, "table expected, got " + table.argtypename())
            }
            return (argerror(2, "nil or table expected"))!!
        }

        override fun call(table: LuaValue?, metatable: LuaValue?): LuaValue? {
            // Named as the argument it is, so that a library function given
            // this one as a callback can be told which argument was wrong.
            if (!table!!.istable()) {
                argerror(1, "table expected, got " + table.argtypename())
            }
            val mt0: LuaValue? = table!!.checktable()!!.getmetatable()
            if (mt0 != null && !mt0.rawget(METATABLE).isnil()) error("cannot change a protected metatable")
            val mt: LuaValue? = if (metatable!!.isnil()) null else metatable!!.checktable()
            val answer: LuaValue = table!!.setmetatable(mt)!!
            // Setting the metatable is where Lua decides an object is to be
            // finalized, and the only place it decides it.
            if (mt != null && !mt.rawget(GC).isnil()) baselib.globals!!.markforfinalization(table)
            return answer
        }
    }

    // "tonumber", // (e [,base]) -> value
    internal class tonumber : LibFunction() {
        override fun call(e: LuaValue?): LuaValue? {
            return e!!.tonumber()
        }

        override fun call(e: LuaValue?, base: LuaValue?): LuaValue? {
            if (base!!.isnil()) return e!!.tonumber()
            val b: Int = base!!.checkint()
            if (b < 2 || b > 36) argerror(2, "base out of range")
            return (e!!.checkstring()!!.tonumber(b))!!
        }
    }

    // "tostring", // (e) -> value
    internal class tostring : LibFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            return net.blueva.luak.lib.BaseLib.tolstring(arg!!)
        }
    }

    // "type",  // (v) -> value
    internal class type : LibFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            return valueOf(arg!!.typename())
        }
    }

    // "xpcall", // (f, err) -> result1, ...
    internal inner class xpcall : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val t: LuaThread = globals!!.running
            val preverror: LuaValue? = t.errorfunc
            t.errorfunc = args.checkvalue(2)
            try {
                try {
                    return (varargsOf(TRUE, (protectedCall(t, args.arg1()!!, args.subargs(3)!!))!!))!!
                } catch (le: LuaError) {
                    nameArgumentError(le, args.arg1()!!)
                    if (le.traceback == null) {
                        // Error raised directly from native/library code (e.g. calling a
                        // non-function) never passed through a LuaClosure's error hook, so
                        // the message handler hasn't run yet. Run it here exactly once,
                        // matching real Lua's xpcall: if the handler itself fails or isn't
                        // callable, substitute the standard "error in error handling".
                        return (varargsOf(FALSE, runMessageHandler(t, le.messageObject ?: NIL)))!!
                    }
                    val m: LuaValue? = le.messageObject
                    return (varargsOf(FALSE, if (m != null) m else NIL))!!
                } catch (e: Exception) {
                    val m: String? = e.message
                    return (varargsOf(FALSE, valueOf(if (m != null) m else e.toString())))!!
                } catch (overflow: Throwable) {
                    // See pcall: a host stack overflow becomes a Lua error here.
                    if (!net.blueva.luak.platformIsStackOverflow(overflow)) throw overflow
                    // The stack has unwound by the time this is reached, so
                    // there is room to run the handler over it.
                    return (varargsOf(FALSE, runMessageHandler(t, valueOf(overflowmessage(t)))))!!
                }
            } finally {
                t.errorfunc = preverror
            }
        }

        // See BaseLib.pcall.invokeSuspend(): lets a yield inside the protected
        // function propagate across this xpcall boundary instead of hitting
        // the C-call boundary error, matching real Lua 5.2.
        override suspend fun invokeSuspend(args: Varargs): Varargs {
            val t: LuaThread = globals!!.running
            val preverror: LuaValue? = t.errorfunc
            t.errorfunc = args.checkvalue(2)
            try {
                try {
                    return (varargsOf(TRUE, (protectedCallSuspend(t, args.arg1()!!, args.subargs(3)!!))!!))!!
                } catch (le: LuaError) {
                    if (le.traceback == null) {
                        return (varargsOf(FALSE, runMessageHandler(t, le.messageObject ?: NIL)))!!
                    }
                    val m: LuaValue? = le.messageObject
                    return (varargsOf(FALSE, if (m != null) m else NIL))!!
                } catch (e: Exception) {
                    val m: String? = e.message
                    return (varargsOf(FALSE, valueOf(if (m != null) m else e.toString())))!!
                } catch (overflow: Throwable) {
                    // See pcall: a host stack overflow becomes a Lua error here.
                    if (!net.blueva.luak.platformIsStackOverflow(overflow)) throw overflow
                    // The stack has unwound by the time this is reached, so
                    // there is room to run the handler over it.
                    return (varargsOf(FALSE, runMessageHandler(t, valueOf(overflowmessage(t)))))!!
                }
            } finally {
                t.errorfunc = preverror
            }
        }

        private fun runMessageHandler(t: LuaThread, errval: LuaValue): LuaValue {
            val handler = t.errorfunc ?: return errval
            // The handler stays installed while it runs, so an error it raises
            // is handled in its turn, and the nesting is bounded rather than
            // left to run away; see LuaClosure.errorHook.
            if (t.state.foreigncalls >= LuaThread.State.MAX_HANDLER_CALLS) {
                return valueOf("error in error handling")!!
            }
            // A handler written in Lua is counted where it enters the
            // interpreter; one of the library's own never does, and is
            // counted here so that a chain of them cannot go round for ever.
            val outer: Int = t.state.foreigncalls
            try {
                if (handler !is net.blueva.luak.LuaClosure) t.state.foreigncalls++
                t.state.inhandler++
                return handler.call(errval) ?: NIL
            } catch (nested: LuaError) {
                // The handler raised in its turn. An error that has already
                // been through the handler is the answer as it stands; one
                // that has not goes through it now, until the room kept for
                // handling runs out.
                if (nested.traceback != null) return nested.messageObject ?: NIL
                return runMessageHandler(t, nested.messageObject ?: NIL)
            } catch (ignored: Throwable) {
                return valueOf("error in error handling")!!
            } finally {
                t.state.inhandler--
                t.state.foreigncalls = outer
            }
        }
    }

    // "pairs" (t) -> iter-func, t, nil, closing
    internal class pairs(val next: BaseLib.next) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val self: LuaValue = args.checkvalue(1)!!
            val handler: LuaValue = self.metatag(LuaValue.PAIRS)
            if (handler.isnil()) return varargsOf(next, args.checktable(1), NIL)
            // A __pairs metamethod supplies the whole loop, four values and no
            // more: the fourth is what the generic for closes when it ends.
            return four(handler.invoke(self))
        }

        // The metamethod may yield, so a coroutine's 'for' can be driven from
        // inside it; see BaseLib.pcall.invokeSuspend().
        override suspend fun invokeSuspend(args: Varargs): Varargs {
            val self: LuaValue = args.checkvalue(1)!!
            val handler: LuaValue = self.metatag(LuaValue.PAIRS)
            if (handler.isnil()) return varargsOf(next, args.checktable(1), NIL)
            return four(handler.invokeSuspend(self))
        }

        /** The first four of [supplied], padded with nil, as Lua asks for. */
        private fun four(supplied: Varargs): Varargs = varargsOf(
            arrayOf(supplied.arg(1), supplied.arg(2), supplied.arg(3), supplied.arg(4)),
        )!!
    }

    // // "ipairs", // (t) -> iter-func, t, 0
    internal class ipairs : VarArgFunction() {
        var inext: inext = net.blueva.luak.lib.BaseLib.inext()
        override fun invoke(args: Varargs): Varargs {
            // Anything indexable will do: the iterator reads with ordinary
            // indexing, so an __index metamethod is honoured.
            return varargsOf(inext, args.checkvalue(1), (ZERO)!!)
        }
    }

    // "next"  ( table, [index] ) -> next-index, next-value
    internal class next : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            return (args.checktable(1).next(args.arg(2)))!!
        }
    }

    // "inext" ( table, [int-index] ) -> next-index, next-value
    /**
     * The iterator `ipairs` hands back.
     *
     * Counts up from the given index and stops at the first nil. The step
     * wraps around at the maximum integer, as every integer operation in Lua
     * does, so an iteration that reaches it ends rather than trapping.
     */
    internal class inext : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val list: LuaValue = args.checkvalue(1)!!
            val index: Long = args.checklong(2) + 1L
            val value: LuaValue = list.get(valueOf(index))
            return if (value.isnil()) NIL else varargsOf(valueOf(index), value)!!
        }
    }

    /**
     * Load from a named file, returning the chunk or nil,error of can't load
     * @param env
     * @param mode
     * @return Varargs containing chunk, or NIL,error-text on error
     */
    fun loadFile(filename: String?, mode: String?, env: LuaValue?): Varargs {
        val `is`: InputStream? = globals!!.finder!!.findResource(filename)
        if (`is` == null) return (varargsOf(NIL, valueOf("cannot open " + filename + ": No such file or directory")))!!
        try {
            return loadStream(`is`, "@" + filename, mode, env)
        } finally {
            try {
                `is`.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadStream(`is`: InputStream?, chunkname: String?, mode: String?, env: LuaValue?): Varargs {
        try {
            if (`is` == null) return (varargsOf(NIL, valueOf("not found: " + chunkname)))!!
            return (globals!!.load(`is`, chunkname, (mode)!!, env))!!
        } catch (e: Exception) {
            return (varargsOf(NIL, valueOf(e.message)))!!
        }
    }


    private class StringInputStream(func: LuaValue) : InputStream() {
        val func: LuaValue
        var bytes: ByteArray = ByteArray(0)
        var offset: Int = 0
        var remaining: Int = 0

        init {
            this.func = func
        }

        @kotlin.Throws(IOException::class)
        override fun read(): Int {
            if (remaining < 0) return -1
            if (remaining == 0) {
                val s: LuaValue = func.call()!!
                if (s.isnil()) return (-1).also { remaining = it }
                val ls: LuaString = s.strvalue()!!
                bytes = ls.m_bytes
                offset = ls.m_offset
                remaining = ls.m_length
                if (remaining <= 0) return -1
            }
            --remaining
            return 0xFF and bytes[offset++].toInt()
        }
    }
}

/**
 * Runs the protected function, counting the re-entry into Lua.
 *
 * See [LuaThread.State.foreigncalls]: a protected call recurses on the
 * host stack, so Lua counts it and stops before the stack is gone.
 */
private fun protectedCall(t: LuaThread?, f: LuaValue, args: Varargs): Varargs {
    val state: LuaThread.State = t?.state ?: return f.invoke(args)
    // A protected call is where the tally goes back to what it was, however
    // the call ends; see LuaClosure.enterforeign.
    val outer: Int = state.foreigncalls
    val debuglib: DebugLib? = frameFor(t, f)
    if (debuglib != null) debuglib.onCall(f as net.blueva.luak.LuaFunction)
    try {
        enterForeign(state)
        return f.invoke(args)
    } finally {
        state.foreigncalls = outer
        if (debuglib != null) debuglib.onReturn()
    }
}

private suspend fun protectedCallSuspend(t: LuaThread?, f: LuaValue, args: Varargs): Varargs {
    val state: LuaThread.State = t?.state ?: return f.invokeSuspend(args)
    val outer: Int = state.foreigncalls
    val debuglib: DebugLib? = frameFor(t, f)
    if (debuglib != null) debuglib.onCall(f as net.blueva.luak.LuaFunction)
    try {
        enterForeign(state)
        return f.invokeSuspend(args)
    } finally {
        state.foreigncalls = outer
        if (debuglib != null) debuglib.onReturn()
    }
}

/**
 * The debug library to push a frame on for [f], or null for none.
 *
 * A function written in Lua pushes its own frame as it starts; one of the
 * library's own has none, so whoever calls it pushes one for it. Without that
 * the levels a traceback counts would skip it.
 */
private fun frameFor(t: LuaThread?, f: LuaValue): DebugLib? {
    if (f !is net.blueva.luak.LuaFunction || f is net.blueva.luak.LuaClosure) return null
    return t?.globals?.debuglib
}

/**
 * What a host stack overflow is reported as on [t].
 *
 * Running out of stack in the room kept for handling an error is a failure of
 * the handling; see LuaThread.State.inhandler.
 */
private fun overflowmessage(t: LuaThread?): String =
    if ((t?.state?.inhandler ?: 0) > 0) "error in error handling" else "C stack overflow"

private fun enterForeign(state: LuaThread.State) {
    net.blueva.luak.enterForeignCall(state)
}
