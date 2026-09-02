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

import net.blueva.luak.lib.MathLib

/**
 * Retained name for what is now plain [MathLib].
 *
 * The trigonometric, hyperbolic, logarithmic, and power functions this class
 * used to add on top of LuaJ's reduced J2ME math library are implemented in
 * the shared [MathLib] with `kotlin.math`, so every target has them.
 */
@Deprecated(
    "JvmMathLib no longer adds anything to MathLib; use MathLib directly.",
    ReplaceWith("MathLib", "net.blueva.luak.lib.MathLib"),
)
typealias JvmMathLib = MathLib
