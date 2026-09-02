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


import net.blueva.luak.WeakReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

/**
 * Subclass of [LuaValue] that implements a lua coroutine thread.
 *
 *
 * A LuaThread is typically created in response to a scripted call to
 * `coroutine.create()`
 *
 *
 * The threads must be initialized with the globals, so that
 * the global environment may be passed along according to rules of lua.
 * This is done via the constructor arguments [.LuaThread] or
 * [.LuaThread].
 *
 *
 * The utility classes [net.blueva.luak.lib.LuaPlatform] and
 * [net.blueva.luak.lib.jvm.JvmPlatform]
 * see to it that this [Globals] are initialized properly.
 *
 *
 * Resume/yield are implemented with Kotlin's own `suspend`/[kotlin.coroutines.Continuation]
 * machinery rather than a native thread or worker per coroutine, so this
 * works identically - and without blocking anything - on every KMP target,
 * including JS and Wasm where there is no thread to block. `yield()` only
 * suspends when called from Lua-to-Lua calls or from a function explicitly
 * written to propagate suspension (currently: `coroutine.yield` itself and
 * `pcall`/`xpcall`, matching real Lua 5.2's yieldable pcall). Calling it from
 * inside any other library function's own callback (e.g. `table.sort`'s
 * comparator) correctly raises "attempt to yield across metamethod/C-call
 * boundary", matching real Lua's C-call boundary restriction - unlike the
 * old Java-Threads-based implementation, which could yield from anywhere at
 * the cost of not being portable to JS/Wasm at all.
 *
 *
 * A suspended coroutine holds no more than a captured [kotlin.coroutines.Continuation]
 * and whatever it closed over; abandoning it (dropping all references without
 * ever resuming it again) is just ordinary garbage, collected normally,
 * with no orphan-thread bookkeeping required.
 *
 *
 * @see LuaValue
 *
 * @see net.blueva.luak.lib.jvm.JvmPlatform
 *
 * @see net.blueva.luak.lib.LuaPlatform
 *
 * @see net.blueva.luak.lib.CoroutineLib
 */
/**
 * Counts a call that recurses on the host stack, refusing it past the ceiling.
 *
 * The interpreter runs on the host's own stack, so a Lua program that calls
 * out and back in without bound would exhaust it. Lua counts those calls
 * instead and stops at a ceiling of its own, keeping a little room above it
 * for whatever handles the failure; running out of that room too is a failure
 * of the handling rather than of the call.
 */
internal fun enterForeignCall(state: LuaThread.State) {
    if (++state.foreigncalls < LuaThread.State.MAX_FOREIGN_CALLS) return
    if (state.foreigncalls == LuaThread.State.MAX_FOREIGN_CALLS) {
        LuaValue.error("C stack overflow")
    }
    if (state.foreigncalls >= LuaThread.State.MAX_HANDLER_CALLS) {
        LuaValue.error("error in error handling")
    }
}

class LuaThread : LuaValue {
    /** See [LuaValue.pinned]; a coroutine can be a weak key. */
    internal override var pinned: Any? = null

    val state: State

    /** Thread-local used by DebugLib to store debugging state.
     * This is an opaque value that should not be modified by applications.  */
    var callstack: Any? = null

    val globals: Globals?

    /** Error message handler for this thread, if any.   */
    var errorfunc: LuaValue? = null

    /** Private constructor for main thread only  */
    constructor(globals: Globals) {
        state = net.blueva.luak.LuaThread.State(globals, this, null)
        state.status = net.blueva.luak.LuaThread.Companion.STATUS_RUNNING
        this.globals = globals
    }

    /**
     * Create a LuaThread around a function and environment
     * @param func The function to execute
     */
    constructor(globals: Globals, func: LuaValue?) {
        LuaValue.assert_(func != null, "function cannot be null")
        state = net.blueva.luak.LuaThread.State(globals, this, func)
        this.globals = globals
    }

    override fun type(): Int {
        return LuaValue.TTHREAD
    }

    override fun typename(): String? {
        return "thread"
    }

    override fun isthread(): Boolean {
        return true
    }

    override fun optthread(defval: LuaThread?): LuaThread {
        return this
    }

    override fun checkthread(): LuaThread {
        return this
    }

    override fun getmetatable(): LuaValue? {
        return net.blueva.luak.LuaThread.Companion.s_metatable
    }

    val status: String?
        get() = net.blueva.luak.LuaThread.Companion.STATUS_NAMES[state.status]

    val isMainThread: Boolean
        get() = this.state.function == null

    fun resume(args: Varargs?): Varargs {
        val s = this.state
        if (s.status > net.blueva.luak.LuaThread.Companion.STATUS_SUSPENDED) return LuaValue.varargsOf(
            LuaValue.FALSE,
            LuaValue.valueOf("cannot resume " + (if (s.status == net.blueva.luak.LuaThread.Companion.STATUS_DEAD) "dead" else "non-suspended") + " coroutine")
        )
        return s.lua_resume(this, args)
    }

    /**
     * `coroutine.close`: ends this coroutine, running its pending closers.
     *
     * A suspended coroutine may be holding to-be-closed variables partway
     * through its body. Closing it unwinds from the point it yielded at, which
     * is what runs their `__close` handlers; an error raised by one of those is
     * reported rather than thrown.
     *
     * @return `true`, or `false` plus the error a closer raised
     */
    fun close(): Varargs {
        // Raised rather than reported: only a suspended or dead coroutine can
        // be closed, so anything else is a mistake in the call itself. The
        // status is looked at before the thread's identity, since the main
        // thread is "normal" while whatever it resumed is running.
        val s = this.state
        if (s.status == net.blueva.luak.LuaThread.Companion.STATUS_NORMAL) {
            LuaValue.error("cannot close a normal coroutine")
        }
        if (s.status == net.blueva.luak.LuaThread.Companion.STATUS_RUNNING) {
            if (this.isMainThread) LuaValue.error("cannot close main thread")
            // A coroutine closing itself is ended on the spot: this never
            // returns, so nothing written after the call runs. Its pending
            // to-be-closed variables are handled on the way out, by the
            // `finally` blocks the unwinding passes through.
            throw ClosedCoroutine()
        }
        return s.lua_close(this)
    }

    class State internal constructor(globals: Globals, lua_thread: LuaThread, function: LuaValue?) {
        private val globals: Globals
        val lua_thread: WeakReference<LuaThread>
        val function: LuaValue?
        var args: Varargs? = LuaValue.NONE
        var result: Varargs? = LuaValue.NONE
        var error: String? = null

        /** Hook function control state used by debug lib.  */
        var hookfunc: LuaValue? = null

        var hookline: Boolean = false
        var hookcall: Boolean = false
        var hookrtrn: Boolean = false
        var hookcount: Int = 0
        var inhook: Boolean = false

        /** True while a hook has been entered but its frame is not on yet. */
        var hookframepending: Boolean = false

        /**
         * How many frames a host stack overflow has unwound so far.
         *
         * The interpreter runs on the host's own stack, so an overflow is
         * noticed with no room left to report it in. Counting the frames it
         * unwinds through lets it be turned into an ordinary Lua error a
         * little way back from the edge, where there is room again for a
         * message handler to run.
         */
        var unwinding: Int = 0

        /**
         * How many message handlers are running on this thread.
         *
         * A reference build gives a handler a stack of its own to work in,
         * past the one the program ran out of; running out again in there is
         * a failure of the handling rather than another overflow.
         */
        var inhandler: Int = 0

        /**
         * Set while a `__gc` handler is being called, so the frame it pushes
         * can be marked as a finalizer's; see [DebugLib.CallFrame.finalizer].
         */
        var finalizerframepending: Boolean = false

        /** The `__call` chain length the next frame pushed should report. */
        var pendingextraargs: Int = 0

        /** True when the next frame pushed is one a tail call is making. */
        var pendingtailcall: Boolean = false

        /**
         * How many calls that are not Lua-to-Lua are in progress.
         *
         * A Lua function calling another loops inside the interpreter, but a
         * call that goes out to the library and back in recurses on the host
         * stack. Lua counts exactly those and refuses to go deeper than
         * [MAX_FOREIGN_CALLS], which is what keeps a runaway
         * `pcall`-through-`pcall` from having to exhaust the whole stack
         * before it is stopped.
         */
        var foreigncalls: Int = 0


        companion object {
            /** As many nested calls out of Lua as Lua allows, `LUAI_MAXCCALLS`. */
            const val MAX_FOREIGN_CALLS: Int = 200

            /**
             * Past this, handling an error is itself given up on.
             *
             * Lua leaves a tenth of the allowance above the ordinary ceiling
             * so that a message handler still has room to run; a handler that
             * keeps failing eats through it, and then there is nothing left to
             * report but the failure of the handling itself.
             */
            const val MAX_HANDLER_CALLS: Int = MAX_FOREIGN_CALLS * 11 / 10

        }

        /**
         * How many library calls into Lua code are in progress on this thread.
         *
         * While any of them is, there is nowhere for a yield to suspend to and
         * the coroutine reports itself as not yieldable.
         */
        var noyield: Int = 0
        var lastline: Int = 0
        var bytecodes: Int = 0

        var status: Int = net.blueva.luak.LuaThread.Companion.STATUS_INITIAL

        /** Continuation captured at this coroutine's most recent yield point, or
         * null if it has never yielded (not yet started, or already resumed
         * back to running). Resuming it continues Lua execution from exactly
         * where `coroutine.yield()` left off, with the resume() arguments
         * becoming yield()'s return values. */
        private var yieldContinuation: Continuation<Varargs>? = null

        /** Values passed to the most recent `coroutine.yield(...)` call, read by
         * lua_resume() once the resumed execution pauses there. */
        private var pendingYieldValues: Varargs? = null

        /** Set true exactly when the coroutine body has truly finished (returned
         * or thrown), as opposed to merely yielding. */
        private var finished = false
        private var finalResult: Result<Varargs>? = null

        private val completion = object : Continuation<Varargs> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Varargs>) {
                finished = true
                finalResult = result
                status = net.blueva.luak.LuaThread.Companion.STATUS_DEAD
            }
        }

        init {
            this.globals = globals
            this.lua_thread = WeakReference(lua_thread)
            this.function = function
        }

        fun lua_resume(new_thread: LuaThread, args: Varargs?): Varargs {
            val previous_thread: LuaThread = globals.running
            // A resumed coroutine runs on the host stack the resuming one is
            // standing on, so it carries on counting from there; see
            // enterForeignCall.
            val outer: Int = previous_thread.state.foreigncalls
            foreigncalls = outer
            try {
                globals.running = new_thread
                // Mark the resuming thread NORMAL before running the resumed
                // thread, not after: the resumed thread may make its own
                // coroutine.status() calls on the resumer before yielding back.
                previous_thread.state.status = net.blueva.luak.LuaThread.Companion.STATUS_NORMAL
                status = net.blueva.luak.LuaThread.Companion.STATUS_RUNNING
                finished = false
                pendingYieldValues = null
                val contToResume = yieldContinuation
                yieldContinuation = null
                if (contToResume == null) {
                    val a: Varargs = args ?: LuaValue.NONE!!
                    val body: suspend () -> Varargs = { function!!.invokeSuspend(a) }
                    body.startCoroutine(completion)
                } else {
                    contToResume.resume(args ?: LuaValue.NONE!!)
                }
                return if (finished) {
                    val r = finalResult!!
                    val err = r.exceptionOrNull()
                    if (err != null) {
                        // The coroutine died: what it failed with is kept, so a
                        // later coroutine.close can report it once.
                        // A coroutine that closed itself did not fail: it
                        // ended, and a resume sees an ordinary empty return.
                        if (err is ClosedCoroutine) LuaValue.TRUE!!
                        else {
                            deadError = err
                            // The stack it died on outlives the frames
                            // themselves, so a traceback can still show it.
                            new_thread.callstack?.let { stack ->
                                (stack as net.blueva.luak.lib.DebugLib.CallStack).frozen =
                                    (err as? LuaError)?.luastack
                            }
                            LuaValue.varargsOf(LuaValue.FALSE, errorObject(err))!!
                        }
                    }
                    else LuaValue.varargsOf(LuaValue.TRUE, r.getOrThrow())!!
                } else {
                    status = net.blueva.luak.LuaThread.Companion.STATUS_SUSPENDED
                    LuaValue.varargsOf(LuaValue.TRUE, pendingYieldValues ?: LuaValue.NONE!!)!!
                }
            } finally {
                finalResult = null
                pendingYieldValues = null
                globals.running = previous_thread
                globals.running.state.status = net.blueva.luak.LuaThread.Companion.STATUS_RUNNING
                previous_thread.state.foreigncalls = outer
            }
        }

        /**
         * What a coroutine died of, until a `coroutine.close` reports it.
         *
         * Lua hands the error back once more when the dead coroutine is
         * closed, and answers plainly the next time it is asked.
         */
        private var deadError: Throwable? = null

        /**
         * The value a failure should be reported as.
         *
         * A Lua error carries its own object, which may be any value; anything
         * else can only be described by its text.
         */
        private fun errorObject(err: Throwable): LuaValue {
            if (err is LuaError) {
                val message: LuaValue? = err.messageObject
                if (message != null) return message
            }
            // Asked first: running out of stack can surface with a message of
            // the host's own, which says nothing useful to a Lua program.
            if (platformIsStackOverflow(err)) return LuaValue.valueOf("C stack overflow")!!
            return LuaValue.valueOf(err.message ?: err.toString())!!
        }

        /** Unwinds a suspended coroutine so its pending closers run. */
        fun lua_close(closing: LuaThread): Varargs {
            // Closing runs the coroutine's pending handlers, which recurse on
            // the host stack the way any other call out of Lua does: a chain
            // of coroutines each closing the one before it is what a ceiling
            // on that is for. The tally goes back where it was afterwards,
            // since a close reports what went wrong rather than raising it.
            // Counted against the thread that asked, not the one being
            // closed: the handlers run on the host stack the asking thread is
            // already standing on, and the thread being closed carries on
            // counting from there.
            val caller: State = globals.running.state
            val outer: Int = caller.foreigncalls
            try {
                enterForeignCall(caller)
            } catch (deep: LuaError) {
                status = net.blueva.luak.LuaThread.Companion.STATUS_DEAD
                return LuaValue.varargsOf(LuaValue.FALSE, errorObject(deep))!!
            }
            foreigncalls = caller.foreigncalls
            try {
                return closing(closing)
            } finally {
                caller.foreigncalls = outer
            }
        }

        /** What [lua_close] does once the call has been counted. */
        private fun closing(closing: LuaThread): Varargs {
            val continuation = yieldContinuation
            yieldContinuation = null
            if (continuation == null) {
                // Never started, or already finished: nothing is on its stack.
                status = net.blueva.luak.LuaThread.Companion.STATUS_DEAD
                // A coroutine that died of an error reports it once more here,
                // and nothing on any close after that.
                val died: Throwable? = deadError
                deadError = null
                if (died != null) {
                    return LuaValue.varargsOf(LuaValue.FALSE, errorObject(died))!!
                }
                return LuaValue.TRUE!!
            }
            val previous_thread: LuaThread = globals.running
            try {
                globals.running = closing
                previous_thread.state.status = net.blueva.luak.LuaThread.Companion.STATUS_NORMAL
                status = net.blueva.luak.LuaThread.Companion.STATUS_RUNNING
                finished = false
                finalResult = null
                // An Error rather than an Exception, so the interpreter's
                // catch-all leaves it alone and only the finally blocks - the
                // ones that close variables - run on the way out.
                continuation.resumeWithException(ClosedCoroutine())
            } finally {
                status = net.blueva.luak.LuaThread.Companion.STATUS_DEAD
                globals.running = previous_thread
                globals.running.state.status = net.blueva.luak.LuaThread.Companion.STATUS_RUNNING
            }
            val result = finalResult
            finalResult = null
            val failure: Throwable? = result?.exceptionOrNull()
            if (failure == null || failure is ClosedCoroutine) return LuaValue.TRUE!!
            return LuaValue.varargsOf(LuaValue.FALSE, errorObject(failure))!!
        }

        suspend fun lua_yield(args: Varargs?): Varargs {
            status = net.blueva.luak.LuaThread.Companion.STATUS_SUSPENDED
            pendingYieldValues = args ?: LuaValue.NONE
            if (this.lua_thread.get() == null) throw OrphanedThread()
            return suspendCoroutine { cont -> yieldContinuation = cont }
        }
    }

    /** Thrown into a suspended coroutine by [close] to unwind it. */
    internal class ClosedCoroutine : Error("coroutine closed")

    companion object {
        /** Shared metatable for lua threads.  */
        var s_metatable: LuaValue? = null

        /** The current number of coroutines.  Should not be set.  */
        var coroutine_count: Int = 0

        /** Unused: kept only for source/binary compatibility with code written
         * against the old Java-Threads-based coroutine implementation. Since
         * resume/yield are now backed by suspend/Continuation rather than a
         * blocked thread per coroutine, there is nothing left to poll - an
         * abandoned suspended coroutine is just unreachable memory, collected
         * the same way any other object is.
         */
        var thread_orphan_check_interval: Long = 5000

        const val STATUS_INITIAL: Int = 0
        const val STATUS_SUSPENDED: Int = 1
        const val STATUS_RUNNING: Int = 2
        const val STATUS_NORMAL: Int = 3
        const val STATUS_DEAD: Int = 4
        val STATUS_NAMES: Array<String?> = arrayOf<String?>(
            "suspended",
            "suspended",
            "running",
            "normal",
            "dead",
        )

        const val MAX_CALLSTACK: Int = 256
    }
}
