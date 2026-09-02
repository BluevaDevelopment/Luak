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
package net.blueva.luak

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.blueva.luak.lib.LuaPlatform

/**
 * `_VERSION` names the Lua *language*, never the implementation.
 *
 * Programs in the wild branch on it (`if _VERSION == "Lua 5.4" then ...`) and
 * the reference test suite reads it to decide which cases apply. Luak used
 * to report its own release here (`"Luak 26.5"`), which no conforming
 * program can make sense of; these tests keep it from drifting back.
 */
class LanguageVersionTest {
    @Test
    fun versionGlobalNamesTheLanguage() {
        val globals = LuaPlatform.standardGlobals()
        val version = globals.get("_VERSION")!!.checkjstring()!!
        assertTrue(
            Regex("""^Lua \d+\.\d+$""").matches(version),
            "_VERSION must look like \"Lua 5.4\", was \"$version\"",
        )
    }

    @Test
    fun versionGlobalMatchesTheConstant() {
        val globals = LuaPlatform.standardGlobals()
        assertEquals(Lua._VERSION, globals.get("_VERSION")!!.checkjstring())
    }

    @Test
    fun theImplementationVersionIsReportedSeparately() {
        // Both exist and say different things; conflating them is the bug this
        // suite guards against.
        assertTrue(Lua.LUAK_VERSION.startsWith("Luak"), Lua.LUAK_VERSION)
        assertTrue(Lua._VERSION.startsWith("Lua "), Lua._VERSION)
    }
}
