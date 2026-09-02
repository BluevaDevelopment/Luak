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
package net.blueva.luak.lib.jvm

import net.blueva.luak.LuaValue
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.*
import kotlin.math.max

/**
 * LuaValue that represents a Java class.
 * 
 * 
 * Will respond to get() and set() by returning field values, or java methods.
 * 
 * 
 * This class is not used directly.
 * It is returned by calls to [CoerceJavaToLua.coerce]
 * when a Class is supplied.
 * @see CoerceJavaToLua
 * 
 * @see CoerceLuaToJava
 */
internal class JavaClass(c: Class<*>?) : JavaInstance(c), CoerceJavaToLua.Coercion {
    var fields: MutableMap<Any?, Any?>? = null
    var methods: MutableMap<Any?, Any?>? = null
    var innerclasses: MutableMap<Any?, Any?>? = null

    init {
        this.jclass = this
    }

    override fun coerce(javaValue: Any?): LuaValue {
        return this
    }

    fun getField(key: LuaValue?): Field? {
        if (fields == null) {
            val m: MutableMap<Any?, Any?> = HashMap<Any?, Any?>()
            val f = (m_instance as Class<*>).getFields()
            for (i in f.indices) {
                val fi = f[i]
                if (Modifier.isPublic(fi.getModifiers())) {
                    m.put(valueOf(fi.getName()), fi)
                    try {
                        if (!fi.isAccessible()) fi.setAccessible(true)
                    } catch (s: SecurityException) {
                    }
                }
            }
            fields = m
        }
        return fields!!.get(key) as Field?
    }

    fun getMethod(key: LuaValue?): LuaValue? {
        if (methods == null) {
            val namedlists = HashMap<String, MutableList<JavaMethod>>()
            val m = (m_instance as Class<*>).getMethods()
            for (i in m.indices) {
                val mi = m[i]
                if (Modifier.isPublic(mi.getModifiers())) {
                    val name = mi.getName()
                    namedlists.getOrPut(name) { ArrayList() }.add(JavaMethod.forMethod(mi))
                }
            }
            val map: MutableMap<Any?, Any?> = HashMap<Any?, Any?>()
            val c = (m_instance as Class<*>).getConstructors()
            val list = ArrayList<JavaConstructor>()
            for (i in c.indices) if (Modifier.isPublic(c[i].getModifiers())) list.add(
                JavaConstructor.forConstructor(
                    c[i]
                )
            )
            when (list.size) {
                0 -> {}
                1 -> map.put(NEW, list.get(0))
                else -> map.put(
                    NEW,
                    JavaConstructor.forConstructors(list.toTypedArray())
                )
            }

            for ((name, overloads) in namedlists) {
                map.put(
                    valueOf(name),
                    if (overloads.size == 1) overloads[0] else JavaMethod.forMethods(overloads.toTypedArray())
                )
            }
            methods = map
        }
        return methods!!.get(key) as LuaValue?
    }

    fun getInnerClass(key: LuaValue?): Class<*>? {
        if (innerclasses == null) {
            val m: MutableMap<Any?, Any?> = HashMap<Any?, Any?>()
            val c = (m_instance as Class<*>).getClasses()
            for (i in c.indices) {
                val ci = c[i]
                val name = ci.getName()
                val stub = name.substring(max(name.lastIndexOf('$'), name.lastIndexOf('.')) + 1)
                m.put(valueOf(stub), ci)
            }
            innerclasses = m
        }
        return innerclasses!!.get(key) as Class<*>?
    }

    val constructor: LuaValue?
        get() = getMethod(NEW)

    companion object {
        val classes: MutableMap<Any?, Any?> = Collections.synchronizedMap<Any?, Any?>(HashMap<Any?, Any?>())

        val NEW: LuaValue = valueOf("new")

        @JvmStatic
        fun forClass(c: Class<*>?): JavaClass {
            var j = classes.get(c) as JavaClass?
            if (j == null) classes.put(c, JavaClass(c).also { j = it })
            return j!!
        }
    }
}
