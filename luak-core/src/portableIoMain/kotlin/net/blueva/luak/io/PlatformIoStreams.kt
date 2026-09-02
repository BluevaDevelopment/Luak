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
package net.blueva.luak.io

// The stream/buffer classes below are plain Kotlin with no platform-specific
// dependency at all (no JS interop, no WASI syscalls), so this one file is
// shared by every non-JVM, non-Native target: Kotlin/JS, Kotlin/Wasm-JS, and
// Kotlin/Wasm-WASI. Each target still supplies its own `standardOutput()` /
// `standardError()` / `platformResource()`, since those genuinely differ per
// host (JS console/node:fs vs raw WASI fd_write/path_open).

actual open class IOException : Exception {
    actual constructor() : super()
    actual constructor(message: String?) : super(message)
    actual constructor(message: String?, cause: Throwable?) : super(message, cause)
}

actual open class EOFException : IOException {
    actual constructor() : super()
    actual constructor(message: String?) : super(message)
}

actual abstract class InputStream actual constructor() {
    actual abstract fun read(): Int

    actual open fun read(bytes: ByteArray): Int = read(bytes, 0, bytes.size)

    actual open fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val first = read()
        if (first < 0) return -1
        bytes[offset] = first.toByte()
        var count = 1
        while (count < length) {
            val value = read()
            if (value < 0) break
            bytes[offset + count] = value.toByte()
            count++
        }
        return count
    }

    actual open fun skip(count: Long): Long {
        var skipped = 0L
        while (skipped < count && read() >= 0) skipped++
        return skipped
    }

    actual open fun available(): Int = 0
    actual open fun close() = Unit
    actual open fun mark(limit: Int) = Unit
    actual open fun reset(): Unit = throw IOException("mark/reset not supported")
    actual open fun markSupported(): Boolean = false
}

actual abstract class OutputStream actual constructor() {
    actual abstract fun write(byte: Int)
    actual open fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)
    actual open fun write(bytes: ByteArray, offset: Int, length: Int) {
        for (index in offset until offset + length) write(bytes[index].toInt() and 0xff)
    }
    actual open fun flush() = Unit
    actual open fun close() = Unit
}

actual abstract class Reader protected actual constructor() {
    actual abstract fun read(chars: CharArray, offset: Int, length: Int): Int
    actual open fun read(): Int {
        val chars = CharArray(1)
        return if (read(chars, 0, 1) < 0) -1 else chars[0].code
    }
    actual abstract fun close()
}

actual class ByteArrayInputStream : InputStream {
    private val bytes: ByteArray
    private val start: Int
    private val end: Int
    private var position: Int
    private var mark: Int

    actual constructor(bytes: ByteArray) : this(bytes, 0, bytes.size)

    actual constructor(bytes: ByteArray, offset: Int, length: Int) : super() {
        this.bytes = bytes
        start = offset.coerceIn(0, bytes.size)
        end = (start + length).coerceAtMost(bytes.size)
        position = start
        mark = start
    }

    actual override fun read(): Int = if (position < end) bytes[position++].toInt() and 0xff else -1
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (position >= end) return -1
        val count = minOf(length, end - position)
        this.bytes.copyInto(bytes, offset, position, position + count)
        position += count
        return count
    }
    override fun skip(count: Long): Long {
        val skipped = minOf(count, (end - position).toLong()).coerceAtLeast(0)
        position += skipped.toInt()
        return skipped
    }
    override fun available(): Int = end - position
    override fun mark(limit: Int) { mark = position }
    override fun reset() { position = mark }
    override fun markSupported(): Boolean = true
}

actual class ByteArrayOutputStream : OutputStream {
    private var bytes: ByteArray
    private var count = 0

    actual constructor() : this(32)
    actual constructor(size: Int) : super() { bytes = ByteArray(size.coerceAtLeast(0)) }

    private fun ensureCapacity(required: Int) {
        if (required <= bytes.size) return
        bytes = bytes.copyOf(maxOf(required, maxOf(32, bytes.size * 2)))
    }
    actual override fun write(byte: Int) {
        ensureCapacity(count + 1)
        bytes[count++] = byte.toByte()
    }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        ensureCapacity(count + length)
        bytes.copyInto(this.bytes, count, offset, offset + length)
        count += length
    }
    actual fun size(): Int = count
    actual fun reset() { count = 0 }
    actual fun toByteArray(): ByteArray = bytes.copyOf(count)
}

actual class DataInputStream actual constructor(private val input: InputStream) : InputStream() {
    actual override fun read(): Int = input.read()
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = input.read(bytes, offset, length)
    override fun close() = input.close()
    actual fun readByte(): Byte {
        val value = read()
        if (value < 0) throw EOFException()
        return value.toByte()
    }
    actual fun readUnsignedByte(): Int = readByte().toInt() and 0xff
    actual fun readInt(): Int = (readByte().toInt() and 0xff shl 24) or
        (readByte().toInt() and 0xff shl 16) or
        (readByte().toInt() and 0xff shl 8) or
        (readByte().toInt() and 0xff)
    actual fun readLong(): Long {
        var value = 0L
        repeat(8) { value = (value shl 8) or (readByte().toLong() and 0xff) }
        return value
    }
    actual fun readFully(bytes: ByteArray, offset: Int, length: Int) {
        var position = offset
        while (position < offset + length) {
            val count = read(bytes, position, offset + length - position)
            if (count < 0) throw EOFException()
            position += count
        }
    }
}

actual class DataOutputStream actual constructor(private val output: OutputStream) : OutputStream() {
    actual override fun write(byte: Int) = output.write(byte)
    override fun write(bytes: ByteArray, offset: Int, length: Int) = output.write(bytes, offset, length)
    override fun flush() = output.flush()
    override fun close() = output.close()
    actual fun writeByte(value: Int) = write(value)
    actual fun writeInt(value: Int) {
        for (shift in 24 downTo 0 step 8) write(value ushr shift)
    }
    actual fun writeLong(value: Long) {
        for (shift in 56 downTo 0 step 8) write((value ushr shift).toInt())
    }
}

actual class PrintStream actual constructor(private val output: OutputStream) : OutputStream() {
    actual override fun write(byte: Int) = output.write(byte)
    override fun write(bytes: ByteArray, offset: Int, length: Int) = output.write(bytes, offset, length)
    override fun flush() = output.flush()
    override fun close() = output.close()
    private fun emit(value: String) = write(value.encodeToByteArray())
    actual fun print(value: Any?) = emit(value.toString())
    actual fun print(value: String?) = emit(value ?: "null")
    actual fun print(value: Char) = emit(value.toString())
    actual fun print(value: Int) = emit(value.toString())
    actual fun println() = emit("\n")
    actual fun println(value: Any?) = emit(value.toString() + "\n")
    actual fun println(value: String?) = emit((value ?: "null") + "\n")
}
