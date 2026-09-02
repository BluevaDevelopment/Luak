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
package net.blueva.luak

/**
 * Prototype representing compiled lua code.
 * 
 * 
 * 
 * This is both a straight translation of the corresponding C type,
 * and the main data structure for execution of compiled lua bytecode.
 * 
 * 
 * 
 * Generally, the [Prototype] is not constructed directly is an intermediate result
 * as lua code is loaded using [Globals.load]:
 * <pre> `Globals globals = JvmPlatform.standardGlobals(); globals.load( new StringReader("print 'hello'"), "main.lua" ).call(); ` </pre>
 * 
 * 
 * 
 * To create a [Prototype] directly, a compiler such as
 * [net.blueva.luak.compiler.LuaC] may be used:
 * <pre> `InputStream is = new ByteArrayInputStream("print('hello,world')".toByteArray()); Prototype p = LuaC.instance.compile(is, "script"); `</pre>
 * 
 * To simplify loading, the [Globals.compilePrototype] method may be used:
 * <pre> `Prototype p = globals.compileProtoytpe(is, "script"); `</pre>
 * 
 * It may also be loaded from a [net.blueva.luak.io.Reader] via [Globals.compilePrototype]:
 * <pre> `Prototype p = globals.compileProtoytpe(new StringReader(script), "script"); `</pre>
 * 
 * To un-dump a binary file known to be a binary lua file that has been dumped to a string,
 * the [Globals.Undumper] interface may be used:
 * <pre> `FileInputStream lua_binary_file = new FileInputStream("foo.lc");  // Known to be compiled lua. Prototype p = globals.undumper.undump(lua_binary_file, "foo.lua"); `</pre>
 * 
 * To execute the code represented by the [Prototype] it must be supplied to
 * the constructor of a [LuaClosure]:
 * <pre> `Globals globals = JvmPlatform.standardGlobals(); LuaClosure f = new LuaClosure(p, globals); f.call(); `</pre>
 * 
 * To simplify the debugging of prototype values, the contents may be printed using [Print.print]:
 * <pre> `Print.print(p); `</pre>
 * 
 * 
 * 
 * @see LuaClosure
 * 
 * @see Globals
 * 
 * @see Globals.undumper
 * 
 * @see Globals.compiler
 * 
 * @see Print.print
 */
class Prototype {
    /* constants used by the function */
    var k: Array<LuaValue?>? = null
    var code: IntArray? = null

    /* functions defined inside the function */
    var p: Array<Prototype?>? = null

    /* map from opcodes to source lines */
    var lineinfo: IntArray? = null

    /* information about local variables */
    var locvars: Array<LocVars?> = emptyArray()

    /* upvalue information */
    var upvalues: Array<Upvaldesc?>?
    var source: LuaString? = null
    var linedefined: Int = 0
    var lastlinedefined: Int = 0
    var numparams: Int = 0
    var is_vararg: Int = 0
    var maxstacksize: Int = 0

    constructor() {
        p = net.blueva.luak.Prototype.Companion.NOSUBPROTOS
        upvalues = net.blueva.luak.Prototype.Companion.NOUPVALUES
    }

    constructor(n_upvalues: Int) {
        p = net.blueva.luak.Prototype.Companion.NOSUBPROTOS
        upvalues = arrayOfNulls<Upvaldesc>(n_upvalues)
    }

    override fun toString(): String {
        return (source?.toString() ?: "?") + ":" + linedefined + "-" + lastlinedefined
    }

    /** Get the name of a local variable.
     * 
     * @param number the local variable number to look up
     * @param pc the program counter
     * @return the name, or null if not found
     */
    fun getlocalname(number: Int, pc: Int): LuaString? {
        var number = number
        var i = 0
        while (i < locvars.size) {
            val lv = locvars[i] ?: break
            if (lv.startpc > pc) break
            if (pc < lv.endpc) {  /* is variable active? */
                number--
                if (number == 0) return lv.varname
            }
            i++
        }
        return null /* not found */
    }

    /**
     * The source name as it appears in an error message or a traceback.
     *
     * This is upstream's `luaO_chunkid`. A name given as `@file` or `=text`
     * loses its marker and is shortened from the front or the back as needed;
     * anything else is the chunk's own text, which is quoted as
     * `[string "..."]` and cut at the first newline so a message stays on one
     * line.
     */
    fun shortsource(): String = Lua.chunkid(source?.tojstring() ?: "=?")

    companion object {
        private val NOUPVALUES: Array<Upvaldesc?> = arrayOf<Upvaldesc?>()
        private val NOSUBPROTOS = arrayOf<Prototype?>()
    }
}
