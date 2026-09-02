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
import kotlinx.cinterop.convert
import platform.posix.FILE
import platform.posix.fseeko
import platform.posix.ftello

// On Linux and Apple targets off_t is 64-bit, so fseeko/ftello carry the whole
// range a Lua file offset can hold.

@OptIn(ExperimentalForeignApi::class)
internal actual fun fileSeek(file: CPointer<FILE>, offset: Long, whence: Int): Boolean =
    fseeko(file, offset.convert(), whence) == 0

@OptIn(ExperimentalForeignApi::class)
internal actual fun fileTell(file: CPointer<FILE>): Long = ftello(file).convert()
