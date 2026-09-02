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

import net.blueva.luak.WeakReference

/**
 * Subclass of [LuaValue] for representing lua tables.
 * 
 * 
 * Almost all API's implemented in [LuaTable] are defined and documented in [LuaValue].
 * 
 * 
 * If a table is needed, the one of the type-checking functions can be used such as
 * [.istable],
 * [.checktable], or
 * [.opttable]
 * 
 * 
 * The main table operations are defined on [LuaValue]
 * for getting and setting values with and without metatag processing:
 * 
 *  * [.get]
 *  * [.set]
 *  * [.rawget]
 *  * [.rawset]
 *  * plus overloads such as [.get], [.get], and so on
 * 
 * 
 * 
 * To iterate over key-value pairs from Java, use
 * <pre> `LuaValue k = LuaValue.NIL; while ( true ) {    Varargs n = table.next(k);    if ( (k = n.arg1()).isnil() )       break;    LuaValue v = n.arg(2)    process( k, v ) }`</pre>
 * 
 * 
 * 
 * As with other types, [LuaTable] instances should be constructed via one of the table constructor
 * methods on [LuaValue]:
 * 
 *  * [LuaValue.tableOf] empty table
 *  * [LuaValue.tableOf] table with capacity
 *  * [LuaValue.listOf] initialize array part
 *  * [LuaValue.listOf] initialize array part
 *  * [LuaValue.tableOf] initialize named hash part
 *  * [LuaValue.tableOf] initialize named hash part
 *  * [LuaValue.tableOf] initialize array and named parts
 *  * [LuaValue.tableOf] initialize array and named parts
 * 
 * @see LuaValue
 */
open class LuaTable : LuaValue, Metatable {
    /** See [LuaValue.gckeeper]; a table is one of the two kinds that can have one. */
    internal override var gckeeper: Any? = null

    /** See [LuaValue.pinned]; a value of this kind can be a weak key. */
    internal override var pinned: Any? = null

    /** the array values  */
    protected lateinit var array: Array<LuaValue?>

    /** the hash part  */
    protected lateinit var hash: Array<Slot?>

    /** the number of hash entries  */
    var hashEntries: Int = 0

    /** metatable for this table, or null  */
    protected var m_metatable: Metatable? = null

    /** Construct empty table  */
    constructor() {
        array = NOVALS
        hash = net.blueva.luak.LuaTable.Companion.NOBUCKETS
        Memory.current.account(Memory.TABLE)
    }

    /**
     * Construct table with preset capacity.
     * @param narray capacity of array part
     * @param nhash capacity of hash part
     */
    constructor(narray: Int, nhash: Int) {
        presize(narray, nhash)
    }

    /**
     * Construct table with named and unnamed parts.
     * @param named Named elements in order `key-a, value-a, key-b, value-b, ... `
     * @param unnamed Unnamed elements in order `value-1, value-2, ... `
     * @param lastarg Additional unnamed values beyond `unnamed.length`
     */
    constructor(named: Array<out LuaValue?>?, unnamed: Array<out LuaValue?>?, lastarg: Varargs?) {
        val nn = (if (named != null) named.size else 0)
        val nu = (if (unnamed != null) unnamed.size else 0)
        val nl = (if (lastarg != null) lastarg.narg() else 0)
        presize(nu + nl, nn shr 1)
        for (i in 0..<nu) rawset(i + 1, unnamed!![i])
        if (lastarg != null) {
            var i = 1
            var n: Int = lastarg.narg()
            while (i <= n) {
                rawset(nu + i, lastarg.arg(i))
                ++i
            }
        }
        var i = 0
        while (i < nn) {
            if (!named!![i + 1]!!.isnil()) rawset(named[i], named[i + 1])
            i += 2
        }
    }

    /**
     * Construct table of unnamed elements.
     * @param varargs Unnamed elements in order `value-1, value-2, ... `
     */
    constructor(varargs: Varargs) : this(varargs, 1)

    /**
     * Construct table of unnamed elements.
     * @param varargs Unnamed elements in order `value-1, value-2, ... `
     * @param firstarg the index in varargs of the first argument to include in the table
     */
    constructor(varargs: Varargs, firstarg: Int) {
        val nskip = firstarg - 1
        val n: Int = maxOf(varargs.narg() - nskip, 0)
        presize(n, 1)
        set(net.blueva.luak.LuaTable.Companion.N, valueOf(n))
        for (i in 1..n) set(i, varargs.arg(i + nskip))
    }

    override fun type(): Int {
        return LuaValue.TTABLE
    }

    override fun typename(): String? {
        return "table"
    }

    override fun istable(): Boolean {
        return true
    }

    override fun checktable(): LuaTable {
        return this
    }

    override fun opttable(defval: LuaTable?): LuaTable {
        return this
    }

    override fun presize(narray: Int) {
        if (narray > MAX_PART) LuaValue.error("table overflow")
        if (narray > array.size) {
            val was: Int = array.size
            array =
                net.blueva.luak.LuaTable.Companion.resize(array, 1 shl net.blueva.luak.LuaTable.Companion.log2(narray))
            Memory.current.account(Memory.SLOT * (array.size - was))
        }
    }

    fun presize(narray: Int, nhash: Int) {
        var nhash = nhash
        // Rounded up to a power of two below, which is where a size close to
        // the largest a host array can be would wrap around into a negative
        // one. Lua refuses the same way.
        if (narray > MAX_PART || nhash > MAX_PART) LuaValue.error("table overflow")
        // Counted here rather than in each constructor, since every one of
        // them that asks for room of its own comes through here.
        if (nhash > 0 && nhash < net.blueva.luak.LuaTable.Companion.MIN_HASH_CAPACITY) nhash =
            net.blueva.luak.LuaTable.Companion.MIN_HASH_CAPACITY
        // Size of both parts must be a power of two.
        array =
            (if (narray > 0) arrayOfNulls<LuaValue>(1 shl net.blueva.luak.LuaTable.Companion.log2(narray)) else NOVALS)
        hash =
            (if (nhash > 0) arrayOfNulls<Slot>(1 shl net.blueva.luak.LuaTable.Companion.log2(nhash)) else net.blueva.luak.LuaTable.Companion.NOBUCKETS)
        hashEntries = 0
        Memory.current.account(Memory.TABLE + Memory.SLOT * array.size + Memory.NODE * hash.size)
    }

    protected val arrayLengthValue: Int
        /**
         * Get the length of the array part of the table.
         * @return length of the array part, does not relate to count of objects in the table.
         */
        get() = array.size

    protected val hashLengthValue: Int
        /**
         * Get the length of the hash part of the table.
         * @return length of the hash part, does not relate to count of objects in the table.
         */
        get() = hash.size

    override fun getmetatable(): LuaValue? {
        return if (m_metatable != null) m_metatable!!.toLuaValue() else null
    }

    override fun setmetatable(metatable: LuaValue?): LuaValue? {
        val hadWeakKeys = m_metatable != null && m_metatable!!.useWeakKeys()
        val hadWeakValues = m_metatable != null && m_metatable!!.useWeakValues()
        m_metatable = LuaValue.metatableOf(metatable)
        if ((hadWeakKeys != (m_metatable != null && m_metatable!!.useWeakKeys())) ||
            (hadWeakValues != (m_metatable != null && m_metatable!!.useWeakValues()))
        ) {
            // How weakly an entry is held is decided when it is stored, so the
            // entries already there have to go in again to be held the new way.
            restore()
        }
        return this
    }

    /** Puts every entry back in, so each is held the way the metatable asks. */
    private fun restore() {
        val keys: ArrayList<LuaValue> = ArrayList()
        val values: ArrayList<LuaValue> = ArrayList()
        var entry: Varargs = next(NIL)
        while (!entry.arg1()!!.isnil()) {
            val key: LuaValue = entry.arg1()!!
            keys.add(key)
            values.add(entry.arg(2)!!)
            entry = next(key)
        }
        presize(keys.size, keys.size)
        for (index in keys.indices) rawset(keys[index], values[index])
    }

    override fun get(key: Int): LuaValue {
        val v: LuaValue = rawget(key)
        return if (v.isnil() && m_metatable != null) LuaValue.gettable(this, valueOf(key)) else v
    }

    override fun get(key: LuaValue): LuaValue {
        val v: LuaValue = rawget(key)
        return if (v.isnil() && m_metatable != null) LuaValue.gettable(this, key) else v
    }

    override fun rawget(key: Int): LuaValue {
        val key = key!!
        if (key > 0 && key <= array.size) {
            val v: LuaValue? = if (m_metatable == null) array[key - 1] else m_metatable!!.arrayget(array, key - 1)
            // An empty array slot is not proof the key is absent: growing the
            // array part leaves earlier entries where they were, so the hash
            // part still has to be asked.
            if (v != null) return v
        }
        return hashget((LuaInteger.valueOf(key))!!)
    }

    /**
     * The key a lookup should really use.
     *
     * A float whose value is integral names the same slot as that integer, so
     * `t[2.0]` and `t[2]` are one entry. With the two subtypes now distinct
     * this has to be done explicitly; before the split the value model folded
     * them together on its own.
     */
    private fun normalizeKey(key: LuaValue): LuaValue {
        if (key.type() != TNUMBER || key.isinttype()) return key
        val asDouble: Double = key.todouble()
        val asLong: Long = asDouble.toLong()
        return if (asLong.toDouble() == asDouble && !asDouble.isInfinite()) valueOf(asLong) else key
    }

    override fun rawget(key: LuaValue?): LuaValue {
        val key = normalizeKey(key!!)
        if (key.isinttype()) {
            val ikey: Int = key.toint()
            if (ikey > 0 && ikey <= array.size) {
                val v: LuaValue? = if (m_metatable == null)
                    array[ikey - 1]
                else
                    m_metatable!!.arrayget(array, ikey - 1)
                return if (v != null) v else NIL
            }
        }
        return hashget(key)
    }

    protected fun hashget(key: LuaValue): LuaValue {
        if (hashEntries > 0) {
            var slot = hash[hashSlot(key)]
            while (slot != null) {
                val foundSlot: StrongSlot?
                if ((slot.find(key).also { foundSlot = it }) != null) {
                    return foundSlot!!.value()
                }
                slot = slot.rest()
            }
        }
        return NIL
    }

    override fun set(key: Int, value: LuaValue?) {
        if (m_metatable == null || !rawget(key).isnil() || !LuaValue.settable(this, LuaInteger.valueOf(key), value)) rawset(
            key,
            value
        )
    }

    fun getArrayLength(): Int = arrayLengthValue

    fun getHashLength(): Int = hashLengthValue

    /** caller must ensure key is not nil  */
    override fun set(key: LuaValue?, value: LuaValue?) {
        if (key == null || !key.isvalidkey() && !metatag(NEWINDEX).isfunction()) throw LuaError("value ('" + key + "') can not be used as a table index")
        if (m_metatable == null || !rawget(key).isnil() || !LuaValue.settable(this, key, value)) rawset(key, value)
    }

    override fun rawset(key: Int, value: LuaValue?) {
        val value = value!!
        if (!arrayset(key, value)) hashset((LuaInteger.valueOf(key))!!, value)
    }

    /** caller must ensure key is not nil  */
    override fun rawset(key: LuaValue?, value: LuaValue?) {
        val key = normalizeKey(key!!)
        val value = value!!
        if (!key.isinttype() || !arrayset(key.toint(), value)) hashset(key, value)
    }

    /**
     * Sets an array element.
     *
     * @return false when the key is not one the array part holds, so the
     *   caller has to go to the hash part instead. Erasing a slot that is
     *   already empty counts as that: the key may be sitting in the hash with
     *   an index the array part happens to cover, and that is where it has to
     *   be removed from.
     */
    private fun arrayset(key: Int, value: LuaValue): Boolean {
        if (key > 0 && key <= array.size) {
            if (value.isnil() && array[key - 1] == null) return false
            array[key - 1] = if (value.isnil()) null else (if (m_metatable != null) m_metatable!!.wrap(value) else value)
            return true
        }
        return false
    }

    /** Remove the element at a position in a list-table
     * 
     * @param pos the position to remove
     * @return The removed item, or [.NONE] if not removed
     */
    fun remove(pos: Int): LuaValue? {
        var pos = pos
        val n = length()
        if (pos == 0) pos = n
        // Removing at #t+1 is allowed and answers what was there, which is
        // nil - a value, not an absence, so the caller still gets one result.
        else if (pos > n) return get(pos)
        val v: LuaValue = get(pos)
        var r: LuaValue = v
        while (!r.isnil()) {
            r = get(pos + 1)
            set(pos++, r)
        }
        return v
    }

    /** Insert an element at a position in a list-table
     * 
     * @param pos the position to remove
     * @param value The value to insert
     */
    fun insert(pos: Int, value: LuaValue) {
        var pos = pos
        var value: LuaValue = value
        if (pos == 0) pos = length() + 1
        while (!value.isnil()) {
            val v: LuaValue = get(pos)
            set(pos++, value)
            value = v
        }
    }

    /** Concatenate the contents of a table efficiently, using [Buffer]
     * 
     * @param sep [LuaString] separater to apply between elements
     * @param i the first element index
     * @param j the last element index, inclusive
     * @return [LuaString] value of the concatenation
     */
    fun concat(sep: LuaString?, i: Int, j: Int): LuaValue {
        var i = i
        val sb: Buffer = Buffer()
        if (i <= j) {
            sb.append((get(i).checkstring())!!)
            while (++i <= j) {
                sb.append((sep)!!)
                sb.append((get(i).checkstring())!!)
            }
        }
        return sb.tostring()
    }

    override fun length(): Int {
        if (m_metatable != null) {
            val len: LuaValue = len()
            if (!len.isint()) throw LuaError("table length is not an integer: " + len)
            return len.toint()
        }
        return rawlen()
    }

    override fun len(): LuaValue {
        val h: LuaValue = metatag(LEN)
        // Lua hands a unary operator its operand twice.
        if (h.toboolean()) return h.call(this, this)!!
        return (LuaInteger.valueOf(rawlen()))!!
    }

    override fun rawlen(): Int {
        val a = this.arrayLengthValue
        var n = a + 1
        var m = 0
        while (!rawget(n).isnil()) {
            m = n
            n += a + this.hashLengthValue + 1
        }
        while (n > m + 1) {
            val k = (n + m) / 2
            if (!rawget(k).isnil()) m = k
            else n = k
        }
        return m
    }

    /**
     * Get the next element after a particular key in the table
     * @return key,value or nil
     */
    override fun next(key: LuaValue?): Varargs {
        val key = key!!
        var i = 0
        do {
            // find current key index
            if (!key.isnil()) {
                if (key.isinttype()) {
                    i = key.toint()
                    if (i > 0 && i <= array.size) {
                        break
                    }
                }
                if (hash.size == 0) error("invalid key to 'next' 1: " + key)
                i = hashSlot(key)
                var found = false
                var slot = hash[i]
                while (slot != null) {
                    if (found) {
                        val nextEntry = slot.first()
                        if (nextEntry != null) {
                            return nextEntry.toVarargs()
                        }
                    } else if (slot.keyeq(key)) {
                        found = true
                    }
                    slot = slot.rest()
                }
                if (!found) {
                    error("invalid key to 'next' 2: " + key)
                }
                i += 1 + array.size
            }
        } while (false)

        // check array part
        while (i < array.size) {
            if (array[i] != null) {
                val value: LuaValue? = if (m_metatable == null) array[i] else m_metatable!!.arrayget(array, i)
                if (value != null) {
                    return (varargsOf(LuaInteger.valueOf(i + 1), value))!!
                }
            }
            ++i
        }

        // check hash part
        i -= array.size
        while (i < hash.size) {
            var slot = hash[i]
            while (slot != null) {
                val first = slot.first()
                if (first != null) return first.toVarargs()
                slot = slot.rest()
            }
            ++i
        }


        // nothing found, push nil, return nil.
        return NIL
    }

    /**
     * Get the next element after a particular key in the
     * contiguous array part of a table
     * @return key,value or none
     */
    override fun inext(key: LuaValue?): Varargs {
        val key = key!!
        val k: Int = key.checkint() + 1
        val v: LuaValue = rawget(k)
        return (if (v.isnil()) NONE else varargsOf(LuaInteger.valueOf(k), v))!!
    }

    /**
     * Set a hashtable value
     * @param key key to set
     * @param value value to set
     */
    fun hashset(key: LuaValue, value: LuaValue) {
        if (value.isnil()) hashRemove(key)
        else {
            var index = 0
            if (hash.size > 0) {
                index = hashSlot(key)
                var slot = hash[index]
                while (slot != null) {
                    val foundSlot: StrongSlot?
                    if ((slot.find(key).also { foundSlot = it }) != null) {
                        hash[index] = hash[index]!!.set(foundSlot, value)
                        return
                    }
                    slot = slot.rest()
                }
            }
            if (checkLoadFactor()) {
                if ((m_metatable == null || !m_metatable!!.useWeakValues())
                    && key.isinttype() && key.toint() > 0
                ) {
                    // a rehash might make room in the array portion for this key.
                    rehash(key.toint())
                    if (arrayset(key.toint(), value)) return
                } else {
                    rehash(-1)
                    if (key.isinttype() && key.toint() > 0 && arrayset(key.toint(), value)) return
                }
                index = hashSlot(key)
            }
            val entry: Slot? = if (m_metatable != null)
                m_metatable!!.entry(key, value)
            else
                net.blueva.luak.LuaTable.Companion.defaultEntry(key, value)
            hash[index] = if (hash[index] != null) hash[index]!!.add(entry) else entry
            ++hashEntries
        }
    }

    /**
     * Find the hashtable slot to use
     * @param key key to look for
     * @return slot to use
     */
    private fun hashSlot(key: LuaValue): Int {
        return net.blueva.luak.LuaTable.Companion.hashSlot(key, hash.size - 1)
    }

    private fun hashRemove(key: LuaValue) {
        if (hash.size > 0) {
            val index = hashSlot(key)
            var slot = hash[index]
            while (slot != null) {
                val foundSlot: StrongSlot?
                if ((slot.find(key).also { foundSlot = it }) != null) {
                    hash[index] = hash[index]!!.remove(foundSlot)
                    --hashEntries
                    return
                }
                slot = slot.rest()
            }
        }
    }

    private fun checkLoadFactor(): Boolean {
        return hashEntries >= hash.size
    }

    private fun countHashKeys(): Int {
        var keys = 0
        for (i in hash.indices) {
            var slot = hash[i]
            while (slot != null) {
                if (slot.first() != null) keys++
                slot = slot.rest()
            }
        }
        return keys
    }

    private fun dropWeakArrayValues() {
        for (i in array.indices) {
            m_metatable!!.arrayget(array, i)
        }
    }

    private fun countIntKeys(nums: IntArray): Int {
        var total = 0
        var i = 1

        // Count integer keys in array part
        for (bit in 0..30) {
            if (i > array.size) break
            val j: Int = minOf(array.size, 1 shl bit)
            var c = 0
            while (i <= j) {
                if (array[i++ - 1] != null) c++
            }
            nums[bit] = c
            total += c
        }

        // Count integer keys in hash part
        i = 0
        while (i < hash.size) {
            var s = hash[i]
            while (s != null) {
                val k: Int
                if ((s.arraykey(Int.MAX_VALUE).also { k = it }) > 0) {
                    nums[net.blueva.luak.LuaTable.Companion.log2(k)]++
                    total++
                }
                s = s.rest()
            }
            ++i
        }

        return total
    }

    /*
	 * newKey > 0 is next key to insert
	 * newKey == 0 means number of keys not changing (__mode changed)
	 * newKey < 0 next key will go in hash part
	 */
    private fun rehash(newKey: Int) {
        if (m_metatable != null && (m_metatable!!.useWeakKeys() || m_metatable!!.useWeakValues())) {
            // If this table has weak entries, hashEntries is just an upper bound.
            hashEntries = countHashKeys()
            if (m_metatable!!.useWeakValues()) {
                dropWeakArrayValues()
            }
        }
        val nums = IntArray(32)
        var total = countIntKeys(nums)
        if (newKey > 0) {
            total++
            nums[net.blueva.luak.LuaTable.Companion.log2(newKey)]++
        }

        // Choose N such that N <= sum(nums[0..log(N)]) < 2N
        var keys = nums[0]
        var newArraySize = 0
        for (log in 1..31) {
            keys += nums[log]
            if (total * 2 < 1 shl log) {
                // Not enough integer keys.
                break
            } else if (keys >= (1 shl (log - 1))) {
                newArraySize = 1 shl log
            }
        }

        val oldArray: Array<LuaValue?> = array
        val oldHash = hash
        val newArray: Array<LuaValue?>
        val newHash: Array<Slot?>

        // Copy existing array entries and compute number of moving entries.
        var movingToArray = 0
        if (newKey > 0 && newKey <= newArraySize) {
            movingToArray--
        }
        if (newArraySize != oldArray.size) {
            newArray = arrayOfNulls<LuaValue>(newArraySize)
            if (newArraySize > oldArray.size) {
                var i: Int = net.blueva.luak.LuaTable.Companion.log2(oldArray.size + 1)
                val j: Int = net.blueva.luak.LuaTable.Companion.log2(newArraySize) + 1
                while (i < j) {
                    movingToArray += nums[i]
                    ++i
                }
            } else if (oldArray.size > newArraySize) {
                var i: Int = net.blueva.luak.LuaTable.Companion.log2(newArraySize + 1)
                val j: Int = net.blueva.luak.LuaTable.Companion.log2(oldArray.size) + 1
                while (i < j) {
                    movingToArray -= nums[i]
                    ++i
                }
            }
            arrayCopy(oldArray, 0, newArray, 0, minOf(oldArray.size, newArraySize))
        } else {
            newArray = array
        }

        val newHashSize = (hashEntries - movingToArray
                + (if (newKey < 0 || newKey > newArraySize) 1 else 0)) // Make room for the new entry
        val oldCapacity = oldHash.size
        val newCapacity: Int
        val newHashMask: Int

        if (newHashSize > 0) {
            // round up to next power of 2.
            newCapacity = if (newHashSize < net.blueva.luak.LuaTable.Companion.MIN_HASH_CAPACITY)
                net.blueva.luak.LuaTable.Companion.MIN_HASH_CAPACITY
            else
                1 shl net.blueva.luak.LuaTable.Companion.log2(newHashSize)
            newHashMask = newCapacity - 1
            newHash = arrayOfNulls<Slot>(newCapacity)
        } else {
            newCapacity = 0
            newHashMask = 0
            newHash = net.blueva.luak.LuaTable.Companion.NOBUCKETS
        }

        // Move hash buckets
        for (i in 0..<oldCapacity) {
            var slot = oldHash[i]
            while (slot != null) {
                val k: Int
                if ((slot.arraykey(newArraySize).also { k = it }) > 0) {
                    val entry = slot.first()
                    if (entry != null) newArray[k - 1] = entry.value()
                } else if (slot !is DeadSlot) {
                    val j = slot.keyindex(newHashMask)
                    newHash[j] = slot.relink(newHash[j])
                }
                slot = slot.rest()
            }
        }

        // Move array values into hash portion
        var i = newArraySize
        while (i < oldArray.size) {
            val v: LuaValue?
            if ((oldArray[i++].also { v = it }) != null) {
                val slot: Int = net.blueva.luak.LuaTable.Companion.hashmod(LuaInteger.hashCode(i), newHashMask)
                val newEntry: Slot?
                if (m_metatable != null) {
                    newEntry = m_metatable!!.entry(valueOf(i), v)
                    if (newEntry == null) {
                        continue
                    }
                } else {
                    newEntry = net.blueva.luak.LuaTable.Companion.defaultEntry(valueOf(i), (v)!!)
                }
                newHash[slot] = if (newHash[slot] != null) newHash[slot]!!.add(newEntry) else newEntry
            }
        }

        val wasarray: Int = array.size
        val washash: Int = hash.size
        hash = newHash
        array = newArray
        hashEntries -= movingToArray
        // Only what the table grew by: what it gave up is for the host to
        // reclaim, and the tally does not go down until a collection ends.
        if (array.size > wasarray) Memory.current.account(Memory.SLOT * (array.size - wasarray))
        if (hash.size > washash) Memory.current.account(Memory.NODE * (hash.size - washash))
    }

    override fun entry(key: LuaValue?, value: LuaValue?): Slot? {
        return net.blueva.luak.LuaTable.Companion.defaultEntry(key!!, value!!)
    }

    // ----------------- sort support -----------------------------
    //
    // implemented heap sort from wikipedia
    //
    // Only sorts the contiguous array part.
    //
    /** Sort the table using a comparator.
     * @param comparator [LuaValue] to be called to compare elements.
     */
    @kotlin.jvm.JvmOverloads
    fun sort(comparator: LuaValue, debuglib: net.blueva.luak.lib.DebugLib? = null) {
        if (len().tolong() >= Int.MAX_VALUE.toLong()) throw LuaError("array too big: " + len().tolong())
        if (m_metatable != null && m_metatable!!.useWeakValues()) {
            dropWeakArrayValues()
        }
        val n = length()
        if (n > 1) auxsort(1, n, if (comparator.isnil()) null else comparator, debuglib)
    }

    /**
     * The quicksort Lua sorts with, over `1..n` of this table.
     *
     * Written as Lua writes it, down to the median of three it takes its
     * pivot from and the two ends it walks towards each other, because that
     * is what lets it notice an order function that contradicts itself: a
     * walk that runs past the pivot can only mean the answers it was given
     * cannot all be true, and Lua says so rather than reading past the part
     * of the table it was given.
     *
     * The larger half is looped on rather than recursed into, so what is on
     * the host stack stays within the logarithm of the size.
     */
    private fun auxsort(from: Int, to: Int, cmpfunc: LuaValue?, debuglib: net.blueva.luak.lib.DebugLib?) {
        var lo = from
        var up = to
        while (lo < up) {
            /* sort elements 'lo', 'p', and 'up' */
            if (compare(up, lo, cmpfunc, debuglib)) swap(lo, up)
            if (up - lo == 1) return /* only 2 elements */
            var p: Int = lo + (up - lo) / 2 /* middle point */
            if (compare(p, lo, cmpfunc, debuglib)) swap(p, lo)
            else if (compare(up, p, cmpfunc, debuglib)) swap(p, up)
            if (up - lo == 2) return /* only 3 elements */
            swap(p, up - 1) /* the pivot goes next to the end */
            p = partition(lo, up, cmpfunc, debuglib)
            /* a[lo .. p - 1] <= a[p] <= a[p + 1 .. up] */
            if (p - lo < up - p) {
                auxsort(lo, p - 1, cmpfunc, debuglib)
                lo = p + 1
            } else {
                auxsort(p + 1, up, cmpfunc, debuglib)
                up = p - 1
            }
        }
    }

    /**
     * Puts everything below the pivot before it and everything above after.
     *
     * The pivot is at `up - 1` when this starts, and at the index answered
     * when it ends.
     */
    private fun partition(
        lo: Int,
        up: Int,
        cmpfunc: LuaValue?,
        debuglib: net.blueva.luak.lib.DebugLib?,
    ): Int {
        val pivot: Int = up - 1
        var i: Int = lo
        var j: Int = up - 1
        while (true) {
            /* repeat ++i while a[i] < P */
            while (compare(++i, pivot, cmpfunc, debuglib)) {
                if (i == up - 1) LuaValue.error("invalid order function for sorting")
            }
            /* repeat --j while P < a[j] */
            while (compare(pivot, --j, cmpfunc, debuglib)) {
                if (j < i) LuaValue.error("invalid order function for sorting")
            }
            if (j < i) {
                swap(up - 1, i) /* the pivot takes its place */
                return i
            }
            swap(i, j)
        }
    }

    private fun swap(i: Int, j: Int) {
        val held: LuaValue = get(i)
        set(i, get(j))
        set(j, held)
    }

    private fun compare(
        i: Int,
        j: Int,
        cmpfunc: LuaValue?,
        debuglib: net.blueva.luak.lib.DebugLib? = null,
    ): Boolean {
        val a: LuaValue? = get(i)
        val b: LuaValue? = get(j)
        if (a == null || b == null) return false
        if (cmpfunc != null) {
            // Through the library's own way of calling back, so that an order
            // function of the library's own can be named in an error.
            return net.blueva.luak.lib.callback(debuglib, cmpfunc, varargsOf(a, b)!!).arg1()!!.toboolean()
        } else {
            return a.lt_b(b)
        }
    }

    /** This may be deprecated in a future release.
     * It is recommended to count via iteration over next() instead
     * @return count of keys in the table
     */
    fun keyCount(): Int {
        var k: LuaValue = LuaValue.NIL
        var i = 0
        while (true) {
            val n: Varargs = next(k)
            if ((n.arg1().also { k = it })!!.isnil()) return i
            i++
        }
    }

    /** This may be deprecated in a future release.
     * It is recommended to use next() instead
     * @return array of keys in the table
     */
    fun keys(): Array<LuaValue?> {
        val l: MutableList<LuaValue> = mutableListOf()
        var k: LuaValue = LuaValue.NIL
        while (true) {
            val n: Varargs = next(k)
            if ((n.arg1().also { k = it })!!.isnil()) break
            l.add(k)
        }
        val a: Array<LuaValue?> = arrayOfNulls<LuaValue>(l.size)
        l.forEachIndexed { index, value -> a[index] = value }
        return a
    }

    // equality w/ metatable processing
    override fun eq(`val`: LuaValue?): LuaValue {
        val `val` = `val`!!
        return (if (eq_b(`val`)) TRUE else FALSE)!!
    }

    override fun eq_b(`val`: LuaValue?): Boolean {
        val `val` = `val`!!
        if (this === `val`) return true
        if (!`val`.istable()) return false
        return LuaValue.eqmtcall(this, `val`)
    }

    /** Unpack all the elements of this table  */
    fun unpack(): Varargs {
        return unpack(1, this.rawlen())
    }

    /** Unpack all the elements of this table from element i  */
    fun unpack(i: Int): Varargs {
        return unpack(i, this.rawlen())
    }

    /** Unpack the elements from i to j inclusive  */
    fun unpack(i: Int, j: Int): Varargs {
        if (j < i) return (NONE)!!
        val count = j - i
        if (count < 0) throw LuaError("too many results to unpack: greater " + Int.MAX_VALUE) // integer overflow

        val max = 0x00ffffff
        if (count >= max) throw LuaError("too many results to unpack: " + count + " (max is " + max + ')')
        var n = j + 1 - i
        when (n) {
            0 -> return (NONE)!!
            1 -> return get(i)
            2 -> return (varargsOf(get(i), get(i + 1)))!!
            else -> {
                if (n < 0) return (NONE)!!
                try {
                    val v: Array<LuaValue?> = arrayOfNulls<LuaValue>(n)
                    while (--n >= 0) v[n] = get(i + n)
                    return (varargsOf(v))!!
                } catch (e: Throwable) {
                    throw LuaError("too many results to unpack [out of memory]: " + n)
                }
            }
        }
    }

    /**
     * Represents a slot in the hash table.
     */
    interface Slot {
        /** Return hash{pow2,mod}( first().key().hashCode(), sizeMask )  */
        fun keyindex(hashMask: Int): Int

        /** Return first Entry, if still present, or null.  */
        fun first(): StrongSlot?

        /** Compare given key with first()'s key; return first() if equal.  */
        fun find(key: LuaValue?): StrongSlot?

        /**
         * Compare given key with first()'s key; return true if equal. May
         * return true for keys no longer present in the table.
         */
        fun keyeq(key: LuaValue?): Boolean

        /** Return rest of elements  */
        fun rest(): Slot?

        /**
         * Return first entry's key, iff it is an integer between 1 and max,
         * inclusive, or zero otherwise.
         */
        fun arraykey(max: Int): Int

        /**
         * Set the value of this Slot's first Entry, if possible, or return a
         * new Slot whose first entry has the given value.
         */
        fun set(target: StrongSlot?, value: LuaValue?): Slot?

        /**
         * Link the given new entry to this slot.
         */
        fun add(newEntry: Slot?): Slot?

        /**
         * Return a Slot with the given value set to nil; must not return null
         * for next() to behave correctly.
         */
        fun remove(target: StrongSlot?): Slot?

        /**
         * Return a Slot with the same first key and value (if still present)
         * and rest() equal to rest.
         */
        fun relink(rest: Slot?): Slot?
    }

    /**
     * Subclass of Slot guaranteed to have a strongly-referenced key and value,
     * to support weak tables.
     */
    interface StrongSlot : Slot {
        /** Return first entry's key  */
        fun key(): LuaValue?

        /** Return first entry's value  */
        fun value(): LuaValue

        /** Return varargsOf(key(), value()) or equivalent  */
        fun toVarargs(): Varargs
    }

    private class LinkSlot(private var entry: Entry, next: Slot) : StrongSlot {
        private var next: Slot?

        init {
            this.next = next
        }

        override fun key(): LuaValue? {
            return entry.key()
        }

        override fun keyindex(hashMask: Int): Int {
            return entry.keyindex(hashMask)
        }

        override fun value(): LuaValue {
            return entry.value()
        }

        override fun toVarargs(): Varargs {
            return entry.toVarargs()
        }

        override fun first(): StrongSlot {
            return entry
        }

        override fun find(key: LuaValue?): StrongSlot? {
            return if (entry.keyeq(key)) this else null
        }

        override fun keyeq(key: LuaValue?): Boolean {
            return entry.keyeq(key)
        }

        override fun rest(): Slot {
            return next!!
        }

        override fun arraykey(max: Int): Int {
            return entry.arraykey(max)
        }

        override fun set(target: StrongSlot?, value: LuaValue?): Slot? {
            if (target === this) {
                entry = entry.set(value)
                return this
            } else {
                return setnext(next!!.set(target, value))
            }
        }

        override fun add(entry: Slot?): Slot? {
            return setnext(next!!.add(entry))
        }

        override fun remove(target: StrongSlot?): Slot {
            if (this === target) {
                return net.blueva.luak.LuaTable.DeadSlot((key())!!, next)
            } else {
                this.next = next!!.remove(target)
            }
            return this
        }

        override fun relink(rest: Slot?): Slot? {
            // This method is (only) called during rehash, so it must not change this.next.
            return if (rest != null) net.blueva.luak.LuaTable.LinkSlot(entry, rest) else entry as Slot?
        }

        // this method ensures that this.next is never set to null.
        fun setnext(next: Slot?): Slot? {
            if (next != null) {
                this.next = next
                return this
            } else {
                return entry
            }
        }

        override fun toString(): String {
            return entry.toString() + "; " + next
        }
    }

    /**
     * Base class for regular entries.
     * 
     * 
     * 
     * If the key may be an integer, the [.arraykey] method must be
     * overridden to handle that case.
     */
    abstract class Entry : Varargs(), StrongSlot {
        abstract override fun key(): LuaValue?
        abstract override fun value(): LuaValue
        abstract fun set(value: LuaValue?): Entry
        abstract override fun keyeq(key: LuaValue?): Boolean
        abstract override fun keyindex(hashMask: Int): Int

        override fun arraykey(max: Int): Int {
            return 0
        }

        override fun arg(i: Int): LuaValue {
            when (i) {
                1 -> return key()!!
                2 -> return value()
            }
            return NIL!!
        }

        override fun narg(): Int {
            return 2
        }

        /**
         * Subclasses should redefine as "return this;" whenever possible.
         */
        override fun toVarargs(): Varargs {
            return (varargsOf(key(), value()))!!
        }

        override fun arg1(): LuaValue {
            return key()!!
        }

        override fun subargs(start: Int): Varargs? {
            when (start) {
                1 -> return this
                2 -> return value()
            }
            return NONE
        }

        override fun first(): StrongSlot {
            return this
        }

        override fun rest(): Slot? {
            return null
        }

        override fun find(key: LuaValue?): StrongSlot? {
            return if (keyeq(key)) this else null
        }

        override fun set(target: StrongSlot?, value: LuaValue?): Slot {
            return set(value)
        }

        override fun add(entry: Slot?): Slot? {
            return net.blueva.luak.LuaTable.LinkSlot(this, (entry)!!)
        }

        override fun remove(target: StrongSlot?): Slot {
            return net.blueva.luak.LuaTable.DeadSlot((key())!!, null)
        }

        override fun relink(rest: Slot?): Slot {
            return if (rest != null) net.blueva.luak.LuaTable.LinkSlot(this, rest) else this as Slot
        }
    }

    internal class NormalEntry(key: LuaValue, value: LuaValue?) : Entry() {
        private val key: LuaValue
        private var value: LuaValue?

        init {
            this.key = key
            this.value = value
        }

        override fun key(): LuaValue {
            return key
        }

        override fun value(): LuaValue {
            return (value)!!
        }

        public override fun set(value: LuaValue?): Entry {
            this.value = value
            return this
        }

        override fun toVarargs(): Varargs {
            return this
        }

        override fun keyindex(hashMask: Int): Int {
            return net.blueva.luak.LuaTable.Companion.hashSlot(key, hashMask)
        }

        override fun keyeq(key: LuaValue?): Boolean {
            return key!!.raweq(this.key)
        }
    }

    private class IntKeyEntry(private val key: Long, value: LuaValue?) : Entry() {
        private var value: LuaValue?

        init {
            this.value = value
        }

        override fun key(): LuaValue {
            return valueOf(key)
        }

        override fun arraykey(max: Int): Int {
            return if (key >= 1L && key <= max.toLong()) key.toInt() else 0
        }

        override fun value(): LuaValue {
            return (value)!!
        }

        public override fun set(value: LuaValue?): Entry {
            this.value = value
            return this
        }

        override fun keyindex(mask: Int): Int {
            return net.blueva.luak.LuaTable.Companion.hashmod(LuaInteger.hashCode(key), mask)
        }

        override fun keyeq(key: LuaValue?): Boolean {
            return key!!.raweq(this.key)
        }
    }

    /**
     * Entry class used with float values, but only when the key is not an integer.
     *
     * Unpacking the number into a raw `double` field saves an object, but it can
     * only hold a float: an integer stored this way comes back rounded once it
     * exceeds 2^53, and loses its subtype in every case. Integers therefore go
     * to [NormalEntry] instead.
     */
    private class NumberValueEntry(key: LuaValue, value: Double) : Entry() {
        private var value: Double
        private val key: LuaValue

        init {
            this.key = key
            this.value = value
        }

        override fun key(): LuaValue {
            return key
        }

        override fun value(): LuaValue {
            return valueOf(value)
        }

        public override fun set(value: LuaValue?): Entry {
            if (value!!.type() === TNUMBER && !value.isinttype()) {
                val n: LuaValue = value!!.tonumber()
                if (!n.isnil()) {
                    this.value = n.todouble()
                    return this
                }
            }
            return net.blueva.luak.LuaTable.NormalEntry(this.key, value)
        }

        override fun keyindex(mask: Int): Int {
            return net.blueva.luak.LuaTable.Companion.hashSlot(key, mask)
        }

        override fun keyeq(key: LuaValue?): Boolean {
            return key!!.raweq(this.key)
        }
    }

    /**
     * A Slot whose value has been set to nil. The key is kept in a weak reference so that
     * it can be found by next().
     */
    private class DeadSlot(key: LuaValue, private var next: Slot?) : Slot {
        private val key: Any

        init {
            this.key = if (net.blueva.luak.LuaTable.Companion.isLargeKey(key)) WeakReference(key) else key as Any
        }

        fun key(): LuaValue? {
            return (if (key is WeakReference<*>) key.get() else key) as LuaValue?
        }

        override fun keyindex(hashMask: Int): Int {
            // Not needed: this entry will be dropped during rehash.
            return 0
        }

        override fun first(): StrongSlot? {
            return null
        }

        override fun find(key: LuaValue?): StrongSlot? {
            return null
        }

        override fun keyeq(key: LuaValue?): Boolean {
            val k: LuaValue? = key()
            return k != null && key!!.raweq(k)
        }

        override fun rest(): Slot? {
            return next
        }

        override fun arraykey(max: Int): Int {
            return -1
        }

        override fun set(target: StrongSlot?, value: LuaValue?): Slot? {
            val next = if (this.next != null) this.next!!.set(target, value) else null
            if (key() != null) {
                // if key hasn't been garbage collected, it is still potentially a valid argument
                // to next(), so we can't drop this entry yet.
                this.next = next
                return this
            } else {
                return next
            }
        }

        override fun add(newEntry: Slot?): Slot? {
            return if (next != null) next!!.add(newEntry) else newEntry
        }

        override fun remove(target: StrongSlot?): Slot? {
            // The rest of the chain is searched either way: dropping this
            // placeholder must not drop the removal along with it.
            val rest: Slot? = next?.remove(target)
            if (key() != null) {
                // The key is still reachable, so it can still be handed to
                // next(), and this placeholder has to stay to answer it.
                next = rest
                return this
            }
            return rest
        }

        override fun relink(rest: Slot?): Slot? {
            return rest
        }

        override fun toString(): String {
            val buf: StringBuilder = StringBuilder()
            buf.append("<dead")
            val k: LuaValue? = key()
            if (k != null) {
                buf.append(": ")
                buf.append(k.toString())
            }
            buf.append('>')
            if (next != null) {
                buf.append("; ")
                buf.append(next.toString())
            }
            return buf.toString()
        }
    }

    // Metatable operations
    override fun useWeakKeys(): Boolean {
        return false
    }

    override fun useWeakValues(): Boolean {
        return false
    }

    override fun toLuaValue(): LuaValue? {
        return this
    }

    override fun wrap(value: LuaValue?): LuaValue? {
        return value
    }

    override fun arrayget(array: Array<LuaValue?>?, index: Int): LuaValue? {
        return array!![index]
    }

    companion object {
        private const val MIN_HASH_CAPACITY = 2

        /** The largest either part of a table can be asked for. */
        internal const val MAX_PART: Int = 1 shl 30
        private val N: LuaString? = valueOf("n")

        /** Resize the table  */
        private fun resize(old: Array<LuaValue?>, n: Int): Array<LuaValue?> {
            val v: Array<LuaValue?> = arrayOfNulls<LuaValue>(n)
            arrayCopy(old, 0, v, 0, old.size)
            return v
        }

        fun hashpow2(hashCode: Int, mask: Int): Int {
            return hashCode and mask
        }

        fun hashmod(hashCode: Int, mask: Int): Int {
            return (hashCode and 0x7FFFFFFF) % mask
        }

        /**
         * Find the hashtable slot index to use.
         * @param key the key to look for
         * @param hashMask N-1 where N is the number of hash slots (must be power of 2)
         * @return the slot index
         */
        fun hashSlot(key: LuaValue, hashMask: Int): Int {
            when (key.type()) {
                TNUMBER, TTABLE, TTHREAD, TLIGHTUSERDATA, TUSERDATA -> return net.blueva.luak.LuaTable.Companion.hashmod(
                    key.hashCode(),
                    hashMask
                )

                else -> return net.blueva.luak.LuaTable.Companion.hashpow2(key.hashCode(), hashMask)
            }
        }

        // Compute ceil(log2(x))
        fun log2(x: Int): Int {
            var x = x
            var lg = 0
            x -= 1
            if (x < 0)  // 2^(-(2^31)) is approximately 0
                return Int.MIN_VALUE
            if ((x and -0x10000) != 0) {
                lg = 16
                x = x ushr 16
            }
            if ((x and 0xFF00) != 0) {
                lg += 8
                x = x ushr 8
            }
            if ((x and 0xF0) != 0) {
                lg += 4
                x = x ushr 4
            }
            when (x) {
                0x0 -> return 0
                0x1 -> lg += 1
                0x2 -> lg += 2
                0x3 -> lg += 2
                0x4 -> lg += 3
                0x5 -> lg += 3
                0x6 -> lg += 3
                0x7 -> lg += 3
                0x8 -> lg += 4
                0x9 -> lg += 4
                0xA -> lg += 4
                0xB -> lg += 4
                0xC -> lg += 4
                0xD -> lg += 4
                0xE -> lg += 4
                0xF -> lg += 4
            }
            return lg
        }

        protected fun isLargeKey(key: LuaValue): Boolean {
            when (key.type()) {
                TSTRING -> return key.rawlen() > LuaString.RECENT_STRINGS_MAX_LENGTH
                TNUMBER, TBOOLEAN -> return false
                else -> return true
            }
        }

        internal fun defaultEntry(key: LuaValue, value: LuaValue): Entry {
            if (key.isinttype()) {
                return net.blueva.luak.LuaTable.IntKeyEntry(key.tolong(), value)
            } else if (value.type() === TNUMBER && !value.isinttype()) {
                return net.blueva.luak.LuaTable.NumberValueEntry(key, value.todouble())
            } else {
                return net.blueva.luak.LuaTable.NormalEntry(key, value)
            }
        }

        private val NOBUCKETS = arrayOf<Slot?>()
    }
}
