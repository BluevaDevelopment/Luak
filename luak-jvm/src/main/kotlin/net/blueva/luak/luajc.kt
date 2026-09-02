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

import net.blueva.luak.lib.jvm.JvmPlatform
import net.blueva.luak.luajc.LuaJC
import java.io.*
import java.util.*

/**
 * Compiler for lua files to compile lua sources or lua binaries into java classes.
 */
class LuaJcMain private constructor(args: Array<String>) {
    private var srcdir: String? = "."
    private var destdir: String? = "."
    private var genmain = false
    private var recurse = false
    private var verbose = false
    private var loadclasses = false
    private var encoding: String? = null
    private var pkgprefix: String? = null
    private val files: MutableList<InputFile> = ArrayList<InputFile>()
    private val globals: Globals

    init {
        // process args

        val seeds: MutableList<String?> = ArrayList<String?>()


        // get stateful args
        run {
            var i = 0
            while (i < args.size) {
                if (!args[i]!!.startsWith("-")) {
                    seeds.add(args[i])
                } else {
                    when (args[i]!!.get(1)) {
                        's' -> {
                            if (++i >= args.size) usageExit()
                            srcdir = args[i]
                        }

                        'd' -> {
                            if (++i >= args.size) usageExit()
                            destdir = args[i]
                        }

                        'l' -> loadclasses = true
                        'p' -> {
                            if (++i >= args.size) usageExit()
                            pkgprefix = args[i]
                        }

                        'm' -> genmain = true
                        'r' -> recurse = true
                        'c' -> {
                            if (++i >= args.size) usageExit()
                            encoding = args[i]
                        }

                        'v' -> verbose = true
                        else -> usageExit()
                    }
                }
                i++
            }
        }


        // echo version
        if (verbose) {
            println(version)
            println("srcdir: " + srcdir)
            println("destdir: " + destdir)
            println("files: " + seeds)
            println("recurse: " + recurse)
        }

        // need at least one seed
        if (seeds.size <= 0) {
            System.err.println(usage)
            System.exit(-1)
        }

        // collect up files to process
        for (i in seeds.indices) collectFiles(srcdir + "/" + seeds.get(i))


        // check for at least one file
        if (files.size <= 0) {
            System.err.println("no files found in " + seeds)
            System.exit(-1)
        }


        // process input files
        globals = JvmPlatform.standardGlobals()
        var i = 0
        val n = files.size
        while (i < n) {
            processFile((files.get(i) as InputFile?)!!)
            i++
        }
    }

    private fun collectFiles(path: String) {
        val f = File(path)
        if (f.isDirectory() && recurse) scandir(f, pkgprefix)
        else if (f.isFile()) {
            val dir = f.getAbsoluteFile().getParentFile()
            if (dir != null) scanfile(dir, f, pkgprefix)
        }
    }

    private fun scandir(dir: File, javapackage: String?) {
        val f = dir.listFiles()
        for (i in f!!.indices) scanfile(dir, f[i]!!, javapackage)
    }

    private fun scanfile(dir: File?, f: File, javapackage: String?) {
        if (f.exists()) {
            if (f.isDirectory() && recurse) scandir(
                f,
                (if (javapackage != null) javapackage + "." + f.getName() else f.getName())
            )
            else if (f.isFile() && f.getName().endsWith(".lua")) files.add(this.InputFile(dir, f, javapackage))
        }
    }

    private class LocalClassLoader(private val t: Hashtable<*, *>) : ClassLoader() {
        @Throws(ClassNotFoundException::class)
        public override fun findClass(classname: String?): Class<*>? {
            val bytes = t.get(classname) as ByteArray?
            if (bytes != null) return defineClass(classname, bytes, 0, bytes.size)
            return super.findClass(classname)
        }
    }

    internal inner class InputFile(dir: File?, var infile: File, javapackage: String?) {
        var luachunkname: String
        var srcfilename: String
        var outdir: File
        var javapackage: String?

        init {
            val subdir = if (javapackage != null) javapackage.replace('.', '/') else null
            val outdirpath = (if (subdir != null) destdir + "/" + subdir else destdir)!!
            this.javapackage = javapackage
            this.srcfilename = (if (subdir != null) subdir + "/" else "") + infile.getName()
            this.luachunkname = (if (subdir != null) subdir + "/" else "") + infile.getName()
                .substring(0, infile.getName().lastIndexOf('.'))
            this.infile = infile
            this.outdir = File(outdirpath)
        }
    }

    private fun processFile(inf: InputFile) {
        inf.outdir.mkdirs()
        try {
            if (verbose) println("chunk=" + inf.luachunkname + " srcfile=" + inf.srcfilename)

            // create the chunk
            val fis = FileInputStream(inf.infile)
            val t = if (encoding != null) LuaJC.instance.compileAll(
                InputStreamReader(fis, encoding),
                inf.luachunkname,
                inf.srcfilename,
                globals,
                genmain
            ) else LuaJC.instance.compileAll(fis, inf.luachunkname, inf.srcfilename, globals, genmain)
            fis.close()


            // write out the chunk
            val e: Enumeration<*> = t.keys()
            while (e.hasMoreElements()) {
                val key = e.nextElement() as String
                val bytes = t.get(key) as ByteArray
                if (key.indexOf('/') >= 0) {
                    val d = (if (destdir != null) destdir + "/" else "") + key.substring(0, key.lastIndexOf('/'))
                    File(d).mkdirs()
                }
                val destpath = (if (destdir != null) destdir + "/" else "") + key + ".class"
                if (verbose) println("  " + destpath + " (" + bytes.size + " bytes)")
                val fos = FileOutputStream(destpath)
                fos.write(bytes)
                fos.close()
            }

            // try to load the files
            if (loadclasses) {
                val loader: ClassLoader = LocalClassLoader(t)
                val e: Enumeration<*> = t.keys()
                while (e.hasMoreElements()) {
                    val classname = e.nextElement() as String?
                    try {
                        val c = loader.loadClass(classname)
                        val o: Any = c.newInstance()
                        if (verbose) println("    loaded " + classname + " as " + o)
                    } catch (ex: Exception) {
                        System.out.flush()
                        System.err.println("    failed to load " + classname + ": " + ex)
                        System.err.flush()
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("    failed to load " + inf.srcfilename + ": " + e)
            e.printStackTrace(System.err)
            System.err.flush()
        }
    }

    companion object {
        private val version = Lua.LUAK_VERSION + " Copyright (C) 2012 luaj.org"

        private val usage = "usage: java -cp luak-jvm.jar,bcel-5.2.jar luajc [options] fileordir [, fileordir ...]\n" +
                "Available options are:\n" +
                "  -        process stdin\n" +
                "  -s src	source directory\n" +
                "  -d dir	destination directory\n" +
                "  -p pkg	package prefix to apply to all classes\n" +
                "  -m		generate main(String[]) function for JVM\n" +
                "  -r		recursively compile all\n" +
                "  -l		load classes to verify generated bytecode\n" +
                "  -c enc  	use the supplied encoding 'enc' for input files\n" +
                "  -v   	verbose\n"

        private fun usageExit() {
            println(usage)
            System.exit(-1)
        }

        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            LuaJcMain(args)
        }
    }
}
