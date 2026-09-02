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
package net.blueva.luak.script

import net.blueva.luak.Globals
import net.blueva.luak.lib.jvm.JvmPlatform
import net.blueva.luak.luajc.LuaJC
import java.io.*
import javax.script.ScriptContext
import javax.script.SimpleScriptContext

/**
 * Context for LuaScriptEngine execution which maintains its own Globals,
 * and manages the input and output redirection.
 */
class LuaContext @JvmOverloads constructor(
    createDebugGlobals: Boolean = flag("debug"),
    useLuaJCCompiler: Boolean = flag("luajc")
) : SimpleScriptContext(), ScriptContext {
    /** Globals for this context instance.  */
    val globals: Globals

    /** The initial value of globals.STDIN  */
    private val stdin: InputStream?

    /** The initial value of globals.STDOUT  */
    private val stdout: PrintStream?

    /** The initial value of globals.STDERR  */
    private val stderr: PrintStream?

    /** Construct a LuaContext with its own globals, which
     * which optionally are debug globals, and optionally use the
     * luajc direct lua to java bytecode compiler.
     * 
     * 
     * If createDebugGlobals is set, the globals
     * created will be a debug globals that includes the debug
     * library.  This may provide better stack traces, but may
     * have negative impact on performance.
     * @param createDebugGlobals true to create debug globals,
     * false for standard globals.
     * @param useLuaJCCompiler true to use the luajc compiler,
     * reqwuires bcel to be on the class path.
     */
    /** Construct a LuaContext with its own globals which may
     * be debug globals depending on the value of the system
     * property 'luak.debug'
     * 
     * 
     * If the system property 'luak.debug' is set, the globals
     * created will be a debug globals that includes the debug
     * library.  This may provide better stack traces, but may
     * have negative impact on performance.
     */
    init {
        globals = if (createDebugGlobals) JvmPlatform.debugGlobals() else JvmPlatform.standardGlobals()
        if (useLuaJCCompiler) LuaJC.install(globals)
        stdin = globals.STDIN
        stdout = globals.STDOUT
        stderr = globals.STDERR
    }

    override fun setErrorWriter(writer: Writer?) {
        globals.STDERR = if (writer != null) PrintStream(WriterOutputStream(writer)) else stderr
    }

    override fun setReader(reader: Reader?) {
        globals.STDIN = if (reader != null) ReaderInputStream(reader) else stdin
    }

    override fun setWriter(writer: Writer?) {
        globals.STDOUT = if (writer != null) PrintStream(WriterOutputStream(writer), true) else stdout
    }

    internal class WriterOutputStream(val w: Writer) : OutputStream() {
        @Throws(IOException::class)
        override fun write(b: Int) {
            w.write(String(byteArrayOf(b.toByte())))
        }

        @Throws(IOException::class)
        override fun write(b: ByteArray, o: Int, l: Int) {
            w.write(String(b, o, l))
        }

        @Throws(IOException::class)
        override fun write(b: ByteArray) {
            w.write(String(b))
        }

        @Throws(IOException::class)
        override fun close() {
            w.close()
        }

        @Throws(IOException::class)
        override fun flush() {
            w.flush()
        }
    }

    internal class ReaderInputStream(val r: Reader) : InputStream() {
        @Throws(IOException::class)
        override fun read(): Int {
            return r.read()
        }
    }

    companion object {
        /**
         * Reads the `luak.<name>` system property, falling back to the
         * inherited `org.luaj.<name>` spelling so existing setups keep working.
         */
        private fun flag(name: String): Boolean =
            "true" == (System.getProperty("luak.$name") ?: System.getProperty("org.luaj.$name"))
    }
}
