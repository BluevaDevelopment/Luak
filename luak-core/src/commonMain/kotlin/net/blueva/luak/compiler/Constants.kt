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
package net.blueva.luak.compiler

import net.blueva.luak.arrayCopy
import net.blueva.luak.LocVars
import net.blueva.luak.Lua
import net.blueva.luak.LuaError
import net.blueva.luak.LuaString
import net.blueva.luak.LuaValue
import net.blueva.luak.Prototype
import net.blueva.luak.Upvaldesc

/**
 * Constants used by the LuaC compiler and related classes.
 *
 * @see LuaC
 *
 * @see FuncState
 */
open class Constants : Lua() {
    /** Maximum stack size of a Luak vm interpreter instance.  */
    val MAXSTACK: Int = 250

    val LUAI_MAXUPVAL: Int = 0xff
    val LUAI_MAXVARS: Int = 200

    internal fun _assert(b: Boolean) {
        if (!b) throw LuaError("compiler assert failed")
    }

    internal fun SET_OPCODE(i: InstructionPtr, o: Int) {
        i.set((i.get() and (MASK_NOT_OP)) or ((o shl POS_OP) and MASK_OP))
    }

    internal fun SETARG_A(code: IntArray, index: Int, u: Int) {
        code[index] = (code[index] and (MASK_NOT_A)) or ((u shl POS_A) and MASK_A)
    }

    internal fun SETARG_A(i: InstructionPtr, u: Int) {
        i.set((i.get() and (MASK_NOT_A)) or ((u shl POS_A) and MASK_A))
    }

    internal fun SETARG_B(i: InstructionPtr, u: Int) {
        i.set((i.get() and (MASK_NOT_B)) or ((u shl POS_B) and MASK_B))
    }

    internal fun SETARG_C(i: InstructionPtr, u: Int) {
        i.set((i.get() and (MASK_NOT_C)) or ((u shl POS_C) and MASK_C))
    }

    internal fun SETARG_Bx(i: InstructionPtr, u: Int) {
        i.set((i.get() and (MASK_NOT_Bx)) or ((u shl POS_Bx) and MASK_Bx))
    }

    internal fun SETARG_sBx(i: InstructionPtr, u: Int) {
        SETARG_Bx(i, u + MAXARG_sBx)
    }

    internal fun CREATE_ABC(o: Int, a: Int, b: Int, c: Int): Int {
        return ((o shl POS_OP) and MASK_OP) or
                ((a shl POS_A) and MASK_A) or
                ((b shl POS_B) and MASK_B) or
                ((c shl POS_C) and MASK_C)
    }

    internal fun CREATE_ABx(o: Int, a: Int, bc: Int): Int {
        return ((o shl POS_OP) and MASK_OP) or
                ((a shl POS_A) and MASK_A) or
                ((bc shl POS_Bx) and MASK_Bx)
    }

    internal fun CREATE_Ax(o: Int, a: Int): Int {
        return ((o shl POS_OP) and MASK_OP) or
                ((a shl POS_Ax) and MASK_Ax)
    }

    // vector reallocation
    internal fun realloc(v: Array<LuaValue?>?, n: Int): Array<LuaValue?> {
        val a: Array<LuaValue?> = arrayOfNulls<LuaValue>(n)
        if (v != null) arrayCopy(v, 0, a, 0, minOf(v.size, n))
        return a
    }

    internal fun realloc(v: Array<Prototype?>?, n: Int): Array<Prototype?> {
        val a: Array<Prototype?> = arrayOfNulls<Prototype>(n)
        if (v != null) arrayCopy(v, 0, a, 0, minOf(v.size, n))
        return a
    }

    internal fun realloc(v: Array<LuaString?>?, n: Int): Array<LuaString?> {
        val a: Array<LuaString?> = arrayOfNulls<LuaString>(n)
        if (v != null) arrayCopy(v, 0, a, 0, minOf(v.size, n))
        return a
    }

    internal fun realloc(v: Array<LocVars?>?, n: Int): Array<LocVars?> {
        val a: Array<LocVars?> = arrayOfNulls<LocVars>(n)
        if (v != null) arrayCopy(v, 0, a, 0, minOf(v.size, n))
        return a
    }

    internal fun realloc(v: Array<Upvaldesc?>?, n: Int): Array<Upvaldesc?> {
        val a: Array<Upvaldesc?> = arrayOfNulls<Upvaldesc>(n)
        if (v != null) arrayCopy(v, 0, a, 0, minOf(v.size, n))
        return a
    }

    internal fun realloc(v: Array<LexState.Vardesc?>?, n: Int): Array<LexState.Vardesc?> {
        val a: Array<LexState.Vardesc?> = arrayOfNulls<LexState.Vardesc>(n)
        if (v != null) arrayCopy(v, 0, a, 0, minOf(v.size, n))
        return a
    }

    internal fun grow(v: Array<LexState.Labeldesc?>?, min_n: Int): Array<LexState.Labeldesc?> {
        return if (v == null) {
            arrayOfNulls(maxOf(2, min_n))
        } else if (v.size < min_n) {
            realloc(v, maxOf(2, min_n, v.size * 2))
        } else {
            v
        }
    }

    internal fun realloc(v: Array<LexState.Labeldesc?>?, n: Int): Array<LexState.Labeldesc?> {
        val a: Array<LexState.Labeldesc?> = arrayOfNulls<LexState.Labeldesc>(n)
        if (v != null) arrayCopy(v, 0, a, 0, minOf(v.size, n))
        return a
    }

    internal fun realloc(v: IntArray?, n: Int): IntArray {
        val a = IntArray(n)
        if (v != null) arrayCopy(v, 0, a, 0, minOf(v.size, n))
        return a
    }

    internal fun realloc(v: ByteArray?, n: Int): ByteArray {
        val a = ByteArray(n)
        if (v != null) arrayCopy(v, 0, a, 0, minOf(v.size, n))
        return a
    }

    internal fun realloc(v: CharArray?, n: Int): CharArray {
        val a = CharArray(n)
        if (v != null) arrayCopy(v, 0, a, 0, minOf(v.size, n))
        return a
    }
}
