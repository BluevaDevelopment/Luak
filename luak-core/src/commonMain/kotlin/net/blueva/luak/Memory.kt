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
 *  SPDX-License-Identifier: MIT
 ******************************************************************************/
package net.blueva.luak

/**
 * What one state's Lua objects cost, and the most it is allowed to cost.
 *
 * The host's own collector is the one that reclaims memory here, and what it
 * reports - a heap shared with everything else the host is doing - says
 * nothing about how much of it is Lua's. So Lua's own objects are counted as
 * they are made, the way a reference build counts what it allocates, and the
 * tally goes back to nothing when a collection finishes: what is left after
 * one is not known object by object, and a program that watches this number
 * is watching it grow with what it allocates and drop when that is reclaimed.
 *
 * The sizes are the ones a reference build would use, so a program that works
 * out how much a table of a given shape costs gets the answer it expects.
 *
 * There is one of these per [Globals], reached as [Globals.memory]. Two lanes
 * running untrusted code in the same process each count their own objects,
 * and one filling a table cannot make the other's `collectgarbage("count")`
 * jump or its ceiling arrive early. Which one is counting is [current]: the
 * objects a state makes are charged to it while its code runs.
 */
internal class Memory {
    /** Bytes of Lua's own objects made since the last collection. */
    var accounted: Long = 0
        private set

    /**
     * Bytes made since the host was last asked to collect.
     *
     * Unlike [accounted] this is not reset by a cycle going by on its own:
     * it says how much has been allocated since anything was actually
     * reclaimed, which is what decides when a program waiting on a finalizer
     * is worth interrupting for.
     */
    var sincecollect: Long = 0
        private set

    /** False while `collectgarbage("stop")` is in force. */
    var running: Boolean = true

    /**
     * The most this state may be charged for, or 0 for no ceiling.
     *
     * Set by the host through [Globals.memoryceiling]; see it for what the
     * figure covers.
     */
    var ceiling: Long = 0

    /**
     * Bytes charged to this state since [startcounting].
     *
     * Not the same tally as [accounted], and deliberately out of Lua's reach:
     * `collectgarbage` resets that one, and a ceiling a script can clear by
     * calling a standard-library function is not a ceiling.
     */
    var charged: Long = 0
        private set

    /** Notes [bytes] just allocated, collecting if that is now overdue. */
    fun account(bytes: Long) {
        accounted += bytes
        sincecollect += bytes
        charged += bytes
        if (ceiling > 0 && charged > ceiling) {
            // Lua's own words for running out, so a script that expects to be
            // able to catch this reads what it would read anywhere else.
            throw LuaError("not enough memory")
        }
        // The host reclaims on its own; what happens here is only that the
        // tally starts again, which is what a finished cycle looks like from
        // a program watching the count.
        if (running && accounted > THRESHOLD) accounted = 0
    }

    /**
     * Takes back [bytes] just counted, for something that is not an object.
     *
     * A reference build keeps a named vararg parameter on the stack rather
     * than in an object of its own, so what stands in for it here is not
     * something a program should see the cost of.
     */
    fun uncount(bytes: Long) {
        accounted -= bytes
        if (accounted < 0) accounted = 0
        charged -= bytes
        if (charged < 0) charged = 0
    }

    /** Ends a collection cycle: nothing made since the last one still counts. */
    fun collected() {
        accounted = 0
        sincecollect = 0
    }

    /** Starts the tally the ceiling is measured against again from nothing. */
    fun startcounting() {
        charged = 0
    }

    /** Bytes in use, as `collectgarbage("count")` reports them. */
    fun used(): Long = BASE + accounted

    companion object {
        /** What a table costs before any of its storage. */
        const val TABLE: Long = 56

        /** One slot of a table's array part. */
        const val SLOT: Long = 16

        /** One entry of a table's hash part. */
        const val NODE: Long = 32

        /** What a string costs beyond its own bytes. */
        const val STRING: Long = 24

        /** What a function written in Lua costs before its upvalues. */
        const val CLOSURE: Long = 32

        /** One upvalue of such a function. */
        const val UPVALUE: Long = 8

        /** What Lua holds with nothing allocated, so a count is never nothing. */
        private const val BASE: Long = 32 * 1024

        /** Where the collector would have run of its own accord. */
        private const val THRESHOLD: Long = 1024 * 1024

        /** How much may be allocated before the host is asked to collect. */
        const val COLLECT_EVERY: Long = 1024 * 1024

        /**
         * Whose objects are being counted right now.
         *
         * A [LuaTable] or a [LuaString] does not know which state made it - a
         * string is shared by every state that has it - so what charges for
         * one is whichever state is running. A state is made current for as
         * long as Lua runs in it and for as long as a library is being loaded
         * into it, and put back afterwards; objects a host makes on its own
         * are charged to [unattributed], which has no ceiling and which
         * nothing reads.
         *
         * Putting it back matters as much as setting it: a state left current
         * after its ceiling was reached would raise `not enough memory` at
         * whatever the host allocated next, including the next state it
         * built.
         */
        var current: Memory = Memory()
            private set

        /** Charged for objects made while no state is running. */
        private val unattributed: Memory = current

        /** Runs [block] with this state's objects charged to it. */
        inline fun <T> charging(memory: Memory, block: () -> T): T {
            val outer: Memory = current
            enter(memory)
            try {
                return block()
            } finally {
                enter(outer)
            }
        }

        /** Makes [memory] the one charged; only [charging] and the interpreter's
         * own boundary call this, and both put back what was there. */
        @PublishedApi
        internal fun enter(memory: Memory) {
            current = memory
        }
    }
}
