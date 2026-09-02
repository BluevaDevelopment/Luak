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

import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop

// Filesystem primitives for targets hosted by a JavaScript engine (Kotlin/JS
// and Kotlin/Wasm-JS), backed by node:fs. A browser host has no `require`, so
// every entry point below degrades to "unsupported" rather than failing to
// load: platformFilesSupported reports false and the operations raise
// IOException, which io.open surfaces as Lua's usual nil, message pair.
//
// Bytes cross the Kotlin/JS boundary as latin-1 strings (one code unit per
// byte, values 0-255) because that is the only binary-safe representation
// both Kotlin/JS and Kotlin/Wasm-JS can pass through @JsFun unchanged.

private const val EOF_MARKER = ""

private fun ByteArray.toLatin1(offset: Int, length: Int): String {
    val text = StringBuilder(length)
    for (index in offset until offset + length) text.append((this[index].toInt() and 0xff).toChar())
    return text.toString()
}

private fun String.fromLatin1(): ByteArray = ByteArray(length) { this[it].code.toByte() }

internal actual val platformFilesSupported: Boolean = nodeFsAvailable()

private class NodeFileHandle(
    private val descriptor: Int,
    private val path: String,
    private val appends: Boolean,
) : PlatformFileHandle {
    private var offset: Long = 0
    private var closed = false

    private fun active(): Int {
        if (closed) throw IOException("$path: file is closed")
        return descriptor
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val chunk = nodeRead(active(), this.offset.toDouble(), length)
            ?: throw IOException("$path: read failed")
        if (chunk == EOF_MARKER) return -1
        val decoded = chunk.fromLatin1()
        decoded.copyInto(bytes, offset, 0, decoded.size)
        this.offset += decoded.size
        return decoded.size
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        val target = if (appends) size() else this.offset
        val written = nodeWrite(active(), bytes.toLatin1(offset, length), target.toDouble())
        if (written < 0) throw IOException("$path: write failed")
        this.offset = target + written
    }

    override fun flush() {
        nodeFlush(active())
    }

    override fun close() {
        if (closed) return
        closed = true
        nodeClose(descriptor)
    }

    override fun position(): Long = offset

    override fun seek(position: Long) {
        offset = position
    }

    override fun size(): Long {
        val bytes = nodeSize(active())
        if (bytes < 0) throw IOException("$path: cannot determine size")
        return bytes.toLong()
    }
}

internal actual fun platformOpenFile(path: String, mode: PlatformFileMode): PlatformFileHandle {
    if (!platformFilesSupported) throw IOException("$path: no filesystem on this host")
    val flags = when (mode) {
        PlatformFileMode.READ -> "r"
        PlatformFileMode.WRITE -> "w"
        PlatformFileMode.APPEND -> "a"
        PlatformFileMode.READ_WRITE -> "r+"
        PlatformFileMode.READ_WRITE_TRUNCATE -> "w+"
        PlatformFileMode.READ_APPEND -> "a+"
    }
    val descriptor = nodeOpen(path, flags)
    if (descriptor < 0) throw IOException("$path: No such file or directory")
    return NodeFileHandle(descriptor, path, mode.appends)
}

internal actual fun platformDeleteFile(path: String) {
    if (!platformFilesSupported) throw IOException("$path: no filesystem on this host")
    if (!nodeUnlink(path)) throw IOException("$path: No such file or directory")
}

internal actual fun platformRenameFile(from: String, to: String) {
    if (!platformFilesSupported) throw IOException("$from: no filesystem on this host")
    if (!nodeRename(from, to)) throw IOException("$from: Failed to rename")
}

private var temporaryFileCounter: Int = 0

internal actual fun platformTempFilePath(): String {
    val directory = nodeTempDirectory() ?: throw IOException("no temporary directory on this host")
    val unique = nodeProcessId().toString(16) + (temporaryFileCounter++).toString(16)
    return "$directory/luak$unique.tmp"
}

private object NodeStandardInput : InputStream() {
    override fun read(): Int {
        val byte = ByteArray(1)
        return if (read(byte, 0, 1) < 0) -1 else byte[0].toInt() and 0xff
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val chunk = nodeReadStandardInput(length) ?: return -1
        if (chunk == EOF_MARKER) return -1
        val decoded = chunk.fromLatin1()
        decoded.copyInto(bytes, offset, 0, decoded.size)
        return decoded.size
    }
}

internal actual fun platformStandardInput(): InputStream? =
    if (platformFilesSupported) NodeStandardInput else null

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => typeof require !== \"undefined\" && !!require(\"node:fs\")")
private external fun nodeFsAvailable(): Boolean

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (path, flags) => {
        try {
            return require("node:fs").openSync(path, flags);
        } catch (_) {
            return -1;
        }
    }
    """
)
private external fun nodeOpen(path: String, flags: String): Int

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (fd, position, length) => {
        try {
            const buffer = Buffer.alloc(length);
            const read = require("node:fs").readSync(fd, buffer, 0, length, position);
            return buffer.toString("latin1", 0, read);
        } catch (_) {
            return null;
        }
    }
    """
)
private external fun nodeRead(fd: Int, position: Double, length: Int): String?

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (fd, text, position) => {
        try {
            const buffer = Buffer.from(text, "latin1");
            return require("node:fs").writeSync(fd, buffer, 0, buffer.length, position);
        } catch (_) {
            return -1;
        }
    }
    """
)
private external fun nodeWrite(fd: Int, text: String, position: Double): Int

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (fd) => {
        try {
            require("node:fs").fsyncSync(fd);
        } catch (_) {
        }
    }
    """
)
private external fun nodeFlush(fd: Int)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (fd) => {
        try {
            require("node:fs").closeSync(fd);
        } catch (_) {
        }
    }
    """
)
private external fun nodeClose(fd: Int)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (fd) => {
        try {
            return require("node:fs").fstatSync(fd).size;
        } catch (_) {
            return -1;
        }
    }
    """
)
private external fun nodeSize(fd: Int): Double

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (path) => {
        try {
            require("node:fs").unlinkSync(path);
            return true;
        } catch (_) {
            return false;
        }
    }
    """
)
private external fun nodeUnlink(path: String): Boolean

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (from, to) => {
        try {
            require("node:fs").renameSync(from, to);
            return true;
        } catch (_) {
            return false;
        }
    }
    """
)
private external fun nodeRename(from: String, to: String): Boolean

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    () => {
        try {
            return require("node:os").tmpdir();
        } catch (_) {
            return null;
        }
    }
    """
)
private external fun nodeTempDirectory(): String?

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => (typeof process !== \"undefined\" && process.pid) ? process.pid : 0")
private external fun nodeProcessId(): Int

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (length) => {
        try {
            const buffer = Buffer.alloc(length);
            const read = require("node:fs").readSync(0, buffer, 0, length, null);
            return buffer.toString("latin1", 0, read);
        } catch (_) {
            return null;
        }
    }
    """
)
private external fun nodeReadStandardInput(length: Int): String?
