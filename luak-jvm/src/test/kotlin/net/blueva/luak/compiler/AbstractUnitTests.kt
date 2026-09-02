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
package net.blueva.luak.compiler

import junit.framework.TestCase
import net.blueva.luak.Globals
import net.blueva.luak.Print
import net.blueva.luak.Prototype
import net.blueva.luak.lib.jvm.JvmPlatform.standardGlobals
import java.io.*
import java.net.MalformedURLException
import java.net.URL

abstract class AbstractUnitTests(zipdir: String?, zipfile: String, dir: String) : TestCase() {
    private val dir: String
    private val jar: String
    private var globals: Globals? = null

    init {
        val resourcePath = "/" + zipdir.orEmpty().trim('/') + "/" + zipfile
        var zip: URL? = javaClass.getResource(resourcePath)
        if (zip == null) {
            val file = File(zipdir + "/" + zipfile)
            try {
                if (file.exists()) zip = file.toURI().toURL()
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }
        }
        if (zip == null) throw RuntimeException("not found: " + zipfile)
        this.jar = "jar:" + zip.toExternalForm() + "!/"
        this.dir = dir
    }

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        globals = standardGlobals()
    }

    protected fun pathOfFile(file: String?): String {
        return jar + dir + "/" + file
    }

    @Throws(IOException::class)
    protected fun inputStreamOfPath(path: String): InputStream {
        val url = URL(path)
        return url.openStream()
    }

    @Throws(IOException::class)
    protected fun inputStreamOfFile(file: String?): InputStream {
        return inputStreamOfPath(pathOfFile(file))
    }

    /**
     * Compiles [file], dumps it, reads it back, and checks the two agree.
     *
     * This used to also compare against the `.lc` files in the archive, which
     * `luac` 5.2 produced. That comparison is retired: in 5.2 every numeral was
     * a float, so now that numerals carry the 5.3 integer subtype, a constant
     * pool here and one there differ for every script that contains a number -
     * the skip list had grown to cover most of the corpus and was measuring
     * the version gap rather than any regression. A reference comparison
     * becomes meaningful again once the port reaches the 5.5 bytecode format
     * and can be checked against `luac` 5.5.
     *
     * What remains still fails on a crash in the compiler, on a dump the
     * undumper cannot read, and on any round-trip that loses information.
     */
    protected open fun doTest(file: String?) {
        try {
            // load source from jar
            val path = pathOfFile(file)
            val lua = bytesFromJar(path)

            // compile in memory
            val `is`: InputStream = ByteArrayInputStream(lua)
            val p: Prototype = globals!!.loadPrototype(`is`, "@" + file, "bt")!!
            val actual = protoToString(p)

            // dump into memory
            val baos = ByteArrayOutputStream()
            DumpState.dump(p, baos, false)
            val dumped = baos.toByteArray()

            // re-undump
            val p2 = loadFromBytes(dumped, file)
            val actual2 = protoToString(p2)

            // compare again
            TestCase.assertEquals(actual, actual2)
        } catch (e: IOException) {
            fail(e.toString())
        }
    }

    @Throws(IOException::class)
    protected fun bytesFromJar(path: String): ByteArray {
        val `is` = inputStreamOfPath(path)
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        var n: Int
        while ((`is`.read(buffer).also { n = it }) >= 0) baos.write(buffer, 0, n)
        `is`.close()
        return baos.toByteArray()
    }

    @Throws(IOException::class)
    protected fun loadFromBytes(bytes: ByteArray, script: String?): Prototype {
        val `is`: InputStream = ByteArrayInputStream(bytes)
        return globals!!.loadPrototype(`is`, script, "b")!!
    }


    protected fun protoToString(p: Prototype): String? {
        val baos = ByteArrayOutputStream()
        val ps = PrintStream(baos)
        Print.ps = ps
        Print.printFunction(p, true)
        return baos.toString()
    }
}
