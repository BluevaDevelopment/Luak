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
package net.blueva.luak.conformance

import net.blueva.luak.Globals
import net.blueva.luak.Lua
import net.blueva.luak.LuaValue
import net.blueva.luak.lib.ResourceFinder
import net.blueva.luak.lib.jvm.JvmPlatform
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Scoreboard for the port to Lua 5.5, run against the upstream test suite.
 *
 * This is deliberately **not** a pass/fail gate. It runs every script in
 * PUC-Lua's own `testes/` directory and writes a tally to
 * `build/reports/lua-conformance.txt`, so each phase of the port can be
 * measured instead of asserted. Nothing here fails the build.
 *
 * The suite is not vendored into this repository; point the harness at a
 * checkout with either
 *
 * ```
 * ./gradlew :luak-jvm:test -Dluak.lua.testsuite=/path/to/lua/testes
 * ```
 *
 * or the `LUAK_LUA_TESTSUITE` environment variable. Without it the report
 * is skipped, which is why this cannot break CI.
 *
 * ### Why two numbers
 *
 * Scoring whole scripts as pass/fail is useless early on: 31 of the 34 files
 * use syntax that did not exist in 5.2 (bitwise operators, `//`, `<const>`,
 * `<close>`, `global`), so they fail in the lexer and the score would sit at
 * zero until the very last phase. Splitting *compiles* from *runs* makes the
 * syntax phases visible as they land, and only then does execution start to
 * move.
 *
 * Note that upstream's own driver, `all.lua`, refuses to run unless
 * `_VERSION == "Lua 5.5"`, so the whole-suite run only becomes meaningful once
 * the port is finished. Until then each file is compiled and run on its own.
 */
class LuaConformanceReport {

    @Test
    fun reportConformanceAgainstTheReferenceSuite() {
        val suite = locateSuite()
        if (suite == null) {
            println(
                "lua-conformance: skipped, no reference suite configured " +
                    "(-D$SUITE_PROPERTY=/path/to/lua/testes)",
            )
            return
        }

        val scripts = suite.listFiles { f -> f.isFile && f.name.endsWith(".lua") }
            ?.sortedBy { it.name }
            .orEmpty()
        assertTrue(scripts.isNotEmpty(), "no .lua scripts under $suite")

        val results = scripts.map { evaluate(it, suite) }
        val report = render(suite, results)

        val target = File("build/reports/lua-conformance.txt")
        target.parentFile?.mkdirs()
        target.writeText(report)
        println(report)
    }

    /** Compiles a script, then runs it if it compiled, without ever throwing. */
    private fun evaluate(script: File, suite: File): Result {
        val globals = sandbox(suite)
        val source = script.readBytes()

        try {
            globals.compilePrototype(BufferedInputStream(source.inputStream()), "@${script.name}")
        } catch (failure: Throwable) {
            return Result(script.name, Outcome.COMPILE_FAILED, summarise(failure))
        }

        if (script.name in SKIPPED_AT_RUNTIME) {
            return Result(script.name, Outcome.COMPILED, "run skipped: ${SKIPPED_AT_RUNTIME.getValue(script.name)}")
        }

        // Upstream scripts can loop or allocate without bound, and a plain Lua
        // call is not interruptible, so the runner is a daemon thread that the
        // JVM can abandon at exit.
        var outcome: Result? = null
        val runner = Thread {
            outcome = try {
                globals.load(source.inputStream(), "@${script.name}", "t", globals)!!.call()
                Result(script.name, Outcome.RAN, "")
            } catch (failure: Throwable) {
                Result(script.name, Outcome.RUN_FAILED, summarise(failure))
            }
        }
        runner.isDaemon = true
        runner.start()
        runner.join(RUN_TIMEOUT_MILLIS)
        return outcome ?: Result(script.name, Outcome.TIMED_OUT, "over ${RUN_TIMEOUT_MILLIS}ms")
    }

    /**
     * Standard globals, with the two edges that would take the test JVM down
     * with them closed off: scripts resolve their siblings out of the suite
     * directory rather than the working directory, and `os.exit` raises
     * instead of calling [System.exit].
     */
    private fun sandbox(suite: File): Globals {
        // debugGlobals, not standardGlobals: the reference interpreter's
        // luaL_openlibs includes the debug library, and several suite files
        // require it outright. Luak leaves it out of standardGlobals on
        // purpose, which is a sound choice for embedders but not what the suite
        // is written against.
        val globals = JvmPlatform.debugGlobals()
        globals.finder = ResourceFinder { filename ->
            val name = filename ?: return@ResourceFinder null
            val direct = File(name)
            val candidate = if (direct.isAbsolute) direct else File(suite, name)
            if (candidate.isFile) BufferedInputStream(FileInputStream(candidate)) else null
        }
        globals.get("os")!!.set(
            "exit",
            object : net.blueva.luak.lib.VarArgFunction() {
                override fun invoke(args: net.blueva.luak.Varargs): net.blueva.luak.Varargs =
                    throw net.blueva.luak.LuaError("os.exit called under the conformance harness")
            },
        )
        return globals
    }

    private fun summarise(failure: Throwable): String {
        val message = failure.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        val text = message.ifEmpty { failure::class.simpleName.orEmpty() }
        return if (text.length <= 96) text else text.take(93) + "..."
    }

    private fun render(suite: File, results: List<Result>): String = buildString {
        val compiled = results.count { it.outcome != Outcome.COMPILE_FAILED }
        val ran = results.count { it.outcome == Outcome.RAN }
        appendLine("Luak conformance against the Lua reference suite")
        appendLine("suite:     $suite")
        appendLine("_VERSION:  ${Lua._VERSION}   (${Lua.LUAK_VERSION})")
        appendLine("compiles:  $compiled / ${results.size}")
        appendLine("runs:      $ran / ${results.size}")
        appendLine()
        val width = results.maxOf { it.name.length }
        for (result in results) {
            appendLine("  ${result.name.padEnd(width)}  ${result.outcome.label.padEnd(9)}  ${result.detail}")
        }
    }

    private fun locateSuite(): File? {
        val configured = System.getProperty(SUITE_PROPERTY) ?: System.getenv(SUITE_ENVIRONMENT)
        val suite = configured?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        return suite.takeIf { it.isDirectory }
    }

    private data class Result(val name: String, val outcome: Outcome, val detail: String)

    private enum class Outcome(val label: String) {
        RAN("ran"),
        COMPILED("compiled"),
        COMPILE_FAILED("no-parse"),
        RUN_FAILED("failed"),
        TIMED_OUT("timeout"),
    }

    private companion object {
        const val SUITE_PROPERTY = "luak.lua.testsuite"
        const val SUITE_ENVIRONMENT = "LUAK_LUA_TESTSUITE"
        const val RUN_TIMEOUT_MILLIS = 20_000L

        /**
         * Compiled but not executed. These are upstream's stress cases; they
         * exist to exhaust memory or to drive a second interpreter process, so
         * running them in the test JVM says nothing about conformance.
         */
        val SKIPPED_AT_RUNTIME = mapOf(
            "big.lua" to "allocates multi-gigabyte strings",
            "verybig.lua" to "allocates multi-gigabyte structures",
            "heavy.lua" to "deliberately exhausts memory",
            "memerr.lua" to "deliberately exhausts memory",
            "main.lua" to "spawns interpreter subprocesses",
        )
    }
}

/** Lets [sandbox] build a finder from a lambda. */
private fun ResourceFinder(resolve: (String?) -> InputStream?): ResourceFinder =
    object : ResourceFinder {
        override fun findResource(filename: String?): InputStream? = resolve(filename)
    }
