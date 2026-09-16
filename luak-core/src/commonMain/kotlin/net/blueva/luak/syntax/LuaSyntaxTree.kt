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

/*
 * A syntax tree for Lua 5.5 source, for tools that need to read a program
 * rather than run it: editors, linters, converters.
 *
 * The compiler in `net.blueva.luak.compiler` goes straight from tokens to
 * bytecode and never builds one, so this is a separate reader of the same
 * grammar. The node names and shapes follow luaparse, the parser most Lua
 * tooling on the web already understands, with the 5.4/5.5 additions it
 * lacks: attributes on declarations, `global` declarations and named
 * varargs.
 *
 * Positions are in the source string's own units (UTF-16 code units on the
 * JVM and in JavaScript), so `source.substring(start, end)` is the node's
 * text. A node starts at its first token and ends at the end of its last
 * one, with luaparse's two conventions kept: a parenthesised expression is
 * the node inside the parentheses, and a `;` after a statement is not part
 * of it (except after `return`, whose grammar includes it).
 */

/** Where a node sits. Lines count from 1, columns from 0. */
public class SourceRange(
    public val start: Int,
    public val end: Int,
    public val startLine: Int,
    public val startColumn: Int,
    public val endLine: Int,
    public val endColumn: Int,
) {
    internal companion object {
        val NONE: SourceRange = SourceRange(0, 0, 0, 0, 0, 0)
    }
}

public sealed class LuaNode {
    public var range: SourceRange = SourceRange.NONE
        internal set
}

public sealed interface LuaStatement

public sealed interface LuaExpression

public class Chunk(
    public val body: List<LuaStatement>,
    public val comments: List<Comment>,
) : LuaNode()

public class Comment(
    /** The text after `--`, or inside the brackets of a long comment. */
    public val value: String,
    public val raw: String,
) : LuaNode()

// Statements

public class LabelStatement(public val label: Identifier) : LuaNode(), LuaStatement

public class BreakStatement : LuaNode(), LuaStatement

public class GotoStatement(public val label: Identifier) : LuaNode(), LuaStatement

public class ReturnStatement(public val arguments: List<LuaExpression>) : LuaNode(), LuaStatement

public class IfStatement(public val clauses: List<IfClause>) : LuaNode(), LuaStatement

public class IfClause(
    public val kind: Kind,
    /** Null for `else`. */
    public val condition: LuaExpression?,
    public val body: List<LuaStatement>,
) : LuaNode() {
    public enum class Kind { IF, ELSEIF, ELSE }
}

public class WhileStatement(
    public val condition: LuaExpression,
    public val body: List<LuaStatement>,
) : LuaNode(), LuaStatement

public class DoStatement(public val body: List<LuaStatement>) : LuaNode(), LuaStatement

public class RepeatStatement(
    public val condition: LuaExpression,
    public val body: List<LuaStatement>,
) : LuaNode(), LuaStatement

/**
 * `local [<attrib>] name [<attrib>] {, name [<attrib>]} [= explist]`.
 *
 * [attributes] runs parallel to [variables]: each name's own attribute, or
 * the one written before the names ([attribute]) when it has none.
 */
public class LocalStatement(
    public val variables: List<Identifier>,
    public val attributes: List<String?>,
    public val attribute: String?,
    public val init: List<LuaExpression>,
) : LuaNode(), LuaStatement

/**
 * A 5.5 `global` declaration: the same shape as [LocalStatement], or
 * `global [<attrib>] *` with [wildcard] set and no variables.
 */
public class GlobalStatement(
    public val variables: List<Identifier>,
    public val attributes: List<String?>,
    public val attribute: String?,
    public val init: List<LuaExpression>,
    public val wildcard: Boolean,
) : LuaNode(), LuaStatement

public class AssignmentStatement(
    public val variables: List<LuaExpression>,
    public val init: List<LuaExpression>,
) : LuaNode(), LuaStatement

public class CallStatement(public val expression: LuaExpression) : LuaNode(), LuaStatement

/**
 * A function: a statement when it has a name (`function a.b:c()`,
 * `local function f()`, `global function f()`), an expression when it has
 * none. [parameters] holds [Identifier]s and, last, a [VarargLiteral].
 */
public class FunctionDeclaration(
    public val identifier: LuaExpression?,
    public val isLocal: Boolean,
    public val isGlobal: Boolean,
    public val parameters: List<LuaExpression>,
    public val body: List<LuaStatement>,
) : LuaNode(), LuaStatement, LuaExpression

public class ForNumericStatement(
    public val variable: Identifier,
    public val start: LuaExpression,
    public val end: LuaExpression,
    public val step: LuaExpression?,
    public val body: List<LuaStatement>,
) : LuaNode(), LuaStatement

public class ForGenericStatement(
    public val variables: List<Identifier>,
    public val iterators: List<LuaExpression>,
    public val body: List<LuaStatement>,
) : LuaNode(), LuaStatement

// Expressions

public class Identifier(public val name: String) : LuaNode(), LuaExpression

/** [value] is the string the literal denotes, its bytes read as UTF-8. */
public class StringLiteral(public val value: String, public val raw: String) : LuaNode(), LuaExpression

public class NumericLiteral(
    public val value: Double,
    public val raw: String,
    /** Whether Lua reads it as an integer rather than a float. */
    public val isInteger: Boolean,
) : LuaNode(), LuaExpression

public class BooleanLiteral(public val value: Boolean, public val raw: String) : LuaNode(), LuaExpression

public class NilLiteral(public val raw: String) : LuaNode(), LuaExpression

/**
 * `...`. As a parameter it may be named (`function f(...args)`, 5.5), in
 * which case [name] is set and the range covers the name too.
 */
public class VarargLiteral(public val raw: String, public val name: Identifier?) : LuaNode(), LuaExpression

public class TableConstructorExpression(public val fields: List<LuaNode>) : LuaNode(), LuaExpression

/** `[key] = value` */
public class TableKey(public val key: LuaExpression, public val value: LuaExpression) : LuaNode()

/** `name = value` */
public class TableKeyString(public val key: Identifier, public val value: LuaExpression) : LuaNode()

/** A positional field. */
public class TableValue(public val value: LuaExpression) : LuaNode()

public class BinaryExpression(
    public val operator: String,
    public val left: LuaExpression,
    public val right: LuaExpression,
) : LuaNode(), LuaExpression

/** `and` / `or`. */
public class LogicalExpression(
    public val operator: String,
    public val left: LuaExpression,
    public val right: LuaExpression,
) : LuaNode(), LuaExpression

public class UnaryExpression(public val operator: String, public val argument: LuaExpression) : LuaNode(), LuaExpression

/** `base.identifier`, or `base:identifier` as the callee of a method call. */
public class MemberExpression(
    public val base: LuaExpression,
    public val indexer: String,
    public val identifier: Identifier,
) : LuaNode(), LuaExpression

public class IndexExpression(public val base: LuaExpression, public val index: LuaExpression) : LuaNode(), LuaExpression

public class CallExpression(public val base: LuaExpression, public val arguments: List<LuaExpression>) : LuaNode(), LuaExpression

/** `f{...}` */
public class TableCallExpression(
    public val base: LuaExpression,
    public val arguments: TableConstructorExpression,
) : LuaNode(), LuaExpression

/** `f"..."` */
public class StringCallExpression(public val base: LuaExpression, public val argument: StringLiteral) : LuaNode(), LuaExpression

public class LuaSyntaxException(
    message: String,
    public val line: Int,
    public val column: Int,
) : RuntimeException(message)
