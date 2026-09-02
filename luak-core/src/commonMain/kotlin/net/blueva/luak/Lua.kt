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


/**
 * Constants for lua limits and opcodes.
 * 
 * 
 * This is a direct translation of C lua distribution header file constants
 * for bytecode creation and processing.
 */
open class Lua {
    companion object {
    /** The Lua *language* version this runtime implements, as scripts see it in
     * the `_VERSION` global. Lua programs branch on this
     * (`if _VERSION == "Lua 5.5" then ...`) and the reference test suite reads
     * it, so it must name the language and not the implementation. See
     * [LUAK_VERSION] for Luak's own release number.
     *
     * One 5.5 language feature is still missing behind this: a named vararg
     * parameter, `function f(...t)`, whose table shares storage with `...` and
     * so needs the 5.5 vararg model rather than the 5.2-shaped one the port is
     * still on.  */
    val _VERSION: String = "Lua 5.5"

    /** Luak's own release, such as `"Luak 26.5"`. This is what tooling
     * should report as the *engine* version; [_VERSION] is the language.  */
    val LUAK_VERSION: String = BuildInfo.VERSION

    /** use return values from previous op  */
    val LUA_MULTRET: Int = -1

    /**
     * Bit in `Prototype.is_vararg` marking a named vararg parameter.
     *
     * `function f(a, ...t)`, from Lua 5.5, binds the extra arguments to a
     * table. The table and `...` are the same storage, so assigning `t[1]`
     * changes what `...` yields, which is why the table is built once on entry
     * and `...` is read back out of it.
     */
    const val VARARG_NAMED: Int = 2


    // from lopcodes.h
    /*===========================================================================
	  We assume that instructions are unsigned numbers.
	  All instructions have an opcode in the first 6 bits.
	  Instructions can have the following fields:
		`A' : 8 bits
		`B' : 9 bits
		`C' : 9 bits
		`Bx' : 18 bits (`B' and `C' together)
		`sBx' : signed Bx

	  A signed argument is represented in excess K; that is, the number
	  value is the unsigned value minus K. K is exactly the maximum value
	  for that argument (so that -max is represented by 0, and +max is
	  represented by 2*max), which is half the maximum for the corresponding
	  unsigned argument.
	===========================================================================*/
    /* basic instruction format */
    const val iABC: Int = 0
    const val iABx: Int = 1
    const val iAsBx: Int = 2
    const val iAx: Int = 3


    /*
	** size and position of opcode arguments.
	*/
    const val SIZE_C: Int = 9
    const val SIZE_B: Int = 9
    val SIZE_Bx: Int = (net.blueva.luak.Lua.SIZE_C + net.blueva.luak.Lua.SIZE_B)
    const val SIZE_A: Int = 8
    val SIZE_Ax: Int = (net.blueva.luak.Lua.SIZE_C + net.blueva.luak.Lua.SIZE_B + net.blueva.luak.Lua.SIZE_A)

    const val SIZE_OP: Int = 6

    const val POS_OP: Int = 0
    val POS_A: Int = (net.blueva.luak.Lua.POS_OP + net.blueva.luak.Lua.SIZE_OP)
    val POS_C: Int = (net.blueva.luak.Lua.POS_A + net.blueva.luak.Lua.SIZE_A)
    val POS_B: Int = (net.blueva.luak.Lua.POS_C + net.blueva.luak.Lua.SIZE_C)
    val POS_Bx: Int = net.blueva.luak.Lua.POS_C
    val POS_Ax: Int = net.blueva.luak.Lua.POS_A

    val MAX_OP: Int = ((1 shl net.blueva.luak.Lua.SIZE_OP) - 1)
    val MAXARG_A: Int = ((1 shl net.blueva.luak.Lua.SIZE_A) - 1)
    val MAXARG_B: Int = ((1 shl net.blueva.luak.Lua.SIZE_B) - 1)
    val MAXARG_C: Int = ((1 shl net.blueva.luak.Lua.SIZE_C) - 1)
    val MAXARG_Bx: Int = ((1 shl net.blueva.luak.Lua.SIZE_Bx) - 1)
    val MAXARG_sBx: Int = (net.blueva.luak.Lua.MAXARG_Bx shr 1) /* `sBx' is signed */
    val MAXARG_Ax: Int = ((1 shl net.blueva.luak.Lua.SIZE_Ax) - 1)

    val MASK_OP: Int = ((1 shl net.blueva.luak.Lua.SIZE_OP) - 1) shl net.blueva.luak.Lua.POS_OP
    val MASK_A: Int = ((1 shl net.blueva.luak.Lua.SIZE_A) - 1) shl net.blueva.luak.Lua.POS_A
    val MASK_B: Int = ((1 shl net.blueva.luak.Lua.SIZE_B) - 1) shl net.blueva.luak.Lua.POS_B
    val MASK_C: Int = ((1 shl net.blueva.luak.Lua.SIZE_C) - 1) shl net.blueva.luak.Lua.POS_C
    val MASK_Bx: Int = ((1 shl net.blueva.luak.Lua.SIZE_Bx) - 1) shl net.blueva.luak.Lua.POS_Bx
    val MASK_Ax: Int = ((1 shl net.blueva.luak.Lua.SIZE_Ax) - 1) shl net.blueva.luak.Lua.POS_Ax

    val MASK_NOT_OP: Int = net.blueva.luak.Lua.MASK_OP.inv()
    val MASK_NOT_A: Int = net.blueva.luak.Lua.MASK_A.inv()
    val MASK_NOT_B: Int = net.blueva.luak.Lua.MASK_B.inv()
    val MASK_NOT_C: Int = net.blueva.luak.Lua.MASK_C.inv()
    val MASK_NOT_Bx: Int = net.blueva.luak.Lua.MASK_Bx.inv()

    /*
	** the following macros help to manipulate instructions
	*/
    fun GET_OPCODE(i: Int): Int {
        return (i shr net.blueva.luak.Lua.POS_OP) and net.blueva.luak.Lua.MAX_OP
    }

    fun GETARG_A(i: Int): Int {
        return (i shr net.blueva.luak.Lua.POS_A) and net.blueva.luak.Lua.MAXARG_A
    }

    fun GETARG_Ax(i: Int): Int {
        return (i shr net.blueva.luak.Lua.POS_Ax) and net.blueva.luak.Lua.MAXARG_Ax
    }

    fun GETARG_B(i: Int): Int {
        return (i shr net.blueva.luak.Lua.POS_B) and net.blueva.luak.Lua.MAXARG_B
    }

    fun GETARG_C(i: Int): Int {
        return (i shr net.blueva.luak.Lua.POS_C) and net.blueva.luak.Lua.MAXARG_C
    }

    fun GETARG_Bx(i: Int): Int {
        return (i shr net.blueva.luak.Lua.POS_Bx) and net.blueva.luak.Lua.MAXARG_Bx
    }

    fun GETARG_sBx(i: Int): Int {
        return ((i shr net.blueva.luak.Lua.POS_Bx) and net.blueva.luak.Lua.MAXARG_Bx) - net.blueva.luak.Lua.MAXARG_sBx
    }


    /*
	** Macros to operate RK indices
	*/
    /** this bit 1 means constant (0 means register)  */
    val BITRK: Int = (1 shl (net.blueva.luak.Lua.SIZE_B - 1))

    /** test whether value is a constant  */
    fun ISK(x: Int): Boolean {
        return 0 != ((x) and net.blueva.luak.Lua.BITRK)
    }

    /** gets the index of the constant  */
    fun INDEXK(r: Int): Int {
        return ((r) and net.blueva.luak.Lua.BITRK.inv())
    }

    val MAXINDEXRK: Int = (net.blueva.luak.Lua.BITRK - 1)

    /** code a constant index as a RK value  */
    fun RKASK(x: Int): Int {
        return ((x) or net.blueva.luak.Lua.BITRK)
    }


    /**
     * invalid register that fits in 8 bits
     */
    val NO_REG: Int = net.blueva.luak.Lua.MAXARG_A


    /*
	** R(x) - register
	** Kst(x) - constant (in constant table)
	** RK(x) == if ISK(x) then Kst(INDEXK(x)) else R(x)
	*/
    /*
	** grep "ORDER OP" if you change these enums
	*/
    /*----------------------------------------------------------------------
	name		args	description
	------------------------------------------------------------------------*/
    const val OP_MOVE: Int = 0 /*	A B	R(A) := R(B)					*/
    const val OP_LOADK: Int = 1 /*	A Bx	R(A) := Kst(Bx)					*/
    const val OP_LOADKX: Int = 2 /*	A 	R(A) := Kst(extra arg)					*/
    const val OP_LOADBOOL: Int = 3 /*	A B C	R(A) := (Bool)B; if (C) pc++			*/
    const val OP_LOADNIL: Int = 4 /*	A B	R(A) := ... := R(A+B) := nil			*/
    const val OP_GETUPVAL: Int = 5 /*	A B	R(A) := UpValue[B]				*/

    const val OP_GETTABUP: Int = 6 /*	A B C	R(A) := UpValue[B][RK(C)]			*/
    const val OP_GETTABLE: Int = 7 /*	A B C	R(A) := R(B)[RK(C)]				*/

    const val OP_SETTABUP: Int = 8 /*	A B C	UpValue[A][RK(B)] := RK(C)			*/
    const val OP_SETUPVAL: Int = 9 /*	A B	UpValue[B] := R(A)				*/
    const val OP_SETTABLE: Int = 10 /*	A B C	R(A)[RK(B)] := RK(C)				*/

    const val OP_NEWTABLE: Int = 11 /*	A B C	R(A) := {} (size = B,C)				*/

    const val OP_SELF: Int = 12 /*	A B C	R(A+1) := R(B); R(A) := R(B)[RK(C)]		*/

    const val OP_ADD: Int = 13 /*	A B C	R(A) := RK(B) + RK(C)				*/
    const val OP_SUB: Int = 14 /*	A B C	R(A) := RK(B) - RK(C)				*/
    const val OP_MUL: Int = 15 /*	A B C	R(A) := RK(B) * RK(C)				*/
    const val OP_DIV: Int = 16 /*	A B C	R(A) := RK(B) / RK(C)				*/
    const val OP_MOD: Int = 17 /*	A B C	R(A) := RK(B) % RK(C)				*/
    const val OP_POW: Int = 18 /*	A B C	R(A) := RK(B) ^ RK(C)				*/
    const val OP_UNM: Int = 19 /*	A B	R(A) := -R(B)					*/
    const val OP_NOT: Int = 20 /*	A B	R(A) := not R(B)				*/
    const val OP_LEN: Int = 21 /*	A B	R(A) := length of R(B)				*/

    const val OP_CONCAT: Int = 22 /*	A B C	R(A) := R(B).. ... ..R(C)			*/

    const val OP_JMP: Int = 23 /*	A sBx	pc+=sBx; if (A) close all upvalues >= R(A - 1)	*/
    const val OP_EQ: Int = 24 /*	A B C	if ((RK(B) == RK(C)) ~= A) then pc++		*/
    const val OP_LT: Int = 25 /*	A B C	if ((RK(B) <  RK(C)) ~= A) then pc++  		*/
    const val OP_LE: Int = 26 /*	A B C	if ((RK(B) <= RK(C)) ~= A) then pc++  		*/

    const val OP_TEST: Int = 27 /*	A C	if not (R(A) <=> C) then pc++			*/
    const val OP_TESTSET: Int = 28 /*	A B C	if (R(B) <=> C) then R(A) := R(B) else pc++	*/

    const val OP_CALL: Int = 29 /*	A B C	R(A), ... ,R(A+C-2) := R(A)(R(A+1), ... ,R(A+B-1)) */
    const val OP_TAILCALL: Int = 30 /*	A B C	return R(A)(R(A+1), ... ,R(A+B-1))		*/
    const val OP_RETURN: Int = 31 /*	A B	return R(A), ... ,R(A+B-2)	(see note)	*/

    const val OP_FORLOOP: Int = 32 /*	A sBx	R(A)+=R(A+2);
				if R(A) <?= R(A+1) then { pc+=sBx; R(A+3)=R(A) }*/
    const val OP_FORPREP: Int = 33 /*	A sBx	R(A)-=R(A+2); pc+=sBx				*/

    const val OP_TFORCALL: Int = 34 /* A C	R(A+3), ... ,R(A+2+C) := R(A)(R(A+1), R(A+2));	*/
    const val OP_TFORLOOP: Int = 35 /* A sBx   if R(A+1) ~= nil then { R(A)=R(A+1); pc += sBx } */
    const val OP_SETLIST: Int = 36 /*	A B C	R(A)[(C-1)*FPF+i] := R(A+i), 1 <= i <= B	*/

    const val OP_CLOSURE: Int = 37 /*	A Bx	R(A) := closure(KPROTO[Bx], R(A), ... ,R(A+n))	*/

    const val OP_VARARG: Int = 38 /*	A B	R(A), R(A+1), ..., R(A+B-1) = vararg		*/

    const val OP_EXTRAARG: Int = 39 /* Ax	extra (larger) argument for previous opcode	*/

    /* Opcodes added by the port past Lua 5.2. They are appended rather than
       slotted into upstream's order so existing 5.2 bytecode keeps loading;
       renumbering to match 5.5 belongs with the instruction-set rewrite. */
    const val OP_IDIV: Int = 40 /*	A B C	R(A) := RK(B) // RK(C)				*/
    const val OP_BAND: Int = 41 /*	A B C	R(A) := RK(B) & RK(C)				*/
    const val OP_BOR: Int = 42 /*	A B C	R(A) := RK(B) | RK(C)				*/
    const val OP_BXOR: Int = 43 /*	A B C	R(A) := RK(B) ~ RK(C)				*/
    const val OP_SHL: Int = 44 /*	A B C	R(A) := RK(B) << RK(C)				*/
    const val OP_SHR: Int = 45 /*	A B C	R(A) := RK(B) >> RK(C)				*/
    const val OP_BNOT: Int = 46 /*	A B	R(A) := ~R(B)					*/

    /** `A` - mark R(A) as a to-be-closed variable, from Lua 5.4's `<close>`. */
    const val OP_TBC: Int = 47 /*	A	mark R(A) "to be closed"			*/

    /**
     * `A Bx` - raise an error if R(A) is not nil, from Lua 5.5's `global`.
     *
     * A `global x = v` declaration checks that the global is still unset
     * before assigning it. `Kst(Bx - 1)` is the global's name, and `Bx == 0`
     * means the name did not fit in the constant table.
     */
    const val OP_ERRNNIL: Int = 48 /*	A Bx	if R(A) ~= nil then error		*/

    val NUM_OPCODES: Int = net.blueva.luak.Lua.OP_ERRNNIL + 1

    /* pseudo-opcodes used in parsing only.  */
    const val OP_GT: Int = 63 // >
    const val OP_GE: Int = 62 // >=
    const val OP_NEQ: Int = 61 // ~=
    const val OP_AND: Int = 60 // and
    const val OP_OR: Int = 59 // or


    /*===========================================================================
	  Notes:
	  (*) In OP_CALL, if (B == 0) then B = top. C is the number of returns - 1,
	      and can be 0: OP_CALL then sets `top' to last_result+1, so
	      next open instruction (OP_CALL, OP_RETURN, OP_SETLIST) may use `top'.

	  (*) In OP_VARARG, if (B == 0) then use actual number of varargs and
	      set top (like in OP_CALL with C == 0).

	  (*) In OP_RETURN, if (B == 0) then return up to `top'

	  (*) In OP_SETLIST, if (B == 0) then B = `top';
	      if (C == 0) then next `instruction' is real C

	  (*) For comparisons, A specifies what condition the test should accept
	      (true or false).

	  (*) All `skips' (pc++) assume that next instruction is a jump
	===========================================================================*/
    /*
	** masks for instruction properties. The format is:
	** bits 0-1: op mode
	** bits 2-3: C arg mode
	** bits 4-5: B arg mode
	** bit 6: instruction set register A
	** bit 7: operator is a test
	*/
    const val OpArgN: Int = 0 /* argument is not used */
    const val OpArgU: Int = 1 /* argument is used */
    const val OpArgR: Int = 2 /* argument is a register or a jump offset */
    const val OpArgK: Int = 3 /* argument is a constant or register/constant */

    val luaP_opmodes: IntArray = intArrayOf(
        /*   T        A           B             C          mode		   opcode	*/
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_MOVE */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABx),  /* OP_LOADK */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgN shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABx),  /* OP_LOADKX */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgU shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_LOADBOOL */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_LOADNIL */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_GETUPVAL */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_GETTABUP */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_GETTABLE */
        (0 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_SETTABUP */
        (0 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_SETUPVAL */
        (0 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_SETTABLE */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgU shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_NEWTABLE */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_SELF */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_ADD */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_SUB */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_MUL */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_DIV */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_MOD */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_POW */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_UNM */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_NOT */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_LEN */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgR shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_CONCAT */
        (0 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iAsBx),  /* OP_JMP */
        (1 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_EQ */
        (1 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_LT */
        (1 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_LE */
        (1 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgN shl 4) or (net.blueva.luak.Lua.OpArgU shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_TEST */
        (1 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgU shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_TESTSET */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgU shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_CALL */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgU shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_TAILCALL */
        (0 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_RETURN */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iAsBx),  /* OP_FORLOOP */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iAsBx),  /* OP_FORPREP */
        (0 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgN shl 4) or (net.blueva.luak.Lua.OpArgU shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_TFORCALL */
        (1 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iAsBx),  /* OP_TFORLOOP */
        (0 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgU shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_SETLIST */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABx),  /* OP_CLOSURE */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_VARARG */
        (0 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgU shl 4) or (net.blueva.luak.Lua.OpArgU shl 2) or (net.blueva.luak.Lua.iAx),  /* OP_EXTRAARG */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_IDIV */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_BAND */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_BOR */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_BXOR */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_SHL */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgK shl 4) or (net.blueva.luak.Lua.OpArgK shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_SHR */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgR shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_BNOT */
        (0 shl 7) or (1 shl 6) or (net.blueva.luak.Lua.OpArgN shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABC),  /* OP_TBC */
        (0 shl 7) or (0 shl 6) or (net.blueva.luak.Lua.OpArgN shl 4) or (net.blueva.luak.Lua.OpArgN shl 2) or (net.blueva.luak.Lua.iABx),  /* OP_ERRNNIL */
    )

    fun getOpMode(m: Int): Int {
        return net.blueva.luak.Lua.luaP_opmodes[m] and 3
    }

    fun getBMode(m: Int): Int {
        return (net.blueva.luak.Lua.luaP_opmodes[m] shr 4) and 3
    }

    fun getCMode(m: Int): Int {
        return (net.blueva.luak.Lua.luaP_opmodes[m] shr 2) and 3
    }

    fun testAMode(m: Int): Boolean {
        return 0 != (net.blueva.luak.Lua.luaP_opmodes[m] and (1 shl 6))
    }

    fun testTMode(m: Int): Boolean {
        return 0 != (net.blueva.luak.Lua.luaP_opmodes[m] and (1 shl 7))
    }

    /* number of list items to accumulate before a SETLIST instruction */
    const val LFIELDS_PER_FLUSH: Int = 50

    /** Room for the identifier a chunk is named by, upstream's `LUA_IDSIZE`. */
    private const val LUA_IDSIZE = 60

    private const val CHUNKID_ELLIPSIS = "..."
    private const val CHUNKID_PREFIX = "[string \""
    private const val CHUNKID_SUFFIX = "\"]"

    /**
     * The name a chunk goes by in error messages, upstream's `luaO_chunkid`.
     *
     * A `=` source is taken literally, a `@` source is a file name and keeps
     * its tail since the directories in front of it matter less, and anything
     * else is source text, quoted and cut short at its first line.
     */
    fun chunkid(source: String): String {
        val bufflen: Int = net.blueva.luak.Lua.LUA_IDSIZE
        if (source.startsWith("=")) {
            return if (source.length <= bufflen) source.substring(1) else source.substring(1, bufflen)
        }
        if (source.startsWith("@")) {
            if (source.length <= bufflen) return source.substring(1)
            return net.blueva.luak.Lua.CHUNKID_ELLIPSIS +
                source.substring(1 + source.length - (bufflen - net.blueva.luak.Lua.CHUNKID_ELLIPSIS.length))
        }
        val newline: Int = source.indexOf('\n')
        val room: Int = bufflen - (
            net.blueva.luak.Lua.CHUNKID_PREFIX.length +
                net.blueva.luak.Lua.CHUNKID_ELLIPSIS.length +
                net.blueva.luak.Lua.CHUNKID_SUFFIX.length
            ) - 1
        if (source.length < room && newline < 0) {
            return net.blueva.luak.Lua.CHUNKID_PREFIX + source + net.blueva.luak.Lua.CHUNKID_SUFFIX
        }
        var length: Int = if (newline >= 0) newline else source.length
        if (length > room) length = room
        return net.blueva.luak.Lua.CHUNKID_PREFIX + source.substring(0, length) +
            net.blueva.luak.Lua.CHUNKID_ELLIPSIS + net.blueva.luak.Lua.CHUNKID_SUFFIX
    }
    }
}
