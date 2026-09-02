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
import net.blueva.luak.lib.LuaPlatform

/**
 * A host refusing bytecode, since the undumper reads a format rather than a
 * language and a malformed chunk is not something checking can make safe.
 */
class TextOnlyLoadingTest {
    private lateinit var globals: Globals

    /**
     * A real dump, made before the state stops accepting them.
     *
     * Kept as a LuaString: a dump is bytes, and putting it through a Kotlin
     * String would decode and re-encode everything above 0x7F.
     */
    private lateinit var dump: LuaString

    @BeforeTest
    fun buildGlobals() {
        globals = LuaPlatform.standardGlobals()
        dump = globals.load("return 21 * 2", "dumped")!!.let { chunk ->
            globals.load("return string.dump(...)", "dump-it")!!.call(chunk)!!.strvalue()!!
        }
    }

    private fun run(script: String): LuaValue = globals.load(script, "text-only-test")!!.call()!!

    private fun loadDump(mode: String): Varargs = loadBytes(dump, mode)

    private fun loadBytes(bytes: LuaString, mode: String): Varargs =
        globals.get("load")!!.invoke(
            LuaValue.varargsOf(bytes, LuaValue.valueOf("=(dump)"), LuaValue.valueOf(mode)),
        )!!

    @Test
    fun aDumpLoadsWhileTheStateStillAcceptsOne() {
        assertEquals(42L, loadDump("bt").arg1()!!.call()!!.tolong())
    }

    @Test
    fun aDumpIsRefusedOnceTheHostSaysTextOnly() {
        globals.textonly = true
        val answer: Varargs = loadDump("bt")
        assertTrue(answer.arg1()!!.isnil(), "load should have refused the dump")
        assertEquals("attempt to load a binary chunk (mode is 't')", answer.arg(2)!!.tojstring())
    }

    @Test
    fun askingForBinaryOutrightIsRefusedToo() {
        // The mode a script writes does not decide it; the state does.
        globals.textonly = true
        val answer: Varargs = loadDump("b")
        assertTrue(answer.arg1()!!.isnil(), "load should have refused the dump")
        assertEquals("attempt to load a binary chunk (mode is '')", answer.arg(2)!!.tojstring())
    }

    @Test
    fun aMalformedDumpIsRefusedBeforeItIsRead() {
        // The crash path this closes: bytes that carry the signature and then
        // whatever the attacker likes. Refused for being binary at all, so
        // nothing parses them.
        globals.textonly = true
        val signature: ByteArray = LoadState.LUA_SIGNATURE
        val rubbish = ByteArray(signature.size + 64) { at ->
            if (at < signature.size) signature[at] else ((at * 37) % 251).toByte()
        }
        val answer: Varargs = loadBytes(LuaString.valueUsing(rubbish), "bt")
        assertTrue(answer.arg1()!!.isnil(), "load should have refused the rubbish")
        assertEquals("attempt to load a binary chunk (mode is 't')", answer.arg(2)!!.tojstring())
    }

    @Test
    fun sourceStillCompiles() {
        globals.textonly = true
        assertEquals(42L, run("return 21 * 2").tolong())
        assertEquals(42L, run("return load('return 21 * 2')()").tolong())
    }

    @Test
    fun dumpingIsStillAllowed() {
        // What is refused is reading a chunk back, not writing one.
        globals.textonly = true
        assertTrue(run("return #string.dump(load('return 1'))").tolong() > 0)
    }

    @Test
    fun theHostsOwnLoaderIsBoundByItToo() {
        // Globals.load is the same choke point, so a host cannot get a binary
        // chunk in by the back door while a script cannot.
        globals.textonly = true
        val failure = kotlin.test.assertFailsWith<LuaError> {
            globals.load(dump.toInputStream()!!, "=(dump)", "bt", globals)
        }
        assertTrue(
            failure.message!!.contains("attempt to load a binary chunk"),
            "was: ${failure.message}",
        )
    }

    @Test
    fun acceptingBinaryChunksIsStillTheDefault() {
        assertEquals(false, globals.textonly)
    }
}
