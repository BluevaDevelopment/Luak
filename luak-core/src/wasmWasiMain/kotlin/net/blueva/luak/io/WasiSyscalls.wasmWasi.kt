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

import kotlin.wasm.ExperimentalWasmInterop
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * The `wasi_snapshot_preview1` syscalls Luak uses, declared once so both
 * the stream layer (`PlatformIo.wasmWasi.kt`) and the filesystem layer
 * (`PlatformFile.wasmWasi.kt`) share a single Wasm import per function.
 *
 * These are the same raw imports the Kotlin/Wasm-WASI standard library itself
 * uses for `println()`, so a conforming host needs no extra configuration
 * beyond what any Kotlin/Wasm-WASI program already requires.
 */

internal const val WASI_ERRNO_SUCCESS = 0
internal const val WASI_PREOPENTYPE_DIR = 0
internal const val WASI_STDIN_FD = 0
internal const val WASI_STDOUT_FD = 1
internal const val WASI_STDERR_FD = 2
internal const val WASI_FIRST_PREOPEN_FD = 3
internal const val WASI_MAX_PREOPEN_FD_TO_TRY = 64

internal const val WASI_OFLAG_CREAT = 1
internal const val WASI_OFLAG_TRUNC = 8

internal const val WASI_RIGHT_FD_DATASYNC = 1L shl 0
internal const val WASI_RIGHT_FD_READ = 1L shl 1
internal const val WASI_RIGHT_FD_SEEK = 1L shl 2
internal const val WASI_RIGHT_FD_SYNC = 1L shl 4
internal const val WASI_RIGHT_FD_TELL = 1L shl 5
internal const val WASI_RIGHT_FD_WRITE = 1L shl 6
internal const val WASI_RIGHT_PATH_CREATE_FILE = 1L shl 10
internal const val WASI_RIGHT_PATH_RENAME_SOURCE = 1L shl 16
internal const val WASI_RIGHT_PATH_RENAME_TARGET = 1L shl 17
internal const val WASI_RIGHT_FD_FILESTAT_GET = 1L shl 21
internal const val WASI_RIGHT_FD_FILESTAT_SET_SIZE = 1L shl 22
internal const val WASI_RIGHT_PATH_UNLINK_FILE = 1L shl 26

/** Byte offset of `size` inside a 64-byte `__wasi_filestat_t`. */
internal const val WASI_FILESTAT_SIZE_OFFSET = 32
internal const val WASI_FILESTAT_BYTES = 64

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_write")
internal external fun wasiFdWrite(fd: Int, iovsPtr: Int, iovsLen: Int, resultPtr: Int): Int

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_read")
internal external fun wasiFdRead(fd: Int, iovsPtr: Int, iovsLen: Int, resultPtr: Int): Int

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_pread")
internal external fun wasiFdPread(fd: Int, iovsPtr: Int, iovsLen: Int, offset: Long, resultPtr: Int): Int

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_pwrite")
internal external fun wasiFdPwrite(fd: Int, iovsPtr: Int, iovsLen: Int, offset: Long, resultPtr: Int): Int

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_close")
internal external fun wasiFdClose(fd: Int): Int

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_sync")
internal external fun wasiFdSync(fd: Int): Int

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_filestat_get")
internal external fun wasiFdFilestatGet(fd: Int, resultPtr: Int): Int

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_prestat_get")
internal external fun wasiFdPrestatGet(fd: Int, resultPtr: Int): Int

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "path_open")
internal external fun wasiPathOpen(
    dirFd: Int,
    dirFlags: Int,
    pathPtr: Int,
    pathLen: Int,
    oFlags: Int,
    fsRightsBase: Long,
    fsRightsInheriting: Long,
    fdFlags: Int,
    resultPtr: Int,
): Int

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "path_unlink_file")
internal external fun wasiPathUnlinkFile(dirFd: Int, pathPtr: Int, pathLen: Int): Int

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "path_rename")
internal external fun wasiPathRename(
    oldDirFd: Int,
    oldPathPtr: Int,
    oldPathLen: Int,
    newDirFd: Int,
    newPathPtr: Int,
    newPathLen: Int,
): Int

@OptIn(UnsafeWasmMemoryApi::class)
internal fun Pointer.storeBytes(bytes: ByteArray) {
    var cursor = this
    for (byte in bytes) {
        cursor.storeByte(byte)
        cursor += 1
    }
}

/** Copies [length] bytes starting at this pointer into [bytes] at [offset]. */
@OptIn(UnsafeWasmMemoryApi::class)
internal fun Pointer.loadBytes(bytes: ByteArray, offset: Int, length: Int) {
    var cursor = this
    for (index in offset until offset + length) {
        bytes[index] = cursor.loadByte()
        cursor += 1
    }
}

/** Allocates a WASI `iovec` (pointer + length pair) describing [dataPtr]. */
@OptIn(UnsafeWasmMemoryApi::class)
internal fun MemoryAllocator.allocateIovec(dataPtr: Pointer, length: Int): Pointer {
    val iovPtr = allocate(8)
    iovPtr.storeInt(dataPtr.address.toInt())
    (iovPtr + 4).storeInt(length)
    return iovPtr
}

/**
 * Runs [action] against every pre-opened directory the host granted, in fd
 * order, returning the first non-null result. WASI hands out filesystem
 * capabilities only as pre-opened directory fds (`wasmtime run --dir=.`, or an
 * embedding host's `preopens`), so path-based operations have to be tried
 * against each one; if the host granted none, this returns null and callers
 * report an ordinary "no such file" instead of a host-specific error.
 */
@OptIn(UnsafeWasmMemoryApi::class)
internal fun <T : Any> withEachPreopen(allocator: MemoryAllocator, action: (Int) -> T?): T? {
    var fd = WASI_FIRST_PREOPEN_FD
    while (fd < WASI_MAX_PREOPEN_FD_TO_TRY) {
        // __wasi_prestat_t: { tag: u8, [3 bytes padding], dir.pr_name_len: u32 }, 8 bytes.
        val prestatPtr = allocator.allocate(8)
        if (wasiFdPrestatGet(fd, prestatPtr.address.toInt()) != WASI_ERRNO_SUCCESS) break
        if (prestatPtr.loadByte().toInt() == WASI_PREOPENTYPE_DIR) {
            action(fd)?.let { return it }
        }
        fd++
    }
    return null
}

/**
 * Opens [path] relative to the pre-opened directory [dirFd] with [rights] and
 * [oFlags]; returns the new fd, or null (never throws) on any failure, so
 * callers can keep trying the remaining pre-opens.
 */
@OptIn(UnsafeWasmMemoryApi::class)
internal fun wasiOpenAt(
    allocator: MemoryAllocator,
    dirFd: Int,
    path: String,
    rights: Long,
    oFlags: Int,
): Int? {
    val pathBytes = path.encodeToByteArray()
    val pathPtr = allocator.allocate(pathBytes.size.coerceAtLeast(1))
    pathPtr.storeBytes(pathBytes)
    val resultPtr = allocator.allocate(4)
    val errno = wasiPathOpen(
        dirFd = dirFd,
        dirFlags = 0,
        pathPtr = pathPtr.address.toInt(),
        pathLen = pathBytes.size,
        oFlags = oFlags,
        fsRightsBase = rights,
        fsRightsInheriting = 0L,
        fdFlags = 0,
        resultPtr = resultPtr.address.toInt(),
    )
    if (errno != WASI_ERRNO_SUCCESS) return null
    return resultPtr.loadInt()
}

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "environ_sizes_get")
internal external fun wasiEnvironSizesGet(countPtr: Int, bufferSizePtr: Int): Int

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "environ_get")
internal external fun wasiEnvironGet(environPtr: Int, bufferPtr: Int): Int

/**
 * Reads the host environment WASI handed the module and returns the value of
 * [name], or null when the host passed no environment or no such entry.
 * Entries arrive as a pointer array into one NUL-separated `KEY=VALUE` buffer.
 */
@OptIn(UnsafeWasmMemoryApi::class)
internal fun wasiEnvironmentValue(name: String): String? = withScopedMemoryAllocator { allocator ->
    val countPtr = allocator.allocate(4)
    val bufferSizePtr = allocator.allocate(4)
    if (wasiEnvironSizesGet(countPtr.address.toInt(), bufferSizePtr.address.toInt()) != WASI_ERRNO_SUCCESS) {
        return@withScopedMemoryAllocator null
    }
    val count = countPtr.loadInt()
    val bufferSize = bufferSizePtr.loadInt()
    if (count <= 0 || bufferSize <= 0) return@withScopedMemoryAllocator null

    val environPtr = allocator.allocate(count * 4)
    val bufferPtr = allocator.allocate(bufferSize)
    if (wasiEnvironGet(environPtr.address.toInt(), bufferPtr.address.toInt()) != WASI_ERRNO_SUCCESS) {
        return@withScopedMemoryAllocator null
    }

    val prefix = "$name=".encodeToByteArray()
    for (index in 0 until count) {
        val entryAddress = (environPtr + index * 4).loadInt()
        var cursor = bufferPtr + (entryAddress - bufferPtr.address.toInt())
        val entry = ByteArrayOutputStream()
        while (true) {
            val byte = cursor.loadByte()
            if (byte.toInt() == 0) break
            entry.write(byte.toInt() and 0xff)
            cursor += 1
        }
        val bytes = entry.toByteArray()
        if (bytes.size < prefix.size) continue
        var matches = true
        for (position in prefix.indices) {
            if (bytes[position] != prefix[position]) {
                matches = false
                break
            }
        }
        if (matches) {
            return@withScopedMemoryAllocator bytes.decodeToString(prefix.size, bytes.size)
        }
    }
    null
}
