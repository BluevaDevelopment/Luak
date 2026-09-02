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

import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

// Filesystem primitives over raw wasi_snapshot_preview1, sharing the syscall
// imports declared in WasiSyscalls.wasmWasi.kt.
//
// Two WASI facts shape this implementation:
//
//  * Paths are only meaningful relative to a pre-opened directory fd the host
//    handed the module, so every path operation walks the pre-opens. A host
//    that granted none leaves the module with no filesystem at all, which
//    surfaces as an IOException and therefore as Lua's nil, message result.
//  * There is no seek-then-read; fd_pread/fd_pwrite take the offset directly.
//    The handle tracks its own position, which also makes append mode a plain
//    "write at the current size" rather than an fd flag.

private const val READ_RIGHTS =
    WASI_RIGHT_FD_READ or WASI_RIGHT_FD_SEEK or WASI_RIGHT_FD_TELL or WASI_RIGHT_FD_FILESTAT_GET

private const val WRITE_RIGHTS =
    WASI_RIGHT_FD_WRITE or WASI_RIGHT_FD_SEEK or WASI_RIGHT_FD_TELL or
        WASI_RIGHT_FD_FILESTAT_GET or WASI_RIGHT_FD_FILESTAT_SET_SIZE or
        WASI_RIGHT_FD_DATASYNC or WASI_RIGHT_FD_SYNC

@OptIn(UnsafeWasmMemoryApi::class)
internal actual val platformFilesSupported: Boolean
    get() = withScopedMemoryAllocator { allocator -> withEachPreopen(allocator) { true } } ?: false

private class WasiFileHandle(
    private val fd: Int,
    private val path: String,
    private val appends: Boolean,
) : PlatformFileHandle {
    private var offset: Long = 0
    private var closed = false

    private fun active(): Int {
        if (closed) throw IOException("$path: file is closed")
        return fd
    }

    @OptIn(UnsafeWasmMemoryApi::class)
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val descriptor = active()
        val count = withScopedMemoryAllocator { allocator ->
            val bufPtr = allocator.allocate(length)
            val iovPtr = allocator.allocateIovec(bufPtr, length)
            val resultPtr = allocator.allocate(4)
            val errno = wasiFdPread(
                descriptor,
                iovPtr.address.toInt(),
                1,
                this.offset,
                resultPtr.address.toInt(),
            )
            if (errno != WASI_ERRNO_SUCCESS) throw IOException("$path: WASI fd_pread failed with errno $errno")
            val read = resultPtr.loadInt()
            if (read > 0) bufPtr.loadBytes(bytes, offset, read)
            read
        }
        if (count <= 0) return -1
        this.offset += count
        return count
    }

    @OptIn(UnsafeWasmMemoryApi::class)
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        val descriptor = active()
        val target = if (appends) size() else this.offset
        val written = withScopedMemoryAllocator { allocator ->
            val dataPtr = allocator.allocate(length)
            var cursor = dataPtr
            for (index in offset until offset + length) {
                cursor.storeByte(bytes[index])
                cursor += 1
            }
            val iovPtr = allocator.allocateIovec(dataPtr, length)
            val resultPtr = allocator.allocate(4)
            val errno = wasiFdPwrite(
                descriptor,
                iovPtr.address.toInt(),
                1,
                target,
                resultPtr.address.toInt(),
            )
            if (errno != WASI_ERRNO_SUCCESS) throw IOException("$path: WASI fd_pwrite failed with errno $errno")
            resultPtr.loadInt()
        }
        this.offset = target + written
    }

    override fun flush() {
        wasiFdSync(active())
    }

    override fun close() {
        if (closed) return
        closed = true
        wasiFdClose(fd)
    }

    override fun position(): Long = offset

    override fun seek(position: Long) {
        offset = position
    }

    @OptIn(UnsafeWasmMemoryApi::class)
    override fun size(): Long {
        val descriptor = active()
        return withScopedMemoryAllocator { allocator ->
            val statPtr = allocator.allocate(WASI_FILESTAT_BYTES)
            val errno = wasiFdFilestatGet(descriptor, statPtr.address.toInt())
            if (errno != WASI_ERRNO_SUCCESS) throw IOException("$path: WASI fd_filestat_get failed with errno $errno")
            (statPtr + WASI_FILESTAT_SIZE_OFFSET).loadLong()
        }
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
internal actual fun platformOpenFile(path: String, mode: PlatformFileMode): PlatformFileHandle {
    val rights = when (mode) {
        PlatformFileMode.READ -> READ_RIGHTS
        PlatformFileMode.WRITE, PlatformFileMode.APPEND -> WRITE_RIGHTS
        else -> READ_RIGHTS or WRITE_RIGHTS
    }
    val oFlags = when (mode) {
        PlatformFileMode.READ, PlatformFileMode.READ_WRITE -> 0
        PlatformFileMode.APPEND, PlatformFileMode.READ_APPEND -> WASI_OFLAG_CREAT
        PlatformFileMode.WRITE, PlatformFileMode.READ_WRITE_TRUNCATE -> WASI_OFLAG_CREAT or WASI_OFLAG_TRUNC
    }
    val fd = withScopedMemoryAllocator { allocator ->
        withEachPreopen(allocator) { dirFd -> wasiOpenAt(allocator, dirFd, path, rights, oFlags) }
    } ?: throw IOException("$path: No such file or directory")
    return WasiFileHandle(fd, path, mode.appends)
}

@OptIn(UnsafeWasmMemoryApi::class)
internal actual fun platformDeleteFile(path: String) {
    val removed = withScopedMemoryAllocator { allocator ->
        withEachPreopen(allocator) { dirFd ->
            val pathBytes = path.encodeToByteArray()
            val pathPtr = allocator.allocate(pathBytes.size.coerceAtLeast(1))
            pathPtr.storeBytes(pathBytes)
            val errno = wasiPathUnlinkFile(dirFd, pathPtr.address.toInt(), pathBytes.size)
            if (errno == WASI_ERRNO_SUCCESS) true else null
        }
    }
    if (removed != true) throw IOException("$path: No such file or directory")
}

@OptIn(UnsafeWasmMemoryApi::class)
internal actual fun platformRenameFile(from: String, to: String) {
    val renamed = withScopedMemoryAllocator { allocator ->
        withEachPreopen(allocator) { dirFd ->
            val fromBytes = from.encodeToByteArray()
            val fromPtr = allocator.allocate(fromBytes.size.coerceAtLeast(1))
            fromPtr.storeBytes(fromBytes)
            val toBytes = to.encodeToByteArray()
            val toPtr = allocator.allocate(toBytes.size.coerceAtLeast(1))
            toPtr.storeBytes(toBytes)
            val errno = wasiPathRename(
                dirFd,
                fromPtr.address.toInt(),
                fromBytes.size,
                dirFd,
                toPtr.address.toInt(),
                toBytes.size,
            )
            if (errno == WASI_ERRNO_SUCCESS) true else null
        }
    }
    if (renamed != true) throw IOException("$from: Failed to rename")
}

private var temporaryFileCounter: Int = 0

/**
 * WASI has no notion of a temporary directory - a module only ever sees the
 * pre-opens it was granted - so the name is relative and lands in whichever
 * pre-open accepts it.
 */
internal actual fun platformTempFilePath(): String {
    if (!platformFilesSupported) throw IOException("no filesystem granted to this WASI module")
    return "luak" + (temporaryFileCounter++).toString(16) + ".tmp"
}

@OptIn(UnsafeWasmMemoryApi::class)
private object WasiStandardInput : InputStream() {
    override fun read(): Int {
        val byte = ByteArray(1)
        return if (read(byte, 0, 1) < 0) -1 else byte[0].toInt() and 0xff
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val count = withScopedMemoryAllocator { allocator ->
            val bufPtr = allocator.allocate(length)
            val iovPtr = allocator.allocateIovec(bufPtr, length)
            val resultPtr = allocator.allocate(4)
            val errno = wasiFdRead(WASI_STDIN_FD, iovPtr.address.toInt(), 1, resultPtr.address.toInt())
            if (errno != WASI_ERRNO_SUCCESS) return@withScopedMemoryAllocator -1
            val read = resultPtr.loadInt()
            if (read > 0) bufPtr.loadBytes(bytes, offset, read)
            read
        }
        return if (count <= 0) -1 else count
    }
}

internal actual fun platformStandardInput(): InputStream? = WasiStandardInput
