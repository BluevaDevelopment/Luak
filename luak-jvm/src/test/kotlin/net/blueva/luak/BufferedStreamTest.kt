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
package net.blueva.luak

import junit.framework.TestCase
import java.io.ByteArrayInputStream
import java.io.IOException

class BufferedStreamTest : TestCase() {
    private fun NewBufferedStream(buflen: Int, contents: String): Globals.BufferedStream {
        return Globals.BufferedStream(buflen, ByteArrayInputStream(contents.toByteArray()))
    }

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
    }

    @Throws(IOException::class)
    fun testReadEmptyStream() {
        val bs = NewBufferedStream(4, "")
        TestCase.assertEquals(-1, bs.read())
        TestCase.assertEquals(-1, bs.read(ByteArray(10)))
        TestCase.assertEquals(-1, bs.read(ByteArray(10), 0, 10))
    }

    @Throws(IOException::class)
    fun testReadByte() {
        val bs = NewBufferedStream(2, "abc")
        TestCase.assertEquals('a'.code, bs.read())
        TestCase.assertEquals('b'.code, bs.read())
        TestCase.assertEquals('c'.code, bs.read())
        TestCase.assertEquals(-1, bs.read())
    }

    @Throws(IOException::class)
    fun testReadByteArray() {
        val array = ByteArray(3)
        val bs = NewBufferedStream(4, "abcdef")
        TestCase.assertEquals(3, bs.read(array))
        TestCase.assertEquals("abc", String(array))
        TestCase.assertEquals(1, bs.read(array))
        TestCase.assertEquals("d", String(array, 0, 1))
        TestCase.assertEquals(2, bs.read(array))
        TestCase.assertEquals("ef", String(array, 0, 2))
        TestCase.assertEquals(-1, bs.read())
    }

    @Throws(IOException::class)
    fun testReadByteArrayOffsetLength() {
        val array = ByteArray(10)
        val bs = NewBufferedStream(8, "abcdefghijklmn")
        TestCase.assertEquals(4, bs.read(array, 0, 4))
        TestCase.assertEquals("abcd", String(array, 0, 4))
        TestCase.assertEquals(4, bs.read(array, 2, 8))
        TestCase.assertEquals("efgh", String(array, 2, 4))
        TestCase.assertEquals(6, bs.read(array, 0, 10))
        TestCase.assertEquals("ijklmn", String(array, 0, 6))
        TestCase.assertEquals(-1, bs.read())
    }

    @Throws(IOException::class)
    fun testMarkOffsetBeginningOfStream() {
        val array = ByteArray(4)
        val bs = NewBufferedStream(8, "abcdefghijkl")
        TestCase.assertEquals(true, bs.markSupported())
        bs.mark(4)
        TestCase.assertEquals(4, bs.read(array))
        TestCase.assertEquals("abcd", String(array))
        bs.reset()
        TestCase.assertEquals(4, bs.read(array))
        TestCase.assertEquals("abcd", String(array))
        TestCase.assertEquals(4, bs.read(array))
        TestCase.assertEquals("efgh", String(array))
        TestCase.assertEquals(4, bs.read(array))
        TestCase.assertEquals("ijkl", String(array))
        TestCase.assertEquals(-1, bs.read())
    }

    @Throws(IOException::class)
    fun testMarkOffsetMiddleOfStream() {
        val array = ByteArray(4)
        val bs = NewBufferedStream(8, "abcdefghijkl")
        TestCase.assertEquals(true, bs.markSupported())
        TestCase.assertEquals(4, bs.read(array))
        TestCase.assertEquals("abcd", String(array))
        bs.mark(4)
        TestCase.assertEquals(4, bs.read(array))
        TestCase.assertEquals("efgh", String(array))
        bs.reset()
        TestCase.assertEquals(4, bs.read(array))
        TestCase.assertEquals("efgh", String(array))
        TestCase.assertEquals(4, bs.read(array))
        TestCase.assertEquals("ijkl", String(array))
        TestCase.assertEquals(-1, bs.read())
    }
}
