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

import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * standardOutput()/standardError()/platformResource() for a raw WASI
 * (`wasi_snapshot_preview1`) host: no JS engine, no `node:fs`, no `process` -
 * just the WASI syscalls every conforming host (wasmtime, wasmer, Node's
 * `node:wasi`, ...) implements, declared in `WasiSyscalls.wasmWasi.kt`.
 *
 * Capability model: WASI grants filesystem access only through pre-opened
 * directory file descriptors the *host* chooses to hand the module (e.g.
 * `wasmtime run --dir=. module.wasm`, or an equivalent `preopens` entry for
 * an embedding host). [platformResource] walks those pre-opens and tries
 * `path_open` against each, so it works regardless of what name the host gave
 * its preopened directory. If the host granted no filesystem capability at
 * all, every lookup deterministically returns null (matching the
 * `expect fun platformResource(...): InputStream?` contract) rather than
 * hanging or throwing a host-specific error.
 */

private const val READ_CHUNK_SIZE = 4096

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiWrite(fd: Int, bytes: ByteArray, offset: Int, length: Int) {
    if (length == 0) return
    withScopedMemoryAllocator { allocator ->
        val dataPtr = allocator.allocate(length)
        var cursor = dataPtr
        for (index in offset until offset + length) {
            cursor.storeByte(bytes[index])
            cursor += 1
        }
        val iovPtr = allocator.allocateIovec(dataPtr, length)
        val resultPtr = allocator.allocate(4)
        val errno = wasiFdWrite(fd, iovPtr.address.toInt(), 1, resultPtr.address.toInt())
        if (errno != WASI_ERRNO_SUCCESS) throw IOException("WASI fd_write failed with errno $errno")
    }
}

private class WasiOutputStream(private val fd: Int) : OutputStream() {
    override fun write(byte: Int) = wasiWrite(fd, byteArrayOf(byte.toByte()), 0, 1)
    override fun write(bytes: ByteArray, offset: Int, length: Int) = wasiWrite(fd, bytes, offset, length)
}

private val wasiStandardOutput = PrintStream(WasiOutputStream(WASI_STDOUT_FD))
private val wasiStandardError = PrintStream(WasiOutputStream(WASI_STDERR_FD))

actual fun standardOutput(): PrintStream = wasiStandardOutput
actual fun standardError(): PrintStream = wasiStandardError

@OptIn(UnsafeWasmMemoryApi::class)
private fun readAllAndClose(allocator: MemoryAllocator, fd: Int): ByteArray {
    try {
        val out = ByteArrayOutputStream()
        val bufPtr = allocator.allocate(READ_CHUNK_SIZE)
        val iovPtr = allocator.allocateIovec(bufPtr, READ_CHUNK_SIZE)
        val resultPtr = allocator.allocate(4)
        while (true) {
            val errno = wasiFdRead(fd, iovPtr.address.toInt(), 1, resultPtr.address.toInt())
            if (errno != WASI_ERRNO_SUCCESS) break
            val count = resultPtr.loadInt()
            if (count <= 0) break
            var cursor = bufPtr
            repeat(count) {
                out.write(cursor.loadByte().toInt() and 0xff)
                cursor += 1
            }
        }
        return out.toByteArray()
    } finally {
        wasiFdClose(fd)
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
actual fun platformResource(name: String): InputStream? {
    return withScopedMemoryAllocator { allocator ->
        val fd = withEachPreopen(allocator) { dirFd ->
            wasiOpenAt(allocator, dirFd, name, WASI_RIGHT_FD_READ, oFlags = 0)
        } ?: return@withScopedMemoryAllocator null
        ByteArrayInputStream(readAllAndClose(allocator, fd))
    }
}
