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
 *  Copyright (c) 2026 Blueva Development
 *
 *  SPDX-License-Identifier: MIT
 ******************************************************************************/
package net.blueva.luak

/**
 * How much Lua a host lets one resumption run, and how it cuts one short.
 *
 * Attach one to a [Globals] and the interpreter counts instructions against
 * it. When the count runs out, or when [interrupt] is called from wherever
 * the host watches the clock, the running chunk stops with an ordinary Lua
 * error - the kind a script sees a line number on and a traceback for, not a
 * host exception torn through the middle of the interpreter.
 *
 * This is what `debug.sethook` with a count was being used for, and it is not
 * the same bargain: the hook needs the `debug` library loaded, which is the
 * one library a sandbox does not want a script to reach, and it pays for a
 * Lua-level call every time it fires. Here nothing is loaded and nothing is
 * called: a state with no budget attached costs one null check per
 * instruction, and one with a budget costs a decrement as well.
 *
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * globals.budget = Budget().apply { instructions = 10_000_000 }
 * globals.load(untrusted, "plugin")!!.call() // stops rather than spins
 * ```
 *
 * ### The ceiling is per resumption
 *
 * [instructions] is what one handover from the host to Lua may run, not what
 * the state may run in its lifetime: entering Lua through [LuaValue.call] or
 * [LuaValue.invoke] with nothing else already running refills it. A host that
 * enters by another route - resuming a coroutine itself, or calling
 * [LuaValue.invokeSuspend] from its own coroutine - calls [refill] for
 * itself. Everything Lua does while inside spends from the one ceiling,
 * coroutines it resumes included, so a script cannot buy itself more by
 * running its loop inside one.
 *
 * ### Stopping stays stopped
 *
 * The error raised is an ordinary one, so `pcall` catches it like any other.
 * That would be a way out if the budget went back to normal afterwards, so it
 * does not: an exhausted budget stays exhausted, and the next instruction
 * after the `pcall` raises again. The chunk unwinds whatever it does, and
 * only the host - through [refill], which the next resumption does anyway -
 * puts the state back to work.
 *
 * ### Thread safety
 *
 * [interrupt] is the one member meant to be called from somewhere other than
 * the thread running Lua, and it is a plain write of two fields: a watchdog
 * calling it is asking the interpreter to stop soon, and "soon" is however
 * long the running thread takes to see the write. Nothing else here expects
 * concurrent use.
 */
class Budget {
    /**
     * Instructions one resumption may run, or 0 for no ceiling.
     *
     * Setting this refills the budget, so a host that raises the ceiling for
     * a particular call gets the whole of the new one.
     */
    var instructions: Long = 0
        set(value) {
            field = if (value > 0) value else 0
            refill()
        }

    /**
     * What is left of the ceiling.
     *
     * The interpreter counts down on this field directly - it is the whole of
     * what the hot path touches - and calls [spent] when it reaches zero.
     * With no ceiling set it starts so high that it never does.
     */
    internal var left: Long = UNLIMITED

    /** True once [interrupt] has been called and before the next [refill]. */
    private var interrupted: Boolean = false

    /** Instructions still available, or [Long.MAX_VALUE] with no ceiling set. */
    val remaining: Long
        get() = if (left > 0) left else 0

    /** True while the budget is spent, so that Lua in this state cannot run. */
    val exhausted: Boolean
        get() = left <= 0

    /**
     * Gives back the whole ceiling and clears any interrupt.
     *
     * A resumption that starts from [LuaValue.call] or [LuaValue.invoke] does
     * this by itself; a host entering Lua by another route does it here.
     */
    fun refill() {
        interrupted = false
        left = if (instructions > 0) instructions else UNLIMITED
    }

    /**
     * Stops the running chunk at its next instruction.
     *
     * Safe to call from a watchdog on another thread: see the note on thread
     * safety above. Calling it while nothing is running leaves the state
     * unable to run until the next [refill], which is what the next
     * resumption does.
     */
    fun interrupt() {
        interrupted = true
        left = 0
    }

    /**
     * Raises the error that stops the chunk, once [left] has run out.
     *
     * Kept out of the interpreter loop: this runs once per resumption at
     * most, and every byte of [LuaClosure.execute] is spoken for.
     */
    internal fun spent(): Nothing {
        // Held at zero rather than left to go further negative, so that
        // catching the error and carrying on meets it again straight away.
        left = 0
        throw LuaError(if (interrupted) "interrupted" else "instruction budget exhausted")
    }

    private companion object {
        /** What [left] starts at with no ceiling: more instructions than a run has. */
        const val UNLIMITED: Long = Long.MAX_VALUE
    }
}
