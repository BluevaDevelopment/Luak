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
import platform.posix._fseeki64
import platform.posix._ftelli64

// Windows keeps C `long` at 32 bits, so plain fseek/ftell would cap file
// offsets at 2 GB; the _i64 pair is the CRT's 64-bit equivalent.

@OptIn(ExperimentalForeignApi::class)
internal actual fun fileSeek(file: CPointer<FILE>, offset: Long, whence: Int): Boolean =
    _fseeki64(file, offset.convert(), whence) == 0

@OptIn(ExperimentalForeignApi::class)
internal actual fun fileTell(file: CPointer<FILE>): Long = _ftelli64(file).convert()
