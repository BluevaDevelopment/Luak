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

import net.blueva.luak.LuaError
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs
import net.blueva.luak.lib.VarArgFunction
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy


/**
 * Subclass of [LibFunction] which implements the features of the luajava package.
 * 
 * 
 * Luajava is an approach to mixing lua and java using simple functions that bind
 * java classes and methods to lua dynamically.  The API is documented on the
 * [luajava](http://www.keplerproject.org/luajava/) documentation pages.
 * 
 * 
 * 
 * Typically, this library is included as part of a call to
 * [JvmPlatform.standardGlobals]
 * <pre> `Globals globals = JvmPlatform.standardGlobals(); System.out.println( globals.get("luajava").get("bindClass").call( LuaValue.valueOf("java.lang.System") ).invokeMethod("currentTimeMillis") ); ` </pre>
 * 
 * 
 * To instantiate and use it directly,
 * link it into your globals table via [Globals.load] using code such as:
 * <pre> `Globals globals = new Globals(); globals.load(new JvmBaseLib()); globals.load(new PackageLib()); globals.load(new LuajavaLib()); globals.load(      "sys = luajava.bindClass('java.lang.System')\n"+      "print ( sys:currentTimeMillis() )\n", "main.lua" ).call(); ` </pre>
 * 
 * 
 * 
 * The `luajava` library is available
 * on all JVM platforms via the call to [JvmPlatform.standardGlobals]
 * and the luajava api's are simply invoked from lua.
 * Because it makes extensive use of Java's reflection API, it is not available
 * on JME, but can be used in Android applications.
 * 
 * 
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 * 
 * @see LibFunction
 * 
 * @see JvmPlatform
 * 
 * @see net.blueva.luak.lib.jme.JmePlatform
 * 
 * @see LuaC
 * 
 * @see CoerceJavaToLua
 * 
 * @see CoerceLuaToJava
 * 
 * @see [http://www.keplerproject.org/luajava/manual.html.luareference](http://www.keplerproject.org/luajava/manual.html.luareference)
 */
class LuajavaLib : VarArgFunction() {
    override fun invoke(args: Varargs): Varargs {
        try {
            when (opcode) {
                INIT -> {
                    // LuaValue modname = args.arg1();
                    val env: LuaValue = args.arg(2)!!
                    val t = LuaTable()
                    bind(t, { LuajavaLib() }, NAMES, BINDCLASS)
                    env.set("luajava", t)
                    if (!env.get("package")!!.isnil()) env.get("package")!!.get("loaded")!!.set("luajava", t)
                    return t
                }

                BINDCLASS -> {
                    val clazz = classForName(args.checkjstring(1))
                    return JavaClass.Companion.forClass(clazz)
                }

                NEWINSTANCE, NEW -> {
                    // get constructor
                    val c: LuaValue = args.checkvalue(1)!!
                    val clazz =
                        (if (opcode == NEWINSTANCE) classForName(c.tojstring()) else c.checkuserdata(Class::class) as Class<*>?)
                    val consargs = args.subargs(2)
                    return JavaClass.Companion.forClass(clazz).constructor!!.invoke(consargs!!)!!
                }

                CREATEPROXY -> {
                    val niface = args.narg() - 1
                    if (niface <= 0) throw LuaError("no interfaces")
                    val lobj: LuaValue = args.checktable(niface + 1)


                    // get the interfaces
                    val ifaces = arrayOfNulls<Class<*>>(niface)
                    var i = 0
                    while (i < niface) {
                        ifaces[i] = classForName(args.checkjstring(i + 1))
                        i++
                    }


                    // create the invocation handler
                    val handler: InvocationHandler = ProxyInvocationHandler(lobj)


                    // create the proxy object
                    val proxy = Proxy.newProxyInstance(javaClass.getClassLoader(), ifaces, handler)


                    // return the proxy
                    return LuaValue.userdataOf(proxy)
                }

                LOADLIB -> {
                    // get constructor
                    val classname = args.checkjstring(1)
                    val methodname = args.checkjstring(2)
                    val clazz = classForName(classname)
                    val method = clazz.getMethod(methodname, *arrayOf<Class<*>?>())
                    val result = method.invoke(clazz, *arrayOf<Any?>())
                    if (result is LuaValue) {
                        return result
                    } else {
                        return NIL
                    }
                }

                else -> throw LuaError("not yet supported: " + this)
            }
        } catch (e: LuaError) {
            throw e
        } catch (ite: InvocationTargetException) {
            throw LuaError(ite.getTargetException())
        } catch (e: Exception) {
            throw LuaError(e)
        }
    }

    // load classes using app loader to allow luaj to be used as an extension
    @Throws(ClassNotFoundException::class)
    protected fun classForName(name: String?): Class<*> {
        return Class.forName(name, true, ClassLoader.getSystemClassLoader())
    }

    private class ProxyInvocationHandler(private val lobj: LuaValue) : InvocationHandler {
        @Throws(Throwable::class)
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            val name = method.name
            val func: LuaValue = lobj.get(name)!!
            if (func.isnil()) return null
            val isvarargs = ((method.modifiers and METHOD_MODIFIERS_VARARGS) != 0)
            val a: Array<out Any?> = args ?: emptyArray<Any?>()
            var n = a.size
            val v: Array<LuaValue?>
            if (isvarargs) {
                val o = a[--n]
                val m = java.lang.reflect.Array.getLength(o)
                v = arrayOfNulls(n + m)
                for (i in 0..<n) v[i] = CoerceJavaToLua.coerce(a[i])
                for (i in 0..<m) v[i + n] = CoerceJavaToLua.coerce(java.lang.reflect.Array.get(o, i))
            } else {
                v = arrayOfNulls(n)
                for (i in 0..<n) v[i] = CoerceJavaToLua.coerce(a[i])
            }
            val result = func.invoke(v)!!.arg1()
            return CoerceLuaToJava.coerce(result, method.returnType)
        }
    }

    companion object {
        const val INIT: Int = 0
        const val BINDCLASS: Int = 1
        const val NEWINSTANCE: Int = 2
        const val NEW: Int = 3
        const val CREATEPROXY: Int = 4
        const val LOADLIB: Int = 5

        val NAMES: kotlin.Array<String?> = arrayOf<String?>(
            "bindClass",
            "newInstance",
            "new",
            "createProxy",
            "loadLib",
        )

        const val METHOD_MODIFIERS_VARARGS: Int = 0x80
    }
}
