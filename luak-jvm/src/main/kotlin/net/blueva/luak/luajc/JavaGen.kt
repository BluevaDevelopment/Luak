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

import net.blueva.luak.LocVars
import net.blueva.luak.Lua
import net.blueva.luak.Prototype
import net.blueva.luak.Upvaldesc

/**
 * TODO:
 * propogate constants
 * loader can find inner classes
 */
class JavaGen private constructor(pi: ProtoInfo, val classname: String?, filename: String?, genmain: Boolean) {
    val bytecode: ByteArray?
    val inners: Array<JavaGen?>?

    constructor(p: Prototype?, classname: String?, filename: String?, genmain: Boolean) : this(
        ProtoInfo(p!!, classname),
        classname,
        filename,
        genmain
    )

    init {
        // build this class
        val builder = JavaBuilder(pi, classname, filename)
        scanInstructions(pi, classname, builder)
        for (i in pi.prototype.locvars.indices) {
            val l: LocVars = pi.prototype.locvars[i]!!
            builder.setVarStartEnd(i, l.startpc, l.endpc, l.varname!!.tojstring())
        }
        this.bytecode = builder.completeClass(genmain)


        // build sub-prototypes
        if (pi.subprotos != null) {
            val n = pi.subprotos.size
            inners = arrayOfNulls<JavaGen>(n)
            for (i in 0..<n) {
                val sub = pi.subprotos[i]!!
                inners[i] = JavaGen(sub, sub.name, filename, false)
            }
        } else {
            inners = null
        }
    }

    private fun scanInstructions(pi: ProtoInfo, classname: String?, builder: JavaBuilder) {
        val p = pi.prototype
        var vresultbase = -1

        for (bi in pi.blocklist.indices) {
            val b0 = pi.blocklist[bi]!!

            // convert upvalues that are phi-variables
            for (slot in 0..<p.maxstacksize) {
                val pc = b0.pc0
                val c = pi.isUpvalueCreate(pc, slot)
                if (c == true && pi.vars[slot]!![pc]!!.isPhiVar()) builder.convertToUpvalue(pc, slot)
            }

            for (pc in b0.pc0..b0.pc1) {
                val pc0 = pc // closure changes pc
                val ins = p.code!![pc]
                val line = if (pc < p.lineinfo!!.size) p.lineinfo!![pc] else -1
                val o = Lua.GET_OPCODE(ins)
                var a = Lua.GETARG_A(ins)
                var b = Lua.GETARG_B(ins)
                val bx = Lua.GETARG_Bx(ins)
                val sbx = Lua.GETARG_sBx(ins)
                val c = Lua.GETARG_C(ins)

                when (o) {
                    Lua.OP_GETUPVAL -> {
                        builder.loadUpvalue(b)
                        builder.storeLocal(pc, a)
                    }

                    Lua.OP_SETUPVAL -> builder.storeUpvalue(pc, b, a)
                    Lua.OP_NEWTABLE -> {
                        builder.newTable(b, c)
                        builder.storeLocal(pc, a)
                    }

                    Lua.OP_MOVE -> {
                        builder.loadLocal(pc, b)
                        builder.storeLocal(pc, a)
                    }

                    Lua.OP_UNM, Lua.OP_NOT, Lua.OP_LEN -> {
                        builder.loadLocal(pc, b)
                        builder.unaryop(o)
                        builder.storeLocal(pc, a)
                    }

                    Lua.OP_LOADK -> {
                        builder.loadConstant(p.k!![bx]!!)
                        builder.storeLocal(pc, a)
                    }

                    Lua.OP_LOADNIL -> {
                        builder.loadNil()
                        while (b >= 0) {
                            if (b > 0) builder.dup()
                            builder.storeLocal(pc, a)
                            a++
                            b--
                        }
                    }

                    Lua.OP_GETTABUP -> {
                        builder.loadUpvalue(b)
                        loadLocalOrConstant(p, builder, pc, c)
                        builder.getTable()
                        builder.storeLocal(pc, a)
                    }

                    Lua.OP_GETTABLE -> {
                        builder.loadLocal(pc, b)
                        loadLocalOrConstant(p, builder, pc, c)
                        builder.getTable()
                        builder.storeLocal(pc, a)
                    }

                    Lua.OP_SETTABUP -> {
                        builder.loadUpvalue(a)
                        loadLocalOrConstant(p, builder, pc, b)
                        loadLocalOrConstant(p, builder, pc, c)
                        builder.setTable()
                    }

                    Lua.OP_SETTABLE -> {
                        builder.loadLocal(pc, a)
                        loadLocalOrConstant(p, builder, pc, b)
                        loadLocalOrConstant(p, builder, pc, c)
                        builder.setTable()
                    }

                    Lua.OP_ADD, Lua.OP_SUB, Lua.OP_MUL, Lua.OP_DIV, Lua.OP_MOD, Lua.OP_POW -> {
                        loadLocalOrConstant(p, builder, pc, b)
                        loadLocalOrConstant(p, builder, pc, c)
                        builder.binaryop(o)
                        builder.storeLocal(pc, a)
                    }

                    Lua.OP_SELF -> {
                        builder.loadLocal(pc, b)
                        builder.dup()
                        builder.storeLocal(pc, a + 1)
                        loadLocalOrConstant(p, builder, pc, c)
                        builder.getTable()
                        builder.storeLocal(pc, a)
                    }

                    Lua.OP_CONCAT -> {
                        var k = b
                        while (k <= c) {
                            builder.loadLocal(pc, k)
                            k++
                        }
                        if (c > b + 1) {
                            builder.tobuffer()
                            var k = c
                            while (--k >= b) {
                                builder.concatbuffer()
                            }
                            builder.tovalue()
                        } else {
                            builder.concatvalue()
                        }
                        builder.storeLocal(pc, a)
                    }

                    Lua.OP_LOADBOOL -> {
                        builder.loadBoolean(b != 0)
                        builder.storeLocal(pc, a)
                        if (c != 0) builder.addBranch(pc, JavaBuilder.Companion.BRANCH_GOTO, pc + 2)
                    }

                    Lua.OP_JMP -> {
                        if (a > 0) {
                            var i = a - 1
                            while (i < pi.openups.size) {
                                builder.closeUpvalue(pc, i)
                                ++i
                            }
                        }
                        builder.addBranch(pc, JavaBuilder.Companion.BRANCH_GOTO, pc + 1 + sbx)
                    }

                    Lua.OP_EQ, Lua.OP_LT, Lua.OP_LE -> {
                        loadLocalOrConstant(p, builder, pc, b)
                        loadLocalOrConstant(p, builder, pc, c)
                        builder.compareop(o)
                        builder.addBranch(
                            pc,
                            (if (a != 0) JavaBuilder.Companion.BRANCH_IFEQ else JavaBuilder.Companion.BRANCH_IFNE),
                            pc + 2
                        )
                    }

                    Lua.OP_TEST -> {
                        builder.loadLocal(pc, a)
                        builder.toBoolean()
                        builder.addBranch(
                            pc,
                            (if (c != 0) JavaBuilder.Companion.BRANCH_IFEQ else JavaBuilder.Companion.BRANCH_IFNE),
                            pc + 2
                        )
                    }

                    Lua.OP_TESTSET -> {
                        builder.loadLocal(pc, b)
                        builder.toBoolean()
                        builder.addBranch(
                            pc,
                            (if (c != 0) JavaBuilder.Companion.BRANCH_IFEQ else JavaBuilder.Companion.BRANCH_IFNE),
                            pc + 2
                        )
                        builder.loadLocal(pc, b)
                        builder.storeLocal(pc, a)
                    }

                    Lua.OP_CALL -> {
                        /*	A B C	R(A), ... ,R(A+C-2):= R(A)(R(A+1), ... ,R(A+B-1)) */

                        // load function
                        builder.loadLocal(pc, a)


                        // load args
                        var narg = b - 1
                        when (narg) {
                            0, 1, 2, 3 -> {
                                var i = 1
                                while (i < b) {
                                    builder.loadLocal(pc, a + i)
                                    i++
                                }
                            }

                            -1 -> {
                                loadVarargResults(builder, pc, a + 1, vresultbase)
                                narg = -1
                            }

                            else -> {
                                builder.newVarargs(pc, a + 1, b - 1)
                                narg = -1
                            }
                        }


                        // call or invoke
                        val useinvoke = narg < 0 || c < 1 || c > 2
                        if (useinvoke) builder.invoke(narg)
                        else builder.call(narg)


                        // handle results
                        when (c) {
                            1 -> builder.pop()
                            2 -> {
                                if (useinvoke) builder.arg(1)
                                builder.storeLocal(pc, a)
                            }

                            0 -> {
                                vresultbase = a
                                builder.storeVarresult()
                            }

                            else -> {
                                var i = 1
                                while (i < c) {
                                    if (i + 1 < c) builder.dup()
                                    builder.arg(i)
                                    builder.storeLocal(pc, a + i - 1)
                                    i++
                                }
                            }
                        }
                    }

                    Lua.OP_TAILCALL -> {
                        // load function
                        builder.loadLocal(pc, a)


                        // load args
                        when (b) {
                            1 -> builder.loadNone()
                            2 -> builder.loadLocal(pc, a + 1)
                            0 -> loadVarargResults(builder, pc, a + 1, vresultbase)
                            else -> builder.newVarargs(pc, a + 1, b - 1)
                        }
                        builder.newTailcallVarargs()
                        builder.areturn()
                    }

                    Lua.OP_RETURN -> {
                        if (c == 1) {
                            builder.loadNone()
                        } else {
                            when (b) {
                                0 -> loadVarargResults(builder, pc, a, vresultbase)
                                1 -> builder.loadNone()
                                2 -> builder.loadLocal(pc, a)
                                else -> builder.newVarargs(pc, a, b - 1)
                            }
                        }
                        builder.areturn()
                    }

                    Lua.OP_FORPREP -> {
                        builder.loadLocal(pc, a)
                        builder.loadLocal(pc, a + 2)
                        builder.binaryop(Lua.OP_SUB)
                        builder.storeLocal(pc, a)
                        builder.addBranch(pc, JavaBuilder.Companion.BRANCH_GOTO, pc + 1 + sbx)
                    }

                    Lua.OP_FORLOOP -> {
                        builder.loadLocal(pc, a)
                        builder.loadLocal(pc, a + 2)
                        builder.binaryop(Lua.OP_ADD)
                        builder.dup()
                        builder.dup()
                        builder.storeLocal(pc, a)
                        builder.storeLocal(pc, a + 3)
                        builder.loadLocal(pc, a + 1) // limit
                        builder.loadLocal(pc, a + 2) // step
                        builder.testForLoop()
                        builder.addBranch(pc, JavaBuilder.Companion.BRANCH_IFNE, pc + 1 + sbx)
                    }

                    Lua.OP_TFORCALL -> {
                        builder.loadLocal(pc, a)
                        builder.loadLocal(pc, a + 1)
                        builder.loadLocal(pc, a + 2)
                        builder.invoke(2)
                        // Results start after the four control values.
                        var i = 1
                        while (i <= c) {
                            if (i < c) builder.dup()
                            builder.arg(i)
                            builder.storeLocal(pc, a + 3 + i)
                            i++
                        }
                    }

                    Lua.OP_TFORLOOP -> {
                        builder.loadLocal(pc, a + 2)
                        builder.dup()
                        builder.storeLocal(pc, a)
                        builder.isNil()
                        builder.addBranch(pc, JavaBuilder.Companion.BRANCH_IFEQ, pc + 1 + sbx)
                    }

                    Lua.OP_SETLIST -> {
                        var index0 = (c - 1) * Lua.LFIELDS_PER_FLUSH + 1
                        builder.loadLocal(pc, a)
                        if (b == 0) {
                            val nstack = vresultbase - (a + 1)
                            if (nstack > 0) {
                                builder.setlistStack(pc, a + 1, index0, nstack)
                                index0 += nstack
                            }
                            builder.setlistVarargs(index0, vresultbase)
                        } else {
                            builder.setlistStack(pc, a + 1, index0, b)
                            builder.pop()
                        }
                    }

                    Lua.OP_CLOSURE -> {
                        val newp: Prototype = p.p!![bx]!!
                        val nup = newp.upvalues!!.size
                        val protoname = pi.subprotos!![bx]!!.name
                        builder.closureCreate(protoname!!)
                        if (nup > 0) builder.dup()
                        builder.storeLocal(pc, a)
                        var up = 0
                        while (up < nup) {
                            if (up + 1 < nup) builder.dup()
                            val u: Upvaldesc = newp.upvalues!![up]!!
                            if (u.instack) builder.closureInitUpvalueFromLocal(protoname, up, pc, u.idx.toInt())
                            else builder.closureInitUpvalueFromUpvalue(protoname, up, u.idx.toInt())
                            ++up
                        }
                    }

                    Lua.OP_VARARG -> if (b == 0) {
                        builder.loadVarargs()
                        builder.storeVarresult()
                        vresultbase = a
                    } else {
                        var i = 1
                        while (i < b) {
                            builder.loadVarargs(i)
                            builder.storeLocal(pc, a)
                            ++a
                            ++i
                        }
                    }
                }


                // let builder process branch instructions
                builder.onEndOfLuaInstruction(pc0, line)
            }
        }
    }

    private fun loadVarargResults(builder: JavaBuilder, pc: Int, a: Int, vresultbase: Int) {
        if (vresultbase <= a) {
            builder.loadVarresult()
            builder.subargs(a + 1 - vresultbase)
        } else if (vresultbase == a) {
            builder.loadVarresult()
        } else {
            builder.newVarargsVarresult(pc, a, vresultbase - a)
        }
    }

    private fun loadLocalOrConstant(p: Prototype, builder: JavaBuilder, pc: Int, borc: Int) {
        if (borc <= 0xff) builder.loadLocal(pc, borc)
        else builder.loadConstant(p.k!![borc and 0xff]!!)
    }
}
