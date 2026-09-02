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
 *  Based on LuaJ (https://luaj.org)
 *  Original work Copyright (c) 2009 Luaj.org
 *  Modifications Copyright (c) 2026 Blueva Development
 *
 *  SPDX-License-Identifier: MIT
 ******************************************************************************/
package net.blueva.luak

import net.blueva.luak.LuaTable.Slot
import net.blueva.luak.LuaTable.StrongSlot
import net.blueva.luak.WeakReference

/**
 * Subclass of [LuaTable] that provides weak key and weak value semantics.
 * 
 * 
 * Normally these are not created directly, but indirectly when changing the mode
 * of a [LuaTable] as lua script executes.
 * 
 * 
 * However, calling the constructors directly when weak tables are required from
 * Java will reduce overhead.
 */
class WeakTable(private val weakkeys: Boolean, private val weakvalues: Boolean, backing: LuaValue?) : Metatable {
    private val backing: LuaValue?

    /**
     * Construct a table with weak keys, weak values, or both
     * @param weakkeys true to let the table have weak keys
     * @param weakvalues true to let the table have weak values
     */
    init {
        this.backing = backing
    }

    override fun useWeakKeys(): Boolean {
        return weakkeys
    }

    override fun useWeakValues(): Boolean {
        return weakvalues
    }

    override fun toLuaValue(): LuaValue? {
        return backing
    }

    override fun entry(key: LuaValue?, value: LuaValue?): Slot? {
        var value: LuaValue? = value
        value = value!!.strongvalue()
        if (value == null) return null
        if (weakkeys && !(key!!.isnumber() || key.isstring() || key.isboolean())) {
            if (weakvalues && !(value.isnumber() || value.isstring() || value.isboolean())) {
                return net.blueva.luak.WeakTable.WeakKeyAndValueSlot(key!!, value, null)
            } else {
                return net.blueva.luak.WeakTable.WeakKeySlot(key!!, value, null)
            }
        }
        if (weakvalues && !(value.isnumber() || value.isstring() || value.isboolean())) {
            return net.blueva.luak.WeakTable.WeakValueSlot(key, value, null)
        }
        return LuaTable.defaultEntry(key!!, value)
    }

    abstract class WeakSlot protected constructor(key: Any?, value: Any?, next: Slot?) : Slot {
        protected var key: Any?
        protected var value: Any?
        protected var next: Slot?

        init {
            this.key = key
            this.value = value
            this.next = next
        }

        abstract override fun keyindex(hashMask: Int): Int

        abstract fun set(value: LuaValue?): Slot?

        override fun first(): StrongSlot? {
            val key: LuaValue? = strongkey()
            val value: LuaValue? = strongvalue()
            if (key != null && value != null) {
                return LuaTable.NormalEntry(key, value)
            } else {
                this.key = null
                this.value = null
                return null
            }
        }

        override fun find(key: LuaValue?): StrongSlot? {
            val first: StrongSlot? = first()
            return if (first != null) first.find(key) else null
        }

        override fun keyeq(key: LuaValue?): Boolean {
            val first: StrongSlot? = first()
            return (first != null) && first.keyeq(key)
        }

        override fun rest(): Slot? {
            return next
        }

        override fun arraykey(max: Int): Int {
            // Integer keys can never be weak.
            return 0
        }

        override fun set(target: StrongSlot?, value: LuaValue?): Slot? {
            val key: LuaValue? = strongkey()
            if (key != null && target!!.find(key) != null) {
                return set(value)
            } else if (key != null) {
                // Our key is still good.
                next = next!!.set(target, value)
                return this
            } else {
                // our key was dropped, remove ourselves from the chain.
                return next!!.set(target, value)
            }
        }

        override fun add(entry: Slot?): Slot? {
            next = if (next != null) next!!.add(entry) else entry
            if (strongkey() != null && strongvalue() != null) {
                return this
            } else {
                return next
            }
        }

        override fun remove(target: StrongSlot?): Slot? {
            val key: LuaValue? = strongkey()
            if (key == null) {
                return next!!.remove(target)
            } else if (target!!.keyeq(key)) {
                this.value = null
                return this
            } else {
                next = next!!.remove(target)
                return this
            }
        }

        override fun relink(rest: Slot?): Slot? {
            if (strongkey() != null && strongvalue() != null) {
                if (rest == null && this.next == null) {
                    return this
                } else {
                    return copy(rest)
                }
            } else {
                return rest
            }
        }

        open fun strongkey(): LuaValue? {
            return key as LuaValue?
        }

        open fun strongvalue(): LuaValue? {
            return value as LuaValue?
        }

        protected abstract fun copy(next: Slot?): WeakSlot?
    }

    /**
     * An entry of a table whose keys are weak, which is to say an ephemeron.
     *
     * The value is held weakly here and strongly by the key, so that it lives
     * exactly as long as the key does. Holding it here instead would keep
     * alive every key its value happens to refer to, which is the difference
     * between a weak-key table and one that is merely inconvenient: a chain
     * of entries each pointing at the key of the next would then never go,
     * however little else referred to it. See [LuaValue.pinned].
     */
    internal class WeakKeySlot : WeakSlot {
        private val keyhash: Int

        constructor(
            key: LuaValue,
            value: LuaValue?,
            next: Slot?
        ) : super(
            net.blueva.luak.WeakTable.Companion.weaken(key),
            net.blueva.luak.WeakTable.Companion.weaken(value!!),
            next,
        ) {
            keyhash = key.hashCode()
            net.blueva.luak.WeakTable.Companion.pin(key, value)
        }

        protected constructor(copyFrom: WeakKeySlot, next: Slot?) : super(copyFrom.key, copyFrom.value, next) {
            this.keyhash = copyFrom.keyhash
        }

        override fun keyindex(mask: Int): Int {
            return LuaTable.hashmod(keyhash, mask)
        }

        override fun set(value: LuaValue?): Slot? {
            val key: LuaValue? = strongkey()
            if (key != null) {
                net.blueva.luak.WeakTable.Companion.unpin(
                    key,
                    net.blueva.luak.WeakTable.Companion.strengthen(this.value),
                )
                if (value != null) net.blueva.luak.WeakTable.Companion.pin(key, value)
            }
            this.value = if (value == null) null else net.blueva.luak.WeakTable.Companion.weaken(value)
            return this
        }

        override fun strongkey(): LuaValue? {
            return net.blueva.luak.WeakTable.Companion.strengthen(key)
        }

        override fun strongvalue(): LuaValue? {
            return net.blueva.luak.WeakTable.Companion.strengthen(value)
        }

        override fun copy(rest: Slot?): WeakSlot {
            return net.blueva.luak.WeakTable.WeakKeySlot(this, rest)
        }
    }

    internal class WeakValueSlot : WeakSlot {
        constructor(key: LuaValue?, value: LuaValue, next: Slot?) : super(
            key,
            net.blueva.luak.WeakTable.Companion.weaken(value),
            next
        )

        protected constructor(copyFrom: WeakValueSlot, next: Slot?) : super(copyFrom.key, copyFrom.value, next)

        override fun keyindex(mask: Int): Int {
            return LuaTable.hashSlot((strongkey())!!, mask)
        }

        override fun set(value: LuaValue?): Slot? {
            this.value = net.blueva.luak.WeakTable.Companion.weaken((value)!!)
            return this
        }

        override fun strongvalue(): LuaValue? {
            return net.blueva.luak.WeakTable.Companion.strengthen(value)
        }

        override fun copy(next: Slot?): WeakSlot {
            return net.blueva.luak.WeakTable.WeakValueSlot(this, next)
        }
    }

    internal class WeakKeyAndValueSlot : WeakSlot {
        private val keyhash: Int

        constructor(
            key: LuaValue,
            value: LuaValue,
            next: Slot?
        ) : super(
            net.blueva.luak.WeakTable.Companion.weaken(key),
            net.blueva.luak.WeakTable.Companion.weaken(value),
            next
        ) {
            keyhash = key.hashCode()
        }

        protected constructor(copyFrom: WeakKeyAndValueSlot, next: Slot?) : super(copyFrom.key, copyFrom.value, next) {
            keyhash = copyFrom.keyhash
        }

        override fun keyindex(hashMask: Int): Int {
            return LuaTable.hashmod(keyhash, hashMask)
        }

        override fun set(value: LuaValue?): Slot? {
            this.value = net.blueva.luak.WeakTable.Companion.weaken((value)!!)
            return this
        }

        override fun strongkey(): LuaValue? {
            return net.blueva.luak.WeakTable.Companion.strengthen(key)
        }

        override fun strongvalue(): LuaValue? {
            return net.blueva.luak.WeakTable.Companion.strengthen(value)
        }

        override fun copy(next: Slot?): WeakSlot {
            return net.blueva.luak.WeakTable.WeakKeyAndValueSlot(this, next)
        }
    }

    /** Internal class to implement weak values.
     * @see WeakTable
     */
    internal open class WeakValue(value: LuaValue) : LuaValue() {
        var ref: WeakReference<LuaValue>?

        init {
            ref = WeakReference(value)
        }

        override fun type(): Int {
            illegal("type", "weak value")
            return 0
        }

        override fun typename(): String? {
            illegal("typename", "weak value")
            return null
        }

        override fun toString(): String {
            return "weak<" + ref!!.get() + ">"
        }

        override fun strongvalue(): LuaValue? {
            val o: Any? = ref!!.get()
            return o as LuaValue?
        }

        override fun raweq(rhs: LuaValue?): Boolean {
            val rhs = rhs!!
            val o: Any? = ref!!.get()
            return o != null && rhs.raweq(o as LuaValue)
        }
    }

    /** Internal class to implement weak userdata values.
     * @see WeakTable
     */
    internal class WeakUserdata internal constructor(value: LuaValue) : WeakValue(value) {
        private val ob: WeakReference<Any>
        private val mt: LuaValue?

        init {
            ob = WeakReference(value.touserdata()!!)
            mt = value.getmetatable()
        }

        override fun strongvalue(): LuaValue? {
            val u: Any? = ref!!.get()
            if (u != null) return u as LuaValue
            val o: Any? = ob.get()
            if (o != null) {
                val ud: LuaValue? = LuaValue.userdataOf(o, mt)
                ref = WeakReference(ud!!)
                return (ud)!!
            } else {
                return null
            }
        }
    }

    override fun wrap(value: LuaValue?): LuaValue? {
        return if (weakvalues) net.blueva.luak.WeakTable.Companion.weaken(value!!) else value
    }

    override fun arrayget(array: Array<LuaValue?>?, index: Int): LuaValue? {
        var value: LuaValue? = array!![index]
        if (value != null) {
            value = net.blueva.luak.WeakTable.Companion.strengthen(value)
            if (value == null) {
                array[index] = null
            }
        }
        return value
    }

    companion object {
        fun make(weakkeys: Boolean, weakvalues: Boolean): LuaTable {
            val mode: LuaString?
            if (weakkeys && weakvalues) {
                mode = LuaString.valueOf("kv")
            } else if (weakkeys) {
                mode = LuaString.valueOf("k")
            } else if (weakvalues) {
                mode = LuaString.valueOf("v")
            } else {
                return LuaValue.tableOf()
            }
            val table: LuaTable = LuaValue.tableOf()
            val mt: LuaTable? = LuaValue.tableOf(arrayOf<LuaValue?>(LuaValue.MODE, mode))
            table.setmetatable(mt)
            return table
        }

        /**
         * Self-sent message to convert a value to its weak counterpart
         * @param value value to convert
         * @return [LuaValue] that is a strong or weak reference, depending on type of `value`
         */
        /**
         * Has [key] hold [value] for as long as it lives; see [WeakKeySlot].
         *
         * A key used in more than one table holds a list of what it keeps.
         */
        @Suppress("UNCHECKED_CAST")
        internal fun pin(key: LuaValue, value: LuaValue) {
            when (val held: Any? = key.pinned) {
                null -> key.pinned = value
                is ArrayList<*> -> (held as ArrayList<LuaValue>).add(value)
                else -> {
                    val list: ArrayList<LuaValue> = ArrayList(2)
                    list.add(held as LuaValue)
                    list.add(value)
                    key.pinned = list
                }
            }
        }

        /** Undoes one [pin]; the entry it belonged to is gone or replaced. */
        @Suppress("UNCHECKED_CAST")
        internal fun unpin(key: LuaValue, value: LuaValue?) {
            if (value == null) return
            val held: Any? = key.pinned
            if (held === value) {
                key.pinned = null
            } else if (held is ArrayList<*>) {
                val list: ArrayList<LuaValue> = held as ArrayList<LuaValue>
                var i = 0
                while (i < list.size) {
                    if (list[i] === value) {
                        list.removeAt(i)
                        return
                    }
                    i++
                }
            }
        }

        protected fun weaken(value: LuaValue): LuaValue {
            when (value.type()) {
                LuaValue.TFUNCTION, LuaValue.TTHREAD, LuaValue.TTABLE -> return net.blueva.luak.WeakTable.WeakValue(
                    value
                )

                LuaValue.TUSERDATA -> return net.blueva.luak.WeakTable.WeakUserdata(value)
                else -> return value
            }
        }

        /**
         * Unwrap a LuaValue from a WeakReference and/or WeakUserdata.
         * @param ref reference to convert
         * @return LuaValue or null
         * @see .weaken
         */
        protected fun strengthen(ref: Any?): LuaValue? {
            var ref: Any? = ref
            if (ref is WeakReference<*>) {
                ref = ref.get()
            }
            if (ref is WeakValue) {
                return ref.strongvalue()
            }
            return ref as LuaValue?
        }
    }
}
