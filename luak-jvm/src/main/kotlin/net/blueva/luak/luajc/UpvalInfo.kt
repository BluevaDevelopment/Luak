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

class UpvalInfo {
    var pi: ProtoInfo // where defined
    var slot: Int // where defined
    var nvars: Int // number of vars involved
    var `var`: Array<VarInfo?>? // list of vars
    var rw: Boolean // read-write

    // Upval info representing the implied context containing only the environment.
    constructor(pi: ProtoInfo) {
        this.pi = pi
        this.slot = 0
        this.nvars = 1
        this.`var` = arrayOf<VarInfo?>(VarInfo.Companion.PARAM(0))
        this.rw = false
    }

    constructor(pi: ProtoInfo, pc: Int, slot: Int) {
        this.pi = pi
        this.slot = slot
        this.nvars = 0
        this.`var` = null
        includeVarAndPosteriorVars(pi.vars[slot]!![pc])
        for (i in 0..<nvars) `var`!![i]!!.allocupvalue = testIsAllocUpvalue(`var`!![i])
        this.rw = nvars > 1
    }

    private fun includeVarAndPosteriorVars(`var`: VarInfo?): Boolean {
        if (`var` == null || `var` === VarInfo.Companion.INVALID) return false
        if (`var`.upvalue === this) return true
        `var`.upvalue = this
        appendVar(`var`)
        if (isLoopVariable(`var`)) return false
        val loopDetected = includePosteriorVarsCheckLoops(`var`)
        if (loopDetected) includePriorVarsIgnoreLoops(`var`)
        return loopDetected
    }

    private fun isLoopVariable(`var`: VarInfo): Boolean {
        if (`var`.pc >= 0) {
            when (Lua.GET_OPCODE(pi.prototype.code!![`var`.pc])) {
                Lua.OP_TFORLOOP, Lua.OP_FORLOOP -> return true
            }
        }
        return false
    }

    private fun includePosteriorVarsCheckLoops(prior: VarInfo?): Boolean {
        var loopDetected = false
        var i = 0
        val n = pi.blocklist.size
        while (i < n) {
            val b = pi.blocklist[i]!!
            val v = pi.vars[slot]!![b.pc1]!!
            if (v === prior) {
                val next = b.next
                var j = 0
                val m = if (next != null) next.size else 0
                while (j < m) {
                    val b1 = next!![j]!!
                    val v1 = pi.vars[slot]!![b1.pc0]!!
                    if (v1 !== prior) {
                        loopDetected = loopDetected or includeVarAndPosteriorVars(v1)
                        if (v1.isPhiVar()) includePriorVarsIgnoreLoops(v1)
                    }
                    j++
                }
            } else {
                for (pc in b.pc1 - 1 downTo b.pc0) {
                    if (pi.vars[slot]!![pc] === prior) {
                        loopDetected = loopDetected or includeVarAndPosteriorVars(pi.vars[slot]!![pc + 1])
                        break
                    }
                }
            }
            i++
        }
        return loopDetected
    }

    private fun includePriorVarsIgnoreLoops(poster: VarInfo?) {
        var i = 0
        val n = pi.blocklist.size
        while (i < n) {
            val b = pi.blocklist[i]!!
            val v = pi.vars[slot]!![b.pc0]!!
            if (v === poster) {
                val prev = b.prev
                var j = 0
                val m = if (prev != null) prev.size else 0
                while (j < m) {
                    val b0 = prev!![j]!!
                    val v0 = pi.vars[slot]!![b0.pc1]!!
                    if (v0 !== poster) includeVarAndPosteriorVars(v0)
                    j++
                }
            } else {
                for (pc in b.pc0 + 1..b.pc1) {
                    if (pi.vars[slot]!![pc] === poster) {
                        includeVarAndPosteriorVars(pi.vars[slot]!![pc - 1])
                        break
                    }
                }
            }
            i++
        }
    }

    private fun appendVar(v: VarInfo?) {
        if (nvars == 0) {
            `var` = arrayOfNulls<VarInfo>(1)
        } else if (nvars + 1 >= `var`!!.size) {
            val s = `var`!!
            `var` = arrayOfNulls<VarInfo>(nvars * 2 + 1)
            System.arraycopy(s, 0, `var`, 0, nvars)
        }
        `var`!![nvars++] = v
    }

    override fun toString(): String {
        val sb = StringBuffer()
        sb.append(pi.name)
        for (i in 0..<nvars) {
            sb.append(if (i > 0) "," else " ")
            sb.append(`var`!![i].toString())
        }
        if (rw) sb.append("(rw)")
        return sb.toString()
    }

    private fun testIsAllocUpvalue(v: VarInfo?): Boolean {
        var v = v
        if (v!!.pc < 0) return true
        val b = pi.blocks[v.pc]!!
        if (v.pc > b.pc0) return pi.vars[slot]!![v.pc - 1]!!.upvalue !== this
        val prev = b.prev
        if (prev == null) {
            v = pi.params[slot]
            if (v != null && v.upvalue !== this) return true
        } else {
            var i = 0
            val n = prev.size
            while (i < n) {
                val bp = prev[i]!!
                v = pi.vars[slot]!![bp.pc1]
                if (v != null && v.upvalue !== this) return true
                i++
            }
        }
        return false
    }
}
