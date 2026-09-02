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

import net.blueva.luak.LuaFunction
import net.blueva.luak.LuaValue
import net.blueva.luak.Prototype

class JavaLoader : ClassLoader() {
    private val unloaded: MutableMap<String?, ByteArray?> = HashMap<String?, ByteArray?>()

    fun load(p: Prototype?, classname: String?, filename: String?, env: LuaValue?): LuaFunction {
        val jg = JavaGen(p, classname, filename, false)
        return load(jg, env)
    }

    fun load(jg: JavaGen, env: LuaValue?): LuaFunction {
        include(jg)
        return load(jg.classname, env)
    }

    fun load(classname: String?, env: LuaValue?): LuaFunction {
        try {
            val c = loadClass(classname)
            val v = c.newInstance() as LuaFunction
            v.initupvalue1(env)
            return v
        } catch (e: Exception) {
            e.printStackTrace()
            throw IllegalStateException("bad class gen: " + e)
        }
    }

    fun include(jg: JavaGen) {
        unloaded.put(jg.classname, jg.bytecode)
        var i = 0
        val n = if (jg.inners != null) jg.inners.size else 0
        while (i < n) {
            include(jg.inners!![i]!!)
            i++
        }
    }

    @Throws(ClassNotFoundException::class)
    public override fun findClass(classname: String?): Class<*>? {
        val bytes = unloaded.get(classname)
        if (bytes != null) return defineClass(classname, bytes, 0, bytes.size)
        return super.findClass(classname)
    }
}
