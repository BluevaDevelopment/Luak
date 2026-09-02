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
package net.blueva.luak.lib

import net.blueva.luak.LuaTable
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs

/**
 * Subclass of LibFunction that implements the Lua standard `bit32` library.
 * 
 * 
 * Typically, this library is included as part of a call to
 * [net.blueva.luak.lib.LuaPlatform.standardGlobals]
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * println(globals.get("bit32").get("bnot").call(LuaValue.valueOf(2)))
 * ```
 * 
 * 
 * To instantiate and use it directly,
 * link it into your globals table via [LuaValue.load] using code such as:
 * ```kotlin
 * val globals = Globals()
 * globals.load(BaseLib())
 * globals.load(PackageLib())
 * globals.load(Bit32Lib())
 * println(globals.get("bit32").get("bnot").call(LuaValue.valueOf(2)))
 * ```
 * 
 * 
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 * @see LibFunction
 * 
 * @see net.blueva.luak.lib.jvm.JvmPlatform
 * 
 * @see net.blueva.luak.lib.LuaPlatform
 * 
 * @see [Lua 5.2 Bitwise Operation Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.7)
 */
class Bit32Lib : TwoArgFunction() {
    /** Perform one-time initialization on the library by creating a table
     * containing the library functions, adding that table to the supplied environment,
     * adding the table to package.loaded, and returning table as the return value.
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env the environment to load into, which must be a Globals instance.
     */
    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        val t: LuaTable = LuaTable()
        bind(
            t, { Bit32LibV() }, arrayOf<String?>(
                "band", "bnot", "bor", "btest", "bxor", "extract", "replace"
            )
        )
        bind(
            t, { Bit32Lib2() }, arrayOf<String?>(
                "arshift", "lrotate", "lshift", "rrotate", "rshift"
            )
        )
        val e = env!!
        e.set("bit32", t)
        if (!e.get("package")!!.isnil()) e.get("package")!!.get("loaded")!!.set("bit32", t)
        return t
    }

    internal class Bit32LibV : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val a = args!!
            when (opcode) {
                0 -> return net.blueva.luak.lib.Bit32Lib.Companion.band(a)
                1 -> return net.blueva.luak.lib.Bit32Lib.Companion.bnot(a)
                2 -> return net.blueva.luak.lib.Bit32Lib.Companion.bor(a)
                3 -> return net.blueva.luak.lib.Bit32Lib.Companion.btest(a)
                4 -> return net.blueva.luak.lib.Bit32Lib.Companion.bxor(a)
                5 -> return net.blueva.luak.lib.Bit32Lib.Companion.extract(
                    a.checkint(1),
                    a.checkint(2),
                    a.optint(3, 1)
                )

                6 -> return net.blueva.luak.lib.Bit32Lib.Companion.replace(
                    a.checkint(1), a.checkint(2),
                    a.checkint(3), a.optint(4, 1)
                )
            }
            return NIL
        }
    }

    internal class Bit32Lib2 : TwoArgFunction() {
        override fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue? {
            when (opcode) {
                0 -> return net.blueva.luak.lib.Bit32Lib.Companion.arshift(arg1!!.checkint(), arg2!!.checkint())
                1 -> return (net.blueva.luak.lib.Bit32Lib.Companion.lrotate(arg1!!.checkint(), arg2!!.checkint()))!!
                2 -> return net.blueva.luak.lib.Bit32Lib.Companion.lshift(arg1!!.checkint(), arg2!!.checkint())
                3 -> return (net.blueva.luak.lib.Bit32Lib.Companion.rrotate(arg1!!.checkint(), arg2!!.checkint()))!!
                4 -> return net.blueva.luak.lib.Bit32Lib.Companion.rshift(arg1!!.checkint(), arg2!!.checkint())
            }
            return NIL
        }
    }

    companion object {
        fun arshift(x: Int, disp: Int): LuaValue {
            if (disp >= 0) {
                return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue(x shr disp)
            } else {
                return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue(x shl -disp)
            }
        }

        fun rshift(x: Int, disp: Int): LuaValue {
            if (disp >= 32 || disp <= -32) {
                return ZERO!!
            } else if (disp >= 0) {
                return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue(x ushr disp)
            } else {
                return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue(x shl -disp)
            }
        }

        fun lshift(x: Int, disp: Int): LuaValue {
            if (disp >= 32 || disp <= -32) {
                return ZERO!!
            } else if (disp >= 0) {
                return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue(x shl disp)
            } else {
                return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue(x ushr -disp)
            }
        }

        fun band(args: Varargs): Varargs {
            var result = -1
            for (i in 1..args.narg()) {
                result = result and args.checkint(i)
            }
            return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue(result)
        }

        fun bnot(args: Varargs): Varargs {
            return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue(args.checkint(1).inv())
        }

        fun bor(args: Varargs): Varargs {
            var result = 0
            for (i in 1..args.narg()) {
                result = result or args.checkint(i)
            }
            return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue(result)
        }

        fun btest(args: Varargs): Varargs {
            var bits = -1
            for (i in 1..args.narg()) {
                bits = bits and args.checkint(i)
            }
            return valueOf(bits != 0)!!
        }

        fun bxor(args: Varargs): Varargs {
            var result = 0
            for (i in 1..args.narg()) {
                result = result xor args.checkint(i)
            }
            return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue(result)
        }

        fun lrotate(x: Int, disp: Int): LuaValue? {
            var disp = disp
            if (disp < 0) {
                return net.blueva.luak.lib.Bit32Lib.Companion.rrotate(x, -disp)
            } else {
                disp = disp and 31
                return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue((x shl disp) or (x ushr (32 - disp)))
            }
        }

        fun rrotate(x: Int, disp: Int): LuaValue? {
            var disp = disp
            if (disp < 0) {
                return net.blueva.luak.lib.Bit32Lib.Companion.lrotate(x, -disp)
            } else {
                disp = disp and 31
                return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue((x ushr disp) or (x shl (32 - disp)))
            }
        }

        fun extract(n: Int, field: Int, width: Int): LuaValue {
            if (field < 0) {
                argerror(2, "field cannot be negative")
            }
            if (width < 0) {
                argerror(3, "width must be postive")
            }
            if (field + width > 32) {
                error("trying to access non-existent bits")
            }
            return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue((n ushr field) and (-1 ushr (32 - width)))
        }

        fun replace(n: Int, v: Int, field: Int, width: Int): LuaValue {
            var n = n
            if (field < 0) {
                argerror(3, "field cannot be negative")
            }
            if (width < 0) {
                argerror(4, "width must be postive")
            }
            if (field + width > 32) {
                error("trying to access non-existent bits")
            }
            val mask = (-1 ushr (32 - width)) shl field
            n = (n and mask.inv()) or ((v shl field) and mask)
            return net.blueva.luak.lib.Bit32Lib.Companion.bitsToValue(n)
        }

        private fun bitsToValue(x: Int): LuaValue {
            return if (x < 0) valueOf((x.toLong() and 0xFFFFFFFFL).toDouble()) else valueOf(x)
        }
    }
}
