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

import net.blueva.luak.LuaDouble
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs
import kotlin.math.pow
import kotlin.random.Random

/**
 * Subclass of [LibFunction] which implements the lua standard `math`
 * library.
 *
 *
 * The whole library is implemented in `commonMain` on top of [kotlin.math],
 * so every target - JVM, JavaScript, Wasm, and Native - gets the same set of
 * functions with the same accuracy. There is no reduced variant: the
 * hand-rolled `exp()`/`pow()` approximations Luak inherited from LuaJ's
 * J2ME profile are gone, and `acos`, `asin`, `atan`, `atan2`, `cosh`, `log`,
 * `sinh`, and `tanh` are part of this class rather than a JVM-only subclass.
 *
 *
 * Typically this library is included as part of a call to
 * [net.blueva.luak.lib.LuaPlatform.standardGlobals] (any target) or
 * [net.blueva.luak.lib.jvm.JvmPlatform.standardGlobals] (JVM only):
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * println(globals.get("math").get("sqrt").call(LuaValue.valueOf(2)))
 * ```
 *
 *
 * To instantiate and use it directly, link it into your globals table via
 * [Globals.load] using code such as:
 * ```kotlin
 * val globals = Globals()
 * globals.load(BaseLib())
 * globals.load(PackageLib())
 * globals.load(MathLib())
 * ```
 * Doing so will ensure the library is properly initialized
 * and loaded into the globals table.
 *
 *
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 * @see LibFunction
 *
 * @see net.blueva.luak.lib.LuaPlatform
 *
 * @see [Lua 5.2 Math Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.6)
 */
open class MathLib : TwoArgFunction() {
    /** Construct a MathLib, which can be initialized by calling it with a
     * modname string, and a global environment table as arguments using
     * [.call].  */
    init {
        net.blueva.luak.lib.MathLib.Companion.MATHLIB = this
    }

    /**
     * The `math.random` this library installed, or null before [call] runs.
     *
     * Kept so that [seed] can reach the generator; a host has no other way to,
     * since the function objects are not public.
     */
    internal var randomfunction: random? = null

    /**
     * Seeds `math.random` from the host, as `math.randomseed(x, y)` would.
     *
     * Reached through [net.blueva.luak.Globals.seedrandom]; see it for why a
     * host wants to.
     */
    internal fun seed(x: Long, y: Long) {
        randomfunction?.generator?.seed(x, y)
    }

    /** Perform one-time initialization on the library by creating a table
     * containing the library functions, adding that table to the supplied environment,
     * adding the table to package.loaded, and returning table as the return value.
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env the environment to load into, typically a Globals instance.
     */
    open override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        val math: LuaTable = LuaTable(0, 30)
        math.set("abs", net.blueva.luak.lib.MathLib.abs())
        math.set("acos", net.blueva.luak.lib.MathLib.acos())
        math.set("asin", net.blueva.luak.lib.MathLib.asin())
        val atan: LuaValue = net.blueva.luak.lib.MathLib.atan()
        math.set("atan", atan)
        math.set("ceil", net.blueva.luak.lib.MathLib.ceil())
        math.set("cos", net.blueva.luak.lib.MathLib.cos())
        math.set("deg", net.blueva.luak.lib.MathLib.deg())
        math.set("exp", net.blueva.luak.lib.MathLib.exp())
        math.set("floor", net.blueva.luak.lib.MathLib.floor())
        math.set("fmod", net.blueva.luak.lib.MathLib.fmod())
        math.set("frexp", net.blueva.luak.lib.MathLib.frexp())
        math.set("huge", LuaDouble.POSINF)
        math.set("maxinteger", LuaValue.valueOf(Long.MAX_VALUE))
        math.set("mininteger", LuaValue.valueOf(Long.MIN_VALUE))
        math.set("ldexp", net.blueva.luak.lib.MathLib.ldexp())
        math.set("log", net.blueva.luak.lib.MathLib.log())
        math.set("max", net.blueva.luak.lib.MathLib.max())
        math.set("min", net.blueva.luak.lib.MathLib.min())
        math.set("modf", net.blueva.luak.lib.MathLib.modf())
        math.set("pi", kotlin.math.PI)
        val r: random?
        math.set("random", net.blueva.luak.lib.MathLib.random().also { r = it })
        math.set("randomseed", net.blueva.luak.lib.MathLib.randomseed((r)!!))
        randomfunction = r
        math.set("rad", net.blueva.luak.lib.MathLib.rad())
        math.set("sin", net.blueva.luak.lib.MathLib.sin())
        math.set("sqrt", net.blueva.luak.lib.MathLib.sqrt())
        math.set("tan", net.blueva.luak.lib.MathLib.tan())
        math.set("tointeger", net.blueva.luak.lib.MathLib.tointeger())
        math.set("type", net.blueva.luak.lib.MathLib.type())
        math.set("ult", net.blueva.luak.lib.MathLib.ult())
        // math.atan2, math.cosh, math.pow, math.sinh and math.tanh were
        // deprecated in 5.3 and removed in 5.4; the classes behind them stay
        // for embedders that want to put them back.
        env!!.set("math", math)
        (env as? net.blueva.luak.Globals)?.mathlib = this
        if (!env!!.get("package")!!.isnil()) env!!.get("package")!!.get("loaded")!!.set("math", math)
        return math
    }

    abstract class UnaryOp : OneArgFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            return valueOf(call(arg!!.checkdouble()))
        }

        protected abstract fun call(d: Double): Double
    }

    abstract class BinaryOp : TwoArgFunction() {
        override fun call(x: LuaValue?, y: LuaValue?): LuaValue? {
            return valueOf(call(x!!.checkdouble(), y!!.checkdouble()))
        }

        protected abstract fun call(x: Double, y: Double): Double
    }

    /** `math.abs`; an integer argument gives an integer, wrapping on mininteger. */
    internal class abs : OneArgFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            val x: LuaValue = arg!!
            if (x.isinttype()) {
                val v: Long = x.tolong()
                return valueOf(if (v < 0L) -v else v) // -mininteger wraps, as in C
            }
            return valueOf(kotlin.math.abs(x.checkdouble()))
        }
    }

    /** `math.type`: `"integer"`, `"float"`, or nil for anything else. */
    internal class type : OneArgFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            val x: LuaValue = arg!!
            if (!x.isnumber() || x.isstring() && !x.isnumber()) return NIL
            if (x.type() != LuaValue.TNUMBER) return NIL
            return valueOf(if (x.isinttype()) "integer" else "float")
        }
    }

    /** `math.tointeger`: the integer a value denotes exactly, or nil. */
    internal class tointeger : OneArgFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            val x: LuaValue = arg!!
            if (x.isinttype()) return x
            val n: LuaValue = x.tonumber()
            if (n.isnil()) return NIL
            // A numeral that already denotes an integer is one, whatever its
            // magnitude; only a float has to be checked for a whole value.
            if (n.isinttype()) return n
            val d: Double = n.todouble()
            // Range as well as round trip: converting a double past the
            // integer range saturates, and converting that back matches.
            if (d < -9.2233720368547758E18 || d >= 9.2233720368547758E18) return NIL
            val l: Long = d.toLong()
            return if (l.toDouble() == d) valueOf(l) else NIL
        }
    }

    /** `math.ult`: compares two integers as unsigned. */
    internal class ult : TwoArgFunction() {
        override fun call(x: LuaValue?, y: LuaValue?): LuaValue? {
            val a: Long = x!!.checklong()
            val b: Long = y!!.checklong()
            // Flipping the sign bit orders the values as if unsigned.
            return valueOf((a xor Long.MIN_VALUE) < (b xor Long.MIN_VALUE))
        }
    }

    internal class acos : UnaryOp() {
        override fun call(d: Double): Double {
            return kotlin.math.acos(d)
        }
    }

    internal class asin : UnaryOp() {
        override fun call(d: Double): Double {
            return kotlin.math.asin(d)
        }
    }

    /** Serves both `math.atan` and its Lua 5.1 alias `math.atan2`; the second
     * argument defaults to 1.0, which makes the one-argument form the plain
     * arc tangent. */
    internal class atan : TwoArgFunction() {
        override fun call(y: LuaValue?, x: LuaValue?): LuaValue? {
            return valueOf(kotlin.math.atan2(y!!.checkdouble(), x!!.optdouble(1.0)))
        }
    }

    internal class cosh : UnaryOp() {
        override fun call(d: Double): Double {
            return kotlin.math.cosh(d)
        }
    }

    internal class sinh : UnaryOp() {
        override fun call(d: Double): Double {
            return kotlin.math.sinh(d)
        }
    }

    internal class tanh : UnaryOp() {
        override fun call(d: Double): Double {
            return kotlin.math.tanh(d)
        }
    }

    /** `math.log(x [, base])`; the base defaults to [kotlin.math.E]. */
    internal class log : TwoArgFunction() {
        override fun call(x: LuaValue?, base: LuaValue?): LuaValue? {
            var natural: Double = kotlin.math.ln(x!!.checkdouble())
            val b: Double = base!!.optdouble(kotlin.math.E)
            if (b != kotlin.math.E) natural /= kotlin.math.ln(b)
            return valueOf(natural)
        }
    }

    /** `math.ceil`; the result is an integer whenever it fits in one. */
    internal class ceil : OneArgFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            val x: LuaValue = arg!!
            if (x.isinttype()) return x
            return net.blueva.luak.lib.MathLib.Companion.narrowToInteger(kotlin.math.ceil(x.checkdouble()))
        }
    }

    internal class cos : UnaryOp() {
        override fun call(d: Double): Double {
            return kotlin.math.cos(d)
        }
    }

    internal class deg : UnaryOp() {
        override fun call(d: Double): Double {
            return d * 180.0 / kotlin.math.PI
        }
    }

    /** `math.floor`; the result is an integer whenever it fits in one. */
    internal class floor : OneArgFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            val x: LuaValue = arg!!
            if (x.isinttype()) return x
            return net.blueva.luak.lib.MathLib.Companion.narrowToInteger(kotlin.math.floor(x.checkdouble()))
        }
    }

    internal class rad : UnaryOp() {
        override fun call(d: Double): Double {
            return d * kotlin.math.PI / 180.0
        }
    }

    internal class sin : UnaryOp() {
        override fun call(d: Double): Double {
            return kotlin.math.sin(d)
        }
    }

    internal class sqrt : UnaryOp() {
        override fun call(d: Double): Double {
            return kotlin.math.sqrt(d)
        }
    }

    internal class tan : UnaryOp() {
        override fun call(d: Double): Double {
            return kotlin.math.tan(d)
        }
    }

    internal class exp : UnaryOp() {
        override fun call(d: Double): Double {
            return kotlin.math.exp(d)
        }
    }

    internal class fmod : TwoArgFunction() {
        override fun call(xv: LuaValue?, yv: LuaValue?): LuaValue? {
            if (xv!!.isinttype() && yv!!.isinttype()) {
                // Two integers give an integer, and there is no integer answer
                // to a division by zero - unlike the float case, which has NaN.
                val y: Long = yv.tolong()
                if (y == 0L) LuaValue.argerror(2, "zero")
                if (y == -1L) return valueOf(0L) // avoids overflow on the minimum
                // Long remainder already takes the sign of the dividend, like C fmod.
                return valueOf(xv.tolong() % y)
            }
            return valueOf(xv.checkdouble() % yv!!.checkdouble())
        }
    }

    /**
     * `math.ldexp (m, e)`: `m * 2^e`.
     *
     * Split into steps that each stay inside the double range, so a large
     * exponent does not go through an infinity on the way and lose the value.
     */
    internal class ldexp : BinaryOp() {
        override fun call(x: Double, y: Double): Double {
            if (x == 0.0 || x.isNaN() || x.isInfinite()) return x
            var result: Double = x
            var remaining: Int = y.toInt()
            while (remaining > 1000) {
                result *= net.blueva.luak.lib.MathLib.Companion.TWO_POW_1000
                remaining -= 1000
            }
            while (remaining < -1000) {
                result /= net.blueva.luak.lib.MathLib.Companion.TWO_POW_1000
                remaining += 1000
            }
            var step = 1.0
            var factor = 2.0
            var count: Int = if (remaining < 0) -remaining else remaining
            while (count > 0) {
                if (count and 1 == 1) step *= factor
                factor *= factor
                count = count shr 1
            }
            return if (remaining < 0) result / step else result * step
        }
    }

    internal class pow : BinaryOp() {
        override fun call(x: Double, y: Double): Double {
            return net.blueva.luak.lib.MathLib.Companion.dpow_default(x, y)
        }
    }

    internal class frexp : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val x: Double = args.checkdouble(1)
            if (x == 0.0) return (varargsOf(ZERO, (ZERO)!!))!!
            // An infinity and a NaN have no mantissa and exponent to split
            // into; C answers the value itself with a zero exponent.
            if (x.isNaN() || x.isInfinite()) return (varargsOf(valueOf(x), (ZERO)!!))!!
            val bits: Long = (x).toBits()
            val m =
                ((bits and ((-1L shl 52).inv()).toLong()) + (1L shl 52)) * (if (bits >= 0) (.5 / (1L shl 52)) else (-.5 / (1L shl 52)))
            val e = ((((bits shr 52).toInt()) and 0x7ff) - 1022).toDouble()
            return (varargsOf(valueOf(m), valueOf(e)))!!
        }
    }

    internal class max : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            // With nothing to compare there is no answer, and the complaint is
            // about the missing argument rather than about its type.
            if (args.narg() < 1) LuaValue.argerror(1, "value expected")
            var m: LuaValue = args.checknumber(1)
            var i = 2
            val n: Int = args.narg()
            while (i <= n) {
                val v: LuaValue = args.checknumber(i)
                if (m.lt_b(v)) m = v
                ++i
            }
            return m
        }
    }

    internal class min : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            // With nothing to compare there is no answer, and the complaint is
            // about the missing argument rather than about its type.
            if (args.narg() < 1) LuaValue.argerror(1, "value expected")
            var m: LuaValue = args.checknumber(1)
            var i = 2
            val n: Int = args.narg()
            while (i <= n) {
                val v: LuaValue = args.checknumber(i)
                if (v.lt_b(m)) m = v
                ++i
            }
            return m
        }
    }

    internal class modf : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val n: LuaValue = args.arg1()!!
            /* number is its own integer part, no fractional part */
            if (n.islong()) return (varargsOf(n, valueOf(0.0)))!!
            val x: Double = n.checkdouble()
            /* integer part (rounds toward zero) */
            val intPart: Double = if (x > 0) kotlin.math.floor(x) else kotlin.math.ceil(x)
            /* fractional part (test needed for inf/-inf) */
            val fracPart = if (x == intPart) 0.0 else x - intPart
            return (varargsOf(valueOf(intPart), valueOf(fracPart)))!!
        }
    }

    /**
     * The generator Lua 5.5 uses: xoshiro256**.
     *
     * Reproduced exactly, seeding included, so a chunk that seeds the generator
     * and records what came out gets the same sequence here as it would from
     * the reference interpreter.
     */
    internal class Xoshiro256 {
        private var s0: Long = 0
        private var s1: Long = 0
        private var s2: Long = 0
        private var s3: Long = 0

        init {
            // Something varying, so an unseeded program does not repeat itself.
            seed(Random.Default.nextLong(), Random.Default.nextLong())
        }

        fun seed(n1: Long, n2: Long) {
            s0 = n1
            s1 = 0xFF // never all-zero, which the generator cannot leave
            s2 = n2
            s3 = 0
            // Discarded, to spread the seed through the whole state.
            repeat(16) { next() }
        }

        fun next(): Long {
            val result: Long = rotl(s1 * 5L, 7) * 9L
            val t: Long = s1 shl 17
            s2 = s2 xor s0
            s3 = s3 xor s1
            s1 = s1 xor s2
            s0 = s0 xor s3
            s2 = s2 xor t
            s3 = rotl(s3, 45)
            return result
        }

        /** A float in `[0,1)`, taking the top 53 bits - a double's whole mantissa. */
        fun nextDouble(): Double = (next() ushr 11).toDouble() * SCALE

        private fun rotl(x: Long, n: Int): Long = (x shl n) or (x ushr (64 - n))

        private companion object {
            /** 2^-53: one unit in the last place of the mantissa. */
            const val SCALE: Double = 1.0 / 9007199254740992.0
        }
    }

    /**
     * `math.random ([m [, n]])`.
     *
     * With no argument a float in `[0,1)`; with one, an integer in `[1,m]`;
     * with two, one in `[m,n]`. The whole 64-bit range is available, so
     * `math.random(1, math.maxinteger)` works, and `math.random(0)` answers an
     * integer with every bit drawn at random.
     */
    internal class random : VarArgFunction() {
        var generator: Xoshiro256 = Xoshiro256()

        override fun invoke(args: Varargs): Varargs {
            // Drawn before the arguments are examined, as upstream draws it, so
            // the sequence does not depend on how the call was written.
            val draw: Long = generator.next()
            val low: Long
            val high: Long
            when (args.narg()) {
                0 -> return valueOf((draw ushr 11).toDouble() * (1.0 / 9007199254740992.0))!!
                1 -> {
                    val m: Long = args.checklong(1)
                    // random(0) is the one case that is not a range: it asks
                    // for an integer with all of its bits set at random.
                    if (m == 0L) return valueOf(draw)!!
                    low = 1L
                    high = m
                }

                2 -> {
                    low = args.checklong(1)
                    high = args.checklong(2)
                }

                else -> return LuaValue.error("wrong number of arguments")!!
            }
            args.argcheck(low <= high, 1, "interval is empty")
            return valueOf(low + project(draw, high - low))!!
        }

        /**
         * An unbiased draw in `[0, span]`, treating both as unsigned.
         *
         * Taking a remainder would favour the low end of the range, so the
         * draw is masked down to the next power of two minus one and retried
         * until it lands inside, as upstream does.
         */
        private fun project(draw: Long, span: Long): Long {
            if (span and (span + 1) == 0L) return draw and span // span + 1 is a power of two
            var limit = span
            limit = limit or (limit ushr 1)
            limit = limit or (limit ushr 2)
            limit = limit or (limit ushr 4)
            limit = limit or (limit ushr 8)
            limit = limit or (limit ushr 16)
            limit = limit or (limit ushr 32)
            var value = draw and limit
            while (value.toULong() > span.toULong()) value = generator.next() and limit
            return value
        }
    }

    /**
     * `math.randomseed ([x [, y]])`.
     *
     * Seeds the generator and answers the two halves of the seed it used, so a
     * run that wants to be repeatable can record them.
     */
    internal class randomseed(val random: MathLib.random) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val x: Long
            val y: Long
            if (args.isnoneornil(1)) {
                x = Random.Default.nextLong()
                y = random.generator.next()
            } else {
                x = args.checklong(1)
                y = args.optlong(2, 0L)
            }
            random.generator.seed(x, y)
            return varargsOf(valueOf(x), valueOf(y))!!
        }
    }

    /**
     * Hook to override the default `^` / `math.pow` behavior with a different
     * implementation. The default is already [kotlin.math.pow] on every target.
     */
    open fun dpow_lib(a: Double, b: Double): Double {
        return net.blueva.luak.lib.MathLib.Companion.dpow_default(a, b)
    }

    companion object {
        /** 2^1000, the largest step ldexp can take without leaving the range. */
        internal val TWO_POW_1000: Double = run {
            var result = 1.0
            repeat(1000) { result *= 2.0 }
            result
        }

        /** A float result becomes an integer when it is representable as one. */
        /**
         * The integer [value] denotes, or the float itself when it denotes none.
         *
         * The range has to be checked as well as the round trip: converting a
         * double past the integer range saturates at the end, and converting
         * that back lands on the same double, so `2^63` would otherwise look
         * like an integer.
         */
        internal fun narrowToInteger(value: Double): LuaValue {
            if (value < -9.2233720368547758E18 || value >= 9.2233720368547758E18) {
                return LuaValue.valueOf(value)
            }
            val asLong: Long = value.toLong()
            return if (asLong.toDouble() == value) LuaValue.valueOf(asLong) else LuaValue.valueOf(value)
        }

        /** Pointer to the latest MathLib instance, used only to dispatch
         * math.exp to tha correct platform math library.
         */
        var MATHLIB: MathLib? = null

        /** compute power using installed math library, or default if there is no math library installed  */
        fun dpow(a: Double, b: Double): LuaValue {
            return LuaDouble.valueOf(net.blueva.luak.lib.MathLib.Companion.dpow_d(a, b))!!
        }

        fun dpow_d(a: Double, b: Double): Double {
            val installed: MathLib? = net.blueva.luak.lib.MathLib.Companion.MATHLIB
            return installed?.dpow_lib(a, b) ?: net.blueva.luak.lib.MathLib.Companion.dpow_default(a, b)
        }

        /**
         * Accurate power on every target; [kotlin.math.pow] is part of the
         * Kotlin standard library, so nothing here needs a platform-specific
         * override any more. [dpow_lib] survives only as an extension point
         * for embedders that want a different implementation.
         */
        protected fun dpow_default(a: Double, b: Double): Double = a.pow(b)
    }
}
