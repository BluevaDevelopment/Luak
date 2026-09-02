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

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.blueva.luak.io.OutputStream
import net.blueva.luak.io.PrintStream
import net.blueva.luak.lib.LuaPlatform

/**
 * Standard library functions the port gained on the way from 5.2 to 5.5:
 * `table.move`, `warn`, `coroutine.close`, `coroutine.isyieldable`, and the
 * `collectgarbage` options added in 5.4.
 *
 * Every expectation was taken from the reference interpreter (`lua-5.5.1`).
 */
class StandardLibraryAdditionsTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
    }

    private fun eval(source: String): String =
        globals.load(source, "library-test")!!.call()!!.tojstring()

    @Test
    fun tableMoveCopiesWithinOneTable() {
        assertEquals(
            "1,2,1,2,3",
            eval("return table.concat(table.move({1,2,3,4,5}, 1, 3, 3), ',')"),
        )
    }

    @Test
    fun tableMoveCopiesBetweenTwoTables() {
        assertEquals(
            "1,2,3",
            eval("return table.concat(table.move({1,2,3}, 1, 3, 1, {}), ',')"),
        )
    }

    @Test
    fun tableMoveHandlesOverlapInEitherDirection() {
        assertEquals("2,3,3", eval("return table.concat(table.move({1,2,3}, 2, 3, 1), ',')"))
    }

    @Test
    fun tableMoveRejectsADestinationThatWouldWrapAround() {
        val script = """
            local ok, err = pcall(table.move, {}, 1, 2, math.maxinteger)
            return tostring(ok) .. "|" .. tostring(err)
        """.trimIndent()
        val result = eval(script)
        assertTrue(result.startsWith("false|"), result)
        assertTrue(result.contains("destination wrap around"), result)
    }

    @Test
    fun warnIsSilentUntilItIsSwitchedOn() {
        val recorded = StringBuilder()
        globals.STDERR = PrintStream(object : OutputStream() {
            override fun write(byte: Int) {
                recorded.append(byte.toChar())
            }
        })
        globals.load(
            """
            warn("before")
            warn("@on")
            warn("hello", " world")
            warn("@off")
            warn("after")
            """.trimIndent(),
            "warn-test",
        )!!.call()
        assertEquals("Lua warning: hello world\n", recorded.toString())
    }

    @Test
    fun isyieldableIsFalseOnTheMainThreadAndTrueInsideACoroutine() {
        val script = """
            local outside = coroutine.isyieldable()
            local inside
            local co = coroutine.create(function() inside = coroutine.isyieldable() end)
            coroutine.resume(co)
            return tostring(outside) .. "," .. tostring(inside)
        """.trimIndent()
        assertEquals("false,true", eval(script))
    }

    @Test
    fun closeEndsASuspendedCoroutine() {
        val script = """
            local co = coroutine.create(function() coroutine.yield() end)
            coroutine.resume(co)
            local ok = coroutine.close(co)
            return tostring(ok) .. "," .. coroutine.status(co)
        """.trimIndent()
        assertEquals("true,dead", eval(script))
    }

    @Test
    fun closeRunsTheCoroutinesPendingClosers() {
        val script = """
            local closed = false
            local co = coroutine.create(function()
                local guard <close> = setmetatable({}, {__close = function() closed = true end})
                coroutine.yield()
            end)
            coroutine.resume(co)
            local ok = coroutine.close(co)
            return tostring(ok) .. "," .. tostring(closed)
        """.trimIndent()
        assertEquals("true,true", eval(script))
    }

    @Test
    fun closeReportsAnErrorRaisedByACloser() {
        val script = """
            local co = coroutine.create(function()
                local guard <close> = setmetatable({}, {__close = function() error("bad", 0) end})
                coroutine.yield()
            end)
            coroutine.resume(co)
            local ok, err = coroutine.close(co)
            return tostring(ok) .. "," .. tostring(err)
        """.trimIndent()
        assertEquals("false,bad", eval(script))
    }

    @Test
    fun collectgarbageAnswersTheOptionsAddedIn54() {
        assertEquals("true", eval("return tostring(collectgarbage('isrunning'))"))
        // The mode answered is the one that was in force, not the one asked for.
        assertEquals(
            "generational,incremental",
            eval(
                "local a = collectgarbage('incremental') " +
                    "local b = collectgarbage('generational') " +
                    "return a .. ',' .. b",
            ),
        )
    }
}
