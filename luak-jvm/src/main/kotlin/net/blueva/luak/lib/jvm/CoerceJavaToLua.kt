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

import net.blueva.luak.LuaDouble
import net.blueva.luak.LuaInteger
import net.blueva.luak.LuaString
import net.blueva.luak.LuaValue
import java.util.*

/**
 * Helper class to coerce values from Java to lua within the luajava library.
 * 
 * 
 * This class is primarily used by the [LuajavaLib],
 * but can also be used directly when working with Java/lua bindings.
 * 
 * 
 * To coerce scalar types, the various, generally the `valueOf(type)` methods
 * on [LuaValue] may be used:
 * 
 *  * [LuaValue.valueOf]
 *  * [LuaValue.valueOf]
 *  * [LuaValue.valueOf]
 *  * [LuaValue.valueOf]
 *  * [LuaValue.valueOf]
 * 
 * 
 * 
 * To coerce arrays of objects and lists, the `listOf(..)` and `tableOf(...)` methods
 * on [LuaValue] may be used:
 * 
 *  * [LuaValue.listOf]
 *  * [LuaValue.listOf]
 *  * [LuaValue.tableOf]
 *  * [LuaValue.tableOf]
 * 
 * The method [coerce] looks as the type and dimesioning
 * of the argument and tries to guess the best fit for corrsponding lua scalar,
 * table, or table of tables.
 * 
 * @see coerce
 * @see LuajavaLib
 */
object CoerceJavaToLua {
    val COERCIONS: MutableMap<Class<*>, Any?> = Collections.synchronizedMap<Class<*>, Any?>(HashMap<Class<*>, Any?>())

    init {
        val boolCoercion: Coercion = BoolCoercion()
        val intCoercion: Coercion = IntCoercion()
        val charCoercion: Coercion = CharCoercion()
        val doubleCoercion: Coercion = DoubleCoercion()
        val stringCoercion: Coercion = StringCoercion()
        val bytesCoercion: Coercion = BytesCoercion()
        val classCoercion: Coercion = ClassCoercion()
        COERCIONS.put(Boolean::class.javaObjectType, boolCoercion)
        COERCIONS.put(Byte::class.javaObjectType, intCoercion)
        COERCIONS.put(Char::class.javaObjectType, charCoercion)
        COERCIONS.put(Short::class.javaObjectType, intCoercion)
        COERCIONS.put(Int::class.javaObjectType, intCoercion)
        COERCIONS.put(Long::class.javaObjectType, doubleCoercion)
        COERCIONS.put(Float::class.javaObjectType, doubleCoercion)
        COERCIONS.put(Double::class.javaObjectType, doubleCoercion)
        COERCIONS.put(String::class.java, stringCoercion)
        COERCIONS.put(ByteArray::class.java, bytesCoercion)
        COERCIONS.put(Class::class.java, classCoercion)
    }

    /**
     * Coerse a Java object to a corresponding lua value.
     * 
     * 
     * Integral types `boolean`, `byte`,  `char`, and `int`
     * will become [LuaInteger];
     * `long`, `float`, and `double` will become [LuaDouble];
     * `String` and `byte[]` will become [LuaString];
     * types inheriting from [LuaValue] will be returned without coercion;
     * other types will become [LuaUserdata].
     * @param o Java object needing conversion
     * @return [LuaValue] corresponding to the supplied Java value.
     * @see LuaValue
     * 
     * @see LuaInteger
     * 
     * @see LuaDouble
     * 
     * @see LuaString
     * 
     * @see LuaUserdata
     */
    @JvmStatic
    fun coerce(o: Any?): LuaValue {
        if (o == null) return LuaValue.NIL
        val clazz: Class<*> = o.javaClass
        var c = COERCIONS.get(clazz) as Coercion?
        if (c == null) {
            c = if (clazz.isArray()) arrayCoercion else if (o is LuaValue) luaCoercion else instanceCoercion
            COERCIONS.put(clazz, c)
        }
        return c.coerce(o)
    }

    private val instanceCoercion: Coercion = InstanceCoercion()

    private val arrayCoercion: Coercion = ArrayCoercion()

    private val luaCoercion: Coercion = LuaCoercion()

    internal interface Coercion {
        fun coerce(javaValue: Any?): LuaValue
    }

    private class BoolCoercion : Coercion {
        override fun coerce(javaValue: Any?): LuaValue {
            val b = javaValue as Boolean
            return if (b) LuaValue.TRUE else LuaValue.FALSE
        }
    }

    private class IntCoercion : Coercion {
        override fun coerce(javaValue: Any?): LuaValue {
            val n = javaValue as Number
            return LuaInteger.valueOf(n.toInt())!!
        }
    }

    private class CharCoercion : Coercion {
        override fun coerce(javaValue: Any?): LuaValue {
            val c = javaValue as Char
            return LuaInteger.valueOf(c.code)!!
        }
    }

    private class DoubleCoercion : Coercion {
        override fun coerce(javaValue: Any?): LuaValue {
            val n = javaValue as Number
            return LuaDouble.valueOf(n.toDouble())!!
        }
    }

    private class StringCoercion : Coercion {
        override fun coerce(javaValue: Any?): LuaValue {
            return LuaString.valueOf(javaValue.toString())
        }
    }

    private class BytesCoercion : Coercion {
        override fun coerce(javaValue: Any?): LuaValue {
            return LuaValue.valueOf(javaValue as ByteArray?)
        }
    }

    private class ClassCoercion : Coercion {
        override fun coerce(javaValue: Any?): LuaValue {
            return JavaClass.Companion.forClass(javaValue as Class<*>?)
        }
    }

    private class InstanceCoercion : Coercion {
        override fun coerce(javaValue: Any?): LuaValue {
            return JavaInstance(javaValue)
        }
    }

    private class ArrayCoercion : Coercion {
        override fun coerce(javaValue: Any?): LuaValue {
            // should be userdata? 
            return JavaArray(javaValue)
        }
    }

    private class LuaCoercion : Coercion {
        override fun coerce(javaValue: Any?): LuaValue {
            return javaValue as LuaValue
        }
    }
}
