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

import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop

// Real process environment lookup for the JS-hosted targets, so os.getenv
// answers the same way it does on JVM and Native. A browser host has no
// `process`, in which case this reports null and os.getenv returns nil.
internal actual fun platformEnvironment(name: String): String? = javaScriptEnvironment(name)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (name) => {
        try {
            if (typeof process === "undefined" || !process.env) return null;
            const value = process.env[name];
            return value === undefined ? null : value;
        } catch (_) {
            return null;
        }
    }
    """
)
private external fun javaScriptEnvironment(name: String): String?
