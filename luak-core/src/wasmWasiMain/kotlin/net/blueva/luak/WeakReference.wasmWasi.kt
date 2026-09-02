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

// Same trade-off as WeakReference.nonJvm.kt (JS/Wasm-JS): Kotlin/Wasm has no
// portable weak-reference primitive available here either, so this holds a
// strong reference. get() never returns null.
actual class WeakReference<T : Any> actual constructor(private val referent: T) {
    actual fun get(): T? = referent
}
