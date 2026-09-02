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

import net.blueva.luak.Globals
import net.blueva.luak.LuaClosure
import net.blueva.luak.LuaFunction
import net.blueva.luak.LuaString
import net.blueva.luak.LuaValue
import net.blueva.luak.Prototype
import net.blueva.luak.io.IOException
import net.blueva.luak.io.InputStream

/**
 * Compiler for Lua.
 * 
 * 
 * 
 * Compiles lua source files into lua bytecode within a [Prototype],
 * loads lua binary files directly into a [Prototype],
 * and optionaly instantiates a [LuaClosure] around the result
 * using a user-supplied environment.
 * 
 * 
 * 
 * Implements the [net.blueva.luak.Globals.Compiler] interface for loading
 * initialized chunks, which is an interface common to
 * lua bytecode compiling and java bytecode compiling.
 * 
 * 
 * 
 * The [LuaC] compiler is installed by default by both the
 * [net.blueva.luak.lib.LuaPlatform] and [net.blueva.luak.lib.jvm.JvmPlatform] classes,
 * so in the following example, the default [LuaC] compiler
 * will be used:
 * <pre> `Globals globals = JvmPlatform.standardGlobals(); globals.load(new StringReader("print 'hello'"), "main.lua" ).call(); ` </pre>
 * 
 * To load the LuaC compiler manually, use the install method:
 * <pre> `LuaC.install(globals); ` </pre>
 * 
 * @see .install
 * @see Globals.compiler
 * 
 * @see Globals.loader
 * 
 * @see net.blueva.luak.luajc.LuaJC
 * 
 * @see net.blueva.luak.lib.jvm.JvmPlatform
 * 
 * @see net.blueva.luak.lib.LuaPlatform
 * 
 * @see BaseLib
 * 
 * @see LuaValue
 * 
 * @see Prototype
 */
class LuaC protected constructor() : Constants(), Globals.Compiler, Globals.Loader {
    /** Compile lua source into a Prototype.
     * @param stream InputStream representing the text source conforming to lua source syntax.
     * @param chunkname String name of the chunk to use.
     * @return Prototype representing the lua chunk for this source.
     * @throws IOException
     */
    @kotlin.Throws(IOException::class)
    override fun compile(stream: InputStream?, chunkname: String?): Prototype {
        return CompileState().luaY_parser(stream!!, chunkname)
    }

    @kotlin.Throws(IOException::class)
    override fun load(prototype: Prototype?, chunkname: String?, env: LuaValue?): LuaFunction? {
        return LuaClosure((prototype)!!, env)
    }

    @Deprecated(
        """ Use Globals.load(InputString, String, String) instead, 
	  or LuaC.compile(InputStream, String) and construct LuaClosure directly."""
    )
    @kotlin.Throws(IOException::class)
    fun load(stream: InputStream, chunkname: String?, globals: Globals?): LuaValue? {
        return LuaClosure(compile(stream, chunkname), globals)
    }

    internal class CompileState {
        var nCcalls: Int = 0
        private val strings: HashMap<LuaString, LuaString> = HashMap()

        /** Parse the input  */
        @kotlin.Throws(IOException::class)
        fun luaY_parser(z: InputStream, name: String?): Prototype {
            val lexstate: LexState = LexState(this, z)
            val funcstate: FuncState = FuncState()
            // lexstate.buff = buff;
            lexstate.fs = funcstate
            lexstate.setinput(this, z.read(), z, LuaValue.valueOf(name) as LuaString?)
            /* main func. is always vararg */
            funcstate.f = Prototype()
            funcstate.f!!.source = LuaValue.valueOf(name) as LuaString?
            lexstate.mainfunc(funcstate)
            compilerAssert(funcstate.prev == null)
            /* all scopes should be correctly finished */
            compilerAssert(
                lexstate.dyd == null
                        || (lexstate.dyd.n_actvar === 0 && lexstate.dyd.n_gt === 0 && lexstate.dyd.n_label === 0)
            )
            return funcstate.f!!
        }

        // look up and keep at most one copy of each string
        fun newTString(s: String?): LuaString? {
            return cachedLuaString(LuaString.valueOf((s)!!))
        }

        // look up and keep at most one copy of each string
        fun newTString(s: LuaString?): LuaString? {
            return cachedLuaString(s)
        }

        fun cachedLuaString(s: LuaString?): LuaString? {
            val c: LuaString? = strings[s]
            if (c != null) return c
            strings[s!!] = s
            return s
        }

        fun pushfstring(string: String?): String? {
            return string
        }

        private fun compilerAssert(condition: Boolean) {
            if (!condition) throw net.blueva.luak.LuaError("compiler assert failed")
        }
    }

    companion object {
        /** A sharable instance of the LuaC compiler.  */
        val instance: LuaC = net.blueva.luak.compiler.LuaC()

        /** Install the compiler so that LoadState will first
         * try to use it when handed bytes that are
         * not already a compiled lua chunk.
         * @param globals the Globals into which this is to be installed.
         */
        fun install(globals: Globals) {
            globals.compiler = net.blueva.luak.compiler.LuaC.Companion.instance
            globals.loader = net.blueva.luak.compiler.LuaC.Companion.instance
        }
    }
}
