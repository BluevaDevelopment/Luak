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

import junit.framework.TestCase
import net.blueva.luak.lib.ResourceFinder
import net.blueva.luak.lib.jvm.JvmPlatform.debugGlobals
import net.blueva.luak.lib.jvm.JvmProcess
import net.blueva.luak.luajc.LuaJC.Companion.install
import java.io.*
import java.net.MalformedURLException
import java.net.URL

abstract
class ScriptDrivenTest protected constructor(private val platform: PlatformType, private val subdir: String?) :
    TestCase(), ResourceFinder {
    enum class PlatformType {
        JVM, LUAJIT,
    }

    protected var globals: Globals? = null

    init {
        initGlobals()
    }

    private fun initGlobals() {
        when (platform) {
            PlatformType.JVM, PlatformType.LUAJIT -> globals = debugGlobals()
            else -> globals = debugGlobals()
        }
    }


    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        initGlobals()
        globals!!.finder = this
    }

    // ResourceFinder implementation.
    override fun findResource(filename: String?): InputStream? {
        var `is` = findInPlainFile(filename)
        if (`is` != null) return `is`
        `is` = findInPlainFileAsResource("", filename)
        if (`is` != null) return `is`
        `is` = findInPlainFileAsResource("/", filename)
        if (`is` != null) return `is`
        `is` = findInZipFileAsPlainFile(filename)
        if (`is` != null) return `is`
        `is` = findInZipFileAsResource("", filename)
        if (`is` != null) return `is`
        `is` = findInZipFileAsResource("/", filename)
        return `is`
    }

    private fun findInPlainFileAsResource(prefix: String?, filename: String?): InputStream? {
        return javaClass.getResourceAsStream(prefix + zipdir + subdir + filename)
    }

    private fun findInPlainFile(filename: String?): InputStream? {
        try {
            val f = File(zipdir + subdir + filename)
            if (f.exists()) return FileInputStream(f)
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }
        return null
    }

    private fun findInZipFileAsPlainFile(filename: String?): InputStream? {
        val zip: URL?
        val file: File = File(zipdir + zipfile)
        try {
            if (file.exists()) {
                zip = file.toURI().toURL()
                val path = "jar:" + zip.toExternalForm() + "!/" + subdir + filename
                val url = URL(path)
                return url.openStream()
            }
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        } catch (e: FileNotFoundException) {
            // Ignore and return null.
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }
        return null
    }


    private fun findInZipFileAsResource(prefix: String?, filename: String?): InputStream? {
        val zip: URL? = javaClass.getResource("/" + zipdir + zipfile)
        if (zip != null) try {
            val path = "jar:" + zip.toExternalForm() + "!/" + subdir + filename
            val url = URL(path)
            return url.openStream()
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }
        return null
    }

    // */
    protected fun runTest(testName: String?) {
        try {
            // override print()
            val output = ByteArrayOutputStream()
            val oldps = globals!!.STDOUT
            val ps = PrintStream(output)
            globals!!.STDOUT = ps


            // run the script
            try {
                val chunk = loadScript(testName, globals!!)
                chunk.call(LuaValue.valueOf(platform.toString()))

                ps.flush()
                var actualOutput = String(output.toByteArray())
                var expectedOutput = getExpectedOutput(testName)
                actualOutput = actualOutput.replace("\r\n".toRegex(), "\n")
                expectedOutput = expectedOutput.replace("\r\n".toRegex(), "\n")

                TestCase.assertEquals(expectedOutput, actualOutput)
            } finally {
                globals!!.STDOUT = oldps
                ps.close()
            }
        } catch (ioe: IOException) {
            throw RuntimeException(ioe.toString())
        } catch (ie: InterruptedException) {
            throw RuntimeException(ie.toString())
        }
    }

    @Throws(IOException::class)
    protected fun loadScript(name: String?, globals: Globals): LuaValue {
        val script = this.findResource(name + ".lua")
        if (script == null) fail("Could not load script for test case: " + name)
        try {
            when (this.platform) {
                PlatformType.LUAJIT -> if (nocompile) {
                    val c = Class.forName(name).newInstance() as LuaValue
                    return c
                } else {
                    install(globals)
                    return globals.load(script!!, name, "bt", globals)!!
                }

                else -> return globals.load(script!!, "@" + name + ".lua", "bt", globals)!!
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw IOException(e.toString())
        } finally {
            script!!.close()
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun getExpectedOutput(name: String?): String {
        val output = this.findResource(name + ".out")
        if (output != null) try {
            return readString(output)
        } finally {
            output.close()
        }
        val expectedOutput = executeLuaProcess(name)
        if (expectedOutput == null) throw IOException("Failed to get comparison output or run process for " + name)
        return expectedOutput
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun executeLuaProcess(name: String?): String {
        val script = findResource(name + ".lua")
        if (script == null) throw IOException("Failed to find source file " + script)
        try {
            var luaCommand = System.getProperty("LUA_COMMAND")
            if (luaCommand == null) luaCommand = "lua"
            val args = arrayOf<String?>(luaCommand, "-", platform.toString())
            return collectProcessOutput(args, script)
        } finally {
            script.close()
        }
    }

    @Throws(IOException::class)
    private fun readString(`is`: InputStream): String {
        val baos = ByteArrayOutputStream()
        copy(`is`, baos)
        return String(baos.toByteArray())
    }

    companion object {
        val nocompile: Boolean = "true" == System.getProperty("nocompile")

        const val zipdir: String = "test/lua/"
        const val zipfile: String = "luaj3.0-tests.zip"

        @Throws(IOException::class, InterruptedException::class)
        fun collectProcessOutput(cmd: Array<String?>?, input: InputStream?): String {
            val r = Runtime.getRuntime()
            val baos = ByteArrayOutputStream()
            JvmProcess(cmd, input, baos, System.err).waitFor()
            return String(baos.toByteArray())
        }

        @Throws(IOException::class)
        private fun copy(`is`: InputStream, os: OutputStream) {
            val buf = ByteArray(1024)
            var r: Int
            while ((`is`.read(buf).also { r = it }) >= 0) {
                os.write(buf, 0, r)
            }
        }
    }
}
