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
package net.blueva.luak.luajc

import net.blueva.luak.lib.jvm.asLuaReader
import net.blueva.luak.Globals
import net.blueva.luak.LuaClosure
import net.blueva.luak.LuaFunction
import net.blueva.luak.LuaValue
import net.blueva.luak.Prototype
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.util.*

/**
 * Implementation of [Globals.Compiler] which does direct
 * lua-to-java-bytecode compiling.
 * 
 * 
 * By default, when using [net.blueva.luak.lib.jvm.JvmPlatform] or
 * [net.blueva.luak.lib.jme.JmePlatform]
 * to construct globals, the plain compiler [LuaC] is installed and lua code
 * will only be compiled into lua bytecode and execute as [LuaClosure].
 * 
 * 
 * To override the default compiling behavior with [LuaJC]
 * lua-to-java bytecode compiler, install it before undumping code,
 * for example:
 * <pre> `LuaValue globals = JvmPlatform.standardGlobals(); LuaJC.install(globals); LuaValue chunk = globals.load( "print('hello, world'), "main.lua"); System.out.println(chunk.isclosure());  // Will be false when LuaJC is working. chunk.call(); ` </pre>
 * 
 * 
 * This requires the bcel library to be on the class path to work as expected.
 * If the library is not found, the default [LuaC] lua-to-lua-bytecode
 * compiler will be used.
 * 
 * @see Globals.compiler
 * 
 * @see .install
 * @see net.blueva.luak.compiler.LuaC
 * 
 * @see LuaValue
 */
class LuaJC protected constructor() : Globals.Loader {
    @Throws(IOException::class)
    fun compileAll(
        script: InputStream?,
        chunkname: String,
        filename: String,
        globals: Globals,
        genmain: Boolean
    ): Hashtable<*, *> {
        val classname: String = toStandardJavaClassName(chunkname)
        val p = globals.loadPrototype(script!!, classname, "bt")
        return compileProtoAndSubProtos(p, classname, filename, genmain)
    }

    @Throws(IOException::class)
    fun compileAll(
        script: Reader?,
        chunkname: String,
        filename: String,
        globals: Globals,
        genmain: Boolean
    ): Hashtable<*, *> {
        val classname: String = toStandardJavaClassName(chunkname)
        val p = globals.compilePrototype(script!!.asLuaReader(), classname)
        return compileProtoAndSubProtos(p, classname, filename, genmain)
    }

    @Throws(IOException::class)
    private fun compileProtoAndSubProtos(
        p: Prototype?,
        classname: String?,
        filename: String,
        genmain: Boolean
    ): Hashtable<*, *> {
        val luaname: String = toStandardLuaFileName(filename)
        val h: Hashtable<Any?, Any?> = Hashtable<Any?, Any?>()
        val gen = JavaGen(p, classname, luaname, genmain)
        insert(h, gen)
        return h
    }

    private fun insert(h: Hashtable<Any?, Any?>, gen: JavaGen) {
        h.put(gen.classname, gen.bytecode)
        var i = 0
        val n = if (gen.inners != null) gen.inners.size else 0
        while (i < n) {
            insert(h, gen.inners!![i]!!)
            i++
        }
    }

    @Throws(IOException::class)
    override fun load(p: Prototype?, name: String?, globals: LuaValue?): LuaFunction? {
        // The generated code has nowhere to run a __close handler from, no
        // notion of a declared global, and still counts a numeric 'for' the
        // way 5.2 did, so a chunk that uses any of those is left to the
        // interpreter rather than compiled wrongly.
        if (p != null && usesInterpreterOnlyOpcodes(p)) {
            return LuaClosure(p, globals as? net.blueva.luak.Globals)
        }
        val luaname: String = toStandardLuaFileName(name!!)
        val classname: String = toStandardJavaClassName(luaname)
        val loader = JavaLoader()
        return loader.load(p, classname, luaname, globals)
    }

    /** True when [p] or anything nested in it needs the interpreter. */
    private fun usesInterpreterOnlyOpcodes(p: Prototype): Boolean {
        val code: IntArray = p.code ?: return false
        for (instruction in code) {
            when (net.blueva.luak.Lua.GET_OPCODE(instruction)) {
                net.blueva.luak.Lua.OP_TBC,
                net.blueva.luak.Lua.OP_ERRNNIL,
                net.blueva.luak.Lua.OP_FORPREP,
                -> return true
            }
        }
        val inner: Array<Prototype?> = p.p ?: return false
        for (nested in inner) {
            if (nested != null && usesInterpreterOnlyOpcodes(nested)) return true
        }
        return false
    }

    companion object {
        val instance: LuaJC = LuaJC()

        /**
         * Install the compiler as the main Globals.Loader to use in a set of globals.
         * Will fall back to the LuaC prototype compiler.
         */
        @JvmStatic
        fun install(G: Globals) {
            G.loader = instance
        }

        private fun toStandardJavaClassName(luachunkname: String): String {
            val stub: String = toStub(luachunkname)
            val classname = StringBuffer()
            var i = 0
            val n = stub.length
            while (i < n) {
                val c = stub.get(i)
                classname.append(
                    if (((i == 0) && Character.isJavaIdentifierStart(c)) || ((i > 0) && Character.isJavaIdentifierPart(
                            c
                        ))
                    ) c else '_'
                )
                ++i
            }
            return classname.toString()
        }

        private fun toStandardLuaFileName(luachunkname: String): String {
            val stub: String = toStub(luachunkname)
            val filename = stub.replace('.', '/') + ".lua"
            return if (filename.startsWith("@")) filename.substring(1) else filename
        }

        private fun toStub(s: String): String {
            val stub = if (s.endsWith(".lua")) s.substring(0, s.length - 4) else s
            return stub
        }
    }
}
