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

import junit.framework.TestCase

/**
 * Tests of basic unary and binary operators on main value types.
 */
class VarargsTest : TestCase() {
    fun testSanity() {
        expectEquals(A_G, A_G)
        expectEquals(A_G_alt, A_G_alt)
        expectEquals(A_G, A_G_alt)
        expectEquals(B_E, B_E_alt)
        expectEquals(C_G, C_G_alt)
        expectEquals(C_E, C_E_alt)
        expectEquals(C_E, C_E_alt2)
        expectEquals(DE, DE_alt)
        expectEquals(DE, DE_alt2)
        expectEquals(E_G, E_G_alt)
        expectEquals(FG, FG_alt)
        expectEquals(FG_alt, FG_alt)
        expectEquals(A, A)
        expectEquals(NONE, NONE)
        expectEquals(NIL, NIL)
    }

    fun testNegativeIndices() {
        expectNegSubargsError(A_G)
        expectNegSubargsError(A_G_alt)
        expectNegSubargsError(B_E)
        expectNegSubargsError(B_E_alt)
        expectNegSubargsError(C_G)
        expectNegSubargsError(C_G_alt)
        expectNegSubargsError(C_E)
        expectNegSubargsError(C_E_alt)
        expectNegSubargsError(C_E_alt2)
        expectNegSubargsError(DE)
        expectNegSubargsError(DE_alt)
        expectNegSubargsError(DE_alt2)
        expectNegSubargsError(E_G)
        expectNegSubargsError(FG)
        expectNegSubargsError(A)
        expectNegSubargsError(NONE)
        expectNegSubargsError(NIL)
    }

    fun testVarargsSubargs() {
        standardTestsA_G(A_G)
        standardTestsA_G(A_G_alt)
        standardTestsC_G(C_G)
        standardTestsC_G(C_G_alt)
        standardTestsE_G(E_G)
        standardTestsE_G(E_G_alt)
        standardTestsFG(FG)
        standardTestsFG(FG_alt)
        standardTestsNone(NONE)
    }

    fun testVarargsMore() {
        var a_g: Varargs
        a_g = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(VarargsTest.Companion.A),
            net.blueva.luak.LuaValue.varargsOf(
                kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                    VarargsTest.Companion.B,
                    VarargsTest.Companion.C,
                    VarargsTest.Companion.D,
                    VarargsTest.Companion.E,
                    VarargsTest.Companion.F,
                    VarargsTest.Companion.G
                )
            )
        )!!
        standardTestsA_G(a_g)
        a_g = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.A,
                VarargsTest.Companion.B,
            ),
            net.blueva.luak.LuaValue.varargsOf(
                kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                    VarargsTest.Companion.C,
                    VarargsTest.Companion.D,
                    VarargsTest.Companion.E,
                    VarargsTest.Companion.F,
                    VarargsTest.Companion.G
                )
            )
        )!!
        standardTestsA_G(a_g)
        a_g = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.A,
                VarargsTest.Companion.B,
                VarargsTest.Companion.C,
            ),
            net.blueva.luak.LuaValue.varargsOf(
                kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                    VarargsTest.Companion.D,
                    VarargsTest.Companion.E,
                    VarargsTest.Companion.F,
                    VarargsTest.Companion.G
                )
            )
        )!!
        standardTestsA_G(a_g)
        a_g = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.A,
                VarargsTest.Companion.B,
                VarargsTest.Companion.C,
                VarargsTest.Companion.D,
            ),
            net.blueva.luak.LuaValue.varargsOf(
                VarargsTest.Companion.E,
                VarargsTest.Companion.F,
                VarargsTest.Companion.G
            )
        )!!
        standardTestsA_G(a_g)
        a_g = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.A,
                VarargsTest.Companion.B,
                VarargsTest.Companion.C,
                VarargsTest.Companion.D,
                VarargsTest.Companion.E
            ), net.blueva.luak.LuaValue.varargsOf(VarargsTest.Companion.F, VarargsTest.Companion.G)
        )!!
        standardTestsA_G(a_g)
        a_g = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.A,
                VarargsTest.Companion.B,
                VarargsTest.Companion.C,
                VarargsTest.Companion.D,
                VarargsTest.Companion.E,
                VarargsTest.Companion.F,
            ), VarargsTest.Companion.G
        )!!
        standardTestsA_G(a_g)
    }

    fun testPairVarargsMore() {
        val a_g: Varargs = Varargs.PairVarargs(
            A,
            Varargs.PairVarargs(
                B,
                Varargs.PairVarargs(
                    C,
                    Varargs.PairVarargs(
                        D,
                        Varargs.PairVarargs(
                            E,
                            Varargs.PairVarargs(F, G)
                        )
                    )
                )
            )
        )
        standardTestsA_G(a_g)
    }

    fun testArrayPartMore() {
        var a_g: Varargs?
        a_g = Varargs.ArrayPartVarargs(Z_H_array, 1, 1, Varargs.ArrayPartVarargs(Z_H_array, 2, 6))
        standardTestsA_G(a_g)
        a_g = Varargs.ArrayPartVarargs(Z_H_array, 1, 2, Varargs.ArrayPartVarargs(Z_H_array, 3, 5))
        standardTestsA_G(a_g)
        a_g = Varargs.ArrayPartVarargs(Z_H_array, 1, 3, Varargs.ArrayPartVarargs(Z_H_array, 4, 4))
        standardTestsA_G(a_g)
        a_g = Varargs.ArrayPartVarargs(Z_H_array, 1, 4, Varargs.ArrayPartVarargs(Z_H_array, 5, 3))
        standardTestsA_G(a_g)
        a_g = Varargs.ArrayPartVarargs(Z_H_array, 1, 5, Varargs.ArrayPartVarargs(Z_H_array, 6, 2))
        standardTestsA_G(a_g)
        a_g = Varargs.ArrayPartVarargs(Z_H_array, 1, 6, Varargs.ArrayPartVarargs(Z_H_array, 7, 1))
        standardTestsA_G(a_g)
    }

    companion object {
        var A: LuaValue = LuaValue.valueOf("a")
        var B: LuaValue = LuaValue.valueOf("b")
        var C: LuaValue = LuaValue.valueOf("c")
        var D: LuaValue = LuaValue.valueOf("d")
        var E: LuaValue = LuaValue.valueOf("e")
        var F: LuaValue = LuaValue.valueOf("f")
        var G: LuaValue = LuaValue.valueOf("g")
        var H: LuaValue = LuaValue.valueOf("h")
        var Z: LuaValue = LuaValue.valueOf("z")
        var NIL: LuaValue = LuaValue.NIL
        var A_G: Varargs = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.A,
                VarargsTest.Companion.B,
                VarargsTest.Companion.C,
                VarargsTest.Companion.D,
                VarargsTest.Companion.E,
                VarargsTest.Companion.F,
                VarargsTest.Companion.G
            )
        )!!
        var B_E: Varargs = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.B,
                VarargsTest.Companion.C,
                VarargsTest.Companion.D,
                VarargsTest.Companion.E
            )
        )!!
        var C_G: Varargs = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.C,
                VarargsTest.Companion.D,
                VarargsTest.Companion.E,
                VarargsTest.Companion.F,
                VarargsTest.Companion.G
            )
        )!!
        var C_E: Varargs = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.C,
                VarargsTest.Companion.D,
                VarargsTest.Companion.E
            )
        )!!
        var DE: Varargs = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.D,
                VarargsTest.Companion.E
            )
        )!!
        var E_G: Varargs = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.E,
                VarargsTest.Companion.F,
                VarargsTest.Companion.G
            )
        )!!
        var FG: Varargs = LuaValue.varargsOf(
            kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                VarargsTest.Companion.F,
                VarargsTest.Companion.G
            )
        )!!
        var Z_H_array: Array<LuaValue?> = arrayOf<LuaValue?>(Z, A, B, C, D, E, F, G, H)
        var A_G_alt: Varargs = Varargs.ArrayPartVarargs(Z_H_array, 1, 7)
        var B_E_alt: Varargs = Varargs.ArrayPartVarargs(Z_H_array, 2, 4)
        var C_G_alt: Varargs = Varargs.ArrayPartVarargs(Z_H_array, 3, 5)
        var C_E_alt: Varargs = Varargs.ArrayPartVarargs(Z_H_array, 3, 3)
        var C_E_alt2: Varargs = LuaValue.varargsOf(C, D, E)
        var DE_alt: Varargs = Varargs.PairVarargs(D, E)
        var DE_alt2: Varargs = LuaValue.varargsOf(D, E)!!
        var E_G_alt: Varargs = Varargs.ArrayPartVarargs(Z_H_array, 5, 3)
        var FG_alt: Varargs = Varargs.PairVarargs(F, G)
        var NONE: Varargs = LuaValue.NONE!!

        fun expectEquals(x: Varargs, y: Varargs) {
            TestCase.assertEquals(x.narg(), y.narg())
            assertEquals(x.arg1(), y.arg1())
            assertEquals(x.arg(0), y.arg(0))
            assertEquals(x.arg(-1), y.arg(-1))
            assertEquals(x.arg(2), y.arg(2))
            assertEquals(x.arg(3), y.arg(3))
            for (i in 4..<x.narg() + 2) assertEquals(x.arg(i), y.arg(i))
        }

        fun standardTestsA_G(a_g: Varargs) {
            expectEquals(A_G, a_g)
            Companion.expectEquals(A_G, a_g.subargs(1)!!)
            Companion.expectEquals(C_G, a_g.subargs(3)!!.subargs(1)!!)
            Companion.expectEquals(E_G, a_g.subargs(5)!!)
            Companion.expectEquals(E_G, a_g.subargs(5)!!.subargs(1)!!)
            Companion.expectEquals(FG, a_g.subargs(6)!!)
            Companion.expectEquals(FG, a_g.subargs(6)!!.subargs(1)!!)
            Companion.expectEquals(G, a_g.subargs(7)!!)
            Companion.expectEquals(G, a_g.subargs(7)!!.subargs(1)!!)
            Companion.expectEquals(NONE, a_g.subargs(8)!!)
            Companion.expectEquals(NONE, a_g.subargs(8)!!.subargs(1)!!)
            Companion.standardTestsC_G(A_G.subargs(3)!!)
        }

        fun standardTestsC_G(c_g: Varargs) {
            Companion.expectEquals(C_G, c_g.subargs(1)!!)
            Companion.expectEquals(E_G, c_g.subargs(3)!!)
            Companion.expectEquals(E_G, c_g.subargs(3)!!.subargs(1)!!)
            Companion.expectEquals(FG, c_g.subargs(4)!!)
            Companion.expectEquals(FG, c_g.subargs(4)!!.subargs(1)!!)
            Companion.expectEquals(G, c_g.subargs(5)!!)
            Companion.expectEquals(G, c_g.subargs(5)!!.subargs(1)!!)
            Companion.expectEquals(NONE, c_g.subargs(6)!!)
            Companion.expectEquals(NONE, c_g.subargs(6)!!.subargs(1)!!)
            Companion.standardTestsE_G(c_g.subargs(3)!!)
        }

        fun standardTestsE_G(e_g: Varargs) {
            Companion.expectEquals(E_G, e_g.subargs(1)!!)
            Companion.expectEquals(FG, e_g.subargs(2)!!)
            Companion.expectEquals(FG, e_g.subargs(2)!!.subargs(1)!!)
            Companion.expectEquals(G, e_g.subargs(3)!!)
            Companion.expectEquals(G, e_g.subargs(3)!!.subargs(1)!!)
            Companion.expectEquals(NONE, e_g.subargs(4)!!)
            Companion.expectEquals(NONE, e_g.subargs(4)!!.subargs(1)!!)
            Companion.standardTestsFG(e_g.subargs(2)!!)
        }

        fun standardTestsFG(fg: Varargs) {
            Companion.expectEquals(FG, fg.subargs(1)!!)
            Companion.expectEquals(G, fg.subargs(2)!!)
            Companion.expectEquals(G, fg.subargs(2)!!.subargs(1)!!)
            Companion.expectEquals(NONE, fg.subargs(3)!!)
            Companion.expectEquals(NONE, fg.subargs(3)!!.subargs(1)!!)
        }

        fun standardTestsNone(none: Varargs) {
            Companion.expectEquals(NONE, none.subargs(1)!!)
            Companion.expectEquals(NONE, none.subargs(2)!!)
        }

        fun expectNegSubargsError(v: Varargs) {
            val expected_msg = "bad argument #1: start must be > 0"
            try {
                v.subargs(0)
                fail("Failed to throw exception for index 0")
            } catch (e: LuaError) {
                TestCase.assertEquals(expected_msg, e.message)
            }
            try {
                v.subargs(-1)
                fail("Failed to throw exception for index -1")
            } catch (e: LuaError) {
                TestCase.assertEquals(expected_msg, e.message)
            }
        }
    }
}
