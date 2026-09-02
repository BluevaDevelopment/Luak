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

actual typealias IOException = java.io.IOException
actual typealias EOFException = java.io.EOFException
actual typealias InputStream = java.io.InputStream
actual typealias OutputStream = java.io.OutputStream
actual abstract class Reader protected actual constructor() : java.io.Reader() {
    actual abstract override fun read(chars: CharArray, offset: Int, length: Int): Int
    actual override fun read(): Int = super.read()
    actual abstract override fun close()
}
actual typealias ByteArrayInputStream = java.io.ByteArrayInputStream
actual typealias ByteArrayOutputStream = java.io.ByteArrayOutputStream
actual typealias DataInputStream = java.io.DataInputStream
actual typealias DataOutputStream = java.io.DataOutputStream
actual typealias PrintStream = java.io.PrintStream

actual fun standardOutput(): PrintStream = System.out
actual fun standardError(): PrintStream = System.err
// Callers pass plain resource names; the leading slash that makes a JVM
// classpath lookup absolute is a JVM detail and is added here rather than by
// the shared code, which also has to work on hosts where a name like
// "/main.lua" would mean the filesystem root.
actual fun platformResource(name: String): InputStream? =
    object {}.javaClass.getResourceAsStream(if (name.startsWith("/")) name else "/$name")
