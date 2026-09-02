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

import net.blueva.luak.io.IOException
import net.blueva.luak.Globals
import net.blueva.luak.LuaFunction
import net.blueva.luak.LuaString
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaValue
import net.blueva.luak.platformLoadLibrary
import net.blueva.luak.platformProperty
import net.blueva.luak.Varargs
import net.blueva.luak.io.InputStream

/**
 * Subclass of [LibFunction] which implements the lua standard package and module
 * library functions.
 * 
 * <h3>Lua Environment Variables</h3>
 * The following variables are available to lua scrips when this library has been loaded:
 * 
 *  * `"package.loaded"` Lua table of loaded modules.
 *  * `"package.path"` Search path for lua scripts.
 *  * `"package.preload"` Lua table of uninitialized preload functions.
 *  * `"package.searchers"` Lua table of functions that search for object to load.
 * 
 * 
 * <h3>Host Configuration</h3>
 * These host properties affect the library behavior:
 * 
 *  * `"luak.package.path"` Initial value for `"package.path"`.  Default value is `"?.lua"`
 * 
 * 
 * <h3>Loading</h3>
 * Typically, this library is included as part of a call to
 * [net.blueva.luak.lib.LuaPlatform.standardGlobals]
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * println(globals.get("require").call("foo"))
 * ```
 * 
 * 
 * To instantiate and use it directly,
 * link it into your globals table via [LuaValue.load] using code such as:
 * ```kotlin
 * val globals = Globals()
 * globals.load(BaseLib())
 * globals.load(PackageLib())
 * println(globals.get("require").call("foo"))
 * ```
 * <h3>Limitations</h3>
 * This library has been implemented to match as closely as possible the behavior in the corresponding library in C.
 * However, the default filesystem search semantics are different and delegated to the bas library
 * as outlined in the [BaseLib] documentation.
 * 
 * 
 * @see LibFunction
 * 
 * @see BaseLib
 * 
 * 
 * @see net.blueva.luak.lib.jvm.JvmPlatform
 * 
 * @see net.blueva.luak.lib.LuaPlatform
 * 
 * @see [Lua 5.2 Package Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.3)
 */
class PackageLib : TwoArgFunction() {
    /** The globals that were used to load this library.  */
    var globals: Globals? = null

    /** The table for this package.  */
    var package_: LuaTable? = null

    /** Loader that loads from `preload` table if found there  */
    var preloadSearcher: preload_searcher? = null

    /** Loader that loads as a lua script using the lua path currently in [path]  */
    var luaSearcher: lua_searcher? = null

    /** Loader that loads as a Java class.  Class must have public constructor and be a LuaValue.  */
    var javaSearcher: java_searcher? = null

    /** Perform one-time initialization on the library by adding package functions
     * to the supplied environment, and returning it as the return value.
     * It also creates the package.preload and package.loaded tables for use by
     * other libraries.
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env the environment to load into, typically a Globals instance.
     */
    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        globals = env!!.checkglobals()
        globals!!.set("require", require())
        package_ = LuaTable()
        package_!!.set(net.blueva.luak.lib.PackageLib.Companion._LOADED, LuaTable())
        package_!!.set(net.blueva.luak.lib.PackageLib.Companion._PRELOAD, LuaTable())
        package_!!.set(
            net.blueva.luak.lib.PackageLib.Companion._PATH,
            LuaValue.valueOf(net.blueva.luak.lib.PackageLib.Companion.DEFAULT_LUA_PATH)
        )
        package_!!.set(net.blueva.luak.lib.PackageLib.Companion._LOADLIB, net.blueva.luak.lib.PackageLib.loadlib())
        package_!!.set(Companion._SEARCHPATH, searchpath())
        val searchers: LuaTable = LuaTable()
        searchers.set(1, preload_searcher().also { preloadSearcher = it })
        searchers.set(2, lua_searcher().also { luaSearcher = it })
        searchers.set(3, java_searcher().also { javaSearcher = it })
        package_!!.set(net.blueva.luak.lib.PackageLib.Companion._SEARCHERS, searchers)
        // No C loader here, so the path for one is empty rather than absent:
        // a chunk that reads package.cpath still finds a string.
        package_!!.set("cpath", "")
        package_!!.set("config", net.blueva.luak.lib.PackageLib.Companion.FILE_SEP.toString() + "\n;\n?\n!\n-\n")
        package_!!.get((net.blueva.luak.lib.PackageLib.Companion._LOADED)!!).set("package", package_)
        // The globals table is a loaded module too, under the name Lua gives
        // it. Among other things that is where an error message looks to find
        // out what a plain global function is called.
        package_!!.get((net.blueva.luak.lib.PackageLib.Companion._LOADED)!!).set("_G", env)
        env!!.set("package", package_)
        globals!!.package_ = this
        return env
    }

    /** Allow packages to mark themselves as loaded  */
    fun setIsLoaded(name: String?, value: LuaTable?) {
        package_!!.get((net.blueva.luak.lib.PackageLib.Companion._LOADED)!!).set(name, value)
    }


    /** Set the lua path used by this library instance to a new value.
     * Merely sets the value of [path] to be used in subsequent searches.  */
    fun setLuaPath(newLuaPath: String?) {
        package_!!.set(net.blueva.luak.lib.PackageLib.Companion._PATH, LuaValue.valueOf(newLuaPath))
    }

    override fun tojstring(): String {
        return "package"
    }

    // ======================== Package loading =============================
    /**
     * require (modname)
     * 
     * Loads the given module. The function starts by looking into the package.loaded table
     * to determine whether modname is already loaded. If it is, then require returns the value
     * stored at package.loaded[modname]. Otherwise, it tries to find a loader for the module.
     * 
     * To find a loader, require is guided by the package.searchers sequence.
     * By changing this sequence, we can change how require looks for a module.
     * The following explanation is based on the default configuration for package.searchers.
     * 
     * First require queries package.preload[modname]. If it has a value, this value
     * (which should be a function) is the loader. Otherwise require searches for a Lua loader using
     * the path stored in package.path. If that also fails, it searches for a Java loader using
     * the classpath, using the public default constructor, and casting the instance to LuaFunction.
     * 
     * Once a loader is found, require calls the loader with two arguments: modname and an extra value
     * dependent on how it got the loader. If the loader came from a file, this extra value is the file name.
     * If the loader is a Java instance of LuaFunction, this extra value is the environment.
     * If the loader returns any non-nil value, require assigns the returned value to package.loaded[modname].
     * If the loader does not return a non-nil value and has not assigned any value to package.loaded[modname],
     * then require assigns true to this entry.
     * In any case, require returns the final value of package.loaded[modname].
     * 
     * If there is any error loading or running the module, or if it cannot find any loader for the module,
     * then require raises an error.
     */
    inner class require : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val arg: LuaValue = args.checkvalue(1)!!
            val name: LuaString? = arg.checkstring()
            val loaded: LuaValue = package_!!.get((net.blueva.luak.lib.PackageLib.Companion._LOADED)!!)
            var result: LuaValue = loaded.get((name)!!)
            if (result.toboolean()) {
                if (result === net.blueva.luak.lib.PackageLib.Companion._SENTINEL) error("loop or previous error loading module '" + name + "'")
                // Already loaded: the module and nothing else, since there was
                // no search this time to say where it came from.
                return result
            }


            /* else must load it; iterate over available loaders */
            val searchers: LuaValue = package_!!.get((net.blueva.luak.lib.PackageLib.Companion._SEARCHERS)!!)
            // Where the searchers are is set up by whoever built the state, so
            // this is a fault in that rather than in the module being asked for.
            if (!searchers.istable()) LuaValue.error("'package.searchers' must be a table")
            val tbl: LuaTable = searchers.checktable()!!
            val sb: StringBuilder = StringBuilder()
            var loader: Varargs? = null
            var i = 1
            while (true) {
                val searcher: LuaValue = tbl.get(i)
                if (searcher.isnil()) {
                    // One line per searcher that had nothing, and no repeat of
                    // the name: the searchers already say what they looked for.
                    error("module '" + name + "' not found:" + sb)
                }


                /* call loader with module name as argument */
                loader = searcher.invoke((name)!!)
                if (loader!!.isfunction(1)) break
                if (loader!!.isstring(1)) {
                    val report: String = loader!!.tojstring(1)
                    if (!report.startsWith("\n")) sb.append("\n\t")
                    sb.append(report)
                }
                i++
            }


            // load the module using the loader
            loaded.set(name, net.blueva.luak.lib.PackageLib.Companion._SENTINEL)
            result = loader!!.arg1()!!.call(name, loader!!.arg(2))!!
            if (!result.isnil()) loaded.set(name, result)
            else if ((loaded.get((name)!!)
                    .also { result = it }) === net.blueva.luak.lib.PackageLib.Companion._SENTINEL
            ) loaded.set(name, LuaValue.TRUE!!.also { result = it })
            // What the search found alongside the loader - the file it came
            // from - is handed back with it, which is what lets a module say
            // where it was loaded from.
            return varargsOf(result, loader!!.arg(2)!!)!!
        }
    }

    class loadlib : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            args.checkstring(1)
            return varargsOf(NIL, valueOf("dynamic libraries not enabled"), valueOf("absent"))
        }
    }

    inner class preload_searcher : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val name: LuaString? = args.checkstring(1)
            val `val`: LuaValue = package_!!.get((net.blueva.luak.lib.PackageLib.Companion._PRELOAD)!!).get((name)!!)
            if (`val`.isnil()) return valueOf("\n\tno field package.preload['" + name + "']")!!
            // Where it came from, for a module that wants to know: nowhere in
            // particular, which Lua says in so many words.
            return varargsOf(`val`, valueOf(":preload:"))!!
        }
    }

    inner class lua_searcher : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val name: LuaString? = args.checkstring(1)


            // get package path
            val path: LuaValue = package_!!.get((net.blueva.luak.lib.PackageLib.Companion._PATH)!!)
            // Not something to look in, so nothing was looked in: this is a
            // fault in how the search was set up rather than a module that
            // could not be found.
            if (!path.isstring()) LuaValue.error("'package.path' must be a string")


            // get the searchpath function.
            var v: Varargs =
                package_!!.get((net.blueva.luak.lib.PackageLib.Companion._SEARCHPATH)!!).invoke((varargsOf(name, path))!!)


            // Did we get a result?
            // searchpath already lists one "no file" line per template.
            if (!v.isstring(1)) return v.arg(2)!!.tostring()
            val filename: LuaString = v.arg1()!!.strvalue()!!


            // Try to load the file.
            val loaded: Varargs = try {
                globals!!.loadfile(filename.tojstring()) ?: NIL
            } catch (le: net.blueva.luak.LuaError) {
                badmodule(name, filename, le.message)
            }
            if (loaded.arg1().isfunction()) return (LuaValue.varargsOf(loaded.arg1(), filename))!!

            // A file that is there but cannot be loaded is not a module that
            // was not found: the search is over and this is what went wrong.
            badmodule(name, filename, loaded.arg(2)!!.tojstring())
        }
    }

    /** Refuses a module that is there but cannot be loaded, as Lua words it. */
    private fun badmodule(name: LuaString?, filename: LuaString, why: String?): Nothing {
        throw net.blueva.luak.LuaError(
            "error loading module '" + name + "' from file '" + filename + "':\n\t" + why
        )
    }

    inner class searchpath : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            var name: String = args.checkjstring(1)
            val path: String = args.checkjstring(2)
            val sep: String = args.optjstring(3, ".")!!
            val rep: String = args.optjstring(4, net.blueva.luak.lib.PackageLib.Companion.FILE_SEP)!!


            // check the path elements
            var e = -1
            val n: Int = path.length
            var sb: StringBuilder? = null
            // The separator is a piece of text, not a character: what is
            // replaced is every run of it, and an empty one replaces nothing.
            if (sep.isNotEmpty() && name.contains(sep)) name = name.replace(sep, rep)
            while (e < n) {
                // find next template

                val b = e + 1
                e = path.indexOf(';', b)
                if (e < 0) e = path.length
                val template: String = path.substring(b, e)


                // create filename: every '?' stands for the name, not just
                // the first one
                val filename: String = template.replace("?", name)


                // try opening the file
                val `is`: InputStream? = globals!!.finder!!.findResource(filename)
                if (`is` != null) {
                    try {
                        `is`.close()
                    } catch (ioe: IOException) {
                    }
                    return valueOf(filename)
                }


                // report error
                if (sb == null) sb = StringBuilder()
                // One line per template that did not match, worded the way Lua
                // words it so a caller can read the list back.
                if (sb.isNotEmpty()) sb.append("\n\t")
                sb.append("no file '" + filename + "'")
            }
            return (varargsOf(NIL, valueOf(sb.toString())))!!
        }
    }

    /**
     * The searcher for a module that is not written in Lua.
     *
     * A reference build looks for a library to load through `package.cpath`;
     * there is none to load here, so what stands in its place is a class of
     * the host's, looked up by the module's name. The places `package.cpath`
     * names are still walked and still reported, so a module that is nowhere
     * to be found says where it was looked for in the words Lua uses.
     */
    inner class java_searcher : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val name: String = args.checkjstring(1)
            val className = toClassname(name)!!
            try {
                val value = platformLoadLibrary(className, globals!!)
                if (value != null) return varargsOf(value, globals!!)!!
            } catch (error: Throwable) {
                // Nothing of the host's under that name either; fall through
                // to reporting where it was looked for.
            }
            val cpath: LuaValue = package_!!.get("cpath")!!
            if (!cpath.isstring()) LuaValue.error("'package.cpath' must be a string")
            val found: Varargs = package_!!.get((net.blueva.luak.lib.PackageLib.Companion._SEARCHPATH)!!)
                .invoke((varargsOf(valueOf(name), cpath))!!)
            // A file that is there is still not something that can be loaded,
            // so what comes back is only ever the list of places looked in.
            if (found.isstring(1)) {
                return valueOf("\n\tno file '" + found.arg1()!!.tojstring() + "'")
            }
            val why: LuaValue = found.arg(2)!!
            if (why.isnil()) return valueOf("\n\tno class '$className'")
            return valueOf("\n\t" + why.tojstring())
        }
    }

    companion object {
        /** The default value to use for package.path.  This can be set with the host
         * property `"luak.package.path"`, and is `"?.lua"` by default.  The
         * inherited `"luaj.package.path"` spelling is still honoured.  */
        val DEFAULT_LUA_PATH: String =
            platformProperty("luak.package.path")
                ?: platformProperty("luaj.package.path")
                ?: "?.lua"

        val _LOADED: LuaString? = valueOf("loaded")
        private val _LOADLIB: LuaString? = valueOf("loadlib")
        val _PRELOAD: LuaString? = valueOf("preload")
        val _PATH: LuaString? = valueOf("path")
        val _SEARCHPATH: LuaString? = valueOf("searchpath")
        val _SEARCHERS: LuaString? = valueOf("searchers")

        private val _SENTINEL: LuaString? = valueOf("\u0001")

        private val FILE_SEP: String = platformProperty("file.separator") ?: "/"

        /** Convert lua filename to valid class name  */
        fun toClassname(filename: String): String? {
            val n: Int = filename.length
            var j = n
            if (filename.endsWith(".lua")) j -= 4
            for (k in 0..<j) {
                var c: Char = filename[k]
                if ((!net.blueva.luak.lib.PackageLib.Companion.isClassnamePart(c)) || (c == '/') || (c == '\\')) {
                    val sb: StringBuilder = StringBuilder(j)
                    for (i in 0..<j) {
                        c = filename[i]
                        sb.append(
                            if (net.blueva.luak.lib.PackageLib.Companion.isClassnamePart(c)) c else if ((c == '/') || (c == '\\')) '.' else '_'
                        )
                    }
                    return sb.toString()
                }
            }
            return if (n == j) filename else filename.substring(0, j)
        }

        private fun isClassnamePart(c: Char): Boolean {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return true
            when (c) {
                '.', '$', '_' -> return true
                else -> return false
            }
        }
    }
}
