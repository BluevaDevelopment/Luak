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

import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WeakReferenceNativeTest {
    private class Payload(val value: Int)

    // Referent must be local to a returned function, or its slot stays live.
    private fun weakRefToUnreachablePayload(): WeakReference<Payload> {
        val strong = Payload(42)
        val weak = WeakReference(strong)
        assertNotNull(weak.get())
        return weak
    }

    @OptIn(NativeRuntimeApi::class)
    @Test
    fun getReturnsNullOnceTheReferentIsUnreachable() {
        val weak = weakRefToUnreachablePayload()
        GC.collect()
        assertNull(weak.get())
    }
}
