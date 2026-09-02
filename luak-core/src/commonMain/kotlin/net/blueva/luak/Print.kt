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

import net.blueva.luak.io.ByteArrayOutputStream
import net.blueva.luak.io.PrintStream
import net.blueva.luak.io.standardOutput

/**
 * Debug helper class to pretty-print lua bytecodes.
 * @see Prototype
 * 
 * @see LuaClosure
 */
class Print : Lua() {
    private fun _assert(b: Boolean) {
        if (!b) throw NullPointerException("_assert failed")
    }

    companion object {
        /** opcode names  */
        private val STRING_FOR_NULL = "null"
        var ps: PrintStream = standardOutput()

        /** String names for each lua opcode value.  */
        val OPNAMES: Array<String?> = arrayOf<String?>(
            "MOVE",
            "LOADK",
            "LOADKX",
            "LOADBOOL",
            "LOADNIL",
            "GETUPVAL",
            "GETTABUP",
            "GETTABLE",
            "SETTABUP",
            "SETUPVAL",
            "SETTABLE",
            "NEWTABLE",
            "SELF",
            "ADD",
            "SUB",
            "MUL",
            "DIV",
            "MOD",
            "POW",
            "UNM",
            "NOT",
            "LEN",
            "CONCAT",
            "JMP",
            "EQ",
            "LT",
            "LE",
            "TEST",
            "TESTSET",
            "CALL",
            "TAILCALL",
            "RETURN",
            "FORLOOP",
            "FORPREP",
            "TFORCALL",
            "TFORLOOP",
            "SETLIST",
            "CLOSURE",
            "VARARG",
            "EXTRAARG",
            "IDIV",
            "BAND",
            "BOR",
            "BXOR",
            "SHL",
            "SHR",
            "BNOT",
            "TBC",
            "ERRNNIL",
            null,
        )


        fun printString(ps: PrintStream, s: LuaString) {
            ps.print('"')
            var i = 0
            val n: Int = s.m_length
            while (i < n) {
                val c: Int = s.m_bytes[s.m_offset + i].toInt()
                if (c >= ' '.code && c <= '~'.code && c != '\"'.code && c != '\\'.code) ps.print(c.toChar())
                else {
                    when (c) {
                        '"'.code -> ps.print("\\\"")
                        '\\'.code -> ps.print("\\\\")
                        0x0007 -> ps.print("\\a")
                        '\b'.code -> ps.print("\\b")
                        '\u000C'.code -> ps.print("\\f")
                        '\t'.code -> ps.print("\\t")
                        '\r'.code -> ps.print("\\r")
                        '\n'.code -> ps.print("\\n")
                        0x000B -> ps.print("\\v")
                        else -> {
                            ps.print('\\')
                            ps.print((1000 + (0xff and c)).toString().substring(1))
                        }
                    }
                }
                i++
            }
            ps.print('"')
        }

        fun printValue(ps: PrintStream, v: LuaValue?) {
            if (v == null) {
                ps.print("null")
                return
            }
            when (v.type()) {
                LuaValue.TSTRING -> net.blueva.luak.Print.Companion.printString(ps, v as LuaString)
                else -> ps.print(v.tojstring())

            }
        }

        fun printConstant(ps: PrintStream, f: Prototype, i: Int) {
            val constants = f.k
            net.blueva.luak.Print.Companion.printValue(
                ps,
                if (constants != null && i < constants.size) constants[i] else LuaValue.valueOf("UNKNOWN_CONST_" + i)
            )
        }

        fun printUpvalue(ps: PrintStream, u: Upvaldesc) {
            ps.print("${u.idx} ")
            net.blueva.luak.Print.Companion.printValue(ps, u.name)
        }

        /**
         * Print the code in a prototype
         * @param f the [Prototype]
         */
        fun printCode(f: Prototype) {
            val code: IntArray = f.code ?: return
            var pc = 0
            val n = code.size
            while (pc < n) {
                pc = net.blueva.luak.Print.Companion.printOpCode(f, pc)
                net.blueva.luak.Print.Companion.ps.println()
                pc++
            }
        }

        /**
         * Print an opcode in a prototype
         * @param f the [Prototype]
         * @param pc the program counter to look up and print
         * @return pc same as above or changed
         */
        fun printOpCode(f: Prototype, pc: Int): Int {
            return net.blueva.luak.Print.Companion.printOpCode(net.blueva.luak.Print.Companion.ps, f, pc)
        }

        /**
         * Print an opcode in a prototype
         * @param ps the [PrintStream] to print to
         * @param f the [Prototype]
         * @param pc the program counter to look up and print
         * @return pc same as above or changed
         */
        fun printOpCode(ps: PrintStream, f: Prototype, pc: Int): Int {
            var pc = pc
            val code: IntArray = f.code ?: return pc
            val i = code[pc]
            val o: Int = GET_OPCODE(i)
            val a: Int = GETARG_A(i)
            val b: Int = GETARG_B(i)
            val c: Int = GETARG_C(i)
            val bx: Int = GETARG_Bx(i)
            val sbx: Int = GETARG_sBx(i)
            val line: Int = net.blueva.luak.Print.Companion.getline(f, pc)
            val upvalues = f.upvalues
            val protos = f.p
            ps.print("  " + (pc + 1) + "  ")
            if (line > 0) ps.print("[" + line + "]  ")
            else ps.print("[-]  ")
            if (o >= net.blueva.luak.Print.Companion.OPNAMES.size - 1) {
                ps.print("UNKNOWN_OP_" + o + "  ")
            } else {
                ps.print(net.blueva.luak.Print.Companion.OPNAMES[o].toString() + "  ")
                when (getOpMode(o)) {
                    iABC -> {
                        ps.print(a)
                        if (getBMode(o) !== OpArgN) ps.print(" " + (if (ISK(b)) (-1 - INDEXK(b)) else b))
                        if (getCMode(o) !== OpArgN) ps.print(" " + (if (ISK(c)) (-1 - INDEXK(c)) else c))
                    }

                    iABx -> if (getBMode(o) === OpArgK) {
                        ps.print(a.toString() + " " + (-1 - bx))
                    } else {
                        ps.print(a.toString() + " " + (bx))
                    }

                    iAsBx -> if (o == OP_JMP) ps.print(sbx)
                    else ps.print(a.toString() + " " + sbx)
                }
                when (o) {
                    OP_LOADK -> {
                        ps.print("  ; ")
                        net.blueva.luak.Print.Companion.printConstant(ps, f, bx)
                    }

                    OP_GETUPVAL, OP_SETUPVAL -> {
                        ps.print("  ; ")
                        if (upvalues != null && b < upvalues.size) {
                            net.blueva.luak.Print.Companion.printUpvalue(ps, upvalues[b]!!)
                        } else {
                            ps.print("UNKNOWN_UPVALUE_" + b)
                        }
                    }

                    OP_GETTABUP -> {
                        ps.print("  ; ")
                        if (upvalues != null && b < upvalues.size) {
                            net.blueva.luak.Print.Companion.printUpvalue(ps, upvalues[b]!!)
                        } else {
                            ps.print("UNKNOWN_UPVALUE_" + b)
                        }
                        ps.print(" ")
                        if (ISK(c)) net.blueva.luak.Print.Companion.printConstant(ps, f, INDEXK(c))
                        else ps.print("-")
                    }

                    OP_SETTABUP -> {
                        ps.print("  ; ")
                        if (upvalues != null && a < upvalues.size) {
                            net.blueva.luak.Print.Companion.printUpvalue(ps, upvalues[a]!!)
                        } else {
                            ps.print("UNKNOWN_UPVALUE_" + a)
                        }
                        ps.print(" ")
                        if (ISK(b)) net.blueva.luak.Print.Companion.printConstant(ps, f, INDEXK(b))
                        else ps.print("-")
                        ps.print(" ")
                        if (ISK(c)) net.blueva.luak.Print.Companion.printConstant(ps, f, INDEXK(c))
                        else ps.print("-")
                    }

                    OP_GETTABLE, OP_SELF -> if (ISK(c)) {
                        ps.print("  ; ")
                        net.blueva.luak.Print.Companion.printConstant(ps, f, INDEXK(c))
                    }

                    OP_SETTABLE, OP_ADD, OP_SUB, OP_MUL, OP_DIV, OP_POW, OP_EQ, OP_LT, OP_LE -> if (ISK(b) || ISK(c)) {
                        ps.print("  ; ")
                        if (ISK(b)) net.blueva.luak.Print.Companion.printConstant(ps, f, INDEXK(b))
                        else ps.print("-")
                        ps.print(" ")
                        if (ISK(c)) net.blueva.luak.Print.Companion.printConstant(ps, f, INDEXK(c))
                        else ps.print("-")
                    }

                    OP_JMP, OP_FORLOOP, OP_FORPREP -> ps.print("  ; to " + (sbx + pc + 2))
                    OP_CLOSURE -> if (protos != null && bx < protos.size) {
                        ps.print("  ; " + protos[bx]!!::class.simpleName)
                    } else {
                        ps.print("  ; UNKNOWN_PROTYPE_" + bx)
                    }

                    OP_SETLIST -> if (c == 0) ps.print("  ; " + code[++pc] + " (stored in the next OP)")
                    else ps.print("  ; " + c)

                    OP_VARARG -> ps.print("  ; is_vararg=" + f.is_vararg)
                    else -> {}
                }
            }
            return pc
        }

        private fun getline(f: Prototype, pc: Int): Int {
            val lineinfo = f.lineinfo
            return if (pc > 0 && lineinfo != null && pc < lineinfo.size) lineinfo[pc] else -1
        }

        fun printHeader(f: Prototype) {
            var s: String = f.source?.toString() ?: "null"
            if (s.startsWith("@") || s.startsWith("=")) s = s.substring(1)
            else if ("\u001bLua" == s) s = "(bstring)"
            else s = "(string)"
            val a = if (f.linedefined == 0) "main" else "function"
            val code = f.code
            val codeLen = code?.size ?: 0
            net.blueva.luak.Print.Companion.ps.print(
                ("\n%" + a + " <" + s + ":" + f.linedefined + ","
                        + f.lastlinedefined + "> (" + codeLen + " instructions, "
                        + codeLen * 4 + " bytes at " + net.blueva.luak.Print.Companion.id(f) + ")\n")
            )
            net.blueva.luak.Print.Companion.ps.print(
                ("${f.numparams} param, " + f.maxstacksize + " slot, "
                        + (f.upvalues?.size ?: 0) + " upvalue, ")
            )
            net.blueva.luak.Print.Companion.ps.print(
                ("${f.locvars?.size ?: 0} local, " + (f.k?.size ?: 0)
                        + " constant, " + (f.p?.size ?: 0) + " function\n")
            )
        }

        fun printConstants(f: Prototype) {
            val k = f.k ?: return
            val n: Int = k.size
            net.blueva.luak.Print.Companion.ps.print("constants (" + n + ") for " + net.blueva.luak.Print.Companion.id(f) + ":\n")
            var i = 0
            while (i < n) {
                net.blueva.luak.Print.Companion.ps.print("  " + (i + 1) + "  ")
                net.blueva.luak.Print.Companion.printValue(net.blueva.luak.Print.Companion.ps, k[i])
                net.blueva.luak.Print.Companion.ps.print("\n")
                i++
            }
        }

        fun printLocals(f: Prototype) {
            val locvars = f.locvars ?: return
            val n: Int = locvars.size
            net.blueva.luak.Print.Companion.ps.print("locals (" + n + ") for " + net.blueva.luak.Print.Companion.id(f) + ":\n")
            var i = 0
            while (i < n) {
                val lv = locvars[i]
                net.blueva.luak.Print.Companion.ps.println("  " + i + "  " + lv?.varname + " " + ((lv?.startpc ?: 0) + 1) + " " + ((lv?.endpc ?: 0) + 1))
                i++
            }
        }

        fun printUpValues(f: Prototype) {
            val upvalues = f.upvalues ?: return
            val n: Int = upvalues.size
            net.blueva.luak.Print.Companion.ps.print("upvalues (" + n + ") for " + net.blueva.luak.Print.Companion.id(f) + ":\n")
            var i = 0
            while (i < n) {
                net.blueva.luak.Print.Companion.ps.print("  " + i + "  " + upvalues[i] + "\n")
                i++
            }
        }

        /** Pretty-prints contents of a Prototype.
         * 
         * @param prototype Prototype to print.
         */
        fun print(prototype: Prototype) {
            net.blueva.luak.Print.Companion.printFunction(prototype, true)
        }

        /** Pretty-prints contents of a Prototype in short or long form.
         * 
         * @param prototype Prototype to print.
         * @param full true to print all fields, false to print short form.
         */
        fun printFunction(prototype: Prototype, full: Boolean) {
            val protos = prototype.p ?: return
            val n: Int = protos.size
            net.blueva.luak.Print.Companion.printHeader(prototype)
            net.blueva.luak.Print.Companion.printCode(prototype)
            if (full) {
                net.blueva.luak.Print.Companion.printConstants(prototype)
                net.blueva.luak.Print.Companion.printLocals(prototype)
                net.blueva.luak.Print.Companion.printUpValues(prototype)
            }
            var i = 0
            while (i < n) {
                net.blueva.luak.Print.Companion.printFunction(protos[i]!!, full)
                i++
            }
        }

        private fun format(s: String, maxcols: Int) {
            val n: Int = s.length
            if (n > maxcols) net.blueva.luak.Print.Companion.ps.print(s.substring(0, maxcols))
            else {
                net.blueva.luak.Print.Companion.ps.print(s)
                var i = maxcols - n
                while (--i >= 0) {
                    net.blueva.luak.Print.Companion.ps.print(' ')
                }
            }
        }

        private fun id(f: Prototype?): String {
            return "Proto"
        }

        /**
         * Print the state of a [LuaClosure] that is being executed
         * @param cl the [LuaClosure]
         * @param pc the program counter
         * @param stack the stack of [LuaValue]
         * @param top the top of the stack
         * @param varargs any [Varargs] value that may apply
         */
        fun printState(cl: LuaClosure, pc: Int, stack: Array<LuaValue?>, top: Int, varargs: Varargs?) {
            // print opcode into buffer
            val previous: PrintStream = net.blueva.luak.Print.Companion.ps
            val baos: ByteArrayOutputStream = ByteArrayOutputStream()
            net.blueva.luak.Print.Companion.ps = PrintStream(baos)
            net.blueva.luak.Print.Companion.printOpCode(cl.p, pc)
            net.blueva.luak.Print.Companion.ps.flush()
            net.blueva.luak.Print.Companion.ps.close()
            net.blueva.luak.Print.Companion.ps = previous
            net.blueva.luak.Print.Companion.format(baos.toByteArray().decodeToString(), 50)
            net.blueva.luak.Print.Companion.printStack(stack, top, varargs)
            net.blueva.luak.Print.Companion.ps.println()
        }

        fun printStack(stack: Array<LuaValue?>, top: Int, varargs: Varargs?) {
            // print stack
            net.blueva.luak.Print.Companion.ps.print('[')
            for (i in stack.indices) {
                val v: LuaValue? = stack[i]
                if (v == null) net.blueva.luak.Print.Companion.ps.print(net.blueva.luak.Print.Companion.STRING_FOR_NULL)
                else when (v.type()) {
                    LuaValue.TSTRING -> {
                        val s: LuaString = v.checkstring()!!
                        net.blueva.luak.Print.Companion.ps.print(
                            if (s.length() < 48) s.tojstring() else s.substring(
                                0,
                                32
                            ).tojstring() + "...+" + (s.length() - 32) + "b"
                        )
                    }

                    LuaValue.TFUNCTION -> net.blueva.luak.Print.Companion.ps.print(v.tojstring())
                    LuaValue.TUSERDATA -> {
                        val o: Any? = v.touserdata()
                        if (o != null) {
                            var n: String = o::class.simpleName ?: "userdata"
                            n = n.substring(n.lastIndexOf('.') + 1)
                            net.blueva.luak.Print.Companion.ps.print(n.toString() + ": " + o.hashCode().toString(16))
                        } else {
                            net.blueva.luak.Print.Companion.ps.print(v.toString())
                        }
                    }

                    else -> net.blueva.luak.Print.Companion.ps.print(v.tojstring())
                }
                if (i + 1 == top) net.blueva.luak.Print.Companion.ps.print(']')
                net.blueva.luak.Print.Companion.ps.print(" | ")
            }
            net.blueva.luak.Print.Companion.ps.print(varargs)
        }
    }
}
