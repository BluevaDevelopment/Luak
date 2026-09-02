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

import net.blueva.luak.lib.StringLib

/**
 * The `string` library on the JVM.
 *
 * This once routed `string.format`'s float conversions through
 * `java.util.Formatter`, which follows the JVM's default locale and so
 * rendered `%.2f` of 3.14159 as "3,14" wherever that locale uses a comma. The
 * shared [StringLib] now renders those conversions itself, identically on
 * every target, and nothing JVM-specific is left here.
 */
@Deprecated(
    "The shared StringLib is now used on every target, including the JVM.",
    ReplaceWith("StringLib()", "net.blueva.luak.lib.StringLib"),
)
class JvmStringLib
/** public constructor  */
    : StringLib()
