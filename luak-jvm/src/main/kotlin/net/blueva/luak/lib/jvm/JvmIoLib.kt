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
package net.blueva.luak.lib.jvm

import net.blueva.luak.LuaError
import net.blueva.luak.LuaString
import net.blueva.luak.lib.IoLib
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Subclass of [IoLib] which adds the one part of the lua standard `io`
 * library that has no portable implementation: `io.popen`.
 *
 * Files, standard streams, `io.tmpfile`, and every read format are handled by
 * the shared [IoLib] in `luak-core`, which works the same on every
 * Kotlin Multiplatform target; only spawning a process needs the JVM.
 *
 * Typically this library is included as part of a call to
 * [JvmPlatform.standardGlobals]:
 * ```kotlin
 * val globals = JvmPlatform.standardGlobals()
 * globals.get("io").get("write").call(LuaValue.valueOf("hello, world\n"))
 * ```
 *
 * @see IoLib
 *
 * @see JvmPlatform
 *
 * @see [Lua 5.2 I/O Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.8)
 */
class JvmIoLib : IoLib() {
    @Throws(IOException::class)
    override fun openProgram(prog: String?, mode: String?): File {
        val process = Runtime.getRuntime().exec(prog)
        return if ("w" == mode) ProcessFile(output = process.outputStream)
        else ProcessFile(input = process.inputStream)
    }

    /**
     * One end of a pipe to a child process. Pipes are not seekable, so the
     * positional operations report the same "not implemented" error the C
     * library raises for them.
     */
    private inner class ProcessFile(
        input: InputStream? = null,
        private val output: OutputStream? = null,
    ) : File() {
        private val input: InputStream? =
            input?.let { if (it.markSupported()) it else BufferedInputStream(it) }
        private var closed = false

        override fun tojstring(): String = "file (" + (if (closed) "closed" else "process") + ")"

        override fun isstdfile(): Boolean = false
        override fun isclosed(): Boolean = closed

        @Throws(IOException::class)
        override fun close() {
            closed = true
            input?.close()
            output?.close()
        }

        @Throws(IOException::class)
        override fun flush() {
            output?.flush()
        }

        @Throws(IOException::class)
        override fun write(string: LuaString?) {
            val s = string ?: return
            val stream = output ?: return notimplemented()
            stream.write(s.m_bytes, s.m_offset, s.m_length)
        }

        @Throws(IOException::class)
        override fun seek(option: String?, bytecount: Int): Int {
            notimplemented()
            return 0
        }

        override fun setvbuf(mode: String?, size: Int) = Unit

        @Throws(IOException::class)
        override fun remaining(): Int = -1

        @Throws(IOException::class, EOFException::class)
        override fun peek(): Int {
            val stream = input ?: return notimplemented().let { 0 }
            stream.mark(1)
            val value = stream.read()
            stream.reset()
            return value
        }

        @Throws(IOException::class, EOFException::class)
        override fun read(): Int {
            val stream = input ?: return notimplemented().let { 0 }
            return stream.read()
        }

        @Throws(IOException::class)
        override fun read(bytes: ByteArray?, offset: Int, length: Int): Int {
            val target = bytes ?: return -1
            val stream = input ?: return notimplemented().let { 0 }
            return stream.read(target, offset, length)
        }
    }

    companion object {
        private fun notimplemented() {
            throw LuaError("not implemented")
        }
    }
}
