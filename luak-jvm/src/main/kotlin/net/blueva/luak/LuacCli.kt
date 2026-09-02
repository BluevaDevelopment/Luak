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

import net.blueva.luak.lib.jvm.asLuaReader
import net.blueva.luak.compiler.DumpState
import net.blueva.luak.lib.jvm.JvmPlatform
import java.io.*

/**
 * Compiler for lua files to lua bytecode.
 */
class LuacCli private constructor(args: Array<String>) {
    private var list = false
    private var output = "luac.out"
    private var parseonly = false
    private var stripdebug = false
    private var littleendian = false
    private var numberformat = DumpState.NUMBER_FORMAT_DEFAULT
    private var versioninfo = false
    private var processing = true
    private var encoding: String? = null

    init {
        // process args

        try {
            // get stateful args
            var i = 0
            while (i < args.size) {
                if (!processing || !args[i].startsWith("-")) {
                    // input file - defer to next stage
                } else if (args[i].length <= 1) {
                    // input file - defer to next stage
                } else {
                    when (args[i].get(1)) {
                        'l' -> list = true
                        'o' -> {
                            if (++i >= args.size) usageExit()
                            output = args[i]
                        }

                        'p' -> parseonly = true
                        's' -> stripdebug = true
                        'e' -> littleendian = true
                        'i' -> {
                            if (args[i].length <= 2) usageExit()
                            numberformat = args[i].substring(2).toInt()
                        }

                        'v' -> versioninfo = true
                        'c' -> {
                            if (++i >= args.size) usageExit()
                            encoding = args[i]
                        }

                        '-' -> {
                            if (args[i].length > 2) usageExit()
                            processing = false
                        }

                        else -> usageExit()
                    }
                }
                i++
            }


            // echo version
            if (versioninfo) println(version)

            // open output file
            val fos: OutputStream = FileOutputStream(output)


            // process input files
            try {
                val globals = JvmPlatform.standardGlobals()
                processing = true
                var i = 0
                while (i < args.size) {
                    if (!processing || !args[i].startsWith("-")) {
                        val chunkname = args[i].substring(0, args[i].length - 4)
                        processScript(globals, FileInputStream(args[i]), chunkname, fos)
                    } else if (args[i].length <= 1) {
                        processScript(globals, System.`in`, "=stdin", fos)
                    } else {
                        when (args[i].get(1)) {
                            'o', 'c' -> ++i
                            '-' -> processing = false
                        }
                    }
                    i++
                }
            } finally {
                fos.close()
            }
        } catch (ioe: IOException) {
            System.err.println(ioe.toString())
            System.exit(-2)
        }
    }

    @Throws(IOException::class)
    private fun processScript(globals: Globals, script: InputStream, chunkname: String?, out: OutputStream?) {
        var script = script
        try {
            // create the chunk
            script = BufferedInputStream(script)
            val chunk = if (encoding != null) globals.compilePrototype(
                InputStreamReader(script, encoding).asLuaReader(),
                chunkname
            ) else globals.compilePrototype(script, chunkname)

            // list the chunk
            if (list) Print.printCode(chunk!!)

            // write out the chunk
            if (!parseonly) {
                DumpState.dump(chunk!!, out, stripdebug, numberformat, littleendian)
            }
        } catch (e: Exception) {
            e.printStackTrace(System.err)
        } finally {
            script.close()
        }
    }

    companion object {
        private val version = Lua.LUAK_VERSION + "Copyright (C) 2009 luaj.org"

        private val usage = "usage: java -cp luak-jvm.jar luac [options] [filenames].\n" +
                "Available options are:\n" +
                "  -        process stdin\n" +
                "  -l       list\n" +
                "  -o name  output to file 'name' (default is \"luac.out\")\n" +
                "  -p       parse only\n" +
                "  -s       strip debug information\n" +
                "  -e       little endian format for numbers\n" +
                "  -i<n>    number format 'n', (n=0,1 or 4, default=" + DumpState.NUMBER_FORMAT_DEFAULT + ")\n" +
                "  -v       show version information\n" +
                "  -c enc  	use the supplied encoding 'enc' for input files\n" +
                "  --       stop handling options\n"

        private fun usageExit() {
            println(usage)
            System.exit(-1)
        }

        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            LuacCli(args)
        }
    }
}
