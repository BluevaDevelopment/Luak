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
package net.blueva.luak.server

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Class loader that can be used to launch a lua script in a Java VM that has a
 * unique set of classes for Luak classes.
 * <P>
 * *Note: This class is experimental and subject to change in future versions.*
</P> * <P>
 * By using a custom class loader per script, it allows the script to have
 * its own set of globals, including static values such as shared metatables
 * that cannot access lua values from other scripts because their classes are
 * loaded from different class loaders.  Thus normally unsafe libraries such
 * as `luajava` can be exposed to scripts in a server environment using these
 * techniques.
</P> * <P>
 * All classes in the package "net.blueva.luak." are considered user classes, and
 * loaded into this class loader from their bytes in the class path. Other
 * classes are considered systemc classes and loaded via the system loader. This
 * class set can be extended by overriding [.isUserClass].
</P> * <P>
 * The [Launcher] interface is loaded as a system class by exception so
 * that the caller may use it to launch lua scripts.
</P> * <P>
 * By default [.NewLauncher] creates a subclass of [Launcher] of
 * type [DefaultLauncher] which creates debug globals, runs the script,
 * and prints the return values. This behavior can be changed by supplying a
 * different implementation class to [.NewLauncher] which must
 * extend [Launcher].
 * 
 * @see Launcher
 * 
 * @see .NewLauncher
 * @see .NewLauncher
 * @see DefaultLauncher
 * 
 * @since luaj 3.0.1
</P> */
class LuaClassLoader : ClassLoader() {
    /** Local cache of classes loaded by this loader.  */
    var classes: MutableMap<String?, Class<*>?> = HashMap<String?, Class<*>?>()

    @Throws(ClassNotFoundException::class)
    override fun loadClass(classname: String): Class<*>? {
        if (classes.containsKey(classname)) return classes.get(classname)
        if (!isUserClass(classname)) return super.findSystemClass(classname)
        return loadAsUserClass(classname)
    }

    @Throws(ClassNotFoundException::class)
    private fun loadAsUserClass(classname: String): Class<*>? {
        val path = classname.replace('.', '/') + ".class"
        val `is` = getResourceAsStream(path)
        if (`is` != null) {
            try {
                val baos = ByteArrayOutputStream()
                val b = ByteArray(1024)
                var n = 0
                while ((`is`.read(b).also { n = it }) >= 0) {
                    baos.write(b, 0, n)
                }
                val bytes = baos.toByteArray()
                val result = super.defineClass(
                    classname, bytes, 0,
                    bytes.size
                )
                classes.put(classname, result)
                return result
            } catch (e: IOException) {
                throw ClassNotFoundException(
                    ("Read failed: " + classname
                            + ": " + e)
                )
            }
        }
        throw ClassNotFoundException("Not found: " + classname)
    }

    companion object {
        /** String describing the Luak packages to consider part of the user classes  */
        const val luaPackageRoot: String = "net.blueva.luak."

        /** String describing the Launcher interface to be considered a system class  */
        val launcherInterfaceRoot: String = Launcher::class.java.getName()

        /**
         * Construct a [Launcher] instance that will load classes in
         * its own [LuaClassLoader] using a user-supplied implementation class
         * that implements [Launcher].
         * <P>
         * The [Launcher] that is returned will be a pristine Luak vm
         * whose classes are loaded into this loader including static variables
         * such as shared metatables, and should not be able to directly access
         * variables from other Launcher instances.
         * 
         * @return instance of type 'launcher_class' that can be used to launch scripts.
         * @throws InstantiationException
         * @throws IllegalAccessException
         * @throws ClassNotFoundException
        </P> */
        /**
         * Construct a default [Launcher] instance that will load classes in
         * its own [LuaClassLoader] using the default implementation class
         * [DefaultLauncher].
         * <P>
         * The [Launcher] that is returned will be a pristine Luak vm
         * whose classes are loaded into this loader including static variables
         * such as shared metatables, and should not be able to directly access
         * variables from other Launcher instances.
         * 
         * @return [Launcher] instance that can be used to launch scripts.
         * @throws InstantiationException
         * @throws IllegalAccessException
         * @throws ClassNotFoundException
        </P> */
        @JvmOverloads
        @Throws(InstantiationException::class, IllegalAccessException::class, ClassNotFoundException::class)
        fun NewLauncher(launcher_class: Class<out Launcher?> = DefaultLauncher::class.java): Launcher {
            val loader = LuaClassLoader()
            val instance: Any = loader.loadAsUserClass(launcher_class.getName())!!
                .newInstance()
            return instance as Launcher
        }

        /**
         * Test if a class name should be considered a user class and loaded
         * by this loader, or a system class and loaded by the system loader.
         * @param classname Class name to test.
         * @return true if this should be loaded into this class loader.
         */
        fun isUserClass(classname: String): Boolean {
            return classname.startsWith(luaPackageRoot)
                    && !classname.startsWith(launcherInterfaceRoot)
        }
    }
}
