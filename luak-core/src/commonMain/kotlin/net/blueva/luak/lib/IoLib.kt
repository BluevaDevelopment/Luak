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

import net.blueva.luak.Globals
import net.blueva.luak.LuaString
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs
import net.blueva.luak.io.ByteArrayOutputStream
import net.blueva.luak.io.EOFException
import net.blueva.luak.io.IOException
import net.blueva.luak.io.InputStream
import net.blueva.luak.io.PlatformFileHandle
import net.blueva.luak.io.PlatformFileMode
import net.blueva.luak.io.platformDeleteFile
import net.blueva.luak.io.platformOpenFile
import net.blueva.luak.io.platformStandardInput
import net.blueva.luak.io.platformTempFilePath

/**
 * Subclass of [LibFunction] which implements the lua standard `io` library.
 *
 *
 * The whole library - the `file` userdata, the read formats, `file:lines()`,
 * `io.open`, `io.lines`, `io.tmpfile` - lives in `commonMain` on top of the
 * small set of host primitives in `net.blueva.luak.io`, so it behaves the same
 * on JVM, JavaScript, Wasm, and Native instead of needing a platform-specific
 * subclass. The one operation that has no portable form is `io.popen`, which
 * needs to spawn a process: [openProgram] therefore fails with an [IOException]
 * by default and is overridden by [net.blueva.luak.lib.jvm.JvmIoLib].
 *
 *
 * On a host that grants no filesystem access at all (a browser, or a WASI
 * module with no pre-opened directory), opening a file fails the way Lua
 * expects - `io.open` returns `nil` plus a message rather than throwing.
 *
 *
 * Typically this library is included as part of a call to
 * [net.blueva.luak.lib.LuaPlatform.standardGlobals]:
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * globals.get("io").get("write").call(LuaValue.valueOf("hello, world\n"))
 * ```
 *
 *
 * To instantiate and use it directly, link it into your globals table via
 * [Globals.load] using code such as:
 * ```kotlin
 * val globals = Globals()
 * globals.load(BaseLib())
 * globals.load(PackageLib())
 * globals.load(IoLib())
 * ```
 *
 *
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 * @see LibFunction
 *
 * @see net.blueva.luak.lib.LuaPlatform
 *
 * @see net.blueva.luak.lib.jvm.JvmIoLib
 *
 * @see [Lua 5.2 I/O Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.8)
 */
open class IoLib : TwoArgFunction() {
    abstract
    inner class File : LuaValue() {
        @kotlin.Throws(IOException::class)
        abstract fun write(string: LuaString?)

        @kotlin.Throws(IOException::class)
        abstract fun flush()
        abstract fun isstdfile(): Boolean

        @kotlin.Throws(IOException::class)
        abstract fun close()
        abstract fun isclosed(): Boolean

        // returns new position
        @kotlin.Throws(IOException::class)
        abstract fun seek(option: String?, bytecount: Int): Int
        abstract fun setvbuf(mode: String?, size: Int)

        // get length remaining to read
        @kotlin.Throws(IOException::class)
        abstract fun remaining(): Int

        // peek ahead one character
        @kotlin.Throws(IOException::class, EOFException::class)
        abstract fun peek(): Int

        // return char if read, -1 if eof, throw IOException on other exception
        @kotlin.Throws(IOException::class, EOFException::class)
        abstract fun read(): Int

        // return number of bytes read if positive, false if eof, throw IOException on other exception
        @kotlin.Throws(IOException::class)
        abstract fun read(bytes: ByteArray?, offset: Int, length: Int): Int

        @kotlin.Throws(IOException::class)
        fun eof(): Boolean {
            try {
                return peek() < 0
            } catch (e: EOFException) {
                return true
            }
        }

        // delegate method access to file methods table
        override fun get(key: LuaValue): LuaValue {
            return filemethods!!.get(key)
        }

        /**
         * The table of file methods, which doubles as the handle's metatable.
         *
         * A script reads the type's name back from `getmetatable(f).__name`,
         * and that is where Lua keeps it.
         */
        override fun getmetatable(): LuaValue? {
            return filemethods
        }

        // essentially a userdata instance
        override fun type(): Int {
            return LuaValue.TUSERDATA
        }

        override fun typename(): String? {
            return "userdata"
        }

        /**
         * How a file handle prints, which says whether it is still open.
         *
         * A closed one has no identity worth showing, so Lua prints the word
         * instead of an address.
         */
        override fun tojstring(): String {
            return if (isclosed()) "file (closed)" else "file (0x" + hashCode().toString(16) + ")"
        }

        /**
         * The handle's own rendering, so `tostring` uses it.
         *
         * Without this the generic conversion would fall back to the type's
         * `__name` and print "FILE*: file (...)".
         */
        override fun tostring(): LuaValue {
            return valueOf(tojstring())
        }

        fun finalize() {
            if (!isclosed()) {
                try {
                    close()
                } catch (ignore: IOException) {
                }
            }
        }
    }

    /**
     * Wrap the standard input.
     * @return File
     * @throws IOException
     */
    @kotlin.Throws(IOException::class)
    protected open fun wrapStdin(): File? = StandardInputFile()

    /**
     * Wrap the standard output.
     * @return File
     * @throws IOException
     */
    @kotlin.Throws(IOException::class)
    protected open fun wrapStdout(): File? = StandardOutputFile(FTYPE_STDOUT)

    /**
     * Wrap the standard error output.
     * @return File
     * @throws IOException
     */
    @kotlin.Throws(IOException::class)
    protected open fun wrapStderr(): File? = StandardOutputFile(FTYPE_STDERR)

    /**
     * Open a file in a particular mode.
     * @param filename
     * @param readMode true if opening in read mode
     * @param appendMode true if opening in append mode
     * @param updateMode true if opening in update mode
     * @param binaryMode true if opening in binary mode
     * @return File object if successful
     * @throws IOException if could not be opened
     */
    @kotlin.Throws(IOException::class)
    protected open fun openFile(
        filename: String?,
        readMode: Boolean,
        appendMode: Boolean,
        updateMode: Boolean,
        binaryMode: Boolean
    ): File? {
        val path: String = filename ?: throw IOException("no file name")
        // Every mode Lua accepts maps onto one C fopen mode; binaryMode makes
        // no difference here because the handles are byte-oriented anyway.
        val mode: PlatformFileMode = when {
            readMode -> if (updateMode) PlatformFileMode.READ_WRITE else PlatformFileMode.READ
            appendMode -> if (updateMode) PlatformFileMode.READ_APPEND else PlatformFileMode.APPEND
            else -> if (updateMode) PlatformFileMode.READ_WRITE_TRUNCATE else PlatformFileMode.WRITE
        }
        return HostFile(
            platformOpenFile(path, mode),
            path,
            deleteOnClose = false,
            readable = readMode || updateMode,
            writable = !readMode || updateMode,
        )
    }

    /**
     * Open a temporary file.
     * @return File object if successful
     * @throws IOException if could not be opened
     */
    @kotlin.Throws(IOException::class)
    protected open fun tmpFile(): File? {
        val path: String = platformTempFilePath()
        return HostFile(
            platformOpenFile(path, PlatformFileMode.READ_WRITE_TRUNCATE),
            path,
            deleteOnClose = true,
            readable = true,
            writable = true,
        )
    }

    /**
     * Start a new process and return a file for input or output.
     *
     * Spawning a process has no portable form across the supported targets, so
     * the shared implementation always fails; [net.blueva.luak.lib.jvm.JvmIoLib]
     * overrides it with a real one.
     * @param prog the program to execute
     * @param mode "r" to read, "w" to write
     * @return File to read to or write from
     * @throws IOException if an i/o exception occurs
     */
    @kotlin.Throws(IOException::class)
    protected open fun openProgram(prog: String?, mode: String?): File? =
        throw IOException("io.popen is not supported on this platform")

    /** A file backed by a real host file, seekable in both directions. */
    private inner class HostFile(
        private val handle: PlatformFileHandle,
        private val path: String,
        private val deleteOnClose: Boolean,
        /** Whether the mode it was opened in allows reading. */
        private val readable: Boolean,
        /** Whether the mode it was opened in allows writing. */
        private val writable: Boolean,
    ) : File() {
        private var closed = false
        private var nobuffer = false

        // Identity, not the path: two handles on the same file are two distinct
        // Lua values and must not share a tostring().
        override fun tojstring(): String =
            "file (" + (if (closed) "closed" else "0x" + hashCode().toString(16)) + ")"

        override fun isstdfile(): Boolean = false
        override fun isclosed(): Boolean = closed

        @kotlin.Throws(IOException::class)
        override fun write(string: LuaString?) {
            // What the host reports for the wrong end of a handle, which is
            // the failure the caller is told about.
            if (!writable) throw IOException(BAD_DESCRIPTOR)
            val s: LuaString = string ?: return
            handle.write(s.m_bytes, s.m_offset, s.m_length)
            if (nobuffer) flush()
        }

        @kotlin.Throws(IOException::class)
        override fun flush() = handle.flush()

        @kotlin.Throws(IOException::class)
        override fun close() {
            if (closed) return
            closed = true
            handle.close()
            if (deleteOnClose) {
                try {
                    platformDeleteFile(path)
                } catch (ignored: IOException) {
                    // A temporary file the host already reclaimed is not an error.
                }
            }
        }

        @kotlin.Throws(IOException::class)
        override fun seek(option: String?, bytecount: Int): Int {
            val target: Long = when (option) {
                "set" -> bytecount.toLong()
                "end" -> handle.size() + bytecount
                else -> handle.position() + bytecount
            }
            handle.seek(target)
            return handle.position().toInt()
        }

        override fun setvbuf(mode: String?, size: Int) {
            nobuffer = "no" == mode
        }

        @kotlin.Throws(IOException::class)
        override fun remaining(): Int = (handle.size() - handle.position()).toInt()

        @kotlin.Throws(IOException::class, EOFException::class)
        override fun peek(): Int {
            val here: Long = handle.position()
            val value: Int = read()
            handle.seek(here)
            return value
        }

        @kotlin.Throws(IOException::class, EOFException::class)
        override fun read(): Int {
            if (!readable) throw IOException(BAD_DESCRIPTOR)
            val byte = ByteArray(1)
            return if (handle.read(byte, 0, 1) < 0) -1 else byte[0].toInt() and 0xff
        }

        @kotlin.Throws(IOException::class)
        override fun read(bytes: ByteArray?, offset: Int, length: Int): Int {
            if (!readable) throw IOException(BAD_DESCRIPTOR)
            val target: ByteArray = bytes ?: return -1
            return handle.read(target, offset, length)
        }
    }

    /** `io.stdout` / `io.stderr`, writing through the [Globals] streams. */
    private inner class StandardOutputFile(private val fileType: Int) : File() {
        override fun tojstring(): String = "file (0x" + hashCode().toString(16) + ")"

        private fun stream() = if (fileType == FTYPE_STDERR) globals?.STDERR else globals?.STDOUT

        @kotlin.Throws(IOException::class)
        override fun write(string: LuaString?) {
            val s: LuaString = string ?: return
            stream()?.write(s.m_bytes, s.m_offset, s.m_length)
        }

        @kotlin.Throws(IOException::class)
        override fun flush() {
            stream()?.flush()
        }

        override fun isstdfile(): Boolean = true

        @kotlin.Throws(IOException::class)
        override fun close() {
            // do not close std files.
        }

        override fun isclosed(): Boolean = false

        @kotlin.Throws(IOException::class)
        /**
         * Always fails: a standard stream has no position to move.
         *
         * The exception becomes the `nil, message, code` an io function
         * answers a failure with.
         */
        override fun seek(option: String?, bytecount: Int): Int {
            throw IOException("Illegal seek")
        }

        override fun setvbuf(mode: String?, size: Int) = Unit

        @kotlin.Throws(IOException::class)
        override fun remaining(): Int = 0

        @kotlin.Throws(IOException::class, EOFException::class)
        override fun peek(): Int = 0

        @kotlin.Throws(IOException::class, EOFException::class)
        override fun read(): Int = 0

        @kotlin.Throws(IOException::class)
        override fun read(bytes: ByteArray?, offset: Int, length: Int): Int = 0
    }

    /**
     * `io.stdin`. Standard input is not seekable, so `peek()` is served from a
     * one-byte pushback rather than by rewinding the stream.
     */
    private inner class StandardInputFile : File() {
        private var pushback = -1

        override fun tojstring(): String = "file (0x" + hashCode().toString(16) + ")"

        private fun stream(): InputStream? = globals?.STDIN ?: platformStandardInput()

        @kotlin.Throws(IOException::class)
        override fun write(string: LuaString?) = Unit

        @kotlin.Throws(IOException::class)
        override fun flush() = Unit

        override fun isstdfile(): Boolean = true

        @kotlin.Throws(IOException::class)
        override fun close() {
            // do not close std files.
        }

        override fun isclosed(): Boolean = false

        @kotlin.Throws(IOException::class)
        /**
         * Always fails: a standard stream has no position to move.
         *
         * The exception becomes the `nil, message, code` an io function
         * answers a failure with.
         */
        override fun seek(option: String?, bytecount: Int): Int {
            throw IOException("Illegal seek")
        }

        override fun setvbuf(mode: String?, size: Int) = Unit

        @kotlin.Throws(IOException::class)
        override fun remaining(): Int = -1

        @kotlin.Throws(IOException::class, EOFException::class)
        override fun peek(): Int {
            if (pushback < 0) pushback = stream()?.read() ?: -1
            return pushback
        }

        @kotlin.Throws(IOException::class, EOFException::class)
        override fun read(): Int {
            if (pushback >= 0) {
                val value = pushback
                pushback = -1
                return value
            }
            return stream()?.read() ?: -1
        }

        @kotlin.Throws(IOException::class)
        override fun read(bytes: ByteArray?, offset: Int, length: Int): Int {
            val target: ByteArray = bytes ?: return -1
            if (length == 0) return 0
            var written = 0
            if (pushback >= 0) {
                target[offset] = pushback.toByte()
                pushback = -1
                written = 1
                if (written == length) return written
            }
            val count: Int = stream()?.read(target, offset + written, length - written) ?: -1
            if (count < 0) return if (written > 0) written else -1
            return written + count
        }
    }

    private var infile: File? = null
    private var outfile: File? = null
    private var errfile: File? = null

    var filemethods: LuaTable? = null

    protected var globals: Globals? = null

    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        globals = env!!.checkglobals()


        // io lib functions
        val t: LuaTable = LuaTable()
        bind(t, { IoLibV() }, net.blueva.luak.lib.IoLib.Companion.IO_NAMES)


        // create file methods table
        filemethods = LuaTable()
        bind(
            (filemethods)!!,
            { IoLibV() },
            net.blueva.luak.lib.IoLib.Companion.FILE_NAMES,
            net.blueva.luak.lib.IoLib.Companion.FILE_CLOSE
        )

        // set up file metatable
        val mt: LuaTable = LuaTable()
        bind(
            mt,
            { IoLibV() },
            arrayOf<String?>("__index"),
            net.blueva.luak.lib.IoLib.Companion.IO_INDEX
        )
        t.setmetatable(mt)


        // all functions link to library instance
        setLibInstance(t)
        setLibInstance((filemethods)!!)
        // Lua names the file handle type, which is what a script reads back
        // from getmetatable(f).__name. Set after the binding pass, which walks
        // the table expecting every value to be one of the library functions.
        filemethods!!.set("__name", "FILE*")
        // A file handle is closable, so `local f <close> = io.open(...)` closes
        // it on the way out of the block whichever way the block is left. A
        // handle that was closed by hand first is left alone rather than
        // complained about, which is what lets both forms be written together.
        val closer = closehandle()
        filemethods!!.set("__close", closer)
        // Lua's file metatable names the same function under __gc, since that
        // is what would close the handle if a collector ran finalizers. This
        // runtime never does - for a file or for anything else - so the field
        // says what closing means here rather than promising it will happen.
        filemethods!!.set("__gc", closer)
        setLibInstance(mt)


        // return the table
        // The three standard streams, which Lua exposes as ready-made file
        // handles rather than only through io.read and io.write.
        t.set("stdin", ioopenfile(net.blueva.luak.lib.IoLib.Companion.FTYPE_STDIN, "-", "r")!!)
        t.set("stdout", ioopenfile(net.blueva.luak.lib.IoLib.Companion.FTYPE_STDOUT, "-", "w")!!)
        t.set("stderr", ioopenfile(net.blueva.luak.lib.IoLib.Companion.FTYPE_STDERR, "-", "w")!!)

        env!!.set("io", t)
        if (!env!!.get("package")!!.isnil()) env!!.get("package")!!.get("loaded")!!.set("io", t)
        return t
    }

    private fun setLibInstance(t: LuaTable) {
        val k: Array<LuaValue?> = t.keys()
        var i = 0
        val n = k.size
        while (i < n) {
            (t.get((k[i])!!) as IoLibV).iolib = this
            i++
        }
    }

    internal class IoLibV : VarArgFunction {
        private var f: File? = null
        var iolib: IoLib? = null
        private var toclose = false
        private var args: Varargs? = null

        constructor()
        constructor(f: File?, name: String?, opcode: Int, iolib: IoLib, toclose: Boolean, args: Varargs) : this(
            f,
            name,
            opcode,
            iolib
        ) {
            this.toclose = toclose
            this.args = args.dealias()
        }

        constructor(f: File?, name: String?, opcode: Int, iolib: IoLib) : super() {
            this.f = f
            this.name = name
            this.opcode = opcode
            this.iolib = iolib
        }

        override fun invoke(args: Varargs): Varargs {
            try {
                when (opcode) {
                    net.blueva.luak.lib.IoLib.Companion.IO_FLUSH -> return iolib!!._io_flush()
                    net.blueva.luak.lib.IoLib.Companion.IO_TMPFILE -> return (iolib!!._io_tmpfile())!!
                    net.blueva.luak.lib.IoLib.Companion.IO_CLOSE -> return iolib!!._io_close((args.arg1())!!)
                    net.blueva.luak.lib.IoLib.Companion.IO_INPUT -> return (iolib!!._io_input((args.arg1())!!))!!
                    net.blueva.luak.lib.IoLib.Companion.IO_OUTPUT -> return (iolib!!._io_output((args.arg1())!!))!!
                    net.blueva.luak.lib.IoLib.Companion.IO_TYPE -> return (iolib!!._io_type(args.arg1()))!!
                    net.blueva.luak.lib.IoLib.Companion.IO_POPEN -> return iolib!!._io_popen(
                        args.checkjstring(1),
                        args.optjstring(2, "r")
                    )!!

                    net.blueva.luak.lib.IoLib.Companion.IO_OPEN -> return iolib!!._io_open(
                        args.checkjstring(1),
                        args.optjstring(2, "r")!!
                    )!!

                    net.blueva.luak.lib.IoLib.Companion.IO_LINES -> return (iolib!!._io_lines(args))!!
                    net.blueva.luak.lib.IoLib.Companion.IO_READ -> return iolib!!._io_read(args)
                    net.blueva.luak.lib.IoLib.Companion.IO_WRITE -> return iolib!!._io_write(args)

                    net.blueva.luak.lib.IoLib.Companion.FILE_CLOSE -> return iolib!!._file_close(args.arg1())
                    net.blueva.luak.lib.IoLib.Companion.FILE_FLUSH -> return iolib!!._file_flush(args.arg1())
                    net.blueva.luak.lib.IoLib.Companion.FILE_SETVBUF -> return iolib!!._file_setvbuf(
                        args.arg1(),
                        args.checkjstring(2),
                        args.optint(3, 8192)
                    )

                    net.blueva.luak.lib.IoLib.Companion.FILE_LINES -> return (iolib!!._file_lines(args))!!
                    net.blueva.luak.lib.IoLib.Companion.FILE_READ -> return iolib!!._file_read(
                        args.arg1(),
                        args.subargs(2)!!
                    )

                    net.blueva.luak.lib.IoLib.Companion.FILE_SEEK -> return iolib!!._file_seek(
                        args.arg1(),
                        args.optjstring(2, "cur"),
                        args.optint(3, 0)
                    )

                    net.blueva.luak.lib.IoLib.Companion.FILE_WRITE -> return iolib!!._file_write(
                        args.arg1(),
                        args.subargs(2)!!
                    )

                    net.blueva.luak.lib.IoLib.Companion.IO_INDEX -> return (iolib!!._io_index((args.arg(2))!!))!!
                    net.blueva.luak.lib.IoLib.Companion.LINES_ITER -> return iolib!!._lines_iter(f, toclose, (this.args)!!)
                }
            } catch (ioe: IOException) {
                if (opcode === net.blueva.luak.lib.IoLib.Companion.LINES_ITER) {
                    val s: String? = ioe.message
                    error(if (s != null) s else ioe.toString())
                }
                return errorresult(ioe)
            }
            return (NONE)!!
        }
    }

    private fun input(): File? {
        return if (infile != null) infile else (ioopenfile(
            net.blueva.luak.lib.IoLib.Companion.FTYPE_STDIN,
            "-",
            "r"
        ).also { infile = it })
    }

    //	io.flush() -> bool
    @kotlin.Throws(IOException::class)
    fun _io_flush(): Varargs {
        net.blueva.luak.lib.IoLib.Companion.checkdefault(output(), "output")
        outfile!!.flush()
        return (LuaValue.TRUE)!!
    }

    //	io.tmpfile() -> file
    @kotlin.Throws(IOException::class)
    fun _io_tmpfile(): Varargs? {
        return tmpFile()
    }

    //	io.close([file]) -> void
    @kotlin.Throws(IOException::class)
    fun _io_close(file: LuaValue): Varargs {
        val f = if (file.isnil()) output() else net.blueva.luak.lib.IoLib.Companion.checkfile(file)
        net.blueva.luak.lib.IoLib.Companion.checkopen(f)
        return net.blueva.luak.lib.IoLib.Companion.ioclose(f)
    }

    //	io.input([file]) -> file
    fun _io_input(file: LuaValue): Varargs? {
        infile = if (file.isnil()) input() else if (file.isstring()) ioopenfile(
            net.blueva.luak.lib.IoLib.Companion.FTYPE_NAMED,
            file.checkjstring(),
            "r"
        ) else net.blueva.luak.lib.IoLib.Companion.checkfile(file)
        return infile
    }

    // io.output(filename) -> file
    fun _io_output(filename: LuaValue): Varargs? {
        outfile = if (filename.isnil()) output() else if (filename.isstring()) ioopenfile(
            net.blueva.luak.lib.IoLib.Companion.FTYPE_NAMED,
            filename.checkjstring(),
            "w"
        ) else net.blueva.luak.lib.IoLib.Companion.checkfile(filename)
        return outfile
    }

    //	io.type(obj) -> "file" | "closed file" | nil
    fun _io_type(obj: LuaValue?): Varargs? {
        val f: File? = net.blueva.luak.lib.IoLib.Companion.optfile(obj)
        return if (f != null) if (f.isclosed()) net.blueva.luak.lib.IoLib.Companion.CLOSED_FILE else net.blueva.luak.lib.IoLib.Companion.FILE else NIL
    }

    // io.popen(prog, [mode]) -> file
    @kotlin.Throws(IOException::class)
    fun _io_popen(prog: String?, mode: String?): Varargs? {
        if (!"r".equals(mode) && !"w".equals(mode)) argerror(
            2,
            "invalid value: '" + mode + "'; must be one of 'r' or 'w'"
        )
        return openProgram(prog, mode)
    }

    //	io.open(filename, [mode]) -> file | nil,err
    @kotlin.Throws(IOException::class)
    fun _io_open(filename: String?, mode: String): Varargs? {
        return rawopenfile(net.blueva.luak.lib.IoLib.Companion.FTYPE_NAMED, filename, mode)
    }

    //	io.lines(filename, ...) -> iterator
    fun _io_lines(args: Varargs): Varargs? {
        // Every format is held on the stack while the iterator runs, so Lua
        // puts a ceiling on how many there may be.
        args.argcheck(
            args.narg() - 1 <= net.blueva.luak.lib.IoLib.Companion.MAX_LINE_FORMATS,
            net.blueva.luak.lib.IoLib.Companion.MAX_LINE_FORMATS + 2,
            "too many arguments",
        )
        val filename: String? = args.optjstring(1, null)
        val infile = if (filename == null) input() else ioopenfile(
            net.blueva.luak.lib.IoLib.Companion.FTYPE_NAMED,
            filename,
            "r"
        )
        net.blueva.luak.lib.IoLib.Companion.checkopen((infile)!!)
        return lines(infile, filename != null, (args.subargs(2))!!)
    }

    //	io.read(...) -> (...)
    @kotlin.Throws(IOException::class)
    fun _io_read(args: Varargs): Varargs {
        net.blueva.luak.lib.IoLib.Companion.checkdefault((input())!!, "input")
        return ioread(infile!!, args)
    }

    //	io.write(...) -> void
    @kotlin.Throws(IOException::class)
    fun _io_write(args: Varargs): Varargs {
        net.blueva.luak.lib.IoLib.Companion.checkdefault(output(), "output")
        return net.blueva.luak.lib.IoLib.Companion.iowrite((outfile)!!, args)
    }

    // file:close() -> void
    @kotlin.Throws(IOException::class)
    fun _file_close(file: LuaValue?): Varargs {
        return net.blueva.luak.lib.IoLib.Companion.ioclose(net.blueva.luak.lib.IoLib.Companion.checkfile(file))
    }

    /**
     * `__close` for a file handle, upstream's `f_gc`.
     *
     * Unlike `file:close()` this says nothing about a handle that is already
     * closed: leaving the block is not a request to close it a second time.
     */
    internal class closehandle : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val handle: LuaValue? = args.arg1()
            val file: File? = net.blueva.luak.lib.IoLib.Companion.optfile(handle)
            // It still has to be given a handle; what it tolerates is one that
            // has already been closed.
            if (file == null) {
                val got: String =
                    if (handle == null || handle.isnil()) "no value" else handle.argtypename()
                LuaValue.argerror(1, "FILE* expected, got " + got)
            }
            if (file!!.isclosed()) return (LuaValue.TRUE)!!
            return net.blueva.luak.lib.IoLib.Companion.ioclose(file)
        }
    }

    // file:flush() -> void
    @kotlin.Throws(IOException::class)
    fun _file_flush(file: LuaValue?): Varargs {
        net.blueva.luak.lib.IoLib.Companion.checkfile(file).flush()
        return (LuaValue.TRUE)!!
    }

    // file:setvbuf(mode,[size]) -> void
    fun _file_setvbuf(file: LuaValue?, mode: String?, size: Int): Varargs {
        if ("no".equals(mode)) {
        } else if ("full".equals(mode)) {
        } else if ("line".equals(mode)) {
        } else {
            argerror(1, "invalid value: '" + mode + "'; must be one of 'no', 'full' or 'line'")
        }
        net.blueva.luak.lib.IoLib.Companion.checkfile(file).setvbuf(mode, size)
        return (LuaValue.TRUE)!!
    }

    // file:lines(...) -> iterator
    fun _file_lines(args: Varargs): Varargs? {
        return lines(net.blueva.luak.lib.IoLib.Companion.checkfile(args.arg1()), false, (args.subargs(2))!!)
    }

    //	file:read(...) -> (...)
    @kotlin.Throws(IOException::class)
    fun _file_read(file: LuaValue?, subargs: Varargs): Varargs {
        return ioread(net.blueva.luak.lib.IoLib.Companion.checkfile(file), subargs)
    }

    //  file:seek([whence][,offset]) -> pos | nil,error
    @kotlin.Throws(IOException::class)
    fun _file_seek(file: LuaValue?, whence: String?, offset: Int): Varargs {
        if ("set".equals(whence)) {
        } else if ("end".equals(whence)) {
        } else if ("cur".equals(whence)) {
        } else {
            argerror(1, "invalid value: '" + whence + "'; must be one of 'set', 'cur' or 'end'")
        }
        return valueOf(net.blueva.luak.lib.IoLib.Companion.checkfile(file).seek(whence, offset))
    }

    //	file:write(...) -> void
    @kotlin.Throws(IOException::class)
    fun _file_write(file: LuaValue?, subargs: Varargs): Varargs {
        return net.blueva.luak.lib.IoLib.Companion.iowrite(net.blueva.luak.lib.IoLib.Companion.checkfile(file), subargs)
    }

    // __index, returns a field
    fun _io_index(v: LuaValue): Varargs? {
        return if (v.equals(net.blueva.luak.lib.IoLib.Companion.STDOUT)) output() else if (v.equals(net.blueva.luak.lib.IoLib.Companion.STDIN)) input() else if (v.equals(
                net.blueva.luak.lib.IoLib.Companion.STDERR
            )
        ) errput() else NIL
    }

    //	lines iterator(s,var) -> var'
    @kotlin.Throws(IOException::class)
    fun _lines_iter(file: LuaValue?, toclose: Boolean, args: Varargs): Varargs {
        val f: File? = net.blueva.luak.lib.IoLib.Companion.optfile(file)
        if (f == null) argerror(1, "not a file: " + file)
        if (f!!.isclosed()) error("file is already closed")
        val ret: Varargs = ioread(f, args)
        if (toclose && ret.isnil(1) && f.eof()) f.close()
        return ret
    }

    private fun output(): File {
        return (if (outfile != null) outfile else (ioopenfile(
            net.blueva.luak.lib.IoLib.Companion.FTYPE_STDOUT,
            "-",
            "w"
        ).also { outfile = it }))!!
    }

    private fun errput(): File? {
        return if (errfile != null) errfile else (ioopenfile(
            net.blueva.luak.lib.IoLib.Companion.FTYPE_STDERR,
            "-",
            "w"
        ).also { errfile = it })
    }

    private fun ioopenfile(filetype: Int, filename: String?, mode: String): File? {
        try {
            return rawopenfile(filetype, filename, mode)
        } catch (e: Exception) {
            error("io error: " + e.message)
            return null
        }
    }

    private fun lines(f: File?, toclose: Boolean, args: Varargs): Varargs? {
        val iterator: LuaValue = try {
            net.blueva.luak.lib.IoLib.IoLibV(
                f,
                "lnext",
                net.blueva.luak.lib.IoLib.Companion.LINES_ITER,
                this,
                toclose,
                args
            )
        } catch (e: Exception) {
            return error("lines: " + e)
        }
        // A file this call opened is handed back as the loop's fourth value,
        // so the generic for closes it however the loop is left. A file the
        // caller already had is left alone.
        if (!toclose) return iterator
        return varargsOf(arrayOf(iterator, NIL, NIL, f))
    }

    @kotlin.Throws(IOException::class)
    private fun ioread(f: File, args: Varargs): Varargs {
        var i: Int
        val n: Int = args.narg()
        if (n == 0) return net.blueva.luak.lib.IoLib.Companion.freadline(f, false)
        val v: Array<LuaValue?> = arrayOfNulls<LuaValue>(n)
        var ai: LuaValue?
        var vi: LuaValue? = NIL
        var fmt: LuaString
        i = 0
        while (i < n) {
            run item@ {
                when ((args.arg(i + 1).also { ai = it })!!.type()) {
                LuaValue.TNUMBER -> {
                    vi = net.blueva.luak.lib.IoLib.Companion.freadbytes(f, ai!!.toint())
                    return@item
                }

                LuaValue.TSTRING -> {
                    fmt = ai!!.checkstring()!!
                    // Since 5.3 the leading '*' is optional, so "n" and "*n"
                    // name the same format.
                    val star: Int =
                        if (fmt.m_length >= 1 && fmt.m_bytes[fmt.m_offset] == '*'.code.toByte()) 1 else 0
                    if (fmt.m_length >= star + 1) {
                        when (fmt.m_bytes[fmt.m_offset + star]) {
                            'n'.code.toByte() -> {
                                vi = net.blueva.luak.lib.IoLib.Companion.freadnumber(f)
                                return@item
                            }

                            'l'.code.toByte() -> {
                                vi = net.blueva.luak.lib.IoLib.Companion.freadline(f, false)
                                return@item
                            }

                            'L'.code.toByte() -> {
                                vi = net.blueva.luak.lib.IoLib.Companion.freadline(f, true)
                                return@item
                            }

                            'a'.code.toByte() -> {
                                vi = net.blueva.luak.lib.IoLib.Companion.freadall(f)
                                return@item
                            }
                        }
                    }
                    return (argerror(i + 1, "(invalid format)"))!!
                }

                else -> return (argerror(i + 1, "(invalid format)"))!!
                }
            }
            if ((vi.also { v[i++] = it })!!.isnil()) break
        }
        return (if (i == 0) NIL else varargsOf(v, 0, i))!!
    }

    @kotlin.Throws(IOException::class)
    private fun rawopenfile(filetype: Int, filename: String?, mode: String): File? {
        var len: Int = mode.length
        var i = 0
        while (i < len) {
            // [rwa][+]?b*
            val ch: Char = mode[i]
            if (i == 0 && "rwa".indexOf(ch) >= 0) {
                i++
                continue
            }
            if (i == 1 && ch == '+') {
                i++
                continue
            }
            if (i >= 1 && ch == 'b') {
                i++
                continue
            }
            len = -1
            break
            i++
        }
        if (len <= 0) argerror(2, "invalid mode: '" + mode + "'")

        when (filetype) {
            net.blueva.luak.lib.IoLib.Companion.FTYPE_STDIN -> return wrapStdin()
            net.blueva.luak.lib.IoLib.Companion.FTYPE_STDOUT -> return wrapStdout()
            net.blueva.luak.lib.IoLib.Companion.FTYPE_STDERR -> return wrapStderr()
        }
        val isreadmode: Boolean = mode.startsWith("r")
        val isappend: Boolean = mode.startsWith("a")
        val isupdate = mode.indexOf('+') > 0
        val isbinary: Boolean = mode.endsWith("b")
        return openFile(filename, isreadmode, isappend, isupdate, isbinary)
    }


    companion object {
        /** Enumerated value representing stdin  */
        protected const val FTYPE_STDIN: Int = 0

        /** Enumerated value representing stdout  */
        protected const val FTYPE_STDOUT: Int = 1

        /** Enumerated value representing stderr  */
        protected const val FTYPE_STDERR: Int = 2

        /** Enumerated value representing a file type for a named file  */
        protected const val FTYPE_NAMED: Int = 3

        private val STDIN: LuaValue? = valueOf("stdin")
        private val STDOUT: LuaValue? = valueOf("stdout")
        private val STDERR: LuaValue? = valueOf("stderr")
        private val FILE: LuaValue? = valueOf("file")

        /** C's ENOENT: no such file or directory. */
        private const val ENOENT: Int = 2

        /** C's EBADF: the handle is not open for what was asked of it. */
        internal const val EBADF: Int = 9

        /** What the host says when a handle is used the wrong way round. */
        internal const val BAD_DESCRIPTOR: String = "Bad file descriptor"
        private val CLOSED_FILE: LuaValue? = valueOf("closed file")

        private const val IO_CLOSE = 0
        private const val IO_FLUSH = 1
        private const val IO_INPUT = 2
        private const val IO_LINES = 3
        private const val IO_OPEN = 4
        private const val IO_OUTPUT = 5
        private const val IO_POPEN = 6
        private const val IO_READ = 7
        private const val IO_TMPFILE = 8
        private const val IO_TYPE = 9
        private const val IO_WRITE = 10

        private const val FILE_CLOSE = 11
        private const val FILE_FLUSH = 12
        private const val FILE_LINES = 13
        private const val FILE_READ = 14
        private const val FILE_SEEK = 15
        private const val FILE_SETVBUF = 16
        private const val FILE_WRITE = 17

        private const val IO_INDEX = 18
        private const val LINES_ITER = 19

        val IO_NAMES: Array<String?> = arrayOf<String?>(
            "close",
            "flush",
            "input",
            "lines",
            "open",
            "output",
            "popen",
            "read",
            "tmpfile",
            "type",
            "write",
        )

        val FILE_NAMES: Array<String?> = arrayOf<String?>(
            "close",
            "flush",
            "lines",
            "read",
            "seek",
            "setvbuf",
            "write",
        )

        @kotlin.Throws(IOException::class)
        internal fun ioclose(f: File): Varargs {
            if (f.isstdfile()) return net.blueva.luak.lib.IoLib.Companion.errorresult("cannot close standard file")
            else {
                f.close()
                return net.blueva.luak.lib.IoLib.Companion.successresult()
            }
        }

        private fun successresult(): Varargs {
            return (LuaValue.TRUE)!!
        }

        fun errorresult(ioe: Exception): Varargs {
            val s: String? = ioe.message
            // nil, message, errno - the shape every io function answers with,
            // so a caller can branch on the number without parsing the text.
            return net.blueva.luak.lib.IoLib.Companion.errorresult(
                if (s != null) s else ioe.toString(),
            )
        }

        /**
         * The `nil, message, errno` an io function answers a failure with.
         *
         * There is no errno to read from a host exception here, so the number
         * is the one C uses for a file that is not there - which is what a
         * caller branching on it is almost always looking for.
         */
        private fun errorresult(errortext: String?): Varargs {
            val errno: Int = if (errortext == BAD_DESCRIPTOR) EBADF else ENOENT
            return (varargsOf(NIL, valueOf(errortext), valueOf(errno)))!!
        }

        @kotlin.Throws(IOException::class)
        private fun iowrite(f: File, args: Varargs): Varargs {
            var i = 1
            val n: Int = args.narg()
            while (i <= n) {
                f.write(args.checkstring(i))
                i++
            }
            return f
        }

        private fun checkfile(`val`: LuaValue?): File {
            val f: File? = net.blueva.luak.lib.IoLib.Companion.optfile(`val`)
            // Worded the way Lua words it, including what was there instead,
            // since calling a file method with no self is the usual mistake.
            if (f == null) {
                // The name the value's own metatable gives it, if it has one,
                // so a script that passed the wrong handle sees which.
                val got: String =
                    if (`val` == null || `val`.isnil()) "no value" else `val`.argtypename()
                argerror(1, "FILE* expected, got " + got)
            }
            net.blueva.luak.lib.IoLib.Companion.checkopen((f)!!)
            return f!!
        }

        internal fun optfile(`val`: LuaValue?): File? {
            return if (`val` is File) `val` as File? else null
        }

        private fun checkopen(file: File): File {
            if (file.isclosed()) error("attempt to use a closed file")
            return file
        }

        /**
         * The default input or output file, refused if it has been closed.
         *
         * Named in the message, since a script that closed `io.input()` needs
         * to know which of the two defaults it is being told about.
         */
        private fun checkdefault(file: File, which: String): File {
            if (file.isclosed()) error("default " + which + " file is closed")
            return file
        }

        // ------------- file reading utilitied ------------------
        @kotlin.Throws(IOException::class)
        fun freadbytes(f: File, count: Int): LuaValue {
            if (count == 0) return if (f.eof()) NIL else EMPTYSTRING
            val b = ByteArray(count)
            val r: Int
            if ((f.read(b, 0, b.size).also { r = it }) < 0) return NIL
            return LuaString.valueUsing(b, 0, r)
        }

        @kotlin.Throws(IOException::class)
        fun freaduntil(f: File, lineonly: Boolean, withend: Boolean): LuaValue {
            val baos: ByteArrayOutputStream = ByteArrayOutputStream()
            var c: Int
            try {
                if (lineonly) {
                    loop@ while ((f.read().also { c = it }) >= 0) {
                        when (c) {
                            '\r'.code -> if (withend) baos.write(c)
                            '\n'.code -> {
                                if (withend) baos.write(c)
                                break@loop
                            }

                            else -> baos.write(c)
                        }
                    }
                } else {
                    while ((f.read().also { c = it }) >= 0) baos.write(c)
                }
            } catch (e: EOFException) {
                c = -1
            }
            return (if (c < 0 && baos.size() === 0) NIL as LuaValue else LuaString.valueUsing(baos.toByteArray()) as LuaValue?)!!
        }

        @kotlin.Throws(IOException::class)
        fun freadline(f: File, withend: Boolean): LuaValue {
            return net.blueva.luak.lib.IoLib.Companion.freaduntil(f, true, withend)
        }

        @kotlin.Throws(IOException::class)
        fun freadall(f: File): LuaValue? {
            val n = f.remaining()
            if (n >= 0) {
                return if (n == 0) EMPTYSTRING else net.blueva.luak.lib.IoLib.Companion.freadbytes(f, n)
            } else {
                return net.blueva.luak.lib.IoLib.Companion.freaduntil(f, false, false)
            }
        }

        /**
         * `io.read("n")`: the next numeral in the stream, or nil.
         *
         * The numeral is assembled one piece at a time, the way upstream's
         * `read_number` does, so no more of the stream is consumed than the
         * numeral itself. Hexadecimal numerals and exponents are read too, and
         * the result keeps its subtype: `12345` comes back as an integer, not
         * as the float a single decimal conversion would give.
         */
        @kotlin.Throws(IOException::class)
        fun freadnumber(f: File): LuaValue {
            val baos: ByteArrayOutputStream = ByteArrayOutputStream()
            var length = 0
            var overflowed = false

            /** Consumes one character out of [chars], if the next one is in it. */
            fun one(chars: String): Boolean {
                if (overflowed) return false
                val c: Int = f.peek()
                if (c < 0 || chars.indexOf(c.toChar()) < 0) return false
                // Once the numeral is as long as Lua allows the read stops
                // here, leaving the rest of it in the stream for whoever reads
                // next, and the result is refused.
                if (length >= net.blueva.luak.lib.IoLib.Companion.MAX_NUMERAL_LENGTH) {
                    overflowed = true
                    return false
                }
                f.read()
                baos.write(c)
                length++
                return true
            }

            fun many(chars: String): Int {
                var count = 0
                while (one(chars)) count++
                return count
            }

            net.blueva.luak.lib.IoLib.Companion.freadchars(f, " \t\r\n", null)
            one("-+")
            var hexadecimal = false
            var digits = 0
            if (one("0")) {
                if (one("xX")) hexadecimal = true else digits = 1
            }
            val digitChars = if (hexadecimal) "0123456789abcdefABCDEF" else "0123456789"
            digits += many(digitChars)
            if (one(".")) digits += many(digitChars)
            if (digits > 0 && one(if (hexadecimal) "pP" else "eE")) {
                one("-+")
                many("0123456789")
            }
            if (overflowed) return NIL
            // decodeToString(), not toString(): only the JVM's
            // ByteArrayOutputStream renders its own bytes as text.
            val s: String = baos.toByteArray().decodeToString()
            return net.blueva.luak.NumberParser.parse(s) ?: NIL
        }

        /** As long as a numeral read from a file may be, upstream's `L_MAXLENNUM`. */
        private const val MAX_NUMERAL_LENGTH = 200

        /** As many formats as `io.lines` takes, upstream's `MAXARGLINE`. */
        internal const val MAX_LINE_FORMATS = 250

        /** Consumes one character out of [chars], if the next one is in it. */
        @kotlin.Throws(IOException::class)
        private fun freadone(f: File, chars: String, baos: ByteArrayOutputStream?): Boolean {
            val c: Int = f.peek()
            if (c < 0 || chars.indexOf(c.toChar()) < 0) return false
            f.read()
            baos?.write(c)
            return true
        }

        private fun freadchars(f: File, chars: String, baos: ByteArrayOutputStream?): Int {
            var count = 0
            var c: Int
            while (true) {
                c = f.peek()
                if (c < 0 || chars.indexOf(c.toChar()) < 0) {
                    return count
                }
                f.read()
                if (baos != null) baos.write(c)
                count++
            }
        }
    }
}
