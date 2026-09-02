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

import net.blueva.luak.lib.BaseLib
import net.blueva.luak.lib.DebugLib
import net.blueva.luak.lib.MathLib
import net.blueva.luak.lib.PackageLib
import net.blueva.luak.lib.ResourceFinder
import net.blueva.luak.io.IOException
import net.blueva.luak.io.InputStream
import net.blueva.luak.io.PrintStream
import net.blueva.luak.io.Reader
import net.blueva.luak.io.standardError
import net.blueva.luak.io.standardOutput

/**
 * Global environment used by Luak.  Contains global variables referenced by executing lua.
 * 
 * 
 * 
 * <h3>Constructing and Initializing Instances</h3>
 * Typically, this is constructed indirectly by a call to
 * [net.blueva.luak.lib.LuaPlatform.standardGlobals],
 * and then used to load lua scripts for execution as in the following example.
 * <pre> `Globals globals = JvmPlatform.standardGlobals(); globals.load( new StringReader("print 'hello'"), "main.lua" ).call(); ` </pre>
 * The creates a complete global environment with the standard libraries loaded.
 * 
 * 
 * For specialized circumstances, the Globals may be constructed directly and loaded
 * with only those libraries that are needed, for example.
 * <pre> `Globals globals = new Globals(); globals.load( new BaseLib() ); ` </pre>
 * 
 * <h3>Loading and Executing Lua Code</h3>
 * Globals contains convenience functions to load and execute lua source code given a Reader.
 * A simple example is:
 * <pre> `globals.load( new StringReader("print 'hello'"), "main.lua" ).call(); ` </pre>
 * 
 * <h3>Fine-Grained Control of Compiling and Loading Lua</h3>
 * Executable LuaFunctions are created from lua code in several steps
 * 
 *  * find the resource using the platform's [ResourceFinder]
 *  * compile lua to lua bytecode using [Compiler]
 *  * load lua bytecode to a [Prototype] using [Undumper]
 *  * construct [LuaClosure] from [Prototype] with [Globals] using [Loader]
 * 
 * 
 * 
 * There are alternate flows when the direct lua-to-Java bytecode compiling [net.blueva.luak.luajc.LuaJC] is used.
 * 
 *  * compile lua to lua bytecode using [Compiler] or load precompiled code using [Undumper]
 *  * convert lua bytecode to equivalent Java bytecode using [net.blueva.luak.luajc.LuaJC] that implements [Loader] directly
 * 
 * 
 * <h3>Java Field</h3>
 * Certain public fields are provided that contain the current values of important global state:
 * 
 *  * [.STDIN] Current value for standard input in the laaded [IoLib], if any.
 *  * [.STDOUT] Current value for standard output in the loaded [IoLib], if any.
 *  * [.STDERR] Current value for standard error in the loaded [IoLib], if any.
 *  * [.finder] Current loaded [ResourceFinder], if any.
 *  * [.compiler] Current loaded [Compiler], if any.
 *  * [.undumper] Current loaded [Undumper], if any.
 *  * [.loader] Current loaded [Loader], if any.
 * 
 * 
 * <h3>Lua Environment Variables</h3>
 * When using [net.blueva.luak.lib.LuaPlatform] or [net.blueva.luak.lib.jvm.JvmPlatform],
 * these environment variables are created within the Globals.
 * 
 *  * "_G" Pointer to this Globals.
 *  * "_VERSION" String containing the version of Lua implemented by Luak.
 * 
 * 
 * <h3>Use in Multithreaded Environments</h3>
 * In a multi-threaded server environment, each server thread should create one Globals instance,
 * which will be logically distinct and not interfere with each other, but share certain
 * static immutable resources such as class data and string data.
 * 
 * 
 * 
 * @see net.blueva.luak.lib.jvm.JvmPlatform
 * 
 * @see net.blueva.luak.lib.LuaPlatform
 * 
 * @see LuaValue
 * 
 * @see Compiler
 * 
 * @see Loader
 * 
 * @see Undumper
 * 
 * @see ResourceFinder
 * 
 * @see net.blueva.luak.compiler.LuaC
 * 
 * @see net.blueva.luak.luajc.LuaJC
 */
class Globals : LuaTable() {
    /** The current default input stream.  */
    var STDIN: InputStream? = null

    /** The current default output stream.  */
    var STDOUT: PrintStream? = standardOutput()

    /** The current default error stream.  */
    var STDERR: PrintStream? = standardError()

    /** The installed ResourceFinder for looking files by name.  */
    var finder: ResourceFinder? = null

    /** The currently running thread.  Should not be changed by non-library code.  */
    var running: LuaThread = LuaThread(this)

    /** The BaseLib instance loaded into this Globals  */
    var baselib: BaseLib? = null

    /** The PackageLib instance loaded into this Globals  */
    var package_: PackageLib? = null

    /** The DebugLib instance loaded into this Globals, or null if debugging is not enabled  */
    var debuglib: DebugLib? = null

    /** The MathLib instance loaded into this Globals, or null if `math` is not loaded  */
    var mathlib: MathLib? = null

    /**
     * What the host lets one resumption run, or null for no ceiling at all.
     *
     * Attaching a [Budget] is how a host bounds untrusted code without
     * loading [DebugLib] to get at its count hook; see [Budget] for what the
     * ceiling covers and what happens when it is reached. Null - the default
     * - costs the interpreter one null check per instruction.
     */
    var budget: Budget? = null

    /**
     * What this state's own Lua objects cost; see [Memory].
     *
     * One per state, so that two lanes in the same process neither see each
     * other's `collectgarbage("count")` nor spend each other's ceiling.
     */
    internal val memory: Memory = Memory()

    /**
     * Loads a library into this state, charging what it builds to this state.
     *
     * The standard libraries are most of what a fresh state holds, so a host
     * reading [memorycharged] or `collectgarbage("count")` should see them.
     */
    override fun load(library: LuaValue): LuaValue = Memory.charging(memory) { super.load(library) }

    /**
     * Bytes this state may be charged for before Lua in it fails, or 0 - the
     * default - for no ceiling.
     *
     * This is what stops a plugin taking the process down by filling a table:
     * past the ceiling, the allocation that crosses it raises `not enough
     * memory`, the same error Lua raises when a real one runs out, and every
     * allocation after it does the same until the host calls
     * [startmemorycount].
     *
     * ### What the figure counts
     *
     * Bytes charged since [startmemorycount], using the sizes a reference
     * build would use - so the ceiling is in the same units a script reads
     * from `collectgarbage("count")`, and a host can set it from a figure it
     * measured. It counts what was allocated, not what is still held: the
     * host's collector is the one that reclaims here, and seven of the eight
     * targets give no way to learn that a particular object has gone. A table
     * that grows is charged once for each size it grows through, so for the
     * runaway case the two figures are the same; a state that churns through
     * short-lived tables is charged for them even after they are gone, and
     * will reach the ceiling with less live than the ceiling says.
     *
     * That makes the ceiling a conservative one - it can arrive early, never
     * late - so a long-lived state wants the host to call [startmemorycount]
     * where it knows the lane is quiet, the way [Budget.instructions] is
     * refilled per resumption.
     *
     * Deliberately out of Lua's own reach: `collectgarbage` in any of its
     * forms clears the tally behind `collectgarbage("count")` and not this
     * one, since a ceiling a script can clear by calling a standard-library
     * function is not a ceiling.
     */
    var memoryceiling: Long
        get() = memory.ceiling
        set(value) {
            memory.ceiling = if (value > 0) value else 0
        }

    /** Bytes charged to this state since [startmemorycount]; see [memoryceiling]. */
    val memorycharged: Long
        get() = memory.charged

    /** Starts the tally [memoryceiling] is measured against again from nothing. */
    fun startmemorycount() {
        memory.startcounting()
    }

    /**
     * Refuses binary chunks in this state, however they are asked for.
     *
     * A binary chunk is trusted input: the undumper reads a format, not a
     * language, and a reference build says outright that a malformed one can
     * crash the interpreter - there is no amount of checking that makes
     * arbitrary bytes safe to load as bytecode. A host taking chunks from
     * somewhere it does not control wants them compiled from source and
     * nothing else.
     *
     * With this set, every route into the loader behaves as though only `t`
     * had been asked for. `load(s, name, "b")`, `load(s, name, "bt")` on a
     * dump, `loadfile`, `dofile`, `require`, and the host's own
     * [Globals.load] all answer Lua's own `attempt to load a binary chunk
     * (mode is 't')` - which is the message a reference build gives when the
     * mode rules a chunk out, so a script that already handles it needs no
     * telling that it is running in a sandbox.
     *
     * Source still compiles as usual, and `string.dump` still produces a
     * chunk; what is refused is reading one back.
     */
    var textonly: Boolean = false

    /**
     * Compiles [script] once, for [bind] to hand to any number of states.
     *
     * This is what makes a new lane cheap. Building the nine standard
     * libraries is not the expensive part of starting one - it is tens of
     * microseconds, and the function objects it makes cannot be shared
     * between lanes anyway: `math.random` carries a generator, `io` carries
     * open files, `package` carries what has been required, and a lane that
     * reached into another's would be no sandbox at all. Compiling the plugin
     * is the expensive part, by more than an order of magnitude, and unlike
     * the libraries it produces something there is no reason not to share: a
     * [Prototype] is the compiled code and its constants, finished and never
     * written to again.
     *
     * ```kotlin
     * val plugin: Prototype = template.compile(source, "@plugin.lua")
     * for (lane in lanes) lane.bind(plugin).call()   // compiled once
     * ```
     *
     * The state this is called on lends its compiler and nothing else; the
     * result belongs to no state until [bind] gives it one.
     *
     * @throws LuaError if the script does not compile
     */
    fun compile(script: String, chunkname: String?): Prototype =
        try {
            compilePrototype(net.blueva.luak.Globals.StrReader(script), chunkname)!!
        } catch (l: LuaError) {
            throw l
        } catch (e: Exception) {
            throw LuaError("load " + chunkname + ": " + e, e)
        }

    /**
     * Makes a callable function of [prototype] in this state.
     *
     * The prototype may have been compiled by any state, or by this one; what
     * it reads as globals, and what its `__gc` handlers and errors belong to,
     * is this one. See [compile].
     *
     * The name errors point at is the one the prototype was compiled under -
     * it is written into the compiled code, and every lane shares it.
     *
     * @param prototype compiled code, from [compile] or [loadPrototype]
     * @param environment what the chunk sees as its globals, this state by
     *   default
     * @throws LuaError if no loader is installed in this state
     */
    fun bind(prototype: Prototype, environment: LuaValue? = this): LuaFunction {
        val install: Loader = loader ?: throw LuaError("No loader.")
        val name: String? = prototype.source?.tojstring()
        // The closure belongs to the state it is being bound into, whichever
        // state ran last; see Memory.current.
        return Memory.charging(memory) {
            try {
                install.load(prototype, name, environment)!!
            } catch (l: LuaError) {
                throw l
            } catch (e: Exception) {
                throw LuaError("load " + name + ": " + e, e)
            }
        }
    }

    /**
     * Seeds this state's `math.random`, as `math.randomseed(x, y)` would.
     *
     * A fresh state seeds itself from the host's own generator, which is
     * enough for two lanes never to start on the same sequence but leaves the
     * host no way to repeat a run. Seeding it here fixes the sequence: the
     * same two numbers give the same draws, here and from the reference
     * interpreter, which is what makes a replay of a recorded session or a
     * seeded test come out the same twice.
     *
     * ```kotlin
     * val globals = LuaPlatform.standardGlobals()
     * globals.seedrandom(lane.toLong(), run.toLong())
     * ```
     *
     * A script can still call `math.randomseed` and seed over this; a host
     * that means the sequence to stay fixed keeps that function away from it,
     * the way it keeps away anything else it does not want reached.
     *
     * @param x the first half of the seed
     * @param y the second half, as `math.randomseed`'s optional argument
     * @throws LuaError if the `math` library is not loaded into this state
     */
    fun seedrandom(x: Long, y: Long = 0L) {
        val math: MathLib = mathlib ?: throw LuaError("math library is not loaded")
        math.seed(x, y)
    }

    /**
     * Objects the host has reclaimed whose `__gc` handler has still to run.
     *
     * Filled by the host, off whatever thread it reclaims on, and emptied here
     * where Lua code can safely be run - which is what [runfinalizers] does.
     */
    internal val finalized: MutableList<LuaValue> = ArrayList()

    /** True once anything at all has been marked for finalization. */
    internal var marksfinalizers: Boolean = false

    /** True while a finalizer runs, so that one cannot set off another. */
    private var finalizing: Boolean = false

    /**
     * Marks [target] to have its `__gc` handler run once it is unreachable.
     *
     * As in Lua this happens when the metatable is set, and only then: a
     * `__gc` added to a metatable that is already in use has no effect on
     * objects that were given it earlier.
     */
    internal fun markforfinalization(target: LuaValue) {
        if (target.gckeeper != null) return
        val keeper: Any? = watchForFinalization(target, finalized)
        if (keeper == null) return // a host that cannot finalize at all
        target.gckeeper = keeper
        marksfinalizers = true
    }

    /**
     * Runs the `__gc` handler of everything the host has reclaimed.
     *
     * Called where the interpreter allocates, which is where Lua runs a step
     * of its own collector, and again whenever `collectgarbage` is asked to
     * collect. A handler that raises is reported as a warning and does not
     * disturb what was running, which is what Lua does with one.
     */
    internal fun runfinalizers() {
        if (!marksfinalizers || finalizing) return
        var due: List<LuaValue> = takeFinalized(finalized)
        if (due.isEmpty()) {
            // Nothing reclaimed yet. Where enough has been allocated that Lua
            // would have run a cycle of its own by now, the host is asked for
            // one: a program waiting for a finalizer to run has nothing else
            // to wait for, and the host collects when it sees fit rather than
            // when Lua would.
            if (memory.sincecollect < Memory.COLLECT_EVERY) return
            memory.collected()
            platformCollectGarbage()
            due = takeFinalized(finalized)
            if (due.isEmpty()) return
        }
        finalizing = true
        try {
            for (target in due) {
                val handler: LuaValue = target.metatag(LuaValue.GC)
                if (handler.isnil()) continue
                val state: LuaThread.State = running.state
                state.finalizerframepending = true
                try {
                    handler.call(target)
                } catch (failure: LuaError) {
                    baselib?.warning("error in __gc metamethod (" + failure.message + ")")
                } finally {
                    state.finalizerframepending = false
                }
            }
        } finally {
            finalizing = false
        }
    }

    /** Interface for module that converts a Prototype into a LuaFunction with an environment.  */
    interface Loader {
        /** Convert the prototype into a LuaFunction with the supplied environment.  */
        @kotlin.Throws(IOException::class)
        fun load(prototype: Prototype?, chunkname: String?, env: LuaValue?): LuaFunction?
    }

    /** Interface for module that converts lua source text into a prototype.  */
    interface Compiler {
        /** Compile lua source into a Prototype. The InputStream is assumed to be in UTF-8.  */
        @kotlin.Throws(IOException::class)
        fun compile(stream: InputStream?, chunkname: String?): Prototype?
    }

    /** Interface for module that loads lua binary chunk into a prototype.  */
    interface Undumper {
        /** Load the supplied input stream into a prototype.  */
        @kotlin.Throws(IOException::class)
        fun undump(stream: InputStream?, chunkname: String?): Prototype?
    }

    /** Check that this object is a Globals object, and return it, otherwise throw an error.  */
    override fun checkglobals(): Globals {
        return this
    }

    /** The installed loader.
     * @see Loader
     */
    var loader: Loader? = null

    /** The installed compiler.
     * @see Compiler
     */
    var compiler: Compiler? = null

    /** The installed undumper.
     * @see Undumper
     */
    var undumper: Undumper? = null

    /** Convenience function for loading a file that is either binary lua or lua source.
     * @param filename Name of the file to load.
     * @return LuaValue that can be call()'ed or invoke()'ed.
     * @throws LuaError if the file could not be loaded.
     */
    fun loadfile(filename: String?): LuaValue? {
        try {
            val stream = finder?.findResource(filename) ?: throw LuaError("load $filename: no resource")
            return load(stream, "@" + filename, "bt", this)
        } catch (l: LuaError) {
            // Already says what is wrong in Lua's own words - where in the
            // file, and what about it - so nothing is added to it here.
            throw l
        } catch (e: Exception) {
            return error("load " + filename + ": " + e)
        }
    }

    /** Convenience function to load a string value as a script.  Must be lua source.
     * @param script Contents of a lua script, such as "print 'hello, world.'"
     * @param chunkname Name that will be used within the chunk as the source.
     * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
     * @throws LuaError if the script could not be compiled.
     */
    fun load(script: String, chunkname: String?): LuaValue? {
        return load(net.blueva.luak.Globals.StrReader(script), chunkname)
    }

    /** Convenience function to load a string value as a script.  Must be lua source.
     * @param script Contents of a lua script, such as "print 'hello, world.'"
     * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
     * @throws LuaError if the script could not be compiled.
     */
    fun load(script: String): LuaValue? {
        return load(net.blueva.luak.Globals.StrReader(script), script)
    }

    /** Convenience function to load a string value as a script with a custom environment.
     * Must be lua source.
     * @param script Contents of a lua script, such as "print 'hello, world.'"
     * @param chunkname Name that will be used within the chunk as the source.
     * @param environment LuaTable to be used as the environment for the loaded function.
     * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
     * @throws LuaError if the script could not be compiled.
     */
    fun load(script: String, chunkname: String?, environment: LuaTable?): LuaValue? {
        return load(net.blueva.luak.Globals.StrReader(script), chunkname, environment)
    }

    /** Load the content form a reader as a text file.  Must be lua source.
     * The source is converted to UTF-8, so any characters appearing in quoted literals
     * above the range 128 will be converted into multiple bytes.
     * @param reader Reader containing text of a lua script, such as "print 'hello, world.'"
     * @param chunkname Name that will be used within the chunk as the source.
     * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
     * @throws LuaError if the script could not be compiled.
     */
    fun load(reader: Reader, chunkname: String?): LuaValue? {
        return load(net.blueva.luak.Globals.UTF8Stream(reader), chunkname, "t", this)
    }

    /** Load the content form a reader as a text file, supplying a custom environment.
     * Must be lua source. The source is converted to UTF-8, so any characters
     * appearing in quoted literals above the range 128 will be converted into
     * multiple bytes.
     * @param reader Reader containing text of a lua script, such as "print 'hello, world.'"
     * @param chunkname Name that will be used within the chunk as the source.
     * @param environment LuaTable to be used as the environment for the loaded function.
     * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
     * @throws LuaError if the script could not be compiled.
     */
    fun load(reader: Reader, chunkname: String?, environment: LuaTable?): LuaValue? {
        return load(net.blueva.luak.Globals.UTF8Stream(reader), chunkname, "t", environment)
    }

    /** Load the content form an input stream as a binary chunk or text file.
     * @param is InputStream containing a lua script or compiled lua"
     * @param chunkname Name that will be used within the chunk as the source.
     * @param mode String containing 'b' or 't' or both to control loading as binary or text or either.
     * @param environment LuaTable to be used as the environment for the loaded function.
     */
    fun load(`is`: InputStream, chunkname: String?, mode: String, environment: LuaValue?): LuaValue? {
        try {
            val p: Prototype? = loadPrototype(`is`, chunkname, mode)
            val loaded: LuaValue? = loader!!.load(p, chunkname, environment)
            // A chunk given an environment of its own still runs in this
            // state: what it reads its globals from and what it runs in are
            // two different things.
            if (loaded is LuaClosure && loaded.globals == null) loaded.globals = this
            return loaded
        } catch (l: LuaError) {
            throw l
        } catch (e: Exception) {
            throw LuaError("load " + chunkname + ": " + e, e)
        }
    }

    /** Load lua source or lua binary from an input stream into a Prototype.
     * The InputStream is either a binary lua chunk starting with the lua binary chunk signature,
     * or a text input file.  If it is a text input file, it is interpreted as a UTF-8 byte sequence.
     * @param is Input stream containing a lua script or compiled lua"
     * @param chunkname Name that will be used within the chunk as the source.
     * @param mode String containing 'b' or 't' or both to control loading as binary or text or either.
     */
    @kotlin.Throws(IOException::class)
    fun loadPrototype(`is`: InputStream, chunkname: String?, mode: String): Prototype? {
        var `is`: InputStream = `is`
        // A state that refuses binary chunks refuses them however the mode was
        // written, and reports the mode it went by rather than the one it was
        // handed; see textonly.
        val mode: String = if (textonly) mode.replace("b", "") else mode
        if (!`is`.markSupported()) `is` = net.blueva.luak.Globals.BufferedStream(`is`)
        `is`.mark(4)
        val first: Int = `is`.read()
        `is`.reset()
        // The signature byte says which kind of chunk is really there, so the
        // mode is checked against that rather than against what the caller
        // hoped for: asking for text and handing over a dump is refused, not
        // parsed as source.
        if (first == LoadState.LUA_SIGNATURE[0].toInt()) {
            if (mode.indexOf('b') < 0) {
                error("attempt to load a binary chunk (mode is '" + mode + "')")
            }
            if (undumper == null) error("No undumper.")
            return undumper!!.undump(`is`, chunkname)
        }
        if (mode.indexOf('t') < 0) {
            error("attempt to load a text chunk (mode is '" + mode + "')")
        }
        return compilePrototype(`is`, chunkname)
    }

    /** Compile lua source from a Reader into a Prototype. The characters in the reader
     * are converted to bytes using the UTF-8 encoding, so a string literal containing
     * characters with codepoints 128 or above will be converted into multiple bytes.
     */
    @kotlin.Throws(IOException::class)
    fun compilePrototype(reader: Reader, chunkname: String?): Prototype? {
        return compilePrototype(net.blueva.luak.Globals.UTF8Stream(reader), chunkname)
    }

    /** Compile lua source from an InputStream into a Prototype.
     * The input is assumed to be UTf-8, but since bytes in the range 128-255 are passed along as
     * literal bytes, any ASCII-compatible encoding such as ISO 8859-1 may also be used.
     */
    @kotlin.Throws(IOException::class)
    fun compilePrototype(stream: InputStream?, chunkname: String?): Prototype? {
        if (compiler == null) error("No compiler.")
        return compiler!!.compile(stream, chunkname)
    }

    /** Function which yields the current thread.
     *
     * Only usable from within the interpreter's suspend-aware call chain (see
     * [LuaValue.callSuspend]); calling this directly from regular, non-suspend
     * Kotlin code has nowhere to suspend to and always fails, matching real
     * Lua's C-call boundary restriction. Use [yieldSuspend] instead when
     * writing a library function that should support being yielded through.
     * @param args  Arguments to supply as return values in the resume function of the resuming thread.
     * @return Values supplied as arguments to the resume() call that reactivates this thread.
     */
    fun yield(args: Varargs?): Varargs {
        if (running.isMainThread) throw LuaError("attempt to yield from outside a coroutine")
        return runLuaSync { yieldSuspend(args) }
    }

    /** Suspending counterpart to [yield]; see its doc for details. */
    suspend fun yieldSuspend(args: Varargs?): Varargs {
        if (running.isMainThread) throw LuaError("attempt to yield from outside a coroutine")
        val s: LuaThread.State = running.state
        return s.lua_yield(args)
    }

    /** Reader implementation to read chars from a String in JME or JVM.  */
    internal class StrReader(val s: String) : Reader() {
        var i: Int = 0
        val n: Int

        init {
            n = s.length
        }

        @kotlin.Throws(IOException::class)
        override fun close() {
            i = n
        }

        @kotlin.Throws(IOException::class)
        override fun read(): Int {
            return if (i < n) s[i++].code else -1
        }

        @kotlin.Throws(IOException::class)
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            var j = 0
            while (j < len && i < n) {
                cbuf[off + j] = s[i]
                ++j
                ++i
            }
            return if (j > 0 || len == 0) j else -1
        }
    }

    /* Abstract base class to provide basic buffered input storage and delivery.
	 * This class may be moved to its own package in the future.
	 */
    abstract class AbstractBufferedStream protected constructor(buflen: Int) : InputStream() {
        protected var b: ByteArray
        protected var i: Int = 0
        protected var j: Int = 0

        init {
            this.b = ByteArray(buflen)
        }

        @kotlin.Throws(IOException::class)
        protected abstract fun avail(): Int

        @kotlin.Throws(IOException::class)
        override fun read(): Int {
            val a = avail()
            return (if (a <= 0) -1 else 0xff and b[i++].toInt())
        }

        @kotlin.Throws(IOException::class)
        override fun read(b: ByteArray): Int {
            return read(b, 0, b.size)
        }

        @kotlin.Throws(IOException::class)
        override fun read(b: ByteArray, i0: Int, n: Int): Int {
            val a = avail()
            if (a <= 0) return -1
            val n_read: Int = minOf(a, n)
            arrayCopy(this.b, i, b, i0, n_read)
            i += n_read
            return n_read
        }

        @kotlin.Throws(IOException::class)
        override fun skip(n: Long): Long {
            val k: Long = minOf(n, (j - i).toLong())
            i += k.toInt()
            return k
        }

        @kotlin.Throws(IOException::class)
        override fun available(): Int {
            return j - i
        }
    }

    /**  Simple converter from Reader to InputStream using UTF8 encoding that will work
     * on both JME and JVM.
     * This class may be moved to its own package in the future.
     */
    internal class UTF8Stream(r: Reader) : AbstractBufferedStream(96) {
        private val c = CharArray(32)
        private val r: Reader

        init {
            this.r = r
        }

        @kotlin.Throws(IOException::class)
        override fun avail(): Int {
            if (i < j) return j - i
            var n: Int = r.read(c, 0, c.size)
            if (n < 0) return -1
            if (n == 0) {
                val u: Int = r.read()
                if (u < 0) return -1
                c[0] = u.toChar()
                n = 1
            }
            j = LuaString.encodeToUtf8(c, n, b, 0.also { i = it })
            return j
        }

        @kotlin.Throws(IOException::class)
        override fun close() {
            r.close()
        }
    }

    /** Simple buffered InputStream that supports mark.
     * Used to examine an InputStream for a 4-byte binary lua signature,
     * and fall back to text input when the signature is not found,
     * as well as speed up normal compilation and reading of lua scripts.
     * This class may be moved to its own package in the future.
     */
    class BufferedStream(buflen: Int, s: InputStream) : AbstractBufferedStream(buflen) {
        private val s: InputStream

        constructor(s: InputStream) : this(128, s)

        init {
            this.s = s
        }

        @kotlin.Throws(IOException::class)
        override fun avail(): Int {
            if (i < j) return j - i
            if (j >= b.size) {
                j = 0
                i = j
            }
            // leave previous bytes in place to implement mark()/reset().
            var n: Int = s.read(b, j, b.size - j)
            if (n < 0) return -1
            if (n == 0) {
                val u: Int = s.read()
                if (u < 0) return -1
                b[j] = u.toByte()
                n = 1
            }
            j += n
            return n
        }

        @kotlin.Throws(IOException::class)
        override fun close() {
            s.close()
        }

                override fun mark(n: Int) {
            if (i > 0 || n > b.size) {
                val dest = if (n > b.size) ByteArray(n) else b
                arrayCopy(b, i, dest, 0, j - i)
                j -= i
                i = 0
                b = dest
            }
        }

        override fun markSupported(): Boolean {
            return true
        }

                @kotlin.Throws(IOException::class)
        override fun reset() {
            i = 0
        }
    }
}
