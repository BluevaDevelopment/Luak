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

import java.io.RandomAccessFile

internal actual val platformFilesSupported: Boolean = true

private class JvmFileHandle(
    private val file: RandomAccessFile,
    private val appends: Boolean,
) : PlatformFileHandle {
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = file.read(bytes, offset, length)

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (appends) file.seek(file.length())
        file.write(bytes, offset, length)
    }

    override fun flush() = Unit
    override fun close() = file.close()
    override fun position(): Long = file.filePointer
    override fun seek(position: Long) = file.seek(position)
    override fun size(): Long = file.length()
}

internal actual fun platformOpenFile(path: String, mode: PlatformFileMode): PlatformFileHandle {
    val target = java.io.File(path)
    if (mode == PlatformFileMode.READ || mode == PlatformFileMode.READ_WRITE) {
        if (!target.exists()) throw IOException("$path: No such file or directory")
    }
    val file = RandomAccessFile(target, if (mode == PlatformFileMode.READ) "r" else "rw")
    when (mode) {
        PlatformFileMode.WRITE, PlatformFileMode.READ_WRITE_TRUNCATE -> file.setLength(0)
        PlatformFileMode.APPEND, PlatformFileMode.READ_APPEND -> file.seek(file.length())
        else -> Unit
    }
    return JvmFileHandle(file, mode.appends)
}

internal actual fun platformDeleteFile(path: String) {
    val target = java.io.File(path)
    if (!target.exists()) throw IOException("$path: No such file or directory")
    if (!target.delete()) throw IOException("$path: Failed to delete")
}

internal actual fun platformRenameFile(from: String, to: String) {
    val source = java.io.File(from)
    if (!source.exists()) throw IOException("$from: No such file or directory")
    if (!source.renameTo(java.io.File(to))) throw IOException("$from: Failed to rename")
}

internal actual fun platformTempFilePath(): String =
    java.io.File.createTempFile("luak", ".tmp").apply { deleteOnExit() }.absolutePath

internal actual fun platformStandardInput(): InputStream? = System.`in`
