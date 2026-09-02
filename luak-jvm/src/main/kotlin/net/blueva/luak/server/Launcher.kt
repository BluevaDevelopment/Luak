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

import java.io.InputStream
import java.io.Reader

/** Interface to launch lua scripts using the [LuaClassLoader].
 * <P>
 * *Note: This class is experimental and subject to change in future versions.*
</P> * <P>
 * This interface is purposely genericized to defer class loading so that
 * luaj classes can come from the class loader.
</P> * <P>
 * The implementation should be acquired using [LuaClassLoader.NewLauncher]
 * or [LuaClassLoader.NewLauncher] which ensure that the classes are
 * loaded to give each Launcher instance a pristine set of Globals, including
 * the shared metatables.
 * 
 * @see LuaClassLoader
 * 
 * @see LuaClassLoader.NewLauncher
 * @see LuaClassLoader.NewLauncher
 * @see DefaultLauncher
 * 
 * @since luaj 3.0.1
</P> */
interface Launcher {
    /** Launch a script contained in a String.
     * 
     * @param script The script contents.
     * @param arg Optional arguments supplied to the script.
     * @return return values from the script.
     */
    fun launch(script: String?, arg: Array<Any?>?): Array<Any?>?

    /** Launch a script from an InputStream.
     * 
     * @param script The script as an InputStream.
     * @param arg Optional arguments supplied to the script.
     * @return return values from the script.
     */
    fun launch(script: InputStream?, arg: Array<Any?>?): Array<Any?>?

    /** Launch a script from a Reader.
     * 
     * @param script The script as a Reader.
     * @param arg Optional arguments supplied to the script.
     * @return return values from the script.
     */
    fun launch(script: Reader?, arg: Array<Any?>?): Array<Any?>?
}