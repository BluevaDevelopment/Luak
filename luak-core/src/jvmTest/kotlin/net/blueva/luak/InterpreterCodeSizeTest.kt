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

import java.io.DataInputStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the interpreter against HotSpot's `DontCompileHugeMethods` cliff.
 *
 * A method whose `Code` attribute exceeds `-XX:HugeMethodLimit` (8000 bytes by
 * default) is never JIT-compiled: HotSpot runs it in its own bytecode
 * interpreter forever. When [LuaClosure.execute] crossed that line, every Lua
 * program ran roughly ten to eighteen times slower, with no warning of any kind
 * - it compiles, it passes every test, it is just silently interpreted.
 *
 * The margin below leaves room for ordinary edits while still failing long
 * before the cliff. If this test fails, do not raise the limit: move a group of
 * opcode cases into a private method, the way `callFixedArity` was split out of
 * `execute`. Suspending call sites are the expensive ones - each expands into
 * its own spill/restore block.
 */
class InterpreterCodeSizeTest {
    @Test
    fun interpreterMethodsStayJitCompilable() {
        val oversized = codeSizes(LuaClosure::class.java)
            .filterValues { it > MAX_METHOD_BYTECODES }
        if (oversized.isNotEmpty()) {
            val detail = oversized.entries.joinToString(", ") { "${it.key} = ${it.value} bytes" }
            fail(
                "$detail exceeds the $MAX_METHOD_BYTECODES-byte budget. Methods over " +
                    "$HOTSPOT_HUGE_METHOD_LIMIT bytes are never JIT-compiled; split opcode " +
                    "cases into a private method instead of relaxing this bound.",
            )
        }
    }

    @Test
    fun theGuardActuallyReadsTheInterpreter() {
        // A parser that silently returned nothing would make the test above
        // vacuous, so pin the method it is meant to be watching.
        val sizes = codeSizes(LuaClosure::class.java)
        assertTrue("execute" in sizes, "expected to find LuaClosure.execute; found ${sizes.keys}")
        assertTrue(sizes.getValue("execute") > 1000, "execute looks implausibly small: ${sizes["execute"]}")
    }

    private companion object {
        /** HotSpot's default `-XX:HugeMethodLimit`. */
        const val HOTSPOT_HUGE_METHOD_LIMIT = 8000

        /** Budget with headroom, so an ordinary edit fails here rather than in production. */
        const val MAX_METHOD_BYTECODES = 7800

        /** Method name to `Code` attribute length, for every method in [type]. */
        fun codeSizes(type: Class<*>): Map<String, Int> {
            val resource = type.getResourceAsStream("${type.simpleName}.class")
                ?: error("cannot read the class file for ${type.name}")
            return resource.use { readCodeSizes(DataInputStream(it.buffered())) }
        }

        private fun readCodeSizes(input: DataInputStream): Map<String, Int> {
            require(input.readInt() == -0x35014542) { "not a class file" } // 0xCAFEBABE
            input.readUnsignedShort() // minor version
            input.readUnsignedShort() // major version

            val constants = readConstantPool(input)

            input.readUnsignedShort() // access flags
            input.readUnsignedShort() // this class
            input.readUnsignedShort() // super class
            repeat(input.readUnsignedShort()) { input.readUnsignedShort() } // interfaces
            repeat(input.readUnsignedShort()) { skipMember(input) } // fields

            val sizes = LinkedHashMap<String, Int>()
            repeat(input.readUnsignedShort()) {
                input.readUnsignedShort() // access flags
                val name = constants[input.readUnsignedShort()] as String
                input.readUnsignedShort() // descriptor
                repeat(input.readUnsignedShort()) {
                    val attributeName = constants[input.readUnsignedShort()]
                    val length = input.readInt()
                    if (attributeName == "Code") {
                        input.readUnsignedShort() // max stack
                        input.readUnsignedShort() // max locals
                        val codeLength = input.readInt()
                        // Keep the largest overload; only the big ones matter here.
                        sizes[name] = maxOf(sizes[name] ?: 0, codeLength)
                        input.skipFully(length - 8)
                    } else {
                        input.skipFully(length)
                    }
                }
            }
            return sizes
        }

        /** Returns a slot-indexed pool where UTF-8 entries hold their text. */
        private fun readConstantPool(input: DataInputStream): Array<Any?> {
            val count = input.readUnsignedShort()
            val pool = arrayOfNulls<Any>(count)
            var index = 1
            while (index < count) {
                when (val tag = input.readUnsignedByte()) {
                    1 -> pool[index] = input.readUTF()
                    7, 8, 16, 19, 20 -> input.skipFully(2)
                    15 -> input.skipFully(3)
                    3, 4, 9, 10, 11, 12, 17, 18 -> input.skipFully(4)
                    // Long and Double take two pool slots each.
                    5, 6 -> {
                        input.skipFully(8)
                        index++
                    }

                    else -> error("unsupported constant pool tag $tag")
                }
                index++
            }
            return pool
        }

        private fun skipMember(input: DataInputStream) {
            input.skipFully(6) // access flags, name, descriptor
            repeat(input.readUnsignedShort()) {
                input.readUnsignedShort() // attribute name
                input.skipFully(input.readInt())
            }
        }

        private fun DataInputStream.skipFully(count: Int) {
            var remaining = count
            while (remaining > 0) {
                val skipped = skip(remaining.toLong()).toInt()
                if (skipped <= 0) {
                    readByte()
                    remaining--
                } else {
                    remaining -= skipped
                }
            }
        }
    }
}
