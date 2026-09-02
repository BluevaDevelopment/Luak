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

import net.blueva.luak.LoadState
import net.blueva.luak.LocVars
import net.blueva.luak.LuaString
import net.blueva.luak.LuaValue
import net.blueva.luak.Prototype
import net.blueva.luak.io.DataOutputStream
import net.blueva.luak.io.IOException
import net.blueva.luak.io.OutputStream

/** Class to dump a [Prototype] into an output stream, as part of compiling.
 * 
 * 
 * Generally, this class is not used directly, but rather indirectly via a command
 * line interface tool such as [luac].
 * 
 * 
 * A lua binary file is created via [dump]:
 * <pre> `Globals globals = JvmPlatform.standardGlobals(); Prototype p = globals.compilePrototype(new StringReader("print('hello, world')"), "main.lua"); ByteArrayOutputStream o = new ByteArrayOutputStream(); DumpState.dump(p, o, false); byte[] lua_binary_file_bytes = o.toByteArray(); ` </pre>
 * 
 * The [LoadState] may be used directly to undump these bytes:
 * <pre> `Prototypep = LoadState.instance.undump(new ByteArrayInputStream(lua_binary_file_bytes), "main.lua"); LuaClosure c = new LuaClosure(p, globals); c.call(); ` </pre>
 * 
 * 
 * More commonly, the [Globals.undumper] may be used to undump them:
 * <pre> `Prototype p = globals.loadPrototype(new ByteArrayInputStream(lua_binary_file_bytes), "main.lua", "b"); LuaClosure c = new LuaClosure(p, globals); c.call(); ` </pre>
 * 
 * @see luac
 * 
 * @see LoadState
 * 
 * @see Globals
 * 
 * @see Prototype
 */
class DumpState(w: OutputStream?, strip: Boolean) {
    // header fields
    private var IS_LITTLE_ENDIAN = true
    private var NUMBER_FORMAT: Int = net.blueva.luak.compiler.DumpState.Companion.NUMBER_FORMAT_DEFAULT
    private var SIZEOF_LUA_NUMBER = 8
    var writer: DataOutputStream?
    var strip: Boolean
    var status: Int

    init {
        this.writer = DataOutputStream(w!!)
        this.strip = strip
        this.status = 0
    }

    @kotlin.Throws(IOException::class)
    fun dumpBlock(b: ByteArray?, size: Int) {
        writer!!.write(b!!, 0, size)
    }

    @kotlin.Throws(IOException::class)
    fun dumpChar(b: Int) {
        writer!!.write(b)
    }

    @kotlin.Throws(IOException::class)
    fun dumpInt(x: Int) {
        if (IS_LITTLE_ENDIAN) {
            writer!!.writeByte(x and 0xff)
            writer!!.writeByte((x shr 8) and 0xff)
            writer!!.writeByte((x shr 16) and 0xff)
            writer!!.writeByte((x shr 24) and 0xff)
        } else {
            writer!!.writeInt(x)
        }
    }

    /**
     * Every string written so far, and where it was written.
     *
     * A string that appears more than once in a chunk - a constant several
     * nested functions share, a name repeated in the debug information - is
     * written once and pointed at afterwards. A chunk of any size is largely
     * made of repeated names, so this is most of what keeps one small.
     */
    private val written: MutableMap<LuaString, Int> = HashMap()

    @kotlin.Throws(IOException::class)
    fun dumpString(s: LuaString?) {
        // A chunk that was loaded without debug information has nothing to
        // say here, and a length of zero is how the format says so.
        if (s == null) {
            dumpInt(0)
            return
        }
        val already: Int? = written[s]
        if (already != null) {
            // Written before: where it was, rather than what it is.
            dumpInt(-already)
            return
        }
        val len: Int = s.len().toint()
        dumpInt(len + 1)
        s.write((writer)!!, 0, len)
        writer!!.write(0)
        written[s] = written.size + 1
    }

    @kotlin.Throws(IOException::class)
    fun dumpDouble(d: Double) {
        dumpLong((d).toBits())
    }

    @kotlin.Throws(IOException::class)
    fun dumpLong(l: Long) {
        if (IS_LITTLE_ENDIAN) {
            dumpInt(l.toInt())
            dumpInt((l shr 32).toInt())
        } else {
            writer!!.writeLong(l)
        }
    }

    @kotlin.Throws(IOException::class)
    fun dumpCode(f: Prototype) {
        val code: IntArray = f.code!!
        val n = code.size
        dumpInt(n)
        for (i in 0..<n) dumpInt(code[i])
    }

    @kotlin.Throws(IOException::class)
    fun dumpConstants(f: Prototype) {
        val k: Array<LuaValue?> = f.k!!
        var i: Int
        var n = k.size
        dumpInt(n)
        i = 0
        while (i < n) {
            val o: LuaValue = k[i]!!
            when (o.type()) {
                LuaValue.TNIL -> writer!!.write(LuaValue.TNIL)
                LuaValue.TBOOLEAN -> {
                    writer!!.write(LuaValue.TBOOLEAN)
                    dumpChar(if (o.toboolean()) 1 else 0)
                }

                LuaValue.TNUMBER -> when (NUMBER_FORMAT) {
                    net.blueva.luak.compiler.DumpState.Companion.NUMBER_FORMAT_FLOATS_OR_DOUBLES ->
                        if (o.isinttype()) {
                            // Tagged apart from a float, or the subtype would
                            // not survive the round trip.
                            writer!!.write(net.blueva.luak.LoadState.LUA_TNUMINT)
                            dumpLong(o.tolong())
                        } else {
                            writer!!.write(LuaValue.TNUMBER)
                            dumpDouble(o.todouble())
                        }

                    net.blueva.luak.compiler.DumpState.Companion.NUMBER_FORMAT_INTS_ONLY -> {
                        kotlin.require(!(!net.blueva.luak.compiler.DumpState.Companion.ALLOW_INTEGER_CASTING && !o.isint())) { "not an integer: " + o }
                        writer!!.write(LuaValue.TNUMBER)
                        dumpInt(o.toint())
                    }

                    net.blueva.luak.compiler.DumpState.Companion.NUMBER_FORMAT_NUM_PATCH_INT32 -> if (o.isint()) {
                        writer!!.write(LuaValue.TINT)
                        dumpInt(o.toint())
                    } else {
                        writer!!.write(LuaValue.TNUMBER)
                        dumpDouble(o.todouble())
                    }

                    else -> throw IllegalArgumentException("number format not supported: " + NUMBER_FORMAT)
                }

                LuaValue.TSTRING -> {
                    writer!!.write(LuaValue.TSTRING)
                    dumpString(o as LuaString)
                }

                else -> throw IllegalArgumentException("bad type for " + o)
            }
            i++
        }
        n = f.p!!.size
        dumpInt(n)
        i = 0
        while (i < n) {
            dumpFunction((f.p!![i])!!, f.source)
            i++
        }
    }

    @kotlin.Throws(IOException::class)
    fun dumpUpvalues(f: Prototype) {
        val n: Int = f.upvalues!!.size
        dumpInt(n)
        for (i in 0..<n) {
            writer!!.writeByte(if (f.upvalues!![i]!!.instack) 1 else 0)
            writer!!.writeByte((f.upvalues!![i]!!.idx).toInt())
        }
    }

    @kotlin.Throws(IOException::class)
    fun dumpDebug(f: Prototype) {
        var i: Int
        var n: Int
        n = if (strip) 0 else f.lineinfo!!.size
        dumpInt(n)
        i = 0
        while (i < n) {
            dumpInt(f.lineinfo!![i])
            i++
        }
        n = if (strip) 0 else f.locvars.size
        dumpInt(n)
        i = 0
        while (i < n) {
            val lvi: LocVars = f.locvars[i]!!
            dumpString(lvi.varname)
            dumpInt(lvi.startpc)
            dumpInt(lvi.endpc)
            i++
        }
        n = if (strip) 0 else f.upvalues!!.size
        dumpInt(n)
        i = 0
        while (i < n) {
            dumpString(f.upvalues!![i]!!.name)
            i++
        }
    }

    @kotlin.Throws(IOException::class)
    @kotlin.jvm.JvmOverloads
    fun dumpFunction(f: Prototype, psource: LuaString? = null) {
        // Written before anything else, so that a nested function can be given
        // it as it is read. A nested function almost always came from the same
        // text as the one around it, and a chunk of any size would otherwise
        // carry the same name once per function in it: nothing written here
        // means "the same as the function this one is inside".
        if (strip || f.source == psource) dumpInt(0)
        else dumpString(f.source)
        dumpInt(f.linedefined)
        dumpInt(f.lastlinedefined)
        dumpChar(f.numparams)
        dumpChar(f.is_vararg)
        dumpChar(f.maxstacksize)
        dumpCode(f)
        dumpConstants(f)
        dumpUpvalues(f)
        dumpDebug(f)
    }

    @kotlin.Throws(IOException::class)
    /**
     * The head of a binary chunk, byte for byte as Lua 5.5 writes it.
     *
     * After the signature and the two bytes that say which Lua and which
     * format wrote it comes a run of bytes chosen to be spoiled by anything
     * that rewrites a file it does not understand, and then one value of each
     * kind the rest of the chunk is written in: the size each takes and a
     * known value of it, so a chunk written by a build that counts or orders
     * bytes differently is refused rather than misread.
     */
    fun dumpHeader() {
        writer!!.write(LoadState.LUA_SIGNATURE)
        writer!!.write(LoadState.LUAC_VERSION)
        writer!!.write(LoadState.LUAC_FORMAT)
        writer!!.write(LoadState.LUAC_TAIL)
        writer!!.write(net.blueva.luak.compiler.DumpState.Companion.SIZEOF_INT)
        dumpInt(LoadState.LUAC_INT)
        writer!!.write(net.blueva.luak.compiler.DumpState.Companion.SIZEOF_INSTRUCTION)
        dumpInt(LoadState.LUAC_INST)
        writer!!.write(net.blueva.luak.compiler.DumpState.Companion.SIZEOF_LUA_INTEGER)
        dumpLong(LoadState.LUAC_INT.toLong())
        writer!!.write(SIZEOF_LUA_NUMBER)
        // A build that keeps every number as an integer has no float to check
        // with, and says so by the size it just wrote.
        if (NUMBER_FORMAT == net.blueva.luak.compiler.DumpState.Companion.NUMBER_FORMAT_INTS_ONLY) {
            dumpInt(LoadState.LUAC_INT)
        } else {
            dumpDouble(LoadState.LUAC_NUM)
        }
    }

    companion object {
        /** set true to allow integer compilation  */
        var ALLOW_INTEGER_CASTING: Boolean = false

        /** format corresponding to non-number-patched lua, all numbers are floats or doubles  */
        const val NUMBER_FORMAT_FLOATS_OR_DOUBLES: Int = 0

        /** format corresponding to non-number-patched lua, all numbers are ints  */
        const val NUMBER_FORMAT_INTS_ONLY: Int = 1

        /** format corresponding to number-patched lua, all numbers are 32-bit (4 byte) ints  */
        const val NUMBER_FORMAT_NUM_PATCH_INT32: Int = 4

        /** default number format  */
        val NUMBER_FORMAT_DEFAULT: Int = net.blueva.luak.compiler.DumpState.Companion.NUMBER_FORMAT_FLOATS_OR_DOUBLES

        private const val SIZEOF_INT = 4

        /** How many bytes a Lua integer takes in a chunk. */
        private const val SIZEOF_LUA_INTEGER = 8
        private const val SIZEOF_SIZET = 4
        private const val SIZEOF_INSTRUCTION = 4

        /*
	** dump Lua function as precompiled chunk
	*/
        @kotlin.Throws(IOException::class)
        fun dump(f: Prototype, w: OutputStream?, strip: Boolean): Int {
            val D: DumpState = net.blueva.luak.compiler.DumpState(w, strip)
            D.dumpHeader()
            D.dumpFunction(f)
            return D.status
        }

        /**
         * 
         * @param f the function to dump
         * @param w the output stream to dump to
         * @param stripDebug true to strip debugging info, false otherwise
         * @param numberFormat one of NUMBER_FORMAT_FLOATS_OR_DOUBLES, NUMBER_FORMAT_INTS_ONLY, NUMBER_FORMAT_NUM_PATCH_INT32
         * @param littleendian true to use little endian for numbers, false for big endian
         * @return 0 if dump succeeds
         * @throws IOException
         * @throws IllegalArgumentException if the number format it not supported
         */
        @kotlin.Throws(IOException::class)
        fun dump(f: Prototype, w: OutputStream?, stripDebug: Boolean, numberFormat: Int, littleendian: Boolean): Int {
            when (numberFormat) {
                net.blueva.luak.compiler.DumpState.Companion.NUMBER_FORMAT_FLOATS_OR_DOUBLES, net.blueva.luak.compiler.DumpState.Companion.NUMBER_FORMAT_INTS_ONLY, net.blueva.luak.compiler.DumpState.Companion.NUMBER_FORMAT_NUM_PATCH_INT32 -> {}
                else -> throw IllegalArgumentException("number format not supported: " + numberFormat)
            }
            val D: DumpState = net.blueva.luak.compiler.DumpState(w, stripDebug)
            D.IS_LITTLE_ENDIAN = littleendian
            D.NUMBER_FORMAT = numberFormat
            D.SIZEOF_LUA_NUMBER =
                (if (numberFormat == net.blueva.luak.compiler.DumpState.Companion.NUMBER_FORMAT_INTS_ONLY) 4 else 8)
            D.dumpHeader()
            D.dumpFunction(f)
            return D.status
        }
    }
}
