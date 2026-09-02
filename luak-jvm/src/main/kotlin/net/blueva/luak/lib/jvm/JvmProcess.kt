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

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Analog of Process that pipes input and output to client-specified streams.
 */
class JvmProcess private constructor(
    val process: Process,
    stdin: InputStream?,
    stdout: OutputStream?,
    stderr: OutputStream?
) {
    val input: Thread?
    val output: Thread?
    val error: Thread?

    /** Construct a process around a command, with specified streams to redirect input and output to.
     * 
     * @param cmd The command to execute, including arguments, if any
     * @param stdin Optional InputStream to read from as process input, or null if input is not needed.
     * @param stdout Optional OutputStream to copy process output to, or null if output is ignored.
     * @param stderr Optinoal OutputStream to copy process stderr output to, or null if output is ignored.
     * @throws IOException If the system process could not be created.
     * @see Process
     */
    constructor(
        cmd: Array<out String?>?,
        stdin: InputStream?,
        stdout: OutputStream?,
        stderr: OutputStream?
    ) : this(Runtime.getRuntime().exec(cmd), stdin, stdout, stderr)

    /** Construct a process around a command, with specified streams to redirect input and output to.
     * 
     * @param cmd The command to execute, including arguments, if any
     * @param stdin Optional InputStream to read from as process input, or null if input is not needed.
     * @param stdout Optional OutputStream to copy process output to, or null if output is ignored.
     * @param stderr Optinoal OutputStream to copy process stderr output to, or null if output is ignored.
     * @throws IOException If the system process could not be created.
     * @see Process
     */
    constructor(
        cmd: String?,
        stdin: InputStream?,
        stdout: OutputStream?,
        stderr: OutputStream?
    ) : this(shell(cmd), stdin, stdout, stderr)

    private companion object {
        /**
         * Runs [cmd] the way a command line would.
         *
         * The text is a command as a shell reads it - pipes, redirections and
         * all - rather than a program and its arguments, which is what Lua
         * hands over and what a reference build passes to `system`.
         */
        fun shell(cmd: String?): Process {
            val command: String = cmd ?: ""
            val windows: Boolean = System.getProperty("os.name")
                ?.lowercase()?.contains("windows") == true
            val parts: Array<String> = if (windows) {
                arrayOf("cmd", "/c", command)
            } else {
                arrayOf("/bin/sh", "-c", command)
            }
            return ProcessBuilder(*parts).start()
        }
    }

    init {
        input =
            if (stdin == null) null else copyBytes(stdin, process.getOutputStream(), null, process.getOutputStream())
        output =
            if (stdout == null) null else copyBytes(process.getInputStream(), stdout, process.getInputStream(), null)
        error =
            if (stderr == null) null else copyBytes(process.getErrorStream(), stderr, process.getErrorStream(), null)
    }

    /** Get the exit value of the process.  */
    fun exitValue(): Int {
        return process.exitValue()
    }

    /** Wait for the process to complete, and all pending output to finish.
     * @return The exit status.
     * @throws InterruptedException
     */
    @Throws(InterruptedException::class)
    fun waitFor(): Int {
        val r = process.waitFor()
        if (input != null) input.join()
        if (output != null) output.join()
        if (error != null) error.join()
        process.destroy()
        return r
    }

    /** Create a thread to copy bytes from input to output.  */
    private fun copyBytes(
        input: InputStream,
        output: OutputStream, ownedInput: InputStream?,
        ownedOutput: OutputStream?
    ): Thread {
        val t: Thread = (CopyThread(output, ownedOutput, ownedInput, input))
        t.start()
        return t
    }

    private class CopyThread(
        private val output: OutputStream, private val ownedOutput: OutputStream?,
        private val ownedInput: InputStream?, private val input: InputStream
    ) : Thread() {
        override fun run() {
            try {
                val buf = ByteArray(1024)
                var r: Int
                try {
                    while ((input.read(buf).also { r = it }) >= 0) {
                        output.write(buf, 0, r)
                    }
                } finally {
                    if (ownedInput != null) ownedInput.close()
                    if (ownedOutput != null) ownedOutput.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
