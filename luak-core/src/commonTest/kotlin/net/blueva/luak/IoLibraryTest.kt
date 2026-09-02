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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.blueva.luak.io.platformFilesSupported
import net.blueva.luak.lib.LuaPlatform

/**
 * The `io` and file-backed `os` functions, exercised through Lua on whatever
 * filesystem the current target has.
 *
 * A host may legitimately have none - a browser, or a WASI module started
 * without a pre-opened directory - so the file cases run only when
 * [platformFilesSupported] says so; the "no filesystem" contract itself is
 * covered by [openingAMissingFileReportsNilAndAMessage], which must hold
 * either way.
 */
class IoLibraryTest {
    private lateinit var globals: Globals

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
    }

    private fun eval(script: String): Varargs = globals.load(script, "io-test")!!.invoke()

    @Test
    fun openingAMissingFileReportsNilAndAMessage() {
        val result = eval("return io.open('no-such-file-4b1c9e.lua', 'r')")
        assertTrue(result.isnil(1), "io.open on a missing file must return nil")
        assertTrue(result.narg() >= 2, "io.open must also return a message")
        assertTrue(result.checkjstring(2).isNotEmpty())
    }

    @Test
    fun writeThenReadBackRoundTrips() {
        if (!platformFilesSupported) return
        val result = eval(
            """
            local path = os.tmpname()
            local out = assert(io.open(path, 'w'))
            out:write('alpha\n', 'beta\n')
            out:close()
            local input = assert(io.open(path, 'r'))
            local all = input:read('*a')
            input:close()
            os.remove(path)
            return all
            """.trimIndent(),
        )
        assertEquals("alpha\nbeta\n", result.checkjstring(1))
    }

    @Test
    fun readFormatsAndLinesIterateTheFile() {
        if (!platformFilesSupported) return
        val result = eval(
            """
            local path = os.tmpname()
            local out = assert(io.open(path, 'w'))
            out:write('first\nsecond\n42\n')
            out:close()

            local input = assert(io.open(path, 'r'))
            local first = input:read('*l')
            local second = input:read('*l')
            local number = input:read('*n')
            input:close()

            local joined = {}
            for line in io.lines(path) do joined[#joined + 1] = line end
            os.remove(path)
            return first, second, number, table.concat(joined, '|')
            """.trimIndent(),
        )
        assertEquals("first", result.checkjstring(1))
        assertEquals("second", result.checkjstring(2))
        assertEquals(42, result.arg(3).checkint())
        assertEquals("first|second|42", result.checkjstring(4))
    }

    @Test
    fun seekAndAppendMovePositionAsInC() {
        if (!platformFilesSupported) return
        val result = eval(
            """
            local path = os.tmpname()
            local out = assert(io.open(path, 'w'))
            out:write('0123456789')
            out:close()

            local appended = assert(io.open(path, 'a'))
            appended:write('AB')
            appended:close()

            local input = assert(io.open(path, 'r'))
            local size = input:seek('end')
            input:seek('set', 4)
            local middle = input:read(3)
            local here = input:seek('cur', 0)
            input:close()
            os.remove(path)
            return size, middle, here
            """.trimIndent(),
        )
        assertEquals(12, result.arg(1).checkint())
        assertEquals("456", result.checkjstring(2))
        assertEquals(7, result.arg(3).checkint())
    }

    @Test
    fun ioTypeTracksOpenAndClosedFiles() {
        if (!platformFilesSupported) return
        val result = eval(
            """
            local path = os.tmpname()
            local file = assert(io.open(path, 'w'))
            local whenOpen = io.type(file)
            file:close()
            local whenClosed = io.type(file)
            os.remove(path)
            return whenOpen, whenClosed, io.type('not a file')
            """.trimIndent(),
        )
        assertEquals("file", result.checkjstring(1))
        assertEquals("closed file", result.checkjstring(2))
        assertTrue(result.isnil(3))
    }

    @Test
    fun tmpfileIsWritableAndReadable() {
        if (!platformFilesSupported) return
        val result = eval(
            """
            local file = assert(io.tmpfile())
            file:write('scratch')
            file:seek('set', 0)
            local back = file:read('*a')
            file:close()
            return back
            """.trimIndent(),
        )
        assertEquals("scratch", result.checkjstring(1))
    }

    @Test
    fun osRenameAndRemoveActOnRealFiles() {
        if (!platformFilesSupported) return
        val result = eval(
            """
            local from = os.tmpname()
            local to = from .. '.renamed'
            local out = assert(io.open(from, 'w'))
            out:write('moved')
            out:close()

            assert(os.rename(from, to))
            local missing = io.open(from, 'r')
            local moved = assert(io.open(to, 'r'))
            local text = moved:read('*a')
            moved:close()
            assert(os.remove(to))
            local gone = io.open(to, 'r')
            return text, missing == nil, gone == nil
            """.trimIndent(),
        )
        assertEquals("moved", result.checkjstring(1))
        assertTrue(result.arg(2).toboolean())
        assertTrue(result.arg(3).toboolean())
    }

    @Test
    fun loadfileAndDofileResolvePlainRelativeNames() {
        if (!platformFilesSupported) return
        // A relative script name has to resolve through the host filesystem on
        // every target, not only where a JVM classpath exists.
        val path = eval("return os.tmpname()").checkjstring(1)
        assertNotNull(path)
        eval(
            """
            local out = assert(io.open([[$path]], 'w'))
            out:write('return 6 * 7')
            out:close()
            """.trimIndent(),
        )
        val loaded = eval("return dofile([[$path]])")
        eval("os.remove([[$path]])")
        assertEquals(42, loaded.arg(1).checkint())
    }

    @Test
    fun popenReportsNilAndAMessageInTheSharedLibrary() {
        // Spawning a process has no portable implementation, so the shared
        // IoLib answers the way Lua does for a failed io call. JvmIoLib in
        // luak-jvm overrides it with a real one.
        val result = eval("return io.popen('echo hi', 'r')")
        assertTrue(result.isnil(1))
        assertTrue(result.checkjstring(2).isNotEmpty())
    }
}
