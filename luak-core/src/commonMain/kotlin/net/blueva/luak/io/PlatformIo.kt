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

expect open class IOException : Exception {
    constructor()
    constructor(message: String?)
    constructor(message: String?, cause: Throwable?)
}

expect open class EOFException : IOException {
    constructor()
    constructor(message: String?)
}

expect abstract class InputStream() {
    @Throws(IOException::class)
    abstract fun read(): Int
    @Throws(IOException::class)
    open fun read(bytes: ByteArray): Int
    @Throws(IOException::class)
    open fun read(bytes: ByteArray, offset: Int, length: Int): Int
    @Throws(IOException::class)
    open fun skip(count: Long): Long
    @Throws(IOException::class)
    open fun available(): Int
    @Throws(IOException::class)
    open fun close()
    open fun mark(limit: Int)
    open fun reset()
    open fun markSupported(): Boolean
}

expect abstract class OutputStream() {
    abstract fun write(byte: Int)
    open fun write(bytes: ByteArray)
    open fun write(bytes: ByteArray, offset: Int, length: Int)
    open fun flush()
    open fun close()
}

expect abstract class Reader protected constructor() {
    @Throws(IOException::class)
    abstract fun read(chars: CharArray, offset: Int, length: Int): Int
    @Throws(IOException::class)
    open fun read(): Int
    @Throws(IOException::class)
    abstract fun close()
}

expect class ByteArrayInputStream : InputStream {
    constructor(bytes: ByteArray)
    constructor(bytes: ByteArray, offset: Int, length: Int)
    override fun read(): Int
}

expect class ByteArrayOutputStream : OutputStream {
    constructor()
    constructor(size: Int)
    override fun write(byte: Int)
    fun size(): Int
    fun reset()
    fun toByteArray(): ByteArray
}

expect class DataInputStream(input: InputStream) : InputStream {
    override fun read(): Int
    fun readByte(): Byte
    fun readUnsignedByte(): Int
    fun readInt(): Int
    fun readLong(): Long
    fun readFully(bytes: ByteArray, offset: Int, length: Int)
}

expect class DataOutputStream(output: OutputStream) : OutputStream {
    override fun write(byte: Int)
    fun writeByte(value: Int)
    fun writeInt(value: Int)
    fun writeLong(value: Long)
}

expect class PrintStream(output: OutputStream) : OutputStream {
    override fun write(byte: Int)
    fun print(value: Any?)
    fun print(value: String?)
    fun print(value: Char)
    fun print(value: Int)
    fun println()
    fun println(value: Any?)
    fun println(value: String?)
}

expect fun standardOutput(): PrintStream
expect fun standardError(): PrintStream
expect fun platformResource(name: String): InputStream?
