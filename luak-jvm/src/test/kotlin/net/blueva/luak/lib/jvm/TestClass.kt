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
package net.blueva.luak.lib.jvm

class TestClass {
    private class PrivateImpl : TestInterface {
        @JvmField
        var public_field: String

        constructor() {
            this.public_field = "privateImpl-constructor"
        }

        internal constructor(f: String) {
            this.public_field = f
        }

        fun public_method(): String {
            return "privateImpl-" + public_field + "-public_method"
        }

        override fun interface_method(x: String?): String {
            return "privateImpl-" + public_field + "-interface_method(" + x + ")"
        }

        override fun toString(): String {
            return public_field
        }
    }

    fun create_PrivateImpl(f: String): TestInterface {
        return PrivateImpl(f)
    }

    fun get_PrivateImplClass(): Class<*> {
        return PrivateImpl::class.java
    }

    enum class SomeEnum {
        ValueOne,
        ValueTwo,
    }
}
