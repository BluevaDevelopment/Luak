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
package net.blueva.luak.lib

import net.blueva.luak.DateParts
import net.blueva.luak.currentTimeMillis
import net.blueva.luak.dateParts
import net.blueva.luak.epochSeconds
import net.blueva.luak.platformEnvironment
import net.blueva.luak.platformExit
import net.blueva.luak.platformProperty
import net.blueva.luak.Buffer
import net.blueva.luak.Globals
import net.blueva.luak.LuaTable
import net.blueva.luak.LuaValue
import net.blueva.luak.Varargs
import net.blueva.luak.io.IOException
import net.blueva.luak.io.platformDeleteFile
import net.blueva.luak.io.platformRenameFile
import net.blueva.luak.io.platformTempFilePath

/**
 * Subclass of [LibFunction] which implements the standard lua `os` library.
 *
 *
 * Everything except `os.execute` is implemented in `commonMain` and behaves
 * the same on every Kotlin Multiplatform target: `os.getenv` reads the real
 * process environment, and `os.remove`, `os.rename`, and `os.tmpname` act on
 * the host filesystem. Running a shell command has no portable form, so
 * [execute] reports failure here and is overridden by
 * [net.blueva.luak.lib.jvm.JvmOsLib].
 *
 *
 * Because the nature of the `os` library is to encapsulate os-specific
 * features, the behavior of these functions varies considerably from their
 * counterparts in the C platform. On a host with no filesystem the file
 * operations fail the way Lua expects rather than being absent.
 *
 *
 * Typically this library is included as part of a call to
 * [net.blueva.luak.lib.LuaPlatform.standardGlobals]:
 * ```kotlin
 * val globals = LuaPlatform.standardGlobals()
 * println(globals.get("os").get("time").call())
 * ```
 *
 *
 * To instantiate and use it directly, link it into your globals table via
 * [Globals.load] using code such as:
 * ```kotlin
 * val globals = Globals()
 * globals.load(BaseLib())
 * globals.load(PackageLib())
 * globals.load(OsLib())
 * ```
 *
 *
 * @see LibFunction
 *
 * @see net.blueva.luak.lib.LuaPlatform
 *
 * @see net.blueva.luak.lib.jvm.JvmOsLib
 *
 * @see [Lua 5.2 OS Lib Reference](http://www.lua.org/manual/5.2/manual.html.6.9)
 */
open class OsLib
/**
 * Create and OsLib instance.
 */
    : TwoArgFunction() {
    protected var globals: Globals? = null

    /** Perform one-time initialization on the library by creating a table
     * containing the library functions, adding that table to the supplied environment,
     * adding the table to package.loaded, and returning table as the return value.
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env the environment to load into, typically a Globals instance.
     */
    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        globals = env!!.checkglobals()
        val os: LuaTable = LuaTable()
        for (i in net.blueva.luak.lib.OsLib.Companion.NAMES.indices) os.set(
            net.blueva.luak.lib.OsLib.Companion.NAMES[i],
            OsLibFunc(i, Companion.NAMES[i])
        )
        env!!.set("os", os)
        if (!env!!.get("package")!!.isnil()) env!!.get("package")!!.get("loaded")!!.set("os", os)
        return os
    }

    internal inner class OsLibFunc(opcode: Int, name: String?) : VarArgFunction() {
        init {
            this.opcode = opcode
            this.name = name
        }

        override fun invoke(args: Varargs): Varargs {
            try {
                when (opcode) {
                    net.blueva.luak.lib.OsLib.Companion.CLOCK -> return valueOf(clock())
                    net.blueva.luak.lib.OsLib.Companion.DATE -> {
                        val s: String = args.optjstring(1, "%c")!!
                        val t = if (args.isnumber(2)) args.todouble(2) else time(null)
                        if (s == "*t") {
                            val d = dateParts(t.toLong())
                            val tbl: LuaTable = LuaValue.tableOf()
                            tbl.set("year", LuaValue.valueOf(d.year))
                            tbl.set("month", LuaValue.valueOf(d.month))
                            tbl.set("day", LuaValue.valueOf(d.day))
                            tbl.set("hour", LuaValue.valueOf(d.hour))
                            tbl.set("min", LuaValue.valueOf(d.minute))
                            tbl.set("sec", LuaValue.valueOf(d.second))
                            tbl.set("wday", LuaValue.valueOf(d.weekday))
                            tbl.set("yday", LuaValue.valueOf(d.yearDay))
                            tbl.set("isdst", LuaValue.FALSE)
                            return tbl
                        }
                        return valueOf(date(s, if (t == -1.0) time(null) else t))
                    }

                    net.blueva.luak.lib.OsLib.Companion.DIFFTIME -> return valueOf(
                        difftime(
                            args.checkdouble(1),
                            args.checkdouble(2)
                        )
                    )

                    net.blueva.luak.lib.OsLib.Companion.EXECUTE -> {
                        // Asked with nothing to run, the question is only
                        // whether there is anything to run commands with.
                        val command: String? = args.optjstring(1, null)
                        return if (command == null) valueOf(hasshell())!! else execute(command)
                    }
                    net.blueva.luak.lib.OsLib.Companion.EXIT -> {
                        exit(args.optint(1, 0))
                        return (NONE)!!
                    }

                    net.blueva.luak.lib.OsLib.Companion.GETENV -> {
                        val `val`: String? = getenv(args.checkjstring(1))
                        return if (`val` != null) valueOf(`val`) else NIL
                    }

                    net.blueva.luak.lib.OsLib.Companion.REMOVE -> {
                        remove(args.checkjstring(1))
                        return (LuaValue.TRUE)!!
                    }

                    net.blueva.luak.lib.OsLib.Companion.RENAME -> {
                        rename(args.checkjstring(1), args.checkjstring(2))
                        return (LuaValue.TRUE)!!
                    }

                    net.blueva.luak.lib.OsLib.Companion.SETLOCALE -> {
                        val s = setlocale(args.optjstring(1, null), args.optjstring(2, "all"))
                        return if (s != null) valueOf(s) else NIL
                    }

                    net.blueva.luak.lib.OsLib.Companion.TIME -> return valueOf(time(args.opttable(1, null)))
                    net.blueva.luak.lib.OsLib.Companion.TMPNAME -> return valueOf(tmpname())
                }
                return (NONE)!!
            } catch (e: IOException) {
                return (varargsOf(NIL, valueOf(e.message)))!!
            }
        }
    }

    /**
     * @return an approximation of the amount in seconds of CPU time used by
     * the program.  Luak simply returns the elapsed time since the
     * OsLib class was loaded.
     */
    protected fun clock(): Double {
        return (currentTimeMillis() - net.blueva.luak.lib.OsLib.Companion.t0) / 1000.0
    }

    /**
     * Returns the number of seconds from time t1 to time t2.
     * In POSIX, Windows, and some other systems, this value is exactly t2-t1.
     * @param t2
     * @param t1
     * @return diffeence in time values, in seconds
     */
    protected fun difftime(t2: Double, t1: Double): Double {
        return t2 - t1
    }

    /**
     * If the time argument is present, this is the time to be formatted
     * (see the os.time function for a description of this value).
     * Otherwise, date formats the current time.
     * 
     * Date returns the date as a string,
     * formatted according to the same rules as ANSII strftime, but without
     * support for %g, %G, or %V.
     * 
     * When called without arguments, date returns a reasonable date and
     * time representation that depends on the host system and on the
     * current locale (that is, os.date() is equivalent to os.date("%c")).
     * 
     * @param format
     * @param time time since epoch, or -1 if not supplied
     * @return a LString or a LTable containing date and time,
     * formatted according to the given string format.
     */
    fun date(format: String, time: Double): String {
        var pattern = format
        if (pattern.startsWith("!")) pattern = pattern.substring(1)
        val d = dateParts(time.toLong())
        val result = StringBuilder(pattern.length)
        var index = 0
        while (index < pattern.length) {
            val char = pattern[index++]
            if (char != '%' || index >= pattern.length) {
                result.append(char)
                continue
            }
            when (val specifier = pattern[index++]) {
                '%' -> result.append('%')
                'a' -> result.append(WeekdayNameAbbrev[d.weekday - 1])
                'A' -> result.append(WeekdayName[d.weekday - 1])
                'b' -> result.append(MonthNameAbbrev[d.month - 1])
                'B' -> result.append(MonthName[d.month - 1])
                'c' -> result.append(date("%a %b %d %H:%M:%S %Y", time))
                'd' -> result.append(d.day.toString().padStart(2, '0'))
                'H' -> result.append(d.hour.toString().padStart(2, '0'))
                'I' -> result.append(((d.hour + 11) % 12 + 1).toString().padStart(2, '0'))
                'j' -> result.append(d.yearDay.toString().padStart(3, '0'))
                'm' -> result.append(d.month.toString().padStart(2, '0'))
                'M' -> result.append(d.minute.toString().padStart(2, '0'))
                'p' -> result.append(if (d.hour < 12) "AM" else "PM")
                'S' -> result.append(d.second.toString().padStart(2, '0'))
                'U' -> result.append(weekNumber(d, false).toString().padStart(2, '0'))
                'w' -> result.append((d.weekday - 1).toString())
                'W' -> result.append(weekNumber(d, true).toString().padStart(2, '0'))
                'x' -> result.append(date("%m/%d/%y", time))
                'X' -> result.append(date("%H:%M:%S", time))
                'y' -> result.append((d.year % 100).toString().padStart(2, '0'))
                'Y' -> result.append(d.year)
                'z' -> result.append("+0000")
                else -> LuaValue.argerror(1, "invalid conversion specifier '%$specifier'")
            }
        }
        return result.toString()
    }

    private fun weekNumber(date: DateParts, mondayFirst: Boolean): Int {
        val januaryFirst = dateParts(epochSeconds(date.year, 1, 1, 0, 0, 0))
        val firstWeekday = if (mondayFirst) (januaryFirst.weekday + 5) % 7 else januaryFirst.weekday - 1
        return (date.yearDay - 1 + firstWeekday) / 7
    }

    /**
     * This function is equivalent to the C function system.
     * It passes command to be executed by an operating system shell.
     * It returns a status code, which is system-dependent.
     * If command is absent, then it returns nonzero if a shell
     * is available and zero otherwise.
     * @param command command to pass to the system
     */
    protected open fun execute(command: String?): Varargs {
        return varargsOf(NIL, valueOf("exit"), (ONE)!!)
    }

    /**
     * True where the host can run a command for `os.execute`.
     *
     * Answered by `os.execute()` with nothing to run, which is how a program
     * asks whether running anything is possible at all.
     */
    protected open fun hasshell(): Boolean = false

    /**
     * Calls the C function exit, with an optional code, to terminate the host program.
     * @param code
     */
    protected fun exit(code: Int) {
        platformExit(code)
    }

    /**
     * Returns the value of the process environment variable varname, falling
     * back to the host's named-property namespace (JVM system properties; the
     * environment again everywhere else), or null if neither defines it.
     *
     * @param varname
     * @return String value, or null if not defined
     */
    protected open fun getenv(varname: String?): String? {
        val name: String = varname ?: return null
        return platformEnvironment(name) ?: platformProperty(name)
    }

    /**
     * Deletes the file or directory with the given name.
     * Directories must be empty to be removed.
     * If this function fails, it throws an IOException
     * 
     * @param filename
     * @throws IOException if it fails
     */
    @kotlin.Throws(IOException::class)
    protected open fun remove(filename: String?) {
        platformDeleteFile(filename ?: throw IOException("no file name"))
    }

    /**
     * Renames file or directory named oldname to newname.
     * If this function fails, it throws an IOException
     * 
     * @param oldname old file name
     * @param newname new file name
     * @throws IOException if it fails
     */
    @kotlin.Throws(IOException::class)
    protected open fun rename(oldname: String?, newname: String?) {
        platformRenameFile(
            oldname ?: throw IOException("no file name"),
            newname ?: throw IOException("no file name"),
        )
    }

    /**
     * Sets the current locale of the program. locale is a string specifying
     * a locale; category is an optional string describing which category to change:
     * "all", "collate", "ctype", "monetary", "numeric", or "time"; the default category
     * is "all".
     * 
     * If locale is the empty string, the current locale is set to an implementation-
     * defined native locale. If locale is the string "C", the current locale is set
     * to the standard C locale.
     * 
     * When called with null as the first argument, this function only returns the
     * name of the current locale for the given category.
     * 
     * @param locale
     * @param category
     * @return the name of the new locale, or null if the request
     * cannot be honored.
     */
    protected fun setlocale(locale: String?, category: String?): String? {
        // This runtime has one locale and it is "C": numbers and dates are
        // formatted the same way everywhere it runs. Reporting success for a
        // locale that is not in force would tell a caller it can expect, say, a
        // comma decimal separator that it will never get.
        if (locale == null || locale == "C" || locale.isEmpty()) return "C"
        return null
    }

    /**
     * Returns the current time when called without arguments,
     * or a time representing the date and time specified by the given table.
     * This table must have fields year, month, and day,
     * and may have fields hour, min, sec, and isdst
     * (for a description of these fields, see the os.date function).
     * @param table
     * @return long value for the time
     */
    protected fun time(table: LuaTable?): Double {
        if (table == null) return currentTimeMillis() / 1000.0
        return epochSeconds(
            table.get("year")!!.checkint(),
            table.get("month")!!.checkint(),
            table.get("day")!!.checkint(),
            table.get("hour")!!.optint(12),
            table.get("min")!!.optint(0),
            table.get("sec")!!.optint(0),
        ).toDouble()
    }

    /**
     * Returns a string with a file name that can be used for a temporary file.
     * The file must be explicitly opened before its use and explicitly removed
     * when no longer needed.
     * 
     * On some systems (POSIX), this function also creates a file with that name,
     * to avoid security risks. (Someone else might create the file with wrong
     * permissions in the time between getting the name and creating the file.)
     * You still have to open the file to use it and to remove it (even if you
     * do not use it).
     * 
     * @return String filename to use
     */
    protected open fun tmpname(): String =
        try {
            platformTempFilePath()
        } catch (e: IOException) {
            // No host temp directory: fall back to a bare, unique relative name.
            TMP_PREFIX + (tmpnames++) + TMP_SUFFIX
        }

    companion object {
        val TMP_PREFIX: String = ".luak"
        val TMP_SUFFIX: String = "tmp"

        private const val CLOCK = 0
        private const val DATE = 1
        private const val DIFFTIME = 2
        private const val EXECUTE = 3
        private const val EXIT = 4
        private const val GETENV = 5
        private const val REMOVE = 6
        private const val RENAME = 7
        private const val SETLOCALE = 8
        private const val TIME = 9
        private const val TMPNAME = 10

        private val NAMES = arrayOf<String?>(
            "clock",
            "date",
            "difftime",
            "execute",
            "exit",
            "getenv",
            "remove",
            "rename",
            "setlocale",
            "time",
            "tmpname",
        )

        private val t0: Long = currentTimeMillis()
        private var tmpnames: Long = net.blueva.luak.lib.OsLib.Companion.t0

        private val WeekdayNameAbbrev = arrayOf<String?>("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        private val WeekdayName =
            arrayOf<String?>("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        private val MonthNameAbbrev =
            arrayOf<String?>("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        private val MonthName = arrayOf<String?>(
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December"
        )
    }
}
