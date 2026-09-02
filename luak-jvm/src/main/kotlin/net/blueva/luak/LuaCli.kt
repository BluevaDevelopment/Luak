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

import net.blueva.luak.lib.OneArgFunction
import net.blueva.luak.lib.jvm.asLuaReader
import net.blueva.luak.lib.jvm.JvmPlatform
import net.blueva.luak.luajc.LuaJC
import java.io.*
import java.util.*

/**
 * lua command for use in JVM environments.
 */
object LuaCli {
    private val version = Lua.LUAK_VERSION + " Copyright (c) 2012 Luaj.org.org"

    private val usage = "usage: java -cp luak-jvm.jar lua [options] [script [args]].\n" +
            "Available options are:\n" +
            "  -e stat  execute string 'stat'\n" +
            "  -l name  require library 'name'\n" +
            "  -i       enter interactive mode after executing 'script'\n" +
            "  -v       show version information\n" +
            "  -b      	use luajc bytecode-to-bytecode compiler (requires bcel on class path)\n" +
            "  -n      	nodebug - do not load debug library by default\n" +
            "  -p      	print the prototype\n" +
            "  -c enc  	use the supplied encoding 'enc' for input files\n" +
            "  --       stop handling options\n" +
            "  -        execute stdin and stop handling options"

    private fun usageExit() {
        println(usage)
        System.exit(-1)
    }

    private var globals: Globals? = null
    private var print = false
    private var encoding: String? = null

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        // process args

        var interactive = (args.size == 0)
        var versioninfo = false
        var processing = true
        var nodebug = false
        var luajc = false
        var libs: Vector<String>? = null
        try {
            // stateful argument processing
            run {
                var i = 0
                while (i < args.size) {
                    if (!processing || !args[i].startsWith("-")) {
                        // input file - defer to last stage
                        break
                    } else if (args[i].length <= 1) {
                        // input file - defer to last stage
                        break
                    } else {
                        when (args[i].get(1)) {
                            'e' -> if (++i >= args.size) LuaCli.usageExit()
                            'b' -> luajc = true
                            'l' -> {
                                if (++i >= args.size) LuaCli.usageExit()
                                libs = libs ?: Vector<String>()
                                libs.addElement(args[i])
                            }

                            'i' -> interactive = true
                            'v' -> versioninfo = true
                            'n' -> nodebug = true
                            'p' -> LuaCli.print = true
                            'c' -> {
                                if (++i >= args.size) LuaCli.usageExit()
                                LuaCli.encoding = args[i]
                            }

                            '-' -> {
                                if (args[i].length > 2) LuaCli.usageExit()
                                processing = false
                            }

                            else -> LuaCli.usageExit()
                        }
                    }
                    i++
                }
            }

            // echo version
            if (versioninfo) println(version)


            // new lua state
            globals = if (nodebug) JvmPlatform.standardGlobals() else JvmPlatform.debugGlobals()
            if (luajc) LuaJC.install(globals!!)
            run {
                var i = 0
                val n = if (libs != null) libs.size else 0
                while (i < n) {
                    LuaCli.loadLibrary(libs!!.elementAt(i) as String?)
                    i++
                }
            }


            // input script processing
            processing = true
            var i = 0
            while (i < args.size) {
                if (!processing || !args[i].startsWith("-")) {
                    LuaCli.processScript(FileInputStream(args[i]), "@" + args[i], args, i)
                    break
                } else if ("-" == args[i]) {
                    LuaCli.processScript(System.`in`, "=stdin", args, i)
                    break
                } else {
                    when (args[i].get(1)) {
                        'l', 'c' -> ++i
                        'e' -> {
                            ++i
                            LuaCli.processScript(
                                ByteArrayInputStream(args[i].toByteArray()),
                                "=(command line)",
                                args,
                                i,
                            )
                        }

                        '-' -> processing = false
                    }
                }
                i++
            }

            if (interactive) interactiveMode()
        } catch (ioe: IOException) {
            System.err.println(ioe.toString())
            System.exit(-2)
        }
    }

    @Throws(IOException::class)
    private fun loadLibrary(libname: String?) {
        val slibname: LuaValue = LuaValue.valueOf(libname)
        try {
            // load via plain require
            globals!!.get("require")!!.call(slibname)
        } catch (e: Exception) {
            try {
                // load as java class
                val v = Class.forName(libname).newInstance() as LuaValue
                v.call(slibname, globals)
            } catch (f: Exception) {
                throw IOException("loadLibrary(" + libname + ") failed: " + e + "," + f)
            }
        }
    }

    @Throws(IOException::class)
    private fun processScript(script: InputStream, chunkname: String?, args: Array<String>?, firstarg: Int) {
        var script = script
        try {
            var c: LuaValue
            try {
                script = BufferedInputStream(script)
                c = (if (encoding != null) globals!!.load(
                    InputStreamReader(script, encoding).asLuaReader(),
                    chunkname
                ) else globals!!.load(script, chunkname, "bt", globals))!!
            } finally {
                script.close()
            }
            if (print && c.isclosure()) Print.print(c.checkclosure()!!.p)
            val scriptargs = setGlobalArg(chunkname?.removePrefix("@"), args, firstarg, globals!!)
            installMessageHandler(globals!!)
            c.invoke(scriptargs!!)
        } catch (e: LuaError) {
            // The shape the standalone interpreter uses: the message, then the
            // traceback the handler captured while the stack was still up.
            // The message already carries the handler's traceback, if one ran.
            System.err.println("luak: " + e.message)
        } catch (e: Exception) {
            e.printStackTrace(System.err)
        }
    }

    /**
     * Installs the standalone interpreter's message handler.
     *
     * `lua.c` runs the script under a handler that appends a traceback to the
     * error message, which is the only point at which the stack is still there
     * to walk. Without one an uncaught error can only report where it was
     * raised, not how the program got there.
     */
    private fun installMessageHandler(globals: Globals) {
        val debuglib = globals.debuglib ?: return
        globals.running.errorfunc = object : OneArgFunction() {
            override fun call(arg: LuaValue?): LuaValue {
                val message: String = arg?.tojstring() ?: "?"
                return LuaValue.valueOf(message + "\n" + debuglib.traceback(1))
            }
        }
    }

    private fun setGlobalArg(chunkname: String?, args: Array<String>?, i: Int, globals: LuaValue): Varargs? {
        if (args == null) return LuaValue.NONE
        val arg = LuaValue.tableOf()
        for (j in args.indices) arg.set(j - i, LuaValue.valueOf(args[j]))
        arg.set(0, LuaValue.valueOf(chunkname))
        arg.set(-1, LuaValue.valueOf("luak"))
        globals.set("arg", arg)
        return arg.unpack()
    }

    @Throws(IOException::class)
    private fun interactiveMode() {
        val reader = BufferedReader(InputStreamReader(System.`in`))
        while (true) {
            print("> ")
            System.out.flush()
            val line = reader.readLine()
            if (line == null) return
            processScript(ByteArrayInputStream(line.toByteArray()), "=stdin", null, 0)
        }
    }
}
