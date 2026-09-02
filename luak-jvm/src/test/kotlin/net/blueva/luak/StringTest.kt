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

import junit.framework.TestCase
import net.blueva.luak.lib.jvm.JvmPlatform.standardGlobals
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException

class StringTest : TestCase() {
    @Throws(Exception::class)
    override fun setUp() {
        standardGlobals()
    }

    @Throws(IOException::class)
    fun testToInputStream() {
        val str = LuaString.valueOf("Hello")

        var `is`: InputStream = str.toInputStream()!!

        TestCase.assertEquals('H'.code, `is`.read())
        TestCase.assertEquals('e'.code, `is`.read())
        TestCase.assertEquals(2, `is`.skip(2))
        TestCase.assertEquals('o'.code, `is`.read())
        TestCase.assertEquals(-1, `is`.read())

        assertTrue(`is`.markSupported())

        `is`.reset()

        TestCase.assertEquals('H'.code, `is`.read())
        `is`.mark(4)

        TestCase.assertEquals('e'.code, `is`.read())
        `is`.reset()
        TestCase.assertEquals('e'.code, `is`.read())

        val substr = str.substring(1, 4)
        TestCase.assertEquals(3, substr.length())

        `is`.close()
        `is` = substr.toInputStream()!!

        TestCase.assertEquals('e'.code, `is`.read())
        TestCase.assertEquals('l'.code, `is`.read())
        TestCase.assertEquals('l'.code, `is`.read())
        TestCase.assertEquals(-1, `is`.read())

        `is` = substr.toInputStream()!!
        `is`.reset()

        TestCase.assertEquals('e'.code, `is`.read())
    }


    @Throws(UnsupportedEncodingException::class)
    fun testUtf820482051() {
        val i = 2048
        val c = charArrayOf((i + 0).toChar(), (i + 1).toChar(), (i + 2).toChar(), (i + 3).toChar())
        val before = String(c) + " " + i + "-" + (i + 4)
        val ls = LuaString.valueOf(before)
        val after = ls.tojstring()
        TestCase.assertEquals(userFriendly(before), userFriendly(after))
    }

    fun testUtf8() {
        var i = 4
        while (i < 0xffff) {
            val c = charArrayOf((i + 0).toChar(), (i + 1).toChar(), (i + 2).toChar(), (i + 3).toChar())
            val before = String(c) + " " + i + "-" + (i + 4)
            val ls = LuaString.valueOf(before)
            val after = ls.tojstring()
            TestCase.assertEquals(userFriendly(before), userFriendly(after))
            i += 4
        }
        val c = charArrayOf((1).toChar(), (2).toChar(), (3).toChar())
        val before = String(c) + " 1-3"
        val ls = LuaString.valueOf(before)
        val after = ls.tojstring()
        TestCase.assertEquals(userFriendly(before), userFriendly(after))
    }

    @Throws(UnsupportedEncodingException::class)
    fun testSpotCheckUtf8() {
        val bytes = byteArrayOf(
            194.toByte(),
            160.toByte(),
            194.toByte(),
            161.toByte(),
            194.toByte(),
            162.toByte(),
            194.toByte(),
            163.toByte(),
            194.toByte(),
            164.toByte()
        )
        val expected = String(bytes, charset("UTF8"))
        val actual = LuaString.valueOf(bytes).tojstring()
        val d = actual.toCharArray()
        TestCase.assertEquals(160, d[0].code)
        TestCase.assertEquals(161, d[1].code)
        TestCase.assertEquals(162, d[2].code)
        TestCase.assertEquals(163, d[3].code)
        TestCase.assertEquals(164, d[4].code)
        TestCase.assertEquals(expected, actual)
    }

    fun testNullTerminated() {
        val c = charArrayOf('a', 'b', 'c', '\u0000', 'd', 'e', 'f')
        val before = String(c)
        val ls = LuaString.valueOf(before)
        val after = ls.tojstring()
        TestCase.assertEquals(userFriendly("abc\u0000def"), userFriendly(after))
    }

    fun testRecentStringsCacheDifferentHashcodes() {
        val abc = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        val xyz = byteArrayOf('x'.code.toByte(), 'y'.code.toByte(), 'z'.code.toByte())
        val abc1 = LuaString.valueOf(abc)
        val xyz1 = LuaString.valueOf(xyz)
        val abc2 = LuaString.valueOf(abc)
        val xyz2 = LuaString.valueOf(xyz)
        val mod = LuaString.RECENT_STRINGS_CACHE_SIZE
        assertTrue(abc1.hashCode() % mod != xyz1.hashCode() % mod)
        assertSame(abc1, abc2)
        assertSame(xyz1, xyz2)
    }

    fun testRecentStringsCacheHashCollisionCacheHit() {
        val abc = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        val lyz = byteArrayOf(
            'l'.code.toByte(),
            'y'.code.toByte(),
            'z'.code.toByte()
        ) // chosen to have hash collision with 'abc'
        val abc1 = LuaString.valueOf(abc)
        val abc2 = LuaString.valueOf(abc) // in cache: 'abc'
        val lyz1 = LuaString.valueOf(lyz)
        val lyz2 = LuaString.valueOf(lyz) // in cache: 'lyz'
        val mod = LuaString.RECENT_STRINGS_CACHE_SIZE
        TestCase.assertEquals(abc1.hashCode() % mod, lyz1.hashCode() % mod)
        assertNotSame(abc1, lyz1)
        assertFalse(abc1 == lyz1)
        assertSame(abc1, abc2)
        assertSame(lyz1, lyz2)
    }

    fun testRecentStringsCacheHashCollisionCacheMiss() {
        val abc = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        val lyz = byteArrayOf(
            'l'.code.toByte(),
            'y'.code.toByte(),
            'z'.code.toByte()
        ) // chosen to have hash collision with 'abc'
        val abc1 = LuaString.valueOf(abc)
        val lyz1 = LuaString.valueOf(lyz) // in cache: 'abc'
        val abc2 = LuaString.valueOf(abc) // in cache: 'lyz'
        val lyz2 = LuaString.valueOf(lyz) // in cache: 'abc'
        val mod = LuaString.RECENT_STRINGS_CACHE_SIZE
        TestCase.assertEquals(abc1.hashCode() % mod, lyz1.hashCode() % mod)
        assertNotSame(abc1, lyz1)
        assertFalse(abc1 == lyz1)
        assertNotSame(abc1, abc2)
        assertNotSame(lyz1, lyz2)
    }

    fun testRecentStringsLongStrings() {
        val abc = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toByteArray()
        assertTrue(abc.size > LuaString.RECENT_STRINGS_MAX_LENGTH)
        val abc1 = LuaString.valueOf(abc)
        val abc2 = LuaString.valueOf(abc)
        assertNotSame(abc1, abc2)
    }

    fun testRecentStringsUsingJavaStrings() {
        val abc = "abc"
        val lyz = "lyz" // chosen to have hash collision with 'abc'
        val xyz = "xyz"

        val abc1 = LuaString.valueOf(abc)
        val abc2 = LuaString.valueOf(abc)
        val lyz1 = LuaString.valueOf(lyz)
        val lyz2 = LuaString.valueOf(lyz)
        val xyz1 = LuaString.valueOf(xyz)
        val xyz2 = LuaString.valueOf(xyz)
        val mod = LuaString.RECENT_STRINGS_CACHE_SIZE
        TestCase.assertEquals(abc1.hashCode() % mod, lyz1.hashCode() % mod)
        assertFalse(abc1.hashCode() % mod == xyz1.hashCode() % mod)
        assertSame(abc1, abc2)
        assertSame(lyz1, lyz2)
        assertSame(xyz1, xyz2)

        val abc3 = LuaString.valueOf(abc)
        val lyz3 = LuaString.valueOf(lyz)
        val xyz3 = LuaString.valueOf(xyz)

        val abc4 = LuaString.valueOf(abc)
        val lyz4 = LuaString.valueOf(lyz)
        val xyz4 = LuaString.valueOf(xyz)
        assertNotSame(abc3, abc4) // because of hash collision
        assertNotSame(lyz3, lyz4) // because of hash collision
        assertSame(xyz3, xyz4) // because hashes do not collide
    }

    fun testLongSubstringGetsOldBacking() {
        val src = LuaString.valueOf("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
        val sub1 = src.substring(10, 40)
        assertSame(src.m_bytes, sub1.m_bytes)
        TestCase.assertEquals(sub1.m_offset, 10)
        TestCase.assertEquals(sub1.m_length, 30)
    }

    fun testShortSubstringGetsNewBacking() {
        val src = LuaString.valueOf("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
        val sub1 = src.substring(10, 20)
        val sub2 = src.substring(10, 20)
        TestCase.assertEquals(sub1.m_offset, 0)
        TestCase.assertEquals(sub1.m_length, 10)
        assertSame(sub1, sub2)
        assertFalse(src.m_bytes == sub1.m_bytes)
    }

    fun testShortSubstringOfVeryLongStringGetsNewBacking() {
        val src = LuaString.valueOf(
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        )
        val sub1 = src.substring(10, 50)
        val sub2 = src.substring(10, 50)
        TestCase.assertEquals(sub1.m_offset, 0)
        TestCase.assertEquals(sub1.m_length, 40)
        assertFalse(sub1 === sub2)
        assertFalse(src.m_bytes == sub1.m_bytes)
    }

    fun testIndexOfByteInSubstring() {
        val str = LuaString.valueOf("abcdef:ghi")
        val sub = str.substring(2, 10)
        TestCase.assertEquals(10, str.m_length)
        TestCase.assertEquals(8, sub.m_length)
        TestCase.assertEquals(0, str.m_offset)
        TestCase.assertEquals(2, sub.m_offset)

        TestCase.assertEquals(6, str.indexOf(':'.code.toByte(), 0))
        TestCase.assertEquals(6, str.indexOf(':'.code.toByte(), 2))
        TestCase.assertEquals(6, str.indexOf(':'.code.toByte(), 6))
        TestCase.assertEquals(-1, str.indexOf(':'.code.toByte(), 7))
        TestCase.assertEquals(-1, str.indexOf(':'.code.toByte(), 9))
        TestCase.assertEquals(9, str.indexOf('i'.code.toByte(), 0))
        TestCase.assertEquals(9, str.indexOf('i'.code.toByte(), 2))
        TestCase.assertEquals(9, str.indexOf('i'.code.toByte(), 9))
        TestCase.assertEquals(-1, str.indexOf('z'.code.toByte(), 0))
        TestCase.assertEquals(-1, str.indexOf('z'.code.toByte(), 2))
        TestCase.assertEquals(-1, str.indexOf('z'.code.toByte(), 9))

        TestCase.assertEquals(4, sub.indexOf(':'.code.toByte(), 0))
        TestCase.assertEquals(4, sub.indexOf(':'.code.toByte(), 2))
        TestCase.assertEquals(4, sub.indexOf(':'.code.toByte(), 4))
        TestCase.assertEquals(-1, sub.indexOf(':'.code.toByte(), 5))
        TestCase.assertEquals(-1, sub.indexOf(':'.code.toByte(), 7))
        TestCase.assertEquals(7, sub.indexOf('i'.code.toByte(), 0))
        TestCase.assertEquals(7, sub.indexOf('i'.code.toByte(), 2))
        TestCase.assertEquals(7, sub.indexOf('i'.code.toByte(), 7))
        TestCase.assertEquals(-1, sub.indexOf('z'.code.toByte(), 0))
        TestCase.assertEquals(-1, sub.indexOf('z'.code.toByte(), 2))
        TestCase.assertEquals(-1, sub.indexOf('z'.code.toByte(), 7))
    }

    fun testIndexOfPatternInSubstring() {
        val str = LuaString.valueOf("abcdef:ghi")
        val sub = str.substring(2, 10)
        TestCase.assertEquals(10, str.m_length)
        TestCase.assertEquals(8, sub.m_length)
        TestCase.assertEquals(0, str.m_offset)
        TestCase.assertEquals(2, sub.m_offset)

        val pat = LuaString.valueOf(":")
        val i = LuaString.valueOf("i")
        val xyz = LuaString.valueOf("xyz")

        TestCase.assertEquals(6, str.indexOf(pat, 0))
        TestCase.assertEquals(6, str.indexOf(pat, 2))
        TestCase.assertEquals(6, str.indexOf(pat, 6))
        TestCase.assertEquals(-1, str.indexOf(pat, 7))
        TestCase.assertEquals(-1, str.indexOf(pat, 9))
        TestCase.assertEquals(9, str.indexOf(i, 0))
        TestCase.assertEquals(9, str.indexOf(i, 2))
        TestCase.assertEquals(9, str.indexOf(i, 9))
        TestCase.assertEquals(-1, str.indexOf(xyz, 0))
        TestCase.assertEquals(-1, str.indexOf(xyz, 2))
        TestCase.assertEquals(-1, str.indexOf(xyz, 9))

        TestCase.assertEquals(4, sub.indexOf(pat, 0))
        TestCase.assertEquals(4, sub.indexOf(pat, 2))
        TestCase.assertEquals(4, sub.indexOf(pat, 4))
        TestCase.assertEquals(-1, sub.indexOf(pat, 5))
        TestCase.assertEquals(-1, sub.indexOf(pat, 7))
        TestCase.assertEquals(7, sub.indexOf(i, 0))
        TestCase.assertEquals(7, sub.indexOf(i, 2))
        TestCase.assertEquals(7, sub.indexOf(i, 7))
        TestCase.assertEquals(-1, sub.indexOf(xyz, 0))
        TestCase.assertEquals(-1, sub.indexOf(xyz, 2))
        TestCase.assertEquals(-1, sub.indexOf(xyz, 7))
    }

    fun testLastIndexOfPatternInSubstring() {
        val str = LuaString.valueOf("abcdef:ghi")
        val sub = str.substring(2, 10)
        TestCase.assertEquals(10, str.m_length)
        TestCase.assertEquals(8, sub.m_length)
        TestCase.assertEquals(0, str.m_offset)
        TestCase.assertEquals(2, sub.m_offset)

        val pat = LuaString.valueOf(":")
        val i = LuaString.valueOf("i")
        val xyz = LuaString.valueOf("xyz")

        TestCase.assertEquals(6, str.lastIndexOf(pat))
        TestCase.assertEquals(9, str.lastIndexOf(i))
        TestCase.assertEquals(-1, str.lastIndexOf(xyz))

        TestCase.assertEquals(4, sub.lastIndexOf(pat))
        TestCase.assertEquals(7, sub.lastIndexOf(i))
        TestCase.assertEquals(-1, sub.lastIndexOf(xyz))
    }

    fun testIndexOfAnyInSubstring() {
        val str = LuaString.valueOf("abcdef:ghi")
        val sub = str.substring(2, 10)
        TestCase.assertEquals(10, str.m_length)
        TestCase.assertEquals(8, sub.m_length)
        TestCase.assertEquals(0, str.m_offset)
        TestCase.assertEquals(2, sub.m_offset)

        val ghi = LuaString.valueOf("ghi")
        val ihg = LuaString.valueOf("ihg")
        val ijk = LuaString.valueOf("ijk")
        val kji = LuaString.valueOf("kji")
        val xyz = LuaString.valueOf("xyz")
        val ABCdEFGHIJKL = LuaString.valueOf("ABCdEFGHIJKL")
        val EFGHIJKL = ABCdEFGHIJKL.substring(4, 12)
        val CdEFGHIJ = ABCdEFGHIJKL.substring(2, 10)
        TestCase.assertEquals(4, EFGHIJKL.m_offset)
        TestCase.assertEquals(2, CdEFGHIJ.m_offset)

        TestCase.assertEquals(7, str.indexOfAny(ghi))
        TestCase.assertEquals(7, str.indexOfAny(ihg))
        TestCase.assertEquals(9, str.indexOfAny(ijk))
        TestCase.assertEquals(9, str.indexOfAny(kji))
        TestCase.assertEquals(-1, str.indexOfAny(xyz))
        TestCase.assertEquals(3, str.indexOfAny(CdEFGHIJ))
        TestCase.assertEquals(-1, str.indexOfAny(EFGHIJKL))

        TestCase.assertEquals(5, sub.indexOfAny(ghi))
        TestCase.assertEquals(5, sub.indexOfAny(ihg))
        TestCase.assertEquals(7, sub.indexOfAny(ijk))
        TestCase.assertEquals(7, sub.indexOfAny(kji))
        TestCase.assertEquals(-1, sub.indexOfAny(xyz))
        TestCase.assertEquals(1, sub.indexOfAny(CdEFGHIJ))
        TestCase.assertEquals(-1, sub.indexOfAny(EFGHIJKL))
    }

    fun testMatchShortPatterns() {
        val args = arrayOf<LuaValue?>(LuaString.valueOf("%bxy"))
        val empty = LuaString.valueOf("")

        val a = LuaString.valueOf("a")
        val ax = LuaString.valueOf("ax")
        val axb = LuaString.valueOf("axb")
        val axby = LuaString.valueOf("axby")
        val xbya = LuaString.valueOf("xbya")
        val bya = LuaString.valueOf("bya")
        val xby = LuaString.valueOf("xby")
        val axbya = LuaString.valueOf("axbya")
        val nil: LuaValue? = LuaValue.NIL

        assertEquals(nil, empty.invokemethod("match", args))
        assertEquals(nil, a.invokemethod("match", args))
        assertEquals(nil, ax.invokemethod("match", args))
        assertEquals(nil, axb.invokemethod("match", args))
        assertEquals(xby, axby.invokemethod("match", args))
        assertEquals(xby, xbya.invokemethod("match", args))
        assertEquals(nil, bya.invokemethod("match", args))
        assertEquals(xby, xby.invokemethod("match", args))
        assertEquals(xby, axbya.invokemethod("match", args))
        assertEquals(xby, axbya.substring(0, 4).invokemethod("match", args))
        assertEquals(nil, axbya.substring(0, 3).invokemethod("match", args))
        assertEquals(xby, axbya.substring(1, 5).invokemethod("match", args))
        assertEquals(nil, axbya.substring(2, 5).invokemethod("match", args))
    }

    companion object {
        private fun userFriendly(s: String): String {
            val sb = StringBuffer()
            var i = 0
            val n = s.length
            while (i < n) {
                val c = s.get(i).code
                if (c < ' '.code || c >= 0x80) {
                    sb.append("\\u" + Integer.toHexString(0x10000 + c).substring(1))
                } else {
                    sb.append(c.toChar())
                }
                i++
            }
            return sb.toString()
        }
    }
}
