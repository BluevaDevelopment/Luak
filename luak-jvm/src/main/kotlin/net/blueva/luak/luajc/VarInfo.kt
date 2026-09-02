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

open class VarInfo(// where assigned
    val slot: Int, // where assigned, or -1 if for block inputs
    val pc: Int
) {
    var upvalue: UpvalInfo? = null // not null if this var is an upvalue
    var allocupvalue: Boolean = false // true if this variable allocates r/w upvalue

    // storage
    var isreferenced: Boolean = false // true if this variable is refenced by some

    // opcode

    override fun toString(): String {
        return if (slot < 0) "x.x" else (slot.toString() + "." + pc)
    }

    /** Return replacement variable if there is exactly one value possible,
     * otherwise compute entire collection of variables and return null.
     * Computes the list of aall variable values, and saves it for the future.
     * 
     * @return new Variable to replace with if there is only one value, or null to leave alone.
     */
    open fun resolvePhiVariableValues(): VarInfo? {
        return null
    }

    protected open fun collectUniqueValues(visitedBlocks: MutableSet<Any?>?, vars: MutableSet<Any?>) {
        vars.add(this)
    }

    open fun isPhiVar(): Boolean {
        return false
    }

    private class ParamVarInfo(slot: Int, pc: Int) : VarInfo(slot, pc) {
        override fun toString(): String {
            return slot.toString() + ".p"
        }
    }

    private class NilVarInfo(slot: Int, pc: Int) : VarInfo(slot, pc) {
        override fun toString(): String {
            return "nil"
        }
    }

    private class PhiVarInfo(private val pi: ProtoInfo, slot: Int, pc: Int) : VarInfo(slot, pc) {
        var values: Array<VarInfo?>? = null

        override fun isPhiVar(): Boolean {
            return true
        }

        override fun toString(): String {
            val sb = StringBuffer()
            sb.append(super.toString())
            sb.append("={")
            var i = 0
            val n = (if (values != null) values!!.size else 0)
            while (i < n) {
                if (i > 0) sb.append(",")
                sb.append(values!![i].toString())
                i++
            }
            sb.append("}")
            return sb.toString()
        }

        override fun resolvePhiVariableValues(): VarInfo? {
            val visitedBlocks: MutableSet<Any?> = HashSet<Any?>()
            val vars: MutableSet<Any?> = HashSet<Any?>()
            this.collectUniqueValues(visitedBlocks, vars)
            if (vars.contains(INVALID)) return INVALID
            val n = vars.size
            val it: MutableIterator<Any?> = vars.iterator()
            if (n == 1) {
                val v = it.next() as VarInfo
                v.isreferenced = v.isreferenced or this.isreferenced
                return v
            }
            this.values = arrayOfNulls<VarInfo>(n)
            for (i in 0..<n) {
                this.values!![i] = it.next() as VarInfo?
                this.values!![i]!!.isreferenced = this.values!![i]!!.isreferenced or this.isreferenced
            }
            return null
        }

        override fun collectUniqueValues(visitedBlocks: MutableSet<Any?>?, vars: MutableSet<Any?>) {
            val b = pi.blocks[pc]!!
            if (pc == 0) vars.add(pi.params[slot])
            val prev = b.prev
            var i = 0
            val n = if (prev != null) prev.size else 0
            while (i < n) {
                val bp = prev!![i]
                if (bp != null && visitedBlocks != null && !visitedBlocks.contains(bp)) {
                    visitedBlocks.add(bp)
                    val v = pi.vars[slot]!![bp.pc1]
                    if (v != null) v.collectUniqueValues(visitedBlocks, vars)
                }
                i++
            }
        }
    }

    companion object {
        var INVALID: VarInfo = VarInfo(-1, -1)

        fun PARAM(slot: Int): VarInfo {
            return ParamVarInfo(slot, -1)
        }

        fun NIL(slot: Int): VarInfo {
            return NilVarInfo(slot, -1)
        }

        fun PHI(pi: ProtoInfo, slot: Int, pc: Int): VarInfo {
            return PhiVarInfo(pi, slot, pc)
        }
    }
}