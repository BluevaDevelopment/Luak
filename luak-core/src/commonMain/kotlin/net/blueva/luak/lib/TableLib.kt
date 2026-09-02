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
import net.blueva.luak.LuaString
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs

/**
 * Subclass of [LibFunction] which implements the lua standard `table`
 * library.
 * 
 * 
 * 
 * Typically, this library is included as part of a call to
 * [net.blueva.luak.lib.LuaPlatform.standardGlobals]
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * println(globals.get("table").get("length").call(LuaValue.tableOf()))
 * ```
 * 
 * 
 * To instantiate and use it directly,
 * link it into your globals table via [LuaValue.load] using code such as:
 * ```kotlin
 * val globals = Globals()
 * globals.load(BaseLib())
 * globals.load(PackageLib())
 * globals.load(TableLib())
 * println(globals.get("table").get("length").call(LuaValue.tableOf()))
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
 * @see [Lua 5.2 Table Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.5)
 */
class TableLib : TwoArgFunction() {
    /** Perform one-time initialization on the library by creating a table
     * containing the library functions, adding that table to the supplied environment,
     * adding the table to package.loaded, and returning table as the return value.
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env the environment to load into, typically a Globals instance.
     */
    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        val table: LuaTable = LuaTable()
        table.set("concat", net.blueva.luak.lib.TableLib.concat())
        table.set("create", net.blueva.luak.lib.TableLib.create())
        table.set("insert", net.blueva.luak.lib.TableLib.insert())
        table.set("move", net.blueva.luak.lib.TableLib.move())
        table.set("pack", net.blueva.luak.lib.TableLib.pack())
        table.set("remove", net.blueva.luak.lib.TableLib.remove())
        table.set("sort", net.blueva.luak.lib.TableLib.sort(env as? net.blueva.luak.Globals))
        table.set("unpack", net.blueva.luak.lib.TableLib.unpack())
        env!!.set("table", table)
        if (!env!!.get("package")!!.isnil()) env!!.get("package")!!.get("loaded")!!.set("table", table)
        return NIL
    }

    // "concat" (table [, sep [, i [, j]]]) -> string
    /**
     * `table.concat (list [, sep [, i [, j]]])`.
     *
     * Written against the argument list rather than against fixed arities, so
     * a bad index is reported with its position: "bad argument #3 to
     * 'table.concat'" rather than a message that says only what was wrong.
     * Anything indexable will do, as upstream allows, and an element that is
     * neither a string nor a number names its own index.
     */
    internal class concat : VarArgFunction() {
        /** Appends `list[index]`, refusing anything that is not a string. */
        private fun addfield(out: Buffer, list: LuaValue, index: Long) {
            val element: LuaValue = list.get(LuaValue.valueOf(index))
            if (!element.isstring()) {
                LuaValue.error(
                    "invalid value (" + element.typename() +
                        ") at index " + index + " in table for 'concat'",
                )
            }
            out.append(element.strvalue()!!)
        }

        override fun invoke(args: Varargs): Varargs {
            val list: LuaValue = checkindexable(args)
            val separator: LuaString = if (args.isnoneornil(2)) EMPTYSTRING!! else args.checkstring(2)
            val first: Long = args.optlong(3, 1L)
            val last: Long = if (args.isnoneornil(4)) list.length().toLong() else args.checklong(4)
            val out: Buffer = Buffer()
            var index: Long = first
            // The last element is added outside the loop, so the counter never
            // has to step past it: with a range ending at math.maxinteger,
            // one more increment would wrap around and read the table again.
            while (index < last) {
                addfield(out, list, index)
                out.append(separator)
                index++
            }
            if (index == last) addfield(out, list, index)
            return out.tostring()
        }
    }

    // "insert" (table, [pos,] value)
    /**
     * `table.create (nseq [, nrec])`, from Lua 5.5.
     *
     * Answers an empty table sized in advance for `nseq` entries in its array
     * part and `nrec` in its hash part. The sizes are a hint about what is
     * about to be put in, not content: the table starts empty either way.
     */
    internal class create : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val sequence: Long = args.checklong(1)
            val records: Long = args.optlong(2, 0L)
            args.argcheck(sequence >= 0 && sequence <= Int.MAX_VALUE, 1, "out of range")
            args.argcheck(records >= 0 && records <= Int.MAX_VALUE, 2, "out of range")
            return LuaTable(sequence.toInt(), records.toInt())
        }
    }

    /**
     * `table.move (a1, f, e, t [,a2])`, from Lua 5.3.
     *
     * Moves `a1[f..e]` to `a2[t..]`, answering `a2`. Source and destination may
     * be the same table and may overlap, so the direction of the copy is chosen
     * to keep the elements that have not been read yet.
     */
    internal class move : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val source: LuaValue = args.checktable(1)!!
            val from: Long = args.checklong(2)
            val to: Long = args.checklong(3)
            val target: Long = args.checklong(4)
            val destination: LuaValue = if (args.isnoneornil(5)) source else args.checktable(5)!!
            if (to >= from) {
                argcheck(
                    from > 0 || to < Long.MAX_VALUE + from,
                    3,
                    "too many elements to move",
                )
                val count: Long = to - from + 1
                argcheck(target <= Long.MAX_VALUE - count + 1, 4, "destination wrap around")
                // Copy backwards when the ranges overlap forwards, so a source
                // element is never overwritten before it has been read.
                if (target > from && target <= to && source === destination) {
                    var i: Long = count - 1
                    while (i >= 0) {
                        destination.set(
                            LuaValue.valueOf(target + i),
                            source.get(LuaValue.valueOf(from + i)),
                        )
                        i--
                    }
                } else {
                    var i = 0L
                    while (i < count) {
                        destination.set(
                            LuaValue.valueOf(target + i),
                            source.get(LuaValue.valueOf(from + i)),
                        )
                        i++
                    }
                }
            }
            return destination
        }
    }

    internal class insert : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val list: LuaValue = checkindexable(args, writable = true)
            // The first free slot. A length of math.maxinteger leaves no room
            // for one more, and the count wraps round rather than overflowing,
            // which is what Lua does here.
            val empty: Long = lengthofValue(list) + 1L
            val pos: Long
            when (args.narg()) {
                2 -> pos = empty

                3 -> {
                    pos = args.checklong(2)
                    // Read unsigned, so a position of zero or a negative one
                    // is out of bounds without a separate test.
                    args.argcheck(
                        (pos - 1L).toULong() < empty.toULong(),
                        2,
                        "position out of bounds",
                    )
                    var index: Long = empty
                    while (index > pos) {
                        list.set(LuaValue.valueOf(index), list.get(LuaValue.valueOf(index - 1L)))
                        index--
                    }
                }

                else -> return (error("wrong number of arguments to 'insert'"))!!
            }
            list.set(LuaValue.valueOf(pos), (args.arg(args.narg()))!!)
            return (NONE)!!
        }
    }

    // "pack" (...) -> table
    internal class pack : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val t: LuaValue = tableOf(args, 1)
            t.set("n", args.narg())
            return t
        }
    }

    // "remove" (table [, pos]) -> removed-ele
    internal class remove : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val table: LuaTable = args.checktable(1)
            val size: Int = table.length()
            val pos: Int = args.optint(2, size)
            if (pos != size && (pos < 1 || pos > size + 1)) {
                argerror(2, "position out of bounds: " + pos + " not between 1 and " + (size + 1))
            }
            return (table.remove(pos))!!
        }
    }

    // "sort" (table [, comp])
    internal class sort(private val globals: net.blueva.luak.Globals?) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            args.checktable(1).sort(
                if (args.isnil(2)) NIL else args.checkfunction(2),
                globals?.debuglib,
            )
            return (NONE)!!
        }
    }


    // "unpack", // (list [,i [,j]]) -> result1, ...
    /**
     * `table.unpack (list [, i [, j]])`.
     *
     * The list only has to be indexable, not a table, which is what lets
     * `table.unpack(s, i, j)` read through an `__index` rather than only from
     * a table's own array part.
     */
    internal class unpack : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val list: LuaValue = checkindexable(args)
            val first: Long = args.optlong(2, 1L)
            // Only work out the length when it is going to be used as the end.
            val last: Long = if (args.isnoneornil(3)) list.length().toLong() else args.checklong(3)
            if (last < first) return NONE!!
            val count: Long = last - first + 1
            // The cap is the stack size Lua allows, and asking for exactly
            // that many leaves no room for the call itself, so it is refused
            // too.
            if (count <= 0 || count >= MAX_UNPACK) LuaValue.error("too many results to unpack")
            val out: Array<LuaValue?> = arrayOfNulls(count.toInt())
            for (offset in 0..<count.toInt()) {
                out[offset] = list.get(LuaValue.valueOf(first + offset))
            }
            return varargsOf(out)!!
        }

        private companion object {
            /** As many results as unpack will produce, mirroring Lua's stack cap. */
            const val MAX_UNPACK: Long = 1000000L
        }
    }
}

/**
 * Argument one of a table function, which need not be a table.
 *
 * Lua lets these work on anything that can be read like one - a value whose
 * metatable supplies `__index` - and rejects everything else with the ordinary
 * "table expected" complaint.
 */
private fun checkindexable(args: Varargs, writable: Boolean = false): LuaValue {
    val list: LuaValue = args.checkvalue(1)!!
    if (list.istable()) return list
    val metatable: LuaValue? = list.getmetatable()
    if (metatable != null && !metatable.isnil()) {
        val readable: Boolean = !metatable.get("__index")!!.isnil()
        // A function that writes back needs somewhere to write: a string can
        // be read like a table but not assigned to.
        val assignable: Boolean = !writable || !metatable.get("__newindex")!!.isnil()
        if (readable && assignable) return list
    }
    args.checktable(1) // raises "bad argument #1 ... (table expected, got X)"
    return list
}

/**
 * How long [list] says it is, as a whole number.
 *
 * A `__len` handler may answer anything at all; what it answers has to be a
 * count for a library function to work from, and Lua says so plainly rather
 * than complaining about an argument.
 */
private fun lengthofValue(list: LuaValue): Long {
    val length: LuaValue = list.len()
    if (length.isnumber()) {
        val value: Double = length.todouble()
        val whole: Long = value.toLong()
        if (whole.toDouble() == value) return whole
    }
    LuaValue.error("object length is not an integer")
    return 0L
}
