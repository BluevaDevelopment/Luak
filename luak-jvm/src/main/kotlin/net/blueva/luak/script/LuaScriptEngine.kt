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
package net.blueva.luak.script

import net.blueva.luak.*
import net.blueva.luak.lib.ThreeArgFunction
import net.blueva.luak.lib.TwoArgFunction
import net.blueva.luak.lib.jvm.CoerceJavaToLua
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import javax.script.*

/**
 * Implementation of the ScriptEngine interface which can compile and execute
 * scripts using luaj.
 * 
 * 
 * 
 * This engine requires the types of the Bindings and ScriptContext to be
 * compatible with the engine.  For creating new client context use
 * ScriptEngine.createContext() which will return [LuaContext],
 * and for client bindings use the default engine scoped bindings or
 * construct the bindings directly.
 */
class LuaScriptEngine : AbstractScriptEngine(), ScriptEngine, Compilable {
    private val context: LuaContext

    init {
        // set up context
        context = LuaContext()
        context.setBindings(createBindings(), ScriptContext.ENGINE_SCOPE)
        setContext(context)


        // set special values
        put(LANGUAGE_VERSION, __LANGUAGE_VERSION__)
        put(LANGUAGE, __LANGUAGE__)
        put(ENGINE, __NAME__)
        put(ENGINE_VERSION, __ENGINE_VERSION__)
        put(ARGV, __ARGV__)
        put(FILENAME, __FILENAME__)
        put(NAME, __SHORT_NAME__)
        put("THREADING", null)
    }

    @Throws(ScriptException::class)
    override fun compile(script: String): CompiledScript {
        return compile(StringReader(script))
    }

    @Throws(ScriptException::class)
    override fun compile(script: Reader): CompiledScript {
        try {
            val `is`: InputStream = Utf8Encoder(script)
            try {
                val g: Globals = context.globals
                val f: LuaFunction = g.load(`is`, "script", "t", g)!!.checkfunction()!!
                return LuaCompiledScript(f, g)
            } catch (lee: LuaError) {
                throw ScriptException(lee.message)
            } finally {
                `is`.close()
            }
        } catch (e: Exception) {
            throw ScriptException("eval threw " + e.toString())
        }
    }

    @Throws(ScriptException::class)
    override fun eval(reader: Reader, bindings: Bindings): Any? {
        return (compile(reader) as LuaCompiledScript).eval(context.globals, bindings)
    }

    @Throws(ScriptException::class)
    override fun eval(script: String, bindings: Bindings): Any? {
        return eval(StringReader(script), bindings)
    }

    override fun getScriptContext(nn: Bindings?): ScriptContext? {
        throw IllegalStateException("LuaScriptEngine should not be allocating contexts.")
    }

    override fun createBindings(): Bindings {
        return SimpleBindings()
    }

    @Throws(ScriptException::class)
    override fun eval(script: String, context: ScriptContext?): Any? {
        return eval(StringReader(script), context)
    }

    @Throws(ScriptException::class)
    override fun eval(reader: Reader, context: ScriptContext?): Any? {
        return compile(reader).eval(context)
    }

    override fun getFactory(): ScriptEngineFactory {
        return myFactory
    }


    internal inner class LuaCompiledScript(@JvmField val function: LuaFunction, val compiling_globals: Globals?) :
        CompiledScript() {
        override fun getEngine(): ScriptEngine {
            return this@LuaScriptEngine
        }

        @Throws(ScriptException::class)
        override fun eval(): Any? {
            return eval(getContext())
        }

        @Throws(ScriptException::class)
        override fun eval(bindings: Bindings): Any? {
            return eval((getContext() as LuaContext).globals, bindings)
        }

        @Throws(ScriptException::class)
        override fun eval(context: ScriptContext): Any? {
            return eval((context as LuaContext).globals, context.getBindings(ScriptContext.ENGINE_SCOPE))
        }

        @Throws(ScriptException::class)
        fun eval(g: Globals, b: Bindings): Any? {
            g.setmetatable(BindingsMetatable(b))
            var f = function
            if (f.isclosure()) f = LuaClosure(f.checkclosure()!!.p, g)
            else {
                try {
                    f = f.javaClass.newInstance()
                } catch (e: Exception) {
                    throw ScriptException(e)
                }
                f.initupvalue1(g)
            }
            return Companion.toJava(f.invoke(LuaValue.NONE!!)!!)
        }
    }

    // ------ convert char stream to byte stream for lua compiler ----- 
    private inner class Utf8Encoder(private val r: Reader) : InputStream() {
        private val buf = IntArray(2)
        private var n = 0

        @Throws(IOException::class)
        override fun read(): Int {
            if (n > 0) return buf[--n]
            val c = r.read()
            if (c < 0x80) return c
            n = 0
            if (c < 0x800) {
                buf[n++] = (0x80 or (c and 0x3f))
                return (0xC0 or ((c shr 6) and 0x1f))
            } else {
                buf[n++] = (0x80 or (c and 0x3f))
                buf[n++] = (0x80 or ((c shr 6) and 0x3f))
                return (0xE0 or ((c shr 12) and 0x0f))
            }
        }
    }

    internal class BindingsMetatable(bindings: Bindings) : LuaTable() {
        init {
            this.rawset(INDEX, object : TwoArgFunction() {
                override fun call(table: LuaValue?, key: LuaValue?): LuaValue? {
                    if (key!!.isstring()) return toLua(bindings.get(key.tojstring()))
                    else return this@BindingsMetatable.rawget(key)
                }
            })
            this.rawset(NEWINDEX, object : ThreeArgFunction() {
                override fun call(table: LuaValue?, key: LuaValue?, value: LuaValue?): LuaValue? {
                    if (key!!.isstring()) {
                        val k: String? = key.tojstring()
                        val v: Any? = LuaScriptEngine.Companion.toJava(value!!)
                        if (v == null) bindings.remove(k)
                        else bindings.put(k, v)
                    } else {
                        this@BindingsMetatable.rawset(key, value)
                    }
                    return NONE
                }
            })
        }
    }

    companion object {
        private val __ENGINE_VERSION__ = Lua.LUAK_VERSION
        private const val __NAME__ = "Luak"
        private const val __SHORT_NAME__ = "Luak"
        private const val __LANGUAGE__ = "lua"
        private val __LANGUAGE_VERSION__ = Lua._VERSION.removePrefix("Lua ")
        private const val __ARGV__ = "arg"
        private const val __FILENAME__ = "?"

        private val myFactory: ScriptEngineFactory = LuaScriptEngineFactory()

        private fun toLua(javaValue: Any?): LuaValue? {
            return if (javaValue == null) LuaValue.NIL else if (javaValue is LuaValue) javaValue else CoerceJavaToLua.coerce(
                javaValue
            )
        }

        private fun toJava(luajValue: LuaValue): Any? {
            when (luajValue.type()) {
                LuaValue.TNIL -> return null
                LuaValue.TSTRING -> return luajValue.tojstring()
                LuaValue.TUSERDATA -> return luajValue.checkuserdata(Any::class)
                LuaValue.TNUMBER -> return if (luajValue.isinttype()) luajValue.toint() as Any else luajValue.todouble() as Any
                else -> return luajValue
            }
        }

        private fun toJava(v: Varargs): Any? {
            val n = v.narg()
            when (n) {
                0 -> return null
                1 -> return Companion.toJava(v.arg1()!!)
                else -> {
                    val o = arrayOfNulls<Any>(n)
                    var i = 0
                    while (i < n) {
                        o[i] = Companion.toJava(v.arg(i + 1)!!)
                        ++i
                    }
                    return o
                }
            }
        }
    }
}
