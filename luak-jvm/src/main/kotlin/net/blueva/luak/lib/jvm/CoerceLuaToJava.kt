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

import net.blueva.luak.LuaString
import net.blueva.luak.LuaValue
import java.lang.Boolean
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Long
import java.lang.Short
import java.lang.reflect.Array
import java.util.*
import kotlin.Any
import kotlin.ByteArray
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.arrayOf
import kotlin.code
import kotlin.math.min

/**
 * Helper class to coerce values from lua to Java within the luajava library.
 * 
 * 
 * This class is primarily used by the [LuajavaLib],
 * but can also be used directly when working with Java/lua bindings.
 * 
 * 
 * To coerce to specific Java values, generally the `toType()` methods
 * on [LuaValue] may be used:
 * 
 *  * [LuaValue.toboolean]
 *  * [LuaValue.tobyte]
 *  * [LuaValue.tochar]
 *  * [LuaValue.toshort]
 *  * [LuaValue.toint]
 *  * [LuaValue.tofloat]
 *  * [LuaValue.todouble]
 *  * [LuaValue.tojstring]
 *  * [LuaValue.touserdata]
 *  * [LuaValue.touserdata]
 * 
 * 
 * 
 * For data in lua tables, the various methods on [LuaTable] can be used directly
 * to convert data to something more useful.
 * 
 * @see LuajavaLib
 * 
 * @see CoerceJavaToLua
 */
object CoerceLuaToJava {
    var SCORE_NULL_VALUE: Int = 0x10
    var SCORE_WRONG_TYPE: Int = 0x100
    @JvmField
    var SCORE_UNCOERCIBLE: Int = 0x10000

    /**
     * Coerce a LuaValue value to a specified java class
     * @param value LuaValue to coerce
     * @param clazz Class to coerce into
     * @return Object of type clazz (or a subclass) with the corresponding value.
     */
	@JvmStatic
    fun coerce(value: LuaValue?, clazz: Class<*>): Any? {
        return getCoercion(clazz).coerce(value!!)
    }

    val COERCIONS: MutableMap<Class<*>, Any?> = Collections.synchronizedMap<Class<*>, Any?>(HashMap<Class<*>, Any?>())

    /**
     * Determine levels of inheritance between a base class and a subclass
     * @param baseclass base class to look for
     * @param subclass class from which to start looking
     * @return number of inheritance levels between subclass and baseclass,
     * or SCORE_UNCOERCIBLE if not a subclass
     */
	@JvmStatic
    fun inheritanceLevels(baseclass: Class<*>?, subclass: Class<*>?): Int {
        if (subclass == null) return SCORE_UNCOERCIBLE
        if (baseclass == subclass) return 0
        var min = min(SCORE_UNCOERCIBLE, inheritanceLevels(baseclass, subclass.getSuperclass()) + 1)
        val ifaces = subclass.getInterfaces()
        for (i in ifaces.indices) min = min(min, inheritanceLevels(baseclass, ifaces[i]) + 1)
        return min
    }

    init {
        val boolCoercion: Coercion = BoolCoercion()
        val byteCoercion: Coercion = NumericCoercion(NumericCoercion.TARGET_TYPE_BYTE)
        val charCoercion: Coercion = NumericCoercion(NumericCoercion.TARGET_TYPE_CHAR)
        val shortCoercion: Coercion = NumericCoercion(NumericCoercion.TARGET_TYPE_SHORT)
        val intCoercion: Coercion = NumericCoercion(NumericCoercion.TARGET_TYPE_INT)
        val longCoercion: Coercion = NumericCoercion(NumericCoercion.TARGET_TYPE_LONG)
        val floatCoercion: Coercion = NumericCoercion(NumericCoercion.TARGET_TYPE_FLOAT)
        val doubleCoercion: Coercion = NumericCoercion(NumericCoercion.TARGET_TYPE_DOUBLE)
        val stringCoercion: Coercion = StringCoercion(StringCoercion.Companion.TARGET_TYPE_STRING)
        val bytesCoercion: Coercion = StringCoercion(StringCoercion.Companion.TARGET_TYPE_BYTES)

        COERCIONS.put(kotlin.Boolean::class.javaPrimitiveType!!, boolCoercion)
        COERCIONS.put(kotlin.Boolean::class.javaObjectType, boolCoercion)
        COERCIONS.put(kotlin.Byte::class.javaPrimitiveType!!, byteCoercion)
        COERCIONS.put(kotlin.Byte::class.javaObjectType, byteCoercion)
        COERCIONS.put(Char::class.javaPrimitiveType!!, charCoercion)
        COERCIONS.put(Char::class.javaObjectType, charCoercion)
        COERCIONS.put(kotlin.Short::class.javaPrimitiveType!!, shortCoercion)
        COERCIONS.put(kotlin.Short::class.javaObjectType, shortCoercion)
        COERCIONS.put(Int::class.javaPrimitiveType!!, intCoercion)
        COERCIONS.put(Int::class.javaObjectType, intCoercion)
        COERCIONS.put(kotlin.Long::class.javaPrimitiveType!!, longCoercion)
        COERCIONS.put(kotlin.Long::class.javaObjectType, longCoercion)
        COERCIONS.put(kotlin.Float::class.javaPrimitiveType!!, floatCoercion)
        COERCIONS.put(kotlin.Float::class.javaObjectType, floatCoercion)
        COERCIONS.put(kotlin.Double::class.javaPrimitiveType!!, doubleCoercion)
        COERCIONS.put(kotlin.Double::class.javaObjectType, doubleCoercion)
        COERCIONS.put(String::class.java, stringCoercion)
        COERCIONS.put(ByteArray::class.java, bytesCoercion)
    }

    @JvmStatic
    internal fun getCoercion(c: Class<*>): Coercion {
        var co = COERCIONS.get(c) as Coercion?
        if (co != null) {
            return co
        }
        if (c.isArray()) {
            val typ = c.getComponentType()
            co = ArrayCoercion(c.getComponentType())
        } else {
            co = ObjectCoercion(c)
        }
        COERCIONS.put(c, co)
        return co
    }

    internal interface Coercion {
        fun score(value: LuaValue): Int
        fun coerce(value: LuaValue): Any?
    }

    internal class BoolCoercion : Coercion {
        override fun toString(): String {
            return "BoolCoercion()"
        }

        override fun score(value: LuaValue): Int {
            when (value.type()) {
                LuaValue.TBOOLEAN -> return 0
            }
            return 1
        }

        override fun coerce(value: LuaValue): Any {
            return if (value.toboolean()) Boolean.TRUE else Boolean.FALSE
        }
    }

    internal class NumericCoercion(val targetType: Int) : Coercion {
        override fun toString(): String {
            return "NumericCoercion(" + TYPE_NAMES[targetType] + ")"
        }

        override fun score(value: LuaValue): Int {
            var value = value
            var fromStringPenalty = 0
            if (value.type() == LuaValue.TSTRING) {
                value = value.tonumber()
                if (value.isnil()) {
                    return SCORE_UNCOERCIBLE
                }
                fromStringPenalty = 4
            }
            if (value.isint()) {
                when (targetType) {
                    TARGET_TYPE_BYTE -> {
                        val i = value.toint()
                        return fromStringPenalty + (if (i == i.toByte().toInt()) 0 else SCORE_WRONG_TYPE)
                    }

                    TARGET_TYPE_CHAR -> {
                        val i = value.toint()
                        return fromStringPenalty + (if (i == i.toByte()
                                .toInt()
                        ) 1 else if (i == i.toChar().code) 0 else SCORE_WRONG_TYPE)
                    }

                    TARGET_TYPE_SHORT -> {
                        val i = value.toint()
                        return fromStringPenalty +
                                (if (i == i.toByte().toInt()) 1 else if (i == i.toShort()
                                        .toInt()
                                ) 0 else SCORE_WRONG_TYPE)
                    }

                    TARGET_TYPE_INT -> {
                        val i = value.toint()
                        return fromStringPenalty +
                                (if (i == i.toByte().toInt()) 2 else if ((i == i.toChar().code) || (i == i.toShort()
                                        .toInt())
                                ) 1 else 0)
                    }

                    TARGET_TYPE_FLOAT -> return fromStringPenalty + 1
                    TARGET_TYPE_LONG -> return fromStringPenalty + 1
                    TARGET_TYPE_DOUBLE -> return fromStringPenalty + 2
                    else -> return SCORE_WRONG_TYPE
                }
            } else if (value.isnumber()) {
                when (targetType) {
                    TARGET_TYPE_BYTE -> return SCORE_WRONG_TYPE
                    TARGET_TYPE_CHAR -> return SCORE_WRONG_TYPE
                    TARGET_TYPE_SHORT -> return SCORE_WRONG_TYPE
                    TARGET_TYPE_INT -> return SCORE_WRONG_TYPE
                    TARGET_TYPE_LONG -> {
                        val d = value.todouble()
                        return fromStringPenalty + (if (d == d.toLong().toDouble()) 0 else SCORE_WRONG_TYPE)
                    }

                    TARGET_TYPE_FLOAT -> {
                        val d = value.todouble()
                        return fromStringPenalty + (if (d == d.toFloat().toDouble()) 0 else SCORE_WRONG_TYPE)
                    }

                    TARGET_TYPE_DOUBLE -> {
                        val d = value.todouble()
                        return fromStringPenalty + (if ((d == d.toLong().toDouble()) || (d == d.toFloat()
                                .toDouble())
                        ) 1 else 0)
                    }

                    else -> return SCORE_WRONG_TYPE
                }
            } else {
                return SCORE_UNCOERCIBLE
            }
        }

        override fun coerce(value: LuaValue): Any? {
            when (targetType) {
                TARGET_TYPE_BYTE -> return value.toint().toByte()
                TARGET_TYPE_CHAR -> return value.toint().toChar()
                TARGET_TYPE_SHORT -> return value.toint().toShort()
                TARGET_TYPE_INT -> return value.toint()
                TARGET_TYPE_LONG -> return value.todouble().toLong()
                TARGET_TYPE_FLOAT -> return value.todouble().toFloat()
                TARGET_TYPE_DOUBLE -> return value.todouble()
                else -> return null
            }
        }

        companion object {
            const val TARGET_TYPE_BYTE: Int = 0
            const val TARGET_TYPE_CHAR: Int = 1
            const val TARGET_TYPE_SHORT: Int = 2
            const val TARGET_TYPE_INT: Int = 3
            const val TARGET_TYPE_LONG: Int = 4
            const val TARGET_TYPE_FLOAT: Int = 5
            const val TARGET_TYPE_DOUBLE: Int = 6
            val TYPE_NAMES: kotlin.Array<String> = arrayOf<String>("byte", "char", "short", "int", "long", "float", "double")
        }
    }

    internal class StringCoercion(val targetType: Int) : Coercion {
        override fun toString(): String {
            return "StringCoercion(" + (if (targetType == TARGET_TYPE_STRING) "String" else "byte[]") + ")"
        }

        override fun score(value: LuaValue): Int {
            when (value.type()) {
                LuaValue.TSTRING -> return if (value.checkstring()!!
                        .isValidUtf8
                ) (if (targetType == TARGET_TYPE_STRING) 0 else 1) else (if (targetType == TARGET_TYPE_BYTES) 0 else SCORE_WRONG_TYPE)

                LuaValue.TNIL -> return SCORE_NULL_VALUE
                else -> return if (targetType == TARGET_TYPE_STRING) SCORE_WRONG_TYPE else SCORE_UNCOERCIBLE
            }
        }

        override fun coerce(value: LuaValue): Any? {
            if (value.isnil()) return null
            if (targetType == TARGET_TYPE_STRING) return value.tojstring()
            val s: LuaString = value.checkstring()!!
            val b = ByteArray(s.m_length)
            s.copyInto(0, b, 0, b.size)
            return b
        }

        companion object {
            const val TARGET_TYPE_STRING: Int = 0
            const val TARGET_TYPE_BYTES: Int = 1
        }
    }

    internal class ArrayCoercion(val componentType: Class<*>) : Coercion {
        val componentCoercion: Coercion

        init {
            this.componentCoercion = getCoercion(componentType)
        }

        override fun toString(): String {
            return "ArrayCoercion(" + componentType.getName() + ")"
        }

        override fun score(value: LuaValue): Int {
            when (value.type()) {
                LuaValue.TTABLE -> return if (value.length() == 0) 0 else componentCoercion.score(value.get(1)!!)
                LuaValue.TUSERDATA -> return inheritanceLevels(
                    componentType,
                    value.touserdata()!!.javaClass.getComponentType()
                )

                LuaValue.TNIL -> return SCORE_NULL_VALUE
                else -> return SCORE_UNCOERCIBLE
            }
        }

        override fun coerce(value: LuaValue): Any? {
            when (value.type()) {
                LuaValue.TTABLE -> {
                    val n = value.length()
                    val a = Array.newInstance(componentType, n)
                    var i = 0
                    while (i < n) {
                        Array.set(a, i, componentCoercion.coerce(value.get(i + 1)!!))
                        i++
                    }
                    return a
                }

                LuaValue.TUSERDATA -> return value.touserdata()
                LuaValue.TNIL -> return null
                else -> return null
            }
        }
    }

    internal class ObjectCoercion(val targetType: Class<*>) : Coercion {
        override fun toString(): String {
            return "ObjectCoercion(" + targetType.getName() + ")"
        }

        override fun score(value: LuaValue): Int {
            when (value.type()) {
                LuaValue.TNUMBER -> return inheritanceLevels(
                    targetType,
                    if (value.isint()) Int::class.java else kotlin.Double::class.java
                )

                LuaValue.TBOOLEAN -> return inheritanceLevels(targetType, kotlin.Boolean::class.java)
                LuaValue.TSTRING -> return inheritanceLevels(targetType, String::class.java)
                LuaValue.TUSERDATA -> return inheritanceLevels(targetType, value.touserdata()!!.javaClass)
                LuaValue.TNIL -> return SCORE_NULL_VALUE
                else -> return inheritanceLevels(targetType, value.javaClass)
            }
        }

        override fun coerce(value: LuaValue): Any? {
            when (value.type()) {
                LuaValue.TNUMBER -> return if (value.isint()) value.toint() as Any else value.todouble() as Any
                LuaValue.TBOOLEAN -> return if (value.toboolean()) Boolean.TRUE else Boolean.FALSE
                LuaValue.TSTRING -> return value.tojstring()
                LuaValue.TUSERDATA -> return value.optuserdata(targetType.kotlin, null)
                LuaValue.TNIL -> return null
                else -> return value
            }
        }
    }
}
