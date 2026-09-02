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

import net.blueva.luak.lib.DebugLib
import net.blueva.luak.lib.DebugLib.CallFrame
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Drives a suspend computation that is not expected to actually suspend here
 * (no active, yield-propagating coroutine chain reaches this call) to
 * completion synchronously. If it tries to suspend anyway - e.g. Lua code
 * called from a library function like `table.sort`'s comparator calls
 * `coroutine.yield()` - that correctly surfaces as a boundary error, exactly
 * like real Lua's C-call boundary restriction. */
/**
 * How many frames a host stack overflow unwinds before it is reported.
 *
 * Enough room for a message handler - `debug.traceback` above all - to run in
 * without running out of stack all over again.
 */
private const val STACK_UNWIND_HEADROOM: Int = 64

internal fun <T> runLuaSync(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    val result = outcome ?: throw LuaError("attempt to yield across metamethod/C-call boundary")
    return result.getOrThrow()
}

/**
 * Extension of [LuaFunction] which executes lua bytecode.
 * 
 * 
 * A [LuaClosure] is a combination of a [Prototype]
 * and a [LuaValue] to use as an environment for execution.
 * Normally the [LuaValue] is a [Globals] in which case the environment
 * will contain standard lua libraries.
 * 
 * 
 * 
 * There are three main ways [LuaClosure] instances are created:
 * 
 *  * Construct an instance using [.LuaClosure]
 *  * Construct it indirectly by loading a chunk via [Globals.load]
 *  * Execute the lua bytecode [Lua.OP_CLOSURE] as part of bytecode processing
 * 
 * 
 * 
 * To construct it directly, the [Prototype] is typically created via a compiler such as
 * [net.blueva.luak.compiler.LuaC]:
 * <pre> `String script = "print( 'hello, world' )"; InputStream is = new ByteArrayInputStream(script.toByteArray()); Prototype p = LuaC.instance.compile(is, "script"); LuaValue globals = JvmPlatform.standardGlobals(); LuaClosure f = new LuaClosure(p, globals); f.call(); `</pre>
 * 
 * 
 * To construct it indirectly, the [Globals.load] method may be used:
 * <pre> `Globals globals = JvmPlatform.standardGlobals(); LuaFunction f = globals.load(new StringReader(script), "script"); LuaClosure c = f.checkclosure();  // This may fail if LuaJC is installed. c.call(); `</pre>
 * 
 * 
 * In this example, the "checkclosure()" may fail if direct lua-to-java-bytecode
 * compiling using LuaJC is installed, because no LuaClosure is created in that case
 * and the value returned is a [LuaFunction] but not a [LuaClosure].
 * 
 * 
 * Since a [LuaClosure] is a [LuaFunction] which is a [LuaValue],
 * all the value operations can be used directly such as:
 * 
 *  * [LuaValue.call]
 *  * [LuaValue.call]
 *  * [LuaValue.invoke]
 *  * [LuaValue.invoke]
 *  * [LuaValue.method]
 *  * [LuaValue.method]
 *  * [LuaValue.invokemethod]
 *  * [LuaValue.invokemethod]
 *  *  ...
 * 
 * @see LuaValue
 * 
 * @see LuaFunction
 * 
 * @see LuaValue.isclosure
 * @see LuaValue.checkclosure
 * @see LuaValue.optclosure
 * @see LoadState
 * 
 * @see Globals.compiler
 */
class LuaClosure(p: Prototype, env: LuaValue?) : LuaFunction() {
    val p: Prototype

    lateinit var upValues: Array<UpValue?>

    /**
     * The state this closure belongs to, which is where the debug library,
     * the running thread and the rest of what a program needs are kept.
     *
     * Not the same thing as the table the chunk reads its globals from: a
     * chunk loaded with an environment of its own still runs in the state
     * that loaded it, which is what fills this in.
     */
    var globals: Globals? = null
        internal set

    /** Create a closure around a Prototype with a specific environment.
     * If the prototype has upvalues, the environment will be written into the first upvalue.
     * @param p the Prototype to construct this Closure for.
     * @param env the environment to associate with the closure.
     */
    init {
        this.p = p
        this.initupvalue1(env)
        globals = env as? Globals
        Memory.current.account(Memory.CLOSURE + Memory.UPVALUE * upValues.size)
    }

    /** As the two-argument form, for a chunk whose environment is its own. */
    constructor(p: Prototype, env: LuaValue?, state: Globals?) : this(p, env) {
        if (globals == null) globals = state
    }

    override fun initupvalue1(env: LuaValue?) {
        val descs: Array<Upvaldesc?>? = p.upvalues
        if (descs == null || descs.isEmpty()) {
            this.upValues = net.blueva.luak.LuaClosure.Companion.NOUPVALUES
            return
        }
        // Every slot gets storage of its own, not only the first: a closure
        // loaded from a dump has upvalues nobody has set yet, and reading one
        // before then has to answer nil rather than fall over.
        this.upValues = arrayOfNulls<UpValue>(descs.size)
        this.upValues[0] = UpValue(arrayOf<LuaValue?>(env), 0)
        for (index in 1..<descs.size) {
            this.upValues[index] = UpValue(arrayOf<LuaValue?>(NIL), 0)
        }
    }


    override fun isclosure(): Boolean {
        return true
    }

    override fun optclosure(defval: LuaClosure?): LuaClosure {
        return this
    }

    override fun checkclosure(): LuaClosure {
        return this
    }

    override fun tojstring(): String {
        return "function: " + p.toString()
    }

    private val newStack: Array<LuaValue>
        get() {
            val max: Int = p.maxstacksize
            val stack: Array<LuaValue> = Array(max) { NIL }
            return stack
        }

    // Note: execute() may return a TailcallVarargs; must resolve it via
    // evalSuspend() (not the plain, non-suspend arg1()/eval()) so a tail
    // call ending in coroutine.yield() still propagates suspension.
    private suspend fun call0(): LuaValue {
        val stack: Array<LuaValue> = this.newStack
        return (execute(stack, (NONE)!!)!!.evalSuspend().arg1())
    }

    private suspend fun call1(arg: LuaValue?): LuaValue {
        val stack: Array<LuaValue> = this.newStack
        when (p.numparams) {
            0 -> return (execute(stack, arg!!)!!.evalSuspend().arg1())
            else -> {
                stack[0] = arg!!
                return (execute(stack, (NONE)!!)!!.evalSuspend().arg1())
            }
        }
    }

    private suspend fun call2(arg1: LuaValue?, arg2: LuaValue?): LuaValue {
        val stack: Array<LuaValue> = this.newStack
        when (p.numparams) {
            1 -> {
                stack[0] = arg1!!
                return (execute(stack, arg2!!)!!.evalSuspend().arg1())
            }

            0 -> return (execute(stack, (if (p.is_vararg !== 0) varargsOf(arg1, arg2!!) else NONE)!!)!!.evalSuspend().arg1())
            else -> {
                stack[0] = arg1!!
                stack[1] = arg2!!
                return (execute(stack, (NONE)!!)!!.evalSuspend().arg1())
            }
        }
    }

    private suspend fun call3(arg1: LuaValue?, arg2: LuaValue?, arg3: LuaValue?): LuaValue {
        val stack: Array<LuaValue> = this.newStack
        when (p.numparams) {
            2 -> {
                stack[0] = arg1!!
                stack[1] = arg2!!
                return (execute(stack, arg3!!)!!.evalSuspend().arg1())
            }

            1 -> {
                stack[0] = arg1!!
                return (execute(stack, (if (p.is_vararg !== 0) varargsOf(arg2, arg3!!) else NONE)!!)!!.evalSuspend().arg1())
            }

            0 -> return (execute(stack, (if (p.is_vararg !== 0) varargsOf(arg1, arg2, arg3!!) else NONE)!!)!!.evalSuspend().arg1())
            else -> {
                stack[0] = arg1!!
                stack[1] = arg2!!
                stack[2] = arg3!!
                return (execute(stack, (NONE)!!)!!.evalSuspend().arg1())
            }
        }
    }

    private suspend fun onInvokeImpl(varargs: Varargs): Varargs? {
        val stack: Array<LuaValue> = this.newStack
        for (i in 0..<p.numparams) stack[i] = varargs.arg(i + 1)
        return execute(stack, (if (p.is_vararg !== 0) varargs.subargs(p.numparams + 1) else NONE)!!)
    }

    /**
     * Runs [block], with yielding shut off while it does.
     *
     * These are the entry points a library function reaches Lua code through,
     * and there is nowhere for a yield inside one to suspend to: it is the
     * C-call boundary, and the coroutine counts as non-yieldable while the
     * call is in progress.
     */
    private fun <T> runAcrossBoundary(block: suspend () -> T): T {
        val state: LuaThread.State = globals?.running?.state ?: return runLuaSync(block)
        // A call in from outside is a protected boundary of its own, so the
        // tally goes back to what it was however this ends.
        val outer: Int = state.foreigncalls
        // A call in with nothing already running is a resumption, which is
        // what a budget's ceiling is measured over; one made from inside Lua
        // spends the ceiling already in force. See Budget.
        if (outer == 0) globals?.budget?.refill()
        // What this call allocates belongs to the state it runs in, whichever
        // state ran before it; see Memory.current.
        val outermemory: Memory = Memory.current
        globals?.let { Memory.enter(it.memory) }
        state.noyield++
        try {
            enterforeign(state)
            val answer: T = runLuaSync(block)
            // Put back only on the way out with an answer: an error leaves the
            // tally where it was, so that whatever handles it is working in
            // the room above the ceiling rather than starting again from
            // below it. A protected call is what puts it back then.
            state.foreigncalls = outer
            return answer
        } finally {
            state.noyield--
            Memory.enter(outermemory)
        }
    }

    /**
     * Counts one call that leaves Lua, refusing to go deeper than Lua does.
     *
     * See [LuaThread.State.foreigncalls]: this is the step that costs host
     * stack, so it is the one with a ceiling on it.
     */
    private fun enterforeign(state: LuaThread.State) {
        // Counted first and left counted if it fails: the tally stays where it
        // was until a protected call puts it back, so an error raised at the
        // ceiling does not make room for the next one on its way out.
        enterForeignCall(state)
    }

    /**
     * Calls a metamethod, counting the re-entry into the interpreter.
     *
     * A call the interpreter makes for an instruction of its own loops inside
     * the loop; one made from here recurses on the host stack, which is what
     * Lua counts and puts a ceiling on.
     */
    private suspend fun callmeta(h: LuaValue, a: LuaValue, b: LuaValue): LuaValue {
        val state: LuaThread.State = globals?.running?.state ?: return h.callSuspend(a, b)!!
        enterforeign(state)
        val result: LuaValue = h.callSuspend(a, b)!!
        // Only on the way out that worked: see enterforeign.
        state.foreigncalls--
        return result
    }

    override fun call(): LuaValue = runAcrossBoundary { call0() }
    override fun call(arg: LuaValue?): LuaValue = runAcrossBoundary { call1(arg) }
    override fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue = runAcrossBoundary { call2(arg1, arg2) }
    override fun call(arg1: LuaValue?, arg2: LuaValue?, arg3: LuaValue?): LuaValue =
        runAcrossBoundary { call3(arg1, arg2, arg3) }

    override fun invoke(varargs: Varargs): Varargs = runAcrossBoundary { onInvokeImpl(varargs)!!.evalSuspend() }
    override fun onInvoke(varargs: Varargs): Varargs? = runAcrossBoundary { onInvokeImpl(varargs) }

    override suspend fun callSuspend(): LuaValue? = call0()
    override suspend fun callSuspend(arg: LuaValue?): LuaValue? = call1(arg)
    override suspend fun callSuspend(arg1: LuaValue?, arg2: LuaValue?): LuaValue? = call2(arg1, arg2)
    override suspend fun callSuspend(arg1: LuaValue?, arg2: LuaValue?, arg3: LuaValue?): LuaValue? =
        call3(arg1, arg2, arg3)

    override suspend fun invokeSuspend(args: Varargs): Varargs = onInvokeImpl(args)!!.evalSuspend()
    override suspend fun onInvokeSuspend(args: Varargs): Varargs? = onInvokeImpl(args)

    /**
     * The `OP_CALL` shapes with a fixed argument count and a fixed result count.
     *
     * These eight branches are the bulk of the opcode's bytecode - each suspending
     * call site expands into its own spill/restore block - and none of them
     * touches anything but `stack[a]`, so they can be lifted out of [execute]
     * wholesale. Returns false for the multiple-result and vararg shapes, which
     * [execute] keeps because they also update its `v` and `top`.
     */
    private suspend fun callFixedArity(
        stack: Array<LuaValue>,
        i: Int,
        a: Int,
        debuglib: DebugLib?,
    ): Boolean {
        // Everything above the arguments is free at a call, and what a
        // finished statement left in those registers would otherwise go on
        // holding an object nothing else refers to. Lua's collector reaches
        // the same conclusion by only looking at a stack up to the top of the
        // call in progress; here the registers are emptied instead.
        val b: Int = (i ushr 23) and 0x1ff
        // Nothing written for the count means the arguments run to the top of
        // what the frame is using, which is not known here.
        if (b > 0) {
            var free: Int = a + b
            while (free < stack.size) {
                stack[free] = LuaValue.NIL
                free++
            }
        }
        // A call is a place a collection can happen, which is where anything
        // waiting to be finalized gets its turn: a loop that allocates
        // without ever building a table would otherwise never let one run.
        val g: Globals? = globals
        if (g != null && g.marksfinalizers) g.runfinalizers()
        // A library function has no frame of its own to push, so the caller
        // pushes one for it: without that a traceback would not name it and
        // the call and return hooks would never fire for it.
        // Only a function of the library's own gets a frame pushed for it
        // here: a Lua closure pushes its own, and anything reached through
        // __call is not what ends up running.
        val callee: LuaValue = stack[a]
        val traced: Boolean = debuglib != null && callee is LuaFunction && callee !is LuaClosure
        if (traced) debuglib!!.onCall(callee as LuaFunction, fixedArityArgs(stack, i, a))
        try {
            val produced: LuaValue? = callFixedArityValues(stack, i, a)
            if (traced && produced != null) debuglib!!.onResults(produced)
            return produced != null
        } catch (le: LuaError) {
            // Still standing on the frame that raised, which is the last
            // chance a coroutine has to write its stack down.
            if (traced) debuglib!!.notestack(le)
            throw le
        } finally {
            if (traced) debuglib!!.onReturn()
        }
    }

    /** The call shapes themselves, without the bookkeeping around them. */
    /**
     * @return what the call produced, or null for a shape this does not handle
     */
    private suspend fun callFixedArityValues(stack: Array<LuaValue>, i: Int, a: Int): LuaValue? {
        val produced: LuaValue = when (i and (Lua.MASK_B or Lua.MASK_C)) {
            (1 shl Lua.POS_B) or (1 shl Lua.POS_C) -> stack[a].callSuspend() ?: NIL
            (2 shl Lua.POS_B) or (1 shl Lua.POS_C) -> stack[a].callSuspend(stack[a + 1]) ?: NIL
            (3 shl Lua.POS_B) or (1 shl Lua.POS_C) ->
                stack[a].callSuspend(stack[a + 1], stack[a + 2]) ?: NIL

            (4 shl Lua.POS_B) or (1 shl Lua.POS_C) ->
                stack[a].callSuspend(stack[a + 1], stack[a + 2], stack[a + 3]) ?: NIL

            (1 shl Lua.POS_B) or (2 shl Lua.POS_C) -> stack[a].callSuspend()!!.also { stack[a] = it }
            (2 shl Lua.POS_B) or (2 shl Lua.POS_C) ->
                stack[a].callSuspend(stack[a + 1])!!.also { stack[a] = it }

            (3 shl Lua.POS_B) or (2 shl Lua.POS_C) ->
                stack[a].callSuspend(stack[a + 1], stack[a + 2])!!.also { stack[a] = it }

            (4 shl Lua.POS_B) or (2 shl Lua.POS_C) ->
                stack[a].callSuspend(stack[a + 1], stack[a + 2], stack[a + 3])!!.also { stack[a] = it }

            else -> return null
        }
        return produced
    }

    /**
     * The bytecode interpreter loop.
     *
     * **Keep this method small enough to stay JIT-compilable.** HotSpot refuses
     * to compile any method whose bytecode exceeds `-XX:HugeMethodLimit`
     * (8000 bytes by default) and runs it in its own interpreter instead, which
     * costs this runtime roughly a factor of ten to eighteen. Being a `suspend`
     * function makes that easy to trip over: every suspending call site inside
     * the loop expands into a spill/restore block, so a handful of them is
     * worth hundreds of bytes each. `callFixedArity` exists purely to keep the
     * total under the limit, and `InterpreterCodeSizeTest` fails the build if it
     * creeps back up. If more room is needed, lift another group of opcode cases
     * out rather than raising the bound.
     */
    protected suspend fun execute(stack: Array<LuaValue>, varargs: Varargs): Varargs? {
        // loop through instructions
        var i: Int
        var a: Int
        var b: Int
        var c: Int
        var pc = 0
        var top = 0
        var o: LuaValue
        var v: Varargs = NONE!!
        val code: IntArray = p.code!!
        val k: Array<LuaValue?> = p.k!!


        // upvalues are only possible when closures create closures
        // TODO: use linked list.
        val openups: Array<UpValue?>? = if (p.p!!.size > 0) arrayOfNulls<UpValue>(stack.size) else null

        // Stack slots holding to-be-closed variables, outermost first. Stays
        // null for the overwhelming majority of functions, which declare none.
        var tbc: ArrayList<Int>? = null

        // Resolved once per frame rather than per instruction: the per-opcode
        // "globals != null && globals.debuglib != null" reload was two field
        // loads and two branches on the hottest path in the interpreter.
        val debuglib: DebugLib? = globals?.debuglib

        // Resolved once per frame for the same reason, and null unless the
        // host asked for a ceiling: what the loop below pays for a state
        // with no budget is the one null check.
        val budget: Budget? = globals?.budget

        // With the debug library watching a vararg function, the arguments get
        // storage of their own so debug.setlocal can write through to what
        // '...' reads. Nothing else pays for it.
        val tracked: Array<LuaValue?>? =
            if (debuglib != null && p.is_vararg != 0) copyArgs(varargs) else null
        // ArrayVarargs directly rather than varargsOf, which for one or two
        // values hands back something that no longer shares the array.
        val args: Varargs = if (tracked != null) Varargs.ArrayVarargs(tracked, NONE!!) else varargs

        // A named vararg parameter is a table over the extra arguments, built
        // once here so that it and '...' read the same storage.
        if (p.is_vararg and Lua.VARARG_NAMED != 0) buildVarargTable(args, p, stack)

        // allow for debug hooks
        if (debuglib != null) debuglib.onCall(this, args, stack as Array<LuaValue?>, tracked)

        // process instructions
        try {
            while (true) {
                // One decrement, and the ceiling is the interrupt as well:
                // Budget.interrupt() puts the count on the floor so that both
                // arrive through this branch. See Budget.
                if (budget != null && --budget.left <= 0L) budget.spent()
                if (debuglib != null) debuglib.onInstruction(pc, v, top)


                // pull out instruction
                i = code[pc]
                a = ((i shr 6) and 0xff)


                // process the op code
                when (i and 0x3f) {
                    Lua.OP_MOVE, Lua.OP_LOADK, Lua.OP_LOADNIL, Lua.OP_GETUPVAL,
                    Lua.OP_SETUPVAL, Lua.OP_NEWTABLE,
                    -> {
                        // Lifted out of this method to keep it under the JVM's
                        // 8000-bytecode JIT limit - see execute()'s doc.
                        loadOpcode(stack, i, a, k)
                        ++pc
                        continue
                    }

                    Lua.OP_LOADKX -> {
                        ++pc
                        i = code[pc]
                        if ((i and 0x3f) != Lua.OP_EXTRAARG) {
                            val op = i and 0x3f
                            throw LuaError(
                                "OP_EXTRAARG expected after OP_LOADKX, got " +
                                        (if (op < Print.OPNAMES.size - 1) Print.OPNAMES[op] else "UNKNOWN_OP_" + op)
                            )
                        }
                        stack[a] = k[i ushr 6]!!
                        ++pc
                        continue
                    }

                    Lua.OP_LOADBOOL -> {
                        stack[a] = (if (i ushr 23 != 0) LuaValue.TRUE else LuaValue.FALSE)!!
                        if ((i and (0x1ff shl 14)) != 0) ++pc /* skip next instruction (if C) */
                        ++pc
                        continue
                    }

                    Lua.OP_GETTABUP, Lua.OP_GETTABLE, Lua.OP_SETTABUP,
                    Lua.OP_SETTABLE, Lua.OP_SELF,
                    -> {
                        // Also lifted out because an __index or __newindex
                        // called from here may yield.
                        tableOpcode(stack, i, a, k)
                        ++pc
                        continue
                    }

                    Lua.OP_ADD, Lua.OP_SUB, Lua.OP_MUL, Lua.OP_DIV, Lua.OP_IDIV,
                    Lua.OP_BAND, Lua.OP_BOR, Lua.OP_BXOR, Lua.OP_SHL, Lua.OP_SHR,
                    Lua.OP_MOD, Lua.OP_POW, Lua.OP_CONCAT,
                    -> {
                        // Lifted out of this method to keep it under the JVM's
                        // 8000-bytecode JIT limit, and because a metamethod
                        // called from there may yield - see execute()'s doc.
                        binaryOpcode(stack, i, a, k)
                        ++pc
                        continue
                    }

                    Lua.OP_UNM, Lua.OP_NOT, Lua.OP_LEN, Lua.OP_BNOT -> {
                        unaryOpcode(stack, i, a)
                        ++pc
                        continue
                    }

                    Lua.OP_JMP -> {
                        pc += (i ushr 14) - 0x1ffff
                        if (a > 0) {
                            --a
                            if (tbc != null) closeToBeClosed(tbc, stack, a, null)?.let { throw it }
                            if (openups == null) {
                                ++pc
                                continue
                            }
                            b = openups.size
                            while (--b >= 0) {
                                if (openups[b] != null && openups[b]!!.index >= a) {
                                    openups[b]!!.close()
                                    openups[b] = null
                                }
                            }
                        }
                        ++pc
                        continue
                    }

                    Lua.OP_EQ, Lua.OP_LT, Lua.OP_LE -> {
                        // Lifted out because a comparison metamethod called
                        // from here may yield - see execute()'s doc.
                        if (compareOpcode(stack, i, k) != (a != 0)) ++pc
                        ++pc
                        continue
                    }

                    Lua.OP_TEST -> {
                        if (stack[a].toboolean() !== ((i and (0x1ff shl 14)) != 0)) ++pc
                        ++pc
                        continue
                    }

                    Lua.OP_TESTSET -> {
                        /* note: doc appears to be reversed */
                        o = stack[i ushr 23]
                        if (o.toboolean() !== ((i and (0x1ff shl 14)) != 0)) ++pc
                        else stack[a] = o // TODO: should be sBx?

                        ++pc
                        continue
                    }

                    Lua.OP_CALL -> {
                        // How many __call handlers stand between here and what
                        // actually runs, so its frame can report them.
                        if (debuglib != null) debuglib.notecallchain(stack[a])
                        when (i and (Lua.MASK_B or Lua.MASK_C)) {
                        (1 shl Lua.POS_B) or (0 shl Lua.POS_C) -> {
                            v = invokeTraced(stack[a], (NONE)!!, debuglib)
                            top = a + v.narg()
                            ++pc
                            continue
                        }

                        (2 shl Lua.POS_B) or (0 shl Lua.POS_C) -> {
                            v = invokeTraced(stack[a], stack[a + 1], debuglib)
                            top = a + v.narg()
                            ++pc
                            continue
                        }

                        else -> {
                            // The fixed-arity shapes touch nothing but stack[a], so
                            // they live in callFixedArity() to keep this method under
                            // the JVM's 8000-bytecode JIT limit - see execute()'s doc.
                            if (callFixedArity(stack, i, a, debuglib)) {
                                ++pc
                                continue
                            }
                            b = i ushr 23
                            c = (i shr 14) and 0x1ff
                            v = invokeTraced(
                                stack[a],
                                if (b > 0) varargsOf(stack, a + 1, b - 1) else  // exact arg count
                                    varargsOf(stack, a + 1, top - v.narg() - (a + 1), v),
                                debuglib,
                            ) // from prev top
                            if (c > 0) {
                                v.copyto(stack as Array<LuaValue?>, a, c - 1)
                                v = NONE
                            } else {
                                top = a + v.narg()
                                v = v.dealias()
                            }
                            ++pc
                            continue
                        }
                    }
                    }

                    Lua.OP_TAILCALL -> {
                        val args: Varargs = when (i and Lua.MASK_B) {
                            (1 shl Lua.POS_B) -> NONE!!
                            (2 shl Lua.POS_B) -> stack[a + 1]
                            (3 shl Lua.POS_B) -> varargsOf(stack[a + 1], stack[a + 2])
                            (4 shl Lua.POS_B) -> varargsOf(stack[a + 1], stack[a + 2], stack[a + 3])
                            else -> {
                                b = i ushr 23
                                if (b > 0) varargsOf(stack, a + 1, b - 1) // exact arg count
                                else varargsOf(stack, a + 1, top - v.narg() - (a + 1), v)
                            }
                        }
                        // A tail call leaves this frame before it is made, so
                        // anything that cannot be called has to be reported
                        // here while the instruction is still known, and a
                        // chain of __call handlers is followed here rather
                        // than by nesting one call inside the next.
                        if (debuglib != null) {
                            debuglib.notecallchain(stack[a])
                            // The frame this makes takes this one's place.
                            debuglib.ontailcall()
                        }
                        val prefix: ArrayList<LuaValue> = ArrayList()
                        val target: LuaValue = resolveTailcall(stack, a, prefix)
                        // See LuaValue.invoke: outermost first, so the
                        // innermost handler's own value leads the arguments.
                        var callArgs: Varargs = args
                        for (self in prefix) callArgs = varargsOf(self, callArgs)
                        // A library function is called from here rather than
                        // handed back as a tail call: it has no frame of its
                        // own to reuse, and calling it here is what gives it
                        // one for a traceback to name.
                        if (target !is LuaClosure) return invokeTraced(target, callArgs, debuglib)
                        return TailcallVarargs(target, callArgs)
                    }

                    Lua.OP_RETURN -> {
                        b = i ushr 23
                        // Before the results are read off the stack, as upstream
                        // closes at the return rather than after it.
                        if (tbc != null) closeToBeClosed(tbc, stack, 0, null)?.let { throw it }
                        val results: Varargs = when (b) {
                            0 -> varargsOf(stack, a, top - v.narg() - a, v)
                            1 -> NONE!!
                            2 -> stack[a]
                            else -> varargsOf(stack, a, b - 1)
                        }
                        // What it hands back, so a return hook can read them.
                        if (debuglib != null) debuglib.onResults(results)
                        return results
                    }

                    Lua.OP_FORLOOP -> {
                        // Lifted out to keep this method under the JVM's
                        // 8000-bytecode JIT limit - see execute()'s doc.
                        if (forLoop(stack, a)) pc += (i ushr 14) - 0x1ffff
                        ++pc
                        continue
                    }

                    Lua.OP_FORPREP -> {
                        // The jump goes past the loop's own closing
                        // instruction; a loop that does run falls through into
                        // its body with the control variable already set.
                        if (forPrep(stack, a)) pc += (i ushr 14) - 0x1ffff + 1
                        ++pc
                        continue
                    }

                    Lua.OP_TFORCALL -> {
                        v = stack[a].invokeSuspend((varargsOf(stack[a + 1], stack[a + 2]))!!)
                        c = (i shr 14) and 0x1ff
                        // Four control values now, so the results start one
                        // slot further along than they did in 5.2.
                        while (--c >= 0) stack[a + 4 + c] = v.arg(c + 1)
                        v = NONE
                        ++pc
                        continue
                    }

                    Lua.OP_TFORLOOP -> {
                        // R(A) is the control value and R(A+2) the first result,
                        // with the closing value in between.
                        if (!stack[a + 2].isnil()) { /* continue loop? */
                            stack[a] = stack[a + 2] /* save control variable */
                            pc += (i ushr 14) - 0x1ffff
                        }
                        ++pc
                        continue
                    }

                    Lua.OP_SETLIST -> {
                        c = (i shr 14) and 0x1ff
                        if (c == 0) c = code[++pc]
                        val offset: Int = (c - 1) * Lua.LFIELDS_PER_FLUSH
                        o = stack[a]
                        b = i ushr 23
                        if (b == 0) {
                            b = top - a - 1
                            val m: Int = b - v.narg()
                            var j = 1
                            while (j <= m) {
                                o.set(offset + j, stack[a + j])
                                j++
                            }
                            while (j <= b) {
                                o.set(offset + j, v.arg(j - m))
                                j++
                            }
                            // Let go of what the call handed over: the values
                            // are in the table now, and holding them here
                            // would keep alive what the program has finished
                            // with. See the end of this instruction.
                            v = NONE!!
                        } else {
                            o.presize(offset + b)
                            var j = 1
                            while (j <= b) {
                                o.set(offset + j, stack[a + j])
                                j++
                            }
                        }
                        // Let go of the table: this is the last instruction of
                        // a constructor, and holding it here would keep alive
                        // something the program has already finished with.
                        o = NIL
                        ++pc
                        continue
                    }

                    Lua.OP_CLOSURE -> {
                        stack[a] = makeclosure(stack, i, openups)
                        ++pc
                        continue
                    }

                    Lua.OP_VARARG -> {
                        val source: Varargs = varargSource(args, p, stack)
                        b = i ushr 23
                        if (b == 0) {
                            b = source.narg()
                            top = a + b
                            v = source
                        } else {
                            var j = 1
                            while (j < b) {
                                stack[a + j - 1] = source.arg(j)
                                ++j
                            }
                        }
                        ++pc
                        continue
                    }

                    Lua.OP_TBC -> {
                        tbc = markToBeClosed(tbc, stack[a], a, p, pc)
                        ++pc
                        continue
                    }

                    Lua.OP_ERRNNIL -> {
                        if (!stack[a].isnil()) errorAlreadyDefined(k, i ushr 14)
                        ++pc
                        continue
                    }

                    Lua.OP_EXTRAARG -> throw IllegalArgumentException("Uexecutable opcode: OP_EXTRAARG")

                    else -> throw IllegalArgumentException("Illegal opcode: " + (i and 0x3f))
                }
                ++pc
            }
        } catch (le: LuaError) {
            // Unwinding past a to-be-closed variable still closes it, and the
            // handler is told which error it is unwinding from.
            // A closer that raises replaces the error being unwound, so what
            // leaves here is not always what arrived.
            // Suspending here too: a handler may yield while the error is on
            // its way out, which is what a coroutine's own pcall allows.
            if (debuglib != null && tbc != null) debuglib.hidetopframe()
            val outgoing: LuaError = try {
                if (tbc == null) le else closeToBeClosed(tbc, stack, 0, le) ?: le
            } finally {
                if (debuglib != null && tbc != null) debuglib.showtopframe()
            }
            if (debuglib != null) debuglib.notestack(outgoing)
            if (outgoing.traceback == null) {
                enrichArgError(outgoing, p, pc, stack)
                enrichOperandError(outgoing, p, pc, stack)
                enrichCallError(outgoing, p, pc)
                enrichIndexError(outgoing, p, pc)
                processErrorHooks(outgoing, p, pc)
            }
            throw outgoing
        } catch (e: Exception) {
            val le: LuaError = LuaError(e)
            processErrorHooks(le, p, pc)
            throw le
        } catch (t: Throwable) {
            // The host running out of stack, and nothing else: a coroutine
            // being closed travels as an Error too and has to pass through.
            val le: LuaError = overflow(t) ?: throw t
            processErrorHooks(le, p, pc)
            throw le
        } finally {
            if (tbc != null) runLuaSync { closeToBeClosed(tbc, stack, 0, null) }?.let { throw it }
            if (openups != null) {
                var u = openups.size
                while (--u >= 0) {
                    if (openups[u] != null) openups[u]!!.close()
                }
            }
            if (debuglib != null) debuglib.onReturn()
        }
    }

    /**
     * Turns a host stack overflow into the Lua error it stands for.
     *
     * Reported only once the unwinding is a little way back from the edge:
     * where it was noticed there is no room left to build a message in, let
     * alone run a handler that walks the stack. The frames given up to make
     * that room are gone from the traceback, which is a report of a stack too
     * deep to print whole in any case.
     *
     * @return the error to raise, or null to let [t] carry on unwinding
     */
    private fun overflow(t: Throwable): LuaError? {
        if (!platformIsStackOverflow(t)) return null
        val state: LuaThread.State? = globals?.running?.state
        if (state != null) {
            if (++state.unwinding < STACK_UNWIND_HEADROOM) return null
            state.unwinding = 0
            // Running out of stack in the room kept for handling one is a
            // failure of the handling; see LuaThread.State.inhandler. It says
            // no more than that, with no place: the handling is what failed,
            // not anything the program wrote.
            if (state.inhandler > 0) {
                val failed = LuaError("error in error handling")
                failed.nowhere = true
                return failed
            }
        }
        return LuaError("stack overflow")
    }

    /**
     * Run the error hook if there is one
     * @param msg the message to use in error hook processing.
     */
    /**
     * Runs the message handler an `xpcall` installed, if there is one.
     *
     * The handler is shown the error object as it stands - a table stays a
     * table - and whatever it answers becomes the error from here on. Without
     * a handler nothing happens: Lua only builds a traceback when one asks for
     * it, as `xpcall(f, debug.traceback)` does, and putting one into the
     * message a plain `pcall` hands back is not what the caller asked for.
     */
    fun errorHook(le: LuaError) {
        val globals: Globals = this.globals ?: return
        val r: LuaThread = globals.running
        if (r.errorfunc == null) return
        val e: LuaValue = r.errorfunc!!
        // Running the handler is itself a call out of Lua, and it is made in
        // the room Lua keeps above the ordinary ceiling for exactly this. Past
        // that room there is nothing left to report but the failure of the
        // handling, and the handler is not called again.
        if (r.state.foreigncalls >= LuaThread.State.MAX_HANDLER_CALLS) {
            le.replaceMessage(LuaValue.valueOf("error in error handling")!!)
            le.traceback = "error in error handling"
            return
        }
        // A handler written in Lua pushes its own frame; one from the library,
        // debug.traceback most of all, needs one pushed for it so the levels it
        // counts line up with what a Lua handler would see.
        val debuglib: net.blueva.luak.lib.DebugLib? =
            if (e !is LuaClosure) globals.debuglib else null
        if (debuglib != null) debuglib.onCall(e as? LuaFunction)
        // The call itself is counted where it re-enters the interpreter, so
        // nothing is added here.
        // A handler written in Lua is counted where it enters the
        // interpreter; one of the library's own never does, and is counted
        // here so that a chain of them cannot go round for ever.
        val outer: Int = r.state.foreigncalls
        if (e !is LuaClosure) r.state.foreigncalls++
        r.state.inhandler++
        val handled: LuaValue = try {
            e.call(le.messageObject ?: NIL)!!
        } catch (nested: LuaError) {
            // The handler raised in its turn. Lua hands that to the same
            // handler, again and again, until the room kept for handling runs
            // out and the failure of the handling is what is left to report.
            // An error raised by Lua code has already been through the handler
            // on its way out of that code; one raised by the runtime itself,
            // which is what running out of room looks like, has not.
            if (nested.traceback == null) errorHook(nested)
            nested.messageObject ?: NIL
        } catch (t: Throwable) {
            LuaValue.valueOf("error in error handling")!!
        } finally {
            r.state.inhandler--
            r.state.foreigncalls = outer
            if (debuglib != null) debuglib.onReturn()
        }
        le.replaceMessage(handled)
        // Doubles as the mark that the handler has already run.
        le.traceback = handled.tojstring()
    }

    /**
     * Says where the thing that could not be indexed came from.
     *
     * "attempt to index a nil value" becomes "... (field 'x')" once the
     * instruction is read back to see which register or upvalue held it.
     */
    private fun enrichIndexError(le: LuaError, p: Prototype, pc: Int) {
        if (le.argMessageOverride != null) return
        val m: String = le.message ?: return
        if (!Regex("^attempt to index a .+ value$").matches(m)) return
        val code: IntArray = p.code ?: return
        if (pc < 0 || pc >= code.size) return
        val instr: Int = code[pc]
        val kind: String
        val name: String
        when (Lua.GET_OPCODE(instr)) {
            Lua.OP_GETTABLE, Lua.OP_SELF -> {
                val found = net.blueva.luak.lib.DebugLib.getobjname(p, pc, Lua.GETARG_B(instr)) ?: return
                kind = found.namewhat
                name = found.name
            }

            Lua.OP_SETTABLE -> {
                val found = net.blueva.luak.lib.DebugLib.getobjname(p, pc, Lua.GETARG_A(instr)) ?: return
                kind = found.namewhat
                name = found.name
            }

            Lua.OP_GETTABUP -> {
                val up = p.upvalues?.getOrNull(Lua.GETARG_B(instr)) ?: return
                kind = "upvalue"
                name = up.name?.tojstring() ?: return
            }

            Lua.OP_SETTABUP -> {
                val up = p.upvalues?.getOrNull(Lua.GETARG_A(instr)) ?: return
                kind = "upvalue"
                name = up.name?.tojstring() ?: return
            }

            else -> return
        }
        le.argMessageOverride = m + " (" + kind + " '" + name + "')"
    }

    /**
     * Says how the program named the thing it tried to call.
     *
     * "attempt to call a nil value" becomes "... (field 'bbbb')" once the call
     * instruction is read back, which is usually the whole of what the reader
     * needs to spot a misspelling.
     */
    private fun enrichCallError(le: LuaError, p: Prototype, pc: Int) {
        if (le.argMessageOverride != null) return
        val m: String = le.message ?: return
        if (!Regex("^attempt to call a .+ value$").matches(m)) return
        val code: IntArray = p.code ?: return
        if (pc < 0 || pc >= code.size) return
        val instr: Int = code[pc]
        val opcode: Int = Lua.GET_OPCODE(instr)
        val found = when (opcode) {
            Lua.OP_CALL, Lua.OP_TAILCALL ->
                net.blueva.luak.lib.DebugLib.getobjname(p, pc, Lua.GETARG_A(instr))
            // The one other instruction that calls: a generic `for` steps its
            // iterator, and Lua names that as what it is.
            Lua.OP_TFORCALL -> net.blueva.luak.lib.DebugLib.NameWhat("for iterator", "for iterator")
            else -> return
        } ?: return
        le.argMessageOverride = m + " (" + found.namewhat + " '" + found.name + "')"
    }

    /**
     * Says where a rejected operand came from, as Lua's `varinfo` does.
     *
     * "attempt to perform arithmetic on a nil value" becomes "... (field 'x')"
     * once the instruction is read back to see which operand had that type and
     * how the program named it. Without this the message says what went wrong
     * but not which of the two values was at fault.
     */
    private fun enrichOperandError(le: LuaError, p: Prototype, pc: Int, stack: Array<LuaValue>) {
        if (le.argMessageOverride != null) return
        val m: String = le.message ?: return
        // Two messages carry a varinfo: one names the type it could not work
        // on, the other says a number was not a whole one.
        val wanted: String
        val insertAt: Int
        val operand = Regex("^attempt to perform (?:arithmetic|bitwise operation) on a (.+) value$")
            .find(m)
        if (operand != null) {
            wanted = operand.groupValues[1]
            insertAt = m.length
        } else if (m == "number has no integer representation") {
            wanted = "number"
            insertAt = "number".length
        } else {
            return
        }
        val code: IntArray = p.code ?: return
        if (pc < 0 || pc >= code.size) return
        val instr: Int = code[pc]
        val operands: IntArray = when (Lua.GET_OPCODE(instr)) {
            Lua.OP_ADD, Lua.OP_SUB, Lua.OP_MUL, Lua.OP_DIV, Lua.OP_MOD, Lua.OP_POW,
            Lua.OP_IDIV, Lua.OP_BAND, Lua.OP_BOR, Lua.OP_BXOR, Lua.OP_SHL, Lua.OP_SHR,
            -> intArrayOf(Lua.GETARG_B(instr), Lua.GETARG_C(instr))

            Lua.OP_UNM, Lua.OP_BNOT, Lua.OP_LEN -> intArrayOf(Lua.GETARG_B(instr))
            else -> return
        }
        for (rk in operands) {
            val value: LuaValue = if (Lua.ISK(rk)) {
                p.k?.getOrNull(Lua.INDEXK(rk)) ?: continue
            } else {
                if (rk >= stack.size) continue
                stack[rk]
            }
            // Compared by the name the message used, which a __name field
            // may have replaced.
            if (value.objtypename() != wanted) continue
            // For the "not a whole number" message, the operand to blame is
            // the one that is not whole - the other may well be an integer.
            if (insertAt != m.length && net.blueva.luak.luaHasIntegerRepresentation(value)) continue
            val kind: String
            val name: String
            if (Lua.ISK(rk)) {
                kind = "constant"
                name = net.blueva.luak.lib.DebugLib.kname(p, pc, rk)
            } else {
                val found = net.blueva.luak.lib.DebugLib.getobjname(p, pc, rk) ?: return
                kind = found.namewhat
                name = found.name
            }
            val varinfo = " (" + kind + " '" + name + "')"
            le.argMessageOverride = m.substring(0, insertAt) + varinfo + m.substring(insertAt)
            return
        }
    }

    /**
     * Enrich a raw "bad argument #N: detail" message (stamped by [Varargs]'
     * argument checkers, which don't know the calling function's name) with
     * that name, matching real Lua's "bad argument #N to 'name' (detail)".
     * Mirrors real Lua's `luaL_argerror`/`getobjname`: [pc] is still the
     * CALL/TAILCALL instruction that invoked the failing callee, since the
     * throw unwound before the loop's `++pc`.
     */
    private fun enrichArgError(le: LuaError, p: Prototype, pc: Int, stack: Array<LuaValue>) {
        var m = le.message ?: return
        val code = p.code ?: return
        if (pc < 0 || pc >= code.size) return
        val instr = code[pc]
        val opcode = Lua.GET_OPCODE(instr)
        if (opcode != Lua.OP_CALL && opcode != Lua.OP_TAILCALL) return
        val a = Lua.GETARG_A(instr)
        // A check made on a value alone cannot know which argument it came
        // from. For a function that takes one argument there is only one it
        // could have been, so the index can be filled in here.
        if (m.startsWith("bad argument: ") && a < stack.size &&
            stack[a] is net.blueva.luak.lib.OneArgFunction
        ) {
            m = "bad argument #1: " + m.removePrefix("bad argument: ")
        }
        val match = Regex("^bad argument #(\\d+): ([\\s\\S]*)$").find(m) ?: return
        var argIndex = match.groupValues[1].toIntOrNull() ?: return
        val detail = match.groupValues[2]
        val nw = net.blueva.luak.lib.DebugLib.getobjname(p, pc, a)
        if (nw != null && nw.namewhat == "method") {
            argIndex--
            if (argIndex == 0) {
                le.argMessageOverride = "calling '" + nw.name + "' on bad self (" + detail + ")"
                return
            }
        }
        val funcname = nw?.name ?: "?"
        le.argMessageOverride = "bad argument #" + argIndex + " to '" + funcname + "' (" + detail + ")"
    }

    private fun processErrorHooks(le: LuaError, p: Prototype, pc: Int) {
        // Done once, where the error was raised: every function it unwinds
        // through afterwards would count its levels from itself and answer
        // with its own line. See [LuaError.positioned].
        if (le.positioned) return
        le.positioned = true
        // Raised where Lua adds no place; see LuaError.nowhere.
        if (le.nowhere) {
            errorHook(le)
            return
        }
        // A level of zero says the message is complete as it stands, which is
        // what `error(msg, 0)` asks for.
        if (le.level <= 0) {
            errorHook(le)
            return
        }
        var file: String? = "?"
        var line = -1
        run {
            var frame: CallFrame? = null
            val debuglib: net.blueva.luak.lib.DebugLib? = globals?.debuglib
            if (debuglib != null) {
                // The library function that raised has already been popped, so
                // level 1 - the function the error is reported against - is
                // the frame at the top from here.
                frame = debuglib.getCallFrame(le.level - 1)
                if (frame != null) {
                    val src: String? = frame.shortsource()
                    file = if (src != null) src else "?"
                    line = frame.currentline()
                }
            }
            if (frame == null) {
                // Shortened the way Lua shortens it, so a long path or a chunk
                // given as text does not run away with the message.
                file = p.shortsource()
                line = if (p.lineinfo != null && pc >= 0 && pc < p.lineinfo!!.size) p.lineinfo!![pc] else -1
            }
        }
        // A chunk whose debug information was stripped has neither a name nor
        // a line to report, and Lua writes both as a question mark.
        le.fileline = file.toString() + ":" + (if (line < 0) "?" else line.toString())
        errorHook(le)
    }

    /**
     * Prepares a numeric `for`, upstream's `forprep`.
     *
     * An integer loop works out how many passes it has before it starts and
     * keeps the count where the limit was: adding the step to the index can
     * wrap around, but a count cannot, so a loop that walks the whole integer
     * range still ends.
     *
     * @return true when the loop does not run at all
     */
    private fun forPrep(stack: Array<LuaValue>, a: Int): Boolean {
        // Checked in upstream's order - limit, step, then the initial value -
        // so a loop with more than one bad bound names the same one Lua would.
        val limit: LuaValue = forNumber(stack[a + 1], "limit")
        val step: LuaValue = forNumber(stack[a + 2], "step")
        val init: LuaValue = forNumber(stack[a], "initial value")
        if (init is LuaInteger && step is LuaInteger) {
            val start: Long = init.tolong()
            val by: Long = step.tolong()
            if (by == 0L) LuaValue.error("'for' step is zero")
            val bound: Long = forLimit(limit, start, by) ?: return true
            val passes: ULong = if (by > 0L) {
                val span: ULong = bound.toULong() - start.toULong()
                if (by == 1L) span else span / by.toULong()
            } else {
                val span: ULong = start.toULong() - bound.toULong()
                // Negating math.mininteger would overflow, so the magnitude is
                // built from '-(by + 1)' instead.
                span / ((-(by + 1L)).toULong() + 1uL)
            }
            stack[a] = init
            stack[a + 1] = LuaValue.valueOf(passes.toLong())
            stack[a + 2] = step
            stack[a + 3] = init
            return false
        }
        // A float loop has no count to work out and compares against the limit
        // on every pass instead.
        val start: Double = init.todouble()
        val by: Double = step.todouble()
        val bound: Double = limit.todouble()
        if (by == 0.0) LuaValue.error("'for' step is zero")
        if (if (by > 0.0) start > bound else start < bound) return true
        val first: LuaValue = LuaValue.valueOf(start)
        stack[a] = first
        stack[a + 1] = LuaValue.valueOf(bound)
        stack[a + 2] = LuaValue.valueOf(by)
        stack[a + 3] = first
        return false
    }

    /**
     * The integer a `for` loop counts up or down to, upstream's `forlimit`.
     *
     * A float limit is rounded towards the loop's direction; one beyond the
     * integer range is either the far end of it or, when it lies the wrong way
     * round, a loop that never runs.
     *
     * @return the limit, or null when the loop does not run at all
     */
    private fun forLimit(limit: LuaValue, init: Long, step: Long): Long? {
        val bound: Long
        if (limit is LuaInteger) {
            bound = limit.tolong()
        } else {
            val value: Double = limit.todouble()
            val rounded: Double =
                if (step < 0L) kotlin.math.ceil(value) else kotlin.math.floor(value)
            if (rounded >= -9223372036854775808.0 && rounded < 9223372036854775808.0) {
                bound = rounded.toLong()
            } else if (rounded > 0.0) {
                // Too large to reach; a descending loop never gets there.
                if (step < 0L) return null
                bound = Long.MAX_VALUE
            } else {
                if (step > 0L) return null
                bound = Long.MIN_VALUE
            }
        }
        return if (if (step > 0L) init > bound else init < bound) null else bound
    }

    /**
     * The function a tail call really reaches, following `__call` handlers.
     *
     * Following the chain here rather than letting each handler call the next
     * keeps a tail call flat, so a loop written as `return t()` over a
     * `__call` table runs as long as one written over a function.
     */
    private fun resolveTailcall(stack: Array<LuaValue>, a: Int, prefix: ArrayList<LuaValue>): LuaValue =
        stack[a].resolvecall(prefix)

    /**
     * Calls [f], giving a library function a frame of its own while it runs.
     *
     * A Lua function pushes its own on the way in; anything else has none, and
     * without one a traceback would not name it and the call and return hooks
     * would never fire for it.
     */
    private suspend fun invokeTraced(f: LuaValue, args: Varargs, debuglib: DebugLib?): Varargs {
        if (debuglib == null || f !is LuaFunction || f is LuaClosure) return f.invokeSuspend(args)
        debuglib.onCall(f, copyArgs(args))
        try {
            val results: Varargs = f.invokeSuspend(args)
            // What it hands back, so a return hook can read the results.
            debuglib.onResults(results)
            return results
        } catch (le: LuaError) {
            debuglib.notestack(le)
            throw le
        } finally {
            debuglib.onReturn()
        }
    }

    /**
     * One pass of a numeric `for`, upstream's `forloop`.
     *
     * @return true when the loop should go round again
     */
    private fun forLoop(stack: Array<LuaValue>, a: Int): Boolean {
        val step: LuaValue = stack[a + 2]
        if (step is LuaInteger) {
            // Read as unsigned: only the test against zero and the decrement
            // matter, and both are the same bits.
            val remaining: Long = stack[a + 1].tolong()
            if (remaining == 0L) return false
            stack[a + 1] = LuaValue.valueOf(remaining - 1L)
            val next: LuaValue = LuaValue.valueOf(stack[a].tolong() + step.tolong())
            stack[a] = next
            stack[a + 3] = next
            return true
        }
        val by: Double = step.todouble()
        val next: Double = stack[a].todouble() + by
        val limit: Double = stack[a + 1].todouble()
        if (if (by > 0.0) next > limit else limit > next) return false
        val value: LuaValue = LuaValue.valueOf(next)
        stack[a] = value
        stack[a + 3] = value
        return true
    }

    /** Builds the closure an OP_CLOSURE asks for, binding its upvalues. */
    private fun makeclosure(stack: Array<LuaValue>, i: Int, openups: Array<UpValue?>?): LuaClosure {
        val newp: Prototype = p.p!![i ushr 14]!!
        val ncl = net.blueva.luak.LuaClosure(newp, globals)
        val uv: Array<Upvaldesc?> = newp.upvalues!!
        var j = 0
        while (j < uv.size) {
            ncl.upValues[j] = if (uv[j]!!.instack) {
                findupval(stack, uv[j]!!.idx, openups!!)
            } else {
                upValues[(uv[j]!!.idx).toInt()]
            }
            ++j
        }
        // Making a function is an allocation like any other, and so a place a
        // collection can happen; see callFixedArity.
        val g: Globals? = globals
        if (g != null && g.marksfinalizers) g.runfinalizers()
        return ncl
    }

    /** The opcodes that only move a value about, with nothing to dispatch. */
    private fun loadOpcode(stack: Array<LuaValue>, i: Int, a: Int, k: Array<LuaValue?>) {
        when (i and 0x3f) {
            Lua.OP_MOVE -> stack[a] = stack[i ushr 23]
            Lua.OP_LOADK -> stack[a] = k[i ushr 14]!!
            Lua.OP_LOADNIL -> {
                var slot: Int = a
                var count: Int = i ushr 23
                while (count-- >= 0) stack[slot++] = LuaValue.NIL
            }

            Lua.OP_GETUPVAL -> stack[a] = upValues[i ushr 23]!!.getValue()!!
            Lua.OP_SETUPVAL -> upValues[i ushr 23]!!.setValue(stack[a])
            else -> {
                stack[a] = LuaTable(i ushr 23, (i shr 14) and 0x1ff)
                // Allocating is where Lua runs a step of its collector, and so
                // where anything waiting to be finalized gets its turn.
                val g: Globals? = globals
                if (g != null && g.marksfinalizers) {
                    // This instruction always writes to the first free
                    // register, so nothing above it is live any more. Lua's
                    // collector reaches the same conclusion by only looking at
                    // a stack up to its top; here the registers are emptied,
                    // so that what a finished statement left behind stops
                    // holding an object that is due to be finalized.
                    var slot: Int = a + 1
                    while (slot < stack.size) {
                        stack[slot] = LuaValue.NIL
                        slot++
                    }
                    g.runfinalizers()
                }
            }
        }
    }

    /**
     * The opcodes that read or write a field.
     *
     * A table answers from its own storage; anything else, or a miss with an
     * `__index`, goes through the metamethod, which may yield.
     */
    private suspend fun tableOpcode(stack: Array<LuaValue>, i: Int, a: Int, k: Array<LuaValue?>) {
        val c: Int = (i shr 14) and 0x1ff
        when (i and 0x3f) {
            Lua.OP_GETTABUP -> {
                val target: LuaValue = upValues[i ushr 23]!!.getValue()!!
                stack[a] = index(target, operand(stack, k, c))
            }

            Lua.OP_GETTABLE -> stack[a] = index(stack[i ushr 23], operand(stack, k, c))

            Lua.OP_SELF -> {
                val target: LuaValue = stack[i ushr 23]
                stack[a + 1] = target
                stack[a] = index(target, operand(stack, k, c))
            }

            Lua.OP_SETTABUP -> {
                val target: LuaValue = upValues[a]!!.getValue()!!
                newindex(target, operand(stack, k, i ushr 23), operand(stack, k, c))
            }

            else -> newindex(stack[a], operand(stack, k, i ushr 23), operand(stack, k, c))
        }
    }

    /**
     * Reads `target[key]`, following `__index` as far as it leads.
     *
     * The handler is called from here rather than from inside the value, so a
     * coroutine can yield out of one.
     */
    private suspend fun index(target: LuaValue, key: LuaValue): LuaValue {
        var value: LuaValue = target
        var loop = 0
        while (loop++ < LuaValue.MAXTAGLOOP) {
            val handler: LuaValue
            if (value.istable()) {
                val found: LuaValue = value.rawget(key)
                if (!found.isnil()) return found
                handler = value.metatag(LuaValue.INDEX)
                if (handler.isnil()) return found
            } else {
                handler = value.metatag(LuaValue.INDEX)
                if (handler.isnil()) return value.get(key) // reports what it is
            }
            if (handler.isfunction()) return callmeta(handler, value, key)
            value = handler
        }
        LuaValue.error("loop in gettable")
        return LuaValue.NIL
    }

    /** Writes `target[key]`, following `__newindex` as far as it leads. */
    private suspend fun newindex(target: LuaValue, key: LuaValue, value: LuaValue) {
        var holder: LuaValue = target
        var loop = 0
        while (loop++ < LuaValue.MAXTAGLOOP) {
            val handler: LuaValue
            if (holder.istable()) {
                if (!holder.rawget(key).isnil()) {
                    holder.rawset(key, value)
                    return
                }
                handler = holder.metatag(LuaValue.NEWINDEX)
                if (handler.isnil()) {
                    holder.set(key, value) // reports a bad key as Lua does
                    return
                }
            } else {
                handler = holder.metatag(LuaValue.NEWINDEX)
                if (handler.isnil()) {
                    holder.set(key, value) // reports what it is
                    return
                }
            }
            if (handler.isfunction()) {
                handler.callSuspend(holder, key, value)
                return
            }
            holder = handler
        }
        LuaValue.error("loop in settable")
    }

    /**
     * The two operands of a binary opcode, constants and registers alike.
     */
    private fun operand(stack: Array<LuaValue>, k: Array<LuaValue?>, rk: Int): LuaValue =
        if (rk > 0xff) k[rk and 0x0ff]!! else stack[rk]

    /**
     * Runs `__add` and its kin, letting the handler yield if it wants to.
     *
     * The handler is looked for on the left operand and then on the right, as
     * Lua looks for it, and calling it here rather than from inside the value
     * itself is what lets a coroutine yield out of one.
     */
    private suspend fun binmeta(tag: LuaValue, lhs: LuaValue, rhs: LuaValue): LuaValue {
        var h: LuaValue = lhs.metatag(tag)
        if (h.isnil()) h = rhs.metatag(tag)
        if (h.isnil()) LuaValue.operandError(tag, lhs, rhs)
        lhs.checkcallable(tag, h)
        return callmeta(h, lhs, rhs)
    }

    /**
     * The arithmetic, bitwise and concatenation opcodes.
     *
     * Two numbers are worked out here and now; anything else goes through the
     * metamethod, which may yield, which is why this is a suspending method of
     * its own rather than part of [execute].
     */
    /**
     * The comparison opcodes.
     *
     * Two numbers or two strings are compared here and now; anything else goes
     * through the metamethod, which may yield.
     */
    private suspend fun compareOpcode(stack: Array<LuaValue>, i: Int, k: Array<LuaValue?>): Boolean {
        val lhs: LuaValue = operand(stack, k, i ushr 23)
        val rhs: LuaValue = operand(stack, k, (i shr 14) and 0x1ff)
        val opcode: Int = i and 0x3f
        if (opcode == Lua.OP_EQ) {
            if (lhs.raweq(rhs)) return true
            // Only two tables or two full userdata have an __eq to consult.
            if (lhs.type() != rhs.type()) return false
            if (!lhs.istable() && !lhs.isuserdata()) return false
            var h: LuaValue = lhs.metatag(LuaValue.EQ)
            if (h.isnil()) h = rhs.metatag(LuaValue.EQ)
            if (h.isnil()) return false
            lhs.checkcallable(LuaValue.EQ, h)
            return callmeta(h, lhs, rhs).toboolean()
        }
        val primitive: Boolean =
            (lhs is LuaNumber && rhs is LuaNumber) || (lhs is LuaString && rhs is LuaString)
        if (primitive) {
            return if (opcode == Lua.OP_LT) lhs.lt_b(rhs) else lhs.lteq_b(rhs)
        }
        val tag: LuaValue = if (opcode == Lua.OP_LT) LuaValue.LT else LuaValue.LE
        var h: LuaValue = lhs.metatag(tag)
        if (h.isnil()) h = rhs.metatag(tag)
        if (h.isnil()) {
            // Lua 5.4 dropped the "not (b < a)" stand-in for a missing __le,
            // so there is nothing left to try.
            lhs.ordererror(lhs, rhs)
        }
        lhs.checkcallable(tag, h)
        return callmeta(h, lhs, rhs).toboolean()
    }

    private suspend fun binaryOpcode(stack: Array<LuaValue>, i: Int, a: Int, k: Array<LuaValue?>) {
        val opcode: Int = i and 0x3f
        if (opcode == Lua.OP_CONCAT) {
            var b: Int = i ushr 23
            var c: Int = (i shr 14) and 0x1ff
            while (c > b) {
                val left: LuaValue = stack[c - 1]
                val right: LuaValue = stack[c]
                stack[c - 1] = if (left.isstring() && right.isstring()) {
                    left.concat(right)
                } else {
                    concatmeta(left, right)
                }
                c--
            }
            stack[a] = stack[b]
            return
        }
        val lhs: LuaValue = operand(stack, k, i ushr 23)
        val rhs: LuaValue = operand(stack, k, (i shr 14) and 0x1ff)
        if (lhs is LuaNumber && rhs is LuaNumber) {
            stack[a] = when (opcode) {
                Lua.OP_ADD -> lhs.add(rhs)
                Lua.OP_SUB -> lhs.sub(rhs)
                Lua.OP_MUL -> lhs.mul(rhs)
                Lua.OP_DIV -> lhs.div(rhs)
                Lua.OP_IDIV -> lhs.idiv(rhs)
                Lua.OP_MOD -> lhs.mod(rhs)
                Lua.OP_POW -> lhs.pow(rhs)
                Lua.OP_BAND -> lhs.band(rhs)
                Lua.OP_BOR -> lhs.bor(rhs)
                Lua.OP_BXOR -> lhs.bxor(rhs)
                Lua.OP_SHL -> lhs.shl(rhs)
                else -> lhs.shr(rhs)
            }
            return
        }
        val tag: LuaValue = when (opcode) {
            Lua.OP_ADD -> LuaValue.ADD
            Lua.OP_SUB -> LuaValue.SUB
            Lua.OP_MUL -> LuaValue.MUL
            Lua.OP_DIV -> LuaValue.DIV
            Lua.OP_IDIV -> LuaValue.IDIV
            Lua.OP_MOD -> LuaValue.MOD
            Lua.OP_POW -> LuaValue.POW
            Lua.OP_BAND -> LuaValue.BAND
            Lua.OP_BOR -> LuaValue.BOR
            Lua.OP_BXOR -> LuaValue.BXOR
            Lua.OP_SHL -> LuaValue.SHL
            else -> LuaValue.SHR
        }
        stack[a] = binmeta(tag, lhs, rhs)
    }

    /** `__concat`, which may yield, blaming the operand that is not a string. */
    private suspend fun concatmeta(lhs: LuaValue, rhs: LuaValue): LuaValue {
        var h: LuaValue = lhs.metatag(LuaValue.CONCAT)
        if (h.isnil()) h = rhs.metatag(LuaValue.CONCAT)
        if (h.isnil()) {
            val culprit: LuaValue = if (!lhs.isstring() || lhs is LuaTable) lhs else rhs
            LuaValue.error("attempt to concatenate a " + culprit.objtypename() + " value")
        }
        lhs.checkcallable(LuaValue.CONCAT, h)
        return callmeta(h, lhs, rhs)
    }

    /** The unary opcodes, whose metamethods may yield in the same way. */
    private suspend fun unaryOpcode(stack: Array<LuaValue>, i: Int, a: Int) {
        val operand: LuaValue = stack[i ushr 23]
        when (i and 0x3f) {
            Lua.OP_NOT -> stack[a] = operand.not()!!
            Lua.OP_UNM -> stack[a] =
                if (operand is LuaNumber) operand.neg() else unmeta(LuaValue.UNM, operand)

            Lua.OP_BNOT -> stack[a] =
                if (operand is LuaNumber) operand.bnot() else unmeta(LuaValue.BNOT, operand)

            else -> stack[a] =
                if (operand is LuaString) operand.len() else unmeta(LuaValue.LEN, operand)
        }
    }

    /**
     * A unary metamethod, which Lua hands its operand twice.
     *
     * A table answers `#t` from its own length when it has no `__len`, which is
     * the one case that is not an error without a handler.
     */
    private suspend fun unmeta(tag: LuaValue, operand: LuaValue): LuaValue {
        val h: LuaValue = operand.metatag(tag)
        if (h.isnil()) {
            if (tag == LuaValue.LEN && operand is LuaTable) return operand.len()
            if (tag == LuaValue.LEN) {
                LuaValue.error("attempt to get length of a " + operand.objtypename() + " value")
            }
            LuaValue.operandError(tag, operand, operand)
        }
        operand.checkcallable(tag, h)
        return callmeta(h, operand, operand)
    }

    /** The arguments of a fixed-arity call, for the debug library to report. */
    private fun fixedArityArgs(stack: Array<LuaValue>, i: Int, a: Int): Array<LuaValue?> {
        val count: Int = ((i ushr 23) and 0x1ff) - 1
        if (count <= 0) return arrayOfNulls(0)
        val values: Array<LuaValue?> = arrayOfNulls(count)
        for (index in 0..<count) values[index] = stack[a + 1 + index]
        return values
    }

    /** The call's arguments in an array of their own, so they can be written to. */
    private fun copyArgs(varargs: Varargs): Array<LuaValue?> {
        val n: Int = varargs.narg()
        val values: Array<LuaValue?> = arrayOfNulls(n)
        for (index in 0..<n) values[index] = varargs.arg(index + 1)
        return values
    }

    /** One bound of a numeric `for`, or the error Lua reports for a bad one. */
    private fun forNumber(value: LuaValue, what: String): LuaValue {
        val number: LuaValue = value.tonumber()
        if (number.isnil()) {
            LuaValue.error("bad 'for' " + what + " (number expected, got " + value.objtypename() + ")")
        }
        return number
    }

    /**
     * Reports a `global x = v` declaration for a global that already has one.
     *
     * @param bx the name's constant index plus one, or zero if it did not fit
     */
    private fun errorAlreadyDefined(k: Array<LuaValue?>, bx: Int): Nothing {
        val name: String = if (bx > 0 && bx - 1 < k.size) k[bx - 1]!!.tojstring() else "?"
        LuaValue.error("global '" + name + "' already defined")
        throw IllegalStateException()
    }

    /** As many values as a vararg table may claim, mirroring Lua's stack cap. */
    private val MAX_VARARG_TABLE: Long = 1000000L

    /**
     * Fills the register of a named vararg parameter with its table.
     *
     * The table holds the extra arguments at `1..n` and their count at `n`,
     * which is what makes `t.n` right even when an argument was nil.
     */
    private fun buildVarargTable(varargs: Varargs, p: Prototype, stack: Array<LuaValue>) {
        val count: Int = varargs.narg()
        // The one instance throughout, so that what is taken back below comes
        // off the same tally it went on.
        val memory: Memory = Memory.current
        val before: Long = memory.accounted
        val table = LuaTable(count, 1)
        for (i in 1..count) table.set(i, varargs.arg(i)!!)
        table.set("n", count)
        // The arguments of a call are not an allocation of the program's; see
        // Memory.uncount.
        memory.uncount(memory.accounted - before)
        stack[p.numparams] = table
    }

    /**
     * Where `...` reads from.
     *
     * Ordinarily the arguments the call arrived with; in a function that named
     * them, the table they were put in, so a change made through the name is
     * visible through `...` as well.
     */
    private fun varargSource(varargs: Varargs, p: Prototype, stack: Array<LuaValue>): Varargs {
        if (p.is_vararg and Lua.VARARG_NAMED == 0) return varargs
        val table: LuaValue = stack[p.numparams]
        val declared: LuaValue = table.get("n")!!
        // The table's 'n' says how many values '...' has, so a program that
        // sets it to something that is not a sensible count has broken the
        // link rather than resized it.
        if (!declared.isnumber() || !declared.isinttype()) {
            LuaValue.error("vararg table has no proper 'n'")
        }
        val n: Long = declared.tolong()
        if (n < 0 || n > MAX_VARARG_TABLE) LuaValue.error("vararg table has no proper 'n'")
        val count: Int = n.toInt()
        if (count <= 0) return NONE!!
        val out: Array<LuaValue?> = arrayOfNulls(count)
        for (i in 0..<count) out[i] = table.get(i + 1)
        return varargsOf(out)!!
    }

    /**
     * Registers R([slot]) as a to-be-closed variable, from `local x <close>`.
     *
     * A false or nil value is not closed and not remembered, which is what lets
     * `local f <close> = io.open(...)` be written without a separate check.
     * Anything else has to answer a `__close` metamethod, and the complaint
     * comes at the declaration rather than at the end of the block.
     *
     * @return the list to keep, which is created on the first such variable
     */
    private fun markToBeClosed(
        list: ArrayList<Int>?,
        value: LuaValue,
        slot: Int,
        p: Prototype,
        pc: Int,
    ): ArrayList<Int>? {
        if (!value.toboolean()) return list
        if (value.metatag(LuaValue.CLOSE).isnil()) {
            val name: LuaString? = p.getlocalname(slot + 1, pc)
            LuaValue.error(
                "variable '" + (name?.tojstring() ?: "?") + "' got a non-closable value",
            )
        }
        val out: ArrayList<Int> = list ?: ArrayList(1)
        out.add(slot)
        return out
    }

    /**
     * Closes the to-be-closed variables at or above [level], innermost first.
     *
     * Each is dropped from the list as it is closed, so a later pass - the
     * `finally` after an error has already unwound one - does not close it
     * twice.
     *
     * A handler that raises does not stop the ones outside it: its error
     * becomes what they are told about, and the last one raised is what
     * leaves here.
     *
     * @param error the error being unwound from, or null on an ordinary exit
     * @return the error to carry on with, or null if none is outstanding
     */
    private suspend fun closeToBeClosed(
        list: ArrayList<Int>,
        stack: Array<LuaValue>,
        level: Int,
        error: LuaError?,
    ): LuaError? {
        var pending: LuaError? = error
        var index = list.size
        while (--index >= 0) {
            val slot: Int = list[index]
            if (slot < level) break
            list.removeAt(index)
            val value: LuaValue = stack[slot]
            val close: LuaValue = value.metatag(LuaValue.CLOSE)
            // The handler may have been taken away since the variable was
            // marked, so what is there now still has to be callable.
            value.checkcallable(LuaValue.CLOSE, close)
            try {
                // With no error to report the handler is called with the value
                // alone: a trailing nil would be an argument the language does
                // not pass, and '...' inside the handler would count it.
                val raised: LuaError? = pending
                // Suspending, so a handler may yield on the ordinary ways out
                // of a block. The paths that close while an error is unwinding
                // reach this through runLuaSync instead, which is where Lua
                // also refuses to yield.
                if (raised == null) {
                    close.callSuspend(value)
                } else {
                    close.callSuspend(value, raised.messageObject ?: NIL)
                }
            } catch (failure: LuaError) {
                pending = failure
            }
        }
        return pending
    }

    private fun findupval(stack: Array<LuaValue>, idx: Short, openups: Array<UpValue?>): UpValue? {
        val n = openups.size
        for (i in 0..<n) if (openups[i] != null && openups[i]!!.index == idx.toInt()) return openups[i]
        for (i in 0..<n) if (openups[i] == null) {
            val created = UpValue(stack as Array<LuaValue?>, (idx).toInt())
            openups[i] = created
            return created
        }
        error("No space for upvalue")
        return null
    }

    protected fun getUpvalue(i: Int): LuaValue {
        return (upValues[i]!!.getValue())!!
    }

    protected fun setUpvalue(i: Int, v: LuaValue?) {
        upValues[i]!!.setValue(v)
    }

    override fun name(): String {
        return "<" + p.shortsource() + ":" + p.linedefined + ">"
    }


    companion object {
        private val NOUPVALUES: Array<UpValue?> = arrayOfNulls<UpValue>(0)
    }
}
