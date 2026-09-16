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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.blueva.luak.Globals
import net.blueva.luak.lib.LuaPlatform

/**
 * The syntax tree. Its shapes and ranges follow luaparse, which the browser
 * build was checked against over the reference test suite; these pin the
 * parts luaparse has no answer for and the conventions a tool relies on.
 */
class LuaParserTest {
    private fun statement(source: String): LuaStatement = LuaParser.parse(source).body.single()

    private fun text(source: String, node: LuaNode) = source.substring(node.range.start, node.range.end)

    @Test
    fun globalDeclarationsAreTheirOwnStatement() {
        val names = assertIs<GlobalStatement>(statement("global score, lives <const> = 0, 3"))
        assertEquals(listOf("score", "lives"), names.variables.map { it.name })
        assertEquals(listOf(null, "const"), names.attributes)
        assertEquals(2, names.init.size)
        assertFalse(names.wildcard)

        val all = assertIs<GlobalStatement>(statement("global <const> *"))
        assertTrue(all.wildcard)
        assertEquals("const", all.attribute)
        assertTrue(all.variables.isEmpty())

        val function = assertIs<FunctionDeclaration>(statement("global function f() end"))
        assertTrue(function.isGlobal)
        assertFalse(function.isLocal)
        assertEquals(0, function.range.start)
    }

    @Test
    fun globalIsAnOrdinaryNameAnywhereElse() {
        val call = assertIs<CallStatement>(statement("global(1)"))
        assertEquals("global", assertIs<Identifier>(assertIs<CallExpression>(call.expression).base).name)
        assertIs<AssignmentStatement>(statement("global = 1"))
        assertIs<AssignmentStatement>(statement("global.x = 1"))
        val local = assertIs<LocalStatement>(statement("local global = 1"))
        assertEquals("global", local.variables.single().name)
    }

    @Test
    fun anAttributeBeforeTheNamesIsEachNamesDefault() {
        val local = assertIs<LocalStatement>(statement("local <const> a, b <close> = 1, 2"))
        assertEquals("const", local.attribute)
        assertEquals(listOf("const", "close"), local.attributes)
        val plain = assertIs<LocalStatement>(statement("local a, b"))
        assertEquals(listOf(null, null), plain.attributes)
        assertTrue(plain.init.isEmpty())
    }

    @Test
    fun aVarargParameterCanBeNamed() {
        val source = "function f(a, ...rest) end"
        val function = assertIs<FunctionDeclaration>(statement(source))
        val vararg = assertIs<VarargLiteral>(function.parameters.last())
        assertEquals("rest", vararg.name?.name)
        assertEquals("...rest", text(source, vararg))
    }

    @Test
    fun stringsAreDecodedAsLuaReadsThem() {
        fun value(literal: String) =
            assertIs<StringLiteral>(assertIs<LocalStatement>(statement("local s = $literal")).init.single()).value
        assertEquals("a\nb", value("'a\\nb'"))
        assertEquals("é", value("\"\\xC3\\xA9\""))
        assertEquals("é", value("'\\195\\169'"))
        assertEquals("😀", value("'\\u{1F600}'"))
        assertEquals("😀 ─", value("'😀 ─'"))
        assertEquals("ab", value("'a\\z\n   b'"))
        assertEquals("line\n", value("[[\nline\r\n]]"))
        assertEquals("]]", value("[==[]]]==]"))
    }

    @Test
    fun numbersKeepTheirSubtype() {
        fun number(literal: String) =
            assertIs<NumericLiteral>(assertIs<LocalStatement>(statement("local n = $literal")).init.single())
        assertTrue(number("10").isInteger)
        assertFalse(number("10.0").isInteger)
        assertEquals(255.0, number("0xff").value)
        assertEquals(-1.0, number("0xffffffffffffffff").value)
        assertEquals(0.5, number("0x.8").value)
        assertEquals(1e10, number("1e10").value)
    }

    @Test
    fun rangesCoverTheSourceTheNodeWasReadFrom() {
        val source = "local x = (a + b) * c; print(x)\n"
        val chunk = LuaParser.parse(source)
        val local = assertIs<LocalStatement>(chunk.body[0])
        assertEquals("local x = (a + b) * c", text(source, local))
        val product = assertIs<BinaryExpression>(local.init.single())
        assertEquals("(a + b) * c", text(source, product))
        // A parenthesised expression is the node inside the parentheses.
        assertEquals("a + b", text(source, product.left as LuaNode))
        val call = assertIs<CallStatement>(chunk.body[1])
        assertEquals("print(x)", text(source, call))
        assertEquals(1, call.range.startLine)
        assertEquals(23, call.range.startColumn)
    }

    @Test
    fun linesAndColumnsFollowEveryKindOfLineBreak() {
        val source = "a = 1\r\nb = [[x\ny]]\rc = 'é😀'\n\rd = 2"
        val chunk = LuaParser.parse(source)
        val lines = chunk.body.map { it as LuaNode }.map { it.range.startLine to it.range.endLine }
        assertEquals(listOf(1 to 1, 2 to 3, 4 to 4, 5 to 5), lines)
        val c = assertIs<AssignmentStatement>(chunk.body[2])
        // Columns count the string's own units, as the range does.
        assertEquals(source.indexOf("'é😀'") + "'é😀'".length - source.indexOf("c ="), c.range.endColumn)
    }

    @Test
    fun ifClausesStartAtTheirKeyword() {
        val source = "if a then x() elseif b then y() else z() end"
        val statement = assertIs<IfStatement>(statement(source))
        assertEquals(listOf("if a then x()", "elseif b then y()", "else z()"), statement.clauses.map { text(source, it) })
        assertNull(statement.clauses.last().condition)
    }

    @Test
    fun commentsAreKeptWithTheirText() {
        val source = "-- one\nx = 1 --[==[ two\n]==]\n--[not long"
        val comments = LuaParser.parse(source).comments
        assertEquals(listOf(" one", " two\n", "[not long"), comments.map { it.value })
        assertEquals(listOf("-- one", "--[==[ two\n]==]", "--[not long"), comments.map { text(source, it) })
        assertEquals(2, comments[1].range.startLine)
        assertEquals(3, comments[1].range.endLine)
    }

    @Test
    fun operatorsBindAsTheCompilerBindsThem() {
        fun shape(e: LuaExpression): String = when (e) {
            is BinaryExpression -> "(${shape(e.left)} ${e.operator} ${shape(e.right)})"
            is LogicalExpression -> "(${shape(e.left)} ${e.operator} ${shape(e.right)})"
            is UnaryExpression -> "(${e.operator}${shape(e.argument)})"
            is Identifier -> e.name
            is NumericLiteral -> e.raw
            else -> "?"
        }
        fun parsed(expression: String) =
            shape(assertIs<ReturnStatement>(statement("return $expression")).arguments.single())
        assertEquals("(-(x ^ 2))", parsed("-x ^ 2"))
        assertEquals("(a .. (b .. c))", parsed("a .. b .. c"))
        assertEquals("(2 ^ (3 ^ 2))", parsed("2 ^ 3 ^ 2"))
        assertEquals("((a + (b * c)) == d)", parsed("a + b * c == d"))
        assertEquals("(a or (b and (notc)))", parsed("a or b and not c"))
        assertEquals("((a | (b ~ (c & (d << 1)))) < e)", parsed("a | b ~ c & d << 1 < e"))
        assertEquals("((~a) // 2)", parsed("~a // 2"))
    }

    @Test
    fun whatTheCompilerRefusesTheParserRefusesToo() {
        val compiler: Globals = LuaPlatform.standardGlobals()
        val broken = listOf("x = = 1", "if x then", "local 1 = 2", "f() = 1", "a, f() = 1, 2", "return return", "x = 'open")
        for (source in broken) {
            assertFailsWith<Exception>(source) { compiler.load(source, "x") }
            assertFailsWith<LuaSyntaxException>(source) { LuaParser.parse(source) }
        }
    }
}
