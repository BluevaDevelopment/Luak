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
import net.blueva.luak.Prototype
import java.util.*

class BasicBlock(
    p: Prototype?, // range of program counter values for the block
    var pc1: Int
) {
    var pc0: Int
    var prev: Array<BasicBlock?>? = null // previous basic blocks (0-n of these)
    var next: Array<BasicBlock?>? = null // next basic blocks (0, 1, or 2 of these)
    var islive: Boolean = false // true if this block is used

    init {
        this.pc0 = this.pc1
    }

    override fun toString(): String {
        val sb = StringBuffer()
        sb.append(
            ((pc0 + 1).toString() + "-" + (pc1 + 1)
                    + (if (prev != null) "  prv: " + str(prev, 1) else "")
                    + (if (next != null) "  nxt: " + str(next, 0) else "")
                    + "\n")
        )
        return sb.toString()
    }

    private fun str(b: Array<BasicBlock?>?, p: Int): String {
        if (b == null) return ""
        val sb = StringBuffer()
        sb.append("(")
        var i = 0
        val n = b.size
        while (i < n) {
            if (i > 0) sb.append(",")
            sb.append((if (p == 1) b[i]!!.pc1 + 1 else b[i]!!.pc0 + 1).toString())
            i++
        }
        sb.append(")")
        return sb.toString()
    }

    private class AllocAndXRefVisitor(
        isbeg: BooleanArray, private val nnext: IntArray, private val nprev: IntArray,
        private val blocks: Array<BasicBlock?>
    ) : BranchVisitor(isbeg) {
        override fun visitBranch(pc0: Int, pc1: Int) {
            if (blocks[pc0]!!.next == null) blocks[pc0]!!.next = arrayOfNulls<BasicBlock>(nnext[pc0])
            if (blocks[pc1]!!.prev == null) blocks[pc1]!!.prev = arrayOfNulls<BasicBlock>(nprev[pc1])
            blocks[pc0]!!.next!![--nnext[pc0]] = blocks[pc1]
            blocks[pc1]!!.prev!![--nprev[pc1]] = blocks[pc0]
        }
    }

    private class CountPrevNextVistor(isbeg: BooleanArray, private val nnext: IntArray, private val nprev: IntArray) :
        BranchVisitor(isbeg) {
        override fun visitBranch(pc0: Int, pc1: Int) {
            nnext[pc0]++
            nprev[pc1]++
        }
    }

    private class MarkAndMergeVisitor(isbeg: BooleanArray, private val isend: BooleanArray) : BranchVisitor(isbeg) {
        override fun visitBranch(pc0: Int, pc1: Int) {
            isend[pc0] = true
            isbeg[pc1] = true
        }

        override fun visitReturn(pc: Int) {
            isend[pc] = true
        }
    }

    abstract class BranchVisitor(val isbeg: BooleanArray) {
        open fun visitBranch(frompc: Int, topc: Int) {}
        open fun visitReturn(atpc: Int) {}
    }

    companion object {
        fun findBasicBlocks(p: Prototype): Array<BasicBlock?> {
            // mark beginnings, endings

            val n = p.code!!.size
            val isbeg = BooleanArray(n)
            val isend = BooleanArray(n)
            isbeg[0] = true
            val bv: BranchVisitor = MarkAndMergeVisitor(isbeg, isend)
            visitBranches(p, bv) // 1st time to mark branches
            visitBranches(p, bv) // 2nd time to catch merges


            // create basic blocks
            val blocks = arrayOfNulls<BasicBlock>(n)
            var i = 0
            while (i < n) {
                isbeg[i] = true
                val b = BasicBlock(p, i)
                blocks[i] = b
                while (!isend[i] && i + 1 < n && !isbeg[i + 1]) blocks[(++i).also { b.pc1 = it }] = b
                i++
            }


            // count previous, next
            val nnext = IntArray(n)
            val nprev = IntArray(n)
            visitBranches(p, CountPrevNextVistor(isbeg, nnext, nprev))


            // allocate and cross-reference
            visitBranches(p, AllocAndXRefVisitor(isbeg, nnext, nprev, blocks))
            return blocks
        }

        fun visitBranches(p: Prototype, visitor: BranchVisitor) {
            var sbx: Int
            var j: Int
            var c: Int
            val code: IntArray = p.code!!
            val n = code.size
            var i = 0
            while (i < n) {
                val ins = code[i]
                when (Lua.GET_OPCODE(ins)) {
                    Lua.OP_LOADBOOL -> {
                        if (0 == Lua.GETARG_C(ins)) break
                        require(Lua.GET_OPCODE(code[i + 1]) != Lua.OP_JMP) { "OP_LOADBOOL followed by jump at " + i }
                        visitor.visitBranch(i, i + 2)
                        i++
                        continue
                    }

                    Lua.OP_EQ, Lua.OP_LT, Lua.OP_LE, Lua.OP_TEST, Lua.OP_TESTSET -> {
                        require(Lua.GET_OPCODE(code[i + 1]) == Lua.OP_JMP) { "test not followed by jump at " + i }
                        sbx = Lua.GETARG_sBx(code[i + 1])
                        ++i
                        j = i + sbx + 1
                        visitor.visitBranch(i, j)
                        visitor.visitBranch(i, i + 1)
                        i++
                        continue
                    }

                    Lua.OP_TFORLOOP, Lua.OP_FORLOOP -> {
                        sbx = Lua.GETARG_sBx(ins)
                        j = i + sbx + 1
                        visitor.visitBranch(i, j)
                        visitor.visitBranch(i, i + 1)
                        i++
                        continue
                    }

                    Lua.OP_JMP, Lua.OP_FORPREP -> {
                        sbx = Lua.GETARG_sBx(ins)
                        j = i + sbx + 1
                        visitor.visitBranch(i, j)
                        i++
                        continue
                    }

                    Lua.OP_TAILCALL, Lua.OP_RETURN -> {
                        visitor.visitReturn(i)
                        i++
                        continue
                    }
                }
                if (i + 1 < n && visitor.isbeg[i + 1]) visitor.visitBranch(i, i + 1)
                i++
            }
        }

        fun findLiveBlocks(blocks: Array<BasicBlock?>): Array<BasicBlock?> {
            // add reachable blocks
            val next: Vector<BasicBlock?> = Vector<BasicBlock?>()
            next.addElement(blocks[0])
            while (!next.isEmpty()) {
                val b = next.elementAt(0) as BasicBlock
                next.removeElementAt(0)
                if (!b.islive) {
                    b.islive = true
                    var i = 0
                    val n = if (b.next != null) b.next!!.size else 0
                    while (i < n) {
                        if (!b.next!![i]!!.islive) next.addElement(b.next!![i])
                        i++
                    }
                }
            }


            // create list in natural order
            val list: Vector<BasicBlock?> = Vector<BasicBlock?>()
            var i = 0
            while (i < blocks.size) {
                if (blocks[i]!!.islive) list.addElement(blocks[i])
                i = blocks[i]!!.pc1 + 1
            }


            // convert to array
            val array = arrayOfNulls<BasicBlock>(list.size)
            list.copyInto(array)
            return array
        }
    }
}