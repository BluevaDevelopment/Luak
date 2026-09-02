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
package net.blueva.luak.lib

import net.blueva.luak.io.InputStream

/**
 * Interface for opening application resource files such as script sources.
 *
 *
 * This is what `require`, `dofile`, and `loadfile` use to find files that are
 * part of the application. [BaseLib] implements it for every target: an
 * ordinary host file first, then the platform's own resource namespace - the
 * classpath on the JVM, the working directory or a pre-opened directory
 * elsewhere.
 *
 *
 * Install your own to load scripts from somewhere else entirely (an archive,
 * an asset bundle, a network cache) by assigning it to [Globals.finder] after
 * the libraries are loaded.
 *
 *
 * The `io` library does not use this API for file manipulation.
 *
 *
 * @see BaseLib
 *
 * @see Globals.finder
 *
 * @see net.blueva.luak.lib.LuaPlatform
 */
interface ResourceFinder {
    /**
     * Try to open a file, or return null if not found.
     *
     * @param filename name of the resource to open
     * @return InputStream, or null if not found.
     */
    fun findResource(filename: String?): InputStream?
}
