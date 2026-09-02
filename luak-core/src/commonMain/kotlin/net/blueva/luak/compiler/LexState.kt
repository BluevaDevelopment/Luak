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
import net.blueva.luak.LuaInteger
import net.blueva.luak.LuaString
import net.blueva.luak.LuaValue
import net.blueva.luak.Prototype
import net.blueva.luak.compiler.FuncState.BlockCnt
import net.blueva.luak.lib.MathLib
import net.blueva.luak.io.IOException
import net.blueva.luak.io.InputStream

internal class LexState internal constructor(state: LuaC.CompileState?, stream: InputStream?) : Constants() {
    /* semantics information */
    internal class SemInfo {
        var r: LuaValue? = null
        var ts: LuaString? = null
    }

    internal class Token {
        var token: Int = 0
        val seminfo: SemInfo = net.blueva.luak.compiler.LexState.SemInfo()
        fun set(other: Token) {
            this.token = other.token
            this.seminfo.r = other.seminfo.r
            this.seminfo.ts = other.seminfo.ts
        }
    }

    var current: Int = 0 /* current character (charint) */
    var linenumber: Int = 0 /* input line counter */
    var lastline: Int = 0 /* line of last token `consumed' */
    internal val t: Token = net.blueva.luak.compiler.LexState.Token() /* current token */
    internal val lookahead: Token = net.blueva.luak.compiler.LexState.Token() /* look ahead token */
    internal var fs: FuncState? = null /* `FuncState' is private to the parser */
    internal var L: LuaC.CompileState?
    var z: InputStream? /* input stream */
    var buff: CharArray /* buffer for tokens */
    var nbuff: Int = 0 /* length of buffer */
    internal var dyd: Dyndata = net.blueva.luak.compiler.LexState.Dyndata() /* dynamic structures used by the parser */
    var source: LuaString? = null /* current source name */
    var envn: LuaString? = null /* environment variable name */

    /** The name `global`, recognised as a statement without being reserved. */
    private val glbn: LuaString = LuaString.valueOf("global")
    var decpoint: Byte = 0 /* locale decimal point */

    private fun isalnum(c: Int): Boolean {
        return (c >= '0'.code && c <= '9'.code)
                || (c >= 'a'.code && c <= 'z'.code)
                || (c >= 'A'.code && c <= 'Z'.code)
                || (c == '_'.code)
        // return Character.isLetterOrDigit(c);
    }

    private fun isalpha(c: Int): Boolean {
        return (c >= 'a'.code && c <= 'z'.code)
                || (c >= 'A'.code && c <= 'Z'.code)
    }

    private fun isdigit(c: Int): Boolean {
        return (c >= '0'.code && c <= '9'.code)
    }

    private fun isxdigit(c: Int): Boolean {
        return (c >= '0'.code && c <= '9'.code)
                || (c >= 'a'.code && c <= 'f'.code)
                || (c >= 'A'.code && c <= 'F'.code)
    }

    private fun isspace(c: Int): Boolean {
        return (c >= 0 && c <= ' '.code)
    }


    fun nextChar() {
        try {
            current = z!!.read()
        } catch (e: IOException) {
            e.printStackTrace()
            current = net.blueva.luak.compiler.LexState.Companion.EOZ
        }
    }

    fun currIsNewline(): Boolean {
        return current == '\n'.code || current == '\r'.code
    }

    fun save_and_next() {
        save(current)
        nextChar()
    }

    fun save(c: Int) {
        if (buff == null || nbuff + 1 > buff.size) buff = realloc(buff, nbuff * 2 + 1)
        buff[nbuff++] = c.toChar()
    }


    /**
     * How an error message names [token].
     *
     * Symbols and reserved words appear in quotes, since they are literal text
     * a program could have written. The four that stand for a whole class of
     * token - `<eof>`, `<number>`, `<name>`, `<string>` - do not, because the
     * angle brackets already mark them as descriptions rather than text.
     */
    fun token2str(token: Int): String {
        if (token < net.blueva.luak.compiler.LexState.Companion.FIRST_RESERVED) {
            return if (net.blueva.luak.compiler.LexState.Companion.iscntrl(token)) "'<\\" + token + ">'" else "'" + token.toChar() + "'"
        }
        val text: String = net.blueva.luak.compiler.LexState.Companion.luaX_tokens!![token - net.blueva.luak.compiler.LexState.Companion.FIRST_RESERVED]!!
        val describes: Boolean = token >= net.blueva.luak.compiler.LexState.Companion.TK_EOS && token <= net.blueva.luak.compiler.LexState.Companion.TK_STRING
        return if (describes) text else "'" + text + "'"
    }

    /**
     * The text to put after "near" in an error message.
     *
     * A name, string or numeral is quoted as it was actually written, taken
     * from the buffer the lexer has been filling; everything else is named the
     * way [token2str] names it.
     */
    fun txtToken(token: Int): String {
        when (token) {
            net.blueva.luak.compiler.LexState.Companion.TK_NAME, net.blueva.luak.compiler.LexState.Companion.TK_STRING, net.blueva.luak.compiler.LexState.Companion.TK_NUMBER ->
                return "'" + buff.concatToString(0, nbuff) + "'"

            else -> return token2str(token)
        }
    }

    fun lexerror(msg: String?, token: Int) {
        val cid: String? = Lua.chunkid(source!!.tojstring())
        var full: String = cid.toString() + ":" + linenumber + ": " + msg
        // "near <token>" says where the compiler was when it gave up, which is
        // what the reader needs to find the problem.
        if (token != 0) full = full + " near " + txtToken(token)
        throw LuaError(full)
    }

    fun syntaxerror(msg: String?) {
        lexerror(msg, t.token)
    }

    // only called by new_localvarliteral() for var names.
    fun newstring(s: String?): LuaString {
        return (L!!.newTString(s))!!
    }

    fun newstring(chars: CharArray?, offset: Int, len: Int): LuaString {
        return (L!!.newTString(chars!!.concatToString(offset, offset + len)))!!
    }

    fun inclinenumber() {
        val old = current
        _assert(currIsNewline())
        nextChar() /* skip '\n' or '\r' */
        if (currIsNewline() && current != old) nextChar() /* skip '\n\r' or '\r\n' */
        if (++linenumber >= net.blueva.luak.compiler.LexState.Companion.MAX_INT) syntaxerror("chunk has too many lines")
    }

    internal fun setinput(L: LuaC.CompileState?, firstByte: Int, z: InputStream?, source: LuaString?) {
        this.decpoint = '.'.code.toByte()
        this.L = L
        this.lookahead.token = net.blueva.luak.compiler.LexState.Companion.TK_EOS /* no look-ahead token */
        this.z = z
        this.fs = null
        this.linenumber = 1
        this.lastline = 1
        this.source = source
        this.envn = LuaValue.ENV /* environment variable name */
        this.nbuff = 0 /* initialize buffer */
        this.current = firstByte /* read first char */
        this.skipShebang()
    }

    /**
     * Skips a `#!` line, but only in a chunk that came from a file.
     *
     * Lua strips it while reading the file, before the lexer ever sees it, so
     * `load("#=1")` is an ordinary chunk that starts with the length operator
     * and fails to parse. A source name beginning with `@` is what marks a
     * chunk as having been read from a file.
     */
    private fun skipShebang() {
        val name: String = source?.tojstring() ?: return
        if (!name.startsWith("@")) return
        if (current == '#'.code) {
            while (!currIsNewline() && current != net.blueva.luak.compiler.LexState.Companion.EOZ) nextChar()
        }
    }


    /*
	** =======================================================
	** LEXICAL ANALYZER
	** =======================================================
	*/
    fun check_next(set: String): Boolean {
        if (set.indexOf(current.toChar()) < 0) return false
        save_and_next()
        return true
    }

    fun buffreplace(from: Char, to: Char) {
        var n = nbuff
        val p = buff
        while ((--n) >= 0) if (p[n] == from) p[n] = to
    }

    internal fun str2d(str: String, seminfo: SemInfo): Boolean {
        // The same reader the `tonumber` coercion uses, so a literal and its
        // text form cannot disagree about whether they are integers.
        val numeral: LuaValue = net.blueva.luak.NumberParser.parse(str.trim())
            ?: run {
                lexerror("malformed number", net.blueva.luak.compiler.LexState.Companion.TK_NUMBER)
                return false
            }
        seminfo.r = numeral
        return true
    }


    internal fun read_numeral(seminfo: SemInfo) {
        var expo = "Ee"
        val first = current
        _assert(isdigit(current))
        save_and_next()
        if (first == '0'.code && check_next("Xx")) expo = "Pp"
        while (true) {
            if (check_next(expo)) check_next("+-")
            if (isxdigit(current) || current == '.'.code) save_and_next()
            else break
        }
        // A letter touching the numeral is part of the mistake, so it is taken
        // into the token and the message can name what was actually written.
        if (isalpha(current)) save_and_next()
        val str = buff.concatToString(0, nbuff)
        str2d(str, seminfo)
    }

    fun skip_sep(): Int {
        var count = 0
        val s = current
        _assert(s == '['.code || s == ']'.code)
        save_and_next()
        while (current == '='.code) {
            save_and_next()
            count++
        }
        return if (current == s) count else (-count) - 1
    }

    internal fun read_long_string(seminfo: SemInfo?, sep: Int) {
        var cont = 0
        save_and_next() /* skip 2nd `[' */
        if (currIsNewline())  /* string starts with a newline? */
            inclinenumber() /* skip it */
        var endloop = false
        while (!endloop) {
            when (current) {
                net.blueva.luak.compiler.LexState.Companion.EOZ -> lexerror(
                    if (seminfo != null)
                        "unfinished long string"
                    else
                        "unfinished long comment", net.blueva.luak.compiler.LexState.Companion.TK_EOS
                )

                '['.code -> {
                    if (skip_sep() == sep) {
                        save_and_next() /* skip 2nd `[' */
                        cont++
                        if (net.blueva.luak.compiler.LexState.Companion.LUA_COMPAT_LSTR == 1) {
                            if (sep == 0) lexerror("nesting of [[...]] is deprecated", '['.code)
                        }
                    }
                }

                ']'.code -> {
                    if (skip_sep() == sep) {
                        save_and_next() /* skip 2nd `]' */
                        if (net.blueva.luak.compiler.LexState.Companion.LUA_COMPAT_LSTR == 2) {
                            cont--
                            if (sep == 0 && cont >= 0) break
                        }
                        endloop = true
                    }
                }

                '\n'.code, '\r'.code -> {
                    save('\n'.code)
                    inclinenumber()
                    if (seminfo == null) nbuff = 0 /* avoid wasting space */
                }

                else -> {
                    if (seminfo != null) save_and_next()
                    else nextChar()
                }
            }
        }
        if (seminfo != null) seminfo.ts = L!!.newTString(LuaString.valueOf(buff, 2 + sep, nbuff - 2 * (2 + sep)))
    }

    fun hexvalue(c: Int): Int {
        return if (c <= '9'.code) c - '0'.code else if (c <= 'F'.code) c + 10 - 'A'.code else c + 10 - 'a'.code
    }

    /** Drops the last [n] characters the lexer saved. */
    private fun buffremove(n: Int) {
        nbuff -= n
    }

    /**
     * Fails with [msg] unless [ok], keeping the offending character.
     *
     * The character is added to the buffer first so that the "near" part of
     * the message shows what was actually written.
     */
    private fun esccheck(ok: Boolean, msg: String) {
        if (!ok) {
            if (current != net.blueva.luak.compiler.LexState.Companion.EOZ) save_and_next()
            lexerror(msg, net.blueva.luak.compiler.LexState.Companion.TK_STRING)
        }
    }

    /** One hexadecimal digit of an escape, left in the buffer for errors. */
    private fun gethexa(): Int {
        save_and_next()
        esccheck(isxdigit(current), "hexadecimal digit expected")
        return hexvalue(current)
    }

    fun readhexaesc(): Int {
        var r: Int = gethexa()
        r = (r shl 4) + gethexa()
        buffremove(2) // the two digits were only kept in case of an error
        return r
    }

    /**
     * Reads a `\u{XXX}` escape and saves its UTF-8 encoding.
     *
     * Added in Lua 5.3. The braces hold at least one hexadecimal digit, and the
     * value may reach `0x7FFFFFFF`, which needs the six-byte form the encoder
     * in [net.blueva.luak.lib.Utf8Lib] also produces.
     */
    internal fun readutf8esc() {
        var removed = 4 /* '\\', 'u', '{', and the first digit */
        save_and_next() /* keep 'u' */
        esccheck(current == '{'.code, "missing '{' in \\u{xxxx}")
        var value: Long = gethexa().toLong() /* at least one digit is required */
        while (true) {
            save_and_next()
            if (!isxdigit(current)) break
            removed++
            esccheck(value <= (0x7FFFFFFFL shr 4), "UTF-8 value too large")
            value = (value shl 4) + hexvalue(current).toLong()
        }
        esccheck(current == '}'.code, "missing '}'")
        nextChar() /* skip '}' */
        buffremove(removed)
        val encoded = ArrayList<Byte>()
        net.blueva.luak.lib.Utf8Lib.encode(value, encoded, 1)
        for (b in encoded) save(b.toInt() and 0xFF)
    }

    internal fun read_string(del: Int, seminfo: SemInfo) {
        save_and_next()
        while (current != del) {
            when (current) {
                net.blueva.luak.compiler.LexState.Companion.EOZ -> {
                    lexerror("unfinished string", net.blueva.luak.compiler.LexState.Companion.TK_EOS)
                    continue  /* to avoid warnings */
                }

                '\n'.code, '\r'.code -> {
                    lexerror("unfinished string", net.blueva.luak.compiler.LexState.Companion.TK_STRING)
                    continue  /* to avoid warnings */
                }

                '\\'.code -> {
                    var c: Int
                    save_and_next() /* keep the backslash for error messages */
                    when (current) {
                        'a'.code -> c = '\u0007'.code
                        'b'.code -> c = '\b'.code
                        'f'.code -> c = '\u000C'.code
                        'n'.code -> c = '\n'.code
                        'r'.code -> c = '\r'.code
                        't'.code -> c = '\t'.code
                        'v'.code -> c = '\u000B'.code
                        'x'.code -> {
                            c = readhexaesc()
                            nextChar()
                            buffremove(1) /* the backslash */
                            save(c)
                            continue
                        }

                        'u'.code -> {
                            readutf8esc()
                            continue
                        }

                        '\n'.code, '\r'.code -> {
                            inclinenumber()
                            buffremove(1)
                            save('\n'.code)
                            continue
                        }

                        net.blueva.luak.compiler.LexState.Companion.EOZ -> continue  /* will raise an error next loop */
                        'z'.code -> {
                            /* zap following span of spaces */
                            buffremove(1) /* the backslash */
                            nextChar() /* skip the 'z' */
                            while (isspace(current)) {
                                if (currIsNewline()) inclinenumber()
                                else nextChar()
                            }
                            continue
                        }

                        else -> {
                            if (!isdigit(current)) {
                                esccheck(
                                    current == '\\'.code || current == '"'.code ||
                                        current == '\''.code,
                                    "invalid escape sequence",
                                )
                                buffremove(1) /* the backslash */
                                save_and_next() /* handles \\, \" and \' */
                            } else { /* \ddd */
                                var i = 0
                                c = 0
                                while (i < 3 && isdigit(current)) {
                                    c = 10 * c + (current - '0'.code)
                                    save_and_next()
                                    i++
                                }
                                esccheck(c <= net.blueva.luak.compiler.LexState.Companion.UCHAR_MAX, "decimal escape too large")
                                buffremove(i + 1) /* the digits and the backslash */
                                save(c)
                            }
                            continue
                        }
                    }
                    nextChar()
                    buffremove(1) /* the backslash */
                    save(c)
                    continue
                }

                else -> save_and_next()
            }
        }
        save_and_next() /* skip delimiter */
        seminfo.ts = L!!.newTString(LuaString.valueOf(buff, 1, nbuff - 2))
    }

    internal fun llex(seminfo: SemInfo): Int {
        nbuff = 0
        while (true) {
            when (current) {
                '\n'.code, '\r'.code -> {
                    inclinenumber()
                    continue
                }

                ' '.code, '\u000C'.code, '\t'.code, 0x0B -> {
                    nextChar()
                    continue
                }

                '-'.code -> {
                    nextChar()
                    if (current != '-'.code) return '-'.code
                    /* else is a comment */
                    nextChar()
                    if (current == '['.code) {
                        val sep = skip_sep()
                        nbuff = 0 /* `skip_sep' may dirty the buffer */
                        if (sep >= 0) {
                            read_long_string(null, sep) /* long comment */
                            nbuff = 0
                            continue
                        }
                    }
                    /* else short comment */
                    while (!currIsNewline() && current != net.blueva.luak.compiler.LexState.Companion.EOZ) nextChar()
                    continue
                }

                '['.code -> {
                    run {
                        val sep = skip_sep()
                        if (sep >= 0) {
                            read_long_string(seminfo, sep)
                            return net.blueva.luak.compiler.LexState.Companion.TK_STRING
                        } else if (sep == -1) return '['.code
                        else lexerror(
                            "invalid long string delimiter",
                            net.blueva.luak.compiler.LexState.Companion.TK_STRING
                        )
                    }
                    run {
                        nextChar()
                        if (current != '='.code) return '='.code
                        else {
                            nextChar()
                            return net.blueva.luak.compiler.LexState.Companion.TK_EQ
                        }
                    }
                }

                '='.code -> {
                    nextChar()
                    if (current != '='.code) return '='.code
                    else {
                        nextChar()
                        return net.blueva.luak.compiler.LexState.Companion.TK_EQ
                    }
                }

                '<'.code -> {
                    nextChar()
                    if (current == '='.code) {
                        nextChar()
                        return net.blueva.luak.compiler.LexState.Companion.TK_LE
                    } else if (current == '<'.code) {
                        nextChar()
                        return net.blueva.luak.compiler.LexState.Companion.TK_SHL
                    } else return '<'.code
                }

                '>'.code -> {
                    nextChar()
                    if (current == '='.code) {
                        nextChar()
                        return net.blueva.luak.compiler.LexState.Companion.TK_GE
                    } else if (current == '>'.code) {
                        nextChar()
                        return net.blueva.luak.compiler.LexState.Companion.TK_SHR
                    } else return '>'.code
                }

                '/'.code -> {
                    nextChar()
                    if (current != '/'.code) return '/'.code
                    else {
                        nextChar()
                        return net.blueva.luak.compiler.LexState.Companion.TK_IDIV
                    }
                }

                '~'.code -> {
                    nextChar()
                    if (current != '='.code) return '~'.code
                    else {
                        nextChar()
                        return net.blueva.luak.compiler.LexState.Companion.TK_NE
                    }
                }

                ':'.code -> {
                    nextChar()
                    if (current != ':'.code) return ':'.code
                    else {
                        nextChar()
                        return net.blueva.luak.compiler.LexState.Companion.TK_DBCOLON
                    }
                }

                '"'.code, '\''.code -> {
                    read_string(current, seminfo)
                    return net.blueva.luak.compiler.LexState.Companion.TK_STRING
                }

                '.'.code -> {
                    save_and_next()
                    if (check_next(".")) {
                        if (check_next(".")) return net.blueva.luak.compiler.LexState.Companion.TK_DOTS /* ... */
                        else return net.blueva.luak.compiler.LexState.Companion.TK_CONCAT /* .. */
                    } else if (!isdigit(current)) return '.'.code
                    else {
                        read_numeral(seminfo)
                        return net.blueva.luak.compiler.LexState.Companion.TK_NUMBER
                    }
                }

                '0'.code, '1'.code, '2'.code, '3'.code, '4'.code, '5'.code, '6'.code, '7'.code, '8'.code, '9'.code -> {
                    read_numeral(seminfo)
                    return net.blueva.luak.compiler.LexState.Companion.TK_NUMBER
                }

                net.blueva.luak.compiler.LexState.Companion.EOZ -> {
                    return net.blueva.luak.compiler.LexState.Companion.TK_EOS
                }

                else -> {
                    if (isalpha(current) || current == '_'.code) {
                        /* identifier or reserved word */
                        val ts: LuaString?
                        do {
                            save_and_next()
                        } while (isalnum(current))
                        ts = newstring(buff, 0, nbuff)
                        if (net.blueva.luak.compiler.LexState.Companion.RESERVED.containsKey(ts)) return net.blueva.luak.compiler.LexState.Companion.RESERVED[
                            ts
                        ]!!
                        else {
                            seminfo.ts = ts
                            return net.blueva.luak.compiler.LexState.Companion.TK_NAME
                        }
                    } else {
                        val c = current
                        nextChar()
                        return c /* single-char tokens (+ - / ...) */
                    }
                }
            }
        }
    }

    fun next() {
        lastline = linenumber
        if (lookahead.token != net.blueva.luak.compiler.LexState.Companion.TK_EOS) { /* is there a look-ahead token? */
            t.set(lookahead) /* use this one */
            lookahead.token = net.blueva.luak.compiler.LexState.Companion.TK_EOS /* and discharge it */
        } else t.token = llex(t.seminfo) /* read next token */
    }

    fun lookahead() {
        _assert(lookahead.token == net.blueva.luak.compiler.LexState.Companion.TK_EOS)
        lookahead.token = llex(lookahead.seminfo)
    }


    internal class expdesc {
        var k: Int = 0 // expkind, from enumerated list, above

        internal class U {
            // originally a union
            var ind_idx: Short = 0 // index (R/K)
            var ind_t: Short = 0 // table(register or upvalue)
            var ind_vt: Short = 0 // whether 't' is register (VLOCAL) or (UPVALUE)
            internal var _nval: LuaValue? = null
            var info: Int = 0
            fun setNval(r: LuaValue?) {
                _nval = r
            }

            fun nval(): LuaValue? {
                return (if (_nval == null) LuaInteger.valueOf(info) else _nval)
            }
        }

        val u: U = net.blueva.luak.compiler.LexState.expdesc.U()
        val t: IntPtr = IntPtr() /* patch list of `exit when true' */
        val f: IntPtr = IntPtr() /* patch list of `exit when false' */

        /**
         * The name of the `global <const>` this expression reads, if any.
         *
         * A read-only global is an ordinary `_ENV[name]` index once compiled,
         * so the only place the restriction survives is here, on the
         * expression the parser hands to [check_readonly].
         */
        var readonlyGlobal: LuaString? = null

        fun init(k: Int, i: Int) {
            this.f.i = net.blueva.luak.compiler.LexState.Companion.NO_JUMP
            this.t.i = net.blueva.luak.compiler.LexState.Companion.NO_JUMP
            this.k = k
            this.u.info = i
            this.readonlyGlobal = null
        }

        fun hasjumps(): Boolean {
            return (t.i !== f.i)
        }

        fun isnumeral(): Boolean {
            return (k == net.blueva.luak.compiler.LexState.Companion.VKNUM && t.i === net.blueva.luak.compiler.LexState.Companion.NO_JUMP && f.i === net.blueva.luak.compiler.LexState.Companion.NO_JUMP)
        }

        fun setvalue(other: expdesc) {
            this.readonlyGlobal = other.readonlyGlobal
            this.f.i = other.f.i
            this.k = other.k
            this.t.i = other.t.i
            this.u._nval = other.u._nval
            this.u.ind_idx = other.u.ind_idx
            this.u.ind_t = other.u.ind_t
            this.u.ind_vt = other.u.ind_vt
            this.u.info = other.u.info
        }
    }


    /* description of active local variable */
    internal class Vardesc(idx: Int) {
        val idx: Short /* variable index in stack */

        /** How the variable was declared: plain, `<const>`, or `<close>`. */
        var kind: Int = net.blueva.luak.compiler.LexState.Companion.VDKREG

        init {
            this.idx = idx.toShort()
        }
    }


    /* description of pending goto statements and label statements */
    internal class Labeldesc(
        name: LuaString?,
        pc: Int,
        line: Int,
        nactvar: Short,
        nglobals: Int = 0,
    ) {
        var name: LuaString? /* label identifier */
        var pc: Int /* position in code */
        var line: Int /* line where it appeared */
        var nactvar: Short /* local level where it appears in current block */

        /** How many `global` declarations were in scope where this appeared. */
        var nglobals: Int

        init {
            this.name = name
            this.pc = pc
            this.line = line
            this.nactvar = nactvar
            this.nglobals = nglobals
        }
    }


    /* dynamic structures used by the parser */
    internal class Dyndata {
        var actvar: Array<Vardesc?>? = null /* list of active local variables */
        var n_actvar: Int = 0
        var gt: Array<Labeldesc?> = arrayOfNulls(0) /* list of pending gotos */
        var n_gt: Int = 0
        var label: Array<Labeldesc?> = arrayOfNulls(0) /* list of active labels */
        var n_label: Int = 0
    }


    fun hasmultret(k: Int): Boolean {
        return ((k) == net.blueva.luak.compiler.LexState.Companion.VCALL || (k) == net.blueva.luak.compiler.LexState.Companion.VVARARG)
    }

    /*----------------------------------------------------------------------
	name		args	description
	------------------------------------------------------------------------*/
    fun anchor_token() {
        /* last token from outer function must be EOS */
        _assert(fs != null || t.token == net.blueva.luak.compiler.LexState.Companion.TK_EOS)
        if (t.token == net.blueva.luak.compiler.LexState.Companion.TK_NAME || t.token == net.blueva.luak.compiler.LexState.Companion.TK_STRING) {
            val ts: LuaString? = t.seminfo.ts
            // TODO: is this necessary?
            L!!.cachedLuaString(t.seminfo.ts)
        }
    }

    /* semantic error */
    fun semerror(msg: String?) {
        t.token = 0 /* remove 'near to' from final message */
        // Something already read is what is wrong, not whatever the lexer has
        // gone on to look at: a complaint about the end of a statement belongs
        // on the line the statement is on.
        linenumber = lastline
        syntaxerror(msg)
    }

    fun error_expected(token: Int) {
        // token2str already quotes whatever needs quoting.
        syntaxerror(token2str(token) + " expected")
    }

    fun testnext(c: Int): Boolean {
        if (t.token == c) {
            next()
            return true
        } else return false
    }

    fun check(c: Int) {
        if (t.token != c) error_expected(c)
    }

    fun checknext(c: Int) {
        check(c)
        next()
    }

    fun check_condition(c: Boolean, msg: String?) {
        if (!(c)) syntaxerror(msg)
    }


    fun check_match(what: Int, who: Int, where: Int) {
        if (!testnext(what)) {
            if (where == linenumber) error_expected(what)
            else {
                // token2str already quotes what it names, so nothing is
                // added around it here.
                syntaxerror(
                    L!!.pushfstring(
                        token2str(what) + " expected (to close " + token2str(who) +
                            " at line " + where + ")",
                    )
                )
            }
        }
    }

    fun str_checkname(): LuaString? {
        val ts: LuaString?
        check(net.blueva.luak.compiler.LexState.Companion.TK_NAME)
        ts = t.seminfo.ts
        next()
        return ts
    }

    internal fun codestring(e: expdesc, s: LuaString?) {
        e.init(net.blueva.luak.compiler.LexState.Companion.VK, fs!!.stringK(s))
    }

    internal fun checkname(e: expdesc) {
        codestring(e, str_checkname())
    }


    fun registerlocalvar(varname: LuaString?): Int {
        val fs: FuncState = this.fs!!
        val f: Prototype = fs.f!!
        if (f.locvars == null || fs.nlocvars + 1 > f.locvars.size) f.locvars = realloc(f.locvars, fs.nlocvars * 2 + 1)
        f.locvars[(fs.nlocvars).toInt()] = LocVars(varname, 0, 0)
        return fs.nlocvars.toInt().also { fs.nlocvars++ }
    }

    fun new_localvar(name: LuaString?) {
        val reg = registerlocalvar(name)
        // Counted within this function alone: the array holds the variables
        // of every function being compiled, and a function nested in another
        // starts where the one around it left off.
        fs!!.checklimit(dyd.n_actvar + 1 - fs!!.firstlocal, LUAI_MAXVARS, "local variables")
        if (dyd.actvar == null || dyd.n_actvar + 1 > dyd.actvar!!.size) dyd.actvar =
            realloc(dyd.actvar, maxOf(1, dyd.n_actvar * 2))
        dyd.actvar!![dyd.n_actvar++] = net.blueva.luak.compiler.LexState.Vardesc(reg)
    }

    fun new_localvarliteral(v: String?) {
        val ts: LuaString = newstring(v)
        new_localvar(ts)
    }

    /** Declares a local with a kind other than the plain one. */
    fun new_varkind(name: LuaString?, kind: Int) {
        new_localvar(name)
        dyd.actvar!![dyd.n_actvar - 1]!!.kind = kind
    }

    fun adjustlocalvars(nvars: Int) {
        var nvars = nvars
        val fs: FuncState = this.fs!!
        fs.nactvar = (fs.nactvar + nvars).toShort()
        while (nvars > 0) {
            fs.getlocvar(fs.nactvar - nvars).startpc = fs.pc
            nvars--
        }
    }

    fun removevars(tolevel: Int) {
        val fs: FuncState = this.fs!!
        while (fs.nactvar > tolevel) {
            fs.nactvar--
            fs.getlocvar(fs.nactvar.toInt()).endpc = fs.pc
        }
    }

    internal fun singlevar(`var`: expdesc) {
        this.buildvar(this.str_checkname()!!, `var`)
    }

    /**
     * Resolves [varname], which may turn out to be a local, an upvalue or a
     * global, and leaves the expression for it in [var].
     *
     * With no `global` declaration anywhere in scope every name that is not a
     * local is a global, which is how Lua has always behaved. Once a `global`
     * statement names anything, the rest of the scope - inner functions
     * included - has to declare what it uses, unless a collective `global *`
     * is also in scope, which puts the default back.
     */
    internal fun buildvar(varname: LuaString, `var`: expdesc) {
        val fs: FuncState = this.fs!!
        val search = FuncState.Globalsearch()
        val resolved: Int = FuncState.singlevaraux(fs, varname, `var`, 1, search)
        if (resolved != net.blueva.luak.compiler.LexState.Companion.VVOID) return
        val declaration: FuncState.Globaldesc? = search.found ?: search.collective
        if (declaration == null && search.named) {
            this.semerror("variable '" + varname.tojstring() + "' not declared")
        }
        this.buildglobal(varname, `var`)
        if (declaration != null && declaration.readonly) `var`.readonlyGlobal = varname
    }

    /**
     * Builds the expression `_ENV[varname]`, which is what a global name is.
     */
    private fun buildglobal(varname: LuaString, `var`: expdesc) {
        val fs: FuncState = this.fs!!
        val key: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        val search = FuncState.Globalsearch()
        // Every global is read through _ENV, so _ENV itself cannot be one: the
        // lookup would have nowhere to start.
        if (FuncState.singlevaraux(fs, (this.envn)!!, `var`, 1, search) ==
            net.blueva.luak.compiler.LexState.Companion.VVOID
        ) {
            this.semerror(
                "_ENV is global when accessing variable '" + varname.tojstring() + "'",
            )
        }
        this.codestring(key, varname) /* key is variable name */
        fs.indexed(`var`, key) /* env[varname] */
    }

    internal fun adjust_assign(nvars: Int, nexps: Int, e: expdesc) {
        val fs: FuncState = this.fs!!
        var extra = nvars - nexps
        if (hasmultret(e.k)) {
            /* includes call itself */
            extra++
            if (extra < 0) extra = 0
            /* last exp. provides the difference */
            fs.setreturns(e, extra)
            if (extra > 1) fs.reserveregs(extra - 1)
        } else {
            /* close last expression */
            if (e.k != net.blueva.luak.compiler.LexState.Companion.VVOID) fs.exp2nextreg(e)
            if (extra > 0) {
                val reg: Int = fs.freereg.toInt()
                fs.reserveregs(extra)
                fs.nil(reg, extra)
            }
        }
    }

    fun enterlevel() {
        if (++L!!.nCcalls > net.blueva.luak.compiler.LexState.Companion.LUAI_MAXCCALLS) lexerror(
            "chunk has too many syntax levels",
            0
        )
    }

    fun leavelevel() {
        L!!.nCcalls--
    }

    internal fun closegoto(g: Int, label: Labeldesc) {
        val fs: FuncState = this.fs!!
        val gl = this.dyd.gt
        val gt = gl[g]!!
        _assert(gt.name!!.eq_b(label.name))
        // A `global` declaration opens a scope of its own, so jumping past one
        // is as wrong as jumping past a local.
        if (gt.nglobals < label.nglobals) {
            val declared: LuaString? = fs.globals[gt.nglobals].name
            semerror(
                "<goto " + gt.name + "> at line " + gt.line +
                    " jumps into the scope of '" + (declared?.tojstring() ?: "*") + "'",
            )
        }
        if (gt.nactvar < label.nactvar) {
            val vname: LuaString = fs.getlocvar((gt.nactvar).toInt()).varname!!
            val msg: String? = L!!.pushfstring(
                ("<goto " + gt.name + "> at line "
                        + gt.line + " jumps into the scope of '"
                        + vname.tojstring() + "'")
            )
            semerror(msg)
        }
        fs.patchlist(gt.pc, label.pc)
        /* remove goto from pending list */
        arrayCopy(gl, g + 1, gl, g, this.dyd.n_gt - g - 1)
        gl[--this.dyd.n_gt] = null
    }

    /*
	 ** try to close a goto with existing labels; this solves backward jumps
	 */
    fun findlabel(g: Int): Boolean {
        var i: Int
        val bl: BlockCnt = fs!!.bl!!
        val dyd = this.dyd
        val gt = dyd.gt[g]!!
        /* check labels in current block for a match */
        i = bl.firstlabel.toInt()
        while (i < dyd.n_label) {
            val lb = dyd.label[i]!!
            if (lb.name!!.eq_b(gt.name)) {  /* correct label? */
                if (gt.nactvar > lb.nactvar &&
                    (bl.upval || dyd.n_label > bl.firstlabel)
                ) fs!!.patchclose(gt.pc, (lb.nactvar).toInt())
                closegoto(g, lb) /* close it */
                return true
            }
            i++
        }
        return false /* label not found; cannot close goto */
    }

    /* Caller must grow() the vector before calling this. */
    internal fun newlabelentry(l: Array<Labeldesc?>, index: Int, name: LuaString?, line: Int, pc: Int): Int {
        l[index] = net.blueva.luak.compiler.LexState.Labeldesc(
            name,
            pc,
            line,
            fs!!.nactvar,
            fs!!.globals.size,
        )
        return index
    }

    /*
	 ** check whether new label 'lb' matches any pending gotos in current
	 ** block; solves forward jumps
	 */
    internal fun findgotos(lb: Labeldesc) {
        val gl = dyd.gt
        var i: Int = fs!!.bl!!.firstgoto.toInt()
        while (i < dyd.n_gt) {
            if (gl[i]!!.name!!.eq_b(lb.name)) closegoto(i, lb)
            else i++
        }
    }


    /*
	** create a label named "break" to resolve break statements
	*/
    fun breaklabel() {
        val n: LuaString? = LuaString.valueOf("break")
        val l = newlabelentry(grow(dyd.label, dyd.n_label + 1).also { dyd.label = it }, dyd.n_label++, n, 0, fs!!.pc)
        findgotos(dyd.label[l]!!)
    }

    /*
	** generates an error for an undefined 'goto'; choose appropriate
	** message when label name is a reserved word (which can only be 'break')
	*/
    internal fun undefgoto(gt: Labeldesc) {
        val msg: String? = L!!.pushfstring(
            if (net.blueva.luak.compiler.LexState.Companion.isReservedKeyword(gt.name!!.tojstring()))
                "<" + gt.name + "> at line " + gt.line + " not inside a loop"
            else
                "no visible label '" + gt.name + "' for <goto> at line " + gt.line
        )
        semerror(msg)
    }

    fun addprototype(): Prototype? {
        val clp: Prototype?
        val f: Prototype = fs!!.f!! /* prototype of current function */
        if (f.p == null || fs!!.np >= f.p!!.size) {
            f.p = realloc(f.p, maxOf(1, fs!!.np * 2))
        }
        clp = Prototype()
        f.p!![fs!!.np++] = clp
        return clp
    }

    internal fun codeclosure(v: expdesc) {
        val fs: FuncState = this.fs!!.prev!!
        v.init(net.blueva.luak.compiler.LexState.Companion.VRELOCABLE, fs.codeABx(OP_CLOSURE, 0, fs.np - 1))
        fs.exp2nextreg(v) /* fix it at stack top (for GC) */
    }

    internal fun open_func(fs: FuncState, bl: BlockCnt?) {
        fs.prev = this.fs /* linked list of funcstates */
        fs.ls = this
        this.fs = fs
        fs.pc = 0
        fs.lasttarget = -1
        fs.jpc = IntPtr(net.blueva.luak.compiler.LexState.Companion.NO_JUMP)
        fs.freereg = 0
        fs.nk = 0
        fs.np = 0
        fs.nups = 0
        fs.nlocvars = 0
        fs.nactvar = 0
        fs.firstlocal = dyd.n_actvar
        fs.firstlabel = dyd.n_label
        fs.bl = null
        fs.f!!.source = this.source
        fs.f!!.maxstacksize = 2 /* registers 0/1 are always valid */
        fs.enterblock((bl)!!, false)
    }

    fun close_func() {
        val fs: FuncState = this.fs!!
        val f: Prototype = fs.f!!
        fs.ret(0, 0) /* final return */
        fs.leaveblock()
        f.code = realloc(f.code, fs.pc)
        f.lineinfo = realloc(f.lineinfo, fs.pc)
        f.k = realloc(f.k, fs.nk)
        f.p = realloc(f.p, fs.np)
        f.locvars = realloc(f.locvars, (fs.nlocvars).toInt())
        f.upvalues = realloc(f.upvalues, (fs.nups).toInt())
        _assert(fs.bl == null)
        this.fs = fs.prev
        // last token read was anchored in defunct function; must reanchor it
        // ls.anchor_token();
    }

    /*============================================================*/ /* GRAMMAR RULES */ /*============================================================*/
    internal fun fieldsel(v: expdesc?) {
        /* fieldsel -> ['.' | ':'] NAME */
        val fs: FuncState = this.fs!!
        val key: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        fs.exp2anyregup((v)!!)
        this.next() /* skip the dot or colon */
        this.checkname(key)
        fs.indexed((v)!!, key)
    }

    internal fun yindex(v: expdesc) {
        /* index -> '[' expr ']' */
        this.next() /* skip the '[' */
        this.expr(v)
        this.fs!!.exp2val(v)
        this.checknext(']'.code)
    }


    /*
	** {======================================================================
	** Rules for Constructors
	** =======================================================================
	*/
    internal class ConsControl {
        var v: expdesc = net.blueva.luak.compiler.LexState.expdesc() /* last list item read */
        var t: expdesc? = null /* table descriptor */
        var nh: Int = 0 /* total number of `record' elements */
        var na: Int = 0 /* total number of array elements */
        var tostore: Int = 0 /* number of array elements pending to be stored */
    }


    internal fun recfield(cc: ConsControl) {
        /* recfield -> (NAME | `['exp1`]') = exp1 */
        val fs: FuncState? = this.fs
        val reg: Int = this.fs!!.freereg.toInt()
        val key: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        val `val`: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        val rkkey: Int
        if (this.t.token == net.blueva.luak.compiler.LexState.Companion.TK_NAME) {
            fs!!.checklimit(cc.nh, net.blueva.luak.compiler.LexState.Companion.MAX_INT, "items in a constructor")
            this.checkname(key)
        } else  /* this.t.token == '[' */
            this.yindex(key)
        cc.nh++
        this.checknext('='.code)
        rkkey = fs!!.exp2RK(key)
        this.expr(`val`)
        fs!!.codeABC(Lua.OP_SETTABLE, cc.t!!.u.info, rkkey, fs!!.exp2RK(`val`))
        fs!!.freereg = reg.toShort() /* free registers */
    }

    internal fun listfield(cc: ConsControl) {
        this.expr(cc.v)
        fs!!.checklimit(cc.na, net.blueva.luak.compiler.LexState.Companion.MAX_INT, "items in a constructor")
        cc.na++
        cc.tostore++
    }


    internal fun constructor(t: expdesc) {
        /* constructor -> ?? */
        val fs: FuncState = this.fs!!
        val line = this.linenumber
        val pc: Int = fs.codeABC(Lua.OP_NEWTABLE, 0, 0, 0)
        val cc: ConsControl = net.blueva.luak.compiler.LexState.ConsControl()
        cc.tostore = 0
        cc.nh = cc.tostore
        cc.na = cc.nh
        cc.t = t
        t.init(net.blueva.luak.compiler.LexState.Companion.VRELOCABLE, pc)
        cc.v.init(net.blueva.luak.compiler.LexState.Companion.VVOID, 0) /* no value (yet) */
        fs.exp2nextreg(t) /* fix it at stack top (for gc) */
        this.checknext('{'.code)
        do {
            _assert(cc.v.k == net.blueva.luak.compiler.LexState.Companion.VVOID || cc.tostore > 0)
            if (this.t.token == '}'.code) break
            fs.closelistfield(cc)
            when (this.t.token) {
                net.blueva.luak.compiler.LexState.Companion.TK_NAME -> {
                    /* may be listfields or recfields */
                    this.lookahead()
                    if (this.lookahead.token != '='.code)  /* expression? */
                        this.listfield(cc)
                    else this.recfield(cc)
                }

                '['.code -> {
                    /* constructor_item -> recfield */
                    this.recfield(cc)
                }

                else -> {
                    /* constructor_part -> listfield */
                    this.listfield(cc)
                }
            }
        } while (this.testnext(','.code) || this.testnext(';'.code))
        this.check_match('}'.code, '{'.code, line)
        fs.lastlistfield(cc)
        val i: InstructionPtr = InstructionPtr((fs.f!!.code)!!, pc)
        SETARG_B(i, net.blueva.luak.compiler.LexState.Companion.luaO_int2fb(cc.na)) /* set initial array size */
        SETARG_C(i, net.blueva.luak.compiler.LexState.Companion.luaO_int2fb(cc.nh)) /* set initial table size */
    }

    /* }====================================================================== */
    fun parlist() {
        /* parlist -> [ param { `,' param } ] */
        val fs: FuncState = this.fs!!
        val f: Prototype = fs.f!!
        var nparams = 0
        f.is_vararg = 0
        if (this.t.token != ')'.code) {  /* is `parlist' not empty? */
            do {
                when (this.t.token) {
                    net.blueva.luak.compiler.LexState.Companion.TK_NAME -> {
                        /* param . NAME */
                        this.new_localvar(this.str_checkname())
                        ++nparams
                    }

                    net.blueva.luak.compiler.LexState.Companion.TK_DOTS -> {
                        /* param . `...' or `...NAME' */
                        this.next()
                        f.is_vararg = 1
                        if (this.t.token == net.blueva.luak.compiler.LexState.Companion.TK_NAME) {
                            // The extra arguments also get a name, holding them
                            // as a table alongside '...'. The name is read-only:
                            // rebinding it would break the link to '...'.
                            this.new_localvar(this.str_checkname())
                            this.dyd!!.actvar!![this.dyd!!.n_actvar - 1]!!.kind =
                                net.blueva.luak.compiler.LexState.Companion.RDKCONST
                            f.is_vararg = 1 or Lua.VARARG_NAMED
                        } else {
                            // The slot exists either way, named or not: a
                            // vararg function always has somewhere to put the
                            // table, and code that walks the locals sees it.
                            this.new_localvarliteral(
                                net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_VARARGS,
                            )
                        }
                    }

                    else -> this.syntaxerror("<name> or " + net.blueva.luak.compiler.LexState.Companion.LUA_QL("...") + " expected")
                }
            } while ((f.is_vararg === 0) && this.testnext(','.code))
        }
        this.adjustlocalvars(nparams)
        // The vararg table is a local of its own, and comes into scope after
        // the count of declared parameters has been taken.
        f.numparams = fs.nactvar.toInt()
        if (f.is_vararg != 0) {
            this.adjustlocalvars(1)
            // In scope only once the call has been set up, which is when the
            // extra arguments exist: asking a function value for its locals
            // reads them at the very start and must not see this one.
            fs.getlocvar(fs.nactvar - 1).startpc = 1
        }
        fs.reserveregs((fs.nactvar).toInt()) /* reserve register for parameters */
    }


    internal fun body(e: expdesc, needself: Boolean, line: Int) {
        /* body -> `(' parlist `)' chunk END */
        val new_fs: FuncState = FuncState()
        val bl: BlockCnt = BlockCnt()
        new_fs.f = addprototype()
        new_fs.f!!.linedefined = line
        open_func(new_fs, bl)
        this.checknext('('.code)
        if (needself) {
            new_localvarliteral("self")
            adjustlocalvars(1)
        }
        this.parlist()
        this.checknext(')'.code)
        this.statlist()
        new_fs.f!!.lastlinedefined = this.linenumber
        this.check_match(
            net.blueva.luak.compiler.LexState.Companion.TK_END,
            net.blueva.luak.compiler.LexState.Companion.TK_FUNCTION,
            line
        )
        this.codeclosure(e)
        this.close_func()
    }

    internal fun explist(v: expdesc): Int {
        /* explist1 -> expr { `,' expr } */
        var n = 1 /* at least one expression */
        this.expr(v)
        while (this.testnext(','.code)) {
            fs!!.exp2nextreg(v)
            this.expr(v)
            n++
        }
        return n
    }


    internal fun funcargs(f: expdesc) {
        val fs: FuncState = this.fs!!
        // Where the arguments start, which is the line a call reports itself
        // on: a call written over several lines is the one at its '(', not the
        // one where the expression naming the function began.
        val line: Int = linenumber
        val args: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        val base: Int
        val nparams: Int
        when (this.t.token) {
            '('.code -> {
                /* funcargs -> `(' [ explist1 ] `)' */
                this.next()
                if (this.t.token == ')'.code)  /* arg list is empty? */
                    args.k = net.blueva.luak.compiler.LexState.Companion.VVOID
                else {
                    this.explist(args)
                    fs.setmultret(args)
                }
                this.check_match(')'.code, '('.code, line)
            }

            '{'.code -> {
                /* funcargs -> constructor */
                this.constructor(args)
            }

            net.blueva.luak.compiler.LexState.Companion.TK_STRING -> {
                /* funcargs -> STRING */
                this.codestring(args, this.t.seminfo.ts)
                this.next() /* must use `seminfo' before `next' */
            }

            else -> {
                this.syntaxerror("function arguments expected")
                return
            }
        }
        _assert(f.k == net.blueva.luak.compiler.LexState.Companion.VNONRELOC)
        base = f.u.info /* base register for call */
        if (hasmultret(args.k)) nparams = Lua.LUA_MULTRET /* open call */
        else {
            if (args.k != net.blueva.luak.compiler.LexState.Companion.VVOID) fs.exp2nextreg(args) /* close last argument */
            nparams = fs.freereg - (base + 1)
        }
        f.init(net.blueva.luak.compiler.LexState.Companion.VCALL, fs.codeABC(Lua.OP_CALL, base, nparams + 1, 2))
        fs.fixline(line)
        fs.freereg = (base + 1).toShort() /* call remove function and arguments and leaves
							 * (unless changed) one result */
    }


    /*
	** {======================================================================
	** Expression parsing
	** =======================================================================
	*/
    internal fun primaryexp(v: expdesc) {
        /* primaryexp -> NAME | '(' expr ')' */
        when (t.token) {
            '('.code -> {
                val line = linenumber
                this.next()
                this.expr(v)
                this.check_match(')'.code, '('.code, line)
                fs!!.dischargevars(v)
                return
            }

            net.blueva.luak.compiler.LexState.Companion.TK_NAME -> {
                singlevar(v)
                return
            }

            else -> {
                // The offending token is named by the "near" part already.
                this.syntaxerror("unexpected symbol")
                return
            }
        }
    }


    internal fun suffixedexp(v: expdesc) {
        /* suffixedexp ->
       	primaryexp { '.' NAME | '[' exp ']' | ':' NAME funcargs | funcargs } */
        primaryexp(v)
        while (true) {
            when (t.token) {
                '.'.code -> {
                    /* fieldsel */
                    this.fieldsel(v)
                }

                '['.code -> {
                    /* `[' exp1 `]' */
                    val key: expdesc = net.blueva.luak.compiler.LexState.expdesc()
                    fs!!.exp2anyregup(v)
                    this.yindex(key)
                    fs!!.indexed(v, key)
                }

                ':'.code -> {
                    /* `:' NAME funcargs */
                    val key: expdesc = net.blueva.luak.compiler.LexState.expdesc()
                    this.next()
                    this.checkname(key)
                    fs!!.self(v, key)
                    this.funcargs(v)
                }

                '('.code, net.blueva.luak.compiler.LexState.Companion.TK_STRING, '{'.code -> {
                    /* funcargs */
                    fs!!.exp2nextreg(v)
                    this.funcargs(v)
                }

                else -> return
            }
        }
    }


    internal fun simpleexp(v: expdesc) {
        /*
		 * simpleexp -> NUMBER | STRING | NIL | true | false | ... | constructor |
		 * FUNCTION body | primaryexp
		 */
        when (this.t.token) {
            net.blueva.luak.compiler.LexState.Companion.TK_NUMBER -> {
                v.init(net.blueva.luak.compiler.LexState.Companion.VKNUM, 0)
                v.u.setNval(this.t.seminfo.r)
            }

            net.blueva.luak.compiler.LexState.Companion.TK_STRING -> {
                this.codestring(v, this.t.seminfo.ts)
            }

            net.blueva.luak.compiler.LexState.Companion.TK_NIL -> {
                v.init(net.blueva.luak.compiler.LexState.Companion.VNIL, 0)
            }

            net.blueva.luak.compiler.LexState.Companion.TK_TRUE -> {
                v.init(net.blueva.luak.compiler.LexState.Companion.VTRUE, 0)
            }

            net.blueva.luak.compiler.LexState.Companion.TK_FALSE -> {
                v.init(net.blueva.luak.compiler.LexState.Companion.VFALSE, 0)
            }

            net.blueva.luak.compiler.LexState.Companion.TK_DOTS -> {
                /* vararg */
                val fs: FuncState = this.fs!!
                this.check_condition(
                    fs.f!!.is_vararg !== 0, ("cannot use " + net.blueva.luak.compiler.LexState.Companion.LUA_QL("...")
                            + " outside a vararg function")
                )
                v.init(net.blueva.luak.compiler.LexState.Companion.VVARARG, fs.codeABC(Lua.OP_VARARG, 0, 1, 0))
            }

            '{'.code -> {
                /* constructor */
                this.constructor(v)
                return
            }

            net.blueva.luak.compiler.LexState.Companion.TK_FUNCTION -> {
                this.next()
                this.body(v, false, this.linenumber)
                return
            }

            else -> {
                this.suffixedexp(v)
                return
            }
        }
        this.next()
    }


    fun getunopr(op: Int): Int {
        when (op) {
            net.blueva.luak.compiler.LexState.Companion.TK_NOT -> return net.blueva.luak.compiler.LexState.Companion.OPR_NOT
            '-'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_MINUS
            '#'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_LEN
            '~'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_BNOT
            else -> return net.blueva.luak.compiler.LexState.Companion.OPR_NOUNOPR
        }
    }


    fun getbinopr(op: Int): Int {
        when (op) {
            '+'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_ADD
            '-'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_SUB
            '*'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_MUL
            '/'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_DIV
            net.blueva.luak.compiler.LexState.Companion.TK_IDIV -> return net.blueva.luak.compiler.LexState.Companion.OPR_IDIV
            '&'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_BAND
            '|'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_BOR
            '~'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_BXOR
            net.blueva.luak.compiler.LexState.Companion.TK_SHL -> return net.blueva.luak.compiler.LexState.Companion.OPR_SHL
            net.blueva.luak.compiler.LexState.Companion.TK_SHR -> return net.blueva.luak.compiler.LexState.Companion.OPR_SHR
            '%'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_MOD
            '^'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_POW
            net.blueva.luak.compiler.LexState.Companion.TK_CONCAT -> return net.blueva.luak.compiler.LexState.Companion.OPR_CONCAT
            net.blueva.luak.compiler.LexState.Companion.TK_NE -> return net.blueva.luak.compiler.LexState.Companion.OPR_NE
            net.blueva.luak.compiler.LexState.Companion.TK_EQ -> return net.blueva.luak.compiler.LexState.Companion.OPR_EQ
            '<'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_LT
            net.blueva.luak.compiler.LexState.Companion.TK_LE -> return net.blueva.luak.compiler.LexState.Companion.OPR_LE
            '>'.code -> return net.blueva.luak.compiler.LexState.Companion.OPR_GT
            net.blueva.luak.compiler.LexState.Companion.TK_GE -> return net.blueva.luak.compiler.LexState.Companion.OPR_GE
            net.blueva.luak.compiler.LexState.Companion.TK_AND -> return net.blueva.luak.compiler.LexState.Companion.OPR_AND
            net.blueva.luak.compiler.LexState.Companion.TK_OR -> return net.blueva.luak.compiler.LexState.Companion.OPR_OR
            else -> return net.blueva.luak.compiler.LexState.Companion.OPR_NOBINOPR
        }
    }

    internal class Priority(i: Int, j: Int) {
        val left: Byte /* left priority for each binary operator */

        val right: Byte /* right priority */

        init {
            left = i.toByte()
            right = j.toByte()
        }
    }

    init {
        this.z = stream
        this.buff = CharArray(32)
        this.L = state
    }

    /*
	** subexpr -> (simpleexp | unop subexpr) { binop subexpr }
	** where `binop' is any binary operator with a priority higher than `limit'
	*/
    internal fun subexpr(v: expdesc, limit: Int): Int {
        var op: Int
        val uop: Int
        this.enterlevel()
        uop = getunopr(this.t.token)
        if (uop != net.blueva.luak.compiler.LexState.Companion.OPR_NOUNOPR) {
            val line = linenumber
            this.next()
            this.subexpr(v, net.blueva.luak.compiler.LexState.Companion.UNARY_PRIORITY)
            fs!!.prefix(uop, v, line)
        } else this.simpleexp(v)
        /* expand while operators have priorities higher than `limit' */
        op = getbinopr(this.t.token)
        while (op != net.blueva.luak.compiler.LexState.Companion.OPR_NOBINOPR && net.blueva.luak.compiler.LexState.Companion.priority[op]!!.left > limit) {
            val v2: expdesc = net.blueva.luak.compiler.LexState.expdesc()
            val line = linenumber
            this.next()
            fs!!.infix(op, v)
            /* read sub-expression with higher priority */
            val nextop = this.subexpr(v2, net.blueva.luak.compiler.LexState.Companion.priority[op]!!.right.toInt())
            fs!!.posfix(op, v, v2, line)
            op = nextop
        }
        this.leavelevel()
        return op /* return first untreated operator */
    }

    internal fun expr(v: expdesc) {
        this.subexpr(v, 0)
    }


    /* }==================================================================== */ /*
	** {======================================================================
	** Rules for Statements
	** =======================================================================
	*/
    fun block_follow(withuntil: Boolean): Boolean {
        when (t.token) {
            net.blueva.luak.compiler.LexState.Companion.TK_ELSE, net.blueva.luak.compiler.LexState.Companion.TK_ELSEIF, net.blueva.luak.compiler.LexState.Companion.TK_END, net.blueva.luak.compiler.LexState.Companion.TK_EOS -> return true
            net.blueva.luak.compiler.LexState.Companion.TK_UNTIL -> return withuntil
            else -> return false
        }
    }


    fun block() {
        /* block -> chunk */
        val fs: FuncState = this.fs!!
        val bl: BlockCnt = BlockCnt()
        fs.enterblock(bl, false)
        this.statlist()
        fs.leaveblock()
    }


    /*
	** structure to chain all variables in the left-hand side of an
	** assignment
	*/
    internal class LHS_assign {
        var prev: LHS_assign? = null

        /* variable (global, local, upvalue, or indexed) */
        var v: expdesc = net.blueva.luak.compiler.LexState.expdesc()
    }


    /*
	** check whether, in an assignment to a local variable, the local variable
	** is needed in a previous assignment (to a table). If so, save original
	** local value in a safe place and use this safe copy in the previous
	** assignment.
	*/
    internal fun check_conflict(lh: LHS_assign?, v: expdesc) {
        var lh = lh
        val fs: FuncState = this.fs!!
        val extra = fs.freereg.toShort() /* eventual position to save local variable */
        var conflict = false
        while (lh != null) {
            if (lh.v.k == net.blueva.luak.compiler.LexState.Companion.VINDEXED) {
                /* table is the upvalue/local being assigned now? */
                if (lh.v.u.ind_vt.toInt() == v.k && lh.v.u.ind_t.toInt() == v.u.info) {
                    conflict = true
                    lh.v.u.ind_vt = net.blueva.luak.compiler.LexState.Companion.VLOCAL.toShort()
                    lh.v.u.ind_t = extra /* previous assignment will use safe copy */
                }
                /* index is the local being assigned? (index cannot be upvalue) */
                if (v.k == net.blueva.luak.compiler.LexState.Companion.VLOCAL && lh.v.u.ind_idx.toInt() == v.u.info) {
                    conflict = true
                    lh.v.u.ind_idx = extra /* previous assignment will use safe copy */
                }
            }
            lh = lh.prev
        }
        if (conflict) {
            /* copy upvalue/local value to a temporary (in position 'extra') */
            val op: Int =
                if (v.k == net.blueva.luak.compiler.LexState.Companion.VLOCAL) Lua.OP_MOVE else Lua.OP_GETUPVAL
            fs.codeABC(op, (extra).toInt(), v.u.info, 0)
            fs.reserveregs(1)
        }
    }


    internal fun assignment(lh: LHS_assign, nvars: Int) {
        val e: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        this.check_condition(
            net.blueva.luak.compiler.LexState.Companion.VLOCAL <= lh.v.k && lh.v.k <= net.blueva.luak.compiler.LexState.Companion.VINDEXED,
            "syntax error"
        )
        this.check_readonly(lh.v)
        if (this.testnext(','.code)) {  /* assignment -> `,' primaryexp assignment */
            val nv: LHS_assign = net.blueva.luak.compiler.LexState.LHS_assign()
            nv.prev = lh
            this.suffixedexp(nv.v)
            if (nv.v.k != net.blueva.luak.compiler.LexState.Companion.VINDEXED) this.check_conflict(lh, nv.v)
            this.assignment(nv, nvars + 1)
        } else {  /* assignment . `=' explist1 */
            val nexps: Int
            this.checknext('='.code)
            nexps = this.explist(e)
            if (nexps != nvars) {
                this.adjust_assign(nvars, nexps, e)
                if (nexps > nvars) this.fs!!.freereg = (this.fs!!.freereg - (nexps - nvars)).toShort() /* remove extra values */
            } else {
                fs!!.setoneret(e) /* close last expression */
                fs!!.storevar(lh.v, e)
                return  /* avoid default */
            }
        }
        e.init(net.blueva.luak.compiler.LexState.Companion.VNONRELOC, this.fs!!.freereg - 1) /* default assignment */
        fs!!.storevar(lh.v, e)
    }


    fun cond(): Int {
        /* cond -> exp */
        val v: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        /* read condition */
        this.expr(v)
        /* `falses' are all equal here */
        if (v.k == net.blueva.luak.compiler.LexState.Companion.VNIL) v.k =
            net.blueva.luak.compiler.LexState.Companion.VFALSE
        fs!!.goiftrue(v)
        return v.f.i
    }

    fun gotostat(pc: Int) {
        val line = linenumber
        val label: LuaString?
        val g: Int
        if (testnext(net.blueva.luak.compiler.LexState.Companion.TK_GOTO)) label = str_checkname()
        else {
            next() /* skip break */
            label = LuaString.valueOf("break")
        }
        g = newlabelentry(grow(dyd.gt, dyd.n_gt + 1).also { dyd.gt = it }, dyd.n_gt++, label, line, pc)
        findlabel(g) /* close it if label already defined */
    }


    /* skip no-op statements */
    fun skipnoopstat() {
        while (t.token == ';'.code || t.token == net.blueva.luak.compiler.LexState.Companion.TK_DBCOLON) statement()
    }


    fun labelstat(label: LuaString?, line: Int) {
        /* label -> '::' NAME '::' */
        val l: Int /* index of new label being created */
        checknext(net.blueva.luak.compiler.LexState.Companion.TK_DBCOLON) /* skip double colon */
        // Read before the label is checked or entered: a run of labels one
        // after another is a single no-op, and the one that ends up entered is
        // the last of them.
        skipnoopstat() /* skip other no-op statements */
        fs!!.checkrepeated(dyd.label, dyd.n_label, (label)!!) /* check for repeated labels */
        /* create new entry for this label */
        l = newlabelentry(
            grow(dyd.label, dyd.n_label + 1).also { dyd.label = it },
            dyd.n_label++,
            label,
            line,
            fs!!.getlabel()
        )
        if (block_follow(false)) {  /* label is last no-op statement in the block? */
            /* assume that locals are already out of scope */
            dyd.label[l]!!.nactvar = fs!!.bl!!.nactvar
        }
        findgotos(dyd.label[l]!!)
    }


    fun whilestat(line: Int) {
        /* whilestat -> WHILE cond DO block END */
        val fs: FuncState = this.fs!!
        val whileinit: Int
        val condexit: Int
        val bl: BlockCnt = BlockCnt()
        this.next() /* skip WHILE */
        whileinit = fs.getlabel()
        condexit = this.cond()
        fs.enterblock(bl, true)
        this.checknext(net.blueva.luak.compiler.LexState.Companion.TK_DO)
        this.block()
        fs.patchlist(fs.jump(), whileinit)
        this.check_match(
            net.blueva.luak.compiler.LexState.Companion.TK_END,
            net.blueva.luak.compiler.LexState.Companion.TK_WHILE,
            line
        )
        fs.leaveblock()
        fs.patchtohere(condexit) /* false conditions finish the loop */
    }

    fun repeatstat(line: Int) {
        /* repeatstat -> REPEAT block UNTIL cond */
        val condexit: Int
        val fs: FuncState = this.fs!!
        val repeat_init: Int = fs.getlabel()
        val bl1: BlockCnt = BlockCnt()
        val bl2: BlockCnt = BlockCnt()
        fs.enterblock(bl1, true) /* loop block */
        fs.enterblock(bl2, false) /* scope block */
        this.next() /* skip REPEAT */
        this.statlist()
        this.check_match(
            net.blueva.luak.compiler.LexState.Companion.TK_UNTIL,
            net.blueva.luak.compiler.LexState.Companion.TK_REPEAT,
            line
        )
        condexit = this.cond() /* read condition (inside scope block) */
        if (bl2.upval) { /* upvalues? */
            fs.patchclose(condexit, (bl2.nactvar).toInt())
        }
        fs.leaveblock() /* finish scope */
        fs.patchlist(condexit, repeat_init) /* close the loop */
        fs.leaveblock() /* finish loop */
    }


    fun exp1(): Int {
        val e: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        val k: Int
        this.expr(e)
        k = e.k
        fs!!.exp2nextreg(e)
        return k
    }


    fun forbody(base: Int, line: Int, nvars: Int, isnum: Boolean) {
        /* forbody -> DO block */
        val bl: BlockCnt = BlockCnt()
        val fs: FuncState = this.fs!!
        val prep: Int
        val endfor: Int
        // A generic for has a fourth control value, closed when the loop ends,
        // which is how it can own the resource its iterator walks.
        this.adjustlocalvars(if (isnum) 3 else 4) /* control variables */
        // The mark goes in whatever the iterator turns out to return, since
        // how many values it produces is not known until it runs; a fourth
        // value of nil or false is skipped when the instruction executes.
        if (!isnum) {
            fs.markblocktobeclosed()
            fs.codeABC(Lua.OP_TBC, base + 3, 0, 0)
        }
        this.checknext(net.blueva.luak.compiler.LexState.Companion.TK_DO)
        prep = if (isnum) fs.codeAsBx(
            Lua.OP_FORPREP,
            base,
            net.blueva.luak.compiler.LexState.Companion.NO_JUMP
        ) else fs.jump()
        fs.enterblock(bl, false) /* scope for declared variables */
        this.adjustlocalvars(nvars)
        fs.reserveregs(nvars)
        this.block()
        fs.leaveblock() /* end of scope for declared variables */
        fs.patchtohere(prep)
        if (isnum)  /* numeric for? */
            endfor = fs.codeAsBx(Lua.OP_FORLOOP, base, net.blueva.luak.compiler.LexState.Companion.NO_JUMP)
        else {  /* generic for */
            fs.codeABC(Lua.OP_TFORCALL, base, 0, nvars)
            fs.fixline(line)
            endfor = fs.codeAsBx(Lua.OP_TFORLOOP, base + 2, net.blueva.luak.compiler.LexState.Companion.NO_JUMP)
        }
        fs.patchlist(endfor, prep + 1)
        fs.fixline(line)
    }


    fun fornum(varname: LuaString?, line: Int) {
        /* fornum -> NAME = exp1,exp1[,exp1] forbody */
        val fs: FuncState = this.fs!!
        val base: Int = fs.freereg.toInt()
        // The loop's own variable is a constant: Lua 5.5 refuses an
        // assignment to it, since the loop overwrites it every pass anyway.
        this.new_localvarliteral(net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_INDEX)
        this.new_localvarliteral(net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_LIMIT)
        this.new_localvarliteral(net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_STEP)
        this.new_varkind(varname, net.blueva.luak.compiler.LexState.Companion.RDKCONST)
        this.checknext('='.code)
        this.exp1() /* initial value */
        this.checknext(','.code)
        this.exp1() /* limit */
        if (this.testnext(','.code)) this.exp1() /* optional step */
        else { /* default step = 1 */
            fs.codeK((fs.freereg).toInt(), fs.numberK((LuaInteger.valueOf(1))!!))
            fs.reserveregs(1)
        }
        this.forbody(base, line, 1, true)
    }


    fun forlist(indexname: LuaString?) {
        /* forlist -> NAME {,NAME} IN explist1 forbody */
        val fs: FuncState = this.fs!!
        val e: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        var nvars = 5 /* gen, state, control, closing, plus one declared var */
        val line: Int
        val base: Int = fs.freereg.toInt()
        /* create control variables */
        this.new_localvarliteral(net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_GENERATOR)
        this.new_localvarliteral(net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_STATE)
        this.new_localvarliteral(net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_CONTROL)
        this.new_localvarliteral(net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_CLOSING)
        /* create declared variables */
        // The first one is the control variable, which the loop overwrites
        // every pass, so Lua 5.5 refuses an assignment to it.
        this.new_varkind(indexname, net.blueva.luak.compiler.LexState.Companion.RDKCONST)
        while (this.testnext(','.code)) {
            this.new_localvar(this.str_checkname())
            ++nvars
        }
        this.checknext(net.blueva.luak.compiler.LexState.Companion.TK_IN)
        line = this.linenumber
        val nexps: Int = this.explist(e)
        this.adjust_assign(4, nexps, e)
        fs.checkstack(3) /* extra space to call generator */
        this.forbody(base, line, nvars - 4, false)
    }


    fun forstat(line: Int) {
        /* forstat -> FOR (fornum | forlist) END */
        val fs: FuncState = this.fs!!
        val varname: LuaString?
        val bl: BlockCnt = BlockCnt()
        fs.enterblock(bl, true) /* scope for loop and control variables */
        this.next() /* skip `for' */
        varname = this.str_checkname() /* first variable name */
        when (this.t.token) {
            '='.code -> this.fornum(varname, line)
            ','.code, net.blueva.luak.compiler.LexState.Companion.TK_IN -> this.forlist(varname)
            else -> this.syntaxerror(
                net.blueva.luak.compiler.LexState.Companion.LUA_QL("=")
                    .toString() + " or " + net.blueva.luak.compiler.LexState.Companion.LUA_QL("in") + " expected"
            )
        }
        this.check_match(
            net.blueva.luak.compiler.LexState.Companion.TK_END,
            net.blueva.luak.compiler.LexState.Companion.TK_FOR,
            line
        )
        fs.leaveblock() /* loop scope (`break' jumps to this point) */
    }


    /**
     * `test_then_block -> [IF | ELSEIF] cond THEN block`
     *
     * Lua 5.2 had a special case here for a `goto` or `break` written as the
     * first statement after `then`, which registered any labels that followed
     * it before emitting the jump that skips the block - so a label could end
     * up pointing at that jump. 5.5 dropped it; the ordinary block handles the
     * same code correctly.
     */
    fun test_then_block(escapelist: IntPtr?) {
        val v: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        this.next() /* skip IF or ELSEIF */
        expr(v) /* read condition */
        if (v.k == net.blueva.luak.compiler.LexState.Companion.VNIL) v.k = net.blueva.luak.compiler.LexState.Companion.VFALSE /* 'falses' are all equal here */
        fs!!.goiftrue(v)
        val condtrue: Int = v.f.i
        this.checknext(net.blueva.luak.compiler.LexState.Companion.TK_THEN)
        this.block() /* 'then' part */
        if (t.token == net.blueva.luak.compiler.LexState.Companion.TK_ELSE || t.token == net.blueva.luak.compiler.LexState.Companion.TK_ELSEIF) {
            fs!!.concat((escapelist)!!, fs!!.jump()) /* must jump over it */
        }
        fs!!.patchtohere(condtrue)
    }


    fun ifstat(line: Int) {
        val escapelist: IntPtr =
            IntPtr(net.blueva.luak.compiler.LexState.Companion.NO_JUMP) /* exit list for finished parts */
        test_then_block(escapelist) /* IF cond THEN block */
        while (t.token == net.blueva.luak.compiler.LexState.Companion.TK_ELSEIF) test_then_block(escapelist) /* ELSEIF cond THEN block */
        if (testnext(net.blueva.luak.compiler.LexState.Companion.TK_ELSE)) block() /* `else' part */
        check_match(
            net.blueva.luak.compiler.LexState.Companion.TK_END,
            net.blueva.luak.compiler.LexState.Companion.TK_IF,
            line
        )
        fs!!.patchtohere(escapelist.i) /* patch escape list to 'if' end */
    }

    fun localfunc() {
        val b: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        val fs: FuncState = this.fs!!
        this.new_localvar(this.str_checkname())
        this.adjustlocalvars(1)
        this.body(b, false, this.linenumber)
        /* debug information will only see the variable after this point! */
        fs.getlocvar(fs.nactvar - 1).startpc = fs.pc
    }


    fun localstat() {
        /* stat -> LOCAL attrib NAME attrib {`,' NAME attrib} [`=' explist1] */
        val fs: FuncState = this.fs!!
        var nvars = 0
        var toclose = -1 /* index, among the new variables, of the <close> one */
        val nexps: Int
        val e: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        /* an attribute before the names is the default for all of them */
        val defaultkind: Int = this.getlocalattribute(net.blueva.luak.compiler.LexState.Companion.VDKREG)
        do {
            this.new_localvar(this.str_checkname())
            val kind = this.getlocalattribute(defaultkind)
            this.dyd!!.actvar!![this.dyd!!.n_actvar - 1]!!.kind = kind
            if (kind == net.blueva.luak.compiler.LexState.Companion.RDKTOCLOSE) {
                // One per statement: closing runs in reverse declaration order,
                // which a single statement has no way to express for two.
                if (toclose != -1) {
                    this.semerror("multiple to-be-closed variables in local list")
                }
                toclose = fs.nactvar + nvars
            }
            ++nvars
        } while (this.testnext(','.code))
        if (this.testnext('='.code)) nexps = this.explist(e)
        else {
            e.k = net.blueva.luak.compiler.LexState.Companion.VVOID
            nexps = 0
        }
        this.adjust_assign(nvars, nexps, e)
        this.adjustlocalvars(nvars)
        if (toclose != -1) {
            // The enclosing block has to be left through a closing jump now,
            // the same one that closes upvalues, so leaving it by any route
            // runs the variable's __close.
            fs.markblocktobeclosed()
            fs.codeABC(Lua.OP_TBC, toclose, 0, 0)
        }
    }


    /**
     * `attrib -> ['<' NAME '>']`, giving the kind of the local just declared.
     *
     * `<const>` marks the variable read-only, which is enforced in
     * [check_readonly]. `<close>` does the same and additionally has the value
     * registered as to-be-closed, so that leaving the block by any route runs
     * its `__close` metamethod.
     */
    /** True when what follows `global` can only be a declaration. */
    private fun startsglobalstat(): Boolean {
        this.lookahead()
        val next: Int = this.lookahead.token
        return next == '<'.code || next == '*'.code ||
            next == net.blueva.luak.compiler.LexState.Companion.TK_NAME ||
            next == net.blueva.luak.compiler.LexState.Companion.TK_FUNCTION
    }

    /**
     * `globalstatfunc -> GLOBAL (globalfunc | globalstat)`, from Lua 5.5.
     *
     * A `global` declaration says which globals a chunk means to use. Once one
     * names anything, every other free name in the scope has to be declared as
     * well, which turns a misspelt global from a silent nil into a compile
     * error. `global *` declares them all and puts the old default back.
     */
    internal fun globalstatfunc(line: Int) {
        this.next() /* skip 'global' */
        if (this.testnext(net.blueva.luak.compiler.LexState.Companion.TK_FUNCTION)) this.globalfunc(line)
        else this.globalstat()
    }

    /**
     * `globalstat -> attrib '*' | attrib NAME attrib {',' NAME attrib} ['=' explist]`
     */
    private fun globalstat() {
        val fs: FuncState = this.fs!!
        /* an attribute before the names is the default for all of them */
        val defaultkind: Int = this.getglobalattribute(net.blueva.luak.compiler.LexState.Companion.VDKREG)
        if (this.testnext('*'.code)) {
            fs.globals.add(
                FuncState.Globaldesc(
                    null,
                    defaultkind == net.blueva.luak.compiler.LexState.Companion.RDKCONST,
                    fs.nactvar.toInt(),
                ),
            )
            return
        }
        val names: ArrayList<LuaString> = ArrayList()
        val readonly: ArrayList<Boolean> = ArrayList()
        do {
            val varname: LuaString = this.str_checkname()!!
            val kind: Int = this.getglobalattribute(defaultkind)
            names.add(varname)
            readonly.add(kind == net.blueva.luak.compiler.LexState.Companion.RDKCONST)
        } while (this.testnext(','.code))
        if (this.testnext('='.code)) this.initglobal(names, 0, this.linenumber)
        /* the names come into scope only after their own initializers */
        for (i in names.indices) {
            fs.globals.add(FuncState.Globaldesc(names[i], readonly[i], fs.nactvar.toInt()))
        }
    }

    /**
     * Assigns an initializer list to freshly declared globals.
     *
     * The targets have to be built before the values are read, and the values
     * are then taken off the stack from the top down, so the recursion walks
     * out to the last name, reads the expression list there, and assigns on the
     * way back.
     */
    private fun initglobal(names: ArrayList<LuaString>, index: Int, line: Int) {
        if (index == names.size) {
            val e: expdesc = net.blueva.luak.compiler.LexState.expdesc()
            val nexps: Int = this.explist(e)
            this.adjust_assign(names.size, nexps, e)
            return
        }
        val fs: FuncState = this.fs!!
        val target: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        this.buildglobal(names[index], target)
        this.enterlevel()
        this.initglobal(names, index + 1, line)
        this.leavelevel()
        this.checkglobal(names[index], line)
        val value: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        value.init(net.blueva.luak.compiler.LexState.Companion.VNONRELOC, fs.freereg - 1)
        fs.storevar(target, value)
    }

    /**
     * Emits the check that a global being declared with a value is still unset.
     *
     * Declaring the same global twice is nearly always a mistake, and it can
     * only be caught when the chunk runs, since another chunk may have set it.
     */
    private fun checkglobal(varname: LuaString, line: Int) {
        val fs: FuncState = this.fs!!
        val `var`: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        this.buildglobal(varname, `var`)
        val nameindex: Int = fs.stringK(varname)
        fs.exp2anyreg(`var`)
        fs.fixline(line)
        fs.codeABx(
            Lua.OP_ERRNNIL,
            `var`.u.info,
            if (nameindex >= Lua.MAXARG_Bx) 0 else nameindex + 1,
        )
        fs.fixline(line)
        fs.freeexp(`var`)
    }

    /** `globalfunc -> GLOBAL FUNCTION NAME body` */
    private fun globalfunc(line: Int) {
        val fs: FuncState = this.fs!!
        val fname: LuaString = this.str_checkname()!!
        fs.globals.add(FuncState.Globaldesc(fname, false, fs.nactvar.toInt()))
        val `var`: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        this.buildglobal(fname, `var`)
        val b: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        this.body(b, false, this.linenumber)
        this.checkglobal(fname, line)
        fs.storevar(`var`, b)
    }

    /**
     * `attrib` on a global, which accepts `<const>` but not `<close>`.
     *
     * There is no scope for a global to be closed at the end of, so `<close>`
     * is rejected rather than quietly treated as `<const>`.
     */
    private fun getglobalattribute(default: Int): Int {
        if (this.t.token != '<'.code) return default
        val kind: Int = this.getlocalattribute(default)
        if (kind == net.blueva.luak.compiler.LexState.Companion.RDKTOCLOSE) {
            this.semerror("global variables cannot be to-be-closed")
        }
        return kind
    }

    internal fun getlocalattribute(default: Int): Int {
        if (this.testnext('<'.code)) {
            val attribute: String? = this.str_checkname()?.tojstring()
            this.checknext('>'.code)
            if ("const" == attribute) return net.blueva.luak.compiler.LexState.Companion.RDKCONST
            if ("close" == attribute) return net.blueva.luak.compiler.LexState.Companion.RDKTOCLOSE
            this.lexerror("unknown attribute '" + attribute + "'", net.blueva.luak.compiler.LexState.Companion.TK_NAME)
        }
        return default
    }

    /** Rejects an assignment to a `<const>` or `<close>` local, or a `<const>` global. */
    internal fun check_readonly(e: expdesc) {
        val globalname: LuaString? = e.readonlyGlobal
        if (globalname != null) {
            this.semerror("attempt to assign to const variable '" + globalname.tojstring() + "'")
        }
        if (e.k == net.blueva.luak.compiler.LexState.Companion.VUPVAL) {
            // The same variable seen from an inner function.
            val up = this.fs?.f?.upvalues?.getOrNull(e.u.info) ?: return
            if (up.kind != net.blueva.luak.compiler.LexState.Companion.VDKREG) {
                this.semerror(
                    "attempt to assign to const variable '" + (up.name?.tojstring() ?: "?") + "'",
                )
            }
            return
        }
        if (e.k != net.blueva.luak.compiler.LexState.Companion.VLOCAL) return
        val fs: FuncState = this.fs!!
        val index: Int = fs.firstlocal + e.u.info
        val vars: Array<Vardesc?> = this.dyd?.actvar ?: return
        if (index < 0 || index >= vars.size) return
        // A <close> variable is read-only too: the value it holds is the one
        // that will be closed, so it must be the one it was given.
        val kind: Int = vars[index]?.kind ?: return
        if (kind == net.blueva.luak.compiler.LexState.Companion.RDKCONST ||
            kind == net.blueva.luak.compiler.LexState.Companion.RDKTOCLOSE
        ) {
            val name: String = fs.getlocvar(e.u.info).varname?.tojstring() ?: "?"
            // A semantic error, not a lexical one: the offending token has
            // already been read, so there is no "near" to report.
            this.semerror("attempt to assign to const variable '" + name + "'")
        }
    }

    internal fun funcname(v: expdesc): Boolean {
        /* funcname -> NAME {field} [`:' NAME] */
        var ismethod = false
        this.singlevar(v)
        while (this.t.token == '.'.code) this.fieldsel(v)
        if (this.t.token == ':'.code) {
            ismethod = true
            this.fieldsel(v)
        }
        return ismethod
    }


    fun funcstat(line: Int) {
        /* funcstat -> FUNCTION funcname body */
        val needself: Boolean
        val v: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        val b: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        this.next() /* skip FUNCTION */
        needself = this.funcname(v)
        // "function f() end" assigns to f, so a read-only f is as much an
        // error here as it would be written out as an assignment.
        this.check_readonly(v)
        this.body(b, needself, line)
        fs!!.storevar(v, b)
        fs!!.fixline(line) /* definition `happens' in the first line */
    }


    fun exprstat() {
        /* stat -> func | assignment */
        val fs: FuncState = this.fs!!
        val v: LHS_assign = net.blueva.luak.compiler.LexState.LHS_assign()
        this.suffixedexp(v.v)
        if (t.token == '='.code || t.token == ','.code) { /* stat -> assignment ? */
            v.prev = null
            assignment(v, 1)
        } else {  /* stat -> func */
            check_condition(v.v.k == net.blueva.luak.compiler.LexState.Companion.VCALL, "syntax error")
            SETARG_C((fs.getcodePtr(v.v))!!, 1) /* call statement uses no results */
        }
    }

    fun retstat() {
        /* stat -> RETURN explist */
        val fs: FuncState = this.fs!!
        val e: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        val first: Int
        var nret: Int /* registers with returned values */
        if (block_follow(true) || this.t.token == ';'.code) {
            nret = 0
            first = nret /* return no values */
        } else {
            nret = this.explist(e) /* optional return values */
            if (hasmultret(e.k)) {
                fs.setmultret(e)
                // Inside the scope of a to-be-closed variable the frame has
                // to outlive the call, so the return stays an ordinary one.
                if (e.k == net.blueva.luak.compiler.LexState.Companion.VCALL && nret == 1 &&
                    !fs.bl!!.insidetbc
                ) { /* tail call? */
                    SET_OPCODE((fs.getcodePtr(e))!!, Lua.OP_TAILCALL)
                    _assert(Lua.GETARG_A(fs.getcode(e)) == fs.nactvar.toInt())
                }
                first = fs.nactvar.toInt()
                nret = Lua.LUA_MULTRET /* return all values */
            } else {
                if (nret == 1)  /* only one single value? */
                    first = fs.exp2anyreg(e)
                else {
                    fs.exp2nextreg(e) /* values must go to the `stack' */
                    first = fs.nactvar.toInt() /* return all `active' values */
                    _assert(nret == fs.freereg - first)
                }
            }
        }
        fs.ret(first, nret)
        testnext(';'.code) /* skip optional semicolon */
    }

    fun statement() {
        val line = this.linenumber /* may be needed for error messages */
        enterlevel()
        when (this.t.token) {
            ';'.code -> {
                /* stat -> ';' (empty statement) */
                next() /* skip ';' */
            }

            net.blueva.luak.compiler.LexState.Companion.TK_IF -> {
                /* stat -> ifstat */
                this.ifstat(line)
            }

            net.blueva.luak.compiler.LexState.Companion.TK_WHILE -> {
                /* stat -> whilestat */
                this.whilestat(line)
            }

            net.blueva.luak.compiler.LexState.Companion.TK_DO -> {
                /* stat -> DO block END */
                this.next() /* skip DO */
                this.block()
                this.check_match(
                    net.blueva.luak.compiler.LexState.Companion.TK_END,
                    net.blueva.luak.compiler.LexState.Companion.TK_DO,
                    line
                )
            }

            net.blueva.luak.compiler.LexState.Companion.TK_FOR -> {
                /* stat -> forstat */
                this.forstat(line)
            }

            net.blueva.luak.compiler.LexState.Companion.TK_REPEAT -> {
                /* stat -> repeatstat */
                this.repeatstat(line)
            }

            net.blueva.luak.compiler.LexState.Companion.TK_FUNCTION -> {
                this.funcstat(line) /* stat -> funcstat */
            }

            net.blueva.luak.compiler.LexState.Companion.TK_LOCAL -> {
                /* stat -> localstat */
                this.next() /* skip LOCAL */
                if (this.testnext(net.blueva.luak.compiler.LexState.Companion.TK_FUNCTION))  /* local function? */
                    this.localfunc()
                else this.localstat()
            }

            net.blueva.luak.compiler.LexState.Companion.TK_DBCOLON -> {
                /* stat -> label */
                next() /* skip double colon */
                labelstat(str_checkname(), line)
            }

            net.blueva.luak.compiler.LexState.Companion.TK_RETURN -> {
                /* stat -> retstat */
                next() /* skip RETURN */
                this.retstat()
            }

            net.blueva.luak.compiler.LexState.Companion.TK_BREAK, net.blueva.luak.compiler.LexState.Companion.TK_GOTO -> {
                /* stat -> breakstat */
                this.gotostat(fs!!.jump())
            }

            net.blueva.luak.compiler.LexState.Companion.TK_NAME -> {
                // 'global' is a statement, not a reserved word: a program that
                // already uses it as a name keeps working, and only the shapes
                // a declaration can take are read as one.
                if (this.t.seminfo.ts == this.glbn && this.startsglobalstat()) {
                    this.globalstatfunc(line)
                } else {
                    this.exprstat()
                }
            }

            else -> {
                this.exprstat()
            }
        }
        _assert(
            fs!!.f!!.maxstacksize >= fs!!.freereg
                    && fs!!.freereg >= fs!!.nactvar
        )
        fs!!.freereg = fs!!.nactvar /* free registers */
        leavelevel()
    }

    fun statlist() {
        /* statlist -> { stat [`;'] } */
        while (!block_follow(true)) {
            if (t.token == net.blueva.luak.compiler.LexState.Companion.TK_RETURN) {
                statement()
                return  /* 'return' must be last statement */
            }
            statement()
        }
    }

    /*
	** compiles the main function, which is a regular vararg function with an
	** upvalue named LUA_ENV
	*/
    internal fun mainfunc(funcstate: FuncState) {
        val bl: BlockCnt = BlockCnt()
        open_func(funcstate, bl)
        fs!!.f!!.is_vararg = 1 /* main function is always vararg */
        val v: expdesc = net.blueva.luak.compiler.LexState.expdesc()
        v.init(net.blueva.luak.compiler.LexState.Companion.VLOCAL, 0) /* create and... */
        fs!!.newupvalue(envn, v) /* ...set environment upvalue */
        next() /* read first token */
        statlist() /* parse main body */
        check(net.blueva.luak.compiler.LexState.Companion.TK_EOS)
        close_func()
    } /* }====================================================================== */

    companion object {
        protected val RESERVED_LOCAL_VAR_FOR_CONTROL: String = "(for control)"

        /** The slot every vararg function keeps for the table form of `...`. */
        protected val RESERVED_LOCAL_VAR_FOR_VARARGS: String = "(vararg table)"

        // The iterator, the state and the value the loop closes at the end all
        // go by one name, as they do upstream, so code that walks a frame's
        // locals counts them the way Lua's own test suite expects: the third
        // "(for state)" is the closing one.
        protected val RESERVED_LOCAL_VAR_FOR_CLOSING: String = "(for state)"
        protected val RESERVED_LOCAL_VAR_FOR_STATE: String = "(for state)"
        protected val RESERVED_LOCAL_VAR_FOR_GENERATOR: String = "(for state)"
        protected val RESERVED_LOCAL_VAR_FOR_STEP: String = "(for step)"
        protected val RESERVED_LOCAL_VAR_FOR_LIMIT: String = "(for limit)"
        protected val RESERVED_LOCAL_VAR_FOR_INDEX: String = "(for index)"

        // keywords array
        protected val RESERVED_LOCAL_VAR_KEYWORDS: Array<String?> = arrayOf<String?>(
            net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_CONTROL,
            net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_CLOSING,
            net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_GENERATOR,
            net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_INDEX,
            net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_LIMIT,
            net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_STATE,
            net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_FOR_STEP
        )
        private val RESERVED_LOCAL_VAR_KEYWORDS_TABLE: HashMap<String?, Boolean> = HashMap()

        init {
            for (i in net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_KEYWORDS.indices) net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_KEYWORDS_TABLE!!.put(
                net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_KEYWORDS[i],
                true
            )
        }

        private val EOZ = (-1)
        private val MAX_INT: Int = Int.MAX_VALUE - 2
        private const val UCHAR_MAX = 255 // TODO, convert to unicode CHAR_MAX?
        private const val LUAI_MAXCCALLS = 200

        private fun LUA_QS(s: String?): String {
            return "'" + s + "'"
        }

        private fun LUA_QL(o: Any?): String {
            return net.blueva.luak.compiler.LexState.Companion.LUA_QS((o).toString())
        }

        private const val LUA_COMPAT_LSTR = 1 // 1 for compatibility, 2 for old behavior
        private const val LUA_COMPAT_VARARG = true

        fun isReservedKeyword(varName: String?): Boolean {
            return net.blueva.luak.compiler.LexState.Companion.RESERVED_LOCAL_VAR_KEYWORDS_TABLE.containsKey(varName)
        }

        /*
	** Marks the end of a patch list. It is an invalid value both as an absolute
	** address, and as a list link (would link an element to itself).
	*/
        val NO_JUMP: Int = (-1)

        /*
	** grep "ORDER OPR" if you change these enums
	*/
        const val OPR_ADD: Int = 0
        const val OPR_SUB: Int = 1
        const val OPR_MUL: Int = 2
        const val OPR_DIV: Int = 3
        const val OPR_MOD: Int = 4
        const val OPR_POW: Int = 5
        const val OPR_CONCAT: Int = 6
        const val OPR_NE: Int = 7
        const val OPR_EQ: Int = 8
        const val OPR_LT: Int = 9
        const val OPR_LE: Int = 10
        const val OPR_GT: Int = 11
        const val OPR_GE: Int = 12
        const val OPR_AND: Int = 13
        const val OPR_OR: Int = 14
        const val OPR_IDIV: Int = 15
        const val OPR_BAND: Int = 16
        const val OPR_BOR: Int = 17
        const val OPR_BXOR: Int = 18
        const val OPR_SHL: Int = 19
        const val OPR_SHR: Int = 20
        const val OPR_NOBINOPR: Int = 21

        const val OPR_MINUS: Int = 0
        const val OPR_NOT: Int = 1
        const val OPR_LEN: Int = 2
        const val OPR_BNOT: Int = 3
        const val OPR_NOUNOPR: Int = 4

        /* exp kind */
        const val VVOID: Int = 0 /* no value */
        const val VNIL: Int = 1
        const val VTRUE: Int = 2
        const val VFALSE: Int = 3
        const val VK: Int = 4 /* info = index of constant in `k' */
        const val VKNUM: Int = 5 /* nval = numerical value */
        const val VNONRELOC: Int = 6 /* info = result register */
        const val VLOCAL: Int = 7 /* info = local register */
        const val VUPVAL: Int = 8 /* info = index of upvalue in `upvalues' */
        const val VINDEXED: Int = 9 /* info = table register, aux = index register (or `k') */
        const val VJMP: Int = 10 /* info = instruction pc */
        const val VRELOCABLE: Int = 11 /* info = instruction pc */
        const val VCALL: Int = 12 /* info = instruction pc */
        const val VVARARG: Int = 13 /* info = instruction pc */

        /* ORDER RESERVED */
        val luaX_tokens: Array<String?>? = arrayOf<String?>(
            "and", "break", "do", "else", "elseif",
            "end", "false", "for", "function", "goto", "if",
            "in", "local", "nil", "not", "or", "repeat",
            "return", "then", "true", "until", "while",
            "..", "...", "==", ">=", "<=", "~=",
            "::", "<eof>", "<number>", "<name>", "<string>", "//", "<<", ">>",
        )

        const val  /* terminal symbols denoted by reserved words */TK_AND: Int = 257
        const val TK_BREAK: Int = 258
        const val TK_DO: Int = 259
        const val TK_ELSE: Int = 260
        const val TK_ELSEIF: Int = 261
        const val TK_END: Int = 262
        const val TK_FALSE: Int = 263
        const val TK_FOR: Int = 264
        const val TK_FUNCTION: Int = 265
        const val TK_GOTO: Int = 266
        const val TK_IF: Int = 267
        const val TK_IN: Int = 268
        const val TK_LOCAL: Int = 269
        const val TK_NIL: Int = 270
        const val TK_NOT: Int = 271
        const val TK_OR: Int = 272
        const val TK_REPEAT: Int = 273
        const val TK_RETURN: Int = 274
        const val TK_THEN: Int = 275
        const val TK_TRUE: Int = 276
        const val TK_UNTIL: Int = 277
        const val TK_WHILE: Int = 278

        /* other terminal symbols */
        const val TK_CONCAT: Int = 279
        const val TK_DOTS: Int = 280
        const val TK_EQ: Int = 281
        const val TK_GE: Int = 282
        const val TK_LE: Int = 283
        const val TK_NE: Int = 284
        const val TK_DBCOLON: Int = 285
        const val TK_EOS: Int = 286
        const val TK_NUMBER: Int = 287
        const val TK_NAME: Int = 288
        const val TK_STRING: Int = 289
        const val TK_IDIV: Int = 290
        const val TK_SHL: Int = 291
        const val TK_SHR: Int = 292

        val FIRST_RESERVED: Int = net.blueva.luak.compiler.LexState.Companion.TK_AND
        val NUM_RESERVED: Int =
            net.blueva.luak.compiler.LexState.Companion.TK_WHILE + 1 - net.blueva.luak.compiler.LexState.Companion.FIRST_RESERVED

        val RESERVED: HashMap<LuaString?, Int> = HashMap()

        init {
            for (i in 0..<net.blueva.luak.compiler.LexState.Companion.NUM_RESERVED) {
                val ts: LuaString? =
                    LuaValue.valueOf(net.blueva.luak.compiler.LexState.Companion.luaX_tokens!![i]) as LuaString?
                net.blueva.luak.compiler.LexState.Companion.RESERVED!!.put(
                    ts,
                    net.blueva.luak.compiler.LexState.Companion.FIRST_RESERVED + i
                )
            }
        }

        /**
         * True for a byte that cannot be shown as itself.
         *
         * Only the printable ASCII range is shown as a character; anything
         * else, a byte past 127 included, is written as its number, since
         * what it looks like depends on how the text is being read.
         */
        private fun iscntrl(token: Int): Boolean {
            return token < ' '.code || token > '~'.code
        }

        // =============================================================
        // from lcode.h
        // =============================================================
        // =============================================================
        // from lparser.c
        // =============================================================
        fun vkisvar(k: Int): Boolean {
            return (net.blueva.luak.compiler.LexState.Companion.VLOCAL <= (k) && (k) <= net.blueva.luak.compiler.LexState.Companion.VINDEXED)
        }

        fun vkisinreg(k: Int): Boolean {
            return ((k) == net.blueva.luak.compiler.LexState.Companion.VNONRELOC || (k) == net.blueva.luak.compiler.LexState.Companion.VLOCAL)
        }

        /*
	** converts an integer to a "floating point byte", represented as
	** (eeeeexxx), where the real value is (1xxx) * 2^(eeeee - 1) if
	** eeeee != 0 and (xxx) otherwise.
	*/
        fun luaO_int2fb(x: Int): Int {
            var x = x
            var e = 0 /* expoent */
            while (x >= 16) {
                x = (x + 1) shr 1
                e++
            }
            if (x < 8) return x
            else return ((e + 1) shl 3) or (x - 8)
        }


        // Levels follow Lua 5.5's table so the operators added by the port have
        // room between comparison and concatenation. The relative order of the
        // operators that already existed is unchanged.
        internal var priority: Array<Priority?> =
            arrayOf<Priority?>( /* ORDER OPR */net.blueva.luak.compiler.LexState.Priority(10, 10),
                net.blueva.luak.compiler.LexState.Priority(10, 10),  /* `+' `-' */
                net.blueva.luak.compiler.LexState.Priority(11, 11),
                net.blueva.luak.compiler.LexState.Priority(11, 11),
                net.blueva.luak.compiler.LexState.Priority(11, 11),  /* `*' `/' `%' */
                net.blueva.luak.compiler.LexState.Priority(14, 13),
                net.blueva.luak.compiler.LexState.Priority(9, 8),  /* power and concat (right associative) */
                net.blueva.luak.compiler.LexState.Priority(3, 3),
                net.blueva.luak.compiler.LexState.Priority(3, 3),  /* equality and inequality */
                net.blueva.luak.compiler.LexState.Priority(3, 3),
                net.blueva.luak.compiler.LexState.Priority(3, 3),
                net.blueva.luak.compiler.LexState.Priority(3, 3),
                net.blueva.luak.compiler.LexState.Priority(3, 3),  /* order */
                net.blueva.luak.compiler.LexState.Priority(2, 2),
                net.blueva.luak.compiler.LexState.Priority(1, 1),  /* logical (and/or) */
                net.blueva.luak.compiler.LexState.Priority(11, 11),  /* `//' */
                net.blueva.luak.compiler.LexState.Priority(6, 6),  /* `&' */
                net.blueva.luak.compiler.LexState.Priority(4, 4),  /* `|' */
                net.blueva.luak.compiler.LexState.Priority(5, 5),  /* `~' */
                net.blueva.luak.compiler.LexState.Priority(7, 7),
                net.blueva.luak.compiler.LexState.Priority(7, 7) /* `<<' `>>' */
            )

        const val UNARY_PRIORITY: Int = 12 /* priority for unary operators */

        /* kinds of local variable, from the attribute in its declaration */
        const val VDKREG: Int = 0 /* regular */
        const val RDKCONST: Int = 1 /* <const> */
        const val RDKTOCLOSE: Int = 2 /* <close> */
    }
}
