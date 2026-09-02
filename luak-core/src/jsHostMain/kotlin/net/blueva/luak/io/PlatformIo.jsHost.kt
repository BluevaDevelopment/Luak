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
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.length
import kotlin.js.toInt

// standardOutput()/standardError()/platformResource() for targets hosted by
// a JavaScript engine (Kotlin/JS and Kotlin/Wasm-JS, both tested on Node).
// This is deliberately *not* shared with wasmWasiMain: a WASI host has no
// `process`/`console`/`require("node:fs")` at all, so these JS-interop-based
// implementations would be meaningless there - see PlatformIo.wasmWasi.kt for
// the WASI-native equivalent (raw wasi_snapshot_preview1 syscalls).

private class JavaScriptOutputStream(
    private val error: Boolean,
) : OutputStream() {
    override fun write(byte: Int) {
        writeJavaScriptConsole(byteArrayOf(byte.toByte()).decodeToString(), error)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        writeJavaScriptConsole(bytes.decodeToString(offset, offset + length), error)
    }
}

private val javaScriptStandardOutput = PrintStream(JavaScriptOutputStream(error = false))
private val javaScriptStandardError = PrintStream(JavaScriptOutputStream(error = true))

actual fun standardOutput(): PrintStream = javaScriptStandardOutput
actual fun standardError(): PrintStream = javaScriptStandardError

@OptIn(ExperimentalWasmJsInterop::class)
actual fun platformResource(name: String): InputStream? {
    val source = readNodeResource(name) ?: return null
    val bytes = ByteArray(source.length) { index -> source[index]!!.toInt().toByte() }
    return ByteArrayInputStream(bytes)
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (text, error) => {
        if (typeof process !== "undefined" && process.stdout && process.stderr) {
            (error ? process.stderr : process.stdout).write(text);
        } else {
            (error ? console.error : console.log)(text);
        }
    }
    """
)
private external fun writeJavaScriptConsole(text: String, error: Boolean)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (path) => {
        try {
            if (typeof require === "undefined") return null;
            return Array.from(require("node:fs").readFileSync(path));
        } catch (_) {
            return null;
        }
    }
    """
)
private external fun readNodeResource(path: String): JsArray<JsNumber>?
