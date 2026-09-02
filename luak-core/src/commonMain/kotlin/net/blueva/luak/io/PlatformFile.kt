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

/**
 * Host filesystem primitives behind the shared `io` and `os` libraries.
 *
 * This is deliberately the smallest surface [net.blueva.luak.lib.IoLib] and
 * [net.blueva.luak.lib.OsLib] need in order to live entirely in `commonMain`:
 * a random-access byte handle plus open/delete/rename/temp-name. Everything
 * else - buffering, Lua read formats, `file:lines()`, the `file` userdata -
 * is shared code on top of it.
 *
 * Targets whose host grants no filesystem access report
 * [platformFilesSupported] `== false` and throw [IOException] from the
 * operations, which surfaces in Lua as the standard `nil, message` result
 * rather than a crash.
 */

/**
 * The six ways Lua's `io.open` can open a file, named after the equivalent C
 * `fopen` mode so each platform can map them without re-deriving the rules.
 */
internal enum class PlatformFileMode(val stdio: String) {
    /** `"r"` - read an existing file. */
    READ("rb"),

    /** `"w"` - create or truncate, write only. */
    WRITE("wb"),

    /** `"a"` - create if needed, every write goes to the end. */
    APPEND("ab"),

    /** `"r+"` - read and write an existing file, no truncation. */
    READ_WRITE("r+b"),

    /** `"w+"` - create or truncate, read and write. */
    READ_WRITE_TRUNCATE("w+b"),

    /** `"a+"` - read anywhere, every write goes to the end. */
    READ_APPEND("a+b"),
    ;

    val appends: Boolean get() = this == APPEND || this == READ_APPEND
}

/** Random-access byte handle over one open host file. */
internal interface PlatformFileHandle {
    /** Reads up to [length] bytes into [bytes]; returns the count, or -1 at end of file. */
    fun read(bytes: ByteArray, offset: Int, length: Int): Int

    /** Writes [length] bytes from [bytes] at the current position. */
    fun write(bytes: ByteArray, offset: Int, length: Int)

    /** Pushes any buffered bytes to the host. */
    fun flush()

    /** Releases the handle; further use throws [IOException]. */
    fun close()

    /** Current read/write offset, in bytes from the start of the file. */
    fun position(): Long

    /** Moves the read/write offset to [position] bytes from the start of the file. */
    fun seek(position: Long)

    /** Total size of the file, in bytes. */
    fun size(): Long
}

/** Whether this target can open host files at all. */
internal expect val platformFilesSupported: Boolean

/** Opens [path] in [mode], or throws [IOException] if the host refuses. */
internal expect fun platformOpenFile(path: String, mode: PlatformFileMode): PlatformFileHandle

/** Deletes [path], or throws [IOException] if the host refuses. */
internal expect fun platformDeleteFile(path: String)

/** Renames [from] to [to], or throws [IOException] if the host refuses. */
internal expect fun platformRenameFile(from: String, to: String)

/** A path usable for a new temporary file, or throws [IOException] if the host has nowhere to put one. */
internal expect fun platformTempFilePath(): String

/** Standard input as a stream, or null on hosts that have none. */
internal expect fun platformStandardInput(): InputStream?
