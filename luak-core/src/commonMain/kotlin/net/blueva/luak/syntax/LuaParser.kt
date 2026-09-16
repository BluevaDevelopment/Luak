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
 *  Copyright (c) 2026 Blueva Development
 *
 *  SPDX-License-Identifier: MIT
 ******************************************************************************/
package net.blueva.luak.syntax

import net.blueva.luak.LuaString

/**
 * Reads Lua 5.5 source into a [Chunk].
 *
 * The grammar is the compiler's (`LexState`): `global` starts a declaration
 * only when a name, `function`, `<` or `*` follows it, and anywhere else it
 * is an ordinary name. What this does not do is the compiler's semantic
 * checks - assigning to a `<const>`, a `goto` with no label, an undeclared
 * name under a `global` declaration. Source it accepts can still fail to
 * compile, so a tool that must agree with the runtime compiles first
 * (`Globals.load`) and reads the tree second.
 */
public object LuaParser {
    /** @throws LuaSyntaxException when the source is not Lua. */
    public fun parse(source: String): Chunk {
        val lexer = Lexer(source)
        lexer.run()
        return Parser(lexer.tokens, lexer.comments).chunk()
    }
}

private enum class Kind { EOF, NAME, KEYWORD, NUMBER, STRING, PUNCT, NIL, TRUE, FALSE, VARARG }

private class Token(
    val kind: Kind,
    val text: String,
    val start: Int,
    val end: Int,
    val line: Int,
    val lineStart: Int,
    /** Where the token ends, for the ones that span lines. */
    val lastLine: Int,
    val lastLineStart: Int,
    val node: LuaNode? = null,
)

private val KEYWORDS = setOf(
    "and", "break", "do", "else", "elseif", "end", "for", "function", "goto", "if", "in",
    "local", "not", "or", "repeat", "return", "then", "until", "while",
)

private val PUNCTUATORS_2 = setOf("..", "==", "~=", "<=", ">=", "<<", ">>", "//", "::")

private const val PUNCTUATORS_1 = "+-*/%^#&~|<>=(){}[];:,."

private class Lexer(private val src: String) {
    val tokens = ArrayList<Token>()
    val comments = ArrayList<Comment>()

    private val n = src.length
    private var i = 0
    private var line = 1
    private var lineStart = 0

    fun run() {
        while (true) {
            skipSpaceAndComments()
            val start = i
            val startLine = line
            val startLineStart = lineStart
            if (i >= n) {
                tokens.add(Token(Kind.EOF, "<eof>", n, n, line, lineStart, line, lineStart))
                return
            }
            val c = src[i]
            var node: LuaNode? = null
            val kind: Kind
            when {
                isNameStart(c) -> {
                    while (i < n && isNameChar(src[i])) i++
                    kind = when (src.substring(start, i)) {
                        "nil" -> Kind.NIL
                        "true" -> Kind.TRUE
                        "false" -> Kind.FALSE
                        in KEYWORDS -> Kind.KEYWORD
                        else -> Kind.NAME
                    }
                }
                isDigit(c) || (c == '.' && i + 1 < n && isDigit(src[i + 1])) -> {
                    node = number()
                    kind = Kind.NUMBER
                }
                c == '"' || c == '\'' -> {
                    node = StringLiteral(quoted(), src.substring(start, i))
                    kind = Kind.STRING
                }
                c == '[' && longLevel(i) >= 0 -> {
                    node = StringLiteral(normalizeNewlines(long(longLevel(i), "string")), src.substring(start, i))
                    kind = Kind.STRING
                }
                src.startsWith("...", i) -> {
                    i += 3
                    kind = Kind.VARARG
                }
                i + 1 < n && src.substring(i, i + 2) in PUNCTUATORS_2 -> {
                    i += 2
                    kind = Kind.PUNCT
                }
                PUNCTUATORS_1.indexOf(c) >= 0 -> {
                    i++
                    kind = Kind.PUNCT
                }
                else -> fail("unexpected symbol near '$c'", startLine, start - startLineStart)
            }
            tokens.add(Token(kind, src.substring(start, i), start, i, startLine, startLineStart, line, lineStart, node))
        }
    }

    private fun fail(message: String, atLine: Int = line, column: Int = i - lineStart): Nothing =
        throw LuaSyntaxException("$atLine: $message", atLine, column)

    private fun isNameStart(c: Char) = c == '_' || c in 'a'..'z' || c in 'A'..'Z'

    private fun isNameChar(c: Char) = isNameStart(c) || isDigit(c)

    private fun isDigit(c: Char) = c in '0'..'9'

    private fun isHex(c: Char) = isDigit(c) || c in 'a'..'f' || c in 'A'..'F'

    private fun isNewline(c: Char) = c == '\n' || c == '\r'

    /** Consumes one line break; `\r\n` and `\n\r` count as one, as in Lua. */
    private fun newline() {
        val c = src[i++]
        if (i < n && isNewline(src[i]) && src[i] != c) i++
        line++
        lineStart = i
    }

    private fun skipSpaceAndComments() {
        while (i < n) {
            val c = src[i]
            when {
                isNewline(c) -> newline()
                c == ' ' || c == '\t' || c == '' || c == '' -> i++
                c == '-' && i + 1 < n && src[i + 1] == '-' -> comment()
                else -> return
            }
        }
    }

    private fun comment() {
        val start = i
        val startLine = line
        val startColumn = i - lineStart
        i += 2
        val level = longLevel(i)
        val value = if (level >= 0) {
            long(level, "comment")
        } else {
            val from = i
            while (i < n && !isNewline(src[i])) i++
            src.substring(from, i)
        }
        val node = Comment(value, src.substring(start, i))
        node.range = SourceRange(start, i, startLine, startColumn, line, i - lineStart)
        comments.add(node)
    }

    /** The level of a long bracket opening at [at] (`[[` is 0, `[==[` is 2), or -1. */
    private fun longLevel(at: Int): Int {
        if (at >= n || src[at] != '[') return -1
        var j = at + 1
        while (j < n && src[j] == '=') j++
        return if (j < n && src[j] == '[') j - at - 1 else -1
    }

    /** Reads a long bracket at `i`; the text inside, without a first line break. */
    private fun long(level: Int, what: String): String {
        val openLine = line
        i += level + 2
        if (i < n && isNewline(src[i])) newline()
        val from = i
        while (i < n) {
            val c = src[i]
            if (c == ']' && closesLong(level)) {
                val content = src.substring(from, i)
                i += level + 2
                return content
            }
            if (isNewline(c)) newline() else i++
        }
        fail("unfinished long $what (starting at line $openLine) near '<eof>'")
    }

    private fun closesLong(level: Int): Boolean {
        for (k in 1..level) {
            if (i + k >= n || src[i + k] != '=') return false
        }
        return i + level + 1 < n && src[i + level + 1] == ']'
    }

    /** Lua reads every line break inside a long string as `\n`. */
    private fun normalizeNewlines(text: String): String {
        if (text.indexOf('\r') < 0) return text
        val out = StringBuilder(text.length)
        var k = 0
        while (k < text.length) {
            val c = text[k++]
            if (isNewline(c)) {
                if (k < text.length && isNewline(text[k]) && text[k] != c) k++
                out.append('\n')
            } else {
                out.append(c)
            }
        }
        return out.toString()
    }

    private fun number(): NumericLiteral {
        val start = i
        var exponent = "Ee"
        if (src[i] == '0' && i + 1 < n && (src[i + 1] == 'x' || src[i + 1] == 'X')) {
            i += 2
            exponent = "Pp"
        }
        while (i < n) {
            val c = src[i]
            if (exponent.indexOf(c) >= 0) {
                i++
                if (i < n && (src[i] == '+' || src[i] == '-')) i++
            } else if (isHex(c) || c == '.') {
                i++
            } else {
                break
            }
        }
        // Lua reads a letter stuck to a number as part of it, and refuses both.
        if (i < n && isNameChar(src[i])) i++
        val raw = src.substring(start, i)
        val value = LuaString.valueOf(raw).tonumber()
        if (value.isnil()) fail("malformed number near '$raw'")
        return NumericLiteral(value.todouble(), raw, value.isinttype())
    }

    /** Reads a quoted string at `i`; what it denotes. */
    private fun quoted(): String {
        val quote = src[i++]
        val bytes = ByteBuilder()
        var run = i
        while (true) {
            if (i >= n) fail("unfinished string near '<eof>'")
            val c = src[i]
            if (c == quote) {
                bytes.addText(src, run, i)
                i++
                break
            }
            if (isNewline(c)) fail("unfinished string near '${src.substring(run - 1, i)}'")
            if (c != '\\') {
                i++
                continue
            }
            bytes.addText(src, run, i)
            i++
            if (i >= n) fail("unfinished string near '<eof>'")
            when (val e = src[i]) {
                'n' -> { bytes.add(10); i++ }
                'a' -> { bytes.add(7); i++ }
                'b' -> { bytes.add(8); i++ }
                'f' -> { bytes.add(12); i++ }
                'r' -> { bytes.add(13); i++ }
                't' -> { bytes.add(9); i++ }
                'v' -> { bytes.add(11); i++ }
                '\\', '"', '\'' -> { bytes.add(e.code); i++ }
                '\n', '\r' -> { bytes.add(10); newline() }
                'x' -> {
                    i++
                    var value = 0
                    repeat(2) {
                        if (i >= n || !isHex(src[i])) fail("hexadecimal digit expected")
                        value = value * 16 + src[i].digitToInt(16)
                        i++
                    }
                    bytes.add(value)
                }
                'z' -> {
                    i++
                    while (i < n) {
                        val s = src[i]
                        if (isNewline(s)) newline()
                        else if (s == ' ' || s == '\t' || s == '' || s == '') i++
                        else break
                    }
                }
                'u' -> {
                    i++
                    if (i >= n || src[i] != '{') fail("missing '{' in \\u{xxxx}")
                    i++
                    var code = 0L
                    var digits = 0
                    while (i < n && isHex(src[i])) {
                        code = code * 16 + src[i].digitToInt(16)
                        if (code > 0x7FFFFFFFL) fail("UTF-8 value too large")
                        digits++
                        i++
                    }
                    if (digits == 0) fail("hexadecimal digit expected")
                    if (i >= n || src[i] != '}') fail("missing '}' in \\u{xxxx}")
                    i++
                    bytes.addUtf8(code)
                }
                else -> {
                    if (!isDigit(e)) fail("invalid escape sequence '\\$e'")
                    var value = 0
                    var digits = 0
                    while (digits < 3 && i < n && isDigit(src[i])) {
                        value = value * 10 + (src[i] - '0')
                        digits++
                        i++
                    }
                    if (value > 255) fail("decimal escape too large")
                    bytes.add(value)
                }
            }
            run = i
        }
        return bytes.decode()
    }
}

/** The bytes a string literal denotes, since escapes can write any of them. */
private class ByteBuilder {
    private var data = ByteArray(32)
    private var size = 0

    fun add(b: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = b.toByte()
    }

    fun addText(src: String, from: Int, to: Int) {
        if (from >= to) return
        for (b in src.substring(from, to).encodeToByteArray()) add(b.toInt())
    }

    /** Lua's `luaO_utf8esc`: UTF-8 extended to 31 bits, as `\u{...}` allows. */
    fun addUtf8(value: Long) {
        var x = value
        if (x < 0x80) {
            add(x.toInt())
            return
        }
        val buffer = IntArray(8)
        var k = 8
        var mfb = 0x3fL
        do {
            buffer[--k] = (0x80L or (x and 0x3f)).toInt()
            x = x shr 6
            mfb = mfb shr 1
        } while (x > mfb)
        buffer[--k] = ((mfb.inv() shl 1) or x).toInt() and 0xFF
        for (j in k until 8) add(buffer[j])
    }

    fun decode(): String = data.copyOf(size).decodeToString()
}

private class Parser(private val tokens: List<Token>, private val comments: List<Comment>) {
    private var p = 0

    private val tok: Token get() = tokens[p]

    /** The last token consumed: where a node being finished ends. */
    private val prev: Token get() = tokens[if (p > 0) p - 1 else 0]

    private fun peek(ahead: Int): Token = tokens[minOf(p + ahead, tokens.size - 1)]

    private fun next() {
        if (tok.kind != Kind.EOF) p++
    }

    private fun Token.isPunct(text: String) = kind == Kind.PUNCT && this.text == text

    private fun Token.isKeyword(text: String) = kind == Kind.KEYWORD && this.text == text

    private fun accept(text: String): Boolean {
        val t = tok
        if ((t.kind == Kind.PUNCT || t.kind == Kind.KEYWORD) && t.text == text) {
            next()
            return true
        }
        return false
    }

    private fun expect(text: String) {
        if (!accept(text)) fail("'$text' expected")
    }

    private fun fail(message: String): Nothing {
        val t = tok
        throw LuaSyntaxException("${t.line}: $message near '${t.text}'", t.line, t.start - t.lineStart)
    }

    private fun <T : LuaNode> T.from(start: Token, end: Token = prev): T {
        range = SourceRange(
            start.start,
            end.end,
            start.line,
            start.start - start.lineStart,
            end.lastLine,
            end.end - end.lastLineStart,
        )
        return this
    }

    fun chunk(): Chunk {
        val start = tok
        val body = block()
        if (tok.kind != Kind.EOF) fail("'<eof>' expected")
        val end = if (body.isEmpty()) tok else prev
        return Chunk(body, comments).from(start, end)
    }

    private fun blockEnds(): Boolean {
        val t = tok
        return t.kind == Kind.EOF ||
            (t.kind == Kind.KEYWORD && (t.text == "else" || t.text == "elseif" || t.text == "end" || t.text == "until"))
    }

    private fun block(): List<LuaStatement> {
        val body = ArrayList<LuaStatement>()
        while (!blockEnds()) {
            if (tok.isKeyword("return")) {
                body.add(returnStatement())
                break
            }
            val statement = statement()
            accept(";")
            if (statement != null) body.add(statement)
        }
        return body
    }

    private fun statement(): LuaStatement? {
        val start = tok
        if (accept("::")) {
            val label = identifier()
            expect("::")
            return LabelStatement(label).from(start)
        }
        if (accept(";")) return null
        if (start.kind == Kind.KEYWORD) {
            when (start.text) {
                "local" -> { next(); return localStatement(start) }
                "if" -> return ifStatement()
                "function" -> {
                    next()
                    return function(functionName(), isLocal = false, isGlobal = false, start = start)
                }
                "while" -> {
                    next()
                    val condition = expression()
                    expect("do")
                    val body = block()
                    expect("end")
                    return WhileStatement(condition, body).from(start)
                }
                "for" -> return forStatement()
                "repeat" -> {
                    next()
                    val body = block()
                    expect("until")
                    return RepeatStatement(expression(), body).from(start)
                }
                "break" -> { next(); return BreakStatement().from(start) }
                "do" -> {
                    next()
                    val body = block()
                    expect("end")
                    return DoStatement(body).from(start)
                }
                "goto" -> {
                    next()
                    return GotoStatement(identifier()).from(start)
                }
            }
        }
        if (start.kind == Kind.NAME && start.text == "global" && startsGlobal()) {
            next()
            return globalStatement(start)
        }
        return assignmentOrCall()
    }

    private fun startsGlobal(): Boolean {
        val after = peek(1)
        return after.kind == Kind.NAME || after.isKeyword("function") || after.isPunct("<") || after.isPunct("*")
    }

    /** `<name>` after a declared name, or before the names. */
    private fun attribute(): String? {
        if (!tok.isPunct("<") || peek(1).kind != Kind.NAME || !peek(2).isPunct(">")) return null
        val name = peek(1).text
        next()
        next()
        next()
        return name
    }

    private fun localStatement(start: Token): LuaStatement {
        if (accept("function")) return function(identifier(), isLocal = true, isGlobal = false, start = start)
        val default = attribute()
        val (variables, attributes) = attributedNames(default)
        val init = if (accept("=")) expressionList() else emptyList()
        return LocalStatement(variables, attributes, default, init).from(start)
    }

    private fun globalStatement(start: Token): LuaStatement {
        if (accept("function")) return function(identifier(), isLocal = false, isGlobal = true, start = start)
        val default = attribute()
        if (accept("*")) return GlobalStatement(emptyList(), emptyList(), default, emptyList(), true).from(start)
        val (variables, attributes) = attributedNames(default)
        val init = if (accept("=")) expressionList() else emptyList()
        return GlobalStatement(variables, attributes, default, init, false).from(start)
    }

    private fun attributedNames(default: String?): Pair<List<Identifier>, List<String?>> {
        val variables = ArrayList<Identifier>()
        val attributes = ArrayList<String?>()
        do {
            variables.add(identifier())
            attributes.add(attribute() ?: default)
        } while (accept(","))
        return variables to attributes
    }

    private fun returnStatement(): ReturnStatement {
        val start = tok
        next()
        val arguments = if (blockEnds() || tok.isPunct(";")) emptyList() else expressionList()
        accept(";")
        return ReturnStatement(arguments).from(start)
    }

    /*
     * Clauses follow luaparse: the first starts where the statement does,
     * each one ends with the last token of its block (or its `then`), and an
     * `elseif`/`else` clause starts at its keyword.
     */
    private fun ifStatement(): IfStatement {
        val start = tok
        next()
        val clauses = ArrayList<IfClause>()
        var condition = expression()
        expect("then")
        clauses.add(IfClause(IfClause.Kind.IF, condition, block()).from(start))
        while (tok.isKeyword("elseif")) {
            val clauseStart = tok
            next()
            condition = expression()
            expect("then")
            clauses.add(IfClause(IfClause.Kind.ELSEIF, condition, block()).from(clauseStart))
        }
        if (tok.isKeyword("else")) {
            val clauseStart = tok
            next()
            clauses.add(IfClause(IfClause.Kind.ELSE, null, block()).from(clauseStart))
        }
        expect("end")
        return IfStatement(clauses).from(start)
    }

    private fun forStatement(): LuaStatement {
        val start = tok
        next()
        val first = identifier()
        if (accept("=")) {
            val from = expression()
            expect(",")
            val to = expression()
            val step = if (accept(",")) expression() else null
            expect("do")
            val body = block()
            expect("end")
            return ForNumericStatement(first, from, to, step, body).from(start)
        }
        val variables = arrayListOf(first)
        while (accept(",")) variables.add(identifier())
        expect("in")
        val iterators = expressionList()
        expect("do")
        val body = block()
        expect("end")
        return ForGenericStatement(variables, iterators, body).from(start)
    }

    private fun assignmentOrCall(): LuaStatement {
        val start = tok
        val targets = ArrayList<LuaExpression>()
        var assignable: Boolean?
        while (true) {
            val mark = tok
            var base: LuaExpression
            if (mark.kind == Kind.NAME) {
                base = identifier()
                assignable = true
            } else if (accept("(")) {
                base = expression()
                expect(")")
                assignable = false
            } else {
                fail("unexpected symbol")
            }
            while (true) {
                val t = tok
                assignable = when {
                    t.isPunct(".") || t.isPunct("[") -> true
                    t.isPunct(":") || t.isPunct("(") || t.isPunct("{") || t.kind == Kind.STRING -> null
                    else -> break
                }
                base = suffix(base, mark) ?: break
            }
            targets.add(base)
            if (!tok.isPunct(",")) break
            if (assignable != true) fail("syntax error")
            next()
        }
        if (targets.size == 1 && assignable == null) return CallStatement(targets[0]).from(start)
        if (assignable != true) fail("syntax error")
        expect("=")
        return AssignmentStatement(targets, expressionList()).from(start)
    }

    private fun identifier(): Identifier {
        val t = tok
        if (t.kind != Kind.NAME) fail("<name> expected")
        next()
        return Identifier(t.text).from(t)
    }

    private fun functionName(): LuaExpression {
        val start = tok
        var base: LuaExpression = identifier()
        while (accept(".")) base = MemberExpression(base, ".", identifier()).from(start)
        if (accept(":")) base = MemberExpression(base, ":", identifier()).from(start)
        return base
    }

    private fun function(name: LuaExpression?, isLocal: Boolean, isGlobal: Boolean, start: Token): FunctionDeclaration {
        expect("(")
        val parameters = ArrayList<LuaExpression>()
        if (!accept(")")) {
            while (true) {
                val t = tok
                if (t.kind == Kind.NAME) {
                    parameters.add(identifier())
                    if (accept(",")) continue
                } else if (t.kind == Kind.VARARG) {
                    next()
                    val named = if (tok.kind == Kind.NAME) identifier() else null
                    parameters.add(VarargLiteral(t.text, named).from(t))
                } else {
                    fail("<name> expected")
                }
                expect(")")
                break
            }
        }
        val body = block()
        expect("end")
        return FunctionDeclaration(name, isLocal, isGlobal, parameters, body).from(start)
    }

    private fun expressionList(): List<LuaExpression> {
        val list = arrayListOf(expression())
        while (accept(",")) list.add(expression())
        return list
    }

    private fun expression(limit: Int = 0): LuaExpression {
        val start = tok
        var left: LuaExpression = if (isUnary(start)) {
            next()
            UnaryExpression(start.text, expression(UNARY_PRIORITY)).from(start)
        } else {
            simpleExpression()
        }
        while (true) {
            val operator = binaryOperator(tok) ?: break
            val priority = PRIORITY.getValue(operator)
            if (priority[0] <= limit) break
            next()
            val right = expression(priority[1])
            left = if (operator == "and" || operator == "or") {
                LogicalExpression(operator, left, right).from(start)
            } else {
                BinaryExpression(operator, left, right).from(start)
            }
        }
        return left
    }

    private fun isUnary(t: Token) =
        t.isKeyword("not") || t.isPunct("-") || t.isPunct("#") || t.isPunct("~")

    private fun binaryOperator(t: Token): String? = when {
        t.kind == Kind.PUNCT && t.text in PRIORITY -> t.text
        t.isKeyword("and") || t.isKeyword("or") -> t.text
        else -> null
    }

    private fun simpleExpression(): LuaExpression {
        val t = tok
        return when (t.kind) {
            Kind.NUMBER, Kind.STRING -> { next(); (t.node as LuaNode).from(t) as LuaExpression }
            Kind.NIL -> { next(); NilLiteral(t.text).from(t) }
            Kind.TRUE, Kind.FALSE -> { next(); BooleanLiteral(t.kind == Kind.TRUE, t.text).from(t) }
            Kind.VARARG -> { next(); VarargLiteral(t.text, null).from(t) }
            else -> when {
                t.isKeyword("function") -> {
                    next()
                    function(null, isLocal = false, isGlobal = false, start = t)
                }
                t.isPunct("{") -> table()
                else -> prefixExpression()
            }
        }
    }

    private fun prefixExpression(): LuaExpression {
        val mark = tok
        var base: LuaExpression = when {
            mark.kind == Kind.NAME -> identifier()
            accept("(") -> expression().also { expect(")") }
            else -> fail("unexpected symbol")
        }
        while (true) base = suffix(base, mark) ?: break
        return base
    }

    private fun suffix(base: LuaExpression, mark: Token): LuaExpression? {
        val t = tok
        return when {
            t.isPunct("[") -> {
                next()
                val index = expression()
                expect("]")
                IndexExpression(base, index).from(mark)
            }
            t.isPunct(".") -> {
                next()
                MemberExpression(base, ".", identifier()).from(mark)
            }
            t.isPunct(":") -> {
                next()
                val method = MemberExpression(base, ":", identifier()).from(mark)
                call(method, mark)
            }
            t.isPunct("(") || t.isPunct("{") || t.kind == Kind.STRING -> call(base, mark)
            else -> null
        }
    }

    private fun call(base: LuaExpression, mark: Token): LuaExpression {
        val t = tok
        return when {
            t.isPunct("(") -> {
                next()
                val arguments = if (tok.isPunct(")")) emptyList() else expressionList()
                expect(")")
                CallExpression(base, arguments).from(mark)
            }
            t.isPunct("{") -> TableCallExpression(base, table()).from(mark)
            t.kind == Kind.STRING -> {
                next()
                StringCallExpression(base, (t.node as StringLiteral).from(t)).from(mark)
            }
            else -> fail("function arguments expected")
        }
    }

    private fun table(): TableConstructorExpression {
        val start = tok
        expect("{")
        val fields = ArrayList<LuaNode>()
        while (!tok.isPunct("}")) {
            val fieldStart = tok
            fields.add(
                when {
                    accept("[") -> {
                        val key = expression()
                        expect("]")
                        expect("=")
                        TableKey(key, expression()).from(fieldStart)
                    }
                    fieldStart.kind == Kind.NAME && peek(1).isPunct("=") -> {
                        val key = identifier()
                        next()
                        TableKeyString(key, expression()).from(fieldStart)
                    }
                    else -> TableValue(expression()).from(fieldStart)
                },
            )
            if (!accept(",") && !accept(";")) break
        }
        expect("}")
        return TableConstructorExpression(fields).from(start)
    }

    private companion object {
        /** Left and right priority of each binary operator, from `lparser.c`. */
        val PRIORITY: Map<String, IntArray> = mapOf(
            "+" to intArrayOf(10, 10), "-" to intArrayOf(10, 10),
            "*" to intArrayOf(11, 11), "%" to intArrayOf(11, 11),
            "^" to intArrayOf(14, 13),
            "/" to intArrayOf(11, 11), "//" to intArrayOf(11, 11),
            "&" to intArrayOf(6, 6), "|" to intArrayOf(4, 4), "~" to intArrayOf(5, 5),
            "<<" to intArrayOf(7, 7), ">>" to intArrayOf(7, 7),
            ".." to intArrayOf(9, 8),
            "==" to intArrayOf(3, 3), "<" to intArrayOf(3, 3), "<=" to intArrayOf(3, 3),
            "~=" to intArrayOf(3, 3), ">" to intArrayOf(3, 3), ">=" to intArrayOf(3, 3),
            "and" to intArrayOf(2, 2), "or" to intArrayOf(1, 1),
        )

        const val UNARY_PRIORITY = 12
    }
}
