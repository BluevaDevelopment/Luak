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
package net.blueva.luak.web

import net.blueva.luak.Globals
import net.blueva.luak.LuaError
import net.blueva.luak.lib.LuaPlatform

/*
 * The browser face of Luak: a page loads luak-web.js as a plain script and
 * gets `globalThis.LuakWeb`.
 *
 *   LuakWeb.version                 the Luak release this bundle was built from
 *   LuakWeb.check(source, name?)    null if the source compiles, otherwise
 *                                   { line, message, raw } - what the same
 *                                   Luak on a server would say about it
 *
 * Checking compiles and runs nothing, so no sandbox is involved.
 */

// `[\s\S]` rather than a dot-all flag, which Kotlin/JS regexes do not offer.
private val located = Regex("""^(?:\[string "[^"]*"\]|[^:\s]+):(\d+):\s*([\s\S]*)$""")

private var compiler: Globals? = null

private fun compiler(): Globals = compiler ?: LuaPlatform.standardGlobals().also { compiler = it }

internal fun checkSource(source: String, chunkName: String): dynamic {
    return try {
        compiler().load(source, chunkName)
        null
    } catch (e: LuaError) {
        problem(e.message ?: "syntax error")
    } catch (e: Throwable) {
        problem(e.toString())
    }
}

private fun problem(raw: String): dynamic {
    val result: dynamic = js("({})")
    val match = located.find(raw)
    result.line = match?.groupValues?.get(1)?.toIntOrNull()
    result.message = match?.groupValues?.get(2)?.trim() ?: raw
    result.raw = raw
    return result
}

fun main() {
    val api: dynamic = js("({})")
    api.version = LUAK_WEB_VERSION
    api.check = { source: String?, chunkName: String? -> checkSource(source ?: "", chunkName ?: "main.lua") }
    val root: dynamic = js("globalThis")
    root.LuakWeb = api
}
