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
package net.blueva.luak.luajc

import net.blueva.luak.Lua
import net.blueva.luak.Print
import net.blueva.luak.Prototype
import net.blueva.luak.Upvaldesc
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.*
import kotlin.Any
import kotlin.Array
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.IntArray
import kotlin.String
import kotlin.arrayOf
import kotlin.arrayOfNulls
import kotlin.plus
import kotlin.run
import kotlin.toString

/**
 * Prototype information for static single-assignment analysis
 */
class ProtoInfo private constructor(// the prototype that this info is about
    val prototype: Prototype, val name: String?, u: Array<UpvalInfo?>?
) {
    val subprotos: Array<ProtoInfo?>? // one per enclosed prototype, or null
    val blocks: Array<BasicBlock?> // basic block analysis of code branching
    val blocklist: Array<BasicBlock?> // blocks in breadth-first order
    val params: Array<VarInfo> // Parameters and initial values of stack variables
    val vars: Array<Array<VarInfo?>?> // Each variable
    val upvals: Array<UpvalInfo?>? // from outer scope
    val openups: Array<Array<UpvalInfo?>?> // per slot, upvalues allocated by this prototype

    // A main chunk proto info.
    constructor(p: Prototype, name: String?) : this(p, name, null)

    init {
        this.upvals = if (u != null) u else arrayOf<UpvalInfo?>(UpvalInfo(this))
        this.subprotos =
            if (prototype.p != null && prototype.p!!.size > 0) arrayOfNulls<ProtoInfo>(prototype.p!!.size) else null


        // find basic blocks
        this.blocks = BasicBlock.Companion.findBasicBlocks(prototype)
        this.blocklist = BasicBlock.Companion.findLiveBlocks(blocks)


        // params are inputs to first block
        this.params = Array(prototype.maxstacksize) { slot -> VarInfo.Companion.PARAM(slot) }


        // find variables
        this.vars = findVariables()
        replaceTrivialPhiVariables()

        // find upvalues, create sub-prototypes
        this.openups = arrayOfNulls<Array<UpvalInfo?>>(prototype.maxstacksize)
        findUpvalues()
    }

    override fun toString(): String {
        val sb = StringBuffer()


        // prototpye name
        sb.append("proto '" + name + "'\n")


        // upvalues from outer scopes
        run {
            var i = 0
            val n = (if (upvals != null) upvals.size else 0)
            while (i < n) {
                sb.append(" up[" + i + "]: " + upvals!![i] + "\n")
                i++
            }
        }


        // basic blocks
        for (i in blocklist.indices) {
            val b = blocklist[i]!!
            val pc0 = b.pc0
            sb.append("  block " + b.toString())
            appendOpenUps(sb, -1)


            // instructions
            for (pc in pc0..b.pc1) {
                // open upvalue storage

                appendOpenUps(sb, pc)


                // opcode
                sb.append("     ")
                for (j in 0..<prototype.maxstacksize) {
                    val v = vars[j]!![pc]
                    val u =
                        (if (v == null) "" else {
                            val vu = v.upvalue
                            if (vu != null) if (!vu.rw) "[C] " else (if (v.allocupvalue && v.pc == pc) "[*] " else "[]  ") else "    "
                        })
                    val s: String? = if (v == null) "null   " else v.toString()
                    sb.append(s + u)
                }
                sb.append("  ")
                val baos = ByteArrayOutputStream()
                val ops: PrintStream? = Print.ps
                Print.ps = PrintStream(baos)
                try {
                    Print.printOpCode(prototype, pc)
                } finally {
                    Print.ps.close()
                    Print.ps = ops!!
                }
                sb.append(baos.toString())
                sb.append("\n")
            }
        }


        // nested functions
        var i = 0
        val n = if (subprotos != null) subprotos.size else 0
        while (i < n) {
            sb.append(subprotos!![i].toString())
            i++
        }

        return sb.toString()
    }

    private fun appendOpenUps(sb: StringBuffer, pc: Int) {
        for (j in 0..<prototype.maxstacksize) {
            val v = (if (pc < 0) params[j] else vars[j]!![pc])
            if (v != null && v.pc == pc && v.allocupvalue) {
                sb.append("    open: " + v.upvalue + "\n")
            }
        }
    }

    private fun findVariables(): Array<Array<VarInfo?>?> {
        // create storage for variables.

        val n = prototype.code!!.size
        val m = prototype.maxstacksize
        val v = Array<Array<VarInfo?>?>(m) { arrayOfNulls<VarInfo?>(n) }


        // process instructions
        for (bi in blocklist.indices) {
            val b0 = blocklist[bi]!!


            // input from previous blocks
            val prev = b0.prev
            val nprev = if (prev != null) prev.size else 0
            for (slot in 0..<m) {
                var `var`: VarInfo? = null
                if (nprev == 0) `var` = params[slot]
                else if (nprev == 1) `var` = v[slot]!![prev!![0]!!.pc1]
                else {
                    for (i in 0..<nprev) {
                        val bp = prev!![i]!!
                        if (v[slot]!![bp.pc1] === VarInfo.Companion.INVALID) `var` = VarInfo.Companion.INVALID
                    }
                }
                if (`var` == null) `var` = VarInfo.Companion.PHI(this, slot, b0.pc0)
                v[slot]!![b0.pc0] = `var`!!
            }

            // process instructions for this basic block
            for (pc in b0.pc0..b0.pc1) {
                // propogate previous values except at block boundaries

                if (pc > b0.pc0) propogateVars(v, pc - 1, pc)

                var a: Int
                var b: Int
                val c: Int
                val ins = prototype.code!![pc]
                val op = Lua.GET_OPCODE(ins)


                // account for assignments, references and invalidations
                when (op) {
                    Lua.OP_LOADK, Lua.OP_LOADBOOL, Lua.OP_GETUPVAL, Lua.OP_NEWTABLE -> {
                        a = Lua.GETARG_A(ins)
                        v[a]!![pc] = VarInfo(a, pc)
                    }

                    Lua.OP_MOVE, Lua.OP_UNM, Lua.OP_NOT, Lua.OP_LEN, Lua.OP_TESTSET -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        v[b]!![pc]!!.isreferenced = true
                        v[a]!![pc] = VarInfo(a, pc)
                    }

                    Lua.OP_ADD, Lua.OP_SUB, Lua.OP_MUL, Lua.OP_DIV, Lua.OP_MOD, Lua.OP_POW -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        c = Lua.GETARG_C(ins)
                        if (!Lua.ISK(b)) v[b]!![pc]!!.isreferenced = true
                        if (!Lua.ISK(c)) v[c]!![pc]!!.isreferenced = true
                        v[a]!![pc] = VarInfo(a, pc)
                    }

                    Lua.OP_SETTABLE -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        c = Lua.GETARG_C(ins)
                        v[a]!![pc]!!.isreferenced = true
                        if (!Lua.ISK(b)) v[b]!![pc]!!.isreferenced = true
                        if (!Lua.ISK(c)) v[c]!![pc]!!.isreferenced = true
                    }

                    Lua.OP_SETTABUP -> {
                        b = Lua.GETARG_B(ins)
                        c = Lua.GETARG_C(ins)
                        if (!Lua.ISK(b)) v[b]!![pc]!!.isreferenced = true
                        if (!Lua.ISK(c)) v[c]!![pc]!!.isreferenced = true
                    }

                    Lua.OP_CONCAT -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        c = Lua.GETARG_C(ins)
                        while (b <= c) {
                            v[b]!![pc]!!.isreferenced = true
                            b++
                        }
                        v[a]!![pc] = VarInfo(a, pc)
                    }

                    Lua.OP_FORPREP -> {
                        a = Lua.GETARG_A(ins)
                        v[a + 2]!![pc]!!.isreferenced = true
                        v[a]!![pc] = VarInfo(a, pc)
                    }

                    Lua.OP_GETTABLE -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        c = Lua.GETARG_C(ins)
                        v[b]!![pc]!!.isreferenced = true
                        if (!Lua.ISK(c)) v[c]!![pc]!!.isreferenced = true
                        v[a]!![pc] = VarInfo(a, pc)
                    }

                    Lua.OP_GETTABUP -> {
                        a = Lua.GETARG_A(ins)
                        c = Lua.GETARG_C(ins)
                        if (!Lua.ISK(c)) v[c]!![pc]!!.isreferenced = true
                        v[a]!![pc] = VarInfo(a, pc)
                    }

                    Lua.OP_SELF -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        c = Lua.GETARG_C(ins)
                        v[b]!![pc]!!.isreferenced = true
                        if (!Lua.ISK(c)) v[c]!![pc]!!.isreferenced = true
                        v[a]!![pc] = VarInfo(a, pc)
                        v[a + 1]!![pc] = VarInfo(a + 1, pc)
                    }

                    Lua.OP_FORLOOP -> {
                        a = Lua.GETARG_A(ins)
                        v[a]!![pc]!!.isreferenced = true
                        v[a + 2]!![pc]!!.isreferenced = true
                        v[a]!![pc] = VarInfo(a, pc)
                        v[a]!![pc]!!.isreferenced = true
                        v[a + 1]!![pc]!!.isreferenced = true
                        v[a + 3]!![pc] = VarInfo(a + 3, pc)
                    }

                    Lua.OP_LOADNIL -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        while (b-- >= 0) {
                            v[a]!![pc] = VarInfo(a, pc)
                            a++
                        }
                    }

                    Lua.OP_VARARG -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        var j = 1
                        while (j < b) {
                            v[a]!![pc] = VarInfo(a, pc)
                            j++
                            a++
                        }
                        if (b == 0) while (a < m) {
                            v[a]!![pc] = VarInfo.Companion.INVALID
                            a++
                        }
                    }

                    Lua.OP_CALL -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        c = Lua.GETARG_C(ins)
                        v[a]!![pc]!!.isreferenced = true
                        v[a]!![pc]!!.isreferenced = true
                        var i = 1
                        while (i <= b - 1) {
                            v[a + i]!![pc]!!.isreferenced = true
                            i++
                        }
                        var j = 0
                        while (j <= c - 2) {
                            v[a]!![pc] = VarInfo(a, pc)
                            j++
                            a++
                        }
                        while (a < m) {
                            v[a]!![pc] = VarInfo.Companion.INVALID
                            a++
                        }
                    }

                    Lua.OP_TFORCALL -> {
                        a = Lua.GETARG_A(ins)
                        c = Lua.GETARG_C(ins)
                        v[a++]!![pc]!!.isreferenced = true
                        v[a++]!![pc]!!.isreferenced = true
                        v[a++]!![pc]!!.isreferenced = true
                        a++ // the closing control value, read only at loop end
                        var j = 0
                        while (j < c) {
                            v[a]!![pc] = VarInfo(a, pc)
                            j++
                            a++
                        }
                        while (a < m) {
                            v[a]!![pc] = VarInfo.Companion.INVALID
                            a++
                        }
                    }

                    Lua.OP_TFORLOOP -> {
                        a = Lua.GETARG_A(ins)
                        v[a + 2]!![pc]!!.isreferenced = true
                        v[a]!![pc] = VarInfo(a, pc)
                    }

                    Lua.OP_TAILCALL -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        v[a]!![pc]!!.isreferenced = true
                        var i = 1
                        while (i <= b - 1) {
                            v[a + i]!![pc]!!.isreferenced = true
                            i++
                        }
                    }

                    Lua.OP_RETURN -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        var i = 0
                        while (i <= b - 2) {
                            v[a + i]!![pc]!!.isreferenced = true
                            i++
                        }
                    }

                    Lua.OP_CLOSURE -> {
                        /*	A Bx	R(A) := closure(KPROTO[Bx], R(A), ... ,R(A+n))	*/
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_Bx(ins)
                        val upvalues: Array<Upvaldesc?> = prototype.p!![b]!!.upvalues!!
                        var k = 0
                        val nups = upvalues.size
                        while (k < nups) {
                            if (upvalues[k]!!.instack) v[upvalues[k]!!.idx.toInt()]!![pc]!!.isreferenced = true
                            ++k
                        }
                        v[a]!![pc] = VarInfo(a, pc)
                    }

                    Lua.OP_SETLIST -> {
                        a = Lua.GETARG_A(ins)
                        b = Lua.GETARG_B(ins)
                        v[a]!![pc]!!.isreferenced = true
                        var i = 1
                        while (i <= b) {
                            v[a + i]!![pc]!!.isreferenced = true
                            i++
                        }
                    }

                    Lua.OP_SETUPVAL, Lua.OP_TEST -> {
                        a = Lua.GETARG_A(ins)
                        v[a]!![pc]!!.isreferenced = true
                    }

                    Lua.OP_EQ, Lua.OP_LT, Lua.OP_LE -> {
                        b = Lua.GETARG_B(ins)
                        c = Lua.GETARG_C(ins)
                        if (!Lua.ISK(b)) v[b]!![pc]!!.isreferenced = true
                        if (!Lua.ISK(c)) v[c]!![pc]!!.isreferenced = true
                    }

                    Lua.OP_JMP -> {
                        a = Lua.GETARG_A(ins)
                        if (a > 0) {
                            --a
                            while (a < m) {
                                v[a]!![pc] = VarInfo.Companion.INVALID
                                a++
                            }
                        }
                    }

                    else -> throw IllegalStateException("unhandled opcode: " + ins)
                }
            }
        }
        return v
    }

    private fun replaceTrivialPhiVariables() {
        for (i in blocklist.indices) {
            val b0 = blocklist[i]!!
            for (slot in 0..<prototype.maxstacksize) {
                val vold = vars[slot]!![b0.pc0]!!
                val vnew = vold.resolvePhiVariableValues()
                if (vnew != null) substituteVariable(slot, vold, vnew)
            }
        }
    }

    private fun substituteVariable(slot: Int, vold: VarInfo?, vnew: VarInfo?) {
        var i = 0
        val n = prototype.code!!.size
        while (i < n) {
            replaceAll(vars[slot]!!, vars[slot]!!.size, vold, vnew)
            i++
        }
    }

    private fun replaceAll(v: Array<VarInfo?>, n: Int, vold: VarInfo?, vnew: VarInfo?) {
        for (i in 0..<n) if (v[i] === vold) v[i] = vnew
    }

    private fun findUpvalues() {
        val code: IntArray = prototype.code!!
        val n = code.size


        // propogate to inner prototypes
        val names = findInnerprotoNames()
        for (pc in 0..<n) {
            if (Lua.GET_OPCODE(code[pc]) == Lua.OP_CLOSURE) {
                val bx = Lua.GETARG_Bx(code[pc])
                val newp: Prototype = prototype.p!![bx]!!
                val newu = arrayOfNulls<UpvalInfo>(newp.upvalues!!.size)
                val newname = name + "$" + names!![bx]
                for (j in newp.upvalues!!.indices) {
                    val u: Upvaldesc = newp.upvalues!![j]!!
                    newu[j] = if (u.instack) findOpenUp(pc, u.idx.toInt()) else upvals!![u.idx.toInt()]
                }
                subprotos!![bx] = ProtoInfo(newp, newname, newu)
            }
        }


        // mark all upvalues that are written locally as read/write
        for (pc in 0..<n) {
            if (Lua.GET_OPCODE(code[pc]) == Lua.OP_SETUPVAL) upvals!![Lua.GETARG_B(code[pc])]!!.rw = true
        }
    }

    private fun findOpenUp(pc: Int, slot: Int): UpvalInfo? {
        if (openups[slot] == null) openups[slot] = arrayOfNulls<UpvalInfo>(prototype.code!!.size)
        if (openups[slot]!![pc] != null) return openups[slot]!![pc]
        val u = UpvalInfo(this, pc, slot)
        var i = 0
        val n = prototype.code!!.size
        while (i < n) {
            if (vars[slot]!![i] != null && vars[slot]!![i]!!.upvalue === u) openups[slot]!![i] = u
            ++i
        }
        return u
    }

    fun isUpvalueAssign(pc: Int, slot: Int): Boolean {
        val v = if (pc < 0) params[slot] else vars[slot]!![pc]
        val vu = v?.upvalue
        return vu != null && vu.rw
    }

    fun isUpvalueCreate(pc: Int, slot: Int): Boolean {
        val v = if (pc < 0) params[slot] else vars[slot]!![pc]
        val vu = v?.upvalue
        return vu != null && vu.rw && v!!.allocupvalue && pc == v.pc
    }

    fun isUpvalueRefer(pc: Int, slot: Int): Boolean {
        // special case when both refer and assign in same instruction
        var pc = pc
        if (pc > 0 && vars[slot]!![pc] != null && vars[slot]!![pc]!!.pc == pc && vars[slot]!![pc - 1] != null) pc -= 1
        val v = if (pc < 0) params[slot] else vars[slot]!![pc]
        val vu = v?.upvalue
        return vu != null && vu.rw
    }

    fun isInitialValueUsed(slot: Int): Boolean {
        val v = params[slot]
        return v.isreferenced
    }

    fun isReadWriteUpvalue(u: UpvalInfo): Boolean {
        return u.rw
    }

    private fun findInnerprotoNames(): Array<String?>? {
        if (prototype.p!!.size <= 0) return null
        // find all the prototype names
        val names = arrayOfNulls<String>(prototype.p!!.size)
        val used: Hashtable<String?, Boolean> = Hashtable<String?, Boolean>()
        val code: IntArray = prototype.code!!
        val n = code.size
        for (pc in 0..<n) {
            if (Lua.GET_OPCODE(code[pc]) == Lua.OP_CLOSURE) {
                val bx = Lua.GETARG_Bx(code[pc])
                var name: String? = null
                val i = code[pc + 1]
                when (Lua.GET_OPCODE(i)) {
                    Lua.OP_SETTABLE, Lua.OP_SETTABUP -> {
                        val b = Lua.GETARG_B(i)
                        if (Lua.ISK(b)) name = prototype.k!![b and 0x0ff]!!.tojstring()
                    }

                    Lua.OP_SETUPVAL -> {
                        val b = Lua.GETARG_B(i)
                        val s = prototype.upvalues!![b]!!.name
                        if (s != null) name = s.tojstring()
                    }

                    else -> {
                        val a = Lua.GETARG_A(code[pc])
                        val s = prototype.getlocalname(a + 1, pc + 1)
                        if (s != null) name = s.tojstring()
                    }
                }
                name = if (name != null) toJavaClassPart(name) else bx.toString()
                if (used.containsKey(name)) {
                    val basename: String? = name
                    var count = 1
                    do {
                        name = basename + '$' + count++
                    } while (used.containsKey(name))
                }
                used.put(name, true)
                names[bx] = name
            }
        }
        return names
    }

    companion object {
        private fun propogateVars(v: Array<Array<VarInfo?>?>, pcfrom: Int, pcto: Int) {
            var j = 0
            val m = v.size
            while (j < m) {
                v[j]!![pcto] = v[j]!![pcfrom]
                j++
            }
        }

        private fun toJavaClassPart(s: String): String {
            val n = s.length
            val sb = StringBuffer(n)
            for (i in 0..<n) sb.append(if (Character.isJavaIdentifierPart(s.get(i))) s.get(i) else '_')
            return sb.toString()
        }
    }
}
