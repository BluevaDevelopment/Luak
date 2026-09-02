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
package net.blueva.luak.lib

import net.blueva.luak.Globals
import net.blueva.luak.LoadState
import net.blueva.luak.compiler.LuaC

/**
 * Builds a ready-to-use [Globals] on any Kotlin Multiplatform target.
 *
 * This is the entry point to reach for first: it loads the Lua 5.5 standard
 * libraries in the right order and installs both the source compiler and the
 * binary-chunk undumper, so `load`, `loadfile`, `require`, and `string.dump`
 * round-trips all work out of the box.
 *
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * globals.load("print('hello, world')").call()
 * ```
 *
 * The same set of libraries is available on JVM, JavaScript, Wasm (both the
 * JS-hosted and the WASI flavour), and Kotlin/Native. Where a host genuinely
 * cannot provide something - a browser with no filesystem, a WASI module with
 * no pre-opened directory, `io.popen` anywhere outside the JVM - the affected
 * function reports the failure the way Lua does, returning `nil` plus a
 * message instead of being absent or throwing.
 *
 * On the JVM, [net.blueva.luak.lib.jvm.JvmPlatform] in the `luak-jvm`
 * module builds on the same libraries and adds the JVM-only pieces:
 * `luajava`, subprocess support behind `io.popen` and `os.execute`, and
 * `java.util.Formatter`-backed `string.format`.
 *
 * @see Globals
 * @see net.blueva.luak.lib.jvm.JvmPlatform
 */
object LuaPlatform {
    /**
     * Creates a [Globals] with the Lua 5.5 standard libraries: `base`,
     * `package`, `table`, `string`, `coroutine`, `math`, `utf8`, `io`, and
     * `os`, plus the [LuaC] compiler and the [LoadState] undumper.
     *
     * `bit32` is not among them: it was deprecated in 5.3 and removed in 5.4,
     * having no purpose once integers are 64 bits wide and the operators are
     * built into the language. [Bit32Lib] is still there for an embedder that
     * wants to load it back.
     *
     * @return globals initialized with the standard libraries
     * @see debugGlobals
     */
    fun standardGlobals(): Globals {
        val globals = Globals()
        globals.load(BaseLib())
        globals.load(PackageLib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(CoroutineLib())
        globals.load(MathLib())
        globals.load(Utf8Lib())
        globals.load(IoLib())
        globals.load(OsLib())
        LoadState.install(globals)
        LuaC.install(globals)
        return globals
    }

    /**
     * [standardGlobals] plus the `debug` library.
     *
     * @return globals initialized with the standard and debug libraries
     * @see standardGlobals
     * @see DebugLib
     */
    fun debugGlobals(): Globals {
        val globals = standardGlobals()
        globals.load(DebugLib())
        return globals
    }
}
