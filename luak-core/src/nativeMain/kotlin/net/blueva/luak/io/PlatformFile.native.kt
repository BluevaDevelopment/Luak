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

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlin.native.OsFamily
import kotlin.native.Platform as NativePlatform
import net.blueva.luak.currentTimeMillis
import platform.posix.FILE
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.getenv
import platform.posix.remove
import platform.posix.rename
import platform.posix.stdin

// The POSIX stdio layer every Kotlin/Native target ships with. Mode strings
// come straight from PlatformFileMode.stdio, so append/truncate semantics are
// the C library's rather than something re-derived here.

/**
 * 64-bit seek and tell. `fseek`/`ftell` take and return C `long`, which is
 * 32 bits on Windows and 64 elsewhere - a width mismatch Kotlin will not let
 * shared native code paper over - so each family supplies its own.
 */
@OptIn(ExperimentalForeignApi::class)
internal expect fun fileSeek(file: CPointer<FILE>, offset: Long, whence: Int): Boolean

@OptIn(ExperimentalForeignApi::class)
internal expect fun fileTell(file: CPointer<FILE>): Long

internal actual val platformFilesSupported: Boolean = true

@OptIn(ExperimentalForeignApi::class)
private class NativeFileHandle(private val file: CPointer<FILE>) : PlatformFileHandle {
    private var closed = false

    private fun stream(): CPointer<FILE> {
        if (closed) throw IOException("file is closed")
        return file
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val count = bytes.usePinned { pinned ->
            fread(pinned.addressOf(offset), 1.convert(), length.convert(), stream()).convert<Long>()
        }
        return if (count <= 0L) -1 else count.toInt()
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        val count = bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(offset), 1.convert(), length.convert(), stream()).convert<Long>()
        }
        if (count.toInt() != length) throw IOException("short write")
    }

    override fun flush() {
        fflush(stream())
    }

    override fun close() {
        if (closed) return
        closed = true
        fclose(file)
    }

    override fun position(): Long = fileTell(stream())

    override fun seek(position: Long) {
        if (!fileSeek(stream(), position, SEEK_SET)) throw IOException("seek failed")
    }

    override fun size(): Long {
        val here: Long = fileTell(stream())
        if (!fileSeek(stream(), 0L, SEEK_END)) throw IOException("seek failed")
        val end: Long = fileTell(stream())
        if (!fileSeek(stream(), here, SEEK_SET)) throw IOException("seek failed")
        return end
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformOpenFile(path: String, mode: PlatformFileMode): PlatformFileHandle {
    val file = fopen(path, mode.stdio) ?: throw IOException("$path: No such file or directory")
    return NativeFileHandle(file)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformDeleteFile(path: String) {
    if (remove(path) != 0) throw IOException("$path: No such file or directory")
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformRenameFile(from: String, to: String) {
    if (rename(from, to) != 0) throw IOException("$from: Failed to rename")
}

@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
private fun temporaryDirectory(): String {
    for (name in arrayOf("TMPDIR", "TEMP", "TMP")) {
        val value = getenv(name)?.toKString()
        if (!value.isNullOrEmpty()) return value.trimEnd('/', '\\')
    }
    return if (NativePlatform.osFamily == OsFamily.WINDOWS) "." else "/tmp"
}

private var temporaryFileCounter: Long = 0

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
internal actual fun platformTempFilePath(): String {
    val separator = if (NativePlatform.osFamily == OsFamily.WINDOWS) "\\" else "/"
    val unique = currentTimeMillis().toString(16) + (temporaryFileCounter++).toString(16)
    return temporaryDirectory() + separator + "luak" + unique + ".tmp"
}

@OptIn(ExperimentalForeignApi::class)
private object NativeStandardInput : InputStream() {
    override fun read(): Int {
        val byte = ByteArray(1)
        return if (read(byte, 0, 1) < 0) -1 else byte[0].toInt() and 0xff
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val count = bytes.usePinned { pinned ->
            fread(pinned.addressOf(offset), 1.convert(), length.convert(), stdin!!).convert<Long>()
        }
        return if (count <= 0L) -1 else count.toInt()
    }
}

internal actual fun platformStandardInput(): InputStream? = NativeStandardInput
