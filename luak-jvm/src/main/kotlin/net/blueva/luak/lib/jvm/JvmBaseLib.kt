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
 *  Copyright (c) 2026 Blueva Development
 *
 *  SPDX-License-Identifier: MIT
 ******************************************************************************/
package net.blueva.luak.lib.jvm

import net.blueva.luak.lib.BaseLib

/**
 * Retained name for what is now plain [BaseLib].
 *
 * The two things this class used to add on the JVM - wiring [Globals.STDIN] to
 * standard input, and looking for a script as a file before falling back to the
 * classpath - are part of the shared [BaseLib] and work on every Kotlin
 * Multiplatform target.
 */
@Deprecated(
    "JvmBaseLib no longer adds anything to BaseLib; use BaseLib directly.",
    ReplaceWith("BaseLib", "net.blueva.luak.lib.BaseLib"),
)
typealias JvmBaseLib = BaseLib
