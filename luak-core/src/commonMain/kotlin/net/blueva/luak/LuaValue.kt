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

import kotlin.reflect.KClass
/**
 * Base class for all concrete lua type values.
 * 
 * 
 * Establishes base implementations for all the operations on lua types.
 * This allows Java clients to deal essentially with one type for all Java values, namely [LuaValue].
 * 
 * 
 * Constructors are provided as static methods for common Java types, such as
 * [valueOf] or [valueOf]
 * to allow for instance pooling.
 * 
 * 
 * Constants are defined for the lua values
 * [.NIL], [.TRUE], and [.FALSE].
 * A constant [.NONE] is defined which is a [Varargs] list having no values.
 * 
 * 
 * Operations are performed on values directly via their Java methods.
 * For example, the following code divides two numbers:
 * <pre> `LuaValue a = LuaValue.valueOf( 5 ); LuaValue b = LuaValue.valueOf( 4 ); LuaValue c = a.div(b); ` </pre>
 * Note that in this example, c will be a [LuaDouble], but would be a [LuaInteger]
 * if the value of a were changed to 8, say.
 * In general the value of c in practice will vary depending on both the types and values of a and b
 * as well as any metatable/metatag processing that occurs.
 * 
 * 
 * Field access and function calls are similar, with common overloads to simplify Java usage:
 * <pre> `LuaValue globals = JvmPlatform.standardGlobals(); LuaValue sqrt = globals.get("math").get("sqrt"); LuaValue print = globals.get("print"); LuaValue d = sqrt.call( a ); print.call( LuaValue.valueOf("sqrt(5):"), a ); ` </pre>
 * 
 * 
 * To supply variable arguments or get multiple return values, use
 * [.invoke] or [.invokemethod] methods:
 * <pre> `LuaValue modf = globals.get("math").get("modf"); Varargs r = modf.invoke( d ); print.call( r.arg(1), r.arg(2) ); ` </pre>
 * 
 * 
 * To load and run a script, [LoadState] is used:
 * <pre> `LoadState.load( new FileInputStream("main.lua"), "main.lua", globals ).call(); ` </pre>
 * 
 * 
 * although `require` could also be used:
 * <pre> `globals.get("require").call(LuaValue.valueOf("main")); ` </pre>
 * For this to work the file must be in the current directory, or in the class path,
 * dependening on the platform.
 * See [net.blueva.luak.lib.LuaPlatform] and [net.blueva.luak.lib.jvm.JvmPlatform] for details.
 * 
 * 
 * In general a [LuaError] may be thrown on any operation when the
 * types supplied to any operation are illegal from a lua perspective.
 * Examples could be attempting to concatenate a NIL value, or attempting arithmetic
 * on values that are not number.
 * 
 * 
 * There are several methods for preinitializing tables, such as:
 * 
 *  * [.listOf] for unnamed elements
 *  * [.tableOf] for named elements
 *  * [.tableOf] for mixtures
 * 
 * 
 * 
 * Predefined constants exist for the standard lua type constants
 * [.TNIL], [.TBOOLEAN], [.TLIGHTUSERDATA], [.TNUMBER], [.TSTRING],
 * [.TTABLE], [.TFUNCTION], [.TUSERDATA], [.TTHREAD],
 * and extended lua type constants
 * [.TINT], [.TNONE], [.TVALUE]
 * 
 * 
 * Predefined constants exist for all strings used as metatags:
 * [.INDEX], [.NEWINDEX], [.CALL], [.MODE], [.METATABLE],
 * [.ADD], [.SUB], [.DIV], [.MUL], [.POW],
 * [.MOD], [.UNM], [.LEN], [.EQ], [.LT],
 * [.LE], [.TOSTRING], and [.CONCAT].
 * 
 * @see net.blueva.luak.lib.jvm.JvmPlatform
 * 
 * @see net.blueva.luak.lib.LuaPlatform
 * 
 * @see LoadState
 * 
 * @see Varargs
 */
abstract
open class LuaValue : Varargs() {
    /**
     * What keeps this value watched for finalization, or null.
     *
     * Only a table or a userdata can have a `__gc` handler, so only those keep
     * one; everything else answers null and ignores what it is given. Holding
     * it here is the point: the keeper has to live exactly as long as the
     * value does. See [Globals.markforfinalization].
     */
    internal open var gckeeper: Any?
        get() = null
        set(value) {}

    /**
     * What this value keeps alive by being the key of a weak-key table.
     *
     * Such a table holds its value only for as long as the key lives, and it
     * is the key itself that holds it: an entry whose key can be reached only
     * through its own value is then a ring that nothing outside refers to, and
     * goes as a whole. That is what makes a weak-key table an ephemeron table
     * rather than one that keeps its keys alive through their values.
     *
     * Holds one value, or a list of them for a key used in several tables.
     * Only the kinds that can be a weak key keep one; see [WeakTable].
     */
    internal open var pinned: Any?
        get() = null
        set(value) {}

    // type
    /** Get the enumeration value for the type of this value.
     * @return value for this type, one of
     * [.TNIL],
     * [.TBOOLEAN],
     * [.TNUMBER],
     * [.TSTRING],
     * [.TTABLE],
     * [.TFUNCTION],
     * [.TUSERDATA],
     * [.TTHREAD]
     * @see .typename
     */
    abstract fun type(): Int

    /** Get the String name of the type of this value.
     * 
     * 
     * 
     * @return name from type name list [.TYPE_NAMES]
     * corresponding to the type of this value:
     * "nil", "boolean", "number", "string",
     * "table", "function", "userdata", "thread"
     * @see .type
     */
    abstract fun typename(): String?

    /** Check if `this` is a `boolean`
     * @return true if this is a `boolean`, otherwise false
     * @see .isboolean
     * @see .toboolean
     * @see .checkboolean
     * @see .optboolean
     * @see .TBOOLEAN
     */
    open fun isboolean(): Boolean {
        return false
    }

    /** Check if `this` is a `function` that is a closure,
     * meaning interprets lua bytecode for its execution
     * @return true if this is a `closure`, otherwise false
     * @see .isfunction
     * @see .checkclosure
     * @see .optclosure
     * @see .TFUNCTION
     */
    open fun isclosure(): Boolean {
        return false
    }

    /** Check if `this` is a `function`
     * @return true if this is a `function`, otherwise false
     * @see .isclosure
     * @see .checkfunction
     * @see .optfunction
     * @see .TFUNCTION
     */
    open fun isfunction(): Boolean {
        return false
    }

    /** Check if `this` is a `number` and is representable by java int
     * without rounding or truncation
     * @return true if this is a `number`
     * meaning derives from [LuaNumber]
     * or derives from [LuaString] and is convertible to a number,
     * and can be represented by int,
     * otherwise false
     * @see .isinttype
     * @see .islong
     * @see .tonumber
     * @see .checkint
     * @see .optint
     * @see .TNUMBER
     */
    open fun isint(): Boolean {
        return false
    }

    /** Check if `this` is a [LuaInteger]
     * 
     * 
     * No attempt to convert from string will be made by this call.
     * @return true if this is a `LuaInteger`,
     * otherwise false
     * @see .isint
     * @see .isnumber
     * @see .tonumber
     * @see .TNUMBER
     */
    open fun isinttype(): Boolean {
        return false
    }

    /** Check if `this` is a `number` and is representable by java long
     * without rounding or truncation
     * @return true if this is a `number`
     * meaning derives from [LuaNumber]
     * or derives from [LuaString] and is convertible to a number,
     * and can be represented by long,
     * otherwise false
     * @see .tonumber
     * @see .checklong
     * @see .optlong
     * @see .TNUMBER
     */
    open fun islong(): Boolean {
        return false
    }

    /** Check if `this` is `#NIL`
     * @return true if this is `#NIL`, otherwise false
     * @see .NIL
     * 
     * @see .NONE
     * 
     * @see .checknotnil
     * @see .optvalue
     * @see Varargs.isnoneornil
     * @see .TNIL
     * 
     * @see .TNONE
     */
    open fun isnil(): Boolean {
        return false
    }

    /** Check if `this` is a `number`
     * @return true if this is a `number`,
     * meaning derives from [LuaNumber]
     * or derives from [LuaString] and is convertible to a number,
     * otherwise false
     * @see .tonumber
     * @see .checknumber
     * @see .optnumber
     * @see .TNUMBER
     */
    open fun isnumber(): Boolean {
        return false
    } // may convert from string

    /** Check if `this` is a `string`
     * @return true if this is a `string`,
     * meaning derives from [LuaString] or [LuaNumber],
     * otherwise false
     * @see .tostring
     * @see .checkstring
     * @see .optstring
     * @see .TSTRING
     */
    open fun isstring(): Boolean {
        return false
    }

    /** Check if `this` is a `thread`
     * @return true if this is a `thread`, otherwise false
     * @see .checkthread
     * @see .optthread
     * @see .TTHREAD
     */
    open fun isthread(): Boolean {
        return false
    }

    /** Check if `this` is a `table`
     * @return true if this is a `table`, otherwise false
     * @see .checktable
     * @see .opttable
     * @see .TTABLE
     */
    open fun istable(): Boolean {
        return false
    }

    /** Check if `this` is a `userdata`
     * @return true if this is a `userdata`, otherwise false
     * @see .isuserdata
     * @see .touserdata
     * @see .checkuserdata
     * @see .optuserdata
     * @see .TUSERDATA
     */
    open fun isuserdata(): Boolean {
        return false
    }

    /** Check if `this` is a `userdata` of type `c`
     * @param c Class to test instance against
     * @return true if this is a `userdata`
     * and the instance is assignable to `c`,
     * otherwise false
     * @see .isuserdata
     * @see .touserdata
     * @see .checkuserdata
     * @see .optuserdata
     * @see .TUSERDATA
     */
    open fun isuserdata(c: KClass<*>?): Boolean {
        return false
    }

    /** Convert to boolean false if [.NIL] or [.FALSE], true if anything else
     * @return Value cast to byte if number or string convertible to number, otherwise 0
     * @see .optboolean
     * @see .checkboolean
     * @see .isboolean
     * @see .TBOOLEAN
     */
    open fun toboolean(): Boolean {
        return true
    }

    /** Convert to byte if numeric, or 0 if not.
     * @return Value cast to byte if number or string convertible to number, otherwise 0
     * @see .toint
     * @see .todouble
     * @see .checknumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun tobyte(): Byte {
        return 0
    }

    /** Convert to char if numeric, or 0 if not.
     * @return Value cast to char if number or string convertible to number, otherwise 0
     * @see .toint
     * @see .todouble
     * @see .checknumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun tochar(): Char {
        return 0.toChar()
    }

    /** Convert to double if numeric, or 0 if not.
     * @return Value cast to double if number or string convertible to number, otherwise 0
     * @see .toint
     * @see .tobyte
     * @see .tochar
     * @see .toshort
     * @see .tolong
     * @see .tofloat
     * @see .optdouble
     * @see .checknumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun todouble(): Double {
        return 0.0
    }

    /** Convert to float if numeric, or 0 if not.
     * @return Value cast to float if number or string convertible to number, otherwise 0
     * @see .toint
     * @see .todouble
     * @see .checknumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun tofloat(): Float {
        return 0f
    }

    /** Convert to int if numeric, or 0 if not.
     * @return Value cast to int if number or string convertible to number, otherwise 0
     * @see .tobyte
     * @see .tochar
     * @see .toshort
     * @see .tolong
     * @see .tofloat
     * @see .todouble
     * @see .optint
     * @see .checknumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun toint(): Int {
        return 0
    }

    /** Convert to long if numeric, or 0 if not.
     * @return Value cast to long if number or string convertible to number, otherwise 0
     * @see .isint
     * @see .isinttype
     * @see .toint
     * @see .todouble
     * @see .optlong
     * @see .checknumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun tolong(): Long {
        return 0
    }

    /** Convert to short if numeric, or 0 if not.
     * @return Value cast to short if number or string convertible to number, otherwise 0
     * @see .toint
     * @see .todouble
     * @see .checknumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun toshort(): Short {
        return 0
    }

    /** Convert to human readable String for any type.
     * @return String for use by human readers based on type.
     * @see .tostring
     * @see .optjstring
     * @see .checkjstring
     * @see .isstring
     * @see .TSTRING
     */
    override fun tojstring(): String {
        return typename().toString() + ": " + hashCode().toString(16)
    }

    /** Convert to userdata instance, or null.
     * @return userdata instance if userdata, or null if not [LuaUserdata]
     * @see .optuserdata
     * @see .checkuserdata
     * @see .isuserdata
     * @see .TUSERDATA
     */
    open fun touserdata(): Any? {
        return null
    }

    /** Convert to userdata instance if specific type, or null.
     * @return userdata instance if is a userdata whose instance derives from `c`,
     * or null if not [LuaUserdata]
     * @see .optuserdata
     * @see .checkuserdata
     * @see .isuserdata
     * @see .TUSERDATA
     */
    open fun touserdata(c: KClass<*>?): Any? {
        return null
    }

    /**
     * Convert the value to a human readable string using [.tojstring]
     * @return String value intended to be human readible.
     * @see .tostring
     * @see .tojstring
     * @see .optstring
     * @see .checkstring
     * @see .toString
     */
    override fun toString(): String {
        return tojstring()
    }

    /** Conditionally convert to lua number without throwing errors.
     * 
     * 
     * In lua all numbers are strings, but not all strings are numbers.
     * This function will return
     * the [LuaValue] `this` if it is a number
     * or a string convertible to a number,
     * and [.NIL] for all other cases.
     * 
     * 
     * This allows values to be tested for their "numeric-ness" without
     * the penalty of throwing exceptions,
     * nor the cost of converting the type and creating storage for it.
     * @return `this` if it is a [LuaNumber]
     * or [LuaString] that can be converted to a number,
     * otherwise [.NIL]
     * @see .tostring
     * @see .optnumber
     * @see .checknumber
     * @see .toint
     * @see .todouble
     */
    open fun tonumber(): LuaValue {
        return net.blueva.luak.LuaValue.Companion.NIL
    }

    /** Conditionally convert to lua string without throwing errors.
     * 
     * 
     * In lua all numbers are strings, so this function will return
     * the [LuaValue] `this` if it is a string or number,
     * and [.NIL] for all other cases.
     * 
     * 
     * This allows values to be tested for their "string-ness" without
     * the penalty of throwing exceptions.
     * @return `this` if it is a [LuaString] or [LuaNumber],
     * otherwise [.NIL]
     * @see .tonumber
     * @see .tojstring
     * @see .optstring
     * @see .checkstring
     * @see .toString
     */
    open fun tostring(): LuaValue {
        return net.blueva.luak.LuaValue.Companion.NIL
    }

    /** Check that optional argument is a boolean and return its boolean value
     * @param defval boolean value to return if `this` is nil or none
     * @return `this` cast to boolean if a [LuaBoolean],
     * `defval` if nil or none,
     * throws [LuaError] otherwise
     * @throws LuaError if was not a boolean or nil or none.
     * @see .checkboolean
     * @see .isboolean
     * @see .TBOOLEAN
     */
    open fun optboolean(defval: Boolean): Boolean {
        argerror("boolean")
        return false
    }

    /** Check that optional argument is a closure and return as [LuaClosure]
     * 
     * 
     * A [LuaClosure] is a [LuaFunction] that executes lua byteccode.
     * @param defval [LuaClosure] to return if `this` is nil or none
     * @return `this` cast to [LuaClosure] if a function,
     * `defval` if nil or none,
     * throws [LuaError] otherwise
     * @throws LuaError if was not a closure or nil or none.
     * @see .checkclosure
     * @see .isclosure
     * @see .TFUNCTION
     */
    open fun optclosure(defval: LuaClosure?): LuaClosure? {
        argerror("closure")
        return null
    }

    /** Check that optional argument is a number or string convertible to number and return as double
     * @param defval double to return if `this` is nil or none
     * @return `this` cast to double if numeric,
     * `defval` if nil or none,
     * throws [LuaError] otherwise
     * @throws LuaError if was not numeric or nil or none.
     * @see .optint
     * @see .optinteger
     * @see .checkdouble
     * @see .todouble
     * @see .tonumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun optdouble(defval: Double): Double {
        argerror("number")
        return 0.0
    }

    /** Check that optional argument is a function and return as [LuaFunction]
     * 
     * 
     * A [LuaFunction] may either be a Java function that implements
     * functionality directly in Java,  or a [LuaClosure]
     * which is a [LuaFunction] that executes lua bytecode.
     * @param defval [LuaFunction] to return if `this` is nil or none
     * @return `this` cast to [LuaFunction] if a function,
     * `defval` if nil or none,
     * throws [LuaError] otherwise
     * @throws LuaError if was not a function or nil or none.
     * @see .checkfunction
     * @see .isfunction
     * @see .TFUNCTION
     */
    open fun optfunction(defval: LuaFunction?): LuaFunction? {
        argerror("function")
        return null
    }

    /** Check that optional argument is a number or string convertible to number and return as int
     * @param defval int to return if `this` is nil or none
     * @return `this` cast to int if numeric,
     * `defval` if nil or none,
     * throws [LuaError] otherwise
     * @throws LuaError if was not numeric or nil or none.
     * @see .optdouble
     * @see .optlong
     * @see .optinteger
     * @see .checkint
     * @see .toint
     * @see .tonumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun optint(defval: Int): Int {
        argerror("int")
        return 0
    }

    /** Check that optional argument is a number or string convertible to number and return as [LuaInteger]
     * @param defval [LuaInteger] to return if `this` is nil or none
     * @return `this` converted and wrapped in [LuaInteger] if numeric,
     * `defval` if nil or none,
     * throws [LuaError] otherwise
     * @throws LuaError if was not numeric or nil or none.
     * @see .optdouble
     * @see .optint
     * @see .checkint
     * @see .toint
     * @see .tonumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun optinteger(defval: LuaInteger?): LuaInteger? {
        argerror("integer")
        return null
    }

    /** Check that optional argument is a number or string convertible to number and return as long
     * @param defval long to return if `this` is nil or none
     * @return `this` cast to long if numeric,
     * `defval` if nil or none,
     * throws [LuaError] otherwise
     * @throws LuaError if was not numeric or nil or none.
     * @see .optdouble
     * @see .optint
     * @see .checkint
     * @see .toint
     * @see .tonumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun optlong(defval: Long): Long {
        argerror("long")
        return 0
    }

    /** Check that optional argument is a number or string convertible to number and return as [LuaNumber]
     * @param defval [LuaNumber] to return if `this` is nil or none
     * @return `this` cast to [LuaNumber] if numeric,
     * `defval` if nil or none,
     * throws [LuaError] otherwise
     * @throws LuaError if was not numeric or nil or none.
     * @see .optdouble
     * @see .optlong
     * @see .optint
     * @see .checkint
     * @see .toint
     * @see .tonumber
     * @see .isnumber
     * @see .TNUMBER
     */
    open fun optnumber(defval: LuaNumber?): LuaNumber? {
        argerror("number")
        return null
    }

    /** Check that optional argument is a string or number and return as Java String
     * @param defval [LuaString] to return if `this` is nil or none
     * @return `this` converted to String if a string or number,
     * `defval` if nil or none,
     * throws [LuaError] if some other type
     * @throws LuaError if was not a string or number or nil or none.
     * @see .tojstring
     * @see .optstring
     * @see .checkjstring
     * @see .toString
     * @see .TSTRING
     */
    open fun optjstring(defval: String?): String? {
        argerror("string")
        return null
    }

    /** Check that optional argument is a string or number and return as [LuaString]
     * @param defval [LuaString] to return if `this` is nil or none
     * @return `this` converted to [LuaString] if a string or number,
     * `defval` if nil or none,
     * throws [LuaError] if some other type
     * @throws LuaError if was not a string or number or nil or none.
     * @see .tojstring
     * @see .optjstring
     * @see .checkstring
     * @see .toString
     * @see .TSTRING
     */
    open fun optstring(defval: LuaString?): LuaString? {
        argerror("string")
        return null
    }

    /** Check that optional argument is a table and return as [LuaTable]
     * @param defval [LuaTable] to return if `this` is nil or none
     * @return `this` cast to [LuaTable] if a table,
     * `defval` if nil or none,
     * throws [LuaError] if some other type
     * @throws LuaError if was not a table or nil or none.
     * @see .checktable
     * @see .istable
     * @see .TTABLE
     */
    open fun opttable(defval: LuaTable?): LuaTable? {
        argerror("table")
        return null
    }

    /** Check that optional argument is a thread and return as [LuaThread]
     * @param defval [LuaThread] to return if `this` is nil or none
     * @return `this` cast to [LuaTable] if a thread,
     * `defval` if nil or none,
     * throws [LuaError] if some other type
     * @throws LuaError if was not a thread or nil or none.
     * @see .checkthread
     * @see .isthread
     * @see .TTHREAD
     */
    open fun optthread(defval: LuaThread?): LuaThread? {
        argerror("thread")
        return null
    }

    /** Check that optional argument is a userdata and return the Any instance
     * @param defval Any to return if `this` is nil or none
     * @return Any instance of the userdata if a [LuaUserdata],
     * `defval` if nil or none,
     * throws [LuaError] if some other type
     * @throws LuaError if was not a userdata or nil or none.
     * @see .checkuserdata
     * @see .isuserdata
     * @see .optuserdata
     * @see .TUSERDATA
     */
    open fun optuserdata(defval: Any?): Any? {
        argerror("object")
        return null
    }

    /** Check that optional argument is a userdata whose instance is of a type
     * and return the Any instance
     * @param c Class to test userdata instance against
     * @param defval Any to return if `this` is nil or none
     * @return Any instance of the userdata if a [LuaUserdata] and instance is assignable to `c`,
     * `defval` if nil or none,
     * throws [LuaError] if some other type
     * @throws LuaError if was not a userdata whose instance is assignable to `c` or nil or none.
     * @see .checkuserdata
     * @see .isuserdata
     * @see .optuserdata
     * @see .TUSERDATA
     */
    open fun optuserdata(c: KClass<*>, defval: Any?): Any? {
        argerror(platformTypeName(c))
        return null
    }

    /** Perform argument check that this is not nil or none.
     * @param defval [LuaValue] to return if `this` is nil or none
     * @return `this` if not nil or none, else `defval`
     * @see .NIL
     * 
     * @see .NONE
     * 
     * @see .isnil
     * @see Varargs.isnoneornil
     * @see .TNIL
     * 
     * @see .TNONE
     */
    open fun optvalue(defval: LuaValue?): LuaValue {
        return this
    }


    /** Check that the value is a [LuaBoolean],
     * or throw [LuaError] if not
     * @return boolean value for `this` if it is a [LuaBoolean]
     * @throws LuaError if not a [LuaBoolean]
     * @see .optboolean
     * @see .TBOOLEAN
     */
    open fun checkboolean(): Boolean {
        argerror("boolean")
        return false
    }

    /** Check that the value is a [LuaClosure] ,
     * or throw [LuaError] if not
     * 
     * 
     * [LuaClosure] is a subclass of [LuaFunction] that interprets lua bytecode.
     * @return `this` cast as [LuaClosure]
     * @throws LuaError if not a [LuaClosure]
     * @see .checkfunction
     * @see .optclosure
     * @see .isclosure
     * @see .TFUNCTION
     */
    open fun checkclosure(): LuaClosure? {
        argerror("closure")
        return null
    }

    /** Check that the value is numeric and return the value as a double,
     * or throw [LuaError] if not numeric
     * 
     * 
     * Values that are [LuaNumber] and values that are [LuaString]
     * that can be converted to a number will be converted to double.
     * @return value cast to a double if numeric
     * @throws LuaError if not a [LuaNumber] or is a [LuaString] that can't be converted to number
     * @see .checkint
     * @see .checkinteger
     * @see .checklong
     * @see .optdouble
     * @see .TNUMBER
     */
    open fun checkdouble(): Double {
        argerror("number")
        return 0.0
    }

    /** Check that the value is a function , or throw [LuaError] if not
     * 
     * 
     * A [LuaFunction] may either be a Java function that implements
     * functionality directly in Java,  or a [LuaClosure]
     * which is a [LuaFunction] that executes lua bytecode.
     * @return `this` if it is a lua function or closure
     * @throws LuaError if not a function
     * @see .checkclosure
     */
    open fun checkfunction(): LuaFunction? {
        argerror("function")
        return null
    }


    /** Check that the value is a Globals instance, or throw [LuaError] if not
     * 
     * 
     * [Globals] are a special [LuaTable] that establish the default global environment.
     * @return `this` if if an instance fof [Globals]
     * @throws LuaError if not a [Globals] instance.
     */
    open fun checkglobals(): Globals? {
        argerror("globals")
        return null
    }

    /** Check that the value is numeric, and convert and cast value to int, or throw [LuaError] if not numeric
     * 
     * 
     * Values that are [LuaNumber] will be cast to int and may lose precision.
     * Values that are [LuaString] that can be converted to a number will be converted,
     * then cast to int, so may also lose precision.
     * @return value cast to a int if numeric
     * @throws LuaError if not a [LuaNumber] or is a [LuaString] that can't be converted to number
     * @see .checkinteger
     * @see .checklong
     * @see .checkdouble
     * @see .optint
     * @see .TNUMBER
     */
    open fun checkint(): Int {
        argerror("int")
        return 0
    }

    /** Check that the value is numeric, and convert and cast value to int, or throw [LuaError] if not numeric
     * 
     * 
     * Values that are [LuaNumber] will be cast to int and may lose precision.
     * Values that are [LuaString] that can be converted to a number will be converted,
     * then cast to int, so may also lose precision.
     * @return value cast to a int and wrapped in [LuaInteger] if numeric
     * @throws LuaError if not a [LuaNumber] or is a [LuaString] that can't be converted to number
     * @see .checkint
     * @see .checklong
     * @see .checkdouble
     * @see .optinteger
     * @see .TNUMBER
     */
    open fun checkinteger(): LuaInteger? {
        argerror("integer")
        return null
    }

    /** Check that the value is numeric, and convert and cast value to long, or throw [LuaError] if not numeric
     * 
     * 
     * Values that are [LuaNumber] will be cast to long and may lose precision.
     * Values that are [LuaString] that can be converted to a number will be converted,
     * then cast to long, so may also lose precision.
     * @return value cast to a long if numeric
     * @throws LuaError if not a [LuaNumber] or is a [LuaString] that can't be converted to number
     * @see .checkint
     * @see .checkinteger
     * @see .checkdouble
     * @see .optlong
     * @see .TNUMBER
     */
    open fun checklong(): Long {
        argerror("long")
        return 0
    }

    /** Check that the value is numeric, and return as a LuaNumber if so, or throw [LuaError]
     * 
     * 
     * Values that are [LuaString] that can be converted to a number will be converted and returned.
     * @return value as a [LuaNumber] if numeric
     * @throws LuaError if not a [LuaNumber] or is a [LuaString] that can't be converted to number
     * @see .checkint
     * @see .checkinteger
     * @see .checkdouble
     * @see .checklong
     * @see .optnumber
     * @see .TNUMBER
     */
    open fun checknumber(): LuaNumber? {
        argerror("number")
        return null
    }

    /** Check that the value is numeric, and return as a LuaNumber if so, or throw [LuaError]
     * 
     * 
     * Values that are [LuaString] that can be converted to a number will be converted and returned.
     * @param msg String message to supply if conversion fails
     * @return value as a [LuaNumber] if numeric
     * @throws LuaError if not a [LuaNumber] or is a [LuaString] that can't be converted to number
     * @see .checkint
     * @see .checkinteger
     * @see .checkdouble
     * @see .checklong
     * @see .optnumber
     * @see .TNUMBER
     */
    open fun checknumber(msg: String?): LuaNumber? {
        throw LuaError(msg)
    }

    /** Convert this value to a Java String.
     * 
     * 
     * The string representations here will roughly match what is produced by the
     * C lua distribution, however hash codes have no relationship,
     * and there may be differences in number formatting.
     * @return String representation of the value
     * @see .checkstring
     * @see .optjstring
     * @see .tojstring
     * @see .isstring
     * 
     * @see .TSTRING
     */
    open fun checkjstring(): String? {
        argerror("string")
        return null
    }

    /** Check that this is a lua string, or throw [LuaError] if it is not.
     * 
     * 
     * In lua all numbers are strings, so this will succeed for
     * anything that derives from [LuaString] or [LuaNumber].
     * Numbers will be converted to [LuaString].
     * 
     * @return [LuaString] representation of the value if it is a [LuaString] or [LuaNumber]
     * @throws LuaError if `this` is not a [LuaTable]
     * @see .checkjstring
     * @see .optstring
     * @see .tostring
     * @see .isstring
     * @see .TSTRING
     */
    open fun checkstring(): LuaString? {
        argerror("string")
        return null
    }

    /** Check that this is a [LuaTable], or throw [LuaError] if it is not
     * @return `this` if it is a [LuaTable]
     * @throws LuaError if `this` is not a [LuaTable]
     * @see .istable
     * @see .opttable
     * @see .TTABLE
     */
    open fun checktable(): LuaTable? {
        argerror("table")
        return null
    }

    /** Check that this is a [LuaThread], or throw [LuaError] if it is not
     * @return `this` if it is a [LuaThread]
     * @throws LuaError if `this` is not a [LuaThread]
     * @see .isthread
     * @see .optthread
     * @see .TTHREAD
     */
    open fun checkthread(): LuaThread? {
        argerror("thread")
        return null
    }

    /** Check that this is a [LuaUserdata], or throw [LuaError] if it is not
     * @return `this` if it is a [LuaUserdata]
     * @throws LuaError if `this` is not a [LuaUserdata]
     * @see .isuserdata
     * @see .optuserdata
     * @see .checkuserdata
     * @see .TUSERDATA
     */
    open fun checkuserdata(): Any? {
        argerror("userdata")
        return null
    }

    /** Check that this is a [LuaUserdata], or throw [LuaError] if it is not
     * @return `this` if it is a [LuaUserdata]
     * @throws LuaError if `this` is not a [LuaUserdata]
     * @see .isuserdata
     * @see .optuserdata
     * @see .checkuserdata
     * @see .TUSERDATA
     */
    open fun checkuserdata(c: KClass<*>?): Any? {
        argerror("userdata")
        return null
    }

    /** Check that this is not the value [.NIL], or throw [LuaError] if it is
     * @return `this` if it is not [.NIL]
     * @throws LuaError if `this` is [.NIL]
     * @see .optvalue
     */
    open fun checknotnil(): LuaValue {
        return this
    }

    /** Return true if this is a valid key in a table index operation.
     * @return true if valid as a table key, otherwise false
     * @see .isnil
     * @see .isinttype
     */
    open fun isvalidkey(): Boolean {
        return true
    }

    /**
     * Throw a [LuaError] indicating an invalid argument was supplied to a function
     * @param expected String naming the type that was expected
     * @throws LuaError in all cases
     */
    protected fun argerror(expected: String?): LuaValue? {
        throw LuaError("bad argument: " + expected + " expected, got " + argtypename())
    }

    /**
     * The type name an argument error calls this value.
     *
     * A `__name` field renames the type, and a light userdata is named as one
     * so a script told "userdata expected" can see what it actually passed.
     */
    internal fun argtypename(): String {
        val mt: LuaValue? = getmetatable()
        if (mt != null) {
            val name: LuaValue = mt.rawget(net.blueva.luak.LuaValue.Companion.NAME)
            if (name.type() == net.blueva.luak.LuaValue.Companion.TSTRING) return name.tojstring()
        }
        if (this is LuaLightUserdata) return "light userdata"
        return typename()!!
    }

    /**
     * Throw a [LuaError] indicating an invalid type was supplied to a function
     * @param expected String naming the type that was expected
     * @throws LuaError in all cases
     */
    protected fun typerror(expected: String?): LuaValue? {
        throw LuaError(expected.toString() + " expected, got " + argtypename())
    }

    /**
     * Throw a [LuaError] indicating an operation is not implemented
     * @throws LuaError in all cases
     */
    protected fun unimplemented(`fun`: String?): LuaValue {
        throw LuaError("'" + `fun` + "' not implemented for " + typename())
    }

    /**
     * Throw a [LuaError] indicating an illegal operation occurred,
     * typically involved in managing weak references
     * @throws LuaError in all cases
     */
    protected fun illegal(op: String?, typename: String?): LuaValue? {
        throw LuaError("illegal operation '" + op + "' for " + typename)
    }

    /**
     * Throw a [LuaError] based on the len operator,
     * typically due to an invalid operand type
     * @throws LuaError in all cases
     */
    protected fun lenerror(): LuaValue? {
        throw LuaError("attempt to get length of " + typename())
    }

    /**
     * Throw a [LuaError] based on an arithmetic error such as add, or pow,
     * typically due to an invalid operand type
     * @throws LuaError in all cases
     */
    protected fun aritherror(): LuaValue? {
        throw LuaError("attempt to perform arithmetic on " + typename())
    }

    /**
     * Throw a [LuaError] based on an arithmetic error such as add, or pow,
     * typically due to an invalid operand type
     * @param fun String description of the function that was attempted
     * @throws LuaError in all cases
     */
    protected fun aritherror(`fun`: String?): LuaValue? {
        throw LuaError("attempt to perform arithmetic '" + `fun` + "' on " + typename())
    }

    /**
     * Throw a [LuaError] based on a comparison error such as greater-than or less-than,
     * typically due to an invalid operand type
     * @param rhs String description of what was on the right-hand-side of the comparison that resulted in the error.
     * @throws LuaError in all cases
     */
    protected fun compareerror(rhs: String?): LuaValue? {
        throw LuaError("attempt to compare " + typename() + " with " + rhs)
    }

    /**
     * Throw a [LuaError] based on a comparison error such as greater-than or less-than,
     * typically due to an invalid operand type
     * @param rhs Right-hand-side of the comparison that resulted in the error.
     * @throws LuaError in all cases
     */
    protected fun compareerror(rhs: LuaValue): LuaValue? {
        throw LuaError("attempt to compare " + typename() + " with " + rhs.typename())
    }

    /** Get a value in a table including metatag processing using [.INDEX].
     * @param key the key to look up, must not be [.NIL] or null
     * @return [LuaValue] for that key, or [.NIL] if not found and no metatag
     * @throws LuaError if `this` is not a table,
     * or there is no [.INDEX] metatag,
     * or key is [.NIL]
     * @see .get
     * @see .get
     * @see .rawget
     */
    open fun get(key: LuaValue): LuaValue {
        return net.blueva.luak.LuaValue.Companion.gettable(this, key)
    }

    /** Get a value in a table including metatag processing using [.INDEX].
     * @param key the key to look up
     * @return [LuaValue] for that key, or [.NIL] if not found
     * @throws LuaError if `this` is not a table,
     * or there is no [.INDEX] metatag
     * @see .get
     * @see .rawget
     */
    open fun get(key: Int): LuaValue? {
        return get((LuaInteger.valueOf(key))!!)
    }

    /** Get a value in a table including metatag processing using [.INDEX].
     * @param key the key to look up, must not be null
     * @return [LuaValue] for that key, or [.NIL] if not found
     * @throws LuaError if `this` is not a table,
     * or there is no [.INDEX] metatag
     * @see .get
     * @see .rawget
     */
    open fun get(key: String?): LuaValue? {
        return get(net.blueva.luak.LuaValue.Companion.valueOf(key))
    }

    /** Set a value in a table without metatag processing using [.NEWINDEX].
     * @param key the key to use, must not be [.NIL] or null
     * @param value the value to use, can be [.NIL], must not be null
     * @throws LuaError if `this` is not a table,
     * or key is [.NIL],
     * or there is no [.NEWINDEX] metatag
     */
    open fun set(key: LuaValue?, value: LuaValue?) {
        net.blueva.luak.LuaValue.Companion.settable(this, key, value)
    }

    /** Set a value in a table without metatag processing using [.NEWINDEX].
     * @param key the key to use
     * @param value the value to use, can be [.NIL], must not be null
     * @throws LuaError if `this` is not a table,
     * or there is no [.NEWINDEX] metatag
     */
    open fun set(key: Int, value: LuaValue?) {
        set(LuaInteger.valueOf(key), value)
    }

    /** Set a value in a table without metatag processing using [.NEWINDEX].
     * @param key the key to use
     * @param value the value to use, must not be null
     * @throws LuaError if `this` is not a table,
     * or there is no [.NEWINDEX] metatag
     */
    open fun set(key: Int, value: String?) {
        set(key, net.blueva.luak.LuaValue.Companion.valueOf(value))
    }

    /** Set a value in a table without metatag processing using [.NEWINDEX].
     * @param key the key to use, must not be [.NIL] or null
     * @param value the value to use, can be [.NIL], must not be null
     * @throws LuaError if `this` is not a table,
     * or there is no [.NEWINDEX] metatag
     */
    open fun set(key: String?, value: LuaValue?) {
        set(net.blueva.luak.LuaValue.Companion.valueOf(key), value)
    }

    /** Set a value in a table without metatag processing using [.NEWINDEX].
     * @param key the key to use, must not be null
     * @param value the value to use
     * @throws LuaError if `this` is not a table,
     * or there is no [.NEWINDEX] metatag
     */
    open fun set(key: String?, value: Double) {
        set(net.blueva.luak.LuaValue.Companion.valueOf(key), net.blueva.luak.LuaValue.Companion.valueOf(value))
    }

    /** Set a value in a table without metatag processing using [.NEWINDEX].
     * @param key the key to use, must not be null
     * @param value the value to use
     * @throws LuaError if `this` is not a table,
     * or there is no [.NEWINDEX] metatag
     */
    open fun set(key: String?, value: Int) {
        set(net.blueva.luak.LuaValue.Companion.valueOf(key), net.blueva.luak.LuaValue.Companion.valueOf(value))
    }

    /** Set a value in a table without metatag processing using [.NEWINDEX].
     * @param key the key to use, must not be null
     * @param value the value to use, must not be null
     * @throws LuaError if `this` is not a table,
     * or there is no [.NEWINDEX] metatag
     */
    open fun set(key: String?, value: String?) {
        set(net.blueva.luak.LuaValue.Companion.valueOf(key), net.blueva.luak.LuaValue.Companion.valueOf(value))
    }

    /** Get a value in a table without metatag processing.
     * @param key the key to look up, must not be [.NIL] or null
     * @return [LuaValue] for that key, or [.NIL] if not found
     * @throws LuaError if `this` is not a table, or key is [.NIL]
     */
    open fun rawget(key: LuaValue?): LuaValue {
        return unimplemented("rawget")
    }

    /** Get a value in a table without metatag processing.
     * @param key the key to look up
     * @return [LuaValue] for that key, or [.NIL] if not found
     * @throws LuaError if `this` is not a table
     */
    open fun rawget(key: Int): LuaValue? {
        return rawget(net.blueva.luak.LuaValue.Companion.valueOf(key))
    }

    /** Get a value in a table without metatag processing.
     * @param key the key to look up, must not be null
     * @return [LuaValue] for that key, or [.NIL] if not found
     * @throws LuaError if `this` is not a table
     */
    open fun rawget(key: String?): LuaValue? {
        return rawget(net.blueva.luak.LuaValue.Companion.valueOf(key))
    }

    /** Set a value in a table without metatag processing.
     * @param key the key to use, must not be [.NIL] or null
     * @param value the value to use, can be [.NIL], must not be null
     * @throws LuaError if `this` is not a table, or key is [.NIL]
     */
    open fun rawset(key: LuaValue?, value: LuaValue?) {
        unimplemented("rawset")
    }

    /** Set a value in a table without metatag processing.
     * @param key the key to use
     * @param value the value to use, can be [.NIL], must not be null
     * @throws LuaError if `this` is not a table
     */
    open fun rawset(key: Int, value: LuaValue?) {
        rawset(net.blueva.luak.LuaValue.Companion.valueOf(key), value)
    }

    /** Set a value in a table without metatag processing.
     * @param key the key to use
     * @param value the value to use, can be [.NIL], must not be null
     * @throws LuaError if `this` is not a table
     */
    fun rawset(key: Int, value: String?) {
        rawset(key, net.blueva.luak.LuaValue.Companion.valueOf(value))
    }

    /** Set a value in a table without metatag processing.
     * @param key the key to use, must not be null
     * @param value the value to use, can be [.NIL], must not be null
     * @throws LuaError if `this` is not a table
     */
    fun rawset(key: String?, value: LuaValue?) {
        rawset(net.blueva.luak.LuaValue.Companion.valueOf(key), value)
    }

    /** Set a value in a table without metatag processing.
     * @param key the key to use, must not be null
     * @param value the value to use
     * @throws LuaError if `this` is not a table
     */
    fun rawset(key: String?, value: Double) {
        rawset(net.blueva.luak.LuaValue.Companion.valueOf(key), net.blueva.luak.LuaValue.Companion.valueOf(value))
    }

    /** Set a value in a table without metatag processing.
     * @param key the key to use, must not be null
     * @param value the value to use
     * @throws LuaError if `this` is not a table
     */
    fun rawset(key: String?, value: Int) {
        rawset(net.blueva.luak.LuaValue.Companion.valueOf(key), net.blueva.luak.LuaValue.Companion.valueOf(value))
    }

    /** Set a value in a table without metatag processing.
     * @param key the key to use, must not be null
     * @param value the value to use, must not be null
     * @throws LuaError if `this` is not a table
     */
    fun rawset(key: String?, value: String?) {
        rawset(net.blueva.luak.LuaValue.Companion.valueOf(key), net.blueva.luak.LuaValue.Companion.valueOf(value))
    }

    /** Set list values in a table without invoking metatag processing
     * 
     * 
     * Primarily used internally in response to a SETLIST bytecode.
     * @param key0 the first key to set in the table
     * @param values the list of values to set
     * @throws LuaError if this is not a table.
     */
    fun rawsetlist(key0: Int, values: Varargs) {
        var i = 0
        val n: Int = values.narg()
        while (i < n) {
            rawset(key0 + i, values.arg(i + 1))
            i++
        }
    }

    /** Preallocate the array part of a table to be a certain size,
     * 
     * 
     * Primarily used internally in response to a SETLIST bytecode.
     * @param i the number of array slots to preallocate in the table.
     * @throws LuaError if this is not a table.
     */
    open fun presize(i: Int) {
        typerror("table")
    }

    /** Find the next key,value pair if `this` is a table,
     * return [.NIL] if there are no more, or throw a [LuaError] if not a table.
     * 
     * 
     * To iterate over all key-value pairs in a table you can use
     * <pre> `LuaValue k = LuaValue.NIL; while ( true ) {    Varargs n = table.next(k);    if ( (k = n.arg1()).isnil() )       break;    LuaValue v = n.arg(2)    process( k, v ) }`</pre>
     * @param index [LuaInteger] value identifying a key to start from,
     * or [.NIL] to start at the beginning
     * @return [Varargs] containing {key,value} for the next entry,
     * or [.NIL] if there are no more.
     * @throws LuaError if `this` is not a table, or the supplied key is invalid.
     * @see LuaTable
     * 
     * @see .inext
     * @see .valueOf
     * @see Varargs.arg1
     * @see Varargs.arg
     * @see .isnil
     */
    open fun next(index: LuaValue?): Varargs? {
        return typerror("table")
    }

    /** Find the next integer-key,value pair if `this` is a table,
     * return [.NIL] if there are no more, or throw a [LuaError] if not a table.
     * 
     * 
     * To iterate over integer keys in a table you can use
     * <pre> `LuaValue k = LuaValue.NIL;   while ( true ) {      Varargs n = table.inext(k);      if ( (k = n.arg1()).isnil() )         break;      LuaValue v = n.arg(2)      process( k, v )   } ` </pre>
     * @param index [LuaInteger] value identifying a key to start from,
     * or [.NIL] to start at the beginning
     * @return [Varargs] containing `(key,value)` for the next entry,
     * or [.NONE] if there are no more.
     * @throws LuaError if `this` is not a table, or the supplied key is invalid.
     * @see LuaTable
     * 
     * @see .next
     * @see .valueOf
     * @see Varargs.arg1
     * @see Varargs.arg
     * @see .isnil
     */
    open fun inext(index: LuaValue?): Varargs? {
        return typerror("table")
    }

    /**
     * Load a library instance by calling it with and empty string as the modname,
     * and this Globals as the environment. This is normally used to iniitalize the
     * library instance and which may install itself into these globals.
     * @param library The callable [LuaValue] to load into `this`
     * @return [LuaValue] returned by the initialization call.
     */
    open fun load(library: LuaValue): LuaValue {
        return library.call(net.blueva.luak.LuaValue.Companion.EMPTYSTRING, this)!!
    }

    // varargs references
    override fun arg(index: Int): LuaValue {
        return if (index == 1) this else net.blueva.luak.LuaValue.Companion.NIL
    }

    override fun narg(): Int {
        return 1
    }

    override fun arg1(): LuaValue {
        return this
    }

    /**
     * Get the metatable for this [LuaValue]
     * 
     * 
     * For [LuaTable] and [LuaUserdata] instances,
     * the metatable returned is this instance metatable.
     * For all other types, the class metatable value will be returned.
     * @return metatable, or null if it there is none
     * @see LuaBoolean.s_metatable
     * 
     * @see LuaNumber.s_metatable
     * 
     * @see LuaNil.s_metatable
     * 
     * @see LuaFunction.s_metatable
     * 
     * @see LuaThread.s_metatable
     */
    open fun getmetatable(): LuaValue? {
        return null
    }

    /**
     * Set the metatable for this [LuaValue]
     * 
     * 
     * For [LuaTable] and [LuaUserdata] instances, the metatable is per instance.
     * For all other types, there is one metatable per type that can be set directly from java
     * @param metatable [LuaValue] instance to serve as the metatable, or null to reset it.
     * @return `this` to allow chaining of Java function calls
     * @see LuaBoolean.s_metatable
     * 
     * @see LuaNumber.s_metatable
     * 
     * @see LuaNil.s_metatable
     * 
     * @see LuaFunction.s_metatable
     * 
     * @see LuaThread.s_metatable
     */
    open fun setmetatable(metatable: LuaValue?): LuaValue? {
        return argerror("table")
    }

    /** Call `this` with 0 arguments, including metatag processing,
     * and return only the first return value.
     * 
     * 
     * If `this` is a [LuaFunction], call it,
     * and return only its first return value, dropping any others.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * If the return value is a [Varargs], only the 1st value will be returned.
     * To get multiple values, use [.invoke] instead.
     * 
     * 
     * To call `this` as a method call, use [.method] instead.
     * 
     * @return First return value `(this())`, or [.NIL] if there were none.
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .call
     * @see .call
     * @see .invoke
     * @see .method
     * @see .method
     */
    open fun call(): LuaValue? {
        return invoke(net.blueva.luak.LuaValue.Companion.NONE!!).arg1()!!
    }

    /** Call `this` with 1 argument, including metatag processing,
     * and return only the first return value.
     * 
     * 
     * If `this` is a [LuaFunction], call it,
     * and return only its first return value, dropping any others.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * If the return value is a [Varargs], only the 1st value will be returned.
     * To get multiple values, use [.invoke] instead.
     * 
     * 
     * To call `this` as a method call, use [.method] instead.
     * 
     * @param arg First argument to supply to the called function
     * @return First return value `(this(arg))`, or [.NIL] if there were none.
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .call
     * @see .call
     * @see .invoke
     * @see .method
     * @see .method
     */
    open fun call(arg: LuaValue?): LuaValue? {
        return invoke(arg!!).arg1()!!
    }

    /** Convenience function which calls a luavalue with a single, string argument.
     * @param arg String argument to the function.  This will be converted to a LuaString.
     * @return return value of the invocation.
     * @see .call
     */
    open fun call(arg: String?): LuaValue? {
        return call(net.blueva.luak.LuaValue.Companion.valueOf(arg))
    }

    /** Call `this` with 2 arguments, including metatag processing,
     * and return only the first return value.
     * 
     * 
     * If `this` is a [LuaFunction], call it,
     * and return only its first return value, dropping any others.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * If the return value is a [Varargs], only the 1st value will be returned.
     * To get multiple values, use [.invoke] instead.
     * 
     * 
     * To call `this` as a method call, use [.method] instead.
     * 
     * @param arg1 First argument to supply to the called function
     * @param arg2 Second argument to supply to the called function
     * @return First return value `(this(arg1,arg2))`, or [.NIL] if there were none.
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .call
     * @see .call
     * @see .invoke
     * @see .method
     * @see .method
     */
    open fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue? {
        return invoke(net.blueva.luak.LuaValue.Companion.varargsOf(arg1, arg2!!)).arg1()!!
    }

    /** Call `this` with 3 arguments, including metatag processing,
     * and return only the first return value.
     * 
     * 
     * If `this` is a [LuaFunction], call it,
     * and return only its first return value, dropping any others.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * If the return value is a [Varargs], only the 1st value will be returned.
     * To get multiple values, use [.invoke] instead.
     * 
     * 
     * To call `this` as a method call, use [.method] instead.
     * 
     * @param arg1 First argument to supply to the called function
     * @param arg2 Second argument to supply to the called function
     * @param arg3 Second argument to supply to the called function
     * @return First return value `(this(arg1,arg2,arg3))`, or [.NIL] if there were none.
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .call
     * @see .call
     * @see .invoke
     * @see .invokemethod
     * @see .invokemethod
     */
    open fun call(arg1: LuaValue?, arg2: LuaValue?, arg3: LuaValue?): LuaValue? {
        return invoke(
            net.blueva.luak.LuaValue.Companion.varargsOf(arrayOf<LuaValue?>(arg1, arg2, arg3)),
        ).arg1()!!
    }

    /** Suspending counterpart to the `call`/`invoke`/`onInvoke` family, used by
     * the interpreter so a Lua-level `coroutine.yield()` can suspend all the
     * way back to `LuaThread.resume()` without blocking a thread.
     *
     * The default implementations here just delegate to the regular
     * (non-suspending) methods, so ordinary library functions need no changes
     * at all: calling them from a suspend context works, they simply never
     * suspend. Only [LuaClosure] (pure Lua-to-Lua calls) and functions that
     * are explicitly yield-aware (e.g. `coroutine.yield` itself) override
     * these to propagate suspension for real. Calling `coroutine.yield()`
     * from anything else correctly surfaces as "attempt to yield across
     * metamethod/C-call boundary", matching real Lua.
     * @see .call
     * @see .invoke
     */
    open suspend fun callSuspend(): LuaValue? {
        return call()
    }

    open suspend fun callSuspend(arg: LuaValue?): LuaValue? {
        return call(arg)
    }

    open suspend fun callSuspend(arg1: LuaValue?, arg2: LuaValue?): LuaValue? {
        return call(arg1, arg2)
    }

    open suspend fun callSuspend(arg1: LuaValue?, arg2: LuaValue?, arg3: LuaValue?): LuaValue? {
        return call(arg1, arg2, arg3)
    }

    /** Call named method on `this` with 0 arguments, including metatag processing,
     * and return only the first return value.
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument.
     * and return only its first return value, dropping any others.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * If the return value is a [Varargs], only the 1st value will be returned.
     * To get multiple values, use [.invoke] instead.
     * 
     * 
     * To call `this` as a plain call, use [.call] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @return All values returned from `this:name()` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .method
     * @see .method
     */
    fun method(name: String?): LuaValue {
        return this.get(name)!!.call(this)!!
    }

    /** Call named method on `this` with 0 arguments, including metatag processing,
     * and return only the first return value.
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument,
     * and return only its first return value, dropping any others.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * If the return value is a [Varargs], only the 1st value will be returned.
     * To get multiple values, use [.invoke] instead.
     * 
     * 
     * To call `this` as a plain call, use [.call] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @return All values returned from `this:name()` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .method
     * @see .method
     */
    fun method(name: LuaValue): LuaValue {
        return this.get(name).call(this)!!
    }

    /** Call named method on `this` with 1 argument, including metatag processing,
     * and return only the first return value.
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument,
     * and return only its first return value, dropping any others.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * If the return value is a [Varargs], only the 1st value will be returned.
     * To get multiple values, use [.invoke] instead.
     * 
     * 
     * To call `this` as a plain call, use [.call] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @param arg Argument to supply to the method
     * @return All values returned from `this:name(arg)` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .method
     * @see .method
     */
    fun method(name: String?, arg: LuaValue?): LuaValue {
        return this.get(name)!!.call(this, arg)!!
    }

    /** Call named method on `this` with 1 argument, including metatag processing,
     * and return only the first return value.
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument,
     * and return only its first return value, dropping any others.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * If the return value is a [Varargs], only the 1st value will be returned.
     * To get multiple values, use [.invoke] instead.
     * 
     * 
     * To call `this` as a plain call, use [.call] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @param arg Argument to supply to the method
     * @return All values returned from `this:name(arg)` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .method
     * @see .method
     */
    fun method(name: LuaValue, arg: LuaValue?): LuaValue {
        return this.get(name).call(this, arg)!!
    }

    /** Call named method on `this` with 2 arguments, including metatag processing,
     * and return only the first return value.
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument,
     * and return only its first return value, dropping any others.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * If the return value is a [Varargs], only the 1st value will be returned.
     * To get multiple values, use [.invoke] instead.
     * 
     * 
     * To call `this` as a plain call, use [.call] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @param arg1 First argument to supply to the method
     * @param arg2 Second argument to supply to the method
     * @return All values returned from `this:name(arg1,arg2)` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .method
     */
    fun method(name: String?, arg1: LuaValue?, arg2: LuaValue?): LuaValue {
        return this.get(name)!!.call(this, arg1, arg2)!!
    }

    /** Call named method on `this` with 2 arguments, including metatag processing,
     * and return only the first return value.
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument,
     * and return only its first return value, dropping any others.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * If the return value is a [Varargs], only the 1st value will be returned.
     * To get multiple values, use [.invoke] instead.
     * 
     * 
     * To call `this` as a plain call, use [.call] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @param arg1 First argument to supply to the method
     * @param arg2 Second argument to supply to the method
     * @return All values returned from `this:name(arg1,arg2)` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .method
     */
    fun method(name: LuaValue, arg1: LuaValue?, arg2: LuaValue?): LuaValue {
        return this.get(name).call(this, arg1, arg2)!!
    }

    /** Call `this` with 0 arguments, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * If `this` is a [LuaFunction], call it, and return all values.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a method call, use [.invokemethod] instead.
     * 
     * @return All return values as a [Varargs] instance.
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .invokemethod
     * @see .invokemethod
     */
    open fun invoke(): Varargs {
        return invoke((net.blueva.luak.LuaValue.Companion.NONE)!!)
    }

    /** Call `this` with variable arguments, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * If `this` is a [LuaFunction], call it, and return all values.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a method call, use [.invokemethod] instead.
     * 
     * @param args Varargs containing the arguments to supply to the called function
     * @return All return values as a [Varargs] instance.
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .varargsOf
     * @see .call
     * @see .invoke
     * @see .invoke
     * @see .invokemethod
     * @see .invokemethod
     */
    open fun invoke(args: Varargs): Varargs {
        val prefix: ArrayList<LuaValue> = ArrayList()
        val target: LuaValue = resolvecall(prefix)
        // Prepended outermost first, so the innermost handler's own value
        // ends up in front: Lua hands them over in the order it walked them.
        var all: Varargs = args
        for (self in prefix) all = net.blueva.luak.LuaValue.Companion.varargsOf(self, all)
        return target.invoke(all)
    }

    /** Suspending counterpart to [.invoke]; see [.callSuspend] for why this
     * exists and when it actually suspends versus just delegating. */
    open suspend fun invokeSuspend(args: Varargs): Varargs {
        return invoke(args)
    }

    /** Call `this` with variable arguments, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * If `this` is a [LuaFunction], call it, and return all values.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a method call, use [.invokemethod] instead.
     * 
     * @param arg The first argument to supply to the called function
     * @param varargs Varargs containing the remaining arguments to supply to the called function
     * @return All return values as a [Varargs] instance.
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .varargsOf
     * @see .call
     * @see .invoke
     * @see .invokemethod
     * @see .invokemethod
     */
    open fun invoke(arg: LuaValue?, varargs: Varargs): Varargs {
        return invoke((net.blueva.luak.LuaValue.Companion.varargsOf(arg, varargs))!!)
    }

    /** Call `this` with variable arguments, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * If `this` is a [LuaFunction], call it, and return all values.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a method call, use [.invokemethod] instead.
     * 
     * @param arg1 The first argument to supply to the called function
     * @param arg2 The second argument to supply to the called function
     * @param varargs Varargs containing the remaining arguments to supply to the called function
     * @return All return values as a [Varargs] instance.
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .varargsOf
     * @see .call
     * @see .invoke
     * @see .invokemethod
     * @see .invokemethod
     */
    open fun invoke(arg1: LuaValue?, arg2: LuaValue?, varargs: Varargs): Varargs {
        return invoke(net.blueva.luak.LuaValue.Companion.varargsOf(arg1, arg2, varargs))
    }

    /** Call `this` with variable arguments, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * If `this` is a [LuaFunction], call it, and return all values.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a method call, use [.invokemethod] instead.
     * 
     * @param args Array of arguments to supply to the called function
     * @return All return values as a [Varargs] instance.
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .varargsOf
     * @see .call
     * @see .invoke
     * @see .invokemethod
     * @see .invokemethod
     */
    open fun invoke(args: Array<LuaValue?>): Varargs {
        return invoke((net.blueva.luak.LuaValue.Companion.varargsOf(args))!!)
    }

    /** Call `this` with variable arguments, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * If `this` is a [LuaFunction], call it, and return all values.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a method call, use [.invokemethod] instead.
     * 
     * @param args Array of arguments to supply to the called function
     * @param varargs Varargs containing additional arguments to supply to the called function
     * @return All return values as a [Varargs] instance.
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .varargsOf
     * @see .call
     * @see .invoke
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     */
    open fun invoke(args: Array<LuaValue?>, varargs: Varargs): Varargs {
        return invoke((net.blueva.luak.LuaValue.Companion.varargsOf(args, varargs))!!)
    }

    /** Call named method on `this` with 0 arguments, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument,
     * and return all return values as a [Varargs] instance.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a plain call, use [.invoke] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @return All values returned from `this:name()` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     */
    fun invokemethod(name: String?): Varargs? {
        return get(name)!!.invoke(this)
    }

    /** Call named method on `this` with 0 arguments, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument,
     * and return all return values as a [Varargs] instance.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a plain call, use [.invoke] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @return All values returned from `this:name()` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     */
    fun invokemethod(name: LuaValue): Varargs? {
        return get(name).invoke(this)
    }

    /** Call named method on `this` with 1 argument, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument,
     * and return all return values as a [Varargs] instance.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a plain call, use [.invoke] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @param args [Varargs] containing arguments to supply to the called function after `this`
     * @return All values returned from `this:name(args)` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     */
    fun invokemethod(name: String?, args: Varargs): Varargs? {
        return get(name)!!.invoke((net.blueva.luak.LuaValue.Companion.varargsOf(this, args))!!)
    }

    /** Call named method on `this` with variable arguments, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument,
     * and return all return values as a [Varargs] instance.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a plain call, use [.invoke] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @param args [Varargs] containing arguments to supply to the called function after `this`
     * @return All values returned from `this:name(args)` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     */
    fun invokemethod(name: LuaValue, args: Varargs): Varargs? {
        return get(name).invoke((net.blueva.luak.LuaValue.Companion.varargsOf(this, args))!!)
    }

    /** Call named method on `this` with 1 argument, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument,
     * and return all return values as a [Varargs] instance.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a plain call, use [.invoke] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @param args Array of [LuaValue] containing arguments to supply to the called function after `this`
     * @return All values returned from `this:name(args)` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see varargsOf
     */
    fun invokemethod(name: String?, args: Array<LuaValue?>): Varargs? {
        return get(name)!!.invoke(
            net.blueva.luak.LuaValue.Companion.varargsOf(
                this,
                net.blueva.luak.LuaValue.Companion.varargsOf(args)
            )
        )
    }

    /** Call named method on `this` with variable arguments, including metatag processing,
     * and retain all return values in a [Varargs].
     * 
     * 
     * Look up `this[name]` and if it is a [LuaFunction],
     * call it inserting `this` as an additional first argument,
     * and return all return values as a [Varargs] instance.
     * Otherwise, look for the [.CALL] metatag and call that.
     * 
     * 
     * To get a particular return value, us [Varargs.arg]
     * 
     * 
     * To call `this` as a plain call, use [.invoke] instead.
     * 
     * @param name Name of the method to look up for invocation
     * @param args Array of [LuaValue] containing arguments to supply to the called function after `this`
     * @return All values returned from `this:name(args)` as a [Varargs] instance
     * @throws LuaError if not a function and [.CALL] is not defined,
     * or the invoked function throws a [LuaError]
     * or the invoked closure throw a lua `error`
     * @see .call
     * @see .invoke
     * @see .method
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see .invokemethod
     * @see varargsOf
     */
    fun invokemethod(name: LuaValue, args: Array<LuaValue?>): Varargs? {
        return get(name).invoke(
            net.blueva.luak.LuaValue.Companion.varargsOf(
                this,
                net.blueva.luak.LuaValue.Companion.varargsOf(args)
            )
        )
    }

    /**
     * Get the metatag value for the [.CALL] metatag, if it exists.
     * @return [LuaValue] value if metatag is defined
     * @throws LuaError if [.CALL] metatag is not defined.
     */
    /**
     * The function a call on this value really reaches, through `__call`.
     *
     * Each handler on the way takes the value it was found on as its first
     * argument, so [prefix] collects them in order. The chain is bounded: Lua
     * allows a fixed number of handlers between the value that was written and
     * the function that runs, and refuses a longer one rather than following
     * it forever.
     */
    internal fun resolvecall(prefix: ArrayList<LuaValue>): LuaValue {
        var target: LuaValue = this
        var depth = 0
        while (target !is LuaFunction) {
            val handler: LuaValue = target.metatag(net.blueva.luak.LuaValue.Companion.CALL)
            // Nothing to call: reported against the value as it was written.
            if (handler.isnil()) target.callmt()
            if (++depth > net.blueva.luak.LuaValue.Companion.MAX_CALL_CHAIN) {
                net.blueva.luak.LuaValue.Companion.error("'__call' chain too long")
            }
            prefix.add(target)
            target = handler
        }
        return target
    }

    protected fun callmt(): LuaValue {
        return checkmetatag(net.blueva.luak.LuaValue.Companion.CALL, "attempt to call ")
    }

    /** Unary not: return inverse boolean value `(~this)` as defined by lua not operator
     * @return [.TRUE] if [.NIL] or [.FALSE], otherwise [.FALSE]
     */
    open fun not(): LuaValue? {
        return net.blueva.luak.LuaValue.Companion.FALSE
    }

    /** Unary minus: return negative value `(-this)` as defined by lua unary minus operator
     * @return boolean inverse as [LuaBoolean] if boolean or nil,
     * numeric inverse as [LuaNumber] if numeric,
     * or metatag processing result if [.UNM] metatag is defined
     * @throws LuaError if  `this` is not a table or string, and has no [.UNM] metatag
     */
    open fun neg(): LuaValue {
        // Lua hands a unary operator its operand twice.
        return checkmetatag(
            net.blueva.luak.LuaValue.Companion.UNM,
            "attempt to perform arithmetic on ",
        ).call(this, this)!!
    }

    /** Length operator: return lua length of object `(#this)` including metatag processing as java int
     * @return length as defined by the lua # operator
     * or metatag processing result
     * @throws LuaError if  `this` is not a table or string, and has no [.LEN] metatag
     */
    open fun len(): LuaValue {
        // Lua hands a unary operator its operand twice.
        return checkmetatag(net.blueva.luak.LuaValue.Companion.LEN, "attempt to get length of ").call(this, this)!!
    }

    /** Length operator: return lua length of object `(#this)` including metatag processing as java int
     * @return length as defined by the lua # operator
     * or metatag processing result converted to java int using [.toint]
     * @throws LuaError if  `this` is not a table or string, and has no [.LEN] metatag
     */
    open fun length(): Int {
        return len().toint()
    }

    /** Get raw length of table or string without metatag processing.
     * @return the length of the table or string.
     * @throws LuaError if `this` is not a table or string.
     */
    open fun rawlen(): Int {
        typerror("table or string")
        return 0
    }

    // object equality, used for key comparison
    override fun equals(obj: Any?): Boolean {
        return this === obj
    }

    /** Equals: Perform equality comparison with another value
     * including metatag processing using [.EQ].
     * @param val The value to compare with.
     * @return  [.TRUE] if values are comparable and `(this == rhs)`,
     * [.FALSE] if comparable but not equal,
     * [LuaValue] if metatag processing occurs.
     * @see .eq_b
     * @see .raweq
     * @see .neq
     * @see .eqmtcall
     * @see .EQ
     */
    open fun eq(`val`: LuaValue?): LuaValue? {
        return if (eq_b(`val`)) net.blueva.luak.LuaValue.Companion.TRUE else net.blueva.luak.LuaValue.Companion.FALSE
    }

    /** Equals: Perform equality comparison with another value
     * including metatag processing using [.EQ],
     * and return java boolean
     * @param val The value to compare with.
     * @return  true if values are comparable and `(this == rhs)`,
     * false if comparable but not equal,
     * result converted to java boolean if metatag processing occurs.
     * @see .eq
     * @see .raweq
     * @see .neq_b
     * @see .eqmtcall
     * @see .EQ
     */
    open fun eq_b(`val`: LuaValue?): Boolean {
        return this === `val`
    }

    /** Notquals: Perform inequality comparison with another value
     * including metatag processing using [.EQ].
     * @param val The value to compare with.
     * @return  [.TRUE] if values are comparable and `(this != rhs)`,
     * [.FALSE] if comparable but equal,
     * inverse of [LuaValue] converted to [LuaBoolean] if metatag processing occurs.
     * @see .eq
     * @see .raweq
     * @see .eqmtcall
     * @see .EQ
     */
    fun neq(`val`: LuaValue?): LuaValue? {
        return if (eq_b(`val`)) net.blueva.luak.LuaValue.Companion.FALSE else net.blueva.luak.LuaValue.Companion.TRUE
    }

    /** Notquals: Perform inequality comparison with another value
     * including metatag processing using [.EQ].
     * @param val The value to compare with.
     * @return  true if values are comparable and `(this != rhs)`,
     * false if comparable but equal,
     * inverse of result converted to boolean if metatag processing occurs.
     * @see .eq_b
     * @see .raweq
     * @see .eqmtcall
     * @see .EQ
     */
    fun neq_b(`val`: LuaValue?): Boolean {
        return !eq_b(`val`)
    }

    /** Equals: Perform direct equality comparison with another value
     * without metatag processing.
     * @param val The value to compare with.
     * @return  true if `(this == rhs)`, false otherwise
     * @see .eq
     * @see .raweq
     * @see .raweq
     * @see .raweq
     * @see .raweq
     * @see .EQ
     */
    open fun raweq(`val`: LuaValue?): Boolean {
        return this === `val`
    }

    /** Equals: Perform direct equality comparison with a [LuaUserdata] value
     * without metatag processing.
     * @param val The [LuaUserdata] to compare with.
     * @return  true if `this` is userdata
     * and their metatables are the same using ==
     * and their instances are equal using [.equals],
     * otherwise false
     * @see .eq
     * @see .raweq
     */
    open fun raweq(`val`: LuaUserdata?): Boolean {
        return false
    }

    /** Equals: Perform direct equality comparison with a [LuaString] value
     * without metatag processing.
     * @param val The [LuaString] to compare with.
     * @return  true if `this` is a [LuaString]
     * and their byte sequences match,
     * otherwise false
     */
    open fun raweq(`val`: LuaString?): Boolean {
        return false
    }

    /** Equals: Perform direct equality comparison with a double value
     * without metatag processing.
     * @param val The double value to compare with.
     * @return  true if `this` is a [LuaNumber]
     * whose value equals val,
     * otherwise false
     */
    open fun raweq(`val`: Double): Boolean {
        return false
    }

    /** Equals: Perform direct equality comparison with a int value
     * without metatag processing.
     * @param val The double value to compare with.
     * @return  true if `this` is a [LuaNumber]
     * whose value equals val,
     * otherwise false
     */
    open fun raweq(`val`: Long): Boolean {
        return false
    }

    /** Add: Perform numeric add operation with another value
     * including metatag processing.
     * 
     * 
     * Each operand must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The right-hand-side value to perform the add with
     * @return  value of `(this + rhs)` if both are numeric,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if either operand is not a number or string convertible to number,
     * and neither has the [.ADD] metatag defined
     * @see .arithmt
     */
    open fun add(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.ADD, rhs)
    }

    /** Add: Perform numeric add operation with another value
     * of double type with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The right-hand-side value to perform the add with
     * @return  value of `(this + rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .add
     */
    open fun add(rhs: Double): LuaValue {
        return arithmtwith(net.blueva.luak.LuaValue.Companion.ADD, rhs)
    }

    /** Add: Perform numeric add operation with another value
     * of int type with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The right-hand-side value to perform the add with
     * @return  value of `(this + rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .add
     */
    open fun add(rhs: Long): LuaValue {
        return arithmtwith(net.blueva.luak.LuaValue.Companion.ADD, rhs)
    }

    /** Subtract: Perform numeric subtract operation with another value
     * of unknown type,
     * including metatag processing.
     * 
     * 
     * Each operand must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The right-hand-side value to perform the subtract with
     * @return  value of `(this - rhs)` if both are numeric,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if either operand is not a number or string convertible to number,
     * and neither has the [.SUB] metatag defined
     * @see .arithmt
     */
    open fun sub(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.SUB, rhs)
    }

    /** Subtract: Perform numeric subtract operation with another value
     * of double type with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The right-hand-side value to perform the subtract with
     * @return  value of `(this - rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .sub
     */
    open fun sub(rhs: Double): LuaValue? {
        return aritherror("sub")
    }

    /** Subtract: Perform numeric subtract operation with another value
     * of int type with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The right-hand-side value to perform the subtract with
     * @return  value of `(this - rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .sub
     */
    open fun sub(rhs: Long): LuaValue? {
        return aritherror("sub")
    }

    /** Reverse-subtract: Perform numeric subtract operation from an int value
     * with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param lhs The left-hand-side value from which to perform the subtraction
     * @return  value of `(lhs - this)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .sub
     * @see .sub
     * @see .sub
     */
    open fun subFrom(lhs: Double): LuaValue {
        return arithmtwith(net.blueva.luak.LuaValue.Companion.SUB, lhs)
    }

    /** Reverse-subtract: Perform numeric subtract operation from a double value
     * without metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * 
     * For metatag processing [.sub] must be used
     * 
     * @param lhs The left-hand-side value from which to perform the subtraction
     * @return  value of `(lhs - this)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .sub
     * @see .sub
     * @see .sub
     */
    open fun subFrom(lhs: Long): LuaValue {
        return arithmtwith(net.blueva.luak.LuaValue.Companion.SUB, lhs)
    }

    /** Multiply: Perform numeric multiply operation with another value
     * of unknown type,
     * including metatag processing.
     * 
     * 
     * Each operand must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The right-hand-side value to perform the multiply with
     * @return  value of `(this * rhs)` if both are numeric,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if either operand is not a number or string convertible to number,
     * and neither has the [.MUL] metatag defined
     * @see .arithmt
     */
    open fun mul(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.MUL, rhs)
    }

    /** Multiply: Perform numeric multiply operation with another value
     * of double type with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The right-hand-side value to perform the multiply with
     * @return  value of `(this * rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .mul
     */
    open fun mul(rhs: Double): LuaValue {
        return arithmtwith(net.blueva.luak.LuaValue.Companion.MUL, rhs)
    }

    /** Multiply: Perform numeric multiply operation with another value
     * of int type with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The right-hand-side value to perform the multiply with
     * @return  value of `(this * rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .mul
     */
    open fun mul(rhs: Long): LuaValue {
        return arithmtwith(net.blueva.luak.LuaValue.Companion.MUL, rhs)
    }

    /** Raise to power: Raise this value to a power
     * including metatag processing.
     * 
     * 
     * Each operand must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The power to raise this value to
     * @return  value of `(this ^ rhs)` if both are numeric,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if either operand is not a number or string convertible to number,
     * and neither has the [.POW] metatag defined
     * @see .arithmt
     */
    open fun pow(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.POW, rhs)
    }

    /** Raise to power: Raise this value to a power
     * of double type with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The power to raise this value to
     * @return  value of `(this ^ rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .pow
     */
    open fun pow(rhs: Double): LuaValue? {
        return aritherror("pow")
    }

    /** Raise to power: Raise this value to a power
     * of int type with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The power to raise this value to
     * @return  value of `(this ^ rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .pow
     */
    open fun pow(rhs: Long): LuaValue? {
        return aritherror("pow")
    }

    /** Reverse-raise to power: Raise another value of double type to this power
     * with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param lhs The left-hand-side value which will be raised to this power
     * @return  value of `(lhs ^ this)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .pow
     * @see .pow
     * @see .pow
     */
    open fun powWith(lhs: Double): LuaValue {
        return arithmtwith(net.blueva.luak.LuaValue.Companion.POW, lhs)
    }

    /** Reverse-raise to power: Raise another value of double type to this power
     * with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param lhs The left-hand-side value which will be raised to this power
     * @return  value of `(lhs ^ this)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .pow
     * @see .pow
     * @see .pow
     */
    open fun powWith(lhs: Long): LuaValue {
        return arithmtwith(net.blueva.luak.LuaValue.Companion.POW, lhs)
    }

    /** Divide: Perform numeric divide operation by another value
     * of unknown type,
     * including metatag processing.
     * 
     * 
     * Each operand must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The right-hand-side value to perform the divulo with
     * @return  value of `(this / rhs)` if both are numeric,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if either operand is not a number or string convertible to number,
     * and neither has the [.DIV] metatag defined
     * @see .arithmt
     */
    open fun div(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.DIV, rhs)
    }

    /** Divide: Perform numeric divide operation by another value
     * of double type without metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * 
     * For metatag processing [.div] must be used
     * 
     * @param rhs The right-hand-side value to perform the divulo with
     * @return  value of `(this / rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .div
     */
    open fun div(rhs: Double): LuaValue? {
        return aritherror("div")
    }

    /** Divide: Perform numeric divide operation by another value
     * of int type without metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * 
     * For metatag processing [.div] must be used
     * 
     * @param rhs The right-hand-side value to perform the divulo with
     * @return  value of `(this / rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .div
     */
    open fun div(rhs: Long): LuaValue? {
        return aritherror("div")
    }

    /** Floor divide: perform the `//` operation with metatag processing.
     *
     * Integer operands produce an integer, rounding towards negative infinity;
     * if either side is a float the result is a float. Dividing an integer by
     * an integer zero is an error, while the float form yields an infinity, all
     * as specified since Lua 5.3.
     *
     * @param rhs the right-hand-side value
     * @return value of `(this // rhs)`
     * @throws LuaError if either operand is not a number and has no `__idiv`
     */
    open fun idiv(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.IDIV, rhs)
    }


    /** Bitwise and: perform the `&` operation with metatag processing.
     *
     * Both operands must denote integers; see `luaBitwiseOperand`.
     *
     * @param rhs the right-hand-side value
     * @return value of `(this & rhs)`
     * @throws LuaError if either operand has no integer representation and
     *   there is no `__band` metamethod
     */
    open fun band(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.BAND, rhs)
    }

    /** Bitwise or: perform the `|` operation with metatag processing.
     *
     * Both operands must denote integers; see `luaBitwiseOperand`.
     *
     * @param rhs the right-hand-side value
     * @return value of `(this | rhs)`
     * @throws LuaError if either operand has no integer representation and
     *   there is no `__bor` metamethod
     */
    open fun bor(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.BOR, rhs)
    }

    /** Bitwise exclusive or: perform the `~` operation with metatag processing.
     *
     * Both operands must denote integers; see `luaBitwiseOperand`.
     *
     * @param rhs the right-hand-side value
     * @return value of `(this ~ rhs)`
     * @throws LuaError if either operand has no integer representation and
     *   there is no `__bxor` metamethod
     */
    open fun bxor(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.BXOR, rhs)
    }

    /** Bitwise left shift: perform the `<<` operation with metatag processing.
     *
     * Both operands must denote integers; see `luaBitwiseOperand`.
     *
     * @param rhs the right-hand-side value
     * @return value of `(this << rhs)`
     * @throws LuaError if either operand has no integer representation and
     *   there is no `__shl` metamethod
     */
    open fun shl(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.SHL, rhs)
    }

    /** Bitwise right shift: perform the `>>` operation with metatag processing.
     *
     * Both operands must denote integers; see `luaBitwiseOperand`.
     *
     * @param rhs the right-hand-side value
     * @return value of `(this >> rhs)`
     * @throws LuaError if either operand has no integer representation and
     *   there is no `__shr` metamethod
     */
    open fun shr(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.SHR, rhs)
    }

    /** Bitwise not: perform the unary `~` operation with metatag processing.
     *
     * @return value of `(~this)`
     * @throws LuaError if this has no integer representation and there is no
     *   `__bnot` metamethod
     */
    open fun bnot(): LuaValue {
        val h: LuaValue = metatag(net.blueva.luak.LuaValue.Companion.BNOT)
        if (h.isnil()) {
            net.blueva.luak.LuaValue.Companion.operandError(
                net.blueva.luak.LuaValue.Companion.BNOT,
                this,
                this,
            )
        }
        // Lua hands a unary operator its operand twice.
        checkcallable(net.blueva.luak.LuaValue.Companion.BNOT, h)
        return h.call(this, this)!!
    }

    /** Reverse-divide: Perform numeric divide operation into another value
     * with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param lhs The left-hand-side value which will be divided by this
     * @return  value of `(lhs / this)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .div
     * @see .div
     * @see .div
     */
    open fun divInto(lhs: Double): LuaValue {
        return arithmtwith(net.blueva.luak.LuaValue.Companion.DIV, lhs)
    }

    /** Modulo: Perform numeric modulo operation with another value
     * of unknown type,
     * including metatag processing.
     * 
     * 
     * Each operand must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param rhs The right-hand-side value to perform the modulo with
     * @return  value of `(this % rhs)` if both are numeric,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if either operand is not a number or string convertible to number,
     * and neither has the [.MOD] metatag defined
     * @see .arithmt
     */
    open fun mod(rhs: LuaValue): LuaValue {
        return arithmt(net.blueva.luak.LuaValue.Companion.MOD, rhs)
    }

    /** Modulo: Perform numeric modulo operation with another value
     * of double type without metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * 
     * For metatag processing [.mod] must be used
     * 
     * @param rhs The right-hand-side value to perform the modulo with
     * @return  value of `(this % rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .mod
     */
    open fun mod(rhs: Double): LuaValue? {
        return aritherror("mod")
    }

    /** Modulo: Perform numeric modulo operation with another value
     * of int type without metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * 
     * For metatag processing [.mod] must be used
     * 
     * @param rhs The right-hand-side value to perform the modulo with
     * @return  value of `(this % rhs)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .mod
     */
    open fun mod(rhs: Long): LuaValue? {
        return aritherror("mod")
    }

    /** Reverse-modulo: Perform numeric modulo operation from another value
     * with metatag processing
     * 
     * 
     * `this` must derive from [LuaNumber]
     * or derive from [LuaString] and be convertible to a number
     * 
     * @param lhs The left-hand-side value which will be modulo'ed by this
     * @return  value of `(lhs % this)` if this is numeric
     * @throws LuaError if `this` is not a number or string convertible to number
     * @see .mod
     * @see .mod
     * @see .mod
     */
    open fun modFrom(lhs: Double): LuaValue {
        return arithmtwith(net.blueva.luak.LuaValue.Companion.MOD, lhs)
    }

    /** Perform metatag processing for arithmetic operations.
     * 
     * 
     * Finds the supplied metatag value for `this` or `op2` and invokes it,
     * or throws [LuaError] if neither is defined.
     * @param tag The metatag to look up
     * @param op2 The other operand value to perform the operation with
     * @return [LuaValue] resulting from metatag processing
     * @throws LuaError if metatag was not defined for either operand
     * @see .add
     * @see .sub
     * @see .mul
     * @see .pow
     * @see .div
     * @see .mod
     * @see .ADD
     * 
     * @see .SUB
     * 
     * @see .MUL
     * 
     * @see .POW
     * 
     * @see .DIV
     * 
     * @see .MOD
     */
    protected fun arithmt(tag: LuaValue?, op2: LuaValue): LuaValue {
        var h = this.metatag(tag)
        if (h.isnil()) {
            h = op2.metatag(tag)
            if (h.isnil()) net.blueva.luak.LuaValue.Companion.operandError(tag, this, op2)
        }
        checkcallable(tag, h)
        return h.call(this, op2)!!
    }

    /**
     * Refuses a metamethod that cannot be called, naming it as Lua does.
     *
     * Whatever sits under a metatag is called without being looked at first,
     * so on its own the failure would read `attempt to call a number value`
     * and say nothing about which metamethod was reached for.
     */
    /**
     * The type name Lua puts in messages, which a metatable can override.
     *
     * A `__name` field lets a library give its own objects a name: a file
     * handle reports itself as `FILE*` rather than as a bare `userdata`.
     */
    fun objtypename(): String {
        val mt: LuaValue? = getmetatable()
        if (mt != null) {
            val name: LuaValue = mt.rawget(net.blueva.luak.LuaValue.Companion.NAME)
            if (name.type() == net.blueva.luak.LuaValue.Companion.TSTRING) return name.tojstring()
        }
        return typename()!!
    }

    /**
     * Refuses a comparison between two values Lua cannot order.
     *
     * The two type names are given in the order the operands were written,
     * which for `a > b` is the order of the `b < a` the compiler turned it
     * into, and a pair of the same type is said once rather than twice.
     */
    internal fun ordererror(op1: LuaValue, op2: LuaValue): LuaValue? {
        val first: String = op1.objtypename()
        val second: String = op2.objtypename()
        return if (first == second) {
            net.blueva.luak.LuaValue.Companion.error("attempt to compare two " + first + " values")
        } else {
            net.blueva.luak.LuaValue.Companion.error("attempt to compare " + first + " with " + second)
        }
    }

    internal fun checkcallable(tag: LuaValue?, h: LuaValue) {
        if (h.isfunction()) return
        if (!h.metatag(net.blueva.luak.LuaValue.Companion.CALL).isnil()) return
        net.blueva.luak.LuaValue.Companion.error(
            "attempt to call a " + h.objtypename() + " value (metamethod '" +
                tag!!.tojstring().substring(2) + "')",
        )
    }

    /** Perform metatag processing for arithmetic operations when the left-hand-side is a number.
     * 
     * 
     * Finds the supplied metatag value for `this` and invokes it,
     * or throws [LuaError] if neither is defined.
     * @param tag The metatag to look up
     * @param op1 The value of the left-hand-side to perform the operation with
     * @return [LuaValue] resulting from metatag processing
     * @throws LuaError if metatag was not defined for either operand
     * @see .add
     * @see .sub
     * @see .mul
     * @see .pow
     * @see .div
     * @see .mod
     * @see .ADD
     * 
     * @see .SUB
     * 
     * @see .MUL
     * 
     * @see .POW
     * 
     * @see .DIV
     * 
     * @see .MOD
     */
    /**
     * As [arithmtwith], for an integer left-hand side.
     *
     * Kept apart from the float version so the metamethod sees the operand
     * with the subtype it was written with: `5 + t` hands it an integer.
     */
    protected fun arithmtwith(tag: LuaValue?, op1: Long): LuaValue {
        val h = metatag(tag)
        if (h.isnil()) {
            net.blueva.luak.LuaValue.Companion.operandError(
                tag,
                net.blueva.luak.LuaValue.Companion.valueOf(op1),
                this,
            )
        }
        checkcallable(tag, h)
        return h.call(net.blueva.luak.LuaValue.Companion.valueOf(op1), this)!!
    }

    protected fun arithmtwith(tag: LuaValue?, op1: Double): LuaValue {
        val h = metatag(tag)
        if (h.isnil()) {
            net.blueva.luak.LuaValue.Companion.operandError(
                tag,
                net.blueva.luak.LuaValue.Companion.valueOf(op1),
                this,
            )
        }
        checkcallable(tag, h)
        return h.call(net.blueva.luak.LuaValue.Companion.valueOf(op1), this)!!
    }

    /** Less than: Perform numeric or string comparison with another value
     * of unknown type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, both operands must derive from [LuaString]
     * or both must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this < rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if either both operands are not a strings or both are not numbers
     * and no [.LT] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun lt(rhs: LuaValue): LuaValue? {
        return comparemt(net.blueva.luak.LuaValue.Companion.LT, rhs)
    }

    /** Less than: Perform numeric comparison with another value
     * of double type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this < rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if this is not a number
     * and no [.LT] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun lt(rhs: Double): LuaValue? {
        return compareerror("number")
    }

    /** Less than: Perform numeric comparison with another value
     * of int type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this < rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if this is not a number
     * and no [.LT] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun lt(rhs: Long): LuaValue? {
        return compareerror("number")
    }

    /** Less than: Perform numeric or string comparison with another value
     * of unknown type, including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, both operands must derive from [LuaString]
     * or both must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this < rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if either both operands are not a strings or both are not numbers
     * and no [.LT] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun lt_b(rhs: LuaValue): Boolean {
        return comparemt(net.blueva.luak.LuaValue.Companion.LT, rhs)!!.toboolean()
    }

    /** Less than: Perform numeric comparison with another value
     * of int type,
     * including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this < rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if this is not a number
     * and no [.LT] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun lt_b(rhs: Long): Boolean {
        compareerror("number")
        return false
    }

    /** Less than: Perform numeric or string comparison with another value
     * of unknown type, including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, both operands must derive from [LuaString]
     * or both must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this < rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if either both operands are not a strings or both are not numbers
     * and no [.LT] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun lt_b(rhs: Double): Boolean {
        compareerror("number")
        return false
    }

    /** Less than or equals: Perform numeric or string comparison with another value
     * of unknown type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, both operands must derive from [LuaString]
     * or both must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this <= rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if either both operands are not a strings or both are not numbers
     * and no [.LE] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun lteq(rhs: LuaValue): LuaValue? {
        return comparemt(net.blueva.luak.LuaValue.Companion.LE, rhs)
    }

    /** Less than or equals: Perform numeric comparison with another value
     * of double type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this <= rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if this is not a number
     * and no [.LE] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun lteq(rhs: Double): LuaValue? {
        return compareerror("number")
    }

    /** Less than or equals: Perform numeric comparison with another value
     * of int type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this <= rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if this is not a number
     * and no [.LE] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun lteq(rhs: Long): LuaValue? {
        return compareerror("number")
    }

    /** Less than or equals: Perform numeric or string comparison with another value
     * of unknown type, including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, both operands must derive from [LuaString]
     * or both must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this <= rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if either both operands are not a strings or both are not numbers
     * and no [.LE] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun lteq_b(rhs: LuaValue): Boolean {
        return comparemt(net.blueva.luak.LuaValue.Companion.LE, rhs)!!.toboolean()
    }

    /** Less than or equals: Perform numeric comparison with another value
     * of int type,
     * including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this <= rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if this is not a number
     * and no [.LE] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun lteq_b(rhs: Long): Boolean {
        compareerror("number")
        return false
    }

    /** Less than or equals: Perform numeric comparison with another value
     * of double type,
     * including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this <= rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if this is not a number
     * and no [.LE] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun lteq_b(rhs: Double): Boolean {
        compareerror("number")
        return false
    }

    /** Greater than: Perform numeric or string comparison with another value
     * of unknown type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, both operands must derive from [LuaString]
     * or both must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this > rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if either both operands are not a strings or both are not numbers
     * and no [.LE] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun gt(rhs: LuaValue): LuaValue? {
        return rhs.comparemt(net.blueva.luak.LuaValue.Companion.LE, this)
    }

    /** Greater than: Perform numeric comparison with another value
     * of double type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this > rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if this is not a number
     * and no [.LE] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun gt(rhs: Double): LuaValue? {
        return compareerror("number")
    }

    /** Greater than: Perform numeric comparison with another value
     * of int type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this > rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if this is not a number
     * and no [.LE] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun gt(rhs: Long): LuaValue? {
        return compareerror("number")
    }

    /** Greater than: Perform numeric or string comparison with another value
     * of unknown type, including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, both operands must derive from [LuaString]
     * or both must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this > rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if either both operands are not a strings or both are not numbers
     * and no [.LE] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun gt_b(rhs: LuaValue): Boolean {
        return rhs.comparemt(net.blueva.luak.LuaValue.Companion.LE, this)!!.toboolean()
    }

    /** Greater than: Perform numeric comparison with another value
     * of int type,
     * including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this > rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if this is not a number
     * and no [.LE] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun gt_b(rhs: Long): Boolean {
        compareerror("number")
        return false
    }

    /** Greater than: Perform numeric or string comparison with another value
     * of unknown type, including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, both operands must derive from [LuaString]
     * or both must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this > rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if either both operands are not a strings or both are not numbers
     * and no [.LE] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun gt_b(rhs: Double): Boolean {
        compareerror("number")
        return false
    }

    /** Greater than or equals: Perform numeric or string comparison with another value
     * of unknown type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, both operands must derive from [LuaString]
     * or both must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this >= rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if either both operands are not a strings or both are not numbers
     * and no [.LT] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun gteq(rhs: LuaValue): LuaValue? {
        return rhs.comparemt(net.blueva.luak.LuaValue.Companion.LT, this)
    }

    /** Greater than or equals: Perform numeric comparison with another value
     * of double type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this >= rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if this is not a number
     * and no [.LT] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun gteq(rhs: Double): LuaValue? {
        return compareerror("number")
    }

    /** Greater than or equals: Perform numeric comparison with another value
     * of int type,
     * including metatag processing, and returning [LuaValue].
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  [.TRUE] if `(this >= rhs)`, [.FALSE] if not,
     * or [LuaValue] if metatag processing occurs
     * @throws LuaError if this is not a number
     * and no [.LT] metatag is defined.
     * @see .gteq_b
     * @see .comparemt
     */
    open fun gteq(rhs: Long): LuaValue? {
        return net.blueva.luak.LuaValue.Companion.valueOf(todouble() >= rhs)
    }

    /** Greater than or equals: Perform numeric or string comparison with another value
     * of unknown type, including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, both operands must derive from [LuaString]
     * or both must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this >= rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if either both operands are not a strings or both are not numbers
     * and no [.LT] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun gteq_b(rhs: LuaValue): Boolean {
        return rhs.comparemt(net.blueva.luak.LuaValue.Companion.LT, this)!!.toboolean()
    }

    /** Greater than or equals: Perform numeric comparison with another value
     * of int type,
     * including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this >= rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if this is not a number
     * and no [.LT] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun gteq_b(rhs: Long): Boolean {
        compareerror("number")
        return false
    }

    /** Greater than or equals: Perform numeric comparison with another value
     * of double type,
     * including metatag processing,
     * and returning java boolean.
     * 
     * 
     * To be comparable, this must derive from [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  true if `(this >= rhs)`, false if not,
     * and boolean interpreation of result if metatag processing occurs.
     * @throws LuaError if this is not a number
     * and no [.LT] metatag is defined.
     * @see .gteq
     * @see .comparemt
     */
    open fun gteq_b(rhs: Double): Boolean {
        compareerror("number")
        return false
    }

    /** Perform metatag processing for comparison operations.
     * 
     * 
     * Finds the supplied metatag value and invokes it,
     * or throws [LuaError] if none applies.
     * @param tag The metatag to look up
     * @param op1 The operand with which to to perform the operation
     * @return [LuaValue] resulting from metatag processing
     * @throws LuaError if metatag was not defined for either operand,
     * or if the operands are not the same type,
     * or the metatag values for the two operands are different.
     * @see .gt
     * @see .gteq
     * @see .lt
     * @see .lteq
     */
    fun comparemt(tag: LuaValue?, op1: LuaValue): LuaValue? {
        var h: LuaValue?
        if (!(metatag(tag).also { h = it }).isnil() || !(op1.metatag(tag)
                .also { h = it }).isnil()
        ) {
            checkcallable(tag, h!!)
            return h!!.call(this, op1)
        }
        if (net.blueva.luak.LuaValue.Companion.LE.raweq(tag) && (!(metatag(net.blueva.luak.LuaValue.Companion.LT).also {
                h = it
            }).isnil() || !(op1.metatag(net.blueva.luak.LuaValue.Companion.LT)
                .also { h = it }).isnil())
        ) {
            checkcallable(net.blueva.luak.LuaValue.Companion.LT, h!!)
            return h!!.call(op1, this)!!.not()
        }
        return ordererror(this, op1)
    }

    /** Perform string comparison with another value
     * of any type
     * using string comparison based on byte values.
     * 
     * 
     * Only strings can be compared, meaning
     * each operand must derive from [LuaString].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  int < 0 for `(this < rhs)`, int > 0 for `(this > rhs)`, or 0 when same string.
     * @throws LuaError if either operand is not a string
     */
    open fun strcmp(rhs: LuaValue?): Int {
        net.blueva.luak.LuaValue.Companion.error("attempt to compare " + typename())
        return 0
    }

    /** Perform string comparison with another value
     * known to be a [LuaString]
     * using string comparison based on byte values.
     * 
     * 
     * Only strings can be compared, meaning
     * each operand must derive from [LuaString].
     * 
     * @param rhs The right-hand-side value to perform the comparison with
     * @return  int < 0 for `(this < rhs)`, int > 0 for `(this > rhs)`, or 0 when same string.
     * @throws LuaError if this is not a string
     */
    open fun strcmp(rhs: LuaString?): Int {
        net.blueva.luak.LuaValue.Companion.error("attempt to compare " + typename())
        return 0
    }

    /** Concatenate another value onto this value and return the result
     * using rules of lua string concatenation including metatag processing.
     * 
     * 
     * Only strings and numbers as represented can be concatenated, meaning
     * each operand must derive from [LuaString] or [LuaNumber].
     * 
     * @param rhs The right-hand-side value to perform the operation with
     * @return  [LuaValue] resulting from concatenation of `(this .. rhs)`
     * @throws LuaError if either operand is not of an appropriate type,
     * such as nil or a table
     */
    open fun concat(rhs: LuaValue): LuaValue {
        return this.concatmt(rhs)
    }

    /** Reverse-concatenation: concatenate this value onto another value
     * whose type is unknwon
     * and return the result using rules of lua string concatenation including
     * metatag processing.
     * 
     * 
     * Only strings and numbers as represented can be concatenated, meaning
     * each operand must derive from [LuaString] or [LuaNumber].
     * 
     * @param lhs The left-hand-side value onto which this will be concatenated
     * @return  [LuaValue] resulting from concatenation of `(lhs .. this)`
     * @throws LuaError if either operand is not of an appropriate type,
     * such as nil or a table
     * @see .concat
     */
    open fun concatTo(lhs: LuaValue): LuaValue {
        return lhs.concatmt(this)
    }

    /** Reverse-concatenation: concatenate this value onto another value
     * known to be a [LuaNumber]
     * and return the result using rules of lua string concatenation including
     * metatag processing.
     * 
     * 
     * Only strings and numbers as represented can be concatenated, meaning
     * each operand must derive from [LuaString] or [LuaNumber].
     * 
     * @param lhs The left-hand-side value onto which this will be concatenated
     * @return  [LuaValue] resulting from concatenation of `(lhs .. this)`
     * @throws LuaError if either operand is not of an appropriate type,
     * such as nil or a table
     * @see .concat
     */
    open fun concatTo(lhs: LuaNumber): LuaValue {
        return lhs.concatmt(this)
    }

    /** Reverse-concatenation: concatenate this value onto another value
     * known to be a [LuaString]
     * and return the result using rules of lua string concatenation including
     * metatag processing.
     * 
     * 
     * Only strings and numbers as represented can be concatenated, meaning
     * each operand must derive from [LuaString] or [LuaNumber].
     * 
     * @param lhs The left-hand-side value onto which this will be concatenated
     * @return  [LuaValue] resulting from concatenation of `(lhs .. this)`
     * @throws LuaError if either operand is not of an appropriate type,
     * such as nil or a table
     * @see .concat
     */
    open fun concatTo(lhs: LuaString): LuaValue {
        return lhs.concatmt(this)
    }

    /** Convert the value to a [Buffer] for more efficient concatenation of
     * multiple strings.
     * @return Buffer instance containing the string or number
     */
    fun buffer(): Buffer? {
        return Buffer(this)
    }

    /** Concatenate a [Buffer] onto this value and return the result
     * using rules of lua string concatenation including metatag processing.
     * 
     * 
     * Only strings and numbers as represented can be concatenated, meaning
     * each operand must derive from [LuaString] or [LuaNumber].
     * 
     * @param rhs The right-hand-side [Buffer] to perform the operation with
     * @return LuaString resulting from concatenation of `(this .. rhs)`
     * @throws LuaError if either operand is not of an appropriate type,
     * such as nil or a table
     */
    open fun concat(rhs: Buffer): Buffer {
        return rhs.concatTo(this)
    }

    /** Perform metatag processing for concatenation operations.
     * 
     * 
     * Finds the [.CONCAT] metatag value and invokes it,
     * or throws [LuaError] if it doesn't exist.
     * @param rhs The right-hand-side value to perform the operation with
     * @return [LuaValue] resulting from metatag processing for [.CONCAT] metatag.
     * @throws LuaError if metatag was not defined for either operand
     */
    fun concatmt(rhs: LuaValue): LuaValue {
        var h = metatag(net.blueva.luak.LuaValue.Companion.CONCAT)
        if (h.isnil() && (rhs.metatag(net.blueva.luak.LuaValue.Companion.CONCAT)
                .also { h = it }).isnil()
        ) {
            // Blame the operand that is not concatenable, the way Lua does,
            // rather than naming both.
            val culprit: LuaValue = if (!this.isstring() || this is LuaTable) this else rhs
            net.blueva.luak.LuaValue.Companion.error(
                "attempt to concatenate a " + culprit.objtypename() + " value",
            )
        }
        checkcallable(net.blueva.luak.LuaValue.Companion.CONCAT, h)
        return h.call(this, rhs)!!
    }

    /** Perform boolean `and` with another operand, based on lua rules for boolean evaluation.
     * This returns either `this` or `rhs` depending on the boolean value for `this`.
     * 
     * @param rhs The right-hand-side value to perform the operation with
     * @return `this` if `this.toboolean()` is false, `rhs` otherwise.
     */
    fun and(rhs: LuaValue?): LuaValue? {
        return if (this.toboolean()) rhs else this
    }

    /** Perform boolean `or` with another operand, based on lua rules for boolean evaluation.
     * This returns either `this` or `rhs` depending on the boolean value for `this`.
     * 
     * @param rhs The right-hand-side value to perform the operation with
     * @return `this` if `this.toboolean()` is true, `rhs` otherwise.
     */
    fun or(rhs: LuaValue?): LuaValue? {
        return if (this.toboolean()) this else rhs
    }

    /** Perform end-condition test in for-loop processing.
     * 
     * 
     * Used in lua-bytecode to Java-bytecode conversion.
     * 
     * @param limit the numerical limit to complete the for loop
     * @param step the numberical step size to use.
     * @return true if limit has not been reached, false otherwise.
     */
    fun testfor_b(limit: LuaValue, step: LuaValue): Boolean {
        return if (step.gt_b(0)) lteq_b(limit) else gteq_b(limit)
    }

    /**
     * Convert this value to a string if it is a [LuaString] or [LuaNumber],
     * or throw a [LuaError] if it is not
     * @return [LuaString] corresponding to the value if a string or number
     * @throws LuaError if not a string or number
     */
    open fun strvalue(): LuaString? {
        typerror("string or number")
        return null
    }

    /** Return this value as a strong reference, or null if it was weak and is no longer referenced.
     * @return [LuaValue] referred to, or null if it was weak and is no longer referenced.
     * @see WeakTable
     */
    open fun strongvalue(): LuaValue? {
        return this
    }

    /**
     * Get particular metatag, or return [NIL] if it doesn't exist
     * @param tag Metatag name to look up, typically a string such as
     * [INDEX] or [NEWINDEX]
     * @return [LuaValue] for tag `reason`, or  [NIL]
     */
    fun metatag(tag: LuaValue?): LuaValue {
        val mt = getmetatable()
        if (mt == null) return net.blueva.luak.LuaValue.Companion.NIL
        return mt.rawget(tag)
    }

    /**
     * Get particular metatag, or throw [LuaError] if it doesn't exist
     * @param tag Metatag name to look up, typically a string such as
     * [INDEX] or [NEWINDEX]
     * @param reason Description of error when tag lookup fails.
     * @return [LuaValue] that can be called
     * @throws LuaError when the lookup fails.
     */
    protected fun checkmetatag(tag: LuaValue?, reason: String?): LuaValue {
        val h = this.metatag(tag)
        if (h.isnil()) throw LuaError(reason.toString() + "a " + objtypename() + " value")
        return h
    }

    /** Throw [LuaError] indicating index was attempted on illegal type
     * @throws LuaError when called.
     */
    /**
     * Reports indexing something that cannot be indexed.
     *
     * The key is not named here: Lua names where the *value* came from, which
     * only the interpreter can work out, and it adds that afterwards.
     */
    private fun indexerror(key: String?) {
        net.blueva.luak.LuaValue.Companion.error("attempt to index a " + objtypename() + " value")
    }

    /**
     * Callback used during tail call processing to invoke the function once.
     * 
     * 
     * This may return a [TailcallVarargs] to be evaluated by the client.
     * 
     * 
     * This should not be called directly, instead use one of the call invocation functions.
     * 
     * @param args the arguments to the call invocation.
     * @return Varargs the return values, possible a TailcallVarargs.
     * @see call
     * @see invoke
     * @see method
     * @see invokemethod
     */
    open fun onInvoke(args: Varargs): Varargs? {
        return invoke(args)
    }

    /** Suspending counterpart to [.onInvoke], used by [TailcallVarargs.evalSuspend]
     * to trampoline tail calls without losing yield propagation. */
    open suspend fun onInvokeSuspend(args: Varargs): Varargs? {
        return invokeSuspend(args)
    }

    /** Hook for implementations such as LuaJC to load the environment of the main chunk
     * into the first upvalue location.  If the function has no upvalues or is not a main chunk,
     * calling this will be no effect.
     * @param env  The environment to load into the first upvalue, if there is one.
     */
    open fun initupvalue1(env: LuaValue?) {}

    /** Varargs implemenation with no values.
     * 
     * 
     * This is an internal class not intended to be used directly.
     * Instead use the predefined constant [NONE]
     * 
     * @see NONE
     */
    private class None : LuaNil() {
        override fun arg(i: Int): LuaValue {
            return net.blueva.luak.LuaValue.Companion.NIL
        }

        override fun narg(): Int {
            return 0
        }

        override fun arg1(): LuaValue {
            return net.blueva.luak.LuaValue.Companion.NIL
        }

        override fun tojstring(): String {
            return "none"
        }

        override fun subargs(start: Int): Varargs? {
            return if (start > 0) this else net.blueva.luak.LuaValue.Companion.argerror(1, "start must be > 0")
        }

        override fun copyto(dest: Array<LuaValue?>, offset: Int, length: Int) {
            var offset = offset
            var length = length
            while (length > 0) {
                dest[offset++] = net.blueva.luak.LuaValue.Companion.NIL
                length--
            }
        }

        companion object {
            var _NONE: None = net.blueva.luak.LuaValue.None()
        }
    }

    /**
     * Create a `Varargs` instance containing arguments starting at index `start`
     * @param start the index from which to include arguments, where 1 is the first argument.
     * @return Varargs containing argument { start, start+1,  ... , narg-start-1 }
     */
    override fun subargs(start: Int): Varargs? {
        if (start == 1) return this
        if (start > 1) return net.blueva.luak.LuaValue.Companion.NONE
        return net.blueva.luak.LuaValue.Companion.argerror(1, "start must be > 0")
    }

    companion object {
        /** Type enumeration constant for lua numbers that are ints, for compatibility with lua 5.1 number patch only  */
        val TINT: Int = (-2)

        /** Type enumeration constant for lua values that have no type, for example weak table entries  */
        val TNONE: Int = (-1)

        /** Type enumeration constant for lua nil  */
        const val TNIL: Int = 0

        /** Type enumeration constant for lua booleans  */
        const val TBOOLEAN: Int = 1

        /** Type enumeration constant for lua light userdata, for compatibility with C-based lua only  */
        const val TLIGHTUSERDATA: Int = 2

        /** Type enumeration constant for lua numbers  */
        const val TNUMBER: Int = 3

        /** Type enumeration constant for lua strings  */
        const val TSTRING: Int = 4

        /** Type enumeration constant for lua tables  */
        const val TTABLE: Int = 5

        /** Type enumeration constant for lua functions  */
        const val TFUNCTION: Int = 6

        /** Type enumeration constant for lua userdatas  */
        const val TUSERDATA: Int = 7

        /** Type enumeration constant for lua threads  */
        const val TTHREAD: Int = 8

        /** Type enumeration constant for unknown values, for compatibility with C-based lua only  */
        const val TVALUE: Int = 9

        /** String array constant containing names of each of the lua value types
         * @see .type
         * @see .typename
         */
        val TYPE_NAMES: Array<String?> = arrayOf<String?>(
            "nil",
            "boolean",
            "lightuserdata",
            "number",
            "string",
            "table",
            "function",
            "userdata",
            "thread",
            "value",
        )

        /** LuaValue constant corresponding to lua `#NIL`  */
        @kotlin.jvm.JvmField
        val NIL: LuaValue = LuaNil._NIL

        /** LuaBoolean constant corresponding to lua `true`  */
        @kotlin.jvm.JvmField
        val TRUE: LuaBoolean = LuaBoolean._TRUE

        /** LuaBoolean constant corresponding to lua `false`  */
        @kotlin.jvm.JvmField
        val FALSE: LuaBoolean = LuaBoolean._FALSE

        /** LuaValue constant corresponding to a [Varargs] list of no values  */
        @kotlin.jvm.JvmField
        val NONE: LuaValue? = net.blueva.luak.LuaValue.None.Companion._NONE

        /** LuaValue number constant equal to 0  */
        @kotlin.jvm.JvmField
        val ZERO: LuaNumber? = LuaInteger.valueOf(0)

        /** LuaValue number constant equal to 1  */
        @kotlin.jvm.JvmField
        val ONE: LuaNumber? = LuaInteger.valueOf(1)

        /** LuaValue number constant equal to -1  */
        @kotlin.jvm.JvmField
        val MINUSONE: LuaNumber? = LuaInteger.valueOf(-1)

        /** LuaValue array constant with no values  */
        @kotlin.jvm.JvmField
        val NOVALS: Array<LuaValue?> = arrayOf<LuaValue?>()

        /** The variable name of the environment.  */
        val ENV: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("_ENV")

        /** LuaString constant with value "__index" for use as metatag  */
        val INDEX: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__index")

        /** LuaString constant with value "__newindex" for use as metatag  */
        val NEWINDEX: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__newindex")

        /** LuaString constant with value "__call" for use as metatag  */
        val CALL: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__call")

        /** LuaString constant with value "__mode" for use as metatag  */
        val MODE: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__mode")

        /** LuaString constant with value "__metatable" for use as metatag  */
        val METATABLE: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__metatable")

        /** LuaString constant with value "__add" for use as metatag  */
        val ADD: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__add")

        /** LuaString constant with value "__sub" for use as metatag  */
        val SUB: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__sub")

        /** LuaString constant with value "__div" for use as metatag  */
        val DIV: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__div")

        /** LuaString constant with value "__idiv" for use as metatag  */
        val IDIV: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__idiv")


        /** LuaString constant with value "__band" for use as metatag  */
        val BAND: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__band")

        /** LuaString constant with value "__bor" for use as metatag  */
        val BOR: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__bor")

        /** LuaString constant with value "__bxor" for use as metatag  */
        val BXOR: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__bxor")

        /** LuaString constant with value "__shl" for use as metatag  */
        val SHL: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__shl")

        /** LuaString constant with value "__shr" for use as metatag  */
        val SHR: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__shr")

        /** LuaString constant with value "__bnot" for use as metatag  */
        val BNOT: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__bnot")

        /** LuaString constant with value "__mul" for use as metatag  */
        val MUL: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__mul")

        /** LuaString constant with value "__pow" for use as metatag  */
        val POW: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__pow")

        /** LuaString constant with value "__mod" for use as metatag  */
        val MOD: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__mod")

        /** LuaString constant with value "__unm" for use as metatag  */
        val UNM: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__unm")

        /* see gckeeper */

        /** LuaString constant with value "__gc" for use as metatag  */
        val GC: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__gc")

        /** LuaString constant with value "__close" for use as metatag  */
        val CLOSE: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__close")

        /** LuaString constant with value "__pairs" for use as metatag  */
        val PAIRS: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__pairs")

        /** LuaString constant with value "__name" for use as metatag  */
        val NAME: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__name")

        /** LuaString constant with value "__len" for use as metatag  */
        val LEN: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__len")

        /** LuaString constant with value "__eq" for use as metatag  */
        val EQ: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__eq")

        /** LuaString constant with value "__lt" for use as metatag  */
        val LT: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__lt")

        /** LuaString constant with value "__le" for use as metatag  */
        val LE: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__le")

        /** LuaString constant with value "__tostring" for use as metatag  */
        val TOSTRING: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__tostring")

        /** LuaString constant with value "__concat" for use as metatag  */
        val CONCAT: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("__concat")

        /** LuaString constant with value ""  */
        val EMPTYSTRING: LuaString
            get() = net.blueva.luak.LuaValue.Companion.valueOf("")

        /** Limit on lua stack size  */
        private const val MAXSTACK = 250

        /** Array of [.NIL] values to optimize filling stacks using arrayCopy().
         * Must not be modified.
         */
        val NILS: Array<LuaValue?> = arrayOfNulls<LuaValue>(net.blueva.luak.LuaValue.Companion.MAXSTACK)

        init {
            for (i in 0..<net.blueva.luak.LuaValue.Companion.MAXSTACK) net.blueva.luak.LuaValue.Companion.NILS[i] =
                net.blueva.luak.LuaValue.Companion.NIL
        }

        /**
         * Throw a [LuaError] with a particular message
         * @param message String providing message details
         * @throws LuaError in all cases
         */
        fun error(message: String?): LuaValue? {
            throw LuaError(message)
        }

        /**
         * Assert a condition is true, or throw a [LuaError] if not
         * Returns no value when b is true, throws [.error] with `msg` as argument
         * and does not return if b is false.
         * @param b condition to test
         * @param msg String message to produce on failure
         * @throws LuaError if b is not true
         */
        fun assert_(b: Boolean, msg: String?) {
            if (!b) throw LuaError(msg)
        }

        /**
         * Throw a [LuaError] indicating an invalid argument was supplied to a function
         * @param iarg index of the argument that was invalid, first index is 1
         * @param msg String providing information about the invalid argument
         * @throws LuaError in all cases
         */
        /**
         * Reports an operator applied to something it cannot work on.
         *
         * The blame goes to the first operand that is not a number, which is
         * the one the reader needs to know about; a bitwise operator says so
         * rather than calling itself arithmetic.
         */
        fun operandError(tag: LuaValue?, op1: LuaValue, op2: LuaValue): Nothing {
            val bitwise: Boolean = tag != null && (
                tag == net.blueva.luak.LuaValue.Companion.BAND ||
                    tag == net.blueva.luak.LuaValue.Companion.BOR ||
                    tag == net.blueva.luak.LuaValue.Companion.BXOR ||
                    tag == net.blueva.luak.LuaValue.Companion.SHL ||
                    tag == net.blueva.luak.LuaValue.Companion.SHR ||
                    tag == net.blueva.luak.LuaValue.Companion.BNOT
                )
            val what: String = if (bitwise) "perform bitwise operation on" else "perform arithmetic on"
            val culprit: LuaValue =
                if (op1.type() != net.blueva.luak.LuaValue.Companion.TNUMBER) op1 else op2
            net.blueva.luak.LuaValue.Companion.error(
                "attempt to " + what + " a " + culprit.objtypename() + " value",
            )
            throw IllegalStateException()
        }

        fun argerror(iarg: Int, msg: String?): LuaValue? {
            throw LuaError("bad argument #" + iarg + ": " + msg)
        }

        /**
         * Runs `__eq` for two values raw equality has already turned down.
         *
         * The handler is the left operand's, or the right one's when the left
         * has none: the two metatables no longer have to agree on it, as they
         * did before Lua 5.3.
         *
         * @return true if the handler returned anything other than nil or false
         * @see .equals
         * @see .eq
         * @see .raweq
         * @see .EQ
         */
        fun eqmtcall(lhs: LuaValue, rhs: LuaValue): Boolean {
            var h: LuaValue = lhs.metatag(net.blueva.luak.LuaValue.Companion.EQ)
            if (h.isnil()) h = rhs.metatag(net.blueva.luak.LuaValue.Companion.EQ)
            if (h.isnil()) return false
            h.checkcallable(net.blueva.luak.LuaValue.Companion.EQ, h)
            return h.call(lhs, rhs)!!.toboolean()
        }

        /** Convert java boolean to a [LuaValue].
         * 
         * @param b boolean value to convert
         * @return [.TRUE] if not  or [.FALSE] if false
         */
                @kotlin.jvm.JvmStatic
                fun valueOf(b: Boolean): LuaBoolean? {
            return if (b) net.blueva.luak.LuaValue.Companion.TRUE else net.blueva.luak.LuaValue.Companion.FALSE
        }

        /** Convert java int to a [LuaValue].
         * 
         * @param i int value to convert
         * @return [LuaInteger] instance, possibly pooled, whose value is i
         */
        @kotlin.jvm.JvmStatic
        fun valueOf(i: Int): LuaInteger {
            return (LuaInteger.valueOf(i))!!
        }

        /** Convert a long to a [LuaValue].
         *
         * Lua's integer subtype is 64 bits wide, so every long is representable
         * exactly and this never yields a float.
         *
         * @param l long value to convert
         * @return [LuaInteger] instance, possibly pooled, whose value is l
         */
        @kotlin.jvm.JvmStatic
        fun valueOf(l: Long): LuaInteger {
            return (LuaInteger.valueOf(l))!!
        }

        /** Convert java double to a [LuaValue].
         * This may return a [LuaInteger] or [LuaDouble] depending
         * on the value supplied.
         * 
         * @param d double value to convert
         * @return [LuaNumber] instance, possibly pooled, whose value is d
         */
        @kotlin.jvm.JvmStatic
        fun valueOf(d: Double): LuaNumber {
            return (LuaDouble.valueOf(d))!!
        }

        /** Convert java string to a [LuaValue].
         * 
         * @param s String value to convert
         * @return [LuaString] instance, possibly pooled, whose value is s
         */
        @kotlin.jvm.JvmStatic
        fun valueOf(s: String?): LuaString {
            return LuaString.valueOf((s)!!)
        }

        /** Convert bytes in an array to a [LuaValue].
         * 
         * @param bytes byte array to convert
         * @return [LuaString] instance, possibly pooled, whose bytes are those in the supplied array
         */
        @kotlin.jvm.JvmStatic
        fun valueOf(bytes: ByteArray?): LuaString {
            return LuaString.valueOf((bytes)!!)
        }

        /** Convert bytes in an array to a [LuaValue].
         * 
         * @param bytes byte array to convert
         * @param off offset into the byte array, starting at 0
         * @param len number of bytes to include in the [LuaString]
         * @return [LuaString] instance, possibly pooled, whose bytes are those in the supplied array
         */
        @kotlin.jvm.JvmStatic
        fun valueOf(bytes: ByteArray?, off: Int, len: Int): LuaString {
            return LuaString.valueOf((bytes)!!, off, len)
        }

        /** Construct an empty [LuaTable].
         * @return new [LuaTable] instance with no values and no metatable.
         */
        @kotlin.jvm.JvmStatic
        fun tableOf(): LuaTable {
            return LuaTable()
        }

        /** Construct a [LuaTable] initialized with supplied array values.
         * @param varargs [Varargs] containing the values to use in initialization
         * @param firstarg the index of the first argument to use from the varargs, 1 being the first.
         * @return new [LuaTable] instance with sequential elements coming from the varargs.
         */
        @kotlin.jvm.JvmStatic
        fun tableOf(varargs: Varargs?, firstarg: Int): LuaTable {
            return LuaTable((varargs)!!, firstarg)
        }

        /** Construct an empty [LuaTable] preallocated to hold array and hashed elements
         * @param narray Number of array elements to preallocate
         * @param nhash Number of hash elements to preallocate
         * @return new [LuaTable] instance with no values and no metatable, but preallocated for array and hashed elements.
         */
        @kotlin.jvm.JvmStatic
        fun tableOf(narray: Int, nhash: Int): LuaTable {
            return LuaTable(narray, nhash)
        }

        /** Construct a [LuaTable] initialized with supplied array values.
         * @param unnamedValues array of [LuaValue] containing the values to use in initialization
         * @return new [LuaTable] instance with sequential elements coming from the array.
         */
        fun listOf(unnamedValues: Array<out LuaValue?>?): LuaTable {
            return LuaTable(null, unnamedValues, null)
        }

        /** Construct a [LuaTable] initialized with supplied array values.
         * @param unnamedValues array of [LuaValue] containing the first values to use in initialization
         * @param lastarg [Varargs] containing additional values to use in initialization
         * to be put after the last unnamedValues element
         * @return new [LuaTable] instance with sequential elements coming from the array and varargs.
         */
        fun listOf(unnamedValues: Array<out LuaValue?>?, lastarg: Varargs?): LuaTable {
            return LuaTable(null, unnamedValues, lastarg)
        }

        /** Construct a [LuaTable] initialized with supplied named values.
         * @param namedValues array of [LuaValue] containing the keys and values to use in initialization
         * in order `{key-a, value-a, key-b, value-b, ...} `
         * @return new [LuaTable] instance with non-sequential keys coming from the supplied array.
         */
        @kotlin.jvm.JvmStatic
        fun tableOf(namedValues: Array<out LuaValue?>?): LuaTable {
            return LuaTable(namedValues, null, null)
        }

        /** Construct a [LuaTable] initialized with supplied named values and sequential elements.
         * The named values will be assigned first, and the sequential elements will be assigned later,
         * possibly overwriting named values at the same slot if there are conflicts.
         * @param namedValues array of [LuaValue] containing the keys and values to use in initialization
         * in order `{key-a, value-a, key-b, value-b, ...} `
         * @param unnamedValues array of [LuaValue] containing the sequenctial elements to use in initialization
         * in order `{value-1, value-2, ...} `, or null if there are none
         * @return new [LuaTable] instance with named and sequential values supplied.
         */
        @kotlin.jvm.JvmStatic
        fun tableOf(namedValues: Array<out LuaValue?>?, unnamedValues: Array<out LuaValue?>?): LuaTable {
            return LuaTable(namedValues, unnamedValues, null)
        }

        /** Construct a [LuaTable] initialized with supplied named values and sequential elements in an array part and as varargs.
         * The named values will be assigned first, and the sequential elements will be assigned later,
         * possibly overwriting named values at the same slot if there are conflicts.
         * @param namedValues array of [LuaValue] containing the keys and values to use in initialization
         * in order `{key-a, value-a, key-b, value-b, ...} `
         * @param unnamedValues array of [LuaValue] containing the first sequenctial elements to use in initialization
         * in order `{value-1, value-2, ...} `, or null if there are none
         * @param lastarg [Varargs] containing additional values to use in the sequential part of the initialization,
         * to be put after the last unnamedValues element
         * @return new [LuaTable] instance with named and sequential values supplied.
         */
        @kotlin.jvm.JvmStatic
        fun tableOf(namedValues: Array<out LuaValue?>?, unnamedValues: Array<out LuaValue?>?, lastarg: Varargs?): LuaTable {
            return LuaTable(namedValues, unnamedValues, lastarg)
        }

        /** Construct a LuaUserdata for an object.
         * 
         * @param o The java instance to be wrapped as userdata
         * @return [LuaUserdata] value wrapping the java instance.
         */
        fun userdataOf(o: Any?): LuaUserdata {
            return LuaUserdata((o)!!)
        }

        /** Construct a LuaUserdata for an object with a user supplied metatable.
         * 
         * @param o The java instance to be wrapped as userdata
         * @param metatable The metatble to associate with the userdata instance.
         * @return [LuaUserdata] value wrapping the java instance.
         */
        fun userdataOf(o: Any?, metatable: LuaValue?): LuaUserdata {
            return LuaUserdata((o)!!, metatable)
        }

        /** Constant limiting metatag loop processing  */
        internal const val MAXTAGLOOP = 100

        /** As many `__call` handlers as Lua follows before refusing the chain. */
        const val MAX_CALL_CHAIN: Int = 15

        /**
         * Return value for field reference including metatag processing, or [NIL] if it doesn't exist.
         * @param t [LuaValue] on which field is being referenced, typically a table or something with the metatag [INDEX] defined
         * @param key [LuaValue] naming the field to reference
         * @return [LuaValue] for the `key` if it exists, or [NIL]
         * @throws LuaError if there is a loop in metatag processing
         */
        /** get value from metatable operations, or NIL if not defined by metatables  */
        internal fun gettable(t: LuaValue, key: LuaValue): LuaValue {
            var t = t
            var tm: LuaValue? = null
            var loop = 0
            do {
                if (t.istable()) {
                    val res = t.rawget(key)
                    if ((!res.isnil()) || (t.metatag(net.blueva.luak.LuaValue.Companion.INDEX)
                            .also { tm = it }).isnil()
                    ) return res
                } else if ((t.metatag(net.blueva.luak.LuaValue.Companion.INDEX).also { tm = it }).isnil()) t.indexerror(
                    key.tojstring()
                )
                if (tm!!.isfunction()) return tm.call(t, key)!!
                t = tm
            } while (++loop < net.blueva.luak.LuaValue.Companion.MAXTAGLOOP)
            net.blueva.luak.LuaValue.Companion.error("loop in gettable")
            return net.blueva.luak.LuaValue.Companion.NIL
        }

        /**
         * Perform field assignment including metatag processing.
         * @param t [LuaValue] on which value is being set, typically a table or something with the metatag [NEWINDEX] defined
         * @param key [LuaValue] naming the field to assign
         * @param value [LuaValue] the new value to assign to `key`
         * @throws LuaError if there is a loop in metatag processing
         * @return true if assignment or metatag processing succeeded, false otherwise
         */
        fun settable(t: LuaValue, key: LuaValue?, value: LuaValue?): Boolean {
            var t = t
            var tm: LuaValue? = null
            var loop = 0
            do {
                if (t.istable()) {
                    if ((!t.rawget(key).isnil()) || (t.metatag(net.blueva.luak.LuaValue.Companion.NEWINDEX)
                            .also { tm = it }).isnil()
                    ) {
                        t.rawset(key, value)
                        return true
                    }
                } else if ((t.metatag(net.blueva.luak.LuaValue.Companion.NEWINDEX)
                        .also { tm = it }).isnil()
                ) throw LuaError("attempt to index a " + t.objtypename() + " value")
                if (tm!!.isfunction()) {
                    tm.call(t, key, value)
                    return true
                }
                t = tm
            } while (++loop < net.blueva.luak.LuaValue.Companion.MAXTAGLOOP)
            net.blueva.luak.LuaValue.Companion.error("loop in settable")
            return false
        }

        /** Construct a Metatable instance from the given LuaValue  */
        internal fun metatableOf(mt: LuaValue?): Metatable? {
            if (mt != null && mt.istable()) {
                val mode: LuaValue = mt.rawget(net.blueva.luak.LuaValue.Companion.MODE)
                if (mode.isstring()) {
                    val m = mode.tojstring()
                    val weakkeys = m.indexOf('k') >= 0
                    val weakvalues = m.indexOf('v') >= 0
                    if (weakkeys || weakvalues) {
                        return WeakTable(weakkeys, weakvalues, mt)
                    }
                }
                return mt as LuaTable
            } else if (mt != null) {
                return NonTableMetatable(mt)
            } else {
                return null
            }
        }

        /** Construct a [Varargs] around an array of [LuaValue]s.
         * 
         * @param v The array of [LuaValue]s
         * @return [Varargs] wrapping the supplied values.
         * @see LuaValue.varargsOf
         * @see LuaValue.varargsOf
         */
        @kotlin.jvm.JvmStatic
        fun varargsOf(v: Array<out LuaValue?>): Varargs {
            when (v.size) {
                0 -> return net.blueva.luak.LuaValue.Companion.NONE!!
                1 -> return v[0]!!
                2 -> return PairVarargs(v[0], (v[1])!!)
                else -> return ArrayVarargs(v, (net.blueva.luak.LuaValue.Companion.NONE)!!)
            }
        }

        /** Construct a [Varargs] around an array of [LuaValue]s.
         * 
         * @param v The array of [LuaValue]s
         * @param r [Varargs] contain values to include at the end
         * @return [Varargs] wrapping the supplied values.
         * @see LuaValue.varargsOf
         * @see LuaValue.varargsOf
         */
        @kotlin.jvm.JvmStatic
        fun varargsOf(v: Array<out LuaValue?>, r: Varargs): Varargs {
            when (v.size) {
                0 -> return r
                1 -> return if (r.narg() > 0) PairVarargs(v[0], r) else v[0]!!
                2 -> return if (r.narg() > 0) ArrayVarargs(v, r) as Varargs else PairVarargs(v[0], (v[1])!!) as Varargs
                else -> return ArrayVarargs(v, r)
            }
        }

        /** Construct a [Varargs] around an array of [LuaValue]s.
         * 
         * @param v The array of [LuaValue]s
         * @param offset number of initial values to skip in the array
         * @param length number of values to include from the array
         * @return [Varargs] wrapping the supplied values.
         * @see LuaValue.varargsOf
         * @see LuaValue.varargsOf
         */
        @kotlin.jvm.JvmStatic
        fun varargsOf(v: Array<out LuaValue?>, offset: Int, length: Int): Varargs {
            when (length) {
                0 -> return net.blueva.luak.LuaValue.Companion.NONE!!
                1 -> return v[offset]!!
                2 -> return PairVarargs(v[offset + 0], (v[offset + 1])!!)
                else -> return ArrayPartVarargs(v, offset, length, (net.blueva.luak.LuaValue.Companion.NONE)!!)
            }
        }

        /** Construct a [Varargs] around an array of [LuaValue]s.
         * 
         * Caller must ensure that array contents are not mutated after this call
         * or undefined behavior will result.
         * 
         * @param v The array of [LuaValue]s
         * @param offset number of initial values to skip in the array
         * @param length number of values to include from the array
         * @param more [Varargs] contain values to include at the end
         * @return [Varargs] wrapping the supplied values.
         * @see LuaValue.varargsOf
         * @see LuaValue.varargsOf
         */
        @kotlin.jvm.JvmStatic
        fun varargsOf(v: Array<out LuaValue?>, offset: Int, length: Int, more: Varargs): Varargs {
            when (length) {
                0 -> return more
                1 -> return if (more.narg() > 0) PairVarargs(v[offset], more) else v[offset]!!
                2 -> return if (more.narg() > 0) ArrayPartVarargs(
                    v,
                    offset,
                    length,
                    more
                ) as Varargs else PairVarargs(v[offset], (v[offset + 1])!!) as Varargs

                else -> return ArrayPartVarargs(v, offset, length, more)
            }
        }

        /** Construct a [Varargs] around a set of 2 or more [LuaValue]s.
         * 
         * 
         * This can be used to wrap exactly 2 values, or a list consisting of 1 initial value
         * followed by another variable list of remaining values.
         * 
         * @param v First [LuaValue] in the [Varargs]
         * @param r [LuaValue] supplying the 2rd value,
         * or [Varargs]s supplying all values beyond the first
         * @return [Varargs] wrapping the supplied values.
         */
        @kotlin.jvm.JvmStatic
        fun varargsOf(v: LuaValue?, r: Varargs): Varargs {
            when (r.narg()) {
                0 -> return v!!
                else -> return PairVarargs(v, r)
            }
        }

        /** Construct a [Varargs] around a set of 3 or more [LuaValue]s.
         * 
         * 
         * This can be used to wrap exactly 3 values, or a list consisting of 2 initial values
         * followed by another variable list of remaining values.
         * 
         * @param v1 First [LuaValue] in the [Varargs]
         * @param v2 Second [LuaValue] in the [Varargs]
         * @param v3 [LuaValue] supplying the 3rd value,
         * or [Varargs]s supplying all values beyond the second
         * @return [Varargs] wrapping the supplied values.
         */
        @kotlin.jvm.JvmStatic
        fun varargsOf(v1: LuaValue?, v2: LuaValue?, v3: Varargs): Varargs {
            when (v3.narg()) {
                0 -> return PairVarargs(v1, (v2)!!)
                else -> return ArrayPartVarargs(arrayOf<LuaValue?>(v1, v2), 0, 2, v3)
            }
        }

        /** Construct a [TailcallVarargs] around a function and arguments.
         * 
         * 
         * The tail call is not yet called or processing until the client invokes
         * [TailcallVarargs.eval] which performs the tail call processing.
         * 
         * 
         * This method is typically not used directly by client code.
         * Instead use one of the function invocation methods.
         * 
         * @param func [LuaValue] to be called as a tail call
         * @param args [Varargs] containing the arguments to the call
         * @return [TailcallVarargs] to be used in tailcall oprocessing.
         * @see call
         * @see invoke
         * @see method
         * @see invokemethod
         */
        @kotlin.jvm.JvmStatic
        fun tailcallOf(func: LuaValue?, args: Varargs?): Varargs {
            return TailcallVarargs(func, args)
        }
    }
}
