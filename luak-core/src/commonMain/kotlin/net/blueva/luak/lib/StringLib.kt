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

import net.blueva.luak.Buffer
import net.blueva.luak.LuaClosure
import net.blueva.luak.LuaString
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs
import net.blueva.luak.compiler.DumpState
import net.blueva.luak.io.ByteArrayOutputStream
import net.blueva.luak.io.IOException

/**
 * Subclass of [LibFunction] which implements the lua standard `string`
 * library.
 * 
 * 
 * Typically, this library is included as part of a call to
 * [net.blueva.luak.lib.LuaPlatform.standardGlobals]
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * println(globals.get("string").get("upper").call(LuaValue.valueOf("abcde")))
 * ```
 * 
 * 
 * To instantiate and use it directly,
 * link it into your globals table via [LuaValue.load] using code such as:
 * ```kotlin
 * val globals = Globals()
 * globals.load(BaseLib())
 * globals.load(PackageLib())
 * globals.load(StringLib())
 * println(globals.get("string").get("upper").call(LuaValue.valueOf("abcde")))
 * ```
 * 
 * 
 * This is a direct port of the corresponding library in C.
 * @see LibFunction
 * 
 * @see net.blueva.luak.lib.jvm.JvmPlatform
 * 
 * @see net.blueva.luak.lib.LuaPlatform
 * 
 * @see [Lua 5.2 String Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.4)
 */
open class StringLib
/** Construct a StringLib, which can be initialized by calling it with a
 * modname string, and a global environment table as arguments using
 * [.call].  */
    : TwoArgFunction() {
    /** Perform one-time initialization on the library by creating a table
     * containing the library functions, adding that table to the supplied environment,
     * adding the table to package.loaded, and returning table as the return value.
     * Creates a metatable that uses __INDEX to fall back on itself to support string
     * method operations.
     * If the shared strings metatable instance is null, will set the metatable as
     * the global shared metatable for strings.
     * <P>
     * All tables and metatables are read-write by default so if this will be used in
     * a server environment, sandboxing should be used.  In particular, the
     * [LuaString.s_metatable] table should probably be made read-only.
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env the environment to load into, typically a Globals instance.
    </P> */
    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        val string: LuaTable = LuaTable()
        string.set("byte", net.blueva.luak.lib.StringLib._byte())
        string.set("char", net.blueva.luak.lib.StringLib._char())
        string.set("dump", net.blueva.luak.lib.StringLib.dump())
        string.set("find", net.blueva.luak.lib.StringLib.find())
        string.set("format", format())
        string.set("gmatch", net.blueva.luak.lib.StringLib.gmatch())
        string.set("gsub", net.blueva.luak.lib.StringLib.gsub(env as? net.blueva.luak.Globals))
        string.set("len", net.blueva.luak.lib.StringLib.len())
        string.set("lower", net.blueva.luak.lib.StringLib.lower())
        string.set("match", net.blueva.luak.lib.StringLib.match())
        string.set("pack", net.blueva.luak.lib.StringLib.pack())
        string.set("packsize", net.blueva.luak.lib.StringLib.packsize())
        string.set("rep", net.blueva.luak.lib.StringLib.rep())
        string.set("unpack", net.blueva.luak.lib.StringLib.unpack())
        string.set("reverse", net.blueva.luak.lib.StringLib.reverse())
        string.set("sub", net.blueva.luak.lib.StringLib.sub())
        string.set("upper", net.blueva.luak.lib.StringLib.upper())

        env!!.set("string", string)
        if (!env!!.get("package")!!.isnil()) env!!.get("package")!!.get("loaded")!!.set("string", string)
        if (LuaString.s_metatable == null) {
            val metatable: LuaTable = LuaValue.tableOf(arrayOf<LuaValue?>(INDEX, string))!!
            // Since 5.4 the arithmetic coercion of strings lives here rather
            // than in the VM, which is what makes `"a" + 1` report "attempt to
            // add a 'string' with a 'number'" instead of a generic arithmetic
            // error, and what lets the other operand's metamethod have a turn.
            metatable.set(ADD, StringArith(ADD, "add"))
            metatable.set(SUB, StringArith(SUB, "sub"))
            metatable.set(MUL, StringArith(MUL, "mul"))
            metatable.set(MOD, StringArith(MOD, "mod"))
            metatable.set(POW, StringArith(POW, "pow"))
            metatable.set(DIV, StringArith(DIV, "div"))
            metatable.set(IDIV, StringArith(IDIV, "idiv"))
            metatable.set(UNM, StringArith(UNM, "unm"))
            LuaString.s_metatable = metatable
        }
        return string
    }

    /** `string.pack (fmt, v1, v2, ...)`, from Lua 5.3. */
    internal class pack : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = net.blueva.luak.lib.StringPack.pack(args)
    }

    /** `string.packsize (fmt)`, from Lua 5.3. */
    internal class packsize : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = net.blueva.luak.lib.StringPack.packsize(args)
    }

    /** `string.unpack (fmt, s [, pos])`, from Lua 5.3. */
    internal class unpack : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = net.blueva.luak.lib.StringPack.unpack(args)
    }

    /**
     * One arithmetic metamethod of the string metatable.
     *
     * It mirrors upstream's `arith` in `lstrlib.c`: if both operands denote
     * numbers the operation goes ahead on those numbers, and otherwise the
     * right-hand operand is offered its own metamethod - unless it is a string
     * too, in which case there is nothing left to try and the operation is an
     * error naming both types.
     *
     * @param event the metatag this handler is registered under
     * @param opname the name that appears in the error message
     */
    internal class StringArith(private val event: LuaString, private val opname: String) : TwoArgFunction() {
        override fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue {
            val left: LuaValue = arg1 ?: NIL
            // Lua hands a unary operator its operand twice, so a caller that
            // passed only one gets the same value for both.
            // Compared by value: the metatag constants are getters that build a
            // fresh LuaString on every read, so identity never holds.
            val right: LuaValue = if (event == UNM) left else (arg2 ?: NIL)
            val leftNumber: LuaValue = left.tonumber()
            val rightNumber: LuaValue = right.tonumber()
            if (!leftNumber.isnil() && !rightNumber.isnil()) return apply(leftNumber, rightNumber)
            if (right.type() != LuaValue.TSTRING) {
                val handler: LuaValue = right.metatag(event)
                if (!handler.isnil()) return handler.call(left, right)!!
            }
            return LuaValue.error(
                "attempt to " + opname + " a '" + left.typename() + "' with a '" + right.typename() + "'",
            )!!
        }

        private fun apply(left: LuaValue, right: LuaValue): LuaValue = when (event) {
            ADD -> left.add(right)
            SUB -> left.sub(right)
            MUL -> left.mul(right)
            MOD -> left.mod(right)
            POW -> left.pow(right)
            DIV -> left.div(right)
            IDIV -> left.idiv(right)
            else -> left.neg()
        }
    }

    /**
     * string.byte (s [, i [, j]])
     * 
     * Returns the internal numerical codes of the
     * characters s[i], s[i+1], ..., s[j]. The default value for i is 1; the
     * default value for j is i.
     * 
     * Note that numerical codes are not necessarily portable across platforms.
     * 
     * @param args the calling args
     */
    internal class _byte : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val s: LuaString = args.checkstring(1)
            val l: Int = s.m_length
            var posi: Int = net.blueva.luak.lib.StringLib.Companion.posrelat(args.optint(2, 1), l)
            var pose: Int = net.blueva.luak.lib.StringLib.Companion.posrelat(args.optint(3, posi), l)
            val n: Int
            var i: Int
            if (posi <= 0) posi = 1
            if (pose > l) pose = l
            if (posi > pose) return (NONE)!! /* empty interval; return no values */
            n = (pose - posi + 1)
            if (posi + n <= pose)  /* overflow? */
                error("string slice too long")
            val v: Array<LuaValue?> = arrayOfNulls<LuaValue>(n)
            i = 0
            while (i < n) {
                v[i] = valueOf(s.luaByte(posi + i - 1))
                i++
            }
            return (varargsOf(v))!!
        }
    }

    /**
     * string.char (...)
     * 
     * Receives zero or more integers. Returns a string with length equal
     * to the number of arguments, in which each character has the internal
     * numerical code equal to its corresponding argument.
     * 
     * Note that numerical codes are not necessarily portable across platforms.
     * 
     * @param args the calling VM
     */
    internal class _char : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val n: Int = args.narg()
            val bytes = ByteArray(n)
            var i = 0
            var a = 1
            while (i < n) {
                // Checked as a whole integer, so a value far out of range is
                // rejected rather than wrapping into an acceptable byte.
                val c: Long = args.checklong(a)
                if (c < 0 || c > 255) argerror(a, "value out of range")
                bytes[i] = c.toByte()
                i++
                a++
            }
            return LuaString.valueUsing(bytes)
        }
    }

    /**
     * string.dump (function[, stripDebug])
     * 
     * Returns a string containing a binary representation of the given function,
     * so that a later loadstring on this string returns a copy of the function.
     * function must be a Lua function without upvalues.
     * Boolean param stripDebug - true to strip debugging info, false otherwise.
     * The default value for stripDebug is false.
     * 
     * TODO: port dumping code as optional add-on
     */
    internal class dump : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val f: LuaValue = args.checkfunction(1)
            // Only a Lua function has bytecode to write out; anything from the
            // library is native and has none.
            if (f !is LuaClosure) LuaValue.argerror(1, "Lua function expected")
            val baos: ByteArrayOutputStream = ByteArrayOutputStream()
            try {
                // Debug information is kept unless the caller asks for it to
                // go: a dump that still names its upvalues is the useful one.
                DumpState.dump((f as LuaClosure).p, baos, args.optboolean(2, false))
                return LuaString.valueUsing(baos.toByteArray())
            } catch (e: IOException) {
                return (error(e.message))!!
            }
        }
    }

    /**
     * string.find (s, pattern [, init [, plain]])
     * 
     * Looks for the first match of pattern in the string s.
     * If it finds a match, then find returns the indices of s
     * where this occurrence starts and ends; otherwise, it returns nil.
     * A third, optional numerical argument init specifies where to start the search;
     * its default value is 1 and may be negative. A value of true as a fourth,
     * optional argument plain turns off the pattern matching facilities,
     * so the function does a plain "find substring" operation,
     * with no characters in pattern being considered "magic".
     * Note that if plain is given, then init must be given as well.
     * 
     * If the pattern has captures, then in a successful match the captured values
     * are also returned, after the two indices.
     */
    internal class find : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            return net.blueva.luak.lib.StringLib.Companion.str_find_aux(args, true)
        }
    }

    /**
     * string.format (formatstring, ...)
     * 
     * Returns a formatted version of its variable number of arguments following
     * the description given in its first argument (which must be a string).
     * The format string follows the same rules as the printf family of standard C functions.
     * The only differences are that the options/modifiers *, l, L, n, p, and h are not supported
     * and that there is an extra option, q. The q option formats a string in a form suitable
     * to be safely read back by the Lua interpreter: the string is written between double quotes,
     * and all double quotes, newlines, embedded zeros, and backslashes in the string are correctly
     * escaped when written. For instance, the call
     * string.format('%q', 'a string with "quotes" and \n new line')
     * 
     * will produce the string:
     * "a string with \"quotes\" and \
     * new line"
     * 
     * The options c, d, E, e, f, g, G, i, o, u, X, and x all expect a number as argument,
     * whereas q and s expect a string.
     * 
     * This function does not accept string values containing embedded zeros,
     * except as arguments to the q option.
     */
    internal inner class format : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val fmt: LuaString = args.checkstring(1)
            val n: Int = fmt.length()
            val result: Buffer = Buffer(n)
            var arg = 1
            var c: Int

            var i = 0
            while (i < n) {
                when (fmt.luaByte(i++).also { c = it }) {
                    '\n'.code -> result.append("\n")
                    net.blueva.luak.lib.StringLib.Companion.L_ESC -> if (i < n) {
                        if ((fmt.luaByte(i).also { c = it }) == net.blueva.luak.lib.StringLib.Companion.L_ESC) {
                            ++i
                            result.append(net.blueva.luak.lib.StringLib.Companion.L_ESC.toByte())
                        } else {
                            // A missing argument is reported before the
                            // specification is even looked at, as upstream does.
                            if (++arg > args.narg()) LuaValue.argerror(arg, "no value")
                            val fdsc: FormatDesc = FormatDesc(fmt, i)
                            i += fdsc.length
                            when (fdsc.conversion) {
                                'c'.code -> {
                                    fdsc.check(FLAGS_C, precision = false)
                                    fdsc.format(result, args.checkint(arg).toByte())
                                }

                                'i'.code, 'd'.code -> {
                                    val value: Long = args.checklong(arg)
                                    fdsc.check(FLAGS_I, precision = true)
                                    fdsc.format(result, value)
                                }

                                'u'.code -> {
                                    val value: Long = args.checklong(arg)
                                    fdsc.check(FLAGS_U, precision = true)
                                    fdsc.format(result, value)
                                }

                                'o'.code, 'x'.code, 'X'.code -> {
                                    val value: Long = args.checklong(arg)
                                    fdsc.check(FLAGS_X, precision = true)
                                    fdsc.format(result, value)
                                }

                                'e'.code, 'E'.code, 'f'.code, 'g'.code, 'G'.code -> {
                                    val value: Double = args.checkdouble(arg)
                                    fdsc.check(FLAGS_F, precision = true)
                                    fdsc.format(result, value)
                                }

                                'a'.code, 'A'.code -> {
                                    fdsc.check(FLAGS_F, precision = true)
                                    fdsc.formathex(
                                        result,
                                        args.checkdouble(arg),
                                        fdsc.conversion == 'A'.code,
                                    )
                                }

                                'p'.code -> {
                                    val text: LuaString =
                                        net.blueva.luak.lib.StringLib.Companion.pointer(args.arg(arg)!!)
                                    fdsc.check(FLAGS_C, precision = false)
                                    fdsc.format(result, text)
                                }

                                'q'.code -> {
                                    if (fdsc.hasmodifiers) error("specifier '%q' cannot have modifiers")
                                    net.blueva.luak.lib.StringLib.Companion.addliteral(
                                        result,
                                        args.arg(arg)!!,
                                    )
                                }

                                's'.code -> {
                                    // Lua's own conversion, so %s accepts a nil
                                    // or a table with __tostring.
                                    val s: LuaString = net.blueva.luak.lib.BaseLib
                                        .tolstring(args.arg(arg)!!).strvalue()!!
                                    if (!fdsc.hasmodifiers) {
                                        // Passed through whole, embedded zeros
                                        // and all: there is nothing to line up.
                                        result.append(s)
                                    } else {
                                        args.argcheck(
                                            s.indexOf(0.toByte(), 0) < 0,
                                            arg,
                                            "string contains zeros",
                                        )
                                        fdsc.check(FLAGS_C, precision = true)
                                        // Without a precision there is nothing
                                        // to truncate, and a long string is
                                        // cheaper to pass through than to pad.
                                        if (fdsc.precision < 0 && s.length() >= 100) {
                                            result.append(s)
                                        } else {
                                            fdsc.format(result, s)
                                        }
                                    }
                                }

                                else -> error("invalid conversion '" + fdsc.src + "' to 'format'")
                            }
                        }
                    }

                    else -> result.append(c.toByte())
                }
            }

            return result.tostring()
        }
    }

    /** As long as a conversion specification may be, upstream's `MAX_FORMAT`. */
    private val MAX_FORMAT_LENGTH: Int = 32

    /** Flags for `%a`, `%A`, `%e`, `%E`, `%f`, `%g` and `%G`. */
    private val FLAGS_F: String = "-+#0 "

    /** Flags for `%o`, `%x` and `%X`. */
    private val FLAGS_X: String = "-#0"

    /** Flags for `%d` and `%i`. */
    private val FLAGS_I: String = "-+0 "

    /** Flags for `%u`. */
    private val FLAGS_U: String = "-0"

    /** Flags for `%c`, `%p` and `%s`. */
    private val FLAGS_C: String = "-"

    /** ASCII only, since a conversion letter is never a byte above 127. */
    private fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    internal inner class FormatDesc(strfrmt: LuaString, start: Int) {
        private var leftAdjust = false
        private var zeroPad: Boolean = false
        private var explicitPlus = false
        private var space = false
        private var alternateForm = false
        private var width: Int
        var precision: Int

        val conversion: Int
        val length: Int

        /** The specification as written, `%` and conversion letter included. */
        val src: String

        init {
            val n: Int = strfrmt.length()
            // Flags, width and precision are read as one span, exactly as
            // upstream's 'getformat' reads them: nothing is judged here, so a
            // malformed specification is still available to be quoted back.
            var p = start
            while (p < n && net.blueva.luak.lib.StringLib.Companion.isSpecSpan(strfrmt.luaByte(p))) p++
            // Upstream counts the conversion letter itself, and over there the
            // string is NUL-terminated, so a specification that runs off the
            // end still counts one character.
            if (p - start + 1 >= MAX_FORMAT_LENGTH - 10) error("invalid format (too long)")
            conversion = if (p < n) strfrmt.luaByte(p) else 0
            length = p - start + 1
            src = "%" + strfrmt.substring(start, if (p < n) p + 1 else n).tojstring()

            var scan = start
            var reading = true
            while (reading && scan < p) {
                when (strfrmt.luaByte(scan)) {
                    '-'.code -> leftAdjust = true
                    '+'.code -> explicitPlus = true
                    ' '.code -> space = true
                    '#'.code -> alternateForm = true
                    '0'.code -> zeroPad = true
                    else -> reading = false
                }
                if (reading) scan++
            }

            width = -1
            if (scan < p && strfrmt.luaByte(scan).toChar() in '0'..'9') {
                width = strfrmt.luaByte(scan++) - '0'.code
                if (scan < p && strfrmt.luaByte(scan).toChar() in '0'..'9') {
                    width = width * 10 + (strfrmt.luaByte(scan++) - '0'.code)
                }
            }

            precision = -1
            if (scan < p && strfrmt.luaByte(scan) == '.'.code) {
                scan++
                // A bare '.' is a precision of zero, not an absent one.
                precision = 0
                if (scan < p && strfrmt.luaByte(scan).toChar() in '0'..'9') {
                    precision = strfrmt.luaByte(scan++) - '0'.code
                    if (scan < p && strfrmt.luaByte(scan).toChar() in '0'..'9') {
                        precision = precision * 10 + (strfrmt.luaByte(scan++) - '0'.code)
                    }
                }
            }

            zeroPad = zeroPad and !leftAdjust // '-' overrides '0'
        }

        /**
         * Refuses a specification C's `printf` would not accept.
         *
         * [flags] are the ones this conversion takes and [precision] says
         * whether it takes one at all; what is left over after them, a width of
         * at most two digits and a precision of at most two more, has to be the
         * conversion letter itself. A width cannot start with a zero, since
         * that reads as the padding flag.
         */
        fun check(flags: String, precision: Boolean) {
            var i = 1 // past the '%'
            while (i < src.length && src[i] in flags) i++
            if (i < src.length && src[i] != '0') {
                i = twoDigits(i)
                if (precision && i < src.length && src[i] == '.') i = twoDigits(i + 1)
            }
            if (i >= src.length || !isAsciiLetter(src[i])) {
                error("invalid conversion specification: '" + src + "'")
            }
        }

        private fun twoDigits(from: Int): Int {
            var i = from
            if (i < src.length && src[i] in '0'..'9') {
                i++
                if (i < src.length && src[i] in '0'..'9') i++
            }
            return i
        }

        fun format(buf: Buffer, c: Byte) {
            // A width pads the single character, on whichever side the flags ask.
            val padding: Int = width - 1
            if (padding > 0 && !leftAdjust) pad(buf, ' ', padding)
            buf.append(c)
            if (padding > 0 && leftAdjust) pad(buf, ' ', padding)
        }

        fun format(buf: Buffer, number: Long) {
            var digits: String

            if (number == 0L && precision == 0) {
                digits = ""
            } else {
                val radix: Int
                val unsigned: Boolean
                when (conversion) {
                    'x'.code, 'X'.code -> { radix = 16; unsigned = true }
                    'o'.code -> { radix = 8; unsigned = true }
                    'u'.code -> { radix = 10; unsigned = true }
                    else -> { radix = 10; unsigned = false }
                }
                // Hexadecimal and octal read the value as unsigned, the way C
                // does, so -1 comes out as all ones rather than with a sign.
                digits = if (unsigned) number.toULong().toString(radix) else number.toString(radix)
                if (conversion == 'X'.code) digits = digits.uppercase()
                // The '#' flag asks for the form a Lua numeral would take, so
                // the base is spelled out: 0 for octal, 0x or 0X for hex.
                if (alternateForm && number != 0L) {
                    digits = when (conversion) {
                        'o'.code -> "0" + digits
                        'x'.code -> "0x" + digits
                        'X'.code -> "0X" + digits
                        else -> digits
                    }
                }
            }

            var minwidth: Int = digits.length
            var ndigits = minwidth
            val nzeros: Int

            if (number < 0 && !digits.startsWith("-")) {
                // Nothing to do: an unsigned conversion has no sign to skip.
            } else if (number < 0) {
                ndigits--
            } else if (explicitPlus || space) {
                minwidth++
            }

            if (precision > ndigits) nzeros = precision - ndigits
            else if (precision == -1 && zeroPad && width > minwidth) nzeros = width - minwidth
            else nzeros = 0

            minwidth += nzeros
            val nspaces = if (width > minwidth) width - minwidth else 0

            if (!leftAdjust) pad(buf, ' ', nspaces)

            if (number < 0) {
                if (nzeros > 0) {
                    buf.append('-'.code.toByte())
                    digits = digits.substring(1)
                }
            } else if (explicitPlus) {
                buf.append('+'.code.toByte())
            } else if (space) {
                buf.append(' '.code.toByte())
            }

            if (nzeros > 0) pad(buf, '0', nzeros)

            buf.append(digits)

            if (leftAdjust) pad(buf, ' ', nspaces)
        }

        fun format(buf: Buffer, x: Double) {
            // C's float conversions, rendered from the exact decimal digits of
            // the double. Going through a host formatter instead would follow
            // the host's locale, so a machine set to a comma decimal separator
            // produced "3,14" where Lua specifies "3.14".
            val digits: Int = if (precision < 0) 6 else precision
            var text: String = when (conversion.toChar()) {
                'e' -> net.blueva.luak.DecimalFormat.e(x, digits, upper = false)
                'E' -> net.blueva.luak.DecimalFormat.e(x, digits, upper = true)
                'f', 'F' -> {
                    val rendered: String = net.blueva.luak.DecimalFormat.f(x, digits)
                    // The '#' flag keeps the decimal point even with no digits
                    // after it, so the value still reads as a float.
                    if (alternateForm && digits == 0 && !rendered.contains('.')) {
                        rendered + "."
                    } else {
                        rendered
                    }
                }
                'G' -> net.blueva.luak.DecimalFormat.g(x, digits).uppercase()
                else -> net.blueva.luak.DecimalFormat.g(x, digits)
            }
            if (!text.startsWith("-")) {
                if (explicitPlus) text = "+" + text else if (space) text = " " + text
            }
            val padding: Int = width - text.length
            if (padding > 0) {
                when {
                    leftAdjust -> text = text + " ".repeat(padding)
                    // Zero padding goes after the sign, and never applies to
                    // 'inf' or 'nan', which have no digits to pad.
                    zeroPad && x.isFinite() -> {
                        val signLength: Int = if (text[0] == '-' || text[0] == '+' || text[0] == ' ') 1 else 0
                        text = text.substring(0, signLength) + "0".repeat(padding) + text.substring(signLength)
                    }

                    else -> text = " ".repeat(padding) + text
                }
            }
            buf.append(text)
        }

        /** True when anything was written between the `%` and the letter. */
        val hasmodifiers: Boolean
            get() = src.length > 2

        /**
         * `%a`: the value in hexadecimal, with this descriptor's sign and width.
         *
         * A precision here counts hexadecimal digits after the point rather
         * than characters, so the padding is applied separately from the
         * rendering.
         */
        fun formathex(buf: Buffer, value: Double, upper: Boolean) {
            var text: String = net.blueva.luak.DecimalFormat.hex(value, upper, precision)
            if (!text.startsWith("-")) {
                if (explicitPlus) text = "+" + text else if (space) text = " " + text
            }
            val padding: Int = width - text.length
            if (padding > 0) {
                text = if (leftAdjust) text + " ".repeat(padding) else " ".repeat(padding) + text
            }
            buf.append(text)
        }

        fun format(buf: Buffer, s: LuaString) {
            var s: LuaString = s
            // A precision on %s is a maximum length, and a width pads to it.
            if (precision >= 0 && s.length() > precision) s = s.substring(0, precision)
            val padding: Int = width - s.length()
            if (padding > 0 && !leftAdjust) pad(buf, ' ', padding)
            buf.append(s)
            if (padding > 0 && leftAdjust) pad(buf, ' ', padding)
        }

        fun pad(buf: Buffer, c: Char, n: Int) {
            var n = n
            val b: Byte = c.code.toByte()
            while (n-- > 0) buf.append(b)
        }

    }

    /**
     * string.gmatch (s, pattern)
     * 
     * Returns an iterator function that, each time it is called, returns the next captures
     * from pattern over string s. If pattern specifies no captures, then the
     * whole match is produced in each call.
     * 
     * As an example, the following loop
     * s = "hello world from Lua"
     * for w in string.gmatch(s, "%a+") do
     * print(w)
     * end
     * 
     * will iterate over all the words from string s, printing one per line.
     * The next example collects all pairs key=value from the given string into a table:
     * t = {}
     * s = "from=world, to=Lua"
     * for k, v in string.gmatch(s, "(%w+)=(%w+)") do
     * t[k] = v
     * end
     * 
     * For this function, a '^' at the start of a pattern does not work as an anchor,
     * as this would prevent the iteration.
     */
    internal class gmatch : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val src: LuaString = args.checkstring(1)
            val pat: LuaString = args.checkstring(2)
            // Since 5.4 a third argument says where to start, counted the way
            // string.find counts, so a negative one is from the end.
            val init: Int = net.blueva.luak.lib.StringLib.Companion.posrelat(
                args.optint(3, 1),
                src.length(),
            )
            val start: Int = when {
                init < 1 -> 0
                init > src.length() + 1 -> src.length() + 1
                else -> init - 1
            }
            return net.blueva.luak.lib.StringLib.GMatchAux(args, src, pat, start)
        }
    }

    internal class GMatchAux(args: Varargs, src: LuaString, pat: LuaString, start: Int = 0) :
        VarArgFunction() {
        private val srclen: Int
        private val ms: MatchState
        private var soffset: Int
        private var lastmatch: Int

        init {
            this.srclen = src.length()
            this.ms = net.blueva.luak.lib.StringLib.MatchState(args, src, pat)
            this.soffset = start
            this.lastmatch = -1
        }

        // The match state is what this iterator carries between calls, which
        // is what an upvalue is.
        override fun nupvalues(): Int = 1

        override fun upvaluestate(n: Int): Any? = if (n == 1) ms else null

        override fun invoke(args: Varargs): Varargs {
            while (soffset <= srclen) {
                ms.reset()
                val res = ms.match(soffset, 0)
                if (res >= 0 && res != lastmatch) {
                    val soff = soffset
                    soffset = res
                    lastmatch = soffset
                    return ms.push_captures(true, soff, res)
                }
                soffset++
            }
            return NIL
        }
    }


    /**
     * string.gsub (s, pattern, repl [, n])
     * Returns a copy of s in which all (or the first n, if given) occurrences of the
     * pattern have been replaced by a replacement string specified by repl, which
     * may be a string, a table, or a function. gsub also returns, as its second value,
     * the total number of matches that occurred.
     * 
     * If repl is a string, then its value is used for replacement.
     * The character % works as an escape character: any sequence in repl of the form %n,
     * with n between 1 and 9, stands for the value of the n-th captured substring (see below).
     * The sequence %0 stands for the whole match. The sequence %% stands for a single %.
     * 
     * If repl is a table, then the table is queried for every match, using the first capture
     * as the key; if the pattern specifies no captures, then the whole match is used as the key.
     * 
     * If repl is a function, then this function is called every time a match occurs,
     * with all captured substrings passed as arguments, in order; if the pattern specifies
     * no captures, then the whole match is passed as a sole argument.
     * 
     * If the value returned by the table query or by the function call is a string or a number,
     * then it is used as the replacement string; otherwise, if it is false or nil,
     * then there is no replacement (that is, the original match is kept in the string).
     * 
     * Here are some examples:
     * x = string.gsub("hello world", "(%w+)", "%1 %1")
     * --> x="hello hello world world"
     * 
     * x = string.gsub("hello world", "%w+", "%0 %0", 1)
     * --> x="hello hello world"
     * 
     * x = string.gsub("hello world from Lua", "(%w+)%s*(%w+)", "%2 %1")
     * --> x="world hello Lua from"
     * 
     * x = string.gsub("home = $HOME, user = $USER", "%$(%w+)", os.getenv)
     * --> x="home = /home/roberto, user = roberto"
     * 
     * x = string.gsub("4+5 = $return 4+5$", "%$(.-)%$", function (s)
     * return loadstring(s)()
     * end)
     * --> x="4+5 = 9"
     * 
     * local t = {name="lua", version="5.1"}
     * x = string.gsub("$name-$version.tar.gz", "%$(%w+)", t)
     * --> x="lua-5.1.tar.gz"
     */
    internal class gsub(private val globals: net.blueva.luak.Globals?) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val src: LuaString = args.checkstring(1)
            val srclen: Int = src.length()
            val p: LuaString = args.checkstring(2)
            var lastmatch = -1 /* end of last match */
            val repl: LuaValue = args.arg(3)!!
            val max_s: Int = args.optint(4, srclen + 1)
            val anchor = p.length() > 0 && p.charAt(0) == '^'.code

            val lbuf: Buffer = Buffer(srclen)
            val ms: MatchState = net.blueva.luak.lib.StringLib.MatchState(args, src, p)

            var soffset = 0
            var n = 0
            // Whether anything was actually replaced. When nothing was, the
            // original string is handed back rather than an equal copy, which
            // is what lets a caller compare identities to detect a no-op.
            var changed = false
            while (n < max_s) {
                ms.reset()
                val res = ms.match(soffset, if (anchor) 1 else 0)
                if (res != -1 && res != lastmatch) {  /* match? */
                    n++
                    if (ms.add_value(lbuf, soffset, res, repl, globals?.debuglib)) changed = true
                    lastmatch = res
                    soffset = lastmatch
                } else if (soffset < srclen)  /* otherwise, skip one character */
                    lbuf.append(src.luaByte(soffset++).toByte())
                else break /* end of subject */
                if (anchor) break
            }
            if (!changed) return (varargsOf(src, valueOf(n)))!!
            lbuf.append(src.substring(soffset, srclen))
            return (varargsOf(lbuf.tostring(), valueOf(n)))!!
        }
    }

    /**
     * string.len (s)
     * 
     * Receives a string and returns its length. The empty string "" has length 0.
     * Embedded zeros are counted, so "a\000bc\000" has length 5.
     */
    internal class len : OneArgFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            return arg!!.checkstring()!!.len()
        }
    }

    /**
     * string.lower (s)
     * 
     * Receives a string and returns a copy of this string with all uppercase letters
     * changed to lowercase. All other characters are left unchanged.
     * The definition of what an uppercase letter is depends on the current locale.
     */
    internal class lower : OneArgFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            return valueOf(arg!!.checkjstring()!!.lowercase())
        }
    }

    /**
     * string.match (s, pattern [, init])
     * 
     * Looks for the first match of pattern in the string s. If it finds one,
     * then match returns the captures from the pattern; otherwise it returns
     * nil. If pattern specifies no captures, then the whole match is returned.
     * A third, optional numerical argument init specifies where to start the
     * search; its default value is 1 and may be negative.
     */
    internal class match : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            return net.blueva.luak.lib.StringLib.Companion.str_find_aux(args, false)
        }
    }

    /**
     * string.rep (s, n)
     * 
     * Returns a string that is the concatenation of n copies of the string s.
     */
    /**
     * `string.rep (s, n [, sep])`.
     *
     * The separator, from Lua 5.2, goes between copies and not around them, so
     * `("x"):rep(3, "-")` is `"x-x-x"`.
     */
    internal class rep : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val s: LuaString = args.checkstring(1)
            val n: Long = args.checklong(2)
            val sep: LuaString = if (args.isnoneornil(3)) EMPTYSTRING!! else args.checkstring(3)
            if (n <= 0L) return EMPTYSTRING!!
            val len: Int = s.length()
            val seplen: Int = sep.length()
            // Checked by division rather than by multiplying out, which would
            // wrap around and let an impossible size look acceptable.
            val perCopy: Long = len.toLong() + seplen.toLong()
            if (perCopy != 0L && n > Int.MAX_VALUE.toLong() / perCopy) {
                LuaValue.error("resulting string too large")
            }
            val total: Long = len.toLong() * n + seplen.toLong() * (n - 1)
            if (total > Int.MAX_VALUE.toLong()) LuaValue.error("resulting string too large")
            val bytes = ByteArray(total.toInt())
            var offset = 0
            var copies = 0L
            while (copies < n) {
                if (copies > 0 && seplen > 0) {
                    sep.copyInto(0, bytes, offset, seplen)
                    offset += seplen
                }
                s.copyInto(0, bytes, offset, len)
                offset += len
                copies++
            }
            return LuaString.valueUsing(bytes)
        }
    }

    /**
     * string.reverse (s)
     * 
     * Returns a string that is the string s reversed.
     */
    internal class reverse : OneArgFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            val s: LuaString = arg!!.checkstring()!!
            val n: Int = s.length()
            val b = ByteArray(n)
            var i = 0
            var j = n - 1
            while (i < n) {
                b[j] = s.luaByte(i).toByte()
                i++
                j--
            }
            return LuaString.valueUsing(b)
        }
    }

    /**
     * string.sub (s, i [, j])
     * 
     * Returns the substring of s that starts at i and continues until j;
     * i and j may be negative. If j is absent, then it is assumed to be equal to -1
     * (which is the same as the string length). In particular, the call
     * string.sub(s,1,j)
     * returns a prefix of s with length j, and
     * string.sub(s, -i)
     * returns a suffix of s with length i.
     */
    internal class sub : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val s: LuaString = args.checkstring(1)
            val l: Int = s.length()

            var start: Int = net.blueva.luak.lib.StringLib.Companion.posrelat(args.checkint(2), l)
            var end: Int = net.blueva.luak.lib.StringLib.Companion.posrelat(args.optint(3, -1), l)

            if (start < 1) start = 1
            if (end > l) end = l

            if (start <= end) {
                return s.substring(start - 1, end)
            } else {
                return EMPTYSTRING
            }
        }
    }

    /**
     * string.upper (s)
     * 
     * Receives a string and returns a copy of this string with all lowercase letters
     * changed to uppercase. All other characters are left unchanged.
     * The definition of what a lowercase letter is depends on the current locale.
     */
    internal class upper : OneArgFunction() {
        override fun call(arg: LuaValue?): LuaValue? {
            return valueOf(arg!!.checkjstring()!!.uppercase())
        }
    }

    internal class MatchState(args: Varargs, s: LuaString, pattern: LuaString) {
        var matchdepth: Int /* control for recursive depth (to avoid C stack overflow) */
        val s: LuaString
        val p: LuaString
        val args: Varargs
        var level: Int
        var cinit: IntArray?
        var clen: IntArray?

        init {
            this.s = s
            this.p = pattern
            this.args = args
            this.level = 0
            this.cinit = IntArray(net.blueva.luak.lib.StringLib.Companion.MAX_CAPTURES)
            this.clen = IntArray(net.blueva.luak.lib.StringLib.Companion.MAX_CAPTURES)
            this.matchdepth = net.blueva.luak.lib.StringLib.Companion.MAXCCALLS
        }

        fun reset() {
            level = 0
            this.matchdepth = net.blueva.luak.lib.StringLib.Companion.MAXCCALLS
        }

        private fun add_s(lbuf: Buffer, news: LuaString, soff: Int, e: Int) {
            val l: Int = news.length()
            var i = 0
            while (i < l) {
                var b = news.luaByte(i).toByte()
                if (b.toInt() != net.blueva.luak.lib.StringLib.Companion.L_ESC) {
                    lbuf.append(b)
                } else {
                    ++i // skip ESC
                    b = (if (i < l) news.luaByte(i) else 0).toByte()
                    if (!Char(b.toUShort()).isDigit()) {
                        if (b.toInt() != net.blueva.luak.lib.StringLib.Companion.L_ESC) error(
                            "invalid use of '" + net.blueva.luak.lib.StringLib.Companion.L_ESC.toChar() +
                                    "' in replacement string: after '" + net.blueva.luak.lib.StringLib.Companion.L_ESC.toChar() +
                                    "' must be '0'-'9' or '" + net.blueva.luak.lib.StringLib.Companion.L_ESC.toChar() +
                                    "', but found " + (if (i < l) ("symbol '" + Char(b.toUShort()) + "' with code " + b +
                                    " at pos " + (i + 1)) else "end of string")
                        )
                        lbuf.append(b)
                    } else if (b == '0'.code.toByte()) {
                        lbuf.append(s.substring(soff, e))
                    } else {
                        lbuf.append((push_onecapture(b - '1'.code.toByte(), soff, e).strvalue())!!)
                    }
                }
                ++i
            }
        }

        /**
         * Appends the replacement for one match.
         *
         * @return true when something was actually replaced; a function or
         *   table that answers nil or false leaves the matched text as it was
         */
        @kotlin.jvm.JvmOverloads
        fun add_value(
            lbuf: Buffer,
            soffset: Int,
            end: Int,
            repl: LuaValue,
            debuglib: DebugLib? = null,
        ): Boolean {
            var repl: LuaValue = repl
            when (repl.type()) {
                LuaValue.TSTRING, LuaValue.TNUMBER -> {
                    add_s(lbuf, (repl.strvalue())!!, soffset, end)
                    return true
                }

                // Through the library's own way of calling back, so that a
                // function of the library's own can be named in an error.
                LuaValue.TFUNCTION -> repl =
                    callback(debuglib, repl, push_captures(true, soffset, end)!!).arg1()!!
                LuaValue.TTABLE ->                // Need to call push_onecapture here for the error checking
                    repl = repl.get(push_onecapture(0, soffset, end))

                else -> {
                    error("bad argument: string/function/table expected")
                    return false
                }
            }

            if (!repl.toboolean()) {
                lbuf.append(s.substring(soffset, end))
                return false
            }
            if (!repl.isstring()) {
                error("invalid replacement value (a " + repl.typename() + ")")
            }
            lbuf.append((repl.strvalue())!!)
            return true
        }

        fun push_captures(wholeMatch: Boolean, soff: Int, end: Int): Varargs {
            val nlevels = if (this.level == 0 && wholeMatch) 1 else this.level
            when (nlevels) {
                0 -> return (NONE)!!
                1 -> return push_onecapture(0, soff, end)
            }
            val v: Array<LuaValue?> = arrayOfNulls<LuaValue>(nlevels)
            for (i in 0..<nlevels) v[i] = push_onecapture(i, soff, end)
            return (varargsOf(v))!!
        }

        private fun push_onecapture(i: Int, soff: Int, end: Int): LuaValue {
            if (i >= this.level) {
                if (i == 0) {
                    return s.substring(soff, end)
                } else {
                    return (error("invalid capture index %" + (i + 1)))!!
                }
            } else {
                val l = clen!![i]
                if (l == net.blueva.luak.lib.StringLib.Companion.CAP_UNFINISHED) {
                    return (error("unfinished capture"))!!
                }
                if (l == net.blueva.luak.lib.StringLib.Companion.CAP_POSITION) {
                    return valueOf(cinit!![i] + 1)
                } else {
                    val begin = cinit!![i]
                    return s.substring(begin, begin + l)
                }
            }
        }

        private fun check_capture(l: Int): Int {
            var l = l
            l -= '1'.code
            if (l < 0 || l >= level || this.clen!![l] == net.blueva.luak.lib.StringLib.Companion.CAP_UNFINISHED) {
                error("invalid capture index %" + (l + 1))
            }
            return l
        }

        private fun capture_to_close(): Int {
            var level = this.level
            level--
            while (level >= 0) {
                if (clen!![level] == net.blueva.luak.lib.StringLib.Companion.CAP_UNFINISHED) return level
                level--
            }
            error("invalid pattern capture")
            return 0
        }

        fun classend(poffset: Int): Int {
            var poffset = poffset
            when (p.luaByte(poffset++)) {
                net.blueva.luak.lib.StringLib.Companion.L_ESC -> {
                    if (poffset == p.length()) {
                        error("malformed pattern (ends with '%')")
                    }
                    return poffset + 1
                }

                '['.code -> {
                    if (poffset != p.length() && p.luaByte(poffset) == '^'.code) poffset++
                    do {
                        if (poffset == p.length()) {
                            error("malformed pattern (missing ']')")
                        }
                        if (p.luaByte(poffset++) === net.blueva.luak.lib.StringLib.Companion.L_ESC && poffset < p.length()) poffset++ /* skip escapes (e.g. '%]') */
                    } while (poffset == p.length() || p.luaByte(poffset) != ']'.code)
                    return poffset + 1
                }

                else -> return poffset
            }
        }

        fun matchbracketclass(c: Int, poff: Int, ec: Int): Boolean {
            var poff = poff
            var sig = true
            if (p.luaByte(poff + 1) == '^'.code) {
                sig = false
                poff++
            }
            while (++poff < ec) {
                if (p.luaByte(poff) === net.blueva.luak.lib.StringLib.Companion.L_ESC) {
                    poff++
                    if (net.blueva.luak.lib.StringLib.MatchState.Companion.match_class(c, p.luaByte(poff))) return sig
                } else if ((p.luaByte(poff + 1) == '-'.code) && (poff + 2 < ec)) {
                    poff += 2
                    if (p.luaByte(poff - 2) <= c && c <= p.luaByte(poff)) return sig
                } else if (p.luaByte(poff) === c) return sig
            }
            return !sig
        }

        fun singlematch(c: Int, poff: Int, ep: Int): Boolean {
            when (p.luaByte(poff)) {
                '.'.code -> return true
                net.blueva.luak.lib.StringLib.Companion.L_ESC -> return net.blueva.luak.lib.StringLib.MatchState.Companion.match_class(
                    c,
                    p.luaByte(poff + 1)
                )

                '['.code -> return matchbracketclass(c, poff, ep - 1)
                else -> return p.luaByte(poff) === c
            }
        }

        /**
         * Perform pattern matching. If there is a match, returns offset into s
         * where match ends, otherwise returns -1.
         */
        fun match(soffset: Int, poffset: Int): Int {
            var soffset = soffset
            var poffset = poffset
            if (matchdepth-- == 0) error("pattern too complex")
            try {
                while (true) {
                    // Check if we are at the end of the pattern -
                    // equivalent to the '\0' case in the C version, but our pattern
                    // string is not NUL-terminated.
                    if (poffset == p.length()) return soffset
                    when (p.luaByte(poffset)) {
                        '('.code -> if (++poffset < p.length() && p.luaByte(poffset) == ')'.code) return start_capture(
                            soffset,
                            poffset + 1,
                            net.blueva.luak.lib.StringLib.Companion.CAP_POSITION
                        )
                        else return start_capture(
                            soffset,
                            poffset,
                            net.blueva.luak.lib.StringLib.Companion.CAP_UNFINISHED
                        )

                        ')'.code -> return end_capture(soffset, poffset + 1)
                        net.blueva.luak.lib.StringLib.Companion.L_ESC -> {
                            if (poffset + 1 == p.length()) error("malformed pattern (ends with '%')")
                            when (p.luaByte(poffset + 1)) {
                                'b'.code -> {
                                    soffset = matchbalance(soffset, poffset + 2)
                                    if (soffset == -1) return -1
                                    poffset += 4
                                    continue
                                }

                                'f'.code -> {
                                    poffset += 2
                                    if (poffset == p.length() || p.luaByte(poffset) != '['.code) {
                                        error("missing '[' after '%f' in pattern")
                                    }
                                    val ep = classend(poffset)
                                    val previous: Int = if (soffset == 0) '\u0000'.code else s.luaByte(soffset - 1)
                                    val next: Int = if (soffset == s.length()) '\u0000'.code else s.luaByte(soffset)
                                    if (matchbracketclass(previous, poffset, ep - 1) ||
                                        !matchbracketclass(next, poffset, ep - 1)
                                    ) return -1
                                    poffset = ep
                                    continue
                                }

                                else -> {
                                    val c: Int = p.luaByte(poffset + 1)
                                    if (c.toChar().isDigit()) {
                                        soffset = match_capture(soffset, c)
                                        if (soffset == -1) return -1
                                        return match(soffset, poffset + 2)
                                    }
                                }
                            }
                            if (poffset + 1 == p.length()) return if (soffset == s.length()) soffset else -1
                        }

                        '$'.code -> if (poffset + 1 == p.length()) return if (soffset == s.length()) soffset else -1
                    }
                    val ep = classend(poffset)
                    val m = soffset < s.length() && singlematch(s.luaByte(soffset), poffset, ep)
                    val pc: Int = if (ep < p.length()) p.luaByte(ep) else '\u0000'.code

                    when (pc) {
                        '?'.code -> {
                            var res: Int = -1
                            if (m && ((match(soffset + 1, ep + 1).also { res = it }) != -1)) return res
                            poffset = ep + 1
                            continue
                        }

                        '*'.code -> return max_expand(soffset, poffset, ep)
                        '+'.code -> return (if (m) max_expand(soffset + 1, poffset, ep) else -1)
                        '-'.code -> return min_expand(soffset, poffset, ep)
                        else -> {
                            if (!m) return -1
                            soffset++
                            poffset = ep
                            continue
                        }
                    }
                }
            } finally {
                matchdepth++
            }
        }

        fun max_expand(soff: Int, poff: Int, ep: Int): Int {
            var i = 0
            while (soff + i < s.length() &&
                singlematch(s.luaByte(soff + i), poff, ep)
            ) i++
            while (i >= 0) {
                val res = match(soff + i, ep + 1)
                if (res != -1) return res
                i--
            }
            return -1
        }

        fun min_expand(soff: Int, poff: Int, ep: Int): Int {
            var soff = soff
            while (true) {
                val res = match(soff, ep + 1)
                if (res != -1) return res
                else if (soff < s.length() && singlematch(s.luaByte(soff), poff, ep)) soff++
                else return -1
            }
        }

        fun start_capture(soff: Int, poff: Int, what: Int): Int {
            val res: Int
            val level = this.level
            if (level >= net.blueva.luak.lib.StringLib.Companion.MAX_CAPTURES) {
                error("too many captures")
            }
            cinit!![level] = soff
            clen!![level] = what
            this.level = level + 1
            if ((match(soff, poff).also { res = it }) == -1) this.level--
            return res
        }

        fun end_capture(soff: Int, poff: Int): Int {
            val l = capture_to_close()
            val res: Int
            clen!![l] = soff - cinit!![l]
            if ((match(soff, poff).also { res = it }) == -1) clen!![l] =
                net.blueva.luak.lib.StringLib.Companion.CAP_UNFINISHED
            return res
        }

        fun match_capture(soff: Int, l: Int): Int {
            var l = l
            l = check_capture(l)
            val len = clen!![l]
            if ((s.length() - soff) >= len &&
                LuaString.equals(s, cinit!![l], s, soff, len)
            ) return soff + len
            else return -1
        }

        fun matchbalance(soff: Int, poff: Int): Int {
            var soff = soff
            val plen: Int = p.length()
            if (poff == plen || poff + 1 == plen) {
                error("malformed pattern (missing arguments to '%b')")
            }
            val slen: Int = s.length()
            if (soff >= slen) return -1
            val b: Int = p.luaByte(poff)
            if (s.luaByte(soff) !== b) return -1
            val e: Int = p.luaByte(poff + 1)
            var cont = 1
            while (++soff < slen) {
                if (s.luaByte(soff) === e) {
                    if (--cont == 0) return soff + 1
                } else if (s.luaByte(soff) === b) cont++
            }
            return -1
        }

        companion object {
            fun match_class(c: Int, cl: Int): Boolean {
                val lcl: Char = cl.toChar().lowercaseChar()
                val cdata: Int = net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[c].toInt()

                val res: Boolean
                when (lcl) {
                    'a' -> res = (cdata and net.blueva.luak.lib.StringLib.Companion.MASK_ALPHA.toInt()) != 0
                    'd' -> res = (cdata and net.blueva.luak.lib.StringLib.Companion.MASK_DIGIT.toInt()) != 0
                    'l' -> res = (cdata and net.blueva.luak.lib.StringLib.Companion.MASK_LOWERCASE.toInt()) != 0
                    'u' -> res = (cdata and net.blueva.luak.lib.StringLib.Companion.MASK_UPPERCASE.toInt()) != 0
                    'c' -> res = (cdata and net.blueva.luak.lib.StringLib.Companion.MASK_CONTROL.toInt()) != 0
                    'p' -> res = (cdata and net.blueva.luak.lib.StringLib.Companion.MASK_PUNCT.toInt()) != 0
                    's' -> res = (cdata and net.blueva.luak.lib.StringLib.Companion.MASK_SPACE.toInt()) != 0
                    'g' -> res =
                        (cdata and (net.blueva.luak.lib.StringLib.Companion.MASK_ALPHA.toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_DIGIT.toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_PUNCT.toInt())) != 0

                    'w' -> res =
                        (cdata and (net.blueva.luak.lib.StringLib.Companion.MASK_ALPHA.toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_DIGIT.toInt())) != 0

                    'x' -> res = (cdata and net.blueva.luak.lib.StringLib.Companion.MASK_HEXDIGIT.toInt()) != 0
                    'z' -> res = (c == 0)
                    else -> return cl == c
                }
                return if (lcl.code == cl) res else !res
            }
        }
    }

    companion object {
        /**
         * The identity `%p` reports, or `(null)` for a value that has none.
         *
         * Only the reference types have one; a number, string, boolean or nil
         * is its own value rather than something living at an address.
         */
        fun pointer(value: LuaValue): LuaString {
            return when (value.type()) {
                // A string has an identity of its own, and two equal strings
                // need not share it, so the bytes behind it are what answers.
                LuaValue.TSTRING -> LuaString.valueOf(
                    "0x" + (value as LuaString).m_bytes.hashCode().toString(16),
                )

                LuaValue.TTABLE, LuaValue.TFUNCTION, LuaValue.TTHREAD, LuaValue.TUSERDATA -> {
                    val rendered: String = value.tojstring()
                    LuaString.valueOf("0x" + rendered.substringAfter(": ", rendered))
                }

                else -> LuaString.valueOf("(null)")
            }
        }

        /**
         * `%q`: writes [value] so that reading it back gives the same value.
         *
         * A string is quoted and escaped; a float goes out in hexadecimal so no
         * digits are lost, with the values that have no literal - the
         * infinities and NaN - written as expressions that produce them.
         */
        fun addliteral(buf: Buffer, value: LuaValue) {
            when (value.type()) {
                LuaValue.TSTRING -> {
                    net.blueva.luak.lib.StringLib.Companion.addquoted(buf, value.checkstring()!!)
                    return
                }

                LuaValue.TNUMBER -> {
                    if (value.isinttype()) {
                        val n: Long = value.tolong()
                        // The minimum integer has no positive literal to negate,
                        // so it is written in hexadecimal.
                        buf.append(if (n == Long.MIN_VALUE) "0x8000000000000000" else n.toString())
                    } else {
                        val d: Double = value.todouble()
                        buf.append(
                            when {
                                d.isNaN() -> "(0/0)"
                                d == Double.POSITIVE_INFINITY -> "1e9999"
                                d == Double.NEGATIVE_INFINITY -> "-1e9999"
                                else -> net.blueva.luak.DecimalFormat.hex(d, upper = false)
                            },
                        )
                    }
                    return
                }

                LuaValue.TNIL, LuaValue.TBOOLEAN -> {
                    buf.append(value.tojstring())
                    return
                }

                else -> LuaValue.error("value has no literal form")
            }
        }

        fun addquoted(buf: Buffer, s: LuaString) {
            var c: Int
            buf.append('"'.code.toByte())
            var i = 0
            val n: Int = s.length()
            while (i < n) {
                when (s.luaByte(i).also { c = it }) {
                    '"'.code, '\\'.code, '\n'.code -> {
                        buf.append('\\'.code.toByte())
                        buf.append(c.toByte())
                    }

                    else -> if (c <= 0x1F || c == 0x7F) {
                        buf.append('\\'.code.toByte())
                        if (i + 1 == n || s.luaByte(i + 1) < '0'.code || s.luaByte(i + 1) > '9'.code) {
                            buf.append(c.toString())
                        } else {
                            buf.append('0'.code.toByte())
                            buf.append(('0'.code + c / 10).toChar().code.toByte())
                            buf.append(('0'.code + c % 10).toChar().code.toByte())
                        }
                    } else {
                        buf.append(c.toByte())
                    }
                }
                i++
            }
            buf.append('"'.code.toByte())
        }

        private val FLAGS = "-+ #0"

        /**
         * This utility method implements both string.find and string.match.
         */
        fun str_find_aux(args: Varargs, find: Boolean): Varargs {
            val s: LuaString = args.checkstring(1)
            val pat: LuaString = args.checkstring(2)
            var init: Int = args.optint(3, 1)

            if (init > 0) {
                // Starting past the end finds nothing, not even the empty
                // pattern: there is no position there to match at.
                if (init > s.length() + 1) return (if (find) NIL else NIL)!!
                init -= 1
            } else if (init < 0) {
                init = maxOf(0, s.length() + init)
            }

            val fastMatch = find && (args.arg(4)
                !!.toboolean() || pat.indexOfAny((net.blueva.luak.lib.StringLib.Companion.SPECIALS)!!) === -1)

            if (fastMatch) {
                val result: Int = s.indexOf(pat, init)
                if (result != -1) {
                    return (varargsOf(valueOf(result + 1), valueOf(result + pat.length())))!!
                }
            } else {
                val ms: MatchState = net.blueva.luak.lib.StringLib.MatchState(args, s, pat)

                var anchor = false
                var poff = 0
                if (pat.length() > 0 && pat.luaByte(0) == '^'.code) {
                    anchor = true
                    poff = 1
                }

                var soff = init
                do {
                    val res: Int
                    ms.reset()
                    if ((ms.match(soff, poff).also { res = it }) != -1) {
                        if (find) {
                            return varargsOf(valueOf(soff + 1), valueOf(res), ms.push_captures(false, soff, res))
                        } else {
                            return ms.push_captures(true, soff, res)
                        }
                    }
                } while (soff++ < s.length() && !anchor)
            }
            return NIL
        }

        fun posrelat(pos: Int, len: Int): Int {
            return if (pos >= 0) pos else len + pos + 1
        }

        // Pattern matching implementation
        private val L_ESC: Int = '%'.code

        /**
         * The bytes a conversion specification may hold before its letter.
         *
         * Flags, width and precision all live in here, which is why a run of
         * six zeros is a long specification rather than a repeated flag.
         */
        internal fun isSpecSpan(byte: Int): Boolean =
            byte == '-'.code || byte == '+'.code || byte == '#'.code || byte == '0'.code ||
                byte == ' '.code || byte == '.'.code || (byte >= '1'.code && byte <= '9'.code)
        private val SPECIALS: LuaString? = valueOf("^$*+?.([%-")
        private const val MAX_CAPTURES = 32

        private const val MAXCCALLS = 200

        private val CAP_UNFINISHED = -1
        private val CAP_POSITION = -2

        private const val MASK_ALPHA: Byte = 0x01
        private const val MASK_LOWERCASE: Byte = 0x02
        private const val MASK_UPPERCASE: Byte = 0x04
        private const val MASK_DIGIT: Byte = 0x08
        private const val MASK_PUNCT: Byte = 0x10
        private const val MASK_SPACE: Byte = 0x20
        private const val MASK_CONTROL: Byte = 0x40
        private val MASK_HEXDIGIT = 0x80.toByte()

        val CHAR_TABLE: ByteArray = ByteArray(256)

        init {

            for (i in 0..127) {
                val c = i.toChar()
                net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[i] =
                    ((if (c.isDigit()) net.blueva.luak.lib.StringLib.Companion.MASK_DIGIT else 0).toInt() or
                            (if (c.isLowerCase()) net.blueva.luak.lib.StringLib.Companion.MASK_LOWERCASE else 0).toInt() or
                            (if (c.isUpperCase()) net.blueva.luak.lib.StringLib.Companion.MASK_UPPERCASE else 0).toInt() or
                            (if (c < ' ' || c.code == 0x7F) net.blueva.luak.lib.StringLib.Companion.MASK_CONTROL else 0).toInt()).toByte()
                if ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || (c >= '0' && c <= '9')) {
                    net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[i] =
                        (net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[i].toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_HEXDIGIT.toInt()).toByte()
                }
                if ((c >= '!' && c <= '/') || (c >= ':' && c <= '@') || (c >= '[' && c <= '`') || (c >= '{' && c <= '~')) {
                    net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[i] =
                        (net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[i].toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_PUNCT.toInt()).toByte()
                }
                if ((net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[i].toInt() and (net.blueva.luak.lib.StringLib.Companion.MASK_LOWERCASE.toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_UPPERCASE.toInt())) != 0) {
                    net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[i] =
                        (net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[i].toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_ALPHA.toInt()).toByte()
                }
            }

            net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[' '.code] =
                net.blueva.luak.lib.StringLib.Companion.MASK_SPACE
            net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE['\r'.code] =
                (net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE['\r'.code].toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_SPACE.toInt()).toByte()
            net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE['\n'.code] =
                (net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE['\n'.code].toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_SPACE.toInt()).toByte()
            net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE['\t'.code] =
                (net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE['\t'.code].toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_SPACE.toInt()).toByte()
            net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[0x0B /* '\v' */] =
                (net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE[0x0B].toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_SPACE.toInt()).toByte()
            net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE['\u000C'.code] =
                (net.blueva.luak.lib.StringLib.Companion.CHAR_TABLE['\u000C'.code].toInt() or net.blueva.luak.lib.StringLib.Companion.MASK_SPACE.toInt()).toByte()
        }
    }
}
