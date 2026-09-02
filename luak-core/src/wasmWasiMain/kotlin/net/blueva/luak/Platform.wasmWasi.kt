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
package net.blueva.luak

import kotlin.reflect.KClass
import kotlin.wasm.ExperimentalWasmInterop
import kotlin.wasm.WasmImport

// Identical to Platform.nonJvm.kt (JS/Wasm-JS): none of this needs a JS host,
// so it's just as portable under WASI. Not shared via a common source set
// with jsHostMain/nonJvmMain to keep wasmWasiMain fully independent of any
// JS-hosted assumptions; this file is intentionally small to duplicate.
internal actual fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
internal actual fun platformProperty(name: String): String? = null
internal actual fun platformEnvironment(name: String): String? =
    net.blueva.luak.io.wasiEnvironmentValue(name)

@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "proc_exit")
private external fun wasiProcExit(code: Int): Unit

internal actual fun platformExit(code: Int) {
    wasiProcExit(code)
}

internal actual fun platformCollectGarbage() = Unit

// Nothing here can bring back an object the host is reclaiming, so an object
// is never handed to its `__gc` handler and the handler never runs.
internal actual fun watchForFinalization(target: LuaValue, pending: MutableList<LuaValue>): Any? = null

internal actual fun takeFinalized(pending: MutableList<LuaValue>): List<LuaValue> = emptyList()
internal actual fun platformUsedMemory(): Long = 0L
internal actual fun platformLoadLibrary(className: String, globals: Globals): LuaValue? = null
internal actual fun platformTypeName(type: KClass<*>): String = type.simpleName ?: "userdata"

/**
 * Always false: a Wasm stack overflow traps rather than arriving here as a
 * throwable, so there is nothing to translate.
 */
internal actual fun platformIsStackOverflow(failure: Throwable): Boolean = false
