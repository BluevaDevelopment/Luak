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
package net.blueva.luak.web

import net.blueva.luak.syntax.AssignmentStatement
import net.blueva.luak.syntax.BinaryExpression
import net.blueva.luak.syntax.BooleanLiteral
import net.blueva.luak.syntax.BreakStatement
import net.blueva.luak.syntax.CallExpression
import net.blueva.luak.syntax.CallStatement
import net.blueva.luak.syntax.Chunk
import net.blueva.luak.syntax.Comment
import net.blueva.luak.syntax.DoStatement
import net.blueva.luak.syntax.ForGenericStatement
import net.blueva.luak.syntax.ForNumericStatement
import net.blueva.luak.syntax.FunctionDeclaration
import net.blueva.luak.syntax.GlobalStatement
import net.blueva.luak.syntax.GotoStatement
import net.blueva.luak.syntax.Identifier
import net.blueva.luak.syntax.IfClause
import net.blueva.luak.syntax.IfStatement
import net.blueva.luak.syntax.IndexExpression
import net.blueva.luak.syntax.LabelStatement
import net.blueva.luak.syntax.LocalStatement
import net.blueva.luak.syntax.LogicalExpression
import net.blueva.luak.syntax.LuaNode
import net.blueva.luak.syntax.MemberExpression
import net.blueva.luak.syntax.NilLiteral
import net.blueva.luak.syntax.NumericLiteral
import net.blueva.luak.syntax.RepeatStatement
import net.blueva.luak.syntax.ReturnStatement
import net.blueva.luak.syntax.StringCallExpression
import net.blueva.luak.syntax.StringLiteral
import net.blueva.luak.syntax.TableCallExpression
import net.blueva.luak.syntax.TableConstructorExpression
import net.blueva.luak.syntax.TableKey
import net.blueva.luak.syntax.TableKeyString
import net.blueva.luak.syntax.TableValue
import net.blueva.luak.syntax.UnaryExpression
import net.blueva.luak.syntax.VarargLiteral
import net.blueva.luak.syntax.WhileStatement

/*
 * The tree as plain JavaScript objects, in luaparse's shape with its
 * `comments`, `locations` and `ranges` options on: `type`, the node's
 * fields, `loc: {start: {line, column}, end: {line, column}}` and
 * `range: [start, end]`. What luaparse has no word for is extra fields on
 * the nodes it does have, plus `GlobalStatement`.
 */

private fun obj(): dynamic = js("({})")

private fun list(items: List<*>): dynamic {
    val array: dynamic = js("[]")
    for (item in items) array.push(toJs(item))
    return array
}

internal fun toJs(value: Any?): dynamic {
    if (value == null) return null
    val node = value as LuaNode
    val o = obj()
    o.type = typeOf(node)
    when (node) {
        is Chunk -> {
            o.body = list(node.body)
            o.comments = list(node.comments)
        }
        is Comment -> {
            o.value = node.value
            o.raw = node.raw
        }
        is LabelStatement -> o.label = toJs(node.label)
        is BreakStatement -> {}
        is GotoStatement -> o.label = toJs(node.label)
        is ReturnStatement -> o.arguments = list(node.arguments)
        is IfStatement -> o.clauses = list(node.clauses)
        is IfClause -> {
            if (node.kind != IfClause.Kind.ELSE) o.condition = toJs(node.condition)
            o.body = list(node.body)
        }
        is WhileStatement -> {
            o.condition = toJs(node.condition)
            o.body = list(node.body)
        }
        is DoStatement -> o.body = list(node.body)
        is RepeatStatement -> {
            o.condition = toJs(node.condition)
            o.body = list(node.body)
        }
        is LocalStatement -> {
            o.variables = list(node.variables)
            o.init = list(node.init)
            o.attributes = strings(node.attributes)
            o.attribute = node.attribute
        }
        is GlobalStatement -> {
            o.variables = list(node.variables)
            o.init = list(node.init)
            o.attributes = strings(node.attributes)
            o.attribute = node.attribute
            o.wildcard = node.wildcard
        }
        is AssignmentStatement -> {
            o.variables = list(node.variables)
            o.init = list(node.init)
        }
        is CallStatement -> o.expression = toJs(node.expression)
        is FunctionDeclaration -> {
            o.identifier = toJs(node.identifier)
            o.isLocal = node.isLocal
            o.isGlobal = node.isGlobal
            o.parameters = list(node.parameters)
            o.body = list(node.body)
        }
        is ForNumericStatement -> {
            o.variable = toJs(node.variable)
            o.start = toJs(node.start)
            o.end = toJs(node.end)
            o.step = toJs(node.step)
            o.body = list(node.body)
        }
        is ForGenericStatement -> {
            o.variables = list(node.variables)
            o.iterators = list(node.iterators)
            o.body = list(node.body)
        }
        is Identifier -> o.name = node.name
        is StringLiteral -> {
            o.value = node.value
            o.raw = node.raw
        }
        is NumericLiteral -> {
            o.value = node.value
            o.raw = node.raw
            o.isInteger = node.isInteger
        }
        is BooleanLiteral -> {
            o.value = node.value
            o.raw = node.raw
        }
        is NilLiteral -> {
            o.value = null
            o.raw = node.raw
        }
        is VarargLiteral -> {
            o.value = "..."
            o.raw = node.raw
            o.name = toJs(node.name)
        }
        is TableConstructorExpression -> o.fields = list(node.fields)
        is TableKey -> {
            o.key = toJs(node.key)
            o.value = toJs(node.value)
        }
        is TableKeyString -> {
            o.key = toJs(node.key)
            o.value = toJs(node.value)
        }
        is TableValue -> o.value = toJs(node.value)
        is BinaryExpression -> {
            o.operator = node.operator
            o.left = toJs(node.left)
            o.right = toJs(node.right)
        }
        is LogicalExpression -> {
            o.operator = node.operator
            o.left = toJs(node.left)
            o.right = toJs(node.right)
        }
        is UnaryExpression -> {
            o.operator = node.operator
            o.argument = toJs(node.argument)
        }
        is MemberExpression -> {
            o.indexer = node.indexer
            o.identifier = toJs(node.identifier)
            o.base = toJs(node.base)
        }
        is IndexExpression -> {
            o.base = toJs(node.base)
            o.index = toJs(node.index)
        }
        is CallExpression -> {
            o.base = toJs(node.base)
            o.arguments = list(node.arguments)
        }
        is TableCallExpression -> {
            o.base = toJs(node.base)
            o.arguments = toJs(node.arguments)
        }
        is StringCallExpression -> {
            o.base = toJs(node.base)
            o.argument = toJs(node.argument)
        }
    }
    val r = node.range
    val loc = obj()
    loc.start = obj()
    loc.start.line = r.startLine
    loc.start.column = r.startColumn
    loc.end = obj()
    loc.end.line = r.endLine
    loc.end.column = r.endColumn
    o.loc = loc
    val range: dynamic = js("[]")
    range.push(r.start)
    range.push(r.end)
    o.range = range
    return o
}

private fun strings(items: List<String?>): dynamic {
    val array: dynamic = js("[]")
    for (item in items) array.push(item)
    return array
}

private fun typeOf(node: LuaNode): String = when (node) {
    is Chunk -> "Chunk"
    is Comment -> "Comment"
    is LabelStatement -> "LabelStatement"
    is BreakStatement -> "BreakStatement"
    is GotoStatement -> "GotoStatement"
    is ReturnStatement -> "ReturnStatement"
    is IfStatement -> "IfStatement"
    is IfClause -> when (node.kind) {
        IfClause.Kind.IF -> "IfClause"
        IfClause.Kind.ELSEIF -> "ElseifClause"
        IfClause.Kind.ELSE -> "ElseClause"
    }
    is WhileStatement -> "WhileStatement"
    is DoStatement -> "DoStatement"
    is RepeatStatement -> "RepeatStatement"
    is LocalStatement -> "LocalStatement"
    is GlobalStatement -> "GlobalStatement"
    is AssignmentStatement -> "AssignmentStatement"
    is CallStatement -> "CallStatement"
    is FunctionDeclaration -> "FunctionDeclaration"
    is ForNumericStatement -> "ForNumericStatement"
    is ForGenericStatement -> "ForGenericStatement"
    is Identifier -> "Identifier"
    is StringLiteral -> "StringLiteral"
    is NumericLiteral -> "NumericLiteral"
    is BooleanLiteral -> "BooleanLiteral"
    is NilLiteral -> "NilLiteral"
    is VarargLiteral -> "VarargLiteral"
    is TableConstructorExpression -> "TableConstructorExpression"
    is TableKey -> "TableKey"
    is TableKeyString -> "TableKeyString"
    is TableValue -> "TableValue"
    is BinaryExpression -> "BinaryExpression"
    is LogicalExpression -> "LogicalExpression"
    is UnaryExpression -> "UnaryExpression"
    is MemberExpression -> "MemberExpression"
    is IndexExpression -> "IndexExpression"
    is CallExpression -> "CallExpression"
    is TableCallExpression -> "TableCallExpression"
    is StringCallExpression -> "StringCallExpression"
}
